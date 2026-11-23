package com.opspilot.agent.domain;

/**
 * 后台事件类型标签。
 *
 * <p>这些标签只用于告警分级和管理员后台，不直接展示给普通用户。</p>
 */
public enum IncidentCategory {
    NORMAL,
    APPLICATION,
    INFRASTRUCTURE,
    DATABASE,
    NETWORK,
    PAYMENT,
    SECURITY,
    CRITICAL
}
