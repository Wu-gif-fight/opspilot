package com.opspilot.agent.service.tools;

import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.repository.IncidentReportRepository;
import com.opspilot.agent.service.knowledge.KnowledgeService;
import com.opspilot.agent.service.knowledge.SearchResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * 模型可自主调用的函数工具（function calling）。
 *
 * <p>与规则驱动的工具链（{@code ToolOrchestrationService}）不同，这些工具由大模型
 * 根据用户意图自行决定是否调用、调用哪个，用于演示「模型自主函数调用」路径。</p>
 */
@Service
public class OpsPilotFunctionTools {

    private static final AtomicInteger TICKET_SEQ = new AtomicInteger(1);

    private final KnowledgeService knowledgeService;
    private final IncidentReportRepository incidentReportRepository;

    public OpsPilotFunctionTools(
            KnowledgeService knowledgeService,
            IncidentReportRepository incidentReportRepository
    ) {
        this.knowledgeService = knowledgeService;
        this.incidentReportRepository = incidentReportRepository;
    }

    @Tool(description = "检索运维知识库，返回与查询最相关的文档片段")
    public String searchKnowledgeBase(@ToolParam(description = "检索关键词") String query) {
        List<SearchResult> results = knowledgeService.retrieve(query, 3);
        if (results.isEmpty()) {
            return "知识库中没有找到与「" + query + "」相关的内容。";
        }
        StringBuilder builder = new StringBuilder();
        for (SearchResult result : results) {
            builder.append("- [").append(result.source()).append("] ").append(result.content()).append('\n');
        }
        return builder.toString().trim();
    }

    @Tool(description = "统计当前数据库中高级别(HIGH)告警的数量")
    public String countHighAlerts() {
        long high = incidentReportRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(report -> report.getSeverity() == SeverityLevel.HIGH)
                .count();
        return "当前共有 " + high + " 条高级别(HIGH)告警记录。";
    }

    @Tool(description = "创建一条值班工单，返回工单号（演示用，本地生成）")
    public String createOnCallTicket(
            @ToolParam(description = "工单标题") String title,
            @ToolParam(description = "工单描述") String description
    ) {
        String ticketId = "OPS-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + TICKET_SEQ.getAndIncrement();
        return "已创建值班工单 " + ticketId + "（标题：" + title + "，描述：" + description + "）";
    }
}
