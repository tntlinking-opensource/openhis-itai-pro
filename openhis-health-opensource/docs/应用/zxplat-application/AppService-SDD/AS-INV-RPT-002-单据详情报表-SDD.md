# 单据详情报表 · 应用服务详细设计（SDD）

> 编码：**AS-INV-RPT-002-SDD**  
> 版本：**v1.0** | 日期：**2026-09-02** | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 进销存 **单据详情报表**（统一包 **`inventory/documentdetail`**；**单一** `InventoryDocumentDetailAppService`） |
| 边界 | 编排 **IF-INV-DDC-{WHS\|PHA\|DEP}-01～04**；**只读**；默认 **`AGREE`**；期初显式类型可含 **`APPROVAL`**；**无管辖库位 Gate**；消费 **`pageWarehouseDocumentDetail`** + 产品/主数据 enrich |
| 编排权威 | `AS-INV-RPT-002-单据详情报表.md`（**v1.7**）§1～§8 |
| 方法权威 | `DM-WKF-006` §6.2.13 `pageWarehouseDocumentDetail`；`DM-ADM-003` Location；主数据 ProductDefinition 读侧 |
| 严格对齐 | IF **IF-INV-DDC-WHS/PHA/DEP-01～04** 与 AS 母表一致 |
| 实现阶段口径 | **领域 Query 与应用层均已落地**（`InventoryDocumentDetailAppService` + support 链） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 分页主表 | **`wkf_supply_request` 明细行**；**禁止** `bus_no` 聚合分页 |
| 持久化数量 | **`item_quantity`**（及盘点 **`total_quantity`**、损益 **`profit_loss_price`** 等）= **拆零单位** |
| 操作单位展示 | **`DocumentDetailRowDto.itemQuantity`**（及 **`bookQuantity`/`actualQuantity`/`gainLossQuantity`**）= **操作单位数量** = 拆零量 ÷ **`ratioToSplit(unit_code)`** |
| 包装/最小双列 | **`packageQuantity`/`packageUnitName`**、**`minUnitQuantity`/`minUnitName`**（**BR-INV-DDC-013**）；导出另有合并文案列 |
| 换算 | **`ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands`** + **`#convertSplitQuantityToDisplayUnit`**；**失败抛 `INV_DDC_204`**（**禁止降级**） |
| 审批时间 | 非期初筛 **`approval_time`**；期初（显式 `*_OPENING`）筛 **`apply_time`**；默认当前自然月 |
| 批号 | **`lotNumberKeyword`** → SQL **`LIKE`**（优先于精确 `lotNumber`） |
| 单据号 | **`busNo`** → SQL **`LIKE`**（大小写不敏感） |
| 品名 | **`productKeyword`** → **`InventoryCatalogFilterSupport#enrichDocumentDetailKeywordDefinitionIds`** → `item_table`+`item_id IN` |
| 盘点盘前 | 拆零源 **`total_quantity − item_quantity`**（同意时冻结；**禁止**读现库存 enrich） |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **INV-DDC-SDD-R-001** | **单一** **`InventoryDocumentDetailAppService`**；三域由 Request **`domainScope`** 区分；差异仅 **`DocumentDetailTypeWhitelistHelper`** 白名单（含本域 `*_OPENING`）；未传 **`typeEnum`** → **`typeEnums`=本域白名单**；init **`defaultTypeEnum`** 仅前端建议 |
| **INV-DDC-SDD-R-002** | 列表/合计/导出 **共用** **`DocumentDetailQueryAssemblySupport#buildPageQuery`**（筛选条件单一真源） |
| **INV-DDC-SDD-R-003** | 换算 **`DocumentDetailQuantityConversionSupport#requireDisplayQuantity`**：内部组装 bands，**不**向 Response 下发 **`productCatalog`** |
| **INV-DDC-SDD-R-004** | **`INV_DDC_204`** 消息模板：`单据 %s 中物品「%s」（编码 %s）无法按操作单位「%s」换算数量，请检查产品单位维护`；占位符来自行 enrich 后字段 |
| **INV-DDC-SDD-R-005** | 合计 **03**：循环分页（**`EXPORT_PAGE_SIZE=500`**）拉全量后 SUM；并经 **`DocumentDetailItemSummarySupport#aggregateByItem`** 生成按品规汇总行；与 **04** 导出共用拉取链 |
| **INV-DDC-SDD-R-006** | 明细导出工作表名 **「单据详情」**；汇总导出 **「单据详情汇总」**（`presentationMode=SUMMARY`）；文件名 `{药库\|药房\|科室}单据详情_{timestamp}.xlsx`；行上限 **`DocumentDetailExportConstants.EXPORT_MAX_ROWS=100000`** |
| **INV-DDC-SDD-R-007** | init **01**：**不做** `CurrentManagedLocationIdsResolver` Gate；库位下拉 = 租户内 **`listAllActiveWarehouses` + `listAllActivePharmacies` + `listAllActiveDepartmentPharmacies`** 并集；下发 **`summaryDimensionOptions`**（本期仅 ITEM） |
| **INV-DDC-SDD-R-008** | 非法或缺失 **`domainScope`** → **`INV_DDC_202`**；与 **`typeEnum`** 白名单 Gate 共用错误码 |
| **INV-DDC-SDD-R-009** | 按品规汇总：**不**新增领域 Query；应用层对已换算明细行按 `itemTable|itemId` 分桶；仅进价合计；**汇总 UI/导出不展示单价** |

## 2. 代码结构

### 2.1 统一包结构

```text
com/openhis/health/application/inventory/documentdetail/
  service/InventoryDocumentDetailAppService.java
  support/ … request/ … response/ … dto/ … mapper/
```

> 三域由请求 **`domainScope`**（`WAREHOUSE` / `PHARMACY` / `DEPARTMENT`）区分；**单一 HTTP 入口** `inventoryDocumentDetail`。

### 2.2 ~~药库/药房/科室三包 AppService~~（已废弃）

三域 IF 语义保留（AS §2.1），实现合并为 **`InventoryDocumentDetailAppService`** + **`domainScope`** 入参。

### 2.3 ~~药房 / 科室 AppService~~

| 历史 HTTP serviceName | 现统一为 |
| --- | --- |
| `inventoryWarehouseDocumentDetail` 等 | **`inventoryDocumentDetail`** |

### 2.4 依赖注入表（`DocumentDetailReadOrchestrationsupport 及子 support）

| 依赖 | 用途 |
| --- | --- |
| `TenantUtils` | Gate：租户 |
| `SupplyRequestQueryService` | **`pageWarehouseDocumentDetail`** |
| `LocationQueryService` | init 库位；行 enrich 库位名（`findOptionalById`） |
| `SupplierQueryService` | init 供应商下拉（`listSuppliers`）；行 enrich 供应商名（`findSupplierNameIfPresent`） |
| `PractitionerQueryService` | 制单/审批/经手人名（`findAllByIds`） |
| `InventoryCatalogFiltersupport | **`productKeyword` → resolved 定义 id 列表**（B.6.4 跨聚合预解析） |
| `ProductDefinitionBatchLoader` | 行 enrich + 换算 bands |
| `DocumentDetailTypeWhitelistsupport | 各域 **`SupplyType`** 白名单 Gate |
| `DocumentDetailQueryAssemblysupport | Request → **`WarehouseDocumentDetailPageQuery`** |
| `DocumentDetailQuantityConversionsupport | **BR-INV-DDC-010/011** |
| `DocumentDetailEnrichmentsupport | 头摊平 + 展示字段 |
| `InventoryDocumentDetailMapper` | Model → DTO；Excel |
| `TabularExcelExporter` | 导出字节（复用日结/库存明细同档工具） |
| `PageWindowGate` | 分页窗口 Gate（02） |

### 2.5 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `SupplyRequestQueryService` | **`pageWarehouseDocumentDetail`** | 02～04 |
| `LocationQueryService` | `listAllActiveWarehouses` / `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies` / `findOptionalById` | 01、02～04 |
| `SupplierQueryService` | `listSuppliers`；`findSupplierNameIfPresent`（批量 id 循环） | 01、02～04 |
| `PractitionerQueryService` | `findAllByIds` | 02～04 |
| `InventoryCatalogFiltersupport | `enrichDocumentDetailKeywordDefinitionIds` | 02～04 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | 02～04 |

### 2.6 枚举、字典与值集

| 类别 | 代码锚点 | DM 锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 | `SupplyType#getCode()` | **DM-WKF-006** §4.1 | **§AS 1.6.1～1.6.3** 白名单 |
| 单据状态 | `SupplyStatus` | **DM-WKF-006** §5 | 默认 **`AGREE`**；期初显式类型 **`AGREE`+`APPROVAL`**（**BR-INV-DDC-003**） |
| 库位形态 | `LocationForm` | **DM-ADM-003** | init 下拉 |
| 计量单位 | `unit_code` 字典 | **DM-WKF-006** §3.1 | **`unitCodeDictText`** |
| 错误码 | `ErrorCodeConstants.INV_DDC_201`～`207` | **AS §5** | 三域共用 |

### 2.7 白名单双向一致性

§2.4 各方法均出现在 §3 编排序列；§3 **无 Cmd**（只读）。

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**AppService**：`InventoryDocumentDetailAppService` · HTTP 前缀 **`/api/app/inventoryDocumentDetail/`**

| IF（三域同构） | 应用服务方法 | HTTP |
| --- | --- | --- |
| IF-INV-DDC-{WHS\|PHA\|DEP}-01 | `loadDocumentDetailFormInit` | `POST …/loadDocumentDetailFormInit` |
| IF-INV-DDC-{WHS\|PHA\|DEP}-02 | `pageDocumentDetails` | `POST …/pageDocumentDetails` |
| IF-INV-DDC-{WHS\|PHA\|DEP}-03 | `summarizeDocumentDetails` | `POST …/summarizeDocumentDetails` |
| IF-INV-DDC-{WHS\|PHA\|DEP}-04 | `exportDocumentDetails` | `POST …/exportDocumentDetails` |

> 三域差异由 Request **`domainScope`** 传入：`WAREHOUSE` / `PHARMACY` / `DEPARTMENT`（大小写不敏感）；非法 → **`INV_DDC_202`**。

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 租户、**`domainScope`**、已传 `typeEnum` 白名单、审批时间合法性 | `BusinessException` |
| **Pre** | 品名→resolved 定义 id；`pageWarehouseDocumentDetail`；批量 enrich | 空页或异常 |
| **Cmd** | — | **无写库** |
| **Asm** | 换算、头摊平、Mapper 装配 | 换算失败 **`INV_DDC_204`** |

---

### 3.2 IF-INV-DDC-{域}-01 `loadDocumentDetailFormInit`

#### Request（`LoadDocumentDetailFormInitRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `domainScope` | `String` | Y | `WAREHOUSE` / `PHARMACY` / `DEPARTMENT` |
| （其余） | — | — | 租户 Gate |

#### Response（`LoadDocumentDetailFormInitResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `typeEnumOptions` | `List<OptionDto>` | 本域白名单；**`value`=code**，**`label`=SupplyType.display** |
| `defaultTypeEnum` | `String` | 建议前端初始选中：药库 **`CABINET_INBOUND_RECEIPT`** / 药房 **`PHARMACY_REQUISITION`** / 科室 **`DEPT_REQUISITION`**；**查询不强制** |
| `sourceLocationOptions` | `List<OptionDto>` | 租户活跃库位并集 |
| `purposeLocationOptions` | `List<OptionDto>` | 同 **`sourceLocationOptions`** |
| `supplierOptions` | `List<OptionDto>` | 租户内全部有效供应商；**`value`=id**，**`label`=name** |
| `defaultApprovalTimeStart` | `LocalDateTime` | 当月 1 日 00:00:00 |
| `defaultApprovalTimeEnd` | `LocalDateTime` | 当月最后一日 23:59:59 |
| `summaryDimensionOptions` | `List<OptionDto>` | 本期仅 **`ITEM` / 按品规** |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `TenantUtils#getCurrentTenantId` | 空 → **`INV_DDC_201`** |
| 2 | Asm | 白名单 → `typeEnumOptions` + 建议 `defaultTypeEnum`（前端回填） | **INV-DDC-SDD-R-001** |
| 3 | Pre | `LocationQueryService` 三 list 并集 → 库位下拉 | **INV-DDC-SDD-R-007** |
| 4 | Pre | `SupplierQueryService#listSuppliers` → `supplierOptions` | 筛选用 |
| 5 | Asm | 系统月 → `defaultApprovalTime*` | |

---

### 3.3 IF-INV-DDC-{域}-02 `pageDocumentDetails`

#### Request（`PageDocumentDetailsRequest` extends `AbstractPageQuery`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `Integer` | Y | ≥1 |
| `pageSize` | `Integer` | Y | **B.10.11** 边界 |
| `domainScope` | `String` | Y | `WAREHOUSE` / `PHARMACY` / `DEPARTMENT` |
| `typeEnum` | `String` | N | 空 → 本域白名单全部（`typeEnums IN`）；已传须落白名单 |
| `sourceLocationId` | `Long` | N | 源库位 |
| `purposeLocationId` | `Long` | N | 目的库位 |
| `supplierId` | `Long` | N | 供应商；空则该维不过滤（**BR-INV-DDC-004**） |
| `approvalTimeStart` | `LocalDateTime` | N | 空 → 当月月初 |
| `approvalTimeEnd` | `LocalDateTime` | N | 空 → 当月月末 |
| `productKeyword` | `String` | N | 品名/编码/拼音/五笔模糊 |
| `lotNumberKeyword` | `String` | N | 批号模糊 |
| `busNo` | `String` | N | 单据号模糊 `LIKE`（可选；完整单号亦可命中） |

#### Response（`PageDocumentDetailsResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `long` | 明细行总数 |
| `pageNo` | `int` | |
| `pageSize` | `int` | |
| `records` | `List<DocumentDetailRowDto>` | |

#### DTO：`DocumentDetailRowDto`（并集稀疏列）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| **头 · 单据** | | |
| `busNo` | `String` | 单据号 |
| `originalBusNo` | `String` | 来源单号 |
| `typeEnum` | `String` | |
| `typeEnumText` | `String` | SupplyType.display |
| `statusEnum` / `statusEnumText` | `String` | 落库状态；期初可为 `APPROVAL` |
| **头 · 库位** | | |
| `sourceLocationId` | `Long` | |
| `sourceLocationName` | `String` | |
| `purposeLocationId` | `Long` | |
| `purposeLocationName` | `String` | |
| **头 · 往来** | | |
| `supplierId` | `Long` | |
| `supplierName` | `String` | |
| **头 · 人员/时间** | | |
| `applicantId` | `Long` | |
| `applicantName` | `String` | |
| `approverId` | `Long` | |
| `approverName` | `String` | |
| `practitionerId` | `Long` | 经手人（盘点等） |
| `practitionerName` | `String` | |
| `applyTime` | `LocalDateTime` | |
| `approvalTime` | `LocalDateTime` | |
| `occurrenceTime` | `LocalDateTime` | 制单/业务时点（头口径：同号 `occurrence_time` 聚合 max 或首行） |
| **行 · 物品** | | |
| `lineId` | `Long` | `wkf_supply_request.id` |
| `itemTable` | `String` | |
| `itemId` | `Long` | |
| `productName` | `String` | |
| `productBusNo` | `String` | 院内编码 |
| `specification` | `String` | |
| `manufacturer` | `String` | |
| `lotNumber` | `String` | |
| `traceNo` | `String` | |
| `startTime` | `LocalDateTime` | 效期起 |
| `endTime` | `LocalDateTime` | 效期止 |
| **行 · 数量金额（操作单位）** | | |
| `unitCode` | `String` | 行 **`unit_code`** |
| `unitCodeDictText` | `String` | |
| `itemQuantity` | `BigDecimal` | **展示用操作单位数量** |
| `packageQuantity` | `BigDecimal` | 包装单位数量（拆零 ÷ 包装倍数） |
| `packageUnitName` | `String` | 包装单位展示名 |
| `minUnitQuantity` | `BigDecimal` | 最小单位数量（拆零持久量） |
| `minUnitName` | `String` | 最小单位展示名 |
| `price` | `BigDecimal` | 落库单价 |
| `totalPrice` | `BigDecimal` | 落库行总价 |
| `remake` | `String` | 行备注 |
| **盘点/损益扩展（可空）** | | |
| `bookQuantity` | `BigDecimal` | 账面（操作单位） |
| `actualQuantity` | `BigDecimal` | 实盘（操作单位） |
| `gainLossQuantity` | `BigDecimal` | 盈亏量（操作单位） |
| `actualAmount` | `BigDecimal` | 实盘金额 |
| `gainLossAmount` | `BigDecimal` | 盈亏金额 |

> **盘点/损益映射**：拆零源 — `item_quantity`→盈亏；`total_quantity`→实盘；**盘前账面拆零 = `total_quantity − item_quantity`**（再经 **BR-INV-DDC-010** 换算为操作单位展示；**禁止**走详情页实时库存 enrich）。

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `TenantUtils` | |
| 2 | Gate | 已传 `typeEnum` → `DocumentDetailTypeWhitelistHelper#assertTypeInDomain`；未传则跳过（查全部） | **`INV_DDC_202`** |
| 3 | Gate | 审批时间 `start <= end` | **`INV_DDC_207`** |
| 4 | Pre | `InventoryCatalogFilterSupport#enrichDocumentDetailKeywordDefinitionIds` | keyword 空跳过；resolved 双空 → **空页短路** |
| 5 | Pre | 装配 **`WarehouseDocumentDetailPageQuery`**：已传 → `typeEnum`；未传 → `typeEnums`=本域白名单；非期初 `statusEnum=AGREE`+`approvalTime*`；期初 `statusEnums=(AGREE,APPROVAL)`+`applyTime*`；库位、`supplierId`、`lotNumberKeyword`、`productKeyword`/resolved | **BR-INV-DDC-002/003/004** |
| 6 | Pre | `SupplyRequestQueryService#pageWarehouseDocumentDetail` | |
| 7 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 当前页 item 集合 |
| 8 | Pre | Location/Supplier/Practitioner 批量 enrich | |
| 9 | Asm | `DocumentDetailEnrichmentSupport#toRows` | 头摊平 |
| 10 | Asm | 每行 `DocumentDetailQuantityConversionSupport#requireDisplayQuantity` 及盘点/损益列 | 失败 **`INV_DDC_204`** |
| 11 | Asm | `Mapper#toPageDocumentDetailsResponse` | |

#### 换算伪代码（实现约束 · 非 AS）

```text
bands = buildInboundSelectionBands(productDisplayDto)
ratio = resolveRatioToSplitByUnitCode(bands, line.unitCode)
if ratio == null || ratio <= 0:
  throw INV_DDC_204(busNo, productName, productBusNo, unitCode, lineId)
displayQty = item_quantity_split.divide(ratio, SCALE, HALF_UP)
```

---

### 3.4 IF-INV-DDC-{域}-03 `summarizeDocumentDetails`

#### Request

与 **§3.3** 相同筛选字段；**无分页**或忽略 `pageNo`/`pageSize`。

#### Response（`SummarizeDocumentDetailsResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalPriceSum` | `BigDecimal` | 全筛选 **`totalPrice`** 之和（进价） |
| `itemQuantitySum` | `BigDecimal` | 全筛选 **展示 `itemQuantity`** 之和（跨单位参考） |
| `rowCount` | `long` | 命中**明细**行数 |
| `records` | `List<DocumentDetailItemSummaryRowDto>` | 按品规汇总行（**BR-INV-DDC-014** / **INV-DDC-SDD-R-009**） |

#### DTO：`DocumentDetailItemSummaryRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dimensionKey` | `String` | `itemTable\|itemId` |
| `summaryDimension` | `String` | 固定 `ITEM` |
| `dimensionName` | `String` | 品名 |
| `itemTable` / `itemId` | `String` / `Long` | 分桶键 |
| `productBusNo` | `String` | 院内编码 |
| `specification` | `String` | 规格 |
| `packageQuantity` / `packageUnitName` | `BigDecimal` / `String` | 包装量合计 |
| `minUnitQuantity` / `minUnitName` | `BigDecimal` / `String` | 最小量合计 |
| `totalPrice` | `BigDecimal` | 进价合计 |
| `lineCount` | `Long` | 命中明细行数（内部） |

#### 编排序列

| 序号 | 阶段 | 说明 |
| --- | --- | --- |
| 1～10 | 同 §3.3 | **`pageSize=500`** 循环直至拉全量 |
| 11 | Asm | SUM 字段；**`DocumentDetailItemSummarySupport#aggregateByItem`**；**`INV_DDC_204`** 任一行换算失败则整次失败 |

---

### 3.5 IF-INV-DDC-{域}-04 `exportDocumentDetails`

#### Request

同 **§3.3** 筛选字段；另：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `exportColumns` | `List<ExcelExportColumnDto>` | N | 前端可见列裁剪（R-EXCEL-4） |
| `presentationMode` | `String` | N | `DETAIL`（默认）或 `SUMMARY`；空按明细 |

#### Response（`ExportDocumentDetailsResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 如 **`药库单据详情_yyyyMMddHHmmss.xlsx`** |
| `contentType` | `String` | 复用 **`InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE`** |
| `fileBytes` | `byte[]` | 或框架统一下载流 |

#### 编排序列

| 序号 | 阶段 | 说明 |
| --- | --- | --- |
| 1～10 | 同 §3.3 全量拉取 | 上限 **`EXPORT_MAX_ROWS`** |
| 11 | Asm | `DETAIL` → `toExportRows`；`SUMMARY` → `aggregateByItem` + `toSummaryExportRows`（并集 DTO 稀疏列） |
| 12 | Asm | **`TabularExcelExporter`**；表名见 **INV-DDC-SDD-R-006** |
| 13 | Asm | 超上限 → **`INV_DDC_205`**（**产品定：失败**） |

---

## 4. QueryService 与领域事件

### 4.1 `WarehouseDocumentDetailPageQuery`（已落地）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `approvalTimeStart` / `approvalTimeEnd` | `LocalDateTime` | **`approval_time`** 闭区间 |
| `supplierId` | `Long` | **`supplier_id`** 等值；空则该维不过滤 |
| `lotNumberKeyword` | `String` | **`lot_number LIKE`**（与精确 `lotNumber` 互斥，keyword 优先） |
| `productKeyword` | `String` | 品名/编码/拼音/五笔；须先经 **`InventoryCatalogFilterSupport#enrichDocumentDetailKeywordDefinitionIds`** |
| `resolvedMedicationDefinitionIdsForKeyword` | `List<Long>` | 药品 `item_table`+`item_id IN`；双列表均为空 → **空结果** |
| `resolvedDeviceDefinitionIdsForKeyword` | `List<Long>` | 耗材 `item_table`+`item_id IN` |

`WarehouseDocumentDetailQueryModel` 已映射 **`totalQuantity` / `profitLossPrice`**（盘点/损益列拆零源）。

`WarehouseDocumentDetailPagePredicatesupport 与上述字段同步。

错误码 **`INV_DDC_201`～`207`** 已登记 **`ErrorCodeConstants`**。

### 4.2 事件

只读；**无**领域写事件。

---

## 5. 写库顺序（核心写接口视角）

本场景 **无写接口**；§5 不适用。

---

## 6. 非功能性需求实现（承诺表）

| NFR | 实现 |
| --- | --- |
| 导出上限 | **`DocumentDetailExportConstants.EXPORT_MAX_ROWS = 100000`** |
| 导出分批 | **`EXPORT_PAGE_SIZE = 500`** |
| 租户 | 所有 Query 带 **`tenantId`**（若 Query 支持） |
| 日志 | **`INV_DDC_204`** 记录 **lineId、itemId、splitQty、unitCode、ratio** |

---

## 7. 测试要点

| 用例 | 预期 |
| --- | --- |
| 包装单位行换算 | 拆零 100、包装比 10 → 展示 10 |
| ratio 缺失 | **204** 含 **busNo + 品名 + 编码 + unitCode** |
| 默认月 | 未传审批时间仅查当月 |
| 品名无命中 | 空列表，非 204 |
| 合计=导出条数 | `rowCount` 一致 |
| 未传 typeEnum | 命中本域白名单全部类型（含非默认类型行、本域 `*_OPENING`） |
| 三域建议 defaultTypeEnum | init 下发；查询不强制 |
| 期初单 | init 下拉含本域 OPENING；`typeEnum=*_OPENING` 时含审核中；目的库位可筛；导出与列表同条件 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v0.1** | **2026-07-03** | 初稿：三包 `documentdetail`；操作单位换算；`INV_DDC_204` 精细报错；Query 扩展 GAP-001 |
| **v0.2** | **2026-07-03** | 领域 Query/Predicate/读模型/错误码已落地；品名走 `InventoryCatalogFiltersupport；盘点盘前 = `total_quantity − item_quantity` |
| **v0.3** | **2026-07-03** | 代码落点收敛为单包 **`inventory/documentdetail`**（三 `AppService` 同包）；Mapper 更名 `InventoryDocumentDetailMapper` |
| **v0.4** | **2026-07-03** | 合并为 **`InventoryDocumentDetailAppService`**；三域由 **`domainScope`** 入参区分 |
| **v0.6** | **2026-07-15** | **BR-INV-DDC-002**：未传 `typeEnum` → `typeEnums` 本域白名单 IN；`defaultTypeEnum` 仅前端建议；对齐 AS **v1.3** |
| **v0.5** | **2026-07-03** | 应用层全链路落地；依赖表对齐 **`InventoryCatalogFilterSupport`** / **`findAllByIds`**；补 **INV-DDC-SDD-R-008**；AS 升至 **v1.2** |
| **v0.7** | **2026-07-31** | 白名单纳入三房期初 `*_OPENING`；对齐 AS **v1.4**；导出仍共用 **`DocumentDetailQueryAssembler#buildPageQuery`** |
| **v0.8** | **2026-07-31** | **BR-INV-DDC-003** 产品化：期初显式类型含 `APPROVAL`+`apply_time`；对齐 AS **v1.5** |
| **v0.9** | **2026-08-28** | **BR-INV-DDC-013**：行 DTO 增 `packageQuantity`/`minUnitQuantity` 及单位名；对齐 AS **v1.6** |
| **v1.0** | **2026-09-02** | **BR-INV-DDC-014 / INV-DDC-SDD-R-009**：IF-03 返回按品规汇总行；导出 `presentationMode=SUMMARY`；仅进价；对齐 AS **v1.7** |
