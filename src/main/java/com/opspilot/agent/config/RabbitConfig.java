package com.opspilot.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 工具链队列配置。
 *
 * <p>主队列 opspilot-tool-queue 负责承载后台工具任务；处理失败的消息经死信交换机
 * opspilot-tool-dlx 路由到死信队列 opspilot-tool-queue.dlq，供人工排查。</p>
 */
@Configuration
public class RabbitConfig {

    public static final String TOOL_QUEUE = "opspilot-tool-queue";
    public static final String TOOL_DLX = "opspilot-tool-dlx";
    public static final String TOOL_DLQ = "opspilot-tool-queue.dlq";

    @Bean
    public Queue toolQueue() {
        return QueueBuilder.durable(TOOL_QUEUE)
                .deadLetterExchange(TOOL_DLX)
                .build();
    }

    @Bean
    public DirectExchange toolDlx() {
        return new DirectExchange(TOOL_DLX);
    }

    @Bean
    public Queue toolDlq() {
        return QueueBuilder.durable(TOOL_DLQ).build();
    }

    @Bean
    public Binding toolDlqBinding(Queue toolDlq, DirectExchange toolDlx) {
        return BindingBuilder.bind(toolDlq).to(toolDlx).with(TOOL_QUEUE);
    }
}
