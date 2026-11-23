package com.opspilot.agent.domain;

/**
 * 用户输入的业务意图。
 *
 * <p>CHAT 走普通对话，CONSULT/INCIDENT 才进入运维支持、RAG 和告警链路。</p>
 */
public enum IntentType {
    CHAT,
    CONSULT,
    INCIDENT
}
