package com.opspilot.agent.service;

/**
 * 简单令牌桶限流器。
 *
 * <p>用于保护后台工具链：短时间内大量高危报告触发时，限制工具执行速率，
 * 避免 Excel 写入、邮件发送等外部调用被瞬间打爆。超出速率时拒绝执行。</p>
 */
public class ToolRateLimiter {

    private final double permitsPerSecond;
    private final Object lock = new Object();
    private double tokens;
    private long lastRefillNanos;

    public ToolRateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = Math.max(0.1, permitsPerSecond);
        this.tokens = this.permitsPerSecond;
        this.lastRefillNanos = System.nanoTime();
    }

    public boolean tryAcquire() {
        synchronized (lock) {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(permitsPerSecond, tokens + elapsedSeconds * permitsPerSecond);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
