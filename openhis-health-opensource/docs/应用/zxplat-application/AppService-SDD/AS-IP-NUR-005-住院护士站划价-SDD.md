# 住院护士站 · 划价 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-005-SDD  
> 版本：v2.27 | 日期：2026-10-17 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · **住院记账**（划价确费 Tab + 医嘱计费 Tab） |
| 实现阶段口径 | **应用层已落地** IF-IP-PRC-01～07 + **AS-IP-NUR-013** 同 Bean 扩展 IF-IP-ARG-02～08；领域 GAP 已关；落包 `inpatient/nursestation/inpatientbilling` · `NurseStationInpatientBillingAppService` |
| 编排权威 | `AS-IP-NUR-005-住院护士站划价.md`（**v2.21**）§1.3、§4 |
| 方法权威 | `DM-WKF-004` §6.1.14/§6.1.19；`DM-WKF-003` §6.1.9/§6.1.10；`DM-WKF-007` §6.1.5～6、§6.1.16/§6.1.21/§6.1.24/§6.1.25；`DM-CLN-004` §6.1.7；`DM-ADM-011` §6.1.3/§6.1.3a、§6.2.5；`ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3.4.4～3.4.5 |
| 严格对齐 | IF 编号 **IF-IP-PRC-01～07**，不得增删改号 |
| 读侧方案 | **方案 A**：InpatientOrderListSkeleton + 域 QueryService 补全；**IF-IP-PRC-03** 父医嘱展示委托 **`NurseStationOrderViewListAssembler`**（§2.5）；**禁止** App 注入QueryService Repository |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 左侧患者树/列表/详情 | **AS-IP-NUR-001** |
| 医嘱校对 / 执行 | **AS-IP-NUR-003** / **AS-IP-NUR-004** |
| 费用明细 Tab、部分数量冲销、signCode、**耗材划价 Procedure**、库存 Gate、药品划价 | AS §1.4 非目标 |
| 直接划价与补费混批 | **禁止**（分 IF、分 Request 类） |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见范围 **BR-IP-NUR-003**；`accountId` 来自患者列表 |
| `AS-IP-NUR-003-SDD` §2.5 | 父医嘱行展示 **`orderview`** 共享装配器 |
| `AS-IP-DOR-003` / IF-IP-DOR-11 | 选品目录定价/单位 bands 同构（**BR-IP-PRC-017**） |
| `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` | **Q-IP-PRC-001/002** |
| `DM §6.2 各 Request QueryService` | 划价写事件 → InpatientOrderListSkeleton QueryService |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 / 枚举 code | 持久化与比较一律 **String `code`**（`RequestStatus`、`ChargeItemStatus`、`DispenseStatus`、`GenerateSource`、`TherapyType` 等） |
| 划价模式 | `DIRECT`（直接划价，无 `based_on_*`）/ `SUPPLEMENT`（补费，须父锚点） |
| 父锚点 | `parentRequestAnchor.orderType`：`MED_REQUEST` / `SERVICE_REQUEST`；`requestId` 写域 `basedOnTable`/`basedOnId`（`TableNameConstants`） |
| 护士划价来源 | `GenerateSource.NURSE_PRICING` → `nurse-pricing`；新建请求 **一律** `TherapyType.TEMPORARY`；诊疗创建态 **`RequestStatus.VERIFIED`**；**不计入** **AS-IP-NUR-001** 任务条数 |
| 直接 vs 补费 | **IF-IP-PRC-02** 与 **IF-IP-PRC-04** 为不同 `public` 方法与 Request 类型；**禁止**同一 HTTP 混批 |
| 取消划价键 | `NurseStationPricingCancelItemDto.orderType` + `requestId`；与 **IF-IP-PRC-05** 列表行 `orderType`/`orderId` 对齐 |
| HTTP | 一律 **`POST`** + `AppServiceControllerRegistrar`；Bean **`NurseStationInpatientBillingAppService`** → 前缀 **`nurseStationInpatientBilling`** |
| 批量上限 | 写接口单批 **≤50** 行（**AS §7**） |
| 选品目录 | **IF-IP-PRC-01** 耗材建议传 `encounterId`（取住院科室取药模板）；**不**按登录人岗位管辖药房；**不**做可见性 Gate 拒绝（就诊不存在时回落 `organizationUnitId`） |
| 事务 | IF-02/04/06/07 App `@Transactional` 整批回滚 |
| 划价客户可见时间 | 列表字段 `pricedTime` 与过滤 `pricedTimeFrom`/`pricedTimeTo` 按**展示轴**（**BR-IP-PRC-030**）：诊疗=净剩 COMPLETED Procedure.`recorded_time`；耗材=主发放 `dispense_time`（无则 `prepare_time`）；**禁止**按骨架 `created_at` 过滤 |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  inpatientbilling/
    service/NurseStationInpatientBillingAppService.java
    request/
      PageNurseStationPricingCatalogRequest.java
      ConfirmDirectNurseStationPricingRequest.java
      ListNurseStationSupplementParentOrdersRequest.java
      ConfirmSupplementNurseStationPricingRequest.java
      ListNurseStationPricedOrdersRequest.java
      CancelNurseStationPricingRequest.java
      ChangeNurseStationPricingBusinessTimeRequest.java
    response/
      PageNurseStationPricingCatalogResponse.java
      ConfirmDirectNurseStationPricingResponse.java
      ListNurseStationSupplementParentOrdersResponse.java
      ConfirmSupplementNurseStationPricingResponse.java
      ListNurseStationPricedOrdersResponse.java
      CancelNurseStationPricingResponse.java
      ChangeNurseStationPricingBusinessTimeResponse.java
    dto/
      NurseStationPricingCatalogRowDto.java
      NurseStationPricingWriteItemDto.java
      NurseStationPricingParentRequestAnchorDto.java
      NurseStationPricingPatientGroupDto.java
      NurseStationSupplementParentOrderRowDto.java
      NurseStationPricedOrderRowDto.java
      NurseStationPricingCancelItemDto.java
    assembler/
      NurseStationInpatientBillingCatalogAssembler.java
      NurseStationInpatientBillingListAssembler.java
      NurseStationInpatientBillingWriteAssembler.java
    support/
      NurseStationInpatientBillingProcedureOrchestrator.java
    mapper/
      NurseStationInpatientBillingMapper.java
      NurseStationInpatientBillingMapperImpl.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/medication/medicationrequest/ 等（医嘱域 QueryService）
  service/MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService.java

zxplat/health-application/.../application/inpatient/nursestation/{feature}/support/
  NurseStationOrderViewListAssembler.java
  dto/NurseStationOrderView*.java
zxplat/health-application/.../application/shared/advice/
  InpatientOrderLookupLoader.java
  dto/InpatientOrderListSkeleton.java
zxplat/health-application/.../application/shared/nursestation-device-dispense/
  DeviceDispenseNurseWorkflowSupport.java           # 划价/执行/门诊处置 AppService 注入
  ProcedureExecutionPerformerOrchestrationSupport.java  # Procedure 主执行人登记

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  InpatientNurseStationConstants.java
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.inpatientbilling`  
> AppService Bean 名 → HTTP 前缀：`nurseStationInpatientBilling`

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentPractitionerResolver` | 写路径 `requesterId`（`findPractitionerIdOrNull`；**B.6.5**） |
| `CurrentOperatorResolver` | 写路径 **operatorId**（**B.6.5**） |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty` → 管理病区（**BR-IP-NUR-003**） |
| `EncounterContextReader` | 可见 encounter 收窄（**IF-02～07**；`listInpatientEncounterContexts`；**不含** IF-01） |
| `EncounterQueryService` | 薄就诊查询 |
| `ProductDefinitionCatalogPageLoader` | IF-01 选品分页（**BR-IP-PRC-017**） |
| `ProductDefinitionBatchLoader` | 列表/补费行目录展示 `loadAsMap` |
| `InpatientOrderLookupLoader` | IF-03/04/05 按 orderType+requestId 解析 encounter |
| `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService` | **Q-IP-PRC-001/002** 列表骨架；**IF-06** `mapListModelsByIds` 取消划价 Pre |
| `MedicationRequestQueryService` | 父医嘱明细补全；补费父校验（药） |
| `ServiceRequestQueryService` | 父医嘱明细补全；补费父校验（诊） |
| `ChargeItemQueryService` | 已划价费用态；`queryOxygenAndECGDuration(encounterId, activityDefinitionId, occurrenceDate?)` 汇总 `wkf_service_request.quantity`（**BR-IP-PRC-010**） |
| `DeviceDispenseQueryService` | 耗材发放态（**mapDeviceDispenseIdsByDeviceRequestIds**）；**IF-07** 主发放 id（`mapPrimaryDispenseIdByDeviceReqIds`） |
| `ProcedureQueryService` | 已划价展示轴 / **IF-07** 净剩 COMPLETED Procedure（`findNetCompletedProcedureIdByServiceRequest`） |
| `ProcedureDomainService` | **IF-07** `amendProcedureRecordedTime` |
| `ActivityDefinitionQueryService` | 诊疗子项读侧：`listChildrenByActivityId` / `listChildrenByActivityIds`（IF-01 选品 `packageChildren` 展示） |
| `ActivityDefinitionPackageChildResolver` | 子项展开（`listResolvedChildren` / `allocateChargeSlices`；**BR-IP-PRC-011**）。只认子项账单冻价；目录有子项但无子项账单时拒绝（**禁止**回落 SALE）。有子项时只出子项行；子项金额之和须等于父 `totalPrice` |
| `ActivityDefinitionPackageChargePersistSupport` | 父 Charge 落库后补写子项账单（`parent_id`、冻价；写路径） |
| `ChsActivityPackageFeeExpandSupport`（chs-jilin bridge） | 2204/2301 费用上传同口径展开（bridge 禁止依赖 health-application，故独立实现） |
| `NurseStationInpatientBillingWriteAssembler` | 写路径吸氧/心电 Gate、权威定价重算（**BR-IP-PRC-010/020**） |
| `ServiceRequestDomainService` | `createFromNursePricing`；`removeFromNursePricing` |
| `DeviceRequestDomainService` | `createExecutedDeviceOrder`；`revokeExecutedDeviceOrder`（批量） |
| `DeviceDispenseDomainService` | `generateDeviceDispenseFromTemporaryPath`；`createPendingRefundDeviceDispense`；`refundDispensedDeviceDispense`；`removeUndispensedDeviceDispense`；**IF-07** `amendDeviceDispenseDisplayTime` |
| `DeviceDispenseNurseWorkflowsupport | `finalizeInpatientTemporaryPathDispense`；`revertInpatientPricingDeviceDispenses`（`application.shared.nursestation-device-dispense`） |
| `DeviceAutoInventoryDeductSettingsupport | 读开关 |
| `InventoryItemDomainService` | `decreaseQuantitiesMerged`（开关 **开**，划价）；`increaseQuantities`（取消划价回补，经 Mapper `toIncreaseQuantityCommand`） |
| `ChargeItemDomainService` | `priceByNurseStation`；`voidNursePricingChargeItemsByServiceAnchors` |
| `NurseStationOrderViewListAssembler` | IF-03 父医嘱展示内核（§2.5） |
| `NurseStationInpatientBillingCatalogAssembler` | IF-01 目录行定价/单位 bands |
| `NurseStationInpatientBillingListAssembler` | IF-03/05 分组列表装配 |
| `NurseStationInpatientBillingMapper` | Request/Command/DTO、Tab/过滤映射 |

> 定价行选取复用 **`ClinicalCatalogPriceResolver#resolveCatalogUnitPrice`**（同 **BR-IP-PRC-017** / **BR-ORD-001**）；**不**将医生站 CatalogAssembler 静态方法列入本模块 dto 清单。

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty` | **IF-02～07** 可见范围 |
| `EncounterContextReader` | `listInpatientEncounterContexts` | 02～07 |
| `ProductDefinitionCatalogPageLoader` | `pageProductDefinition` | 01 |
| `InpatientOrderLookupLoader` | `findByOrderTypeAndId` / `resolveEncounterId` | 03～05 |
| `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService` | `listInpatientNurseStationPricedOrders` | 05 |
| `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService` | `listInpatientNurseStationSupplementOrders` | 03 |
| `MedicationRequestQueryService` | `listMedicationRequestsByEncounterForDoctorStation` | 03 |
| `MedicationRequestQueryService` | `loadMedicationRequestById` | 04 父校验（药） |
| `ServiceRequestQueryService` | `listServiceRequestsByGenerateSource` | 03 |
| `ServiceRequestQueryService` | `mustGetServiceRequest` | 04 父校验（诊） |
| `ChargeItemQueryService` | `listChargeItemsByServiceIds` | 05 |
| `ChargeItemQueryService` | `queryOxygenAndECGDuration` | 02、04 |
| `DeviceDispenseQueryService` | `mapDeviceDispenseIdsByDeviceRequestIds` | 05 |
| `DeviceDispenseQueryService` | `mapPrimaryDispenseIdByDeviceReqIds` | **07** 改时耗材主发放 |
| `ProcedureQueryService` | `findNetCompletedProcedureIdByServiceRequest` | 05 展示轴；**07** 改时诊疗 |
| `ActivityDefinitionQueryService` | `findAllByIds` | 02、04 子项 |
| `ServiceRequestQueryService` | `mapListModelsByIds` | 05 已划价行回源；**06/07** Pre |
| `DeviceRequestQueryService` | `mapListModelsByIds` | 05 已划价行回源；**06/07** Pre |
| `ServiceRequestDomainService` | `createFromNursePricing` | 02、04 |
| `DeviceRequestDomainService` | `createExecutedDeviceOrder` | 02、04 |
| `DeviceDispenseDomainService` | `generateDeviceDispenseFromTemporaryPath` | 02、04 |
| `DeviceDispenseNurseWorkflowsupport | `finalizeInpatientTemporaryPathDispense` | 02、04 |
| `InventoryItemDomainService` | `decreaseQuantitiesMerged`（**BR-IP-PRC-019 开**） | 02、04 |
| `ChargeItemDomainService` | `priceByNurseStation` | 02、04 |
| `ServiceRequestDomainService` | `removeFromNursePricing` | 06 |
| `DeviceRequestDomainService` | `revokeExecutedDeviceOrder`（批量） | 06 |
| `ChargeItemDomainService` | `voidNursePricingChargeItemsByServiceAnchors` | 06 |
| `DeviceDispenseNurseWorkflowsupport | `revertInpatientPricingDeviceDispenses` | 06 |
| `DeviceDispenseDomainService` | `createPendingRefundDeviceDispense`；`refundDispensedDeviceDispense`；`removeUndispensedDeviceDispense` | 06 |
| `InventoryItemDomainService` | `increaseQuantities`（Dispense 已 COMPLETED 回补） | 06 |
| `ProcedureDomainService` | `amendProcedureRecordedTime` | **07** |
| `DeviceDispenseDomainService` | `amendDeviceDispenseDisplayTime` | **07** |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 医嘱来源 | `com.zxplat.domain.share.enums.GenerateSource` | `DM-WKF-004` §3 `generate_source_enum`；`AS-IP-NUR-005` **BR-IP-PRC-006** | `doctor-prescription` / `nurse-pricing` |
| 请求状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-WKF-004` §2.3；`DM-MED-003` §2.3 | 父医嘱 `VERIFIED`/`STOPPED`；诊划价产出 **`VERIFIED`**；材 `COMPLETED` |
| 长临嘱 | `com.zxplat.domain.share.enums.TherapyType` | `DM-MED-003` §3 `therapy_enum` | 护士新建固定 `TEMPORARY` |
| 费用状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §5 | 护士划价 `PENDING_SETTLEMENT`（待结算） |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-WKF-007` §5 | 待发 `PREPARATION` |
| QueryService医嘱类型 | `com.zxplat.domain.share.constants.QueryServiceOrderTypeConstants` | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §2 `order_type` | `MED_REQUEST` / `SERVICE_REQUEST` / `DEVICE_REQUEST` |
| 产品物理表 | `com.zxplat.domain.share.constants.TableNameConstants` | `AS-IP-NUR-005` **BR-IP-PRC-002** | `WKF_ACTIVITY_DEFINITION`、`ADM_DEVICE_DEFINITION`；父锚 `MED_MEDICATION_REQUEST`、`WKF_SERVICE_REQUEST` |
| 划价标记 | `PageNurseStationPricingCatalogRequest.pricingFlag` | `AS-IP-NUR-005` **BR-IP-PRC-002** | 固定传 `1` 排除药品 |
| 统一查价 | `ClinicalCatalogPriceResolvesupport | **BR-ORD-001** / **BR-IP-PRC-003** / **BR-IP-PRC-017** / **BR-IP-PRC-020** | 诊疗 **SALE**；药耗 **FIFO 批号→LOT_NUMBER_PRICE**；**废止** `pricingSourceCode` |
| 划价模式（展示） | `NurseStationPricedOrderRowDto.pricingModeCode` | `AS-IP-NUR-005` **BR-IP-PRC-015** | `DIRECT` / `SUPPLEMENT` |
| 补费父锚（列表出参） | `NurseStationPricedOrderRowDto.basedOnTable` / `basedOnId` | `AS-IP-NUR-005` **BR-IP-PRC-015**；域 `based_on_*` | 补费子行回传；前端据表名推导父 `orderType` 匹配 IF-IP-PRC-03 |
| 父锚点 orderType | `NurseStationPricingParentRequestAnchorDto.orderType` | `AS-IP-NUR-005` §1.3 补费锚点 | 仅 `MED_REQUEST`/`SERVICE_REQUEST` |
| 业务消息码 | `InpatientNurseStationConstants.EX_IP_PRC_*` | `AS-IP-NUR-005` §5 | **101～205、301、401～407** |

### 2.4 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-NUR-005` §3**（均已关闭）；本 SDD **禁止**维护 Gap 表。

**已落地**：`MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationPricedOrders`、`#listInpatientNurseStationSupplementOrders`；`ServiceRequestDomainService#createFromNursePricing` → `List<Long>`；`DeviceRequestDomainService#createExecutedDeviceOrder` → `List<Long>`；`ChargeItemQueryService#queryOxygenAndECGDuration`。

**本 SDD 成码交付**：`NurseStationInpatientBillingAppService` IF-IP-PRC-01～07 及 Request/Response/DTO/Mapper/Assembler；`InpatientNurseStationConstants.EX_IP_PRC_*`；IF-03 注入 `NurseStationOrderViewListAssembler`；**IF-IP-PRC-01** `categoryCode` 透传（**IF-IP-ARG-01**）。**自动计费**扩展见 **AS-IP-NUR-013-SDD**（同 Bean 增补 IF-IP-ARG-02～08）。

### 2.5 共享医嘱查看装配器（`orderview`）

> 完整契约见 **`AS-IP-NUR-003-住院护士站医嘱校对-SDD.md` §2.5**。

| 消费 Assembler | 本 AS 职责 |
| --- | --- |
| `NurseStationInpatientBillingListAssembler` | IF-03：`indexEncounterDetails` → `prepareDisplayContext` → `buildMedicationDisplayCore` / `buildServiceDisplayCore` → 拷贝至 `NurseStationSupplementParentOrderRowDto`（**含药嘱 `groupId`**） |
| （禁止） | 在 `inpatientbilling` 包内复制展示字段逻辑 |

**硬约束**：IF-03 QueryService过滤仍用 **Q-IP-PRC-002**；`DisplayCore` **禁止**直接作为 HTTP 字段。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-PRC-01 | `pageNurseStationPricingCatalog` | 划价选品目录 | `POST /api/app/nurseStationInpatientBilling/pageNurseStationPricingCatalog` |
| IF-IP-PRC-02 | `confirmDirectNurseStationPricing` | 直接划价确认 | `POST /api/app/nurseStationInpatientBilling/confirmDirectNurseStationPricing` |
| IF-IP-PRC-03 | `listNurseStationSupplementParentOrders` | 医嘱补费父医嘱列表 | `POST /api/app/nurseStationInpatientBilling/listNurseStationSupplementParentOrders` |
| IF-IP-PRC-04 | `confirmSupplementNurseStationPricing` | 医嘱补费确认 | `POST /api/app/nurseStationInpatientBilling/confirmSupplementNurseStationPricing` |
| IF-IP-PRC-05 | `listNurseStationPricedOrders` | 已划价列表 | `POST /api/app/nurseStationInpatientBilling/listNurseStationPricedOrders` |
| IF-IP-PRC-06 | `cancelNurseStationPricing` | 取消划价 | `POST /api/app/nurseStationInpatientBilling/cancelNurseStationPricing` |
| IF-IP-PRC-07 | `changeNurseStationPricingBusinessTime` | 修改划价客户可见时间 | `POST /api/app/nurseStationInpatientBilling/changeNurseStationPricingBusinessTime` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数 / 批量条数 / 混批 / **列表与写**可见性 | 抛 **`EX-IP-PRC-*`**，不进入 Cmd |
| **Pre** | QueryService骨架、父医嘱加载、吸氧心电累计 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 域写 Request / Dispense / Charge | 同事务回滚 |
| **Asm** | Catalog/List Assembler、Mapper 纯 DTO | — |

记法：`MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationPricedOrders` 表示注入 Bean 调用。

### 3.2 IF-IP-PRC-01 `pageNurseStationPricingCatalog`（划价选品目录）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/pageNurseStationPricingCatalog`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `Integer` | Y | 页码（继承分页基类） |
| `pageSize` | `Integer` | Y | 页大小 |
| `keyword` | `String` | N | 名称/拼音检索 |
| `encounterId` | `Long` | N | 就诊主键；耗材选品优先取该就诊 `organizationId` 作取药模板键 |
| `patientId` | `Long` | N | 患者主键（可选透传） |
| `productTableNames` | `List<String>` | N | 空则默认 `[wkf_activity_definition, adm_device_definition]`；**禁止**含药品表 |
| `categoryCode` | `String` | N | 诊疗目录 **category_code** 精确过滤；自动计费 Tab（**IF-IP-ARG-01**）传 **`51`**（床位服务） |
| `organizationUnitId` | `Long` | N | 住院科室回落（无 encounter 时）；耗材取药模板键 |
| `pharmacyLocationId` | `Long` | N | 耗材库房筛选（可选；须落在取药模板候选内） |
| `sortMode` | `String` | N | 关键字重排模式（同医生站目录） |
| `prepickedProductKeys` | `List<PrepickedProductKeyDto>` | N | 组套/模板/历史行 ids 收窄目录（与 IF-DOR-11 同套路） |

> 耗材建议传 `encounterId`（取就诊住院科室作取药模板键）；无就诊时回落 `organizationUnitId`。**不**做护士站可见性 Gate 拒绝。App 固定设置 `pricingFlag=1`（**BR-IP-PRC-002**），不暴露药品。

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | `Long` | 总条数（B.10.11） |
| `content` | `List<NurseStationPricingCatalogRowDto>` | 当前页目录行 |
| `pageNo` | `Integer` | 当前页码（1-based） |
| `pageSize` | `Integer` | 页大小 |

**DTO：`NurseStationPricingCatalogRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `productDefinitionId` | `Long` | 产品定义主键 |
| `productTableName` | `String` | `wkf_activity_definition` / `adm_device_definition` |
| `busNo` | `String` | 业务编码 |
| `name` | `String` | 展示名称 |
| `pyCode` | `String` | 拼音码 |
| `wbCode` | `String` | 五笔码 |
| `categoryCode` | `String` | 目录类别 code |
| `categoryCodeDictText` | `String` | 类别展示名 |
| `unitCode` | `String` | 默认划价单位：耗材优先 **SPLIT**；诊疗 **PACKAGE**（**BR-IP-PRC-017**） |
| `unitName` | `String` | 默认划价单位展示名 |
| `unitBands` | `List<ProductDefinitionCatalogUnitBandDto>` | 五槽 DTO（含各槽 `unitPrice`，包装锚线性换算；**禁止**包装价÷ratio 再写拆零） |
| `chargeItemDefinitionId` | `Long` | 展示用选中定价行定义 id（**非**入账权威） |
| `unitPrice` | `BigDecimal` | 默认划价单位下单价（耗材对齐拆零槽；**非**入账权威；写路径见 **BR-IP-PRC-020**） |
| `activityId` | `Long` | 诊疗：活动定义 id（`product_id`） |
| `deviceDefId` | `Long` | 耗材：设备定义 id |
| `defaultDispenseLocationId` | `Long` | 耗材：批号行取库存库位；无库存回落模板默认库；写 IF-02/04 回传 `performLocation` |
| `inventoryItemId` | `Long` | 耗材：库存项主键（批号展开行） |
| `lotNumber` | `String` | 耗材批号（批号展开行） |
| `remainingQuantity` | `BigDecimal` | 批号剩余数量（拆零净量，已扣预占） |
| `availableQuantityText` | `String` | 剩余量展示文案（如「4盒6支」） |
| `expirationDate` | `LocalDateTime` | 效期；未维护可空 |
| `totalVolume` | `String` | 规格/装量（产品定义 `total_volume`） |
| `packageChildren` | `List<NurseStationPricingPackageChildDto>` | 诊疗套餐子项（子表 `children_id`/`children_num`/`children_name`；**禁止**对外下发 `childrenJson` 串，B.6.2） |
| `oxygenOrEcgFlag` | `Boolean` | 是否吸氧/心电计时类（Gate 提示用） |
| `durationGateKind` | `String` | `OXYGEN` / `ECG`（**InpatientNurseStationConstants**；BR-IP-PRC-010） |

> **分页说明**：诊疗 `totalCount` 仍为 **ProductDefinition** 页级条数；**耗材** `totalCount` 为管辖药房库存批号行数（与 `content` 同源，可因批号多于品数）。

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PRC-002** | 仅诊疗+耗材；`pricingFlag=1` |
| **BR-IP-PRC-003** | 统一查价（**BR-ORD-001**）；**废止** `pricingSourceCode` |
| **BR-IP-PRC-017** | 定价/单位 bands 同 IF-DOR-11；耗材读侧按**住院科室取药模板**耗材房库存批号分页（含数量 0；`totalCount`=库存行；**不**按登录人岗位管辖）+ 默认拆零槽价；诊疗仍产品定义分页 |
| **BR-IP-PRC-009** | 耗材写路径 `performLocation` 须由 IF-IP-PRC-01 目录回传；写路径 **不**二次 match 模板 |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-101** | 分页非法 / 目录加载失败 |

**Mapper 接口方法签名**

- `toProductDefinitionPageQuery(PageNurseStationPricingCatalogRequest request): ProductDefinitionPageQuery`
- `resolvePricingProductTableNames(List<String> requestTables): List<String>`

> **`toProductDefinitionPageQuery`** 须透传 **`request.categoryCode`** → **`ProductDefinitionPageQuery.categoryCode`**（Loader 已支持；**IF-IP-ARG-01** 床位服务选品依赖此字段）。

**CatalogAssembler 方法签名**

- `assembleCatalogRows(PageNurseStationPricingCatalogRequest request, Page<ProductDefinitionDisplayDto> page): List<NurseStationPricingCatalogRowDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | 分页参数 | 非法 → **EX-IP-PRC-101** |
| 2 | Pre | `NurseStationInpatientBillingMapper#toProductDefinitionPageQuery` | `pricingFlag=1`、收窄表集合、**可选 `categoryCode`** | **BR-IP-PRC-002**；**IF-IP-ARG-01** 传 `51` |
| 3 | Pre | `ProductDefinitionCatalogPageLoader#pageProductDefinition` | ② Query | 仅读 |
| 4 | Asm | `NurseStationInpatientBillingCatalogAssembler#assembleCatalogRows` | Request + ③ Page | **BR-IP-PRC-017** |
| 5 | Asm | `PagedResultBuilder.build` | ④ rows | Response |
| — | — | — | — | **仅读路径** |

### 3.3 IF-IP-PRC-02 `confirmDirectNurseStationPricing`（直接划价确认）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/confirmDirectNurseStationPricing`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<NurseStationPricingWriteItemDto>` | Y | 1～50 行；**禁止** `parentRequestAnchor` |
| `pricingTime` | `LocalDateTime` | N | 整批客户可见划价时刻（**BR-IP-PRC-033**）；空则服务端当前时刻 |

**DTO：`NurseStationPricingWriteItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 就诊 |
| `patientId` | `Long` | Y | 患者 |
| `accountId` | `Long` | Y | 账户（**BR-IP-PRC-013**） |
| `productTableName` | `String` | Y | 诊疗/耗材物理表 |
| `productDefinitionId` | `Long` | Y | 产品定义 id |
| `activityId` | `Long` | C | 诊疗行必填 |
| `deviceDefId` | `Long` | C | 耗材行必填 |
| `quantity` | `BigDecimal` | Y | 数量 |
| `unitCode` | `String` | Y | 单位 code |
| `chargeItemDefinitionId` | `Long` | Y | 入账定价定义 id（**服务端 `reapplyAuthoritativeWritePricing` 重算**；Request 展示值不作权威） |
| `unitPrice` | `BigDecimal` | Y | 入账单价（**服务端按提交 unitCode 线性换算后重算**） |
| `totalPrice` | `BigDecimal` | Y | 总价（=重算单价×数量；`MonetaryAmountSupport.lineTotal`） |
| `performLocation` | `Long` | C | 耗材发放库房（IF-IP-PRC-01 `defaultDispenseLocationId` 回传；**不**二次 match 模板） |
| `lotNumber` | `String` | C | 耗材选中批号（目录批号行回传；有值锁定 `LOT_NUMBER_PRICE` **并**落请求供自动扣库锁批；空则价/扣库均 FIFO） |
| `parentRequestAnchor` | `NurseStationPricingParentRequestAnchorDto` | N | **必须为空**（**BR-IP-PRC-004**） |
| `oxygenOrEcgFlag` | `Boolean` | N | 选品回传；为 true 或带 `durationGateKind` 时执行 Gate |
| `durationGateKind` | `String` | N | `OXYGEN` / `ECG`；可空时写路径按活动名推断 |

**DTO：`NurseStationPricingPackageChildDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `childrenId` | `Long` | 子项诊疗项目 id |
| `childrenNum` | `Integer` | 相对父项数量系数 |
| `childrenName` | `String` | 子项名称快照 |
| `unitCode` | `String` | 可选单位 |
| `quantity` | `BigDecimal` | 可选独立数量 |

**DTO：`NurseStationPricingParentRequestAnchorDto`**（IF-04 使用；本 IF 禁止出现）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `orderType` | `String` | `MED_REQUEST` / `SERVICE_REQUEST` |
| `requestId` | `Long` | 父医嘱主键 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功 |
| `processedCount` | `Integer` | 成功行数 |
| `createdServiceRequestIds` | `List<Long>` | 新建诊疗请求 id（含子项） |
| `createdDeviceRequestIds` | `List<Long>` | 新建耗材请求 id |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PRC-004** | 任一行带父锚点 → **EX-IP-PRC-201** |
| **BR-IP-PRC-006** | `nurse-pricing` + `TEMPORARY`（域层） |
| **BR-IP-PRC-007～008** | 诊 **VERIFIED** + 材 COMPLETED + Dispense **PREPARED 或 COMPLETED**（**BR-IP-PRC-019**）+ Charge PENDING_SETTLEMENT |
| **BR-IP-PRC-019** | 开关 **关**→PREPARED；**开**→锁批或 FIFO 定批+COMPLETED+合并扣库（同事务待扣预减）；**DeviceRequest** 不变 |
| **BR-IP-PRC-018** | 护士划价 **`nurse-pricing`** 不计入 **AS-IP-NUR-001** 任务条数 |
| **BR-IP-PRC-009** | 耗材无库房 → **EX-IP-PRC-202** |
| **BR-IP-PRC-010** | 吸氧/心电超限 → **EX-IP-PRC-203** |
| **BR-IP-PRC-011** | 带子项诊疗落**父+子项账单**（子行 `parent_id`、冻价）；SR 仍一条父请求；结算/医保只认账单子行（无子项账单则拒绝，**禁止** SALE 回落） |
| **BR-IP-PRC-012** | 废止（住院划价不绑定诊断） |
| **BR-IP-PRC-013** | accountId + 同事务 |
| **BR-IP-PRC-020** | 写路径 **`reapplyAuthoritativeWritePricing`** 批量重算；`lotNumber` 锁价并落请求；finalize（锁批/FIFO）后再按主发放批号二次重算，再落 Charge |
| **BR-IP-PRC-033** | 可选整批 `pricingTime`：诊 Procedure.recorded（创建 occurrence 同值）；材发放后 `amendDeviceDispenseDisplayTime`；空则当前时刻 |
| **BR-IP-PRC-035** | 有计划出院时 `pricingTime`/`newTime` 不得晚于出院；`InpatientDischargeBusinessTimeGate`；**EX-IP-PRC-206** |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-102** | 就诊不可见 |
| **EX-IP-PRC-201** | 夹带父锚点 |
| **EX-IP-PRC-202** | 耗材库房空 |
| **EX-IP-PRC-203** | 当日时长超限 |
| **EX-IP-PRC-204** | 目录/定价/数量非法 |
| **EX-IP-PRC-205** | 域写失败整批回滚 |
| **EX-IP-PRC-206** | 划价/改时晚于计划出院（**BR-IP-PRC-035**） |

**WriteAssembler 方法签名**

- `reapplyAuthoritativeWritePricing(List<NurseStationPricingWriteItemDto> items, List<Long> encounterIds, List<Long> managedWardLocationIds): void`
- `assertOxygenOrEcgDuration(NurseStationPricingWriteItemDto row): void`
- `NurseStationInpatientBillingProcedureOrchestrator#registerCompletedNursePricingProcedure(Long serviceRequestId, Long encounterId, Long patientId, Long practitionerId, LocalDateTime occurrenceTime): Long`
- `NurseStationInpatientBillingProcedureOrchestrator#cancelCompletedNursePricingProcedures(Collection<Long> serviceRequestIds, Long operatorId): void`

**Mapper 接口方法签名**

- `validateDirectPricingItems(List<NurseStationPricingWriteItemDto> items): void`
- `toCreateFromNursePricingCommand(List<NurseStationPricingWriteItemDto> serviceRows, Long operatorId, Long requesterId): CreateFromNursePricingCommand`
- `toCreateExecutedDeviceOrderCommand(List<NurseStationPricingWriteItemDto> deviceRows, Long operatorId): CreateExecutedDeviceOrderCommand`
- `toGenerateDeviceDispenseFromTemporaryPathCommand(Long deviceReqId, Long operatorId): GenerateDeviceDispenseFromTemporaryPathCommand`
- `toCreateChargeItemCommand(NurseStationPricingWriteItemDto row, String serviceTable, Long serviceId, String dispenseTable, Long dispenseId, Long procedureId, Long operatorId, Long orgId): CreateChargeItemCommand`
- `toCreateNursePricingProcedureRecordCommand(Long serviceRequestId, Long encounterId, Long patientId, Long practitionerId, LocalDateTime occurrenceTime): CreateProcedureRecordCommand`
- `toCreateProcedureCancelOffsetCommand(ProcedureRecordQueryModel completedRow, Long operatorId, LocalDateTime now): CreateProcedureRecordCommand`
- `toStartProcedureExecutionCommand(Long procedureId, Long operatorId): StartProcedureExecutionCommand`
- `toCompleteNursePricingProcedureRecordCommand(Long procedureId, Long operatorId): CompleteProcedureRecordCommand`
- `toCancelNursePricingProcedureRecordCommand(Long newProcedureId, String statusReasonText, Long offsetCompletedProcedureId, Long operatorId): CancelProcedureRecordCommand`
**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `items` 非空且 ≤50 | |
| 2 | Gate | `CurrentPractitionerResolver#requireOperatorId` | — | operatorId = 登录医务人员 |
| 3 | Pre | `CurrentPractitionerResolver#findPractitionerIdOrNull` | 空则回落 operatorId | requesterId（开嘱人） |
| 3a | Pre | 解析 `request.pricingTime` | 空则 `LocalDateTime.now()` | **BR-IP-PRC-033** 整批展示轴 |
| 4 | Gate | `NurseStationInpatientBillingMapper#validateDirectPricingItems` | 禁止 `parentRequestAnchor` | **EX-IP-PRC-201** |
| 5 | Pre | 可见性 Gate | `LocationQueryService` + `EncounterQueryService` + `InpatientPendingSettlementClinicalGateSupport` | 各行 encounter 均可见；`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-PRC-406**（**BR-IP-PRC-029**）；否则 **EX-IP-PRC-102** |
| 5a | Gate | `InpatientDischargeBusinessTimeGate#assertNotAfterPlannedDischargeForEncounters` | `pricingTime` vs 计划出院 | 有生效出院嘱且严格晚于 → **EX-IP-PRC-206**（**BR-IP-PRC-035**） |
| 6 | Pre | `NurseStationInpatientBillingWriteAssembler#reapplyAuthoritativeWritePricing` | 耗材 `performLocation` + 可选 `lotNumber` 锁定批号价，否则 FIFO；包装锚→提交 `unitCode` 线性换算；**不** `fillDevicePerformLocationWhenAbsent` | **BR-IP-PRC-009/020**；缺库位 → **EX-IP-PRC-202**；定价失败 → **EX-IP-PRC-204** |
| 7 | Pre | 分轨 | App 按 `productTableName` 拆 `serviceRows` / `deviceRows` | 非法表 → **EX-IP-PRC-204** |
| 8 | Pre | **for-each 诊疗行（吸氧/心电）** | `NurseStationInpatientBillingWriteAssembler#assertOxygenOrEcgDuration` → `queryOxygenAndECGDuration`（`activityDefinitionId` + 可选 `occurrenceDate=今日`）+ 本次 quantity + 在院小时上限 | 超限 → **EX-IP-PRC-203**；非计时类 → 跳过 |
| 10 | Cmd | `DeviceRequestDomainService#createExecutedDeviceOrder(CreateExecutedDeviceOrderCommand)` | `generateSourceEnum=nurse-pricing`；`basedOn*` 为空 | **写库点①** `wkf_device_request`；返回 id 列表 |
| 11 | Cmd | **for-each ⑩ 中每个 deviceReqId** | `generateDeviceDispenseFromTemporaryPath` → **`DeviceDispenseNurseWorkflowOrchestrator#finalizeInpatientTemporaryPathDispense`**（请求有 `lotNumber` 锁批定批，空则 FIFO；同事务待扣预减）→ **按主发放 `lotNumber` 二次 `reapplyAuthoritativeWritePricing`** → **`amendDeviceDispenseDisplayTime(pricingTime)`** → `priceByNurseStation` | **写库点②**；**无** `procedure_id`；条件 **`decreaseQuantitiesMerged`**；展示轴 **BR-IP-PRC-033**；入账批号与实发一致 **BR-IP-PRC-008/020** |
| 12 | Cmd | **for-each 耗材行** | `ChargeItemDomainService#priceByNurseStation(CreateChargeItemCommand)` | **写库点③**；`serviceTable=wkf_device_request`；`dispenseTable` 锚发放单 |
| 13 | Cmd | `ServiceRequestDomainService#createFromNursePricing(CreateFromNursePricingCommand)` | 诊疗行批量；`basedOn*` 为空 | **写库点④** `wkf_service_request` |
| 14 | Cmd | **for-each ⑬ 返回的 serviceRequestId** | `NurseStationInpatientBillingProcedureOrchestrator#registerCompletedNursePricingProcedure`（`practitionerId=operatorId`，`occurrenceTime=pricingTime`；内部 **create→start→挂主执行人→complete**） | **写库点⑤** `cln_procedure` COMPLETED + `cln_procedure_performer` 主执行人（**须在 COMPLETED 前挂入**，DM-CLN-004）；recorded=pricingTime |
| 15 | Cmd | **for-each ⑭ procedureId** | `ChargeItemDomainService#priceByNurseStation` → **`ActivityDefinitionPackageChargePersistSupport#persistPackageChildChargeItemsAfterParent`**（挂 `procedure_id`） | **写库点⑥**；锚 `wkf_service_request`；带子项诊疗父+子项账单（**BR-IP-PRC-011**） |
| 16 | Asm | Response | 汇总新建 id | |
| — | — | — | — | **App `@Transactional` 包裹 10～15**；**耗材不写 Procedure**；**不写**套餐子项 SR（子项仅 Charge） |

### 3.4 IF-IP-PRC-03 `listNurseStationSupplementParentOrders`（医嘱补费父医嘱列表）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/listNurseStationSupplementParentOrders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterIds` | `List<Long>` | Y | 左侧多选就诊 |
| `therapyType` | `String` | N | `all`/`long`/`temp`；映射 **BR-IP-PRC-014** |
| `keyword` | `String` | N | 医嘱名称关键字（App 二次过滤） |
| `orgId` | `Long` | N | 开立科室筛选 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groups` | `List<NurseStationPricingPatientGroupDto>` | 按 encounter 分组 |

**DTO：`NurseStationPricingPatientGroupDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 |
| `orders` | `List<NurseStationSupplementParentOrderRowDto>` | 父医嘱行 |

**DTO：`NurseStationSupplementParentOrderRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `listRowKey` | `String` | `requestId` + U+0001 + `orderType` |
| `requestId` | `Long` | 药/诊申请主键 |
| `orderType` | `String` | `MED_REQUEST` / `SERVICE_REQUEST` |
| `encounterId` | `Long` | 就诊 |
| `patientId` | `Long` | 患者 |
| `statusEnum` | `String` | 状态 code |
| `statusDisplay` | `String` | 状态展示 |
| `therapyEnum` | `String` | 长临 code |
| `therapyDisplay` | `String` | 长临展示 |
| `adviceDisplayName` | `String` | 医嘱摘要；**嘱托行**优先 `entrust_content`（**BR-IP-ORD-028④**；**AS-IP-NUR-003-SDD §2.5** 同源） |
| `specificationText` | `String` | 规格 |
| `dosageDisplay` | `String` | 剂量展示 |
| `usageDisplay` | `String` | 用法（药品） |
| `frequencyDisplay` | `String` | 频次 |
| `reqOpenedTime` | `LocalDateTime` | 开立时间（`req_opened_time`） |
| `reqAuthoredTime` | `LocalDateTime` | 签发时间（`req_authored_time`；**仅系统追溯**；列表/报表**默认不展示**；未签发可 null） |
| `stopTime` | `LocalDateTime` | 停嘱时间 |
| `requesterName` | `String` | 开立医生；UI 跟在开立时间后（**BR-IP-ORD-031**） |
| `stopperId` | `Long` | 停嘱人主键（`stopper_id`；未停 null） |
| `stopperName` | `String` | 停嘱人姓名；UI 跟在停嘱时间后（**BR-IP-ORD-031**） |
| `performerLocationDisplay` | `String` | 执行/发药位置 |
| `categoryEnum` | `String` | 诊疗类目 |
| `groupId` | `Long` | 药品成组号（`med_medication_request.group_id`；与住院医生站/校对/执行列表同源；仅药嘱行有效，未成组/诊嘱为 null） |
| `groupNo` | `Integer` | 组号 int 形态（`groupId ≤ Integer.MAX_VALUE` 时与 `groupId` 一致，供组内排序；诊嘱 null） |
| `sortNumber` | `Integer` | 排序号 |

> 展示列与 **AS-IP-NUR-003** 校对列表同构裁剪（**含药嘱 `groupId`**）；**不含**余额/费别（**BR-IP-PRC-016**）。

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-101** | 列表异常 |
| **EX-IP-PRC-102** | 所选 encounter 均不可见 |

**Mapper 接口方法签名**

- `resolveTherapyEnum(String therapyType): String`
- `filterByKeywordAndOrg(List<NurseStationSupplementParentOrderRowDto> rows, String keyword, Long orgId): List<NurseStationSupplementParentOrderRowDto>`

**ListAssembler 方法签名**

- `assembleSupplementParentGroups(List<InpatientOrderListSkeleton> skeleton, Map<Long, List<MedicationRequestListQueryModel>> medByEncounter, Map<Long, List<ServiceRequestListModel>> svcByEncounter): List<NurseStationPricingPatientGroupDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `encounterIds` 非空 | |
| 2 | Pre | `CurrentManagedWardLocationIdsResolver#listManagedWardLocationIdsOrEmpty` | — | 管理病区 |
| 3 | Pre | `EncounterQueryService#listInpatientEncounterContexts` | `encounterIdsIn` | 可见集合；空 → **EX-IP-PRC-102** |
| 4 | Pre | `NurseStationInpatientBillingMapper#resolveTherapyEnum` | `therapyType` | **BR-IP-PRC-014** |
| 5 | Pre | `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationSupplementOrders` | 可见 ids、`therapyEnum` | **Q-IP-PRC-002** |
| 6 | Pre | for-each encounter | `MedicationRequestQueryService#listMedicationRequestsByEncounterForDoctorStation` | `medByEncounter` |
| 7 | Pre | for-each encounter | `ServiceRequestQueryService#listServiceRequestsByGenerateSource`（`doctor-prescription`） | `svcByEncounter` |
| 8 | Asm | `NurseStationInpatientBillingListAssembler#assembleSupplementParentGroups` | ⑤～⑦ | 内部委托 §2.5 **OrderView** 三步 |
| 9 | Asm | `NurseStationInpatientBillingMapper#filterByKeywordAndOrg` | Request 筛选 | |
| 10 | Asm | `toListNurseStationSupplementParentOrdersResponse` | ⑧⑨ | |
| — | — | — | — | **仅读**；**不做**频次/槽位展开 |

### 3.5 IF-IP-PRC-04 `confirmSupplementNurseStationPricing`（医嘱补费确认）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/confirmSupplementNurseStationPricing`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<NurseStationPricingWriteItemDto>` | Y | 1～50 行；**每行**须 `parentRequestAnchor` |
| `pricingTime` | `LocalDateTime` | N | 整批客户可见划价时刻（同 IF-02，**BR-IP-PRC-033**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功 |
| `processedCount` | `Integer` | 成功行数 |
| `createdServiceRequestIds` | `List<Long>` | 新建诊疗 id |
| `createdDeviceRequestIds` | `List<Long>` | 新建耗材 id |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PRC-005** | 父须 `doctor-prescription` 药/诊且 `VERIFIED`/`STOPPED`；`based_on_*` 成对 |
| 其余 | 同 IF-IP-PRC-02（库房、时长、子项、事务；**不含诊断**） |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-301** | 缺父锚点/父不可加载/父为护士划价 |
| **EX-IP-PRC-202～205** | 同 IF-02 |

**Mapper 接口方法签名**

- `validateSupplementPricingItems(List<NurseStationPricingWriteItemDto> items): void`
- `resolveBasedOn(NurseStationPricingParentRequestAnchorDto anchor): BasedOnPair`（返回 table + id）
- `assertParentOrderAllowed(NurseStationPricingParentRequestAnchorDto anchor, Long encounterId): void`
- 写路径 Mapper：**复用** §3.3 `toCreateFromNursePricingCommand` / `toCreateExecutedDeviceOrderCommand` 等，Item 填充 `basedOnTable`/`basedOnId`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1～3 | Gate | 同 §3.3 序号 1～3 | — | |
| 4 | Gate | `validateSupplementPricingItems` | 每行 `parentRequestAnchor` 非空 | 缺 → **EX-IP-PRC-301** |
| 5 | Pre | 可见性 | 同 §3.3 序号 5 | **EX-IP-PRC-102** |
| 6 | Pre | **for-each 行** `assertParentOrderAllowed` | 药：`MedicationRequestQueryService#loadMedicationRequestById`；诊：`ServiceRequestQueryService#mustGetServiceRequest` | 须 `doctor-prescription` + `VERIFIED`/`STOPPED`；护士划价父 → **EX-IP-PRC-301** |
| 7 | Pre | `resolveBasedOn` | 药→`med_medication_request`；诊→`wkf_service_request` | 写入 Command Item |
| 8～17 | Cmd/Asm | **复用 §3.3 序号 6～17** | Command Item 带成对 `basedOnTable`/`basedOnId` | **BR-IP-PRC-005**；同事务 |
| — | — | — | — | **禁止** 无父锚点行 |

### 3.6 IF-IP-PRC-05 `listNurseStationPricedOrders`（已划价列表）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/listNurseStationPricedOrders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterIds` | `List<Long>` | Y | 多选就诊 |
| `pricedTimeFrom` | `LocalDateTime` | N | 划价客户可见时间起（展示轴，**BR-IP-PRC-030**） |
| `pricedTimeTo` | `LocalDateTime` | N | 划价客户可见时间止（展示轴，**BR-IP-PRC-030**） |
| `parentRequestId` | `Long` | N | 父医嘱 id（补费子行过滤，**BR-IP-PRC-015**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groups` | `List<NurseStationPricingPatientGroupDto>` | 按 encounter 分组 |

**DTO：`NurseStationPricedOrderRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `listRowKey` | `String` | 稳定行键 |
| `orderId` | `Long` | InpatientOrderListSkeleton 主键 / 请求 id |
| `orderType` | `String` | `SERVICE_REQUEST` / `DEVICE_REQUEST` |
| `encounterId` | `Long` | 就诊 |
| `patientId` | `Long` | 患者 |
| `productDisplayName` | `String` | 项目名称 |
| `quantity` | `BigDecimal` | 数量 |
| `unitCode` | `String` | 单位 code |
| `unitDisplay` | `String` | 单位展示 |
| `unitPrice` | `BigDecimal` | 单价 |
| `totalPrice` | `BigDecimal` | 总价 |
| `pricedTime` | `LocalDateTime` | 划价客户可见时间（**BR-IP-PRC-030**）：诊疗=净剩 COMPLETED Procedure.`recorded_time`；耗材=主发放 `dispense_time`（无则 `prepare_time`）；**非**骨架 `created_at` |
| `chargeStatusEnum` | `String` | 费用状态 code |
| `chargeStatusDisplay` | `String` | 费用状态展示 |
| `dispenseStatusEnum` | `String` | 耗材发放状态；诊疗行 null |
| `dispenseStatusDisplay` | `String` | 发放状态展示 |
| `pricingModeCode` | `String` | `DIRECT` / `SUPPLEMENT` |
| `pricingModeDisplay` | `String` | 直接划价 / 医嘱补费 |
| `basedOnTable` | `String` | 上级医嘱来源表（补费；与域 `based_on_table` 一致，如 `med_medication_request`、`wkf_service_request`）；直接划价 null |
| `basedOnId` | `Long` | 上级医嘱来源主键（补费；与域 `based_on_id` 一致，对应 IF-IP-PRC-03 父行 `requestId`）；直接划价 null |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-101** | 列表异常 |
| **EX-IP-PRC-102** | 不可见 |

**Mapper 接口方法签名**

- `toChargeItemQueryByServiceIds(Collection<Long> serviceRequestIds, Collection<Long> deviceRequestIds): ChargeItemQueryCommand`
- `resolvePricingMode(InpatientOrderListSkeleton skeleton): String`

**ListAssembler 方法签名**

- `buildPricedRowFactsByOrderId(List<InpatientOrderListSkeleton> skeleton): Map<Long, PricedOrderRowFacts>`
- `assemblePricedOrderGroups(List<InpatientOrderListSkeleton> skeleton, Map<Long, List<ChargeItem>> chargeByServiceId, Map<Long, List<Long>> dispenseIdsByDeviceReqId, Map<Long, PricedOrderRowFacts> pricedRowFactsByOrderId): List<NurseStationPricingPatientGroupDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `encounterIds` 非空 | — | |
| 2 | Pre | 可见性 | `Location` + `EncounterQueryService` | **EX-IP-PRC-102** |
| 3 | Pre | `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationPricedOrders` | 可见 ids、`parentRequestId`（**不**按 `created_at` 预过滤） | **Q-IP-PRC-001** |
| 4 | Pre | 收集 service/device 请求 id | 骨架行 | |
| 5 | Pre | `ChargeItemQueryService#listChargeItemsByServiceIds` | ④ ids | 费用态 |
| 6 | Pre | 批量回源 `ServiceRequest`/`DeviceRequest` + `ProductDefinition` | ④ ids | `buildPricedRowFactsByOrderId` → 名称/数量/单位 |
| 7 | Pre | `DeviceDispenseQueryService#mapDeviceDispenseIdsByDeviceRequestIds` + Procedure/发放展示时刻 | ④ ids | 发放态 + **BR-IP-PRC-030** 展示轴 |
| 8 | Asm | `NurseStationInpatientBillingListAssembler#assemblePricedOrderGroups` | ③～⑦ | **BR-IP-PRC-015/016/030**；组装 `pricedTime` |
| 9 | Asm | `filterPricedGroupsByDisplayTime` | `pricedTimeFrom`/`pricedTimeTo` | 按展示轴闭区间过滤（空区间不过滤） |
| 10 | Asm | Response | — | |
| — | — | — | — | **仅读** |

### 3.7 IF-IP-PRC-06 `cancelNurseStationPricing`（取消划价）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/cancelNurseStationPricing`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<NurseStationPricingCancelItemDto>` | Y | 1～50 行；来自 **IF-IP-PRC-05** 已划价列表勾选 |

**DTO：`NurseStationPricingCancelItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderType` | `String` | Y | `SERVICE_REQUEST` / `DEVICE_REQUEST`（与列表 `NurseStationPricedOrderRowDto.orderType` 一致） |
| `requestId` | `Long` | Y | 诊疗/耗材请求主键（与列表 `orderId` 一致） |
| `encounterId` | `Long` | Y | 就诊（可见性 Gate 与列表分组一致） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功 |
| `processedCount` | `Integer` | 成功取消行数 |
| `cancelledServiceRequestIds` | `List<Long>` | 本批已冲销处理的诊疗请求 id（Request **未**软删） |
| `cancelledDeviceRequestIds` | `List<Long>` | 本批已冲销处理的耗材请求 id（Request **未**软删） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PRC-021** | 非空、≤50、禁止重复 `(orderType, requestId)` |
| **BR-IP-PRC-022** | 须 `generate_source_enum=nurse-pricing` 且可加载 |
| **BR-IP-PRC-023** | 关联 Charge 须全部 `PENDING_SETTLEMENT`；已结算/退费 → **EX-IP-PRC-403**；**排除** `ChargeItemRegenerationChainSupport` 判定的链上历史祖先 `SETTLED`（**BR-REF-020** / **BR-IP-RVS-011**） |
| **BR-IP-PRC-024** | **整行冲销留痕**；Request 保持完成态；原 Charge 保留 + 负向冲销行；**非**部分数量冲销；反结再生正向（数量>0 且 `refund_id`→SETTLED）仍可冲；仅 `isPricingOffsetLine()` 负向行视为已冲 |
| **BR-IP-PRC-025** | 耗材：按 **BR-IP-PRC-019** 两轨——**已发** `revertInpatientPricingDeviceDispenses`；**未发** 软删 Dispense → **offset** Charge → **不**软删 DeviceRequest |
| **BR-IP-PRC-026** | 诊疗：`cancelCompletedNursePricingProcedures`（CANCEL 对冲）→ **offset** Charge → **不**软删 ServiceRequest |
| **BR-IP-PRC-027** | 补费父医嘱**不变** |
| **BR-IP-PRC-028** | 同事务整批回滚 |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-102** | 就诊不可见 |
| **EX-IP-PRC-401** | 未选/重复键 |
| **EX-IP-PRC-402** | 非 nurse-pricing 或不可加载 |
| **EX-IP-PRC-403** | 费用已结算/退费中 |
| **EX-IP-PRC-404** | 发放态不可回退 |
| **EX-IP-PRC-405** | 域写失败整批回滚 |
| **EX-IP-PRC-406** | 就诊 `PENDING`/`COMPLETED`/`DISCHARGED`（**BR-IP-PRC-029**） |

**Mapper 接口方法签名**

- `validateCancelPricingItems(List<NurseStationPricingCancelItemDto> items): void`
- `resolveCancelServiceTable(String orderType): String`
- `toOffsetNursePricingChargeItemsByServiceAnchorsCommand(Long encounterId, String serviceTable, Long serviceId, Long operatorId): OffsetNursePricingChargeItemsByServiceAnchorsCommand`
- `toRemoveUndispensedDeviceDispenseCommand(Long deviceRequestId, Long operatorId): RemoveUndispensedDeviceDispenseCommand`
- `toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToQty): IncreaseQuantityCommand`

> **注**：`toRemoveFromNursePricingCommand` / `toRevokeExecutedDeviceOrderCommand` 仍保留于 Mapper（历史整行还原），**IF-IP-PRC-06 冲销路径不再调用**。

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `items` 非空且 ≤50 | |
| 2 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | operatorId |
| 3 | Gate | `validateCancelPricingItems` | 禁止重复 `(orderType, requestId)` | **EX-IP-PRC-401** |
| 4 | Pre | 可见性 Gate | 各行 `encounterId` + `InpatientPendingSettlementClinicalGateSupport` | 不可见 → **EX-IP-PRC-102**；`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-PRC-406** |
| 5 | Pre | 分轨加载 | `ServiceRequestQueryService#mapListModelsByIds` / `DeviceRequestQueryService#mapListModelsByIds` | 断言 **nurse-pricing**（**BR-IP-PRC-022**） |
| 6 | Pre | `ChargeItemQueryService#listChargeItemsByServiceIds` | ⑤ ids | 断言全部 **PENDING_SETTLEMENT** + 存在未冲销正向行（**BR-IP-PRC-023/024**） |
| 7 | Cmd | **for-each 耗材行** | `revertInpatientPricingDeviceDispenses` → `offsetNursePricingChargeItemsByServiceAnchors` | **BR-IP-PRC-025**；**不**软删 DeviceRequest |
| 8 | Cmd | **诊疗批量** | `NurseStationInpatientBillingProcedureOrchestrator#cancelCompletedNursePricingProcedures` | **BR-IP-PRC-026** Procedure CANCEL 对冲（内部 **create→start→挂 PRIMARY→cancel**） |
| 9 | Cmd | **for-each 诊疗行** | `offsetNursePricingChargeItemsByServiceAnchors` | **BR-IP-PRC-026**；**不**软删 ServiceRequest |
| 10 | Asm | Response | 汇总已冲销处理的请求 id | |
| — | — | — | — | **App `@Transactional` 包裹 7～9** |

### 3.8 IF-IP-PRC-07 `changeNurseStationPricingBusinessTime`（修改划价客户可见时间）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/changeNurseStationPricingBusinessTime`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<NurseStationPricingCancelItemDto>` | Y | 1～50 行；来自 **IF-IP-PRC-05** 已划价列表勾选；行键 DTO 与冲销同构 |
| `newTime` | `LocalDateTime` | Y | 新的客户可见时刻（统一写入各行展示轴） |

**DTO：`NurseStationPricingCancelItemDto`**（与 §3.7 同构）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderType` | `String` | Y | `SERVICE_REQUEST` / `DEVICE_REQUEST` |
| `requestId` | `Long` | Y | 诊疗/耗材请求主键（与列表 `orderId` 一致） |
| `encounterId` | `Long` | Y | 就诊（可见性 Gate 与列表分组一致） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功 |
| `processedCount` | `Integer` | 成功改时行数 |
| `message` | `String` | 提示文案（可选） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PRC-021** | `items` 非空、≤50、禁止重复 `(orderType, requestId)`（复用冲销校验） |
| **BR-IP-PRC-022** | 须 `generate_source_enum=nurse-pricing` 且可加载；就诊一致 |
| **BR-IP-PRC-023** | 关联 Charge 须全部 `PENDING_SETTLEMENT`；已结算/退费 → **EX-IP-PRC-403**；**排除**再生链祖先 `SETTLED` |
| **BR-IP-PRC-029** | 就诊 `PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-PRC-406** |
| **BR-IP-PRC-030** | 展示轴语义（改后列表 `pricedTime` 与区间过滤同源） |
| **BR-IP-PRC-031** | **诊疗**仅改净剩 COMPLETED Procedure.`recorded_time`（**不改** `occurrence_time`、Charge.occurrence、Request 创建/开立）；**耗材**仅改主发放 `dispense_time`（无则 `prepare_time`） |
| **BR-IP-PRC-032** | 可改行范围=待结算护士划价行；`newTime` 必填 |
| **BR-IP-PRC-035** | 有计划出院时 `newTime` 不得晚于出院；**EX-IP-PRC-206** |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PRC-102** | 就诊不可见 |
| **EX-IP-PRC-206** | 改时晚于计划出院（**BR-IP-PRC-035**） |
| **EX-IP-PRC-401** | 未选/重复键 |
| **EX-IP-PRC-402** | 非 nurse-pricing 或不可加载 |
| **EX-IP-PRC-403** | 费用已结算/退费中 |
| **EX-IP-PRC-406** | 就诊 `PENDING`/`COMPLETED`/`DISCHARGED`（**BR-IP-PRC-029**） |
| **EX-IP-PRC-407** | 域写失败整批回滚；含 `newTime` 空、无净剩 COMPLETED Procedure、无主发放 |

**Mapper 接口方法签名**

- `validateCancelPricingItems(List<NurseStationPricingCancelItemDto> items): void`（复用冲销）

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `validateCancelPricingItems` | `items` 非空且 ≤50、禁止重复键 | **EX-IP-PRC-401** |
| 2 | Gate | `newTime` 非空 | — | 空 → **EX-IP-PRC-407** |
| 3 | Gate | `CurrentPractitionerResolver#requireOperatorId` | — | operatorId；失败 → **EX-IP-PRC-407** |
| 4 | Pre | 可见性 Gate | 各行 `encounterId` + `InpatientPendingSettlementClinicalGateSupport` | 不可见 → **EX-IP-PRC-102**；出院结算轨 → **EX-IP-PRC-406** |
| 4a | Gate | `InpatientDischargeBusinessTimeGate#assertNotAfterPlannedDischargeForEncounters` | `newTime` vs 计划出院 | 严格晚于 → **EX-IP-PRC-206**（**BR-IP-PRC-035**） |
| 5 | Pre | 分轨加载 | `ServiceRequestQueryService#mapListModelsByIds` / `DeviceRequestQueryService#mapListModelsByIds` | 断言 **nurse-pricing** + 就诊一致（**BR-IP-PRC-022**） |
| 6 | Pre | `ChargeItemQueryService#listChargeItemsByServiceIds` | ⑤ ids；`excludeSupersededSettledLines=true` | 断言全部 **PENDING_SETTLEMENT**（**BR-IP-PRC-023/032**） |
| 7 | Pre | `DeviceDispenseQueryService#mapPrimaryDispenseIdByDeviceReqIds` | 耗材 req ids | 主发放定位 |
| 8 | Cmd | **for-each 诊疗行** | `ProcedureQueryService#findNetCompletedProcedureIdByServiceRequest` → `ProcedureDomainService#amendProcedureRecordedTime` | **BR-IP-PRC-031**；无净剩 → **EX-IP-PRC-407** |
| 9 | Cmd | **for-each 耗材行** | `DeviceDispenseDomainService#amendDeviceDispenseDisplayTime` | **BR-IP-PRC-031**；无主发放 → **EX-IP-PRC-407** |
| 10 | Asm | Response | `success`/`processedCount`/`message` | |
| — | — | — | — | **App `@Transactional` 包裹 8～9** |

## 4. QueryService 与事件刷新

| QueryService | 查询方法 | 本 AS 用途 |
| --- | --- | --- |
| `InpatientOrderListSkeleton` | `listInpatientNurseStationSupplementOrders` | PRC-03 父医嘱骨架（**Q-IP-PRC-002**） |
| `InpatientOrderListSkeleton` | `listInpatientNurseStationPricedOrders` | PRC-05 已划价骨架（**Q-IP-PRC-001**） |
| `InpatientEncounterContextQueryModel` | `listInpatientEncounterContexts` | **IF-02～07** 可见 encounter 门禁 |
| `ProductDefinition` | `pageProductDefinition` | PRC-01 选品（非 OrderInfo） |

**QueryService 查询语义（最终一致）**

| 领域事件 | QueryService | 更新动作 |
| --- | --- | --- |
| `NursePricingServiceRequestCreatedEvent` | `InpatientOrderListSkeleton` | INSERT/UPSERT nurse-pricing **诊疗**行（§4.2；`status_enum`←`event.statusEnum` **VERIFIED**） |
| `NursePricingChargeItemCreatedEvent` | `InpatientChargeDetail` | INSERT；写侧 ChargeItem **PENDING_SETTLEMENT**；读侧 **PENDING（10）** |
| `NursePricingDeviceRequestCreatedEvent` | `InpatientOrderListSkeleton` | INSERT/UPSERT nurse-pricing **耗材**行（§4.2.3） |
| `NursePricingChargeItemOffsetEvent` | 费用读侧 | 冲销行落库；待结算占用净额轧差；费用明细可见「冲销」/来源「护士冲销」 |
| `NursePricingChargeItemRemovedEvent` | （历史） | 旧物理删路径；冲销后不再发布 |
| `NursePricingServiceRequestRemovedEvent` / `NursePricingDeviceRequestRemovedEvent` | （历史） | 旧软删 Request；冲销后 IF-06 不再发布 |
| `DeviceDispenseCreatedEvent` | 发放读侧 | 耗材待发；**不**写 InpatientOrderListSkeleton |
| 医生校对/停嘱事件 | `InpatientOrderListSkeleton` | PRC-03 父医嘱可见性 |

字段级映射以 `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §4.2、`DM §6.2 各 Request QueryService` §3 为唯一真源。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | §3 序号 | 领域 / 资源写入顺序 |
| --- | --- | --- | --- |
| **PRC-02** | `confirmDirectNurseStationPricing` | 10～16 | ① `wkf_device_request` ② `wkf_device_dispense`（**PREPARED 或 COMPLETED**，**BR-IP-PRC-019**）③ `adm_charge_item`（材）④ `wkf_service_request` ⑤ `adm_charge_item`（诊）⑥ 子项重复 ④⑤ |
| **PRC-04** | `confirmSupplementNurseStationPricing` | 8～17（复用 02） | 同 PRC-02，且 Request/Item 带 `based_on_*` |
| **PRC-06** | `cancelNurseStationPricing` | 7～9 | ① Dispense 回退/软删（材）② `adm_charge_item` **冲销**（原行保留 + 负向行）③ Request **保持完成态** ④ 库存 `increaseQuantities` |
| **PRC-07** | `changeNurseStationPricingBusinessTime` | 8～9 | ① `cln_procedure.recorded_time`（诊）② `wkf_device_dispense.dispense_time`/`prepare_time`（材）；**不写** occurrence / Charge / Request |

### 5.2 事务边界 / 失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-IP-PRC-02 | `@Transactional` 含 10～16 全部 Cmd | 任一步失败 → 全回滚 → **EX-IP-PRC-205** |
| IF-IP-PRC-04 | 同 PRC-02 | 父非法 → **EX-IP-PRC-301**；其余同 02 |
| IF-IP-PRC-06 | `@Transactional` 含 7～8 全部 Cmd | 任一步失败 → 全回滚 → **EX-IP-PRC-405** |
| IF-IP-PRC-07 | `@Transactional` 含 8～9 全部 Cmd | 任一步失败 → 全回滚 → **EX-IP-PRC-407** |
| IF-IP-PRC-01/03/05 | 无写事务 | — |

> **顺序约束**：耗材须 **DeviceRequest → Dispense → Charge** 同事务（**AS §7**）；诊疗 **ServiceRequest → Procedure（create→start→挂主执行人→complete）→ Charge**；取消划价诊疗须 **Procedure CANCEL 对冲 → Charge offset**（Request 不软删）；取消划价耗材须 **Dispense 回退 → Charge offset**（Request 不软删）；改时诊疗/耗材仅写展示轴字段（**BR-IP-PRC-031**）。

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 读接口天然幂等 | `pageNurseStationPricingCatalog` 等 | §3.2/3.4/3.6 |
| 写接口非幂等 | 重复提交可能重复建单/重复取消/重复改时 | §3.3、§3.7、§3.8 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 批量 ≤50 | Gate 步骤 1 | §3.3/3.5/3.7/3.8 |
| 域层状态断言 | `createFromNursePricing` / `createExecutedDeviceOrder` / `amendProcedureRecordedTime` 内部 | §3.3 序号 10、13；§3.8 |

### 6.3 重试与容错

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 读失败 **EX-IP-PRC-101** | 列表/目录 catch | §3.2/3.4/3.6 |
| 写失败整批回滚 | `confirmDirect*` / `confirmSupplement*` / `cancelNurseStationPricing` / `changeNurseStationPricingBusinessTime` | §3.3、§3.7、§3.8 |

### 6.4 数据脱敏

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 列表不返回余额/费别 | DTO 无字段（**BR-IP-PRC-016**） | §3.4/3.6 |

### 6.5 审计日志

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| `operatorId` 写入各 Command | Domain Command | §3.3 序号 2 |
| `requesterId` 为当前 Practitioner | `CreateFromNursePricing` Item | §3.3 序号 3 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 选品仅诊疗+耗材 | 无药品行；`pricingFlag=1` | P0 |
| 选品耗材库位 | 有 `encounterId` 时按住院科室取药模板耗材房分页；无模板时空页 | P0 |
| 直接划价多患者 | 诊 **VERIFIED** + **Procedure COMPLETED**（登录人 recorder+主执行人）+ Charge 待结算；材 COMPLETED + Dispense 两轨 | P0 |
| 无 based_on | 直接划价行 `based_on_id` 空 | P0 |
| 补费父 VERIFIED/STOPPED | `based_on_*` 正确 | P0 |
| 混批拒绝 | IF-02 带父锚点 **201**；IF-04 无父 **301** | P0 |
| 耗材无库房 | **EX-IP-PRC-202** | P0 |
| 吸氧/心电超限 | **EX-IP-PRC-203** | P0 |
| 套餐子项 | 父+子费用均待结算 | P1 |
| 已划价列表 | 直接/补费均可；`pricedTime` 展示轴（诊=recorded、材=发放）；按展示轴时间/父过滤 | P0 |
| IF-03 展示 | 与校对列表同口径；含 STOPPED | P0 |
| 药嘱成组（IF-03） | 同 `groupId` 多行回传一致组号；未成组为 null；诊嘱行无 `groupId` | P1 |
| 取消划价整行冲销 | 仅待结算且未冲销可取消；Request 保持完成态；Charge 负向留痕；补费父医嘱不变；Dispense 两轨回退+库存回补 | P0 |
| 取消划价已结算拒绝 | **EX-IP-PRC-403** | P0 |
| 修改划价客户可见时间 | 仅待结算可改；诊只改 `recorded_time`；材只改发放/拣配时刻；不改 occurrence/Charge/Request 开立 | P0 |
| 改时已结算拒绝 | **EX-IP-PRC-403** | P0 |
| 改时域写失败 | **EX-IP-PRC-407** | P0 |
| 待结算就诊禁止划价/取消划价/改时 | **EX-IP-PRC-406** | P0 |

### 7.2 非功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 多 encounter 列表 | 单次 Q-IP-PRC-001/002 + 批量补明细 | P0 |
| 写 ≤50 行 | 超限 Gate 拒绝 | P1 |

### 7.3 QueryService一致性

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 划价后刷新 PRC-05 | InpatientOrderListSkeleton 出现 nurse-pricing 行 | P1 |
| 取消后刷新 PRC-05 | InpatientOrderListSkeleton 剔除已取消行 | P1 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| POST `pageNurseStationPricingCatalog` | `POST /api/app/nurseStationInpatientBilling/pageNurseStationPricingCatalog` | P0 |
| POST `confirmDirectNurseStationPricing` | 路径与方法名一致 | P0 |
| POST `listNurseStationSupplementParentOrders` | 路径与方法名一致 | P0 |
| POST `confirmSupplementNurseStationPricing` | 路径与方法名一致 | P0 |
| POST `listNurseStationPricedOrders` | 路径与方法名一致 | P0 |
| POST `cancelNurseStationPricing` | 路径与方法名一致 | P0 |
| POST `changeNurseStationPricingBusinessTime` | 路径与方法名一致 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 集成测试待执行 | IF-IP-PRC-01～07 与 **IF-IP-ARG-02～08** 应用层已成码；P0 用例待集成环境跑通 | P1 |
| 前端 Tab | 划价页 + **自动计费** Tab（**AS-IP-NUR-013**）待 openhis-pro-ui 对接 | P1 |

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v2.28 | 2026-10-18 | **代码先行回写**：**BR-IP-PRC-008/019/020**：目录 `lotNumber` 落请求并自动扣库锁批定批；空批号 FIFO；同事务待扣预减；对齐 **AS v2.25** |
| v2.27 | 2026-10-17 | **代码先行回写**：**BR-IP-PRC-024** 冲销源区分冲销负向 vs 反结再生正向；对齐 **AS v2.24** / **DM-ADM-011 v1.6.28** |
| v2.26 | 2026-10-09 | **代码先行回写**：**BR-IP-PRC-035** / **EX-IP-PRC-206**：§3.3/§3.8 增 `InpatientDischargeBusinessTimeGate`；对齐 **AS v2.21** |
| v2.25 | 2026-10-06 | **代码先行回写**：**BR-IP-PRC-011** 禁 SALE 回落；类表口径改为带子项诊疗/子项账单 |
| v2.23 | 2026-09-27 | **BR-IP-PRC-007 / IF-02/04**：`registerCompletedNursePricingProcedure` 改为 **create→start→挂主执行人→complete**（修复 COMPLETED 后再挂执行人）；类名对齐 `NurseStationInpatientBillingProcedureOrchestrator`；对齐 **AS v2.18** / **DM-CLN-004** |
| v2.22 | 2026-08-17 | **BR-IP-PRC-020**：耗材 finalize 后按主发放批号二次 `reapplyAuthoritativeWritePricing` 再挂账；对齐 **AS v2.7** |
| v2.21 | 2026-08-06 | **IF-IP-PRC-01**：耗材库存改住院科室取药模板耗材房；去掉货位列；对齐 **AS v2.16** |
| v2.20 | 2026-08-06 | **IF-IP-PRC-01** 目录增 `totalVolume`/`locationName`；**IF-02/04** Request 增 `pricingTime`（**BR-IP-PRC-033**）直写展示轴；对齐 **AS v2.15** |
| v2.19 | 2026-08-06 | **IF-IP-PRC-07** `changeNurseStationPricingBusinessTime`（§3.8）；`pricedTime` 展示轴（**BR-IP-PRC-030～032**）；方法白名单/写库顺序/测试补 07；**EX-IP-PRC-407**；对齐 **AS v2.14** / **DM-CLN-004** / **DM-WKF-007** |
| v2.18 | 2026-08-05 | **IF-IP-PRC-01**：耗材改为库存批号分页驱动（`InventoryItemQueryService#queryInventoryItemsByPage` + 管辖药房）；`totalCount` 与 content 对齐；对齐 **AS v2.13** |
| v2.17 | 2026-08-05 | **IF-IP-PRC-01 / BR-IP-PRC-017**：耗材仅登录人管辖科室药房有库存记录才展示（含数量 0）；废止不限库位回落；对齐 **AS v2.12** |
| v2.16 | 2026-08-05 | **IF-IP-PRC-01**：耗材按品×批号展开（`lotNumber`/`remainingQuantity`/`inventoryItemId`）；默认拆零槽价；**IF-02/04** 写项增 `lotNumber`，权威价按提交单位线性换算；对齐 **AS v2.11** |
| v2.15 | 2026-07-24 | **BR-IP-PRC-011**：套餐展开口径（子项 SALE、不分摊、数量=父×`children_num`）；类名 **`ActivityDefinitionPackageChildResolver`**；医保 **`ChsActivityPackageFeeExpandSupport`** |
| v2.14 | 2026-07-20 | **UI**：IF-IP-PRC-06 按钮文案「冲销」；费用明细冲销展示对齐 **AS-IP-NUR-005 v2.9** / **AS-IP-NUR-006 v1.11** |
| v2.13 | 2026-07-18 | **BR-IP-ORD-031**：补费父医嘱行增补 `stopperId`/`stopperName`；开立/停嘱时间展示跟人名 |
| v2.12 | 2026-07-17 | **IF-IP-PRC-06**：取消划价改为整行冲销留痕（`offsetNursePricingChargeItemsByServiceAnchors`）；Request 不软删；已划价列表净额过滤；对齐 **AS v2.8** / **DM-ADM-011** |
| v2.11 | 2026-07-16 | **BR-IP-ORD-028④**：IF-IP-PRC-03 父医嘱 `adviceDisplayName` 嘱托行优先 `entrust_content`；**OrderView** 同源；对齐 **AS-IP-NUR-003-SDD v1.3.4** |
| v2.10 | 2026-07-11 | **BR-IP-PRC-029**：§3.3/3.7 可见性 Gate 增补就诊态；**EX-IP-PRC-406**；对齐 **AS v2.6** / **BR-IP-FLW-008** |
| v2.8 | 2026-06-20 | **support 落点对齐**：废止 App `orderview`；`NurseStationOrderViewListAssembler` → `module.querysupport.nursestation`；`DeviceDispenseNurseWorkflowsupport → `module.orchestration.nursestation`；对齐 **AS v2.4** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.nursestation-device-dispense）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v2.7 | 2026-06-19 | **IF-IP-PRC-06 / BR-IP-PRC-025**：取消划价耗材按 **BR-IP-PRC-019** 两轨——已发 `revertInpatientPricingDeviceDispenses`（待退耗材单对冲+库存回补）；未发软删发放单；对齐 **AS v2.3** |
| v2.6 | 2026-06-19 | 实现阶段口径更新：**AS-IP-NUR-013** 同 Bean 扩展 IF-IP-ARG-02～08 已成码；§7.5 测试缺口调整 |
| v2.5 | 2026-06-19 | **IF-IP-PRC-01**：`PageNurseStationPricingCatalogRequest` 增补 **`categoryCode`** 透传（支撑 **AS-IP-NUR-013 IF-IP-ARG-01** 床位服务选品，**不新增** HTTP） |
| v2.4 | 2026-06-18 | **规范刷码**：Mapper 增补 `toIncreaseQuantityCommand`/`toRemoveUndispensedDeviceDispenseCommand`；库存回补对齐 `increaseQuantities` |
| v2.4 | 2026-06-18 | **应用层** `cancelNurseStationPricing`（IF-IP-PRC-06）已成码 |
| v2.4 | 2026-06-18 | 同步 **DM-WKF-004 v1.1.16**、**DM-WKF-003 v1.2.8**、**DM-ADM-011 v1.5.5** 取消划价域契约 |
| v2.4 | 2026-06-18 | 关闭 **GAP-IP-PRC-007～010** 域能力；对齐 **AS v2.2** Gap 落点 |
| v2.4 | 2026-06-18 | 新增 **IF-IP-PRC-06 取消划价**（§3.7）；**BR-IP-PRC-021～028** |
| v2.3 | 2026-06-08 | **BR-IP-PRC-019**：§2.2 白名单、`finalizeInpatientTemporaryPathDispense`；§3.3 移除 `promoteDeviceDispenseToPreparation`；Dispense 终态 **PREPARED/COMPLETED**；对齐 **AS v2.1** |
| v2.3 | 2026-06-11 | **IF-IP-PRC-01/02/04 库位透传**：目录耗材解析 `defaultDispenseLocationId` + FIFO 售价；新增 `prepickedProductKeys`；写路径 `performLocation` 经 `resolveDraftChargePriceLine`/`clientResolvedDispenseLocationId`，**废止** `fillDevicePerformLocationWhenAbsent` |
| v2.2 | 2026-05-28 | **六维对账**：`NurseStationInpatientBillingAppService` 五方法与 IF-IP-PRC-01～05 对齐；写方法 `confirmDirect*`/`confirmSupplement*` 已声明 `@Transactional` |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.8 | 2026-05-27 | **IF-IP-PRC-03**：`NurseStationSupplementParentOrderRowDto` 增补药嘱 **`groupId`**（`mapSupplementRow` 自 `DisplayCore` 拷贝；与 **AS-IP-NUR-003/004** 列表同源） |
| v1.7 | 2026-05-24 | 诊疗划价创建态 **VERIFIED**；**BR-IP-PRC-018**；事件/投影 **`statusEnum`**；对齐 **AS v2.0** / **DM-WKF-004 v1.1.7** |
| v1.6 | 2026-05-24 | **IF-IP-PRC-05**：出参移除 `parentAdviceDisplayName` 及父医嘱摘要预热编排 |
| v1.5 | 2026-05-24 | **IF-IP-PRC-05**：出参移除 `parentRequestId`/`parentOrderType`，仅保留 **`basedOnTable`/`basedOnId`** |
| v1.4 | 2026-05-24 | **IF-IP-PRC-05**：`NurseStationPricedOrderRowDto` 增补 **`basedOnTable`/`basedOnId`**；对齐 **AS v1.6** |
| v1.3 | 2026-05-24 | 对齐 **`ProductDefinition` QueryService v3.12**：诊疗目录投影须回填单位/状态列，IF-IP-PRC-01 **`unitCode`/`unitBands`** 可读（叠加 **v1.2** 槽语义） |
| v1.2 | 2026-05-24 | **IF-IP-PRC-01**：`unitCode`/`unitBands` 与 IF-DOR-11 对齐（**`PACKAGE` 槽**、主单位 **`defEncounterUnitCode`→`packageUnitCode`**、诊疗仅 PACKAGE 有值）；**BR-IP-PRC-017** 说明更新 |
| v1.1 | 2026-05-24 | 对齐 **AS v1.4**：**IF-IP-PRC-01** 移除 `encounterId`/`patientId` 与可见性编排序列；响应字段对齐 B.10.11（`content`/`totalCount`） |
| v1.0 | 2026-05-24 | 首版：对齐 **AS-IP-NUR-005 v1.2**；IF-IP-PRC-01～05 编排序列、DTO、HTTP；复用 **orderview**；域能力 Gap 已关；`EX_IP_PRC_*` 目标态待生成 |
