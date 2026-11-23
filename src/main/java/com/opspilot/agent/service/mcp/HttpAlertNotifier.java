package com.opspilot.agent.service.mcp;

import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.AlertRecord;
import com.opspilot.agent.domain.IncidentReport;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 通过 HTTP MCP 服务发送高风险预警。
 */
public class HttpAlertNotifier implements AlertNotifier {

    private final WebClient webClient;

    public HttpAlertNotifier(WebClient.Builder webClientBuilder, OpsPilotProperties properties) {
        this.webClient = webClientBuilder.baseUrl(properties.getMcp().getEmail().getUrl()).build();
    }

    @Override
    public void notify(AlertRecord alertRecord, IncidentReport report) {
        webClient.post()
                .uri("/send")
                .bodyValue(Map.of(
                        "recipient", alertRecord.getRecipient(),
                        "reportId", report.getId(),
                        "userId", report.getUser().getId(),
                        "username", report.getUser().getUsername(),
                        "severity", report.getSeverity().name(),
                        "summary", report.getSummary(),
                        "content", report.getContent()))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
