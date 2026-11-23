package com.opspilot.agent.dto;

import com.opspilot.agent.domain.AgentRunTrace;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.service.agent.AgentName;
import java.time.Instant;

/**
 * 管理员后台运行轨迹列表项。
 */
public record AgentRunTraceSummaryResponse(
        String traceId,
        String sessionId,
        Long userId,
        String username,
        String displayName,
        Long triggerMessageId,
        String input,
        IntentType intent,
        SeverityLevel severity,
        AgentName responseAgent,
        int stepCount,
        Instant startedAt,
        Instant completedAt
) {
    public static AgentRunTraceSummaryResponse from(AgentRunTrace trace) {
        return new AgentRunTraceSummaryResponse(
                trace.getTraceId(),
                trace.getSession().getPublicId(),
                trace.getUser().getId(),
                trace.getUser().getUsername(),
                trace.getUser().getDisplayName(),
                trace.getTriggerMessage() == null ? null : trace.getTriggerMessage().getId(),
                trace.getInput(),
                trace.getIntent(),
                trace.getSeverity(),
                trace.getResponseAgent(),
                trace.getStepCount(),
                trace.getStartedAt(),
                trace.getCompletedAt());
    }
}
