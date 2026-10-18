# SYS-IMP-007 跨角色状态不同步与 Stale 页面边界

> **问题类型**：Stale 页面与跨角色 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`DoctorStationAdviceAppService`、`OutpatientChargeAppService`、`NurseStationOrderCancelExecuteAppService`、`InpatientDoctorStationAdviceAppService`、门诊/住院药房核发、`MedicationDispenseDomainService`）。
>
> **关联 AS**：`AS-OP-DOR-003/010`（签退）、`AS-OP-FIN-002`（门诊收费）、`AS-IP-DOR-003/006`（住院签退）、`AS-IP-NUR-012`（取消执行）、`AS-IP-PHA-001/002`、`AS-OP-PHA-001`
>
> **关联**：`SYS-IMP-002` GAP-FIN-001/003（正式结算无源态校验）；`SYS-IMP-001` GAP-EXE-009（Dispense 无乐观锁）；`SYS-IMP-005` GAP-INV-003/004
>
> **状态**：待实施

---

## 1. 背景与结论摘要

临床系统常见 **多角色并行**（医生签退、护士取消执行、款台预结算/正式结算、药房配药/核发），前端列表往往 **不实时推送**。ZXPlat 后端策略是：

| 策略 | 含义 | 典型落点 |
| --- | --- | --- |
| **写路径重读 Gate** | 提交时重新加载实体再校验 | 取消执行 `assertSettlementGate`、门诊 `preSettleChargeItems` 要求 `PLANNED` |
| **仅拦终态** | 只拒绝 `SETTLED`/`REFUNDED` 等 | 门诊 `assertNotSettledChargeItems` |
| **不查软删/锚点** | 发放写路径不看 `is_deleted`、不绑医嘱态 | 药房 `mustGet` + `ensureStatusForPrepare` |
| **AS 未落地** | 规格有错误码，实现缺失 | ~~`EX-IP-ORD-103` 已收费不可签退~~（GAP-STALE-005 已实施） |

**结论**：

- **刷新后再操作**：多数路径行为正确（签退后无法再预结算；已结算后不可取消执行）。
- **Stale 页面 / 双窗不刷新**：存在 **对已撤回医嘱收款**、**取消执行后仍配药/核发** 等缺口；与 SYS-IMP-002 的「读 Gate + 无源态 UPDATE」同族，但触发条件是 **跨角色时序** 而非纯并发双点。

**与并发文档的区别**：本文件聚焦 **「A 角色已改库态，B 角色仍持旧 UI 提交」**；双窗同时点同一按钮见 IMP-001/002/005。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-STALE-001 | 门诊：预结算后医生签退，款台用旧 draft 正式结算 | **高危** | 门诊 | P0 | **已实施** |
| GAP-STALE-002 | 门诊/住院：正式结算或核发双提交（Stale + 并发） | **高危** | 门+住 | P0 | 见 SYS-IMP-002 |
| GAP-STALE-003 | 住院：取消执行软删 Dispense 后，药房仍可对同一 id 配药/核发 | 中危 | 住 | P1 | 已实施 |
| GAP-STALE-004 | 住院：药房已配药（IN_PROGRESS/PREPARED）后护士取消执行 | 中危 | 住 | P1 | 已实施 |
| GAP-STALE-005 | 住院：医生签退已执行未结算医嘱（无执行/发药/费用 Gate） | 中危 | 住 | P2 | **已实施** |
| GAP-STALE-006 | 汇总/核发 Stale 页双操作 | 中危 | 住 | P2 | 已实施 |
| GAP-STALE-007 | 仅前端 disable、写路径不重读锚点态 | 中危 | 门+住 | P2 | **已实施**（Gate 矩阵） |

---

## 3. 分项：问题与修改方案

### GAP-STALE-001 门诊签退后 Stale 正式结算

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（对已撤回医嘱收款） |
| **关联 IF/BR** | IF-DOR-16 签退；IF-OPC-07 正式结算；**BR-ORD-017** 签退回滚 `PLANNED→DRAFT` |
| **典型时序** | ① 款台预结算（Charge 仍 `PLANNED`，生成 `paymentDraftId`）→ ② 医生签退（`rollbackPlanned…` → `DRAFT`，`releaseDispenseReserve`，`signOff`）→ ③ 款台**未刷新**，仍用旧 draft 点「正式结算」。 |
| **根因** | ① `confirmOutpatientChargeSettlement` 重载 Charge 后仅 `assertNotSettled`（拦 `SETTLED`/`REFUNDED`），**不**要求 `PLANNED`；② `settleChargeItems` 无源态条件更新（**GAP-FIN-003**）；③ 未校验锚点 `MedicationRequest` 仍为可收费态（非 `signOff`/非软删）。 |
| **现有兜底** | 若用户**重新预结算**：`preSettleChargeItems` 要求 `PLANNED` → 失败；签退后 `completeMedicationRequestAfterOutpatientCharge` 未触发（因未结算）。 |
| **业务影响** | 患者为已撤回医嘱付款；`syncAdviceStatesAfterChargeSettlement` 仍可能推进发药。 |

**修改方案（推荐组合）**

1. **正式结算 Gate（核心）**：`confirmOutpatientChargeSettlement` 在 `settleChargeItems` 前增加：
   - 全部 `chargeItem.status_enum == PLANNED`；
   - 锚点医嘱/发放未签退（`RequestStatus` 非回草稿路径 / Dispense 未 `is_deleted`）。
2. **条件 UPDATE（与 GAP-FIN-003 合并）**：`UPDATE … SET status=SETTLED WHERE id IN (…) AND status_enum='PLANNED'`。
3. **draft 失效**：签退成功同事务或事件后，作废该就诊下仍挂载已回滚 Charge 的 `DRAFT` Payment（扩展 `cancelStaleOutpatientPaymentDrafts` 口径：绑定项含非 `PLANNED` 则作废 draft）。
4. **前端**：签退/预结算互斥提示；正式结算失败码明确「医嘱已变更，请重新预结算」。

**涉及代码**

- `OutpatientChargeAppService#confirmOutpatientChargeSettlement`
- `ChargeItemDomainService#settleChargeItems`
- `DoctorStationAdviceAppService#withdrawDoctorStationAdviceBatch`（可选发布「签退作废 draft」事件）
- `DoctorStationTcmAppService#withdrawOrDeleteTcmHerbalPrescription`（对称）

**验收用例**

- T1：预结算 → 签退 → 旧 draft 正式结算 → **拒绝**，无 SUCCESS Payment。
- T2：预结算 → 签退 → 重新预结算 → 仅未签退行可结。
- T3：已 SETTLED 后签退 → 签退失败（医嘱 `COMPLETED`）。

**工作量**：2～3 人天（与 GAP-FIN-003 合并）

---

### GAP-STALE-002 正式结算 / 核发双提交

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危** |
| **修改方案** | 见 **SYS-IMP-002 GAP-FIN-001**、**SYS-IMP-005 GAP-INV-004**；Stale 与并发为同一修复。 |

---

### GAP-STALE-003 取消执行后药房仍对软删 Dispense 操作

| 维度 | 说明 |
| --- | --- |
| **状态** | **已实施**（2026-06-28） |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-CXL-01；**BR-IP-CXL-007**（未发软删 Dispense） |
| **典型时序** | ① 护士取消执行（`PREPARATION` → `removeUndispensedDispense` / `markDeleted`）→ ② 药房列表未刷新，对同一 `dispenseId` 点「保存配药」或「核发」。 |
| **根因** | ① `MedicationDispenseQueryService#validateDispensePreparable` / `#validateDispensable` 只查 `status_enum`，**不**查 `is_deleted`；② `MedicationDispenseDomainService#mustGet` / `#prepareMedicationDispense` / `#dispenseMedication` 不拒绝软删行；③ 列表 Query 若未过滤 `is_deleted`，加剧 Stale 感知。 |
| **现有兜底** | 若取消后 id 已从列表消失且用户重查 → 无法提交；库存核发仍有 `InventoryItem` 硬扣（但可能发「幽灵单」）。 |
| **业务影响** | 取消执行后仍配药/核发；账实与执行记录不一致。 |

**修改方案**

1. **域层统一 Gate**：`mustGet` 或各 `ensure*` 增加 `assertNotDeleted()`；软删 → `MEDICATION_DISPENSE_NOT_FOUND` 或 `INVALID_STATUS`。
2. **Query 侧**：待发/配药列表 Specification 默认 `is_deleted = false`（核对所有药房/护士 Query 入口）。
3. **取消执行**：`removeUndispensedDispense` 可选将状态改为 `DECLINED` 或 `ON_HOLD`，使 stale 提交在状态层也失败（与释占事件一致）。
4. **条件更新**：核发 `UPDATE … WHERE id=? AND is_deleted=false AND status_enum IN (…)`（叠加 SYS-IMP-001 GAP-EXE-009）。

**涉及代码**

- `MedicationDispense.java`、`MedicationDispenseDomainService.java`
- `MedicationDispenseQueryService.java`（`validateDispensePreparable` / `validateDispensable`）
- `OutpatientPharmacyDispenseAppService`、`InpatientPharmacyDispenseAppService`
- `NurseStationOrderCancelExecuteWriteAssembler#rollbackMainDispenseAndCharge`

**验收用例**

- T1：取消执行后立即对同一 `dispenseId` 配药 → 失败。
- T2：列表仍显示该行（模拟 stale）但提交 → 失败。
- T3：取消前已 `IN_PROGRESS` → 见 GAP-STALE-004。

**工作量**：2～3 人天

---

### GAP-STALE-004 药房已配药后护士仍可取消执行（费退药仍发）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-CXL-01；**BR-IP-CXL-010**（已核发走待退药，**未覆盖 IN_PROGRESS/PREPARED**） |
| **典型时序** | ① 药房「保存配药」（`PREPARATION→IN_PROGRESS`）→ ② 护士取消执行 → ③ 费用回滚/void，Dispense **未**删除 → ④ 药房「核发」成功。 |
| **根因** | `rollbackMainDispenseAndCharge` 仅分支处理 `PREPARATION` / `SUMMARIZED` / `COMPLETED`；**无** `PREPARED` / `IN_PROGRESS` 分支；药房写路径**不**校验 Charge 仍为 `PENDING_SETTLEMENT`。 |
| **现有兜底** | 若已 `COMPLETED`，取消走 `createPendingRefundDispense`；已结算费用 `EX-IP-CXL-203` 拒绝。 |
| **业务影响** | 费用已取消/void，药品仍发出；住院账实不符。 |

**修改方案（二选一或组合）**

1. **取消执行 Gate（推荐）**：存在关联 Dispense 态 ∈ `{PREPARED, IN_PROGRESS}` → **EX-IP-CXL-204** 或新码「药房配药中，请先作废配药或完成退药流程」。
2. **取消时强制回退 Dispense**：对 `PREPARED/IN_PROGRESS` 调用 `cancelMedicationDispense`（`DECLINED`）或回退 `PREPARATION`，再 void Charge。
3. **核发 Gate**：`dispenseMedication` 前校验关联 Charge（按 `procedureId` 或 dispense 锚点）仍为 `PENDING_SETTLEMENT` 且 Procedure 未被 CANCEL 对冲。

**落地（2026-06-28）**：方案 1 + 3。

**涉及代码**

- `NurseStationOrderCancelExecuteWriteAssembler#assertPharmacyPreparingDispenseGate`
- `InpatientPharmacyDispenseAppService#assertDispenseExecutionChargeGate`
- `InpatientPharmacyDispenseAppService#dispenseInpatientMedications`
- `ChargeItemQueryService` + 取消执行 Pre 链

**验收用例**

- T1：`IN_PROGRESS` 后取消执行 → 拒绝或 Dispense 同步作废。
- T2：取消成功后同一 dispense 核发 → 失败。
- T3：`COMPLETED` 仍走待退药（回归 BR-IP-CXL-010）。

**工作量**：3～4 人天

---

### GAP-STALE-005 住院医生签退已执行未结算医嘱（已实施）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **状态** | **已实施**（v1.1，2026-06-28） |
| **关联 IF/BR** | IF-IP-DOR-16；AS **EX-IP-ORD-103**（已收费不可签退）、**EX-IP-ORD-106**；住院中药 **BR-IP-TCM-009** 已有门控 |
| **典型时序** | ① 护士已执行（Procedure + Dispense + `PENDING_SETTLEMENT` Charge）→ ② 医生签退（仅 `signOffMedicationOrder`）→ ③ 请求回草稿，**执行/发放/费用仍在**。 |
| **根因** | `InpatientDoctorStationAdviceAppService#withdrawInpatientDoctorStationAdviceBatch` **不**回滚执行与费用；`ensureSignOffAllowed` 仅要求 `ACTIVE`；**未实现** AS 的 `EX-IP-ORD-103`；西药路径**无** `InpatientTcmRxRevokeGatesupport 同类校验。 |
| **现有兜底** | 费用 **SETTLED** 后取消执行仍被 `EX-IP-CXL-203` 拦截；中药签退有 `assertNoNetExecutionOrCompletedDispenseOrLockedCharge`。 |
| **业务影响** | 医嘱显示已撤回，护士站/药房仍见执行任务；结算口径混乱。 |

**修改方案**

1. **签退 Gate（对齐 AS）**：签退前批量检查：无净剩余 `COMPLETED` Procedure、无 `COMPLETED`/`SUMMARIZED` Dispense（可配置是否含 `PREPARATION`）、无 `PENDING_SETTLEMENT`/`SETTLED` Charge（按 medReqId 锚点）→ 否则 **EX-IP-ORD-103**。
2. **复用组件**：抽取 `InpatientAdviceRevokeGatesupport（西药/诊疗/中药共用），逻辑参考 `InpatientTcmRxRevokeGatesupport。
3. **产品口径**：若允许「签退但保留已发生事实」，则 AS 改为文档化「仅停止后续」，签退 UI 区分「停止医嘱」与「撤回未执行」——**当前实现与 AS 错误码不一致，须二选一**。

**涉及代码**

- `InpatientDoctorStationAdviceAppService#withdrawInpatientDoctorStationAdviceBatch`
- 新建 `InpatientAdviceRevokeGatesupport（或扩展 TCM Support）
- `InpatientNurseStationConstants` / `ErrorCodeConstants` 增加 `EX-IP-ORD-103`

**验收用例**

- T1：已执行未结算 → 签退 → 拒绝 EX-IP-ORD-103。
- T2：仅签发未校对 → 签退成功。
- T3：已结算 → 签退拒绝（或走退费，不按签退）。

**工作量**：2～3 人天

---

### GAP-STALE-006 汇总/核发 Stale 页

| 维度 | 说明 |
| --- | --- |
| **状态** | **已实施**（2026-06-28） |
| **严重度** | 中危 |
| **典型时序** | ① 护士/药房在 Stale 列表对同一批 `dispenseId` 或 `summaryNo` 重复提交；② 读 Gate 仍显示可汇总/可核发；③ 若无源态 UPDATE 则双写成功。 |
| **修改方案** | 见 **SYS-IMP-005 [GAP-INV-003](../01-并发双写与条件更新/GAP-INV-003.md)/004**；汇总 `summarizeIfPreparationWhereIdIn` → EX-IP-SUM-201；核发扣库前 Supply/Dispense 条件迁移 → EX-IPS-201。 |
| **验收** | T1 双窗同批汇总 → 1 summaryNo；T3 双窗同 summaryNo 核发 → 1 成功。 |

---

### GAP-STALE-007 仅前端防重、后端不重读

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（已知模式） |
| **现象** | 各模块 AS §7 写「双开以后写者为准」，但仅靠按钮 disable；接口直调、Stale Tab 仍可提交。 |
| **修改方案** | 所有 **跨角色写接口** 在 Cmd 前增加 **最小重读 Gate**（ Charge 态 / Dispense 态 / 医嘱态 三选其二按场景）；前端作体验层。见各 IMP 文档 §6「不建议的做法」。 |

---

## 4. 按角色速查（Stale 页是否还能往下走）

| 上游操作 | 下游操作 | 刷新后 | Stale 未刷新 |
| --- | --- | --- | --- |
| 门诊医生签退 | 款台重新预结算 | **不能**（非 PLANNED） | **能正式结算**（GAP-STALE-001） |
| 门诊医生签退 | 款台正式结算（已 SETTLED 前从未签退） | 签退失败（已 COMPLETED） | N/A |
| 门诊医生签退 | 门诊药房发药（未收费） | 一般无 PREPARATION | 少见 |
| 护士取消执行 | 住院药房配药/核发（PREPARATION） | **不能**（软删后应失败） | **不能**（GAP-STALE-003 已实施） |
| 护士取消执行 | 住院药房核发（已 IN_PROGRESS） | **不能**（EX-IP-CXL-204） | **不能**（GAP-STALE-004 已实施） |
| 护士取消执行 | 住院结算 | 未结算行已 void | 正常 |
| 护士取消执行 | 同批已 SETTLED | **不能**（EX-IP-CXL-203） | **不能** |
| 住院医生签退 | 护士再执行 | 视请求态 | 签退成功但执行记录仍在（GAP-STALE-005） |
| 住院医生签退 | 款台/住院结算 | 待结算行仍在 | 仍可结算未撤回部分 |

---

## 5. 边界问题（非 Stale，但易混淆）

| 编号 | 主题 | 说明 |
| --- | --- | --- |
| BND-STALE-001 | 门诊签退注释 vs 正式结算 | 签退注释写「避免继续预结算」，但未声明「作废已有 draft」 |
| BND-STALE-002 | `hasUnsettledBillingStatement` | draft 绑定 Charge 为 `DRAFT` 时仍视未结清，但**不阻止** confirm 旧 draft |
| BND-STALE-003 | 已收费门诊退药 | 须走退费非签退；与 Stale 无关 |
| BND-STALE-004 | 取消执行 CANCEL Procedure | 对冲后列表仍可能显示「已执行」直到刷新；读侧投影问题 |

---

## 6. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-STALE-001 + SYS-IMP-002 FIN-003 | 正式结算 PLANNED Gate + 条件 UPDATE | 2～3 |
| 2 | GAP-STALE-003 | Dispense 软删 Gate + 列表过滤 | 2～3 |
| 3 | GAP-STALE-004 | 取消执行 vs IN_PROGRESS 互斥 | 3～4 |
| 4 | GAP-STALE-005 | 住院签退 EX-IP-ORD-103 | 2～3 | **已实施** |
| 5 | GAP-STALE-006、007 | Gate 矩阵 + 体验层 | 1～2 |

**Gate 矩阵（Wave 2 产出）**：[SYS-IMP-007-跨角色写接口Gate矩阵](./SYS-IMP-007-跨角色写接口Gate矩阵.md)

---

## 7. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅提示用户「请先刷新页面」 | 挡不住双 Tab、接口重放 |
| 只在 Query 过滤软删、写路径不校验 | Stale id 直调 API 仍可写 |
| 取消执行不处理 IN_PROGRESS 却依赖药房自觉 | GAP-STALE-004 根因 |
| 住院签退不回滚执行却无任何 Gate | 与 AS EX-IP-ORD-103 矛盾 |

---

## 8. 体验层加固（可与 P1 并行）

| 项 | 做法 |
| --- | --- |
| 款台 | 正式结算前静默 `preSettle` 或校验 Charge 快照；失败强制刷新 |
| 药房 | 配药/核发提交前按 id 批量 `validate*`；403 统一文案 |
| 护士/医生 | 写成功后 `websocket`/轮询刷新受影响就诊 |
| 全局 | 跨角色关键写接口返回 `affectedEncounterId` 供前端 invalidate |

---

## 9. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 预结算 → 签退 → 旧 draft 结算 | 拒绝 |
| T2 | 取消 PREPARATION → 同 id 配药 | 拒绝 |
| T3 | 配药 IN_PROGRESS → 取消执行 | 拒绝或 Dispense 作废 |
| T4 | 取消执行 → 同 id 核发 | 拒绝 |
| T5 | 已执行未结算 → 住院签退 | EX-IP-ORD-103 |
| T6 | 已 SETTLED → 取消执行 | EX-IP-CXL-203 |
| T7 | 签退 → 重新预结算 | 仅有效行 |

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.1 | 2026-06-28 | GAP-STALE-006 汇总/核发条件更新已实施 |
| v1.0 | 2026-06-21 | 初稿：跨角色 Stale 页面与状态不同步边界审查 |
| v1.1 | 2026-06-28 | STALE-001/005/006/007 状态同步；附录 Gate 矩阵（Health + IMP-003/004） |
| v1.1 | 2026-06-28 | GAP-STALE-005 落地：住院签退 EX-IP-ORD-103 Gate |
