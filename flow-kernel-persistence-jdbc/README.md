# flow-kernel-persistence-jdbc

JDBC persistence adapter baseline。

本模块实现 core、event 和 packet 的 JDBC repository adapter，并打包 H2 schema resource 给 `JdbcSchemaInitializer` 使用。

主要入口：

- `JdbcSchemaInitializer`：初始化 `open-flow-kernel-jdbc-schema.sql`。
- `JdbcProcessDefinitionRepository`
- `JdbcProcessInstanceRepository`
- `JdbcTaskInstanceRepository`
- `JdbcTaskRelationRepository`
- `JdbcTaskCandidateRepository`
- `JdbcCandidateRelationRepository`
- `JdbcProcessInstanceRelationRepository`
- `JdbcProcessInstanceDataInitRepository`
- `JdbcProcessLogRepository`
- `JdbcPacketRepo` / `JdbcPacketDataRepo`
- `JdbcEventStore`

Schema：

```text
db/schema/h2/open-flow-kernel-jdbc-schema.sql
db/schema/mysql/open-flow-kernel-jdbc-schema.sql
```

当前验证：

- H2 MySQL mode repository tests。
- `JdbcSchemaParityTest` 字段级 DDL 保护。
- MySQL/Testcontainers source set 可编译。

边界：

- 真实 MySQL runtime 需要 Docker daemon，目前不能在没有 Docker 的环境证明。
- 本模块不引入 JOOQ/generated records。
- Event payload serialization 通过 `JdbcEventPayloadCodec` 注入。

更多说明见 `docs/持久化与集成/持久化.md`、`docs/持久化与集成/参考DDL对照.md` 和 `docs/持久化与集成/MySQL-Testcontainers验证.md`。
