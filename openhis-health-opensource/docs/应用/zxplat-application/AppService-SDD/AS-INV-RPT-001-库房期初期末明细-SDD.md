# 库房期初期末明细 · 应用服务详细设计（SDD）

> 编码：**AS-INV-RPT-001-SDD**  
> 版本：**v1.1** | 日期：**2026-07-11** | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 进销存 **库房期初期末明细** 只读报表（**`inventory/periodbalancedetail`**，与 **`inventory/inventorydetail`** 同属 **`inventory/`** 直属包） |
| 边界 | 编排 **IF-INV-SET-01～03**；**只读**对账；消费 **DOC 快照** / **InventoryItem** 实时库存 / **SupplyRequest** / **Dispense** / **调价**；**禁止**写库存、写单据、触发备份、新建跨域读包 |
| 编排权威 | `AS-INV-RPT-001-库房期初期末明细.md`（**v2.0**）§1～§8 |
| 方法权威 | `DM-ADM-003` §6.2；`DM-DOC-001` §6.2；`DM-WKF-006` §6.2.12；`DM-WKF-007` §6.2.19；`DM-WKF-008` §6.2；`DM-MED-001` §6.2；`DM-MED-002` §6.2；`ADM` 耗材定义读侧；`DM-CUS-011` §6.2；`DM-ADM-010` §6.2 |
| 严格对齐 | IF **IF-INV-SET-01～03** 与 AS 母表一致 |
| 实现阶段口径 | **已上线**：IF-INV-SET-01～03；包 **`inventory/periodbalancedetail`**；领域读方法 **GAP-INV-SET-001/002 已关闭** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 数量汇总 | **拆零最小单位** `BigDecimal` 累加；展示串 **`packageStockText` / `splitStockText`** 对齐 **AS-INV-WHS-004** |
| 金额汇总 | **禁止**「最小单位总量 × 包装单价」及「品种总额 ÷ 品种总片数」反推单价；**`#computeLineAmountByPackageAndRemainder`** / **`PeriodBalanceDetailPricingAssembler#computeLineTotal`** 同构 **BR-INV-DTL-007** / **INV-DTL-SDD-R-010** |
| 包装单价展示 | 期初/期末/期间业务分列包装进价单价与进价总额；**售价侧本版不返回动态列**（**BR-INV-SET-026**） |
| 账平展示 | 主表列：推算期末数量/进价总额、盈亏数、盈亏差、是否账平；页面展示核对公式 |
| 期间业务日 | 零点备份口径（**BR-INV-SET-004** / **BR-INV-DTL-028**）：期初 ABU 日 **D₀** 库存为 **(D₀−1) 日终**；期间 **含 D₀ 全天**，下界 **D₀ 00:00**；期末备份日 **D₁** 时上界 **(D₁−1) 23:59:59**，实时期末为查询时刻；由 **`PeriodBalanceDetailWindowResolver`** 换算 `LocalDateTime` |
| 分页主表 | **全量未删**药品/耗材定义；**禁止**以期初备份品种集做主表；**禁止** `PublicationStatus` 过滤 |
| 动态列 | 由 **`Location.formEnum`** 决定 **`activeColumnCodes`**；不适用列**不返回**（非填 0 占位） |
| 备份单号 | 接口 **`startBackupBusNo` / `endBackupBusNo`** ↔ 静表 **`bus_no`** |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **INV-SET-SDD-R-001** | 期间 **Supply** 批量读仅 **`SupplyRequestQueryService#listAgreedLinesByWarehouseAndApprovalWindow`**；`type_enum` 白名单由 **`PeriodBalanceDetailTypeWhitelistHelper** 按 **`LocationForm`** 组装（**BR-INV-SET-007/010**） |
| **INV-SET-SDD-R-002** | 药品发退药：**`pageMedicationDispenseRecords` 分页循环**（步长 **`EXPORT_PAGE_SIZE`**，总上限 **`EXPORT_MAX_ROWS`**；**BR-INV-SET-029**）。正向发药 **`RECORD_TYPE_DISPENSE`**：门诊/住院各一次（`outpatientMedicationOnly` / `inpatientMedicationOnly`）；退药 **`RECORD_TYPE_REFUND`**：**单通道一次**（`toMedicationDispenseRefundRecordQuery`）。三路经 **`mergeMedicationDispenseSlipsForSettlement`** 按 **`dispenseId` 去重**后分组（**BR-INV-SET-030**）；**禁止**双路默认 **`ALL`** 合并导致退药翻倍；**禁止** `listMedicationDispenseRecords` 单次截断 |
| **INV-SET-SDD-R-003** | 耗材发药：**`DeviceDispenseQueryService#listDeviceDispensesByLocationAndDispenseWindow`** |
| **INV-SET-SDD-R-004** | 期初/期末（备份）金额：`**PeriodBalanceDetailPricingAssembler#resolveSnapshotLineCostTotal` / `#resolveSnapshotLineSaleTotal`** 逐批号行套 **BR-INV-DTL-007** 后品种 SUM；缺批号包装价回落静表 **`line_cost_total` / `line_price_total`**（**BR-INV-SET-022**） |
| **INV-SET-SDD-R-005** | 期末实时：数量 **`getBatchInventoryByWarehouse`**；金额现价 **`ChargeItemDefinitionQueryService`** 批号链 + **BR-INV-DTL-007** |
| **INV-SET-SDD-R-006** | 发药历史进价：**`PriceAdjustmentQueryService#findApprovedHistoryByItem`**（**当前页** `itemId` 集合）+ 不晚于发药日备份快照回落（**BR-INV-SET-020**） |
| **INV-SET-SDD-R-007** | 盘点/损益：**数量、金额均拆盈/亏列**；域内保留符号参与推算（**BR-INV-SET-009/010/011**） |
| **INV-SET-SDD-R-008** | 账平：**数量 + 进价总额** 二维均平为 `isBalance=true`（**BR-INV-SET-013**）；售价侧不参与；**`costAmountDiff`** 与比较端均经 **`scaleMoney`** |
| **INV-SET-SDD-R-018** | 调价进价盈亏：`PriceAdjustmentQueryService#sumApprovedPurchaseImpactByLocation` → `priceAdjustmentCostAmount`；**`addSignedBusinessCost`** 加总；主表/导出「调价」列（**BR-INV-SET-012/031**）；历史 loc 空行不计 |
| **INV-SET-SDD-R-012** | 动态列元数据含 **`PACKAGE_UNIT_PRICE_COST`**；`**addQtyCostGroup`** 为各业务组追加数量/包装进价/进价额（**不含售价列**） |
| **INV-SET-SDD-R-013** | 导出固定列与屏显主表同构：进价侧 + 推算期末 + 盈亏数/盈亏差/是否账平；售价列剔除 |
| **INV-SET-SDD-R-009** | 导出工作表名 **「库房期初期末明细」**；行上限 **`InventoryPeriodBalanceDetailExportConstants.EXPORT_MAX_ROWS`**（默认 **100000**）；分页拉取步长 **`EXPORT_PAGE_SIZE=500`**；MIME 复用 **`InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE`** |
| **INV-SET-SDD-R-010** | 快照汇总候选行上限 **`InventoryPeriodBalanceDetailConstants.SNAPSHOT_SUM_MAX_ROWS`**（**30000**，对齐 **AS-INV-WHS-004 IF-05**） |
| **INV-SET-SDD-R-014** | 账平推算：**`PeriodBalanceDetailAssembler#computeBalanceFields(dto, locationFormEnum)`**；`returnToWarehouse*` 药库形态 **+**、药房/科室形态 **−**（**BR-INV-SET-027**）；进价/售价累加 **`addSignedBusinessCost` / `addSignedBusinessSale`** 同构 |
| **INV-SET-SDD-R-015** | 药品发药有效拆零量：**`#resolveMedicationDispenseSplitQuantity`** — `dispense_quantity` **> 0** 用之，否则回落 `quantity`（**BR-INV-SET-028**） |
| **INV-SET-SDD-R-016** | 业务日窗：**`PeriodBalanceDetailWindowResolver#resolveContext`** — 下界 **D₀ 00:00:00**（含当天）；期末备份上界 **(D₁−1) MAX**；实时期末上界 **now**（**BR-INV-SET-004** / **BR-INV-DTL-028**） |
| **INV-SET-SDD-R-017** | 药品发退药 Mapper：**`toMedicationDispenseRecordQuery`** 写 **`RECORD_TYPE_DISPENSE`**；**`toMedicationDispenseRefundRecordQuery`** 写 **`RECORD_TYPE_REFUND`**（不设门诊/住院标志）。AppService **`#accumulateMedicationDispenseWithHistory`** 三路拉取 + **`#mergeMedicationDispenseSlipsForSettlement`**（**BR-INV-SET-030**） |

## 2. 代码结构

```text
com/openhis/health/application/inventory/periodbalancedetail/
  service/
    InventoryPeriodBalanceDetailAppService.java
  support/
    PeriodBalanceDetailWindowResolver.java
    PeriodBalanceDetailWindowContext.java
    PeriodBalanceDetailTypeWhitelistHelper.java
    PeriodBalanceDetailAssembler.java
    PeriodBalanceDetailPricingAssembler.java
    PeriodBalanceDetailPackageUnitPriceTracker.java
    PeriodBalanceDetailAggregateRow.java
    PeriodBalanceDetailAssemblyContext.java
  request/
    LoadPeriodBalanceDetailFormInitRequest.java
    PagePeriodBalanceDetailRequest.java
    ExportPeriodBalanceDetailRequest.java
  response/
    LoadPeriodBalanceDetailFormInitResponse.java
    PagePeriodBalanceDetailResponse.java
    ExportPeriodBalanceDetailResponse.java
  dto/
    PeriodBalanceDetailListRowDto.java
    PeriodBalanceDetailExportRowDto.java
    PeriodBalanceDetailColumnDto.java
  mapper/
    InventoryPeriodBalanceDetailMapper.java
    InventoryPeriodBalanceDetailMapperImpl.java
```

> 包名：`com.zxplat.domain.application.inventory.dailysettlement`（**`inventory/` 直属**，与 **`inventory/inventorydetail`** 同级；**非** `report/inventory/`）。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate：`requireOperatorId`（登录操作员） |
| `TenantUtils` | Gate：`getCurrentTenantId`（租户） |
| `CurrentManagedLocationIdsResolver` | IF-01 下拉；IF-02/03 管辖 Gate（**WAREHOUSE / PHARMACY / DEPARTMENT_PHARMACY**） |
| `LocationQueryService` | IF-02/03 **`findOptionalById`**；Gate 全量活跃库位列表 |
| `DocInventoryItemSnapshotQueryService` | IF-01 备份单号；IF-02/03 期初/期末快照读 |
| `InventoryItemQueryService` | IF-02/03 期末实时数量 |
| `MedicationDefinitionQueryService` | 药品 Tab 目录分页 |
| `DeviceDefinitionQueryService` | 耗材 Tab 目录分页 |
| `SupplyRequestQueryService` | IF-02/03 期间供应动账 |
| `MedicationDispenseQueryService` | 药品 Tab 期间发退药 |
| `DeviceDispenseQueryService` | 耗材 Tab 期间发退药 |
| `PriceAdjustmentQueryService` | 发药历史进价追溯（页内品种） |
| `ChargeItemDefinitionQueryService` | 实时期末批号现价 |
| `ProductDefinitionBatchLoader` | 行展示 enrich（名称/规格/包装比/单位 Label） |
| `InventoryPeriodBalanceDetailMapper` | Request ↔ Query / DTO 映射；**`buildExcelBytes`** 委托 **`TabularExcelExporter`** |
| `PeriodBalanceDetailWindowResolver | 业务日窗解析 |
| `PeriodBalanceDetailTypeWhitelistHelper | `LocationForm` → `SupplyType` 白名单 |
| `PeriodBalanceDetailAssembler | 品种级汇总、行筛选、账平、动态列裁剪 |
| `PeriodBalanceDetailPricingAssembler | **BR-INV-DTL-007** 行额计算 |
| `OptionDto`（`com.zxplat.framework.core.application.dto`） | IF-01 下拉；**`busNo` 承载 `formEnum`**；**公共库，不在本模块 dto/ 新建** |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedLocationIdsResolver` | `listManagedActiveWarehousesOrEmpty` / `listManagedActivePharmaciesOrEmpty` / `narrowActiveLocationsByManaged` / `gatedLocationIdsFromActive` / `assertLocationInManagedGate` | 01、02、03 |
| `LocationQueryService` | `listAllActiveWarehouses` / `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies` / `findOptionalById` | 02、03（Gate） |
| `DocInventoryItemSnapshotQueryService` | `findDistinctBusNoByTenant` | 01 |
| `DocInventoryItemSnapshotQueryService` | `listSnapshotRowsForSum` | 02、03 |
| `InventoryItemQueryService` | `getBatchInventoryByWarehouse` | 02、03 |
| `MedicationDefinitionQueryService` | `pageMedicationCatalog` | 02、03（药品 Tab） |
| `DeviceDefinitionQueryService` | `pageDeviceDefinition` | 02、03（耗材 Tab） |
| `SupplyRequestQueryService` | `listAgreedLinesByWarehouseAndApprovalWindow` | 02、03 |
| `MedicationDispenseQueryService` | `pageMedicationDispenseRecords` | 02、03（药品 Tab；正向双路 + 退药单路） |
| `DeviceDispenseQueryService` | `listDeviceDispensesByLocationAndDispenseWindow` | 02、03（耗材 Tab） |
| `PriceAdjustmentQueryService` | `findApprovedHistoryByItem` | 02、03 |
| `ChargeItemDefinitionQueryService` | `findLotNumberCostLinesGroupedByLotBatchKey` / `findLotNumberPriceLinesGroupedByLotBatchKey` | 02、03（实时期末） |
| `InventoryPeriodBalanceDetailMapper` | `toSnapshotSumQuery` / `toSupplyWindowQuery` / `toDeviceDispenseWindowQuery` / `toMedicationDispenseRecordQuery` / `toMedicationDispenseRefundRecordQuery` / `toMedicationCatalogPageQuery` / `toExportRowDto` / `toPageResponse` / `buildExcelBytes` | 02、03 |
| `PeriodBalanceDetailAssembler | `sumSnapshotRowsByItemId` / `accumulateSupplyLines` / `buildListRows` / `applyRowFilters` / `resolveActiveColumns` / `extractActiveColumnCodes` | 02、03 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 库位形态 | `com.zxplat.domain.share.enums.LocationForm` | **DM-ADM-003** §3 `adm_location.form_enum` | 动态列；白名单逻辑在 `PeriodBalanceDetailTypeWhitelistHelper |
| 供应类型 | `com.zxplat.domain.share.enums.SupplyType` | **DM-WKF-006** §4.1 `type_enum` 白名单 | **AS §1.6.1～1.6.3** 日结动账子集 |
| 供应状态 | `com.zxplat.domain.share.enums.SupplyStatus` | **DM-WKF-006** §5 状态机 | 期间动账仅 **`AGREE`** |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | **DM-WKF-007** §5.2 / **DM-MED-002** §5 | 发药 **`COMPLETED`**、退药 **`REFUNDED`** |
| 药品主表 | `com.zxplat.domain.share.constants.TableNameConstants.MED_MEDICATION_DEFINITION` | **DM-MED-001** §3 主表 | `PagePeriodBalanceDetailRequest.itemTable` |
| 耗材主表 | `com.zxplat.domain.share.constants.TableNameConstants.ADM_DEVICE_DEFINITION` | **DM-ADM** 耗材定义 §3 主表 | 同上 |
| 物品表字段 | `PagePeriodBalanceDetailRequest.itemTable` | **DM-WKF-006** §3 `item_table` | 药/耗 Tab 切换 |
| 账平筛选 | `PagePeriodBalanceDetailRequest.isBalance` | **AS-INV-RPT-001** **BR-INV-SET-013** | `true`=仅账平；`false`=仅不平；`null`=不过滤 |
| 业务行筛选 | `PagePeriodBalanceDetailRequest.isBus` | **AS-INV-RPT-001** **BR-INV-SET-006** | 默认 `true`：隐藏全 0 业务行 |
| 备份前缀 | `com.zxplat.domain.share.constants.AggregateTypeConstants.INVENTORY_DETAIL_SNAPSHOT_BACKUP` | **DM-DOC-001** §3 `bus_no` 规则 | 与 **AS-INV-WHS-004** 一致（`ABU`） |
| 快照汇总上限 | `InventoryPeriodBalanceDetailConstants.SNAPSHOT_SUM_MAX_ROWS` | **AS-INV-WHS-004** IF-05 / **DM-DOC-001** §6.2 | **30000** |
| 导出行上限 | `InventoryPeriodBalanceDetailExportConstants.EXPORT_MAX_ROWS` | **AS-INV-RPT-001** §7 导出 | 默认 **100000** |
| 导出分页步长 | `InventoryPeriodBalanceDetailExportConstants.EXPORT_PAGE_SIZE` | **INV-SET-SDD-R-009** | **500** |
| 列值类型 | `InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_*` | **`PeriodBalanceDetailColumnDto.valueType`** | `QUANTITY` / `AMOUNT_COST` / `AMOUNT_SALE` |
| 备份单号日期段 | `InventoryPeriodBalanceDetailConstants.BACKUP_BUS_NO_DATE_SEGMENT_LENGTH` | **DM-DOC-001** `ABU`+`yyyyMMdd` | **8** |
| 导出 MIME | `InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE` | 与库存明细导出同档 | 复用，**不在**日结 ExportConstants 重复定义 |
| 导出文件名时间戳 | `GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER` | **`ExportPeriodBalanceDetailResponse.fileName`** | `库房期初期末明细_{timestamp}.xlsx` |
| 错误码 | `ErrorCodeConstants.INV_SET_201`～`206` | **AS-INV-RPT-001** §5 **EX-INV-SET-201**～**206** | 已落地 |

### 2.4 白名单双向一致性复核

§2.2 各方法均在 §3 编排序列出现；§3 **无 Cmd**（只读）；编排序列 **禁止** 调用白名单外 Repository。

## 3. 按接口 ID 命名展开

> **阅读顺序**：先读完各 IF 的「领域编排序列」，按序号从上到下执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-INV-SET-01 | `loadPeriodBalanceDetailFormInit` | 页面初始化 | `POST /api/app/inventoryWarehousePeriodBalanceDetail/loadPeriodBalanceDetailFormInit` |
| IF-INV-SET-02 | `pagePeriodBalanceDetail` | 日结分页查询 | `POST /api/app/inventoryWarehousePeriodBalanceDetail/pagePeriodBalanceDetail` |
| IF-INV-SET-03 | `exportPeriodBalanceDetail` | 导出 Excel | `POST /api/app/inventoryWarehousePeriodBalanceDetail/exportPeriodBalanceDetail` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 租户 / 管辖库房 / 必填参数 / 期初快照存在性 | 抛 **`BusinessException`**，不再调用后续步骤 |
| **Pre** | 只读批量加载：快照、实时库存、供应、**本库调价进价盈亏**、发退药、目录分页、调价历史 | 抛业务异常或空结果短路 |
| **Cmd** | — | 本场景**无写库** |
| **Asm** | 品种汇总、账平、筛选、Mapper 装配 Response / Excel | — |

**记法**：`SupplyRequestQueryService#listAgreedLinesByWarehouseAndApprovalWindow` 表示注入 Bean 后调用该方法。

---

### 3.2 IF-INV-SET-01 `loadPeriodBalanceDetailFormInit`（页面初始化）

#### Request 字段表（`LoadPeriodBalanceDetailFormInitRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 租户由 Gate 解析 |

#### Response 字段表（`LoadPeriodBalanceDetailFormInitResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `warehouseOptions` | `List<OptionDto>` | 管辖范围内 **药库 + 药房 + 科室药房**；**`formEnum` 写入 `OptionDto.busNo`**（前端动态列） |
| `backupBusNoOptions` | `List<OptionDto>` | 租户 **`bus_no` 去重降序**（语义对齐 **AS-INV-WHS-004 IF-11**） |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `INV_SET_201` | 初始化装载失败 | 页面加载失败 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 须已登录；失败 **`INV_SET_201`** |
| 2 | Gate | `TenantUtils#getCurrentTenantId` | — | 租户上下文；空 → **`INV_SET_201`** |
| 3 | Pre | `#buildWarehouseOptions` | `listManagedActiveWarehousesOrEmpty` + `listManagedActivePharmaciesOrEmpty` + `narrowActiveLocationsByManaged(listAllActiveDepartmentPharmacies)` | **`OptionDto.busNo` ← `Location.formEnum`** |
| 4 | Pre | `DocInventoryItemSnapshotQueryService#findDistinctBusNoByTenant` | `tenantId` | 允许空列表 |
| 5 | Asm | 组装 `LoadPeriodBalanceDetailFormInitResponse` | — | **仅读路径** |

---

### 3.3 IF-INV-SET-02 `pagePeriodBalanceDetail`（日结分页查询）

#### Request 字段表（`PagePeriodBalanceDetailRequest`）

> 继承 **`AbstractHealthPageRequest`**（`pageNo` / `pageSize` / `keyword` / `getBoundedPageSize()`）。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `locationId` | `Long` | 是 | 选中库房（**BR-INV-SET-001**） |
| `startBackupBusNo` | `String` | 是 | 期初备份单号（**BR-INV-SET-002**） |
| `endBackupBusNo` | `String` | 否 | 期末备份；空=实时库存（**BR-INV-SET-003**） |
| `itemTable` | `String` | 是 | **`med_medication_definition`** 或 **`adm_device_definition`**（药/耗 Tab） |
| `pageNo` | `Integer` | 否 | 默认 **1** |
| `pageSize` | `Integer` | 否 | 默认 **10** |
| `keyword` | `String` | 否 | 名称/编码/拼音/五笔（**BR-INV-SET-006** 后过滤） |
| `isBus` | `Boolean` | 否 | 默认 **`true`**：隐藏期间全 0 业务行；**`false`** 展示含全 0 |
| `isBalance` | `Boolean` | 否 | `true` 仅账平；`false` 仅不平；`null` 不过滤 |

#### Response 字段表（`PagePeriodBalanceDetailResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `Long` | 目录主表 **`totalElements`**（未删定义总数）；**`keyword` / `isBus` / `isBalance` 为当前页行后过滤，不改变 `total`** |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `locationFormEnum` | `String` | 当前库位 **`LocationForm.getCode()`** |
| `activeColumnCodes` | `List<String>` | 当前形态适用动态列 code（见 **`PeriodBalanceDetailColumnDto`**） |
| `columns` | `List<PeriodBalanceDetailColumnDto>` | 列元数据（标题、是否金额列、分组） |
| `rows` | `List<PeriodBalanceDetailListRowDto>` | 当前页数据行 |

#### DTO 字段表 — `PeriodBalanceDetailColumnDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `columnCode` | `String` | 列唯一 code（如 `purchaseQty`、`dispenseCostAmount`） |
| `columnTitle` | `String` | 表头中文 |
| `valueType` | `String` | `InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_*`（`QUANTITY` / `PACKAGE_UNIT_PRICE_COST` / `PACKAGE_UNIT_PRICE_SALE` / `AMOUNT_COST` / `AMOUNT_SALE`） |
| `groupCode` | `String` | 业务分组（进货/发药/盘点…） |

#### DTO 字段表 — `PeriodBalanceDetailListRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `itemId` | `Long` | 品种主键 |
| `itemNo` | `String` | 院内编码 |
| `itemName` | `String` | 名称 |
| `specText` | `String` | 规格 |
| `manufacturerText` | `String` | 厂商 |
| `categoryCode` | `String` | 分类 code |
| `categoryCodeLabel` | `String` | 分类展示 |
| `packageUnitCode` | `String` | 包装单位 code |
| `packageUnitCodeLabel` | `String` | 包装单位展示 |
| `minUnitCode` | `String` | 拆零单位 code |
| `minUnitCodeLabel` | `String` | 拆零单位展示 |
| `packageUnitRatio` | `BigDecimal` | 包装换算比（展示串用） |
| `beginQuantity` | `BigDecimal` | 期初数量（最小单位） |
| `beginPackageStockText` | `String` | 期初包装展示串 |
| `beginSplitStockText` | `String` | 期初拆零展示串 |
| `beginCostAmount` | `BigDecimal` | 期初进价总额 |
| `beginCostPackageUnitPrice` | `BigDecimal` | 期初包装进价（每包装单位；多批号混价 null） |
| `beginSaleAmount` | `BigDecimal` | 期初售价总额 |
| `beginSalePackageUnitPrice` | `BigDecimal` | 期初包装售价（每包装单位；多批号混价 null） |
| `endQuantity` | `BigDecimal` | 实际期末数量 |
| `endPackageStockText` | `String` | 期末包装展示串 |
| `endSplitStockText` | `String` | 期末拆零展示串 |
| `endCostAmount` | `BigDecimal` | 实际期末进价总额 |
| `endCostPackageUnitPrice` | `BigDecimal` | 期末包装进价 |
| `endSaleAmount` | `BigDecimal` | 实际期末售价总额 |
| `endSalePackageUnitPrice` | `BigDecimal` | 期末包装售价 |
| `computedEndQuantity` | `BigDecimal` | 推算期末数量 |
| `computedEndCostAmount` | `BigDecimal` | 推算期末进价总额 |
| `computedEndSaleAmount` | `BigDecimal` | 推算期末售价总额 |
| `quantityDiff` | `BigDecimal` | 盈亏数（实际期末数量−推算期末数量） |
| `costAmountDiff` | `BigDecimal` | 盈亏差（实际期末进价总额−推算期末进价总额） |
| `saleAmountDiff` | `BigDecimal` | 售价差额（本版不展示，接口仍返回） |
| `isBalance` | `Boolean` | 盈亏数与盈亏差均为 0 |
| `purchaseQuantity` | `BigDecimal` | 进货数量（动态列，不适用形态为 `null`） |
| `returnToSupplierQuantity` | `BigDecimal` | 退供应商数量 |
| `stockOutQuantity` | `BigDecimal` | 出库/请领发出数量 |
| `returnToWarehouseQuantity` | `BigDecimal` | 退库数量（列内为**绝对量**；药库=退库入库，药房/科室=退库出库；推算 ± 见 **BR-INV-SET-027**） |
| `transferInQuantity` | `BigDecimal` | 调入数量 |
| `transferOutQuantity` | `BigDecimal` | 调出数量 |
| `stockInQuantity` | `BigDecimal` | 药房入库（药库出库目的侧）数量 |
| `dispenseQuantity` | `BigDecimal` | 发药数量 |
| `refundDispenseQuantity` | `BigDecimal` | 退药数量 |
| `deptIssueQuantity` | `BigDecimal` | 领用数量 |
| `deptIssueReturnQuantity` | `BigDecimal` | 退领数量 |
| `stocktakeGainQuantity` | `BigDecimal` | 盘点盈数量 |
| `stocktakeLossQuantity` | `BigDecimal` | 盘点亏数量 |
| `gainQuantity` | `BigDecimal` | 损益盈数量 |
| `lossQuantity` | `BigDecimal` | 损益亏数量 |
| `purchaseCostAmount` | `BigDecimal` | 进货进价额 |
| `purchaseSaleAmount` | `BigDecimal` | 进货售价额 |
| `returnToSupplierCostAmount` | `BigDecimal` | 退供应商进价额 |
| `returnToSupplierSaleAmount` | `BigDecimal` | 退供应商售价额 |
| `stockOutCostAmount` | `BigDecimal` | 出库进价额 |
| `stockOutSaleAmount` | `BigDecimal` | 出库售价额 |
| `returnToWarehouseCostAmount` | `BigDecimal` | 退库进价额 |
| `returnToWarehouseSaleAmount` | `BigDecimal` | 退库售价额 |
| `transferInCostAmount` | `BigDecimal` | 调入进价额 |
| `transferInSaleAmount` | `BigDecimal` | 调入售价额 |
| `transferOutCostAmount` | `BigDecimal` | 调出进价额 |
| `transferOutSaleAmount` | `BigDecimal` | 调出售价额 |
| `stockInCostAmount` | `BigDecimal` | 药房入库进价额 |
| `stockInSaleAmount` | `BigDecimal` | 药房入库售价额 |
| `dispenseCostAmount` | `BigDecimal` | 发药进价额 |
| `dispenseCostPackageUnitPrice` | `BigDecimal` | 发药包装进价 |
| `dispenseSaleAmount` | `BigDecimal` | 发药售价额 |
| `dispenseSalePackageUnitPrice` | `BigDecimal` | 发药包装售价 |
| `refundDispenseCostAmount` | `BigDecimal` | 退药进价额 |
| `refundDispenseSaleAmount` | `BigDecimal` | 退药售价额 |
| `deptIssueCostAmount` | `BigDecimal` | 领用进价额 |
| `deptIssueSaleAmount` | `BigDecimal` | 领用售价额 |
| `deptIssueReturnCostAmount` | `BigDecimal` | 退领进价额 |
| `deptIssueReturnSaleAmount` | `BigDecimal` | 退领售价额 |
| `stocktakeGainCostAmount` | `BigDecimal` | 盘点盈进价额 |
| `stocktakeLossCostAmount` | `BigDecimal` | 盘点亏进价额 |
| `gainCostAmount` | `BigDecimal` | 损益盈进价额 |
| `lossCostAmount` | `BigDecimal` | 损益亏进价额 |

#### 关键校验

| 规则 | 说明 |
| --- | --- |
| **BR-INV-SET-001～021** | 见 AS §1.6；金额 **INV-SET-SDD-R-004～008** |
| 目录分页 | 仅 `is_deleted=false`；**禁止**启用态过滤 |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `INV_SET_202` | `locationId` 空 | 请选择库房 |
| `INV_SET_203` | `startBackupBusNo` 空 | 请选择期初备份单号 |
| `INV_SET_204` | 期初快照无该库位行 | 该库房没有备份库存数据 |
| `INV_SET_206` | 库房不在管辖范围 | 无权查看该库房 |
| `INV_SET_205` | 查询装配失败 | 查询失败，请稍后重试 |

#### Mapper 接口方法签名

| 方法签名 |
| --- |
| `toSnapshotSumQuery(PagePeriodBalanceDetailRequest, Long tenantId, String busNo, Long warehouseId): DocInventoryItemSnapshotPageQuery` |
| `toSupplyWindowQuery(PagePeriodBalanceDetailRequest, PeriodBalanceDetailWindowContext, List<String> typeWhitelist, Long tenantId): SupplyRequestSettlementWindowQuery` |
| `toMedicationDispenseRecordQuery(PagePeriodBalanceDetailRequest, PeriodBalanceDetailWindowContext, boolean outpatientOnly): MedicationDispenseRecordPageQuery`（**`RECORD_TYPE_DISPENSE`**） |
| `toMedicationDispenseRefundRecordQuery(PagePeriodBalanceDetailRequest, PeriodBalanceDetailWindowContext): MedicationDispenseRecordPageQuery`（**`RECORD_TYPE_REFUND`**） |
| `toDeviceDispenseWindowQuery(PagePeriodBalanceDetailRequest, PeriodBalanceDetailWindowContext): DeviceDispenseSettlementWindowQuery` |
| `toMedicationCatalogPageQuery(PagePeriodBalanceDetailRequest): MedicationDefinitionCatalogPageQuery`（耗材 Tab 由 AppService 内联映射 **`DeviceDefinition`** 对等 Query） |
| `toExportRowDto(PeriodBalanceDetailListRowDto): PeriodBalanceDetailExportRowDto` |
| `toPageResponse(List<PeriodBalanceDetailListRowDto>, long total, int pageNo, int pageSize, String locationFormEnum, List<PeriodBalanceDetailColumnDto>, List<String> activeColumnCodes): PagePeriodBalanceDetailResponse` |
| `buildExcelBytes(List<PeriodBalanceDetailExportRowDto>, List<PeriodBalanceDetailColumnDto>, String sheetName): byte[]`（**`MapperImpl`** 委托 **`TabularExcelExporter#exportToXlsxBytes`**） |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `#assertGateOperatorPresent` + `#requireTenantId` | — | 登录 + 租户 |
| 2 | Gate | `#validatePageRequest` | `locationId` / `startBackupBusNo` / `itemTable` | **`INV_SET_202/203`** |
| 3 | Gate | `#resolveGatedLocation` | `locationId` | `findOptionalById` + `gatedLocationIdsFromActive`（药库/药房/科室药房并集）→ **`INV_SET_206`** |
| 4 | Pre | `PeriodBalanceDetailWindowResolver#resolveContext` | 期初/期末 `bus_no` | 审批/发放日窗（**BR-INV-SET-004**） |
| 5 | Pre | `#loadAssemblyContext` | 合并步骤 6～9 | 期初/期末/期间 Supply/**本库调价**/Dispense → **`periodByItemId`** |
| 6 | Pre | （内嵌）`listSnapshotRowsForSum` + `toSnapshotSumQuery(..., tenantId, startBusNo, ...)` | 期初 | 无行 → **`INV_SET_204`** |
| 7 | Pre | （内嵌）期末备份或 `#loadRealtimeEndInventory` | — | **INV-SET-SDD-R-004/005** |
| 8 | Pre | （内嵌）`resolveSupplyTypeCodes` + `listAgreedLinesByWarehouseAndApprovalWindow` | `tenantId` 写入 Query | 期间供应 |
| 8b | Pre | （内嵌）`#accumulatePriceAdjustmentCostByLocation` | `locationId` + 审批窗 | **BR-INV-SET-031** 本库调价进价盈亏 |
| 9 | Pre | （内嵌）`#accumulateMedicationDispenseWithHistory` + 耗材发退药批量读 | — | 药品正向 DISPENSE 门诊/住院双路 + REFUND 单路；`#mergeMedicationDispenseSlipsForSettlement` 按 **`dispenseId` 去重**（**BR-INV-SET-030**）；调价历史内嵌 |
| 10 | Pre | `#pageCatalogDefinitions` | 目录分页 | **BR-INV-SET-005** |
| 11 | Pre | `#enrichProductsForItems` + `#loadPriceHistoryForItems` | 当前页 `itemId` | 展示 enrich |
| 12 | Asm | `PeriodBalanceDetailAssembler#buildListRows` | 当前页 `itemId` | 品种填数、**`computeBalanceFields`** 账平（含调价进价，**BR-INV-SET-012/027/028/031**）；**`nullifyInactiveColumns`** 按形态裁剪 |
| 13 | Asm | `PeriodBalanceDetailAssembler#applyRowFilters` | `keyword` / `isBus` / `isBalance` | **BR-INV-SET-006/013** |
| 14 | Asm | `resolveActiveColumns` + `extractActiveColumnCodes` | `formEnum` | 动态列 |
| 15 | Asm | `toPageResponse` | `catalogSlice.totalElements` 作 `total` | **仅读路径** |

---

### 3.4 IF-INV-SET-03 `exportPeriodBalanceDetail`（导出 Excel）

#### Request 字段表（`ExportPeriodBalanceDetailRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `locationId` | `Long` | 是 | 同 IF-02 |
| `startBackupBusNo` | `String` | 是 | 同 IF-02 |
| `endBackupBusNo` | `String` | 否 | 同 IF-02 |
| `itemTable` | `String` | 是 | 同 IF-02 |
| `keyword` | `String` | 否 | 同 IF-02 |
| `isBus` | `Boolean` | 否 | 同 IF-02 |
| `isBalance` | `Boolean` | 否 | 同 IF-02 |

#### Response 字段表（`ExportPeriodBalanceDetailResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | `库房期初期末明细_{timestamp}.xlsx`（**`GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER`**） |
| `contentType` | `String` | **`InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE`** |
| `fileBytes` | `byte[]` | Excel 二进制 |
| `truncated` | `Boolean` | 是否触顶 **`EXPORT_MAX_ROWS`** |
| `exportedRowCount` | `Integer` | 实际导出行数 |

#### DTO 字段表 — `PeriodBalanceDetailExportRowDto`

与 **`PeriodBalanceDetailListRowDto`** 字段**同构**（导出扁平行）；列顺序与 **`activeColumnCodes`** 一致。

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `INV_SET_202`～`206` | 同 IF-02 | 同 IF-02 |
| `INV_SET_205` | Excel 生成失败 | 导出失败，请稍后重试 |

#### Mapper 接口方法签名

| 方法签名 |
| --- |
| `toExportRowDto(PeriodBalanceDetailListRowDto): PeriodBalanceDetailExportRowDto` |
| `buildExcelBytes(List<PeriodBalanceDetailExportRowDto>, List<PeriodBalanceDetailColumnDto>, String sheetName): byte[]` |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1～5 | — | **复用 IF-INV-SET-02 编排序列 1～5**（`#loadAssemblyContext` 一次完成期间数据装载） | Gate + 期间批量读 |
| 6 | Pre | 循环 `#pageCatalogDefinitions`（`pageSize=EXPORT_PAGE_SIZE`）直至无更多页或触顶 **`EXPORT_MAX_ROWS`** | 每页 **`buildListRows` + `applyRowFilters`** 后累加 |
| 7 | Asm | `toExportRowDto` 批量映射 | — |
| 8 | Asm | `buildExcelBytes` + 组装 `ExportPeriodBalanceDetailResponse` | 工作表 **「库房期初期末明细」**；`truncated` 语义 **INV-SET-SDD-R-009** |

## 4. QueryService 与领域事件

| 读侧 | 说明 |
| --- | --- |
| **DOC 快照** | 期初/期末（备份）数量与冻结金额；`listSnapshotRowsForSum` 后在应用层按 `item_id` SUM |
| **WKF-008** | 期末实时数量；定价链仅用于**未选期末备份**场景 |
| **WKF-006** | `listAgreedLinesByWarehouseAndApprovalWindow`；方向在 **`PeriodBalanceDetailAssembler#accumulateSupplyLines`** 按 **BR-INV-SET-008** 判定（列内绝对量；账平 ± 在 **`#computeBalanceFields`**） |
| **MED-002** | 药品发退药：正向 **`DISPENSE`** 门诊/住院双路 + 退药 **`REFUND` 单路**；**`#mergeMedicationDispenseSlipsForSettlement`**；有效数量 **`#resolveMedicationDispenseSplitQuantity`**（**BR-INV-SET-028/030**） |
| **WKF-007** | 耗材发退药；`COMPLETED`/`REFUNDED` |
| **CUS-011** | 页内品种调价历史（发药进价时点还原）；**`sumApprovedPurchaseImpactByLocation`**（本库调价进价盈亏，**BR-INV-SET-031**） |
| **ADM-010** | 实时期末批号现价 |
| **事件** | **无**；IF-01～03 均不发布、不消费领域写事件 |

## 5. 写库顺序（核心写接口视角）

本应用服务 **无 Cmd / 无写库**。

| IF | App 方法 | Cmd | 事务 |
| --- | --- | --- | --- |
| IF-INV-SET-01～03 | 全部 | — | 只读；**无** `@Transactional` 写事务 |

失败回滚：仅指 Excel 生成等应用内资源；**不**涉及 DB 回滚。

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 查询幂等 | `InventoryPeriodBalanceDetailAppService` 各 `public` 方法无写库 | §3.2～3.4 全部 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 只读无锁 | 不更新库存/单据 | — |

### 6.3 重试与容错

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 快照/批量读失败 | 统一 `BusinessException` **`INV_SET_205`** | §3.3 步骤 6～16 |

### 6.4 数据脱敏

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 无患者明细 | 日结仅品种级；发药行不暴露就诊/患者字段 | §3.3 步骤 11～13 |

### 6.5 审计日志

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 导出审计 | `exportPeriodBalanceDetail` 记录操作员、库位、期初/期末备份号 | §3.4 步骤 20～21 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 期初 3/1 日终 → 3/1 业务不计、3/2 起计 | 集成 | 待建 | P0 |
| 期初 9 号备份 + 实时期末 11 号 → 期间含 9～11 号业务 | 集成 | 待建 | P0 |
| 期初 9 号 + 期末 11 号备份 → 期间 9～10 号、期末=10 号日终库存 | 集成 | 待建 | P0 |
| 快照金额与批号包装价+BR-INV-DTL-007 一致（非总额÷片数） | 单元+集成 | 待建 | P0 |
| 进价差与账平刻度一致（不出现 0 显示但账平否） | 单元 | 待建 | P0 |
| 包装单价与进价额分列展示 | 集成 | 待建 | P0 |
| 全量目录分页：期末有库存、期初无仍出现 | 集成 | 待建 | P0 |
| 药库/药房/科室动态列差异 | 集成 | 待建 | P0 |
| 药房退库：推算期末减 `returnToWarehouse`、药库加（**BR-INV-SET-027**） | 单元 | 待建 | P0 |
| 发药 `dispense_quantity=0` 回落 `quantity` 计入发药列（**BR-INV-SET-028**） | 单元 | 待建 | P0 |
| 仅门诊退药、住院无正向发药：退药列不翻倍（**BR-INV-SET-030**） | 集成 | 待建 | P0 |
| 壳单不计入动账 | 集成 | 待建 | P0 |
| 数量+进价+售价三维账平 | 单元+集成 | 待建 | P0 |
| 包装/拆零展示串 | 单元 | 待建 | P1 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 期间批量读无全库品种 N+1 | 性能 | 待建 | P0 |
| 导出触顶 `truncated=true` | 集成 | 待建 | P1 |

### 7.3 查询结果一致性

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 备份期末与 **AS-INV-WHS-004** 同 `bus_no` 汇总一致 | 集成 | 待建 | P0 |
| 导出列与屏显 `activeColumnCodes` 一致 | 集成 | 待建 | P0 |

### 7.4 集成测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| `listAgreedLinesByWarehouseAndApprovalWindow` 与手工 SQL 抽样一致 | 领域+应用 | 待建 | P0 |
| 药品门诊+住院正向发药合并 + 退药单路 | 集成 | 待建 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| 自动化用例 | 应用包已落地，契约/集成测试待补 | 随联调 PR |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v1.2** | **2026-07-26** | 调价进价盈亏入账平 **INV-SET-SDD-R-018** / **BR-INV-SET-031**；对齐 AS **BR-INV-SET-012/026** |
| **v1.1** | **2026-07-11** | 药品发退药分通道读 **INV-SET-SDD-R-017**（**BR-INV-SET-030**）：`toMedicationDispenseRefundRecordQuery`；`#mergeMedicationDispenseSlipsForSettlement`；对齐 AS **v2.0** |
| **v1.0** | **2026-07-11** | 零点备份期间窗 **INV-SET-SDD-R-016**（**BR-INV-SET-004** / **BR-INV-DTL-028**）；对齐 AS **v1.9** |
| **v0.9** | **2026-07-11** | 药品发退药改 **`listAllMedicationDispenseSlipsForSettlement` 分页全量**（**INV-SET-SDD-R-002** / **BR-INV-SET-029**）；对齐 AS **v1.8** |
| **v0.8** | **2026-07-11** | **`computeBalanceFields`** 按 `locationFormEnum` 区分退库 ±（**INV-SET-SDD-R-014** / **BR-INV-SET-027**）；**`resolveMedicationDispenseSplitQuantity`** 零值回落（**INV-SET-SDD-R-015** / **BR-INV-SET-028**）；对齐 AS **v1.7** |
| **v0.7** | **2026-07-09** | 动态列/导出剔除售价；账平改为数量+进价二维；导出列与屏显主表对齐；**INV-SET-SDD-R-013** |
| **v0.6** | **2026-07-09** | 快照金额 **INV-SET-SDD-R-004** 改批号包装价+BR-INV-DTL-007；包装单价列 **INV-SET-SDD-R-012**；同日实时期末窗 **INV-SET-SDD-R-011**；账平盈亏差刻度 **INV-SET-SDD-R-008**；新增 **`PeriodBalanceDetailPackageUnitPriceTracker`** |
| **v0.5** | **2026-07-02** | **`nullifyInactiveColumns`**：科室药房与药房一致隐藏药库列、保留领用/退领列 |
| **v0.4** | **2026-07-02** | 与 **`zxplat`** 实现对齐：support 类名 **`PeriodBalanceDetail*`**；Gate/Pre 编排序列；Mapper 签名；**`TabularExcelExporter`** 导出；移除 **`ProductDefinitionSelectionTerminologysupport**；**`OptionDto.busNo`** 承载 formEnum |
| **v0.3** | **2026-07-02** | 包路径对齐代码 **`inventory/periodbalancedetail`**；补全 supClient 内部类；实现阶段改为 **已上线** |
| **v0.2** | **2026-07-02** | ~~包路径迁至 `report/inventory/periodbalancedetail`~~（已废止，见 **v0.3**）；类名 **`InventoryWarehouse*`**；HTTP **`inventoryWarehousePeriodBalanceDetail`** |
| **v0.1** | **2026-07-02** | 初稿：对齐 **AS-INV-RPT-001 v1.2**；Gap 已关；三 IF 只读编排与动态列 DTO |
