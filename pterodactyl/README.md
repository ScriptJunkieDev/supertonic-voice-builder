# Pterodactyl: Supertonic Voice Builder (dedicated egg)

**Not a generic Java/Python egg.** Use only with [supertonic-voice-builder](https://github.com/ScriptJunkieDev/supertonic-voice-builder) (`app.jar` + `worker/`).

## 1. Runtime image (Ptero)

**CI** (push to `main`) publishes:

```text
ghcr.io/scriptjunkiedev/supertonic-voice-builder-ptero:runtime
```

Egg **install** uses sibling tag **`install`** (root user) so Wings can run `/mnt/install/install.sh`.

Built from `pterodactyl/Dockerfile.yolk` and `Dockerfile.yolk-install`. Multi-arch: `linux/amd64`, `linux/arm64`. Make GHCR **public** if the panel has no registry auth.

## 2. Import the egg

1. Admin → **Nests** → import `pterodactyl/egg-supertonic-voice-builder.json`.
2. Create or **reinstall** a server using this egg.

**Important:** Updating the egg JSON does **not** re-run install on existing servers. Use **Reinstall Server** to rebuild `./venv`.

Install creates `venv/` (PyTorch CPU), `data/`, `voices/` under `/home/container`.

## 3. Deploy the app

Upload to `/home/container`:

- `app.jar`
- `worker/` (optional `trainer-backup/` for offline trainer snapshot)

Panel defaults:

- `PYTHON_BIN=./venv/bin/python3`
- `JAR_FILE=app.jar`
- **`JVM_HEAP_MB=1024`** — do not give Java the full server RAM (PyTorch training needs the rest).
- Assign **6–8 GB+** container memory for CPU training.

## 4. Verify

```text
GET /api/health
```

## Docker appliance (non-Ptero)

```text
ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest
```

Separate image from the Ptero egg runtime — use for Docker Compose / VPS, not for unrelated apps.
