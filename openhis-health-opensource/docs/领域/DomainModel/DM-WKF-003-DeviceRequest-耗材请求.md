# DM-WKF-003 DeviceRequest 耗材请求

> **限界上下文**：WKF 域 · 耗材申请管理\
> **聚合类型**：实体聚合\
> **版本**：v1.2.11

## 1. 概述

### 1.1 业务定义

`DeviceRequest` 用于表达一次耗材请求事实，覆盖草稿、签发、执行确认、退耗材等核心业务过程。聚合边界只收口「请求事实与状态」；库存扣减、发放单回退、费用结算等由应用编排与协作域完成。

### 1.2 核心职责

| 职责   | 说明                         |
| ---- | -------------------------- |
| 草稿管理 | 创建/修改/删除耗材请求草稿，签发前可自由变更    |
| 状态驱动 | 通过签发、签退、执行确认、取消等操作驱动请求状态迁移 |
| 退耗材  | 以负向单模式新建退耗材请求并关联原单，保证审计追溯  |
| 冲销校验 | 计算可冲销余量，防止退耗材数量超额          |


### 1.3 适用范围

| 场景      | 说明                  |
| ------- | ------------------- |
| 门诊耗材医嘱  | 门诊就诊过程中开立耗材请求       |
| 住院耗材医嘱  | 住院期间护士站或医生开立耗材医嘱    |
| 手术/操作耗材 | 手术间、治疗室按操作消耗耗材并产生请求 |
| 退耗材申请   | 已发放耗材的退回，通过负向单冲销原请求 |


### 1.4 与相关聚合的关系

- **InventoryItem（库存项）**：签发前检查库存，执行确认时触发扣减，退耗材时触发库存回补。
- **DeviceDispense（耗材发放）**：签发后由发放域创建发放单，执行确认需与发放完成事件协同收口。
- **ServiceRequest（服务申请）**：通过 `based_on_table` / `based_on_id` 关联上游诊疗服务申请，实现组套耗材追溯。
- **MedicationRequest（药品申请）**：执行侧生成耗材时，`based_on_table` / `based_on_id` 可指向药品医嘱等资源，用于来源追溯（以实现枚举表名为准）。**门诊医生站耗材**：开立**不**选房，直接落取药科室模板**默认发材房**到 `location_id` / `perform_location`（**BR-DR-013**）；IF-DOR-14 载荷 **`devicePayload.dispenseLocationId`** 仅用于费用草稿择价，**不**作为医生选房写库。签发在该默认房 FIFO 生成 **`DeviceDispense`**，并回写请求发材房与发放行一致。**门诊护士站**可指定耗材房：未选走默认；选中则校验该房签发批号库存够量后改发放行 `location_id` 并回写请求 `location_id`/`perform_location`，**请求发材房与发放行必须一致**。护士划价等路径仍须满足 **BR-DR-012**。计费锚点与 **`ChargeItem`** 关系见 **`DM-ADM-011`** 表下说明及 **`DM-WKF-007`** §1。

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表（无聚合内子表）。

**表结构**：

- 主表：`wkf_device_request`（主键：`id`）
- 子表：无；若后续出现「一单多耗材明细」，再扩展子实体与落库形态。

**高频查询模式**：

- 按请求标识：主键查询。
- 按就诊：主表 `encounter_id` 索引扫描。
- 按患者历史：主表 `patient_id` + 就诊过滤。
- 冲销余量：按 **`refund_device_id = 原单 id` 且 `quantity < 0`** 汇总历史负向行（与读侧 `offsetVerification`、BR-DR-011 一致；见 §9.2）。

### 2.2 聚合根标识


| 属性     | 类型                | 说明                             |
| ------ | ----------------- | ------------------------------ |
| 耗材请求标识 | `DeviceRequestId` | 全局唯一，对应主表主键 `id`（雪花或序列策略以落库与交互契约为准） |


### 2.3 状态机（RequestStatus）

```text
DRAFT（待签发） --签发--> ACTIVE（已发送） --签退--> DRAFT
ACTIVE / DRAFT（允许时） --执行确认/收费收口--> COMPLETED（已完成）
退耗材负向单等 ----> CANCELLED（已取消）
```

上述为主链路示意；**落库值**为 **`RequestStatus#getCode()`（英文字符串）**，不得与 sortOrder 数字混用。枚举全集见 **§4.4**（含 `VERIFIED`/`HOLD`/`STOPPED` 等共享态）。

#### 状态定义


| 状态（display） | 英文 code   | 说明                     | 允许的操作（主链路）        |
| ----------- | --------- | ---------------------- | ------------------ |
| 待签发         | `DRAFT`   | 草稿态，可修改/删除             | 修改 / 删除 / 签发       |
| 已发送         | `ACTIVE`  | 签发后进入履约链路              | 签退 / 执行确认 / 取消（口径以实现为准） |
| 已完成         | `COMPLETED` | 执行确认或门诊收费收口后的收口态       | 退耗材 / 撤销执行（口径以实现为准） |
| 已校对         | `VERIFIED` | 共享枚举态；其他上下文可能单独使用     | 以实现编排为准            |
| 暂挂          | `HOLD`    | 暂停执行                   | 以实现编排为准            |
| 已取消         | `CANCELLED` | 取消或退耗材负向单据             | —                  |
| 已停嘱         | `STOPPED` | 医嘱停止                   | —                  |


> **状态枚举说明**：持久化字段 `status_enum` 对齐 **`com.zxplat.domain.share.enums.RequestStatus`**（**`String`，存 `getCode()`**）。本聚合 **主路径** 常用 `DRAFT` ↔ `ACTIVE` → `COMPLETED`，退耗材负向单等为 `CANCELLED`。**sortOrder 仅作枚举对照排序，不是落库码**。详见 **§4.4**。

## 3. 实体

```
DeviceRequest（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                  DeviceRequest                       │
  │  id · bus_no · status_enum · device_def_id · quantity │
  │  encounter_id · patient_id · perform_location        │
  │  refund_device_id · based_on_table · based_on_id     │
  └──────────────────────────────────────────────────────┘

跨域关联（非本聚合维护）
  -> DeviceDispense（device_req_id）
  -> ChargeItem（门诊：`service_table` + `service_id` 锚 **`DeviceDispense`** 行，**`DM-ADM-011`** **`BR-CHG-001`**）
  -> Procedure（request_table + request_id）
  -> InventoryItem（执行确认与撤销时协作）
```

### 3.1 耗材请求（DeviceRequest）

DeviceRequest 聚合根的属性定义，对应 `wkf_device_request` 表。


| 属性        | 字段名                      | 类型           | 必填  | 说明                     |
| --------- | ------------------------ | ------------ | --- | ---------------------- |
| 耗材请求标识    | `id`                     | `Long`       | 是   | 主键                     |
| 请求业务编码    | `bus_no`                 | `String`     | 否   | 耗材请求编码                 |
| 处方号       | `prescription_no`        | `String`     | 否   | 处方号                    |
| 诊疗项目标识    | `activity_id`            | `Long`       | 否   | 相关诊疗项目                 |
| 组套标识      | `package_id`             | `Long`       | 否   | 组套                     |
| 请求状态      | `status_enum`            | `String`     | 是   | `RequestStatus#getCode()`，语义见 §2.3，全集见 §4.4   |
| 请求意图编码    | `intent_code`            | `String`     | 否   | 按字典                    |
| 请求类型编码    | `category_enum`          | `String`     | 否   | 字典/码表 code（字符串）           |
| 优先级编码     | `priority_enum`          | `String`     | 否   | 字典/码表 code（字符串）           |
| 停止执行标识    | `perform_flag`           | `Boolean`    | 否   | 是否停止执行                 |
| 分组编号      | `group_no`               | `String`     | 否   | 分组编号                   |
| 器材类型      | `device_type_code`       | `String`     | 否   | 器材类型                   |
| 请求数量      | `quantity`               | `BigDecimal` | 是   | 请求数量                   |
| 数量单位编码    | `unit_code`              | `String`     | 否   | 请求单位                   |
| 产品批号      | `lot_number`             | `String`     | 否   | 批号                     |
| 耗材定义标识    | `device_def_id`          | `Long`       | 是   | 耗材目录来源                 |
| 器材规格      | `device_specifications`  | `String`     | 否   | 规格描述                   |
| 请求发起人标识   | `requester_id`           | `Long`       | 否   | 开立人                    |
| 请求发起科室标识  | `org_id`                 | `Long`       | 否   | 开立科室                   |
| 默认器材房标识   | `location_id`            | `Long`       | 否   | 默认/实际发材房；**门诊医生站开立**落取药科室模板默认配置；护士换房时与 `perform_location`、发放行同写                   |
| 发放耗材房标识   | `perform_location`       | `Long`       | 否   | 发放耗材房；与 `location_id`、**`DeviceDispense.location_id`** 保持一致；护士站选房后回写                   |
| 就诊标识      | `encounter_id`           | `Long`       | 是   | 就诊锚点                   |
| 患者标识      | `patient_id`             | `Long`       | 是   | 患者主体                   |
| 发放频次编码    | `rate_code`              | `String`     | 否   | 用药/发放频次                |
| 预计使用时间    | `use_time`               | `DateTime`   | 否   | 预计使用时间（与审计时间精度一致以落库与交互契约为准） |
| 预计使用开始时间  | `use_start_time`         | `DateTime`   | 否   | 预计使用窗口起                |
| 预计使用结束时间  | `use_end_time`           | `DateTime`   | 否   | 预计使用窗口止                |
| 预计使用周期描述  | `use_timing`             | `String`     | 否   | 周期/频次文字描述              |
| 开立时间      | `req_opened_time`        | `DateTime`   | 否   | **首次建草稿/直建落库写一次**；签发、改草稿、签退**不覆盖**；执行侧直建完成态写创建时刻；与药/诊同构              |
| 签发时间      | `req_authored_time`      | `DateTime`   | 否   | **仅签发**（及无草稿直建完成态）写入；草稿暂存**不写**本列；与药/诊同构；**仅系统追溯**，列表/报表**默认不展示**              |
| 停嘱时间      | `stop_time`              | `DateTime`   | 否   | 长期停嘱写入；取消停嘱清空；与药/诊同构              |
| 执行人类型编码   | `performer_enum`         | `String`     | 否   | 字典/码表 code（字符串）           |
| 执行人标识     | `performer_id`           | `Long`       | 否   | 执行人                    |
| 执行科室标识    | `perform_org_id`         | `Long`       | 否   | 执行科室                   |
| 按需标识      | `as_need_flag`           | `Boolean`    | 否   | 是否按需给出                 |
| 按需原因      | `as_need_reason`         | `String`     | 否   | 按需使用原因                 |
| 合同编码      | `contract_code`          | `String`     | 否   | 合同/费用性质                |
| 退耗材关联标识   | `refund_device_id`       | `Long`       | 否   | 关联原单                   |
| 医保类别编码    | `yb_class_enum`          | `String`     | 否   | 医保类别 code（字符串）            |
| 追溯码       | `trace_no`               | `String`     | 否   | 追溯码                    |
| 就诊诊断绑定有序 ids 串 | `encounter_diagnosis_ids` | `String` | 否 | 有序逗号分隔的 `adm_encounter_diagnosis.id`；首位为主诊断（与药品申请同源） |

| 观测扩展 JSON | `observation_id_json`    | `String`     | 否   | 相关观测 JSON              |
| 支持用药信息    | `support_info`           | `String`     | 否   | 支持用药/附加说明文本            |
| 来源表       | `based_on_table`         | `String`     | 否   | 来源表名                   |
| 来源标识      | `based_on_id`            | `Long`       | 否   | 来源 ID                  |
| 生成来源      | `generate_source_enum`   | `String`     | 否   | **`GenerateSource`**（§4.2，存枚举 `getCode()`） |
| 备注        | `remark`                 | `String`     | 否   | 备注                     |


> 通用审计字段继承自基类实体（以落库与交互契约为准），不在此重复列举。

### 3.2 无聚合内子实体

当前无聚合内子实体；若后续出现「一单多耗材明细」需求，再扩展子实体与不变式。

## 4. 值对象

### 4.1 请求状态（RequestStatus）

语义、允许操作与状态机见 **§2.3**。持久化字段为 `status_enum`，类型 **`String`**，取值 **`RequestStatus#getCode()`**（与 **§4.4** 第一列一致）。**`sortOrder` 仅供排序对照，不是落库值**。

### 4.2 生成来源（GenerateSource）

与 **`com.zxplat.domain.share.enums.GenerateSource`** 及 **`DM-MED-003` §4.5** / **`DM-ADM-011` §4.2** 共用同一套英文 **code**。

| 枚举常量 | code（落库） | 说明 |
| --- | --- | --- |
| `DOCTOR_PRESCRIPTION` | `doctor-prescription` | 医生开立 |
| `NURSE_PRICING` | `nurse-pricing` | 护士划价（须满足 **BR-DR-012**） |
| `REGISTRATION_REGISTER` | `registration` | 挂号登记 |
| `ORDER_EXECUTE` | `order-execute` | 医嘱执行 |
| `AUTO_ROLL_FEES` | `auto-roll-fees` | 自动滚费 |

> **存储口径**：对应主表字段 `generate_source_enum`，使用上表 **英文 code 字符串** 存储（禁止数字串作身份）。

### 4.3 其他编码字段

`intent_code`、`category_enum`、`priority_enum`、`performer_enum`、`yb_class_enum` 在 **`DeviceRequest`** 上均为 **`String`**，按院内字典/码表承载；未在本文档展开码表时，以实现与数据字典为准。

### 4.4 `status_enum`（RequestStatus）枚举 code 与 sortOrder 对照

落库存 **`code`（String）**；下列 **`sortOrder`** 来自共享枚举定义，**仅用于排序/文档对照**，禁止当作持久化主键或替代 `getCode()`。

| **落库 code（String）** | sortOrder（对照） | 中文 display | 说明 |
| --- | --- | --- | --- |
| `DRAFT` | 1 | 待签发 | 草稿 |
| `ACTIVE` | 2 | 已发送 | 已签发进入链路 |
| `VERIFIED` | 3 | 已校对 | 共享枚举；是否单独用于耗材以实现编排为准 |
| `HOLD` | 4 | 暂挂 | 暂停 |
| `CANCELLED` | 5 | 已取消 | 取消 / 退耗材负向单等 |
| `STOPPED` | 6 | 已停嘱 | 停嘱 |
| `COMPLETED` | 7 | 已完成 | 本聚合执行确认 / 门诊收费收口常用收口态 |

> **码表约束**：以 **`RequestStatus`** 源码为准；若枚举增删，同步修订 §2.3 与本表。

## 5. 领域事件

### 5.1 事件定义

| 编号 | 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
| --- | --- | --- | --- | --- | --- |
| 5.1.1 | 耗材草稿已创建 | `DeviceOrderDraftCreatedEvent` | 草稿创建后 | `deviceRequestId`、`deviceDefId`、`encounterId`、`quantity` | 就诊医嘱列表QueryService；费用草稿编排（应用层） |
| 5.1.2 | 耗材草稿已修改 | `DeviceOrderDraftEditedEvent` | 草稿修改后 | `deviceRequestId`、变更摘要 | 同上 |
| 5.1.3 | 耗材草稿已移除 | `DeviceOrderDraftRemovedEvent` | 草稿删除后 | `deviceRequestId`、`encounterId` | 同上；关联QueryService失效 |
| 5.1.4 | 耗材请求已签发 | `DeviceOrderSignedEvent` | 签发后 | `deviceRequestId`、签发侧关键字段摘要（以落库与交互契约为准） | DeviceDispense 编排（发放单生成）；护士站/执行待办 |
| 5.1.5 | 耗材请求已签退 | `DeviceOrderSignOffEvent` | 签退后 | `deviceRequestId` | 发放侧取消或待处理（用例编排）；领域事件后查询刷新 |
| 5.1.6 | 门诊耗材执行已确认 | `OutpatientDeviceDispenseConfirmedEvent` | 执行确认后；或门诊正式结算 **`completeDeviceRequestAfterOutpatientCharge`**（**AS-OP-FIN-002** **BR-OPC-015**） | `deviceRequestId`、执行人/科室/时间等收口字段摘要（以落库与交互契约为准；收费收口时 **`performerId`** 常为收银） | InventoryItem（扣减编排）；ChargeItem（待收费推进）；统计/审计 |
| 5.1.7 | 门诊耗材执行已撤销 | `OutpatientDeviceDispenseConfirmationCancelledEvent` | 执行撤销后 | `deviceRequestId` | DeviceDispense（回退待配药等）；InventoryItem（按批号回补编排）；费用侧冲正入口（用例） |
| 5.1.8 | 退耗材请求已发起 | `DeviceOrderRefundRequestedEvent` | 生成负向请求后 | `deviceRequestId`（负向单）、`refundDeviceId`（原单） | 退费编排；库存回补编排；审计 |

### 5.2 事件触发规则

| 当前状态             | 操作       | 目标状态        | 发布事件                                                 |
| ---------------- | -------- | ----------- | ---------------------------------------------------- |
| —                | 创建草稿     | `DRAFT`     | `DeviceOrderDraftCreatedEvent`                       |
| `DRAFT`          | 修改草稿     | `DRAFT`     | `DeviceOrderDraftEditedEvent`                        |
| `DRAFT`          | 删除草稿     | 逻辑删除        | `DeviceOrderDraftRemovedEvent`                       |
| `DRAFT`          | 签发       | `ACTIVE`    | `DeviceOrderSignedEvent`                             |
| `ACTIVE`         | 签退       | `DRAFT`     | `DeviceOrderSignOffEvent`                            |
| `ACTIVE`/`DRAFT` | 执行确认     | `COMPLETED` | `OutpatientDeviceDispenseConfirmedEvent`             |
| `ACTIVE`         | 门诊正式结算收口 | `COMPLETED` | `OutpatientDeviceDispenseConfirmedEvent`             |
| `COMPLETED`      | 执行撤销     | 由用例编排决定     | `OutpatientDeviceDispenseConfirmationCancelledEvent` |
| —                | 退耗材（负向单） | `CANCELLED` | `DeviceOrderRefundRequestedEvent`                    |
| —                | 护士划价/执行直建完成态（**`createExecutedDeviceOrder`**） | `COMPLETED` | 护士划价：`NursePricingDeviceRequestCreatedEvent`；其它来源：**无** |
| `COMPLETED`      | 取消护士划价（**`revokeExecutedDeviceOrder`**） | 逻辑删除 | 护士划价：`NursePricingDeviceRequestRemovedEvent`；其它来源：**无** |

### 5.3 `NursePricingDeviceRequestCreatedEvent` 载荷细表（护士划价 · **B.14**）

> 仅当 **`generate_source_enum=nurse-pricing`** 时在 **`createExecutedDeviceOrder`** `saveAll` 后逐条发布；执行绑定等其它来源**不**发布本事件。

| 载荷字段 | 类型 | 说明 |
| --- | --- | --- |
| `aggregateId`（`deviceRequestId`） | `Long` | 新耗材请求主键 |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `deviceDefId` | `Long` | 耗材定义标识 |
| `quantity` | `BigDecimal` | 请求数量 |
| `unitCode` | `String` | 请求单位 **code**（可空） |
| `performLocation` | `Long` | 发放库房 |
| `basedOnTable` | `String` | 补费父锚点表名；直接划价为 **null** |
| `basedOnId` | `Long` | 补费父锚点 ID；直接划价为 **null** |

**消费方**：护士划价行已在域方法落库；列表读 **`DeviceRequestQueryService#listInpatientNurseStationPricedOrders`** / **`mapListModelsByIds`**（**非** OrderInfo RM）。

### 5.4 `NursePricingDeviceRequestRemovedEvent` 载荷细表（取消护士划价 · **B.14**）

> 与 **`NursePricingDeviceRequestCreatedEvent`** 对称；仅当 **`generate_source_enum=nurse-pricing`** 时在 **`revokeExecutedDeviceOrder`** `saveAll` 软删后逐条发布。

| 载荷字段 | 类型 | 说明 |
| --- | --- | --- |
| `aggregateId`（`deviceRequestId`） | `Long` | 已软删耗材请求主键 |
| `encounterId` | `Long` | 就诊标识 |
| `basedOnTable` | `String` | 补费父锚点表名；直接划价为 **null** |
| `basedOnId` | `Long` | 补费父锚点 ID；直接划价为 **null** |

**消费方**：已划价列表等读侧剔除投影；与 **GAP-IP-PRC-010** 同构——直读主表 + `is_deleted` 过滤。

## 6. 领域服务

### 6.1 写侧领域服务（DeviceRequestDomainService）

#### 6.1.1 创建耗材草稿（`createDeviceOrderDraft`）

- **业务意义**：在就诊上下文中创建一条可编辑的耗材请求草稿。
- **边界说明**：仅写入本聚合与校验请求事实；库存占用/释放、费用草稿由应用编排与协作域完成。
- **入参**：耗材定义标识、数量、单位、就诊标识、患者标识等（以实现契约为准）。
- **出参**：`DeviceRequestId`（新建主键）。
- **前置条件**：就诊与患者锚点合法；数量大于零；满足库存相关前置校验（BR-DR-001）。
- **状态变化**：初始化为 `DRAFT`。
- **聚合内影响**：插入 `wkf_device_request` 草稿行；写入 **`req_opened_time`** 为首次暂存时刻（见 §3）。
- **事件输出**：`DeviceOrderDraftCreatedEvent`。
- **失败语义**：必填缺失、库存不足、就诊状态不允许开立时拒绝。

#### 6.1.2 修改耗材草稿（`changeDeviceOrderDraft`）

- **业务意义**：在签发前修订草稿内容（数量、库房、规格等）。
- **边界说明**：仅更新 `DRAFT` 态字段；不直接改发放单与收费明细。
- **入参**：`DeviceRequestId`、变更字段集合。
- **出参**：—。
- **前置条件**：当前状态为 `DRAFT`；变更前完成库存相关前置校验（BR-DR-001）。
- **状态变化**：`N/A`（仍为 `DRAFT`）。
- **聚合内影响**：更新主表可编辑字段。
- **事件输出**：`DeviceOrderDraftEditedEvent`。
- **失败语义**：非草稿态、并发冲突、库存校验失败时拒绝。

#### 6.1.3 删除耗材草稿（`removeDeviceOrderDraft`）

- **业务意义**：逻辑删除未签发请求，释放后续引用。
- **边界说明**：不物理删除主数据行（以实现约定为准）；已收费约束由用例层结合费用域处理（BR-DR-002）。
- **入参**：`DeviceRequestId`。
- **出参**：—。
- **前置条件**：状态为 `DRAFT`；满足可删断言（读侧 `assertDeviceRequestDeletable` 门控，费用断言在用例层）。
- **状态变化**：逻辑删除或等价终止口径（以落库与交互契约为准）。
- **聚合内影响**：主表删除标记或状态迁移。
- **事件输出**：`DeviceOrderDraftRemovedEvent`。
- **失败语义**：非草稿态、已收费不可删时拒绝。

#### 6.1.3a 废弃耗材草稿（`cancelDeviceOrderDraft`）

- **业务意义**：未签发耗材申请 DRAFT→CANCELLED（保留行，不软删；调价在途配套 **BR-PRC-SUB-003a**）。
- **边界说明**：与 `removeDeviceOrderDraft` 软删路径分流；发布 `DeviceOrderCancelledEvent`。
- **入参**：`CancelDeviceOrderDraftCommand`（id / operatorId / cancelReason）。
- **出参**：—。
- **前置条件**：状态为 `DRAFT`。
- **状态变化**：`DRAFT -> CANCELLED`。
- **实现锚点**：`DeviceRequestDomainService#cancelDeviceOrderDraft`。

#### 6.1.4 签发耗材请求（`signDeviceOrder`）

- **业务意义**：将草稿转为已发送，进入发放与执行链路。
- **边界说明**：签发后创建发放单等由 DeviceDispense 上下文与用例编排；本方法只保证状态迁移合法。
- **入参**：`DeviceRequestId`。
- **出参**：—。
- **前置条件**：状态为 `DRAFT`；库存与业务规则允许签发。
- **状态变化**：`DRAFT -> ACTIVE`。
- **聚合内影响**：更新 `status_enum` 及签发相关字段（以落库与交互契约为准）；**写入 `req_authored_time` 为签发时刻**（不覆盖 `req_opened_time`；与药/诊签发口径一致）。
- **事件输出**：`DeviceOrderSignedEvent`。
- **失败语义**：状态非法、库存或费用门控未通过时拒绝。

#### 6.1.5 签退耗材请求（`signOffDeviceOrder`）

- **业务意义**：将已发送请求退回草稿，用于纠错与重开。
- **边界说明**：已收费不可签退（BR-DR-003）；发放回退由用例编排。
- **入参**：`DeviceRequestId`。
- **出参**：—。
- **前置条件**：状态为 `ACTIVE`；读侧 `assertDeviceRequestSignOffAllowed` 与费用校验通过。
- **状态变化**：`ACTIVE -> DRAFT`。
- **聚合内影响**：更新状态为 `DRAFT`。
- **事件输出**：`DeviceOrderSignOffEvent`。
- **失败语义**：已收费、已执行完成或状态不允许时拒绝。

#### 6.1.6 执行确认（`executeDeviceOrder`）

- **业务意义**：将请求收口为已校对，表示执行侧已确认消耗事实。
- **边界说明**：库存扣减、追溯码、发放完成协同由用例编排（BR-DR-005）；本聚合更新请求状态并发布确认事件。**门诊护士站 IF-NUR-04/05** 与 **住院护士划价/绑定执行**（**BR-NUR-014** / **BR-IP-PRC-019**）**不调用**本方法；仍供 **AS-OP-FIN-002**（**BR-OPC-015**）等场景。
- **入参**：`DeviceRequestId`、执行人标识、执行科室标识等。
- **出参**：—。
- **前置条件**：状态为 `ACTIVE` 或允许确认的 `DRAFT`（以落库与交互契约为准）；发放链路就绪。
- **状态变化**：`ACTIVE`/`DRAFT`（允许时）`-> COMPLETED`。
- **聚合内影响**：写入执行人、执行科室、完成时间等收口字段。
- **事件输出**：`OutpatientDeviceDispenseConfirmedEvent`。
- **失败语义**：状态不允许、协同前置未满足时拒绝。

#### 6.1.6a 门诊收费后完成耗材请求（`completeDeviceRequestAfterOutpatientCharge`）

- **业务意义**：门诊正式结算后，将仍为 **ACTIVE** 的耗材请求**幂等**收口为 **COMPLETED**，与收费事实一致。
- **边界说明**：由 **`OutpatientChargeAdviceStateSyncService`**（**IF-OPC-07** / **BR-OPC-015**）同步编排；与 **`executeDeviceOrder`** 共用 **`OutpatientDeviceDispenseConfirmedEvent`**，区别在操作者语义（收银 vs 执行科室）。
- **入参**：耗材请求标识、收费操作人标识。
- **出参**：—。
- **前置条件**：当前须允许收口为完成态（实现：`assertCompletableAfterOutpatientCharge`）；已为 **COMPLETED** 时幂等。
- **状态变化**：`ACTIVE -> COMPLETED`。
- **聚合内影响**：与执行确认路径一致的完成态收口字段策略。
- **事件输出**：`OutpatientDeviceDispenseConfirmedEvent`。
- **失败语义**：状态非法时拒绝。

#### 6.1.7 撤销执行（`revokeDeviceOrderExecution`）

- **业务意义**：撤销已执行确认，用于纠错场景。
- **边界说明**：发放单回退至待配药、清空批号/追溯码、库存按批号加回等须满足 **BR-DR-006**，由用例继续编排 `DeviceDispense` 等协作接口完成；本方法发布撤销事件并处理请求侧可回滚字段，**不**单方法包办全链路。**门诊护士站 IF-NUR-05** 与 **住院护士**路径**不调用**本方法；取消执行改由 **`DeviceDispenseDomainService#revertPreparedBatchToPreparation`** / **`#revertCompletedBatchToPreparation`**（**DM-WKF-007 §6.1.24/§6.1.21**）编排，**DeviceRequest** 状态不变。
- **入参**：`DeviceRequestId`。
- **出参**：—。
- **前置条件**：处于已执行且允许撤销的窗口（以落库与交互契约为准）。
- **状态变化**：由用例编排决定目标状态（如回退至 `ACTIVE`/`DRAFT`，须在接口契约中固定）。
- **聚合内影响**：回滚执行收口字段或状态。
- **事件输出**：`OutpatientDeviceDispenseConfirmationCancelledEvent`。
- **失败语义**：超过可撤销时限或下游未回退完成时拒绝。

#### 6.1.8 申请退耗材（`createDeviceOrderRefund`）

- **业务意义**：创建负向请求并关联原单，表达退耗材事实。
- **边界说明**：退费编排、库存回补不在本方法内一次完成，由应用服务分段编排；已发放与未发放路径分流见 **BR-DR-008**。
- **入参**：原 `DeviceRequestId`、预冲销数量等。
- **出参**：新负向单 `DeviceRequestId`，或已存在的未完结负向单标识（幂等）。
- **前置条件**：原单为正向单（`refund_device_id` 为空）；冲销校验通过（BR-DR-011）；读侧 `offsetVerification` 余量充足；已有未完结负向单时幂等返回（BR-DR-008）。
- **状态变化**：新单为 `CANCELLED`（或产品约定的负向终态）；幂等命中时不改已有行。
- **聚合内影响**：插入负向行：`quantity` 为原数量的相反数；`status_enum` 置取消/待退口径；`refund_device_id = 原单 id`；`prescription_no` 可按 `"T" + 原处方号` 规则区分负向单（产品约定）；可同时写入 `based_on_table`/`based_on_id` 作来源追溯（非冲销主键）；**已有未完结负向单时返回已有主键**；定位仅本原单，不按患者扫他单。
- **事件输出**：`DeviceOrderRefundRequestedEvent`（幂等命中已有行时不重复发布）。
- **失败语义**：原单已是负向单、超额冲销、原单不可退、关联字段不合法时拒绝。

#### 6.1.9 生成已完成请求（`createExecutedDeviceOrder`）

- **业务意义**：由**医嘱执行绑定**、**住院护士划价/补费**、**医技耗材划价**等场景直接产生 `COMPLETED` 态耗材请求；不经过草稿—签发链路。
- **边界说明**：对齐 **AS-IP-NUR-004**（执行 bindItems）、**AS-IP-NUR-005** IF-IP-PRC-02/04、**AS-MT-CHG-001** IF-MT-CHG-02；须满足 **BR-DR-010**（`generate_source_enum` 为约定枚举）；护士/医技划价来源须 **perform_location** 非空（**BR-DR-012**）；划价**不**做库存可用量 Gate（AS 决议）；发放/收费/投影由应用层编排。
- **入参**：`CreateExecutedDeviceOrderCommand`（含 `operatorId` + `items[]`）。每行至少：`generateSourceEnum`、`deviceDefId`、`quantity`、`unitCode`、`encounterId`、`patientId`、`performLocation`（划价必填）；可选 **`lotNumber`**（目录选中批号，有值落 `lot_number` 供临时路径自动扣库锁批）；可选 **`basedOnTable`/`basedOnId`**（补费父锚点，须成对；直接划价/执行绑定可为空）。
- **出参**：`List<Long>`——新耗材请求主键列表，**顺序与 `items` 一致**。
- **前置条件**：`items` 非空；每行通过 `assertSourceAllowedForCompletedCreate`、`assertRequiredForCreate`；`basedOnTable` 与 `basedOnId` **须同时为空或同时有值**；`nurse-pricing`/`medical-tech-pricing` 时 `performLocation` 非空。
- **状态变化**：每行新建即 `COMPLETED`。
- **聚合内影响**：`saveAll` 批量插入；写入 `generate_source_enum`；无草稿直建**同时写入** `req_opened_time` 与 `req_authored_time`（批次内统一取方法入口时刻）；划价目录选中批号非空时写入 `lot_number`；补费/绑定路径写入 `based_on_table`/`based_on_id`；`requester_id` 默认 `operatorId`；划价路径 `location_id` 与 `perform_location` 同落。
- **事件输出**：护士划价路径（`generate_source=nurse-pricing`）每行 **`NursePricingDeviceRequestCreatedEvent`**（**§5.3**）；其它执行直建来源无事件。
- **失败语义**：`items` 为空、`based_on_*` 半填、来源非法、划价缺库房、不变式失败 → `DEVICE_REQUEST_INVALID_ARGUMENT` 等。

#### 6.1.10 撤销已完成请求（`revokeExecutedDeviceOrder`）

- **业务意义**：对执行侧产出的已完成请求做撤销或软删收口；**护士划价取消**时整行撤销 `COMPLETED` 态耗材请求（**AS-IP-NUR-005** IF-IP-PRC-06）。
- **边界说明**：与收费、库存、发放的回滚顺序由用例编排（**BR-IP-PRC-024～026**）；若已走发放完成路径，须先 **`DeviceDispenseNurseWorkflowSupport#revertInpatientBindingDeviceDispenses`** 再调用本方法（**BR-DR-008**）；本方法不单独保证跨域一致性。
- **入参**：`RevokeExecutedDeviceOrderCommand`（批量 `Item`：`id`；含 `operatorId`）。
- **出参**：—。
- **前置条件**：`items` 非空；护士划价路径须 **`COMPLETED` + `generate_source_enum=nurse-pricing`** 且未逻辑删除（`assertNursePricingRevocable`）；其它来源须 **`COMPLETED`**（`assertCompleted`）。
- **状态变化**：逻辑删除（软删）；**不**迁移至 `STOPPED`/`CANCELLED`。
- **聚合内影响**：`saveAll` 软删主表。
- **事件输出**：护士划价行 **`NursePricingDeviceRequestRemovedEvent`**（逐条，**§5.4**）；其它来源：**无**。
- **失败语义**：状态不允许、来源不允许、不存在或入参为空 → `DEVICE_REQUEST_INVALID_ARGUMENT` / `DEVICE_REQUEST_NOT_FOUND`。

#### 6.1.11 半退保留链正向克隆（`cloneDeviceRequestForPreservedChargeRegeneration`）

- **业务意义**：门诊半退「保留行」账单重生时，按源耗材请求事实在本聚合内插入**新主键**请求行，供后续执行/发放/计费锚定；与 **DM-ADM-011 §6.1.9a**、**AS-OP-DOR-004** IF-DRA-03 编排对齐。
- **边界说明**：**正向克隆**；**禁止**以 `createDeviceOrderRefund` 等退费命令充当再生入口。
- **入参**：`CloneDeviceRequestForPreservedChargeRegenerationCommand`（至少含 `sourceDeviceRequestId`、`operatorId`）。
- **出参**：新耗材请求主键。
- **前置条件**：源请求可加载且未逻辑删除；`status_enum` **不得**为 `CANCELLED` / `HOLD`；`CloneDeviceRequestForPreservedChargeRegenerationCommand` 之 `sourceDeviceRequestId` 与 `operatorId` 均非空。
- **状态变化**：新行 `status_enum = DRAFT`（`initDraft`）。
- **聚合内影响**：按源行复制可对齐之业务字段；`prescription_no`、`refund_device_id` 清空；`based_on_table`=`wkf_device_request`、`based_on_id`=源主键；新 `bus_no`；`requester_id`=`operatorId`；`req_opened_time`=当前时刻、`req_authored_time`=空、`stop_time`=空；`is_deleted=false`；须通过 `assertRequiredForCreate`。
- **事件输出**：**先** `save` **后**发布 **`DeviceOrderDraftCreatedEvent`**（**B.14**）。
- **失败语义**：`DEVICE_REQUEST_INVALID_ARGUMENT`（入参或 `operatorId` 为空）；`DEVICE_REQUEST_NOT_FOUND`（源不存在）；`DEVICE_REQUEST_RULE_CONFLICT`（源已删或源状态为取消/暂挂等）。

#### 6.1.12 退费收口完成耗材请求（`completeDeviceRequestAfterRefund`）

- **业务意义**：退耗发放确认后，将负向耗材申请（`refund_device_id` 非空、`CANCELLED` 待退）推进至 **`COMPLETED`**，供 **AS-OP-FIN-003 IF-REF-04** 放行资金退费（对称 **DM-MED-003** `completeMedicationRequestAfterRefund`）。
- **边界说明**：由 **`DeviceDispenseNurseWorkflowSupport#refundDispensedDeviceDispenseAndCompleteNegativeRequests`** 在 `refundDispensedDeviceDispense` 成功后编排调用；仅当待退发放行 `device_req_id` 锚定负向耗单时执行。
- **入参**：`Long requestId`（负向耗材申请主键）。
- **前置条件**：`refund_device_id` 非空；尚未 `COMPLETED`（`ensureCompletableAfterPharmacyRefund`）。
- **状态变化**：`CANCELLED`（待退）→ **`COMPLETED`**（幂等跳过已完结）。
- **事件输出**：无（首版；与药嘱退药闭环同族）。
- **失败语义**：非负向单、已完结重复操作 → 业务异常。

#### 6.1.13 撤销未完结负向退耗材申请（`voidOpenRefundDeviceOrder`）

- **业务意义**：门诊退费申请撤销时，软删尚未完结的负向退耗材请求。
- **边界说明**：对称药嘱 `voidOpenRefundMedicationOrder`、诊疗 `voidOpenRefundServiceOrder`。

#### 6.1.14 医生站签发后 FIFO 批号挂接（`bindLotNumberAfterDoctorStationFifoSign`）

- **业务意义**：门诊医生站签发后，将 FIFO 分段批号绑定到耗材请求/发放编排所需事实。

#### 6.1.15 门诊护士执行重绑发材房（`rebindPerformLocationForOutpatientNurseExecute`）

- **业务意义**：门诊护士执行时回写请求 `location_id` + `perform_location`，与发放行 `DeviceDispense.location_id` 对齐（选中房或默认房）。

#### 6.1.16 医生站签发后回写发材房（`bindDispenseLocationsAfterDoctorStationFifoSign`）

- **业务意义**：门诊医生站 FIFO 签发后，将默认发材房同步写入请求 `location_id`/`perform_location`，与发放行一致。

> **变更（2026-10-09 · 模式 B）**：纠正「医生站选房」误口径——开立落默认配置；护士站选房校验库存并双写一致。

### 6.2 读侧查询服务（DeviceRequestQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于列表、历史、冲销与删除/签退门控。

#### 6.2.1 必取耗材请求（`mustGetDeviceRequest`）

- **业务意义**：按标识加载单条未删除请求，供写路径强依赖加载。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_request` 主表按主键。
- **过滤口径**：排除逻辑删除；租户/机构可见性以落库与交互契约为准。
- **组装规则**：返回完整聚合根QueryService或领域对象快照。
- **返回约束**：不存在或已删除时抛明确业务异常。

#### 6.2.2 按就诊查询耗材请求（`listDeviceRequestsByEncounter`）

- **业务意义**：查询某就诊下全部耗材请求，支撑医嘱列表与护士站视图。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_request` 按 `encounter_id`。
- **过滤口径**：默认排除逻辑删除；可按状态子集筛选（以落库与交互契约为准）。
- **组装规则**：按开立时间或组套字段排序；不内联费用明细。
- **返回约束**：列表可为空；单次查询条数上限以落库与交互契约为准。

#### 6.2.2a 按就诊主键集合批量查询耗材请求（`listDeviceRequestsByEncounterIds`）

- **业务意义**：按本页就诊主键 **IN** 批量加载耗材请求，供门诊护士站队列待发耗材红点（**AS-OP-NUR-001 BR-NUR-017**）与侧栏待发放对齐。
- **边界说明**：只读；仅本聚合 Repository；`IdInQueryChunkSupport` 分批，禁止超长 IN。
- **查询范围（读库，无写入）**：`wkf_device_request` 按 `encounter_id IN (...)`。
- **过滤口径**：排除逻辑删除；空集合返回空表。
- **组装规则**：映射 `DeviceRequestListModel`；不内联发放/费用。
- **返回约束**：允许空列表；调用方再按 `COMPLETED` 且 `refund_device_id` 为空筛选纳入口径。

#### 6.2.3 查询患者历史耗材请求（`listDeviceRequestHistoryByPatient`）

- **业务意义**：按患者查询历史请求并排除当前就诊，支撑历史追溯。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_request` 按 `patient_id`。
- **过滤口径**：排除当前 `encounter_id`；排除逻辑删除。
- **组装规则**：按时间倒序；字段子集与列表页合同一致。
- **返回约束**：大数据量分页；不返回其他患者数据。

#### 6.2.4 断言可删除（`assertDeviceRequestDeletable`）

- **业务意义**：删除前门控，判断当前请求是否允许删除。
- **边界说明**：只读断言；已收费等强约束可在用例层叠加费用域查询。
- **查询范围（读库，无写入）**：主表状态与删除标记。
- **过滤口径**：仅 `DRAFT` 且未违反删除策略时通过。
- **组装规则**：返回布尔或可删原因码。
- **返回约束**：不通过时上层应拦截删除，不调用写侧删除。

#### 6.2.5 断言可签退（`assertDeviceRequestSignOffAllowed`）

- **业务意义**：签退前门控，判断当前请求是否允许签退。
- **边界说明**：只读断言；费用已收费不可签退规则由 BR-DR-003 与用例层校验共同保证。
- **查询范围（读库，无写入）**：主表状态、关联收费摘要（若可读）。
- **过滤口径**：`ACTIVE` 且无签退禁止条件时通过。
- **组装规则**：返回布尔或原因码。
- **返回约束**：不通过时拦截签退。

#### 6.2.6 冲销校验（`offsetVerification`）

- **业务意义**：计算可冲销余量，支撑退耗材与退费前校验。
- **边界说明**：只读查询，不写库，不做跨域编排；不发起退费。
- **查询范围（读库，无写入）**：`wkf_device_request` 全表或按原单限定子集。
- **过滤口径（权威）**：对已冲销数量，仅统计满足 `refund_device_id = 原单 id` 且 `quantity < 0` 的历史行，对其 `quantity` 取绝对值后求和（与 BR-DR-011 一致）。`based_on_id` 仅作来源追溯，**不参与**已冲销数量主口径。
- **组装规则**：入参为原单标识、本次预冲销数量等（以实现契约为准）；返回剩余可冲销数量 = 原单 `quantity` − 已冲销汇总 − 预冲销。
- **返回约束**：剩余可冲销 `< 0` 时拒绝本次退耗材/负向单创建（BR-DR-011）。

#### 6.2.7 退费前可退断言（`assertDeviceRefundableBeforePaymentRefundRequested`）

- **业务意义**：在申请退费前校验耗材执行与可退口径，避免无效退费单。
- **边界说明**：只读断言，不写库，不做跨域编排；须结合 `DeviceDispense` 发放状态（已发放/部分发放视为已执行）与费用域（**BR-DR-009**）。跨发放/费用维度的完整门禁由应用层在调用本方法后叠加（与 **BR-DR-009** 原文一致）。
- **查询范围（读库，无写入）**：主表执行状态、关联发放/收费只读摘要（以落库与交互契约为准）。
- **过滤口径**：未执行完成、已全额冲销等场景不通过。
- **组装规则**：返回布尔与原因码列表。
- **返回约束**：不通过时上层应拦截退费申请。

#### 6.2.8 按原单批量查退耗关联请求（`listByRefundDeviceIdIn`）

- **业务意义**：AS-OP-FIN-003 IF-REF-04：按拟退收费行锚定的原耗材请求 id，查询 `refund_device_id` 命中的退耗/负向请求行，供应用层判断是否存在 `CANCELLED` 等未完成退耗语义（对称 **EX-REF-202**）。
- **边界说明**：只读；`DeviceRequestRepository#findByRefundDeviceIdIn` 下推；不做跨域编排。
- **过滤口径**：原耗材请求主键集合；空集合返回空列表。
- **组装规则**：返回主表实体列表（含 `statusEnum`、`refundDeviceId`）。
- **返回约束**：不修改聚合状态；门禁结论由应用层输出。

#### 6.2.9 批量按主键加载QueryService 列表查询（`mapListModelsByIds`）

- **业务意义**：**AS-IP-NUR-005** **IF-IP-PRC-05** 已划价列表：按耗材请求主键批量回源，补全 `device_def_id`/数量/单位/补费 `based_on_*` 等展示字段。
- **边界说明**：只读；空集合返回空 `Map`。
- **入参**：`Collection<Long> deviceRequestIds`。
- **出参**：`Map<Long, DeviceRequestListModel>`。
- **前置条件**：无。
- **状态变化**：无。
- **聚合内影响**：无。
- **事件输出**：无。
- **失败语义**：无（缺失 ID 不出现在 Map 中）。

#### 6.2.10 按来源锚点查询绑定耗材请求（`listDeviceRequestsByBasedOnAnchor` / `listDeviceRequestsByBasedOnAnchors`）

- **业务意义**：**AS-IP-NUR-012** 取消执行绑定耗材 **based_on** 反查（耗材无 `procedureId`）。
- **边界说明**：只读；`DeviceRequestRepository#findByBasedOnTableAndBasedOnId(In)` 下推；排除逻辑删除。
- **入参**：`basedOnTable` + `basedOnId`（单条）或 `basedOnIds` 集合（批量，同表名）。
- **过滤口径**：`based_on_table` + `based_on_id` 精确匹配；`basedOnTable` 空时按空串计。
- **组装规则**：映射为 `DeviceRequestListModel` 列表。
- **返回约束**：只读；空 `basedOnIds` 批量路径返回空列表。

#### 6.2.11 按耗材定义列出未签发草稿（`listUnsignedDraftsByDeviceDefId`）

- **业务意义**：调价保存/提交在途配套（**AS-FIN-PRC-001 · BR-PRC-SUB-003a**）：按本单调价耗材列出未签发 `DRAFT`，供应用层 **48h** 新鲜窗口分流。
- **边界说明**：只读；**仅本聚合**；`status=DRAFT`、未软删、`req_authored_time` 空；填充患者/开立人/有效开立时刻。
- **入参**：`deviceDefId`；空则空列表。
- **出参**：`List<StaleUnsignedDeviceRequestDraftQueryModel>`。
- **实现锚点**：`DeviceRequestQueryService#listUnsignedDraftsByDeviceDefId`。

#### 6.2.12 硬在途耗材申请判定（`existsHardInTransitRequestByDeviceDefId`）

- **业务意义**：调价提交硬在途互斥（不含 DRAFT）。
- **实现锚点**：`DeviceRequestQueryService#existsHardInTransitRequestByDeviceDefId`。

### 6.3 领域边界说明

- 聚合内动作仅描述耗材请求草稿创建/修改/删除、签发/签退、执行确认/撤销、退耗材负向单及执行侧直写完成态等**请求事实**。
- 费用约束断言、库存扣减与回补、发放单生成与回退由**应用用例**协调；领域服务不内联收费结算与库存事务。
- 患者主索引、耗材目录维护、收费结算不属于本聚合职责；跨上下文仅传递 `DeviceRequestId`（见 §8.3）。
- **冲销与退耗材试算**以 `refund_device_id` 指向原单且 `quantity < 0` 的负向历史行作为唯一汇总口径（§9.2）；`based_on_id` 不替代该口径。
- **就诊内列表**优先QueryService 刷新，写前校验回到主表断言（**BR-DR-QRY-001**）；历史查询排除当前就诊（**BR-DR-QRY-002**）；查询/断言/写入分层（**BR-DR-QRY-003**）。

## 7. 不变式


| 编号            | 不变式            | 说明                                                                                                               |
| ------------- | -------------- | ---------------------------------------------------------------------------------------------------------------- |
| BR-DR-001     | 保存/修改需库存相关前置校验 | 变更前先释放草稿占用再校验库存（策略以落库与交互契约为准）                                                                                         |
| BR-DR-002     | 已收费请求不可删除      | 费用域状态为已收费时拒绝删除（断言可在用例层叠加）                                                                                        |
| BR-DR-003     | 已收费请求不可签退      | 签退前须完成费用状态校验                                                                                                     |
| BR-DR-004     | 保存与签发驱动状态      | 保存落 `DRAFT`，签发迁移至 `ACTIVE`（见状态机）                                                                                 |
| BR-DR-005     | 执行确认协同收口       | 请求完成、发放完成、批号/追溯码、库存扣减在同一用例内协同；失败应整体撤销                                                                            |
| BR-DR-006     | 撤销执行与发放/库存一致   | 仅允许将已发放记录撤销回待配药态并清空 `lot_number`/`trace_no` 等，库存按批号与单位换算加回；须继续调用发放域能力收口                                          |
| BR-DR-007     | 退耗材负向单事实       | 新行 `quantity` 取原数量相反数；`status_enum` 为取消/待退口径；`refund_device_id = 原单 id`；`prescription_no` 可用 `"T"+原处方号` 区分（产品约定） |
| BR-DR-008     | 退耗材路径与防重复      | 已发放/部分发放走退耗材编排；未发放可走取消编排；**同一原单已有未完结负向单 → 幂等返回已有主键**；仅原单本身已是负向单才拒；不按患者扫他单 |
| BR-DR-009     | 退费前发放校验        | 存在已发放或部分发放时须先撤销执行再申请退费；校验以发放状态为准；QueryService 未落地时由用例结合发放/费用域完成                                                  |
| BR-DR-010     | 执行侧产出即完成态      | 执行绑定或用法绑定触发生成的请求，创建即 `COMPLETED`，并同步协作对象；`generate_source_enum` 取约定来源值                                           |
| BR-DR-011     | 冲销数量不得超额       | **已冲销** = 满足 `refund_device_id = 原单 id` 且 `quantity < 0` 的历史行数量绝对值之和；须 `原数量 − 已冲销 − 预冲销 ≥ 0`                     |
| BR-DR-012     | 护士划价开立耗材       | 等价于护士开耗材并完成执行确认路径：创建即 `COMPLETED`，须指定发放库房；库存预减/实扣以落库与交互契约为准                                                           |
| BR-DR-013     | 医生开立落默认发材房 / 护士选房换发 | **医生站**开立直接落取药科室模板默认发材房到 `location_id`/`perform_location`（医生**不**选房）；载荷 **`dispenseLocationId`** 仅费用草稿择价。签发在该默认房 FIFO，回写请求与 **`DeviceDispense.location_id`** 一致。**护士站**执行：未选耗材房走默认；选中则校验该房签发批号库存够量后改发放行并回写请求，**请求发材房与发放行必须一致** |
| BR-DR-QRY-001 | 就诊内列表优先QueryService     | 列表展示优先投影；写前断言回到主表或领域加载                                                                                           |
| BR-DR-QRY-002 | 患者历史排除当前就诊     | 历史查询按 `patient_id` 且排除当前 `encounter_id`                                                                          |
| BR-DR-QRY-003 | 查询/断言/写入分层     | 写侧不以投影为唯一事实源                                                                                                     |

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文   | 聚合                                 | 关联方式                               | 说明            |
| ------- | ---------------------------------- | ---------------------------------- | ------------- |
| 患者管理    | Patient                            | `patient_id`                       | 耗材请求主体        |
| 就诊管理    | Encounter                          | `encounter_id`                     | 就诊内请求锚点       |
| 基础数据    | DeviceDefinition                   | `device_def_id`                    | 耗材目录来源        |
| 组织架构    | Practitioner                       | `requester_id` / `performer_id`    | 开立与执行人员       |
| 组织架构    | Organization                       | `org_id` / `perform_org_id`        | 开立与执行科室       |
| 位置服务    | Location                           | `location_id` / `perform_location` | 默认库房与发放库房     |
| 服务/药品申请 | ServiceRequest / MedicationRequest | `based_on_table` / `based_on_id`   | 执行侧生成请求时的来源追溯 |


### 8.2 下游影响


| 下游上下文 | 聚合             | 触发方式      | 说明            |
| ----- | -------------- | --------- | ------------- |
| 耗材发放  | DeviceDispense | 事件订阅/用例编排 | 生成发放、执行回退     |
| 收费管理  | ChargeItem     | 用例编排      | 划价、待收费推进、退费协同；门诊 **`ChargeItem` 与每条 `DeviceDispense` 1:1**（**`DM-ADM-011`**） |
| 库存管理  | InventoryItem  | 用例编排      | 扣减与回补         |
| 执行记录  | Procedure      | 用例编排      | 执行事实记录        |


### 8.3 引用规则

> **统一规则**：跨上下文只允许传递 `DeviceRequestId`（`Long`）；禁止下游直接读取 `wkf_device_request` 表；消费方通过应用服务接口或领域事件获取请求状态。本聚合对外发布事件以 **§5.1** 为准（含最小载荷与消费方）。

### 8.4 事件流示意图

```text
DeviceRequest（本聚合）  事件（§5.1）                              下游/协作
────────────────────────────────────────────────────────────────────────────
草稿创建    ──> DeviceOrderDraftCreatedEvent (5.1.1)        ──> 医嘱列表QueryService / 费用草稿（应用）
草稿修改    ──> DeviceOrderDraftEditedEvent (5.1.2)        ──> 同上
草稿删除    ──> DeviceOrderDraftRemovedEvent (5.1.3)      ──> QueryService失效
签发        ──> DeviceOrderSignedEvent (5.1.4)            ──> DeviceDispense（发放编排）
签退        ──> DeviceOrderSignOffEvent (5.1.5)          ──> 发放侧取消或待处理（用例）
执行确认    ──> OutpatientDeviceDispenseConfirmedEvent (5.1.6)   ──> InventoryItem / ChargeItem（编排）
执行撤销    ──> OutpatientDeviceDispenseConfirmationCancelledEvent (5.1.7) ──> DeviceDispense / 库存回补（编排）
退耗材负向单 ──> DeviceOrderRefundRequestedEvent (5.1.8)   ──> 退费编排 / 库存回补 / 审计
```

## 9. 补充

### 9.1 冲销与关联口径说明

**冲销已冲销数量（权威口径）**：统计 `wkf_device_request` 中满足 **`refund_device_id = 原耗材请求主键`** 且 **`quantity < 0`** 的历史行，对 `quantity` 取绝对值后求和，作为已冲销数量；读侧 **`offsetVerification`**、写侧 **`createDeviceOrderRefund` 前置校验**、**BR-DR-011** 均须与此一致。

**`based_on_table` / `based_on_id`**：用于来源追溯（诊疗/药品医嘱等），**不替代**上述冲销汇总主键。

### 9.2 与 DeviceDispense 的协作
| 退耗材关联    | `basedOn`                | 关联原始请求（本地扩展）                           |

### 9.3 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.2.15 | 2026-10-18 | **模式 B**：§6.1.9 `createExecutedDeviceOrder` 可选落目录选中 `lotNumber`（供临时路径自动扣库锁批）；医技划价同源；对齐 **AS-IP-NUR-005 v2.25** / **AS-MT-CHG-001** |
| v1.2.14 | 2026-10-09 | **模式 B**：BR-DR-013 / §3 发材房——医生开立落默认配置；护士选房校验库存并与发放行一致；补 §6.1.16 `bindDispenseLocationsAfterDoctorStationFifoSign` |
| v1.2.13 | 2026-09-16 | §6.1.3a / §6.2.11/12：按耗材列未签发草稿 + 硬在途；对齐 **AS-FIN-PRC-001 v0.12** |
| v1.2.12 | 2026-09-16 | （已由 v1.2.13 取代）开立日 7 天废弃与硬软在途；对齐 AS v0.11 |
| v1.2.11 | 2026-08-19 | §6.2.2a `listDeviceRequestsByEncounterIds`（门诊护士站队列 **BR-NUR-017** 批量红点） |
| v1.2.10 | 2026-08-01 | **BR-DR-008 幂等修正**：§6.1.8 已有未完结负向单返回已有主键；仅原单已是负向单拒；对齐 **AS-OP-DOR-004 v1.2.8** |
| v1.2.9 | 2026-07-09 | §6.1.12 **`completeDeviceRequestAfterRefund`** 落码：退耗确认后负向单 `CANCELLED`→`COMPLETED`；编排锚点 **`DeviceDispenseNurseWorkflowSupport`**（对称药嘱 IF-REF-04） |
| v1.2.8 | 2026-06-18 | §6.1.10 **`revokeExecutedDeviceOrder`** 批量 + 护士划价 Gate；§5 **`NursePricingDeviceRequestRemovedEvent`**（**AS-IP-NUR-005** IF-IP-PRC-06；关闭 **GAP-IP-PRC-009/010**） |
| v1.2.7 | 2026-06-11 | **医生站暂存库位透传（BR-DR-013）**：`devicePayload.dispenseLocationId` 仅用于费用草稿择价，不落库 `perform_location`；对称 **DM-MED-003 BR-MRQ-030** |
| v1.2.6 | 2026-06-10 | §6.2.10 **`listDeviceRequestsByBasedOnAnchor(s)`**（**AS-IP-NUR-012** Gap 006） |
| v1.2.5 | 2026-06-08 | §6.1.6/§6.1.7 边界：护士站/住院护士**不调用** `executeDeviceOrder`/`revokeDeviceOrderExecution`；取消改 **DM-WKF-007 §6.1.24/§6.1.21** |
| v1.2.4 | 2026-05-24 | §5.3 **`NursePricingDeviceRequestCreatedEvent`** 落码；关闭 **GAP-IP-PRC-006** |
| v1.2.4.1 | 2026-05-28 | §5.3：废止 **OrderInfo** RM；护士划价列表改 **`DeviceRequestQueryService`** 直读主表 |
| v1.2.3 | 2026-05-24 | §5.3 护士划价事件；§6.2.9 **`mapListModelsByIds`** |
| v1.2.2 | 2026-05-24 | **createExecutedDeviceOrder** 对齐 **AS-IP-NUR-005**：批量 `items` + `List<Long>`；补费 `based_on_table`/`based_on_id` 成对写入；关闭 **GAP-IP-PRC-004** |
| v1.2.1 | （历史） | 半退克隆、门诊执行确认等（见 Git 历史） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`DeviceRequestDomainService`

| 方法 |
|------|
| `createDeviceOrderDraft` |
| `changeDeviceOrderDraft` |
| `removeDeviceOrderDraft` |
| `cancelDeviceOrderDraft` |
| `voidOpenRefundDeviceOrder` |
| `signDeviceOrder` |
| `signOffDeviceOrder` |
| `completeDeviceRequestAfterOutpatientCharge` |
| `executeDeviceOrder` |
| `revokeDeviceOrderExecution` |
| `createDeviceOrderRefund` |
| `completeDeviceRequestAfterRefund` |
| `createExecutedDeviceOrder` |
| `revokeExecutedDeviceOrder` |
| `cloneDeviceRequestForPreservedChargeRegeneration` |
| `bindLotNumberAfterDoctorStationFifoSign` |
| `bindDispenseLocationsAfterDoctorStationFifoSign` |
| `rebindPerformLocationForOutpatientNurseExecute` |

### QueryService：`DeviceRequestQueryService`

| 方法 |
|------|
| `mapListModelsByIds` |
| `existsByDeviceDefId` |
| `mustGetDeviceRequest` |
| `loadFifoSignContextOrThrow` |
| `existsInTransitRequestByDeviceDefId` |
| `listUnsignedDraftsByDeviceDefId` |
| `existsHardInTransitRequestByDeviceDefId` |
| `listDeviceRequestsByEncounter` |
| `listDeviceRequestsByEncounterIds` |
| `listDeviceRequestsByEncounterForDoctorStation` |
| `listDeviceRequestsByBasedOnAnchor` |
| `listDeviceRequestsByBasedOnAnchors` |
| `listDistinctEncounterIdsByPatientForDoctorStation` |
| `listDeviceRequestHistoryByPatient` |
| `assertDeviceRequestDeletable` |
| `assertDeviceRequestSignOffAllowed` |
| `offsetVerification` |
| `assertDeviceRefundableBeforePaymentRefundRequested` |
| `listByRefundDeviceIdIn` |
| `listInpatientNurseStationPricedOrders` |
| `getListModelById` |
| `mustGetListModelById` |
| `countReferencingEncounterDiagnosisId` |

