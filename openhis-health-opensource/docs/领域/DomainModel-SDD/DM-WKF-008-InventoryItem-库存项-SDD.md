# DM-WKF-008 InventoryItem 库存项 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-WKF-008-InventoryItem-库存项.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：InventoryItem（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-WKF-008 |
| 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 | 满足 | `com.zxplat.domain.workflow.inventoryitem` |
| 固定技术口径 | 满足 | 见 §9 |
| DM 写/读白名单 | 满足 | §5 覆盖 |
| DM BR | 满足 | §6 |
| DM 事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime` |
| `inventory_status_enum` 类型 | §3.1 曾写 Integer | 与 **B.10.8.1** 及落库一致：**`String`（`PublicationStatus#getCode()`）** |
| 库存行自然键 | DM 曾写四元组（供应商不进键） | **五元组** `location_id+item_table+item_id+lot_number+supplier_id`（见 DM **§2.1** v1.0.8）；批号价键仍为实例+批号 |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.inventoryitem` |
| 批次分配策略 | §6.1.3 主记录口径 | 以 `allocated_batch_id` 非空标识批次已分配，quantity>0 且 available=true 标识待领用 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化库存项作为“数量与状态事实”聚合，不承载业务原因叙事。
- 明确**五元组**业务唯一、入库合并、FIFO 分配、锁定解锁、停供恢复、清零删除规则。
- 与 SupplyDelivery、MedicationDispense、DeviceDispense 的编排边界清晰化。

### 1.2 边界

- **在内**：库存新增、增减、分配、锁定、停供、删除。
- **在外**：采购/调拨/发放业务事实写库（上游聚合承载）。
- **跨域仅端口声明**：提供通用库存能力供应用层编排调用。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.inventoryitem`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `InventoryItem`,`InventoryLock` |
| `valueobject` | `BatchAllocationResult`,`InventoryMatchKey` |
| `command` | 每写方法一个命令 |
| `repository` | 主仓储、锁仓储 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.inventoryitem` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间 | `LocalDateTime` |
| `*_enum`（**供应状态**） | **`String`**（**`PublicationStatus#getCode()`**） |
| 其它 `*_enum`（锁状态等） | `Integer`（若 DM 仍为整型码，以 DM 为准） |
| 数量 | `BigDecimal` |
| 软删 | `isDeleted/deleteFlag` |
| 乐观锁 | `version` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_inventory_item` | `InventoryItem` | 库存项主表；**业务唯一**：`(location_id, item_table, item_id, lot_number)` 须 **UNIQUE**（与 DM **§2.1** 一致） |
| `inventory_lock` | `InventoryLock` | 锁记录表 |

### 4.2 聚合根 `InventoryItem`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `itemTable` | `String` | 是 | 物品来源表 |
| `itemId` | `Long` | 是 | 物品定义 |
| `name` | `String` | 是 | 物品名称 |
| `inventoryStatusEnum` | `String` | 是 | **`ACTIVE` / `RETIRED`**（**`PublicationStatus#getCode()`**） |
| `supplierId` | `Long` | 是 | 供应商 |
| `unitCode` | `String` | 是 | 单位 |
| `quantity` | `BigDecimal` | 是 | 库存量 |
| `lotNumber` | `String` | 是 | 批号 |
| `expirationDate` | `LocalDateTime` | 否 | 效期 |
| `locationId` | `Long` | 是 | 库房 |
| `locationStoreId` | `Long` | 否 | 货位 |
| `traceNo` | `String` | 否 | 追溯码 |
| `version` | `Integer` | 系统 | 乐观锁 |

### 4.3 子实体 `InventoryLock`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 主键 |
| `inventoryId` | `Long` | 是 | 库存项 |
| `lockFlag` | `Boolean` | 是 | 是否锁定 |
| `status` | `Integer` | 是 | 锁状态 |
| `busNo` | `String` | 是 | 业务单号 |
| `orderFlag` | `Integer` | 是 | 同单顺序 |

### 4.4 值对象契约

| 值对象 | 说明 |
| ------ | ---- |
| `BatchAllocationResult` | 分批扣减结果 |
| `InventoryMatchKey` | **五元组**匹配键（`locationId,itemTable,itemId,lotNumber,supplierId`；批号归一化与读侧一致） |

### 4.5 仓储接口

- `InventoryItemRepository`, `InventoryLockRepository`
- 必备签名：按主键、**五元组**、可用候选、锁状态查询

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `InventoryItemModel`（`getInventoryItemById`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `quantity` | `BigDecimal` | 主表 | 库存量 |
| `inventoryStatusEnum` | `String` | 主表 | 状态码 |

#### 4.6.2 `AvailableInventoryModel`（`findAvailableInventories`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `lotNumber` | `String` | 主表 | 批号 |
| `expirationDate` | `LocalDateTime` | 主表 | 效期 |
| `quantity` | `BigDecimal` | 主表 | 可用量 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `InventoryItemDomainService`

#### 5.1.1 `createInventoryItem(CreateInventoryItemCommand cmd)` → `InventoryItem`
**命令文件**：`CreateInventoryItemCommand.java`  
**字段全集**：外层 **`operatorId`**（**`BatchCommand`** 侧）；项命令 **`CreateInventoryItemItemCommand`**：`itemTable,itemId,name,supplierId,unitCode,quantity,lotNumber,locationId,locationStoreId,expirationDate,productionDate,categoryCode,pyStr,wbStr`

> **装配说明（应用层常见来源，非域不变式）**：供应驱动建单行可将 **`wkf_supply_request.start_time`/`end_time`** 映射为 **`productionDate`/`expirationDate`**；**`categoryCode`/`pyStr`/`wbStr`** 可由应用 **`ProductDefinitionBatchLoader`** 解析后冗余写入（解析结果为 `null` 或字段空白时对应命令字段可缺省）。供应行未维护效期列时 **`productionDate`/`expirationDate`** 可为 **`null`**（与 **DM-WKF-008** §3.1 可空列一致）。**`increaseQuantity`** 路径不更新上述冗余列。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCreatable` |
| `statusTransitionMethod` | `initActive` |
| `repositoryChecks` | **五元组**新建前校验（含 DB UNIQUE 失败转业务语义） |
| `eventPublishAnchor` | `InventoryItemCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-001` |

---

#### 5.1.2 `increaseQuantity(IncreaseQuantityCommand cmd)` → `InventoryItem`
**命令文件**：`IncreaseQuantityCommand.java`
**字段全集**：`id,increaseQty`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertIncreaseAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `requireActiveRow` + 未锁定 |
| `eventPublishAnchor` | `InventoryQuantityIncreasedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-002` |

---

#### 5.1.3 `decreaseQuantity(DecreaseQuantityCommand cmd)` → `InventoryItem`
**命令文件**：`DecreaseQuantityCommand.java`
**字段全集**：`id,decreaseQty,allowNegative`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDecreaseAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `requireActiveRow` + 未锁定 |
| `eventPublishAnchor` | `InventoryQuantityDecreasedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-003` |

---

#### 5.1.4 `allocateAndDecrease(AllocateAndDecreaseCommand cmd)` → `List<BatchAllocationResult>`
**命令文件**：`AllocateAndDecreaseCommand.java`
**字段全集**：外层 `operatorId`；项命令 `itemTable,itemId,requiredQty,locationId(optional),lotNumber(optional),supplierId(optional),includeExpired(optional),literalLot(optional)`  
- `lotNumber` 空且未 `literalLot`：跨批号 FEFO；非空或 `literalLot`：同批可用行 FEFO **拆分扣**。`supplierId` 非空只扣该供应商。发药 `includeExpired` 为空则排除过期批；库房 `allocateWarehouseMovement` 置 `includeExpired` 与 `literalLot`（对齐 DM §6.1.4 / v1.0.10）
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertAllocatable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | FEFO 候选（ACTIVE、quantity&gt;0、未过期、未锁定；可选批号收窄） |
| `eventPublishAnchor` | `InventoryAllocatedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-004` |

---

#### 5.1.5 `lockInventory(LockInventoryCommand cmd)` → `InventoryLock`
**命令文件**：`LockInventoryCommand.java`
**字段全集**：`inventoryId,busNo`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertLockable` |
| `statusTransitionMethod` | `lockOn` |
| `repositoryChecks` | 同库存唯一有效锁 |
| `eventPublishAnchor` | `InventoryLockedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-005` |

---

#### 5.1.6 `unlockByBusNo(UnlockByBusNoCommand cmd)` → `void`
**命令文件**：`UnlockByBusNoCommand.java`
**字段全集**：`busNo`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertUnlockableByBusNo` |
| `statusTransitionMethod` | `lockToUnlocked` |
| `repositoryChecks` | 锁记录存在校验 |
| `eventPublishAnchor` | `InventoryUnlockedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-006` |

---

#### 5.1.7 `forceUnlock(ForceUnlockCommand cmd)` → `void`
**命令文件**：`ForceUnlockCommand.java`
**字段全集**：`lockId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertForceUnlockable` |
| `statusTransitionMethod` | `lockToForceUnlocked` |
| `repositoryChecks` | 锁记录存在 |
| `eventPublishAnchor` | `InventoryUnlockedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-007` |

---

#### 5.1.8 `stopSupply(StopSupplyCommand cmd)` → `InventoryItem`
**命令文件**：`StopSupplyCommand.java`
**字段全集**：`id`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStopSupplyAllowed` |
| `statusTransitionMethod` | `activeToRetired` |
| `repositoryChecks` | `requireActiveRow` |
| `eventPublishAnchor` | `InventorySupplyStoppedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-008` |

---

#### 5.1.9 `resumeSupply(ResumeSupplyCommand cmd)` → `InventoryItem`
**命令文件**：`ResumeSupplyCommand.java`
**字段全集**：`id`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertResumeSupplyAllowed` |
| `statusTransitionMethod` | `retiredToActive` |
| `repositoryChecks` | `requireActiveRow` |
| `eventPublishAnchor` | `InventorySupplyResumedEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-009` |

---

#### 5.1.10 `deleteInventoryOnZeroQuantity(DeleteInventoryOnZeroQuantityCommand cmd)` → `InventoryItem`
**命令文件**：`DeleteInventoryOnZeroQuantityCommand.java`
**字段全集**：`id,applicationConfirmedNoPendingDelivery`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertZeroAndNoPending` |
| `statusTransitionMethod` | `logicalDelete` |
| `repositoryChecks` | `requireActiveRow` |
| `eventPublishAnchor` | `InventoryDeletedOnZeroEvent` |
| `unitTestCaseId` | `TC-WKF-II-CMD-010` |

### 5.2 读侧 — `InventoryItemQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `getInventoryItemById` | `id` | `InventoryItemModel` | 单条查询 | TC-WKF-II-QRY-001 |
| `findByIdIn` | `idList` | `List<InventoryItemModel>` | 批量查询 | TC-WKF-II-QRY-002 |
| `findAvailableInventories` | `itemTable,itemId,locationId` | `List<AvailableInventoryModel>` | FIFO候选 | TC-WKF-II-QRY-003 |
| `calculateTotalAvailableInventory` | `itemTable,itemId,locationId` | `BigDecimal` | 可用总量 | TC-WKF-II-QRY-004 |
| `findMatchingInventory` | `InventoryMatchKey` | `InventoryItemModel` | **五元组**命中 | TC-WKF-II-QRY-005 |
| `findInventoryItemByItemInfo` | `query` | `List<InventoryItemModel>` | 条件查询 | TC-WKF-II-QRY-006 |
| `getBatchInventoryByWarehouse` | `warehouseId,itemTable` | `List<InventoryItemModel>` | 盘点批量加载 | TC-WKF-II-QRY-007 |
| `checkVersion` | `id,version` | `Boolean` | 乐观锁校验 | TC-WKF-II-QRY-008 |
| `isLocked` | `inventoryId` | `Boolean` | 锁状态查询 | TC-WKF-II-QRY-009 |
| `queryInventoryItemsByPage` | `query,pageable` | `Page<InventoryItemModel>` | 分页查询 | TC-WKF-II-QRY-010 |
| `requireActiveRow` | `id` | `InventoryItemModel` | 写前强加载 | TC-WKF-II-QRY-011 |
| `findAvailableCatalogRowsForOrder` | `itemTable,itemIds,locationIds` | `List<InventoryCatalogAvailabilityRowModel>` | 医嘱开立目录：多物品 id×多候选库位一次规格查询 + 锁定剔除（IF-DOR-11） | TC-WKF-II-QRY-012 |
| `listHospitalWideProductQuantityAggregates` | — | `List<InventoryProductQuantityAggregateModel>` | 全院 **`item_table+item_id`** 拆零合计（**AS-INV-WHS-004** 库存预警 **BR-INV-DTL-024**） | TC-WKF-II-QRY-013 |
| `countExpireWarningInventoryRows` | `WarehouseInventoryDetailPageQuery` | `long` | 效期预警批号行 count（**BR-INV-DTL-023** / IF-12） | TC-WKF-II-QRY-014 |
| `listProductKeysWhoseLatestLotNearExpiry` | `expireWithinDays`；可选 `itemTable` | `List<InventoryProductLatestExpiryNearModel>` | 订货快捷：品种**最晚效期批**近效（含 `latestExpirationDate`；**AS-INV-WHS-001 BR-WHS-PO-014**；**≠** 明细任意近效批） | TC-WKF-II-QRY-015 |

**QueryModel（预警）**

| 类型 | 包路径 | 字段 |
| ---- | ------ | ---- |
| `InventoryProductAggregateKey` | `queries` | `itemTable`, `itemId` |
| `InventoryProductQuantityAggregateModel` | `queries.model` | `itemTable`, `itemId`, `totalSplitQuantity` |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-II-001~009 | 扣减校验、**五元组**唯一与合并、FIFO、锁、并发、停供、逻辑删除、清零删除 |
| PR-II-LOCK-001~004 | 锁唯一、解锁约束、强解锁、排序号 |
| BR-II-QRY-001~004 | 先查后扣、报表QueryService、候选过滤、快照QueryService |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `InventoryItemCreatedEvent` | `com.zxplat.domain.share.events.workflow.inventoryitem` | `id,itemId,lotNumber,locationId,quantity` | 5.1.1 |
| `InventoryQuantityIncreasedEvent` | 同上 | `id,increaseQty,afterQty` | 5.1.2 |
| `InventoryQuantityDecreasedEvent` | 同上 | `id,decreaseQty,afterQty` | 5.1.3 |
| `InventoryAllocatedEvent` | 同上 | `allocationResults,totalAllocated` | 5.1.4 |
| `InventoryLockedEvent` | 同上 | `inventoryId,busNo` | 5.1.5 |
| `InventoryUnlockedEvent` | 同上 | `inventoryId,busNo,unlockType` | 5.1.6/5.1.7 |
| `InventorySupplyStoppedEvent` | 同上 | `id` | 5.1.8 |
| `InventorySupplyResumedEvent` | 同上 | `id` | 5.1.9 |
| `InventoryDeletedOnZeroEvent` | 同上 | `id,itemId,lotNumber` | 5.1.10 |
| `InventoryBelowThresholdEvent` | 同上 | `id,currentQty,threshold` | 预留（读侧/任务） |

---

## 8. 删除与可见性契约

- 清零删除为逻辑删除，默认查询排除删除行。
- 已锁定库存不可增减。
- 停供不改变数量，仅影响可分配候选。

---

## 9. 框架优先适配规则

- 乐观锁冲突统一按业务异常处理。
- 复杂报表与快照走QueryService，不扩展写模型联表。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 10 个写方法小节，覆盖新增/增减/分配/锁定/停供/删除/启用等操作
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 12 项与 DM §6.2 对齐，含可用量检索、批次查询与 IF-DOR-11 目录批量可用行
- §6 BR 规则映射覆盖 FIFO、锁定、**五元组**唯一与合并规则，均可追溯到方法实现
- §7 事件 6 条与 DM §5.1 全量对齐（含增减/锁定/解锁/停供/启用等库存事件）
- 批次分配策略明确：以 `allocated_batch_id` 非空标识批次已分配，quantity>0 且 available=true 标识待领用
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-WKF-008，联动 DM-WKF-005/006/007/MED-002

### 11.2 DoD

- 通用库存能力与供应侧/发放侧编排边界清晰。
- 关键不变式均具备测试绑定。

### 11.3 失败处理

- 若出现重复扣减或跨域边界混淆，回文档修订。

---

## 定稿检查清单（可打勾）

- [x] 骨架完整
- [x] 写侧 10 方法覆盖
- [x] 读侧 11 方法覆盖
- [x] BR/事件映射齐全

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 14 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **96（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| **2026-07-15** | **§5.2** 增补 **`listProductKeysWhoseLatestLotNearExpiry`**（返回 **`InventoryProductLatestExpiryNearModel`**）；对齐 **DM-WKF-008 v1.0.7** / **AS-INV-WHS-001**；领域代码已落地 |
| **2026-07-15** | 交叉引用 **AS-INV-WHS-004 v0.28**：**BR-INV-DTL-024** 库存预警目录警戒线优先于全局包装阈值（判定仍在应用层 Filter） |
| **2026-07-03** | **§5.2** 增补 **`listHospitalWideProductQuantityAggregates`** / **`countExpireWarningInventoryRows`** 与预警 QueryModel；对齐 **AS-INV-WHS-004 v0.25**（全院库存预警、默认阈值 30） |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
