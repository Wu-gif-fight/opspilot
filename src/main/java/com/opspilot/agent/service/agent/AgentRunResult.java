package com.opspilot.agent.service.agent;

import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.IncidentAssessment;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.knowledge.SearchResult;
import java.util.List;

/**
 * Agent loop 完成后的结构化结果。
 */
public record AgentRunResult(
        IntentType intent,
        SeverityLevel severity,
        IncidentAssessment assessment,
        List<SearchResult> retrievedKnowledge,
        List<AiMessage> modelHistory,
        List<AiMessage> responseMessages,
        String memoryBrief,
        String knowledgeQuery,
        String responsePlan,
        AgentName responseAgent,
        List<AgentStep> steps
) {
    public static AgentRunResult from(AgentContext context) {
        return new AgentRunResult(
                context.intent(),
                context.severity(),
                context.assessment(),
                context.retrievedKnowledge(),
                context.modelHistory(),
                context.responseMessages(),
                context.memoryBrief(),
                context.knowledgeQuery(),
                context.responsePlan(),
                context.responseAgent(),
                context.steps()
        );
    }

    public boolean requiresReport() {
        return intent != null && intent != IntentType.CHAT && assessment != null;
    }
}
