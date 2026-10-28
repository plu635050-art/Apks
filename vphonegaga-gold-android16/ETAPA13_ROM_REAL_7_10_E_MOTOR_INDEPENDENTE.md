# Etapa 13 — Pré-viabilidade de motor independente

Data 28/10/2026. **Status NO-GO para prometer um APK que rode as duas ROMs fornecidas com root e duas ou mais instâncias simultâneas.**

## Inspeção real do conteúdo das ROMs originais
Os arquivos `androidfs_7.1.2.7z` (345.458.715 bytes, SHA-256 `9560fb4f00859a835b7b991f9cf337a1d6e454d3659e92c122e5d7f4e42caf2b`) e `androidfs_10.0.0.7z` (505.428.529 bytes, SHA-256 `f512d2d9ef025edd033ea54b1e70217269cfd1e47efb9e330ae8e0db97289469`) foram realmente lidos com libarchive no ambiente privado.

- Android 7: 41 arquivos não diretório, `androidfs.bin` = marca `SFDA`, código LE `712`. Estruturas de `root`, `system`, `data`, `cache`.
- Android 10: 55 arquivos não diretório, `androidfs.bin` = marca `SFDA`, código LE `1000`. Estruturas de `root`, `system`, `vendor`, `data`, `cache`.
- Os dois possuem `superblock.bin` = marca `BPUS` e arquivos `.metadata` = marca `ATEM`; grandes blocos `readonly.bin` e `fscache.bin`.
- Nenhuma entrada `boot.img`, `system.img`, `ramdisk.img` ou uma imagem `qcow2` que QEMU possa inicializar diretamente. **Não são discos bootáveis prontos.**
- As bibliotecas proprietárias Gold e VPhoneOS contêm referências a `BPUS`; as mais recentes também a `ATEM`/ `readonly.bin`. O formato precisa de leitor/compatibilidade específica.

## Motores livres avaliados

1. [QubeVM](https://github.com/rhn-1k/QubeVM): QEMU real para Android, GPL-3.0, software emulation TCG funciona sem root do host, mas pode ser lento. Não reconhece androidfs proprietário diretamente.
2. [DroidVM](https://github.com/Droid-VM/DroidVM): QEMU/crosvm GPL-3.0, requer root do host e `/dev/kvm`, `/dev/gzvm` ou hypervisor equivalente para aceleração; não reconhece androidfs diretamente.
3. Android AVF: API restrita de sistema e Microdroid, não opção drop-in para ROM Android 7 e 10 num aplicativo comum.

## Plano com critério GO obrigatório

- A) Implementar um conversor validado do formato `BPUS/ATEM/readonly.bin` em uma imagem de disco real (ou obter imagem AOSP legalmente utilizável feita para emulador ARM).
- B) Compilar motor open-source com licença preservada e demonstrar boot de uma ROM no Android15/16.
- C) Demonstrar root **no Android convidado** e duas máquinas executando *simultaneamente*, cada uma com estado de escrita separado.
- D) Somente depois incorporar UI, múltiplas instâncias e assinar APK.

**Nenhum APK de interface/catálogo sem motor foi gerado.** Não confundir 12 GB de RAM com garantia de desempenho de 2 guests em TCG. A versão VPhoneOS original não é um SDK licenciável só porque disponibiliza bibliotecas compiladas.

## Auditoria reproduzível
O arquivo `auditar_rom_vphone.py` (sem as ROMs) está no projeto; seu JSON de resultados está salvo na Biblioteca privada. Não subir ROMs ou bibliotecas proprietárias ao repositório público.

Fontes:
- https://github.com/rhn-1k/QubeVM
- https://github.com/Droid-VM/DroidVM
- https://www.qemu.org/docs/master/system/arm/virt
- https://android.git.googlesource.com/platform/packages/modules/Virtualization/+/HEAD/libs/framework-virtualization/README.md
