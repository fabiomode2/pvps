#!/usr/bin/env bash
#
# PVPS bootstrap for Ubuntu/Debian.
#
# Installs OpenCode v2 as a per-user systemd service on 127.0.0.1:49374,
# stores its basic-auth password in /etc/pvps/opencode.env (root:root 0600),
# adds the phone's SSH public key to ~/.ssh/authorized_keys, and prints a
# single-line PVPS1 setup code (pvps-setup: prefix) that the app imports.
#
# Run as your normal user (the script uses sudo where needed), NOT as root.
# Download the installer and its checksums first, verify, then run:
#   base="https://github.com/fabiomode2/pvps/releases/latest/download"
#   curl -fsSLO "${base}/install.sh"
#   curl -fsSLO "${base}/SHA256SUMS"
#   grep -E '  install\.sh$' SHA256SUMS | sha256sum -c -
#   bash install.sh
#
# The only interactive question is the phone SSH public key. It can be pasted
# from the app or given as a path to a .pub file. For unattended installs use:
#   PVPS_SSH_PUBKEY="$(cat phone.pub)" bash install.sh
#
# Optional:
#   PVPS_OPENCODE_INSTALL_SHA256=<sha256> bash install.sh
#   PVPS_HOST=203.0.113.10 bash install.sh
#   PVPS_SSH_PORT=2222 bash install.sh
#   bash install.sh --tailscale
#   bash install.sh --swap
#   bash install.sh --harden-ssh
#   bash install.sh --replace
#
# By default the setup code uses the machine's public IPv4 address, because a
# phone on mobile data cannot reach a Tailscale 100.x address unless it is
# signed into the same tailnet. Use --tailscale (or PVPS_HOST) when the phone
# is on the tailnet and the public SSH port is not exposed.
#
# The setup code contains the OpenCode password. It is printed to the terminal,
# or written to ~/.pvps-setup-code (0600) when no terminal is attached. Treat
# the code as secret.
#
set -euo pipefail

OC_PORT=49374
OC_USER=opencode
SERVICE_NAME=pvps-opencode.service
ENV_DIR=/etc/pvps
ENV_FILE="${ENV_DIR}/opencode.env"
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}"
SSHD_DROPIN=/etc/ssh/sshd_config.d/99-pvps.conf
SSHD_KEEPALIVE=/etc/ssh/sshd_config.d/98-pvps-keepalive.conf
KEY_MARKER=pvps
OPENCODE_INSTALL_URL=https://opencode.ai/v2/install
OPENCODE_INSTALL_SHA256="${PVPS_OPENCODE_INSTALL_SHA256:-}"

REPLACE=0
ENABLE_SWAP=0
HARDEN_SSH=0
PREFER_TAILSCALE="${PVPS_PREFER_TAILSCALE:-0}"
NONINTERACTIVE="${PVPS_NONINTERACTIVE:-0}"
UNIT_CHANGED=0
PASSWORD=""
SSH_PORT=""
SSH_KEY_TEXT=""

usage() {
  cat <<'EOF'
Usage: install.sh [--tailscale] [--swap] [--harden-ssh] [--replace]

  --tailscale   Prefer the Tailscale 100.x address in the setup code instead
                of the public IPv4 address. Only use this when the phone is
                signed into the same tailnet.
  --swap        Add a 4 GB /swapfile if none is active (default: off).
  --harden-ssh  Restrict SSH local forwarding to 127.0.0.1:49374 (default: off).
  --replace     Stop an old opencode.service/background service that is
                holding port 49374. Never kills an unknown process.

Environment:
  PVPS_SSH_PUBKEY         Phone public key text, or a path to a .pub file.
                          Prompted on a terminal when unset.
  PVPS_HOST               Host/IP to put in the setup code (overrides detection).
  PVPS_SSH_PORT           SSH port to put in the setup code (default: detected
                          from sshd, otherwise 22).
  PVPS_PREFER_TAILSCALE   Set to 1 for the same effect as --tailscale.
  PVPS_NONINTERACTIVE     Set to 1 to never prompt (for CI/unattended runs).
  PVPS_OPENCODE_INSTALL_SHA256
                          Expected SHA-256 of the upstream OpenCode v2
                          installer. When set, a mismatch aborts. When unset,
                          the downloaded installer runs after a warning.
EOF
}

say() { printf '\n==> %s\n' "$*"; }
warn() { printf 'WARNING: %s\n' "$*" >&2; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

is_true() {
  case "${1:-}" in
    1|true|TRUE|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

has_tty() {
  # /dev/tty exists even without a controlling terminal; opening it is the
  # reliable check. Do not rely on -r/-w mode bits, which are always set.
  ( exec 3<>/dev/tty ) 2>/dev/null
}

while (( $# > 0 )); do
  case "$1" in
    --replace) REPLACE=1 ;;
    --swap) ENABLE_SWAP=1 ;;
    --harden-ssh) HARDEN_SSH=1 ;;
    --tailscale) PREFER_TAILSCALE=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
  shift
done

if [[ "${EUID}" -eq 0 ]]; then
  die "Run as your normal user, not root. The script uses sudo where needed."
fi

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
  sudo -v || die "sudo authentication failed."
fi

install_base_packages() {
  local pkg status missing=()

  for pkg in curl ca-certificates tmux openssl python3 unzip; do
    status="$(dpkg-query -W -f='${Status}' "${pkg}" 2>/dev/null || true)"
    if [[ "${status}" != *"ok installed"* ]]; then
      missing+=("${pkg}")
    fi
  done

  if (( ${#missing[@]} == 0 )); then
    say "Base packages are already installed"
    return 0
  fi

  say "Installing base packages: ${missing[*]}"
  local APT_OPTS=(-o DPkg::Lock::Timeout=300 -o Acquire::Retries=3)
  sudo apt-get "${APT_OPTS[@]}" update
  sudo env DEBIAN_FRONTEND=noninteractive apt-get "${APT_OPTS[@]}" install -y --no-install-recommends \
    "${missing[@]}"
}

expand_path() {
  local tilde='~'
  case "${1:-}" in
    "${tilde}") printf '%s' "${HOME}" ;;
    "${tilde}/"*) printf '%s/%s' "${HOME}" "${1#"${tilde}/"}" ;;
    *) printf '%s' "$1" ;;
  esac
}

strip_quotes() {
  local value="${1:-}"
  value="${value%\"}"; value="${value#\"}"
  value="${value%\'}"; value="${value#\'}"
  printf '%s' "${value}"
}

trim_value() {
  local value="${1:-}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

resolve_key_input() {
  local value="${1:-}"

  [[ -n "${value}" ]] || return 0
  if [[ -f "${value}" ]]; then
    cat -- "${value}"
    return 0
  fi
  case "${value}" in
    ssh-*|ecdsa-*|sk-*) printf '%s' "${value}" ;;
    *) warn "Not a public key or readable file: ${value}" ;;
  esac
}

read_phone_key_from_tty() {
  local value=""

  exec 3<>/dev/tty || return 0
  printf '\nPhone SSH public key\n' >&3
  printf 'In the app: SSH keys -> + -> copy the public key.\n' >&3
  printf 'Paste the key or a path to a .pub file (Enter to skip): ' >&3
  IFS= read -r value <&3 || value=""
  exec 3>&-
  value="$(trim_value "$(strip_quotes "${value}")")"
  value="$(expand_path "${value}")"
  SSH_KEY_TEXT="$(resolve_key_input "${value}")"
}

collect_phone_key() {
  local value=""

  # An explicitly set (even empty) PVPS_SSH_PUBKEY wins over prompting.
  if [[ ${PVPS_SSH_PUBKEY+x} ]]; then
    value="$(trim_value "$(strip_quotes "${PVPS_SSH_PUBKEY}")")"
    value="$(expand_path "${value}")"
    SSH_KEY_TEXT="$(resolve_key_input "${value}")"
    return 0
  fi

  if is_true "${NONINTERACTIVE}"; then
    SSH_KEY_TEXT=""
    return 0
  fi

  if has_tty; then
    read_phone_key_from_tty
  fi
}

install_ssh_key() {
  local raw key_type key_data rest entry added=0

  if [[ -z "${SSH_KEY_TEXT}" ]]; then
    cat <<'EOF'
No phone SSH key was installed. Add it later with:

  mkdir -p ~/.ssh && chmod 700 ~/.ssh
  echo 'ssh-ed25519 AAAA... pvps' >> ~/.ssh/authorized_keys
  chmod 600 ~/.ssh/authorized_keys

Or re-run:
  PVPS_SSH_PUBKEY="$(cat phone-key.pub)" bash install.sh

If the phone key is already authorized, nothing else is needed.
EOF
    return 0
  fi

  install -d -m 700 "${HOME}/.ssh"
  chmod 700 "${HOME}/.ssh"
  if [[ ! -e "${HOME}/.ssh/authorized_keys" ]]; then
    install -m 600 /dev/null "${HOME}/.ssh/authorized_keys"
  fi
  chmod 600 "${HOME}/.ssh/authorized_keys"

  while IFS= read -r raw || [[ -n "${raw}" ]]; do
    raw="${raw%$'\r'}"
    # Trim surrounding whitespace without touching the key material.
    raw="${raw#"${raw%%[![:space:]]*}"}"
    raw="${raw%"${raw##*[![:space:]]}"}"
    [[ -n "${raw}" ]] || continue
    [[ "${raw}" == \#* ]] && continue

    key_type="${raw%%[[:space:]]*}"
    if [[ "${key_type}" == "${raw}" ]]; then
      warn "Ignoring a malformed SSH key line (missing key data)."
      continue
    fi
    rest="${raw#"${key_type}"}"
    rest="${rest#"${rest%%[![:space:]]*}"}"
    key_data="${rest%%[[:space:]]*}"

    case "${key_type}" in
      ssh-ed25519|ssh-rsa|ecdsa-*|sk-ssh-*|sk-ecdsa-*) ;;
      *) warn "Ignoring unsupported SSH key type: ${key_type}"; continue ;;
    esac
    if [[ -z "${key_data}" || ! "${key_data}" =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || \
       (( ${#key_data} < 32 || ${#key_data} % 4 != 0 )) || \
       ! printf '%s' "${key_data}" | base64 -d >/dev/null 2>&1; then
      warn "Ignoring a malformed ${key_type} public key."
      continue
    fi

    if grep -qF -- "${key_data}" "${HOME}/.ssh/authorized_keys"; then
      say "SSH key is already authorized in ~/.ssh/authorized_keys"
      continue
    fi

    if [[ -s "${HOME}/.ssh/authorized_keys" ]] && \
       [[ "$(tail -c 1 "${HOME}/.ssh/authorized_keys" | od -An -tu1 | tr -d ' \n')" != "10" ]]; then
      printf '\n' >> "${HOME}/.ssh/authorized_keys"
    fi
    entry="${key_type} ${key_data} ${KEY_MARKER}"
    printf '%s\n' "${entry}" >> "${HOME}/.ssh/authorized_keys"
    say "Added phone SSH key to ~/.ssh/authorized_keys (marker: ${KEY_MARKER})"
    added=$((added + 1))
  done <<< "${SSH_KEY_TEXT}"

  chmod 600 "${HOME}/.ssh/authorized_keys"
  if (( added == 0 )); then
    warn "No new SSH key was added (empty, malformed, or already present)."
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
  local ver installer actual expected

  if [[ -x "${OPENCODE_BIN}" ]]; then
    ver="$(opencode_version "${OPENCODE_BIN}")"
    if [[ "${ver}" == 2.* ]]; then
      say "OpenCode v${ver} is already installed at ${OPENCODE_BIN}"
      return 0
    fi
    die "Existing ${OPENCODE_BIN} is not OpenCode v2 (version: ${ver:-unknown}). Move it aside and re-run, or install v2 with the upstream installer after verifying its SHA-256."
  fi

  say "Installing OpenCode v2"
  # https://opencode.ai/install is the v1 installer and refuses 2.x by design.
  # The v2 installer is https://opencode.ai/v2/install. Download it to a file
  # first so it can be digest-checked before execution instead of piping it
  # straight from the network into bash.
  installer="${WORK_DIR}/opencode-install.sh"
  if ! curl -fsSL "${OPENCODE_INSTALL_URL}" -o "${installer}"; then
    rm -f "${installer}"
    cat >&2 <<'EOF'
OpenCode v2 installer download failed. Manual fallback:
  curl -fsSLo /tmp/opencode-install.sh https://opencode.ai/v2/install
  # Inspect the file, then run: bash /tmp/opencode-install.sh --no-modify-path

Or, with Node.js/npm available:
  npm install -g @opencode/cli@2
  mkdir -p "$HOME/.opencode/bin"
  ln -sf "$(npm prefix -g)/bin/opencode" "$HOME/.opencode/bin/opencode"
EOF
    exit 1
  fi

  if [[ -n "${OPENCODE_INSTALL_SHA256}" ]]; then
    if [[ ! "${OPENCODE_INSTALL_SHA256}" =~ ^[0-9A-Fa-f]{64}$ ]]; then
      rm -f "${installer}"
      die "PVPS_OPENCODE_INSTALL_SHA256 must be a 64-character hexadecimal SHA-256."
    fi
    actual="$(sha256sum "${installer}" | awk '{print $1}')"
    expected="$(printf '%s' "${OPENCODE_INSTALL_SHA256}" | tr 'A-F' 'a-f')"
    if [[ "${actual}" != "${expected}" ]]; then
      rm -f "${installer}"
      die "OpenCode installer failed SHA-256 verification; refusing to run it."
    fi
    say "OpenCode installer SHA-256 verified"
  else
    warn "PVPS_OPENCODE_INSTALL_SHA256 is not set; the downloaded OpenCode installer is not integrity-pinned."
    warn "It will run from ${installer} after an HTTPS download from ${OPENCODE_INSTALL_URL}."
    warn "Set PVPS_OPENCODE_INSTALL_SHA256 to a reviewed digest to fail closed on changes."
  fi

  if ! bash "${installer}" --no-modify-path; then
    rm -f "${installer}"
    cat >&2 <<'EOF'
OpenCode v2 install failed. Manual fallback:
  curl -fsSLo /tmp/opencode-install.sh https://opencode.ai/v2/install
  # Compare the file with the digest you trust, then run:
  bash /tmp/opencode-install.sh --no-modify-path

Or, with Node.js/npm available:
  npm install -g @opencode/cli@2
  mkdir -p "$HOME/.opencode/bin"
  ln -sf "$(npm prefix -g)/bin/opencode" "$HOME/.opencode/bin/opencode"
EOF
    exit 1
  fi
  rm -f "${installer}"

  [[ -x "${OPENCODE_BIN}" ]] || die "OpenCode installer did not create ${OPENCODE_BIN}"
  ver="$(opencode_version "${OPENCODE_BIN}")"
  [[ "${ver}" == 2.* ]] || die "Installed OpenCode is not v2 (version: ${ver:-unknown})."
  say "Installed OpenCode v${ver}"
}

write_env_file() {
  local src dst

  src="$(mktemp "${WORK_DIR}/env.XXXXXX")"
  dst="$(mktemp "${WORK_DIR}/env.XXXXXX")"
  chmod 600 "${src}" "${dst}"
  # shellcheck disable=SC2024 # redirect target is a user-owned temporary file
  sudo cat "${ENV_FILE}" > "${src}" 2>/dev/null || true
  PVPS_ENV_PASSWORD="${PASSWORD}" awk '
    /^[[:space:]]*(export[[:space:]]+)?OPENCODE_PASSWORD[[:space:]]*=/ {
      print "OPENCODE_PASSWORD=" ENVIRON["PVPS_ENV_PASSWORD"]; done=1; next
    }
    { print }
    END { if (!done) print "OPENCODE_PASSWORD=" ENVIRON["PVPS_ENV_PASSWORD"] }
  ' "${src}" > "${dst}"
  sudo install -m 600 -o root -g root "${dst}" "${ENV_FILE}"
  rm -f "${src}" "${dst}"
}

write_env() {
  local existing=""

  sudo install -d -m 755 -o root -g root "${ENV_DIR}"

  if sudo test -f "${ENV_FILE}"; then
    existing="$(sudo awk '/^[[:space:]]*(export[[:space:]]+)?OPENCODE_PASSWORD[[:space:]]*=/{sub(/^[^=]*=/,""); print; exit}' "${ENV_FILE}" | tr -d '\r"')"
    existing="$(trim_value "${existing//$'\n'/}")"
  fi

  if [[ -n "${existing}" ]]; then
    PASSWORD="${existing}"
    write_env_file
    say "Reusing the existing OpenCode password in ${ENV_FILE}"
  else
    PASSWORD="$(openssl rand -hex 32)"
    write_env_file
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
Environment=PATH=${HOME}/.opencode/bin:${HOME}/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
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
  sudo chown root:root "${UNIT_FILE}"
  sudo chmod 644 "${UNIT_FILE}"
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

curl_api() {
  local timeout="$1" url="$2" user password
  user="${OC_USER//\\/\\\\}"; user="${user//\"/\\\"}"
  password="${PASSWORD//\\/\\\\}"; password="${password//\"/\\\"}"
  # Keep the password out of curl's argv so other local users cannot see it
  # with ps/proc. The config is supplied on stdin, never written to disk.
  printf 'user = "%s:%s"\n' "${user}" "${password}" |
    curl -fsS --max-time "${timeout}" --config - "${url}"
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
  if systemctl cat "${SERVICE_NAME}" >/dev/null 2>&1; then
    sudo systemctl stop "${SERVICE_NAME}" 2>/dev/null || true
  fi
  if [[ -x "${OPENCODE_BIN}" ]]; then
    "${OPENCODE_BIN}" service stop 2>/dev/null || true
  fi
  if command -v pkill >/dev/null 2>&1; then
    pkill -u "${RUN_USER}" -f 'opencode serve --service' 2>/dev/null || true
    pkill -u "${RUN_USER}" -f "opencode serve --hostname 127.0.0.1 --port ${OC_PORT}" 2>/dev/null || true
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
    if (( REPLACE == 1 )) && systemctl cat opencode.service >/dev/null 2>&1; then
      say "Disabling old opencode.service because --replace was given"
      sudo systemctl disable --now opencode.service 2>/dev/null || true
    fi
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
  # A unit that hit systemd's start limit stays failed even after a fix.
  sudo systemctl reset-failed "${SERVICE_NAME}" 2>/dev/null || true
  sudo systemctl enable "${SERVICE_NAME}" >/dev/null

  if systemctl is-active --quiet "${SERVICE_NAME}" && (( UNIT_CHANGED == 0 )); then
    if curl_api 2 "http://127.0.0.1:${OC_PORT}/api/info" >/dev/null 2>&1; then
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

  # First start on a small VPS can be slow (npm/opencode cold start, swap-less box).
  for _ in {1..45}; do
    if response="$(curl_api 3 "http://127.0.0.1:${OC_PORT}/api/info" 2>/dev/null)"; then
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
    if ! sudo mkswap /swapfile >/dev/null; then
      sudo rm -f /swapfile
      die "Could not format /swapfile."
    fi
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

reload_sshd() {
  if systemctl is-active --quiet ssh.service; then
    sudo systemctl reload ssh.service || warn "Could not reload ssh.service; the drop-in will apply on the next restart."
  elif systemctl is-active --quiet sshd.service; then
    sudo systemctl reload sshd.service || warn "Could not reload sshd.service; the drop-in will apply on the next restart."
  else
    warn "sshd is not active; the drop-in will apply the next time it starts."
  fi
}

# Long-lived mobile tunnels need both ends to speak. The app sends keepalives
# every 15s; this keeps the server's NAT mapping warm and lets sshd notice a
# phone that vanished without a FIN. MaxSessions is raised because one OpenCode
# client uses several direct-tcpip channels (HTTP pool, SSE, SFTP, terminal).
configure_ssh_keepalive() {
  local tmp="${WORK_DIR}/98-pvps-keepalive.conf"

  if [[ ! -d /etc/ssh/sshd_config.d ]]; then
    warn "/etc/ssh/sshd_config.d not found; skipping SSH keepalive tuning."
    return 0
  fi
  cat > "${tmp}" <<EOF
ClientAliveInterval 15
ClientAliveCountMax 4
TCPKeepAlive yes
MaxSessions 64
EOF
  if sudo test -f "${SSHD_KEEPALIVE}" && sudo cmp -s "${tmp}" "${SSHD_KEEPALIVE}"; then
    say "SSH keepalive settings already present"
    return 0
  fi
  sudo install -m 644 -o root -g root "${tmp}" "${SSHD_KEEPALIVE}"
  if ! sudo sshd -t; then
    sudo rm -f "${SSHD_KEEPALIVE}"
    warn "sshd -t failed after adding keepalive settings; removed ${SSHD_KEEPALIVE}."
    return 0
  fi
  say "SSH keepalives enabled (ClientAliveInterval 15, MaxSessions 64)"
  reload_sshd
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
  printf '%s\n' "${effective}" | grep -E '^(allowtcpforwarding|permitopen|allowagentforwarding|x11forwarding|maxauthtries|logingracetime|clientaliveinterval|clientalivecountmax|tcpkeepalive|maxsessions)[[:space:]]' || true

  if ! grep -qE "^permitopen[[:space:]]+127\\.0\\.0\\.1:${OC_PORT}([[:space:],]|$)" <<< "${effective}"; then
    warn "The drop-in does not appear to be active; check that sshd_config includes /etc/ssh/sshd_config.d/*.conf."
  fi
  if ! grep -qF "allowtcpforwarding local" <<< "${effective}"; then
    warn "AllowTcpForwarding is not 'local' in the effective sshd config; another file may override the drop-in."
  fi
  if ! grep -qE "^clientaliveinterval[[:space:]]+15$" <<< "${effective}"; then
    warn "ClientAliveInterval is not 15s in the effective sshd config; the tunnel may still idle out on mobile networks."
  fi

  reload_sshd

  say "SSH forwarding is now restricted. Keep this session open while you test a new one."
}

is_ipv4() {
  local ip="${1:-}" octet
  local -a octets

  [[ "${ip}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || return 1
  IFS=. read -r -a octets <<< "${ip}"
  for octet in "${octets[@]}"; do
    (( 10#${octet} <= 255 )) || return 1
  done
}

detect_public_ipv4() {
  local endpoint ip
  for endpoint in \
    "https://api.ipify.org" \
    "https://ifconfig.me/ip" \
    "https://icanhazip.com"; do
    ip="$(curl -4 -fsS --max-time 8 --retry 2 --retry-delay 1 "${endpoint}" 2>/dev/null | tr -d '[:space:]' || true)"
    if is_ipv4 "${ip}"; then
      printf '%s' "${ip}"
      return 0
    fi
  done
  return 1
}

detect_tailscale_ipv4() {
  local ip
  command -v tailscale >/dev/null 2>&1 || return 1

  ip="$(timeout 5 tailscale ip -4 2>/dev/null | head -n 1 | tr -d '[:space:]' || true)"
  if ! is_ipv4 "${ip}"; then
    ip="$(timeout 5 sudo -n tailscale ip -4 2>/dev/null | head -n 1 | tr -d '[:space:]' || true)"
  fi
  is_ipv4 "${ip}" || return 1
  printf '%s' "${ip}"
}

detect_lan_ipv4() {
  local ip
  # Prefer the address on the default-route interface; hostname -I can list
  # Docker bridges and other virtual interfaces first.
  ip="$(ip route get 1.1.1.1 2>/dev/null |
    awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }' || true)"
  if is_ipv4 "${ip}"; then
    printf '%s' "${ip}"
    return 0
  fi

  while IFS= read -r ip; do
    [[ "${ip}" =~ ^127\. ]] && continue
    [[ "${ip}" =~ ^169\.254\. ]] && continue
    [[ "${ip}" =~ ^172\.(1[7-9]|2[0-9]|3[01])\. ]] && continue
    if is_ipv4 "${ip}"; then
      printf '%s' "${ip}"
      return 0
    fi
  done < <(hostname -I 2>/dev/null | tr ' ' '\n')
  return 1
}

detect_host() {
  local host="" public="" tailscale="" lan=""

  if [[ -n "${PVPS_HOST:-}" ]]; then
    [[ "${PVPS_HOST}" != *[[:space:]]* ]] || die "PVPS_HOST must not contain whitespace."
    printf '%s' "${PVPS_HOST}"
    return 0
  fi

  public="$(detect_public_ipv4 || true)"
  tailscale="$(detect_tailscale_ipv4 || true)"
  lan="$(detect_lan_ipv4 || true)"

  if is_true "${PREFER_TAILSCALE}"; then
    host="${tailscale:-${public}}"
  else
    host="${public:-${tailscale}}"
  fi
  [[ -n "${host}" ]] || host="${lan}"

  if [[ -z "${host}" ]]; then
    warn "Could not detect an IP address; falling back to the hostname."
    host="$(hostname 2>/dev/null || true)"
  fi
  [[ -n "${host}" ]] || host="unknown"

  if [[ -n "${public}" && "${host}" != "${public}" ]]; then
    warn "Public IPv4 ${public} was detected but not used. The phone must be able to reach ${host}."
  fi
  if [[ -n "${tailscale}" && "${host}" == "${tailscale}" ]]; then
    warn "Using Tailscale address ${host}; the phone must be signed into the same tailnet."
  elif [[ -n "${tailscale}" ]]; then
    warn "Tailscale address ${tailscale} was detected but not used; phones outside the tailnet cannot reach it."
    warn "If the phone is on the same tailnet, rerun with --tailscale or PVPS_HOST=${tailscale}."
  fi
  if [[ -n "${lan}" && "${host}" == "${lan}" && "${host}" != "${public}" && "${host}" != "${tailscale}" ]]; then
    warn "Using LAN address ${host}; the phone must be on the same network."
  fi

  printf '%s' "${host}"
}

detect_ssh_port() {
  local port="${PVPS_SSH_PORT:-}"

  if [[ -n "${port}" ]]; then
    if [[ ! "${port}" =~ ^[0-9]+$ ]] || (( 10#${port} < 1 || 10#${port} > 65535 )); then
      die "PVPS_SSH_PORT must be a TCP port between 1 and 65535."
    fi
    printf '%s' "${port}"
    return 0
  fi

  port="$(sudo -n sshd -T 2>/dev/null | awk 'tolower($1) == "port" { print $2; exit }' || true)"
  if [[ "${port}" =~ ^[0-9]+$ ]] && (( 10#${port} >= 1 && 10#${port} <= 65535 )); then
    printf '%s' "${port}"
    return 0
  fi
  warn "Could not detect the sshd port; assuming 22. Set PVPS_SSH_PORT if SSH uses another port."
  printf '22'
}

make_setup_code() {
  local host="$1"

  # The wire payload is the PVPS1 format. The Android app currently requires
  # the "pvps-setup:" prefix (see SetupCode.kt), so keep that prefix for
  # compatibility; the decoded JSON is identical.
  #
  # The app requires ocPassword to import a host. The password is passed on
  # stdin, never as an argv or environment value, and the finished code is not
  # written to disk by this script.
  printf '%s' "${PASSWORD}" | python3 -c '
import base64
import json
import sys

host, port, user, oc_port, oc_user, directory = sys.argv[1:7]
password = sys.stdin.read()
payload = {
    "v": 1,
    "format": "PVPS1",
    "name": "vps",
    "host": host,
    "port": int(port),
    "user": user,
    "ocPort": int(oc_port),
    "ocUser": oc_user,
    "ocPassword": password,
    "mode": "tunnel",
    "directory": directory,
    "auth": "ssh-key",
    "secretRef": "/etc/pvps/opencode.env",
}
raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
print("pvps-setup:" + base64.urlsafe_b64encode(raw).decode("ascii").rstrip("="))
' "${host}" "${SSH_PORT}" "${RUN_USER}" "${OC_PORT}" "${OC_USER}" "${HOME}"
}

print_setup_code() {
  local host="$1" out="/dev/stdout" file tmp

  if [[ ! -t 1 ]]; then
    if has_tty; then
      out="/dev/tty"
    else
      # Never put the secret in CI logs or redirected output. A 0600 file is
      # readable only by the installing user and can be removed after import.
      file="${HOME}/.pvps-setup-code"
      if ! tmp="$(mktemp "${HOME}/.pvps-setup-code.XXXXXX")"; then
        die "Could not create a temporary file for the setup code in ${HOME}."
      fi
      if ! printf '%s\n' "${SETUP_CODE}" > "${tmp}"; then
        rm -f "${tmp}"
        die "Could not write the setup code to ${file}."
      fi
      chmod 600 "${tmp}"
      if ! mv -f "${tmp}" "${file}"; then
        rm -f "${tmp}"
        die "Could not replace ${file}."
      fi
      printf '\nSSH target: %s@%s:%s    OpenCode: 127.0.0.1:%s (SSH tunnel)\n' \
        "${RUN_USER}" "${host}" "${SSH_PORT}" "${OC_PORT}"
      printf 'PVPS setup code written to %s (mode 0600).\n' "${file}"
      printf 'Import it in the app, then delete the file:\n'
      printf '  rm -f %s\n' "${file}"
      return 0
    fi
  fi

  {
    printf '\n'
    printf '================ PVPS SETUP CODE ================\n'
    printf 'SSH: %s@%s:%s    OpenCode: 127.0.0.1:%s (SSH tunnel)\n' \
      "${RUN_USER}" "${host}" "${SSH_PORT}" "${OC_PORT}"
    printf '\n'
    printf '%s\n' "${SETUP_CODE}"
    printf '=================================================\n'
    cat <<'EOF'
Paste the whole pvps-setup: line into the app:
  Add host -> Paste setup code.

It contains the OpenCode password. Treat it like a password and do not paste
it into chats, tickets or logs. The code is printed once and is not written to
disk by this script. Re-run the installer to print it again; the existing
password is reused.
EOF
  } > "${out}"
}

install_base_packages
SSH_PORT="$(detect_ssh_port)"
collect_phone_key
install_ssh_key
configure_ssh_keepalive
install_opencode
write_env
write_unit
resolve_port

if (( ENABLE_SWAP == 1 )); then
  enable_swap
fi

start_service

say "Service status"
sudo systemctl --no-pager --full --lines=15 status "${SERVICE_NAME}" || true

say "OpenCode API check"
if ! verify_service; then
  die "${SERVICE_NAME} did not answer on 127.0.0.1:${OC_PORT}. Check the logs above."
fi

if (( HARDEN_SSH == 1 )); then
  harden_ssh
fi

SSH_HOST="$(detect_host)"
say "Setup code target: ${RUN_USER}@${SSH_HOST}:${SSH_PORT}"
SETUP_CODE="$(make_setup_code "${SSH_HOST}")" || die "Could not build the setup code."
print_setup_code "${SSH_HOST}"
