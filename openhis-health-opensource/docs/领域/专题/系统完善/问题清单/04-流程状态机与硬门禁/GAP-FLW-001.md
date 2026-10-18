# GAP-FLW-001 校对通过转科/出院无财务 Gate

> **问题类型**：流程状态机与硬门禁
> **严重度**：**高危**（流程态与财务态脱节）
> **影响范围**：住
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-009](../04-流程状态机与硬门禁/SYS-IMP-009-出院转科召回与财务硬门禁.md)
> **关联 IF/BR**：IF-IP-AUD-02；IF-IP-NUR-ATD-05；**BR-IP-ATD-011**

---

## 1. 问题描述

校对通过转科/出院无财务 Gate

## 2. 发生场景

`NurseStationOrderAuditAppService#orchestrateEncounterAfterServiceVerified`：转科/出院 ServiceRequest 校对通过后直接 `initiateDepartmentTransfer` / `dischargeInpatientEncounter`，**不**调用 `assertDischargeGatePassed`，**不**查 `ChargeItem` 待结算。

## 3. 根因分析

编排层仅处理就诊态迁移；财务 Gate 仅挂在 ATD「办理出院」按钮路径。

## 4. 业务影响

存在大量 `PENDING_SETTLEMENT` 或 REFUNDING 时仍进入待出院；后续结算/清床扯皮。

## 5. 修改方案

1. **校对前硬 Gate（推荐）**：转科/出院类 ServiceRequest 校对通过前：
   - 调用 `assertDischargeGatePassed`（或抽取公共 `DischargeClinicalGate`）；
   - 新增 **`assertDischargeFinancialGate`**：`count(PENDING_SETTLEMENT) == 0` 且不存在 REFUNDING Payment（可配置「允许待出院后结算」则降级为警告）。
2. **或延迟改态**：校对仅标记 ServiceRequest VERIFIED，**不**改 encounter 为 ON_HOLD，直到 ATD 办理出院通过全 Gate（产品需确认与 AS 差异）。
3. 失败码：`EX-IP-AUD-204`（财务未清）/ 复用 `EX-IP-ATD-401`。


## 6. 涉及代码

- `NurseStationOrderAuditAppService#orchestrateEncounterAfterServiceVerified`
- 新建 `InpatientDischargeFinancialGateSupport.java`
- `ChargeItemQueryService`、`PaymentReconciliationQueryService`

## 7. 验收用例

- T1：存在 PENDING_SETTLEMENT → 校对出院嘱 → 拒绝，encounter 仍为 IN_PROGRESS。
- T2：临床 Gate 失败（长期未停）→ 拒绝（与 ATD 一致）。
- T3：全 Gate 通过 → ON_HOLD。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
