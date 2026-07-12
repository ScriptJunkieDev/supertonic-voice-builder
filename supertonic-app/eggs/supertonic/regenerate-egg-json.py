#!/usr/bin/env python3
"""Embed eggs/supertonic/egg-install.sh into egg-supertonic-voice-builder.json."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EGG = ROOT / "eggs" / "supertonic" / "egg-supertonic-voice-builder.json"
SCRIPT = ROOT / "eggs" / "supertonic" / "egg-install.sh"
IMG = "ghcr.io/scriptjunkiedev/towncraft-ptero-arm"


def main() -> None:
    egg = json.loads(EGG.read_text(encoding="utf-8"))
    script = SCRIPT.read_text(encoding="utf-8").replace("\r\n", "\n")
    if not script.endswith("\n"):
        script += "\n"
    egg["scripts"]["installation"]["script"] = script
    egg["scripts"]["installation"]["container"] = f"{IMG}:java_21_python_install"
    egg["docker_images"] = {f"{IMG}:java_21_python": f"{IMG}:java_21_python"}
    egg["description"] = (
        "Dedicated to ScriptJunkieDev/supertonic-voice-builder. "
        "Reinstall fetches supertonic-server-bundle.zip (worker/) and creates ./venv. "
        "Upload app.jar only. Not for other Spring or Python apps."
    )
    vars = egg.setdefault("variables", [])
    if not any(v.get("env_variable") == "SUPERTONIC_BUNDLE_URL" for v in vars):
        vars.append(
            {
                "name": "Supertonic bundle URL",
                "description": "Zip with worker/ (default: GitHub raw on main).",
                "env_variable": "SUPERTONIC_BUNDLE_URL",
                "default_value": "https://raw.githubusercontent.com/ScriptJunkieDev/supertonic-voice-builder/main/eggs/supertonic/supertonic-server-bundle.zip",
                "user_viewable": True,
                "user_editable": True,
                "rules": "required|string|url",
                "field_type": "text",
            }
        )
    EGG.write_text(json.dumps(egg, indent=4, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Updated {EGG.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
