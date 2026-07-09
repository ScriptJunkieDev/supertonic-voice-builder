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
    private String trainerRequirementsPath;
    private boolean trainerBootstrapEnabled = true;
    private boolean pipUserInstall = true;
    private String trainerVenvDir;
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
    public String getTrainerRequirementsPath() { return trainerRequirementsPath; }
    public void setTrainerRequirementsPath(String trainerRequirementsPath) { this.trainerRequirementsPath = trainerRequirementsPath; }
    public boolean isTrainerBootstrapEnabled() { return trainerBootstrapEnabled; }
    public void setTrainerBootstrapEnabled(boolean trainerBootstrapEnabled) { this.trainerBootstrapEnabled = trainerBootstrapEnabled; }
    public boolean isPipUserInstall() { return pipUserInstall; }
    public void setPipUserInstall(boolean pipUserInstall) { this.pipUserInstall = pipUserInstall; }
    public String getTrainerVenvDir() { return trainerVenvDir; }
    public void setTrainerVenvDir(String trainerVenvDir) { this.trainerVenvDir = trainerVenvDir; }
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
        trainerRequirementsPath = VoiceBuilderPaths.resolveDirectory(root, trainerRequirementsPath);
        trainerVenvDir = VoiceBuilderPaths.resolveDirectory(root, trainerVenvDir);
        pythonBin = VoiceBuilderPaths.resolvePythonBin(pythonBin);
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
