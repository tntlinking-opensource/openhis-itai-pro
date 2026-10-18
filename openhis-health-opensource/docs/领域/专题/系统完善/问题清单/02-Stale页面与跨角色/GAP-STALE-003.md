# GAP-STALE-003 取消执行后药房仍对软删 Dispense 操作

> **问题类型**：Stale页面与跨角色
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：已实施
> **父文档**：[SYS-IMP-007](../02-Stale页面与跨角色/SYS-IMP-007-跨角色Stale页面与状态不同步.md)
> **关联 IF/BR**：IF-IP-CXL-01；**BR-IP-CXL-007**（未发软删 Dispense）

---

## 1. 问题描述

住院：取消执行软删 Dispense 后，药房仍可对同一 id 配药/核发

## 2. 发生场景

① 护士取消执行（`PREPARATION` → `removeUndispensedDispense` / `markDeleted`）→ ② 药房列表未刷新，对同一 `dispenseId` 点「保存配药」或「核发」。

## 3. 根因分析

① `MedicationDispenseQueryService#validateDispensePreparable` / `#validateDispensable` 只查 `status_enum`，**不**查 `is_deleted`；② `MedicationDispenseDomainService#mustGet` / `#prepareMedicationDispense` / `#dispenseMedication` 不拒绝软删行；③ 列表 Query 若未过滤 `is_deleted`，加剧 Stale 感知。

## 4. 业务影响

取消执行后仍配药/核发；账实与执行记录不一致。

## 5. 修改方案

1. **域层统一 Gate**：`mustGet` 或各 `ensure*` 增加 `assertNotDeleted()`；软删 → `MEDICATION_DISPENSE_NOT_FOUND` 或 `INVALID_STATUS`。
2. **Query 侧**：待发/配药列表 Specification 默认 `is_deleted = false`（核对所有药房/护士 Query 入口）。
3. **取消执行**：`removeUndispensedDispense` 可选将状态改为 `DECLINED` 或 `ON_HOLD`，使 stale 提交在状态层也失败（与释占事件一致）。
4. **条件更新**：核发 `UPDATE … WHERE id=? AND is_deleted=false AND status_enum IN (…)`（叠加 SYS-IMP-001 [GAP-EXE-009](../01-并发双写与条件更新/GAP-EXE-009.md)）。


## 6. 涉及代码

- `MedicationDispense.java`、`MedicationDispenseDomainService.java`
- `MedicationDispenseQueryService.java`（`validateDispensePreparable` / `validateDispensable`）
- `OutpatientPharmacyDispenseAppService`、`InpatientPharmacyDispenseAppService`
- `NurseStationOrderCancelExecuteWriteAssembler#rollbackMainDispenseAndCharge`

## 7. 验收用例

- T1：取消执行后立即对同一 `dispenseId` 配药 → 失败。
- T2：列表仍显示该行（模拟 stale）但提交 → 失败。
- T3：取消前已 `IN_PROGRESS` → 见 GAP-STALE-004。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 域层 `assertNotDeleted` + Query Gate；取消执行软删同步 ON_HOLD；批量加载过滤软删行 |
