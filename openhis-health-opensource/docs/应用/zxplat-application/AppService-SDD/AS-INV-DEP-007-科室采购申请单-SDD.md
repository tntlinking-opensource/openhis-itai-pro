# 科室采购申请单 · 应用服务详细设计（SDD）

> 编码：AS-INV-DEP-007-SDD  
> 版本：v0.2 | 日期：2026-07-15

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 科室采购申请单（**`DEPT_PURCHASE_REQUEST`**） |
| 边界 | 建单/修订/审批/删除；**产品定义级选品**（同药库订货）；**不**调用 InventoryItem / SupplyDelivery；**不**调用 `createPurchaseOrder` |
| 编排权威 | `AS-INV-DEP-007-科室采购申请单.md` |
| 方法权威 | **DM-WKF-006** §6.1.1a **`createDeptPurchaseRequest`**；审批共用 `submitApproval` / `withdrawApproval` / `agreeRequest` / `rejectRequest` / `removeByBusNo` |
| 严格对齐 | IF **IF-DEP-CPR-01～11** |
| **实现阶段口径** | **目标态全量契约**（领域 **`createDeptPurchaseRequest`** 已落地；应用包 **`inventoryDepartmentPurchaseRequest`** 待成码） |

### 1.1 术语

| 项 | 约定 |
| --- | --- |
| **`bus_no`** | 序号键 **`DPR`** |
| 操作库位 | **`purposeLocationId`**；形态 **`LocationForm.DEPARTMENT_PHARMACY`** |
| 列表「全部」 | **`purposeLocationId=0`** → `InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID` |
| 审批 | 科室线岗位门禁（与科室请领/领用同源策略）；**不**调用 `requiresFinanceApproval` |
| 选品 | **`ProductDefinitionPurchasePageLoader#pageForPurchase`**（与订货 **IF-WHS-PO-11** 同构） |

### 1.2 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **DEP-CPR-SDD-R-001** | 建单/改单走 **`createDeptPurchaseRequest`**；首行带 `busNo` = 同号修订 |
| **DEP-CPR-SDD-R-002** | **`source_*` / `supplier_id` 恒 NULL**；HTTP 不传 |
| **DEP-CPR-SDD-R-003** | 同意仅 **`agreeRequest`**；**禁止**同事务 `createPurchaseOrder` |
| **DEP-CPR-SDD-R-004** | 详情/修订带 **`rowVersion`**（乐观锁，对偶订货 **WHS_PO_105** → **`DEP_CPR_105`**） |

## 2. 代码结构

```text
com/openhis/health/application/inventory/department/purchaserequest/
  service/InventoryDepartmentPurchaseRequestAppService.java
  request/   LoadDeptPurchaseRequestFormInitRequest、PageDeptPurchaseRequestsRequest、
             GetDeptPurchaseRequestDetailRequest、CreateDeptPurchaseRequestRequest、
             UpdateDeptPurchaseRequestRequest、SubmitDeptPurchaseRequestApprovalRequest、
             WithdrawDeptPurchaseRequestApprovalRequest、AgreeDeptPurchaseRequestRequest、
             RejectDeptPurchaseRequestRequest、RemoveDeptPurchaseRequestRequest、
             PageProductsForDeptPurchaseRequestRequest
  response/  （与上一一对应）
  dto/       DeptPurchaseRequestListRowDto、DeptPurchaseRequestLineDto、
             DeptPurchaseRequestDetailLineDto、ProductDefinitionItemDto
  mapper/    InventoryDepartmentPurchaseRequestMapper.java
             InventoryDepartmentPurchaseRequestMapperImpl.java
```

### 2.1 依赖注入

| 依赖 | 用途 |
| --- | --- |
| `LocationQueryService#listAllActiveDepartmentPharmacies` | IF-01/02/04/11 |
| `CurrentManagedLocationIdsResolver` | 管辖 Gate + 列表占位 |
| `CurrentOperatorResolver` / `CurrentPractitionerResolver` | 操作人 |
| `SupplyRequestDomainService#createDeptPurchaseRequest` | IF-04/05（目标态） |
| `SupplyRequestDomainService` 审批/删除方法 | IF-06～10 |
| `SupplyRequestQueryService` | 列表/详情/`verifyAbleEdit` |
| `ProductDefinitionPurchasePageLoader` / `ProductDefinitionDisplayTerminologyAssembler` | IF-11 选品分页 / IF-03 详情 enrich（`loadAsMap`） |
| `InventoryDepartmentPurchaseRequestMapper` | Asm |
| `PageWindowGate` | 分页门禁 |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `SupplyRequestDomainService` | `createDeptPurchaseRequest` | 04/05 |
| `SupplyRequestDomainService` | `submitApproval` / `withdrawApproval` / `agreeRequest` / `rejectRequest` / `removeByBusNo` | 06～10 |
| `SupplyRequestQueryService` | `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter` | 02 |
| `SupplyRequestQueryService` | `getSupplyByBusNo` / `verifyAbleEdit` / `supplyRequestValidation` | 03～10 |
| `LocationQueryService` | `listAllActiveDepartmentPharmacies` / `requireSupplyRequestTypeForLocationId` | 01/04/11 |
| `ProductDefinitionPurchasePageLoader` | `pageForPurchase` | 11 |
| `ProductDefinitionDisplayTerminologyAssembler` | `loadAsMap` | 03 Asm |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 单据类型 | `com.zxplat.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST` | `DM-WKF-006` §4.1.4 value **31** | 已落地 |
| 单据状态 | `com.zxplat.domain.share.enums.SupplyStatus` | `DM-WKF-006` §4.2 | 审批链 |
| 库位形态 | `LocationForm#DEPARTMENT_PHARMACY` | Location 主数据 | Gate |
| 项目类型 | `CreateDeptPurchaseRequestRequest`/`DeptPurchaseRequestLineDto.itemTable` | `DM-WKF-006` §3.1 `item_table` | 药品/耗材表名 |
| 列表占位 | `InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID` | 科室线列表惯例 | `purposeLocationId=0` |
| `busNoSeqKey` | `SupplyType.DEPT_PURCHASE_REQUEST` → **`DPR`** | `DM-WKF-006` §6.1.1a | 新建单号 |

## 3. 按接口 ID 命名展开

先读完各 IF「领域编排序列」，按序执行；Gate/Pre 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

**HTTP**：`POST /api/app/inventoryDepartmentPurchaseRequest/{methodName}`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-DEP-CPR-01 | `loadDeptPurchaseRequestFormInit` | 加载页初始化 | `POST /api/app/inventoryDepartmentPurchaseRequest/loadDeptPurchaseRequestFormInit` |
| IF-DEP-CPR-02 | `pageDeptPurchaseRequests` | 分页列表 | `POST /api/app/inventoryDepartmentPurchaseRequest/pageDeptPurchaseRequests` |
| IF-DEP-CPR-03 | `getDeptPurchaseRequestDetail` | 单据详情 | `POST /api/app/inventoryDepartmentPurchaseRequest/getDeptPurchaseRequestDetail` |
| IF-DEP-CPR-04 | `createDeptPurchaseRequest` | 新建申请单 | `POST /api/app/inventoryDepartmentPurchaseRequest/createDeptPurchaseRequest` |
| IF-DEP-CPR-05 | `updateDeptPurchaseRequest` | 编辑申请单 | `POST /api/app/inventoryDepartmentPurchaseRequest/updateDeptPurchaseRequest` |
| IF-DEP-CPR-06 | `submitDeptPurchaseRequestApproval` | 提交审批 | `POST /api/app/inventoryDepartmentPurchaseRequest/submitDeptPurchaseRequestApproval` |
| IF-DEP-CPR-07 | `withdrawDeptPurchaseRequestApproval` | 撤回审批 | `POST /api/app/inventoryDepartmentPurchaseRequest/withdrawDeptPurchaseRequestApproval` |
| IF-DEP-CPR-08 | `agreeDeptPurchaseRequest` | 审批同意 | `POST /api/app/inventoryDepartmentPurchaseRequest/agreeDeptPurchaseRequest` |
| IF-DEP-CPR-09 | `rejectDeptPurchaseRequest` | 审批驳回 | `POST /api/app/inventoryDepartmentPurchaseRequest/rejectDeptPurchaseRequest` |
| IF-DEP-CPR-10 | `removeDeptPurchaseRequest` | 删除 | `POST /api/app/inventoryDepartmentPurchaseRequest/removeDeptPurchaseRequest` |
| IF-DEP-CPR-11 | `pageProductsForDeptPurchaseRequest` | 采购选品分页 | `POST /api/app/inventoryDepartmentPurchaseRequest/pageProductsForDeptPurchaseRequest` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 参数门禁 | 停止 |
| **Pre** | 写前只读门禁 | 禁止 Cmd |
| **Cmd** | 领域写 | 事务回滚见 §5 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-DEP-CPR-01 `loadDeptPurchaseRequestFormInit`

**Request**：可空体（机构取会话）。

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `departmentPharmacyLocationOptions` | `List<OptionDto>` | 管辖收窄；首项「全部」=`0` |
| `statusOptions` | `List<OptionDto>` | `SupplyStatus#getCode()` |
| `purchaseItemTableTypeOptions` | `List<OptionDto>` | 药品/耗材表 |

**编排序列**：Gate → `listAllActiveDepartmentPharmacies` → `narrowActiveLocationsByManaged` → Asm。

---

### 3.3 IF-DEP-CPR-02 `pageDeptPurchaseRequests`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | `0`=全部可见科室药房 |
| `statusEnum` | String | N | `SupplyStatus` code |
| `keyword` | String | N | 单号/备注 |
| `applyTimeFrom` / `applyTimeTo` | `LocalDateTime` | N | 申请时间窗 |
| `pageNo` / `pageSize` | Integer | Y | 分页 |

**Response**：`PagedResult<DeptPurchaseRequestListRowDto>`（`busNo` 聚合；含状态文案、制单人、总金额、时间）。

**编排序列**：Gate 门控库位集合 → `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`（`typeEnum=DEPT_PURCHASE_REQUEST`）→ Asm 聚合行。

---

### 3.4 IF-DEP-CPR-03 `getDeptPurchaseRequestDetail`

**Request**：`busNo`（Y）。

**Response**：头字段 + `List<DeptPurchaseRequestDetailLineDto>` + `rowVersion`；明细含品名/规格/厂家/`unitBands`/数量单价总价。

**编排序列**：`getSupplyByBusNo` → 产品 enrich → Asm。失败：不存在 → **DEP_CPR_103**。

---

### 3.5 IF-DEP-CPR-04 `createDeptPurchaseRequest`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | Long | Y | 科室药房 |
| `occurrenceTime` | `LocalDateTime` | Y | 制单时刻 |
| `practitionerId` | Long | N | 经手人 |
| `categoryEnum` | String | N | 默认 `STOCK_SUPPLY` |
| `remake` | String | N | 备注 |
| `lines` | `List<DeptPurchaseRequestLineDto>` | Y | 非空 |

**DeptPurchaseRequestLineDto**：`itemTable`、`itemId`、`itemQuantity`、`unitCode`、`price`、`totalPrice`、`remake`（可选）。

**Response**：`busNo`、`statusEnum`。

**关键校验**：位置 ∈ 管辖科室药房；明细非空（**BR-DEP-CPR-001/002**）。

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | |
| 2 | Pre | Location 门禁 + `requireSupplyRequestTypeForLocationId` | 目的须科室药房 |
| 3 | Cmd | `createDeptPurchaseRequest` | 无 `busNo` 新建 |
| 4 | Asm | 响应 | |

失败码：**DEP_CPR_001**。

---

### 3.6 IF-DEP-CPR-05 `updateDeptPurchaseRequest`

**Request**：同新建 + **`busNo`（Y）** + **`rowVersion`（Y）**；`purposeLocationId` 不可改异库（或与存量一致）。

**编排序列**：Gate → Pre `getSupplyByBusNo` + `verifyAbleEdit` + 版本校验 → Cmd `createDeptPurchaseRequest`（带 `busNo` 修订）→ Asm。

失败：**DEP_CPR_004** / **DEP_CPR_105**。

---

### 3.7～3.11 IF-DEP-CPR-06～10（审批与删除）

与 **AS-INV-WHS-001-SDD** 订货审批链同构：

| IF | method | Cmd |
| --- | --- | --- |
| 06 | `submitDeptPurchaseRequestApproval` | `submitApproval(busNo)` |
| 07 | `withdrawDeptPurchaseRequestApproval` | `withdrawApproval(busNo)` |
| 08 | `agreeDeptPurchaseRequest` | `agreeRequest(busNo)`（**无**订货编排） |
| 09 | `rejectDeptPurchaseRequest` | `rejectRequest(busNo, reason)` |
| 10 | `removeDeptPurchaseRequest` | `removeByBusNo(busNo)` |

Pre 均 `getSupplyByBusNo` 校验类型 **`DEPT_PURCHASE_REQUEST`**；失败状态 → **DEP_CPR_003**；删除阻塞 → **DEP_CPR_005**。

---

### 3.12 IF-DEP-CPR-11 `pageProductsForDeptPurchaseRequest`

**Request**：与 **IF-WHS-PO-11** 同构字段（`purposeLocationId`、关键字、`itemTable`、分页等）。

**Response**：`PagedResult<ProductDefinitionItemDto>`（含参考采购价、`unitBands`；**无**批号列）。

**编排序列**：Gate 科室药房 → `pageForPurchase` → Asm。

---

## 4. QueryService 与领域事件

| 事件 | IF | 读侧影响 |
| --- | --- | --- |
| Created / Submitted / Withdrawn / Agreed / Rejected / Deleted | 04/06/07/08/09/10 | 列表与待办刷新；**Agreed 不触发库存投影** |

列表真源：**`SupplyRequestDocumentListQuery`** 三件套。

## 5. 写库顺序（核心写接口）

| IF | 写库要点 |
| --- | --- |
| 04/05 | 单事务：`createDeptPurchaseRequest`（新建或同号软删+重插） |
| 06～09 | 单事务：对应审批领域方法 |
| 10 | 单事务：`removeByBusNo` |
| 08 | **禁止**同事务写订货/进货/库存 |

## 6. 非功能性需求实现

| 项 | 承诺 |
| --- | --- |
| 幂等 | 审批重复提交返回已处理语义 |
| 并发 | `rowVersion` → **DEP_CPR_105** |
| 审计 | `approverId` / `approvalTime` / `rejectReason` 落聚合 |

## 7. 测试要点

| 用例 | 优先级 |
| --- | --- |
| 新建→提交→同意；DB 无订货/无库存变动 | P0 |
| 修订乐观锁冲突 | P0 |
| 选品无批号、目录字段齐全 | P0 |
| 下游订货可选 `originalBusNo` 预填联调（**AS-INV-WHS-001**） | P0 |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v0.2 | 2026-07-15 | 领域 Gap 关闭：枚举/方法已落地；§1/§2.3 标注「已落地」 |
| v0.1 | 2026-07-15 | 初稿：对齐 **AS-INV-DEP-007 v0.1**；包 `department/purchaserequest`；选品同构订货 |

---

**文档负责人**：（待填） · **评审**：（待填）
