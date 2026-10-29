package com.primeapps.webplayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.Toast;
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
    private boolean ended=false;
    private long lastPosition=0;
    private void fullscreen(){
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
        Intent input=getIntent();
        String url=input.getStringExtra("url");
        if(url==null||url.isEmpty()){finish();return;}
        playerView=new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setFocusable(true);
        setContentView(playerView);
        try{
            DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true);
            String cookie=CookieManager.getInstance().getCookie(url);
            if(cookie!=null&&!cookie.isEmpty()){
                Map<String,String> headers=new HashMap<>();
                headers.put("Cookie",cookie);
                http.setDefaultRequestProperties(headers);
            }
            DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
            player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(data)).build();
            playerView.setPlayer(player);
            String mime=input.getStringExtra("format");
            MediaItem.Builder item=new MediaItem.Builder().setUri(Uri.parse(url));
            if("hls".equalsIgnoreCase(mime)||"m3u8".equalsIgnoreCase(mime))item.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if("dash".equalsIgnoreCase(mime)||"mpd".equalsIgnoreCase(mime))item.setMimeType(MimeTypes.APPLICATION_MPD);
            player.setMediaItem(item.build());
            player.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException error){
                    Toast.makeText(NativeVideoActivity.this,"Vídeo indisponível: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
                }
                @Override public void onPlaybackStateChanged(int state){
                    if(state==Player.STATE_ENDED){ended=true;endPlayer();}
                }
            });
            player.prepare();
            long start=input.getLongExtra("start",0);
            if(start>0)player.seekTo(start);
            player.play();
        }catch(Exception exc){
            Toast.makeText(this,"Não foi possível iniciar o motor nativo.",Toast.LENGTH_LONG).show();
            endPlayer();
        }
    }
    private void endPlayer(){
        if(player!=null)lastPosition=player.getCurrentPosition();
        Intent result=new Intent();
        result.putExtra("positionMs",lastPosition);
        result.putExtra("ended",ended);
        setResult(RESULT_OK,result);
        finish();
    }
    @Override @SuppressWarnings("deprecation") public void onBackPressed(){endPlayer();}
    @Override protected void onStop(){if(player!=null)player.pause();super.onStop();}
    @Override protected void onDestroy(){
        if(playerView!=null)playerView.setPlayer(null);
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)fullscreen();}
}
