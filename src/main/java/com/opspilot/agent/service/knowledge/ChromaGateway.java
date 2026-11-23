package com.opspilot.agent.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.domain.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
/**
 * Chroma 向量库网关。
 *
 * <p>当 use-chroma=true 时，把知识库切块镜像到外部向量库，并优先从 Chroma 检索。</p>
 */
public class ChromaGateway {

    private final OpsPilotProperties properties;
    private final WebClient webClient;
    private final EmbeddingClient embeddingClient;
    private volatile boolean collectionEnsured;

    public ChromaGateway(
            OpsPilotProperties properties,
            WebClient.Builder webClientBuilder,
            EmbeddingClient embeddingClient
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getKnowledge().getChromaBaseUrl()).build();
        this.embeddingClient = embeddingClient;
    }

    public void mirror(KnowledgeChunk chunk, List<Double> embedding) {
        if (!properties.getKnowledge().isUseChroma()) {
            return;
        }
        // 本地数据库仍是主存储；Chroma 只是可选检索加速层。
        ensureCollection();
        Map<String, Object> metadata = Map.of(
                "source", chunk.getSource(),
                "sourceIndex", chunk.getSourceIndex());
        // 有向量时显式写入，保证 Chroma 内向量和查询向量来自同一 embedding 模型；
        // 没有向量时退回 documents，由 Chroma 服务端默认 embedding function 兜底。
        Map<String, Object> body = embedding == null || embedding.isEmpty()
                ? Map.of(
                        "ids", List.of(String.valueOf(chunk.getId())),
                        "documents", List.of(chunk.getContent()),
                        "metadatas", List.of(metadata))
                : Map.of(
                        "ids", List.of(String.valueOf(chunk.getId())),
                        "documents", List.of(chunk.getContent()),
                        "embeddings", List.of(embedding),
                        "metadatas", List.of(metadata));
        webClient.post()
                .uri("/api/v1/collections/{collection}/add", properties.getKnowledge().getChromaCollection())
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .onErrorComplete()
                .block();
    }

    public List<SearchResult> query(String text, int topK) {
        if (!properties.getKnowledge().isUseChroma()) {
            return List.of();
        }
        ensureCollection();
        List<Double> queryEmbedding = safeEmbed(text);
        // 查询向量优先走 query_embeddings，和写入时的 embeddings 保持同一向量空间。
        Map<String, Object> body = queryEmbedding.isEmpty()
                ? Map.of(
                        "query_texts", List.of(text),
                        "n_results", topK,
                        "include", List.of("documents", "metadatas", "distances"))
                : Map.of(
                        "query_embeddings", List.of(queryEmbedding),
                        "n_results", topK,
                        "include", List.of("documents", "metadatas", "distances"));
        try {
            JsonNode response = webClient.post()
                    .uri("/api/v1/collections/{collection}/query", properties.getKnowledge().getChromaCollection())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return parseResults(response);
        } catch (Exception ignored) {
            // 外部向量库不可用时返回空结果，让 KnowledgeService 回退到本地检索。
            return List.of();
        }
    }

    public void deleteSource(String source) {
        if (!properties.getKnowledge().isUseChroma()) {
            return;
        }
        ensureCollection();
        Map<String, Object> body = Map.of("where", Map.of("source", source));
        webClient.post()
                .uri("/api/v1/collections/{collection}/delete", properties.getKnowledge().getChromaCollection())
                .bodyValue(body)
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

    private List<SearchResult> parseResults(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        JsonNode docs = response.path("documents").path(0);
        JsonNode ids = response.path("ids").path(0);
        JsonNode metadatas = response.path("metadatas").path(0);
        JsonNode distances = response.path("distances").path(0);
        for (int i = 0; i < docs.size(); i++) {
            Long id = parseId(ids.path(i).asText());
            double score = 1.0 - distances.path(i).asDouble(1.0);
            String source = metadatas.path(i).path("source").asText("chroma");
            results.add(new SearchResult(id, source, docs.path(i).asText(), score));
        }
        return results;
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
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
                    .bodyValue(Map.of("name", properties.getKnowledge().getChromaCollection()))
                    .retrieve()
                    .toBodilessEntity()
                    .onErrorComplete()
                    .block();
        } finally {
            collectionEnsured = true;
        }
    }
}
