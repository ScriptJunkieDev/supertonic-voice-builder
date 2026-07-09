package ai.nxly.voicebuilder.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "voice-builder")
public class VoiceBuilderProperties {
    private String dataDir;
    private String outputDir;
    private String pythonBin;
    private String trainerDir;
    private String workerScript;
    private String trainerGitUrl;
    private String trainerArchiveUrl;
    private String trainerBackupDir;
    private boolean trainerBootstrapEnabled = true;
    private int defaultSteps;
    private double defaultLearningRate;
    private int maxConcurrentJobs;

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getPythonBin() { return pythonBin; }
    public void setPythonBin(String pythonBin) { this.pythonBin = pythonBin; }
    public String getTrainerDir() { return trainerDir; }
    public void setTrainerDir(String trainerDir) { this.trainerDir = trainerDir; }
    public String getWorkerScript() { return workerScript; }
    public void setWorkerScript(String workerScript) { this.workerScript = workerScript; }
    public String getTrainerGitUrl() { return trainerGitUrl; }
    public void setTrainerGitUrl(String trainerGitUrl) { this.trainerGitUrl = trainerGitUrl; }
    public String getTrainerArchiveUrl() { return trainerArchiveUrl; }
    public void setTrainerArchiveUrl(String trainerArchiveUrl) { this.trainerArchiveUrl = trainerArchiveUrl; }
    public String getTrainerBackupDir() { return trainerBackupDir; }
    public void setTrainerBackupDir(String trainerBackupDir) { this.trainerBackupDir = trainerBackupDir; }
    public boolean isTrainerBootstrapEnabled() { return trainerBootstrapEnabled; }
    public void setTrainerBootstrapEnabled(boolean trainerBootstrapEnabled) { this.trainerBootstrapEnabled = trainerBootstrapEnabled; }
    public int getDefaultSteps() { return defaultSteps; }
    public void setDefaultSteps(int defaultSteps) { this.defaultSteps = defaultSteps; }
    public double getDefaultLearningRate() { return defaultLearningRate; }
    public void setDefaultLearningRate(double defaultLearningRate) { this.defaultLearningRate = defaultLearningRate; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int maxConcurrentJobs) { this.maxConcurrentJobs = maxConcurrentJobs; }

    @PostConstruct
    void normalizeConfiguredPaths() {
        Path root = VoiceBuilderPaths.appRoot();
        dataDir = VoiceBuilderPaths.resolveDirectory(root, dataDir);
        outputDir = VoiceBuilderPaths.resolveDirectory(root, outputDir);
        workerScript = VoiceBuilderPaths.resolveDirectory(root, workerScript);
        trainerDir = VoiceBuilderPaths.resolveDirectory(root, trainerDir);
        trainerBackupDir = VoiceBuilderPaths.resolveDirectory(root, trainerBackupDir);
        Path configuredPython = Path.of(pythonBin != null ? pythonBin : "");
        if (!configuredPython.isAbsolute()) {
            configuredPython = root.resolve(pythonBin != null ? pythonBin : "python3").normalize();
        }
        if (Files.isExecutable(configuredPython)) {
            pythonBin = configuredPython.toString();
        } else {
            Path eggVenv = root.resolve("venv/bin/python3").normalize();
            if (Files.isExecutable(eggVenv)) {
                pythonBin = eggVenv.toString();
            } else {
                pythonBin = VoiceBuilderPaths.resolvePythonBin(pythonBin);
            }
        }
    }

    public boolean isPythonAvailable() {
        return VoiceBuilderPaths.isRunnableExecutable(pythonBin);
    }

    public boolean isWorkerScriptPresent() {
        return Files.isRegularFile(Path.of(workerScript));
    }

    public boolean isTrainerPresent() {
        return Files.isRegularFile(Path.of(trainerDir, "train_style.py"));
    }
}
