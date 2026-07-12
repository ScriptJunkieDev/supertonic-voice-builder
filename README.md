# Supertonic Voice Builder

Spring Boot appliance with a built-in web UI for creating Supertonic custom voice-style JSON files from WAV samples.

**CPU-only** — no NVIDIA, CUDA, or Intel XPU.

Repository: [github.com/ScriptJunkieDev/supertonic-voice-builder](https://github.com/ScriptJunkieDev/supertonic-voice-builder)

## What it does

- Upload a `.wav` file and name the voice
- Choose fast / balanced / high training step counts
- Run [supertonic3-voice-clone](https://github.com/saurabhv749/supertonic3-voice-clone) `train_style.py` via `worker/train_voice.py`
- Stream training logs in the browser
- Export `<voice>.json` to the configured output directory

This project wraps that flow in Spring Boot and a small Python worker.

## Trainer bootstrap (app-owned)

Docker **appliance** and **Pterodactyl** images are built for this repo only. They do **not** clone the upstream trainer at image build time.

On startup, `TrainerBootstrapService` fetches **trainer source files** and **Supertonic ONNX weights** (no app `pip`):

1. Uses `TRAINER_DIR` if `train_style.py` already exists  
2. Else `git clone` from `TRAINER_GIT_URL`  
3. Else downloads `TRAINER_ARCHIVE_URL` zip into `data/tmp`  
4. Else copies from `TRAINER_BACKUP_DIR` (optional local vendor snapshot — see [`trainer-backup/README.md`](trainer-backup/README.md))  
5. If `supertonic3/onnx/*.onnx` are missing, downloads **`TRAINER_HF_MODEL`** (default `Supertone/supertonic-3`) via `worker/download_supertonic_models.py` — same as upstream `setup.sh`

**PyTorch and other Python deps** are installed outside the app:

- **Pterodactyl:** egg install script → `./venv` (see `pterodactyl/install-voice-builder-deps.sh`)  
- **Docker appliance:** image build creates `/app/venv` from `worker/trainer-pip-requirements.txt`  

Check `GET /api/health` for `trainerBootstrap` / `trainerPresent` / `pythonAvailable`.

## Requirements

| Context | You need |
|--------|----------|
| **Build** | JDK 21, Maven 3.9+ |
| **Run (jar / Ptero egg)** | JRE 21, Python 3, `git` on PATH for clone fallback |
| **Run (Docker)** | Docker — image provides Java, Python, `git`; app fetches trainer |

## Clone and build

```bash
git clone https://github.com/ScriptJunkieDev/supertonic-voice-builder.git
cd supertonic-voice-builder
mvn clean package -DskipTests
```

Boot JAR: `target/supertonic-voice-builder-0.1.0.jar` (UI embedded at `classpath:/static/`).

## Configuration

See `.env.example`. Important variables:

| Variable | Purpose |
|----------|---------|
| `TRAINER_GIT_URL` | Upstream trainer git URL (clone on startup) |
| `TRAINER_BACKUP_DIR` | Local vendor copy if clone fails (default `./trainer-backup`) |
| `TRAINER_DIR` | Where `train_style.py` lives after bootstrap |
| `TRAINER_BOOTSTRAP_ENABLED` | Set `false` if you manage trainer files yourself |
| `PYTHON_BIN` | Default `./venv/bin/python3` (egg or Docker image) |

## Docker images (GitHub Actions)

[`.github/workflows/docker.yml`](.github/workflows/docker.yml) publishes **multi-arch** manifests (`linux/amd64`, `linux/arm64`) to GHCR on push to `main` / tags:

| Image | Use |
|-------|-----|
| `ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest` | Docker appliance (JAR + worker + `trainer-backup/` slot) |
| `ghcr.io/scriptjunkiedev/java21-python-yolk:java_21_python` | **Pterodactyl server runtime** |
| `ghcr.io/scriptjunkiedev/java21-python-yolk:java_21_python_install` | Pterodactyl egg Reinstall only (root) |

Make GHCR packages **public** if the panel cannot pull private images.

Pull (Docker picks the right arch automatically):

```bash
docker pull ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest
```

Inspect platforms: `docker buildx imagetools inspect ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest`

## Deploy

### Docker appliance

```bash
docker pull ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest
```

Same tag on **amd64** and **arm64**; use on Apple Silicon or ARM VPS without a separate image name.

Populate `trainer-backup/` on the host before build if you want an offline snapshot in the image; otherwise the app clones on first start (needs network + git in container).

### Pterodactyl

Dedicated egg: [`pterodactyl/egg-supertonic-voice-builder.json`](pterodactyl/egg-supertonic-voice-builder.json) + [`pterodactyl/README.md`](pterodactyl/README.md).

Upload `app.jar`, `worker/`, and optionally `trainer-backup/` via local deploy (`send.jar`, gitignored bat/ps1).

## Repository hygiene

Gitignored: `*.jar`, `*.zip`, `*.bat`, `*.ps1`, `trainer-backup/**` (except `trainer-backup/README.md`), runtime data dirs, `.env`, `.cursor/`.

## Project layout

```text
src/main/java/          API, TrainerBootstrapService, embedded UI
worker/                 train_voice.py
trainer-backup/         Optional vendor snapshot (contents not in git)
pterodactyl/            Supertonic Voice Builder dedicated egg + java21-python-yolk Dockerfiles
Dockerfile              App image (no upstream git clone at build)
```

## Responsible use

Only create custom voices from speakers you have permission to clone. Do not use this for impersonation, fraud, harassment, or bypassing voice authentication.
