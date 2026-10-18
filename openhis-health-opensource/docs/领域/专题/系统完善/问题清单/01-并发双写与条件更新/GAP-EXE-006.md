# GAP-EXE-006 门诊护士执行次数 netCount 非原子

> **问题类型**：并发双写与条件更新
> **严重度**：低危～中危
> **影响范围**：门诊
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-NUR-04；BR-NUR-005

---

## 1. 问题描述

门诊护士执行次数 netCount 非原子

## 2. 发生场景

`OutpatientNurseStationTreatmentAppService` 中 `netProcedureCount >= planned` 时 **`continue` 跳过**；两并发请求均读到 `net < planned`，各建 COMPLETED Procedure。

## 3. 根因分析

读-改-写非原子；无 request 行锁或 DB 约束。

## 4. 业务影响

**药品**：Dispense 签发时已定量，多为 Procedure 超记；**诊疗**：执行次数可超 `execution_count` / `execute_num`。

## 5. 修改方案

1. **推荐**：`ProcedureDomainService` 前增加 `assertPerformCountBelowPlanned`：对 `med_medication_request` / `wkf_service_request` **`SELECT FOR UPDATE`**，再数净 Procedure；≥ planned → 业务异常（**禁止静默 continue**）。
2. 批量执行时满额应 **抛错** 而非 skip，避免部分成功无感知。


## 6. 涉及代码

- `health-application/.../outpatient/nursestation/treatment/service/OutpatientNurseStationTreatmentAppService.java`
- `zxplat-domain/.../clinical/procedure/service/ProcedureQueryService.java`（或 Request Repository）

## 7. 验收用例

- `execute_num=1` 双点执行：第二次失败且提示明确。
- 取消执行对冲后净次数恢复，可再执行。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | `ProcedurePerformCountGatesupport FOR UPDATE + 整批失败；状态 → 已实施 |
