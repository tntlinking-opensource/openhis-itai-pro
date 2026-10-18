# GAP-ATD-005 出院/清床/召回并发

> **问题类型**：并发双写与条件更新
> **严重度**：中危（文档已知）
> **影响范围**：住
> **优先级**：P2
> **状态**：**已实施**（2026-06-28，阶段 A + B）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)
> **关联 IF/BR**：AS-IP-NUR-002「出院/清床/召回按就诊乐观门禁」；反结见 SYS-IMP-002

---

## 1. 问题描述

出院/清床/召回并发

## 2. 发生场景

在住场景下，双窗同时对同一就诊办理出院（ATD-05）、清床（ATD-06）或召回（ATD-07）；均通过读 Gate，领域 `loadEncounterMap` 无行锁。

## 3. 根因分析

读时状态 Gate；写路径无 `PESSIMISTIC_WRITE`、无 `Encounter` `@Version`。

## 4. 业务影响

重复状态迁移、领域事件双发；召回路径反结与就诊写交叉风险。

## 5. 修改方案

`Encounter` 状态迁移统一条件 UPDATE + `@Version`；召回与反结编排顺序在 AS-IP-FIN-004 已定义，实现层禁止绕过。

**已采用**：
- **阶段 A**：ATD-05/06/07 + `completeInpatientFinancialSettlement` 悲观行锁 + 锁内断言
- **阶段 B**：`Encounter` `@Version`；`OptimisticLockingFailureException` / 状态冲突映射

**未纳入本 GAP**：转科等路径见 GAP-ATD-006

## 6. 涉及代码

- `Encounter.java`
- `EncounterDomainService`（ATD-05/06/07、`completeInpatientFinancialSettlement`、`revertInpatientFinancialSettlement`）
- `NurseStationATDManagerAppService`、`InpatientSettleAppService`、`InpatientRegistrationAppService`（乐观锁映射）

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **阶段 A** | `requireEncounterForUpdate`；`ENCOUNTER_INVALID_STATUS` → EX-IP-ATD-304/401/501 |
| **阶段 B** | `Encounter` `@Version` → `adm_encounter.version` |
| **阶段 B** | `completeInpatientFinancialSettlement` / `revertInpatientFinancialSettlement` 行锁 |
| **乐观锁映射** | ATD-05 → **304**；ATD-06 → **402**；ATD-07 → **504**；出院全结 → **EX-IP-STL-109**；登记 → **EX-IP-REG-003** |
| **事务** | `AppServiceAspect` UoW |

**变更文件**

| 模块 | 文件 | 要点 |
| --- | --- | --- |
| zxplat-domain | `Encounter.java` | `@Version` |
| zxplat-domain | `EncounterDomainService.java` | 行锁写路径 + 全结/回退 |
| health-application | `NurseStationATDManagerAppService.java` | 乐观锁异常映射 |
| health-application | `InpatientSettleAppService.java` | 全结乐观锁/状态映射 |
| health-application | `InpatientRegistrationAppService.java` | 登记乐观锁映射（随 `@Version`） |

## 8. 验收用例

- 双窗办理出院：1 成功 `PENDING`，另 1 幂等或 **304**。
- 双窗清床：1 成功 `DISCHARGED`，另 1 **401/402**。
- 双窗召回：1 成功 `REGISTERED`，另 1 **501/504**。
- 双窗出院全结：1 成功 `COMPLETED`，另 1 **EX-IP-STL-109**。
- 就诊与其它写路径并发改同一行：`OptimisticLockingFailureException` 明确失败。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 阶段 A：ATD-05/06/07 就诊行锁 + 异常映射 |
| v1.2 | 2026-06-28 | 阶段 B：`@Version` + 全结行锁 + 乐观锁映射；状态 → 已实施 |
