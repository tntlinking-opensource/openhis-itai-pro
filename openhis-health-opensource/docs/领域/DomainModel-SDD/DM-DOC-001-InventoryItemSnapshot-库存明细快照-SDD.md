# DM-DOC-001 InventoryItemSnapshot 库存明细快照 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-DOC-001-InventoryItemSnapshot-库存明细快照.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat/whale-zxplat-domain`  
> **限界上下文**：Document（DOC）  
> **聚合**：DocInventoryItemSnapshot（代码实体 `DocInventoryItemSnapshotEntity`）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-DOC-001 v1.0 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（`document` 包） |
| 包规则 `com.zxplat.domain.document` | 满足 | 全小写子包 `inventoryitemsnapshot` |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 写 1 方法；读 5 方法 |
| DM 第 7 章 BR | 满足 | §6 |
| DM 第 5 章事件 | 满足 | §7（0 事件） |

**与 DM 显式差异**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 写侧入口 | — | App **IF-10** 组装 Command 后调用 **`DocInventoryItemSnapshotDomainService#batchAppendSnapshots`**（**禁止** App 直写 Entity 散落） |
| 备份库房/供应商名 | DM §6.4 允许读侧补名 | App **`#mapSnapshotEntityToBackupListRow`** 可批量解析 **Location/Supplier**；**禁止** ChargeItem 联结 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **`doc_inventory_item_snapshot`** append-only 实体、仓储、DomainService 与 QueryService，使下一会话可 **一次性** 生成 DOC 域实现并通过编译。
- 写路径经 **`DocInventoryItemSnapshotDomainService#batchAppendSnapshots`** 批量 INSERT；读路径供 **AS-INV-WHS-004** IF-04～07、IF-11 使用。
- 列集与 **AS-SDD §3.11.2**、**DM §3.1** 完全一致：**四价 + code 事实**，**不含货位**、**不含 `*_text` 翻译列**。

### 1.2 边界

- **在内**：实体字段全集、批量 append 写契约、备份读 QueryService、日级存在性判定、distinct **`bus_no`**。
- **在外**：现库 IF-02 装配、**`bus_no` 序号生成**（**SequenceService**）、备份 DTO `*Label` / 库存展示串拼装、Quartz **DISALLOW** 并发策略。
- **跨域仅端口声明**：写路径读 **InventoryItem** + 定价；备份读**禁止** **ChargeItemDefinition**；可选只读 **Location/Supplier** 补名称。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.document.inventoryitemsnapshot`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DocInventoryItemSnapshotEntity` |
| `command` | `BatchAppendDocInventoryItemSnapshotCommand`、`DocInventoryItemSnapshotRowCommand` |
| `repository` | `DocInventoryItemSnapshotRepository` |
| `service` | `DocInventoryItemSnapshotDomainService`、`DocInventoryItemSnapshotQueryService` |
| `queries` | `DocInventoryItemSnapshotPageQuery` |
| `queries.model` | `DocInventoryItemSnapshotRowQueryModel` |

**存储口径（DM 优先）**：`*_enum` → **`String`（枚举 code）**；`*_code` → **`String`（字典 code）**；`chrgitm_lv` → **`Integer`**。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键与 DM 一致（`inventoryItemId`、`warehouseId` 等） |
| 时间 | `snapshotTime`、`productionDate`、`expirationDate`、`createdAt` → **`LocalDateTime`**（DB `timestamptz`） |
| 金额 / 数量 | `BigDecimal`；数量 `numeric(18,2)`、金额 `numeric(18,4)`（与 DM §3.1 / Flyway 一致） |
| `inventory_status_enum` | `String`（**PublicationStatus** code） |
| 软删 | `Boolean isDeleted`，继承审计基类 |
| 实体 | `@Entity`，显式 `@Table(name = "doc_inventory_item_snapshot")` / `@Column` |
| 仓储 | `interface … extends JpaRepository<…, Long>`，**禁止** `@Query` |
| 分页 | `Pageable`；备份列表默认 **`pyStr ASC NULLS LAST`**（与现库 **BR-INV-DTL-006** 一致） |
| 事务 | DomainService / QueryService **不** 声明 `@Transactional`；随 AppService UoW |
| 事件 | **不发**域事件 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `doc_inventory_item_snapshot` | `DocInventoryItemSnapshotEntity` | append-only 静表行 |

### 4.2 聚合根 `DocInventoryItemSnapshotEntity`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 Snowflake |
| `tenantId` | `Long` | 是 | 租户 |
| `busNo` | `String` | 是 | **ABU**+yyyyMMdd+2 位；**BR-DOC-SNAP-001** |
| `snapshotTime` | `LocalDateTime` | 是 | 批次快照时刻 |
| `inventoryItemId` | `Long` | 是 | 源 **InventoryItem**；**UK** 成员 |
| `createdAt` | `LocalDateTime` | 是 | 审计 |
| `createdBy` | `Long` | 否 | 任务操作者 |
| `isDeleted` | `Boolean` | 是 | 默认 false |
| `itemTable` | `String` | 是 | 物品定义表名 |
| `itemId` | `Long` | 是 | 物品定义主键 |
| `chargeItemDefinitionId` | `Long` | 否 | 写入时收费项 |
| `warehouseId` | `Long` | 是 | 库房；**不含货位** |
| `supplierId` | `Long` | 是 | 供应商 |
| `productBusNo` | `String` | 否 | 项目编码 |
| `name` | `String` | 是 | 项目名称 |
| `merchandiseName` | `String` | 否 | 商品名称 |
| `totalVolume` | `String` | 否 | 规格 |
| `manufacturer` | `String` | 否 | 厂家/产地 |
| `approvalNumber` | `String` | 否 | 批准文号 |
| `ybNo` | `String` | 否 | 医保码 |
| `traceNo` | `String` | 否 | 追溯码 |
| `pyStr` | `String` | 否 | 拼音；排序/关键字 |
| `wbStr` | `String` | 否 | 五笔；关键字 |
| `lotNumber` | `String` | 是 | 生产批号 |
| `quantity` | `BigDecimal` | 是 | 拆零最小单位数量 |
| `packageUnitRatio` | `BigDecimal` | 否 | 拆零↔包装倍数 |
| `packageUnitCode` | `String` | 否 | 包装单位字典 code |
| `lotNumberCostAmount` | `BigDecimal` | 否 | 批号进价（包装整包） |
| `lotNumberPriceAmount` | `BigDecimal` | 否 | 批号售价（包装整包） |
| `lineCostTotal` | `BigDecimal` | 否 | 进价行总额（冻结） |
| `linePriceTotal` | `BigDecimal` | 否 | 售价行总额（冻结） |
| `productionDate` | `LocalDateTime` | 否 | 生产日期 |
| `expirationDate` | `LocalDateTime` | 否 | 效期 |
| `remainingDays` | `Integer` | 否 | 写入时刻剩余天数 |
| `inventoryStatusEnum` | `String` | 是 | **PublicationStatus** code |
| `chrgitmLv` | `Integer` | 否 | 医保等级 |
| `medicationCategoryCode` | `String` | 否 | 药品分类 code |
| `deviceCategoryCode` | `String` | 否 | 耗材分类 code |
| `doseFormCode` | `String` | 否 | 剂型 code |

**不变式方法**：无状态机；**append-only**（**BR-DOC-SNAP-003**）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

`DocInventoryItemSnapshotRepository extends JpaRepository<DocInventoryItemSnapshotEntity, Long>`

**说明**：软删由框架过滤；**禁止**在接口名强制 `…AndIsDeletedFalse` 冗余（除非基类未统一）。

**批量写**：DomainService 使用 **`repository.saveAll(entities)`**；Flyway **UK** `(tenant_id, bus_no, inventory_item_id)` 防重复行。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `DocInventoryItemSnapshotPageQuery`（对应 `pageSnapshotRows` / `listSnapshotRowsForSum` / `listSnapshotRowsForEx旧命名（已废止） 入参）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `tenantId` | `Long` | Gate | 必填 |
| `busNo` | `String` | Request.`backupBusNo` | 必填（App **R-009** 默认当天） |
| `keyword` | `String` | Request | 备份关键字；**不含批号**（**BR-INV-DTL-010**） |
| `warehouseIds` | `List<Long>` | Request | 多选 IN |
| `supplierIds` | `List<Long>` | Request | 多选 IN |
| `medicationCategoryCodes` | `List<String>` | Request | |
| `deviceCategoryCodes` | `List<String>` | Request | |
| `inventoryStatusEnums` | `List<String>` | Request | |
| `chrgitmLvs` | `List<Integer>` | Request | |
| `zeroStockFlag` | `Boolean` | Request | true→quantity=0；false→quantity>0 |
| `remainingDaysLe` | `Integer` | Request | **BR-INV-DTL-003** |
| `itemTable` | `String` | Request | 药/耗类型筛选 |

#### 4.6.2 `DocInventoryItemSnapshotRowQueryModel`（对应 `pageSnapshotRows` / `listSnapshotRowsForSum` / `listSnapshotRowsForEx旧命名（已废止） 出参）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | 快照行 PK（内部） |
| `inventoryItemId` | `Long` | `inventory_item_id` | 备份 DTO **`id`** 语义 |
| `busNo` | `String` | `bus_no` | |
| `snapshotTime` | `LocalDateTime` | `snapshot_time` | |
| `itemTable` | `String` | `item_table` | |
| `itemId` | `Long` | `item_id` | |
| `chargeItemDefinitionId` | `Long` | `charge_item_definition_id` | |
| `warehouseId` | `Long` | `warehouse_id` | |
| `supplierId` | `Long` | `supplier_id` | |
| `productBusNo` | `String` | `product_bus_no` | |
| `name` | `String` | `name` | |
| `merchandiseName` | `String` | `merchandise_name` | |
| `totalVolume` | `String` | `total_volume` | |
| `manufacturer` | `String` | `manufacturer` | |
| `approvalNumber` | `String` | `approval_number` | |
| `ybNo` | `String` | `yb_no` | |
| `traceNo` | `String` | `trace_no` | |
| `pyStr` | `String` | `py_str` | |
| `wbStr` | `String` | `wb_str` | |
| `lotNumber` | `String` | `lot_number` | |
| `quantity` | `BigDecimal` | `quantity` | |
| `packageUnitRatio` | `BigDecimal` | `package_unit_ratio` | |
| `packageUnitCode` | `String` | `package_unit_code` | |
| `lotNumberCostAmount` | `BigDecimal` | `lot_number_cost_amount` | → DTO `lotCostPrice` |
| `lotNumberPriceAmount` | `BigDecimal` | `lot_number_price_amount` | → DTO `lotSalePrice` |
| `lineCostTotal` | `BigDecimal` | `line_cost_total` | |
| `linePriceTotal` | `BigDecimal` | `line_price_total` | |
| `productionDate` | `LocalDateTime` | `production_date` | App 格式化为 DTO String |
| `expirationDate` | `LocalDateTime` | `expiration_date` | 同上 |
| `remainingDays` | `Integer` | `remaining_days` | |
| `inventoryStatusEnum` | `String` | `inventory_status_enum` | |
| `chrgitmLv` | `Integer` | `chrgitm_lv` | |
| `medicationCategoryCode` | `String` | `medication_category_code` | |
| `deviceCategoryCode` | `String` | `device_category_code` | |
| `doseFormCode` | `String` | `dose_form_code` | |

#### 4.6.3 `existsSuccessfulBackupForDate` / `findDistinctBusNoByTenant`

| 方法 | 出参 | 说明 |
| ---- | ---- | ---- |
| `existsSuccessfulBackupForDate` | `boolean` | 无独立 QueryModel；谓词：`tenant_id` + `bus_no LIKE 'ABU'+yyyyMMdd+'%'` + `is_deleted=false` |
| `findDistinctBusNoByTenant` | `List<String>` | 无独立 QueryModel；`SELECT DISTINCT bus_no … ORDER BY bus_no DESC` |

#### 4.6.4 IF-10 写入映射（`InventoryDetailListRowDto` → `DocInventoryItemSnapshotRowCommand`）

| DTO 字段 | Command / Entity 字段 | 说明 |
| -------- | --------------------- | ---- |
| `id` | `inventoryItemId` | 源库存项 |
| — | `busNo` / `snapshotTime` | App 批次参数 |
| `itemTable` … `doseFormCode` | 同名 camelCase | 事实列 1:1 |
| `lotCostPrice` | `lotNumberCostAmount` | 四价映射 |
| `lotSalePrice` | `lotNumberPriceAmount` | |
| `lineCostTotal` | `lineCostTotal` | |
| `linePriceTotal` | `linePriceTotal` | |
| `quantity`（`@JsonIgnore` 源） | `quantity` | 装配链路内部量 |
| `productionDate` / `expirationDate`（String） | `productionDate` / `expirationDate`（`LocalDateTime`） | App **`#parseSnapshotDateTime`** 统一解析 |
| `inventoryStatusLabel` 等 | **不落库** | 读侧翻译 |
| `warehouseName` / `supplierName` | **不落库** | 备份读可选 QS 补名 |
| `packageStockText` / `splitStockText` | **不落库** | 读侧按 quantity+ratio 拼装 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DocInventoryItemSnapshotDomainService`

#### 5.1.1 `batchAppendSnapshots(BatchAppendDocInventoryItemSnapshotCommand cmd)` → `int`

**命令文件**：`BatchAppendDocInventoryItemSnapshotCommand.java`（内含 `List<DocInventoryItemSnapshotRowCommand> rows`）

**`BatchAppendDocInventoryItemSnapshotCommand` 字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `tenantId` | `Long` | 非空 |
| `busNo` | `String` | trim 非空；**BR-DOC-SNAP-001** |
| `snapshotTime` | `LocalDateTime` | 非空 |
| `createdBy` | `Long` | 可空；系统任务 |
| `rows` | `List<DocInventoryItemSnapshotRowCommand>` | 非空；允许空列表（no-op） |

**`DocInventoryItemSnapshotRowCommand` 字段全集**（与 **§4.2** 业务列一致，**不含** `id`/`tenantId`/`busNo`/`snapshotTime`/审计）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `inventoryItemId` | `Long` | 非空 |
| `itemTable` | `String` | 非空 |
| `itemId` | `Long` | 非空 |
| `chargeItemDefinitionId` | `Long` | 可空 |
| `warehouseId` | `Long` | 非空 |
| `supplierId` | `Long` | 非空 |
| `productBusNo` | `String` | 可空 |
| `name` | `String` | 非空 |
| `merchandiseName` | `String` | 可空 |
| `totalVolume` | `String` | 可空 |
| `manufacturer` | `String` | 可空 |
| `approvalNumber` | `String` | 可空 |
| `ybNo` | `String` | 可空 |
| `traceNo` | `String` | 可空 |
| `pyStr` | `String` | 可空 |
| `wbStr` | `String` | 可空 |
| `lotNumber` | `String` | 非空 |
| `quantity` | `BigDecimal` | 非空 |
| `packageUnitRatio` | `BigDecimal` | 可空 |
| `packageUnitCode` | `String` | 可空 |
| `lotNumberCostAmount` | `BigDecimal` | 可空 |
| `lotNumberPriceAmount` | `BigDecimal` | 可空 |
| `lineCostTotal` | `BigDecimal` | 可空 |
| `linePriceTotal` | `BigDecimal` | 可空 |
| `productionDate` | `LocalDateTime` | 可空 |
| `expirationDate` | `LocalDateTime` | 可空 |
| `remainingDays` | `Integer` | 可空 |
| `inventoryStatusEnum` | `String` | 非空 |
| `chrgitmLv` | `Integer` | 可空 |
| `medicationCategoryCode` | `String` | 可空 |
| `deviceCategoryCode` | `String` | 可空 |
| `doseFormCode` | `String` | 可空 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | —（append-only） |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | 依赖 DB **UK**；重复 `(tenantId, busNo, inventoryItemId)` → 抛 **`ValidationException`** 或包装 **`DataIntegrityViolationException`** |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-DOC-SNAP-APPEND-001 |

**方法契约摘要**：前置 **`rows`** 已由 App 完成 IF-02 同等装配与 **§4.6.4** 映射；持久化 **`saveAll`** 批量 INSERT（**写库点 1**）；返回写入行数；失败向上抛（IF-10 → **`WHS_DTL_211`**）。**注释锚点**：校验 tenant/busNo → 组装 Entity 列表 → repository.saveAll。

---

### 5.2 读侧 — `DocInventoryItemSnapshotQueryService`（`domain.service`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `existsSuccessfulBackupForDate` | `tenantId`, `LocalDate` | `boolean` | `busNo LIKE 'ABU'+yyyyMMdd+'%'` 存在即 true | TC-DOC-SNAP-EXISTS-001 |
| `findDistinctBusNoByTenant` | `tenantId` | `List<String>` | DISTINCT + ORDER BY busNo DESC | TC-DOC-SNAP-BUS-001 |
| `pageSnapshotRows` | `DocInventoryItemSnapshotPageQuery`, `Pageable` | `Page<DocInventoryItemSnapshotRowQueryModel>` | Criteria/Specification；关键字 OR name/py/wb/productBusNo/merchandiseName（**无 lotNumber**）；排序 pyStr ASC NULLS LAST | TC-DOC-SNAP-PAGE-001 |
| `listSnapshotRowsForSum` | `query`, `maxRows` | `List<DocInventoryItemSnapshotRowQueryModel>` | 同谓词；LIMIT maxRows | TC-DOC-SNAP-SUM-001 |
| `listSnapshotRowsForEx旧命名（已废止） | `query`, `maxRows` | `List<DocInventoryItemSnapshotRowQueryModel>` | 同 sum | TC-DOC-SNAP-EXP-001 |

**读方法注释锚点（统一）**：构建查询条件（含软删、`busNo` 必填）→ 单次 SQL/仓储调用并完成分页或 limit → 组装 QueryModel（**无写库**）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-DOC-SNAP-001** | App Gate + Command：`busNo` trim 非空；格式由 **SequenceService** 保证 |
| **BR-DOC-SNAP-002** | Flyway **UK** `(tenant_id, bus_no, inventory_item_id)` |
| **BR-DOC-SNAP-003** | DomainService **仅** insert；**禁止** update/delete API |
| **BR-DOC-SNAP-004** | Command 字段与 **§4.2** 对齐；App **`#toDocSnapshotPersistRows`** 单点映射 |
| **BR-DOC-SNAP-005** | Entity 只存 code 列；App 备份读 **`CodeSystemQueryService`** 仅补 Label |
| **BR-DOC-SNAP-006** | 实体/Flyway **无** location_store 列 |
| **BR-DOC-SNAP-007** | IF-10 Pre **`existsSuccessfulBackupForDate`** → skip |
| **BR-DOC-SNAP-008** | 备份 Mapper 四价直读表列；**禁止** `#applyLotPricingAndTotals` |
| **AS BR-INV-DTL-010** | `pageSnapshotRows` 关键字谓词排除 `lot_number` |
| **AS BR-INV-DTL-020** | 同上 exists 方法 |
| **AS BR-INV-DTL-021** | **非本层**；ScheduledJob **DISALLOW** |

---

## 7. 事件契约与触发规则（DM §5）

首期 **不发** 域事件；下表为空集（与 DM §5 一致）。

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |

**发布时机**：无。

---

## 8. 删除与可见性契约

- append-only：**禁止**业务 update/delete API。
- 软删仅框架层；查询默认 `is_deleted=false`。
- 租户隔离：读写必须 `tenant_id = 当前租户`。
- 备份 DTO **`id`** 对外映射 **`inventoryItemId`**，**非**快照行 `id`。

---

## 9. 框架优先适配规则

- 实体继承 `BaseAuditedEntity`（或项目 DOC 域等价基类）。
- Repository / QueryService **禁止** `@Query`；读侧用 **Criteria API** 或 **JpaSpecificationExecutor**（团队统一一种）。
- DomainService **不**注入 `ICurrentUser`；`createdBy` 由 App 填入 Command。
- **`bus_no` 生成**在 **application**（**SequenceService#nextBusNo**）；DomainService **不**生成序号。
- IF-10 分页循环读现库在 **App**；DomainService 单次 `saveAll` 批次内全量或 App 分 chunk 多次调用 DomainService（**同一 busNo**）。
- Flyway 文件：`V100__doc_inventory_item_snapshot.sql`（与 **§4.2** 同步）。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] `DocInventoryItemSnapshotEntity` 与 DM §3.1、Flyway 列一致
- [ ] **UK** `(tenant_id, bus_no, inventory_item_id)` 与索引 **§2.1**
- [ ] `BatchAppendDocInventoryItemSnapshotCommand` + `DocInventoryItemSnapshotRowCommand` 与 §5.1.1 一致
- [ ] `DocInventoryItemSnapshotDomainService#batchAppendSnapshots` 单写入口
- [ ] `DocInventoryItemSnapshotQueryService` 五读方法与 §5.2 一致
- [ ] `existsSuccessfulBackupForDate` 按 **ABU+yyyyMMdd** 前缀判定
- [ ] 备份分页关键字**不含批号**
- [ ] App **`#toDocSnapshotPersistRows`** 与 **§4.6.4** 一致；**不落库** Label/StockText
- [ ] IF-04 Mapper 四价字段名与现库 DTO 对齐（`lotCostPrice` 等）
- [ ] **`InventoryDetailBackupListRowDto.id`** = `inventoryItemId`
- [ ] 编译 `zxplat-domain` + `health-application` 通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 本 SDD + DM-DOC-001 v1.0 + **AS-SDD §3.11.2** + Flyway 脚本。

### 11.2 DoD

- `zxplat-domain` 编译通过；DomainService append 与 QueryService 单测可跑。
- `InventoryWarehouseInventoryDetailAppService` IF-10 经 DomainService 写库（**禁止** App 直注 `EntityManager`）。
- IF-04～07、IF-11 经 QueryService 读；备份四价与现库改价后仍不变（**BR-DOC-SNAP-008**）。
- 规范合规摘要：注释锚点、P0 rg、Repository 禁 @Query。

### 11.3 失败处理

- 仍引用 **`wkf_inventory_item_snapshot`** → 改表名 **`doc_inventory_item_snapshot`** 并对齐 DM-DOC-001。
- 备份读仍调用 `#applyLotPricingAndTotals` → 改为静表直映射。
- 同日重复快照 → 检查 Pre **`existsSuccessfulBackupForDate`** 与 Job **DISALLOW**。

---

## 定稿检查清单（可打勾）

- [x] §1～§11 金骨架齐全
- [x] §5.1.1 写方法 1 节
- [x] §4.2 字段与 DM §3.1 对齐
- [x] §7 0 事件与 DM §5 对齐
- [x] §4.6.1～4.6.3 QueryModel / 读契约字段表
- [x] 无 Gap / 待补充措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98，可进入代码会话** |

---

**本 SDD 正文结束。**
