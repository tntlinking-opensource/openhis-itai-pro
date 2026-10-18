# 药库订货单 · 应用服务详细设计（SDD）

> 编码：AS-INV-WHS-001-SDD  
> 版本：v2.0.1 | 日期：2026-07-15 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药库订货单应用服务（库存 · 药库 · 采购订货页） |
| 边界 | 编排 **SupplyRequest** 订货（`type_enum`=`CABINET_PURCHASE_ORDER`）建单、修订、审批、删除；**可选**从 **`DEPT_PURCHASE_REQUEST`** 拉源预填；**预警快捷选品只读**（IF-14）勾选后经选品（IF-11 ids）入明细；**不**调用 **InventoryItem** 写侧 / **SupplyDelivery**；**不**调用 `createGoodsReceipt` |
| 编排权威 | `AS-INV-WHS-001-药库订货单.md` §2、§4 |
| 方法权威 | `DM-WKF-006` §6.1.1 / §6.1.1a；`DM-WKF-008` §6.2.17 / §6.2.19；`DM-ADM-003` §6.2；`DM-CUS-007` §6.2；目录 QueryService |
| 严格对齐 | IF **IF-WHS-PO-01～14** 与 AS 母表一致 |
| 实现阶段口径 | **目标态全量契约**（**IF-12/13** 应用待成码；**IF-14 / IF-11 ids / GAP-WHS-PO-001** 已落地） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 字段基于 AS 语义、DM §3/§6、QueryService §3 查询签名；**枚举 API/落库均为 `getCode()` 字符串**（B.10.8.1），本文档禁止以排序号/数字串作为契约 |
| QueryService口径 | 列表按 **`bus_no` 聚合**（**`SupplyRequestDocumentListQuery`** + Mapper **`toPurchaseOrderListAggregated`**）；选品以 **ProductDefinition** 为主；详情/写前校验 **`getSupplyByBusNo`** |
| 事务口径 | 每个写接口默认单事务；Pre/Gate 失败**禁止**进入 Cmd；详见 §5 |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **WHS-SDD-R-001** | **进货单不在本应用服务创建**：`IF-WHS-PO-08` 仅调用 **`agreeRequest`** 将订货单置 `AGREE`，**禁止**在本服务同一事务内调用 **`SupplyRequestDomainService#createGoodsReceipt`**；进货单落 **AS-INV-WHS-002**。与 **DM-WKF-006** §6.1.14「申请单 AGREE 后可编排进货单」为**允许**关系，非本模块必达。 |
| **WHS-SDD-R-002** | **订货草稿修订（IF-WHS-PO-05）**：`DM-WKF-006` §6.1 未单列「修订」方法；实现裁定 — **`CreatePurchaseOrderCommand.busNo` 传入既有、且处于可编辑态的订货单号** 时，领域服务执行「同一 `bus_no` 下明细行全量对齐（子行软删 + 按命令重插）」，与 **BR-SR-003** / **verifyAbleEdit** 一致。若领域代码尚未支持该重入语义，则 **Cmd 不得投产**，须先按 **DomainModel-SDD** 扩展 `createPurchaseOrder`。 |
| **WHS-SDD-R-003** | **新建 `bus_no`（IF-WHS-PO-04 等）**：首行 **`busNo` 空白** 时由 **`BusinessSequenceService#getSeqByDay`** 生成；**序号键前缀** = **`SupplyType.busNoSeqKey`**（订货 **`CPO`**），与 **`wkf_supply_request.type_enum`** 分桶；**无法匹配 `SupplyType` 白名单**时回退 **`SR`**（与 **`SupplyType.busNoSeqPrefixForTypeCode`** 一致；**不含**历史 **`SQ`**）。 |
| **WHS-SDD-R-004** | **可选拉科室采购申请（IF-WHS-PO-12/13 + IF-04）**：**`originalBusNo` 可空**（手工直建）；非空时 Pre 校验类型 **`DEPT_PURCHASE_REQUEST`**、状态 **`AGREE`**、**BR-WHS-PO-012**（`existsUndeletedSupplyByOriginalBusNo`）；修订禁止切换来源单号；预填仅读不落库，写仍走 `createPurchaseOrder`。 |
| **WHS-SDD-R-005** | **预警快捷选品（IF-WHS-PO-14 → IF-11）**：**只读**；效期=**最晚效期批**近效（**BR-014**），**禁止**复用库存明细 `warningMode=expire`；低储=全院合计**仅低于最低**（目录未维护回落全局包装阈值，**BR-015**），**禁止**纳入超储；须表头 `productTableName`；勾选后 **`productDefinitionIds`** 调 IF-11；**不**自动写数量。 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/warehouse/purchaseorder/
  service/
    InventoryWarehousePurchaseOrderAppService.java
  request/
    LoadPurchaseOrderFormInitRequest.java
    PagePurchaseOrdersRequest.java
    GetPurchaseOrderDetailRequest.java
    CreatePurchaseOrderRequest.java
    UpdatePurchaseOrderRequest.java
    SubmitPurchaseOrderApprovalRequest.java
    WithdrawPurchaseOrderApprovalRequest.java
    AgreePurchaseOrderRequest.java
    RejectPurchaseOrderRequest.java
    RemovePurchaseOrderRequest.java
    PageProductsForPurchaseRequest.java
    PageLinkableDeptPurchaseRequestsRequest.java
    GetLinkableDeptPurchaseRequestDetailRequest.java
    PagePurchaseWarningProductsRequest.java
  response/
    LoadPurchaseOrderFormInitResponse.java
    PagePurchaseOrdersResponse.java
    GetPurchaseOrderDetailResponse.java
    CreatePurchaseOrderResponse.java
    UpdatePurchaseOrderResponse.java
    SubmitPurchaseOrderApprovalResponse.java
    WithdrawPurchaseOrderApprovalResponse.java
    AgreePurchaseOrderResponse.java
    RejectPurchaseOrderResponse.java
    RemovePurchaseOrderResponse.java
    PageProductsForPurchaseResponse.java
    PageLinkableDeptPurchaseRequestsResponse.java
    GetLinkableDeptPurchaseRequestDetailResponse.java
    PagePurchaseWarningProductsResponse.java
  dto/
    PurchaseOrderLineDto.java
    PurchaseOrderDetailLineDto.java
    PurchaseOrderListRowDto.java
    ProductDefinitionItemDto.java
    PurchaseWarningProductRowDto.java
  support/
    PurchaseOrderWarningProductResolver.java
  mapper/
    InventoryWarehousePurchaseOrderMapper.java
    InventoryWarehousePurchaseOrderMapperImpl.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 / 审批 **`requireOperatorId`**；制单人解析前 Gate（**B.6.5**） |
| `CurrentManagedOrganizationIdsResolver` | 会话科室 **`requireSessionOrganizationUnitId`** / **`resolveOrganizationUnitIdForQuery`**（**B.6.5**）；**禁止** Request 传 `organizationId` 伪造范围 |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`**（管辖 empty 过渡态不收窄，**B.6.5**） |
| `SupplyRequestDomainService` | `createPurchaseOrder`、`submitApproval`、`withdrawApproval`、`agreeRequest`、`rejectRequest`、`removeByBusNo` |
| `SupplyRequestQueryService` | `countDistinctBusNo`、`listDistinctBusNoPage`、`listByBusNosWithFilter`、`getSupplyByBusNo`、`supplyRequestValidation`、`verifyAbleEdit` |
| `LocationQueryService` | **`listAllActiveWarehouses`**（**IF-WHS-PO-01** 初始化；**IF-WHS-PO-02/04/11** Gate） |
| `SupplierQueryService` | `listSuppliersByTypeAndActive`、`findSupplierById` |
| `ProductDefinitionDisplayTerminologyAssembler` | `loadAsMap`；**IF-11 ids**、**IF-14** 展示装配 |
| `ProductDefinitionPurchasePageLoader` | IF-WHS-PO-11：`pageProductForPurchase`（无 ids 时） |
| `InventoryItemQueryService` | IF-14：`listHospitalWideProductQuantityAggregates`；`listProductKeysWhoseLatestLotNearExpiry`（已落地） |
| `InventoryWarningSettingResolver` | IF-14：效期天数 / 全局包装阈值 |
| `PurchaseOrderWarningProductResolver` | IF-14：合并效期/低储（仅低于最低） |
| `InventoryWarehousePurchaseOrderMapper` | Request/Response、领域模型 ↔ DTO 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActiveWarehouses`**、**`listAllActiveDepartmentPharmacies`** | IF-WHS-PO-01 Pre（药库 + 关联申请弹窗科室药房）、IF-WHS-PO-02 Gate、IF-WHS-PO-04 Gate |
| `CurrentManagedLocationIdsResolver` | **`narrowActiveLocationsByManaged`**、**`gatedLocationIdsFromActive`** | IF-WHS-PO-01、IF-WHS-PO-02、IF-WHS-PO-04 |
| `SupplierQueryService` | `listSuppliersByTypeAndActive`、`findSupplierById` | IF-WHS-PO-01、IF-WHS-PO-04 Gate |
| `SupplyRequestDomainService` | `createPurchaseOrder` | IF-WHS-PO-04、IF-WHS-PO-05 |
| `SupplyRequestDomainService` | `submitApproval` | IF-WHS-PO-06 |
| `SupplyRequestDomainService` | `withdrawApproval` | IF-WHS-PO-07 |
| `SupplyRequestDomainService` | `agreeRequest` | IF-WHS-PO-08 |
| `SupplyRequestDomainService` | `rejectRequest` | IF-WHS-PO-09 |
| `SupplyRequestDomainService` | `removeByBusNo` | IF-WHS-PO-10 |
| `SupplyRequestQueryService` | `countDistinctBusNo`、`listDistinctBusNoPage`、`listByBusNosWithFilter`、`existsUndeletedSupplyByOriginalBusNo` | IF-WHS-PO-02；**IF-WHS-PO-12**；**IF-WHS-PO-04** 拉源占用 |
| `SupplyRequestQueryService` | `getSupplyByBusNo` | IF-WHS-PO-03、IF-WHS-PO-05 Pre、IF-WHS-PO-08/09/10 Pre、**IF-WHS-PO-13** |
| `SupplyRequestQueryService` | `supplyRequestValidation` | IF-WHS-PO-08 Pre |
| `SupplyRequestQueryService` | `verifyAbleEdit` | IF-WHS-PO-05 Pre |
| `ProductDefinitionPurchasePageLoader` | `pageProductForPurchase` | IF-WHS-PO-11（无 ids） |
| `ProductDefinitionDisplayTerminologyAssembler` | `loadAsMap` | IF-WHS-PO-03 Asm、**IF-WHS-PO-11**（有 ids）、**IF-WHS-PO-13** Asm、**IF-WHS-PO-14** Asm |
| `InventoryItemQueryService` | `listHospitalWideProductQuantityAggregates` | IF-WHS-PO-14（低储） |
| `InventoryItemQueryService` | `listProductKeysWhoseLatestLotNearExpiry` | IF-WHS-PO-14（效期） |
| `PurchaseOrderWarningProductResolver` | `resolveWarningProductHits` | IF-WHS-PO-14 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 `type_enum` | 后端 **`SupplyType.CABINET_PURCHASE_ORDER#getCode()`** 固定写入命令；**请求体不传** | `DM-WKF-006` §4.1.1 | IF-WHS-PO-04/05 |
| 可选上游类型 | `CreatePurchaseOrderRequest.originalBusNo` → 须对应 **`SupplyType.DEPT_PURCHASE_REQUEST`** | `DM-WKF-006` §4.1.4 value **31**；§6.1.1 | IF-WHS-PO-04/12/13；领域已校验 |
| 单据状态 `status_enum` | `PurchaseOrderListRowDto.statusEnum`（持久化 code） | `DM-WKF-006` §4.2 | IF-WHS-PO-02 列表行 |
| 源/目标位置类型（落库） | **`CreatePurchaseOrderCommand`** 行装配之 **`sourceTypeEnum`/`purposeTypeEnum`**（**`SupplyRequestType#getCode()`**） | `DM-WKF-006` §4.4；**`DM-WKF-006` §3.1.1** 双端点回填 | **HTTP 请求体不传**类型字段：由 **`InventoryWarehousePurchaseOrderMapper`** 据 **`purposeLocationId`**（及订货无源位时 **`sourceTypeEnum`=null**）调用 **`LocationQueryService#requireSupplyRequestTypeForLocationId`** 按 **`adm_location.form_enum`** 推导后写入命令；须满足 **BR-SR-016**（药库发起） |
| 供应类别 | `CreatePurchaseOrderRequest.categoryEnum`（**`String`**，**`SupplyCategory#getCode()`**） | `DM-WKF-006` §4.3；典型 **`STOCK_SUPPLY`** | 可缺省；空白时 Mapper 默认 **`STOCK_SUPPLY`** |
| 领域枚举 `SupplyType` | `com.zxplat.domain.workflow.supplyrequest.enums.SupplyType`（**目标态待生成**，与领域 SDD包一致） | `DM-WKF-006` §4.1.1 药库订货 value `1` | 领域命令装配 |
| 领域枚举 `SupplyStatus` | `com.zxplat.domain.workflow.supplyrequest.enums.SupplyStatus`（**目标态待生成**） | `DM-WKF-006` §4.2 | agree/submit 门禁对照 |
| 物品来源表 `item_table` | `PurchaseOrderLineDto.itemTable` | `DM-WKF-006` §3.1 `item_table` | 与 ProductDefinition `product_table_name` 对齐 |
| 计量单位 `unit_code` | `PurchaseOrderLineDto.unitCode` | `DM-WKF-006` §3.1 `unit_code` | 字典展示可选 |
| 预警筛选项 | `PagePurchaseWarningProductsRequest.warningFilter`：`expire` / `lowStock` / `all` | **AS BR-WHS-PO-014～015**；`InventoryWarningConstants` | IF-WHS-PO-14 |
| 系统设置效期天数 | `InventoryWarningConstants.SETTING_INVENTORY_EXPIRE_WARNING_DAYS` | **DM-WKF-008** §9.4 | IF-WHS-PO-14 |
| 系统设置全局包装阈值 | `InventoryWarningConstants.SETTING_INVENTORY_MIN_PACKAGE_QUANTITY_WARNING` | **DM-WKF-008** §9.4；目录无最低时回落 | IF-WHS-PO-14 |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向（白名单 → §3 序列） | 白名单内方法均至少在 §3 某一 IF 序列出现 |
| 反向（§3 序列 → 白名单） | §3 各 **调用** 均可在 §2.2 找到同名条目 |
| 互斥步骤对 | IF-WHS-PO-04 仅新建（无 `busNo`）；IF-WHS-PO-05 仅修订（有 `busNo`），不得串为无条件两步 |

## 3. 按接口 ID 命名展开

先读完各 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-WHS-PO-01 | `loadPurchaseOrderFormInit` | 加载订货页初始化 | `POST /api/app/inventoryWarehousePurchaseOrder/loadPurchaseOrderFormInit` |
| IF-WHS-PO-02 | `pagePurchaseOrders` | 订货单分页列表 | `POST /api/app/inventoryWarehousePurchaseOrder/pagePurchaseOrders` |
| IF-WHS-PO-03 | `getPurchaseOrderDetail` | 订货单详情 | `POST /api/app/inventoryWarehousePurchaseOrder/getPurchaseOrderDetail` |
| IF-WHS-PO-04 | `createPurchaseOrder` | 新建订货单 | `POST /api/app/inventoryWarehousePurchaseOrder/createPurchaseOrder` |
| IF-WHS-PO-05 | `updatePurchaseOrder` | 编辑订货单 | `POST /api/app/inventoryWarehousePurchaseOrder/updatePurchaseOrder` |
| IF-WHS-PO-06 | `submitPurchaseOrderApproval` | 提交审批 | `POST /api/app/inventoryWarehousePurchaseOrder/submitPurchaseOrderApproval` |
| IF-WHS-PO-07 | `withdrawPurchaseOrderApproval` | 撤回审批 | `POST /api/app/inventoryWarehousePurchaseOrder/withdrawPurchaseOrderApproval` |
| IF-WHS-PO-08 | `agreePurchaseOrder` | 审批同意 | `POST /api/app/inventoryWarehousePurchaseOrder/agreePurchaseOrder` |
| IF-WHS-PO-09 | `rejectPurchaseOrder` | 审批驳回 | `POST /api/app/inventoryWarehousePurchaseOrder/rejectPurchaseOrder` |
| IF-WHS-PO-10 | `removePurchaseOrder` | 删除订货单 | `POST /api/app/inventoryWarehousePurchaseOrder/removePurchaseOrder` |
| IF-WHS-PO-11 | `pageProductsForPurchase` | 采购选品分页 | `POST /api/app/inventoryWarehousePurchaseOrder/pageProductsForPurchase` |
| IF-WHS-PO-12 | `pageLinkableDeptPurchaseRequests` | 可关联科室采购申请分页 | `POST /api/app/inventoryWarehousePurchaseOrder/pageLinkableDeptPurchaseRequests` |
| IF-WHS-PO-13 | `getLinkableDeptPurchaseRequestDetail` | 科室采购申请预填明细 | `POST /api/app/inventoryWarehousePurchaseOrder/getLinkableDeptPurchaseRequestDetail` |
| IF-WHS-PO-14 | `pagePurchaseWarningProducts` | 预警快捷选品分页 | `POST /api/app/inventoryWarehousePurchaseOrder/pagePurchaseWarningProducts` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁（`*QueryService` / `*QueryService`） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService#…(…Command)`，持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | `Mapper` 纯 DTO 装配 | — |

记法：`SupplyRequestQueryService#getSupplyByBusNo` 表示对注入 Bean 实例调用。

---

### 3.2 IF-WHS-PO-01 `loadPurchaseOrderFormInit`（加载订货页初始化）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/loadPurchaseOrderFormInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | N | **兼容旧客户端**：若出现由服务端忽略；机构以 **`CurrentManagedOrganizationIdsResolver`** 会话为准 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `warehouseLocationOptions` | `List<OptionDto>` | 启用态药库经 **`narrowActiveLocationsByManaged`** 收窄；**AppService 在首项插入** `value="0"`、`label="全部"` 供 **IF-WHS-PO-02** 筛选 |
| `departmentPharmacyLocationOptions` | `List<OptionDto>` | 院内启用**科室药房**（**`listAllActiveDepartmentPharmacies`**，**不按**药库管辖收窄）；**AppService 在首项插入** `value="0"`、`label="全部"`，供 **IF-WHS-PO-12** 申请科室筛选 |
| `supplierOptions` | `List<OptionDto>` | 启用供应商下拉（**B.10.5**） |
| `warehouseKeeperOptions` | `List<OptionDto>` | 库管员/经手人候选（**`PractitionerRoleScopeLoader`** + **`ROLE_WAREHOUSE`**） |
| `statusOptions` | `List<OptionDto>` | 单据状态（`value`=`SupplyStatus#getCode()`） |
| `purchaseItemTableTypeOptions` | `List<OptionDto>` | 物品来源表（药品/耗材） |

> **Breaking（相对 v1.6 前）**：删除 **`warehouseOptions`** / **`supplierOptions` 专用 DTO** / **`allWarehousesOption`**；统一 **`OptionDto`**，药库列表字段名 **`warehouseLocationOptions`**。

**Mapper 接口方法签名**

- `toOptionDtoListFromLocations(List<Location>): List<OptionDto>`
- `toOptionDtoListFromSuppliers(List<SupplierListItemQueryModel>): List<OptionDto>`
- `toLoadPurchaseOrderFormInitResponse(...): LoadPurchaseOrderFormInitResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `organizationId` 解析成功 | 非法则 **结束** |
| 2 | Pre | `LocationQueryService#listAllActiveWarehouses` | — | 启用态药库全库列表（仅读；**不按**旧 **`listWarehousesByOrganization`** 机构过滤） |
| 3 | Pre | （装配） | ② | **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** → **`warehouseLocationOptions`**（首项插入「全部」） |
| 3a | Pre | `LocationQueryService#listAllActiveDepartmentPharmacies` | — | 启用态科室药房全库列表 → **`departmentPharmacyLocationOptions`**（首项插入「全部」；关联申请弹窗） |
| 4 | Pre | `SupplierQueryService#listSuppliersByTypeAndActive` | 采购/供货类型过滤参数（与产品字典对齐，默认 ACTIVE） | 仅读 |
| 5 | Asm | `InventoryWarehousePurchaseOrderMapper#toLoadPurchaseOrderFormInitResponse` | **收窄后药库**（步骤 ③）、**科室药房**（③a）与 **`SupplierQueryService`** 结果（④） | 仅装配 |

---

### 3.3 IF-WHS-PO-02 `pagePurchaseOrders`（订货单分页列表）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/pagePurchaseOrders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 订货目的药库；映射QueryService `purpose_location_id`；`0` 表示机构内全部药库（与初始化「全部」选项占位一致） |
| `statusEnum` | String | N | 可空；非空须为合法 **`SupplyStatus#getCode()`** |
| `keyword` | String | N | 单据号/备注关键字 |
| `applyTimeFrom` | `LocalDateTime` | N | 申请时间起 |
| `applyTimeTo` | `LocalDateTime` | N | 申请时间止 |
| `pageNo` | Integer | Y | 页码 ≥1（**B.10.11**） |
| `pageSize` | Integer | Y | 页大小 |

> **已删除**：`typeEnum` 请求字段（后端固定 **`CABINET_PURCHASE_ORDER`** 写入 Query）。

**Response**（`PagePurchaseOrdersResponse` extends **`PagedResult<PurchaseOrderListRowDto>`**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | 不重复 **`busNo`** 总数 |
| `pageNo` / `pageSize` | Integer | 分页元数据 |
| `content` | `List<PurchaseOrderListRowDto>` | 当前页聚合行 |

**DTO：PurchaseOrderListRowDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 聚合键 |
| `supplierName` | String | 供应商展示名（AppService enrich） |
| `purposeLocationId` | Long | 目的药库 |
| `purposeLocationName` | String | 药库展示名 |
| `statusEnum` / `statusEnumText` | String | 状态 code / 展示文案 |
| `creatorName` / `approverName` | String | 制单人 / 审批人展示名 |
| `approverId` | Long | 审批人主键 |
| `totalAmount` | `BigDecimal` | 各行 `total_price` 之和 |
| `approvalTime` / `applyTime` / `occurrenceTime` | `LocalDateTime` | 聚合时刻 |

**关键校验**

- `purposeLocationId` 非占位 `0` 时必须 ∈ **`gatedLocationIdsFromActive(listAllActiveWarehouses)`**；`0` 时展开为门控 id 集合写入 **`purposeLocationIds`**（无可见库时用 `[-1]` 空结果）。

**Mapper 接口方法签名**

- `toPurchaseOrderListAggregated(List<String> busNoPageOrder, List<SupplyRequestModel> detailRows): List<PurchaseOrderListRowDto>`
- `toPagePurchaseOrdersAggregatedResponse(long mergedTotal, int pageNo, int pageSize, List<PurchaseOrderListRowDto> records): PagePurchaseOrdersResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 + 药库门控 | 分页、`purposeLocationId` | 非法则 **结束** |
| 2 | Pre | 组装 **`SupplyRequestDocumentListQuery`** | `typeEnum=CABINET_PURCHASE_ORDER`、`purposeLocationId`/`purposeLocationIds`、状态、时间、关键字 | |
| 3 | Pre | `countDistinctBusNo` → `listDistinctBusNoPage` → `listByBusNosWithFilter` | ② | **仅读**；得当前页 busNo 顺序 + 明细行 |
| 4 | Pre | `toPurchaseOrderListAggregated` + `enrichPurchaseOrderListDisplayNames` | ③ | Mapper 聚合 + AppService 富化 |
| 5 | Asm | `toPagePurchaseOrdersAggregatedResponse` | ④ | 仅装配 |

---

### 3.4 IF-WHS-PO-03 `getPurchaseOrderDetail`（订货单详情）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/getPurchaseOrderDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 订货单号 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 单据号 |
| `typeEnum` | String | 恒为 **`CABINET_PURCHASE_ORDER`** |
| `statusEnum` | String | **`SupplyStatus#getCode()`** |
| `supplierId` | Long | N |
| `sourceLocationId` | Long | N | QueryService源位；药库订货落库多为空 |
| `purposeLocationId` | Long | Y |
| `sourceTypeEnum` | String | N | **`SupplyRequestType#getCode()`**；QueryService落库值；无源位时 **null** |
| `purposeTypeEnum` | String | Y | **`SupplyRequestType#getCode()`** |
| `categoryEnum` | String | Y | **`SupplyCategory#getCode()`** |
| `practitionerId` | Long | Y | 经手人医务人员主键 |
| `rowVersion` | Integer | Y | 文档级乐观锁（`@JsonInclude.ALWAYS`）；修订必带回 |
| `lines` | `List<PurchaseOrderDetailLineDto>` | 明细 | 含 `productCatalog` / 单位槽 enrich |

**DTO：PurchaseOrderDetailLineDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | Long | N | 行主键 |
| `itemTable` | String | Y | 物品来源表 |
| `itemId` | Long | Y | 物品 ID |
| `itemQuantity` | `BigDecimal` | Y | 数量 |
| `unitCode` | String | Y | 单位 |
| `price` | `BigDecimal` | N | 单价 |
| `totalPrice` | `BigDecimal` | N | 总价 |
| `occurrenceTime` | `LocalDateTime` | N | 期望到货 |
| `remake` | String | N | 备注 |
| `productName` | String | N | 选服 `ProductDefinition` 回显 |

**关键校验**

- `busNo` 对应 `type_enum`=`CABINET_PURCHASE_ORDER`；否则 **WHS_PO_103**。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_PO_103` | 404 | 不存在或非订货单 |

**Mapper 接口方法签名**

- `toGetPurchaseOrderDetailResponse(List<SupplyRequestModel>, Map<Long, ProductDefinitionDisplayDto>): GetPurchaseOrderDetailResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` 非空 | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | 若空或未包含 **`CABINET_PURCHASE_ORDER`** → **失败** |
| 3 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 提取 `itemId`+`itemTable` 分组批量 | **可选**；失败可降级仅展示 ID |
| 4 | Asm | `InventoryWarehousePurchaseOrderMapper#toGetPurchaseOrderDetailResponse` | ②③ | 仅装配 |

---

### 3.5 IF-WHS-PO-04 `createPurchaseOrder`（新建订货单）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/createPurchaseOrder`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 入库目的药库（写库 `purpose_location_id`；药库订货不要求请求体传 `source_location_id`） |
| `originalBusNo` | String | N | **可选**：已同意 **`DEPT_PURCHASE_REQUEST`** 单号；空=手工直建（**WHS-SDD-R-004**） |
| `categoryEnum` | String | N | **`SupplyCategory#getCode()`**；缺省或空白时后端按 **`STOCK_SUPPLY`** 装配 |
| `supplierId` | Long | N | 供应商 |
| `practitionerId` | Long | Y | 经手人（库管员下拉） |
| `lines` | `List<PurchaseOrderLineDto>` | Y | 至少 1 行；每行 **`occurrenceTime` 必填** |

**DTO：PurchaseOrderLineDto**（与 Request 嵌套相同字段）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `itemTable` | String | Y | 物品来源表 |
| `itemId` | Long | Y | 物品定义 ID |
| `itemQuantity` | `BigDecimal` | Y | >0 |
| `unitCode` | String | Y | 单位编码 |
| `price` | `BigDecimal` | N | 单价 |
| `totalPrice` | `BigDecimal` | N | 总价 |
| `occurrenceTime` | `LocalDateTime` | Y | 制单时刻 |
| `remake` | String | N | 行备注 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 新建单据号 |
| `statusEnum` | String | 初态 **`PENDING_APPROVAL`**（`SupplyStatus#getCode()`） |

**关键校验**

- `lines` 非空；**`purposeLocationId`** 须为当前用户可见药库，且经 **`LocationQueryService`** 推导之 **`purposeTypeEnum`** 满足 **BR-SR-016** / **BR-WHS-PO-002**（与 **DM-WKF-006** 药库订货语义一致）。
- `supplierId` 若填：`SupplierQueryService#findSupplierById` 存在且未废弃（**BR-SUP-005**）。
- **若** `originalBusNo` 非空：须 **`DEPT_PURCHASE_REQUEST` + `AGREE`**，且 **`existsUndeletedSupplyByOriginalBusNo`** 为 false（**BR-WHS-PO-010/012**）；失败 → **`WHS_PO_106`**。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_PO_001` | 400 | 校验失败（明细/类型/位置） |
| `WHS_PO_002` | 400 | 供应商无效 |
| `WHS_PO_106` | 400 | 科室采购申请不可引用（未同意/类型不符/已占用） |

**Mapper 接口方法签名**

- `toCreatePurchaseOrderCommand(CreatePurchaseOrderRequest): CreatePurchaseOrderCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | lines 非空、数量合法 | |
| 2 | Gate | **`LocationQueryService#listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**（同源 **IF-WHS-PO-02** Gate） | **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** | `purposeLocationId` 必须 ∈ 门控 id 集合；**管辖 empty → 启用态药库全量 id** |
| 3 | Pre | `SupplierQueryService#findSupplierById` | `supplierId` | **若** `supplierId==null` **则跳过**；若非 null 且不存在 → **失败** |
| 4 | Pre | `getSupplyByBusNo` + `existsUndeletedSupplyByOriginalBusNo` | `originalBusNo` | **若** `originalBusNo` 空白 → **跳过**；否则须 **AGREE + DEPT_PURCHASE_REQUEST** 且未占用 |
| 5 | Cmd | `SupplyRequestDomainService#createPurchaseOrder(CreatePurchaseOrderCommand)` | Mapper 装配命令：`typeEnum`=`CABINET_PURCHASE_ORDER`，可选 `originalBusNo`；`applicantId`/`applyTime` 由当前用户与时间填充 | **写库点 ①**：插入 `wkf_supply_request` 多行；发布 `SupplyRequestCreatedEvent` |
| 6 | Asm | `InventoryWarehousePurchaseOrderMapper#toCreatePurchaseOrderResponse` | ⑤ 返回 `busNo`、状态 | |

---

### 3.6 IF-WHS-PO-05 `updatePurchaseOrder`（编辑订货单）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/updatePurchaseOrder`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 既有订货单 |
| `purposeLocationId` | Long | Y | 入库目的药库（全量修订写库 `purpose_location_id`） |
| `categoryEnum` | String | N | **`SupplyCategory#getCode()`**；缺省或空白时后端默认 **`STOCK_SUPPLY`** |
| `supplierId` | Long | N | |
| `practitionerId` | Long | Y | 经手人 |
| `lines` | `List<PurchaseOrderLineDto>` | Y | 全量替换集合 |
| `rowVersion` | Long | Y | 乐观锁；须与详情 **`rowVersion`** 一致，映射 **`CreatePurchaseOrderCommand.expectedRowVersionForRevision`** |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | String | 与入参相同 |
| `statusEnum` | String | 仍为可编辑态（如 **`PENDING_APPROVAL`**；以领域为准） |

**关键校验**

- Pre：`getSupplyByBusNo` → 全部行 `type_enum`=`CABINET_PURCHASE_ORDER` 且 `status_enum` 允许编辑（如 **`PENDING_APPROVAL`** 等，与 **BR-SR-003** 一致）。
- Pre：抽样或逐行 `verifyAbleEdit(busNo, itemId)`（新物品 `itemId` 仅校验单头可编辑态 — **若**领域仅支持按行校验，则对**删除行**跳过、对**新增行**仅校验头状态）。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_PO_004` | 409 | 不可编辑 |
| `WHS_PO_105` | 409 | 并发冲突 |

**Mapper 接口方法签名**

- `toCreatePurchaseOrderCommandForUpdate(UpdatePurchaseOrderRequest): CreatePurchaseOrderCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo`、lines 非空 | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | 非法或类型非 **`CABINET_PURCHASE_ORDER`** → **失败** |
| 3 | Pre | `SupplyRequestQueryService#verifyAbleEdit` | `busNo` + 代表性 `itemId` | **若**返回不可编辑 → **失败（禁止 Cmd）** |
| 4 | Pre | `SupplierQueryService#findSupplierById` | `supplierId` | 同 IF-04 条件跳过语义 |
| 5 | Cmd | `SupplyRequestDomainService#createPurchaseOrder(CreatePurchaseOrderCommand)` | **命令携带既有 `busNo`** + **`expectedRowVersionForRevision`** | **写库点 ①**；版本冲突 **`WHS_PO_105`** |
| 6 | Asm | `InventoryWarehousePurchaseOrderMapper#toUpdatePurchaseOrderResponse` | ⑤ | |

---

### 3.7 IF-WHS-PO-06 `submitPurchaseOrderApproval`（提交审批）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/submitPurchaseOrderApproval`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |
| `applyClock` | `LocalDateTime` | N | 申请时间，默认当前 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |
| `statusEnum` | String | 提交成功后 **`APPROVAL`**（`SupplyStatus#getCode()`；示例：审核中） |

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_PO_003` | 409 | 状态不允许 |

**Mapper 接口方法签名**

- `toSubmitApprovalCommand(SubmitPurchaseOrderApprovalRequest): SubmitApprovalCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `busNo` | 确认 **`CABINET_PURCHASE_ORDER`** |
| 3 | Cmd | `SupplyRequestDomainService#submitApproval(SubmitApprovalCommand)` | | **写库点 ①**：`SupplyRequestSubmittedEvent`（须带 `statusEnum` 与租户等快照；`InventoryItemQueryService（同步查询）` 对 UUID 形 `eventId` 不可按数字比序，见实现） |
| 4 | Asm | `InventoryWarehousePurchaseOrderMapper#toSubmitPurchaseOrderApprovalResponse` | | |

---

### 3.8 IF-WHS-PO-07 `withdrawPurchaseOrderApproval`（撤回审批）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/withdrawPurchaseOrderApproval`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |
| `statusEnum` | String | 撤回成功后 **`WITHDRAW`**（`SupplyStatus#getCode()`） |

**失败码口径**：同 `WHS_PO_003`

**Mapper 接口方法签名**

- `toWithdrawApprovalCommand(WithdrawPurchaseOrderApprovalRequest): WithdrawApprovalCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | | |
| 3 | Cmd | `SupplyRequestDomainService#withdrawApproval(WithdrawApprovalCommand)` | | **写库点 ①**：`SupplyRequestWithdrawnEvent`（须带 `statusEnum` 快照；QueryService 侧同 IF-WHS-PO-06） |
| 4 | Asm | `InventoryWarehousePurchaseOrderMapper#toWithdrawPurchaseOrderApprovalResponse` | | |

---

### 3.9 IF-WHS-PO-08 `agreePurchaseOrder`（审批同意）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/agreePurchaseOrder`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | 单据号 |
| `approverId` | Long | Y | 审批人，默认 **`CurrentOperatorResolver#requireOperatorId`** |
| `approvalClock` | `LocalDateTime` | N | 审批时间 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |

**关键校验**

- Pre：`supplyRequestValidation(busNo)` 防重复同意（**DM** §6.2.6）。
- **禁止**：本方法内调用 `createGoodsReceipt`（**§1.3 WHS-SDD-R-001**）。

**失败码口径**

| 业务码 | HTTP | 含义 |
| --- | --- | --- |
| `WHS_PO_003` | 409 | 状态不允许 |
| `WHS_PO_104` | 409 | 重复同意 |

**Mapper 接口方法签名**

- `toAgreeRequestCommand(AgreePurchaseOrderRequest): AgreeRequestCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo`、`approverId` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | | **`CABINET_PURCHASE_ORDER`** |
| 3 | Pre | `SupplyRequestQueryService#supplyRequestValidation` | `busNo` | 已 AGREE → **失败（WHS_PO_104）** |
| 4 | Cmd | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` | | **写库点 ①**：`SupplyRequestAgreedEvent`；**不**调用 createGoodsReceipt |
| 5 | Asm | `InventoryWarehousePurchaseOrderMapper#toAgreePurchaseOrderResponse` | | |

---

### 3.10 IF-WHS-PO-09 `rejectPurchaseOrder`（审批驳回）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/rejectPurchaseOrder`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `busNo` | String | Y | |
| `approverId` | Long | Y | |
| `approvalClock` | `LocalDateTime` | N | |
| `rejectReason` | String | Y | 驳回原因 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | |

**Mapper 接口方法签名**

- `toRejectRequestCommand(RejectPurchaseOrderRequest): RejectRequestCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `rejectReason` 非空 | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | | |
| 3 | Cmd | `SupplyRequestDomainService#rejectRequest(RejectRequestCommand)` | | **写库点 ①**：`SupplyRequestRejectedEvent` |
| 4 | Asm | `InventoryWarehousePurchaseOrderMapper#toRejectPurchaseOrderResponse` | | |

---

### 3.11 IF-WHS-PO-10 `removePurchaseOrder`（删除订货单）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/removePurchaseOrder`

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
| `WHS_PO_005` | 409 | 下游引用禁止删除 |

**Mapper 接口方法签名**

- `toRemoveByBusNoCommand(RemovePurchaseOrderRequest): RemoveByBusNoCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `busNo` | |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | | |
| 3 | Cmd | `SupplyRequestDomainService#removeByBusNo(RemoveByBusNoCommand)` | | **写库点 ①**：`SupplyRequestDeletedEvent` |
| 4 | Asm | `InventoryWarehousePurchaseOrderMapper#toRemovePurchaseOrderResponse` | | |

---

### 3.12 IF-WHS-PO-11 `pageProductsForPurchase`（采购选品分页）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/pageProductsForPurchase`

**Request**（对齐 **ProductDefinitionPageQuery**，字段全集）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 目的药库门禁（与 **IF-WHS-IN-11** 一致） |
| `productTableName` | String | N | `med_medication_definition` / `adm_device_definition` / 空=全部；**预警带回时须与表头一致** |
| `productDefinitionIds` | `List<Long>` | N | 非空时按 id 收窄（**IF-14** 快捷）；走 **`ProductDefinitionDisplayTerminologyAssembler#loadAsMap`** |
| `keyword` | String | N | |
| `categoryCode` | String | N | |
| `activeFlag` | Boolean | N | 默认 true |
| `supplierId` | Long | N | |
| `manufacturer` | String | N | |
| `includeInventory` | Boolean | N | DTO 与 **ProductDefinitionPageQuery** 对齐；**当前QueryService表未投影该字段**，`MedicationDefinitionQueryService#pageForPurchase / DeviceDefinitionQueryService#pageForPurchase` **不按此参数过滤**（详见 **DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService** §3.1）。 |
| `pageNo` | Integer | Y | |
| `pageSize` | Integer | Y | |
| `sortField` | String | N | DTO 预留；**采购选品路径不参与 Criteria WHERE**；分页排序以QueryService实现为准（**`created_at` DESC**）。 |
| `sortOrder` | String | N | 同上。 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | |
| `records` | `List<ProductDefinitionItemDto>` | |

**DTO：ProductDefinitionItemDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `productDefinitionId` | Long | Y | |
| `productTableName` | String | Y | |
| `productBusNo` | String | N | 目录 **`bus_no`**（**`ProductDefinitionDisplayDto#getProductBusNo()`**） |
| `productName` | String | Y | |
| `merchandiseName` | String | N | |
| `unitCode` | String | Y | |
| `minUnitCode` | String | N | |
| `categoryCode` | String | N | |
| `price` | `BigDecimal` | N | |
| `purchasePrice` | `BigDecimal` | N | 定价表 **PURCHASE** 实例维进价 |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | N | 与进货选品同构 |
| `availableQuantity` | `BigDecimal` | N | 恒 **null**（不汇总库存） |
| `manufacturer` | String | N | |
| `pyStr` | String | N | |
| `wbStr` | String | N | |

**失败码口径**：`WHS_PO_102`

**Mapper 接口方法签名**

- `toPageProductsForPurchaseResponse(Page<ProductDefinitionDisplayDto>): PageProductsForPurchaseResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 分页；`purposeLocationId`；药库白名单 | |
| 2a | Pre | `ProductDefinitionDisplayTerminologyAssembler#loadAsMap` | **仅当** `productDefinitionIds` 非空 | ids 快捷路径 |
| 2b | Pre | `MedicationDefinitionQueryService#pageForPurchase / DeviceDefinitionQueryService#pageForPurchase` | `ProductDefinitionPageQuery`；**仅当** ids 为空 | **仅读路径**（DM §6.2 **Q-PD-004**） |
| 3 | Asm | `InventoryWarehousePurchaseOrderMapper#toPageProductsForPurchaseResponse` | ② | |

---

### 3.13 IF-WHS-PO-12 `pageLinkableDeptPurchaseRequests`（可关联科室采购申请分页）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/pageLinkableDeptPurchaseRequests`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 申请单号/备注 |
| `applyTimeFrom` / `applyTimeTo` | `LocalDateTime` | N | 申请时间窗 |
| `pageNo` / `pageSize` | Integer | Y | 分页 |

**Response**：`PagedResult` 行含 `busNo`、申请科室药房名、状态、总金额、申请时间等（按 `bus_no` 聚合）。

**关键校验**：仅 **`DEPT_PURCHASE_REQUEST` + `AGREE`**；排除 **`existsUndeletedSupplyByOriginalBusNo`**（占用方类型 **`CABINET_PURCHASE_ORDER`**）为 true 的单号（**BR-WHS-PO-012**）。

**编排序列**：Gate → `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`（type/status 如上）→ 过滤占用 → Asm。

---

### 3.14 IF-WHS-PO-13 `getLinkableDeptPurchaseRequestDetail`（科室采购申请预填明细）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/getLinkableDeptPurchaseRequestDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `originalBusNo` | String | Y | 已同意科室采购申请单号 |

**Response**：申请头摘要（科室药房）+ `List<PurchaseOrderDetailLineDto>` 预填行（物品/数量/单位/参考价；**无**供应商；批号空）。

**关键校验**：同 **IF-WHS-PO-12** 门禁；失败 → **`WHS_PO_106`**。

**编排序列**：Gate → Pre `getSupplyByBusNo` + 占用校验 → 产品 enrich → Asm（**不写库**）。

---

### 3.15 IF-WHS-PO-14 `pagePurchaseWarningProducts`（预警快捷选品查询）

**HTTP**：`POST /api/app/inventoryWarehousePurchaseOrder/pagePurchaseWarningProducts`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 目的药库门禁 |
| `productTableName` | String | Y | 表头项目类型对应表名 |
| `warningFilter` | String | Y | `expire` / `lowStock` / `all` |
| `keyword` | String | N | 名称/编码/拼音 |
| `pageNo` / `pageSize` | Integer | Y | 分页 |

**Response**：`PagedResult<PurchaseWarningProductRowDto>`（品种维；含 `hitExpire`/`hitLowStock`/`latestExpirationDate`/`remainingDays`/`hospitalWidePackageQty`）

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 药库 / 表名 / `warningFilter` | **BR-013** |
| 2 | Pre | `PurchaseOrderWarningProductResolver#resolveWarningProductHits` | 内部调效期 QS + 全院汇总；低储仅低于最低 |
| 3 | Pre | `ProductDefinitionDisplayTerminologyAssembler#loadAsMap` | 滤启用 |
| 4 | Asm | `InventoryWarehousePurchaseOrderMapper#toPagePurchaseWarningProductsResponse` | 内存分页；**不写库** |

> **WHS-SDD-R-005**：禁止复用库存明细 `warningMode` 过滤器。

## 4. QueryService 与事件刷新

| QueryService | 订阅事件（节选） | 更新意图 |
| --- | --- | --- |
| **列表读模型（应用层直连 QueryService）** | 同上事件（可选投影） | **IF-WHS-PO-02** 以 **`SupplyRequestDocumentListQuery`** 实时查询为主，不依赖行级 **`WarehouseDocumentDetail`** |
| **ProductDefinition** | （目录侧事件，不在本文展开） | 选品检索 |
| **一致性** | 写接口返回成功不代表QueryService已同步展示 | **最终一致**；前端可在写成功后刷新列表 |

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（与 §3 Cmd 序号对照）

| IF | App 方法 | Cmd | `SupplyRequestDomainService` 方法 | 聚合写入 |
| --- | --- | --- | --- | --- |
| IF-WHS-PO-04 | `createPurchaseOrder` | 1 | `createPurchaseOrder` | `wkf_supply_request` 插入多行 |
| IF-WHS-PO-05 | `updatePurchaseOrder` | 1 | `createPurchaseOrder`（重入 `busNo`） | 同行（全量明细对齐） |
| IF-WHS-PO-06 | `submitPurchaseOrderApproval` | 1 | `submitApproval` | 批量更新状态 |
| IF-WHS-PO-07 | `withdrawPurchaseOrderApproval` | 1 | `withdrawApproval` | 批量更新状态 |
| IF-WHS-PO-08 | `agreePurchaseOrder` | 1 | `agreeRequest` | 批量更新 AGREE |
| IF-WHS-PO-09 | `rejectPurchaseOrder` | 1 | `rejectRequest` | 批量更新 REJECT |
| IF-WHS-PO-10 | `removePurchaseOrder` | 1 | `removeByBusNo` | 逻辑删除 |

### 5.2 事务边界 / 失败回滚

| 接口 | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-WHS-PO-04～10 | 单事务：`Cmd` 失败则整单回滚 | 领域事件 `beforeCommit` 未触发则一同回滚 |
| IF-WHS-PO-08 | **禁止**在本事务追加 `createGoodsReceipt` | 与 **§1.3** 一致 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 审批链路 | `submit`/`agree`/`reject` 重复提交：依赖领域侧状态机拒绝并返回业务码 | `SupplyRequestDomainService` | §3.7～§3.9 |
| 新建 | `createPurchaseOrder`：客户端 `Idempotency-Key`（可选）由网关或应用层缓存 24h | `InventoryWarehousePurchaseOrderAppService` | §3.5 |

### 6.2 并发控制

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 编辑冲突 | `UpdatePurchaseOrderRequest.rowVersion` **必填**；领域 **`expectedRowVersionForRevision`** 校验 | §3.6 |

### 6.3 重试与容错

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| QueryService延迟 | 写成功后可提示前端延迟刷新 | §4 |

### 6.4 数据脱敏

| 项 | 承诺 |
| --- | --- |
| 审计可见性 | 审批人、申请人 ID 可对外展示；手机号等不在本接口出现 |

### 6.5 审计日志

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 审批轨迹 | `approverId`、`approvalTime`、`rejectReason` 落领域聚合字段 | §3.9～§3.10 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 新建订货 → 列表可见 | API 集成 | P0 |
| 编辑订货（待审核）→ 明细变更 | API 集成 | P0 |
| 提交/撤回/同意/驳回 状态链 | API 集成 | P0 |
| 同意**不**创建进货单（DB 无 type=2 新单） | API + DB 断言 | P0 |
| 预警：最晚效期批近效 / 仅低储不含超储 / 表头药耗 | API 集成 | P0 |
| 预警勾选 → IF-11 ids → 手填数量 | API 集成 | P0 |

### 7.2 非功能测试

| 用例 | 优先级 |
| --- | --- |
| 分页查询响应时间（经验目标 <1s） | P1 |

### 7.3 QueryService一致性

| 用例 | 优先级 |
| --- | --- |
| 领域事件载荷后列表状态与写模型一致（异步容忍） | P1 |

### 7.4 集成测试

| 用例 | 优先级 |
| --- | --- |
| 与 **Location**/**Supplier** 主数据联通 | P0 |

### 7.5 测试缺口跟踪

| ID | 缺口 | 关闭条件 |
| --- | --- | --- |
| — | 无 | — |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.0.2 | 2026-07-15 | **IF-WHS-PO-01** 增补 **`departmentPharmacyLocationOptions`**（`listAllActiveDepartmentPharmacies`，关联申请弹窗）；对齐应用落地 |
| v2.0.1 | 2026-07-15 | **GAP-WHS-PO-001** 关闭；**IF-14 / IF-11 ids** 应用落地；DI 对齐 **`ProductDefinitionDisplayTerminologyAssembler`**；对齐 **AS-INV-WHS-001 v0.9.1** |
| v1.9 | 2026-07-15 | 领域 Gap 关闭：订货 **`originalBusNo`** / **`WHS_PO_106`**；枚举 **DEPT_PURCHASE_REQUEST** 已落地；对齐 **AS-INV-WHS-001 v0.8** |
| v1.8 | 2026-07-15 | **可选拉科室采购申请**：**IF-WHS-PO-12/13**；建单 **`originalBusNo`**；**WHS-SDD-R-004**；失败码 **`WHS_PO_106`**；对齐 **AS-INV-WHS-001 v0.7** / **AS-INV-DEP-007** |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.7 | 2026-06-30 | **W-01 与代码对齐**：init **`OptionDto`**（`warehouseLocationOptions` 首项「全部」）；列表 **`busNo` 聚合** + **`PurchaseOrderListRowDto`**；删除 **`WarehouseOptionDto`/`SupplierOptionDto`/`WarehouseDocumentDetailRowDto`**；详情/修订 **`rowVersion`** 必填；新建/列表移除请求 **`typeEnum`**；Query 改 **`SupplyRequestDocumentListQuery`** 三件套 |
| v1.6 | 2026-06-30 | **对齐进货同构**：**IF-WHS-PO-01** 增库管员/状态/物品表选项；**IF-WHS-PO-11** 必填 **`purposeLocationId`**、选品富化 **PURCHASE+unitBands**；列表/详情 **`typeEnum`/`statusEnum` 改持久化 code** |
| v1.5 | 2026-05-24 | **药库门控对齐库存统一口径**：以 **`LocationQueryService#listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`** 替代 **`listWarehousesByOrganization`** 作为 **IF-WHS-PO-02/04** Gate 与 **IF-WHS-PO-01** **`warehouseOptions`** 收窄来源；**剔除**组织机构列表查库作门控；**`CurrentManagedLocationIdsResolver`**，**管辖 empty 过渡态不收窄**；§2.1、§2.2、§3.2、§3.3、§3.5 |
| v1.4 | 2026-05-14 | **WHS-SDD-R-003**：`bus_no` 序号键改为 **`busNoSeqKey`**（**`CPO`** 等），**不含 `SQ`**；回退 **`SR`**；对齐 **AS-INV-WHS-001 v0.4** |
| v1.3 | 2026-05-14 | **§1.3 WHS-SDD-R-003**：新建 **`bus_no`** 按 **`SQ_` + `SupplyType#getCode()`** 分桶；对齐 **AS-INV-WHS-001 v0.3** |
| v1.2 | 2026-05-13 | 与 **`DM-WKF-006` v1.1** 对齐：`type`/`status`/`category`/`source_type`/`purpose_type` 契约统一为 **`String` + `getCode()`**；分页字段 **`pageNo`**；去除 **`sortOrder`/数字** 作为单据状态说明；边界 **`type_enum`=`CABINET_PURCHASE_ORDER`**；§2.3/编排表中的 **`typeEnum=1`** 等改为 **code** |
| v1.0 | 2026-05-02 | 初版：对齐 **AS-INV-WHS-001** 全 IF；裁决「同意后不在本服务建进货单」；§2.3 领域枚举 **目标态待生成** FQCN；IF-WHS-PO-05 采用 `createPurchaseOrder`+`busNo` 重入（**WHS-SDD-R-002**） |
| v1.1 | 2026-05-03 | **IF-WHS-PO-02** 列表请求字段更名为 **`purposeLocationId`**（映射 `purpose_location_id`）；**IF-WHS-PO-04/05** 移除无效 **`sourceLocationId`** 请求字段；Gate 白名单与 `WarehouseDocumentDetailPageQuery` 装配描述同步 |

---

**文档负责人**：（待填） · **评审**：（待填）
