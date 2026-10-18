# 门诊处方查询 · 应用服务详细设计（SDD）

> **编码**：AS-OP-PHA-005-SDD  
> **版本**：v1.12 | **日期**：2026-09-14
> **编排权威**：`AS-OP-PHA-005-门诊处方查询.md`（**v1.8**）
> **领域权威**：`DM-MED-003` §6.2.21、`DM-MED-001`、`DM-MED-002`、`DM-ADM-011`、`DM-FIN-001`、`DM-CLN-002`、`DM-ADM-002`、`DM-CUS-015`  
> **说明**：本 SDD **不维护 Gap 表**；**GAP-OPQ-001 已关闭**；应用层 `outpatient/pharmacy/prescriptionquery/**` 已按本文成码。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊处方查询（初始化、分页查询） |
| **边界** | 纯读；主事实 **MedicationRequest** 已签发行 + 跨聚合批量装配；**不**发药/退药/收费写路径；**不**按药房收窄；**不**小计行；前端方级列表 + 详情抽屉 + 药房同款打印 |
| **编排权威** | `AS-OP-PHA-005` §2 / §4 |
| **方法权威** | `DM-MED-003` §6.2.21；`ChargeItemQueryService#listDistinctServiceIdsByStatusEnumAndServiceTable`；`MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum`；`MedicationDefinitionQueryService#listIdsByInjectFlag` / `#listIdsByCategoryCodesIn`；`ContractQueryService#listIdsByCategoryEnum`；`PrescriptionReviewQueryService#listActivePrescriptionNos` |
| **严格对齐** | IF **IF-OPQ-01～02** |
| **实现阶段口径** | **领域已落地**；应用层 `outpatient/pharmacy/prescriptionquery/**` 已按本 SDD 生成 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **时间轴** | 筛选与排序仅用 `req_opened_time`（空回落 `req_authored_time`）；映射 `OutpatientSignedPrescriptionDrugLinePageQuery.openedDateFrom/To` |
| **场景** | `outpatientAmbOnly=true`（EXISTS `adm_encounter.class_enum=AMB` 或预解析就诊 id） |
| **已签发** | `prescription_no IS NOT NULL AND prescription_no <> ''` **且** `status_enum IN ('ACTIVE','COMPLETED')`（排除仅重分方写了 CP 的 DRAFT） |
| **含输液** | 应用层 `listIdsByInjectFlag(true)` → `resolvedMedicationIdsForInjectFlag`；领域同根解析处方号后按 YES/NO 展开整方 |
| **AMB** | 应用层预写 `resolvedEncounterIds`；领域**禁止** EXISTS Encounter |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionQuery/{methodName}` |
| **事务** | 纯读；`OutpatientPharmacyPrescriptionQueryAppService` **禁止** `@Transactional` |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/pharmacy/prescriptionquery/
  service/
    OutpatientPharmacyPrescriptionQueryAppService.java
  request/
    LoadOutpatientPharmacyPrescriptionQueryPageInitRequest.java
    PageOutpatientPharmacyPrescriptionQueryRequest.java
  response/
    LoadOutpatientPharmacyPrescriptionQueryPageInitResponse.java
    PageOutpatientPharmacyPrescriptionQueryResponse.java
  dto/
    OutpatientPharmacyPrescriptionDrugLineDto.java
  mapper/
    OutpatientPharmacyPrescriptionQueryMapper.java
    OutpatientPharmacyPrescriptionQueryMapperImpl.java
  support/
    OutpatientPharmacyPrescriptionQuerySupport.java
    OutpatientPharmacyPrescriptionQueryReadAssembler.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  OutpatientPharmacyPrescriptionQueryConstants.java   （EX-OPQ 语义码、含输液筛 code/文案、费用性质分类文案）
```

> 包名：`com.zxplat.domain.application.outpatient.pharmacy.prescriptionquery`。  
> `OptionDto` 使用 zxplat-framework 公共库，不在本模块 `dto` 声明。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate 登录门禁 |
| `TerminologyOptionLoader` | IF-OPQ-01 术语 `rx_type_code`（355） |
| `ContractQueryService` | IF-OPQ-01/02 费用性质；批量解析 `category_enum` |
| `MedicationRequestQueryService` | IF-OPQ-02 `pageOutpatientSignedPrescriptionDrugLines`（**GAP-OPQ-001**） |
| `OutpatientPharmacyPrescriptionQuerySupport` | 患者关键字预解析；筛选项枚举装配 |
| `MedicationDefinitionQueryService` | 药品展示、`inject_flag` 批量 |
| `MedicationDispenseQueryService` | 按医嘱批量读发放状态（既有反查或列表方法，见白名单） |
| `ChargeItemQueryService` | `listChargeItemsByServiceIds` |
| `ConditionDefinitionQueryService` | `getConditionDefinitionListByIds` |
| `EncounterContextReader` / `EncounterQueryService` | 门诊号、AMB、患者关键字 |
| `PatientQueryService` | 患者关键字 |
| `PractitionerQueryService` | 开单医生姓名 |
| `OrganizationQueryService` | IF-OPQ-01 科室下拉；IF-OPQ-02 开单科室名 |
| `ProductDefinitionBatchLoader` | 药品展示批量（可选，与 Definition QS 二选一以工程惯例） |
| `OutpatientPharmacyPrescriptionQueryReadAssembler` | 平铺行 + 方级含输液 |
| `OutpatientPharmacyPrescriptionQueryMapper` | Request/Query/Response 装配 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 收费状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §4.1 / §3 `status_enum` | IF-OPQ-01/02；`PageOutpatientPharmacyPrescriptionQueryRequest.chargeStatusEnum` |
| 发药状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2.2 状态机 | IF-OPQ-01/02；`PageOutpatientPharmacyPrescriptionQueryRequest.dispenseStatusEnum` |
| 费用性质分类 | `com.zxplat.domain.share.enums.ContractCategory` | `DM-FIN-001` §4.1 / §3 `category_enum` | IF-OPQ-01/02；`contractCategoryCode` |
| 处方类别 | `PageOutpatientPharmacyPrescriptionQueryRequest.rxTypeCode` | `DM-MED-003` §3 `rx_type_code`；术语 **355** | IF-OPQ-01/02 |
| 注射/输液目录标志 | `MedicationDefinition.injectFlag` / `OutpatientPharmacyPrescriptionDrugLineDto.infusionPrescriptionFlag` | `DM-MED-001` §3 `inject_flag`；**AS** **BR-OPQ-007/013** | 方级含输液 |
| 含输液筛 | `OutpatientPharmacyPrescriptionQueryConstants.INFUSION_FILTER_ALL` 等 | **AS** §1.3；**BR-OPQ-007** | `ALL`/`YES`/`NO` |
| 开方时间 | `PageOutpatientPharmacyPrescriptionQueryRequest.openedDateFrom` / `openedDateTo` | `DM-MED-003` §3 `req_opened_time` | **BR-OPQ-003/004** |
| 处方号 | `OutpatientPharmacyPrescriptionDrugLineDto.prescriptionNo` | `DM-MED-003` §3 `prescription_no`；**BR-MRQ-013** | 正方/退方 |
| 应用语义码 | `OutpatientPharmacyPrescriptionQueryConstants` | **AS** §5 **EX-OPQ-*** | 已落地 |

### 2.4 领域方法与 Mapper 白名单

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `MedicationRequestQueryService` | `pageOutpatientSignedPrescriptionDrugLines` | IF-OPQ-02 |
| `MedicationDefinitionQueryService` | `listIdsByInjectFlag` | IF-OPQ-02（Loader 预解析） |
| `ChargeItemQueryService` | `listDistinctServiceIdsByStatusEnumAndServiceTable` | IF-OPQ-02（Loader） |
| `MedicationDispenseQueryService` | `listDistinctMedicationRequestIdsByStatusEnum` | IF-OPQ-02（Loader） |
| `ContractQueryService` | `listIdsByCategoryEnum` | IF-OPQ-02（Loader） |
| `ContractQueryService` | `listAllContracts` | IF-OPQ-01（分类选项去重） |
| `ContractQueryService` | `findOptionalContractEntityById` / 批量等价 | IF-OPQ-02 费用性质 |
| `ChargeItemQueryService` | `listChargeItemsByServiceIds` | IF-OPQ-02 行装配 |
| `MedicationDispenseQueryService` | `listByMedReqIds` / `listMedicationDispensesByMedReqIds` | IF-OPQ-02 行装配 |
| `MedicationDefinitionQueryService` | `findAllByIds` | IF-OPQ-02 |
| `ConditionDefinitionQueryService` | `getConditionDefinitionListByIds` | IF-OPQ-02 |
| `EncounterQueryService` | `listEncounterIdsByBusNoKeyword`；`listEncounterIdsByPatientIds`；`filterOutpatientAmbEncounterIds`；`listOutpatientAmbEncounterIdsForCashierScope`（无患者关键字时按开方窗写 AMB） | support 患者关键字 / 全院 AMB |
| `PatientQueryService` | `listPatientIdsByEncounterListKeyword` | support 患者关键字 |
| `EncounterContextReader` | `listOutpatientContextsByEncounterIdsOrdered` | IF-OPQ-02 |
| `PractitionerQueryService` | `findAllByIds` | IF-OPQ-02 开单医生 |

**module-support**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientPharmacyPrescriptionQuerySupport` | `buildChargeStatusOptions` | IF-OPQ-01 |
| `OutpatientPharmacyPrescriptionQuerySupport` | `buildDispenseStatusOptions` | IF-OPQ-01 |
| `OutpatientPharmacyPrescriptionQuerySupport` | `buildContractCategoryOptions` | IF-OPQ-01 |
| `OutpatientPharmacyPrescriptionQuerySupport` | `buildInfusionFilterOptions` | IF-OPQ-01 |
| `OutpatientPharmacyPrescriptionQuerySupport` | `enrichPatientKeywordFilters` | IF-OPQ-02 |
| `OutpatientPharmacyPrescriptionQueryReadAssembler` | `assembleRows` | IF-OPQ-02 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientPharmacyPrescriptionQueryMapper` | `toPageQuery` | IF-OPQ-02 |
| `OutpatientPharmacyPrescriptionQueryMapper` | `toLoadPageInitResponse` | IF-OPQ-01 |
| `OutpatientPharmacyPrescriptionQueryMapper` | `toPageResponse` | IF-OPQ-02 |

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行；**Gate/Pre 失败禁止进入后续装配**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-OPQ-01 | `loadOutpatientPharmacyPrescriptionQueryPageInit` | 门诊处方查询页初始化 | `POST /api/app/outpatientPharmacyPrescriptionQuery/loadOutpatientPharmacyPrescriptionQueryPageInit` |
| IF-OPQ-02 | `pageOutpatientPharmacyPrescriptionQuery` | 分页查询门诊处方明细 | `POST /api/app/outpatientPharmacyPrescriptionQuery/pageOutpatientPharmacyPrescriptionQuery` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、时间窗合法 | 抛 EX-OPQ-101/102，停止 |
| **Pre** | 预解析、领域分页读、批量关联读 | 抛 EX-OPQ-103，**禁止 Asm** |
| **Cmd** | — | 本期无写路径 |
| **Asm** | Mapper + ReadAssembler 装配 Response | — |

---

### 3.2 IF-OPQ-01 `loadOutpatientPharmacyPrescriptionQueryPageInit`（门诊处方查询页初始化）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionQuery/loadOutpatientPharmacyPrescriptionQueryPageInit` |

**Request `LoadOutpatientPharmacyPrescriptionQueryPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体或占位 |

**Response `LoadOutpatientPharmacyPrescriptionQueryPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultAuthoredDateFrom` | `String` | 默认开方日起（`yyyy-MM-dd`，本年首日） |
| `defaultAuthoredDateTo` | `String` | 默认开方日止（`yyyy-MM-dd`，本年末日） |
| `chargeStatusOptions` | `List<OptionDto>` | 收费状态 |
| `dispenseStatusOptions` | `List<OptionDto>` | 发药状态 |
| `rxTypeOptions` | `List<OptionDto>` | 术语 355 处方类别 |
| `medTypeOptions` | `List<OptionDto>` | 门诊向 `YbMedType`（医保类别） |
| `contractCategoryOptions` | `List<OptionDto>` | 费用性质三档：自费 / 省医保 / 市医保 |
| `infusionFilterOptions` | `List<OptionDto>` | 全部/是/否 |
| `organizationOptions` | `List<OptionDto>` | 开单科室（门诊启用科室；value=组织主键字符串） |
| `reviewStatusOptions` | `List<OptionDto>` | 点评状态 PENDING/REVIEWED |
| `reasonableOptions` | `List<OptionDto>` | 是否合理（value=`true`/`false` 字符串） |
| `medicationCategoryOptions` | `List<OptionDto>` | 药品类型（术语 `medication_category_code`） |

**关键校验**：无业务强校验；依赖租户上下文。

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OPQ-101 | 元数据加载失败或未登录 |

**Mapper 方法签名**

- `toLoadPageInitResponse(..., organizationOptions, reviewStatusOptions, reasonableOptions, medicationCategoryOptions): LoadOutpatientPharmacyPrescriptionQueryPageInitResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-OPQ-101 |
| 2 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildChargeStatusOptions` | — | 收费状态下拉 |
| 3 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildDispenseStatusOptions` | — | 发药状态下拉 |
| 4 | Pre | `TerminologyOptionLoader#loadOptions` | `rx_type_code` / 355 | 处方类别 |
| 5 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildMedTypeOptions` | 门诊向 `YbMedType` | 医保类别 |
| 6 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildContractCategoryOptions` | 三档 `ContractCategory` | 自费/省医保/市医保 |
| 7 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildInfusionFilterOptions` | — | `ALL`/`YES`/`NO` |
| 8 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildOrganizationOptions` | 门诊启用科室 | 开单科室下拉 |
| 9 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildReviewStatusOptions` | — | 点评状态 |
| 10 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#buildReasonableOptions` | — | 是否合理 |
| 11 | Pre | `TerminologyOptionLoader#loadOptions` | `medication_category_code` | 药品类型（药典） |
| 12 | Asm | `Mapper#toLoadPageInitResponse` | **本年**默认日期 + 步骤 2～11 | 仅读路径 |

---

### 3.3 IF-OPQ-02 `pageOutpatientPharmacyPrescriptionQuery`（分页查询门诊处方明细）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionQuery/pageOutpatientPharmacyPrescriptionQuery` |

**Request `PageOutpatientPharmacyPrescriptionQueryRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `openedDateFrom` | `String` | 是 | 开方时间起（`yyyy-MM-dd`，闭区间日初） |
| `openedDateTo` | `String` | 是 | 开方时间止（`yyyy-MM-dd`，闭区间日末） |
| `chargeStatusEnum` | `String` | 否 | `ChargeItemStatus` code；空=不限 |
| `dispenseStatusEnum` | `String` | 否 | `DispenseStatus` code；空=不限 |
| `infusionFilterCode` | `String` | 否 | `ALL`/`YES`/`NO`；默认 `ALL` |
| `rxTypeCode` | `String` | 否 | 术语 355 code |
| `medTypeCode` | `String` | 否 | `YbMedType` code（医保类别）；空=不限 |
| `contractCategoryCode` | `String` | 否 | `ContractCategory` code（筛仅三档） |
| `patientKeyword` | `String` | 否 | 患者姓名/身份证/门诊号等模糊（与队列同族；身份证用于重名定位） |
| `orderingDepartmentId` | `Long` | 否 | 开单科室组织主键（药嘱 `org_id`）；空=不限 |
| `prescriptionNo` | `String` | 否 | 处方号**精确**匹配（详情整方重载）；空=不限 |
| `reviewStatusEnum` | `String` | 否 | `PENDING`/`REVIEWED`；空=不限（**BR-OPQ-017**） |
| `reasonableFlag` | `Boolean` | 否 | 是否合理；空=不限；与待点评叠用→空结果（**BR-OPQ-018**） |
| `medicationCategoryCode` | `String` | 否 | 药典 `medication_category_code` / 目录 `category_code`；方级（**BR-OPQ-019**） |
| `pageNo` | `Integer` | 是 | 1-based |
| `pageSize` | `Integer` | 是 | 每页条数（按**明细行**分页） |

**Response `PageOutpatientPharmacyPrescriptionQueryResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | `Long` | 明细行总数（框架 {@code PagedResult.totalCount}；与页脚 `prescriptionCount` **分口径**） |
| `prescriptionCount` | `Integer` | 总处方量：`COUNT(DISTINCT prescription_no)`（一号一方） |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `content` | `List<OutpatientPharmacyPrescriptionDrugLineDto>` | 平铺行 |

**DTO `OutpatientPharmacyPrescriptionDrugLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `medicationRequestId` | `Long` | 药品申请主键 |
| `prescriptionNo` | `String` | 存库处方号（`CP…`/`TCP…`） |
| `encounterId` | `Long` | 就诊主键 |
| `encounterBusNo` | `String` | 门诊号 |
| `patientId` | `Long` | 患者主键 |
| `patientName` | `String` | 患者姓名 |
| `idCard` | `String` | 身份证号（**明文**；列表脱敏由前端） |
| `patientPhone` | `String` | 患者联系电话 |
| `orderingPractitionerId` | `Long` | 开单医生主键（`requester_id`） |
| `orderingPractitionerName` | `String` | 开单医生姓名 |
| `orderingDepartmentId` | `Long` | 开单科室组织主键（`org_id`） |
| `orderingDepartmentName` | `String` | 开单科室名称 |
| `conditionDefinitionId` | `Long` | 诊断定义主键 |
| `diagnosisName` | `String` | 诊断名称 |
| `infusionPrescriptionFlag` | `Boolean` | 方级含输液（目录 `inject_flag` ANY） |
| `reqOpenedTime` | `String` | 开方时间（`req_opened_time`；空回落 `req_authored_time`；ISO 或 `yyyy-MM-dd HH:mm:ss`） |
| `reqAuthoredTime` | `String` | 签发时间（`req_authored_time`；**仅系统追溯**；列表默认不展示；可空） |
| `chargeStatusEnum` | `String` | 收费状态 code |
| `chargeStatusText` | `String` | 收费状态文案 |
| `dispenseStatusEnum` | `String` | 发药状态 code |
| `dispenseStatusText` | `String` | 发药状态文案 |
| `rxTypeCode` | `String` | 处方类别 code |
| `rxTypeText` | `String` | 处方类别文案 |
| `medTypeCode` | `String` | 医保类别 code（`YbMedType`；取绑定诊断首位 `med_type`） |
| `medTypeText` | `String` | 医保类别文案 |
| `contractId` | `Long` | 费用性质主键 |
| `contractCategoryCode` | `String` | `ContractCategory` code |
| `contractCategoryText` | `String` | 费用性质分类文案 |
| `medicationId` | `Long` | 药品定义主键 |
| `medicationName` | `String` | 药品名称 |
| `medicationSpec` | `String` | 规格 |
| `manufacturerText` | `String` | 生产厂家展示（目录 **`manufacturer_text`**；空串视为未填） |
| `quantity` | `BigDecimal` | 数量 |
| `unitCode` | `String` | 单位 code |
| `unitText` | `String` | 单位文案 |
| `injectFlag` | `Boolean` | 本行目录是否注射 |
| `sortNumber` | `Integer` | 行序 |
| `groupId` | `Long` | 医嘱分组主键（详情「分组」） |
| `methodCode` / `methodText` | `String` | 用药途径（术语 method_code） |
| `rateCode` / `rateText` | `String` | 用药频次（cus_frequency） |
| `dose` | `BigDecimal` | 单次剂量 |
| `doseUnitCode` / `doseUnitText` | `String` | 剂量单位（术语 dose_unit） |
| `dispensePerDuration` | `Integer` | 用药天数 |
| `chineseHerbsDoseQuantity` | `BigDecimal` | 中药付数（方头 `chinese_herbs_dose_quantity`；与 `quantity` 请求总量分列） |
| `dosageInstructionCode` / `dosageInstructionText` | `String` | 用法说明（术语 dosage_instruction；码落库列 `dosage_instruction`） |
| `skinTestFlag` / `skinTestFlagText` | `Boolean` / `String` | 是否皮试及文案 |
| `unitPrice` | `BigDecimal` | 单价（ChargeItem.unit_price；无费用行空） |
| `chargeableFlag` / `chargeableFlagText` | `Boolean` / `String` | 是否计价（收费明细存在且非 DRAFT） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| 时间窗必填 | `openedDateFrom`/`To` 可解析且 `from ≤ to`，否则 EX-OPQ-102 |
| 分页 | `pageNo≥1`，`pageSize` 落在允许区间（常量） |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OPQ-102 | 时间窗非法 |
| EX-OPQ-103 | 查询/装配失败 |

**Mapper 方法签名**

- `toPageQuery(PageOutpatientPharmacyPrescriptionQueryRequest request, OutpatientPharmacyPrescriptionQuerySupport.PatientScope patientScope): OutpatientSignedPrescriptionDrugLinePageQuery`
- `toPageResponse(PageResult<?> page, List<OutpatientPharmacyPrescriptionDrugLineDto> rows): PageOutpatientPharmacyPrescriptionQueryResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-OPQ-101 |
| 2 | Gate | （应用内）校验开方时间窗 | `openedDateFrom`/`To` | 非法 → EX-OPQ-102；**禁止进入 Pre** |
| 3 | Pre | `OutpatientPharmacyPrescriptionQuerySupport#enrichPatientKeywordFilters` | `patientKeyword` + 开方时间窗 | 关键字非空：门诊号 ∪ 患者列表关键字（姓名/身份证/拼音/五笔）→ AMB 就诊并集；关键字为空：`listOutpatientAmbEncounterIdsForCashierScope(null, start, end)` 写入 AMB `resolvedEncounterIds`（不得为 null） |
| 4 | Pre | `ChargeItemQueryService#listDistinctServiceIdsByStatusEnumAndServiceTable` | 收费状态 + `med_medication_request` | 若〔收费状态非空〕执行；否则跳过，不写 charge resolved |
| 5 | Pre | `MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum` | 发药状态 | 若〔发药状态非空〕执行；否则跳过 |
| 6 | Pre | `MedicationDefinitionQueryService#listIdsByInjectFlag` | `true` | 若〔含输液≠ALL〕执行；写入 `resolvedMedicationIdsForInjectFlag`；否则跳过 |
| 7 | Pre | `ContractQueryService#listIdsByCategoryEnum` | 费用性质分类 | 若〔分类非空〕执行；**双码并查**（`PROVINCIAL_INSURANCE`↔`3` 等）；否则跳过 |
| 8 | Pre | `EncounterQueryService#listEncounterDiagnosisIdsByMedTypeAndEncounterIdIn` + `MedicationRequestQueryService#listIdsReferencingAnyEncounterDiagnosisId` | 医保类别 + AMB 就诊范围 | 若〔`medTypeCode` 非空〕执行；写入 `resolvedMedicationRequestIdsForMedType`；否则跳过 |
| 9 | Asm | `Mapper#toPageQuery` | Request + 步骤 3～8 | 组装 `OutpatientSignedPrescriptionDrugLinePageQuery`（含 start/endDate） |
| 10 | Pre | `MedicationDefinitionQueryService#listIdsByCategoryCodesIn` | 药品类型 code | 若〔`medicationCategoryCode` 非空〕写入 `resolvedMedicationIdsForCategory`；否则跳过 |
| 11 | Pre | `PrescriptionReviewQueryService#listActivePrescriptionNos` + AppService `applyReviewFilterToPageQuery` | 点评状态 / 是否合理 | 写入 `resolvedPrescriptionNosForReview` + `reviewPrescriptionFilterMode`（INCLUDE/EXCLUDE）；否则跳过 |
| 12 | Pre | `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines` | 步骤 9～11 | **BR-OPQ-001～019**；仅读路径主查询 |
| 13 | Pre | `MedicationDefinitionQueryService#findAllByIds` | 页内 `medication_id` 集 | 名称/规格/`inject_flag` |
| 14 | Pre | `ChargeItemQueryService#listChargeItemsByServiceIds` | 页内 request id | 收费状态展示 |
| 15 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByMedReqIds` | 页内 request id | 发药状态展示 |
| 16 | Pre | `ConditionDefinitionQueryService#getConditionDefinitionListByIds` | 页内诊断 id | 诊断名 |
| 17 | Pre | `EncounterQueryService#mapMedTypeByEncounterDiagnosisIds` | 页内就诊诊断 id | 医保类别 |
| 18 | Pre | `ContractQueryService` 批量按 `contract_id` | 页内合同 id | 分类文案 |
| 19 | Pre | `EncounterContextReader#listOutpatientContextsByEncounterIdsOrdered` | 页内 encounter | 门诊号、患者名、电话 |
| 20 | Pre | `PractitionerQueryService#findAllByIds` | 页内 requester | 开单医生名 |
| 21 | Pre | `OrganizationQueryService#mapNamesByIds` | 页内 `org_id` | 开单科室名 |
| 22 | Asm | `OutpatientPharmacyPrescriptionQueryReadAssembler#assembleRows` | 步骤 13～21 | 方级 `infusionPrescriptionFlag`；`medType*` / `patientPhone` / **`idCard` 明文** / `orderingDepartment*`（身份证列表脱敏前端） |
| 23 | Asm | `Mapper#toPageResponse` | 分页元数据 + 步骤 22 | 仅读路径 |

**领域查询对象 `OutpatientSignedPrescriptionDrugLinePageQuery`（已落地）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `startDate` / `endDate` | `LocalDateTime` | 开方时间窗（基类） |
| `rxTypeCode` | `String` | 可选 |
| `infusionFilterCode` | `String` | `ALL`/`YES`/`NO` |
| `resolvedMedicationIdsForInjectFlag` | `List<Long>` | YES/NO 必填 |
| `resolvedMedicationRequestIdsForChargeStatus` | `List<Long>` | 可选；空列表拒查 |
| `resolvedMedicationRequestIdsForDispenseStatus` | `List<Long>` | 可选；空列表拒查 |
| `resolvedMedicationRequestIdsForMedType` | `List<Long>` | 医保类别预解析；可选；空列表拒查 |
| `resolvedContractIdsForCategory` | `List<Long>` | 可选；空列表拒查 |
| `resolvedEncounterIds` | `List<Long>` | AMB/患者；可选；空列表拒查 |
| `prescriptionNo` | `String` | 可选精确匹配 |
| `orderingDepartmentId` | `Long` | 可选；药嘱 `org_id` 等值 |
| `resolvedMedicationIdsForCategory` | `List<Long>` | 药品类型预解析；可选；空列表拒查；方级展开 |
| `resolvedPrescriptionNosForReview` | `List<String>` | 点评态预解析方号；配合 filterMode |
| `reviewPrescriptionFilterMode` | `String` | `INCLUDE`/`EXCLUDE`；空=不筛点评 |
| `pageNo` / `pageSize` | `Integer` | 按明细行条数分页 |

---

## 4. QueryService 与领域事件

| 项 | 说明 |
| --- | --- |
| **主读** | `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines`（**GAP-OPQ-001 已关闭**） |
| **预解析** | Charge / Dispense / Definition（inject + category） / Contract / Encounter / PrescriptionReview 各聚合 QS → `resolved*` |
| **关联读** | 页内批量装配，禁止按行循环仓储 |
| **事件** | 本期无写、无事件刷新 |

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 调用 | 说明 |
| --- | --- | --- | --- | --- |
| — | — | — | — | **本期无 Cmd / 无写库** |

| 项 | 说明 |
| --- | --- |
| **事务边界** | 无；AppService 禁止 `@Transactional` |
| **失败回滚** | 不适用 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 内容 |
| --- | --- |
| **承诺** | 纯读，天然幂等 |
| **代码位置（必须）** | `OutpatientPharmacyPrescriptionQueryAppService` |
| **关联步骤** | §3.2～§3.3 全部 |

### 6.2 并发控制

| 项 | 内容 |
| --- | --- |
| **承诺** | 无写锁；分页读可不重复读保证 |
| **代码位置（必须）** | 领域分页 Query |
| **关联步骤** | §3.3 序号 5 |

### 6.3 重试与容错

| 项 | 内容 |
| --- | --- |
| **承诺** | EX-OPQ-101/103 可前端重试；EX-OPQ-102 不可重试需改条件 |
| **代码位置（必须）** | AppService Gate/异常翻译 |
| **关联步骤** | §3.3 序号 1～2 |

### 6.4 数据脱敏

| 项 | 内容 |
| --- | --- |
| **承诺** | 患者姓名按院内列表惯例展示；无额外脱敏字段 |
| **代码位置（必须）** | ReadAssembler |
| **关联步骤** | §3.3 序号 13 |

### 6.5 审计日志

| 项 | 内容 |
| --- | --- |
| **承诺** | 可选框架操作日志 |
| **代码位置（必须）** | 框架审计切面（若启用） |
| **关联步骤** | §3.2 / §3.3 入口 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| init 返回本年日期与五类筛选项 | API | 应用单测/接口 | P0 |
| 已签发未收费未发药可查 | API | 应用/领域 | P0 |
| 收费筛与发药筛 AND 组合 | API | 应用/领域 | P0 |
| 含输液=YES 返回整方全部行 | API | 领域 | P0 |
| 退方 `TCP…` 入结果 | API | 领域 | P0 |
| 无处方号草稿不入结果；仅有 CP 仍为 DRAFT 亦不入结果 | API | 领域 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 月窗分页 ≤ 2000ms（基线数据量） | 性能 | 手工/压测 | P1 |

### 7.3 查询结果一致性

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 方头收费/发药状态与方内行一致 | API | 应用 | P0 |
| `infusionPrescriptionFlag` 与方内 `injectFlag` ANY 一致 | API | 应用 | P0 |

### 7.4 集成测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 签发→收费→发药链路后状态组合可筛 | 集成 | 联调 | P1 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| **依赖** | 领域已就绪；应用层 Loader/AppService 成码后方可跑通 IF-OPQ-02 自动化 |
| **本期不做** | 导出行校验 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.12 | 2026-09-14 | IF-OPQ-02 明细 DTO 增 `chineseHerbsDoseQuantity`（中药付数）；详情方头展示付数；费用性质筛 `listIdsByCategoryEnum` **双码并查**（契约枚举名 ↔ ChargeTypeCategory 数字码） |
| v1.11 | 2026-09-09 | **BR-OPQ-004**：init 默认开方窗改为本年首末日；对齐 AS v1.7 |
| v1.10 | 2026-08-29 | 筛增点评状态/是否合理/药品类型（药典 category_code）；init 选项；`resolvedMedicationIdsForCategory` / `resolvedPrescriptionNosForReview`；对齐 AS v1.6 |
| v1.9 | 2026-08-25 | **BR-OPQ-010**：关键字明示含身份证；DTO 增 `idCard` 明文回传，列表脱敏前端；分页按明细行条数，`prescriptionCount`=`COUNT DISTINCT prescription_no`（与 `totalCount` 分口径）；对齐 AS v1.5 |
| v1.8 | 2026-08-20 | **BR-MRQ-034**：中药饮片处方类别规范码 `2`；停用术语冗余码 `9`；查询入参归一；Flyway `V12__unify_herbal_rx_type_alias` |
| v1.7 | 2026-08-20 | 交叉 **DM BR-MRQ-033**：精二方 `rx_type_code=8` 后方可被 IF-OPQ-02 处方类别筛命中；历史补数见 health Flyway `V11__backfill_psych_two_rx_type` |
| v1.6 | 2026-08-01 | 开单科室筛/展示（`orderingDepartmentId`/`Name`；药嘱 `org_id`）；init `organizationOptions`；**BR-OPQ-016** |
| v1.5 | 2026-07-23 | 医保类别（`medType*`）筛与展示、电话号；费用性质筛三档；`resolvedMedicationRequestIdsForMedType` |
| v1.4 | 2026-07-21 | 方级列表+详情交互；IF-OPQ-02 增 `prescriptionNo` 与详情用药字段（途径/频次/剂量/天数/单价/皮试/是否计价）；ReadAssembler 注入 `FrequencyOptionLoader` |
| v1.3 | 2026-07-15 | 药品行增补 **`manufacturerText`**（目录 `manufacturer_text`） |
| v1.2 | 2026-07-15 | 应用层成码对齐：分页总数字段改为 `totalCount`；无关键字 AMB 预解析落点 `listOutpatientAmbEncounterIdsForCashierScope` |
| v1.1 | 2026-07-15 | **GAP-OPQ-001 关闭**：对齐领域已落地方法与 B.6.4 预解析编排序列；应用层仍目标态 |
| v1.0 | 2026-07-15 | 初版目标态：IF-OPQ-01/02；对齐 AS-OP-PHA-005 |
| v1.0 | 2026-07-15 | §2.3 代码锚点：`ChargeItemStatus`/`DispenseStatus`/`ContractCategory`/`injectFlag`/`rxTypeCode` 等为目标态或既有 FQCN，标注待生成常量类 |
