# Etapa 7 — Triagem de uma correção isolada para o motor Android 16

Data: 28/10/2026
Estado: **investigação sem novo APK**, porque ainda não foi demonstrada a condição específica que retorna ENGINE_ERROR_VERIFY_FAIL (51).

## Evidências reproduzíveis na base original

- APK Gold corrigido para abertura e reassinado: aplicativo abre a interface e chega ao motor; motor devolve **51**, string **initialize feature fail!**.
- Java original `classes3.dex`, `com.vphonegaga.titan.VPhoneGaGaLibHelper`: `ENGINE_ERROR_VERIFY_FAIL = 51`. Falhas de ROM ausente (50), ROM corrompida (23) e kernel não carregado (45) possuem códigos distintos; isso **não prova** que a ROM está boa, apenas que a categoria retornada foi verificação.
- `libVPhoneGaGaLib.so` antigo: 3.744.096 bytes, `JNI_OnLoad` exportado em `0x4a6fc`, seção proprietária `.main`, `0x6a8` bytes. A seção `.main` apresenta entropia ~7,893 bits/byte e instruções que não são fluxo ARM64 plausível quando lidas diretamente; `.data` 0x337a40 bytes, entropia ~7,749 bits/byte. **Indício de embaralhamento/empacotamento nativo**; não constitui prova de algoritmo criptográfico nem identifica o teste que falhou.
- O alinhamento dos PT_LOAD da biblioteca `libVPhoneGaGaLib.so` Gold é **0x10000 (64 KB)**, enquanto no VPhoneOS é **0x4000 (16 KB)**. A biblioteca *central* antiga, portanto, já tem alinhamento suficiente para um host de páginas 16 KB, embora **14 das outras 16** bibliotecas Gold tenham 4 KB. Não se deve atribuir o código 51 ao alinhamento da biblioteca central.
- A rotina Java `arm.chajv` de integridade já teve seu registro automático removido de forma isolada, e esse é o candidato que agora chega à interface; **não repetir esse patch**.

## Hipóteses e testes decisivos

1. **Verificação nativa ligada à assinatura/integridade** (hipótese prioritária para o código 51): comparar no **mesmo Android 16** o APK original inalterado e assinado pelo fornecedor, com as ROMs, com a variante já testada e reassinada, ambos com logs. Se a versão original completar boot e a reassinada sempre falhar, é evidência forte da condição de autenticidade do pacote; não implica que a modificação do manifesto resolva o motor.
2. **Outras validações nativas**: configuração, recursos, licença, estado do sistema ou hash da imagem. Logcat com erro de verificação e, se disponível e autorizado, telemetria nativa precisa para apontar a condição.
3. **Compatibilidade Android16 / 16KB**: recolher `getconf PAGE_SIZE`, logs linker e do processo; detectar uma falha ELF concreta antes de alterar bibliotecas. Android16 oferece modo de compatibilidade em alguns dispositivos, mas não garante funcionamento do motor de virtualização.

## Decisão de versão

Não substituir 16 por 33 bibliotecas. Não liberar candidato com uma 'correção 51' presumida. Se uma única condição for identificada, testar primeiro isoladamente a alteração autorizada; somente publicar APK após validar assinatura, instalação, abertura e boot efetivo do Android 10. Preservar as ROMs e os 4 DEX existentes. A base Java/REA já foi decompilada e não será refeita.
