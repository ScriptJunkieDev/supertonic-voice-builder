# towncraft-ptero-arm

Generic **Java 21 + Python** Docker image for Pterodactyl, built for `linux/amd64` and `linux/arm64`.

This root project is the platform image only. It should not contain app-specific `ENV`, JARs, Python requirements, or install logic. App-specific Supertonic files are staged in [`supertonic-app/`](supertonic-app/) so that folder can be moved into its own repository later.

## Images

| Tag | Use |
|-----|-----|
| `ghcr.io/scriptjunkiedev/towncraft-ptero-arm:java_21_python` | Server runtime (`USER container`) |
| `ghcr.io/scriptjunkiedev/towncraft-ptero-arm:java_21_python_install` | Egg Reinstall only (`root`) |

Both tags are built from the single root [`Dockerfile`](Dockerfile):

- `runtime` target: normal Pterodactyl server container
- `install` target: root install container for Wings Reinstall

## Build / Publish

[`.github/workflows/docker.yml`](.github/workflows/docker.yml) publishes both tags to GHCR on push to `main` / tags.

```bash
docker pull ghcr.io/scriptjunkiedev/towncraft-ptero-arm:java_21_python
docker pull ghcr.io/scriptjunkiedev/towncraft-ptero-arm:java_21_python_install
```

## What Belongs Here

- `Dockerfile`
- `.github/workflows/docker.yml`
- generic image documentation

## What Does Not Belong Here

- app source (`pom.xml`, `src/`, `worker/`)
- app eggs or app install scripts
- app `.env.example`
- bundled app payloads

Those Supertonic files are currently grouped under [`supertonic-app/`](supertonic-app/) as a move-ready folder for a future app repository.
