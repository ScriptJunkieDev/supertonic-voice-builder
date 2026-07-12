package ai.nxly.voicebuilder.controller;

import ai.nxly.voicebuilder.model.TrainingJob;
import ai.nxly.voicebuilder.service.JobStore;
import ai.nxly.voicebuilder.service.TrainingService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class TrainingController {
    private final TrainingService trainingService;
    private final JobStore jobStore;

    public TrainingController(TrainingService trainingService, JobStore jobStore) {
        this.trainingService = trainingService;
        this.jobStore = jobStore;
    }

    @GetMapping
    public List<TrainingJob> list() {
        return jobStore.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrainingJob> get(@PathVariable String id) {
        return jobStore.get(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrainingJob create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("voiceName") String voiceName,
            @RequestParam(value = "steps", required = false) Integer steps,
            @RequestParam(value = "learningRate", required = false) Double learningRate,
            @RequestParam(value = "referenceStyle", required = false) String referenceStyle
    ) throws IOException {
        return trainingService.createJob(file, voiceName, steps, learningRate, referenceStyle);
    }

    @GetMapping(value = "/{id}/log", produces = MediaType.TEXT_PLAIN_VALUE)
    public String log(@PathVariable String id) throws IOException {
        return trainingService.readLog(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
        Path path = trainingService.outputPath(id);
        FileSystemResource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }
}
