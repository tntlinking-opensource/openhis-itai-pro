# GAP-ACL-001 门诊收费无机构-岗位 Gate

> **问题类型**：权限越权与数据域
> **严重度**：**高危**（合规/等保类；知晓 encounterId 即可操作）
> **影响范围**：门诊
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-008](../03-权限越权与数据域/SYS-IMP-008-多租户岗位与财务数据域.md)
> **关联 IF/BR**：IF-OPC-06/07；AS 隐含「当前收银岗位可见队列」

---

## 1. 问题描述

门诊收费预结算/正式结算无机构-岗位 Gate

## 2. 发生场景

`preSettleOutpatientCharges`、`confirmOutpatientChargeSettlement`、`loadPendingChargeItems` 等：`mustGetEncounter` + `assertChargeItemsBelongEncounter`，**不**校验当前操作员机构/岗位是否覆盖该就诊科室。

## 3. 根因分析

无 `EncounterFinancialAccesssupport 或等价组件；护士站 `assertVisible` 未复用到财务层。

## 4. 业务影响

水平越权：A 科室收银员对 B 科室就诊预结算/收款；审计无法归因到正确窗口。

## 5. 修改方案

1. 新增 **`EncounterFinancialAccesssupport**（module-support）：
   - 输入：`encounterId`、`operatorId`、可选 `cashierWindowId`；
   - 校验：就诊 `organizationId` ∈ 操作员可管理科室/机构；可选校验费别账户可见性。
2. 在 **所有财务写入口** 首行调用：`OutpatientChargeAppService`、`OutpatientRefundAppService`、挂号 `payRegisterFee` 等。
3. Query 列表：`OutpatientChargeEncounterQueueLoader` 与写 Gate 共用同一收窄规则（防「列表看不到但能直调 API」）。
4. 失败码：统一 `FIN-ACL-403` / 复用 `EX-IP-NUR-102` 风格文案。


## 6. 涉及代码

- 新建 `EncounterFinancialAccessSupport.java`
- `OutpatientChargeAppService.java`（preSettle / confirm / loadPending）
- `OutpatientChargeEncounterQueueLoader.java`

## 7. 验收用例

- T1：操作员仅管辖科室 A，对科室 B 的 `encounterId` 预结算 → 403。
- T2：同机构多科室岗位，队列仅含授权科室就诊。
- T3：Supervisor 角色（若产品定义）可跨科室 — 按 RBAC 配置覆盖。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
