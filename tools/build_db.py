#!/usr/bin/env python3
"""Converte o GTFS oficial do Metrofor no banco SQLite embarcado no app.

Uso:
    python3 tools/build_db.py                 # baixa o feed mais recente
    python3 tools/build_db.py feed.zip        # usa um zip local

Saida: app/src/main/assets/metrofor.db
"""
import csv
import io
import json
import math
import os
import ssl
import sqlite3
import sys
import urllib.request
import zipfile
from collections import defaultdict
from datetime import datetime, timezone

FEED_URL = "https://info.metrofor.ce.gov.br/gtfs_file"
# Estacoes do Bicicletar, publicadas pela AMC no portal de dados abertos da
# prefeitura. Sao apenas as posicoes: nao existe fonte publica de quantas
# bicicletas estao disponiveis agora.
BIKE_URL = (
    "https://dados.fortaleza.ce.gov.br/dataset/1294172b-36f8-4c6d-a356-029e460830e5"
    "/resource/c6bef3ce-6846-431f-9e26-07e01f064413/download/"
    "dadosabertos_estacoesbicicletar.geojson"
)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "metrofor.db")
# Arquivo minusculo que o app le direto dos assets, sem precisar copiar o banco,
# para saber se o que ele ja instalou esta desatualizado.
OUT_VERSION = OUT + ".version"

# O feed nao traz transfers.txt. Estas baldeacoes foram derivadas por distancia
# entre estacoes de linhas diferentes (<1 km) e conferidas manualmente. Sao
# declaradas por NOME de estacao e resolvidas para stop_id na geracao, para nao
# quebrarem caso o Metrofor renumere as estacoes.
TRANSFERS = [
    ("Parangaba", "Parangaba - Ne", 2, "Mesma estacao"),
    ("Expedicionarios", "Expedicionarios - Ae", 3, "Passarela"),
    ("Chico Da Silva", "Moura Brasil", 5, "A pe, ~200 m"),
]
MAX_TRANSFER_METERS = 400

SCHEMA = """
PRAGMA journal_mode=DELETE;
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE route (
    route_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL,
    color TEXT NOT NULL, text_color TEXT NOT NULL, url TEXT NOT NULL, sort_order INTEGER NOT NULL);
CREATE TABLE stop (
    stop_id TEXT PRIMARY KEY, code TEXT NOT NULL, name TEXT NOT NULL,
    lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE direction (
    route_id TEXT NOT NULL, direction_id INTEGER NOT NULL, headsign TEXT NOT NULL,
    PRIMARY KEY (route_id, direction_id));
CREATE TABLE route_stop (
    route_id TEXT NOT NULL, direction_id INTEGER NOT NULL, seq INTEGER NOT NULL,
    stop_id TEXT NOT NULL, PRIMARY KEY (route_id, direction_id, seq));
CREATE TABLE departure (
    route_id TEXT NOT NULL, direction_id INTEGER NOT NULL, stop_id TEXT NOT NULL,
    minutes INTEGER NOT NULL, trip_id TEXT NOT NULL);
CREATE TABLE bike_station (
    number INTEGER PRIMARY KEY, name TEXT NOT NULL, district TEXT NOT NULL,
    slots INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL);
CREATE TABLE transfer (
    from_stop_id TEXT NOT NULL, to_stop_id TEXT NOT NULL,
    walk_minutes INTEGER NOT NULL, note TEXT NOT NULL,
    PRIMARY KEY (from_stop_id, to_stop_id));
CREATE INDEX idx_departure_lookup ON departure (stop_id, route_id, direction_id, minutes);
CREATE INDEX idx_route_stop_stop ON route_stop (stop_id);
"""


def load_bike_stations():
    """As estacoes do Bicicletar adulto que existem hoje.

    Devolve lista vazia (com aviso) se o portal estiver fora do ar: o banco do
    metro e o que importa, e o app trata a ausencia sem quebrar.
    """
    print(f"baixando {BIKE_URL}")
    try:
        req = urllib.request.Request(BIKE_URL, headers={"User-Agent": "metrofor-horarios/1.0"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.load(resp)
    except Exception as exc:  # noqa: BLE001 - qualquer falha de rede serve
        print(f"  AVISO: nao foi possivel baixar as estacoes do Bicicletar ({exc})")
        return []

    rows = []
    for feature in data.get("features", []):
        props = feature.get("properties", {})
        status = (props.get("STATUS") or "").upper()
        kind = (props.get("TIPO") or "").upper()
        # MINIBICICLETAR e infantil, com bicicletas que nao servem para adulto.
        if not status.startswith("EXISTENTE") or kind != "BICICLETAR":
            continue
        number, lat, lon = props.get("ID"), props.get("LAT"), props.get("LONG")
        if number is None or lat is None or lon is None:
            continue
        rows.append((
            int(number),
            pretty((props.get("NOME") or "").strip()),
            pretty((props.get("BAIRRO") or "").strip()),
            int(props.get("VAGAS ATUAIS") or 0),
            float(lat),
            float(lon),
        ))
    return rows


def load_feed(source):
    if source:
        print(f"lendo {source}")
        return zipfile.ZipFile(source)
    print(f"baixando {FEED_URL}")
    # O certificado de *.metrofor.ce.gov.br costuma estar expirado.
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(FEED_URL, headers={"User-Agent": "metrofor-horarios/1.0"})
    with urllib.request.urlopen(req, context=ctx, timeout=60) as resp:
        return zipfile.ZipFile(io.BytesIO(resp.read()))


def read(zf, name):
    with zf.open(name) as fh:
        text = io.TextIOWrapper(fh, encoding="utf-8-sig", newline="")
        return list(csv.DictReader(text))


# O GTFS do Metrofor vem todo em Title Case ("Vlt Sobral", "Chico Da Silva",
# "Cohab Iii"), o que fica estranho na tela. Normalizamos na geracao para o app
# nao precisar saber disso.
CONNECTIVES = {"de", "da", "do", "das", "dos", "e"}
ALWAYS_UPPER = {"vlt", "ii", "iii", "iv", "ne", "ae"}


def pretty(text):
    tokens = text.split()
    out = []
    for i, token in enumerate(tokens):
        low = token.lower()
        if low in ALWAYS_UPPER:
            out.append(token.upper())
        elif low in CONNECTIVES and i > 0:
            out.append(low)
        else:
            out.append(token)
    return " ".join(out)


def haversine(a, b):
    """Distancia em metros entre duas paradas do GTFS."""
    lat1, lon1, lat2, lon2 = (math.radians(float(v)) for v in (
        a["stop_lat"], a["stop_lon"], b["stop_lat"], b["stop_lon"]))
    h = (math.sin((lat2 - lat1) / 2) ** 2
         + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2)
    return 2 * 6371000 * math.asin(math.sqrt(h))


def to_minutes(hhmmss):
    h, m, s = (int(p) for p in hhmmss.strip().split(":"))
    return h * 60 + m + (1 if s >= 30 else 0)


def main():
    source = sys.argv[1] if len(sys.argv) > 1 else None
    zf = load_feed(source)

    routes = read(zf, "routes.txt")
    stops = read(zf, "stops.txt")
    trips = read(zf, "trips.txt")
    stop_times = read(zf, "stop_times.txt")
    feed_info = read(zf, "feed_info.txt")
    calendar = read(zf, "calendar.txt")

    trip_by_id = {t["trip_id"]: t for t in trips}
    by_trip = defaultdict(list)
    for row in stop_times:
        by_trip[row["trip_id"]].append(row)
    for rows in by_trip.values():
        rows.sort(key=lambda r: int(r["stop_sequence"]))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    if os.path.exists(OUT):
        os.remove(OUT)
    db = sqlite3.connect(OUT)
    db.executescript(SCHEMA)

    db.executemany(
        "INSERT INTO route VALUES (?,?,?,?,?,?,?)",
        [(r["route_id"], pretty((r["route_short_name"] or r["route_long_name"]).strip()),
          pretty(r["route_desc"].strip()), r["route_color"].strip() or "777777",
          r["route_text_color"].strip() or "FFFFFF", r["route_url"].strip(),
          int(r["route_id"])) for r in routes])

    db.executemany(
        "INSERT INTO stop VALUES (?,?,?,?,?)",
        [(s["stop_id"], s["stop_code"].strip(), pretty(s["stop_name"].strip()),
          float(s["stop_lat"]), float(s["stop_lon"])) for s in stops])

    # trip_headsign e confiavel: bate com a estacao final em todas as viagens.
    directions = {}
    for t in trips:
        key = (t["route_id"], int(t["direction_id"]))
        directions.setdefault(key, pretty(t["trip_headsign"].strip()))
    db.executemany("INSERT INTO direction VALUES (?,?,?)",
                   [(r, d, h) for (r, d), h in directions.items()])

    # A sequencia de estacoes vem da viagem mais longa de cada sentido.
    longest = {}
    for trip_id, rows in by_trip.items():
        t = trip_by_id[trip_id]
        key = (t["route_id"], int(t["direction_id"]))
        if key not in longest or len(rows) > len(longest[key]):
            longest[key] = rows
    route_stop = []
    for (route_id, direction_id), rows in longest.items():
        for i, row in enumerate(rows):
            route_stop.append((route_id, direction_id, i, row["stop_id"]))
    db.executemany("INSERT INTO route_stop VALUES (?,?,?,?)", route_stop)

    departures = []
    for row in stop_times:
        t = trip_by_id[row["trip_id"]]
        departures.append((t["route_id"], int(t["direction_id"]), row["stop_id"],
                           to_minutes(row["departure_time"]), row["trip_id"]))
    db.executemany("INSERT INTO departure VALUES (?,?,?,?,?)", departures)

    by_name = {}
    for s in stops:
        by_name.setdefault(s["stop_name"].strip().lower(), s)
    rows = []
    for name_a, name_b, walk, note in TRANSFERS:
        a = by_name.get(name_a.lower())
        b = by_name.get(name_b.lower())
        if not a or not b:
            print(f"aviso: baldeacao ignorada, estacao nao encontrada: {name_a} / {name_b}")
            continue
        dist = haversine(a, b)
        if dist > MAX_TRANSFER_METERS:
            print(f"aviso: baldeacao ignorada, {name_a} e {name_b} estao a {dist:.0f} m")
            continue
        rows.append((a["stop_id"], b["stop_id"], walk, note))
        rows.append((b["stop_id"], a["stop_id"], walk, note))
    db.executemany("INSERT INTO transfer VALUES (?,?,?,?)", rows)

    bikes = load_bike_stations()
    db.executemany("INSERT INTO bike_station VALUES (?,?,?,?,?,?)", bikes)

    # O feed tem um unico service_id valendo os sete dias da semana: nao ha
    # distincao util/sabado/domingo. O app precisa avisar isso ao usuario.
    same_every_day = all(
        all(c[d] == "1" for d in ("monday", "tuesday", "wednesday", "thursday",
                                  "friday", "saturday", "sunday"))
        for c in calendar)
    info = feed_info[0] if feed_info else {}
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    db.executemany("INSERT INTO meta VALUES (?,?)", [
        ("schema_version", "1"),
        ("feed_version", info.get("feed_version", "")),
        ("feed_start_date", info.get("feed_start_date", "")),
        ("feed_end_date", info.get("feed_end_date", "")),
        ("source_url", FEED_URL),
        ("generated_at", generated_at),
        ("same_schedule_every_day", "1" if same_every_day else "0"),
        ("bike_station_count", str(len(bikes))),
    ])

    db.commit()
    db.execute("VACUUM")
    db.close()

    with open(OUT_VERSION, "w", encoding="utf-8") as fh:
        fh.write(generated_at)

    size = os.path.getsize(OUT)
    print(f"ok: {OUT} ({size // 1024} KB)")
    print(f"  {len(routes)} linhas, {len(stops)} estacoes, {len(trips)} viagens, "
          f"{len(departures)} partidas, {len(rows) // 2} baldeacoes, "
          f"{len(bikes)} estacoes Bicicletar")
    if same_every_day:
        print("  ATENCAO: o feed usa o mesmo quadro de horarios nos sete dias da semana")


if __name__ == "__main__":
    main()
