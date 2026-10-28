# PRIME APPS VM — ETAPA 14: criação real de disco EXT4 (28/10/2026)

## Entrega executada

- Adicionado `build_ext4_from_atit.py` ao mesmo repositório e branch do leitor de ROM original.
- Lê `readonly.bin` do Gold (via `atitreconstruction.Atit`), valida CRC32, cria EXT4 verdadeiro via `mke2fs -d` e restaura UID, GID e modos dos arquivos e diretórios originais.
- Usa `debugfs` para atribuir IDs aos inodes e `e2fsck -fn` para verificar a integridade do sistema de arquivos.
- **Sem `sudo`, sem loop mount, sem modificar as ROMs**. Usa temporários para preparar o disco.
- Adicionado `test_ext4_builder.py`, com dados sintéticos, leitura do arquivo de volta do ext4 pelo debugfs, validação UID=1000, GID=1001 e teste de tamanho insuficiente.
- GitHub Actions run 40738044274 passou quatro testes (2 do leitor, 2 do ext4). Depois da correção que preserva os modos de diretórios, o novo GitHub Actions run **40738325988** também terminou com sucesso.

## Achados adicionais do guest real

- Android 7: a raiz tem `init` ELF ARM64 (Android API 25) e `init.rc`; o `fstab.${ro.hardware}` referencia `mtdblock0` como `/system`, `mtdblock1` como `/data`, `mtdblock2` como `/cache`.
- Android 10: raiz tem `init` ELF ARM64 (API 29) e um `fstab.${ro.hardware}` que referencia `mtdblock0` como `/system` e `mtdblock3` como `/vendor`, ambos com `first_stage_mount`.
- As partições extraídas possuem executáveis `su` em `system/xbin/su` (Android 7) e `system/system/xbin/su` (Android 10). A presença desses arquivos **não prova que o root será ativado no guest**.
- Os nomes mtdblock são convenções do dispositivo virtual antigo do VPhoneGaGa Gold. Uma máquina `qemu-system-aarch64 -M virt` com armazenamento virtio não é automaticamente compatível com esses caminhos: será preciso configurar/emular dispositivos e adaptar a inicialização no guest.

## O que AINDA NÃO está feito

**Não existe APK com motor de VM funcional neste estágio.** Criar EXT4 não significa criar um kernel, um hipervisor, drivers virtio ou uma experiência gráfica Android. O teste do GitHub utilizou um filesystem sintético pequeno; as imagens completas das ROMs, com gigabytes, não foram criadas em CI porque os ROMs são privados e não foram publicados.

Os itens faltantes para um boot real são: kernel ARM64 compatível com QEMU virt, boot/initramfs adequados a cada ROM, montagem correta de system/vendor/data, overlays e SELinux labels, subsistema gráfico, monitor de instâncias, root no Android guest e testes de duas VMs paralelas no Poco X7 Pro.

## Comandos em Linux (sem root) para uma partição da ROM que pertence ao usuário

```bash
python3 extract_readonly_from_7z.py androidfs_10.0.0.7z system system_readonly.bin
python3 build_ext4_from_atit.py system_readonly.bin system.ext4 --label system
```

Ferramentas: Python 3.13+ e `e2fsprogs` (`mke2fs`, `debugfs`, `e2fsck`), `libarchive.so.13` para extrair do 7z. Necessário espaço em disco suficiente para os arquivos temporários e a imagem EXT4 (pode passar de 3 GB).

### Fontes técnicas

- QEMU ARM virt: https://www.qemu.org/docs/master/system/arm/virt
- QEMU ARM machine/board: https://www.qemu.org/docs/master/system/target-arm
- GitHub: https://github.com/plu635050-art/Apks/tree/feature/primeapps-vm-real-rom-reader-20261028/primeapps-vm/rom-engine

**Próximo marco:** adaptar fstab/init e selecionar kernel Android ARM64/virtio capaz de inicializar os guests recuperados em um QEMU real, antes de montar o APK.