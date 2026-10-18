# GAP-EXE-010 长期嘱无累计发放量上限

> **问题类型**：并发双写与条件更新
> **严重度**：低危（**设计取舍**，非缺陷）
> **影响范围**：住院
> **优先级**：P3
> **状态**：已实施
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-IP-EXE-02；BR-IP-EXE-016；DM-MED-003 长期=单次用量

---

## 1. 问题描述

长期嘱无累计发放量上限（相对开立总量）

## 2. 发生场景

住院长期药嘱每次执行按 **单次剂量** 新建 Dispense（**BR-IP-EXE-016**），**无**临时嘱「待发合计 = 开立总量」类校验；若频次展开过宽、补录（**GAP-EXE-008**）或接口直调，理论上可在较长时间内累计超过临床预期总量。

## 3. 根因分析

- DM-MED-003 / AS-IP-NUR-004：**长期 req = 单次量**，执行侧每次新建单行 Dispense，不以开立总量做待发 cap。
- 写路径原先**未**对自然月累计做硬 Gate。

## 4. 业务影响

- **低危**：正常护士站操作下，停嘱与 planTime 防重已限制异常放大。
- **边界**：补录 / 非常规 planTime 或长期未停嘱时，**跨月累计**可能偏离院方药事预期。

## 5. 修改方案（**1 个自然月**硬 Gate）

| 维度 | 说明 |
| --- | --- |
| **约束窗口** | **1 个自然月**（`YYYY-MM`）；按 `medReqId` 汇总该月内未软删 Dispense `quantity` |
| **参考上限** | `单次执行发药量 × 月内频次展开可执行点数`；展开窗口 `[max(monthStart, 开立日00:00), min(monthEnd, 停嘱时刻)]` |
| **写路径 Gate** | IF-IP-EXE-02 库存 FIFO 通过后、**Procedure 创建前**：`已发合计 + 本次 FIFO 合计 > 参考上限` → **EX-IP-EXE-305** |
| **临时嘱** | 不适用（仍走待发=总量模型） |

## 6. 涉及代码

- `LongTermMedicationMonthlyDispenseCapsupport（module-support）
- `MedicationDispenseQueryService#sumActiveDispenseQuantityByMedReqIdInNaturalMonth`
- `NurseStationOrderExecuteAppService#assertLongTermMonthlyDispenseCapBeforeWrite`
- `InpatientNurseStationConstants#EX_IP_EXE_305`

## 7. 验收用例

- T1：同月内执行次数 ≤ 月内展开点数 → 成功。
- T2：同月累计 + 本次 > 月内展开点数 × 单次剂量 → 拒绝 **EX-IP-EXE-305**。
- T3：跨自然月 → 上月累计不计入新月 cap。
- T4：停嘱日 mid-month → 上限按停嘱截断，非整月展开点数。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 明确接受口径：**1 个自然月**累计观测窗口 |
| v1.2 | 2026-06-28 | 写路径 Gate 落地（EX-IP-EXE-305） |
