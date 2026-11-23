package com.opspilot.agent.service.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class OpsPilotMcpTools {

    private final McpToolExecutionService executionService;

    public OpsPilotMcpTools(McpToolExecutionService executionService) {
        this.executionService = executionService;
    }

    @Tool(
            name = McpToolNames.WRITE_EXCEL_REPORT,
            description = "Write an OpsPilot incident report to the configured Excel workbook."
    )
    public String writeExcelReport(
            @ToolParam(description = "Internal report id") Long reportId,
            @ToolParam(description = "Internal user id") Long userId,
            @ToolParam(description = "User username") String username,
            @ToolParam(description = "Public chat session id") String sessionId,
            @ToolParam(description = "Classified intent") String intent,
            @ToolParam(description = "Classified category label") String category,
            @ToolParam(description = "Severity score") double severityScore,
            @ToolParam(description = "Severity level") String severity,
            @ToolParam(description = "Assessment confidence") double confidence,
            @ToolParam(description = "Incident assessment summary") String summary,
            @ToolParam(description = "Original conversation content") String content,
            @ToolParam(description = "Report creation timestamp") String createdAt
    ) {
        return executionService.writeExcelReport(
                reportId,
                userId,
                username,
                sessionId,
                intent,
                category,
                severityScore,
                severity,
                confidence,
                summary,
                content,
                createdAt);
    }

    @Tool(
            name = McpToolNames.SEND_INCIDENT_ALERT,
            description = "Send an OpsPilot production-incident alert to an on-call engineer or administrator."
    )
    public String sendIncidentAlert(
            @ToolParam(description = "Alert recipient email address") String recipient,
            @ToolParam(description = "Internal report id") Long reportId,
            @ToolParam(description = "Internal user id") Long userId,
            @ToolParam(description = "User username") String username,
            @ToolParam(description = "User display name") String displayName,
            @ToolParam(description = "Severity level") String severity,
            @ToolParam(description = "Classified category label") String category,
            @ToolParam(description = "Severity score") double severityScore,
            @ToolParam(description = "Incident assessment summary") String summary,
            @ToolParam(description = "Original conversation content") String content
    ) {
        return executionService.sendIncidentAlert(
                recipient,
                reportId,
                userId,
                username,
                displayName,
                severity,
                category,
                severityScore,
                summary,
                content);
    }
}
