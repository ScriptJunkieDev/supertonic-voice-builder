package ai.nxly.voicebuilder.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DiskDiagnostics {

    private DiskDiagnostics() {}

    public static String summarize(Path... paths) {
        Set<String> targets = new LinkedHashSet<>();
        targets.add("/tmp");
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            Path abs = path.toAbsolutePath().normalize();
            targets.add(abs.toString());
            Path parent = abs.getParent();
            if (parent != null) {
                targets.add(parent.toString());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(runDf("-h", targets));
        sb.append(runDf("-i", targets));
        for (Path path : paths) {
            if (path == null) {
                continue;
            }
            try {
                long free = VoiceBuilderPaths.usableBytes(path);
                sb.append("Java usableBytes ").append(path.toAbsolutePath().normalize())
                        .append(": ").append(free / 1024 / 1024).append(" MiB").append(System.lineSeparator());
            } catch (IOException e) {
                sb.append("Java usableBytes ").append(path).append(": error ").append(e.getMessage())
                        .append(System.lineSeparator());
            }
        }
        return sb.toString().trim();
    }

    private static String runDf(String flag, Set<String> paths) {
        List<String> existing = new ArrayList<>();
        for (String p : paths) {
            if (Files.exists(Path.of(p))) {
                existing.add(p);
            }
        }
        if (existing.isEmpty()) {
            return "df " + flag + ": (no paths exist yet)" + System.lineSeparator();
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("df");
        cmd.add(flag);
        cmd.addAll(existing);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            out.append("--- df ").append(flag).append(" ---").append(System.lineSeparator());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append(System.lineSeparator());
                }
            }
            if (process.waitFor() != 0) {
                out.append("(df exit ").append(process.exitValue()).append(")").append(System.lineSeparator());
            }
            return out.toString();
        } catch (Exception e) {
            return "df " + flag + " failed: " + e.getMessage() + System.lineSeparator();
        }
    }
}
