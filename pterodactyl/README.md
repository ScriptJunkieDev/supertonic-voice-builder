# Pterodactyl: Java 21 + Python (generic egg)

Generic runtime for Spring Boot apps that need **Java 21** and **Python 3**. The egg does **not** clone third-party repositories; your application handles that (this repo does via `TrainerBootstrapService` on startup).

## 1. Runtime image (yolk)

**CI:** push to `main` builds:

```text
ghcr.io/scriptjunkiedev/java21-python-yolk:java_21_python
```

Built from `pterodactyl/Dockerfile.yolk` (Java yolk + `python3`, `pip`, `git`, ffmpeg, libsndfile). Make the GHCR package **public** if the panel has no registry auth.

## 2. Import the egg

1. Admin → **Nests** → import `pterodactyl/egg-java21-python.json`.
2. Create or reinstall a server using this egg.

Install only creates `data/` and a short `README.txt` — no trainer clone.

## 3. Deploy Supertonic Voice Builder

Upload to `/home/container`:

- `app.jar`
- `worker/` (optional `trainer-backup/` if you vendor a trainer snapshot for offline use)

Set app env in the panel (or `APP_ARGS`) — see root `.env.example` / README. Defaults use `./data`, `./voices`, `TRAINER_GIT_URL`, etc.

## 4. Verify

```text
GET /api/health
```

Check `trainerBootstrap`, `trainerPresent`, `pythonAvailable`.

## Appliance image (this app only)

```text
ghcr.io/scriptjunkiedev/supertonic-voice-builder:latest
```

Java + Python **runtime** only; trainer is fetched or restored from `trainer-backup/` at startup, not baked into the image build from upstream git.
