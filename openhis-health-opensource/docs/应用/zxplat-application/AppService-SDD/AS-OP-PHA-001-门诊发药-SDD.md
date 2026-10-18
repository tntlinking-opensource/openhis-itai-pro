# 门诊发药 · 应用服务详细设计（SDD）

> 编码：AS-OP-PHA-001-SDD  
> 版本：v2.18 | 日期：2026-08-31 | 作者：（待填）  
> 来源契约：`AS-OP-PHA-001-门诊发药.md` v1.30 · DM：`DM-MED-002`、`DM-MED-003`、`DM-ADM-002`、`DM-ADM-003`、`DM-ADM-004` §1.5、`DM-CUS-006` 等 · QueryService：`DM-MED-002 §6.2 MedicationDispenseQueryService 发药单分页`、`DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）`、`ProductDefinition`（`med_medication_definition / adm_device_definition / wkf_activity_definition（主表）`，用于发放 **操作单位→拆零** 换算；五类优先，同码禁止 `trace_unit_ratio` 覆盖）

## 1. 实现边界

### 1.1 目标与边界


| 项 | 内容 |
| --- | --- |
| 模块 | 门诊发药应用服务（药房工作站：初始化、就诊队列已发/待发筛选、就诊下**药品**发药单明细含候选库房×批号库存、**保存配药**、**发药**、**作废**） |
| 边界 | 编排 **`MedicationDispense`（药品，不含耗材）** / `MedicationRequest` / `Encounter` / `Practitioner` / **`Location`（药房点位，非 Organization 药房树）** / `Organization`（**仅** `mapNamesByIds` 开单科室名） / **`ServiceLocationTemplate`（取药科室）** / **库存查询** 及 **`MedicationDispenseSlip`**、**`OutpatientEncounterContextQueryModel`** QueryService；医保商品销售/进销存上传为 **事务外应用协作**（见 §6.2，不纳入 §2.2 领域白名单）；库存实物扣减发生在 **IF-OPP-05 发药**，与 DM **§8.2** / 事件订阅对齐 |
| 编排权威 | `AS-OP-PHA-001-门诊发药.md` §2、§4 |
| 方法权威 | `DM-MED-002` §6、`DM-MED-003` §6、`DM-ADM-002` §6（查询侧）；**MedicationDispenseSlip**、**OutpatientEncounterContextQueryModel** QueryService §3；**DM-CUS-006**（候选药房解析） |
| 严格对齐 | IF 编号严格对齐 AS：`IF-OPP-01`～`IF-OPP-06` |
| 实现阶段口径 | **目标态全量契约**：部分查询与筛选（待发就诊分页、医嘱「发布中」目录过滤、追溯码重复出库、医保上传端口）在现行仓库中尚缺专用下推方法处，已在 **§2.3 / §8** 标注 **目标态待生成**；已存在类型与方法严格沿用下列 FQCN |


### 1.2 术语口径


| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 与 DM、QueryService一致；持久化枚举字段存 **`*.getCode()`** 字符串（与 `DispenseStatus`、`RequestStatus` 等一致），QueryService侧 `MedicationDispenseSlipQueryModel.statusEnum` 等以实现侧 JPA `Converter` 为准 |
| QueryService口径 | IF-OPP-02：先发药事实收敛就诊主键分页；**库房收敛**：**管辖库位非空**时 `MedicationDispense.location_id` **`IN` 并集**（调用方不传库房）；**管辖 empty** 不拼谓词；可选日期窗/`keyword` 由 **`PharmacyEncounterQueueLoader`** 预解析为 `resolvedEncounterScope`（就诊 **`startTime`**，与收费队列同口径）；**不按**发放行 `status_changed_time` 作时间窗；就诊排序 **`ORDER BY max(created_at) DESC`**。`dispenseStatusEnumIn` 由前端 Tab 传入（待发=`PREPARATION`+`IN_PROGRESS`，已发=`COMPLETED`）；再 **`EncounterQueryService#listOutpatientContextsByEncounterIdsOrdered`** 回补展示；IF-OPP-03：以 **`MedicationDispenseQueryService#pageMedicationDispenseSlip`** 为主路径，**仅药品**，过滤同上；行级 **候选库房×批号可用量** 由 **模板服务 × 库存查询** 装配（§3.4）；主表查询 **即时一致**（§4） |
| 事务口径 | Gate/Pre 失败禁止进入 Cmd；**IF-OPP-04**（保存配药）与 **IF-OPP-05**（发药）**各自独立事务**（默认）；医保上传失败 **不回滚** 已成功本地发药事实（与 AS §7 **一致性** 表述一致） |


## 2. 代码结构

```text
com/openhis/health/application/outpatient/pharmacy/dispense/
  service/
    OutpatientPharmacyDispenseAppService.java
  request/
    LoadPharmacyDispensePageInitRequest.java
    PagePendingDispenseEncounterQueueRequest.java
    PageEncounterMedicationDispenseSlipsRequest.java
    SavePrepareOutpatientMedicationDispenseRequest.java
    DispenseOutpatientMedicationsRequest.java
    CancelOutpatientMedicationDispenseRequest.java
  response/
    LoadPharmacyDispensePageInitResponse.java
    PagePendingDispenseEncounterQueueResponse.java
    PageEncounterMedicationDispenseSlipsResponse.java
    SavePrepareOutpatientMedicationDispenseResponse.java
    DispenseOutpatientMedicationsResponse.java
    CancelOutpatientMedicationDispenseResponse.java
  dto/
    PendingDispenseEncounterQueueRowDto.java
    EncounterMedicationDispenseSlipRowDto.java
    MedicationDispenseInventoryDetailDto.java
    SavePrepareDispenseLineSubmitDto.java
    DispenseMedicationLineSubmitDto.java
    CancelMedicationDispenseLineSubmitDto.java
  mapper/
    OutpatientPharmacyDispenseMapper.java
    OutpatientPharmacyDispenseMapperImpl.java
```

### 2.1 依赖注入表


| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate / 写路径 **`requireOperatorId`**（**B.6.5**）；IF-OPP-04/05 **`findByUserId`** 解析发药人 |
| `CurrentManagedLocationIdsResolver` | IF-OPP-01 **`listManagedActivePharmaciesOrEmpty`**；IF-OPP-02 **`listManagedLocationIdsOrEmpty`** → 非空写入 **`OutpatientPharmacyPendingEncounterPageQuery#locationIds`**；empty 不拼谓词（**B.6.5**） |
| `OrganizationQueryService` | **仅 IF-OPP-03**：**`mapNamesByIds(Collection<Long>)`** 批量解析开立科室机构展示名（**禁止**循环 `loadOrganizationByIdForDisplay`；**禁止**用于药房下拉）。**生产厂家**不得经本服务解析——Mapper 直取 **`ProductDefinitionDisplayDto#manufacturer`** 文本（IF-OPP-03 响应 **不**含供应商字段） |
| `PractitionerQueryService` | IF-OPP-01：`pageActivePractitionersForPosition(resolvedIds, …)`（ids 由 **`PractitionerRoleScopeLoader`** 预解析，见 §3.2）；IF-OPP-03 **目标态**：`findById`/批量解析 **`dispensingPractitionerName`**；IF-OPP-04/05：`findByUserId` 解析当前用户对应 **`Practitioner`**（发药人） |
| `PractitionerRoleScopeLoader` | IF-OPP-01：`resolvePractitionerIdsForActiveRole`（`WhaleRoleLookup` 解析 `pharmacistRoleFilterCode` 或默认 `ROLE_DOCTOR`） |
| `WhaleRoleLookup` | IF-OPP-01：语义码 → `whale_roles.id` |
| `TerminologyOptionLoader`（`module.common`） | IF-OPP-01：`loadOptions` / `loadLegacyRows`（下拉）。**IF-OPP-03**：**`buildLabelMap`** 预热频次/途径/剂量单位/业务单位/处方类别/拆分属性等；行内展示经 **`OrderCatalogDisplayAssembler#resolveCategoryDisplay`** 注入 Mapper（**禁止** AppService 私有 `buildConceptLabelMap`） |
| `CodeSystemQueryService` | IF-OPP-01：下拉枚举装配（`getOptionListByEnum`）。**不再**在 IF-OPP-03 列表行路径直接调用 **`getOptionListByCodeSystemName`** 建 Map（已由 **`TerminologyOptionLoader`** 承接） |
| `EncounterQueryService` | IF-OPP-03/04/05/06：`mustGetEncounter` 就诊存在性与上下文 Gate；IF-OPP-03：`mapMedTypeByEncounterDiagnosisIds` 回填笺面医保医疗类别 |
| `LocationQueryService` | IF-OPP-03：**`mapNamesByIds(Collection<Long>)`** 解析**发放库房**与**候选库存行库房**展示名（纯只读，结果注入 Mapper，禁止在 Mapper 内再查） |
| `MedicationDispenseQueryService` | 写前加载、门控（`mustGetMedicationDispense`、`validateDispensePreparable`、`validateDispensable`）；IF-OPP-02 **`pageEncounterIdsForOutpatientPharmacyPending`**（**非空 `locationIds`**，库房 IN）；**IF-OPP-03**：**`listMedicationDispensesByIds`** 回补领域实体上的 **`traceNo`**（与QueryService 刷新并存时取实体非空值）；IF-OPP-04～06 写前校验 |
| `MedicationDispenseDomainService` | IF-OPP-04：`prepareMedicationDispense`；IF-OPP-05：`dispenseMedication`；IF-OPP-06：`cancelMedicationDispense`（`CancelMedicationDispenseCommand`，与 **DM-MED-002** §6.1.3 一致） |
| `MedicationRequestQueryService` | IF-OPP-03/04：`loadMedicationRequestById`；临时医嘱数量校验 **BR-OPP-006** |
| `MedicationDispenseQueryService` | IF-OPP-03：`pageMedicationDispenseSlip`、`listMedicationDispenseSlip`（与QueryService §3 一致） |
| `EncounterContextReader` | IF-OPP-02：`listOutpatientContextsByEncounterIdsOrdered`（`application.shared.encounter`） |
| `PharmacyEncounterQueueLoader` | IF-OPP-02：待发药就诊队列分页（`pageOutpatientPharmacyPendingEncounters` 等，内部调 `MedicationDispenseQueryService` + Facade） |
| `PharmacyDispenseSlipQuerysupport | IF-OPP-03：发药单分页/列表读组装（`application.outpatient|inpatient.pharmacy/dispense/support`） |
| `ProductDefinitionBatchLoader` | IF-OPP-03：`loadAsMap`（`application.shared.product`） |
| **ServiceLocationTemplate 解析** | IF-OPP-03：按就诊/开立科室上下文得候选发放 **`Location`（药房/药库）ID 列表**（**目标态** Bean，FQCN 与 `DM-CUS-006` 实现包对齐；**禁止** Organization 药房节点） | 
| **InventoryItemQueryService**（或项目定额名） | IF-OPP-03：按「药品+候选库房」拉取 **批号级可用量**（数量口径为 **最小单位/拆零单位**，与 `wkf_inventory_item.quantity` 一致）；IF-OPP-04/05 Pre：经 **`MedicationDispenseInventoryAvailabilityGate`** → **`assertSufficientForMedicationDispenseGivenReserve`**（物理与预占**同批号**；**BR-OPP-008/009**、**EX-OPP-205**、**GAP-EXE-003**；**仅库存门禁**，与 **BR-OPP-013** 计费门禁 **分列**） |
| **`InventoryItemDomainService`** | IF-OPP-05 Cmd 后续：**`allocateAndDecrease`**（有批号：同批多供应商 FEFO 拆分扣；无批号：跨批号 FEFO）；入参数量均为 **最小单位** |
| **`TraceNumberDispenseTraceOrchestrator`** | IF-OPP-05 Cmd：**`recordDispenseOutLines`**（扣库后写追溯 **IF-TRC-16**；**BR-OPP-014**：`isRemainingSplitSufficientForDispenseOut` 盖住写 OUT，否则软跳过） |
| **ChargeItemQueryService** | IF-OPP-03： **`listChargeItemsForMedicationDispensePricing(Long encounterId, Collection<Long> dispenseIds)`** 拉取与本页发放单锚点一致的 **`ChargeItem`**（`dispense_table`=`med_medication_dispense`，状态含 **DRAFT/PLANNED/SETTLED**），聚合为 **`dispenseId → unitPrice/totalPrice`** 注入 Mapper。**IF-OPP-04 Pre（BR-OPP-013）**：同一方法在保存配药前汇总锚定 **`ChargeItem.total_price`**，并由 **`OutpatientPharmacyDispenseAppService#assertPrepareChargeTotalMatchesInventoryLotPricing`** 以 **`ChargeItem.unit_price`**（同 dispense 多行加权）重算总额比对（**BR-PRICE-SRC-001**；**不得**查当前定价表或 **`wkf_inventory_item.price`**） |
| **就诊诊断查询**（FQCN 以 **Encounter/Condition** 域实现为准） | IF-OPP-03 **目标态**：装配 **`conditionSummaryText`** |
| `OutpatientPharmacyDispenseMapper` | DTO 装配；`**OptionDto`** 使用公共库 |


### 2.2 领域方法白名单


| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedLocationIdsResolver` | `listManagedActivePharmaciesOrEmpty` | IF-OPP-01 |
| `OrganizationQueryService` | **`mapNamesByIds`** | IF-OPP-03（开单科室名） |
| `PractitionerQueryService` | `pageActivePractitionersForPosition(Collection<Long>, …)`、`findByUserId`、`findById` | IF-OPP-01 / IF-OPP-03 / IF-OPP-04 / IF-OPP-05 |
| `PractitionerRoleScopeLoader` | `resolvePractitionerIdsForActiveRole` | IF-OPP-01 Pre |
| `WhaleRoleLookup` | `findRoleIdByCode` / `resolvePlatformRoleId` | IF-OPP-01 Pre |
| `TerminologyOptionLoader` | `loadOptions`、`loadLegacyRows`、**`buildLabelMap`** | IF-OPP-01 / IF-OPP-03 |
| `OrderCatalogDisplayAssembler` | 静态 **`resolveCategoryDisplay`** | IF-OPP-03 Mapper |
| `CodeSystemQueryService` | `getOptionListByEnum` | IF-OPP-01 |
| `EncounterQueryService` | `mustGetEncounter` | IF-OPP-03 / IF-OPP-04 / IF-OPP-05 / IF-OPP-06 |
| `MedicationDispenseQueryService` | `mustGetMedicationDispense`、`validateDispensePreparable`、`validateDispensable`、`pageEncounterIdsForOutpatientPharmacyPending`（IF-OPP-02）、`listMedicationDispensesByIds`（IF-OPP-03 追溯码回补） | IF-OPP-02～06 |
| `LocationQueryService` | `mapNamesByIds` | IF-OPP-03 |
| `MedicationDispenseDomainService` | `prepareMedicationDispense`（`PrepareMedicationDispenseCommand`） | IF-OPP-04 |
| `MedicationDispenseDomainService` | `dispenseMedication`（`DispenseMedicationCommand`） | IF-OPP-05 |
| `InventoryItemDomainService` | `allocateAndDecrease`（可选 `lotNumber`） | IF-OPP-05（库存最小单位扣减） |
| `TraceNumberDispenseTraceOrchestrator` | `recordDispenseOutLines`（写 OUT；防御性复检剩余） | IF-OPP-05 |
| `MedicationDispenseDomainService` | `cancelMedicationDispense`（`CancelMedicationDispenseCommand`） | IF-OPP-06 |
| `MedicationRequestQueryService` | `listMedicationRequestsByEncounter`、`loadMedicationRequestById` | IF-OPP-03 / IF-OPP-04 |
| `MedicationDispenseQueryService` | `pageMedicationDispenseSlip`、`listMedicationDispenseSlip` | IF-OPP-03 |
| `EncounterContextReader` | `listOutpatientContextsByEncounterIdsOrdered` | IF-OPP-02 |
| `PharmacyEncounterQueueLoader` | `pageEncounterIdsForOutpatientPharmacyPending` | IF-OPP-02 |
| `PharmacyDispenseSlipQuerysupport | `pageMedicationDispenseSlip` / `listMedicationDispenseSlip` | IF-OPP-03 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-OPP-03 |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing` | IF-OPP-03 / IF-OPP-04（**BR-OPP-013** 账单单价门禁） |
| `InventoryItemQueryService` | `assertSufficientForMedicationDispenseGivenReserve`（经 Gate） | IF-OPP-04/05（库存断言；**不含**计费单价查询） |
| `MedicationDispenseInventoryAvailabilityGate` | `assertSufficientForMedicationDispenseWithReserve` | IF-OPP-04/05 |
| `MedicationDispenseQueryService` | `sumDispenseExeReserveQuantityInSplitUnit`（同批预占） | IF-OPP-04/05 |
| `TraceNoManageQueryService` | `assertCodesNotAlreadyOutbound`（实现以仓库 **`TraceNoManageQueryService`** FQCN 为准） | IF-OPP-04 |
| `TraceNumberQueryService` | **`assertCodesInNetStockForDispenseLine`** / **`assertCodesInHospitalForDispense`**（IF-OPP-04 有码批在院/本库）；核发写追溯经 Orchestrator **`isRemainingSplitSufficientForDispenseOut`**（**BR-OPP-014**，IF-OPP-05） | IF-OPP-04～05 |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2.2、§3.1 `status_enum` | IF-OPP-01 下拉；IF-OPP-02/03 筛选（缺省含 **`PREPARATION`/`IN_PROGRESS`/`COMPLETED`**）；IF-OPP-04～06 状态门控 |
| 发放类型 | `com.zxplat.domain.share.enums.MedicationDispenseType` | `DM-MED-002` §3.1 `dispense_enum`、§4.5 | IF-OPP-02/03：**门诊药品**范围收敛（**排除耗材发放**，与 AS **BR-OPP-012** 对齐） |
| 医嘱申请状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-MED-003` §4.1、`status_enum` | **BR-OPP-002** 发布目录过滤（IF-OPP-03 **目标态** 查询字段 `requestStatusEnumIn`，见 §8） |
| 治疗类型（临时/长期） | `com.zxplat.domain.share.enums.TherapyType` | `DM-MED-003` §4.2、`therapy_enum` | **BR-OPP-006** 临时医嘱拆分行合计校验（IF-OPP-04）；长期医嘱按次发放**不适用**拆分行合计 |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §4.1、`class_enum` | IF-OPP-01 就诊类型下拉（可选） |
| 门诊就诊状态展示 | `PagePendingDispenseEncounterQueueRowDto.statusEnum` | `DM-ADM-002` §3.1 `status_enum`；**OutpatientEncounterContextQueryModel** QueryService §2 `status_enum` | IF-OPP-02 队列行 |
| 未发药原因（初始化下拉 / 作废字典） | **目标态待生成** `com.zxplat.domain.share.enums.NotPerformedReason` + `CodeSystemQueryService#getOptionListByEnum`；当前可用术语表键位以实现为准 | `DM-MED-002` §4.6 **NotPerformedReason** | IF-OPP-01 / IF-OPP-06（作废原因装配可与初始化共用术语或独立字段） |
| 中药标识 | `EncounterMedicationDispenseSlipRowDto.tcmFlag` | `DM-MED-003` §3.1 `tcm_flag` | IF-OPP-03：由 `MedicationRequestQueryService#listMedicationRequestsByEncounter` 映射 `MedicationRequestListQueryModel.tcmFlag` |
| 发药队列就诊分页查询 | `MedicationDispenseQueryService#pageEncounterIdsForOutpatientPharmacyPending(OutpatientPharmacyPendingEncounterPageQuery): Page<Long>`；**非空** `locationIds` → **`location_id IN (...)`**；**empty** 不拼库房谓词 | `AS-OP-PHA-001` **BR-OPP-001**；`DM-MED-002` §3.1 `encounter_id`/`status_enum`/`location_id` | IF-OPP-02；HTTP **不含**库房；可选 `startDate`/`endDate`/`keyword` → 应用层 `resolvedEncounterScope`（就诊 `startTime`）；**无**发放行 `status_changed_time` 时间窗；`max(created_at)` 排序；`dispenseStatusEnumIn`、**门诊药品** |
| 追溯码重复出库校验 | **`TraceNoManageQueryService#assertCodesNotAlreadyOutbound(Collection<String>)`**（FQCN 以仓库 Trace 域 Bean 为准） | `AS-OP-PHA-001` **BR-OPP-003**；`DM-MED-002` **BR-MDP-009** | IF-OPP-04 Pre：在 **库存充足断言之后**、装配 Cmd 之前调用（与 **`OutpatientPharmacyDispenseAppService`** 当前序列一致） |
| 追溯码写 OUT 覆盖判定 | **`TraceNumberDispenseTraceOrchestrator#recordDispenseOutLines`** → **`isRemainingSplitSufficientForDispenseOut`** | **BR-OPP-014** / **AS-CUS-TRC-001** **BR-TRC-019** | **IF-OPP-05** 核发写 **IF-TRC-16**；盖住写 OUT，盖不住软跳过（不抛 **`TRACE_NUMBER_*`**） |
| 前端半扫确认 | openhis-pro-ui 门诊发药页 | **BR-OPP-015** / **BR-TRC-021** | 个数/剩余不足弹窗；确认后清空不足行 `traceNo` 再提交 |
| 库存可用量断言与最小单位换算 | 现行路径：`MedicationDispenseInventoryAvailabilityGate#assertSufficientForMedicationDispenseWithReserve` → `MedicationDispenseQueryService#sumDispenseExeReserveQuantityInSplitUnit(..., lotNumber)` + `InventoryItemQueryService#assertSufficientForMedicationDispenseGivenReserve`。数量经 **`MedicationProductQuantityToSplitUnitConverter`**：发放行 **`unit_code`（操作单位）→ 拆零**（目录五类倍数优先；**AS BR-OPP-017**）；同码「盒」**禁止**用 `trace_unit_ratio` 覆盖五类；**仅游离贴码单位**才回落贴码倍数。**可用 = 该批物理 − 同库房同品同批号待发预占**（排除本行；**GAP-EXE-003**）；批号空时双方均按全批汇总。**禁止**物理按批、预占按品混算 | `DM-MED-002` **BR-MDP-005**、**BR-MDP-008**；AS **BR-OPP-008**、**BR-OPP-009**、**BR-OPP-017**；**SYS-IMP-001** GAP-EXE-003 | IF-OPP-04/05 Pre |
| 发药实物扣减（同步事务） | `OutpatientPharmacyDispenseAppService` 在 **`MedicationDispenseDomainService#dispenseMedication` 成功落库之后**，先 **`MedicationProductQuantityToSplitUnitConverter`（操作单位→拆零，BR-OPP-017）**，再 **`InventoryItemDomainService#allocateAndDecrease`**：有批号传入 `lotNumber` 同批多行 FEFO 拆分扣；无批号跨批 FEFO；**禁止** `requireInventoryItemId…` 择首条整笔砸 | AS **BR-OPP-009**、**BR-OPP-017**；**DM-WKF-008** §6.1.4 | IF-OPP-05 Cmd 序列第 9 步 |
| 发药写追溯（同步事务） | **`TraceNumberDispenseTraceOrchestrator#recordDispenseOutLines`**（扣库后）：有码批且非空 `trace_no` → **`isRemainingSplitSufficientForDispenseOut`** 盖住则剩余先出 OUT，否则跳过 → **IF-TRC-16** | AS **BR-OPP-014** / **IF-TRC-16** | IF-OPP-05 Cmd 序列第 10 步 |


### 2.4 白名单双向一致性复核


| 复核项 | 结论 |
| --- | --- |
| 正向（白名单 → §3 序列） | PASS（**目标态** 方法出现在 IF-OPP-02/04/05 已标注步骤） |
| 反向（§3 序列 → 白名单） | PASS |


## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）


| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-OPP-01 | `loadPharmacyDispensePageInit` | 加载发药页初始化 | `POST /api/app/outpatientPharmacyDispense/loadPharmacyDispensePageInit` |
| IF-OPP-02 | `pagePendingDispenseEncounterQueue` | 分页查询门诊发药就诊队列（已发/待发） | `POST /api/app/outpatientPharmacyDispense/pagePendingDispenseEncounterQueue` |
| IF-OPP-03 | `pageEncounterMedicationDispenseSlips` | 分页加载就诊下药品发药单（含候选库房×批号库存） | `POST /api/app/outpatientPharmacyDispense/pageEncounterMedicationDispenseSlips` |
| IF-OPP-04 | `savePrepareOutpatientMedicationDispense` | 保存配药（PREPARATION→IN_PROGRESS） | `POST /api/app/outpatientPharmacyDispense/savePrepareOutpatientMedicationDispense` |
| IF-OPP-05 | `dispenseOutpatientMedications` | 发药扣库（IN_PROGRESS→COMPLETED） | `POST /api/app/outpatientPharmacyDispense/dispenseOutpatientMedications` |
| IF-OPP-06 | `cancelOutpatientMedicationDispense` | 作废发放（→DECLINED） | `POST /api/app/outpatientPharmacyDispense/cancelOutpatientMedicationDispense` |


### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁（`*QueryService`、`*QueryService`） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService`；持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | `OutpatientPharmacyDispenseMapper` 纯 DTO 装配，无领域不变式 | — |


记法：`MedicationDispenseQueryService#pageMedicationDispenseSlip` 表示在 `OutpatientPharmacyDispenseAppService` 中注入并调用该 Bean 方法。

### 3.2 IF-OPP-01 `loadPharmacyDispensePageInit`（加载发药页初始化）

**HTTP**：`POST /api/app/outpatientPharmacyDispense/loadPharmacyDispensePageInit`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pharmacyLocationId` | Long | N | 当前药房/科室药房库位主键（`adm_location.id`，`LocationForm.PHARMACY` 或 `DEPARTMENT_PHARMACY`）；用于收窄调配药师候选（若前端传入） |
| `practitionerPageNum` | Integer | N | 调配药师分页页码（0-based，与 Spring `PageRequest` 一致） |
| `practitionerPageSize` | Integer | N | 调配药师分页大小 |
| `pharmacistRoleFilterCode` | String | N | **已废弃/忽略**；调配药师固定按 **`ROLE_PHARMACIST`** + 药房 Location 管辖收窄 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pharmacyOrganizationOptions` | `List<OptionDto>` | 可操作药房族选项（**`PHARMACY` + `DEPARTMENT_PHARMACY`**）；**`value` = `adm_location.id`**；字段名历史兼容，**非** `adm_organization.id` |
| `dispenseStatusFilterOptions` | `List<OptionDto>` | 发药状态下拉（`DispenseStatus`） |
| `encounterClassOptions` | `List<OptionDto>` | 就诊类别下拉（`EncounterClass`，可选） |
| `notPerformedReasonOptions` | `List<OptionDto>` | 未发药原因（**目标态** 枚举/术语，见 §2.3） |
| `preparerCandidatePage` | `Page<PractitionerOptionDto>` | 调配药师候选分页 |

**DTO 字段表：PractitionerOptionDto**（本模块 dto）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `practitionerId` | Long | Y | 药师主键 |
| `displayName` | String | Y | 展示姓名 |
| `orgId` | Long | N | 所属机构 |
| `orgName` | String | N | 所属机构名称 |


**DTO 字段表：OptionDto**（公共库）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `value` | String | Y | 枚举/字典 code |
| `label` | String | Y | 展示文案 |
| `disabled` | Boolean | N | 是否禁用 |


**关键校验**

- 术语或机构装载失败 → **EX-OPP-101**。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-OPP-101 | 元数据加载失败（映射 AS §5） |


**Mapper 接口方法签名**

- `OutpatientPharmacyDispenseMapper#toLoadInitResponse(List<Location> pharmacies, List<OptionDto> dispenseStatusOptions, List<OptionDto> encounterClassOptions, List<OptionDto> notPerformedReasonOptions, Page<Practitioner> preparerPage): LoadPharmacyDispensePageInitResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-OPP-101 |
| 2 | Pre | `CurrentManagedLocationIdsResolver#listManagedActivePharmaciesOrEmpty()` | — | 装载管辖内启用药房 **Location**（见 **DM-ADM-003** §1.5） |
| 3 | Pre | `CodeSystemQueryService#getOptionListByEnum(DispenseStatus.class)` | — | 发药状态筛选项 |
| 4 | Pre | `CodeSystemQueryService#getOptionListByEnum(EncounterClass.class)` | — | 可选；若术语未配置可降级空列表 |
| 5 | Pre | `CodeSystemQueryService#getOptionListByEnum(Class)` | **若** `NotPerformedReason` 枚举已在 `zxplat-domain` 发布则传入该类；否则跳过并返回空选项 | **目标态**见 §2.3 |
| 6 | Pre | `PractitionerRoleScopeLoader#pageActivePractitionersForPlatformRoleInLocations(ROLE_PHARMACIST, pharmacyLocationIds, …)` | 药师岗 + 药房库位 | 调配药师候选：须 **`ROLE_PHARMACIST`** 且 **`adm_pr_assign.location_id`** 落在 `pharmacyLocationId`（请求指定）或步骤 2 管辖内启用药房；无命中库位时返回空分页 |
| 7 | Asm | `OutpatientPharmacyDispenseMapper#toLoadInitResponse` | ①～⑥ 结果 | 仅读路径 |


### 3.3 IF-OPP-02 `pagePendingDispenseEncounterQueue`（分页查询门诊发药就诊队列）

**HTTP**：`POST /api/app/outpatientPharmacyDispense/pagePendingDispenseEncounterQueue`

**库房收敛（服务端，不出现在 Request）**：**`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`**。**若列表非空**：去重写入 **`OutpatientPharmacyPendingEncounterPageQuery#locationIds`**，**`MedicationDispenseQueryService`** 以 **`location_id IN (locationIds)`** 过滤。**若列表为空**：**不拼** `location_id` 谓词（过渡，待身份模型注入）。**禁止**由调用方传入库房以扩大可见范围；**禁止** `listPharmaciesByOrganization`。

**队列时间与排序（服务端）**：可选 `startDate`/`endDate` 按就诊 **`startTime`** 收窄（与门诊收费队列同口径；`PharmacyEncounterQueueLoader` 写入 `resolvedEncounterScope`，可与关键字求交）。**禁止**以发放行 `status_changed_time` 作时间窗谓词。就诊分页排序为同一就诊下命中发放行的 **`max(created_at) DESC`**（`MedicationDispenseQueryService#pageEncounterIdsForOutpatientPharmacyPending`）。

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 患者姓名/拼音/就诊号等（关键字 EXISTS 子查询 **`OutpatientEncounterContextQueryModel`**） |
| `startDate` / `endDate` | LocalDate | N | 可选日期窗：按就诊 **`startTime`** 收窄 AMB（与收费队列同口径）；空则不额外按日收窄 |
| `dispenseStatusEnumIn` | `List<String>` | N | 参与收敛的 `DispenseStatus` code 集合；**空**表示不按发放状态过滤；**待发药 Tab** 传 `PREPARATION`+`IN_PROGRESS`；**已发药 Tab** 传 `COMPLETED`（与 AS **BR-OPP-001** 一致） |
| `pageNum` | Integer | Y | 页码（≥1） |
| `pageSize` | Integer | Y | 页大小 |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | 总条数 |
| `rows` | `List<PendingDispenseEncounterQueueRowDto>` | 就诊队列表格行 |


**DTO 字段表：PendingDispenseEncounterQueueRowDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊主键 |
| `encounterBusNo` | String | N | 就诊业务号 |
| `patientId` | Long | Y | 患者主键 |
| `patientName` | String | N | 患者姓名 |
| `genderEnum` | String | N | 性别 code |
| `genderEnumText` | String | N | 性别展示文案（由 **`AdministrativeGender`** code 解析；前端只展示不翻译） |
| `ageText` | String | N | 年龄展示 |
| `organizationName` | String | N | 科室名称 |
| `doctorName` | String | N | 接诊医生 |
| `statusEnum` | String | N | 就诊状态 code（`OutpatientEncounterContextQueryModel`） |
| `statusEnumText` | String | N | 就诊状态文案 |
| `dispensePendingSummaryText` | String | N | **目标态**：摘要（如「待配药 n / 待发药 m / 已发放 k」）；由二次聚合查询填充 |
| `startTime` | `LocalDateTime` | N | 就诊开始时间 |


**关键校验**

- 分页参数非法 → **EX-OPP-102**。
- 未登录或会话无效 → **EX-OPP-102**。
- **`getCurrentOrganizationUnitId()`** 为空（机构上下文缺失）→ **EX-OPP-102**。
- 领域查询 **`locationIds` 必须非空**；应用层在服务机构下**无任何启用药房库位**时已短路为空分页。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-OPP-102 | 队列查询失败 |


**Mapper 接口方法签名**

- `OutpatientPharmacyDispenseMapper#toQueuePage(Page<Long> encounterIdPage, List<OutpatientEncounterContextQueryModel> infos, Map<Long, String> dispenseSummaryByEncounterId): PagePendingDispenseEncounterQueueResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | Request 分页 | 分页非法、未登录 → EX-OPP-102 |
| 2 | Pre | **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`** | — | 非空→写入查询 **`locationIds`**；**empty**→不拼库房谓词 |
| 3 | Pre | `PharmacyEncounterQueueLoader#pageEncounterIdsForOutpatientPharmacyPending` → `MedicationDispenseQueryService#pageEncounterIdsForOutpatientPharmacyPending` | ② 的 **`locationIds`**、`dispenseStatusEnumIn`、日期/关键字预解析的 **`resolvedEncounterScope`**、**门诊药品**、分页 | **`location_id IN`**；收敛 **就诊 ID 分页**；`max(created_at)` 排序 |
| 4 | Pre | `EncounterQueryService#listOutpatientContextsByEncounterIdsOrdered(List<Long>)` | ③ 返回的 ID 列表（有序） | 回补患者与就诊展示字段 |
| 5 | Asm | `OutpatientPharmacyDispenseMapper#toQueuePage` | ③④或空壳；`dispensePendingSummaryText` **目标态** 留空或由前端二次查询 | 仅读路径 |


### 3.4 IF-OPP-03 `pageEncounterMedicationDispenseSlips`（分页加载就诊下药品发药单/发放行）

**HTTP**：`POST /api/app/outpatientPharmacyDispense/pageEncounterMedicationDispenseSlips`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `locationId` | Long | N | 发药药房（显式单库位）；**未传**时服务端按 **`CurrentManagedLocationIdsResolver`** 注入 **`locationIds IN`**（与 IF-OPP-02 同源） |
| `tcmOnly` | Boolean | N | `true` 仅中药；`false`/null 时若 `westernOnly=true` 则仅非中药；二者均空表示不过滤 |
| `westernOnly` | Boolean | N | 仅非中药 |
| `dispenseStatusEnumIn` | `List<String>` | N | 与 IF-OPP-02 语义对齐：**空**=`PREPARATION`+`IN_PROGRESS`+`COMPLETED`；待发 Tab=前两；已发 Tab=`COMPLETED`；**仅药品** |
| `requestStatusEnumIn` | `List<String>` | N | **目标态**：医嘱发布态过滤（`RequestStatus` code），落实 **BR-OPP-002** |
| `pageNum` | Integer | Y | 页码 |
| `pageSize` | Integer | Y | 页大小 |

**实现口径（v2.5～）**：`EncounterMedicationDispenseSlipReadAssembler` 在 slip 行装配后按 **`tcmFlag`**（空时回落 `tcmFlagText`/`medicationCategoryText` 含「中药」）过滤；`tcmOnly=true` 仅饮片、`westernOnly=true` 仅西药中成药；二者均空不过滤。就诊单页场景下过滤后 `totalCount` 与 `content` 对齐。


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | 总条数 |
| `rows` | `List<EncounterMedicationDispenseSlipRowDto>` | 发放行 |


**DTO 字段表：`EncounterMedicationDispenseSlipRowDto`（患者发药信息 / 与历史导出范例语义校准）**

> **校准约定**：下表为 **OpenHIS 命名**；括号内标注历史范例字段名便于对照。持久化枚举在 HTTP JSON 中输出 **`*.getCode()` 字符串**（与 §2.3、`DM-MED-002` **`DispenseStatus`** 一致）；词典码字段 companion `*Text` 承担范例中 `_dictText` / `_enumText` 的展示职责。**单价/金额**：存在 **`ChargeItem`** 锚点时由 **`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`** 汇总回填；就诊尚无对应收费行时可空白。**术语展示**：频次/途径/剂量单位/业务单位/处方类别/拆分属性等通过 **`TerminologyOptionLoader#buildLabelMap`** 预热为 Map，**`OrderCatalogDisplayAssembler#resolveCategoryDisplay`** 解析行内 `*Text`（禁止 Mapper / AppService 内私有术语 Map；**`UNIT_CODE` 本请求内只预热一次**，库存明细与发药单行共用）。


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊主键（范例 `encounterId`） |
| `encounterBusNo` | String | N | 就诊业务号（范例 `encounterBusNo` / 就诊 NO） |
| `dispenseId` | Long | Y | 发放单主键（范例 `dispenseId`） |
| `medReqId` | Long | Y | 药品申请主键（范例 `requestId`） |
| `medicationId` | Long | Y | 药品/项目定义主键（范例 `itemId`，对齐 **`medication_id`**） |
| `dispenseLocationId` | Long | Y | **发放库房**机构 ID（范例 `locationId`；**MedicationDispense.location_id**） |
| `dispenseLocationName` | String | N | 发放库房名称（范例 `locationName`） |
| `lotNumber` | String | N | 批号/批次号（范例 `lotNumber`） |
| `remainingQuantity` | `BigDecimal` | N | **发放库房×本行批号**当前账面剩余数量（最小单位/拆零单位，与 `wkf_inventory_item.quantity` 一致；IF-OPP-03 **现查**，`InventoryItemQueryService#findAvailableCatalogRowsForOrder` 按 `dispenseLocationId`×`medicationId` 批量取可用品正数量未过期行后按批号汇总；库房或批号空不回填；无匹配账面行时为 `0`）。与 `inventoryDetailList`（候选库房×批号）分列，供发药单笺面展示 |
| `traceNo` | String | N | 追溯码（范例 `traceNo`） |
| `orderingDepartmentId` | Long | N | 开立科室 ID |
| `orderingDepartmentName` | String | N | 开立科室名称（范例 `departmentName`） |
| `orderingPractitionerId` | Long | N | 开单医生 ID（范例 `doctorId`）；优先 slip **`med_requester_id`**，缺省回落药嘱 **`requester_id`** |
| `orderingPractitionerName` | String | N | 开单医生姓名（范例 `doctorName`） |
| `dispensingPractitionerName` | String | N | 发药药师姓名（范例 `dispenseDoctorName`；已核发后有值） |
| `medicationCategoryCode` | String | N | 项目类型/药品大类术语码（范例 `itemType` ↔ `med_category_code`） |
| `medicationCategoryText` | String | N | 项目类型展示（范例 `itemType_dictText`） |
| `statusEnum` | String | Y | **`DispenseStatus` code**（范例曾为 Integer，本项目统一 **String**） |
| `statusEnumText` | String | N | 发药状态展示（范例 `statusEnum_enumText`） |
| `conditionSummaryText` | String | N | 诊断摘要/主要诊断名（可与医生站契约字段 **`conditionDefinitionName`** 同源展示；可拼接多病种以实现为准） |
| `prescriptionNo` | String | N | 处方号（**`med_medication_request.prescription_no`**，`CP` 前缀）；`EncounterMedicationDispenseSlipReadAssembler` 在发放 slip 未 join 时从 **`MedicationRequestListQueryModel`** 回填；**禁止**用发放 **`busNo`（`MDP`）**、`prescriptionGroupId`（输液 **`group_id`**）或 `MR-{id}` 冒充；前端 `map-api-to-dispense-ui` 分块/标题与报表 `OutpatientPrescriptionPrintDatasetService` 匹配键**仅**读本字段 |
| `medicationName` | String | N | 项目名称/通用展示名（范例 `itemName`） |
| `merchandiseName` | String | N | 商品名（范例 `merchandiseName`） |
| `specificationText` | String | N | 规格（范例 `totalVolume`） |
| `packageSplitRatioText` | String | N | **包装最小单位换算比**展示（服务端拼装）：形如 **`1盒/36片`**；同单位×1 为 **`1支`**；与库存明细同族（**`MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText`**）；UI 规格列后展示；非临床装量后缀 `1片x100` |
| `manufacturerName` | String | N | 生产厂家（**`ProductDefinitionDisplayDto#manufacturer`** 文本；**非** `supplier_name`，**禁止**组织主键解析） |
| `productTableName` | String | N | 目录所在物理表锚点（范例 `itemTable`，对齐 **`product_table_name`**） |
| `doseValue` | `BigDecimal` | N | 单次剂量（范例 `dose`） |
| `splitRatio` | `BigDecimal` | N | 拆零比（范例 `partPercent`） |
| `frequencyCode` | String | N | 用药频次术语码（范例 `rateCode` ↔ `rate_code`） |
| `frequencyText` | String | N | 用药频次展示（范例 `rateCode_dictText`） |
| `routeCode` | String | N | 给药途径/用法术语码（范例 `methodCode` ↔ `method_code`） |
| `routeText` | String | N | 用法展示（范例 `methodCode_dictText`） |
| `doseUnitCode` | String | N | 剂量单位码（范例 `doseUnitCode` ↔ `unit_code`） |
| `doseUnitText` | String | N | 剂量单位展示（范例 `doseUnitCode_dictText`） |
| `unitCode` | String | Y | 发放/计价单位码（范例 `unitCode`） |
| `unitText` | String | N | 单位展示（范例 `unitCode_dictText`） |
| `packageUnitCode` | String | N | 包装单位码（产品定义；待扫换算辅助） |
| `packageUnitRatio` | `BigDecimal` | N | 包装相对拆零倍数（1 包装 = N 拆零） |
| `splitUnitCode` | String | N | 拆零单位码（产品定义） |
| `traceUnitCode` | String | N | 追溯码单位：优先主档 `cus_trace_number.trace_unit_code`，**无主档时回落药典** `MedicationDefinition.trace_unit_code`（待扫/弹窗展示） |
| `traceUnitCodeText` | String | N | 追溯码单位展示文案（`UNIT_CODE`；扫码弹窗「追溯码单位」列） |
| `traceUnitRatio` | `BigDecimal` | N | 追溯码单位→拆零比例：优先主档，无主档回落药典；语义 **1 追溯码单位 = N 拆零**（弹窗「码倍」列展示 `×N`） |
| `lotHasTraceMaster` | Boolean | N | 主表该品批是否已有码（有码批=true：硬校验；无码批：扫码软提示） |
| `dispensePerQuantity` | Integer | N | 单次发药数（范例 `dispensePerQuantity`） |
| `dispensePerSupplyDays` | Integer | N | 每次发药供应天数（范例 `dispensePerDuration`） |
| `requestQuantity` | `BigDecimal` | N | **医嘱开立总量**（一次就诊同一药品申请上的总需求量，业务单位与医嘱一致；范例 `requestQuantity`） |
| `quantity` | `BigDecimal` | Y | **本发放单行数量**（本次发放分段实物数量，`unitCode` 与之对齐；范例 `quantity`） |
| `unitPrice` | `BigDecimal` | N | 单价（范例 `unitPrice`；由 **`ChargeItem`** 按发放单锚点加权折算，无收费行时为空） |
| `totalPrice` | `BigDecimal` | N | 金额（范例 `totalPrice`；与本发放分段锚点对应的 **`ChargeItem.total_price` 合计**） |
| `prescriptionGroupId` | Long | N | 医嘱组号（范例 `groupId`） |
| `requestOpenedTime` | `LocalDateTime` | N | 医嘱开立时间（`req_opened_time`；空可回落 `req_authored_time`；签发仅追溯不展示） |
| `skinTestFlag` | Boolean | N | 是否皮试医嘱（范例曾为 Integer；HTTP 输出 **布尔**） |
| `skinTestFlagText` | String | N | 皮试标志文案（如「是/否」） |
| `tcmFlag` | Boolean | N | 中药标识 |
| `tcmFlagText` | String | N | 中药标识文案（范例 `tcmFlagDesc`） |
| `infusionFlag` | Boolean | N | 是否输液/注射相关医嘱（范例曾为 Integer） |
| `infusionFlagText` | String | N | 输液/注射相关展示（范例 `injectFlagDesc`） |
| `partAttributeCode` | String | N | 拆分属性（范例 `partAttributeEnum`；持久化 **String code** 与全院枚举策略对齐） |
| `partAttributeText` | String | N | 拆分属性展示（术语 Map 装配） |
| `encounterMedicationTypeCode` | String | N | 医保医疗类别码（**`YbMedType#getCode()`** ↔ **`adm_encounter_diagnosis.med_type`**）；由 **`EncounterMedicationDispenseSlipPrintEnricher`** 按本方药嘱 **`encounter_diagnosis_ids` 并集首位非空 med_type** 回填；**一张方一种类型**（同 `prescriptionNo` 各行一致）；与纸质方头 **`rx_type_code` / `prescriptionTypeLine*`** 解耦 |
| `encounterMedicationTypeText` | String | N | 医保医疗类别展示（**`YbMedType#getDisplay()`**，如「普通门诊」「门诊慢病」）；发药单笺面「就诊类型」与处方打印 **`feeNatureText` 槽位**（模板标题「就诊类型」）同源；纸质方头分类仍走 `prescriptionTypeLine*` |
| `dispenseEnum` | String | N | 发放类型 **`MedicationDispenseType` code** |
| `dispenseEnumText` | String | N | 发放类型展示 |
| `therapyEnum` | String | N | 长临标识 **`TherapyType` code**（**BR-OPP-006** 仅 `TEMPORARY`） |
| `therapyEnumText` | String | N | 长临医嘱展示 |
| `busNo` | String | N | 发放业务号 |
| `inventoryDetailList` | `List<MedicationDispenseInventoryDetailDto>` | N | **候选库存明细**（范例 `inventoryDetailList` / `InventoryDetailDto`；装配来源 §3.4 序号 ⑤） |
| `genderEnumText` | String | N | 患者性别展示（处方打印；由就诊上下文性别 code 解析） |
| `feeNatureText` | String | N | 费用性质分类展示文案（**`ContractCategory`** → 自费/公费/省医保/市医保/职工医保）；由 **`ContractExpenseNatureAssembler`** 按药嘱 **`contract_id`** 批量解析；无合同时为空，前端回落「自费」 |


**DTO 字段表：`MedicationDispenseInventoryDetailDto`（库存明细行 / 范例 `InventoryDetailDto`）**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pharmacyOrganizationId` | Long | Y | 候选发放库房 ID |
| `pharmacyDisplayName` | String | N | 库房名称 |
| `lotNumber` | String | N | 批号 |
| `availableQuantity` | `BigDecimal` | Y | 可用数量（计量口径与发放单位一致） |
| `unitCode` | String | Y | 计量单位码 |
| `unitText` | String | N | 单位展示（可选） |


**关键校验**

- `EncounterQueryService#mustGetEncounter(encounterId)` 失败 → **EX-OPP-103**。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-OPP-103 | 明细加载失败 / 无权访问 |


**Mapper 接口方法签名**

- `OutpatientPharmacyDispenseMapper#toSlipPage(Page<MedicationDispenseSlipQueryModel> slipPage, Map<Long, MedicationRequestListQueryModel> reqByMedReqId, Map<Long, ProductDefinitionDisplayDto> productByMedicationId, Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId, Boolean tcmOnly, Boolean westernOnly, Map<Long, String> dispensingPractitionerNameByDispenseId, String encounterBusNo, Map<Long, String> orderingDepartmentNameByOrgId, Map<Long, String> orderingPractitionerNameById, Map<Long, String> dispenseLocationNameByLocationId, Map<Long, BigDecimal> chargeUnitPriceByDispenseId, Map<Long, BigDecimal> chargeTotalPriceByDispenseId, Map<Long, String> traceNoEffectiveByDispenseId, Map<String, String> rateCodeLabelMap, Map<String, String> methodCodeLabelMap, Map<String, String> doseUnitLabelMap, Map<String, String> businessUnitLabelMap, Map<String, String> rxFlagLabelMap, Map<String, String> partAttributeLabelMap): PageEncounterMedicationDispenseSlipsResponse`

**纯装配约束**：Mapper **不得**发起仓储/远程调用；发药人姓名、开立科室/医师名、库房名、收费折算单价/总价、追溯码有效值及各术语 **`Map<String,String>`** 均由应用服务 **`Pre`** 阶段批量解析后传入；**`manufacturerName`** 在 Mapper 内由 **`productByMedicationId`** 直取 **`manufacturer`**（无额外 Pre）。


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `encounterId`、`locationId`、分页 | 必填校验 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long)` | `encounterId` | 就诊必须存在；失败 EX-OPP-103 |
| 3 | Pre | `MedicationDispenseQueryService#pageMedicationDispenseSlip(MedicationDispenseSlipPageQuery)` | `encounterId`、`locationId`、`dispenseStatusEnumIn`（映射查询）、**门诊药品**、`分页`；**目标态** `tcmFlag`、`requestStatusEnumIn` 下推 | QueryService 分页；若查询类尚无中药/医嘱态字段，则 **Asm 前内存过滤**（仅小数据量过渡期，§8） |
| 4 | Pre | `MedicationRequestQueryService#listMedicationRequestsByEncounter(Long)` | `encounterId` | 构建 `medReqId → MedicationRequestListQueryModel`，取 `tcmFlag`、开立科室（**ServiceLocationTemplate** 输入） |
| 5 | Pre | **ServiceLocationTemplate 解析 + 库存查询** | 本页每行：`medicationId` + 就诊接诊或开立科室上下文 | 得候选库房列表；再批量查 **库房×批号可用量**，写入 `inventoryDetailListByDispenseId`（**BR-OPP-002**）；**`LocationQueryService#mapNamesByIds`** 回填候选库存行 **`pharmacyDisplayName`** 与单位术语展示 |
| 5a | Asm | **发放库房×批号剩余数量现查** | 本页行：`dispenseLocationId` + `medicationId` + `lotNumber` | Asm 后由 `OutpatientEncounterMedicationDispenseSlipPageLoader#enrichRemainingQuantityFromInventory`：对已锚定库房×批号的行再批量 `findAvailableCatalogRowsForOrder`，回填 **`remainingQuantity`**（无账面匹配置 `0`；与候选明细分列） |
| 6 | Pre | `ProductDefinitionBatchLoader#loadAsMap(Collection<Long>)` | 本页 `medicationId` | 装配名称、规格、**`manufacturer` 文本**、`productTableName`、大类等（**禁止**将厂家当组织 id 再查库；本 IF **不**下发供应商） |
| 7 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByIds` | 本页 `dispenseId` 列表 | merge **`traceNo`**（QueryService空时以领域实体为准） |
| 8 | Pre | **批量展示名**：`PractitionerQueryService` / **`OrganizationQueryService#mapNamesByIds`** / **`LocationQueryService#mapNamesByIds`** | 发药人、开立医师、开立科室机构、发放库房 ID 集合 | 各 `Map<Long,String>`；开立科室 **禁止**循环 `loadOrganizationByIdForDisplay`（**A.9.1**） |
| 9 | Pre | **`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`** + 应用层聚合 | `encounterId`、本页 `dispenseId` | 写入 **`chargeUnitPriceByDispenseId`** / **`chargeTotalPriceByDispenseId`** |
| 10 | Pre | **`TerminologyOptionLoader#buildLabelMap`**（多编码体系，见 `TerminologyCodeSystemNameConstants`） | 频次/途径/剂量单位/业务单位/处方类别/拆分属性 等 | 构建各 **`labelByCode`** 术语表；Mapper 内 **`OrderCatalogDisplayAssembler#resolveCategoryDisplay`** |
| 11 | Pre | **目标态** 就诊诊断查询 | `encounterId` | 装配 `conditionSummaryText`（现行未接线时允许空） |
| 12 | Asm | `EncounterMedicationDispenseSlipReadAssembler#assembleOutpatientSlipPage` | ③～⑪ + `tcmOnly`/`westernOnly` | 按中药标识过滤后挂载 **`inventoryDetailList`** |
| 13 | Asm | `EncounterMedicationDispenseSlipPrintEnricher#enrichOutpatientPrintFields` | 本页 slip + 药嘱索引 | 回填打印字段与 **`encounterMedicationTypeCode/Text`**：`EncounterQueryService#mapMedTypeByEncounterDiagnosisIds` ← 药嘱 `encounter_diagnosis_ids`；同处方号一致；方头 `prescriptionTypeLine*` 锚定 **`rx_type_code`** |


### 3.5 IF-OPP-04 `savePrepareOutpatientMedicationDispense`（保存配药）

**HTTP**：`POST /api/app/outpatientPharmacyDispense/savePrepareOutpatientMedicationDispense`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 上下文就诊 |
| `preparerPractitionerId` | Long | Y | 调配药师（**BR-OPP-004**） |
| `lines` | `List<SavePrepareDispenseLineSubmitDto>` | Y | 勾选 **`PREPARATION`** 行，至少一行 |


**DTO 字段表：SavePrepareDispenseLineSubmitDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | Y | 发放单主键 |
| `dispenseLocationId` | Long | Y | 确认后的**发放库房**（须在 ServiceLocationTemplate 候选集中且库存可满足） |
| `lotNumber` | String | N | 确认批号（以实现必填策略为准） |
| `traceNo` | String | N | 追溯码（多码英文逗号拼接见 **DM-MED-002** §4.4） |
| `quantity` | `BigDecimal` | Y | **实发数量**，须与该发放行对应医嘱开立数量一致（**BR-OPP-006/007**） |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否保存成功 |
| `message` | String | 提示文案 |
| `processedDispenseIds` | `List<Long>` | 已处理发放单主键 |


**关键校验**

- **BR-OPP-003～008、013**；前端半扫 **BR-OPP-015**；绑码软硬边界 **BR-OPP-016**。保存配药：有码批本库/在院校验（`assertCodesInNetStock*` / `assertCodesInHospital*`）；**按行**去重与扫多上限；**无**剩余盖住硬 Gate（**BR-OPP-014** 已迁至 IF-OPP-05 写侧软判定）。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-OPP-201 | 追溯码已出库 |
| EX-OPP-202 | 调配药师无效或同人 |
| EX-OPP-203 | 状态不允许保存配药（非 `PREPARATION` 等） |
| EX-OPP-204 | 单位/开立数量/临时医嘱拆分行合计不一致；或 **锚定收费明细 `total_price` 汇总**与 **按 `ChargeItem.unit_price` × `SavePrepareDispenseLineSubmitDto.quantity`（与 `med_medication_dispense.unit_code` / 医生站签发 **`ChargeItem.quantity` 同源业务单位，禁止先换拆零再乘单价）** 重算总额（四舍五入到分）不一致（**BR-OPP-013** / **BR-PRICE-SRC-001**） |
| EX-OPP-205 | 库存不足或库房不在候选集 |


**Mapper 接口方法签名**

- `OutpatientPharmacyDispenseMapper#toPrepareMedicationDispenseCommand(Long operatorId, Long preparerPractitionerId, List<SavePrepareDispenseLineSubmitDto> lines): PrepareMedicationDispenseCommand`
- `OutpatientPharmacyDispenseMapper#toPrepareBatchResponse(List<Long> ids, String message, boolean success): SavePrepareOutpatientMedicationDispenseResponse`


**领域编排序列（严格顺序，与 `OutpatientPharmacyDispenseAppService#savePrepareOutpatientMedicationDispense` 对齐）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `lines` 非空；`preparerPractitionerId` 非空 | 否则 EX-OPP-202 |
| 2 | Gate | `PractitionerQueryService#findById` / 登录用户解析 | 调配药师存在且非停用；**不得**与当前登录 **`Practitioner`** 同人 | **EX-OPP-202** |
| 3 | Gate | — | `lines[].quantity` >0 | 否则 **EX-OPP-204** |
| 4 | Pre | `EncounterQueryService#mustGetEncounter(Long)` | `encounterId` | — |
| 5 | Pre | `MedicationRequestQueryService#listMedicationRequestsByEncounter` + `MedicationDispenseQueryService#listMedicationDispensesByIds` | 提交 `dispenseId` | 构建医嘱映射与发放聚合映射 |
| 6 | Pre | **逐行循环**：发放属于就诊、**门诊药品**、`validateDispensePreparable`（**`PREPARATION`**）；**ServiceLocationTemplate** 候选库房门禁；`loadMedicationRequestById` **单位与发放行 `unit_code` 一致** | — | **BR-OPP-005/006/007** 前置；单位不符 **EX-OPP-204** |
| 7 | Pre | `MedicationDispenseInventoryAvailabilityGate#assertSufficientForMedicationDispenseWithReserve` | 每行：药品+提交库房+批号+业务数量+目录投影；预占**同批**收窄 | **BR-OPP-008** / GAP-EXE-003 → **EX-OPP-205** |
| 7a | Pre | 有码批：`assertCodesInNetStockForDispenseLine`；**按行** `assertNoDuplicateTraceCodes`（仅行内）+ 绑码个数 ≤ `resolveOutboundMaxCodeCount`；汇总后 `assertCodesInHospitalForDispense`（**禁止**整单跨行码去重硬拒，**BR-OPP-016**） | 非空码列表 | 空码跳过；**无** `assertRemainingSplitSufficientForDispenseOut` |
| 8 | Pre | **`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`**（经 **`OutpatientPharmacyDispenseAppService#assertPrepareChargeTotalMatchesInventoryLotPricing`** / **`buildUnitPriceByDispenseIdFromChargeRows`**） | 若就诊下**存在**锚定收费行：`sum(ChargeItem.total_price)` **四舍五入到分**后 **等于** `sum( quantity × ChargeItem.unit_price )`（同 dispense 多行时单价加权），其中 **`quantity` 为提交行实发数量且与发放 `unit_code` 一致**；**禁止**查当前 **`adm_charge_item_definition`** | **BR-OPP-013** / **BR-PRICE-SRC-001**；无收费行时**跳过**；不等 **EX-OPP-204** |
| 9 | Pre | （历史 `TraceNoManageQueryService#assertCodesNotAlreadyOutbound` 已收敛为现行 TraceNumber 读侧去重/在院校验，见 7a） | — | **BR-OPP-003** 语义保留 |
| 10 | Pre | 应用内 **`assertTemporaryTherapyQuantities`**（临时医嘱拆分行合计） | `lines` + 发放映射 | **BR-OPP-006** → **EX-OPP-204** |
| 11 | Asm | `OutpatientPharmacyDispenseMapper#toPrepareMedicationDispenseCommand` | — | 装配 `PrepareMedicationDispenseCommand` |
| 12 | Cmd | `MedicationDispenseDomainService#prepareMedicationDispense` | ⑪ | **写库点**：`PREPARATION`→`IN_PROGRESS`；领域事件以 DM 为准 |
| 13 | Asm | `OutpatientPharmacyDispenseMapper#toPrepareBatchResponse` | — | Response |


### 3.6 IF-OPP-05 `dispenseOutpatientMedications`（发药扣库）

**HTTP**：`POST /api/app/outpatientPharmacyDispense/dispenseOutpatientMedications`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 上下文就诊 |
| `medicareUploadEnabled` | Boolean | N | 事务外进销存上传门控（**BR-OPP-010**；实现侧以 `InventoryUploadExtension#isEnabled` 为准，非 `whale_settings`） |
| `lines` | `List<DispenseMedicationLineSubmitDto>` | Y | 勾选发放行，至少一行 |


**DTO 字段表：DispenseMedicationLineSubmitDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | Y | 发放单主键；**批号/库房/数量以库内已保存快照为准**（AS：按 dis 扣减） |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 本地发药是否成功 |
| `message` | String | 含医保失败摘要时 **EX-OPP-207** |
| `processedDispenseIds` | `List<Long>` | 已处理 ID |


**关键校验**

- **BR-OPP-009～010**；**EX-OPP-203**（非 `IN_PROGRESS`）；**EX-OPP-205**；医保 **EX-OPP-206/207**。写追溯盖不住时软跳过（**BR-OPP-014**），不抛 **`TRACE_NUMBER_*`**。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-OPP-203 | 状态不允许发药 |
| EX-OPP-205 | 库存不足 |
| EX-OPP-206 | 医保参数缺失 |
| EX-OPP-207 | 医保上传部分失败 |


**Mapper 接口方法签名**

- `OutpatientPharmacyDispenseMapper#toDispenseCommand(Long operatorPractitionerId, List<DispenseMedicationLineSubmitDto> lines): DispenseMedicationCommand`
- `OutpatientPharmacyDispenseMapper#toDispenseBatchResponse(...): DispenseOutpatientMedicationsResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `lines` 非空 | — |
| 2 | Pre | `PractitionerQueryService#findByUserId` | 当前用户 | 解析发药人 `practitionerId` |
| 3 | Pre | `EncounterQueryService#mustGetEncounter` | `encounterId` | — |
| 4 | Pre | `MedicationDispenseQueryService#mustGetMedicationDispense` | 每 ID | — |
| 5 | Pre | `MedicationDispenseQueryService#validateDispensable(Long)` | 每 ID | **`IN_PROGRESS`**（对齐 **DM-MED-002** 待发药窗口） |
| 6 | Pre | `MedicationDispenseInventoryAvailabilityGate#assertSufficientForMedicationDispenseWithReserve` | 从聚合读出库房+批号+数量+单位+目录投影；预占与物理**同批** | **发药前再断言**（**BR-OPP-009** / GAP-EXE-003） |
| 7 | Asm | `OutpatientPharmacyDispenseMapper#toDispenseCommand` | 发药人、`lines` | 毒麻双人 **BR-MDP-013** |
| 8 | Cmd | `MedicationDispenseDomainService#dispenseMedication(DispenseMedicationCommand)` | ⑦ | **`IN_PROGRESS`→`COMPLETED`**；发布领域事件 |
| 9 | Cmd | **`InventoryItemDomainService#allocateAndDecrease`** | ⑧ 成功后同一事务内执行；数量=最小单位 | **实物账面扣减**（**BR-OPP-009**）；有批号同批 FEFO 拆分扣，无批号跨批 FEFO |
| 10 | Cmd | **`TraceNumberDispenseTraceOrchestrator#recordDispenseOutLines`** | ⑨ 后同一事务；有码批且非空 `trace_no` | **BR-OPP-014**：盖住写 OUT，盖不住软跳过；空码/无码批跳过 |
| 11 | Asm | `OutpatientPharmacyDispenseMapper#toDispenseBatchResponse` | — | Response |

**事务提交后**：若进销存扩展启用（`InventoryUploadExtension#isEnabled`），调用进销存适配；失败合并 **EX-OPP-206/207**（**BR-OPP-010**）。


### 3.7 IF-OPP-06 `cancelOutpatientMedicationDispense`（作废）

**HTTP**：`POST /api/app/outpatientPharmacyDispense/cancelOutpatientMedicationDispense`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 上下文 |
| `lines` | `List<CancelMedicationDispenseLineSubmitDto>` | Y | 至少一行 |


**DTO 字段表：CancelMedicationDispenseLineSubmitDto**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | Y | 发放单 |
| `notPerformedReasonEnum` | String | Y | 未发药/作废原因（存 **`NotPerformedReason` code**，映射 **`CancelMedicationDispenseCommand.Item#notPerformedReasonEnum`**） |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | — |
| `message` | String | — |
| `processedDispenseIds` | `List<Long>` | — |


**关键校验**

- **BR-OPP-011**；状态 **EX-OPP-208**。

**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-OPP-208 | 不可作废或原因缺失 |


**Mapper 接口方法签名**

- `OutpatientPharmacyDispenseMapper#toCancelMedicationDispenseCommand(Long operatorId, List<CancelMedicationDispenseLineSubmitDto> lines): CancelMedicationDispenseCommand`
- `OutpatientPharmacyDispenseMapper#toCancelBatchResponse(...): CancelOutpatientMedicationDispenseResponse`


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | 每行 `notPerformedReasonEnum` 非空 | **EX-OPP-208** |
| 2 | Pre | `MedicationDispenseQueryService#mustGetMedicationDispense` | — | 状态 ∈ `PREPARATION`∪`IN_PROGRESS` |
| 3 | Asm | `OutpatientPharmacyDispenseMapper#toCancelMedicationDispenseCommand` | — | — |
| 4 | Cmd | `MedicationDispenseDomainService#cancelMedicationDispense(CancelMedicationDispenseCommand)` | — | **`DECLINED`** + `notPerformedReasonEnum`；`MedicationDispenseDeclinedEvent` |
| 5 | Asm | `OutpatientPharmacyDispenseMapper#toCancelBatchResponse` | — | — |


## 4. QueryService 与事件刷新

- **MedicationDispenseSlip**：订阅 **`DM-MED-002` §5.1** 白名单事件（创建/配药/发药/作废/退药/汇总/释放等），由 **`MedicationDispenseQueryService（同步读 `med_medication_dispense`）`** 维护 ``med_medication_dispense`（经 `MedicationDispenseQueryService`）`；字段级映射以 **DM-MED-002 §6.2 MedicationDispenseQueryService 发药单分页 §4.2** 为唯一真源，本文不重复。
- **OutpatientEncounterContextQueryModel**：IF-OPP-02 仅消费 **§3** 查询接口回补就诊维度展示；发药状态统计若未投影聚合，则以 **目标态** 写模型查询补充（§8）。
- **一致性**：IF-OPP-04/05/06 写路径成功后，发药单QueryService **异步最终一致**；工作站应在写成功后 **刷新** 列表。


## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 序号对照（写接口）


| IF | App 方法 | Cmd 序号（§3） | 调用 | 聚合写入顺序（语义） |
| --- | --- | --- | --- | --- |
| IF-OPP-04 | `savePrepareOutpatientMedicationDispense` | 3.5-⑫ | `MedicationDispenseDomainService#prepareMedicationDispense` | `MedicationDispense` UPDATE：`PREPARATION`→`IN_PROGRESS`，写入库房/批号/数量/追溯/调配信息；**Pre** 含 **BR-OPP-013**（§3.5-⑧） |
| IF-OPP-05 | `dispenseOutpatientMedications` | 3.6-⑧ | `MedicationDispenseDomainService#dispenseMedication` | `MedicationDispense` UPDATE：`IN_PROGRESS`→`COMPLETED`，发药人/时间 |
| IF-OPP-05 | `dispenseOutpatientMedications` | 3.6-⑨ | `InventoryItemDomainService#allocateAndDecrease`（有批号带 `lotNumber`） | `wkf_inventory_item.quantity` **最小单位**扣减（与 §3.6 序列一致） |
| IF-OPP-05 | `dispenseOutpatientMedications` | 3.6-⑩ | `TraceNumberDispenseTraceOrchestrator#recordDispenseOutLines` | 有码批写追溯 OUT（写前可再断言剩余）；空码/无码批跳过 |
| IF-OPP-06 | `cancelOutpatientMedicationDispense` | 3.7-④ | `MedicationDispenseDomainService#cancelMedicationDispense` | `MedicationDispense` UPDATE：`DECLINED` + `not_performed_reason_enum` |


### 5.2 事务边界 / 失败回滚


| 接口 | 事务边界 | 失败回滚 |
| --- | --- | --- |
| IF-OPP-04 | **单事务** 覆盖 §3.5 序号 12 Cmd | 失败全局回滚 |
| IF-OPP-05 | **单事务** 覆盖 §3.6 序号 **8～10** Cmd（发放事实 + 库存扣减 + 写追溯） | 失败全局回滚；**医保上传在事务外** |
| IF-OPP-06 | **单事务** 覆盖 §3.7 序号 4 Cmd | 失败全局回滚 |
| IF-OPP-01～03 | 无写库 Cmd | — |


## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 同一发放行并发保存配药/发药 | 依赖 DB 乐观/version 或唯一业务约束（与仓库现行 `@Version` 策略对齐） | `MedicationDispense` / `InventoryItem` 实体与仓储 | §3.5-⑫ / §3.6-⑧～⑩ |
| 医保上传 | 实现期幂等键（就诊+发放单+商品）由集成层保证 | **目标态** 医保适配模块 | IF-OPP-05 事务提交后协作 |


### 6.2 并发控制


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 队列刷新 | 只读查询无锁；写后读依赖前端刷新 | `OutpatientPharmacyDispenseAppService` | §3.3～3.7 |


### 6.3 重试与容错


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 医保上传失败 | 捕获异常，合并 **EX-OPP-207** 文案；不重试自动冲正本地库存 | 应用服务事务后协作块 | IF-OPP-05 事务提交后协作 |


### 6.4 数据脱敏


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 患者证件等 | 队列/明细仅返回业务必需字段；敏感字段遵循全院日志脱敏规范 | Mapper 装配层 | §3.3、3.4 |


### 6.5 审计日志


| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 配药/发药/作废痕迹 | 领域事件 + 聚合字段 `preparer_id`/`practitioner_id`/时间/作废原因 | `MedicationDispenseDomainService` | §3.5～3.7 Cmd |


## 7. 测试要点

### 7.1 功能测试


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| IF-OPP-01 下拉装配完整 | 集成 | P0 |
| IF-OPP-02 关键字+已发/待发筛选；队列按 `created_at` 排序 | 集成 | P0 |
| IF-OPP-02 管辖库位 empty 时不拼 location 谓词；管辖非空时 `location_id IN`；未登录 EX-OPP-102 | 集成 | P0 |
| IF-OPP-03 东西药切换 + 患者发药信息字段全集（§3.4 DTO）+ `inventoryDetailList` | 集成 | P0 |
| IF-OPP-04 保存配药（PREPARATION→IN_PROGRESS） | 集成 | P0 |
| IF-OPP-04 **BR-OPP-013**：改批号后 **`ChargeItem.total_price` 汇总** vs **`ChargeItem.unit_price`×提交数量**（业务单位，与签发 **`ChargeItem.quantity`** 同源；**非**定价表/库存价） | 集成 | P0 |
| IF-OPP-05 发药扣库（IN_PROGRESS→COMPLETED） | 集成 | P0 |
| IF-OPP-06 作废（`cancelMedicationDispense`）→DECLINED | 集成 | P0 |
| 调配药师=当前用户拦截 | 单元 | P0 |


### 7.2 非功能测试


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 并发双点同一 `dispenseId` | 集成 | P1 |
| 队列大数据量分页耗时 | 手工/性能 | P1 |


### 7.3 QueryService一致性


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 保存配药/发药/作废事件后 Slip QueryService 字段 | 集成（QueryService） | P0 |


### 7.4 集成测试


| 用例 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 临时医嘱拆分总量校验（IF-OPP-04） | 集成 | P0 |
| 发药前二次库存断言（IF-OPP-05） | 集成 | P0 |
| 进销存扩展开/关合并文案（IF-OPP-05） | 手工 | P1 |


### 7.5 测试缺口跟踪


| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| Q-PHA-001 | 追溯码国家库解析未覆盖 | 随追溯域联调补充 |
| Q-PHA-002 | IF-OPP-04/05 库存断言与 IF-OPP-05 同步扣减已在应用层落地；若后续下沉为库存专用 DM §6 封装方法，可收敛重复叙事 | 随库存 DM 定稿复核 |
| Q-PHA-003 | `validateDispensable` 与 **`IN_PROGRESS`** 待发药窗口须与 **DM-MED-002** **BR-MDP-003** 一致；若现行代码仍收口 **`PREPARED`**，需合并状态机后再跑 IF-OPP-05 | 领域对齐后回归 |


## 8. 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.19 | 2026-10-14 | **IF-OPP-05**：有批号扣库改 **`allocateAndDecrease(lotNumber)`** 同批多供应商 FEFO 拆分扣；废止择首条 `decreaseQuantitiesMerged`；对齐 **DM-WKF-008 v1.0.9** |
| v2.18 | 2026-08-31 | **IF-OPP-03**：`EncounterMedicationDispenseSlipRowDto` 增 **`packageSplitRatioText`**（包装↔拆零展示，如 `1盒/36片`）；`EncounterMedicationDispenseSlipReadAssembler` 经 **`MedicationPackageSplitQuantityDisplayAssembler#buildInventoryPackageSplitRatioText`**；对齐 **AS v1.30** |
| v2.17 | 2026-08-19 | **BR-OPP-017**：库存断言/扣库换算收口为 **操作单位→拆零（五类）**；明确 **禁止** `trace_unit_*` 参与扣库；锚点 `MedicationProductQuantityToSplitUnitConverter`；对齐 **AS v1.29** |
| v2.16 | 2026-08-17 | **IF-OPP-02**：启用可选 `startDate`/`endDate`（就诊 `startTime`）；`PharmacyEncounterQueueLoader` 日期∩关键字 → `resolvedEncounterScope`；废止「bind 后清空日期」；对齐 **AS v1.28** |
| v2.15 | 2026-08-05 | **IF-OPP-04 Pre 7a**：**BR-OPP-016** 按行去重 + 扫多硬上限；禁止整单跨行码去重；对齐 **AS v1.27** / DD v1.6 §3.4 |
| v2.14 | 2026-08-03 | **IF-OPP-03**：`encounterMedicationTypeCode/Text` 改由绑定诊断 **`adm_encounter_diagnosis.med_type`**（`YbMedType`）按处方号回填；方头 `prescriptionTypeLine*` 改锚定药嘱 **`rx_type_code`**；发药单笺面与 **op-rx-print / op-tcm-rx-print**「就诊类型」槽位同源回显；对齐 **AS v1.26** |
| v2.13 | 2026-08-02 | **IF-OPP-03**：发放行增 `remainingQuantity`（发放库房×批号现查账面剩余，最小单位）；`OutpatientEncounterMedicationDispenseSlipPageLoader#enrichRemainingQuantityFromInventory`；与 `inventoryDetailList` 候选明细分列；对齐 **AS v1.25** |
| v2.13 | 2026-08-02 | **IF-OPP-03**：无主档时 `traceUnit*` 回落药典；扫码弹窗去掉处方号，追溯码单位/码倍列收窄 |
| v2.12 | 2026-08-02 | **IF-OPP-03 DTO**：补文档 `packageUnit*`/`splitUnitCode`/`traceUnitCode`/`traceUnitCodeText`/`traceUnitRatio`/`lotHasTraceMaster`；`DispenseTraceUnitSnapshotEnricher` 回填贴码单位术语文案；批量扫码弹窗药品明细展示追溯码单位与码/拆零比 |
| v2.11 | 2026-07-29 | **IF-OPP-04/05**：库存断言改为 Gate + **同批号预占**（纠偏 FIFO 拆批假报不足）；对齐 **AS v1.23** / **SYS-IMP-001 GAP-EXE-003 v1.3** |
| v2.7 | 2026-07-15 | **IF-OPP-02/03**：队列 **`genderEnumText`**；slip **`feeNatureText`**（`ContractExpenseNatureAssembler`）+ 开单医生回落药嘱 `requester_id` |
| v2.6 | 2026-06-11 | **处方号展示/分块收敛**：前端发药页 `mapSlipRowsToPrescriptionBlocks` 与护士站/报表打印目标组装**仅** `prescriptionNo`；废止 `busNo`/`prescriptionGroupId`/`发放-{dispenseId}` 回落；`OutpatientPrescriptionPrintDatasetService#resolveRowRxNo`/`resolveMedicationRxNo` 同步 |
| v2.5 | 2026-06-11 | **IF-OPP-03**：`tcmOnly`/`westernOnly` 在 `EncounterMedicationDispenseSlipReadAssembler` 落地行过滤（废止 v1.3「占位不过滤」） |
| v2.5 | 2026-07-02 | **IF-OPP-05**：追溯写出库 **`location_type_enum`** 改 **`LocationQueryService#requireSupplyRequestTypeForLocationId`**（科室药房→`PHARMACY`）；**IF-OPP-01** 药房下拉口径为药房族（含 **`DEPARTMENT_PHARMACY`**） |
| v2.4 | 2026-06-09 | **IF-OPP-01**：药房 init 改 `CurrentManagedLocationIdsResolver#listManagedActivePharmaciesOrEmpty`；Mapper 入参 `List<Location>`；废止 `getOrganizationTreeForPharmacy`；`pharmacyOrganizationOptions.value`=locationId；对齐 **AS v1.14** / **DM-ADM-003/004** §1.5 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-08 | 初版：对齐 `AS-OP-PHA-001` IF-OPP-01～04；§2.3 标注待发药队列分页、医嘱目录过滤、追溯冲突、库存断言、未发药原因枚举、医保协作等 **目标态待生成**；明确 IF-OPP-04 医保上传为事务外协作（不入 §2.2 白名单）；IF-OPP-03 行级 `therapyEnum`、医嘱发布态 DB 下推、待发队列摘要列为目标态 |
| v1.1 | 2026-05-09 | 对齐 AS v1.2：拆分 **IF-OPP-04 保存配药** / **IF-OPP-05 发药** / **IF-OPP-06 作废**；队列与明细 **`PREPARATION`/`IN_PROGRESS`/`COMPLETED`** + 已发/待发；IF-OPP-03 行级 **ServiceLocationTemplate×库存** 候选列表；**耗材排除**；医保协作迁至 IF-OPP-05；IF-OPP-06 对齐仓库 **`cancelMedicationDispense`/`CancelMedicationDispenseCommand`**；补充 **Q-PHA-003**（`PREPARED` vs `IN_PROGRESS` 对齐） |
| v1.2 | 2026-05-09 | IF-OPP-03：**患者发药信息** `EncounterMedicationDispenseSlipRowDto` 与历史导出范例 **语义校准**（OpenHIS 字段名 + 范例对照列）；**`inventoryDetailList`** + `MedicationDispenseInventoryDetailDto`；补充定价/诊断/**发药人姓名** 目标态装配说明；编排序号 **6a/6b** |
| v1.3 | 2026-05-09 | **文档—代码对齐**：IF-OPP-02/03 在 `dispenseStatusEnumIn` **为空**时默认 **`PREPARATION`+`IN_PROGRESS`+`COMPLETED`**（与 §3.3/§3.4 正文一致）；历史 **`PREPARED`** 未迁待发药态的数据须在请求中**显式传入**该状态码。IF-OPP-03：`tcmOnly`/`westernOnly` **当前不做服务端行过滤**（§3.4 字段表下补充说明）。模板默认发放类别码收口 **`MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE`**（`DEFAULT`）。 |
| v1.4 | 2026-05-09 | **IF-OPP-02**（已废止）：曾用会话机构 + `listPharmaciesByOrganization`；现行见 **v2.3** **`CurrentManagedLocationIdsResolver`** |
| v1.5 | 2026-05-09 | **IF-OPP-05**：补全 **`wkf_inventory_item` 实物扣减**（`dispenseMedication` 成功后同一事务内调用库存域 **`decreaseQuantitiesMerged`/`allocateAndDecrease`**）。**IF-OPP-04/05**：库存断言与扣减数量一律先按 **`med_medication_definition / adm_device_definition / wkf_activity_definition（主表）`** 将发放 **`unit_code` 数量** 换算为 **最小单位（拆零单位）** 再与账面比较或扣减。§2.1、§2.2、§2.3、§3.5、§3.6、§5.1、§5.2、§6.1 同步。 |
| v1.6 | 2026-05-10 | **IF-OPP-02**：机构下**无启用药房**时不再「直接空分页」，改为 **`OutpatientPharmacyPendingEncounterPageQuery#encounterOrganizationId`** + **`OutpatientEncounterContextQueryModel`** EXISTS（`organizationId`）收窄；与 **`location_id IN (药房列表)`** 二选一。**默认发放状态** **`PREPARATION`/`IN_PROGRESS`/`COMPLETED`**，**不含** **`PREPARED`**。§1.2、§2.1、§2.3、§3.3 关键校验与编排序列已修订；**取代** v1.4 变更记录中「机构无药房则空分页」表述。对齐 **`AS-OP-PHA-001` v1.5**。 |
| v1.7 | 2026-05-10 | **IF-OPP-03**：落实 **`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`**、**`LocationQueryService#mapNamesByIds`**、术语 **`CodeSystemQueryService#getOptionListByCodeSystemName`** 预热、**`MedicationDispenseQueryService#listMedicationDispensesByIds`** 回补 **`traceNo`**；**`OutpatientPharmacyDispenseMapper#toSlipPage`** 扩展签名（收费/追溯/展示 Map）。**IF-OPP-04**：**BR-OPP-013** 保存配药前 **ChargeItem** 总价 vs **`wkf_inventory_item.price`**×拆零数量（**`requireInventoryBatchUnitPriceForMedicationLot`**）；**`TraceNoManageQueryService`** 已接线；领域序列与实现对齐（金额门禁在库存断言之后、临时医嘱合计之前）。**EX-OPP-204** 语义扩展。**DTO**：皮试/输液 **Boolean**；补 **`partAttributeText`/`dispenseEnumText`/`therapyEnumText`**。对齐 **`AS-OP-PHA-001` v1.6**。 |
| v1.8 | 2026-05-10 | **IF-OPP-02**：移除 **`encounterOrganizationId`** 及 **OutpatientEncounterContextQueryModel** EXISTS 退路；删除 **`OutpatientPharmacyPendingEncounterPageQuery#encounterOrganizationId`**；队列**仅** **`location_id IN (启用药房列表)`**；无库房时应用层空分页。§1.2、§2.1、§2.3、§3.3 已修订。对齐 **`AS-OP-PHA-001` v1.7**。 |
| v2.10 | 2026-07-25 | **DD v1.2**：**BR-OPP-014** 移出 IF-OPP-04；IF-OPP-05 **`isRemainingSplitSufficientForDispenseOut`** 软跳过写追溯；对齐 **AS v1.22** |
| v2.9 | 2026-07-25 | **纠偏**：**BR-OPP-014** 主挂 **IF-OPP-04** Pre **7a**（`assertRemainingSplitSufficientForDispenseOut`）；IF-OPP-05 写追溯仅 IF-TRC-16 防御性复检；废止 v2.8「仅挂核发」口径；对齐 **AS v1.21** |
| v2.8 | 2026-07-25 | **（已被 v2.9 更正）** 曾误将 **BR-OPP-014** 自 IF-OPP-04 迁至 IF-OPP-05；现行见 v2.9 |
| v2.6 | 2026-07-25 | **BR-OPP-015（UI）**：发药前半扫不够 → 确认弹窗；确认后不足行清空追溯码再提交；对齐 **AS v1.19** / **AS-CUS-TRC-001 BR-TRC-021** |
| v2.5 | 2026-07-08 | **IF-OPP-04**：**BR-OPP-014** 码数/剩余 Gate（后经 v2.8 误迁、**v2.9 纠回**） |

| v2.4 | 2026-06-27 | **BR-OPP-013 / BR-PRICE-SRC-001**：保存配药金额门禁改为 **`ChargeItem.unit_price`** 重算（**禁止**再查 **`adm_charge_item_definition`**）；移除 **`requireLotNumberPriceValueFromChargeDefinition`** |
| v1.9 | 2026-05-15 | **BR-OPP-013 / IF-OPP-04**：计费门禁与 **`wkf_inventory_item.price`×拆零数量** 脱钩；改为 **`adm_charge_item_definition`** 的 **`LOT_NUMBER_PRICE`** + **发放业务单位数量**（与医生站签发 **`ChargeItem`** 同源），由 **`ChargeItemDefinitionQueryService#findLotNumberPriceByInstanceIdAndLot`** 与 **`OutpatientPharmacyDispenseAppService#assertPrepareChargeTotalMatchesInventoryLotPricing`** 实现；**`requireInventoryBatchUnitPriceForMedicationLot`** 自仓库移除，文档同步删除。§2.1、§2.2、§2.3（`package_unit_ratio` 列名）、§3.5 **EX-OPP-204** 与编排序列第 8 步已修订。 |
| v2.2 | 2026-05-24 | **IF-OPP-02**：移除 Request `startTime`/`endTime` 契约；队列**禁止** `status_changed_time` 时间窗；排序 **`max(created_at) DESC`**；对齐 **`AS-OP-PHA-001` v1.11** 与 `MedicationDispenseQueryService` 实现 |
| v2.3 | 2026-05-24 | **IF-OPP-02**：库房收敛改为 **`CurrentManagedLocationIdsResolver`**；管辖 empty 不拼谓词；**禁止** `listPharmaciesByOrganization`；对齐 **`AS-OP-PHA-001` v1.12** |
| v2.1 | 2026-05-18 | **IF-OPP-03**：**生产厂家**改为直取 **`ProductDefinitionDisplayDto#manufacturer`**，删除误用 **`OrganizationQueryService`** 解析厂家；与供应商字段分离 |
| v2.0 | 2026-05-18 | **IF-OPP-03**：开立科室 **`OrganizationQueryService#mapNamesByIds`**；列表术语 **`TerminologyOptionLoader` + `OrderCatalogDisplayAssembler`**（对齐 **AS v1.9**、护士站/医生站 common 组件，**展示语义不变**） |
