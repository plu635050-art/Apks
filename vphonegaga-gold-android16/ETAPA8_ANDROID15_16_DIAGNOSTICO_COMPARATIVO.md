# ETAPA 8 — Auditoria de compatibilidade Android 14 versus 15/16 (28/10/2026)

## O que foi auditado
- Fonte canônica: pacote privado 'VPhoneGaGa Gold - Engenharia Reversa', já extraído com quatro DEX e 13.286 arquivos Java JADX, sem refazer a descompilação.
- No `MyNativeActivity.onCreate()`, por volta das linhas 3041–3044, ocorrem as chamadas JNI `VPhoneGaGaLibHelper.InitEngine()` e `mLibHelper.InitializeInstance(...)`.
- `VPhoneGaGaLibHelper.OnGaGaEngineError()` encaminha o erro nativo ao callback Java; `MyNativeActivity.OnGaGaError()` recebe o código e reporta, mas não o cria.
- `ENGINE_ERROR_VERIFY_FAIL = 51` está definido em `VPhoneGaGaLibHelper.java`. Não prova qual condição foi rejeitada.
- O APK corrigido apenas para o ContentProvider de verificação Java abre no Android 16, com as ROMs recolocadas pelo usuário, mas o motor retorna 51.
- O `titan.log` enviado não foi decodificado: é binário de alta entropia empacotado pelo método nativo `packLogs`.

## Verificações do SDK, ABI e linker
- APK original target SDK 29, acima do limite mínimo 24 do Android 15: a restrição de instalação de target antigo **não explica** esse defeito.
- Nas comparações **Java explícitas** `Build.VERSION.SDK_INT >= 35/36`, não foram encontrados bloqueios. Pode haver lógica escondida em JNI ou ofuscação.
- `libVPhoneGaGaLib.so` original: alinhamento ELF PT_LOAD **64 KB**.
- `libuserkernel64.so`: PT_LOAD **4 KB**.
- `libOpenglRender.so`: PT_LOAD **4 KB**.
- 14 das 16 ELF antigas não vazias têm segmentos de carga com 4 KB, incluindo módulos 32-bit que não necessariamente são carregados em processos de 64-bit. Atualizar todo o conjunto sem diagnóstico não é justificado.
- Android 15 **passou a suportar** kernels 16 KB, mas não os impõe em todos os aparelhos. Sem `getconf PAGE_SIZE` do Poco X7 Pro, **não existe prova de incompatibilidade por páginas**.
- `libuserkernel64.so` do Gold contém textos `ro.build.version.sdk`, `KernelPageSize`, `Seccomp`, `PTRACE_SYSCALL`; a presença dessas strings não comprova que a syscall correspondente falhe no Android 16.

## Conclusões, limites e decisão
- O caminho do erro demonstrado é JNI -> callback Java; **a condição originadora ainda não foi localizada** na biblioteca nativa protegida.
- Diferenciar (a) verificação de integridade/certificado/condição do motor, (b) problema de linker/execução nativa, (c) 16 KB de página apenas quando realmente presente.
- A prova decisiva é logcat no período de boot e tamanho de página do mesmo aparelho. Foi criado `coletar_motor_gold_android15_16.sh`, que não altera APK nem ROMs.
- Comparar, se viável, **o mesmo APK** com as mesmas ROMs e assinatura no Android 14 e 16 para isolar versão do SO.
- **Nenhum APK foi gerado nesta etapa**, pois não há patch de motor com causa comprovada; não reivindicar boot funcional.
- Binários privados não foram enviados ao GitHub público.

Relatório completo com sequência Java e hipóteses controladas está salvo na Biblioteca como `VPhoneGaGa_Gold_ETAPA8_ANDROID15_16_AUDITORIA.md`.

Fontes Android: https://developer.android.com/about/versions/15/behavior-changes-all e https://source.android.com/docs/core/architecture/16kb-page-size/16kb
