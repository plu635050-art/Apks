# LAB2 — APK Editor Pro CLÁSSICO (Heagoo / SteelWorks)

A base agora e o codigo-fonte **timscriptov/ApkEditor** (versao 1.10.0), e NAO o AEE.
A interface e as opcoes principais sao as do editor original, mas a compilacao derivada
nao garante que seja pixel-perfect em relacao a versao 3.0 modificada com App Cloner.

- Fonte: https://github.com/timscriptov/ApkEditor
- Commit: `bddfe5807ce7f03cd59dd568b50853759501adac`
- Aplicativo: `com.gmail.heagoo.apkeditor.pro`.
- Android minimo no codigo: API 14; alvo no codigo: API 26.
- Codigo nativo `libsyscheck`: compilacao prevista para armeabi-v7a, arm64-v8a, x86 e x86_64.
- Executaveis auxiliares `assets/aapt`, `assets/aaptz`, `assets/mycp`: cuidado,
  podem continuar sendo ARM32 e exigir substituicao para recompilar APKs em telefone 64-bit-only.

## Execucao
Actions > APK Editor CLASSICO - LAB2 (32 e 64 bits) > Run workflow.
A acao compila a versao debug, ja assinada com a chave de depuracao temporaria.
Artefato `APK-Editor-CLASSICO-LAB2-32-64` contem APK e auditoria.
Ainda falta testar recursos de edicao e assinatura em aparelhos reais.

## Politica
- Nunca fazer checkout do projeto AEE para este LAB.
- Nunca incluir no repositorio publico os APKs modificados dos testes do usuario.
- Nao substituir executaveis Android ARM32 por binarios Linux/x86 de SDK.
- Nao declarar suporte 64-bit total ate os testes de recompilacao e assinatura funcionarem.
- Preservar codigo e interface originais; priorizar correcao comprovada e teste unitario.
- Nao elevar targetSdk apenas para remover o aviso de versao antiga.
- Workflow manual em main; PR experimentais podem ter verificacao automatica.
- Respeitar direitos de terceiros e licencas dos componentes.
