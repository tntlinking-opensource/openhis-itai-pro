# 科室药房退领单 · 应用服务详细设计（SDD）

> 编码：AS-INV-DEP-009-SDD  
> 版本：v0.8 | 日期：2026-08-31

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 科室药房退领单（**`DEPT_ISSUE_RETURN`**，`SupplyType#DEPT_ISSUE_RETURN`） |
| 边界 | 拉取/可退余量对偶 **`InventoryDepartmentReturnToWarehouseAppService`** + **`ReturnToWarehouseStockInLinesupport**；落账对偶 **AS-INV-DEP-004** agree **加回**（非扣减）；**无**壳单 |
| 编排权威 | `AS-INV-DEP-009-科室退领单.md` |
| 方法权威 | `DM-WKF-006` **`createDeptIssueReturn`**（目标态）；`DM-WKF-005` **`createSupplyDelivery`**；`DM-WKF-008` **`increaseQuantity`** / **`createInventoryItem`** |
| 严格对齐 | IF **IF-DEP-TL-01～13**（无 11 自由选品） |
| **实现阶段口径** | 领域层 **已落地**（`createDeptIssueReturn` / `DEPT_ISSUE_RETURN`）；应用层 **`inventoryDepartmentDeptIssueReturn`** **已落地**（`InventoryDepartmentDeptIssueReturnAppService`，IF-DEP-TL-01～10/12/13） |

### 1.1 术语

| 项 | 约定 |
| --- | --- |
| **`bus_no`** | 序号键 **`DTL`** |
| **源单** | `originalBusNo` → **`DEPT_ISSUE`** 且 **`AGREE`** |
| **行键** | `itemTable` + `itemId` + `lotNumber`（规范化批号）；**不含** `traceNo` |
| **可退余量** | 源领用行 `itemQuantity` − Σ已同意退领 − Σ在途退领（同键） |
| **库位** | `purposeLocationId` = 源领用单 `purpose_location_id`；`source_*` NULL |
| **领用科室** | **`consumingOrganizationId`** 自源领用单解析（优先落库，历史回落 `Location.organization_id`），经 Mapper 写入 **`CreateDeptIssueReturnCommand`**；**禁止**客户端请求体覆盖；详情/拉取表头 **只读** |
| **追溯** | 源领用行有 `trace_no` → 退领行**必填同值**；同意 `operation_type`=`DEPT_ISSUE_RETURN`；方向 **入库** |

### 1.2 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **DEP-TL-SDD-R-001** | 建单/改单走 **`createDeptIssueReturn`**；`originalBusNo` **必填** |
| **DEP-TL-SDD-R-002** | 可退余量 Support：**`DeptIssueReturnLinesupport**（对偶 `ReturnToWarehouseStockInLinesupport，源类型 **`DEPT_ISSUE`**） |
| **DEP-TL-SDD-R-003** | 保存校验：明细非空；每行 ≤ 可退余量；行 ⊆ 源领用单品批 |
| **DEP-TL-SDD-R-004** | agree：**`increaseQuantity`**；四元组无行时 **`createInventoryItem`**（对偶损益报溢） |
| **DEP-TL-SDD-R-005** | **无** `pageProducts*` 接口 |
| **DEP-TL-SDD-R-006** | 建改单 **`consumingOrganizationId`** 由 AppService 自源领用单表头解析传入 Mapper；HTTP 请求体**不含**该字段 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/department/deptissuereturn/
  service/InventoryDepartmentDeptIssueReturnAppService.java
  support/DeptIssueReturnLineSupport.java
  request/   LoadDeptIssueReturnFormInitRequest、PageDeptIssueReturnsRequest …
  response/
  dto/       DeptIssueReturnListRowDto、DeptIssueReturnLineDto、LinkableIssueListRowDto …
  mapper/    InventoryDepartmentDeptIssueReturnMapper.java
             InventoryDepartmentDeptIssueReturnMapperImpl.java
```

### 2.1 依赖注入

| 依赖 | 用途 |
| --- | --- |
| `LocationQueryService#listAllActiveDepartmentPharmacies` | IF-DEP-TL-01/02/12 门控 |
| `CurrentManagedLocationIdsResolver` | 管辖 Gate + `InventoryDepartmentConstants` |
| `SupplyRequestDomainService#createDeptIssueReturn` | IF-DEP-TL-04/05（**目标态**） |
| `SupplyRequestQueryService#getSupplyByBusNo` / `getSupplyByOriginalBusNo` | 源领用、已退占用 |
| `DeptIssueReturnLinesupport | 可退余量、拉取预填、保存前校验 |
| `SupplyDeliveryDomainService#createSupplyDelivery` | IF-DEP-TL-08 |
| `InventoryItemDomainService#increaseQuantity` / `#createInventoryItem` | IF-DEP-TL-08 |
| `TraceNumberDomainService#recordTraceRecords` | IF-DEP-TL-08 |
| `PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` | init 经手人（全体在职）；agree 药师岗位门禁另解析 **`ROLE_PHARMACIST`** |

### 2.2 领域方法白名单

| 聚合服务 | 方法 | 用于 IF |
|----------|------|---------|
| `SupplyRequestDomainService` | `createDeptIssueReturn` | 04/05 |
| `SupplyRequestDomainService` | `submitApproval` / `withdrawApproval` / `agreeRequest` / `rejectRequest` / `removeByBusNo` | 06～10 |
| `SupplyDeliveryDomainService` | `createSupplyDelivery` | 08 |
| `InventoryItemDomainService` | `increaseQuantity` / `createInventoryItem` | 08 |
| `TraceNumberDomainService` | `recordTraceRecords` | 08 |
| `SupplyRequestQueryService` | `getSupplyByBusNo` / `countDistinctBusNo` / `supplyRequestValidation` / `verifyAbleEdit` | 02～05/08/12/13 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| 退领类型 | `com.zxplat.domain.share.enums.SupplyType#DEPT_ISSUE_RETURN` | `DM-WKF-006` §4.1.4 value **30** | 领域已落地 |
| 源领用类型 | `SupplyType#DEPT_ISSUE` | `DM-WKF-006` §4.1.4 value **29** | 拉取源 |
| 追溯 | `TraceNumberRecordOperationType.supplyTypeCode(DEPT_ISSUE_RETURN)` | `DM-CUS-010` §4.4 | 领域/DM 已对齐 |
| 列表占位 | `InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID` | 科室线惯例 | `purposeLocationId=0` |

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP**：`POST /api/app/inventoryDepartmentDeptIssueReturn/{methodName}`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-DEP-TL-01 | `loadDeptIssueReturnFormInit` | 加载页主数据 | `POST …/loadDeptIssueReturnFormInit` |
| IF-DEP-TL-02 | `pageDeptIssueReturns` | 退领列表 | `POST …/pageDeptIssueReturns` |
| IF-DEP-TL-03 | `getDeptIssueReturnDetail` | 退领详情 | `POST …/getDeptIssueReturnDetail` |
| IF-DEP-TL-04 | `createDeptIssueReturn` | 新建退领单 | `POST …/createDeptIssueReturn` |
| IF-DEP-TL-05 | `updateDeptIssueReturn` | 编辑退领单 | `POST …/updateDeptIssueReturn` |
| IF-DEP-TL-06 | `submitDeptIssueReturnApproval` | 提交审批 | `POST …/submitDeptIssueReturnApproval` |
| IF-DEP-TL-07 | `withdrawDeptIssueReturnApproval` | 撤回审批 | `POST …/withdrawDeptIssueReturnApproval` |
| IF-DEP-TL-08 | `agreeDeptIssueReturn` | 同意退领 | `POST …/agreeDeptIssueReturn` |
| IF-DEP-TL-09 | `rejectDeptIssueReturn` | 驳回 | `POST …/rejectDeptIssueReturn` |
| IF-DEP-TL-10 | `removeDeptIssueReturn` | 删除 | `POST …/removeDeptIssueReturn` |
| IF-DEP-TL-12 | `pageLinkableIssueDocuments` | 可关联领用单 | `POST …/pageLinkableIssueDocuments` |
| IF-DEP-TL-13 | `getLinkableIssueDetail` | 拉取领用明细 | `POST …/getLinkableIssueDetail` |

**DeptIssueReturnListRowDto（IF-DEP-TL-02）**：聚合行须返回整单金额 **`totalAmount`**（明细行 `totalPrice` 之和，供侧栏「总金额」）。

#### 3.1.1 编排约定

Gate → Pre → Cmd → Asm（同 **AS-INV-DEP-004-SDD** §3.1.1）。

### 3.2 IF-DEP-TL-13 `getLinkableIssueDetail`（拉取领用明细）

**GetLinkableIssueDetailRequest**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `originalBusNo` | String | Y | 源 **`DEPT_ISSUE`** `busNo` |
| `purposeLocationId` | Long | Y | 科室药房（须与源单一致） |

**GetLinkableIssueDetailResponse**

| 字段 | 类型 | 说明 |
|------|------|------|
| `originalBusNo` | String | 源领用单号 |
| `purposeLocationId` | String | 科室药房 |
| `purposeLocationName` | String | 展示 |
| `consumingOrganizationId` | String | 领用科室（只读，自源领用单 **`consuming_organization_id`** 解析） |
| `consumingOrganizationName` | String | 领用科室名（只读） |
| `lines` | `List<LinkableIssueReturnLineDto>` | 可退行 |

**LinkableIssueReturnLineDto**

| 字段 | 类型 | 说明 |
|------|------|------|
| `itemTable` / `itemId` | String/Long | 物品 |
| `lotNumber` | String | 批号（行键） |
| `traceNo` | String | 源领用追溯码；非空则退回必填同值 |
| `issueQuantity` | BigDecimal | 源领用数量 |
| `returnableQuantity` | BigDecimal | 可退余量 |
| `unitCode` | String | 单位 |
| `price` | BigDecimal | 源行落库价 |
| `totalPrice` | BigDecimal | 金额预览（单价 × 可退余量，BR-MON-001） |
| `productName` | String | 产品名称（读模型富化） |
| `specification` | String | 规格展示 |
| `manufacturer` | String | 厂家展示 |
| `unitCodeDictText` | String | 计量单位展示名 |
| `endTime` | LocalDateTime | 源领用行效期止点 |
| `remake` | String | 源领用行备注 |

**前端明细网格列（与领用/请领对齐）**：项目名称、规格、厂家、退领数量、金额、批号、有效期、追溯码、备注、操作。

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数非空 | |
| 2 | Pre | `getSupplyByBusNo` + assert `DEPT_ISSUE` + `AGREE` | |
| 3 | Pre | `DeptIssueReturnLineSupport#buildReturnableLines` | 行键余量；**不含 trace 参与键**；快照带 `endTime`/`remake` |
| 4 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 批量产品读模型 |
| 5 | Asm | `Mapper#toGetLinkableIssueDetailResponse` | **行价保留源领用落库价**；enrich 厂家/金额 |
| 6 | Asm | 库存补齐 `endTime` | 源领用行未落效期时，按科室药房+品批从库存项回填 |

**失败码**：`DEP_TL_001` / `DEP_TL_103`

### 3.3 IF-DEP-TL-04 `createDeptIssueReturn`（新建退领单）

**CreateDeptIssueReturnRequest**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `originalBusNo` | String | Y | 源领用单 |
| `purposeLocationId` | Long | Y | 须与源单一致 |
| `lines` | `List<DeptIssueReturnLineDto>` | Y | **至少一行** |

**DeptIssueReturnLineDto**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `itemTable` | String | Y | 须在源单品批集合内 |
| `itemId` | Long | Y | |
| `lotNumber` | String | N | 行键 |
| `traceNo` | String | 条件 | 源行有码则必填且同码 |
| `itemQuantity` | BigDecimal | Y | ≤ returnable |
| `unitCode` | String | Y | |
| `price` | BigDecimal | N | 默认源行价 |
| `endTime` | LocalDateTime | N | 效期（通常自源领用带入） |
| `remake` | String | N | 行备注 |

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 操作人 | |
| 2 | Pre | 源领用校验 + `DeptIssueReturnLineSupport#assertDraftLinesWithinReturnable` | **DEP-TL-SDD-R-003** |
| 3 | Pre | AppService **`resolveConsumingOrganizationIdFromIssueHead`**（源表头，非客户端） | **DEP-TL-SDD-R-006** |
| 4 | Cmd | `SupplyRequestDomainService#createDeptIssueReturn(CreateDeptIssueReturnCommand)` | **写库点**；含 **`consumingOrganizationId`** |
| 5 | Asm | `toCreateDeptIssueReturnResponse` | |

### 3.4 IF-DEP-TL-08 `agreeDeptIssueReturn`（同意退领）

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 药师岗位 | |
| 2 | Pre | `supplyRequestValidation` + 发放幂等 | |
| 3 | Cmd | `agreeRequest` | **写库点 1** |
| 4 | Cmd | `createSupplyDelivery` | **写库点 2** |
| 5 | Cmd | 逐行 `increaseQuantity` 或 `createInventoryItem` | **写库点 3** |
| 6 | Cmd | `recordTraceRecords`（`trace_no` 非空；**IN**） | `DEPT_ISSUE_RETURN` |

**失败码**：`DEP_TL_104` / `DEP_TL_105`

其余 IF（01/02/03/05～07/09/10/12）结构对偶 **`department/returntowarehouse`** 或 **AS-INV-DEP-004-SDD**，替换类型与字段名即可。

## 4. QueryService 与领域事件

`SupplyRequestAgreedEvent` 后库存读侧刷新；`getSupplyByOriginalBusNo` 用于统计已退占用。

## 5. 写库顺序（IF-DEP-TL-08）

1. `agreeRequest`  
2. `createSupplyDelivery`  
3. `increaseQuantity` / `createInventoryItem` × N  
4. 条件 `recordTraceRecords`  

单事务；失败整单回滚。

## 6. 非功能性需求实现

幂等、并发、权限同 **AS-INV-DEP-004-SDD** §6；**额外**：保存/agree 前须重算可退余量防超退。

## 7. 测试要点

| 层级 | 场景 | 优先级 |
|------|------|--------|
| 功能 | 拉取→部分退→同意加库存 | P0 |
| 功能 | 同一领用单多次退领至余量 0 | P0 |
| 功能 | 超余量 / 加行 / 零行保存拒绝 | P0 |
| 功能 | 源行有追溯须同码 | P1 |
| 回归 | 与 DEPT_RETURN 源单类型隔离 | P1 |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.8 | 2026-08-31 | **IF-DEP-TL-01**：经手人下拉改为全体在职医务人员（含拼音）；agree 仍须药师岗 |
| v0.7 | 2026-07-26 | **领用科室**自源领用单继承落库 **`consuming_organization_id`**；表头科室药房/领用科室只读；**DEP-TL-SDD-R-006** |
| v0.6 | 2026-07-26 | 列表聚合增 **`totalPrice`/`totalAmount`**（侧栏总金额）；详情/拉取/写行效期：源领用优先、库存兜底 |
| v0.5 | 2026-07-26 | 明细列对齐请领：详情/写行增 **`endTime`/`remake`**；**IF-DEP-TL-13** 增 **`manufacturer`/`totalPrice`/`endTime`/`remake`**；领域 `CreateDeptIssueReturnCommand` 落 `end_time` |
| v0.4 | 2026-07-02 | **IF-DEP-TL-13**：`LinkableIssueReturnLineDto` 增补 **`productName`/`specification`/`unitCodeDictText`**；编排序列增 **`ProductDefinitionBatchLoader`**；对齐 **4666f46ae** |
| v0.3 | 2026-07-01 | 应用包 **`InventoryDepartmentDeptIssueReturnAppService`** 落地；§1 实现阶段口径更新 |
| v0.2 | 2026-07-01 | 领域层已落地；AS §3 Gap 关闭；应用包仍待建 |
| v0.1 | 2026-07-01 | 初稿：目标态 `inventoryDepartmentDeptIssueReturn`；Gap 见 AS §3 |

---

**文档负责人**：（待填）
