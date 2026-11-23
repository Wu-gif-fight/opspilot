package com.opspilot.agent.dto;

import com.opspilot.agent.domain.IncidentCategory;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.IncidentReport;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.domain.ToolStatus;
import java.time.Instant;

/**
 * 后台故障报告列表响应。
 */
public record ReportResponse(
        Long id,
        Long userId,
        String username,
        String sessionId,
        IntentType intent,
        IncidentCategory category,
        double severityScore,
        SeverityLevel severity,
        double confidence,
        String summary,
        ToolStatus excelStatus,
        ToolStatus emailStatus,
        Instant createdAt
) {
    public static ReportResponse from(IncidentReport report) {
        return new ReportResponse(
                report.getId(),
                report.getUser().getId(),
                report.getUser().getUsername(),
                report.getSession() == null ? null : report.getSession().getPublicId(),
                report.getIntent(),
                report.getCategory(),
                report.getSeverityScore(),
                report.getSeverity(),
                report.getConfidence(),
                report.getSummary(),
                report.getExcelStatus(),
                report.getEmailStatus(),
                report.getCreatedAt());
    }
}
