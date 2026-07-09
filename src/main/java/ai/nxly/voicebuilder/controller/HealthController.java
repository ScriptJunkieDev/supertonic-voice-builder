package ai.nxly.voicebuilder.controller;

import ai.nxly.voicebuilder.config.DiskDiagnostics;
import ai.nxly.voicebuilder.config.VoiceBuilderPaths;
import ai.nxly.voicebuilder.config.VoiceBuilderProperties;
import ai.nxly.voicebuilder.service.TrainerBootstrapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final VoiceBuilderProperties props;
    private final TrainerBootstrapService trainerBootstrap;

    public HealthController(VoiceBuilderProperties props, TrainerBootstrapService trainerBootstrap) {
        this.props = props;
        this.trainerBootstrap = trainerBootstrap;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        boolean ready = props.isPythonAvailable()
                && props.isWorkerScriptPresent()
                && trainerBootstrap.isTrainerReady();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ready ? "ok" : "degraded");
        body.put("mode", "cpu-only");
        body.put("pythonBin", props.getPythonBin());
        body.put("pythonAvailable", props.isPythonAvailable());
        body.put("workerScript", props.getWorkerScript());
        body.put("workerPresent", props.isWorkerScriptPresent());
        body.put("trainerDir", props.getTrainerDir());
        body.put("trainerPresent", trainerBootstrap.isTrainerReady());
        body.put("trainerBootstrap", trainerBootstrap.getStatus());
        body.put("trainerBootstrapDetail", trainerBootstrap.getDetail());
        body.put("trainerGitUrl", props.getTrainerGitUrl());
        body.put("trainerArchiveUrl", props.getTrainerArchiveUrl());
        body.put("trainerBackupDir", props.getTrainerBackupDir());
        body.put("trainerVenvDir", props.getTrainerVenvDir());
        body.put("diskDiagnostics", trainerBootstrap.getLastDiskDiagnostics().isBlank()
                ? DiskDiagnostics.summarize(
                        VoiceBuilderPaths.appRoot(),
                        java.nio.file.Path.of(props.getDataDir()),
                        java.nio.file.Path.of(props.getTrainerVenvDir()),
                        java.nio.file.Path.of("/tmp"))
                : trainerBootstrap.getLastDiskDiagnostics());
        try {
            body.put("diskFreeContainerMiB", VoiceBuilderPaths.usableBytes(VoiceBuilderPaths.appRoot()) / 1024 / 1024);
            body.put("diskFreeDataMiB", VoiceBuilderPaths.usableBytes(java.nio.file.Path.of(props.getDataDir())) / 1024 / 1024);
        } catch (Exception ignored) {
            body.put("diskFreeContainerMiB", -1);
            body.put("diskFreeDataMiB", -1);
        }
        return body;
    }
}
