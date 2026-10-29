# Prime Player LAB 5 — live recovery and web/VOD popup

**Android:** Java WebView + native Media3 ExoPlayer 1.8.1 (minSdk 21, targetSdk 35), appId `com.primeapps.webplayer`.

## Live streaming reliability
- HLS/DASH/TS streaming uses ExoPlayer's continuous segment loading; live streams are **not** downloaded as files to storage.
- Both fullscreen and fallback floating players share a network configuration: web-compatible user-agent, WebView cookies when available, connection/read timeouts and larger bounded buffers.
- Transient network drop, timeout, HTTP 408/429/5xx: limited automatic recovery in ExoPlayer with backoff, up to 4 recoveries per minute after Media3's normal load retries. It does not retry HTTP 401, 403, or 404 endlessly; these indicate unavailable or unauthorized resources.
- On fatal error, return `primeapps-native-live-result` with `ok:false`, `status:"error"`, `error:"HTTP_<status>"` or a generic Media3 code. Do not show an ExoPlayer technical Toast or the Android WebView `A página em https://...` dialog for the known native→web fallback confirmation.
- The Lovable site must handle that event (already reported implemented by Lovable) by offering/starting web fallback without losing the channel/catalog state.

## Popup in movies and TV series
- The native `NativeVideoActivity` always has POP-UP. This works only when the VOD playback is actually routed to the native bridge.
- Added `window.PrimeAppsNative.playPopupVod(url,title,startPositionMs,format)` so Lovable can open VOD directly in the native PiP/floating player; `playVod(url,title,startPositionMs,format)` remains unchanged.
- Added POP-UP to **WebChromeClient HTML5 fullscreen custom view**. If the site enters a genuine WebView HTML5 fullscreen mode, the native overlay button can enter system PiP (Android 8+ where supported) or resize the fullscreen custom view as an in-app popup on older Androids.
- A React/CSS-only fullscreen player does NOT call WebChromeClient.onShowCustomView. Its POP-UP button must be added in the Lovable player UI and call `playPopupVod` or its own web PiP; Android code cannot infer the media URL from an arbitrary site with a blob: URL. Web-only streams/DRM that native ExoPlayer cannot access should remain in the web player.

## Lovable instructions
In the VOD full-screen React component add a focusable `POP-UP` control (D-pad accessible). When the current VOD stream has a legitimate direct URL, if `PrimeAppsNative?.playPopupVod` exists, pause the web media and invoke `playPopupVod(url,title,Math.round(currentTime*1000),format)`. Handle `primeapps-native-vod-result`: restore web playback and `continue watching` progress; if `ok:false`, fall back to web. Do not allow both video engines to play audio simultaneously. If the native bridge is absent, use the existing HTML5 player and platform PiP if supported. Do not hardcode an access token into the APK.

## Verification
GitHub Actions compiles debug and release, checks debug signature, minSdk, targetSdk. Playback needs real-world testing with authorized channel URLs on Android 5–16 and TV boxes. No guaranteed recovery when the server rejects a request or when the media URL expires.
