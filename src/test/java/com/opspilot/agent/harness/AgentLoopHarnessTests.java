package com.opspilot.agent.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.ChatSession;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.domain.SeverityLevel;
import com.opspilot.agent.domain.UserAccount;
import com.opspilot.agent.repository.ChatMessageRepository;
import com.opspilot.agent.service.IntentClassifier;
import com.opspilot.agent.service.PrivacySanitizer;
import com.opspilot.agent.service.IncidentAssessmentService;
import com.opspilot.agent.service.agent.AgentName;
import com.opspilot.agent.service.agent.AgentRunResult;
import com.opspilot.agent.service.agent.AgentRuntimeService;
import com.opspilot.agent.service.agent.AgentStep;
import com.opspilot.agent.service.agent.GeneralAgent;
import com.opspilot.agent.service.agent.OpsSupportAgent;
import com.opspilot.agent.service.agent.KnowledgeAgent;
import com.opspilot.agent.service.agent.MemoryAgent;
import com.opspilot.agent.service.agent.IncidentGuardianAgent;
import com.opspilot.agent.service.agent.SupervisorAgent;
import com.opspilot.agent.service.knowledge.KnowledgeService;
import com.opspilot.agent.service.knowledge.SearchResult;
import com.opspilot.agent.service.memory.ShortTermMemoryService;
import com.opspilot.agent.service.memory.UserProfileMemoryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentLoopHarnessTests {

    private ScriptedAiClient aiClient;
    private KnowledgeService knowledgeService;
    private AgentRuntimeService runtimeService;

    @BeforeEach
    void setUp() {
        aiClient = new ScriptedAiClient();
        OpsPilotProperties properties = new OpsPilotProperties();
        properties.getKnowledge().setTopK(2);
        properties.getKnowledge().setRerankerEnabled(false);

        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        ShortTermMemoryService shortTermMemoryService = mock(ShortTermMemoryService.class);
        UserProfileMemoryService userProfileMemoryService = mock(UserProfileMemoryService.class);
        knowledgeService = mock(KnowledgeService.class);

        when(shortTermMemoryService.recent(anyString())).thenReturn(List.of());
        when(userProfileMemoryService.profileBrief(any(UserAccount.class), anyString()))
                .thenReturn("无已保存用户画像。");
        when(knowledgeService.retrieve(anyString(), anyInt())).thenReturn(List.of(
                new SearchResult(
                        1L,
                        "incident-response.md",
                        "HIGH production incident: 先止损，再升级 OnCall，回滚最近一次发布或切换流量。",
                        0.95),
                new SearchResult(
                        2L,
                        "monitoring-alerting.md",
                        "高延迟先看调用链定位慢在哪一跳，再查数据库慢查询和连接池。",
                        0.87)));

        MemoryAgent memoryAgent = new MemoryAgent(
                chatMessageRepository,
                shortTermMemoryService,
                properties,
                new PrivacySanitizer(),
                aiClient,
                userProfileMemoryService);
        SupervisorAgent supervisorAgent = new SupervisorAgent(new IntentClassifier(aiClient));
        KnowledgeAgent knowledgeAgent = new KnowledgeAgent(knowledgeService, properties, aiClient);
        IncidentGuardianAgent incidentGuardianAgent = new IncidentGuardianAgent(
                new IncidentAssessmentService(aiClient, new ObjectMapper()));
        GeneralAgent generalAgent = new GeneralAgent(aiClient);
        OpsSupportAgent opsSupportAgent = new OpsSupportAgent(aiClient);

        runtimeService = new AgentRuntimeService(
                memoryAgent,
                supervisorAgent,
                knowledgeAgent,
                incidentGuardianAgent,
                generalAgent,
                opsSupportAgent);
    }

    @Test
    void chatRouteSkipsRagAndIncidentGuardian() {
        AgentRunResult result = run("帮我解释一下 Java 多线程。");

        assertThat(result.intent()).isEqualTo(IntentType.CHAT);
        assertThat(result.severity()).isEqualTo(SeverityLevel.LOW);
        assertThat(result.requiresReport()).isFalse();
        assertThat(result.retrievedKnowledge()).isEmpty();
        assertThat(result.responseAgent()).isEqualTo(AgentName.GENERAL_AGENT);
        assertThat(agentNames(result)).containsExactly(
                AgentName.MEMORY_AGENT,
                AgentName.SUPERVISOR_AGENT,
                AgentName.GENERAL_AGENT);
    }

    @Test
    void consultRouteUsesKnowledgeIncidentGuardianAndOpsSupport() {
        AgentRunResult result = run("Redis 连接超时，帮忙排查一下。");

        assertThat(result.intent()).isEqualTo(IntentType.CONSULT);
        assertThat(result.severity()).isEqualTo(SeverityLevel.LOW);
        assertThat(result.requiresReport()).isTrue();
        assertThat(result.retrievedKnowledge()).extracting(SearchResult::source)
                .contains("monitoring-alerting.md");
        assertThat(result.responseAgent()).isEqualTo(AgentName.OPS_SUPPORT_AGENT);
        assertThat(agentNames(result)).containsExactly(
                AgentName.MEMORY_AGENT,
                AgentName.SUPERVISOR_AGENT,
                AgentName.KNOWLEDGE_AGENT,
                AgentName.INCIDENT_GUARDIAN_AGENT,
                AgentName.OPS_SUPPORT_AGENT);
    }

    @Test
    void highIncidentRouteEscalatesToHighAndKeepsOpsSupportPath() {
        AgentRunResult result = run("线上支付系统宕机了，订单大量失败。");

        assertThat(result.intent()).isEqualTo(IntentType.INCIDENT);
        assertThat(result.severity()).isEqualTo(SeverityLevel.HIGH);
        assertThat(result.assessment().severity()).isEqualTo(SeverityLevel.HIGH);
        assertThat(result.requiresReport()).isTrue();
        assertThat(result.responseAgent()).isEqualTo(AgentName.OPS_SUPPORT_AGENT);
        assertThat(agentNames(result)).containsExactly(
                AgentName.MEMORY_AGENT,
                AgentName.SUPERVISOR_AGENT,
                AgentName.KNOWLEDGE_AGENT,
                AgentName.INCIDENT_GUARDIAN_AGENT,
                AgentName.OPS_SUPPORT_AGENT);
    }

    private AgentRunResult run(String input) {
        return runtimeService.run(user(), session(), input, input);
    }

    private UserAccount user() {
        UserAccount user = new UserAccount();
        user.setUsername("engineer");
        user.setDisplayName("Demo Engineer");
        return user;
    }

    private ChatSession session() {
        ChatSession session = new ChatSession();
        session.setPublicId("harness-session");
        session.setTitle("Harness session");
        return session;
    }

    private List<AgentName> agentNames(AgentRunResult result) {
        return result.steps().stream()
                .map(AgentStep::agent)
                .toList();
    }
}
