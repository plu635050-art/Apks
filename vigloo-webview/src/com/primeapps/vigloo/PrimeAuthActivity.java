package com.primeapps.vigloo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Launcher isolado: nenhuma classe SecNeo nem código Vigloo é alterado.
 * O site de login deve realizar seu próprio controle de validade/sessão.
 * Vigloo://approved indica apenas o retorno da página de login.
 */
public final class PrimeAuthActivity extends Activity {
    private static final String LOGIN = "https://primepsusiv.shop/app/vigloo/login";
    private static final String ORIGINAL = "com.vigloo.MainActivity";
    private WebView browser;
    private ProgressBar progress;
    private boolean reachedLoginOrigin;
    private boolean entered;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLACK);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        layout.addView(browser, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(100, 100, Gravity.CENTER);
        layout.addView(progress, progressParams);
        setContentView(layout);

        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                if (progress != null) progress.setVisibility(value < 80 ? View.VISIBLE : View.GONE);
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return navigate(url);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return navigate(request.getUrl().toString());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                try {
                    Uri uri = Uri.parse(url);
                    if ("https".equalsIgnoreCase(uri.getScheme()) &&
                        "primepsusiv.shop".equalsIgnoreCase(uri.getHost())) {
                        reachedLoginOrigin = true;
                    }
                } catch (Exception ignored) {}
                if (progress != null) progress.setVisibility(View.GONE);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(PrimeAuthActivity.this, "Falha de seguranca HTTPS", Toast.LENGTH_LONG).show();
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame() && progress != null) progress.setVisibility(View.GONE);
            }
        });
        if (state != null && browser.restoreState(state) != null) {
            reachedLoginOrigin = state.getBoolean("seen_login", false);
        } else {
            browser.loadUrl(LOGIN);
        }
    }

    private boolean navigate(String address) {
        Uri uri;
        try { uri = Uri.parse(address); }
        catch (Exception e) { return true; }
        String scheme = uri.getScheme();
        if ("vigloo".equalsIgnoreCase(scheme)) {
            if ("approved".equalsIgnoreCase(uri.getHost()) &&
                    reachedLoginOrigin && !entered) {
                entered = true;
                Intent next = new Intent();
                next.setClassName(getPackageName(), ORIGINAL);
                startActivity(next);
                finish();
            } else {
                Toast.makeText(this, "Retorno de autorizacao invalido", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        // JS and file/content/intent schemes must not escape from the login page.
        if ("https".equalsIgnoreCase(scheme)) return false;
        if ("about".equalsIgnoreCase(scheme) && "about:blank".equalsIgnoreCase(address)) return false;
        return true;
    }

    @Override
    public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("seen_login", reachedLoginOrigin);
        if (browser != null) browser.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        if (browser != null) {
            ((ViewGroup)browser.getParent()).removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
