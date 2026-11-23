package com.opspilot.agent.service.agent;

import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import com.opspilot.agent.service.knowledge.KnowledgeService;
import com.opspilot.agent.service.knowledge.SearchResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 知识库 Agent。
 *
 * <p>只有运维咨询和故障场景才检索 Chroma/RAG，普通闲聊不会被强行转成故障诊断。</p>
 */
@Component
public class KnowledgeAgent implements OpsPilotAgent {

    private final KnowledgeService knowledgeService;
    private final OpsPilotProperties properties;
    private final AiClient aiClient;

    public KnowledgeAgent(KnowledgeService knowledgeService, OpsPilotProperties properties, AiClient aiClient) {
        this.knowledgeService = knowledgeService;
        this.properties = properties;
        this.aiClient = aiClient;
    }

    @Override
    public AgentName name() {
        return AgentName.KNOWLEDGE_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.intentRouted()
                && !context.knowledgeHandled()
                && context.intent() != IntentType.CHAT;
    }

    @Override
    public AgentDecision act(AgentContext context) {
        String query = rewriteQuery(context);
        List<SearchResult> retrieved = knowledgeService.retrieve(query, properties.getKnowledge().getTopK());
        String observation = "query=%s; retrieved=%d".formatted(query, retrieved.size());
        if (!isKnowledgeEnough(context, retrieved)) {
            String refinedQuery = refineQuery(context, query, retrieved);
            if (!refinedQuery.equals(query)) {
                List<SearchResult> refined = knowledgeService.retrieve(refinedQuery, properties.getKnowledge().getTopK());
                if (!refined.isEmpty()) {
                    query = refinedQuery;
                    retrieved = refined;
                    observation = "query=%s; refined=true; retrieved=%d".formatted(query, retrieved.size());
                }
            }
        }
        context.setKnowledgeQuery(query);
        context.setRetrievedKnowledge(retrieved);
        context.markKnowledgeHandled();
        return AgentDecision.continueWith(
                AgentAction.RETRIEVE_KNOWLEDGE,
                observation);
    }

    private String rewriteQuery(AgentContext context) {
        try {
            String query = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 OpsPilot 的 KnowledgeAgent。
                            你的任务是把用户输入改写成适合检索运维知识库的中文查询词。
                            只输出查询词本身，不要解释，不要超过 40 个字。
                            聚焦故障排查、部署配置、监控告警或应急处理知识。
                            """),
                    AiMessage.user("""
                            记忆摘要：
                            %s

                            当前输入：
                            %s
                            """.formatted(context.memoryBrief(), context.modelInput()))
            )).trim();
            return normalizeQuery(query, context.modelInput());
        } catch (Exception ignored) {
            return context.modelInput();
        }
    }

    private boolean isKnowledgeEnough(AgentContext context, List<SearchResult> results) {
        if (results.isEmpty()) {
            return false;
        }
        try {
            String decision = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 OpsPilot 的 KnowledgeAgent。
                            判断检索结果是否足以支持后续运维支持回答。
                            只输出 SUFFICIENT 或 INSUFFICIENT。
                            """),
                    AiMessage.user("""
                            当前输入：
                            %s

                            检索结果：
                            %s
                            """.formatted(context.modelInput(), formatResults(results)))
            )).trim().toUpperCase();
            return decision.contains("SUFFICIENT") && !decision.contains("INSUFFICIENT");
        } catch (Exception ignored) {
            return true;
        }
    }

    private String refineQuery(AgentContext context, String previousQuery, List<SearchResult> results) {
        try {
            String query = aiClient.complete(List.of(
                    AiMessage.system("""
                            你是 OpsPilot 的 KnowledgeAgent。
                            上一次检索信息不足，请给出一个新的、更具体的中文检索 query。
                            只输出查询词本身，不要解释，不要超过 40 个字。
                            """),
                    AiMessage.user("""
                            当前输入：
                            %s

                            上一次 query：
                            %s

                            上一次结果：
                            %s
                            """.formatted(context.modelInput(), previousQuery, formatResults(results)))
            )).trim();
            return normalizeQuery(query, previousQuery);
        } catch (Exception ignored) {
            return previousQuery;
        }
    }

    private String formatResults(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "无";
        }
        return String.join("\n", results.stream()
                .limit(4)
                .map(result -> "- " + result.content())
                .toList());
    }

    private String normalizeQuery(String value, String fallback) {
        String query = value
                .replace("查询词：", "")
                .replace("query:", "")
                .replace("Query:", "")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        if (query.isBlank()) {
            return fallback;
        }
        return query.length() > 60 ? query.substring(0, 60) : query;
    }
}
