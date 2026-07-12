package ai.nxly.voicebuilder.service;

import ai.nxly.voicebuilder.config.VoiceBuilderPaths;
import ai.nxly.voicebuilder.config.VoiceBuilderProperties;
import ai.nxly.voicebuilder.model.JobStatus;
import ai.nxly.voicebuilder.model.TrainingJob;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrainingService {
    private static final Pattern SAFE_NAME = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final Pattern TRAINER_CHECKPOINT_STEP = Pattern.compile(".*_(\\d{4})\\.json$");

    private final VoiceBuilderProperties props;
    private final JobStore store;
    private final TrainerBootstrapService trainerBootstrap;
    private final ExecutorService executor;
    private final ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "training-progress");
        t.setDaemon(true);
        return t;
    });

    public TrainingService(VoiceBuilderProperties props, JobStore store, TrainerBootstrapService trainerBootstrap) {
        this.props = props;
        this.store = store;
        this.trainerBootstrap = trainerBootstrap;
        this.executor = Executors.newFixedThreadPool(Math.max(1, props.getMaxConcurrentJobs()));
    }

    public TrainingJob createJob(MultipartFile wavFile, String voiceName, Integer steps, Double learningRate, String referenceStyle) throws IOException {
        String cleanVoiceName = sanitizeVoiceName(voiceName);
        String id = UUID.randomUUID().toString();
        Path jobDir = Path.of(props.getDataDir(), "jobs", id);
        Path uploadDir = Path.of(props.getDataDir(), "uploads", id);
        Files.createDirectories(jobDir);
        Files.createDirectories(uploadDir);

        String original = Optional.ofNullable(wavFile.getOriginalFilename()).orElse("voice.wav");
        Path uploadPath = uploadDir.resolve("source.wav");
        wavFile.transferTo(uploadPath);

        TrainingJob job = new TrainingJob();
        job.setId(id);
        job.setVoiceName(cleanVoiceName);
        job.setOriginalFilename(original);
        job.setStatus(JobStatus.QUEUED);
        job.setSteps(steps != null && steps > 0 ? steps : props.getDefaultSteps());
        job.setLearningRate(learningRate != null && learningRate > 0 ? learningRate : props.getDefaultLearningRate());
        job.setReferenceStyle(referenceStyle == null || referenceStyle.isBlank() ? "auto" : referenceStyle.trim());
        job.setUploadPath(uploadPath.toString());
        job.setJobDir(jobDir.toString());
        job.setLogPath(jobDir.resolve("train.log").toString());
        job.setCreatedAt(Instant.now());
        store.save(job);

        executor.submit(() -> runJob(job.getId()));
        return job;
    }

    private void runJob(String jobId) {
        TrainingJob job = store.get(jobId).orElseThrow();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        store.save(job);

        Path log = Path.of(job.getLogPath());
        try {
            String startLine = "Starting CPU-only Supertonic voice training job " + job.getId() + System.lineSeparator();
            Files.writeString(log, startLine, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            echoConsole(startLine);

            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPythonBin());
            cmd.add("-u");
            cmd.add(props.getWorkerScript());
            cmd.add("--trainer-dir");
            cmd.add(props.getTrainerDir());
            cmd.add("--name");
            cmd.add(job.getVoiceName());
            cmd.add("--target-wav-path");
            cmd.add(job.getUploadPath());
            cmd.add("--num-steps");
            cmd.add(String.valueOf(job.getSteps()));
            cmd.add("--learning-rate");
            cmd.add(String.valueOf(job.getLearningRate()));
            cmd.add("--reference-style");
            cmd.add(job.getReferenceStyle());
            cmd.add("--job-dir");
            cmd.add(job.getJobDir());
            cmd.add("--output-dir");
            cmd.add(props.getOutputDir());

            if (!props.isPythonAvailable()) {
                throw new IOException(
                        "Python was not found (PYTHON_BIN=" + props.getPythonBin()
                                + "). Reinstall the server egg to create ./venv, or set PYTHON_BIN to ./venv/bin/python3.");
            }
            if (!props.isWorkerScriptPresent()) {
                throw new IOException("Worker script not found: " + props.getWorkerScript());
            }

            append(log, "Ensuring trainer is provisioned under TRAINER_DIR=" + props.getTrainerDir() + System.lineSeparator());
            try {
                trainerBootstrap.prepareTrainer();
            } catch (Exception e) {
                throw new IOException(
                        "Trainer bootstrap failed: " + e.getMessage()
                                + " (see /api/health trainerBootstrapDetail)", e);
            }
            if (!props.isTrainerPresent()) {
                throw new IOException(
                        "train_style.py still missing under TRAINER_DIR=" + props.getTrainerDir()
                                + " after bootstrap. Detail: " + trainerBootstrap.getDetail());
            }
            if (!trainerBootstrap.isTrainerReady()) {
                throw new IOException(
                        "Supertonic ONNX weights missing under "
                                + props.getTrainerDir() + "/" + props.getTrainerHfLocalDir()
                                + "/onnx. Bootstrap detail: " + trainerBootstrap.getDetail());
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(VoiceBuilderPaths.appRoot().toFile());
            pb.environment().put("CUDA_VISIBLE_DEVICES", "");
            String threads = String.valueOf(Math.max(1, props.getTrainingCpuThreads()));
            pb.environment().put("TRAINING_CPU_THREADS", threads);
            pb.environment().put("OMP_NUM_THREADS", System.getenv().getOrDefault("OMP_NUM_THREADS", threads));
            pb.environment().put("MKL_NUM_THREADS", System.getenv().getOrDefault("MKL_NUM_THREADS", threads));
            pb.environment().put("OPENBLAS_NUM_THREADS", System.getenv().getOrDefault("OPENBLAS_NUM_THREADS", threads));
            pb.environment().put("TORCH_NUM_THREADS", System.getenv().getOrDefault("TORCH_NUM_THREADS", threads));
            pb.environment().put("PYTHONUNBUFFERED", "1");

            append(log, "Command: " + String.join(" ", cmd) + System.lineSeparator());
            Instant trainStart = Instant.now();
            Process process = pb.start();
            int progressSec = Math.max(60, props.getTrainingProgressIntervalSeconds());
            ScheduledFuture<?> heartbeat = progressScheduler.scheduleAtFixedRate(() -> {
                if (!process.isAlive()) {
                    return;
                }
                try {
                    String hint = trainerCheckpointHint(job);
                    append(log, "[progress] " + formatElapsed(trainStart) + " — still running. " + hint + System.lineSeparator());
                } catch (IOException ignored) {
                }
            }, progressSec, progressSec, TimeUnit.SECONDS);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    append(log, line + System.lineSeparator());
                }
            } finally {
                heartbeat.cancel(false);
            }

            int exit = process.waitFor();
            if (exit != 0) {
                job.setStatus(JobStatus.FAILED);
                if (exit == 137 || exit == -9) {
                    String oom = "Training was killed (SIGKILL / OOM). Assign more container memory in the panel "
                            + "(6–8 GB+ recommended with Java), cap JVM heap (JVM_HEAP_MB≈1024), "
                            + "or lower TRAINING_CPU_THREADS.";
                    job.setErrorMessage(oom);
                    append(log, oom + System.lineSeparator());
                } else {
                    job.setErrorMessage("Training process exited with code " + exit);
                }
            } else {
                Path expected = Path.of(props.getOutputDir(), job.getVoiceName() + ".json");
                job.setOutputJsonPath(expected.toString());
                job.setStatus(Files.exists(expected) ? JobStatus.SUCCESS : JobStatus.FAILED);
                if (!Files.exists(expected)) {
                    job.setErrorMessage("Training completed but no output JSON was found at " + expected);
                }
            }
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            try { append(log, "ERROR: " + e + System.lineSeparator()); } catch (IOException ignored) {}
        } finally {
            job.setFinishedAt(Instant.now());
            store.save(job);
        }
    }

    public String readLog(String id) throws IOException {
        TrainingJob job = store.get(id).orElseThrow(() -> new NoSuchElementException("Job not found"));
        Path log = Path.of(job.getLogPath());
        if (!Files.exists(log)) return "";
        return Files.readString(log);
    }

    public Path outputPath(String id) {
        TrainingJob job = store.get(id).orElseThrow(() -> new NoSuchElementException("Job not found"));
        if (job.getOutputJsonPath() == null) throw new NoSuchElementException("No output JSON available yet");
        return Path.of(job.getOutputJsonPath());
    }

    private static void append(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        echoConsole(text);
    }

    private static void echoConsole(String text) {
        System.out.print(text);
        System.out.flush();
    }

    private static String sanitizeVoiceName(String name) {
        String value = name == null || name.isBlank() ? "custom_voice" : name.trim();
        value = value.replace(' ', '_').toLowerCase(Locale.ROOT);
        value = SAFE_NAME.matcher(value).replaceAll("");
        if (value.isBlank()) value = "custom_voice";
        return value;
    }

    private String trainerCheckpointHint(TrainingJob job) throws IOException {
        Path logDir = Path.of(props.getTrainerDir(), "logs", job.getVoiceName());
        if (!Files.isDirectory(logDir)) {
            return "No checkpoints yet (model load / reference-style search can take many minutes on CPU).";
        }
        int maxStep = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(logDir, "*.json")) {
            for (Path entry : entries) {
                Matcher matcher = TRAINER_CHECKPOINT_STEP.matcher(entry.getFileName().toString());
                if (matcher.matches()) {
                    maxStep = Math.max(maxStep, Integer.parseInt(matcher.group(1)));
                }
            }
        }
        if (maxStep > 0) {
            return "Latest checkpoint ~step " + maxStep + " of " + job.getSteps() + " (saved every 500 steps).";
        }
        return "In optimization loop (trainer prints loss every 8 steps when stdout is unbuffered).";
    }

    private static String formatElapsed(Instant start) {
        long sec = Duration.between(start, Instant.now()).getSeconds();
        long hours = sec / 3600;
        long minutes = (sec % 3600) / 60;
        long seconds = sec % 60;
        if (hours > 0) {
            return String.format("elapsed %dh %dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("elapsed %dm %ds", minutes, seconds);
        }
        return "elapsed " + seconds + "s";
    }
}
