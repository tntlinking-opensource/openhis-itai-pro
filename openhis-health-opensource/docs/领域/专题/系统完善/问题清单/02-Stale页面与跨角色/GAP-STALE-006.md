# GAP-STALE-006 汇总/核发 Stale 页

> **问题类型**：Stale页面与跨角色
> **严重度**：中危
> **影响范围**：住
> **优先级**：P2
> **状态**：已实施
> **父文档**：[SYS-IMP-007](../02-Stale页面与跨角色/SYS-IMP-007-跨角色Stale页面与状态不同步.md)
> **关联 IF/BR**：[GAP-INV-003](../01-并发双写与条件更新/GAP-INV-003.md)、[GAP-INV-004](../01-并发双写与条件更新/GAP-INV-004.md)；**AS-IP-NUR-010**（EX-IP-SUM-201）、**AS-IP-PHA-002**（EX-IPS-201）

---

## 1. 问题描述

汇总/核发 Stale 页双操作

## 2. 发生场景

① 护士对同一批 `dispenseId` 在**未刷新**的汇总页重复点「汇总保存」；或 ② 药房对同一 `summaryNo` 在**未刷新**的核发页重复点「核发」。两窗口均通过读 Gate（`PREPARATION` 无 `summaryNo` / `APPROVAL` + `SUMMARIZED`），各自提交成功，产生双汇总单或双核发。

## 3. 根因分析

与 [SYS-IMP-005](../01-并发双写与条件更新/SYS-IMP-005-进销存单据与汇总发药并发边界.md) 同族：**读 Gate + 无源态条件更新**。Stale 页与纯并发双点为同一修复——提交时须以条件 UPDATE 占位，而非仅依赖列表快照。

- **汇总**：`summarizeMedicationDispenses` 原先 `applySummaryState` + `saveAll`，无 `WHERE status=PREPARATION AND summary_no IS NULL`。
- **核发**：`agreeRequest` / `completeSummaryDispense` 原先在扣库之后写态，Stale 双窗可能在占位前重复扣库（已在 GAP-INV-004 中调整为扣库前条件迁移）。

## 4. 业务影响

双汇总单、药房重复核发、账实不符；取消执行后汇总重建复杂化（BR-IP-CXL-012）。

## 5. 修改方案

1. **汇总条件更新（GAP-INV-003）**：`UPDATE med_medication_dispense SET summary_no, status=SUMMARIZED WHERE id IN (…) AND status=PREPARATION AND summary_no IS NULL AND is_deleted=false`；影响行数 ≠ 批大小 → **EX-IP-SUM-201**。
2. **核发条件更新（GAP-INV-004）**：扣库前 `Supply APPROVAL→AGREE`、`Dispense SUMMARIZED→COMPLETED` 条件迁移；失败 → **EX-IPS-201**；扣库仍走 `decreaseQuantitiesMerged`（最后防线）。
3. **应用层**：汇总写路径将域层 `MEDICATION_DISPENSE_INVALID_STATUS` 映射为 **EX-IP-SUM-201**，与 AS 一致。

## 6. 涉及代码

- `MedicationDispenseRepository#summarizeIfPreparationWhereIdIn`（新增）
- `MedicationDispenseDomainService#summarizeMedicationDispenses`
- `MedicationDispenseRepository#completeSummaryDispenseWhereSummaryNoAndSourceStatus`
- `SupplyRequestRepository#approveToAgreeWhereBusNoAndSourceStatus`
- `NurseStationDispenseSummaryAppService#createMedicationSummary`
- `InpatientPharmacySummaryDispenseAppService#dispenseSummaryMedications`

## 7. 验收用例

- T1：两窗口同一 `dispenseIds` 汇总 → 1 个 `summaryNo`，另 1 **EX-IP-SUM-201**。
- T2：列表仍显示已汇总行（模拟 Stale）但提交 → **EX-IP-SUM-201**。
- T3：两窗口同一 `summaryNo` 核发 → 1 成功，另 1 **EX-IPS-201**，无重复扣库。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 汇总/核发条件更新落地；Stale 双窗与并发统一由源态 UPDATE 拦截 |
