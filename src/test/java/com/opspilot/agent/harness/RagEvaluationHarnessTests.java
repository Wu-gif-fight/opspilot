package com.opspilot.agent.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.agent.service.IntentClassifier;
import com.opspilot.agent.service.IncidentAssessmentService;
import com.opspilot.agent.service.knowledge.KnowledgeService;
import com.opspilot.agent.service.knowledge.SearchResult;
import com.opspilot.agent.service.knowledge.eval.RagEndToEndCaseResult;
import com.opspilot.agent.service.knowledge.eval.RagEvalReport;
import com.opspilot.agent.service.knowledge.eval.RagEvaluationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RagEvaluationHarnessTests {

    private RagEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        ScriptedAiClient aiClient = new ScriptedAiClient();
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.retrieve(anyString(), anyInt())).thenAnswer(invocation -> {
            String question = invocation.getArgument(0, String.class);
            if (question.contains("宕机") || question.contains("支付失败")) {
                return List.of(new SearchResult(
                        1L,
                        "incident-response.md",
                        "HIGH production incident: 先止损，再升级 OnCall，回滚最近一次发布或切换流量。",
                        0.98));
            }
            return List.of(new SearchResult(
                    2L,
                    "monitoring-alerting.md",
                    "高延迟先看调用链定位慢在哪一跳，再查数据库慢查询和连接池。",
                    0.91));
        });

        evaluationService = new RagEvaluationService(
                knowledgeService,
                aiClient,
                new IntentClassifier(aiClient),
                new IncidentAssessmentService(aiClient, new ObjectMapper()),
                new ObjectMapper());
    }

    @Test
    void evaluatesRagDatasetWithIntentIncidentRetrievalAndAnswerAssertions() {
        RagEvalReport report = evaluationService.evaluate("classpath:harness/rag-harness-scenarios.json", 2);

        assertThat(report.totalCases()).isEqualTo(2);
        assertThat(report.passedCases()).isEqualTo(2);
        assertThat(report.failedCases()).isZero();
        assertThat(report.cases()).allSatisfy(testCase -> {
            assertThat(testCase.passed()).isTrue();
            assertThat(testCase.failures()).isEmpty();
            assertThat(testCase.actualIntent()).isNotBlank();
            assertThat(testCase.actualSeverityLevel()).isNotBlank();
            assertThat(testCase.retrievedSources()).isNotEmpty();
            assertThat(testCase.answer()).isNotBlank();
        });
    }

    @Test
    void summaryIncludesHarnessPassFailCounts() {
        RagEvalReport report = evaluationService.evaluate("classpath:harness/rag-harness-scenarios.json", 2);

        assertThat(evaluationService.formatSummary(report))
                .contains("passed=2")
                .contains("failed=0")
                .contains("Java harness assertions");
        assertThat(report.cases())
                .extracting(RagEndToEndCaseResult::expectedIntent)
                .containsExactly("INCIDENT", "CONSULT");
    }
}
