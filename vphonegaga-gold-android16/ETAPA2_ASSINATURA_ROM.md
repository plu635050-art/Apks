# Etapa 2 — VPhoneGaGa Gold: assinatura e ROMs (28/10/2026)

**Status:** auditoria estática executada sobre o APK ORIGINAL, sem modificação. A causalidade do fechamento no Android 16 ainda depende do logcat real.

## Integridade da comparação

- APK original: 913.981.981 bytes, SHA-256 `5fd7b0bde058b83e8ea497476fcc0798e8d2c4e33af02ed1b15d3c823487ccc5`.
- APK original: 2.422 entradas. LAB 3: 2.417 entradas.
- Exatamente cinco ausentes no LAB 3: `assets/androidfs_10.0.0.7z`, `assets/androidfs_7.1.2.7z`, `META-INF/KEY0.RSA`, `META-INF/KEY0.SF`, `META-INF/MANIFEST.MF`.
- Os 2.417 arquivos comuns são idênticos até nos bytes **comprimidos**, verificados por SHA-256. Todos os 4 DEX, manifesto e configuração são iguais.
- Certificado original: `CN=Goxome,O=Goxome`; SHA-256 `0e92324e5546f5e21dfe5d1885bbacf85d2d574badf1563bf19fc51aa59ba9ae`.
- Certificado de cópia reempacotada: `CN=np`; SHA-256 `492682f877607ee99df2ddd2bd5953fd727bdf6e19d397de9dbbafd582bcad75`.
- O `META-INF/MANIFEST.MF` original contém digests das duas ROMs.

## Evidência direta de verificação interna

No `classes4.dex` ORIGINAL, `Larm/chajv;`:
- estende `ContentProvider`, implementa `Runnable`, está declarado no manifesto;
- `onCreate()` inicia uma `Thread`;
- `run()` obtém dados de assinatura por reflexão e contém `Signature.toByteArray` (2x), `X509Certificate.getEncoded`, `Base64.encodeToString`, `String.equals` e `System.exit` em caminhos distintos.

Isso é **forte evidência de uma verificação de certificado capaz de encerrar o processo na inicialização**. Não prova ainda qual condição específica causou a falha observada no telefone. Não alterar essa proteção por tentativa.

## Evidência de dependência das ROMs

Em `classes2.dex`, `RomManager.getAssetRomVersion()` usa `Context.getAssets`, `AssetManager.list`, `startsWith` e `endsWith` para detectar e analisar os arquivos. `extractRom()` conecta ao `VPhoneGaGaLibHelper` e agenda extração.

A ausência das ROMs pode ser uma **segunda causa independente**. O manifesto declara classes `VPhoneInstance1..48`, sem comprovar 48 VMs simultâneas.

## Próxima validação isolada

Comparar A: APK íntegro com ROMs e certificado original; B: APK com ROMs, reassinado por chave de testes **autorizada**; C: APK sem ROMs, reassinado pela **mesma** chave de B. **Inserir as ROMs antes de assinar**; não modificar o ZIP após a assinatura. Guardar logcat de cada caso e `getconf PAGE_SIZE`. **Não** reutilizar o DEX4 alterado do LAB 5/6/7.

A atualização do motor Android 16, aceitação de nova assinatura e o boot da ROM ainda **não estão implementados/comprovados**.

**Ferramenta usada nesta etapa:** auditoria de ZIP/PKCS#7/DEX e referências de instruções com Python; o REA no GitHub Actions foi executado anteriormente sobre o PrimeApps VM, mas ainda **não** sobre o APK original do Gaga Gold (não foi enviado ao runner). Os binários originais não foram publicados no GitHub.
