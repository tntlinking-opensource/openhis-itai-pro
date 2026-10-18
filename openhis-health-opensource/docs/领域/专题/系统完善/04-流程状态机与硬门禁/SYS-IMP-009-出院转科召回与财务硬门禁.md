# SYS-IMP-009 出院、转科、召回与财务硬门禁

> **问题类型**：流程状态机与硬门禁 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`NurseStationOrderAuditAppService`、`NurseStationATDManagerAppService`、`EncounterDomainService`、`InpatientSettleAppService`）。
>
> **关联 AS**：`AS-IP-NUR-002/003`（入出转）、`AS-IP-NUR-003`（医嘱校对）、`AS-IP-NUR-004/005/011/012`（执行/划价/皮试/取消执行）、`AS-IP-FIN-003/004`（住院结算）、`AS-IP-DOR-003`（医嘱开立）
>
> **关联**：`SYS-IMP-004` GAP-ATD-001～007；`SYS-IMP-007` GAP-STALE-005；`SYS-IMP-002` GAP-FIN-010
>
> **状态**：BR-IP-FLW-008 **已实施**；其余项待实施

---

## 1. 背景与结论摘要

住院 **流程状态机** 在 ZXPlat 中分两段：

| 阶段 | 触发 | 就诊态 | 财务/发药 Gate |
| --- | --- | --- | --- |
| **医嘱校对通过** | `verifyNurseStationOrders` → 转科/出院 ServiceRequest | `IN_PROGRESS` → **`ON_HOLD`（待转科/待出院）** | **无**待结算/未发药 Gate |
| **办理出院** | `NurseStationATDManagerAppService` 出院确认 | `ON_HOLD` → `COMPLETED` 等 | **有** `assertDischargeGatePassed`（医嘱/待发药） |
| **清床** | ATD 清床 | 床位释放 | **有** `EX-IP-ATD-401` 未结算拦截 |

**结论**：

- **办理出院**路径有较完整的 **临床 Gate**（长期未停、临时未执行完、待发药 — `EX-IP-ATD-301/302/303`）。
- **校对通过即改就诊态**（`initiateDepartmentTransfer` / `dischargeInpatientEncounter`）**不**经过上述 Gate，也 **不**校验 `PENDING_SETTLEMENT` 费用、REFUNDING 流水、未完成 Dispense（除 PREPARATION 软删路径）。
- **清床/结算**与 **待出院态** 之间可能出现：**人已在流程上待出院，费用仍大量未结**（**设计允许**待出院期间继续滚费/执行/划价，**清床**时硬拦未结清）。

本文件补齐 **流程状态机 vs 财务/发药硬门禁**，与 IMP-004（床位）、IMP-007（签退）交叉。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-FLW-001 | 校对通过转科/出院无财务 Gate | **高危** | 住 | P0 | 待实施 |
| GAP-FLW-002 | 待出院（ON_HOLD）期间仍可滚费/执行/划价 | 中危 | 住 | P1 | **设计如此** |
| GAP-FLW-003 | 办理出院 Gate 不含待结算费用汇总 | 中危 | 住 | P1 | 待实施 |
| GAP-FLW-004 | 清床 Gate 与结算 Gate 口径不一致 | 中危 | 住 | P2 | 待实施 |
| GAP-FLW-005 | 出院召回与未反结费用顺序 | 中危 | 住 | P2 | 待实施 |
| GAP-FLW-006 | 转科后旧科室费用归属与结算窗口 | 低危～中危 | 住 | P2 | 待评估 |
| GAP-FLW-007 | 医生签退 vs 待出院/未结费用（→ STALE-005） | 中危 | 住 | P2 | 见 SYS-IMP-007 |
| BR-IP-FLW-008 | 出院结算轨临床/费用写收口（`PENDING`+） | 中危 | 住 | P1 | **已实施** |

---

## 3. 分项：问题与修改方案

### GAP-FLW-001 校对通过转科/出院无财务 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（流程态与财务态脱节） |
| **关联 IF/BR** | IF-IP-AUD-02；IF-IP-NUR-ATD-05；**BR-IP-ATD-011** |
| **现象** | `NurseStationOrderAuditAppService#orchestrateEncounterAfterServiceVerified`：转科/出院 ServiceRequest 校对通过后直接 `initiateDepartmentTransfer` / `dischargeInpatientEncounter`，**不**调用 `assertDischargeGatePassed`，**不**查 `ChargeItem` 待结算。 |
| **根因** | 编排层仅处理就诊态迁移；财务 Gate 仅挂在 ATD「办理出院」按钮路径。 |
| **业务影响** | 存在大量 `PENDING_SETTLEMENT` 或 REFUNDING 时仍进入待出院；后续结算/清床扯皮。 |

**修改方案（推荐组合）**

1. **校对前硬 Gate（推荐）**：转科/出院类 ServiceRequest 校对通过前：
   - 调用 `assertDischargeGatePassed`（或抽取公共 `DischargeClinicalGate`）；
   - 新增 **`assertDischargeFinancialGate`**：`count(PENDING_SETTLEMENT) == 0` 且不存在 REFUNDING Payment（可配置「允许待出院后结算」则降级为警告）。
2. **或延迟改态**：校对仅标记 ServiceRequest VERIFIED，**不**改 encounter 为 ON_HOLD，直到 ATD 办理出院通过全 Gate（产品需确认与 AS 差异）。
3. 失败码：`EX-IP-AUD-204`（财务未清）/ 复用 `EX-IP-ATD-401`。

**涉及代码**

- `NurseStationOrderAuditAppService#orchestrateEncounterAfterServiceVerified`
- 新建 `InpatientDischargeFinancialGateSupport.java`
- `ChargeItemQueryService`、`PaymentReconciliationQueryService`

**验收用例**

- T1：存在 PENDING_SETTLEMENT → 校对出院嘱 → 拒绝，encounter 仍为 IN_PROGRESS。
- T2：临床 Gate 失败（长期未停）→ 拒绝（与 ATD 一致）。
- T3：全 Gate 通过 → ON_HOLD。

**工作量**：3～4 人天

---

### GAP-FLW-002 待出院期间仍可滚费/执行/划价

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（审查项；**产品确认为预期设计**） |
| **关联 IF/BR** | IF-IP-ARG-08；IF-IP-EXE-02；IF-IP-NUR-005 |
| **现象** | encounter `ON_HOLD` 后，滚费/执行/划价等写路径未见统一「非 IN_PROGRESS 禁止写」Gate。 |
| **设计口径** | 出院医嘱校对通过进入待出院后，**仍允许**滚费、执行、划价；费用硬门禁 **收口于清床**（`EX-IP-ATD-401/403`），而非 ON_HOLD 态。 |
| **业务影响** | 待出院窗口可补记/补执行；护士须在清床前完成费用收口。 |

**结论**：**不实施** ON_HOLD 写拦截。确保清床 Gate 完整；可选 GAP-FLW-003 办理出院前结清策略。

**验收用例**

- ON_HOLD 下滚费/执行/划价 → 允许。
- 未结清 → 清床 → `EX-IP-ATD-401`。

**状态**：设计如此（见独立问题单 [GAP-FLW-002](../问题清单/04-流程状态机与硬门禁/GAP-FLW-002.md)）

---

### GAP-FLW-003 办理出院 Gate 不含待结算费用

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-NUR-ATD-05；**BR-IP-ATD-016**（清床账户非零） |
| **现象** | `assertDischargeGatePassed` 覆盖医嘱与待发药（301/302/303），**不**包含「存在 PENDING_SETTLEMENT ChargeItem」。 |
| **根因** | 产品设计可能允许「先出院后结算」；AS 与实现需对齐。 |
| **业务影响** | 若院方要求出院前结清，当前 Gate 不足。 |

**修改方案**

1. **可配置策略**（推荐）：
   - `discharge.requireZeroPendingCharges=true` → 办理出院前 `pendingChargeCount==0`；
   - `false` → 仅清床/结算时拦（现状）。
2. 清床路径 `EX-IP-ATD-401` 保持硬拦未结算。

**涉及代码**

- `NurseStationATDManagerAppService#assertDischargeGatePassed`（扩展或拆财务 Gate）

**工作量**：1～2 人天

---

### GAP-FLW-004 清床 vs 办理出院 Gate 不一致

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 清床要求无未结算（401）、账户余额为零（403）；办理出院不要求 — 用户可「待出院 + 未结 + 占床」。 |
| **修改方案** | 文档化产品口径；或办理出院后自动触发占床释放策略与 IMP-004 床位联动。 |

**状态**：待评估（产品决策）

---

### GAP-FLW-005 出院召回与反结顺序

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-NUR-ATD-07；**BR-IP-RVS-*** |
| **现象** | 召回路径 `EX-IP-ATD-502` 反结失败时整单失败；与医保 2305 撤销（IMP-006）顺序需一致。 |
| **修改方案** | 召回 Saga：医保撤销 → 本地反结 → encounter 回滚；任一步失败可补偿；与 GAP-CHS-003/008 合并测试。 |

**涉及代码**

- `NurseStationATDManagerAppService` 召回分支
- `InpatientSettleAppService` 反结

**工作量**：2 人天（与 IMP-006 联调）

---

### GAP-FLW-006 转科后费用归属

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **现象** | 转科 ON_HOLD 后，旧科室 Charge 是否仍归旧 org 结算 — 需读 `ChargeItem.organizationId` 与转科后 encounter org 变更是否回写。 |
| **修改方案** | 转科完成时审计 Charge 归属；报表按 billDate/organization 分段。 |

**状态**：待评估

---

### GAP-FLW-007 医生签退 vs 流程态

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **修改方案** | 见 **SYS-IMP-007 GAP-STALE-005**（住院签退无执行/费用 Gate）；与本文件 FLW-001 叠加：待出院后医生仍可能签退未停长期嘱。 |

---

### BR-IP-FLW-008 出院结算轨临床/费用写收口（已实施 v1.2）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-EXE-02/04；IF-IP-CXL-01；IF-IP-PRC-02/04/06；IF-IP-STK-03；IF-IP-DOR-14/15/16/17/20/21/25；IF-IP-STL-05/06 |
| **现象（v1.0 修复前）** | 护士办理出院后就诊进入 `PENDING`，护士仍可执行/划价、医生仍可开立，与早期「出院后不可再变更」口径不一致。 |
| **设计口径（v1.2）** | **`ON_HOLD` / `PENDING` 均允许**滚费/执行/划价/开立（待结算窗口补账，含反结后再待结算）；**`COMPLETED` / `DISCHARGED` 禁止**临床与费用写。临床收口改挂 **FIN-003 出院全结 Pre**：复用 ATD-03/04/05 同源 **`InpatientDischargeClinicalGateSupport`**（长期未停、临时未完结、待发/待退药 → EX-IP-ATD-301/302/303/307）。 |
| **实现** | `InpatientPendingSettlementClinicalGateSupport`（仅拦 `COMPLETED`/`DISCHARGED`）；`InpatientDischargeClinicalGateSupport`（ATD-05 + FIN-003 共用）；前端 `encounter-clinical-order-gate.ts`。 |
| **用户提示（写拦截）** | 「患者已结算或已离院，不可变更医嘱及费用」 |
| **用户提示（结算拦截）** | 与办理出院一致（EX-IP-ATD-301/302/303/307） |

**验收用例**

- T1：`ON_HOLD` → 执行/划价/开立 → **允许**。
- T2：`PENDING` → 执行/不执行/取消执行/划价/取消划价/皮试保存/医生暂存签发 → **允许**。
- T2b：`PENDING` + 长期未停/待发药 → 预结算/正式出院全结 → **拒绝**（EX-IP-ATD-301～307）。
- T3：`COMPLETED` / `DISCHARGED` → 执行/划价/开立 → **拒绝**（EX-IP-EXE-307 等）。
- T4：`IN_PROGRESS` + 大量 `PENDING_SETTLEMENT` Charge → 执行/划价 → **允许**。

**状态**：**已实施 v1.2**（2026-07-11）

---

## 4. 流程态与 Gate 对照（目标态）

```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS
    IN_PROGRESS --> ON_HOLD: 校对转科/出院嘱
    ON_HOLD --> PENDING: 护士办理出院
    PENDING --> COMPLETED: 住院结算
    COMPLETED --> DISCHARGED: 清床离院
    ON_HOLD --> IN_PROGRESS: 召回
    COMPLETED --> IN_PROGRESS: 出院召回
    note right of ON_HOLD: 允许滚费/执行/划价
    note right of PENDING: 允许写；FIN-003出院全结Pre复用ATD Gate
    note right of COMPLETED: 禁止写；清床:未结算+余额Gate
```

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-FLW-001 | 校对转科/出院财务+临床 Gate | 3～4 |
| 2 | GAP-FLW-002 | ~~ON_HOLD 写路径统一 Gate~~ → 清床 Gate 验收 | — |
| 3 | GAP-FLW-003 | 可配置出院前结清 | 1～2 |
| 4 | GAP-FLW-005 | 召回 Saga | 2 |
| 5 | GAP-FLW-004/006/007 | 产品对齐 | 按需 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅在前端禁用「校对出院」 | 挡不住 API |
| 待出院后仍无限滚费再人工退 | 账实与审计困难（**例外**：待出院窗口内补记为设计允许，以清床 Gate 收口） |
| 校对与办理出院用两套不一致 Gate | 用户无法理解 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 有待结算 → 校对出院嘱 | 拒绝（若 requireZeroPending） |
| T2 | 长期未停 → 校对出院嘱 | EX-IP-ATD-301 |
| T3 | ON_HOLD → 滚费 Job | **允许** |
| T4 | ON_HOLD → 护士执行/划价 | **允许** |
| T4b | ON_HOLD + 未结清 → 清床 | `EX-IP-ATD-401` |
| T7 | `PENDING` → 执行/划价/开立 | **允许**；未清临床待办 → 出院全结 **拒绝**（EX-IP-ATD-301～307） |
| T7b | `COMPLETED`/`DISCHARGED` → 执行/划价/开立 | **拒绝**（**BR-IP-FLW-008**） |
| T5 | 全结清 → 校对 → 办理出院 → 清床 | 成功 |
| T6 | 已结算出院 → 召回 | 反结+态回滚 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：出院/转科/召回与财务硬门禁审查 |
| v1.2 | 2026-07-11 | **BR-IP-FLW-008 v1.2**：`PENDING` 待结算窗口允许临床写；FIN-003 出院全结 Pre 复用 `InpatientDischargeClinicalGateSupport` |
| v1.1 | 2026-06-28 | GAP-FLW-002 确认为设计如此：待出院允许动费用，清床收口 |
