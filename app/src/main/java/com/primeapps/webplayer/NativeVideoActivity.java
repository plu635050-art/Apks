package com.primeapps.webplayer;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

public final class NativeVideoActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout root;
    private TextView popupButton;
    private boolean ended = false;
    private boolean live = false;
    private boolean exiting = false;
    private boolean popupRequested = false;
    private boolean playingBeforeBackground = false;
    private long positionMs = 0;
    private String failureCode = "";
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private long retryWindowStartMs = 0;
    private static final int MAX_RECONNECTS_PER_MINUTE = 4;

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
    private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private boolean currentlyInSystemPip() {
        return Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
        final Intent input = getIntent();
        final String url = input.getStringExtra("url");
        live = input.getBooleanExtra("isLive", false);
        popupRequested = input.getBooleanExtra("autoPopup", false);
        if (url == null || url.trim().length() == 0) {
            failureCode = "MISSING_MEDIA_URL";
            finishWithResult(false);
            return;
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setFocusable(true);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        popupButton = new TextView(this);
        popupButton.setText("▣  POP-UP");
        popupButton.setTextColor(Color.WHITE);
        popupButton.setTextSize(15);
        popupButton.setGravity(Gravity.CENTER);
        popupButton.setContentDescription("Abrir vídeo em janela pop-up");
        popupButton.setFocusable(true);
        popupButton.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable btn = new GradientDrawable();
        btn.setColor(Color.argb(240, 36, 30, 36));
        btn.setCornerRadius(dp(11));
        btn.setStroke(dp(1), Color.rgb(255, 54, 71));
        popupButton.setBackground(btn);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                dp(126), dp(48), Gravity.RIGHT | Gravity.TOP);
        buttonParams.topMargin = dp(70);
        buttonParams.rightMargin = dp(16);
        root.addView(popupButton, buttonParams);
        popupButton.setOnClickListener(v -> enterPopup());
        try {
            player = StreamHttp.makePlayer(this, url, input.getStringExtra("userAgent"));
            playerView.setPlayer(player);
            MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
            String format = input.getStringExtra("format");
            if (format != null) {
                if (format.equalsIgnoreCase("hls") || format.equalsIgnoreCase("m3u8"))
                    item.setMimeType(MimeTypes.APPLICATION_M3U8);
                else if (format.equalsIgnoreCase("dash") || format.equalsIgnoreCase("mpd"))
                    item.setMimeType(MimeTypes.APPLICATION_MPD);
            }
            player.setMediaItem(item.build());
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException e) {
                    if (scheduleReconnect(e)) return;
                    failureCode = StreamHttp.safeError(e);
                    finishWithResult(false);
                }
                @Override public void onPlaybackStateChanged(int status) {
                    if (status == Player.STATE_READY && popupRequested && !exiting) {
                        popupRequested = false;
                        enterPopup();
                    }
                    if (status == Player.STATE_ENDED && !exiting) {
                        ended = true;
                        finishWithResult(false);
                    }
                }
            });
            player.prepare();
            long start = Math.max(0, input.getLongExtra("start", 0));
            if (start > 0 && !live) player.seekTo(start);
            player.play();
        } catch (Exception e) {
            failureCode = "PLAYER_INITIALIZATION_ERROR";
            finishWithResult(false);
        }
    }
    private boolean scheduleReconnect(PlaybackException error) {
        if (!live || exiting || player == null || !StreamHttp.canRetry(error)) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        if (retryWindowStartMs == 0 || now - retryWindowStartMs > 60000) {
            retryWindowStartMs = now;
            retryCount = 0;
        }
        if (retryCount >= MAX_RECONNECTS_PER_MINUTE) return false;
        retryCount++;
        long delay = Math.min(12000L, 1500L << (retryCount - 1));
        reconnectHandler.postDelayed(() -> {
            if (exiting || player == null) return;
            player.seekToDefaultPosition();
            player.prepare();
            player.play();
        }, delay);
        return true;
    }

    private void enterPopup() {
        if (exiting || player == null) return;
        if (Build.VERSION.SDK_INT >= 26 &&
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).build();
                if (enterPictureInPictureMode(params)) {
                    return;
                }
            } catch (Exception ignored) {
                // Fall back to an in-app window without requesting overlay permission.
            }
        }
        finishWithResult(true);
    }
    private void finishWithResult(boolean popupFallback) {
        if (exiting) return;
        exiting = true;
        if (player != null) positionMs = Math.max(0, player.getCurrentPosition());
        Intent result = new Intent();
        result.putExtra("positionMs", positionMs);
        result.putExtra("ended", ended);
        result.putExtra("errorCode", failureCode);
        result.putExtra("isLive", live);
        if (popupFallback) {
            result.putExtra("popupFallback", true);
            result.putExtra("url", getIntent().getStringExtra("url"));
            result.putExtra("title", getIntent().getStringExtra("title"));
            result.putExtra("format", getIntent().getStringExtra("format"));
        }
        setResult(RESULT_OK, result);
        finish();
    }
    @Override @SuppressWarnings("deprecation")
    public void onBackPressed() {
        finishWithResult(false);
    }
    @Override protected void onStop() {
        if (player != null && !currentlyInSystemPip()) {
            playingBeforeBackground = player.isPlaying();
            player.pause();
        }
        super.onStop();
    }
    @Override protected void onStart() {
        super.onStart();
        if (player != null && !exiting && playingBeforeBackground) {
            player.play();
            playingBeforeBackground = false;
        }
    }
    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (playerView != null) playerView.setUseController(!inPip);
        if (popupButton != null) popupButton.setVisibility(inPip ? View.GONE : View.VISIBLE);
        if (!inPip) fullscreen();
    }
    @Override protected void onDestroy() {
        reconnectHandler.removeCallbacksAndMessages(null);
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !currentlyInSystemPip()) fullscreen();
    }
}
