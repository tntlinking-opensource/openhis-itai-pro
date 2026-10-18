# 库存明细 · 应用服务详细设计（SDD）

> 编码：**AS-INV-WHS-004-SDD**  
> 版本：**v0.38** | 日期：**2026-10-06** | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药库 **库存明细** 应用服务（现库只读 + **定时快照写** + 备份只读 + **InventoryItem** 供应状态写） |
| 边界 | 编排 **IF-INV-DTL-01～12**；**不**创建 **SupplyRequest**；**不**执行数量增减（除 **`inventory_status_enum`**）；现库定价 **`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`**；**备份读禁止再联定价**（**AS BR-018**）；**不含货位** |
| 编排权威 | `AS-INV-WHS-004-库存明细.md`（**v0.34**）§2、§4、§5、§7 |
| 方法权威 | `DM-WKF-008` §6.1.8/9、**§6.2.17～18**（**DM-SDD** §5.2）；`DM-ADM-010`；`InventoryItemQueryService`；**`DocInventoryItemSnapshotQueryService`** / **`DocInventoryItemSnapshotDomainService`**（**DM-DOC-001**）；ZXPlat **`ScheduledJobConcurrentPolicy#DISALLOW`** |
| 实现阶段口径 | **已上线**：IF-01～12（含 **IF-04/05/07/10/11** 备份读 + 定时快照 + **IF-12** 预警）；包 **`inventory/inventorydetail`**；Flyway **`V101__doc_inventory_item_snapshot.sql`** |

### 1.2 术语口径

| 严格对齐 | IF **IF-INV-DTL-01～12** 与 AS 母表一致 |

| 项 | 约定 |
| --- | --- |
| 快照表（**DOC 域**） | **`doc_inventory_item_snapshot`**；批次列 **`bus_no`**；接口 **`backupBusNo`↔`bus_no`**；**DM**：**`DM-DOC-001`** · **DomainModel-SDD/DM-DOC-001-*** |
| 备份读 | 静表 **四价 + code + 主数据文本**；**禁止**定价联结；**`*Label`** 读侧字典翻译 |
| 现库对客 | **四价**：`lotCostPrice`/`lotSalePrice`/`lineCostTotal`/`linePriceTotal`（**包装单位**）；**禁止**重复 `display*UnitPrice/Name` |
| 快照写 | IF-10：现库 **`#enrichRows`** → **`Mapper#toSnapshotRowCommand`** → **`DocInventoryItemSnapshotDomainService#batchAppendSnapshots`**（**§3.11.2** 事实列） |
| 日级幂等 | IF-10 Pre：租户+自然日是否已有 **`ABU`+yyyyMMdd** 之 **`bus_no`** |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **INV-DTL-SDD-R-001** | 现库双链取价：**`findLotNumberCostLinesGroupedByLotBatchKey`** / **`findLotNumberPriceLinesGroupedByLotBatchKey`**；**禁止**行级 N+1 |
| **INV-DTL-SDD-R-002** | 同键多行保留首次映射 |
| **INV-DTL-SDD-R-003** | 拆零换算比非法 → **`WHS_DTL_210`** |
| **INV-DTL-SDD-R-004** | 停供/恢复 → **`stopSupply`/`resumeSupply`** |
| **INV-DTL-SDD-R-005** | **`py_str` ASC NULLS LAST** |
| **INV-DTL-SDD-R-006** | 对客单价 = **`lotCostPrice`/`lotSalePrice`**（= 批号 **LOT_NUMBER_* .amount**，包装整包价）；单位名 = **`packageUnitName`** |
| **INV-DTL-SDD-R-007** | **备份读**：静表 **`lot_number_cost_amount`/`lot_number_price_amount`/`line_*_total`** + code；**禁止** `#applyLotPricingAndTotals`；**`*Label`** 可字典补全 |
| **INV-DTL-SDD-R-008** | **IF-10**：分页 **`pageNo=1..N`**、`pageSize` 固定（如 **1000**），直至 **`totalPages`** 耗尽；**禁止**单页 **9999** 截断 |
| **INV-DTL-SDD-R-009** | **`backupBusNo` 未传**：`#resolveBackupBusNo(tenantId)` → 当自然日最新 **`ABU`** 批次；无则空结果 |
| **INV-DTL-SDD-R-010** | **拆零换算比**：`#resolveSplitToPackageRatio` 仅取 **`BAND_PACKAGE`** / **`package_unit_ratio`**；**禁止**以 **`purchase_unit_ratio`** 或库存单位参与行总额；进/售价 **`#computeLineTotal`** **共用同一 ratio** |
| **INV-DTL-SDD-R-010a** | **行总额公式（BR-MON-PKG-001）**：`MonetaryAmountSupport.computeSplitQuantityLineTotal` = **满包×包装价 + 零头（包装价×余量/ratio 单步）**；**禁止** `perSplit = packagePrice/ratio` 后再乘余量或全量 |
| **INV-DTL-SDD-R-011** | **关键字预解析**：IF-02/03/06 Pre 调用 **`InventoryCatalogFilterSupport#enrichKeywordDefinitionIds`**（**MedicationDefinitionQueryService#listIdsByInventoryDetailKeyword** / **DeviceDefinitionQueryService#listIdsByInventoryDetailKeyword**）；inventory Criteria **禁止**跨表 JOIN 定义表 |
| **INV-DTL-SDD-R-012** | **IF-10 现库 0 行**：记录日志并 **return**，**不**生成静表行、**不**消耗 **`bus_no`**；日级幂等 **`BR-020`** 仍依赖后续有库存时首次成功快照 |
| **INV-DTL-SDD-R-013** | **导出进价刻度（AS BR-INV-DTL-029）**：`lotCostPrice` / `lineCostTotal` `@Excel(scale=CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE, roundingMode=DOWN)`；**零售价两列仍** `CHARGE_MONETARY_DISPLAY_SCALE` + `HALF_UP`。列表 API 仍 `MonetaryAmountSupport#scaleCalc`（6 位） |

## 2. 代码结构

```text
com/openhis/health/application/inventory/inventorydetail/
  service/
    InventoryWarehouseInventoryDetailAppService.java   # IF-01～11；@SchedulableJob IF-10
  request/
    LoadInventoryDetailFormInitRequest.java
    PageInventoryDetailCurrentRequest.java
    SumInventoryDetailCurrentRequest.java
    PageInventoryDetailBackupRequest.java
    SumInventoryDetailBackupRequest.java
    ExportInventoryDetailCurrentRequest.java
    ExportInventoryDetailBackupRequest.java
    ListInventoryDetailBackupBusNoRequest.java
    RetireInventoryItemsRequest.java
    ResumeInventoryItemsRequest.java
  response/
    LoadInventoryDetailFormInitResponse.java
    PageInventoryDetailCurrentResponse.java
    SumInventoryDetailCurrentResponse.java
    PageInventoryDetailBackupResponse.java
    SumInventoryDetailBackupResponse.java
    ExportInventoryDetailCurrentResponse.java
    ExportInventoryDetailBackupResponse.java
    ListInventoryDetailBackupBusNoResponse.java
    RetireInventoryItemsResponse.java
    ResumeInventoryItemsResponse.java
  dto/
    InventoryDetailListRowDto.java
    InventoryDetailBackupListRowDto.java
    InventoryDetailCurrentExportRowDto.java
    InventoryDetailBackupExportRowDto.java
  mapper/
    InventoryWarehouseInventoryDetailMapper.java
    InventoryWarehouseInventoryDetailMapperImpl.java
```

> 包名：`com.zxplat.domain.application.inventory.inventorydetail`（**`inventory/` 直属**，与 **`warehouse/`**、**`pharmacy/`** 同级，非 `warehouse/inventorydetail`）。  
> **Domain 落点（非 application 包）**：**`DocInventoryItemSnapshotEntity`** / **`DocInventoryItemSnapshotRepository`** / **`DocInventoryItemSnapshotQueryService`** / **`DocInventoryItemSnapshotDomainService`** → `zxplat-domain/.../document/inventoryitemsnapshot/`；Flyway **`V101__doc_inventory_item_snapshot.sql`**。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentPractitionerResolver` | Gate / IF-08/09 写路径 operatorId |
| `CurrentManagedLocationIdsResolver` | IF-01 管辖药库+药房+科室药房；IF-02/03/06/04/05/07 库房 Gate |
| `CodeSystemQueryService` | IF-01 **`PublicationStatus`** 供应状态下拉 |
| `LocationQueryService` | IF-01 Option；IF-02/04 enrich 库房名 |
| `SupplierQueryService` | IF-01 Option；IF-02/04 enrich 供应商名 |
| `InventoryCatalogFiltersupport | IF-02/03/06 **`enrichKeywordDefinitionIds`**（**禁止** IF-04/05/07） |
| `InventoryLowStockWarningFiltersupport | IF-02/03/06/12 库存预警 **`lowStockWarningAggregateKeys`**（**`#resolveLowStockProductKeys`**：目录警戒线优先，未维护回落全局包装阈值；**`application.inventory.inventorydetail.support`**） |
| `InventoryWarningSettingsupport | IF-01/02/03/06/12 读取效期/库存预警**全局**阈值（系统设置；目录未维护警戒线时回落） |
| `InventoryWarningRoleGate` | IF-12 库管/药师可见性门控 |
| `TraceNumberQueryService` | IF-13 **`listInStockTraceNumbersByInventoryItemKey`** |
| `InventoryItemQueryService` | IF-02/03/06/10/12 现库读（经 Mapper **`selectCurrentDetailPage`** 或 **`countExpireWarningInventoryRows`** / **`listHospitalWideProductQuantityAggregates`**） |
| `DocInventoryItemSnapshotQueryService` | IF-04/05/07/11 静表读；IF-10 Pre **`existsSuccessfulBackupForDate`** |
| `DocInventoryItemSnapshotDomainService` | IF-10 **`batchAppendSnapshots`** |
| `InventoryItemDomainService` | IF-08/09 |
| `ProductDefinitionBatchLoader` | IF-02/03/06/10 现库 enrich；IF-04/07 备份展示 enrich（**只读**，禁止定价） |
| `ProductDefinitionSelectionTerminologysupport | IF-02/04 **`*Label`** / 单位名 |
| `ChargeItemDefinitionQueryService` | **仅** IF-02/03/06/10（**禁止** IF-04/05/07） |
| `InventoryWarehouseInventoryDetailMapper` | 请求 ↔ 领域 Query / 快照 Command / DTO 映射 |
| `TraceNumberQueryService` | **`listInStockTraceNumbersByInventoryItemKey`** | **13** |
| `BusinessSequenceService` | IF-10 **`getSeqByDay(ABU, 2)`** |
| `SystemOperatorResolver` | IF-10 后台 **`createdBy`** |
| `@SchedulableJob`（ZXPlat Job） | IF-10 处理器 key **`inventoryWarehouseInventoryDetail/executeInventoryDetailSnapshot`**；Cron/启停由 zxplat-job 配置；**`concurrentPolicy=DISALLOW`** |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `InventoryItemQueryService` | **`queryWarehouseInventoryDetailPage`/`TopN`**；**`listHospitalWideProductQuantityAggregates`**；**`countExpireWarningInventoryRows`** | 02、03、06、**10**、**12** |
| `MedicationDefinitionQueryService` | **`listIdsByInventoryDetailKeyword`** | 02、03、06（经 **InventoryCatalogFilterSupport**） |
| `DeviceDefinitionQueryService` | **`listIdsByInventoryDetailKeyword`** | 02、03、06（经 **InventoryCatalogFilterSupport**） |
| `InventoryItemDomainService` | **`stopSupply`/`resumeSupply`** | 08、09 |
| `ChargeItemDefinitionQueryService` | 批号定价批量查询 | 02、03、06、**10** |
| `DocInventoryItemSnapshotQueryService` | **`pageSnapshotRows`** / **`listSnapshotRowsForSum`** / **`listSnapshotRowsForEx旧命名（已废止）** / **`findDistinctBusNoByTenant`** / **`existsSuccessfulBackupForDate`** | 04～07、**10 Pre**、11 |
| `DocInventoryItemSnapshotDomainService` | **`batchAppendSnapshots`** | **10** |
| `InventoryWarehouseInventoryDetailMapper` | **`toSnapshotPageQuery`** / **`toBackupListRowDto`** / **`toSnapshotRowCommand`** / **`selectCurrentDetailPage`** | 04、05、07、**10** |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | 说明 |
| --- | --- | --- |
| 备份单号前缀 | **`AggregateTypeConstants#INVENTORY_DETAIL_SNAPSHOT_BACKUP`**（`"ABU"`） | IF-10 |
| 序号位数 | **`BusinessSequenceConstants#INVENTORY_DETAIL_SNAPSHOT_BUS_NO_SEQ_LENGTH`**（**2**） | IF-10 |
| 任务并发 | **`ScheduledJobConcurrentPolicy#DISALLOW`** | IF-10 运维注册 |
| 错误码 | **`WHS_DTL_101`～`210`、`211`、`212`、**`213`** | 对齐 AS §5 |

### 2.4 白名单双向一致性复核

§2.2 各方法均在 §3 出现；§3 Cmd 不引用白名单外方法。

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | HTTP |
| --- | --- | --- |
| IF-01 | `loadInventoryDetailFormInit` | `POST /api/app/inventoryWarehouseInventoryDetail/loadInventoryDetailFormInit` |
| IF-02 | `pageInventoryDetailCurrent` | `POST .../pageInventoryDetailCurrent` |
| IF-03 | `sumInventoryDetailCurrent` | `POST .../sumInventoryDetailCurrent` |
| IF-04 | `pageInventoryDetailBackup` | `POST .../pageInventoryDetailBackup` |
| IF-05 | `sumInventoryDetailBackup` | `POST .../sumInventoryDetailBackup` |
| IF-06 | `exportInventoryDetailCurrent` | `POST .../exportInventoryDetailCurrent` |
| IF-07 | `exportInventoryDetailBackup` | `POST .../exportInventoryDetailBackup` |
| IF-08 | `retireInventoryItems` | `POST .../retireInventoryItems` |
| IF-09 | `resumeInventoryItems` | `POST .../resumeInventoryItems` |
| IF-11 | `listInventoryDetailBackupBusNo` | `POST .../listInventoryDetailBackupBusNo` |
| IF-12 | `loadInventoryWarningWorkbenchSummary` | `POST .../loadInventoryWarningWorkbenchSummary` |
| IF-13 | `listInStockTraceNumbersByInventoryItem` | `POST .../listInStockTraceNumbersByInventoryItem` |

| IF | 应用服务方法 | 说明 |
| --- | --- | --- |
| IF-10 | **`InventoryWarehouseInventoryDetailAppService#executeInventoryDetailSnapshot`**（`@SchedulableJob("库存备份")`） | **非 HTTP**；处理器 key **`inventoryWarehouseInventoryDetail/executeInventoryDetailSnapshot`**；**`concurrentPolicy=DISALLOW`** |

---

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数 | 不再调用后续步骤 |
| **Pre** | 只读：分页/汇总/导出装载、定价行、倍数、**BR** 校验 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写或静表批量 INSERT | 事务回滚见 §5 |
| **Asm** | Mapper 装配 DTO、排序、Excel | — |

---

### 3.2 IF-INV-DTL-01 `loadInventoryDetailFormInit`

#### Request 字段表（`LoadInventoryDetailFormInitRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 租户由 **`CurrentPractitionerResolver#requireOperatorId`** Gate |

#### Response 字段表（`LoadInventoryDetailFormInitResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chrgitmLvOptions` | `List<OptionDto>` | 医保等级 |
| `inventoryStatusOptions` | `List<OptionDto>` | **`CodeSystemQueryService#getOptionListByEnum(PublicationStatus)`**，仅 **ACTIVE/INACTIVE** |
| `medicationCategoryOptions` | `List<OptionDto>` | 药品类型 |
| `deviceCategoryOptions` | `List<OptionDto>` | 耗材类型 |
| `inventoryRangeOptions` | `List<OptionDto>` | 零库存/有库存（**排除**「无限制」） |
| `warehouseOptions` | `List<OptionDto>` | 管辖范围内**启用态药库+药房+科室药房** |
| `supplierOptions` | `List<OptionDto>` | 供应商全量列表 |
| `expireWarningDays` | `Integer` | 系统设置效期预警天数（默认 180） |
| `minPackageQuantityWarning` | `Integer` | 系统设置库存预警**全局**包装阈值（默认 **30**；目录未维护警戒线时回落） |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | **`WHS_DTL_101`** |
| 2 | Pre | **`TerminologyOptionLoader`**（医保等级/药耗分类/库存范围） | 失败 **`WHS_DTL_201`** |
| 3 | Pre | **`CodeSystemQueryService#getOptionListByEnum(PublicationStatus)`** | 过滤 **ACTIVE/INACTIVE** |
| 4 | Pre | **`LocationQueryService#listAllActiveDepartmentPharmacies`** + **`CurrentManagedLocationIdsResolver`** | **`#buildWarehouseOptions`**（药库+药房+科室药房） |
| 5 | Pre | **`SupplierQueryService#listSuppliers`** | **`#buildSupplierOptions`** |
| 6 | Pre | **`InventoryWarningSettingsupport** | 写入 **`expireWarningDays`** / **`minPackageQuantityWarning`** |
| 7 | Asm | 组装 Response | — |

---

### 3.3 IF-INV-DTL-02 `pageInventoryDetailCurrent`

#### Request 字段表（`PageInventoryDetailCurrentRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `Integer` | 是 | 1-based |
| `pageSize` | `Integer` | 是 | |
| `keyword` | `String` | 否 | 名称/批号/拼音/五笔；**及**编码/商品名（**R-011** 预解析 **`item_id`**） |
| `itemTable` | `String` | 否 | 项目类型：`med_medication_definition`=药品 / `adm_device_definition`=耗材；空=全部 |
| `medicationCategoryCodes` | `List<String>` | 否 | **BR-001** |
| `deviceCategoryCodes` | `List<String>` | 否 | |
| `warehouseIds` | `List<Long>` | 否 | **BR-016** |
| `supplierIds` | `List<Long>` | 否 | |
| `chrgitmLvList` | `List<Integer>` | 否 | |
| `keyDrugType` | `String` | 否 | 重点药类型（`KeyDrugType`：`BASIC`/`NATIONAL_MONITOR`/`CENTRAL_PURCHASE`）；选中即筛对应药典 flag=`true`；**仅药品行**；由 `InventoryCatalogFilter` 预解析 `item_id`（替代原 `basicFlag` 是/否） |
| `zeroStockOnly` | `Boolean` | 否 | **BR-002** |
| `expireWithinDays` | `Integer` | 否 | **BR-003** |
| `inventoryStatusEnums` | `List<String>` | 否 | |
| `warningMode` | `String` | 否 | **`expire`** / **`lowStock`**；见 §3.13（**BR-INV-DTL-023/024**） |

#### Response（`PageInventoryDetailCurrentResponse`）

`pageNo` / `pageSize` / `totalElements` / `totalPages` / `rows: List<InventoryDetailListRowDto>`

#### DTO（`InventoryDetailListRowDto`）

**不含货位**。**事实 + 少量拼装展示**（**禁止**重复定价/单位字段）。

| 分组 | 字段 |
| --- | --- |
| 标识 | `id`, `itemTable`, `itemId`, `chargeItemDefinitionId` |
| 主数据 | `productBusNo`, `name`, `merchandiseName`, `totalVolume`, `manufacturer`, `approvalNumber`, `ybNo`, **`traceNo`（移除，v0.30）**, `pyStr`, `wbStr`, `lotNumber` |
| 位置 | `warehouseId`, `warehouseName`, `supplierId`, `supplierName` |
| 效期 | `productionDate`, `expirationDate`, `remainingDays` |
| code | `inventoryStatusEnum`, `chrgitmLv`, `medicationCategoryCode`, `deviceCategoryCode`, `doseFormCode`, `packageUnitCode` |
| Label（读侧拼） | `inventoryStatusLabel`, `chrgitmLvLabel`, `categoryLabel`, `doseFormLabel` |
| 库存 | `packageUnitRatio`, `packageUnitName`, `splitUnitName`, `packageStockText`, `splitStockText`, **`packageSplitRatioText`**（`1盒/36片`；同单位×1 为 `1支`；非临床 `1片x100`） |
| **四价** | **`lotCostPrice`**, **`lotSalePrice`**, **`lineCostTotal`**, **`linePriceTotal`** |

**`@JsonIgnore`**：`quantity`, `unitCode`, `unitBands`, `packageUnitRatioFallback`

#### `#applyLotPricingAndTotals`（**R-006/R-010**）

| 步骤 | 说明 |
| --- | --- |
| 取价 | 批号键 → **`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`** 整包 amount |
| 对客 | **`lotCostPrice`/`lotSalePrice`** ← amount；**`lineCostTotal`/`linePriceTotal`** ← `#computeLineTotal`（**R-010a** / 满包×包装价 + 零头单步） |
| 展示 | **`#applyListDisplayFields`**：`packageStockText`/`splitStockText`/`packageSplitRatioText`；**`packageUnitName`** 来自包装槽 |

失败码：**`WHS_DTL_202`/`210`**

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 权限/租户 | **`WHS_DTL_101`** |
| 2 | Pre | **`#resolveGatedInventoryLocationIds`** + 请求多选求交 | **BR-016**（药库+药房+科室药房白名单） |
| 3 | Pre | **`InventoryCatalogFilterSupport#enrich*`** | **R-011** |
| 4 | Pre | **`#applyWarningModeToQuery`** | **BR-INV-DTL-023/024**（**warningMode**） |
| 5 | Pre | **`Mapper#selectCurrentDetailPage`** | |
| 6～7 | Pre | 产品批量 + 定价批量 | **R-001** |
| 8 | Asm | **`#applyLotPricingAndTotals` + `#applyListDisplayFields`** | |
| 9 | Asm | **`py_str` ASC** | **BR-006** |

---

### 3.4 IF-INV-DTL-03 `sumInventoryDetailCurrent`

#### Request

与 **`PageInventoryDetailCurrentRequest`** 同筛选，**无分页**。

#### Response（`SumInventoryDetailCurrentResponse`）

`sumLineCostTotal` / `sumLinePriceTotal` / `truncated`

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 同 IF-02 | |
| 2 | Pre | **`enrichKeywordDefinitionIds`** + **`Mapper#selectCurrentDetailForSum(maxRows=30000)`** | **R-011** |
| 3～5 | Pre/Asm | 复用 IF-02 定价装配 | |
| 6 | Asm | 累加 | **BR-009** |

失败码：**`WHS_DTL_203`**

---

### 3.5 IF-INV-DTL-04 `pageInventoryDetailBackup`

#### Request（`PageInventoryDetailBackupRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` / `pageSize` | `Integer` | 是 | 同 IF-02 |
| `backupBusNo` | `String` | 否 | **未传** → **R-009** 默认当天批次 |
| `keyword` | `String` | 否 | 名称/批号/拼音/五笔/编码/商品名（与现库 **IF-02** 对齐） |
| `itemTable` | `String` | 否 | 项目类型：`med_medication_definition`=药品 / `adm_device_definition`=耗材；空=全部 |
| 其余筛选 | — | 否 | 同 **`PageInventoryDetailCurrentRequest`**（**不含** `warehouseIds` Gate 时 **SDD** 与现库一致） |

#### Response

**`PageInventoryDetailBackupResponse`**：`rows` = **`List<InventoryDetailBackupListRowDto>`**

#### DTO（`InventoryDetailBackupListRowDto`）

与 **`InventoryDetailListRowDto` 对客字段同构**（**无货位**）；金额/展示列**来自静表**，字段名与现库 DTO **一致**以便前端复用列定义。

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | **`assertGateUserPresent`** + **`requireTenantId`** | **`WHS_DTL_101`** |
| 2 | Pre | **`#resolveBackupBusNo`** | **R-009**；无批次 → 空页 |
| 3 | Pre | **`Mapper#toSnapshotPageQuery`** + **`DocInventoryItemSnapshotQueryService#pageSnapshotRows`** | 静表分页；**禁止** ChargeItem |
| 4 | Asm | **`Mapper#toBackupListRowDto`** + **`#enrichBackupRows`** | **R-007** |
| 5 | Asm | **`PagedResultBuilder`** | — |

失败码：**`WHS_DTL_204`**

---

### 3.6 IF-INV-DTL-05 `sumInventoryDetailBackup`

**Request**：同 IF-04（无分页）。**Response**：**`sumLineCostTotal`/`sumLinePriceTotal`/`truncated`**。

编排序列：Gate + **`#resolveBackupBusNo`** → **`DocInventoryItemSnapshotQueryService#listSnapshotRowsForSum(maxRows=30000)`** → 直累加静表 **`line_cost_total`/`line_price_total`**（**禁止** `#enrichRows` / `#applyLotPricingAndTotals`）。

失败码：**`WHS_DTL_205`**

---

### 3.7 IF-INV-DTL-06 `exportInventoryDetailCurrent`

#### Request

与 **`SumInventoryDetailCurrentRequest`** 同构。

#### Response（`ExportInventoryDetailCurrentResponse`）

`fileName` / `contentType` / `body` / `exportedRowCount` / `truncated`

#### 导出列（`InventoryDetailCurrentExportRowDto`，22 列，**不含货位**）

药品编码、项目名称、商品名称、项目类别、仓库、规格、厂家/产地、生产批号、库存（包装单位）、库存（最小单位）、**拆零换算比**、采购价格、零售价、进价总额、零售价总额、剂型、国药准字、供应商、医保等级、生产日期、有效期至、剩余过期天数。

金额进价两列写出：**3 位 + `RoundingMode.DOWN`**（**R-013** / **BR-INV-DTL-029**）；零售价两列仍 2 位 HALF_UP。

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 导出权 | |
| 2 | Pre | **`enrichKeywordDefinitionIds`** + **`Mapper#selectCurrentDetailForEx旧命名（已废止）** | 上限 **`INVENTORY_DETAIL_SUM_MAX_ROWS`**；**R-011** |
| 3～5 | Pre/Asm | 同 IF-02 | **BR-012** |
| 6 | Asm | **`AnnotatedExcelExporter`** | 工作表 **「库存明细记录」**；金额列 **R-013** |

失败码：**`WHS_DTL_206`**

---

### 3.8 IF-INV-DTL-07 `exportInventoryDetailBackup`

**Request**：同 IF-05 + 导出语义。**Response**：同 IF-06 结构。

编排序列：同 IF-04 Gate/Pre → **`listSnapshotRowsForExport(maxRows=100000)`** → **`#enrichBackupRows`** → **`InventoryDetailBackupExportRowDto`** + **`AnnotatedExcelExporter`**（金额列 **R-013** / **BR-013 / BR-029**）。

失败码：**`WHS_DTL_207`**

---

### 3.9 IF-INV-DTL-08 `retireInventoryItems`

#### Request（`RetireInventoryItemsRequest`）

`inventoryItemIds: List<Long>` 必填非空。

#### Response

`affectedRows: Integer`

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 写权限 | |
| 2 | Cmd | **`InventoryItemDomainService#stopSupply(StopSupplyCommand)`** | 0 行 → **`WHS_DTL_208`** |
| 3 | Asm | Response | |

---

### 3.10 IF-INV-DTL-09 `resumeInventoryItems`

#### Request（`ResumeInventoryItemsRequest`）

`inventoryItemIds: List<Long>` 必填非空。

#### Response

`affectedRows: Integer`

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 写权限 | |
| 2 | Cmd | **`InventoryItemDomainService#resumeSupply(ResumeSupplyCommand)`** | 0 行 → **`WHS_DTL_209`** |
| 3 | Asm | Response | |

---

### 3.11 IF-INV-DTL-10 定时库存快照（`executeInventoryDetailSnapshot`）

#### 运维注册（硬门禁）

| 项 | 值 |
| --- | --- |
| 处理器 key | **`inventoryWarehouseInventoryDetail/executeInventoryDetailSnapshot`** |
| 方法 | **`InventoryWarehouseInventoryDetailAppService#executeInventoryDetailSnapshot`**（`@SchedulableJob("库存备份")`） |
| `concurrentPolicy` | **`DISALLOW`**（**AS BR-021**） |
| `cronExpression` | 院点 zxplat-job 配置；建议 **`0 5 0 * * ?`**（**BR-INV-DTL-028** / **BR-INV-SET-004**） |
| `tenantId` | 任务实例绑定租户上下文（**`TenantUtils`**） |

#### 3.11.1 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | ZXPlat Job 框架 + **`TenantUtils#getCurrentTenantId`** | 租户缺失 → **`WHS_DTL_101`** |
| 2 | Pre | **`DocInventoryItemSnapshotQueryService#existsSuccessfulBackupForDate`** | **BR-020**；true → log + return |
| 3 | Pre | **`Mapper#selectCurrentDetailPage`**（pageNo=1） | 现库 **0 行** → **R-012** log + return |
| 4 | Pre | **`BusinessSequenceService#getSeqByDay(ABU, 2)`** | 确认有明细后生成 **`bus_no`** |
| 5 | Pre/Cmd | 循环 **`selectCurrentDetailPage`** + **`#enrichRows`** → **`Mapper#toSnapshotRowCommand`** → **`DocInventoryItemSnapshotDomainService#batchAppendSnapshots`** | pageSize=**`InventoryWarehouseDetailExportConstants#INVENTORY_DETAIL_SNAPSHOT_PAGE_SIZE`**（1000）；失败 **`WHS_DTL_211`** |
| 6 | Asm | ZXPlat Job 执行日志（框架） | 成功/跳过/失败 |

#### 3.11.2 快照表 `doc_inventory_item_snapshot`（DOC 域 · 事实列）

> **不含货位**；**不含** `*_text` 字典翻译列、**不含** `purchase_unit_ratio`；读侧 **`*Label`** 由 code 翻译。

| 库列 | Java | 类型 | 说明 |
| --- | --- | --- | --- |
| `id` | `id` | int8 | 快照行 PK |
| `tenant_id` | `tenantId` | int8 | 租户 |
| `bus_no` | `busNo` | varchar(32) | 批次单号（**ABU**+日+序号） |
| `snapshot_time` | `snapshotTime` | timestamptz | 快照时刻 |
| `inventory_item_id` | `inventoryItemId` | int8 | 源库存项 |
| `created_at` / `created_by` / `is_deleted` | 审计 | | |
| `item_table` / `item_id` / `charge_item_definition_id` | | | 引用 ID |
| `warehouse_id` / `supplier_id` | | int8 | 库房/供应商 ID |
| `product_bus_no` / `name` / `merchandise_name` / `total_volume` / `manufacturer` / `approval_number` / `yb_no` / `trace_no` / `py_str` / `wb_str` | | | 主数据文本 |
| `lot_number` / `quantity` / `package_unit_ratio` / `package_unit_code` | | | 批号/数量/倍数/包装单位码 |
| `lot_number_cost_amount` / `lot_number_price_amount` / `line_cost_total` / `line_price_total` | | numeric | **四价** |
| `production_date` / `expiration_date` / `remaining_days` | | | 效期 |
| `inventory_status_enum` / `chrgitm_lv` / `medication_category_code` / `device_category_code` / `dose_form_code` | | | **仅 code** |

**约束**：UNIQUE `(tenant_id, bus_no, inventory_item_id)`；INDEX `(tenant_id, bus_no)`。

**写入映射**：IF-10 自现库装配结果映射上表（`lotCostPrice`→`lot_number_cost_amount` 等）；**不**落库 `*Label`/`*StockText`。

**Flyway**：**`V101__doc_inventory_item_snapshot.sql`**

---

### 3.12 IF-INV-DTL-11 `listInventoryDetailBackupBusNo`

#### Request（`ListInventoryDetailBackupBusNoRequest`）

无业务字段（租户 Gate）。

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `backupBusNoList` | `List<String>` | **`bus_no` DESC** |

#### 编排序列

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | **`assertGateUserPresent`** + **`requireTenantId`** |
| 2 | Cmd | **`DocInventoryItemSnapshotQueryService#findDistinctBusNoByTenant`** |
| 3 | Asm | Response |

失败码：**`WHS_DTL_212`**

---

### 3.13 IF-INV-DTL-12 `loadInventoryWarningWorkbenchSummary`

#### Request（`LoadInventoryWarningWorkbenchSummaryRequest`）

无业务字段（租户 + 登录 Gate）。

#### Response（`LoadInventoryWarningWorkbenchSummaryResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `visible` | `Boolean` | 库管/药师为 `true` |
| `expireWarningCount` | `Integer` | 效期预警批号行数 |
| `lowStockWarningCount` | `Integer` | 库存预警品种数（**全院**各库房合计；目录警戒线或全局包装阈值，**BR-024**） |
| `expireWarningDays` | `Integer` | 当前效期阈值（系统设置） |
| `minPackageQuantityWarning` | `Integer` | 当前库存**全局**包装阈值（系统设置；目录未维护时回落） |

#### 编排序列

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | **`assertGateUserPresent`** |
| 2 | Pre | **`InventoryWarningSettingsupport** 读阈值并写入 Response |
| 3 | Gate | **`InventoryWarningRoleGate#canViewInventoryWarnings`**（否 → **`visible=false`**、计数 0 返回） |
| 4 | Pre | **`resolveGatedInventoryLocationIds`** 收窄库房（**仅效期计数**） |
| 5 | Cmd | 效期：**`InventoryItemQueryService#countExpireWarningInventoryRows`**；库存：**`InventoryLowStockWarningFilterSupport#countLowStockWarningProducts`** / **`#resolveLowStockProductKeys`**（全院，**`application.inventory.inventorydetail.support`**） |
| 6 | Asm | Response |

#### IF-02/03/06 增量：`warningMode`

| 值 | 行为 |
| --- | --- |
| `expire` | 写 **`expireWithinDays`** = 系统设置；默认 **`zeroStockOnly=false`** |
| `lowStock` | 解析 **`lowStockWarningAggregateKeys`**（**BR-INV-DTL-024**）：全院品种合计（**含 0**）目录警戒线或全局包装阈值命中；**不**写默认 **`zeroStockOnly=false`**；库房范围走 **`resolveEffectiveWarehouseIdsForCurrentList`**（默认不套管辖 Gate，仅尊重显式库房多选） |

系统设置项（**`SettingDefinitionEnum`**）：`inventoryExpireWarningDays`（默认 180）、`inventoryMinPackageQuantityWarning`（默认 **30**，目录未维护警戒线时回落）。产品展示 DTO 须携带 **`itemMinQuantity`/`itemMaxQuantity`**（**`ProductDefinitionDisplaySupport`**）。

---

### 3.14 IF-INV-DTL-13 `listInStockTraceNumbersByInventoryItem`

> 编排 **AS-CUS-TRC-001 · IF-TRC-22**；详情 deep link 跳转 **AS-CUS-TRC-002**（前端路由 + `traceNo` + `openDetail=true`）。
> 扫码弹窗药品展开传 **`hospitalWide=true`**：按药品读主档全院在院码（**`listInHospitalTraceNumbersByItem`**，含全部批号），前端「药品 → 批号列表 → 码」；不展示库位。

#### Request（`ListInStockTraceNumbersByInventoryItemRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `locationId` | `Long` | 条件 | 本库/库取路径必填；**`hospitalWide`** 可空 |
| `itemId` | `Long` | Y | 药品定义 id |
| `lotNumber` | `String` | 条件 | 本库/库取路径必填；**`hospitalWide`** 忽略（查该药全部批号） |
| `itemTable` | `String` | N | 默认 **`TableNameConstants.MED_MEDICATION_DEFINITION`**；非药品 → Gate 拒绝（**BR-INV-DTL-027**） |
| `hospitalWideFallbackWhenEmpty` | `Boolean` | N | 本库空才回落全院在院码（发药库取）；与 `hospitalWide` 同时为 true 时以 **`hospitalWide`** 为准 |
| `hospitalWide` | `Boolean` | N | **`true`**：按药品全院在院码（全部批号）；跳过本库子表与入参批号 |

#### Response（`ListInStockTraceNumbersByInventoryItemResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `locationId` | `Long` | |
| `itemId` | `Long` | |
| `lotNumber` | `String` | 入参回显；`hospitalWide` 时可空 |
| `items` | `List<InventoryItemTraceNumberRowDto>` | 本库净量 > 0 的码；或全院在院码（含批号） |

#### DTO：`InventoryItemTraceNumberRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceNo` | `String` | 可点击跳转报表页 |
| `lotNumber` | `String` | 主档批号；全院按药品浏览时供前端分组 |
| `locationNetSplitQuantity` | `BigDecimal` | 本库净拆零量（本库查询）；全院浏览可空 |
| `remainingSplitQuantity` | `BigDecimal` | 主档拆零剩余量（单位见 `splitUnitCode`） |
| `splitUnitCode` | `String` | 药典拆零单位 |
| `splitUnitCodeLabel` | `String` | 拆零单位术语文案 |
| `traceUnitCode` | `String` | 主档贴码单位 |
| `traceUnitCodeLabel` | `String` | 追溯码单位术语文案 |
| `traceUnitRatio` | `BigDecimal` | 主档贴码比快照 |
| `inHospitalStatusEnum` | `String` | 全院在院态（主表） |
| `inHospitalStatusLabel` | `String` | 读侧 Label |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | **`assertGateUserPresent`** + 租户 + **`itemId`** | **`WHS_DTL_101`** |
| 2 | Gate | 非 **`hospitalWide`** 时 **`locationId`/`lotNumber`** 必填；**`itemTable`** 须为药品 | **BR-INV-DTL-027** |
| 3a | Pre | **`hospitalWide=true`** → **`listInHospitalTraceNumbersByItem`** | 该药全部批号；主档剩余 > 0 |
| 3b | Pre | 否则 **`listInStockTraceNumbersByInventoryItemKey`**；空且 **`hospitalWideFallbackWhenEmpty`** → **`listInHospitalTraceNumbersByItemLot`** | **IF-TRC-22**；**禁止**读 `InventoryItem.trace_no` |
| 4 | Asm | **`toTraceNumberRowDtoList`** + 药典拆零单位 / `unit_code` Label | 空列表合法 |

失败码：**`WHS_DTL_213`**

#### 前端约定（非 HTTP）

- **仅现库**行展示「追溯码」按钮；备份视图隐藏（**BR-INV-DTL-027**）。
- 列表/导出 **不含** `traceNo` 列（**BR-INV-DTL-026**）。
- 扫码弹窗：药品行展开 → **批号列表** → 再展开批号 → **在院码**（只读）；不按发药行批号过滤；单位展示优先 Label。

---

## 4. QueryService 与领域事件

| 读/写 | 真源 | IF |
| --- | --- | --- |
| 现库 | **`wkf_inventory_item`** + **`InventoryItemQueryService`** | 02、03、06、10（读）、**12** |
| 快照 | **`doc_inventory_item_snapshot`** | 04、05、07、10（写）、11 |
| 定价 | **`ChargeItemDefinition`** | **仅** 02、03、06、10 |
| 追溯 | **`TraceNumberQueryService`**（**`listInStockTraceNumbersByInventoryItemKey`**） | **13** |

**事件**：IF-10 成功后备份读自然可见；**不**发领域事件（**AS §6** 语义级 **库存明细已快照**）。

## 5. 写库顺序

| IF | Cmd | 写入 |
| --- | --- | --- |
| IF-08/09 | **`stopSupply`/`resumeSupply`** | **`wkf_inventory_item.inventory_status_enum`** |
| IF-10 | **`DocInventoryItemSnapshotDomainService#batchAppendSnapshots`** | **`doc_inventory_item_snapshot`** 批量 INSERT |

## 6. 非功能性需求实现

### 6.1 幂等

| 项 | 承诺 |
| --- | --- |
| IF-10 | **日级**：Pre 步骤已备份则 skip；**禁止**同日第二批次 |
| 读接口 | 天然幂等 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置 |
| --- | --- | --- |
| IF-10 任务重叠 | **`ScheduledJobConcurrentPolicy.DISALLOW`** | zxplat-job 任务配置；**非** AppService 内自建锁 |
| IF-08/09 | **`@Version` 乐观锁** | **`InventoryItemDomainService`** |

### 6.3 重试与容错

读失败可重试（**AS EX-201～207、212**）；IF-10 失败可下轮 Cron 重试（须 **BR-020** 防重复）。

### 6.4 数据脱敏

导出遵循租户数据范围。

### 6.5 审计日志

停供/恢复写操作者与时间（**BR-014/015**）。

## 7. 测试要点

| 用例 | 优先级 |
| --- | --- |
| IF-02：关键字命中编码/商品名（**R-011**）；库房/供应商筛选 | P0 |
| IF-02/03：进/售价行总额共用拆零换算比（**R-010**）；进价=售价时总额一致 | P0 |
| IF-06：导出 22 列含拆零换算比 | P1 |
| IF-06/07：中药金额第三位导出不被 2 位四舍五入（**R-013**） | P0 |
| IF-10：全量行 = IF-02 无筛选；列 **−货位**；日级 skip | P0 |
| IF-10：任务 **DISALLOW**（重叠触发仅一次执行） | P0 |
| IF-04：默认 **`backupBusNo`**；选手动单号 | P0 |
| IF-04/05：定价变更后备份金额不变 | P0 |
| IF-11：降序去重 | P1 |
| IF-12：库管/药师 **`visible=true`**；非授权 **`visible=false`** 且计数 0 | P0 |
| IF-12：**warningMode** 跳转 IF-02 自动带效期/库存筛选 | P0 |
| IF-13：现库药品行查本库仍在库码；空列表；跳转报表 deep link | P0 |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v0.38** | **2026-10-06** | **R-013 / BR-INV-DTL-029**：导出**进价/进总额** `@Excel` 3 位 `DOWN`；售价仍 2 位 |
| **v0.37** | **2026-08-31** | IF-02/03/04/05/06/07：筛选增 **`itemTable`**（药品/耗材）；备份关键字含批号模糊（与现库对齐） |
| **v0.36** | **2026-08-26** | **`packageSplitRatioText`**：包装=拆零且倍数为 1 时展示 `1支`（不再空） |
| **v0.35** | **2026-08-26** | 列表 DTO 增 **`packageSplitRatioText`**（`1盒/36片`）；`#applyListDisplayFields` / `#applyBackupListDisplayFields` 经 **`MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText`** 拼装；与临床 `1片x100` 区分 |
| **v0.34** | **2026-08-01** | **R-010a / BR-MON-PKG-001**：`#computeLineTotal` 改为满包×包装价 + 零头单步线性；禁止包装价→拆零单价再乘 |
| **v0.33** | **2026-07-27** | **IF-INV-DTL-13**：**`hospitalWide`** 按药品全批；DTO **`lotNumber`**；前端「药品→批号→码」 |
| **v0.32** | **2026-07-15** | **BR-INV-DTL-024**：库存预警=全院品种采购（目录警戒线优先、断货计入、列表不套默认库房 Gate）；与效期批号预警分工；**`ProductDefinitionDisplayDto`** 携带警戒量 |
| **v0.31** | **2026-07-11** | IF-10 Cron 建议 **00:05**（**BR-INV-DTL-028**）；与库房日结 **BR-INV-SET-004** 对齐 |
| **v0.30b** | **2026-07-27** | **IF-INV-DTL-13**：**`hospitalWide`** 始终全院在院；DTO 增 **`remainingSplitQuantity`/`splitUnitCode`**/Label |
| **v0.30** | **2026-07-06** | **IF-INV-DTL-13** 本库仍在库追溯码；列表 DTO **移除 `traceNo`**（**BR-INV-DTL-026～027**）；`TraceNumberQueryService` |
| **v0.29** | **2026-07-03** | **库存预警**：**全院**合计（**`listHospitalWideProductQuantityAggregates`** / **`InventoryProductAggregateKey`**）；默认阈值 **30** |
| **v0.28** | **2026-07-03** | **IF-12** 工作台预警摘要；IF-02/03/06 **`warningMode`**；**`InventoryLowStockWarningFiltersupport** 落点 **`application.inventory.inventorydetail.support`**；**`InventoryItemQueryService#listLocationProductQuantityAggregates`** / **`#countExpireWarningInventoryRows`** |
| **v0.27** | **2026-07-02** | IF-01/BR-016：**`#buildWarehouseOptions`** / **`#resolveGatedInventoryLocationIds`** 纳入科室药房（`listAllActiveDepartmentPharmacies` + `narrowActiveLocationsByManaged`） |
| **v0.26** | **2026-07-02** | 包路径对齐代码 **`inventory/inventorydetail`**；IF-10 改为 **`@SchedulableJob`** 内嵌 **`executeInventoryDetailSnapshot`**（移除独立 Job 类） |
| **v0.25** | **2026-06-28** | **实现落地**：IF-04～11 + 定时快照；白名单改 **QueryService/DomainService**；**R-012** 现库 0 行不落库；Flyway **V101** |
| **v0.24** | **2026-06-28** | **DOC 快照** **`doc_inventory_item_snapshot`**（**`bus_no`**、事实列）；现库 DTO **四价** + **`*Label`**；去除重复 display 定价字段 |
| **v0.23** | **2026-06-28** | 现库对齐 openhis（略） |
| **v0.21** | **2026-06-28** | **库存备份**：**IF-10** `InventoryDetailSnapshotJob` + **`wkf_inventory_item_snapshot`** 列清单；**IF-11** 备份单号列表；**IF-04/05/07** 改为静表只读（**R-007/R-009**）；任务 **DISALLOW**；移除备份路径定价联结 |
| v0.20 | 2026-05-29 | 对客展示字段收敛 |
| v0.5 | 2026-05-24 | 库房 Gate **BR-016** |
| v0.1 | 2026-05-14 | 初稿 |
