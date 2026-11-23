package com.opspilot.agent.domain;

/**
 * 告警等级，用于工具链判断和管理员统计。
 *
 * <p>LOW / MEDIUM / HIGH 在展示层分别对应 P2 / P1 / P0。</p>
 */
public enum SeverityLevel {
    LOW,
    MEDIUM,
    HIGH
}
