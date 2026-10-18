# DM-WKF-006 SupplyRequest 供应请求

> **限界上下文**：WKF 域 · 供应申请管理  
> **聚合类型**：实体聚合  
> **版本**：v1.27

---

## 1. 概述

### 1.1 业务定义

供应请求（SupplyRequest）用于承载 **药库、药房、科室业务** 三条菜单线下、**经本聚合审批（或汇总类既定规则）后会驱动库存数量或库存协作事实变化** 的业务单据。`wkf_supply_request` 采用同表多形态：同一业务单据以 `bus_no` 聚合多行明细，共享 `status_enum`；每行一条物品维度，以 `type_enum` 绑定 **§4.1** 中的唯一单据类型。

**明确不包含（不归本聚合 `SupplyType` 表达）**：

- **药房发药单**、**病人发药**路径下的发药动作——发放事实与库存扣减由 **MedicationDispense（药品发放）** 及上游医嘱域编排承载。  
- **临床科室发出/发耗材**（医嘱、收费、护士站驱动的 **DeviceDispense** 路径）——**不**写入本聚合 **§4.1**；**手工科室领用单（`DEPT_ISSUE` · 值 `29`）** 见 **§4.1.4** 与 **§6.1.12**；**科室退领单（`DEPT_ISSUE_RETURN` · 值 `30`）** 见 **§4.1.4** 与 **§6.1.13**，为库房菜单下单端点加回，与临床发放链无关。  
- **中心物资库独立菜单线**（原「物资业务单据」整线）——本版 **§4.1** 不分配 `SupplyType`；若产品仍需共表，走 **§9.3** 迁移或独立 DM。  
- **业务月结、库存警戒、用后销毁记录、养护记录** 等非「单笔单据驱动库存变更」能力——不进入 **§4.1**。

### 1.2 核心职责


| 职责     | 说明                                                                                                                                                                  |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 单据创建   | **§6.1.1**～**§6.1.9** 按业务动作建草稿（药库订货/退货/出库/退库/损益/盘点/调拨，药房/科室请领/入库等）；**§6.1.10**/**§6.1.11** 汇总与耗材盘点；退库/关联入库等 **original_bus_no** 随建单或应用编排生成执行单时写入（见 **§6.1** 建单公共约定） |
| 审批流转   | **§6.1.13**～**§6.1.16**；汇总药品路径允许创建即 **SEND**（**§2.3**、**§5.2**）；**已失效**由应用层落库并发 **SupplyRequestUpdateEvent**                                                        |
| 来源追溯   | **original_bus_no** 与 **bus_no** 语义见 **§3.1**                                                                                                                       |
| 汇总撤销   | **§6.1.18** `cancelSummarySupplyRequest`                                                                                                                            |
| 关联发放网关 | 审批 **AGREE**（或汇总下游确认）后由应用编排 **SupplyDelivery**、**InventoryItem**                                                                                                    |


### 1.3 适用范围


| 业务线    | 说明                                                                                                                 |
| ------ | ------------------------------------------------------------------------------------------------------------------ |
| 药库业务单据 | 订货、进货、退货、出库、退库、损益、盘点、调入、调出、调入申请、进货验收（**§4.1** 值 `1`～`11`）                                                          |
| 药房业务单据 | 请领、入库、退库、损益、盘点、调入、调出、调入申请（`12`～`19`）；**不含发药单**；**药品汇总单** 见值 `27`                                                   |
| 科室业务单据 | 对应院内「科室业务单据」菜单中的请领/入退库/损益/盘点/调入调出/**领用/退领**/**采购申请**等（`20`～`26`、`29`～`31`）；**科室耗材盘点单** 见值 `28`（与值 `24` **科室盘点单** 区分：`24` 为科室侧通用盘点，`28` 为耗材专项盘点） |
| 汇总与专项  | **药品汇总单**（`27`）、**科室耗材盘点单**（`28`）                                                                                  |


### 1.4 与相关聚合的关系

- **SupplyDelivery（供应发放）**：审批或汇总规则满足后，由应用编排创建发放事实；`deliver_id` / `summary_id` 回写以落库与交互契约为准。  
- **InventoryItem（库存项）**：数量变更由应用编排调用库存域完成。  
- **MedicationDispense**：**药品汇总单** 经本聚合与 **SupplyDelivery** 编排后，发药事实仍落在 **MedicationDispense**（见 **DM-MED-002**）；**非** 以 `SupplyType` 冒充发药单。  
- **DeviceDispense**：**临床科室发出**（医嘱/收费链）不在 **§4.1**；**手工 `DEPT_ISSUE`（29）** / **`DEPT_ISSUE_RETURN`（30）** 在本聚合；**科室耗材盘点** 仅盘点审批与差异入账，不替代发放聚合。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表多行（同一 `bus_no` 下多行明细，共享审批状态；一行对应一个物品维度）。

**表结构**：

- 主表：`wkf_supply_request`（主键：`id`）

**高频查询模式**：

- 按 `bus_no`：加载整张单据全部明细。  
- 按 `original_bus_no`：溯源派生单。  
- 按 `summary_id`：汇总领药、汇总撤销、关联 **SupplyDelivery**。  
- 按主键列表：批量勾选审批。

### 2.2 聚合根标识


| 属性     | 类型                 | 说明            |
| ------ | ------------------ | ------------- |
| 供应请求标识 | `Long`（`id`）       | 主键            |
| 业务单据号  | `String`（`bus_no`） | 单据聚合键，同单据多行共享 |


### 2.3 状态机（SupplyRequestStatus / `status_enum`）

```text
PENDING_APPROVAL(1) --submitApproval--> APPROVAL(2)
APPROVAL(2) --agreeRequest--> AGREE(3)
APPROVAL(2) --rejectRequest--> REJECT(4)
PENDING_APPROVAL/APPROVAL --withdrawApproval--> WITHDRAW(9)

（分支）createSummarySupplyRequest ──> PENDING_APPROVAL(0) ──> submitApproval ──> APPROVAL(2) ──> agreeRequest ──> AGREE(3)   // type 27 护士站保存→发送→药房核发

（应用层/作业）──> EXPIRED_INVALIDATED(11)   // 无本聚合写方法；落库后须发 SupplyRequestUpdateEvent
```


| 状态  | 英文                    | 说明                                                             | 允许的操作                   |
| --- | --------------------- | -------------------------------------------------------------- | ----------------------- |
| 待审核 | `PENDING_APPROVAL`    | 默认状态（**§4.1** 值 `1`～`26`、`28` 等常规路径）                           | 提交审批 / 改明细（同号修订） / 整单删除 |
| 审核中 | `APPROVAL`            | 已提交审批                                                          | 同意 / 驳回 / 撤回            |
| 同意  | `AGREE`               | 审批通过终态（动账后不可再改明细）                                              | —                       |
| 驳回  | `REJECT`              | 审批驳回；**可改明细 / 再提交 / 整单删除**（改单重插后回落 `PENDING_APPROVAL`）           | 保存修订 / 提交审批 / 删除      |
| 已撤回 | `WITHDRAW`            | 从审核中撤回；**可改明细 / 再提交 / 整单删除**（改单重插后回落 `PENDING_APPROVAL`）         | 保存修订 / 提交审批 / 删除      |
| 待复审 | `PENDING_REVIEW`      | 可选中间态                                                          | 提交审批                    |
| 已失效 | `EXPIRED_INVALIDATED` | 超期等终态                                                          | —                       |
| 已发送 | `SEND`                | **药品汇总单（§4.1 值 `27`）** 创建后的典型初态，表示汇总包已下发至药房/护士站执行链；**不**表示发药完成 | 下游编排消费；是否可再提交审批以本稿约定为准 |


> **状态枚举说明**：`status_enum` 为 **String**，存状态枚举 **code**，与 **§4.2** 一致。**动账/通过终态**为 `AGREE` / `EXPIRED_INVALIDATED`；`REJECT` / `WITHDRAW` 属**审批结果态但仍可编辑重提**（**BR-SR-003** 可修订集合含 `PENDING_APPROVAL`/`REJECT`/`WITHDRAW`；同号全量修订重插后状态回落 `PENDING_APPROVAL`）。文中出现的 `27`、`28` 仅作历史对照/排序编号，不作为落库码值；是否走 **6.1.12** 仍以 **本稿 §6.1** 的产品分支约定为准。**EXPIRED_INVALIDATED** 仅要求状态机与事件契约一致，**不**映射到本聚合任一 **§6.1** 写方法。

---

## 3. 实体

### 3.1 供应请求（SupplyRequest）


| 属性     | 字段名                   | 类型           | 必填  | 说明                                                                                                                                                |
| ------ | --------------------- | ------------ | --- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 供应请求标识 | `id`                  | `Long`       | 是   | 主键                                                                                                                                                |
| 业务单据号  | `bus_no`              | `String`     | 是   | 单据聚合键                                                                                                                                             |
| 来源单据号  | `original_bus_no`     | `String`     | 否   | **关联原单号**：退库单/退货单等指向「被退」的**原入库或原出库** `bus_no`；关联入库/冲销类执行单指向来源申请或来源执行单 `bus_no`。**在创建本单或应用编排生成执行单时一次性写入**，不通过单独领域命令维护；纠错走整单作废/冲正流程（**本稿 **§6.1****） |
| 单据类型   | `type_enum`           | `String`     | 是   | **§4.1 SupplyType**：持久化 **`getCode()`**（**B.10.8.1**），与 `com.zxplat.domain.share.enums.SupplyType` 一致；**不**以 sortOrder/整型落库 |
| 单据状态   | `status_enum`         | `String`     | 是   | **§4.2 SupplyStatus**，持久化 **`getCode()`**                                                                                                       |
| 意图编码   | `intent_enum`         | `String`     | 否   | 意图编码                                                                                                                                              |
| 供应类别   | `category_enum`       | `String`     | 是   | **§4.3 SupplyCategory**，持久化 **`getCode()`**                                                                                                     |
| 优先级    | `priority_enum`       | `String`     | 否   | ActPriority 持久化码                                                                                                                                  |
| 汇总编号   | `summary_id`          | `Long`       | 否   | **药品汇总单**、汇总撤销、与 **SupplyDelivery** 对齐                                                                                                            |
| 发放记录标识 | `deliver_id`          | `Long`       | 否   | 关联发放                                                                                                                                              |
| 患者标识   | `patient_id`          | `Long`       | 否   | **药品汇总单** 等按患者汇总的场景                                                                                                                               |
| 物品所属表  | `item_table`          | `String`     | 是   | 物品来源表                                                                                                                                             |
| 数量     | `item_quantity`       | `BigDecimal` | 是   | 默认：执行数量（拆零、正数，与库存一致）。**盘点类**（`7`/`16`/`24`/`28`）：**盈亏数量**（拆零，盈为正、亏为负、可无差异为 0）                                                                 |
| 合计数量   | `total_quantity`      | `BigDecimal` | 否   | 默认：合计。**盘点类**：**实盘数量**（拆零，≥0）                                                                                                                    |
| 物品定义标识 | `item_id`             | `Long`       | 是   | 物品定义                                                                                                                                              |
| 计量单位编码 | `unit_code`           | `String`     | 是   | 计量单位                                                                                                                                              |
| 期望时间   | `occurrence_time`     | `Date`       | 否   | 期望时间                                                                                                                                              |
| 经办人标识  | `practitioner_id`     | `Long`       | 否   | 经办人                                                                                                                                               |
| 供应商标识  | `supplier_id`         | `Long`       | 否   | 供应商                                                                                                                                               |
| 原因     | `reason`              | `String`     | 否   | 申请原因                                                                                                                                              |
| 源位置类型  | `source_type_enum`    | `String`     | 否   | **§4.4 SupplyRequestType**，持久化 **`getCode()`**；订货等场景可无源位置时允许空（与落库可空一致）                                                                 |
| 源位置标识  | `source_location_id`  | `Long`       | 否   | 源位置；同上可空                                                                                                                                          |
| 目标位置类型 | `purpose_type_enum`   | `String`     | 是   | **§4.4 SupplyRequestType**，持久化 **`getCode()`**                                                                                                |
| 目标位置标识 | `purpose_location_id` | `Long`       | 是   | 目标位置                                                                                                                                              |
| 领用科室标识 | `consuming_organization_id` | `Long` | 否   | **行政 Organization.id**；**`DEPT_ISSUE`** 建改单客户端手选落库；**`DEPT_ISSUE_RETURN`** 自源领用单继承；历史行可空（读侧可回落 `Location.organization_id`）；**禁止**写入 `purpose_*` |
| 审批人标识  | `approver_id`         | `Long`       | 否   | 审批人                                                                                                                                               |
| 审批时间   | `approval_time`       | `Date`       | 否   | 审批时间                                                                                                                                              |
| 申请人标识  | `applicant_id`        | `Long`       | 是   | 申请人                                                                                                                                               |
| 申请时间   | `apply_time`          | `Date`       | 是   | 申请时间                                                                                                                                              |
| 产品批号   | `lot_number`          | `String`     | 否   | 批号                                                                                                                                                |
| 追溯码    | `trace_no`            | `String`     | 否   | 追溯码；**TRC-WIDE-R-001**：物理列目标 **VARCHAR(50000)**（与 **DM-CUS-010** 主/子表单码列同宽口径；禁止 **VARCHAR(255)** 承载原始逗号扫码串） |
| 采购发票号 | `invoice_no`          | `String`     | 否   | 采购发票号；**同 `bus_no` 多行共享**（与单号机制一致，读侧取任一行）；进销存 **3503/3504 `purc_invo_no`** 真源；库默认 **`0`**；进货单单头选填（前端**不必填**，空白落库占位 **`0`**）；退货可落本列或上报时回落关联进货单 |
| 单价     | `price`               | `BigDecimal` | 否   | 单价                                                                                                                                                |
| 总价     | `total_price`         | `BigDecimal` | 否   | 默认：行总价。**盘点类**：**实盘金额**                                                                                                                                 |
| 盈亏金额   | `profit_loss_price`   | `BigDecimal` | 否   | **盘点类**：盈亏金额（盈为正、亏为负）；其它类型可空                                                                                                                         |
| 备注     | `remake`              | `String`     | 否   | 备注                                                                                                                                                |

### 3.1.1 药库 `source_*` / `purpose_*` 语义（库房业务）

> **总规则**：**单库房就是目的**（仅 `purpose_*`）；**双端就是出为源、进为目的**（`source_*` = 发出/扣减侧，`purpose_*` = 接收/增加侧）。

- **双端点（存在明确「从库房 A 到库房 B」）**：`source_location_id` 表示 **A**（**出** / 发出/扣减侧），`purpose_location_id` 表示 **B**（**进** / 接收/增加侧）；`source_type_enum` / `purpose_type_enum` 分别由 **Location（及 Form/形态）** 回查回填，存 **`SupplyRequestType#getCode()`** 字符串，**禁止**手填与主数据不一致的类型码；**两侧均必填**，**禁止**同值双写冒充单端点。**典型**：药库进货、出库、调拨、请领、药房入库等。**对外 REST**：**HTTP 请求体可不传**类型枚举，由 **`Inventory*Mapper`** + **`LocationQueryService#requireSupplyRequestTypeForLocationId`** 推导后写入 Command（**B.10.8.1**）。  
- **单库房（仅操作一个库房点位）**：**就是目的**——`purpose_location_id` / `purpose_type_enum` 必填，`source_*` **NULL**（库表 `V103` 已放宽非空；**禁止** source/purpose 同值镜像双写）。**典型**：药库订货（`1`）、药库/药房/科室盘点（`7`/`16`/`24`/`28`）、药库/药房/科室损益（`6`/`15`/`23`）、**科室领用（`29`）**、**科室退领（`30`）**、药品汇总（`27`）；对外建单/查询/选品仅以 **purpose** 为真源。  
- **与订货/进货列表筛选**：订货单「目的药库」以 `purpose_location_id` 为真源（**IF-WHS-IN-12** 等）。**药库进货分页（IF-WHS-IN-02）** 以 `source_location_id` 为锚点，可选 `purpose_location_id` 收窄（**双端点**进货单）。

> 设计预留（若实现尚未落库）：`expirationDate`、`isBatches`、`orgId`、`inventoryId` 等以落库实现为准。

### 3.2 聚合关系图

```text
SupplyRequest（聚合根）
════════════════════════════════════════════════════════════
  常规单据（1～26、28）: PENDING_APPROVAL ⇄ APPROVAL → AGREE → 编排 SupplyDelivery + InventoryItem
  药品汇总单（27）: **6.1.10** createSummarySupplyRequest → **PENDING_APPROVAL** → **6.1.13** submitApproval → **APPROVAL** → **AS-IP-PHA-002** agreeRequest → **AGREE**
  科室耗材盘点（28）: **6.1.11** createDeviceSummarySupplyRequest → 审批链 6.1.13～6.1.15 → AGREE → 盘点差异入账编排
  科室领用（29）: **6.1.12** createDeptIssue → 审批链 6.1.14～6.1.16 → AGREE → 出库发放 + purpose 扣减
  科室退领（30）: **6.1.13** createDeptIssueReturn → 审批链 6.1.14～6.1.16 → AGREE → 入库发放 + purpose 加回
```

---

## 4. 值对象

### 4.1 单据类型（SupplyType）

`type_enum` 使用 **SupplyType**：**一种业务单据对应一个枚举常量**（共 **31** 个）。下列为 **文档权威白名单**；**`value` 列仅为文档排序/人类对照**；**库表与领域持久化一律为各枚举 `getCode()` 字符串**（`com.zxplat.domain.share.enums.*`，**B.10.8.1**）；历史整型迁移见 **§9.3**。

#### 4.1.1 药库业务单据（`1`～`11`）


| value | code                               | info    | 语义          |
| ----- | ---------------------------------- | ------- | ----------- |
| 1     | `CABINET_PURCHASE_ORDER`           | 药库订货单   | 向供应商订货      |
| 2     | `CABINET_INBOUND_RECEIPT`          | 药库进货单   | 采购到货入库      |
| 3     | `CABINET_RETURN_TO_SUPPLIER`       | 药库退货单   | 退供应商出库      |
| 4     | `CABINET_STOCK_OUT`                | 药库出库单   | 对药房/科室等下游出库 |
| 5     | `CABINET_RETURN_STOCK_IN`   | 退库入库单   | 药房/科室退库 agree 后药库侧只读壳单（库存已在退库 agree 落账） |
| 6     | `CABINET_GAIN_LOSS`                | 药库损益单   | 盘盈盘亏、报损报溢   |
| 7     | `CABINET_STOCKTAKE`                | 药库盘点单   | 盘点确认        |
| 8     | `CABINET_TRANSFER_IN`              | 药库调入单   | 调入          |
| 9     | `CABINET_TRANSFER_OUT`             | 药库调出单   | 调出          |
| 10    | `CABINET_TRANSFER_IN_REQUEST`      | 药库调入申请单 | 调入申请        |
| 11    | `CABINET_INBOUND_RECEIPT_INSPECTION` | 药库进货验收单 | 进货验收        |

> **命名对齐**：旧稿 `CABINET_GOODS_RECEIPT` / `CABINET_GOODS_RECEIPT_INSPECTION` 已合并为与代码一致的 **`CABINET_INBOUND_RECEIPT`** / **`CABINET_INBOUND_RECEIPT_INSPECTION`**；`value`=`2`/`11` 仍为 **§4.1.1** 文档序号，**不**作为 API/落库数值。

#### 4.1.2 药房业务单据（`12`～`19`）


| value | code                           | info    | 语义    |
| ----- | ------------------------------ | ------- | ----- |
| 12    | `PHARMACY_REQUISITION`         | 药房请领单   | 向药库申领 |
| 13    | `PHARMACY_STOCK_IN`            | 药房入库单   | 验收入库  |
| 14    | `PHARMACY_RETURN_TO_WAREHOUSE`  | 药房退库单   | 退回药库  |
| 15    | `PHARMACY_GAIN_LOSS`           | 药房损益单   | 损益调整  |
| 16    | `PHARMACY_STOCKTAKE`           | 药房盘点单   | 盘点确认  |
| 17    | `PHARMACY_TRANSFER_IN`         | 药房调入单   | 调入    |
| 18    | `PHARMACY_TRANSFER_OUT`        | 药房调出单   | 调出    |
| 19    | `PHARMACY_TRANSFER_IN_REQUEST` | 药房调入申请单 | 调入申请  |


#### 4.1.3 科室业务单据（`20`～`26`）

> **命名说明**：`code` 使用前缀 `DEPT_` 表示**科室业务线**（药品、耗材、材料等凡落在「科室业务单据」菜单下的入出库类单据均可挂接）。**info** 列中文名为领域展示口径（「科室 + 动作」），**不**将业务限定为「材料」；若院内 HIS 菜单仍显示「科室材料××单」，可在**界面别名/映射层**对照本文 `info`，**不改变** `type_enum` 语义。


| value | code                | info  | 语义                                    |
| ----- | ------------------- | ----- | ------------------------------------- |
| 20    | `DEPT_REQUISITION`  | 科室请领单 | 科室向中心库申领（药品/耗材/物资等，以 `item_table` 为准） |
| 21    | `DEPT_STOCK_IN`     | 科室入库单 | 科室验收入库                                |
| 22    | `DEPT_RETURN`       | 科室退库单 | 退回上级库                                 |
| 23    | `DEPT_GAIN_LOSS`    | 科室损益单 | 科室损益调整                                |
| 24    | `DEPT_STOCKTAKE`    | 科室盘点单 | 科室侧通用盘点结果单                            |
| 25    | `DEPT_TRANSFER_IN`  | 科室调入单 | 科室调入                                  |
| 26    | `DEPT_TRANSFER_OUT` | 科室调出单 | 科室调出                                  |


#### 4.1.4 汇总、科室耗材盘点、科室领用/退领与科室采购申请（`27`～`31`）


| value | code                       | info    | 语义                                                                                                |
| ----- | -------------------------- | ------- | ------------------------------------------------------------------------------------------------- |
| 27    | `MEDICATION_SUMMARY_ORDER` | 药品汇总单   | 住院护士站汇总领药（**AS-IP-NUR-010**）保存→发送；药房汇总核发（**AS-IP-PHA-002**）；初态 **PENDING_APPROVAL**，发送后 **APPROVAL** |
| 28    | `DEPT_DEVICE_STOCKTAKE`    | 科室耗材盘点单 | 科室侧**耗材**盘点建单；物品维度以耗材目录为主；审批 **AGREE** 后驱动盘盈盘亏入账                                                  |
| 29    | `DEPT_ISSUE`               | 科室领用单   | 科室药房手工领用消耗；**单端点** `purpose_*`（科室药房，库存扣减真源）；**领用科室**落库 **`consuming_organization_id`**（行政 Organization，与科室药房解耦）；同意后在科室药房扣减库存；**AS-INV-DEP-004**；与 **DeviceDispense** 无关 |
| 30    | `DEPT_ISSUE_RETURN`        | 科室退领单   | 自已同意 **`DEPT_ISSUE`** 未用完退回；**`original_bus_no` 必填**；**单端点** `purpose_*`；同意后在科室药房**加回**库存；**AS-INV-DEP-009**；与 **`DEPT_RETURN`（退库）** 区分 |
| 31    | `DEPT_PURCHASE_REQUEST`    | 科室采购申请单 | 科室药房向院内提出**采购需求**（**申请单**）；**单端点** `purpose_*`=科室药房；**不**绑供应商（由下游药库订货填写）；同意**仅终态**；药库订货可**可选**拉取（**AS-INV-DEP-007** / **AS-INV-WHS-001**） |

#### 4.1.5 库存期初（`32`～`34`，AS-BAS-INV-001）

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 32 | `CABINET_OPENING` | 药库期初单 | 药库上线开账；**单端点** `purpose_*`；同意后整库期初真源（后单覆盖前单）；**不**走财务审批；busNo 前缀 `COP` |
| 33 | `PHARMACY_OPENING` | 药房期初单 | 药房上线开账；语义同药库期初；前缀 `POP` |
| 34 | `DEPT_OPENING` | 科室药房期初单 | 耗材房/科室药房开账；前缀 `DOP` |

> **持久化口径**：共 **34** 个 `SupplyType`；期初为 **BR-SR-012 执行单据**；见 **§6.1.20** `createOpening` / **§6.1.21** `voidOpening`。

### 4.2 单据状态（SupplyStatus）


| 编码值 | 常量                    | 中文  | 说明                                           |
| --- | --------------------- | --- | -------------------------------------------- |
| 1   | `PENDING_APPROVAL`    | 待审核 | 常规默认态                                        |
| 2   | `APPROVAL`            | 审核中 | 已提交待批                                        |
| 3   | `AGREE`               | 同意  | 审批通过终态                                       |
| 4   | `REJECT`              | 驳回  | 审批驳回终态                                       |
| 9   | `WITHDRAW`            | 已撤回 | 撤回终态                                         |
| 10  | `PENDING_REVIEW`      | 待复审 | 可选中间态                                        |
| 11  | `EXPIRED_INVALIDATED` | 已失效 | 超期等终态；**无**本聚合 **§6.1** 写方法，由应用层落状态并见 **§5** |
| 12  | `SEND`                | 已发送 | **药品汇总单（值 `27`）** 典型初态；其它类型新建默认不使用           |

> **持久化**：`status_enum` 存 **`SupplyStatus#getCode()`**；上表「编码值」列为历史 sortOrder **对照**，**不**作为落库整数。

### 4.3 单据类别（SupplyCategory）


| 值   | 常量             | 中文    |
| --- | -------------- | ----- |
| 1   | `STOCK_SUPPLY` | 库存供应  |
| 2   | `NON_STOCK`    | 非库存供应 |

> **持久化**：`category_enum` 存 **`SupplyCategory#getCode()`**（如 `STOCK_SUPPLY`）；上表「值」列为文档序号对照。

### 4.4 位置类型（SupplyRequestType）


| 值   | 常量           | 中文  |
| --- | ------------ | --- |
| 2   | `DEPARTMENT` | 科室  |
| 11  | `CABINET`    | 库房  |
| 16  | `PHARMACY`   | 药房  |

> **持久化**：`source_type_enum` / `purpose_type_enum` 存 **`SupplyRequestType#getCode()`**（如 `CABINET`）；上表「值」列为文档序号对照。

---

## 5. 领域事件

### 5.1 事件定义

本聚合按**每类业务动作**发布独立事件，消费者无需解析 `statusEnum` 即可知道发生了什么；同一张表承载**申请单**与**执行单据**两类记录，两类记录共用同一套事件，以 `typeEnum` / `originalBusNo` 区分上下文。


| #   | 事件名称（中文）  | 英文名称                                 | 触发写侧方法                                          | 携带数据（最小合同）                                                                                                                     | 消费方                                                                | 版本  |
| --- | --------- | ------------------------------------ | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ | --- |
| 1   | 供应请求已创建   | `SupplyRequestCreatedEvent`          | **6.1.1**～**6.1.11** 各建单方法；申请单通过后应用编排生成执行单时同样发布 | `busNo`、`typeEnum`、`statusEnum`（初态）、`occurredAt`、`tenantId`（若多租户）；可选 `originalBusNo`、`summaryId`、`patientId`（值 27）、`lineCount` | 领域事件后查询刷新、供应统计、下游订阅建单通知                                                | v1  |
| 2   | 供应请求已提交审批 | `SupplyRequestSubmittedEvent`        | **6.1.13** `submitApproval`                     | `busNo`、`typeEnum`、`applyTime`、`applicantId`、`occurredAt`                                                                      | 待办中心、审批通知                                                          | v1  |
| 3   | 供应请求已撤回   | `SupplyRequestWithdrawnEvent`        | **6.1.14** `withdrawApproval`                   | `busNo`、`typeEnum`、`occurredAt`                                                                                                | 领域事件后查询刷新、待办撤回通知                                                       | v1  |
| 4   | 供应请求已审批同意 | `SupplyRequestAgreedEvent`           | **6.1.15** `agreeRequest`                       | `busNo`、`typeEnum`、`approverId`、`approvalTime`、`occurredAt`；可选 `originalBusNo`（执行单场景）、`summaryId`                              | 领域事件后查询刷新、**申请单**：触发生成执行单；**执行单**：触发 SupplyDelivery + InventoryItem 编排 | v1  |
| 5   | 供应请求已审批驳回 | `SupplyRequestRejectedEvent`         | **6.1.16** `rejectRequest`                      | `busNo`、`typeEnum`、`approverId`、`approvalTime`、`changeReasonCode`（可选）、`occurredAt`                                             | 领域事件后查询刷新、驳回通知                                                         | v1  |
| 6   | 供应请求已逻辑删除 | `SupplyRequestDeletedEvent`          | **6.1.17** `removeByBusNo`                      | `busNo`、`typeEnum`、`occurredAt`                                                                                                | 领域事件后查询刷新、关联清理                                                         | v1  |
| 7   | 供应请求已失效   | `SupplyRequestExpiredEvent`          | 应用层定时/规则触发（无本聚合写方法）                             | `busNo`、`typeEnum`、`occurredAt`                                                                                                | 领域事件后查询刷新、过期告警                                                         | v1  |
| 8   | 药品汇总单已取消  | `SummarySupplyRequestCancelledEvent` | **6.1.18** `cancelSummarySupplyRequest`         | `summaryIdList`（或受影响 `busNo` 列表）、`occurredAt`                                                                                  | 领域事件后查询刷新、撤销 SupplyDelivery / 占位发药任务                                   | v1  |


> `**original_bus_no` 与事件**：执行单在建单或应用编排生成时写入 `originalBusNo`，应在同一事务末随 `SupplyRequestCreatedEvent` 发布（载荷带 `originalBusNo`）；本稿约定一种写法。

> `**SupplyRequestExpiredEvent`**：此终态由应用层定时任务/规则引擎触发，本聚合无对应写方法（见 **§2.3**）；应用层落状态后发布本事件。

> **事件落地**：是否写入消息总线、是否与库表同事务，以落库实现为准。

### 5.2 事件触发规则（操作 × 事件）


| 操作                            | 方法                   | 发布事件                                 | 下游编排（应用层）                                        |
| ----------------------------- | -------------------- | ------------------------------------ | ------------------------------------------------ |
| 建单（各 create*，含用户直接建单与编排生成执行单） | **6.1.1**～**6.1.11** | `SupplyRequestCreatedEvent`          | —                                                |
| 提交审批                          | **6.1.13**           | `SupplyRequestSubmittedEvent`        | 待办通知                                             |
| 撤回审批                          | **6.1.14**           | `SupplyRequestWithdrawnEvent`        | —                                                |
| 审批同意（**申请单**）                 | **6.1.15**           | `SupplyRequestAgreedEvent`           | **订货/请领等**：应用层 `agreeRequest` 仅终态，**不**在本事件回调内自动建执行单；执行单由用户在下游页面另行创建（见 **§5.3**） |
| 审批同意（**执行单据**）                | **6.1.15**           | `SupplyRequestAgreedEvent`           | 编排 SupplyDelivery + InventoryItem 变动（见 **§5.3**） |
| 审批驳回                          | **6.1.16**           | `SupplyRequestRejectedEvent`         | **AS-IP-PHA-002** IF-IPS-04（type **27** 待核发驳回） |
| 逻辑删除                          | **6.1.17**           | `SupplyRequestDeletedEvent`          | **AS-IP-NUR-010** IF-IP-SUM-08（type **27** 待发送撤销） |
| 应用层超时/规则失效                    | 非 §6.1 方法            | `SupplyRequestExpiredEvent`          | —                                                |
| 取消汇总单（按 summaryId）         | **6.1.18**           | `SummarySupplyRequestCancelledEvent` | 存量路径；**非** IF-IP-SUM-08（见 **§6.1.16**） |


### 5.3 两阶段审批与事件链路说明

本聚合同一张表承载两类性质不同的记录（**BR-SR-011**、**BR-SR-012**）：

- **申请单**（`createPurchaseOrder`/`createRequisition`/`createReturn` 等建出的记录）：表达申请意图，审批通过（`SupplyRequestAgreedEvent`）后**领域层仅置 `AGREE`**；**是否**生成执行单由**各应用服务**在 agree 之后或下游页面另行编排（订货→进货、请领→药库出库，见 **§6.1.14** 表 A 备注）。
- **执行单据**（应用编排生成，或 `createInboundReceipt`/`createStockOut`/`createGainLoss`/`createStocktake`/`createTransfer`/`createRequisition`（入库分支）/`createOpening` 等直接创建的记录）：审批通过（`SupplyRequestAgreedEvent`）后由**应用层编排**生成 `SupplyDelivery` 并触发 `InventoryItem` 增减。

**典型两阶段链路（以请领为例 · 对齐 **AS-INV-PHA-002** / **`department/requisition`**）**：

```text
① createRequisition（type 12/20，申请单）
       → SupplyRequestCreatedEvent（statusEnum=PENDING_APPROVAL）
   → submitApproval   → SupplyRequestSubmittedEvent
   → agreeRequest     → SupplyRequestAgreedEvent（申请单）
       → 【应用层 · 请领 agree 终点】仅 status=AGREE，不建出库/入库执行单
② 用户在药库出库页（AS-INV-WHS-003）另行 createStockOut（type 4，可 originalBusNo=请领单）
       → SupplyRequestCreatedEvent（出库单）
   → submitApproval → agreeRequest（出库执行单）
       → 【应用编排】SupplyDelivery（出库发放）+ InventoryItem 源药库减
       → 同时 createRequisition（type 13/21 壳单，bootstrapFinalStatusCode=AGREE，originalBusNo=出库单）
              → 目标药房/科室药房入库壳单只读台账（库存已在出库 agree 落账）
```

**典型免申请链路（以损益/盘点/调拨为例）**：

```text
createGainLoss / createStocktake / createTransfer（调出执行单，type 9/18/26 等）
       → SupplyRequestCreatedEvent（statusEnum=PENDING_APPROVAL）
   → submitApproval → SupplyRequestSubmittedEvent
   → agreeRequest   → SupplyRequestAgreedEvent（调出执行单）
       → 【应用编排】
           损溢：SupplyDelivery（损溢发放）+ InventoryItem 增/减
           盘点：对有差异须落账行 createStocktakingDelivery + InventoryItem 调整
           调拨（调出 agree · 药库 **AS-INV-WHS-009** / 药房 **AS-INV-PHA-008** / 科室 **`department/transferout`** 已落地）：
               createTransferDelivery + InventoryItem 源减目增（唯一落账点）
               → createTransfer 生成对侧调入壳单（type 8/17/25，`original_bus_no`=调出单号，
                 `bootstrapFinalStatusCode=AGREE`，只读台账，**不再**二次 agree/落账）
           退库（药房/科室退库 agree · **AS-INV-PHA-003** / **`department/returntowarehouse`** 已落地）：
               createSupplyDelivery + InventoryItem 源药房/科室减、目的药库增（唯一落账点）
               → createReturn 生成退库入库壳单（type 5，`original_bus_no`=退库单号，
                 `bootstrapFinalStatusCode=AGREE`，只读台账，**WHS_RSI_006** 禁止手工写/agree）
```

**调入执行单（type 8/17/25）**：**不支持**应用层手工新建（**WHS_TI_002** / **PHA_TI_002** / **DEPT_TI_002**）；仅调出 agree 编排生成壳单，或后续「调入申请 type 10/19 → 调出执行单」链路（未落地）。

> 上述跨域编排均属**应用层职责**，SupplyRequest 聚合本身不直接写 SupplyDelivery 或 InventoryItem（见 **§1.4**）。

---

## 6. 领域服务

### 6.1 写侧领域服务（SupplyRequestDomainService）

> **分层说明**：按 `bus_no` 的加载与校验属读侧（**§6.2**）；写侧编排应先调读侧再调下列动作，**禁止**在领域写服务中夹带「仅查询无状态变更」的伪写方法。

**建单公共约定**（**§6.1.1**～**§6.1.9** 默认遵循，条文另有说明除外）：入参为 `**CreateSupplyRequestCommand`** 或等价「行草稿列表 + 业务时钟」（字段全集 **本稿 **§6.1****）；生成或接收 `**bus_no`**；每行写 `**type_enum**`（调用方传入，须在本方法允许集合内）、`**category_enum**`、`**source_*` / `purpose_***`、`**item_table` / `item_id` / `item_quantity` / `unit_code**`、`**applicant_id` / `apply_time**` 等；初态默认 `**PENDING_APPROVAL**`；退库/退货类 `**original_bus_no` 必填**；不写 **SupplyDelivery** / 库存主表；成功发布 `**SupplyRequestCreateEvent`**（**§5**）。

#### 写侧方法索引（`type_enum` → 方法）


| `type_enum`           | 单据名称              | 方法（§）                                         |
| --------------------- | ----------------- | --------------------------------------------- |
| 1                     | 药库订货单             | **6.1.1** `createPurchaseOrder`               |
| 2、11                  | 药库进货单、进货验收单       | **6.1.2** `createInboundReceipt`                |
| 3                     | 药库退货单             | **6.1.3** `createReturnToSupplier`            |
| 4                     | 药库出库单             | **6.1.4** `createStockOut`                    |
| 5、14、22               | 退库入库壳单、药房退库单、科室退库单 | **6.1.5** `createReturn`                      |
| 6、15、23               | 药库损益单、药房损益单、科室损益单 | **6.1.6** `createGainLoss`                    |
| 7、16、24               | 药库盘点单、药房盘点单、科室盘点单 | **6.1.7** `createStocktake`                   |
| 8、9、10、17、18、19、25、26 | 各级调入/调出/调入申请单     | **6.1.8** `createTransfer`                    |
| 12、20                 | 药房请领单、科室请领单       | **6.1.9** `createRequisition`                 |
| 13、21                 | 药房入库单、科室入库单       | **6.1.9** `createRequisition`（入库执行单分支）        |
| 27                    | 药品汇总单             | **6.1.10** `createSummarySupplyRequest`       |
| 28                    | 科室耗材盘点单           | **6.1.11** `createDeviceSummarySupplyRequest` |
| 29                    | 科室领用单             | **6.1.12** `createDeptIssue`                  |
| 30                    | 科室退领单             | **6.1.13** `createDeptIssueReturn`            |
| 31                    | 科室采购申请单           | **6.1.1a** `createDeptPurchaseRequest`        |
| 32、33、34             | 药库/药房/科室药房期初单 | **6.1.20** `createOpening`                    |
| —                     | 作废已同意期初（状态）   | **6.1.21** `voidOpening`                      |
| —                     | 提交审批              | **6.1.13** `submitApproval`                   |
| —                     | 撤回审批              | **6.1.14** `withdrawApproval`                 |
| —                     | 审批同意              | **6.1.15** `agreeRequest`                     |
| —                     | 审批驳回              | **6.1.16** `rejectRequest`                    |
| —                     | 逻辑删除              | **6.1.17** `removeByBusNo`                    |
| —                     | 取消汇总单             | **6.1.18** `cancelSummarySupplyRequest`       |


---

**一、申请单（用户页面发起；有申请类业务；审批通过后由应用编排生成执行单——不直接产生发放）**

> 本组方法创建的是**申请单**（Record A）：表达申请意图，审批通过后 **不直接** 触发库存变动，而是由应用层编排生成对应**执行单据**（新 `bus_no`，`originalBusNo` 指向本申请单），再由执行单审批通过后触发发放与库存；**BR-SR-011** 约束。

#### 6.1.1 创建药库订货单（`createPurchaseOrder`）

- **业务意义**：**药库**向外部供应商发起订货草稿（**BR-SR-016**：**`CABINET_PURCHASE_ORDER`** 只能由药库发起；**不含**科室侧 **`DEPT_PURCHASE_REQUEST`**）。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**仅 `1`**（`CABINET_PURCHASE_ORDER`）；应用层新建/修订请求**不传** `typeEnum`，由领域/Mapper 固定写入。
- **聚合内影响**：按明细创建 `SupplyRequest` 行并共享 `bus_no`；写入 `item_id / item_table / item_quantity / unit_code / price / total_price / purpose_location_id / purpose_type_enum / supplier_id`（可选）/ `practitioner_id`（经手人，应用层必填）/ `occurrence_time`（制单时刻，应用层门禁非空）/ `category_enum / applicant_id / apply_time`；订货场景 `source_location_id / source_type_enum` 通常为 **NULL**（单端点 **purpose** 真源，**§3.1.1**）；**`original_bus_no` 可选**——非空时指向已 **`AGREE`** 的 **`DEPT_PURCHASE_REQUEST`**（科室采购申请，**AS-INV-WHS-001** **IF-WHS-PO-12/13**），**禁止**无源以外类型；手工直建时 `original_bus_no` 为空。
- **关键约束**：`BR-SR-001`、`BR-SR-006`、`BR-SR-016`；`purpose_type_enum` 须为药库（`SupplyRequestType#getCode()` **`CABINET`**）；若填 `original_bus_no` 则原单须 **`DEPT_PURCHASE_REQUEST` + `AGREE`**，且尚未被其它未删除订货单占用（应用 **BR-WHS-PO-012**）。
- **修订路径（同号全量替换）**：首行传入既有 `bus_no` 时 → `findByBusNo` → **`SupplyRequest#assertPurchaseOrderLinesRevisable`**（须 type **1** 且各行可修订态 **`PENDING_APPROVAL`/`REJECT`/`WITHDRAW`**）→ 校验命令 **`expectedRowVersionForRevision`** 与同号各行 JPA **`version`** 一致（不一致对外 **`WHS_PO_105`** / 领域 `SUPPLY_REQUEST_INVALID_ARGUMENT`）→ 旧行 `is_deleted=true` 批量落库 → **`buildPurchaseOrderEntityFromItem`** 全量 `saveAll` 重插（新行状态回落 **`PENDING_APPROVAL`**）；改单时 **`original_bus_no`** 自存量首行快照保留，禁止客户端切换来源申请；**不**发布 **`SupplyRequestCreatedEvent`**（与进货 **`createInboundReceipt`** 修订一致）。
- **应用层读路径（非写能力）**：列表 **IF-WHS-PO-02**（**AS-INV-WHS-001**）按 **`bus_no`** 聚合展示（**`SupplyRequestDocumentListQuery`** → `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`），筛选锚点 **`purpose_location_id`**（`0` = 当前用户可见全部药库）；详情 **IF-WHS-PO-03** 经 **`getSupplyByBusNo`** 返回文档级 **`version`** 映射为 **`rowVersion`** 供修订乐观锁带回；可选拉源 **IF-WHS-PO-12/13**。
- **审批通过后（跨域，应用层编排）**：**本页 `agreeRequest` 仅置 `AGREE`，不触库存、不自动建进货单**（**AS-INV-WHS-001** **WHS-SDD-R-001**）。用户至 **AS-INV-WHS-002** 按已同意订货 **`bus_no`** 预填进货（**IF-WHS-IN-13** `getLinkablePurchaseOrderDetail`），落库仍走 **`createInboundReceipt`**（新 `bus_no`，`originalBusNo` 指向订货单）。
- **事件输出**：新建路径 **`SupplyRequestCreatedEvent`**；修订路径无 Created 事件。
- **失败语义**：明细为空、目的位置非药库、类型不为 `1`、修订态非待审核、乐观锁版本不匹配、来源科室采购申请不可引用时拒绝。

#### 6.1.1a 创建科室采购申请单（`createDeptPurchaseRequest`）

- **业务意义**：**科室药房**向院内提出采购需求草稿（**申请单**）；**不**直接向供应商订货（订货仍走 **`createPurchaseOrder`**）；填写交互与药库订货同构（**产品定义级选品**，**非**库存批号行）。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**仅 `31`**（`DEPT_PURCHASE_REQUEST`）；应用层新建/修订**不传** `typeEnum`，由领域/Mapper 固定写入；**`bus_no` 前缀** **`DPR`**（`SupplyType.busNoSeqKey`）。
- **聚合内影响**：按明细创建 `SupplyRequest` 行并共享 `bus_no`；写入 `item_id / item_table / item_quantity / unit_code / price / total_price / purpose_location_id / purpose_type_enum`（**`PHARMACY`** 族，形态 **`DEPARTMENT_PHARMACY`**）/ `practitioner_id`（可选）/ `occurrence_time` / `category_enum / applicant_id / apply_time`；**`source_location_id` / `source_type_enum` / `supplier_id` 恒 NULL**（单端点 **purpose**；供应商在下游订货填写）。
- **关键约束**：`BR-SR-001`、`BR-SR-006`；`purpose_location_id` 须为启用态科室药房；**不**适用 **BR-SR-016**（该规则仅约束药库订货）。
- **修订路径（同号全量替换）**：首行带既有 `bus_no` → **`SupplyRequest#assertDeptPurchaseRequestLinesRevisable`**（须 type **31** 且可修订态）→ 可选 **`expectedRowVersionForRevision`** 乐观锁 → 软删旧行 + 全量重插（回落 **`PENDING_APPROVAL`**）；**不**发布 Created 事件。
- **应用层读路径**：**AS-INV-DEP-007** 列表/详情；下游药库订货 **IF-WHS-PO-12/13** 仅读 **`AGREE`** 且未被未删除订货占用的申请。
- **审批通过后**：**`agreeRequest` 仅置 `AGREE`**，**不**触库存、**不**自动建订货/进货（**BR-DEP-CPR-009**）；药库可在订货页**可选**拉取。
- **事件输出**：新建 **`SupplyRequestCreatedEvent`**；修订无 Created。
- **失败语义**：明细为空、目的非科室药房、类型不为 `31`、修订态非法时拒绝。

#### 6.1.2 创建进货/验收单（`createInboundReceipt`）

> **实现名**：Java `SupplyRequestDomainService#createInboundReceipt`（历史文档别名 `createGoodsReceipt`）。

- **业务意义**：
  - **采购进货（`CABINET_INBOUND_RECEIPT`）**：药库订货单审批通过后由应用编排生成，或药库人员免申请直接创建；补充发票号、批号、验收信息后提交审批。
  - **进货验收（type 11）**：验收操作独立建单场景；可与订货/进货链通过 `original_bus_no` 关联。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**`CABINET_INBOUND_RECEIPT`** 或 **`CABINET_INBOUND_RECEIPT_INSPECTION`**，由调用方指定；禁止混单。
- **聚合内影响**：从来源单 copy 字段并使用新 `bus_no`；写入 `original_bus_no`（指向来源订货/合同 `bus_no`，若业务要求则必填，以本稿约定为准）；允许补充 `lot_number / trace_no / invoice_no / price / total_price` 等进货明细（**`invoice_no` 同号多行共享**，进销存 3503 `purc_invo_no`）。
- **关键约束**：`BR-SR-001`；`original_bus_no` 所指原单须已 `AGREE`（若关联创建时，本稿 **§6.1** 约束）。
- **应用层读路径（非写能力）**：进货模块 **IF-WHS-IN-13** `getLinkablePurchaseOrderDetail` 按订货 `bus_no` 读 **`getSupplyByBusNo`** 并拷贝头/行至进货表单 DTO（批号/追溯码留空）；落库仍走 **`createInboundReceipt`**。
- **事件输出**：`SupplyRequestCreatedEvent`。
- **失败语义**：原单未闭环、数量非法、类型不在允许集合时拒绝。

#### 6.1.3 创建药库退货单（`createReturnToSupplier`）

- **业务意义**：**药库**基于**已同意**的进货单（`original_bus_no` → 进货 `bus_no`），向外部供应商发起退货；**禁止自由选品**——明细须为进货行的子集，数量可部分退；同一进货单可多次建退货单直至各行可退余量为 0；本单审批即为终态——通过后直接触发退货发放与**源药库**库存扣减（**无**二次生成执行单）。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**仅 `3`**（`CABINET_RETURN_TO_SUPPLIER`）。
- **聚合内影响**：写入 `item_id / item_table / item_quantity / unit_code / source_location_id / purpose_location_id / original_bus_no / supplier_id`（可选）/ `practitioner_id`（可选，前端显式选择）/ `lot_number / trace_no / invoice_no`（可选，可空；上报 3504 时优先本列，空则回落关联进货单 `invoice_no`，再空占位 `0`）/ **`price` / `total_price`**（**BR-SR-019**：落库前写价门禁重算）/ `occurrence_time / reason / remake / category_enum / applicant_id / apply_time` 等。
- **写价前置（领域）**：`createReturnToSupplier` **须先**调用 **`SupplyRequestWritePricingSupport#applyReturnToSupplierCommand`**（**BR-SR-019** / **BR-MON-001**）——优先命令行已带的同源进货落库包装单价（应用层按行键回填，**BR-PRICE-SRC-001**），缺省解析批号/目录进价；再按拆零数量重算行 `total_price`；**`buildReturnToSupplierEntityFromItem`** 将 `price`/`total_price` 写入实体。
- **关键约束**：`BR-SR-008`（`**original_bus_no` 必填**且原始进货单已 `AGREE`、类型为 **`CABINET_INBOUND_RECEIPT`**、退货源药库 = 进货 `purpose_location_id`）；`BR-SR-009`（行键 **`item_table + item_id + normalize(批号)`**（批号归一化与库存可用量口径一致）；退货拆零量 ≤ 可退余量；可退余量 = 进货行量 − Σ 同键已占用退货量；占用仅计 **`original_bus_no = 进货 bus_no`** 且 `type_enum = CABINET_RETURN_TO_SUPPLIER`、状态 ∈ {`PENDING_APPROVAL`,`APPROVAL`,`AGREE`}；**`REJECT`/`WITHDRAW` 不计入**；修订/同意复检时 **排除本单 `bus_no`**）；**BR-SR-019**（行金额落库）；**源库实物库存**须 ≥ 本次退货量（应用层 **`AS-INV-WHS-005`** 校验；已调拨至药房导致源库为 0 时须先调回进货药库）。
- **修订路径（同号全量替换）**：首行传入既有 `bus_no` 时 → `findByBusNo` → **`SupplyRequest#assertReturnToSupplierLinesRevisable`**（须 type **3** 且各行 **`PENDING_APPROVAL`**）→ 旧行 `is_deleted=true` 批量落库 → 写价前置 → **`buildReturnToSupplierEntityFromItem`** 全量 `saveAll` 重插；**无** `rowVersion` 乐观锁（应用层传入 `rowVersion` 即 **`WHS_RT_105`**）；改单时 **`original_bus_no`** 自存量首行快照保留，禁止客户端切换来源进货单；**不**发布 **`SupplyRequestCreatedEvent`**。
- **应用层读路径（非写能力）**：**IF-WHS-RT-02** 列表按 `bus_no` 聚合，整单金额取行 `total_price` 之和（侧栏展示名由应用层富化；历史未落价可读侧按进货比例回补，见 **AS-INV-WHS-005** **BR-WHS-RT-010**）；**IF-WHS-RT-12** `pageLinkableGoodsReceipts` 分页已同意进货单（按 `bus_no` 聚合，过滤仍存在可退余量者）；**IF-WHS-RT-13** `getLinkableGoodsReceiptDetail` 按进货 `bus_no` 返回各行 `inboundQuantity` / `returnedQuantity` / `returnableQuantity` / `sourceAvailableQuantity`（改单预填可传 `excludeReturnBusNo`）；**禁止**自由选品（原 `pageProductsForReturnToSupplier` / RT-11 已删除）。
- **审批通过后（跨域，应用层编排）**：`agreeRequest` → flush → 防重复发放 → **`createSupplyDelivery`**（退货发放）→ 源药库 **`decreaseQuantitiesMerged`** → 追溯 OUT → 可选医保 3504（**AS-INV-WHS-005** IF-WHS-RT-08）；type **3** 须财务审批岗。
- **事件输出**：新建路径 **`SupplyRequestCreatedEvent`**；修订路径无 Created 事件。
- **失败语义**：无可退原单、超量退货、行不在进货范围内、源库库存不足、类型不为 `3`、`original_bus_no` 缺失、修订态非待审核、权威单价不可解析时拒绝。

#### 6.1.4 创建药库出库单（`createStockOut`）

- **业务意义**：**执行单据**——记录「药库向药房/科室出库」事实。通常由请领单 `AGREE` 后应用编排生成（`originalBusNo` 指向来源请领单），与请领入库单（目标侧）同时生成；也可由制单人免申请直接创建。**调拨不走本方法**（调拨走 `createTransfer`）。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**仅 `4`**（`CABINET_STOCK_OUT`）。
- **聚合内影响**：从请领单 copy 字段并使用新 `bus_no`；写入 `original_bus_no`（指向来源请领 `bus_no`，若业务要求则必填）；`source_type_enum` 须为 `CABINET`。
- **关键约束**：`BR-SR-001`；`source_type_enum` 须为 `CABINET`，目的角色合法。
- **审批通过后（跨域，应用层编排）**：生成 SupplyDelivery（药库出库发放）并扣减药库 InventoryItem。
- **事件输出**：`SupplyRequestCreatedEvent`。
- **失败语义**：源不为药库、目的角色非法、类型不为 `4` 时拒绝。

---

**二、退库/退货类（用户发起；`original_bus_no` 必填；审批通过后直接编排发放 + 库存变动）**

> 退库/退货单兼有"申请"语义（需人工审批）和"执行"效果（审批通过即触发发放与库存扣减/增加），**无**二次生成执行单的过程；**BR-SR-008**、**BR-SR-009** 约束。

#### 6.1.5 创建退库单（`createReturn`）

- **业务意义**：分两类，**禁止混单**：
  - **主动退库单（type 14 / 22）**：药房或科室将已入库物品退回上级药库；用户页面发起，`original_bus_no` 指向**已同意入库单**（药房 type 13）；审批通过后应用层编排退库发放 + 双侧库存调整，并 **`createReturn` 生成药库侧退库入库壳单**（见下）。
  - **退库入库壳单（type 5）**：药库侧**只读台账**；**不支持**应用层手工新建/修订/agree（**`WHS_RSI_006`**，对偶调入壳单 **`WHS_TI_002`**）。由药房/科室退库单 **`agree` 编排**生成：`original_bus_no` = 退库单 `bus_no`，`bootstrapFinalStatusCode=AGREE`；**库存已在退库 agree 落账**，壳单**不再**二次发放/改库存。壳单行须从已同意退库明细拷贝数量/批号/价/追溯，并**拷贝 `reason`**；写入 **`approver_id`/`approval_time`**（与退库同意人对齐；bootstrap **不**走 `approvalToAgree`）。
  - **历史说明**：type 5 曾实现为药库手工「退库入库」；现行口径以 **P-02 药房退库 agree 编排 + 调入壳单（W-08）对偶** 为准，药库不主动增库存。
- **状态变化**：主动退库单初态 `PENDING_APPROVAL`；壳单可由 **`bootstrapFinalStatusCode`** 直接落 `AGREE`。
- **适用单据类型（`type_enum`）**：`**5`（壳单）**、`**14`（药房退库）**、`**22`（科室退库）** 之一。
- **聚合内影响**：写入 `item_id / item_table / item_quantity / unit_code / source_location_id / purpose_location_id / original_bus_no / category_enum / applicant_id / apply_time / reason / approver_id / approval_time` 等；壳单行从已同意退库明细拷贝。
- **关键约束**：
  - 主动退库（type 14/22）：**`original_bus_no` 可空**（空=按源库库存自由选品）；非空时须指向已同意**入库单**（药房 `PHARMACY_STOCK_IN` / 科室 `DEPT_STOCK_IN`），并由应用层 **`ReturnToWarehouseStockInLineHelper`** 校验可退余量（`BR-SR-008`/`BR-SR-009` 对偶进货退货）。
  - 壳单（type 5）：**`original_bus_no` 必填**（指向已同意退库单）；**禁止** `createReturn` 修订路径（**`assertCabinetReturnStockInLinesRevisable`** 恒拒绝）。
- **修订路径（主动退库 type 14/22）**：首行 `busNo` → 软删重插；**`assertPharmacyReturnToWarehouseLinesRevisable`** / **`assertDeptReturnLinesRevisable`**；**不**发布 `SupplyRequestCreatedEvent`。
- **应用层读路径（药房退库 · P-02）**：**IF-PHA-RTW-11** `pageProductsForReturnToWarehouse`（源药房库存批号选品）；**IF-PHA-RTW-12** `pageLinkableReturnSourceDocuments`；**IF-PHA-RTW-13** `getLinkablePharmacyStockInDetail`。
- **应用层读路径（科室退库）**：**IF-DEP-RTW-11** 选品对称；**IF-DEP-RTW-12/13** 可关联科室入库单。
- **审批通过后（主动退库 · 跨域编排）**：`agreeRequest` → flush → **`createSupplyDelivery`** → 源药房减、目的药库增 → 追溯 OUT → **`createReturn`** 生成 type 5 壳单（**`bootstrapFinalStatusCode=AGREE`**）。
- **审批通过后（退库入库壳单）**：**无**二次 `agreeRequest` / 发放 / 库存（**W-05** 应用包 **`returnstockin`** 写接口拒绝 **`WHS_RSI_006`**；**AS 待建**）。
- **事件输出**：新建路径 `SupplyRequestCreatedEvent`（含壳单）；主动退库修订无 Created 事件。
- **失败语义**：无可退原单、超可退余量、壳单修订、类型不在允许集合、位置方向矛盾时拒绝。

---

**三、免申请执行单据（无申请阶段，直接创建；审批通过后直接编排发放 + 库存变动）**

> 本组方法创建的是**执行单据**（Record B），**跳过申请阶段**（**BR-SR-015**）：由制单人直接创建并走审批，通过后应用层编排 SupplyDelivery + InventoryItem；**不**先生成申请单再生成执行单。损溢、盘点、调拨均属此类。

#### 6.1.6 创建损益单（`createGainLoss`）

- **业务意义**：**免申请**，直接创建损益单，覆盖报损（库存减少）与报溢/报增（库存增加）两个方向；适用于药库、药房、科室各级。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：`**6`（`CABINET_GAIN_LOSS`）**、`**15`（`PHARMACY_GAIN_LOSS`）**、`**23`（`DEPT_GAIN_LOSS`）** 之一，禁止混单。
- **聚合内影响**：写入 `item_id / item_table / item_quantity`（正数报增/负数报损，以本稿 **§6.1** 口径为准）/ `unit_code / purpose_location_id / purpose_type_enum / lot_number`（可选）/ `reason / category_enum / applicant_id / apply_time`；`source_*` **NULL**（单端点 purpose 真源）。
- **关键约束**：`BR-SR-001`；报损数量不得超过对应库存项当前可用量（应用层创建前断言）；类型须与位置层级对应。
- **审批通过后（跨域，应用层编排）**：生成 SupplyDelivery（损溢发放）+ InventoryItem 增减（报损减、报溢增）。
- **事件输出**：`SupplyRequestCreatedEvent`。
- **失败语义**：损溢为零或违反库存下限、类型与位置不匹配时拒绝。

#### 6.1.7 创建盘点单（`createStocktake`）

- **业务意义**：**免申请**，直接创建盘点执行单；适用于药库、药房、科室各级。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：`**7`（`CABINET_STOCKTAKE`）**、`**16`（`PHARMACY_STOCKTAKE`）**、`**24`（`DEPT_STOCKTAKE`）** 之一，禁止混单。
- **行字段口径（盘点专用）**：
  - `item_quantity`：**盈亏数量**（拆零；盈为正、亏为负、可无差异为 **0**）；
  - `total_quantity`：**实盘数量**（拆零，≥ 0）；
  - `total_price`：**实盘金额**；
  - `profit_loss_price`：**盈亏金额**（盈为正、亏为负）；
  - 盘点库房：**仅** `purpose_location_id` / `purpose_type_enum`（单端点 purpose 真源）；`source_*` **NULL**。
- **聚合内影响**：写入 `item_id / item_table / item_quantity / total_quantity / total_price / profit_loss_price / unit_code / purpose_location_id / purpose_type_enum / lot_number`（可选）/ `reason`（可选）/ `category_enum / applicant_id / apply_time`。
- **关键约束**：`BR-SR-001`；盈亏/实盘数量口径合法；类型须与 **purpose** 位置层级对应。
- **审批通过后（跨域，应用层编排）**：对 **`item_quantity`（盈亏）≠ 0** 的明细行，调用 `createStocktakingDelivery`（发放 `quantity` = 行盈亏，可正可负）+ InventoryItem **按盈亏做加减**（**不得**用 `total_quantity` 覆盖账面）；`item_quantity = 0` 行不产生发放记录。
- **修订路径（同号全量替换）**：首行传入既有 `bus_no` 时，对该单号下未删除行批量逻辑删除后按命令全量重插；门控 `assertStocktakeLinesRevisable`（须为盘点类 `type_enum` 且各行 `PENDING_APPROVAL`）；**不**再发布 `SupplyRequestCreatedEvent`（与订货/出库修订一致）。
- **事件输出**：新建路径 `SupplyRequestCreatedEvent`；修订路径无 Created 事件。
- **失败语义**：盈亏/实盘数量非法、盘点库房缺失、类型与位置不匹配、修订时单号不存在或状态不可编辑时拒绝。

#### 6.1.8 创建调拨单（`createTransfer`）

- **业务意义**：在源位置与目标位置之间发起调拨；`type_enum` 区分调入/调出/调入申请三种单据形态，适用于药库、药房、科室各级。**每一张供应单**独占一个 `bus_no`；方向由 `source_location_id`（发出/扣减侧）与 `purpose_location_id`（接收/增加侧）表达，由列表筛选与权限视图呈现给不同侧管理员。
- **状态变化**：默认 `statusEnum` 初始化为 `PENDING_APPROVAL`；命令可选 **`bootstrapFinalStatusCode`**（如 `AGREE`）覆盖初态——用于调出 agree 后生成对侧**调入壳单**（只读台账，与 **`createRequisition`** 壳单语义对齐）。
- **适用单据类型（`type_enum`）**：
  - 药库：`**8`**（调入）、`**9**`（调出）、`**10**`（调入申请）
  - 药房：`**17**`（调入）、`**18**`（调出）、`**19**`（调入申请）
  - 科室：`**25**`（调入）、`**26**`（调出）
  - 禁止混单；禁止 `source_location_id = purpose_location_id`。
- **聚合内影响**：写入 `item_id / item_table / item_quantity / unit_code / source_location_id / source_type_enum / purpose_location_id / purpose_type_enum / category_enum / applicant_id / apply_time`；可选 `lot_number / trace_no / supplier_id / price / total_price / practitioner_id / occurrence_time / reason`。
  - **调出执行单（主动制单）**：`original_bus_no` 通常为空（免申请）或指向请领/调入申请等上游单（若业务关联）。
  - **调入壳单（应用编排生成）**：`**original_bus_no` 必填**（指向已同意调出单 `bus_no`）；`type_enum` 按目的侧 **LocationForm** 映射：药库 → `CABINET_TRANSFER_IN`；药房 → `PHARMACY_TRANSFER_IN`；科室药房 → `DEPT_TRANSFER_IN`。壳单行须从已同意调出明细拷贝数量/批号/价/追溯，并写入 **`approver_id`/`approval_time`**（与调出同意人对齐；bootstrap **不**走 `approvalToAgree`）；制单人优先沿用调出行 `applicant_id`。
  - **科室调出（type 26 · 应用层 `department/transferout`）**：源侧 **仅** `LocationForm.DEPARTMENT_PHARMACY`（`LocationQueryService#listAllActiveDepartmentPharmacies`）；目的侧可为三方任一库位；**禁止**关联科室请领 `original_bus_no`（**DEPT_TR_002**）。
- **关键约束**：`BR-SR-007`（`source_location_id ≠ purpose_location_id`）；`BR-SR-006`；类型须与方向和位置层级对应。
- **修订路径（同号全量替换）**：首行携带既有 `bus_no` 时 → 软删同号未删除行 → 全量重插；当前 **`zxplat`** 已实现调出修订门控（须各行可修订态）：**`CABINET_TRANSFER_OUT`** → **`assertCabinetTransferOutLinesRevisable`**；**`PHARMACY_TRANSFER_OUT`** → **`assertPharmacyTransferOutLinesRevisable`**；**`DEPT_TRANSFER_OUT`** → **`assertDeptTransferOutLinesRevisable`**；**不**发布 `SupplyRequestCreatedEvent`。
- **审批通过后（跨域，应用层编排 · 调出执行单）**：`agreeRequest` 后 **`createTransferDelivery`**（源减、目增各一条发放记录，同一调出单）+ **InventoryItem 双向变动**（**唯一落账点**）→ 可选 **`createTransfer`** 生成对侧调入壳单（`bootstrapFinalStatusCode=AGREE`，行带 `approverId`/`approvalTime`）。
- **审批通过后（调入壳单）**：**无**二次 `agreeRequest` / 发放 / 库存变动；应用层写路径拒绝：**药库** **`WHS_TI_002`**、**药房** **`PHA_TI_002`**、**科室** **`DEPT_TI_002`**（包 `warehouse/transferin`、`pharmacy/transferin`、`department/transferin`）。
- **事件输出**：新建路径 `SupplyRequestCreatedEvent`；修订路径无 Created 事件。
- **失败语义**：源目相同、位置类型非法、类型与方向不匹配、修订时单号不存在或状态不可编辑时拒绝。

---

**四、请领类（申请单 + 配套执行单共用一个方法，由 `type_enum` 区分）**

> 本方法覆盖**两类记录**：请领申请单（type 12/20，用户发起，审批同意后**不**在本聚合内自动生成执行单）；入库执行单（type 13/21，多由药库出库 agree 编排生成壳单，初态 `AGREE`）。两类记录**禁止混单**。

#### 6.1.9 创建请领/入库单（`createRequisition`）

- **业务意义**：本方法覆盖**两类记录**，`type_enum` 决定当前创建的是申请单还是执行单：
  - **请领申请单（type 12/20）**：药房/科室药房向药库发起请领（**BR-SR-017**：`source_location_id` 须药库 `WAREHOUSE`，`purpose_location_id` 须药房 `PHARMACY` 或科室药房 `DEPARTMENT_PHARMACY`）；**`agreeRequest` 仅终态**（**AS-INV-PHA-002** / **`inventory/department/requisition`**），下游药库出库在 **AS-INV-WHS-003** 另行 `createStockOut`。
  - **入库执行单（type 13/21）**：多由药库出库 agree 系统生成壳单（`bootstrapFinalStatusCode=AGREE`）；应用包 **`pharmacy/stockin`**、**`department/stockin`** 禁止手工写/agree；库存落账在出库 agree。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：`**12`（`PHARMACY_REQUISITION`）**、`**20`（`DEPT_REQUISITION`）**、`**13`（`PHARMACY_STOCK_IN`）**、`**21`（`DEPT_STOCK_IN`）** 之一；请领单与入库执行单禁止混单。
- **聚合内影响**：
  - 请领申请单：写入 `item_id / item_table / item_quantity / unit_code / lot_number / start_time / end_time / source_location_id / source_type_enum / purpose_location_id / purpose_type_enum / category_enum / applicant_id / apply_time`；`start_time`/`end_time`（生产日期/有效期）自 **`CreateRequisitionItemCommand`** 落库，供详情回显并与库存批号 **`expiration`** 对齐；`original_bus_no` 为空。
  - 入库执行单：从请领单 copy 字段并使用新 `bus_no`；`**original_bus_no` 必填**（指向来源请领单 `bus_no`）。
- **关键约束**：`BR-SR-001`、`BR-SR-006`、`BR-SR-017`（请领方向）、**BR-SR-020**（请领/批号级建单须持久化 `end_time`）；入库执行单须原请领单已 `AGREE`（**BR-SR-011**）。
- **修订路径（同号全量替换 · 请领 type 12/20）**：首行携带既有 `bus_no` 时 → 软删同号未删除行 → 全量重插；门控：**`PHARMACY_REQUISITION`** → **`assertPharmacyRequisitionLinesRevisable`**；**`DEPT_REQUISITION`** → **`assertDeptRequisitionLinesRevisable`**（须各行可修订态）；**不**发布 `SupplyRequestCreatedEvent`。
- **修订路径（入库 type 13）**：领域允许 **`assertPharmacyStockInLinesRevisable`** 同号重插；应用层 **`pharmacy/stockin`** / **`department/stockin`** 仍拒绝手工修订（壳单只读）。**`DEPT_STOCK_IN`** 不走 `createRequisition` 修订。
- **审批通过后（请领申请单 type 12/20）**：**无**领域内自动 `createStockOut` / 入库壳单；应用层 agree 路径禁止扩展库存编排（对偶订货单 **WHS-SDD-R-001**）。
- **审批通过后（入库执行单 type 13/21 · 手工验收路径，若存在）**：生成 SupplyDelivery + InventoryItem 增加。
- **应用层只读壳单（type 13/21 · 药库出库 agree 生成）**：`PHARMACY_STOCK_IN` / `DEPT_STOCK_IN` 由 **AS-INV-WHS-003** 出库同意后系统落库、初态 **`AGREE`**；应用包 **`pharmacy/stockin`**、**`department/stockin`** **拒绝一切手工写**（**`PHA_IN_002`** / **`DEPT_IN_002`**）；库存落账在出库 agree 相位完成。
- **应用层请领申请（type 12/20）**：
  - **药房请领**：`**inventory/pharmacy/requisition`** · **`InventoryPharmacyRequisitionAppService`** · 错误码 **`PHA_QL_*`** · 目的库位 `PHARMACY`。
  - **科室药房请领**：`**inventory/department/requisition`** · **`InventoryDepartmentRequisitionAppService`** · 错误码 **`DEP_QL_*`** · 目的库位 **`DEPARTMENT_PHARMACY`**（`LocationQueryService#listAllActiveDepartmentPharmacies`）；列表 `purposeLocationId=0` → **`InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID`**。
- **事件输出**：`SupplyRequestCreatedEvent`。
- **失败语义**：请领方向违反约束（**BR-SR-017**）、原单未 `AGREE`（入库执行单时）、类型不在允许集合时拒绝。

---

**五、汇总与专项盘点**

#### 6.1.10 创建药品汇总单（`createSummarySupplyRequest`）

- **业务意义**：按药品 + 库房等维度汇总生成药品汇总单，承载护士站批量领药明细行。**不**在本方法内写 **MedicationDispense** / **SupplyDelivery**；发放行汇总由 **MedicationDispense#summarizeMedicationDispenses**；Delivery 与扣库由 **AS-IP-PHA-002** 药房核发编排。
- **状态变化**：`statusEnum` 初始化为 **`PENDING_APPROVAL`**（对齐库房单据保存草稿）；`apply_time` **不写**（由 **6.1.12 submitApproval** 在护士站发送时写入）。
- **适用单据类型（`type_enum`）**：**仅 `27`**（`MEDICATION_SUMMARY_ORDER`）；禁止与其它类型混单。
- **聚合内影响**：批量插入行；写入 `type_enum=27`、`item_table`（`MED_MEDICATION_DEFINITION`）、`item_id / item_quantity / unit_code / purpose_location_id / purpose_type_enum / lot_number / patient_id`（若按患者汇总）/ `summary_id / applicant_id / apply_time / category_enum`。
- **位置语义（type 27 · 单端点 purpose 真源）**：**仅**写 **`purpose_location_id` / `purpose_type_enum`**（发放药房 / 汇总目标库房）；`source_*` **NULL**；与 **AS-IP-NUR-010** 汇总 Tab 按 `purpose_location_id` 筛选一致。
- **关键约束**：`BR-SR-001`；重复汇总策略校验；禁止混入非药品目录项；**禁止**跨发放药房合并为同一 `bus_no`（合并键见 **AS-IP-NUR-010 BR-IP-SUM-005/006**）。
- **事件输出**：`SupplyRequestCreatedEvent`。
- **失败语义**：参数非法、重复创建、混入非药品项时拒绝。

#### 6.1.11 创建科室耗材盘点供应请求（`createDeviceSummarySupplyRequest`）

- **业务意义**：创建 **科室耗材盘点单**（值 `28`），物品维度以耗材为主；与「科室盘点单」（值 `24`）在 `type_enum` 上严格区分，避免库存归因与报表口径混淆。方法名为与存量实现对齐的**保留名**；语义以本文 **科室耗材盘点单** 为准。
- **状态变化**：`statusEnum` 典型初始化为 `PENDING_APPROVAL`，再走 **6.1.13**～**6.1.15** 审批链。
- **适用单据类型（`type_enum`）**：**仅 `28`**（`DEPT_DEVICE_STOCKTAKE`）。
- **聚合内影响**：插入多行；写入 `type_enum=28`、`item_table`（耗材定义来源）、`item_id / item_quantity / unit_code / purpose_location_id / purpose_type_enum / occurrence_time / category_enum / applicant_id / apply_time`；`source_*` **NULL**。
- **关键约束**：`BR-SR-001`；科室位置合法；同一科室同一盘点周期防重策略（本稿 **§6.1**）；禁止类型混用。
- **事件输出**：`SupplyRequestCreatedEvent`。
- **失败语义**：类型混用、空明细、科室无权限时拒绝。

#### 6.1.12 创建科室领用单（`createDeptIssue`）

- **业务意义**：**免申请**，科室药房手工创建领用执行单；库存仅在 **`purpose_location_id`（科室药房）** 扣减；**领用科室**为行政 Organization，落库 **`consuming_organization_id`**（客户端手选，与科室药房解耦）；**不**关联请领/调拨/临床 **DeviceDispense**（**AS-INV-DEP-004**）。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**仅 `29`**（`DEPT_ISSUE`）。
- **聚合内影响**：写入 `item_id / item_table / item_quantity`（正数领用量）/ `unit_code / purpose_location_id / purpose_type_enum / consuming_organization_id / lot_number / trace_no`（可选）/ `price / total_price / reason / category_enum / applicant_id / practitioner_id / apply_time`；`source_*` **NULL**；**禁止** `original_bus_no`。
- **位置语义**：单端点 **purpose** 真源（科室药房 Location）；**领用科室**独立列 **`consuming_organization_id`**，**禁止**往 `purpose_*` 存 orgId。
- **关键约束**：`BR-SR-001`；`item_quantity` **> 0**；**`consuming_organization_id` 非空**（应用层 Gate）；类型须为 `DEPT_ISSUE`。
- **审批通过后（跨域，应用层编排）**：`createSupplyDelivery`（出库发放）+ **仅** `purpose_location_id` 侧 `InventoryItem` **扣减**；**禁止**目的库增加、**禁止**调入壳单。
- **修订路径**：首行传入既有 `bus_no` 时同号全量替换；门控 `assertDeptIssueLinesRevisable`；**不**发布 `SupplyRequestCreatedEvent`。
- **事件输出**：新建路径 `SupplyRequestCreatedEvent`。
- **失败语义**：明细为空、数量非法、类型非 29、修订状态不允许时拒绝。

#### 6.1.13 创建科室退领单（`createDeptIssueReturn`）

- **业务意义**：自已同意 **科室领用单（`DEPT_ISSUE`）** 未用完部分退回科室药房；库存仅在 **`purpose_location_id`（与源领用相同科室药房）** 加回；**不**生成调入壳单（**AS-INV-DEP-009**）。
- **状态变化**：`statusEnum` 初始化为 `PENDING_APPROVAL`。
- **适用单据类型（`type_enum`）**：**仅 `30`**（`DEPT_ISSUE_RETURN`）。
- **聚合内影响**：写入 `original_bus_no`（源领用 `bus_no`）/ `item_id / item_table / item_quantity`（正数退回量）/ `unit_code / purpose_location_id / purpose_type_enum / consuming_organization_id`（自源领用单继承）/ `lot_number / trace_no`（可选）/ `price / total_price / reason / category_enum / applicant_id / practitioner_id / apply_time`；`source_*` **NULL**。
- **位置语义**：单端点 **purpose** 真源；**领用科室**自源领用单 **`consuming_organization_id`** 继承落库；可退余量、源行追溯码一致性由应用层门控。
- **关键约束**：`BR-SR-001`；`item_quantity` **> 0**；`original_bus_no` **必填**；类型须为 `DEPT_ISSUE_RETURN`。
- **审批通过后（跨域，应用层编排）**：`createSupplyDelivery`（入库发放）+ **仅** `purpose_location_id` 侧 `InventoryItem` **增加**；**禁止**壳单。
- **修订路径**：首行传入既有 `bus_no` 时同号全量替换；门控 `assertDeptIssueReturnLinesRevisable`；**不**发布 `SupplyRequestCreatedEvent`。
- **事件输出**：新建路径 `SupplyRequestCreatedEvent`。
- **失败语义**：明细为空、数量非法、`original_bus_no` 缺失、类型非 30、修订状态不允许时拒绝。

---

**六、状态流转动作**

#### 6.1.14 提交审批（`submitApproval`）

- **业务意义**：将同一 `bus_no` 下各明细从待审核推进到审核中，写入申请时间等追溯字段（将申请单和执行单据均适用）。
- **状态变化**：`PENDING_APPROVAL` / `WITHDRAW` → `APPROVAL`。
- **适用单据类型（`type_enum`）**：`1`～`26`、`28`、`29`、`30` 及 **type 27 药品汇总单**（护士站 **AS-IP-NUR-010** 保存后发送路径）。
- **入参**：`bus_no`；可选业务时钟 `applyClock`（与 本稿 **§6.1** 一致）。
- **出参**：`boolean`（或统一 Result，以落库与交互契约为准）。
- **聚合内影响**：按 `bus_no` 批量更新 `status_enum`、`apply_time`。
- **关键约束**：**§2.3**；**BR-SR-002**。
- **事件输出**：`SupplyRequestSubmittedEvent`。
- **失败语义**：非法状态、空单据、类型不在适用集合时拒绝。

#### 6.1.15 撤回审批（`withdrawApproval`）

- **业务意义**：审批完成前撤回，不自动删除下游 **SupplyDelivery**；已生成发放走纠错用例。
- **状态变化**：`APPROVAL` → `WITHDRAW`（及允许集合内的撤回）。
- **适用单据类型（`type_enum`）**：`1`～`26`、`28`、`29`、`30`；`27` 若处于 `APPROVAL` 等可撤回态则适用（本稿 **§6.1**）。
- **入参**：`bus_no`。
- **出参**：`boolean`。
- **聚合内影响**：按 `bus_no` 批量更新 `status_enum`。
- **事件输出**：`SupplyRequestWithdrawnEvent`。
- **失败语义**：终态不可撤回时拒绝。

#### 6.1.16 审批同意（`agreeRequest`）

- **业务意义**：将同一 `bus_no` 的记录从审核中推进到同意；本方法**不直接**写 SupplyDelivery 或 InventoryItem，而是作为触发下游编排的状态门控。审批对象分两类（**PR-SR-002**）：
  - **审批申请单**：`AGREE` 后，**领域层不自动**生成执行单；订货/请领等由**下游应用服务**在用户操作时再 `createInboundReceipt` / `createStockOut`（新 `bus_no`，`originalBusNo` 可指向申请单）；
  - **审批执行单据**：`AGREE` 后，应用层编排生成 SupplyDelivery + 触发 InventoryItem 变动（终态审批）。
- **状态变化**：`APPROVAL` → `AGREE`。
- **适用单据类型（`type_enum`）**：`1`～`26`、`28`、`29`、`30`；`27` 若存在「先审后发」变体则适用。
- **入参**：`bus_no`、`approvalClock`（`LocalDateTime`，写入 `approval_time`）、审批人标识（`approverId`，写入 `approver_id`）。
- **出参**：更新后列表或 `boolean`（以落库与交互契约为准）。
- **聚合内影响**：按 `bus_no` 批量更新 `status_enum=AGREE`；写 `approver_id`、`approval_time`（**BR-SR-005**）。
- **关键约束**：`status_enum = APPROVAL`；**BR-SR-005**；**BR-SR-013**（财务审批族 type 须持有 `ROLE_ACCOUNTANT`，见 `SupplyType#requiresFinanceApproval`）；读侧 `supplyRequestValidation`（**§6.2.6**）防重复同意。
- **事件输出**：`SupplyRequestAgreedEvent`。
- **失败语义**：非审核中、重复同意、财务族无财务角色时拒绝。

> **跨域后续（均属应用层职责，不在本聚合内实现）**：
>
> **A. 审批的是申请单 → 生成执行单据（BR-SR-011）**
>
>
> | 申请单类型                  | 生成的执行单据                        | 说明                        |
> | ---------------------- | ------------------------------ | ------------------------- |
> | 订货单（type 1）            | 进货单（type 2）                    | 药库人员补充批号、发票号等后走审批         |
> | 请领单（type 12/20）        | **无**自动建单；用户在 **AS-INV-WHS-003** 建出库单（type 4，`originalBusNo` 可指向请领） | 请领 agree 仅终态（**PHA-QL-009** / **`department/requisition`**）；出库 agree 落账并生成 type 13/21 壳单 |
> | 退库主动单（type 14/22/3） | 审批即终态：发放+库存；药房/科室退库另生成 type 5 壳单 | 见 **6.1.5** / **6.1.3** |
> | 退库入库壳单（type 5） | **无**二次 agree；只读台账 | 库存已在退库 agree 完成 |
>
>
> **B. 审批的是执行单据 → 生成 SupplyDelivery + InventoryItem（BR-SR-012）**
>
>
> | 执行单类型                      | 下游编排动作                                                                       |
> | -------------------------- | ---------------------------------------------------------------------------- |
> | 进货/入库（type 2/11/13/21）     | SupplyDelivery（入库发放）+ InventoryItem 增加（科室虚拟库存除外 **BR-SR-018**）               |
> | 出库单（type 4）                | SupplyDelivery（出库发放）+ InventoryItem 扣减（源库房）                                  |
> | 退库主动单（type 14/22/3）      | SupplyDelivery（退库发放）+ 上级库增、退库方减（虚拟库存除外 **BR-SR-018**）；type 14 agree 后 **`createReturn`** 生成 type 5 壳单 |
> | 退库入库壳单（type 5，系统生成） | **无**下游发放/库存（已在药房/科室退库 agree 落账）；应用层禁止手工 agree（**`WHS_RSI_006`**） |
> | 调拨**调出**执行单（type 9/18/26 等） | `createTransferDelivery` + InventoryItem 源减目增；并 **`createTransfer`** 生成对侧调入壳单（type 8/17/25，`original_bus_no`=调出单号，初态 `AGREE`） |
> | 调拨**调入**壳单（type 8/17/25，系统生成） | **无**下游发放/库存（已在调出 agree 落账）；应用层禁止手工 agree（**WHS_TI_002** / **PHA_TI_002** / **DEPT_TI_002**） |
> | 损益单（type 6/15/23）          | SupplyDelivery（损溢发放）+ InventoryItem 增（报溢）或减（报损）                              |
> | 盘点单（type 7/16/24）          | 对有差异须落账明细调用 `createStocktakingDelivery`（盘盈+/盘亏-）+ InventoryItem 调整；无差异行不产生发放 |
> | 科室耗材盘点（type 28）            | 与盘点单相同逻辑，物品维度为耗材目录                                                           |
| 科室领用（type 29）                | SupplyDelivery（出库发放）+ **仅** `purpose_location_id` 侧 InventoryItem **扣减**；**禁止**调入壳单与临床 DeviceDispense |
| 科室退领（type 30）                | SupplyDelivery（入库发放）+ **仅** `purpose_location_id` 侧 InventoryItem **增加**；**禁止**壳单；源单校验在应用层 |
>

#### 6.1.17 审批驳回（`rejectRequest`）

- **业务意义**：驳回，不触发发放。
- **状态变化**：`APPROVAL` → `REJECT`。
- **适用单据类型（`type_enum`）**：`1`～`30` 中处于 `APPROVAL` 者。
- **入参**：`bus_no`；可选驳回原因。
- **出参**：`boolean`。
- **聚合内影响**：按 `bus_no` 批量更新 `status_enum`；写 `approver_id`、`approval_time`（本稿 **§6.1**）。
- **关键约束**：`status_enum = APPROVAL`；**BR-SR-005**；**BR-SR-013**（财务审批族须财务角色，同同意）。
- **事件输出**：`SupplyRequestRejectedEvent`。
- **失败语义**：状态不允许、财务族无财务角色时拒绝。

---

**七、其他写侧动作**

#### 6.1.18 删除单据（逻辑）（`removeByBusNo`）

- **业务意义**：按 `bus_no` 将全部明细行标记逻辑删除。
- **适用单据类型（`type_enum`）**：`1`～`30`（含 type **27** 药品汇总单）。
- **入参**：`bus_no`、操作人标识。
- **出参**：`boolean`。
- **聚合内影响**：同号全部行 `is_deleted=true`。
- **关键约束**：**BR-SR-011**（同号全部明细行 `status_enum` 须为 **`PENDING_APPROVAL`** / **`REJECT`** / **`WITHDRAW`**，与 **`SupplyStatus#getCode()`** 一致）；**BR-SR-010**（禁止删除已产生下游发放/库存的单据，应用层跨域校验）。
- **事件输出**：`SupplyRequestDeletedEvent`。
- **失败语义**：状态不允许、下游已引用时拒绝。
- **应用编排（type 27 · 护士撤销待发送）**：**AS-IP-NUR-010** IF-IP-SUM-08 在应用层**先**调用 **DM-MED-002** `cancelMedicationDispenseSummary` 释放发放行，**再**本方法（`bus_no = summaryNo`）；门控须 **PENDING_APPROVAL**；**不**与 **§6.1.17** `cancelSummarySupplyRequest` 混用。

#### 6.1.19 取消汇总单（`cancelSummarySupplyRequest`）

- **业务意义**：按 **`summary_id`** 列表批量逻辑删除 type **27** 汇总明细行，发布 `SummarySupplyRequestCancelledEvent`。
- **边界说明**：按 `findBySummaryIdIn` 定位行；**不**按 `bus_no` 整单删除。**护士站撤销待发送汇总（AS-IP-NUR-010 IF-IP-SUM-08）走 §6.1.16 `removeByBusNo`**，不走本方法。
- **适用单据类型（`type_enum`）**：**仅 `27`**（`MEDICATION_SUMMARY_ORDER`）。
- **入参**：`summaryIdList`、操作人标识。
- **出参**：`List<Long>`（受影响行主键）。
- **聚合内影响**：命中行逻辑删除。
- **关键约束**：type 非 27 行跳过；状态门控以实现为准（应用层调用前校验）。
- **事件输出**：`SummarySupplyRequestCancelledEvent`（载荷含 `summaryIdList`）。
- **失败语义**：参数非法、批次规则违反时拒绝。

#### 6.1.20 创建库存期初单（`createOpening`）

- **业务意义**：药库/药房/科室药房**上线开账**草稿（**AS-BAS-INV-001**）；**BR-SR-012 执行单据**；空行草稿允许；审批同意后由应用编排 Delivery + InventoryItem + 追溯真覆盖。
- **适用单据类型**：`CABINET_OPENING` / `PHARMACY_OPENING` / `DEPT_OPENING`（code `COP`/`POP`/`DOP`）。
- **入参**：`CreateOpeningCommand`（`purposeLocationId`、类型、明细行含批号/效期/数量/进价/售价/追溯码；修订时带既有 `bus_no` + `expectedRowVersionForRevision`）。
- **初态**：`PENDING_APPROVAL`；**单端点**仅写 `purpose_*`。
- **关键约束**：行内 `(itemTable,itemId,lotNumber)` 唯一；药库进价硬必填由应用闸门；**不**绑供应商/订货 `original_bus_no`。
- **事件**：新建发 `SupplyRequestCreatedEvent`；修订同号全量替换口径对齐损益。

#### 6.1.21 作废已同意期初（状态）（`voidOpening`）

- **业务意义**：将同号期初行置 `EXPIRED_INVALIDATED`（**仅状态**）；库存/发放/追溯回滚由应用 **`InventoryOpeningVoidOrchestrator`** 编排（B.6），**禁止**领域内联跨聚合冲销。
- **前置**：同号须为 OPENING 类型且当前 `AGREE`。
- **事件**：以实现为准（可复用失效类事件或应用审计）。

### 6.2 读侧查询服务（SupplyRequestQueryService）

> 查询以 `bus_no` / `id` / `original_bus_no` / `summary_id` 为键；返回 `type_enum` 期望在 1～28。

#### 6.2.1 按单据号查询（`getSupplyByBusNo`）

- **业务意义**：按 `bus_no` 加载全部明细。
- **边界说明**：只读，不写库，不做跨域编排。
- **适用单据类型（`type_enum`）**：1～28。
- **查询范围（读库，无写入）**：`wkf_supply_request` 按 `bus_no`。
- **过滤口径**：排除逻辑删除；租户隔离以落库与交互契约为准。
- **组装规则**：按主键或行号排序。
- **返回约束**：无则空列表。

#### 6.2.2 按单据号批量查询（`getSupplyByBusNoBatch`）

- **业务意义**：批量 `bus_no`。
- **边界说明**：只读。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：`bus_no IN`。
- **过滤口径**：同 6.2.1。
- **组装规则**：分组或平铺以落库与交互契约为准。
- **返回约束**：入参长度上限以落库与交互契约为准。

#### 6.2.3 按原始单据号查询（`getSupplyByOriginalBusNo`）

- **业务意义**：按 `original_bus_no` 追溯。
- **边界说明**：只读。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：`original_bus_no`。
- **过滤口径**：同 6.2.1。
- **组装规则**：列表。
- **返回约束**：多单命中策略以落库与交互契约为准。

#### 6.2.4 按 ID 列表查询（`selectByIdList`）

- **业务意义**：主键 IN。
- **边界说明**：只读。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：主键 IN。
- **过滤口径**：排除逻辑删除。
- **组装规则**：顺序以落库与交互契约为准。
- **返回约束**：顺序不保证与入参一致除非显式排序。

#### 6.2.5 获取单行（`getById`）

- **业务意义**：主键单行。
- **边界说明**：只读。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：主键单行。
- **过滤口径**：排除逻辑删除。
- **组装规则**：实体或 DTO。
- **返回约束**：不存在时空或异常以落库与交互契约为准。

#### 6.2.6 查询已同意状态（`supplyRequestValidation`）

- **业务意义**：是否存在 `AGREE` 行，防重复同意。
- **边界说明**：只读断言。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：`bus_no` + 状态子集。
- **过滤口径**：任一行 `AGREE` 即真。
- **组装规则**：`boolean` 或原因码。
- **返回约束**：写侧 **6.1.14** 前应调用。

#### 6.2.7 校验物品可编辑性（`verifyAbleEdit`）

- **业务意义**：是否允许改价/改量。
- **边界说明**：只读；**BR-SR-014**。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：`bus_no` 与物品键。
- **过滤口径**：`APPROVAL`/`AGREE` 等禁止编辑态。
- **组装规则**：布尔或原因码。
- **返回约束**：不通过则拦截写路径。

#### 6.2.8 获取物品所属表（`getItemTable`）

- **业务意义**：取 `item_table`。
- **边界说明**：只读。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：首行或指定行策略以落库与交互契约为准。
- **过滤口径**：排除删除。
- **组装规则**：字符串。
- **返回约束**：无则空或错误码。

#### 6.2.9 获取物品 ID 列表（`getItem`）

- **业务意义**：取 `item_id` 列表。
- **边界说明**：只读。
- **适用单据类型（`type_enum`）**：同 6.2.1。
- **查询范围（读库，无写入）**：`bus_no`。
- **过滤口径**：去重以落库与交互契约为准。
- **组装规则**：`List<Long>`。
- **返回约束**：空则空列表。

#### 6.2.10 药品汇总单头分页（`pageMedicationSummarySlips`）

- **业务意义**：**AS-IP-NUR-010 · IF-IP-SUM-02/06** 汇总 Tab 分页查询已生成 **type 27** 汇总单头。
- **边界说明**：只读；复用 `countDistinctBusNo` / `listDistinctBusNoPage` 按 `bus_no` 去重分页；关联 **SupplyDelivery** 只读组装领药人、发放态与发药时间；**不**替代通用单据列表三件套，入参为 `SupplyRequestDocumentListQuery`（须收窄 `type_enum=27`）。
- **适用单据类型（`type_enum`）**：**仅 `27`**（`MEDICATION_SUMMARY_ORDER`）。
- **查询范围（读库，无写入）**：`wkf_supply_request` + 只读协作 `wkf_supply_delivery`。
- **过滤口径**：同通用 `bus_no` 聚合列表（位置、申请时段、关键字等）；排除逻辑删除。
- **组装规则**：`MedicationSummarySlipQueryModel`（`busNo`、申请/发药时间、药房、申请人、领药人、`deliveryStatusEnum`）；发药时间取自 SupplyDelivery.`receive_time`（已核发态）。
- **返回约束**：`Page<MedicationSummarySlipQueryModel>`；无数据时空页。

#### 6.2.11 药品汇总单合并明细（`listMedicationSummarySlipLinesByBusNo`）

- **业务意义**：**AS-IP-NUR-010 · IF-IP-SUM-03/06** 按 `summaryNo`/`busNo` 加载合并后 Supply 明细行及只读价格。
- **边界说明**：只读；以 **SupplyRequest** 合并行为准；只读协作 **MedicationDispense**（`summary_no`）与 **ChargeItem** 按 **itemId + unit_price** 分组累加（**BR-IP-SUM-009**）；**不返回** `lot_number`（**BR-IP-SUM-010**）；药品名称/规格由应用层读 **MedicationDefinition** 装配。
- **适用单据类型（`type_enum`）**：**仅 `27`**。
- **查询范围（读库，无写入）**：`wkf_supply_request` 按 `bus_no`；只读 **med_medication_dispense**、**adm_charge_item**。
- **过滤口径**：排除逻辑删除；非 type 27 行忽略。
- **组装规则**：`MedicationSummarySlipLineQueryModel`（拆零 `itemQuantity`/`unitCode`、`unitPrice`、`totalPrice`）；`total_price` = 组内 ChargeItem.`total_price` 之和，禁止单价×数量重算。
- **返回约束**：单号无效或无 type 27 行时空列表。

#### 6.2.12 库房日结期间供应行批量读（`listAgreedLinesByWarehouseAndApprovalWindow`）

- **业务意义**：**AS-INV-RPT-001 · IF-INV-SET-02** 按选中库房与审批自然日窗，批量拉取 **已同意（`AGREE`）且已落库存** 的供应明细行，供应用层按 `item_id` 汇总期间动账数量与冻结金额。
- **边界说明**：只读；**不**新建跨域读包；入参/出参 Query 类型归属本聚合 `queries` 包（如 `SupplyRequestSettlementWindowQuery` / `SupplyRequestSettlementLineQueryModel`）；**不**替代 `getSupplyByBusNo` 单据详情读。
- **查询范围（读库，无写入）**：`wkf_supply_request` 主表及日结所需行字段（`item_id`、`item_quantity`、`total_price`、`profit_loss_price`、`type_enum`、`status_enum`、`source_location_id`、`purpose_location_id`、`approval_time`、`item_table` 等）。
- **过滤口径**：
  - `status_enum = AGREE`；
  - `approval_time` 落在入参 **左开右闭** 自然日窗（与 **BR-INV-SET-004** 一致）；
  - `item_table` = 入参 Tab（药品/耗材）；
  - `type_enum IN` 入参白名单（由应用层按 **Location.formEnum** 与 **BR-INV-SET-007/010** 组装，**排除**壳单/申请单）；
  - 库房命中：`source_location_id = 库房` **或** `purpose_location_id = 库房`（应用层再按 **BR-INV-SET-008** 判 ±）。
- **组装规则**：返回明细行列表（含源/目的库位、类型、数量、冻结价字段）；**不在本方法内**做品种汇总或方向换算。
- **实现锚点**：`**SupplyRequestQueryService#listAgreedLinesByWarehouseAndApprovalWindow`**；动态 WHERE 见 **`SupplyRequestSettlementWindowPredicatesupport**；应用消费 **`InventoryWarehouseDailySettlementAppService#loadAssemblyContext`** → **`DailySettlementAssemblySupport#accumulateSupplyLines`**（**AS-INV-RPT-001**）。
- **返回约束**：允许空列表；大批量时须可索引（`approval_time` + 库位 + `item_table`）；禁止全表扫后内存过滤作为唯一实现。

#### 6.2.13 库房单据明细分页（`pageWarehouseDocumentDetail`）

- **业务意义**：**AS-INV-RPT-002 · IF-INV-DDC-{WHS\|PHA\|DEP}-02～04** 及既有进销存单详情/关联选品等场景的 **供应明细行分页**真源（查 `wkf_supply_request` **一行一条**，非 `bus_no` 聚合）。
- **边界说明**：只读；入参 **`WarehouseDocumentDetailPageQuery`**、出参 **`WarehouseDocumentDetailQueryModel`** 归属本聚合 `queries` 包；**不**替代 `getSupplyByBusNo` 单号详情读。
- **查询范围（读库，无写入）**：`wkf_supply_request` 明细行字段（含 `item_quantity`、`total_quantity`、`profit_loss_price`、`approval_time`、`item_table`、`item_id`、`lot_number` 等）。
- **过滤口径**：
  - 软删排除；
  - 可选 `type_enum` / `status_enum`、源/目的库位、`item_id` 精确；
  - **`approval_time`** 闭区间（`approvalTimeStart`/`approvalTimeEnd`）；
  - **`lotNumberKeyword`** → `lot_number` 不区分大小写 LIKE（优先于精确 `lotNumber`）；
  - 品名/编码关键字：应用层 **`InventoryCatalogFilterSupport#enrichDocumentDetailKeywordDefinitionIds`** 写入 `resolvedMedicationDefinitionIdsForKeyword` / `resolvedDeviceDefinitionIdsForKeyword` 后，Predicate 按 **`item_table`+`item_id IN`** 过滤（双列表均为空 → 无命中）；
  - 通用 `keyword`（继承 `AbstractPageQuery`）仍匹配 `bus_no`/`reason`/`remake`。
- **组装规则**：`WarehouseDocumentDetailQueryModel` 映射行级持久化字段；**盘点盘前拆零账面**由应用层按 **`total_quantity − item_quantity`** 推导（**禁止**读现库存 enrich 作为报表口径）。
- **实现锚点**：**`SupplyRequestQueryService#pageWarehouseDocumentDetail`**；动态 WHERE 见 **`WarehouseDocumentDetailPagePredicatesupport**。
- **返回约束**：`Page<WarehouseDocumentDetailQueryModel>`；无数据时空页。

#### 6.2.14 财务审批·供应侧单据列表（复用三件套 + 扩展过滤）

- **业务意义**：**AS-FIN-APR-001 · IF-APR-02** 财务审批统一列表的**供应侧**读契约；按 `bus_no` 聚合一行一单。
- **边界说明**：只读；**仅本聚合** `SupplyRequestQueryService`；**禁止**在本 QS 内联查调价表（B.6.4）。与调价侧并集分页由应用层 **`application.finance.approval.support.FinanceApprovalDocumentPageLoader`**（目标态）分步调本方法 + `PriceAdjustmentQueryService` 列表三件套后内存/游标归并。
- **接口签名（复用）**：
  - `countDistinctBusNo(SupplyRequestDocumentListQuery)`
  - `listDistinctBusNoPage(SupplyRequestDocumentListQuery)`
  - `listByBusNosWithFilter(SupplyRequestDocumentListQuery, List<String> busNos)`
- **财务族 type 收窄**：未指定单一 `typeEnum` 时，应用层须将 `typeEnums` 设为 `SupplyType#listFinanceApprovalTypeCodes()`（进货、退供应商、三线损益、四类盘点）；**不得**默认写死仅进货。
- **扩展过滤（`SupplyRequestDocumentListQuery`）**：`busNoLike`（单号模糊）、`applicantId`、`approverId`、`itemTable`（药品/耗材表名）、`occurrenceTimeFrom/To`（制单）、`approvalTimeFrom/To`（审批）；库房继续用既有 `sourceLocationId(s)` / `purposeLocationId(s)`；`statusEnum` 待审为 **`APPROVAL`**。
- **实现锚点**：谓词 **`SupplyRequestDocumentListPredicateSupport#collectPredicates`**；白名单 **`SupplyType#requiresFinanceApproval` / `#listFinanceApprovalTypeCodes`**。
- **返回约束**：与通用单据列表三件套相同；金额汇总由应用层按行装配（不属本 QS）。

#### 6.2.15 科室领用/退领报表明细与净汇总（`pageDeptIssueReportLines` / `listDeptIssueReportLines` / `summarizeDeptIssueReport`）

- **业务意义**：**AS-RPT-003～006** 科室侧：已同意 **`DEPT_ISSUE` / `DEPT_ISSUE_RETURN`** 明细分页与按科室/药房/品规净数量汇总；按 **`item_table`** 拆药品 vs 耗材。
- **边界说明**：只读；**仅本聚合**；门诊/住院域库位由应用层预写 `resolvedPurposeLocationIds`；科室维汇总优先 **`consuming_organization_id`**，列空时回落 `organizationIdByPurposeLocationId`（`Location.organization_id`）。
- **入参**：`DeptIssueReportPageQuery`（`itemTable` 必填）。
- **组装规则**：`DeptIssueReportLineQueryModel` / `DeptIssueReportSummaryRow`；退领数量取负；明细行含 **`approverId`**（`approver_id`，供耗材使用报表回填执行人/发耗材人）。
- **实现锚点**：`SupplyRequestQueryService#pageDeptIssueReportLines` / `#listDeptIssueReportLines` / `#summarizeDeptIssueReport`。
- **返回约束**：`itemTable` 空或预解析空列表 → 空页/空列表。

#### 6.2.16 期初业务封锁与当前有效单（`existsNonOpening*` / `existsOpeningInApproval*` / `findCurrentAgreedOpeningBusNo`）

- **`existsNonOpeningBusinessForLocation`**：该库房是否存在非 OPENING 且状态为 `APPROVAL`/`AGREE` 的供应行（封锁期初写路径）。
- **`existsOpeningInApprovalForLocation`**：该库房是否存在 OPENING 且 `APPROVAL`（关闭前须清）。
- **`findCurrentAgreedOpeningBusNo`**：该库房当前 `AGREE` 的 OPENING `bus_no`（列表 `currentEffective` 对照；同意覆盖时作废目标）。
- **`existsDispenseBusinessForLocation`**：该库房是否存在发药 COMPLETED/PART_COMPLETED/REFUNDED/PENDING_REFUND（封锁窄口径，DEPT_ISSUE 走供应侧）。

### 6.3 领域边界说明

- **聚合内**：按 **bus_no** 批量一致的状态迁移、**original_bus_no** 随建单/编排写入（见 **§6.1** 总述）、**仅逻辑删除**、汇总撤销（**值 27** 专用）。
- **跨域**：`**AGREE`** / `**SEND`** 下游由应用编排 **SupplyDelivery**、**InventoryItem**、**MedicationDispense**；本聚合不直接写上述主表。  
- **非本聚合**：**药房发药单**、**科室发出单**（领用发放）、中心物资独立线（本版无 `**type_enum`**）、月结/警戒/销毁/养护、调价共表遗留（**§9.3**）。  
- **类型完备**：菜单增删须同步 **§4.1**；**禁止** **PATIENT_MATERIAL_DISPATCH** 及已删除的整线物资枚举重新混入白名单而不走变更流程。

---

## 7. 不变式


| 编号        | 不变式                                                                                 | 说明                         |
| --------- | ----------------------------------------------------------------------------------- | -------------------------- |
| BR-SR-001 | 创建必须具备 `busNo`、`typeEnum`、`itemId`、`itemQuantity`，且 `typeEnum` ∈ **§4.1**（`1`～`31`） | 最小建单 + 白名单                 |
| BR-SR-002 | 同一 `busNo` 状态一致                                                                     | 批量更新                       |
| BR-SR-003 | 仅可编辑态允许修改：`PENDING_APPROVAL` / `REJECT` / `WITHDRAW`（`SupplyRequest#isRevisableSupplyStatusCode`） | `AGREE` 等动账终态禁止；壳单另有恒拒绝门控 |
| BR-SR-004 | 提交审批写入申请时间                                                                          | **6.1.12**                 |
| BR-SR-005 | 同意/驳回写入审批信息                                                                         | **6.1.16** / **6.1.17**    |
| BR-SR-013 | 财务审批族 `type_enum`（`SupplyType#requiresFinanceApproval`：进货、退供应商、损益、盘点）的 agree/reject 须持有 `ROLE_ACCOUNTANT`；非财务族不校此门控；调价不经本规则 | **6.1.16** / **6.1.17**；**AS-FIN-APR-001** |
| BR-SR-008 | 退库/退货类 `original_bus_no` 必填；所指原单须已 `AGREE` 且位置/类型与业务方向一致（药库退货：`original_bus_no` → 已同意 **`CABINET_INBOUND_RECEIPT`**，源药库 = 进货 `purpose_location_id`） | **6.1.3** / **6.1.5** |
| BR-SR-009 | 退库/退货按 **`item_table + item_id + normalize(批号)`** 行键：本次拆零量 ≤ 可退余量；可退余量 = 原单行量 − Σ 同键已占用量（同 `original_bus_no` 退单且状态 ∈ {待审/审中/已同意}；**驳回/撤回不计**；修订/同意复检排除本 `bus_no`）；**药库退货**同意前还须源库 **`InventoryItem`** 可用量 ≥ 本次量（应用层 **AS-INV-WHS-005**） | **6.1.3** / **6.1.5** |
| BR-SR-010 | 删除防下游引用                                                                             | **6.1.16** `removeByBusNo` |
| BR-SR-011 | 逻辑删除仅允许 `PENDING_APPROVAL` / `REJECT` / `WITHDRAW`（`SupplyStatus` 持久化 `code`）                    | **6.1.16** `removeByBusNo` |
| BR-SR-014 | 已发生活动物品不可编辑删除                                                                       | **verifyAbleEdit**         |
| BR-SR-016 | **`CABINET_PURCHASE_ORDER`（药库订货）** 只能由药库发起；`purpose_type_enum` 须为药库；**不含**科室 **`DEPT_PURCHASE_REQUEST`** | **6.1.1** |
| BR-SR-019 | **药库退货（type 3）** 落库前须经 **`SupplyRequestWritePricingSupport#applyReturnToSupplierCommand`**：优先同源进货行落库包装单价（**BR-PRICE-SRC-001**；应用层按行键回填），缺省批号/目录进价；按拆零数量重算 `total_price`（**BR-MON-001** / **BR-INV-DTL-007**）；**禁止**信任前端传价；实体须持久化 `price`/`total_price` | **6.1.3** |
| BR-SR-020 | **请领/批号级建单（type 12/20 等）** 须持久化行 **`end_time`**（有效期/失效日期，自选品效期写入）；可选 **`start_time`**（生产日期）；供详情回显并与 **InventoryItem** 批号 **`expiration`** 对齐 | **6.1.9** |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文   | 聚合/服务                                   | 关联方式                                               | 说明            |
| ------- | --------------------------------------- | -------------------------------------------------- | ------------- |
| 位置管理    | Location                                | `source_location_id` / `purpose_location_id`       | 源/目标位置        |
| 供应商管理   | Supplier                                | `supplier_id`                                      | 采购/退货         |
| 人员管理    | Practitioner                            | `practitioner_id` / `applicant_id` / `approver_id` | 人员锚点          |
| 患者管理    | Patient                                 | `patient_id`                                       | **药品汇总单** 等   |
| 药品/耗材目录 | MedicationDefinition / DeviceDefinition | `item_table` + `item_id`                           | 含 **28** 耗材盘点 |


### 8.2 下游影响


| 下游上下文 | 聚合                 | 触发方式  | 说明                                |
| ----- | ------------------ | ----- | --------------------------------- |
| 供应发放  | SupplyDelivery     | 应用层编排 | 与 **AGREE**、**27** 的 **SEND** 链衔接 |
| 库存管理  | InventoryItem      | 应用层编排 | 含盘点差异                             |
| 药品发放  | MedicationDispense | 应用层编排 | **发药事实**；**非** **§4.1** 发药单类型     |
| 耗材发放  | DeviceDispense     | 应用层编排 | **科室发出**（领用发放）                    |


### 8.3 引用规则

> **统一规则**：跨上下文只传 `**SupplyRequestId`**（`Long`）或 `**busNo`**（`String`）；禁止旁路直连表。

### 8.4 事件流示意图

```text
常规/盘点（1～26、28）:
  CreateEvent → UpdateEvent(提交) → UpdateEvent(同意) → 编排 SupplyDelivery + InventoryItem

药品汇总（27）:
  CreateEvent(SEND) → UpdateEvent(取消/异常) 或 下游发药链事件（邻域）

科室耗材盘点（28）:
  CreateEvent → UpdateEvent(审批…) → AGREE → 编排库存盘点差异
```

---

## 9. 补充说明

### 9.1 两阶段审批与申请单/执行单编排

1. **常规/科室耗材盘点/科室领用/退领（`1`～`26`、`28`、`29`、`30`）**：`submitApproval` → `agreeRequest` / `rejectRequest`；通过后应用层可再生成执行单（新 `bus_no`），`type_enum` 仍为 **§4.1** 中对应执行类取值（**`29`/`30` 无下游执行单，同意即动账**）。
2. **药品汇总（`27`）**：以 **createSummarySupplyRequest**（**§6.1.10**）切入，初态 **PENDING_APPROVAL**；护士站 **submitApproval** → **APPROVAL**（**AS-IP-NUR-010** IF-IP-SUM-05）；**待发送撤销**：**DM-MED-002** `cancelMedicationDispenseSummary` + **§6.1.17** `removeByBusNo`（**AS-IP-NUR-010** IF-IP-SUM-08，仅 **PENDING_APPROVAL**）；药房 **agreeRequest** → **AGREE**（**AS-IP-PHA-002**）；药房 **rejectRequest** + **DM-MED-002** 取消汇总（**AS-IP-PHA-002** IF-IPS-04，Supply 留 **REJECT**）；按 `summary_id` 批量删走 **§6.1.18** `cancelSummarySupplyRequest`（**非**护士站 IF-IP-SUM-08 路径）；发药/扣库事实由 **MedicationDispense** 承接，**SupplyDelivery** 在药房核发时创建。
3. **临床发药、临床科室发出**（医嘱/收费 **DeviceDispense** 链）不纳入本聚合；**手工 `DEPT_ISSUE`（29）** 见 **§6.1.12**；**`DEPT_ISSUE_RETURN`（30）** 见 **§6.1.13**。

### 9.4 版本修订（v1.1）

- **枚举落库**：`type_enum` / `status_enum` / `category_enum` / `intent_enum` / `priority_enum` / `source_type_enum` / `purpose_type_enum` 与 **`com.zxplat.domain.share.enums.*#getCode()`** 对齐（**B.10.8.1**）；`value`/「编码值」列仍为文档对照，**不**作对外持久化整数。  
- **药库进货 code**：`CABINET_GOODS_RECEIPT` → **`CABINET_INBOUND_RECEIPT`**；`CABINET_GOODS_RECEIPT_INSPECTION` → **`CABINET_INBOUND_RECEIPT_INSPECTION`**。  
- **药库位置语义**：新增 **§3.1.1**（双端点 `source`/`purpose`；单库以 `purpose_*` 为主）。

### 9.5 版本修订（v1.2）

- **TRC-WIDE-R-001**：明细 **`trace_no`** 与 **DM-CUS-010**、**AS-INV-WHS-002-SDD**（**WHS-IN-SDD-R-008**）对齐，目标 DDL **VARCHAR(50000)**。

### 9.6 版本修订（v1.3）

- **§6.1.16** `removeByBusNo`：增补聚合内状态门控 **BR-SR-011**——仅 **`PENDING_APPROVAL`**、**`REJECT`**、**`WITHDRAW`** 允许整单逻辑删除；与 **DM-WKF-006-SDD** §5.1.16、**SupplyRequest** 实体 **`assertRemovableForSoftDelete`** 对齐。

### 9.7 版本修订（v1.4）

- **§6.1.16 / §6.1.17 / §9.1**：对齐 **AS-IP-NUR-010 v1.5** IF-IP-SUM-08——护士撤销待发送汇总 = **MedicationDispense** `cancelMedicationDispenseSummary` + **`removeByBusNo`**（**非** `cancelSummarySupplyRequest`）；药房驳回路径见 **AS-IP-PHA-002**。

### 9.8 版本修订（v1.5）

- **§6.1.3 / BR-SR-008/009**：对齐 **AS-INV-WHS-005**（W-03 药库退货）——明细仅来自已同意进货单；`getLinkableGoodsReceiptDetail` / `ReturnToSupplierInboundLinesupport 行键与可退余量算法；修订 **`assertReturnToSupplierLinesRevisable`** 软删重插（**无** rowVersion）；`practitioner_id` 可选落库；同意编排发放 + 源库扣减 + 追溯。

### 9.11 版本修订（v1.8）

- **§4.1.1 / §6.1.5 / §6.1.14**：**退库入库单（type 5）** 口径修订——由药房/科室退库 agree 自动生成只读壳单（`bootstrapFinalStatusCode=AGREE`，`original_bus_no`=退库单号）；药库**不**手工建单/agree（**`WHS_RSI_006`**）；库存唯一落账点在药房/科室退库 agree（**`ReturnToWarehouseStockInLinesupport**，对偶 **W-03 退货 · AS-INV-WHS-005**）。**药房退库（type 14）** 为用户主动单（**`pharmacy/returntowarehouse`**），`original_bus_no` 指向药房入库单；**科室退库（type 22）** 对称落地 **`department/returntowarehouse`**，错误码 **`DEPT_RTW_*`**，`original_bus_no` 指向 **`DEPT_STOCK_IN`**。

### 9.12 版本修订（v1.8 补遗）

- **§6.1.5**：澄清 **AS-INV-WHS-005** 仅指 **W-03 药库退货**；**W-05 退库入库** 对应包 **`returnstockin`**，**AS/SDD 待建**（勿与 005 混用）。

### 9.10 版本修订（v1.7）

- **§6.1.8 / §6.1.14 / 典型免申请链路**：对齐 **AS-INV-WHS-008/009**（药库调拨）——**仅调出 agree 落账**；`createTransfer` 支持 **`bootstrapFinalStatusCode`** 与调出修订路径（**`CABINET_TRANSFER_OUT` / `PHARMACY_TRANSFER_OUT` / `DEPT_TRANSFER_OUT`**）；调入壳单 `original_bus_no` 指向调出单；**WHS_TI_002** 禁止手工调入写/agree。

### 9.13 版本修订（v1.9）

- **§6.1.8 / §6.1.14**：对齐 **`zxplat`** 科室调拨应用包 **`inventory/department/transferout`**、**`transferin`**（type **25/26**）——源侧仅 **`DEPARTMENT_PHARMACY`**；调入壳单 type **25** 映射与 **`DEPT_TI_002`** 写拒绝；调出 agree 编排与药库/药房同构（`createTransferDelivery` + InventoryItem 源减目增 + 对侧壳单 `bootstrapFinalStatusCode=AGREE`）。

### 9.19 版本修订（v1.15）

- **§1.4 / §4.1（31 常量）/ §4.1.4 / §6.1.1 / §6.1.1a / BR-SR-001 / BR-SR-016**：新增 **科室采购申请单（type 31 · `DEPT_PURCHASE_REQUEST`）**——科室药房申请单、产品定义级选品、同意仅终态；领域 **`createDeptPurchaseRequest`**；药库订货 **`createPurchaseOrder`** 支持可选 **`original_bus_no`** 拉源；**BR-SR-016** 明确仅约束药库订货；**AS-INV-DEP-007** / **AS-INV-WHS-001**。

### 9.18 版本修订（v1.14）

- **§1.1 / §1.4 / §3.1.1 / §4.1.4 / §6.1.13**：新增 **科室退领单（type 30 · `DEPT_ISSUE_RETURN`）**——自 **`DEPT_ISSUE`** 退回、单端点 `purpose_*`、`original_bus_no` 必填、同意后在科室药房加回；领域 **`createDeptIssueReturn`** / **`assertDeptIssueReturnLinesRevisable`**；**AS-INV-DEP-009**；状态流转节号 **6.1.14～6.1.19** 顺延。

### 9.17 版本修订（v1.13）

- **§1.1 / §1.4 / §4.1.4 / §6.1.12**：新增 **科室领用单（type 29 · `DEPT_ISSUE`）**——手工库房菜单、单端点 `purpose_*`、同意后在科室药房扣减；与临床 **DeviceDispense** 分列；领域 **`createDeptIssue`** / **`assertDeptIssueLinesRevisable`**；**AS-INV-DEP-004**。
- **§6.1.13～§6.1.18**：原状态流转动作 **6.1.12～6.1.17** 顺延编号（提交/撤回/同意/驳回/删除/取消汇总）。

### 9.16 版本修订（v1.12）

- **§5.3 / §6.1.9 / §6.1.14**：对齐 **`zxplat`** 请领申请单 agree **不自动**建出库/入库执行单（**AS-INV-PHA-002**、**`inventory/department/requisition`**）；科室药房请领 **`DEPT_REQUISITION`**（type **20**）落地 **`InventoryDepartmentRequisitionAppService`**，库位 **`DEPARTMENT_PHARMACY`**，错误码 **`DEP_QL_*`**，列表占位 **`InventoryDepartmentConstants`**；对偶药房 **`pharmacy/requisition`**。

### 9.15 版本修订（v1.11）

- **§6.1.6 / §6.1.7**：对齐 **`zxplat`** 科室药房 **`inventory/department/gainloss`**（type **23** `DEPT_GAIN_LOSS`）、**`department/stocktake`**（type **24** `DEPT_STOCKTAKE`）——库位 **`LocationForm.DEPARTMENT_PHARMACY`**（`LocationQueryService#listAllActiveDepartmentPharmacies`）；列表占位 **`InventoryDepartmentConstants`**；应用服务 **`InventoryDepartmentGainLossAppService`** / **`InventoryDepartmentStocktakeAppService`**；损益 agree → **`createSupplyDelivery`**（须财务审批）；盘点 agree → **`createStocktakingDelivery`**（**亦须财务审批**，**BR-SR-013** / **AS-FIN-APR-001**）；对偶药房 **`pharmacy/gainloss`**、**`pharmacy/stocktake`**；AS **AS-INV-DEP-005/006**。

### 9.14 版本修订（v1.10）

- **§4.1.1 / §6.1.5 / §6.1.9**：对齐 **`zxplat`** 科室药房 **`inventory/department/stockin`**（type **21** `DEPT_STOCK_IN` 壳单只读 **`DEPT_IN_002`**）与 **`department/returntowarehouse`**（type **22** `DEPT_RETURN` 主动退库 **`DEPT_RTW_*`**）——库位 **`LocationForm.DEPARTMENT_PHARMACY`**；列表占位 **`InventoryDepartmentConstants`**；科室退库可退余量 **`ReturnToWarehouseStockInLinesupport**（type **22** 占用）；对偶药房 **`pharmacy/stockin`**、**`pharmacy/returntowarehouse`**。

### 9.9 版本修订（v1.6）

- **§6.1.1 / 药库订货（type 1）**：对齐 **AS-INV-WHS-001**（W-01）与 **`zxplat`** 实现——**`createPurchaseOrder` 修订路径**（**`assertPurchaseOrderLinesRevisable`** + **`expectedRowVersionForRevision`** 乐观锁 **`WHS_PO_105`**）；`practitioner_id` / `occurrence_time` / 行价字段应用层契约；列表 **`bus_no` 聚合读**（**`SupplyRequestDocumentListQuery`**）；**`agreeRequest` 不自动 `createInboundReceipt`**，进货预填见 **IF-WHS-IN-13**。

### 9.31 版本修订（v1.27）

- **§6.1.5 `createReturn`**：主动退库（type **14/22**）**`original_bus_no` 可空**（自由选品）；非空须绑已同意入库单并由应用层校验可退余量；壳单 type **5** 仍必填关联退库单号。修订门控补 **`assertDeptReturnLinesRevisable`**。对齐 **AS-INV-PHA-003 v0.2** / **AS-INV-DEP-003 v0.1**。

### 9.30 版本修订（v1.26）

- **§6.2.15**：`DeptIssueReportLineQueryModel` 明确带出 **`approverId`**（对齐 **AS-RPT-005 / AS-RPT-006** 明细执行人/发耗材人）。

### 9.29 版本修订（v1.25）

- **§3 / §4.1.4 / §6.1.12 / §6.1.13 / §6.2.15**：**领用科室与科室药房解耦**——新增 **`consuming_organization_id`**（Flyway **health-final V111**）；**`DEPT_ISSUE`** 客户端手选落库；**`DEPT_ISSUE_RETURN`** 自源领用单继承；历史行读侧可回落 **`Location.organization_id`**；报表科室维优先落库列；对齐 **AS-INV-DEP-004 v0.6** / **AS-INV-DEP-009 v0.6**。

### 9.28 版本修订（v1.24）

- **§6.1.8**：调入壳单（type **8/17/25**）生成须写入 **`approver_id`/`approval_time`**（与调出同意人对齐；bootstrap AGREE 不走 `approvalToAgree`）；制单人优先沿用调出行 `applicant_id`；对齐三方调入列表 **IF-*-TI-02** 侧栏审批人回显与历史壳单只读回填。

### 9.27 版本修订（v1.23）

- **§6.1.5**：退库入库壳单（type **5**）生成须拷贝下游退库 **`reason`**，并写入 **`approver_id`/`approval_time`**（与同意人对齐；bootstrap AGREE 不走 `approvalToAgree`）；对齐 **AS-INV-WHS-006 v0.2**（**BR-WHS-RSI-003～005**）列表/详情回显与历史壳单只读回填。

### 9.26 版本修订（v1.22）

- **§6.1.9 / BR-SR-020**：请领/批号级建单（type **12/20**）自 **`CreateRequisitionItemCommand`** 持久化 **`start_time`/`end_time`**（生产日期/有效期）至 **SupplyRequest**，供详情回显并与库存批号效期对齐；对齐 **AS-INV-PHA-002** / **AS-INV-DEP-001** 列表 enrich 与效期落库。

### 9.25 版本修订（v1.21）

- **§6.1.3 / BR-SR-019**：药库退货建改单领域写价——`applyReturnToSupplierCommand` + 实体落 `price`/`total_price`（进货落库价优先，**BR-PRICE-SRC-001** / **BR-MON-001**）；对齐 **AS-INV-WHS-005 v0.4** / SDD **v0.5**（列表侧栏金额与制单人富化为应用层，见 **BR-WHS-RT-009/010**）。

### 9.24 版本修订（v1.20）

- **BR-SR-013 / `SupplyType#requiresFinanceApproval`**：财务审批族对齐 **AS-FIN-APR-001**——进货、退供应商、三线损益、四类盘点；订货/退库入库退出财务门控；盘点纳入。
- **§6.2.14**：财务审批供应侧列表读契约（三件套 + 扩展过滤）；跨聚合并集由应用层 Loader（B.6.4）。
- **§6.1.14**：提交审批关键约束去掉误挂的 **BR-SR-013**（财务门控仅 agree/reject）。

### 9.23 版本修订（v1.19）

- **§3.1 / §6.1.2 / §6.1.3**：补 **`invoice_no`**（采购发票号，同号多行共享；进销存 3503/3504 `purc_invo_no` 真源；库默认 `0`；进货单头填写、退货可回落进货单）。

### 9.22 版本修订（v1.18）

- **§2 状态表 / BR-SR-003 / §6.1 修订路径**：**`REJECT`/`WITHDRAW`** 纳入可修订与可再提交集合（`isRevisableSupplyStatusCode` / `assertSubmittable`）；同号软删重插后状态回落 **`PENDING_APPROVAL`**。
- **§6.1.15 `rejectRequest`**：`changeReasonCode` 同号全行写入实体 **`reason`**；列表聚合 DTO 暴露 **`rejectReason`** 供侧栏展示。
- **同号修订读侧**：业务读统一 **`findByBusNoAndIsDeletedFalse`**；修订写路径 **`deleteAll` + `flush` 后再重插**；底座 **`AuditedEntityBase`** 补全 **`@FilterDef(deletedFilter)`**（原先仅有 `@Filter`，Session 启用失败被吞掉会导致软删行仍被读出、明细翻倍）。

### 9.21 版本修订（v1.17）

- **§6.2.13**：**AS-INV-RPT-002** 单据详情报表读侧 **`pageWarehouseDocumentDetail`** 扩展审批时间、批号 LIKE、品名关键字 resolved 过滤；读模型补 **`totalQuantity`/`profitLossPrice`**；Predicate **`WarehouseDocumentDetailPagePredicatesupport**。

### 9.20 版本修订（v1.16）

- **§6.2.12**：对齐 **`zxplat`** 应用层 **`inventory/dailysettlement`** 消费路径；补充 **`SupplyRequestSettlementWindowPredicatesupport** 与 **`DailySettlementAssemblySupport#accumulateSupplyLines`** 锚点。

### 9.19 版本修订（v1.15）

- **§6.2.12**：**AS-INV-RPT-001** 库房日结期间供应动账批量读 **`listAgreedLinesByWarehouseAndApprovalWindow`** 落地 **`SupplyRequestQueryService`**；入参 **`SupplyRequestSettlementWindowQuery`**、出参 **`SupplyRequestSettlementLineQueryModel`**（本聚合 `queries` 包）。

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`SupplyRequestDomainService`

| 方法 |
|------|
| `createPurchaseOrder` |
| `createDeptPurchaseRequest` |
| `createInboundReceipt` |
| `createReturnToSupplier` |
| `createStockOut` |
| `createReturn` |
| `createGainLoss` |
| `createDeptIssue` |
| `createDeptIssueReturn` |
| `createStocktake` |
| `createOpening` |
| `voidOpening` |
| `createTransfer` |
| `createRequisition` |
| `createSummarySupplyRequest` |
| `createDeviceSummarySupplyRequest` |
| `submitApproval` |
| `withdrawApproval` |
| `agreeRequest` |
| `rejectRequest` |
| `removeByBusNo` |
| `cancelSummarySupplyRequest` |
| `allocateBusNoForType` |
| `flushPendingChanges` |

### QueryService：`SupplyRequestQueryService`

| 方法 |
|------|
| `getSupplyByBusNo` |
| `getSupplyByBusNoBatch` |
| `getSupplyByOriginalBusNo` |
| `existsUndeletedSupplyByOriginalBusNo` |
| `selectByIdList` |
| `getById` |
| `supplyRequestValidation` |
| `verifyAbleEdit` |
| `existsInboundByItemId` |
| `getItemTable` |
| `getItem` |
| `countDistinctBusNo` |
| `listDistinctBusNoPage` |
| `listByBusNosWithFilter` |
| `pageWarehouseDocumentDetail` |
| `listMedicationSummarySlipLinesByBusNo` |
| `listAgreedLinesByWarehouseAndApprovalWindow` |
| `pageDeptIssueReportLines` |
| `listDeptIssueReportLines` |
| `summarizeDeptIssueReport` |
| `summarizeDeptIssueReportWithLines` |
| `openingTypeCodes` |
| `existsNonOpeningBusinessForLocation` |
| `findFirstNonOpeningBusinessForLocation` |
| `existsOpeningInApprovalForLocation` |
| `findCurrentAgreedOpeningBusNo` |

