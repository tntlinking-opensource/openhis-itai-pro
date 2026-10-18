# 药库进货单 · 应用服务详细设计（SDD）

> 编码：AS-INV-WHS-002-SDD  
> 版本：v2.45 | 日期：2026-08-31 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药库进货单应用服务（库存 · 药库 · **采购进货**页，`type_enum`=`CABINET_INBOUND_RECEIPT`，与 **`com.zxplat.domain.share.enums.SupplyType#CABINET_INBOUND_RECEIPT#getCode()`** 一致） |
| 边界 | 编排 **SupplyRequest** 进货建单、修订、审批、删除；**审批同意**后应用层编排 **SupplyDelivery（入库发放）**、**按批号补录批号维定价（`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`）**、**InventoryItem** 在**拆零（最小）单位**下增加（**BR-WHS-IN-009** / **BR-SR-012**），及对 **`trace_no`/`traceNo` 非空白** 明细写入追溯主子表（**IF-TRC-05** / **WHS-IN-SDD-R-015**，**DM-CUS-010 v2.0.4**）。**不包含** **`CABINET_INBOUND_RECEIPT_INSPECTION`** 进货验收独立页（另 AS）。**不包含** 医保进销存对外上传（遗留开关路径）。 |
| 编排权威 | `AS-INV-WHS-002-药库进货单.md`（**v0.43**）§2、§4 |
| 方法权威 | `DM-WKF-006-SupplyRequest-供应请求.md` §6；`DM-WKF-005-SupplyDelivery-供应发放.md` §6；`DM-WKF-008-InventoryItem-库存项.md` §6；**计费侧** **ChargeItemDefinition** 定价写路径（以 **SDD** §2.2 白名单方法为准）；`DM-CUS-010-TraceNumberRecord-追溯码记录.md` §6 与 **`DM-CUS-010-TraceNumberRecord-追溯码记录-SDD.md`** §5.1；`DM-ADM-003-Location-位置.md` §6.2；`DM-CUS-007-Supplier-供应商.md` §6.2；`DM-WKF-006-SupplyRequest-供应请求-SDD.md`、`DM-WKF-005-SupplyDelivery-供应发放-SDD.md`、`DM-WKF-008-InventoryItem-库存项-SDD.md`（命令与领域服务签名）；QueryService `DM-WKF-006 §6.2 `SupplyRequestQueryService` / `InventoryItemQueryService``、`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` §3 |
| 严格对齐 | IF **IF-WHS-IN-01～12** 与 AS 母表一致 |
| 实现阶段口径 | **目标态全量契约**（非「当前已上线范围」） |
| **姊妹文档（定稿基线）** | **`AS-INV-WHS-001-SDD`（v1.0 定稿）**：结构、§2.3 枚举锚点口径、白名单 Gate/Pre 记法与本文对齐；进货模块在 **IF-WHS-IN-08** 于 **`agreeRequest`（§3.9 序号 4）**之后，以 **§3.9 序号 7～10** 之 **Cmd** 接续 **SupplyDelivery**、**ChargeItemDefinition（批号维定价）**、**InventoryItem**、**TraceNumberDomainService（`cus_trace_number` + `cus_trace_number_flow`）** 写库（与 §3.1.1 四类阶段一致，不另立阶段名），及对应依赖（§2.1）。 |
| **实现裁决：WHS-IN-SDD-R-001** | **订货审批不在本服务**：与 **`AS-INV-WHS-001-SDD` §1.3 `WHS-SDD-R-001`** 对偶 — 药库订货 **AGREE** 仅在 **AS-INV-WHS-001**；本服务仅在 **IF-WHS-IN-04 / IF-WHS-IN-12** 读路径校验来源订货（**BR-WHS-IN-003**）。 |
| **实现裁决：WHS-IN-SDD-R-002** | **进货修订（IF-WHS-IN-05）**：与订货 SDD **WHS-SDD-R-002** 同构 — **`CreateInboundReceiptCommand` 携带既有可编辑态 `busNo`** 时领域执行明细全量对齐（软删 + 重插），与 **BR-SR-003** / **verifyAbleEdit** 一致；领域未支持前 **Cmd 不得投产**。 |
| **实现裁决：WHS-IN-SDD-R-003** | **IF-WHS-IN-04 互斥主路径**：**若** `originalBusNo` 非空 → Pre 校验订货 `type_enum` 对应 **`CABINET_PURCHASE_ORDER`**（`SupplyType#getCode()`）且 **`AGREE`**（`SupplyStatus#getCode()`）及 **DM** §6.1.2 拷贝语义；**否则**（免申请）**跳过**来源订货 Pre，直接建 **`CABINET_INBOUND_RECEIPT`**。两条路径互斥，不得并行校验虚构订货号。 |
| **实现裁决：WHS-IN-SDD-R-004** | **IF-WHS-IN-08**：**`SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)`** 成功后必须以 **Cmd** 接续：**`SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)`**（逐行）、**批号维定价写库**（**WHS-IN-SDD-R-006**）及 **`InventoryItemDomainService#createInventoryItem` / `#increaseQuantity`**（**WHS-IN-SDD-R-007**；**`createInventoryItem`** 新建路径冗余列见 **WHS-IN-SDD-R-012**；五元组分支见 §3.9）；**InventoryItem** 成功后须按 **WHS-IN-SDD-R-009** 追加 **`cus_trace_number` + `cus_trace_number_flow`**；**禁止**在 **AS-INV-WHS-001**（订货单）**审批同意**路径内执行本条编排。 |
| **实现裁决：WHS-IN-SDD-R-005** | **IF-WHS-IN-11 选品列表**：**`purchasePrice`** 真源为定价表 **`PURCHASE`**（**每包装单位进价**）；经 **`#resolveFirstPurchasePriceValue`**。**`unitBands`[].`unitPrice`** 仅以该价为包装锚，经 **`#applyInboundWarehouseBandPrices`** 写四槽（**`P_pkg × R_slot / R_pkg`**，采购槽与包装单位不同时由比例推算）。**禁止**取 **`SALE`**；**`availableQuantity`** 恒 **`null`**。 |
| **实现裁决：WHS-IN-SDD-R-006** | **IF-WHS-IN-08 批号维定价补录**：**`priceValue`** 为**每包装单位**单价。供应行 **`price`** 须经 **`#resolveLotNumberPackageUnitPriceForInboundAgree`** 归一后写入 **`LOT_NUMBER_COST`** 与 **`LOT_NUMBER_PRICE`**（同值）。同价跳过；异价（**R-018** 门禁已过）先 **`deactivateActivePriceLinesByCommand`** 再写入；无价首次写入。**`LOT_NUMBER_COST`** 为库房后续读锚；**`LOT_NUMBER_PRICE`** **仅写库**，供门诊/收费下游，**库房读路径不消费**。维护态 **`PURCHASE`** 亦为每包装价。目录参考价回写见 **WHS-IN-SDD-R-019**（与批号写库同序号 **8**，批号同价跳过时仍执行）。 |
| **实现裁决：WHS-IN-SDD-R-019** | **IF-WHS-IN-08 目录参考价跟随（对齐 AS BR-WHS-IN-019）**：批号维写价之后（批号同价跳过时亦执行），经 **`CatalogReferencePriceSyncSupport#syncPurchaseAndSaleIfChanged`** 以本行归一包装进价回写实例级 ACTIVE **`PURCHASE`** 与 **`SALE`**（同值；异值先停用再 **`addItemPurchasePrice`/`addItemRetailPrice`**；同值跳过）。参考价仅供 **IF-WHS-IN-11** 选品默认与目录展示，**禁止**当作报表/账单真价（**BR-PRICE-SRC-001**）。同单多行同品后行覆盖。院点中草药加价 after 钩子在抬高 **`LOT_NUMBER_PRICE`** 后须 **`syncSaleIfChanged`** 对齐 **`SALE`**（不改 **`PURCHASE`**）。 |
| **实现裁决：WHS-IN-SDD-R-007** | **IF-WHS-IN-08 库存拆零入账**：**`wkf_supply_request.item_quantity`** 已与 **`wkf_inventory_item.quantity`** 同为**拆零（最小）单位**持久化；**`createInventoryItem` / `increaseQuantity`** 的增量数量取 **`SupplyPersistedSplitQuantity#requireSupplyLineItemQuantityAsPersistedSplitUnit(SupplyRequestModel, …)`**（或等价：物品表白名单 + **`item_quantity` > 0**），**禁止**再按行上 **`unit_code`** 调用 **`MedicationProductQuantityToSplitUnitConverter`**（该换算器仅用于发药等业务单位语义）。**`unitCode`** 落 **`ProductDefinitionDisplayDto#getSplitUnitCode`**：先 **`ProductDefinitionBatchLoader#loadAsMap`**，缺投影或 **`splitUnitCode` 空白** → **WHS_IN_104**；**新建**库存行 **`unitCode`** = **`splitUnitCode`**。 |
| **实现裁决：WHS-IN-SDD-R-011** | **库房单据读锚（最终）**：选品/详情槽价计算**仅** **`PURCHASE`**（无批号，每包装）与 **`LOT_NUMBER_COST`**（有批号，每包装）两档；经 **`#applyInboundWarehouseBandPrices`** / **`#applyInboundWarehouseDetailLineBandPrices`**。**禁止**读 **`SALE`**、**`LOT_NUMBER_PRICE`** 及采购锚传播。 |
| **实现裁决：WHS-IN-SDD-R-008** | **TRC-WIDE-R-001（列宽）**：凡承载「原始追溯扫码串」之 **`trace_no` 物理列**（至少 **`wkf_supply_request.trace_no`**、**`cus_trace_number_record.trace_no`**）及 **HTTP/DTO 同源 `traceNo`** 之契约最大长度，目标 DDL 统一 **VARCHAR(50000)**；**不得**以 **VARCHAR(255)** 作为目标宽度；超长入参须在 **IF-WHS-IN-04/05** **Gate** 或 **Cmd** 前拒绝（对齐 **`WHS_IN_001`/`WHS_IN_004`** 系业务失败口径，见 §3.5/§3.6）。 |
| **实现裁决：WHS-IN-SDD-R-009** | **IF-WHS-IN-08 追溯入库写库（遗留·成码前现行）**：在 **§3.9** 序号 **9**（**InventoryItem**）**整单成功**后、**Asm** 之前，对 **`trace_no`/`traceNo` 去空白后非空** 的每一行，调用 **`TraceNumberDomainService#recordTraceRecords*`** 向 **`cus_trace_number` + `cus_trace_number_flow`** 追加轨迹；**`operation_type`** = **`CABINET_INBOUND_RECEIPT`**；**空白行跳过**；失败 → **WHS_IN_104**，与序号 **4～10** 同事务。**目标态**见 **WHS-IN-SDD-R-015**，成码后 **R-009 废止**。 |
| **实现裁决：WHS-IN-SDD-R-015** | **IF-WHS-IN-08 追溯入库写库（目标态 · TRC v2）**：序号 **9** 成功后调用 **`TraceNumberRecordOrchestrator#recordFromSupplyLine`**（**AS-CUS-TRC-001-SDD §3.1**）→ **`TraceNumberDomainService#recordTraceRecordsFromLineItems`**；**`flow_category_enum=HOSPITAL_IO`**、**`flow_direction_enum=IN`**；**`operation_type=CABINET_INBOUND_RECEIPT`**；每码 **`trace_unit_code`+`trace_unit_ratio` 取药典写入主档快照**（供应表**无**贴码单位列）；写 **`cus_trace_number` + `cus_trace_number_flow`**；失败 → **WHS_IN_104**。 |
| **实现裁决：WHS-IN-SDD-R-010** | **IF-WHS-IN-02 列表真源**：进货分页列表的 **`total`/分页/聚合行** 以 **`wkf_supply_request`** 为唯一数据源，经 **`SupplyRequestQueryService#countDistinctBusNo`**、**`#listDistinctBusNoPage`**、**`#listByBusNosWithFilter`**（入参 **`SupplyRequestDocumentListQuery`**，继承 **`AbstractPageQuery`**，分页边界 **B.10.11**）；应用层 **`InventoryWarehouseInboundReceiptMapper#toInboundReceiptListAggregated`** 按 **`bus_no` 装配 `InboundReceiptListRowDto`**，**`SupplierQueryService`/`PractitionerQueryService`** 富化展示名；**禁止**以 **WarehouseDocumentDetail** QueryService替代本列表（该QueryService仍用于 **IF-WHS-IN-12** 等明细分页）。 |
| **实现裁决：WHS-IN-SDD-R-013** | **供应请求时间与操作者（订货/进货/出库统一）**：**`occurrence_time`** 由前端随明细传入（制单/业务时刻）；**`apply_time`** 仅在 **提交审批**（**`submitApproval`**）成功时由领域/服务端写 **当前时间**，草稿创建与修订命令中各 **`apply_time` 恒为 `null`**；**`approval_time`** 在 **同意/驳回** 时由领域写 **当前时间**。**`applicant_id`**：建单行与会话绑定医务人员一致；**提交审批** 时 **`SubmitApprovalItemCommand.applicantId`** 取会话医务人员。**`approver_id`**：**同意/驳回** 装配取 **当前操作人主键**（`operatorId`）；同意/驳回请求体**不再**接收 **`approverId`/`approvalClock`**。**`practitioner_id`**：**新建/修订** 请求体 **`CreateInboundReceiptRequest`/`UpdateInboundReceiptRequest#practitionerId`** 必填，由前端选择经手人。 |
| **实现裁决：WHS-IN-SDD-R-014** | **订货→进货 1:1（BR-WHS-IN-012）**：已 **`AGREE`** 订货单 **`bus_no`** 仅允许被一张**未软删** **`CABINET_INBOUND_RECEIPT`** 以 **`original_bus_no`** 引用；**不区分**占用方进货单 **`status_enum`**；**已逻辑删除**进货单不再占用。**IF-WHS-IN-04/12/13** 与 **`SupplyRequestDomainService#createInboundReceipt`** 新建路径均须门禁；改单 **`IF-WHS-IN-05`** 排除本单 **`bus_no`**。实现：`SupplyRequestRepository#existsByOriginalBusNoAndTypeEnum`（及 **`…AndBusNoNot`**）+ **`SupplyRequestQueryService#existsUndeletedSupplyByOriginalBusNo`**；与 **AS-INV-WHS-003** **IF-WHS-SO-12** / **BR-PHA-QL-011** 同构。 |
| **实现裁决：WHS-IN-SDD-R-016** | **IF-WHS-IN-08 追溯 Gate（写库前）**：序号 **9**（**InventoryItem**）成功后、序号 **10**（追溯写库）之前，应用层 **`InventoryWarehouseInboundReceiptAppService`** 须：**①** **`assertInboundReceiptTraceCodesNotDuplicatedAcrossLines`**（**BR-WHS-IN-013**）；**②** 逐非空 **`trace_no`** 行 **`assertInboundTraceCodeCountMatchesSplitQuantity`**（**BR-WHS-IN-014**：应扫码数 = ceil(本行拆零 / 每码药典追溯单位拆零)，半盒允许）。失败 → **WHS_IN_104**。写侧异阶前填实入拆零由 **`TraceNumberRecordOrchestrator`** 展开；贴码单位取药典。 |
| **实现裁决：WHS-IN-SDD-R-017** | **库房族明细数量单位 UI（AS BR-WHS-IN-015）**：**后端**仍下发完整业务四槽 **`unitBands`**（及药典追溯字段）；**前端**数量单位下拉对同 `unitCode` 且同 `ratioToSplit` 的槽合并展示，映射优先 **PACKAGE → SPLIT → STOCK → PURCHASE**；同码不同倍不合并；**TRACE / 药典追溯单位不进数量单位下拉**。落库仍为拆零 **`item_quantity`** + 录入 **`unit_code`**（**WHS-IN-SDD-R-007**）。实现锚：`openhis-pro-ui` **`inboundUnitBandSelectOptions`**。 |
| **实现裁决：WHS-IN-SDD-R-012** | **供应同意链 `createInventoryItem` 新建库存项冗余列**（**IF-WHS-IN-08**；**IF-PHA-IN-08**；**IF-WHS-SO-08** 目的库 **新建** 分支）：**`InventoryItemDomainService#createInventoryItem`** 落 **`wkf_inventory_item`** 时须持久化 **`production_date`**（供应行 **`start_time`**）、**`expiration_date`**（**`end_time`**）及 **`category_code`/`py_str`/`wb_str`**（自 **`ProductDefinitionDisplayDto`**，与 **`loadAsMap`** 同行快照；应用层经 **`InventoryWarehouseInboundReceiptMapper#toCreateInventoryItemCommand(..., ProductDefinitionDisplayDto productDefinition)`** 写入 **`CreateInventoryItemCommand`**；**`productDefinition` 为 `null`** 时分类/检索码不入库）。五元组已存在走 **`increaseQuantity`** 时**不**要求同步更新上述冗余列。 |
| **实现裁决：WHS-IN-SDD-R-018** | **同表同品同批号进价一致性（与供应商无关；对齐 AS BR-WHS-IN-018）**：**IF-WHS-IN-04/05/08** Gate：已存在 ACTIVE **`LOT_NUMBER_COST`** 且与本次归一包装进价**相同** → 允许（写路径跳过）；尚无批号进价 → 允许首次写入。若进价**不同**：仅当（1）全院该 `itemTable+itemId+lotNumber` 未删库存数量合计为 0，（2）该实例+批号定价定义下从未产生过收费行（含已退费；软删草稿除外），（3）无同品同批号发药/发耗行时允许改价；**同意写路径**先 **`DeactivateActivePriceLinesCommand`** 停用旧 ACTIVE **`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`** 再 **`addLotPurchasePrice`/`addLotSalePrice`**。否则拒绝（**`WHS_IN_001`/`WHS_IN_104`**），文案区分库存/收费/发放。**供应单（SupplyRequest）不构成阻塞**。实现锚：**`LotNumberPurchasePriceConsistencyGate`** + InventoryItem/ChargeItem/MedicationDispense/DeviceDispense QueryService。库存入账按 **五元组**（含 **`supplier_id`**）；批号价仍按「品+批号」一条。 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 字段基于 AS 语义、DM §3/§6、QueryService §3 查询签名；进货验收字段（`lot_number`、`trace_no`、`price`、`total_price`）以 **DM-WKF-006** §3.1 / §6.1.2 为准；**`traceNo`/`trace_no`** 契约最大长度与目标 DDL 列宽见 **WHS-IN-SDD-R-008**（**TRC-WIDE-R-001**） |
| **新建 `bus_no`** | **`SupplyRequestDomainService`** 在首行 **`busNo` 空白** 时调用 **`BusinessSequenceService#getSeqByDay`**；**序号键前缀** = **`SupplyType.busNoSeqKey`**（进货 **`CIR`**），与 **`wkf_supply_request.type_enum`** 分桶；**无法匹配 `SupplyType` 白名单**时回退 **`SR`**（**`SupplyType.busNoSeqPrefixForTypeCode`**；**不含 `SQ`**） |
| QueryService口径 | **IF-WHS-IN-02** 进货分页列表以 **`wkf_supply_request`（`SupplyRequestQueryService`）** 为数据源，在领域侧完成 **`COUNT(DISTINCT bus_no)`** 与 **`GROUP BY bus_no` 分页**，应用层将多行装配为 **`InboundReceiptListRowDto`**（每 **`bus_no` 一行**；`total` 为去重后单据条数）；**IF-WHS-IN-12** 可关联订货列表仍以 **WarehouseDocumentDetail** QueryService **明细行**分页；选品以 **ProductDefinition** 为主（**Q-PD-004**），**IF-WHS-IN-11** 仅由应用层按 **WHS-IN-SDD-R-005** 二次装配 **`PURCHASE`** 采购进价，**不**在选品路径汇总 **`InventoryItem`** 可用量；详情与审批后再读路径仍主要使用 **`SupplyRequestQueryService#getSupplyByBusNo`**（写模型一致口径）；**凡 `SupplyType`/`SupplyStatus`/`SupplyRequestType`/`SupplyCategory`/`PriceType` 对外与入参、Query 装配一律使用 `getCode()` 字符串（如 `CABINET_INBOUND_RECEIPT`、`AGREE`、`CABINET`、`STOCK_SUPPLY`、`PURCHASE`、`LOT_NUMBER_COST`、`LOT_NUMBER_PRICE`），禁止以 sortOrder 数字对外表述** |
| 事务口径 | 每个写接口默认单事务；Pre/Gate 失败**禁止**进入 Cmd；**IF-WHS-IN-08** 默认单事务覆盖 **§3.9** 编排序列序号 **1～11**（**`agreeRequest` 写库后** **Pre** 序号 **5～6** 再读行并校验发放重复；**Cmd** 为序号 **4、7～10**；**Asm** 为序号 **11**），异步策略与补偿见 §5.2，对齐 AS §7 |

---

## 2. 代码结构

```text
com/openhis/health/application/inventory/warehouse/inboundreceipt/
  service/
    InventoryWarehouseInboundReceiptAppService.java
  request/
    LoadInboundReceiptFormInitRequest.java
    PageInboundReceiptsRequest.java
    GetInboundReceiptDetailRequest.java
    CreateInboundReceiptRequest.java
    UpdateInboundReceiptRequest.java
    SubmitInboundReceiptApprovalRequest.java
    WithdrawInboundReceiptApprovalRequest.java
    AgreeInboundReceiptRequest.java
    RejectInboundReceiptRequest.java
    RemoveInboundReceiptRequest.java
    PageProductsForInboundReceiptRequest.java
    PageLinkablePurchaseOrdersRequest.java
  response/
    LoadInboundReceiptFormInitResponse.java
    PageInboundReceiptsResponse.java
    GetInboundReceiptDetailResponse.java
    CreateInboundReceiptResponse.java
    UpdateInboundReceiptResponse.java
    SubmitInboundReceiptApprovalResponse.java
    WithdrawInboundReceiptApprovalResponse.java
    AgreeInboundReceiptResponse.java
    RejectInboundReceiptResponse.java
    RemoveInboundReceiptResponse.java
    PageProductsForInboundReceiptResponse.java
    PageLinkablePurchaseOrdersResponse.java
  dto/
    WarehouseDocumentDetailRowDto.java
    InboundReceiptListRowDto.java
    InboundReceiptLineDto.java
    InboundReceiptDetailLineDto.java
    ProductDefinitionItemDto.java
  mapper/
    InventoryWarehouseInboundReceiptMapper.java
    InventoryWarehouseInboundReceiptMapperImpl.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 / 审批 **`requireOperatorId`**；制单人 **`findByUserId`** 前 Gate（**B.6.5**） |
| `CurrentManagedOrganizationIdsResolver` | 会话科室 **`requireSessionOrganizationUnitId`**（**B.6.5**）；**`PractitionerRoleScopeLoader`** 人员下拉 org 入参；**禁止** Request 传 `organizationId` |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**（下拉）、**`gatedLocationIdsFromActive`**（Gate id 集）、**`assertLocationInManagedGate`**；管辖 empty 过渡态不收窄（**B.6.5**） |
| `SupplyRequestDomainService` | `createInboundReceipt`、`submitApproval`、`withdrawApproval`、`agreeRequest`、`rejectRequest`、`removeByBusNo` |
| `SupplyRequestQueryService` | `getSupplyByBusNo`、`supplyRequestValidation`、`verifyAbleEdit`；**IF-WHS-IN-02**：`countDistinctBusNo`、`listDistinctBusNoPage`、`listByBusNosWithFilter`（入参 **`SupplyRequestDocumentListQuery`**） |
| `SupplyDeliveryDomainService` | `createSupplyDelivery`（**§3.9** 序号 **7**） |
| `ChargeItemDefinitionDomainService` | `addLotPurchasePrice` / `addLotSalePrice`（或等价 **`SavePriceLineCommand`** 批量两行，见 **WHS-IN-SDD-R-006**）；实例级 **`addItemPurchasePrice`/`addItemRetailPrice`**（经 **`CatalogReferencePriceSyncSupport`**，**WHS-IN-SDD-R-019**）（**§3.9** 序号 **8**） |
| `InventoryItemDomainService` | `createInventoryItem`、`increaseQuantity`（**§3.9** 序号 **9**；数量/单位须满足 **WHS-IN-SDD-R-007**） |
| `TraceNumberDomainService` | `recordTraceRecords`、`recordTraceRecordsFromLineItems`（**§3.9** 序号 **10**；须满足 **WHS-IN-SDD-R-009**） |
| `LocationQueryService` | **`listAllActivePharmacies`**、**`listAllActiveWarehouses`**（IF-WHS-IN-01：启用态药房/药库**全库**主数据，**不按 `organizationId` 过滤**；下拉收窄经 **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`**，**管辖 empty 则不收窄**，有交则仅辖内）；IF-WHS-IN-02/04/11/12 **可选药库 id 集合**：**`listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**（与初始化药库下拉同源；**管辖 empty → 全库启用态药库 id**；**IF-WHS-IN-05** **`关键校验`** 与同路径语义对齐，§3.6 未单列 Gate） |
| `PractitionerRoleScopeLoader` | **`listActivePractitionersForRoleInOrganization`**（药师/库管；`WhaleRoleLookup` + **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`**；口径：**`adm_pr_assign.org_id`**） | IF-WHS-IN-01 |
| `SupplierQueryService` | `listSuppliersByTypeAndActive`、`findSupplierById` |
| `PractitionerQueryService` | `findByUserId` | IF-WHS-IN-01 制单人解析 |
| `WhaleRoleLookup` | `ROLE_PHARMACIST` / `ROLE_WAREHOUSE` → `whale_roles.id` | IF-WHS-IN-01 |
| `SupplyRequestQueryService` | `pageWarehouseDocumentDetail`（**IF-WHS-IN-12** 等明细分页；**不含 IF-WHS-IN-02** 进货列表，见 **WHS-IN-SDD-R-010**） |
| `ProductDefinitionBatchLoader` | `loadAsMap`（审批后 **splitUnitCode** 等） |
| `ProductDefinitionPurchasePageLoader` | IF-WHS-IN-11：`pageProductForPurchase`（进货选品分页；`application.shared.product`） |
| `ProductDefinitionSelectionTerminologysupport | IF-WHS-IN-11：选品页术语/单位展示预热（与 **ProductDefinitionCatalogUnitBandsAssembler** 联用） |
| `ChargeItemDefinitionQueryService` | `findPurchaseItemByInstanceId`（**IF-WHS-IN-11** 选品、**IF-WHS-IN-03** 等：**`PURCHASE`**，经 **`ProductDefinitionPurchasePriceResolver#resolvePurchasePrice`**）；`existsLotNumberCostOrLotNumberPriceForInstanceAndLot`（**IF-WHS-IN-08** 批号维去重，**WHS-IN-SDD-R-006**） |
| `SupplyDeliveryQueryService` | `supplyDeliveryValidation`（**IF-WHS-IN-08** Pre：逐 **`requestId`** 防重复已完成发放，对齐 **BR-WHS-IN-010**） |
| `InventoryItemQueryService` | `findFirstIdByInventoryMatchKey`（**IF-WHS-IN-08**：五元组命中既有库存项） |
| `InventoryWarehouseInboundReceiptMapper` | Request/Response、领域模型 ↔ DTO 装配 |
| `SupplyRequestDomainService` | **`flushPendingChanges`**（**IF-WHS-IN-08**：**`agreeRequest`** 后同持久化单元落盘，再 **`getSupplyByBusNo`** 读回 **AGREE** 行；**B.2** 禁直调 **`SupplyRequestRepository`** / **`EntityManager`**，见 §3.9） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActivePharmacies`**、**`listAllActiveWarehouses`**（IF-WHS-IN-01）；**`listAllActiveWarehouses`**（IF-WHS-IN-02/04/05/11/12 Gate 药库集合，经 **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**） |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** | IF-WHS-IN-01；IF-WHS-IN-02 Gate、IF-WHS-IN-04 Gate、IF-WHS-IN-11 Gate、IF-WHS-IN-12 Gate |
| `SupplierQueryService` | `listSuppliersByTypeAndActive`、`findSupplierById` | IF-WHS-IN-01、IF-WHS-IN-04 Gate、IF-WHS-IN-05 Pre |
| `PractitionerQueryService` | `findByUserId` | IF-WHS-IN-01 |
| `PractitionerRoleScopeLoader` | **`listActivePractitionersForRoleInOrganization`** | IF-WHS-IN-01 |
| `WhaleRoleLookup` | `findRoleIdByCode(ROLE_PHARMACIST/ROLE_WAREHOUSE)` | IF-WHS-IN-01 |
| `SupplyRequestDomainService` | `createInboundReceipt` | IF-WHS-IN-04、IF-WHS-IN-05 |
| `SupplyRequestDomainService` | `submitApproval` | IF-WHS-IN-06 |
| `SupplyRequestDomainService` | `withdrawApproval` | IF-WHS-IN-07 |
| `SupplyRequestDomainService` | `agreeRequest` | IF-WHS-IN-08 |
| `SupplyRequestDomainService` | `rejectRequest` | IF-WHS-IN-09 |
| `SupplyRequestDomainService` | `removeByBusNo` | IF-WHS-IN-10 |
| `SupplyRequestQueryService` | `getSupplyByBusNo` | IF-WHS-IN-03、IF-WHS-IN-04 Pre、IF-WHS-IN-05 Pre、IF-WHS-IN-06/07/08/09/10 Pre；**IF-WHS-IN-08** **§3.9** 序号 **5**（**AGREE** 后再读） |
| `SupplyRequestQueryService` | `supplyRequestValidation` | IF-WHS-IN-08 Pre |
| `SupplyRequestQueryService` | `verifyAbleEdit` | IF-WHS-IN-05 Pre |
| `SupplyRequestQueryService` | `countDistinctBusNo`、`listDistinctBusNoPage`、`listByBusNosWithFilter` | **IF-WHS-IN-02**（**WHS-IN-SDD-R-010**） |
| `SupplyRequestQueryService` | `pageWarehouseDocumentDetail` | **IF-WHS-IN-12**（可关联订货等QueryService明细分页） |
| `ProductDefinitionPurchasePageLoader` | `pageProductForPurchase` | IF-WHS-IN-11 |
| `ChargeItemDefinitionQueryService` | `findPurchaseItemByInstanceId`（**IF-WHS-IN-11** 选品、**IF-WHS-IN-03** 等 **`PURCHASE`**）；`existsLotNumberCostOrLotNumberPriceForInstanceAndLot`（**IF-WHS-IN-08**；**WHS-IN-SDD-R-006**） | IF-WHS-IN-11 Asm；IF-WHS-IN-08 **§3.9** 序号 **8** 前 |
| `InventoryItemQueryService` | `findFirstIdByInventoryMatchKey` | IF-WHS-IN-08 **§3.9** 序号 **9**（分支判定） |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-WHS-IN-03 Asm（可选，物品名称回显）；**IF-WHS-IN-08** **§3.9** 序号 **9** 前取 **`splitUnitCode`**（**WHS-IN-SDD-R-007**） |
| `ProductDefinitionPurchasePriceResolver` | `resolvePurchasePrice` | **IF-WHS-IN-11** Asm（**WHS-IN-SDD-R-005**；**`PURCHASE`**）；**IF-WHS-IN-03** Asm（**WHS-IN-SDD-R-011**） |
| `ProductDefinitionCatalogUnitBandsAssembler` | **`applyInboundWarehouseBandPrices`** / **`applyInboundWarehouseDetailLineBandPrices`**（**static**）；同意链 **`resolveLotNumberPackageUnitPriceForInboundAgree`** | **IF-WHS-IN-03** Asm（**WHS-IN-SDD-R-011**）；**IF-WHS-IN-11** 选品 **`unitBands`** 单价 |
| `SupplyDeliveryDomainService` | `createSupplyDelivery` | IF-WHS-IN-08 **§3.9** 序号 **7** |
| `ChargeItemDefinitionDomainService` | `addLotPurchasePrice`、`addLotSalePrice`（或 **`SavePriceLineCommand`** 批量两行）；`addItemPurchasePrice`、`addItemRetailPrice`（目录参考价） | IF-WHS-IN-08 **§3.9** 序号 **8** |
| `CatalogReferencePriceSyncSupport` | `syncPurchaseAndSaleIfChanged`（**WHS-IN-SDD-R-019**） | IF-WHS-IN-08 **§3.9** 序号 **8** |
| `InventoryItemDomainService` | `createInventoryItem`、`increaseQuantity` | IF-WHS-IN-08 **§3.9** 序号 **9** |
| `TraceNumberDomainService` | `recordTraceRecords` | IF-WHS-IN-08 **§3.9** 序号 **10** |
| `TraceNumberDomainService` | `recordTraceRecordsFromLineItems` | IF-WHS-IN-08 **§3.9** 序号 **10**（与 `recordTraceRecords` **二选一**落地，以领域已投产签名为准） |
| `SupplyPersistedSplitQuantity` | `requireSupplyLineItemQuantityAsPersistedSplitUnit`（**static**） | IF-WHS-IN-08 **§3.9** 序号 **9**（**WHS-IN-SDD-R-007**） |
| `SupplyDeliveryQueryService` | `supplyDeliveryValidation` | IF-WHS-IN-08 **§3.9** 序号 **6**（**BR-WHS-IN-010**） |
| `SupplyRequestDomainService` | `flushPendingChanges` | IF-WHS-IN-08 **§3.9** 序号 **4** 后（读回 **AGREE** 行前持久化落盘；**B.2** 应用层经领域服务 **`flushPendingChanges`**，禁直调 **`SupplyRequestRepository`** / **`EntityManager` 字段注入**） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

与 **`AS-INV-WHS-001-SDD` §2.3** 同一锚点体系（**定稿 v1.0**）；下列为药库进货单（`type_enum`=`CABINET_INBOUND_RECEIPT`，**`SupplyType#getCode()`**）增量或覆盖行。

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 `type_enum`（进货单） | **`CreateInboundReceiptCommand.CreateInboundReceiptItemCommand#typeEnum`**：应用层 Mapper 对各行固定装配 **`CABINET_INBOUND_RECEIPT`**；**不向** `CreateInboundReceiptRequest` 暴露 `typeEnum` | `DM-WKF-006-SupplyRequest-供应请求.md` §4.1.1；代码枚举 **`CABINET_INBOUND_RECEIPT`** | IF-WHS-IN-04/05 |
| 单据类型 `type_enum`（可关联订货列表） | `WarehouseDocumentDetailPageQuery.typeEnum`=`CABINET_PURCHASE_ORDER`（**`SupplyType#getCode()`**） | `DM-WKF-006` §4.1.1（DM 表内序号仅供对照，**不作**对外 API 数值） | IF-WHS-IN-12 |
| 单据状态 `status_enum` | `InboundReceiptListRowDto.statusEnum`；`LoadInboundReceiptFormInitResponse.statusOptions` 元素为 **`OptionDto`** | `DM-WKF-006` §4.2 `SupplyStatus`；§3.1 `wkf_supply_request.status_enum` | **IF-WHS-IN-02** 聚合列表行；**IF-WHS-IN-12** 筛选 **`AGREE`**；**IF-WHS-IN-01** 初始化筛选项（`OptionDto.value`=`SupplyStatus.getCode()`，B.10.8.1） |
| 源/目标位置类型（落库） | **`CreateInboundReceiptCommand`** 行装配之 **`sourceTypeEnum`/`purposeTypeEnum`**（**`SupplyRequestType#getCode()`**） | `DM-WKF-006` §4.4；典型 **`CABINET`** | **HTTP 请求体不传**：**`InventoryWarehouseInboundReceiptMapper`** 据 **`sourceLocationId`/`purposeLocationId`** 经 **`LocationQueryService#requireSupplyRequestTypeForLocationId`** 按 **`adm_location.form_enum`** 推导后写入命令；对齐 **BR-SR-016** 及 **AS-INV-WHS-001-SDD** §2.3 |
| 供应类别 | `CreateInboundReceiptRequest.categoryEnum`（**String**，**`SupplyCategory#getCode()`**） | `DM-WKF-006` §4.3；默认 **`STOCK_SUPPLY`** | 默认库存供应 |
| 领域枚举 `SupplyType` | `com.zxplat.domain.share.enums.SupplyType` | `DM-WKF-006` §4.1.1 | 命令与QueryService Query 字段一律 **code 字符串** |
| 领域枚举 `SupplyStatus` | `com.zxplat.domain.share.enums.SupplyStatus` | `DM-WKF-006` §4.2 | agree/submit 门禁对照 |
| 进货单列表聚合行 | `InboundReceiptListRowDto` | **DM-WKF-006** §3.1（**`wkf_supply_request`** 行装配；金额/时刻聚合口径见 §3.3）；**`supplierName`/`creatorName`/`approverName`** 由应用层主数据查询富化 | **IF-WHS-IN-02**；**按 `bus_no` 一行**；含 **审批人** 展示字段 |
| 进货单详情明细行 | `InboundReceiptDetailLineDto` | `DM-WKF-006` §3.1 + **ProductDefinition** QueryService（**`product_name`/`total_volume`/`manufacturer`** 等） | **IF-WHS-IN-03**；列（JSON）：**`productName`**、**`specification`**、**`manufacturer`**、**`itemQuantity`**、**`unitCode`**、**`unitCodeDictText`**、**`price`**、**`totalPrice`**、**`lotNumber`**、**`traceNo`**、**`remake`**、**`startTime`/`endTime`**（效期）；**`id`/`itemTable`/`itemId`/`occurrenceTime`**；**`productCatalog`**（嵌套 **`ProductDefinitionCatalogEditorHintsDto`**，含 **`unitBands`** 四槽含 **`PACKAGE`**、**`defaultPurchaseUnitCode`**、**`purchasePrice`** 与各槽 **`unitPrice`**，见 **WHS-IN-SDD-R-011**） |
| 物品来源表 `item_table` | `InboundReceiptLineDto.itemTable` | `DM-WKF-006` §3.1 `item_table` | 与 ProductDefinition `product_table_name` 对齐 |
| 入库项目类型（初始化） | `LoadInboundReceiptFormInitResponse.inboundItemTableTypeOptions` 元素为 **`OptionDto`** | — | **仅**两档：`value` **药品定义表**（`label`：**药品**；与 **`med_medication_definition`**）、**耗材定义表**（**耗材**；与 **`adm_device_definition`**）；常量装配 |
| 计量单位 `unit_code` | `InboundReceiptLineDto.unitCode` | `DM-WKF-006` §3.1 `unit_code` | 字典展示可选 |
| 进货验收字段 | `InboundReceiptLineDto.lotNumber`、`traceNo`、`price`、`totalPrice` | `DM-WKF-006` §3.1；§6.1.2 | IF-WHS-IN-03/04/05；**`traceNo`** 长度与 DDL 见 **WHS-IN-SDD-R-008** |
| 选品分页行（扩展） | `ProductDefinitionItemDto` | **ProductDefinition** QueryService §3 **Q-PD-004**；**WHS-IN-SDD-R-005** | **IF-WHS-IN-11**；字段见 §3.12 |
| 选品列表展示价价类 | `ProductDefinitionItemDto.purchasePrice`（字段名沿用） | **`PURCHASE`**（**`WHS-IN-SDD-R-005`**）；与 **`findPurchaseItemByInstanceId`** / **`resolvePurchasePrice`** 一致 | **IF-WHS-IN-11** |
| 详情目录采购参考价价类 | `productCatalog.purchasePrice` | **`PURCHASE`**（**`WHS-IN-SDD-R-011`**） | **IF-WHS-IN-03** |
| 批号进价/批号售价 | 进货同意补录价类 | **`LOT_NUMBER_COST`**、**`LOT_NUMBER_PRICE`**（**`PriceType#getCode()`**） | **IF-WHS-IN-08**；**WHS-IN-SDD-R-006** |
| 单位槽列表（选品） | `List<ProductDefinitionCatalogUnitBandDto>`（`unitBands`） | 由 **`ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands`** 从 **ProductDefinition** QueryService装配（**采购 / 包装（`PACKAGE`）/ 拆零 / 库存** 四槽；槽语义 **`ProductDefinitionCatalogUnitBandConstants`**）；结构见 §3.12 | **IF-WHS-IN-11** |
| 目录编辑提示（详情嵌套） | `ProductDefinitionCatalogEditorHintsDto`（`productCatalog`） | **ProductDefinition** QueryService经 **`ProductDefinitionCatalogEditorHintsAssembler`**；**`purchasePrice`**/**`unitBands`[].unitPrice`** 二次装配见 **WHS-IN-SDD-R-011** | **IF-WHS-IN-03** |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向（白名单 → §3 序列） | 白名单内方法均至少在 §3 某一 IF 序列出现 |
| 反向（§3 序列 → 白名单） | §3 各 **调用** 均可在 §2.2 找到同名条目 |
| 互斥步骤对 | IF-WHS-IN-04 仅新建（无 `busNo`）；IF-WHS-IN-05 仅修订（有 `busNo`），不得串为无条件两步；IF-WHS-IN-04「带 `originalBusNo`」与「免申请」二选一，见 §3.5 编排序列 |

---

## 3. 按接口 ID 命名展开

先读完各 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-WHS-IN-01 | `loadInboundReceiptFormInit` | 加载进货页初始化 | `POST /api/app/inventoryWarehouseInboundReceipt/loadInboundReceiptFormInit` |
| IF-WHS-IN-02 | `pageInboundReceipts` | 进货单分页列表 | `POST /api/app/inventoryWarehouseInboundReceipt/pageInboundReceipts` |
| IF-WHS-IN-03 | `getInboundReceiptDetail` | 进货单详情 | `POST /api/app/inventoryWarehouseInboundReceipt/getInboundReceiptDetail` |
| IF-WHS-IN-04 | `createInboundReceipt` | 新建进货单 | `POST /api/app/inventoryWarehouseInboundReceipt/createInboundReceipt` |
| IF-WHS-IN-05 | `updateInboundReceipt` | 编辑进货单 | `POST /api/app/inventoryWarehouseInboundReceipt/updateInboundReceipt` |
| IF-WHS-IN-06 | `submitInboundReceiptApproval` | 提交审批 | `POST /api/app/inventoryWarehouseInboundReceipt/submitInboundReceiptApproval` |
| IF-WHS-IN-07 | `withdrawInboundReceiptApproval` | 撤回审批 | `POST /api/app/inventoryWarehouseInboundReceipt/withdrawInboundReceiptApproval` |
| IF-WHS-IN-08 | `agreeInboundReceipt` | 审批同意 | `POST /api/app/inventoryWarehouseInboundReceipt/agreeInboundReceipt` |
| IF-WHS-IN-09 | `rejectInboundReceipt` | 审批驳回 | `POST /api/app/inventoryWarehouseInboundReceipt/rejectInboundReceipt` |
| IF-WHS-IN-10 | `removeInboundReceipt` | 删除进货单 | `POST /api/app/inventoryWarehouseInboundReceipt/removeInboundReceipt` |
| IF-WHS-IN-11 | `pageProductsForInboundReceipt` | 采购选品检索 | `POST /api/app/inventoryWarehouseInboundReceipt/pageProductsForInboundReceipt` |
| IF-WHS-IN-12 | `pageLinkablePurchaseOrders` | 加载可关联订货单列表 | `POST /api/app/inventoryWarehouseInboundReceipt/pageLinkablePurchaseOrders` |
| IF-WHS-IN-13 | `getLinkablePurchaseOrderDetail` | 按订货单号拉取进货预填 | `POST /api/app/inventoryWarehouseInboundReceipt/getLinkablePurchaseOrderDetail` |

**HTTP 路径生成规则（硬门禁，与 ZXPlat 应用层一致）**：**§3.1 总表与本章各 IF 小节「HTTP」列**均须 **`POST /api/app/{serviceName}/{methodName}`** 形态——**动词唯一 `POST`（全大写 + 空格）**；`serviceName` 为 §2.1 所载 `*AppService` 类名去掉后缀 `AppService` 后**首字母小写**的 camelCase；`methodName` 为该 IF 对应 **`public` 应用服务方法名**，与上表「应用服务方法（英文）」列**逐字一致**（含大小写）。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁（`*QueryService` / `*QueryService`） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService#…(…Command)`，持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | `Mapper` 纯 DTO 装配 | — |

**IF-WHS-IN-08 补充**：`agreeRequest`（下表序号 **4**）成功后，**`createSupplyDelivery`（序号 7）→ 批号维定价（序号 8）→ `InventoryItem`（序号 9）→ `TraceNumberDomainService`（序号 10）** 为**同一 Cmd 阶段**内连续写库（见 §3.9），不另立阶段名；与序号 **4** 默认同一事务，失败回滚见 §5.2。

记法：`SupplyRequestQueryService#getSupplyByBusNo` 表示对注入 Bean 实例调用。

---

### 3.2 IF-WHS-IN-01 `loadInboundReceiptFormInit`（加载进货页初始化）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/loadInboundReceiptFormInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 空请求体即可；机构以 **`CurrentManagedOrganizationIdsResolver`** 会话为准。**已删除** 曾忽略的 `organizationId` 兼容字段 |

**统一类型（硬门禁）**：下文各 `*Options` 响应字段类型均为 **`java.util.List<com.zxplat.framework.core.application.dto.OptionDto>`**（下文简写 **`List<OptionDto>`**）；**禁止**在 `inboundreceipt/dto` 包内为本接口新增 `WarehouseOptionDto`、`SupplierOptionDto`、`PharmacistOptionDto`、`WarehouseKeeperOptionDto`、`InboundItemTableTypeOptionDto` 等专用下拉 DTO。

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pharmacyLocationOptions` | `List<OptionDto>` | 可选**药房**位置：`LocationQueryService#listAllActivePharmacies`（启用态、**不按机构过滤**）后经 **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`**（**管辖 empty 则不收窄**，否则有交仅辖内） |
| `warehouseLocationOptions` | `List<OptionDto>` | 可选**药库**位置：`LocationQueryService#listAllActiveWarehouses` + 同上 **`narrowActiveLocationsByManaged`**（**药房与药库分列、独立收窄**） |
| `supplierOptions` | `List<OptionDto>` | 供应商下拉 |
| `pharmacistOptions` | `List<OptionDto>` | 经手人（药师） |
| `warehouseKeeperOptions` | `List<OptionDto>` | 库管人员 |
| `inboundItemTableTypeOptions` | `List<OptionDto>` | 入库项目类型（**仅**两档，见下表） |
| `statusOptions` | `List<OptionDto>` | 单据状态 **SupplyStatus**（`value`=`getCode()` **字符串**（B.10.8.1），`label` 与 **DM-WKF-006** §4.2 展示一致） |

**`OptionDto` 字段约定（IF-WHS-IN-01）**

| 响应字段 | `value`（String） | `label` |
| --- | --- | --- |
| `pharmacyLocationOptions` | 位置 `locationId` | 位置展示名 |
| `warehouseLocationOptions` | 位置 `locationId` | 位置展示名 |
| `supplierOptions` | `supplierId` | 供应商名称 |
| `pharmacistOptions` | `practitionerId`（药师） | 姓名 |
| `warehouseKeeperOptions` | `practitionerId`（库管） | 姓名 |
| `inboundItemTableTypeOptions` | **药品定义表** / **耗材定义表**（与持久化 `item_table`、**IF-WHS-IN-11** `productTableName` 白名单 **`med_medication_definition`** / **`adm_device_definition`** 一致） | **药品** / **耗材** |
| `statusOptions` | `SupplyStatus.getCode()` | 状态展示名 |

**关键校验**：Gate：用户具备药库进货权限；会话机构可解析（**`organizationId` 请求字段不参与**位置主数据装配）。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_101` | 400/403 | 机构上下文缺失或无权限 |
| `WHS_IN_102` | 500/503 | 主数据加载失败 |

**Mapper 接口方法签名**

- `toOptionDtoListFromLocations(List<Location>): List<OptionDto>`
- `toOptionDtoListFromSuppliers(List<SupplierListItemQueryModel>): List<OptionDto>`
- `toOptionDtoListFromPractitioners(Page<Practitioner>): List<OptionDto>`（或与 **`List<Practitioner>`** 同构的另一重载）
- `toOptionDtoListInboundItemTableTypes(): List<OptionDto>`（常量两档）
- `toOptionDtoListSupplyStatuses(): List<OptionDto>`（**`SupplyStatus`** 枚举装配）
- `toLoadInboundReceiptFormInitResponse(List<OptionDto> pharmacyLocationOptions, List<OptionDto> warehouseLocationOptions, List<OptionDto> supplierOptions, List<OptionDto> pharmacistOptions, List<OptionDto> warehouseKeeperOptions, List<OptionDto> inboundItemTableTypeOptions, List<OptionDto> statusOptions): LoadInboundReceiptFormInitResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 会话机构可解析 | 非法则 **结束** |
| 2 | Pre | `LocationQueryService#listAllActivePharmacies` | — | 启用态药房全库列表（仅读） |
| 3 | Pre | `LocationQueryService#listAllActiveWarehouses` | — | 启用态药库全库列表（仅读） |
| 4 | Pre | （装配 · 下拉） | ② | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → 药房下拉；**管辖 id 为空则等同原②全量** |
| 5 | Pre | （装配 · 下拉） | ③ | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → 药库下拉 |
| 6 | Pre | `WhaleRoleLookup#findRoleIdByCode(ROLE_PHARMACIST)` → `PractitionerRoleScopeLoader#listActivePractitionersForRoleInOrganization(roleId, sessionOrgId)` | 会话机构 | 经手人（药师） |
| 7 | Pre | `WhaleRoleLookup#findRoleIdByCode(ROLE_WAREHOUSE)` → `PractitionerRoleScopeLoader#listActivePractitionersForRoleInOrganization(roleId, sessionOrgId)` | 同上 | 库管人员 |
| 8 | Pre | `SupplierQueryService#listSuppliersByTypeAndActive` | 采购/供货类型过滤（与产品字典对齐） | 仅读 |
| 9 | Asm | `InventoryWarehouseInboundReceiptMapper#toOptionDtoListSupplyStatuses` | — | **`SupplyStatus`** → `statusOptions` |
| 10 | Asm | `InventoryWarehouseInboundReceiptMapper#toOptionDtoListInboundItemTableTypes` | — | 常量两档 → `inboundItemTableTypeOptions` |
| 11 | Asm | `InventoryWarehouseInboundReceiptMapper#toLoadInboundReceiptFormInitResponse` | 步骤 4～5 与各下拉装配结果 | 仅装配 |

---

### 3.3 IF-WHS-IN-02 `pageInboundReceipts`（进货单分页列表）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/pageInboundReceipts`

**Request**（`PageInboundReceiptsRequest` 继承 **`AbstractPageQuery`**，含 **`keyword`/`startDate`/`endDate`** 等全局列表字段，**B.10.11**）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sourceLocationId` | Long | Y | 源药库；下推 **`SupplyRequestDocumentListQuery#sourceLocationId`**。**`0`** = 当前用户可见全部药库（**`InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID`**，展开为 `sourceLocationIds`）；单库时须 ∈ 门控集合 |
| `purposeLocationId` | Long | N | 可选；非空时按**目的库**收窄（下推 **`SupplyRequestDocumentListQuery#purposeLocationId`**）。**`0`** 时同 **`sourceLocationId=0`** 展开为可见药库 id 集合 |
| `statusEnum` | String | N | 可空；非空须为合法 **`SupplyStatus#getCode()`**（如 **`PENDING_APPROVAL`**），筛选 |
| `keyword` | String | N | 单据号/备注；下推 **`SupplyRequestDocumentListQuery#keyword`** |
| `applyTimeFrom` | `LocalDateTime` | N | 申请时间起；应用层映射 **`SupplyRequestDocumentListQuery#setStartDate`** |
| `applyTimeTo` | `LocalDateTime` | N | 申请时间止；应用层映射 **`SupplyRequestDocumentListQuery#setEndDate`** |
| `startDate` | `LocalDateTime` | N | 与 **`applyTimeFrom`** 二选一；**若** `applyTimeFrom` **为空**则回落为本字段（兼容 **`AbstractPageQuery`** 客户端） |
| `endDate` | `LocalDateTime` | N | 与 **`applyTimeTo`** 二选一；**若** `applyTimeTo` **为空**则回落为本字段 |
| `pageNo` | Integer | Y | ≥1；**B.10.11** |
| `pageSize` | Integer | Y | ≤100（经 **`getBoundedPageSize`** 边界对齐） |
| `typeEnum` | String | N | 可空；非空须为 **`CABINET_INBOUND_RECEIPT`**；列表路径**固定**下推 **`CABINET_INBOUND_RECEIPT`**（防窥探其它 **`SupplyType`**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | **`COUNT(DISTINCT bus_no)`** 后的单据总条数（与 **`pageNo`/`pageSize`** 对齐；**非**明细行数） |
| `pageNo` | Integer | 当前页码（1-based，与 Request 一致，B.10.11） |
| `pageSize` | Integer | 每页条数（与查询边界一致） |
| `records` | `List<InboundReceiptListRowDto>` | 每 **`bus_no` 一行** |

**DTO：InboundReceiptListRowDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |
| `supplierName` | String | N | 供应商（展示名；应用层 **`SupplierQueryService`** 富化） |
| `statusEnum` | String | Y | **`SupplyStatus#getCode()`**（与 Request 筛选同为 code 口径） |
| `creatorName` | String | N | 制单人（应用层 **`PractitionerQueryService`** 由申请人主键解析） |
| `approverId` | Long | N | 审批人主键（取 **`apply_time` 降序**下首行头字段 **`approver_id`**；同号多行头字段须一致，否则 **WHS_IN_102**） |
| `approverName` | String | N | 审批人展示名（由 **`approverId`** 解析；未审批可空） |
| `totalAmount` | `BigDecimal` | N | 总金额（该 `bus_no` 下各明细 **`total_price`** 之和，**null** 行跳过） |
| `approvalTime` | `LocalDateTime` | N | 审批时间（同号多行取 **`id` 升序**下**首条非空** **`approval_time`**，稳定口径） |
| `applyTime` | `LocalDateTime` | N | **申请时间**（同号多行取 **`apply_time` 的最大值**；草稿未提交审批前为 **null**） |
| `occurrenceTime` | `LocalDateTime` | N | **制单时间**（同号多行取 **`occurrence_time` 的最大值**；来自前端明细） |

**关键校验**

- `typeEnum` 若传则须为 **`CABINET_INBOUND_RECEIPT`**。
- `sourceLocationId`：**`0`** 表示当前用户可见全部药库（展开为 `sourceLocationIds`）；**非 0** 时须 ∈ **「当前用户可选药库」id 集合**（与 **IF-WHS-IN-01** 之 **`warehouseLocationOptions`** 及 **IF-WHS-IN-04/05/11/12** Gate 同源）。
- 聚合装配时：同一 **`bus_no`** 下 **`supplierId`**、**`statusEnum`** 须一致，否则 **WHS_IN_102**（禁止列表「假一致」）。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_102` | 400/500 | 列表查询失败 |

**Mapper 接口方法签名**

- `toInboundReceiptListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows): List<InboundReceiptListRowDto>`（输出顺序与 **`listDistinctBusNoPage`** 一致；**`approverId`/`statusEnum`** 取自 **`apply_time` 降序**头行；**`applyTime`**/**`approvalTime`**/**`occurrenceTime`**/**`totalAmount`** 聚合口径见上表）
- `toPageInboundReceiptsAggregatedResponse(long mergedTotal, int pageNo, int pageSize, List<InboundReceiptListRowDto> records): PageInboundReceiptsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 分页、`sourceLocationId`；**若** `purposeLocationId` **非空且 ≠0**则须 ∈ **「当前用户可选药库」id 集合** | 非法则 **结束** |
| 2 | Pre | （库位展开） | **`sourceLocationId`/`purposeLocationId` = 0** → **`sourceLocationIds`/`purposeLocationIds`** = 可见药库集合；无可见库 → 占位 **`-1`** 空页 | 与 **IF-WHS-PO-02**、**§10.1** 功能清单一致 |
| 3 | Gate | （集合装配 + 校验） | **`listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`** | 单库 id（非 0）须 ∈ 集合；否则拒绝 |
| 4 | Pre | `SupplyRequestQueryService#countDistinctBusNo` | **`SupplyRequestDocumentListQuery`**：`typeEnum`=`CABINET_INBOUND_RECEIPT`、`sourceLocationId`/`sourceLocationIds`、**`purposeLocationId`/`purposeLocationIds`（可空）**、`statusEnum`、`keyword`、`startDate`/`endDate`、分页 | **`total`**=去重 **`bus_no`** 条数 |
| 5 | Pre | `SupplyRequestQueryService#listDistinctBusNoPage` | 同上 | 当前页 **`bus_no`** 列表（有序） |
| 6 | Pre | `SupplyRequestQueryService#listByBusNosWithFilter` | **`SupplyRequestDocumentListQuery`** + ⑤ 之 **`busNos`** | 拉回当前页全部明细行（**`SupplyRequestModel`**） |
| 7 | Asm | `InventoryWarehouseInboundReceiptMapper#toInboundReceiptListAggregated` | ⑤⑥ | 每 **`bus_no` 一行** |
| 8 | Asm | （应用层）`enrichInboundReceiptListDisplayNames` | 步骤 6 明细、步骤 7 聚合行 | **`supplierName`/`creatorName`/`approverName`** 批量主数据富化 |
| 9 | Asm | `InventoryWarehouseInboundReceiptMapper#toPageInboundReceiptsAggregatedResponse` | **`mergedTotal`**、**`pageNo`/`pageSize`**、⑧ | 装配 **`PageInboundReceiptsResponse`** |

> **`SupplyRequestDocumentListQuery`**（`queries` 包）：继承 **`AbstractPageQuery`**；含 **`typeEnum`**、**`sourceLocationId`**、**`purposeLocationId`（可选）**、**`statusEnum`**、**`keyword`**、**`tenantId`（可选）**、**`pageNo`/`pageSize`**、**`startDate`/`endDate`**（申请时刻窗）；分页索引在领域查询内按 **`springPageIndex`** 与 **`getBoundedPageSize`** 处理（**B.10.11**）。

---

### 3.4 IF-WHS-IN-03 `getInboundReceiptDetail`（进货单详情）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/getInboundReceiptDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 进货单号 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 单据号 |
| `originalBusNo` | String | 来源订货单 |
| `typeEnum` | String | Y | 恒为 **`CABINET_INBOUND_RECEIPT`**（**`SupplyType#getCode()`**） |
| `statusEnum` | String | Y | **`SupplyStatus#getCode()`** |
| `supplierId` | Long | N | 供应商 |
| `sourceLocationId` | Long | Y | 源位置 |
| `purposeLocationId` | Long | Y | 目标位置 |
| `sourceTypeEnum` | String | N | 读侧回显 **`SupplyRequestType#getCode()`**；无源库位时 **null** |
| `purposeTypeEnum` | String | Y | 读侧回显 **`SupplyRequestType#getCode()`** |
| `categoryEnum` | String | Y | **`SupplyCategory#getCode()`** |
| `invoiceNo` | String | N | 采购发票号（单头共享，落 **`wkf_supply_request.invoice_no`**；同号多行一致；读侧与 **`busNo`** 一样取任一行；进销存 **`purc_invo_no`**；空则落库/上报占位 `0`）。**契约必填=N**；**前端不必填** |
| `rowVersion` | Integer | N | 乐观锁；与 **§3.6** **`UpdateInboundReceiptRequest.rowVersion`** 及领域 **`row_version`** 一致 |
| `lines` | `List<InboundReceiptDetailLineDto>` | Y | 明细 |

**DTO：InboundReceiptDetailLineDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | Y | 供应请求行主键 |
| `itemTable` | String | Y | 物品来源表名（同源 **`item_table`**） |
| `itemId` | Long | Y | 物品定义主键 |
| `itemQuantity` | `BigDecimal` | Y | 数量（同源 **`item_quantity`**） |
| `unitCode` | String | Y | 计量单位编码（字典码） |
| `lotNumber` | String | Y | 批号（**必填**；**IF-WHS-IN-08** 同意路径在批号维定价与库存入账前整单校验，去空白后为空则 **WHS_IN_104**） |
| `traceNo` | String | N | 追溯码（同源 **`wkf_supply_request.trace_no`**；可空；**最大 50000 字符**，**WHS-IN-SDD-R-008**） |
| `price` | `BigDecimal` | N | 单价（同源 **`price`**） |
| `totalPrice` | `BigDecimal` | N | 行总价（同源 **`total_price`**） |
| `occurrenceTime` | `LocalDateTime` | Y | 制单时刻（**WHS-IN-SDD-R-013**） |
| `remake` | String | N | 行备注（同源 **`remake`**） |
| `productName` | String | N | 物品名称（**ProductDefinition** QueryService **`product_name`**） |
| `specification` | String | N | 规格（QueryService **`total_volume`**） |
| `manufacturer` | String | N | 生产厂家及产地合一（QueryService **`manufacturer`**） |
| `startTime` | `LocalDateTime` | N | 效期起点（**`wkf_supply_request.start_time`**） |
| `endTime` | `LocalDateTime` | N | 效期止点（**`wkf_supply_request.end_time`**） |
| `unitCodeDictText` | String | N | **`unitCode`** 对齐产品QueryService单位槽时之展示名 |
| `productCatalog` | `ProductDefinitionCatalogEditorHintsDto` | N | 多单位目录提示；无QueryService命中时为 `null`；**`purchasePrice`** 与各槽 **`unitPrice`** 须按 **WHS-IN-SDD-R-011** 二次装配 |

> **效期**：行级 **`start_time`/`end_time`** 已契约输出为 **`startTime`/`endTime`**；历史「不输出效期」表述废止。

**关键校验**：全行 `type_enum` 对应 **`CABINET_INBOUND_RECEIPT`**；否则失败。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_103` | 404 | 不存在或非 **`CABINET_INBOUND_RECEIPT`** 单据 |

**Mapper 接口方法签名**

- `toGetInboundReceiptDetailResponse(List<SupplyRequestModel>, Map<String, ProductDefinitionDisplayDto>): GetInboundReceiptDetailResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` 非空 | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | **若**任一行 `typeEnum` ≠ **`CABINET_INBOUND_RECEIPT`** → **失败（WHS_IN_103）**；否则继续 |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 提取 `itemId` + `itemTable` | **可选**；失败可降级 |
| 4 | Asm | `InventoryWarehouseInboundReceiptMapper#toGetInboundReceiptDetailResponse` | ②③ | 装配写模型行 + QueryService展示列（含 **`productCatalog`** 初装） |
| 5 | Asm | **`InventoryWarehouseInboundReceiptAppService`**（私有 **`enrichInboundReceiptDetailLines`** 等价语义） | ④ **`lines`** | **WHS-IN-SDD-R-011**：**`ProductDefinitionPurchasePriceResolver#resolvePurchasePrice`** + **`ProductDefinitionCatalogUnitBandsAssembler`**（**`purchaseUnitRatioToSplit`** 包装口径换算为主） |

---

### 3.5 IF-WHS-IN-04 `createInboundReceipt`（新建进货单）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/createInboundReceipt`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `originalBusNo` | String | N | 来源订货 `bus_no`；空=免申请 |
| `sourceLocationId` | Long | Y | 药库 |
| `purposeLocationId` | Long | Y | 入库去向 |
| `categoryEnum` | String | N | 默认 **`STOCK_SUPPLY`**（**`SupplyCategory#getCode()`**）；可缺省或 blank 时由 Mapper 默认 |
| `supplierId` | Long | N | 供应商 |
| `practitionerId` | Long | Y | 经手人（医务人员主键；落 **`wkf_supply_request.practitioner_id`**） |
| `invoiceNo` | String | N | 采购发票号（单头填写；Mapper 写入命令各行 **`invoiceNo`**，同号多行共享）。**契约必填=N**；**前端不必填**；空→落库/进销存占位 `0` |
| `lines` | `List<InboundReceiptLineDto>` | Y | ≥1 行 |

**DTO：InboundReceiptLineDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemTable` | String | Y | 物品来源表 |
| `itemId` | Long | Y | 物品定义 ID |
| `itemQuantity` | `BigDecimal` | Y | >0 |
| `unitCode` | String | Y | 单位编码 |
| `lotNumber` | String | Y | 批号（**必填**；**IF-WHS-IN-08** 同意路径在批号维定价与库存入账前整单校验，去空白后为空则 **WHS_IN_104**） |
| `traceNo` | String | N | 追溯码（落 **`wkf_supply_request.trace_no`**；**最大 50000 字符**，**WHS-IN-SDD-R-008**） |
| `price` | `BigDecimal` | N | 单价 |
| `totalPrice` | `BigDecimal` | N | 总价 |
| `occurrenceTime` | `LocalDateTime` | Y | 制单/业务时刻（落 **`occurrence_time`**） |
| `remake` | String | N | 行备注 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 新单号 |
| `statusEnum` | String | 创建完成后的 **`SupplyStatus#getCode()`**（典型 **`PENDING_APPROVAL`**） |

**关键校验**：**BR-WHS-IN-001～003**；**源/目的药库**须 ∈ **「当前用户可选药库」id 集合**（与 **IF-WHS-IN-01** **`warehouseLocationOptions`** 同源收窄规则）；供应商存在性（若填）；各行 **`traceNo`** 非空时长度 **≤50000**（**WHS-IN-SDD-R-008**）。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_001` | 400 | 建单/复制校验失败 |
| `WHS_IN_002` | 400 | 订货单不可引用 |

**Mapper 接口方法签名**

- `toCreateInboundReceiptCommand(CreateInboundReceiptRequest, Long operatorId, Long applicantId): CreateInboundReceiptCommand`
- `toCreateInboundReceiptCommandForUpdate(UpdateInboundReceiptRequest, Long operatorId, Long applicantId, String originalBusNoSnapshot): CreateInboundReceiptCommand`
- `toCreateInboundReceiptResponse(String busNo, SupplyStatus status): CreateInboundReceiptResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `lines` 非空、数量合法、`organizationId`/`sourceLocationId`/`purposeLocationId` 可解析 | 非法则 **结束** |
| 2 | Gate | （集合装配 + 校验） | **`listAllActiveWarehouses`** + **`gatedLocationIdsFromActive`**（同源 **IF-WHS-IN-02** Gate） | **可选药库 id 集合**；`sourceLocationId` 与 `purposeLocationId` 须 ∈ 集合；否则 **失败** |
| 3 | Pre | `SupplierQueryService#findSupplierById` | `supplierId` | **若** `supplierId==null` **则跳过本步骤**；否则不存在 → **失败** |
| 4 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` + **`#existsUndeletedSupplyByOriginalBusNo`** | `originalBusNo` | **仅当** `originalBusNo` **非空**执行：校验订货类型 **CABINET_PURCHASE_ORDER**、状态 **AGREE**、**DM** §6.1.2 拷贝语义；且 **不存在**其它未软删 **`CABINET_INBOUND_RECEIPT`** 以 **`original_bus_no`** 引用该订货（**BR-WHS-IN-012** / **WHS-IN-SDD-R-014**）；失败 → **WHS_IN_002**。**若** `originalBusNo` **为空** → **跳过本步骤**，直接进入步骤 5 |
| 5 | Cmd | `SupplyRequestDomainService#createInboundReceipt(CreateInboundReceiptCommand)` | Mapper 装配命令：各行 **`typeEnum`=`CABINET_INBOUND_RECEIPT`**、`originalBusNo`；**`applicantId`** 取会话医务人员；**`practitionerId`** 取请求头；各行 **`applyTime`=`null`**（**WHS-IN-SDD-R-013**） | **写库点①**：插入 `wkf_supply_request`（新建路径领域层二次 **`assertPurchaseOrderNotReferencedByOtherInboundReceipt`**）；`SupplyRequestCreatedEvent` |
| 6 | Asm | `InventoryWarehouseInboundReceiptMapper#toCreateInboundReceiptResponse` | ⑤ 返回 | |

---

### 3.6 IF-WHS-IN-05 `updateInboundReceipt`（编辑进货单）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/updateInboundReceipt`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 既有进货单 |
| `sourceLocationId` | Long | Y | 药库 |
| `purposeLocationId` | Long | Y | 去向 |
| `categoryEnum` | String | N | 默认 **`STOCK_SUPPLY`**；可缺省或 blank 时由 Mapper 默认 |
| `supplierId` | Long | N | |
| `practitionerId` | Long | Y | 经手人（与 **§3.5** 同口径） |
| `invoiceNo` | String | N | 采购发票号（与 **§3.5** 同口径；前端不必填；空→占位 `0`） |
| `lines` | `List<InboundReceiptLineDto>` | Y | 全量替换集合 |
| `rowVersion` | Integer | N | 乐观锁；与 **§3.4** **`GetInboundReceiptDetailResponse.rowVersion`** 及领域 **`row_version`** 一致 |

**（v2.11 口径）**：**IF-WHS-IN-04** 新建、**IF-WHS-IN-10** 删除之请求体与编排序列**保持 v2.10**；本版仅对 **IF-WHS-IN-05** 做 **`rowVersion`** 类型与详情对齐。

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 与入参相同 |
| `statusEnum` | String | 仍为可编辑态（**`SupplyStatus#getCode()`**） |

**关键校验**：**BR-WHS-IN-005**；**源/目的药库**须 ∈ **「当前用户可选药库」id 集合**（与 **IF-WHS-IN-04** Gate 同源）；`verifyAbleEdit`；各行 **`traceNo`** 非空时长度 **≤50000**（**WHS-IN-SDD-R-008**）。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_004` | 409 | 禁止编辑 |
| `WHS_IN_105` | 409 | 并发冲突 |

**Mapper 接口方法签名**

- `toCreateInboundReceiptCommandForUpdate(UpdateInboundReceiptRequest, Long operatorId, Long applicantId, String originalBusNoSnapshot): CreateInboundReceiptCommand`
- `toUpdateInboundReceiptResponse(String busNo, SupplyStatus status): UpdateInboundReceiptResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo`、`lines` 非空 | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | **若**类型非 **`CABINET_INBOUND_RECEIPT`** → **失败** |
| 3 | Pre | `SupplyRequestQueryService#verifyAbleEdit` | `busNo` + 代表性 `itemId` | **若**不可编辑 → **失败（禁止 Cmd）** |
| 4 | Pre | `SupplierQueryService#findSupplierById` | `supplierId` | **若** `supplierId==null` **则跳过** |
| 5 | Cmd | `SupplyRequestDomainService#createInboundReceipt(CreateInboundReceiptCommand)` | **命令携带既有 `busNo`**（**WHS-IN-SDD-R-002**） | **写库点①** |
| 6 | Asm | `InventoryWarehouseInboundReceiptMapper#toUpdateInboundReceiptResponse` | ⑤ | |

---

### 3.7 IF-WHS-IN-06 `submitInboundReceiptApproval`（提交审批）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/submitInboundReceiptApproval`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |
| ~~`applyClock`~~ | `LocalDateTime` | N | **已废弃**：服务端在 **`submitApproval`** 内统一写 **`apply_time`**，请求体若仍带该字段 **忽略**（**WHS-IN-SDD-R-013**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |
| `statusEnum` | String | 提交后 **`APPROVAL`**（**`SupplyStatus#getCode()`**） |

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_003` | 409 | 状态不允许 |

**Mapper 接口方法签名**

- `toSubmitApprovalCommand(SubmitInboundReceiptApprovalRequest, Long operatorId, Long applicantId): SubmitApprovalCommand`
- `toSubmitInboundReceiptApprovalResponse(boolean, SupplyStatus): SubmitInboundReceiptApprovalResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | 确认 `typeEnum`=`CABINET_INBOUND_RECEIPT` |
| 3 | Cmd | `SupplyRequestDomainService#submitApproval(SubmitApprovalCommand)` | Mapper 装配（**`SubmitApprovalItemCommand.applicantId`**=会话医务人员；**不得**依赖请求体时钟） | **写库点①**；`SupplyRequestSubmittedEvent` |
| 4 | Asm | `InventoryWarehouseInboundReceiptMapper#toSubmitInboundReceiptApprovalResponse` | ③ | |

---

### 3.8 IF-WHS-IN-07 `withdrawInboundReceiptApproval`（撤回审批）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/withdrawInboundReceiptApproval`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |
| `statusEnum` | String | 撤回后 **`WITHDRAW`**（**`SupplyStatus#getCode()`**；以领域为准） |

**失败码口径**：同 `WHS_IN_003`

**Mapper 接口方法签名**

- `toWithdrawApprovalCommand(WithdrawInboundReceiptApprovalRequest): WithdrawApprovalCommand`
- `toWithdrawInboundReceiptApprovalResponse(boolean, SupplyStatus): WithdrawInboundReceiptApprovalResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | |
| 3 | Cmd | `SupplyRequestDomainService#withdrawApproval(WithdrawApprovalCommand)` | | **写库点①**；`SupplyRequestWithdrawnEvent` |
| 4 | Asm | `InventoryWarehouseInboundReceiptMapper#toWithdrawInboundReceiptApprovalResponse` | | |

---

### 3.9 IF-WHS-IN-08 `agreeInboundReceipt`（审批同意）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/agreeInboundReceipt`

**审批通过逻辑（业务，对齐 AS §4 IF-WHS-IN-08；编排锚点对齐遗留入库审批通过主路径，不含医保上传）**

1. **门禁**：单据须为 **`CABINET_INBOUND_RECEIPT`** 且处于 **`APPROVAL`**；通过 **`supplyRequestValidation`** 防「头表已 **同意**」类重复提交。  
2. **领域写库（见下表序号 4）**：**`agreeRequest`** 将整单状态落 **`AGREE`**，并写入 **`approver_id` / `approval_time`**（见 **DM-WKF-006** §3.1），发布 **`SupplyRequestAgreedEvent`**；随后调用 **`SupplyRequestDomainService#flushPendingChanges`**（**B.2**：应用层**禁止**注入 **`EntityManager`**）确保后续读回 **AGREE** 行与持久化主键可用。  
3. **发放前校验（见下表序号 6）**：对同意后再读回的**每一供应请求行主键**调用 **`SupplyDeliveryQueryService#supplyDeliveryValidation`**（或 **等价批量** **`#findRequestIdsHavingCompletedDelivery`**，与 **`hasCompletedDelivery`** 语义一致且满足 **A.9.1** 禁循环逐条 DB I/O）；**若**任一行 **`hasCompletedDelivery`** 为 **true** → **WHS_IN_104**（对齐遗留 **supplyDeliveryValidation** / **请勿重复审批**，**BR-WHS-IN-010**）。  
4. **执行编排（见下表序号 7～10，与序号 4 默认同一事务）**：  
   - **序号 7**：**`createSupplyDelivery`**（入库发放，逐行或批量命令以 **Mapper** 装配为准）。  
   - **序号 8**：**批号维定价 + 目录参考价**（**WHS-IN-SDD-R-006** / **R-018** / **R-019**）：同批号进价同值跳过批号写；异价先停用旧 ACTIVE 再 **`addLotPurchasePrice`/`addLotSalePrice`**；**无论批号是否跳过**，经 **`CatalogReferencePriceSyncSupport`** 以本行包装进价回写 **`PURCHASE`/`SALE`**。  
   - **序号 9**：**库存拆零入账**（**WHS-IN-SDD-R-007**；**`createInventoryItem`** 新建路径 **`productionDate`/`expirationDate`/`categoryCode`/`pyStr`/`wbStr`** 见 **WHS-IN-SDD-R-012**）：**`loadAsMap`**（取 **`splitUnitCode`** 及 **`ProductDefinitionDisplayDto`** 冗余装配）→ **`SupplyPersistedSplitQuantity#requireSupplyLineItemQuantityAsPersistedSplitUnit`** → 五元组命中 **`findFirstIdByInventoryMatchKey`** → **`increaseQuantity`** / **`createInventoryItem`**。  
   - **序号 10**：**追溯入库轨迹**（**WHS-IN-SDD-R-009** / **BR-WHS-IN-011**）：对 **`trace_no`/`traceNo` 非空白** 行调用 **`TraceNumberDomainService#recordTraceRecords`** 或 **`#recordTraceRecordsFromLineItems`**，写入 **`cus_trace_number` + `cus_trace_number_flow`**；**空白行跳过**。  
   **任一步失败**则默认**整事务回滚**（对齐 **WHS-IN-SDD-R-004** 与 **WHS_IN_104**）。  
5. **对外可见性**：成功路径下，列表/详情应能观察到 **审批人**、**审批时间**、**批号维定价**、**库存增加**及**非空追溯码之轨迹可查**（QueryService异步容忍须在 **§7.3** 断言）。  

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |
| ~~`approverId`~~ | Long | — | **已删除**：Mapper 固定取 **`operatorId`** 写入 **`AgreeRequestItemCommand.approverId`**（**WHS-IN-SDD-R-013**） |
| ~~`approvalClock`~~ | `LocalDateTime` | — | **已删除**：领域 **`agreeRequest`** 内统一写 **`approval_time`** |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |

**关键校验**：单据状态须为 **`APPROVAL`**（**`SupplyStatus#getCode()`**）；**supplyRequestValidation** 防重复同意；单据类型须为 **`CABINET_INBOUND_RECEIPT`**；**supplyDeliveryValidation**（**BR-WHS-IN-010**）；**下表序号 7～10**（发放/批号维定价/拆零库存/**追溯写库**）失败口径见 **WHS_IN_104**；**BR-WHS-IN-011**。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_003` | 409 | 状态不允许 |
| `WHS_IN_104` | 409 | 重复同意、**重复发放**、发放/批号维定价/供应行拆零库存入账/**追溯写库**失败 |

**Mapper 接口方法签名**

- `toAgreeRequestCommand(AgreeInboundReceiptRequest request, Long operatorId): AgreeRequestCommand`
- `toCreateSupplyDeliveryCommand(SupplyRequestModel line, Long purposeLocationId): CreateSupplyDeliveryCommand`
- `toCreateInventoryItemCommand(SupplyRequestModel line, Long purposeLocationId, String resolvedProductName, String resolvedLotNumber, BigDecimal quantity, String unitCode, Long operatorId, ProductDefinitionDisplayDto productDefinition): CreateInventoryItemCommand`
- `toIncreaseQuantityCommand(Long inventoryItemId, BigDecimal increaseQty, Long operatorId): IncreaseQuantityCommand`
- `torecordTraceRecordsCommand(…): recordTraceRecordsCommand` **或** `torecordTraceRecordsFromLineItemsCommand(…): recordTraceRecordsFromLineItemsCommand`（**以 `DM-CUS-010-TraceNumberRecord-追溯码记录-SDD.md` §5.1 已投产签名为准；与 `recordTraceRecords*` **二选一**）
- `toAgreeInboundReceiptResponse(boolean): AgreeInboundReceiptResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | **若**非 **`CABINET_INBOUND_RECEIPT`** → **失败** |
| 3 | Pre | `SupplyRequestQueryService#supplyRequestValidation` | `busNo` | **若**已同意 → **WHS_IN_104** |
| 4 | Cmd | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` | Mapper 装配 | **写库点①**；`SupplyRequestAgreedEvent`；成功后立即 **`SupplyRequestDomainService#flushPendingChanges`**（**B.2**，禁 **`EntityManager`**） |
| 5 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | **再读**为 **`AGREE`** 后的全量明细行（与 **§3.9** 步骤 **4** 之 **`flush`** 配套）；**若**行状态非 **`AGREE`** 或缺主键 → **WHS_IN_104** |
| 6 | Pre | `SupplyDeliveryQueryService`（**单条** **`#supplyDeliveryValidation`** 或 **等价批量** **`#findRequestIdsHavingCompletedDelivery`**） | **步骤 5 各行** **`SupplyRequestModel#getId()`** | **若**任一行 **`hasCompletedDelivery`** 为 true（或批量结果非空）→ **WHS_IN_104**（**BR-WHS-IN-010**）；批量路径满足 **A.9.1** 禁循环逐条 DB I/O |
| 7 | Cmd | `SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)` | **对步骤 5 的每一行**装配命令（`requestId`=行 ID，含 `lotNumber` 等） | **写库点②**；**若**失败 → **整事务回滚** / **WHS_IN_104** |
| 8 | Cmd | `ChargeItemDefinitionDomainService#addLotPurchasePrice` / `#addLotSalePrice`；`CatalogReferencePriceSyncSupport#syncPurchaseAndSaleIfChanged` | **逐行**：**WHS-IN-SDD-R-006** / **R-019** | **写库点③**（定价聚合表，以 **DM** 为准） |
| 9 | Cmd | `InventoryItemDomainService#createInventoryItem` **或** `#increaseQuantity` | **逐行**：**`InventoryItemQueryService#findFirstIdByInventoryMatchKey`** 分支；数量/单位为拆零口径（**WHS-IN-SDD-R-007**）；**`createInventoryItem`** 时 **`CreateInventoryItemCommand`** 须含 **WHS-IN-SDD-R-012** 所列冗余列（**`InventoryWarehouseInboundReceiptMapper#toCreateInventoryItemCommand`**） | **写库点④** |
| 9a | Pre | **`assertInboundReceiptTraceCodesNotDuplicatedAcrossLines`** + **`assertInboundTraceCodeCountMatchesSplitQuantity`**（**WHS-IN-SDD-R-016**） | **步骤 5 全量行**（仅非空 **`trace_no`** 参与码数校验） | **BR-WHS-IN-013/014**；失败 → **WHS_IN_104** |
| 10 | Cmd | **`TraceNumberRecordOrchestrationSupport#recordFromSupplyLine`** → **`TraceNumberDomainService#recordTraceRecordsFromLineItems`** | **步骤 5 全量行**；仅 `trace_no` / `traceNo` **去空白后非空**之行参与；**WHS-IN-SDD-R-015** | **写库点⑤** → **`cus_trace_number` + `cus_trace_number_flow`**；失败 → **WHS_IN_104** |
| 11 | Asm | `InventoryWarehouseInboundReceiptMapper#toAgreeInboundReceiptResponse` | | |

---

### 3.10 IF-WHS-IN-09 `rejectInboundReceipt`（审批驳回）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/rejectInboundReceipt`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | |
| ~~`approverId`~~ | Long | — | **已删除**：Mapper 固定取 **`operatorId`**（**WHS-IN-SDD-R-013**） |
| ~~`approvalClock`~~ | `LocalDateTime` | — | **已删除** |
| `rejectReason` | String | Y | 驳回原因 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | |

**失败码口径**：`WHS_IN_003`

**Mapper 接口方法签名**

- `toRejectRequestCommand(RejectInboundReceiptRequest): RejectRequestCommand`
- `toRejectInboundReceiptResponse(boolean): RejectInboundReceiptResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `rejectReason` 非空 | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | |
| 3 | Cmd | `SupplyRequestDomainService#rejectRequest(RejectRequestCommand)` | | **写库点①**；`SupplyRequestRejectedEvent` |
| 4 | Asm | `InventoryWarehouseInboundReceiptMapper#toRejectInboundReceiptResponse` | | |

---

### 3.11 IF-WHS-IN-10 `removeInboundReceipt`（删除进货单）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/removeInboundReceipt`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | |

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_IN_005` | 409 | 删除被拒绝 |

**Mapper 接口方法签名**

- `toRemoveByBusNoCommand(RemoveInboundReceiptRequest): RemoveByBusNoCommand`
- `toRemoveInboundReceiptResponse(boolean): RemoveInboundReceiptResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | |
| 3 | Cmd | `SupplyRequestDomainService#removeByBusNo(RemoveByBusNoCommand)` | | **写库点①**；`saveAll` 前领域内逐行 **`SupplyRequest#assertRemovableForSoftDelete`**（**BR-SR-011**：`PENDING_APPROVAL` / `REJECT` / `WITHDRAW`）；`SupplyRequestDeletedEvent` |
| 4 | Asm | `InventoryWarehouseInboundReceiptMapper#toRemoveInboundReceiptResponse` | | |

---

### 3.12 IF-WHS-IN-11 `pageProductsForInboundReceipt`（采购选品检索）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/pageProductsForInboundReceipt`

**契约要点（对齐 AS §4）**：本接口服务**手动目录检索**分页；**从订货单拉取明细**由 **IF-WHS-IN-03/04** 与 **`SupplyRequestQueryService#getSupplyByBusNo`** 等路径承载，**不要求**与本 HTTP 合并，但**列字段**应与 **`ProductDefinitionItemDto`** 扩展一致以便前端同构（见 **AS** §2 **IF-WHS-IN-11**）。

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productTableName` | String | N | `med_medication_definition` / `adm_device_definition` / 空=全部；**非空**时须为白名单之一 |
| `purposeLocationId` | Long | Y | **目的药库** `location_id`；**Gate**：须 ∈ **「当前用户可选药库」id 集合**（与 **IF-WHS-IN-01** **`warehouseLocationOptions`** / **IF-WHS-IN-04/05** 同源：**`listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**；**管辖 empty 则门控为全库启用态药库 id**）；与进货单头 **`purpose_location_id`** 语义对齐；**不**再用于选品列表库存汇总 |
| `keyword` | String | N | |
| `categoryCode` | String | N | |
| `activeFlag` | Boolean | N | 默认 true |
| `supplierId` | Long | N | |
| `manufacturer` | String | N | 检索条件；**返回行**之厂家展示取自QueryService **`manufacturer`** 列，二者不必相同 |
| `includeInventory` | Boolean | N | 与 **ProductDefinitionPageQuery** 对齐；**仅**影响QueryService WHERE；**不**改变本接口「不汇总 **`availableQuantity`**」契约 |
| `pageNo` | Integer | Y | 继承 **`AbstractPageQuery`**（B.10.11） |
| `pageSize` | Integer | Y | |
| `sortField` | String | N | 预留；采购选品路径可不参与 WHERE |
| `sortOrder` | String | N | 预留 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | |
| `records` | `List<ProductDefinitionItemDto>` | 选品扩展行，字段见下表 |

**DTO：`ProductDefinitionItemDto`（选品扩展）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productDefinitionId` | Long | Y | 与 **`item_id`** 语义一致（药品/耗材定义主键） |
| `productTableName` | String | Y | 与 **`item_table`** / QueryService **`product_table_name`** 一致 |
| `productBusNo` | String | N | 业务编号（QueryService **`product_bus_no`** 或等价投影，以 **ProductDefinition** QueryService为准） |
| `productName` | String | Y | |
| `merchandiseName` | String | N | |
| `totalVolume` | String | N | 规格/包装规格（QueryService **`total_volume`**） |
| `purchaseSplitRatioText` | String | N | **采购最小单位换算比**展示（服务端拼装）：形如 **`1箱/3桶`**（1 采购单位 / ratio 拆零单位）；同单位且倍数为 1 时为 **`1个`**；与库存明细 **`packageSplitRatioText`** 同族算法（**`MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText`**），单位取 **`unitBands`** 采购槽 / 拆零槽；缺采购倍数时 **null** |
| `manufacturer` | String | N | **生产厂家及产地**合一展示（QueryService **`manufacturer`** 列；不在QueryService拆独立产地列时，厂商与产地语义均由该列承载；可空） |
| `unitCode` | String | Y | 展示默认单位（与QueryService采购/展示单位列一致，见 **ProductDefinition** QueryService） |
| `minUnitCode` | String | N | 拆零单位 |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | N | 四槽；**`unitPrice`** 仅以目录 **`PURCHASE`（每包装）** 为锚经 **`#applyInboundWarehouseBandPrices`** 写各槽 |
| `categoryCode` | String | N | 物资分类编码（字典持久化码） |
| `categoryCodeDictText` | String | N | 物资分类展示名（QueryService **`category_code_dict_text`**；选品下拉「项目种类」等） |
| `price` | `BigDecimal` | N | 基础售价等QueryService投影（若有），**非**本接口行展示价真源 |
| `purchasePrice` | `BigDecimal` | N | 定价表 **`PURCHASE`**（**每包装单位进价**）；无命中 **null** |
| `unitPrice` | `BigDecimal` | N | 各槽由包装锚线性装配（**WHS-IN-SDD-R-011**）；否则 **null** |
| `availableQuantity` | `BigDecimal` | N | **进货选品不装配**：恒 **`null`**（字段保留兼容 JSON）；**禁止**前端将本列当作目的库可用量真源 |
| `pyStr` | String | N | |
| `wbStr` | String | N | |

**DTO：`ProductDefinitionCatalogUnitBandDto`（嵌套；与 IF-DOR-11 目录单位槽 JSON 字段同构，进货选品为子集）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bandKind` | String | Y | 槽语义：进货选品为 **`PURCHASE`** / **`PACKAGE`** / **`SPLIT`** / **`STOCK`**（与 **`ProductDefinitionCatalogUnitBandConstants`** 中 **`BAND_PURCHASE`/`BAND_PACKAGE`/`BAND_SPLIT`/`BAND_STOCK`** 的字符串值一致） |
| `unitCode` | String | Y | 单位编码（字典持久化码） |
| `unitName` | String | N | 单位展示名 |
| `ratioToSplit` | `BigDecimal` | Y | 相对拆零倍数；拆零槽恒为 **1** |
| `unitPrice` | `BigDecimal` | N | 四槽均由包装锚线性装配（**`P_pkg × R_slot / R_pkg`**，**WHS-IN-SDD-R-011**）；否则 **null** |

**失败码口径**：`WHS_IN_102`

**Mapper 接口方法签名**

- `toProductDefinitionPageQuery(PageProductsForInboundReceiptRequest): ProductDefinitionPageQuery`
- `toPageProductsForInboundReceiptResponse(Page<ProductDefinitionDisplayDto>): PageProductsForInboundReceiptResponse`（可拆分为 **toProductDefinitionItemDto** + **`enrichInboundProductSelectionRows`** 私有步骤，实现自定；**`enrichInboundProductSelectionRows`** 不再接收库位参数）

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `pageNo`/`pageSize`；**`purposeLocationId`** ∈ **`gatedLocationIdsFromActive(listAllActiveWarehouses)`**（与 **IF-WHS-IN-02** Gate 同源）；**`productTableName`** 白名单 | |
| 2 | Pre | `MedicationDefinitionQueryService#pageForPurchase / DeviceDefinitionQueryService#pageForPurchase(ProductDefinitionPageQuery)` | Mapper 装配 | **仅读路径** **Q-PD-004** |
| 3 | Asm | `InventoryWarehouseInboundReceiptMapper#toPageProductsForInboundReceiptResponse`（内联 **`ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands`**；应用层 **`enrichInboundProductSelectionRows`** 仅编排 **`ProductDefinitionPurchasePriceResolver#resolvePurchasePrice`**） | ② 之行 | **展示价**：**`PURCHASE`**（**WHS-IN-SDD-R-005**）；**不**汇总库存 |

---

### 3.13 IF-WHS-IN-12 `pageLinkablePurchaseOrders`（加载可关联订货单列表）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/pageLinkablePurchaseOrders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 订货目的药库；映射QueryService `purpose_location_id`（与 **IF-WHS-PO-02** / 订货写库一致；不得以 `source_location_id` 筛选） |
| `keyword` | String | N | 订货单号等 → `WarehouseDocumentDetailPageQuery.keyword` |
| `pageNo` | Integer | Y | 继承 **`AbstractPageQuery`**（B.10.11） |
| `pageSize` | Integer | Y | |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | |
| `pageNo` | Integer | 当前页码（1-based） |
| `pageSize` | Integer | 每页条数 |
| `records` | `List<WarehouseDocumentDetailRowDto>` | **按 `bus_no` 聚合一行一单**（与 **IF-WHS-PO-02** 同构）；行内 `typeEnum`=`CABINET_PURCHASE_ORDER`、状态等为 **`getCode()` 字符串**；含 **`supplierName`**（应用层批量富化） |

**关键校验**：Gate：`purposeLocationId` ∈ **「当前用户可选药库」id 集合**（与 **IF-WHS-IN-01** **`warehouseLocationOptions`** 同源）；**排除已被未软删 **`CABINET_INBOUND_RECEIPT`**（**`original_bus_no`** 指向该订货 **`bus_no`**）引用的订货单（**BR-WHS-IN-012** / **WHS-IN-SDD-R-014**；应用层 **`filterAlreadyReferencedPurchaseOrdersFromLinkablePage`** 内存过滤，与 **IF-WHS-SO-12** 同构）。

**失败码口径**：`WHS_IN_102`

**Mapper 接口方法签名**

- `toLinkablePurchaseOrderRows(List<PurchaseOrderListRowDto>, List<SupplyRequestModel>): List<WarehouseDocumentDetailRowDto>`
- `toPageLinkablePurchaseOrdersAggregatedResponse(long mergedTotal, int pageNo, int pageSize, List<WarehouseDocumentDetailRowDto>): PageLinkablePurchaseOrdersResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `purposeLocationId`、分页 | |
| 2 | Gate | （集合装配 + 校验） | **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(listAllActiveWarehouses)`** | **可选药库 id 集合** 与 **IF-WHS-IN-02** Gate 同源；**若** `purposeLocationId` ∉ 集合 → **失败** |
| 3 | Cmd | `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter` | **`SupplyRequestDocumentListQuery`**：`typeEnum`=`CABINET_PURCHASE_ORDER`、`statusEnum`=`AGREE`、`purposeLocationId`、`keyword`、分页 | **按 busNo 去重分页**（**WHS-PO-SDD-R-001** 同构） |
| 4 | Pre | `InventoryWarehousePurchaseOrderMapper#toPurchaseOrderListAggregated` + `enrichLinkablePurchaseOrderDisplayNames` | ③ 明细行 | 聚合行 + 展示名富化 |
| 5 | Pre | `filterAlreadyReferencedPurchaseOrdersFromLinkablePage` | ④ 当前页行 | **BR-WHS-IN-012**：剔除已被未软删进货单占用的订货 **`bus_no`** |
| 6 | Asm | `toLinkablePurchaseOrderRows` + `toPageLinkablePurchaseOrdersAggregatedResponse` | ⑤ | **一行一单** |

---

### 3.14 IF-WHS-IN-13 `getLinkablePurchaseOrderDetail`（按订货单号拉取进货预填）

**HTTP**：`POST /api/app/inventoryWarehouseInboundReceipt/getLinkablePurchaseOrderDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 已同意药库订货单号 |
| `purposeLocationId` | Long | N | 可选；非空时须与订货头 **`purpose_location_id`** 一致且 ∈ 库房门禁 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `originalBusNo` | String | 写入进货单 **`original_bus_no`** |
| `purchaseOrderBusNo` | String | 展示用，与 **`originalBusNo`** 相同 |
| `sourceLocationId` | Long | 默认 = 订货目的药库 |
| `purposeLocationId` | Long | 订货目的药库 |
| `supplierId` | Long | 订货头供应商 |
| `categoryEnum` | String | **`SupplyCategory#getCode()`** |
| `practitionerId` | Long | 订货经手人（可改） |
| `lines` | `List<InboundReceiptDetailLineDto>` | 从订货明细拷贝；**批号/追溯码留空**（**DM §6.1.2**）；含 **`productName`/`specification`/`manufacturer`/`unitCodeDictText`/`productCatalog`** 等展示富化（**行价 `price`/`totalPrice` 仍取订货落库价**，禁止当前目录价覆盖） |

**失败码**：`WHS_IN_001`（参数）；`WHS_IN_002`（订货未 AGREE/不存在/目的库不一致/**已被其它未删除进货单引用**）

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 参数 + 库房门禁 | |
| 2 | Pre | `validateOriginalPurchaseOrderForInboundReceipt` + `getSupplyByBusNo` | 须 **AGREE** + **CABINET_PURCHASE_ORDER** + **BR-WHS-IN-012**（**`existsUndeletedSupplyByOriginalBusNo`**） |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 批量产品读模型 |
| 4 | Asm | `toGetLinkablePurchaseOrderDetailResponse` + `enrichInboundReceiptDetailProductDisplay` + `enrichInboundReceiptDetailLines` | 拷贝头/行并富化展示 |

---

## 4. QueryService 与事件刷新

| QueryService | 订阅事件（节选） | 更新意图 |
| --- | --- | --- |
| **WarehouseDocumentDetail** | `SupplyRequestCreatedEvent`、`SupplyRequestSubmittedEvent`、`SupplyRequestWithdrawnEvent`、`SupplyRequestAgreedEvent`、`SupplyRequestRejectedEvent`、`SupplyRequestDeletedEvent` | 列表/明细QueryService upsert/UPDATE；字段级映射以 **DM-WKF-006 §6.2 `SupplyRequestQueryService` / `InventoryItemQueryService`** §4.2 为真源；**IF-WHS-IN-12** 另依赖订货 **AGREE** 列表口径 |
| **ProductDefinition** | （目录侧事件，不在本文展开） | 选品检索（**IF-WHS-IN-11**） |
| **一致性** | 写接口返回成功不代表QueryService已同步展示 | **最终一致**；前端可在写成功后刷新列表 |

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（与 §3 Cmd 序号对照）

| IF | App 方法 | Cmd | `DomainService#method(Command)` | 聚合写入 |
| --- | --- | --- | --- | --- |
| IF-WHS-IN-04 | `createInboundReceipt` | 1 | `SupplyRequestDomainService#createInboundReceipt(CreateInboundReceiptCommand)` | `wkf_supply_request` |
| IF-WHS-IN-05 | `updateInboundReceipt` | 1 | `SupplyRequestDomainService#createInboundReceipt(CreateInboundReceiptCommand)` | 同行明细对齐 |
| IF-WHS-IN-06 | `submitInboundReceiptApproval` | 1 | `SupplyRequestDomainService#submitApproval(SubmitApprovalCommand)` | 状态 |
| IF-WHS-IN-07 | `withdrawInboundReceiptApproval` | 1 | `SupplyRequestDomainService#withdrawApproval(WithdrawApprovalCommand)` | 状态 |
| IF-WHS-IN-08 | `agreeInboundReceipt` | 1 | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` | **AGREE** |
| IF-WHS-IN-08 | `agreeInboundReceipt` | 2 | `SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)`（×N 或单次批量） | `wkf_supply_delivery` |
| IF-WHS-IN-08 | `agreeInboundReceipt` | 3 | `ChargeItemDefinitionDomainService#addLotPurchasePrice` / `#addLotSalePrice`；`CatalogReferencePriceSyncSupport#syncPurchaseAndSaleIfChanged`（×N） | 收费项定义（定价行） |
| IF-WHS-IN-08 | `agreeInboundReceipt` | 4 | `InventoryItemDomainService#createInventoryItem` / `#increaseQuantity`（×N） | `wkf_inventory_item` |
| IF-WHS-IN-08 | `agreeInboundReceipt` | 5 | `TraceNumberDomainService#recordTraceRecords` / `#recordTraceRecordsFromLineItems`（×N 或单次批量，**WHS-IN-SDD-R-009**） | `cus_trace_number` + `cus_trace_number_flow` |
| IF-WHS-IN-09 | `rejectInboundReceipt` | 1 | `SupplyRequestDomainService#rejectRequest(RejectRequestCommand)` | **REJECT** |
| IF-WHS-IN-10 | `removeInboundReceipt` | 1 | `SupplyRequestDomainService#removeByBusNo(RemoveByBusNoCommand)` | 逻辑删除 |

### 5.2 事务边界 / 失败回滚

| 接口 | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-WHS-IN-04～07、09、10 | 单事务：`Cmd` 失败整单回滚 | 事件 `beforeCommit` 未触发则一并回滚 |
| IF-WHS-IN-08 | **默认**：**§3.9** 序号 **1～11** **同一事务** | 序号 **7～10**（发放/批号维定价/拆零库存/**追溯写库**）失败 → **WHS_IN_104**；**Pre** 序号 **6**（重复发放）失败 → **WHS_IN_104**；禁止长期处于 AGREE 未入库不可观测 |
| IF-WHS-IN-08（异步备选） | 若拆异步须登记补偿作业 | 对齐 AS §7 **最终一致** |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 审批链路 | `submit`/`agree`/`reject` 重复提交由领域状态机拒绝 | `SupplyRequestDomainService` | §3.7～§3.10 序号含 Cmd 的步骤 |
| 新建进货 | 可选 `Idempotency-Key` 24h 去重 | `InventoryWarehouseInboundReceiptAppService` | §3.5 序号 5 |
| 同意入库 | `supplyRequestValidation`；**`supplyDeliveryValidation`**；发放/批号维定价/拆零库存/**追溯写库**幂等靠领域不变式与 DB 约束 | `SupplyRequestQueryService`、`SupplyDeliveryQueryService`、`SupplyDeliveryDomainService`、`ChargeItemDefinitionDomainService`、`InventoryItemDomainService`、`TraceNumberDomainService` | §3.9 序号 3～10 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 编辑冲突 | `UpdateInboundReceiptRequest.rowVersion` 或 DB 行版本校验 | `InventoryWarehouseInboundReceiptAppService`、`SupplyRequestDomainService`（领域乐观锁实现处） | §3.6 |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| QueryService延迟 | 写成功后可提示前端延迟刷新 | 前端交互与通用错误提示约定；`InventoryWarehouseInboundReceiptAppService` 成功返回路径 | §4 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 审计可见性 | 审批人、申请人 ID 可对外展示；手机号等不在本接口出现 | `AgreeInboundReceiptRequest` / `RejectInboundReceiptRequest` 及领域聚合字段映射；本模块 Response 不含手机号字段 | §3.9～§3.10 |

### 6.5 审计日志

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 审批轨迹 | `approverId`、`approvalTime`、`rejectReason` 落领域聚合字段 | `SupplyRequestDomainService#agreeRequest` / `#rejectRequest` 及 **DM-WKF-006** 对应聚合持久化 | §3.9～§3.10 |

---

## 7. 测试要点

### 7.1 功能测试

| 测试层级 | 用例 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 功能 | 免申请进货 → 提交 → 同意 → 库存可查 | API 集成 | P0 |
| 功能 | 从订货生成 → `originalBusNo` 溯源 → 同意 → 入库 | API 集成 | P0 |
| 功能 | 订货未 AGREE 引用 → `WHS_IN_002` | API 集成 | P0 |
| 功能 | **AS-INV-WHS-001** 订货「审批同意」**不**自动产生 **`CABINET_INBOUND_RECEIPT`** 进货单 | API + DB 断言 | P0 |
| 功能 | 二次同意（已存在完成态发放）→ **`supplyDeliveryValidation`** → **WHS_IN_104** | API 集成 | P0 |
| 功能 | 审批同意 → 库存 **`quantity`** 为拆零量、**`unit_code`** 为 **`splitUnitCode`** | API + DB 断言 | P0 |
| 功能 | IF-WHS-IN-02 聚合列表含 **`approverName`**；**`AGREE`** 后与 **`approver_id`** 一致 | API 集成 | P0 |
| 功能 | **IF-WHS-IN-03** 详情 **`InboundReceiptDetailLineDto.traceNo`** 与 **`wkf_supply_request.trace_no`** 一致（可空） | API 集成 | P0 |
| 功能 | **IF-WHS-IN-03** 详情 **`productCatalog`**：QueryService命中时 **`unitBands`** 四槽（含 **`PACKAGE`**）、**`purchasePrice`**（**`PURCHASE`**）与各槽 **`unitPrice`** 回填符合 **WHS-IN-SDD-R-011** | API 集成 | P0 |
| 功能 | **IF-WHS-IN-04/05** 传入 **`traceNo`** → **`wkf_supply_request.trace_no`** 落库；超长 **>50000** → 业务失败（**WHS-IN-SDD-R-008**） | API 集成 | P0 |
| 功能 | **IF-WHS-IN-08** 同意成功且行 **`traceNo` 非空** → **`cus_trace_number` + `cus_trace_number_flow`** 存在对应入库轨迹（**`operation_type`** = **`CABINET_INBOUND_RECEIPT`**（与 **`SupplyType`** / **`wkf_supply_request.type_enum`** 一致），**BR-WHS-IN-011**） | API + DB 断言 | P0 |
| 功能 | IF-WHS-IN-12 空列表 / 分页 | API 集成 | P1 |

### 7.2 非功能测试

| 测试层级 | 用例 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 非功能 | 分页查询响应时间（经验目标 <1s） | 性能/API 监控 | P1 |

### 7.3 QueryService一致性

| 测试层级 | 用例 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| QueryService | 领域事件载荷后列表状态与写模型一致（异步容忍） | API + QueryService 刷新延迟断言 | P1 |

### 7.4 集成测试

| 测试层级 | 用例 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 集成 | 与 **Location**/**Supplier**/**ProductDefinition** 联通 | API 集成 | P0 |
| 集成 | **IF-WHS-IN-11**：**`ChargeItemDefinitionQueryService`**（**`PURCHASE`**）与 **`InventoryItemQueryService`**（目的库汇总）与选品分页联调 | API 集成 | P0 |
| 集成 | **IF-WHS-IN-08** 与 **`TraceNumberDomainService`**（**`cus_trace_number` + `cus_trace_number_flow`**，**DM-CUS-010-SDD** §5.1）联调 | API 集成 | P0 |

### 7.5 测试缺口跟踪

| 测试层级 | ID | 缺口 | 自动化归属 | 优先级 | 关闭条件 |
| --- | --- | --- | --- | --- | --- |
| QueryService/集成 | **GAP-IN-001** | IF-WHS-IN-12「尚未完全履约」过滤依赖 Query 扩展或专用 SQL | 文档与实现联调 | P1 | `WarehouseDocumentDetailPageQuery` 或 层闭合履约差额规则 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.43 | 2026-08-24 | **WHS-IN-SDD-R-018 / BR-WHS-IN-018**：同批号异价仅零库存且无收费/发药发耗时允许；同意写路径停用旧 ACTIVE 再写入；同价跳过；编排权威 **AS v0.41** |
| v2.41 | 2026-07-26 | **WHS-IN-SDD-R-017**：库房族数量单位 UI 同码同倍合并与 TRACE 不进下拉（对齐 **AS BR-WHS-IN-015**）；后端 `unitBands`/落库口径不变 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-03 | 初版草案（对照 sibling SDD） |
| v2.31 | 2026-05-24 | **库位门控统一**：**`CurrentManagedLocationIdsResolver`**（下拉 **`#narrowActiveLocationsByManaged`**，Gate **`#gatedLocationIdsFromActive`**）；**剔除** **`PractitionerRoleQueryService#listAssignmentsByPractitioner`** 于库位门控链；**管辖 empty 过渡态不收窄**；§1.1、§2.1、§2.2、§3.2～§3.3、§3.5、§3.12、§3.13；编排权威 **AS v0.29** |
| v2.30 | 2026-05-21 | **§3.9** 持久化刷盘由 **`SupplyRequestRepository#flush`** 改为 **`SupplyRequestDomainService#flushPendingChanges`**（应用层 **B.2** 禁直调 Repository）；§2.1/§2.2 同步 |
| v2.0 | 2026-05-03 | **按 `应用服务SDD文档生成提示词模板` SKILL 重整**：固定 §1～§8；§2.3 双锚（FQCN/README）；HTTP 全 **POST**；§3.1 中文列；编排序列 Cmd 带 `(...Command)`；§6/§7 子节与测试列；白名单↔序列对齐；新增 **GAP-IN-001**（可关联订货履约过滤） |
| v2.1 | 2026-05-03 | 对齐上游 **AS v0.3**；§2.2 `LocationQueryService#listWarehousesByOrganization` 补登记 **IF-WHS-IN-02、IF-WHS-IN-12**（与白名单↔§3 序列双向一致）；§7.1 用例措辞去应用方法名 |
| v2.2 | 2026-05-03 | 与 **`AS-INV-WHS-001-SDD` v1.0 定稿**结构对齐：§1.1 姊妹基线与实现阶段口径；§2.1/§2.2 Gate·Pre·Post 记法；§2.3 与 001 同锚点体系 + 进货增量行；新增 §2.4 白名单复核；§3 引导语与 §3.1.1 Pre/Asm 措辞同 001；移除独立 HTTP 规则段（路径仍列于 §3.1 表）；§3.3/§3.5/§3.13 QueryService Query 使用 `typeEnum=2`/`1` 字面口径（同 001）；§6/§7 表列收敛同 001 |
| v2.3 | 2026-05-03 | **IF-WHS-IN-12** 请求字段更名为 **`purposeLocationId`**，读路径按 `purpose_location_id` 筛选可关联订货（与订货写库及 **IF-WHS-PO-02** 一致）；文档版本同步修订 |
| v2.4 | 2026-05-11 | 对齐《应用服务 SDD 文档生成提示词模板》：§1.1 合并原 §1.3「实现裁决」；§3.1 下增补 **HTTP 路径生成规则** 硬门禁说明；§3.1.1 仅保留 **Gate / Pre / Cmd / Asm** 四类（IF-WHS-IN-08 发放/库存并入 **Cmd** 序号 5～6）；§6.2～§6.5 补 **代码位置（必须）** 列；§7 各表补 **测试层级** 与 **自动化归属** 等必填列 |
| v2.5 | 2026-05-11 | **实现锚点命名**：全文将旧 **`goodsreceipt` / `GoodsReceipt`** 与误写的 **`inboundsreceipt`** 统一为 **`inboundreceipt` 包路径**与 **`InboundReceipt*` 类型/方法**（与 **InboundReceipt** 一词一致，**无 `inbounds` 复数段**）。原「**`CABINET_GOODS_RECEIPT`** 等 DM 用语不改」为历史口径；现以 **`DM-WKF-006` v1.1** 与 **`SupplyType#getCode()`** 为准（进货 **`CABINET_INBOUND_RECEIPT`**） |
| v2.6 | 2026-05-13 | **IF-WHS-IN-01**：响应增补供应商、经手人（药师）、库管、药房+药库（`listPharmacies`+`listWarehouses` 并集，按 **PractitionerRole** 分配推导管辖 `locationId` 过滤；无管辖则全量）、入库项目类型两档（**药品定义表**/**耗材定义表**）；§2.1/§2.2/§2.3、§3.2、代码树 **dto** 同步 |
| v2.7 | 2026-05-13 | **IF-WHS-IN-01**：增补 **`statusOptions`（SupplyStatus）**；**全部初始化下拉统一 `OptionDto`**，删除 `WarehouseOptionDto` 等专用下拉 DTO 约定；§2.3、§3.2 Mapper 与编排序列同步 |
| v2.8 | 2026-05-13 | **IF-WHS-IN-02**：列表 **`records`** 改为 **`InboundReceiptListRowDto`**（**按 `bus_no` 聚合**）；`total` 为合并后单据条数；**IF-WHS-IN-03**：**`InboundReceiptDetailLineDto`** 列对齐「项目名称/规格/厂家产地/采购数量/单位/采购单价/总价/批号/效期/备注」；§1.2、§2.3、§3.3～§3.4、代码树 **dto** 同步 |
| v2.9 | 2026-05-13 | **枚举口径与代码对齐**：`SupplyType`/`SupplyStatus`/`SupplyRequestType`/`SupplyCategory` 及 Request/Response/`WarehouseDocumentDetailPageQuery` 一律 **`getCode()` 字符串**；进货固定 **`CABINET_INBOUND_RECEIPT`**、订货 **`CABINET_PURCHASE_ORDER`**；去除 sortOrder/DM 序号对外表述；§2.3 FQCN 更正为 **`com.zxplat.domain.share.enums`**；**IF-WHS-IN-02** 分页字段与 **`AbstractPageQuery#pageNo`** 对齐；§3.13 与 §3.3 DTO 关系澄清；§7.1 用例用语改为 code；对齐上游 **AS v0.10** |
| v2.10 | 2026-05-13 | **IF-WHS-IN-11**：请求必填 **`purposeLocationId`**；返回扩展 **编号/规格/厂家/产地/换算比列表/目的库可用库存**；**`purchasePrice`** 真源为定价 **`PURCHASE`**（**`ChargeItemDefinitionQueryService#findPurchaseItemByInstanceId`**），**WHS-IN-SDD-R-005**；库存 **`InventoryItemQueryService#calculateTotalAvailableInventory`**；新增 **`UnitRatioItemDto`**；§1.1/§1.2、§2.1～§2.3、§3.12、§7.4；对齐上游 **AS v0.11** |
| v2.11 | 2026-05-13 | **IF-WHS-IN-02**：**`InboundReceiptListRowDto`** 增补 **`approverId`/`approverName`**；**`sourceLocationId`** 与 **`source_location_id`** 对齐说明；可选 **`purposeLocationId`** 筛选；聚合 Mapper 约束 **`approver_id`** 多值；**IF-WHS-IN-05** **`rowVersion`** 改为 **`Integer`** 与 §3.4 详情一致；**IF-WHS-IN-08** 增补**审批通过**四步业务说明、**`approverId`** 可空默认当前用户、Gate/关键校验与 **`APPROVAL`** 门禁；**IF-WHS-IN-09** **`approverId`** 同步可空；§7.1；对齐上游 **AS v0.12** |
| v2.12 | 2026-05-13 | **IF-WHS-IN-08** 对齐遗留 **`purchaseInventoryApproved`**：**`supplyDeliveryValidation`**（**BR-WHS-IN-010**）、**`agreeRequest`+flush+再读`getSupplyByBusNo`**、**`createSupplyDelivery` → 批号维定价（`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`，WHS-IN-SDD-R-006）→ 拆零库存（WHS-IN-SDD-R-007）**（**§3.9** 序号 **1～10**）；§1.1 **R-004～R-007**、§1.2、§2.1～§2.3、§3.1.1、**§3.9**、§5.1～§5.2、§6.1、§7.1、**GAP-IN-002**；**Mapper** 签名增补拆零入参；对齐上游 **AS v0.13** |
| v2.13 | 2026-05-13 | **追溯码**：**IF-WHS-IN-03** **`InboundReceiptDetailLineDto.traceNo`**；**IF-WHS-IN-04/05** 入参长度与 **WHS-IN-SDD-R-008**（**TRC-WIDE-R-001**，**VARCHAR(50000)**）；**IF-WHS-IN-08** **§3.9** 增序号 **10 Cmd**（**`TraceNumberDomainService`**）+ **Asm 序号 11**；§1.1 **R-008/R-009**、§2.1～§2.3、§3.1.1、§3.4～§3.6、**§3.9**、§5.1～§5.2、§6.1、§7.1；**关闭 GAP-IN-002**；对齐上游 **AS v0.14** |
| v2.14 | 2026-05-13 | **IF-WHS-IN-02** 列表真源改为 **`wkf_supply_request`**：**`SupplyRequestQueryService#countDistinctBusNo` / `#listDistinctBusNoPage` / `#listByBusNosWithFilter`**（**`SupplyRequestDocumentListQuery`**）；Mapper **`toInboundReceiptListAggregated`**、**`toPageInboundReceiptsAggregatedResponse`**；展示名应用层富化；**§3.9** **`flush`** 统一为 **`SupplyRequestDomainService#flushPendingChanges`**（禁 **`EntityManager`**）；新增 **WHS-IN-SDD-R-010**；**WarehouseDocumentDetail** 仅保留 **IF-WHS-IN-12** 等路径 |
| v2.15 | 2026-05-13 | **IF-WHS-IN-01**：响应拆 **`pharmacyLocationOptions`** / **`warehouseLocationOptions`**（药房/药库分列，各自经岗位分配收窄；无分配则**不收窄**）；编排序列同步。**IF-WHS-IN-02/04/05/11/12** Gate：**可选药库 id 集合**与药库下拉同源（`resolveGatedWarehouseLocationIdsForInboundGate`）。**WHS-IN-SDD-R-006** 明确进货明细**批号必填**及同意路径整单失败口径。**IF-WHS-IN-11** DTO：删除 **`placeOfOrigin`**，**`manufacturer`** 合一厂家+产地。**IF-WHS-IN-08**：发放前校验允许 **`findRequestIdsHavingCompletedDelivery`** 批量等价 **`supplyDeliveryValidation`**（**A.9.1**） |
| v2.16 | 2026-05-13 | **IF-WHS-IN-03**：**`InboundReceiptDetailLineDto`** 字段表与实现 JSON 对齐（**`id`/`itemTable`/`itemId`/`itemQuantity`/`unitCode`/`lotNumber`/`traceNo`/`price`/`totalPrice`/`occurrenceTime`/`remake`/`productName`/`specification`/`manufacturer`**）；删除 **`manufacturerOrigin`** / 误列 **`expiryDate`** 契约（效期待 **DM** 行级扩展）；**§2.3** 锚点行、**Mapper** 签名 **`Map<String, ProductDefinitionDisplayDto>`**；对齐上游 **AS v0.16** |
| v2.17 | 2026-05-13 | **IF-WHS-IN-01**：药房/药库下拉改为 **`listAllActivePharmacies` / `listAllActiveWarehouses`**（启用态全库，不按机构过滤）+ **`narrowLocationsByManagedIds`**（**按类**与岗位分配求交；**无交则该类仍全量**）；请求 **`organizationId`** 标明**忽略**；§3.2/§3.6/§3.12 Gate 药库集合与 **`resolveGatedWarehouseLocationIdsForInboundGate`** 同源叙述更新。**IF-WHS-IN-11**：**`unitBands`**（**`ProductDefinitionCatalogUnitBandDto`**，**`ProductDefinitionCatalogUnitBandsAssembler`**）替代 **`UnitRatioItemDto`**；增补 **`categoryCodeDictText`**；§1.2 代码树、§2.1/§2.2、§3.12 |
| v2.23 | 2026-05-14 | **WHS-IN-SDD-R-007**：同意路径库存增量改取持久化拆零 **`item_quantity`** + **`SupplyPersistedSplitQuantity`**；**`splitUnitCode`** 仍经 **`loadAsMap`**；**剔除** **`MedicationProductQuantityToSplitUnitConverter`** 于进货同意链之白名单；§3.9 序号 **9** 叙述与失败码表同步；编排权威 **AS v0.26** |
| v2.25 | 2026-05-14 | **WHS-IN-SDD-R-005** 回调：**IF-WHS-IN-11** 选品 **`purchasePrice`** 真源恢复 **`PURCHASE`**（订货/采购业务）；**`availableQuantity`** 曾约定二次汇总（**v2.26** 起进货选品不再汇总，恒 **`null`**）；**`LOT_NUMBER_COST`** 随批号展示仅保留于 **药库出库 / 药房入库** 库存行选品链；§1.1 **R-005/R-011**、§2.1、§2.2、§2.3、§3.12；编排权威 **AS v0.28** |
| v2.26 | 2026-05-15 | **IF-WHS-IN-11**：选品列表**不再**汇总 **`availableQuantity`**（恒 **`null`**）；**`purposeLocationId`** 仍用于药库门禁；与 **v2.25** 中「仍二次汇总」口径变更对齐；§1.1 **R-005**、§1.2、§2.1、§2.2、§3.12 |
| v2.27 | 2026-05-15 | **供应同意链 `createInventoryItem`**：**`production_date`/`expiration_date`/`category_code`/`py_str`/`wb_str`** 新建落库（**WHS-IN-SDD-R-012**，覆盖 **IF-WHS-IN-08 / IF-PHA-IN-08 / IF-WHS-SO-08** 同源 Mapper）；**`toCreateInventoryItemCommand`** 签名增补 **`ProductDefinitionDisplayDto`**（**`quantity`/`unitCode`** 参数名与 Java 一致）；§1.1 **R-004/R-012**、§3.9 |
| v2.34 | 2026-05-29 | **库房读锚两档**：单据选品/详情槽价**仅** **`PURCHASE`** + **`LOT_NUMBER_COST`**；**`LOT_NUMBER_PRICE`** 同意入库仅写库、库房不读；§1.1 **R-006/R-011** |
| v2.33 | 2026-05-29 | **定价包装锚最终版**：维护态 **`PURCHASE`/`SALE`** 与批号 **`LOT_NUMBER_*`** 均为**每包装单位**价；库房 **`unitBands`** 仅以包装锚写槽（**`#applyInboundWarehouseBandPrices`**）；采购选品**不取 `SALE`**；§1.1 **R-005/R-006/R-011** |
| v2.32 | 2026-05-29 | **WHS-IN-SDD-R-011 包装锚统一**：库房选品/详情 **`unitBands`** — **`PURCHASE`/`PACKAGE` 原样落槽**；**`STOCK`/`SPLIT` 仅 `#applyInboundWarehouseBandPrices` / `#applyInboundWarehouseDetailLineBandPrices` 包装锚换算**；**禁止**采购价锚传播；§1.1、§2.2、§3.12 |
| v2.31 | 2026-05-29 | **WHS-IN-SDD-R-006 纠偏**：同意批号维定价 **`priceValue`** 须为**每 `BAND_PACKAGE` 单价**；供应行 **`price`**（每采购包装）经 **`#resolveLotNumberPackageUnitPriceForInboundAgree`** / **`#convertPurchasePackagingPriceToPackageUnitPrice`** 换算后落库；同意链批号定价前 **`loadAsMap`**；§1.1、§3.9 序号 **8**；编排权威 **AS v0.31** |
| v2.30 | 2026-05-29 | **WHS-IN-SDD-R-005/R-011 纠偏**：目录 **`PURCHASE`** 槽价改经 **`#applyInboundBandsPriceFromCatalogPurchase`**（**每采购包装**锚，**`purchase_unit_ratio`**）；**禁止**误用 **`#applyInboundBandsPriceUsingPackageAnchor`** 将箱价放大为 PURCHASE 槽 **`ratioToSplit` 倍**；批号 **`LOT_NUMBER_COST`** 仍走 **`BAND_PACKAGE`** 锚；§1.1、§2.1、§3.12 |
| v2.29 | 2026-05-15 | **WHS-IN-SDD-R-005/R-011**：**`purchase_unit_ratio`** 透出为 **`productCatalog.purchaseUnitRatioToSplit`**；**`IF-WHS-IN-11`** 选品 **`unitBands`[].`unitPrice`** 与 **IF-WHS-IN-03** 详情均以 **`applyInboundBandsPriceUsingPurchasePackagingRatio`** 为主换算（**`loadAsMap`** + **Resolver**）；§1.1、§3.12、§7.1 |
| v2.28 | 2026-05-15 | **WHS-IN-SDD-R-013**：**`occurrence_time`** 前端随明细；**`apply_time`** 仅 **`submitApproval`** 写当前时间，草稿 **`apply_time`=`null`**；**`approval_time`** 同意/驳回写当前时间；**`applicant_id`** 会话医务人员；**`approver_id`** 取 **`operatorId`**；**`practitioner_id`** **新建/修订请求体 `practitionerId`** 必填；**`InboundReceiptListRowDto`** 增补 **`occurrenceTime`**；**IF-WHS-IN-03** 明细 **`occurrenceTime`** 必填；**§3.9** **`toAgreeRequestCommand(…, operatorId)`**；§1.3、§3.2、§3.5、§3.6、§3.9 |
| v2.24 | 2026-05-14 | **WHS-IN-SDD-R-005**：**IF-WHS-IN-11** 选品 **`purchasePrice`** 展示真源改为 **`LOT_NUMBER_COST`**（目的库可用批次）；与 **WHS-IN-SDD-R-011**（**IF-WHS-IN-03** **`PURCHASE`**）分流；§1.1 编排权威 **AS v0.27**；§2.2 **`resolveLotNumberCostPrice`**；§2.3；§3.12 DTO/Asm |
| v2.22 | 2026-05-14 | **WHS-IN-SDD-R-011**：剔除与药库出库/药房入库 AS 之**契约互指**；若其它服务复用同一 **Resolver/Assembler**，字段族宜与本文 **§3.4** 一致；§1.1 编排权威 **AS v0.25** |
| v2.21 | 2026-05-14 | **WHS-IN-SDD-R-011**：**IF-WHS-IN-03** **`productCatalog`** 二次装配（**`ProductDefinitionPurchasePriceResolver`** + **`applyUnitPricingToBandsIfPresent`**）；§1.1 编排权威 **AS v0.24**；§2.2、§2.3、§3.4（**`InboundReceiptDetailLineDto`** 增补 **`startTime`/`endTime`/`unitCodeDictText`/`productCatalog`**；编排序列序号 **5**）、§3.12（选品 **`unitBands`** 四槽 **`PACKAGE`**；**IF-WHS-IN-11** Asm 采购价经 **Resolver**）；§7.1 |
| v2.20 | 2026-05-14 | **§1.2**：`bus_no` 序号键 **`busNoSeqKey`**（进货 **`CIR`**），**不含 `SQ`**；回退 **`SR`**；编排权威 AS **v0.22** |
| v2.19 | 2026-05-14 | **§1.2**：新建 **`bus_no`** 序号前缀 **`SQ_` + `SupplyType#getCode()`**（进货 **`SQ_CABINET_INBOUND_RECEIPT`**）；编排权威 AS **v0.21** |
| v2.35 | 2026-07-02 | **IF-WHS-IN-02**：**`sourceLocationId`/`purposeLocationId`=0** 展开可见药库 id 集合（**`InventoryWarehouseConstants`**）；**IF-WHS-IN-12**：改 **`bus_no` 聚合一行一单** + **`supplierName`**；**IF-WHS-IN-13**：预填行 **`InboundReceiptDetailLineDto`** + 产品展示富化（行价保留订货落库价）；对齐后端 **4666f46ae** |
| v2.42 | 2026-08-19 | **`invoiceNo`**：前端**不必填**；空→落库/上报占位 `0`（对齐 AS v0.40 / 09 §4.5e） |
| v2.41 | 2026-07-23 | **贴码单位改药典**：删除供应行 `trace_unit_code`；**R-015/R-016** 对齐 ceil + 药典快照（编排权威 **AS v0.38** / **AS-CUS-TRC-001 v2.0**） |
| v2.40 | 2026-07-19 | **`invoiceNo`**：明示契约可选 + **前端暂定必填**；空上报 `0`（对齐 AS v0.37 / 09 §4.5e） |
| v2.39 | 2026-07-19 | **`invoiceNo`**：详情/新建/修订单头可选；落 **`invoice_no`** 同号多行共享（进销存 3503/3504）；编排权威对齐前端进货单单头 |
| v2.38 | 2026-07-08 | **WHS-IN-SDD-R-016** / **BR-WHS-IN-013/014** 初版引入（**口径已被 v2.41 取代**） |
| v2.37 | 2026-07-06 | **WHS-IN-SDD-R-015**：追溯目标态 **IF-TRC-05**（**`TraceNumberRecordOrchestrationSupport`**）；**R-009** 标为遗留；编排权威 **AS v0.34** / **DM-CUS-010 v2.0.4** |
| v2.36 | 2026-07-04 | **WHS-IN-SDD-R-014** / **BR-WHS-IN-012**：订货→进货 **1:1**（**`original_bus_no`** 指向订货 **`bus_no`**；未软删占用；**不区分**占用方进货单状态）；**IF-WHS-IN-04/12/13** + 领域 **`createInboundReceipt`** 新建路径；**`existsByOriginalBusNoAndTypeEnum`**；编排权威 **AS v0.32** |
| v2.45 | 2026-08-31 | **IF-WHS-IN-11**：`ProductDefinitionItemDto` 增 **`purchaseSplitRatioText`**（采购↔拆零展示，如 `1箱/3桶`）；`#enrichInboundProductSelectionRows` 经 **`MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText`**；编排权威 **AS v0.43** |
| v2.44 | 2026-08-24 | **WHS-IN-SDD-R-019** / **BR-WHS-IN-019**：同意后回写实例级 **`PURCHASE`/`SALE`**（**`CatalogReferencePriceSyncSupport`**；批号同价跳过仍回写）；§1.1/§2.1/§3.9；编排权威 **AS v0.42** |

**文档负责人**：（待填） · **评审**：（待填）
