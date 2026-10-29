package com.primeapps.webplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Rational;
import android.graphics.drawable.GradientDrawable;
import android.webkit.JsResult;
import android.widget.TextView;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.content.res.AssetManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String HOME="https://primeappswebplayer.lovable.app/";
    private static final String TRUSTED_HOST="primeappswebplayer.lovable.app";
    private static final int VIDEO_REQUEST=882;
    private static final int LIVE_REQUEST=883;
    private boolean nativeVideoOpen=false;
    private String samePlayerScript;
    private String vodPopupScript;
    private FloatingPlayback floatingPlayback;
    private FrameLayout root;
    private WebView browser;
    private ProgressBar loading;
    private View webFullscreen;
    private FrameLayout webPopupWindow;
    private TextView webPopupButton;
    private boolean webPopupActive=false;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private long lastMenuPress=0;

    private int dp(int n) {return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreen();
        root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8,6,8));
        setContentView(root);
        browser=new WebView(this);
        browser.setBackgroundColor(Color.rgb(8,6,8));
        browser.setFocusable(true);
        browser.setFocusableInTouchMode(true);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s=browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        browser.addJavascriptInterface(new NativePlaybackBridge(),"PrimeAppsNative");
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request.isForMainFrame() && !isInternal(request.getUrl());
            }
            @Override @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view,String url) {
                return !isInternal(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                if(loading!=null)loading.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(loading!=null)loading.setVisibility(View.GONE);
                if(isInternal(Uri.parse(url))){
                    String script=loadSamePlayerScript();
                    if(script!=null)view.evaluateJavascript(script,null);
                    String vodScript=loadVodPopupScript();
                    if(vodScript!=null)view.evaluateJavascript(vodScript,null);
                }
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error) {
                if(req.isForMainFrame()) {
                    if(loading!=null)loading.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this,"Sem conexão. MENU para atualizar.",Toast.LENGTH_LONG).show();
                }
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view,CustomViewCallback callback){
                if(webFullscreen!=null){callback.onCustomViewHidden();return;}
                webFullscreen=view; fullscreenCallback=callback;
                root.addView(view,new FrameLayout.LayoutParams(-1,-1));
                showWebPopupButton();
                fullscreen();
            }
            @Override public void onHideCustomView(){hideCustomVideo();}
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                String m=message==null?"":message.toLowerCase(java.util.Locale.ROOT);
                // The Lovable page already asks the user to use the web player on native failure.
                // Agree automatically, avoiding a browser-origin dialog with the public URL.
                if(m.contains("player nativo") && m.contains("player web")){
                    result.confirm();
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setNegativeButton("Cancelar",(d,w)->result.cancel())
                    .setPositiveButton("OK",(d,w)->result.confirm())
                    .setOnCancelListener(d->result.cancel())
                    .show();
                return true;
            }
        });
        root.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        loading=new ProgressBar(this);
        root.addView(loading,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
        browser.loadUrl(HOME);
    }
    private boolean isInternal(Uri u){
        return "https".equalsIgnoreCase(u.getScheme())&&TRUSTED_HOST.equalsIgnoreCase(u.getHost());
    }
    private void reload(){if(browser!=null){browser.reload();browser.requestFocus();}}
    private TextView videoButton(String title) {
        TextView v=new TextView(this);
        v.setText(title);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setTextSize(14);
        v.setFocusable(true);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.argb(230,34,27,34));
        bg.setStroke(dp(1),Color.rgb(254,42,63));
        bg.setCornerRadius(dp(10));
        v.setBackground(bg);
        return v;
    }
    private void showWebPopupButton(){
        if(webFullscreen==null || webPopupButton!=null) return;
        webPopupButton=videoButton("▣ POP-UP");
        webPopupButton.setContentDescription("Abrir filme em janela pop-up");
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(124),dp(44),
                Gravity.TOP|Gravity.RIGHT);
        p.topMargin=dp(72);
        p.rightMargin=dp(14);
        root.addView(webPopupButton,p);
        webPopupButton.setOnClickListener(v->openWebPopup());
    }
    private void hideWebPopupButton(){
        if(webPopupButton!=null){
            root.removeView(webPopupButton);
            webPopupButton=null;
        }
    }
    private void openWebPopup(){
        if(webFullscreen==null) return;
        if(Build.VERSION.SDK_INT>=26 &&
           getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)){
            try {
                PictureInPictureParams params=new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16,9)).build();
                if(enterPictureInPictureMode(params)){
                    hideWebPopupButton();
                    return;
                }
            }catch(Exception ignored){}
        }
        // Old Android/TV hardware: move only WebChrome fullscreen video over the web catalogue.
        hideWebPopupButton();
        root.removeView(webFullscreen);
        webPopupWindow=new FrameLayout(this);
        webPopupWindow.setBackgroundColor(Color.BLACK);
        int width=Math.min(dp(340),getResources().getDisplayMetrics().widthPixels/2);
        int height=Math.round(width*9f/16f)+dp(42);
        FrameLayout.LayoutParams floating=new FrameLayout.LayoutParams(width,height,
            Gravity.RIGHT|Gravity.BOTTOM);
        floating.rightMargin=dp(12);
        floating.bottomMargin=dp(15);
        root.addView(webPopupWindow,floating);
        FrameLayout.LayoutParams videoSize=new FrameLayout.LayoutParams(-1,-1);
        videoSize.topMargin=dp(42);
        webPopupWindow.addView(webFullscreen,videoSize);
        TextView maximize=videoButton("⛶ Tela cheia");
        FrameLayout.LayoutParams maxSize=new FrameLayout.LayoutParams(dp(125),dp(40),
            Gravity.TOP|Gravity.LEFT);
        webPopupWindow.addView(maximize,maxSize);
        maximize.setOnClickListener(v->expandWebPopup());
        TextView close=videoButton("✕ Fechar");
        FrameLayout.LayoutParams closeSize=new FrameLayout.LayoutParams(dp(102),dp(40),
            Gravity.TOP|Gravity.RIGHT);
        webPopupWindow.addView(close,closeSize);
        close.setOnClickListener(v->hideCustomVideo());
        webPopupActive=true;
    }
    private void expandWebPopup(){
        if(webPopupWindow==null || webFullscreen==null) return;
        webPopupWindow.removeView(webFullscreen);
        root.removeView(webPopupWindow);
        webPopupWindow=null;
        webPopupActive=false;
        root.addView(webFullscreen,new FrameLayout.LayoutParams(-1,-1));
        showWebPopupButton();
    }
    private void hideCustomVideo(){
        hideWebPopupButton();
        if(webPopupWindow!=null){
            if(webFullscreen!=null)webPopupWindow.removeView(webFullscreen);
            root.removeView(webPopupWindow);
            webPopupWindow=null;
        }else if(webFullscreen!=null){
            root.removeView(webFullscreen);
        }
        webFullscreen=null;
        webPopupActive=false;
        WebChromeClient.CustomViewCallback cb=fullscreenCallback;
        fullscreenCallback=null;
        if(cb!=null)cb.onCustomViewHidden();
        fullscreen();
    }
    @Override public void onPictureInPictureModeChanged(boolean pip,Configuration config){
        super.onPictureInPictureModeChanged(pip,config);
        if(!pip && webFullscreen!=null && webPopupWindow==null)showWebPopupButton();
        if(browser!=null){
            browser.evaluateJavascript("window.dispatchEvent(new CustomEvent("+
              "'primeapps-app-pip-changed',{detail:{active:"+(pip?"true":"false")+"}}));",null);
        }
        if(!pip)fullscreen();
    }
    // Explicit entry points: the website decides when a movie or live TV goes fullscreen.
    // TV mini-player remains in the WebView; no automatic URL or button interception.
    private final class NativePlaybackBridge {
        @JavascriptInterface public void playVod(String url,String title,long startMs,String format){
            startNativePlayback(url,title,startMs,format,false);
        }
        // Live TV is already playing through hls.js/mpegts.js on the same WebView video.
        // Never open a second ExoPlayer connection merely to make it fullscreen.
        @JavascriptInterface public void playLive(String url,String title,String format){
            useSamePlayer(false);
        }
        @JavascriptInterface public void playPopup(String url,String title,String format){
            useSamePlayer(true);
        }
        @JavascriptInterface public void playPopupVod(String url,String title,long startMs,String format){
            startNativePlayback(url,title,startMs,format,false,true);
        }
        // PWA-like POP-UP reuses the already-playing HTML5 video in this WebView.
        @JavascriptInterface public void popupWebVideo(){
            runOnUiThread(()->openExistingWebVideoPopup());
        }
    }
    private String loadVodPopupScript(){
        if(vodPopupScript!=null)return vodPopupScript;
        try(InputStream stream=getAssets().open("vod_popup.js");
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[4096];
            int n;
            while((n=stream.read(chunk))!=-1)out.write(chunk,0,n);
            vodPopupScript=new String(out.toByteArray(),StandardCharsets.UTF_8);
            return vodPopupScript;
        }catch(Exception ignored){return null;}
    }
    private void openExistingWebVideoPopup(){
        if(browser==null||isFinishing()||nativeVideoOpen)return;
        if(webFullscreen!=null){
            openWebPopup();
            return;
        }
        if(Build.VERSION.SDK_INT>=26 &&
            getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)){
            try{
                PictureInPictureParams params=new PictureInPictureParams.Builder()
                   .setAspectRatio(new Rational(16,9)).build();
                if(enterPictureInPictureMode(params))return;
            }catch(Exception ignored){}
        }
        String script=loadSamePlayerScript();
        if(script!=null){
            browser.evaluateJavascript(script,ignored->browser.evaluateJavascript(
               "window.PrimeAppsSamePlayer && window.PrimeAppsSamePlayer.enter('popup','vod');",null));
        }
    }
    private String loadSamePlayerScript(){
        if(samePlayerScript!=null)return samePlayerScript;
        try(InputStream stream=getAssets().open("same_player.js");
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[4096];
            int n;
            while((n=stream.read(chunk))!=-1)out.write(chunk,0,n);
            samePlayerScript=new String(out.toByteArray(),StandardCharsets.UTF_8);
            return samePlayerScript;
        }catch(Exception ignored){return null;}
    }
    private void useSamePlayer(boolean popup){
        runOnUiThread(()->{
            if(browser==null||isFinishing()||nativeVideoOpen)return;
            String current=browser.getUrl();
            if(current==null || !isInternal(Uri.parse(current)))return;
            String script=loadSamePlayerScript();
            if(script==null){
                browser.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('primeapps-native-live-result',{detail:{ok:false,status:'error',error:'SAME_PLAYER_SCRIPT_UNAVAILABLE'}}));",null);
                return;
            }
            // Evaluate in the same page; do not create an Activity, ExoPlayer, or connection.
            browser.evaluateJavascript(script,ignored->
                browser.evaluateJavascript(
                    "(window.PrimeAppsSamePlayer && window.PrimeAppsSamePlayer.enter('"+
                    (popup?"popup":"fullscreen")+"')) || 'missing'",
                    value->{
                        if(value!=null && value.contains("missing")){
                            browser.evaluateJavascript(
                                "window.dispatchEvent(new CustomEvent('primeapps-native-live-result',{detail:{ok:false,status:'error',error:'ACTIVE_WEB_VIDEO_NOT_FOUND'}}));",null);
                        }
                    })
            );
        });
    }
    private void startNativePlayback(String url,String title,long startMs,String format,boolean live){
        startNativePlayback(url,title,startMs,format,live,false);
    }
    private void startNativePlayback(String url,String title,long startMs,String format,
                                     boolean live,boolean autoPopup){
        if(url==null || url.length()>8192 || title!=null && title.length()>2000)return;
        Uri uri;
        try{uri=Uri.parse(url);}catch(Exception ignored){return;}
        String scheme=uri.getScheme();
        if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return;
        if(uri.getHost()==null || uri.getHost().trim().isEmpty())return;
        runOnUiThread(()->{
            if(isFinishing() || browser==null || nativeVideoOpen)return;
            String current=browser.getUrl();
            if(current==null || !isInternal(Uri.parse(current)))return;
            nativeVideoOpen=true;
            Intent i=new Intent(MainActivity.this,NativeVideoActivity.class);
            i.putExtra("url",url);
            i.putExtra("title",title==null?"":title.substring(0,Math.min(200,title.length())));
            i.putExtra("start",Math.max(0L,startMs));
            i.putExtra("format",format==null?"":format.substring(0,Math.min(24,format.length())));
            i.putExtra("isLive",live);
            i.putExtra("autoPopup",autoPopup);
            i.putExtra("userAgent",browser.getSettings().getUserAgentString());
            try{
                startActivityForResult(i,live?LIVE_REQUEST:VIDEO_REQUEST);
            }catch(Exception e){
                nativeVideoOpen=false;
                browser.evaluateJavascript("window.dispatchEvent(new CustomEvent('primeapps-native-player-failed'));",null);
            }
        });
    }
    @Override protected void onActivityResult(int req,int code,Intent data){
        super.onActivityResult(req,code,data);
        if(req!=VIDEO_REQUEST && req!=LIVE_REQUEST)return;
        if(data!=null && data.getBooleanExtra("popupFallback",false)){
            String url=data.getStringExtra("url");
            String title=data.getStringExtra("title");
            String format=data.getStringExtra("format");
            long pos=Math.max(0L,data.getLongExtra("positionMs",0L));
            boolean live=data.getBooleanExtra("isLive",req==LIVE_REQUEST);
            if(url!=null && root!=null){
                try{
                    floatingPlayback=new FloatingPlayback(this,root,url,format,pos,live,
                        browser.getSettings().getUserAgentString(),new FloatingPlayback.Callback(){
                            @Override public void onClosed(long endMs,String error){
                                floatingPlayback=null;
                                nativeVideoOpen=false;
                                dispatchPlaybackResult(req,endMs,false,error);
                            }
                            @Override public void onExpand(long resumeMs){
                                floatingPlayback=null;
                                nativeVideoOpen=false;
                                startNativePlayback(url,title,resumeMs,format,live);
                            }
                        });
                    return;
                }catch(Exception ignored){
                    nativeVideoOpen=false;
                    dispatchPlaybackResult(req,pos,false,"FLOATING_WINDOW_UNAVAILABLE");
                    return;
                }
            }
        }
        nativeVideoOpen=false;
        long pos=data==null?0:data.getLongExtra("positionMs",0);
        boolean ended=data!=null&&data.getBooleanExtra("ended",false);
        String error=data==null?"":data.getStringExtra("errorCode");
        dispatchPlaybackResult(req,pos,ended,error);
    }
    private void dispatchPlaybackResult(int req,long pos,boolean ended,String error){
        if(browser==null)return;
        String message=error==null?"":error;
        boolean ok=message.isEmpty();
        try{
            JSONObject details=new JSONObject();
            details.put("positionMs",Math.max(0L,pos));
            details.put("ended",ended);
            details.put("error",message);
            details.put("ok",ok);
            details.put("status",ok?"closed":"error");
            String event=req==LIVE_REQUEST?
                 "primeapps-native-live-result":"primeapps-native-vod-result";
            String js="window.dispatchEvent(new CustomEvent("+JSONObject.quote(event)+
                ",{detail:"+details.toString()+"}));";
            browser.evaluateJavascript(js,null);
            browser.requestFocus();
        }catch(Exception ignored){
            browser.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('primeapps-native-player-failed'));",null);
        }
    }
    @Override public boolean onKeyDown(int key,KeyEvent e){
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_F5){
            if(webFullscreen==null){
                new AlertDialog.Builder(this).setTitle("Prime App Player")
                    .setItems(new String[]{"Atualizar página","Início","Sair"},(d,choice)->{
                        if(choice==0)reload();
                        if(choice==1)browser.loadUrl(HOME);
                        if(choice==2)finish();
                    }).show();
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_BACK){onBackPressed();return true;}
        return super.onKeyDown(key,e);
    }
    @Override @SuppressWarnings("deprecation")
    public void onBackPressed(){
        if(floatingPlayback!=null && floatingPlayback.isActive()){
            floatingPlayback.close("");
            return;
        }
        if(webFullscreen!=null){hideCustomVideo();return;}
        if(browser!=null){
            browser.evaluateJavascript("(function(){var e=new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true,cancelable:true});(document.activeElement||document).dispatchEvent(e);})();",null);
        }else super.onBackPressed();
    }
    @Override protected void onResume(){
        super.onResume();
        fullscreen();
        if(browser!=null)browser.onResume();
        if(floatingPlayback!=null)floatingPlayback.resumeAfterBackground();
    }
    @Override protected void onPause(){
        if(floatingPlayback!=null)floatingPlayback.pauseForBackground();
        if(browser!=null && !(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode()))
            browser.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(floatingPlayback!=null){floatingPlayback.dispose();floatingPlayback=null;}
        if(webFullscreen!=null)hideCustomVideo();
        if(browser!=null){root.removeView(browser);browser.destroy();browser=null;}
        super.onDestroy();
    }
}
