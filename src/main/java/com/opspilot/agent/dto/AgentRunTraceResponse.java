package com.opspilot.agent.dto;

import com.opspilot.agent.domain.AgentRunTrace;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.agent.AgentName;
import java.time.Instant;
import java.util.List;

/**
 * 管理员查看 agent run trace 详情的响应体。
 */
public record AgentRunTraceResponse(
        String traceId,
        String sessionId,
        Long userId,
        String username,
        String displayName,
        Long triggerMessageId,
        String input,
        IntentType intent,
        SeverityLevel severity,
        String memoryBrief,
        String knowledgeQuery,
        String responsePlan,
        AgentName responseAgent,
        int stepCount,
        Instant startedAt,
        Instant completedAt,
        List<AgentRunTraceStepResponse> steps
) {
    public static AgentRunTraceResponse from(AgentRunTrace trace) {
        return new AgentRunTraceResponse(
                trace.getTraceId(),
                trace.getSession().getPublicId(),
                trace.getUser().getId(),
                trace.getUser().getUsername(),
                trace.getUser().getDisplayName(),
                trace.getTriggerMessage() == null ? null : trace.getTriggerMessage().getId(),
                trace.getInput(),
                trace.getIntent(),
                trace.getSeverity(),
                trace.getMemoryBrief(),
                trace.getKnowledgeQuery(),
                trace.getResponsePlan(),
                trace.getResponseAgent(),
                trace.getStepCount(),
                trace.getStartedAt(),
                trace.getCompletedAt(),
                trace.getSteps().stream()
                        .map(AgentRunTraceStepResponse::from)
                        .toList());
    }
}
