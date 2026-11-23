package com.opspilot.agent.service.mcp;

import com.opspilot.agent.domain.AlertRecord;
import com.opspilot.agent.domain.IncidentReport;

/**
 * 高风险预警通知接口。
 *
 * <p>具体实现可以是日志、SMTP 邮件或 HTTP MCP 服务。</p>
 */
public interface AlertNotifier {

    void notify(AlertRecord alertRecord, IncidentReport report);
}
