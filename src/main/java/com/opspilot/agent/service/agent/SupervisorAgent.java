package com.opspilot.agent.service.agent;

import com.opspilot.agent.domain.IntentType;
import com.opspilot.agent.service.IntentClassifier;
import org.springframework.stereotype.Component;

/**
 * 主控 Agent。
 *
 * <p>Supervisor 负责把本轮输入路由到普通闲聊、运维咨询或故障守护链路。</p>
 */
@Component
public class SupervisorAgent implements OpsPilotAgent {

    private final IntentClassifier intentClassifier;

    public SupervisorAgent(IntentClassifier intentClassifier) {
        this.intentClassifier = intentClassifier;
    }

    @Override
    public AgentName name() {
        return AgentName.SUPERVISOR_AGENT;
    }

    @Override
    public boolean supports(AgentContext context) {
        return context.memoryLoaded() && !context.intentRouted();
    }

    @Override
    public AgentDecision act(AgentContext context) {
        IntentType intent = intentClassifier.classify(context.modelInput(), context.modelHistory());
        context.setIntent(intent);
        context.markIntentRouted();
        if (intent == IntentType.CHAT) {
            context.markKnowledgeHandled();
            context.markIncidentAssessed();
        }
        return AgentDecision.continueWith(AgentAction.ROUTE_INTENT, "intent=%s".formatted(intent));
    }
}
