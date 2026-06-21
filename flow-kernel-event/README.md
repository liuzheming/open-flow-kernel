# flow-kernel-event

事件内核模块。

它保留原设计中的事件先记录、事务边界后分发、listener delivery、retry/backoff、永久失败和 replay 语义。

主要入口：

- `EventBus`：事件发布和分发。
- `EventStore`：事件持久化 SPI。
- `EventListener` / `EventListenerRegistration`：监听器注册。
- `TransactionBoundary`：事务后分发边界。
- `RetryPolicy` / `BackoffPolicy`：重试策略。
- `EventV2`：参考设计中的事件基类抽取。

边界：

- 本模块不绑定 Spring transaction manager。
- JDBC 事件存储在 `flow-kernel-persistence-jdbc` 的 `JdbcEventStore`。
- 生产 executor、scheduler 和运维 API 属于 deferred scope。

更多说明见 `docs/持久化与集成/事件系统.md`。
