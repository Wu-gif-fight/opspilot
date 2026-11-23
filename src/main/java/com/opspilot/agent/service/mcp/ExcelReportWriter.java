package com.opspilot.agent.service.mcp;

import com.opspilot.agent.domain.IncidentReport;

/**
 * 故障报告写入 Excel 的工具接口。
 *
 * <p>本地文件写入和远程 MCP 写入都实现这个接口。</p>
 */
public interface ExcelReportWriter {

    void write(IncidentReport report);
}
