package com.opspilot.agent.service;

import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.config.RabbitConfig;
import com.opspilot.agent.domain.AlertRecord;
import com.opspilot.agent.domain.IncidentReport;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.domain.ToolStatus;
import com.opspilot.agent.repository.AlertRecordRepository;
import com.opspilot.agent.repository.IncidentReportRepository;
import com.opspilot.agent.service.mcp.AlertNotifier;
import com.opspilot.agent.service.mcp.ExcelReportWriter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
/**
 * 后台工具编排服务。
 *
 * <p>故障报告生成后，按“写 Excel -> 高级别发告警”的顺序执行工具链并持久化状态。
 * 任务经 RabbitMQ 投递到工具队列异步消费，具备幂等提交、令牌桶限流和死信标记。</p>
 */
public class ToolOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ToolOrchestrationService.class);
    private static final double TOOL_PERMITS_PER_SECOND = 5.0;

    private final ExcelReportWriter excelReportWriter;
    private final AlertNotifier alertNotifier;
    private final IncidentReportRepository reportRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final OpsPilotProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ToolRateLimiter rateLimiter = new ToolRateLimiter(TOOL_PERMITS_PER_SECOND);
    private final Set<Long> inFlightReportIds = ConcurrentHashMap.newKeySet();

    public ToolOrchestrationService(
            ExcelReportWriter excelReportWriter,
            AlertNotifier alertNotifier,
            IncidentReportRepository reportRepository,
            AlertRecordRepository alertRecordRepository,
            OpsPilotProperties properties,
            TransactionTemplate transactionTemplate,
            RabbitTemplate rabbitTemplate
    ) {
        this.excelReportWriter = excelReportWriter;
        this.alertNotifier = alertNotifier;
        this.reportRepository = reportRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 生产端：把报告 id 投递到 RabbitMQ 工具队列，由消费者异步处理。
     */
    public void handleAsync(Long reportId) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.TOOL_QUEUE, reportId);
        } catch (Exception exception) {
            log.warn("Failed to publish report {} to RabbitMQ: {}", reportId, exception.getMessage());
            markDeadLetter(reportId, "rabbitmq publish failed: " + exception.getMessage());
        }
    }

    /**
     * 消费端：幂等 + 限流后执行工具链；处理失败时抛异常触发消息进入死信队列。
     */
    public void process(Long reportId) {
        if (!rateLimiter.tryAcquire()) {
            log.warn("Tool queue rate limit exceeded, report {} rejected.", reportId);
            markDeadLetter(reportId, "rate limit exceeded");
            return;
        }
        if (!inFlightReportIds.add(reportId)) {
            log.debug("Report {} is already being processed, skip duplicate.", reportId);
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> handleInTransaction(reportId));
        } catch (Exception exception) {
            log.error("Failed to process report {}", reportId, exception);
            throw exception;
        } finally {
            inFlightReportIds.remove(reportId);
        }
    }

    @Transactional
    public void handle(Long reportId) {
        handleInTransaction(reportId);
    }

    private void handleInTransaction(Long reportId) {
        IncidentReport managedReport = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        writeExcel(managedReport);
        // 只有 Excel 写入成功且风险等级为 HIGH，才进入预警通知，和文档中的工具链顺序保持一致。
        if (managedReport.getSeverity() == SeverityLevel.HIGH && managedReport.getExcelStatus() == ToolStatus.SUCCESS) {
            sendAlerts(managedReport);
        }
        reportRepository.save(managedReport);
    }

    private void writeExcel(IncidentReport report) {
        try {
            excelReportWriter.write(report);
            report.setExcelStatus(ToolStatus.SUCCESS);
        } catch (Exception exception) {
            report.setExcelStatus(ToolStatus.FAILED);
            report.setToolError(shorten(exception.getMessage()));
        }
    }

    private void sendAlerts(IncidentReport report) {
        boolean allSuccess = true;
        for (String recipient : properties.getMcp().getEmail().getRecipients()) {
            AlertRecord alertRecord = new AlertRecord();
            alertRecord.setReport(report);
            alertRecord.setRecipient(recipient);
            alertRecordRepository.save(alertRecord);

            boolean sent = false;
            int maxAttempts = Math.max(1, properties.getMcp().getEmail().getMaxRetries() + 1);
            // 每个收件人独立重试和落库，管理员后台可以看到每封通知的最终状态。
            for (int attempt = 0; attempt < maxAttempts && !sent; attempt++) {
                try {
                    alertRecord.incrementAttempts();
                    alertNotifier.notify(alertRecord, report);
                    alertRecord.setStatus(ToolStatus.SUCCESS);
                    sent = true;
                } catch (Exception exception) {
                    alertRecord.setStatus(ToolStatus.FAILED);
                    alertRecord.setErrorMessage(shorten(exception.getMessage()));
                }
            }
            // 重试耗尽仍未成功，进入死信（DEAD_LETTER），保留错误信息供后续人工排查。
            if (!sent) {
                alertRecord.setStatus(ToolStatus.DEAD_LETTER);
            }
            alertRecordRepository.save(alertRecord);
            allSuccess = allSuccess && sent;
        }
        report.setEmailStatus(allSuccess ? ToolStatus.SUCCESS : ToolStatus.FAILED);
    }

    private void markDeadLetter(Long reportId, String reason) {
        reportRepository.findById(reportId).ifPresent(report -> {
            report.setExcelStatus(ToolStatus.DEAD_LETTER);
            report.setToolError(shorten(reason));
            reportRepository.save(report);
        });
    }

    private String shorten(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
