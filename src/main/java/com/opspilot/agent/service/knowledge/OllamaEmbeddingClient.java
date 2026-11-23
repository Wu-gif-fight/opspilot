package com.opspilot.agent.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.opspilot.agent.config.OpsPilotProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 本地 Ollama embedding 客户端。
 *
 * <p>调用 Ollama 的 {@code /api/embed} 接口把文本转成向量。当 DeepSeek 这类
 * OpenAI 兼容但未提供 embedding 接口的生成模型被启用时，用它补齐向量检索能力。</p>
 */
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final OpsPilotProperties properties;
    private final WebClient webClient;

    public OllamaEmbeddingClient(OpsPilotProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getEmbedding().getOllama().getBaseUrl()).build();
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", properties.getEmbedding().getOllama().getModel(),
                    "input", text);
            JsonNode response = webClient.post()
                    .uri("/api/embed")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            JsonNode embedding = response == null ? null : response.path("embeddings").path(0);
            if (embedding == null || !embedding.isArray()) {
                return List.of();
            }
            List<Double> values = new ArrayList<>(embedding.size());
            embedding.forEach(value -> values.add(value.asDouble()));
            return values;
        } catch (Exception ignored) {
            // Ollama 未启动、模型未拉取或响应异常时返回空向量，交给检索层回退。
            return List.of();
        }
    }

    @Override
    public String modelName() {
        return properties.getEmbedding().getOllama().getModel();
    }
}
