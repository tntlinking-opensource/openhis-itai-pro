# GAP-EXE-008 长期嘱写路径不校验 planTime ∈ 频次展开

> **问题类型**：并发双写与条件更新
> **严重度**：低危～中危（**BR-IP-EXE-023 允许补录/提前**为产品能力）
> **影响范围**：住院
> **优先级**：P3
> **状态**：待评估
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-IP-EXE-02；BR-IP-EXE-023/024

---

## 1. 问题描述

长期嘱写路径不校验 planTime ∈ 频次展开

## 2. 发生场景

写 Gate 仅校验 occurrence 净态 + 不晚于停嘱；**不校验** planTime 是否在 `computeExecutionTimePoints` 展开集内；无 Procedure 的 occurrence 视为 PENDING。

## 3. 根因分析

补录设计；列表展开与写 Gate 解耦。

## 4. 业务影响

接口直调或非常规 planTime 可多执行 → 长期嘱多次 Dispense（每次单次剂量）。

## 5. 修改方案

1. 配置项 `inpatient.execute.strictPlanTime`（默认 `false` 保持补录）。
2. `true` 时：`assertWriteSlotExecutable` 增加 `frequencyQueryService.isValidOccurrence(...)`，不在展开集 → EX-IP-EXE-303。


## 6. 涉及代码

- `NurseStationOrderExecuteAppService`
- `FrequencyQueryService` 封装

## 7. 验收用例

- 开关 false：补录 21 日点仍成功。
- 开关 true：非展开 planTime 拒绝。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
