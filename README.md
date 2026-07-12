# Java 21 + Python (`app`)

Generic **Java 21 + Python** Docker image for Pterodactyl, built for `linux/amd64` and `linux/arm64`.

This root project is the platform image only. It should not contain app-specific `ENV`, JARs, Python requirements, or install logic. App-specific Supertonic files are staged in [`supertonic-app/`](supertonic-app/) so that folder can be moved into its own repository later.

## Image

| Tag | Use |
|-----|-----|
| `ghcr.io/scriptjunkiedev/app:java21-python` | Server runtime and egg Reinstall (same as official Ptero eggs) |

Built from the root [`Dockerfile`](Dockerfile).

## Build / Publish

[`.github/workflows/docker.yml`](.github/workflows/docker.yml) publishes to GHCR on push to `main` / tags.

```bash
docker pull ghcr.io/scriptjunkiedev/app:java21-python
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
