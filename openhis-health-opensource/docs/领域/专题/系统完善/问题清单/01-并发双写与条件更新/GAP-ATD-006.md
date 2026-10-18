# GAP-ATD-006 转科/转病区 ON_HOLD

> **问题类型**：并发双写与条件更新
> **严重度**：低危～中危
> **影响范围**：住
> **优先级**：P3
> **状态**：**已实施**（2026-06-28，阶段 A + B + C）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)
> **关联 IF/BR**：AS-IP-NUR-003 IF-IP-AUD-02 **BR-IP-AUD-011**

---

## 1. 问题描述

转科/转病区 ON_HOLD 幂等窗口

## 2. 发生场景

`NurseStationOrderAuditAppService` 对已 `ON_HOLD` 粗粒度幂等跳过；`initiateDepartmentTransfer` / `dischargeInpatientEncounter` / `confirmDepartmentTransfer` 无行锁；并发校对转科/出院可能重复编排。

## 3. 根因分析

读 Gate + `loadEncounterMap` 无 `PESSIMISTIC_WRITE`；App 非 `IN_PROGRESS` 一律静默 `return`，易产生「医嘱已 VERIFIED、就诊未迁」。

## 4. 业务影响

待转科/待出院分流错乱、参与者重复收口、领域事件双发、与 ATD-05 出院链交叉风险。

## 5. 修改方案

与 GAP-ATD-004/005 对齐：`requireEncounterForUpdate` + 锁内源态断言 + `Encounter` `@Version`。

**已采用**：
- **阶段 A**：三领域写路径悲观行锁
- **阶段 B**：校对编排收紧（里程碑 `requestId` 幂等；异态/异 request 拒绝）；`ENCOUNTER_INVALID_STATUS` / 乐观锁 → **EX-IP-AUD-203**
- **阶段 C**：`hasVerifiedOppositeTransferOrDischargeRequest` 异类已校对 Pre 门禁

## 6. 涉及代码

- `EncounterDomainService`（§6.1.16～18）
- `ServiceRequestQueryService#hasVerifiedOppositeTransferOrDischargeRequest`
- `NurseStationOrderAuditAppService#orchestrateEncounterAfterServiceVerified`

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **阶段 A** | `initiateDepartmentTransfer` / `dischargeInpatientEncounter` / `confirmDepartmentTransfer` → `requireEncounterForUpdate` |
| **阶段 B** | 校对编排：`IN_PROGRESS` 才写；`ON_HOLD` 仅同里程碑 request 幂等；否则 **203** |
| **阶段 C** | 校对前异类 `VERIFIED` 转科/出院诊疗 Pre 拒绝 |
| **乐观锁** | `OptimisticLockingFailureException` → **EX-IP-AUD-203** |
| **关联 GAP-ATD-008** | 逆向编排对称性已实施（[GAP-ATD-008](./GAP-ATD-008.md)） |

**变更文件**

| 模块 | 文件 | 要点 |
| --- | --- | --- |
| zxplat-domain | `EncounterDomainService.java` | 三写路径行锁 |
| zxplat-domain | `ServiceRequestQueryService.java` | 异类已校对查询 |
| health-application | `NurseStationOrderAuditAppService.java` | 编排 + 异常映射 |

## 8. 验收用例

- 双窗同时校对转科：1 成功 `ON_HOLD`，另 1 **EX-IP-AUD-203**（整批回滚）。
- 转科与出院几乎同时校对：仅 1 链路成功。
- 已 `ON_HOLD` 再校对第二条转科/出院医嘱：**203**（非静默跳过）。
- 已 `ON_HOLD` 重复提交同一条转科校对（幂等）：里程碑 request 对齐可跳过 Encounter 写。
- `confirmDepartmentTransfer` 双窗：1 成功 `REGISTERED`，另 1 明确失败。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 阶段 A+B+C 落地；状态 → 已实施 |
