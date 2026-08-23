package dev.reclaim.audit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLedger auditLedger;

    public AuditController(AuditLedger auditLedger) {
        this.auditLedger = auditLedger;
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditLedger.AuditVerifyResponse> verifyAuditLedger() {
        AuditLedger.AuditVerifyResponse response = auditLedger.verifyChain();
        return ResponseEntity.ok(response);
    }
}
