package dev.reclaim.web;

import dev.reclaim.audit.AuditLedger;
import dev.reclaim.domain.*;
import dev.reclaim.policy.PolicyEngine;
import dev.reclaim.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.*;

@Controller
public class DashboardController {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AgentDecisionRepository agentDecisionRepository;
    private final PolicyVerdictRepository policyVerdictRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final PolicyEngine policyEngine;
    private final AuditLedger auditLedger;

    public DashboardController(
            RecoveryCaseRepository recoveryCaseRepository,
            RecoveryActionRepository recoveryActionRepository,
            AgentDecisionRepository agentDecisionRepository,
            PolicyVerdictRepository policyVerdictRepository,
            AuditEntryRepository auditEntryRepository,
            PolicyEngine policyEngine,
            AuditLedger auditLedger) {
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.agentDecisionRepository = agentDecisionRepository;
        this.policyVerdictRepository = policyVerdictRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.policyEngine = policyEngine;
        this.auditLedger = auditLedger;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<RecoveryCase> cases = recoveryCaseRepository.findAll();
        long totalAtRiskPaise = cases.stream().mapToLong(RecoveryCase::getAmountPaise).sum();
        long totalRecoveredPaise = cases.stream().mapToLong(RecoveryCase::getRecoveredPaise).sum();
        long totalCostPaise = cases.stream().mapToLong(RecoveryCase::getCostIncurredPaise).sum();
        long netRecoveredPaise = totalRecoveredPaise - totalCostPaise;

        long recoveredCount = cases.stream().filter(c -> c.getState() == CaseState.RECOVERED).count();
        long activeCount = cases.stream().filter(c -> !c.getState().isTerminal()).count();

        AuditLedger.AuditVerifyResponse verifyResult = auditLedger.verifyChain();

        model.addAttribute("cases", cases);
        model.addAttribute("totalCases", cases.size());
        model.addAttribute("totalAtRiskRupees", totalAtRiskPaise / 100.0);
        model.addAttribute("totalRecoveredRupees", totalRecoveredPaise / 100.0);
        model.addAttribute("totalCostRupees", totalCostPaise / 100.0);
        model.addAttribute("netRecoveredRupees", netRecoveredPaise / 100.0);
        model.addAttribute("recoveryRate", cases.isEmpty() ? 0.0 : (recoveredCount * 100.0 / cases.size()));
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("isHalted", policyEngine.isHalted());
        model.addAttribute("auditVerification", verifyResult);

        return "dashboard";
    }

    @GetMapping("/cases/{id}")
    public String caseDetail(@PathVariable UUID id, Model model) {
        Optional<RecoveryCase> caseOpt = recoveryCaseRepository.findById(id);
        if (caseOpt.isEmpty()) {
            return "redirect:/";
        }

        RecoveryCase rc = caseOpt.get();
        List<AgentDecision> decisions = agentDecisionRepository.findByCaseIdOrderByCreatedAtAsc(id);
        List<PolicyVerdict> verdicts = policyVerdictRepository.findByCaseIdOrderByCreatedAtAsc(id);
        List<RecoveryAction> actions = recoveryActionRepository.findByCaseIdOrderByScheduledForAsc(id);
        List<AuditEntry> auditEntries = auditEntryRepository.findByCaseIdOrderBySeqAsc(id);

        model.addAttribute("case", rc);
        model.addAttribute("decisions", decisions);
        model.addAttribute("verdicts", verdicts);
        model.addAttribute("actions", actions);
        model.addAttribute("auditEntries", auditEntries);

        return "case-detail";
    }
}
