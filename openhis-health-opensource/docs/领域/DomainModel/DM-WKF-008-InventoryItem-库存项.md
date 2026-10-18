# DM-WKF-008 InventoryItem 库存项

> **限界上下文**：WKF 域 · 库存管理  
> **聚合类型**：实体聚合  
> **版本**：v1.0.9 · **日期**：2026-10-14

### 1.1 业务定义

库存项（InventoryItem）是药品/物资在**某一库位**上、按**批号与供应商**拆分的库存事实行：其**业务自然键（五元组）**为 **`location_id + item_table + item_id + lot_number + supplier_id`**（同一未删除行在库表内须唯一，见 **§2.1**）。同库同品同批号、**不同供应商**须落成**多行**（库存明细按供应商拆量展示）；目录/医生选品可用量仍可按 **`item_table + item_id`**（及库位）汇总。**货位等**为行内业务属性，不另扩自然键。**单价/进价/售价不在本聚合持久化**，须通过 **`adm_charge_item_definition`** 按**实例表 + 实例主键 + 批号**查询（**不含供应商**；同批号同价共用一条批号价）。**库房供应单据读锚**仅 **`PURCHASE`**（无批号）与 **`LOT_NUMBER_COST`**（有批号，均每包装，见 **`DM-ADM-010` §1.5**）；**`LOT_NUMBER_PRICE`** 由进货同意写入、供门诊/收费下游，**不参与**供应单据槽价计算。同一五元组下不得并存多行有效库存。发药按批号定位账面时，同批号多供应商行按 **FEFO**（效期升序 → 创建时间升序）在**多行间拆分扣减**（`allocateAndDecrease` 传入批号；候选须 ACTIVE、quantity&gt;0、未过期、未锁定；**禁止**整笔砸 FEFO 首条导致有货行扣不到）。它只负责管理库存本身的数量、状态与批次信息，不关心库存变动的业务原因（采购入库、调拨、报损、药品发放、耗材发放、盘点等）。**「为什么变」由上游编排域分别承载：供应侧（SupplyDelivery / SupplyRequest：采购、调拨、报损、盘点等）与发放侧**（**MedicationDispense**、**DeviceDispense**：常规发药/耗材发放事实落在各自发放主表）；应用层编排调用库存域通用方法完成数量变更。**SupplyDelivery 不承担常规发药动作**，仅少数特殊发药/出库链路可能仍经 **SupplyDelivery** 编排扣减（以本稿约定为准），见 **§1.4**、**§3**。

> **可用量 / FIFO 读口径（与 `InventoryItemQueryService` 对齐）**：参与「可用库存」与 **§6.1.4** 候选集的行须同时满足：`inventory_status_enum` 等于 **`PublicationStatus.ACTIVE` 之持久化码**（**`getCode()` → `"ACTIVE"`**，**禁止**以数字串 **`"2"`** 等与枚举实体不一致的写法参与规格查询）、未逻辑删除、`quantity > 0`、无有效锁定、满足效期谓词（见 **§6.2.3** / **BR-II-QRY-003**）。跨 **ProductDefinition** 目录与 **InventoryItem** 批号行做可用量聚合时，批号比对须使用与域查询相同的**归一化函数**（实现名 **`normalizeLotNumberForAvailabilityMatch`**），且**仅统计 `wkf_inventory_item.location_id` 与来源库位（如选品请求之 `sourceLocationId`）一致**的库存行。

### 1.2 核心职责


| 职责      | 说明                       |
| ------- | ------------------------ |
| 库存量管理   | 管理库存数量的新增、增加、减少，维护当前库存量  |
| 批次与效期管理 | 记录产品批号、生产日期、效期，支持效期预警    |
| 先进先出分配  | 按效期升序跨批次自动分配扣减库存         |
| 冻结/解锁管理 | 锁定库存项以防止并发操作修改，解锁后恢复正常业务 |
| 供应状态管理  | 管理库存项的正常供应与停供状态          |
| 高值耗材追溯  | 通过追溯码与包装层级支持高值耗材的全程追溯    |


### 1.3 适用范围


| 场景          | 说明                                                                                                                              |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------- |
| 采购入库        | 采购到货后增加库存                                                                                                                       |
| 调拨          | 库房间库存转移                                                                                                                         |
| 药品发放 / 耗材发放 | 由 **MedicationDispense** / **DeviceDispense** 承载发放事实，应用层编排 `allocateAndDecrease` 等扣减库存；**不由 SupplyDelivery 承担常规动作**（见 **§1.4**） |
| 盘点          | 盘盈盘亏调整库存数量                                                                                                                      |
| 报损          | 过期/损坏药品报损扣减                                                                                                                     |
| 停供/恢复       | 暂停或恢复某库存项的分配参与                                                                                                                  |


### 1.4 与上游域（SupplyDelivery / 发放域）的职责边界

**InventoryItem** 只维护库存**数量与状态事实**（增减、分配、锁定、停供、清零删除），不承载「因何业务单据扣减」的完整叙事——后者在**供应发放**或**药品/耗材发放**聚合中落库。


| 职责        | InventoryItem                    | SupplyDelivery（供应发放）                                      | MedicationDispense / DeviceDispense（药品/耗材发放）            |
| --------- | -------------------------------- | --------------------------------------------------------- | ------------------------------------------------------- |
| 关注点       | 库存「有多少」、能否分配                     | 供应侧「为什么变」：采购、调拨、报损、盘点等及数量拆分                               | 发放侧「谁被发放」：常规**药品发放**、**耗材发放**事实在**发放主表**                |
| 常规发药/耗材扣减 | 仅执行 `allocateAndDecrease` 等写库存能力 | **不承担**门诊/住院等**常规发药动作**与发放事实                              | **承担**发放事实；发放确认路径编排调用库存域 `allocateAndDecrease`（或等价扣减序列） |
| 数量与来源     | 不存业务来源明细                         | 记录供应类变动与来源                                                | 记录发放量、批号、与请求/执行等锚点（**DM-MED-002**、**DM-WKF-007**）       |
| 先进先出算法    | 本聚合内执行                           | 不涉及                                                       | 不实现 FIFO，依赖库存域                                          |
| 特殊链路      | 同左                               | **少数**特殊发药/出库若设计为经 **SupplyDelivery** 扣减，须在本稿 **§6.1** 单列 | 与左列互斥：避免同一业务重复扣减                                        |


> **编排口径（与 §3 一致）**：
>
> - **供应侧**：进货/入库/出库/退库/损益 → `createSupplyDelivery` → `increaseQuantity` / `decreaseQuantity`；调拨 → `createTransferDelivery` → 源 `decreaseQuantity` + 目 `increaseQuantity`；盘点差异 → `createStocktakingDelivery` → `increaseQuantity` / `decreaseQuantity`（`allowNegative=true`）；药品汇总 COMPLETED → `allocateAndDecrease`。
> - **发放侧**：**MedicationDispense** / **DeviceDispense** → 常规扣减以 `allocateAndDecrease` 为主。
> - **禁止**在正文默认将「常规发药扣减」写成仅由 **SupplyDelivery** 驱动；**禁止**同一发药业务被 SupplyDelivery 与 MedicationDispense 双重扣减。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 独立锁表（锁定记录持久化在 **inventory_lock**，不按聚合根行内嵌字段表达，语义见 **§3.2**）。

**表结构**：

- 主表：`wkf_inventory_item`（主键：`id`，对应 **InventoryItem** 聚合根）
- 锁表：`inventory_lock`（外键：`inventory_id` → `wkf_inventory_item.id`）
- 快照静表：`wkf_inventory_item_snapshot`（只读历史镜像，**非聚合根**；见 **§9.5**）

**业务唯一性（五元组）与库表约束**：

- **自然键**：`location_id` + `item_table` + `item_id` + `lot_number` + `supplier_id`（逻辑删除行是否纳入 UNIQUE 以 DDL 为准；**目标态**为：在**有效库存**语义下不存在两条未删除且五元组完全相同的行）。
- **数据库**：目标态须在 `wkf_inventory_item` 上建立与上述五元组等价的 **UNIQUE** 约束（或唯一索引）；迁移前须清洗历史重复行并对 **`lot_number`** 做 **trim / 空串等价** 等与归一化规则一致的规范。当前实现以应用层五元组匹配/新建门禁为主。
- **与读侧一致**：FIFO、可用量、药库出库/药房入库选品等场景的**批号比对**，须与 UNIQUE 所用 **`lot_number`** 归一化规则一致（见 **§1.1** 说明）；入账合并/新建定位须带 **`supplier_id`**。

**高频查询模式**：

- 按库存项主键：主表单键加载，供写侧前置与乐观锁校验。
- 按物品、库房、批号等：主表条件扫描，支撑列表与 **§6.1.4** `allocateAndDecrease` 候选集。
- 按**五元组**命中判断：应用层先读后决策 `createInventoryItem` / `increaseQuantity`（见 **§9.1**）；写库前应对 **`lot_number`** 做与 UNIQUE 一致的业务归一化（与读侧归一化口径协调）。
- 按业务单号查锁：`inventory_lock.bus_no`，支撑 **§6.1.6** `unlockByBusNo`。

### 2.2 聚合根标识


| 属性    | 类型     | 说明                                                |
| ----- | ------ | ------------------------------------------------- |
| 库存项标识 | `Long` | 全局唯一标识，雪花 ID（`@TableId(type = IdType.ASSIGN_ID)`） |


### 2.3 状态机

```text
                     ┌─────── resumeSupply ────────┐
                     │                              │
                     v                              │
  [新建] ──创建──> [正常供应] ──stopSupply──> [已停供]
                     │                              │
                     │                              │
                     └──deleteOnZero──> [已删除]     └──deleteOnZero──> [已删除]
```

#### 状态定义


| 持久化码（`getCode()`） | 枚举常量    | 语义                                  |
| ---------------- | ------- | ----------------------------------- |
| `ACTIVE`         | ACTIVE  | 正常供应：参与先进先出分配，允许增减，允许停供、增加、减少、分配、锁定、清零删除 |
| `RETIRED`        | RETIRED | 已停供：不参与先进先出分配，库存数量不变，允许恢复供应、清零删除        |
| （逻辑删除字段）       | —       | 已删除：不参与任何业务（**不**再占用 `DELETED` 码表示行状态）     |


> **状态枚举说明**：列 **`inventory_status_enum`** 与 **`PublicationStatus`** 对齐，**API / 落库 / 规格查询一律使用 `getCode()` 字符串**（**B.10.8.1**）：正常供应为 **`ACTIVE`**，停供为 **`RETIRED`**。**禁止**在查询可用量时使用与实体枚举不一致的数字串（如 **`"2"`**）。清零删除通过 `isDeleted` / `deleteFlag` 逻辑删除字段标记，映射 FHIR `entered-in-error`。

---

## 3. 实体

```text
InventoryItem（聚合根）—— 只管库存本身，不关心业务原因
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                  InventoryItem                        │
  │                                                      │
  │  - itemTable / itemId     （物品引用）                │
  │  - quantity: BigDecimal   （当前库存量）              │
  │  - lotNumber: String      （产品批号）                │
  │  - expirationDate         （效期）                    │
  │  - locationId: Long       （仓库/药房/药库）          │
  │  - locationStoreId: Long  （货位）                    │
  │  - supplierId: Long       （供应商）                  │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ InventoryLock（辅助实体）                     │    │
  │  │ - inventoryId -> InventoryItem.id            │    │
  │  │ - lockFlag / status / busNo / orderFlag      │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ BatchAllocationResult（值对象）               │    │
  │  │ - inventoryItemId / lotNumber                │    │
  │  │ - allocatedQuantity / remainingQuantity      │    │
  │  │ - locationId / locationStoreId               │    │
  │  │ - expirationDate                             │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

上游域通过应用层编排调用库存域的通用方法（与 **§1.4** 一致）。

**供应侧（SupplyDelivery）**——完整写库映射（与 **DM-WKF-005 §9.2** / **DM-WKF-006 §6.1.14** 对齐）：

> 触发时机：**SupplyRequest 执行单 `SupplyRequestAgreedEvent`** 后由应用层编排；药品汇总单为 `SupplyRequestCreatedEvent`（SEND）后由下游编排。**申请单 AGREE 不直接触发库存写入。**


| SupplyRequest 执行单类型（`type_enum`）           | SupplyDelivery 方法                                  | InventoryItem 写库方法                                                                                               | 说明                                                   |
| ------------------------------------------ | -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| 进货单 / 入库单（`2`/`11`/`13`/`21`）              | `createSupplyDelivery`                             | **四元组**匹配：`increaseQuantity`；无匹配：`createInventoryItem`                                                               | 进货增加源库房库存；科室入库为虚拟库存（`BR-SR-018`），跳过 InventoryItem 写入 |
| 出库单（`4`）                                   | `createSupplyDelivery`                             | `decreaseQuantity`（源库房）                                                                                          | 药库向药房/科室出库，直接扣减源库存                                   |
| 退库 / 退货单（`3`/`5`/`14`/`22`）                | `createSupplyDelivery`                             | 上级库：`increaseQuantity`；退库方：`decreaseQuantity`（科室虚拟库存除外 **BR-SR-018**）                                            | 双侧调整；退货（type 3）仅扣减药库侧                                |
| 损益单（`6`/`15`/`23`）                         | `createSupplyDelivery`                             | 报溢（`quantity > 0`）：`increaseQuantity`；报损（`quantity < 0`）：`decreaseQuantity`（`allowNegative=false`，应用层在创建前断言库存充足） | 正数报增，负数报损                                            |
| 调拨单（`8`/`9`/`10`/`17`/`18`/`19`/`25`/`26`） | `createTransferDelivery`（同一 `request_id` 生成两条发放记录） | 源：`decreaseQuantity`；目标：**四元组**匹配 `increaseQuantity`，无匹配 `createInventoryItem`                                       | 双侧原子编排；参见 **DM-WKF-005 §6.1.2**                      |
| 盘点 / 耗材盘点（`7`/`16`/`24`/`28`）              | `createStocktakingDelivery`（仅差异行，`quantity ≠ 0`）   | 盘盈（`quantity > 0`）：`increaseQuantity`；盘亏（`quantity < 0`）：`decreaseQuantity`（`allowNegative=true`，允许落负值）          | 零差异行跳过，不生成发放也不写库存；参见 **DM-WKF-005 §6.1.3**           |
| 药品汇总单（`27`，SEND 初态）                        | `createSummarySupplyDelivery` → 流程推进至 `COMPLETED`  | `COMPLETED` 后：`allocateAndDecrease`（FIFO 扣减源库房库存）                                                                | 汇总路径以 FIFO 分配扣减；与常规发药路径互斥，避免重复扣减（**§1.4**）           |


**发放侧（MedicationDispense / DeviceDispense）**——常规扣减：

- **MedicationDispense**（药品发放）/ **DeviceDispense**（耗材发放）→ `allocateAndDecrease`（先进先出）；发放事实与状态在各自发放主表。
- **SupplyDelivery 不承担常规发药动作**（门诊/住院日常发药链路不经 SupplyDelivery，见 **§1.4**）。

### 3.1 库存项（InventoryItem）

InventoryItem 聚合根的属性定义，对应 `wkf_inventory_item` 表。


| 属性        | 字段名                   | 类型           | 必填  | 说明                                                  |
| --------- | --------------------- | ------------ | --- | --------------------------------------------------- |
| 物品类别编码    | `categoryCode`        | `String`     | 是   | 物品类别编码（药品/耗材/诊疗项等）                                  |
| 物品定义表名    | `itemTable`           | `String`     | 是   | 物品所属定义表名（对应 FHIR `code`）                            |
| 物品定义标识    | `itemId`              | `Long`       | 是   | 物品定义 ID（关联 MedicationDefinition / DeviceDefinition） |
| 物品名称      | `name`                | `String`     | 是   | 冗余存储，用于展示与搜索                                        |
| 拼音码       | `pyStr`               | `String`     | 否   | 拼音检索码                                               |
| 五笔码       | `wbStr`               | `String`     | 否   | 五笔检索码                                               |
| 库存状态      | `inventoryStatusEnum` | `String`     | 是   | 见 §2.3 / §4.1；与 **`PublicationStatus#getCode()`** 对齐（**`ACTIVE` / `RETIRED`**） |
| 供应商标识     | `supplierId`          | `Long`       | 是   | 关联供应商 ID                                            |
| 描述文本      | `descriptionText`     | `String`     | 否   | 补充描述                                                |
| 计量单位编码    | `unitCode`            | `String`     | 是   | 最小计量单位编码（对应 FHIR `baseUnit`）                        |
| 当前库存数量    | `quantity`            | `BigDecimal` | 是   | 以最小单位计的当前库存量（对应 FHIR `netContent`）                  |
| 特征扩展 JSON | `characteristicJson`  | `String`     | 否   | 扩展特征信息（对应 FHIR `characteristic`）                    |
| 产品批号      | `lotNumber`           | `String`     | 是   | 产品批号（对应 FHIR `instance.lotNumber`）                  |
| 生产日期      | `productionDate`      | `Date`       | 否   | 生产日期；日期时间类型以落库 DDL 为准                               |
| 效期        | `expirationDate`      | `Date`       | 否   | 失效日期；日期时间类型以落库 DDL 为准                               |
| 有效期月数     | `validityMon`         | `Integer`    | 否   | 有效期月数                                               |
| 货位标识      | `locationStoreId`     | `Long`       | 否   | 货位 ID                                               |
| 库房标识      | `locationId`          | `Long`       | 是   | 仓库/药房/药库 ID（关联 Location 聚合）                         |
| 追溯码       | `traceNo`             | `String`     | 否   | 高值耗材追溯码                                             |
| 追溯码包装层级   | `packagingLevels`     | `Integer`    | 否   | 追溯码包装层级                                             |
| 乐观锁版本号    | `version`             | `Integer`    | —   | `@Version`，并发更新时自动校验                                |

> **同意链新建库存（`createInventoryItem`）**：应用层 **`InventoryWarehouseInboundReceiptMapper#toCreateInventoryItemCommand`**（及药房入库、药库出库目的库等**同源 Mapper 装配**）将供应行 **`start_time`/`end_time`** 写入 **`CreateInventoryItemItemCommand`** 之 **`productionDate`/`expirationDate`**；**`categoryCode`/`pyStr`/`wbStr`** 在应用层经 **`ProductDefinitionBatchLoader`**（按 `item_table` 路由各 `*DefinitionQueryService`）解析且字段 **非空白** 时写入（**trim** 后落库），**否则**对应列保持 **`null`**。**五元组已存在**走 **`increaseQuantity`** 时**不**回填上述冗余列。

### 3.2 库存锁定记录（InventoryLock）

库存锁定记录管理库存项的冻结/解锁状态，锁定期间库存不可被任何业务修改。对应 `inventory_lock` 表。


| 属性      | 字段名           | 类型        | 必填  | 说明                     |
| ------- | ------------- | --------- | --- | ---------------------- |
| 锁定记录标识  | `id`          | `Long`    | 是   | 主键（雪花 ID）              |
| 关联库存项标识 | `inventoryId` | `Long`    | 是   | 关联 InventoryItem.id    |
| 锁定标识    | `lockFlag`    | `Boolean` | 是   | `true` 已锁定、`false` 未锁定 |
| 锁定状态    | `status`      | `Integer` | 是   | 见 §4.3 LockStatus      |
| 业务单号    | `busNo`       | `String`  | 是   | 触发锁定的业务单号              |
| 排序号     | `orderFlag`   | `Integer` | 是   | 同一业务单内的顺序              |
| 乐观锁版本号  | `version`     | `Integer` | —   | `@Version`             |


> 同一库存项同一时刻只允许一条有效锁定记录（`lockFlag = true`）。锁定状态的流转：锁定中(0) → 已解锁(1)（正常完成）或 强制解锁(2)（异常恢复）。

---

## 4. 值对象

### 4.1 库存状态（InventoryStatus / PublicationStatus）

库存项状态复用全局 `PublicationStatus` 枚举，仅使用其中两个值；**持久化与查询一律取 `getCode()` 字符串**（**B.10.8.1**）。


| 语义   | 持久化码      | 说明        |
| ---- | --------- | --------- |
| 正常供应 | `ACTIVE`  | 参与先进先出分配  |
| 已停供  | `RETIRED` | 不参与先进先出分配 |


> `PublicationStatus` 在 Java 中另有序号型 `getValue()`，**不得**写入 `inventory_status_enum` 列或以数字串参与 SQL/JPA 规格；库存项仅使用 **`ACTIVE`** 与 **`RETIRED`** 两码。

### 4.2 批次分配结果（BatchAllocationResult）

封装跨批次先进先出分配的单批次结果。


| 属性    | 类型              | 说明                      |
| ----- | --------------- | ----------------------- |
| 库存项标识 | `Long`          | 被分配的库存项 ID              |
| 产品批号  | `String`        | 被分配的批号                  |
| 分配数量  | `BigDecimal`    | 本批次分配数量                 |
| 剩余数量  | `BigDecimal`    | 本批次分配后的剩余库存             |
| 库房标识  | `Long`          | 库存所在仓库                  |
| 货位标识  | `Long`          | 库存所在货位                  |
| 效期    | `LocalDateTime` | 效期（用于药品/耗材发放分配场景标注批次信息） |


### 4.3 锁定状态（LockStatus）


| 枚举值  | 编码  | 说明          |
| ---- | --- | ----------- |
| 锁定中  | `0` | 库存项已锁定，不可修改 |
| 已解锁  | `1` | 正常业务完成后解锁   |
| 强制解锁 | `2` | 异常恢复场景下强制解锁 |
| 待确认  | `3` | 等待确认的锁定状态   |


### 4.4 五元组匹配键（InventoryMatchKey）

用于判断入库时是否合并到已有库存行的匹配标准（与 **§2.1** 业务唯一一致）。


| 属性     | 类型       | 说明          |
| ------ | -------- | ----------- |
| 库房标识   | `Long`   | 仓库/药房/药库 ID |
| 物品定义表名 | `String` | 物品所属定义表名    |
| 物品定义标识 | `Long`   | 物品定义 ID     |
| 产品批号   | `String` | 产品批号（须与归一化规则一致） |
| 供应商标识  | `Long`   | 供应商 ID；入账定位须非空 |


> **不变式**：五元组完全匹配时才合并库存行；**同批号不同供应商须拆成多行**（`BR-II-002` / `BR-II-006`）。批号维定价键仍为 **实例表 + 实例主键 + 批号**（不含供应商）；同品种同批号进价冲突由进货/期初应用门禁拒绝（须换批号字面值）。

---

## 5. 领域事件

事件命名以 **Inventory** / **InventoryItem** 等为前缀、过去分词或完成态名词（如 **Created**、**Allocated**）与院内库存类事件一致；载荷字段以 `zxplat-domain` 及 **§5.1**/**§6.1** 为准（若事件载荷未在本稿单列，以实现与 **§6.1.x** 写侧契约为准）。

### 5.1 事件定义

> **实现状态说明**：当前实现中 InventoryItem 聚合**尚未实际发布**下列领域事件；库存变动的下游通知可由 **SupplyRequest** / **SupplyDelivery**（供应侧）及 **MedicationDispense** / **DeviceDispense**（发放侧）等**邻域事件**间接驱动QueryService更新。下表为**设计契约（规划）**；落地时须与 **§6.1.1**～**§6.1.10** 写侧方法一一对应，并在迁移计划中补齐发布点，避免与 **§5.2** 长期偏离。


| 事件名称（中文） | 英文名称                               | 触发时机                                                           | 携带数据（示意）                                            |
| -------- | ---------------------------------- | -------------------------------------------------------------- | --------------------------------------------------- |
| 库存项已创建   | `InventoryItemCreatedEvent`        | 新建库存项成功（**§6.1.1** `createInventoryItem`）                      | 库存项标识、`itemId`、`name`、`lotNumber`、`locationId`、初始数量 |
| 库存数量已增加  | `InventoryQuantityIncreasedEvent`  | 库存数量增加成功（**§6.1.2** `increaseQuantity`）                        | 库存项标识、增加量、增加后数量                                     |
| 库存数量已减少  | `InventoryQuantityDecreasedEvent`  | 库存数量减少成功（**§6.1.3** `decreaseQuantity`）                        | 库存项标识、减少量、减少后数量                                     |
| 库存已分配    | `InventoryAllocatedEvent`          | 先进先出分配扣减成功（**§6.1.4** `allocateAndDecrease`）                   | 分配明细列表（`BatchAllocationResult`）、总分配量                |
| 库存已锁定    | `InventoryLockedEvent`             | 库存项锁定成功（**§6.1.5** `lockInventory`）                            | 库存项标识、`busNo`、锁定人（若有）                               |
| 库存已解锁    | `InventoryUnlockedEvent`           | 库存项解锁成功（**§6.1.6** `unlockByBusNo` / **§6.1.7** `forceUnlock`） | 库存项标识、`busNo`、解锁类型（正常/强制）                           |
| 库存已停供    | `InventorySupplyStoppedEvent`      | 库存项停供成功（**§6.1.8** `stopSupply`）                               | 库存项标识、`name`                                        |
| 库存已恢复供应  | `InventorySupplyResumedEvent`      | 库存项恢复供应成功（**§6.1.9** `resumeSupply`）                           | 库存项标识、`name`                                        |
| 库存已清零删除  | `InventoryDeletedOnZeroEvent`      | 库存清零并逻辑删除成功（**§6.1.10** `deleteInventoryOnZeroQuantity`）       | 库存项标识、`itemId`、`lotNumber`                          |
| 库存低于预警线  | `InventoryBelowThresholdEvent`（预留） | 库存数量低于预警阈值（读侧或定时任务检出，写侧方法未定）                                   | 库存项标识、`name`、当前数量、预警阈值                              |


### 5.2 事件触发规则


| 当前状态（示意）   | 操作                | 目标状态（示意） | 发布事件                              |
| ---------- | ----------------- | -------- | --------------------------------- |
| —          | 创建库存项             | 正常供应     | `InventoryItemCreatedEvent`       |
| 正常供应       | 增加数量              | 正常供应     | `InventoryQuantityIncreasedEvent` |
| 正常供应       | 减少数量              | 正常供应     | `InventoryQuantityDecreasedEvent` |
| 正常供应       | 先进先出分配扣减          | 正常供应     | `InventoryAllocatedEvent`         |
| 正常供应       | 锁定库存              | 正常供应     | `InventoryLockedEvent`            |
| 正常供应       | 解锁库存（含按单号批量 / 强制） | 正常供应     | `InventoryUnlockedEvent`          |
| 正常供应       | 停供                | 已停供      | `InventorySupplyStoppedEvent`     |
| 已停供        | 恢复供应              | 正常供应     | `InventorySupplyResumedEvent`     |
| 正常供应 / 已停供 | 清零删除              | 已删除      | `InventoryDeletedOnZeroEvent`     |


> **状态常量**：「当前状态」「目标状态」中文列与 **§2.3**、`inventory_status_enum` / 逻辑删除语义同义；停供对应 **`RETIRED`**，正常供应对应 **`ACTIVE`**，已删除对应逻辑删除标记（见 **§2.3** 说明）。

> **发布说明**：上表为**规划契约**；实现若为同步编排未显式发布，须在迁移计划中补齐，避免与 **§5.1** 长期偏离。**InventoryBelowThresholdEvent** 未在 **§6.1.x** 单列写侧方法时，不得以本表臆造落库语义（以 **§6.1.x** 写侧契约为准）。

---

## 6. 领域服务

> **实现落点**：写侧以 `InventoryItemDomainService` 为主路径，与 `InventoryItemManager` 并存；构造/装配使用 `InventoryItemConverter`；只读查询在 `InventoryItemQueryService`；盘点锁写路径与 `InventoryLockManager` 并存（应用层择一）。不加 `@Transactional`。

### 6.1 写侧领域服务（InventoryItemDomainService）

下列方法编号 **§6.1.1**～**§6.1.10** 须与 **§5.1**/**§5.2** 事件契约一致；实现未显式发布事件时，以 **§5.1**「实现状态说明」为准。

#### 6.1.1 新增库存项目（`createInventoryItem`）

- **业务意义**：在应用层已判定**四元组**下不存在可合并库存行时，创建新的库存项并落主表全字段。
- **边界说明**：不代为执行四元组命中查询；不写入供应/发放业务单（由 **SupplyDelivery** / **MedicationDispense** / **DeviceDispense** 等上游承载叙事）。
- **入参**：`CreateInventoryItemCommand`（物品表名、物品标识、数量、批号、库房、供应商等，以本稿约定为准）。
- **出参**：新建后的库存项实体。
- **前置条件**：物品表与物品标识非空；初始数量 > 0；五元组语义满足「应新建」而非误合并（`BR-II-006`）。
- **状态变化**：`N/A`（新行默认正常供应 **`ACTIVE`**）。
- **聚合内影响**：经 `InventoryItemConverter` 装配并持久化 `wkf_inventory_item`；初始化 `inventoryStatusEnum`。
- **事件输出**：`InventoryItemCreatedEvent`（规划）。
- **失败语义**：校验失败、持久化失败时拒绝。

#### 6.1.2 库存数量增加（`increaseQuantity` / `increaseQuantities`）

- **业务意义**：在已有库存行上累加数量（典型：采购入库命中**四元组**后的累加）。
- **边界说明**：不创建新库存项；调用方须已确认目标行存在。
- **入参**：库存项标识、增加量。
- **出参**：更新后的库存项实体。
- **前置条件**：目标行未删除且未处于有效锁定（`BR-II-004`）；`requireActiveRow` 可加载。
- **状态变化**：`N/A`（不改变停供/正常枚举语义，除非另有编排）。
- **聚合内影响**：`quantity` 累加；乐观锁 `version` 校验（`BR-II-005`）。
- **事件输出**：`InventoryQuantityIncreasedEvent`（规划）。
- **失败语义**：锁定、乐观锁冲突、行不存在时拒绝。

#### 6.1.3 库存数量减少（`decreaseQuantity` / `decreaseQuantitiesMerged`）

- **业务意义**：在指定库存行上直接扣减数量（典型：报损、调拨出、盘点亏等供应侧路径）。
- **边界说明**：不做跨批次 FIFO；单表行级扣减。
- **入参**：库存项标识、扣减量、是否允许负数（盘亏等）。
- **出参**：更新后的库存项实体。
- **前置条件**：未锁定（`BR-II-004`）；常规路径须库存充足；`allowNegative` 为真时放行负库存（`BR-II-001`）。
- **状态变化**：`N/A`（除非扣减后置策略另行编排）。
- **聚合内影响**：`quantity` 扣减；乐观锁校验（`BR-II-005`）。
- **事件输出**：`InventoryQuantityDecreasedEvent`（规划）。
- **失败语义**：库存不足、锁定、乐观锁冲突时拒绝。

#### 6.1.4 跨批次 / 同批多行先进先出分配扣减（`allocateAndDecrease`）

- **业务意义**：按效期优先满足需求数量并逐行扣减 **InventoryItem**（典型：**药品发放** / **耗材发放**编排）。
- **边界说明**：不写发放主表、不写收费明细；仅本域数量与批次事实一致。发放主表可不存库存主键；扣减当下按库存行 id FEFO 拆分。
- **入参**：物品定义标识、物品定义表名、需求拆零数量、库房标识（可选）、**批号（可选）**、**供应商（可选）**、**includeExpired（可选）**、**literalLot（可选）**。
  - **批号空且未要求字面批号**：库房下跨不同批号 FEFO（发药无批号路径）。
  - **批号非空，或 literalLot**：仅同批号可用行（含不同供应商；供应商非空则只扣该供应商）间 FEFO **拆分扣减**，与发药门禁「同批合计」对齐；**禁止**择首条后整笔 `decreaseQuantitiesMerged`。
  - **includeExpired**：发药默认排除过期批；库房调拨/出库/退库/退货/领用经 `allocateWarehouseMovement` 置 true，过期批仍可动。
- **库房出库**：`allocateWarehouseMovement` 复用本方法（字面批号、允许过期）。数量为 0 的历史行不参与。同批多供应商按效期、创建时间拆分，批号价不含供应商。
- **出参**：`BatchAllocationResult` 列表（每账面行分配量与剩余量等，见 **§4.2**）。
- **前置条件**：存在满足 **§6.2.3** / FEFO 候选口径的库存；同口径合计 ≥ 需求量（`BR-II-QRY-001` / `BR-II-003`）。
- **状态变化**：`N/A`（行级仍为正常供应态，除非单行扣至零后的策略由上层处理）。
- **聚合内影响**：按 **BR-II-003** 排序后逐行扣减 `quantity`；多行乐观锁更新。
- **事件输出**：`InventoryAllocatedEvent`（规划）。
- **失败语义**：候选不足、锁定、过期/停供行误入（应被查询排除）时拒绝。

#### 6.1.5 锁定库存（`lockInventory`）

- **业务意义**：为指定业务单号建立库存冻结，防止并发增减。
- **边界说明**：写 `inventory_lock` 辅助表（**§3.2**）；不修改 `quantity`。
- **入参**：库存项标识、业务单号 `busNo`。
- **出参**：新建锁定记录实体。
- **前置条件**：库存行存在且未删（`requireActiveRow`）；同一库存项无另一条 `lockFlag=true`（`PR-II-LOCK-001`）。
- **状态变化**：`N/A`（锁定语义在锁表 `status` 表达）。
- **聚合内影响**：新建 `InventoryLock`；`orderFlag` 在同一 `busNo` 下自增，首条为 `0`（`PR-II-LOCK-004`）。
- **事件输出**：`InventoryLockedEvent`（规划）。
- **失败语义**：已锁定、行不存在时拒绝。

#### 6.1.6 按业务单号批量解锁（`unlockByBusNo`）

- **业务意义**：业务单正常完成后，按单号批量解除冻结。
- **边界说明**：仅更新锁表；不调整库存数量。
- **入参**：业务单号 `busNo`。
- **出参**：`—`。
- **前置条件**：存在待解锁记录；已解锁（`status=1`）跳过（`PR-II-LOCK-002`）。
- **状态变化**：锁定中 → 已解锁（`lockFlag=false, status=1`）。
- **聚合内影响**：批量更新满足条件的 `InventoryLock` 行。
- **事件输出**：`InventoryUnlockedEvent`（规划）。
- **失败语义**：无匹配记录时依策略返回或拒绝（以落库与交互契约为准）。

#### 6.1.7 强制解锁（`forceUnlock`）

- **业务意义**：异常恢复场景下按锁定记录主键强制解除冻结。
- **边界说明**：与 **§6.1.6** 为两条独立解锁路径（按单号批量 vs 按锁定主键强制）；编排层按场景择一调用；本方法标记强制解锁以区别于正常解锁。
- **入参**：锁定记录标识。
- **出参**：`—`。
- **前置条件**：锁定记录存在（`PR-II-LOCK-003`）。
- **状态变化**：→ `lockFlag=false, status=2`（强制解锁）。
- **聚合内影响**：更新单条 `InventoryLock`。
- **事件输出**：`InventoryUnlockedEvent`（规划）。
- **失败语义**：记录不存在时拒绝。

#### 6.1.8 停供（`stopSupply`）

- **业务意义**：将库存项标记为停供，使其不参与 FIFO 分配但保留数量。
- **边界说明**：不改变 `quantity`（`BR-II-007`）。
- **入参**：库存项标识。
- **出参**：更新后的库存项实体。
- **前置条件**：行未删除（`requireActiveRow`）。
- **状态变化**：正常供应 **`ACTIVE`** → 停供 **`RETIRED`**。
- **聚合内影响**：更新 `inventoryStatusEnum`。
- **事件输出**：`InventorySupplyStoppedEvent`（规划）。
- **失败语义**：行不存在或已删除时拒绝。

#### 6.1.9 恢复供应（`resumeSupply`）

- **业务意义**：将停供库存恢复为可参与 FIFO 的正常供应态。
- **边界说明**：不自动回补数量。
- **入参**：库存项标识。
- **出参**：更新后的库存项实体。
- **前置条件**：行未删除（`requireActiveRow`）。
- **状态变化**：**`RETIRED`** → **`ACTIVE`**。
- **聚合内影响**：更新 `inventoryStatusEnum`。
- **事件输出**：`InventorySupplyResumedEvent`（规划）。
- **失败语义**：行不存在或状态不允许时拒绝。

#### 6.1.10 库存清零删除（`deleteInventoryOnZeroQuantity`）

- **业务意义**：在数量为零且业务确认后，对库存行做逻辑删除，退出可用集合。
- **边界说明**：不物理删表；不处理上游单据状态。
- **入参**：库存项标识、应用层确认参数（曾用字段名 `applicationConfirmedNoPendingDelivery`：已确认无未完成关联发放等；实现若以 **Command/DTO** 或布尔字段命名，以 **本稿 §6.1** 为准，见 `BR-II-009`）。
- **出参**：标记删除后的库存项实体（或依实现返回 void，以本稿约定为准）。
- **前置条件**：`quantity = 0`；应用层确认满足 `BR-II-009`。
- **状态变化**：逻辑删除标记置位（`isDeleted` / `deleteFlag`）。
- **聚合内影响**：行不再参与查询默认集（`BR-II-008`）。
- **事件输出**：`InventoryDeletedOnZeroEvent`（规划）。
- **失败语义**：数量非零、确认未满足、乐观锁冲突时拒绝。

### 6.2 读侧查询服务（InventoryItemQueryService）

> 查询类动作无写库；用于写侧前置校验、**§6.1.4** 候选集、列表与盘点加载。复杂报表优先QueryService（`BR-II-QRY-002`）。

#### 6.2.1 按主键查询库存项（`getInventoryItemById`）

- **业务意义**：按主键加载单行库存项。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_inventory_item` 主键命中一行。
- **过滤口径**：默认排除逻辑删除行（与实现 `requireActiveRow` / 查询策略一致处从实现）。
- **组装规则**：实体或 DTO 直映射。
- **返回约束**：不存在时返回空或抛业务异常以落库与交互契约为准。

#### 6.2.2 按 ID 列表批量查询（`findByIdIn`）

- **业务意义**：按主键列表批量加载，服务盘点等批处理。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_inventory_item` 多主键 IN。
- **过滤口径**：排除已删除行。
- **组装规则**：列表原样返回。
- **返回约束**：顺序不保证与入参一致，除非实现显式排序。

#### 6.2.3 查询先进先出可用库存（`findAvailableInventories`）

- **业务意义**：返回满足先进先出策略的可用库存行有序列表。
- **边界说明**：供 **§6.1.4** 内部及发放前评估复用；不扣减。
- **查询范围（读库，无写入）**：主表多行。
- **过滤口径**：`inventory_status_enum` 等于 **`PublicationStatus.ACTIVE.getCode()`**（**`"ACTIVE"`**）、未删、`quantity > 0`、无有效锁、未过期（`BR-II-QRY-003`）；规格实现**不得**使用与枚举实体不一致的数字串（如 **`"2"`**）。
- **组装规则**：按 **BR-II-003** 效期升序、同效期创建时间升序。
- **返回约束**：可能为空列表；不保证覆盖全部库房除非入参限定。

#### 6.2.4 计算总可用库存量（`calculateTotalAvailableInventory`）

- **业务意义**：汇总指定物品在给定条件下的可分配总量。
- **边界说明**：只读；不替代 **§6.1.4** 的批次拆分结果。
- **查询范围（读库，无写入）**：与 **§6.2.3** 相同过滤维度下的聚合。
- **过滤口径**：同 **§6.2.3**。
- **组装规则**：对 `quantity` 求和或等价聚合。
- **返回约束**：数值型；无行时为零（以落库与交互契约为准）。

#### 6.2.5 按四元组查找匹配库存（`findMatchingInventory`）

- **业务意义**：命中**四元组**首条未删库存行，支撑入库合并判断（**§9.1**）。
- **边界说明**：只读；不创建新行。
- **查询范围（读库，无写入）**：`wkf_inventory_item`。
- **过滤口径**：`supplierId + locationId + itemTable + itemId + lotNumber` 精确匹配（与 **§2.1** 五元组及批号归一化口径一致；`BR-II-002` / `BR-II-006`）。
- **组装规则**：首条命中即返回。
- **返回约束**：无命中时返回空。

#### 6.2.6 按物品条件查找库存（`findInventoryItemByItemInfo`）

- **业务意义**：按物品、批号、库房等动态条件过滤库存列表。
- **边界说明**：只读通用查询；非报表汇总入口。
- **查询范围（读库，无写入）**：`wkf_inventory_item` 条件扫描。
- **过滤口径**：未删；其余条件以入参动态拼接（以落库与交互契约为准）。
- **组装规则**：列表映射。
- **返回约束**：分页与否由调用重载决定。

#### 6.2.7 按仓库获取批量盘点数据（`getBatchInventoryByWarehouse`）

- **业务意义**：按仓库与物品表批量拉取盘点初始数据。
- **边界说明**：只读；盘点审批流在 **SupplyRequest**（**§6.3**）。
- **查询范围（读库，无写入）**：主表批量行。
- **过滤口径**：仓库 + 物品表等（以落库与交互契约为准）。
- **组装规则**：盘点视图行列表。
- **返回约束**：数据量可能较大，调用方须分页或限流。

#### 6.2.8 乐观锁版本校验（`checkVersion`）

- **业务意义**：比对客户端携带版本与库中 `version` 是否一致。
- **边界说明**：只读校验；不写库。
- **查询范围（读库，无写入）**：单行 `version` 字段。
- **过滤口径**：主键命中。
- **组装规则**：布尔或业务码返回（以落库与交互契约为准）。
- **返回约束**：行不存在时拒绝或返回 false（以落库与交互契约为准）。

#### 6.2.9 检查是否锁定（`isLocked`）

- **业务意义**：判断库存项是否存在 `lockFlag=true` 的有效锁定。
- **边界说明**：只读；供增减前断言（`BR-II-004`）。
- **查询范围（读库，无写入）**：`inventory_lock` 与主键关联。
- **过滤口径**：`lockFlag=true` 且语义上为锁定中态。
- **组装规则**：布尔。
- **返回约束**：无锁时为 false。

#### 6.2.10 分页条件查询库存（`queryInventoryItemsByPage`）

- **业务意义**：分页检索库存行，服务 UI 列表。
- **边界说明**：复杂报表仍走QueryService（`BR-II-QRY-002`）。
- **查询范围（读库，无写入）**：主表分页查询。
- **过滤口径**：多条件动态查询（以落库与交互契约为准）；**`InventoryItemPageQuery.positiveQuantityOnly=true`** 时追加 **`quantity > 0`**（与库存明细「有库存」列表口径一致）；**`false`/未传** 不按数量收窄。应用层各单据库存批号选品由请求可选字段 **`positiveQuantityOnly`** 下推（**2026-07-15**；原请领硬编码 true 已改为前台传参）。
- **组装规则**：分页对象封装行列表与总数。
- **返回约束**：须含总数或下一页标记以落库与交互契约为准。

#### 6.2.11 加载未删除活跃行（`requireActiveRow`）

- **业务意义**：写侧前置加载；不存在或已删则失败。
- **边界说明**：只读但语义为**门禁**；通常由写侧编排先调。
- **查询范围（读库，无写入）**：主表单键。
- **过滤口径**：`isDeleted = false`（及等价删除标记）。
- **组装规则**：返回实体或抛业务异常。
- **返回约束**：失败时不进入写路径。

#### 6.2.12 批量汇总可用库存（`sumAvailableInventory`）

- **业务意义**：为医嘱开立/护士站执行等场景提供**多库位 × 多物品**可用量汇总（**AS-IP-NUR-004** 库存 Gate 库存侧读能力）；与发药侧 `listDispenseExeReserve` **分聚合**，由应用层扣减预占。
- **边界说明**：只读；内部委托 `findAvailableCatalogRowsForOrder` 按 FIFO 口径加载批次行后按库位+物品求和；**禁止**在库存 QueryService 内调用发药 QueryService。
- **查询范围（读库，无写入）**：`wkf_inventory_item` 可用批次行（同 **§6.2.3** 过滤）。
- **过滤口径**：`itemTable` 非空；`locationIds`、`itemIds` 非空；ACTIVE、未删、quantity>0、无锁定、效期谓词。
- **组装规则**：返回 `InventoryAvailSumModel`（`locationId`、`itemId`、`quantity` 汇总）。
- **返回约束**：入参缺表名或任一集合为空时返回空列表。

#### 6.2.13 按匹配键查找首条库存主键（`findFirstIdByInventoryMatchKey`）

- **业务意义**：五元组定位既有账面（入库加量、报溢、盘点）。供应商为空时不再因同批多行拒绝。
- **过滤口径**：ACTIVE、未删、未锁定。供应商为空且存在数量 &gt; 0 的行时，数量为 0 的历史行不参与；多行按 FEFO 取首条。**出库扣减不得只依赖本方法整笔砸行**，须 `allocateWarehouseMovement`。
#### 6.2.14 断言药品发放库存充足（`assertSufficientForMedicationDispense`）
#### 6.2.15 批量要求药品库存项主键（`requireInventoryItemIdForMedicationDispenseBatch`）

- **业务意义**：定位同库同品同批号下 FEFO **首条**未删库存主键（退药回补等须锚定单行）。
- **边界说明**：**发药实物扣减不得**经本方法整笔砸单行；发药有批号路径须 **`allocateAndDecrease(lotNumber)`** 同批拆分扣。
- **过滤口径**：未软删；同批多行时 FEFO 择首（可含 quantity=0 历史行——故发药扣减禁用本路径）。

#### 6.2.16 批量要求耗材库存项主键（`requireInventoryItemIdForDeviceDispenseBatch`）

（**§6.2.13～14、§6.2.16** 方法契约见 **`zxplat`** 实现与对应 AS。）

#### 6.2.17 全院品种库存数量汇总（`listHospitalWideProductQuantityAggregates`）

- **业务意义**：为 **AS-INV-WHS-004** 库存预警（**BR-INV-DTL-024**）按 **`item_table + item_id`** 汇总租户内各库房拆零数量合计；**不含库房维度**。
- **边界说明**：只读本聚合 **`wkf_inventory_item`**；**禁止** JOIN 产品定义表（包装换算比在应用层 **`application.inventory.inventorydetail.support`** 完成，B.6.4）。
- **查询范围（读库，无写入）**：未删全表行（**含 `quantity = 0`**）；全院合计为 0 亦出候选，供库存预警断货命中。
- **过滤口径**：默认排除逻辑删除；**不按** `location_id` 分组；**不**排除零数量行。
- **组装规则**：返回 **`InventoryProductQuantityAggregateModel`**（`itemTable`、`itemId`、`totalSplitQuantity`，可为 0）。
- **返回约束**：无库存行时返回空列表。

#### 6.2.18 效期预警批号行计数（`countExpireWarningInventoryRows`）

- **业务意义**：与库存明细 **`expireWithinDays`** / **`warningMode=expire`**（**BR-INV-DTL-023**）同口径，统计命中批号行数（供工作台 IF-12）。
- **边界说明**：只读；入参 **`WarehouseInventoryDetailPageQuery`** 须含 **`effectiveWarehouseIds`**（效期计数仍可按管辖库房收窄）与 **`expireWithinDays`**。
- **查询范围（读库，无写入）**：`wkf_inventory_item` + **`buildWarehouseInventoryDetailSpecification`** 等价谓词。
- **过滤口径**：与现库明细近效期筛选一致；默认 **`quantity > 0`**（由应用层 **`zeroStockOnly=false`** 写入）。
- **组装规则**：`count` 聚合。
- **返回约束**：非负整数。

> **预警 QueryModel**：**`InventoryProductAggregateKey`**（`itemTable` + `itemId`）供 **`lowStockWarningAggregateKeys`** 批号行 IN 过滤；列表展示仍受应用层 **BR-INV-DTL-016** 库房 Gate 约束。

#### 6.2.19 订货·最晚效期批近效品种键列表（`listProductKeysWhoseLatestLotNearExpiry`）

- **业务意义**：供 **AS-INV-WHS-001** 预警快捷选品（**BR-WHS-PO-014**）：按 **`item_table + item_id`**，在未删且 **`quantity > 0`** 的批号行中取 **`expiration_date` 最大（最晚）** 的一批；若该批剩余天数 ≤ 入参阈值，则返回该品种键。**≠** **§6.2.18** / 库存明细「任意近效批号行」口径。
- **边界说明**：只读本聚合；**禁止** JOIN 产品定义表（B.6.4）；应用层再按药品/耗材表名与启用态过滤。
- **查询范围（读库，无写入）**：`wkf_inventory_item` 未删除行；默认要求数量 &gt; 0。
- **过滤口径**：可选 **`itemTable`** 收窄；效期阈值使用入参 **`expireWithinDays`**（由应用层读系统设置后传入）。
- **组装规则**：返回 **`List<InventoryProductLatestExpiryNearModel>`**（`itemTable`、`itemId`、`latestExpirationDate`；去重品种）。
- **返回约束**：无命中返回空列表；`expiration_date` 为 null 的批不参与「最晚一批」竞选。

### 6.3 领域边界说明

- `InventoryItem` **只管理库存本身的事情**：新增库存项、数量增减、冻结/解锁、停供/恢复、先进先出分配、清零删除。
- **不关心库存变动的业务原因**——采购入库、调拨、报损、盘点等供应侧「为什么变」由 **SupplyRequest** / **SupplyDelivery** 承载；**常规药品发放、耗材发放**的发放事实由 **MedicationDispense** / **DeviceDispense** 承载（**SupplyDelivery 不承担常规发药动作**）。应用层编排调用库存域通用方法完成数量变更，口径见 **§1.4**、**§3**。
- 应用层通过**四元组**（`locationId + itemTable + itemId + lotNumber`，批号按归一化规则）查询判断是否已有匹配库存项：无则调用 `createInventoryItem` 新建，有则调用 `increaseQuantity` 累加。
- 库存快照（备份/历史数据）属于**QueryService 能力**（`InventorySnapshot`），不在 InventoryItem 领域服务中实现。
- 库存商品明细报表、库存流水统计等走QueryService（`InventoryLedger`），不在领域模型中展开联表查询。
- 盘点发起、盘点审批等业务流程由 SupplyRequest 域承载，库存域仅负责盘盈盘亏的数量调整。

---

## 7. 不变式


| 编号               | 不变式                            | 说明                                                                                                                                                      |
| ---------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BR-II-001`      | 库存扣减前必须校验库存充足（盘亏场景允许负数）        | 常规 `decreaseQuantity` 校验 `quantity ≥ decreaseQty`；盘亏场景通过 `allowNegative` 参数放行。**业务上** `quantity` 可为 **0**；`allowNegative=true` 时扣减后可**落库为负**（仍以最小单位计量）。 |
| `BR-II-002`      | 入库合并规则遵循五元组匹配                  | 按 `supplierId + lotNumber + locationId + itemTable + itemId` 匹配，命中走累加，未命中走新建；由应用层查询判断后调用对应方法                                                                         |
| `BR-II-003`      | 先进先出分配按效期升序                    | `expirationDate` 升序，`null` 视为最晚（`2099-12-31`），同效期按 `createdAt` 升序                                                                                       |
| `BR-II-004`      | 锁定期间库存不可被修改                    | 被锁定的库存项（`lockFlag=true`）不得增减数量；先进先出候选集在查询阶段排除仍存在锁定行                                                                                                     |
| `BR-II-005`      | 并发更新受乐观锁保护                     | `version` 不匹配时拒绝写入，由 JPA `@Version` 自动校验                                                                                                                |
| `BR-II-006`      | **五元组**在库表内必须唯一                  | 由 **§2.1** 目标 UNIQUE + 写路径归一化 **`lot_number`** 与必填 **`supplier_id`** 保证；同批号不同供应商允许并存多行                                                                 |
| `BR-II-007`      | 停供仅影响分配参与，不影响库存数量              | 停供后库存数量不变，但不参与先进先出分配；正常供应写入 **`ACTIVE`**，停供写入 **`RETIRED`**（均取 **`PublicationStatus#getCode()`**）                                                                                               |
| `BR-II-008`      | 逻辑删除的库存项不参与任何业务                | 所有查询默认过滤 `isDeleted = false`（`deleteFlag = '0'`）                                                                                                        |
| `BR-II-009`      | 清零删除须满足双重条件                    | `quantity = 0` 且应用层确认无未完成关联发放时才可标记逻辑删除                                                                                                                  |
| `PR-II-LOCK-001` | 同一库存项同一时刻只允许一条有效锁定             | `lockFlag = true` 的记录只允许一条，重复锁定应拒绝                                                                                                                      |
| `PR-II-LOCK-002` | 已解锁记录不可重复解锁                    | 解锁操作必须校验锁定记录存在且为锁定态（status ≠ 1）                                                                                                                         |
| `PR-II-LOCK-003` | 强制解锁须验证锁定记录存在                  | 锁定记录主键不存在时拒绝（业务码 `PR-II-LOCK-003`）；成功时 `lockFlag=false, status=2`，区别于正常解锁 `status=1`                                                                    |
| `PR-II-LOCK-004` | 锁定排序号自动分配                      | 同一业务单（`busNo`）内首条 `orderFlag=0`，后续为 `max(orderFlag)+1`                                                                                                  |
| `BR-II-QRY-001`  | 扣减前先查询先进先出可用库存并计算总量            | 先查再扣减                                                                                                                                                   |
| `BR-II-QRY-002`  | 库存商品明细报表优先走QueryService                 | 新功能避免写模型联表                                                                                                                                              |
| `BR-II-QRY-003`  | 先进先出查询仅包含 **`ACTIVE`**（**`inventory_status_enum`=`PublicationStatus.ACTIVE.getCode()`**）且未删除、数量>0 的记录 | 停供与已删除的库存不参与分配；**禁止**用数字串 **`"2"`** 等错误码导致可用量恒为 0                                                                                                                                          |
| `BR-II-QRY-004`  | 库存快照查询走QueryService                     | 快照的创建、分表路由、查询均在 InventorySnapshot QueryService中实现                                                                                                                |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合                   | 关联方式                 | 说明           |
| ----- | -------------------- | -------------------- | ------------ |
| 药品管理  | MedicationDefinition | `itemTable + itemId` | 药品类库存关联药品目录  |
| 耗材管理  | DeviceDefinition     | `itemTable + itemId` | 耗材类库存关联器材目录  |
| 位置管理  | Location             | `locationId`         | 库存所在仓库/药房/药库 |
| 供应商管理 | Supplier             | `supplierId`         | 库存品的供应商      |


### 8.2 下游影响


| 下游上下文 | 受影响聚合              | 影响方式     | 说明                                                                        |
| ----- | ------------------ | -------- | ------------------------------------------------------------------------- |
| 供应链管理 | SupplyDelivery     | 应用层编排    | 采购入库/调拨/报损/盘点通过应用层调用库存域通用方法                                               |
| 药房管理  | MedicationDispense | 应用层编排    | **常规药品发放**在发放域落事实，扣减由应用层编排调用 `allocateAndDecrease`（**不经 SupplyDelivery**） |
| 耗材管理  | DeviceDispense     | 应用层编排    | **常规耗材发放**同上；扣减由应用层编排调用 `allocateAndDecrease`                             |
| 采购管理  | SupplyRequest      | 事件订阅（预留） | 监听 `InventoryBelowThresholdEvent`，触发自动补货                                  |


### 8.3 引用规则

> **统一规则**：库存项通过 `itemTable + itemId` 多态引用药品/耗材定义，不直接持有对象引用。库房引用使用 `locationId`，供应商引用使用 `supplierId`。库存项不直接引用就诊；与就诊的关联由发放域（**MedicationDispense** / **DeviceDispense**）及供应编排（**SupplyDelivery** 等）在上游承载，口径见 **§1.4**。

### 8.4 事件流示意图

```text
SupplyDelivery                  InventoryItem                     MedicationDispense / DeviceDispense
    │                               │                                         │
    │── 进货/入库/出库/退库/损益       │                                         │
    │   createSupplyDelivery ───────>│                                         │
    │                               │── InventoryItemCreatedEvent（新建）       │
    │                               │   / InventoryQuantityIncreasedEvent（增）│
    │                               │   / InventoryQuantityDecreasedEvent（减）│
    │                               │                                         │
    │── 调拨（双侧）                  │                                         │
    │   createTransferDelivery ─────>│                                         │
    │                               │── decreaseQuantity（源）                  │
    │                               │── increaseQuantity（目）                  │
    │                               │                                         │
    │── 盘点差异行                    │                                         │
    │   createStocktakingDelivery ──>│                                         │
    │                               │── increaseQuantity（盘盈）                │
    │                               │   / decreaseQuantity（盘亏，allowNegative）│
    │                               │                                         │
    │── 药品汇总（COMPLETED 后）       │                                         │
    │   allocateAndDecrease ────────>│                                         │
    │                               │── InventoryAllocatedEvent（FIFO 扣减）   │
    │                               │                                         │
    │                               │<─────── 常规发药/耗材发放编排 ─────────────│
    │                               │── InventoryAllocatedEvent              │
    │                               │                                         │
    │                               │── InventoryBelowThresholdEvent（预留）──> SupplyRequest（自动补货）
```

> **说明**：**DeviceDispense** 与 **MedicationDispense** 同属发放域，常规扣减均指向 `allocateAndDecrease`，**不经 SupplyDelivery**（见 **§1.4**）。药品汇总单（type 27）经 SupplyDelivery 完成后走 `allocateAndDecrease`，与门诊/住院常规发药链路互斥，避免重复扣减。

---

## 9. 补充

### 9.1 入库合并判断流程

应用层在接收到入库指令后，先通过**四元组**查询是否已有匹配库存行：

1. **查询匹配**：按 `locationId + itemTable + itemId + lotNumber`（**`lot_number`** 与 **§2.1** / 读侧 **`normalizeLotNumberForAvailabilityMatch`** 同口径）查询（`findMatchingInventory` / 等价规格）
2. **有匹配**：调用 `increaseQuantity` 累加库存数量
3. **无匹配**：调用 `createInventoryItem` 新建库存行

此判断逻辑在应用层完成，库存域只提供纯粹的新建与累加能力。

### 9.2 先进先出分配规则

先进先出分配适用于出库、**药品发放**、**耗材发放**等需要跨批次自动扣减的场景（常规路径由发放域编排调用本域 `allocateAndDecrease`，见 **§1.4**）：

1. **查询候选集**：`isDeleted = false`、`quantity > 0`、`inventory_status_enum` = **`PublicationStatus.ACTIVE.getCode()`**（**`"ACTIVE"`**）、未锁定（无 `lockFlag = true` 的 InventoryLock 记录）、未过期（`expirationDate > now` 或 `null`）
2. **排序规则**：`expirationDate` 升序（`null` 视为 `2099-12-31`），同效期按 `createdAt` 升序
3. **逐批次扣减**：从第一批次开始扣减，直到满足需求数量
4. **不足拒绝**：总可用量不足时拒绝整个分配请求

### 9.3 多库房管理说明

中国 HIS 通常采用三级库房结构：


| 层级   | 说明            | 示例        |
| ---- | ------------- | --------- |
| 药库   | 一级库房，集中采购入库   | 西药库、中药库   |
| 药房   | 二级库房，面向临床发药   | 门诊药房、住院药房 |
| 科室药柜 | 三级库房（可选），科室自管 | ICU 备药柜   |


库存项通过 `locationId` 关联到具体库房层级。调拨操作由应用层编排：源库房 `decreaseQuantity` + 目标库房 `increaseQuantity`（或 `createInventoryItem`）。

> **相关枚举**：`InventoryType` 枚举定义仓库类型——`INVENTORY(1, "仓库")`、`PHARMACY(2, "药房")`，位于 `InventoryType`。

### 9.4 中国 HIS 特色业务说明


| 特色场景     | 说明          | 库存域处理方式                                      |
| -------- | ----------- | -------------------------------------------- |
| 药品拆零     | 大包装拆为小包装销售  | 通过计量单位编码统一为最小单位管理                            |
| 中药饮片按克管理 | 中药饮片按克数计量   | `BigDecimal` 精度支持克级管理                        |
| 高值耗材追溯   | 需追溯到单品（UDI） | 通过追溯码（`traceNo`）与包装层级（`packagingLevels`）字段支持 |
| 近效期预警    | 距效期 N 天内告警（批号行） | 库存明细 **`expireWithinDays`** / **`warningMode=expire`**（**AS-INV-WHS-004 BR-INV-DTL-023**）；`InventoryBelowThresholdEvent` 仍预留 |
| 库存预警     | 全院品种够不够采（采购视角） | **与库房/批号无关**；合计含 0=断货；目录 **`item_min/max_quantity`** 优先，未维护回落 **`inventoryMinPackageQuantityWarning`**；**`listHospitalWideProductQuantityAggregates`**（**AS-INV-WHS-004 BR-INV-DTL-024**） |
| 订货快捷·最晚效期批 | 品种最晚效期批已近效 | **`listProductKeysWhoseLatestLotNearExpiry`**（**AS-INV-WHS-001 BR-WHS-PO-014**）；**不**用于库存明细工作台 |


### 9.5 库存明细快照（DOC 域 · 交叉引用）

> **权威文档**：**`DM-DOC-001-InventoryItemSnapshot-库存明细快照.md`**（表 **`doc_inventory_item_snapshot`**；实现 SDD：**`DomainModel-SDD/DM-DOC-001-InventoryItemSnapshot-库存明细快照-SDD.md`**）。
>
> **与 InventoryItem 边界**：快照为 **DOC 域 append-only 静表**；写入由 **AS-INV-WHS-004 IF-10** 触发；**不参与**本聚合写路径、FIFO 或数量变更。备份读（IF-04/05/07/11）只读静表，**禁止**联结 **ChargeItemDefinition** 重算四价（**AS BR-INV-DTL-018**）。
>
> **批次单号**：库列 **`bus_no`**（接口 **`backupBusNo`**）= **`ABU`+yyyyMMdd+2 位序号**。

---

### 9.6 库存快照与 QueryService 边界（摘要）

- 库存明细快照归属 **DOC 域** **`DocInventoryItemSnapshotQueryService`** / **`DocInventoryItemSnapshotWriter`**（见 **DM-DOC-001**），**不在** **InventoryItem** 领域服务中实现
- 库存商品明细报表、库存流水统计等走 **InventoryLedger** QueryService，不在本 DM 展开
- 盘点发起、盘点审批由 **SupplyRequest** 域承载；库存域仅负责盘盈盘亏数量调整

---

### 9.7 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v1.0.10** | **2026-10-18** | **代码先行回写**：库房出库/调拨/退库/退货/领用源库改走 `allocateWarehouseMovement`（同批多供应商 FEFO 拆分，跳过数量 0 行，允许过期批）；`findFirstIdByInventoryMatchKey` 供应商为空时不再抛「须指定供应商」 |
| **v1.0.9** | **2026-10-14** | **代码先行回写**：§1.1 / §6.1.4 明确发药有批号走 `allocateAndDecrease(lotNumber)` **同批多供应商 FEFO 拆分扣**；§6.2.15 标明发药扣减禁用「择首条整笔砸」；候选供应态与读侧一致为 **ACTIVE** |
| **v1.0.7** | **2026-07-15** | **§6.2.19** 订货快捷最晚效期批近效品种键；**§9.4** 增订货快捷口径（对齐 **AS-INV-WHS-001 BR-WHS-PO-014**） |
| **v1.0.6** | **2026-07-15** | **§9.4** 库存预警：目录警戒线优先于全局包装阈值（对齐 **AS-INV-WHS-004 BR-INV-DTL-024**） |
| **v1.0.5** | **2026-07-03** | **§6.2.17～18**：库存明细预警读能力（全院品种汇总 **`listHospitalWideProductQuantityAggregates`**、效期计数 **`countExpireWarningInventoryRows`**）；**§9.4** 近效期/库存预警与 **AS-INV-WHS-004** 对齐 |
| **v1.0.4** | **2026-06-28** | **§9.5** 收简：快照权威迁至 **DM-DOC-001**（**`doc_inventory_item_snapshot`**）；删除原 **`wkf_inventory_item_snapshot`** 字段表 |
| **v1.0.3** | **2026-06-28** | ~~§9.5 wkf 快照草案~~（已由 **DM-DOC-001** 替代） |
| v1.0.3 | 2026-07-15 | **§6.2.10**：明确 **`positiveQuantityOnly`** 由各单据选品请求可选下推（非必传；true=`quantity > 0`） |
| v1.0.2 | 2026-05-29 | **§1.1**：取价口径对齐 **`DM-ADM-010` §1.5**——库房供应读锚仅 **`PURCHASE` + `LOT_NUMBER_COST`** |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `InventoryItemDomainService`/`InventoryItemQueryService` 白名单对齐（12写+16读，补充批量增删、库存断言等） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`InventoryItemDomainService`

| 方法 |
|------|
| `createInventoryItem` |
| `increaseQuantity` |
| `increaseQuantities` |
| `decreaseQuantity` |
| `decreaseQuantitiesMerged` |
| `allocateAndDecrease` |
| `allocateWarehouseMovement` |
| `lockInventory` |
| `unlockByBusNo` |
| `forceUnlock` |
| `stopSupply` |
| `resumeSupply` |
| `deleteInventoryOnZeroQuantity` |

### QueryService：`InventoryItemQueryService`

| 方法 |
|------|
| `getInventoryItemById` |
| `listInventoryItemEntitiesByLotLocationItem` |
| `findByIdIn` |
| `findAvailableInventories` |
| `findActiveInventoryRowsForPriceAdjustmentLockPreview` |
| `calculateTotalAvailableInventory` |
| `sumTotalAvailableInventoryByItemIds` |
| `sumAvailableInventory` |
| `findAvailableCatalogRowsForOrder` |
| `findAvailableCatalogRowsByItemIds` |
| `findCatalogInventoryRowsIncludingZeroForLocations` |
| `normalizeLotNumberForAvailabilityMatch` |
| `findMatchingInventory` |
| `findInventoryItemByItemInfo` |
| `getBatchInventoryByWarehouse` |
| `listDistinctItemTablesByLocationId` |
| `listByLocationId` |
| `checkVersion` |
| `isLocked` |
| `findActiveLockBusNo` |
| `queryInventoryItemsByPage` |
| `requireActiveRow` |
| `listHospitalWideProductQuantityAggregates` |
| `countExpireWarningInventoryRows` |
| `listProductKeysWhoseLatestLotNearExpiry` |
| `findFirstIdByInventoryMatchKey` |
| `assertSufficientForMedicationDispense` |
| `assertSufficientForDeviceDispenseAtLocationLot` |
| `assertSufficientForMedicationDispenseGivenReserve` |
| `lockInventoryRowsForCatalogAvailability` |
| `requireInventoryItemIdForMedicationDispenseBatch` |
| `requireInventoryItemIdForDeviceDispenseBatch` |
| `listHospitalWideActiveInventoryRows` |
| `sumQuantityByItemTableAndItemIdAndLotNumber` |

