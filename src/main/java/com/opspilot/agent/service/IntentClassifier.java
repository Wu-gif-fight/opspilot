package com.opspilot.agent.service;

import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.ai.IncidentLexicon;
import com.opspilot.agent.service.ai.PromptTemplates;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
/**
 * 用户意图分类服务。
 *
 * <p>把每轮输入路由到普通聊天、运维咨询或生产事故处理链路。</p>
 */
public class IntentClassifier {

    private final AiClient aiClient;

    public IntentClassifier(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public IntentType classify(String input) {
        return classify(input, List.of());
    }

    public IntentType classify(String input, List<AiMessage> history) {
        String normalized = input.toLowerCase(Locale.ROOT);
        // 生产事故表达优先级最高，不交给普通任务规则覆盖。
        if (IncidentLexicon.hasIncidentSignal(normalized)) {
            return IntentType.INCIDENT;
        }
        try {
            String label = aiClient.complete(PromptTemplates.intentPrompt(history, input)).trim().toUpperCase();
            if (label.contains("INCIDENT")) {
                return IntentType.INCIDENT;
            }
            if (label.contains("CONSULT")) {
                return IntentType.CONSULT;
            }
            if (label.contains("CHAT")) {
                return IntentType.CHAT;
            }
        } catch (Exception ignored) {
            // Keyword fallback keeps the route deterministic when the model is unavailable.
        }
        if (IncidentLexicon.hasConsultSignal(normalized) || hasRecentConsultContext(history)) {
            return IntentType.CONSULT;
        }
        return IntentType.CHAT;
    }

    private boolean hasRecentConsultContext(List<AiMessage> history) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        return history.stream()
                .skip(Math.max(0, history.size() - 6))
                .map(message -> message.content().toLowerCase())
                .anyMatch(IncidentLexicon::hasConsultSignal);
    }
}
