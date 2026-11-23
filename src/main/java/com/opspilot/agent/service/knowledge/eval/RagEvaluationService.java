package com.opspilot.agent.service.knowledge.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.IntentClassifier;
import com.opspilot.agent.service.IncidentAssessmentService;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.knowledge.KnowledgeService;
import com.opspilot.agent.service.knowledge.SearchResult;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class RagEvaluationService {

    private final KnowledgeService knowledgeService;
    private final AiClient aiClient;
    private final IntentClassifier intentClassifier;
    private final IncidentAssessmentService assessmentService;
    private final ObjectMapper objectMapper;
    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    public RagEvaluationService(
            KnowledgeService knowledgeService,
            AiClient aiClient,
            IntentClassifier intentClassifier,
            IncidentAssessmentService assessmentService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeService = knowledgeService;
        this.aiClient = aiClient;
        this.intentClassifier = intentClassifier;
        this.assessmentService = assessmentService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public RagEvalReport evaluate(String datasetLocation, int topK) {
        List<RagEvalCase> cases = loadDataset(datasetLocation);
        List<RagEndToEndCaseResult> results = cases.stream()
                .map(testCase -> buildRagasCase(testCase, topK))
                .toList();
        long passedCases = results.stream()
                .filter(RagEndToEndCaseResult::passed)
                .count();
        RagMetrics metrics = computeMetrics(results);
        return new RagEvalReport(
                Instant.now(),
                datasetLocation,
                topK,
                results.size(),
                passedCases,
                results.size() - passedCases,
                metrics,
                results);
    }

    public void writeReport(RagEvalReport report, String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(outputPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.writeValue(path.toFile(), report);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write RAGAS input report: " + outputPath, exception);
        }
    }

    public String formatSummary(RagEvalReport report) {
        long casesWithContexts = report.cases().stream()
                .filter(testCase -> !testCase.retrievedContexts().isEmpty())
                .count();
        RagMetrics metrics = report.metrics();
        return """
                RAGAS input report completed.
                dataset=%s
                cases=%d
                passed=%d
                failed=%d
                topK=%d
                casesWithRetrievedContexts=%d
                hitRate=%.4f
                mrr=%.4f
                intentAccuracy=%.4f
                severityAccuracy=%.4f
                highRiskRecall=%.4f
                output contains Java harness assertions; run eval/run-ragas-eval.py for optional RAGAS scores.
                """.formatted(
                report.dataset(),
                report.totalCases(),
                report.passedCases(),
                report.failedCases(),
                report.topK(),
                casesWithContexts,
                metrics.hitRate(),
                metrics.mrr(),
                metrics.intentAccuracy(),
                metrics.severityAccuracy(),
                metrics.highRiskRecall());
    }

    private RagMetrics computeMetrics(List<RagEndToEndCaseResult> results) {
        if (results.isEmpty()) {
            return new RagMetrics(0.0, 0.0, 0.0, 0.0, 0.0);
        }
        int total = results.size();
        long hits = results.stream().filter(RagEndToEndCaseResult::hit).count();
        double mrr = results.stream()
                .mapToDouble(RagEndToEndCaseResult::reciprocalRank)
                .average()
                .orElse(0.0);
        long intentCorrect = results.stream()
                .filter(result -> result.expectedIntent().equals(result.actualIntent()))
                .count();
        long severityCorrect = results.stream()
                .filter(result -> result.expectedSeverityLevel().equals(result.actualSeverityLevel()))
                .count();
        long highExpected = results.stream()
                .filter(result -> "HIGH".equals(result.expectedSeverityLevel()))
                .count();
        long highRecalled = results.stream()
                .filter(result -> "HIGH".equals(result.expectedSeverityLevel())
                        && "HIGH".equals(result.actualSeverityLevel()))
                .count();
        double highRiskRecall = highExpected == 0 ? 1.0 : (double) highRecalled / highExpected;
        return new RagMetrics(
                (double) hits / total,
                mrr,
                (double) intentCorrect / total,
                (double) severityCorrect / total,
                highRiskRecall);
    }

    private boolean matchesExpectedSource(String source, List<String> expectedSources) {
        for (String expected : safeList(expectedSources)) {
            if (containsNormalized(source, expected)) {
                return true;
            }
        }
        return false;
    }

    private RagEndToEndCaseResult buildRagasCase(RagEvalCase testCase, int topK) {
        List<SearchResult> retrieved = knowledgeService.retrieve(testCase.question(), topK);
        List<String> orderedSources = retrieved.stream()
                .map(SearchResult::source)
                .toList();
        boolean hit = false;
        double reciprocalRank = 0.0;
        for (int index = 0; index < orderedSources.size(); index++) {
            if (matchesExpectedSource(orderedSources.get(index), testCase.expectedSources())) {
                hit = true;
                reciprocalRank = 1.0 / (index + 1);
                break;
            }
        }
        List<String> retrievedSources = orderedSources.stream().distinct().toList();
        List<String> retrievedContexts = retrieved.stream()
                .map(SearchResult::content)
                .toList();
        String actualIntent = actualIntent(testCase.question());
        String actualSeverityLevel = actualSeverityLevel(testCase.question(), actualIntent);
        String answer = generateAnswer(testCase.question(), retrievedContexts);
        List<String> failures = evaluateAssertions(
                testCase,
                actualIntent,
                actualSeverityLevel,
                retrievedSources,
                retrievedContexts,
                answer);
        return new RagEndToEndCaseResult(
                testCase.id(),
                testCase.question(),
                testCase.expectedSources(),
                normalizeLabel(testCase.expectedIntent()),
                actualIntent,
                normalizeLabel(testCase.expectedSeverityLevel()),
                actualSeverityLevel,
                retrievedSources,
                retrievedContexts,
                safeString(testCase.referenceAnswer()),
                answer,
                hit,
                reciprocalRank,
                failures.isEmpty(),
                failures);
    }

    private String generateAnswer(String question, List<String> retrievedContexts) {
        String context = retrievedContexts.isEmpty()
                ? "无可用检索上下文。"
                : String.join("\n\n---\n\n", retrievedContexts);
        try {
            return aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 OpsPilot 的 RAG 回答生成器，用于 RAGAS 评测样本生成。
                            请依据检索上下文回答用户问题，语气专业、具体、克制。
                            如果上下文不足，只给出通用的排查建议，不要编造运维流程。
                            禁止编造命令或配置，禁止透露后台告警等级、Excel、MCP 或报告流程。
                            用中文回答，不超过 180 字。
                            """),
                    AiMessage.user("""
                            用户问题：
                            %s

                            检索上下文：
                            %s
                            """.formatted(question, context))
            )).trim();
        } catch (Exception ignored) {
            return "（模型调用失败，未生成回答）";
        }
    }

    private List<RagEvalCase> loadDataset(String datasetLocation) {
        try {
            Resource resource = resourceLoader.getResource(datasetLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, new TypeReference<>() {
                });
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to load RAG evaluation dataset: " + datasetLocation, exception);
        }
    }

    private String normalizeLabel(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String actualIntent(String question) {
        try {
            return intentClassifier.classify(question).name();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String actualSeverityLevel(String question, String actualIntent) {
        if (IntentType.CHAT.name().equals(actualIntent)) {
            return SeverityLevel.LOW.name();
        }
        // 与 IncidentGuardianAgent 行为一致：INCIDENT 意图在真实链路中会被强制判 HIGH。
        if (IntentType.INCIDENT.name().equals(actualIntent)) {
            return SeverityLevel.HIGH.name();
        }
        try {
            return assessmentService.assess(question).severity().name();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> evaluateAssertions(
            RagEvalCase testCase,
            String actualIntent,
            String actualSeverityLevel,
            List<String> retrievedSources,
            List<String> retrievedContexts,
            String answer
    ) {
        List<String> failures = new ArrayList<>();
        expectEqual(failures, "intent", normalizeLabel(testCase.expectedIntent()), actualIntent);
        expectEqual(failures, "severity", normalizeLabel(testCase.expectedSeverityLevel()), actualSeverityLevel);
        boolean isChat = "CHAT".equals(normalizeLabel(testCase.expectedIntent()));
        if (!isChat) {
            requireSources(failures, testCase.expectedSources(), retrievedSources);
            requireTerms(failures, "retrievedContext", testCase.expectedTerms(), joined(retrievedContexts));
            requireGroundedTerms(
                    failures,
                    testCase.groundedAnswerTerms(),
                    testCase.minGroundedAnswerTerms(),
                    answer + "\n" + joined(retrievedContexts));
        }
        requireTerms(failures, "requiredAnswer", testCase.requiredAnswerTerms(), answer);
        requireTerms(failures, "requiredHelp", testCase.requiredHelpTerms(), answer);
        forbidAnswerTerms(failures, testCase.forbiddenAnswerTerms(), answer);
        return failures;
    }

    private void expectEqual(List<String> failures, String field, String expected, String actual) {
        if (expected.isBlank()) {
            return;
        }
        if (!expected.equals(normalizeLabel(actual))) {
            failures.add("%s expected=%s actual=%s".formatted(field, expected, safeString(actual)));
        }
    }

    private void requireSources(List<String> failures, List<String> expectedSources, List<String> actualSources) {
        for (String expected : safeList(expectedSources)) {
            boolean matched = actualSources.stream()
                    .anyMatch(source -> containsNormalized(source, expected));
            if (!matched) {
                failures.add("missing expected source: " + expected);
            }
        }
    }

    private void requireTerms(List<String> failures, String scope, List<String> terms, String value) {
        for (String term : safeList(terms)) {
            if (!containsNormalized(value, term)) {
                failures.add("missing %s term: %s".formatted(scope, term));
            }
        }
    }

    private void requireGroundedTerms(List<String> failures, List<String> terms, int minimum, String value) {
        List<String> safeTerms = safeList(terms);
        if (safeTerms.isEmpty() || minimum <= 0) {
            return;
        }
        long matched = safeTerms.stream()
                .filter(term -> containsNormalized(value, term))
                .count();
        if (matched < minimum) {
            failures.add("grounded terms matched=%d required=%d".formatted(matched, minimum));
        }
    }

    private void forbidAnswerTerms(List<String> failures, List<String> forbiddenTerms, String answer) {
        for (String term : safeList(forbiddenTerms)) {
            if (containsNormalized(answer, term)) {
                failures.add("forbidden answer term present: " + term);
            }
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String joined(List<String> values) {
        return String.join("\n", values == null ? List.of() : values);
    }

    private boolean containsNormalized(String value, String expected) {
        if (value == null || expected == null || expected.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT)
                .contains(expected.toLowerCase(Locale.ROOT));
    }
}
