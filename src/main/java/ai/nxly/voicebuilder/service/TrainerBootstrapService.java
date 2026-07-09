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
            ensureTrainerReady();
            status.set("ready");
            detail.set("train_style.py is available at " + props.getTrainerDir());
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
        return props.isTrainerPresent();
    }

    private void ensureTrainerReady() throws IOException, InterruptedException {
        Path trainerDir = Path.of(props.getTrainerDir());
        Path trainScript = trainerDir.resolve("train_style.py");
        if (Files.isRegularFile(trainScript)) {
            log.info("Trainer already present at {}", trainScript);
            installPythonRequirements(trainerDir);
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
                    "Could not provision trainer at " + trainScript + ". Attempts: " + String.join("; ", attempts)
                            + ". Set TRAINER_GIT_URL / TRAINER_ARCHIVE_URL or populate " + props.getTrainerBackupDir()
                            + " (see trainer-backup/README.md).");
        }

        installPythonRequirements(trainerDir);
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
        Path tempZip = Files.createTempFile("trainer-archive-", ".zip");
        Path staging = Files.createTempDirectory("trainer-staging-");
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
            Files.createDirectories(trainerDir.getParent());
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

    private void installPythonRequirements(Path trainerDir) throws IOException, InterruptedException {
        Path requirements = resolvePipRequirementsFile(trainerDir);
        if (!Files.isRegularFile(requirements) || !props.isPythonAvailable()) {
            log.warn("Skipping pip install; requirements file missing or Python unavailable ({})", requirements);
            return;
        }
        Path venvDir = Path.of(props.getTrainerVenvDir());
        Path venvPython = resolveVenvPython(venvDir);
        if (!Files.isExecutable(venvPython)) {
            log.info("Creating trainer venv at {} with {}", venvDir, props.getPythonBin());
            Files.createDirectories(venvDir.getParent() != null ? venvDir.getParent() : venvDir);
            runProcess(List.of(props.getPythonBin(), "-m", "venv", venvDir.toString()), VoiceBuilderPaths.appRoot());
            venvPython = resolveVenvPython(venvDir);
            if (!Files.isExecutable(venvPython)) {
                throw new IOException("Failed to create venv at " + venvDir);
            }
        }
        Path venvPip = venvDir.resolve(isWindows() ? "Scripts/pip.exe" : "bin/pip");
        if (!Files.isExecutable(venvPip)) {
            venvPip = venvDir.resolve(isWindows() ? "Scripts/pip" : "bin/pip3");
        }
        runProcess(List.of(venvPip.toString(), "install", "--upgrade", "pip", "setuptools", "wheel"), trainerDir);
        List<String> cmd = new ArrayList<>();
        cmd.add(venvPip.toString());
        cmd.add("install");
        cmd.add("-r");
        cmd.add(requirements.toString());
        log.info("Installing trainer Python requirements into venv {} from {}", venvDir, requirements);
        runProcess(cmd, trainerDir);
        props.setPythonBin(venvPython.toString());
        log.info("Training will use Python from venv: {}", venvPython);
    }

    private Path resolvePipRequirementsFile(Path trainerDir) {
        String configured = props.getTrainerRequirementsPath();
        if (configured != null && !configured.isBlank()) {
            Path candidate = Path.of(configured);
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }
        Path upstream = trainerDir.resolve("requirements.txt");
        return Files.isRegularFile(upstream) ? upstream : Path.of(configured != null ? configured : upstream.toString());
    }

    private static Path resolveVenvPython(Path venvDir) {
        if (isWindows()) {
            Path py = venvDir.resolve("Scripts/python.exe");
            return Files.isExecutable(py) ? py : venvDir.resolve("Scripts/python");
        }
        Path py3 = venvDir.resolve("bin/python3");
        return Files.isExecutable(py3) ? py3 : venvDir.resolve("bin/python");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
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
            }
        }
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
