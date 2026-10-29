(function () {
  "use strict";
  if (window.PrimeAppsSamePlayer && window.PrimeAppsSamePlayer.version === 6) return;

  var state = null;
  var fullscreenZ = 2147483600;

  function currentVideo() {
    var items = document.querySelectorAll("video");
    var eligible = [];
    for (var i = 0; i < items.length; i++) {
      var v = items[i];
      var rect = v.getBoundingClientRect();
      if (rect.width < 50 || rect.height < 30) continue;
      if (!v.currentSrc && !v.srcObject && v.readyState === 0) continue;
      eligible.push(v);
    }
    for (var j = 0; j < eligible.length; j++) {
      if (!eligible[j].paused && !eligible[j].ended) return eligible[j];
    }
    return eligible.length ? eligible[0] : null;
  }

  function button(text, label, handler) {
    var b = document.createElement("button");
    b.type = "button";
    b.textContent = text;
    b.setAttribute("aria-label", label);
    b.style.cssText = "color:#fff;background:#261a20;border:1px solid #ff293e;border-radius:9px;" +
      "font:600 14px sans-serif;padding:10px 13px;min-height:42px;cursor:pointer;";
    b.onclick = function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      handler();
    };
    return b;
  }

  function fire(eventName, details) {
    try { window.dispatchEvent(new CustomEvent(eventName, {detail: details})); }
    catch (ignored) {}
  }
  function recordStyle(el, props) {
    props.push({el: el, css: el.getAttribute("style")});
  }
  function restoreStyle(entry) {
    if (entry.css === null) entry.el.removeAttribute("style");
    else entry.el.setAttribute("style", entry.css);
  }
  function finish() {
    if (!state) return false;
    var prior = state;
    state = null;
    document.removeEventListener("keydown", prior.onKey, true);
    for (var i = prior.styles.length - 1; i >= 0; i--) {
      try { restoreStyle(prior.styles[i]); } catch (ignored) {}
    }
    if (prior.backdrop.parentNode) prior.backdrop.parentNode.removeChild(prior.backdrop);
    if (prior.toolbar.parentNode) prior.toolbar.parentNode.removeChild(prior.toolbar);
    prior.video.controls = prior.oldControls;
    fire("primeapps-native-live-result", {
      ok: true, status: "closed", error: "", ended: false,
      positionMs: Math.max(0, Math.round((prior.video.currentTime || 0) * 1000)),
      samePlayer: true
    });
    return true;
  }
  function resize(mode) {
    if (!state) return;
    var s = state;
    var compact = mode === "popup";
    s.mode = mode;
    var width = Math.min(340, Math.max(210, Math.floor(window.innerWidth * 0.46)));
    var height = Math.round(width * 9 / 16);
    var v = s.video;
    var style = v.style;
    style.setProperty("position", "fixed", "important");
    style.setProperty("display", "block", "important");
    style.setProperty("visibility", "visible", "important");
    style.setProperty("transform", "none", "important");
    style.setProperty("object-fit", "contain", "important");
    style.setProperty("background", "#000", "important");
    style.setProperty("z-index", String(fullscreenZ + 5), "important");
    style.setProperty("margin", "0", "important");
    style.setProperty("padding", "0", "important");
    style.setProperty("border", "0", "important");
    style.setProperty("max-width", "none", "important");
    style.setProperty("max-height", "none", "important");
    style.setProperty("min-width", "0", "important");
    style.setProperty("min-height", "0", "important");
    style.setProperty("width", compact ? width + "px" : "100vw", "important");
    style.setProperty("height", compact ? height + "px" : "100vh", "important");
    style.setProperty("top", compact ? "auto" : "0", "important");
    style.setProperty("left", compact ? "auto" : "0", "important");
    style.setProperty("right", compact ? "14px" : "0", "important");
    style.setProperty("bottom", compact ? "60px" : "0", "important");
    s.backdrop.style.display = compact ? "none" : "block";
    s.toolbar.style.cssText = "position:fixed!important;z-index:2147483641!important;" +
      "display:flex!important;gap:8px;align-items:center;justify-content:flex-end;" +
      "right:14px;" + (compact ? "bottom:" + (height + 64) + "px;" : "top:14px;") +
      "background:rgba(10,7,10,.86);padding:6px;border-radius:11px;";
    s.sizeButton.textContent = compact ? "⛶ Tela cheia" : "▣ Pop-up";
    s.sizeButton.setAttribute("aria-label", compact ? "Tela cheia no mesmo player" : "Mini-player no mesmo player");
  }
  function keyHandler(ev) {
    if (ev.key === "Escape" || ev.key === "BrowserBack" || ev.key === "Backspace") {
      ev.preventDefault();
      ev.stopImmediatePropagation();
      finish();
    }
  }
  function enter(mode) {
    if (state) {
      resize(mode === "popup" ? "popup" : "fullscreen");
      return "same";
    }
    var video = currentVideo();
    if (!video) {
      fire("primeapps-sameplayer-error", {error:"ACTIVE_WEB_VIDEO_NOT_FOUND"});
      return "missing";
    }
    var styles = [];
    recordStyle(video, styles);
    var ancestor = video.parentElement;
    var hops = 0;
    while (ancestor && ancestor !== document.body && hops < 16) {
      recordStyle(ancestor, styles);
      ancestor.style.setProperty("overflow", "visible", "important");
      ancestor.style.setProperty("transform", "none", "important");
      ancestor.style.setProperty("contain", "none", "important");
      ancestor.style.setProperty("z-index", String(fullscreenZ), "important");
      ancestor.style.setProperty("position", "relative", "important");
      ancestor = ancestor.parentElement;
      hops++;
    }
    var backdrop = document.createElement("div");
    backdrop.style.cssText = "position:fixed!important;inset:0!important;" +
      "background:#000!important;z-index:2147483580!important;";
    document.body.appendChild(backdrop);
    var toolbar = document.createElement("div");
    var sizeButton = button("", "Alternar tamanho do mesmo vídeo", function(){
      if (state) resize(state.mode === "popup" ? "fullscreen" : "popup");
    });
    var playButton = button("⏯", "Pausar ou reproduzir", function(){
      if (video.paused) { var p = video.play(); if(p && p.catch) p.catch(function(){}); }
      else video.pause();
    });
    var closeButton = button("✕ Fechar", "Voltar ao catálogo", finish);
    toolbar.appendChild(playButton);
    toolbar.appendChild(sizeButton);
    toolbar.appendChild(closeButton);
    document.body.appendChild(toolbar);
    var oldControls = video.controls;
    video.controls = true;
    state = {video:video, styles:styles, backdrop:backdrop, toolbar:toolbar,
      sizeButton:sizeButton, oldControls:oldControls, mode:"", onKey:keyHandler};
    document.addEventListener("keydown", keyHandler, true);
    resize(mode === "popup" ? "popup" : "fullscreen");
    if (video.paused && !video.ended) {
      var promise = video.play();
      if (promise && promise.catch) promise.catch(function(){
        fire("primeapps-sameplayer-error", {error:"WEB_STREAM_PAUSED_OR_RELEASED"});
      });
    }
    closeButton.focus();
    fire("primeapps-sameplayer-open", {samePlayer:true, mode:mode});
    return "same";
  }
  window.PrimeAppsSamePlayer = {
    version: 6,
    enter: enter,
    exit: finish,
    isActive: function(){return !!state;},
    getVideo:currentVideo
  };
  window.addEventListener("pagehide", function(){ if (state) finish(); });
})();