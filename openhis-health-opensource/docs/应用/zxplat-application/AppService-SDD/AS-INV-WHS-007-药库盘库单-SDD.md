# 药库盘库单 · 应用服务详细设计（SDD）

> 编码：AS-INV-WHS-007-SDD  
> 版本：v0.3 | 日期：2026-05-24 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药库盘库单应用服务（库存 · 药库 · **`CABINET_STOCKTAKE`**，与 **`com.zxplat.domain.share.enums.SupplyType#CABINET_STOCKTAKE#getCode()`** 一致） |
| 边界 | 编排 **SupplyRequest** 盘点建单、修订、审批、删除；**审批同意**后应用层编排 **`createStocktakingDelivery`**（仅 **`item_quantity`（盈亏）≠ 0** 行）+ **`InventoryItem` 按盈亏加减**（盘亏 **`decreaseQuantity(allowNegative=true)`**）；**不**补录批号维定价、**不**写追溯码 |
| 编排权威 | `AS-INV-WHS-007-药库盘库单.md`（**v0.1**）§2、§4 |
| 方法权威 | `DM-WKF-006-SupplyRequest-供应请求.md` §6.1.7；`DM-WKF-005-SupplyDelivery-供应发放.md` §6.1.3；`DM-WKF-008-InventoryItem-库存项.md` §3 供应侧映射；`DM-ADM-003-Location-位置.md` §6.2；`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` §3 |
| 严格对齐 | IF **IF-WHS-ST-01～11** 与 AS 母表一致 |
| 实现阶段口径 | **目标态全量契约**（应用层代码待生成） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | API 层使用 **`profitLossQuantity` / `actualQuantity` / `actualAmount` / `profitLossAmount`** 与业务口语对齐；落库映射 **`wkf_supply_request.item_quantity` / `total_quantity` / `total_price` / `profit_loss_price`**（**DM-WKF-006 §3**） |
| **`bus_no` 生成** | **`BusinessSequenceService#getSeqByDay`**；序号键 **`CST`**（**`SupplyType.busNoSeqPrefixForTypeCode`**）；回退 **`SR`** |
| 盘点库房 | 请求/契约字段 **`purposeLocationId`**；**`purpose_type_enum`/`purpose_location_id`** 为持久化真源；**`source_*`** 由领域 **`buildStocktakeEntityFromItem`** 与 purpose 镜像 |
| QueryService口径 | 列表与 **AS-INV-WHS-002/003** 同策略：**`SupplyRequestQueryService`** **`bus_no` 去重分页**；选品 **`InventoryItemQueryService#queryInventoryItemsByPage`** + **ProductDefinition** |
| 事务口径 | **IF-WHS-ST-08** 默认单事务：**`agreeRequest` → `createStocktakingDelivery` → 库存逐行加减**，失败整单回滚 |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **WHS-ST-SDD-R-001** | **IF-WHS-ST-08 库存**：**`agreeRequest`** 成功后，对 **`item_quantity`（盈亏）≠ 0** 且 **`compareTo(ZERO) != 0`** 之行：先 **`SupplyDeliveryDomainService#createStocktakingDelivery`**（**`DifferenceLineCommand.quantity` = 行 `item_quantity`**，可正可负），再按符号分支：**`quantity > 0`** → **`InventoryItemDomainService#increaseQuantity`**；**`quantity < 0`** → **`InventoryItemDomainService#decreaseQuantity`**（**`allowNegative=true`**，扣减量为 **`abs(quantity)`**）。**禁止**用 **`total_quantity`（实盘）** 覆盖库存。**整单全部为零**：不调用 **`createStocktakingDelivery`**（避免空 `items`），同意仍成功、库存不变。 |
| **WHS-ST-SDD-R-002** | **IF-WHS-ST-05 修订**：**`CreateStocktakeCommand`** 首行带既有 **`busNo`** → **`assertStocktakeLinesRevisable`** → 旧行软删 → 全量重插；**不**发布 **`SupplyRequestCreatedEvent`**（与出库修订同构）。 |
| **WHS-ST-SDD-R-003** | **选品/详情单价**：与 **`WHS-IN-SDD-R-011`** 同构 — 读锚仅 **`LOT_NUMBER_COST`** 或 **`PURCHASE`**（均每包装）；**禁止**读 **`SALE`**、**`LOT_NUMBER_PRICE`**。 |
| **WHS-ST-SDD-R-004** | **供应行数量**：盘点 **`item_quantity`** 允许 ≤0、可负；**禁止**对盘点同意路径调用 **`SupplyPersistedSplitQuantity#requireMedicationOrDeviceTableAndPositiveSplitQuantity`**；入账数量直接取行 **`item_quantity`**。 |
| **WHS-ST-SDD-R-005** | **时间/操作者**：与 **AS-INV-WHS-002-SDD `WHS-IN-SDD-R-013`** 一致 — 草稿 **`apply_time=null`**；**`submitApproval`** 写 **`apply_time`**；**`practitionerId`** 请求体必填。 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/warehouse/stocktake/
  service/
    InventoryWarehouseStocktakeAppService.java
  request/
    LoadStocktakeFormInitRequest.java
    PageStocktakesRequest.java
    GetStocktakeDetailRequest.java
    CreateStocktakeRequest.java
    UpdateStocktakeRequest.java
    SubmitStocktakeApprovalRequest.java
    WithdrawStocktakeApprovalRequest.java
    AgreeStocktakeRequest.java
    RejectStocktakeRequest.java
    RemoveStocktakeRequest.java
    PageProductsForStocktakeRequest.java
  response/
    LoadStocktakeFormInitResponse.java
    PageStocktakesResponse.java
    GetStocktakeDetailResponse.java
    CreateStocktakeResponse.java
    UpdateStocktakeResponse.java
    SubmitStocktakeApprovalResponse.java
    WithdrawStocktakeApprovalResponse.java
    AgreeStocktakeResponse.java
    RejectStocktakeResponse.java
    RemoveStocktakeResponse.java
    PageProductsForStocktakeResponse.java
  dto/
    StocktakeListRowDto.java
    StocktakeLineDto.java
    StocktakeDetailLineDto.java
    ProductDefinitionItemDto.java
  mapper/
    InventoryWarehouseStocktakeMapper.java
    InventoryWarehouseStocktakeMapperImpl.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentManagedOrganizationIdsResolver` | 会话科室 **`requireSessionOrganizationUnitId`**（**B.6.5**）；**`PractitionerRoleScopeLoader`** org 入参 |
| `CurrentOperatorResolver` | 操作人主键 **`requireOperatorId`**（**B.6.5**） |
| `PageWindowGate` | 分页边界 **B.10.11** |
| `BusinessSequenceService` | **`bus_no`**（**`CST`**） |
| `SupplyRequestDomainService` | **`createStocktake`**、审批链、**`removeByBusNo`**、**`flushPendingChanges`** |
| `SupplyRequestQueryService` | 详情/校验/列表 |
| `SupplyDeliveryDomainService` | **`createStocktakingDelivery`**（**IF-WHS-ST-08**） |
| `SupplyDeliveryQueryService` | **`findRequestIdsHavingCompletedDelivery`**（防重） |
| `InventoryItemDomainService` | **`increaseQuantity`** / **`decreaseQuantity`** |
| `InventoryItemQueryService` | 选品、账面量、五元组定位 |
| `LocationQueryService` | **`listAllActiveWarehouses`**（**IF-WHS-ST-01** 下拉主数据） |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**（**IF-WHS-ST-01**）、**`gatedLocationIdsFromActive`**（**IF-WHS-ST-02/04/11** **`purposeLocationId`** 药库门控） |
| `PractitionerQueryService` | 制单人 **`findByUserId`** | IF-WHS-ST-01 |
| `PractitionerRoleScopeLoader` | **`listActivePractitionersForRoleInOrganization`**（org 由 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 传入；Loader 层不直读会话） | IF-WHS-ST-01 |
| `WhaleRoleLookup` | `findRoleIdByCode` | IF-WHS-ST-01 |
| `ProductDefinitionBatchLoader` | 选品/详情装配 |
| `ProductDefinitionSelectionTerminologysupport | 选品列表术语预热 |
| `ChargeItemDefinitionQueryService` | **`LOT_NUMBER_COST`** / **`PURCHASE`** |
| `ProductDefinitionPurchasePriceResolver` | 价格解析 |
| `ProductDefinitionCatalogEditorHintsAssembler` | 详情目录提示 |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`unitBands`** |
| `InventoryWarehouseStocktakeMapper` | DTO 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActiveWarehouses`** | IF-WHS-ST-01、02、04、05、11 |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** | IF-WHS-ST-01、02、04、05、11 |
| `PractitionerQueryService` | **`findByUserId`** | IF-WHS-ST-01 |
| `PractitionerRoleScopeLoader` | **`listActivePractitionersForRoleInOrganization`**（org 由 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 传入；Loader 层不直读会话） | IF-WHS-ST-01 |
| `WhaleRoleLookup` | `findRoleIdByCode` | IF-WHS-ST-01 |
| `SupplyRequestDomainService` | **`createStocktake`** | IF-WHS-ST-04、05 |
| `SupplyRequestDomainService` | **`submitApproval`** | IF-WHS-ST-06 |
| `SupplyRequestDomainService` | **`withdrawApproval`** | IF-WHS-ST-07 |
| `SupplyRequestDomainService` | **`agreeRequest`** | IF-WHS-ST-08 |
| `SupplyRequestDomainService` | **`rejectRequest`** | IF-WHS-ST-09 |
| `SupplyRequestDomainService` | **`removeByBusNo`** | IF-WHS-ST-10 |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`**、**`supplyRequestValidation`**、**`verifyAbleEdit`** | 多 IF Pre |
| `SupplyRequestQueryService` | **`countDistinctBusNo`**、**`listDistinctBusNoPage`**、**`listByBusNosWithFilter`** | IF-WHS-ST-02 |
| `SupplyDeliveryDomainService` | **`createStocktakingDelivery`** | IF-WHS-ST-08 |
| `SupplyDeliveryQueryService` | **`findRequestIdsHavingCompletedDelivery`** | IF-WHS-ST-08 Pre |
| `InventoryItemDomainService` | **`increaseQuantity`**、**`decreaseQuantity`** | IF-WHS-ST-08 |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`**、**`findFirstIdByInventoryMatchKey`** | IF-WHS-ST-11；04/05 可选校验 |
| `ProductDefinitionBatchLoader` | **`loadAsMap`** | IF-WHS-ST-03、11 |
| `ChargeItemDefinitionQueryService` | **`findLotNumberCostByInstanceIdAndLot`**、**`findPurchaseItemByInstanceId`** | IF-WHS-ST-11、03 |
| `ProductDefinitionPurchasePriceResolver` | **`resolveLotNumberCostPrice`**、**`resolvePurchasePrice`** | IF-WHS-ST-11、03 |
| `ProductDefinitionCatalogEditorHintsAssembler` | **`fromDisplayDto`** | IF-WHS-ST-03 |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`buildInboundSelectionBands`** 等 | IF-WHS-ST-11、03 |
| `BusinessSequenceService` | **`getSeqByDay`** | IF-WHS-ST-04 |
| `SupplyRequestDomainService` | **`flushPendingChanges`** | IF-WHS-ST-08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 | **`CreateStocktakeRequest`** 根装配 **`CABINET_STOCKTAKE`** | `DM-WKF-006` §4.1.1 **`CABINET_STOCKTAKE`** | IF-WHS-ST-04/05 |
| 单据状态 | **`StocktakeListRowDto.statusEnum`**；**`LoadStocktakeFormInitResponse.statusOptions`** | `DM-WKF-006` §4.2 | IF-WHS-ST-02、01 |
| **`SupplyType`** | **`com.zxplat.domain.share.enums.SupplyType`** | `DM-WKF-006` §4.1 | 全 IF |
| **`SupplyStatus`** | **`com.zxplat.domain.share.enums.SupplyStatus`** | `DM-WKF-006` §4.2 | 审批链 |
| **`SupplyRequestType`**（落库） | **`CreateStocktakeCommand`** 行 **`purposeTypeEnum`**（**`LocationQueryService#requireSupplyRequestTypeForLocationId`** 推导） | `DM-WKF-006` §4.4；§3.1.1 | HTTP 不传 type，Mapper 写入 |
| **`SupplyCategory`** | **`CreateStocktakeRequest.categoryEnum`** | `DM-WKF-006` §4.3 | 默认 **`STOCK_SUPPLY`** |
| 行盈亏/实盘 | **`StocktakeLineDto.profitLossQuantity`** → **`item_quantity`**；**`actualQuantity`** → **`total_quantity`** | `DM-WKF-006` §3、`§6.1.7` | IF-WHS-ST-04/05/03 |
| 行金额 | **`actualAmount`** → **`total_price`**；**`profitLossAmount`** → **`profit_loss_price`** | `DM-WKF-006` §3 | 同上 |
| **`PriceType.PURCHASE`** | **`com.zxplat.domain.share.enums.PriceType#PURCHASE`** | ProductDefinition QueryService | IF-WHS-ST-03 |
| **`PriceType.LOT_NUMBER_COST`** | **`com.zxplat.domain.share.enums.PriceType#LOT_NUMBER_COST`** | 收费项批号价 | IF-WHS-ST-11 |

## 3. 按接口 ID 命名展开

先读完各 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-WHS-ST-01 | `loadStocktakeFormInit` | 进入盘点页面初始化 | `POST /api/app/inventoryWarehouseStocktake/loadStocktakeFormInit` |
| IF-WHS-ST-02 | `pageStocktakes` | 盘点单分页列表 | `POST /api/app/inventoryWarehouseStocktake/pageStocktakes` |
| IF-WHS-ST-03 | `getStocktakeDetail` | 盘点单详情 | `POST /api/app/inventoryWarehouseStocktake/getStocktakeDetail` |
| IF-WHS-ST-04 | `createStocktake` | 新建盘点单 | `POST /api/app/inventoryWarehouseStocktake/createStocktake` |
| IF-WHS-ST-05 | `updateStocktake` | 编辑盘点单 | `POST /api/app/inventoryWarehouseStocktake/updateStocktake` |
| IF-WHS-ST-06 | `submitStocktakeApproval` | 提交审批 | `POST /api/app/inventoryWarehouseStocktake/submitStocktakeApproval` |
| IF-WHS-ST-07 | `withdrawStocktakeApproval` | 撤回审批 | `POST /api/app/inventoryWarehouseStocktake/withdrawStocktakeApproval` |
| IF-WHS-ST-08 | `agreeStocktake` | 审批同意 | `POST /api/app/inventoryWarehouseStocktake/agreeStocktake` |
| IF-WHS-ST-09 | `rejectStocktake` | 审批驳回 | `POST /api/app/inventoryWarehouseStocktake/rejectStocktake` |
| IF-WHS-ST-10 | `removeStocktake` | 删除单据 | `POST /api/app/inventoryWarehouseStocktake/removeStocktake` |
| IF-WHS-ST-11 | `pageProductsForStocktake` | 盘点选品检索 | `POST /api/app/inventoryWarehouseStocktake/pageProductsForStocktake` |

**HTTP 路径生成规则（硬门禁）**：**`POST /api/app/inventoryWarehouseStocktake/{methodName}`**，**禁止** `GET`/`PUT` 等。

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 参数 / 位置门控 | 不进入后续 |
| **Pre** | 只读加载与 BR | 抛异常，**禁止 Cmd** |
| **Cmd** | 领域写 | 事务见 §5 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-WHS-ST-01 `loadStocktakeFormInit`（进入盘点页面初始化）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/loadStocktakeFormInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | N | 忽略，以 **`CurrentManagedOrganizationIdsResolver`** 会话为准 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `warehouseLocationOptions` | `List<OptionDto>` | **仅药库**（**不**含药房列表） |
| `inboundItemTableTypeOptions` | `List<OptionDto>` | 药品/耗材定义表两档 |
| `statusOptions` | `List<OptionDto>` | **SupplyStatus** |
| `pharmacistOptions` | `List<OptionDto>` | 可选 |
| `warehouseKeeperOptions` | `List<OptionDto>` | 可选 |

**失败码**：`WHS_ST_101`（**EX-WHS-ST-101**）

**Mapper**：`toLoadStocktakeFormInitResponse(...): LoadStocktakeFormInitResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 会话有效 |  |  |
| 2 | Pre | `LocationQueryService#listAllActiveWarehouses` |  | 药库全集 |
| 3 | Pre | （装配 · 下拉） | ② | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → **`warehouseLocationOptions`**；**管辖 empty 不收窄** |
| 4 | Asm | `InventoryWarehouseStocktakeMapper#toLoadStocktakeFormInitResponse` | ③ 及 **`statusOptions`/`inboundItemTableTypeOptions`** / 可选人员下拉（与实现一致） | 仅读 |

---

### 3.3 IF-WHS-ST-02 `pageStocktakes`（盘点单分页列表）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/pageStocktakes`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | N | 盘点药库筛选 |
| `statusEnum` | String | N | **`SupplyStatus#getCode()`** |
| `keyword` | String | N | 单号等 |
| `pageNo` | Integer | Y | 1-based |
| `pageSize` | Integer | Y |  |
| `startDate` / `endDate` | `LocalDateTime` | N | 申请时间窗 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | Long |  |
| `pageNo` | Integer |  |
| `pageSize` | Integer |  |
| `content` | `List<StocktakeListRowDto>` |  |

**DTO：`StocktakeListRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String |  |
| `statusEnum` | String |  |
| `purposeLocationId` | Long | 盘点库房 |
| `purposeLocationName` | String | 展示名（应用层富化） |
| `itemCount` | Integer | 明细行数 |
| `profitLossQuantitySum` | `BigDecimal` | 可选汇总：盈亏数量合计 |
| `actualAmountSum` | `BigDecimal` | 可选汇总：实盘金额合计 |
| `applicantName` | String |  |
| `applyTime` | `LocalDateTime` |  |
| `approverName` | String |  |
| `approvalTime` | `LocalDateTime` |  |
| `occurrenceTime` | `LocalDateTime` | 头行制单时间 |

**失败码**：`WHS_ST_102`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate` |  |
| 2 | Pre | `SupplyRequestQueryService#countDistinctBusNo` | **`typeEnum=CABINET_STOCKTAKE`** |
| 3 | Pre | `SupplyRequestQueryService#listDistinctBusNoPage` | 分页 **`bus_no`** |
| 4 | Pre | `SupplyRequestQueryService#listByBusNosWithFilter` | 拉全量行聚合 |
| 5 | Asm | `InventoryWarehouseStocktakeMapper#toPageStocktakesResponse` | 富化位置/人员名 |

---

### 3.4 IF-WHS-ST-03 `getStocktakeDetail`（盘点单详情）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/getStocktakeDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String |  |
| `statusEnum` | String |  |
| `purposeLocationId` | Long |  |
| `purposeLocationName` | String |  |
| `practitionerId` | Long |  |
| `applicantId` | Long |  |
| `applyTime` | `LocalDateTime` |  |
| `approverId` | Long |  |
| `approvalTime` | `LocalDateTime` |  |
| `lines` | `List<StocktakeDetailLineDto>` |  |

**DTO：`StocktakeDetailLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `lineId` | Long | **`wkf_supply_request.id`** |
| `itemTable` | String |  |
| `itemId` | Long |  |
| `productName` | String |  |
| `specification` | String |  |
| `manufacturer` | String |  |
| `lotNumber` | String |  |
| `expirationDate` | `LocalDateTime` | 效期 |
| `unitCode` | String |  |
| `bookQuantity` | `BigDecimal` | **展示**：当前账面量（实时查库存或保存时快照，**SDD 实现二选一须在 §8 固定**） |
| `profitLossQuantity` | `BigDecimal` | 持久化 **`item_quantity`** |
| `actualQuantity` | `BigDecimal` | **`total_quantity`** |
| `actualAmount` | `BigDecimal` | **`total_price`** |
| `profitLossAmount` | `BigDecimal` | **`profit_loss_price`** |
| `price` | `BigDecimal` | 行单价（可选） |
| `remake` | String |  |
| `occurrenceTime` | `LocalDateTime` |  |
| `productCatalog` | `ProductDefinitionCatalogEditorHintsDto` | 多单位目录（**WHS-ST-SDD-R-003**） |

**失败码**：`WHS_ST_103`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | 类型须 **`CABINET_STOCKTAKE`** |
| 2 | Pre | `ProductDefinitionBatchLoader#loadAsMap` |  |
| 3 | Pre | `InventoryItemQueryService`（可选） | 装配 **`bookQuantity`** |
| 4 | Asm | `InventoryWarehouseStocktakeMapper#toGetStocktakeDetailResponse` |  |

---

### 3.5 IF-WHS-ST-04 `createStocktake`（新建盘点单）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/createStocktake`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 盘点药库 |
| `practitionerId` | Long | Y | 经手人 |
| `categoryEnum` | String | N | 默认 **`STOCK_SUPPLY`** |
| `lines` | `List<StocktakeLineDto>` | Y | 至少一行 |

**DTO：`StocktakeLineDto`（写）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemTable` | String | Y |  |
| `itemId` | Long | Y |  |
| `profitLossQuantity` | `BigDecimal` | Y | 盈亏，可负可零 |
| `actualQuantity` | `BigDecimal` | N | 实盘 ≥0 |
| `actualAmount` | `BigDecimal` | N |  |
| `profitLossAmount` | `BigDecimal` | N |  |
| `unitCode` | String | Y |  |
| `lotNumber` | String | N |  |
| `inventoryItemId` | Long | N | 选品锚点 |
| `price` | `BigDecimal` | N |  |
| `remake` | String | N |  |
| `occurrenceTime` | `LocalDateTime` | N |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 新生成 **`CST`** 流水 |

**失败码**：`WHS_ST_001`、`WHS_ST_002`

**Mapper**：`toCreateStocktakeCommand(CreateStocktakeRequest, Long operatorId, String purposeTypeEnum): CreateStocktakeCommand`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | **`purposeLocationId`** ∈ **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActiveWarehouses)`** | 与 **IF-WHS-ST-01** **`warehouseLocationOptions`** 同源；**管辖 empty → 启用态药库全量 id** |  |
| 2 | Gate | 明细非空、物品表白名单 |  |
| 3 | Pre | `LocationQueryService#requireSupplyRequestTypeForLocationId` | 得 **`purposeTypeEnum`** |
| 4 | Cmd | `SupplyRequestDomainService#createStocktake(CreateStocktakeCommand)` | **写库点**；**`typeEnum`=`CABINET_STOCKTAKE`** |
| 5 | Asm | `toCreateStocktakeResponse` |  |

---

### 3.6 IF-WHS-ST-05 `updateStocktake`（编辑盘点单）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/updateStocktake`

**Request**：同 **IF-WHS-ST-04**，且根级 **`busNo`** 必填。

**Response**：`busNo`

**失败码**：`WHS_ST_004`、`WHS_ST_005`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `SupplyRequestQueryService#verifyAbleEdit` |  |
| 2 | Cmd | `createStocktake`（修订：**WHS-ST-SDD-R-002**，首行 **`busNo`** 软删+重插） | **写库点** |

---

### 3.7～3.10 审批与删除（`submit` / `withdraw` / `reject` / `remove`）

与 **AS-INV-WHS-003-SDD** **§3.8～3.10** 同构：`SupplyRequestDomainService#submitApproval` / `#withdrawApproval` / `#rejectRequest` / `#removeByBusNo`；Request 含 **`busNo`**；失败码 **`WHS_ST_003`** / **`WHS_ST_005`**。

---

### 3.11 IF-WHS-ST-08 `agreeStocktake`（审批同意）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/agreeStocktake`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String |  |
| `statusEnum` | String | **`AGREE`** |

**失败码**：`WHS_ST_003`、`WHS_ST_104`（**EX-WHS-ST-104**）

**Mapper**：`toCreateStocktakingDeliveryCommand(...)`、`toIncreaseQuantityCommand` / `toDecreaseQuantityCommand`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 审批权限 |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | **`APPROVAL`** |  |
| 3 | Pre | `SupplyRequestQueryService#supplyRequestValidation` | 防重复同意 |  |
| 4 | Cmd | `SupplyRequestDomainService#agreeRequest` |  | **写库点** **`AGREE`** |
| 5 | Cmd | `SupplyRequestDomainService#flushPendingChanges` |  |  |
| 6 | Pre | `getSupplyByBusNo` |  | 取同意行 |
| 7 | Pre | `SupplyDeliveryQueryService#findRequestIdsHavingCompletedDelivery` | 批量防重 |  |
| 8 | Cmd | `SupplyDeliveryDomainService#createStocktakingDelivery` | 仅 **`item_quantity≠0`** 行；**`quantity=item_quantity`**；**装配后 items 为空则跳过本步** | **写库点**（可跳过） |
| 9 | Cmd | 逐行：`quantity>0` → **`increaseQuantity`**；`quantity<0` → **`decreaseQuantity(allowNegative=true)`** | **`purpose_location_id`** + 批号五元组 | **写库点** **WHS-ST-SDD-R-001** |
| 10 | Asm | `toAgreeStocktakeResponse` |  |  |

---

### 3.12 IF-WHS-ST-11 `pageProductsForStocktake`（盘点选品检索）

**HTTP**：`POST /api/app/inventoryWarehouseStocktake/pageProductsForStocktake`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 盘点药库（**非** `sourceLocationId`） |
| `productTableName` | String | Y |  |
| `positiveQuantityOnly` | Boolean | N | **`true`**：仅盘点库账面 **`quantity > 0`**；**`false`/未传：不过滤 |
| `keyword` | String | N |  |
| `pageNo` / `pageSize` | Integer | Y |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | Long |  |
| `content` | `List<ProductDefinitionItemDto>` |  |

**DTO：`ProductDefinitionItemDto`**（与 **IF-WHS-SO-11** 列族一致；**`availableQuantity`** 作账面/可用量展示；**UI 须展示 `lotNumber`**，见 **AS-INV-WHS-003 §1.3**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `productTableName` | String |  |
| `productDefinitionId` | Long |  |
| `productBusNo` | String | 目录 **`bus_no`**（**`ProductDefinitionDisplayDto#getProductBusNo()`**） |
| `productName` | String |  |
| `specification` | String |  |
| `manufacturer` | String |  |
| `lotNumber` | String |  |
| `expirationDate` | `LocalDateTime` |  |
| `availableQuantity` | `BigDecimal` | **`purposeLocationId`** 下库存量 |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` |  |
| `purchaseUnitPrice` | `BigDecimal` | **`LOT_NUMBER_COST`**（**WHS-ST-SDD-R-003**） |
| `inventoryItemId` | Long |  |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | **`purposeLocationId`** ∈ **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActiveWarehouses)`** | 与 **IF-WHS-ST-01** **`warehouseLocationOptions`** 同源；**管辖 empty → 启用态药库全量 id** |  |
| 2 | Pre | `InventoryItemQueryService#queryInventoryItemsByPage` | **`locationId=purposeLocationId`** |  |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` |  |  |
| 4 | Asm | **`resolveLotNumberCostPrice`** + **`buildInboundSelectionBands`** + Mapper | 仅读 |

---

## 4. QueryService 与事件刷新

| 读路径 | 数据源 | 说明 |
| --- | --- | --- |
| 列表/详情主路径 | **`wkf_supply_request`** + **`SupplyRequestQueryService`** | 与进货/出库列表一致，**禁止**用 WarehouseDocumentDetail 替代列表 |
| 选品 | **`InventoryItemQueryService`** + **ProductDefinition** | 批号级 |
| 投影 | **SupplyRequest** 领域事件 | 列表/待办由既有QueryService收敛（与库房单据通用） |

---

## 5. 写库顺序（核心写接口视角）

| IF | 事务内顺序（摘要） |
| --- | --- |
| IF-WHS-ST-04 | **`createStocktake`** → **`wkf_supply_request` INSERT** → **CreatedEvent** |
| IF-WHS-ST-05 | 软删旧行 → INSERT 新行（目标态） |
| IF-WHS-ST-06～09 | 状态字段更新 + 领域事件 |
| IF-WHS-ST-08 | **`agreeRequest`** → **`wkf_supply_delivery` INSERT（差异行）** → **`wkf_inventory_item` UPDATE（逐行盈亏）**；任一步失败 **整单回滚** |

---

## 6. 非功能性需求实现（承诺表）

| 类别 | 承诺 |
| --- | --- |
| 性能 | 选品分页；大批量盘点同意可批处理库存更新（实现可选） |
| 幂等 | 同意/提交重复请求幂等 |
| 并发 | 乐观锁 **`version`** |
| 一致性 | 同意与库存变更、发放记录对外一致 |
| 审计 | 审批人、盘点库房、盈亏/实盘可追溯 |

---

## 7. 测试要点

| 类别 | 要点 |
| --- | --- |
| 正向 | 新建 → 提交 → 同意；盘盈增、盘亏减 |
| 零盈亏 | 行 **`profitLossQuantity=0`** 同意无发放、无库存变；**整单全零亦同意成功** |
| 负盈亏 | **`profitLossQuantity<0`** 可保存且同意扣减 |
| init | 仅药库下拉 |
| 字段回显 | 详情四类数量/金额与保存一致 |
| 防重 | 二次同意拒绝 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v0.4 | 2026-05-29 | **WHS-ST-SDD-R-003**：选品/详情单价与 **`WHS-IN-SDD-R-011`** 最终版对齐 — 批号 **`LOT_NUMBER_COST`** / 目录 **`PURCHASE`** 均为每包装锚 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v0.1 | 2026-05-22 | 初稿：**`CABINET_STOCKTAKE`** 目标态 SDD |
| v0.3 | 2026-05-24 | **库位门控统一**：**`CurrentManagedLocationIdsResolver`**（ **`#narrowActiveLocationsByManaged`** / **`#gatedLocationIdsFromActive`**）；**剔除** **`PractitionerRoleQueryService#listAssignmentsByPractitioner`** 及 **`narrowLocationsByManagedIds`**；**§2.1**、§2.2、§3.2、§3.5、§3.11 |
| v0.2 | 2026-05-22 | 领域 **`createStocktake` 修订** 已落地，**WHS-ST-SDD-R-002** 与代码对齐 |

### 8.1 目标态待生成（应用层）

| 项 | 计划 FQCN |
| --- | --- |
| 应用服务 | **`InventoryWarehouseStocktakeAppService`** |

---

**文档负责人**：（待填） · **评审**：（待填）
