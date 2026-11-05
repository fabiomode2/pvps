#!/usr/bin/env bash
#
# PVPS VPS bootstrap for Ubuntu/Debian.
#
# Installs OpenCode v2 as a per-user systemd service on 127.0.0.1:49374,
# stores its basic-auth password in /etc/pvps/opencode.env, and prints a
# pvps-setup: code that can be pasted into the Android app.
#
# Run as your normal user (the script uses sudo where needed), NOT as root:
#   curl -fsSL https://github.com/fabiomode2/pvps/releases/latest/download/install.sh | bash
#
# Optional:
#   PVPS_SSH_PUBKEY="$(cat phone.pub)" bash install.sh
#   bash install.sh --swap
#   bash install.sh --harden-ssh
#   bash install.sh --replace
#
set -euo pipefail

OC_PORT=49374
OC_USER=opencode
SERVICE_NAME=pvps-opencode.service
ENV_DIR=/etc/pvps
ENV_FILE="${ENV_DIR}/opencode.env"
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}"
SSHD_DROPIN=/etc/ssh/sshd_config.d/99-pvps.conf

REPLACE=0
ENABLE_SWAP=0
HARDEN_SSH=0
UNIT_CHANGED=0
PASSWORD=""

usage() {
  cat <<'EOF'
Usage: install.sh [--swap] [--harden-ssh] [--replace]

  --swap        Add a 4 GB /swapfile if none is active (default: off).
  --harden-ssh  Restrict SSH local forwarding to 127.0.0.1:49374 (default: off).
  --replace     Stop an old opencode.service/background service that is
                holding port 49374. Never kills an unknown process.

Environment:
  PVPS_SSH_PUBKEY   Public key to append to ~/.ssh/authorized_keys (marker: pvps).
EOF
}

say() { printf '\n==> %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

if [[ "${EUID}" -eq 0 ]]; then
  die "Run as your normal user, not root. The script uses sudo where needed."
fi

while (( $# > 0 )); do
  case "$1" in
    --replace) REPLACE=1 ;;
    --swap) ENABLE_SWAP=1 ;;
    --harden-ssh) HARDEN_SSH=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
  shift
done

RUN_USER="$(id -un)"
HOME="${HOME%/}"
[[ -n "${HOME}" && "${HOME}" != "/" ]] || die "HOME is not set correctly."
[[ "${HOME}" != *[[:space:]]* ]] || die "HOME contains whitespace; the systemd unit would be ambiguous."
OPENCODE_BIN="${HOME}/.opencode/bin/opencode"

command -v sudo >/dev/null 2>&1 || die "sudo is required."
command -v apt-get >/dev/null 2>&1 || die "This script supports Ubuntu/Debian (apt-get) only."
command -v systemctl >/dev/null 2>&1 || die "systemd is required."
[[ -d /run/systemd/system ]] || die "systemd is not running (this script needs a systemd host)."

umask 077
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

if ! sudo -n true 2>/dev/null; then
  say "sudo is required; you may be asked for your password"
  sudo -v
fi

say "Installing base packages"
sudo apt-get update
sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  curl ca-certificates tmux openssl python3 unzip

install_ssh_key() {
  local raw key_type key_data _rest entry added=0

  if [[ -z "${PVPS_SSH_PUBKEY:-}" ]]; then
    cat <<'EOF'
No PVPS_SSH_PUBKEY was provided. Add the phone key manually:

  mkdir -p ~/.ssh && chmod 700 ~/.ssh
  echo 'ssh-ed25519 AAAA... pvps' >> ~/.ssh/authorized_keys
  chmod 600 ~/.ssh/authorized_keys

Or re-run with:
  PVPS_SSH_PUBKEY="$(cat phone-key.pub)" bash install.sh

If a phone key is already authorized, nothing else is needed.
EOF
    return 0
  fi

  install -d -m 700 "${HOME}/.ssh"
  touch "${HOME}/.ssh/authorized_keys"
  chmod 600 "${HOME}/.ssh/authorized_keys"

  while IFS= read -r raw || [[ -n "${raw}" ]]; do
    raw="${raw%$'\r'}"
    [[ -n "${raw}" ]] || continue
    if [[ "${raw}" == \#* ]]; then
      continue
    fi

    read -r key_type key_data _rest <<< "${raw}" || true
    if [[ -z "${key_data:-}" ]]; then
      warn "Ignoring a malformed line in PVPS_SSH_PUBKEY."
      continue
    fi
    case "${key_type}" in
      ssh-*|ecdsa-*|sk-ssh-*|sk-ecdsa-*) ;;
      *) warn "Ignoring unsupported SSH key type: ${key_type}"; continue ;;
    esac

    if grep -qF -- "${key_data}" "${HOME}/.ssh/authorized_keys"; then
      say "SSH key already present in ~/.ssh/authorized_keys"
      continue
    fi

    if [[ -s "${HOME}/.ssh/authorized_keys" ]] && \
       [[ "$(tail -c 1 "${HOME}/.ssh/authorized_keys" | od -An -tu1 | tr -d ' \n')" != "10" ]]; then
      printf '\n' >> "${HOME}/.ssh/authorized_keys"
    fi
    entry="${key_type} ${key_data} pvps"
    printf '%s\n' "${entry}" >> "${HOME}/.ssh/authorized_keys"
    say "Added SSH key to ~/.ssh/authorized_keys (marker: pvps)"
    added=$((added + 1))
  done <<< "${PVPS_SSH_PUBKEY}"

  if (( added == 0 )); then
    warn "No new key was added from PVPS_SSH_PUBKEY (empty, malformed, or already present)."
  fi
}

opencode_version() {
  local bin="$1" raw
  raw="$(timeout 10 "${bin}" --version 2>/dev/null | tail -n 1 || true)"
  if [[ "${raw}" =~ ([0-9]+\.[0-9]+\.[0-9]+[^[:space:]]*) ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi
  raw="${raw##* }"
  raw="${raw#v}"
  printf '%s' "${raw}"
}

install_opencode() {
  local ver

  if [[ -x "${OPENCODE_BIN}" ]]; then
    ver="$(opencode_version "${OPENCODE_BIN}")"
    if [[ "${ver}" == 2.* ]]; then
      say "OpenCode v${ver} is already installed at ${OPENCODE_BIN}"
      return 0
    fi
    die "Existing ${OPENCODE_BIN} is not OpenCode v2 (version: ${ver:-unknown}). Move it aside and re-run, or install v2 with: curl -fsSL https://opencode.ai/v2/install | bash"
  fi

  say "Installing OpenCode v2"
  # https://opencode.ai/install is the v1 installer and refuses 2.x by design.
  # The v2 installer is https://opencode.ai/v2/install (documented by the v1
  # installer itself when it is asked for a 2.x version).
  if ! curl -fsSL https://opencode.ai/v2/install | bash -s -- --no-modify-path; then
    cat >&2 <<'EOF'
OpenCode v2 install failed. Manual fallback:
  curl -fsSL https://opencode.ai/v2/install | bash

Or, with Node.js/npm available:
  npm install -g @opencode/cli@2
  mkdir -p "$HOME/.opencode/bin"
  ln -sf "$(npm prefix -g)/bin/opencode" "$HOME/.opencode/bin/opencode"
EOF
    exit 1
  fi

  [[ -x "${OPENCODE_BIN}" ]] || die "OpenCode installer did not create ${OPENCODE_BIN}"
  ver="$(opencode_version "${OPENCODE_BIN}")"
  [[ "${ver}" == 2.* ]] || die "Installed OpenCode is not v2 (version: ${ver:-unknown})."
  say "Installed OpenCode v${ver}"
}

write_env() {
  local existing=""

  sudo install -d -m 755 -o root -g root "${ENV_DIR}"

  if sudo test -f "${ENV_FILE}"; then
    existing="$(sudo awk '/^OPENCODE_PASSWORD=/{sub(/^[^=]*=/,""); print; exit}' "${ENV_FILE}" | tr -d '\r"')"
  fi

  if [[ -n "${existing}" ]]; then
    PASSWORD="${existing}"
    sudo sed -i "s|^OPENCODE_PASSWORD=.*|OPENCODE_PASSWORD=${PASSWORD}|" "${ENV_FILE}"
    say "Reusing the existing OpenCode password in ${ENV_FILE}"
  else
    PASSWORD="$(openssl rand -hex 32)"
    if ! sudo test -f "${ENV_FILE}"; then
      sudo install -m 600 -o root -g root /dev/null "${ENV_FILE}"
    fi
    if sudo grep -q '^OPENCODE_PASSWORD=' "${ENV_FILE}"; then
      sudo sed -i "s|^OPENCODE_PASSWORD=.*|OPENCODE_PASSWORD=${PASSWORD}|" "${ENV_FILE}"
    else
      printf 'OPENCODE_PASSWORD=%s\n' "${PASSWORD}" | sudo tee -a "${ENV_FILE}" >/dev/null
    fi
    say "Generated a new OpenCode password in ${ENV_FILE}"
  fi

  [[ -n "${PASSWORD}" ]] || die "Could not read or generate OPENCODE_PASSWORD."
  sudo chown root:root "${ENV_FILE}"
  sudo chmod 600 "${ENV_FILE}"
}

write_unit() {
  cat > "${WORK_DIR}/${SERVICE_NAME}" <<EOF
[Unit]
Description=PVPS OpenCode v2 API
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${RUN_USER}
WorkingDirectory=${HOME}
Environment=HOME=${HOME}
EnvironmentFile=${ENV_FILE}
ExecStart=${OPENCODE_BIN} serve --hostname 127.0.0.1 --port ${OC_PORT}
Restart=always
RestartSec=3
UMask=0077

[Install]
WantedBy=multi-user.target
EOF

  UNIT_CHANGED=0
  if ! sudo test -f "${UNIT_FILE}" || ! sudo cmp -s "${WORK_DIR}/${SERVICE_NAME}" "${UNIT_FILE}"; then
    sudo install -m 644 -o root -g root "${WORK_DIR}/${SERVICE_NAME}" "${UNIT_FILE}"
    UNIT_CHANGED=1
    say "Wrote ${UNIT_FILE}"
  else
    say "${UNIT_FILE} is already up to date"
  fi
}

port_in_use() {
  python3 - "${OC_PORT}" <<'PY'
import socket
import sys

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.settimeout(1)
try:
    sock.connect(("127.0.0.1", int(sys.argv[1])))
except OSError:
    sys.exit(1)
finally:
    sock.close()
sys.exit(0)
PY
}

stop_old_opencode() {
  say "Stopping old OpenCode service(s) because --replace was given"
  warn "This may terminate an active OpenCode chat hosted by that service."

  if systemctl cat opencode.service >/dev/null 2>&1; then
    sudo systemctl stop opencode.service 2>/dev/null || true
    sudo systemctl disable opencode.service 2>/dev/null || true
  fi
  if systemctl --user cat opencode.service >/dev/null 2>&1; then
    systemctl --user stop opencode.service 2>/dev/null || true
    systemctl --user disable opencode.service 2>/dev/null || true
  fi
  if [[ -x "${OPENCODE_BIN}" ]]; then
    "${OPENCODE_BIN}" service stop 2>/dev/null || true
  fi
  if command -v pkill >/dev/null 2>&1; then
    pkill -f 'opencode serve --service' 2>/dev/null || true
  fi

  for _ in {1..10}; do
    if ! port_in_use; then
      return 0
    fi
    sleep 1
  done
  return 1
}

resolve_port() {
  if ! port_in_use; then
    if systemctl cat opencode.service >/dev/null 2>&1 && \
       systemctl is-enabled --quiet opencode.service 2>/dev/null; then
      warn "An existing opencode.service is enabled. If it also uses port ${OC_PORT}, disable it (sudo systemctl disable --now opencode.service) to avoid a reboot conflict."
    fi
    return 0
  fi

  if systemctl is-active --quiet "${SERVICE_NAME}"; then
    say "Port ${OC_PORT} is already served by ${SERVICE_NAME}; leaving it running"
    return 0
  fi

  if (( REPLACE == 1 )); then
    if ! stop_old_opencode; then
      die "Port ${OC_PORT} is still in use. Refusing to kill an unknown process. Inspect it with: ss -ltnp 'sport = :${OC_PORT}'"
    fi
    return 0
  fi

  cat >&2 <<EOF
WARNING: something is already listening on 127.0.0.1:${OC_PORT}.
Not touching it and not starting ${SERVICE_NAME}.

Re-run with --replace to stop an old opencode.service/background service,
or stop the existing listener yourself, then run:
  sudo systemctl enable --now ${SERVICE_NAME}

Find the listener with: sudo ss -ltnp 'sport = :${OC_PORT}'
EOF
  exit 1
}

start_service() {
  sudo systemctl daemon-reload
  sudo systemctl enable "${SERVICE_NAME}" >/dev/null

  if systemctl is-active --quiet "${SERVICE_NAME}" && (( UNIT_CHANGED == 0 )); then
    if curl -fsS --max-time 2 -u "${OC_USER}:${PASSWORD}" \
      "http://127.0.0.1:${OC_PORT}/api/info" >/dev/null 2>&1; then
      say "${SERVICE_NAME} is already running and healthy"
      return 0
    fi
  fi

  say "Starting ${SERVICE_NAME}"
  if ! sudo systemctl restart "${SERVICE_NAME}"; then
    sudo systemctl --no-pager --full --lines=15 status "${SERVICE_NAME}" || true
    sudo journalctl --no-pager -u "${SERVICE_NAME}" -n 40 || true
    die "Failed to start ${SERVICE_NAME}."
  fi
}

verify_service() {
  local response

  for _ in {1..15}; do
    if response="$(curl -fsS --max-time 3 -u "${OC_USER}:${PASSWORD}" \
      "http://127.0.0.1:${OC_PORT}/api/info" 2>/dev/null)"; then
      printf '%s\n' "${response}"
      return 0
    fi
    sleep 1
  done

  sudo systemctl --no-pager --full --lines=15 status "${SERVICE_NAME}" || true
  sudo journalctl --no-pager -u "${SERVICE_NAME}" -n 40 || true
  return 1
}

enable_swap() {
  local swaps avail_kb

  say "Configuring a 4 GB /swapfile"
  swaps="$(swapon --show=NAME --noheadings 2>/dev/null || true)"
  if grep -qx '/swapfile' <<< "${swaps}"; then
    say "/swapfile is already active"
  elif [[ -e /swapfile ]]; then
    warn "/swapfile exists but is not active; leaving it untouched."
    warn "Inspect it, then run: sudo swapon /swapfile"
    return 0
  else
    avail_kb="$(df -Pk / | awk 'NR==2 {print $4}')"
    if [[ -n "${avail_kb}" ]] && (( avail_kb < 5 * 1024 * 1024 )); then
      warn "Less than 5 GiB free on /; skipping swap creation."
      return 0
    fi

    if ! sudo fallocate -l 4G /swapfile 2>/dev/null; then
      if ! sudo dd if=/dev/zero of=/swapfile bs=1M count=4096 status=progress; then
        sudo rm -f /swapfile
        die "Could not create /swapfile."
      fi
    fi
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile >/dev/null
    if ! sudo swapon /swapfile; then
      warn "Could not activate /swapfile (the filesystem may not support swap files); removing it."
      sudo rm -f /swapfile
      return 0
    fi
    say "Enabled 4 GB /swapfile"
  fi

  if ! grep -qE '^[[:space:]]*/swapfile[[:space:]]' /etc/fstab; then
    printf '/swapfile none swap sw 0 0\n' | sudo tee -a /etc/fstab >/dev/null
    say "Added /swapfile to /etc/fstab"
  fi
  swapon --show || true
}

harden_ssh() {
  local tmp="${WORK_DIR}/99-pvps.conf" effective

  say "Hardening SSH (local forwarding to 127.0.0.1:${OC_PORT} only)"
  if [[ ! -d /etc/ssh/sshd_config.d ]]; then
    warn "/etc/ssh/sshd_config.d not found; skipping SSH hardening."
    return 0
  fi

  cat > "${tmp}" <<EOF
AllowTcpForwarding local
PermitOpen 127.0.0.1:${OC_PORT}
AllowAgentForwarding no
X11Forwarding no
MaxAuthTries 3
LoginGraceTime 20
EOF

  sudo install -m 644 -o root -g root "${tmp}" "${SSHD_DROPIN}"
  if ! sudo sshd -t; then
    sudo rm -f "${SSHD_DROPIN}"
    die "sshd -t failed; removed ${SSHD_DROPIN}. The existing SSH config was not reloaded."
  fi
  say "sshd -t passed"

  effective="$(sudo sshd -T 2>/dev/null || true)"
  printf '%s\n' "${effective}" | grep -E '^(allowtcpforwarding|permitopen|allowagentforwarding|x11forwarding|maxauthtries|logingracetime)[[:space:]]' || true

  if ! grep -qE "^permitopen[[:space:]]+127\\.0\\.0\\.1:${OC_PORT}([[:space:],]|$)" <<< "${effective}"; then
    warn "The drop-in does not appear to be active; check that sshd_config includes /etc/ssh/sshd_config.d/*.conf."
  fi
  if ! grep -qF "allowtcpforwarding local" <<< "${effective}"; then
    warn "AllowTcpForwarding is not 'local' in the effective sshd config; another file may override the drop-in."
  fi

  if systemctl is-active --quiet ssh.service; then
    sudo systemctl reload ssh.service || warn "Could not reload ssh.service; the drop-in will apply on the next restart."
  elif systemctl is-active --quiet sshd.service; then
    sudo systemctl reload sshd.service || warn "Could not reload sshd.service; the drop-in will apply on the next restart."
  else
    warn "sshd is not active; the drop-in will apply the next time it starts."
  fi

  say "SSH forwarding is now restricted. Keep this session open while you test a new one."
}

detect_host() {
  local host=""

  if command -v tailscale >/dev/null 2>&1; then
    host="$(tailscale ip -4 2>/dev/null | head -n 1 || true)"
    if [[ -z "${host}" ]]; then
      host="$(sudo -n tailscale ip -4 2>/dev/null | head -n 1 || true)"
    fi
  fi
  if [[ -z "${host}" ]]; then
    host="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  if [[ -z "${host}" ]]; then
    host="$(curl -fsS --max-time 8 https://ifconfig.me 2>/dev/null || true)"
  fi
  printf '%s' "${host}"
}

make_setup_code() {
  local host
  host="$(detect_host)"
  if [[ -z "${host}" ]]; then
    warn "Could not detect an IP address; falling back to the hostname."
    host="$(hostname 2>/dev/null || true)"
    [[ -n "${host}" ]] || host="unknown"
  fi

  python3 - "${host}" "${RUN_USER}" "${PASSWORD}" "${HOME}" "${OC_PORT}" "${OC_USER}" <<'PY'
import base64
import json
import sys

host, user, password, directory, oc_port, oc_user = sys.argv[1:7]
payload = {
    "v": 1,
    "name": "vps",
    "host": host,
    "port": 22,
    "user": user,
    "ocPort": int(oc_port),
    "ocUser": oc_user,
    "ocPassword": password,
    "mode": "tunnel",
    "directory": directory,
}
raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
print("pvps-setup:" + base64.urlsafe_b64encode(raw).decode("ascii").rstrip("="))
PY
}

install_ssh_key
install_opencode
write_env
write_unit
resolve_port
start_service

say "Service status"
sudo systemctl --no-pager --full --lines=15 status "${SERVICE_NAME}" || true

say "OpenCode API check"
if ! verify_service; then
  die "${SERVICE_NAME} did not answer on 127.0.0.1:${OC_PORT}. Check the logs above."
fi

if (( ENABLE_SWAP == 1 )); then
  enable_swap
fi

if (( HARDEN_SSH == 1 )); then
  harden_ssh
fi

SETUP_CODE="$(make_setup_code)"

printf '\n'
printf '================ PVPS SETUP CODE ================\n'
printf '%s\n' "${SETUP_CODE}"
printf '=================================================\n'
printf 'Paste this into the Android app. It contains the OpenCode password; treat it like a secret.\n'
