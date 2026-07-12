# Supertonic egg

- **`egg-supertonic-voice-builder.json`** — import into Pterodactyl.
- **`egg-install.sh`** — install script source; embedded into the egg JSON by `regenerate-egg-json.py`.
- **`supertonic-server-bundle.zip`** — built by [`build-bundle.sh`](build-bundle.sh) from repo `worker/` and `trainer-backup/README.md`; fetched by the install script on Reinstall (commit after rebuilding when `worker/` changes).
- **[`.env.example`](.env.example)** — optional Spring env reference for the panel or local runs.

```bash
./build-bundle.sh
python3 regenerate-egg-json.py
```

Default bundle URL (override with egg/server `SUPERTONIC_BUNDLE_URL`):

`https://raw.githubusercontent.com/ScriptJunkieDev/supertonic-voice-builder/main/eggs/supertonic/supertonic-server-bundle.zip`

Upload **`app.jar`** separately to `/home/container` after install.
