package ai.nxly.voicebuilder.service;

import ai.nxly.voicebuilder.config.VoiceBuilderPaths;
import ai.nxly.voicebuilder.config.VoiceBuilderProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TrainerBootstrapService {
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
            ensureTrainerReady();
            status.set("ready");
            detail.set("train_style.py is available");
        } catch (Exception e) {
            status.set("failed");
            detail.set(e.getMessage());
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
            installPythonRequirements(trainerDir);
            return;
        }

        Files.createDirectories(trainerDir);

        String gitUrl = props.getTrainerGitUrl();
        if (gitUrl != null && !gitUrl.isBlank()) {
            try {
                cloneTrainerRepo(gitUrl.trim(), trainerDir);
            } catch (IOException e) {
                detail.set("Git clone failed: " + e.getMessage());
            }
        }

        if (!Files.isRegularFile(trainScript)) {
            Path backupRoot = Path.of(props.getTrainerBackupDir());
            Path backupScript = backupRoot.resolve("train_style.py");
            if (Files.isRegularFile(backupScript)) {
                copyTree(backupRoot, trainerDir);
            } else {
                throw new IOException(
                        "Trainer not found at " + trainScript
                                + ". Set TRAINER_GIT_URL, populate " + backupRoot
                                + " with a vendor copy of the trainer, or place train_style.py under TRAINER_DIR.");
            }
        }

        installPythonRequirements(trainerDir);
    }

    private void cloneTrainerRepo(String url, Path trainerDir) throws IOException, InterruptedException {
        if (!VoiceBuilderPaths.isRunnableExecutable("git")) {
            throw new IOException("git is not on PATH; cannot clone " + url);
        }
        Path parent = trainerDir.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(trainerDir)) {
            deleteRecursive(trainerDir);
        }
        List<String> cmd = List.of("git", "clone", "--depth", "1", url, trainerDir.toString());
        runProcess(cmd, VoiceBuilderPaths.appRoot());
    }

    private void installPythonRequirements(Path trainerDir) throws IOException, InterruptedException {
        Path requirements = trainerDir.resolve("requirements.txt");
        if (!Files.isRegularFile(requirements) || !props.isPythonAvailable()) {
            return;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getPythonBin());
        cmd.add("-m");
        cmd.add("pip");
        cmd.add("install");
        if (props.isPipUserInstall()) {
            cmd.add("--user");
        }
        cmd.add("-r");
        cmd.add(requirements.toString());
        runProcess(cmd, trainerDir);
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
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
