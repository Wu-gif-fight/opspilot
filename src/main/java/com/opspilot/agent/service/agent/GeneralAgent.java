package com.opspilot.agent.service.agent;

import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.ai.PromptTemplates;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 通用闲聊 Agent。
 *
 * <p>处理日常闲聊和通用知识问答，不生成后台故障报告。</p>
 */
@Component
public class GeneralAgent implements OpsPilotAgent {

    private final AiClient aiClient;

    public GeneralAgent(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.GENERAL_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.intentRouted()
                && context.intent() == IntentType.CHAT
                && !context.responsePlanned();
    }

    @Override
    public AgentDecision act(AgentContext context) {
        context.setSeverity(SeverityLevel.LOW);
        context.setResponseAgent(AgentName.GENERAL_AGENT);
        String plan = planResponse(context);
        context.setResponsePlan(plan);
        context.setResponseMessages(buildResponseMessages(context, plan));
        context.markResponsePlanned();
        return AgentDecision.finish(AgentAction.PLAN_RESPONSE, "normal companion response planned by model");
    }

    private String planResponse(AgentContext context) {
        try {
            String plan = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 OpsPilot 的 GeneralAgent。
                            你负责日常闲聊和通用知识问答。
                            请根据当前输入和记忆摘要，制定一句简短回复策略。
                            不要做故障诊断，不要输出告警等级，不要替用户下结论。
                            """),
                    AiMessage.user("""
                            记忆摘要：
                            %s

                            当前输入：
                            %s
                            """.formatted(context.memoryBrief(), context.modelInput()))
            )).trim();
            return plan.isBlank() ? "围绕用户当前问题直接、自然地回答。" : shorten(plan, 300);
        } catch (Exception ignored) {
            return "围绕用户当前问题直接、自然地回答。";
        }
    }

    private List<AiMessage> buildResponseMessages(AgentContext context, String plan) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(PromptTemplates.answerSystemPrompt(
                IntentType.CHAT,
                SeverityLevel.LOW,
                "",
                context.user().getDisplayName()));
        messages.add(AiMessage.system("""
                当前由 GeneralAgent 负责回复。
                记忆摘要：
                %s

                回复策略：
                %s
                """.formatted(context.memoryBrief(), plan)));
        messages.addAll(context.modelHistory());
        return messages;
    }

    private String shorten(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
