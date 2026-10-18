# GAP-REG-002 应扫品种空 traceNo 仍可提交

> **问题类型**：监管合规与追溯
> **严重度**：**高危**
> **影响范围**：门+住
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-013](../08-监管合规与追溯/SYS-IMP-013-监管合规链与核发追溯.md)

---

## 1. 问题描述

监管品种「应扫未扫」与空 traceNo 路径

## 2. 发生场景

配药循环：`lineTraceCodes` 为空时跳过 `assertCodesInNetStockForDispenseLine`；Gate Extension 对空列表行为依赖 CHS Client。

## 3. 根因分析

「有码才校验」；缺 **按品种/批号强制扫满数量** 规则。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

1. `MedicationTracePolicy`：按 `medicationId + distributionCategory` 标记 `traceMandatory`；2. 核发前 `assertTraceCountMatchesDispenseQty`；3. 汇总核发 `InpatientPharmacySummaryDispenseAppService` 对称。


## 6. 涉及代码

- 上述核发 AppService + `TraceNumberRecordQueryService`
- `InpatientPharmacySummaryDispenseAppService`

## 7. 验收用例

- 监管品种 qty=2 只扫 1 码 → 失败。
- 非监管品种无码 → 允许（可配置）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
