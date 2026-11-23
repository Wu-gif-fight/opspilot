package com.opspilot.agent.service.knowledge.eval;

import java.util.List;

public record RagEndToEndCaseResult(
        String id,
        String question,
        List<String> expectedSources,
        String expectedIntent,
        String actualIntent,
        String expectedSeverityLevel,
        String actualSeverityLevel,
        List<String> retrievedSources,
        List<String> retrievedContexts,
        String referenceAnswer,
        String answer,
        boolean hit,
        double reciprocalRank,
        boolean passed,
        List<String> failures
) {
}
