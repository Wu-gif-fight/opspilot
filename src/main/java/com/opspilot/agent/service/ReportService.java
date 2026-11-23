package com.opspilot.agent.service;

import com.opspilot.agent.domain.ChatMessage;
import com.opspilot.agent.domain.ChatSession;
import com.opspilot.agent.domain.IncidentReport;
import com.opspilot.agent.domain.ToolStatus;
import com.opspilot.agent.domain.UserAccount;
import com.opspilot.agent.dto.AlertRecordResponse;
import com.opspilot.agent.dto.AgentRunTraceResponse;
import com.opspilot.agent.dto.ConversationResponse;
import com.opspilot.agent.dto.ExcelRecordResponse;
import com.opspilot.agent.repository.AlertRecordRepository;
import com.opspilot.agent.repository.ChatMessageRepository;
import com.opspilot.agent.repository.ChatSessionRepository;
import com.opspilot.agent.repository.IncidentReportRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
/**
 * 管理员后台数据查询服务。
 *
 * <p>封装报告列表、Excel 写入记录、预警记录和完整会话读取逻辑。</p>
 */
public class ReportService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final IncidentReportRepository incidentReportRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AlertRecordRepository alertRecordRepository;
    private final AgentRunTraceService agentRunTraceService;

    public ReportService(
            IncidentReportRepository incidentReportRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            AlertRecordRepository alertRecordRepository,
            AgentRunTraceService agentRunTraceService
    ) {
        this.incidentReportRepository = incidentReportRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.alertRecordRepository = alertRecordRepository;
        this.agentRunTraceService = agentRunTraceService;
    }

    @Transactional(readOnly = true)
    public List<IncidentReport> myReports(Long userId) {
        return incidentReportRepository.findTop50ByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<IncidentReport> latestReports() {
        // 管理员后台只展示普通用户对话产生的报告，避免管理员测试消息混入统计大屏。
        return incidentReportRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(ReportService::isUserReport)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExcelRecordResponse> excelRecords() {
        return incidentReportRepository
                .findTop100ByExcelStatusOrderByCreatedAtDesc(ToolStatus.SUCCESS)
                .stream()
                .filter(ReportService::isUserReport)
                .map(ExcelRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertRecordResponse> alertRecords() {
        return alertRecordRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(alertRecord -> isUserReport(alertRecord.getReport()))
                .map(AlertRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse conversation(String sessionId) {
        ChatSession session = chatSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        // 管理员只能查看普通用户会话；非普通用户会话统一按不存在处理，减少后台数据误展示。
        if (!isRegularUser(session.getUser())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        List<ChatMessage> messages = chatMessageRepository.findBySession_PublicIdOrderByCreatedAtAsc(sessionId);
        List<AgentRunTraceResponse> runTraces = agentRunTraceService.tracesForSession(sessionId);
        return ConversationResponse.from(session, messages, runTraces);
    }

    private static boolean isUserReport(IncidentReport report) {
        return report != null && isRegularUser(report.getUser());
    }

    private static boolean isRegularUser(UserAccount user) {
        return user != null && !user.getRoles().contains(ROLE_ADMIN);
    }
}
