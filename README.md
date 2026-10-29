# Prime Player LAB 8: exclusively WebView browser players

Removed native ExoPlayer and its Media3 dependencies, player Activity and floating player.

Same HTML5 video with HLS.js / mpegts.js from Lovable for TV, movies, and series.

playVod and playLive now resize the video already playing, without changing src or opening another connection.

playPopup, playPopupVod, popupWebVideo show that same video using Android Activity PiP where supported, or an internal popup on legacy devices.

Android WebView cannot be assumed to support Chrome's video.requestPictureInPicture API. The Activity PiP alternative reuses the same WebView session.

To work properly, Lovable must create the web HTMLVideoElement before calling playVod and must not destroy/remount HLS.js/mpegts.js on fullscreen change.

The native floating refresh button remains removed; use MENU to reload.

minSdk 21 Android 5; versionCode 8; Android 5-16 target, real device validation still required.

Debug APK is signed only for testing; stable client updates require same signing certificate.

No second media engine, no Media3, no NativeVideoActivity, no FloatingPlayback.