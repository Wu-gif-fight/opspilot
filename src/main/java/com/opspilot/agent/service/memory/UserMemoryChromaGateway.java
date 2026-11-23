package com.opspilot.agent.service.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.ChatSession;
import com.opspilot.agent.domain.UserMemoryItem;
import com.opspilot.agent.service.knowledge.EmbeddingClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
/**
 * 用户画像记忆 Chroma 网关。
 *
 * <p>MySQL/H2 保存可审计的画像条目；Chroma 只保存语义检索索引，
 * 用于按当前输入召回相关长期记忆。</p>
 */
public class UserMemoryChromaGateway {

    private final OpsPilotProperties properties;
    private final WebClient webClient;
    private final EmbeddingClient embeddingClient;
    private volatile boolean collectionEnsured;

    public UserMemoryChromaGateway(
            OpsPilotProperties properties,
            WebClient.Builder webClientBuilder,
            EmbeddingClient embeddingClient
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getMemory().getChromaBaseUrl()).build();
        this.embeddingClient = embeddingClient;
    }

    public void mirror(UserMemoryItem item) {
        if (!properties.getMemory().isUseChroma() || item.getId() == null) {
            return;
        }
        ensureCollection();
        delete(item.getId());
        List<Double> embedding = safeEmbed(document(item));
        // 有向量时显式写入，保证画像记忆的向量空间和查询一致。
        Map<String, Object> body = embedding.isEmpty()
                ? Map.of(
                        "ids", List.of(chromaId(item.getId())),
                        "documents", List.of(document(item)),
                        "metadatas", List.of(metadata(item)))
                : Map.of(
                        "ids", List.of(chromaId(item.getId())),
                        "documents", List.of(document(item)),
                        "embeddings", List.of(embedding),
                        "metadatas", List.of(metadata(item)));
        webClient.post()
                .uri("/api/v1/collections/{collection}/add", properties.getMemory().getChromaCollection())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .onErrorComplete()
                .block();
    }

    public List<UserMemoryMatch> query(Long userId, String text, int topK) {
        if (!properties.getMemory().isUseChroma() || userId == null || text == null || text.isBlank()) {
            return List.of();
        }
        ensureCollection();
        List<Double> queryEmbedding = safeEmbed(text);
        // 查询向量优先走 query_embeddings，和写入时的 embeddings 保持同一向量空间。
        Map<String, Object> body = queryEmbedding.isEmpty()
                ? Map.of(
                        "query_texts", List.of(text),
                        "n_results", Math.max(1, topK),
                        "where", Map.of("userId", String.valueOf(userId)),
                        "include", List.of("metadatas", "distances"))
                : Map.of(
                        "query_embeddings", List.of(queryEmbedding),
                        "n_results", Math.max(1, topK),
                        "where", Map.of("userId", String.valueOf(userId)),
                        "include", List.of("metadatas", "distances"));
        try {
            JsonNode response = webClient.post()
                    .uri("/api/v1/collections/{collection}/query", properties.getMemory().getChromaCollection())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return parseMatches(response);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public void delete(Long memoryId) {
        if (!properties.getMemory().isUseChroma() || memoryId == null) {
            return;
        }
        ensureCollection();
        webClient.post()
                .uri("/api/v1/collections/{collection}/delete", properties.getMemory().getChromaCollection())
                .bodyValue(Map.of("ids", List.of(chromaId(memoryId))))
                .retrieve()
                .toBodilessEntity()
                .onErrorComplete()
                .block();
    }

    private List<Double> safeEmbed(String text) {
        try {
            return embeddingClient.embed(text);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<UserMemoryMatch> parseMatches(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        List<UserMemoryMatch> matches = new ArrayList<>();
        JsonNode metadatas = response.path("metadatas").path(0);
        JsonNode distances = response.path("distances").path(0);
        for (int i = 0; i < metadatas.size(); i++) {
            Long memoryId = parseLong(metadatas.path(i).path("memoryId").asText());
            if (memoryId != null) {
                double score = 1.0 - distances.path(i).asDouble(1.0);
                matches.add(new UserMemoryMatch(memoryId, score));
            }
        }
        return matches;
    }

    private Map<String, Object> metadata(UserMemoryItem item) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("memoryId", String.valueOf(item.getId()));
        metadata.put("userId", String.valueOf(item.getUser().getId()));
        metadata.put("type", item.getType().name());
        ChatSession session = item.getSourceSession();
        if (session != null && session.getPublicId() != null) {
            metadata.put("sourceSessionId", session.getPublicId());
        }
        return metadata;
    }

    private String document(UserMemoryItem item) {
        String evidence = item.getEvidence() == null || item.getEvidence().isBlank()
                ? ""
                : "\n证据：" + item.getEvidence();
        return "%s：%s%s".formatted(item.getType().name(), item.getSummary(), evidence);
    }

    private String chromaId(Long memoryId) {
        return "memory:" + memoryId;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureCollection() {
        if (collectionEnsured) {
            return;
        }
        try {
            webClient.post()
                    .uri("/api/v1/collections")
                    .bodyValue(Map.of("name", properties.getMemory().getChromaCollection()))
                    .retrieve()
                    .toBodilessEntity()
                    .onErrorComplete()
                    .block();
        } finally {
            collectionEnsured = true;
        }
    }

    public record UserMemoryMatch(Long memoryId, double score) {
    }
}
