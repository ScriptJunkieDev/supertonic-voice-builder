package ai.nxly.voicebuilder.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VoiceBuilderPaths {

    private VoiceBuilderPaths() {}

    public static Path appRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    static String resolveDirectory(Path root, String configured) {
        if (configured == null || configured.isBlank()) {
            return root.toString();
        }
        Path path = Path.of(configured);
        if (!path.isAbsolute()) {
            path = root.resolve(path);
        }
        return path.normalize().toString();
    }

    static String resolvePythonBin(String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured.trim());
        }
        candidates.add("python3");
        candidates.add("python");

        for (String candidate : candidates) {
            Optional<String> resolved = executablePath(candidate);
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        return configured != null && !configured.isBlank() ? configured.trim() : "python3";
    }

    static Optional<String> executablePath(String name) {
        Path direct = Path.of(name);
        if (direct.isAbsolute()) {
            return Files.isExecutable(direct) ? Optional.of(direct.toString()) : Optional.empty();
        }
        if (name.contains("/") || name.contains("\\")) {
            Path relative = appRoot().resolve(name).normalize();
            return Files.isExecutable(relative) ? Optional.of(relative.toString()) : Optional.empty();
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate.toString());
            }
        }
        return Optional.empty();
    }

    public static boolean isRunnableExecutable(String bin) {
        return executablePath(bin).isPresent();
    }
}
