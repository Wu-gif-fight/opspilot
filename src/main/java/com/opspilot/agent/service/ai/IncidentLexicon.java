package com.opspilot.agent.service.ai;

import java.util.List;

/**
 * 生产事故与运维咨询关键词词库。
 *
 * <p>它是模型判断前的硬规则兜底，主要用于快速识别明确的生产事故信号。</p>
 */
public final class IncidentLexicon {

    private static final List<String> INCIDENT_WORDS = List.of(
            "宕机", "挂了", "崩溃", "瘫痪", "生产事故", "线上事故",
            "数据丢失", "数据不一致", "支付失败", "订单丢失",
            "不可用", "不可写", "无法访问", "泄露", "攻击",
            "全部失败", "全部报错", "全线崩溃",
            "p0", "p1", "down", "crash", "outage", "critical", "sev0", "sev1"
    );

    private static final List<String> CONSULT_WORDS = List.of(
            "报错", "异常", "排查", "部署", "配置", "监控", "告警", "日志", "慢查询",
            "接口", "数据库", "重启", "扩容", "限流", "熔断", "超时", "内存", "磁盘", "cpu",
            "docker", "k8s", "kubernetes", "redis", "mysql", "nginx", "error", "exception",
            "timeout", "deploy", "config", "发布", "上线", "堆栈", "排查一下"
    );

    private IncidentLexicon() {
    }

    public static boolean hasIncidentSignal(String text) {
        return containsAny(text, INCIDENT_WORDS);
    }

    public static boolean hasConsultSignal(String text) {
        return containsAny(text, CONSULT_WORDS);
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
