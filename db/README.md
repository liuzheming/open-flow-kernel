# DB 建表语句

这个目录放 `open-flow-kernel` 当前 JDBC baseline 需要的建表语句。

## 目录说明

```text
db/
  schema/
    h2/
      open-flow-kernel-jdbc-schema.sql
    mysql/
      open-flow-kernel-jdbc-schema.sql
```

- `schema/h2`：测试和 `JdbcSchemaInitializer` 当前实际使用的 DDL。
- `schema/mysql`：面向真实 MySQL 8 的参考 DDL，字段与当前核心 schema 对齐。

`flow-kernel-persistence-jdbc` 会把 `db/schema/h2/open-flow-kernel-jdbc-schema.sql` 打进 jar，作为 `JdbcSchemaInitializer` 的资源。

## 当前已覆盖表

| 表 | 用途 |
| --- | --- |
| `process_def` | 流程定义 |
| `process_task_config` | 任务定义和任务配置 |
| `process_inst` | 业务流程实例 |
| `process_inst_data` | 流程实例数据 |
| `process_inst_data_init_config` | 流程实例初始化数据配置 |
| `process_task_inst` | 业务任务实例 |
| `process_task_inst_data` | 任务实例数据 |
| `process_task_inst_relation` | 任务外部 relation，例如表单、子流程 |
| `process_inst_relation` | 流程实例外部 relation |
| `process_log` | 流程运行日志 |
| `process_inst_data_packet` | packet 当前指针 |
| `process_inst_data_packet_value` | packet value 历史 |
| `process_task_inst_candidate` | 任务候选人 baseline |
| `candidate_inst_relation` | 原实现 action/candidate 待办关系表 |
| `event_record` | Event baseline 主表 |
| `event_delivery` | Event listener 投递状态 |

## 说明

当前 typed packet 复用 `process_inst_data_packet` 和 `process_inst_data_packet_value`
承载 pointer/value/history，`process_def` 上保留 `data_packet_resolver_class` 和
`packet_init_config`。如果后续为了生产治理增加归档表、索引表或审计扩展表，应继续放在本目录下，并同步更新
`docs/持久化与集成/持久化.md`。

字段级参考对照见 [`docs/持久化与集成/参考DDL对照.md`](../docs/持久化与集成/参考DDL对照.md)。
