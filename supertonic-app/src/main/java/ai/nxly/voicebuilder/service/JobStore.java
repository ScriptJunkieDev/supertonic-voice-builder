package ai.nxly.voicebuilder.service;

import ai.nxly.voicebuilder.config.VoiceBuilderProperties;
import ai.nxly.voicebuilder.model.TrainingJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobStore {
    private final VoiceBuilderProperties props;
    private final ObjectMapper mapper;
    private final Map<String, TrainingJob> jobs = new ConcurrentHashMap<>();

    public JobStore(VoiceBuilderProperties props) {
        this.props = props;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Path.of(props.getDataDir(), "jobs"));
        Files.createDirectories(Path.of(props.getDataDir(), "uploads"));
        Files.createDirectories(Path.of(props.getOutputDir()));
        loadExistingJobs();
    }

    public List<TrainingJob> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(TrainingJob::getCreatedAt).reversed())
                .toList();
    }

    public Optional<TrainingJob> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public void save(TrainingJob job) {
        jobs.put(job.getId(), job);
        try {
            Path path = Path.of(job.getJobDir(), "job.json");
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), job);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save job metadata", e);
        }
    }

    private void loadExistingJobs() throws IOException {
        Path jobsDir = Path.of(props.getDataDir(), "jobs");
        if (!Files.exists(jobsDir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jobsDir)) {
            for (Path dir : stream) {
                Path jobFile = dir.resolve("job.json");
                if (Files.exists(jobFile)) {
                    TrainingJob job = mapper.readValue(jobFile.toFile(), TrainingJob.class);
                    jobs.put(job.getId(), job);
                }
            }
        }
    }
}
