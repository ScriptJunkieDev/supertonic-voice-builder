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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Service
public class TrainingService {
    private static final Pattern SAFE_NAME = Pattern.compile("[^a-zA-Z0-9._-]");

    private final VoiceBuilderProperties props;
    private final JobStore store;
    private final ExecutorService executor;

    public TrainingService(VoiceBuilderProperties props, JobStore store) {
        this.props = props;
        this.store = store;
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
            Files.writeString(log, "Starting CPU-only Supertonic voice training job " + job.getId() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPythonBin());
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
                                + "). Install Python 3 on the server or set PYTHON_BIN to a full path.");
            }
            if (!props.isWorkerScriptPresent()) {
                throw new IOException("Worker script not found: " + props.getWorkerScript());
            }
            if (!props.isTrainerPresent()) {
                throw new IOException(
                        "train_style.py not found under TRAINER_DIR=" + props.getTrainerDir()
                                + ". Run scripts/setup-trainer.sh on the server or set TRAINER_DIR.");
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(VoiceBuilderPaths.appRoot().toFile());
            pb.environment().put("CUDA_VISIBLE_DEVICES", "");
            pb.environment().put("OMP_NUM_THREADS", System.getenv().getOrDefault("OMP_NUM_THREADS", "16"));
            pb.environment().put("MKL_NUM_THREADS", System.getenv().getOrDefault("MKL_NUM_THREADS", "16"));

            append(log, "Command: " + String.join(" ", cmd) + System.lineSeparator());
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    append(log, line + System.lineSeparator());
                }
            }

            int exit = process.waitFor();
            if (exit != 0) {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage("Training process exited with code " + exit);
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
    }

    private static String sanitizeVoiceName(String name) {
        String value = name == null || name.isBlank() ? "custom_voice" : name.trim();
        value = value.replace(' ', '_').toLowerCase(Locale.ROOT);
        value = SAFE_NAME.matcher(value).replaceAll("");
        if (value.isBlank()) value = "custom_voice";
        return value;
    }
}
