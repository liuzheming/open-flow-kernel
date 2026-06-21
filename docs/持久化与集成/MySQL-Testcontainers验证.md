# MySQL / Testcontainers 验证

本文记录 MySQL/Testcontainers 验证边界。

## 当前状态

默认验收命令仍然是：

```bash
./gradlew test --no-daemon --warning-mode all
```

这条命令使用 H2 MySQL mode 和根目录 H2 DDL，覆盖当前 JDBC repository 行为。

MySQL DDL 已放在：

```text
db/schema/mysql/open-flow-kernel-jdbc-schema.sql
```

当前会话尝试检查 Docker：

```bash
docker info --format '{{.ServerVersion}}'
```

结果是 Docker daemon 不可连接，因此不能在当前环境真实启动 MySQL Testcontainer。这是可选外部环境验证，不作为当前核心复刻闭环阻塞项。

## 为什么不把 Testcontainers 放进默认测试

Testcontainers 依赖本机 Docker daemon。若直接放进默认 `test`，没有 Docker 的开发者会无法运行最小验收。

本项目当前阶段的默认测试目标是证明：

- Camunda 最小闭环可运行。
- Process-Task-Form 核心语义成立。
- JDBC repository baseline 在 H2 MySQL mode 下通过。
- H2/MySQL 两份 DDL 都在 `db/schema` 中可审计。

## 验证方式

已提供一个独立的、显式开启的 MySQL 验证任务：

```bash
./gradlew :flow-kernel-persistence-jdbc:mysqlIntegrationTest --no-daemon --warning-mode all
```

验证内容应至少包括：

- 使用 `db/schema/mysql/open-flow-kernel-jdbc-schema.sql` 初始化 MySQL 8。
- 验证 `JdbcProcessDefinitionRepository` 可写入/读取 `data_packet_resolver_class` 和 `packet_init_config`。
- 验证 packet pointer/value/CAS commit 的 MySQL 行为。
- 验证 `candidate_inst_relation` baseline repository。
- 验证 `JdbcEventStore` 的 event record/replay 基础行为。

当前已完成的非 Docker 验证：

```bash
./gradlew :flow-kernel-persistence-jdbc:mysqlIntegrationTestClasses --no-daemon --warning-mode all
```

这证明 Testcontainers source set、依赖和测试代码可编译，但不证明 MySQL 运行时兼容性。

## 当前结论

MySQL/Testcontainers 是环境依赖验证项，当前尚未完成真实运行。

这不影响当前默认验收命令通过，也不影响 第三轮 核心复刻闭环。不能把 MySQL compatibility 标记为已证明；只能说明 MySQL DDL、测试入口和编译保护已经具备。
