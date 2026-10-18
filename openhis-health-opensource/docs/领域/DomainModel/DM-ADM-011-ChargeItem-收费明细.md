# DM-ADM-011 ChargeItem 收费明细

> **限界上下文**：财务域 · 收费明细管理  
> **聚合类型**：实体聚合（单表聚合）  
> **版本**：v1.6.28

---

## 1. 概述

### 1.1 业务定义

收费明细（ChargeItem）是一条可计费、可结算、可退费的费用明细事实，承载数量、金额、状态和发生时间，是收费全链路的统一锚点。它回答的核心业务问题是："**患者被收取了哪些费用？**"——它是 HIS 中挂号、医嘱执行、收费、退费、医保结算等业务的基础数据支撑。

在临床业务中，收费明细是连接**诊疗活动**（医嘱、检查、治疗）与**财务结算**（发票、支付对账、医保申报）的关键纽带。

### 1.2 核心职责


| 职责     | 说明                                                               |
| ------ | ---------------------------------------------------------------- |
| 费用明细归集 | 记录单次就诊中每项收费的明细，包括数量、单价、总价、优惠、调价；组套项目拆为多行，父子层级用 `bus_no`（`父.子`）表达 |
| 生命周期管理 | 维护收费明细的状态流转（草稿→已计划/待结算→已结算→退款中→已退款）                               |
| 业务来源追溯 | 追溯费用产生的业务来源（医嘱、检查、治疗、挂号等）                                        |
| 退费链路追踪 | 记录退费关联信息，确保退费、退药、退检等操作可追溯                                        |
| 跨域关联锚点 | 为发票（Invoice）、对账（PaymentReconciliation）、医保（Claim）等提供统一明细标识        |


### 1.3 适用范围


| 类型   | 说明                               |
| ---- | -------------------------------- |
| 门诊挂号 | 门诊挂号产生挂号费（如普通门诊挂号）          |
| 医嘱开立 | 药品医嘱、服务医嘱开立时创建草稿明细（如西药处方、检验申请）   |
| 医嘱执行 | 医嘱执行后激活收费明细（如静脉输液执行、血常规检验）       |
| 护士划价 | 护士站发起临时耗材/诊疗活动划价（如一次性耗材使用、换药）    |
| 收费结算 | 收费台批量结算费用明细（如门诊收费、出院结算）          |
| 退费   | 退号、退药、退检、退治等退费操作（如退药退费、退检退费）     |
| 医保切换 | 医保/自费账户切换（如患者补办医保卡）              |
| 自动滚费 | 住院在院期间按规则自动生成周期性费用（如床位费、护理费自动滚动） |


### 1.4 与 Invoice 的边界

> Invoice（发票）是汇总同一就诊的多条收费明细，是收费的法律凭证。ChargeItem 是收费的最小粒度，独立聚合存在，不作为 Invoice 聚合内实体。Invoice 通过引用 `ChargeItem.id` 关联收费明细。

### 1.5 下游应用服务（住院结算）

| AS | 本聚合能力 | DM 锚点 |
| --- | --- | --- |
| **AS-IP-FIN-003** 住院结算 | 预结算门禁、批量结算、队列「有待结算」就诊筛选 | §6.1.9b / §6.1.12 / §6.2.16 |
| **AS-IP-NUR-006** 费用明细 | 护士站费用 Tab（**非**结算 IF；过滤口径见 §6.2.15） | §6.2.15 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表（**一行一条可计费明细**；组套为**多行**，不再使用 `children_json` 承载子项）

聚合根实体对应单表 `adm_charge_item`；每条物理行有独立主键 `id`。非组套场景一行即一条业务明细；组套场景由**父行（组套头）+ 若干子行**共同表达，父子关系与排序见 **§4.4**（`bus_no` 与可选 `parent_id`）。

**表结构**：

- 主表：`adm_charge_item`（主键：`id`）
- 组套关系：与 `DM-ADM-003 Location` 一致，采用 **层级业务编码 `bus_no`（半角 `.` 分隔路径）**；子行编码为 `**{父行.bus_no}.{子项稳定编码}`**；可选 `**parent_id**` 指向父行 `id`（自引用，便于级联与 ORM，与 Location 的父引用同属一类做法）
- 索引建议：`encounter_id`（就诊归集）、`patient_id`（患者）、`account_id`（账户）、`status_enum`（收费状态）、`(service_table, service_id)`（业务来源锚点）、`definition_id`（定价锚点）、`org_id`（机构）、`bus_no`（组套前缀/层级检索）、`parent_id`（子行归组）

**高频查询模式**：

- 按明细主键：`id` 主键或批量 `IN`
- 按就诊列费用：`encounter_id` 索引扫描
- 按医嘱/请求追溯：`service_table` + `service_id` 命中
- 按组套展开：`bus_no` 等值命中父行，或 `bus_no` 前缀 / `LIKE '根.%'`（配合索引策略）列出子行；或 `parent_id = 父行 id`
- 按结算单/账单：`BR-CHG-QRY-002` 优先QueryService；写库侧按支付/住院结算单关联键查询（实现见 本稿 **§6.1**）

### 2.2 聚合根标识


| 属性     | 类型                      | 说明                        |
| ------ | ----------------------- | ------------------------- |
| 收费明细标识 | `ChargeItemId` / `Long` | 系统内部唯一标识（雪花 ID），对应主键 `id` |


### 2.3 状态机

```
收费明细链路（ChargeItem）

                    ┌─────────────┐
                    │   DRAFT     │  医嘱暂存（住院临时等）
                    └──────┬──────┘
           临时签发/门诊签发 │  护士划价 / 住院执行入账
                    ┌──────┴──────────────────────────┐
                    ▼                                  ▼
             ┌─────────────┐                  ┌──────────────────┐
             │  PLANNED    │                  │ PENDING_SETTLEMENT│
             │ （待收费）   │                  │   （待结算）       │
             └──────┬──────┘                  └─────────┬─────────┘
                    │ 门诊预结算/结算                    │ 住院日结/出院结算
                    ▼                                  ▼
             ┌─────────────┐                  ┌─────────────┐
             │  SETTLED    │◄─────────────────│  SETTLED    │
             └──────┬──────┘                  └─────────────┘
                    │ 发起退费
                    ▼
             ┌─────────────┐
             │ REFUNDING   │
             └──────┬──────┘
                    ▼
             ┌─────────────┐
             │  REFUNDED   │
             └─────────────┘
```

#### 状态定义


| code | info | 语义 |
| ---- | ---- | ---- |
| `DRAFT` | 草稿 | 业务已开立但未进入收费窗口（常见于**住院临时**医嘱暂存），允许执行、取消 |
| `PLANNED` | 已计划（待收费） | 已形成可收费事实（**非**占用住院预交金展示余额）；门诊预结算/结算门禁；**住院临时**医嘱签发后升位 |
| `PENDING_SETTLEMENT` | 待结算 | **已划价或已执行入账**，占用住院预交金展示余额（与 `SETTLED` 一并计入占用额）；待日结或出院结算 |
| `SETTLED` | 已结算 | 收费完成，允许发起退费 |
| `REFUNDING` | 退款中 | 退费处理中，避免并发重复退费，允许退费完成 |
| `REFUNDED` | 已退款 | 退费完成终态 |


> **状态枚举说明**：
>
> - 编码值与实现 `ChargeItemStatus` 枚举一一对应（共 6 值，均为字符串编码）
> - `DRAFT` 对应医嘱开立（暂存）后的草稿态；**正式签发前**不得进入门诊预结算（见 `preSettleChargeItems`）
> - `PLANNED`（**待收费**）与 `PENDING_SETTLEMENT`（**待结算**）**不是同义**：前者不扣住院展示余额，后者已计入在院占用额
> - `PLANNED` 是**门诊与住院共用**状态，**不是**门诊专用
> - **住院医生站 · 长临分轨**（**AS-IP-DOR-003** **BR-IP-ORD-005/006**）：**长期**暂存/签发**不**创建 `ChargeItem`；**临时**暂存建 `DRAFT`、签发 **`DRAFT→PLANNED`**（按发放锚点升位）
> - **门诊医生站**：**诊疗**路径——**IF-DOR-15** 签发成功后，对 `encounter_id` + `service_table`（服务申请物理表）+ `service_id`（请求主键）锚定的、仍为 `DRAFT` 的明细调用 `planDraftChargeItemsAfterAdviceSigned` 批量升为 `PLANNED`。**药品/耗材 FIFO 分段**路径——费用行须同时落 **`service_*`（医嘱请求）**、**`product_*`（定义）**、**`dispense_*`（发放分段）**；签发升位与签退回退按 **`dispense_table` + `dispense_id`** 集合命中仍为 `DRAFT` / `PLANNED` 的明细（如 `planDraftChargeItemsAfterAdviceSignedByDispenseAnchors` / 对称回退）。**IF-DOR-16** 签退时诊疗仍可按请求锚点 `rollbackPlannedChargeItemsAfterAdviceSignedOff`；药/耗分段费用另按发放锚点作废草稿或回退已计划态。
> - **住院护士站**：护士划价（`priceByNurseStation`）、医嘱执行入账（`activateChargeByExe` / `createPlannedChargeItemFromOrderExecution`）目标态为 **`PENDING_SETTLEMENT`**
> - `SETTLED` 对应已完成收费确认
> - `REFUNDING` 仅作为退费过程态，不应长期滞留；必须被后续动作收口为 `REFUNDED`
> - 本节状态为 HIS 本地业务状态机，并非 FHIR `ChargeItem.status` 的一一等值

### 2.4 本地状态与 FHIR 交换状态映射


| 本地状态 | 本地语义 | FHIR `ChargeItem.status`（建议） | 交换说明 |
| -------- | -------- | ---------------------------- | ------------------ |
| `DRAFT` | 已开立未进入收费窗口 | `planned` | 尚未形成收费事实 |
| `PLANNED` | 已形成可收费事实（待收费） | `billable` | 门诊可预结算/结算；住院临时签发后待执行/划价才进入待结算 |
| `PENDING_SETTLEMENT` | 待结算（已入账） | `billable` | 住院待结算，占用预交金展示余额 |
| `SETTLED` | 已结算 | `billed` | 已完成收费确认 |
| `REFUNDING` | 退费处理中 | `entered-in-error`（退费完成后） | 处理中建议用本地扩展，完成后映射终态 |
| `REFUNDED` | 已退款 | `entered-in-error` | 通过关联反向流水保留追溯 |


---

## 3. 实体

```text
ChargeItem（聚合根实体，单表一行一实体实例）
════════════════════════════════════════════════════════════

  非组套：单行即一条收费明细（bus_no 可为单段业务键或空策略由实现约定）

  组套：多行同属一组
    父行  bus_no = GRP20250418001
    子行  bus_no = GRP20250418001.S001
          bus_no = GRP20250418001.S002   （形态对齐 Location：父编码 "." 子编码）

  ┌──────────────────────────────────────────────────────┐
  │ 每行：ChargeItem                                      │
  │  encounterId / patientId / accountId                 │
  │  serviceTable + serviceId / definitionId             │
  │  bus_no（层级键） / parent_id（可选，指向父行）         │
  │  quantityValue / unitPrice / totalPrice / statusEnum  │
  └──────────────────────────────────────────────────────┘
```

组套子项以**子行**持久化，不再使用 `children_json`；层级规则见 §4.4。

### 3.1 收费明细（ChargeItem）

ChargeItem 聚合根的属性定义，对应 `adm_charge_item` 表。


| 属性       | 字段名                      | 类型                       | 必填  | 说明                                                                                                                      |
| -------- | ------------------------ | ------------------------ | --- | ----------------------------------------------------------------------------------------------------------------------- |
| 收费状态     | `status_enum`            | `ChargeItemStatus`（值对象）  | 是   | 见 §2.3 状态机                                                                                                              |
| 生成来源     | `generate_source_enum`   | `GenerateSource`（值对象）    | 是   | 见 §4.2                                                                                                                  |
| 收费上下文    | `context_enum`           | `ChargeItemContext`（值对象） | 是   | 见 §4.3                                                                                                                  |
| 业务编码（层级） | `bus_no`                 | `String`                 | 否   | **层级路径业务键**，半角 `.` 分隔（与 `DM-ADM-003 Location` 的 `bus_no` 形态一致）。非组套：单段或院内约定；组套父行：组内根键；组套子行：必为 `父行.bus_no + "." + 子项稳定编码` |
| 父明细标识    | `parent_id`              | `Long`                   | 否   | 组套子行指向父行主键 `id`；非组套或父行自身为空。与 `bus_no` 二选一或并存由实现约定，**推荐同时落库**以便校验与级联                                                     |
| 处方号      | `prescription_no`        | `String`                 | 否   | **结算快照**（非业务真源）。门诊药品业务真源为 `MedicationRequest.prescription_no`；签发挂费**不**预写本列，**AS-OP-FIN-002 IF-OPC-07** 正式结算前由应用层从药品请求（或发放→药品请求）抄写；诊疗侧**仅**申请单号（SR `prescription_no` 且前缀 **`AR`**）可盖本列。**中药代煎 companion** 建费/建 SR **不**写方号（关联靠 `based_on`→药嘱，**AS-OP-DOR-010 · BR-TCM-RX-009**）。账单打印/统计可只读本列，业务取号/分方一律走请求侧。 |
| 患者标识     | `patient_id`             | `Long`                   | 是   | 关联患者聚合根的 ID                                                                                                             |
| 就诊标识     | `encounter_id`           | `Long`                   | 是   | 关联就诊聚合根的 ID                                                                                                             |
| 费用发生时间   | `occurrence_time`        | `DateTime`               | 是   | 费用发生的时刻                                                                                                                 |
| 医技确费人标识  | `fee_confirmer_id`       | `Long`                   | 否   | 医技费用清单一键确费人；空=未确费。**禁止**当临床执行人或收款人——执行人见 Procedure / DeviceDispense，收款人见 `enterer_id`（**BR-OPC-016**） |
| 医技确费时刻   | `fee_confirmed_at`       | `DateTime`               | 否   | 与 `fee_confirmer_id` 成对；有值即该行已确费                                                                                     |
| 开立科室标识   | `requesting_org_id`      | `Long`                   | 否   | 开立科室 ID                                                                                                                 |
| 成本科室标识   | `cost_org_id`            | `Long`                   | 否   | 成本科室 ID                                                                                                                 |
| 数量值      | `quantity_value`         | `BigDecimal`             | 是   | 数量                                                                                                                      |
| 数量单位     | `quantity_unit`          | `String`                 | 是   | 单位                                                                                                                      |
| 单价       | `unit_price`             | `BigDecimal`             | 是   | 单价                                                                                                                      |
| 总价       | `total_price`            | `BigDecimal`             | 是   | 总价（原始金额）                                                                                                                |
| 项目定义标识   | `definition_id`          | `Long`                   | 是   | 关联收费项定价聚合根的 ID                                                                                                          |
| 定价明细标识   | `def_detail_id`          | `Long`                   | 否   | 定价明细 ID（组套子项）                                                                                                           |
| 原价       | `base_amount`            | `BigDecimal`             | 是   | 原价                                                                                                                      |
| 折后金额     | `discount_amount`        | `BigDecimal`             | 是   | 折后金额                                                                                                                    |
| 附加金额     | `surcharge_amount`       | `BigDecimal`             | 是   | 附加费用                                                                                                                    |
| 系统优惠金额   | `system_discount_price`  | `BigDecimal`             | 否   | 系统自动优惠的金额                                                                                                               |
| 人工调价金额   | `manual_adjusted_price`  | `BigDecimal`             | 否   | 人工调价的金额                                                                                                                 |
| 改价原因编码   | `override_reason_code`   | `String`                 | 否   | 改价原因编码                                                                                                                  |
| 改价原因说明   | `override_reason_text`   | `String`                 | 否   | 改价原因说明                                                                                                                  |
| 收费人标识    | `enterer_id`             | `Long`                   | 否   | 收费人 `adm_practitioner.id`；仅结算/退费完成时写入，与 payment `enterer_id` 同源（**BR-OPC-016**）。开单医生见上游 Request `requester_id` |
| 收费时间     | `entered_date`           | `DateTime`               | 否   | 收费时刻；与 payment `bill_date` 同值。开立时刻见上游 Request `req_opened_time`                                              |
| 服务来源表名   | `service_table`          | `String`                 | 是   | 上游业务表名（开立来源），BR-CHG-001                                                                                                 |
| 服务来源主键   | `service_id`             | `Long`                   | 是   | 上游业务主键，BR-CHG-001                                                                                                       |
| 产品定义表名   | `product_table`          | `String`                 | 否   | 产品定义表名                                                                                                                  |
| 产品定义主键   | `product_id`             | `Long`                   | 否   | 产品定义主键                                                                                                                  |
| 执行记录标识   | `procedure_id`           | `Long`                   | 否   | 执行记录 ID（Procedure/DiagnosticReport）                                                                                     |
| 发放记录表名   | `dispense_table`         | `String`                 | 否   | 发放记录表名（MedicationDispense/DeviceDispense）                                                                               |
| 发放记录主键   | `dispense_id`            | `Long`                   | 否   | 发放记录主键                                                                                                                  |
| 医保结算状态   | `claim_state_enum`       | `Integer`                | 否   | 索赔/医保相关状态                                                                                                               |
| 打印次数     | `print_count`            | `Integer`                | 否   | 发票/收据打印次数                                                                                                               |
| 账户标识     | `account_id`             | `Long`                   | 是   | 关联账户聚合根的 ID（自费/医保）                                                                                                      |
| 机构标识     | `org_id`                 | `Long`                   | 是   | 机构 ID                                                                                                                   |
| 退费关联标识   | `refund_id`              | `Long`                   | 否   | 退费/冲销/再生关联 ID（**同字段双义，须结合数量符号区分**）。① **冲销负向行**（§6.1.3a）：数量非正，`refund_id`→被冲正向源行；② **反结/半退再生正向行**（§6.1.12a / **BR-CHG-015**）：数量为正，`refund_id`→源 `SETTLED` 行（血缘，**非**已冲销）。判定冲销留痕用实体 `isPricingOffsetLine()`（数量非正且 `refund_id` 非空）；**禁止**仅凭 `refund_id != null` 排除再生正向行。半退重开与 **DM-FIN-002** **BR-PMR-017** 编排一致                                                                                                         |
| 就诊诊断绑定有序 ids 串 | `encounter_diagnosis_ids` | `String` | 否 | 有序逗号分隔的就诊诊断主键；挂费自请求拷贝；结算 2203 按此多诊上传（首位仅 maindiag/分组代表，不经 Condition） |

| 中医标识     | `tcm_flag`               | `Boolean`                | 否   | 中医项目标识（`true`=中医，`false`=西医）                                                                                            |


> **门诊药品/耗材计费锚点（`BR-CHG-001` 扩展口径）**：签发后按 FIFO 拆分为多条发放物理行时，**每条** `ChargeItem` 与**一条**发放分段 **一对一**（计价粒度）；持久化上 **`service_table` + `service_id` 始终锚定医嘱请求**（`med_medication_request` / `wkf_device_request`），**`product_table` + `product_id` 锚定药品/诊疗/耗材定义**（三大目录：`med_medication_definition` / `wkf_activity_definition` / `adm_device_definition` 与对应定义主键；**暂存**相位即须写入，**不得**留空），**`dispense_table` + `dispense_id` 锚定本条发放物理行**（`med_medication_dispense` / `wkf_device_dispense`；**仅签发 FIFO 后**落库）。同一请求下可有多条费用行（多段发放），故 **`service_*` 在多行间可重复**，与 **`dispense_*` 组合唯一标识**计费分段。**前端传入金额不得作为入账权威**，须以定价域重算结果落库 `unit_price` / `total_price`。**暂存**相位择价：`ChargeItemDefinition` 行由应用层 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`** 在调用 `createDraftChargeItemFromOrder` **之前**解析（药耗 **LOT_NUMBER_PRICE** 须结合目录已解析库位 FIFO 批号；见 **BR-CHG-016**）——库位透传载荷 **`dispenseLocationId`/`performLocation`** **不**落库本表。**签发 FIFO 分段**择价：每一发放分段须按该段 **`lotNumber`** 经 **`ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft`** 匹配 **`LOT_NUMBER_PRICE`**（与护士执行挂费同口径；**禁止**跨批号 priority；见 **BR-CHG-017** / **AS-OP-DOR-003 · BR-ORD-016**）。


> 通用审计字段（`createBy/createTime/updateBy/updateTime`）、租户字段（`tenantId`）、逻辑删除字段（`deleteFlag`）继承自 `HisBaseEntity`，遵循统一约定。

### 3.2 子实体

> 持久化为**单表多行**：无独立子表。组套时**子项为额外物理行**（子行 `bus_no` 为父 `bus_no` 的点分扩展），不再使用 `children_json`。

---

## 4. 值对象 / 枚举

### 4.1 收费状态（ChargeItemStatus）

见 §2.3 状态定义中的枚举表。

### 4.2 生成来源（GenerateSource）

与 **`com.zxplat.domain.share.enums.GenerateSource`** 及 **`DM-MED-003` §4.5** 共用同一套英文 **code**。

| 枚举常量 | code（落库） | 说明 |
| --- | --- | --- |
| `DOCTOR_PRESCRIPTION` | `doctor-prescription` | 医生开立 |
| `NURSE_PRICING` | `nurse-pricing` | 护士划价 |
| `MEDICAL_TECH_PRICING` | `medical-tech-pricing` | 医技划价 |
| `REGISTRATION_REGISTER` | `registration` | 挂号登记 |
| `ORDER_EXECUTE` | `order-execute` | 医嘱执行 |
| `AUTO_ROLL_FEES` | `auto-roll-fees` | 自动滚费 |
| `PHYSICAL_EXAM_GROUP` | `physical-exam-group` | 团检套餐 |
| `ANESTHESIA_EXECUTE` | `anesthesia-execute` | 麻醉执行（与 `ORDER_EXECUTE` 同属**执行族**，`GenerateSource#isOrderExecuteFamilyCode`） |
| `ANESTHESIA_PRICING` | `anesthesia-pricing` | 麻醉划价（与 `NURSE_PRICING` 同属护士站划价族） |

> **存储口径**：对应主表字段 `generate_source_enum`，与 **`GenerateSource#getCode()`** 一一对应；**禁止**数字串作身份。取消执行账单逻辑删须覆盖**执行族**（`order-execute` + `anesthesia-execute`），见 §6.1.5a。

### 4.3 收费上下文（ChargeItemContext）


| 编码值 | 枚举名          | 英文编码 | 说明  |
| --- | ------------ | ---- | --- |
| 1   | `MEDICATION` | `1`  | 药品  |
| 2   | `DEVICE`     | `2`  | 耗材  |
| 3   | `ACTIVITY`   | `3`  | 项目  |
| 4   | `REGISTER`   | `4`  | 挂号  |


> **存储口径**：对应主表字段 `context_enum`，与实现 `ChargeItemContext` 枚举一一对应（共 4 值）。

### 4.4 带子项诊疗层级编码（bus_no）

与 `**DM-ADM-003 Location`** 一致，`bus_no` 表示**层级路径**，使用 **半角 `.` 分隔**各段，用于树形检索与「父–子」归属。本聚合用该形态表达**带子项诊疗的父账单与子项账单**（**不是** `DM-CUS-012` 医嘱组套 OrderTemplate）。


| 行角色      | `bus_no` 约定                                                                   | `parent_id`     |
| -------- | ----------------------------------------------------------------------------- | --------------- |
| 父账单（头行） | 组内唯一根段，如 `GRP20250418001` 或与上游医嘱/申请分组号对齐                                      | `null`          |
| 子项账单     | `**父行.bus_no + "." + 子项稳定编码`**，如 `GRP20250418001.S001`、`GRP20250418001.LIS02` | 指向父行 `id`（推荐必填） |
| 无子项明细    | 单段业务键或院内约定；无子行                                                                | `null`          |


**规则要点**：

- 子项稳定编码建议取自定价子项、收费项目编码等，保证可追溯与打印一致。
- 同一组套内**共享同一** `service_table + service_id`（或与实现约定的头行锚点一致），子行通过 `definition_id`/`def_detail_id` 区分具体计价行。
- 删除/退费组套时，应对父行与子行**联动**处理（状态与事件覆盖组内全部物理行，细则见 本稿 **§6.1**）。

### 4.5 医保结算状态（claim_state_enum）

> **存储口径**：对应主表字段 `claim_state_enum`，以 **Integer** 承载院内「医保费用/索赔状态」字典编码；具体码表以实现侧数据字典或医保对接配置为准，避免在 DM 与运行时字典双写导致漂移。领域层字段名 `claimStateEnum` 与表列对齐见领域层 本稿 **§6.1**。


| 字典语义档位     | 说明                     |
| ---------- | ---------------------- |
| 未上传 / 自费未传 | 明细尚未进入医保上传链路，或纯自费不计入上传 |
| 已上传待反馈     | 已发起上传，等待平台返回           |
| 已结算确认      | 医保侧已确认计入结算的费用状态        |
| 已冲销或作废     | 上传失败、拒付后冲销或业务作废后的状态    |


> **交叉引用**：领域层详细设计（`DM-ADM-011-ChargeItem-收费明细-本稿 **§6.1**.md`）中 `claimStateEnum` 字段说明指向本节。

---

## 5. 领域事件

### 5.1 事件定义


| 编号     | 事件名称                                  | 触发时机           | 携带数据                        | 消费方          | 版本  |
| ------ | ------------------------------------- | -------------- | --------------------------- | ------------ | --- |
| 5.1.1  | `RegistrationChargeItemCreatedEvent`  | 挂号费明细创建后       | 收费明细 ID、患者标识、就诊标识、项目定义标识、金额 | 收费台、QueryService、票据域  | v1  |
| 5.1.2  | `OrderDraftChargeItemCreatedEvent`    | 医嘱草稿账单生成后      | 收费明细 ID、患者标识、开立来源           | 医嘱追溯、QueryService     | v1  |
| 5.1.3  | `NursePricingChargeItemCreatedEvent`  | 护士划价生成明细后      | 收费明细 ID、患者标识、项目定义标识、金额      | 护士站、收费台、审计域  | v1  |
| 5.1.4  | `ChargeItemActivatedByExecutionEvent` | 执行后明细进入可结算后    | 收费明细 ID、状态、执行时间             | 收费台、住院费用视图   | v1  |
| 5.1.5  | `ChargeItemExecutionCancelledEvent`   | 取消执行触发明细回退/移除后 | 收费明细 ID、回退状态                | 执行域、药房域、审计域  | v1  |
| 5.1.6  | `ChargeItemSettledEvent`              | 收费完成后          | 收费明细 ID、结算时间、结算人            | 对账域、票据域      | v1  |
| 5.1.7  | `RegistrationChargeItemRefundedEvent` | 退号联动退费完成后      | 收费明细 ID、退费 ID、退费金额          | 挂号域、对账域、票据域  | v1  |
| 5.1.8  | `ChargeItemAccountSwitchedEvent`      | 医保/自费切换后       | 收费明细 ID、原账户 ID、新账户 ID       | 医保编排、账户域、QueryService | v1  |
| 5.1.9  | `ChargeItemPreSettledEvent`           | 预结算快照生成后       | 收费明细 ID 列表、预结算总金额           | 收费台、支付编排     | v1  |
| 5.1.10 | `OutpatientBillRefundRequestedEvent`  | 门诊按账单申请退费后     | 收费明细 ID 列表、退费金额             | 退费编排、对账域     | v1  |
| 5.1.11 | `OutpatientBillRefundCompletedEvent`  | 门诊按账单退费完成后     | 收费明细 ID 列表、退费结果             | 对账域、票据域、统计   | v1  |
| 5.1.12 | `InpatientChargeItemsSettledEvent`    | 住院收费结算完成后      | 收费明细 ID 列表、结算批次             | 对账域、住院收费域、统计 | v1  |
| 5.1.13 | `InpatientBillRefundRequestedEvent`   | 住院账单申请退费后      | 收费明细 ID 列表、退费金额             | 退费编排、对账域     | v1  |
| 5.1.14 | `InpatientBillRefundCompletedEvent`   | 住院账单退费完成后      | 收费明细 ID 列表、退费结果             | 对账域、票据域、统计   | v1  |
| 5.1.15 | `NursePricingChargeItemOffsetEvent` | 取消护士划价冲销待结算明细后 | 冲销行 ID、源行 ID、就诊标识 | 护士站、住院费用视图 | v1 |
| 5.1.15a | `NursePricingChargeItemRemovedEvent` | （历史）物理删路径 | 收费明细 ID、就诊标识 | — | deprecated |
| 5.1.16 | `AutoRollChargeItemCreatedEvent`      | 住院滚费生成明细后（**`priceByAutoRoll`**） | 收费明细 ID                  | 住院费用视图、审计域   | v1  |


**账单领域事件后查询刷新（并列发布）**：在发布上表各业务事件的同一写路径中，可同时发布 `ChargeItemCreatedEvent`、`ChargeItemUpdatedEvent`、`ChargeItemDeletedEvent`（载荷为明细主键及就诊、患者等最小标识），供就诊维度账单汇总等QueryService 消费事件后刷新；业务语义仍以本表事件为准，二者载荷不可互相替代。

### 5.2 事件触发规则


| 当前状态                         | 操作                                            | 目标状态                     | 发布事件                                  |
| ---------------------------- | --------------------------------------------- | ------------------------ | ------------------------------------- |
| —                            | 挂号创建（`createRegistrationChargeItem`）          | `PLANNED`        | `RegistrationChargeItemCreatedEvent`  |
| —                            | 医嘱开立（`createDraftChargeItemFromOrder`）        | `DRAFT`                  | `OrderDraftChargeItemCreatedEvent`    |
| —                            | 护士划价（`priceByNurseStation`）                   | `PENDING_SETTLEMENT`    | `NursePricingChargeItemCreatedEvent`  |
| —                            | 住院滚费（`priceByAutoRoll`）                       | `PENDING_SETTLEMENT`    | `AutoRollChargeItemCreatedEvent`      |
| `PENDING_SETTLEMENT`         | 取消护士划价冲销（`offsetNursePricingChargeItemsByServiceAnchors`） | 原行保持 + 插入负向 `PENDING_SETTLEMENT` 冲销行 | `NursePricingChargeItemOffsetEvent`  |
| —                            | 住院医嘱执行新建费用（`createPlannedChargeItemFromOrderExecution`） | `PENDING_SETTLEMENT` | `ChargeItemActivatedByExecutionEvent` |
| `DRAFT`                      | 住院临时医嘱执行激活（`activateChargeByExe`）           | `PENDING_SETTLEMENT`    | `ChargeItemActivatedByExecutionEvent` |
| `PLANNED`                    | 住院临时医嘱执行激活（`activateChargeByExe`；签发后常态） | `PENDING_SETTLEMENT`    | `ChargeItemActivatedByExecutionEvent` |
| `DRAFT`                      | 执行医嘱（`activateChargeItemAfterExecution`）      | `PLANNED`               | `ChargeItemActivatedByExecutionEvent` |
| `DRAFT`                      | 医嘱签发后按请求锚点批量激活（`planDraftChargeItemsAfterAdviceSigned`） | `PLANNED`               | `ChargeItemActivatedByExecutionEvent` |
| `DRAFT`/`PLANNED`            | 取消执行（`cancelExecutionRollbackChargeItem`）     | 回退/移除                    | `ChargeItemExecutionCancelledEvent`   |
| `PLANNED`                    | 医嘱签退后按请求锚点回退（`rollbackPlannedChargeItemsAfterAdviceSignedOff`） | `DRAFT`                 | `ChargeItemExecutionCancelledEvent`   |
| `PLANNED`                    | 收费结算（`settleChargeItems`）                     | `SETTLED`                 | `ChargeItemSettledEvent`              |
| `SETTLED`                    | 退号联动退费（`returnRegistrationRefundChargeItems`） | `REFUNDED`               | `RegistrationChargeItemRefundedEvent` |
| `DRAFT`/`PLANNED`            | 账户切换（`switchChargeItemAccount`）               | 同状态                      | `ChargeItemAccountSwitchedEvent`      |
| `PLANNED`                    | 预结算（`preSettleChargeItems`）                   | `PLANNED`               | `ChargeItemPreSettledEvent`           |
| `SETTLED`                    | 门诊按账单申请退费（`requestOutpatientBillRefund`）      | `REFUNDING`              | `OutpatientBillRefundRequestedEvent`  |
| `REFUNDING`                  | 门诊按账单退费完成（`completeOutpatientBillRefund`）     | `REFUNDED` | `OutpatientBillRefundCompletedEvent`  |
| `PENDING_SETTLEMENT`         | 住院收费结算（`settleInpatientChargeItems`，目标态）   | `SETTLED`                 | `InpatientChargeItemsSettledEvent`    |
| `SETTLED`                    | 住院反结再生（`revertInpatientSettledChargeItems`）   | 源行保持 `SETTLED`；插入 `PENDING_SETTLEMENT` 新行 | `InpatientChargeItemsRevertedEvent`   |
| `SETTLED`                    | 住院退费申请（`requestInpatientBillRefund`）          | `REFUNDING`              | `InpatientBillRefundRequestedEvent`   |
| `REFUNDING`                  | 住院退费完成（`completeInpatientBillRefund`）         | `REFUNDED` | `InpatientBillRefundCompletedEvent`   |


---

## 6. 领域服务

### 6.1 写侧领域服务（ChargeItemDomainService）

> 写侧由应用服务编排事务后调用；命令类名、幂等键与异常字面量见 `DM-ADM-011-ChargeItem-收费明细-本稿 **§6.1**.md`。

#### 6.1.1 门诊挂号生成挂号费账单（`createRegistrationChargeItem`）

- **业务意义**：将挂号动作落地为可追溯收费明细。
- **边界说明**：仅维护收费明细，不处理支付与号源事务。
- **入参**：`CreateChargeItemCommand`（含患者、就诊、账户、锚点字段）。
- **出参**：新建明细主键（或等价结果载体，以落库与交互契约为准）。
- **前置条件**：命令必须携带 `serviceTable+serviceId` 与 `definitionId`（`BR-CHG-001`）。
- **状态变化**： `N/A -> PLANNED`（按命令口径）。
- **聚合内影响**：创建 `contextEnum=REGISTER` 明细并绑定患者/就诊/账户。
- **事件输出**：`RegistrationChargeItemCreatedEvent`。
- **失败语义**：锚点缺失、账户或就诊引用非法时拒绝；同语义重复创建按 `BR-CHG-003` 幂等。

#### 6.1.1a 挂号费科室更正（`retargetRegistrationFeeDepartment`）

- **业务意义**：挂号窗口改科室时，把仍挂在原科室上的挂号费行改到目标科室（**AS-OP-FIN-001** IF-REG-15）。
- **边界说明**：只处理 `service_table=adm_healthcare_service`，且 `org_id` / `requesting_org_id` / `cost_org_id` 等于原科室的列。医生站已开医嘱费用不移动。不改状态、不发事件。
- **入参**：就诊主键、原科室、目标科室。
- **出参**：无。
- **前置条件**：三参数均非空；原科室与目标科室相同则空操作。
- **状态变化**：`N/A`。
- **聚合内影响**：更新命中行的科室三列。
- **事件输出**：无。
- **失败语义**：参数不完整时拒绝。

#### 6.1.2 开立医嘱生成草稿账单（`createDraftChargeItemFromOrder`）

- **业务意义**：把医嘱开立事实转为计费草稿。
- **边界说明**：不维护医嘱聚合本体；**药耗择价**在应用层 **`DoctorStationAdviceAppService#createChargeIfPossible`**（及住院/护士对称编排）内完成——先 **`resolveDraftChargePriceLine`**（可选 `clientResolvedDispenseLocationId` 来自 IF 载荷 **`dispenseLocationId`/`performLocation`**，**BR-CHG-016**），再映射 **`CreateDraftChargeItemFromOrderCommand`** 调用本方法。
- **入参**：`CreateDraftChargeItemFromOrderCommand`。
- **出参**：新建明细主键。
- **前置条件**：具备业务来源锚点与定价锚点（`BR-CHG-001`）。
- **状态变化**：`N/A -> DRAFT`。
- **聚合内影响**：创建草稿明细并记录开立信息。
- **事件输出**：`OrderDraftChargeItemCreatedEvent`。
- **失败语义**：锚点不完整拒绝；重复开立按实现约定幂等或拒绝。

#### 6.1.3 护士划价生成费用明细（`priceByNurseStation`）

- **业务意义**：将护士站临时划价落地为**待结算**应收（**AS-IP-NUR-005**）。
- **边界说明**：不处理发放与库存主流程。
- **入参**：`PriceByNurseStationCommand`。
- **出参**：新建或更新后的明细主键集合。
- **前置条件**：计价关联锚点完整。
- **状态变化**：`N/A -> PENDING_SETTLEMENT`。
- **聚合内影响**：创建/更新明细并绑定请求单/发放记录。
- **事件输出**：`NursePricingChargeItemCreatedEvent`。
- **失败语义**：数量或金额违反 `BR-CHG-008` 时拒绝。

#### 6.1.3b 住院滚费生成费用明细（`priceByAutoRoll`）

- **业务意义**：**AS-IP-NUR-013** IF-IP-ARG-08 滚费落账创建待结算收费明细；与 **6.1.3** 同构但 `generate_source_enum=auto-roll-fees`。
- **边界说明**：初始状态 `PENDING_SETTLEMENT`；内部路由 `ChargeItemEventType.AUTO_ROLL_CREATED`；**不计入**护士站 pending 计数。
- **入参**：`CreateChargeItemCommand`（`service_table=wkf_service_request`；`definition_id` 为定价锚点；`occurrence_time` 为 billDate 口径）。
- **出参**：首条新建明细主键。
- **状态变化**：`N/A -> PENDING_SETTLEMENT`。
- **事件输出**：`AutoRollChargeItemCreatedEvent`（逐条；aggregateId 为明细主键）。

#### 6.1.3a 按请求锚点冲销站内划价待结算明细（`offsetNursePricingChargeItemsByServiceAnchors`）

- **业务意义**：住院护士站**取消划价**或医技工作站**取消医技划价**时，按上游请求锚点对尚未冲销的 **`PENDING_SETTLEMENT` + `generate_source∈{nurse-pricing, medical-tech-pricing}`** 正向费用行插入数量/金额取反的冲销行，`refund_id` 链回原行（**AS-IP-NUR-005** IF-IP-PRC-06 / **AS-MT-CHG-001**；冲销行继承原 `generate_source`；费用明细来源列对医技负向行展示「医技冲销」）。
- **边界说明**：与护士/医技划价写路径对称留痕；`Dispense`/库存回退、Procedure CANCEL 由应用编排；**不**软删上游 Request；**不**处理已结算/退费链路（整批 Gate）；**不**重复冲销已被**冲销负向行**（`isPricingOffsetLine()`）指向的源行。`refund_id` 与反结/半退再生共用：再生正向行（数量>0 且 `refund_id`→源 `SETTLED`）**仍是合法冲销源**。
- **入参**：`OffsetNursePricingChargeItemsByServiceAnchorsCommand`（`encounterId`、`serviceTable`、`serviceIds[]`；含 `operatorId`）。
- **出参**：—。
- **前置条件**：锚点参数完整；同锚点下若存在 **`SETTLED`/`REFUNDING`/`REFUNDED`** 等不可回退状态则整批拒绝（**BR-CHG-002**；反结链上已被再生叶替代的源 `SETTLED` 不参与 Gate，见 **BR-IP-RVS-011**）；仅冲销 **正向**（数量>0、**非** `isPricingOffsetLine()`）且尚未被冲销负向行对冲的 **`PENDING_SETTLEMENT` + 站内划价来源** 行（含反结再生正向，其 `refund_id` 可非空）。
- **状态变化**：原行保持 `PENDING_SETTLEMENT`；新冲销行 `N/A -> PENDING_SETTLEMENT`（负数量/负金额，`refund_id`→本批正向源）。
- **聚合内影响**：按 `encounterId` + `serviceTable` + `serviceIds` 批量加载；插入冲销行；无待冲销行静默跳过。
- **事件输出**：`NursePricingChargeItemOffsetEvent`（逐条，含 `offsetChargeItemId`、`sourceChargeItemId`、`encounterId`）。
- **失败语义**：锚点参数不完整 → `CHARGE_ITEM_INVALID_ARGUMENT`；已结算/退费中 → `CHARGE_ITEM_INVALID_STATUS`；无待冲销行静默。

> **历史**：原 `voidNursePricingChargeItemsByServiceAnchors`（物理删除）与 `NursePricingChargeItemRemovedEvent` 已由本方法替代；IF-IP-PRC-06 不再调用物理删。

#### 6.1.4 批量状态推进至待收费（`activateChargeItemAfterExecution`）

- **业务意义**：将草稿费用批量推进到 **PLANNED（待收费）**；供**门诊签发**及 **`planDraftChargeItemsAfterAdviceSigned`** 等路径委托；**不**用于住院护士站执行入账（执行走 **6.1.4c/d**）。
- **边界说明**：执行单主状态仍由执行域维护。
- **入参**：`ActivateChargeItemAfterExecutionCommand`（或等价 `BatchChargeItemIdsCommand`）。
- **出参**：更新后的明细标识（或空）。
- **前置条件**：仅 `DRAFT` 可推进（`BR-CHG-002`）。
- **状态变化**：`DRAFT -> PLANNED`。
- **聚合内影响**：固化执行信息与计价数量（若命令携带）。
- **事件输出**：`ChargeItemActivatedByExecutionEvent`。
- **失败语义**：前态不符、重复激活按 `BR-CHG-003` 幂等返回。

#### 6.1.4a 医嘱签发后按请求锚点批量激活（`planDraftChargeItemsAfterAdviceSigned`）

- **业务意义**：**门诊 IF-DOR-15** 及 **住院临时 IF-IP-DOR-15** 签发完成后，将仍处草稿的医嘱费用行升为 **`PLANNED`（待收费）**，与 **`preSettleChargeItems` 仅接受 `PLANNED`** 对齐。**长期住院医嘱签发不产生费用行**，本动作对其无匹配行、静默跳过。
- **边界说明**：不修改医嘱请求聚合；按 `encounterId` + `serviceTable` + `serviceId` 与 `createDraftChargeItemFromOrder` 写入的锚点一致做仓储过滤；无匹配行时静默返回。
- **入参**：`encounterId`，`serviceTable`，`serviceId`（Long 标量，非 Command 包装，以实现类签名为准）。
- **出参**：无（或等价 `void`）。
- **前置条件**：锚点完整；目标行为 `DRAFT`（由仓储查询限定）。
- **状态变化**：`DRAFT -> PLANNED`（批量，内部委托 `activateChargeItemAfterExecution`）。
- **事件输出**：与 **6.1.4** 相同（`ChargeItemActivatedByExecutionEvent`，逐条）。
- **失败语义**：委托批量迁移时的明细缺失等语义同 **6.1.4**。

#### 6.1.4b 医嘱签退后按请求锚点回退草稿（`rollbackPlannedChargeItemsAfterAdviceSignedOff`）

- **业务意义**：**IF-DOR-16** 签退后与「医嘱未对外生效」对称，禁止收费端继续预结算该医嘱锚点下的已计划费用。
- **边界说明**：仅处理仍为 `PLANNED` 的行（`SETTLED` 等不在查询范围内）；无匹配行时静默返回。
- **入参**：`encounterId`，`serviceTable`，`serviceId`。
- **出参**：无。
- **前置条件**：目标行为 `PLANNED`。
- **状态变化**：`PLANNED -> DRAFT`（内部委托 `cancelExecutionRollbackChargeItem`）。
- **事件输出**：`ChargeItemExecutionCancelledEvent`（逐条）。
- **失败语义**：同 **6.1.5**。

#### 6.1.4e 按单请求锚点作废未计费草稿（`voidUnbilledDraftChargeItemsByServiceAnchor`）

- **业务意义**：医嘱删除/签退前，作废仍 `DRAFT` 且未挂账的锚点费用行（单 `serviceTable` + `serviceId`）。
- **入参**：`VoidUnbilledDraftChargeItemsByServiceAnchorCommand`（`encounterId`、`serviceTable`、`serviceId`）。
- **状态变化**：`DRAFT` → `CANCELLED`（或软删，以实现为准）。
- **失败语义**：无匹配行静默。

#### 6.1.4f 按多请求锚点批量作废未计费草稿（`voidUnbilledDraftChargeItemsByServiceAnchors`）

- **业务意义**：门诊/住院医嘱批量签退、删除时，对多组 `(serviceTable, serviceId)` 作废 `DRAFT` 行。
- **入参**：`encounterId` + 锚点集合。
- **状态变化**：同 **6.1.4e**。

#### 6.1.4g 按多请求锚点批量签发升位（`planDraftChargeItemsAfterAdviceSignedForAnchors`）

- **业务意义**：**IF-DOR-15** 批量路径：多诊疗/药品请求签发后，将各锚点下 `DRAFT` 升为 `PLANNED`。
- **入参**：`encounterId` + 锚点集合（`serviceTable` + `serviceId`）。
- **状态变化**：`DRAFT` → `PLANNED`（委托 **6.1.4a** 语义）。

#### 6.1.4h 按多请求锚点批量签退回退（`rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors`）

- **业务意义**：**IF-DOR-16** 批量路径：多锚点 `PLANNED` → `DRAFT`。
- **入参**：`encounterId` + 锚点集合。
- **状态变化**：同 **6.1.4b**。

#### 6.1.4i 按发放锚点批量作废未计费草稿（`voidUnbilledDraftChargeItemsByDispenseAnchors`）

- **业务意义**：药/耗 **FIFO 分段**签退：按 `dispense_table` + `dispense_id` 集合作废仍为 `DRAFT` 的费用行。
- **入参**：`encounterId` + 发放锚点集合。
- **状态变化**：`DRAFT` → `CANCELLED`（或等价）。

#### 6.1.4j 按发放锚点批量签发升位（`planDraftChargeItemsAfterAdviceSignedByDispenseAnchors`）

- **业务意义**：药/耗签发后按发放分段将 `DRAFT` 升为 `PLANNED`（**AS-OP-DOR-003** / **AS-IP-DOR-003** 现行 Java 方法名）。
- **入参**：`encounterId` + 发放锚点集合。
- **状态变化**：`DRAFT` → `PLANNED`。

#### 6.1.4k 按发放锚点批量签退回退（`rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors`）

- **业务意义**：药/耗签退后按发放分段将 `PLANNED` 回退 `DRAFT`。
- **入参**：`encounterId` + 发放锚点集合。
- **状态变化**：`PLANNED` → `DRAFT`。

#### 6.1.4c 执行后激活收费明细并挂 Procedure（`activateChargeByExe`）

- **业务意义**：**临时**药品/诊疗医嘱执行后，将签发阶段已升为 **`PLANNED`（待收费）** 的费用行（或尚未升位的 **`DRAFT`**）激活为 **`PENDING_SETTLEMENT`（待结算）** 并挂接 `procedureId`（**AS-IP-NUR-004 BR-IP-EXE-017/018**）。
- **边界说明**：与 **6.1.4** 批量改状态区分——本命令逐条写 `procedureId`、`occurrenceTime`；**禁止**写收费行操作人戳（执行人真源在 Procedure）；**禁止**写 `enterer_*`（收费人仅结算/退费完成写入，**BR-OPC-016**）；**禁止**用于长期路径（长期走 **6.1.4d** 新建）。住院临时药品 FIFO 分段费用须按 **`dispense_table` + `dispense_id`** 定位待激活行（`service_*` 仍指向医嘱请求）。
- **入参**：`ActivateChargeByExeCommand`（批量 `Item`：`chargeItemId`、`procedureId`、`performedAt`）。
- **出参**：—。
- **前置条件**：目标行须 **`PLANNED` 或 `DRAFT`**（`BR-CHG-002`）；签发后常态为 `PLANNED`。
- **状态变化**：`PLANNED -> PENDING_SETTLEMENT`（兼容 `DRAFT -> PENDING_SETTLEMENT`）。
- **聚合内影响**：写入 Procedure 锚点与执行时刻（`procedure_id` / `occurrence_time`）。
- **事件输出**：`ChargeItemActivatedByExecutionEvent`（逐条，含 `procedureId`）。
- **失败语义**：非 `PLANNED`/`DRAFT`、明细不存在时拒绝。

#### 6.1.4d 长期执行新建待结算收费明细（`createPlannedChargeItemFromOrderExecution`）

- **业务意义**：**长期**医嘱每计划点执行后**新建**费用行，来源 **`ORDER_EXECUTE`**，直接 **`PENDING_SETTLEMENT`** 并挂 `procedureId`（**AS-IP-NUR-004 BR-IP-EXE-016/018**）。
- **边界说明**：与护士划价、签发后批量激活区分；数量/单位由应用层按单次量或首日量规则组装 `CreateChargeItemCommand`。
- **入参**：`CreateChargeItemCommand`（含 `generateSource=ORDER_EXECUTE`、`procedureId`、锚点与计价字段）。
- **出参**：新建明细主键。
- **前置条件**：Procedure 已完成；计价锚点完整。
- **状态变化**：`N/A -> PENDING_SETTLEMENT`。
- **聚合内影响**：新建一行并绑定执行锚点。
- **事件输出**：`ChargeItemActivatedByExecutionEvent`。
- **失败语义**：锚点缺失、金额非法时拒绝。

#### 6.1.4l 取消执行逆激活收费明细（`deactivateChargeByExe`）

- **业务意义**：**临时**医嘱取消执行时，将执行激活后的费用行从 **`PENDING_SETTLEMENT`** 回退为 **`PLANNED`** 并清 **`procedureId`**（**AS-IP-NUR-012 BR-IP-CXL-008**；与 **6.1.4c** 对称）。
- **边界说明**：仅用于临时路径已激活行；长期执行新建行走 **6.1.5a** 逻辑删除。
- **入参**：`DeactivateChargeByExeCommand`（批量 `Item`：`chargeItemId`）。
- **出参**：—。
- **前置条件**：目标行须 **`PENDING_SETTLEMENT`**；`SETTLED/REFUNDING/REFUNDED` 禁止（`BR-CHG-002`）。
- **状态变化**：`PENDING_SETTLEMENT -> PLANNED`；清 `procedureId`。
- **聚合内影响**：解除执行锚点，恢复签发后待收费态。
- **事件输出**：`ChargeItemExecutionCancelledEvent`（逐条）。
- **失败语义**：前态不符或已进入结算/退费链路时拒绝。

#### 6.1.5 医嘱取消执行回退账单（`cancelExecutionRollbackChargeItem`）

- **业务意义**：撤回取消执行后不应存在的计费事实。
- **边界说明**：不展开药域/执行域回退细节。
- **入参**：`CancelExecutionRollbackChargeItemCommand`。
- **出参**：回退结果摘要（删除/状态回退标识）。
- **前置条件**：已进入结算/退费链路的明细禁止回退（`BR-CHG-002`）。
- **状态变化**：草稿可移除；可回退场景回到 `PLANNED` 等（见命令口径）。
- **聚合内影响**：回退或逻辑删除目标明细（`BR-CHG-006`）。
- **事件输出**：`ChargeItemExecutionCancelledEvent`。
- **失败语义**：禁止回退的前态下拒绝。

#### 6.1.5a 执行新建账单逻辑删除（`voidOrderExecuteChargeItemsByProcedureIds`）

- **业务意义**：取消执行时，逻辑删除执行新建的 **`PENDING_SETTLEMENT` + 执行族** 行（**`order-execute` / `anesthesia-execute`**，`GenerateSource#isOrderExecuteFamilyCode`；**AS-IP-NUR-012 BR-IP-CXL-008**）。麻醉申请（AA）执行入账分码后须与普通医嘱执行同轨 void，避免 Procedure 已 CANCEL、费用仍待结算。
- **边界说明**：按 `procedureId` 定位；长临统一走本方法（不替代 **6.1.4l** 历史临时逆激活）；不替代 **6.1.5**（门诊/草稿回退 `DRAFT`）；**不**删划价/滚费等非执行族来源。
- **入参**：`VoidOrderExecuteChargeItemsByProcedureIdsCommand`（`procedureIds` 集合）。
- **出参**：—。
- **前置条件**：同 `procedureId` 关联行若含 **`SETTLED/REFUNDING/REFUNDED`** 则整批拒绝（`BR-CHG-002`）。
- **状态变化**：命中行逻辑删除（软删）。
- **聚合内影响**：移除执行计费事实，保留审计链。
- **事件输出**：`ChargeItemExecutionCancelledEvent`（逐条）。
- **失败语义**：已结算/退费链路拒绝；无命中行静默跳过。

#### 6.1.5b 按主键集合删除待结算 ORDER_EXECUTE 行（`voidPendingOrderExecuteChargeItemsByChargeItemIds`）

- **业务意义**：**代煎费刷新**（**BR-IP-TCM-008**）执行前 void 同就诊同方同 `occurrence` 未结算代煎费；亦可供应用层按 id 精确删单条执行费。
- **边界说明**：仅删 **`PENDING_SETTLEMENT` + `ORDER_EXECUTE`** 命中行；**不**替代 **6.1.5a**（按 `procedureId` 批量逻辑删）。
- **入参**：`Collection<Long> chargeItemIds`（主键集合，去重后批量 `findAllByIdIn`）。
- **出参**：—。
- **前置条件**：无结算 Gate（调用方 Pre 已收窄 id）；无命中行静默跳过。
- **状态变化**：命中行逻辑删除（软删）。
- **聚合内影响**：移除指定执行计费事实。
- **事件输出**：`ChargeItemExecutionCancelledEvent`（逐条，`deleteAll` 后发布）。
- **失败语义**：无。

#### 6.1.6 收费结算（`settleChargeItems`）

- **业务意义**：确认收费完成并形成财务已收事实。
- **边界说明**：不处理支付通道与对账流水。
- **入参**：`SettleChargeItemsCommand`（含待结算明细集合、结算人/时间等）。
- **出参**：成功结算的明细主键集合。
- **前置条件**：仅 `PLANNED` 可结算（`BR-CHG-002`）；写前须满足 `BR-CHG-QRY-001`。
- **状态变化**：`PLANNED -> SETTLED`。
- **聚合内影响**：批量推进状态；将 `enterer_id`/`entered_date` 写为命令 `operatorId`/`billDate`（收费人与收费时间，与 payment `enterer_id`/`bill_date` 同源，**BR-OPC-016**）；**不再**写 `performer_id` 为收款人。
- **事件输出**：`ChargeItemSettledEvent`（每行或批量，以实现与事件契约为准）。
- **失败语义**：任一行前态不符则整批失败或按实现约定部分失败（须在 本稿 **§6.1** 统一）。

#### 6.1.6a 结算处方号快照盖章（`stampPrescriptionSnapshots`）

- **业务意义**：正式结算前将上游请求处方号抄写到收费行 `prescription_no`（**仅快照**；药品业务真源仍在 `MedicationRequest`）。
- **边界说明**：不改收费状态；应用层（**AS-OP-FIN-002 IF-OPC-07**）在 `settleChargeItems` **之前**编排，从药品请求 / 发放→药品请求 / 诊疗请求解析号后调用。
- **入参**：`StampChargeItemPrescriptionSnapshotCommand`（收费明细主键 + 期望处方号批次）。
- **出参**：—。
- **前置条件**：明细须已装载；期望号为空则跳过该行；已与期望一致则跳过。
- **状态变化**：主状态不变。
- **聚合内影响**：仅更新 `prescription_no`。
- **事件输出**：无（随后结算发 `ChargeItemSettledEvent`）。
- **失败语义**：明细缺失按 `require` 拒绝。

#### 6.1.6b 医技费用确费盖章（`stampMedicalTechFeeConfirmation`）

- **业务意义**：**AS-MT-FEE-001 IF-MT-FEE-03** 一键确费——对**住院**本科相关收费行写入 `fee_confirmer_id` / `fee_confirmed_at`（行级确费，无头表）。**门诊无确费业务**（应用层 Gate 拒 `OUTPATIENT`）。
- **边界说明**：不改收费状态；不写 `enterer_*`；已确费行（`fee_confirmer_id` 非空）跳过（幂等）。**出院门禁不再读本字段**（改读医技执行净态，见协同 DD 2026-09-29 / **BR-IP-ATD-011⑤**）；产品一键确费入口已隐藏，方法保留。
- **入参**：`StampMedicalTechFeeConfirmationCommand`（确费人、确费时刻、收费明细主键列表）。
- **出参**：—。
- **前置条件**：确费人与时刻非空；明细可装载。
- **状态变化**：主状态不变。
- **聚合内影响**：仅更新确费两列。
- **事件输出**：无。
- **失败语义**：确费人/时刻空 → `CHARGE_ITEM_INVALID_ARGUMENT`；明细缺失按 `require` 拒绝。

#### 6.1.7 退号联动退费（`returnRegistrationRefundChargeItems`）

- **业务意义**：退号后联动收口挂号相关费用状态。
- **边界说明**：退款支付与医保回执由外层编排。
- **入参**：`BatchChargeItemIdsCommand`（须含 `operatorId`；`billDate` 可选）。
- **出参**：已收口明细集合。
- **前置条件**：明细处于可退号退费可处理状态（`SETTLED` 或流程允许的 `REFUNDING`）。
- **状态变化**：`SETTLED/REFUNDING -> REFUNDED`（以命令为准）。
- **聚合内影响**：按目标集合批量推进终态并维护 `refundId`（`BR-CHG-005`）；将 `enterer_id`/`entered_date` 写为命令 `operatorId`/`billDate`（退费操作人/退费时刻覆盖收费人戳，**BR-OPC-016**）。
- **事件输出**：`RegistrationChargeItemRefundedEvent`。
- **失败语义**：目标状态不允许退号联动时拒绝。

#### 6.1.8 医保/自费切换（`switchChargeItemAccount`）

- **业务意义**：在未结算阶段将明细切换到正确账户。
- **边界说明**：不负责账户开立与医保登记。
- **入参**：`SwitchChargeItemAccountCommand`（目标账户标识、明细集合）。
- **出参**：更新行数或主键列表。
- **前置条件**：仅 `DRAFT/PLANNED` 可切换（`BR-CHG-004`）。
- **状态变化**：主状态不变。
- **聚合内影响**：批量更新 `accountId`。
- **事件输出**：`ChargeItemAccountSwitchedEvent`。
- **失败语义**：目标账户无效或状态越权时拒绝。

#### 6.1.9 预结算（`preSettleChargeItems`）

- **业务意义**：生成收费前可支付快照，防止金额与状态漂移（**门诊**待收费 `PLANNED`）。
- **边界说明**：不改写收费明细主事实。
- **入参**：`BatchChargeItemIdsCommand`。
- **出参**：无（`void`，由事件驱动下游生成快照）。
- **前置条件**：目标明细须为 `PLANNED`。
- **状态变化**：无（`PLANNED` 保持）。
- **聚合内影响**：仅执行前置状态校验，不改写收费明细状态；快照由下游基于事件生成。
- **事件输出**：`ChargeItemPreSettledEvent`。
- **失败语义**：含非 `PLANNED` 明细时拒绝。

#### 6.1.9b 住院预结算（`preSettleInpatientChargeItems`）

- **业务意义**：住院结算前校验待结算明细并发布预结算事件（**AS-IP-FIN-003** IF-IP-STL-05）；不改写主状态。
- **边界说明**：与门诊 `preSettleChargeItems` 分离；支付草稿由应用层编排 `createPaymentDraftFromPreSettle`。
- **入参**：`BatchChargeItemIdsCommand`。
- **出参**：无（`void`）。
- **前置条件**：目标明细须为 `PENDING_SETTLEMENT`。
- **状态变化**：无（保持 `PENDING_SETTLEMENT`）。
- **聚合内影响**：状态门禁校验后落库确认存在性。
- **事件输出**：`ChargeItemPreSettledEvent`（批次粒度与门诊一致）。
- **失败语义**：含非 `PENDING_SETTLEMENT` 明细时拒绝。

#### 6.1.9a 半退保留链新行落库（`createChargeItemsLinkingRegeneratedAnchors`）

- **业务意义**：在**应用编排**已完成各上游聚合之**正向克隆**并取得新 `service_table`/`service_id`（及可选 `dispense_table`/`dispense_id`）后，由**本聚合**批量插入新 `adm_charge_item` 行，初始 **`PLANNED`**，并将新行 `refund_id` 写回**源已结算行**主键，落实 **BR-CHG-015** 追溯链。
- **边界说明**：**禁止**在本方法内写 `med_medication_request` / `wkf_device_request` / `wkf_service_request` 或发放表；**禁止**以退费/负向语义 API 代替正向克隆；跨聚合写库仅能通过各自 `*DomainService` 的显式命令由外层编排调用。
- **入参**：`CreateChargeItemsLinkingRegeneratedAnchorsCommand`（含 `operatorId`；`items` 每条须含 `sourceChargeItemId`、`newServiceTable`、`newServiceId`，发放锚点成对空或成对非空）。
- **出参**：本批首条新行主键（与既有批量创建返回口径一致）。
- **前置条件**：每条 `sourceChargeItemId` 对应行须为 **`SETTLED`**；`newServiceTable`/`newServiceId` 非空；同批 `sourceChargeItemId` 不得重复。
- **状态变化**：新行 **`PLANNED`**；源行状态**不在本动作内**变更。
- **聚合内影响**：新行金额/生成来源/收费上下文等可对齐字段从源行复刻；锚点取自入参；`refund_id` 指向源行。
- **事件输出**：`PreservedLineRegeneratedChargeItemCreatedEvent`（单条粒度；事件类型码 **8**，与 `ChargeItemEventType` 对齐）。
- **失败语义**：源行非 `SETTLED`、锚点非法、入参重复或违反发放锚点成对规则时拒绝。

#### 6.1.10 门诊按账单申请退费（`requestOutpatientBillRefund`）

- **业务意义**：将账单级退费意图转为可执行明细任务（拟退行进入过程态）；与 **DM-FIN-002** **BR-PMR-017** 协同——**半退**时资金侧整单冲正后，**未选退**行不在本动作内改为 `REFUNDED`，而由后续编排**重开**为 `PLANNED`（**BR-CHG-015**）。
- **边界说明**：支付/医保/票据动作由应用编排；**全退/半退**按各 `paymentId` 下所选行是否覆盖该支付 `charge_item_ids` 快照全集由**后台判定**，非用户混用语义。
- **入参**：`RequestOutpatientBillRefundCommand`。
- **出参**：进入退费流程的明细主键集合。
- **前置条件**：目标明细为 `SETTLED`；退费金额满足 `BR-CHG-011`。
- **状态变化**：`SETTLED -> REFUNDING`。
- **聚合内影响**：批量标记退费中并写退费追溯链路（`BR-CHG-005`）。
- **事件输出**：`OutpatientBillRefundRequestedEvent`。
- **失败语义**：已处于 `REFUNDING` 的重复申请按幂等策略处理。

#### 6.1.11 门诊按账单退费完成（`completeOutpatientBillRefund`）

- **业务意义**：确认门诊退费结果并收口明细状态；拟退行 `REFUNDING`→`REFUNDED`。**半退**时并行产生**重开**的 `PLANNED` 新行（**BR-CHG-015**），由同一用例或紧随其后的应用编排落库，本动作契约以「拟退集合收口 + 事件发布」为主。
- **边界说明**：退款到账确认不在本动作展开；支付主单 `REFUNDED` / `PARTIAL_REFUNDED` 语义见 **DM-FIN-002** §2.3，**不得**与产品「半退」混读。
- **入参**：`BatchChargeItemIdsCommand`（须含 `operatorId`；`billDate` 可选）。
- **出参**：终态明细集合与退费结果摘要。
- **前置条件**：目标明细在 `REFUNDING`。
- **状态变化**：`REFUNDING -> REFUNDED`。
- **聚合内影响**：回写终态并保留追溯关系（`BR-CHG-005`）；将 `enterer_id`/`entered_date` 写为命令 `operatorId`/`billDate`（退费操作人/退费时刻覆盖收费人戳，**BR-OPC-016**）。
- **事件输出**：`OutpatientBillRefundCompletedEvent`。
- **失败语义**：前态非 `REFUNDING` 拒绝；部分成功策略以本稿约定为准。

#### 6.1.12 住院收费结算（`settleInpatientChargeItems`）

- **业务意义**：对住院可结算明细按批次完成收费（**AS-IP-FIN-003** IF-IP-STL-06）。
- **边界说明**：住院预交金抵扣与医保结算由外层编排；入参为 `BatchChargeItemIdsCommand`（与门诊结算命令形态一致，成码复用）。
- **入参**：`BatchChargeItemIdsCommand`（须含 `operatorId`；`billDate` 与 payment `bill_date` 同值）。
- **出参**：无（`void`；事件携带批次 id 列表）。
- **前置条件**：目标明细处于 `PENDING_SETTLEMENT`。
- **状态变化**：`PENDING_SETTLEMENT -> SETTLED`。
- **聚合内影响**：批量推进状态并记录批次追溯；将 `enterer_id`/`entered_date` 写为命令 `operatorId`/`billDate`（收费人/收费时间，与门诊 `settleChargeItems` 同源，**BR-OPC-016**）；**不再**写 `performer_id` 为收款人。
- **事件输出**：`InpatientChargeItemsSettledEvent`。
- **失败语义**：含非 `PENDING_SETTLEMENT` 明细时拒绝；同 `settleChargeItems` 批次策略。

#### 6.1.12a 住院反结再生收费项（`revertInpatientSettledChargeItems`）

- **业务意义**：住院**反结**时，对支付单快照绑定的源 `SETTLED` 行**完整复刻**并插入 `PENDING_SETTLEMENT` 新行，`refund_id` 链回源行；源行**保持 `SETTLED` 终态**供结算历史查阅（**AS-IP-FIN-004** IF-IP-RVS-01 / **ATD-07 `RecallReverseSettlesupport`**，对称 **BR-CHG-015** / **BR-IP-RVS-011**）。
- **边界说明**：仅在本聚合插入再生行并发布事件；支付逆向、就诊/预交金/发票回退由应用层与 **DM-FIN-002** 编排；**禁止**改写源 `SETTLED` 行状态。再生行 `refund_id` 表示**血缘**（数量为正），**不等于**已冲销；其后护士/医技取消划价仍可按 §6.1.3a 对再生正向行插入冲销负向行。
- **入参**：`BatchChargeItemIdsCommand`（源已结算行主键，通常为 `fin_payment_reconciliation.charge_item_ids` 快照子集）。
- **出参**：无（`void`）。
- **前置条件**：目标明细均为 `SETTLED`；同就诊**不存在**已指向源行的有效再生行（`PENDING_SETTLEMENT` + `refund_id`）。
- **状态变化**：源行不变；新行 `N/A → PENDING_SETTLEMENT`，`refund_id = 源行.id`（数量/金额与源同号，为正）。
- **聚合内影响**：新行业务锚点（`service_table/service_id`、`dispense_*`、`procedure_id` 等）与金额字段自源行复刻。
- **事件输出**：`InpatientChargeItemsRevertedEvent`（携带**新行**主键列表）。
- **失败语义**：含非 `SETTLED` 明细或已再生时拒绝。

#### 6.1.13 住院退费申请（`requestInpatientBillRefund`）

- **业务意义**：将住院账单目标明细冻结到退费流程。
- **边界说明**：医保资格校验与退款指令由外层处理。
- **入参**：`RequestInpatientBillRefundCommand`。
- **出参**：进入 `REFUNDING` 的明细集合。
- **前置条件**：目标明细为 `SETTLED`；`BR-CHG-011`。
- **状态变化**：`SETTLED -> REFUNDING`。
- **聚合内影响**：锁定目标明细并写入退费申请追溯信息。
- **事件输出**：`InpatientBillRefundRequestedEvent`。
- **失败语义**：同门诊退费申请。

#### 6.1.14 住院退费完成（`completeInpatientBillRefund`）

- **业务意义**：确认住院退费完成并收口状态。
- **边界说明**：票据冲红与支付回执由外层处理。
- **入参**：`BatchChargeItemIdsCommand`（须含 `operatorId`；`billDate` 可选）。
- **出参**：终态明细集合。
- **前置条件**：目标明细在 `REFUNDING`。
- **状态变化**：`REFUNDING -> REFUNDED`（特定场景可回退至 `PLANNED`，以命令为准）。
- **聚合内影响**：回写退费终态并保留追溯关系；将 `enterer_id`/`entered_date` 写为命令 `operatorId`/`billDate`（退费操作人/退费时刻覆盖收费人戳，**BR-OPC-016**）。
- **事件输出**：`InpatientBillRefundCompletedEvent`。
- **失败语义**：前态不符拒绝；与 `BR-CHG-013` 收口要求一致。

### 6.2 读侧查询服务（ChargeItemQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于写入前校验、UI 回显、列表与资格门控。SQL 与QueryService表名以落库与交互契约为准。

#### 6.2.1 按明细主键批量读取（`loadChargeItemsByIds`）

- **业务意义**：写入前批量确认目标明细当前事实。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_charge_item` 写模型或只读副本。
- **过滤口径**：主键集合；租户与逻辑删除按全局约定过滤。
- **组装规则**：按输入顺序或主键升序返回实体列表（以实现约定为准）。
- **返回约束**：只读实体/DTO；无状态变更；未命中主键不抛业务异常，返回缺行或空列表以落库与交互契约为准。
- **跨模块消费**：`zxplat-module-chs-jilin` bridge `*InsuranceContextLoader` 批量加载 2204 费用明细源数据。

#### 6.2.2 按请求单查询关联明细（`listChargeItemsByServiceIds`）

> **实现名**：Java `ChargeItemQueryService#listChargeItemsByServiceIds`（持久化列 `service_table` + `service_id`；历史文档曾写作 `listChargeItemsByRequestIds`）。

- **业务意义**：支撑医嘱撤销、执行回退的明细追溯。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：写模型或QueryService中可关联到请求单的明细行。
- **过滤口径**：请求单标识集合；可选 `encounterId` 收敛同就诊；可选 `excludeSupersededSettledLines=true` 排除已被 `PLANNED`/`PENDING_SETTLEMENT` 再生替代的源 `SETTLED` 行（**BR-REF-020** / **BR-IP-RVS-011**）。
- **组装规则**：默认平铺列表；不展开医嘱全文。
- **返回约束**：仅用于追溯与校验，不作为结算唯一依据（须配合状态机）。

#### 6.2.3 按就诊查询收费明细（`listChargeItemsByEncounterId`）

- **业务意义**：提供就诊维度待收费与已收费明细视图。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`encounter_id` 命中行。
- **过滤口径**：单次就诊标识；可附加账户筛选（见 API）；**可选** `ChargeItemQueryCommand.statusEnumIn`：非空时在 JPA `Specification` / 仓储组合查询中下推 `status_enum in (...)`（AS-OP-FIN-002 **BR-OPC-010**：门诊收银 IF-OPC-03/04/05 固定为 `PLANNED`、`SETTLED`，不含 `DRAFT`）；**可选** `ChargeItemQueryCommand.excludedServiceTable`：非空时下推 `service_table <> excludedServiceTable`（**BR-OPC-013**，排除 `adm_healthcare_service` 挂号费锚点）；**可选** `ChargeItemQueryCommand.excludeSupersededSettledLines`：为真时于查询后调用 `ChargeItemRegenerationChainSupport` 按 **BR-REF-020** / **BR-IP-RVS-011** **递归**排除链上已被有效再生叶替代的祖先 `SETTLED` 行（支持多轮结算/反结多层 `refund_id` 链；**非**仅单层直接子行）。
- **组装规则**：列表视图字段遵循 `BR-CHG-QRY-003` 金额展示优先级。
- **返回约束**：大数据量列表应分页或由QueryService承载（`BR-CHG-QRY-002`）。

#### 6.2.4 费用明细视图查询（`queryCostDetails` / `queryInpatientCostDetails`）

- **业务意义**：提供门诊/住院费用明细与汇总回显。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：领域事件后查询刷新为主（`BR-CHG-QRY-002`）。
- **过滤口径**：时间区间、科室、费别、收费状态等由查询对象携带；与 §6.2.3 一致：若命令携带 `statusEnumIn` / `excludedServiceTable` / `contextEnum`，则在写模型仓储 `Specification` 中下推；**可选** `excludeSupersededSettledLines`：为真时于查询后调用 `ChargeItemRegenerationChainSupport` 按 **BR-REF-020** / **BR-IP-RVS-011** **递归**排除链上已被有效再生叶替代的祖先 `SETTLED` 行（多轮结算/反结后「已结算」筛选不再展示已被对冲的历史行；**AS-IP-NUR-006** 经 §6.2.15 Loader 固定为真）。
- **组装规则**：必要时组合QueryService与主档；金额以 `BR-CHG-QRY-003` 为准。
- **返回约束**：只读视图 DTO；不写回收费明细。

#### 6.2.5 计时项目累计用量查询（`queryOxygenAndECGDuration`）

> **成码状态（2026-05-28）**：`ChargeItemQueryService` **尚无**同名 public 方法；**BR-IP-PRC-010** 当日上限 Gate 待接通或改由 `ServiceRequestQueryService` 汇总实现后再关闭本节。
- **边界说明**：只读查询，不写库，不做跨域编排；**不**汇总 `adm_charge_item.total_price`，**不**经 ChargeItem 表聚合。
- **查询范围（读库，无写入）**：`wkf_service_request` 主表（`ServiceRequestRepository#findByEncounterId`）。
- **入参**：`ChargeItemQueryCommand`——**必填** `encounterId`、`activityDefinitionId`；**可选** `occurrenceDate`（`LocalDate`），非空时仅统计该自然日内的请求行。
- **过滤口径**：`encounter_id` 命中；`activity_id = activityDefinitionId`；排除 `is_deleted=true`；`occurrenceDate` 非空时，以 `occurrence_start_time` 优先、否则 `authored_time`、再否则 `created_at` 的**本地日期**等于 `occurrenceDate`。
- **组装规则**：对命中行的 `quantity`（小时数，`BigDecimal`）求和后 **向下取整** 为 `Long` 返回；无匹配行返回 `0`。
- **返回约束**：返回累计小时数；应用层将返回值与本次拟写入 `quantity` 相加后与当日/在院上限比较（吸氧 20h、心电 24h 等常量由应用/共享常量承载）。

#### 6.2.6 查询收费项及定价关联（`listChargeItemDefInfoWithChildren`）

- **业务意义**：返回收费明细、定价与组套子项关联信息。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：收费明细多行 + 定价主数据；组套子项为 `**bus_no` 子行**或 `parent_id` 关联行。
- **过滤口径**：明细主键或就诊维度查询入口；组套按父行 `bus_no` 前缀或 `parent_id` 归组。
- **组装规则**：将父行与子行组装为组套树用于打印与审计追溯；与 `ChargeItemDefinition` 联查以落库与交互契约为准。
- **返回约束**：打印/审计专用；历史行以持久化时的多行事实为准。

#### 6.2.7 按结算单读取明细（`listChargeItemsByPaymentBill`）

- **业务意义**：支撑账单详情与门诊按账单退费入口。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：支付/结算单与明细关联表或QueryService。
- **过滤口径**：结算单号/支付主单号（见 API）。
- **组装规则**：按账单行序或收费时间排序（以落库与交互契约为准）。
- **返回约束**：退费前须再次校验状态（`BR-CHG-QRY-001`）。

#### 6.2.8 按开票场景读取明细（`listChargeItemsForInvoice`）

- **业务意义**：为开票、补票、重开票提供收费明细快照。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：已结算且符合开票条件的明细子集。
- **过滤口径**：开票场景编码、结算关系、机构（见 API）。
- **组装规则**：与 `DM-ADM-012` 开票要求对齐的字段投影。
- **返回约束**：只读快照；重复开票控制由应用与票据域负责。

#### 6.2.9 按住院结算单读取明细（`listChargeItemsByInpatientBill`）

- **业务意义**：支撑住院收费明细展示与住院退费入口。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：住院结算单与明细关联QueryService。
- **过滤口径**：住院结算单标识、批次号（见 API）。
- **组装规则**：可按费别、科室分组返回（以落库与交互契约为准）。
- **返回约束**：退费联动前须与 `requestInpatientBillRefund` 目标集合一致。

#### 6.2.10 门诊收银队列就诊分页（`pageEncounterIdsForOutpatientCashierQueue`）

> **落点（2026-05-28）**：对外读编排由应用层 **`OutpatientChargeEncounterQueueLoader`** 承担；领域层底层锚点为 `ChargeItemQueryService#pageEncounterIdsForOutpatientChargeQueue`（与本文方法语义等价，命名以实现为准）。

- **业务意义**：AS-OP-FIN-002 IF-OPC-02：仅将「存在至少一条符合收银口径收费明细」的就诊纳入分页候选（**BR-OPC-014**）。
- **边界说明**：只读；**仅查 `adm_charge_item`**，就诊/患者/机构/关键字收窄由应用层 **`OutpatientChargeEncounterQueueLoader`** 预解析 **`resolvedEncounterScope`** 后本方法 `encounter_id IN (...)`；**禁止** Criteria EXISTS `adm_encounter`。展示列回填由 `EncounterContextReader` 编排（**DM-ADM-002 §6.2.18**）。
- **查询范围**：入参 `OutpatientChargeQueueEncounterQuery` **extends `AbstractPageQuery`**（`keyword`、`startDate`、`endDate`、`pageNum`、`pageSize`）、业务字段 **`excludedServiceTable`**、**`resolvedEncounterScope`**（Loader 写入；空集合 → 空页）；**不含** `organizationId`（机构收窄在 Loader 经 **`EncounterQueryService#listOutpatientAmbEncounterIdsForCashierScope`** 完成）。
- **过滤口径**：`status_enum in (PLANNED, SETTLED)` 且 `service_table <> excludedServiceTable`（典型排除 `adm_healthcare_service`）；租户/软删按全局约定。
- **组装规则**：按就诊聚合后分页返回 `encounter_id`；排序以实现约定为准（如按就诊内最近命中明细业务时刻倒序）。
- **返回约束**：`Page<Long>`；无写库。

#### 6.2.11 门诊收银多就诊明细批量（`listOutpatientCashierChargeItemsForEncounters`）

- **业务意义**：为队列行挂载收费明细摘要（与 IF-OPC-03 列表同口径），避免前端对每条就诊重复拉明细。
- **边界说明**：只读；批量查询多就诊下明细行。
- **过滤口径**：与 §6.2.10 同一收银状态与白名单、同一 `excludedServiceTable` 下推。
- **返回约束**：`List<ChargeItem>` 扁平列表；调用方按 `encounterId` 分组装配 DTO。

#### 6.2.12 门诊退费队列就诊分页（`pageEncounterIdsForOutpatientRefundQueue`）

> **落点（2026-05-28）**：对外读编排由应用层 **`OutpatientRefundEncounterQueueLoader`** 承担；领域层底层锚点以实现为准（与收银队列同族 Criteria 查询）。

- **业务意义**：AS-OP-FIN-003 IF-REF-02：仅将「存在至少一条拟退（`REFUNDING`）收费明细」的就诊纳入分页候选（**BR-REF-001**）。
- **边界说明**：只读；与 §6.2.10 同构（**`resolvedEncounterScope` + 单表 `adm_charge_item`**）；入参复用 `OutpatientChargeQueueEncounterQuery`；机构/关键字预解析仍由 **`OutpatientChargeEncounterQueueLoader`** 承担。
- **过滤口径**：`status_enum = REFUNDING` 且 `service_table <> excludedServiceTable`（典型排除 `adm_healthcare_service`）；租户/软删按全局约定。
- **组装规则**：按就诊聚合后分页返回 `encounter_id`；按就诊内最近命中明细 `occurrence_time` 倒序。
- **返回约束**：`Page<Long>`；无写库。

#### 6.2.13 单就诊待结算总价（`sumPendingSettlementTotalPriceByEncounterId`）

- **业务意义**：住院展示余额公式中的 **待结算合计**（**AS-IP-FIN-002** / **AS-IP-NUR-001** **BR-IP-NUR-006**）组成部分；**不含** `PLANNED` 待收费。
- **边界说明**：只读；`encounterId` 为 null 时返回 **0**。
- **过滤口径**：`adm_charge_item.encounter_id = ?` 且 `status_enum = PENDING_SETTLEMENT`（持久化 `code`）；**且 `parent_id IS NULL`**（院内合计只计父行，带子项诊疗子账单不参与，§9.1）；软删按全局 `isDeleted=false`。
- **实现落点**：`ChargeItemQueryService` 内 Criteria **`SUM(total_price)`**（与 §6.2.10 同用 `EntityManager`）；**禁止**仓储派生 `sumTotalPriceBy…`（解析失败）及先 `findAll…` 再内存加总。

#### 6.2.13a 单就诊已结算合计（`sumSettledTotalPriceByEncounterId`）

- **业务意义**：批量汇总 **`SETTLED`** 费用合计；用于 **`InpatientAccountBalanceDisplayAssembler`**「已结清 XX 元」主文案及历史统计；**不**参与展示可用余额公式（已结金额在结算成功时已写入 `Account.balance`）。
- **过滤口径**：`encounter_id = ?` 且 `status_enum = SETTLED`；**且 `parent_id IS NULL`**（与 §6.2.13 院内合计同口径）。
- **实现落点**：与 §6.2.13 同构 Criteria `SUM`。
- **出院多退（BR-IP-STL-014）**：正式结算须先按患者应付扣减账户——自费 **`balance −= 本批费用合计`**，医保 **`balance −= 个人现金`**——再按扣减后库内余额退盈余；**禁止**再用 `prepayNet − SUM(SETTLED)` 推算多退额（对齐 **AS-IP-FIN-003** v1.19）。

#### 6.2.14 批量待结算总价（`mapPendingSettlementTotalPriceByEncounterIds`）

- **业务意义**：当前页/批量 `encounterId` **一次**汇总待结算 `total_price`；供 **`InpatientDisplayAccountBalanceAssembler#computeDisplayAccountBalance`**（**库内余额 − 待结算**）；避免 N+1。
- **边界说明**：只读；入参去重；无匹配明细的就诊 **不出现在 Map**（调用方按缺失视为 0）。
- **过滤口径**：`encounter_id IN (…)` 且 `status_enum = PENDING_SETTLEMENT`；**且 `parent_id IS NULL`**（与 §6.2.13 同口径）。
- **实现落点**：Criteria **`GROUP BY encounter_id`** + **`SUM(total_price)`**；与 §6.2.13 共用同一私有分组查询；**禁止** `findAllByEncounterIdInAndStatusEnumIn` 拉全表行再累加。

#### 6.2.14a 批量已结算合计（`mapSettledTotalPriceByEncounterIds` / `mapInpatientChargedOccupancyTotalPriceByEncounterIds`）

- **业务意义**：`mapSettledTotalPriceByEncounterIds` 批量汇总 **已结算**合计（展示文案）；`mapInpatientChargedOccupancyTotalPriceByEncounterIds` 汇总 **`PENDING_SETTLEMENT` + `SETTLED`**，**遗留内部汇总**，**禁止**再用于展示可用余额（展示见 §6.2.13 / `InpatientDisplayAccountBalanceAssembler`）。
- **过滤口径**：`mapSettled`：`status_enum = SETTLED`；`mapInpatientChargedOccupancy`：`status_enum IN (PENDING_SETTLEMENT, SETTLED)`；均 **`parent_id IS NULL`** + `GROUP BY encounter_id`。

#### 6.2.16 有待结算就诊主键列表（`listEncounterIdsWithPendingSettlementCharges`）

- **业务意义**：住院结算队列「有待结算」页签：存在 ≥1 条 `PENDING_SETTLEMENT` 的就诊 id 列表（**AS-IP-FIN-003** / **Q-IPI-007**）。
- **查询范围**：`adm_charge_item`，`status_enum = PENDING_SETTLEMENT`，`is_deleted = false`，`DISTINCT encounter_id`。
- **返回约束**：无匹配时空列表；**禁止**在应用层全表拉明细再分组。

#### 6.2.15 住院护士站费用明细候选行（`listInpatientNurseStationCostDetailChargeRows`）

> **落点（2026-05-28）**：对外读编排由应用层 **`InpatientNurseStationCostDetailLoader`** 承担；领域层仍提供 Procedure 净执行时间等窄查询供其组装。

- **业务意义**：**AS-IP-NUR-006** IF-IP-CDT-02：单就诊 + 业务日期闭区间内的费用明细事实，供应用层组装明细/汇总 Tab；**不**经 `InpatientChargeDetail` QueryService。
- **边界说明**：只读；`ChargeItemQueryService` 编排 `ProcedureQueryService#mapNetCompletedOccurrenceTimeByEncounterId`（净剩 COMPLETED，口径同 **DM-CLN-004** §6.2.6）。
- **入参**：`ChargeItemQueryCommand`——**必填** `encounterId`、`businessDateFrom`、`businessDateTo`（`LocalDate`）；**可选** `statusEnumIn`（缺省时固定为 `PENDING_SETTLEMENT`/`SETTLED`/`REFUNDING`/`REFUNDED`）；**可选** `excludedServiceTable`（缺省时 `adm_healthcare_service`）；**固定** `excludeSupersededSettledLines=true`（**BR-REF-020** / **BR-IP-RVS-011**，排除反结再生链上历史 `SETTLED`）。
- **过滤口径（下推）**：`encounter_id`；`status_enum IN (...)`；`service_table <> excludedServiceTable`（与 §6.2.3 同构 `Specification`）。
- **业务时间（内存，净执行）**：
  - `generate_source_enum = nurse-pricing` → `adm_charge_item.occurrence_time`；
  - 其余 → `procedure_id` 须命中就诊下**净剩** `COMPLETED` 的 `cln_procedure.occurrence_time`（已被 `CANCEL`+`refund_id` 对冲的 COMPLETED **不得**作为业务时间）；
  - 业务日期 = 上述时刻的本地自然日，须 ∈ `[businessDateFrom, businessDateTo]`；
  - **Procedure 已对冲且无法解析业务时间**：若账单 `occurrence_time` 的本地日 **不在**查询闭区间，该行**跳过**（不进入结果集）；若在闭区间内 → 抛 `IllegalArgumentException`（**BR-IP-CDT-016**）。
- **组装规则**：返回 `List<InpatientNurseStationCostDetailChargeRow>`（`chargeItem` + `businessDateTime`）；按 `businessDateTime` **倒序**；**结果集内**无法解析业务时间 → 抛 `IllegalArgumentException`（应用层映射 EX-IP-CDT-203）。
- **返回约束**：区间内无命中返回空列表；禁止应用层对全就诊 ChargeItem 做状态/挂号/日期三重内存过滤替代本方法。

#### 6.2.17 半退/反结：过滤已被替代的已结行（`filterOutSupersededSettledLines`）

- **业务意义**：**BR-REF-020** / **BR-IP-RVS-011**：读侧排除已被有效再生叶替代的祖先 `SETTLED` 行。
- **实现**：`ChargeItemRegenerationChainSupport#collectSupersededSettledAncestorIds` — 自链末端 `PLANNED`/`PENDING_SETTLEMENT` 叶沿 `refund_id` **向上递归**收集应忽略的祖先主键；`ChargeItemQueryService#filterOutSupersededSettledLines` 据此过滤入参列表。
- **入参**：`encounterId`；`List<ChargeItem> rows`。
- **返回约束**：过滤后的新列表；不改变库。

#### 6.2.18 半退/反结：源行到链末端再生叶映射（`mapPreservedNewChargeItemIdBySourceId`）

- **业务意义**：半退保留链或**多轮**住院反结完成后，按源 `SETTLED` 行 id 解析链末端有效再生叶 id（`PLANNED` 或 `PENDING_SETTLEMENT`）。
- **实现**：`ChargeItemRegenerationChainSupport#mapSettledAncestorToActiveRegenerationLeafId`。
- **入参**：`encounterId`；`sourceChargeItemIds` 集合。
- **返回约束**：`Map<Long, Long>`（祖先 id → 链末端再生叶 id）；无有效叶时不出现在 Map 中。

#### 6.2.19 门诊退费完成：解析拟结明细 id 集合（`resolveChargeItemIdsForOutpatientRefundBillCompletion`）

- **业务意义**：**AS-OP-FIN-003** IF-REF-05/06 正式退费前，解析本次账单应结的 `chargeItemId` 集合（含半退替代行规则）。
- **边界说明**：只读；与 **BR-REF-020**、支付重叠校验联动。

#### 6.2.20 收银/退费队列状态白名单（`outpatientCashierBillableStatusCodes` / `outpatientRefundQueueStatusCodes`）

- **业务意义**：集中返回门诊收银/退费队列 SQL 使用的 `status_enum` code 列表（**BR-OPC-010** / **BR-REF-001**）。
- **返回约束**：`List<String>`；与 `ChargeItemStatus` 持久化 code 一致。

#### 6.2.21 发药划价读侧（`listChargeItemsForMedicationDispensePricing`）

- **业务意义**：门诊/住院发药按 `dispense_id` 集合加载待划价/待收费费用行。
- **入参**：`encounterId`；`dispenseIds` 集合。

#### 6.2.22 多就诊退费关联明细（`listRefundRelatedChargeItemsByEncounterIds`）

- **业务意义**：退费队列批量挂载各就诊下 `REFUNDING` 等相关行。
- **入参**：`encounterIds` 集合。

#### 6.2.23 按执行记录批量查询收费明细（`listChargeItemsByProcedureIds`）

- **业务意义**：**AS-IP-NUR-012** 取消执行 Gate 与 Charge 分轨定位：按 `procedure_id` 集合加载关联明细。
- **边界说明**：只读；`ChargeItemRepository#findAllByProcedureIdIn` 下推；排除逻辑删除。
- **入参**：`Collection<Long> procedureIds`。
- **过滤口径**：`procedure_id IN (...)`；空集合返回空列表。
- **组装规则**：返回写模型实体列表；应用层按状态/`generate_source_enum` 分轨调用写命令。
- **返回约束**：只读；不修改聚合状态。

#### 6.2.24 滚费幂等探测（`existsAutoRollChargeItemForBillDate`）

- **业务意义**：**AS-IP-NUR-013** BR-IP-ARG-010：同 `billDate + encounterId + chargeDefinitionId` 是否已有 `auto-roll-fees` 收费行。
- **边界说明**：只读；匹配 `adm_charge_item.definition_id` + `generate_source_enum=auto-roll-fees`；`occurrence_time ∈ [billDate 00:00, billDate+1 00:00)`；排除软删。
- **入参**：`encounterId`、`chargeDefinitionId`、`billDate`；任一为空返回 `false`（不抛错）。
- **返回约束**：`boolean`。

#### 6.2.25 门诊费用明细就诊块分页（`pageOutpatientFeeDetailEncounterBlocks`）

> **落点（2026-06-21）**：payment/就诊/患者/项目 scope 预解析由 **`OutpatientFeeDetailQuerySupport#prepareOutpatientFeeDetailQueryScopes`**（`application.outpatient.financial.charge.support`）承担；本方法在已解析 scope 内组装就诊块分页。

- **业务意义**：**AS-OP-FIN-005** IF-OP-FDT-02/03：按就诊分组分页返回明细候选行（块内全量、块间按最新 `bill_date` 倒序）；**不**复用 §6.2.4 `queryCostDetails`（单就诊视图）。
- **边界说明**：只读；须先写入 `OutpatientFeeDetailPageQuery.resolvedPaymentChargeItemLinks` / `resolvedChargeItemScope` / `resolvedEncounterScope` 与 `paymentChargeItemLinks`（**DM-FIN-002** §6.2.17～6.2.18）。
- **入参**：`OutpatientFeeDetailPageQuery`——**必填** `billDateFrom`/`billDateTo`（`LocalDateTime` 闭区间；由应用层自 Request 时间窗转换：`yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss`，仅日期时起补 `00:00:00`、止补 `23:59:59`）；**必填** scope 字段（由 Support 预载）；**可选** `pageNo`（1-based，缺省 1）、`pageSize`（缺省 **10**，见 `OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE`）；**可选** `feeCategoryCodes`（大类，**BR-OP-FDT-006**）。
- **过滤口径（内存，scope 内）**：`status_enum IN (SETTLED, REFUNDED)`；`encounter_id ∈ resolvedEncounterScope`；大类映射命中；须存在 payment 快照关联（`resolvedPaymentChargeItemLinks`）。
- **组装规则**：按 `resolvedPaymentChargeItemLinks` 逐条展开明细行（同 chargeItem 可收退各一行）；按 `encounter_id` 分组；块内行按 payment `bill_date` **倒序**；块顺序按块内最大 `bill_date` **倒序**；返回 `Page<OutpatientFeeDetailEncounterBlockQueryModel>`（`encounterId`/`patientId`/`lines`）。
- **返回约束**：scope 为空时返回空页；禁止应用层全表拉 ChargeItem 再内存分页替代本方法。

#### 6.2.26 门诊费用明细全量金额代数和（`summarizeOutpatientFeeDetailAmounts`）

- **业务意义**：**AS-OP-FIN-005** IF-OP-FDT-02：对**全量命中集**（非当前页）汇总原价/应收/折后/行金额/优惠差值；实收/医保/自费由应用层在相同 scope 上叠加 **BR-OP-FDT-010～012**。
- **边界说明**：只读；与 **§6.2.25** 共用 `listOutpatientFeeDetailChargeRows` 筛选链；**不**含退单负号与医保分摊（域层仅账单事实代数）。
- **入参**：同 **§6.2.25** 的 `OutpatientFeeDetailPageQuery`（忽略分页键）。
- **出参**：`OutpatientFeeDetailAmountSummaryQueryModel`（`baseAmountTotal`/`discountAmountTotal`/`receivableAmountTotal`/`discountedAmountTotal`/`lineAmountTotal`）。
- **返回约束**：无命中时各合计为 **0**；与分页查询须复用同一 scope 预解析，避免二次不一致筛选。

#### 6.2.27 工作量 fin_type 汇总（`summarizePractitionerWorkloadByFinType`）

> **落点（2026-07-12）**：scope 预解析由 **`PractitionerWorkloadQuerySupport#preparePractitionerWorkloadQueryScopes`**（`health-application/.../application/report/practitionerworkload/support`）承担；payment 筛选见 **DM-FIN-002** §6.2.19。

- **业务意义**：**AS-RPT-001** IF-RPT-WKL-02：在已结算/已退费 scope 内，按 **`requesting_org_id` + `definition_id`** 聚合账单应收/折后/行金额；**fin_type_code**、实收/成本/净收入由应用层映射（**DailySettlementFinTypeAmountSupport** 等）。**未选科室**时应用层按 **`fin_type_code` 跨 org 合并**（**禁止**按科室分块展示）。
- **边界说明**：只读；须先写入 `PractitionerWorkloadPageQuery.resolvedPaymentChargeItemLinks`、`resolvedOrganizationIds`、`resolvedEncounterScope` 等。
- **入参**：`PractitionerWorkloadPageQuery`——**必填** `billDateFrom`/`billDateTo`、`encounterScope`；**可选** `organizationId`、`practitionerId`（费用 Request `requester_id` 预筛；挂号 chargeItem 走 `REGISTRATION_DOCTOR`）。
- **过滤口径**：`status_enum ∈ {SETTLED, REFUNDED}`；`encounter_id ∈ resolvedEncounterScope`；费用 **`requesting_org_id ∈ resolvedOrganizationIds`**；须存在 payment 快照关联。
- **出参**：`List<PractitionerWorkloadFinTypeOrgSummaryQueryModel>`。
- **返回约束**：无命中返回空列表；**禁止**应用层全表拉 ChargeItem 再内存聚合替代本方法。

#### 6.2.28 工作量医嘱明细分页（`pagePractitionerWorkloadDetailByRequest`）

- **业务意义**：**AS-RPT-001** IF-RPT-WKL-03/04：按 **payment–chargeItem 链路**展开明细行分页（**一行 = `chargeItemId + paymentId`**）；**不含**挂号 chargeItem。
- **边界说明**：只读；与 **§6.2.27** 共用 `listPractitionerWorkloadChargeRows`（`registrationOnlyMode=false`）。领域读模型 `entererId`/`enteredDate` 字段名保留，**值由应用层自 Request `requester_id` / `req_opened_time` 回填**（非 ChargeItem.`enterer_*`；该列已为收费人/收费时间，**BR-OPC-016**）。
- **入参**：同 **§6.2.27** + **可选** `pageNo`（1-based，缺省 1）、`pageSize`（缺省 **20**，见 `PractitionerWorkloadConstants.DEFAULT_PAGE_SIZE`）；选医生时费用侧预筛 Request **`requester_id`**（**BR-RPT-WKL-005**）。
- **排序**：应用层 enrich 后按 Request **`req_opened_time` DESC**（读模型字段可仍名 `enteredDate`；**BR-RPT-WKL-012**）；**非** ChargeItem.`entered_date`。
- **出参**：`Page<PractitionerWorkloadRequestDetailQueryModel>`（含 `requestingOrgId`；`entererId`/`enteredDate` 语义见边界说明）。

#### 6.2.29 工作量挂号号源净人次（`summarizeRegistrationChargesByServiceType`）

- **业务意义**：**AS-RPT-001** IF-RPT-WKL-02 下半区：挂号 **净人次**按 **`Encounter.organization_id` + `service_type_id`** 分组；口径对齐 **AS-RPT-007**：同一 `encounter_id` 只计 1 次；有效挂号 +1，`CANCELLED`/`PLANNED`/`ENTERED_IN_ERROR` 不计入（净 = 挂 − 退）；**禁止**按收费/退费 payment 链路重复计次。**未选科室**时应用层按 **`service_type_id` 跨 org 合并**。
- **边界说明**：只读；挂号 chargeItem 识别：`generate_source_enum = registration` 或 `context_enum = 4`（REGISTER）；科室收窄用 **挂号科室**（非 `requesting_org_id`）。
- **入参**：同 **§6.2.27**；须含 **`encounterContextByEncounterId`**（Encounter §6.2.31）。
- **出参**：`List<PractitionerWorkloadRegistrationOrgSummaryQueryModel>`。

#### 6.2.30 工作量 scope 候选行（`listPractitionerWorkloadScopeChargeRows`）

- **业务意义**：**AS-RPT-001** IF-RPT-WKL-02～04：在 payment 预解析 scope 内加载并过滤工作量候选 **chargeItem 行**；作为 fin_type 汇总、成本 enrich、外显指标统计的**统一 scope 源**（**BR-RPT-WKL-018**）。
- **边界说明**：只读；须先写入 `PractitionerWorkloadPageQuery.resolvedPaymentChargeItemLinks` 等；**禁止**应用层重复实现不同过滤口径。
- **入参**：`PractitionerWorkloadPageQuery`；`registrationOnlyMode`——`true` 挂号净人次分支，`false` fin_type/明细/overview 分支。
- **出参**：`List<PractitionerWorkloadChargeRowQueryModel>`；scope 为空时返回空列表。
- **实现别名**：公开方法委托内部 `listPractitionerWorkloadChargeRows`。

#### 6.2.31 工作量外显指标汇总（`summarizePractitionerWorkloadOverview`）

- **业务意义**：**AS-RPT-001** IF-RPT-WKL-02：**`summaryTotals`** 外显三指标——**接诊人数**（distinct `encounter_id`）、**处方数量**（**仅药品**；按结算快照或应用层已用 `MedicationRequest.prescription_no` 覆盖后的 `ChargeItem.prescription_no` 去重，同方多行计 1；仍无号再回落 `service_id`）、**诊疗项目总数量**（distinct `wkf_service_request` + `wkf_device_request`）（**BR-RPT-WKL-019**）。
- **边界说明**：只读；入参为 **§6.2.30** 已过滤候选行；**排除**挂号 chargeItem 参与处方/诊疗项目计数。
- **入参**：`List<PractitionerWorkloadChargeRowQueryModel>`。
- **出参**：`PractitionerWorkloadOverviewSummaryQueryModel`（`encounterCountTotal` / `prescriptionCountTotal` / `activityItemCountTotal`；无命中各为 **0**）。

#### 6.2.32 门诊费用明细按 fin_type 净汇总（`summarizeOutpatientFeeDetailByFinType`）

- **业务意义**：**AS-RPT-008** 汇总 Tab：与门诊费用明细同 payment scope，按 **`definition_id`** 代数汇总应收/折后/行金额（含退费负向）；`fin_type_code` 由应用层 enrich。
- **边界说明**：只读；须先写入 `OutpatientFeeDetailPageQuery.resolvedPaymentChargeItemLinks`。
- **出参**：`List<FeeDetailFinTypeSummaryQueryModel>`；无命中空列表。
- **实现锚点**：`ChargeItemQueryService#summarizeOutpatientFeeDetailByFinType`。

#### 6.2.33 住院费用明细就诊块分页（`pageInpatientFeeDetailEncounterBlocks`）

- **业务意义**：**AS-RPT-009** 明细 Tab：与 **§6.2.25** 同构，由 Support 写入 **IMP** 就诊 scope 与 payment 快照后调用。
- **实现锚点**：`ChargeItemQueryService#pageInpatientFeeDetailEncounterBlocks`（委托同构门诊分页实现）。

#### 6.2.34 住院费用明细按 fin_type 净汇总（`summarizeInpatientFeeDetailByFinType`）

- **业务意义**：**AS-RPT-009** 汇总 Tab；口径同 **§6.2.32**。
- **实现锚点**：`ChargeItemQueryService#summarizeInpatientFeeDetailByFinType`。

#### 6.2.35 重点药品使用统计汇总（`summarizeKeyMedicationUsage`）

- **业务意义**：**AS-RPT-011**：在已写入的费用明细 payment/账单 scope 上，仅保留 `productTable = med_medication_definition` 且 `productId ∈` 入参药典主键集合的行；按 `encounter_id` 对带收退符号的售价（`total_price`）代数求和；**净人次** = 净售价 **> 0** 的 distinct 就诊数；**售价净额** = 全部命中行代数和（含负向）。
- **边界说明**：只读；仅本聚合 `ChargeItem`；药典主键由应用层经 `MedicationDefinitionQueryService#listIdsByKeyDrugType` 预解析后传入，**禁止**本 QS JOIN 药典表。入参 `productDefinitionIds` 为空时直接返回人次 0、金额 0。
- **出参**：`KeyMedicationUsageAggQueryModel`（`encounterNetCount`、`saleAmountTotal`）。
- **实现锚点**：`ChargeItemQueryService#summarizeKeyMedicationUsage`。

#### 查询与QueryService规则


| 编号   | 规则 ID            | 规则描述                         | 约束/落地要点          |
| ---- | ---------------- | ---------------------------- | ---------------- |
| R-01 | `BR-CHG-QRY-001` | 写入前先查当前明细状态与关联主档             | 避免跳过状态判断直接更新数据库  |
| R-02 | `BR-CHG-QRY-002` | 费用列表、住院汇总、报表优先走QueryService           | 避免在写模型上叠加复杂联表    |
| R-03 | `BR-CHG-QRY-003` | 查询口径应显式声明金额优先级（人工调价/系统优惠/原价） | 保证收费台、住院端、报表口径一致 |


### 6.3 领域边界说明

- `ChargeItem` 领域动作只描述收费明细聚合内的业务行为，不展开支付渠道、医保平台、发票平台等外部系统流程。
- 产品口语中的"收费""退费""退号退费"通常是应用服务用例，需要编排账户、支付、对账、就诊等多个领域；本模型只保留收费明细最小业务动作。
- 查询类动作用于写入前校验或聚合内只读能力；报表和大屏走QueryService，不在领域模型中展开联表实现。
- 发药、退药、发放单汇总、库存扣减等属于药域主职责；本域只承接其结果对收费状态的影响，不展开药域内流程细节。
- **实现落点（命名与分包）**：写侧编排类为 `ChargeItemDomainService`，读侧编排类为 `ChargeItemQueryService`，均位于 `administration 领域服务包`；领域命令置于 `administration.command.chargeitem`，只读查询入参与预结算结果置于 `administration.query`；持久化锚点不变量由聚合根 `ChargeItem` 实例方法承载。

---

## 7. 不变式


| 编号           | 不变式                                                             | 约束/落地要点                                                                 |
| ------------ | --------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `BR-CHG-001` | 收费明细必须可追溯到业务来源与计价来源（`serviceTable+serviceId`、`definitionId`）    | 创建明细时必须完整设置关联锚点。**门诊药耗（签发分段后）**：`service_*`＝医嘱请求，`product_*`＝定义，`dispense_*`＝发放分段行（见 §3.1 表下说明）；不得以「仅用 `service_*` 指向发放表」替代 `dispense_*` 语义                                                         |
| `BR-CHG-002` | 收费状态迁移遵循生命周期约束；终态（`REFUNDED`）后不得逆向回退为未收费态 | 状态迁移动作统一校验                                                              |
| `BR-CHG-003` | 同语义重复提交应具备幂等性（例如重复置相同状态）                                        | 写侧动作应识别「目标状态已达成」场景                                                      |
| `BR-CHG-004` | 账户切换仅允许影响未完成收费/结算的明细                                            | 仅作用 `DRAFT`/`PLANNED`                                        |
| `BR-CHG-005` | 退费链路必须可追溯，需保留退费关联信息（`refundId`）                                 | 退费动作写入关联标识并保留明细追溯                                                       |
| `BR-CHG-006` | 业务撤销优先使用状态表达，避免直接物理删除破坏审计                                       | 物理删除仅可在受控窗口使用                                                           |
| `BR-CHG-007` | 写操作前应先按就诊或明细主键确认当前状态，再执行变更                                      | 采用「先查再改」编排                                                              |
| `BR-CHG-008` | 金额计算必须符合定价规则                                                    | **`quantity_value`/`quantity_unit`/`unit_price`/`total_price`** 由 **`ChargeItemDefinition.price_value`** 与业务 **`quantity`/`unit_code`** 经 **`ProductDefinitionCatalogUnitBandsAssembler`** 单位槽比例换算（医嘱开立/护士执行见 **`ProductDefinitionOrderChargePricingsupport**）；**禁止**默认 1/「次」或整价直乘；折后金额 = 原价 - 系统优惠 - 人工调价 |
| `BR-CHG-016` | **暂存草稿择价库位透传**                                                    | 医生站 IF-DOR-14 / IF-IP-DOR-14（**仅临嘱**）及护士 IF-IP-PRC-02/04 在调用本聚合 **`createDraftChargeItemFromOrder`** / **`priceByNurseStation`** 前，须经 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`** 解析 **`ChargeItemDefinition`**；药耗 **`clientResolvedDispenseLocationId` 非空**时 **不**二次 match **`ServiceLocationTemplate`**；透传库位**不**写入 `adm_charge_item` 列；对称 **DM-MED-003 BR-MRQ-030**、**DM-WKF-003 BR-DR-013** |
| `BR-CHG-017` | **签发 FIFO 分段批号对价**                                                  | 门诊 IF-DOR-15/37（及同构住院临嘱药耗签发）按发放分段重建 `ChargeItem` 时，`unit_price` **必须**按该段 **`lotNumber`** 匹配 **`LOT_NUMBER_PRICE`**（`ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft`）；批号未命中 → 业务异常；**禁止** `selectFirstPriceLineForChargeDraft` / 跨批号 **priority** 落账；对齐 **AS-OP-DOR-003 · BR-ORD-016** |
| `BR-CHG-009` | 带子项诊疗展开后总价必须与父项总价一致 | 各**子项账单**折后金额之和 = **父账单**总价；父行与子行均为 `adm_charge_item` 物理行；**不是**医嘱组套（OrderTemplate） |
| `BR-CHG-010` | 收费明细必须关联有效的就诊                                                   | 就诊标识必填                                                                  |
| `BR-CHG-011` | 退费金额不得超过原收费金额                                                   | 退费金额 ≤ 原收费金额                                                            |
| `BR-CHG-012` | 同一收费明细在同一时间只能处于一种状态                                             | 状态机确保状态互斥性                                                              |
| `BR-CHG-013` | `REFUNDING` 仅作为退费过程态                                            | 不应长期滞留；必须被后续动作收口为 `REFUNDED` 或回退策略态                       |
| `BR-CHG-014` | 金额展示口径优先级遵循业务约定                                                 | 人工调价 > 系统优惠 > 原始总价，明细原始值需保留                                             |
| `BR-CHG-015` | 门诊**半退**下未选退明细须以**新行**承接后续收费事实                                       | 原 `SETTLED` 行随支付整单冲正进入退费终态后，对保留项插入 `PLANNED` 新行，`refund_id` 链回被替换行；禁止在已冲正行上「部分改金额」冒充仍有效应收；与 **DM-FIN-002** **BR-PMR-017** 一致。**重生成一致（硬）**：凡本规则下**新生成**之 **ChargeItem** 及连带 **请求/发放**（及编排规定的其它替换根），其**业务状态与可对齐的业务属性**须与**源原单**一致（复刻），仅允许新主键、`refund_id`、再结算入口态等**技术性差异**。 |
| `BR-REF-020` | 读侧排除已被半退/住院反结再生替代的源 `SETTLED` 行                                            | 同就诊**链末端**存在有效再生叶（`PLANNED` 或 `PENDING_SETTLEMENT`）时，须沿 `refund_id` **向上递归**整条链：链上**全部祖先** `SETTLED` 行均视为终态历史，门诊收银待收、住院待结算/划价/取消执行 Gate、医生站可退列表等读侧**不得**再展示或拦截这些祖先行，仅保留链末端有效再生行可操作；执行收口时须将源 `SETTLED` 与拟退 `REFUNDING` 一并纳入 `completeOutpatientBillRefund` |
| `BR-IP-RVS-011` | 住院反结须复制再生 ChargeItem，源 `SETTLED` 为终态 | 反结仅对支付单 `charge_item_ids` 快照内源行插入 `PENDING_SETTLEMENT` 新行并写 `refund_id`；**禁止**改写源 `SETTLED` 行；新行须完整复刻源行业务锚点与金额；**多轮**「结算→反结→再结算」可形成 `SETTLED→…→PENDING_SETTLEMENT` 多层链，已完成轮次支付单与快照**封存**；读侧/Gate 须按 **BR-REF-020** 递归追溯整条链，避免历史 `SETTLED` 挡当前轮逆流程 |
| `BR-PRICE-SRC-001` | **取价双源**：已发生业务读账单/供应行，当前价读定价表 | **历史/已落库**（`ChargeItem` 或库房 **`SupplyRequest`** 已持久化 `price`/`total_price`）：金额与展示单价以持久化字段为准，**禁止**再查当前 `ChargeItemDefinition` 覆盖详情槽价。**当前价/新建**（开立目录、待收费草稿、调价制单、库房**选品**进价锚等）：读 **`ChargeItemDefinition`**（ACTIVE）。名称/分类等元数据可经 `definition_id` 关联定价行；**库存明细**按现存量×当前批号价为现估值，不属于历史单据。**报表进价/成本**：药耗可经 `HistoricalChargeItemPurchasePriceSupport` 按批号 `LOT_NUMBER_COST` 同批号回放调前进价；**诊疗/挂号无真实进价**，统计进价/成本一律用账单 `unit_price`（**禁止**调价回放与目录 `PURCHASE`/`SALE` 覆盖）。对齐 **DM-ADM-010** |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文   | 依赖聚合                 | 关联方式                       | 说明              |
| ------- | -------------------- | -------------------------- | --------------- |
| 患者档案上下文 | Patient              | `patientId`                | 通过患者标识引用        |
| 患者诊疗上下文 | Encounter            | `encounterId`              | 通过就诊标识引用        |
| 药品管理上下文 | MedicationRequest    | `serviceTable + serviceId` | 通过服务来源表名 + 主键引用 |
| 诊疗服务上下文 | ServiceRequest       | `serviceTable + serviceId` | 通过服务来源表名 + 主键引用 |
| 诊疗服务上下文 | ChargeItemDefinition | `definitionId`             | 通过项目定义标识引用      |
| 财务收费上下文 | Account              | `accountId`                | 通过账户标识引用        |


### 8.2 下游影响


| 下游上下文   | 受影响聚合                 | 触发方式 | 说明               |
| ------- | --------------------- | ---- | ---------------- |
| 财务收费上下文 | Invoice               | 订阅事件 | 收费明细已收费后生成发票     |
| 财务收费上下文 | PaymentReconciliation | 订阅事件 | 收费明细已收费后生成支付对账记录 |
| 财务收费上下文 | Claim                 | 订阅事件 | 收费明细已收费后触发医保申报   |
| 药房管理上下文 | MedicationDispense    | 订阅事件 | 收费明细已退费后触发退药     |
| 库存管理上下文 | InventoryItem         | 订阅事件 | 收费明细已冲销后恢复库存     |


### 8.3 引用规则

> **统一规则**：
>
> - 门诊场景：引用 `就诊标识(EncounterId)`；患者引用统一使用 `患者标识(PatientId)`
> - 住院场景：引用 `就诊标识(EncounterId)`；住院全程由 EpisodeOfCare 串联
> - 收费明细对上游业务事实一律使用 `serviceTable + serviceId` 可追溯锚点，禁止仅依赖展示名称
> - 对定价事实使用 `definitionId` 引用 `ChargeItemDefinition`；账户维度使用 `accountId` 引用 `Account`


| 模型名称                               | 关系类型 | 说明                                                  |
| ---------------------------------- | ---- | --------------------------------------------------- |
| `DM-ADM-002 Encounter`             | 关联   | 通过 `encounterId` 归集同次就诊费用明细                         |
| `DM-ADM-001 Patient`               | 关联   | 通过 `patientId` 关联患者                                 |
| `DM-ADM-009 Account`               | 关联   | 通过 `accountId` 关联自费/医保账户                            |
| `DM-ADM-010 ChargeItemDefinition`  | 关联   | 通过 `definitionId` 关联定价模型                            |
| `DM-FIN-002 PaymentReconciliation` | 依赖   | 收费、退费、对账按 `chargeItemIds` 编排状态推进                    |
| `DM-ADM-012 Invoice`               | 依赖   | 发票开具/作废引用收费明细集合                                     |
| `ServiceRequest` / `Procedure`     | 依赖   | 通过 `serviceTable+serviceId`、`procedureId` 关联开立与执行事实 |


### 8.4 事件流示意图

```
ServiceRequest        ChargeItem                    Account                    Invoice                  PaymentReconciliation
    │                     │                           │                          │                              │
    │──医嘱开立────────────>│                           │                          │                              │
    │                     │──ActivatedByExecution────>│                          │                              │
    │                     │                           │──ChargePostedEvent──────>│                              │
    │                     │                           │                          │                              │
    │                     │──SettledEvent────────────>│──AccountSettledEvent───>│──生成发票                     │
    │                     │                           │                          │                              │──记录支付流水
    │                     │                           │                          │                              │
    │                     │──RefundedEvent───────────>│                          │                              │
    │                     │                           │                          │                              │
    │                     │──PreSettledEvent─────────>│                          │                              │
```

---

## 9. 补充

### 9.1 带子项诊疗拆分说明

带子项诊疗（目录 `wkf_activity_definition_children`，**不是**医嘱组套 OrderTemplate）的收费明细以 **多行** 落库，**不再使用 `children_json`**：

- **父账单**：承载父项总价与院内展示/合计锚点，`bus_no` 为组内根键（见 §4.4）；`parent_id` 空；开立/划价/回显**只见父项**
- **子项账单**：每行一条子项，`bus_no = 父行.bus_no + "." + 子项稳定编码`，`parent_id` 指向父行；金额按**落账当时**子项 SALE 冻死
- 子项账单金额之和等于父项总价（`BR-CHG-009`）；院内合计只计父行（`parent_id` 空）；结算/医保上传只认子项账单冻价，**禁止**回查现行定价定义；目录有子项但无子项账单时**拒绝**（**禁止**回落 SALE）
- 退费时父行与子项账单**联动**处理，不支持仅退部分子项行
- 写路径：凡带子项诊疗生成父 Charge 后，均由 `ActivityDefinitionPackageChargePersistSupport` 补写子项账单（门诊开立草稿、住院执行、护士划价、滚费等同构；目录无子项则跳过）。对齐 **AS-IP-NUR-005 BR-IP-PRC-011**

### 9.2 退费链路说明

退费流程如下：

1. 应用服务发起退费请求，校验退费条件和退费金额
2. 领域服务将收费明细状态从 `SETTLED` 推进为 `REFUNDING`（待退费）
3. 调用支付域生成退款流水
4. 调用医保域处理医保退费（如适用）
5. 领域服务将收费明细状态从 `REFUNDING` 推进为 `REFUNDED`（已退费）
6. 发布退费完成事件，触发退药、退检等下游处理

退费关联信息通过退费关联标识（`refundId`）字段记录，确保退费链路可追溯。

### 9.3 医保结算说明

医保结算流程如下：

1. **预结算阶段**：调用医保系统接口，获取医保报销预估金额
2. **正式结算阶段**：按医保目录分类计算报销金额，扣除起付线，累计报销金额不超过封顶线，生成医保结算明细
3. **医保申报**：通过 `Claim` 聚合向医保系统提交报销申请
4. **医保对账**：医保系统审核后返回实际报销金额，通过 `PaymentReconciliation` 聚合对账

医保与自费切换规则：

- 初始开立医嘱时，默认按患者当前账户类型（自费或医保）创建收费明细
- 如果患者中途补办医保或切换医保类型，可通过 `switchChargeItemAccount` 方法切换账户
- 账户切换仅允许 `DRAFT`、`PLANNED` 状态的明细（BR-CHG-004）
- 已结算（`SETTLED`）或已退费（`REFUNDED`）的明细不允许切换账户

### 9.4 跨模块产品目录读（`ProductDefinitionQueryService` · CHS bridge）

> **落点**：`zxplat-domain` · `com.zxplat.domain.administration.product.service.ProductDefinitionQueryService`（跨药品/诊疗/耗材/服务聚合的**只读**门面）。  
> **消费方**：`zxplat-module-chs-jilin` bridge `ChsOutpatientInsuranceContextLoader` / `ChsInpatientInsuranceContextLoader`（2204/2301 组装 `med_list_codg`）；诊疗套餐另经 `ChsActivityPackageFeeExpandSupport` 按子项目录批量加载。

| 方法 | 业务意义 | 出参 |
| --- | --- | --- |
| `batchLoadAsMap` | 按 `ProductDefinitionBatchLookup`（表名 + 定义 id）批量读 `yb_no` | `Map<String, ProductDefinitionCatalogQueryModel>` |

- **边界说明**：只读；键为 `ProductDefinitionKeyUtil` 复合键；禁止 bridge 直连各品目 `*Repository`。
- **套餐上传**：院内 ChargeItem 仍锚父诊疗定义；2204/2301 逻辑行可一对多展开为子项，`feedetl_sn={父busNo或id}.{childrenId}`，`chargeItemId` 仍挂父行。
- **关联 DM**：**DM-MED-001** §6.1.7、**DM-WKF-001** §6.1.6 / §6.2.10、**DM-ADM-008** §6.1.6（3301 写回）；本节仅描述 2204/2301 **读**侧。

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.6.28 | 2026-10-17 | **代码先行回写**：§3.1 / §6.1.3a / §6.1.12a — `refund_id` 双义（冲销负向 vs 反结再生正向）；冲销源判定改 `isPricingOffsetLine()`（数量非正），反结再生正向可再冲销；对齐 **AS-IP-NUR-005 BR-IP-PRC-024** |
| v1.6.27 | 2026-10-16 | **代码先行回写**：§4.2 补 `ANESTHESIA_EXECUTE`/`ANESTHESIA_PRICING`；§6.1.5a `voidOrderExecuteChargeItemsByProcedureIds` 覆盖执行族（含 `anesthesia-execute`）；对齐 **AS-IP-NUR-012 BR-IP-CXL-008** |
| v1.6.26 | 2026-10-13 | §6.1.1a：`retargetRegistrationFeeDepartment`（挂号费开单/执行/成本科室跟随挂号科室更正）。代码先行回写，对齐 **AS-OP-FIN-001** IF-REG-15 |
| v1.6.25 | 2026-10-13 | **代码先行回写**：§6.2.13～6.2.14a 待结算/已结算/占用汇总过滤 **`parent_id IS NULL`**，院内展示余额与 §9.1 父行合计对齐，避免带子项诊疗父子双计 |
| v1.6.24 | 2026-10-06 | **代码先行回写**：§4.4 / §9.1 / **BR-CHG-009** 带子项诊疗落父+子项账单、回显只见父项、结算禁 SALE 回落；与医嘱组套（OrderTemplate）区分 |
| v1.6.22 | 2026-09-21 | §6.1.6b `stampMedicalTechFeeConfirmation`（医技费用清单一键确费） |
| v1.6.21 | 2026-09-21 | §3.1 移除 `performer_id`/`performing_org_id`；新增 `fee_confirmer_id`/`fee_confirmed_at`（医技行级确费）；§6.1.4c 激活不再写操作人戳；执行人展示跟 Procedure / DeviceDispense |
| v1.6.20 | 2026-09-06 | §3.1 `prescription_no`：诊疗侧仅 **`AR`** 可结算盖章；代煎 companion **不**预写方号（**BR-OPC-026** / **BR-TCM-RX-009**） |
| v1.6.19 | 2026-09-06 | §3.1 `prescription_no` 明确为结算快照；新增 §6.1.6a `stampPrescriptionSnapshots`；药品业务真源 `MedicationRequest`（**AS-OP-FIN-002 · BR-OPC-026**） |
| v1.6.18 | 2026-09-02 | §6.2.25：`OutpatientFeeDetailPageQuery.billDateFrom/To` 明确为 `LocalDateTime` 闭区间（应用层将仅日期扩为 `00:00:00～23:59:59`） |
| v1.6.17 | 2026-08-21 | §6.2.35：`summarizeKeyMedicationUsage`（**AS-RPT-011** 重点药品使用统计） |
| v1.6.16 | 2026-08-13 | §6.2.13a：**BR-IP-STL-014** 账户扣减对齐成码——自费扣本批合计、医保扣个人现金；交叉 **AS-IP-FIN-003** v1.19 |
| v1.6.15 | 2026-08-06 | **BR-PRICE-SRC-001**：报表进价/成本——药耗同批号回放；诊疗/挂号用账单 `unit_price`（不回放） |
| v1.6.14 | 2026-08-04 | **BR-OPC-016** 补全：§6.1.7/11/12/14 退费完成与住院结算亦写 `enterer_*`；§6.1.4c 激活仅写 `performer_id`；§6.2.28 明细改为 payment 链路 + Request 开立时间排序 |
| v1.6.13 | 2026-08-04 | **BR-OPC-016** 口径迁移：`enterer_id`/`entered_date`=收费人/收费时间；结算不再写 `performer_id` 为收款人；开单医生/开立时间归 Request；历史回填见 Flyway `V15__backfill_charge_item_cashier_enterer.sql` |
| v1.6.12 | 2026-08-03 | §6.2.29 挂号净人次对齐 **AS-RPT-007**（一就诊一次）；§6.2.31 处方数按 `prescription_no` 去重（**AS-RPT-001 v1.6**） |
| v1.6.11 | 2026-07-31 | **§3.1 / BR-CHG-017**：签发 FIFO 分段须按 `lotNumber` → `selectExecutionPriceLineForChargeDraft`；`BR-CHG-016` 类名对齐 `ClinicalCatalogPriceResolver`；对齐 **AS-OP-DOR-003 v1.56** |
| v1.6.10 | 2026-07-24 | §9.4 套餐 2204/2301 一对多展开注记（`ChsActivityPackageFeeExpandSupport`） |
| v1.6.9 | 2026-07-11 | §6.2.4 `queryInpatientCostDetails` 对齐 §6.2.3：`excludeSupersededSettledLines` 递归排除反结链历史 `SETTLED`；§6.2.15 入参固定开启 |
| v1.6.8 | 2026-07-11 | **BR-REF-020** / **BR-IP-RVS-011** 成码回更：`ChargeItemRegenerationChainSupport` 沿 `refund_id` **递归**解析多层结算/反结链；§6.2.3/§6.2.17～18 读侧与 Gate 口径同步 |
| v1.6.7 | 2026-07-11 | §6.1.12a 住院反结改为**复制再生**（源 `SETTLED` 终态）；**BR-IP-RVS-011**；**BR-REF-020** 扩展至 `PENDING_SETTLEMENT` 再生行；§6.2.2 `listChargeItemsByServiceIds` 可选去重 |
| v1.6.6 | 2026-07-11 | §6.2.1 CHS bridge 消费注记；§9.4 `ProductDefinitionQueryService.batchLoadAsMap`（**zxplat-module-chs-jilin** 2204） |
| v1.6.5 | 2026-07-06 | §6.2.30 **`listPractitionerWorkloadScopeChargeRows`**、§6.2.31 **`summarizePractitionerWorkloadOverview`**（**AS-RPT-001 v1.4** 外显指标与 scope 统一源） |
| v1.6.4 | 2026-07-03 | §6.2.27～29 / §6.2.31 注释与 AS v1.3 对齐：领域 org 粒度 + 应用层跨 org 合并 |
| v1.6.3 | 2026-07-03 | §6.2.27/29 对齐 **AS-RPT-001 v1.3**：未选科室时应用层跨 org 合并，**禁止**汇总 Tab 按科室分块 |
| v1.6.2 | 2026-07-03 | §6.2.27～29 工作量读侧（**AS-RPT-001** 关闭 **GAP-RPT-WKL-001～003**） |
| v1.6.1 | 2026-06-27 | **BR-PRICE-SRC-001** 扩至库房：`SupplyRequest.price/total_price` 为历史单据锚点；详情槽价禁止回落定价表；库存明细仍为现估值 |
| v1.6.0 | 2026-06-27 | **BR-PRICE-SRC-001**：取价双源——已落库业务读 `ChargeItem` 账单单价/总额，当前价读 `ChargeItemDefinition`；调价后禁止用定价表覆盖历史账单金额 |
| v1.5.9 | 2026-06-24 | §3.1 `performer_id` 口径：门诊/挂号 `settleChargeItems` 写收款人；§6.1.6 聚合内影响同步（**BR-OPC-016**） |
| v1.5.8 | 2026-06-21 | §6.2.25 **`pageOutpatientFeeDetailEncounterBlocks`**、§6.2.26 **`summarizeOutpatientFeeDetailAmounts`**（**AS-OP-FIN-005** 关闭 **GAP-OP-FDT-001**） |
| v1.5.7 | 2026-06-20 | §6.1.5b **`voidPendingOrderExecuteChargeItemsByChargeItemIds`**（**BR-IP-TCM-008** 代煎费刷新 void）；`CreateChargeItemCommand.Item.prescriptionNo` 落库口径 |
| v1.5.6 | 2026-06-19 | §6.1.3b **`priceByAutoRoll`**、§6.2.24 **`existsAutoRollChargeItemForBillDate`**、§5 **`AutoRollChargeItemCreatedEvent`** / **`ChargeItemEventType.AUTO_ROLL_CREATED`**（**AS-IP-NUR-013** 关闭 **GAP-IP-ARG-003/004**） |
| v1.5.6 | 2026-07-17 | §6.1.3a 改为 **`offsetNursePricingChargeItemsByServiceAnchors`**（冲销留痕）；§5 **`NursePricingChargeItemOffsetEvent`**；对齐 **AS-IP-NUR-005 v2.8** |
| v1.5.5 | 2026-06-18 | §6.1.3a **`voidNursePricingChargeItemsByServiceAnchors`**、§5 **`NursePricingChargeItemRemovedEvent`**（**AS-IP-NUR-005** IF-IP-PRC-06；关闭 **GAP-IP-PRC-008/010**） |
| v1.5.4 | 2026-06-11 | **BR-CHG-016**：暂存/划价草稿择价库位透传（`resolveDraftChargePriceLine`）；§6.1.2 应用层 Pre 边界；§3.1 锚点说明同步 |
| v1.5.3 | 2026-06-10 | §6.1.4l **`deactivateChargeByExe`**、§6.1.5a **`voidOrderExecuteChargeItemsByProcedureIds`**、§6.2.23 **`listChargeItemsByProcedureIds`**（**AS-IP-NUR-012** Gap 001～003 关闭） |
| v1.5.2 | 2026-05-30 | §6.2.13a：**BR-IP-STL-014** 须在 `settleInpatientChargeItems` 前快照已结占用 + 本批合计；禁止 post-SETTLE SUM 全额退预交金 |
| v1.4.10 | 2026-05-28 | **BR-CHG-008**：医嘱开立/护士执行 Charge 须经 **`ProductDefinitionOrderChargePricingsupport** 按 **`quantity`/`unit_code`** 与目录单位槽比例换算；禁止默认 1/「次」 |
| v1.4.9 | 2026-05-28 | §6.2.10～6.2.12：废止 **OutpatientInfo** RM 表述；门诊收银/退费队列 EXISTS 直连 `adm_encounter`，展示由 **EncounterContextFacade** 编排 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.4.8 | 2026-05-27 | §6.2.15 Procedure 对冲孤儿行：闭区间外跳过、闭区间内失败（**BR-IP-CDT-016**） |
| v1.4.7 | 2026-05-26 | §1.5 下游应用服务（**AS-IP-FIN-003** / **AS-IP-NUR-006**）追溯 |
| v1.4.6 | 2026-05-26 | §6.1.9b **`preSettleInpatientChargeItems`**；§6.1.12 住院结算门禁 **`PENDING_SETTLEMENT→SETTLED`**；§6.2.16 **`listEncounterIdsWithPendingSettlementCharges`**（**AS-IP-FIN-003** G-STL-03 / Q-IPI-007） |
| v1.4.5 | 2026-05-24 | §6.2.15 **`listInpatientNurseStationCostDetailChargeRows`**（**AS-IP-NUR-006**）：业务日期 + 净 Procedure 时刻 + `InpatientNurseStationCostDetailChargeRow` |
| v1.4.4 | 2026-05-24 | §6.2.13～6.2.14：待结算汇总 **Criteria `SUM`+`GROUP BY`** 下推 SQL；对齐 **DM-ADM-011-SDD** §5.2 |
| v1.4.3 | 2026-05-24 | 新增 **`PENDING_SETTLEMENT`（待结算）** 与 **`PLANNED`（待收费）** 分轨：护士划价/住院执行入账→待结算；**住院临时**签发→待收费、**长期**不生成账单；展示余额占用待结算合计（**AS-IP-FIN-002** / **AS-IP-NUR-001**） |
| v1.4.2 | 2026-05-24 | §6.2.5 **`queryOxygenAndECGDuration`** 落库口径：`ChargeItemQueryCommand` + 汇总 `wkf_service_request.quantity`（**AS-IP-NUR-005** **BR-IP-PRC-010**） |

*更早变更见各节脚注与历史提交。*

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ChargeItemDomainService`

| 方法 |
|------|
| `createRegistrationChargeItem` |
| `retargetRegistrationFeeDepartment` |
| `createDraftChargeItemFromOrder` |
| `voidUnbilledDraftChargeItemsByServiceAnchor` |
| `priceByNurseStation` |
| `priceByAutoRoll` |
| `activateChargeItemAfterExecution` |
| `activateChargeByExe` |
| `createPlannedChargeItemFromOrderExecution` |
| `planDraftChargeItemsAfterAdviceSigned` |
| `rollbackPlannedChargeItemsAfterAdviceSignedOff` |
| `voidUnbilledDraftChargeItemsByServiceAnchors` |
| `planDraftChargeItemsAfterAdviceSignedForAnchors` |
| `rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors` |
| `voidUnbilledDraftChargeItemsByDispenseAnchors` |
| `planDraftChargeItemsAfterAdviceSignedByDispenseAnchors` |
| `rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors` |
| `cancelExecutionRollbackChargeItem` |
| `voidOrderExecuteChargeItemsByProcedureIds` |
| `voidPendingOrderExecuteChargeItemsByChargeItemIds` |
| `offsetNursePricingChargeItemsByServiceAnchors` |
| `deactivateChargeByExe` |
| `settleChargeItems` |
| `stampPrescriptionSnapshots` |
| `stampMedicalTechFeeConfirmation` |
| `returnRegistrationRefundChargeItems` |
| `switchChargeItemAccount` |
| `createChargeItemsLinkingRegeneratedAnchors` |
| `preSettleChargeItems` |
| `manualOverridePlannedChargeItemPrices` |
| `preSettleInpatientChargeItems` |
| `requestOutpatientBillRefund` |
| `cancelOutpatientBillRefund` |
| `completeOutpatientBillRefund` |
| `settleInpatientChargeItems` |
| `revertInpatientSettledChargeItems` |
| `requestInpatientBillRefund` |
| `completeInpatientBillRefund` |

### QueryService：`ChargeItemQueryService`

| 方法 |
|------|
| `loadChargeItemsByIds` |
| `listChargeItemsByServiceIds` |
| `listByEncounterAndServiceAnchors` |
| `listDistinctServiceIdsByStatusEnumAndServiceTable` |
| `listDistinctServiceIdsByStatusEnumAndServiceTableAndServiceIdIn` |
| `listChargeItemsByProcedureIds` |
| `listPendingSettlementChargeItemsByServiceTableAndServiceIds` |
| `sumPendingSettlementTotalPriceByEncounterId` |
| `mapPendingSettlementTotalPriceByEncounterIds` |
| `sumSettledTotalPriceByEncounterId` |
| `mapSettledTotalPriceByEncounterIds` |
| `sumInpatientChargedOccupancyTotalPriceByEncounterId` |
| `mapInpatientChargedOccupancyTotalPriceByEncounterIds` |
| `listChargeItemIdsByEncounterIdAndStatusEnum` |
| `listChargeItemsForMedicationDispensePricing` |
| `listPendingSettlementChargeItemsForMedicationDispensePricing` |
| `listChargeItemsForDeviceDispensePricingByDispenseIds` |
| `listChargeItemsForMedicationDispensePricingByDispenseIds` |
| `listChargeItemsByDeviceDispenseIds` |
| `listChargeItemsByEncounterId` |
| `listChargeItemsByEncounterAndProduct` |
| `mapPreservedNewChargeItemIdBySourceId` |
| `existsActiveRegenerationForSourceChargeItemIds` |
| `filterOutSupersededSettledLines` |
| `filterOutSupersededSettledLinesGroupedByEncounter` |
| `resolveChargeItemIdsForOutpatientRefundBillCompletion` |
| `listChargeItemsByEncounterIds` |
| `listChargeItemsByRefundIds` |
| `listRefundRelatedChargeItemsByEncounterIds` |
| `queryCostDetails` |
| `queryInpatientCostDetails` |
| `listChargeItemDefInfoWithChildren` |
| `listChargeItemsByPaymentBill` |
| `listChargeItemsForInvoice` |
| `listChargeItemsByInpatientBill` |
| `listOutpatientCashierChargeItemsForEncounters` |
| `outpatientCashierBillableStatusCodes` |
| `isEncounterPaid` |
| `outpatientRefundQueueStatusCodes` |
| `listEncounterIdsWithPendingSettlementCharges` |
| `pageEncounterIdsForOutpatientChargeQueue` |
| `existsAutoRollChargeItemForBillDate` |
| `existsOrderExecuteChargeForServiceRequestBillDate` |
| `listOrderExecuteChargesByServiceRequestId` |
| `listOutpatientFeeDetailAugmentChargeItems` |
| `pageOutpatientFeeDetailEncounterBlocks` |
| `summarizeOutpatientFeeDetailAmounts` |
| `listSettledOrRefundedChargeItemsByOccurrenceWindow` |
| `listSettledOrRefundedChargeItemsByEnteredDateWindow` |
| `listSettledChargeItemsByEnteredDateWindow` |
| `listDistinctEncounterIdsByEnteredDateWindow` |
| `pageFeeDetailEncounterIdsByEnteredDateWindow` |
| `listChargeItemsByEncounterIdsInEnteredDateWindow` |
| `sumFeeDetailSaleTotalPriceByEnteredDateWindow` |
| `listFeeDetailChargeItemFilterProjections` |
| `resolveServiceTablesForFeeCategories` |
| `listChargeItemsByEnteredDateWindow` |
| `listChargeItemsByOccurrenceTimeWindow` |
| `summarizeOutpatientFeeDetailByFinType` |
| `pageInpatientFeeDetailEncounterBlocks` |
| `summarizeInpatientFeeDetailByFinType` |
| `summarizeKeyMedicationUsage` |
| `summarizeKeyMedicationUsageMatrix` |
| `listKeyMedicationUsageLines` |
| `summarizeKeyMedicationUsageSectionFromLines` |
| `summarizeKeyMedicationUsageSection` |
| `countPositiveMedicationEncounterNet` |
| `summarizeAllMedicationUsage` |
| `summarizeOutpatientFeeDetailSaleAmountFromPreloadedScope` |
| `listOutpatientFeeDetailChargeRows` |
| `summarizePractitionerWorkloadByFinType` |
| `summarizeRegistrationChargesByServiceType` |
| `pagePractitionerWorkloadDetailByRequest` |
| `pagePractitionerWorkloadDetailFromChargeRows` |
| `listPractitionerWorkloadDetailFromChargeRows` |
| `buildPractitionerWorkloadDetailRowsFromChargeRows` |
| `slicePractitionerWorkloadDetailRows` |
| `filterPractitionerWorkloadAdviceChargeRows` |
| `slicePractitionerWorkloadChargeRows` |
| `listPractitionerWorkloadScopeChargeRows` |
| `loadPractitionerWorkloadChargeItemMap` |
| `summarizePractitionerWorkloadOverview` |
| `countReferencingEncounterDiagnosisId` |
| `existsByDefinitionIdIn` |

