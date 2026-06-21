# flow-kernel-form

表单扩展模块。

Form 是 `Process -> Task -> Form` 三层模型里的用户输入层。表单提交不会直接推进 Camunda，而是先完成 `TaskRelation`，再由 task 生命周期推进引擎。

主要入口：

- `FormService`：真实表单系统 adapter SPI。
- `FormTask`：表单任务基类。
- `FormSubmitted`：表单提交事件。
- `FormSubmittedEventListener` / `FormCompleteListener`：表单提交到 relation 完成的桥接。

当前支持：

- single-form baseline。
- `FORM_DEF_MAP` / `FORM_INPUT_MAP` / `FORM_OUTPUT_MAP` multi-form baseline。
- form relation wait and submit completion。

边界：

- 本模块不实现 UI 渲染、菜单、按钮或表单平台 side effects。
- in-memory form service 只在 `flow-kernel-example`。

更多说明见 `docs/API与边界/核心接口.md` 和 `docs/迁移与治理/延后范围.md`。
