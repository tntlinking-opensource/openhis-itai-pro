# 医技工作站 · 医技计费 · 应用服务详细设计（SDD）

> **编码**：AS-MT-CHG-001-SDD · **版本**：v1.2.0 · **日期**：2026-10-11  
> **上级 AS**：`health/AppService/AS-MT-CHG-001-医技工作站医技计费.md`  
> **同构参照**：`AS-IP-NUR-005-住院护士站划价-SDD.md`（耗材 + 诊疗直接划价；医技更窄：登录 TECH 执行科诊疗 + 登录 TECH 下属耗材房）  
> **实现阶段口径**：**目标态全量契约**（应用层由本 SDD 驱动生成；**禁止**在本文件写 Gap）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 医技工作站 · 医技计费（**仅住院**；诊疗 + 耗材） |
| **边界** | IF-MT-CHG-01～07；门诊无入口；禁止药品；耗材划价**不**写 Procedure；诊疗划价写 Procedure（org=登录 TECH） |
| **编排权威** | AS-MT-CHG-001 §2 / §4 |
| **方法权威** | `ServiceRequestDomainService#createFromMedicalTechPricing`；护士划价同族 Device/Charge/Procedure |
| **严格对齐** | 诊疗活动 `org_id`=登录 TECH；库房 ∈ 登录 TECH 下属耗材房；Charge **PENDING_SETTLEMENT**；`generate_source=medical-tech-pricing` |
| **实现阶段口径** | 目标态全量契约 |

### 1.2 术语口径

| 术语 | 口径 |
| --- | --- |
| **医技计费** | 住院诊疗（本科 TECH）+ 耗材（本科耗材房）；来源医技划价 |
| **与护士划价差异** | 护士禁 TECH 执行科诊疗（BR-IP-PRC-034）；医技承接该轨 + 锁定本科耗材房 |
| **HTTP** | 一律 `POST /api/app/medicalTechCharging/{methodName}` |
| **事务** | IF-02 / IF-04 / IF-07 整批同事务回滚 |
| **字段口径** | Long 主键不转 Number；分页用 `PagedResult` 的 `content`/`totalCount` |

---

## 2. 代码结构

### 2.1 包路径

```text
com.zxplat.domain.application.medicaltech.charging/
  service/MedicalTechChargingAppService.java
  mapper/MedicalTechChargingMapper.java
  mapper/MedicalTechChargingMapperImpl.java
  request/
    PageMedicalTechDeviceCatalogRequest.java
    ConfirmMedicalTechDevicePricingRequest.java
    ListMedicalTechPricedDevicesRequest.java
    CancelMedicalTechDevicePricingRequest.java
    LoadMedicalTechChargingInitRequest.java
  response/
    PageMedicalTechDeviceCatalogResponse.java
    ConfirmMedicalTechDevicePricingResponse.java
    ListMedicalTechPricedDevicesResponse.java
    CancelMedicalTechDevicePricingResponse.java
    LoadMedicalTechChargingInitResponse.java
  dto/
    MedicalTechDeviceCatalogRowDto.java
    MedicalTechDevicePricingWriteItemDto.java
    MedicalTechPricedDeviceRowDto.java
    MedicalTechDevicePricingCancelItemDto.java
  assembler/
    MedicalTechChargingCatalogAssembler.java
    MedicalTechChargingWriteAssembler.java
    MedicalTechChargingListAssembler.java
```

> 公共库 `OptionDto` 不在本模块 dto/ 新建。  
> 同构参照 Bean：`NurseStationInpatientBillingAppService` 耗材路径（不复制诊疗/Procedure/扣库分支）。

### 2.1.1 前端落点（openhis-pro-ui）

| 项 | 值 |
| --- | --- |
| 菜单 | 同 EXE：`MedicalTechWorkstation` · Tab「医技计费」（仅住院）· `V29` |
| 页 | `views/medical-tech/workstation/charging/charging-panel.vue` |
| HTTP | `api/medical-tech/medical-tech-charging.*` → `/medicalTechCharging/*` |

### 2.2 依赖注入与领域方法白名单

| 依赖 | 用途 |
| --- | --- |
| `CurrentManagedOrganizationIdsResolver` / `OrganizationQueryService` | TECH 门禁 |
| `CurrentPractitionerResolver` / `CurrentOperatorResolver` | 开立人/操作人 |
| `EncounterQueryService` / `EncounterContextReader` | 住院就诊门闸 |
| `LocationQueryService` | 登录科下属耗材房解析 |
| `InventoryItemProductSelectionPageLoader` | IF-01 耗材库存选品分页（锁定 TECH 下属耗材房） |
| `DeviceRequestDomainService` | 划价写（既有护士划价等价；冲销**不** revoke Request） |
| `DeviceDispenseDomainService` | 生成待发 + `promoteDraftDispensesForDeviceRequests`（停在 PREPARATION） |
| `DeviceDispenseNurseWorkflowOrchestrator` | 冲销回退发放（`revertInpatientPricingDeviceDispenses`） |
| `ChargeItemDomainService` | `priceByNurseStation` 挂账 / `offsetNursePricingChargeItemsByServiceAnchors` 冲销 |
| `DeviceRequestQueryService` / `DeviceDispenseQueryService` / `ChargeItemQueryService` | 已划价列表与冲销 Pre |
| `MedicalTechChargingWriteAssembler` / Catalog / List Assembler | 定价重算、行装配 |
| `MedicalTechChargingMapper` | Asm |

**领域方法白名单**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| EncounterQueryService | `mustGetEncounter`（既有等价） | 全部 |
| LocationQueryService | 按组织列出耗材房 / `loadLocationEntityMapByIds`（既有等价） | 01、02、05 |
| InventoryItemProductSelectionPageLoader | `page`（`InventoryItemPageQuery`；仅 `ADM_DEVICE_DEFINITION`；库位∈TECH 下属耗材房） | 01 |
| DeviceRequestDomainService | `createExecutedDeviceOrder`（既有护士划价等价；`generateSourceEnum=nurse-pricing` 同族） | 02 |
| DeviceDispenseDomainService | `generateDeviceDispenseFromTemporaryPath`（既有等价） | 02 |
| DeviceDispenseDomainService | `promoteDraftDispensesForDeviceRequests`（升待发；**不** finalize / **不**扣库） | 02 |
| ChargeItemDomainService | `priceByNurseStation`（既有等价）→ PENDING_SETTLEMENT | 02 |
| DeviceRequestQueryService | `listDeviceRequestsByEncounter` / `mapListModelsByIds`（既有等价） | 03、04 |
| DeviceDispenseQueryService | `listDeviceDispensesByEncounter` / `mapPrimaryDispenseIdByDeviceReqIds`（既有等价） | 03、04 |
| ChargeItemQueryService | `listChargeItemsByServiceIds` / `listChargeItemsByEncounterId`（既有等价） | 03、04 |
| ChargeItemDomainService | `offsetNursePricingChargeItemsByServiceAnchors`（护士冲销同族） | 04 |
| DeviceDispenseNurseWorkflowOrchestrator | `revertInpatientPricingDeviceDispenses`（未发软删；已发累计回补量） | 04 |
| InventoryItemDomainService | `increaseQuantities`（累加器非空时；划价未扣库则跳过） | 04 |
| MedicalTechChargingMapper / Assembler | 各 `to*` / `assemble*` | 全 IF |

> **明确不调用**：`InventoryItemDomainService#decreaseQuantitiesMerged`（划价不扣库）；`ProcedureDomainService` / 护士划价 Procedure Support（不写诊疗 Procedure）。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 医嘱来源 | `com.zxplat.domain.share.enums.GenerateSource` | DM-WKF-003/004 §3 `generate_source_enum`；AS-IP-NUR-005 BR-IP-PRC-006 | 复用 `nurse-pricing` 同族；独立 medical-tech-pricing **可选未来** |
| 请求状态 | `com.zxplat.domain.share.enums.RequestStatus` | DM-WKF-003 §5 | 耗材划价产出 COMPLETED 口径对齐护士 |
| 费用状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | DM-ADM-011 §5 | **PENDING_SETTLEMENT** |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | DM-WKF-007 §5 | **PREPARATION** 待发 |
| 就诊 class | `com.zxplat.domain.share.enums.EncounterClass` | DM-ADM-002 §3 | 必须 IMP |
| 组织分类 TECH | `com.zxplat.domain.share.enums.OrganizationClassification` | DM-ADM-004 §3 | 登录科须 TECH |
| 选品定价标记 | `PageMedicalTechDeviceCatalogRequest.pricingFlag` | AS-IP-NUR-005 BR-IP-PRC-002 | 固定耗材口径，禁止药品 |
| 业务异常码 | `MedicalTechChargingConstants.EX_MT_CHG_*` | AS-MT-CHG-001 §5 | 101/201～203 |

---

## 3. 按接口 ID 命名展开

> **先读各 IF「领域编排序列」**，按序号执行。Pre/Gate 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-MT-CHG-01 | `pageMedicalTechDeviceCatalog` | 分页查询医技耗材选品 | `POST /api/app/medicalTechCharging/pageMedicalTechDeviceCatalog` |
| IF-MT-CHG-02 | `confirmMedicalTechDevicePricing` | 确认医技耗材划价 | `POST /api/app/medicalTechCharging/confirmMedicalTechDevicePricing` |
| IF-MT-CHG-03 | `listMedicalTechPricedDevices` | 查询已划价列表（耗材+诊疗） | `POST /api/app/medicalTechCharging/listMedicalTechPricedDevices` |
| IF-MT-CHG-04 | `cancelMedicalTechDevicePricing` | 取消医技划价（分轨） | `POST /api/app/medicalTechCharging/cancelMedicalTechDevicePricing` |
| IF-MT-CHG-05 | `loadMedicalTechChargingInit` | 加载医技计费初始化 | `POST /api/app/medicalTechCharging/loadMedicalTechChargingInit` |
| IF-MT-CHG-06 | `pageMedicalTechServiceCatalog` | 分页查询医技诊疗选品 | `POST /api/app/medicalTechCharging/pageMedicalTechServiceCatalog` |
| IF-MT-CHG-07 | `confirmMedicalTechServicePricing` | 确认医技诊疗划价 | `POST /api/app/medicalTechCharging/confirmMedicalTechServicePricing` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | TECH / 住院 / 参数 | 不再后续 |
| **Pre** | 库房归属、定价、状态门控 | 禁止 Cmd |
| **Cmd** | DeviceRequest / Dispense / Charge 写 | 整批回滚 |
| **Asm** | Mapper / Assembler | — |

---

### 3.2 IF-MT-CHG-01 `pageMedicalTechDeviceCatalog`（分页查询医技耗材选品）

**Request `PageMedicalTechDeviceCatalogRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 |
| `keyword` | String | N | 品名/编码 |
| `locationId` | Long | N | 指定本科耗材房；空则默认房 |
| `pageNo` / `pageSize` | Integer | N | |
| `pricingFlag` | Integer | N | 固定耗材口径（对齐护士选品） |

**Response `PageMedicalTechDeviceCatalogResponse`**（继承 `PagedResult`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | List\<MedicalTechDeviceCatalogRowDto\> | 选品行 |
| `totalCount` | Long | 总数 |

**DTO `MedicalTechDeviceCatalogRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `definitionId` | Long | 耗材定义主键 |
| `itemName` / `itemCode` | String | 名称/编码 |
| `specification` | String | 规格 |
| `unitCode` / `unitName` | String | 单位 |
| `unitPrice` | BigDecimal | 展示单价 |
| `locationId` | Long | 默认/选定库房 |
| `availableQty` | BigDecimal | 可选库存展示（只读提示；划价不扣） |
| `lotNumber` | String | 库存批号（写路径回传：锁价 + 自动扣库锁批定批） |

**失败码**：EX-MT-CHG-101

**Mapper / Assembler**：`toProductDefinitionPageQuery`；`assembleCatalogRows`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、encounterId | 非住院 → EX-MT-CHG-101 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | | class=IMP |
| 3 | Pre | 解析 loginTech 下属耗材房集合 | locationId 须∈集合 | 非法房 → EX-MT-CHG-101 |
| 4 | Pre | `InventoryItemProductSelectionPageLoader#page` | 库位∈TECH 下属耗材房；仅耗材表 | BR-MT-CHG-002 |
| 5 | Asm | CatalogAssembler + Mapper → `PagedResult` | | 仅读 |

---

### 3.3 IF-MT-CHG-02 `confirmMedicalTechDevicePricing`（确认医技耗材划价）

**Request `ConfirmMedicalTechDevicePricingRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |
| `accountId` | Long | N | 账户（若护士路径需要则同构传入） |
| `items` | List\<MedicalTechDevicePricingWriteItemDto\> | Y | 至少一行 |

**DTO `MedicalTechDevicePricingWriteItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `definitionId` | Long | Y | 耗材定义 |
| `quantity` | BigDecimal | Y | >0 |
| `unitCode` | String | Y | |
| `locationId` | Long | Y | 必须本科耗材房 |
| `lotNumber` | String | N | 可选批号：锁定价并落请求供 finalize 锁批；空则 FIFO |

**Response `ConfirmMedicalTechDevicePricingResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `succeeded` | Boolean | |
| `createdRequestIds` | List\<Long\> | 新建 DeviceRequest 主键 |
| `message` | String | |

**失败码**：EX-MT-CHG-201（库房非本科）；EX-MT-CHG-202（写失败）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、住院、items 非空 | 禁止诊疗/药品行 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | | IMP |
| 3 | Pre | 校验每行 `locationId` ∈ loginTech 下属耗材房 | BR-MT-CHG-003 | 失败 → EX-201 |
| 4 | Pre | WriteAssembler 权威定价重算（同护士耗材路径） | 单价/金额 | 失败 → EX-202 |
| 5 | Cmd | `DeviceRequestDomainService#createExecutedDeviceOrder(CreateExecutedDeviceOrderCommand)` | `generateSourceEnum=nurse-pricing` 同族；`performLocation`=行库房 | **写库点1** DeviceRequest |
| 6 | Cmd | `DeviceDispenseDomainService#generateDeviceDispenseFromTemporaryPath(...)` | 草稿发放 | **写库点2a** DeviceDispense |
| 7 | Cmd | `DeviceDispenseDomainService#promoteDraftDispensesForDeviceRequests(...)` | 升 **PREPARATION** | **写库点2b**；**禁止** finalize / decreaseQuantitiesMerged |
| 8 | Cmd | `ChargeItemDomainService#priceByNurseStation(...)` | 态=PENDING_SETTLEMENT；`requestingOrgId`=loginTech | **写库点3** ChargeItem；**不**写 Procedure |
| 9 | Asm | Response | | BR-MT-CHG-004/007 |

---

### 3.4 IF-MT-CHG-03 `listMedicalTechPricedDevices`（查询已划价耗材列表）

**Request `ListMedicalTechPricedDevicesRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |

**Response `ListMedicalTechPricedDevicesResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `rows` | List\<MedicalTechPricedDeviceRowDto\> | 已划价行 |

**DTO `MedicalTechPricedDeviceRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | Long | DeviceRequest 主键 |
| `dispenseId` | Long | 主发放 |
| `chargeItemId` | Long | 主费用行（有则） |
| `itemName` / `itemCode` | String | |
| `quantity` | BigDecimal | |
| `unitPrice` / `totalPrice` | BigDecimal | |
| `locationId` | Long | 耗材房 |
| `chargeStatusEnum` | String | |
| `dispenseStatusEnum` | String | |
| `pricedTime` | LocalDateTime | 展示轴（主发放时间优先） |
| `feeConfirmerId` | Long | 确费人主键；空=未确费 |
| `feeConfirmerName` | String | 确费人展示名 |
| `billingPractitionerId` | Long | 计费人主键（优先 DeviceRequest.requester_id；否则 Dispense 发放/配药人） |
| `billingPractitionerName` | String | 计费人展示名 |
| `cancellable` | Boolean | 待结算且未冲销可冲 |

**失败码**：EX-MT-CHG-101（查询失败口径可复用）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、encounterId | |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | | IMP |
| 3 | Pre | `DeviceRequestQueryService#listDeviceRequestsByEncounter` + Dispense + Charge | | |
| 4 | Pre | 过滤：来源同族 + 库房属 loginTech | BR-MT-CHG-005 | 仅读 |
| 5 | Pre | `ChargeItemActivityOperatorResolver` + `PractitionerQueryService` | 主费用/开立人 | 确费人/计费人姓名 |
| 6 | Asm | ListAssembler | | |

---

### 3.5 IF-MT-CHG-04 `cancelMedicalTechDevicePricing`（取消医技耗材划价）

**Request `CancelMedicalTechDevicePricingRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |
| `items` | List\<MedicalTechDevicePricingCancelItemDto\> | Y | |

**DTO `MedicalTechDevicePricingCancelItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `requestId` | Long | Y | DeviceRequest 主键 |

**Response `CancelMedicalTechDevicePricingResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `succeeded` | Boolean | |
| `cancelledCount` | Integer | |
| `message` | String | |

**失败码**：EX-MT-CHG-203（已结算不可冲）；EX-MT-CHG-202（写失败）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、items 非空 | |
| 2 | Pre | 就诊门闸 + `mapListModelsByIds` | 断言同族来源、本科房、就诊一致 | BR-MT-CHG-006 |
| 3 | Pre | Charge 态须 PENDING_SETTLEMENT 且未冲销 | | 否则 EX-203 |
| 4 | Cmd | `DeviceDispenseNurseWorkflowOrchestrator#revertInpatientPricingDeviceDispenses(...)` | 未发软删；已发则累计回补量 | **写库点1** Dispense |
| 5 | Cmd | `ChargeItemDomainService#offsetNursePricingChargeItemsByServiceAnchors(...)` | | **写库点2** Charge |
| 6 | Cmd | `InventoryItemDomainService#increaseQuantities`（累加器非空时） | 仅已扣库路径 | **写库点3** 库存（可选） |
| 7 | Asm | Response | | **不** revoke DeviceRequest（对齐护士冲销） |

---

### 3.6 IF-MT-CHG-05 `loadMedicalTechChargingInit`（加载医技计费初始化）

**Request `LoadMedicalTechChargingInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |

**Response `LoadMedicalTechChargingInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultLocationId` | Long | 默认耗材房 |
| `locationOptions` | List\<OptionDto\> | 登录科下属耗材房（公共 `OptionDto`） |
| `encounterId` | Long | 回显 |

**失败码**：EX-MT-CHG-101

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH | |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | | IMP |
| 3 | Pre | LocationQuery 列 loginTech 下属耗材房 | | BR-MT-CHG-003 |
| 4 | Asm | 选默认房（首个或既有偏好） | | 仅读 |

---

## 4. QueryService 与领域事件

| 写侧事件 | 读侧刷新 |
| --- | --- |
| 耗材划价完成（同护士 DeviceRequest 创建事件族） | 医技执行右侧待发、费用清单、IF-03 |
| 冲销完成 | 同上 |

本模块不另建独立事件类型；复用护士划价写事件即可（同族来源）。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合顺序 |
| --- | --- | --- | --- | --- |
| 02 | `confirmMedicalTechDevicePricing` | 5 | createExecutedDeviceOrder | DeviceRequest |
| 02 | 同上 | 6～7 | generateTemporaryPath → promoteDraft | DeviceDispense（PREPARATION） |
| 02 | 同上 | 8 | priceByNurseStation | ChargeItem（PENDING_SETTLEMENT） |
| 04 | `cancelMedicalTechDevicePricing` | 4～6 | revert Dispense → offset Charge →（可选）increaseQuantities | Dispense → Charge → Inventory（Request 保持完成态） |

### 5.2 事务边界 / 失败回滚

| IF | 策略 |
| --- | --- |
| 02 / 04 | `@Transactional` 整批；任一步失败全回滚 |
| 01 / 03 / 05 | 无写库 |

**禁止**：划价事务内调用库存扣减；禁止写诊疗 Procedure。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 冲销仅待结算可冲 | IF-04 Pre | §3.5 序号 3 |
| 同批 requestId 去重 | Gate | §3.5 序号 1 |

### 6.2 并发控制

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 冲销前再读 Charge 态 | Pre | §3.5 序号 3；已结算 EX-203 |

### 6.3 重试与容错

| 项 | 说明 |
| --- | --- |
| 选品/列表 | 可重试 |
| 库房非法 | 不可盲目重试 |
| 写失败 | 整批回滚后可重提 |

### 6.4 数据脱敏

| 项 | 说明 |
| --- | --- |
| 选品/已划价 | 仅工作站所需字段 |

### 6.5 审计日志

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 划价操作人 | DeviceRequest / Charge 写链 | §3.3 序号 5～7 |
| 冲销操作人 | 冲销写链 | §3.5 序号 4～6 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 本科房划价出待发 + PENDING_SETTLEMENT | API | 是 | P0 |
| 他科房拒绝 EX-201 | API | 是 | P0 |
| 诊疗/药品行拒绝 | API | 是 | P0 |
| 划价不扣库存 | API | 是 | P0 |
| 待结算可冲；已结算 EX-203 | API | 是 | P0 |
| 门诊就诊拒绝 | API | 是 | P0 |
| 执行侧可发医技划价行 | 集成 | 是 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 整批事务回滚 | API | 是 | P0 |

### 7.3 查询结果一致性

| 用例 | 说明 |
| --- | --- |
| IF-03 仅本科房相关行 | BR-MT-CHG-005 |
| 与 EXE 待发轴一致 | 发放房组织=loginTech |

### 7.4 集成测试

| 用例 | 说明 |
| --- | --- |
| 参照 NurseStationInpatientBilling 耗材路径 | 同族写能力、更窄门禁 |
| 联调 AS-MT-EXE 发放扣库 | 划价不扣、执行扣 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 无阻塞 | 复用护士划价领域写；独立 generate_source 枚举非必需 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2.0 | 2026-10-11 | **代码先行回写**：IF-06/07 诊疗选品/确认；`createFromMedicalTechPricing`；列表/冲销分轨 `orderType`；来源仍 `medical-tech-pricing` |
| v1.1.0 | 2026-09-23 | IF-03 已划价行补 `feeConfirmer*` / `billingPractitioner*` |
| v1.0.3 | 2026-09-21 | 补 §2.1.1 前端落点（住院 Tab） |
| v1.0.3 | 2026-10-18 | **代码先行回写**：`lotNumber` 写路径锁价+锁批定批（对齐 **AS v1.3.1** / **AS-IP-NUR-005 BR-IP-PRC-008**） |
| v1.0.2 | 2026-09-19 | 选品行补 lotNumber；冲销已发路径对称回补库存；确认 Gate 非库房错误统一 EX-202 |
| v1.0.1 | 2026-09-19 | 对齐成码：选品改库存 Loader；确认加 promoteDraft；冲销改 offset+revert、不 revoke |
| v1.0.0 | 2026-09-19 | 初版目标态：MedicalTechChargingAppService；IF-01～05；同构护士耗材划价但锁定 TECH 耗材房、不扣库、不写 Procedure；generate_source 复用 nurse-pricing 同族 |
