# PrimeApps VM — LAB 1 (base independente)

Este projeto NÃO é uma modificação do VPhoneOS ou do VPhoneGaGa.

## Estado real

- Código Android próprio em Java (sem bibliotecas nativas proprietárias).
- Compilação para API 36 (Android 16), mínimo API 26.
- Interface horizontal, adaptável a celular/TV, sem login.
- Importação **somente leitura** de arquivos locais via seletor do Android.
- Detecta cabeçalho 7z, calcula tamanho e SHA-256.
- Mostra tamanho de página de memória para diagnóstico 4K/16K.
- **Não descompacta, instala, inicializa nem executa Android virtual**.
- **Não possui motor**, root virtual, 32/64 bits mistos, nem integração Lovable.
- Versão release sem certificado de produção (assinatura pelo usuário).

## Validação

Para aprovar este LAB:
1. GitHub Actions deve compilar e produzir APK unsigned.
2. REA 6.8.0 deve analisar o APK gerado e salvar evidências.
3. Instalar APK (após assinatura pelo usuário), abrir no Android 16.
4. Selecionar .7z por seletor; cabeçalho, bytes e SHA-256 corretos.
5. Observar PAGE_SIZE e guardar logs dos erros.

Aprovação **não** comprova que Android 7/10 inicia; essa é uma meta separada, depois da escolha do motor.

## Engenharia reversa

Ferramenta REA, licença MIT. Em GitHub Actions instalamos via npm e inspecionamos o APK gerado usando JADX. Os APKs de terceiros não são armazenados neste repositório público; análise desses arquivos requer acesso autorizado por um meio seguro. A análise de binários nativos aprofundada exige fornecedor como Ghidra configurado separadamente.

### Roteiro

- Etapa 1: compilação + REA + inventário de ROM local
- Etapa 2: rastrear contratos JNI e arquitetura de inicialização
- Etapa 3: escolher ou construir motor de virtualização legalmente reutilizável
- Etapa 4: prova de boot real de Android 10 no Android 16 (critério central)
- Etapa 5: portar Android 7, armazenamento e gerenciamento de instâncias
- Etapas posteriores: performance, compatibilidade, segurança e API Lovable

Não misturar bibliotecas .so dos aplicativos de terceiros por simples cópia.
