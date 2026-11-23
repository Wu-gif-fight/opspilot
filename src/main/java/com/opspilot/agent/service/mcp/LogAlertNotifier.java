package com.opspilot.agent.service.mcp;

import com.opspilot.agent.domain.AlertRecord;
import com.opspilot.agent.domain.IncidentReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志预警实现。
 *
 * <p>用于本地演示或无 SMTP 环境时验证告警链路是否被触发。</p>
 */
public class LogAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogAlertNotifier.class);

    @Override
    public void notify(AlertRecord alertRecord, IncidentReport report) {
        log.warn(
                "Incident alert dry-run: recipient={}, reportId={}, user={}, summary={}",
                alertRecord.getRecipient(),
                report.getId(),
                report.getUser().getUsername(),
                report.getSummary());
    }
}
