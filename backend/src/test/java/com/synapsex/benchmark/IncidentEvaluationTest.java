package com.synapsex.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEvaluationTest {

    @Test
    @DisplayName("Incident Replay Evaluation: Multi-Agent investigation achieves >= 30% speedup over baseline")
    void testRootCauseIdentificationSpeedupAndCorrectness() {
        UUID workspaceId = UUID.randomUUID();
        List<IncidentEvaluationHarness.LabeledIncident> dataset =
                IncidentEvaluationHarness.generateLabeledBenchmarkDataset(workspaceId);

        // Verify dataset has at least 20 labeled incidents
        assertTrue(dataset.size() >= 20, "Evaluation dataset must contain at least 20 labeled incidents");

        IncidentEvaluationHarness.EvaluationSummary summary =
                IncidentEvaluationHarness.evaluateDataset(dataset);

        System.out.println(summary.formatReport());

        // Verify Target 3: >= 30% reduction in root cause identification time
        assertTrue(summary.speedupPercent() >= 30.0,
                String.format("Expected MTTRC speedup >= 30.0%%, but achieved %.2f%%", summary.speedupPercent()));

        // Verify Diagnostic Correctness: Accuracy >= 90%
        assertTrue(summary.optimizedAccuracyPercent() >= 90.0,
                String.format("Expected diagnostic accuracy >= 90.0%%, but achieved %.1f%%", summary.optimizedAccuracyPercent()));

        // Verify Precision and Recall
        assertTrue(summary.precision() >= 0.9, "Precision must be >= 0.90");
        assertTrue(summary.recall() >= 0.9, "Recall must be >= 0.90");
    }
}
