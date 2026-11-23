# 常见报错 FAQ

## 连接类报错

- `Connection refused`：目标服务未启动或端口不对，先确认服务状态和网络。
- `Connection timeout`：网络不通或服务响应过慢，检查防火墙和负载。
- `Connection reset`：服务端主动断开，可能是连接池失效或服务重启。

## 数据库类报错

- `Too many connections`：连接池耗尽，调大连接池或优化慢查询。
- `Deadlock found`：发生死锁，优化事务顺序，缩短锁持有时间。
- `Data too long`：字段长度不够，调整字段定义或截断输入。

## 内存类报错

- `OutOfMemoryError: Java heap space`：堆内存不足，先 dump 分析是否内存泄漏。
- `GC overhead limit exceeded`：GC 过于频繁，检查是否有大对象或内存泄漏。

## 超时类报错

- `Read timed out`：下游响应过慢，调大超时或优化下游性能。
- `Gateway Timeout`：网关等待上游超时，检查上游延迟和网关超时配置。

## 排查建议

- 先看报错栈和发生时间，关联对应时间段的发布与配置变更。
- 用 traceId 关联全链路日志，确认错误发生在哪一跳。
