package com.opspilot.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.agent.domain.IncidentCategory;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.ai.IncidentLexicon;
import com.opspilot.agent.service.ai.PromptTemplates;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
/**
 * 后台故障分级服务。
 *
 * <p>结合事故词库、模型结构化输出和关键词兜底，生成告警所需的事件类型与严重度字段。</p>
 */
public class IncidentAssessmentService {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public IncidentAssessmentService(AiClient aiClient, ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    public IncidentAssessment assess(String input) {
        return assess(input, List.of());
    }

    public IncidentAssessment assess(String input, List<AiMessage> history) {
        // 事故词库是硬规则，优先于模型判断，保证明显生产事故信号不会被漏掉。
        if (IncidentLexicon.hasIncidentSignal(input.toLowerCase())) {
            return new IncidentAssessment(
                    IncidentCategory.CRITICAL,
                    4.0,
                    SeverityLevel.HIGH,
                    0.95,
                    "Explicit production incident signal detected.");
        }
        try {
            String raw = aiClient.complete(PromptTemplates.incidentPrompt(history, input));
            return normalize(parseJson(raw));
        } catch (Exception ignored) {
            // 模型输出格式异常或调用失败时，使用关键词兜底，保证告警链路仍可运行。
            return heuristic(input);
        }
    }

    private IncidentAssessment parseJson(String raw) throws Exception {
        String json = raw.trim();
        // 兼容模型在 JSON 前后额外输出少量文本的情况，只截取最外层 JSON 对象。
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(json);
        IncidentCategory category = IncidentCategory.valueOf(node.path("category").asText("NORMAL").toUpperCase());
        double score = node.path("severityScore").asDouble(scoreForCategory(category));
        SeverityLevel severity = SeverityLevel.valueOf(node.path("severity").asText(severityFromScore(score).name()).toUpperCase());
        double confidence = node.path("confidence").asDouble(0.75);
        String summary = node.path("summary").asText("Model assessment.");
        return new IncidentAssessment(category, score, severity, confidence, summary);
    }

    private IncidentAssessment normalize(IncidentAssessment assessment) {
        // 告警等级取模型等级和分数推导等级中更高的一方，降低低估故障严重度的概率。
        SeverityLevel scoreSeverity = severityFromScore(assessment.severityScore());
        SeverityLevel severity = assessment.severity().ordinal() > scoreSeverity.ordinal() ? assessment.severity() : scoreSeverity;
        if (assessment.category() == IncidentCategory.CRITICAL) {
            severity = SeverityLevel.HIGH;
        }
        return new IncidentAssessment(
                assessment.category(),
                assessment.severityScore(),
                severity,
                clamp(assessment.confidence(), 0.0, 1.0),
                assessment.summary());
    }

    private IncidentAssessment heuristic(String input) {
        String normalized = input.toLowerCase();
        if (containsAny(normalized, "支付失败", "订单丢失", "数据不一致", "数据丢失", "payment", "data loss")) {
            return new IncidentAssessment(IncidentCategory.PAYMENT, 3.1, SeverityLevel.MEDIUM, 0.75, "Payment or data inconsistency keywords detected.");
        }
        if (containsAny(normalized, "报错", "异常", "超时", "无法访问", "error", "exception", "timeout", "outage")) {
            return new IncidentAssessment(IncidentCategory.APPLICATION, 2.2, SeverityLevel.LOW, 0.72, "Application error or timeout keywords detected.");
        }
        return new IncidentAssessment(IncidentCategory.NORMAL, 0.0, SeverityLevel.LOW, 0.66, "No obvious incident signal.");
    }

    private SeverityLevel severityFromScore(double score) {
        if (score >= 4.0) {
            return SeverityLevel.HIGH;
        }
        if (score >= 3.0) {
            return SeverityLevel.MEDIUM;
        }
        return SeverityLevel.LOW;
    }

    private double scoreForCategory(IncidentCategory category) {
        return switch (category) {
            case CRITICAL -> 4.0;
            case PAYMENT, SECURITY -> 3.0;
            case DATABASE, INFRASTRUCTURE, NETWORK -> 2.5;
            case APPLICATION -> 2.0;
            case NORMAL -> 0.0;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
