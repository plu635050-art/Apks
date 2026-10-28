# PRIMEAPPS VM — ETAPA 16: Provas de boot, ABI proprietária e bloqueios

**Status real:** há execução comprovada do `init` das duas ROMs Android numa VM ARM64 QEMU, **mas nenhuma ROM completou boot**. Nenhum APK Android16 criado.

## Execução validada

1. Extraídas as partições reais `root/system` (Android 7) e `root/system/vendor` (Android 10) do formato ATIT e convertidas em cinco imagens EXT4 reais; verificação `e2fsck` e hashes de amostras aprovados.
2. QEMU TCG ARM64 iniciou Linux Alpine real em GitHub Actions, rodou `/init` e recuperou console (run 40740606197).
3. Baixados 2 kernels AOSP ARM64 prebuilt `3.18.94+` e `4.4.136+`, publicados pelo Android Emulator, e usados em testes reais locais.
4. Executáveis `/init` originais de ambas as ROMs falharam exatamente em **0xB0000018**, devido a uma chamada de função em um endereço fixo não mapeado. Inspeção ARM64 identificou o padrão `mov x8,#0xb0000000; ldr x8,[x8,#0x18]; br x8`. É um ponto de integração específico ao motor antigo.
5. Criado **adaptador experimental de ABI de syscalls**, que trabalha em cópias e substitui 92 stubs do Android10 e 98 do Android7 por syscall Linux `svc #0` seguida de tratamento original de erro. Esse adaptador não é um motor Android completo, mas demonstrou funcionar no primeiro estágio.
6. Android10: `init: init first stage started!`; montou com sucesso **sua partição real `system.ext4`** em `/system`; chegou a `Switching root to '/system'`, falhando no movimento de `/dev`.
7. Android7: `init` passou pelo primeiro e segundo estágios, carregou SELinux e propriedades, iniciou `healthd`; o `healthd` continha outros 63 stubs, adaptados numa cópia. O processo passou a abortar por dependências adicionais, e faltam dispositivos/ueventd/partições.

## É essencial não confundir provas

- Validado: engine QEMU ARM64 em Linux Actions, execução dos dois `/init` proprietários com ABI adaptada, Android10 `/system` EXT4 real montado.
- **Não validado:** Android7 ou Android10 na tela inicial, desempenho no Poco X7 Pro Android16, root nas VMs, quatro guests simultâneos e transferência real de arquivos/APKs.
- O patch é **somente de laboratório em cópias privadas dos executáveis**; ROMs originais preservadas e nenhum arquivo proprietário publicado no GitHub.
- O QEMU portátil usado para testes é binário Linux x86_64 e **não é motor Android pronto para APK**.

## Pendências técnicas ativas

- Mapeamento ueventd e `/dev/block` no Android7; montagem do `system` e discos de dados reais.
- Switch-root com transferência de `/dev`, `/proc`, `/sys` no Android10.
- Reconstrução de SELinux labels, mounts de `data/cache`, overlay de escrita por instância.
- Dependência comum ao endereço `0xB0000000` em executáveis e bibliotecas restantes; exige compatibilidade sistêmica, não apenas editar `/init`.
- Binder, HAL gráficos, vídeo/áudio, apps, API ADB, root guest e quatro guests.
- Validação real no Poco Android16; ainda não realizada.

O script aberto `compat_syscall_shim.py` só altera **cópias de executáveis ARM64 fornecidos como entrada** e valida o padrão antes de escrever. `test_compat_syscall_shim.py` comprova não modificar o original e rejeitar entradas inseguras.

## Prova adicional — Android 7 monta SYSTEM e processa serviços de interface

Uma segunda configuração de laboratório restaurou o serviço `ueventd` que estava comentado em `init.rc` na ROM extraída, e mapeou o RC-modelo `init.${ro.hardware}.rc` para `init.titan.rc`. A operação foi limitada à cópia temporária da partição `root`.

Registro no QEMU com kernel AOSP 3.18:

- `ueventd: ueventd started!`
- `ueventd: Coldboot took 0.18s.`
- `EXT4-fs (vda): mounted filesystem`
- O `init` examinou scripts `/system/etc/init/*.rc` e reconheceu serviços `zygote`, `surfaceflinger`, `servicemanager`, `su_daemon` e muitos outros.
- A maioria dos serviços falhou antes de iniciar por falta de domínios e rótulos SELinux, porque o conversor EXT4 ainda não reconstruiu o xattr `security.selinux`.

**Não foram validados Android gráfico, root guest, binder funcional, quatro VMs ou Poco Android16.**