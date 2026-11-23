package com.opspilot.agent.service.mcp;

import com.opspilot.agent.config.OpsPilotProperties;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class McpToolExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolExecutionService.class);

    private final Path excelPath;
    private final Object excelLock = new Object();
    private final JavaMailSender mailSender;
    private final OpsPilotProperties properties;

    public McpToolExecutionService(JavaMailSender mailSender, OpsPilotProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.excelPath = Path.of(properties.getMcp().getExcel().getLocalPath());
    }

    public String writeExcelReport(
            Long reportId,
            Long userId,
            String username,
            String sessionId,
            String intent,
            String category,
            double severityScore,
            String severity,
            double confidence,
            String summary,
            String content,
            String createdAt
    ) {
        synchronized (excelLock) {
            try {
                if (excelPath.getParent() != null) {
                    Files.createDirectories(excelPath.getParent());
                }
                Workbook workbook = openWorkbook();
                Sheet sheet = workbook.getNumberOfSheets() == 0
                        ? workbook.createSheet("reports")
                        : workbook.getSheetAt(0);
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    writeHeader(sheet.createRow(0));
                }
                Row row = sheet.createRow(sheet.getLastRowNum() + 1);
                cell(row, 0).setCellValue(nullSafe(reportId));
                cell(row, 1).setCellValue(nullSafe(userId));
                cell(row, 2).setCellValue(nullSafe(username));
                cell(row, 3).setCellValue(nullSafe(sessionId));
                cell(row, 4).setCellValue(nullSafe(intent));
                cell(row, 5).setCellValue(nullSafe(category));
                cell(row, 6).setCellValue(severityScore);
                cell(row, 7).setCellValue(nullSafe(severity));
                cell(row, 8).setCellValue(confidence);
                cell(row, 9).setCellValue(nullSafe(summary));
                cell(row, 10).setCellValue(nullSafe(content));
                cell(row, 11).setCellValue(nullSafe(createdAt));
                for (int i = 0; i < 12; i++) {
                    sheet.autoSizeColumn(i);
                }
                try (OutputStream outputStream = Files.newOutputStream(excelPath)) {
                    workbook.write(outputStream);
                }
                workbook.close();
                return "Excel report written: " + reportId;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to write report through MCP tool", exception);
            }
        }
    }

    public String sendIncidentAlert(
            String recipient,
            Long reportId,
            Long userId,
            String username,
            String displayName,
            String severity,
            String category,
            double severityScore,
            String summary,
            String content
    ) {
        if ("log".equalsIgnoreCase(properties.getMcp().getEmail().getMcpServerDeliveryMode())) {
            logger.warn(
                    "MCP incident alert recipient={} reportId={} userId={} username={} severity={} summary={}",
                    recipient, reportId, userId, username, severity, summary);
            return "Incident alert logged: " + reportId;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMcp().getEmail().getFrom());
        message.setTo(recipient);
        message.setSubject("【生产事故预警】用户 %s 触发了高级别告警".formatted(username));
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
                发送时间：%s

                """.formatted(
                reportId,
                userId,
                nullSafe(displayName),
                nullSafe(content),
                nullSafe(category),
                severityScore,
                nullSafe(severity),
                nullSafe(summary),
                Instant.now()));
        mailSender.send(message);
        return "Incident alert sent: " + reportId;
    }

    private Workbook openWorkbook() throws Exception {
        if (!Files.exists(excelPath)) {
            return new XSSFWorkbook();
        }
        try (InputStream inputStream = Files.newInputStream(excelPath)) {
            return WorkbookFactory.create(inputStream);
        }
    }

    private void writeHeader(Row row) {
        String[] headers = {
                "报告ID", "用户ID", "账号", "会话ID", "意图", "事件类型", "严重度得分",
                "告警等级", "置信度", "判断摘要", "对话内容", "对话时间"
        };
        for (int i = 0; i < headers.length; i++) {
            cell(row, i).setCellValue(headers[i]);
        }
    }

    private Cell cell(Row row, int index) {
        return row.createCell(index);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
