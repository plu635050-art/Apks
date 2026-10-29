package com.primeapps.webplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String HOME="https://primeappswebplayer.lovable.app/";
    private static final String TRUSTED_HOST="primeappswebplayer.lovable.app";
    private static final int VIDEO_REQUEST=882;
    private FrameLayout root;
    private WebView browser;
    private ImageButton refresh;
    private ProgressBar loading;
    private View webFullscreen;
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
        browser.addJavascriptInterface(new NativeVodBridge(),"PrimeAppsNative");
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
                refresh.setVisibility(View.GONE); fullscreen();
            }
            @Override public void onHideCustomView(){hideCustomVideo();}
        });
        root.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        loading=new ProgressBar(this);
        root.addView(loading,new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER));
        refresh=new ImageButton(this);
        refresh.setImageResource(android.R.drawable.ic_popup_sync);
        refresh.setContentDescription(getString(R.string.refresh));
        refresh.setBackgroundColor(Color.argb(220,42,34,38));
        refresh.setColorFilter(Color.WHITE);
        refresh.setFocusable(false);
        refresh.setOnClickListener(v->reload());
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.RIGHT|Gravity.TOP);
        p.topMargin=dp(10);p.rightMargin=dp(12);
        root.addView(refresh,p);
        browser.loadUrl(HOME);
    }
    private boolean isInternal(Uri u){
        return "https".equalsIgnoreCase(u.getScheme())&&TRUSTED_HOST.equalsIgnoreCase(u.getHost());
    }
    private void reload(){if(browser!=null){browser.reload();browser.requestFocus();}}
    private void hideCustomVideo(){
        if(webFullscreen!=null){
            root.removeView(webFullscreen);webFullscreen=null;
            WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreenCallback=null;
            if(cb!=null)cb.onCustomViewHidden();
        }
        if(refresh!=null)refresh.setVisibility(View.VISIBLE);
        fullscreen();
    }
    private final class NativeVodBridge {
        @JavascriptInterface public void playVod(String url,String title,long startMs,String format){
            if(url==null||url.length()>8192)return;
            Uri u;
            try{u=Uri.parse(url);}catch(Exception e){return;}
            String scheme=u.getScheme();
            if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return;
            runOnUiThread(()->{
                if(isFinishing())return;
                Intent i=new Intent(MainActivity.this,NativeVideoActivity.class);
                i.putExtra("url",url);
                i.putExtra("title",title==null?"":title.substring(0,Math.min(200,title.length())));
                i.putExtra("start",Math.max(0,startMs));
                i.putExtra("format",format==null?"":format);
                startActivityForResult(i,VIDEO_REQUEST);
            });
        }
    }
    @Override protected void onActivityResult(int req,int code,Intent data){
        super.onActivityResult(req,code,data);
        if(req==VIDEO_REQUEST&&browser!=null){
            long pos=data==null?0:data.getLongExtra("positionMs",0);
            boolean ended=data!=null&&data.getBooleanExtra("ended",false);
            String js="window.dispatchEvent(new CustomEvent('primeapps-native-vod-result',{detail:{positionMs:"+
                pos+",ended:"+(ended?"true":"false")+"}}));";
            browser.evaluateJavascript(js,null);
            browser.requestFocus();
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
        if(webFullscreen!=null){hideCustomVideo();return;}
        if(browser!=null){
            browser.evaluateJavascript("(function(){var e=new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true,cancelable:true});(document.activeElement||document).dispatchEvent(e);})();",null);
        }else super.onBackPressed();
    }
    @Override protected void onResume(){super.onResume();fullscreen();if(browser!=null)browser.onResume();}
    @Override protected void onPause(){if(browser!=null)browser.onPause();super.onPause();}
    @Override protected void onDestroy(){
        if(webFullscreen!=null)hideCustomVideo();
        if(browser!=null){root.removeView(browser);browser.destroy();browser=null;}
        super.onDestroy();
    }
}
