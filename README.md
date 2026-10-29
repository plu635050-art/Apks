# PRIME PLAYER – LAB 4 completo

## Objetivo
Este branch contém um APK Android Java/WebView com ExoPlayer/Media3 1.8.1, tela horizontal, foco/D-pad preservado pela Lovable, PWA e cache existente, e ícone novo com símbolo Play reduzido e margens. Android minSdk 21 (Android 5.0). Nome de launcher: **Prime Player**. Versão 1.0.0-lab4.

## POP-UP
O botão "▣ POP-UP" aparece no player fullscreen nativo de canais e VOD.
- Se o sistema fornecer Picture-in-Picture (Android 8+ em celulares compatíveis; Android 14+ em TVs que ofereçam suporte), usa PiP verdadeiro do Android, e o vídeo continua dentro da janela do sistema. Não usa permissão de sobreposição.
- Se PiP não for suportado ou for recusado, a Activity devolve `popupFallback` e o próprio app abre uma **janela flutuante interna sobre a interface Lovable**. Essa janela permite "Tela cheia" e "Fechar", e pausa quando o app vai para segundo plano. Não é PiP global: não acompanha outros aplicativos.
- O botão do site pode iniciar diretamente a abertura com `PrimeAppsNative.playPopup(url, title, format)` em canais ao vivo. A Lovable deve pausar seu mini-player web antes de fazer essa chamada. `playLive(url,title,format)` e `playVod(url,title,startMs,format)` continuam compatíveis.

## Contrato de retorno para a Lovable
O app dispara o evento `primeapps-native-live-result` para TV e `primeapps-native-vod-result` para VOD, com `detail` sendo um objeto JavaScript:
```json
{"positionMs": 0, "ended": false, "ok": true, "status": "closed", "error": ""}
```
Erros são entregues com `ok:false`, `status:"error"` e `error:"EXOPLAYER_ERROR_CODE"`. Isso corresponde à convenção relatada pela Lovable. Nenhum evento de "fechou" é emitido ao trocar da tela cheia para pop-up interno: ele é enviado quando a reprodução finalmente termina ou fecha. Assim, o mini-player web permanece pausado e não existem dois áudios em paralelo. Em pop-up do sistema, o evento é emitido quando a Activity nativa realmente termina.

## Regras
- Não modificar foco D-pad, listas, favoritos nem IndexedDB.
- Codec AC3/EAC3, HLS, DASH, MPEG-TS, HTTPS, streams protegidos ou TV Box antiga ainda precisam de validação por aparelho; o componente não transforma streams incompatíveis em formatos suportados.
- Não se promete PiP global em Android 5–7 nem em todas as TVs.
- Um APK assinado debug **não atualiza** automaticamente outro pacote assinado com chave diferente, mesmo tendo o mesmo applicationId. Para preservar storage entre LABs, a instalação de atualização deve usar a mesma chave/keystore; caso contrário exporte seus dados antes de desinstalar.

## GitHub Actions
Workflow `PrimeAppPlayer LAB4 PiP and Icon` compila debug assinado e release sem assinar. Confere assinatura com `apksigner verify` e pacote com `aapt dump badging` antes de publicar os artefatos.
