# 药库出库单 · 应用服务详细设计（SDD）

> 编码：AS-INV-WHS-003-SDD  
> 版本：v1.15 | 日期：2026-07-24 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药库出库单应用服务（库存 · 药库 · **`CABINET_STOCK_OUT`**，与 **`com.zxplat.domain.share.enums.SupplyType#CABINET_STOCK_OUT#getCode()`** 一致） |
| 边界 | 编排 **SupplyRequest** 出库建单、修订、审批、删除；**审批同意**后应用层编排 **SupplyDelivery**、**源药库 `InventoryItem` 扣减**、**目的位置 `InventoryItem` 增加**、**对侧 `PHARMACY_STOCK_IN` 单据落库（业务态 `AGREE`，`original_bus_no` = 本出库 `bus_no`）**、**`trace_no` 非空** 行之 **`cus_trace_number` + `cus_trace_number_flow`**；**不**补录 **`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`** |
| 编排权威 | `AS-INV-WHS-003-药库出库单.md`（**v0.11**）§2、§4 |
| 方法权威 | `DM-WKF-006-SupplyRequest-供应请求.md` §6；`DM-WKF-005-SupplyDelivery-供应发放.md` §6；`DM-WKF-008-InventoryItem-库存项.md` §6；`DM-ADM-003-Location-位置.md` §6.2；`DM-CUS-010-TraceNumberRecord-追溯码记录.md` §6；QueryService `DM-WKF-006 §6.2 `SupplyRequestQueryService` / `InventoryItemQueryService``、`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` §3 |
| 严格对齐 | IF **IF-WHS-SO-01～13** 与 AS 母表一致 |
| 实现阶段口径 | **目标态全量契约**（非「当前已上线范围」） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 基于 AS、`DM-WKF-006` §3/§6、QueryService §3；**枚举 API/落库均为 `getCode()` 字符串**（B.10.8.1） |
| **`bus_no` 生成** | **`BusinessSequenceService#getSeqByDay`**；序号键前缀 **`SupplyType.busNoSeqKey`**（出库 **`CSO`**）；无法匹配白名单时回退 **`SR`**（**`SupplyType.busNoSeqPrefixForTypeCode`**，**不含 `SQ`**） |
| QueryService口径 | 列表与 **AS-INV-WHS-002** 进货列表策略对偶：以 **`wkf_supply_request`** 经 **`SupplyRequestQueryService`** **`bus_no` 去重**分页为主路径；**IF-WHS-SO-12** 可关联请领同构 **IF-WHS-IN-12**（**`countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`**，一行一单）；选品以 **`InventoryItemQueryService#queryInventoryItemsByPage`**（**DM-WKF-008** §6.2.10）+ **ProductDefinition** 装配为主 |
| 事务口径 | 写接口默认单事务；**IF-WHS-SO-08** 覆盖 **`agreeRequest`（出库）→ 发放与库存 → 对侧壳单 → 追溯** 全链路，失败整单回滚，见 §5 |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **WHS-SO-SDD-R-001** | **对侧 `PHARMACY_STOCK_IN`（`AGREE`）**：须在 **同一事务**、**目的库存已加账之后** 落 **`wkf_supply_request`** 行且 **`status_enum`=`AGREE`**、**`type_enum`=`PHARMACY_STOCK_IN`**、**`original_bus_no`** = **本出库 `bus_no`**。**GAP-WHS-SO-001** 关闭前：领域侧采用 **`SupplyRequestDomainService#createRequisition(CreateRequisitionCommand)`** 之 **扩展语义**（**`CreateRequisitionCommand#bootstrapFinalStatusCode`** = **`AGREE`**，**目标态待生成**，见 §8）**或** 等价 **`persistAgreedPharmacyStockInShell`** 单方法（**FQCN 以领域 SDD 为准**），**禁止**对壳单再调用 **`agreeRequest`** 触发二次入库发放（与 **AS BR-WHS-SO-012**、**PHA AS BR-PHA-IN-012** 一致）。 |
| **WHS-SO-SDD-R-002** | **IF-WHS-SO-08 库存**：在 **`SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)`**（**出库 `bus_no`**）成功后，顺序 **`SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)`**（**`CABINET_STOCK_OUT`**）→ **`InventoryItemDomainService#decreaseQuantity(DecreaseQuantityCommand)`**（**`source_location_id`**）→ **`InventoryItemDomainService#increaseQuantity(IncreaseQuantityCommand)`** / **`#createInventoryItem(CreateInventoryItemCommand)`**（**`purpose_location_id`**，**四元组**命中分支 **DM-WKF-008** **§9.1**），以对齐 **AS**「源扣 + 目的增」。 |
| **WHS-SO-SDD-R-003** | **修订（IF-WHS-SO-05）**：与 **`AS-INV-WHS-001-SDD` `WHS-SDD-R-002`** 同构 — **`CreateStockOutCommand` 携带既有可编辑态 `busNo`** 时 **`createStockOut`** 执行明细全量对齐（软删 + 重插）；领域未支持前 **Cmd 不得投产**。 |
| **WHS-SO-SDD-R-004** | **IF-WHS-SO-11 选品**：读锚仅 **`LOT_NUMBER_COST`**（有批号）或 **`PURCHASE`**（无批号，均每包装）。**`unitBands`**：**`#applyInboundWarehouseBandPrices`**。**禁止**读 **`SALE`**、**`LOT_NUMBER_PRICE`**。 |
| **WHS-SO-SDD-R-005** | **详情 `productCatalog`**：与 **`WHS-IN-SDD-R-011`** 同构 — **`#applyInboundWarehouseDetailLineBandPrices`** + **`#resolvePackageAnchorUnitPriceFromLine`**。 |
| **WHS-SO-SDD-R-006** | **追溯**：**`trace_no` 非空** 行 **`operation_type`** = **`SupplyType#CABINET_STOCK_OUT#getCode()`**（**`CABINET_STOCK_OUT`**）；**`TraceNumberDomainService#recordTraceRecords` / `#recordTraceRecordsFromLineItems`**（**DM-CUS-010**）；列宽 **TRC-WIDE-R-001** 与进货 SDD 一致。 |
| **WHS-SO-SDD-R-007** | **IF-WHS-SO-08 供应行数量与库存一致**：**`decreaseQuantity` / `increaseQuantity` / `createInventoryItem`** 的增量数量取 **`wkf_supply_request.item_quantity`**（已与 **`wkf_inventory_item.quantity`** 同为拆零下持久化），经 **`SupplyPersistedSplitQuantity#requireSupplyLineItemQuantityAsPersistedSplitUnit`**（或等价校验）确认正数与白名单物品表；**`unitCode`** 落 **`ProductDefinitionDisplayDto#getSplitUnitCode`**（**`loadAsMap`**，缺投影或 **`splitUnitCode` 空白** → 业务失败）。**禁止**在同意路径对 **`item_quantity`** 调用 **`MedicationProductQuantityToSplitUnitConverter`**（该换算器仅用于发药等业务单位语义）。草稿 **BR-WHS-SO-010** 可用量比对亦直接以行 **`item_quantity`** 与 **`findAvailableCatalogRowsForOrder`** 聚合结果比较。**`createInventoryItem`**（目的库 **新建**）时 **`productionDate`/`expirationDate`** 优先供应行 **`start_time`/`end_time`**；**供应行未带效期**时须承袭**源库命中行** **`production_date`/`expiration_date`**（**WHS-SO-SDD-R-010**），再装配分类/检索码（**`WHS-IN-SDD-R-012`** 同构；**`increaseQuantity`** 不回写冗余列）。 |
| **WHS-SO-SDD-R-010** | **目的库新建库存效期承袭**：**IF-WHS-SO-08** 在 **`createInventoryItem`** 前，若供应行 **`end_time`/`start_time`** 为空，从 **`InventoryItemQueryService#getInventoryItemById`** 所读**源库扣减行**复制 **`expiration_date`/`production_date`** 写入 **`CreateInventoryItemCommand`**（**禁止**目的药房新账无批号效期）。 |
| **WHS-SO-SDD-R-008** | **选品可用量与四元组口径**：装配可用量时须遵守 **DM-WKF-008 §1.1 / §2.1**：仅 **`wkf_inventory_item.location_id` = `sourceLocationId`** 参与；**`inventory_status_enum`** 须 **ACTIVE**；**展示键**为 **`wkf_inventory_item.id`（`inventoryItemId`）**——经 **`findAvailableCatalogRowsForOrder`** 或 **`findAvailableCatalogRowsForInventoryItemIds`** 回填，**禁止** **`item_id + normalizeLotNumber` 聚合**；与 **AS-INV-PHA-002** **PHA-QL-SDD-R-004** 对偶 |
| **WHS-SO-SDD-R-009** | **供应请求时间与操作者**：与 **`AS-INV-WHS-002-SDD` `WHS-IN-SDD-R-013`** 对齐 — **`occurrence_time`** 前端随明细；**`apply_time`** 仅 **`submitApproval`** 写当前时间，草稿 **`createStockOut`** 各行 **`apply_time`=`null`**；**`approval_time`** 同意/驳回由领域写当前时间；**`applicant_id`** 会话医务人员；**`approver_id`** 同意/驳回取 **`operatorId`**（请求体 **`approvalTime`/`approverId`** 若仍存在则 **忽略**）；**`practitioner_id`**：**`CreateStockOutRequest`/`UpdateStockOutRequest#practitionerId`** 必填（前端选择经手人）；对侧 **`PHARMACY_STOCK_IN`** 壳单各行 **`apply_time`=`null`**（**`createRequisition`** 装配）。 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/warehouse/stockout/
  service/
    InventoryWarehouseStockOutAppService.java
  request/
    LoadStockOutFormInitRequest.java
    PageStockOutsRequest.java
    GetStockOutDetailRequest.java
    CreateStockOutRequest.java
    UpdateStockOutRequest.java
    SubmitStockOutApprovalRequest.java
    WithdrawStockOutApprovalRequest.java
    AgreeStockOutRequest.java
    RejectStockOutRequest.java
    RemoveStockOutRequest.java
    PageProductsForStockOutRequest.java
    PageLinkableRequisitionsRequest.java
  response/
    LoadStockOutFormInitResponse.java
    PageStockOutsResponse.java
    GetStockOutDetailResponse.java
    CreateStockOutResponse.java
    UpdateStockOutResponse.java
    SubmitStockOutApprovalResponse.java
    WithdrawStockOutApprovalResponse.java
    AgreeStockOutResponse.java
    RejectStockOutResponse.java
    RemoveStockOutResponse.java
    PageProductsForStockOutResponse.java
    PageLinkableRequisitionsResponse.java
  dto/
    StockOutListRowDto.java
    StockOutLineDto.java
    StockOutDetailLineDto.java
    ProductDefinitionItemDto.java
    WarehouseDocumentDetailRowDto.java
  mapper/
    InventoryWarehouseStockOutMapper.java
    InventoryWarehouseStockOutMapperImpl.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 / 审批 **`requireOperatorId`**；制单人 **`findByUserId`** 前 Gate（**B.6.5**） |
| `CurrentManagedOrganizationIdsResolver` | 会话科室 **`requireSessionOrganizationUnitId`**（**B.6.5**）；**`PractitionerRoleScopeLoader`** org 入参；**禁止** Request 传 `organizationId` |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**（**IF-WHS-SO-01** 下拉）、**`gatedLocationIdsFromActive`**（列表/写/选品 **药库** id 门控；**B.6.5**） |
| `BusinessSequenceService` | **`bus_no`** 日流水（**`CSO`** / 回退 **`SR`**） |
| `SupplyRequestDomainService` | **`createStockOut`**、**`submitApproval`**、**`withdrawApproval`**、**`agreeRequest`**、**`rejectRequest`**、**`removeByBusNo`**、**`createRequisition`**（**对侧壳单**，见 **WHS-SO-SDD-R-001**） |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`**、**`supplyRequestValidation`**、**`verifyAbleEdit`**；列表 **`countDistinctBusNo`**、**`listDistinctBusNoPage`**、**`listByBusNosWithFilter`**（**`SupplyRequestDocumentListQuery`**） |
| `SupplyDeliveryDomainService` | **`createSupplyDelivery`**（**IF-WHS-SO-08**） |
| `SupplyDeliveryQueryService` | **`findRequestIdsHavingCompletedDelivery`**（**A.9.1** 批量防重，**语义等价** **`supplyDeliveryValidation`** 之「已完成发放」判重，见 §3.11 序号 7 说明） |
| `InventoryItemDomainService` | **`decreaseQuantity`**、**`increaseQuantity`**、**`createInventoryItem`**（**IF-WHS-SO-08**） |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`**、**`findFirstIdByInventoryMatchKey`**（选品 / 草稿可用量 / 同意链） |
| `TraceNumberDomainService` | **`recordTraceRecords`** / **`recordTraceRecordsFromLineItems`**（**IF-WHS-SO-08**） |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActivePharmacyPartyLocations`**（**IF-WHS-SO-01** 下拉主数据全集：源药库 / 目的**药房族**；下拉收窄 **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`**；Gate 药库 id 集 **`#gatedLocationIdsFromActive(listAllActiveWarehouses)`**；目的 Gate **`#gatedLocationIdsFromActive(listAllActivePharmacyPartyLocations)`**） |
| `PractitionerQueryService` | **IF-WHS-SO-01** 制单人 **`findByUserId`** |
| `PractitionerRoleScopeLoader` | **IF-WHS-SO-01** 经手人/库管 **`listActivePractitionersForRoleInOrganization`**（org 由 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 传入；Loader 层不直读会话） |
| `WhaleRoleLookup` | **IF-WHS-SO-01** 平台角色解析 |
| `SupplyRequestQueryService` | **`countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`**（**IF-WHS-SO-02**、**IF-WHS-SO-12**） |
| `ProductDefinitionBatchLoader` | **`pageProductForTransfer`**（**Q-PD-005**）、**`loadAsMap`**（**IF-WHS-SO-08**：取 **`splitUnitCode`**，**WHS-SO-SDD-R-007**） |
| `ProductDefinitionSelectionTerminologysupport | 选品/列表页术语与单位展示预热（`application.shared.product`） |
| `ChargeItemDefinitionQueryService` | **`findLotNumberCostByInstanceIdAndLot`**（**IF-WHS-SO-11** **`LOT_NUMBER_COST`**）；**`findPurchaseItemByInstanceId`**（**IF-WHS-SO-03** **`PURCHASE`**） |
| `ProductDefinitionPurchasePriceResolver` | **`resolveLotNumberCostPrice`**（**IF-WHS-SO-11**）；**`resolvePurchasePrice`**（**IF-WHS-SO-03** **`PURCHASE`**） |
| `ProductDefinitionCatalogEditorHintsAssembler` | **`fromDisplayDto`**（**IF-WHS-SO-03**） |
| `ProductDefinitionCatalogUnitBandsAssembler` | 四槽装配 + **`resolvePurchaseUnitRatioForInboundPricing`** / **`applyInboundBandsPriceUsingPurchasePackagingRatio`**（**WHS-SO-SDD-R-004/R-005**） |
| `SupplyPersistedSplitQuantity` | **`requireSupplyLineItemQuantityAsPersistedSplitUnit`** 等（**static**） | **IF-WHS-SO-08**（**WHS-SO-SDD-R-007**）；**IF-WHS-SO-04/05** 与可用量比对 |
| `SupplyRequestDomainService` | **`flushPendingChanges`**（**`agreeRequest` 后读回**，与进货 SDD **B.2** 一致） |
| `InventoryWarehouseStockOutMapper` | DTO 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActivePharmacyPartyLocations`** | IF-WHS-SO-01（**`narrowActiveLocationsByManaged`**）；IF-WHS-SO-02、04、05、11、12 Gate（药库：**`gatedLocationIdsFromActive`**；目的：**`gatedLocationIdsFromActive(listAllActivePharmacyPartyLocations)`**） |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** | IF-WHS-SO-01～02、04～05、11～12 |
| `PractitionerQueryService` | **`findByUserId`** | IF-WHS-SO-01 |
| `PractitionerRoleScopeLoader` | **`listActivePractitionersForRoleInOrganization`** | IF-WHS-SO-01 |
| `WhaleRoleLookup` | `findRoleIdByCode` | IF-WHS-SO-01 |
| `SupplyRequestDomainService` | **`createStockOut`** | IF-WHS-SO-04、IF-WHS-SO-05 |
| `SupplyRequestDomainService` | **`submitApproval`** | IF-WHS-SO-06 |
| `SupplyRequestDomainService` | **`withdrawApproval`** | IF-WHS-SO-07 |
| `SupplyRequestDomainService` | **`agreeRequest`** | IF-WHS-SO-08 |
| `SupplyRequestDomainService` | **`rejectRequest`** | IF-WHS-SO-09 |
| `SupplyRequestDomainService` | **`removeByBusNo`** | IF-WHS-SO-10 |
| `SupplyRequestDomainService` | **`createRequisition`** | IF-WHS-SO-08（对侧 **`PHARMACY_STOCK_IN`** 壳单，**WHS-SO-SDD-R-001**） |
| `SupplyRequestQueryService` | **`getSupplyByBusNo`**、**`supplyRequestValidation`**、**`verifyAbleEdit`** | 多 IF Pre |
| `SupplyRequestQueryService` | **`countDistinctBusNo`**、**`listDistinctBusNoPage`**、**`listByBusNosWithFilter`** | IF-WHS-SO-02、**IF-WHS-SO-12** |
| `ProductDefinitionBatchLoader` | **`pageProductForTransfer`**、**`loadAsMap`** | IF-WHS-SO-11、IF-WHS-SO-03；**IF-WHS-SO-08**（**WHS-SO-SDD-R-007**） |
| `InventoryItemQueryService` | **`queryInventoryItemsByPage`**、**`calculateTotalAvailableInventory`**、**`findAvailableCatalogRowsForOrder`**、**`findFirstIdByInventoryMatchKey`** | IF-WHS-SO-11；IF-WHS-SO-04/05（**BR-WHS-SO-010**）；**IF-WHS-SO-08** |
| `SupplyDeliveryDomainService` | **`createSupplyDelivery`** | IF-WHS-SO-08 |
| `SupplyDeliveryQueryService` | **`findRequestIdsHavingCompletedDelivery`** | IF-WHS-SO-08 Pre |
| `InventoryItemDomainService` | **`decreaseQuantity`**、**`increaseQuantity`**、**`createInventoryItem`** | IF-WHS-SO-08 |
| `TraceNumberDomainService` | **`recordTraceRecords`**、**`recordTraceRecordsFromLineItems`** | IF-WHS-SO-08 |
| `ChargeItemDefinitionQueryService` | **`findLotNumberCostByInstanceIdAndLot`**（**IF-WHS-SO-11** **`LOT_NUMBER_COST`**）；**`findPurchaseItemByInstanceId`**（**IF-WHS-SO-03** **`PURCHASE`**） | IF-WHS-SO-11 Asm；IF-WHS-SO-03 |
| `ProductDefinitionPurchasePriceResolver` | **`resolveLotNumberCostPrice`**（**IF-WHS-SO-11**）；**`resolvePurchasePrice`**（**IF-WHS-SO-03**） | IF-WHS-SO-11 Asm；IF-WHS-SO-03 |
| `ProductDefinitionCatalogEditorHintsAssembler` | **`fromDisplayDto(ProductDefinitionDisplayDto)`** | IF-WHS-SO-03 |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`buildInboundSelectionBands`**、**`resolvePurchaseUnitRatioForInboundPricing`**、**`applyInboundBandsPriceUsingPurchasePackagingRatio`**、**`applyUnitPricingToBandsIfPresent`** | IF-WHS-SO-11、IF-WHS-SO-03 |
| `SupplyPersistedSplitQuantity` | **`requireSupplyLineItemQuantityAsPersistedSplitUnit`**、**`requireMedicationOrDeviceTableAndPositiveSplitQuantity`** | IF-WHS-SO-08（**WHS-SO-SDD-R-007**）；IF-WHS-SO-04/05 |
| `BusinessSequenceService` | **`getSeqByDay`** | IF-WHS-SO-04 |
| `SupplyRequestDomainService` | **`flushPendingChanges`** | IF-WHS-SO-08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 `type_enum`（出库） | **`CreateStockOutRequest`** 根装配固定 **`CABINET_STOCK_OUT`**（**不向** Request 暴露可变 `typeEnum` 时由 Mapper 写入 **`CreateStockOutCommand`**） | `DM-WKF-006` §4.1.1 **`CABINET_STOCK_OUT`** | IF-WHS-SO-04/05 |
| 单据类型（请领列表） | **`SupplyRequestDocumentListQuery.typeEnums`** = **`PHARMACY_REQUISITION` + `DEPT_REQUISITION`**（或 `typeEnum` 收窄） | `DM-WKF-006` §4.6.3 | IF-WHS-SO-12 |
| 单据类型（对侧壳单） | **`CreateRequisitionCommand.CreateRequisitionItemCommand#typeEnum`** = **`PHARMACY_STOCK_IN`** | `DM-WKF-006` §4.1.2；§6.1.9 入库执行单 | IF-WHS-SO-08 |
| 单据状态 `status_enum` | **`StockOutListRowDto.statusEnum`**；**`LoadStockOutFormInitResponse.statusOptions`** 为 **`List<OptionDto>`** | `DM-WKF-006` §4.2；§3.1 `status_enum` | IF-WHS-SO-02、01 |
| 领域枚举 **`SupplyType`** | **`com.zxplat.domain.share.enums.SupplyType`** | `DM-WKF-006` §4.1 | 全 IF |
| 领域枚举 **`SupplyStatus`** | **`com.zxplat.domain.share.enums.SupplyStatus`** | `DM-WKF-006` §4.2 | 审批链 |
| 领域枚举 **`SupplyRequestType`**（落库） | **`CreateStockOutCommand`** 行装配之 **`sourceTypeEnum`/`purposeTypeEnum`**（**`SupplyRequestType#getCode()`**） | `DM-WKF-006` §4.4 | **HTTP 请求体不传**：**`InventoryWarehouseStockOutMapper`** 据 **`sourceLocationId`/`purposeLocationId`** 经 **`LocationQueryService#requireSupplyRequestTypeForLocationId`** 推导 |
| 领域枚举 **`SupplyCategory`** | **`CreateStockOutRequest.categoryEnum`** | `DM-WKF-006` §4.3 | 默认 **`STOCK_SUPPLY`** |
| 追溯 **`operation_type`** | **`TraceNumberRecordAppendCommand.operationType`**（等价字段名以实现为准）填 **`CABINET_STOCK_OUT`** | **AS BR-WHS-SO-011**；`DM-CUS-010` §4.4（**GAP-WHS-SO-002** 关闭后对齐白名单） | IF-WHS-SO-08 |
| 价类 **`PURCHASE`** | **`com.zxplat.domain.share.enums.PriceType#PURCHASE`** | **ProductDefinition** QueryService §3 **Q-PD-004** 与定价表关联说明 | IF-WHS-SO-03（详情 **`productCatalog.purchasePrice`**，**WHS-SO-SDD-R-005**） |
| 价类 **`LOT_NUMBER_COST`** | **`com.zxplat.domain.share.enums.PriceType#LOT_NUMBER_COST`** | 收费项定价行 **`lot_no`** 与 **`price_value`**；与库存行 **`lot_number`** 对齐 | IF-WHS-SO-11（**`purchaseUnitPrice`**，**WHS-SO-SDD-R-004**） |
| 入库项目类型（初始化） | **`LoadStockOutFormInitResponse.inboundItemTableTypeOptions`** **`OptionDto.value`** | — | **仅** **`med_medication_definition`** / **`adm_device_definition`** 两档，与 **IF-WHS-IN-01** 同源 |
| 对侧壳单终态引导 | **`CreateRequisitionCommand.bootstrapFinalStatusCode`**（**`String`**，**目标态待生成**）= **`AGREE`** | **AS BR-WHS-SO-012**；**GAP-WHS-SO-001** | IF-WHS-SO-08；**§8** 留痕 |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向 | §2.2 各方法至少在 §3 之一出现 |
| 反向 | §3 各 **调用** 均能在 §2.2 找到同名条目 |
| 互斥 | IF-WHS-SO-04「新建」与 IF-WHS-SO-05「修订」**不得**无条件串行；IF-WHS-SO-04 带 **`originalBusNo`** 与不带之校验互斥见 §3.5 |

## 3. 按接口 ID 命名展开

先读完各 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-WHS-SO-01 | `loadStockOutFormInit` | 进入出库页面初始化 | `POST /api/app/inventoryWarehouseStockOut/loadStockOutFormInit` |
| IF-WHS-SO-02 | `pageStockOuts` | 出库单分页列表 | `POST /api/app/inventoryWarehouseStockOut/pageStockOuts` |
| IF-WHS-SO-03 | `getStockOutDetail` | 出库单详情 | `POST /api/app/inventoryWarehouseStockOut/getStockOutDetail` |
| IF-WHS-SO-04 | `createStockOut` | 新建出库单 | `POST /api/app/inventoryWarehouseStockOut/createStockOut` |
| IF-WHS-SO-05 | `updateStockOut` | 编辑出库单 | `POST /api/app/inventoryWarehouseStockOut/updateStockOut` |
| IF-WHS-SO-06 | `submitStockOutApproval` | 提交审批 | `POST /api/app/inventoryWarehouseStockOut/submitStockOutApproval` |
| IF-WHS-SO-07 | `withdrawStockOutApproval` | 撤回审批 | `POST /api/app/inventoryWarehouseStockOut/withdrawStockOutApproval` |
| IF-WHS-SO-08 | `agreeStockOut` | 审批同意 | `POST /api/app/inventoryWarehouseStockOut/agreeStockOut` |
| IF-WHS-SO-09 | `rejectStockOut` | 审批驳回 | `POST /api/app/inventoryWarehouseStockOut/rejectStockOut` |
| IF-WHS-SO-10 | `removeStockOut` | 删除单据 | `POST /api/app/inventoryWarehouseStockOut/removeStockOut` |
| IF-WHS-SO-11 | `pageProductsForStockOut` | 出库选品检索 | `POST /api/app/inventoryWarehouseStockOut/pageProductsForStockOut` |
| IF-WHS-SO-12 | `pageLinkableRequisitions` | 加载可关联请领列表 | `POST /api/app/inventoryWarehouseStockOut/pageLinkableRequisitions` |
| IF-WHS-SO-13 | `getLinkablePharmacyRequisitionDetail` | 拉取请领预填明细 | `POST /api/app/inventoryWarehouseStockOut/getLinkablePharmacyRequisitionDetail` |

**HTTP 路径生成规则（硬门禁）**：**`POST /api/app/inventoryWarehouseStockOut/{methodName}`**，**`POST` 全大写 + 空格**；**禁止** `GET`/`PUT`/`PATCH`/`DELETE`。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR（`*QueryService` / `*QueryService`） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService#…(…Command)` | 事务回滚见 §5 |
| **Asm** | `Mapper` 纯 DTO 装配 | — |

**IF-WHS-SO-08 补充**：**`agreeRequest` 后**之 **`createSupplyDelivery` → 库存扣加 → `createRequisition`（壳单）→ 追溯** 为 **Cmd 阶段连续写库**，默认同一事务，见 §5。

---

### 3.2 IF-WHS-SO-01 `loadStockOutFormInit`（进入出库页面初始化）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/loadStockOutFormInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | N | 若出现由服务端忽略；以 **`CurrentManagedOrganizationIdsResolver`** 会话为准（与进货 SDD 一致） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `warehouseLocationOptions` | `List<OptionDto>` | **源药库** |
| `pharmacyLocationOptions` | `List<OptionDto>` | **目的药房/科室药房**（`listAllActivePharmacyPartyLocations` + 管辖收窄） |
| `inboundItemTableTypeOptions` | `List<OptionDto>` | 入库项目类型两档 |
| `statusOptions` | `List<OptionDto>` | **SupplyStatus** |
| `pharmacistOptions` | `List<OptionDto>` | 可选，与 **IF-PHA-IN-01** 同源 |
| `warehouseKeeperOptions` | `List<OptionDto>` | 可选 |

**失败码**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_SO_101` | 400/500 | 主数据加载失败（**EX-WHS-SO-101**） |

**Mapper 方法签名**

- `toLoadStockOutFormInitResponse(...): LoadStockOutFormInitResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 会话用户存在 | 否则结束 |
| 2 | Pre | `LocationQueryService#listAllActiveWarehouses` | — | 源药库候选 |
| 3 | Pre | `LocationQueryService#listAllActivePharmacyPartyLocations` | — | 目的药房族候选（药房+科室药房） |
| 4 | Pre | （装配 · 下拉） | ② | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → **`warehouseLocationOptions`**；**管辖 empty 不收窄** |
| 5 | Pre | （装配 · 下拉） | ③、同上 | **`narrowActiveLocationsByManaged`** → **`pharmacyLocationOptions`** |
| 6 | Asm | `InventoryWarehouseStockOutMapper#toLoadStockOutFormInitResponse` | ②③经④⑤收窄 + 状态/类型/人员 | 仅读路径 |

---

### 3.3 IF-WHS-SO-02 `pageStockOuts`（出库单分页列表）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/pageStockOuts`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | **源药库**；`0` = 机构内全部可见药库（占位策略与进货 **`purposeLocationId`** 对偶）。**实现说明**：当 `0` 且当前用户无可选药库时，列表查询使用「不可能命中」的库位占位（如 `-1L`）以得到空页并避免无约束列表扫描。 |
| `purposeLocationId` | Long | N | 目的位置筛选 |
| `statusEnum` | String | N | **`SupplyStatus#getCode()`** |
| `keyword` | String | N | 关键字 |
| `applyTimeFrom` / `applyTimeTo` | `LocalDateTime` | N | 申请时间窗 |
| `pageNo` | Integer | Y | ≥1 |
| `pageSize` | Integer | Y | ≤100 |
| `typeEnum` | String | N | 固定 **`CABINET_STOCK_OUT`** |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | **`DISTINCT bus_no`** 计数 |
| `records` | `List<StockOutListRowDto>` | 每 **`bus_no` 一行**；**须含目的位置展示** |

**DTO：`StockOutListRowDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |
| `typeEnum` | String | Y | **`CABINET_STOCK_OUT`** |
| `statusEnum` | String | Y | 状态 |
| `sourceLocationId` | Long | Y | 源药库 |
| `purposeLocationId` | Long | Y | 目的位置 |
| `purposeLocationName` | String | N | **到何处** |
| `applyTime` | `LocalDateTime` | N | **申请时间**（提交审批后；同号多行取 **`apply_time` 最大值**） |
| `approvalTime` | `LocalDateTime` | N | 审批时间 |
| `occurrenceTime` | `LocalDateTime` | N | **制单时间**（同号多行取 **`occurrence_time` 最大值**） |
| `applicantId` | Long | N | 申请人（制单人）主键 |
| `creatorName` | String | N | 制单人展示名（由 **`applicantId`** 解析医务人员姓名） |
| `approverId` | Long | N | 审批人主键 |
| `approverName` | String | N | 审批人展示名 |
| `totalAmount` | `BigDecimal` | N | 该单各明细 **`total_price` 之和**（与进货列表同构） |
| `originalBusNo` | String | N | 关联请领等 |

**失败码**：`WHS_SO_102`（**EX-WHS-SO-102**）

**Mapper**：`toPageStockOutsResponse(...): PageStockOutsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 分页、**`typeEnum`** |  |
| 2 | Gate | **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**（**`LocationQueryService#listAllActiveWarehouses`**） | **`sourceLocationId`** | 非 `0` 须 ∈ 门控集合；`0` 时 ORM 条件为「可见药库 id 集合」= 本 Gate 结果集（**管辖 empty 则全库启用态药库 id**；见 Request 说明） |
| 3 | Pre | `SupplyRequestQueryService#countDistinctBusNo` | **`SupplyRequestDocumentListQuery`**：`typeEnum`=`CABINET_STOCK_OUT`，位置/状态/时间/关键字 | 仅读 |
| 4 | Pre | `SupplyRequestQueryService#listDistinctBusNoPage` | 同上 | 仅读 |
| 5 | Pre | `SupplyRequestQueryService#listByBusNosWithFilter` | **`bus_no` IN** | 装载行 |
| 6 | Asm | `InventoryWarehouseStockOutMapper#toStockOutListAggregated` + `#toPageStockOutsAggregatedResponse` | ⑤ | 聚合行（含 **`totalAmount`**） |
| 7 | Asm | **`InventoryWarehouseStockOutAppService`** 富化 | ⑥ | 目的位置名、状态文案、**`creatorName`/`approverName`** |

---

### 3.4 IF-WHS-SO-03 `getStockOutDetail`（出库单详情）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/getStockOutDetail`

**Request**：`busNo` String Y

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String |  |
| `typeEnum` | String | **`CABINET_STOCK_OUT`** |
| `statusEnum` | String |  |
| `sourceLocationId` / `purposeLocationId` | Long |  |
| `sourceTypeEnum` / `purposeTypeEnum` | String | **`SupplyRequestType#getCode()`** |
| `categoryEnum` | String | **`SupplyCategory#getCode()`** |
| `originalBusNo` | String | N |
| `lines` | `List<StockOutDetailLineDto>` | 批号级明细 |

**DTO：`StockOutDetailLineDto`**（与 **IF-PHA-IN-03** 列族对偶）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | N | 行 id |
| `itemTable` | String | Y |  |
| `itemId` | Long | Y |  |
| `productName` | String | N | QueryService **`product_name`**（**`enrichStockOutDetailProductDisplay`**） |
| `specification` | String | N | QueryService **`total_volume`** |
| `manufacturer` | String | N | 厂家产地合一（QueryService **`manufacturer`**；**禁止** Mapper 固定 null 不下发） |
| `itemQuantity` | `BigDecimal` | Y | 数量 |
| `unitCode` | String | Y |  |
| `unitCodeDictText` | String | N |  |
| `lotNumber` | String | N | 批号 |
| `startTime` / `endTime` | `LocalDateTime` | N | **效期**展示 |
| `traceNo` | String | N |  |
| `remake` | String | N |  |
| `price` / `totalPrice` | `BigDecimal` | N |  |
| `occurrenceTime` | `LocalDateTime` | N |  |
| `productCatalog` | `ProductDefinitionCatalogEditorHintsDto` | N | **多单位目录提示**（**WHS-SO-SDD-R-005**） |

**嵌套 `ProductDefinitionCatalogEditorHintsDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defaultPurchaseUnitCode` | String | N |  |
| `purchasePrice` | `BigDecimal` | N | **`PURCHASE`** |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | N | 四槽含 **`PACKAGE`** |

**`ProductDefinitionCatalogUnitBandDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bandKind` | String | Y | **`PACKAGE`** / 采购 / 拆零 / 库存 等 |
| `unitCode` | String | Y |  |
| `conversionRatio` | `BigDecimal` | N | 相对主单位换算 |
| `unitPrice` | `BigDecimal` | N | 回填价（**`applyInboundBandsPriceUsingPurchasePackagingRatio`** / **`applyUnitPricingToBandsIfPresent`**） |

**失败码**：`WHS_SO_103`（**EX-WHS-SO-103**）

**Mapper**：`toGetStockOutDetailResponse(List<SupplyRequestModel>, Map<...>): GetStockOutDetailResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` 非空 |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | 须存在且 **`type_enum`** 为 **`CABINET_STOCK_OUT`** |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 行键列表 | 可选 |
| 4 | Asm | `ProductDefinitionCatalogEditorHintsAssembler#fromDisplayDto` | ③ 每行QueryService | 每行 **`productCatalog`** |
| 5 | Asm | `ProductDefinitionPurchasePriceResolver#resolvePurchasePrice` |  | **`purchasePrice`** |
| 6 | Asm | **`ProductDefinitionCatalogUnitBandsAssembler`**（**`resolvePurchaseUnitRatioForInboundPricing`** + **`applyInboundBandsPriceUsingPurchasePackagingRatio`** 等） |  | 槽单价 |
| 7 | Asm | `InventoryWarehouseStockOutMapper#toGetStockOutDetailResponse` | ②～⑥ | 仅读 |

---

### 3.5 IF-WHS-SO-04 `createStockOut`（新建出库单）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/createStockOut`

**Request（根）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` / `purposeLocationId` | Long | Y | 源药库 / 目的位置 |
| `categoryEnum` | String | N | 默认 **`STOCK_SUPPLY`**；可缺省或 blank 时由 Mapper 默认 |
| `originalBusNo` | String | N | **BR-WHS-SO-003** |
| `busNo` | String | N | 空白则 **Cmd** 生成 |
| `practitionerId` | Long | Y | 经手人（**WHS-SO-SDD-R-009**） |
| `lines` | `List<StockOutLineDto>` | Y | 非空 |

**DTO：`StockOutLineDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemTable` | String | Y |  |
| `itemId` | Long | Y |  |
| `itemQuantity` | `BigDecimal` | Y | ≤ 可用量 **BR-WHS-SO-010** |
| `unitCode` | String | Y |  |
| `lotNumber` | String | N | 批号 |
| `traceNo` | String | N |  |
| `price` / `totalPrice` | `BigDecimal` | N |  |
| `remake` | String | N |  |
| `occurrenceTime` | `LocalDateTime` | Y | 制单时刻（**WHS-SO-SDD-R-009**） |

**Response**：`busNo` String Y

**失败码**：`WHS_SO_001`（**EX-WHS-SO-001**）、`WHS_SO_002`（**EX-WHS-SO-002**）

**Mapper**：`toCreateStockOutCommand(CreateStockOutRequest, Long operatorId, Long applicantId, String busNo): CreateStockOutCommand`；**修订**委托 **`toCreateStockOutCommand(UpdateStockOutRequest, Long operatorId, Long applicantId)`**

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数与行非空 |  |  |
| 2 | Gate | 位置门控 | 源 **`sourceLocationId`** ∈ **`gatedLocationIdsFromActive(listAllActiveWarehouses)`**；目的 **`purposeLocationId`** ∈ **`gatedLocationIdsFromActive(listAllActivePharmacyPartyLocations)`**（与 **IF-WHS-SO-01** 下拉同源；**管辖 empty 不收窄**）；并满足 **BR-WHS-SO-002** |
| 3 | Pre | **若** `originalBusNo` 非空 **`SupplyRequestQueryService#getSupplyByBusNo`** | 请领单号 | **若**不满足 **BR-WHS-SO-003** 则失败；**否则跳过本步** |
| 4 | Pre | **可用量校验（BR-WHS-SO-010）**：按物品表调用 **`InventoryItemQueryService#findAvailableCatalogRowsForOrder`**（**`locationIds`=`sourceLocationId`**，**`itemIds` 批量**）在内存聚合后与行上拆零量比较；**语义等价**于对每物品 **`#calculateTotalAvailableInventory`**（满足 **A.9.1**） | 草稿行 **`sourceLocationId`** + 行键与数量 | **BR-WHS-SO-010** |
| 5 | Cmd | `SupplyRequestDomainService#createStockOut(CreateStockOutCommand)` | Mapper：**各行 `applyTime`=`null`**；**`practitionerId`** 来自 **`CreateStockOutRequest`**（**WHS-SO-SDD-R-009**） | **写库点**；**`type_enum=CABINET_STOCK_OUT`** |
| 6 | Asm | `InventoryWarehouseStockOutMapper#toCreateStockOutResponse` | ⑤ `busNo` |  |

---

### 3.6 IF-WHS-SO-05 `updateStockOut`（编辑出库单）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/updateStockOut`

**Request**：同 IF-WHS-SO-04 + **`busNo`** Y

**失败码**：`WHS_SO_004`（**EX-WHS-SO-004**）、`WHS_SO_105`（**EX-WHS-SO-105**）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | 类型 **`CABINET_STOCK_OUT`** |
| 3 | Pre | `SupplyRequestQueryService#verifyAbleEdit` |  | **BR-WHS-SO-005** |
| 4 | Pre | 可用量 | 同 IF-WHS-SO-04 | **BR-WHS-SO-010** |
| 5 | Cmd | `SupplyRequestDomainService#createStockOut(CreateStockOutCommand)` | 修订语义 **WHS-SO-SDD-R-003** | **写库点** |
| 6 | Asm | `toUpdateStockOutResponse` |  |  |

---

### 3.7 IF-WHS-SO-06 `submitStockOutApproval`（提交审批）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/submitStockOutApproval`

**Request**：`busNo` String Y

**Response**：`boolean` 或空体（与工程统一 **`SubmitStockOutApprovalResponse`** 为准）

**失败码**：`WHS_SO_003`

**Mapper**：`toSubmitApprovalCommand(SubmitStockOutApprovalRequest request, Long operatorId, Long applicantId): SubmitApprovalCommand`（**`applicantId`** 会话医务人员，**WHS-SO-SDD-R-009**）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` 非空 |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | **`type_enum`**=`**CABINET_STOCK_OUT**`；状态 **`PENDING_APPROVAL`** |
| 3 | Cmd | `SupplyRequestDomainService#submitApproval(SubmitApprovalCommand)` | **`apply_time`** 领域写当前时间；**`applicant_id`** 来自 **`applicantId`** 参数 | **写库点** |
| 4 | Asm | `InventoryWarehouseStockOutMapper#toSubmitStockOutApprovalResponse` |  |  |

---

### 3.8 IF-WHS-SO-07 `withdrawStockOutApproval`（撤回审批）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/withdrawStockOutApproval`

**Request**：`busNo` String Y

**失败码**：`WHS_SO_003`

**Mapper**：`toWithdrawStockOutApprovalResponse(...)` / Command 映射

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | 状态 **`APPROVAL`** |
| 3 | Cmd | `SupplyRequestDomainService#withdrawApproval(WithdrawApprovalCommand)` |  | **写库点** |
| 4 | Asm | Mapper 装配 Response |  |  |

---

### 3.9 IF-WHS-SO-09 `rejectStockOut`（审批驳回）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/rejectStockOut`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y |  |
| `changeReasonCode` | String | N | **`RejectRequestCommand`** |
| `changeReason` | String | N | 展示用 |

**失败码**：`WHS_SO_003`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | **`APPROVAL`** |
| 3 | Cmd | `SupplyRequestDomainService#rejectRequest(RejectRequestCommand)` |  | **写库点** |
| 4 | Asm | Mapper |  |  |

---

### 3.10 IF-WHS-SO-10 `removeStockOut`（删除单据）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/removeStockOut`

**Request**：`busNo` String Y

**失败码**：`WHS_SO_005`（**EX-WHS-SO-005**）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | 可删态 **DM-WKF-006** |
| 3 | Cmd | `SupplyRequestDomainService#removeByBusNo(RemoveByBusNoCommand)` |  | **写库点** |
| 4 | Asm | Mapper |  |  |

---

### 3.11 IF-WHS-SO-08 `agreeStockOut`（审批同意）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/agreeStockOut`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 出库单 |
| ~~`approvalTime`~~ | `LocalDateTime` | N | **已废弃**：领域 **`agreeRequest`** 内写 **`approval_time`**（**WHS-SO-SDD-R-009**），请求体若仍带则 **忽略** |
| ~~`approverId`~~ | Long | N | **已废弃**：Mapper 固定取 **`operatorId`** 写入 **`AgreeRequestItemCommand.approverId`** |

**Response**：`busNo`、`statusEnum`、`generatedPharmacyStockInBusNo`（**对侧单号**，N）

**失败码**：`WHS_SO_003`、`WHS_SO_104`（**EX-WHS-SO-104**）

**Mapper**：`toAgreeRequestCommand(AgreeStockOutRequest request, Long operatorId)`、`toCreateSupplyDeliveryCommand`、**`toCreateRequisitionCommandForShellPharmacyStockIn`**（目标态）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 审批权限 |  |  |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | **`busNo`** | 须 **`APPROVAL`** |
| 3 | Pre | `SupplyRequestQueryService#supplyRequestValidation` |  | **防重复同意 BR-WHS-SO-009** |
| 4 | Cmd | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` |  | **写库点** 出库 **`AGREE`** |
| 5 | Cmd | `SupplyRequestDomainService#flushPendingChanges` |  | 读回一致 |
| 6 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` |  | 取 **AGREE** 行 |
| 7 | Pre | **`SupplyDeliveryQueryService#findRequestIdsHavingCompletedDelivery`** | **`requestId` 列表（一次性）** | **BR-WHS-SO-009**；**语义等价**逐行 **`supplyDeliveryValidation`** 之「已完成发放」判重；**禁止**对 `requestId` 循环调用单条 **`supplyDeliveryValidation`**（**A.9.1**） |
| 8 | Cmd | `SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)` | **出库发放** | **写库点** |
| 9 | Cmd | `InventoryItemDomainService#decreaseQuantity(DecreaseQuantityCommand)` | **源库** 逐行 | **写库点**；数量 **WHS-SO-SDD-R-007** |
| 10 | Cmd | `InventoryItemDomainService#increaseQuantity(IncreaseQuantityCommand)` 或 **`#createInventoryItem(CreateInventoryItemCommand)`** | **目的库** 五元组 **DM-WKF-008** §9.3 | **写库点**；数量 **WHS-SO-SDD-R-007**；**`createInventoryItem`** 冗余列 **WHS-IN-SDD-R-012** |
| 11 | Cmd | `SupplyRequestDomainService#createRequisition(CreateRequisitionCommand)` | **`PHARMACY_STOCK_IN`**、**`original_bus_no`=出库 `bus_no`**、**`bootstrapFinalStatusCode`=`AGREE`**（**目标态**，**WHS-SO-SDD-R-001**）；行 **`itemTable`/`itemId`/数量/批号/单价等** 自出库 **AGREE** 行拷贝 | **写库点** 壳单；详情展示富化见 **AS-INV-PHA-001-SDD** **IF-PHA-IN-03** **`enrichPharmacyStockInDetailProductDisplay`** |
| 12 | Cmd | `TraceNumberDomainService#recordTraceRecordsFromLineItems(...)` 或 **`#recordTraceRecords`** | **`operation_type`=`CABINET_STOCK_OUT`** | **写库点**；**空白 `trace_no` 跳过** **BR-WHS-SO-011** |
| 13 | Asm | `InventoryWarehouseStockOutMapper#toAgreeStockOutResponse` |  |  |

---

### 3.12 IF-WHS-SO-11 `pageProductsForStockOut`（出库选品检索）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/pageProductsForStockOut`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | 与出库源库一致 |
| `productTableName` | String | Y | **`med_medication_definition`** / **`adm_device_definition`** |
| `positiveQuantityOnly` | Boolean | N | **`true`**：仅源库账面 **`quantity > 0`**；**`false`/未传：不过滤数量 |
| `keyword` | String | N |  |
| `pageNo` / `pageSize` | Integer | Y |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long |  |
| `records` | `List<ProductDefinitionItemDto>` | 批号级展开：每库存行 + 产品装配 |

**DTO：`ProductDefinitionItemDto`（库存行选品列族；与 **`IF-PHA-IN-11`** 对偶，`purchaseUnitPrice` 为 **`LOT_NUMBER_COST`**）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productTableName` | String | Y |  |
| `productDefinitionId` | Long | Y |  |
| `productBusNo` | String | N | 目录 **`bus_no`**（**`ProductDefinitionDisplayDto#getProductBusNo()`**） |
| `productName` | String | N |  |
| `specification` | String | N |  |
| `manufacturer` | String | N |  |
| `lotNumber` | String | N | **批号**（批号级选品 UI **必显列**；与 **AS-INV-WHS-003 §1.3「选品主表」** / 前端 `STOCK_OUT_PRODUCT_PICK_TABLE_COLUMNS` 对齐；**不得**像订货/进货选品那样省略） |
| `expirationDate` | `LocalDateTime` | N | **效期** |
| `availableQuantity` | `BigDecimal` | Y | **`sourceLocationId`** 可用量 |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | N | **`buildInboundSelectionBands`** |
| `purchaseUnitPrice` | `BigDecimal` | N | **`LOT_NUMBER_COST`**（**`WHS-SO-SDD-R-004`**）：随本行 **`lotNumber`** 解析；无批号或无命中为 **`null`** |
| `inventoryItemId` | Long | N | 库存行锚点 |

**失败码**：`WHS_SO_102`

**Mapper**：`toPageProductsForStockOutResponse(Page<...>): PageProductsForStockOutResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **`sourceLocationId`** ∈ **`gatedLocationIdsFromActive(listAllActiveWarehouses)`** | 与 **IF-WHS-SO-02** 药库门控同源 |  |
| 2 | Pre | `InventoryItemQueryService#queryInventoryItemsByPage` | **`InventoryItemPageQuery`**：`locationId`=`sourceLocationId`，物品条件；按请求可选 **`positiveQuantityOnly`** 下推 | 批号行主表；仅 **`true`** 时收窄 **`quantity > 0`** |
| 3 | Pre | `InventoryItemQueryService#findAvailableCatalogRowsForOrder`（或 **`#findAvailableCatalogRowsForInventoryItemIds`**） | 当前页库存行 **`id`** 集合 + `sourceLocationId` | **`availableQuantity`** 按 **`inventoryItemId`** 回填；未命中展示 **0** |
| 4 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | ② 行键 |  |
| 5 | Asm | **`ProductDefinitionPurchasePriceResolver#resolveLotNumberCostPrice`** | 逐行 **`LOT_NUMBER_COST`** | **WHS-SO-SDD-R-004** |
| 6 | Asm | **`ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands`** + Mapper |  | **仅读** |

---

### 3.13 IF-WHS-SO-12 `pageLinkableRequisitions`（加载可关联请领列表）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/pageLinkableRequisitions`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | 本药库；**`0`** = 当前用户可见全部药库（**`InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID`**，展开为 `sourceLocationIds`；无可见库时用占位 **`-1`** 空页） |
| `requisitionTypeEnum` | String | N | 收窄为 **`PHARMACY_REQUISITION`** 或 **`DEPT_REQUISITION`**；空则两类并集 |
| `keyword` | String | N |  |
| `pageNo` | Integer | Y |  |
| `pageSize` | Integer | Y |  |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | **不重复 `bus_no` 总数**（再经占用剔除后本页 `content` 可能更短） |
| `content` | `List<WarehouseDocumentDetailRowDto>` | **按 `bus_no` 聚合一行一单** |

**DTO：选单行（聚合头，非品项明细）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 请领单号（一单一行） |
| `typeEnum` | String | Y | **`PHARMACY_REQUISITION`** 或 **`DEPT_REQUISITION`** |
| `statusEnum` | String | Y | **`SupplyStatus#getCode()`**（选单固定 **`AGREE`**） |
| `categoryEnum` | String | N | **`SupplyCategory#getCode()`** |
| `sourceLocationId` | Long | N | 请领来源（**药库**） |
| `purposeLocationId` | Long | N | 请领目标（**药房/科室药房**） |
| `purposeLocationName` | String | N | 目的库位展示名（应用层富化） |
| `applicantId` | Long | N | 申请人 |
| `creatorName` | String | N | 制单人展示名（应用层富化） |
| `totalAmount` | `BigDecimal` | N | 同号各行 **`total_price`** 之和（行级刻度加总） |
| `applyTime` | `LocalDateTime` | N | 同号申请时刻上界 |
| `approvalTime` | `LocalDateTime` | N | 审批时刻（同号稳定口径） |

**Mapper**：`toLinkableRequisitionRows` + `toPageLinkableRequisitionsAggregatedResponse`（对偶 **IF-WHS-IN-12**）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | **`PageWindowGate`** + 机构上下文 | 分页 | — |
| 2 | Pre | （库位展开） | **`sourceLocationId=0`** → **`sourceLocationIds`** = **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActiveWarehouses)`**；无可见库 → **`LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID`（-1）** | 与 **IF-WHS-IN-02**、INV 功能清单 §10.1 一致 |
| 3 | Pre | **`sourceLocationId` 非 0** 时门控 | 须 ∈ 步骤 2 可见药库集合 | 非法 → **WHS_SO_102** |
| 4 | Pre | `SupplyRequestDocumentListQuery` + `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter` | **`typeEnums`**=药房+科室请领（或 `typeEnum`/`requisitionTypeEnum` 收窄）；**`statusEnum`=`AGREE`**；**`sourceLocationId`** 或 **`sourceLocationIds`**（见步骤 2～3） | **禁止** `pageWarehouseDocumentDetail`；**BR-PHA-QL-011** / **BR-DEP-QL-011** 在应用层内存剔除已引用请领 |
| 5 | Asm | `toLinkableRequisitionRows` + `enrichLinkableRequisitionsDisplayNames` + `toPageLinkableRequisitionsAggregatedResponse` + `filterAlreadyReferencedRequisitionsFromLinkablePage` | ④ | 仅读；一行一单 |

---

### 3.14 IF-WHS-SO-13 `getLinkablePharmacyRequisitionDetail`（拉取请领预填明细）

**HTTP**：`POST /api/app/inventoryWarehouseStockOut/getLinkablePharmacyRequisitionDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 已同意请领单号 |
| `sourceLocationId` | Long | N | 源药库门禁，非空时须与请领 **`source_location_id`** 一致 |
| `excludeStockOutBusNo` | String | N | 改单时排除本出库单号（**BR-PHA-QL-011** 自引用豁免） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `originalBusNo` | String | 写入出库单 `original_bus_no`（= 请领 `bus_no`） |
| `requisitionBusNo` | String | 展示用，同 `originalBusNo` |
| `sourceLocationId` | Long | 出库源药库（请领 `source_location_id`） |
| `purposeLocationId` | Long | 出库目的药房（请领 `purpose_location_id`） |
| `categoryEnum` | String | 供应类别 code |
| `practitionerId` | Long | 经手人 |
| `lines` | `List<StockOutDetailLineDto>` | 从请领明细拷贝的出库行；含 **`productName`/`specification`/`manufacturer`/`unitCodeDictText`/`productCatalog`**（**行价保留请领落库价**） |

**Mapper**：`toGetLinkablePharmacyRequisitionDetailResponse(List<SupplyRequestModel>, Map<Long, ProductDefinitionCatalogEditorHintsDto>)`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `busNo` 非空；可选 `sourceLocationId` ∈ 药库门控 | 同 **IF-WHS-SO-12** |  |
| 2 | Pre | `validateOriginalPharmacyRequisitionForStockOut` | 类型 **`PHARMACY_REQUISITION`**、**`AGREE`**、药库匹配、**BR-PHA-QL-011** |  |
| 3 | Pre | `getSupplyByBusNo` + `ProductDefinitionBatchLoader#loadAsMap` | `busNo` |  |
| 4 | Asm | `toGetLinkablePharmacyRequisitionDetailResponse` + `enrichStockOutDetailProductDisplay` + `enrichStockOutDetailLines` | ③ | 拷贝并富化展示 |

## 4. QueryService 与事件刷新

| QueryService / 写表 | 事件（DM §5 摘要） | 更新意图 |
| --- | --- | --- |
| **`wkf_supply_request` 列表路径** | `SupplyRequestCreatedEvent` / `Submitted` / `Withdrawn` / `Agreed` / `Rejected` / `Deleted` | IF-WHS-SO-02 刷新 |
| **`wkf_supply_request` 按 bus_no 聚合路径** | 同上 | IF-WHS-SO-12（选单；非明细分页） |
| **ProductDefinition** | 目录变更事件 | IF-WHS-SO-11 展示 |
| **库存读路径** | **InventoryItem** 数量事件 | IF-WHS-SO-11 可用量 |

字段级映射以各QueryService **§4.2** 为真源；本文不重复。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 序号对照

| IF | App 方法 | Cmd 步骤（§3 序号） | 领域写调用 |
| --- | --- | --- | --- |
| IF-WHS-SO-04 | `createStockOut` | 5 | `createStockOut` |
| IF-WHS-SO-05 | `updateStockOut` | 5 | `createStockOut` |
| IF-WHS-SO-06 | `submitStockOutApproval` | Cmd 1 | `submitApproval` |
| IF-WHS-SO-07 | `withdrawStockOutApproval` | Cmd 1 | `withdrawApproval` |
| IF-WHS-SO-08 | `agreeStockOut` | 4～12 | `agreeRequest` → `createSupplyDelivery` → **`decreaseQuantity`** → **`increaseQuantity`/`createInventoryItem`** → **`createRequisition`** → **Trace** |
| IF-WHS-SO-09 | `rejectStockOut` | Cmd 1 | `rejectRequest` |
| IF-WHS-SO-10 | `removeStockOut` | Cmd 1 | `removeByBusNo` |

### 5.2 事务边界与失败回滚

| 接口 | 事务 | 失败回滚 |
| --- | --- | --- |
| IF-WHS-SO-08 | **单事务** 序号 4～12 | 任一步失败 → 全回滚；对外 **EX-WHS-SO-104** |
| 其它写接口 | 单事务 | 标准回滚 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 同意/提交短时重复 | **`supplyRequestValidation`** + **`findRequestIdsHavingCompletedDelivery`**（批量等价 **`supplyDeliveryValidation`**）+ 状态机 | `SupplyRequestQueryService` / `SupplyDeliveryQueryService` | §3.11（IF-WHS-SO-08）序号 3、7 |
| 对侧壳单防双 | **对 `bus_no` 唯一 + 事务** | 领域 / DB 约束 | §3.11（IF-WHS-SO-08）序号 11 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 编辑冲突 | **乐观版本**或 **`verifyAbleEdit`** 门禁 | `SupplyRequestQueryService` | §3.6（IF-WHS-SO-05） |

### 6.3 重试与容错

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 列表/选品失败可重试 | **EX-WHS-SO-102** | §3.3、§3.12 |

### 6.4 数据脱敏

| 项 | 承诺 |
| --- | --- |
| 追溯码展示 | 按安全策略截断展示（非持久化删改） |

### 6.5 审计日志

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 审批人/时刻/源目的/批号数量 | **`agreeRequest`** + **`cus_trace_number` + `cus_trace_number_flow`** | §3.11（IF-WHS-SO-08）序号 4、12 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 新建→提交→同意→源减目增 + 对侧 **`PHARMACY_STOCK_IN`** **`AGREE`** + **`original_bus_no`** | 集成 | P0 |
| 列表「到何处」、详情效期、选品 **`unitBands`** 四槽 | API | P0 |

### 7.2 非功能测试

| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 同意链路超时与重试 | 手工 / 集成 | P1 |

### 7.3 QueryService一致性

| 用例 | 优先级 |
| --- | --- |
| 事件后列表/明细最终一致 | P0 |

### 7.4 集成测试

| 用例 | 优先级 |
| --- | --- |
| 与 **AS-INV-PHA-001** 列表中联调 **`PHARMACY_STOCK_IN`** 展示 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 |
| --- | --- |
| **GAP-WHS-SO-001/002** | 对侧壳单与 **`CABINET_STOCK_OUT`** **`operation_type`** 以 DM 关闭后补集成用例 |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.15 | 2026-07-24 | **IF-WHS-SO-01/写路径目的门控**：目的下拉与 Gate 改 **`listAllActivePharmacyPartyLocations`**（药房+科室药房显式并集）；与药房业务线 **`listAllActivePharmacies`（仅药房）** 口径分离；对齐 **DM-ADM-003 v1.5** |
| v1.14 | 2026-07-15 | **IF-WHS-SO-11**：Request 增可选 **`positiveQuantityOnly`**；下推 `InventoryItemPageQuery`（非必传；true=源库账面量>0） |
| v1.13 | 2026-07-14 | **IF-WHS-SO-12**：可关联请领改为 **`bus_no` 聚合一行一单**（`countDistinctBusNo` 三件套 + `totalAmount`）；废止选单对 **`pageWarehouseDocumentDetail`** 的误用；与 **IF-WHS-IN-12** 同构；**`sourceLocationId=0`** 展开可见药库 id 集合（**`InventoryWarehouseConstants`**） |
| v1.12 | 2026-07-14 | **IF-WHS-SO-11**：明确批号级选品 UI **必显 `lotNumber`**（与 **AS-INV-WHS-003 §1.3**、其余库存批号行选品同构；非订货/进货产品定义展开） |
| v1.11 | 2026-07-02 | **IF-WHS-SO-11**：可用量按 **`inventoryItemId`** 回填；**IF-WHS-SO-13**：预填 **`StockOutDetailLineDto`** + **`productCatalog`** 富化；对齐 **4666f46ae / 4c8dae681** |
| v1.10 | 2026-05-29 | **WHS-SO-SDD-R-004/005**：选品/详情 **`unitBands`** 与 **`WHS-IN-SDD-R-011`** 最终版对齐 — **`LOT_NUMBER_COST`** / **`PURCHASE`** 均为每包装锚，**`#applyInboundWarehouseBandPrices`** |
| v1.9 | 2026-05-29 | **IF-WHS-SO-08** 序号 **11** 脚注：对侧壳单详情 **项目名称/规格/厂家** 由 **IF-PHA-IN-03** **`enrichPharmacyStockInDetailProductDisplay`** 富化（**AS-INV-PHA-001-SDD v1.0.7**）；互指 **AS-INV-WHS-003** 开篇分工说明 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.8 | 2026-05-27 | **IF-WHS-SO-02**：**`creatorName`/`approverName`/`totalAmount`**；**IF-WHS-SO-03**：详情行 **`productName`/`specification`/`manufacturer`** QueryService富化；**WHS-SO-SDD-R-010**：目的库新建承袭源库效期 |
| v1.7 | 2026-05-24 | **库位门控统一**：**`CurrentManagedLocationIdsResolver`**（**IF-WHS-SO-01** **`#narrowActiveLocationsByManaged`**；列表/写/选品药库 **`#gatedLocationIdsFromActive`**）；**剔除** **`PractitionerRoleQueryService#listAssignmentsByPractitioner`** 于库位门控；**管辖 empty 过渡态不收窄**；§2.1、§2.2、§3.2、§3.3、§3.5、§3.12、§3.13；编排权威 **AS v0.9** |
| v1.6 | 2026-05-21 | **IF-WHS-SO-08** 持久化刷盘改为 **`SupplyRequestDomainService#flushPendingChanges`**（**B.2** 禁应用层直调 **`SupplyRequestRepository#flush`**）；§2.1/§2.2 同步 |
| v1.5 | 2026-05-15 | **WHS-SO-SDD-R-009**：与进货 SDD **WHS-IN-SDD-R-013** 对齐之时间与操作者口径；**`StockOutListRowDto`** 增补 **`occurrenceTime`**；**IF-WHS-SO-04/05** 请求体 **`practitionerId`**、明细 **`occurrenceTime`** 必填；**IF-WHS-SO-06/08** Mapper 签名（**`submitApproval`** 含 **`applicantId`**；**`agree`** 废弃 **`approvalTime`/`approverId`**）；§1.3、§3.2、§3.5、§3.7、§3.11 |
| v1.4 | 2026-05-15 | **WHS-SO-SDD-R-007** / **§3.11** 序号 **10**：**`createInventoryItem`** 冗余列与 **`WHS-IN-SDD-R-012`** 及 **`InventoryWarehouseInboundReceiptMapper#toCreateInventoryItemCommand`** 同构；§1.3、§3.11 |
| v1.3 | 2026-05-14 | **WHS-SO-SDD-R-004**：批号维选品同档引用改为 **`AS-INV-PHA-001-SDD` `PHA-SDD-R-004` / `IF-PHA-IN-11`**（药库进货选品 **`IF-WHS-IN-11`** 为 **`PURCHASE`**，不再互指）；§3.12 DTO 脚注 |
| v1.2 | 2026-05-14 | **WHS-SO-SDD-R-004**：**IF-WHS-SO-11** **`purchaseUnitPrice`** 为 **`LOT_NUMBER_COST`**（随库存行 **`lot_number`**）；§1.1 编排权威 **AS v0.8**；§2.2 白名单补 **`InventoryItemQueryService`** / **`SupplyDelivery*`** / **`SupplyPersistedSplitQuantity`** 等；§2.3 价类表；§3.12 编排序列 |
| v1.1 | 2026-05-14 | 新增 **WHS-SO-SDD-R-007**（供应行 **`item_quantity`** 拆零持久化 + **`SupplyPersistedSplitQuantity`**；同意链**剔除** **`MedicationProductQuantityToSplitUnitConverter`**）；§2.1/§2.2 依赖与白名单同步；编排权威 **AS v0.7** |
| v1.0 | 2026-05-14 | 初稿：对齐 **AS-INV-WHS-003** 全 IF；**WHS-SO-SDD-R-001** 声明 **`CreateRequisitionCommand.bootstrapFinalStatusCode`**（**`AGREE`**）**目标态待生成**；§3.13 **`WarehouseDocumentDetailRowDto`** 全量字段 v1.1 补录 |
