# SYS-IMP-006 医保平台与本地结算顺序边界

> **问题类型**：外部集成与分布式顺序 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`ChsOutpatientSettlementFacadeImpl`、`ChsInpatientSettlementFacadeImpl`（对称）、`ChargeBatchLifecycle`、`OutpatientChargeAppService` 医保扩展、`OutpatientRegistrationInsuranceExtension`）。
>
> **关联 AS**：`AS-OP-FIN-002/003`、`AS-IP-FIN-003/004`；住院/门诊 AS 均标注医保 2303/2304/2305 为扩展/Gap
>
> **关联**：`SYS-IMP-002` GAP-FIN-001（本地正式结算双提交）、GAP-FIN-012（转医保切账户）、BND-FIN-005（医保预结算刷新）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

吉林 CHS 适配（`zxplat-module-chs-jilin`）将 **平台交易（2206/2207/2208、2303/2304/2305 等）** 与 **本地 Payment/ChargeItem/预结算快照** 编排在一起。并发问题不仅是「双按钮」，更是 **分布式顺序**：

| 顺序风险 | 典型后果 |
| --- | --- |
| 本地 SUCCESS 先于平台 2207/2304 confirm | 平台无单、本地已收费；对账不平 |
| 双 confirm 同一 ACTIVE 预结算快照 | 双 2207/2304、双 setl_id |
| 2208/2305 与本地退费/反结乱序 | 平台已撤、本地仍 SUCCESS |
| 预结算 refresh 与 confirm 交叉 | 金额不一致、draft 作废 |

**结论**：

- **本地金钱双提交**：仍以 SYS-IMP-002 为主；医保层须 **在本地条件更新之后** 或 **同一 Outbox/ Saga 步骤** 调用平台。
- **平台幂等**：`ChargeBatchLifecycle` 对部分撤销有幂等；**预结算 ACTIVE→SETTLED** 迁移需条件更新，防双 confirm。
- **补偿**：2207/2304 超时需 2601 冲正 + 本地回滚策略，须文档化且可演练。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-CHS-001 | 同一 ACTIVE 预结算快照双 confirm（2207/2304） | **高危** | 门+住 | P0 | 待实施 |
| GAP-CHS-002 | 本地 Payment 已 SUCCESS 但平台 confirm 失败/超时 | **高危** | 门+住 | P0 | 待实施 |
| GAP-CHS-003 | 2208/2305 撤销与本地退费/反结顺序 | 中危 | 门+住 | P1 | 待实施 |
| GAP-CHS-004 | 预结算 refresh 与 confirm 竞态 | 中危 | 门+住 | P1 | 待实施 |
| GAP-CHS-005 | 2201 就诊登记幂等与挂号并发 | 中危 | 门诊 | P2 | 部分已有 |
| GAP-CHS-006 | 转医保切账户与医保试算/confirm 交叉 | 中危 | 门诊 | P2 | 见 SYS-IMP-002 |
| GAP-CHS-007 | 3505/3506 库存追溯上报重复 | 低危～中危 | 药库 | P3 | 待评估 |
| GAP-CHS-008 | 住院反结 `runInsuranceRevokeBeforeCancel` 未接入主路径 | 中危 | 住 | P1 | 待实施 |

---

## 3. 分项：问题与修改方案

### GAP-CHS-001 同一 ACTIVE 预结算快照双 confirm

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（双 setl_id、基金重复记账） |
| **关联** | `ChsOutpatientSettlementFacadeImpl#confirmSettleInternal` / `doConfirmSettleInternal`；住院 2304 对称 |
| **现象** | 两收银窗对同一 `paymentDraftId` / `chrg_bchno` 几乎同时 confirm；均读到 `YbClinicPreSettle.STATUS_ACTIVE`，均调 2207/2304。 |
| **根因** | ① Gate 读 `pre.getStatus()`；② `markClinicPreSettleSettled` 普通 save，**无** `UPDATE yb_clinic_pre_settle SET status=SETTLED WHERE id=? AND status=ACTIVE`；③ 与 GAP-FIN-001 本地 Payment 双提交**叠加**。 |
| **现有兜底** | 平台可能拒绝第二笔；**不可依赖**平台作唯一防重。 |
| **业务影响** | 医保中心与 HIS 对账不平；冲正复杂。 |

**修改方案（推荐组合）**

1. **预结算快照条件迁移**（confirm 第一步）：

```sql
UPDATE yb_clinic_pre_settle
SET status = 'SETTLED', version = version + 1
WHERE id = :preId AND status = 'ACTIVE';
-- 影响行数=0 → 拒绝，提示重新预结算
```

2. **本地 Payment/ChargeItem 条件更新**（见 SYS-IMP-002 GAP-FIN-001）**先于**平台 invoke，或同一本地事务内完成本地态迁移后再调平台（失败则本地回滚）。
3. **业务幂等键**：`confirmSettle` 入参 `Idempotency-Key` + `(tenantId, chrg_bchno)` 唯一表；重复返回同一 `settlementRecordId`。
4. **住院对称**：`yb_inpatient_pre_settle` 同等改造。

**涉及代码**

- `chs-jilin-application/.../facadeimpl/ChsOutpatientSettlementFacadeImpl.java`
- `chs-jilin-application/.../inpatient/`（2304 路径）
- `chs-jilin-domain/.../settlement/service/ChargeBatchLifecycle.java`
- `chs-jilin-domain/.../outpatient/entity/YbClinicPreSettle.java`（建议加 `@Version`）

**验收用例**

- 两线程同一 preId confirm：仅 1 次 2207/2304，1 条 `YbSettlementRecord`。
- 第二笔：本地拒绝，**不**再调平台。

**工作量**：4～5 人天（含住院对称 + 幂等表）

---

### GAP-CHS-002 本地 SUCCESS 但平台 confirm 失败/超时

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危** |
| **现象** | 编排顺序错误：先 `confirmOutpatientChargeSettlement` 本地 SUCCESS，再调 `confirmSettle` 2207 超时/失败 → 患者已付、医保无结算。 |
| **根因** | 本地收银与医保扩展 **非原子**；缺少 Saga /  outbox 与 2601 补偿闭环。 |
| **修改方案** | ① **推荐顺序**：平台 2207/2304 成功 → 落 `YbSettlementRecord` → 本地 Payment SUCCESS（或同一事务最后一步）；② 若必须本地先扣款：引入 **TCC / 补偿表** 记录 `PENDING_PLATFORM_CONFIRM`，定时 2601 + 本地冲正；③ UI 禁止在 2206 未 ACTIVE 时启用正式结算。 |
| **涉及代码** | `OutpatientChargeAppService` 医保扩展、`ChsOutpatientSettlementFacadeImpl`、前端收银流程 |
| **工作量** | 5～8 人天（含联调与演练） |

---

### GAP-CHS-003 2208/2305 与本地退费/反结顺序

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `ChsOutpatientSettlementFacadeImpl#revokeSettleInternal`；`ChargeBatchLifecycle#markVoucherRevoked` |
| **现象** | 本地退费已完成，2208 未调或失败；或 2208 成功本地仍 SUCCESS。 |
| **现有兜底** | `markVoucherRevoked` 对已 REVOKED 幂等；2205 禁止撤 SETTLED 行（须先 2208）。 |
| **修改方案** | 与 AS-OP-FIN-003 / AS-IP-FIN-004 编排对齐：**enterRefundProcessingWindow → 2208/2305 → 本地逆向 Payment**；任一步失败整单 REFUND_PROCESSING 可回滚；记录 `yb_settlement_reversal` 与本地 `paymentId` 双向锚。 |
| **工作量** | 3～4 人天 |

---

### GAP-CHS-004 预结算 refresh 与 confirm 竞态

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | SYS-IMP-002 BND-FIN-005、`refreshPaymentDraftPreSettleAmounts` |
| **现象** | 线程 A refresh 2206 作废旧 ACTIVE 建新 ACTIVE；线程 B 对旧 preId confirm。 |
| **修改方案** | refresh 条件作废 `ACTIVE`；confirm 绑定 `preId` + `version`；前端 confirm 仅带最新 preId。 |
| **工作量** | 2 人天 |

---

### GAP-CHS-005 2201 就诊登记与挂号并发

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `OutpatientRegistrationInsuranceExtension`（注释：2201 幂等） |
| **现象** | 双挂号触发双 2201；或 2201 与退号交叉。 |
| **修改方案** | 2201 以 `(mdtrt_id|encounterId)` 幂等表；与 GAP-FIN-006 挂号防重联动。 |
| **工作量** | 2 人天 |

---

### GAP-CHS-006 转医保与 confirm 交叉

| 维度 | 说明 |
| --- | --- |
| **修改方案** | 见 **SYS-IMP-002 GAP-FIN-012**；转医保期间禁止 confirm；ChargeItem `PLANNED` 条件切 `accountId`。 |

---

### GAP-CHS-007 3505/3506 追溯上报

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **关联** | `ChsInventoryAppService`（注释：3505 幂等） |
| **现象** | 盘点/进货同意后重复触发 3505。 |
| **修改方案** | `(tenantId, traceCode, infNo, businessKey)` 幂等表；见 SYS-IMP-005 GAP-INV-007。 |
| **延伸** | 本地成功/上传失败 Outbox、对账台 → **SYS-IMP-011 GAP-INT-001/008** |

---

### GAP-CHS-008 住院反结医保撤销未接入主路径

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | AS-IP-FIN-004-SDD：`runInsuranceRevokeBeforeCancel` **已成码但未接入** `executeReverseInpatientSettle` |
| **现象** | 本地反结成功，2305 未撤，医保中心仍 SETTLED。 |
| **修改方案** | 反结编排强制 **2305 → 本地逆向** 或明确 feature flag 下拒绝反结；与 GAP-CHS-003 合并。 |
| **工作量** | 2～3 人天 |

---

## 4. 边界问题（非并发）

| 编号 | 主题 | 说明 |
| --- | --- | --- |
| BND-CHS-001 | 2206/2303 金额校验 | `validateSettlementAmounts` 与本地 draft 不一致时拒绝 |
| BND-CHS-002 | disabled 契约 NoOp | 无医保配置时跳过平台调用，本地纯自费路径须单独验收 |
| BND-CHS-003 | invoke 日志 | `ChsInvokeLogContextHolder` 与结算 record 关联，便于冲正 |
| BND-CHS-004 | 费别切换后 draft | 须重新 2206/2303，禁止旧 pre  confirm |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-CHS-001 + SYS-IMP-002 FIN-001 | 预结算 + Payment 条件更新后再调平台 | 5～7 |
| 2 | GAP-CHS-002 | 编排顺序 / 补偿表 / 2601 演练 | 5～8 |
| 3 | GAP-CHS-003 + 008 | 撤销与反结接入 2208/2305 | 4～6 |
| 4 | GAP-CHS-004、005、006 | 预结算 refresh、2201、转医保 | 4～6 |
| 5 | GAP-CHS-007 | 3505 幂等 | 1～2 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅依赖平台拒绝第二笔 confirm | 网络分区时不可靠 |
| 本地 SUCCESS 后再「尽力」调医保 | GAP-CHS-002 根因 |
| 无 2601 补偿演练 | 超时必现生产事故 |
| 跳过 `ChargeBatchLifecycle` 直接改 upload_status | 破坏 2205/2208 不变量 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 同一 preId 双 confirm | 1 次平台调用，1 settlementRecord |
| T2 | 2207 超时 | 本地不 SUCCESS，或补偿表 + 2601 闭环 |
| T3 | 2208 成功前本地退费 | 阻塞或回滚本地 |
| T4 | refresh 与 confirm 并行 | confirm 旧 pre 失败 |
| T5 | 2201 双调 | 1 mdtrt 登记 |
| T6 | 住院反结 | 2305 先于本地 REFUNDED |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：医保平台与本地结算顺序/并发边界审查 |
