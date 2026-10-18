# GAP-EXE-003 核发断言不含全局待发预占

> **问题类型**：并发双写与条件更新
> **严重度**：中危（偏流程/运维）
> **影响范围**：门+住
> **优先级**：P1
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)

---

## 1. 问题描述

核发断言不含全局待发预占

## 2. 发生场景

多条 `PREPARATION` 待发单各自调用 `assertSufficientForMedicationDispense` 时，仅比对**物理库存**，未减其他待发 Dispense 预占；多条均可过断言，核发时部分失败。

## 3. 根因分析

`InventoryItemQueryService#assertSufficientForMedicationDispense` 只 `findAvailableInventories` 求和，未调用 `listDispenseExeReserve`。

## 4. 业务影响

待发堆积、核发失败体验差；与 GAP-EXE-001 叠加时更明显。

## 5. 修改方案

1. 新增（或扩展）方法，例如：

```text
assertSufficientForMedicationDispenseWithReserve(
  itemTable, medicationId, locationId, lotNumber,
  requiredQty, unitCode, productDefinition,
  excludeDispenseId   // 当前行不计入预占
)
```

2. 计算：`可用 = 物理(批号) − listDispenseExeReserve(同 location+med[+lot])`（排除 `excludeDispenseId`）。
3. 替换调用点：门诊核发、住院核发、住院汇总核发。


## 6. 涉及代码

- `zxplat-domain/.../inventoryitem/service/InventoryItemQueryService.java`
- `health-application/.../outpatient/pharmacy/dispense/service/OutpatientPharmacyDispenseAppService.java`
- `health-application/.../inpatient/pharmacy/dispense/service/InpatientPharmacyDispenseAppService.java`
- `health-application/.../inpatient/pharmacy/summarydispense/service/InpatientPharmacySummaryDispenseAppService.java`

## 7. 验收用例

- 物理 10 片，已有待发预占 8 片，新发 4 片 → 配药/核发断言失败，提示库存不足。
- 当前行 `excludeDispenseId` 不计入自身预占。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | `assertSufficientForMedicationDispenseWithReserve` + 门/住核发替换；状态 → 已实施 |
