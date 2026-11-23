package com.opspilot.agent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.agent.domain.IncidentCategory;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.IntentClassifier;
import com.opspilot.agent.service.IncidentAssessmentService;
import com.opspilot.agent.service.IncidentAssessment;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.ai.PromptTemplates;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class IncidentAssessmentHarnessTests {

    private ScriptedAiClient aiClient;
    private IntentClassifier intentClassifier;
    private IncidentAssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        aiClient = new ScriptedAiClient();
        intentClassifier = new IntentClassifier(aiClient);
        assessmentService = new IncidentAssessmentService(aiClient, new ObjectMapper());
    }

    @Test
    void explicitIncidentSignalIsAlwaysIncidentAndHigh() {
        String input = "线上支付系统宕机了，订单大量失败。";

        assertThat(intentClassifier.classify(input)).isEqualTo(IntentType.INCIDENT);
        IncidentAssessment assessment = assessmentService.assess(input);
        assertThat(assessment.severity()).isEqualTo(SeverityLevel.HIGH);
        assertThat(assessment.category()).isEqualTo(IncidentCategory.CRITICAL);
        assertThat(assessment.confidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void ordinaryChitchatStaysChat() {
        assertThat(intentClassifier.classify("帮我解释一下 Java 线程池怎么写。"))
                .isEqualTo(IntentType.CHAT);
        assertThat(intentClassifier.classify("今天天气怎么样？"))
                .isEqualTo(IntentType.CHAT);
    }

    @Test
    void consultSignalRoutesToConsultAndKeepsLowerSeverity() {
        String input = "接口一直报错，帮忙排查一下。";

        assertThat(intentClassifier.classify(input)).isEqualTo(IntentType.CONSULT);
        IncidentAssessment assessment = assessmentService.assess(input);
        assertThat(assessment.severity()).isEqualTo(SeverityLevel.LOW);
        assertThat(assessment.category()).isEqualTo(IncidentCategory.APPLICATION);
    }

    @Test
    void malformedModelAssessmentFallsBackToKeywordHeuristic() {
        IncidentAssessmentService fallbackService = new IncidentAssessmentService(
                new AiClient() {
                    @Override
                    public String complete(List<AiMessage> messages) {
                        return "not-json";
                    }

                    @Override
                    public Flux<String> stream(List<AiMessage> messages) {
                        return Flux.empty();
                    }
                },
                new ObjectMapper());

        IncidentAssessment assessment = fallbackService.assess("数据库连接一直异常。");

        assertThat(assessment.severity()).isEqualTo(SeverityLevel.LOW);
        assertThat(assessment.category()).isEqualTo(IncidentCategory.APPLICATION);
        assertThat(assessment.summary()).contains("keywords");
    }

    @Test
    void answerPromptsForbidLeakingInternalDetails() {
        AiMessage consultPrompt = PromptTemplates.answerSystemPrompt(
                IntentType.CONSULT,
                SeverityLevel.MEDIUM,
                "故障排查手册：先看监控和调用链。",
                "Demo Engineer");
        AiMessage incidentPrompt = PromptTemplates.answerSystemPrompt(
                IntentType.INCIDENT,
                SeverityLevel.HIGH,
                "事故应急响应：先止损，再升级 OnCall。",
                "Demo Engineer");

        assertThat(consultPrompt.content())
                .contains("不要编造命令")
                .contains("不要向用户输出告警等级");
        assertThat(incidentPrompt.content())
                .contains("止损")
                .contains("OnCall");
    }

    @Test
    void recentConsultContextRoutesFollowUpToConsult() {
        List<AiMessage> history = List.of(
                AiMessage.user("Redis 连接超时。"),
                AiMessage.assistant("先确认影响范围。"));

        assertThat(intentClassifier.classify("那今晚该怎么排查？", history))
                .isEqualTo(IntentType.CONSULT);
    }
}
