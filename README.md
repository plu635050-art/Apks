# Prime Player LAB 10 — Auditoria de carregamento e otimização segura

## Problema relatado
Os canais, filmes e séries demoram mais para iniciar no APK WebView do que no PWA aberto no Chrome. O APK não controla as requisições HLS.js/mpegts.js do site nem os servidores remotos, por isso NÃO é tecnicamente correto prometer velocidade idêntica só alterando o wrapper nativo.

## Melhorias LAB 10
1. A tela inicial deixa de manter o spinner quando o primeiro conteúdo da página já foi renderizado (`onPageCommitVisible`, Android >= 6 / API 23), mesmo que imagens ou recursos secundários ainda estejam carregando. Em Android 5 continua usando `onPageFinished`.
2. Prioridade de renderizador WebView explicitamente mantida em IMPORTANT no Android >= 8 para evitar rebaixamento durante reprodução; não é uma prioridade maior que a padrão do Android.
3. Mantemos exatamente `WebSettings.LOAD_DEFAULT` para cache HTTP e Service Worker/cache original, não `LOAD_NO_CACHE` e não `LOAD_CACHE_ELSE_NETWORK` (que pode deixar playlists, autorizações e dados de vídeos obsoletos).
4. Instrumentação passiva de tempo de carregamento HTML5: registra diferença aproximada entre `loadstart` e `playing`, tentativas de vídeo, eventos de buffering (`waiting`). O script NÃO cria vídeo, não altera src, não coloca player, não manipula HLS.js/mpegts.js e não registra credenciais nem URL dos canais.
5. MENU/F5 -> **Diagnóstico** mostra versão real do WebView, primeira imagem da página, tempo até DOM, eventos de buffering e se o Service Worker controla a página. Útil para distinguir problema de WebView antigo, servidor lento e catálogo muito pesado. O relatório é local, não enviado a servidores.
6. Todos os controles, estilo, mini-player, sessão, favoritos, histórico, IndexedDB, Cache API, função upload e orientação horizontal continuam sendo responsabilidade da Lovable.

## Próxima otimização no site Lovable (necessária para acelerar de fato os streams)
- Cronometrar seleção de canal -> primeiro frame de vídeo em PWA e Android com a MESMA fonte e Wi-Fi, separando resolução de lista, preparação de vídeo e resposta do servidor.
- Evitar parsear 30 mil filmes/9 mil séries e 900 canais novamente a cada aba; indexar em IndexedDB, virtualizar grid e carregar somente os itens visíveis.
- Gerar thumbnails menores com Lazy Loading / IntersectionObserver; não carregar todas as capas ao abrir catálogo.
- Reutilizar a mesma instância de Hls.js/mpegts.js ao abrir tela cheia, preservar mini-player e evitar conexões duplicadas.
- Não aplicar pré-carregamento indiscriminado a streams IPTV: isso poderia violar limites de sessões do servidor e deixar a inicialização mais lenta.
- A função de buffer dos players deve ser medida por perfil de dispositivo, especialmente TV Box de 1GB.

## Compilação e assinatura
GitHub Actions gera debug assinado para teste e release sem assinatura (para assinar com sua própria chave). `com.primeapps.webplayer`, versionCode 10, minSdk 21 (Android 5). Confirmação funcional exige testes em dispositivos, porque WebView Chrome/PWA e WebView do TV Box não têm sempre o mesmo motor ou codecs.

## Como coletar um teste útil
1. No APK LAB10, abrir MENU > Diagnóstico após abrir um canal e filme.
2. Fazer a mesma abertura no PWA no mesmo Wi-Fi, comparar tempo de iniciar vídeo.
3. Ver se a versão do WebView está atualizável no aparelho. Enviar tempos registrados sem colar URLs de vídeo com tokens.
