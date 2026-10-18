# DM-ADM-012 Invoice 票据发票

> **限界上下文**：费用域 · 票据发票管理  
> **聚合类型**：单表主档聚合（当前实现口径）  
> **版本**：v1.0.2 · **日期**：2026-07-16

---

## 1. 概述

### 1.1 业务定义

票据发票（Invoice）是结算完成后生成的财务凭证，是收费全链路的最终闭环锚点。它回答的核心业务问题是："**患者被收取的费用以什么形式出具了法律凭证？**"——在 HIS 系统中，Invoice 是门诊收费票据、住院结算发票、退费冲红的统一表达。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 票面事实管理 | 记录发票编号、金额、开票人、票据类型等票面要素 |
| 电子票据管理 | 管理电子发票的二维码、图片地址、卡包链接等数字化票面要素 |
| 红冲追溯 | 原票作废与红票成对保留，确保退费场景下票据链路完整可追溯 |
| 费用项关联 | 通过费用项 ID 集合关联 ChargeItem 收费明细 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊收费票据 | 门诊每次收费后生成一张或多张收费票据，如挂号费票据、诊间收费票据 |
| 住院结算发票 | 出院结算后生成一张汇总发票，如住院费用结算发票 |
| 退费冲红 | 退费时原票红冲作废，同时生成红票，如门诊退费冲红、住院退费冲红 |
| 电子票据 | 对接第三方电子票据平台开具电子发票，如财政电子票据、税务电子发票 |

### 1.4 与 Account / ChargeItem 的关系

> **Account（就诊账户）**：结算完成后发布 `AccountSettled` 事件，触发 Invoice 生成。Invoice 通过 `付款单标识(reconciliationId)` 间接关联 Account。  
> **ChargeItem（收费明细）**：Invoice 通过 `charge_item_ids` 关联收费明细主键集合，当前以逗号分隔字符串表达；明细本体见 `DM-ADM-011`。ChargeItem 是收费最小粒度，Invoice 是汇总凭证。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表 `adm_invoice`，票面要素与电子票据扩展字段均落在主表；聚合边界以主表主键 `id`（票据标识）划分。当前无独立子实体表。

**表结构**：

- 主表：`adm_invoice`（主键：`id`）
- 索引建议：`patient_id`（患者）、`reconciliation_id`（付款单）、`status_enum`（票据状态）、`type_code`（蓝字/红字）、`bus_no`（院内发票编号唯一）、`bill_bus_date`（业务日期查询）

**高频查询模式**：

- 按票据主键：主键查询
- 按付款单回查票据：`reconciliation_id` 索引扫描
- 按患者列历史票据：`patient_id` 索引扫描
- 列表/报表：优先QueryService（与 `BR-INV-QRY-*` 一致）

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 票据标识 | `InvoiceId` / `Long` | 全局唯一标识，系统生成（雪花 ID），对应主键 `id` |

### 2.3 状态机

```
  [草稿] ──开票──> [已开具] ──对账确认──> [已结清]
    │                │
    │                └──红冲/作废──> [已作废]
    │
    └──作废──> [已作废]

  任意非终态 ──错误修正──> [填入错误]
```

> **当前主路径**：`草稿(DRAFT) → 已开具(ISSUED) → 已作废(CANCELLED)`；已结清(BALANCED) 和 填入错误(ERROR) 为枚举保留，当前主流程未落地。

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | draft | 草稿 | 结算/支付后生成票据草稿，尚未正式开票，允许开票、作废 |
| 2 | issued | 已开具 | 正式开票完成（纸质打印或电子开具），允许对账确认、红冲/作废 |
| 3 | balanced | 已结清 | 票据金额与支付对账一致，收支平衡确认 | — |
| 4 | cancelled | 已作废 | 票据已作废（含被红冲的原票） | — |
| 5 | entered-in-error | 填入错误 | 数据录入错误，非正常业务终态 | — |

> **状态枚举说明**：Invoice 状态枚举与 FHIR R5 `Invoice.status` 保持一致，落库枚举类为 `InvoiceStatus`，以 `Integer` 编码值（`@EnumValue`）存储于 `status_enum` 列。

---

## 3. 实体

```text
Invoice（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                     Invoice                          │
  │                                                      │
  │  - 患者标识 / 付款单标识 / 票据类型 / 票据状态       │
  │  - 发票编号 / 总净额 / 总含税额 / 开票人标识         │
  │  - 电子票据要素（扁平字段） / 票据业务日期           │
  │  - 关联费用项ID集合 / 作废原因 / 支付条款            │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

> **当前实现口径**：Invoice 为单表主档聚合（`adm_invoice`），无子实体表。

### 3.1 票据发票（Invoice）

Invoice 聚合根的属性定义，对应 `adm_invoice` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 票据标识 | `id` | `InvoiceId` | 是 | 主键，雪花 ID |
| 患者标识 | `patient_id` | `PatientId` | 是 | 关联患者聚合根 |
| 付款单标识 | `reconciliation_id` | `PaymentReconciliationId` | 否 | 关联结算/付款单 |
| 票据状态 | `status_enum` | `InvoiceStatus`（值对象） | 是 | 见 §2.3 状态机 |
| 票据类型 | `type_code` | `InvoiceType`（值对象） | 否 | 见 §4.1；以字符串存储 |
| 作废原因 | `cancelled_reason` | `String` | 否 | 作废/红冲时必填 |
| 关联费用项ID集合 | `charge_item_ids` | `String` | 否 | 关联 ChargeItem ID 集合，逗号分隔字符串 |
| 总净额 | `total_net` | `BigDecimal` | 是 | 发票净额 |
| 总含税额 | `total_gross` | `BigDecimal` | 是 | 发票含税总金额 |
| 支付条款 | `payment_terms` | `String` | 否 | 支付条款/付款详情说明 |
| 发票编号 | `bus_no` | `String` | 否 | 院内发票业务编号 |
| 开票人标识 | `invoicing_staff_id` | `OperatorId` | 否 | 开票操作人 |
| 票据业务日期 | `bill_bus_date` | `DateTime` | 否 | 票据所属业务日期 |
| 电子票据要素 | — | `ElectronicInvoiceInfo`（值对象） | 否 | 见 §4.3；以扁平字段存储于主表 |
> 通用审计字段（`createBy/createTime/updateBy/updateTime`）、租户字段（`tenantId`）、逻辑删除字段（`deleteFlag`）继承自 `HisBaseEntity`，遵循统一约定。

### 3.2 子实体

> 本聚合为单表聚合，无聚合内子实体行。

---

## 4. 值对象 / 枚举

### 4.1 票据类型（InvoiceType）

| 枚举值 | code 值 | 类型描述 | 说明 |
|---|---|---|---|
| 蓝字（开具） | `0` | 蓝字 | 正常开具的发票 |
| 红字（冲销） | `1` | 红字 | 冲销/红冲发票 |

> **存储口径**：枚举类 `InvoiceType`，对应主表字段 `type_code`，以 **String** 存储（`0`/`1`）。

### 4.2 票据状态（InvoiceStatus）

见 §2.3 状态定义中的枚举表。

### 4.3 电子票据要素（ElectronicInvoiceInfo）

> 主数据以扁平字段存储于 `adm_invoice` 主表，本稿为建模清晰性归纳为值对象。

| 属性 | 类型 | 持久化字段 | 说明 |
|---|---|---|---|
| 电子票据代码 | `String` | `billBatchCode` | 电子票据批次/代码 |
| 电子票据号码 | `String` | `billNo` | 电子票据号码 |
| 随机码 | `String` | `random` | 电子校验码/防伪随机码 |
| 电子票据生成时间 | `String` | `billCreateTime` | 电子票据平台返回的生成时间（字符串） |
| 二维码 | `String` | `billQrCode` | 电子票据二维码图片数据 |
| 票据图片地址 | `String` | `pictureUrl` | 电子票据 H5 页面 URL |
| 网络图片地址 | `String` | `pictureNetUrl` | 电子票据外网 H5 页面 URL |
| 微信卡包地址 | `String` | `wxCardUrl` | 微信插卡 URL |

### 4.4 票据介质（InvoiceMedium）

> 作为业务字典口径保留，当前不新增独立实体。

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 纸质票据 | `paper` | 传统纸质打印票据 |
| 电子票据 | `electronic` | 财政/税务电子票据 |
| 纸电同开 | `paper-and-electronic` | 纸质与电子同时开具 |

### 4.5 金额口径（Money）

> 作为金额值对象语义口径保留；当前持久化字段仍以 `BigDecimal` 直接存储。

| 属性 | 类型 | 说明 |
|---|---|---|
| 金额值 | `Decimal` | 数值，精确到分 |
| 币种 | `String` | 默认 CNY（人民币） |

### 4.6 费用类别（ChargeCategory）

> 作为业务字典口径保留，供票面汇总与报表分类统一语义。

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 挂号费 | `registration` | 挂号/诊查费 |
| 药品费 | `medication` | 西药、中成药、中药饮片 |
| 检查费 | `examination` | CT、MRI、超声、X光等 |
| 检验费 | `laboratory` | 血常规、生化检验等 |
| 治疗费 | `treatment` | 诊疗服务费 |
| 手术费 | `operation` | 手术相关费用 |
| 护理费 | `nursing` | 护理服务费 |
| 材料费 | `material` | 耗材、器材费用 |
| 床位费 | `bed` | 住院床位费 |
| 其他 | `other` | 其他费用 |

## 5. 领域事件

### 5.1 事件定义

| 编号 | 事件名称 | 触发时机 | 携带数据 | 消费方 | 版本 |
|---|---|---|---|---|---|
| 5.1.1 | `InvoiceDraftCreatedEvent` | 结算/支付后生成票据草稿 | 票据标识、患者标识、付款单标识、票据类型、总金额 | 对账、QueryService | v1 |
| 5.1.2 | `InvoiceIssuedEvent` | 正式开票完成 | 票据标识、发票编号、开票时间 | 对账、通知、QueryService | v1 |
| 5.1.3 | `InvoiceCancelledEvent` | 票据作废/红冲 | 票据标识、作废原因、操作人标识 | 对账、QueryService | v1 |

> **事件载荷补充（实现对齐）**：`InvoiceIssuedEvent` 除聚合标识外，额外携带 `patientId`、`reconciliationId`、`billNo`、`billBatchCode`、`billCreateTime`、`typeCode`，其中 `billCreateTime` 对应电子票据生成时间（作为“开票时间”口径之一，取第三方票据平台返回值）。

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 创建草稿 | 草稿 | `InvoiceDraftCreatedEvent` |
| 草稿 | 开票 | 已开具 | `InvoiceIssuedEvent` |
| 已开具 | 红冲/作废 | 已作废 | `InvoiceCancelledEvent` |
| 草稿 | 作废 | 已作废 | `InvoiceCancelledEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（InvoiceDomainService）

#### 6.1.1 支付后生成发票草稿（`createDraftInvoiceAfterPayment`）
- **业务意义**：支付成功后沉淀票据主档草稿。
- **边界说明**：只写票据主档，不处理第三方电子票据平台交互与号段分配。
- **入参**：命令载荷含患者标识、付款单标识、金额与关联 `charge_item_ids` 等（以落库与交互契约为准）。
- **出参**：新建票据主键。
- **前置条件**：患者与付款单引用有效；满足 `BR-INV-005`、`BR-INV-006` 等业务前置（由应用编排保证）；**应用层**须先满足 `BR-INV-008`（票面金额 > 0，收费项合计，非现金 `displayAmount`）再调用本方法。
- **状态变化**：`N/A -> DRAFT`（编码值见 §2.3）。
- **聚合内影响**：建立 `reconciliation_id` 绑定；落 `charge_item_ids` 与金额字段。
- **事件输出**：`InvoiceDraftCreatedEvent`。
- **失败语义**：必填项缺失拒绝；违反唯一/防重规则时拒绝或幂等返回（与 `BR-INV-006` 一致）。

#### 6.1.2 电子开票（`issueElectronicInvoice`）
- **业务意义**：将草稿票据开具为正式电子票据（回填票面要素）。
- **边界说明**：领域层落库票面事实；HTTP 调用票据平台在应用层完成后再调用本方法或同事务内的编排以本方法收口状态。
- **入参**：命令载荷含票据主键及待回填的电子票据要素（若由应用层已拉取）。
- **出参**：更新后的票据实体或成功标记。
- **前置条件**：当前状态为 `DRAFT`（`BR-INV-001`）。
- **状态变化**：`DRAFT -> ISSUED`。
- **聚合内影响**：补全 `bus_no`/电子票据扁平字段等；满足 `BR-INV-007` 时校验关键要素。
- **事件输出**：`InvoiceIssuedEvent`。
- **失败语义**：非 `DRAFT` 拒绝；票面要素不满足 `BR-INV-007` 拒绝。

#### 6.1.3 红冲并生成红票（`writeoffInvoiceAndCreateRedRecord`）
- **业务意义**：对已开票据执行红冲并生成红字记录。
- **边界说明**：退款支付流水、医保冲正不在本动作内展开。
- **入参**：命令载荷含原票主键、作废原因、红票侧付款单/金额绑定等（以落库与交互契约为准）。
- **出参**：原票更新结果及红票新行主键。
- **前置条件**：原票为 `ISSUED`；作废原因非空（`BR-INV-002`）。
- **状态变化**：原票 `ISSUED -> CANCELLED`；**新增**红票行（`type_code` 红字，见 `BR-INV-003`）。
- **聚合内影响**：原票写 `cancelled_reason`；红票关联退款付款单。
- **事件输出**：`InvoiceCancelledEvent`（原票/红票事件细分以实现与契约为准）。
- **失败语义**：原票非 `ISSUED` 或重复红冲拒绝。

#### 6.1.4 作废草稿票据（`voidDraftInvoice`）
- **业务意义**：作废未开具的草稿票据。
- **边界说明**：不涉及已开具票据的红冲路径。
- **入参**：命令载荷含票据主键、作废原因。
- **出参**：成功/失败结果。
- **前置条件**：当前状态为 `DRAFT`；作废原因非空（`BR-INV-002`）。
- **状态变化**：`DRAFT -> CANCELLED`。
- **聚合内影响**：记录 `cancelled_reason`。
- **事件输出**：`InvoiceCancelledEvent`。
- **失败语义**：非 `DRAFT` 拒绝。

### 6.2 读侧查询服务（InvoiceQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService。

#### 6.2.1 按主键读票据主档（`getInvoiceById`）
- **业务意义**：加载票据详情用于稽核与回显。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_invoice` 主表或只读副本。
- **过滤口径**：主键 `id`。
- **组装规则**：单实体直出。
- **返回约束**：未命中返回空/`null` 以实现约定为准；不写业务状态（`BR-INV-QRY-001`）。

#### 6.2.2 按付款单查询发票（`getInvoiceByPaymentId`）
- **业务意义**：按付款单回查关联票据。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：按 `reconciliation_id` 过滤。
- **过滤口径**：付款单标识；若多条按业务取最新或列表以落库与交互契约为准。
- **组装规则**：单条或QueryService 列表查询。
- **返回约束**：只读 DTO/实体。

#### 6.2.3 支付详情附带票据（`getLatestIssuedInvoiceByPaymentId`）
- **业务意义**：在支付详情展示当前付款单下最新已开具票据。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：同 6.2.2，限定 `status_enum = ISSUED`。
- **过滤口径**：遵循 `BR-INV-QRY-002`，按开票时间或主键降序取一条。
- **组装规则**：单条「最新已开具」记录。
- **返回约束**：无已开具记录时返回空；不修改票据状态。

#### 查询与QueryService规则

| 编号 | 规则ID | 规则描述 | 约束/落地要点 |
|---|---|---|---|
| R-01 | `BR-INV-QRY-001` | 票据主档查询为只读 | 不在查询路径写业务状态 |
| R-02 | `BR-INV-QRY-002` | 支付详情展示票面时取「当前付款单下最新已开具」记录 | 与现有收费/支付详情实现口径一致 |

### 6.3 领域边界说明

- `Invoice` 只表达票据主档事实；结算、退费、医保冲正等跨域动作由应用服务显式编排。
- 不把支付对账、号段管理、第三方电子票据 HTTP 调用等写入票据领域动作；跨域步骤由对应聚合文档与应用层编排说明。
- 查询类动作无写库操作；列表与大报表优先QueryService，遵循 CQRS 分离原则。
- **实现落点**：写侧 `InvoiceDomainService`，读侧 `InvoiceQueryService`，实体与仓储位于 `administration` 领域包（与院内工程划分一致）。

---

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| `BR-INV-001` | 状态值语义以数据库注释为准，写入必须遵循状态机推进 | 现行主路径：`DRAFT → ISSUED → CANCELLED`；终态（`CANCELLED`、`ERROR`、`BALANCED`）后不可逆向回退 |
| `BR-INV-002` | 作废必须可追溯：记录 `cancelled_reason` | 红冲/作废动作须落原因字段，非空校验 |
| `BR-INV-003` | 红冲后需保留原票与红票两条记录 | 原票置 `CANCELLED`，红票新增（`type_code = 1` 红字）并绑定退款付款单 |
| `BR-INV-004` | 发票编号（`bus_no`）在同一机构内唯一 | 号段管理由应用层保证，领域层校验唯一性 |
| `BR-INV-005` | 每张票据必须关联有效的患者 | `patient_id` 非空校验 |
| `BR-INV-006` | 同一付款单下不允许存在多张有效（非作废）的同类型票据 | 防止重复开票 |
| `BR-INV-007` | 电子票据开具后必须包含关键票面要素 | 电子票据要素中至少包含二维码（`bill_qr_code`）和票据图片地址（`picture_url`） |
| `BR-INV-008` | **票面金额 > 0 方创建票据** | 开票闸门取**收费项合计票面金额**（结算刻度），**不是**患者现金应付 `displayAmount`；医保基金/个账分担后现金为 0 仍须开票；仅**真正零元**收费/挂号（票面合计 ≤ 0）跳过 `createDraftInvoiceAfterPayment`、不产生 `adm_invoice`、不入博思队列；应用编排落点 `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable` |
| `BR-INV-QRY-001` | 票据主档查询为只读 | 不在查询路径写业务状态 |
| `BR-INV-QRY-002` | 支付详情展示票面时取「当前付款单下最新已开具」记录 | 与收费/支付详情展示约定一致 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 依赖聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 患者管理 | Patient | `患者标识(patientId)` | 票据归属的患者 |
| 支付对账 | PaymentReconciliation | `付款单标识(reconciliationId)` | 结算/付款单关联 |
| 收费管理 | ChargeItem | `关联费用项ID集合(chargeItemIds)` | 票据关联的收费明细（字符串表达） |

### 8.2 下游影响

| 下游上下文 | 受影响聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 支付对账 | PaymentReconciliation | 事件订阅 | 监听 `InvoiceIssuedEvent`，记录票据开具流水用于对账 |
| QueryService/报表 | — | 事件订阅 | 监听 `InvoiceIssuedEvent` / `InvoiceCancelledEvent`，更新票据查询视图与财务报表 |
| 通知服务 | — | 事件订阅 | 监听 `InvoiceIssuedEvent`，推送电子票据链接给患者 |

### 8.3 引用规则

> **统一规则**：
>
> - 患者引用统一使用 `患者标识(patientId)`（`patient_id`）
> - 付款单引用使用 `付款单标识(reconciliationId)`（`reconciliation_id`）
> - 收费明细引用使用 `charge_item_ids`（逗号分隔 ChargeItem 主键）；就诊维度由 ChargeItem 承载，**Invoice 主表不强制 `encounter_id`**（与 `DM-ADM-011` 分工一致）
> - 需要就诊维度报表时，通过 ChargeItem 反查 `encounter_id`，不在票据聚合内做跨聚合拼装

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-ADM-001 Patient` | 关联 | 通过 `patient_id` |
| `DM-FIN-002 PaymentReconciliation` | 关联 | 通过 `reconciliation_id` |
| `DM-ADM-011 ChargeItem` | 关联 | 通过 `charge_item_ids` 主键集合（字符串承载） |

### 8.4 事件流示意图

```
Account                    Invoice                    PaymentReconciliation       通知服务
    │                         │                              │                      │
    │── AccountSettled ──────>│                              │                      │
    │                         │── InvoiceDraftCreatedEvent ──>│                      │
    │                         │                              │                      │
    │                         │── InvoiceIssuedEvent ────────>│── 记录开票流水         │
    │                         │                              │                      │── 推送电子票据
    │                         │                              │                      │
    │   (退费场景)             │                              │                      │
    │                         │── InvoiceCancelledEvent ─────>│── 记录红冲流水         │
    │                         │                              │                      │
```

---

## 9. 补充

### 9.1 门诊与住院票据差异

| 维度 | 门诊票据 | 住院发票 |
|---|---|---|
| 生成时机 | 每次收费后即生成 | 出院结算后统一生成 |
| 票据数量 | 一次就诊可能多张票据（挂号一张、药费一张、检查一张） | 一次住院通常一张汇总发票 |
| 明细粒度 | 按收费批次汇总 | 按费用类别（药品、检查、治疗等）汇总 |
| 退费处理 | 退哪张票冲红哪张票 | 整体冲红后重新开具 |
| 医保拆分 | 预结算时拆分，票面体现自费/医保金额 | 出院结算时拆分，票面体现完整的费用拆分明细 |

### 9.2 退费冲红流程

退费冲红是 Invoice 最复杂的业务场景，完整流程如下：

1. **退费发起**：应用服务校验退费条件，确定需冲红的原票
2. **原票红冲**：调用 `writeoffInvoiceAndCreateRedRecord`，原票状态推进为 `CANCELLED`
3. **红票创建**：生成红冲票据（`typeCode = 1(红字)`），绑定退款付款单
4. **红票开具**：如原票已开具电子票据，红票也需对接电子票据平台开具红字电子票据
5. **对账闭环**：红票与退款付款单形成闭环，PaymentReconciliation 记录冲红流水

> **关键约束**：红冲不修改原票金额，而是通过新增红票实现冲抵。原票与红票通过 `reconciliationId` 追溯，确保审计链完整。

### 9.3 电子票据管理

电子票据对接第三方平台（如财政电子票据管理系统），流程如下：

1. **Invoice 侧**：仅负责保存电子票据要素（二维码、图片地址等），不参与平台交互逻辑
2. **应用层编排**：调用第三方票据平台 API 完成开票，成功后回填电子票据要素字段
3. **异步通知**：开票成功后发布 `InvoiceIssuedEvent`，通知服务推送票据链接给患者

### 9.4 与 Account / ChargeItem / PaymentReconciliation 的职责边界

| 职责 | Account | ChargeItem | Invoice | PaymentReconciliation |
|---|---|---|---|---|
| 费用归集 | 按就诊汇总 | 单条收费明细 | — | — |
| 结算 | 执行结算逻辑 | — | — | — |
| 票据凭证 | — | — | 开具法律凭证 | — |
| 退费 | 扣减费用总额 | 标记退费状态 | 红冲原票 + 生成红票 | 记录退款流水 |
| 对账 | — | — | 提供票面数据 | 执行对账逻辑 |

### 9.5 票据号管理说明

- 发票编号（`busNo`）由号段管理模块统一分配
- 号段资源由应用层管理，Invoice 聚合不负责号段分配策略
- 电子票据号码（`billNo`）与发票编号（`busNo`）用途不同：前者为电子票据平台分配的号码，后者为院内业务编号
- 作废票据的号码不回收，保留空号记录用于审计

*最后更新：2026-04-18*

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `InvoiceDomainService`/`InvoiceQueryService` 白名单对齐（4写+3读） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`InvoiceDomainService`

| 方法 |
|------|
| `createDraftInvoiceAfterPayment` |
| `issueElectronicInvoice` |
| `writeoffInvoiceAndCreateRedRecord` |
| `voidDraftInvoice` |

### QueryService：`InvoiceQueryService`

| 方法 |
|------|
| `getInvoiceById` |
| `getInvoiceByPaymentId` |
| `getLatestIssuedInvoiceByPaymentId` |
| `findOptionalInvoiceEntityById` |
| `listInvoiceEntities` |
| `pageInvoiceEntities` |

