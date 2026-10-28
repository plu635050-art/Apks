# VPhoneGaGa Gold 3.4.0 (Olivia) — Android 16

**Estado:** auditoria original concluída; motor ainda **não foi atualizado nem testado**.

## Base de referência verificável

- Original remontado dos 5 fragmentos: `VPhoneGaGa_3.4.0_Olivia_ORIGINAL.apk`.
- Tamanho: **913.981.981 bytes**.
- SHA-256: `5fd7b0bde058b83e8ea497476fcc0798e8d2c4e33af02ed1b15d3c823487ccc5`.
- Pacote `com.vphonegaga.titan`.
- ROM Android 7: `assets/androidfs_7.1.2.7z` (345.458.715 bytes).
- ROM Android 10: `assets/androidfs_10.0.0.7z` (505.428.529 bytes).
- Das 16 bibliotecas nativas ELF ARM64, **14 têm alinhamento PT_LOAD de 4096 bytes** e 2 têm 65536.
- O VPhoneOS 5.2.1 possui 33 bibliotecas nativas no split ARM64; as versões com nomes comuns diferem em hashes, tamanho e JNI.

## Problemas que precisam ser isolados

1. **Assinatura/estrutura ZIP/DEX:** tentativas anteriores falharam ao carregar `goxome.tW` ou o XML após reempacotamento; não extrapolar o erro para o original.
2. **Kernel/ELF:** medir `getconf PAGE_SIZE` no Poco X7 Pro. Android 16 não implica 16 KB. Não repetir `android:pageSizeCompat` sem evidência.
3. **Inicialização:** capturar logcat da execução do **APK original íntegro**, com certificado original, para separar falhas de linker, SELinux, ART, fork/exec, EGL e motor.
4. **Compatibilidade JNI:** comparar métodos realmente registrados, incluindo `JNI_OnLoad`; símbolos JNI visíveis não abrangem registros dinâmicos.
5. **ROM:** manter os dois arquivos originais, sem extraí-los, alterar seu hash ou copiá-los entre aplicativos até investigar formato e contrato de boot.

## Regras de desenvolvimento

- Não copiar bibliotecas proprietárias de um aplicativo para o outro e chamar de modernização.
- Não alterar o alvo SDK ou certificar APK “funcional” apenas porque compilou.
- Não tocar na branch `main`, nem substituir os APKs originais neste repositório público.
- O REA no Actions já analisou PrimeApps VM próprios, **não o VPhoneGaGa Gold original**. Para inspecionar este APK com REA, é preciso disponibilizar uma cópia autorizada ao runner sem publicar material proprietário; nenhuma etapa deste branch afirma tê-lo executado.
- Criterio de LAB de motor aprovado: assinatura válida, instalação e abertura real no Android 16, boot da ROM Android 10, exibição e interação. Múltiplas VMs somente depois disso.

## Como coletar o diagnóstico

Execute no Android 16 com permissão root e com o pacote original `com.vphonegaga.titan` instalado:

```sh
su -c 'sh /sdcard/Download/coletar_erro_android16.sh'
```

O diagnóstico fica em `/sdcard/Download/VPhoneGaGa_Gold_Android16_Diagnostico.txt`. Revise o arquivo antes de compartilhar.
