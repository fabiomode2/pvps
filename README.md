# PVPS

Portable VPS control for Android.

**Download:** [latest APK](https://github.com/fabiomode2/pvps/releases/latest)

**VPS setup (Ubuntu/Debian):**

```bash
curl -fsSL https://github.com/fabiomode2/pvps/releases/latest/download/install.sh | bash
```

Paste the `pvps-setup:` code it prints into the app. Add your phone SSH key separately, or pass `PVPS_SSH_PUBKEY` to the installer.

The setup code uses the server's **public IPv4** by default. A Tailscale `100.x`
address only works while the phone is signed into the same tailnet; if that is
what you want, run the installer with `--tailscale` (or set
`PVPS_HOST=100.x.y.z`). Use `PVPS_SSH_PORT=2222` for a non-standard SSH port.

**Troubleshooting `EHOSTUNREACH` / `No route to host`:** the phone cannot route
to the host in the setup code. Check that it uses the public IPv4 (not a
Tailscale address), that port 22 is open in the cloud firewall, and that the
phone's public key is in `~/.ssh/authorized_keys`.

- SSH-only, no cloud backend; keys stay on the phone.
- OpenCode v2 sessions, streaming, tools and permissions.
- Native terminal, SFTP, Docker, systemd and processes.
- Auto-updates from GitHub Releases.

Termux terminal-emulator is GPLv3; see [NOTICE](NOTICE).

## Fast iteration

```bash
./scripts/dev.sh install   # incremental build + install + launch
./scripts/dev.sh apk       # debug APK only
./scripts/dev.sh logcat    # follow app logs
```

Gradle's daemon and build cache stay warm between runs. On ARM hosts the script
automatically uses the local qemu-wrapped `aapt2` in `/tmp/opencode`.
