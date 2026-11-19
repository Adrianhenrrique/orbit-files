# Pesquisa: gerenciadores de arquivos Android em 2026

Pesquisa feita em 18/11/2026 para orientar o design do **Orbit Files**. As fontes estão no fim do documento.

## 1. Panorama dos líderes

| App | Pontos fortes | Limitações relatadas |
|---|---|---|
| **Files by Google** | O padrão mais usado. Limpeza inteligente (duplicados, lixo), categorias, Pasta Segura com PIN ou padrão, Quick Share offline e criptografado, sem anúncios. | Não navega em rede, não tem painel duplo e só trabalha com ZIP. Os controles avançados são limitados. |
| **Solid Explorer** | Considerado o mais completo. Painel duplo com arrastar e soltar, nuvem, visual Material polido. Compra única. | É pago (cerca de €3) e parte dos recursos depende de plugins instalados à parte. Algumas análises acham a interface datada. |
| **MiXplorer** | Velocidade, cobertura enorme de protocolos e personalização extrema. | Configurar os plugins é complicado para quem não é avançado. Não tem distribuição oficial na loja (a versão Silver é paga). |
| **FX File Explorer** | Abas e acesso root profundo. | Rede e nuvem só na versão Plus. A interface para TV não é otimizada. |
| **X-plore** | Painel duplo em árvore, FTP/SMB/WebDAV. | A curva de aprendizado é alta. |
| **CX File Explorer** | Limpo e simples, sem anúncios. | Na rede, suporta só SMB. |
| **Material Files** | Código aberto, leve e fiel ao Material Design. | Tem menos ferramentas: não faz limpeza nem análise de espaço. |
| **Total Commander** | Port do clássico do Windows, com plugins. | A interface é antiga e quase tudo depende de plugins. |

## 2. Tendências de 2026

1. **Material 3 Expressive**: a evolução do Material You lançada com o Android 16 QPR1 (setembro/2025). Ela traz animações com física de mola, uma biblioteca com 35 formas e tipografia e cores mais marcantes. Os apps do Google estão migrando para esse visual.
2. **Busca em linguagem natural e no próprio aparelho**: apps novos aceitam buscas como "PDFs de janeiro" ou "vídeos acima de 500 MB" e fazem a indexação localmente. O Files by Google já lê o conteúdo de documentos com o Smart Search.
3. **Limpeza assistida**: o mercado está cheio de "AI cleaners" com anúncios. Quem ganha confiança é quem é transparente e não tem anúncios.
4. **Privacidade como recurso**: pasta segura e criptografia passaram a ser recursos esperados.
5. **Transferência sem cabo**: Quick Share e acesso pelo navegador do PC estão em alta.
6. **Restrições do Android**: desde o Android 11 é preciso a permissão "Acesso a todos os arquivos" (MANAGE_EXTERNAL_STORAGE), e a pasta `Android/data` ficou inacessível. O Android 16 aplica o Scoped Storage com rigor total.

## 3. Lacunas que o Orbit Files ataca

| Lacuna nos concorrentes | Resposta do Orbit Files |
|---|---|
| Quem tem visual bonito costuma ser limitado (Files by Google, Material Files), e quem é poderoso costuma ser feio ou complexo (MiXplorer, Total Commander). | Um app completo com Material 3 Expressive de ponta a ponta: molas, ícones que mudam de forma ao selecionar e cabeçalhos expressivos. |
| Busca só por nome de arquivo. | **Busca em linguagem natural em português e inglês**, 100% offline. Entende tipo, tamanho, datas relativas, ano, extensão e regex, e também busca **dentro de textos**. |
| A maioria não deixa desfazer nada. | **Histórico com desfazer** para renomear, mover, copiar, criar e excluir. Aparece também como snackbar "Desfazer". |
| Pastas seguras com PIN fraco ou sem criptografia real. | **Cofre AES-256-GCM** em blocos autenticados (detecta alterações e truncamento), chave derivada com PBKDF2 e **biometria via Android Keystore**. |
| Painel duplo só em apps pagos ou complexos. | **Abas + painel duplo automático** em tablets e dobráveis, com "copiar ou mover para o outro painel". |
| Limpeza com anúncios ou opaca. | **Limpeza transparente**: duplicados (tamanho → hash parcial → SHA-256), temporários, miniaturas, pastas vazias, APKs, arquivos grandes e downloads antigos. Tudo vai para a lixeira, com restauração. |
| Compartilhar com o PC exige outro app. | **Servidor Wi-Fi embutido** com interface web moderna, download com streaming (Range), upload por arrastar e soltar e chave de acesso única. |
| Ações espalhadas por menus. | **Paleta de comandos** no estilo Spotlight: você digita "limpar", "cofre" ou o nome de uma pasta e executa na hora. |

## Fontes

- Bestforandroid, "The Best File Manager Apps for Android" (jan/2026): https://bestforandroid.com/file-manager-apps.md
- AnExplorer, "Best Android File Manager" (comparativo 2026): https://auth.anexplorer.io/compare/best-android-file-manager
- iTechGuides, "10 best Android file explorer apps 2026": https://www.itechguides.com/10-best-android-file-explorer-apps-file-browsers-and-file-managers-2026/
- Techwench, "Best Android file manager apps 2026": https://www.techwench.com/best-android-file-manager-apps-2026/
- Onoff, "Best File Manager Apps for Android: 2026 Comparison": https://www.onoff.gr/blog/en/android/file-manager-android-best-apps/
- Android Authority, "Material 3 Expressive deep dive": https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/
- Hackernoon, busca por conteúdo e linguagem natural em apps Android: https://hackernoon.com/heres-how-i-stopped-scrolling-on-my-phone
- Esper, "Android 13 makes file managers less useful": https://esper.io/blog/android-dessert-bites-28-file-manager-loophole-closed-73891524
- Capgo, Scoped Storage do Android 10 ao 16: https://capgo.app/blog/android-scoped-storage-in-capacitor-apps/
- Wikipedia, "Files (Google)": https://en.wikipedia.org/wiki/Files_(Google)
