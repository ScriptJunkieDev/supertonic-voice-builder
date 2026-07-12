# Optional vendor copy of the upstream trainer

If the upstream Git repository is unreachable at startup, the app copies this folder into `TRAINER_DIR` (see `TRAINER_BACKUP_DIR`).

## Populate (your machine, not committed)

1. Clone the trainer repo locally (default URL is set in `application.yml` as `TRAINER_GIT_URL`).
2. Copy the **contents** of that clone into this directory so `train_style.py` exists at:

   ```text
   trainer-backup/train_style.py
   ```

3. Optionally mirror trainer files you need for offline bootstrap (PyTorch deps come from the Supertonic egg `venv`, not from here).

Everything under `trainer-backup/` except this README is **gitignored**.

At runtime the app will:

1. Use `TRAINER_DIR` if `train_style.py` is already there  
2. Else `git clone` from `TRAINER_GIT_URL`  
3. Else download `TRAINER_ARCHIVE_URL` (GitHub zip over HTTPS — works when `git` is blocked)  
4. Else copy from `trainer-backup/`  
5. Download HF ONNX weights if missing (see `TrainerBootstrapService`)
