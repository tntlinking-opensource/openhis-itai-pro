# DM-WKF-005 SupplyDelivery 供应发放

> **限界上下文**：WKF域 · 供应发放管理
> **聚合类型**：实体聚合
> **版本**：v1.0.1 · **日期**：2026-05-28

---

## 1. 概述

### 1.1 业务定义

供应发放（SupplyDelivery）记录供应请求**执行单据**审批同意后的实际发放事实，用于追溯「发了什么、发给谁、何时发放」。

> **关键时机约束**：发放记录由**应用层编排**在 `**SupplyRequestAgreedEvent`（DM-WKF-006 §5 事件 #4）触发后**创建，且触发对象必须是**执行单据**（Record B）——即申请单审批通过后由编排生成的执行单，或免申请直接创建的执行单——审批**申请单**本身不直接触发发放。药品汇总单（type 27）为特例：护士站发送后 SupplyRequest 进入 **APPROVAL**，由**药房核发**（**AS-IP-PHA-002**）编排调用 `createSummarySupplyDelivery`，不经二次审批。

聚合边界锁定在发放事实与状态，库存增减由应用编排协调 **InventoryItem** 完成；本聚合不直接操作库存数量列。

### 1.2 核心职责


| 职责     | 说明                                                                            |
| ------ | ----------------------------------------------------------------------------- |
| 发放事实记录 | 记录每次物资发放的品种、数量、批号、发放时间与来源请求                                                   |
| 状态驱动   | 普通发放创建即 `COMPLETED`；汇总发放按流程推进 `DRAFT → PREPARATION → IN_PROGRESS → COMPLETED` |
| 调拨双侧记录 | 调拨发放创建两条记录（源减、目增），同一调拨单 `request_id`                                          |
| 盘点差异落账 | 盘点发放仅对有差异须落账的明细行创建，`quantity` 可正（盘盈）可负（盘亏）                                    |
| 退发放    | 以负向数量或单独退药记录关联原发放，支撑退库/退货追溯                                                   |
| 查询对账   | 按 `request_id`、`summary_id`、主键批量回查，支撑审批后核验与财务对账                               |


### 1.3 适用范围


| 场景      | 触发来源                                                  | 说明                              |
| ------- | ----------------------------------------------------- | ------------------------------- |
| 进货/入库   | 执行单 `SupplyRequestAgreedEvent`（type 2/11/13/21）       | 创建即 `COMPLETED`；含批号、发票号等        |
| 出库      | 执行单 `SupplyRequestAgreedEvent`（type 4）                | 创建即 `COMPLETED`；源库房扣减           |
| 退库/退货   | 执行单 `SupplyRequestAgreedEvent`（type 3/5/14/22）        | 创建即 `COMPLETED`；负向或原单关联         |
| 损益      | 执行单 `SupplyRequestAgreedEvent`（type 6/15/23）          | 创建即 `COMPLETED`；`quantity` 正增负损 |
| 调拨      | 执行单 `SupplyRequestAgreedEvent`（type 8～10/17～19/25/26） | 创建两条：源减 + 目增                    |
| 盘点/耗材盘点 | 执行单 `SupplyRequestAgreedEvent`（type 7/16/24/28）       | 仅差异行；`quantity` 正盈负亏            |
| 药品汇总发药  | **AS-IP-PHA-002** 核发：`SupplyRequest` **APPROVAL→AGREE** 后应用编排 | 创建时 **PREPARATION**，核发同事务 **COMPLETED** |


### 1.4 与相关聚合的关系

- **SupplyRequest（供应请求）**：`request_id` 关联上游请求；执行单 **AGREE** 后或 **type 27 汇总单药房核发（AS-IP-PHA-002）** 时由应用编排触发本聚合创建发放——见 **DM-WKF-006 §6.1.14** 跨域编排表格。
- **InventoryItem（库存项）**：本聚合 `COMPLETED` 后，应用编排调用 InventoryItem 增减数量；本聚合不直接写库存主账。
- **MedicationDefinition / DeviceDefinition**：`item_table + item_id` 记录发放对象，物资类别由目录域维护。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表聚合（发放事实一行一单，无聚合内子表）。

**表结构**：

- 主表：`wkf_supply_delivery`（主键：`id`）

**高频查询模式**：

- 按发放标识：主键查询。
- 按供应请求：`request_id` 索引批量回查。
- 按汇总批次：`summary_id` 索引（汇总路径）。

### 2.2 聚合根标识


| 属性     | 类型     | 说明                             |
| ------ | ------ | ------------------------------ |
| 供应发放标识 | `Long` | 主键，对应 `wkf_supply_delivery.id` |


### 2.3 状态机（DispenseStatus）

```text
普通/进货/出库/退库/损益/调拨/盘点路径：
  创建后直接为 COMPLETED（已发放），由应用层在执行单 SupplyRequestAgreedEvent 后一次性写入

药品汇总发放路径（type 27，**AS-IP-PHA-002**）：
  药房核发时 createSummarySupplyDelivery → PREPARATION →（同事务）COMPLETED
  库存扣减在 MedicationDispense 完成路径，不由本聚合 COMPLETED 触发
```

#### 状态定义（对齐 `DispenseStatus`）


| value | code           | info | 语义                 |
| ----- | -------------- | ---- | ------------------ |
| 1     | DRAFT          | 草稿   | 草稿态，可修改/删除（汇总路径起点） |
| 2     | PREPARATION    | 待配药  | 汇总发放路径，等待配药        |
| 3     | PREPARED       | 已配药  | 已配药，等待发放           |
| 4     | IN_PROGRESS    | 待发药  | 等待实际发放             |
| 5     | ON_HOLD        | 暂停   | 发放流程暂停             |
| 6     | COMPLETED      | 已发放  | 终态，发放完成            |
| 7     | STOPPED        | 停止   | 终态，流程停止            |
| 8     | DECLINED       | 拒绝发药 | 终态，拒绝执行            |
| 9     | CANCELLED      | 撤回   | 终态，已撤销             |
| 10    | SUMMARIZED     | 已汇总  | 汇总批次已汇总，允许继续发放     |
| 11    | PART_COMPLETED | 部分发药 | 部分发放完成，允许继续发放/退药   |
| —     | PENDING_REFUND | 待退药  | 等待退药审核             |
| —     | PART_REFUND    | 部分退药 | 部分退药中              |
| —     | REFUNDED       | 已退药  | 终态，全部退药            |
| —     | UNKNOWN        | 未知   | 迁移/异常保留态，人工修复      |


> **状态枚举说明**：持久化字段 `status_enum` 对齐 `DispenseStatus` 枚举（**String 存储，存枚举 code**）；枚举 code 以实现枚举为权威，与 **DM-WKF-007** §4.1 同族口径横向对齐。

---

## 3. 实体

```text
SupplyDelivery（聚合根，单表聚合）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                  SupplyDelivery                      │
  │  - requestId / summaryId / statusEnum / typeEnum     │
  │  - itemTable / itemId / quantity / unitCode          │
  │  - lotNumber / traceNo / supplierId                  │
  │  - practitionerId / receiverId / receiveTime         │
  │  - basedOnTable / basedOnIds / occurrenceTime        │
  └──────────────────────────────────────────────────────┘
```

### 3.1 供应发放（SupplyDelivery）


| 属性      | 字段名               | 类型           | 必填  | 说明                                 |
| ------- | ----------------- | ------------ | --- | ---------------------------------- |
| 供应发放标识  | `id`              | `Long`       | 是   | 主键                                 |
| 供应请求标识  | `request_id`      | `Long`       | 否   | 关联上游 SupplyRequest                 |
| 汇总编号    | `summary_id`      | `Long`       | 否   | 汇总发放编号（type 27 路径）                 |
| 发放状态    | `status_enum`     | `Integer`    | 是   | DispenseStatus 枚举                  |
| 患者标识    | `patient_id`      | `Long`       | 否   | 患者主体（药品汇总发药等场景）                    |
| 单据类型    | `type_enum`       | `Integer`    | 是   | SupplyType 枚举，与来源 SupplyRequest 一致 |
| 发放项目来源表 | `item_table`      | `String`     | 否   | 物品来源表名                             |
| 发放项目标识  | `item_id`         | `Long`       | 否   | 物品 ID                              |
| 来源表     | `based_on_table`  | `String`     | 否   | 基于来源表                              |
| 来源标识列表  | `based_on_ids`    | `String`     | 否   | 来源 ID（可多值）                         |
| 数量单位编码  | `unit_code`       | `String`     | 否   | 计量单位                               |
| 数量      | `quantity`        | `BigDecimal` | 否   | 发放数量；盘点差异行可负；退药可负                  |
| 状态说明    | `condition`       | `String`     | 否   | 状态说明                               |
| 产品批号    | `lot_number`      | `String`     | 否   | 批号                                 |
| 追溯码     | `trace_no`        | `String`     | 否   | 追溯码                                |
| 供应商标识   | `supplier_id`     | `Long`       | 否   | 采购场景                               |
| 经办人标识   | `practitioner_id` | `Long`       | 否   | 供应/经办人                             |
| 发放时间    | `occurrence_time` | `Date`       | 否   | 发放时间                               |
| 接收人标识   | `receiver_id`     | `Long`       | 否   | 接收人                                |
| 接收时间    | `receive_time`    | `Date`       | 否   | 接收时间                               |


> 通用审计字段继承自 HisBaseEntity，不在此处逐行列出。

---

## 4. 值对象

### 4.1 发放状态（DispenseStatus）

与 **§2.3** 状态定义表一致；持久化字段 `status_enum` 为 **String**，存院内 `DispenseStatus` 的 **code**，与语义一一对应；与 **DM-WKF-007** §4.1 口径同族时可横向对齐。


| 编码值（示意） | code（缩写） | 名称（中文） | 说明       |
| ------- | -------- | ------ | -------- |
| 1       | `DR`     | 草稿     | 可修改/删除   |
| 2       | `PR`     | 待配药    | 汇总路径待发   |
| 3       | `IN`     | 待发药    | 待发核对发放窗口 |
| 4       | `CO`     | 已发放    | 发放完成     |
| 5       | `ON`     | 暂停     | 流程暂停     |
| 6       | `ST`     | 停止     | 终态       |
| 7       | `DE`     | 拒绝发药   | 终态       |
| 8       | `SR`     | 已汇总    | 汇总中间态    |
| 9       | `PC`     | 部分发药   | 分次发放中    |
| 10      | `PFR`    | 部分退药   | 部分退      |
| 12      | `RE`     | 已退药    | 终态       |
| 13      | `UN`     | 未知     | 迁移保留     |
| 14      | `PD`     | 已配药    | 已配待发     |
| 15      | `CA`     | 撤回     | 终态       |
| 16      | `PRD`    | 待退药    | 待退审核     |


> 若本表编码与运行时代码不一致，以 **实现枚举** 覆盖文档；修订时同步 **DM-WKF-007**。

### 4.2 单据类型（SupplyType）

`type_enum` 使用 `**SupplyType.value`**，与来源 SupplyRequest 行保持一致。**完整白名单（共 28 项）以 `DM-WKF-006` §4.1 为唯一权威**；本聚合不单独维护类型枚举。

> **迁移说明**：存量库若仍存在旧版「一品多义」整型码，按 **DM-WKF-006** §9.3 映射到 §4.1；发放侧读写以迁移后白名单为准。

---

## 5. 领域事件

### 5.1 事件定义


| #   | 事件名称（中文） | 英文名称                           | 触发时机                                   | 携带数据（最小合同）                                                           | 消费方             | 版本  |
| --- | -------- | ------------------------------ | -------------------------------------- | -------------------------------------------------------------------- | --------------- | --- |
| 1   | 供应发放已创建  | `SupplyDeliveryCreatedEvent`   | 各 create* 方法落库成功后                      | `id`、`requestId`、`typeEnum`、`statusEnum`（初态）、`quantity`、`occurredAt` | 领域事件后查询刷新、库存编排触发    | v1  |
| 2   | 供应发放已完成  | `SupplyDeliveryCompletedEvent` | `updateCompletedStatusBatch` 或汇总路径最终确认 | `id`、`requestId`、`occurrenceTime`、`occurredAt`                       | QueryService、对账报表、下游完成通知 | v1  |
| 3   | 供应发放已取消  | `SupplyDeliveryCancelledEvent` | 汇总路径取消/拒绝                              | `id`、`requestId`、`changeReasonCode`（可选）、`occurredAt`                 | QueryService、汇总执行编排      | v1  |
| 4   | 供应发放已删除  | `SupplyDeliveryDeletedEvent`   | `deleteSupplyDeliveryByReqId` 逻辑删除后    | `id`、`requestId`、`occurredAt`                                        | QueryService、关联清理        | v1  |


### 5.2 事件触发规则


| 操作              | 方法                                      | 发布事件                           | 下游编排                   |
| --------------- | --------------------------------------- | ------------------------------ | ---------------------- |
| 创建普通/调拨/盘点/汇总发放 | **6.1.1**～**6.1.4** 各 create*           | `SupplyDeliveryCreatedEvent`   | 库存编排（InventoryItem 增减） |
| 汇总路径批量置完成       | **6.1.5** `updateCompletedStatusBatch`  | `SupplyDeliveryCompletedEvent` | 对账报表更新                 |
| 汇总路径取消/拒绝       | 汇总状态流转                                  | `SupplyDeliveryCancelledEvent` | 同步撤销关联占位任务             |
| 逻辑删除            | **6.1.6** `deleteSupplyDeliveryByReqId` | `SupplyDeliveryDeletedEvent`   | QueryService清理                  |


---

## 6. 领域服务

### 6.1 写侧领域服务（SupplyDeliveryDomainService）

> **分层说明**：按 `request_id` / `summary_id` 的加载与校验属读侧（**§6.2**）；写侧编排应先调读侧再调下列动作。
>
> **发放生成时机总约定**：**6.1.1**～**6.1.3** 均由应用层在收到 `**SupplyRequestAgreedEvent`（执行单）** 后触发；**6.1.4** 由汇总单 `SupplyRequestCreatedEvent`（`SEND` 初态）下游编排触发；**禁止**在申请单 AGREE 后直接调用本聚合写方法。

---

**一、普通发放（执行单 AGREE 后，创建即 `COMPLETED`）**

#### 6.1.1 创建供应发放（`createSupplyDelivery`）

- **业务意义**：供应请求执行单审批同意（`SupplyRequestAgreedEvent`）后，按执行单明细批量写入发放事实；创建即进入 `**COMPLETED`**（普通供应链路径，**BR-SDL-003**）。
- **覆盖单据类型（`type_enum`）**：进货/入库（`2`/`11`/`13`/`21`）、出库（`4`）、退库/退货（`3`/`5`/`14`/`22`）、损益（`6`/`15`/`23`）；具体 `type_enum` 由调用方传入，须与来源 SupplyRequest 行一致。
- **边界说明**：仅写 `wkf_supply_delivery`；库存账面增减由应用编排调用 **InventoryItem**；损益的`quantity` 正增负损，退库为负向数量或单独退药记录（见本稿业务约定）。
- **入参**：待生成发放的执行单行集合（含 `request_id`、`type_enum`、`item_table`/`item_id`、`quantity`、`unit_code`、`lot_number`、`occurrence_time` 等，以本稿约定为准）、业务时间 `now`。
- **出参**：新建发放实体列表或标识列表（以实现契约为准）。
- **前置条件**：**BR-SDL-001**（`quantity` 非零）；同请求防重见读侧 `supplyDeliveryValidation`；上游执行单须已 `AGREE`。
- **状态变化**：创建即 `**COMPLETED`**。
- **聚合内影响**：批量插入主表；写入 `request_id`、`type_enum`、`item_table`/`item_id`、`quantity`、`lot_number`、`occurrence_time` 等。
- **事件输出**：`SupplyDeliveryCreatedEvent`。
- **失败语义**：执行单未 `AGREE`、防重命中、必填缺失时拒绝。

#### 6.1.2 创建调拨发放（`createTransferDelivery`）

- **业务意义**：调拨执行单审批同意后，同时为**源位置（出）和目标位置（入）**各创建一条发放记录，共享同一 `request_id`，以 `quantity` 正负区分方向（或以字段区分，以本稿约定为准）；双侧均创建即 `**COMPLETED`**。
- **覆盖单据类型（`type_enum`）**：调入/调出/调入申请系列（`8`/`9`/`10`/`17`/`18`/`19`/`25`/`26`）。
- **边界说明**：仅写 `wkf_supply_delivery`；InventoryItem 源减目增由应用编排在本方法后调用。
- **入参**：调拨执行单行（含源/目位置信息、`request_id`、物品、数量等，以本稿约定为准）。
- **出参**：两条发放记录标识（以落库与交互契约为准）。
- **前置条件**：**BR-SDL-001**；上游调拨执行单已 `AGREE`；源 ≠ 目（**BR-SR-007**）。
- **状态变化**：创建即 `**COMPLETED`**（双侧各一条）。
- **聚合内影响**：插入两行，`request_id` 相同，分别记录出库侧和入库侧。
- **事件输出**：`SupplyDeliveryCreatedEvent`（两条各一次，或批量一次，以落库与交互契约为准）。
- **失败语义**：单据未 `AGREE`、双侧位置相同、数量非法时拒绝。

#### 6.1.3 创建盘点差异发放（`createStocktakingDelivery`）

- **业务意义**：盘点执行单审批同意后，**仅对有差异须落账的明细行**创建发放记录；`quantity` 正值为盘盈（增），负值为盘亏（减），零差异行不创建；创建即 `**COMPLETED`**。
- **覆盖单据类型（`type_enum`）**：药库盘点（`7`）、药房盘点（`16`）、科室盘点（`24`）、科室耗材盘点（`28`）。
- **边界说明**：仅写 `wkf_supply_delivery`；InventoryItem 差异调整由应用编排调用；无差异行跳过，不产生空发放。
- **入参**：盘点执行单中差异行集合（`request_id`、`item_table`/`item_id`、盘点差量 `quantity`（正盈负亏，不为 0）、`lot_number`（可选）等，以本稿约定为准）。
- **出参**：新建发放标识列表（以落库与交互契约为准）。
- **前置条件**：**BR-SDL-001**（`quantity ≠ 0`，零差异行由应用层过滤后不传入）；上游盘点执行单已 `AGREE`。
- **状态变化**：创建即 `**COMPLETED`**。
- **聚合内影响**：批量插入有差异的盘点发放行；`type_enum` 与来源盘点单一致。
- **事件输出**：`SupplyDeliveryCreatedEvent`。
- **失败语义**：数量为零、单据未 `AGREE` 时拒绝。

---

**二、汇总发放（药品汇总单 APPROVAL 后、药房核发时创建，进入流程状态机）**

#### 6.1.4 创建药品汇总发放（`createSummarySupplyDelivery`）

- **业务意义**：药品汇总单（type 27）在护士站发送后处于 **APPROVAL**，由**药房核发**编排（**AS-IP-PHA-002** IF-IPS-03）触发本方法为每条汇总明细创建发放头/行，进入待发药链路（首态 `**PREPARATION`**；与 **BR-SDL-004** 一致）。
- **覆盖单据类型（`type_enum`）**：**仅 `27`**（`MEDICATION_SUMMARY_ORDER`）。
- **边界说明**：不写库存台账；与 SupplyRequest 汇总单通过 `summary_id` / `request_id` 关联由编排保证；**不**适用于 type 28（耗材盘点走 **6.1.3**）。
- **入参**：`medicationId`、`lotNumber`、`patientId`、`minQuantity`、`minUnitCode`、`receiverId`、`supplyRequestId`、`summaryId` 等（以本稿约定为准）。
- **出参**：`void` 或新建发放标识（以落库与交互契约为准）。
- **前置条件**：患者与目录引用合法；批号与数量口径通过校验；来源 SupplyRequest type 27 已处于 **APPROVAL**。
- **状态变化**：创建为汇总路径首态 **PREPARATION**；同事务内由 `updateCompletedStatusBatch` 推进 **COMPLETED**。
- **聚合内影响**：插入主表；绑定 `item_table`/`item_id`、患者、批号、`summary_id`、`receiver_id` 等。
- **事件输出**：`SupplyDeliveryCreatedEvent`。
- **失败语义**：参数非法、汇总策略不允许、来源 SupplyRequest 非 **APPROVAL** 时拒绝。

---

**三、状态推进与删除**

#### 6.1.5 批量更新为已完成（`updateCompletedStatusBatch`）

- **业务意义**：汇总或批量发放场景下，将多条发放记录统一推进到完成态并收口经办人与时间。
- **边界说明**：仅更新本表状态与时间类字段；不替代库存扣减编排。
- **入参**：发放标识列表 `supplyDeliveryIdList`、经办人/时间等业务字段（以本稿约定为准）。
- **出参**：`void` 或更新条数（以落库与交互契约为准）。
- **前置条件**：当前状态允许进入 `**COMPLETED`**；非终态锁定（**BR-SDL-002**）。
- **状态变化**：`PREPARATION` / `IN_PROGRESS` 等 → `**COMPLETED`**。
- **聚合内影响**：批量 `UPDATE` 状态、经办人、完成时间等。
- **事件输出**：`SupplyDeliveryCompletedEvent`（每条或批量一次，以落库与交互契约为准）。
- **失败语义**：含非法状态行、并发冲突时拒绝或部分失败策略以落库与交互契约为准。

#### 6.1.6 按请求删除发放（`deleteSupplyDeliveryByReqId`）

- **业务意义**：审批回退或纠错场景下，按供应请求批量撤销尚未被下游固化的发放行（逻辑删除或等价策略以落库与交互契约为准）。
- **边界说明**：若发放已驱动库存扣减，须先由编排完成回补或冲正，**不得**仅靠本方法静默修复跨域状态。
- **入参**：`requestIdList`。
- **出参**：`void`。
- **前置条件**：业务规则允许删除；无未闭合对账引用（以实现校验为准）。
- **状态变化**：逻辑删除标记或物理删除（以落库与交互契约为准）。
- **聚合内影响**：按 `request_id` 批量删除/标记删除。
- **事件输出**：`SupplyDeliveryDeletedEvent`（若采用逻辑删除）。
- **失败语义**：存在禁止删除的已完成发放或下游锁定时拒绝。

### 6.2 读侧查询服务（SupplyDeliveryQueryService）

#### 6.2.1 按 ID 列表查询发放（`selectByIdList`）

- **业务意义**：按主键批量加载发放记录，供详情与状态回查。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_supply_delivery` 主键 IN。
- **过滤口径**：排除逻辑删除（若存在）；租户可见性以落库与交互契约为准。
- **组装规则**：按入参或主键顺序返回QueryService 列表查询。
- **返回约束**：允许空列表；大批量建议分页。

#### 6.2.2 按请求 ID 列表查询发放（`selectByRequestIdList`）

- **业务意义**：按 `request_id` 批量回查发放，支撑审批后核验与对账。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_supply_delivery` 按 `request_id` IN。
- **过滤口径**：同 **6.2.1**。
- **组装规则**：可按请求分组返回（以实现契约为准）。
- **返回约束**：不返回其他租户数据。

#### 6.2.3 发放完成性校验（`supplyDeliveryValidation`）

- **业务意义**：判断给定请求是否已存在 `**COMPLETED`**（或等价「已发放」态）发放，用于审批防重与幂等门控。
- **边界说明**：只读断言；**不得**与写侧方法同名混放在写路径。
- **查询范围（读库，无写入）**：`wkf_supply_delivery` 按 `request_id` 与状态子集。
- **过滤口径**：已完成态命中即视为已发放事实存在。
- **组装规则**：返回 `boolean` 或可解释原因码（以落库与交互契约为准）。
- **返回约束**：调用方据此决定是否允许再次「执行单 AGREE → 生成发放」。

### 6.3 领域边界说明

- **聚合内动作**：发放记录创建、状态推进（草稿→待配→待发→已完成）、终态锁定与允许路径下的删除。
- **跨域编排**：库存增减、费用结算由应用层协调；本聚合提供发放事实与事件，**不**直接修改 **InventoryItem** 数量列。
- **非本聚合职责**：供应请求审批（SupplyRequest）、物资目录维护、安全库存预警、跨库房报表。

---

## 7. 不变式


| 编号         | 不变式                  | 说明                                                      |
| ---------- | -------------------- | ------------------------------------------------------- |
| BR-SDL-001 | `quantity` 不得为 0     | 零数量不应形成发放事实；盘点差异行由应用层在传入前过滤                             |
| BR-SDL-002 | 终态发放不可逆              | 已完成/已取消后不得回退状态                                          |
| BR-SDL-003 | 普通路径创建即 `COMPLETED`  | 对应执行单 SupplyRequestAgreedEvent 后直接完成的语义                 |
| BR-SDL-004 | 汇总路径须与 §2.3 状态机首态一致  | 文档口径为 `DRAFT` 起流转；若实现以 `PREPARATION` 为创建首态，须与本稿状态口径一致 |
| BR-SDL-005 | 发放与请求 `type_enum` 一致 | 避免跨类型错配                                                 |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文   | 聚合                                    | 关联方式                     | 说明                                   |
| ------- | ------------------------------------- | ------------------------ | ------------------------------------ |
| 供应请求管理  | SupplyRequest                         | `request_id`             | **执行单 AGREE** 后由编排触发发放创建（见 **§9.3**） |
| 药品/耗材目录 | MedicationDefinition/DeviceDefinition | `item_table` + `item_id` | 记录发放对象                               |
| 供应商管理   | Supplier                              | `supplier_id`            | 采购场景                                 |
| 患者管理    | Patient                               | `patient_id`             | 发药/退药场景                              |


### 8.2 下游影响


| 下游上下文 | 聚合/模型                   | 触发方式    | 说明          |
| ----- | ----------------------- | ------- | ----------- |
| 库存管理  | InventoryItem           | 应用层编排调用 | 发放创建后触发库存增减 |
| 领域事件后查询刷新 | SupplyDeliveryQueryService | 事件订阅    | 支撑查询与报表     |


### 8.3 引用规则

> **统一规则**：跨上下文只允许传递 `SupplyDeliveryId`（Long）；禁止直接读取 `wkf_supply_delivery` 表；消费方通过应用服务接口或领域事件获取发放状态。

### 8.4 事件流示意图

```text
执行单 SupplyRequestAgreedEvent
    │
    ├─ 普通/退库/损益  ──► createSupplyDelivery      ──► SupplyDeliveryCreatedEvent ──► InventoryItem 增减
    ├─ 调拨           ──► createTransferDelivery     ──► SupplyDeliveryCreatedEvent ──► InventoryItem 源减目增
    └─ 盘点           ──► createStocktakingDelivery  ──► SupplyDeliveryCreatedEvent ──► InventoryItem 差异调整

汇总单 SupplyRequestCreatedEvent（SEND）
    └─ 药品汇总       ──► createSummarySupplyDelivery ──► SupplyDeliveryCreatedEvent
                                │
                   DRAFT→IN_PROGRESS→COMPLETED ──► SupplyDeliveryCompletedEvent ──► 对账报表
```

---

## 9. 补充

### 9.1 发放生成时机汇总

以下表格是 **DM-WKF-006 §6.1.14 agreeRequest** 跨域编排表格在本聚合的对应承接。


| 触发条件                               | 触发事件（DM-WKF-006）                  | 本聚合方法                         | 初态                       | 说明                             |
| ---------------------------------- | --------------------------------- | ----------------------------- | ------------------------ | ------------------------------ |
| 进货/入库执行单 AGREE（type 2/11/13/21）    | `SupplyRequestAgreedEvent`        | `createSupplyDelivery`        | `COMPLETED`              | 含批号、发票号；科室入库虚拟库存 **BR-SR-018** |
| 出库执行单 AGREE（type 4）                | `SupplyRequestAgreedEvent`        | `createSupplyDelivery`        | `COMPLETED`              | 源库房扣减，扣减由 InventoryItem 完成     |
| 退库/退货执行单 AGREE（type 3/5/14/22）     | `SupplyRequestAgreedEvent`        | `createSupplyDelivery`        | `COMPLETED`              | 负向数量或退库标记（见本稿业务约定）              |
| 损益执行单 AGREE（type 6/15/23）          | `SupplyRequestAgreedEvent`        | `createSupplyDelivery`        | `COMPLETED`              | 正增负损                           |
| 调拨执行单 AGREE（type 8～10/17～19/25/26） | `SupplyRequestAgreedEvent`        | `createTransferDelivery`      | `COMPLETED`              | 双侧各一条；源减目增                     |
| 盘点/耗材盘点执行单 AGREE（type 7/16/24/28）  | `SupplyRequestAgreedEvent`        | `createStocktakingDelivery`   | `COMPLETED`              | 仅差异行；零差异行跳过                    |
| 药品汇总单 药房核发（type 27）           | **AS-IP-PHA-002** IF-IPS-03           | `createSummarySupplyDelivery` + `updateCompletedStatusBatch` | 创建 **PREPARATION** → **COMPLETED** | 护士站发送后 APPROVAL；**核发时**创建；写 receiverId；**不**在此扣库 |


### 9.2 与 SupplyRequest 的协作约定

1. **单据链路**：SupplyRequest（申请单）→ `SupplyRequestAgreedEvent`（申请单）→ 编排生成 SupplyRequest（执行单）→ `SupplyRequestAgreedEvent`（执行单）→ 本聚合 create*；**不存在** 申请单 AGREE 直接触发发放的路径。
2. **幂等保护**：应用层在调用 create* 前，须先调用 `supplyDeliveryValidation(requestId)` 确认该执行单尚未生成 `COMPLETED` 发放，防止重复发放。
3. **错误回退**：若发放已创建但需回退（如执行单审批撤销），须先由编排完成 InventoryItem 冲正，再调用 `deleteSupplyDeliveryByReqId`；不得仅删发放记录而遗留库存差异。
4. `**type_enum` 透传**：发放行的 `type_enum` 与来源 SupplyRequest 行保持一致，由调用方透传，**不**在本聚合内重新推断。


### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.2 | 2026-06-06 | 汇总发药触发改为 **AS-IP-PHA-002 核发**（APPROVAL→AGREE）；Delivery 护士站不预创建；扣库走 MedicationDispense |
| v1.0.1 | 2026-05-28 | **M1**：`§6.1/§6.2` 与 Java DomainService/QueryService 白名单对齐 |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`SupplyDeliveryDomainService`

| 方法 |
|------|
| `createSupplyDelivery` |
| `createTransferDelivery` |
| `createStocktakingDelivery` |
| `createSummarySupplyDelivery` |
| `updateCompletedStatusBatch` |
| `deleteSupplyDeliveryByReqId` |

### QueryService：`SupplyDeliveryQueryService`

| 方法 |
|------|
| `selectByIdList` |
| `selectByRequestIdList` |
| `supplyDeliveryValidation` |
| `findRequestIdsHavingCompletedDelivery` |
| `listDeliveriesForSummarySlipHeaders` |

