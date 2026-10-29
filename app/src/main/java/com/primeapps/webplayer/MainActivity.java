package com.primeapps.webplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.content.pm.PackageInfo;
import org.json.JSONObject;
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
    private long lastPageStartMs = 0L;
    private long lastFirstPaintMs = 0L;
    private long lastPageFinishedMs = 0L;
    private String lastMainFrameHttpError = "";
    private static final String PERF_TAG = "PrimePlayerPerf";

    // Passive diagnostics only: no URL substitution, custom player, overlays or media controls.
    // Works with old JavaScript engines (Android 5) and never stores media URLs or credentials.
    private static final String MEDIA_TIMING_SCRIPT =
        "(function(){if(window.__primePerf)return;" +
        "var d={start:0,first:0,stalls:0,waiting:0,playing:0,loadEvents:0};" +
        "window.__primePerf=d;" +
        "document.addEventListener('loadstart',function(e){if(e.target && e.target.tagName==='VIDEO'){" +
        "d.start=Date.now();d.loadEvents++;}},true);" +
        "document.addEventListener('waiting',function(e){if(e.target && e.target.tagName==='VIDEO'){" +
        "d.stalls++;d.waiting=Date.now();}},true);" +
        "document.addEventListener('playing',function(e){if(e.target && e.target.tagName==='VIDEO'){" +
        "d.playing=Date.now();if(d.start && !d.first)d.first=d.playing-d.start;d.waiting=0;}},true);" +
        "})();";

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
        // Chromium cache/Service Worker are owned by the PWA. Never force
        // cache-else-network: playlists, sessions and streaming URLs must stay fresh.
        if (Build.VERSION.SDK_INT >= 26) {
            // Android's default priority is IMPORTANT. Make that invariant explicit
            // so the visible player renderer is not deprioritized on low-memory TVs.
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
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
                lastPageStartMs = SystemClock.elapsedRealtime();
                lastFirstPaintMs = 0;
                lastPageFinishedMs = 0;
                lastMainFrameHttpError = "";
                if (progress != null) progress.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                // First actually visible page pixels can be shown before slower
                // images/secondary resources finish, without changing the site.
                if (Build.VERSION.SDK_INT >= 23) {
                    if (lastPageStartMs > 0 && lastFirstPaintMs == 0)
                        lastFirstPaintMs = SystemClock.elapsedRealtime() - lastPageStartMs;
                    if (progress != null) progress.setVisibility(View.GONE);
                }
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (lastPageStartMs > 0)
                    lastPageFinishedMs = SystemClock.elapsedRealtime() - lastPageStartMs;
                if (progress != null) progress.setVisibility(View.GONE);
                if (isFirstParty(Uri.parse(url))) {
                    // Read-only performance probe: listens for video events, never
                    // alters the player, src, HLS.js or mpegts.js lifecycle.
                    view.evaluateJavascript(MEDIA_TIMING_SCRIPT, null);
                }
                Log.i(PERF_TAG, "Navigation firstPaintMs=" + lastFirstPaintMs
                        + " pageFinishedMs=" + lastPageFinishedMs);
            }
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) {
                    lastMainFrameHttpError = "HTTP " + response.getStatusCode();
                }
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

    private void showDiagnostics() {
        if (web == null) return;
        String provider = "Sistema WebView";
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                PackageInfo pkg = WebView.getCurrentWebViewPackage();
                if (pkg != null) provider = pkg.packageName + " " + pkg.versionName;
            } catch (Exception ignored) {}
        }
        final String webViewVersion = provider;
        final String code =
            "(function(){try{var p=window.__primePerf||{};" +
            "var n=performance.getEntriesByType?performance.getEntriesByType('navigation'):[];" +
            "var v=n&&n.length?n[0]:null;" +
            "return JSON.stringify({mediaMs:p.first||0,stalls:p.stalls||0," +
            "loadEvents:p.loadEvents||0,domMs:v?Math.round(v.domContentLoadedEventEnd):0," +
            "serviceWorker:!!(navigator.serviceWorker&&navigator.serviceWorker.controller)});" +
            "}catch(e){return '{}';}})()";
        web.evaluateJavascript(code, value -> {
            long mediaMs = 0;
            long stalls = 0;
            long loaded = 0;
            long domMs = 0;
            boolean sw = false;
            try {
                String jsonString = (String) new org.json.JSONTokener(value).nextValue();
                JSONObject json = new JSONObject(jsonString);
                mediaMs = json.optLong("mediaMs", 0);
                stalls = json.optLong("stalls", 0);
                loaded = json.optLong("loadEvents", 0);
                domMs = json.optLong("domMs", 0);
                sw = json.optBoolean("serviceWorker", false);
            } catch (Exception ignored) {}
            String message = "WebView: " + webViewVersion +
                "\nPrimeira imagem: " + (lastFirstPaintMs > 0 ? lastFirstPaintMs + " ms" : "não medido") +
                "\nPágina finalizada: " + lastPageFinishedMs + " ms" +
                "\nDOM pronto: " + domMs + " ms" +
                "\nVídeo: " + (mediaMs > 0 ? mediaMs + " ms até reproduzir" : "não medido") +
                "\nCarregamentos de vídeo: " + loaded +
                "\nTravamentos registrados: " + stalls +
                "\nService Worker ativo: " + (sw ? "sim" : "não") +
                (lastMainFrameHttpError.isEmpty() ? "" : "\nPágina: " + lastMainFrameHttpError);
            new AlertDialog.Builder(this).setTitle("Diagnóstico de carregamento")
                .setMessage(message).setPositiveButton("Fechar", null).show();
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_F5) {
            if (customFullscreenView == null) {
                new AlertDialog.Builder(this)
                    .setTitle("Prime Player")
                    .setItems(new String[] {"Atualizar página", "Início", "Diagnóstico", "Sair"},
                        (dialog, which) -> {
                            if (which == 0) reloadWebsite();
                            else if (which == 1 && web != null) web.loadUrl(START_URL);
                            else if (which == 2) showDiagnostics();
                            else if (which == 3) finish();
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
