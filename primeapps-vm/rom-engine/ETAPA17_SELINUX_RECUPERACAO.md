# Etapa 17 — Rótulos SELinux originais do Android 10 restaurados nas imagens EXT4

Data: 28/10/2026. Status: **conversão e rótulos comprovados em imagens reais; nenhum boot gráfico completo ainda**.

## Por que esta etapa era necessária

O boot real do Android 7 em QEMU chegou aos serviços `zygote`, `servicemanager` e `surfaceflinger`, mas registrou `Service ... does not have a SELinux domain defined` e AVCS para arquivos `unlabeled`. As imagens EXT4 da etapa anterior preservavam UID/GID/modo, porém **não** `security.selinux`.

## Implementação

Foi criado `selinux_relabel_ext4.py`. Ele usa a biblioteca oficial do host **libselinux** para carregar os `file_contexts` fornecidos na própria ROM e resolver as regras de precedência de regex e tipos de inode. Grava os rótulos com `debugfs ea_set` numa **cópia** da imagem EXT4, confere os atributos com `debugfs ea_get` e a integridade do disco com `e2fsck -fn`. Não executa `sudo`, não altera a ROM `.7z`, não sobrescreve a imagem de entrada. Também recusa caminhos que possam injetar comandos no `debugfs`.

## Resultados executados com as ROMs reais do usuário

| Guest | Partição | Entradas totais | SELinux restaurado | Sem regra correspondente | Imagem resultante |
|---|---|---:|---:|---:|---|
| Android 10 | root | 10 | 9 | 1 (`/share`) | 96 MiB |
| Android 10 | system | 4.509 | 4.505 | 4 (`/dsp`, `/firmware`, `/persist`, `/share`) | 1,88 GiB |
| Android 10 | vendor | 503 | 503 | 0 | 128 MiB |
| **Total** | **3** | **5.022** | **5.017** | **5** | **2,10 GiB** |

Os atributos foram lidos diretamente dos discos depois da gravação:

- `/system/bin/servicemanager` → `u:object_r:servicemanager_exec:s0`
- `/system/bin/app_process64` → `u:object_r:zygote_exec:s0`
- `/system/bin/surfaceflinger` → `u:object_r:surfaceflinger_exec:s0`
- `/system/bin/logd` → `u:object_r:logd_exec:s0`

**Os três sistemas de arquivos passaram no e2fsck sem erros.** O teste sintético criou uma imagem EXT4 e comprovou leitura do xattr, sem modificações na original.

## Limitações importantes

- A ROM Android 7 traz `file_contexts.bin` compilado em formato antigo de 2015; o libselinux do Linux atual não aceita esse formato. Isso **não significa** que a política do Android 7 é incompatível; exige outra ferramenta/adaptação para recuperar os mesmos labels.
- Restaurar as labels no disco **não comprova o carregamento da política SELinux pelo kernel convidado**, a habilitação de domínios, nem o boot dos serviços Android.
- Continuam pendentes `/dev`, `/proc`, `/sys`, `/data`, SELinux policy, Binder, HALs gráficas, Android na tela inicial, root dentro do guest, quatro guests simultâneos, pontes ADB reais e validação no Poco X7 Pro Android 16.
- As imagens privadas de ~2,1 GiB **não** foram nem serão publicadas no GitHub. Apenas o conversor e os testes serão disponibilizados no repositório.

## Próximo marco de aceitação

Repetir o boot com as imagens SELinux reconstruídas e resolver `switch-root`, `/dev` e `/data`, registrando a subida real de `servicemanager`, `zygote` e `surfaceflinger`. **Não criar APK final até Android 7/10 alcançarem interface gráfica e execução simultânea comprovada no aparelho.**

## Reteste QEMU das imagens SELinux (2026-10-28)

Foi executado o kernel AOSP ARM64 3.18 no QEMU TCG com as partições **reais** `system` e `vendor` rotuladas. O processo `init.real` continua encerrando em `Unable to move mount at '/dev': No such file or directory`, mesmo depois de criar os diretórios de montagem na imagem temporária. Isso confirma que recuperar rótulos SELinux é necessário, mas **insuficiente**: precisamos resolver a topologia/semântica de montagens do primeiro estágio antes do segundo estágio e da GUI. Logs privados retidos na Biblioteca; ROMs não publicadas.
