# APK Editor Universal — LAB 1

Primeiro laboratorio de compilacao experimental baseado no projeto open source **[APK Explorer & Editor (AEE)](https://github.com/apk-editor/APK-Explorer-Editor)**, sob **GPL-3.0-ou-posterior**. Codigo de origem fixado no commit `988401012e90c5bfbcc800726bc6aa0d76e2c3c2`.

## Compilar no GitHub Actions
1. Abra **Actions > APK Editor Universal - LAB 1 > Run workflow**.
2. Escolha **full** para o editor completo, depois Run workflow.
3. Baixe o artefato `APK-Editor-Universal-LAB1-full` quando a compilacao terminar.
4. Extraia o APK do ZIP. A compilacao debug usa a chave de debug do Android, **nao** a sua chave de assinatura.

## Funcionalidades e limites
AEE fornece edicao XML/texto, visualizacao/substituicao de recursos, Smali->DEX, abertura de APK e instalados, e assinatura. Ainda nao foi adaptado para o layout antigo nem inclui troca automatica de nome e icone por 1 toque. Android minimo upstream: API 23 (Android 6). Seu APK Editor Pro anterior nao e incluido nem redistribuido neste repositorio. Este workflow nao comprova compatibilidade Android 16 ou com todos os APKs: validar nos aparelhos 32/64 e testar recompilacao, assinatura, instalacao e abertura do APK gerado.

## Licenca
Caso modifique e distribua o AEE, mantenha avisos de autoria/licenca GPL e forneca o codigo-fonte correspondente. Veja o repositorio upstream.

## Plano
Etapa 1: build debug e auditoria do pacote.
Etapa 2: testes reais e correcoes de Android 16.
Etapa 3: fluxos simples de troca de nome e icone, preservando XML e referencias.
Etapa 4: interface de edicao rapida, testes ARM32/ARM64, assinatura pessoal.
