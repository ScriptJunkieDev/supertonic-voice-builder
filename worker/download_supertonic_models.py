#!/usr/bin/env python3
"""Download Supertone/supertonic-3 weights into the trainer tree (upstream setup.sh equivalent)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser(description="Download Supertonic HF model into trainer directory")
    parser.add_argument("--trainer-dir", required=True)
    parser.add_argument("--repo", default="Supertone/supertonic-3")
    parser.add_argument("--local-dir-name", default="supertonic3")
    args = parser.parse_args()

    trainer_dir = Path(args.trainer_dir).resolve()
    local_dir = trainer_dir / args.local_dir_name
    marker = local_dir / "onnx" / "duration_predictor.onnx"

    if marker.is_file():
        print(f"Supertonic weights already present: {marker}", flush=True)
        return

    try:
        from huggingface_hub import snapshot_download
    except ImportError as e:
        print("ERROR: huggingface_hub is not installed in this venv.", file=sys.stderr, flush=True)
        raise SystemExit(1) from e

    token = __import__("os").environ.get("HF_TOKEN") or __import__("os").environ.get("HUGGING_FACE_HUB_TOKEN")
    print(f"Downloading {args.repo} -> {local_dir} (this may take several minutes) ...", flush=True)
    snapshot_download(
        repo_id=args.repo,
        local_dir=str(local_dir),
        token=token,
    )

    if not marker.is_file():
        print(f"ERROR: download finished but missing expected file: {marker}", file=sys.stderr, flush=True)
        raise SystemExit(2)

    print(f"Supertonic weights ready at {local_dir}", flush=True)


if __name__ == "__main__":
    main()
