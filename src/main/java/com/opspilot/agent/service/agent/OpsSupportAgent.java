package com.opspilot.agent.service.agent;

import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.ai.PromptTemplates;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 运维支持 Agent。
 *
 * <p>处理咨询和故障场景，回复会结合 RAG 知识与 IncidentGuardian 的后台评估结果。</p>
 */
@Component
public class OpsSupportAgent implements OpsPilotAgent {

    private final AiClient aiClient;

    public OpsSupportAgent(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.OPS_SUPPORT_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.incidentAssessed()
                && context.intent() != IntentType.CHAT
                && !context.responsePlanned();
    }

    @Override
    public AgentDecision act(AgentContext context) {
        context.setResponseAgent(AgentName.OPS_SUPPORT_AGENT);
        String plan = planResponse(context);
        context.setResponsePlan(plan);
        context.setResponseMessages(buildResponseMessages(context, plan));
        context.markResponsePlanned();
        return AgentDecision.finish(
                AgentAction.PLAN_RESPONSE,
                "support response planned by model with severity=%s".formatted(context.severity()));
    }

    private String planResponse(AgentContext context) {
        try {
            String plan = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 OpsPilot 的 OpsSupportAgent。
                            你负责运维支持式回应策略，不直接给结论。
                            请结合记忆摘要、故障分级结果和知识库命中，制定 2-3 句回复策略。
                            高级别告警时必须优先给出止损步骤。
                            不要输出后台标签、告警等级、分数或报告口吻。
                            """),
                    AiMessage.user("""
                            记忆摘要：
                            %s

                            当前输入：
                            %s

                            故障分级结果：
                            %s

                            知识库 query：
                            %s

                            知识库命中：
                            %s
                            """.formatted(
                            context.memoryBrief(),
                            context.modelInput(),
                            context.assessment() == null ? "无" : context.assessment().summary(),
                            context.knowledgeQuery(),
                            formatKnowledge(context)))
            )).trim();
            return plan.isBlank() ? "先复述问题，再给出具体排查步骤；高级别告警时优先止损。" : shorten(plan, 500);
        } catch (Exception ignored) {
            return "先复述问题，再给出具体排查步骤；高级别告警时优先止损。";
        }
    }

    private List<AiMessage> buildResponseMessages(AgentContext context, String plan) {
        String knowledgeContext = String.join("\n\n", context.retrievedKnowledge().stream()
                .map(result -> "- [" + result.source() + "] " + result.content())
                .toList());
        List<AiMessage> messages = new ArrayList<>();
        messages.add(PromptTemplates.answerSystemPrompt(
                context.intent(),
                context.severity(),
                knowledgeContext,
                context.user().getDisplayName()));
        messages.add(AiMessage.system("""
                当前由 OpsSupportAgent 负责回复。
                记忆摘要：
                %s

                KnowledgeAgent 检索 query：
                %s

                回复策略：
                %s
                """.formatted(context.memoryBrief(), context.knowledgeQuery(), plan)));
        messages.addAll(context.modelHistory());
        return messages;
    }

    private String formatKnowledge(AgentContext context) {
        if (context.retrievedKnowledge().isEmpty()) {
            return "无";
        }
        return String.join("\n", context.retrievedKnowledge().stream()
                .limit(4)
                .map(result -> "- " + result.content())
                .toList());
    }

    private String shorten(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
