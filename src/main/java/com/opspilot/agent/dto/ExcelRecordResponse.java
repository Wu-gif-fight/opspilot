package com.opspilot.agent.dto;

import com.opspilot.agent.domain.IncidentCategory;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.IncidentReport;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.domain.ToolStatus;
import java.time.Instant;

/**
 * 管理员后台 Excel 写入数据响应。
 */
public record ExcelRecordResponse(
        Long reportId,
        Long userId,
        String username,
        String sessionId,
        IntentType intent,
        IncidentCategory category,
        double severityScore,
        SeverityLevel severity,
        double confidence,
        String summary,
        String content,
        ToolStatus excelStatus,
        Instant createdAt
) {
    public static ExcelRecordResponse from(IncidentReport report) {
        return new ExcelRecordResponse(
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
                report.getContent(),
                report.getExcelStatus(),
                report.getCreatedAt());
    }
}
