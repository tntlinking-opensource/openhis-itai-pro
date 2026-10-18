# DM-MED-002 MedicationDispense 药品发放

> **限界上下文**：用药域 · 药品发放管理  
> **聚合类型**：单表聚合（物理表 `med_medication_dispense`）  
> **版本**：v1.3.17

---

## 1. 概述

### 1.1 业务定义

药品发放（MedicationDispense）是药房根据药品申请（MedicationRequest）完成**调配、核对、发药**的事实记录，贯穿从待配药到已发放或退药完成的全生命周期。它是库存扣减、追溯码出入库、药品计费联动的业务锚点。

### 1.2 核心职责


| 职责       | 说明                                       |
| -------- | ---------------------------------------- |
| 发放生命周期管理 | 记录一次药品发放从草稿、待配药、已配药、已发放到退药/作废的完整状态流转     |
| 发放事实承载   | 承载药品、批号、数量、单位、药房、追溯码、配药人/发药人/时间等发放环节必需事实 |
| 库存协作锚点   | 提供库存扣减/回补所需的物资口径五元组（药品+批号+数量+单位+药房）      |
| 追溯码协作    | 为追溯码出入库提供业务锚点——发药记录 OUT、退药记录 IN          |
| 预减统计     | 草稿/待配药阶段的发放单数量计入"已占用待发量"，用于库存可用量展示与校验    |
| 汇总发放支持   | 住院场景按药房维度汇总多条发放明细，生成汇总发放申请与批量发药          |


### 1.3 适用范围


| 发放场景   | 说明                        |
| ------ | ------------------------- |
| 门诊窗口发药 | 患者凭处方到药房窗口取药：配药→核对→发药     |
| 住院病区送药 | 药房按医嘱配药后送至病区，护士接收确认       |
| 住院汇总发药 | 按药房维度汇总多条发放明细，批量配药发放      |
| 自动发药机  | 自动发药柜/机器人场景，系统自动推进配药与发药状态 |
| 退药     | 已发放药品退回药房，回补库存并关闭发放单      |


### 1.4 与 MedicationRequest 的关系

MedicationRequest（药品申请）是**请求侧**聚合，表达"医生开立了什么药"；MedicationDispense 是**执行侧**聚合，表达"药房发了什么药"。二者通过 `药品申请标识(MedicationRequestId)` 关联，一条请求可对应多条发放记录（拆零、多批次等）。

**医生站·多库房拆分示例（与签发 FIFO 对齐）**：同一 `med_req_id` 下允许存在**多条** `MedicationDispense` 物理行。例：经 **`ServiceLocationTemplate`** 与库存核对，药房 **A** 可发剩余 **1** 件、药房 **B** 可发剩余 **2** 件，医生在请求上开立总量 **3** 件且**不**在请求上指定发放药房——**签发**编排（`replaceFifoDraftDispensesAfterDoctorStationSign`，AS-IP-DOR-003 / AS-OP-DOR-003）按库房维 FIFO 分配后，生成**两条**发放行。**住院/门诊临时**在**医生站暂存**阶段**不**应存在发放草稿；历史数据若存在，以签发替换或签退释放收口（BR-MDP-015、BR-MRQ-028）。


| 对比维度 | MedicationRequest | MedicationDispense                     |
| ---- | ----------------- | -------------------------------------- |
| 业务角色 | 请求方（医生/护士）        | 执行方（药房）                                |
| 核心语义 | 药品申请/处方           | 药品调配发放                                 |
| 生命周期 | 开立→审核→执行→完成       | 与 `DispenseStatus` 一致：草稿→待配药→…→已发放/汇总/退药等（见 §2.2） |
| 数量含义 | 处方开立量             | 实际发放量                                  |
| 关联方向 | —                 | 通过 `药品申请标识(MedicationRequestId)` 指向请求侧 |


---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表聚合（与 §3 一致）。

聚合根对应表 `med_medication_dispense`，主键 `id`；一行表示一次药品发放事实（状态、追溯、与申请关联等）。若院内拆分子表（如明细行），以本稿 **§6.1**/Flyway 为准，本 DM 仍以发放单为聚合根口径。

**高频查询模式（建议索引口径）**：按 `med_req_id`、`encounter_id`、`patient_id`、`medication_id`、`status_enum`、`location_id`、发放时间；药房工作台按状态与药房维度筛选。

### 2.2 聚合根标识


| 属性     | 类型                     | 说明                 |
| ------ | ---------------------- | ------------------ |
| 药品发放标识 | `MedicationDispenseId` | 全局唯一标识，系统生成（雪花 ID） |


### 2.2 状态机（DispenseStatus）

与 **`DM-WKF-007 DeviceDispense`** 共用同一套 **`DispenseStatus`** 语义与持久化枚举；以下图示、状态定义表及编码值对照与 WKF-007 **§2.3 / §4.1** 一致，仅业务语境为药品发放。

```text
[DRAFT(草稿)] ──> [PREPARATION(待配药)] ──> [IN_PROGRESS(待发药)] ──> [COMPLETED(已发放)]
                        │                                                     │
                        ├──> [ON_HOLD(暂停)] ──> [STOPPED(停止)]              ├──> [SUMMARIZED(已汇总)]
                        ├──> [PREPARED(已配药)]                               ├──> [CANCELLED(撤回)]
                        └──> [DECLINED(拒绝)] ──> [STOPPED]                  ├──> [REFUNDED(已退药)]
                                                                             └──> [PART_COMPLETED] ──> [PART_REFUND/PENDING_REFUND]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | DRAFT | 草稿 | 发放草稿，可修改/删除，允许修改/删除/执行 |
| 2 | PREPARATION | 待配药 | 执行后生成，等待配药，允许配药/暂停/拒绝 |
| 3 | PREPARED | 已配药 | 已配药待发，允许发药 |
| 4 | IN_PROGRESS | 待发药 | 配药完成，等待发药，允许发药/停止 |
| 5 | COMPLETED | 已发放 | 发放完成，允许汇总/撤回/退药 |
| 6 | SUMMARIZED | 已汇总 | 汇总中间态 |
| 7 | PART_COMPLETED | 部分发药 | 分次发放中，允许继续发放/退药 |
| 8 | ON_HOLD | 暂停 | 暂停执行，允许恢复/停止 |
| 9 | STOPPED | 停止 | 停止/作废 |
| 10 | DECLINED | 拒绝 | 拒绝发药，允许停止 |
| 11 | PENDING_REFUND | 待退药 | 申请退药中，允许确认退药 |
| 12 | PART_REFUND | 部分退药 | 部分退药完成 |
| 13 | REFUNDED | 已退药 | 全部退药完成 |
| 14 | CANCELLED | 撤回 | 已发放后撤回 |
| 未知    | `UNKNOWN`       | 历史迁移场景   | —                  |

> **状态枚举说明**：持久化字段 `status_enum` 为 **String**，存储 **`DispenseStatus` 的 code（英文常量名）**，与语义一一对应；数值 `value` 仅用于文档排序/对照，不作为落库事实源。与 **FHIR `MedicationDispense.status`** 的展示映射见 **§9**（FHIR 亦为字符串值域，但值域不同：不得把 FHIR 字符串直接当作 `status_enum` 的 code 写入同一列）。

#### 编码值对照（与 `DM-WKF-007` §4.1 一致）

| 编码值 | code（缩写） | 名称    |
| --- | --------- | ----- |
| 1   | `DR`      | 草稿    |
| 2   | `PR`      | 待配药   |
| 3   | `IN`      | 待发药   |
| 4   | `CO`      | 已发放   |
| 5   | `ON`      | 暂停    |
| 6   | `ST`      | 停止    |
| 7   | `DE`      | 拒绝发药  |
| 8   | `SR`      | 已汇总   |
| 9   | `PC`      | 部分发药  |
| 10  | `PFR`     | 部分退药  |
| 12  | `RE`      | 已退药   |
| 13  | `UN`      | 未知    |
| 14  | `PD`      | 已配药   |
| 15  | `CA`      | 撤回    |
| 16  | `PRD`     | 待退药   |

> **存储口径**：`status_enum` 使用 **字符串编码（存枚举 code）**；本节 `code（缩写）` 列仅作展示缩写，不作为落库码值；发生冲突时以**实现枚举的 code**为权威。

---

## 3. 实体

```text
MedicationDispense（聚合根，单表聚合）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                 MedicationDispense                    │
  │  - busNo / statusEnum / medReqId / medicationId       │
  │  - quantity / unitCode / lotNumber / traceNo / summaryNo │
  └──────────────────────────────────────────────────────┘
```

### 3.1 药品发放（MedicationDispense）

MedicationDispense 聚合根属性，对应 `med_medication_dispense` 表。下列「领域属性名」与物理列语义以 **本节 §3.1** 与 `**§5.1**/**§6.1** 为锚；Java 属性命名以仓储层命名为准。`rec_practitioner_id` 等列物理类型（`varchar` / `bigint`）以迁移脚本与本稿 **§3** 为准；早期版本 v1.3 曾混记 `String`/`Long`，以本稿 §3.1 与 本稿 **§6.1** 为权威。


| 属性     | 字段名                         | 类型                          | 必填  | 说明                   |
| ------ | --------------------------- | --------------------------- | --- | -------------------- |
| 发放业务号  | `bus_no`                    | `String`                    | 是   | 唯一业务号                |
| 发放状态   | `status_enum`               | `String`（`DispenseStatus` code） | 是   | 见 §2.2（图示、状态定义与编码对照） |
| 未发药原因  | `not_performed_reason_enum` | `String`                   | 否   | 仅作废/拒绝发放时写入（存原因枚举 code） |
| 状态变更时间 | `status_changed_time`       | `DateTime`                  | 是   | 状态变更时间戳              |
| 发放类型   | `dispense_enum`             | `String`                   | 是   | 发放类型枚举（存枚举 code）        |
| 药品定义标识 | `medication_id`             | `Long`                      | 是   | 关联药品主数据              |
| 患者标识   | `patient_id`                | `Long`                      | 是   | 关联患者                 |
| 就诊标识   | `encounter_id`              | `Long`                      | 是   | 关联就诊                 |
| 发放药房标识 | `location_id`               | `Long`                      | 是   | 药房位置                 |
| 药品申请标识 | `med_req_id`                | `Long`                      | 是   | 关联药品申请               |
| 应发数量   | `quantity`                  | `Decimal`                   | 是   | 发放单位下应发量             |
| 发放单位   | `unit_code`                 | `String`                    | 是   | 发放计量单位               |
| 已发数量   | `dispense_quantity`         | `Decimal`                   | 否   | 实际已发数量               |
| 产品批号   | `lot_number`                | `String`                    | 否   | 药品批号                 |
| 追溯码    | `trace_no`                  | `String`                    | 否   | 药品追溯码                |
| 配药人标识  | `preparer_id`               | `Long`                      | 否   | 配药人                  |
| 配药时间   | `prepare_time`              | `DateTime`                  | 否   | 配药时间戳                |
| 发药人标识  | `practitioner_id`           | `Long`                      | 否   | 发药人                  |
| 发药时间   | `dispense_time`             | `DateTime`                  | 否   | 发药时间戳                |
| 有效期限   | `limit_time`                | `DateTime`                  | 否   | 预减统计有效截止时间；草稿 +2h、待发 +24h（见 **§9.6**）           |
| 计划发药时间 | `planned_dispense_time`     | `DateTime`                  | 否   | 计划发药时间               |
| 执行记录标识 | `procedure_id`              | `Long`                      | 否   | 关联执行记录               |
| 汇总单号   | `summary_no`                | `String`                    | 否   | 住院汇总发药单号             |
| 用药说明快照 | `dosage_instruction`        | `String`                    | 否   | 开立时用药说明快照；快照语义见 §4.3 |
| 用法编码   | `method_code`               | `String`                    | 否   | 用法快照（§4.3）           |
| 频次编码   | `frequency_code`            | `String`                    | 否   | 频次快照（§4.3）           |
| 单次剂量   | `dose`                      | `BigDecimal`                | 否   | 剂量快照（§4.3）           |
| 剂量单位编码 | `dose_unit_code`            | `String`                    | 否   | 剂量单位快照（§4.3）         |
| 最大剂量单位 | `max_unit`                  | `String`                    | 否   | 最大剂量单位快照（§4.3）       |
| 发药目的地  | `des_location_id`           | `Long`                      | 否   | 发药目的地标识              |
| 接收人    | `rec_practitioner_id`       | `Long`                      | 否   | 接收人标识                |
| 支持用药信息 | `support_info`              | `String`                    | 否   | 辅助用药信息               |
| 发药类型编码 | `part_type_code`            | `String`                    | 否   | 分次发放类型               |
| 发药频次编码 | `dispense_frequency_code`   | `String`                    | 否   | 发药频次                 |
| 交账批次标识 | `cashier_settlement_id`     | `Long`                      | 否   | 非空 = 已纳入某药房交账批次（含待审）；逻辑指向 `fin_cashier_settlement.id`（无物理外键）；见 **DM-FIN-003** / DD 交账日结改造 |


> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `HisBaseEntity` / 统一基类，遵循统一约定。

**交账挂载（与 DM-FIN-003 联动）**：

- 提交药房日结时批量写入；须行级仍为 `NULL`，否则整单失败。
- 撤回 / 待审驳回清空；审批通过不改挂载。
- 交账候选时间轴为 `dispense_time`：**不允许为空**（候选组装遇空报错，不回落其它时间字段）。
- 交账 `status_enum` **不是**退药门禁；已挂载正向行仍可开对冲退药新行再交下一批。
- 建议索引：`(cashier_settlement_id)`；未交账扫描 `(location_id, dispense_time, cashier_settlement_id)`。

### 3.2 子实体

本聚合为单表聚合，无聚合内子实体。每条记录对应一次独立的药品发放事实（一品一单）。若后续演进为「一单多行」，再在本节补充子实体名称与职责。

---

## 4. 值对象（含枚举）

### 4.1 发放状态快照（MedicationDispenseStatusSnapshot）

封装发放状态与未发药原因的联合语义，收口所有状态迁移判断，避免裸 `statusEnum` 散落比较。

> **与 §2.2 对齐**：中文业务态与 `DispenseStatus` 英文常量一一对应，同 **`DM-WKF-007`** 口径；下列方法中的自然语言态名均可替换为表中「英文」列常量理解。

| 属性    | 类型                   | 说明        |
| ----- | -------------------- | --------- |
| 发放状态  | `DispenseStatus`     | 状态枚举      |
| 未发药原因 | `NotPerformedReason` | 仅作废/拒绝时有值 |


**行为方法**：

- `allowsPrepare()`：是否允许配药（仅 `PREPARATION` / 待配药成立）
- `allowsDispense()`：是否允许核对发药（待发药窗口：`PREPARED` 与/或 `IN_PROGRESS`，与 **BR-MDP-003** 收口一致）
- `allowsCancel()`：是否允许作废（未完成发药类非终态子集，以实现 BR 为准）
- `allowsRefund()`：是否允许退药（仅 `PENDING_REFUND` / 待退药成立）
- `isTerminal()`：是否为终态（`COMPLETED`/`DECLINED`/`REFUNDED`/`STOPPED`/`CANCELLED`/`SUMMARIZED`/`PART_REFUND`/`UNKNOWN` 等以 **§4** 枚举表及落库为准）

### 4.2 库存扣减口径（DispenseInventoryDeductionSpec）

封装与库存扣减/回补协作的物资五元组，作为本聚合对外传递库存协作信息的唯一视图。


| 属性     | 类型                       | 说明            |
| ------ | ------------------------ | ------------- |
| 药品定义标识 | `MedicationDefinitionId` | 药品 ID         |
| 产品批号   | `String`                 | 批号            |
| 数量     | `Decimal`                | 本单应发数量（发放单位下） |
| 单位编码   | `String`                 | 发放单位          |
| 药房标识   | `LocationId`             | 发放药房          |


> **不变式**：参与库存扣减/回补时，五元组必须齐全且数量 > 0（`validateCompleteForStockOperation()`）。

### 4.3 用药说明快照（MedicationUsageInstructionSnapshot）

封装发放侧用药说明事实，发放单生成时从医嘱整体拷贝，后续不得静默改写。


| 属性     | 类型        | 说明     |
| ------ | --------- | ------ |
| 用法编码   | `String`  | 给药途径编码 |
| 用药频次编码 | `String`  | 频次编码   |
| 单次剂量   | `Decimal` | 单次给药剂量 |
| 剂量单位   | `String`  | 剂量计量单位 |
| 单次最大剂量 | `Decimal` | 安全上限   |
| 用药说明   | `String`  | 文本说明   |
| 支持用药信息 | `String`  | 辅助信息   |


### 4.4 追溯码载荷（TraceIdentifierBundle）

封装追溯码单列存储语义，收口逗号分隔多码的解析、规范化与校验前置条件。


| 属性    | 类型       | 说明            |
| ----- | -------- | ------------- |
| 追溯码原文 | `String` | 持久化单列，多码以逗号分隔 |


**行为方法**：

- `normalizedRaw()`：去首尾空白后返回规范化原文
- `individualCodes()`：按逗号拆分为独立编码列表，逐条参与校验
- `isBlank()`：是否为空（配药/发药前置校验用）

### 4.5 发放类型（DispenseType）


| 枚举值    | 说明           |
| ------ | ------------ |
| 门诊发药   | 门诊窗口发药       |
| 住院发药   | 住院病区送药（长期医嘱） |
| 住院临时发药 | 住院临时医嘱发药     |
| 退药发放   | 退药场景的反向发放记录  |


### 4.6 未发药原因（NotPerformedReason）


| 枚举值  | 说明            |
| ---- | ------------- |
| 库存不足 | 药房无库存或库存不足    |
| 医嘱取消 | 关联医嘱已被取消/撤回   |
| 患者拒绝 | 患者拒绝取药        |
| 处方过期 | 处方超过有效期限      |
| 药品停用 | 药品已被停用/召回     |
| 其他   | 其他原因（需在备注中说明） |


---

## 5. 领域事件

### 5.1 事件定义


| 事件名称（中文） | 英文名称                                          | 触发时机          | 携带数据 / 最小载荷        | 消费方                    |
| -------- | --------------------------------------------- | ------------- | ------------------- | ---------------------- |
| 药品发放单已生成 | `MedicationDispenseCreatedEvent`              | 发放单生成（草稿或待配药） | 发放标识、药品申请标识、药品定义标识、就诊标识、发放单业务号、状态枚举、配药类型枚举、患者标识、科室标识、数量、单位编码、**产品批号（`lotNumber`，住院 FIFO/执行路径可非空）**、处方号、开单机构标识、申请人标识 | 库存预减/药房工作台、QueryService（若启用）    |
| 发放单草稿已推进待配药 | `MedicationDispensePromotedToPreparationEvent` | 草稿批量推进待配药（含门诊收费 **IF-OPC-07**） | 发放标识、药品申请标识、操作人、`status_enum_code`（`DispenseStatus#getCode()`） | 处方视图、发药单QueryService、药房工作台 |
| 药品已配药    | `MedicationDispensePreparedEvent`             | 配药成功          | 发放标识、配药人、配药时间      | 药房工作台、追溯（若启用）          |
| 药品已发放    | `MedicationDispensedEvent`                    | 核对发药成功        | 发放标识、药品定义标识、批号、数量  | 计费/ChargeItem 编排、追溯码（若启用） |
| 药品发放已作废  | `MedicationDispenseDeclinedEvent`             | 作废未发成功        | 发放标识、未发药原因         | 库存回补、QueryService               |
| 药品已退药    | `MedicationDispenseRefundedEvent`             | 退药成功          | 发放标识、退药数量          | 库存回补、计费冲正              |
| 药品发放已汇总  | `MedicationDispenseSummarizedEvent`           | 汇总绑定成功        | 发放标识、汇总单号          | 住院药房批量发药视图             |
| 汇总发药已完成  | `MedicationDispenseSummaryCompletedEvent`     | 汇总批量发药成功      | 汇总单号、发放标识列表        | 病区/护士站、计费              |
| 汇总发药已取消  | `MedicationDispenseSummaryCancelledEvent`     | 取消汇总成功        | 汇总单号               | **AS-IP-NUR-010** IF-IP-SUM-08（护士撤销待发送）、**AS-IP-PHA-002** IF-IPS-04（药房驳回）→ 待汇总明细刷新 |
| 退药申请已生成  | `MedicationDispensePendingRefundCreatedEvent` | 退药申请生成        | 发放标识、退药数量          | 退费窗口、库存                 |
| 预减占用已释放  | `MedicationDispenseReserveReleasedEvent`      | 预减占用释放        | 发放标识、释放数量          | 库存可用量展示                |
| 发放单已挂接执行记录 | `DispenseProcedureLinkedEvent` | 临时医嘱执行后挂接已有发放单并推进待配药（**IF-IP-EXE-02**） | 发放标识、药品申请标识、执行记录标识、计划发药时刻、操作人、`status_enum_code` | 发药单QueryService、药房工作台 |


事件载荷以 `zxplat-domain` / 应用层 本稿 **§6.1** 为准。

### 5.2 事件触发规则


| 当前状态      | 操作            | 目标状态     | 发布事件                                          |
| --------- | ------------- | -------- | --------------------------------------------- |
| —         | 生成发放单（临时医嘱开立） | 草稿       | `MedicationDispenseCreatedEvent`              |
| —         | 生成发放单（长期医嘱执行） | 待配药      | `MedicationDispenseCreatedEvent`              |
| 草稿        | 推进为待配药        | 待配药      | `MedicationDispensePromotedToPreparationEvent` |
| 草稿        | 临时医嘱执行挂接 Procedure | 待配药 | `DispenseProcedureLinkedEvent` |
| 待配药       | 配药            | 已配药      | `MedicationDispensePreparedEvent`             |
| 已配药       | 核对发药          | 已发放      | `MedicationDispensedEvent`                    |
| 待配药 / 已配药 | 作废未发          | 已拒绝      | `MedicationDispenseDeclinedEvent`             |
| 待配药       | 汇总            | 已汇总      | `MedicationDispenseSummarizedEvent`           |
| 已汇总       | 汇总发药          | 已发放      | `MedicationDispenseSummaryCompletedEvent`     |
| 已汇总       | 取消汇总          | 待配药      | `MedicationDispenseSummaryCancelledEvent`     |
| 已汇总       | 作废            | 已拒绝      | `MedicationDispenseDeclinedEvent`             |
| —         | 生成待退药单        | 待退药      | `MedicationDispensePendingRefundCreatedEvent` |
| 待退药       | 退药            | 已退药      | `MedicationDispenseRefundedEvent`             |
| 草稿 / 待配药  | 释放预减          | 已停止 / 删除 | `MedicationDispenseReserveReleasedEvent`      |

> **状态名与常量**：上表「草稿、待配药、已配药、已发放、已汇总、待退药、已退药、已拒绝、已停止」等中文列与 **§2.2** 中 `DRAFT`/`PREPARATION`/`PREPARED`/`COMPLETED`/`SUMMARIZED`/`PENDING_REFUND`/`REFUNDED`/`DECLINED`/`STOPPED` 等同义；若编排中存在 `IN_PROGRESS` 衔接，以实现状态机为准。

---

## 6. 领域服务

### 6.1 写侧领域服务（MedicationDispenseDomainService）

> 写侧由应用服务编排事务边界后调用领域服务完成状态与事实收口。命令类型、幂等键、异常码字面量以 `**§5.1**/**§6.1** 为准。

#### 6.1.1 配药（`prepareMedicationDispense`）

- **业务意义**：药房完成拣药并绑定批号、追溯码与数量口径，将发放单从待配药推进为已配药，供后续核对发药。
- **边界说明**：仅写本聚合；不在本方法内完成库存账面扣减（发药成功时由协作方处理，见 BR-MDP-005）。**批号**可在配药环节由药房人工确认或调整并写入本动作；门诊「读侧批号推荐不落库」与住院执行后写侧落库为不同机制，与 BR-MDP-008 口径一致。
- **入参**：药品发放标识、配药人标识、追溯码、批号、数量（及命令要求的其他字段，以本稿约定为准）。
- **出参**：—。
- **前置条件**：当前状态须为待配药（BR-MDP-002）；追溯码须通过「已出库不可重复使用」校验（BR-MDP-009）；批号效期等约束见 BR-MDP-014；临时医嘱多条待发时须满足待发量合计校验（BR-MDP-018）。
- **状态变化**：待配药 → 已配药。
- **聚合内影响**：写入配药人、配药时间、追溯码、批号及数量相关字段；刷新 `status_changed_time`。
- **事件输出**：`MedicationDispensePreparedEvent`。
- **失败语义**：状态不允许、追溯码命中重复出库、必填事实缺失时拒绝。

#### 6.1.2 核对发药（`dispenseMedication`）

- **业务意义**：核对无误后确认发药事实，推进为已发放并触发下游库存扣减与计费协作（由订阅/编排完成）。
- **边界说明**：不写 `ChargeItem` 本体、不写库存台账行；仅保证本聚合内发药事实与状态原子一致（BR-MDP-004）。
- **入参**：药品发放标识、发药人标识、已发数量（及命令要求的其他字段，以本稿约定为准）。
- **出参**：—。
- **前置条件**：处于待发药窗口（`PREPARED` 与/或 `IN_PROGRESS`，与 **BR-MDP-003** 一致）；库存扣减五元组须完整且数量大于零（BR-MDP-008）；毒麻双人规则须满足（BR-MDP-013）。
- **状态变化**：待发药窗口 → `COMPLETED`（是否经历 `IN_PROGRESS` 以实现编排为准）。
- **聚合内影响**：写入发药人、发药时间、已发数量等；刷新状态时间。
- **事件输出**：`MedicationDispensedEvent`。
- **失败语义**：状态不允许、库存不足（协作方返回时上抛）、五元组不完整、效期不合法时拒绝。

#### 6.1.3 作废未发（`cancelMedicationDispense`）

- **业务意义**：对未完成发药的单据作废，固定未发药原因并进入已拒绝终态，关闭后续发药路径。
- **边界说明**：不写退费金额；下游回补预减或冲销协作由事件/编排处理。
- **入参**：药品发放标识、未发药原因（`NotPerformedReason`）。
- **出参**：—。
- **前置条件**：须为允许作废的未完成发药类状态（见 §5.2）；须写入原因（BR-MDP-011）。
- **状态变化**：进入已拒绝（`DECLINED`，以 **§4** 枚举表及落库为准）。
- **聚合内影响**：写入 `not_performed_reason_enum` 及状态相关字段。
- **事件输出**：`MedicationDispenseDeclinedEvent`。
- **失败语义**：已发放等终态不允许作废、原因缺失时拒绝。

#### 6.1.4 退药确认（`refundDispensedMedication`）

- **业务意义**：在待退药态确认实物退回，推进为已退药并驱动库存回补与追溯码入库协作。
- **边界说明**：不写费用冲销明细本体；退费语义由收费域订阅处理。**退药链路禁止读写原正向 COMPLETED 发药单**（不查原单、不改 `status_enum`）——发药统计计正向行（`dispense_enum` 非 REFUND 且 `COMPLETED`），退药统计计 `dispense_enum=REFUND` 且 `REFUNDED` 的对冲行；二者形成数量对冲。应用层签退/校对退回门控（`InpatientAdviceRevokeGate` / **BR-IP-AUD-013**、**EX-IP-ORD-103**）须按同一净额口径判断「净已发」，**禁止**仅因正向行仍为 `COMPLETED` 即拦截。
- **入参**：药品发放标识（待退 REFUND 行）、退药人标识、退药数量（及命令要求的其他字段，以本稿约定为准）。
- **出参**：—。
- **前置条件**：当前状态须为待退药（BR-MDP-006）；须幂等防重复退药（BR-MDP-006）。
- **状态变化**：仅待退 REFUND 行：待退药 → 已退药；**与原发药单无关联**。
- **聚合内影响**：写入退药数量、退药人、退药时间等（仅待退行）。
- **事件输出**：`MedicationDispenseRefundedEvent`（仅待退行）。
- **失败语义**：状态不允许、重复退药、数量非法时拒绝。

#### 6.1.5 由临时医嘱生成发放单（`generateDispenseFromTemporaryOrder`）

- **业务意义**：领域能力保留，供**非医生站暂存**的临时路径（如护士执行、其它编排）在需要时生成草稿发放单；**门诊/住院医生站暂存**（AS-OP-DOR-003、AS-IP-DOR-003）**不**调用本方法，发放仅在**签发 FIFO** 阶段建立（BR-MDP-015、BR-MRQ-028）。
- **边界说明**：从申请侧拷贝用药说明快照等事实（BR-MDP-016）；不替代医嘱签发状态机。
- **入参**：药品申请标识、药品/数量/药房等生成命令（以本稿 **§6.1** 字段为准）。
- **出参**：药品发放标识（新建主键）。
- **前置条件**：须满足临时路径与查重策略（BR-MDP-001、BR-MDP-015）；业务号唯一。
- **状态变化**：新建为草稿。
- **聚合内影响**：生成 `bus_no`；写入申请引用、患者/就诊/药房/数量单位及快照字段。
- **事件输出**：`MedicationDispenseCreatedEvent`。
- **失败语义**：重复创建、申请引用非法、必填缺失时拒绝。

#### 6.1.6 草稿推进为待配药（`promoteToPreparation`）

- **业务意义**：门诊收费确认等节点将草稿批量推进为待配药，进入药房主流程而不新建单据。
- **边界说明**：不生成新发放标识；仅状态迁移与必要时间字段。
- **入参**：药品申请标识集合（或等价批量定位键，以本稿约定为准）。
- **出参**：—。
- **前置条件**：目标单据须处于草稿；调用时机由应用编排（如收费完成）约定。
- **状态变化**：草稿 → 待配药。
- **聚合内影响**：批量更新状态及 `status_changed_time` 等。
- **事件输出**：`MedicationDispensePromotedToPreparationEvent`（逐条发放单；载荷见 **§5.1**）。
- **失败语义**：存在非草稿、标识不存在或无权访问时拒绝或部分失败策略以落库与交互契约为准。

#### 6.1.7 由长期执行生成发放单（`generateDispenseFromExecution`）

- **业务意义**：长期医嘱执行后**新建**发放单并直接进入待配药，支撑病区汇总与发药工作台（**AS-IP-NUR-004 BR-IP-EXE-016**）。
- **边界说明**：与临时路径「挂接已有 Dispense」区分（**§6.1.7a**）；FIFO 分配的 `locationId`、`lotNumber` 由应用层编排写入命令（**禁止**写死 req.performLocation）。
- **入参**：`GenerateDispenseFromExecutionCommand`——`medReqId`、`procedureId`、`plannedDispenseTime`、`locationId`、`lotNumber`（FIFO 后，可空）、`quantity`、`unitCode`、`medicationId`、`encounterId`、`patientId`。
- **出参**：药品发放标识。
- **前置条件**：长期路径；执行引用合法；应用层库存 Gate 已通过。
- **状态变化**：新建为 **待配药**（`initPreparation`）。
- **聚合内影响**：写入 `procedure_id`、`planned_dispense_time`、`lot_number` 等与执行锚点相关字段。
- **事件输出**：`MedicationDispenseCreatedEvent`。
- **失败语义**：执行引用冲突、申请非法、必填缺失时拒绝。

#### 6.1.7a 临时医嘱执行挂接发放单（`linkDispenseProcedure`）

- **业务意义**：临时医嘱执行完成后，将**签发阶段已生成**的草稿发放单挂接 `procedureId` 并推进 **PREPARATION**（**AS-IP-NUR-004 BR-IP-EXE-017**）。
- **边界说明**：不新建发放标识；仅更新已有草稿行；与 `promoteToPreparation`（批量、不写 Procedure 锚点）区分。
- **入参**：`LinkDispenseProcedureCommand`——`dispenseId`、`procedureId`、`plannedDispenseTime`、`operatorId`。
- **出参**：—。
- **前置条件**：目标发放单须为 **草稿**；`procedureId` 非空。
- **状态变化**：草稿 → **待配药**；写入 `procedure_id`、`planned_dispense_time`。
- **聚合内影响**：更新挂接字段并推进状态。
- **事件输出**：`DispenseProcedureLinkedEvent`（**save 之后**发布）。
- **失败语义**：非草稿、发放单不存在、Procedure 缺失时拒绝。

#### 6.1.8 生成待退药单（`createPendingRefundDispense`）

- **业务意义**：基于已发/部分发事实生成待退药发放记录，衔接 **AS-OP-DOR-004** / **AS-OP-PHA-002**（门诊退药申请）与 **AS-IP-NUR-012**（住院取消执行已发退药，**BR-IP-CXL-010**）。
- **边界说明**：`med_req_id` 由 `refundRequestId` 写入——门诊为退药申请主键，住院取消执行为原发放单关联药嘱主键；可选 `procedure_id` 挂接执行记录；**不写原单物理删除、不改原正向行 `status_enum`**。
- **入参**：`CreatePendingRefundDispenseCommand`——`originDispenseId`、`refundRequestId`、`refundQuantity`、`operatorId`；`procedureId` **可选**（住院取消执行**必填** = 新 CANCEL 行 id，与正向执行发放单挂 COMPLETED 行对称）。
- **出参**：新药品发放标识。
- **前置条件**：原单 `ensureRefundable`；`refundRequestId` 非空；防重复申请。
- **状态变化**：新建为 `PENDING_REFUND`。
- **聚合内影响**：拷贝关键字段（含 **`lot_number`、`trace_no`**，以及原单 **`summary_no`、用法用量摘要字段**（`dosage_instruction`/`method_code`/`frequency_code`/`dose`/`dose_unit_code` 等）、`planned_dispense_time`、`des_location_id`**）；`dispenseEnum=REFUND`；若传入 `procedureId` 则写入 `procedure_id`；事件载荷含 `originDispenseId`、`procedureId`（若有）。
- **事件输出**：`MedicationDispenseCreatedEvent` + `MedicationDispensePendingRefundCreatedEvent`（持久化成功后发布）。
- **失败语义**：重复申请、原单状态不允许、`refundRequestId` 缺失时拒绝。

#### 6.1.9 释放预减占用（`releaseDispenseReserve`）

- **业务意义**：在医嘱取消、停嘱或删除未发等场景释放草稿/待配药占用，避免可用量长期被锁；**门诊 IF-DOR-16 签退**对 **DRAFT / PREPARATION** 行执行 **`markDeleted` 逻辑删除**。
- **边界说明**：可涉及逻辑删除或推进为已停止等策略，以实现与 BR-MDP-010 一致；不替代库存域最终可用量。
- **入参**：药品申请标识（或批量键，以本稿约定为准）。
- **出参**：—。
- **前置条件**：定位到须释放的占用集合；幂等策略以落库与交互契约为准。
- **状态变化**：签退/FIFO 替换路径对 **DRAFT / PREPARATION** 行 **`is_deleted=true`**（保留原 `status_enum`）；其他场景可进入 `STOPPED`（见 §5.2）。
- **聚合内影响**：逻辑删除或更新状态及关联占用字段。
- **事件输出**：`MedicationDispenseReserveReleasedEvent`。
- **失败语义**：无可释放记录、并发冲突时拒绝或 noop 策略以落库与交互契约为准。

#### 6.1.10 汇总发放明细（`summarizeMedicationDispenses`）

- **业务意义**：住院按药房维度将多条待配药明细绑定同一汇总单号，进入已汇总态以支持批量发药（BR-MDP-012）。
- **边界说明**：不写物资申请本体；与 `SupplyRequest` 等下游为编排协作。
- **入参**：药房标识、发放标识列表（以本稿约定为准）。
- **出参**：汇总单号。
- **前置条件**：明细须处于允许汇总的状态；防重复汇总（BR-MDP-012）。
- **状态变化**：待配药 → 已汇总。
- **聚合内影响**：批量写入 `summary_no` 与状态。
- **事件输出**：`MedicationDispenseSummarizedEvent`。
- **失败语义**：跨药房混选、状态不允许、重复汇总时拒绝。

#### 6.1.11 取消汇总（`cancelMedicationDispenseSummary`）

- **业务意义**：撤销汇总绑定，清空汇总单号并回到待配药，以便重新分单或作废。
- **边界说明**：不自动冲销已发生的物资出库事实；**不**修改 **SupplyRequest** 审批态——Supply 侧撤销/驳回/逻辑删除由应用层另调 **DM-WKF-006**（见下「应用编排」）。
- **入参**：汇总单号（`summaryNo`）、操作人标识。
- **出参**：—。
- **前置条件**：汇总单号下明细须为 **已汇总**（`SUMMARIZED`）态（**BR-MDP-012**）；`ensureSummaryCancellable` 门控。
- **状态变化**：已汇总 → 待配药。
- **聚合内影响**：清空 `summary_no` 并回写状态。
- **事件输出**：`MedicationDispenseSummaryCancelledEvent`（逐行发布，载荷含 `summaryNo`、发放行 ID、回退后 `status_enum` code）。
- **失败语义**：单号不存在、明细非 `SUMMARIZED`、存在不允许取消的下游锁定时拒绝。
- **应用编排（跨域，本方法仅负责发放行）**：

| 场景 | 应用服务 | IF | Supply 侧编排（邻域） | 说明 |
| --- | --- | --- | --- | --- |
| 护士撤销待发送汇总 | **AS-IP-NUR-010** | IF-IP-SUM-08 | 同事务先本方法，再 **DM-WKF-006** `removeByBusNo`（type **27**、**PENDING_APPROVAL**） | 发放行回待汇总池；Supply 汇总单逻辑删除 |
| 药房驳回待核发汇总 | **AS-IP-PHA-002** | IF-IPS-04 | 同事务先 **DM-WKF-006** `rejectRequest`（→ **REJECT**），再本方法 | Supply 保留驳回历史；发放行回待汇总池 |

#### 6.1.12 完成汇总发药（`completeSummaryDispense`）

- **业务意义**：对同一 `summary_no` 下 **SUMMARIZED**（待发核发中间态）批量确认发药，推进为 **COMPLETED** 终态并写入配药人/发药人/配药时间/发药时间；发布汇总完成事件供库存/计费消费。
- **边界说明**：**发放表**（`med_medication_dispense`）为核发业务终态真源；**SupplyRequest AGREE** 仅为审批链中转，**不得**替代本方法对发放行的更新。是否汇总路径以 **`summary_no` 非空** 识别；**SUMMARIZED 不是终态**。读路径（**AS-IP-PHA-002** IF-IPS-01/02）「已发药」仅以发放行 **COMPLETED** 为准；待发详情另断言 Supply **APPROVAL**（不接受客户端审批态入参）。
- **入参**：汇总单号、发药人标识、调配药师标识（及操作人，以命令为准）。
- **出参**：—。
- **前置条件**：同号明细须 **SUMMARIZED** 且 `summary_no` 一致；发药人/调配药师非空（**BR-IPS-005** 由应用层 Gate）。
- **状态变化**：**SUMMARIZED → COMPLETED**（保留 `summary_no`）。
- **聚合内影响**：批量写入 `preparer_id`、`prepare_time`、`practitioner_id`、`dispense_time`、`dispense_quantity` 与 `status_enum`。
- **事件输出**：`MedicationDispenseSummaryCompletedEvent`。
- **失败语义**：部分失败时全单回滚；校验失败拒绝提交。

#### 6.1.13 回退未发（临时路径）（`rollbackUndispensedDispense`）

- **业务意义**：临时医嘱路径下将待配药或已汇总但未发单据回退为草稿，保留单据主键用于再次推进（与删除未发路径区分）。
- **边界说明**：非物理删除；不写库存扣减事实。
- **入参**：药品发放标识列表。
- **出参**：—。
- **前置条件**：目标状态须为允许回退的集合（待配药/已汇总，见 §5.2 口径）；与长期路径 `removeUndispensedDispense` 不得混用（BR-MDP-015）。
- **状态变化**：待配药/已汇总 → 草稿（语义以落库与交互契约为准）。
- **聚合内影响**：回写状态与汇总号清理策略以落库与交互契约为准。
- **事件输出**：以实现是否发布补充事件为准；未统一命名则不写死。
- **失败语义**：状态不允许、列表含非法标识时拒绝。

#### 6.1.14 删除未发（长期路径）（`removeUndispensedDispense`）

- **业务意义**：长期路径下物理删除未进入已发放的发放明细并释放预减，用于执行撤销等场景。
- **边界说明**：与临时路径回退语义不同（BR-MDP-015）；删除前须无下游硬依赖或已由编排解除。
- **入参**：药品发放标识列表。
- **出参**：—。
- **前置条件**：目标须为允许删除的未发放集合；须释放预减占用（与 `releaseDispenseReserve` 策略协调，以落库与交互契约为准）。
- **状态变化**：物理删除或等价移除（以实现持久化策略为准）。
- **聚合内影响**：删除主表行或逻辑删除策略以落库与交互契约为准。
- **事件输出**：若与释放预减同事务，可伴随 `MedicationDispenseReserveReleasedEvent` 或等价事件，以落库与交互契约为准。
- **失败语义**：已配药/已发放等不可删状态、引用未释放时拒绝。

#### 6.1.15 医生站签发后按 FIFO 重写草稿发放（`replaceFifoDraftDispensesAfterDoctorStationSign`）

- **业务意义**：**AS-OP-DOR-003 · IF-DOR-15** / **AS-IP-DOR-003 · IF-IP-DOR-15** 路径下，医嘱请求已由域 `signMedicationOrder` 生效后，应用层已按模板库位与库存可用完成分段；本动作**幂等替换**该请求下草稿态发放行集，使每条物理发放承载真实 **`location_id`** / **`lot_number`** / **分段数量**，与 **`ChargeItem`** **锚发放表主键**（**`DM-ADM-011`** **`BR-CHG-001`**）对齐。
- **边界说明**：不写 **`ChargeItem`**；不在此接口内做门诊收费状态迁移；命令载荷 **`ReplaceFifoMedicationDispenseDraftsCommand`**（分段列表）由应用装配生成。
- **入参**：操作者、就诊/患者/药品/单位、`med_req_id`、分段集合（库位、批号、数量）等（以命令定稿为准）。
- **出参**：本请求替换后的 **`med_medication_dispense` 主键列表**（顺序与分段一致，供下游批量挂费）。
- **前置条件**：请求已签发；分段总量与申请数量一致（或与产品口径一致的溢出策略）；仅改写允许替换的草稿/预占类状态集合（以实现状态机为准）。
- **状态变化**：删除或废止旧草稿发放事实并插入新草稿行，或等价替换策略以实现为准。
- **事件输出**：以实现是否发布「批量替换」类事件为准；须满足QueryService与库存预减一致性。
- **失败语义**：库存不足、分段非法、请求状态不允许时拒绝。

#### 6.1.16 汇总核发后分配追溯码（`assignTraceNoToCompletedDispense`）

- **业务意义**：**AS-IP-NUR-015 · IF-IP-TRC-04** 在药房汇总核发完成后，由护士将追溯码归因到患者发放行，仅补齐 **`trace_no`** 事实。
- **边界说明**：**不**迁移 `status_enum`；**不**发布 `MedicationDispensedEvent`；**不**写 `cus_trace_number` + `cus_trace_number_flow` OUT；**不**写 SupplyDelivery。
- **入参**：`AssignTraceNoToCompletedDispenseCommand`——批量 `id` + `traceNo`；可选 `encounterId` 锚点；`operatorId`。
- **出参**：—。
- **前置条件**：目标行 **`COMPLETED`** 且 **`summary_no` 非空**（**BR-IP-TRC-002**）；`traceNo` 空白则跳过该行。
- **状态变化**：**无**（保持 `COMPLETED`）。
- **聚合内影响**：批量 UPDATE **`trace_no`**；组内多行可写入相同字符串（**BR-IP-TRC-010**）。
- **事件输出**：**无**。
- **失败语义**：非汇总路径、非已核发态、就诊锚点不一致时拒绝。

#### 6.1.17 发药前写入追溯码（`applyTraceNoBeforeDispense`）

- **业务意义**：发药核对前将追溯码落到发放行（门诊/住院发药路径）。
- **入参**：发放标识 + 追溯码等。
- **出参**：—。

#### 6.1.18 拒绝待退药发放（`declinePendingRefundMedicationDispense`）

- **业务意义**：药房拒绝待退药申请，保持可追溯状态迁移。
- **边界说明**：对称耗材 `declinePendingRefundDeviceDispense`。

#### 6.1.19 作废待退药发放（`voidPendingRefundMedicationDispense`）

- **业务意义**：作废尚未确认的待退药发放行（退费撤销等编排）。

#### 6.1.20 退费撤销后恢复已拒绝待退药（`restoreDeclinedMedicationDispenseAfterRefundRevoke`）

- **业务意义**：门诊退费申请撤销后，恢复先前拒绝的待退药发放事实。

> **变更（2026-09-23 · 模式 B）**：按代码回写退费/追溯相关写方法。

### 6.2 读侧查询服务（MedicationDispenseQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于列表/详情、写前强加载及与写操作分层的**前置校验**（与 BR-MDP-017 一致）。

#### 6.2.1 按主键查询发放单（`loadMedicationDispenseById`）

- **业务意义**：按主键加载单条聚合根，支撑写前存在性、状态门控与详情回显。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`med_medication_dispense` 主表单条。
- **过滤口径**：主键精确匹配；默认排除逻辑删除（与基类约定一致，以落库与交互契约为准）。
- **组装规则**：映射为领域实体或查询 DTO；不在读侧改写业务状态。
- **返回约束**：不存在或无权限时抛明确异常；存在则返回单条。

#### 6.2.2 按就诊查询发放单列表（`listMedicationDispensesByEncounter`）

- **业务意义**：返回某次就诊下的药品发放集合，支撑发药工作台与执行闭环展示。
- **边界说明**：只读查询，不写库，不做跨域编排；列表优先投影/QueryService（与 BR-MDP-017 一致）。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按 `encounter_id` 过滤。
- **过滤口径**：租户与就诊标识必选；默认排除逻辑删除；可按状态子集过滤（以落库与交互契约为准）。
- **组装规则**：列表 DTO；字段口径与 §3.1 一致；可合并展示字段（以落库与交互契约为准）。
- **返回约束**：允许空列表；分页策略以落库与交互契约为准。

#### 6.2.3 按汇总单号查询发放明细（`listMedicationDispensesBySummaryNo`）

- **业务意义**：按汇总单号拉取绑定明细，支撑住院汇总发药核对、核发扣库与批量发药。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按 `summary_no` 过滤。
- **过滤口径**：汇总单号精确匹配；默认排除逻辑删除。
- **组装规则**：列表 DTO（`MedicationDispenseSummaryItemQueryModel`）；字段含 `summaryNo`、`id`、`statusEnum`、`quantity`、`medicationId`、`locationId`、`lotNumber`、`unitCode`、`encounterId`、`patientId`、`traceNo`；与汇总发药写路径同一标识与数量口径。
- **返回约束**：允许空列表；单号不存在时返回空或抛异常以落库与交互契约为准。

#### 6.2.3a 按就诊集合反查已汇总单号（`listDistinctSummaryNosByEncounterIds`）

- **业务意义**：护士站汇总单列表/详情门控——给定管辖病区下的就诊集合，反查已绑定 `summary_no` 的去重汇总单号，供应用层写入 Supply `busNoIn`（**AS-IP-NUR-010** IF-IP-SUM-02）。
- **边界说明**：只读；不写库；不编排就诊/供应写。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按 `encounter_id IN`（分批）过滤。
- **过滤口径**：未软删；`summary_no` 非空；同号下任一发放行 encounter 命中即纳入。
- **返回约束**：允许空列表；空入参返回空（禁止回落全院单号）。

#### 6.2.4 按就诊查询待退药列表（`listPendingRefundDispensesByEncounter`）

- **业务意义**：返回就诊维度待退药记录，支撑退药工作台与药房核对。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按 `encounter_id` 与待退药状态过滤。
- **过滤口径**：状态须为待退药语义；租户与就诊标识必选；默认排除逻辑删除。
- **组装规则**：列表 DTO；可附带原单关键展示字段（以落库与交互契约为准）。
- **返回约束**：允许空列表。

#### 6.2.4a 按就诊集合查询取消执行退药候选（`listCancelExecuteRefundDispensesByEncounterIds`）

- **业务意义**：护士站汇总领药「退药」Tab（**AS-IP-NUR-010 IF-IP-SUM-10**）按患者装载取消执行产生的退药发放行候选。
- **边界说明**：只读；不写库；**不**校验 Procedure CANCEL 终态（由应用层挂接 `ProcedureQueryService` 完成，对齐 **BR-IP-SUM-019** / **BR-IP-CXL-010**）。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按 `encounter_id IN` 批量拉取。
- **过滤口径**：未软删；`dispense_enum=REFUND`；`status_enum ∈ {PENDING_REFUND, REFUNDED}`；`procedure_id` 非空。
- **组装规则**：返回领域实体列表（按 id 升序）；应用层再做 CANCEL 校验、中西药收窄与展示装配。
- **返回约束**：允许空列表；空入参返回空。

#### 6.2.5 按主键加载发放单（须存在）（`mustGetMedicationDispense`）

- **业务意义**：在配药、发药、作废、退药等写操作前，按发放单主键加载本条记录；约定**记录必须存在且当前上下文可读**，并返回完整数据供后续写逻辑使用。无记录、无权限或不符合可见性时抛业务异常，等价于「存在性 + 读取」一次完成，避免先判存在再查详情两次往返。与 **6.2.6～6.2.9** 分工：后者校验状态、库存、追溯等业务规则；**本条仅保证主键能落到一条未逻辑删除的发放单**。
- **边界说明**：只读，不改数据库，不编排他域写操作。**与 6.2.1 对比**：同一主表、同一主键定位；**6.2.1** 面向详情/列表等场景，「无记录」是否抛错由 本稿与实现约定；**本条**面向写前加载，**无记录必抛错**（早期版本 5.3.1.1 与 `load…` 并列口径，本稿用独立方法名写清合同）。
- **查询范围（读库，无写入）**：表 `med_medication_dispense`，按主键取一行。
- **过滤口径**：主键等于入参里的发放单 ID；默认不查已逻辑删除的行（与 6.2.1 一致）。
- **组装规则**：查出后交给上层的方式与 **6.2.1** 相同（领域实体或查询用 DTO，由实现约定）；不在读方法里改状态字段。
- **返回约束**：**有这条记录**：正常返回这一条。**没有这条记录**（删了、ID 错了、或无权看）：不要悄悄返回 `null` 让后面代码空指针，应抛出明确的业务/权限异常，由应用层转成用户能看懂的提示。**与 6.2.1 的区别**：6.2.1 可以约定「没有就抛」或「没有就空」，以本稿与实现为准；**本方法合同上就是「没有必抛」**，专门给写前「先拿到再改」用。

#### 6.2.6 配药前置校验（`validateDispensePreparable`）

- **业务意义**：在调用配药写操作前校验状态、追溯码可用性等门控条件，避免无效写事务。
- **边界说明**：只读校验，不写库；与 `prepareMedicationDispense` 分层（BR-MDP-017）。
- **查询范围（读库，无写入）**：本聚合主键定位 + 协作方只读查询（追溯码状态等，以落库与交互契约为准）。
- **过滤口径**：状态须为 `PREPARATION`（待配药）；追溯码、批号等规则与 BR-MDP-002、BR-MDP-008、BR-MDP-009、BR-MDP-018 对齐。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.7 发药前置校验（`validateDispensable`）

- **业务意义**：在核对发药写操作前校验状态、五元组完整性与库存足额等（库存结论以协作方返回为准）。
- **边界说明**：只读校验，不写库；与 `dispenseMedication` 分层（BR-MDP-017）。
- **查询范围（读库，无写入）**：本聚合主键定位 + 库存可用量等只读查询（以落库与交互契约为准）。
- **过滤口径**：状态须处于待发药窗口（`PREPARED` 与/或 `IN_PROGRESS`，与 **BR-MDP-003** 一致）；五元组须满足 BR-MDP-008；毒麻双人等见 BR-MDP-013。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.8 作废前置校验（`validateDispenseCancellable`）

- **业务意义**：在作废未发写操作前校验是否允许进入已拒绝路径。
- **边界说明**：只读校验，不写库；与 `cancelMedicationDispense` 分层（BR-MDP-017）。
- **查询范围（读库，无写入）**：本聚合主键定位。
- **过滤口径**：须为允许作废的未完成发药类状态（与 §5.2 一致）；原因枚举合法等以落库与交互契约为准。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.9 退药前置校验（`validateRefundable`）

- **业务意义**：在退药确认写操作前校验待退药态与幂等（防重复退药）。
- **边界说明**：只读校验，不写库；与 `refundDispensedMedication` 分层（BR-MDP-017）。
- **查询范围（读库，无写入）**：本聚合主键定位。
- **过滤口径**：状态须为 `PENDING_REFUND`（待退药）；须满足 BR-MDP-006。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.10 护士站执行前全局待发预占汇总（`listDispenseExeReserve`）

- **业务意义**：为住院护士站执行前库存 Gate 提供**发药聚合侧**全局待发占用量（**AS-IP-NUR-004 GAP-IP-EXE-005**）；与 `InventoryItemQueryService#sumAvailableInventory` **分聚合**，由应用层组装 Gate。
- **边界说明**：**不限就诊**——统计候选药房 + 药品下全部未完成发药闭环占用；状态口径为 `NurseStationExecutionConstants.DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE`（**宽于** IF-DOR-11 目录预减，含 `PREPARED`/`IN_PROGRESS`/`SUMMARIZED`）；`limitTime` 窗与目录同（草稿约 2h、待发约 24h）；**禁止**在本 QueryService 内调用库存聚合。
- **查询范围（读库，无写入）**：`med_medication_dispense`，按 `location_id IN` + `medication_id IN` + 占用状态 IN。
- **过滤口径**：`is_deleted=false`；状态 ∈ 执行 Gate 预占集；`now < limitTime`。
- **组装规则**：按 `locationId + medicationId` 对 `quantity` 求和，返回 `DispenseExeReserveRowModel` 列表。
- **返回约束**：入参任一集合为空时返回空列表；无匹配行时返回空列表。

#### 6.2.11 发药单分页与列表（`pageMedicationDispenseSlip` / `listMedicationDispenseSlip`）

- **业务意义**：药房发药单工作台分页/导出；直接查 `med_medication_dispense` 主表及关联字段同步组装。
- **边界说明**：只读；**不**经 `query_medication_dispense_slip` 等已废弃投影表。
- **查询范围（读库，无写入）**：`med_medication_dispense` + 关联患者/就诊/药品定义等。
- **返回约束**：`Page<MedicationDispenseSlipQueryModel>` 或同条件全量列表。

#### 6.2.12 药房待发就诊分页

| 方法 | 场景 |
| --- | --- |
| `pageEncounterIdsForOutpatientPharmacyPending` | 门诊药房待发药就诊队列 |
| `pageEncounterIdsForInpatientPharmacyPending` | 住院药房待发药就诊队列 |

- **边界说明**：只读；返回待处理 `encounter_id` 分页，明细由应用层再查发放单/医嘱。门诊队列跨聚合（就诊机构/关键字、退药收口医嘱）由 **`PharmacyEncounterQueueLoader`** 预写 **`OutpatientPharmacyPendingEncounterPageQuery#resolvedEncounterScope`**、**`#resolvedSupersededForwardMedReqIds`** 后委托本方法；**禁止**在 `MedicationDispenseQueryService` 内 EXISTS `Encounter`/`Patient`。
- **查询范围（读库，无写入）**：`med_medication_dispense` 单表状态聚合 + `location_id IN` + 可选 `encounter_id IN (resolvedEncounterScope)`。

#### 6.2.13 退药队列与明细

> **落点**：门诊 **`pageEncounterIdsForOutpatientPharmacyReturn`** / **`listOutpatientReturnMedicationDispensesForDetail`**（**AS-OP-PHA-002**）；住院 **`pageEncounterIdsForInpatientPharmacyReturn`** / **`listInpatientReturnMedicationDispensesForDetail`** / **`mapAggregatedInpatientRefundStatusCodeByEncounterIds`**（**AS-IP-PHA-003**）；在院/关键字收窄由 **`PharmacyEncounterQueueLoader`** 预写 **`resolvedEncounterScope`** 后委托本服务。

| 方法 | 场景 |
| --- | --- |
| `pageEncounterIdsForOutpatientPharmacyReturn` | 门诊退药患者队列（就诊维度） |
| `pageEncounterIdsForInpatientPharmacyReturn` | 住院退药患者队列（在院就诊维度；`dispense_enum ∈ {INPATIENT,TEMPORARY,REFUND}`） |
| `mapAggregatedRefundStatusCodeByEncounterIds` | 门诊退药队列列展示态批量聚合 |
| `mapAggregatedInpatientRefundStatusCodeByEncounterIds` | 住院退药队列列展示态批量聚合 |
| `listOutpatientReturnMedicationDispensesForDetail` | 门诊退药详情行 |
| `listInpatientReturnMedicationDispensesForDetail` | 住院退药详情行（不含拒退态默认集） |
| `listReturnMedicineDetails` | 门诊明细跨聚合装配（应用层 **`ReturnMedicineDetailLoader`**） |

- **边界说明**：只读；状态须满足待退/已退语义（与 **BR-MDP-006** 一致）；住院队列**必须**经应用层写入在院 `resolvedEncounterScope`。
- **查询范围（读库，无写入）**：`med_medication_dispense` 及关联。

#### 6.2.14 医嘱/发放关联反查

| 方法 | 业务意义 |
| --- | --- |
| `listMedicationDispenseIdsByMedicationRequest` | 按医嘱查发放单 ID |
| `mapMedicationRequestToPrimaryDispenseLocationId` | 医嘱→主发药库位 |
| `mapMedicationDispenseIdsToMedReqIds` | 发放单→医嘱反查 |
| `listMedicationDispensesByMedReqIds` | 批量按医嘱查发放实体 |
| `listMedicationDispensesByProcedureIds` | 按执行记录主键集合查发放实体（**AS-IP-NUR-012** 取消执行 Dispense 分轨） |
| `listMedicationDispensesByIds` | 批量按 ID 查发放实体 |

#### 6.2.15 退药展示态聚合（`mapAggregatedRefundStatusCodeByEncounterIds`）

- **业务意义**：批量聚合就诊维度退药展示状态码（门诊退药工作台）。
- **边界说明**：只读；一次 IN 查询；返回 `Map<encounterId, statusCode>`。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按就诊聚合。

#### 6.2.16 医嘱目录占用（`listMedicationDispensesForOrderCatalogOccupancy`）

- **业务意义**：医生站开立页（IF-DOR-11）候选库位×目录药品的草稿/待发占用批量查询（**不限就诊**；`now < limitTime`）。
- **边界说明**：只读；状态口径同 `DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG`（仅 `DRAFT`/`PREPARATION`）。**不**含住院 `SUMMARIZED`——目录「还可开」与签发 FIFO 选批分离。
- **查询范围（读库，无写入）**：`med_medication_dispense` 交集过滤。
- **返回约束**：`List<MedicationDispenseOrderCatalogOccupancyModel>`。

#### 6.2.16a 签发 FIFO 住院汇总占用（`listMedicationDispensesForInpatientSummarizedOccupancy`）

- **业务意义**：**IF-DOR-15** 签发 FIFO 选批时，额外读取已进汇总单、尚未核发扣库的住院发放占用（`SUMMARIZED`），与发药门禁对汇总态预占对齐，避免门诊/其它签发抢已汇总批号。
- **边界说明**：只读；**不**进入 IF-DOR-11 目录预减。过滤：`dispense_enum=INPATIENT`、状态 ∈ `DoctorStationOrderCatalogConstants.DISPENSE_OCCUPANCY_STATUSES_FOR_FIFO_INPATIENT_SUMMARY_EXTRA`（仅 `SUMMARIZED`）、`now < limitTime`；候选库房全局，不限就诊。应用层在 `DoctorStationAdviceFifoAllocationAssembler#buildMedicationOccupancy` 与目录占用**叠加**后做 FEFO。
- **查询范围（读库，无写入）**：`med_medication_dispense`。
- **返回约束**：`List<MedicationDispenseOrderCatalogOccupancyModel>`（与目录占用同模型，便于合并）；入参空集合返回空列表。

#### 6.2.17 住院护士站待汇总领药明细分页（`pageMedicationDispensePickupPending`）

- **业务意义**：**AS-IP-NUR-010 · IF-IP-SUM-01/05** 明细 Tab 分页查询待汇总发放行，含可勾选时间点与只读账单单价。
- **边界说明**：只读；**不写** ChargeItem；中西药 `tcmFlag`、长临嘱 `therapyEnum` 由应用层预解析为 `resolvedMedReqIds` 后传入；本方法不做 MedicationRequest 跨聚合过滤。
- **查询范围（读库，无写入）**：`med_medication_dispense` 主表；只读协作 **ChargeItem**（与发放行 1:1 锚点取 `unit_price`）。
- **过滤口径（BR-IP-SUM-002）**：`status_enum = PREPARATION`；`procedure_id` 非空；`summary_no` 为空（未汇总）；`encounter_id IN`（必填）；`dispense_enum ∈ {INPATIENT, TEMPORARY}`；可选 `med_req_id IN`（应用预解析）；可选 `planned_dispense_time <= queryDeadline`；排除逻辑删除。
- **组装规则**：映射为 `MedicationDispensePickupPendingQueryModel`（含 `dispenseId`、`procedureId`、`plannedDispenseTime`、`chargeUnitPrice` 等）；按 `planned_dispense_time` 升序、`created_at` 降序分页。
- **返回约束**：`encounterIds` 为空时返回空页；允许空列表。

#### 6.2.18 发退药记录分页与列表（`pageMedicationDispenseRecords` / `listMedicationDispenseRecords`）

- **业务意义**：**AS-OP-PHA-004** / **AS-RPT-004** 发退药/发药报表查询与导出；平铺已发/已退事实，**不做**就诊/处方小计。
- **边界说明**：只读；**不**套用发药单 slip 的 supersede 正向行排除；时间轴为 **`dispense_time`**（非 `status_changed_time`）；有时间窗时 **`dispense_time` 非空** 才纳入。
- **查询范围（读库，无写入）**：`med_medication_dispense` 主表；关键字/项目类型/就诊场景由应用层预写 **`MedicationDispenseRecordPageQuery`** resolved 字段后 IN 收窄。
- **过滤口径**：
  - **记录类型**：`ALL` = 正向 `COMPLETED`（场景 `dispense_enum`）∪ 退药 `REFUND+REFUNDED`；`DISPENSE` / `REFUND` 单选。
  - **场景正向**：门诊 `dispense_enum ∈ {OUTPATIENT,TEMPORARY}`；住院 `∈ {INPATIENT,TEMPORARY}`（`outpatientMedicationOnly` / `inpatientMedicationOnly` 互斥，由应用层设置）。
  - **退药行**：`dispense_enum=REFUND` 且 `status_enum=REFUNDED`；须落在同窗就诊，**禁止**门诊/住院列表互串：
    - 应用层写入 **`resolvedEncounterIdsForScene`** 时（通常有患者关键字）：`encounter_id IN (...)`；
    - 未写入且已设 **`inpatientMedicationOnly` / `outpatientMedicationOnly`** 时：QueryService 以 **EXISTS** 关联 **`adm_encounter.class_enum`**（住院 **IMP** / 门诊 **AMB**）收窄；与预解析 id 列表**互斥**。
  - 可选：`location_id`、`practitioner_id`、药品/患者独立模糊（resolved 列表）、项目类型（resolved 药品 id）。
- **组装规则**：映射为 `MedicationDispenseSlipQueryModel`（与 slip 字段直映一致）；`prescription_no` 等由应用层批量补全。
- **排序**：`dispense_time DESC`, `id DESC`。
- **返回约束**：`Page<MedicationDispenseSlipQueryModel>` 或同条件全量列表。

#### 6.2.19 住院护士站追溯码分配 · 待分配患者队列（`pageEncountersPendingTraceAssignment`）

- **业务意义**：**AS-IP-NUR-015 · IF-IP-TRC-01** 分页返回存在待分配追溯码的住院就诊及待办汇总组计数。
- **边界说明**：只读；关键字收窄由应用层预解析为 `encounterIds`；本方法不做 Encounter 跨聚合 join。
- **查询范围（读库，无写入）**：`med_medication_dispense` 主表；Criteria 按就诊 DISTINCT 分页。
- **过滤口径（BR-IP-TRC-002）**：`status_enum = COMPLETED`；`summary_no` 非空；`trace_no` 为空；`encounter_id IN`（必填）；`dispense_enum ∈ {INPATIENT, TEMPORARY}`；排除逻辑删除。
- **组装规则**：映射为 `EncounterPendingTraceAssignmentQueryModel`（`encounterId`、`patientId`、`pendingGroupCount`）；`pendingGroupCount` 为药+批汇总组中仍有底层行 `trace_no` 空的组数。
- **返回约束**：`encounterIds` 为空时返回空页。

#### 6.2.20 住院护士站追溯码分配 · 患者药批汇总行（`listPatientTraceAssignmentAggregateLines`）

- **业务意义**：**AS-IP-NUR-015 · IF-IP-TRC-02** 按就诊返回汇总路径已核发发放行的药+批聚合列表。
- **边界说明**：只读；拆零数量累加由应用层按药品目录 unitBands 完成；本方法返回底层行 `quantity`/`unit_code` 明细。
- **查询范围（读库，无写入）**：`med_medication_dispense` 按 `encounter_id` 加载后在 QueryService 内聚合。
- **过滤口径**：同 **6.2.19** 汇总路径 **`COMPLETED`** 基线（**含**已分配与待分配组，供回显与覆盖保存）。
- **组装规则**：映射为 `PatientTraceAssignmentAggregateLineQueryModel`（`groupKey`、`summaryNo`、`medicationId`、`lotNumber`、`dispenseIds`、`dispenseLines`、`assignedTraceNo`、`assignmentPending`）；合并键 **`summary_no + medication_id + lot_number`**（**BR-IP-TRC-004**）。
- **返回约束**：`encounterId` 为空时返回空表。

#### 6.2.21 住院患者药品使用行列表（`listInpatientPatientMedicationUsageDispenseRows`）

- **业务意义**：按单就诊加载「药品使用」发放事实行（时间轴为**计划发药时间**，含待发/已发/退药相关态）。原应用页 **AS-IP-PHA-005** 已废止；本方法仍可作为领域读能力保留，供其它编排复用（**非**报表中心发药报表口径）。
- **边界说明**：只读；**仅本聚合** `MedicationDispenseRepository`；**禁止**跨表 EXISTS/join Encounter；**不**按 `location_id` 收窄；账单价与三态展示由应用层装载。
- **查询范围（读库，无写入）**：`med_medication_dispense` 主表；入参 **`InpatientPatientMedicationUsageDispenseQuery`**。
- **过滤口径（BR-IP-PMU-003～005 / AS §1.3）**：
  - `encounter_id` 必填（空则空列表）；可选 `patient_id` 等值；
  - `planned_dispense_time` 本地日闭区间（起止日转日界）；**空计划发药时间不入窗**；起止倒置返回空列表；
  - `status_enum IN`：入参 `statusEnumIn`；空则默认 `PREPARATION`/`PREPARED`/`IN_PROGRESS`/`SUMMARIZED`/`PART_COMPLETED`/`COMPLETED`/`PENDING_REFUND`/`PART_REFUND`/`REFUNDED`（不含草稿/作废/停止/暂停）；
  - `dispense_enum IN`：入参 `dispenseEnumIn`；空则默认 `INPATIENT`/`TEMPORARY`/`REFUND`；
  - `is_deleted = false`。
- **组装规则**：映射为 **`InpatientPatientMedicationUsageDispenseRow`**（`dispenseId`、`medicationId`、`encounterId`、`patientId`、`medReqId`、`statusEnum`、`dispenseEnum`、`quantityValue`、`quantityUnitCode`、`plannedDispenseTime`、`summaryNo`、`lotNumber`、`locationId`）；`quantityValue` 优先 `dispense_quantity`，为空回落 `quantity`。
- **排序**：`planned_dispense_time DESC`, `id DESC`。
- **返回约束**：`List<InpatientPatientMedicationUsageDispenseRow>`；无匹配为空列表。

#### 6.2.22 发药报表患者侧净汇总（`summarizeMedicationDispenseReport`）

- **业务意义**：**AS-RPT-003 / AS-RPT-004** 发药报表汇总 Tab：按药房 / 品规 / 药品类型对患者侧发退药数量做**净**汇总（发为正、退为负）。
- **边界说明**：只读；**仅本聚合**；金额与科室领用净汇总不在本方法（ChargeItem / SupplyRequest 由应用层并集）。
- **入参**：`MedicationDispenseReportSummaryQuery`（筛选同 **§6.2.18**；`summaryDimension`；类型维须预写 `categoryCodeByMedicationId`）。
- **组装规则**：`MedicationDispenseReportSummaryRow`（`dimensionKey`、`netQuantity`、`lineCount` 及维主键）。
- **返回约束**：无命中为空列表。
- **实现锚点**：`MedicationDispenseQueryService#summarizeMedicationDispenseReport`。

#### 6.2.23 按申请主键 + 状态分页患者发退药（`pagePatientDispensesByMedReqIdsAndStatusIn`）

- **业务意义**：**AS-RPT-015** 结余留用报表：在已解析申请主键范围内分页患者侧发退药事实行（`encounter_id` 非空，排除科室领用）。
- **边界说明**：只读；**仅本聚合**；未软删；`med_req_id` 分批 IN；`status_enum IN`；排序默认由调用方 `Pageable`（推荐 `id ASC`）。
- **入参**：`medReqIds`、`statusEnums`（如 `COMPLETED`/`REFUNDED`）、`pageable`；任一侧空则空页。
- **出参**：`Page<MedicationDispense>`。
- **实现锚点**：`MedicationDispenseQueryService#pagePatientDispensesByMedReqIdsAndStatusIn`；售价补价读模型见公开 `toSlipQueryModel`。

### 6.3 领域边界说明

- `MedicationDispense` 只描述药房配药、发药、退药、汇总发药等与发放单直接相关的业务事实；医嘱开立与停嘱、收费明细金额计算、库存台账本体不在本聚合内维护。
- 库存扣减/回补、追溯码出入库、计费联动由库存域、追溯域与收费域通过事件或应用编排协作；本聚合提供五元组口径与业务事件，不替代他域不变式。
- **纯 UI 装配的初始化接口**、报表型统计等应用层能力不得冒充本节领域方法；预减可用量展示口径见 **§9.7**，读侧统计不作为库存域唯一事实来源（BR-MDP-010、BR-MDP-017）。
- 写编排由 `MedicationDispenseDomainService`（或应用层 Manager）承担；读编排由 `MedicationDispenseQueryService` 承担（与 `DM-ADM-001` §6 分工一致）。

---

## 7. 不变式


| 编号         | 不变式                                                                                                  | 说明                                                            |
| ---------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| BR-MDP-001 | 同一药品申请标识下，临时医嘱不得重复创建发放单                                                                              | 防止预减重复占用与工作台重复待配药；长期医嘱在执行阶段按查重逻辑保证唯一                          |
| BR-MDP-002 | 配药只能对 `PREPARATION`（待配药）状态的发放单执行                                                                       | 非 `PREPARATION` 拒绝进入 `PREPARED` 等配药收口态                                  |
| BR-MDP-003 | 核对发药只能在待发药窗口内执行（`PREPARED` 与/或 `IN_PROGRESS`，与 §2.2 一致）                                                         | 非待发药窗口拒绝进入 `COMPLETED`                                             |
| BR-MDP-004 | 发药成功必须同事务写入已发数量、发药人、发药时间并推进为"已发放"                                                                    | 保证发放事实与状态的原子一致性                                               |
| BR-MDP-005 | 发药成功后必须扣减对应批号库存；库存不足必须拒绝发药                                                                           | 库存扣减与发放状态同事务提交                                                |
| BR-MDP-006 | 退药只能对"待退药"状态的发放单执行；已退药拒绝重复退药                                                                         | 幂等保护，防止重复回补库存                                                 |
| BR-MDP-007 | 退药成功后必须回补库存，并将追溯码记录为 IN                                                                              | 退药闭环：库存回补 + 追溯码入库同口径                                          |
| BR-MDP-008 | 参与库存协作时，库存扣减口径五元组（药品+批号+数量+单位+药房）必须齐全且数量 > 0；待发/待配药阶段允许 `lot_number` 为空（尚未批次分配），进入配药收口与核对发药前须补齐并完成校验 | 与追溯/库存协作前强制一致；门诊批号自动匹配可读侧推荐与住院执行后写侧落库为不同机制，配药动作允许人工确认/调整批号后写入 |
| BR-MDP-009 | 配药时追溯码必须做"已出库不可重复使用"校验，命中则拒绝                                                                         | 防止追溯码重复出库                                                     |
| BR-MDP-010 | 预减统计仅计入 `DRAFT`/`PREPARATION`（草稿/待配药）且 `now < limitTime` 的记录；**候选库房全局汇总，不限就诊**                                              | 过期占用不计入可用量展示；写侧 `initDraft` +2h、`initPreparation` +24h                  |
| BR-MDP-011 | 作废未发必须记录未发药原因（`NotPerformedReason`）                                                                  | 审计追溯与统计分析需要                                                   |
| BR-MDP-012 | 汇总时必须写入汇总单号；取消汇总必须清空汇总单号并回退为"待配药"                                                                    | 汇总锚点一致性，同一明细不得重复汇总                                            |
| BR-MDP-013 | 毒麻药品发放必须双人复核（配药人 ≠ 发药人）                                                                              | 特殊药品管理法规要求（《麻醉药品和精神药品管理条例》）                                   |
| BR-MDP-014 | 发药时产品批号对应的有效期不得早于当前日期                                                                                | 禁止发放过期药品                                                      |
| BR-MDP-015 | **医生站**：临时医嘱**签发**经 FIFO 生成草稿发放（`replaceFifoDraftDispensesAfterDoctorStationSign`），**暂存不建发放**；**非医生站**临时路径可调用 `generateDispenseFromTemporaryOrder`；长期医嘱执行生成「待配药」发放单——三种口径不可混用 | 与 BR-MRQ-028、AS-IP-DOR-003 / AS-OP-DOR-003 一致 |
| BR-MDP-016 | 用药说明快照随发放单生成时整体拷贝，后续变更须有明确业务动作                                                                       | 保证核对发药与审计视图一致                                                 |
| BR-MDP-017 | 查询、校验、写入分层明确                                                                                         | 读侧 `QueryService` 不写库；写前门控与QueryService 列表查询不得替代领域写模型为唯一事实来源               |
| BR-MDP-018 | 临时医嘱路径下，同一药品申请关联的多条待发/待配药发放明细，在配药阶段其待发数量合计须与请求数量一致（单位口径一致）                                           | 由应用用例聚合查询后统一校验（早期版本 `BR-MD-009`）；仅对临时医嘱集合生效，长期路径不适用            |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文      | 聚合                   | 关联方式                             | 说明                        |
| ---------- | -------------------- | -------------------------------- | ------------------------- |
| 用药域·药品申请管理 | MedicationRequest    | `药品申请标识(MedicationRequestId)`    | 发放单由医嘱开立/执行生成，一条请求可对应多条发放 |
| 患者管理       | Patient              | `患者标识(PatientId)`                | 发放归属患者                    |
| 就诊管理       | Encounter            | `就诊标识(EncounterId)`              | 发放关联就诊，工作台以就诊为锚点          |
| 基础数据·药品目录  | MedicationDefinition | `药品定义标识(MedicationDefinitionId)` | 药品主数据，提供药品名称、规格、效期等信息     |
| 基础数据·位置    | Location             | `药房标识(LocationId)`               | 发放药房与送药目的地                |
| 人员管理       | Practitioner         | `配药人标识 / 发药人标识(PractitionerId)`  | 配药人、发药人、接收人               |


### 8.2 下游影响


| 下游上下文 | 聚合                             | 触发方式    | 说明                                                                         |
| ----- | ------------------------------ | ------- | -------------------------------------------------------------------------- |
| 库存管理  | InventoryItem                  | 事件 / 编排 | `MedicationDispensedEvent` → 库存扣减；`MedicationDispenseRefundedEvent` → 库存回补 |
| 追溯码管理 | TraceNumberRecord              | 事件 / 编排 | 发药 → 追溯码 OUT；退药 → 追溯码 IN                                                   |
| 收费管理  | ChargeItem                     | 事件 / 编排 | 发放完成后触发药品计费联动；退药完成后触发退费                                                    |
| 物资流转  | SupplyRequest / SupplyDelivery | 汇总单号协作  | 住院汇总发放场景的物资申请与发放                                                           |


### 8.3 引用规则

> **统一规则**：
>
> - **门诊场景**：引用 `就诊标识(EncounterId)`，`住院就诊标识(EpisodeOfCareId)` 为空
> - **住院场景**：同时引用 `就诊标识(EncounterId)` 和 `住院就诊标识(EpisodeOfCareId)`
> - 患者引用统一使用 `患者标识(PatientId)`（对应 FHIR `subject` 字段）
> - 药品申请引用使用 `药品申请标识(MedicationRequestId)`（对应 FHIR `authorizingPrescription` 字段）

### 8.4 事件流示意图

```
MedicationRequest        MedicationDispense        InventoryItem    TraceNumberRecord   ChargeItem
      │                        │                        │                 │                │
      │── 医嘱开立/执行 ──────>│                        │                 │                │
      │                        │── PreparedEvent ──────>│                 │                │
      │                        │     (配药校验)           │                 │── 追溯码校验    │
      │                        │── DispensedEvent ─────>│── 库存扣减       │── 追溯码OUT     │
      │                        │                        │                 │                │── 药品计费
      │                        │── RefundedEvent ──────>│── 库存回补       │── 追溯码IN      │
      │                        │                        │                 │                │── 退费冲销
      │                        │                        │                 │                │
```

---

## 9. 补充

### 9.1 门诊发药流程

1. **医生开立**：临时医嘱保存 → 生成草稿发放单（`DRAFT`）
2. **收费确认**：门诊收费完成 → 草稿推进为待配药（`PREPARATION`）
3. **药房配药**：药房人员确认批号、追溯码、数量 → 已配药（`PREPARED`；若编排先落待发药态则为 `IN_PROGRESS`，以落库与交互契约为准）
4. **核对发药**：核对药品与处方一致 → 库存扣减 → 已发放（`COMPLETED`）

### 9.2 住院发药流程

1. **长期医嘱执行**：护士执行长期医嘱 → 直接生成待配药发放单（`PREPARATION`）
2. **汇总发药**：按药房维度汇总多条明细 → 已汇总（`SUMMARIZED`）→ 批量发药 → 已发放（`COMPLETED`）
3. **临时医嘱**：走类似门诊流程，但计划发药时间由执行记录决定

### 9.3 退药流程

1. **退药发起**：退费或取消执行 → 生成待退药发放单（`PENDING_REFUND`）
2. **药房退药**：药房确认退回药品 → 库存回补 → 追溯码 IN → 已退药（`REFUNDED`）
3. **约束**：已退药的发放单不可重复退药（幂等保护 BR-MDP-006）

### 9.4 毒麻药品特殊管理

- 毒麻药品（麻醉药品、第一类精神药品）发放必须实行**双人复核制**：配药人与发药人不得为同一人（BR-MDP-013）
- 追溯码为必填项，且必须逐码校验出库状态
- 退药时同样需要双人签名确认
- 需保留完整的领用/退回审计链

### 9.5 效期管理

- 发药时必须校验产品批号对应的有效期，过期药品禁止发放（BR-MDP-014）
- 近效期药品优先发放（先进先出原则 FEFO），由库存域配合实现批号匹配
- 效期预警由库存域负责，本聚合不承担效期预警职责

### 9.6 预减统计说明

预减统计用于"库存可用量展示/校验"场景，统计范围严格限定：


| 条件    | 说明                                     |
| ----- | -------------------------------------- |
| 状态范围  | 仅 `DRAFT`（草稿）与 `PREPARATION`（待配药）         |
| 有效期范围 | `now < limitTime`（过期占用不计入；无定时释放任务，读侧过滤）             |
| 统计范围  | **候选库房全局**，不限就诊/患者                         |
| 写侧窗口  | 草稿 `initDraft`：`limitTime = now + 2h`；待发 `initPreparation`/`draftToPreparation`：`now + 24h` |
| 聚合维度  | 统一零售价：按药品+药房聚合，忽略批号；非统一零售价：按药品+批号+药房聚合 |
| 单位折算  | 数量统一折算到最小单位后再与库存可用量相减                  |


> 预减为读侧统计，不作为库存域真实可用量；实际发药以库存域扣减校验为准。

### 9.7 与 ChargeItem 的职责边界


| 职责   | MedicationDispense | ChargeItem           |
| ---- | ------------------ | -------------------- |
| 发放事实 | 记录药品调配发药的全过程       | —                    |
| 计费触发 | 发放完成后发布事件          | 接收事件进行药品计费           |
| 退费协同 | 退药完成后发布事件          | 接收事件进行费用冲销           |
| 库存扣减 | 提供物资口径五元组          | —                    |
| 金额计算 | —                  | 按收费标准计算药品费用          |
| 医保拆分 | —                  | 配合 Account 进行医保/自费拆分 |


> **门诊医生站扩展（与 `DeviceDispense` 对称）**：一条 **`MedicationRequest`** 经签发可对应多条 **`MedicationDispense`**（同 `med_req_id`、多库房/多批号拆行）。**`ChargeItem` 与每条发放物理行 1:1**；`service_table` + `service_id` **锚定发放表主键**（与 **`DM-ADM-011`** 中 `dispense_table` + `dispense_id` 可冗余互证）。单价/总价以 **`ChargeItemDefinition`**、**`LOT_NUMBER_PRICE`** 等定价策略由**后端按批号与数量重算**；前端传入金额**仅校验、不作为权威落库**。**住院 IF-IPP-03 列表展示**直接按 **`dispense_id` 1:1** 读取已落库 **`ChargeItem.unit_price`/`total_price`**，**不做**门诊 IF-OPP-03 式同 dispense 多行加权聚合。

---

### 9.8 版本变更（摘要）

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.3.18 | 2026-10-15 | §6.2.4a **`listCancelExecuteRefundDispensesByEncounterIds`**：护士站 IF-IP-SUM-10 取消执行退药只读候选（代码先行回写） |
| v1.3.17 | 2026-10-14 | §6.2.16a **`listMedicationDispensesForInpatientSummarizedOccupancy`**：IF-DOR-15 FIFO 额外并入住院 `SUMMARIZED`（目录预减仍仅 DRAFT/PREPARATION）；§6.2.10 状态口径更正为 `NurseStationExecutionConstants`（宽于目录；代码先行回写） |
| v1.3.16 | 2026-10-13 | §6.2.3a **`listDistinctSummaryNosByEncounterIds`**：护士站汇总单管辖病区门控反查（**AS-IP-NUR-010** IF-02；代码先行回写） |
| v1.3.15 | 2026-09-10 | §6.2.23：`pagePatientDispensesByMedReqIdsAndStatusIn` + 公开 `toSlipQueryModel`（**AS-RPT-015**） |
| v1.3.14 | 2026-08-24 | **BR-MDP-010 / §9.6**：待发 `limitTime` 写侧窗 **+12h→+24h**（草稿仍 +2h）；对齐 **AS-OP-DOR-003 v1.67** / `DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS` |
| v1.3.13 | 2026-08-13 | §6.1.4 边界：应用层签退/校对退回须按正向 `COMPLETED` 与 `REFUND+REFUNDED` **数量净额**判断净已发（`InpatientAdviceRevokeGate`） |
| v1.3.12 | 2026-07-27 | §3.1 增补 **`cashier_settlement_id`**（药房日结挂载）；交账口径对齐 **DM-FIN-003 v3.0** / DD 交账日结改造（`dispense_time` 空报错；交账态≠退药门禁） |
| v1.3.11 | 2026-07-15 | §6.2.21 **`listInpatientPatientMedicationUsageDispenseRows`**（原 **AS-IP-PHA-005** 已废止；领域读能力保留）：单就诊 + `planned_dispense_time` 闭区间 + 使用态白名单 |
| v1.3.10 | 2026-07-11 | §6.2.18 退药行场景收窄：`resolvedEncounterIdsForScene` 未写入时，按 `inpatientMedicationOnly` / `outpatientMedicationOnly` **EXISTS** 关联 `adm_encounter.class_enum`（IMP/AMB）；修复住院/门诊发退药记录互串 |
| v1.3.9 | 2026-07-04 | **AS-IP-NUR-015**：§6.1.16 **`assignTraceNoToCompletedDispense`**；§6.2.19 **`pageEncountersPendingTraceAssignment`**；§6.2.20 **`listPatientTraceAssignmentAggregateLines`**（关闭 **GAP-IP-TRC-001～003**） |
| v1.3.8 | 2026-06-26 | §6.1.8 **`createPendingRefundDispense`**：自原正向发放行拷贝 **`summary_no`、用法用量摘要、`planned_dispense_time`、`des_location_id`** 等展示字段（批号/追溯码仍单独拷贝） |
| v1.3.7 | 2026-06-21 | §6.2.18 **`pageMedicationDispenseRecords`** / **`listMedicationDispenseRecords`**（**AS-OP-PHA-004** / 住院侧现 **AS-RPT-004**；关闭 **GAP-MED-002-REC-001**） |
| v1.3.6 | 2026-06-10 | §6.2.13 增补 **`pageEncounterIdsForInpatientPharmacyReturn`**、**`listInpatientReturnMedicationDispensesForDetail`**、**`mapAggregatedInpatientRefundStatusCodeByEncounterIds`**（**AS-IP-PHA-003** Gap 001～002 关闭） |
| v1.3.5 | 2026-06-10 | §6.1.8 **`createPendingRefundDispense`** 增补可选 **`procedureId`**（**AS-IP-NUR-012 BR-IP-CXL-010** 挂接新 CANCEL 行）；自原单拷贝 **`lot_number`、`trace_no`**；事件 **`MedicationDispensePendingRefundCreatedEvent`** 载荷补 **`procedureId`** |
| v1.3.4 | 2026-06-10 | §6.2.14 **`listMedicationDispensesByProcedureIds`**（**AS-IP-NUR-012** Gap 004） |
| v1.3.3 | 2026-06-06 | **§6.1.11** 增补 **AS-IP-NUR-010** IF-IP-SUM-08 / **AS-IP-PHA-002** IF-IPS-04 应用编排表；事件消费方对齐护士撤销与药房驳回 |
| v1.3.2 | 2026-05-24 | **`MedicationDispenseCreatedEvent`** 最小载荷补 **`lotNumber`**（`MedicationDispenseDomainService` 发布时取自聚合 `lot_number`）；QueryService **v2.5** §4.2 INSERT 投影；对齐 **AS-IP-PHA-001 v1.7** |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`MedicationDispenseDomainService`

| 方法 |
|------|
| `prepareMedicationDispense` |
| `applyTraceNoBeforeDispense` |
| `dispenseMedication` |
| `cancelMedicationDispense` |
| `refundDispensedMedication` |
| `declinePendingRefundMedicationDispense` |
| `voidPendingRefundMedicationDispense` |
| `restoreDeclinedMedicationDispenseAfterRefundRevoke` |
| `generateDispenseFromTemporaryOrder` |
| `replaceFifoDraftDispensesAfterDoctorStationSign` |
| `promoteToPreparation` |
| `generateDispenseFromExecution` |
| `linkDispenseProcedure` |
| `createPendingRefundDispense` |
| `releaseDispenseReserve` |
| `summarizeMedicationDispenses` |
| `cancelMedicationDispenseSummary` |
| `assignTraceNoToCompletedDispense` |
| `completeSummaryDispense` |
| `rollbackUndispensedDispense` |
| `removeUndispensedDispense` |

### QueryService：`MedicationDispenseQueryService`

| 方法 |
|------|
| `loadMedicationDispenseById` |
| `listMedicationDispenseIdsByMedicationRequest` |
| `mapMedicationRequestToPrimaryDispenseLocationId` |
| `listMedicationDispensesByEncounter` |
| `listUndeletedMedicationDispensesByEncounterId` |
| `listCompletedForwardDispensesMissingTraceNo` |
| `existsCompletedForwardDispenseMissingTraceNo` |
| `listMedicationDispensesBySummaryNo` |
| `listSummaryNosWhereAllDispenseRowsHaveStatus` |
| `listDistinctSummaryNosByEncounterIds` |
| `indexSummaryDispenseGateBySummaryNos` |
| `listPendingRefundDispensesByEncounter` |
| `listCancelExecuteRefundDispensesByEncounterIds` |
| `listOutpatientReturnMedicationDispensesForDetail` |
| `listInpatientReturnMedicationDispensesForDetail` |
| `mapAggregatedRefundStatusCodeByEncounterIds` |
| `mapAggregatedInpatientRefundStatusCodeByEncounterIds` |
| `listMedicationDispensesForOrderCatalogOccupancy` |
| `listMedicationDispensesForInpatientSummarizedOccupancy` |
| `listDispenseExeReserve` |
| `sumDispenseExeReserveQuantityInSplitUnit` |
| `mustGetMedicationDispense` |
| `listMedicationDispensesByIds` |
| `validateDispensePreparable` |
| `validateDispensable` |
| `validateDispenseCancellable` |
| `validateRefundable` |
| `findById` |
| `listByMedReqIds` |
| `listByBusNos` |
| `existsByMedicationId` |
| `existsByMedicationIdAndLotNumber` |
| `existsInTransitDispenseByMedicationId` |
| `listRefundedRefundDispenseMedReqIds` |
| `mapMedicationDispenseIdsToMedReqIds` |
| `listMedicationDispensesByMedReqIds` |
| `listDistinctMedicationRequestIdsByStatusEnumAndMedReqIdIn` |
| `listDistinctMedicationRequestIdsByStatusEnum` |
| `pagePatientDispensesByMedReqIdsAndStatusIn` |
| `listMedicationDispensesByProcedureIds` |
| `pageSlip` |
| `pageMedicationDispenseSlip` |
| `listMedicationDispenseSlip` |
| `pageMedicationDispenseRecords` |
| `listMedicationDispenseRecords` |
| `listInpatientPatientMedicationUsageDispenseRows` |
| `toSlipQueryModel` |
| `pageEncounterIdsForOutpatientPharmacyPending` |
| `pageEncounterIdsForInpatientPharmacyPending` |
| `pageEncounterIdsForInpatientPharmacyReturn` |
| `pageEncounterIdsForOutpatientPharmacyReturn` |
| `pageMedicationDispensePickupPending` |
| `pageEncountersPendingTraceAssignment` |
| `listPatientTraceAssignmentAggregateLines` |
| `summarizeMedicationDispenseReport` |
| `summarizeMedicationDispenseReportWithSlips` |
| `existsDispenseBusinessForLocation` |
| `previewUnsettledDispensesForCashierSettlement` |
| `countUnsettledDispensesForCashierSettlement` |
| `findDispensesByCashierSettlementId` |
| `findUnsettledDispensesForCashierSettlement` |
| `findDispensesForPharmacyCashierSettlementSummary` |
| `listDistinctPractitionerIdsInPharmacyCashierSettlementWindow` |
| `extractDistinctPractitionerIds` |
| `filterUnsettledDispenses` |
| `findEarliestUnsettledDispenseTimeBeforeWindow` |
| `countUnsettledDispensesAfterWindow` |

