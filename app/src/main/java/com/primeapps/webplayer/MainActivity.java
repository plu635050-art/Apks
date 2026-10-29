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
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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

public final class MainActivity extends Activity {
    private static final String HOME="https://primeappswebplayer.lovable.app/";
    private static final String TRUSTED_HOST="primeappswebplayer.lovable.app";
    private boolean pipFromSameVideo=false;
    private String samePlayerScript;
    private String vodPopupScript;
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
        if(!pip && pipFromSameVideo && browser!=null){
            pipFromSameVideo=false;
            browser.evaluateJavascript("window.PrimeAppsSamePlayer && window.PrimeAppsSamePlayer.exit()",null);
        }
        if(browser!=null){
            browser.evaluateJavascript("window.dispatchEvent(new CustomEvent("+
              "'primeapps-app-pip-changed',{detail:{active:"+(pip?"true":"false")+"}}));",null);
        }
        if(!pip)fullscreen();
    }
    // Existing Lovable calls remain compatible, but no Android media engine is started.
    private final class NativePlaybackBridge {
        @JavascriptInterface public void playVod(String url,String title,long startMs,String format) {
            useSamePlayer(false,true,false);
        }
        @JavascriptInterface public void playLive(String url,String title,String format) {
            useSamePlayer(false,false,false);
        }
        @JavascriptInterface public void playPopup(String url,String title,String format) {
            MainActivity.this.popupWebVideo(false);
        }
        @JavascriptInterface public void playPopupVod(String url,String title,long startMs,String format) {
            MainActivity.this.popupWebVideo(true);
        }
        @JavascriptInterface public void popupWebVideo() {
            MainActivity.this.popupWebVideo(true);
        }
    }
    private String loadScript(String name) {
        try(InputStream input=getAssets().open(name);
            ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] chunk=new byte[4096];
            int size;
            while((size=input.read(chunk))!=-1)output.write(chunk,0,size);
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        }catch(Exception ignored){return null;}
    }
    private String loadSamePlayerScript() {
        if(samePlayerScript==null)samePlayerScript=loadScript("same_player.js");
        return samePlayerScript;
    }
    private String loadVodPopupScript() {
        if(vodPopupScript==null)vodPopupScript=loadScript("vod_popup.js");
        return vodPopupScript;
    }
    private boolean enterSystemPip() {
        if(Build.VERSION.SDK_INT<26 ||
            !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            return false;
        try {
            PictureInPictureParams params=new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16,9)).build();
            return enterPictureInPictureMode(params);
        }catch(Exception ignored){return false;}
    }
    private void popupWebVideo(boolean vod) {
        runOnUiThread(()-> {
            if(browser==null || isFinishing())return;
            if(webFullscreen!=null){openWebPopup();return;}
            useSamePlayer(true,vod,true);
        });
    }
    private void emitPlayerError(boolean vod,String code) {
        if(browser==null)return;
        String event=vod?"primeapps-native-vod-result":"primeapps-native-live-result";
        browser.evaluateJavascript("window.dispatchEvent(new CustomEvent('"+event+
            "',{detail:{ok:false,status:'error',error:'"+code+"'}}));",null);
    }
    private void useSamePlayer(boolean popup,boolean vod,boolean preferSystemPip) {
        runOnUiThread(()->{
            if(browser==null||isFinishing())return;
            String current=browser.getUrl();
            if(current==null || !isInternal(Uri.parse(current)))return;
            String script=loadSamePlayerScript();
            if(script==null){emitPlayerError(vod,"SAME_PLAYER_UNAVAILABLE");return;}
            String kind=vod?"vod":"live";
            String mode=popup&&!preferSystemPip?"popup":"fullscreen";
            browser.evaluateJavascript(script,ignored->browser.evaluateJavascript(
                "(window.PrimeAppsSamePlayer && window.PrimeAppsSamePlayer.enter('"+
                    mode+"','"+kind+"')) || 'missing'",
                value->{
                    if(value==null || !value.contains("same")){
                        emitPlayerError(vod,"ACTIVE_WEB_VIDEO_NOT_FOUND");
                        return;
                    }
                    if(popup && preferSystemPip){
                        if(enterSystemPip()){
                            pipFromSameVideo=true;
                        }else {
                            browser.evaluateJavascript(
                                "window.PrimeAppsSamePlayer.enter('popup','"+kind+"')",null);
                        }
                    }
                }));
        });
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
        if(webFullscreen!=null){hideCustomVideo();return;}
        if(browser!=null){
            browser.evaluateJavascript("(function(){if(window.PrimeAppsSamePlayer&&window.PrimeAppsSamePlayer.isActive()){window.PrimeAppsSamePlayer.exit();return;}var e=new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true,cancelable:true});(document.activeElement||document).dispatchEvent(e);})();",null);
        }else super.onBackPressed();
    }
    @Override protected void onResume(){
        super.onResume();
        fullscreen();
        if(browser!=null)browser.onResume();
    }
    @Override protected void onPause(){
        if(browser!=null && !(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode()))
            browser.onPause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(webFullscreen!=null)hideCustomVideo();
        if(browser!=null){root.removeView(browser);browser.destroy();browser=null;}
        super.onDestroy();
    }
}
