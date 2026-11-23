package com.opspilot.agent.config;

import com.opspilot.agent.domain.UserAccount;
import com.opspilot.agent.repository.UserAccountRepository;
import com.opspilot.agent.service.knowledge.KnowledgeIngestionService;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeIngestionService knowledgeIngestionService;

    public DataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            KnowledgeIngestionService knowledgeIngestionService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 首次启动准备演示账号；内置知识库会按 source 补齐或刷新。
        seedUsers();
        knowledgeIngestionService.syncClasspathKnowledge();
    }

    private void seedUsers() {
        if (userAccountRepository.count() > 0) {
            return;
        }
        // 管理员账号用于后台查看，工程师账号用于正常对话体验。
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setDisplayName("Ops Admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
        userAccountRepository.save(admin);

        UserAccount engineer = new UserAccount();
        engineer.setUsername("engineer");
        engineer.setDisplayName("Demo Engineer");
        engineer.setPassword(passwordEncoder.encode("engineer123"));
        engineer.setRoles(Set.of("ROLE_USER"));
        userAccountRepository.save(engineer);
    }
}
