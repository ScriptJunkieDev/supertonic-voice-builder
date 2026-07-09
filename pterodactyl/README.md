# Pterodactyl: Java 21 + Python (generic egg)

Generic runtime for Spring Boot apps that need **Java 21** and **Python 3**. The egg installs **PyTorch CPU + trainer pip deps** into `./venv`. The Java app does **not** run `pip`; it only clones `supertonic3-voice-clone` sources at startup.

## 1. Runtime image (yolk)

**CI:** push to `main` builds:

```text
ghcr.io/scriptjunkiedev/java21-python-yolk:java_21_python
```

Built from `pterodactyl/Dockerfile.yolk` (Java yolk + `python3`, `pip`, `git`, ffmpeg, libsndfile). Make the GHCR package **public** if the panel has no registry auth.

Egg **installation** uses a sibling image `java21-python-yolk:java_21_python_install` (same Python, **root** user) so Wings can run `/mnt/install/install.sh`. The runtime server still uses `java_21_python` (`USER container`).

## 2. Import the egg

1. Admin → **Nests** → import `pterodactyl/egg-java21-python.json`.
2. Create or **reinstall** a server using this egg.

**Important:** Updating the egg JSON in the panel does **not** re-run install on existing servers. Use **Reinstall Server** (wipes `/home/container` except what install recreates) so the installation script runs and builds `venv/`.

The install script is **inlined in the egg**. It runs in **`java21-python-yolk:java_21_python_install`** (root; same Python 3.10+ as the game container). Do **not** point installation at the runtime yolk tag — that image runs as `container` and Wings will fail with `install.sh: Permission denied`.

Install creates:

- `venv/` — PyTorch CPU stack (`worker/trainer-pip-requirements.txt`)
- `data/`, `data/tmp`, `voices/`

Everything stays under `/home/container` (install uses `TMPDIR=/mnt/server/data/tmp`).

## 3. Deploy Supertonic Voice Builder

Upload to `/home/container`:

- `app.jar`
- `worker/` (optional `trainer-backup/` if you vendor a trainer snapshot for offline use)

Panel defaults:

- `PYTHON_BIN=./venv/bin/python3`
- `JAR_FILE=app.jar`

See root `.env.example` for trainer clone URLs and paths.

If you previously used `data/trainer-venv` from an older app-side pip flow, remove it after switching to this egg.

## 4. Verify

```text
GET /api/health
```

Check `trainerBootstrap`, `trainerPresent`, `pythonAvailable`, `diskFreeContainerMiB`.

## Appliance image (this app only)

```text
ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest
```

Java + Python **runtime** with `/app/venv` baked at image build; trainer sources are fetched or restored from `trainer-backup/` at startup, not cloned in the Docker build from upstream git.
