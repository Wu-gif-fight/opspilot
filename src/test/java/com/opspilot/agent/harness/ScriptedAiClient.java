package com.opspilot.agent.harness;

import com.opspilot.agent.service.ai.AiClient;
import com.opspilot.agent.service.ai.AiMessage;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;

final class ScriptedAiClient implements AiClient {

    private final List<String> completePrompts = new CopyOnWriteArrayList<>();
    private final List<String> streamPrompts = new CopyOnWriteArrayList<>();

    @Override
    public String complete(List<AiMessage> messages) {
        String prompt = joined(messages);
        completePrompts.add(prompt);
        String normalized = prompt.toLowerCase(Locale.ROOT);
        if (prompt.contains("用户意图分类器")) {
            return classify(normalized);
        }
        if (prompt.contains("电商运维平台的消息")) {
            return assessment(normalized);
        }
        if (prompt.contains("MemoryAgent")) {
            return "无相关历史记忆。";
        }
        if (prompt.contains("用户画像记忆抽取器")) {
            return "[]";
        }
        if (prompt.contains("KnowledgeAgent") && prompt.contains("改写成适合检索")) {
            return "故障排查 监控 告警 部署";
        }
        if (prompt.contains("判断检索结果是否足以")) {
            return "SUFFICIENT";
        }
        if (prompt.contains("RAG reranker")) {
            return "[{\"index\":1,\"score\":0.99},{\"index\":2,\"score\":0.70}]";
        }
        if (prompt.contains("GeneralAgent")) {
            return "直接回答用户的普通问题，不引导成故障诊断。";
        }
        if (prompt.contains("OpsSupportAgent")) {
            return "先复述问题，再给出排查步骤；高级别告警时优先止损。";
        }
        if (prompt.contains("RAG 回答生成器")) {
            return ragAnswer(normalized);
        }
        return "好的，我会稳妥回应。";
    }

    @Override
    public Flux<String> stream(List<AiMessage> messages) {
        String prompt = joined(messages);
        streamPrompts.add(prompt);
        String normalized = lastUserInput(prompt);
        if (containsAny(normalized, "宕机", "支付失败", "订单失败", "数据丢失", "线上事故")) {
            return Flux.just(
                    "先止损，",
                    "请回滚最近一次发布或切换到备用实例，",
                    "并立即通知值班负责人升级 OnCall。");
        }
        if (containsAny(normalized, "超时", "报错", "排查", "监控", "告警")) {
            return Flux.just("先确认影响范围，", "再查看监控和日志定位慢在哪一跳，", "按故障排查手册处理。");
        }
        return Flux.just("这是一个稳定的测试回复。");
    }

    List<String> completePrompts() {
        return List.copyOf(completePrompts);
    }

    List<String> streamPrompts() {
        return List.copyOf(streamPrompts);
    }

    private String classify(String prompt) {
        String currentInput = currentInput(prompt);
        String recentContext = recentContext(prompt);
        if (containsAny(currentInput, "宕机", "支付失败", "订单失败", "数据丢失", "生产事故", "线上事故")) {
            return "INCIDENT";
        }
        if (containsAny(currentInput, "排查", "超时", "报错", "部署", "配置", "监控", "告警", "异常", "数据库")
                || containsAny(recentContext, "排查", "超时", "报错", "部署", "配置", "监控", "告警", "异常", "数据库")) {
            return "CONSULT";
        }
        return "CHAT";
    }

    private String assessment(String prompt) {
        String currentInput = currentInput(prompt);
        if (containsAny(currentInput, "宕机", "支付失败", "订单失败", "数据丢失", "生产事故", "线上事故")) {
            return """
                    {"category":"CRITICAL","severityScore":4.0,"severity":"HIGH","confidence":0.96,"summary":"明确生产事故信号。"}
                    """;
        }
        if (containsAny(currentInput, "数据库", "redis", "慢查询")) {
            return """
                    {"category":"DATABASE","severityScore":2.5,"severity":"LOW","confidence":0.82,"summary":"数据库相关问题。"}
                    """;
        }
        if (containsAny(currentInput, "报错", "异常", "超时", "排查", "部署", "配置", "监控", "告警")) {
            return """
                    {"category":"APPLICATION","severityScore":2.2,"severity":"LOW","confidence":0.78,"summary":"应用异常或超时。"}
                    """;
        }
        return """
                {"category":"NORMAL","severityScore":0.0,"severity":"LOW","confidence":0.70,"summary":"无明显故障信号。"}
                """;
    }

    private String ragAnswer(String prompt) {
        String question = userQuestion(prompt);
        if (containsAny(question, "宕机", "支付失败", "订单失败", "数据丢失")) {
            return "先把故障止损放在第一位：请回滚最近一次发布或切换到备用实例，并立即通知值班负责人升级 OnCall。";
        }
        if (containsAny(question, "超时", "报错", "排查")) {
            return "先确认影响范围，再查看监控大盘和调用链，按故障排查手册定位慢在哪一跳。";
        }
        return "先确认影响范围和发生时间，再按监控告警与故障排查手册逐步定位。";
    }

    private String joined(List<AiMessage> messages) {
        return String.join("\n", messages.stream()
                .map(message -> message.role() + ": " + message.content())
                .toList());
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String currentInput(String prompt) {
        return sectionAfter(prompt, "当前输入：");
    }

    private String lastUserInput(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        int lastUser = lower.lastIndexOf("user: ");
        if (lastUser < 0) {
            return lower;
        }
        return lower.substring(lastUser + "user: ".length());
    }

    private String recentContext(String prompt) {
        int start = prompt.lastIndexOf("最近上下文：");
        int end = prompt.lastIndexOf("当前输入：");
        if (start < 0 || end <= start) {
            return "";
        }
        return prompt.substring(start + "最近上下文：".length(), end)
                .toLowerCase(Locale.ROOT);
    }

    private String userQuestion(String prompt) {
        return sectionAfter(prompt, "用户问题：");
    }

    private String sectionAfter(String prompt, String marker) {
        int start = prompt.lastIndexOf(marker);
        if (start < 0) {
            return prompt;
        }
        String section = prompt.substring(start + marker.length());
        int nextBlankLine = section.indexOf("\n\n");
        if (nextBlankLine >= 0) {
            section = section.substring(0, nextBlankLine);
        }
        return section.toLowerCase(Locale.ROOT).trim();
    }
}
