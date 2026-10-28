# ETAPA 6 — Comparação nativa direta Gaga Gold x VPhoneOS (28/10/2026)

**Escopo:** comparação estática dos arquivos ELF ARM64 já extraídos dos APKs do usuário. Nenhum APK novo criado, nenhum motor alegado funcional.

## Inventário exato

- Gold: **16** bibliotecas ELF não vazias.
- VPhoneOS 5.2.1: **33** bibliotecas ELF não vazias.
- Bibliotecas em comum pelo nome: **15**.
- Bibliotecas exclusivas do VPhoneOS: **18**.
- Exclusiva do Gold: `libgdbserver.so`.

Exclusivas do VPhoneOS:

`libXlorie.so`, `libglib-2.0.so`, `libintl.so`, `liboboe.so`, `libsdk29helper.so`, `libsteamhelper.so`, `libtranslator.so`, `libtranslator_x32.so`, `libtranslator_x32_fex.so`, `libtranslator_x64.so`, `libtranslator_x64_fex.so`, `libuserkernel32emu.so`, `libuserkernel32emu_x86.so`, `libuserkernel64emu_x64.so`, `libuserkernelemu.so`, `libvkcompat.so`, `libvulkan_freedreno.so`, `libzstd-jni-1.5.7-11.so`.

## Bibliotecas centrais

| Nome | Gold, bytes | VPhoneOS, bytes | Dependências dinâmicas (DT_NEEDED) relevantes |
|---|---:|---:|---|
| libVPhoneGaGaLib.so | 3.744.096 | 10.363.368 | Gold: liblog/libz/libstdc++/libm/libc/libdl. VPhoneOS: adiciona liboboe/libandroid/libOpenSLES/libEGL/libGLESv2/libjnigraphics/libc++_shared. |
| libuserkernel64.so | 4.166.952 | 7.795.072 | Ambos: libz, liblog, libm, libdl, libc. Versões binárias diferem. |
| libloader64.so | 200.304 | 239.880 | Ambos: liblog, libm, libdl, libc. |
| libuserkernel.so | 3.795.344 | 7.102.260 | Ambos: libz, liblog, libm, libdl, libc. |
| libuserkernelemu.so | inexistente | 7.642.744 | libz, liblog, libm, libdl, libc. |
| libXlorie.so | inexistente | 3.524.528 | GLESv2, android, log, m, z, EGL, dl, libc. |
| libvkcompat.so | inexistente | 4.204.056 | android, log, m, dl, libc. |

Símbolos JNI visíveis de `libVPhoneGaGaLib.so`:
- Gold: 1 símbolo `JNI_OnLoad`.
- VPhoneOS: 114 símbolos `JNI_OnLoad` ou `Java_*`.
- Isso não é a contagem definitiva das funções nativas: `JNI_OnLoad` pode registrar outras funções com `RegisterNatives`, e símbolos podem ser ocultos.

## Consequências

1. **Não basta copiar as 18 novas .so**: sem chamadas Java/serviços correspondentes, podem nunca ser usadas.
2. **Não basta sobrescrever as 15 em comum**: ABI C/C++, layout de estruturas, JNI, contrato do guest AndroidFS e configuração do motor podem ser incompatíveis.
3. **A biblioteca nova tem dependências que a versão antiga não tinha**; as novas chamadas JNI incluem outras classes do aplicativo.
4. **Erro 51 = ENGINE_ERROR_VERIFY_FAIL** no motor Gold. A disponibilidade de mais bibliotecas não comprova que o erro 51 desaparecerá; é necessário observar qual condição falhou.
5. **Alinhamento**: 14 das 16 bibliotecas Gold apresentam PT_LOAD de 4 KB; 33/33 da versão VPhoneOS têm alinhamento adequado para 16 KB. Isso importa se o aparelho usa páginas 16 KB, o que ainda não foi verificado no Poco X7 Pro.
6. **Licenças**: os binários proprietários não devem ser republicados em novo APK comercial sem autorização. Engenharia reversa pode fundamentar design de interoperabilidade, mas não representa direito automático de redistribuição.

## Decisão técnica para uma única versão final

**Não realizar substituição em massa.** Priorizar no mesmo workspace:
- Inventariar todas as funções nativas chamadas pelos DEX do Gold, suas assinaturas e sua compatibilidade com a interface nova.
- Mapear DT_NEEDED e JNI do motor VPhoneOS para descobrir quais módulos são necessários em conjunto.
- Determinar o formato real das ROMs e o contrato JNI de boot; a abertura do VPhoneOS não comprova que aceite os arquivos androidfs do Gold.
- Trabalhar com motor legalmente utilizável ou implementação independente e só depois assinar/compilar um único candidato integrado.
- Critérios finais: inicia em Android 16 com a assinatura prevista, boot do Android 10, inicialização do Android 7, persistência de dados, 2/3/4 máquinas simultâneas sem partilha involuntária de userdata.

**O APK Gold atual com assinatura corrigida abre a interface e falha com código 51, enquanto o VPhoneOS abre no mesmo aparelho.** Não atribuir a causa especificamente ao linker de 16 KB sem `getconf PAGE_SIZE` e logcat.
