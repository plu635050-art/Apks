# Prime App Player — LAB 2 (Android 5+ com ExoPlayer nativo)
Este branch isolado contém o wrapper WebView do Prime Apps Web Player, mantendo IndexedDB, localStorage, Cache API e controles D-pad existentes na Lovable. O motor nativo Media3 ExoPlayer 1.8.1 é integrado em uma Activity fullscreen, sem exigir Capacitor 6 (que inicia em Android 5.1).
## Integração com Lovable
Apenas VOD deve invocar `window.PrimeAppsNative.playVod(url, title, startPositionMs, format)`, quando a API existir. `format`: `hls`, `dash`, ou string vazia para MP4 e detecção automática. O WebView não redireciona VOD por si só — o código da Lovable precisa chamar a ponte. No browser normal, usar o player web de sempre. Ao fechar, a página recebe `primeapps-native-vod-result` com `event.detail.positionMs` e `event.detail.ended`. É necessário integrar isso ao estado de "Continuar assistindo" na Lovable.
## Build GitHub Actions
Push nessa branch inicia o workflow `PrimeAppPlayer LAB2 build`. O APK `app-debug.apk` recebe assinatura debug AUTOMÁTICA do GitHub runner, adequada para testes e instalações diretas. NÃO é certificado definitivo nem permite atualizar um APK assinado por outro certificado sem reinstalar. A release unsigned é para assinar com chave particular.
## Restrições
Android 5-17 é alvo de instalação, não certificação de testes reais. Dispositivos sem WebView moderno podem não abrir o site. Aceleração de vídeo e formatos dependem dos codecs do dispositivo e do servidor. HLS.js/mpegts.js continuam no site para live TV. ExoPlayer não garante reprodução sem acesso autorizado à fonte.
