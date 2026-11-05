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
- OpenCode v2 sessions, streaming, tools, permissions, step-by-step inline
  questions, photo/file attachments, slash commands, markdown/tables/code
  rendering and per-session drafts.
- Native terminal, SFTP, Docker, systemd and processes.
- Auto-updates from GitHub Releases.

Termux terminal-emulator is GPLv3; see [NOTICE](NOTICE).

## Fast iteration

```bash
./scripts/dev.sh install            # incremental build + install + launch
./scripts/dev.sh watch              # rebuild + reinstall on save
./scripts/dev.sh connect 192.168.1.5:5555  # adb over WiFi
./scripts/dev.sh apk                # debug APK only
./scripts/dev.sh logcat             # follow app logs
```

Gradle's daemon and build cache stay warm between runs. On ARM hosts the script
automatically uses the local qemu-wrapped `aapt2` in `/tmp/opencode`. `watch`
needs `inotify-tools` and is the fastest loop when the phone is connected over
USB or wireless adb.

### UI iteration without reinstalling

The app reads its appearance at runtime. **Settings → Appearance** can switch
the palette, change the font scale and zoom the whole interface (spacing and
controls included); changes apply immediately and persist, no rebuild needed.

For structural UI edits (layout, new composables), use Android Studio's
Compose **Live Edit** with a debug build on Android 11+. It pushes changed
composable bodies to the running app without a full reinstall. Android has no
generic "reload any new Kotlin code" mechanism without that tooling; if Live
Edit is not available, use `./scripts/dev.sh watch` plus wireless adb.

Design tokens live in `ui/theme/Theme.kt`; shared widgets live in
`ui/components/Components.kt`, so screens normally only compose existing
building blocks.
