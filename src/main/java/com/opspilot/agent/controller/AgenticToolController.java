package com.opspilot.agent.controller;

import com.opspilot.agent.dto.AgenticToolResult;
import com.opspilot.agent.dto.ChatRequest;
import com.opspilot.agent.service.tools.AgenticToolService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型自主函数调用接口。
 *
 * <p>演示「大模型自主决定调用工具」的路径，与规则驱动的 /api/chat/stream 并存。</p>
 */
@RestController
@RequestMapping("/api/agentic")
public class AgenticToolController {

    private final AgenticToolService agenticToolService;

    public AgenticToolController(AgenticToolService agenticToolService) {
        this.agenticToolService = agenticToolService;
    }

    @PostMapping("/chat")
    public AgenticToolResult chat(@Valid @RequestBody ChatRequest request) {
        return agenticToolService.run(request.message());
    }
}
