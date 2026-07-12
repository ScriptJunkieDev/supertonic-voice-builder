# Pterodactyl: Supertonic Voice Builder (dedicated egg)

**Not a generic Java/Python egg.** Use only with [supertonic-voice-builder](https://github.com/ScriptJunkieDev/supertonic-voice-builder) (`app.jar` + `worker/`).

## 1. Images (CI)

Push to `main` publishes **multi-arch** (`linux/amd64`, `linux/arm64`) — same Python/Java stack as x86, plus ARM:

```text
ghcr.io/scriptjunkiedev/java21-python-yolk:java_21_python          # server runtime
ghcr.io/scriptjunkiedev/java21-python-yolk:java_21_python_install  # egg Reinstall only (root)
```

Built from `pterodactyl/Dockerfile.yolk` and `Dockerfile.yolk-install`. Make GHCR **public** if the panel has no registry auth.

After CI, on each Wings node: `docker pull` both tags before Reinstall if the node had cached amd64-only layers.

## 2. Import the egg

1. Admin → **Nests** → import `pterodactyl/egg-supertonic-voice-builder.json`.
2. Create or **Reinstall** the server.

Install creates `venv/` (PyTorch CPU), `data/`, `voices/` under `/home/container`.

## 3. Deploy

Upload to `/home/container`:

- `app.jar`
- `worker/` (optional `trainer-backup/`)

Panel defaults:

- `PYTHON_BIN=./venv/bin/python3`
- `JAR_FILE=app.jar`

Startup uses `{{SERVER_MEMORY}}` for Java `-Xmx` (unchanged from the working x86 egg).

## 4. Verify

```text
GET /api/health
```

## Docker appliance (non-Ptero)

```text
ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest
```

Separate from the Ptero yolk — use for Docker Compose / VPS.
