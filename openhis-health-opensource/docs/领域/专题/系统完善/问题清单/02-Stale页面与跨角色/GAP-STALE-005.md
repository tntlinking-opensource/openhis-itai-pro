# GAP-STALE-005 住院医生签退已执行未结算医嘱

> **问题类型**：Stale页面与跨角色
> **严重度**：中危
> **影响范围**：住
> **优先级**：P2
> **状态**：已实施
> **父文档**：[SYS-IMP-007](../02-Stale页面与跨角色/SYS-IMP-007-跨角色Stale页面与状态不同步.md)
> **关联 IF/BR**：IF-IP-DOR-16；AS **EX-IP-ORD-103**（已收费不可签退）、**EX-IP-ORD-106**；住院中药 **BR-IP-TCM-009** 已有门控

---

## 1. 问题描述

住院：医生签退已执行未结算医嘱（无执行/发药/费用 Gate）

## 2. 发生场景

① 护士已执行（Procedure + Dispense + `PENDING_SETTLEMENT` Charge）→ ② 医生签退（仅 `signOffMedicationOrder`）→ ③ 请求回草稿，**执行/发放/费用仍在**。

## 3. 根因分析

`InpatientDoctorStationAdviceAppService#withdrawInpatientDoctorStationAdviceBatch` **不**回滚执行与费用；`ensureSignOffAllowed` 仅要求 `ACTIVE`；**未实现** AS 的 `EX-IP-ORD-103`；西药路径**无** `InpatientTcmRxRevokeGatesupport 同类校验。

## 4. 业务影响

医嘱显示已撤回，护士站/药房仍见执行任务；结算口径混乱。

## 5. 修改方案

1. **签退 Gate（对齐 AS）**：签退前批量检查：无净剩余 `COMPLETED` Procedure；发药无在途/`PENDING_REFUND` 且**净已发**（正向 `COMPLETED` − `REFUND+REFUNDED`）不大于 0（DM-MED-002 §6.1.4）；无 `PENDING_SETTLEMENT`/`SETTLED` Charge（按 medReqId 锚点）→ 否则 **EX-IP-ORD-103**。
2. **复用组件**：抽取 `InpatientAdviceRevokeGate`（西药/诊疗/中药共用），逻辑参考 `InpatientTcmRxRevokeGate`。
3. **产品口径**：若允许「签退但保留已发生事实」，则 AS 改为文档化「仅停止后续」，签退 UI 区分「停止医嘱」与「撤回未执行」——**当前实现与 AS 错误码不一致，须二选一**。


## 6. 涉及代码

- `InpatientDoctorStationAdviceAppService#withdrawInpatientDoctorStationAdviceBatch` — 签退前调用 Gate
- `InpatientAdviceRevokeGatesupport — 西药/诊疗/中药共用门控（新建）
- `InpatientTcmRxRevokeGatesupport — 委托至共用 Gate
- `DoctorStationAdviceConstants` — `EX_IP_ORD_103` / `MSG_INPATIENT_WITHDRAW_BLOCKED`

## 7. 验收用例

- T1：已执行未结算 → 签退 → 拒绝 EX-IP-ORD-103。
- T2：仅签发未校对 → 签退成功。
- T3：已结算 → 签退拒绝（或走退费，不按签退）。
- T4：Stale Tab 不刷新直调 IF-IP-DOR-16 → 同上拒绝。

**自动化**：`InpatientAdviceRevokeGateSupportTest`（T1/T2/T3/T4 单元覆盖）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.2 | 2026-08-13 | 发药门控改为净已发数量对冲（正向 `COMPLETED` 全额 `REFUNDED` 后不误拦） |
| v1.1 | 2026-06-28 | 落地签退 Gate：`InpatientAdviceRevokeGate` + EX-IP-ORD-103 |
