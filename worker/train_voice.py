#!/usr/bin/env python3
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


def find_newest_json(root: Path):
    if not root.exists():
        return None
    jsons = [p for p in root.rglob("*.json") if p.is_file()]
    if not jsons:
        return None
    return max(jsons, key=lambda p: p.stat().st_mtime)


def main():
    parser = argparse.ArgumentParser(description="Run supertonic3-voice-clone train_style.py and export the resulting JSON")
    parser.add_argument("--trainer-dir", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--target-wav-path", required=True)
    parser.add_argument("--num-steps", required=True)
    parser.add_argument("--learning-rate", required=True)
    parser.add_argument("--reference-style", default="auto")
    parser.add_argument("--job-dir", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    trainer_dir = Path(args.trainer_dir)
    train_script = trainer_dir / "train_style.py"
    output_dir = Path(args.output_dir)
    job_dir = Path(args.job_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    job_dir.mkdir(parents=True, exist_ok=True)

    if not train_script.exists():
        print(f"ERROR: train_style.py not found at {train_script}", flush=True)
        sys.exit(2)

    before = set(str(p) for p in trainer_dir.rglob("*.json"))

    cmd = [
        sys.executable,
        str(train_script),
        "--name", args.name,
        "--target-wav-path", args.target_wav_path,
        "--num-steps", str(args.num_steps),
        "--learning-rate", str(args.learning_rate),
    ]

    if args.reference_style:
        cmd.extend(["--reference-style", args.reference_style])

    env = os.environ.copy()
    env["CUDA_VISIBLE_DEVICES"] = ""
    env.setdefault("OMP_NUM_THREADS", "16")
    env.setdefault("MKL_NUM_THREADS", "16")

    print("CPU-only mode enabled. CUDA_VISIBLE_DEVICES is blank.", flush=True)
    print("Running:", " ".join(cmd), flush=True)

    proc = subprocess.Popen(cmd, cwd=str(trainer_dir), env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
    assert proc.stdout is not None
    for line in proc.stdout:
        print(line, end="", flush=True)
    exit_code = proc.wait()
    if exit_code != 0:
        print(f"Training failed with exit code {exit_code}", flush=True)
        sys.exit(exit_code)

    after_paths = [p for p in trainer_dir.rglob("*.json") if p.is_file() and str(p) not in before]
    if after_paths:
        result = max(after_paths, key=lambda p: p.stat().st_mtime)
    else:
        likely_dirs = [trainer_dir / args.name, trainer_dir / "runs" / args.name, trainer_dir / "outputs" / args.name, trainer_dir / "logs" / args.name]
        candidates = [find_newest_json(p) for p in likely_dirs]
        candidates = [p for p in candidates if p is not None]
        result = max(candidates, key=lambda p: p.stat().st_mtime) if candidates else find_newest_json(trainer_dir)

    if not result or not result.exists():
        print("Training completed, but no JSON output could be located.", flush=True)
        sys.exit(3)

    target = output_dir / f"{args.name}.json"
    shutil.copy2(result, target)
    shutil.copy2(result, job_dir / f"{args.name}.json")
    print(f"Exported voice JSON: {target}", flush=True)


if __name__ == "__main__":
    main()
