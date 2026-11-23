package com.opspilot.agent.service;

import com.opspilot.agent.config.RabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 工具队列消费者。
 *
 * <p>消费故障报告 id，交给 {@link ToolOrchestrationService} 做幂等、限流和工具执行；
 * 处理抛异常时消息不会被重新入队，而是进入死信队列。</p>
 */
@Component
public class ToolQueueConsumer {

    private final ToolOrchestrationService toolOrchestrationService;

    public ToolQueueConsumer(ToolOrchestrationService toolOrchestrationService) {
        this.toolOrchestrationService = toolOrchestrationService;
    }

    @RabbitListener(queues = RabbitConfig.TOOL_QUEUE)
    public void onToolTask(Long reportId) {
        toolOrchestrationService.process(reportId);
    }
}
