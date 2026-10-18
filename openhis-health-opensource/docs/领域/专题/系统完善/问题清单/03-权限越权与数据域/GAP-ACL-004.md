# GAP-ACL-004 挂号可指定任意 patientId

> **问题类型**：权限越权与数据域
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-008](../03-权限越权与数据域/SYS-IMP-008-多租户岗位与财务数据域.md)
> **关联 IF/BR**：IF-OP-REG-01

---

## 1. 问题描述

挂号写路径可指定任意 `patientId`（IDOR）

## 2. 发生场景

`resolvePatientIdForPreRegister`：若 `request.getPatientId() != null` **直接复用**，不校验该患者是否属于当前机构/是否对操作员可见。

## 3. 根因分析

信任前端 patientId；无 `PatientAccessGate`。

## 4. 业务影响

IDOR：为他人 patientId 挂号产生 encounter 与费用；与 GAP-FIN-006 防重（按 patientId）组合可能产生「挂错人」而非「双挂」。

## 5. 修改方案

1. 指定 `patientId` 时：`patientQueryService` 加载 + 校验机构归属/状态 ACTIVE；
2. 身份证查重路径保留；新建患者仍走 `requireSessionOrganizationUnitId`；
3. 住院 `InpatientRegistrationAppService#resolvePatientIdForSubmit` 对称改造。


## 6. 涉及代码

- `OutpatientRegistrationAppService#resolvePatientIdForPreRegister`
- `InpatientRegistrationAppService#resolvePatientIdForSubmit`

## 7. 验收用例

- 传入其他机构 patientId → REG-PAT-403。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
