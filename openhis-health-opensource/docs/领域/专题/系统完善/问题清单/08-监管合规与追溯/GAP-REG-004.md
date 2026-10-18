# GAP-REG-004 处方权签发 Gate 未落地

> **问题类型**：监管合规与追溯
> **严重度**：**高危**
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-013](../08-监管合规与追溯/SYS-IMP-013-监管合规链与核发追溯.md)
> **关联 IF/BR**：DM-MRQ 处方权；`MedicationManageInitDto` 仅有字典项

---

## 1. 问题描述

处方权/精麻权限签发 Gate 未落地

## 2. 发生场景

医生站 `signOff` / 签发路径 **未见**统一 `PractitionerPrescriptionPrivilegeGate`（抗菌、精麻、毒性药品等级别）。

## 3. 根因分析

主数据字典与签发写路径未编排。

## 4. 业务影响

无处方权医师仍可签发受限品种。

## 5. 修改方案

1. 新增 `PrescriptionPrivilegeGatesupport：输入 `practitionerId + medicationId/category + rxTypeCode`；
2. 挂接：`DoctorStationAdviceAppService` 批量签发、住院对称路径、中药签发；
3. 失败码：`DOR-RX-403`；override 需双签角色 + 审计（BND-REG-001）。


## 6. 涉及代码

- `DoctorStationAdviceAppService.java`
- `InpatientDoctorStationAdviceAppService.java`（对称）
- 新建 Gate Support

## 7. 验收用例

- 无精麻权医生签发麻药 → 403。
- 有权限 → 成功。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
