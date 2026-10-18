# 药房请领单 · 应用服务详细设计（SDD）

> 编码：AS-INV-PHA-002-SDD  
> 版本：v0.4 | 日期：2026-07-24 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药房请领单应用服务（库存 · 药房 · **`PHARMACY_REQUISITION`** / type **12**） |
| 边界 | 编排 **`createRequisition`**（申请单）建单、修订、审批、删除；**选品**以 **`InventoryItemQueryService`** 批号行为主 + **ProductDefinition** 装配（与 **IF-PHA-IN-11** 同构）；**同意**仅 **`agreeRequest`** → **`AGREE`**，**禁止** **`createStockOut`** / **SupplyDelivery** / **InventoryItem** 写侧 |
| 编排权威 | `AS-INV-PHA-002-药房请领单.md` §2、§4 |
| 方法权威 | `DM-WKF-006` §6.1.9、§6.1.12～16；`DM-ADM-003` §6.2；`DM-WKF-008` §6.2；QueryService **`DM-WKF-006 §6.2`** |
| 严格对齐 | IF **IF-PHA-QL-01～11** 与 AS 母表一致 |
| 实现阶段口径 | **目标态全量契约**（后端 **`InventoryPharmacyRequisitionAppService`** 待生成） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | 枚举/状态/类型一律 **`getCode()`** 字符串（B.10.8.1） |
| **`bus_no` 生成** | **`BusinessSequenceService#getSeqByDay`**；**`SupplyType.busNoSeqKey`** = **`PQR`**；回退 **`SR`** |
| 位置口径 | **`source_location_id`** = 药库；**`purpose_location_id`** = 药房（**DM 物流双端点**） |
| QueryService 口径 | 列表 **`SupplyRequestDocumentListQuery`** **`bus_no` 去重**；详情 **`getSupplyByBusNo`**；选品库存分页 + ProductDefinition |
| 事务口径 | 每个写接口默认单事务；Pre/Gate 失败**禁止**进入 Cmd |

### 1.3 实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **PHA-QL-SDD-R-001** | **IF-PHA-QL-08**：仅 **`SupplyRequestDomainService#agreeRequest`**；**禁止**同事务内 **`createStockOut`** / **`createRequisition`**（type 13 壳单）；与 **AS-INV-WHS-001** **WHS-SDD-R-001** 对偶 |
| **PHA-QL-SDD-R-002** | **修订（IF-PHA-QL-05）**：**`CreateRequisitionCommand`** 首行携带既有 **`busNo`** 且存量为 **`PHARMACY_REQUISITION`** 时，领域 **`createRequisition`** 软删同号行后重插；**不**发布 **`SupplyRequestCreatedEvent`**（领域已实现 **PHA-QL-SDD-R-002**） |
| **PHA-QL-SDD-R-003** | **选品（IF-PHA-QL-11）**：与 **`AS-INV-PHA-001-SDD`** **IF-PHA-IN-11** 编排序列同构（**`pageProductForTransfer`**、**`LOT_NUMBER_COST`**、**`unitBands`**、可用量门控）；错误码前缀 **`PHA_QL_`** |
| **PHA-QL-SDD-R-004** | **下游关联**：药库出库 **`getLinkablePharmacyRequisitionDetail`** / **`pageLinkableRequisitions`** 归 **AS-INV-WHS-003** **IF-WHS-SO-12/13**；请领列表过滤 **`sourceLocationId`** = 当前药库（修正旧 SDD 将 purpose 误标为药库之口径） |

## 2. 代码结构

```text
com/openhis/health/application/inventory/pharmacy/requisition/
  service/
    InventoryPharmacyRequisitionAppService.java
  request/
    LoadPharmacyRequisitionFormInitRequest.java
    PagePharmacyRequisitionsRequest.java
    GetPharmacyRequisitionDetailRequest.java
    CreatePharmacyRequisitionRequest.java
    UpdatePharmacyRequisitionRequest.java
    SubmitPharmacyRequisitionApprovalRequest.java
    WithdrawPharmacyRequisitionApprovalRequest.java
    AgreePharmacyRequisitionRequest.java
    RejectPharmacyRequisitionRequest.java
    RemovePharmacyRequisitionRequest.java
    PageProductsForPharmacyRequisitionRequest.java
  response/
    LoadPharmacyRequisitionFormInitResponse.java
    PagePharmacyRequisitionsResponse.java
    GetPharmacyRequisitionDetailResponse.java
    CreatePharmacyRequisitionResponse.java
    UpdatePharmacyRequisitionResponse.java
    SubmitPharmacyRequisitionApprovalResponse.java
    WithdrawPharmacyRequisitionApprovalResponse.java
    AgreePharmacyRequisitionResponse.java
    RejectPharmacyRequisitionResponse.java
    RemovePharmacyRequisitionResponse.java
    PageProductsForPharmacyRequisitionResponse.java
  dto/
    PharmacyRequisitionListRowDto.java
    PharmacyRequisitionLineDto.java
    PharmacyRequisitionDetailLineDto.java
    ProductDefinitionItemDto.java
  mapper/
    InventoryPharmacyRequisitionMapper.java
    InventoryPharmacyRequisitionMapperImpl.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentPractitionerResolver` | 写路径 **`requireOperatorId`** |
| `CurrentManagedOrganizationIdsResolver` | 会话机构 **`requireSessionOrganizationUnitId`** |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** |
| `PageWindowGate` | 分页参数门禁 |
| `BusinessSequenceService` | **`bus_no`**（**`PQR`**） |
| `SupplyRequestDomainService` | **`createRequisition`**、**`submitApproval`**、**`withdrawApproval`**、**`agreeRequest`**、**`rejectRequest`**、**`removeByBusNo`** |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`**、**`supplyRequestValidation`**、**`verifyAbleEdit`**、列表去重三件套 |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActivePharmacies`**、**`requireSupplyRequestTypeForLocationId`** |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`** |
| `ProductDefinitionBatchLoader` | **`pageProductForTransfer`**、**`loadAsMap`** |
| `ProductDefinitionSelectionTerminologysupport | 选品/详情术语 |
| `ChargeItemDefinitionQueryService` | **`findLotNumberCostByInstanceIdAndLot`**、**`findPurchaseItemByInstanceId`** |
| `ProductDefinitionPurchasePriceResolver` | **`resolveLotNumberCostPrice`**、**`resolvePurchasePrice`** |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`buildInboundSelectionBands`** 等同构方法 |
| `PractitionerQueryService` / `PractitionerRoleScopeLoader` | init 人员下拉（可选，与 **IF-PHA-IN-01** 同源） |
| `InventoryPharmacyRequisitionMapper` | DTO / Command 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActivePharmacies`**、**`requireSupplyRequestTypeForLocationId`** | IF-PHA-QL-01；IF-PHA-QL-02/04/05/11 Gate |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** | IF-PHA-QL-01～02、04～05、11 |
| `SupplyRequestDomainService` | **`createRequisition`** | IF-PHA-QL-04、IF-PHA-QL-05 |
| `SupplyRequestDomainService` | **`submitApproval`** | IF-PHA-QL-06 |
| `SupplyRequestDomainService` | **`withdrawApproval`** | IF-PHA-QL-07 |
| `SupplyRequestDomainService` | **`agreeRequest`** | IF-PHA-QL-08 |
| `SupplyRequestDomainService` | **`rejectRequest`** | IF-PHA-QL-09 |
| `SupplyRequestDomainService` | **`removeByBusNo`** | IF-PHA-QL-10 |
| `SupplyRequestQueryService` | **`countDistinctBusNo`**、**`listDistinctBusNoPage`**、**`listByBusNosWithFilter`** | IF-PHA-QL-02 |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`** | IF-PHA-QL-03、04～10 Pre |
| `SupplyRequestQueryService` | **`supplyRequestValidation`** | IF-PHA-QL-08 Pre |
| `SupplyRequestQueryService` | **`verifyAbleEdit`** | IF-PHA-QL-05 Pre |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`** | IF-PHA-QL-11；IF-PHA-QL-04/05 可用量断言 |
| `ProductDefinitionBatchLoader` | **`pageProductForTransfer`**、**`loadAsMap`** | IF-PHA-QL-11、IF-PHA-QL-03 |
| `ChargeItemDefinitionQueryService` | **`findLotNumberCostByInstanceIdAndLot`**、**`findPurchaseItemByInstanceId`** | IF-PHA-QL-11、IF-PHA-QL-03 |
| `ProductDefinitionPurchasePriceResolver` | **`resolveLotNumberCostPrice`**、**`resolvePurchasePrice`** | IF-PHA-QL-11、IF-PHA-QL-03 |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`buildInboundSelectionBands`** 等 | IF-PHA-QL-11、IF-PHA-QL-03 |
| `BusinessSequenceService` | **`getSeqByDay`** | IF-PHA-QL-04 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 `type_enum` | 后端固定 **`SupplyType.PHARMACY_REQUISITION#getCode()`** | `DM-WKF-006` §4.1.2 值 12 | IF-PHA-QL-04/05；请求体不传 |
| 单据状态 | **`PharmacyRequisitionListRowDto.statusEnum`** | `DM-WKF-006` §4.2 | IF-PHA-QL-02 |
| **`SupplyType`** | **`com.zxplat.domain.share.enums.SupplyType`** | `DM-WKF-006` §4.1 | |
| **`SupplyStatus`** | **`com.zxplat.domain.share.enums.SupplyStatus`** | `DM-WKF-006` §4.2 | agree/submit 门禁 |
| 源/目标位置类型 | **`CreateRequisitionCommand`** 行 **`sourceTypeEnum`/`purposeTypeEnum`** | `DM-WKF-006` §3.1.1 双端点；**BR-SR-017** | Mapper 经 **`requireSupplyRequestTypeForLocationId`** 推导 |
| **`SupplyCategory`** | **`CreatePharmacyRequisitionRequest.categoryEnum`** | `DM-WKF-006` §4.3 | 默认 **`STOCK_SUPPLY`** |
| 批号 `lot_number` | **`PharmacyRequisitionLineDto.lotNumber`** | `DM-WKF-006` §3.1 | 选品带入 |
| 计量单位 | **`PharmacyRequisitionLineDto.unitCode`** | `DM-WKF-006` §3.1 `unit_code` | 展示/录入；拆零量见 **BR-PHA-QL-010** |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向 / 反向 | §2.2 ↔ §3 全量调用一致 |
| 互斥 | IF-PHA-QL-04 新建（无 `busNo`）vs IF-PHA-QL-05 修订（有 `busNo`） |

## 3. 按接口 ID 命名展开

先读完各 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-PHA-QL-01 | `loadPharmacyRequisitionFormInit` | 加载请领页初始化 | `POST /api/app/inventoryPharmacyRequisition/loadPharmacyRequisitionFormInit` |
| IF-PHA-QL-02 | `pagePharmacyRequisitions` | 药房请领单分页列表 | `POST /api/app/inventoryPharmacyRequisition/pagePharmacyRequisitions` |
| IF-PHA-QL-03 | `getPharmacyRequisitionDetail` | 药房请领单详情 | `POST /api/app/inventoryPharmacyRequisition/getPharmacyRequisitionDetail` |
| IF-PHA-QL-04 | `createPharmacyRequisition` | 新建药房请领单 | `POST /api/app/inventoryPharmacyRequisition/createPharmacyRequisition` |
| IF-PHA-QL-05 | `updatePharmacyRequisition` | 编辑药房请领单 | `POST /api/app/inventoryPharmacyRequisition/updatePharmacyRequisition` |
| IF-PHA-QL-06 | `submitPharmacyRequisitionApproval` | 提交审批 | `POST /api/app/inventoryPharmacyRequisition/submitPharmacyRequisitionApproval` |
| IF-PHA-QL-07 | `withdrawPharmacyRequisitionApproval` | 撤回审批 | `POST /api/app/inventoryPharmacyRequisition/withdrawPharmacyRequisitionApproval` |
| IF-PHA-QL-08 | `agreePharmacyRequisition` | 审批同意 | `POST /api/app/inventoryPharmacyRequisition/agreePharmacyRequisition` |
| IF-PHA-QL-09 | `rejectPharmacyRequisition` | 审批驳回 | `POST /api/app/inventoryPharmacyRequisition/rejectPharmacyRequisition` |
| IF-PHA-QL-10 | `removePharmacyRequisition` | 删除请领单 | `POST /api/app/inventoryPharmacyRequisition/removePharmacyRequisition` |
| IF-PHA-QL-11 | `pageProductsForPharmacyRequisition` | 药房请领选品检索 | `POST /api/app/inventoryPharmacyRequisition/pageProductsForPharmacyRequisition` |

**HTTP 路径规则**：**`POST /api/app/inventoryPharmacyRequisition/{methodName}`**（与 **`requisition.ts`** `BASE` 一致）。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数 / 位置门禁 | 不再调用后续 |
| **Pre** | 只读校验、可用量、状态 | 禁止进入 Cmd |
| **Cmd** | 领域写 + 事件 | 回滚见 §5 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-PHA-QL-01 `loadPharmacyRequisitionFormInit`（加载请领页初始化）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/loadPharmacyRequisitionFormInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | N | 兼容字段，服务端忽略 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pharmacyLocationOptions` | `List<OptionDto>` | **目的药房**（**`purpose`** 侧） |
| `warehouseLocationOptions` | `List<OptionDto>` | **来源药库**（**`source`** 侧） |
| `statusOptions` | `List<OptionDto>` | **`SupplyStatus#getCode()`** |

**失败码**：`PHA_QL_101`

**Mapper**：`toLoadPharmacyRequisitionFormInitResponse(...): LoadPharmacyRequisitionFormInitResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 会话机构 | — | **`requireSessionOrganizationUnitId`** |
| 2 | Pre | `LocationQueryService#listAllActivePharmacies` | — | 仅读 |
| 3 | Pre | `LocationQueryService#listAllActiveWarehouses` | — | 仅读 |
| 4 | Pre | （装配） | ② | **`narrowActiveLocationsByManaged`** → **`pharmacyLocationOptions`** |
| 5 | Pre | （装配） | ③ | **`narrowActiveLocationsByManaged`** → **`warehouseLocationOptions`** |
| 6 | Asm | `InventoryPharmacyRequisitionMapper#toLoadPharmacyRequisitionFormInitResponse` | ④⑤ | 仅读 |

---

### 3.3 IF-PHA-QL-02 `pagePharmacyRequisitions`（药房请领单分页列表）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/pagePharmacyRequisitions`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | **目的药房**；**`0`** = 门控内全部可见药房 |
| `sourceLocationId` | Long | N | **来源药库**筛选 |
| `statusEnum` | String | N | **`SupplyStatus#getCode()`** |
| `keyword` | String | N | 单号/关键字 |
| `applyTimeFrom` / `applyTimeTo` | `LocalDateTime` | N | |
| `pageNo` / `pageSize` | Integer | Y | |
| `typeEnum` | String | N | 固定 **`PHARMACY_REQUISITION`** |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | |
| `records` | `List<PharmacyRequisitionListRowDto>` | |

**DTO：`PharmacyRequisitionListRowDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 业务单号 |
| `statusEnum` | String | Y | 状态 code |
| `sourceLocationId` | Long | N | 药库 |
| `purposeLocationId` | Long | N | 药房 |
| `sourceLocationName` | String | N | 展示 |
| `purposeLocationName` | String | N | 展示 |
| `applicantId` | Long | N | |
| `creatorName` | String | N | |
| `applyTime` | `LocalDateTime` | N | |
| `approvalTime` | `LocalDateTime` | N | |
| `totalPrice` | `BigDecimal` | N | 行汇总展示 |
| `linkedStockOutBusNo` | String | N | 只读：已被出库引用时展示 **`original_bus_no` 反向**（QueryService 装配，可选） |

**失败码**：`PHA_QL_102`

**Mapper**：`toPharmacyRequisitionListAggregated(...)`、`toPagePharmacyRequisitionsResponse(...)`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 分页 + 机构 | `pageNo`/`pageSize` | |
| 2 | Pre | `LocationQueryService#listAllActivePharmacies` | — | Gate **`gatedLocationIdsFromActive`** |
| 3 | Pre | `SupplyRequestQueryService#countDistinctBusNo` | **`SupplyRequestDocumentListQuery`**：`typeEnum`=`PHARMACY_REQUISITION` | 仅读 |
| 4 | Pre | `SupplyRequestQueryService#listDistinctBusNoPage` | 同上 | 仅读 |
| 5 | Pre | `SupplyRequestQueryService#listByBusNosWithFilter` | `busNos` from ④ | 仅读 |
| 6 | Asm | `InventoryPharmacyRequisitionMapper#toPagePharmacyRequisitionsResponse` | ⑤ | 聚合 + 展示名富化 |

---

### 3.4 IF-PHA-QL-03 `getPharmacyRequisitionDetail`（药房请领单详情）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/getPharmacyRequisitionDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | |
| `typeEnum` | String | **`PHARMACY_REQUISITION`** |
| `statusEnum` | String | |
| `sourceLocationId` | Long | 药库 |
| `purposeLocationId` | Long | 药房 |
| `sourceTypeEnum` | String | |
| `purposeTypeEnum` | String | |
| `categoryEnum` | String | |
| `applicantId` | Long | |
| `applyTime` | `LocalDateTime` | |
| `lines` | `List<PharmacyRequisitionDetailLineDto>` | |

**DTO：`PharmacyRequisitionDetailLineDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | Y | 供应行 id |
| `itemTable` | String | Y | |
| `itemId` | Long | Y | |
| `itemQuantity` | `BigDecimal` | Y | 拆零持久化量 |
| `unitCode` | String | Y | |
| `lotNumber` | String | N | |
| `traceNo` | String | N | |
| `expirationDate` | `LocalDate` | N | 效期展示 |
| `productName` | String | N | 富化 |
| `specification` | String | N | |
| `manufacturer` | String | N | |
| `productCatalog` | `ProductDefinitionCatalogEditorHintsDto` | N | 与 **IF-PHA-IN-03** 同构 |

**失败码**：`PHA_QL_103`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` 非空 | | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | | 类型断言 **`PHARMACY_REQUISITION`** |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 行 `itemTable`+`itemId` | |
| 4 | Asm | `InventoryPharmacyRequisitionMapper#toGetPharmacyRequisitionDetailResponse` | ②③ | **`unitBands`** 等同构富化 |

---

### 3.5 IF-PHA-QL-04 `createPharmacyRequisition`（新建药房请领单）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/createPharmacyRequisition`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | **药库** |
| `purposeLocationId` | Long | Y | **药房** |
| `categoryEnum` | String | N | 默认 **`STOCK_SUPPLY`** |
| `applicantId` | Long | Y | 医务人员 |
| `applyTime` | `LocalDateTime` | Y | |
| `lines` | `List<PharmacyRequisitionLineDto>` | Y | 非空 |

**DTO：`PharmacyRequisitionLineDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemTable` | String | Y | |
| `itemId` | Long | Y | |
| `itemQuantity` | `BigDecimal` | Y | |
| `unitCode` | String | Y | |
| `lotNumber` | String | N | 批号级选品带入 |
| `traceNo` | String | N | |
| `occurrenceTime` | `LocalDateTime` | N | |
| `reason` | String | N | |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 新单号 |
| `statusEnum` | String | **`PENDING_APPROVAL`** |

**关键校验**：**BR-PHA-QL-002/010**；位置门控；明细可用量 ≤ 源药库库存

**失败码**：`PHA_QL_001`

**Mapper**：`toCreateRequisitionCommand(...): CreateRequisitionCommand`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数 + 明细非空 | | |
| 2 | Pre | 位置 Gate | `sourceLocationId`/`purposeLocationId` | 药库+药房形态 |
| 3 | Pre | `InventoryItemQueryService#findAvailableCatalogRowsForOrder` | `sourceLocationId` + 行键 | **BR-PHA-QL-010** |
| 4 | Cmd | `SupplyRequestDomainService#createRequisition(CreateRequisitionCommand)` | type **12** | **写库点**；发布 **CreatedEvent** |
| 5 | Asm | `toCreatePharmacyRequisitionResponse` | ④ | |

---

### 3.6 IF-PHA-QL-05 `updatePharmacyRequisition`（编辑药房请领单）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/updatePharmacyRequisition`

**Request**：同 IF-PHA-QL-04，**另加** `busNo` String Y

**Response**：同 IF-PHA-QL-04

**失败码**：`PHA_QL_004`、`PHA_QL_105`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `busNo` + 明细 | |
| 2 | Pre | `getSupplyByBusNo` + **`assertPharmacyRequisitionLinesRevisable`** | 仅 **`PENDING_APPROVAL`** |
| 3 | Pre | 可用量断言 | 同 IF-PHA-QL-04 ③ |
| 4 | Cmd | **`createRequisition`**（携带既有 **`busNo`**） | **PHA-QL-SDD-R-002**；**不**发 CreatedEvent |
| 5 | Asm | `toUpdatePharmacyRequisitionResponse` | |

---

### 3.7 IF-PHA-QL-06 `submitPharmacyRequisitionApproval`（提交审批）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/submitPharmacyRequisitionApproval`

**Request**：`busNo` Y；`applyClock` N

**Response**：`success`；`statusEnum`

**失败码**：`PHA_QL_003`

**领域编排序列**：Gate → **`getSupplyByBusNo`** → **`submitApproval`**（Cmd 写库点）→ Asm

---

### 3.8 IF-PHA-QL-07 `withdrawPharmacyRequisitionApproval`（撤回审批）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/withdrawPharmacyRequisitionApproval`

**Request**：`busNo` Y

**Response**：`success`；`statusEnum`

**失败码**：`PHA_QL_003`

**领域编排序列**：Gate → Pre 状态 **`APPROVAL`** → **`withdrawApproval`**（Cmd）→ Asm

---

### 3.9 IF-PHA-QL-08 `agreePharmacyRequisition`（审批同意）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/agreePharmacyRequisition`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | |
| `operatorId` | Long | N | 默认当前操作人 |
| `approvalClock` | `LocalDateTime` | N | |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | |
| `statusEnum` | String | **`AGREE`** |

> **Breaking**：删除旧 **`stockOutBusNo`/`pharmacyStockInBusNo`** 字段（**PHA-QL-SDD-R-001**）。

**失败码**：`PHA_QL_003`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `busNo` | |
| 2 | Pre | `getSupplyByBusNo` + **`supplyRequestValidation`** | 须 **`APPROVAL`** |
| 3 | Cmd | **`agreeRequest`** | **唯一写库点**；**禁止** `createStockOut` |
| 4 | Asm | `toAgreePharmacyRequisitionResponse` | 仅 **`busNo`+`statusEnum`** |

---

### 3.10 IF-PHA-QL-09 `rejectPharmacyRequisition`（审批驳回）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/rejectPharmacyRequisition`

**Request**：`busNo` Y；`rejectReason` N；`operatorId` N

**Response**：`success`

**领域编排序列**：Gate → Pre **`APPROVAL`** → **`rejectRequest`**（Cmd）→ Asm

---

### 3.11 IF-PHA-QL-10 `removePharmacyRequisition`（删除请领单）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/removePharmacyRequisition`

**Request**：`busNo` Y

**Response**：`success`

**失败码**：`PHA_QL_005`（下游出库已引用）

**领域编排序列**：Gate → Pre 可删态 + 无 **`CABINET_STOCK_OUT.original_bus_no`** 引用 → **`removeByBusNo`**（Cmd）→ Asm

---

### 3.12 IF-PHA-QL-11 `pageProductsForPharmacyRequisition`（药房请领选品检索）

**HTTP**：`POST /api/app/inventoryPharmacyRequisition/pageProductsForPharmacyRequisition`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | **来源药库** |
| `productTableName` | String | N | |
| `positiveQuantityOnly` | Boolean | N | **`true`**：仅来源药库账面 **`quantity > 0`**；**`false`/未传：不过滤 |
| `keyword` | String | N | |
| `categoryCode` | String | N | |
| `pageNo` / `pageSize` | Integer | Y | |
| `sortField` / `sortOrder` | String | N | |

**Response**：`total`；`records` **`List<ProductDefinitionItemDto>`**（字段族与 **IF-PHA-IN-11** 一致：`lotNumber`、`expirationDate`、`availableQuantity`、`inventoryItemId`、`unitBands`、`purchaseUnitPrice` 等）

**UI**：批号级选品下拉/列表**必须展示 `lotNumber`（批号）**（与 **AS-INV-WHS-003 §1.3**、前端 `INVENTORY_PRODUCT_PICK_TABLE_COLUMNS` 对齐；**非**订货/进货产品定义展开）

**领域读口径（2026-07-02）**

- 请求可选 **`positiveQuantityOnly`**（非必传）→ 下推 `InventoryItemPageQuery.positiveQuantityOnly`：**`true`** 仅账面量 **> 0**；**`false`/未传** 不过滤数量。
- 可用量：`InventoryItemQueryService#findAvailableCatalogRowsForInventoryItemIds`，键 **`wkf_inventory_item.id`**；未命中时 **`availableQuantity`** 回退**账面量**（与库存明细「有库存」列表对齐）。

**失败码**：`PHA_QL_102`

**领域编排序列**：Gate → **`queryInventoryItemsByPage`**（含 **`positiveQuantityOnly`**）→ **`findAvailableCatalogRowsForInventoryItemIds`** → **`loadAsMap`** → Mapper 装配；错误码 **`PHA_QL_`**。

---

## 4. QueryService 与领域事件

| 写事件 | 触发 IF | 读侧刷新 |
| --- | --- | --- |
| `SupplyRequestCreatedEvent` | IF-PHA-QL-04 | 列表/详情 |
| `SupplyRequestSubmittedEvent` | IF-PHA-QL-06 | 待办、列表 |
| `SupplyRequestAgreedEvent` | IF-PHA-QL-08 | 列表；**不**刷新库存 |
| `SupplyRequestRejectedEvent` | IF-PHA-QL-09 | 列表 |
| `SupplyRequestDeletedEvent` | IF-PHA-QL-10 | 列表隐藏 |

出库侧消费 **`AGREE`** 请领：见 **AS-INV-WHS-003-SDD** **IF-WHS-SO-12/13**（最终一致）。

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 领域写 | 聚合 |
| --- | --- | --- | --- | --- |
| IF-PHA-QL-04 | `createPharmacyRequisition` | 4 | `createRequisition` | `wkf_supply_request` INSERT |
| IF-PHA-QL-05 | `updatePharmacyRequisition` | 4 | `createRequisition`（修订） | 软删 + INSERT |
| IF-PHA-QL-06～09 | 审批族 | Cmd | `submit/withdraw/agree/reject` | `wkf_supply_request` UPDATE |
| IF-PHA-QL-10 | `removePharmacyRequisition` | Cmd | `removeByBusNo` | 逻辑删 |

**事务边界**：各写接口默认 **`@Transactional`** 单事务；**IF-PHA-QL-08** **禁止**跨聚合库存写。

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 同意重复提交 | **`agreeRequest`** 状态机拒绝或幂等返回 | §3.9 序号 3 |

### 6.2 并发控制

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 修订冲突 | 领域修订全量对齐；UI 刷新后重试 | §3.6 |

### 6.3 审计

审批人/时间由 **`agreeRequest`/`rejectRequest`** 写入 **`approver_id`/`approval_time`**。

## 7. 测试要点

### 7.1 功能测试（P0）

| 用例 | 预期 |
| --- | --- |
| 新建请领 + 批号选品 | `PQR` 单号；库存不变 |
| 同意 | **`AGREE`**；无出库/入库单生成 |
| 出库关联 | **WHS-003** 从 **`AGREE`** 请领带入；数量可改 |

### 7.2 规则测试（P0）

**BR-PHA-QL-010** 超可用量拒绝；**BR-PHA-QL-002** 非药库源拒绝。

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.4 | 2026-07-24 | **目的仅药房**：`listAllActivePharmacies` 口径收敛为仅 `PHARMACY`（对齐 **DM-ADM-003 v1.5** / **AS v0.3**）；科室请领另走 `listAllActiveDepartmentPharmacies` |
| v0.3 | 2026-07-15 | **IF-PHA-QL-11**：**`positiveQuantityOnly`** 改为请求可选下推（非必传）；废止硬编码 true |
| v0.2 | 2026-07-02 | **IF-PHA-QL-11**：**`positiveQuantityOnly`** + **`findAvailableCatalogRowsForInventoryItemIds`** 行级可用量；对齐 **4666f46ae** |
| v0.1 | 2026-06-30 | 初稿：11 IF；**PHA-QL-SDD-R-001** 同意不生成执行单；位置 DM 口径；选品对齐 **PHA-001**；**Agree** 响应去 **`stockOutBusNo`** |

---

**关联 AS**：`health/AppService/AS-INV-PHA-002-药房请领单.md`
