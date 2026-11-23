package com.opspilot.agent.service.agent;

/**
 * OpsPilot 内部专业智能体名称。
 *
 * <p>多智能体协作采用 Supervisor 统筹、专家 Agent 分工的方式，避免把所有职责继续堆在一个服务里。</p>
 */
public enum AgentName {
    MEMORY_AGENT,
    SUPERVISOR_AGENT,
    KNOWLEDGE_AGENT,
    INCIDENT_GUARDIAN_AGENT,
    GENERAL_AGENT,
    OPS_SUPPORT_AGENT
}
