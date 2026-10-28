# PrimeApps VM — LAB 2 (cadastro de instâncias)

**Estado real:** independente do VPhoneOS e do VPhoneGaGa. Projeto de engenharia reversa por observação, sem cópia de bibliotecas fechadas.

## Recursos entregues
- Seleção de ROM .7z pelo Storage Access Framework, leitura em segundo plano e SHA-256 integral
- Permissão de leitura persistente pelo Android (pode ser revogada se usuário mover/apagar o arquivo)
- Catálogo de ROMs local persistente, associação por hash e tamanho
- Um arquivo-base referenciado por várias instâncias, sem replicar arquivo .7z
- Botões reais para preparar 3 instâncias de uma vez ou registrar clones adicionais (até 64 cadastros)
- Cada cadastro com ID aleatório e diretórios privados separados `files/instances/<id>/data` e `cache`
- UI horizontal compatível com Android 16, sem login ou Lovable
- GitHub Actions compila um APK release **sem assinatura** e gera evidências com REA/JADX

## NÃO implementado, nem alegado como funcional
- **Nenhuma ROM inicia. Não há motor de virtualização nesta build.**
- Diretórios privados vazios não são userdata Android, nem overlay COW, nem máquinas virtuais ativas.
- Não existe runtime simultâneo, emulação, root virtual, virtualização por hardware, importação de sistema Android desempacotado ou guest rendering.
- O cabeçalho 7z não comprova que a imagem é compatível ou que bootará.
- O teto de 64 é **apenas de cadastros**; não representa capacidade de 64 VMs simultâneas.

## Critérios de validação deste LAB
1. GitHub Actions termina verde e gera APK sem assinatura e JSON do REA.
2. APK instalado após assinatura pelo usuário abre em Android 16.
3. Importa uma única ROM e prepara três cadastros, sem duplicar o arquivo-base.
4. Clona mais um cadastro e preserva todas as instâncias após reiniciar o aplicativo.
5. Os IDs e diretórios são exclusivos, nenhum registro inicia VM nem promete execução.

## Próxima etapa crítica
Selecionar/implementar motor licenciado e provar boot **de uma** ROM Android 10. Depois validar execução simultânea 2 → 3, isolamento de userdata, gráficos e desempenho. Somente após isso: gestão remota e renovação por Lovable.

As imagens e .so originais de aplicativos de terceiros não são incluídos neste repositório.
