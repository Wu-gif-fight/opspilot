package com.opspilot.agent.service.mcp;

import com.opspilot.agent.domain.IncidentReport;
import java.util.LinkedHashMap;
import java.util.Map;

public class McpExcelReportWriter implements ExcelReportWriter {

    private final McpToolClient mcpToolClient;

    public McpExcelReportWriter(McpToolClient mcpToolClient) {
        this.mcpToolClient = mcpToolClient;
    }

    @Override
    public void write(IncidentReport report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.getId());
        payload.put("userId", report.getUser().getId());
        payload.put("username", report.getUser().getUsername());
        payload.put("sessionId", report.getSession() == null ? "" : report.getSession().getPublicId());
        payload.put("intent", report.getIntent().name());
        payload.put("category", report.getCategory().name());
        payload.put("severityScore", report.getSeverityScore());
        payload.put("severity", report.getSeverity().name());
        payload.put("confidence", report.getConfidence());
        payload.put("summary", report.getSummary());
        payload.put("content", report.getContent());
        payload.put("createdAt", report.getCreatedAt().toString());
        mcpToolClient.call(McpToolNames.WRITE_EXCEL_REPORT, payload);
    }
}
