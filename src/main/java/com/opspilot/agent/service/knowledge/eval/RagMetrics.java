package com.opspilot.agent.service.knowledge.eval;

/**
 * RAG 与意图评测的聚合指标。
 *
 * @param hitRate 检索命中率（至少命中一个期望来源的样本占比）
 * @param mrr 平均倒数排名（Mean Reciprocal Rank）
 * @param intentAccuracy 意图分类准确率
 * @param severityAccuracy 告警等级准确率
 * @param highRiskRecall 高危告警召回率（期望 HIGH 的样本中实际判为 HIGH 的比例）
 */
public record RagMetrics(
        double hitRate,
        double mrr,
        double intentAccuracy,
        double severityAccuracy,
        double highRiskRecall
) {
}
