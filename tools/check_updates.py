#!/usr/bin/env python3
"""Verifica se as fontes de dados do app mudaram.

    python3 tools/check_updates.py            # compara e informa
    python3 tools/check_updates.py --notify   # ainda avisa na area de trabalho
    python3 tools/check_updates.py --save     # grava o estado atual sem comparar

Codigos de saida: 0 = nada mudou, 10 = algo mudou, 1 = erro ao consultar.

Compara o CONTEUDO das fontes, nunca os bytes: o Metrofor gera o zip do GTFS a
cada requisicao, entao os bytes mudam sempre e o conteudo quase nunca.
"""
import argparse
import hashlib
import io
import json
import os
import ssl
import subprocess
import sys
import urllib.request
import zipfile
from collections import defaultdict
from datetime import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATE = os.path.join(ROOT, "tools", "data-state.json")

GTFS_URL = "https://info.metrofor.ce.gov.br/gtfs_file"
BIKE_URL = (
    "https://dados.fortaleza.ce.gov.br/dataset/1294172b-36f8-4c6d-a356-029e460830e5"
    "/resource/c6bef3ce-6846-431f-9e26-07e01f064413/download/"
    "dadosabertos_estacoesbicicletar.geojson"
)


def fetch(url, insecure=False):
    context = None
    if insecure:
        # O certificado de *.metrofor.ce.gov.br costuma estar expirado.
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
    request = urllib.request.Request(url, headers={"User-Agent": "metrofor-horarios/1.0"})
    with urllib.request.urlopen(request, context=context, timeout=90) as response:
        return response.read()


def gtfs_state():
    archive = zipfile.ZipFile(io.BytesIO(fetch(GTFS_URL, insecure=True)))

    digest = hashlib.sha256()
    for name in sorted(archive.namelist()):
        digest.update(name.encode())
        digest.update(archive.read(name))

    def rows(name):
        text = archive.read(name).decode("utf-8-sig").splitlines()
        header = text[0].split(",")
        return [dict(zip(header, line.split(","))) for line in text[1:] if line.strip()]

    routes = {r["route_id"]: r["route_long_name"] for r in rows("routes.txt")}
    trips = rows("trips.txt")
    times = rows("stop_times.txt")

    trips_per_route = defaultdict(int)
    for trip in trips:
        trips_per_route[trip["route_id"]] += 1

    return {
        "hash": digest.hexdigest(),
        "routes": {routes[k]: v for k, v in trips_per_route.items() if k in routes},
        "stops": len(rows("stops.txt")),
        "trips": len(trips),
        "stop_times": len(times),
    }


def bike_state():
    data = json.loads(fetch(BIKE_URL))
    stations = {}
    for feature in data.get("features", []):
        props = feature.get("properties", {})
        status = (props.get("STATUS") or "").upper()
        if not status.startswith("EXISTENTE") or (props.get("TIPO") or "").upper() != "BICICLETAR":
            continue
        stations[str(props.get("ID"))] = (props.get("NOME") or "").strip()
    digest = hashlib.sha256(
        json.dumps(sorted(stations.items()), ensure_ascii=False).encode()
    ).hexdigest()
    return {"hash": digest, "count": len(stations), "stations": stations}


def describe(old, new):
    """As diferencas em linguagem humana. Lista vazia = nada mudou."""
    changes = []

    old_gtfs, new_gtfs = old.get("gtfs", {}), new["gtfs"]
    if old_gtfs.get("hash") != new_gtfs["hash"]:
        changes.append("Horarios do Metrofor mudaram:")
        for field, label in (("stops", "estacoes"), ("trips", "viagens"), ("stop_times", "partidas")):
            before, after = old_gtfs.get(field), new_gtfs[field]
            if before != after:
                changes.append(f"  - {label}: {before} -> {after}")
        before_routes = old_gtfs.get("routes", {})
        for line, count in new_gtfs["routes"].items():
            if before_routes.get(line) != count:
                changes.append(f"  - {line}: {before_routes.get(line, 0)} -> {count} viagens")
        for line in set(before_routes) - set(new_gtfs["routes"]):
            changes.append(f"  - {line}: saiu do feed")
        if len(changes) == 1:
            changes.append("  - conteudo alterado sem mudanca nas contagens")

    old_bike, new_bike = old.get("bike", {}), new["bike"]
    if old_bike.get("hash") != new_bike["hash"]:
        before = old_bike.get("stations", {})
        added = set(new_bike["stations"]) - set(before)
        removed = set(before) - set(new_bike["stations"])
        changes.append(f"Estacoes Bicicletar mudaram: {old_bike.get('count', 0)} -> {new_bike['count']}")
        for number in sorted(added, key=int)[:10]:
            changes.append(f"  + n {number} {new_bike['stations'][number]}")
        for number in sorted(removed, key=int)[:10]:
            changes.append(f"  - n {number} {before[number]}")
        if not added and not removed:
            changes.append("  - mesmas estacoes, algum nome mudou")
    return changes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--notify", action="store_true", help="avisa na area de trabalho")
    parser.add_argument("--save", action="store_true", help="grava o estado sem comparar")
    args = parser.parse_args()

    try:
        new = {"gtfs": gtfs_state(), "bike": bike_state(), "checked_at": datetime.now().isoformat(timespec="seconds")}
    except Exception as exc:  # noqa: BLE001 - qualquer falha de rede ou formato
        print(f"ERRO ao consultar as fontes: {exc}", file=sys.stderr)
        return 1

    old = {}
    if os.path.exists(STATE) and not args.save:
        with open(STATE, encoding="utf-8") as fh:
            old = json.load(fh)

    changes = describe(old, new) if old else []

    with open(STATE, "w", encoding="utf-8") as fh:
        json.dump(new, fh, ensure_ascii=False, indent=2, sort_keys=True)

    if not old:
        print(f"estado inicial gravado em {STATE}")
        return 0
    if not changes:
        print(f"nada mudou ({new['checked_at']})")
        return 0

    report = "\n".join(changes)
    print(report)
    print("\nPara atualizar o app:  python3 tools/build_db.py && ./gradlew assembleDebug")
    if args.notify:
        subprocess.run(
            ["notify-send", "-u", "normal", "Metro Fortaleza: dados mudaram", report[:400]],
            check=False,
        )
    return 10


if __name__ == "__main__":
    sys.exit(main())
