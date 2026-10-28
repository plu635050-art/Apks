# Etapa 4 — Correção isolada da verificação de assinatura

**Status: candidato montado e assinado para testes; boot Android 16 NÃO validado.**

## Causa concreta demonstrada

- `Larm/chajv;` é ContentProvider autoiniciado com `initOrder=19999999`.
- `onCreate()` inicia thread de verificação; `run()` chama `pm path`, lê o certificado e encerra o processo por `System.exit(0)` quando diverge.
- Desofuscação do campo `Signer` retornou 5.732 caracteres base64, decodificando 4.299 bytes: 1 byte de contagem + 4 bytes tamanho + 4.294 bytes de X.509 DER.
- A estrutura decodificada coincide **byte a byte** com o certificado Goxome do `META-INF/KEY0.RSA` original.
- Certificado Goxome SHA-256 `0e92324e5546f5e21dfe5d1885bbacf85d2d574badf1563bf19fc51aa59ba9ae`.

## Alteração isolada

Removido somente **o registro de `arm.chajv` do manifesto binário**, não a classe nem métodos; nenhuma alteração de DEX/ROM/lib. Os dois registros XML de abertura/fechamento do ContentProvider foram excluídos (total 140 bytes). Os demais componentes permanecem, incluindo os ContentProviders legítimos.

Reempacotamento conserva bytes comprimidos dos demais arquivos, preservando 4 DEX, `libVPhoneGaGaLib.so`, configurações e recursos. 1.436 entradas `STORED` ficaram alinhadas em 4 bytes. ZIP CRC íntegro em variantes.

## Arquivos privados da Biblioteca

Pasta **/VPhoneGaGa Gold - Engenharia Reversa/**
- `VPhoneGaGa_Gold_PATCH_ASSINATURA_E_FERRAMENTAS.zip`: APK sem ROMs e sem assinatura + código de reprodução + diagnóstico + relatório.
- `ETAPA4_ASSINATURA_COMPAT_RELATORIO.md` e `ETAPA4_VALIDACAO.json`.
- `VPhoneGaGa_Gold_ANDROID16_CANDIDATO_ASSINADO_TESTE.apk.part01`, `.part02`, `.part03`: versão completa assinada, dividida porque a Biblioteca limita a 512 MB por arquivo.
- `COMO_REUNIR_APK_PARTES.txt`: instrução de remontagem.
- O arquivo completo assinado também foi gerado na sessão, tamanho 913.941.029 bytes, SHA-256 `84ce09b9fe96dc9bc1de43525f4ef33bd335176000512038ca82bede688bee74`.

## Assinatura

O APK completo de teste foi assinado com **uma chave descartável de laboratório**, NÃO com a chave privada guardada nos segredos do GitHub. `apksigner` validou V1, V2 e V3, inclusive requisito de API 36. A estrutura do APK está íntegra. O original instalado não pode ser atualizado diretamente com certificado diferente; backup dos dados antes de desinstalar.

Para assinatura com a chave GitHub, o artefato privado deve ser enviado ao runner por um meio privado autorizado e assinado depois que todas as ROMs tiverem sido inseridas. Nenhuma biblioteca proprietária foi publicada no repositório GitHub.

## O que não está comprovado nem resolvido

- Instalação e boot em Android 16 real; `getconf PAGE_SIZE` não foi retornado pelo usuário.
- As 14 bibliotecas com alinhamento ELF 4 KB permanecem originais; o aparelho pode exigir compatibilidade com 16 KB e correção de outras chamadas nativas.
- Inicialização e execução de ROM Android 10/7.
- Múltiplas instâncias simultâneas.

**Próximo insumo decisivo:** resultado de instalação/abertura/boot no Poco X7 Pro e logcat de erro, se houver. Não criar mais LABs sem logs de falha do motor.
