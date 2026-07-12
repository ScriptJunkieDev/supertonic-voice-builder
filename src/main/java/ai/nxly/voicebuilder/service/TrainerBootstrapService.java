package ai.nxly.voicebuilder.service;

import ai.nxly.voicebuilder.config.VoiceBuilderPaths;
import ai.nxly.voicebuilder.config.VoiceBuilderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class TrainerBootstrapService {
    private static final Logger log = LoggerFactory.getLogger(TrainerBootstrapService.class);

    private final VoiceBuilderProperties props;
    private final AtomicReference<String> status = new AtomicReference<>("pending");
    private final AtomicReference<String> detail = new AtomicReference<>("");

    public TrainerBootstrapService(VoiceBuilderProperties props) {
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.isTrainerBootstrapEnabled()) {
            status.set("disabled");
            detail.set("Trainer bootstrap is disabled");
            return;
        }
        try {
            prepareTrainer();
        } catch (Exception e) {
            log.error("Trainer bootstrap failed at startup", e);
            status.set("failed");
            detail.set(e.getMessage());
        }
    }

    public synchronized void prepareTrainer() throws IOException, InterruptedException {
        if (!props.isTrainerBootstrapEnabled()) {
            return;
        }
        try {
            ensureTrainerSourcesReady();
            ensureSupertonicModelWeights();
            status.set("ready");
            detail.set("train_style.py and Supertonic ONNX weights are available under " + props.getTrainerDir());
        } catch (IOException | InterruptedException e) {
            status.set("failed");
            detail.set(e.getMessage());
            throw e;
        }
    }

    public String getStatus() {
        return status.get();
    }

    public String getDetail() {
        return detail.get();
    }

    public boolean isTrainerReady() {
        return props.isTrainerPresent() && (!props.isTrainerModelBootstrapEnabled() || props.isTrainerModelsPresent());
    }

    private void ensureSupertonicModelWeights() throws IOException, InterruptedException {
        if (!props.isTrainerModelBootstrapEnabled()) {
            return;
        }
        Path trainerDir = Path.of(props.getTrainerDir());
        Path marker = trainerDir.resolve(props.getTrainerHfLocalDir()).resolve("onnx").resolve("duration_predictor.onnx");
        if (Files.isRegularFile(marker)) {
            log.info("Supertonic ONNX weights already present at {}", marker);
            return;
        }
        if (!props.isPythonAvailable()) {
            throw new IOException("Python is required to download Supertonic weights (HF_TOKEN optional for rate limits)");
        }
        Path workerScript = Path.of(props.getWorkerScript());
        Path downloadScript = workerScript.getParent().resolve("download_supertonic_models.py");
        if (!Files.isRegularFile(downloadScript)) {
            throw new IOException("Missing worker script: " + downloadScript);
        }
        String repo = props.getTrainerHfModel();
        if (repo == null || repo.isBlank()) {
            throw new IOException("TRAINER_HF_MODEL is not configured");
        }
        log.info("Downloading Supertonic weights {} into {}/{}", repo, trainerDir, props.getTrainerHfLocalDir());
        List<String> cmd = List.of(
                props.getPythonBin(),
                downloadScript.toString(),
                "--trainer-dir", trainerDir.toString(),
                "--repo", repo.trim(),
                "--local-dir-name", props.getTrainerHfLocalDir()
        );
        runProcess(cmd, VoiceBuilderPaths.appRoot());
        if (!Files.isRegularFile(marker)) {
            throw new IOException("Supertonic weight download completed but marker file is still missing: " + marker);
        }
    }

    private void ensureTrainerSourcesReady() throws IOException, InterruptedException {
        Path trainerDir = Path.of(props.getTrainerDir());
        Path trainScript = trainerDir.resolve("train_style.py");
        if (Files.isRegularFile(trainScript)) {
            log.info("Trainer already present at {}", trainScript);
            return;
        }

        Files.createDirectories(trainerDir.getParent() != null ? trainerDir.getParent() : trainerDir);
        List<String> attempts = new ArrayList<>();

        String gitUrl = props.getTrainerGitUrl();
        if (gitUrl != null && !gitUrl.isBlank()) {
            try {
                log.info("Cloning trainer from {}", gitUrl);
                cloneTrainerRepo(gitUrl.trim(), trainerDir);
            } catch (IOException | InterruptedException e) {
                attempts.add("git clone: " + e.getMessage());
                log.warn("Git clone failed: {}", e.getMessage());
            }
        }

        if (!Files.isRegularFile(trainScript)) {
            String archiveUrl = props.getTrainerArchiveUrl();
            if (archiveUrl != null && !archiveUrl.isBlank()) {
                try {
                    log.info("Downloading trainer archive from {}", archiveUrl);
                    downloadAndExtractArchive(archiveUrl.trim(), trainerDir);
                } catch (IOException e) {
                    attempts.add("archive download: " + e.getMessage());
                    log.warn("Archive download failed: {}", e.getMessage());
                }
            }
        }

        if (!Files.isRegularFile(trainScript)) {
            Path backupRoot = Path.of(props.getTrainerBackupDir());
            Path backupScript = backupRoot.resolve("train_style.py");
            if (Files.isRegularFile(backupScript)) {
                log.info("Restoring trainer from backup {}", backupRoot);
                if (Files.exists(trainerDir)) {
                    deleteRecursive(trainerDir);
                }
                copyTree(backupRoot, trainerDir);
            } else {
                attempts.add("backup missing at " + backupScript);
            }
        }

        if (!Files.isRegularFile(trainScript)) {
            throw new IOException(
                    "Could not provision trainer sources at " + trainScript + ". Attempts: " + String.join("; ", attempts)
                            + ". Python deps belong in ./venv from egg install; the app only fetches trainer sources.");
        }
    }

    private void cloneTrainerRepo(String url, Path trainerDir) throws IOException, InterruptedException {
        if (!VoiceBuilderPaths.isRunnableExecutable("git")) {
            throw new IOException("git is not on PATH");
        }
        if (Files.exists(trainerDir)) {
            deleteRecursive(trainerDir);
        }
        List<String> cmd = List.of("git", "clone", "--depth", "1", url, trainerDir.toString());
        runProcess(cmd, VoiceBuilderPaths.appRoot());
    }

    private void downloadAndExtractArchive(String url, Path trainerDir) throws IOException {
        Path tmpDir = Path.of(props.getDataDir(), "tmp");
        Files.createDirectories(tmpDir);
        Path tempZip = tmpDir.resolve("trainer-archive-" + UUID.randomUUID() + ".zip");
        Path staging = tmpDir.resolve("trainer-staging-" + UUID.randomUUID());
        try {
            try (InputStream in = URI.create(url).toURL().openStream()) {
                Files.copy(in, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }
            unzipToDirectory(tempZip, staging);
            Path sourceRoot = resolveArchiveRoot(staging);
            if (!Files.isRegularFile(sourceRoot.resolve("train_style.py"))) {
                throw new IOException("Archive did not contain train_style.py under " + sourceRoot);
            }
            if (Files.exists(trainerDir)) {
                deleteRecursive(trainerDir);
            }
            if (trainerDir.getParent() != null) {
                Files.createDirectories(trainerDir.getParent());
            }
            copyTree(sourceRoot, trainerDir);
        } finally {
            Files.deleteIfExists(tempZip);
            deleteRecursive(staging);
        }
    }

    private static Path resolveArchiveRoot(Path staging) throws IOException {
        try (var entries = Files.list(staging)) {
            List<Path> children = entries.toList();
            if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                return children.get(0);
            }
        }
        return staging;
    }

    private static void unzipToDirectory(Path zip, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("Zip entry escapes staging dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static void runProcess(List<String> cmd, Path workDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append(System.lineSeparator());
                System.out.println(line);
            }
        }
        System.out.flush();
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException("Command failed (" + code + "): " + String.join(" ", cmd)
                    + System.lineSeparator() + out);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path rel = source.relativize(path);
                Path dest = target.resolve(rel);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
