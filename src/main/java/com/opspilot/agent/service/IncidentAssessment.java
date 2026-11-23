package com.opspilot.agent.service;

import com.opspilot.agent.domain.IncidentCategory;
import com.opspilot.agent.domain.SeverityLevel;

/**
 * 一次后台故障分级结果。
 *
 * <p>该对象只在服务端告警和工具链中使用，不作为普通用户消息内容。</p>
 */
public record IncidentAssessment(
        IncidentCategory category,
        double severityScore,
        SeverityLevel severity,
        double confidence,
        String summary
) {
}
