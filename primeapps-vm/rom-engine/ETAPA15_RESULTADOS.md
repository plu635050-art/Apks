# Etapa 15 — Validação de armazenamento multi-instância e ponte de arquivos

Data: 28/10/2026

## Resultado concreto

O GitHub Actions #40739997926 aprovou 9 testes. Foi criado um disco-base EXT4 sintético e quatro overlays QCOW2 reais, cada um com userdata EXT4 separado. Uma escrita num overlay não apareceu nos outros e o SHA-256 do disco-base permaneceu idêntico.

`instance_storage.py` aloca grupos de 4 a 16 discos; isso NÃO significa que o processador e o motor consigam executar 4 a 16 VMs simultâneas.

`guest_file_bridge.py` prepara upload/download e instalação/exportação de APKs por ADB individual por instância. Seus testes usaram uma implementação ADB **simulada** para verificar a lógica, portanto não comprovam transferência em uma ROM Android inicializada.

## Próximo bloqueio para Android 16

A ROM Gold usa configuração de hardware diferente de QEMU `virt`; o Android 10 exige kernel ARM64 com virtio, rootfs completo e `fstab` apropriado. Não há boot concluído nem quatro máquinas em execução.

A interface de armazenamento do APK futuro usará seletor de arquivos Android (SAF) nos dois sentidos. O guest deverá inicializar e oferecer mecanismo de transferência autorizado como `adbd`; só então se validará APKs, splits, fotos, documentos e arquivos exportados.

**Regra:** não criar APK final antes de boot real de Android 7 e 10, root convidado, quatro VMs simultâneas e transferência bidirecional validada no Poco X7 Pro.

Referência permanente: `CRITERIOS_APROVACAO_VM_ANDROID16.md`.

Também passou o cenário de **2 discos Android 7 + 2 discos Android 10**, usando dois discos-base diferentes, userdata separados e quatro portas propostas distintas. Isso ainda não executa Android convidado.