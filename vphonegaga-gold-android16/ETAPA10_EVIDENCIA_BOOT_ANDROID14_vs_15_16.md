# Etapa 10 — Evidência de execução até Android 14 versus falha Android 15/16

Data: 2026-10-28. Status: observação documentada; **sem novo APK ou correção de motor**.

## Evidência enviada pelo usuário

- Duas fotografias de um dispositivo mostrando o **ambiente Android virtual** do Gaga Gold.
- Foto A: launcher convidado com Goxome, Play Store, Subscribe, Sys Settings e File Transfer; barras e botões de navegação renderizados.
- Foto B: gaveta de aplicativos virtual aberta, com Camera, Downloads, Gallery, Play Store, Settings, Subscribe e Via.
- As imagens comprovam tela de Android convidado inicializada e interação básica com o launcher (em oposição a mostrar somente uma splash screen).
- O usuário afirma expressamente ter **testado e aprovado a execução em aparelhos até Android 14**, e observado falha de inicialização do motor em Android 15/16. As fotos não mostram a versão do sistema **host** fotografado, portanto a afirmação de host 14 é evidência do teste informado pelo usuário e **não verificável por estas imagens isoladas**.
- Não podemos inferir versão do Android convidado a partir da aparência da tela.

## Estado da comparação anterior

- No Android 16, o APK derivado Gold corrigido para abrir, com ROMs 7/10 recolocadas e assinatura nova, chega à splash e retorna **ENGINE_ERROR_VERIFY_FAIL = 51**.
- Na versão que abre Android 16, o ContentProvider de verificação Java do certificado foi retirado do manifesto; os DEX foram preservados.
- **Cuidado experimental:** para atribuir o diferencial somente à versão do host é preciso manter *mesmo APK, mesmas ROMs, mesma assinatura e mesmas configurações* em 14 e 15/16. Caso contrário, a nova assinatura pode permanecer como fator de confusão para a verificação do motor.
- 14/16 componentes ELF Gold possuem PT_LOAD 4 KB; biblioteca central tem PT_LOAD 64 KB. Android 15+ suporta dispositivos 16 KB, mas nem todo host é 16 KB. `getconf PAGE_SIZE` funciona no shell **sem root** e distingue 4096 de 16384.
- Outros vetores Android 15/16: chamadas nativas de processo, políticas SELinux/seccomp, ART e comportamento de carga de módulos. A classe Java retorna o código JNI e não informa subcondição do VERIFY_FAIL.
- O `titan.log` fornecido é empacotamento nativo binário, não o logcat do SO; não tratar como causa decodificada.

## Diagnóstico único restante

1. Quando houver acesso ao Android 16, coletar `getconf PAGE_SIZE` **sem root** e, se possível, logcat na inicialização do mesmo APK.
2. Se 16384, priorizar verificar `libuserkernel64.so` e outras libs relevantes 4 KB, além do pacote APK zip alignment 16 KB. A falha 51 não demonstra que o linker seja a causa.
3. Se 4096, não investir em converter bibliotecas apenas por alinhamento; focalizar condição de VERIFY_FAIL, assinatura, ambiente e dependências nativas.
4. Não trocar 16 bibliotecas por 33 de outro aplicativo sem ABI JNI/guest comprovada e direitos de redistribuição.

**Objetivo:** única versão final que efetivamente dê boot na ROM no Android 16; não gerar LAB de interface novamente.
Fontes oficiais: https://developer.android.com/guide/practices/page-sizes ; https://developer.android.com/about/versions/15/behavior-changes-all .
