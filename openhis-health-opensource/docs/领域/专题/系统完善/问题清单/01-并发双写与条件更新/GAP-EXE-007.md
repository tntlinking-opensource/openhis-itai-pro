# GAP-EXE-007 门诊耗材自动扣库并发（已知限制）

> **问题类型**：并发双写与条件更新
> **严重度**：中危（**AS-OP-NUR-001 §7 已声明**「并发以后写者校验为准」）
> **影响范围**：门诊
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-NUR-04；BR-NUR-008；BR-NUR-014

---

## 1. 问题描述

门诊耗材自动扣库并发（BR-NUR-008 静默 skip）

## 2. 发生场景

`applyOutpatientNurseDeviceDispenses` 对 `validateDeviceDispensePreparable` 失败 **`continue` 静默跳过**；极端并发两请求均通过则可能双扣。

## 3. 根因分析

无发放单状态乐观锁；静默 skip 掩盖冲突。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

1. `DeviceDispense` 实体映射 `@Version`（表若无列则 Flyway 增加）。
2. 状态迁移 `PREPARATION → PREPARED/COMPLETED` 使用乐观更新；失败 → `EX-NUR-207/208`，**整批失败**，提示刷新。
3. 用户提交的行若不可 preparable，**不得静默 skip**（与 BR-NUR-008「避免重复发放」一致化）。


## 6. 涉及代码

- `zxplat-domain/.../workflow/devicedispense/entity/DeviceDispense.java`
- `health-module-support/.../DeviceDispenseNurseWorkflowSupport.java`

## 7. 验收用例

- 同 `dispenseId` 双线程执行：至多一笔扣库；另一笔明确失败。
- 开关关（仅 PREPARED）：仍仅处理 PREPARATION 行。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | `DeviceDispense @Version` + 静默 skip 改整批失败；状态 → 已实施 |
