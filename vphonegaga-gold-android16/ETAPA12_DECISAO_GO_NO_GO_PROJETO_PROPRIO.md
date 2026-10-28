# Etapa 12 — Decisão técnica GO / NO-GO: APK próprio baseado no VPhoneOS

**Estado: NO-GO para desenvolvimento do aplicativo completo usando somente os binários e ROMs disponíveis.** Sem novo APK, sem commit de bibliotecas proprietárias, sem substituição do motor. A alternativa viável depende de um motor legalmente utilizável e de prova de boot das ROMs.

## Evidências do próprio REA/JADX e APK
- VPhoneOS 5.2.1 `com.yoyo.snake.rush`: REA analisou o pacote; JADX decompilou as classes críticas em `vphoneos_jni_compare/decoded` no ambiente da análise. O APK base tem suporte de libs ARM64 em split APK.
- Gold `com.vphonegaga.titan`: ROMs `assets/androidfs_7.1.2.7z` e `assets/androidfs_10.0.0.7z`.
- Em `RomManager.getAssetRomVersion(Context)` no VPhoneOS, arquivos em `assets` com prefixo `androidfs_` são interpretados **apenas quando terminam** em `_32.7z` ou `_64.7z`. Os nomes Gold não encaixam nesse ramo do reconhecedor. **Isso não prova incompatibilidade definitiva do conteúdo ROM:** outras rotas de importação podem existir e o contrato de boot ainda não foi demonstrado.
- Gold `VPhoneGaGaLibHelper` declara 34 métodos nativos; VPhoneOS declara 82, com 8 nomes comuns de assinatura diferente. Boot mudou de `InitEngine()` a `InitEnvironment()` e `InitializeInstance` ganhou terceiro parâmetro e retorno boolean.
- Gold antigo com patch de abertura chega à interface no Android16 mas retorna erro 51. VPhoneOS original abre no mesmo host, segundo teste do usuário. Não há boot de ROM Gold no motor VPhoneOS comprovado.
- Bibliotecas proprietárias prontas não tornam um projeto **compilável do zero** no GitHub e distribuição comercial pode exigir autorização/licença.
- Android AVF não é substituto direto para apps comuns de VMs Android completas: `MANAGE_VIRTUAL_MACHINE` é permissão restrita e o cenário padrão é Microdroid, conforme documentação AOSP.

## Gatilho de liberação do GO (sem investir em UI ou recursos antes)
1. Fonte/SDK de um motor de VM que possamos compilar e distribuir legalmente.
2. Comprovação de que **ao menos uma** das ROMs Android 7 ou 10 fornecidas inicializa nesse motor; caso não, usar imagem compatível licenciada.
3. Build reproduzível no GitHub Actions, assinatura pelo usuário e instalação/boot real no Android15/16 (Poco X7 Pro).
4. Só após a prova de boot, desenvolver interface própria, gerenciamento de múltiplas instâncias, armazenamento e refinamento.

**Conclusão:** possível criar aplicativo próprio de VM em tese, mas **não é possível garantir**, com as 33 bibliotecas do VPhoneOS e as duas ROMs Gold, que um APK autoral reproduza o motor e carregue o Android 7/10. Não avançar vendendo esse resultado como funcional.
