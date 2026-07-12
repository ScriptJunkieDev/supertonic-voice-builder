# Supertonic Voice Builder App

This folder contains the Supertonic-specific app, build files, worker bundle, and Pterodactyl egg. It is grouped so its contents can be moved into a separate `supertonic-voice-builder` repository later.

It depends on the generic Pterodactyl image from the platform repo:

```text
ghcr.io/scriptjunkiedev/app:java21-python
```

## Contents

```text
pom.xml                    Maven build for app.jar
src/                       Spring Boot app
worker/                    Python training worker
eggs/supertonic/           Pterodactyl egg, install script, bundle zip, env example
trainer-backup/            Optional offline trainer snapshot slot
```

## Build App JAR

```bash
mvn clean package -DskipTests
```

Upload `target/supertonic-voice-builder-0.1.0.jar` to Pterodactyl as `app.jar`.

## Pterodactyl Egg

Import:

```text
eggs/supertonic/egg-supertonic-voice-builder.json
```

Reinstall downloads `eggs/supertonic/supertonic-server-bundle.zip`, extracts `worker/`, creates `./venv`, and installs Python training deps.

When `worker/` changes:

```bash
bash eggs/supertonic/build-bundle.sh
python eggs/supertonic/regenerate-egg-json.py
```

Commit the updated `supertonic-server-bundle.zip` and egg JSON before deploying.

## Config

See:

```text
eggs/supertonic/.env.example
```

The default `SUPERTONIC_BUNDLE_URL` in the egg assumes these app files are at the root of the future Supertonic app repo (`eggs/supertonic/...`).
