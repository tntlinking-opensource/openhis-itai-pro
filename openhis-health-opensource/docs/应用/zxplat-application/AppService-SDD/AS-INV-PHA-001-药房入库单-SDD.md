# 药房入库单 · 应用服务详细设计（SDD）

> 编码：AS-INV-PHA-001-SDD  
> 版本：v1.0.7 | 日期：2026-05-29 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药房入库单应用服务（库存 · 药房 · **`PHARMACY_STOCK_IN`**，与 **`com.zxplat.domain.share.enums.SupplyType#PHARMACY_STOCK_IN#getCode()`** 一致） |
| 边界 | **只读台账**：列表、详情（**IF-PHA-IN-01～03**）。**写路径**（**IF-PHA-IN-04～11**）统一 **Gate → `PHA_IN_002`**，单据**仅**由 **AS-INV-WHS-003** 出库 agree 生成（**`AGREE`**，`original_bus_no`=出库 `bus_no`）。库存落账与追溯在出库 agree 完成。**不**补录 **`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`** |
| 编排权威 | `AS-INV-PHA-001-药房入库单.md`（**v0.11**）§2、§4 |
| 方法权威 | `DM-WKF-006` §6；`DM-WKF-005` §6；`DM-WKF-008` §6；`DM-ADM-003` §6.2；`DM-CUS-010` §6；QueryService **`DM-WKF-006 §6.2 `SupplyRequestQueryService` / `InventoryItemQueryService``**、**`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService`** §3 |
| 严格对齐 | IF **IF-PHA-IN-01～11** 与 AS 母表一致 |
| 实现阶段口径 | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | 枚举/状态/类型一律 **`getCode()`** 字符串（B.10.8.1） |
| **`bus_no` 生成** | **`BusinessSequenceService#getSeqByDay`**；**`SupplyType.busNoSeqKey`** = **`PSI`**；回退 **`SR`** |
| QueryService口径 | 列表以 **`wkf_supply_request`** **`SupplyRequestQueryService`** **`bus_no` 去重**分页为主；详情 **`getSupplyByBusNo`**；选品 **库存分页 + ProductDefinition**（与 **IF-WHS-SO-11** 同构） |
| 事务口径 | **IF-PHA-IN-08** 单事务覆盖 **`agreeRequest` → `createSupplyDelivery` → `InventoryItem` → 追溯** |

### 1.3 实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **PHA-SDD-R-001** | **BR-PHA-IN-012**：**`getSupplyByBusNo`** 若 **`status_enum`=`AGREE`** 且 **`type_enum`=`PHARMACY_STOCK_IN`** 且 **`original_bus_no`** 语义为 **药库出库生成**（**以产品/领域白名单解析**，与 **AS-INV-WHS-003** **BR-WHS-SO-012** 对称），**则** **IF-PHA-IN-05/06/07/08/09/10** **Gate 直接拒绝**（**EX-PHA-IN-003** / **004** 等口径由错误码表区分）。 |
| **PHA-SDD-R-002** | **IF-PHA-IN-08 幂等（**AS IF-PHA-IN-08** 与 **GAP-PHA-IN-001**）**：**若** 手工单 **`original_bus_no`** 指示「**对侧出库已全量完成库存迁移**」**且** 只读证据表明 **目的库存已入账**（**`SupplyDeliveryQueryService#supplyDeliveryValidation`** + **`InventoryItemQueryService`** 命中），**则** **禁止** **`createSupplyDelivery`** 与 **`increaseQuantity`** 二次执行；**若** 领域尚未提供显式门闩，**则** **Gate** 拒绝本接口或返回幂等已处理（**须**在 **GAP-PHA-IN-001** 关闭后收敛为 **DM** 唯一语义）。 |
| **PHA-SDD-R-003** | **修订**：**`createRequisition(CreateRequisitionCommand)`** 携带既有 **`busNo`** 时执行明细全量对齐（与 **`createStockOut`** 修订语义对偶）；领域未支持前 **Cmd 不得投产**。 |
| **PHA-SDD-R-004** | **IF-PHA-IN-11 / IF-PHA-IN-03**：与 **`WHS-IN-SDD-R-011`** 同构 — 读锚仅 **`PURCHASE`**、**`LOT_NUMBER_COST`**（均每包装）；**`#applyInboundWarehouseBandPrices`** / **`#applyInboundWarehouseDetailLineBandPrices`**；**禁止**读 **`SALE`**、**`LOT_NUMBER_PRICE`**。 |
| **PHA-SDD-R-005** | **追溯**：**`operation_type`** = **`PHARMACY_STOCK_IN`**（**`SupplyType#getCode()`**）；**仅手工同意路径**（**BR-PHA-IN-011**）。 |
| **PHA-SDD-R-006** | **IF-PHA-IN-08 库存入账数量**：与 **WHS-SO-SDD-R-007** / **WHS-IN-SDD-R-007** 同构 — **`item_quantity`** 已为拆零下持久化；**`createInventoryItem` / `increaseQuantity`** 取 **`SupplyPersistedSplitQuantity#requireSupplyLineItemQuantityAsPersistedSplitUnit`**；**`unitCode`** = **`splitUnitCode`**（**`loadAsMap`**）；**禁止** **`MedicationProductQuantityToSplitUnitConverter`** 于本同意链。**`createInventoryItem`** 场景 **`CreateInventoryItemCommand`** 之 **`productionDate`/`expirationDate`/`categoryCode`/`pyStr`/`wbStr`** 装配与 **`AS-INV-WHS-002-SDD` `WHS-IN-SDD-R-012`** / **`InventoryWarehouseInboundReceiptMapper#toCreateInventoryItemCommand`** 同构（**`increaseQuantity`** 不回写冗余列）。 |
| **PHA-SDD-R-007** | **选品可用量与四元组口径**（历史手工路径；**IF-PHA-IN-11 已废止**）：与 **`WHS-SO-SDD-R-008`** 对偶同构。 |
| **PHA-SDD-R-008** | **IF-PHA-IN-04～11**：AppService 入口 **Gate** 调用 **`rejectManualPharmacyStockInWrite`**，错误码 **`PHA_IN_002`**（与 **WHS_TI_002** 调入壳单同构）。 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/pharmacy/stockin/
  service/
    InventoryPharmacyStockInAppService.java
  request/
    LoadPharmacyStockInFormInitRequest.java
    PagePharmacyStockInsRequest.java
    GetPharmacyStockInDetailRequest.java
    CreatePharmacyStockInRequest.java
    UpdatePharmacyStockInRequest.java
    SubmitPharmacyStockInApprovalRequest.java
    WithdrawPharmacyStockInApprovalRequest.java
    AgreePharmacyStockInRequest.java
    RejectPharmacyStockInRequest.java
    RemovePharmacyStockInRequest.java
    PageProductsForPharmacyStockInRequest.java
  response/
    LoadPharmacyStockInFormInitResponse.java
    PagePharmacyStockInsResponse.java
    GetPharmacyStockInDetailResponse.java
    CreatePharmacyStockInResponse.java
    UpdatePharmacyStockInResponse.java
    SubmitPharmacyStockInApprovalResponse.java
    WithdrawPharmacyStockInApprovalResponse.java
    AgreePharmacyStockInResponse.java
    RejectPharmacyStockInResponse.java
    RemovePharmacyStockInResponse.java
    PageProductsForPharmacyStockInResponse.java
  dto/
    PharmacyStockInListRowDto.java
    PharmacyStockInLineDto.java
    PharmacyStockInDetailLineDto.java
    ProductDefinitionItemDto.java
  mapper/
    InventoryPharmacyStockInMapper.java
    InventoryPharmacyStockInMapperImpl.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 / 审批 **`requireOperatorId`**；制单人 **`findByUserId`** 前 Gate（**B.6.5**） |
| `CurrentManagedOrganizationIdsResolver` | 会话科室 **`requireSessionOrganizationUnitId`**（**B.6.5**）；**`PractitionerRoleScopeLoader`** org 入参 |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**（**IF-PHA-IN-01**）、**`gatedLocationIdsFromActive`**（**IF-PHA-IN-02/04/05/11** Gate；**B.6.5**） |
| `BusinessSequenceService` | **`bus_no`**（**`PSI`**） |
| `SupplyRequestDomainService` | **`createRequisition`**、**`submitApproval`**、**`withdrawApproval`**、**`agreeRequest`**、**`rejectRequest`**、**`removeByBusNo`** |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`**、**`supplyRequestValidation`**、**`verifyAbleEdit`**、列表去重分页三件套 |
| `SupplyDeliveryDomainService` | **`createSupplyDelivery`**（**IF-PHA-IN-08**） |
| `SupplyDeliveryQueryService` | **`supplyDeliveryValidation`** |
| `InventoryItemDomainService` | **`createInventoryItem`**、**`increaseQuantity`**（**IF-PHA-IN-08**） |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`**、**`findFirstIdByInventoryMatchKey`** |
| `TraceNumberDomainService` | **`recordTraceRecords`** / **`recordTraceRecordsFromLineItems`** |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActivePharmacies`**（**IF-PHA-IN-01** 主数据：**仅药房** + 药库；Gate 经 **`CurrentManagedLocationIdsResolver`**；**非**药库出库目的「药房族」口径，出库见 **AS-INV-WHS-003** **`listAllActivePharmacyPartyLocations`**） |
| `PractitionerQueryService` | **IF-PHA-IN-01** 制单人 **`findByUserId`** |
| `PractitionerRoleScopeLoader` | **IF-PHA-IN-01** 经手人/库管 **`listActivePractitionersForRoleInOrganization`**（org 由 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 传入；Loader 层不直读会话） |
| `WhaleRoleLookup` | **IF-PHA-IN-01** 平台角色解析 |
| `ProductDefinitionBatchLoader` | **`pageProductForTransfer`**、**`loadAsMap`** |
| `ProductDefinitionSelectionTerminologysupport | 选品/列表术语预热（`application.shared.product`） |
| `ChargeItemDefinitionQueryService` | **`findPurchaseItemByInstanceId`**、**`findLotNumberCostByInstanceIdAndLot`** |
| `ProductDefinitionPurchasePriceResolver` | **`resolvePurchasePrice`**、**`resolveLotNumberCostPrice`** |
| `ProductDefinitionCatalogEditorHintsAssembler` | **`fromDisplayDto`** |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`buildInboundSelectionBands`**、**`resolvePurchaseUnitRatioForInboundPricing`**、**`applyInboundBandsPriceUsingPurchasePackagingRatio`**、**`applyUnitPricingToBandsIfPresent`** |
| `SupplyPersistedSplitQuantity` | **`requireSupplyLineItemQuantityAsPersistedSplitUnit`**（**static**；**IF-PHA-IN-08** / **PHA-SDD-R-006**） |
| `SupplyRequestDomainService` | **`flushPendingChanges`** |
| `InventoryPharmacyStockInMapper` | DTO 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActivePharmacies`** | IF-PHA-IN-01；IF-PHA-IN-02、04、05、11 Gate（经 **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**） |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** | IF-PHA-IN-01～02、04～05、11 |
| `PractitionerQueryService` | **`findByUserId`** | IF-PHA-IN-01 |
| `PractitionerRoleScopeLoader` | **`listActivePractitionersForRoleInOrganization`** | IF-PHA-IN-01 |
| `WhaleRoleLookup` | `findRoleIdByCode` | IF-PHA-IN-01 |
| `SupplyRequestDomainService` | **`createRequisition`** | IF-PHA-IN-04、IF-PHA-IN-05 |
| `SupplyRequestDomainService` | **`submitApproval`** | IF-PHA-IN-06 |
| `SupplyRequestDomainService` | **`withdrawApproval`** | IF-PHA-IN-07 |
| `SupplyRequestDomainService` | **`agreeRequest`** | IF-PHA-IN-08 |
| `SupplyRequestDomainService` | **`rejectRequest`** | IF-PHA-IN-09 |
| `SupplyRequestDomainService` | **`removeByBusNo`** | IF-PHA-IN-10 |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`**、**`supplyRequestValidation`**、**`verifyAbleEdit`** | 多 IF |
| `SupplyRequestQueryService` | **`countDistinctBusNo`**、**`listDistinctBusNoPage`**、**`listByBusNosWithFilter`** | IF-PHA-IN-02 |
| `ProductDefinitionBatchLoader` | **`pageProductForTransfer`**、**`loadAsMap`** | IF-PHA-IN-11、IF-PHA-IN-03；**IF-PHA-IN-08**（**PHA-SDD-R-006**） |
| `ChargeItemDefinitionQueryService` | **`findLotNumberCostByInstanceIdAndLot`** | IF-PHA-IN-11 |
| `ChargeItemDefinitionQueryService` | **`findPurchaseItemByInstanceId`** | IF-PHA-IN-11（`unitBands` 兜底）、IF-PHA-IN-03 |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`** | IF-PHA-IN-11 |
| `InventoryItemQueryService` | **`findFirstIdByInventoryMatchKey`** | IF-PHA-IN-08 |
| `SupplyDeliveryDomainService` | **`createSupplyDelivery`** | IF-PHA-IN-08 |
| `SupplyDeliveryQueryService` | **`supplyDeliveryValidation`** | IF-PHA-IN-08 |
| `InventoryItemDomainService` | **`createInventoryItem`**、**`increaseQuantity`** | IF-PHA-IN-08 |
| `TraceNumberDomainService` | **`recordTraceRecords`**、**`recordTraceRecordsFromLineItems`** | IF-PHA-IN-08 |
| `SupplyPersistedSplitQuantity` | **`requireSupplyLineItemQuantityAsPersistedSplitUnit`** | IF-PHA-IN-08 |
| `ProductDefinitionPurchasePriceResolver` | **`resolveLotNumberCostPrice`** | IF-PHA-IN-11 |
| `ProductDefinitionPurchasePriceResolver` | **`resolvePurchasePrice`** | IF-PHA-IN-03 |
| `ProductDefinitionCatalogEditorHintsAssembler` | **`fromDisplayDto`** | IF-PHA-IN-03 |
| `ProductDefinitionSelectionTerminologysupport | **`loadSelectionTerminologyMaps`**、**`applyInboundUnitBandDisplayNames`** | IF-PHA-IN-03（**`enrichPharmacyStockInDetailProductDisplay`**） |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`buildInboundSelectionBands`**、**`resolvePurchaseUnitRatioForInboundPricing`**、**`applyInboundBandsPriceUsingPurchasePackagingRatio`**、**`applyUnitPricingToBandsIfPresent`**、**`resolveUnitDisplayName`** | IF-PHA-IN-03、IF-PHA-IN-11 |
| `BusinessSequenceService` | **`getSeqByDay`** | IF-PHA-IN-04 |
| `SupplyRequestDomainService` | **`flushPendingChanges`** | IF-PHA-IN-08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 | **`CreatePharmacyStockInRequest`** 装配 **`PHARMACY_STOCK_IN`** | `DM-WKF-006` §4.1.2 | IF-PHA-IN-04/05 |
| 单据状态 | **`PharmacyStockInListRowDto.statusEnum`** | `DM-WKF-006` §4.2；§3.1 | IF-PHA-IN-02 |
| **`SupplyType`** | **`com.zxplat.domain.share.enums.SupplyType`** | `DM-WKF-006` §4.1 |  |
| **`SupplyStatus`** | **`com.zxplat.domain.share.enums.SupplyStatus`** | `DM-WKF-006` §4.2 | **BR-PHA-IN-012** |
| **`SupplyRequestType`**（落库） | **`CreateRequisitionCommand`** 行装配之 **`sourceTypeEnum`/`purposeTypeEnum`**（**`SupplyRequestType#getCode()`**） | `DM-WKF-006` §4.4 | **HTTP 请求体不传**：**`InventoryPharmacyStockInMapper`** 据 **`sourceLocationId`/`purposeLocationId`** 经 **`LocationQueryService#requireSupplyRequestTypeForLocationId`** 推导；**BR-PHA-IN-002** |
| **`SupplyCategory`** | **`CreatePharmacyStockInRequest.categoryEnum`** | `DM-WKF-006` §4.3 |  |
| 追溯 **`operation_type`** | **`TraceNumberRecord` 命令字段** = **`PHARMACY_STOCK_IN`** | **BR-PHA-IN-011**；`DM-CUS-010`（**GAP-PHA-IN-002**） | IF-PHA-IN-08 |
| **`PURCHASE`** | **`com.zxplat.domain.share.enums.PriceType#PURCHASE`** | **ProductDefinition** QueryService §3 | IF-PHA-IN-03；**IF-PHA-IN-11** `unitBands` 无 **`LOT_NUMBER_COST`** 命中时兜底 |
| **`LOT_NUMBER_COST`** | **`com.zxplat.domain.share.enums.PriceType#LOT_NUMBER_COST`** | 收费项定价行 **`lot_no`** 与库存行 **`lot_number`** 对齐 | IF-PHA-IN-11 **`purchaseUnitPrice`**（**PHA-SDD-R-004**） |
| 系统单门禁 | **`GetPharmacyStockInDetailRequest.busNo`** + **`SupplyRequestModel.statusEnum`** + **`originalBusNo`** | **BR-PHA-IN-012** | IF-PHA-IN-03 只读 |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向 / 反向 | §2.2 ↔ §3 全量调用一致 |
| 互斥 | IF-PHA-IN-04 新建 vs IF-PHA-IN-05 修订；**IF-PHA-IN-08** **`PHA-SDD-R-002`** 库存跳过 vs 正常入库链 |

## 3. 按接口 ID 命名展开

先读完各 IF 的「领域编排序列（严格顺序）」，按序号执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-PHA-IN-01 | `loadPharmacyStockInFormInit` | 进入药房入库页面初始化 | `POST /api/app/inventoryPharmacyStockIn/loadPharmacyStockInFormInit` |
| IF-PHA-IN-02 | `pagePharmacyStockIns` | 药房入库单分页列表 | `POST /api/app/inventoryPharmacyStockIn/pagePharmacyStockIns` |
| IF-PHA-IN-03 | `getPharmacyStockInDetail` | 药房入库单详情 | `POST /api/app/inventoryPharmacyStockIn/getPharmacyStockInDetail` |
| IF-PHA-IN-04 | `createPharmacyStockIn` | 新建药房入库单 | `POST /api/app/inventoryPharmacyStockIn/createPharmacyStockIn` |
| IF-PHA-IN-05 | `updatePharmacyStockIn` | 编辑药房入库单 | `POST /api/app/inventoryPharmacyStockIn/updatePharmacyStockIn` |
| IF-PHA-IN-06 | `submitPharmacyStockInApproval` | 提交审批 | `POST /api/app/inventoryPharmacyStockIn/submitPharmacyStockInApproval` |
| IF-PHA-IN-07 | `withdrawPharmacyStockInApproval` | 撤回审批 | `POST /api/app/inventoryPharmacyStockIn/withdrawPharmacyStockInApproval` |
| IF-PHA-IN-08 | `agreePharmacyStockIn` | 审批同意（手工单） | `POST /api/app/inventoryPharmacyStockIn/agreePharmacyStockIn` |
| IF-PHA-IN-09 | `rejectPharmacyStockIn` | 审批驳回 | `POST /api/app/inventoryPharmacyStockIn/rejectPharmacyStockIn` |
| IF-PHA-IN-10 | `removePharmacyStockIn` | 删除单据 | `POST /api/app/inventoryPharmacyStockIn/removePharmacyStockIn` |
| IF-PHA-IN-11 | `pageProductsForPharmacyStockIn` | 药房入库选品检索 | `POST /api/app/inventoryPharmacyStockIn/pageProductsForPharmacyStockIn` |

**HTTP 路径规则**：**`POST /api/app/inventoryPharmacyStockIn/{methodName}`**。

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 参数 / **BR-PHA-IN-012** 系统单门禁 | 不再调用后续 |
| **Pre** | 只读校验 | 禁止进入 Cmd |
| **Cmd** | 领域写 + 事件 | 回滚见 §5 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-PHA-IN-01 `loadPharmacyStockInFormInit`（进入药房入库页面初始化）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/loadPharmacyStockInFormInit`

**Request**：`organizationId` Long N（忽略，同进货）

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pharmacyLocationOptions` | `List<OptionDto>` | **目的药房** |
| `warehouseLocationOptions` | `List<OptionDto>` | **来源药库** |
| `inboundItemTableTypeOptions` | `List<OptionDto>` | 两档 |
| `statusOptions` | `List<OptionDto>` | **SupplyStatus** |
| `pharmacistOptions` / `warehouseKeeperOptions` | `List<OptionDto>` | 可选 |

**失败码**：`PHA_IN_101`

**Mapper**：`toLoadPharmacyStockInFormInitResponse(...): LoadPharmacyStockInFormInitResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 会话 |  |  |
| 2 | Pre | `LocationQueryService#listAllActivePharmacies` |  |  |
| 3 | Pre | `LocationQueryService#listAllActiveWarehouses` |  |  |
| 4 | Pre | （装配 · 下拉） | ② | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → **`pharmacyLocationOptions`**；**管辖 empty 不收窄** |
| 5 | Pre | （装配 · 下拉） | ③ | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → **`warehouseLocationOptions`** |
| 6 | Asm | `InventoryPharmacyStockInMapper#toLoadPharmacyStockInFormInitResponse` | ④⑤ | 仅读 |

---

### 3.3 IF-PHA-IN-02 `pagePharmacyStockIns`（药房入库单分页列表）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/pagePharmacyStockIns`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | **目的药房**；传 **`0`** 表示「门控允许的**全部可见药房**」：**`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActivePharmacies)`** 写入 `purposeLocationIds`（**`purpose_location_id IN (...)`**，与 **IF-WHS-SO-02** 源药库 `0` 对偶）；**管辖 empty 则门控为启用态药房全量 id**。**无命中**时占位 id 集避免扫全表。非 **`0`** 时等值过滤并须落在门控集合内。 |
| `sourceLocationId` | Long | N | **从哪入** 筛选 |
| `statusEnum` | String | N |  |
| `keyword` | String | N |  |
| `applyTimeFrom` / `applyTimeTo` | `LocalDateTime` | N |  |
| `pageNo` / `pageSize` | Integer | Y |  |
| `typeEnum` | String | N | 固定 **`PHARMACY_STOCK_IN`** |

**Response**：`total`；`records` **`List<PharmacyStockInListRowDto>`**

**DTO：`PharmacyStockInListRowDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y |  |
| `typeEnum` | String | Y |  |
| `statusEnum` | String | Y |  |
| `sourceLocationId` | Long | Y | **从哪入** |
| `sourceLocationName` | String | N | 展示 |
| `purposeLocationId` | Long | Y |  |
| `applyTime` | `LocalDateTime` | N |  |
| `approvalTime` | `LocalDateTime` | N |  |
| `applicantId` / `approverId` | Long | N |  |
| `originalBusNo` | String | N | 系统生成时展示上游出库号 |

**失败码**：`PHA_IN_102`

**Mapper**：`toPagePharmacyStockInsResponse(...): PagePharmacyStockInsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **`purposeLocationId`** ∈ **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActivePharmacies)`** | 药房 id 集合；**管辖 empty → 启用态药房全量** |  |
| 2 | Pre | `SupplyRequestQueryService#countDistinctBusNo` | **`SupplyRequestDocumentListQuery`**：`typeEnum`=`PHARMACY_STOCK_IN` | 仅读 |
| 3 | Pre | `listDistinctBusNoPage` | 同上 |  |
| 4 | Pre | `listByBusNosWithFilter` |  |  |
| 5 | Asm | Mapper |  | 富化 **来源位置名** |

---

### 3.4 IF-PHA-IN-03 `getPharmacyStockInDetail`（药房入库单详情）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/getPharmacyStockInDetail`

**Request**：`busNo` String Y

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String |  |
| `typeEnum` | String | **`PHARMACY_STOCK_IN`** |
| `statusEnum` | String |  |
| `sourceLocationId` / `purposeLocationId` | Long |  |
| `sourceTypeEnum` / `purposeTypeEnum` | String | 读侧回显 **`SupplyRequestType#getCode()`**（与落库一致；**IF-PHA-IN-04/05 请求体不传**，由 Mapper 按库位推导） |
| `categoryEnum` | String |  |
| `originalBusNo` | String | N |
| `lines` | `List<PharmacyStockInDetailLineDto>` |  |

**DTO：`PharmacyStockInDetailLineDto`**（与 **`StockOutDetailLineDto`** 列族对偶）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | N |  |
| `itemTable` | String | Y |  |
| `itemId` | Long | Y |  |
| `productName` | String | N | QueryService **`product_name`**（**`enrichPharmacyStockInDetailProductDisplay`**；与 **IF-WHS-SO-03** **`enrichStockOutDetailProductDisplay`** **同构**；**系统壳单**须下发，**禁止** Mapper 固定 null） |
| `specification` | String | N | QueryService **`total_volume`** |
| `manufacturer` | String | N | 厂家产地合一（QueryService **`manufacturer`**） |
| `itemQuantity` | `BigDecimal` | Y |  |
| `unitCode` | String | Y |  |
| `unitCodeDictText` | String | N |  |
| `lotNumber` | String | N |  |
| `startTime` / `endTime` | `LocalDateTime` | N | 效期 |
| `traceNo` | String | N |  |
| `remake` | String | N |  |
| `price` / `totalPrice` | `BigDecimal` | N |  |
| `occurrenceTime` | `LocalDateTime` | N |  |
| `productCatalog` | `ProductDefinitionCatalogEditorHintsDto` | N | 多单位目录提示；嵌套字段见下表 **`ProductDefinitionCatalogEditorHintsDto`** |

**DTO：`ProductDefinitionCatalogEditorHintsDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | N | 四槽含 **`PACKAGE`** |
| `purchasePrice` | `BigDecimal` | N | **`PURCHASE`** 解析后写入 |
| `productBasePrice` | `BigDecimal` | N | QueryService基础标价 |
| `minUnitCode` | String | N | 拆零单位 |
| `defaultPurchaseUnitCode` | String | N | 默认采购单位 |
| `merchandiseName` | String | N | 商品名 |
| `categoryCode` | String | N | 分类编码 |
| `categoryCodeDictText` | String | N | 分类展示 |
| `productBusNo` | String | N | 业务流水号 |
| `totalVolume` | String | N | 规格/装量 |

**DTO：`ProductDefinitionCatalogUnitBandDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bandKind` | String | Y | **`ProductDefinitionCatalogUnitBandConstants`** 之 **`BAND_*`** |
| `unitCode` | String | Y | 单位编码 |
| `unitName` | String | N | 展示名 |
| `ratioToSplit` | `BigDecimal` | N | 相对拆零倍数；拆零槽为 1 |
| `unitPrice` | `BigDecimal` | N | 槽单价（元） |

**失败码**：`PHA_IN_103`

**Mapper**：`toGetPharmacyStockInDetailResponse(...): GetPharmacyStockInDetailResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | **`PHARMACY_STOCK_IN`** |
| 3 | Gate | **BR-PHA-IN-012** | ② 快照 | 系统 **`AGREE`** 单仅读；**若**非法写入口径则拒绝 |
| 4 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 行 **`itemTable`+`itemId`** 复合键 | 读模型降级为空映射时仍返回写模型字段 |
| 5 | Pre | `ProductDefinitionSelectionTerminologySupport#loadSelectionTerminologyMaps` |  | 单位展示名 |
| 6 | Asm | `ProductDefinitionCatalogEditorHintsAssembler#fromDisplayDto` | ④ 每行 | 行 id → **`productCatalog`** |
| 7 | Asm | `InventoryPharmacyStockInMapper#toGetPharmacyStockInDetailResponse` | ②～⑥ | 写模型主体 |
| 8 | Asm | **`InventoryPharmacyStockInAppService#enrichPharmacyStockInDetailProductDisplay`** | ④ 读模型 | **`productName`/`specification`/`manufacturer`/`unitCodeDictText`**；**`original_bus_no` 系统壳单**与手工单共用 |
| 9 | Asm | **`InventoryPharmacyStockInAppService#enrichPharmacyStockInDetailLines`** | **`ChargeItemDefinitionQueryService#resolveFirstPurchasePriceValue`** + **`ProductDefinitionCatalogUnitBandsAssembler`** | **`productCatalog.purchasePrice`** 与槽 **`unitPrice`** |

**前端（IF-PHA-IN-03 只读展示）**：加载详情后 **项目类型**（选品 **`productTableName`**）须按明细首行非空 **`itemTable`** 回填，**禁止**固定默认药品表导致壳单（如耗材出库生成）表头与行 **`itemTable`** 不一致。

---

### 3.5 IF-PHA-IN-04 `createPharmacyStockIn`（新建药房入库单）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/createPharmacyStockIn`

**Request（根）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 目的药房 |
| `sourceLocationId` | Long | Y | 来源药库 |
| `categoryEnum` | String | N | 可缺省或 blank 时由 Mapper 按 **`STOCK_SUPPLY`** 默认（**`SupplyCategory#getCode()`**） |
| `originalBusNo` | String | N | **BR-PHA-IN-003** |
| `busNo` | String | N |  |
| `lines` | `List<PharmacyStockInLineDto>` | Y | 明细行 |

**DTO：`PharmacyStockInLineDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemTable` | String | Y | 物品来源表 |
| `itemId` | Long | Y | 物品定义主键 |
| `itemQuantity` | `BigDecimal` | Y | 数量 |
| `unitCode` | String | Y | 计量单位编码 |
| `lotNumber` | String | N | 批号 |
| `traceNo` | String | N | 追溯码 |
| `price` | `BigDecimal` | N | 单价 |
| `totalPrice` | `BigDecimal` | N | 行总价 |
| `remake` | String | N | 备注 |
| `occurrenceTime` | `LocalDateTime` | N | 业务发生时间 |

**Response**：`busNo` String Y

**失败码**：`PHA_IN_001`

**Mapper**：`toCreateRequisitionCommand(CreatePharmacyStockInRequest): CreateRequisitionCommand`（**`typeEnum`=`PHARMACY_STOCK_IN`**）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 行非空 |  |  |
| 2 | Gate | 位置门控 | **目的** ∈ **`gatedLocationIdsFromActive(listAllActivePharmacies)`**；**来源** ∈ **`gatedLocationIdsFromActive(listAllActiveWarehouses)`**（同源 **IF-PHA-IN-01**，**管辖 empty 不收窄**） | **BR-PHA-IN-002** |
| 3 | Pre | **若** `originalBusNo` 非空 **`getSupplyByBusNo`** |  | **BR-PHA-IN-003** |
| 4 | Pre | **`calculateTotalAvailableInventory`** | **`sourceLocationId`** | **BR-PHA-IN-013** |
| 5 | Cmd | `SupplyRequestDomainService#createRequisition(CreateRequisitionCommand)` |  | **写库点** |
| 6 | Asm | `toCreatePharmacyStockInResponse` |  |  |

---

### 3.6 IF-PHA-IN-05 `updatePharmacyStockIn`（编辑药房入库单）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/updatePharmacyStockIn`

**Request**：同 IF-PHA-IN-04 + **`busNo`** Y

**失败码**：`PHA_IN_004`、`PHA_IN_105`；**BR-PHA-IN-012** → **`PHA_IN_004`**

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **`busNo`** + **BR-PHA-IN-012** |  | 系统单拒绝 |
| 2 | Pre | `getSupplyByBusNo` |  |  |
| 3 | Pre | `verifyAbleEdit` |  | **BR-PHA-IN-005** |
| 4 | Pre | 可用量 |  | **BR-PHA-IN-013** |
| 5 | Cmd | `createRequisition`（修订 **PHA-SDD-R-003**） |  | **写库点** |
| 6 | Asm | Mapper |  |  |

---

### 3.7 IF-PHA-IN-06 `submitPharmacyStockInApproval`（提交审批）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/submitPharmacyStockInApproval`

**Request**：`busNo` String Y

**失败码**：`PHA_IN_003`；**BR-PHA-IN-012** → 同码

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **BR-PHA-IN-012** | `getSupplyByBusNo` | 系统 **`AGREE`** 拒绝 |
| 2 | Pre | `getSupplyByBusNo` |  | **`PENDING_APPROVAL`** |
| 3 | Cmd | `submitApproval(SubmitApprovalCommand)` |  | **写库点** |
| 4 | Asm | Mapper |  |  |

---

### 3.8 IF-PHA-IN-07 `withdrawPharmacyStockInApproval`（撤回审批）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/withdrawPharmacyStockInApproval`

**Request**：`busNo` Y

**失败码**：`PHA_IN_003`；**BR-PHA-IN-012**

**领域编排序列**：Gate（**BR-012**）→ `getSupplyByBusNo`（**`APPROVAL`**）→ **`withdrawApproval(WithdrawApprovalCommand)`** → Asm

---

### 3.9 IF-PHA-IN-08 `agreePharmacyStockIn`（审批同意 · 手工单）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/agreePharmacyStockIn`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y |  |
| `approvalTime` | `LocalDateTime` | Y |  |
| `approverId` | Long | N |  |

**失败码**：`PHA_IN_003`、`PHA_IN_104`

**Mapper**：`toAgreeRequestCommand`、`toCreateSupplyDeliveryCommand`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **BR-PHA-IN-012** | `busNo` | 系统生成 **`AGREE`** 拒绝 |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | **`APPROVAL`** |
| 3 | Pre | `SupplyRequestQueryService#supplyRequestValidation` |  | **BR-PHA-IN-010** |
| 4 | Pre | **PHA-SDD-R-002** 只读校验 | **`SupplyDeliveryQueryService#supplyDeliveryValidation`** 等 | **若**已存在完成发放且库存已加，**则**抛 **`PHA_IN_104`**（防双加；**GAP-PHA-IN-001** 关闭后以 **DM** 为准） |
| 5 | Cmd | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` |  | **写库点** |
| 6 | Cmd | `SupplyRequestDomainService#flushPendingChanges` |  |  |
| 7 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | **AGREE** 行 |
| 8 | Pre | `SupplyDeliveryQueryService#supplyDeliveryValidation` |  | 防重复发放 |
| 9 | Cmd | `SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)` |  | **写库点** |
| 10 | Cmd | **`SupplyPersistedSplitQuantity#requireSupplyLineItemQuantityAsPersistedSplitUnit`** + **`InventoryItemDomainService#createInventoryItem(CreateInventoryItemCommand)`** / **`#increaseQuantity(IncreaseQuantityCommand)`**（数量取校验后之 **`item_quantity`**；**`unitCode`** = **`splitUnitCode`**，**PHA-SDD-R-006**） | 目的药房 | **写库点** |
| 11 | Cmd | `TraceNumberDomainService#recordTraceRecordsFromLineItems` | **`operation_type`=`PHARMACY_STOCK_IN`** | **写库点**；**`traceNo`** 空白跳过 |
| 12 | Asm | `InventoryPharmacyStockInMapper#toAgreePharmacyStockInResponse` |  |  |

---

### 3.10 IF-PHA-IN-09 `rejectPharmacyStockIn`（审批驳回）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/rejectPharmacyStockIn`

**Request**：`busNo` Y；`changeReasonCode` / `changeReason` **N**（均可空；非空时应用层优先将 `changeReasonCode` 落入领域驳回项，其次 `changeReason` 自由文本）。

**失败码**：`PHA_IN_003`；**BR-012**

**领域编排序列**：Gate（**BR-012**）→ `getSupplyByBusNo`（**`APPROVAL`**）→ **`rejectRequest(RejectRequestCommand)`** → Asm

---

### 3.11 IF-PHA-IN-10 `removePharmacyStockIn`（删除单据）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/removePharmacyStockIn`

**Request**：`busNo` Y

**失败码**：`PHA_IN_005`；**BR-012**

**领域编排序列**：Gate（**BR-012**）→ `getSupplyByBusNo` → **`removeByBusNo(RemoveByBusNoCommand)`** → Asm

---

### 3.12 IF-PHA-IN-11 `pageProductsForPharmacyStockIn`（药房入库选品检索）

**HTTP**：`POST /api/app/inventoryPharmacyStockIn/pageProductsForPharmacyStockIn`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | 来源药库 |
| `productTableName` | String | Y | **`med_medication_definition`** / **`adm_device_definition`** |
| `keyword` | String | N |  |
| `pageNo` / `pageSize` | Integer | Y |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | 总行数 |
| `records` | `List<ProductDefinitionItemDto>` | 选品行 |

**DTO：`ProductDefinitionItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productTableName` | String | Y | 与 **`item_table`** 一致 |
| `productDefinitionId` | Long | Y |  |
| `productBusNo` | String | N | 目录 **`bus_no`**（**`ProductDefinitionDisplayDto#getProductBusNo()`**） |
| `productName` | String | N |  |
| `specification` | String | N |  |
| `manufacturer` | String | N |  |
| `lotNumber` | String | N | 批号 |
| `expirationDate` | `LocalDateTime` | N | 效期 |
| `availableQuantity` | `BigDecimal` | Y | **`sourceLocationId`** 可用量 |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | N | 每元素 **`bandKind`**（**`BAND_*`**）、**`unitCode`**、**`unitName`**、**`ratioToSplit`**、**`unitPrice`**（元） |
| `purchaseUnitPrice` | `BigDecimal` | N | **`LOT_NUMBER_COST`**（**`PHA-SDD-R-004`**）：随本行 **`lotNumber`**；无批号或无命中为 **`null`** |
| `inventoryItemId` | Long | N | 库存行锚点 |

**失败码**：`PHA_IN_102`

**Mapper**：`toPageProductsForPharmacyStockInResponse(...): PageProductsForPharmacyStockInResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **`sourceLocationId`** ∈ **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActiveWarehouses)`** | 同源 **IF-WHS-SO-11**/**IF-WHS-IN-02** 药库门控 |  |
| 2 | Pre | `InventoryItemQueryService#queryInventoryItemsByPage` |  | 批号主表 |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` |  |  |
| 4 | Asm | **`buildInboundSelectionBands`** + **`resolveLotNumberCostPrice`**（批号随 **`lotNumber`**）+ **`resolvePurchasePrice`**（槽价兜底）+ **`resolvePurchaseUnitRatioForInboundPricing`** + **`applyInboundBandsPriceUsingPurchasePackagingRatio`** + Mapper |  | **PHA-SDD-R-004** |

## 4. QueryService 与事件刷新

| 数据源 | 事件 | 意图 |
| --- | --- | --- |
| **`wkf_supply_request`** 投影链 | **DM-WKF-006** §5 事件 | 列表/详情刷新 |
| **ProductDefinition** | 目录事件 | 选品/详情装配 |
| **InventoryItem** | 数量事件 | 可用量 |

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 对照

| IF | App 方法 | Cmd |
| --- | --- | --- |
| IF-PHA-IN-04 | `createPharmacyStockIn` | `createRequisition` |
| IF-PHA-IN-05 | `updatePharmacyStockIn` | `createRequisition` |
| IF-PHA-IN-06 | `submitPharmacyStockInApproval` | `submitApproval` |
| IF-PHA-IN-07 | `withdrawPharmacyStockInApproval` | `withdrawApproval` |
| IF-PHA-IN-08 | `agreePharmacyStockIn` | `agreeRequest` → `createSupplyDelivery` → `InventoryItem` → `Trace` |
| IF-PHA-IN-09 | `rejectPharmacyStockIn` | `rejectRequest` |
| IF-PHA-IN-10 | `removePharmacyStockIn` | `removeByBusNo` |

### 5.2 事务边界

| 接口 | 事务 |
| --- | --- |
| IF-PHA-IN-08 | 单事务覆盖 **§3.9** 序号 **5～11** |
| 其它写接口 | 单事务 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 同意防重 | **`supplyRequestValidation`** | `SupplyRequestQueryService` | §3.9 序号 3、8 |
| 出库生成单 | **BR-012 Gate** | `InventoryPharmacyStockInAppService` | §3.7～§3.8、§3.10～§3.11 |

### 6.2 并发控制

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 编辑冲突 | **`verifyAbleEdit`** | §3.6 |

### 6.3 重试与容错

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 查询失败 | **EX-PHA-IN-102** | §3.3、§3.12 |

### 6.4 数据脱敏

| 项 | 承诺 |
| --- | --- |
| 追溯码 | 展示截断策略 |

### 6.5 审计日志

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 审批与追溯 | **`agreeRequest`** + **`cus_trace_number` + `cus_trace_number_flow`** | §3.9 序号 5、11 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 手工新建→提交→同意→目的库存增加 + 追溯 **`PHARMACY_STOCK_IN`** | 集成 | P0 |
| 系统生成 **`AGREE`** 单列表可见且 **不可提交/同意** | API | P0 |

### 7.2 非功能测试

| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 同意链路失败回滚 | 集成 | P1 |

### 7.3 QueryService一致性

| 用例 | 优先级 |
| --- | --- |
| 事件后列表一致 | P0 |

### 7.4 集成测试

| 用例 | 优先级 |
| --- | --- |
| 与 **AS-INV-WHS-003** 出库同意生成 **`original_bus_no`** 对账 | P0 |

### 7.5 测试缺口

| 缺口 | 说明 |
| --- | --- |
| **GAP-PHA-IN-001/002** | **PHA-SDD-R-002** 条件检测待 DM 关闭后固化断言 |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0.8 | 2026-05-29 | **PHA-SDD-R-004**：选品/详情单价与 **`WHS-IN-SDD-R-011`** 最终版对齐 — 维护态 **`PURCHASE`**、批号 **`LOT_NUMBER_COST`** 均为每包装锚 |
| v1.0.7 | 2026-05-29 | **IF-PHA-IN-03**：补齐 **`enrichPharmacyStockInDetailProductDisplay`**（与 **IF-WHS-SO-03** **`enrichStockOutDetailProductDisplay`** 同构）；**`PharmacyStockInDetailLineDto`** **`productName`/`specification`/`manufacturer`** 脚注；§3.4 编排序列序号 **8～9**；§2.2 白名单补 **`ProductDefinitionSelectionTerminologysupport**；**系统壳单**详情展示与手工单一致；前端 **项目类型** 按首行 **`itemTable`** 回填 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0.6 | 2026-05-24 | **库位门控统一**：**`CurrentManagedLocationIdsResolver`**；**剔除** **`PractitionerRoleQueryService#listAssignmentsByPractitioner`** 与「岗位收窄」表述；§2.1、§2.2、§3.2～§3.5、§3.12 |
| v1.0.5 | 2026-05-21 | **IF-PHA-IN-08** 持久化刷盘改为 **`SupplyRequestDomainService#flushPendingChanges`**（**B.2** 禁应用层直调 **`SupplyRequestRepository#flush`**）；§2.1/§2.2 同步 |
| v1.0.4 | 2026-05-15 | **PHA-SDD-R-006**：**`createInventoryItem`** 冗余列装配与 **`AS-INV-WHS-002-SDD` `WHS-IN-SDD-R-012`** / **`InventoryWarehouseInboundReceiptMapper#toCreateInventoryItemCommand`** 同构；§1.3 |
| v1.0.3 | 2026-05-14 | **PHA-SDD-R-004**：**IF-PHA-IN-11** **`purchaseUnitPrice`** 真源改为 **`LOT_NUMBER_COST`**（随库存行 **`lotNumber`**）；与 **药库进货 IF-WHS-IN-11**（**`PURCHASE`**）业务分流；§2.1、§2.2、§2.3、§3.12；与 **AS-INV-WHS-003-SDD** **WHS-SO-SDD-R-004** 互指 |
| v1.0.2 | 2026-05-14 | **PHA-SDD-R-006**：手工同意入账改 **`SupplyPersistedSplitQuantity`** + **`splitUnitCode`**（**`loadAsMap`**）；§2.1/§2.2、§3.9 序号 **10**；**剔除** **`MedicationProductQuantityToSplitUnitConverter`** 白名单；编排权威 **AS v0.8** |
| v1.0.1 | 2026-05-14 | **§3.3** 明确 `purposeLocationId=0` 展开为 `purposeLocationIds` IN 谓词；**§3.10** 明确驳回原因双字段可空及 Mapper 合并优先级 |
| v1.0 | 2026-05-14 | 初稿：对齐 **AS-INV-PHA-001** IF-PHA-IN-01～11；**§3.4**/**§3.12** 嵌套 DTO 全量展开；**PHA-SDD-R-002** 置于 **§3.9** **Pre** 序号 4 |
