# 住院发药 · 应用服务详细设计（SDD）

> 编码：AS-IP-PHA-001-SDD  
> 版本：v2.9 | 日期：2026-08-31 | 作者：（待填）  
> 来源契约：`AS-IP-PHA-001-住院发药.md` v2.9 · DM：`DM-MED-002`、`DM-MED-003`、`DM-CLN-004`、`DM-ADM-002`、`DM-ADM-003` §1.5、`DM-ADM-004` §1.5、`DM-ADM-011` · QueryService：`DM-MED-002 §6.2 MedicationDispenseQueryService 发药单分页`、`DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）`、`ProductDefinition`（`med_medication_definition / adm_device_definition / wkf_activity_definition（主表）`，发放 **操作单位→拆零**；五类优先，同码禁止贴码倍数覆盖）

## 1. 实现边界

### 1.1 目标与边界


| 项 | 内容 |
| --- | --- |
| 模块 | 住院发药应用服务（药房工作站：初始化、**患者队列**（在院+出院轨）含**病区**筛选、就诊下**药品**发药单明细、**保存配药**、**发药**、**作废**） |
| 边界 | 编排 **`MedicationDispense`（药品，不含耗材）** / `MedicationRequest` / `Encounter` / `Practitioner` / **`Location`（药房点位 + 病区）** / `Organization`（**仅** `mapNamesByIds` 开单科室名） / **`ChargeItem`（IF-IPP-03 只读展示）** / **库存查询** 及 **`MedicationDispenseSlip`**、**`InpatientEncounterContextQueryModel`** QueryService；**不包含**汇总发药、退药、医保上传、结算前置、**候选库房×批号改选**、IF-IPP-04 保存配药时**收费明细比价**；库存实物扣减发生在 **IF-IPP-05 发药** |
| 编排权威 | `AS-IP-PHA-001-住院发药.md` §2、§4 |
| 方法权威 | `DM-MED-002` §6、`DM-MED-003` §6、`DM-ADM-002` §6（查询侧）；**MedicationDispenseSlip**、**InpatientEncounterContextQueryModel** QueryService §3；`MedicationDispenseQueryService#pageEncounterIdsForInpatientPharmacyPending`（已落地） |
| 严格对齐 | IF 编号严格对齐 AS：`IF-IPP-01`～`IF-IPP-06` |
| 实现阶段口径 | 应用层 `InpatientPharmacyDispenseAppService` 及配套 Request/Response/DTO/Mapper **已落地**（`health-application/.../inpatient/pharmacy/dispense/`）；领域读查询与写方法**已存在**（见 §2.2） |


### 1.2 术语口径


| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 与 DM、QueryService一致；持久化枚举存 **`*.getCode()`** 字符串 |
| QueryService口径 | IF-IPP-02：先发药事实收敛 `encounterId` 分页（**在院+出院轨** + 可选**病区**（仅 `wardId` 非空）+ 关键字经 **`PharmacyEncounterQueueLoader`** 预解析）→ **`EncounterQueryService#listInpatientContextsByEncounterIdsOrdered`** 回补；**管辖库位非空**时库房 **`location_id IN`**；**管辖 empty** 时不拼谓词；**不按** `status_changed_time` 时间窗筛选；就诊排序 **`ORDER BY max(created_at) DESC`**。IF-IPP-03：**`inpatientMedicationOnly=true`**；**`locationId` 未传时**服务端按 **`CurrentManagedLocationIdsResolver`** 注入 **`locationIds IN`**（与 IF-IPP-02 同源）；**不返回** `inventoryDetailList`；批号/数量**只读**；**批号** slip 投影 + 领域 **`MedicationDispense#lotNumber` 非空覆盖**；**单价/金额** **`ChargeItem` 与发放分段 1:1**（**非**门诊 `mergeChargePricingFromChargeItems` 加权聚合）；**`procedureId`/`recordedTime`**：slip/聚合带出执行主键后 **`ProcedureQueryService#listByIds`** 回填护士 **`recorded_time`**（AS **BR-IPP-014** 日分单真源；**禁止**用 `planned_dispense_time` 作日键） |
| 事务口径 | Gate/Pre 失败禁止进入 Cmd；**IF-IPP-04** 与 **IF-IPP-05** **各自独立事务**（默认） |


## 2. 代码结构

```text
com/openhis/health/application/inpatient/pharmacy/dispense/
  service/
    InpatientPharmacyDispenseAppService.java
  request/
    LoadInpatientPharmacyDispensePageInitRequest.java
    PagePendingDispenseInpatientEncounterQueueRequest.java
    PageEncounterMedicationDispenseSlipsRequest.java
    SavePrepareInpatientMedicationDispenseRequest.java
    DispenseInpatientMedicationsRequest.java
    CancelInpatientMedicationDispenseRequest.java
  response/
    LoadInpatientPharmacyDispensePageInitResponse.java
    PagePendingDispenseInpatientEncounterQueueResponse.java
    PageEncounterMedicationDispenseSlipsResponse.java
    SavePrepareInpatientMedicationDispenseResponse.java
    DispenseInpatientMedicationsResponse.java
    CancelInpatientMedicationDispenseResponse.java
  dto/
    PendingDispenseInpatientEncounterQueueRowDto.java
    EncounterMedicationDispenseSlipRowDto.java
    SavePrepareInpatientDispenseLineSubmitDto.java
    DispenseMedicationLineSubmitDto.java
    CancelMedicationDispenseLineSubmitDto.java
  mapper/
    InpatientPharmacyDispenseMapper.java
    InpatientPharmacyDispenseMapperImpl.java
```

### 2.1 依赖注入表


| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate / 写路径 **`requireOperatorId`**（**B.6.5**）；IF-IPP-04/05 **`findByUserId`** 解析发药人 |
| `CurrentManagedOrganizationIdsResolver` | IF-IPP-01 病区候选：**`resolveOrganizationUnitIdForQuery`** → **`LocationQueryService#listWardsByOrganization`**（**B.6.5**） |
| `CurrentManagedLocationIdsResolver` | IF-IPP-01 **`listManagedActivePharmaciesOrEmpty`**；IF-IPP-02 **`listManagedLocationIdsOrEmpty`** → 非空写入 **`InpatientPharmacyPendingEncounterPageQuery#locationIds`**；empty 不拼谓词（**B.6.5**） |
| `EncounterContractDisplayAssembler` | IF-IPP-02：本页就诊批量回填 `contractDisplay`（与门诊发药/医生站卡片同口径） |
| `OrganizationQueryService` | **仅 IF-IPP-03**：**`mapNamesByIds`** 批量开单科室名 |
| `PractitionerQueryService` | IF-IPP-01 调配药师候选；IF-IPP-03/04/05 人员展示与 `findByUserId` |
| `TerminologyOptionLoader` | IF-IPP-01 下拉；IF-IPP-03 **`buildLabelMap`** |
| `OrderCatalogDisplayAssembler` | IF-IPP-03 **`resolveCategoryDisplay`** |
| `CodeSystemQueryService` | IF-IPP-01：`getOptionListByEnum` |
| `EncounterQueryService` | IF-IPP-03～06：`mustGetEncounter` |
| `LocationQueryService` | IF-IPP-01：**`listWardsByOrganization`** 病区候选；IF-IPP-03 **`mapNamesByIds`** 库房名 |
| `MedicationDispenseQueryService` | IF-IPP-02 **`pageEncounterIdsForInpatientPharmacyPending`**；IF-IPP-03 **`listMedicationDispensesByIds`**（**`lotNumber`/`traceNo`/`procedureId`** 回补）；IF-IPP-04～06 写前加载与门控 |
| `ProcedureQueryService` | IF-IPP-03：**`listByIds`** 回填 **`recordedTime`**（按日虚拟发药单日键；**BR-IPP-014**） |
| `ChargeItemQueryService` | IF-IPP-03：**`listChargeItemsForMedicationDispensePricing(encounterId, dispenseIds)`** 拉取锚定 **`med_medication_dispense`** 的 **`ChargeItem`**（状态 **DRAFT/PLANNED/SETTLED**）；应用层 **`mapChargePricingByDispenseId`** 按 **`dispenseId` 1:1** 写入 **`unitPrice`/`totalPrice`**（**禁止**门诊式求和加权）。**不**用于 IF-IPP-04 保存前比价 |
| `MedicationDispenseDomainService` | IF-IPP-04 `prepareMedicationDispense`；IF-IPP-05 `dispenseMedication`；IF-IPP-06 `cancelMedicationDispense` |
| `MedicationRequestQueryService` | IF-IPP-03/04：`listMedicationRequestsByEncounter`、`loadMedicationRequestById` |
| `MedicationDispenseQueryService` | IF-IPP-03：`pageMedicationDispenseSlip` |
| `EncounterQueryService` | IF-IPP-02：`listInpatientContextsByEncounterIdsOrdered` |
| `ProductDefinitionBatchLoader` | IF-IPP-03：`loadAsMap` |
| `InventoryItemQueryService` | IF-IPP-04/05：**`assertSufficientForMedicationDispense`**（按**聚合已持久化**库房+批号+数量） |
| `InventoryItemDomainService` | IF-IPP-05：`allocateAndDecrease`（有批号带 `lotNumber` 同批拆分；无批号跨批 FEFO） |
| `TraceNumberRecordQueryService` | IF-IPP-04：**`assertCodesNotAlreadyOutbound`**（与门诊 Trace 域 Bean 对齐） |
| `InpatientPharmacyDispenseMapper` | DTO 装配；**`OptionDto`** 使用公共库 |


### 2.2 领域方法白名单


| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedLocationIdsResolver` | `listManagedActivePharmaciesOrEmpty` | IF-IPP-01 |
| `OrganizationQueryService` | `mapNamesByIds` | IF-IPP-03（开单科室名） |
| `PractitionerQueryService` | `pageActivePractitionersForPosition(Collection<Long>, …)`、`findByUserId`、`findById` | IF-IPP-01 / IF-IPP-03～05 |
| `PractitionerRoleScopeLoader` | `resolvePractitionerIdsForActiveRole` | IF-IPP-01 Pre |
| `WhaleRoleLookup` | `findRoleIdByCode` / `resolvePlatformRoleId` | IF-IPP-01 Pre |
| `TerminologyOptionLoader` | `loadOptions`、`buildLabelMap` | IF-IPP-01 / IF-IPP-03 |
| `OrderCatalogDisplayAssembler` | `resolveCategoryDisplay` | IF-IPP-03 |
| `CodeSystemQueryService` | `getOptionListByEnum` | IF-IPP-01 |
| `EncounterQueryService` | `mustGetEncounter` | IF-IPP-03～06 |
| `LocationQueryService` | `listWardsByOrganization`、`mapNamesByIds` | IF-IPP-01～03 |
| `MedicationDispenseQueryService` | `pageEncounterIdsForInpatientPharmacyPending`、`mustGetMedicationDispense`、`validateDispensePreparable`、`validateDispensable`、`listMedicationDispensesByIds` | IF-IPP-02～06 |
| `ProcedureQueryService` | `listByIds` | IF-IPP-03 |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing` | IF-IPP-03 |
| `MedicationDispenseDomainService` | `prepareMedicationDispense` | IF-IPP-04 |
| `MedicationDispenseDomainService` | `dispenseMedication` | IF-IPP-05 |
| `InventoryItemDomainService` | `allocateAndDecrease`（可选 `lotNumber`） | IF-IPP-05 |
| `MedicationDispenseDomainService` | `cancelMedicationDispense` | IF-IPP-06 |
| `MedicationRequestQueryService` | `listMedicationRequestsByEncounter`、`loadMedicationRequestById` | IF-IPP-03 / IF-IPP-04 |
| `MedicationDispenseQueryService` | `pageMedicationDispenseSlip` | IF-IPP-03 |
| `EncounterQueryService` | `listInpatientContextsByEncounterIdsOrdered` | IF-IPP-02 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-IPP-03 |
| `InventoryItemQueryService` | `assertSufficientForMedicationDispenseGivenReserve`（经 Gate） | IF-IPP-04 / IF-IPP-05 |
| `MedicationDispenseInventoryAvailabilityGate` | `assertSufficientForMedicationDispenseWithReserve`（预占同批） | IF-IPP-04 / IF-IPP-05 |
| `TraceNumberRecordQueryService` | `assertCodesNotAlreadyOutbound` | IF-IPP-04 |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2.2、`status_enum` | IF-IPP-01～03 筛选；IF-IPP-04～06 门控 |
| 发放类型 | `com.zxplat.domain.share.enums.MedicationDispenseType` | `DM-MED-002` §4.5、`dispense_enum` | IF-IPP-03：`inpatientMedicationOnly` → **INPATIENT**+**TEMPORARY**（排除门诊行） |
| 住院就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3.1 | IF-IPP-02 队列：**IN_PROGRESS**、**ON_HOLD**、**PENDING**、**COMPLETED**、**DISCHARGED** |
| 治疗类型（临时/长期） | `com.zxplat.domain.share.enums.TherapyType` | `DM-MED-003` §4.2 | **BR-IPP-006**（IF-IPP-04）仅 **`TEMPORARY`**：`assertTemporaryTherapyQuantitiesForInpatient` 校验拆分行合计；**`LONG_TERM`** 按次生成多条发放，**不做** req 全量合计 |
| 未发药原因 | —（**DOC-CONFLICT**：DM §4.6 术语存在，`NotPerformedReason` 枚举**尚未**落 `zxplat-domain`） | `DM-MED-002` §4.6 | IF-IPP-01：**空列表**（与 `OutpatientPharmacyDispenseAppService` 一致）；IF-IPP-06：请求 `notPerformedReasonEnum` **字符串 code** 直传领域，落 `not_performed_reason_enum` |
| 住院队列分页查询 | `MedicationDispenseQueryService#pageEncounterIdsForInpatientPharmacyPending(InpatientPharmacyPendingEncounterPageQuery)` | `AS-IP-PHA-001` **BR-IPP-001** | IF-IPP-02；含可选 `wardId`、关键字、`InpatientEncounterContextQueryModel` 在院子查询；**无** `status_changed_time` 时间窗；`max(created_at)` 排序 |
| 住院信息有序回补 | `EncounterQueryService#listInpatientContextsByEncounterIdsOrdered(List<Long>)` | `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §3；`AS-IP-PHA-001` IF-IPP-02 | 队列展示 |
| 业务异常码（实现） | `com.zxplat.domain.share.constants.ErrorCodeConstants` 中 **`EX_OPP_*`**（与 AS §5 **EX-IPP-xxx** 同语义复用，见 `InpatientPharmacyDispenseAppService`） | `AS-IP-PHA-001` §5 | 对外契约仍为 **EX-IPP-xxx**；落库/日志以 `BusinessException` 常量为准 |


### 2.4 白名单双向一致性复核


| 复核项 | 结论 |
| --- | --- |
| 正向（白名单 → §3 序列） | PASS |
| 反向（§3 序列 → 白名单） | PASS |


## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）


| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IPP-01 | `loadInpatientPharmacyDispensePageInit` | 加载住院发药页初始化 | `POST /api/app/inpatientPharmacyDispense/loadInpatientPharmacyDispensePageInit` |
| IF-IPP-02 | `pagePendingDispenseInpatientEncounterQueue` | 分页查询住院发药在院患者队列 | `POST /api/app/inpatientPharmacyDispense/pagePendingDispenseInpatientEncounterQueue` |
| IF-IPP-03 | `pageEncounterMedicationDispenseSlips` | 分页加载就诊下住院药品发药单 | `POST /api/app/inpatientPharmacyDispense/pageEncounterMedicationDispenseSlips` |
| IF-IPP-04 | `savePrepareInpatientMedicationDispense` | 保存配药（PREPARATION→IN_PROGRESS） | `POST /api/app/inpatientPharmacyDispense/savePrepareInpatientMedicationDispense` |
| IF-IPP-05 | `dispenseInpatientMedications` | 发药扣库（IN_PROGRESS→COMPLETED） | `POST /api/app/inpatientPharmacyDispense/dispenseInpatientMedications` |
| IF-IPP-06 | `cancelInpatientMedicationDispense` | 作废发放（→DECLINED） | `POST /api/app/inpatientPharmacyDispense/cancelInpatientMedicationDispense` |


### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService` | 事务回滚见 §5 |
| **Asm** | `InpatientPharmacyDispenseMapper` 纯 DTO 装配 | — |


记法：`EncounterQueryService#listInpatientContextsByEncounterIdsOrdered` 表示在 `InpatientPharmacyDispenseAppService` 中注入并调用。

### 3.2 IF-IPP-01 `loadInpatientPharmacyDispensePageInit`（加载住院发药页初始化）

**HTTP**：`POST /api/app/inpatientPharmacyDispense/loadInpatientPharmacyDispensePageInit`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pharmacyLocationId` | Long | N | 当前药房/科室药房库位主键（`adm_location.id`）；收窄调配药师候选 |
| `pageNo` | Integer | N | 调配药师分页（**1-based**，继承 `AbstractPageQuery`） |
| `pageSize` | Integer | N | 调配药师分页大小 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pharmacyOrganizationOptions` | `List<OptionDto>` | 可操作药房族（**`PHARMACY` + `DEPARTMENT_PHARMACY`**）；**`value` = `adm_location.id`** |
| `dispenseStatusFilterOptions` | `List<OptionDto>` | `DispenseStatus` |
| `notPerformedReasonOptions` | `List<OptionDto>` | 占位空列表（枚举未落库前）；作废仍提交 `notPerformedReasonEnum` code |
| `wardOptions` | `List<OptionDto>` | 病区候选（`value`=病区 locationId，`label`=病区名） |
| `preparerCandidatePage` | `PagedResult` 壳 + 药师行 | 调配药师分页 |


**关键校验**

- 元数据装载失败 → **EX-IPP-101**（实现可映射 `ErrorCodeConstants.EX_OPP_101_INIT_METADATA_FAILED`）。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-IPP-101 | 元数据加载失败 |


**Mapper 接口方法签名**

- `InpatientPharmacyDispenseMapper#toLoadInitResponse(List<Location> pharmacies, List<OptionDto> dispenseStatusOptions, List<OptionDto> notPerformedReasonOptions, List<OptionDto> wardOptions, Page<Practitioner> preparerPage): LoadInpatientPharmacyDispensePageInitResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 否则 EX-IPP-101 |
| 2 | Pre | `CurrentManagedLocationIdsResolver#listManagedActivePharmaciesOrEmpty` | — | 管辖内启用药房 **Location** |
| 3 | Pre | `CodeSystemQueryService#getOptionListByEnum(DispenseStatus.class)` | — | 发药状态 |
| 4 | Pre | — | — | 未发药原因：`new ArrayList<>()`（与门诊一致；待 `NotPerformedReason` 枚举落库后改为 `getOptionListByEnum`） |
| 5 | Pre | `LocationQueryService#listWardsByOrganization(Long)` | **`CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()`** | 病区下拉（**BR-IPP-001** 筛选数据源） |
| 6 | Pre | `PractitionerRoleScopeLoader#pageActivePractitionersForPlatformRoleInLocations(ROLE_PHARMACIST, pharmacyLocationIds, …)` | 药师岗 + 药房库位 | 调配药师候选：须 **`ROLE_PHARMACIST`** 且 **`adm_pr_assign.location_id`** 落在 `pharmacyLocationId`（请求指定）或步骤 2 管辖内启用药房；无命中库位时返回空分页 |
| 7 | Asm | `InpatientPharmacyDispenseMapper#toLoadInitResponse` | ②～⑥ | 仅读 |


### 3.3 IF-IPP-02 `pagePendingDispenseInpatientEncounterQueue`（分页查询住院发药在院患者队列）

**HTTP**：`POST /api/app/inpatientPharmacyDispense/pagePendingDispenseInpatientEncounterQueue`

**库房收敛（服务端）**：**`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`** → 非空写入 **`InpatientPharmacyPendingEncounterPageQuery#locationIds`**；**empty 不拼** `location_id` 谓词（过渡，待身份模型注入管辖集合）。**禁止**以 **`listPharmaciesByOrganization(organizationUnitId)`** 替代。

**队列时间与排序（服务端）**：**禁止**以 `status_changed_time` 作时间窗谓词（应用层 bind 后清空 `startDate`/`endDate`）；就诊分页排序为同一就诊下命中发放行的 **`max(created_at) DESC`**（`MedicationDispenseQueryService#pageEncounterIdsForInpatientPharmacyPending`）。

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 姓名/住院号/床号（**BR-IPP-001**） |
| `wardId` | Long | N | 病区主键；**未传或 null** 表示不按病区过滤 |
| `dispenseStatusEnumIn` | `List<String>` | N | 空表示不按发放状态过滤；待发 Tab 传 `PREPARATION`+`IN_PROGRESS`；已发 Tab 传 `COMPLETED` |
| `pageNo` | Integer | Y | **1-based** |
| `pageSize` | Integer | Y | ≥1 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<PendingDispenseInpatientEncounterQueueRowDto>` | 队列行 |
| `totalCount` | Long | 总条数 |
| `pageNo` | Integer | 当前页 |
| `pageSize` | Integer | 页大小 |


**DTO 字段表：PendingDispenseInpatientEncounterQueueRowDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊主键 |
| `encounterBusNo` | String | N | 住院号 |
| `patientId` | Long | Y | 患者主键 |
| `patientName` | String | N | 患者姓名 |
| `genderEnum` | String | N | 性别 code |
| `ageText` | String | N | 年龄展示 |
| `wardId` | Long | N | 病区 ID |
| `wardName` | String | N | 病区名称 |
| `bedName` | String | N | 床位名称 |
| `organizationName` | String | N | 当前科室名称 |
| `attendingDoctorName` | String | N | 主治医生 |
| `statusEnum` | String | N | 住院状态 code（`InpatientEncounterContextQueryModel`） |
| `statusEnumText` | String | N | 住院状态展示（IMP 专规工具类） |
| `startTime` | `LocalDateTime` | N | 入院时间 |
| `contractDisplay` | String | N | 费用性质展示名（就诊优先合同名；与门诊发药/医生站卡片同口径；无合同时为空，前端回落「自费」） |


**关键校验**

- 未登录/机构缺失/分页非法 → **EX-IPP-102**。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-IPP-102 | 队列查询失败 |


**Mapper 接口方法签名**

- `InpatientPharmacyDispenseMapper#toInpatientQueuePage(Page<Long> encounterIdPage, List<InpatientEncounterContextQueryModel> infos): PagePendingDispenseInpatientEncounterQueueResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `PageQueryBinder` / 登录态 | `pageNo`/`pageSize` | 非法 → EX-IPP-102 |
| 2 | Pre | **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`** | — | 非空→`locationIds`；**empty**→不拼库房谓词 |
| 3 | Pre | `MedicationDispenseQueryService#pageEncounterIdsForInpatientPharmacyPending` | `locationIds`、可选 `wardId`、`keyword`、状态子集、分页 | 在院+发放事实收敛；`max(created_at)` 排序 |
| 4 | Pre | `EncounterQueryService#listInpatientContextsByEncounterIdsOrdered` | ③ ID 列表有序 | 回补展示 |
| 5 | Asm | `InpatientPharmacyDispenseMapper#toInpatientQueuePage` | ③④ | 映射队列行 |
| 6 | Asm | **`EncounterContractDisplayAssembler#mapContractDisplayByEncounterIds`** + 逐行写回 `contractDisplay` | ⑤ 本页 encounterIds | 费用性质与门诊发药/医生站卡片同口径；无合同时留空 |


### 3.4 IF-IPP-03 `pageEncounterMedicationDispenseSlips`（分页加载就诊下住院药品发药单）

**HTTP**：`POST /api/app/inpatientPharmacyDispense/pageEncounterMedicationDispenseSlips`

**库房收敛（调用方）**：与 **IF-OPP-03** 一致——**`locationId` 可选**；工作站选患者后通常**不传**；未传时QueryService**不拼** `location_id` 谓词，按 `encounterId` + 状态子集 + `inpatientMedicationOnly` 收敛。

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 |
| `locationId` | Long | N | 发药库房（库位）主键；**null** 时服务端按 **`CurrentManagedLocationIdsResolver`** 注入 **`locationIds IN`**（与 IF-IPP-02 同源） |
| `tcmOnly` | Boolean | N | 中药过滤（与门诊相同：契约保留，实现可内存过滤过渡期） |
| `westernOnly` | Boolean | N | 西药过滤 |
| `dispenseStatusEnumIn` | `List<String>` | N | 与队列 Tab 一致 |
| `pageNo` | Integer | Y | **1-based** |
| `pageSize` | Integer | Y | ≥1 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<EncounterMedicationDispenseSlipRowDto>` | 发放行（**无** `inventoryDetailList`） |
| `totalCount` | Long | 总条数 |
| `pageNo` | Integer | 当前页 |
| `pageSize` | Integer | 页大小 |


**DTO 字段表：`EncounterMedicationDispenseSlipRowDto`（住院发药；与门诊同构，下列差异）**

> **住院裁剪**：**禁止**装配 **`inventoryDetailList`**；**不**调用 `ServiceLocationTemplate`；**不**在 IF-IPP-04 做 ChargeItem 保存前比价（**BR-IPP-011**）。**IF-IPP-03 列表展示**：**调用** **`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`** + 应用层私有 **`mapChargePricingByDispenseId`**（**1:1**，**非** IF-OPP-03 **`mergeChargePricingFromChargeItems`**）。**批号**：Mapper 先取 slip **`lotNumber`**，再以 **`lotNumberEffectiveByDispenseId`**（来自 **`MedicationDispenseQueryService#listMedicationDispensesByIds`**）非空覆盖。**日分单**：出参 **`procedureId`/`recordedTime`**；前端 **`map-api-to-inpatient-dispense-ui`** 按 **`recordedTime` 自然日**虚拟发药单、同 **`medReqId`** 合并数量（**BR-IPP-014**）。其余展示字段与 `AS-OP-PHA-001-SDD` §3.4 同语义。


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊主键 |
| `encounterBusNo` | String | N | 住院号 |
| `dispenseId` | Long | Y | 发放单主键 |
| `medReqId` | Long | Y | 药品申请主键（前端同日合并展示键） |
| `procedureId` | Long | N | 挂接护士执行记录主键；无执行挂接时为空 |
| `recordedTime` | LocalDateTime | N | 护士认定实际执行时刻（`cln_procedure.recorded_time`）；**按日虚拟发药单日键**；空则前端落入「未登记执行时间」桶 |
| `medicationId` | Long | Y | 药品定义主键 |
| `dispenseLocationId` | Long | Y | 发放库房（**只读**） |
| `dispenseLocationName` | String | N | 库房名称 |
| `lotNumber` | String | N | 批号（**只读**；slip 投影 + 领域聚合回补，见编排序列 ⑥） |
| `traceNo` | String | N | 追溯码（slip + 领域聚合回补） |
| `orderingDepartmentId` | Long | N | 开立科室 |
| `orderingDepartmentName` | String | N | 开立科室名 |
| `orderingPractitionerId` | Long | N | 开单医生；优先 slip **`med_requester_id`**，缺省回落药嘱 **`requester_id`** |
| `orderingPractitionerName` | String | N | 开单医生名 |
| `dispensingPractitionerName` | String | N | 发药药师 |
| `medicationCategoryCode` | String | N | 项目类型码 |
| `medicationCategoryText` | String | N | 项目类型展示 |
| `statusEnum` | String | Y | `DispenseStatus` code |
| `statusEnumText` | String | N | 状态展示 |
| `conditionSummaryText` | String | N | 诊断摘要 |
| `prescriptionNo` | String | N | 处方号（**`med_medication_request.prescription_no`**）；仍出参供打印/排查；**住院按患者发药 UI 分块不再按本字段**，改按 **`recordedTime` 自然日**（AS **BR-IPP-014**）；**禁止**用发放 **`busNo`** 或 `prescriptionGroupId` 冒充处方号 |
| `medicationName` | String | N | 项目名称 |
| `merchandiseName` | String | N | 商品名 |
| `specificationText` | String | N | 规格 |
| `packageSplitRatioText` | String | N | **包装最小单位换算比**展示（服务端拼装）：形如 **`1盒/36片`**；同单位×1 为 **`1支`**；与门诊 IF-OPP-03 / 库存明细同族；UI 规格列后展示 |
| `manufacturerName` | String | N | 厂家（`ProductDefinition#manufacturer`） |
| `productTableName` | String | N | 产品表锚点 |
| `doseValue` | BigDecimal | N | 单次剂量 |
| `splitRatio` | BigDecimal | N | 拆零比 |
| `frequencyCode` | String | N | 频次码 |
| `frequencyText` | String | N | 频次展示 |
| `routeCode` | String | N | 用法码 |
| `routeText` | String | N | 用法展示 |
| `doseUnitCode` | String | N | 剂量单位码 |
| `doseUnitText` | String | N | 剂量单位展示 |
| `unitCode` | String | Y | 发放单位码 |
| `unitText` | String | N | 单位展示 |
| `packageUnitCode` | String | N | 包装单位码（待扫换算辅助） |
| `packageUnitRatio` | BigDecimal | N | 包装相对拆零倍数 |
| `splitUnitCode` | String | N | 拆零单位码 |
| `traceUnitCode` | String | N | 追溯码单位：主档优先，无主档回落药典（与门诊同口径） |
| `traceUnitCodeText` | String | N | 追溯码单位展示文案（扫码弹窗「追溯码单位」列） |
| `traceUnitRatio` | BigDecimal | N | 追溯码单位→拆零比例：主档优先否则药典（1 追溯码单位 = N 拆零；弹窗「码倍」） |
| `lotHasTraceMaster` | Boolean | N | 主表该品批是否已有码（有码批硬校验 / 无码批软提示） |
| `dispensePerQuantity` | Integer | N | 单次发药数 |
| `dispensePerSupplyDays` | Integer | N | 供应天数 |
| `requestQuantity` | BigDecimal | N | 医嘱请求量 |
| `quantity` | BigDecimal | Y | 本行发放数量（**只读**） |
| `unitPrice` | BigDecimal | N | 单价（**只读**；锚定 **`ChargeItem.unit_price`**，与发放分段 **1:1**；无收费行时为空） |
| `totalPrice` | BigDecimal | N | 金额（**只读**；锚定 **`ChargeItem.total_price`**，**不求和**） |
| `prescriptionGroupId` | Long | N | 医嘱组号 |
| `requestOpenedTime` | LocalDateTime | N | 医嘱开立时间（`req_opened_time`；空可回落 `req_authored_time`；签发仅追溯不展示） |
| `skinTestFlag` | Boolean | N | 皮试 |
| `skinTestFlagText` | String | N | 皮试文案 |
| `tcmFlag` | Boolean | N | 中药标识 |
| `tcmFlagText` | String | N | 中药文案 |
| `infusionFlag` | Boolean | N | 输液 |
| `infusionFlagText` | String | N | 输液文案 |
| `partAttributeCode` | String | N | 拆分属性 |
| `partAttributeText` | String | N | 拆分属性展示 |
| `encounterMedicationTypeCode` | String | N | 就诊用药类别 |
| `encounterMedicationTypeText` | String | N | 类别展示 |
| `dispenseEnum` | String | N | 发放类型 code |
| `dispenseEnumText` | String | N | 发放类型展示 |
| `therapyEnum` | String | N | 长临 code |
| `therapyEnumText` | String | N | 长临展示 |
| `busNo` | String | N | 发放业务号 |


**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-IPP-103 | 明细加载失败 |


**Mapper 接口方法签名**

- `InpatientPharmacyDispenseMapper#toSlipPage(Page<MedicationDispenseSlipQueryModel> slipPage, Map<Long, MedicationRequestListQueryModel> reqByMedReqId, Map<Long, ProductDefinitionDisplayDto> productByMedicationId, Boolean tcmOnly, Boolean westernOnly, Map<Long, String> dispensingPractitionerNameByDispenseId, String encounterBusNo, Map<Long, String> orderingDepartmentNameByOrgId, Map<Long, String> orderingPractitionerNameById, Map<Long, String> dispenseLocationNameByLocationId, Map<Long, String> traceNoEffectiveByDispenseId, Map<Long, String> lotNumberEffectiveByDispenseId, Map<Long, BigDecimal> chargeUnitPriceByDispenseId, Map<Long, BigDecimal> chargeTotalPriceByDispenseId, Map<String, String> rateCodeLabelMap, Map<String, String> methodCodeLabelMap, Map<String, String> doseUnitLabelMap, Map<String, String> businessUnitLabelMap, Map<String, String> rxFlagLabelMap, Map<String, String> partAttributeLabelMap): PageEncounterMedicationDispenseSlipsResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `encounterId`、分页 | `locationId` 可选 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | — | 失败 EX-IPP-103 |
| 3 | Pre | `MedicationDispenseQueryService#pageMedicationDispenseSlip` | `encounterId`、可选 `locationId`、`inpatientMedicationOnly=true`、`dispenseStatusEnumIn` | **不含**候选库存；未传 `locationId` 不拼库房谓词 |
| 4 | Pre | `MedicationRequestQueryService#listMedicationRequestsByEncounter` | — | `tcmFlag` 等 |
| 5 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | — | 目录展示 |
| 6 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByIds` | 本页 `dispenseId` | 构建 **`traceNoEffectiveByDispenseId`**、**`lotNumberEffectiveByDispenseId`**（非空覆盖 slip）；收集 **`procedureId`** |
| 6a | Pre | `ProcedureQueryService#listByIds` | 本页挂接 `procedureId` | 回填 **`recordedTime`**（**BR-IPP-014**；禁止用 planned 作日键） |
| 7 | Pre | **`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`** + **`mapChargePricingByDispenseId`** | `encounterId`、本页 `dispenseId` | **`chargeUnitPriceByDispenseId`** / **`chargeTotalPriceByDispenseId`**（**1:1**） |
| 8 | Pre | `OrganizationQueryService#mapNamesByIds`、`PractitionerQueryService`、`LocationQueryService#mapNamesByIds` | — | 批量展示名 |
| 9 | Pre | `TerminologyOptionLoader#buildLabelMap` + `OrderCatalogDisplayAssembler` | — | 术语 |
| 10 | Asm | `InpatientPharmacyDispenseMapper#toSlipPage` / Loader enrich | ③～⑨ | **不**传入 inventory Map；前端按日+`medReqId` 虚拟分单 |


### 3.5 IF-IPP-04 `savePrepareInpatientMedicationDispense`（保存配药）

**HTTP**：`POST /api/app/inpatientPharmacyDispense/savePrepareInpatientMedicationDispense`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 上下文就诊 |
| `preparerPractitionerId` | Long | Y | 调配药师（**BR-IPP-004**） |
| `lines` | `List<SavePrepareInpatientDispenseLineSubmitDto>` | Y | 至少一行 `PREPARATION` |


**DTO 字段表：SavePrepareInpatientDispenseLineSubmitDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | Y | 发放单主键 |
| `traceNo` | String | N | 追溯码（多码逗号分隔）；**不得**用本接口改库房/批号/数量 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |
| `message` | String | 提示 |
| `processedDispenseIds` | `List<Long>` | 已处理 ID |


**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-IPP-201 | 追溯码已出库 |
| EX-IPP-202 | 调配药师无效或同人 |
| EX-IPP-203 | 状态不允许 |
| EX-IPP-204 | 单位/数量与医嘱不一致 |
| EX-IPP-205 | 库存不足 |


**Mapper 接口方法签名**

- `InpatientPharmacyDispenseMapper#toPrepareMedicationDispenseCommand(Long operatorId, Long preparerPractitionerId, List<SavePrepareInpatientDispenseLineSubmitDto> lines, Map<Long, MedicationDispense> dispenseById): PrepareMedicationDispenseCommand`
- `InpatientPharmacyDispenseMapper#toPrepareBatchResponse(List<Long> ids, String message, boolean success): SavePrepareInpatientMedicationDispenseResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `lines` 非空、`preparerPractitionerId` 非空 | EX-IPP-202 |
| 2 | Gate | `PractitionerQueryService#findById` + 当前用户 | 调配药师≠本人 | **BR-IPP-004** |
| 3 | Pre | `EncounterQueryService#mustGetEncounter` | — | — |
| 4 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByIds` + `MedicationRequestQueryService#listMedicationRequestsByEncounter` | — | 映射 |
| 5 | Pre | 逐行：`validateDispensePreparable`（**PREPARATION**）；数量=医嘱（**BR-IPP-007**）；**禁止**接受提交侧改 `locationId`/`lotNumber`/`quantity` | — | **BR-IPP-005/007** |
| 6 | Pre | `MedicationDispenseInventoryAvailabilityGate#assertSufficientForMedicationDispenseWithReserve` | 使用**聚合已持久化**库房+批号+数量；预占**同批**收窄 | **BR-IPP-008** / GAP-EXE-003 → EX-IPP-205 |
| 7 | Pre | `TraceNumberRecordQueryService#assertCodesNotAlreadyOutbound` | 提交追溯码 | **BR-IPP-003** |
| 8 | Pre | 应用内临时医嘱拆分行合计校验 | — | **BR-IPP-006** |
| 9 | Asm | `InpatientPharmacyDispenseMapper#toPrepareMedicationDispenseCommand` | **Cmd 项** `locationId`/`lotNumber`/`quantity` **取自聚合实体**，非 Request | **BR-IPP-007** |
| 10 | Cmd | `MedicationDispenseDomainService#prepareMedicationDispense` | ⑨ | **写库点** → `IN_PROGRESS` |
| 11 | Asm | `toPrepareBatchResponse` | — | Response |


### 3.6 IF-IPP-05 `dispenseInpatientMedications`（发药扣库）

**HTTP**：`POST /api/app/inpatientPharmacyDispense/dispenseInpatientMedications`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 上下文就诊 |
| `lines` | `List<DispenseMedicationLineSubmitDto>` | Y | 至少一行 |


**DTO 字段表：DispenseMedicationLineSubmitDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | Y | 发放单；扣减以库内快照为准 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | — |
| `message` | String | — |
| `processedDispenseIds` | `List<Long>` | — |


**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-IPP-203 | 状态不允许发药 |
| EX-IPP-205 | 库存不足 |


**Mapper 接口方法签名**

- `InpatientPharmacyDispenseMapper#toDispenseCommand(Long operatorId, Long practitionerId, List<MedicationDispense> dispensers): DispenseMedicationCommand`
- `InpatientPharmacyDispenseMapper#toDispenseBatchResponse(...): DispenseInpatientMedicationsResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `lines` 非空 | — |
| 2 | Pre | `PractitionerQueryService#findByUserId` | 发药人 | — |
| 3 | Pre | `EncounterQueryService#mustGetEncounter` | — | — |
| 4 | Pre | `mustGetMedicationDispense` + `validateDispensable` | 每行 | **`IN_PROGRESS`** **BR-IPP-009** |
| 5 | Pre | `MedicationDispenseInventoryAvailabilityGate#assertSufficientForMedicationDispenseWithReserve` | 聚合库房+批号+数量；预占与物理**同批** | 再校验 **BR-IPP-009** / GAP-EXE-003 |
| 6 | Asm | `toDispenseCommand` | — | — |
| 7 | Cmd | `MedicationDispenseDomainService#dispenseMedication` | ⑥ | **`COMPLETED`** **写库点** |
| 8 | Cmd | `InventoryItemDomainService#allocateAndDecrease` | ⑦ 成功后同事务；入参为 **操作单位→拆零** 后的拆零量（**BR-IPP-015**，同门诊 **BR-OPP-017**；有批号传 `lotNumber` 同批 FEFO 拆分） | 拆零扣减 |
| 9 | Cmd | `TraceNumberDomainService#recordTraceRecords` | ⑦ 行 **`trace_no`** | **BR-IPP-009a** 追溯 OUT（与门诊 IF-OPP-05 同族） |
| 10 | Asm | `toDispenseBatchResponse` | — | **无**医保事务外块 |


### 3.7 IF-IPP-06 `cancelInpatientMedicationDispense`（作废）

**HTTP**：`POST /api/app/inpatientPharmacyDispense/cancelInpatientMedicationDispense`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 上下文 |
| `lines` | `List<CancelMedicationDispenseLineSubmitDto>` | Y | 至少一行 |


**DTO 字段表：CancelMedicationDispenseLineSubmitDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | Y | 发放单 |
| `notPerformedReasonEnum` | String | Y | 未发药原因 **code**（落 `not_performed_reason_enum`；下拉枚举未落库前由前端/主数据维护 code 字面量） |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | — |
| `message` | String | — |
| `processedDispenseIds` | `List<Long>` | — |


**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-IPP-208 | 不可作废或原因缺失 |


**Mapper 接口方法签名**

- `InpatientPharmacyDispenseMapper#toCancelMedicationDispenseCommand(Long operatorId, List<CancelMedicationDispenseLineSubmitDto> lines): CancelMedicationDispenseCommand`
- `InpatientPharmacyDispenseMapper#toCancelBatchResponse(...): CancelInpatientMedicationDispenseResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | 每行原因非空 | EX-IPP-208 |
| 2 | Pre | `mustGetMedicationDispense` | 状态 ∈ `PREPARATION`∪`IN_PROGRESS` | **BR-IPP-010** |
| 3 | Asm | `toCancelMedicationDispenseCommand` | — | — |
| 4 | Cmd | `MedicationDispenseDomainService#cancelMedicationDispense` | — | **`DECLINED`** |
| 5 | Asm | `toCancelBatchResponse` | — | — |


## 4. QueryService 与事件刷新

- **MedicationDispenseSlip**：`MedicationDispenseQueryService（同步读 `med_medication_dispense`）` 维护；IF-IPP-03 消费 **`pageMedicationDispenseSlip`**；写成功后**最终一致**，前端刷新列表。
- **`MedicationDispenseCreatedEvent`** 载荷含 **`lotNumber`**（住院签发 FIFO / 护士执行写入聚合时非空）→ 投影 **``med_medication_dispense`（经 `MedicationDispenseQueryService`）.lot_number`**（见QueryService **v2.5** §4.2）；历史 slip 若批号空，IF-IPP-03 仍以 **`med_medication_dispense`** 回补。
- **ChargeItem 展示**：IF-IPP-03 **只读**查询，**不**触发写侧；住院签发 **「每一发放分段一行 ChargeItem」**（**`DM-MED-002` §9.7**），故列表按 **`dispenseId` 1:1** 映射单价/金额。
- **InpatientEncounterContextQueryModel**：IF-IPP-02 **`listInpatientContextsByEncounterIdsOrdered`** 仅回补展示；在院/病区过滤在 **`pageEncounterIdsForInpatientPharmacyPending`** 子查询完成。
- **SUMMARIZED** 汇总态：本工作站**不编排**；若数据存在且未传入状态子集，由调用方 `dispenseStatusEnumIn` 自然排除。


## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 序号对照


| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入语义 |
| --- | --- | --- | --- | --- |
| IF-IPP-04 | `savePrepareInpatientMedicationDispense` | 3.5-⑩ | `prepareMedicationDispense` | `PREPARATION`→`IN_PROGRESS`；库房/批号/数量**保持执行写入值** |
| IF-IPP-05 | `dispenseInpatientMedications` | 3.6-⑦ | `dispenseMedication` | `IN_PROGRESS`→`COMPLETED` |
| IF-IPP-05 | `dispenseInpatientMedications` | 3.6-⑧ | 库存扣减 | `wkf_inventory_item` |
| IF-IPP-05 | `dispenseInpatientMedications` | 3.6-⑨ | 追溯 OUT | `cus_trace_number` + `cus_trace_number_flow`（**MedicationDispense.trace_no**） |
| IF-IPP-06 | `cancelInpatientMedicationDispense` | 3.7-④ | `cancelMedicationDispense` | `DECLINED` |


### 5.2 事务边界 / 失败回滚


| 接口 | 事务边界 | 失败回滚 |
| --- | --- | --- |
| IF-IPP-04 | 单事务（序号 10） | 全局回滚 |
| IF-IPP-05 | 单事务（序号 7～8） | 全局回滚 |
| IF-IPP-06 | 单事务（序号 4） | 全局回滚 |
| IF-IPP-01～03 | 无写 Cmd | — |


## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 同一发放行并发配药/发药 | 乐观锁/版本或业务门禁 | `MedicationDispense` 实体 | §3.5-⑩、§3.6-⑦～⑧ |


### 6.2 并发控制


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 队列只读 | 无锁 | `InpatientPharmacyDispenseAppService` | §3.3 |


### 6.3 重试与容错


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 无医保上传 | 不适用 | — | IF-IPP-05 |


### 6.4 数据脱敏


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 队列/明细 | 仅业务必需字段 | Mapper | §3.3～3.4 |


### 6.5 审计日志


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 配药/发药/作废 | 领域事件 + 聚合人员时间字段 | `MedicationDispenseDomainService` | §3.5～3.7 Cmd |


## 7. 测试要点

### 7.1 功能测试


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| IF-IPP-01 含病区下拉 | 集成 | P0 |
| IF-IPP-02 在院+病区+关键字（床号）+已发/待发 | 集成 | P0 |
| IF-IPP-02 无启用药房→空队列 | 集成 | P0 |
| IF-IPP-03 无 `inventoryDetailList`；批号/单价/金额只读；不传 `locationId` | 集成 | P0 |
| IF-IPP-03 批号：领域表有值、slip 空时仍能展示 | 集成 | P0 |
| IF-IPP-03 价格：ChargeItem 1:1 映射 `unitPrice`/`totalPrice` | 集成 | P0 |
| IF-IPP-04 保存配药（不改批号数量） | 集成 | P0 |
| IF-IPP-05 发药扣库 | 集成 | P0 |
| IF-IPP-06 作废 | 集成 | P0 |
| 调配药师=当前用户拦截 | 单元 | P0 |


### 7.2 非功能测试


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 同 `dispenseId` 并发发药 | 集成 | P1 |


### 7.3 QueryService一致性


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 写后 Slip 投影 | 集成 | P0 |


### 7.4 集成测试


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 护士执行后 PREPARATION 行可保存配药 | 集成 | P0 |
| 出院轨患者仍可出现在队列（有匹配发放事实） | 集成 | P0 |


### 7.5 测试缺口跟踪


| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| Q-IP-PHA-001 | `validateDispensable` 与 `IN_PROGRESS` 窗口须与 DM **BR-MDP-003** 一致 | 领域对齐后回归 |


## 8. 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.10 | 2026-10-14 | **IF-IPP-05**：有批号扣库改 **`allocateAndDecrease(lotNumber)`** 同批 FEFO 拆分；对齐 **DM-WKF-008 v1.0.9** / 门诊 SDD **v2.19** |
| v2.9 | 2026-08-31 | **IF-IPP-03**：`EncounterMedicationDispenseSlipRowDto` 增 **`packageSplitRatioText`**；住院 `EncounterMedicationDispenseSlipReadAssembler` 经 **`MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText`**；对齐 **AS v2.9** |
| v2.8 | 2026-08-19 | **IF-IPP-05**：扣库换算收口 **操作单位→拆零（五类）**，**禁止**追溯码倍数；对齐 **AS v2.8 BR-IPP-015** / 门诊 **BR-OPP-017** |
| v2.7 | 2026-08-17 | **IF-IPP-02 / BR-IPP-001**：队列可见态扩至 **PENDING/COMPLETED/DISCHARGED**；`EncounterQueryService#listInpatientInHospitalEncounterIds` 含出院轨；已清床病区/床号回落末次 COMPLETED 位置；对齐 **AS v2.7** |
| v2.6 | 2026-08-07 | **IF-IPP-03 / BR-IPP-014**：slip 出参补 `procedureId`/`recordedTime`；Loader 经 `ProcedureQueryService#listByIds` 回填；前端按日虚拟发药单 + 同 `medReqId` 合并展示，并启用 **发药单笺面 + 列表明细**（与门诊同构）；对齐 **AS v2.6** |
| v2.5 | 2026-08-02 | **IF-IPP-03**：无主档时 `traceUnit*` 回落药典；扫码弹窗去掉处方号，追溯码单位/码倍列收窄（与门诊同口径） |
| v2.4 | 2026-08-02 | **IF-IPP-03 DTO**：补 `packageUnit*`/`splitUnitCode`/`traceUnitCode`/`traceUnitCodeText`/`traceUnitRatio`/`lotHasTraceMaster`；贴码单位术语文案与门诊同口径；批量扫码弹窗药品明细展示追溯码单位与码/拆零比 |
| v2.3 | 2026-07-31 | **IF-IPP-02**：队列回填 `contractDisplay`（`EncounterContractDisplayAssembler`，与门诊发药/医生站卡片同口径） |
| v2.2 | 2026-07-29 | **IF-IPP-04/05**：库存断言 Gate + **同批号预占**；对齐 **AS v2.3** / **SYS-IMP-001 GAP-EXE-003 v1.3** |
| v2.1 | 2026-07-15 | slip 开单医生：优先 `med_requester_id`，缺省回落药嘱 `requester_id` |
| v2.0 | 2026-06-11 | **处方号展示收敛**：与门诊一致，发药页分块/标题与打印匹配**仅** `prescriptionNo`；废止 `busNo`/`prescriptionGroupId` 回落；对齐 **AS-IP-DOR-003** 临时签发 `COMPOSITE_PRESCRIPTION` |
| v2.0 | 2026-07-02 | **IF-IPP-01**：`pharmacyOrganizationOptions` 为药房族（`PHARMACY`+`DEPARTMENT_PHARMACY`）；`pharmacyLocationId` 接受科室药房；对齐 **AS v2.0** / **DM-ADM-003** |
| v1.9 | 2026-06-09 | **IF-IPP-01**：药房 init 改 `CurrentManagedLocationIdsResolver`；Mapper 入参 `List<Location>`；废止 `getOrganizationTreeForPharmacy`；对齐 **AS v1.9** |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-24 | 初版：对齐 `AS-IP-PHA-001` v1.1；相对门诊 SDD 裁剪候选库存、改批号、医保、ChargeItem 比价；IF-IPP-02 在院队列+病区；领域读能力已落地 |
| v1.1 | 2026-05-24 | §1.1 应用层已落地；§2.3 明确 `EX_OPP_*` 复用；§3.6 `toDispenseCommand` 签名与实现一致 |
| v1.2 | 2026-05-24 | 规范刷对齐实现：统一 `TherapyType`；未发药原因下拉空列表 + DOC-CONFLICT；`PageWindowGate` 包路径为 `common.validator` |
| v1.3 | 2026-05-24 | **IF-IPP-02**：移除 Request `startDate`/`endDate` 契约；队列**禁止** `status_changed_time` 时间窗；排序 **`max(created_at) DESC`**；`wardId` 仅非空时过滤；对齐 **`AS-IP-PHA-001` v1.3** 与 `MedicationDispenseQueryService` 实现 |
| v1.4 | 2026-05-24 | **IF-IPP-02**：库房收敛改为 **`CurrentManagedLocationIdsResolver`**；管辖 empty 不拼谓词；**禁止** `listPharmaciesByOrganization`；对齐 **`AS-IP-PHA-001` v1.4** |
| v1.5 | 2026-05-24 | **IF-IPP-03**：`locationId` **可选**（对齐 IF-OPP-03）；Gate 仅校验 `encounterId`；未传 `locationId` 不拼库房谓词；对齐 **`AS-IP-PHA-001` v1.5** |
| v1.6 | 2026-05-24 | **IF-IPP-03**：**批号**——CreatedEvent 投影 + 领域回补；**ChargeItem** 列表 **`mapChargePricingByDispenseId`（1:1）**；§2.1/§2.2 增 **`ChargeItemQueryService`**；§3.4 编排序列 ⑥⑦、Mapper 签名、DTO 字段说明；§4 投影与 ChargeItem 边界；对齐 **`AS-IP-PHA-001` v1.7**、QueryService **v2.5** |
