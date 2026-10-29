# Prime Player LAB 9 — player ORIGINAL da Lovable

A imagem cinza com círculo Play e botões "Pause / Pop-up / Fechar" era criada por código de interface injetado pelo wrapper Android em LAB 6-8. Removemos todo esse código para que o APK abra o site publicado sem substituir o player.

## O que está incluso
- Apenas o WebView Android para carregar https://primeappswebplayer.lovable.app/
- HTML, CSS, React, HLS.js, mpegts.js, catálogo, controles e tela cheia originais da Lovable
- Fullscreen HTML5 padrão via WebChromeClient (sem botões adicionados)
- Persistência do IndexedDB, cache, cookies, localStorage e outras funções PWA compatíveis com WebView
- Seleção de arquivos locais (upload M3U etc. pelo input de arquivo do site)
- Orientação horizontal, sem barra de URL, foco D-pad do site, instalação em TV Box sem touch
- Atualização da página somente através da tecla MENU/F5 > Atualizar; não limpa storage

## Removido
- Classes e dependências ExoPlayer/Media3
- "same_player.js" e "vod_popup.js" (e todos os controls overlays que desenhavam outro player)
- Ponte `PrimeAppsNative`, para que a Lovable escolha naturalmente o player web que usa no PWA
- PiP nativo, janelas flutuantes e POP-UP extras
- Botão de atualização flutuante sobre a interface

## Importante
Como a Lovable continua sendo um site remoto, é possível que o próprio site inclua um botão POP-UP. Esse botão é parte da Lovable e não deve ser retirado por scripts do APK; altere a interface da Lovable para escondê-lo no perfil TV se desejar. O Android WebView não é o mesmo navegador Chrome do PWA, podendo diferir em codecs e APIs; identidade de interface não garante reprodução idêntica em todos os Android antigos.

Android minSdk21 = Android 5.0. package com.primeapps.webplayer, versionCode9. A assinatura debug gerada no GitHub Actions pode mudar; atualizações sem perda de dados exigem a mesma chave de assinatura.
