package com.opspilot.agent;

import com.opspilot.agent.config.OpsPilotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * OpsPilot 后端启动入口。
 *
 * <p>应用启动后会加载配置、初始化演示账号和知识库，并开放聊天、后台记录、知识库上传等接口。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(OpsPilotProperties.class)
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
