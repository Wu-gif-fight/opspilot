package com.opspilot.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;

@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * SSE 流式聊天事件。
 *
 * <p>meta 先返回会话 id，token 持续返回模型片段，done 表示本轮结束。</p>
 */
public record ChatStreamEvent(
        String type,
        String sessionId,
        String content,
        IntentType intent,
        SeverityLevel severity
) {
    public static ChatStreamEvent meta(String sessionId) {
        return new ChatStreamEvent("meta", sessionId, "", null, null);
    }

    public static ChatStreamEvent token(String sessionId, String content) {
        return new ChatStreamEvent("token", sessionId, content, null, null);
    }

    public static ChatStreamEvent done(String sessionId) {
        return new ChatStreamEvent("done", sessionId, "", null, null);
    }

    public static ChatStreamEvent error(String sessionId, String content) {
        return new ChatStreamEvent("error", sessionId, content, null, null);
    }
}
