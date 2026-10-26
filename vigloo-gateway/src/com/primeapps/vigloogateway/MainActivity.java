package com.primeapps.vigloogateway;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String LOGIN_URL = "https://primepsusiv.shop/app/vigloo/login";
    private static final String ORIGINAL_PACKAGE = "com.vigloo";
    private static final String ORIGINAL_ACTIVITY = "com.vigloo.MainActivity";
    private WebView webView;
    private ProgressBar progress;
    private boolean opened;
    private boolean seenTrustedPage;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        frame.addView(webView, new FrameLayout.LayoutParams(-1,-1));
        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(88,88,Gravity.CENTER);
        frame.addView(progress, progressParams);
        setContentView(frame);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view,int p) {
                if(progress!=null) progress.setVisibility(p<95 ? View.VISIBLE:View.GONE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if(!request.isForMainFrame())return false;
                return handleNavigation(request.getUrl().toString());
            }
            @Override
            public void onPageFinished(WebView view,String url){
                Uri uri=Uri.parse(url);
                if(isTrusted(uri))seenTrustedPage=true;
                if(progress!=null)progress.setVisibility(View.GONE);
            }
            @Override
            public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError err){
                handler.cancel();
                Toast.makeText(MainActivity.this,"Conexao HTTPS invalida",Toast.LENGTH_LONG).show();
            }
        });
        if(state!=null && webView.restoreState(state)!=null) {
            seenTrustedPage=state.getBoolean("trusted",false);
        } else {
            webView.loadUrl(LOGIN_URL);
        }
    }
    private boolean isTrusted(Uri uri){
        return uri!=null && "https".equalsIgnoreCase(uri.getScheme()) &&
            "primepsusiv.shop".equalsIgnoreCase(uri.getHost());
    }
    private boolean handleNavigation(String url){
        Uri u;
        try {u=Uri.parse(url);} catch(Exception e){return true;}
        if("vigloo".equalsIgnoreCase(u.getScheme())){
            Uri top=Uri.parse(webView.getUrl()==null?"":webView.getUrl());
            if("approved".equalsIgnoreCase(u.getHost()) && seenTrustedPage &&
               isTrusted(top) && !opened) {
                opened=true;
                Intent i=new Intent(Intent.ACTION_MAIN);
                i.setClassName(ORIGINAL_PACKAGE,ORIGINAL_ACTIVITY);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                    finish();
                } catch(ActivityNotFoundException e){
                    opened=false;
                    Toast.makeText(this,"Instale o Vigloo original primeiro.",Toast.LENGTH_LONG).show();
                } catch(SecurityException e){
                    opened=false;
                    Toast.makeText(this,"Nao foi possivel abrir o Vigloo.",Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this,"Retorno de autenticacao invalido.",Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        String scheme=u.getScheme();
        return !("https".equalsIgnoreCase(scheme) ||
           ("about".equalsIgnoreCase(scheme) && "about:blank".equalsIgnoreCase(url)));
    }
    @Override
    protected void onSaveInstanceState(Bundle state){
        state.putBoolean("trusted",seenTrustedPage);
        if(webView!=null)webView.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed(){
        if(webView!=null&&webView.canGoBack())webView.goBack();
        else super.onBackPressed();
    }
    @Override
    protected void onDestroy(){
        if(webView!=null){
            if(webView.getParent() instanceof ViewGroup)
                ((ViewGroup)webView.getParent()).removeView(webView);
            webView.destroy();
            webView=null;
        }
        super.onDestroy();
    }
}
