# Orbit Files

Gerenciador de arquivos para Android, moderno e sem anúncios, feito em **Kotlin + Jetpack Compose + Material 3 Expressive**.

- Pacote: `com.orbitfiles.app`
- Android 8.0+ (minSdk 26), target Android 16 (API 36)
- Interface em português do Brasil

---

## Como gerar o APK

### Opção A: Android Studio (recomendado)
1. Abra o Android Studio e use **File › Open** nesta pasta (`OrbitFiles`).
2. Espere o Gradle sincronizar. Na primeira vez ele baixa as dependências.
3. Vá em **Build › Generate App Bundles or APKs › Generate APKs**.
4. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.
   - Para a versão otimizada, rode `./gradlew assembleRelease` e pegue o arquivo em `app/build/outputs/apk/release/app-release.apk`.
5. Passe o APK para o celular e instale. Se o Android pedir, permita a instalação de fontes desconhecidas.

### Opção B: GitHub Actions (sem instalar nada no PC)
1. Crie um repositório no GitHub e envie esta pasta (`git init`, `git add .`, `git commit`, `git push`).
2. A aba **Actions** roda o workflow **Build APK** sozinha. Ele executa os testes do núcleo e compila os APKs.
3. Baixe o artefato **OrbitFiles-APK** ao fim da execução. Ao rodar o workflow manualmente (*Run workflow*), ele também cria uma Release com os APKs anexados.

### Linha de comando
```bash
./gradlew :core:test          # testes do núcleo
./gradlew :app:assembleDebug  # APK de depuração
./gradlew :app:assembleRelease
```
Requer JDK 17+ e Android SDK (compileSdk 36). No Windows, use `gradlew.bat`.

> **Assinatura:** o release usa a chave de debug quando não há keystore configurado. Para publicar ou manter atualizações compatíveis, defina as variáveis `ORBIT_KEYSTORE`, `ORBIT_KEYSTORE_PASSWORD`, `ORBIT_KEY_ALIAS` e `ORBIT_KEY_PASSWORD`.

---

## Funcionalidades

### Essenciais
- Navegação por **armazenamento interno, cartão SD e USB OTG**, com trilha de pastas (breadcrumbs) e troca de volume.
- **Lista ou grade**, ordenação por nome (ordem natural: "foto 2" vem antes de "foto 10"), data, tamanho ou tipo, com pastas primeiro.
- **Cada pasta lembra sua ordenação e o modo de visualização.**
- **Seleção múltipla**: selecionar tudo, inverter, copiar, recortar, colar, compartilhar, excluir.
- **Colar com resolução de conflitos**: manter ambos, substituir ou pular.
- Criar pasta ou arquivo, renomear com validação, propriedades com tamanho da pasta, resolução de imagem e **MD5/SHA-256**.
- Abrir com, compartilhar, copiar caminho, **favoritos** na tela inicial e arquivos ocultos.
- **Coleções inteligentes** (imagens, vídeos, áudio, documentos, compactados, APKs, downloads) via MediaStore, que aparecem na hora.
- Recentes com miniaturas, incluindo **ícones reais dos APKs**.

### Avançadas
- **Operações em segundo plano** com notificação, progresso, **velocidade, tempo restante** e cancelamento instantâneo. A gravação é atômica, em arquivos `.orbitpart`, para nunca deixar arquivo corrompido.
- **Lixeira** separada por volume (enviar para ela é instantâneo), com retenção configurável e restauração ao local original.
- **Desfazer** em snackbar e **Histórico** com desfazer para renomear, mover, copiar, criar e excluir.
- **Abas + painel duplo** automático em tablets e dobráveis, com copiar ou mover para o outro painel.
- **Renomeação em lote** com padrões (`{name}`, `{n:3}`, `{date:yyyy-MM-dd}`, `{ext}`, `{parent}`), localizar e substituir, regex e maiúsculas/minúsculas, com prévia ao vivo e detecção de conflitos.
- **ZIP**: compactar, navegar dentro do arquivo, abrir arquivos internos e extrair tudo ou só uma pasta. Tem proteção contra *zip slip*.
- **Busca em linguagem natural** (PT/EN): "fotos de ontem", "pdf maior que 10 mb", "vídeos grandes do mês passado", "ext:kt main", "músicas em 2025". Também busca **dentro de textos** e por **regex**. É toda offline e mostra os resultados conforme encontra.
- **Paleta de comandos** no estilo Spotlight: comandos e pastas por digitação.
- **Análise de armazenamento**: gráfico em anel por categoria, mapa de pastas para explorar nível a nível e lista dos maiores arquivos.
- **Limpeza inteligente**: duplicados em 3 estágios (tamanho → hash de 64 KB → SHA-256), temporários, cache de miniaturas, pastas vazias, APKs, arquivos grandes e downloads antigos. Tudo vai para a lixeira.
- **Cofre criptografado**: AES-256-GCM em blocos autenticados de 64 KB, chave mestra protegida por PBKDF2-HMAC-SHA256, desbloqueio por **biometria via Android Keystore**, bloqueio automático e troca de senha sem recriptografar os arquivos.
- **Compartilhar por Wi-Fi**: servidor HTTP embutido com interface web moderna. Faz download com streaming (HTTP Range), upload por arrastar e soltar e exige uma chave de acesso aleatória.
- **Visualizadores internos**: imagens com zoom e deslize, vídeo e áudio (Media3/ExoPlayer) com playlist da pasta, **PDF** (PdfRenderer com zoom) e **editor de texto/código** com fonte monoespaçada, quebra de linha e salvar.
- **Bloqueio do app** por biometria ou bloqueio de tela.
- **Atalhos do ícone** (pressione e segure): Buscar, Limpeza, Cofre, Wi-Fi.

### Visual (Material 3 Expressive)
- Cores dinâmicas do papel de parede (Android 12+) ou 6 paletas próprias. Tema claro, escuro ou do sistema e **AMOLED preto**.
- Animações com física de mola, ícones de arquivo que **mudam de forma ao selecionar** (quadrado arredondado → círculo com ✓), botões que encolhem ao toque e cabeçalhos grandes que recolhem.
- Layout adaptativo: barra inferior no celular, **trilho lateral** em telas largas e painel duplo em tablets.
- Opções de **modo compacto** e **reduzir animações** (acessibilidade).

---

## Arquitetura

```
OrbitFiles/
├── core/   Módulo Kotlin/JVM puro: toda a lógica de arquivos, sem dependência do Android
│   ├── FileOps            copiar/mover/excluir com progresso, cancelamento e escrita atômica
│   ├── TrashStore         lixeira com índice TSV
│   ├── Journal            histórico com desfazer
│   ├── Search             busca iterativa (sem recursão, segura contra symlinks) + pontuação fuzzy
│   ├── NaturalQueryParser busca em linguagem natural PT/EN
│   ├── BatchRenamer       renomeação em lote em 2 fases (permite trocas a↔b)
│   ├── ArchiveManager     ZIP com proteção contra zip slip
│   ├── Analysis           análise de armazenamento, duplicados, limpeza, checksums
│   ├── VaultCrypto/Store  cofre AES-256-GCM em blocos
│   ├── LanShareServer     servidor HTTP com Range, upload e autenticação
│   └── Sorting            ordem natural
└── app/    Android: Jetpack Compose + Material 3
    ├── data/   Configurações (DataStore), armazenamento/MediaStore, lixeira por volume
    ├── ops/    OperationManager (motor central) + serviço em primeiro plano
    ├── vault/  Cofre + Android Keystore
    ├── share/  Controle do servidor Wi-Fi
    └── ui/     Tema, componentes, telas, visualizadores e navegação
```

- **MVVM + fluxo de dados unidirecional**: StateFlow/Compose State nos ViewModels.
- **Injeção de dependência manual** (`AppContainer`): sem reflexão nem processamento de anotações, e o app abre instantaneamente.
- **Kotlin Coroutines** para todo I/O. A UI nunca bloqueia.
- **Testes**: 35 testes unitários cobrem o núcleo, incluindo criptografia contra adulteração e truncamento, servidor HTTP de ponta a ponta, zip slip, conflitos e desfazer.

## Permissões

| Permissão | Para quê |
|---|---|
| Acesso a todos os arquivos | Necessária para qualquer gerenciador de arquivos no Android 11+ |
| Notificações | Progresso de cópias longas |
| Internet / Wi-Fi | **Apenas** para o compartilhamento local por Wi-Fi (o app não acessa servidores externos) |
| Biometria | Desbloquear o app e o cofre |
| Instalar apps | Abrir arquivos APK |

Sem anúncios, sem rastreamento e sem coleta de dados.

## Limitações conhecidas
- A pasta `Android/data` de outros apps é bloqueada pelo próprio Android (11+). Nenhum gerenciador sem root acessa.
- O cofre fica no armazenamento privado do app. **Desinstalar apaga o cofre**, então exporte os arquivos antes.
- Formatos RAR e 7z abrem com apps externos. O suporte interno é a ZIP, JAR, APK, CBZ e EPUB.
- PDFs protegidos por senha abrem em app externo.
