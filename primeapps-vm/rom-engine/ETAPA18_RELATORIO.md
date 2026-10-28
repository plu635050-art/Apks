# PrimeApps VM — Etapa 18: diagnóstico da troca de raiz Android 10

**Status: PASS de montagem e MS_MOVE no QEMU ARM64 com partição Android10 real. NÃO é boot Android completo.**

## Teste real executado em 28/10/2026

Foi compilado o `arm64_mount_move_probe.c` como ELF AArch64 estático. O QEMU ARM64 (emulação TCG x86_64) executou o kernel AOSP Android Emulator 3.18 e usou **a imagem real de system do Android 10**, previamente reconstruída e rotulada em EXT4. A ROM foi montada somente para leitura.

Saída serial relevante:

```
PRIMEVM18 mount_proc result=0
PRIMEVM18 mount_sys result=0
PRIMEVM18 mount_dev result=0
PRIMEVM18 FILE /sys/block/vda/dev: 254:0
PRIMEVM18 mknodat_dev_block_vda result=0
PRIMEVM18 mount_real_system_ext4 result=0
PRIMEVM18 mkdir_system_dev result=-17
PRIMEVM18 MOVE_DEV_TO_SYSTEM_DEV result=0
PRIMEVM18 MOVE_DEV_BACK result=0
PRIMEVM18_END
```

`mkdir_system_dev result=-17` = EEXIST, confirmando que `/dev` já existe como diretório no `system.ext4` original. Os movimentos MS_MOVE em ambas as direções foram bem-sucedidos.

## Falha descoberta na preparação dos dispositivos

O teste inicial montou `/dev` como `tmpfs` com flags `MS_NOSUID|MS_NODEV` e `mount("/dev/block/vda", "/system", "ext4")` retornou `-13` (EACCES). Ao repetir montando `/dev` **sem `MS_NODEV`**, a mesma imagem EXT4 real montou com sucesso e o kernel conseguiu mover o mount `/dev` para `/system/dev` e voltar. Essa correção é necessária apenas para o ambiente isolado do convidado, não é orientação para enfraquecer a montagem `/dev` do host Android.

O `init` original, por sua vez, ainda falha em `Unable to move mount at '/dev': No such file or directory`. O teste demonstra que **o kernel e a partição não proíbem o MS_MOVE**. A topologia efetiva de montagens organizada pelo `init` original precisa de diagnóstico. Não há prova de boot gráfico, root, quatro VMs ou performance em Android16.

## Regressão automatizada

`test_arm64_guest_mounts.py` cria EXT4 sintético e executa o ELF ARM64 em QEMU, verificando a montagem real, as transferências `MS_MOVE`, e que a imagem-base permanece inalterada. **O teste público só contém dados sintéticos.** Nenhum arquivo extraído de ROM Gold foi publicado no GitHub.

## Próxima prioridade técnica

Instrumentar o ambiente de primeiro estágio para registrar `/proc/self/mountinfo`, o `new_root`, o destino do movimento e os retornos errno no instante da falha do `init` real. Se necessário, substituir a preparação de `/dev`, `/proc` e `/sys` por um bootstrap compatível antes de entrar no segundo estágio. Não declarar boot Android completo até observar execução de `servicemanager`, `zygote`, `surfaceflinger`, interface gráfica e conectividade ADB reais. Validação no Poco X7 Pro permanece dependente de teste físico.

## Reteste do init Android 10 com MS_NODEV corrigido

O bootstrap de laboratório foi refeito com `/dev` sem `MS_NODEV` e o boot foi repetido com as imagens reais Android10 `system` e `vendor`. O `init.real` ainda registra `Unable to move mount at '/dev': No such file or directory`; portanto a alteração é válida para o probe isolado, **mas não corrige o init original**. Log restrito à Biblioteca. O script de regressão sintético ARM64 + ext4 foi aprovado em GitHub Actions **#40749665984**. Continuam pendentes switch_root real, Android GUI, root guest, 4 VMs e prova no Poco Android16.
