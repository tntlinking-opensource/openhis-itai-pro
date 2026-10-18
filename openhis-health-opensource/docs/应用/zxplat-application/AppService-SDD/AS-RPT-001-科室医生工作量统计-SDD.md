# 科室医生工作量统计 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-001-SDD  
> **版本**：v1.9 | **日期**：2026-10-06  
> **编排权威**：`AS-RPT-001-科室医生工作量统计.md`（**v1.14**）  
> **领域权威**：`DM-ADM-011`、`DM-FIN-002`、`DM-ADM-002`、`DM-ADM-004`、`DM-ADM-010`  
> **说明**：本 SDD **不维护 Gap 表**；领域读能力见 AS §3（**GAP-RPT-WKL-001～003 已关闭**）；**应用层** `report/practitionerworkload/**` **已落地**（2026-07-11 明细链路粒度与净收入展示移除）。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 科室/医生工作量统计（页初始化、汇总查询、明细分页、导出明细） |
| **边界** | **只读**报表；双 Tab（汇总默认 / 明细）；**导出仅明细**；**禁止** `departmentBlocks[]` 分块；**不**接报表中心通用引擎；**不**做数据权限 Gate |
| **编排权威** | `AS-RPT-001` §2 / §4 |
| **方法权威** | `DM-ADM-011` §6.2.27～31；`DM-ADM-002` §6.2.30～31；`DM-FIN-002` §6.2.19；`DM-ADM-010` §6.2.16 |
| **严格对齐** | IF **IF-RPT-WKL-01～05** |
| **实现阶段口径** | **领域读 + module-support + 应用层均已落地**（2026-07-06 增补 **`summarizePractitionerWorkloadOverview`** 与 scope 限定成本/fin_type enrich） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/practitionerWorkload/{methodName}` |
| **分页** | `pageNo` **1-based**；出参 `content` / `totalCount`（对齐 zxplat-framework `PagedResult`） |
| **operatorId** | `CurrentOperatorResolver#requireOperatorId`（Gate；**不**做个人数据权限收窄） |
| **前端 HTTP 客户端** | 须使用 `publicRequestClient`（`responseReturn: 'data'`）；**禁止** `requestClient` 未解包 `data` |
| **时间** | Request `dateFrom`/`dateTo` 为 `yyyy-MM-dd`；Support 转 `billDateFrom`/`billDateTo` 闭区间 `[startOfDay, endOfDay]` 写入 `PractitionerWorkloadPageQuery` |
| **encounterScope** | `OUTPATIENT` / `INPATIENT`；**查询 IF 必填**；初始化默认 `OUTPATIENT` |
| **科室** | `organizationId` **可选**；未选 = `resolvedOrganizationIds` 为该域全部 ACTIVE 科室；汇总 **跨 org 合并**（**BR-RPT-WKL-016**） |
| **金额刻度** | 域计算 **6 位 HALF_UP**（`ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE`）；展示 **2 位**（Assembler `setScale(2, HALF_UP)`） |
| **退单符号** | payment `refundPayment=true` 或 `status=REFUNDED` 时，金额列 × **−1**（**BR-RPT-WKL-011**） |
| **汇总实收分摊** | 仅 **IF-02 / fin_type 行 / summaryTotals**：对齐 **AS-OP-FIN-005** §1.3.1，按 chargeItem 在 payment 快照内 `\|total_price\|` 占比分摊 **`tendered − returned`** |
| **明细实收** | **IF-03/04 逐链路行**：带符号 **`total_price`**（**BR-RPT-WKL-008a** / **BR-OP-FDT-012**）；**不做** tendered 分摊 |
| **fin_type** | 快照优先；回落 **仅锚 `definition_id` 历史定价行**；挂号强制 **1012**（**BR-RPT-WKL-007** / **BR-OP-DCL-010**） |
| **事务** | 本模块**只读**；AppService **禁止**写事务；可选 `@Transactional(readOnly=true)` |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **RPT-WKL-SDD-R-001** | IF-02 响应 **禁止** `departmentBlocks[]`；须为 **`finTypeRows[]` + `registrationRows[]` + `summaryTotals`**（**BR-RPT-WKL-016**） |
| **RPT-WKL-SDD-R-002** | 领域层 fin_type / 号源读模型保留 **org 粒度**；**应用层**按 `fin_type_code` / `serviceTypeId` **跨 org SUM** 后输出 |
| **RPT-WKL-SDD-R-003** | 点「查询」前端 **并行** IF-02 + IF-03（page=1）；明细翻页 **仅 IF-03**（**BR-RPT-WKL-015**） |
| **RPT-WKL-SDD-R-004** | 导出 **仅明细 sheet**；与 IF-03 **同筛选、同口径、同排序**（**BR-RPT-WKL-013**） |
| **RPT-WKL-SDD-R-005** | 导出行上限 **`PractitionerWorkloadConstants.EXPORT_MAX_ROWS`** = **100000**；分页拉取步长 **`EXPORT_PAGE_SIZE=500`** |
| **RPT-WKL-SDD-R-006** | 无明细导出：**拒绝策略** → **EX-RPT-WKL-201**（与 AS §5 一致） |
| **RPT-WKL-SDD-R-007** | 明细/导出 **必含开单科室列**（**BR-RPT-WKL-017**） |
| **RPT-WKL-SDD-R-008** | 未知 `service_table` → **整单失败 EX-RPT-WKL-103**（**BR-RPT-WKL-006**） |
| **RPT-WKL-SDD-R-009** | 明细 **一行 = `chargeItemId + paymentId`** 链路；**禁止** request 键合并；**禁止**过滤 0/0 行（**BR-RPT-WKL-008**） |
| **RPT-WKL-SDD-R-010** | **净收入不展示**；`netIncomeAmount*` DTO 字段 **保留但不填充**；导出列 **不含净收入**（**BR-RPT-WKL-020**） |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/report/practitionerworkload/
  service/
    PractitionerWorkloadAppService.java
  request/
    LoadPractitionerWorkloadPageInitRequest.java
    QueryPractitionerWorkloadSummaryRequest.java
    PagePractitionerWorkloadDetailRequest.java
    ExportPractitionerWorkloadDetailRequest.java
  response/
    LoadPractitionerWorkloadPageInitResponse.java
    QueryPractitionerWorkloadSummaryResponse.java
    PagePractitionerWorkloadDetailResponse.java
    ExportPractitionerWorkloadDetailResponse.java
  dto/
    PractitionerWorkloadFinTypeRowDto.java
    PractitionerWorkloadRegistrationRowDto.java
    PractitionerWorkloadSummaryTotalsDto.java
    PractitionerWorkloadDetailLineDto.java
    PractitionerWorkloadExportRowDto.java
  assembler/
    PractitionerWorkloadAssembler.java
  mapper/
    PractitionerWorkloadMapper.java
    PractitionerWorkloadMapperImpl.java
  support/
    PractitionerWorkloadQuerySupport.java                    # 已落地（S-R 自 module-support 迁入）
    PractitionerWorkloadFinTypeEnrichmentSupport.java        # 目标态：definition→fin_type_code
    PractitionerWorkloadCostEnrichmentSupport.java             # 药耗批号进价；诊疗/挂号账单价

zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/charge/support/
  DailySettlementFinTypeAmountSupport.java                   # 已落地；fin_type 解析精神参照

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/administration/chargeitem/query/
  PractitionerWorkloadPageQuery.java                       # 已落地
  PractitionerWorkloadFinTypeOrgSummaryQueryModel.java      # 已落地
  PractitionerWorkloadRegistrationOrgSummaryQueryModel.java  # 已落地
  PractitionerWorkloadRequestDetailQueryModel.java           # 已落地
  PractitionerWorkloadOverviewSummaryQueryModel.java         # 已落地（2026-07-06）

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  PractitionerWorkloadConstants.java                         # 已落地；扩展 EX_* / EXPORT_MAX_ROWS
```

> 包名：`com.zxplat.domain.application.report.practitionerworkload`  
> `OptionDto` 使用 zxplat-framework 公共库。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | IF-01～04 Gate |
| `OrganizationQueryService` | IF-01 科室下拉；IF-02～04 Organization 展示名 |
| `PractitionerQueryService` | IF-01 医生下拉；IF-03/04 开单医生姓名 |
| `HealthcareServiceQueryService` | IF-02 号源类型名 `loadHealthcareServiceMapByIds` |
| `ChargeItemQueryService` | IF-02/03/04：`listPractitionerWorkloadScopeChargeRows`、`summarizePractitionerWorkloadByFinType`、`summarizeRegistrationChargesByServiceType`、`summarizePractitionerWorkloadOverview`、`pagePractitionerWorkloadDetailByRequest` |
| `PaymentReconciliationQueryService` | SupClient 内：`listPractitionerWorkloadChargeItemIdsByPaymentFilter` |
| `EncounterQueryService` | SupClient 内：`listEncounterIdsForPractitionerWorkloadScope`、`mapPractitionerWorkloadEncounterContextByIds`、`filterEncounterIdsByActiveRegistrationDoctor` |
| `ChargeItemDefinitionQueryService` | fin_type 历史定价行；成本价锚点 |
| `ServiceRequestQueryService` | IF-03/04：`mapListModelsByIds` |
| `MedicationRequestQueryService` | IF-03/04：`listMedicationRequestListModelsByIds` |
| `DeviceRequestQueryService` | IF-03/04：`mapListModelsByIds` |
| `ProductDefinitionBatchLoader` | IF-03/04 目录名称/规格 |
| `TerminologyOptionLoader` | IF-02 fin_type 字典展示名 |
| `PractitionerWorkloadQuerysupport | IF-02～04：`preparePractitionerWorkloadQueryScopes` |
| `PractitionerWorkloadFinTypeEnrichmentsupport | IF-02：`resolveFinTypeCodeByDefinitionId(query, definitionIds, scopedChargeItemIds)`（**BR-RPT-WKL-007/018**） |
| `PractitionerWorkloadCostEnrichmentsupport | IF-02～04：`computeCostByChargeItemScope(query, scopedChargeItemIds)`（**BR-RPT-WKL-010/018**） |
| `PractitionerWorkloadMapper` | Request ↔ Query / Response 装配 |
| `PractitionerWorkloadAssembler` | 跨 org 合并、汇总实收分摊、明细链路金额装配、summaryTotals |
| `AnnotatedExcelExporter` | IF-04 xlsx 导出 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊范围 | `QueryPractitionerWorkloadSummaryRequest.encounterScope` | **AS-RPT-001** **BR-RPT-WKL-001/003** | `OUTPATIENT`/`INPATIENT`；常量 **`PractitionerWorkloadConstants.ENCOUNTER_SCOPE_*`** |
| 就诊类别存储码 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3 `adm_encounter.class_enum` | `AMB`/`IMP`；Support 映射 |
| 科室类别 | `com.zxplat.domain.share.enums.OrganizationClassification` 或 `OrganizationClassification` 等价 | `DM-ADM-004` §3 `class_enum` | 门诊/住院科室与 encounterScope 一致校验（**BR-RPT-WKL-004a**） |
| ChargeItem 状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §5 | 白名单 `SETTLED`/`REFUNDED` |
| Payment 状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §2.3 | `SUCCESS`/`REFUNDED` |
| 挂号上下文 | `PractitionerWorkloadConstants.CHARGE_ITEM_CONTEXT_REGISTER` | `DM-ADM-011` §4.3 `context_enum` | 值 **4**（REGISTER） |
| fin_type 字典 | `PractitionerWorkloadFinTypeRowDto.finTypeCode` | `DM-ADM-010` §3 定价行 `type_code`；**BR-RPT-WKL-007** | 诊察费强制 **`DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE`**（1012） |
| 定价类型 | `com.zxplat.domain.share.enums.PriceType` | `DM-ADM-010` §1.5 | `PURCHASE`/`SALE`/`LOT_NUMBER_COST` |
| 医嘱锚点表 | `PractitionerWorkloadDetailLineDto.serviceTable` | `DM-ADM-011` §3 `service_table`；**BR-RPT-WKL-006** | `wkf_service_request`/`med_medication_request`/`wkf_device_request` |
| 目录类型展示 | `PractitionerWorkloadDetailLineDto.catalogTypeDisplay` | **AS-RPT-001** §1.3 | 诊疗/药品/耗材 |
| 参与者类型 | `com.zxplat.domain.share.enums.ParticipantType` | `DM-ADM-002` §3 EncounterParticipant | `REGISTRATION_DOCTOR`（**BR-RPT-WKL-005**） |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §5 | `CANCELLED` 排除挂号笔数（**BR-RPT-WKL-009**） |
| 业务消息码 | `PractitionerWorkloadConstants.EX_RPT_WKL_*` | **AS-RPT-001** §5 | **101～106、201**（目标态待生成） |
| 导出上限 | `PractitionerWorkloadConstants.EXPORT_MAX_ROWS` | **AS-RPT-001** §7 | **100000**（目标态待生成） |
| 默认分页 | `PractitionerWorkloadConstants.DEFAULT_PAGE_SIZE` | **AS-RPT-001** §1.3 | **20**（已落地） |

### 2.4 领域方法与 Mapper 白名单

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OrganizationQueryService` | `listOrganizationsForOutpatientRegistration` | IF-01 |
| `OrganizationQueryService` | `listOrganizationsForInpatientAdmission` | IF-01 |
| `OrganizationQueryService` | `loadOrganizationByIdForDisplay` | IF-01～04 |
| `OrganizationQueryService` | `loadOrganizationsByIds` | IF-02～04 |
| `PractitionerQueryService` | `pageActivePractitionersForPosition` | IF-01 |
| `PractitionerQueryService` | `loadPractitionersByIds` | IF-03、04 |
| `HealthcareServiceQueryService` | `loadHealthcareServiceMapByIds` | IF-02 |
| `ChargeItemQueryService` | `listPractitionerWorkloadScopeChargeRows` | IF-02～04 |
| `ChargeItemQueryService` | `summarizePractitionerWorkloadByFinType` | IF-02 |
| `ChargeItemQueryService` | `summarizeRegistrationChargesByServiceType` | IF-02 |
| `ChargeItemQueryService` | `summarizePractitionerWorkloadOverview` | IF-02 |
| `ChargeItemQueryService` | `pagePractitionerWorkloadDetailByRequest` | IF-03、04 |
| `PaymentReconciliationQueryService` | `listPractitionerWorkloadChargeItemIdsByPaymentFilter` | IF-02～04（SupClient 内） |
| `EncounterQueryService` | `listEncounterIdsForPractitionerWorkloadScope` | IF-02～04（SupClient 内） |
| `EncounterQueryService` | `mapPractitionerWorkloadEncounterContextByIds` | IF-02～04（SupClient 内） |
| `EncounterQueryService` | `filterEncounterIdsByActiveRegistrationDoctor` | IF-02～04（SupClient 内） |
| `ServiceRequestQueryService` | `mapListModelsByIds` | IF-03、04 |
| `MedicationRequestQueryService` | `listMedicationRequestListModelsByIds` | IF-03、04 |
| `DeviceRequestQueryService` | `mapListModelsByIds` | IF-03、04 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-03、04 |
| `ChargeItemDefinitionQueryService` | fin_type 历史 definition 快照（`findAllDefinitionsByIdIn`） | IF-02～04 |
| `HistoricalChargeItemPurchasePriceSupport` | 行成本：药耗批号进价（同批号回放）；诊疗/挂号账单 `unit_price`（不回放）；禁止 `listHistoricalDefinitionsByInstance` | IF-02～04 |
| `TerminologyOptionLoader` | `loadOptionsByCodeSystem`（fin_type 字典） | IF-02 |

**Support / Assembler**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PractitionerWorkloadQuerysupport | `preparePractitionerWorkloadQueryScopes` | IF-02～04 |
| `PractitionerWorkloadFinTypeEnrichmentsupport | `resolveFinTypeCodeByDefinitionId(query, definitionIds, scopedChargeItemIds)` | IF-02 |
| `PractitionerWorkloadCostEnrichmentsupport | `computeCostByChargeItemScope(query, scopedChargeItemIds)` | IF-02～04 |
| `PractitionerWorkloadAssembler` | `applyOverviewSummary(totals, overview)` | IF-02 |
| `PractitionerWorkloadAssembler` | `mergeFinTypeRowsAcrossOrgs` | IF-02 |
| `PractitionerWorkloadAssembler` | `mergeRegistrationRowsAcrossOrgs` | IF-02 |
| `PractitionerWorkloadAssembler` | `assembleSummaryTotals` | IF-02 |
| `PractitionerWorkloadAssembler` | `assembleDetailLines` | IF-03、04 |
| `PractitionerWorkloadAssembler` | `allocateActualReceivedAmount` | IF-02 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PractitionerWorkloadMapper` | `toPractitionerWorkloadPageQuery` | IF-02～04 |
| `PractitionerWorkloadMapper` | `toLoadPractitionerWorkloadPageInitResponse` | IF-01 |
| `PractitionerWorkloadMapper` | `toQueryPractitionerWorkloadSummaryResponse` | IF-02 |
| `PractitionerWorkloadMapper` | `toPagePractitionerWorkloadDetailResponse` | IF-03 |
| `PractitionerWorkloadMapper` | `toExportRowDto` | IF-04 |
| `PractitionerWorkloadMapper` | `toExportPractitionerWorkloadDetailResponse` | IF-04 |

> **领域层能力核对（非 Gap）**：`ChargeItemQueryService#summarizePractitionerWorkloadByFinType`、`#summarizeRegistrationChargesByServiceType`、`#pagePractitionerWorkloadDetailByRequest`；`PaymentReconciliationQueryService#listPractitionerWorkloadChargeItemIdsByPaymentFilter`；`EncounterQueryService` §6.2.30～31；`PractitionerWorkloadQuerySupport#preparePractitionerWorkloadQueryScopes` **均已落地**（2026-07-03）。

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行；本模块无 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-RPT-WKL-01 | `loadPractitionerWorkloadPageInit` | 工作量统计页初始化 | `POST /api/app/practitionerWorkload/loadPractitionerWorkloadPageInit` |
| IF-RPT-WKL-02 | `queryPractitionerWorkloadSummary` | 工作量汇总查询 | `POST /api/app/practitionerWorkload/queryPractitionerWorkloadSummary` |
| IF-RPT-WKL-03 | `pagePractitionerWorkloadDetail` | 工作量明细分页 | `POST /api/app/practitionerWorkload/pagePractitionerWorkloadDetail` |
| IF-RPT-WKL-04 | `exportPractitionerWorkloadDetail` | 导出工作量明细 | `POST /api/app/practitionerWorkload/exportPractitionerWorkloadDetail` |
| IF-RPT-WKL-05 | `listPractitionerWorkloadDoctorCards` | 医生卡片墙批量 | `POST /api/app/practitionerWorkload/listPractitionerWorkloadDoctorCards` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、日期/encounterScope/科室/分页参数门禁 | 停止，抛 **EX-RPT-WKL-*** |
| **Pre** | 只读：Support scope 预解析、领域 Query、批量 enrichment | 抛业务异常 |
| **Asm** | Mapper/Assembler 装配 Response / Excel | — |

---

### 3.2 IF-RPT-WKL-01 `loadPractitionerWorkloadPageInit`（工作量统计页初始化）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/practitionerWorkload/loadPractitionerWorkloadPageInit` |

**Request `LoadPractitionerWorkloadPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 否 | 默认 **`OUTPATIENT`**；决定科室列表源 |
| `organizationId` | `Long` | 否 | 可选；传入时收窄 **医生下拉** 至该科室有岗医生 |

**Response `LoadPractitionerWorkloadPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterScopeOptions` | `List<OptionDto>` | 门诊/住院单选项 |
| `defaultEncounterScope` | `String` | **`OUTPATIENT`** |
| `organizationOptions` | `List<OptionDto>` | 当前 `encounterScope` 下 ACTIVE 科室（含「全部」占位由前端处理，**不传** sentinel id） |
| `practitionerOptions` | `List<OptionDto>` | 随 `encounterScope` + 可选 `organizationId` 级联；未传科室 = 域内全部有岗医生 |
| `defaultDateFrom` | `String` | 当日 `yyyy-MM-dd` |
| `defaultDateTo` | `String` | 当日 `yyyy-MM-dd` |
| `defaultActiveTab` | `String` | **`SUMMARY`** |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toLoadPractitionerWorkloadPageInitResponse` | `toLoadPractitionerWorkloadPageInitResponse(...): LoadPractitionerWorkloadPageInitResponse` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 登录；失败 **EX-RPT-WKL-101** |
| 2 | Gate | 校验 `encounterScope` 枚举 | 默认 OUTPATIENT | 非法 → **EX-RPT-WKL-102** |
| 3 | Pre | `OrganizationQueryService#listOrganizationsForOutpatientRegistration` 或 `#listOrganizationsForInpatientAdmission` | 按 encounterScope | IF-01 科室下拉 |
| 4 | Pre | `PractitionerQueryService#pageActivePractitionersForPosition` | organizationId 可选 | 医生下拉；position 口径对齐日结/费用明细 |
| 5 | Asm | `PractitionerWorkloadMapper#toLoadPractitionerWorkloadPageInitResponse` | org/practitioner 列表 | 默认日期=当日 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-RPT-WKL-101 | 主数据加载失败 |
| EX-RPT-WKL-102 | encounterScope 非法 |

---

### 3.3 IF-RPT-WKL-02 `queryPractitionerWorkloadSummary`（工作量汇总查询）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/practitionerWorkload/queryPractitionerWorkloadSummary` |

**Request `QueryPractitionerWorkloadSummaryRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 是 | **`OUTPATIENT`** / **`INPATIENT`**（**BR-RPT-WKL-001**） |
| `dateFrom` | `String` | 是 | `yyyy-MM-dd` |
| `dateTo` | `String` | 是 | `yyyy-MM-dd`；`dateFrom ≤ dateTo` |
| `organizationId` | `Long` | 否 | 单选科室；未选=域内全部科室合并 |
| `practitionerId` | `Long` | 否 | 选医生时收窄 Request `requester_id` / REGISTRATION_DOCTOR（**非** ChargeItem.`enterer_id`） |

**Response `QueryPractitionerWorkloadSummaryResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterScope` | `String` | 回显 |
| `organizationId` | `Long` | 已选科室时回显 |
| `organizationName` | `String` | 已选科室时回显 |
| `finTypeRows` | `List<PractitionerWorkloadFinTypeRowDto>` | **跨 org 合并**后的 fin_type 表（**禁止** departmentBlocks） |
| `registrationRows` | `List<PractitionerWorkloadRegistrationRowDto>` | **跨 org 合并**后的号源表 |
| `summaryTotals` | `PractitionerWorkloadSummaryTotalsDto` | 应收/实收/成本三列 + 挂号净笔数合计（**无净收入展示**） |

**DTO `PractitionerWorkloadFinTypeRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `finTypeCode` | `String` | 字典码 |
| `finTypeDisplay` | `String` | 字典展示名；未命中回落 code |
| `receivableAmount` | `BigDecimal` | 应收（展示 2 位） |
| `actualReceivedAmount` | `BigDecimal` | 实收 |
| `costAmount` | `BigDecimal` | 成本 |
| `netIncomeAmount` | `BigDecimal` | **保留字段；不填充、不展示**（**BR-RPT-WKL-020**） |

**DTO `PractitionerWorkloadRegistrationRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `serviceTypeId` | `Long` | 号源类型 id |
| `serviceTypeName` | `String` | HealthcareService 名 |
| `netChargeCount` | `Integer` | 净挂号收费笔数 |

**DTO `PractitionerWorkloadSummaryTotalsDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `receivableAmountTotal` | `BigDecimal` | fin_type 行应收合计 |
| `actualReceivedAmountTotal` | `BigDecimal` | 实收合计 |
| `costAmountTotal` | `BigDecimal` | 成本合计 |
| `netIncomeAmountTotal` | `BigDecimal` | **保留字段；不填充、不展示** |
| `registrationNetCountTotal` | `Integer` | 挂号净人次合计（一就诊一次，退号不计入） |
| `encounterCountTotal` | `Integer` | 接诊人数（distinct encounter，**BR-RPT-WKL-019**） |
| `prescriptionCountTotal` | `Integer` | 处方数量（按 `prescription_no` 去重；同方多行计 1） |
| `activityItemCountTotal` | `Integer` | 诊疗项目总数量（distinct 诊疗 + 耗材 request） |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toPractitionerWorkloadPageQuery` | `toPractitionerWorkloadPageQuery(QueryPractitionerWorkloadSummaryRequest req): PractitionerWorkloadPageQuery` |
| `toQueryPractitionerWorkloadSummaryResponse` | `toQueryPractitionerWorkloadSummaryResponse(...): QueryPractitionerWorkloadSummaryResponse` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | **EX-RPT-WKL-101** |
| 2 | Gate | 校验 encounterScope / dateFrom / dateTo | **BR-RPT-WKL-001** | **EX-RPT-WKL-102** |
| 3 | Asm | `PractitionerWorkloadMapper#toPractitionerWorkloadPageQuery` | SummaryRequest | 映射 Query；**不设分页** |
| 4 | Pre | `PractitionerWorkloadQuerySupport#preparePractitionerWorkloadQueryScopes` | Query | 科室/就诊/payment scope；科室域不一致 → **EX-RPT-WKL-106** |
| 5 | Pre | `ChargeItemQueryService#listPractitionerWorkloadScopeChargeRows` | Query；`registrationOnlyMode=false` | 得 scope 候选行；空 scope 短路空响应 |
| 6 | Pre | 提取 `scopedChargeItemIds` / `definitionIds` | 自步骤 5 | **BR-RPT-WKL-018** |
| 7 | Pre | `PractitionerWorkloadFinTypeEnrichmentSupport#resolveFinTypeCodeByDefinitionId` | query + definitionIds + scopedChargeItemIds | **BR-RPT-WKL-007/018** |
| 8 | Pre | `PractitionerWorkloadCostEnrichmentSupport#computeCostByChargeItemScope` | query + scopedChargeItemIds | **BR-RPT-WKL-010/018** |
| 9 | Pre | `ChargeItemQueryService#summarizePractitionerWorkloadByFinType` | Query | org+definition 粒度应收 |
| 10 | Pre | `ChargeItemQueryService#summarizeRegistrationChargesByServiceType` | Query | org+serviceType 净笔数 |
| 11 | Asm | `PractitionerWorkloadAssembler#mergeFinTypeRowsAcrossOrgs` | 步骤 9～8 | 按 **fin_type_code** 跨 org SUM（**BR-RPT-WKL-016**） |
| 12 | Asm | `PractitionerWorkloadAssembler#allocateActualReceivedAmount` | payment 上下文 | 实收分摊（对齐 OP-FIN-005） |
| 13 | Asm | `PractitionerWorkloadAssembler#mergeRegistrationRowsAcrossOrgs` | 步骤 10 + HealthcareService | 按 **serviceTypeId** 跨 org SUM |
| 14 | Asm | `PractitionerWorkloadAssembler#assembleSummaryTotals` | finTypeRows + registrationRows | 三列金额 + 挂号净笔数 |
| 15 | Pre | `ChargeItemQueryService#summarizePractitionerWorkloadOverview` | scope 候选行 | **BR-RPT-WKL-019** |
| 16 | Asm | `PractitionerWorkloadAssembler#applyOverviewSummary` | summaryTotals + overview | 回填外显三计数 |
| 17 | Pre | `OrganizationQueryService#loadOrganizationByIdForDisplay` | organizationId 已选时 | 回显科室名 |
| 18 | Asm | `PractitionerWorkloadMapper#toQueryPractitionerWorkloadSummaryResponse` | DTO 列表 | 空命中 → 空行 + totals 全 0 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-RPT-WKL-102 | 日期非法或未选 encounterScope |
| EX-RPT-WKL-106 | 科室与门诊/住院域不一致 |
| EX-RPT-WKL-103 | fin_type / 成本锚点无法解析 |
| EX-RPT-WKL-104 | 汇总查询失败 |

---

### 3.4 IF-RPT-WKL-03 `pagePractitionerWorkloadDetail`（工作量明细分页）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/practitionerWorkload/pagePractitionerWorkloadDetail` |

**Request `PagePractitionerWorkloadDetailRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 是 | 同 IF-02 |
| `dateFrom` | `String` | 是 | 同 IF-02 |
| `dateTo` | `String` | 是 | 同 IF-02 |
| `organizationId` | `Long` | 否 | 同 IF-02 |
| `practitionerId` | `Long` | 否 | 同 IF-02 |
| `pageNo` | `Integer` | 是 | **1-based**；默认 **1** |
| `pageSize` | `Integer` | 否 | 默认 **`PractitionerWorkloadConstants.DEFAULT_PAGE_SIZE`**（20） |

**Response `PagePractitionerWorkloadDetailResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<PractitionerWorkloadDetailLineDto>` | 明细行 |
| `totalCount` | `Long` | 总条数 |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `summaryTotals` | `PractitionerWorkloadSummaryTotalsDto` | 可选；与 IF-02 同口径 digest（前端页脚） |

**DTO `PractitionerWorkloadDetailLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | `Long` | 收费明细主键（链路行键之一） |
| `paymentId` | `Long` | payment 快照主键（链路行键之一） |
| `requestingOrgId` | `Long` | 开单科室 id |
| `requestingOrgName` | `String` | 开单科室名（**BR-RPT-WKL-017**） |
| `serviceTable` | `String` | 医嘱锚点表 |
| `serviceId` | `Long` | 医嘱 id |
| `catalogTypeDisplay` | `String` | 诊疗/药品/耗材 |
| `productName` | `String` | 目录名称 |
| `productCode` | `String` | 编码 |
| `specification` | `String` | 规格 |
| `practitionerId` | `Long` | 开单医生 |
| `practitionerName` | `String` | 开单医生名 |
| `enteredDate` | `String` | 开立时间 `yyyy-MM-dd HH:mm:ss` |
| `receivableAmount` | `BigDecimal` | 应收（退费负号；折后为 0 回落带符号 total_price） |
| `actualReceivedAmount` | `BigDecimal` | 实收 = 本链路带符号 **total_price**（**BR-RPT-WKL-008a**） |
| `costAmount` | `BigDecimal` | 成本（导出列；明细 Tab 默认不展示） |
| `netIncomeAmount` | `BigDecimal` | **保留字段；不填充、不展示** |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toPractitionerWorkloadPageQuery` | `toPractitionerWorkloadPageQuery(PagePractitionerWorkloadDetailRequest req): PractitionerWorkloadPageQuery` |
| `toPagePractitionerWorkloadDetailResponse` | `toPagePractitionerWorkloadDetailResponse(Page<...>, List<PractitionerWorkloadDetailLineDto>): PagePractitionerWorkloadDetailResponse` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | **EX-RPT-WKL-101** |
| 2 | Gate | 同 IF-02 步骤 2 + 分页合法 | pageNo≥1 | **EX-RPT-WKL-102** |
| 3 | Asm | `PractitionerWorkloadMapper#toPractitionerWorkloadPageQuery` | DetailRequest | 写入 pageNo/pageSize |
| 4 | Pre | **复用 IF-02 步骤 4** | Query | Support scope |
| 5 | Pre | `ChargeItemQueryService#pagePractitionerWorkloadDetailByRequest` | Query | **逐 payment–chargeItem 链路行**；应用层回填 Request 开立时间后按 **req_opened_time DESC**（**BR-RPT-WKL-012**） |
| 6 | Pre | 批量 enrich：`OrganizationQueryService#loadOrganizationsByIds` | requestingOrgId | 开单科室名 |
| 7 | Pre | 批量 enrich：`PractitionerQueryService#loadPractitionersByIds` | Request `requester_id`（读模型字段名可仍为 entererId） | 开单医生名 |
| 8 | Pre | 按 `serviceTable` 分流批量读 request + `ProductDefinitionBatchLoader#loadAsMap` | serviceId | **BR-RPT-WKL-006**；未知表 → **EX-RPT-WKL-103** |
| 9 | Pre | `PractitionerWorkloadCostEnrichmentSupport#computeCostByChargeItemScope` | query + scopedChargeItemIds | 行成本（**BR-RPT-WKL-018**） |
| 10 | Asm | `PractitionerWorkloadAssembler#assembleDetailLines` | 域行 + payment 上下文 | 按 **chargeItemId+paymentId** 装配应收/实收/成本（**BR-RPT-WKL-008/008a**） |
| 11 | Asm | `PractitionerWorkloadMapper#toPagePractitionerWorkloadDetailResponse` | line DTOs | 空列表合法 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-RPT-WKL-102 | 参数非法 |
| EX-RPT-WKL-106 | 科室域不一致 |
| EX-RPT-WKL-103 | 未知 service_table / 成本锚点失败 |
| EX-RPT-WKL-104 | 查询失败 |

---

### 3.5 IF-RPT-WKL-04 `exportPractitionerWorkloadDetail`（导出工作量明细）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/practitionerWorkload/exportPractitionerWorkloadDetail` |

**Request `ExportPractitionerWorkloadDetailRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 是 | 同 IF-02 |
| `dateFrom` | `String` | 是 | 同 IF-02 |
| `dateTo` | `String` | 是 | 同 IF-02 |
| `organizationId` | `Long` | 否 | 同 IF-02 |
| `practitionerId` | `Long` | 否 | 同 IF-02 |

**Response `ExportPractitionerWorkloadDetailResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 建议 `科室医生工作量明细_yyyyMMddHHmmss.xlsx` |
| `contentType` | `String` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `body` | `byte[]` | xlsx 字节 |
| `rowCount` | `Integer` | 导出行数 |
| `truncated` | `Boolean` | 是否触达 `EXPORT_MAX_ROWS` |

**DTO `PractitionerWorkloadExportRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestingOrgName` | `String` | @Excel 开单科室 |
| `catalogTypeDisplay` | `String` | @Excel 目录类型 |
| `productName` | `String` | @Excel 项目名称 |
| `productCode` | `String` | @Excel 项目编码 |
| `specification` | `String` | @Excel 规格 |
| `practitionerName` | `String` | @Excel 开单医生 |
| `enteredDate` | `String` | @Excel 开立时间 |
| `receivableAmount` | `BigDecimal` | @Excel 应收 |
| `actualReceivedAmount` | `BigDecimal` | @Excel 实收 |
| `costAmount` | `BigDecimal` | @Excel 成本 |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toExportRowDto` | `toExportRowDto(PractitionerWorkloadDetailLineDto line): PractitionerWorkloadExportRowDto` |
| `toExportPractitionerWorkloadDetailResponse` | `toExportPractitionerWorkloadDetailResponse(byte[] body, int rowCount, boolean truncated): ExportPractitionerWorkloadDetailResponse` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | **EX-RPT-WKL-101** |
| 2 | Gate | 同 IF-02 步骤 2 | — | **EX-RPT-WKL-102** |
| 3 | Asm | `PractitionerWorkloadMapper#toPractitionerWorkloadPageQuery` | ExportRequest | `pageSize=EXPORT_PAGE_SIZE`（500） |
| 4 | Pre | **循环分页**：复用 IF-03 步骤 4～10 | pageNo 递增直至无数据或触顶 | **BR-RPT-WKL-013**；**禁止**汇总 sheet |
| 5 | Gate | 行数 = 0 | — | **EX-RPT-WKL-201**（拒绝策略 **RPT-WKL-SDD-R-006**） |
| 6 | Gate | 行数 `> EXPORT_MAX_ROWS` | — | **EX-RPT-WKL-105** |
| 7 | Asm | `PractitionerWorkloadMapper#toExportRowDto` | 全量 line DTOs | 平铺 |
| 8 | Asm | `AnnotatedExcelExporter#exportToXlsxBytes` | exportRows；sheet 名 **「工作量明细」** | 框架导出 |
| 9 | Asm | `PractitionerWorkloadMapper#toExportPractitionerWorkloadDetailResponse` | body | 返回 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-RPT-WKL-105 | 导出失败或超上限 |
| EX-RPT-WKL-201 | 无明细可导出 |
| EX-RPT-WKL-103 | 组装失败 |

---

## 4. QueryService 与领域事件

### 4.1 ChargeItem 读契约（GAP-RPT-WKL-001/002/003 **已关闭**；**DM-ADM-011** §6.2.27～31）

**`listPractitionerWorkloadScopeChargeRows(PractitionerWorkloadPageQuery query, boolean registrationOnlyMode): List<PractitionerWorkloadChargeRowQueryModel>`**

| 项 | 约定 |
| --- | --- |
| **语义** | payment 预解析 scope 内加载并过滤工作量候选 chargeItem 行；`registrationOnlyMode=true` 为挂号分支 |
| **用途** | IF-02 统一 scope 源；fin_type/成本/overview enrich **须**基于同一批候选行（**BR-RPT-WKL-018**） |

**`summarizePractitionerWorkloadOverview(List<PractitionerWorkloadChargeRowQueryModel> rows): PractitionerWorkloadOverviewSummaryQueryModel`**

| 项 | 约定 |
| --- | --- |
| **语义** | 在已过滤候选行上统计 **distinct encounter**、**仅药品按真源处方号去重的处方数**（调用方先以 `MedicationRequest.prescription_no` 覆盖收费行快照，仍无号再回落 `service_id`）、**distinct 诊疗+耗材 request** |
| **排除** | 挂号 chargeItem 不计入处方/诊疗项目计数 |
| **用途** | IF-02 **`summaryTotals` 外显三指标**（**BR-RPT-WKL-019**） |

**`summarizePractitionerWorkloadByFinType(PractitionerWorkloadPageQuery query): List<PractitionerWorkloadFinTypeOrgSummaryQueryModel>`**

| 项 | 约定 |
| --- | --- |
| **过滤** | 须先经 `PractitionerWorkloadQuerySupport#preparePractitionerWorkloadQueryScopes`；`SETTLED`/`REFUNDED` + payment scope + 科室/就诊/医生收窄（**BR-RPT-WKL-002～005**） |
| **聚合** | 按 **`requesting_org_id` + `definition_id`** SUM 应收/折后/行金额 |
| **返回** | org 粒度；应用层按 fin_type **跨 org 合并**（**BR-RPT-WKL-016**） |

**`summarizeRegistrationChargesByServiceType(PractitionerWorkloadPageQuery query): List<PractitionerWorkloadRegistrationOrgSummaryQueryModel>`**

| 项 | 约定 |
| --- | --- |
| **过滤** | 挂号 chargeItem；同一 `encounter_id` 只计 1 次；退号/待挂/错误态不计入；选医生时 **REGISTRATION_DOCTOR**（**BR-RPT-WKL-005/009**；对齐 **AS-RPT-007**） |
| **聚合** | 按 **`organization_id` + `service_type_id`** 净人次 |
| **返回** | org 粒度；应用层按 serviceType **跨 org 合并** |

**`pagePractitionerWorkloadDetailByRequest(PractitionerWorkloadPageQuery query): Page<PractitionerWorkloadRequestDetailQueryModel>`**

| 项 | 约定 |
| --- | --- |
| **过滤** | 同汇总；**不含**挂号 chargeItem；仅三类 request 表（**BR-RPT-WKL-006**） |
| **粒度** | **一行 = 一条 payment 快照内的 chargeItem**（`chargeItemId` + `paymentId`）；**禁止**按 `service_table + service_id` 合并（**BR-RPT-WKL-008**） |
| **金额** | 退费 **负号**；折后为 0 回落 **带符号 total_price**；**禁止**过滤 0/0 幽灵行 |
| **排序** | Request **`req_opened_time DESC`**（读模型字段可仍名 enteredDate；**BR-RPT-WKL-012**） |
| **分页** | Spring `Page`；pageNo/pageSize 来自 Query |

**`PractitionerWorkloadRequestDetailQueryModel` 关键字段**

| 字段 | 说明 |
| --- | --- |
| `chargeItemId` | 收费明细主键 |
| `paymentId` | payment 快照主键 |
| `serviceTable` / `serviceId` | 医嘱锚点（展示 enrich 用） |
| `requestingOrgId` / `entererId` | 开单科室 / 开单医生（`entererId` 字段名历史保留，值为 Request.`requester_id`，**非** ChargeItem 收费人） |
| `enteredDate` | 开立时间 |
| `billDate` | payment 记账日（符号上下文） |

### 4.2 Payment / Encounter 读契约（**DM-FIN-002** §6.2.19；**DM-ADM-002** §6.2.30～31）

**`PaymentReconciliationQueryService#listPractitionerWorkloadChargeItemIdsByPaymentFilter`**

| 项 | 约定 |
| --- | --- |
| **语义** | `bill_date` 闭区间 + kind（门诊/住院）+ `SUCCESS`/`REFUNDED`；展开 `charge_item_ids` 写入 Query |

**`EncounterQueryService#listEncounterIdsForPractitionerWorkloadScope`**

| 项 | 约定 |
| --- | --- |
| **语义** | `class_enum` + 挂号科室 IN `resolvedOrganizationIds` |

**`EncounterQueryService#mapPractitionerWorkloadEncounterContextByIds`**

| 字段 | 说明 |
| --- | --- |
| `organizationId` | 挂号科室 |
| `serviceTypeId` | 号源 |
| `statusEnum` | 退号过滤 |

### 4.3 事件消费

| 事件 | 读侧刷新 |
| --- | --- |
| `OutpatientPaymentSettledEvent` | 手动查询可见新收费 |
| `OutpatientRefundCompletedEvent` | 负向金额/笔数 |
| `OutpatientRegistrationPaymentSettledEvent` | 挂号 fin_type / 号源笔数 |

> 本模块**不订阅**事件；无 QueryService 投影延迟承诺。

---

## 5. 写库顺序（核心写接口视角）

本模块 **IF-01～04 均为只读**，**无 Cmd 写库点**。

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| IF-RPT-WKL-01 | `loadPractitionerWorkloadPageInit` | — | 只读 |
| IF-RPT-WKL-02 | `queryPractitionerWorkloadSummary` | — | 只读 |
| IF-RPT-WKL-03 | `pagePractitionerWorkloadDetail` | — | 只读 |
| IF-RPT-WKL-04 | `exportPractitionerWorkloadDetail` | — | 只读 |

**事务边界**：无写事务；只读查询默认 Spring 只读连接（可选 `@Transactional(readOnly=true)` 于 AppService）。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| IF | 承诺 |
| --- | --- |
| IF-01～04 | **天然幂等**（只读） |

### 6.2 性能

| 项 | 承诺 |
| --- | --- |
| IF-02 / IF-03 | **独立 SQL**；禁止 App 全表内存过滤 |
| 就诊 scope | `listEncounterIdsForPractitionerWorkloadScope` **须传 bill_date 日窗**；Encounter context **仅 payment 命中就诊** |
| IF-02 chargeItem | payment 快照 **一次** `loadPractitionerWorkloadChargeItemMap`，复用于费用行 / 挂号行 / 实收分母；**禁止**同请求多次 `loadChargeItemsByIds` |
| payment 筛选 | 已有 `resolvedEncounterScope` 时 Specification 叠加 `encounter_id IN`（与 **BR-RPT-WKL-003** 同口径） |
| 内存匹配 | 就诊 / 科室 scope 用 **HashSet**，禁止对每条 payment 链路线性扫列表 |
| 批量读 | Organization / Practitioner / HealthcareService / 三类 Request / Product **IN 批量** |
| 导出 | 步长 **500** 循环分页；上限 **100000** |
| IF-05 卡片墙 | **一次**加载 scope charge 行后按人分桶；挂号医生 `mapActiveRegistrationDoctorByEncounterIds`；**禁止** N×IF-02；卡片仅 `summaryTotals`，fin_type/号源弹窗懒加载 IF-02 |

### 6.3 一致性

| 项 | 承诺 |
| --- | --- |
| 快照 | 查询时刻只读快照 |
| 金额 | 6 位计算、2 位展示 |

### 6.4 权限与审计

| 项 | 承诺 |
| --- | --- |
| 租户 | 仅当前租户 |
| Gate | 登录必选；**无**个人数据权限收窄 |
| 审计 | 只读查询**不**写业务审计表 |

### 6.5 容错

| 场景 | 策略 |
| --- | --- |
| Support 参数非法 | `IllegalArgumentException` → 映射 **EX-RPT-WKL-102/106** |
| fin_type / 成本 / service_table 无法解析 | **整单失败 EX-RPT-WKL-103** |
| 导出无数据 | **EX-RPT-WKL-201** |

---

## 7. 测试要点

| 类别 | 要点 |
| --- | --- |
| **Tab 交互** | 默认汇总；翻页仅 IF-03 |
| **encounterScope** | 必选；默认门诊；切换清空科室/医生 |
| **科室** | 未选=全院合并 finTypeRows/registrationRows；**无** departmentBlocks |
| **已选科室** | 响应含 organizationId/Name；数据范围收窄 |
| **医生** | Request `requester_id` + REGISTRATION_DOCTOR 收窄 |
| **汇总** | fin_type 跨 org SUM；号源跨 org SUM；summaryTotals 与行一致 |
| **明细** | 含开单科室列；**chargeItemId+paymentId 逐链路**；收退各一行；开立时间 DESC；**无净收入** |
| **金额** | 汇总实收 tendered 分摊；明细实收带符号 total_price；退费负号；折后 0 回落 total_price |
| **导出** | 仅明细 sheet；无数据 → 201；超 100000 → 105 |
| **异常** | 科室域不一致 106；缺 fin_type 103 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.9 | 2026-10-06 | 对齐 **AS-RPT-001 v1.14**：**BR-RPT-WKL-021** 优惠列；售价保持 `total_price` 原价 |
| v1.8 | 2026-09-06 | **BR-RPT-WKL-019**：处方数量真源改为药品请求；对齐 **AS-RPT-001 v1.13** |
| v1.7 | 2026-08-06 | 诊疗/挂号成本改账单价（不回放）；对齐 **AS-RPT-001 v1.11** |
| v1.6 | 2026-08-04 | **文档纠偏**：选医生/卡片分桶/开单医生 enrich 改为 Request `requester_id`（非 ChargeItem.`enterer_id`）；对齐 **AS-RPT-001 v1.10** |
| v1.5 | 2026-08-03 | 对齐 **AS-RPT-001 v1.9**：增 IF-05 医生卡片墙批量；弹窗懒加载 fin_type/号源 |
| v1.4 | 2026-08-03 | 对齐 **AS-RPT-001 v1.8**：IF-02 chargeItem 一次加载复用；payment 叠加 encounter scope；HashSet 匹配（口径不变） |
| v1.3 | 2026-08-03 | 对齐 **AS-RPT-001 v1.6**：挂号净人次一就诊一次（**BR-RPT-WKL-009**）；处方数按 `prescription_no` 去重（**BR-RPT-WKL-019**） |
| v1.2 | 2026-07-11 | 对齐 **AS-RPT-001 v1.5**：明细 **payment–chargeItem 链路行**（**RPT-WKL-SDD-R-009**）；明细实收 **BR-OP-FDT-012**；**移除净收入展示/导出**（**RPT-WKL-SDD-R-010**）；`PractitionerWorkloadRequestDetailQueryModel` 增 `chargeItemId`/`paymentId`/`billDate` |
| v1.1 | 2026-07-06 | 对齐 **AS-RPT-001 v1.4**：**`summarizePractitionerWorkloadOverview`**、scope 限定 cost/fin_type enrich、**`summaryTotals` 外显三指标**；应用层标记 **已落地** |
| v1.0 | 2026-07-03 | 首版 SDD；对齐 **AS-RPT-001 v1.3**（汇总全院合并、禁止 departmentBlocks）；领域读侧 GAP-001～003 已关闭；应用层 `report/practitionerworkload/**` 目标态契约 |
