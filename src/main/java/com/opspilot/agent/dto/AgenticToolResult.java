package com.opspilot.agent.dto;

import java.util.List;

/**
 * 模型自主函数调用结果。
 *
 * @param answer 最终回答
 * @param toolCalls 本次调用中模型自主调用的工具及结果（用于演示与调试）
 */
public record AgenticToolResult(String answer, List<String> toolCalls) {
}
