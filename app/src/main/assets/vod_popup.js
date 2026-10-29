/* Prime Player LAB 7: a single video session for PWA-style PiP in Android WebView.
 * Injected only into the trusted first-party WebView origin.
 * Do not replace src, remount video, instantiate a decoder, or consume a second connection.
 */
(function () {
  "use strict";
  if (window.PrimeAppsVodPopup && window.PrimeAppsVodPopup.version === 7) return;

  var control = null;
  var activeVideo = null;
  var pip = false;
  var delayHandle = null;
  var lastSize = "";
  var videoSelector = "video";
  var controlsSelector = "button,[role=button]";
  var pipWords = /(?:pop\s*[- ]?\s*up|picture\s*[- ]?\s*in\s*[- ]?\s*picture|mini\s*[- ]?\s*player)/i;

  function viewport() {
    return {width: window.innerWidth || 1, height: window.innerHeight || 1};
  }
  function isActuallyVisible(el) {
    if (!el || !el.isConnected) return false;
    var r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var s = window.getComputedStyle(el);
    return s.display !== "none" && s.visibility !== "hidden" && Number(s.opacity || 1) !== 0;
  }
  function largeVideo() {
    var videos = document.querySelectorAll(videoSelector);
    var v = viewport(), best = null, area = 0;
    for (var i = 0; i < videos.length; i++) {
      var el = videos[i];
      if (!isActuallyVisible(el)) continue;
      if ((!el.currentSrc && !el.srcObject && !el.src) || el.ended) continue;
      var r = el.getBoundingClientRect();
      var visibleWidth = Math.max(0, Math.min(r.right, v.width) - Math.max(0, r.left));
      var visibleHeight = Math.max(0, Math.min(r.bottom, v.height) - Math.max(0, r.top));
      var visibleArea = visibleWidth * visibleHeight;
      if (visibleArea > area) {
        best = el;
        area = visibleArea;
      }
    }
    if (!best) return null;
    var rect = best.getBoundingClientRect();
    // Do not overlay the TV catalogue's small preview with a second POP-UP control.
    if (area < v.width * v.height * 0.29 ||
        rect.width < v.width * 0.48 ||
        rect.height < v.height * 0.42) return null;
    return best;
  }
  function siteHasVisiblePopupButton() {
    var nodes = document.querySelectorAll(controlsSelector);
    for (var i = 0; i < nodes.length && i < 120; i++) {
      var el = nodes[i];
      if (control && el === control) continue;
      var name = (el.getAttribute("aria-label") || "") + " " + (el.textContent || "");
      if (pipWords.test(name) && isActuallyVisible(el)) return true;
    }
    return false;
  }
  function makeButton() {
    var b = document.createElement("button");
    b.type = "button";
    b.id = "prime-app-native-vod-popup";
    b.setAttribute("aria-label", "POP-UP do vídeo sem reiniciar reprodução");
    b.setAttribute("title", "Assistir em janela flutuante");
    b.textContent = "▣  POP-UP";
    b.style.cssText = [
      "position:fixed!important",
      "z-index:2147483646!important",
      "top:72px!important",
      "right:16px!important",
      "height:44px!important",
      "min-width:116px!important",
      "padding:0 12px!important",
      "display:none",
      "color:#fff!important",
      "font:600 14px sans-serif!important",
      "border:1px solid #ff3146!important",
      "border-radius:10px!important",
      "background:rgba(30,23,30,.94)!important",
      "box-sizing:border-box!important",
      "cursor:pointer!important",
      "outline-offset:3px!important"
    ].join(";");
    b.addEventListener("click", function (event) {
      event.stopPropagation();
      event.preventDefault();
      if (!activeVideo) return;
      // Native Android PiP works even when the web browser's requestPictureInPicture
      // is unavailable inside Android System WebView.
      var bridge = window.PrimeAppsNative;
      if (bridge && typeof bridge.popupWebVideo === "function") {
        bridge.popupWebVideo();
        return;
      }
      if (window.PrimeAppsSamePlayer && window.PrimeAppsSamePlayer.enter)
        window.PrimeAppsSamePlayer.enter("popup");
    });
    return b;
  }
  function hide() {
    if (control) control.style.setProperty("display", "none", "important");
  }
  function update() {
    if (!document.body || pip) {
      hide();
      return;
    }
    if (!control || !control.isConnected) {
      control = makeButton();
      document.body.appendChild(control);
    }
    activeVideo = largeVideo();
    if (!activeVideo || siteHasVisiblePopupButton()) {
      hide();
      return;
    }
    // A VOD fullscreen player may use a custom toolbar and right-hand close icon.
    // Place this button beneath that X, rather than on top of it.
    var v = viewport();
    var top = v.height < 420 ? "60px" : "72px";
    control.style.setProperty("top", top, "important");
    control.style.setProperty("display", "block", "important");
  }
  function debouncedUpdate() {
    if (delayHandle) return;
    delayHandle = setTimeout(function(){ delayHandle = null; update(); }, 120);
  }
  if (document.body) {
    var observer = new MutationObserver(function (records) {
      for (var i=0; i<records.length; i++) {
        // Only observe page mutations, not our control's own style updates.
        if (control && (records[i].target === control || records[i].target.parentElement === control))
          continue;
        debouncedUpdate();
        break;
      }
    });
    observer.observe(document.body, {subtree:true, childList:true, attributes:true,
      attributeFilter:["class","style","hidden"]});
  }
  window.addEventListener("resize", debouncedUpdate, {passive:true});
  window.addEventListener("play", debouncedUpdate, true);
  window.addEventListener("pause", debouncedUpdate, true);
  window.addEventListener("primeapps-app-pip-changed", function(e) {
    pip = !!(e && e.detail && e.detail.active);
    debouncedUpdate();
  });
  window.PrimeAppsVodPopup = {
    version: 7,
    update: update,
    currentVideo: function(){return activeVideo;},
    isVisible: function(){return !!control && control.style.display !== "none";}
  };
  update();
  // The player may change its CSS dimensions during route transitions without
  // changing DOM children. Poll slowly: suitable for low-memory TV boxes.
  setInterval(update, 1600);
})();
