# PRIMEAPPS VM — CONTRATO DE ACEITE / ESTADO DA ENGENHARIA

Data: 28/10/2026. Este documento é a referência permanente do projeto. **Não chamar APK de funcional até todos os itens críticos PASSAREM em aparelho real.**

## Objetivo fixado pelo usuário

**Um único APK autoral**, gerado por GitHub Actions e assinado depois pelo próprio usuário, para Android 15 e 16, especialmente Poco X7 Pro com 12 GB de RAM:

1. Importar as ROMs Android 7.1.2 e Android 10 já pertencentes ao usuário, a partir do armazenamento do celular.
2. Inicializar realmente cada ROM com interface gráfica e interação normal, sem depender de VPhoneGaGa/VPhoneOS proprietário.
3. **Executar pelo menos quatro máquinas virtuais ao mesmo tempo**, podendo repetir a ROM base em instâncias distintas; nenhuma deve compartilhar inadvertidamente userdata/cache/config com outra.
4. **Root dentro de cada Android convidado** (verificar `id -u`/`su` = 0 conforme a imagem e a configuração), sem exigir root do telefone se não for estritamente necessário. Se hardware acceleration exigir root no host, informar explicitamente.
5. Importar arquivos do celular para uma VM, e exportar arquivos de uma VM para o celular; com seletor Android/SAF e permissão controlada pelo usuário.
6. Importar/instalar APKs do celular na VM e exportar APKs de aplicativos instalados na VM para o celular, incluindo split APKs quando possível; preservar certificados e exigências do Android. **Não prometer transferir automaticamente dados privados, licenças ou sessões de aplicativos.**
7. Operações de importar/exportar devem selecionar uma das quatro VMs; não misturar os dados ou comandos de diferentes instâncias.
8. Suporte a pausa, parada, retomada e limpeza segura de cada instância, sempre sem corromper o disco-base comum.
9. Não gerar APK final antes de testes reais satisfatórios no Poco; builds de laboratório só quando tiverem motor e objetivo técnico comprovável, não apenas interface.

## Checkpoints de validação

| ID | Teste objetivo | Estado verificado em 28/10 |
|---|---|---|
| V01 | Ler as duas ROMs (`ATIT`/`readonly.bin`) e CRC dos arquivos | **PASSOU** com arquivos reais | 
| V02 | Recuperar partições `root/system/vendor` e arquivos executáveis ARM64 | **PASSOU** com arquivos reais |
| V03 | Gerar EXT4 com UID/GID/modes e reler arquivos | **PASSOU** em imagem sintética no Actions; ROM real ainda não montada no QEMU |
| V04 | Criar 4 overlays QCOW2 independentes e 4 partições userdata EXT4 | **PASSOU em QEMU no GitHub Actions #40739483992** |
| V05 | Provar leitura/escrita sem contaminação entre quatro instâncias | **PASSOU em QEMU no GitHub Actions #40739483992** (não implica VM em execução) |
| V06 | Obter kernel ARM64 de distribuição permitida com drivers virtio e adaptar init/fstab | PENDENTE |
| V07 | Boot de Android 10 recuperado, shell funcional | PENDENTE |
| V08 | Boot de Android 7 recuperado, shell funcional | PENDENTE |
| V09 | Exibir vídeo/interface gráfica e obter interação estável | PENDENTE |
| V10 | Root dentro de cada VM confirmado | PENDENTE |
| V11 | Quatro VMs simultâneas com desempenho utilizável no Poco X7 Pro | PENDENTE — não inferir de 12 GB RAM |
| V12 | Transferência bidirecional real com ADB autorizado (ou equivalente) | PENDENTE — ponte terá teste simulado primeiro |
| V13 | Instalar/exportar APKs real, com split quando presente | PENDENTE — ponte terá teste simulado primeiro |
| V14 | Integração Android 15/16: arquivos SAF e interface | PENDENTE |
| V15 | CI compila APK real, assinado pelo usuário, boota convidados no telefone | PENDENTE |

## Arquitetura de transferência aprovada

- **No celular Android**: usar `ACTION_OPEN_DOCUMENT` para importar arquivos/APKs e `ACTION_CREATE_DOCUMENT` para exportar. Não pedir `MANAGE_EXTERNAL_STORAGE` se SAF resolver.
- **Por instância**: manter um identificador e porta ADB local única, caso o convidado forneça `adbd` autorizado. O bridge deve recusar conexão ADB remota não selecionada.
- **Celular -> VM**: escolher arquivo, copiar para staging do app e usar `adb push` para o `Download` do convidado.
- **VM -> celular**: escolher arquivo guest, usar `adb pull` para área privada do app e salvar via SAF.
- **APK celular -> VM**: `adb install` ou `install-multiple` se pacote dividido; depende de arquitetura, SDK e assinatura.
- **APK VM -> celular**: `pm path` e `adb pull` de `base.apk` e splits, exportados para pasta/ZIP. Instalar o APK de volta no host requer confirmação do usuário e compatibilidade.
- O gerenciamento de apps já instalados no **telefone** depende de visibilidade de pacotes e permissões; para importar APK sempre há a opção de selecionar um arquivo APK fornecido pelo usuário.
- **Esta ponte não concede root**. A imagem convidada precisa fornecer root se for uma exigência de teste.

## Implementações reais — esta etapa

- `instance_storage.py`: aloca discos EXT4 userdata diferentes e overlays QCOW2 sobre uma base de sistema EXT4 com SHA256 conservado.
- `guest_file_bridge.py`: API CLI para `adb push/pull/install/install-multiple/pm path` por VM, com validação de serial local e nomes; não possui guest boot próprio.
- Testes automatizados provam **discos independentes** e validam a lógica do ADB com comandos simulados; prova real no convidado permanece pendente.

## Riscos que não serão ignorados

- As ROMs Gold trazem fstab com `mtdblock`, enquanto QEMU `virt` usa virtio: kernel e init precisam de port/adaptação.
- O leitor atual não restaura todos os xattrs SELinux, overlays legados, contexto de boot ou drivers do convidado.
- Android 16 não garante aceleração KVM/GZVM nem desempenho de 4 guests; memória RAM disponível não equivale a capacidade de CPU/GPU.
- Não usar bibliotecas nativas proprietárias de terceiros sem autorização para distribuição.
- Até o primeiro guest subir, **não existem evidências para prometer um APK funcional ou quatro guests simultâneos**.

## Fontes de implementação

- QEMU QCOW2/backing files: https://www.qemu.org/docs/master/tools/qemu-img.html
- QEMU ARM64 virt: https://www.qemu.org/docs/master/system/arm/virt
- ADB: https://developer.android.com/tools/adb
- Android SAF: https://developer.android.com/training/data-storage/shared/documents-files

## Resultado CI executado, etapa 15

- Branch: `feature/primeapps-vm-real-rom-reader-20261028` no repositório `plu635050-art/Apks`.
- **Run #40739483992:** 8 testes aprovados.
- `test_four_independent_disks_and_immutable_base`: QEMU `qemu-img` criou quatro overlays QCOW2; `qemu-io` escreveu em um e leu o outro sem contaminação; SHA-256 da base permaneceu igual; discos userdata EXT4 distintos testados pelo `debugfs`.
- `test_four_separate_guest_serials_and_transfer`: testes com ADB simulado comprovaram a seleção da instância, `push`, `pull`, `install` e exportação de APK normal/split no nível de lógica. **Nenhuma transferência em guest real foi testada**.
- O CI deste momento executou em Ubuntu x86_64, **não** no Poco Android 16; boot de sistema convidado continua pendente.