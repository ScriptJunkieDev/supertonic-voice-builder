package ai.nxly.voicebuilder.model;

import java.time.Instant;

public class TrainingJob {
    private String id;
    private String voiceName;
    private String originalFilename;
    private JobStatus status;
    private int steps;
    private double learningRate;
    private String referenceStyle;
    private String uploadPath;
    private String jobDir;
    private String logPath;
    private String outputJsonPath;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVoiceName() { return voiceName; }
    public void setVoiceName(String voiceName) { this.voiceName = voiceName; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getSteps() { return steps; }
    public void setSteps(int steps) { this.steps = steps; }
    public double getLearningRate() { return learningRate; }
    public void setLearningRate(double learningRate) { this.learningRate = learningRate; }
    public String getReferenceStyle() { return referenceStyle; }
    public void setReferenceStyle(String referenceStyle) { this.referenceStyle = referenceStyle; }
    public String getUploadPath() { return uploadPath; }
    public void setUploadPath(String uploadPath) { this.uploadPath = uploadPath; }
    public String getJobDir() { return jobDir; }
    public void setJobDir(String jobDir) { this.jobDir = jobDir; }
    public String getLogPath() { return logPath; }
    public void setLogPath(String logPath) { this.logPath = logPath; }
    public String getOutputJsonPath() { return outputJsonPath; }
    public void setOutputJsonPath(String outputJsonPath) { this.outputJsonPath = outputJsonPath; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
