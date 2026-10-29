# Prime Player — LAB 6: mesma sessão no mini-player, tela cheia e pop-up

## Causa evidenciada pelo vídeo
No arquivo de teste, Band SP HD reproduz na grade do site e, após clicar em tela cheia, a Activity ExoPlayer (vídeo preto inicial) carrega outra sessão de mídia. Isso pode causar recusa por limite de sessões e mudança aparente de programação.

## Solução do APK
- O método público `PrimeAppsNative.playLive(url,title,format)` **não inicia outro player nativo**. Ele usa `PrimeAppsSamePlayer.enter('fullscreen')` para ampliar o mesmo elemento `<video>` existente na WebView, sem alterar `src`, `currentSrc`, `MediaSource` ou instância Hls.js/mpegts.js.
- `PrimeAppsNative.playPopup(url,title,format)` usa esse mesmo elemento em modo flutuante DENTRO do aplicativo, com controle de pausa, tela cheia e fechar; não cria outro socket.
- O vídeo volta ao estilo inicial ao fechar (inclusive via botão Voltar/D-pad). Os favoritos, categorias, IndexedDB e histórico permanecem no mesmo site.
- Se não houver elemento `<video>` ativo acessível (por exemplo, o site destruiu o elemento ao acionar fullscreen, ou a reprodução está em iframe isolado), retorna erro `ACTIVE_WEB_VIDEO_NOT_FOUND` e **não tenta abrir outra sessão silenciosamente**.
- Filmes e séries preservam sua rota existente para o ExoPlayer nativo; este LAB altera somente o comportamento padrão de live TV.

## A integração da Lovable é obrigatória para resultado integral
O site publicado ainda precisa ser atualizado. A partir do mini-player, o botão Tela Cheia/POP-UP deve **manter montado o mesmo componente de vídeo e a instância Hls.js/mpegts.js**, mudando somente a apresentação/layout. Ao chamar `PrimeAppsNative.playLive` ou `playPopup`, NÃO execute `pause()`, `stopLoad()`, `destroy()`, `detachMedia()`, `removeAttribute('src')`, troque `key` de React nem remonte o componente. A ponte Android pode restaurar vídeo pausado, mas não ressuscita um Hls.js já destruído. No fechamento, `primeapps-native-live-result.detail.samePlayer === true`: não recrie a lista nem a conexão, apenas restaure o tamanho original.

Para máxima confiabilidade no próprio site (PWA, navegador, Android), é melhor que a Lovable implemente diretamente a alternância de tela cheia via CSS sobre o MESMO componente de vídeo; usar a ponte nativa é uma compatibilidade para a versão atual do site.

## Evitar falsas promessas
Isto remove a abertura de um **segundo motor pelo botão live**, não corrige HTTP 403, permissões, URLs expiradas nem servidores que retornam somente um clipe .ts de 60 segundos. O modo live nativo separado, quando algum outro caminho o utilizar, continua sujeito às limitações de fonte da Media3. Testar no Android 5–16, TV Box e Fire TV.

## CI
Branch `fix/prime-player-lab6-live-source-20261029`; Gradle 8.13/AGP 8.13.2; Android mínimo 5.0 (minSdk21). GitHub Actions verifica sintaxe do helper JS, compila Java/recursos, valida assinatura debug e publica os dois APKs. Certificado debug não é chave de distribuição estável.
