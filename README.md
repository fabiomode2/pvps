# PVPS

Portable VPS control for Android.

PVPS is a native SSH control plane for a VPS running [OpenCode](https://opencode.ai) v2. It reaches OpenCode through an SSH tunnel, so the API stays on `127.0.0.1` and no cloud backend is involved.

**Download:** [latest APK](https://github.com/fabiomode2/pvps/releases/latest)

## Requirements

- Android 9+ (arm64-v8a APK).
- Ubuntu/Debian VPS with `systemd`, `sudo`, and outbound HTTPS.
- SSH access to the VPS (key authentication recommended).
- A phone SSH key. In the app: **SSH keys → +**, then copy the public key.

The installer adds OpenCode v2 for your user. It does not expose OpenCode to the network.

## How it works

The app connects to the VPS over SSH. Shell, terminal and SFTP use SSH; OpenCode requests are forwarded through a local tunnel to `127.0.0.1:49374`. The API is never bound to a public interface, and no cloud relay is involved.

- OpenCode v2 sessions, streaming, tools, permissions and attachments.
- Automatic tunnel recovery: SSH/TCP keepalives, a foreground wake lock, instant redial after Wi-Fi/LTE handoffs, and the OpenCode client rebinds to the rebuilt tunnel without losing the transcript.
- Native terminal, SFTP file browser, Docker/systemd views and a process inspector.
- SSH keys stay on the phone; the app pins the host key on first connect.
- Auto-updates from GitHub Releases.

## Install

1. In the app, create an SSH key and copy its **public** key.
2. On the VPS, run this as your normal user (not root):

   ```bash
   base="https://github.com/fabiomode2/pvps/releases/latest/download"
   curl -fsSLO "${base}/install.sh"
   curl -fsSLO "${base}/SHA256SUMS"
   grep -E '  install\.sh$' SHA256SUMS | sha256sum -c -
   bash install.sh
   ```

   `SHA256SUMS` covers both `install.sh` and the release APK. If the check fails, do not run the installer.

3. When prompted, paste the phone public key. You can also paste a path to a `.pub` file, or press Enter to skip.
4. The installer prints one `pvps-setup:` line (a PVPS1 JSON payload, base64url encoded), or writes it to `~/.pvps-setup-code` if no terminal is attached. Copy the whole line.
5. In the app: **Hosts → + → Paste setup code → Add host**.

The setup code carries the host, SSH port/user, OpenCode port/user/password and working directory. It is printed once and is **not written to disk** by the installer when a terminal is attached. Treat it like a password. The app currently requires the `pvps-setup:` prefix; the decoded JSON is the PVPS1 format.

If no terminal is available (CI or unattended), the installer writes the code to `~/.pvps-setup-code` with mode `0600` and prints the path instead of putting it in the log. Import it, then delete it.

For unattended installs, pass the key in the environment:

```bash
PVPS_SSH_PUBKEY="$(cat phone.pub)" bash install.sh
```

For a non-interactive run, export the key first so it reaches the installer process, then download and verify the installer:

```bash
export PVPS_SSH_PUBKEY="$(cat phone.pub)"
base="https://github.com/fabiomode2/pvps/releases/latest/download"
curl -fsSLO "${base}/install.sh"
curl -fsSLO "${base}/SHA256SUMS"
grep -E '  install\.sh$' SHA256SUMS | sha256sum -c -
bash install.sh
```

## Host and SSH port

The installer uses the VPS **public IPv4** by default, because phones on mobile data cannot reach a Tailscale `100.x` or private LAN address.

| Situation | Use |
| --- | --- |
| Public IPv4 | Default, nothing to set |
| Phone on the same Tailscale tailnet | `--tailscale` or `PVPS_PREFER_TAILSCALE=1` |
| Private LAN or specific hostname | `PVPS_HOST=192.168.1.20` |
| Non-standard SSH port | `PVPS_SSH_PORT=2222` |

When printed, the `SSH: user@host:port` header shows what will be imported. If it is not reachable from the phone, re-run with `PVPS_HOST`/`PVPS_SSH_PORT`.

## What the installer does

- Installs missing packages: `curl`, `ca-certificates`, `tmux`, `openssl`, `python3`, `unzip`.
- Installs OpenCode v2 under `~/.opencode/bin/opencode` and refuses an existing v1 binary.
- Creates `/etc/pvps/opencode.env`, owned by `root:root` with mode `0600`, and reuses the existing password on re-runs.
- Writes `pvps-opencode.service` running as your user on `127.0.0.1:49374`.
- Adds the phone key to `~/.ssh/authorized_keys`, deduplicated, with `.ssh` at `0700` and `authorized_keys` at `0600`.
- Adds SSH keepalives (`ClientAliveInterval`) and raises `MaxSessions` so long-lived mobile tunnels survive NAT idle timeouts and the app's HTTP/SSE channels.
- Optionally creates swap and restricts SSH forwarding (see below).
- Verifies `http://127.0.0.1:49374/api/info` before printing the setup code.

## Updating

Re-run the same install command. The installer is idempotent:

- The existing OpenCode password is reused.
- The phone key is not added twice.
- The systemd unit is restarted only when it changed or the health check fails.
- The app updates itself from GitHub Releases.

To rotate the OpenCode password, remove the env file and re-run:

```bash
sudo rm /etc/pvps/opencode.env
# re-run install.sh, then import the new setup code
```

## Security notes

- OpenCode binds to `127.0.0.1` only. The app reaches it through the SSH tunnel.
- The app pins the SSH host key on first connect; the SSH private key stays on the phone.
- `/etc/pvps/opencode.env` is root-owned and not readable by other users.
- The setup code contains the OpenCode password. Do not paste it into chats, tickets, logs, or screenshots.
- The code is only printed to a terminal. Unattended runs without a terminal write it to `~/.pvps-setup-code` with mode `0600`; delete that file after importing.
- `install.sh` is listed in the release `SHA256SUMS`, and the documented install steps verify it before execution. The checksum file and installer come from the same release channel, so this detects a corrupted or mismatched asset but not a compromised release publisher; compare the digest out-of-band if you need stronger assurance.
- The upstream OpenCode v2 installer is downloaded to a temporary file and executed from that file, not piped directly into `bash`. Set `PVPS_OPENCODE_INSTALL_SHA256` to a reviewed digest to reject a changed upstream installer. Without it, the script prints a warning and relies on HTTPS only; that remains a residual trust-on-first-download risk.
- `--harden-ssh` sets `AllowTcpForwarding local` and `PermitOpen 127.0.0.1:49374`. Keep an existing SSH session open until you have tested a new one; a bad SSH config can lock you out.
- The installer does not disable SSH password authentication. Prefer key-only SSH and restrict the SSH port in your cloud firewall.

## Advanced options

| Option / variable | Effect |
| --- | --- |
| `--tailscale` | Use the Tailscale `100.x` address in the setup code |
| `PVPS_PREFER_TAILSCALE=1` | Same as `--tailscale` |
| `PVPS_HOST=HOST` | Force the host in the setup code |
| `PVPS_SSH_PORT=PORT` | Force the SSH port in the setup code |
| `PVPS_SSH_PUBKEY=KEY_OR_PATH` | Phone key, or path to a `.pub` file; skips the prompt |
| `PVPS_OPENCODE_INSTALL_SHA256=HEX` | Require this SHA-256 for the upstream OpenCode v2 installer; a mismatch aborts |
| `PVPS_NONINTERACTIVE=1` | Never prompt; missing key is only reported |
| `--swap` | Create and enable a 4 GB `/swapfile` if none exists |
| `--harden-ssh` | Restrict SSH local forwarding to `127.0.0.1:49374` |
| `--replace` | Stop an old `opencode.service`/managed service holding port `49374` |

`--replace` only targets known OpenCode service patterns. It never kills an unidentified process.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `No route to host` / timeout | Does the setup code use a reachable host? Public IPv4 vs Tailscale/LAN; cloud firewall allows the SSH port |
| `Permission denied (publickey)` | Phone key present in `~/.ssh/authorized_keys`; `.ssh` is `0700`, `authorized_keys` is `0600`; correct SSH user; correct key selected in app |
| `Invalid setup code` | Copy the complete single line, including `pvps-setup:`; do not let the terminal wrap it; use a current app build |
| OpenCode check fails | `systemctl status pvps-opencode`; `journalctl -u pvps-opencode -n 80`; confirm nothing else owns `49374` |
| Port `49374` already in use | Re-run with `--replace`, or stop the old service; inspect with `sudo ss -ltnp 'sport = :49374'` |
| Tunnel drops when the screen is off | Settings → Connection: enable **Always-on tunnel**, allow battery optimization exemption, and re-run the installer to apply the sshd keepalives |
| Existing OpenCode v1 | Move the old `~/.opencode/bin/opencode` aside, then re-run |
| Locked out after `--harden-ssh` | Keep a session open; remove `/etc/ssh/sshd_config.d/99-pvps.conf`, run `sudo sshd -t`, reload SSH |

## Uninstall

```bash
sudo systemctl disable --now pvps-opencode
sudo rm -f /etc/systemd/system/pvps-opencode.service
sudo rm -f /etc/pvps/opencode.env
sudo rm -f /etc/ssh/sshd_config.d/99-pvps.conf
rm -f ~/.pvps-setup-code
sudo systemctl daemon-reload
```

The app, `~/.opencode`, and `~/.ssh/authorized_keys` are left in place. Remove the `pvps` key line from `authorized_keys` if you no longer want phone access.

## Development

```bash
./scripts/dev.sh install            # incremental build + install + launch
./scripts/dev.sh watch              # rebuild + reinstall on save
./scripts/dev.sh connect 192.168.1.5:5555
./scripts/dev.sh apk                # debug APK only
./scripts/dev.sh logcat             # follow app logs
```

`watch` needs `inotify-tools`. Design tokens live in `ui/theme/Theme.kt`; shared widgets live in `ui/components/Components.kt`.

Termux terminal-emulator is GPLv3; see [NOTICE](NOTICE).
