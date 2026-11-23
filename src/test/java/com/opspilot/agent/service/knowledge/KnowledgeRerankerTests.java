package com.opspilot.agent.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opspilot.agent.config.OpsPilotProperties;
import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class KnowledgeRerankerTests {

    @Test
    void reranksCandidatesUsingModelScores() {
        KnowledgeReranker reranker = new KnowledgeReranker(
                properties(),
                new StubAiClient("""
                        [
                          {"index": 1, "score": 0.10},
                          {"index": 2, "score": 0.98},
                          {"index": 3, "score": 0.40}
                        ]
                        """),
                new ObjectMapper());

        List<SearchResult> results = reranker.rerank("线上服务延迟升高怎么排查", List.of(
                new SearchResult(1L, "a.md", "故障排查手册", 0.90),
                new SearchResult(2L, "b.md", "调用链定位和慢查询优化", 0.80),
                new SearchResult(3L, "c.md", "部署发布与回滚", 0.20)
        ), 2);

        assertThat(results)
                .extracting(SearchResult::chunkId)
                .containsExactly(2L, 3L);
    }

    @Test
    void fallsBackToInitialOrderWhenModelOutputIsInvalid() {
        KnowledgeReranker reranker = new KnowledgeReranker(
                properties(),
                new StubAiClient("我觉得第二条更相关"),
                new ObjectMapper());

        List<SearchResult> results = reranker.rerank("数据库慢查询", List.of(
                new SearchResult(1L, "a.md", "初排第一", 0.90),
                new SearchResult(2L, "b.md", "初排第二", 0.80),
                new SearchResult(3L, "c.md", "初排第三", 0.70)
        ), 2);

        assertThat(results)
                .extracting(SearchResult::chunkId)
                .containsExactly(1L, 2L);
    }

    private OpsPilotProperties properties() {
        OpsPilotProperties properties = new OpsPilotProperties();
        properties.getKnowledge().setRerankerEnabled(true);
        properties.getKnowledge().setRerankerCandidateLimit(3);
        properties.getKnowledge().setRerankerMaxContentChars(200);
        return properties;
    }

    private static class StubAiClient implements AiClient {

        private final String response;

        private StubAiClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(List<AiMessage> messages) {
            return response;
        }

        @Override
        public Flux<String> stream(List<AiMessage> messages) {
            return Flux.empty();
        }
    }
}
