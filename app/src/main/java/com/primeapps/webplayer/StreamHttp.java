package com.primeapps.webplayer;

import android.app.Activity;
import android.webkit.CookieManager;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared network settings for full-screen and in-app floating playback.
 * Does not change the stream URL, remove authentication or fetch the entire live stream.
 */
public final class StreamHttp {
    private StreamHttp() {}

    public static ExoPlayer makePlayer(Activity activity, String url, String browserUserAgent) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(15000);
        if (browserUserAgent != null && !browserUserAgent.trim().isEmpty()) {
            http.setUserAgent(browserUserAgent);
        }
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null && !cookies.trim().isEmpty()) {
            Map<String, String> props = new HashMap<>();
            props.put("Cookie", cookies);
            http.setDefaultRequestProperties(props);
        }
        DefaultDataSource.Factory source = new DefaultDataSource.Factory(activity, http);
        DefaultMediaSourceFactory media = new DefaultMediaSourceFactory(source)
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6));
        DefaultLoadControl control = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(12000, 30000, 2000, 4000).build();
        return new ExoPlayer.Builder(activity)
                .setLoadControl(control)
                .setMediaSourceFactory(media)
                .build();
    }

    private static int status(PlaybackException error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) {
                return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
            if (t == t.getCause()) break;
            t = t.getCause();
        }
        return 0;
    }

    public static boolean canRetry(PlaybackException error) {
        int response = status(error);
        if (response != 0) {
            return response == 408 || response == 429 ||
                    (response >= 500 && response <= 599);
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
    }

    public static String safeError(PlaybackException error) {
        int response = status(error);
        if (response != 0) return "HTTP_" + response;
        // No stream URL, authorization token or subscriber data in diagnostics.
        return error.getErrorCodeName();
    }
}
