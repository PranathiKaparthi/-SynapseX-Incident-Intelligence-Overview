package com.synapsex.simulation.controller;

import com.synapsex.auth.security.UserPrincipal;
import com.synapsex.simulation.model.SimulationScenario;
import com.synapsex.simulation.service.TelemetrySimulatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final TelemetrySimulatorService simulatorService;

    public SimulationController(TelemetrySimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<String>> listScenarios() {
        return ResponseEntity.ok(Arrays.stream(SimulationScenario.values()).map(Enum::name).toList());
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startSimulation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {

        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        String scenarioStr = (String) body.getOrDefault("scenario", "DATABASE_CONNECTION_POOL_EXHAUSTION");
        SimulationScenario scenario = SimulationScenario.valueOf(scenarioStr);
        int duration = body.containsKey("durationSeconds") ? ((Number) body.get("durationSeconds")).intValue() : 60;
        long seed = body.containsKey("seed") ? ((Number) body.get("seed")).longValue() : 42L;

        return ResponseEntity.ok(simulatorService.startSimulation(workspaceId, scenario, duration, seed));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopSimulation(@AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(simulatorService.stopSimulation(workspaceId));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        UUID workspaceId = principal != null && principal.getWorkspaceId() != null
                ? principal.getWorkspaceId()
                : UUID.fromString("00000000-0000-0000-0000-000000000001");

        return ResponseEntity.ok(simulatorService.getStatus(workspaceId));
    }
}
