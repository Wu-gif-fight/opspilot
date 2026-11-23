package com.opspilot.agent.service.tools;

import com.opspilot.agent.dto.AgenticToolResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * 模型自主函数调用服务。
 *
 * <p>与规则驱动工具链（{@code ToolOrchestrationService}）并存：这里让大模型自己决定
 * 是否调用工具（查询知识库、统计告警、创建工单），适用于开放意图的场景；而告警升级
 * 等确定性动作仍由规则链按固定顺序执行。模型输出工具调用后由本服务手动执行并回填。</p>
 */
@Service
public class AgenticToolService {

    private static final int MAX_TOOL_ITERATIONS = 3;

    private final ChatModel chatModel;
    private final List<ToolCallback> toolCallbacks;

    public AgenticToolService(ChatModel chatModel, OpsPilotFunctionTools tools) {
        this.chatModel = chatModel;
        this.toolCallbacks = List.of(MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build()
                .getToolCallbacks());
    }

    public AgenticToolResult run(String userInput) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                你是 OpsPilot 的运维助手。你可以调用工具来查询运维知识库、统计高级别告警、创建值班工单。
                当用户的问题需要查询实时数据或执行某个动作时，调用对应工具；工具返回结果后，基于结果给出清晰简洁的中文回答。
                如果不需要任何工具，直接回答。
                """));
        messages.add(new UserMessage(userInput));

        OpenAiChatOptions options = new OpenAiChatOptions();
        options.setToolCallbacks(toolCallbacks);
        // 关闭模型内部自动执行，改由服务端手动执行，以便记录工具调用轨迹。
        options.setInternalToolExecutionEnabled(false);

        List<String> toolCallsLog = new ArrayList<>();
        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            AssistantMessage output = response.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                return new AgenticToolResult(output.getText(), toolCallsLog);
            }
            messages.add(output);
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : toolCalls) {
                String result = executeTool(toolCall.name(), toolCall.arguments());
                toolCallsLog.add(toolCall.name() + "(" + toolCall.arguments() + ") -> " + result);
                responses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), result));
            }
            messages.add(new ToolResponseMessage(responses));
        }
        return new AgenticToolResult("工具调用轮次达到上限，请简化问题后重试。", toolCallsLog);
    }

    private String executeTool(String name, String arguments) {
        for (ToolCallback callback : toolCallbacks) {
            if (callback.getToolDefinition().name().equals(name)) {
                try {
                    return callback.call(arguments);
                } catch (Exception exception) {
                    return "工具执行失败：" + exception.getMessage();
                }
            }
        }
        return "未找到工具：" + name;
    }
}
