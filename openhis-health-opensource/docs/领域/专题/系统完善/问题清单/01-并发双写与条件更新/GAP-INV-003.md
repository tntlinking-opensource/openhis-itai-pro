# GAP-INV-003 汇总领药双写（同 dispenseId）

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：已实施
> **父文档**：[SYS-IMP-005](../01-并发双写与条件更新/SYS-IMP-005-进销存单据与汇总发药并发边界.md)

---

## 1. 问题描述

汇总领药双写（同 dispenseId）

## 2. 发生场景

两护士对**同一批 dispenseId** 同时「汇总保存」；均读到 `PREPARATION` 且无 `summaryNo`，各生成 `summaryNo` + SupplyRequest。

## 3. 根因分析

`assertAllDispensesSummarizable` 读 Gate；`MedicationDispenseDomainService#summarizeMedicationDispenses` 直接 `applySummaryState` + `saveAll`，**无** `WHERE status=PREPARATION AND summary_no IS NULL`。

## 4. 业务影响

双汇总单、药房重复核发风险、取消执行汇总重建复杂化（BR-IP-CXL-012）。

## 5. 修改方案

1. **条件更新（核心）**：

```sql
UPDATE med_medication_dispense
SET summary_no = :summaryNo, status_enum = 'SUMMARIZED', version = version + 1
WHERE id IN (:ids)
  AND status_enum = 'PREPARATION'
  AND (summary_no IS NULL OR summary_no = '')
  AND is_deleted = false;
```

2. 影响行数 ≠ `dispenseIds.size()` → EX-IP-SUM-201。
3. **`MedicationDispense` 映射 `@Version`**（与 [GAP-INV-005](../01-并发双写与条件更新/GAP-INV-005.md) / [GAP-EXE-009](../01-并发双写与条件更新/GAP-EXE-009.md) 合并）。
4. 可选：`(id)` 上对 `SUMMARIZED` 态 `summary_no` 非空部分唯一（防重复汇总号绑定）。


## 6. 涉及代码

- `NurseStationDispenseSummaryAppService#createMedicationSummary`
- `MedicationDispenseDomainService#summarizeMedicationDispenses`
- `MedicationDispenseRepository#summarizeIfPreparationWhereIdIn`

## 7. 验收用例

- 两线程同一 dispenseIds 汇总：1 个 summaryNo，另 1 EX-IP-SUM-201。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 域层 `summarizeIfPreparationWhereIdIn`；应用层 EX-IP-SUM-201 映射（GAP-STALE-006） |
