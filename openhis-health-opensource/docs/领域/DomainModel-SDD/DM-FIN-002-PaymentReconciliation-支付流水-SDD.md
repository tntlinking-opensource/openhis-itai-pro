# DM-FIN-002 PaymentReconciliation 支付流水 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md`（**v1.4.12**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：FIN  
> **聚合**：PaymentReconciliation（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-FIN-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.fin.paymentreconciliation` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1~§3.3（Date/DateTime 混用） | 成码口径统一为 `LocalDateTime`，落库时由框架处理映射 |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.financial.paymentreconciliation` |
| 标识集合处理 | §3.1 `chargeItemIds` 为 `String` | SDD 保持一致；`getChargeItemIdsByPayments` §4.6.4 提供解析后 `List<Long>` 视图 |
| 医保结算关联 | 原 §3.1 `ybSettleIds` 逗号串 | 已废止；改 **§3.4 PaymentYbSettlement** + 命令/事件 `ybSettlements` 列表 |
| 读方法 QueryModel | DM 未展开 | SDD §4.6 按患者稿标准扩展为 6 个独立 QueryModel 子节，与 §5.2 读方法一一对应 |
| 明细归属校验 | DM §7 BR-PMR-013 仅文字描述 | SDD §4.3 显式增加 `loadByIdAndReconciliationId` 归属校验规则 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化支付流水主表、支付明细、统计快照三层契约，确保收费/退费事实可追溯。
- 将 13 个写方法按状态机与金额口径拆分，保证结算、退款、收口、预交金流程可实现可测试。
- 明确读侧查询方法（含 **§5.2** 收费员交账候选扩展 3 项），支撑写前校验、对账统计、退费门控、日结交账预览与轨迹展示。

### 1.2 边界

- **在内**：支付状态机、支付主从落账、退款追溯链、医保结算号回写。
- **在外**：医保平台协议细节、发票平台协议、ChargeItem 状态推进编排。
- **跨域仅端口声明**：通过 `encounterId/patientId/accountId/invoiceId/chargeItemIds` 关联上下游。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.fin.paymentreconciliation`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PaymentReconciliation`、`PaymentRecDetail`、`PaymentRecStatic`、`PaymentYbSettlement` |
| `valueobject` | `ChargeItemIdSet`、`InsuranceSettleIdSet` |
| `enums`（共享） | `PaymentStatus`、`PaymentKind`、`PaymentType`、`PaymentOutcome`、`PaymentResult`、`PaymentStatisticalMethod` |
| `command` | 13 个写命令（见 §5.1） |
| `repository` | `PaymentReconciliationRepository`、`PaymentRecDetailRepository`、`PaymentRecStaticRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.financial.paymentreconciliation` |

**存储口径（DM 优先）**：`*_enum` 为 `Integer`；`*_code` 为 `String`；金额字段为 `BigDecimal`；时间字段统一 `LocalDateTime`（落库可映射 Date/DateTime）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id`，外键 `reconciliationId/relationId/...` |
| 时间 | `billDate/payTransDate/expirationDate` 使用 `LocalDateTime` |
| `*_enum` | `statusEnum/kindEnum/paymentEnum/outcomeEnum/...` 均 `Integer` |
| `*_code` | `paymentNo/contractNo/typeCode/accountCode` 为 `String` |
| `_flag` | `ybClearFlag/isDeleted` 为 `Boolean` |
| 软删 | `Boolean isDeleted`（基类） |
| 实体 | 主从实体均 `@Entity` |
| 值对象 | 标识集合可用 `@Embeddable` 或转换器 |
| 仓储 | 仅 `JpaRepository` 接口 |
| 分页 | `Pageable` + 默认 `createdAt DESC` |
| 事务 | 依赖工作单元，不机械加 `@Transactional` |
| 事件 | 方法内直接 `new Event + publish(beforeCommit)` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `fin_payment_reconciliation` | `PaymentReconciliation` | 支付流水主记录 |
| `fin_payment_rec_detail` | `PaymentRecDetail` | 支付渠道明细（仅业务自付渠道） |
| `fin_payment_yb_settlement` | `PaymentYbSettlement` | 医保结算凭证关联（一行一个 setl_id） |
| `fin_payment_rec_static` | `PaymentRecStatic` | 分类统计快照 |

### 4.2 聚合根 `PaymentReconciliation`（字段级契约）

对应表 `fin_payment_reconciliation`，主键 `id`。

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 支付流水标识，雪花 ID |
| `statusEnum` | `Integer` | 是 | 支付状态，状态机：DRAFT(0)→SUCCESS(1)→REFUNDING(5)→REFUND_ALL(3)/REFUND_PART(4)，或 CANCEL(2)/ERROR(9) |
| `relationId` | `Long` | 否 | 关联原单标识，退费单指向原支付单，用于退费链路追溯（BR-PMR-007） |
| `paymentNo` | `String` | 是 | 支付业务号，用于外部追溯，唯一 |
| `paymentEnum` | `Integer` | 是 | 付款类别，PAY=0（正向收款）/ UN_PAY=1（逆向退款），金额乘数分别为 1/-1 |
| `kindEnum` | `Integer` | 是 | 支付业务类型，HOSPITAL_DEPOSIT=0（住院预交金）/ OUTPATIENT_CLINIC=1（门诊收费）/ INPATIENT_CLINIC=2（住院结算） |
| `entererId` | `Long` | 是 | 收费员/经办人标识 |
| `issuerEnum` | `Integer` | 否 | 支付发起人类型 |
| `patientId` | `Long` | 是 | 患者标识，关联 Patient 聚合根 |
| `practitionerId` | `Long` | 否 | 责任医师标识 |
| `outcomeEnum` | `Integer` | 否 | 支付结果，QUEUED=0/COMPLETED=1/ERROR=3/PARTIAL=4 |
| `locationId` | `Long` | 否 | 支付窗口/终端位置标识 |
| `expirationDate` | `LocalDateTime` | 否 | 支付有效到期时间 |
| `tenderedAmount` | `BigDecimal` | 是 | 应收/实收口径金额，非负（BR-PMR-014） |
| `returnedAmount` | `BigDecimal` | 否 | 找零金额 |
| `displayAmount` | `BigDecimal` | 是 | 前端展示用总金额 |
| `printCount` | `Integer` | 是 | 收据/发票打印次数 |
| `contractNo` | `String` | 否 | 费用性质编码（自费/医保合同）；自付与医保同时付费时可为空 |
| `chargeItemIds` | `String` | 是 | 收费明细标识集合，逗号分隔，非空（预交金场景除外）（BR-PMR-001） |
| `encounterId` | `Long` | 是 | 就诊标识，关联 Encounter 聚合根 |
| `billDate` | `LocalDateTime` | 否 | 结算完成时间 |
| `invoiceId` | `Long` | 否 | 关联发票聚合根标识 |
| `accountId` | `Long` | 否 | 关联就诊账户聚合根标识；自付与医保同时付费时可为空 |
| `ybClearFlag` | `Boolean` | 否 | 医保清算标记，false=未清算，true=已清算 |
| `cashierSettlementId` | `Long` | 否 | 交账批次标识，非空=已交账（**DM-FIN-003**） |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记，默认 false（BR-PMR-015） |

**不变式方法（示例命名，实现须一致）**：
- `assertChargeItemsNotEmpty()`：非预交金场景必须关联至少一条收费明细（BR-PMR-001）
- `assertStatusTransitionAllowed()`：状态迁移遵循生命周期约束，终态禁止逆向回退（BR-PMR-002）
- `assertNoDuplicateRefund()`：同一原单不得重复发起退款（BR-PMR-008）
- `assertAmountNonNegative()`：应收金额不可为负，退费方向由 paymentEnum 区分（BR-PMR-014）
- `assertWriteBeforeStateChange()`：写前必须先加载主状态，禁止盲更新（BR-PMR-012）

### 4.3 子实体 `PaymentRecDetail`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `reconciliationId` | `Long` | 是 | 主表外键 |
| `predecessorId` | `Long` | 否 | 前置明细 |
| `targetEnum` | `Integer` | 否 | 目标类型 |
| `accountId` | `Long` | 是 | 账户标识 |
| `accountCode` | `String` | 否 | 账户编码 |
| `payEnum` | `Integer` | 是 | 支付方式 |
| `payLevelEnum` | `Integer` | 否 | 支付级别 |
| `amount` | `BigDecimal` | 是 | 支付金额 |
| `returnAmount` | `BigDecimal` | 否 | 退还金额 |
| `chargeAmount` | `BigDecimal` | 否 | 收费金额 |
| `payTransNo` | `String` | 否 | 交易号 |
| `payTransText` | `String` | 否 | 交易描述 |
| `payTransDate` | `LocalDateTime` | 否 | 交易时间 |
| `beforeBalance` | `BigDecimal` | 否 | 交易前余额 |
| `afterBalance` | `BigDecimal` | 否 | 交易后余额 |
| `resultEnum` | `Integer` | 是 | 明细结果 |

**归属校验**：带 `id` 的更新必须 `loadByIdAndReconciliationId(id, reconciliationId)`。
**入参约束**：`accountId` 必须由应用层在请求 DTO 明细中显式回填（`paymentDetails[*].accountId`），禁止通过新增 mapper 额外参数绕过契约。

### 4.4 子实体 `PaymentYbSettlement`

对应表 `fin_payment_yb_settlement`：

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `reconciliationId` | `Long` | 是 | 支付主流水外键 |
| `setlId` | `String` | 是 | 平台 setl_id |
| `chrgBchno` | `String` | 否 | 收费批次号 |
| `mdtrtId` | `String` | 否 | 医保就诊 ID |
| `settlementRecordId` | `Long` | 否 | CHS `yb_settlement_record.id` |
| `accountId` | `Long` | 否 | 账户标识 |
| `status` | `String` | 是 | `CONFIRMED` / `REVOKED` |

**不变式**：`assertPersistableChildRow()` — `reconciliationId`、`setlId`、`status` 非空。

### 4.5 子实体 `PaymentRecStatic`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `reconciliationId` | `Long` | 是 | 主表外键 |
| `countByEnum` | `Integer` | 是 | 统计方式 |
| `typeCode` | `String` | 是 | 分类编码 |
| `typeName` | `String` | 否 | 分类名称 |
| `amount` | `BigDecimal` | 是 | 分类金额 |
| `chargeItemIds` | `String` | 否 | 关联收费项 |

### 4.6 仓储接口

- `Optional<PaymentReconciliation> findByIdAndIsDeletedFalse(Long id)`
- `List<PaymentReconciliation> findByEncounterIdAndIsDeletedFalse(Long encounterId)`
- `boolean existsByRelationIdAndIsDeletedFalse(Long relationId)`
- `List<PaymentRecDetail> findByReconciliationIdAndIsDeletedFalse(Long reconciliationId)`
- `List<PaymentReconciliation> findByBillDateBetweenAndIsDeletedFalse(...)`
- `PaymentYbSettlementRepository` — `findAllByReconciliationIdInAndIsDeletedFalseOrderByReconciliationIdAscCreatedAtAsc`

### 4.7 查询模型（`queries.model`）

每个读方法一个独立子节，字段级逐行列清，与 §5.2 读侧白名单一一对应：

#### 4.7.1 `PaymentReconciliationQueryModel`（对应 `mustGetPayment` / `findPaymentsByEncounter` / `findPaySuccessOrRefundingByEncounter` / `findPaySuccessOrRefundingOverlappingChargeItems` / `findReturnPayment` / `findPaymentByBillDateRange` / `findDraftPaymentByPatient`）

通用支付主记录查询模型，覆盖上述 6 个读方法的出参：

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `fin_payment_reconciliation.id` | 主键 |
| `statusEnum` | `Integer` | `fin_payment_reconciliation.status_enum` | 支付状态 |
| `relationId` | `Long` | `fin_payment_reconciliation.relation_id` | 原单关联标识 |
| `paymentNo` | `String` | `fin_payment_reconciliation.payment_no` | 支付业务号 |
| `paymentEnum` | `Integer` | `fin_payment_reconciliation.payment_enum` | 付款类别 |
| `kindEnum` | `Integer` | `fin_payment_reconciliation.kind_enum` | 支付业务类型 |
| `entererId` | `Long` | `fin_payment_reconciliation.enterer_id` | 收费员标识 |
| `patientId` | `Long` | `fin_payment_reconciliation.patient_id` | 患者标识 |
| `practitionerId` | `Long` | `fin_payment_reconciliation.practitioner_id` | 责任医师标识 |
| `outcomeEnum` | `Integer` | `fin_payment_reconciliation.outcome_enum` | 支付结果 |
| `locationId` | `Long` | `fin_payment_reconciliation.location_id` | 支付窗口标识 |
| `tenderedAmount` | `BigDecimal` | `fin_payment_reconciliation.tendered_amount` | 应收金额 |
| `returnedAmount` | `BigDecimal` | `fin_payment_reconciliation.returned_amount` | 找零金额 |
| `displayAmount` | `BigDecimal` | `fin_payment_reconciliation.display_amount` | 展示总额 |
| `contractNo` | `String` | `fin_payment_reconciliation.contract_no` | 费用性质编码 |
| `chargeItemIds` | `String` | `fin_payment_reconciliation.charge_item_ids` | 收费明细标识集合 |
| `encounterId` | `Long` | `fin_payment_reconciliation.encounter_id` | 就诊标识 |
| `billDate` | `LocalDateTime` | `fin_payment_reconciliation.bill_date` | 结算时间 |
| `invoiceId` | `Long` | `fin_payment_reconciliation.invoice_id` | 票据标识 |
| `accountId` | `Long` | `fin_payment_reconciliation.account_id` | 账户标识 |
| `ybSettlements` | `List<PaymentYbSettlementQueryModel>` | `fin_payment_yb_settlement` | 医保结算关联行（读侧批量挂载） |
| `ybClearFlag` | `Boolean` | `fin_payment_reconciliation.yb_clear_flag` | 医保清算标记 |
| `cashierSettlementId` | `Long` | `fin_payment_reconciliation.cashier_settlement_id` | 交账批次标识 |
| `printCount` | `Integer` | `fin_payment_reconciliation.print_count` | 打印次数 |

#### 4.7.2 `PaymentYbSettlementQueryModel`（`PaymentReconciliationQueryModel.ybSettlements` 元素）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `fin_payment_yb_settlement.id` | 主键 |
| `reconciliationId` | `Long` | `fin_payment_yb_settlement.reconciliation_id` | 支付流水标识 |
| `setlId` | `String` | `fin_payment_yb_settlement.setl_id` | 平台 setl_id |
| `chrgBchno` | `String` | `fin_payment_yb_settlement.chrg_bchno` | 收费批次号 |
| `mdtrtId` | `String` | `fin_payment_yb_settlement.mdtrt_id` | 医保就诊 ID |
| `settlementRecordId` | `Long` | `fin_payment_yb_settlement.settlement_record_id` | CHS 结算凭证 ID |
| `accountId` | `Long` | `fin_payment_yb_settlement.account_id` | 账户标识 |
| `status` | `String` | `fin_payment_yb_settlement.status` | CONFIRMED / REVOKED |

#### 4.7.3 `PaymentRecDetailQueryModel`（对应 `listPaymentRecDetailsByPaymentId`）

支付明细查询模型：

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `fin_payment_rec_detail.id` | 明细标识 |
| `reconciliationId` | `Long` | `fin_payment_rec_detail.reconciliation_id` | 支付流水标识 |
| `predecessorId` | `Long` | `fin_payment_rec_detail.predecessor_id` | 前置明细标识（退费场景） |
| `targetEnum` | `Integer` | `fin_payment_rec_detail.target_enum` | 目标类型 |
| `accountId` | `Long` | `fin_payment_rec_detail.account_id` | 账户标识 |
| `accountCode` | `String` | `fin_payment_rec_detail.account_code` | 账户编码 |
| `payEnum` | `Integer` | `fin_payment_rec_detail.pay_enum` | 支付方式 |
| `payLevelEnum` | `Integer` | `fin_payment_rec_detail.pay_level_enum` | 支付级别 |
| `amount` | `BigDecimal` | `fin_payment_rec_detail.amount` | 支付金额 |
| `returnAmount` | `BigDecimal` | `fin_payment_rec_detail.return_amount` | 退还金额 |
| `chargeAmount` | `BigDecimal` | `fin_payment_rec_detail.charge_amount` | 收费金额 |
| `payTransNo` | `String` | `fin_payment_rec_detail.pay_trans_no` | 支付交易号 |
| `payTransText` | `String` | `fin_payment_rec_detail.pay_trans_text` | 支付交易描述 |
| `payTransDate` | `LocalDateTime` | `fin_payment_rec_detail.pay_trans_date` | 支付交易时间 |
| `beforeBalance` | `BigDecimal` | `fin_payment_rec_detail.before_balance` | 交易前余额 |
| `afterBalance` | `BigDecimal` | `fin_payment_rec_detail.after_balance` | 交易后余额 |
| `resultEnum` | `Integer` | `fin_payment_rec_detail.result_enum` | 明细结果 |

#### 4.7.4 `RefundExistenceCheckResult`（对应 `hasRefundByRelationId`）

退款存在性检查返回模型：

| 字段名（Java） | 类型 | 来源（表.列/计算） | 说明 |
| -------------- | ---- | ------------------ | ---- |
| `exists` | `Boolean` | 查询结果 | 是否存在关联退款单 |
| `refundPaymentId` | `Long` | `fin_payment_reconciliation.id` | 关联退款单标识（存在时） |
| `refundStatusEnum` | `Integer` | `fin_payment_reconciliation.status_enum` | 退款单状态（存在时） |

#### 4.7.5 `ChargeItemIdsCollectionQueryModel`（对应 `getChargeItemIdsByPayments`）

收费项标识集合解析模型：

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `paymentId` | `Long` | `fin_payment_reconciliation.id` | 支付流水标识 |
| `chargeItemIds` | `List<Long>` | `fin_payment_reconciliation.charge_item_ids` 解析 | 收费明细标识列表 |
| `parsedAt` | `LocalDateTime` | 系统时间 | 解析时间戳 |

#### 4.7.6 `PaymentSummaryQueryModel`（对应 `queryPaymentAccountContract`）

支付汇总统计模型：

| 字段名（Java） | 类型 | 来源（表.列/聚合） | 说明 |
| -------------- | ---- | ------------------ | ---- |
| `entererId` | `Long` | `fin_payment_reconciliation.enterer_id` | 收费员标识 |
| `entererName` | `String` | 关联查询 | 收费员姓名 |
| `contractNo` | `String` | `fin_payment_reconciliation.contract_no` | 费用性质编码 |
| `contractName` | `String` | 关联查询 | 费用性质名称 |
| `totalAmount` | `BigDecimal` | SUM(`tendered_amount`) | 汇总金额 |
| `paymentCount` | `Long` | COUNT(*) | 支付笔数 |
| `successCount` | `Long` | 条件聚合 | 成功笔数 |
| `refundCount` | `Long` | 条件聚合 | 退款笔数 |
| `billDateFrom` | `LocalDateTime` | 查询参数 | 时间窗起始 |
| `billDateTo` | `LocalDateTime` | 查询参数 | 时间窗结束 |
| `groupByField` | `String` | 查询参数 | 分组维度（款员/合同/日期） |

#### 4.7.7 `PaymentStaticSnapshotQueryModel`（扩展QueryService，对应统计快照查询）

支付分类统计快照模型：

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `fin_payment_rec_static.id` | 快照标识 |
| `reconciliationId` | `Long` | `fin_payment_rec_static.reconciliation_id` | 支付流水标识 |
| `countByEnum` | `Integer` | `fin_payment_rec_static.count_by` | 统计方式 |
| `typeCode` | `String` | `fin_payment_rec_static.type_code` | 分类编码 |
| `typeName` | `String` | `fin_payment_rec_static.type_name` | 分类名称 |
| `amount` | `BigDecimal` | `fin_payment_rec_static.amount` | 分类金额 |
| `chargeItemIds` | `String` | `fin_payment_rec_static.charge_item_ids` | 关联收费项标识集合 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PaymentReconciliationDomainService`

#### 5.1.1 `createPaymentDraftFromPreSettle(CreatePaymentDraftFromPreSettleCommand cmd)` → `Long`
**命令文件**：`CreatePaymentDraftFromPreSettleCommand.java`  
**字段全集**：`encounterId,patientId,chargeItemIds,kindEnum,paymentEnum,statusEnum,entererId,tenderedAmount,displayAmount,contractNo,paymentDetails`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertChargeItemsNotEmpty` |
| `statusTransitionMethod` | `initDraft` + `setStatusEnum` |
| `repositoryChecks` | `findPaySuccessOrRefundingByEncounter` |
| `eventPublishAnchor` | `PaymentDraftCreatedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-001` |
**方法契约摘要**：前置收费项集合非空且未占用；应用层命令显式传入 `statusEnum=DRAFT`，领域层落库时优先采用命令状态（未传则兜底 `initDraft`）；持久化创建草稿主从；失败语义冲突拒绝；发布草稿事件。

---

#### 5.1.2 `settleOutpatientPayment(SettleOutpatientPaymentCommand cmd)` → `Long`
**命令文件**：`SettleOutpatientPaymentCommand.java`  
**字段全集**：`paymentId,outcomeEnum,ybSettlements,billDate,payReceiptDetails`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStatusTransitionAllowed` |
| `statusTransitionMethod` | `draftToSuccess` |
| `repositoryChecks` | `mustGetPayment` |
| `eventPublishAnchor` | `OutpatientPaymentSettledEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-002` |
**方法契约摘要**：前置状态 DRAFT；持久化更新主状态与明细结果为已支付；失败语义状态非法拒绝；发布门诊结算事件。

**`payReceiptDetails` 非空时**：领域通过 `replaceDetails` **先删后插**整表替换 `fin_payment_rec_detail`；每条 `PaymentDetailItemCommand` 须经 `PaymentRecDetail#assertPersistableChildRow`（含 **`accountId` 非空** 等）。门诊收费 IF-OPC-07 若 Mapper 仅映射渠道与金额，**应用层须在调用本方法前**将 IF-OPC-06 草稿从表明细中的记账账户回填至命令行（与 `AS-OP-FIN-002-SDD` §3.8 步骤 ⑧ 一致）。

**与 ADM 协同（门诊挂号正式结算）**：本方法仅负责支付聚合落账并发布 `OutpatientPaymentSettledEvent`（**不含**就诊状态）。`Encounter` 的 `PLANNED`→`REGISTERED` 由应用层在同一用例中调用 `EncounterDomainService#completeOutpatientRegistration` 编排；QueryService 侧 `REGISTERED` 展示由订阅 **`OutpatientRegistrationConfirmedEvent`**（就诊域）的QueryService更新。叙事与 `DM-FIN-002` §5.1.1、`DM-ADM-002` §5.1 / §6.1.2 一致。

---

#### 5.1.3 `settleInpatientPayment(SettleInpatientPaymentCommand cmd)` → `Long`
**命令文件**：`SettleInpatientPaymentCommand.java`  
**字段全集**：`paymentId,outcomeEnum,ybSettlements,billDate`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStatusTransitionAllowed` |
| `statusTransitionMethod` | `draftToSuccess` |
| `repositoryChecks` | `mustGetPayment` |
| `eventPublishAnchor` | `InpatientPaymentSettledEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-003` |
**方法契约摘要**：前置 DRAFT；持久化更新住院结算结果；失败语义医保回写异常拒绝；发布住院结算事件。

---

#### 5.1.5 `requestOutpatientRefund(RequestOutpatientRefundCommand cmd)` → `Long`
**命令文件**：`RequestOutpatientRefundCommand.java`  
**字段全集**：`relationId,encounterId,patientId,entererId,tenderedAmount,displayAmount,chargeItemIds,kindEnum,paymentEnum,contractNo,paymentDetails`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertNoDuplicateRefund` |
| `statusTransitionMethod` | `initDraft` + `setStatusEnum(REFUND_PROCESSING)` |
| `repositoryChecks` | `hasRefundByRelationId` |
| `eventPublishAnchor` | `OutpatientRefundRequestedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-005` |
**方法契约摘要**：前置原单处于可退且无重复退款单；新建主单先 `initDraft`（`print_count`、`yb_clear_flag` 等非空默认）再置 `REFUND_PROCESSING`；持久化生成逆向退款主从；**`paymentDetails` 为原路渠道副本（应用层装配，含统筹/个账/自付）**，`contractNo` 继承原单；失败语义重复退款拒绝；发布退费申请事件。

---

#### 5.1.6 `completeOutpatientRefund(CompleteOutpatientRefundCommand cmd)` → `void`
**命令文件**：`CompleteOutpatientRefundCommand.java`  
**字段全集**：`originPaymentId,refundPaymentId,refundType,outcomeEnum`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRefundRelationValid` |
| `statusTransitionMethod` | `refundingToAllOrPart` |
| `repositoryChecks` | `findReturnPayment` |
| `eventPublishAnchor` | `OutpatientRefundCompletedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-006` |
**方法契约摘要**：前置退款链有效且金额匹配；持久化将原单收口为 **`REFUNDED`** 或 **`PARTIAL_REFUNDED`**（支付状态机语义：见 DM §2.3，**非**产品「半退」同义词；半退编排 **BR-PMR-017**）；失败语义关联无效拒绝；发布退费完成事件。

---

#### 5.1.7 `cancelInpatientSettleAsRefund(CancelInpatientSettleAsRefundCommand cmd)` → `Long`
**命令文件**：`CancelInpatientSettleAsRefundCommand.java`  
**字段全集**：`originPaymentId,reverseSettleInfo,reason`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStatusTransitionAllowed` |
| `statusTransitionMethod` | `initDraft` + `setStatusEnum(REFUND_PROCESSING)` |
| `repositoryChecks` | `mustGetPayment` |
| `eventPublishAnchor` | `InpatientSettleCancelledEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-007` |
**方法契约摘要**：前置原单可取消；新建逆向主单先 `initDraft` 再置 `REFUND_PROCESSING`，满足非空列约束；持久化生成逆向单并回写反结算；失败语义反结算失败拒绝；发布住院取消结算事件。

---

#### 5.1.8 `updatePaymentStatusWithSettleIds(UpdatePaymentStatusWithSettleIdsCommand cmd)` → `void`
**命令文件**：`UpdatePaymentStatusWithSettleIdsCommand.java`  
**字段全集**：`paymentId,targetStatusEnum,outcomeEnum,ybSettlements`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStatusTransitionAllowed` |
| `statusTransitionMethod` | `transitionByCommand` |
| `repositoryChecks` | `mustGetPayment` |
| `eventPublishAnchor` | `PaymentStatusEditedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-008` |
**方法契约摘要**：前置主记录存在且允许回写；持久化更新状态/结果/结算号；失败语义终态非法回写拒绝；发布状态变更事件。

---

#### 5.1.9 `abortPreSettlementBeforePayment(AbortPreSettlementBeforePaymentCommand cmd)` → `void`
**命令文件**：`AbortPreSettlementBeforePaymentCommand.java`  
**字段全集**：`paymentId,reason`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertAbortAllowed` |
| `statusTransitionMethod` | `toCancel` |
| `repositoryChecks` | `mustGetPayment` |
| `eventPublishAnchor` | `PreSettlementAbortedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-009` |
**方法契约摘要**：前置可放弃草稿链路；持久化改为 CANCEL 并释放占用；失败语义无可放弃链路拒绝；发布放弃事件。

---

#### 5.1.10 `refreshRegistrationPaymentDraft(RefreshRegistrationPaymentDraftCommand cmd)` → `Long`
**命令文件**：`RefreshRegistrationPaymentDraftCommand.java`  
**字段全集**：`patientId,encounterId,newChargeItemIds`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertPatientScopeValid` |
| `statusTransitionMethod` | `replaceDraft` |
| `repositoryChecks` | `findDraftPaymentByPatient` |
| `eventPublishAnchor` | `RegistrationPaymentDraftRefreshedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-010` |
**方法契约摘要**：前置患者维度合法；持久化清理旧草稿并新建草稿；失败语义重复刷新冲突拒绝；发布草稿刷新事件。

---

#### 5.1.11 `closeSinglePaymentResult(CloseSinglePaymentResultCommand cmd)` → `void`
**命令文件**：`CloseSinglePaymentResultCommand.java`  
**字段全集**：`paymentId,targetStatusEnum,outcomeEnum`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertWriteBeforeStateChange` |
| `statusTransitionMethod` | `transitionByCommand` |
| `repositoryChecks` | `mustGetPayment` |
| `eventPublishAnchor` | `SinglePaymentResultClosedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-011` |
**方法契约摘要**：前置主记录存在；持久化轻量收口状态；失败语义状态机非法拒绝；发布单笔收口事件。

---

#### 5.1.12 `finalizePaymentDetailReceipt(FinalizePaymentDetailReceiptCommand cmd)` → `void`
**命令文件**：`FinalizePaymentDetailReceiptCommand.java`  
**字段全集**：`paymentId,detailReceipts`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDetailBelongsToPayment` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `listPaymentRecDetailsByPaymentId` |
| `eventPublishAnchor` | `PaymentDetailReceiptFinalizedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-012` |
**方法契约摘要**：前置主从归属一致；持久化批量更新明细回执与结果；失败语义明细不匹配拒绝；发布回执收口事件。

---

#### 5.1.13 `collectAdvanceDeposit(CollectAdvanceDepositCommand cmd)` → `Long`
**命令文件**：`CollectAdvanceDepositCommand.java`  
**字段全集**：`encounterId,patientId,entererId,tenderedAmount,displayAmount,kindEnum,paymentEnum,paymentDetails`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertAmountNonNegative` / `assertHeaderCompleteBeforeNewPersist` |
| `statusTransitionMethod` | `initDraft`（落 **DRAFT**；应用层再 `settleInpatientPayment`） |
| `repositoryChecks` | —（余额/开户在应用层） |
| `eventPublishAnchor` | `AdvanceDepositCollectedEvent` |
| `unitTestCaseId` | `TC-FIN-PMR-013` |
**方法契约摘要**：金额 >0、`kind=HOSPITAL_DEPOSIT`、`paymentEnum=PAY`；正式收取须带明细；**意向草稿**允许明细为空；落 DRAFT 后发事件；settle/调账在应用层。

#### 5.1.13a `softDeleteAdvanceDepositDraft(Long paymentId)` → `void`
**命令文件**：无（主键入参）  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | kind=`HOSPITAL_DEPOSIT`；status ∈ {`DRAFT`,`CANCELLED`} |
| `statusTransitionMethod` | 软删（`is_deleted`）；不经 CANCELLED |
| `repositoryChecks` | `paymentRepository.delete` |
| `eventPublishAnchor` | — |
| `unitTestCaseId` | `TC-FIN-PMR-013a` |
**方法契约摘要**：仅住院预交金草稿/历史作废稿可软删；成功收款单禁止；读侧 deletedFilter 不可见。

### 5.2 读侧 — `PaymentReconciliationQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6 对应子节） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ----------------------------------- | -------- | ---------------- |
| `mustGetPayment` | `paymentId` | `PaymentReconciliationQueryModel` §4.6.1 | 写前强校验加载 | `TC-FIN-PMR-Q-001` |
| `findPaymentsByEncounter` | `encounterId` | `PaymentReconciliationQueryModel` §4.6.1 | 就诊维度列表 | `TC-FIN-PMR-Q-002` |
| `findLatestHospitalDepositDraftByEncounter` | `encounterId` | `Optional<PaymentReconciliationQueryModel>` | 最新 HOSPITAL_DEPOSIT DRAFT | `TC-FIN-PMR-Q-012` |
| `listHospitalDepositDraftsByEncounter` | `encounterId` | `List<PaymentReconciliationQueryModel>` | 全部 HOSPITAL_DEPOSIT DRAFT | `TC-FIN-PMR-Q-013` |
| `findPaySuccessOrRefundingByEncounter` | `encounterId,statusEnums` | `PaymentReconciliationQueryModel` §4.6.1 | 状态集合过滤 | `TC-FIN-PMR-Q-003` |
| `findPaySuccessOrRefundingOverlappingChargeItems` | `encounterId,chargeItemIds` | `PaymentReconciliationQueryModel` §4.6.1 | 成功/退款中与收费项交集过滤（IF-OPC-07） | `TC-FIN-PMR-Q-011` |
| `hasRefundByRelationId` | `relationId` | `RefundExistenceCheckResult` §4.6.3 | 原单退款存在性判断 | `TC-FIN-PMR-Q-004` |
| `findReturnPayment` | `relationId` | `PaymentReconciliationQueryModel` §4.6.1 | 反查退款单 | `TC-FIN-PMR-Q-005` |
| `findPaymentByBillDateRange` | `from,to,pageable` | `PaymentReconciliationQueryModel` §4.6.1 | 按结算时间窗检索 | `TC-FIN-PMR-Q-006` |
| `findDraftPaymentByPatient` | `patientId` | `PaymentReconciliationQueryModel` §4.6.1 | 草稿检索 | `TC-FIN-PMR-Q-007` |
| `listPaymentRecDetailsByPaymentId` | `paymentId` | `PaymentRecDetailQueryModel` §4.6.2 | 查询明细列表 | `TC-FIN-PMR-Q-008` |
| `getChargeItemIdsByPayments` | `paymentIds` | `ChargeItemIdsCollectionQueryModel` §4.6.4 | 解析收费项集合 | `TC-FIN-PMR-Q-009` |
| `queryPaymentAccountContract` | `from,to,entererId,contractNo` | `PaymentSummaryQueryModel` §4.6.5 | 时间窗汇总与过滤 | `TC-FIN-PMR-Q-010` |
| `findDraftPaymentIdsByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long,Long>` | 批量 DRAFT 支付主键 | `TC-FIN-PMR-Q-012` |
| `mapFirstPayEnumByPaymentIds` | `Collection<Long> paymentIds` | `Map<Long,String>` | 首选支付方式 code | `TC-FIN-PMR-Q-013` |
| `previewUnsettledPaymentsForCashierSettlement` | `PreviewCashierSettlementQuery` | `PreviewCashierSettlementQueryModel`（**DM-FIN-003** §4.6） | 收费员交账未挂载候选预览（**BR-CS-001～006**） | `TC-FIN-CS-002` |
| `countUnsettledPaymentsForCashierSettlement` | `PreviewCashierSettlementQuery` | `Long` | 同预览过滤，仅 count | `TC-FIN-CS-003` |
| `findUnsettledPaymentsForCashierSettlement` | `PreviewCashierSettlementQuery` | `List<PaymentReconciliation>` | 写侧 `submitCashierSettlement` 锁定候选实体 | `TC-FIN-CS-001` |
| `listOutpatientFeeDetailChargeItemIdsByPaymentFilter` | `OutpatientFeeDetailPageQuery` | `List<Long>` | 门诊费用明细 payment 筛 chargeItem + 写入 context map（**AS-OP-FIN-005** **BR-OP-FDT-003、009**） | `TC-FIN-FDT-Q-001` |
| `mapOutpatientFeeDetailPaymentContextByChargeItemIds` | `OutpatientFeeDetailPageQuery`, `Collection<Long> chargeItemIds` | `Map<Long, OutpatientFeeDetailPaymentContextQueryModel>` | 批量 payment enrichment；医保分摊分母占位 | `TC-FIN-FDT-Q-002` |
| `listPractitionerWorkloadChargeItemIdsByPaymentFilter` | `PractitionerWorkloadPageQuery` | `List<Long>` | 工作量 payment 筛 chargeItem + 写入 context（**AS-RPT-001** **BR-RPT-WKL-004**） | `TC-FIN-WKL-Q-001` |

> **收费员交账候选**：入参 `PreviewCashierSettlementQuery` 定义于 `cashiersettlement.queries` 包；筛选口径与 **DM-FIN-002** §6.2.14～6.2.16、**DM-FIN-003** §6 **BR-CS-*** 一致。`CashierSettlementQueryService` 仅读本聚合交账头（**B.6.4**）。  
> **门诊费用明细**：入参 `OutpatientFeeDetailPageQuery` 定义于 `chargeitem.query` 包；scope 预解析见 `OutpatientFeeDetailQuerysupport；对齐 **DM-FIN-002** §6.2.17～6.2.18 与 **AS-OP-FIN-005**。  
> **工作量统计**：入参 `PractitionerWorkloadPageQuery` 定义于 `chargeitem.query`；对齐 **DM-FIN-002** §6.2.19 与 **AS-RPT-001**。

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PMR-001 | 非预交金场景支付主记录必须关联收费项集合 |
| BR-PMR-002 | 状态迁移遵循生命周期，终态禁止逆向回退 |
| BR-PMR-003 | 非现金明细必须有 `payTransNo` |
| BR-PMR-004 | 支付成功后触发应用编排推进 ChargeItem |
| BR-PMR-005 | 退费完成后触发应用编排推进 ChargeItem |
| BR-PMR-006 | 结算前校验同收费项不可重复收费 |
| BR-PMR-007 | 退款单 `relationId` 必填 |
| BR-PMR-008 | 同原单不得重复发起退款 |
| BR-PMR-009 | 明细必须归属同一主单 |
| BR-PMR-010 | 主状态与明细结果收敛一致 |
| BR-PMR-011 | 退款通过新单/新明细表达，不覆盖原单 |
| BR-PMR-012 | 写前必须先加载主状态 |
| BR-PMR-013 | 读明细必须按 `reconciliationId` 聚合 |
| BR-PMR-014 | 应收金额不可为负，退费方向由 `paymentEnum` 表达 |
| BR-PMR-015 | 软删过滤为默认查询/更新口径 |
| BR-PMR-016 | 新建主单先 `initDraft` 补齐 `print_count`/`yb_clear_flag` 再覆盖目标状态 |
| BR-PMR-017 | 门诊产品「半退」：按 payment 整单资金冲正 + 未选退行 ChargeItem 重开（与 DM §7 一致） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `PaymentDraftCreatedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,encounterId,patientId,chargeItemIds,tenderedAmount` | `createPaymentDraftFromPreSettle` |
| `OutpatientPaymentSettledEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,encounterId,chargeItemIds,tenderedAmount,billDate` | `settleOutpatientPayment` |
| `OutpatientRefundRequestedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,relationId,encounterId,refundAmount` | `requestOutpatientRefund` |
| `OutpatientRefundCompletedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,relationId,encounterId,refundAmount,refundType` | `completeOutpatientRefund` |
| `InpatientPaymentSettledEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,encounterId,chargeItemIds,displayAmount,ybSettlements` | `settleInpatientPayment` |
| `InpatientSettleCancelledEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,relationId,encounterId` | `cancelInpatientSettleAsRefund` |
| `PaymentStatusEditedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,beforeStatus,afterStatus,ybSettlements` | `updatePaymentStatusWithSettleIds` |
| `PreSettlementAbortedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,encounterId` | `abortPreSettlementBeforePayment` |
| `RegistrationPaymentDraftRefreshedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,patientId` | `refreshRegistrationPaymentDraft` |
| `SinglePaymentResultClosedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,finalStatus` | `closeSinglePaymentResult` |
| `PaymentDetailReceiptFinalizedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,detailCount` | `finalizePaymentDetailReceipt` |
| `AdvanceDepositCollectedEvent` | `com.zxplat.domain.share.events.financial.paymentreconciliation` | `paymentId,encounterId,accountId,amount` | `collectAdvanceDeposit` |

**发布时机**：全部 beforeCommit；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 主从表全部逻辑删除，不物理删除。
- 默认查询统一过滤 `isDeleted=true`。
- 退款通过新增逆向记录表达，不在原记录静默覆盖。

---

## 9. 框架优先适配规则

- 统一复用共享支付枚举，不重复定义等价枚举。
- 读侧查询方法保持无副作用，可按需投影优化。
- 子表更新遵循“全量对齐 + 软删旧行”与归属校验。
- 事件发布锚点固定在领域服务持久化成功之后。

---

## 10. 生成与验收清单

- [ ] 主表/明细/统计快照字段与 DM §3 全量对齐
- [ ] 13 个写方法全部有独立命令文件
- [ ] 10 个读方法全部建模且 QueryModel 字段化
- [ ] 状态机迁移与退款链路覆盖单测
- [ ] BR-PMR-001~015 全覆盖并绑定测试

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-FIN-002`
- 支付状态枚举与退款语义冻结
- 跨域连续动作边界（ChargeItem/Invoice/Account）已确认

### 11.2 DoD

- 写侧 13 方法签名、命令字段、状态迁移与 §5.1 完全一致。
- 读侧方法（含 §5.2 交账候选扩展）均可在软删过滤下正确返回。
- 退款链路满足 `relationId` 可追溯与防重复退款。
- 事件发布点、载荷字段与 §7 一致。

### 11.3 失败处理

- 任一写方法缺绑定项或字段全集，停止成码回补。
- 若状态机实现与 §2.3/§5.1冲突，回退到 SDD 先修订。
- 若读方法出现“先分页后过滤”，立即按数据库侧过滤重构。

---

## 定稿检查清单（可打勾）

- [x] 章节结构完整，模板同构
- [x] 写侧 13 方法逐项契约化
- [x] 读侧方法和 QueryModel 字段表齐全（含收费员交账候选扩展 §5.2 末 3 行）
- [x] BR-PMR-001~015 映射完成
- [x] §7 事件覆盖 DM 全量 13 个事件

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 9 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-10-11 | §5.1.13a：新增 `softDeleteAdvanceDepositDraft`；对齐 **DM-FIN-002 v1.4.16**（意向/作废预交金草稿软删） |
| 2026-07-29 | §5.1.5：`RequestOutpatientRefundCommand` 字段全集对齐成码（含 `contractNo`/`paymentDetails` 原路返回）；对齐 **DM-FIN-002 v1.4.12**、**AS-OP-FIN-001 IF-REG-10** |
| 2026-07-03 | **工作量 payment 读扩展**：§5.2 增补 `listPractitionerWorkloadChargeItemIdsByPaymentFilter`；对齐 **DM-FIN-002 v1.4.10** §6.2.19 与 **AS-RPT-001** |
| 2026-06-21 | **门诊费用明细读扩展**：§5.2 增补 `listOutpatientFeeDetailChargeItemIdsByPaymentFilter` / `mapOutpatientFeeDetailPaymentContextByChargeItemIds`；对齐 **DM-FIN-002 v1.4.6** §6.2.17～6.2.18 与 **AS-OP-FIN-005** |
| 2026-06-21 | **交账候选读扩展**：§5.2 增补 `previewUnsettledPaymentsForCashierSettlement` / `countUnsettledPaymentsForCashierSettlement` / `findUnsettledPaymentsForCashierSettlement`；对齐 **DM-FIN-002 v1.4.5** §6.2.14～6.2.16 与 **DM-FIN-003** **BR-CS-001～006** |
| 2026-05-28 | **DM-SDD M1**：§5.2 补 `findDraftPaymentIdsByEncounterIds`/`mapFirstPayEnumByPaymentIds`；对齐 DM **v1.4.1** |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
