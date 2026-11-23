package com.opspilot.agent.config;

import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.SpringAiChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 大模型客户端装配配置。
 *
 * <p>根据 application.yml 或环境变量选择本地项目模型或 OpenAI 客户端。
 * 这里同时暴露 {@link ChatModel}，供需要函数调用（function calling）的服务直接使用。</p>
 */
@Configuration
public class AiClientConfig {

    @Bean
    public ChatModel chatModel(OpsPilotProperties properties) {
        String provider = properties.getAi().getProvider().toLowerCase();
        if ("ollama".equals(provider)) {
            return ollamaChatModel(properties);
        }
        if ("openai".equals(provider)) {
            if (properties.getAi().getOpenai().getApiKey().isBlank()) {
                throw new IllegalStateException("AI_PROVIDER=openai requires OPENAI_API_KEY.");
            }
            return openAiChatModel(properties);
        }
        throw new IllegalArgumentException(
                "Unsupported AI_PROVIDER=" + provider + ". Supported providers: ollama, openai.");
    }

    @Bean
    public AiClient aiClient(ChatModel chatModel) {
        // OllamaChatModel 与 OpenAiChatModel 都同时实现了 StreamingChatModel。
        return new SpringAiChatClient(chatModel, (StreamingChatModel) chatModel);
    }

    private OllamaChatModel ollamaChatModel(OpsPilotProperties properties) {
        OpsPilotProperties.Ollama ollama = properties.getAi().getOllama();
        OllamaApi api = OllamaApi.builder()
                .baseUrl(ollama.getBaseUrl())
                .build();
        OllamaOptions options = OllamaOptions.builder()
                .model(ollama.getModel())
                .temperature(properties.getAi().getTemperature())
                .numPredict(properties.getAi().getMaxTokens())
                .topP(0.85)
                .repeatPenalty(1.12)
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
    }

    private OpenAiChatModel openAiChatModel(OpsPilotProperties properties) {
        OpsPilotProperties.OpenAi openai = properties.getAi().getOpenai();
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(openai.getBaseUrl())
                .apiKey(openai.getApiKey())
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(openai.getModel())
                .temperature(properties.getAi().getTemperature())
                .maxTokens(properties.getAi().getMaxTokens())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }
}
