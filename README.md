# Metrô Fortaleza — horários

App Android **não oficial** com os horários das linhas do Metrofor (Fortaleza,
Sobral e Cariri). O quadro de horários vai embarcado no APK, então as consultas
funcionam inteiramente offline. A única coisa que usa a rede são as
**manchetes de notícias** (veja abaixo), e sem internet o app mostra a última
lista baixada.

A tela inicial destaca uma estação com a próxima partida de cada sentido. Ela
pode vir de duas fontes:

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
bicicleta no trem agora**, na tela de partidas e no cartão da tela inicial. As
janelas vêm do regulamento
[*Bike é bem-vinda no metrô*](https://www.ce.gov.br/metrofor/wp-content/uploads/sites/75/2023/03/Regula_Bikes_2023.pdf)
(Metrofor, 2023): segunda a sexta das 9h às 15h e a partir das 20h; sábado a
partir das 15h. Os VLTs não aparecem no regulamento, por isso ficam de fora; no
domingo o aviso de bike some, porque nesse dia só há operação especial e a regra
dela não é conhecida. As regras estão fixas em `data/BikeBoarding.kt` — se o
Metrofor mudar o regulamento, é lá que se mexe.

A tela inicial termina com as **notícias do Metrofor**: só manchetes, fonte e
data, para bater o olho e saber se vem alguma mudança (operação especial em
eleição e ENEM, obras, feriado). Nem o site do Metrofor nem o do Governo do
Ceará têm RSS ou API, então as manchetes vêm da busca RSS do Google Notícias
(`metrofor OR "metrô de fortaleza" OR "VLT" Ceará`), limitada aos últimos 30
dias. A lista fica guardada no aparelho e é baixada de novo ao abrir o app se
tiver mais de 3 horas, ou pelo botão de atualizar. Não é uma API oficial: se
ela falhar, a lista antiga continua na tela.

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
64 estações e 3.877 partidas.

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
aí é rodar `tools/build_db.py` e recompilar.

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
   verificação de certificado — e por isso o app nunca fala com o servidor do
   Metrofor: quem baixa o feed é você, na sua máquina.

## Build

O jeito mais curto, a partir da raiz do projeto:

```bash
bash tools/setup-dev.sh
```

O script instala **JDK 21**, **Gradle 9.8** e o **Android SDK 37** dentro do seu
diretório de usuário (`~/.local/opt` e `~/Android/Sdk`), sem usar `sudo` e sem
tocar no sistema, confere o checksum de cada download, gera o
`gradle-wrapper.jar` — que não está versionado aqui — e compila o APK de debug
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

Este é um app **não oficial**, sem vínculo com o Metrofor, a AMC ou a
Prefeitura de Fortaleza.
