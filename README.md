# PVPS

Portable VPS control for Android.

**Download:** [latest APK](https://github.com/fabiomode2/pvps/releases/latest)

**VPS setup (Ubuntu/Debian):**

```bash
curl -fsSL https://github.com/fabiomode2/pvps/releases/latest/download/install.sh | bash
```

Paste the `pvps-setup:` code it prints into the app. Add your phone SSH key separately, or pass `PVPS_SSH_PUBKEY` to the installer.

- SSH-only, no cloud backend; keys stay on the phone.
- OpenCode v2 sessions, streaming, tools and permissions.
- Native terminal, SFTP, Docker, systemd and processes.

Termux terminal-emulator is GPLv3; see [NOTICE](NOTICE).
