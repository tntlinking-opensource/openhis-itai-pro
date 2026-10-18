# 门诊发退药记录 · 应用服务详细设计（SDD）

> **编码**：AS-OP-PHA-004-SDD  
> **版本**：v1.0 | **日期**：2026-06-21  
> **编排权威**：`AS-OP-PHA-004-门诊发退药记录.md`（**v1.0**）  
> **领域权威**：`DM-MED-002-MedicationDispense-药品发放.md`（§6.2.18）、`DM-MED-003`、`DM-MED-001`、`DM-ADM-011`、`DM-ADM-002` §6.2.19  
> **说明**：本 SDD **不维护 Gap 表**；领域层 `pageMedicationDispenseRecords` 已对齐；应用层为目标态全量契约。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊发退药记录（初始化、分页查询、导出） |
| **边界** | 纯读；编排 **MedicationDispense** 记录查询 + 跨聚合批量装配；**不**发药/退药写路径；**不** supersede 排除；**不**小计行 |
| **编排权威** | `AS-OP-PHA-004` §2 / §4 |
| **方法权威** | `DM-MED-002` §6.2.18；`DM-ADM-011` §6.2；`DM-ADM-002` §6.2.19 |
| **严格对齐** | IF **IF-OPR-01～03** |
| **实现阶段口径** | **目标态全量契约**（`outpatient/pharmacy/dispenserecord/**` 待生成；领域 `MedicationDispenseQueryService#pageMedicationDispenseRecords` 已实现） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **时间轴** | 筛选与排序仅用 `dispense_time`；映射 `MedicationDispenseRecordPageQuery.startDate/endDate` |
| **场景** | `outpatientMedicationOnly=true`；正向 `dispense_enum ∈ {OUTPATIENT,TEMPORARY}` |
| **退药场景** | 退药行 `REFUND+REFUNDED`；有患者关键字时 `resolvedEncounterIdsForScene` 写入 AMB 就诊集合；无关键字时领域 **EXISTS** 关联 `adm_encounter.class_enum=AMB`（**DM-MED-002** §6.2.18） |
| **药房下拉** | `LocationQueryService#listAllActivePharmacies`（**非**管辖库房收窄） |
| **价格** | 正向：`listChargeItemsForMedicationDispensePricing`（**dispense_id**）；退药：`PharmacyDispenseRecordChargePricingSupport#supplementRefundPricingFromOriginMedicationRequests`（原药嘱 **service_id**）；金额为负；无 charge 为空 |
| **处方号** | 发药 `CP…`；退药存库 **`TCP…`**（**DM-MED-003** **`markAsRefundFromPrescription`**）；DTO `prescriptionNo` 返回存库值 |
| **前端展示** | 平铺 `content`；`pharmacy-dispense-record-page.vue` + **`dispense-record-group-display.ts`**：默认**按处方**合并（完整 `CP…`/`TCP…` key）；**临床方维度**重排使一发一退**相邻**；退药处方号列展示原 `CP…`（Tooltip `TCP…`）；可切换按就诊/平铺 |
| **HTTP** | `POST /api/app/outpatientPharmacyDispenseRecord/{methodName}` |
| **事务** | 纯读；`OutpatientPharmacyDispenseRecordAppService` **禁止** `@Transactional` |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/pharmacy/dispenserecord/
  service/
    OutpatientPharmacyDispenseRecordAppService.java
  request/
    LoadOutpatientPharmacyDispenseRecordPageInitRequest.java
    PageOutpatientPharmacyDispenseRecordsRequest.java
    ExportOutpatientPharmacyDispenseRecordsRequest.java
  response/
    LoadOutpatientPharmacyDispenseRecordPageInitResponse.java
    PageOutpatientPharmacyDispenseRecordsResponse.java
    ExportOutpatientPharmacyDispenseRecordsResponse.java
  dto/
    PharmacyDispenseRecordRowDto.java
    PharmacyDispenseRecordExportRowDto.java
  mapper/
    OutpatientPharmacyDispenseRecordMapper.java
    OutpatientPharmacyDispenseRecordMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/pharmacy/dispense/support/
  PharmacyDispenseRecordQuerySupport.java
  PharmacyDispenseRecordReadAssembler.java
  PharmacyDispenseRecordChargePricingSupport.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  PharmacyDispenseRecordConstants.java          （已存在：记录类型 code）
  PharmacyDispenseRecordExportConstants.java    （目标态：导出行上限、MIME、文件名）
  OutpatientPharmacyDispenseRecordConstants.java（目标态：EX-OPR 语义码）
```

> 包名：`com.zxplat.domain.application.outpatient.pharmacy.dispenserecord`。  
> `OptionDto` 使用 zxplat-framework 公共库，不在本模块 `dto` 声明。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate 登录门禁 |
| `LocationQueryService` | IF-OPR-01 全部启用药房 |
| `TerminologyOptionLoader` | IF-OPR-01 `medication_category_code` 下拉 |
| `PractitionerQueryService` | IF-OPR-01 发药人下拉；行内发药人姓名 |
| `MedicationDispenseQueryService` | IF-OPR-02/03 记录分页/列表 |
| `PharmacyDispenseRecordQuerysupport | IF-OPR-02/03 跨聚合预解析 |
| `MedicationRequestQueryService` | 批量药嘱、处方号、用法用量 |
| `MedicationDefinitionQueryService` | 药品展示、`listIdsByCategoryCodesIn`、`pageForCatalog`（药品关键字） |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing` |
| `EncounterContextReader` | 门诊就诊上下文（门诊号、患者姓名） |
| `EncounterQueryService` | 患者关键字、`filterOutpatientAmbEncounterIds` |
| `PatientQueryService` | `listPatientIdsByEncounterListKeyword` |
| `OrganizationQueryService` | 开单科室名称 |
| `ProductDefinitionBatchLoader` | IF-OPR-02/03 药品展示批量加载（`loadAsMap`） |
| `PharmacyDispenseRecordReadAssembler` | 平铺行装配（含退药负金额） |
| `AnnotatedExcelExporter` | IF-OPR-03 xlsx 字节流 |
| `OutpatientPharmacyDispenseRecordMapper` | Request/Query/Response 装配 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2.2 状态机 | IF-OPR-02：`COMPLETED` / `REFUNDED` |
| 发放类型 | `com.zxplat.domain.share.enums.MedicationDispenseType` | `DM-MED-002` §3.1 `dispense_enum` | 正向 `OUTPATIENT`/`TEMPORARY`；退药 `REFUND` |
| 记录类型筛 | `PharmacyDispenseRecordConstants.RECORD_TYPE_ALL` 等 | **AS** §1.3；**BR-OPR-004** | init 固定三项 |
| 药品类目 | `PageOutpatientPharmacyDispenseRecordsRequest.medicationCategoryCodes` | `DM-MED-001` §3 `category_code`；术语 `medication_category_code` | 多选；**BR-OPR-006** |
| 记录类型请求 | `PageOutpatientPharmacyDispenseRecordsRequest.recordTypeCode` | **BR-OPR-004** | `ALL`/`DISPENSE`/`REFUND` |
| 应用语义码 | `OutpatientPharmacyDispenseRecordConstants` | **AS** §5 **EX-OPR-*** | 目标态待生成 |
| 导出行上限 | `PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS` | **AS** §7 NFR | 建议 5000；目标态待生成 |

### 2.4 领域方法与 Mapper 白名单

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `MedicationDispenseQueryService` | `pageMedicationDispenseRecords` | IF-OPR-02 |
| `MedicationDispenseQueryService` | `listMedicationDispenseRecords` | IF-OPR-03 |
| `LocationQueryService` | `listAllActivePharmacies` | IF-OPR-01 |
| `MedicationDefinitionQueryService` | `listIdsByCategoryCodesIn` | IF-OPR-02/03（support 预解析） |
| `MedicationDefinitionQueryService` | `pageForCatalog` | IF-OPR-02/03（药品关键字预解析） |
| `MedicationDefinitionQueryService` | `findAllByIds` | IF-OPR-02/03 展示 |
| `MedicationRequestQueryService` | `listMedicationRequestListModelsByIds` | IF-OPR-02/03 |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing`；`listChargeItemsByServiceIds`（退药补价） |
| `PharmacyDispenseRecordChargePricingsupport | `supplementRefundPricingFromOriginMedicationRequests` | IF-OPR-02/03 |
| `EncounterQueryService` | `listEncounterIdsByBusNoKeyword` | support 患者关键字 |
| `EncounterQueryService` | `listEncounterIdsByPatientIds` | support 患者关键字 |
| `EncounterQueryService` | `filterOutpatientAmbEncounterIds` | support 场景/患者收窄 |
| `PatientQueryService` | `listPatientIdsByEncounterListKeyword` | support 患者关键字 |
| `EncounterContextReader` | `listOutpatientContextsByEncounterIdsOrdered` | IF-OPR-02/03 |
| `PractitionerQueryService` | `pageActivePractitionersForPosition` | IF-OPR-01 |
| `OrganizationQueryService` | `mapNamesByIds` | IF-OPR-02/03 开单科室名 |
| `PractitionerQueryService` | `findAllByIds` | IF-OPR-02/03 发药人姓名 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-OPR-02/03 药品展示 |

**module-support**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PharmacyDispenseRecordQuerysupport | `enrichOutpatientRecordFilters` | IF-OPR-02/03 |
| `PharmacyDispenseRecordReadAssembler` | `assembleOutpatientRows` | IF-OPR-02/03 |
| `PharmacyDispenseRecordChargePricingsupport | `supplementRefundPricingFromOriginMedicationRequests` | IF-OPR-02/03 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientPharmacyDispenseRecordMapper` | `toMedicationDispenseRecordPageQuery` | IF-OPR-02/03 |
| `OutpatientPharmacyDispenseRecordMapper` | `toLoadPageInitResponse` | IF-OPR-01 |
| `OutpatientPharmacyDispenseRecordMapper` | `toPageResponse` | IF-OPR-02 |
| `OutpatientPharmacyDispenseRecordMapper` | `toExportRowDto` | IF-OPR-03 |
| `OutpatientPharmacyDispenseRecordMapper` | `toExportResponse` | IF-OPR-03 |
| `OutpatientPharmacyDispenseRecordMapper` | `buildRecordTypeOptions` | IF-OPR-01 |

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行；**Gate/Pre 失败禁止进入后续 Query**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-OPR-01 | `loadOutpatientPharmacyDispenseRecordPageInit` | 门诊发退药记录页初始化 | `POST /api/app/outpatientPharmacyDispenseRecord/loadOutpatientPharmacyDispenseRecordPageInit` |
| IF-OPR-02 | `pageOutpatientPharmacyDispenseRecords` | 分页查询门诊发退药记录 | `POST /api/app/outpatientPharmacyDispenseRecord/pageOutpatientPharmacyDispenseRecords` |
| IF-OPR-03 | `exportOutpatientPharmacyDispenseRecords` | 导出门诊发退药记录 | `POST /api/app/outpatientPharmacyDispenseRecord/exportOutpatientPharmacyDispenseRecords` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、参数、时间窗合法 | 抛 EX-OPR-102/101，停止 |
| **Pre** | 跨聚合预解析、领域分页/列表读 | 抛 EX-OPR-103，**禁止 Asm** |
| **Cmd** | — | 本期无写路径 |
| **Asm** | Mapper + ReadAssembler 装配 Response | — |

---

### 3.2 IF-OPR-01 `loadOutpatientPharmacyDispenseRecordPageInit`（门诊发退药记录页初始化）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyDispenseRecord/loadOutpatientPharmacyDispenseRecordPageInit` |

**Request `LoadOutpatientPharmacyDispenseRecordPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体或占位 |

**Response `LoadOutpatientPharmacyDispenseRecordPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultDispenseDateFrom` | `String` | 默认发药日起（`yyyy-MM-dd`，当日） |
| `defaultDispenseDateTo` | `String` | 默认发药日止（当日） |
| `pharmacyOptions` | `List<OptionDto>` | 租户下全部启用药房 |
| `medicationCategoryOptions` | `List<OptionDto>` | `medication_category_code` 术语 |
| `dispenserOptions` | `List<OptionDto>` | 发药人（药师岗启用主档） |
| `recordTypeOptions` | `List<OptionDto>` | 全部/发药/退药 |

**关键校验**：无业务强校验；依赖租户上下文。

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OPR-101 | 元数据加载失败或未登录 |

**Mapper 方法签名**

- `toLoadPageInitResponse(List<Location> pharmacies, List<OptionDto> categoryOptions, List<OptionDto> dispenserOptions, List<OptionDto> recordTypeOptions, String defaultFrom, String defaultTo): LoadOutpatientPharmacyDispenseRecordPageInitResponse`
- `buildRecordTypeOptions(): List<OptionDto>`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-OPR-101 |
| 2 | Pre | `LocationQueryService#listAllActivePharmacies` | — | **BR-OPR-007**；非管辖收窄 |
| 3 | Pre | `TerminologyOptionLoader#loadOptions` | `MEDICATION_CATEGORY_CODE` | 项目类型下拉 |
| 4 | Pre | `PractitionerQueryService#pageActivePractitionersForPosition` | 药师岗、大页 | 发药人下拉（以实现为准） |
| 5 | Asm | `Mapper#buildRecordTypeOptions` | — | `ALL`/`DISPENSE`/`REFUND` |
| 6 | Asm | `Mapper#toLoadPageInitResponse` | 步骤 2～5 + 当日默认日期 | 仅读路径 |

---

### 3.3 IF-OPR-02 `pageOutpatientPharmacyDispenseRecords`（分页查询门诊发退药记录）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyDispenseRecord/pageOutpatientPharmacyDispenseRecords` |

**Request `PageOutpatientPharmacyDispenseRecordsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseDateFrom` | `String` | 是 | 发药时间起（`yyyy-MM-dd`，闭区间日初） |
| `dispenseDateTo` | `String` | 是 | 发药时间止（`yyyy-MM-dd`，闭区间日末） |
| `recordTypeCode` | `String` | 否 | `ALL`/`DISPENSE`/`REFUND`；默认 `ALL` |
| `pharmacyLocationId` | `Long` | 否 | 药房 `location_id` |
| `medicationCategoryCodes` | `List<String>` | 否 | 项目类型多选 |
| `dispenserPractitionerId` | `Long` | 否 | 发药人 `practitioner_id` |
| `drugKeyword` | `String` | 否 | 药品名称/拼音/编码模糊 |
| `patientKeyword` | `String` | 否 | 患者姓名等模糊；与药品关键字 AND |
| `pageNo` | `Integer` | 是 | 1-based |
| `pageSize` | `Integer` | 是 | 每页条数 |

**Response `PageOutpatientPharmacyDispenseRecordsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 每页条数 |
| `totalCount` | `Long` | 总条数 |
| `content` | `List<PharmacyDispenseRecordRowDto>` | 平铺明细（无小计） |

**DTO `PharmacyDispenseRecordRowDto` 字段表**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | 发放单主键 |
| `recordTypeCode` | `String` | `DISPENSE`/`REFUND` |
| `recordTypeText` | `String` | 记录类型展示 |
| `dispenseTime` | `String` | 发药时间（`yyyy-MM-dd HH:mm:ss`） |
| `pharmacyLocationId` | `Long` | 药房主键 |
| `pharmacyLocationName` | `String` | 药房名称 |
| `dispenserPractitionerId` | `Long` | 发药人主键 |
| `dispenserName` | `String` | 发药人姓名 |
| `patientId` | `Long` | 患者主键 |
| `patientName` | `String` | 患者姓名 |
| `encounterId` | `Long` | 就诊主键（前端分组） |
| `encounterBusNo` | `String` | 门诊号 |
| `prescriptionNo` | `String` | 处方号（存库：发药 `CP…`、退药 `TCP…`） |
| `medicationId` | `Long` | 药品定义主键 |
| `medicationCode` | `String` | 药品编码 |
| `medicationName` | `String` | 药品名称 |
| `medicationSpec` | `String` | 规格展示 |
| `quantity` | `BigDecimal` | 数量（发药正、退药正数绝对值） |
| `unitCode` | `String` | 单位 code |
| `unitText` | `String` | 单位展示 |
| `unitPrice` | `BigDecimal` | 单价；无 charge 为空 |
| `totalAmount` | `BigDecimal` | 金额；发药正、退药**负**；无 charge 为空 |
| `lotNumber` | `String` | 批号 |
| `traceNo` | `String` | 追溯码 |
| `orderingDepartmentId` | `Long` | 开单科室 |
| `orderingDepartmentName` | `String` | 开单科室名称 |
| `usageText` | `String` | 用法用量摘要 |
| `summaryNo` | `String` | 汇总单号（若有） |
| `medReqId` | `Long` | 药嘱主键 |

**关键校验**

- `dispenseDateFrom ≤ dispenseDateTo`（**EX-OPR-102**）
- `pageNo ≥ 1`，`pageSize` 在工程 bounded 范围内

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OPR-102 | 时间窗非法 |
| EX-OPR-103 | 查询/装配失败 |

**Mapper 方法签名**

- `toMedicationDispenseRecordPageQuery(PageOutpatientPharmacyDispenseRecordsRequest request): MedicationDispenseRecordPageQuery`
- `toPageResponse(Page<MedicationDispenseSlipQueryModel> page, List<PharmacyDispenseRecordRowDto> rows): PageOutpatientPharmacyDispenseRecordsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-OPR-103 |
| 2 | Gate | 校验 `dispenseDateFrom/To` | — | 非法 → EX-OPR-102 |
| 3 | Asm | `Mapper#toMedicationDispenseRecordPageQuery` | Request | 映射时间窗至 `startDate/endDate` |
| 4 | Pre | `PharmacyDispenseRecordQuerySupport#enrichOutpatientRecordFilters` | 步骤 3 Query | 写 `outpatientMedicationOnly=true`、药品/患者/类目预解析；有患者关键字时写 `resolvedEncounterIdsForScene`；无关键字时退药场景由领域 EXISTS（AMB）收窄 |
| 5 | Pre | `MedicationDispenseQueryService#pageMedicationDispenseRecords` | 步骤 4 Query | **BR-OPR-001～005**；退药行同窗见 **DM-MED-002** §6.2.18；仅读 |
| 6 | Pre | `MedicationRequestQueryService#listMedicationRequestListModelsByIds` | 页内 `medReqId` | 处方号、用法（**仅负向/正向药嘱自身字段**；**禁止**读侧回落原药嘱） |
| 7 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 页内 `medicationId` + `MED_MEDICATION_DEFINITION` | 药品名/规格/编码 |
| 8 | Pre | `EncounterContextReader#listOutpatientContextsByEncounterIdsOrdered` | 页内 `encounterId` | 门诊号、患者名 |
| 9 | Pre | `ChargeItemQueryService#listChargeItemsForMedicationDispensePricing` | 按就诊分组批量 | **BR-OPR-010**（正向 dispense 锚点） |
| 9b | Pre | `PharmacyDispenseRecordChargePricingSupport#supplementRefundPricingFromOriginMedicationRequests` | 退药行未命中步骤 9 | **BR-OPR-010**（原药嘱 service 锚点） |
| 10 | Pre | `OrganizationQueryService#mapNamesByIds` | 药嘱 `orgId` | 开单科室 |
| 11 | Pre | `PractitionerQueryService#findAllByIds` | 发药人 id | 发药人展示 |
| 12 | Asm | `PharmacyDispenseRecordReadAssembler#assembleOutpatientRows` | 步骤 5～11 | 退药金额取负；处方号/科室/用法**仅**取发放行或当前 `medReqId` 药嘱 |
| 13 | Asm | `Mapper#toPageResponse` | 步骤 5 分页 + 步骤 12 行 | 返回平铺分页 |

---

### 3.4 IF-OPR-03 `exportOutpatientPharmacyDispenseRecords`（导出门诊发退药记录）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyDispenseRecord/exportOutpatientPharmacyDispenseRecords` |

**Request `ExportOutpatientPharmacyDispenseRecordsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| 同 `PageOutpatientPharmacyDispenseRecordsRequest` 筛选字段 | — | — | **不含** `pageNo`/`pageSize`；与 IF-OPR-02 筛选一致 |

**Response `ExportOutpatientPharmacyDispenseRecordsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 默认文件名 |
| `contentType` | `String` | xlsx MIME |
| `body` | `byte[]` | 文件字节 |
| `exportedRowCount` | `Integer` | 导出行数 |
| `truncated` | `Boolean` | 是否触达导出行上限 |

**DTO `PharmacyDispenseRecordExportRowDto` 字段表**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recordTypeText` | `String` | 记录类型 |
| `dispenseTime` | `String` | 发药时间 |
| `pharmacyLocationName` | `String` | 药房 |
| `dispenserName` | `String` | 发药人 |
| `patientName` | `String` | 患者 |
| `encounterBusNo` | `String` | 门诊号 |
| `prescriptionNo` | `String` | 处方号（存库：发药 `CP…`、退药 `TCP…`） |
| `medicationCode` | `String` | 药品编码 |
| `medicationName` | `String` | 药品名称 |
| `medicationSpec` | `String` | 规格 |
| `quantity` | `BigDecimal` | 数量 |
| `unitText` | `String` | 单位 |
| `unitPrice` | `BigDecimal` | 单价 |
| `totalAmount` | `BigDecimal` | 金额 |
| `lotNumber` | `String` | 批号 |
| `traceNo` | `String` | 追溯码 |
| `orderingDepartmentName` | `String` | 开单科室 |
| `usageText` | `String` | 用法用量 |
| `summaryNo` | `String` | 汇总单号 |

**失败码**：EX-OPR-102、EX-OPR-104（导出失败）

**Mapper 方法签名**

- `toMedicationDispenseRecordPageQuery(ExportOutpatientPharmacyDispenseRecordsRequest request): MedicationDispenseRecordPageQuery`
- `toExportRowDto(PharmacyDispenseRecordRowDto row): PharmacyDispenseRecordExportRowDto`
- `toExportResponse(byte[] body, int rowCount, boolean truncated): ExportOutpatientPharmacyDispenseRecordsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1～12 | — | **复用 IF-OPR-02 序号 1～12** | Gate/Pre/Asm 同源；步骤 5 改为 `listMedicationDispenseRecords` 且 `pageSize` 上限 `EXPORT_MAX_ROWS` |
| 13 | Asm | `Mapper#toExportRowDto` | 逐行映射导出 DTO |
| 14 | Asm | `AnnotatedExcelExporter#exportToXlsxBytes` | 工作表名见 `PharmacyDispenseRecordExportConstants` |
| 15 | Asm | `Mapper#toExportResponse` | 零行时仍导出空表（列头齐全） |

---

## 4. QueryService 与领域事件

| QueryService 方法 | 查询语义 | 读侧刷新 |
| --- | --- | --- |
| `pageMedicationDispenseRecords` | `med_medication_dispense`；`dispense_time` 轴；记录类型 OR；无 supersede | 发药/退药写后即时可见（同事务提交后） |
| `listMedicationDispenseRecords` | 与分页同谓词全量列表（带上限） | 同左 |
| `listChargeItemsForMedicationDispensePricing` | 锚点 `dispense_table+dispense_id`；多状态并集读 | charge 写后刷新 |

**本期无领域写操作，不发布、不消费领域事件。**

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 写库 | 说明 |
| --- | --- | --- | --- | --- |
| IF-OPR-01～03 | 全部 | — | **无** | 纯读 |

**事务边界**：无写事务；查询失败仅抛业务异常，无回滚。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 查询/导出 | 纯读，天然幂等 | `OutpatientPharmacyDispenseRecordAppService` | §3.3/3.4 全序列 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 分页一致性 | 不保证跨翻页强一致 | QueryService | §3.3 序号 5 |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 查询失败 | 记录 warn 日志；EX-OPR-103 | AppService | §3.3 序号 5～12 |
| 导出失败 | EX-OPR-104 | AppService | §3.4 序号 14 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 患者姓名 | 列表/导出按门诊队列同族；无额外身份证列 | ReadAssembler | §3.3 序号 12 |

### 6.5 审计日志

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 导出 | 可选记操作审计（框架） | AppService | §3.4 |

---

## 7. 测试要点

### 7.1 功能测试（P0）

| 用例 | 要点 | 自动化 |
| --- | --- | --- |
| 发药行 | `COMPLETED` 正向可见 | 集成 |
| 退药行 | `REFUND+REFUNDED` 可见；与正向同行并存 | 集成 |
| 时间窗 | 按 `dispense_time`；空时间不入窗 | 集成 |
| 记录类型筛 | ALL/DISPENSE/REFUND | 集成 |
| 价格 | 发药正、退药负；无 charge 空 | 集成 |
| 不含住院行 | 无 `INPATIENT` 正向行 | 集成 |

### 7.2 非功能测试（P1）

| 用例 | 要点 |
| --- | --- |
| 分页性能 | 常规日窗 + 单药房 ≤ 2000ms |
| 导出上限 | 超 `EXPORT_MAX_ROWS` 时 `truncated=true` |

### 7.3 查询结果一致性（P0）

| 用例 | 要点 |
| --- | --- |
| 导出 vs 列表 | 同筛选条件行集合一致（上限内） |

### 7.4 集成测试（P1）

| 用例 | 要点 |
| --- | --- |
| init 药房 | 全部启用药房，非管辖收窄 |

### 7.5 测试缺口跟踪

| 缺口 | 优先级 | 说明 |
| --- | --- | --- |
| 大数据量导出压测 | P2 | 5000 行导出耗时 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.4 | 2026-07-11 | §1.2 / IF-OPR-02 步骤 4～5：退药行 AMB 同窗；无患者关键字时领域 EXISTS 收窄（对齐 **DM-MED-002** v1.3.10） |
| v1.3 | 2026-06-26 | **BR-OPR-014**：取消读侧原药嘱批量加载与 `resolveClinicalMedicationRequest` 回落；退药价仍走步骤 9b |
| v1.2 | 2026-06-26 | 退药定价 **`PharmacyDispenseRecordChargePricingsupport**；**BR-OPR-013** 前端一发一退相邻与 `T` 前缀处方展示；对齐 **DM-MED-003** **BR-MRQ-013** |
| v1.0 | 2026-06-21 | 初版：门诊发退药记录目标态 SDD（IF-OPR-01～03）；对齐 `DM-MED-002` §6.2.18 |
| v1.1 | 2026-06-24 | 前端按处方/按就诊/平铺展示切换与组头列合并（`pharmacy-dispense-record-page`） |
