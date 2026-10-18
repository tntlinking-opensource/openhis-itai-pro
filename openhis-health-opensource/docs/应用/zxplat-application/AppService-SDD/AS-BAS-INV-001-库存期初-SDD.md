# 基础数据 · 库存期初 · 应用服务详细设计（SDD）

> 编码：AS-BAS-INV-001-SDD · **版本**：v0.6 · **日期**：2026-07-31  
> 编排权威：AS-BAS-INV-001 v0.7  
> 业务裁决：[`库存期初初始化方案.md`](../业务方案/库存期初初始化方案.md) v0.16  

> **实现阶段口径**：**目标态全量契约**。成码前须关闭 AS §3 **GAP-OPN-001～004**（领域类型/关闭标志/作废回滚/业务封锁查询）。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 基础数据 · 库存期初（`basic/inventoryopening`） |
| 边界 | 三房 OPENING 单据 CRUD/审批、Excel、关闭期初、作废回滚；不调医保；**已同意明细查询/导出不在本包**（见 **AS-INV-RPT-002** 单据详情白名单 `*_OPENING`） |
| 编排权威 | AS-BAS-INV-001 §2、§4 |
| 方法权威 | `DM-WKF-006`（目标态增补 OPENING）、`DM-WKF-005`、`DM-WKF-008`、`DM-CUS-010`、`DM-ADM-003`（目标态关闭标志） |
| 严格对齐 | IF-BAS-INV-OPN-01～16 |
| 实现阶段口径 | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| HTTP | 一律 `POST /api/app/basicInventoryOpening/{methodName}` |
| `serviceName` | `basicInventoryOpening`（类名 `BasicInventoryOpeningAppService`） |
| 类型映射 | `WAREHOUSE`→`CABINET_OPENING`；`PHARMACY`→`PHARMACY_OPENING`；`DEPARTMENT_PHARMACY`→`DEPT_OPENING` |
| 目标库 | Request `locationId` → 落 `purpose_location_id` |
| 数量 | `item_quantity` 拆零持久化；有码行应扫数 = ceil(拆零 / 每码药典追溯单位拆零)（半盒允许；对齐进货 BR-WHS-IN-014） |
| 效期 | Request `endTime`（有行时必填）→ `wkf_supply_request.end_time`；可选 `startTime` |
| 追溯强制 | `MedicationCategory`：`western`/`chinese-patent` 强制；`chinese-herbal` 与耗材表不强制 |
| 码已在院 | **拒绝同意**（须人工清码后再期初）；禁止 OUT 冲平叠写；本单仅 HOSPITAL_IO IN |
| 药库进价 | `CABINET_OPENING` 硬必填；药房/耗材房可空 |
| Excel | 整单失败；零写入 |

### 1.3 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **BAS-INV-OPN-SDD-R-001** | 同意：库房串行；若有前有效单，先 App `InventoryOpeningVoidOrchestrator.voidOpening`，再 agree→flush→Delivery→**整库清账后按本单重建**→价→追溯 IN；同事务；**不**走财务审批；码已在院则拒绝（须人工清码） |
| **BAS-INV-OPN-SDD-R-002** | 库存以本单为整库期初结果（非累加）；同意时清空该库正数量后按本单重建；批号属性以本单为准 |
| **BAS-INV-OPN-SDD-R-003** | 期初关闭态存**独立表**；仅三房可写；关前无审核中；可空关；业务封锁命中**必须**同写关闭 |
| **BAS-INV-OPN-SDD-R-004** | 作废/覆盖回滚：应用门面 **`InventoryOpeningVoidOrchestrator`**（decrease + T1 追溯 + 领域 `voidOpening` 仅置失效）；批号价手工作废后保留；**禁止** Domain 跨聚合内聚三写 |
| **BAS-INV-OPN-SDD-R-005** | 不调用任何 CHS/350x API |
| **BAS-INV-OPN-SDD-R-006** | IF-16：读源明细（AGREE 或作废）→ `createOpening` 直接建草稿返 `busNo`；源单不变 |
| **BAS-INV-OPN-SDD-R-007** | 同库审核中最多一张；提交前校验 |
| **BAS-INV-OPN-SDD-R-008** | 同单禁止重复 `(itemTable,itemId,lotNumber)` |
| **BAS-INV-OPN-SDD-R-009** | 允许本地空草稿；**落库保存须至少一行**（提交/导入同） |
| **BAS-INV-OPN-SDD-R-010** | Gap-004 发放类窄口径：发药 OUT / 退药 / 科室领用退回 |
| **BAS-INV-OPN-SDD-R-011** | `CABINET_OPENING` 进价硬必填；**供应商行级硬必填**（三房） |
| **BAS-INV-OPN-SDD-R-012** | 撤回复用现网 `withdrawApproval`；关闭/作废与菜单同权 |
| **BAS-INV-OPN-SDD-R-013** | 售价可选：有则持久化（OPENING 借用 `profit_loss_price`）并写批号售价；无则回落进价 |
| **BAS-INV-OPN-SDD-R-014** | **上线清库保留集**（对齐 AS BR-023）：同意入账后正式库清测试流水时，下列表**禁止 TRUNCATE/整表删**；批号价行禁止按 `LOT_NUMBER_*` 清扫 |

### 1.3.1 上线清库保留表（运维约束）

| 表 | 保留原因 |
| --- | --- |
| `wkf_supply_request` | 三类 OPENING 单据（本清单整表留；若混有非 OPENING 测试单可条件删） |
| `wkf_supply_delivery` | 同意发放事实 |
| `wkf_inventory_item` | 期初台账真源 |
| `adm_location_inventory_opening` | 关闭/封锁态 |
| `cus_trace_number` / `cus_trace_number_record` | 有码同意建档与流水 |
| `adm_charge_item_definition` 中 `LOT_NUMBER_COST` / `LOT_NUMBER_PRICE` | 批号进价/售价 |

院点脚本真源：朝阳 `sql/期初后清库.md` · `cleanup_pro_after_opening.sql`；清库前备份 `whale-pro`→`whale-bak`（`clone_training_to_pro.sql`）。

---

## 2. 代码结构

```text
com/openhis/health/application/basic/inventoryopening/
  service/
    BasicInventoryOpeningAppService.java
  request/
    LoadInventoryOpeningFormInitRequest.java
    PageInventoryOpeningsRequest.java
    GetInventoryOpeningDetailRequest.java
    CreateInventoryOpeningRequest.java
    UpdateInventoryOpeningRequest.java
    SubmitInventoryOpeningApprovalRequest.java
    WithdrawInventoryOpeningApprovalRequest.java
    AgreeInventoryOpeningRequest.java
    RejectInventoryOpeningRequest.java
    RemoveInventoryOpeningRequest.java
    PageProductsForInventoryOpeningRequest.java
    DownloadInventoryOpeningImportTemplateRequest.java
    ImportInventoryOpeningRequest.java
    CloseInventoryOpeningRequest.java
    VoidInventoryOpeningRequest.java
    CopyInventoryOpeningFromPreviousRequest.java
  response/
    LoadInventoryOpeningFormInitResponse.java
    PageInventoryOpeningsResponse.java
    GetInventoryOpeningDetailResponse.java
    CreateInventoryOpeningResponse.java
    ImportInventoryOpeningResponse.java
    DownloadInventoryOpeningImportTemplateResponse.java
    CloseInventoryOpeningResponse.java
    VoidInventoryOpeningResponse.java
  dto/
    InventoryOpeningListRowDto.java
    InventoryOpeningLineDto.java
    InventoryOpeningDetailLineDto.java
    InventoryOpeningProductRowDto.java
    InventoryOpeningImportErrorDto.java
    LocationOpeningOptionDto.java
  mapper/
    BasicInventoryOpeningMapper.java
    BasicInventoryOpeningMapperImpl.java
  support/   （可选）
    InventoryOpeningExcelSupport.java
    InventoryOpeningTraceGateSupport.java
    InventoryOpeningBusinessLockSupport.java
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `SupplyRequestDomainService` | 建单/提交/撤回/同意/驳回/删除/作废（目标态含 `createOpening`） |
| `SupplyRequestQueryService` | 列表/详情/类型门控 |
| `SupplyDeliveryDomainService` | `createSupplyDelivery` |
| `InventoryItemDomainService` | `createInventoryItem` / `increaseQuantity` / `decreaseQuantity` |
| `InventoryItemQueryService` | 四元组命中 |
| `TraceNumberDomainService` | `recordTraceRecords`；作废回滚（目标态） |
| `LocationDomainService` | `closeInventoryOpening`（目标态） |
| `LocationQueryService` | 三类库房列表、关闭态 |
| `ChargeItemDefinitionDomainService`（或既有批号价写入门面） | 批号进价/售价 |
| `ProductDefinition` 选品装配（与进货同源 Loader） | IF-11 |
| `BasicInventoryOpeningMapper` | Asm |
| `BusinessSequenceService` | `bus_no` 采番 |
| 公共 `OptionDto` | 下拉（不新建模块内 OptionDto） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF | 备注 |
| --- | --- | --- | --- |
| `LocationQueryService` | `listAllActiveWarehouses` / `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies`（或等价三态列表） | 01 | 目标态可合并为按 form 查询 |
| `LocationQueryService` | `isInventoryOpeningClosed(locationId)` | 01/闸门 | Gap-002：读独立表；非三房恒 false |
| `LocationDomainService` | `closeInventoryOpening(CloseInventoryOpeningCommand)` | 14；封锁 | Gap-002：仅 WAREHOUSE/PHARMACY/DEPARTMENT_PHARMACY 可写独立表 |
| `SupplyRequestQueryService` | `pageSupplyByTypes` / `getSupplyByBusNo` | 02/03 | 过滤三类 OPENING |
| `SupplyRequestQueryService` | `existsNonOpeningBusinessForLocation(locationId)` | 闸门 | Gap-004；按 **`purpose_location_id`** |
| `SupplyRequestQueryService` | `existsOpeningInApprovalForLocation(locationId)` | 06/14 | 同库审核中仅一张 / 关前校验 |
| `SupplyRequestQueryService` | `findCurrentAgreedOpeningBusNo(locationId)` | 02/16 | 当前有效；列表 `currentEffective` |
| `SupplyRequestDomainService` | `createOpening(CreateOpeningCommand)` | 04/05/13 | Gap-001 |
| `SupplyRequestDomainService` | `submitApproval` / `withdrawApproval` / `agreeRequest` / `rejectRequest` / `removeByBusNo` | 06～10 | 既有 |
| `SupplyRequestDomainService` | `voidOpening(VoidOpeningCommand)` | 15 | Gap-003 |
| `SupplyDeliveryDomainService` | `createSupplyDelivery(...)` | 08 | 既有 |
| `InventoryItemQueryService` | 四元组查询 | 08/15 | 既有 |
| `InventoryItemDomainService` | `createInventoryItem` / `increaseQuantity` / `decreaseQuantity` | 08/15 | 既有 |
| `TraceNumberDomainService` | `recordTraceRecords` | 08 | 既有 |
| `TraceNumberDomainService` | `reverseOpeningTraceRecords(...)`（目标态名） | 15 | Gap-003 |
| `ChargeItemDefinitionDomainService` | 批号价写入/更新（与进货同源） | 08/15 | 既有路径 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 领域枚举 | `com.zxplat.domain.share.enums.SupplyType`（目标态增 `CABINET_OPENING`/`PHARMACY_OPENING`/`DEPT_OPENING`） | `DM-WKF-006` §4.1 SupplyType | Gap-001；目标态待生成枚举常量 |
| 领域枚举 | `com.zxplat.domain.share.enums.SupplyStatus` | `DM-WKF-006` §4 SupplyStatus | `AGREE`/`EXPIRED_INVALIDATED` 等 |
| 领域枚举 | `com.zxplat.domain.share.enums.LocationForm` | `DM-ADM-003` §2/§3 `form_enum` | 三房形态 |
| 领域枚举 | `com.zxplat.domain.share.enums.InventoryLedgerBusinessType` | 台账语义 DM / 共享枚举 | `INBOUND_OR_INIT` |
| 字典字段 | `CreateInventoryOpeningRequest.locationId` | `DM-ADM-003` §3 `adm_location.id` | 目标库房 |
| 字典字段 | `InventoryOpeningLineDto.endTime` | `DM-WKF-006` §3 `end_time` | 效期必填 |
| 字典字段 | `InventoryOpeningLineDto.traceNo` | `DM-WKF-006` §3 `trace_no`；`DM-CUS-010` | 宽列 |
| 字典字段 | `InventoryOpeningProductRowDto.categoryCode` | `DM-MED-001` §4.1 MedicationCategory | `western`/`chinese-patent`/`chinese-herbal` |
| 状态取值 | `VoidInventoryOpeningRequest`→`EXPIRED_INVALIDATED` | `DM-WKF-006` SupplyStatus | 作废 |
| 公共常量 | `AggregateTypeConstants`（若审计引用） | 对应聚合 BR | 仅审计需要时 |

---

## 3. 按接口 ID 命名展开

先读完各 IF「领域编排序列」，按序号执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-BAS-INV-OPN-01 | `loadInventoryOpeningFormInit` | 进入库存期初页初始化 | `POST /api/app/basicInventoryOpening/loadInventoryOpeningFormInit` |
| IF-BAS-INV-OPN-02 | `pageInventoryOpenings` | 期初单分页列表 | `POST /api/app/basicInventoryOpening/pageInventoryOpenings` |
| IF-BAS-INV-OPN-03 | `getInventoryOpeningDetail` | 期初单详情 | `POST /api/app/basicInventoryOpening/getInventoryOpeningDetail` |
| IF-BAS-INV-OPN-04 | `createInventoryOpening` | 新建期初单 | `POST /api/app/basicInventoryOpening/createInventoryOpening` |
| IF-BAS-INV-OPN-05 | `updateInventoryOpening` | 编辑期初单 | `POST /api/app/basicInventoryOpening/updateInventoryOpening` |
| IF-BAS-INV-OPN-06 | `submitInventoryOpeningApproval` | 提交审批 | `POST /api/app/basicInventoryOpening/submitInventoryOpeningApproval` |
| IF-BAS-INV-OPN-07 | `withdrawInventoryOpeningApproval` | 撤回审批 | `POST /api/app/basicInventoryOpening/withdrawInventoryOpeningApproval` |
| IF-BAS-INV-OPN-08 | `agreeInventoryOpening` | 审批同意 | `POST /api/app/basicInventoryOpening/agreeInventoryOpening` |
| IF-BAS-INV-OPN-09 | `rejectInventoryOpening` | 审批驳回 | `POST /api/app/basicInventoryOpening/rejectInventoryOpening` |
| IF-BAS-INV-OPN-10 | `removeInventoryOpening` | 删除单据 | `POST /api/app/basicInventoryOpening/removeInventoryOpening` |
| IF-BAS-INV-OPN-11 | `pageProductsForInventoryOpening` | 选品检索 | `POST /api/app/basicInventoryOpening/pageProductsForInventoryOpening` |
| IF-BAS-INV-OPN-12 | `downloadInventoryOpeningImportTemplate` | 下载导入模板 | `POST /api/app/basicInventoryOpening/downloadInventoryOpeningImportTemplate` |
| IF-BAS-INV-OPN-13 | `importInventoryOpening` | 上传导入 | `POST /api/app/basicInventoryOpening/importInventoryOpening` |
| IF-BAS-INV-OPN-14 | `closeInventoryOpening` | 关闭期初 | `POST /api/app/basicInventoryOpening/closeInventoryOpening` |
| IF-BAS-INV-OPN-15 | `voidInventoryOpening` | 作废当前有效单 | `POST /api/app/basicInventoryOpening/voidInventoryOpening` |
| IF-BAS-INV-OPN-16 | `copyInventoryOpeningFromPrevious` | 从上一张复制 | `POST /api/app/basicInventoryOpening/copyInventoryOpeningFromPrevious` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限/参数 | 停止 |
| **Pre** | 只读门禁（关闭、业务封锁、状态、追溯规则） | 禁止 Cmd |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-BAS-INV-OPN-01 `loadInventoryOpeningFormInit`（进入库存期初页初始化）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （可空体） | — | 否 | 无过滤；忽略组织收窄按产品定 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `warehouseOptions` | `List<LocationOpeningOptionDto>` | 药库 |
| `pharmacyOptions` | `List<LocationOpeningOptionDto>` | 药房 |
| `departmentPharmacyOptions` | `List<LocationOpeningOptionDto>` | 耗材房/科室药房 |
| `statusOptions` | `List<OptionDto>` | SupplyStatus |
| `practitionerOptions` | `List<OptionDto>` | 经手人（可选） |

**LocationOpeningOptionDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `value` | `String` | locationId |
| `label` | `String` | 名称 |
| `openingClosed` | `Boolean` | 是否已关闭期初 |
| `formEnum` | `String` | LocationForm code |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | 会话 | 登录门禁 |
| 2 | Pre | `LocationQueryService#listAllActiveWarehouses` | — | 药库 |
| 3 | Pre | `LocationQueryService#listAllActivePharmacies` | — | 药房 |
| 4 | Pre | `LocationQueryService#listAllActiveDepartmentPharmacies` | — | 耗材房；目标态方法名以实现为准 |
| 5 | Pre | `LocationQueryService#getLocationOpeningClosed`（批量） | ids | Gap-002 |
| 6 | Asm | `BasicInventoryOpeningMapper#toFormInitResponse` | ②～⑤ | 仅读路径 |

**Mapper**：`toFormInitResponse(...): LoadInventoryOpeningFormInitResponse`

---

### 3.3 IF-BAS-INV-OPN-02 `pageInventoryOpenings`（期初单分页列表）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `locationId` | `String` | 否 | 库房 |
| `statusEnum` | `String` | 否 | SupplyStatus |
| `busNo` | `String` | 否 | 单号 |
| `pageNo`/`pageSize` | `Integer` | 是 | 分页 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `Long` | 合并后条数 |
| `records` | `List<InventoryOpeningListRowDto>` | 行 |

**InventoryOpeningListRowDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | `String` | 单号 |
| `typeEnum` | `String` | OPENING 类型 |
| `locationId`/`locationName` | `String` | 库房 |
| `statusEnum`/`statusDisplay` | `String` | 状态 |
| `applicantName` | `String` | 制单人 |
| `approverName` | `String` | 审批人 |
| `applyTime`/`approvalTime` | `String` | 时间 |
| `lineCount` | `Integer` | 行数 |
| `totalQuantity` | `BigDecimal` | 可选汇总 |
| `currentEffective` | `Boolean` | 同库当前唯一 `AGREE` 为 true |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | 分页参数 | — |
| 2 | Pre | `SupplyRequestQueryService#pageSupplyByTypes` | 三类 OPENING + 筛选 | 仅读 |
| 3 | Asm | `BasicInventoryOpeningMapper#toListRows` | ② | — |

---

### 3.4 IF-BAS-INV-OPN-03 `getInventoryOpeningDetail`（期初单详情）

**Request**：`busNo`（必填）

**Response**：头字段 + `lines: List<InventoryOpeningDetailLineDto>`

**InventoryOpeningDetailLineDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `itemTable`/`itemId` | `String` | 品规 |
| `itemName`/`specification` | `String` | 展示 |
| `lotNumber` | `String` | 批号 |
| `endTime`/`startTime` | `String` | 效期 |
| `itemQuantity` | `BigDecimal` | 数量（拆零） |
| `unitCode` | `String` | 单位 |
| `price`/`salePrice`/`totalPrice` | `BigDecimal` | 进价/售价/金额 |
| `traceNo`/`traceUnitCode` | `String` | 追溯 |
| `supplierId` | `String` | 可选 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `busNo` | — |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | busNo | 非 OPENING → EX-103 |
| 3 | Asm | `BasicInventoryOpeningMapper#toDetailResponse` | ② | 仅读 |

---

### 3.5 IF-BAS-INV-OPN-04 `createInventoryOpening`（新建期初单）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `locationId` | `String` | 是 | 目标库房 |
| `practitionerId` | `String` | 否 | 经手人 |
| `occurrenceTime` | `String` | 是 | 业务时点 |
| `lines` | `List<InventoryOpeningLineDto>` | 否 | 可空列表；空草稿合法 |

**InventoryOpeningLineDto**：同详情行可写字段（`itemTable/itemId/lotNumber/endTime/itemQuantity/unitCode/price/salePrice/traceNo/traceUnitCode/supplierId`）；`endTime` 必填。

**Response**：`busNo`

**失败码**：`EX-BAS-INV-OPN-001/002`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | locationId、lines | — |
| 2 | Pre | `LocationQueryService#getLocationOpeningClosed` | locationId | 已关闭→001 |
| 3 | Pre | `SupplyRequestQueryService#existsNonOpeningBusinessForLocation` | locationId | true→001；可触发自动 close |
| 4 | Pre | 目录加载 + 行唯一 +（有行时）效期/追溯/（药库）进价校验 | lines | 失败→002 |
| 5 | Cmd | `SupplyRequestDomainService#createOpening(CreateOpeningCommand)` | `purposeLocationId`+类型+行 | **写库点**；Gap-001 |
| 6 | Asm | `BasicInventoryOpeningMapper#toCreateResponse` | ⑤ | — |

---

### 3.6 IF-BAS-INV-OPN-05 `updateInventoryOpening`（编辑期初单）

**Request**：`busNo` + 同创建行结构（全量替换）

**失败码**：`001/003`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | busNo | — |
| 2 | Pre | `getSupplyByBusNo` + 可修订态断言 | — | 非可编→003 |
| 3 | Pre | 关闭/封锁闸门 | location | →001 |
| 4 | Pre | 效期/追溯规则 | lines | →002 |
| 5 | Cmd | `SupplyRequestDomainService#createOpening(CreateOpeningCommand)` | 首行带既有 busNo 修订 | **写库点**（与进货修订同构） |
| 6 | Asm | — | — | 可空响应 |

---

### 3.7 IF-BAS-INV-OPN-06 `submitInventoryOpeningApproval`（提交审批）

**Request**：`busNo`

**失败码**：`001/004/017/019`

**领域编排序列**：Gate → Pre 关闭/封锁/状态/至少一行/`existsOpeningInApprovalForLocation`（其它单）→ Cmd `submitApproval`（写库点）→ Asm

---

### 3.8 IF-BAS-INV-OPN-07 `withdrawInventoryOpeningApproval`（撤回审批）

**Request**：`busNo`

**领域编排序列**：Gate → Pre 状态 → Cmd `withdrawApproval`（写库点；与进货/盘点同语义）

---

### 3.9 IF-BAS-INV-OPN-08 `agreeInventoryOpening`（审批同意）

**Request**：`busNo`

**失败码**：`006/007/008/009`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | busNo | — |
| 2 | Pre | `getSupplyByBusNo` | — | 须 APPROVAL |
| 3 | Pre | 关闭/封锁闸门；**获取库房串行锁**（按 purposeLocationId） | location | →006 |
| 4 | Pre | `InventoryOpeningTraceGateSupport`：强制码/码数/单内不重复/行唯一 | — | →007/008 |
| 5 | Cmd | （若存在前有效单）`InventoryOpeningVoidOrchestrator#voidOpening` | 前 bus_no | **写库点0** |
| 6 | Cmd | `SupplyRequestDomainService#agreeRequest(...)` | 本 busNo | **写库点1** 置 AGREE |
| 7 | Cmd | `SupplyRequestDomainService#flushPendingChanges` | — | 对齐进货；再读 AGREE 行 |
| 8 | Cmd | `SupplyDeliveryDomainService#createSupplyDelivery(...)` | 同意行 | **写库点2** |
| 9 | Cmd | 清空该库正数量 → 命中四元组则 `increase`、否则 `create` | 本单明细 | **写库点3**（整库真源） |
| 10 | Cmd | 批号价按本单写入/更新（售价可回落进价） | — | **写库点4** |
| 11 | Cmd | 非空 `traceNo`：码已在院则已在 Pre 拒绝；否则 HOSPITAL_IO **IN** | OPENING 类型 | **写库点5** |
| 12 | Asm | — | — | 成功；禁止医保；非财务审批 |

---

### 3.10 IF-BAS-INV-OPN-09 `rejectInventoryOpening`（审批驳回）

**Request**：`busNo`、`reason`（可选）

**领域编排序列**：Gate → Pre → Cmd `rejectRequest`（写库点）

---

### 3.11 IF-BAS-INV-OPN-10 `removeInventoryOpening`（删除单据）

**Request**：`busNo`

**领域编排序列**：Gate → Pre 可删态（同意态拒绝，提示走作废）→ Cmd `removeByBusNo`（写库点）

---

### 3.12 IF-BAS-INV-OPN-11 `pageProductsForInventoryOpening`（选品检索）

**Request**：`locationId`、`productTableName`、关键字、分页

**Response**：`records: List<InventoryOpeningProductRowDto>`（含 `categoryCode`、单位、参考进价等）

**领域编排序列**：Gate → Pre ProductDefinition 分页 → Asm（仅读）

---

### 3.13 IF-BAS-INV-OPN-12 `downloadInventoryOpeningImportTemplate`（下载导入模板）

**Request**：空体可

**Response**：`fileName`、`contentType`、`fileBytes`（base64）

**领域编排序列**：Gate → Asm 生成模板字节（仅读）

---

### 3.14 IF-BAS-INV-OPN-13 `importInventoryOpening`（上传导入）

**Request**：`locationId`、`fileBytes`、`fileName?`

**Response（成功）**：`busNo`

**Response（失败）**：`message`、`errors: List<InventoryOpeningImportErrorDto>`（`excelRowNum/columnName/code/message`）

**InventoryOpeningImportErrorDto**：如上

**失败码**：`001/012`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | file/location | — |
| 2 | Pre | 关闭/封锁 | location | →001 |
| 3 | Pre | Excel 解析 + **全量**校验 | 效期/强制码/码数/编码 | 有错→012，**不写库** |
| 4 | Cmd | `createOpening(CreateOpeningCommand)` | 校验通过行 | **写库点**；单事务 |
| 5 | Asm | 成功 `busNo` / 失败 errors | — | 无部分成功 |

---

### 3.15 IF-BAS-INV-OPN-14 `closeInventoryOpening`（关闭期初）

**Request**：`locationId`

**Response**：`locationId`、`openingClosed=true`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | locationId | — |
| 2 | Pre | 读当前关闭态 | — | 已关闭→幂等成功跳至 Asm |
| 3 | Pre | `existsOpeningInApprovalForLocation` | locationId | true→018 |
| 4 | Cmd | `LocationDomainService#closeInventoryOpening(CloseInventoryOpeningCommand)` | locationId | **写库点**；Gap-002；可空关 |
| 5 | Asm | toCloseResponse | — | — |

---

### 3.16 IF-BAS-INV-OPN-15 `voidInventoryOpening`（作废已同意单）

**Request**：`busNo`、`reason?`

**失败码**：`014/015`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | busNo | — |
| 2 | Pre | `getSupplyByBusNo` | 须 AGREE | 否则失败 |
| 3 | Pre | `getLocationOpeningClosed` | 已关闭→014 | 关闭后禁止作废 |
| 4 | Cmd | `InventoryOpeningVoidOrchestrator#voidOpening` | decrease+T1+领域置失效；**不改批号价** | **写库点** |
| 5 | Asm | — | — | — |

> 同意覆盖与手工作废的回滚入口均为 App **`InventoryOpeningVoidOrchestrator`**（R-004）。

---

### 3.17 IF-BAS-INV-OPN-16 `copyInventoryOpeningFromPrevious`（从上一张复制）

**Request**：`locationId`（必填）、`sourceBusNo`（可选；默认该库当前有效 AGREE；指定时可指向同库 `AGREE` 或 `EXPIRED_INVALIDATED`）

**Response**：`busNo`（新草稿；**本期唯一模式**：直接建草稿）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | locationId | — |
| 2 | Pre | 关闭/封锁闸门 | — | →001 |
| 3 | Pre | 查当前 AGREE 或 `getSupplyByBusNo`（须同库 OPENING 且 AGREE/作废） | source | 无源→016 |
| 4 | Cmd | `createOpening(CreateOpeningCommand)` | 拷贝源行，新 bus_no，`purposeLocationId` | **写库点**；源单不变 |
| 5 | Asm | toCreateResponse | — | 返新 `busNo` |

---

## 4. QueryService 与领域事件

| 写侧事件 | 读侧刷新 |
| --- | --- |
| 供应创建/同意/作废 | 列表 IF-02、详情 IF-03 |
| 库存增减 | 库存明细页（非本 AS） |
| 追溯流水 | 库存追溯弹窗 |
| 关闭期初 | IF-01 下拉 `openingClosed` |

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 调用 | 聚合顺序 |
| --- | --- | --- | --- | --- |
| 04/05/13 | create/update/import | §3.5/3.6/3.14 之 Cmd | `createOpening` | SupplyRequest |
| 06 | submit | Cmd | `submitApproval` | SupplyRequest |
| 07 | withdraw | Cmd | `withdrawApproval` | SupplyRequest |
| 08 | agree | 5→11 | VoidOrchestrator 前单 → agree → flush → Delivery → 清零后 increase/create → Price → Trace IN | 同事务；库房串行；非财务审；码已在院拒 |
| 09 | reject | Cmd | `rejectRequest` | SupplyRequest |
| 10 | remove | Cmd | `removeByBusNo` | SupplyRequest |
| 14 | close | Cmd | 无审核中校验 → `closeInventoryOpening` | 独立关闭表；可空关 |
| 15 | void | Cmd | `InventoryOpeningVoidOrchestrator` | 同事务 |
| 16 | copy | Cmd | `createOpening` 拷贝建草稿 | SupplyRequest |

**事务边界**：08/13/15 单事务；失败全回滚。  
**失败回滚**：任一 Cmd 抛错则整单回滚。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 同意防重复入账 | `agreeInventoryOpening` Pre 已 AGREE 拒绝 | §3.9 序号 2 |
| 关闭幂等 | `closeInventoryOpening` | §3.15 序号 2 |
| 作废一次 | `voidInventoryOpening` 非 AGREE 拒绝 | §3.16 序号 2 |

### 6.2 并发控制

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 关闭 vs 同意 | Pre 闸门读关闭标志 | §3.9 序号 3；§3.15 |
| 同库双同意 | 同意库房串行锁 | §3.9 序号 3 |
| 同库双审核中 | 提交前 `existsOpeningInApprovalForLocation` | §3.7 |

### 6.3 重试与容错

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| Excel 整单失败可重传 | `importInventoryOpening` | §3.14 |
| 业务异常不重试半成功 | 事务回滚 | §5 |

### 6.4 数据脱敏

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 无额外 PII | — | — |

### 6.5 审计日志

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 同意/作废/关闭记操作者时间 | AppService 写侧 | §3.9 / §3.15 / §3.16 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 三房建单同意 | 应用 | 是 | P0 |
| 复制上一张（含作废源）后同意覆盖前单 | 应用 | 是 | P0 |
| 药库无进价拒绝；药房可空价同意 | 应用 | 是 | P0 |
| 业务封锁后关闭标志已写 | 应用 | 是 | P0 |
| 码已在院拒绝（须人工清码） | 应用 | 是 | P0 |
| 同库第二张提交审核中被拒 | 应用 | 是 | P0 |
| 有审核中不可关闭；可空关 | 应用 | 是 | P0 |
| 西药无码拒绝；中草药无码通过 | 应用 | 是 | P0 |
| 码数不一致拒绝 | 应用 | 是 | P0 |
| Excel 一行错整单失败 | 应用 | 是 | P0 |
| 关闭后禁止新建/复制 | 应用 | 是 | P0 |
| 非期初业务单后封锁 | 应用 | 是 | P0 |
| 手工作废当前有效单 | 应用 | 是 | P0 |
| 关闭后作废失败 | 应用 | 是 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 同意事务回滚无半成功 | 应用 | 是 | P0 |

### 7.3 查询结果一致性

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 同意后列表/库存/追溯一致 | 集成 | 是 | P0 |

### 7.4 集成测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 期初后发药扣减带码 | 集成 | 是 | P1 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 领域 Gap 未关闭前 | 应用层成码阻塞；先关闭 GAP-OPN-001～004 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.6 | 2026-07-31 | **R-014** + §1.3.1：期初后上线清库保留表；边界注明已同意明细查询/导出走 **AS-INV-RPT-002**；对齐 AS v0.7 BR-023 |
| v0.1 | 2026-07-21 | 初稿目标态 SDD；对齐 AS v0.1；注明成码依赖 AS Gap-001～004 关闭；§2.3 含目标态 SupplyType/Location 关闭标志 |
| v0.2 | 2026-07-21 | Gap-002 改为独立表+仅三房可写；对齐 AS v0.2 裁决 |
| v0.5 | 2026-07-21 | 拍板 U1～U8：码真覆盖、封锁窄口径+必写关闭、voidOpening、无财务审、药库进价必填、撤回对齐、关/作废同菜单权 |
| v0.4 | 2026-07-21 | 拍板 Q1～Q10：purpose 库、串行+单审核中、行唯一、空草稿、复制建草稿/可拷作废、currentEffective、关前清审核中、可空关、码覆盖、作废保留价、无则建有则加 |
| v0.3 | 2026-07-21 | 最后一张为准；IF-16 复制；同意先 void 前单；对齐 AS v0.3 |
