# ETAPA 3 — Base única da engenharia reversa VPhoneGaGa Gold

**Executada em 28/10/2026. Não foi criado APK modificado.**

## Referências permanentes

A pasta privada da Biblioteca **/VPhoneGaGa Gold - Engenharia Reversa/** contém:
- `VPhoneGaGa_Gold_ABERTO_SEM_ROMS_BASE_UNICA.zip` — todos os arquivos internos do original extraídos, exceto ROMs 7 e 10.
- `LEIA-ME_BASE_UNICA.md` — instruções.
- `VERSAO_REFERENCIA.json` — identificador do original e inventário resumido.
- `MAPA_METODOS_CRITICOS.txt` —  referência a métodos que importam para a auditoria.
- `RESUMO_DEX.json` e `MANIFESTO_COMPONENTES.txt` — consulta direta.
- `REA_ANALISE_ORIGINAL_DEX.json` — resultado real REA da cópia sem ROMs, com DEX/manifesto idênticos ao original.
- `PLANO_DIRETO_SEM_RETRABALHO.md` — situação e pendências.
- `INDICE_JAVA_DECOMPILADO.txt` — índice dos arquivos Java no ZIP.
  
**Não subir esse ZIP num GitHub público:** inclui código e bibliotecas proprietários.

## Proveniência e checagem

- Original: `VPhoneGaGa_3.4.0_Olivia_ORIGINAL.apk`, 913.981.981 bytes.
- SHA-256 original: `5fd7b0bde058b83e8ea497476fcc0798e8d2c4e33af02ed1b15d3c823487ccc5`.
- ROMs **excluídas apenas do ZIP de estudo**:
  - `assets/androidfs_10.0.0.7z` — 505.428.529 bytes; SHA-256 `f512d2d9ef025edd033ea54b1e70217269cfd1e47efb9e330ae8e0db97289469`.
  - `assets/androidfs_7.1.2.7z` — 345.458.715 bytes; SHA-256 `9560fb4f00859a835b7b991f9cf337a1d6e454d3659e92c122e5d7f4e42caf2b`.
- Os 2.420 demais arquivos originais foram preservados no ZIP aberto (incluindo META-INF **somente como evidência**, sem validade de assinatura para um APK novo).
- 4 DEX indexados: **23.513 classes definidas, 158.493 métodos definidos**. 21 entradas *.so, 16 bibliotecas ELF não vazias.

## Como economizar trabalho

1. Sempre abrir primeiro `LEIA-ME_BASE_UNICA.md` e `RESUMO_DEX.json`.
2. Pesquisar `MAPA_METODOS_CRITICOS.txt` para localizar classe/DEX antes de usar o JADX.
3. **Os quatro DEX já foram decompilados uma vez** pelo JADX 1.5.6 com memória controlada por DEX; há 13.286 arquivos Java exportados no ZIP (22 erros relatados). Consultá-los sem repetir a descompilação. Quando houver erro, conferir o bytecode original.
4. Jamais refazer modificações no DEX4 dos LABs antigos; começar da base original verificada.
5. Não assinar antes de reinserir ROMs. O ZIP atual é **material de estudo**, **não um APK**.

## Pendências críticas para o aplicativo realmente funcional

- Reproduzir a falha no Android 16 com **APK original intacto** e `getconf PAGE_SIZE`.
- Fazer análise dinâmica/logcat; isolar assinatura, ausência de ROMs, integridade do DEX e falhas ELF/processos.
- Verificar como ROM 7/10 é descoberta, extraída e inicializada (Java/JNI e libs).
- Determinar viabilidade de atualização do motor, incluindo permissões/licenças de bibliotecas e reconstrução nativa.
- Só produzir **um candidato a APK** quando uma correção concreta estiver pronta, validar instalação/abertura/boot antes de iniciar múltiplas máquinas.

### Limites e status REA

**REA 6.8.0 com motor JADX headless 0.7.1 foi executado neste ambiente de análise sobre a cópia LAB 3 do VPhoneGaGa Gold** (sem ROMs e sem assinatura). Os 4 DEX e o AndroidManifest.xml dessa cópia foram verificados byte a byte iguais aos do original. Resultado REA: pacote `com.vphonegaga.titan`, 3.4.0 Olivia, 12.504 classes (métrica de REA distinta das 23.513 definições brutas indexadas); a análise foi estática, não testou boot ou assinatura. **JADX CLI 1.5.6 exportou 13.286 `.java` em quatro grupos**, com 22 erros de decompilação. O ZIP final e o plano técnico estão preservados na Biblioteca privada; os binários não estão publicados no GitHub.

Arquivo de auditoria da etapa 2: `vphonegaga-gold-android16/ETAPA2_ASSINATURA_ROM.md`.
