# Metrô Fortaleza — horários

App Android **não oficial** com os horários das linhas do Metrofor (Fortaleza,
Sobral e Cariri). Uma vez por dia, ao abrir, o app **baixa os horários do dia
direto do Metrofor** (veja [Horários do dia](#horários-do-dia)). Sem internet,
tudo continua funcionando com a última cópia guardada no aparelho e, em último
caso, com a grade que vem no APK.

## Baixar

**[⬇ Baixar a última versão (APK)](https://github.com/giovanildo/metro-fortaleza-horarios/releases/latest)**
— Android 7 ou mais novo. Ao abrir o arquivo, permita instalar apps dessa fonte;
se o Play Protect avisar "app não verificado", toque em *Mais detalhes →
Instalar mesmo assim*. Para atualizar sozinho, use o
[Obtainium](https://github.com/ImranR98/Obtainium) com o endereço deste
repositório.

Para divulgar, há imagens de story (1080×1920) em [`divulgacao/`](divulgacao/):
`story-qrcode.png`, só com o QR code, e `story-qrcode-link.png`, com o QR e o
link escrito e um espaço livre embaixo para a figurinha de link do Instagram. O
QR leva a `releases/latest`. Imagem não tem link clicável: no Instagram use a
figurinha "Link"; no status do WhatsApp, poste o link num status de texto.

Há também sequências com as telas do app e o link no rodapé, para o status:
`story-tela-1..3.png` (tela inicial, horários, modo viagem) e
`story-casa-1..4.png`, que conta a ida de casa até o metrô (de Pacatuba, a
1,5 km de Carlito Benevides, até José de Alencar) e termina com o aviso de
descida. No status do WhatsApp, o link escrito na **legenda** da imagem fica
clicável.

A tela inicial destaca uma estação com a próxima partida de cada sentido — nos
terminais, só o sentido em que dá para partir (o que termina ali só teria
chegadas), e a tela de horários de um terminal já abre nesse sentido. Pelo
GPS, se houver estações de **outras linhas a até 3 km**, cada linha ganha seu
cartão (por exemplo, Chico da Silva na Linha Sul e Moura Brasil na Oeste, a
~200 m uma da outra), e o modo viagem oferece todas como ponto de partida. A
estação em destaque pode vir de duas fontes:

- **A mais próxima de você**, o que exige `ACCESS_FINE_LOCATION`. A permissão só
  é pedida quando você toca no botão, nunca ao abrir o app, e as features de
  localização estão declaradas com `required="false"` — o app instala e funciona
  em aparelho sem GPS.
- **Uma estação fixada à mão**, pela estrela na tela de partidas. Não exige
  permissão nenhuma, e ganha da localização quando existe, porque é uma escolha
  explícita.

Sem permissão e sem estação fixada, o app continua inteiro: o cartão vira um
convite e o resto funciona igual.

A localização usa o `LocationManager` do próprio Android, sem Play Services.
Para achar a posição, o app pede **ao mesmo tempo** ao provedor do Google
(`fused`, o mesmo do Maps, quando o aparelho tem), à rede (Wi-Fi e antenas) e
ao GPS, e fica com a primeira posição boa (até 500 m de erro), esperando até
30 s; sem nada a tempo, usa a última posição guardada de até 30 min. Pedir só
ao GPS fazia a primeira localização falhar em quem acabou de instalar o app —
GPS frio não acha satélites em segundos dentro de casa ou do trem — e só abrir o
Maps destravava. Se mesmo assim falhar, o app continua escutando enquanto a
tela está aberta, e a estação aparece sozinha quando a posição chegar.
Com a tela inicial aberta e a estação vindo do GPS, o app segue a posição
continuamente (a cada 10 s ou 30 m): ao descer em outra estação, o destaque
muda sozinho. Uma estação fixada pela estrela continua tendo prioridade.

### Integração entre linhas

Nas estações de troca de linha, a tela de horários mostra um bloco **"Integração
com"** a outra linha: o nome da estação de lá, o tempo a pé e, em cada sentido, o
primeiro trem **que ainda dá para pegar** (partida depois de agora + caminhada).
Tocar no bloco abre a outra estação já no sentido mostrado. Nos terminais, o
sentido que só chega ali fica de fora. Os pontos vêm da tabela `transfer` do
banco (`TRANSFERS` em `tools/build_db.py` e `GtfsImporter.kt`):

| Integração | Linhas | A pé |
|---|---|---|
| Parangaba ↔ Parangaba - NE | Sul ↔ Nordeste | 2 min |
| Expedicionários ↔ Expedicionários - AE | Nordeste ↔ VLT Aeroporto | 3 min |
| Chico da Silva ↔ Moura Brasil | Sul ↔ Oeste | 5 min (~200 m) |

**Passagem.** A regra fica em `data/TransferFares.kt`, fixa no código porque não
vem do GTFS, e vale **por sentido** (de → para), com um motivo opcional:

| De → Para | Passagem |
|---|---|
| Chico da Silva → Moura Brasil (Sul → Oeste) | paga |
| Moura Brasil → Chico da Silva (Oeste → Sul) | paga |
| Parangaba → Parangaba - NE (Sul → Nordeste) | **gratuita** — a Linha Nordeste está gratuita durante a implantação |
| Parangaba - NE → Parangaba (Nordeste → Sul) | paga — entrar na estação da Linha Sul cobra |
| Expedicionários ↔ Expedicionários - AE (Nordeste ↔ VLT Aeroporto) | não informada |

Sem regra (`UNKNOWN`) o app não fala de passagem, para não informar errado.
Quando a Nordeste começar a cobrar, basta trocar a entrada que cita o motivo.

### Modo viagem

O cartão **Modo viagem** fica no topo da tela inicial: a origem é a estação em
destaque (GPS ou fixada) e o destino se escolhe entre as estações seguintes,
nos dois sentidos. O mesmo botão existe na tela de partidas de cada estação.
Durante a viagem, o cartão do topo passa a mostrar o andamento, e um serviço em
primeiro plano, com notificação fixa, segue a viagem com a tela desligada:

- **Onde o trem está.** Pelo GPS, quando a posição é precisa (≤150 m) e está a
  até 300 m de uma estação do caminho. Cada estação confirmada mede o atraso
  em relação à grade. Quando o sinal some (dentro do trem, no trecho
  subterrâneo), a posição passa a sair **da tabela de horários**, corrigida
  por esse atraso. A tela diz qual das duas fontes está valendo.
- **Quando o GPS é dado como perdido.** Depois de **1 minuto** sem nenhuma
  posição boa (12 tentativas, uma a cada 5 s) — menos que os ~2 min entre duas
  estações. A partir daí o cartão mostra um quadro em destaque, de fundo neutro
  e letra legível (ícone vermelho só para chamar atenção): "**Sem GPS há N
  min** — Posição estimada pela tabela de horários. Confira o nome das
  estações pela janela." A notificação fixa ganha
  "sem GPS há N min", e todo aviso baseado na tabela diz isso: a estação vira
  "Provavelmente Parangaba" e o aviso de descida começa com "Pela tabela de
  horários". A tabela é a "média" da viagem: os horários programados do
  Metrofor, deslocados pelo atraso que o GPS mediu na última estação vista.
- **Aviso duas estações antes do destino**, com notificação e vibração. O
  botão de alto-falante no cartão da viagem liga ou desliga o **som**; com som,
  o aviso usa o áudio de alarme, que toca mesmo com o celular no vibrar.
- **Avisar cada estação (voz)**, opcional e desligado por padrão: a cada
  estação alcançada, uma notificação curta e a voz do sistema (o "Conversão de
  texto em voz" do Android, em pt-BR) dizem, por exemplo, "Parangaba. Faltam 5
  estações para Benfica." Com o som ligado, o aviso de duas estações antes
  também é falado. A voz sai pelo canal de navegação: toca no fone e abaixa a
  música. Sem voz pt-BR instalada no aparelho, fica só a notificação.
- **Distância até a partida.** Se a pessoa está a mais de **1 km** da estação de
  partida (ajustável), o app avisa antes de iniciar (até o GPS a encontrar na
  linha, a posição sai só da tabela). A mais de **3 km** (ajustável), a opção some,
  na tela inicial e na de partidas. A distância vem do GPS da tela inicial ou,
  com estação fixada, da última posição guardada no aparelho; sem posição
  conhecida, o app deixa iniciar.
- **Ir sentado pelo terminal.** Perto de um terminal (até 3 estações,
  ajustável; 0 desliga), indo no sentido contrário a ele, o app pergunta "Como
  você quer ir?": **direto** ou **sentado** — seguir até o terminal, onde o trem
  esvazia, e voltar nele. Mostra os horários dos dois e quanto o sentado custa
  (em geral um intervalo, ~18 min na Linha Sul). No terminal avisa "fique no
  trem, ele volta às HH:MM"; na volta, as estações repetidas são reconhecidas
  como volta (o GPS sempre casa com a próxima ocorrência à frente). O trem não
  sai do terminal antes do horário: adiantamento medido na ida é descartado ali.
  O app não sabe a lotação — é uma sugestão de quem anda de metrô.
- O destino pode estar nos **dois sentidos**, tanto pelo cartão do topo quanto
  pelo botão da tela de horários (antes, ali, só o sentido da aba aberta).
- Durante a viagem, o cartão da estação em destaque some da tela inicial: o da
  viagem já diz onde a pessoa está.
- A viagem encerra sozinha ao chegar, ou 30 min depois da chegada prevista.
- Precisa da permissão de localização (o Android não deixa o serviço rodar sem
  ela) e, no Android 13+, pede também a de notificações.

O app mostra a **estação do Bicicletar mais próxima** com nome, número,
distância e capacidade de vagas, em dois lugares (são 253 estações no banco,
também offline):

- **Na tela inicial**, num cartão próprio logo abaixo do da estação. Com GPS, a
  distância é medida a partir de **você**, até 3 km — acima disso (fora de
  Fortaleza, por exemplo) o cartão some. Com estação fixada, é medida a partir
  da estação, até 600 m.
- **Na tela de partidas** de cada estação, medida a partir dela e escondida
  acima de 600 m, porque a essa distância deixa de ajudar.

Nas linhas Sul, Oeste e Nordeste, o app diz também **se dá para embarcar com
bicicleta agora** (no metrô ou no VLT, conforme a linha; o aviso some quando
não há mais partidas no dia), na tela de partidas e no cartão da tela inicial. As
janelas vêm do regulamento
[*Bike é bem-vinda no metrô*](https://www.ce.gov.br/metrofor/wp-content/uploads/sites/75/2023/03/Regula_Bikes_2023.pdf)
(Metrofor, 2023): segunda a sexta das 9h às 15h e a partir das 20h; sábado a
partir das 15h. Os VLTs não aparecem no regulamento, por isso ficam de fora; no
domingo o aviso de bike some, porque nesse dia só há operação especial e a regra
dela não é conhecida. As regras estão fixas em `data/BikeBoarding.kt` — se o
Metrofor mudar o regulamento, é lá que se mexe.

Um ícone de **engrenagem** no topo abre as **Configurações**, com as distâncias
e os tempos que antes eram fixos no código (`data/Settings.kt`; padrões entre
parênteses):

| Ajuste | Padrão | Faixa |
|---|---|---|
| Mostrar outras linhas até | 3 km | 500 m – 5 km |
| Bicicletar perto de você até | 3 km | 500 m – 5 km |
| Bicicletar perto da estação até | 600 m | 200 m – 1,5 km |
| Avisar antes do destino | 2 estações | 1 – 4 |
| Perguntar antes de começar a viagem a partir de | 1 km | 500 m – 3 km |
| Esconder o modo viagem a partir de | 3 km | 1 – 10 km |
| Considerar o GPS perdido depois de | 1 min | 30 s – 5 min |
| Contar o trem que saiu há até | 2 min | 0 – 10 min |
| Encerrar a viagem sozinho depois de | 30 min | 10 – 60 min |
| Oferecer ir sentado pelo terminal até | 3 estações | 0 (não oferecer) – 5 |
| Atualizar sua posição (tela inicial) a cada | 10 s | 5 – 60 s |

O aviso de distância nunca fica maior que o limite em que o modo viagem some
(mexer num ajusta o outro). "Restaurar padrões" volta tudo. Os parâmetros
técnicos do GPS (precisão mínima, raio de estação) continuam fixos em
`data/Trip.kt`, porque mexer neles exige entender o algoritmo.

Um ícone de **informação** no topo abre a tela **Sobre**: o aviso de que o app
não é oficial, de onde vêm os dados, a licença, o link do código-fonte e uma
chave Pix para quem quiser apoiar o projeto (`PIX_KEY` em `ui/AboutScreen.kt`;
vazia, a seção de doação some).

## Horários do dia

O GTFS do Metrofor é gerado a cada requisição e, pelo que observamos, traz a
**grade do dia em que é baixado**, embora o `calendar.txt` diga que vale todos
os dias:

| Baixado em | Linha Sul |
|---|---|
| quinta, 24/09 | 5h30 – 23h18, 53 viagens por sentido (dia útil) |
| sábado, 03/10 | 5h30 – 17h08, 37 viagens por sentido |
| domingo, 04/10 (eleição) | 7h30 – 18h10 (operação especial) |

Por isso o app baixa o feed **no próprio celular** uma vez por dia
(`data/ScheduleStore.kt`), monta o banco com o mesmo resultado de
`tools/build_db.py` (`data/GtfsImporter.kt`, uma tradução direta do script, que
gera tabelas idênticas para o mesmo feed) e guarda a última grade de cada tipo
de dia: dia útil, sábado e domingo. A grade de hoje vale até a meia-noite; sem
internet, o app usa a última do mesmo tipo e avisa em vermelho de que dia ela
é. O banco do APK fica como último recurso. Uma linha no topo da tela inicial
diz de onde vêm os horários e tem um botão para baixar de novo.

Detalhes que valem saber:

- **Certificado vencido.** O certificado HTTPS de `*.metrofor.ce.gov.br`
  costuma estar expirado. O app aceita qualquer certificado **só nessa
  conexão**: são dados públicos de horário, e o pior que um intermediário
  faria é mostrar uma grade errada.
- **Nada de trava por "grade menor".** Como a grade muda de verdade de um dia
  para outro, o app só recusa um feed que não dá para ler ou que não tem linhas
  nem estações. Zero viagens é aceito: num domingo comum pode ser exatamente a
  verdade.
- **Domingo.** Se a grade de domingo foi baixada hoje, ela já diz se há
  operação especial e o aviso de domingo some. Sem ela, o aviso aparece.
- **Depois da última viagem do dia**, as partidas de "amanhã" vêm da grade do
  tipo de dia de amanhã, e não da de hoje repetida: num domingo de eleição,
  segunda mostra a grade de dia útil. Se amanhã for sábado ou domingo e essa
  grade nunca tiver sido baixada, o app não inventa horário.
- **Bicicletar** não vem do GTFS: as estações são copiadas do banco do APK.
- **Gere o banco do APK num dia útil.** Rodar `tools/build_db.py` num sábado ou
  domingo embarcaria a grade daquele dia como reserva para todos os dias.

## Como os dados chegam aqui

O Metrofor publica um feed [GTFS](https://gtfs.org) em
`https://info.metrofor.ce.gov.br/gtfs_file` (linkado na
[página oficial de GTFS](https://www.ce.gov.br/metrofor/gtfs/)).

`tools/build_db.py` baixa esse feed e o converte num SQLite achatado para a
consulta que o app faz o tempo todo — *"quais as próximas partidas nesta
estação, nesta linha, neste sentido?"*:

```bash
python3 tools/build_db.py            # baixa o feed mais recente
python3 tools/build_db.py feed.zip   # ou usa um zip local
```

A saída é `app/src/main/assets/metrofor.db` (~200 KB), com 7 linhas,
64 estações e 3.877 partidas. É a reserva que vai no APK; no dia a dia o app
usa a grade que ele mesmo baixa.

Junto do banco o script grava `metrofor.db.version`, com o carimbo de geração.
O app lê esse arquivo minúsculo direto dos assets a cada arranque e só reinstala
o banco quando o carimbo muda — então basta rodar o gerador e recompilar, sem
precisar mexer no `versionCode`.

O script também normaliza a capitalização, porque o feed vem todo em Title Case
(`Vlt Sobral`, `Chico Da Silva`, `Cohab Iii`).

## Mantendo os dados atualizados

`tools/check_updates.py` compara as fontes com o último estado conhecido e diz
o que mudou:

```bash
python3 tools/check_updates.py           # compara e informa
python3 tools/check_updates.py --notify  # avisa na área de trabalho
```

Ele compara o **conteúdo** das fontes, nunca os bytes: o Metrofor gera o zip do
GTFS a cada requisição, então o arquivo muda sempre e o conteúdo quase nunca.
Comparar o arquivo daria alarme falso toda semana.

Para agendar isso semanalmente, num timer de usuário do systemd (sem root):

```bash
bash tools/install-weekly-check.sh            # instala e ativa
bash tools/install-weekly-check.sh --remove   # desfaz
```

Roda segunda-feira às 10h e, com `Persistent=true`, recupera a execução se a
máquina estiver desligada na hora. Quando algo mudar, aparece uma notificação —
aí é rodar `tools/build_db.py` e recompilar. Com o download diário no celular,
isso só serve para renovar a reserva do APK; como o conteúdo muda conforme o
dia da semana, espere avisos quando a checagem não cair num dia útil.

## Limitações conhecidas dos dados

Estas vêm do feed oficial, não do app:

1. **O `calendar.txt` tem um único serviço valendo os sete dias da semana.** O
   feed não distingue dia útil, sábado e domingo, então o app mostra o mesmo
   quadro todo dia e exibe um aviso na tela de horários. Se você conferir os
   quadros publicados e eles divergirem, é preciso modelar os dias à mão.
   **Domingo é o caso mais grave:** metrô e VLTs normalmente não circulam, a
   não ser em operação especial (eleição, ENEM, eventos), mas o feed diz que
   sim. Por isso, aos domingos, o app põe um aviso em destaque na tela inicial
   e na de partidas. As partidas continuam visíveis como referência para os
   dias de operação especial.
2. **O `calendar_dates.txt` está desatualizado**: lista feriados de 2025,
   enquanto o feed diz valer até 2027. O app ignora esse arquivo.
3. **Não há `transfers.txt`.** As três baldeações reais foram derivadas por
   distância entre estações de linhas diferentes e estão declaradas em
   `TRANSFERS`, dentro do gerador:
   - Parangaba ↔ Parangaba-NE (Linha Sul ↔ Linha Nordeste)
   - Expedicionários ↔ Expedicionários-AE (Linha Nordeste ↔ VLT Aeroporto)
   - Chico da Silva ↔ Moura Brasil (Linha Sul ↔ Linha Oeste, ~200 m a pé)

   A tabela `transfer` já existe no banco, mas **a tela de baldeação não foi
   construída** — ficou para a v2.
4. **Não há GTFS-RT.** Tudo aqui é horário programado: o app não sabe de
   atrasos nem da posição real dos trens.
5. **O Bicicletar não tem disponibilidade em tempo real.** O GeoJSON da AMC traz
   posição e capacidade de vagas, nunca quantas bicicletas estão na estação
   agora. O sistema não está no registro oficial do
   [GBFS](https://github.com/MobilityData/gbfs) — só Brasília, no Brasil — e o
   site oficial bloqueia acesso automatizado (HTTP 403). O arquivo usado é de
   22/07/2025.
6. **Não há dados de ônibus.** A integração temporal da Etufor é uma regra
   tarifária, não um horário; o GTFS de ônibus de Fortaleza é
   [publicado à parte](https://dados.fortaleza.ce.gov.br/dataset/?tags=gtfs).
7. O domínio `metrofor.ce.gov.br` está com **certificado HTTPS expirado** e
   redireciona para `ce.gov.br/metrofor`. Por isso o gerador desliga a
   verificação de certificado, e o app aceita qualquer certificado **só** na
   conexão com `info.metrofor.ce.gov.br` (veja [Horários do dia](#horários-do-dia)).

## Build

O jeito mais curto, a partir da raiz do projeto:

```bash
bash tools/setup-dev.sh
```

O script instala **JDK 21**, **Gradle 9.8** e o **Android SDK 37** dentro do seu
diretório de usuário (`~/.local/opt` e `~/Android/Sdk`), sem usar `sudo` e sem
tocar no sistema, confere o checksum de cada download, gera o
`gradle-wrapper.jar` e compila o APK de debug
em `app/build/outputs/apk/debug/`.

Ele é idempotente: se você já tiver um JDK 21 (por exemplo via
`sudo apt install openjdk-21-jdk`), ele reaproveita e pula a etapa.

Em terminais novos, carregue o ambiente antes de usar `gradle` ou `adb`:

```bash
. ~/.local/opt/android-env.sh
./gradlew assembleDebug
```

Abrindo em Android Studio (Ladybug ou mais novo), nada disso é necessário: ele
resolve o SDK e o wrapper sozinho.

## Estrutura

```
tools/build_db.py                    GTFS oficial -> SQLite embarcado
app/src/main/assets/metrofor.db      banco gerado (não editar à mão)
app/src/main/java/.../data/          modelos, abertura do banco, consultas, relógio
app/src/main/java/.../ui/            telas Compose: linhas -> estações -> partidas
```

O app não usa Room nem navigation-compose: são três telas e cinco consultas
SQL, e o banco é somente-leitura.

O build usa **AGP 9**, que compila Kotlin nativamente — por isso não existe
plugin `org.jetbrains.kotlin.android` aqui, só o do compilador do Compose.
`compileSdk` está em 37 para acompanhar as bibliotecas, mas `targetSdk` ficou
em 36 de propósito: subir o `targetSdk` muda comportamento em tempo de
execução, e o app ainda não foi testado em aparelho. É a origem do único aviso
de lint que sobrou.

## Publicando uma versão no GitHub

A release é assinada com uma chave própria, que fica **só na máquina de quem
publica** (`~/.android/metro-fortaleza-release.jks`), com as senhas em
`keystore.properties` na raiz — os dois fora do git. **Guarde uma cópia do
`.jks` e das senhas num lugar seguro**: sem eles, as próximas versões não
instalam por cima, e cada pessoa teria que desinstalar o app.

```bash
./gradlew assembleRelease            # app/build/outputs/apk/release/app-release.apk
cp app/build/outputs/apk/release/app-release.apk metro-fortaleza.apk
gh release create vX.Y metro-fortaleza.apk --notes-file notas.md
```

O arquivo vai sempre com o nome `metro-fortaleza.apk`, para o link
`releases/latest/download/metro-fortaleza.apk` (e o QR code) nunca mudarem.

## Publicação no F-Droid

O app vai ser distribuído pelo [F-Droid](https://f-droid.org), que compila a
partir deste repositório. O que já está pronto aqui:

- **Textos e imagens da loja** em `fastlane/metadata/android/pt-BR/`: título,
  descrições, ícone, capturas de tela e `changelogs/<versionCode>.txt`.
- **A receita** em `fdroid/io.github.giova.metrofortaleza.yml`, para copiar
  para `metadata/` no [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
- **Uma tag por versão** (`v1.7`, …): com `UpdateCheckMode: Tags`, o F-Droid
  acha versões novas sozinho.

Para cada versão nova: subir `versionCode`/`versionName`, escrever
`changelogs/<versionCode>.txt`, criar a tag `vX.Y` e dar push com `--tags`.

Para o primeiro envio, alguém com conta no GitLab faz um fork do fdroiddata,
copia a receita e abre um *merge request* (o guia é
[Submitting to F-Droid](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)).
Duas coisas para explicar no pedido: o `app/src/main/assets/metrofor.db` é
**dado**, não código — é gerado por `tools/build_db.py` a partir do GTFS público
e serve só de reserva, porque o app baixa os horários do dia sozinho; e o
build usa AGP 9 com `compileSdk` 37, que o servidor de build do F-Droid precisa
suportar.

## Licença

Copyright (C) 2026 Giovanildo

Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
os termos da Licença Pública Geral GNU, conforme publicada pela Free Software
Foundation, na versão 3 da licença ou (a seu critério) qualquer versão
posterior.

Este programa é distribuído na esperança de que seja útil, mas **sem nenhuma
garantia**, nem mesmo a garantia implícita de comercialização ou adequação a
um fim específico. Veja a [Licença Pública Geral GNU](LICENSE) para mais
detalhes.

### Sobre os dados

A licença acima cobre **o código**, não os dados. O `metrofor.db` versionado
aqui é gerado a partir de fontes públicas de terceiros, cada uma com seus
próprios termos:

- **Horários e estações do metrô** — feed GTFS publicado pelo
  [Metrofor](https://www.ce.gov.br/metrofor/gtfs/), empresa do Governo do
  Estado do Ceará.
- **Estações do Bicicletar** — GeoJSON publicado pela AMC no
  [portal de dados abertos da Prefeitura de Fortaleza](https://dados.fortaleza.ce.gov.br/organization/amc).

Este é um app **não oficial**, sem vínculo com o Metrofor, a AMC, a Prefeitura
de Fortaleza ou o Governo do Ceará.

### Sobre o nome e a marca

"Metrofor" é o nome da Companhia Cearense de Transportes Metropolitanos. O app
cita o nome **só para dizer de onde vêm os dados** (uso nominativo): ele se
chama "Metrô Fortaleza", não usa "Metrofor" no nome nem no ícone, não reproduz
o logotipo, e traz o aviso de não oficial na tela inicial e na tela Sobre. As
cores das linhas vêm do próprio GTFS e servem só para identificá-las. Quem for
publicar o app em loja deve manter esses cuidados — nome, ícone e descrição sem
sugerir vínculo oficial.
