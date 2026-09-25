#!/usr/bin/env bash
#
# Agenda a verificacao das fontes de dados uma vez por semana.
#
#   bash tools/install-weekly-check.sh            # instala e ativa
#   bash tools/install-weekly-check.sh --remove   # desfaz
#
# Usa um timer de usuario do systemd: nao precisa de root e some junto com a
# sua sessao. Com Persistent=true, se a maquina estiver desligada na hora
# marcada, a verificacao roda assim que voce ligar.

set -euo pipefail

PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT_DIR="$HOME/.config/systemd/user"
NAME=metrofor-check

if [ "${1:-}" = "--remove" ]; then
    systemctl --user disable --now "$NAME.timer" 2>/dev/null || true
    rm -f "$UNIT_DIR/$NAME.service" "$UNIT_DIR/$NAME.timer"
    systemctl --user daemon-reload
    echo "verificacao semanal removida"
    exit 0
fi

command -v python3 >/dev/null || { echo "python3 nao encontrado"; exit 1; }
mkdir -p "$UNIT_DIR"

cat > "$UNIT_DIR/$NAME.service" <<UNIT
[Unit]
Description=Verifica se os dados do Metrofor e do Bicicletar mudaram

[Service]
Type=oneshot
WorkingDirectory=$PROJECT
ExecStart=/usr/bin/python3 $PROJECT/tools/check_updates.py --notify
# 10 = houve mudanca. E um resultado esperado, nao uma falha da unidade.
SuccessExitStatus=0 10
UNIT

cat > "$UNIT_DIR/$NAME.timer" <<UNIT
[Unit]
Description=Verificacao semanal dos dados do Metro Fortaleza

[Timer]
OnCalendar=Mon *-*-* 10:00:00
# Recupera a execucao perdida se a maquina estava desligada na hora.
Persistent=true
RandomizedDelaySec=30m

[Install]
WantedBy=timers.target
UNIT

systemctl --user daemon-reload
systemctl --user enable --now "$NAME.timer"

echo "verificacao semanal ativada"
echo
systemctl --user list-timers "$NAME.timer" --no-pager
echo
echo "Testar agora:       systemctl --user start $NAME.service"
echo "Ver o resultado:    journalctl --user -u $NAME.service -n 30 --no-pager"
echo "Remover:            bash tools/install-weekly-check.sh --remove"
