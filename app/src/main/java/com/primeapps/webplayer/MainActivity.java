package com.primeapps.webplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * PRIME PLAYER LAB 9 - neutral Android shell for the original Lovable PWA.
 *
 * No playback bridge, injected CSS/JS, overlay controls, Picture-in-Picture,
 * ExoPlayer or custom decoder. The published website owns every player UI.
 */
public final class MainActivity extends Activity {
    private static final String START_URL = "https://primeappswebplayer.lovable.app/";
    private static final String FIRST_PARTY_HOST = "primeappswebplayer.lovable.app";
    private static final int FILE_PICKER_REQUEST = 1099;

    private FrameLayout container;
    private WebView web;
    private ProgressBar progress;
    private View customFullscreenView;
    private WebChromeClient.CustomViewCallback customFullscreenCallback;
    private ValueCallback<Uri[]> fileCallback;

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersiveMode();

        container = new FrameLayout(this);
        container.setBackgroundColor(Color.rgb(8, 6, 8));
        setContentView(container);

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(8, 6, 8));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        // Do NOT call addJavascriptInterface or evaluateJavascript with player code.
        // Lovable runs exactly its own video engine and fullscreen controls.
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Allow normal in-page navigation on the published site.
                // Refuse to launch external browsers from the app's main frame.
                return request.isForMainFrame() && !isFirstParty(request.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isFirstParty(Uri.parse(url));
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (progress != null) progress.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (progress != null) progress.setVisibility(View.GONE);
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    if (progress != null) progress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this,
                        "Sem conexão. Use MENU para atualizar.", Toast.LENGTH_LONG).show();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Standard HTML5 fullscreen support only: do not add any overlay UI.
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customFullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customFullscreenView = view;
                customFullscreenCallback = callback;
                container.addView(view, new FrameLayout.LayoutParams(-1, -1));
                enterImmersiveMode();
            }
            @Override
            public void onHideCustomView() {
                closeNativeFullscreen();
            }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                }
                fileCallback = callback;
                try {
                    Intent picker = params.createIntent();
                    startActivityForResult(picker, FILE_PICKER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException | SecurityException exception) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return true;
                }
            }
        });

        container.addView(web, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(this);
        container.addView(progress, new FrameLayout.LayoutParams(dp(42), dp(42),
                android.view.Gravity.CENTER));
        web.loadUrl(START_URL);
    }

    private boolean isFirstParty(Uri url) {
        return url != null
                && "https".equalsIgnoreCase(url.getScheme())
                && FIRST_PARTY_HOST.equalsIgnoreCase(url.getHost());
    }

    private void closeNativeFullscreen() {
        if (customFullscreenView == null) return;
        container.removeView(customFullscreenView);
        customFullscreenView = null;
        WebChromeClient.CustomViewCallback callback = customFullscreenCallback;
        customFullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
        enterImmersiveMode();
    }

    private void reloadWebsite() {
        if (web == null) return;
        web.reload(); // Never clears IndexedDB, cache, localStorage or cookies.
        web.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_F5) {
            if (customFullscreenView == null) {
                new AlertDialog.Builder(this)
                    .setTitle("Prime Player")
                    .setItems(new String[] {"Atualizar página", "Início", "Sair"}, (dialog, which) -> {
                        if (which == 0) reloadWebsite();
                        else if (which == 1 && web != null) web.loadUrl(START_URL);
                        else if (which == 2) finish();
                    })
                    .show();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customFullscreenView != null) {
            closeNativeFullscreen();
            return;
        }
        if (web != null) {
            // Let Lovable close the currently open player/modal and handle its own routes.
            web.evaluateJavascript(
                "(function(){var e=new KeyboardEvent('keydown',{key:'Escape',code:'Escape'," +
                "bubbles:true,cancelable:true});(document.activeElement||document).dispatchEvent(e);" +
                "window.dispatchEvent(new Event('primeapps-android-back'));})();",
                null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST && fileCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (customFullscreenView != null) closeNativeFullscreen();
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (web != null) {
            container.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
