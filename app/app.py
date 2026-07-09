import os
import re
import json
import shutil
import subprocess
import threading
import time
import uuid
from datetime import datetime
from pathlib import Path

import gradio as gr

DATA_HOME = Path(os.environ.get("DATA_HOME", "/data"))
CLONE_HOME = Path(os.environ.get("CLONE_HOME", "/opt/supertonic3-voice-clone"))
UPLOAD_DIR = DATA_HOME / "uploads"
OUTPUT_DIR = DATA_HOME / "outputs"
JOB_DIR = DATA_HOME / "jobs"
VOICE_DIR = DATA_HOME / "voices"

for p in [UPLOAD_DIR, OUTPUT_DIR, JOB_DIR, VOICE_DIR]:
    p.mkdir(parents=True, exist_ok=True)

JOBS = {}


def safe_name(value: str) -> str:
    value = (value or "voice").strip().lower()
    value = re.sub(r"[^a-z0-9._-]+", "-", value)
    value = value.strip("-._")
    return value or "voice"


def now_iso() -> str:
    return datetime.utcnow().isoformat(timespec="seconds") + "Z"


def write_job(job_id: str):
    job_path = JOB_DIR / f"{job_id}.json"
    with job_path.open("w", encoding="utf-8") as f:
        json.dump(JOBS[job_id], f, indent=2)


def append_log(job_id: str, line: str):
    JOBS[job_id]["log"] += line
    write_job(job_id)


def find_generated_jsons(start_time: float):
    candidates = []
    search_roots = [CLONE_HOME, OUTPUT_DIR, DATA_HOME]
    for root in search_roots:
        if not root.exists():
            continue
        for path in root.rglob("*.json"):
            try:
                if path.stat().st_mtime >= start_time - 2:
                    candidates.append(path)
            except FileNotFoundError:
                continue
    # Prefer files outside job metadata folder.
    candidates = [p for p in candidates if JOB_DIR not in p.parents]
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates


def run_training(job_id: str, wav_path: Path, voice_name: str, reference_style: str, num_steps: int, learning_rate: float, seed: int, speed: float, vocoder_steps: int):
    job = JOBS[job_id]
    job["status"] = "PROCESSING"
    job["started_at"] = now_iso()
    write_job(job_id)

    start_time = time.time()
    cmd = [
        "python",
        "train_style.py",
        "--name", voice_name,
        "--target-wav-path", str(wav_path),
        "--reference-style", reference_style,
        "--num-steps", str(num_steps),
        "--learning-rate", str(learning_rate),
        "--seed", str(seed),
        "--speed", str(speed),
        "--vocoder-steps", str(vocoder_steps),
    ]

    append_log(job_id, f"Command: {' '.join(cmd)}\n\n")

    try:
        proc = subprocess.Popen(
            cmd,
            cwd=str(CLONE_HOME),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        for line in proc.stdout:
            append_log(job_id, line)

        rc = proc.wait()
        job["return_code"] = rc

        jsons = find_generated_jsons(start_time)
        copied = []
        for src in jsons[:5]:
            dst_name = f"{voice_name}-{src.name}" if src.name == "style.json" else src.name
            dst = VOICE_DIR / dst_name
            if src.resolve() != dst.resolve():
                shutil.copy2(src, dst)
            copied.append(str(dst))

        job["generated_jsons"] = copied
        job["finished_at"] = now_iso()
        job["status"] = "SUCCESS" if rc == 0 else "FAILED"
        if rc != 0:
            job["error"] = f"train_style.py exited with code {rc}"
        write_job(job_id)

    except Exception as e:
        job["status"] = "FAILED"
        job["error"] = str(e)
        job["finished_at"] = now_iso()
        append_log(job_id, f"\nERROR: {e}\n")
        write_job(job_id)


def start_job(wav_file, voice_name, reference_style, num_steps, learning_rate, seed, speed, vocoder_steps):
    if wav_file is None:
        return "Upload a WAV file first.", "", None

    voice_name = safe_name(voice_name)
    job_id = uuid.uuid4().hex[:12]
    uploaded = Path(wav_file.name)
    wav_dst = UPLOAD_DIR / f"{job_id}-{voice_name}.wav"
    shutil.copy2(uploaded, wav_dst)

    JOBS[job_id] = {
        "job_id": job_id,
        "voice_name": voice_name,
        "status": "PENDING",
        "created_at": now_iso(),
        "wav_path": str(wav_dst),
        "generated_jsons": [],
        "log": "",
        "params": {
            "reference_style": reference_style,
            "num_steps": num_steps,
            "learning_rate": learning_rate,
            "seed": seed,
            "speed": speed,
            "vocoder_steps": vocoder_steps,
        },
    }
    write_job(job_id)

    thread = threading.Thread(
        target=run_training,
        args=(job_id, wav_dst, voice_name, reference_style, int(num_steps), float(learning_rate), int(seed), float(speed), int(vocoder_steps)),
        daemon=True,
    )
    thread.start()

    return f"Started job {job_id} for voice '{voice_name}'.", job_id, None


def refresh_job(job_id):
    if not job_id:
        return "No job selected.", "", None

    job = JOBS.get(job_id)
    if job is None:
        path = JOB_DIR / f"{job_id}.json"
        if path.exists():
            job = json.loads(path.read_text(encoding="utf-8"))
        else:
            return "Job not found.", "", None

    status = f"Status: {job.get('status')}"
    if job.get("error"):
        status += f"\nError: {job.get('error')}"
    if job.get("generated_jsons"):
        status += "\nGenerated JSONs:\n" + "\n".join(job.get("generated_jsons", []))

    first_file = None
    for p in job.get("generated_jsons", []):
        if Path(p).exists():
            first_file = p
            break

    return status, job.get("log", ""), first_file


def list_jobs():
    rows = []
    for path in sorted(JOB_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
        try:
            job = json.loads(path.read_text(encoding="utf-8"))
            rows.append([job.get("job_id"), job.get("voice_name"), job.get("status"), job.get("created_at")])
        except Exception:
            continue
    return rows


def list_voice_files():
    files = []
    for path in sorted(VOICE_DIR.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True):
        files.append(str(path))
    return "\n".join(files) if files else "No generated voice JSON files yet."


with gr.Blocks(title="Supertonic Voice Builder") as demo:
    gr.Markdown("# Supertonic Voice Builder\nUpload a WAV file, train a Supertonic-3 style JSON, and save it to the shared voices folder.")

    with gr.Tab("Create Voice"):
        wav_file = gr.File(label="Source WAV", file_types=[".wav"])
        voice_name = gr.Textbox(label="Voice Name", value="my-voice")
        with gr.Row():
            reference_style = gr.Textbox(label="Reference Style", value="auto")
            num_steps = gr.Number(label="Steps", value=3000, precision=0)
            learning_rate = gr.Number(label="Learning Rate", value=0.0002)
        with gr.Row():
            seed = gr.Number(label="Seed", value=42, precision=0)
            speed = gr.Number(label="Speed", value=1.0)
            vocoder_steps = gr.Number(label="Vocoder Steps", value=5, precision=0)

        start_btn = gr.Button("Train Voice", variant="primary")
        start_status = gr.Textbox(label="Start Status")
        active_job_id = gr.Textbox(label="Job ID")

    with gr.Tab("Job Logs"):
        refresh_btn = gr.Button("Refresh Job")
        status_box = gr.Textbox(label="Status", lines=6)
        log_box = gr.Textbox(label="Log", lines=24, max_lines=40)
        result_file = gr.File(label="Generated JSON")

    with gr.Tab("Jobs"):
        jobs_btn = gr.Button("Refresh Jobs")
        jobs_table = gr.Dataframe(headers=["Job ID", "Voice", "Status", "Created"], datatype=["str", "str", "str", "str"])

    with gr.Tab("Voice Files"):
        voices_btn = gr.Button("Refresh Voice Files")
        voices_box = gr.Textbox(label="Voice JSON Files", lines=20)

    start_btn.click(
        start_job,
        inputs=[wav_file, voice_name, reference_style, num_steps, learning_rate, seed, speed, vocoder_steps],
        outputs=[start_status, active_job_id, result_file],
    )
    refresh_btn.click(refresh_job, inputs=[active_job_id], outputs=[status_box, log_box, result_file])
    jobs_btn.click(list_jobs, outputs=[jobs_table])
    voices_btn.click(list_voice_files, outputs=[voices_box])

if __name__ == "__main__":
    demo.queue().launch(server_name="0.0.0.0", server_port=7790, show_api=False)
