# SYS-IMP-002 收费、结算与账户并发边界

> **问题类型**：并发双写与条件更新 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`OutpatientChargeAppService`、`InpatientSettleAppService`、`InpatientPrepayAppService`、`PaymentReconciliationDomainService`、`ChargeItemDomainService`、`AccountDomainService`、住院护士执行/划价写路径）。
>
> **关联 AS**：`AS-OP-FIN-001/002/003/004`、`AS-IP-FIN-002/003/004`、`AS-IP-NUR-004/005/012`
>
> **关联**：`SYS-IMP-001`（医嘱执行重复会导致重复 `PENDING_SETTLEMENT` 费用行）；`SYS-IMP-010` GAP-BAT-001/004（滚费/日结）；`SYS-IMP-008` GAP-ACL-001（财务越权）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

ZXPlat 金钱链路采用 **「ChargeItem 状态机 + PaymentReconciliation 流水 + Account 余额」** 三层模型，AppService 由 `AppServiceAspect` 包裹单事务。业务规则在 AS 中已有较多 **BR-OPC-005、BR-IP-STL-007、BR-IP-STL-010** 等防重描述，但实现层大量依赖 **读时 Gate + 内存改态写回**，与发药库存问题同族。

**结论**：

| 类型 | 是否已有硬兜底 | 说明 |
| --- | --- | --- |
| **重复收款（同一 ChargeItem 双 SUCCESS 流水）** | **弱** | 依赖 `findPaySuccessOrRefundingOverlappingChargeItems` 读时交集；ChargeItem `settleChargeItems` **门诊路径不校验源状态**；实体 **未映射** DB `version` |
| **账户余额错账** | **弱** | `adjustAdvanceDepositBalance` 读-改-写无行锁/乐观锁（表有 `version` 列） |
| **重复退费** | **中** | `enterRefundProcessingWindow` + `existsByRelationId` 有部分保护；仍依赖读时状态 |
| **重复挂号占号** | **中** | 当日同科室防重在开立就诊**之前**读库，存在 TOCTOU |
| **已结算后仍取消执行/退药** | **强** | `BR-IP-CXL-203`、退费 Gate 等显式拒绝 |

**与 SYS-IMP-001 的交叉**：住院同一 planTime 双执行（GAP-EXE-002）会直接产生 **双条 `ORDER_EXECUTE` 待结算 ChargeItem**，属于 **金钱后果** 而不仅是发药问题。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-FIN-001 | 正式结算 ChargeItem / Payment 双提交（读时防重 TOCTOU） | **高危** | 门+住 | P0 | 待实施 |
| GAP-FIN-002 | 账户余额 `adjustAdvanceDepositBalance` 丢失更新 | **高危** | 住 | P0 | 待实施 |
| GAP-FIN-003 | 门诊 `settleChargeItems` 不校验源状态（PLANNED） | **高危** | 门诊 | P0 | 待实施 |
| GAP-FIN-004 | 财务实体 DB 有 `version` 但 JPA 未映射乐观锁 | 中危 | 门+住 | P1 | 待实施 |
| GAP-FIN-005 | 退款受理窗口 `enterRefundProcessingWindow` 并发 | 中危 | 门+住 | P1 | 待实施 |
| GAP-FIN-006 | 门诊挂号当日同科室防重 TOCTOU | 中危 | 门诊 | P2 | 待实施 |
| GAP-FIN-007 | 住院预结算多草稿 / BR-IP-STL-010 作废竞态 | 低危～中危 | 住 | P2 | 待实施 |
| GAP-FIN-008 | 住院预交金并发收/退（文档已知） | 中危（已知） | 住 | P2 | 待实施 |
| GAP-FIN-009 | 收费员日结 payment 挂载并发 | 中危（已知） | 门+住 | P2 | 文档化 |
| GAP-FIN-010 | 护士执行/划价重复写 Charge（→ GAP-EXE-002 等） | 中危 | 住 | P1 | 见 SYS-IMP-001 |
| GAP-FIN-011 | 住院反结 / 门诊退费并发（文档已知） | 中危（已知） | 门+住 | P2 | 文档化 |
| GAP-FIN-012 | 转医保/转自费批量切账户无 ChargeItem 行锁 | 低危～中危 | 门诊 | P3 | 待评估 |

---

## 3. 分项：问题与修改方案

### GAP-FIN-001 正式结算双提交（读时防重 TOCTOU）

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（直接多收患者钱） |
| **关联 IF/BR** | IF-OPC-07 / **BR-OPC-005**；IF-IP-STL-06 / **BR-IP-STL-007** |
| **现象** | 两个收银窗口对**同一批 ChargeItem** 几乎同时点「正式结算」；或同一草稿支付单双提交。 |
| **根因** | ① `assertNotAlreadySettled` / `findPaySuccessOrRefundingOverlappingChargeItems` 在事务开头**只读**；② `settleOutpatientPayment` 仅 `assertStatusTransitionAllowed(DRAFT)` 读时校验；③ 两事务均可能在对方提交 SUCCESS 前通过 Gate。 |
| **现有兜底** | 第二笔若**重新加载**到 ChargeItem 已 SETTLED，`assertNotSettledChargeItems` 可拦——**仅当加载发生在第一笔提交之后**；无 DB 条件更新则不可靠。 |
| **业务影响** | 同一费用双 SUCCESS 流水、患者多付、日结对账不平。 |

**修改方案（推荐组合）**

1. **条件更新（核心）**：`ChargeItemDomainService#settleChargeItems` / `#settleInpatientChargeItems` 改为：
   - `UPDATE adm_charge_item SET status_enum=?, version=version+1 WHERE id IN (...) AND status_enum IN (期望源态)`；
   - 影响行数 ≠ 期望条数 → 抛 `CHARGE_ITEM_INVALID_STATUS`（重复结算）。
2. **Payment 条件迁移**：`settleOutpatientPayment` / `#settleInpatientPayment` 使用 `UPDATE ... WHERE id=? AND status_enum='DRAFT'` 或 JPA `@Version`。
3. **提交前二次校验（可选）**：正式结算 Cmd 序列末尾再查一次 `findPaySuccessOrRefundingOverlappingChargeItems`。
4. **幂等键（体验层）**：`confirmOutpatientChargeSettlement` / `confirmInpatientSettle` 支持 `Idempotency-Key`，同 key 返回同一 `paymentId`。

**涉及代码**

- `ChargeItemDomainService.java`（`batchUpdateStatus` 或专用 settle 方法）
- `ChargeItemRepository`（新增 `updateStatusIfIn`）
- `PaymentReconciliationDomainService.java`
- `OutpatientChargeAppService#confirmOutpatientChargeSettlement`
- `InpatientSettleAppService#confirmInpatientSettle`

**验收用例**

- 两线程同一 `chargeItemIds` 正式结算：仅 1 条 SUCCESS 流水，另 1 明确失败。
- 同一 `paymentDraftId` 双 confirm：第二笔「支付草稿状态非法」。

**工作量**：3～4 人天

---

### GAP-FIN-002 账户余额丢失更新

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（预交金余额错、出院少退/多扣） |
| **关联 IF/BR** | IF-IP-PREP-03/04；IF-IP-STL-06（结算扣余额）；**BR-IP-PREP-005** |
| **现象** | 同一就诊并发「收预交金 + 住院结算扣余额」或「双收预交金」。 |
| **根因** | `AccountDomainService#adjustAdvanceDepositBalance`：`getBalance()` → `add(delta)` → `saveOrUpdateAccount`，**无** `SELECT FOR UPDATE`；`adm_account.version` 存在但实体**未** `@Version`。 |
| **现有兜底** | 退费 delta 为负时 `nextBalance < 0` 抛 **EX-IP-PREP-005**——仅对单线程读到的 `current` 有效；并发下两笔正向收取可能都基于同一 `current` 写回。 |
| **业务影响** | 展示余额与流水不一致；出院全结多退/少补错误。 |

**修改方案**

1. **行锁（推荐）**：`requireActivePersonalCashAccount` 改为 `findForUpdateByPatientIdAndEncounterIdAndTypeCode`（`FOR UPDATE`）。
2. **或乐观锁**：`Account` 映射 `@Version`；冲突 → 「账户余额已变更，请刷新」。
3. **流水幂等**：预交金收/退接口带业务幂等键（如 `busNo` / 前端 requestId），重复提交返回同一 `paymentId`。

**涉及代码**

- `AccountDomainService.java`
- `AccountRepository.java`
- `Account.java`（实体）
- `InpatientPrepayAppService.java`
- `InpatientSettleAppService.java`（`adjustAdvanceDepositBalance` 调用链）

**验收用例**

- 并发两笔 +1000 预交金：余额 +2000，非 +1000。
- 并发收预交金与结算扣款：最终余额 = 初始 + 收 − 扣。

**工作量**：2～3 人天

---

### GAP-FIN-003 门诊 settleChargeItems 不校验源状态

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（GAP-FIN-001 子项，单独列出便于 Code Review） |
| **关联** | IF-OPC-07；`ChargeItemDomainService#settleChargeItems` |
| **现象** | 住院 `settleInpatientChargeItems` 要求源态 `PENDING_SETTLEMENT`；门诊 `settleChargeItems` 直接 `batchUpdateStatus` → SETTLED，**不校验 PLANNED**。 |
| **根因** | `batchUpdateStatus` 通用方法无源态门禁。 |
| **业务影响** | 异常态 ChargeItem 被强行 SETTLED；与 GAP-FIN-001 叠加放大双结风险；**医生签退后 Stale 正式结算**见 **SYS-IMP-007 GAP-STALE-001**。 |

**修改方案**

- 门诊 settle 与住院对称：批量更新前断言 `status_enum = PLANNED`（或预结算后的允许态集合）。
- 与 GAP-FIN-001 条件更新一并实现；签退/draft 失效见 **SYS-IMP-007**。

**工作量**：含在 GAP-FIN-001

---

### GAP-FIN-004 财务实体乐观锁未映射

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（跨切面，放大 GAP-FIN-001/002/005） |
| **现象** | 表 `fin_payment_reconciliation`、`adm_charge_item`、`adm_account` 均有 `version` 列及 COMMENT「乐观锁」；对应 JPA 实体**多数未** `@Version`。 |
| **业务影响** | 并发写 last-write-wins；与 AS-SDD 承诺不一致。 |

**修改方案**

1. 为 `PaymentReconciliation`、`ChargeItem`、`Account` 增加 `@Version Integer version`。
2. 捕获 `OptimisticLockException` → 统一业务码「数据已被他人修改，请刷新」。
3. 与 GAP-FIN-001 条件更新二选一或叠加（条件更新更硬，乐观锁作补充）。

**工作量**：2 人天（含回归）

---

### GAP-FIN-005 退款受理窗口并发

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-REF-03；退号 IF-REG-10；`enterRefundProcessingWindow` |
| **现象** | 两窗口对同一 SUCCESS 支付单同时发起退费。 |
| **根因** | 均读到 `SUCCESS` → 均 `setStatusEnum(REFUND_PROCESSING)`；`requestOutpatientRefund` 有 `existsByRelationId` 防重复逆向单，但**原单状态**仍可能被重复推进。 |
| **现有兜底** | 逆向单 `relationId` 唯一；完整退费流程仍有金额校验。 |
| **业务影响** | 极端下双逆向单申请或状态机混乱。 |

**修改方案**

1. `enterRefundProcessingWindow`：`UPDATE fin_payment_reconciliation SET status_enum='REFUND_PROCESSING' WHERE id=? AND status_enum IN ('SUCCESS','PARTIAL_REFUNDED')`。
2. 映射 `@Version`（GAP-FIN-004）。
3. 退费 App 层：进入窗口失败 → 提示「已在退款处理中」。

**工作量**：1～2 人天

---

### GAP-FIN-006 门诊挂号当日同科室防重 TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF** | IF-REG-01；`checkOutpatientDuplicateRegistration` |
| **现象** | 双窗口同时给同患者挂同科室号：均通过防重 → 两条有效就诊/双挂号费。 |
| **根因** | 防重在 `preRegisterSettlement` 读路径；开立就诊与 Charge 创建在之后，无唯一约束。 |
| **业务影响** | 重复挂号费、占两个号源名额。 |

**修改方案**

1. DB 部分唯一索引（需定义「有效就诊」口径）：如 `(patient_id, organization_id, registration_date)` WHERE `status NOT IN (CANCELLED)` AND 非 PLANNED 未交费态——**须与产品确认 PLANNED 是否占名额**（AS 写：PLANNED 未交费不占名额）。
2. 或 `Encounter` 创建时 `SELECT FOR UPDATE` 患者当日挂号计数行。

**工作量**：2～3 人天（含存量清洗）

---

### GAP-FIN-007 住院预结算多草稿竞态

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **关联 IF/BR** | IF-IP-STL-05；**BR-IP-STL-010** |
| **现象** | 并发预结算：均作废旧 DRAFT 后各建一新 DRAFT；短暂存在多条 INPATIENT_CLINIC 草稿。 |
| **根因** | 作废与新建非全局串行；读 `findPaymentsByEncounter` 快照。 |
| **现有兜底** | `findDraftPaymentIdsByEncounterIds` 取 **max(id)** 给前端；正式结算绑具体 `paymentDraftId`。 |
| **业务影响** | 用户若用旧草稿 id 结算可能失败；一般不易双扣款。 |

**修改方案**

1. 预结算入口对 `encounterId` 应用层 mutex / DB advisory lock。
2. 或唯一约束：每就诊仅一条 `INPATIENT_CLINIC` + `DRAFT`（作废改 CANCELLED 后再 insert）。

**工作量**：1～2 人天

---

### GAP-FIN-008 住院预交金并发收/退

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（**AS-IP-FIN-002 §7 已知**：「同一就诊并发收退以后写者余额校验为准」） |
| **现象** | 与 GAP-FIN-002 同源；文档已披露但未硬防。 |
| **修改方案** | 实施 GAP-FIN-002 即可；补充集成测试覆盖收+退+结算交错。 |

---

### GAP-FIN-009 收费员日结 payment 挂载并发

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（**AS-OP-DCL-005 / BR-CDS-008** 已定义） |
| **现象** | 两收费员同时交账，同一 payment 被两批次挂载。 |
| **现有兜底** | 提交时校验 payment 未挂载 → **EX-OP-DCL-301** 整单失败。 |
| **修改方案** | 保持；可选 `fin_payment_reconciliation.daily_settlement_batch_id` 条件更新加固。 |
| **延伸** | 日结交账锁、C 类报表 snapshot → **SYS-IMP-010 GAP-BAT-004/005** |
| **状态** | 文档化；实现若已满足 BR-CDS-008 可标 **已关闭** |

---

### GAP-FIN-010 护士写路径重复 Charge（交叉项）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **场景** | ① 住院医嘱执行双 planTime → 双 `ORDER_EXECUTE` Charge（**GAP-EXE-002**）；② 护士划价重复提交 → 双 `nurse-pricing` 行；③ 皮试 **BR-IP-STK-006** 重复划价（`EX_IP_STK_204` 部分场景）。 |
| **修改方案** | 执行侧见 **SYS-IMP-001 GAP-EXE-002**；划价侧：写批次幂等键 / 自然键唯一（同患者+同产品+同 occurrence 等按 BR 定义）。 |

---

### GAP-FIN-011 反结 / 门诊退费并发

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（文档已知） |
| **关联** | **BR-IP-RVS-002**（同一 payment 已成功反结后再提交拒绝）；门诊退费 `enterRefundProcessingWindow` |
| **修改方案** | 与 GAP-FIN-005 统一用 Payment 条件状态更新 + `@Version`。 |

---

### GAP-FIN-012 转医保/转自费切账户

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **关联 IF** | IF-OPC-04/05 |
| **现象** | 并发「转医保」与「正式结算」：ChargeItem `accountId` 与结算账户不一致风险。 |
| **修改方案** | 切账户时 `UPDATE adm_charge_item SET account_id=? WHERE id=? AND status_enum='PLANNED'`；结算前再校验 account 与 draft 一致。 |
| **状态** | 待评估（视现场是否启用医保切换） |

---

## 4. 边界问题（非并发，但影响金额正确性）

| 编号 | 主题 | 说明 | 建议 |
| --- | --- | --- | --- |
| BND-FIN-001 | 门诊预结算作废草稿 | `cancelStaleOutpatientPaymentDrafts` 在每次预结算前执行；若用户持有旧 draftId 正式结算会失败 | 前端始终用最新 draftId；失败提示刷新 |
| BND-FIN-002 | 住院展示余额公式 | 待结算合计 + 账户余额 − 已结算；与 **BR-IP-NUR-006** 一致；执行新增 Charge 后余额展示滞后直到刷新 | 写成功后推送刷新事件（可选） |
| BND-FIN-003 | 半退/反结保留链 | **BR-REF-020** / **BR-CHG-015** / **BR-IP-RVS-011** 排除已替代 SETTLED 行；读侧/Gate 须 **递归**追溯 `refund_id` 链，与写侧一致 | 回归半退/多轮反结后再收银、取消执行 |
| BND-FIN-004 | 零元出院全结 | 空 chargeItemIds 分支；须 PENDING 且无待结算行 | 集成测试覆盖 |
| BND-FIN-005 | 医保预结算刷新 | `refreshPaymentDraftPreSettleAmounts` 仅 DRAFT；正式结算以 displayAmount 为准 | 医保失败时禁止带旧 draft 结算 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-FIN-001 + 003 | Charge/Payment 条件更新 + 门诊源态校验 | 3～4 |
| 2 | GAP-FIN-002 + 008 | 账户行锁/乐观锁 | 2～3 |
| 3 | GAP-FIN-004 | 财务实体 @Version | 2 |
| 4 | GAP-FIN-005 + 011 | 退款/反结 Payment 条件迁移 | 1～2 |
| 5 | GAP-FIN-010 | 联动 SYS-IMP-001 GAP-EXE-002 | 见 SYS-IMP-001 |
| 6 | GAP-FIN-006、007、012 | 按上线优先级 | 2～4 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅依赖 `findPaySuccessOrRefundingOverlappingChargeItems` 读 Gate | 无法防并发双结 |
| 仅前端禁用结算按钮 | 挡不住双开与接口重放 |
| 在 QueryService 写 ChargeItem 状态 | 违反分层；应在 DomainService + Repository 条件更新 |
| 去掉 `enterRefundProcessingWindow` | 会引入重复退费窗口 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 同一批 chargeItemIds 双线程正式结算 | 1 SUCCESS，1 失败 |
| T2 | 同一 paymentDraftId 双 confirm | 1 SUCCESS，1 草稿态非法 |
| T3 | 并发两笔预交金 +1000 | balance +2000 |
| T4 | 预交金收与结算扣款并发 | balance 数学正确 |
| T5 | 双窗口退同一 SUCCESS 支付 | 1 进入 REFUND_PROCESSING，1 拒绝 |
| T6 | 双窗口同日同科室挂号 | 1 成功，1 REG-BIZ-409 |
| T7 | 执行双 planTime（联动 SYS-IMP-001） | 1 条 ORDER_EXECUTE Charge |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：收费结算与账户并发/边界审查 |
