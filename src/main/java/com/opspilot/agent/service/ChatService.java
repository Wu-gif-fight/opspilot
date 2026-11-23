package com.opspilot.agent.service;

import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.ChatMessage;
import com.opspilot.agent.domain.ChatSession;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.MessageRole;
import com.opspilot.agent.domain.IncidentReport;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.domain.UserAccount;
import com.opspilot.agent.dto.ChatRequest;
import com.opspilot.agent.dto.ChatStreamEvent;
import com.opspilot.agent.repository.ChatMessageRepository;
import com.opspilot.agent.repository.ChatSessionRepository;
import com.opspilot.agent.repository.IncidentReportRepository;
import com.opspilot.agent.repository.UserAccountRepository;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.ai.PromptTemplates;
import com.opspilot.agent.service.knowledge.SearchResult;
import com.opspilot.agent.service.agent.AgentRunResult;
import com.opspilot.agent.service.agent.AgentRuntimeService;
import com.opspilot.agent.service.memory.ShortTermMemoryService;
import com.opspilot.agent.service.memory.UserProfileMemoryService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
/**
 * 用户聊天主流程服务。
 *
 * <p>负责会话落库、模型流式调用和后台报告触发；意图路由、记忆读取、RAG 与风险评估
 * 由 AgentRuntimeService 中的多 Agent loop 完成。</p>
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final UserAccountRepository userAccountRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IncidentReportRepository reportRepository;
    private final OpsPilotProperties properties;
    private final ToolOrchestrationService toolOrchestrationService;
    private final PrivacySanitizer privacySanitizer;
    private final ShortTermMemoryService shortTermMemoryService;
    private final UserProfileMemoryService userProfileMemoryService;
    private final AgentRuntimeService agentRuntimeService;
    private final AgentRunTraceService agentRunTraceService;
    private final AiClient aiClient;

    public ChatService(
            UserAccountRepository userAccountRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            IncidentReportRepository reportRepository,
            OpsPilotProperties properties,
            ToolOrchestrationService toolOrchestrationService,
            PrivacySanitizer privacySanitizer,
            ShortTermMemoryService shortTermMemoryService,
            UserProfileMemoryService userProfileMemoryService,
            AgentRuntimeService agentRuntimeService,
            AgentRunTraceService agentRunTraceService,
            AiClient aiClient
    ) {
        this.userAccountRepository = userAccountRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.reportRepository = reportRepository;
        this.properties = properties;
        this.toolOrchestrationService = toolOrchestrationService;
        this.privacySanitizer = privacySanitizer;
        this.shortTermMemoryService = shortTermMemoryService;
        this.userProfileMemoryService = userProfileMemoryService;
        this.agentRuntimeService = agentRuntimeService;
        this.agentRunTraceService = agentRunTraceService;
        this.aiClient = aiClient;
    }

    public Flux<ServerSentEvent<ChatStreamEvent>> streamChat(Long userId, ChatRequest request) {
        // 聊天接口使用 SSE 流式返回；数据库读写放到 boundedElastic，避免阻塞响应线程。
        return Mono.fromCallable(() -> prepare(userId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::streamPrepared)
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(null, "服务暂时不可用：" + exception.getMessage()))));
    }

    private PreparedConversation prepare(Long userId, ChatRequest request) {
        String input = request.message().trim();
        String modelInput = privacySanitizer.sanitize(input);
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        ChatSession session = resolveSession(user, request.sessionId(), input);
        Instant startedAt = Instant.now();
        AgentRunResult agentRun = agentRuntimeService.run(user, session, input, modelInput);
        Instant completedAt = Instant.now();
        ChatMessage userMessage = saveMessage(user, session, MessageRole.USER, input);
        agentRunTraceService.saveRun(user, session, userMessage, input, agentRun, startedAt, completedAt);
        rememberUserProfile(user, session, input, agentRun.memoryBrief());

        IncidentReport report = null;
        if (agentRun.requiresReport()) {
            report = saveReport(user, session, input, agentRun.intent(), agentRun.assessment());
        }

        SeverityLevel severity = agentRun.severity() == null ? SeverityLevel.LOW : agentRun.severity();
        List<AiMessage> messages = agentRun.responseMessages().isEmpty()
                ? buildMessages(user, agentRun.intent(), severity, agentRun.retrievedKnowledge(), agentRun.modelHistory())
                : agentRun.responseMessages();
        Long reportId = report == null ? null : report.getId();
        return new PreparedConversation(user, session, agentRun.intent(), severity, messages, reportId);
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> streamPrepared(PreparedConversation prepared) {
        StringBuilder assistantReply = new StringBuilder();
        Flux<ServerSentEvent<ChatStreamEvent>> meta = Flux.just(event(
                "meta",
                ChatStreamEvent.meta(prepared.session().getPublicId())));

        Flux<ServerSentEvent<ChatStreamEvent>> tokens = aiClient.stream(prepared.messages())
                .doOnNext(assistantReply::append)
                .map(token -> event("token", ChatStreamEvent.token(prepared.session().getPublicId(), token)))
                .timeout(Duration.ofSeconds(45))
                .onErrorResume(exception -> Flux.just(event(
                        "error",
                        ChatStreamEvent.error(prepared.session().getPublicId(), "模型响应超时或失败，请稍后重试。"))))
                .switchIfEmpty(Flux.just(event(
                        "error",
                        ChatStreamEvent.error(prepared.session().getPublicId(), "模型没有返回内容，请稍后重试。"))));

        Mono<ServerSentEvent<ChatStreamEvent>> done = Mono.fromCallable(() -> {
            if (!assistantReply.isEmpty()) {
                saveMessage(prepared.user(), prepared.session(), MessageRole.ASSISTANT, assistantReply.toString());
            }
            // 工具链在模型回复完成后异步执行，不打断用户端正在进行的对话体验。
            if (prepared.reportId() != null) {
                toolOrchestrationService.handleAsync(prepared.reportId());
            }
            return event("done", ChatStreamEvent.done(prepared.session().getPublicId()));
        }).subscribeOn(Schedulers.boundedElastic());

        return meta.concatWith(tokens).concatWith(done);
    }

    private ChatSession resolveSession(UserAccount user, String publicId, String input) {
        if (publicId != null && !publicId.isBlank()) {
            return chatSessionRepository.findByPublicIdAndUser_Id(publicId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        }
        ChatSession session = new ChatSession();
        session.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        session.setUser(user);
        session.setTitle(input.length() > 36 ? input.substring(0, 36) : input);
        return chatSessionRepository.save(session);
    }

    private ChatMessage saveMessage(UserAccount user, ChatSession session, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setUser(user);
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        chatMessageRepository.save(message);
        session.touch();
        chatSessionRepository.save(session);
        shortTermMemoryService.append(session.getPublicId(), role, content);
        return message;
    }

    private void rememberUserProfile(UserAccount user, ChatSession session, String input, String memoryBrief) {
        try {
            userProfileMemoryService.rememberUserInput(user, session, input, memoryBrief);
        } catch (Exception exception) {
            log.debug("User profile memory update skipped: {}", exception.getMessage());
        }
    }

    private IncidentReport saveReport(
            UserAccount user,
            ChatSession session,
            String content,
            IntentType intent,
            IncidentAssessment assessment
    ) {
        IncidentReport report = new IncidentReport();
        report.setUser(user);
        report.setSession(session);
        report.setContent(content);
        report.setIntent(intent);
        report.setCategory(assessment.category());
        report.setSeverityScore(assessment.severityScore());
        report.setSeverity(assessment.severity());
        report.setConfidence(assessment.confidence());
        report.setSummary(assessment.summary());
        return reportRepository.save(report);
    }

    private List<AiMessage> buildMessages(
            UserAccount user,
            IntentType intent,
            SeverityLevel severity,
            List<SearchResult> retrieved,
            List<AiMessage> history
    ) {
        // 检索片段只作为系统上下文给模型使用，不直接展示后台评估信息给用户。
        String context = String.join("\n\n", retrieved.stream()
                .map(result -> "- [" + result.source() + "] " + result.content())
                .toList());
        List<AiMessage> messages = new ArrayList<>();
        messages.add(PromptTemplates.answerSystemPrompt(intent, severity, context, user.getDisplayName()));

        int limit = messageWindowLimit();
        history.stream()
                .skip(Math.max(0, history.size() - limit))
                .forEach(messages::add);
        return messages;
    }

    private int messageWindowLimit() {
        // history-limit 以轮次理解，这里乘 2 保留用户和助手两侧消息。
        return Math.max(2, properties.getChat().getHistoryLimit() * 2);
    }

    private ServerSentEvent<ChatStreamEvent> event(String name, ChatStreamEvent data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    private record PreparedConversation(
            UserAccount user,
            ChatSession session,
            IntentType intent,
            SeverityLevel severity,
            List<AiMessage> messages,
            Long reportId
    ) {
    }
}
