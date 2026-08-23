package dev.reclaim.policy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PolicyEngine policyEngine;

    public AdminController(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    @PostMapping("/halt")
    public ResponseEntity<Map<String, Object>> haltExecution() {
        policyEngine.haltExecution();
        return ResponseEntity.ok(Map.of(
                "status", "HALTED",
                "message", "EMERGENCY KILL SWITCH ACTIVATED: All recovery executions are frozen instantly."
        ));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeExecution() {
        policyEngine.resumeExecution();
        return ResponseEntity.ok(Map.of(
                "status", "ACTIVE",
                "message", "Recovery operations resumed normally."
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "isHalted", policyEngine.isHalted(),
                "status", policyEngine.isHalted() ? "HALTED" : "ACTIVE"
        ));
    }
}
