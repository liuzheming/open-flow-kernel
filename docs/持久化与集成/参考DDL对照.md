# 参考 DDL 对照

本文记录 `open-flow-kernel` 当前建表语句与参考项目迁移 SQL 的对照结果。

参考来源：

```text
<reference-repo>/db/migration/merchant-kernel-flow/
<reference-repo>/db/migration/merchant-kernel-event/
```

当前开源 DDL：

```text
db/schema/h2/open-flow-kernel-jdbc-schema.sql
db/schema/mysql/open-flow-kernel-jdbc-schema.sql
```

## 已对齐的核心表

| 表 | 参考来源 | 当前状态 |
| --- | --- | --- |
| `process_def` | `V12.001`, `V12.007`, `V12.010`, `V12.029` | 核心字段、packet init 字段、流程类型、业务归属、`biz_proc_code`、`version` 已保留 |
| `process_task_config` | `V12.001` | `process_def_id`、`task_code`、`condition`、`key`、`value` 已保留；MySQL 使用 `LONGTEXT` 对齐参考 |
| `process_inst` | `V12.001`, `V12.009`, `V12.011`, `V12.012`, `V12.015`, `V12.024`, `V12.026`, `V12.029` | 子流程、取消、候选人、流程类型、城市、来源、业务归属字段已保留 |
| `process_inst_data` | `V12.001` | 已对齐 |
| `process_inst_data_packet` | `V12.001` | 已对齐 |
| `process_inst_data_packet_value` | `V12.001` | 已对齐 |
| `process_inst_relation` | `V12.001` | 已对齐 |
| `process_log` | `V12.001` | 已对齐 |
| `process_task_inst` | `V12.001`, `V12.012` | 任务名、操作人字段已保留 |
| `process_task_inst_data` | `V12.001` | 已对齐 |
| `process_task_inst_relation` | `V12.001`, `V12.004` | relation id 长度按后续变更保留为 128 |
| `process_task_inst_candidate` | `V12.002` | 已对齐，并额外保留 task id 查询索引 |
| `candidate_inst_relation` | `V12.020`, `V12.021`, `V12.029` | rename 后的 `relate_inst_id`、`deleted`、`type`、`status`、`task_name` 已保留 |

## Adapter 化的表

| 表 | 参考来源 | 当前处理 |
| --- | --- | --- |
| `event_record` | `merchant-kernel-event/V11.001` | 保留事件记录、状态、payload、上下文和 replay 所需字段，但字段名按开源 `EventStore` 模型调整为 `event_type`、`payload_class`、`subject`、`partition`、`correlation` |
| `event_delivery` | `merchant-kernel-event/V11.001` 的 `event_record_result` | 保留 listener 级投递状态、attempts、next retry 和错误信息；表名按内核语义调整 |

事件表没有逐字段照搬参考 JOOQ 表，原因是开源模块要保持 serializer、transaction boundary、scheduler 可替换。行为对齐记录在 [事件系统](事件系统.md)。

## 明确不迁移的参考表

这些表属于业务平台、审批平台、UI、action orchestration 或内部运维能力，不进入当前有界核心：

- `process_def_relation`
- `process_action`
- `process_action_group`
- `process_action_pipe`
- `process_action_op_log`
- `process_action_def`
- `process_action_inst_relation`
- `process_audit_*`
- `process_candidate_*`
- `process_task_reject_info`
- `process_task_failed_info`
- `process_ssc_check_template`
- `proc_entity`

这不是否定这些能力，而是避免把业务平台实现误当成流程内核。后续如迁移 action/audit/task retry，应单独定义 bounded scope，并继续以参考迁移 SQL 为准。

## 当前仍需验证

- MySQL/Testcontainers runtime 还未在当前机器跑通，原因是 Docker daemon 不可连接。
- 当前 H2/MySQL 两份 DDL 已保持字段含义对齐，但 exact original table/index naming 只在核心表范围内做到高保真；事件表和少量索引名按开源 adapter 语义保留差异。
