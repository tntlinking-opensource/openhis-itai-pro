# GAP-INV-004 汇总核发双提交（EX-IPS-201）

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：已实施
> **父文档**：[SYS-IMP-005](../01-并发双写与条件更新/SYS-IMP-005-进销存单据与汇总发药并发边界.md)
> **关联 IF/BR**：`InpatientPharmacySummaryDispenseAppService`；**AS-IP-PHA-002**

---

## 1. 问题描述

汇总核发双提交（EX-IPS-201）

## 2. 发生场景

两窗口对同一 `summaryNo` 同时核发；均读到 Supply `APPROVAL` + Dispense `SUMMARIZED`。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

① Supply 行 `APPROVAL → AGREE` 条件更新；② Dispense `SUMMARIZED → COMPLETED` 条件更新（**扣库前**占位）；③ 扣库存仍走 `decreaseQuantitiesMerged`（最后防线）。


## 6. 涉及代码

- `InpatientPharmacySummaryDispenseAppService#dispenseSummaryMedications`
- `SupplyRequestRepository#approveToAgreeWhereBusNoAndSourceStatus`
- `MedicationDispenseRepository#completeSummaryDispenseWhereSummaryNoAndSourceStatus`
- `MedicationDispenseDomainService#completeSummaryDispense`

## 7. 验收用例

- 两窗口同一 summaryNo 核发：1 成功，1 EX-IPS-201。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 扣库前 Supply/Dispense 条件迁移（GAP-STALE-006） |
