# Prime Player LAB 7 — Limpeza de controles + PiP da WebView

## Mudanças reais
1. **Refresh**: excluído o botão 🔃 fixo que encobria Voltar/Fechar e aparecia em qualquer tela. A atualização continua acessível pelo botão MENU do controle remoto, com a opção "Atualizar página". Não apaga IndexedDB/localStorage.
2. **POP-UP nos filmes e séries**: incluído `vod_popup.js` na WebView do endereço confiável, com um botão compacto a 72px do topo, abaixo do botão X. Somente aparece quando há um `<video>` realmente visível ocupando grande parte da tela e quando não há outro botão POP-UP visível. Não mostra na grade ou no mini-player de TV, e não substitui controles do site.
3. **Mesmo vídeo durante PiP**: o botão chama `PrimeAppsNative.popupWebVideo()` na mesma Activity. Se o sistema suporta PiP (Android 8+ em aparelho compatível), entra em PiP sem criar nova instância de ExoPlayer, sem trocar `src` e sem conectar outra vez ao servidor. Em Android 5–7 ou TV que não suporta PiP do sistema, usa o modo flutuante interno de `PrimeAppsSamePlayer.enter('popup','vod')`. Fechar esse fallback devolve evento de VOD, preservando progresso e sem reiniciar a fonte.
4. **Botões responsivos**: os controles nativos adicionais de POP-UP foram deslocados para baixo da região de fechamento superior; não cobrem o X. A barra nativa de atualização não existe mais. Controles internos que o site constrói e esconde por CSS/React precisam de revisão no projeto Lovable; o APK não tem o código-fonte dessas telas para alterar com precisão.

## PWA versus APK
- Chrome/PWA pode usar `HTMLVideoElement.requestPictureInPicture`, ausente ou indisponível no Android System WebView. O APK usa o método `Activity.enterPictureInPictureMode` por ponte nativa.
- A exibição PiP global **não é garantida** em Android 5–7 ou qualquer TV sem recurso. Nesses casos, existe uma janela interna; ela não fica sobre a tela inicial de outros apps.
- Quando React desmonta o elemento HTMLVideoElement ou usa iframe remoto fora do domínio, um mesmo vídeo não pode ser preservado por injeção do wrapper. Nesse caso, a Lovable precisa preservar o componente original ao alterar a apresentação.

## Contratos públicos
- `PrimeAppsNative.playLive(url,title,format)`: continua usando o mesmo vídeo do mini-player (LAB 6).
- `PrimeAppsNative.playPopup(url,title,format)`: mesmo mini-player live flutuante dentro do app.
- `PrimeAppsNative.popupWebVideo()`: novo, usa o vídeo web atual do fullscreen sem outra Activity/stream, com PiP do Android ou fallback interno.
- `PrimeAppsNative.playVod(url,title,startMs,format)` / `playPopupVod(...)`: mantidos para conteúdo VOD nativo.
- Evento no fallback VOD: `primeapps-native-vod-result` com `samePlayer:true`, posição atual e `ok:true`. O site não deve recriar a fonte nessa saída.

## Validação
Workflow `PrimeAppPlayer LAB7 VOD PiP and Clean Controls`: valida ambos JS, compila debug/release, verifica APK e assinatura debug. Testes em dispositivo físico continuam obrigatórios, sobretudo PiP em WebView Android 5–16, TV Box e Fire Stick. Este LAB não muda codecs, transmissão nem autenticação.

## Assinatura e dados
O pacote permanece `com.primeapps.webplayer` com `minSdk 21` (Android 5.0); `versionCode 7`. Assinaturas debug diferentes podem impedir atualização direta do LAB 6. Assine com a mesma chave para preservar os dados locais; não desinstale sem exportar as listas.
