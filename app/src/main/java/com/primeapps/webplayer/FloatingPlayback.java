package com.primeapps.webplayer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

/**
 * Internal floating player for devices without system Picture-in-Picture.
 * It never uses SYSTEM_ALERT_WINDOW or leaves the application's private UI.
 */
public final class FloatingPlayback {
    public interface Callback {
        void onClosed(long positionMs, String error);
        void onExpand(long positionMs);
    }
    private final Activity host;
    private final FrameLayout root;
    private final Callback callback;
    private final FrameLayout window;
    private final PlayerView view;
    private ExoPlayer player;
    private String errorCode = "";
    private boolean released = false;
    private boolean resumeAfterBackground = false;
    private final boolean live;
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private long retryWindowStartMs = 0;

    private int dp(int dp) {
        return Math.round(dp * host.getResources().getDisplayMetrics().density);
    }
    private TextView control(String text) {
        TextView v = new TextView(host);
        v.setText(text);
        v.setTextSize(13);
        v.setTextColor(Color.WHITE);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setContentDescription(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(242, 34, 26, 29));
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), Color.rgb(247, 51, 63));
        v.setBackground(bg);
        return v;
    }
    public FloatingPlayback(Activity host, FrameLayout root, String url,
                            String format, long startMs, boolean live, String userAgent, Callback callback) {
        this.host = host;
        this.root = root;
        this.live = live;
        this.callback = callback;
        window = new FrameLayout(host);
        GradientDrawable back = new GradientDrawable();
        back.setColor(Color.BLACK);
        back.setCornerRadius(dp(13));
        back.setStroke(dp(2), Color.rgb(248, 43, 57));
        window.setBackground(back);
        window.setClipToOutline(true);
        int available = host.getResources().getDisplayMetrics().widthPixels - dp(28);
        int width = Math.max(dp(210), Math.min(dp(324), available));
        int height = (int)(width * 9f / 16f) + dp(48);
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(width, height,
                                                       Gravity.RIGHT | Gravity.BOTTOM);
        size.rightMargin = dp(14);
        size.bottomMargin = dp(16);
        root.addView(window, size);

        view = new PlayerView(host);
        view.setUseController(true);
        view.setControllerAutoShow(true);
        view.setFocusable(false);
        FrameLayout.LayoutParams videoSize = new FrameLayout.LayoutParams(-1, -1);
        videoSize.topMargin = dp(47);
        window.addView(view, videoSize);

        LinearLayout toolbar = new LinearLayout(host);
        toolbar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(3), dp(8), dp(3));
        toolbar.setBackgroundColor(Color.rgb(18, 15, 20));
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1, dp(46),
                                                                  Gravity.TOP);
        window.addView(toolbar, top);

        TextView expand = control("⛶  Tela cheia");
        LinearLayout.LayoutParams expandParams = new LinearLayout.LayoutParams(
                                                       0, dp(39), 1);
        toolbar.addView(expand, expandParams);
        TextView close = control("✕  Fechar");
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(92), dp(39));
        closeParams.leftMargin = dp(8);
        toolbar.addView(close, closeParams);
        expand.setOnClickListener(v -> expand());
        close.setOnClickListener(v -> close(""));
        close.setOnKeyListener((v, key, event) -> false);

        try {
            player = StreamHttp.makePlayer(host, url, userAgent);
            view.setPlayer(player);
            MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
            if ("hls".equalsIgnoreCase(format) || "m3u8".equalsIgnoreCase(format))
                item.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if ("dash".equalsIgnoreCase(format) || "mpd".equalsIgnoreCase(format))
                item.setMimeType(MimeTypes.APPLICATION_MPD);
            player.setMediaItem(item.build());
            player.addListener(new Player.Listener() {
                @Override public void onPlayerError(PlaybackException e) {
                    if (scheduleReconnect(e)) return;
                    errorCode = StreamHttp.safeError(e);
                    close(errorCode);
                }
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) close("");
                }
            });
            player.prepare();
            if (startMs > 0 && !live) player.seekTo(startMs);
            player.play();
        } catch (Exception e) {
            close("FLOATING_PLAYER_INITIALIZATION_ERROR");
        }
    }
    private boolean scheduleReconnect(PlaybackException error) {
        if (!live || released || player == null || !StreamHttp.canRetry(error)) return false;
        long now = android.os.SystemClock.elapsedRealtime();
        if (retryWindowStartMs == 0 || now - retryWindowStartMs > 60000) {
            retryWindowStartMs = now;
            retryCount = 0;
        }
        if (retryCount >= 4) return false;
        retryCount++;
        long delay = Math.min(12000L, 1500L << (retryCount - 1));
        reconnectHandler.postDelayed(() -> {
            if (released || player == null) return;
            player.seekToDefaultPosition();
            player.prepare();
            player.play();
        }, delay);
        return true;
    }

    public boolean isActive() { return !released; }
    private long position() {
        return player == null ? 0 : Math.max(0, player.getCurrentPosition());
    }
    private void release() {
        if (released) return;
        reconnectHandler.removeCallbacksAndMessages(null);
        released = true;
        view.setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
        root.removeView(window);
    }
    public void close(String error) {
        if (released) return;
        long positionMs = position();
        release();
        callback.onClosed(positionMs, error == null ? "" : error);
    }
    public void expand() {
        if (released) return;
        long positionMs = position();
        release();
        callback.onExpand(positionMs);
    }
    public void pauseForBackground() {
        if (!released && player != null) {
            resumeAfterBackground = player.isPlaying();
            player.pause();
        }
    }
    public void resumeAfterBackground() {
        if (!released && player != null && resumeAfterBackground) {
            player.play();
            resumeAfterBackground = false;
        }
    }
    public void dispose() {
        release();
    }
}
