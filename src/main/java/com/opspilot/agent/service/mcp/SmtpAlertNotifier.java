package com.opspilot.agent.service.mcp;

import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.AlertRecord;
import com.opspilot.agent.domain.IncidentReport;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP 邮件预警实现。
 *
 * <p>高级别告警触发后，把摘要信息发送给配置的值班负责人邮箱。</p>
 */
public class SmtpAlertNotifier implements AlertNotifier {

    private final JavaMailSender mailSender;
    private final OpsPilotProperties properties;

    public SmtpAlertNotifier(JavaMailSender mailSender, OpsPilotProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void notify(AlertRecord alertRecord, IncidentReport report) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMcp().getEmail().getFrom());
        message.setTo(alertRecord.getRecipient());
        message.setSubject("【生产事故预警】用户 %s 触发了高级别告警".formatted(report.getUser().getUsername()));
        message.setText("""
                系统在对话中监测到 1 名用户报告了疑似生产事故，请及时响应并升级。

                【告警信息如下】
                报告ID：%s
                用户ID：%s
                用户：%s
                对话内容：%s
                事件类型：%s
                严重度得分：%.2f
                告警等级：%s
                判断摘要：%s

                """.formatted(
                report.getId(),
                report.getUser().getUsername(),
                report.getUser().getDisplayName(),
                report.getContent(),
                report.getCategory(),
                report.getSeverityScore(),
                report.getSeverity(),
                report.getSummary()));
        mailSender.send(message);
    }
}
