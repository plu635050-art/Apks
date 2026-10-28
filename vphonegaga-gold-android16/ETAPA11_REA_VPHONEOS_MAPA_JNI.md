# ETAPA 11 — REA + JADX no VPhoneOS e mapa JNI para atualizar Gold
Data: 2026-10-28

**Status:** análise comparativa realmente executada em arquivos recebidos; *nenhum APK corrigido gerado*, motor Gold Android16 ainda não roda guest.

## Evidência
- REA 6.8.0 com JADX bridge 0.7.1 analisou `new_base.apk` do VPhoneOS 5.2.1 (`com.yoyo.snake.rush`) e reconheceu 27.399 classes, 4.938 recursos, `min_sdk=32`, `target_sdk=36`.
- As 4 classes de boot e ROM foram decompiladas do `classes4.dex` VPhoneOS com JADX CLI 1.5.6; Gold já possuía base REA/JADX verificada.
- **Gold `VPhoneGaGaLibHelper`: 34 native métodos. VPhoneOS: 82; 23 com assinaturas iguais, 8 nomes iguais/assinaturas incompatíveis, 3 só Gold e 51 só novo.**
- `InitEngine():void` no Gold vs `InitEnvironment():void` no VPhoneOS.
- `InitializeInstance(Context,ClassLoader):void` Gold vs `InitializeInstance(Context,ClassLoader,MyNativeActivity):boolean` VPhoneOS.
- VPhoneOS possui `ContinueStartEngine(int,boolean)` e caminhos assíncronos de boot.
- VPhoneOS define erro 74 para páginas de 16 KB não suportadas e 68 para testes seccomp, além do erro 51 existente em ambos. São códigos demonstrados; não implicam que o Gold falhe especificamente em 16KB/seccomp.
- Gold inclui 2 ROMs em `assets/androidfs_*.7z`; base APK VPhoneOS não inclui nenhuma, e distribui motor ARM64 em split APK.
- Gold `libVPhoneGaGaLib.so` tem `JNI_OnLoad` visível, JNI pode ser registrado dinamicamente. Nova lib tem 113 símbolos `Java_*` visíveis; a substituição direta sem ponte Java é incompatível.
- `libuserkernel64.so` Gold tem PT_LOAD 4KB, a nova 16KB; sem `getconf PAGE_SIZE` do aparelho não é prova do defeito.
- Em Android16, Gold *reassinado* abre interface mas falha boot com `VERIFY_FAIL=51`. Os testes do Gold em Android14 podem ter assinatura diferente: **assinatura e Android não foram controlados simultaneamente**.

## Resultado persistente (privado, sem publicar código proprietário)

Biblioteca: `/VPhoneGaGa Gold - Engenharia Reversa/Gold_VPhoneOS_COMPARACAO_REAL_JNI_REA_20261028.zip`; inclui relatório, 4 Java decompilados, REA VPhoneOS original e matriz JNI completa; `ETAPA11_MAPA_JNI_GOLD_VPHONEOS.json`.

**Decisão:** não entregar variante com bibliotecas trocadas na sorte; para preservar Gold será necessário construir ponte JNI, portar serviços/fluxo do motor e validar ROM antiga e direitos de uso das libs. Uma interface aberta sem máquina virtual não é APK funcional.
