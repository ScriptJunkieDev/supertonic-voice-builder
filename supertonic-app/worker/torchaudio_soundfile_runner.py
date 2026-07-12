#!/usr/bin/env python3
"""Run train_style.py with torchaudio.load backed by soundfile (no torchcodec/FFmpeg)."""
from __future__ import annotations

import os
import runpy
import sys
from pathlib import Path


def _patch_torchaudio_load() -> None:
    import torch
    import soundfile as sf
    import torchaudio

    def load(uri, frame_offset=0, num_frames=-1, normalize=True, channels_first=True, **kwargs):
        del kwargs  # upstream loss.py only passes a path
        data, sample_rate = sf.read(uri, dtype="float32", always_2d=True)
        if frame_offset or num_frames != -1:
            start = int(frame_offset)
            end = None if num_frames == -1 else start + int(num_frames)
            data = data[start:end]
        tensor = torch.from_numpy(data.T if channels_first else data)
        if normalize and tensor.numel():
            peak = tensor.abs().max()
            if float(peak) > 1.0:
                tensor = tensor / peak
        return tensor, sample_rate

    torchaudio.load = load  # type: ignore[method-assign]


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: torchaudio_soundfile_runner.py /path/to/train_style.py [train_style args...]", file=sys.stderr)
        sys.exit(2)

    train_script = Path(sys.argv[1]).resolve()
    if not train_script.is_file():
        print(f"ERROR: train_style.py not found at {train_script}", file=sys.stderr)
        sys.exit(2)

    trainer_dir = train_script.parent
    os.chdir(trainer_dir)
    trainer_dir_str = str(trainer_dir)
    if trainer_dir_str not in sys.path:
        sys.path.insert(0, trainer_dir_str)

    _patch_torchaudio_load()
    sys.argv = [str(train_script), *sys.argv[2:]]
    runpy.run_path(str(train_script), run_name="__main__")


if __name__ == "__main__":
    main()
