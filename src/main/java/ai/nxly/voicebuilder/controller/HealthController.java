package ai.nxly.voicebuilder.controller;

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
        body.put("trainerBackupDir", props.getTrainerBackupDir());
        return body;
    }
}
