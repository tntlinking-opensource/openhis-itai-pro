# 科室药房领用单 · 应用服务详细设计（SDD）

> 编码：AS-INV-DEP-004-SDD  
> 版本：v0.6 | 日期：2026-08-31

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 科室药房领用单（**`DEPT_ISSUE`**，`SupplyType#DEPT_ISSUE`） |
| 边界 | 选品/明细与 **`InventoryDepartmentTransferOutAppService`** 同构；落库为 **单库房 `purpose_*`**；agree **仅扣减**（无调拨/壳单/临床发放） |
| 编排权威 | `AS-INV-DEP-004-科室领用单.md` |
| 方法权威 | `DM-WKF-006` **`createDeptIssue`**（目标态）；`DM-WKF-005` **`createSupplyDelivery`**；`DM-WKF-008` **`decreaseQuantity`** |
| 严格对齐 | IF **IF-DEP-IS-01～11** |
| **实现阶段口径** | **目标态全量契约**（领域 **`createDeptIssue`** 与应用包 **`inventoryDepartmentDeptIssue`** 均已落地） |

### 1.1 术语

| 项 | 约定 |
| --- | --- |
| **`bus_no`** | 序号键 **`DEI`** |
| 操作库位 | **`purposeLocationId`**；形态 **`LocationForm.DEPARTMENT_PHARMACY`**；**禁止**写入 orgId |
| 领用科室 | **`consumingOrganizationId`** 建改单**必填**，落库 **`wkf_supply_request.consuming_organization_id`**（Flyway **health-final V111**）；读侧 **`consumingOrganizationId`/`consumingOrganizationName`**；init 下拉 **`organizationOptions`**（全启用行政科室）；历史列空读侧回落 **`Location.organization_id`** |
| 列表「全部」 | **`purposeLocationId=0`** → `InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID` |
| 同意库存 | 仅 **`decreaseQuantity`**；**禁止** `increaseQuantity` / `createTransfer` |
| 审批 | **`PractitionerRoleScopeLoader`** + **`ROLE_PHARMACIST`**；**不**调用 `requiresFinanceApproval` |
| 详情槽价 | **BR-PRICE-SRC-001**：仅 **`SupplyRequest.price`** |

### 1.2 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **DEP-IS-SDD-R-001** | 建单/改单走 **`createDeptIssue`**；首行带 `busNo` 表示同号修订（对齐调出 `createTransfer` 修订策略） |
| **DEP-IS-SDD-R-002** | **`source_location_id` / `source_type_enum` 恒 NULL**；HTTP 请求体不传 `sourceLocationId` |
| **DEP-IS-SDD-R-003** | init 增 **`organizationOptions`**（`OrganizationQueryService#getOrganizationTree`，筛选 `OrganizationType#DEPARTMENT`）；科室药房 OptionDto 仍仅 `value`/`label`（可选保留 `organizationId`/`organizationName` 作参考，**非**领用科室真源） |
| **DEP-IS-SDD-R-003a** | 建改单 **`consumingOrganizationId` 必填**；Mapper → **`CreateDeptIssueCommand`** 写 **`consumingOrganizationId`**；Gate **`assertConsumingOrganizationGate`** 校验启用行政科室 |
| **DEP-IS-SDD-R-004** | agree：**`createSupplyDelivery`**（非 `createStocktakingDelivery`）；逐行 `decreaseQuantity(allowNegative=false)` |
| **DEP-IS-SDD-R-005** | 追溯：`trace_no` 非空 → **`TraceNumberRecordOperationType.supplyTypeCode(DEPT_ISSUE)`** |
| **DEP-IS-SDD-R-006** | 选品参数名 **`purposeLocationId`**（非调出侧 `sourceLocationId`） |

## 2. 代码结构

```text
com/openhis/health/application/inventory/department/deptissue/
  service/InventoryDepartmentDeptIssueAppService.java
  request/   LoadDeptIssueFormInitRequest、PageDeptIssuesRequest、CreateDeptIssueRequest …
  response/
  dto/       DeptIssueListRowDto、DeptIssueLineDto、DeptIssueDetailLineDto、ProductDefinitionItemDto
  mapper/    InventoryDepartmentDeptIssueMapper.java
             InventoryDepartmentDeptIssueMapperImpl.java
```

### 2.1 依赖注入

| 依赖 | 用途 |
| --- | --- |
| `LocationQueryService#listAllActiveDepartmentPharmacies` | IF-DEP-IS-01/02/04/11 科室药房 |
| `OrganizationQueryService#getOrganizationTree` / `#loadOrganizationByIdForDisplay` | IF-DEP-IS-01 **`organizationOptions`**；建改单 Gate / 详情 enrich |
| `CurrentManagedLocationIdsResolver` | 管辖 Gate + `InventoryDepartmentConstants` |
| `CurrentPractitionerResolver` | 操作人 |
| `PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` | init 经手人（全体在职）；agree 岗位门禁仍用 **`ROLE_PHARMACIST`** |
| `SupplyRequestDomainService#createDeptIssue` | IF-DEP-IS-04/05（**目标态**） |
| `SupplyRequestDomainService#agreeRequest` / `#submitApproval` / `#withdrawApproval` / `#rejectRequest` / `#removeByBusNo` | IF-DEP-IS-06～10 |
| `SupplyRequestQueryService` | 列表/详情/校验 |
| `SupplyDeliveryDomainService#createSupplyDelivery` | IF-DEP-IS-08 |
| `SupplyDeliveryQueryService#findRequestIdsHavingCompletedDelivery` | agree 幂等 |
| `InventoryItemDomainService#decreaseQuantity` | IF-DEP-IS-08 |
| `InventoryItemQueryService` | IF-DEP-IS-11 选品与可用量 |
| `ProductDefinitionBatchLoader` / `ProductDefinitionSelectionTerminologysupport | 选品/详情 enrich |
| `ChargeItemDefinitionQueryService` | 选品槽价 `LOT_NUMBER_COST` |
| `TraceNumberDomainService#recordTraceRecords` | IF-DEP-IS-08 条件追溯 |
| `PageWindowGate` | 分页门禁 |

### 2.2 领域方法白名单

| 聚合服务 | 方法 | 用于 IF |
|----------|------|---------|
| `SupplyRequestDomainService` | `createDeptIssue(CreateDeptIssueCommand)` | 04/05 |
| `SupplyRequestDomainService` | `submitApproval` / `withdrawApproval` / `agreeRequest` / `rejectRequest` / `removeByBusNo` | 06～10 |
| `SupplyDeliveryDomainService` | `createSupplyDelivery` | 08 |
| `InventoryItemDomainService` | `decreaseQuantity` | 08 |
| `TraceNumberDomainService` | `recordTraceRecords` | 08 |
| `SupplyRequestQueryService` | `getSupplyByBusNo` / `countDistinctBusNo` / `supplyRequestValidation` / `verifyAbleEdit` | 02～05/08 |
| `InventoryItemQueryService` | `queryInventoryItemsByPage` / `findAvailableCatalogRowsForOrder` | 11 |
| `LocationQueryService` | `listAllActiveDepartmentPharmacies` / `requireSupplyRequestTypeForLocationId` | 01～05/11 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| 单据类型 | `com.zxplat.domain.share.enums.SupplyType#DEPT_ISSUE` | `DM-WKF-006` §4.1.4 value **29** | 已实现 |
| 单据状态 | `com.zxplat.domain.share.enums.SupplyStatus` | `DM-WKF-006` §2.3 / §4.2 | 审批链 |
| 库位形态 | `com.zxplat.domain.share.enums.LocationForm#DEPARTMENT_PHARMACY` | Location 主数据 | 门控 |
| 项目类型 | `TableNameConstants.MED_MEDICATION_DEFINITION` / `ADM_DEVICE_DEFINITION` | `DM-WKF-006` §3.1 `item_table` | 选品白名单 |
| 追溯操作类型 | `TraceNumberRecordOperationType.supplyTypeCode(DEPT_ISSUE)` | `DM-CUS-010` §4.4 | 与 SupplyType 同码 |
| 列表占位 | `InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID` | 科室线列表惯例 | `purposeLocationId=0` |
| 药师岗位 | `WhalePlatformRoleCodeConstants.ROLE_PHARMACIST` | AS BR-DEP-IS-006 | agree 门禁 |

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/inventoryDepartmentDeptIssue/{methodName}`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-DEP-IS-01 | `loadDeptIssueFormInit` | 加载页主数据 | `POST /api/app/inventoryDepartmentDeptIssue/loadDeptIssueFormInit` |
| IF-DEP-IS-02 | `pageDeptIssues` | 分页列表 | `POST /api/app/inventoryDepartmentDeptIssue/pageDeptIssues` |
| IF-DEP-IS-03 | `getDeptIssueDetail` | 单据详情 | `POST /api/app/inventoryDepartmentDeptIssue/getDeptIssueDetail` |
| IF-DEP-IS-04 | `createDeptIssue` | 新建领用单 | `POST /api/app/inventoryDepartmentDeptIssue/createDeptIssue` |
| IF-DEP-IS-05 | `updateDeptIssue` | 编辑领用单 | `POST /api/app/inventoryDepartmentDeptIssue/updateDeptIssue` |
| IF-DEP-IS-06 | `submitDeptIssueApproval` | 提交审批 | `POST /api/app/inventoryDepartmentDeptIssue/submitDeptIssueApproval` |
| IF-DEP-IS-07 | `withdrawDeptIssueApproval` | 撤回审批 | `POST /api/app/inventoryDepartmentDeptIssue/withdrawDeptIssueApproval` |
| IF-DEP-IS-08 | `agreeDeptIssue` | 同意领用 | `POST /api/app/inventoryDepartmentDeptIssue/agreeDeptIssue` |
| IF-DEP-IS-09 | `rejectDeptIssue` | 驳回 | `POST /api/app/inventoryDepartmentDeptIssue/rejectDeptIssue` |
| IF-DEP-IS-10 | `removeDeptIssue` | 删除 | `POST /api/app/inventoryDepartmentDeptIssue/removeDeptIssue` |
| IF-DEP-IS-11 | `pageProductsForDeptIssue` | 选品分页 | `POST /api/app/inventoryDepartmentDeptIssue/pageProductsForDeptIssue` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 参数 / 机构 / 库位管辖 / 药师岗位 | 不再调用后续 |
| **Pre** | QueryService 只读门禁 | 抛业务异常，禁止 Cmd |
| **Cmd** | DomainService 写 | 事务回滚 |
| **Asm** | Mapper DTO 装配 | — |

### 3.2 IF-DEP-IS-01 `loadDeptIssueFormInit`（加载页主数据）

**LoadDeptIssueFormInitRequest**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| — | — | — | 可为空体 |

**LoadDeptIssueFormInitResponse**

| 字段 | 类型 | 说明 |
|------|------|------|
| `departmentPharmacyOptions` | `List<DeptPharmacyOptionDto>` | 科室药房；含 `value`/`label` |
| `organizationOptions` | `List<OptionDto>` | **全启用行政科室**（领用科室手选） |
| `productTableOptions` | `List<OptionDto>` | 药品 / 耗材 |
| `statusOptions` | `List<OptionDto>` | SupplyStatus |
| `pharmacistOptions` | `List<OptionDto>` | 经手人：全体在职医务人员（含 **`pyStr`/`wbStr`**） |

**DeptPharmacyOptionDto**

| 字段 | 类型 | 说明 |
|------|------|------|
| `value` | String | locationId |
| `label` | String | 科室药房名 |
| `organizationId` | String | （可选）科室药房上级科室 ID，**非**领用科室落库真源 |
| `organizationName` | String | （可选）上级科室名 |

**失败码**：`DEP_IS_101`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | `CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId` | 机构 |
| 2 | Pre | `LocationQueryService#listAllActiveDepartmentPharmacies` | 启用科室药房 |
| 3 | Pre | `CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged` | 管辖 Gate |
| 4 | Pre | `OrganizationQueryService#getOrganizationTree` → 行政科室 Option | **`organizationOptions`** |
| 5 | Pre | `PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` | 经手人（全体在职；含拼音码） |
| 6 | Asm | `InventoryDepartmentDeptIssueMapper#toLoadDeptIssueFormInitResponse` | 含组织名 enrich |

### 3.3 IF-DEP-IS-02 `pageDeptIssues`（分页列表）

**PageDeptIssuesRequest**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `purposeLocationId` | Long | Y | `0`=全部 |
| `statusEnum` | String | N | SupplyStatus code |
| `applyTimeFrom` / `applyTimeTo` | DateTime | N | 申请时间窗 |
| `keyword` | String | N | bus_no 等 |
| `pageNo` / `pageSize` | Integer | Y | 分页 |

**PageDeptIssuesResponse**（继承 `PagedResult<DeptIssueListRowDto>`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `total` | Long | 合并 bus_no 后总数 |
| `pageNo` / `pageSize` | Integer | 分页元数据（1-based） |
| `content` | `List<DeptIssueListRowDto>` | 列表行 |

**DeptIssueListRowDto**

| 字段 | 类型 | 说明 |
|------|------|------|
| `busNo` | String | 单号 |
| `purposeLocationId` | String | 科室药房 |
| `purposeLocationName` | String | 科室药房名 |
| `consumingOrganizationId` | String | 领用科室 |
| `consumingOrganizationName` | String | 领用科室名 |
| `statusEnum` / `statusEnumText` | String | 状态 |
| `applicantName` / `applyTime` | — | 制单 |
| `approverName` / `approvalTime` | — | 审批 |
| `totalAmount` | BigDecimal | 整单金额（明细行 `totalPrice` 之和；侧栏「总金额」） |

**编排序列**：Gate 分页 → Pre `SupplyRequestDocumentListQuery`（`typeEnum=DEPT_ISSUE`，`purposeLocationId` 或 IN 列表）→ `countDistinctBusNo` + `listByBusNosWithFilter` → Asm 聚合 enrich **领用科室名**（优先落库 id，历史回落）。

**失败码**：`DEP_IS_001` / `DEP_IS_002` / `DEP_IS_102`

### 3.4 IF-DEP-IS-03 `getDeptIssueDetail`（单据详情）

**GetDeptIssueDetailRequest**：`busNo`（Y）

**GetDeptIssueDetailResponse**：头字段 + `lines: List<DeptIssueDetailLineDto>`

**DeptIssueDetailLineDto**（与调出/请领明细列族同构，数量字段为领用量）

| 字段 | 类型 | 说明 |
|------|------|------|
| `itemTable` / `itemId` | String/Long | 物品 |
| `itemName` / `spec` / `manufacturer` | String | 展示（产品读模型 enrich） |
| `lotNumber` / `traceNo` | String | 批号/追溯 |
| `itemQuantity` | BigDecimal | 领用数量（拆零） |
| `unitCode` / `unitName` | String | 单位 |
| `price` / `totalPrice` | BigDecimal | 落库槽价 |
| `endTime` | LocalDateTime | 批号效期止点（落库 `end_time`；前端「有效期」） |
| `remake` | String | 行备注（落库 `remake`；前端「备注」） |
| `productCatalog` | Object | 多单位目录提示 |

**前端明细网格列（与请领对齐）**：项目名称、规格、厂家、数量、金额、批号、有效期、追溯码、备注、操作。

**编排序列**：Gate `busNo` → Pre `getSupplyByBusNo` + `assertDeptIssue` → Asm enrich 产品/组织/库位名。

**失败码**：`DEP_IS_103`

### 3.5 IF-DEP-IS-04 `createDeptIssue`（新建领用单）

**CreateDeptIssueRequest**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `purposeLocationId` | Long | Y | 科室药房 |
| `consumingOrganizationId` | Long | Y | 领用科室（行政 Organization） |
| `practitionerId` | Long | N | 经办人 |
| `reason` / `remake` | String | N | 原因/头备注（头 `remake` 仅作行备注缺省回落） |
| `lines` | `List<DeptIssueLineDto>` | Y | 明细 |

**DeptIssueLineDto**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `itemTable` | String | Y | 药品/耗材表 |
| `itemId` | Long | Y | 物品 ID |
| `lotNumber` | String | N | 批号 |
| `traceNo` | String | N | 追溯码 |
| `itemQuantity` | BigDecimal | Y | >0 |
| `unitCode` | String | Y | 单位 |
| `price` | BigDecimal | N | 槽价 |
| `endTime` | LocalDateTime | N | 批号效期（选品 `expirationDate`） |
| `remake` | String | N | 行备注 |

**CreateDeptIssueResponse**：`busNo`

**关键校验**：**`consumingOrganizationId` 非空**且为启用行政科室；`source_*` 不写；可用量；禁止 `originalBusNo`

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数非空 | |
| 2 | Pre | 科室药房门控 + **`assertConsumingOrganizationGate`** | DEP-IS-SDD-R-003a |
| 3 | Pre | 草稿行可用量校验 | 同调出 |
| 4 | Cmd | `SupplyRequestDomainService#createDeptIssue(CreateDeptIssueCommand)` | **写库点**；`type_enum=DEPT_ISSUE` |
| 5 | Asm | `toCreateDeptIssueResponse` | 返回 busNo |

**失败码**：`DEP_IS_001` / `DEP_IS_002`

### 3.6 IF-DEP-IS-05 `updateDeptIssue`（编辑领用单）

复用 **IF-DEP-IS-04** 字段；`UpdateDeptIssueRequest` 增 `busNo`（Y）。

**编排序列**：Pre `verifyAbleEdit` → 同 04 校验 → `createDeptIssue` 首行带 `busNo` 修订。

**失败码**：`DEP_IS_004`

### 3.7 IF-DEP-IS-06～07 / 09 审批流转

| IF | Request | Cmd |
|----|---------|-----|
| 06 | `SubmitDeptIssueApprovalRequest.busNo` | `submitApproval` |
| 07 | `WithdrawDeptIssueApprovalRequest.busNo` | `withdrawApproval` |
| 09 | `RejectDeptIssueRequest.busNo` | `rejectRequest` |

**失败码**：`DEP_IS_003`

### 3.8 IF-DEP-IS-08 `agreeDeptIssue`（同意领用）

**AgreeDeptIssueRequest**：`busNo`（Y）

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 操作人 + **药师岗位** | 非财务岗 |
| 2 | Pre | `getSupplyByBusNo` + `assertDeptIssue` | |
| 3 | Pre | `supplyRequestValidation` | 防重复同意 |
| 4 | Pre | `findRequestIdsHavingCompletedDelivery` | 发放幂等 |
| 5 | Cmd | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` | **写库点 1** |
| 6 | Pre | `flushPendingChanges` + 回读 AGREE | |
| 7 | Cmd | `SupplyDeliveryDomainService#createSupplyDelivery(CreateSupplyDeliveryCommand)` | **写库点 2** |
| 8 | Cmd | 逐行 `InventoryItemDomainService#decreaseQuantity(DecreaseQuantityCommand)` | **写库点 3**；`locationId=purpose_location_id` |
| 9 | Cmd | `TraceNumberDomainService#recordTraceRecords` | `trace_no` 非空；**DEP-IS-SDD-R-005** |

**失败码**：`DEP_IS_104` / `DEP_IS_105`

### 3.9 IF-DEP-IS-10 `removeDeptIssue`（删除）

`removeByBusNo`；失败 `DEP_IS_005`

### 3.10 IF-DEP-IS-11 `pageProductsForDeptIssue`（选品分页）

**PageProductsForDeptIssueRequest**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `purposeLocationId` | Long | Y | 科室药房 |
| `productTableName` | String | Y | 药品/耗材表 |
| `positiveQuantityOnly` | Boolean | N | **`true`**：仅该库账面 **`quantity > 0`**；**`false`/未传：不过滤 |
| `pageNo` / `pageSize` | Integer | Y | 分页 |
| `keyword` | String | N | 选品关键字 |

**PageProductsForDeptIssueResponse**（继承 `PagedResult<ProductDefinitionItemDto>`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `total` | Long | 选品总数 |
| `pageNo` / `pageSize` | Integer | 分页元数据（1-based） |
| `content` | `List<ProductDefinitionItemDto>` | 选品行（可用量、批号、槽价等，同科室调出选品） |

**编排序列**：对齐 `InventoryDepartmentTransferOutAppService#pageProductsForTransferOut`，将 `sourceLocationId` 替换为 **`purposeLocationId`**。

**失败码**：`DEP_IS_002` / `DEP_IS_102`

**Mapper 签名（示例）**

- `toCreateDeptIssueCommand(CreateDeptIssueRequest, Long operatorId, Long applicantId, String busNo): CreateDeptIssueCommand`
- `toCreateSupplyDeliveryCommand(List<SupplyRequestModel>, Long operatorId): CreateSupplyDeliveryCommand`
- `toLoadDeptIssueFormInitResponse(...): LoadDeptIssueFormInitResponse`

## 4. QueryService 与领域事件

| 写侧 | 读侧刷新 |
|------|----------|
| `SupplyRequestAgreedEvent` | 列表/详情状态；库存查询下次读最新 |
| `createSupplyDelivery` 完成 | `SupplyDeliveryQueryService` 可判定发放完成态 |

同意前所有读 IF **仅读** QueryService，禁止直查 Repository。

## 5. 写库顺序（IF-DEP-IS-08）

| 顺序 | Cmd 序号 | 调用 | 聚合 |
|------|----------|------|------|
| 1 | §3.8-5 | `agreeRequest` | SupplyRequest 状态 |
| 2 | §3.8-7 | `createSupplyDelivery` | SupplyDelivery |
| 3 | §3.8-8 | `decreaseQuantity` × N | InventoryItem |
| 4 | §3.8-9 | `recordTraceRecords` | TraceNumberRecord（条件） |

**事务**：默认 **单事务**；任一步失败整单回滚。

**失败回滚**：状态不得停留 AGREE 而无发放/库存（与科室损益 agree 同构）。

## 6. 非功能性需求实现

### 6.1 幂等性

`supplyRequestValidation` + `findRequestIdsHavingCompletedDelivery`（§3.8 序号 3～4）

### 6.2 并发控制

`OptimisticLockingFailureException` → `DEP_IS_105`（§3.8）

### 6.3 权限

`CurrentManagedLocationIdsResolver` + 药师岗位（§3.8 序号 1）

### 6.4 审计

`AgreeRequestCommand` 写 `approverId` / `approvalTime`

### 6.5 数据脱敏

无患者字段；不涉及

## 7. 测试要点

| 层级 | 场景 | 优先级 |
|------|------|--------|
| 功能 | 药品/耗材领用 agree 扣库 | P0 |
| 功能 | **`consumingOrganizationId` 缺失拒绝建单** | P0 |
| 功能 | 落库 **`consuming_organization_id`** 与手选一致 | P0 |
| 功能 | `source_*` 持久化为 NULL | P0 |
| 功能 | 无调入壳单 / 无 increase | P0 |
| 功能 | 药师可审；非药师拒绝 | P1 |
| 集成 | 追溯 `DEPT_ISSUE` operation_type | P1 |
| 并发 | 双 agree | P1 |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.6 | 2026-08-31 | **IF-DEP-IS-01**：经手人下拉改为全体在职医务人员（含拼音）；agree 仍须 **`ROLE_PHARMACIST`** |
| v0.5 | 2026-07-26 | **领用科室解耦**：init **`organizationOptions`**；建改单必填 **`consumingOrganizationId`** → 落库 **`consuming_organization_id`**；读侧历史回落 **`Location.organization_id`** |
| v0.4 | 2026-07-26 | 列表聚合增 **`totalPrice`/`totalAmount`**（侧栏总金额） |
| v0.3 | 2026-07-26 | 明细列对齐请领：`DeptIssueDetailLineDto`/`DeptIssueLineDto` 增 **`endTime`/`remake`**；领域 `CreateDeptIssueCommand` 落 `end_time`；前端网格规格/厂家/金额/有效期/备注 |
| v0.2 | 2026-07-01 | 应用层 `inventoryDepartmentDeptIssue` 成码完成；分页响应对齐 `PagedResult#content` |
| v0.1 | 2026-07-01 | 初稿；领域 **`createDeptIssue`** 已落地，应用包待成码 |

---

**文档负责人**：（待填）
