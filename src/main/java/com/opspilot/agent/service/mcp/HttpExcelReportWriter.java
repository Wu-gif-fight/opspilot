package com.opspilot.agent.service.mcp;

import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.IncidentReport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 通过 HTTP MCP 服务写入 Excel 记录。
 */
public class HttpExcelReportWriter implements ExcelReportWriter {

    private final WebClient webClient;

    public HttpExcelReportWriter(WebClient.Builder webClientBuilder, OpsPilotProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.getMcp().getExcel().getUrl()).build();
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

        webClient.post()
                .uri("/write")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
