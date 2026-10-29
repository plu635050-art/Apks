# Prime App Player LAB 3 — Live TV fullscreen em ExoPlayer

## Compatibilidade com LAB 2
- Mantém a UI na WebView: TV ao vivo com mini-player HLS.js/mpegts.js, foco D-pad e persistência IndexedDB.
- Mantém o pacote Android `com.primeapps.webplayer`; `capacitor.config.json` **não é utilizado** porque este APK é Java + WebView, e não um container Capacitor.
- ExoPlayer Media3 1.8.1 é chamado **apenas por demanda** para tela cheia: VOD usa `playVod(url,title,startMs,format)`; live usa `playLive(url,title,format)`.
- Abre outra Activity nativa. A Lovable deve **pausar o mini-player antes de chamar `playLive`** e retomá-lo quando receber `primeapps-native-live-result` (ou após falha), evitando áudio duplo.

## Contrato da ponte nativa

```js
function openLiveFullscreen(url, title, format = "hls") {
  const native = window.PrimeAppsNative;
  if (native && typeof native.playLive === "function") {
    // pauseWebLivePlayer(): manter ID, lista/categoria e posição, mas parar o áudio
    pauseWebLivePlayer();
    native.playLive(url, title, format); // 'hls' | 'dash' | 'ts' | ''
    return;
  }
  openExistingWebFullscreen(); // comportamento atual em browser/desktop/PWA
}

window.addEventListener("primeapps-native-live-result", (e) => {
  // fechar tela cheia nativa devolve o usuário à mesma lista e canal
  // e.detail = { positionMs, ended, error }
  resumeWebMiniPlayer(); // retomar stream quando conveniente
  if (e.detail.error) showExistingPlayerError(e.detail.error);
});
window.addEventListener("primeapps-native-player-failed", () => resumeWebMiniPlayer());
```

Esse trecho é **contrato ilustrativo**, não deve ser colado literalmente sem adaptar as funções aos componentes reais da Lovable. A implementação da página deve incluir detecção segura da ponte, pausa, retorno e fallback para tela cheia web quando não há ponte ou quando o motor nativo falha.

## Format e limitações
- `hls`/m3u8: suporte ExoPlayer HLS.
- `dash`/mpd: suporte ExoPlayer DASH.
- `ts`: formato progressivo MPEG-TS (a detecção de container é automática); streams HTTP longos exigem teste de campo.
- Canal com protocolo não suportado pela Media3, stream protegido ou cabeçalhos especiais pode precisar continuar em HLS.js/mpegts.js. Áudio AC3/EAC3 não é garantido em todos os decodificadores do Android.
- Android 5–17 é objetivo de instalação; estabilidade do WebView, codecs, TV Box e Fire TV requer testes reais.
- A assinatura debug do GitHub Actions muda entre runners. Para atualizar LAB 2 sem desinstalar e preservar dados, use uma keystore privada persistente, nunca publique a chave no GitHub.

## CI
Branch: `lab/primeapps-webplayer-live-native-lab3-20261029`. O GitHub Actions publica APK debug de testes **assinado**, e APK release **sem assinatura**.
