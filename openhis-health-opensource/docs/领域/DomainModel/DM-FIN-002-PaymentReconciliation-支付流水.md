# DM-FIN-002 PaymentReconciliation 支付流水

> **限界上下文**：费用域 · 支付流水管理  
> **聚合类型**：实体聚合  
> **版本**：v1.4.14

---

## 1. 概述

### 1.1 业务定义

支付流水（PaymentReconciliation）是一次收费或退费业务中的**支付主记录**，承载支付状态、金额口径、结算单号与医保结算关联，是财务闭环中"钱"的流转事实。它回答的核心业务问题是："**患者实际付了多少钱、通过什么渠道付的、退了多少？**"

在费用域中，Invoice 是"应收"（法律凭证），PaymentReconciliation 是"实收"（支付事实）；两者配合形成收费闭环。PaymentReconciliation 同时作为 ChargeItem 收费状态推进的金融侧触发锚点，保证"账单结算/退费"与"支付流水"连续一致。

### 1.2 核心职责


| 职责     | 说明                                           |
| ------ | -------------------------------------------- |
| 支付事实记录 | 记录每笔收费/退费的实际支付行为，包括支付金额、找零、支付时间              |
| 多渠道分摊  | 管理支付流水子明细（PaymentRecDetail），支持多账户、多支付方式的统一落账 |
| 退费链路追溯 | 退款单通过 `relation_id` 追溯原支付。**产品「半退」**：同一笔支付（`charge_item_ids` 快照）下用户仅退其中部分收费项时，**资金侧对该 payment 整单冲退**；应用层对**未选退的明细**做**重开**（新建 `PLANNED` 行并链 `refund_id`，再接续预结算/结算），详见 **BR-PMR-017**、**DM-ADM-011** **BR-CHG-015**。**正常全退**：该支付快照内拟退集合 = 全部行，无重开保留项。一次退费操作可编排多个 `paymentId`，**全退/半退由后台依据所选行是否覆盖各支付快照全集判定**。支付终态 **`PARTIAL_REFUNDED`** 表示「原支付仍有可分次/可分笔冲退的余量语义」，**不等价**于产品口语「半退」，见 §2.3 表下说明 |
| 医保结算对接 | 承载医保结算号与反结算号，支撑医保日对账/月对账                     |
| 对账基础   | 按操作员/窗口/支付方式/时间维度汇总，支撑窗口日结与交班对账              |
| 统计快照   | 通过 PaymentRecStatic 记录付款按费用分类的统计快照，支撑报表与费用分析 |


### 1.3 适用范围


| 类型     | 说明                          |
| ------ | --------------------------- |
| 门诊收费   | 门诊预结算后完成支付（如普通门诊挂号收费、诊间收费）  |
| 住院结算   | 住院出院结算支付（出院结算、中途结算）         |
| 预交金收取  | 住院入院时收取预交金（住院预交金充值）         |
| 预交金退还  | 住院在院期间按账户余额退还预交金（不关联原收款单）   |
| 门诊退费   | 对已收费支付单发起退款（退药退费、退检退费）      |
| 住院取消结算 | 住院已结算支付的逆向处理（出院结算取消）        |
| 日结对账   | 按操作员/窗口汇总当日支付流水（收费员日结、窗口交班） |
| 医保对账   | 医保结算号核对与差异处理（医保日对账、月对账）     |


### 1.4 与 Invoice、ChargeItem、Account 的关系


| 概念               | 职责            | 与 PaymentReconciliation 的关系                           |
| ---------------- | ------------- | ----------------------------------------------------- |
| Invoice（发票）      | 法律凭证，汇总"应收"   | 支付完成后关联发票标识，Invoice 是"应收"，PaymentReconciliation 是"实收" |
| ChargeItem（收费明细） | 费用明细事实，承载单项费用 | 支付成功后推进 ChargeItem 为"已收费"，退费完成后推进为"已退费"               |
| Account（就诊账户）    | 就诊维度的财务载体     | 监听 AccountSettled 事件记录支付流水；支付明细按账户维度分摊                |


---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表（支付明细、统计快照、医保结算关联）。

- 聚合根主表：`fin_payment_reconciliation`，主键 `id`；一行表示一笔支付流水事实。
- 子实体见 **§3**：`PaymentRecDetail`（1..N）、`PaymentRecStatic`（0..N）、`PaymentYbSettlement`（0..N）。

**高频查询模式（建议索引口径）**：按 `encounter_id`、`patient_id`、`account_id`、`status_enum`、支付时间、操作员/窗口、原单追溯键；日结与交班按时间窗与操作员/窗口聚合。

### 2.2 聚合根标识


| 属性     | 类型     | 说明                |
| ------ | ------ | ----------------- |
| 支付流水标识 | `Long` | 全局唯一标识，雪花 ID，系统生成 |


### 2.3 状态机

```
                    ┌──────────────┐
                    │  DRAFT       │
                    │    草稿       │
                    └──┬───────┬───┘
            结算成功   │       │ 取消
                      v       v
           ┌─────────────┐  ┌──────────────┐
           │ SUCCESS      │  │ CANCELLED    │
           │  支付成功      │  │  支付取消    │
           └──────┬──────┘  └────────────┘
                  │ 发起退费
                  v
           ┌────────────────────┐<──再次退费──┐
           │ REFUND_PROCESSING  │             │
           │   退款中      │             │
           └──┬───────┬──┘             │
     全额退完 │       │ 部分退完         │
              v       v                │
      ┌────────────────┐ ┌──────────────────────┐   │
      │  REFUNDED      │ │ PARTIAL_REFUNDED     │───┘
      │  全部退款       │ │   部分退款            │
      │                │ └──────────────────────┘
      └───────────┘

      任意状态 → ERROR 错误
```

#### 状态定义


| code | info | 语义 |
| ---- | ---- | ---- |
| `DRAFT` | 草稿 | 预结算已生成支付草稿，待支付，允许结算、取消支付 |
| `SUCCESS` | 支付成功 | 支付结算完成，已形成支付成功事实，允许发起退费 |
| `CANCELLED` | 支付取消 | 支付已取消（放弃预结算等） |
| `REFUNDED` | 全部退款 | 全额退款完成 |
| `PARTIAL_REFUNDED` | 部分退款 | **支付域终态**：本原支付仍存在「可分次/可分笔」继续冲退的剩余有效实收或未冲尽义务（与 §5.2 再次进入 `REFUND_PROCESSING` 组合使用）。**不是**「仅对 `charge_item_ids` 快照内部分行冲实付、其余行仍挂在原支付下」的含义。**禁止**与产品「半退」（整单冲 + 未选行重开，**BR-PMR-017**）混读 |
| `REFUND_PROCESSING` | 退款中 | 退款处理中，防止并发重复退款，允许退款完成 |
| `ERROR` | 错误 | 支付处理出错或误录 |

> **术语区分（必守）**：`PARTIAL_REFUNDED` 仅描述 **PaymentReconciliation** 状态机上的「仍可继续退」的收口语义；产品「半退」指**同一 payment 下只退部分收费项**时的编排（**整单资金冲正 + 未选退行重开**），权威口径 **BR-PMR-017**。ChargeItem 侧重开行与 `refund_id` 链见 **DM-ADM-011** **BR-CHG-015**。

---

## 3. 实体

```
PaymentReconciliation（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │              PaymentReconciliation                   │
  │                                                      │
  │  - encounterId / patientId / accountId              │
  │  - kindEnum / statusEnum / outcomeEnum              │
  │  - tenderedAmount / returnedAmount / displayAmount  │
  │  - chargeItemIds / cashierSettlementId              │
  │                                                      │
  │  1..N                                                │
  │  ┌──────────────────────────────────────────────┐    │
  │  │         PaymentRecDetail（1..N）             │    │
  │  │  - reconciliationId / predecessorId          │    │
  │  │  - payEnum / payLevelEnum / accountId        │    │
  │  │  - amount / returnAmount / chargeAmount      │    │
  │  │  - payTransNo / payTransText / resultEnum    │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  │  0..N                                                │
  │  ┌──────────────────────────────────────────────┐    │
  │  │     PaymentYbSettlement（0..N）              │    │
  │  │  - reconciliationId / setlId                 │    │
  │  │  - settlementRecordId / status               │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  │  0..N                                                │
  │  ┌──────────────────────────────────────────────┐    │
  │  │        PaymentRecStatic（0..N）              │    │
  │  │  - reconciliationId / countBy                │    │
  │  │  - typeCode / typeName / amount              │    │
  │  └──────────────────────────────────────────────┘    │
  └──────────────────────────────────────────────────────┘
```

### 3.1 支付流水（PaymentReconciliation）

PaymentReconciliation 聚合根的属性定义，对应 `fin_payment_reconciliation` 表。


| 属性       | 字段名                         | 类型                          | 必填  | 说明                            |
| -------- | --------------------------- | --------------------------- | --- | ----------------------------- |
| 支付流水标识   | `id`                        | `Long`                      | 是   | 全局唯一标识，雪花 ID（PK）              |
| 支付状态     | `status_enum`               | `String`（`PaymentStatus`）  | 是   | 见 §2.3 / §4.1（存枚举 code）        |
| 关联原单标识   | `relation_id`               | `Long`                      | 否   | 退费单指向原支付单，用于退费链路追溯            |
| 支付业务号    | `payment_no`                | `String`                    | 是   | 支付业务标识号，用于外部追溯                |
| 付款类别     | `payment_enum`              | `String`（`PaymentType`）    | 是   | 见 §4.3（存枚举 code）               |
| 付款实体关联标识 | `payment_reconciliation_id` | `Long`                      | 否   | 历史兼容字段                        |
| 支付业务类型   | `kind_enum`                 | `Integer`（`PaymentKind`）    | 是   | 见 §4.2                        |
| 收费员标识    | `enterer_id`                | `Long`                      | 是   | 收费员/经办人 ID                    |
| 支付发起人类型  | `issuer_enum`               | `Integer`                   | 否   | 见 §4.5                        |
| 患者标识     | `patient_id`                | `Long`                      | 是   | 关联患者聚合根 ID                    |
| 责任医师标识   | `practitioner_id`           | `Long`                      | 否   | 责任医务人员 ID                     |
| 支付结果     | `outcome_enum`              | `Integer`（`PaymentOutcome`） | 否   | 见 §4.4                        |
| 支付位置标识   | `location_id`               | `Long`                      | 否   | 支付窗口/终端位置                     |
| 到期时间     | `expiration_date`           | `Date`                      | 否   | 支付有效到期时间                      |
| 应收金额     | `tendered_amount`           | `BigDecimal`                | 是   | 应收/实收口径金额                     |
| 找零金额     | `returned_amount`           | `BigDecimal`                | 否   | 找零金额                          |
| 展示总额     | `display_amount`            | `BigDecimal`                | 是   | 前端展示用总金额                      |
| 打印次数     | `print_count`               | `Integer`                   | 是   | 收据/发票打印次数                     |
| 合同编码     | `contract_no`               | `String`                    | 否   | 费用性质编码（自费/医保合同）；自付与医保同时付费时可为空 |
| 收费明细标识集合 | `charge_item_ids`           | `String`                    | 是   | 逗号分隔的 ChargeItemId 集合，表达本支付事实关联的明细**快照**；**半退**后重开的新行归属后续支付草稿/新支付，由应用按 **BR-PMR-017** 连续编排维护，本字段仍以「该笔支付落账时所锁定/引用的明细分组」为语义锚点         |
| 就诊标识     | `encounter_id`              | `Long`                      | 是   | 关联就诊聚合根 ID                    |
| 结算时间     | `bill_date`                 | `Date`                      | 否   | 结算完成时间                        |
| 票据标识     | `invoice_id`                | `Long`                      | 否   | 关联发票聚合根 ID                    |
| 账户标识     | `account_id`                | `Long`                      | 否   | 关联就诊账户聚合根 ID；自付与医保同时付费时可为空    |
| 医保清算标识   | `yb_clear_flag`             | `Boolean`                   | 否   | `false`=未清算，`true`=已清算        |
| 交账批次标识   | `cashier_settlement_id`     | `Long`                      | 否   | 非空 = 已纳入收费员交账；逻辑指向 **DM-FIN-003** `fin_cashier_settlement.id`（无物理外键） |


> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。

### 3.2 支付流水明细（PaymentRecDetail）

支付流水明细记录每一笔**业务自付渠道**的支付事实，与支付流水聚合根为 1:N 关系。每条明细承载一个支付账户、一种支付方式的单笔金额与交易回执。**医保基金/个账/统筹等不落本表**，见 **§3.4 PaymentYbSettlement**。对应 `fin_payment_rec_detail` 表。


| 属性     | 字段名                 | 类型                         | 必填  | 说明                        |
| ------ | ------------------- | -------------------------- | --- | ------------------------- |
| 明细标识   | `id`                | `Long`                     | 是   | 全局唯一标识，雪花 ID（PK）          |
| 支付流水标识 | `reconciliation_id` | `Long`                     | 是   | 关联支付主记录 ID                |
| 前置明细标识 | `predecessor_id`    | `Long`                     | 否   | 退费场景关联原明细 ID              |
| 目标类型   | `target_enum`       | `Integer`                  | 否   | 明细作用对象类型（当前落库未使用）         |
| 账户标识   | `account_id`        | `Long`                     | 是   | 关联账户 ID                   |
| 账户编码   | `account_code`      | `String`                   | 否   | 冗余存储，用于列表展示               |
| 支付方式   | `pay_enum`          | `String`                   | 是   | ZXPlat 收银语义码（477 / `PaymentChannel`），见 §4.7 |
| 支付级别   | `pay_level_enum`    | `Integer`                  | 否   | 支付级别枚举，见 §4.8             |
| 支付金额   | `amount`            | `BigDecimal`               | 是   | 本笔支付金额                    |
| 退还金额   | `return_amount`     | `BigDecimal`               | 否   | 退费场景下退还金额                 |
| 收费金额   | `charge_amount`     | `BigDecimal`               | 否   | 对应收费金额（与 ChargeItem 口径一致） |
| 支付交易号  | `pay_trans_no`      | `String`                   | 否   | 渠道回执交易号 / 医保批次号           |
| 支付交易描述 | `pay_trans_text`    | `String`                   | 否   | 支付交易补充描述 / 医保结算 ID        |
| 支付交易时间 | `pay_trans_date`    | `Date`                     | 否   | 渠道回执交易时间                  |
| 交易前余额  | `before_balance`    | `BigDecimal`               | 否   | 预交金/账户交易前余额               |
| 交易后余额  | `after_balance`     | `BigDecimal`               | 否   | 预交金/账户交易后余额               |
| 明细结果   | `result_enum`       | `Integer`（`PaymentResult`） | 是   | 见 §4.9                    |


> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。
> 应用编排约束：门诊挂号正式结算（IF-REG-07）在生成 `SettleOutpatientPaymentCommand` 前，必须先解析默认 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`** 就诊账户（`AccountQueryService#getRegistrationAccount`）并回填到请求明细 DTO（`accountId/accountCode`），禁止以额外 mapper 形参传递账户信息。

### 3.3 支付统计快照（PaymentRecStatic）

付款记录的费用分类统计快照，用于统计报表与费用分析。与支付流水聚合根为 0:N 关系。对应 `fin_payment_rec_static` 表。


| 属性       | 字段名                 | 类型                         | 必填  | 说明                   |
| -------- | ------------------- | -------------------------- | --- | -------------------- |
| 快照标识     | `id`                | `Long`                     | 是   | 全局唯一标识，雪花 ID（PK）     |
| 支付流水标识   | `reconciliation_id` | `Long`                     | 是   | 关联支付主记录 ID           |
| 统计方式     | method_code         | `PaymentStatisticalMethod` | 是   | 见 §4.10；默认值 2（医保十四项） |
| 分类编码     | `type_code`         | `String`                   | 是   | 字典编码（财务分类或医保分类）      |
| 分类名称     | `type_name`         | `String`                   | 否   | 字典名称                 |
| 金额       | `amount`            | `BigDecimal`               | 是   | 该分类下金额；**权威**=关联 `charge_item.total_price` **代数和**（含冲正负行；计算刻度 6 位）；**禁止**对行额取绝对值再汇总 |
| 收费明细标识集合 | `charge_item_ids`   | `String`                   | 否   | 关联收费项 ID 集合          |


> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。
> **写路径**：结算/退费落账由 `PaymentFinTypeStaticSnapshotWriter` 按挂载收费行重建；日结上半区/总收入优先读本快照（**AS-OP-FIN-004 BR-OP-DCL-010** / **AS-IP-FIN-005 BR-IP-DCL-002**）。

### 3.4 医保结算关联（PaymentYbSettlement）

支付流水与医保正式结算凭证的关联行，与聚合根为 0:N 关系。**一行一个平台 `setl_id`**；混合结算时同一 payment 可有多行。对应 `fin_payment_yb_settlement` 表（Flyway V98/V100；主表 `yb_settle_ids` 列已于 V99 删除）。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 关联标识 | `id` | `Long` | 是 | 雪花 ID（PK） |
| 支付流水标识 | `reconciliation_id` | `Long` | 是 | 关联 `fin_payment_reconciliation.id` |
| 平台结算 ID | `setl_id` | `String` | 是 | 医保平台 `setl_id` |
| 收费批次号 | `chrg_bchno` | `String` | 否 | CHS 收费批次 |
| 就诊 ID | `mdtrt_id` | `String` | 否 | 医保就诊 ID |
| 结算凭证标识 | `settlement_record_id` | `Long` | 否 | 逻辑指向 CHS `yb_settlement_record.id` |
| 账户标识 | `account_id` | `Long` | 否 | 关联就诊账户 |
| 关联状态 | `status` | `String` | 是 | `CONFIRMED` / `REVOKED` |

> 读侧：`PaymentReconciliationQueryModel.ybSettlements` 挂载本表行；**日结 UI 费用性质（自费/医保）筛选**以 payment `contract_no` → **`fin_contract`** 的 `category_enum` / `yb_flag` 为准；医保金额分摊与对账展示仍经本表及 `settlement_record_id` 关联 CHS 侧凭证，**禁止**再从 `fin_payment_rec_detail` 按医保支付枚举汇总。

---

## 4. 值对象

### 4.1 支付状态（PaymentStatus）

见 §2.3 状态定义中的枚举表。

**枚举类**：`PaymentStatus`

### 4.2 支付业务类型（PaymentKind）


| 编码值 | 常量名                 | 说明          |
| --- | ------------------- | ----------- |
| 0   | `HOSPITAL_DEPOSIT`  | 住院存款（预交金收取） |
| 1   | `OUTPATIENT_CLINIC` | 门诊收费（含挂号收费） |
| 2   | `INPATIENT_CLINIC`  | 住院费用（出院结算）  |


### 4.3 付款类别（PaymentType）

**枚举类**：`PaymentType`


| 编码值 | 常量名      | 说明       | 金额乘数 |
| --- | -------- | -------- | ---- |
| 0   | `PAY`    | 付费（正向收款） | 1    |
| 1   | `UN_PAY` | 退费（逆向退款） | -1   |

> `payment_enum` 持久化口径为枚举 `code`（`String`），实现中禁止写死 `"PAY"` / `"UN_PAY"`，统一使用 `PaymentType.PAY.getCode()`、`PaymentType.UN_PAY.getCode()`。


### 4.4 支付结果（PaymentOutcome）


| 编码值 | 常量名         | 说明     |
| --- | ----------- | ------ |
| 0   | `QUEUED`    | 排队等待处理 |
| 1   | `COMPLETED` | 支付成功完成 |
| 3   | `ERROR`     | 支付处理失败 |
| 4   | `PARTIAL`   | 部分支付成功 |


### 4.5 收费明细标识集合（ChargeItemIdSet）

表示本次支付作用的收费明细快照，以逗号分隔的 ChargeItemId 集合落库。


| 属性   | 类型       | 说明                                |
| ---- | -------- | --------------------------------- |
| 标识集合 | `String` | 逗号分隔的 ChargeItemId，后续退款与重算基于该快照追溯 |


> **不变式**：集合不允许为空（至少包含一条收费明细标识）；预交金收取场景除外。

### 4.6 医保结算标识集合（InsuranceSettleIdSet）

> **已废止（v1.4.4）**：原主表 `yb_settle_ids` 逗号串已删除。医保结算关联改由 **§3.4 PaymentYbSettlement** 多行表达；写侧命令携带 `List<PaymentYbSettlementItemCommand>`，读侧见 `PaymentYbSettlementQueryModel` 列表。

### 4.7 支付方式（ZXPlat 收银语义码 · ADR-PAY-001）

> **落库口径（v1.4.9）**：`PaymentRecDetail.pay_enum` 存 **ZXPlat 收银语义码**（`ter_concept.code_system_id = 477` / `PaymentChannel.getCode()`），**禁止**写入 YbPayment `220xxx`。真源：[01-概要设计](../../模块/zxplat-module-unionpay-bpc/01-概要设计.md)。

| `pay_enum` | `PaymentChannel` | 说明 |
| --- | --- | --- |
| `CASH` | `CASH` | 手工现金 |
| `WECHAT` | `WECHAT` | 固定码手工，或银联被扫 BPC `WEIX` 映射 |
| `ALIPAY` | `ALIPAY` | 固定码手工，或银联被扫 BPC `ZFBA` 映射 |
| `BANK_CARD` | `BANK_CARD` | 手工刷卡，或银联被扫 BPC `UPAY` 映射 |
| `INSURANCE_FUND` | `INSURANCE_FUND` | 医保统筹（服务端写入；非用户单选） |
| `INSURANCE_ACCT` | `INSURANCE_ACCT` | 医保个账（服务端写入；非用户单选） |
| `CASHIER` | `CASHIER` | 内部占位（草稿/展示回落；非用户单选） |
| `DEPOSIT_OFFSET` | `DEPOSIT_OFFSET` | **预交金冲抵**（住院出院结算明细；服务端写入；**不进**日结/交账自付渠道小计；**不是** `HOSPITAL_DEPOSIT` 主单） |

- **`SCAN_PAY`**：仅 UI/入参（477 院点 Flyway 可选）；**禁止落库**。
- **同码多源**：同一 `WECHAT`/`ALIPAY` 可能来自 health 手工或银联被扫；是否经银联以是否存在 `upc_bpc_order` 区分。
- **日结排除**：`DEPOSIT_OFFSET` 与统筹/个账一样不得计入微信/现金等自付渠道小计（见 DD「住院预交金结算口径纠错」）。

#### 4.7.1 YbPayment 树（只读 · 医保结算域）

> 以下编码属 **CHS/医保报文** 分类树，**不落** `fin_payment_rec_detail`；老 JSP/UnionPayPlugin 对照见 `docs/模块/zxplat-module-unionpay-bpc/` 院点文档。

支付方式采用树状枚举结构，覆盖医保基金支付、个人支付和其他支付三大类。

**一级分类**


| 编码值    | 常量名             | 说明          |
| ------ | --------------- | ----------- |
| 100000 | `YB_FUND_PAY`   | 基金支付总额      |
| 200000 | `SELF_PAY`      | 个人负担总金额     |
| 300000 | `OTHER_PAY`     | 其他（如医院负担金额） |
| 400000 | `SCHOOL_GF_PAY` | 学校垫支（院校定制）  |


**常用个人支付二级 / 三级分类（摘要）**


| 编码值    | 常量名                     | 父级            | 说明        |
| ------ | ----------------------- | ------------- | --------- |
| 210000 | `SELF_YB_ZH_PAY`        | SELF_PAY      | 个人医保账户支付  |
| 220000 | `SELF_CASH_PAY`         | SELF_PAY      | 个人现金支付金额  |
| 230000 | `SELF_VX_PAY`           | SELF_PAY      | 微信支付金额    |
| 240000 | `SELF_ALI_PAY`          | SELF_PAY      | 阿里支付金额    |
| 220400 | `SELF_CASH_VALUE`       | SELF_CASH_PAY | 现金        |
| 220100 | `SELF_CASH_VX_VALUE`    | SELF_CASH_PAY | 个人现金（微信）  |
| 220200 | `SELF_CASH_ALI_VALUE`   | SELF_CASH_PAY | 个人现金（支付宝） |
| 220300 | `SELF_CASH_UNION_VALUE` | SELF_CASH_PAY | 个人现金（银联）  |


### 4.8 支付级别（PaymentLevel）

> **设计预留**：实现 `PaymentRecDetail.payLevelEnum` 字段已定义（`Integer`），但 尚未建立专用枚举类。以下取值为业务规划：


| 编码值     | 说明                           |
| ------- | ---------------------------- |
| （待代码定义） | 主支付 / 辅支付 / 找零等，具体取值以枚举类落地为准 |


### 4.9 明细结果（PaymentResult）


| 编码值 | 常量名        | 说明  |
| --- | ---------- | --- |
| 0   | `UNPAID`   | 未支付 |
| 1   | `PAID`     | 已支付 |
| -2  | `REFUNDED` | 已退费 |


### 4.10 统计方式（PaymentStatisticalMethod）


| 编码值 | 常量名                | 说明                               |
| --- | ------------------ | -------------------------------- |
| 1   | `FIN_TYPE_CODE`    | 按字典 `fin_type_code`（财务分类）统计      |
| 2   | `MED_CHRGITM_TYPE` | 按医保十四项 `med_chrgitm_type` 统计（默认） |


---

## 5. 领域事件

### 5.1 事件定义


| 事件名称      | 英文名称                                     | 触发时机        | 携带数据 / 最小载荷                           | 消费方                        |
| --------- | ---------------------------------------- | ----------- | ------------------------------------- | -------------------------- |
| 支付草稿已创建   | `PaymentDraftCreatedEvent`               | 预结算生成支付草稿后  | 支付流水标识、就诊标识、患者标识、收费明细标识集合、应收金额、租户（若有） | 收费窗口会话、QueryService（若启用）            |
| 门诊支付已结算   | `OutpatientPaymentSettledEvent`          | 门诊结算成功后     | 支付流水标识、就诊标识、收费明细标识集合、应收金额、结算时间、收费员标识；**不含就诊生命周期状态**（与就诊域 `OutpatientRegistrationConfirmedEvent` 解耦，避免普通门诊收银误推进挂号相关QueryService） | ChargeItem 推进编排、Invoice、对账；**挂号列表**等可由QueryService 刷新本事件做 **金额摘要**（如 `displayAmount`，见 `ClinicRegister` / `DM-ADM-002 §6.2 EncounterQueryService`）；就诊展示状态 **不**由本事件承载 |
| 门诊退费已申请   | `OutpatientRefundRequestedEvent`         | 门诊退款申请后     | 支付流水标识、关联原单标识、就诊标识、退费金额               | 退费窗口、原支付追溯                 |
| 门诊退费已完成   | `OutpatientRefundCompletedEvent`         | 门诊退款完成后     | 支付流水标识、关联原单标识、就诊标识、退费金额、退费类型（**支付终态**：原单收口为 `REFUNDED` 或 `PARTIAL_REFUNDED`，含义见 §2.3 表下说明；**非**产品「半退」同义词）   | ChargeItem 退费收口、对账         |
| 住院支付已结算   | `InpatientPaymentSettledEvent`           | 住院结算成功后     | 支付流水标识、就诊标识、收费明细标识集合、应收金额、医保结算标识集合    | 出院流程、医保上传、对账               |
| 住院结算已取消   | `InpatientSettleCancelledEvent`          | 取消住院结算完成后   | 支付流水标识、关联原单标识、就诊标识                    | 费用回退编排、病区通知                |
| 支付状态已变更   | `PaymentStatusEditedEvent`               | 支付状态和结算号回写后 | 支付流水标识、变更前状态、变更后状态、医保结算标识集合           | 收费列表刷新、报表快照（若启用）           |
| 预结算已放弃    | `PreSettlementAbortedEvent`              | 收费前放弃预结算后   | 支付流水标识、就诊标识                           | 收费窗口、草稿回收                  |
| 挂号收费草稿已刷新 | `RegistrationPaymentDraftRefreshedEvent` | 挂号收费草稿刷新后   | 支付流水标识、患者标识                           | 挂号收费界面                     |
| 单笔支付结果已收口 | `SinglePaymentResultClosedEvent`         | 单笔支付结果收口后   | 支付流水标识、最终状态                           | 渠道对账、明细闭环                  |
| 支付回执明细已收口 | `PaymentDetailReceiptFinalizedEvent`     | 支付回执明细收口后   | 支付流水标识、明细数量                           | 日结/交班聚合                    |
| 预交金已收取    | `AdvanceDepositCollectedEvent`           | 预交金收取落 **DRAFT** 后 | 支付流水标识、就诊标识、患者标识、收取金额                 | 日结押金口径、流水读侧                 |
| 预交金已退还    | `AdvanceDepositRefundedEvent`            | 预交金退还落 **DRAFT** 后 | 支付流水标识、就诊标识、患者标识、退还金额                 | 日结押金口径、流水读侧                 |


#### 5.1.1 门诊挂号费结清与 Encounter、QueryService协同

- **支付域**：`settleOutpatientPayment` 成功后发布 `OutpatientPaymentSettledEvent`（最小载荷见 §5.1）。本事件**仅**表达收银流水语义，**禁止**在载荷中携带就诊生命周期状态。
- **写模型（Encounter）**：挂号正式结算用例（如 IF-REG-07）在支付成功落库之后，由**应用层**调用 `EncounterDomainService#completeOutpatientRegistration`，将门诊就诊从 `PLANNED` 推进为 `REGISTERED`；持久化成功后发布 **`OutpatientRegistrationConfirmedEvent`**（载荷 `statusEnum` 与写模型一致，典型 `REGISTERED`），详见 `DM-ADM-002` §5.1 / §6.1.2。
- **QueryService 能力**：挂号列表等与收银相关的 **金额摘要** 可订阅 `OutpatientPaymentSettledEvent`（如 `displayAmount` 覆盖策略，见 `ClinicRegister` §4.2）。**就诊展示状态**（含挂号单、`OutpatientEncounterContextQueryModel` 上的 `REGISTERED`）由应用层在 **`OutpatientRegistrationConfirmedEvent`** 后刷新上下文或请求级加载，从 **`event.statusEnum`** 派生展示文本（§4.2 真源：`RegistrationSlip` / **`EncounterContextReader`** / `adm_encounter`）。


### 5.2 事件触发规则


| 当前状态        | 操作         | 目标状态         | 发布事件                                     |
| ----------- | ---------- | ------------ | ---------------------------------------- |
| —           | 预结算生成草稿    | DRAFT        | `PaymentDraftCreatedEvent`               |
| —           | 预交金收取（`collectAdvanceDeposit`） | DRAFT        | `AdvanceDepositCollectedEvent`           |
| —           | 预交金退还（`refundAdvanceDeposit`） | DRAFT        | `AdvanceDepositRefundedEvent`            |
| DRAFT       | 门诊结算       | SUCCESS      | `OutpatientPaymentSettledEvent`          |
| DRAFT       | 住院支付结算（`settleInpatientPayment`，含预交金收/退） | SUCCESS | `InpatientPaymentSettledEvent`           |
| DRAFT       | 放弃预结算      | CANCELLED       | `PreSettlementAbortedEvent`              |
| DRAFT       | 挂号草稿刷新     | DRAFT（新草稿替换） | `RegistrationPaymentDraftRefreshedEvent` |
| SUCCESS     | 门诊退费申请（逆向单） | 原单 SUCCESS；新建逆向 `REFUND_PROCESSING` | `OutpatientRefundRequestedEvent`         |
| REFUND_PROCESSING   | 门诊退费完成 | REFUNDED   | `OutpatientRefundCompletedEvent`         |
| SUCCESS     | 住院取消结算     | REFUNDED   | `InpatientSettleCancelledEvent`          |
| 任意状态        | 支付状态/结算号回写 | 目标状态         | `PaymentStatusEditedEvent`               |
| 任意状态        | 单笔结果收口     | 目标状态         | `SinglePaymentResultClosedEvent`         |
| 任意状态        | 回执明细收口     | 同状态          | `PaymentDetailReceiptFinalizedEvent`     |


---

## 6. 领域服务

### 6.1 写侧领域服务（PaymentReconciliationDomainService）

#### 6.1.1 预结算生成支付草稿（`createPaymentDraftFromPreSettle`）

- **业务意义**：在正式收款前生成支付主记录与子明细草稿，锁定收费明细快照并进入可结算态。
- **边界说明**：仅创建本聚合内 DRAFT 主从数据，不替代预结算域的全部规则与渠道参数校验。
- **入参**：预结算命令（含收费明细标识集合、就诊/患者上下文、`paymentEnum=PAY`、`statusEnum=DRAFT` 等，以命令对象为准）。
- **出参**：支付流水标识。
- **前置条件**：收费明细标识集合非空且未被其他草稿占用；满足 `BR-PMR-001` 等集合口径。
- **状态变化**：创建主记录为 `DRAFT`，明细为 `UNPAID`（见 §4.9 `PaymentResult`）。
- **聚合内影响**：写入 `fin_payment_reconciliation` 与 `fin_payment_rec_detail`；典型入口含门诊预结算 `prePayment`、挂号预结算 `regPrePay`、住院预结算 `inpatientPreSettle`、部分退费后重算等。
- **事件输出**：`PaymentDraftCreatedEvent`。
- **失败语义**：明细被占用、集合非法或持久化失败时拒绝生成草稿。

#### 6.1.2 门诊收费结算（`settleOutpatientPayment`）

- **业务意义**：完成门诊侧支付事实落账，并将支付结果与结算号回写到主从记录。
- **边界说明**：支付域内完成状态与明细收口；ChargeItem 推进由应用层连续动作编排（见 §6.3）。
- **入参**：门诊结算命令。
- **出参**：支付流水标识。
- **前置条件**：主记录状态为 `DRAFT`；渠道回执满足完成条件。
- **状态变化**：`DRAFT` → `SUCCESS`（见 §2.3 / §5.2）。
- **聚合内影响**：明细结果更新为 `PAID`；回写结算号；与 `BR-PMR-010` 收敛。
- **事件输出**：`OutpatientPaymentSettledEvent`（连续动作：推进 ChargeItem 为已收费/BILLED 由应用编排）。
- **与挂号正式结算用例协同**：同一业务用例内，应用层在 `settleOutpatientPayment` 成功后编排 `completeOutpatientRegistration`（写模型 `REGISTERED`）；读侧 `REGISTERED` 展示以 **`OutpatientRegistrationConfirmedEvent`** 为投影锚点（见 §5.1.1），**非** `OutpatientPaymentSettledEvent`。
- **失败语义**：状态非法、回执缺失或并发冲突时拒绝结算。

#### 6.1.3 住院收费结算（`settleInpatientPayment`）

- **业务意义**：完成住院出院（或中途）结算的支付主记录落账，并承载医保住院结算号回写。
- **边界说明**：不展开医保报文协议；住院就诊状态联动由应用编排。
- **入参**：住院结算命令。
- **出参**：支付流水标识。
- **前置条件**：主记录状态为 `DRAFT`。
- **状态变化**：`DRAFT` → `SUCCESS`。
- **聚合内影响**：回写医保住院结算号；更新明细结果；主状态推进为成功。
- **事件输出**：`InpatientPaymentSettledEvent`。
- **失败语义**：医保回写失败、状态非法或金额口径不一致时拒绝或按失败语义回滚（以落库与交互契约为准）。

#### 6.1.5 门诊退费申请（`requestOutpatientRefund`）

- **业务意义**：基于原支付单生成逆向退款单及负向明细，创建时写入 `relation_id` 追溯原单。
- **边界说明**：生成新支付主从记录表达退费，不在原明细上做不可追踪覆盖（`BR-PMR-011`）；**原收费主单状态不变**（保持 `SUCCESS`）。
- **入参**：退费申请命令（须含 `relationId`、`contractNo`、**`paymentDetails` 原路渠道副本**——含医保统筹/个账与自付；空明细禁止落库，供日结交账弹窗与渠道汇总回显）。
- **出参**：支付流水标识（退款单）。
- **前置条件**：原支付单处于 `SUCCESS`（或 `PARTIAL_REFUNDED`）；满足 `BR-PMR-007` / `BR-PMR-008`；原单尚无未删除的 `REFUND_PROCESSING`/`REFUNDED` 逆向单。
- **状态变化**：逆向退款单新建为 `REFUND_PROCESSING`；原单保持 `SUCCESS`。
- **聚合内影响**：生成关联退款单（`PaymentType = UN_PAY`）；`relation_id` 指向原单；**按命令 `paymentDetails` 落逆向明细**（应用层须按原单 `fin_payment_rec_detail` 原路装配，账单退费 **BR-REF-011** / 退号 **IF-REG-10** 同口径）；新建主单落库前须通过 `initDraft` 等价初始化（如 `print_count=0`、`yb_clear_flag=false`）再置为 `REFUND_PROCESSING`，以满足库非空约束。**并发防重（硬）**：先对原单加悲观写锁再判重落库；库层部分唯一索引 `uk_fin_payment_rec_active_unpay_relation`（`relation_id`，条件：未删 + `UN_PAY` + `REFUND_PROCESSING`/`REFUNDED`）兜底；唯一冲突视为「原单已存在退款单」。`abortOutpatientRefundAttempt` 清空 `relation_id` 后不占唯一坑，允许重试。
- **事件输出**：`OutpatientRefundRequestedEvent`。
- **失败语义**：原单状态非法或已存在进行中/已完成逆向单时拒绝申请；并发第二笔插入唯一索引冲突时同口拒绝。

#### 6.1.6 门诊退费完成（`completeOutpatientRefund`）

- **业务意义**：收口**逆向退款支付单**（`relationId` 指向原单），将退款单从 `REFUND_PROCESSING` 推进为 `REFUNDED`。
- **边界说明**：**原支付主单**终态恒为 **`SUCCESS`**（历史事实）；ChargeItem 拟退行/被替代源行收口由 `completeOutpatientBillRefund` 负责。二者与本文方法由应用层同一执行编排串联。
- **入参**：退费完成命令（**逆向退款单**主键）。
- **出参**：—。
- **前置条件**：逆向退款单处于 `REFUND_PROCESSING`。
- **状态变化**：逆向退款单 → `REFUNDED`。
- **聚合内影响**：更新逆向支付主记录终态。
- **事件输出**：`OutpatientRefundCompletedEvent`（载荷含 `relationId` 指向原单）。
- **失败语义**：状态机不允许时拒绝完成。

#### 6.1.6a 门诊退费失败回滚（`abortOutpatientRefundAttempt`）

- **业务意义**：本地编排（收费项收口、半退再结算等）失败时，回滚已创建的逆向单，清空 `relation_id` 并置 `CANCELLED`，允许原单再次发起退费。
- **边界说明**：仅作用于仍处于 `REFUND_PROCESSING` 的逆向单；原收费单始终不改状态。
- **入参**：逆向支付主键。
- **出参**：—。
- **前置条件**：逆向单处于 `REFUND_PROCESSING`（其他状态忽略）。
- **状态变化**：逆向单 → `CANCELLED`；`relation_id` → `null`。
- **事件输出**：—。
- **失败语义**：逆向单不存在时按领域 `mustGet` 语义失败。

#### 6.1.6b 原支付主单静默收口（`finalizeOriginalOutpatientPayment`，已废弃）

- **业务意义**：**已废弃**；门诊主路径不再将原单改为 `REFUNDED`。保留空实现以兼容旧引用。
- **边界说明**：收退展示以支付状态区分：`SUCCESS`=收费（正）、`REFUNDED`=退费（负）；原单保持 `SUCCESS` 与逆向单 `REFUNDED` 成对出现。

#### 6.1.8 退号联动退费完成（`completeRegistrationCancelRefund`）

- **业务意义**：**挂号退号**场景下，在 `requestOutpatientRefund` 之后，将**逆向单**收口为 `REFUNDED`；**仅整单全退**，无产品半退、无保留链再生。
- **边界说明**：与 **§6.1.5～6.1.6**（门诊账单退费执行）**编排隔离**；收费明细收口由 **DM-ADM-011** `returnRegistrationRefundChargeItems` 承担；**原收费单保持 `SUCCESS`**。调用本方法前，应用层 **`requestOutpatientRefund` 须已写入原路 `contractNo` + `paymentDetails`**（对齐 **AS-OP-FIN-001 IF-REG-10** / **AS-OP-FIN-003 BR-REF-011**）；本方法不补写渠道明细。
- **入参**：原单主键、逆向单主键。
- **出参**：—。
- **前置条件**：逆向单 `REFUND_PROCESSING`；`reverse.relation_id = original.id`；原单 `SUCCESS`。
- **状态变化**：逆向单 → `REFUNDED`；原单不变。
- **事件输出**：**一次** `OutpatientRefundCompletedEvent`（`aggregateId`=逆向单，`relationId`=原单）。
- **失败语义**：关联不一致或状态非法时拒绝。

#### 6.1.7 住院反结逆向单（`cancelInpatientSettleAsRefund`）

- **业务意义**：以逆向支付记录表达**住院反结**（AS-IP-FIN-004）；本聚合负责逆向单创建（`payment_enum=UN_PAY`、`relation_id=原单`）及逆向单收口；**ChargeItem 复制再生不在本聚合**。
- **边界说明**：不替代医保平台反交易规则；ChargeItem **复制再生**由应用编排调用 **DM-ADM-011** `revertInpatientSettledChargeItems`（源 `SETTLED` 保持终态，插入 `PENDING_SETTLEMENT` 新行 + `refund_id`）。
- **入参**：取消结算命令（含 `payment_enum=UN_PAY`、正向金额、渠道明细副本）。
- **出参**：支付流水标识（逆向单）。
- **前置条件**：原结算支付事实存在且允许取消；`existsByRelationId(原单.id)` 为 false。
- **状态变化**：新建逆向单 → `REFUND_PROCESSING`；原正向结算单保持 `SUCCESS`（见 §6.1.7a）。
- **聚合内影响**：生成逆向支付记录；新建逆向主单须先 `initDraft` 再置 `REFUND_PROCESSING`。
- **事件输出**：收口阶段发布 `InpatientSettleCancelledEvent`（见 §6.1.7a）。
- **失败语义**：反结算失败或原单状态非法时拒绝或按失败语义处理。

#### 6.1.7a 住院反结收口（`completeInpatientSettleCancelRefund`）

- **业务意义**：将逆向反结单从 `REFUND_PROCESSING` 收口为 `REFUNDED`；**原正向结算单保持 `SUCCESS` 与原始 `bill_date`**（与门诊退费 §6.1.6 对称）。
- **入参**：原单主键、逆向单主键、可选医保回执与 `bill_date`（仅写逆向单）。
- **出参**：—。
- **前置条件**：原单 `SUCCESS`；逆向单 `REFUND_PROCESSING`；`reverse.relation_id = original.id`。
- **状态变化**：逆向单 → `REFUNDED`；原单不变。
- **事件输出**：`InpatientSettleCancelledEvent`（`aggregateId`=逆向单）。
- **失败语义**：关联不一致或状态非法时拒绝。

#### 6.1.8 支付状态与结算号回写（`updatePaymentStatusWithSettleIds`）

- **业务意义**：在结算链路后期统一回写主状态、结果枚举与医保结算标识集合，支撑多退少补等收口场景。
- **边界说明**：不写 ChargeItem；仅更新本聚合持久化字段。
- **入参**：回写命令（含目标主键、状态、结算号集合等）。
- **出参**：—。
- **前置条件**：主记录存在且非不允许回写的终态（以实现与 `BR-PMR-002` 为准）。
- **状态变化**：按命令推进到目标状态（见 §5.2「任意状态」行组）。
- **聚合内影响**：更新 `status_enum` / `outcome_enum`；持久化 **§3.4** `fin_payment_yb_settlement` 关联行（命令 `ybSettlements`）。
- **事件输出**：`PaymentStatusEditedEvent`。
- **失败语义**：主键不存在、终态禁止变更或回写冲突时拒绝。

#### 6.1.9 收费前放弃预结算（`abortPreSettlementBeforePayment`）

- **业务意义**：终止当前预结算链路，释放草稿占用，避免旧草稿误收。
- **边界说明**：不推进 ChargeItem；可涉及医保批次回滚（如适用，以落库与交互契约为准）。
- **入参**：放弃命令。
- **出参**：—。
- **前置条件**：存在可放弃的预结算上下文（以落库与交互契约为准）。
- **状态变化**：主记录 `DRAFT` → `CANCELLED`（见 §5.2）。
- **聚合内影响**：终止预结算；释放草稿占用。
- **事件输出**：`PreSettlementAbortedEvent`。
- **失败语义**：无可放弃链路或外部渠道拒绝终止时拒绝或告警（以落库与交互契约为准）。

#### 6.1.10 挂号收费草稿刷新（`refreshRegistrationPaymentDraft`）

- **业务意义**：按患者维度清理当日挂号收费草稿并重建，防止旧单误收。
- **边界说明**：仅处理挂号收费草稿域数据，不编排其他就诊收费。
- **入参**：刷新命令。
- **出参**：支付流水标识（新草稿）。
- **前置条件**：患者标识合法；符合当日草稿清理策略。
- **状态变化**：旧草稿被替换，新记录仍为 `DRAFT`（见 §5.2）。
- **聚合内影响**：清理并重建挂号收费草稿主从数据。
- **事件输出**：`RegistrationPaymentDraftRefreshedEvent`。
- **失败语义**：患者维度冲突或重复刷新冲突时拒绝。

#### 6.1.11 单笔支付结果收口（`closeSinglePaymentResult`）

- **业务意义**：在无完整医保结算号场景下，将单笔支付按业务结果轻量收口到目标状态。
- **边界说明**：不承担完整渠道对账；不替代 `finalizePaymentDetailReceipt` 的批量明细语义。
- **入参**：收口命令。
- **出参**：—。
- **前置条件**：主记录存在；结果参数合法。
- **状态变化**：按命令推进到目标状态。
- **聚合内影响**：更新主记录状态/结果字段。
- **事件输出**：`SinglePaymentResultClosedEvent`。
- **失败语义**：状态机不允许或命令非法时拒绝。

#### 6.1.12 支付回执明细收口（`finalizePaymentDetailReceipt`）

- **业务意义**：按支付单批量更新明细结果与回执字段，将明细统一收口为已支付。
- **边界说明**：面向多渠道回执批量落库，不改变发票与 ChargeItem 规则边界。
- **入参**：明细收口命令。
- **出参**：—。
- **前置条件**：主记录存在；明细集合与支付单一致。
- **状态变化**：主状态按实现可不变或同步推进（见 §5.2「同状态」行）。
- **聚合内影响**：批量更新 `PaymentRecDetail` 的结果与回执字段；明细结果收口为 `PAID`。
- **事件输出**：`PaymentDetailReceiptFinalizedEvent`。
- **失败语义**：明细与主单不一致或回执字段校验失败时拒绝。

#### 6.1.13 预交金收取（`collectAdvanceDeposit`）

- **业务意义**：登记住院预交金**收取**支付事实，先落 **DRAFT**，由应用层再调 `settleInpatientPayment` 收口为 `SUCCESS`。
- **边界说明**：`charge_item_ids` 为空；**不**在本方法内变更 Account 余额（见 §9.1）；余额在结算成功后由应用编排调 Account 域。
- **入参**：预交金收取命令（`CollectAdvanceDepositCommand`）。
- **出参**：支付流水标识。
- **前置条件**：金额 **> 0**；`kind_enum = HOSPITAL_DEPOSIT`，`payment_enum = PAY`。
  - **正式收取/登记确认**：须带支付明细（可多渠道多行，合计=主金额）。
  - **意向草稿例外**（门诊转住院，**AS-OP-DOR-005 BR-IP-REQ-010**）：允许 `paymentDetails` 为空，仅落总额 DRAFT；**禁止**在本方法内 settle；登记确认前须补齐明细或**软删**（`softDeleteAdvanceDepositDraft`）。
- **状态变化**：创建为 `DRAFT`（见 §5.2）。
- **聚合内影响**：写入草稿主记录；有明细时写入 `PaymentRecDetail`。
- **事件输出**：`AdvanceDepositCollectedEvent`（落 DRAFT 后发布）。
- **失败语义**：金额非法或头字段不完整时拒绝。

#### 6.1.13a 软删住院预交金草稿（`softDeleteAdvanceDepositDraft`）

- **业务意义**：登记确认重建 / 取消登记 / 预交金页清理时，将意向或历史作废的住院预交金草稿从读侧移除（软删），**禁止**在预交金流水可见。
- **边界说明**：仅 `kind_enum = HOSPITAL_DEPOSIT` 且状态为 `DRAFT` 或 `CANCELLED`；**禁止**软删已 `SUCCESS` 的收款单（须走退费）。
- **入参**：支付主键 `paymentId`。
- **出参**：无。
- **前置条件**：主记录存在；kind/status 门禁通过。
- **状态变化**：`is_deleted = true`（经 SoftDeleteAspect）；不经 `CANCELLED` 中间态。
- **聚合内影响**：主记录软删；明细随读侧过滤不可见。
- **事件输出**：无（应用层编排侧处理可见性）。
- **失败语义**：非住院预交金或非 DRAFT/CANCELLED 时拒绝。

#### 6.1.14 预交金退还（`refundAdvanceDeposit`）

- **业务意义**：按账户余额退还住院预交金，先落 **DRAFT**，由应用层再调 `settleInpatientPayment` 收口为 `SUCCESS`。
- **边界说明**：**不**设置 `relation_id`（不关联原收款单）；`charge_item_ids` 为空；余额校验与扣减在应用层（退费金额 ≤ 当前账户余额，结算成功后减余额）。
- **入参**：预交金退还命令（`RefundAdvanceDepositCommand`）。
- **出参**：支付流水标识。
- **前置条件**：金额 **> 0**；`kind_enum = HOSPITAL_DEPOSIT`，`payment_enum = UN_PAY`；金额为**正数**展示；须带支付明细（退现渠道）。
- **状态变化**：创建为 `DRAFT`（见 §5.2）。
- **聚合内影响**：写入草稿主记录与明细；**无** `relation_id`。
- **事件输出**：`AdvanceDepositRefundedEvent`（落 DRAFT 后发布）。
- **失败语义**：金额或明细非法时拒绝。

### 6.2 读侧查询服务（PaymentReconciliationQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于写前校验、轨迹展示、对账与退费门控。  
> **收费员交账候选**（**DM-FIN-003** **BR-CS-001～006**）归属本聚合读侧扩展（§6.2.14～6.2.16）；`CashierSettlementQueryService` 仅读本聚合交账头（**B.6.4**）。

#### 6.2.1 写前强校验加载（`mustGetPayment`）

- **业务意义**：写链路开始前按主键强加载支付主记录，避免盲更新。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation` 单条主记录。
- **过滤口径**：默认排除已逻辑删除（`BR-PMR-015`）；按主键精确匹配。
- **组装规则**：映射为领域实体或写命令加载对象。
- **返回约束**：不存在则抛业务异常（与 `BR-PMR-012` 一致）。

#### 6.2.2a 查询住院预交金草稿（`findLatestHospitalDepositDraftByEncounter` / `listHospitalDepositDraftsByEncounter`）

- **业务意义**：登记弹窗回显门诊转住院意向预交金总额；取消登记时批量作废未 settle 草稿。
- **边界说明**：只读；`kind_enum = HOSPITAL_DEPOSIT` 且 `status_enum = DRAFT`；多条时 `findLatest*` 取主键较大者。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation` 按 `encounter_id` + 状态 DRAFT。
- **返回约束**：无草稿时 Optional 空 / 空列表；不挂载明细与医保子表。

#### 6.2.2 按就诊查询支付主记录（`findPaymentsByEncounter`）

- **业务意义**：按就诊维度列出支付主记录，支撑收费轨迹与时间线展示。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation` 按 `encounter_id` 查询。
- **过滤口径**：默认排除已逻辑删除记录。
- **组装规则**：按结算时间或主键排序（以落库与交互契约为准）；不附带明细时可二次调用明细查询。
- **返回约束**：列表可为空；字段口径与 §3.1 一致。

#### 6.2.3 按就诊与状态列表查询（`findPaySuccessOrRefundingByEncounter`）

- **业务意义**：按就诊与状态值列表筛选支付主记录，用于**就诊维度**资金态门禁（如挂号结算轨迹、是否存在成功/退款中等流水），不等价于「收费项集合」级防重。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation`。
- **过滤口径**：`encounter_id` 精确匹配 + 状态 IN 调用方传入集合（如成功态/退款中）。
- **组装规则**：返回主记录列表或 ID 列表（以落库与交互契约为准）。
- **返回约束**：与 `BR-PMR-006` 等幂等校验协同使用；不做写库副作用。
- **与门诊分批结算**：门诊正式结算 IF-OPC-07 与 **BR-OPC-005** 应采用 **6.2.3a**，避免「就诊内另有已成功批次」误拦**未重叠**的新收费项。

#### 6.2.3a 按就诊、状态与收费项交集过滤（`findPaySuccessOrRefundingOverlappingChargeItems`）

- **业务意义**：在同一就诊下检索 `SUCCESS` / `REFUND_PROCESSING` 流水，且流水头 `charge_item_ids` 与给定收费项主键集合存在交集，用于门诊正式结算防重复勾选已结项。
- **边界说明**：只读查询；交集判定在应用层解析 ids 串 挂载串后与入参集合比对（单就诊成功流水条数通常极少）。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation`（先按 `encounter_id` + 状态 IN 缩小候选集）。
- **过滤口径**：状态为成功或退款处理中；内存中对每条流水的 `charge_item_ids` 与入参集合求交。
- **组装规则**：仅返回存在交集的流水QueryService 列表。
- **返回约束**：无交集时返回空列表；与 **AS-OP-FIN-002** `EX-OPC-404` 语义一致。

#### 6.2.4 判断是否存在退款单（`hasRefundByRelationId`）

- **业务意义**：判断原单是否已存在关联退款单，支撑防重复退款门控。
- **边界说明**：只读断言，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation`（按 `relation_id` 或等价关联口径）。
- **过滤口径**：默认排除已逻辑删除（`BR-PMR-015`）；关联原单标识匹配。
- **组装规则**：返回布尔或计数判定。
- **返回约束**：与 `BR-PMR-008` 配合；不修改任何支付状态。

#### 6.2.5 查询原单对应退款单（`findReturnPayment`）

- **业务意义**：由原支付单定位退款单，支撑退费追溯与展示。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation`。
- **过滤口径**：按 `relation_id` 或项目约定关联字段匹配；默认排除已逻辑删除（`BR-PMR-015`）。
- **组装规则**：返回退款单实体或列表（以落库与交互契约为准）。
- **返回约束**：不存在时返回空或空列表；不抛业务异常除非调用方约定强校验。

#### 6.2.6 按结算时间范围查询（`findPaymentByBillDateRange`）

- **业务意义**：按 `bill_date` 时间窗检索支付主记录，支撑日报与日结。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation`。
- **过滤口径**：`bill_date` 落在闭开区间或实现约定区间；默认排除已逻辑删除（`BR-PMR-015`）。
- **组装规则**：按时间、窗口、操作员等索引友好顺序返回（以落库与交互契约为准）。
- **返回约束**：大数据量时以实现分页/流式为准；本方法文档口径保持只读。

#### 6.2.7 查询患者草稿支付（`findDraftPaymentByPatient`）

- **业务意义**：按患者定位草稿态支付单，支撑预结算清理与挂号草稿治理。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation`（`status_enum = DRAFT` 等）。
- **过滤口径**：`patient_id` 精确匹配；可按当日等业务规则附加过滤。
- **组装规则**：返回草稿主记录或列表。
- **返回约束**：无草稿时返回空；不触发写库。

#### 6.2.8 查询支付子明细（`listPaymentRecDetailsByPaymentId`）

- **业务意义**：按支付主键加载全部渠道级明细，支撑支付详情与渠道核对。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_rec_detail` 按 `reconciliation_id`（`BR-PMR-013`）。
- **过滤口径**：默认排除已逻辑删除（`BR-PMR-015`）；主键存在性由调用方保证或本方法校验。
- **组装规则**：按明细序号或实现默认顺序组装列表。
- **返回约束**：主单无明细时返回空列表；与主单金额一致性由上层校验。

#### 6.2.9 由支付单反查收费明细（`getChargeItemIdsByPayments`）

- **业务意义**：由一批支付主记录解析 `charge_item_ids` 快照集合，支撑退费、核销与重复收费校验。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation` 主表字段 `charge_item_ids`。
- **过滤口径**：输入支付 ID 列表去重；过滤已删除主单（如适用）。
- **组装规则**：拆分逗号分隔 ID 并去重合并为集合。
- **返回约束**：集合口径与 §4.6 一致；不保证 ChargeItem 仍存在（跨域一致性由上层处理）。

#### 6.2.10 按时间、收费员与合同查询支付汇总（`queryPaymentAccountContract`）

- **业务意义**：按时间窗、收费员、费用性质（合同）维度汇总支付数据，支撑对账台账与账户核销报表。
- **边界说明**：只读查询，不写库，不做跨域编排；大规模场景宜演进为QueryService/SQL 投影（见下行说明）。
- **查询范围（读库，无写入）**：时间窗内 `fin_payment_reconciliation`（及必要关联字段）。
- **过滤口径**：时间范围必选；收费员、合同编码为可选过滤条件。
- **组装规则**：当前实现多为「时间窗仓储查询 + 内存按款员/合同过滤」；投影化后以落库与交互契约为准。
- **返回约束**：返回汇总行或中间结果集；只读，不写库。

> **读侧实现说明**：`queryPaymentAccountContract`（§6.2.10）当前实现为「时间窗仓储查询 + 内存按款员/合同过滤」；大规模台账仍以QueryService / SQL 投影替换为准。

#### 6.2.11 批量解析就诊草稿支付主键（`findDraftPaymentIdsByEncounterIds`）

- **业务意义**：住院/门诊队列批量挂载 `DRAFT` 支付单，避免 N+1。
- **入参**：就诊 id 集合。
- **出参**：`Map<Long, Long>`（`encounterId → paymentId`）。

#### 6.2.12 批量解析首选支付方式（`mapFirstPayEnumByPaymentIds`）

- **业务意义**：队列/列表展示支付渠道摘要。
- **入参**：支付主键集合。
- **出参**：`Map<Long, String>`（`paymentId → payEnum` code）。

#### 6.2.13 读模型挂载医保结算关联（`attachYbSettlements`）

- **业务意义**：为 `PaymentReconciliationQueryModel` 批量挂载 **§3.4** 关联行，支撑日结费用性质判定与医保对账展示。
- **边界说明**：只读；不写库。
- **查询范围**：`fin_payment_yb_settlement` 按 `reconciliation_id IN (...)` 批量加载。
- **组装规则**：填充 `PaymentReconciliationQueryModel.ybSettlements`；`REVOKED` 行读侧按工具类口径排除于「已确认」集合。
- **返回约束**：无关联行时为空列表。

#### 6.2.14 预览收费员交账未挂载候选（`previewUnsettledPaymentsForCashierSettlement`）

- **业务意义**：按交账类型、经办人与业务日窗预览**尚未挂载** `cashier_settlement_id` 的 payment，供门诊/住院日结 IF 展示候选明细与 `paymentCount`（**AS-OP-FIN-004** IF-OP-DCL-02、**AS-IP-FIN-005** IF-IP-DCL-02）。
- **边界说明**：只读查询，不写库；渠道/病区/费用性质汇总由应用层在候选集之上 enrichment（**BR-OP-DCL-009** / **BR-IP-DCL-005** 仅过滤展示）。
- **入参**：`PreviewCashierSettlementQuery`（`typeEnum`、`cashierId`、`dateFrom`、`dateTo`；字段全集见 **DM-FIN-003** §5.2/§5.3）。
- **出参**：`PreviewCashierSettlementQueryModel`（`items` + `paymentCount`；见 **DM-FIN-003** §4.6.1～4.6.2）。
- **查询范围（读库，无写入）**：`fin_payment_reconciliation` 单表条件下推。
- **过滤口径**（与 **BR-CS-001～006** 一致，写侧 **§6.2.16** 共用）：
  - `is_deleted = false`（**BR-PMR-015**）；
  - `cashier_settlement_id IS NULL`（未交账）；
  - `status_enum IN (SUCCESS, REFUNDED)`；
  - `enterer_id = cashierId`（默认经办人=收费员；代交账由应用 Gate 放行后传入不同 `cashierId`）；
  - `bill_date ∈ [dateFrom 00:00:00, dateTo 23:59:59]`（左闭右闭日窗）；
  - `kind_enum`：`typeEnum=OUTPATIENT` → `OUTPATIENT_CLINIC`；`typeEnum=INPATIENT` → `INPATIENT_CLINIC` + `HOSPITAL_DEPOSIT`。
- **组装规则**：实体映射为 `UnsettledPaymentPreviewItemQueryModel` 列表；`paymentCount = items.size()`；按 `bill_date` 降序。
- **返回约束**：无候选时 `items` 为空、`paymentCount=0`；条件非法抛 `CASHIER_SETTLEMENT_INVALID_ARGUMENT`。

#### 6.2.15 统计收费员交账未挂载条数（`countUnsettledPaymentsForCashierSettlement`）

- **业务意义**：与 **§6.2.14** 相同筛选条件下仅返回条数，供页眉提示或轻量校验。
- **边界说明**：只读；实现可复用 **§6.2.16** 结果集计数。
- **入参/出参**：入参同 **§6.2.14**；出参 `Long`。
- **返回约束**：与预览候选集条数一致。

#### 6.2.16 加载收费员交账未挂载 payment 实体（`findUnsettledPaymentsForCashierSettlement`）

- **业务意义**：与 **§6.2.14** 相同口径返回 `PaymentReconciliation` 实体列表，供 **DM-FIN-003** `submitCashierSettlement` 写侧批量挂载 `cashier_settlement_id`（提交时重新锁定，防篡改）。
- **边界说明**：只读查询；**不**更新 `cashier_settlement_id`；挂载由 `CashierSettlementDomainService` + `PaymentReconciliationRepository#saveAll` 在同一 UoW 完成。
- **查询范围/过滤口径**：与 **§6.2.14** 完全一致。
- **组装规则**：按 `bill_date` 降序返回实体列表。
- **返回约束**：空列表时写侧拒绝提交（**BR-CS-007**）；任一 payment 提交瞬间已被他人挂载 → 整单失败（**BR-CS-008**）。

#### 6.2.17 门诊费用明细 payment 筛 chargeItem（`listOutpatientFeeDetailChargeItemIdsByPaymentFilter`）

- **业务意义**：**AS-OP-FIN-005** IF-OP-FDT-02/03：按 payment **`bill_date` 闭区间**、收费人、收退 Tab 解析候选 **`charge_item_ids` 快照**主键，并同步写入 query 内 payment 读上下文（供 **§6.2.18** 与 ChargeItem 读侧复用）。
- **边界说明**：只读；**不**跨聚合查 ChargeItem 表；时间/收费人/收退方向须在本方法下推（**BR-OP-FDT-003、009**）。
- **入参**：`OutpatientFeeDetailPageQuery`——**必填** `billDateFrom`/`billDateTo`（`LocalDateTime` 日窗）；**可选** `cashierId`（payment `enterer_id`）；**可选** `tradeDirection`（`ALL`/`CHARGE`/`REFUND`，常量 **`OutpatientFeeDetailConstants.TRADE_*`**）。
- **查询范围**：`fin_payment_reconciliation` 单表 + `attachYbSettlements` 读侧挂载。
- **过滤口径**：
  - `is_deleted = false`；
  - `kind_enum = OUTPATIENT_CLINIC`；
  - `status_enum`：`CHARGE` → `SUCCESS`；`REFUND` → `REFUNDED`；`ALL` → 二者并集；
  - `bill_date ∈ [billDateFrom, billDateTo]`（左闭右闭）；
  - `enterer_id = cashierId`（传入时）；
  - 解析 `charge_item_ids` JSON 快照为去重 id 列表。
- **组装规则**：按 `bill_date` **降序**遍历 payment；为每个快照 id 追加一条 `OutpatientFeeDetailChargePaymentLinkQueryModel`（`chargeItemId` + `OutpatientFeeDetailPaymentContextQueryModel`）；**同一 chargeItem 出现在收费与退费两笔 payment 时各保留一行**（收退平账，**BR-OP-FDT-012**）；同步写入 `paymentChargeItemLinks` 与兼容用 `paymentContextByChargeItemId`（后者仅用于快照截断补全，**禁止**作为明细行唯一数据源）。
- **副作用**：写入 `OutpatientFeeDetailPageQuery.paymentChargeItemLinks`、`paymentContextByChargeItemId`。
- **返回约束**：无命中返回空列表且 context map 为空；`billDateFrom > billDateTo` 抛 `IllegalArgumentException`。

#### 6.2.18 门诊费用明细 payment 上下文 enrichment（`mapOutpatientFeeDetailPaymentContextByChargeItemIds`）

- **业务意义**：**AS-OP-FIN-005** IF-OP-FDT-02/03：按 chargeItem 主键批量返回 payment 读上下文（收费人、`bill_date`、退单标记、医保侧合计、快照分母占位），供应用层 **BR-OP-FDT-011** 占比分摊。
- **边界说明**：只读；须与 **§6.2.17** 使用相同 `billDateFrom/To` 预载 `paymentContextByChargeItemId`；`paymentSnapshotAbsAmountTotal` 由 **ChargeItemQueryService** 在加载 scope 内明细后补全（医保占比分母）。
- **入参**：`OutpatientFeeDetailPageQuery query`；`Collection<Long> chargeItemIds`。
- **出参**：`Map<Long, OutpatientFeeDetailPaymentContextQueryModel>`；未命中 id **不包含**在 map 中。
- **组装规则**：若 query 未预载 context，内部调用 **§6.2.17** 后再过滤入参 ids。

#### 6.2.19 工作量 payment 筛 chargeItem（`listPractitionerWorkloadChargeItemIdsByPaymentFilter`）

- **业务意义**：**AS-RPT-001** IF-RPT-WKL-02～04：按 **`bill_date` 闭区间** 与 **`encounterScope`** 对应 payment **`kind_enum`**（门诊 `OUTPATIENT_CLINIC`；住院 `INPATIENT_CLINIC` + `HOSPITAL_DEPOSIT`）解析 **`charge_item_ids` 快照**，写入 `PractitionerWorkloadPageQuery` payment 上下文。
- **边界说明**：只读；状态 **`SUCCESS` / `REFUNDED`**；**无**收退 Tab 变体。
- **入参**：`PractitionerWorkloadPageQuery`——**必填** `billDateFrom`/`billDateTo`、`encounterScope`。
- **返回约束**：无命中返回空列表；与 **§6.2.17** 同构写入 `resolvedPaymentChargeItemLinks`。
- **返回约束**：`chargeItemIds` 为空返回空 map；**禁止**从 `PaymentRecDetail.pay_enum` 推断医保（**BR-OP-FDT-011**）。

#### 6.2.20 住院出院结算净额按就诊映射（`mapInpatientClinicSettledNetAmountByEncounterIds`）

- **业务意义**：**AS-RPT-010**：按 encounterId 批量映射 `INPATIENT_CLINIC` 收退净额（正向 SUCCESS 加、逆向 REFUNDED 减）；净 0 不入 Map；待重结另见 `filterReversedPendingResettleEncounterIds`。
- **边界说明**：只读；仅本聚合 Payment 仓储；金额结算刻度（`MonetaryAmountSupport.scaleSettle`）。
- **入参**：就诊主键集合。
- **出参**：`Map<Long, BigDecimal>`（缺省键=无有效净额）。
- **实现锚点**：`PaymentReconciliationQueryService#mapInpatientClinicSettledNetAmountByEncounterIds`。

### 6.3 领域边界说明

- `PaymentReconciliation` 描述支付主记录、支付明细、医保结算关联与统计快照；不展开医保平台、第三方支付渠道、发票平台协议细节。
- 与 `ChargeItem` 的关系是"连续动作"：支付域确认支付结果后，应用编排推进收费明细状态；不直接替代收费域业务规则。
- 押金、账户余额、就诊主状态属于跨域编排协同，非本聚合单独完成。
- 查询类动作无写库操作，以QueryService表达，遵循 CQRS 分离原则。

---

## 7. 不变式


| 编号         | 不变式                                                           | 说明                                                       |
| ---------- | ------------------------------------------------------------- | -------------------------------------------------------- |
| BR-PMR-001 | 支付主记录必须关联至少一条收费明细（预交金场景除外）                                    | 结算前必须校验收费明细标识集合完整性                                       |
| BR-PMR-002 | 支付状态迁移遵循生命周期约束：DRAFT→SUCCESS→REFUNDING→REFUND_ALL/REFUND_PART | 终态（REFUND_ALL、CANCEL、ERROR）后不得逆向回退                       |
| BR-PMR-003 | 同一支付单下流水明细必须可追溯渠道交易号                                          | 非现金支付的明细必须记录 `payTransNo`                                |
| BR-PMR-004 | 支付成功后必须联动 ChargeItem 状态推进为"已收费"                               | 由应用编排保证连续动作一致性                                           |
| BR-PMR-005 | 退费完成后必须联动 ChargeItem 状态推进                                     | **全退**：拟退行均 `REFUNDED`。**半退（BR-PMR-017）**：拟退行 `REFUNDED`；**未选退行**以新建 `PLANNED` 承接并 `refund_id` 链回（**BR-CHG-015**）。**住院反结**：对支付单快照内源 `SETTLED` 行**复制插入** `PENDING_SETTLEMENT` 再生行（**BR-IP-RVS-011** / **DM-ADM-011** `revertInpatientSettledChargeItems`）；源行保持 `SETTLED`                       |
| BR-PMR-006 | 同一收费明细不得重复收费                                                  | 结算前按就诊已成功支付记录做幂等校验                                       |
| BR-PMR-007 | 退款单必须通过 `relationId` 追溯原支付单                                   | 退款单的 `relationId` 不允许为空                                  |
| BR-PMR-008 | 同一原支付单不得重复发起退款                                                | 发起退费前：原单悲观锁 + 未删有效逆向（`REFUND_PROCESSING`/`REFUNDED`）判重；库部分唯一索引 `uk_fin_payment_rec_active_unpay_relation` 兜底并发双写 |
| BR-PMR-009 | `PaymentRecDetail.reconciliationId` 必须指向同聚合主记录                | 不允许孤儿明细                                                  |
| BR-PMR-010 | 明细结果与主状态需收敛一致                                                 | 支付成功后明细结果应为 PAID                                         |
| BR-PMR-011 | 退款冲正场景通过新明细/新支付单表达                                            | 不在原明细上做不可追踪覆盖                                            |
| BR-PMR-012 | 写前校验必须先读取支付主状态                                                | 禁止直接按 ID 盲更新，防止并发状态覆盖                                    |
| BR-PMR-013 | 读支付明细时必须按 `reconciliationId` 聚合查询                             | 保证主从口径一致                                                 |
| BR-PMR-014 | 应收金额不允许为负数（退费方向通过 PaymentType 区分）                             | 金额层面校验                                                   |
| BR-PMR-015 | 软删过滤作为默认查询更新口径                                                | 默认仅查询/更新未逻辑删除数据，遵循 `BaseAuditedEntity` 对 `isDeleted` 的约定 |
| BR-PMR-016 | 凡新建支付主记录落库前必须具备 `print_count`、`yb_clear_flag` 等持久化非空字段的明确取值 | 与草稿创建共用 `initDraft` 再按需覆盖状态，避免逆向单路径遗漏导致约束违反 |
| BR-PMR-017 | 门诊**产品「半退」**编排（按 payment 粒度）                                | 对某一 `paymentId`：若用户所选拟退行 **未覆盖** 该支付在 `charge_item_ids` 中的**全集**，则判定为半退——**资金侧对该 payment 执行整单冲退**（与逆向退款单、`BR-PMR-011` 一致）；同时对**未选退**的收费明细执行 **ChargeItem 重开**（`PLANNED` 新行 + `refund_id` 指向原行，**BR-CHG-015**），再接续预结算/结算。**全退**：所选行覆盖该 payment 快照全集，无重开保留项。多 `paymentId` 在同一操作中**按笔分别**判定与编排 |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文  | 聚合           | 关联方式            | 说明          |
| ------ | ------------ | --------------- | ----------- |
| 患者管理   | Patient      | `patientId`     | 支付流水归属的患者   |
| 就诊管理   | Encounter    | `encounterId`   | 支付流水关联的就诊   |
| 就诊账户管理 | Account      | `accountId`     | 支付按账户维度分摊   |
| 收费明细管理 | ChargeItem   | `chargeItemIds` | 支付作用的收费明细快照 |
| 票据管理   | Invoice      | `invoiceId`     | 支付完成后关联发票   |
| 组织架构   | Practitioner | `entererId`     | 操作经办人       |
| 位置管理   | Location     | `locationId`    | 支付窗口/终端     |


### 8.2 下游影响


| 下游上下文  | 聚合          | 触发方式 | 说明                                          |
| ------ | ----------- | ---- | ------------------------------------------- |
| 收费明细管理 | ChargeItem  | 连续动作 | 支付成功后推进 ChargeItem 为 BILLED，退费后推进为 REFUNDED |
| 票据管理   | Invoice     | 事件订阅 | 监听 `OutpatientPaymentSettledEvent`，触发生成发票   |
| 就诊管理   | Encounter   | **应用编排** + **事件订阅（QueryService）** | **门诊**：写模型 `adm_encounter.status_enum` 由应用在同一用例中调用 `completeOutpatientRegistration`（`PLANNED`→`REGISTERED`），成功后发布 **`OutpatientRegistrationConfirmedEvent`** 驱动挂号相关QueryService就诊展示状态；`OutpatientPaymentSettledEvent` **仅**收银语义，可驱动挂号列表等 **金额摘要**（见 §5.1.1）。**住院**：可监听 `InpatientPaymentSettledEvent` 等联动住院就诊状态（以住院用例契约为准）。 |
| 对账域    | **CashierSettlement**（**DM-FIN-003**） | 应用编排 + 事件 | 收费员交账挂载 `cashier_settlement_id`；候选预览/写侧锁定经本聚合 **§6.2.14～6.2.16**；监听 `CashierSettlementSubmittedEvent` |
| 医保域    | Claim（医保申报） | 事件订阅 | 监听结算事件，同步医保结算结果                             |


### 8.3 引用规则

> **统一规则**：所有需要引用就诊的领域模型，遵循以下原则：
>
> - 门诊场景：引用 `encounterId`，住院就诊标识为空
> - 住院场景：同时引用 `encounterId` 和住院就诊标识
> - 患者引用统一使用 `patientId`

### 8.4 事件流示意图

```
ChargeItem            Account                 PaymentReconciliation           Invoice            Encounter
    │                    │                           │                          │                    │
    │                    │──AccountSettled──────────>│                          │                    │
    │                    │                           │──createPaymentDraft     │                    │
    │                    │                           │  (PaymentDraftCreated)   │                    │
    │                    │                           │                          │                    │
    │                    │                           │──settleOutpatient        │                    │
    │                    │                           │  (OutpatientPaymentSettled)────────>│──生成发票           │
    │                    │                           │  （挂号列表等：可选订阅→金额摘要；不含就诊状态）              │                    │<──应用编排 completeOutpatientRegistration（PLANNED→REGISTERED）
    │                    │                           │                          │                    │──OutpatientRegistrationConfirmedEvent→QueryService REGISTERED 投影
    │<──推进 BILLED──────│                           │                          │                    │
    │                    │                           │                          │                    │
    │                    │                           │──settleInpatient         │                    │
    │                    │                           │  (InpatientSettled)──────>│──生成发票           │
    │<──推进 BILLED──────│                           │                          │                    │──联动住院状态
    │                    │                           │                          │                    │
    │──发起退费──────────>│                           │──requestRefund           │                    │
    │                    │                           │  (RefundRequested)       │                    │
    │<──推进 REFUNDED────│                           │──completeRefund          │                    │
    │                    │                           │  (RefundCompleted)──────>│──红冲发票           │
```

---

## 9. 补充

### 9.1 预交金收退说明（对齐 AS-IP-FIN-001 / AS-IP-FIN-002）

住院预交金**收取与退还**共用「落支付事实 → 住院结算收口」节奏，与登记首笔预交金、预交金工作台续收/退费一致：

| 步骤 | 领域方法 | 支付主记录状态 | 领域事件 |
| --- | --- | --- | --- |
| 1 | `collectAdvanceDeposit` 或 `refundAdvanceDeposit` | `DRAFT` | `AdvanceDepositCollectedEvent` / `AdvanceDepositRefundedEvent` |
| 2 | `settleInpatientPayment` | `SUCCESS` | `InpatientPaymentSettledEvent` |
| 3（应用编排） | Account `saveOrUpdateAccount` | — | `AccountBalanceEditedEvent` |

- 收取：`payment_enum = PAY`；退还：`payment_enum = UN_PAY`，金额为**正数**，**无** `relation_id`
- `kind_enum = HOSPITAL_DEPOSIT`；`charge_item_ids` 为空
- 明细账户标识指向就诊下**个人现金账户**
- 账户余额变更**不在**支付聚合内完成，由应用在 **步骤 2 成功后**增减余额

### 9.2 与 ChargeItem 的连续动作边界


| 触发场景          | 协同约束                         | 目的          |
| ------------- | ---------------------------- | ----------- |
| 支付成功（SUCCESS） | 应用编排推进 ChargeItem 为 BILLED   | 保证支付与收费状态一致 |
| 门诊退费完成（全额退）   | 应用编排推进 ChargeItem 为 REFUNDED | 保证退费链路闭环    |
| 门诊退费完成（部分退）   | 部分退费后重新预结算剩余收费明细             | 避免旧支付草稿继续使用 |
| 住院反结        | 应用编排调用 ADM-011 `revertInpatientSettledChargeItems`：源 `SETTLED` 不变，插入 `PENDING_SETTLEMENT` 再生行 | 费用回到待再次结算；历史快照仍可查源行 |


> PaymentReconciliation 只描述支付主记录与支付明细子表，不展开医保平台协议细节。与 ChargeItem 的关系是"连续动作"：支付域确认支付结果后，应用编排推进收费明细状态；不直接替代收费域业务规则。

### 9.3 第三方银联 BPC 收单（扩展 Module）

> **模块真源**：`docs/模块/zxplat-module-unionpay-bpc/` · **编排真源**：[ADR-PAY-001 §9](../../模块/zxplat-module-unionpay-bpc/01-概要设计.md) · **settle DTO**：[03 §3.10](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md#310-settle-挂接-dto定稿-2026-06-23)

| 维度 | health（DM-FIN-002） | unionpay-bpc |
| --- | --- | --- |
| 支付主从表 | `fin_payment_reconciliation` / `fin_payment_rec_detail` | — |
| BPC 原值 | — | `upc_bpc_order.pay_type` = WEIX/ZFBA/UPAY |
| HIS 扫码渠道 | `pay_enum` **WECHAT/ALIPAY/BANK_CARD**（银联被扫：`BpcPayTypeMapper`） | adapter 内映射 |
| 手工微信/支付宝 | `pay_enum` **WECHAT/ALIPAY**（固定码；**无** `upc_bpc_order`） | health settle 直接写 |
| 手工现金 | `pay_enum` **CASH** | 不经 BPC |
| 第三方订单 | — | `upc_bpc_order`（merTradeNo、tradeNo、status） |
| 挂接 | settle/refund IF 携带 `clientScanResult`/`clientRefundResult`；AppService 内 **`ThirdPartyPaymentExtension`** | adapter `@Primary` 实现 |

**P0 编排时序**（挂号 IF-REG-07、门诊收费 IF-OPC-07 共用；**通道 C · OpenHIS.Win**）：

```text
预结算 → payment 草稿（DRAFT）
  → （医保）CHS confirm 成功
  → 若 scanPayAmount>0 且 Extension.isEnabled()
       → prepareConsumerScan → Win consumer-scan
       → Win 失败：不 settle
  → settle（正式结算）
       → paymentDetails 扫码行 payChannelCode=SCAN_PAY
       → 顶层 clientScanResult（Win 回传）
       → AppService 内 applyClientScanResult → 行替换 WECHAT|ALIPAY|BANK_CARD
       → upc_bpc_order.pay_type 存 BPC 原值
  → 若 wechatAmount/alipayAmount>0（固定码手工）
       → 直接写 PaymentRecDetail(WECHAT|ALIPAY)；不调 Extension
  → settle 失败且 BPC 已成功
       → retrySettleFromBpcOrder（**重传完整 clientScanResult**，禁止重复 C）
```

- BPC **失败**时不 settle；payment 草稿保留，允许换现金或重扫。
- **禁止**在 zxplat-domain 增加 BPC 专有列；posNo 默认 `contract.*.pos_no`（= tid）。
- 门诊退费原路退：**P0** 先 Win refund（金额=原扫码段 `txnAmtFen`）→ 退费 IF 顶层 **`clientRefundResult`** → AppService 内 **`applyClientRefundResult`**（等额门禁 + 已 REFUNDED 幂等）→ 本地退费；半成功（银联已退）本页带凭证重试；见 **AS-OP-FIN-003 §8.1**。

### 9.4 文档变更历史

| 版本 | 日期 | 说明 |
| ---- | ---- | ---- |
| v1.4.16 | 2026-10-11 | 新增 §6.1.13a `softDeleteAdvanceDepositDraft`：意向/作废预交金草稿软删；登记取消/重建与预交金页清理对齐（代码先行回写） |
| v1.4.15 | 2026-10-11 | §6.1.13：意向预交金允许无明细 DRAFT；新增 §6.2.2a 住院预交金草稿读侧（门诊转住院 / 登记回显与取消作废） |
| v1.4.14 | 2026-08-05 | §6.1.5 / **BR-PMR-008**：门诊/住院逆向 UN_PAY 并发防重（原单锁 + 部分唯一索引）；对齐 **AS-OP-FIN-003 v1.0.18** |
| v1.4.13 | 2026-08-01 | §9.3：扫码原路退金额=txnAmtFen；等额/幂等/半成功收口；对齐 **AS-OP-FIN-003 v1.0.15** |
| v1.4.12 | 2026-07-29 | §6.1.5：`requestOutpatientRefund` 明确须带 `contractNo` + 原路 `paymentDetails`；§6.1.8 退号收口前置依赖该明细（对齐 **AS-OP-FIN-001 IF-REG-10** / **BR-REF-011**） |
| v1.4.11 | 2026-07-27 | 新增 **§6.2.20** 住院出院结算净额按就诊映射（**AS-RPT-010**） |
| v1.4.10 | 2026-07-03 | 新增 **§6.2.19** 工作量 payment 筛 chargeItem（**AS-RPT-001**） |
| v1.4.9 | 2026-06-23 | §4.7/§9.3 对齐 ADR-PAY-001 语义码；settle/refund clientResult 挂接 |
| v1.4.8 | 2026-06-23 | §9.3 同步 payType 双存、P0 退费同事务、半成功补偿、CHS→BPC 顺序 |
| v1.4.7 | 2026-06-22 | 新增 **§9.3** 银联 BPC 扩展挂接与双存储分工（unionpay-bpc） |
| v1.4.6 | 2026-06-21 | 新增 **§6.2.17～6.2.18** 门诊费用明细 payment 读侧扩展（**AS-OP-FIN-005** 关闭 **GAP-OP-FDT-002**） |
| v1.4.5 | 2026-06-21 | 新增 **§6.2.14～6.2.16** 收费员交账未挂载 payment 读侧扩展（对齐 **DM-FIN-003** **BR-CS-001～006** 与现行 `PaymentReconciliationQueryService`）；§8.2 对账域下游补充候选读落点 |
| v1.4.4 | 2026-06-21 | 移除主表 `yb_settle_ids`；新增 **§3.4 PaymentYbSettlement** / `fin_payment_yb_settlement`；§3.2/§4.7 明确 rec_detail 仅业务自付渠道；§6.2.13 读侧挂载 |
| v1.4.3 | 2026-06-21 | §3.1 交账挂载对齐 **DM-FIN-003 v2.0**：保留 `cashier_settlement_id`，移除 `handed_over_at`；逻辑关联 `fin_cashier_settlement` |
| v1.4.2 | 2026-06-19 | §3.1 新增 `cashier_settlement_id`、`handed_over_at`（交账挂载，见 **DM-FIN-003**）；§8.2 对账域下游改为 CashierDailySettlement |
| v1.3 | 2026-05-09 | §5.1/§5.1.1/§6.1.2/§8.2/§8.4：`OutpatientPaymentSettledEvent` **移除**就诊状态载荷；`REGISTERED` 读侧改由 **`OutpatientRegistrationConfirmedEvent`**（`DM-ADM-002`）驱动；支付事件仅保留收银与挂号列表金额摘要语义。 |
| v1.4.1 | 2026-05-28 | **M1**：§6.2.11 `findDraftPaymentIdsByEncounterIds`、§6.2.12 `mapFirstPayEnumByPaymentIds` |
| v1.4 | 2026-05-19 | §6.1.13/§6.1.14/§9.1/§5.2：预交金收/退均为 **DRAFT→settleInpatientPayment**；新增 `refundAdvanceDeposit` 与 `AdvanceDepositRefundedEvent`（不绑原单）。 |
| v1.2 | 2026-05-02 | §5.1 `OutpatientPaymentSettledEvent` 载荷补充 `encounterStatusEnum` 字段（由 `PaymentReconciliationDomainService` 在挂号结算场景显式写入 `REGISTERED`，QueryService从事件读取，禁止硬编码）；§5.1.1 更新领域服务侧赋值说明与领域事件后查询刷新契约。（**已由 v1.3 废止**：状态迁移事件归属就诊域。） |
| v1.1 | 2026-05-02 | 补充 `OutpatientPaymentSettledEvent` 与 Encounter 写模型、`completeOutpatientRegistration` 应用编排及挂号/门诊领域事件后查询刷新协同（§5.1.1、§6.1.2、§8.2、§8.4）。 |
| v1.0 | （既有） | 初稿。 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PaymentReconciliationDomainService`

| 方法 |
|------|
| `createPaymentDraftFromPreSettle` |
| `refreshPaymentDraftPreSettleAmounts` |
| `backfillPaymentContractNoIfBlank` |
| `settleOutpatientPayment` |
| `settleInpatientPayment` |
| `requestOutpatientRefund` |
| `completeOutpatientRefund` |
| `abortOutpatientRefundAttempt` |
| `completeRegistrationCancelRefund` |
| `cancelInpatientSettleAsRefund` |
| `completeInpatientSettleCancelRefund` |
| `updatePaymentStatusWithSettleIds` |
| `abortPreSettlementBeforePayment` |
| `refreshRegistrationPaymentDraft` |
| `closeSinglePaymentResult` |
| `finalizePaymentDetailReceipt` |
| `collectAdvanceDeposit` |
| `softDeleteAdvanceDepositDraft` |
| `refundAdvanceDeposit` |
| `replacePaymentRecStatics` |
| `markYbSettlementsRevoked` |
| `softDeleteInsuranceFundAcctDetailsBySetlId` |

### QueryService：`PaymentReconciliationQueryService`

| 方法 |
|------|
| `mustGetPayment` |
| `findOptionalPaymentEntityById` |
| `listPaymentEntitiesByBillDateBetween` |
| `findPaymentsByEncounter` |
| `findLatestHospitalDepositDraftByEncounter` |
| `listHospitalDepositDraftsByEncounter` |
| `findPaymentsByEncounterIds` |
| `findPaySuccessOrRefundingByEncounter` |
| `findPaySuccessOrRefundingOverlappingChargeItems` |
| `findDraftPaymentIdsByEncounterIds` |
| `findLatestSuccessPaymentIdsByEncounterIds` |
| `findSuccessPaymentIdsOverlappingChargeItemsByEncounter` |
| `listSuccessPaymentsByEncounterId` |
| `mapEntererIdByPaymentIds` |
| `hasRefundByRelationId` |
| `findReturnPayment` |
| `findPaymentByBillDateRange` |
| `findPaymentByBillDateWindow` |
| `previewUnsettledPaymentsForCashierSettlement` |
| `countUnsettledPaymentsForCashierSettlement` |
| `findUnsettledPaymentsForCashierSettlement` |
| `findPaymentsForCashierSettlementSummary` |
| `previewPaymentsForCashierSettlementSummary` |
| `findPaymentQueryModelsForCashierSettlementSummary` |
| `loadPaymentsForCashierSettlementSummary` |
| `findPaymentQueryModelsByCashierSettlementId` |
| `previewPaymentsByCashierSettlementId` |
| `loadMountedPaymentsByCashierSettlementId` |
| `listDistinctEntererIdsInCashierSettlementWindow` |
| `findEarliestUnsettledPaymentBillTimeBeforeWindow` |
| `countUnsettledPaymentsAfterWindow` |
| `findDraftPaymentByPatient` |
| `mapFirstPayEnumByPaymentIds` |
| `mapDistinctPayEnumCodesByPaymentIds` |
| `listPaymentRecDetailsByPaymentId` |
| `mapPaymentRecDetailsByPaymentIds` |
| `findPaymentQueryModelsByIds` |
| `mapPaymentRecStaticsByReconciliationIds` |
| `getChargeItemIdsByPayments` |
| `queryPaymentAccountContract` |
| `filterReversedPendingResettleEncounterIds` |
| `listOutpatientFeeDetailChargeItemIdsByPaymentFilter` |
| `listInpatientFeeDetailChargeItemIdsByPaymentFilter` |
| `listPractitionerWorkloadChargeItemIdsByPaymentFilter` |
| `mapOutpatientFeeDetailPaymentContextByChargeItemIds` |
| `mapInpatientClinicSettledNetAmountByEncounterIds` |

