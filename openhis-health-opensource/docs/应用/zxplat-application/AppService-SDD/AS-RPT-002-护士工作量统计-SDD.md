# 护士工作量统计 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-002-SDD · **版本**：v1.8 · **日期**：2026-10-06  
> **对应 AS**：`health/AppService/AS-RPT-002-护士工作量统计.md`（v1.10）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | `zxplat/health-application/report/nurseworkload`（报表只读包，与 `inventory/dailysettlement` 同级直属 `application/`） |
| **边界** | 编排 **IF-RPT-NWL-01～07**；**只读**；消费 **Procedure 净执行** + **DeviceDispense 耗材发放**；**禁止**写库、禁止 App 直查 Repository |
| **编排权威** | `AS-RPT-002-护士工作量统计.md` §1～§8 |
| **方法权威** | `DM-CLN-004` §6.2.10～6.2.11；`DM-WKF-007` §6.2.20～6.2.22；`DM-ADM-002` §6.2.32；`DM-WKF-004` §6.2.26；`DM-WKF-003` / `DM-ADM-008` enrich |
| **严格对齐** | IF **IF-RPT-NWL-01～07** 与 AS 母表一致 |
| **实现阶段口径** | **应用层 + 领域读侧均已落地**（含 **`NurseWorkloadAmountEnrichmentsupport** 金额 enrich，2026-07-06） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **encounterScope** | `OUTPATIENT` / `INPATIENT` → `Encounter.class_enum` = `AMB` / `IMP`；**必选**；初始化默认 **门诊** |
| **护士筛选** | **可选**；未选 = 当前域 **全院或已选工作科室** 合计 |
| **工作科室** | 可选 `organizationId` → 岗位 **`adm_pr_assign.org_id`**（护士或医技）；应用层解析人员主键集合写入 `NurseWorkloadPageQuery.practitionerIds`；空集合须空结果；**禁止**映射为 `requestingOrgId`，**禁止**再滤 `Practitioner.org_id` |
| **scope 预解析** | 所有 IF-02～06 在调领域 QS 前 **必须** 调用 `NurseWorkloadLoader#prepareNurseWorkloadQueryScopes`；诊疗 IF 仅 enrich SR 类目，耗材 IF 仅 enrich hvcm，**禁止**两侧互扫 |
| **主 Tab** | `EXECUTION`（诊疗执行）/ `CONSUMABLE`（耗材使用）；导出 IF-06 **仅当前主 Tab** 明细 |
| **GenerateSource** | 诊疗明细来自 **SR**；耗材明细来自 **DeviceRequest**；展示名由 **`GenerateSource` 枚举 `display`** |
| **分页** | 明细默认 **20** 条/页（`NurseWorkloadConstants.DEFAULT_PAGE_SIZE`）；汇总 **无分页** |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **NWL-SDD-R-001** | 诊疗统计 **仅** `wkf_service_request` 净 COMPLETED Procedure（**BR-RPT-NWL-003/004**）；**不含** `med_medication_request` |
| **NWL-SDD-R-002** | 耗材纳入状态/归属/时间 **完全** 由 `NurseWorkloadQuerysupport 读取 `deviceAutoInventoryDeductSwich` 后写入 `NurseWorkloadPageQuery`（**BR-RPT-NWL-010～012**） |
| **NWL-SDD-R-003** | 汇总 Tab **禁止** 按护士分块；页脚合计 = 当前筛选下 **单块** 代数和（**BR-RPT-NWL-016**） |
| **NWL-SDD-R-004** | 导出 **拒绝空明细**：0 行 → **`RPT_NWL_201`**（与 AS **EX-RPT-NWL-201** 对齐） |
| **NWL-SDD-R-005** | 导出行上限 **`NurseWorkloadExportConstants.EXPORT_MAX_ROWS = 100000`**；循环分页步长 **`EXPORT_PAGE_SIZE = 500`** |
| **NWL-SDD-R-006** | 导出 MIME 复用 **`InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE`**；文件名 `护士工作量_{主Tab}_{timestamp}.xlsx` |

---

## 2. 代码结构

```text
com/openhis/health/application/report/nurseworkload/
  service/
    NurseWorkloadReportAppService.java
  support/
    NurseWorkloadQuerySupport.java                         # S-R 自 module-support 迁入
    NurseWorkloadAssemblySupport.java
    NurseWorkloadAmountEnrichmentSupport.java
  request/
    LoadNurseWorkloadFormInitRequest.java
    NurseWorkloadQueryRequest.java
    PageNurseExecutionWorkloadDetailRequest.java
    PageNurseConsumableWorkloadDetailRequest.java
    ExportNurseWorkloadDetailRequest.java
  response/
    LoadNurseWorkloadFormInitResponse.java
    SummarizeNurseExecutionWorkloadResponse.java
    PageNurseExecutionWorkloadDetailResponse.java
    SummarizeNurseConsumableWorkloadResponse.java
    PageNurseConsumableWorkloadDetailResponse.java
    ExportNurseWorkloadDetailResponse.java
  dto/
    NurseExecutionSummaryRowDto.java
    NurseExecutionDetailRowDto.java
    NurseConsumableSummaryRowDto.java
    NurseConsumableDetailRowDto.java
    NurseExecutionDetailExportRowDto.java
    NurseConsumableDetailExportRowDto.java
  mapper/
    NurseWorkloadReportMapper.java
    NurseWorkloadReportMapperImpl.java
  constants/
    NurseWorkloadExportConstants.java
```

> 包名：`com.zxplat.domain.application.report.nurseworkload`（**`report/` 直属**，非 `inpatient/nursestation/`）。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate：`requireOperatorId`（登录上下文；**非**数据权限 Gate） |
| `TenantUtils` | Gate：`getCurrentTenantId`（租户隔离） |
| `NurseWorkloadQuerysupport | IF-02～06：**scope / category / hvcm** 预解析 |
| `ProcedureQueryService` | IF-02/03/06（诊疗）：汇总与明细分页 |
| `DeviceDispenseQueryService` | IF-04/05/06（耗材）：汇总与明细分页 |
| `ServiceRequestQueryService` | IF-03/06：批量 SR enrich（项目名、GenerateSource） |
| `ActivityDefinitionQueryService` | IF-03/06：诊疗项目名称 |
| `DeviceRequestQueryService` | IF-05/06：批量 DeviceRequest enrich（GenerateSource） |
| `DeviceDefinitionQueryService` | IF-05/06：耗材名称/规格/hvcm（补全 Support 未覆盖的展示字段） |
| `PractitionerQueryService` | IF-01 护士下拉；IF-03/05/06 执行人展示名 |
| `PatientQueryService` | IF-03/05/06：`findPatientNamesByIds` |
| `EncounterQueryService` | IF-03/05/06：`mapBusNoByEncounterIds` |
| `PractitionerRoleScopeLoader` | IF-01：租户内激活 **护士岗** 人员 |
| `TerminologyOptionLoader` | IF-02～06：`activity_category_code` / `unit_code` 展示名 |
| `NurseWorkloadAssemblysupport | 汇总页脚合计、明细 enrich、GenerateSource/类目/高值展示 |
| `NurseWorkloadAmountEnrichmentsupport | IF-02/03/04/05/06：明细行 **ChargeItem.total_price** 金额 enrich 与 **`totalAmount`** 汇总 |
| `ChargeItemQueryService` | IF-03/05/06 金额 enrich：按 encounter / procedure / service 批量读 chargeItem |
| `NurseWorkloadReportMapper` | Request ↔ `NurseWorkloadPageQuery` / Response / Export DTO |
| `OptionDto`（`com.zxplat.framework.core.application.dto`） | IF-01 下拉；**公共库，不在本模块 dto/ 新建** |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `NurseWorkloadQuerysupport | `prepareNurseWorkloadQueryScopes(NurseWorkloadPageQuery)` | 02、03、04、05、06 |
| `ProcedureQueryService` | `summarizeNurseExecutionWorkloadByCategory(NurseWorkloadPageQuery)` | 02 |
| `ProcedureQueryService` | `pageNurseExecutionWorkloadDetail(NurseWorkloadPageQuery)` | 03、06 |
| `DeviceDispenseQueryService` | `summarizeNurseConsumableWorkloadByHvcm(NurseWorkloadPageQuery)` | 04 |
| `DeviceDispenseQueryService` | `pageNurseConsumableWorkloadDetail(NurseWorkloadPageQuery)` | 05、06 |
| `ServiceRequestQueryService` | `mapListModelsByIds(Collection<Long>)` | 03、06 |
| `ActivityDefinitionQueryService` | `findAllByIds(Collection<Long>)` | 03、06 |
| `DeviceRequestQueryService` | `mapListModelsByIds(Collection<Long>)` | 05、06 |
| `DeviceDefinitionQueryService` | `getAllDeviceDefinitions(Collection<Long>)` | 05、06 |
| `PractitionerQueryService` | `findAllByIds(Collection<Long>)` | 01、03、05、06 |
| `PractitionerRoleScopeLoader` | `listActivePractitionersForPlatformRole(String, Long)` | 01 |
| `PatientQueryService` | `findPatientNamesByIds(Collection<Long>)` | 03、05、06 |
| `EncounterQueryService` | `mapBusNoByEncounterIds(Collection<Long>)` | 03、05、06 |
| `TerminologyOptionLoader` | `buildLabelMap(String codeSystemName)` | 02、03、04、05、06 |
| `NurseWorkloadAssemblysupport | `buildExecutionSummaryResponse(...)` | 02 |
| `NurseWorkloadAssemblysupport | `enrichExecutionDetailRows(...)` | 03、06 |
| `NurseWorkloadAssemblysupport | `buildConsumableSummaryResponse(...)` | 04 |
| `NurseWorkloadAssemblysupport | `enrichConsumableDetailRows(...)` | 05、06 |
| `NurseWorkloadAmountEnrichmentsupport | `enrichExecutionDetailAmounts(List<NurseExecutionDetailRowDto>)` | 03、06 |
| `NurseWorkloadAmountEnrichmentsupport | `enrichConsumableDetailAmounts(rows, deviceReqIdByDispenseId)` | 05、06 |
| `NurseWorkloadAmountEnrichmentsupport | `sumDetailAmounts(List<BigDecimal>)` | 02、04 |
| `ChargeItemQueryService` | `listChargeItemsByEncounterIds` / `listChargeItemsByProcedureIds` / `listChargeItemsByServiceIds` | 03、05、06 |
| `NurseWorkloadReportMapper` | `toNurseWorkloadPageQuery(NurseWorkloadQueryRequest)` | 02、04 |
| `NurseWorkloadReportMapper` | `toNurseWorkloadPageQuery(PageNurseExecutionWorkloadDetailRequest)` | 03、06 |
| `NurseWorkloadReportMapper` | `toNurseWorkloadPageQuery(PageNurseConsumableWorkloadDetailRequest)` | 05、06 |
| `NurseWorkloadReportMapper` | `toExecutionExportRowDto(NurseExecutionDetailRowDto)` | 06 |
| `NurseWorkloadReportMapper` | `toConsumableExportRowDto(NurseConsumableDetailRowDto)` | 06 |
| `NurseWorkloadReportMapper` | `buildExecutionExcelBytes(List<NurseExecutionDetailExportRowDto>)` | 06 |
| `NurseWorkloadReportMapper` | `buildConsumableExcelBytes(List<NurseConsumableDetailExportRowDto>)` | 06 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊范围 | `NurseWorkloadQueryRequest.encounterScope` | **DM-ADM-002** §3 `adm_encounter.class_enum`；**AS-RPT-002** **BR-RPT-NWL-001/002** | `OUTPATIENT`/`INPATIENT` |
| 就诊类别存储码 | `NurseWorkloadPageQuery.resolvedEncounterClassCode` | **DM-ADM-002** §3 `class_enum` | Support 写入 `AMB`/`IMP` |
| 诊疗执行状态 | `com.zxplat.domain.share.enums.EventStatus` | **DM-CLN-004** §5 状态机 | 净执行仅 `COMPLETED`；对冲 `CANCEL` |
| 耗材发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | **DM-WKF-007** §5.2 | 关=`PREPARED`；开=`COMPLETED` |
| 诊疗来源表 | `com.zxplat.domain.share.constants.TableNameConstants.WKF_SERVICE_REQUEST` | **DM-CLN-004** §3 `request_table`；**BR-RPT-NWL-003** | 护士诊疗统计锚点 |
| 诊疗类目 | `NurseExecutionSummaryRowDto.categoryEnum` | **DM-WKF-004** §4.3 `category_enum`；**ServiceRequestCategoryConstants** | 汇总维度 |
| 诊疗类目展示 | `TerminologyCodeSystemNameConstants.ACTIVITY_CATEGORY_CODE` | **DM-WKF-004** §4.3 ActivityDefCategory | `TerminologyOptionLoader` |
| 高值标志 | `NurseConsumableSummaryRowDto.hvcmFlag` | **DM-ADM-008** §3 `hvcm_flag`；**BR-RPT-NWL-013** | 汇总维度 |
| 生成来源 | `com.zxplat.domain.share.enums.GenerateSource` | **DM-ADM-011** §4.2；**DM-WKF-004** / **DM-WKF-003** `generate_source_enum` | 明细单列展示 |
| 生成来源字段（诊疗） | `NurseExecutionDetailRowDto.generateSourceEnum` | **DM-WKF-004** §3 `generate_source_enum` | 来自 SR |
| 生成来源字段（耗材） | `NurseConsumableDetailRowDto.generateSourceEnum` | **DM-WKF-003** §3 `generate_source_enum` | 来自 DeviceRequest |
| 计量单位 | `NurseConsumableDetailRowDto.unitCode` | **DM-WKF-007** §3 `unit_code` | 展示经 `UNIT_CODE` 术语 |
| 明细金额 | `NurseExecutionDetailRowDto.amount` / `NurseConsumableDetailRowDto.amount` | **DM-ADM-011** §3 `total_price`；**AS-RPT-002** **BR-RPT-NWL-021** | 展示 2 位 HALF_UP |
| 汇总页脚金额 | `SummarizeNurseExecutionWorkloadResponse.totalAmount` / `SummarizeNurseConsumableWorkloadResponse.totalAmount` | **AS-RPT-002** **BR-RPT-NWL-022** | 明细全量金额代数和 |
| 主 Tab（导出） | `ExportNurseWorkloadDetailRequest.mainTabCode` | **AS-RPT-002** §1.3 **BR-RPT-NWL-019** | `EXECUTION` / `CONSUMABLE` |
| 明细分页默认 | `NurseWorkloadConstants.DEFAULT_PAGE_SIZE` | **AS-RPT-002** §1.3 分页 | **20** |
| 导出行上限 | `NurseWorkloadExportConstants.EXPORT_MAX_ROWS` | **AS-RPT-002** §7 导出 | **100000** |
| 全局耗材开关 | `NurseWorkloadPageQuery.deviceAutoInventoryDeductEnabled` | **AS-RPT-002** **BR-RPT-NWL-010**；库表 `deviceAutoInventoryDeductSwich` | Support 写入 |
| 护士平台角色 | `com.zxplat.framework.platform.constants.WhalePlatformRoleCodeConstants.ROLE_NURSE` | **DM-ADM-005** 岗位分配 | IF-01 护士下拉 |
| 错误码 | `ErrorCodeConstants.RPT_NWL_101`～`201` | **AS-RPT-002** §5 **EX-RPT-NWL-*** | **目标态待生成** |

### 2.4 白名单双向一致性复核

§2.2 各方法均在 §3 编排序列出现；§3 **无 Cmd**（只读）；编排序列 **禁止** 调用白名单外 Repository / 他聚合 QueryService（enrich 经已列 QS）。

---

## 3. 按接口 ID 命名展开

> **阅读顺序**：先读完各 IF 的「领域编排序列」，按序号从上到下执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-RPT-NWL-01 | `loadNurseWorkloadFormInit` | 页面初始化 | `POST /api/app/nurseWorkloadReport/loadNurseWorkloadFormInit` |
| IF-RPT-NWL-02 | `summarizeNurseExecutionWorkload` | 诊疗执行汇总查询 | `POST /api/app/nurseWorkloadReport/summarizeNurseExecutionWorkload` |
| IF-RPT-NWL-03 | `pageNurseExecutionWorkloadDetail` | 诊疗执行明细分页查询 | `POST /api/app/nurseWorkloadReport/pageNurseExecutionWorkloadDetail` |
| IF-RPT-NWL-04 | `summarizeNurseConsumableWorkload` | 耗材使用汇总查询 | `POST /api/app/nurseWorkloadReport/summarizeNurseConsumableWorkload` |
| IF-RPT-NWL-05 | `pageNurseConsumableWorkloadDetail` | 耗材使用明细分页查询 | `POST /api/app/nurseWorkloadReport/pageNurseConsumableWorkloadDetail` |
| IF-RPT-NWL-06 | `exportNurseWorkloadDetail` | 导出明细 Excel | `POST /api/app/nurseWorkloadReport/exportNurseWorkloadDetail` |
| IF-RPT-NWL-07 | `listNurseWorkloadNurseCards` | 护士卡片墙批量 | `POST /api/app/nurseWorkloadReport/listNurseWorkloadNurseCards` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录 / 租户 / 必填筛选项（`encounterScope`、日期窗、导出主 Tab） | 抛 **`BusinessException`**（`RPT_NWL_102` 等），**不进入 Pre** |
| **Pre** | `NurseWorkloadQuerysupport 预解析 + 领域 QS 读 + 批量 enrich | 抛业务异常或空结果短路 |
| **Cmd** | — | 本场景 **无写库** |
| **Asm** | `NurseWorkloadAssemblysupport + Mapper 装配 Response / Excel | — |

**记法**：`ProcedureQueryService#summarizeNurseExecutionWorkloadByCategory` 表示注入 Bean 后调用。

---

### 3.2 IF-RPT-NWL-01 `loadNurseWorkloadFormInit`（页面初始化）

#### Request 字段表（`LoadNurseWorkloadFormInitRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 否 | 门诊/住院；空则默认门诊。工作科室下拉不随就诊域变化 |
| `organizationId` | `Long` | 否 | 工作科室；已选时人员下拉仅返回挂了该科室的护士/医技 |

#### Response 字段表（`LoadNurseWorkloadFormInitResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultEncounterScope` | `String` | **`OUTPATIENT`**（**BR-RPT-NWL-001**） |
| `defaultDateFrom` | `LocalDate` | 当日 |
| `defaultDateTo` | `LocalDate` | 当日 |
| `encounterScopeOptions` | `List<OptionDto>` | 门诊/住院单选；`value`=scope code，`label`=中文 |
| `organizationOptions` | `List<OptionDto>` | 工作科室下拉（启用护士站 `NS` + 医技 `TECH`） |
| `nurseOptions` | `List<OptionDto>` | 护士下拉（随 `organizationId` 收窄）；`value`=practitionerId |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `RPT_NWL_101` | 初始化装载失败 | 页面配置加载失败，请稍后重试 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 须已登录；失败 **`RPT_NWL_101`** |
| 2 | Gate | `TenantUtils#getCurrentTenantId` | — | 租户上下文 |
| 3 | Pre | `PractitionerRoleScopeLoader#listActivePractitionersForPlatformRole(ROLE_NURSE, null)` | 全院护士岗 | **不按岗位管辖 org 收窄** |
| 3a | Pre | 按岗位 `adm_pr_assign.org_id` 过滤护士/医技 | 可选 `organizationId` | **BR-RPT-NWL-001a** |
| 4 | Asm | 组装 `LoadNurseWorkloadFormInitResponse` | 默认门诊+当日+科室/护士下拉 | **仅读路径** |

---

### 3.3 IF-RPT-NWL-02 `summarizeNurseExecutionWorkload`（诊疗执行汇总查询）

#### Request 字段表（`NurseWorkloadQueryRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 是 | `OUTPATIENT` / `INPATIENT`（**BR-RPT-NWL-001**） |
| `dateFrom` | `LocalDate` | 是 | 闭区间起 |
| `dateTo` | `LocalDate` | 是 | 闭区间止；`dateFrom ≤ dateTo` |
| `organizationId` | `Long` | 否 | 工作科室（`adm_pr_assign.org_id`）；空=全院该域（**BR-RPT-NWL-001a**） |
| `practitionerId` | `Long` | 否 | 单护士；优先于科室集合 |

#### Response 字段表（`SummarizeNurseExecutionWorkloadResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `rows` | `List<NurseExecutionSummaryRowDto>` | 按 `category_enum` 汇总行 |
| `totalExecutionCount` | `Long` | 页脚合计次数（**BR-RPT-NWL-016**） |
| `totalAmount` | `BigDecimal` | 页脚合计金额（**BR-RPT-NWL-022**；展示 2 位） |

#### DTO 字段表 — `NurseExecutionSummaryRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `categoryEnum` | `String` | `ServiceRequest.category_enum` 存储码 |
| `categoryDisplay` | `String` | 类目展示名（术语 `activity_category_code`） |
| `executionCount` | `Long` | 净 COMPLETED 次数 |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `RPT_NWL_102` | 未选门诊/住院或日期非法 | 请选择门诊或住院及有效的查询日期 |
| `RPT_NWL_104` | 查询失败 | 查询失败，请稍后重试 |

#### Mapper 接口方法签名

| 方法签名 |
| --- |
| `toNurseWorkloadPageQuery(NurseWorkloadQueryRequest): NurseWorkloadPageQuery` |
| `toSummaryRowDto(NurseExecutionCategorySummaryQueryModel, String categoryDisplay): NurseExecutionSummaryRowDto` |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `#validateNurseWorkloadQueryRequest` | `encounterScope` / 日期 | 非法 → **`RPT_NWL_102`** |
| 2 | Pre | `NurseWorkloadReportMapper#toNurseWorkloadPageQuery` | Request | 映射领域 Query |
| 3 | Pre | `NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes` | Query | IAE → 转 **`RPT_NWL_102`** |
| 4 | Pre | `ProcedureQueryService#summarizeNurseExecutionWorkloadByCategory` | 预解析 Query | **BR-RPT-NWL-003～007** |
| 5 | Pre | `TerminologyOptionLoader#buildLabelMap(ACTIVITY_CATEGORY_CODE)` | — | 类目展示名 |
| 6 | Asm | `NurseWorkloadAssemblySupport#buildExecutionSummaryResponse` | 汇总行 + 术语 map | 计算 **`totalExecutionCount`** |
| 7 | Pre | `#sumExecutionAmountForQuery` → `NurseWorkloadAmountEnrichmentSupport#sumDetailAmounts` | 复用导出循环 enrich 规则 | 计算 **`totalAmount`** |
| 8 | Asm | 组装 `SummarizeNurseExecutionWorkloadResponse` | — | **仅读路径** |

---

### 3.4 IF-RPT-NWL-03 `pageNurseExecutionWorkloadDetail`（诊疗执行明细分页查询）

#### Request 字段表（`PageNurseExecutionWorkloadDetailRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 是 | 同 IF-02（**BR-RPT-NWL-018** 一致 payload） |
| `dateFrom` | `LocalDate` | 是 | 同 IF-02 |
| `dateTo` | `LocalDate` | 是 | 同 IF-02 |
| `practitionerId` | `Long` | 否 | 同 IF-02 |
| `pageNo` | `Integer` | 否 | 默认 **1** |
| `pageSize` | `Integer` | 否 | 默认 **20**；上限 **1000** |

#### Response 字段表（`PageNurseExecutionWorkloadDetailResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `Long` | 命中总行数 |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `rows` | `List<NurseExecutionDetailRowDto>` | 当前页明细 |

#### DTO 字段表 — `NurseExecutionDetailRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `procedureId` | `Long` | 执行记录主键 |
| `serviceRequestId` | `Long` | SR 主键 |
| `itemName` | `String` | 诊疗项目名称（ActivityDefinition） |
| `categoryEnum` | `String` | 诊疗类目 code |
| `categoryDisplay` | `String` | 类目展示名 |
| `generateSourceEnum` | `String` | SR `generate_source_enum` |
| `generateSourceDisplay` | `String` | GenerateSource 展示名（**BR-RPT-NWL-008**） |
| `practitionerId` | `Long` | 主执行人 |
| `practitionerName` | `String` | 执行护士姓名 |
| `recordedTime` | `LocalDateTime` | 执行时间（**BR-RPT-NWL-005/009**） |
| `encounterId` | `Long` | 就诊主键 |
| `encounterBusNo` | `String` | 就诊号展示 |
| `patientId` | `Long` | 患者主键 |
| `patientName` | `String` | 患者姓名 |
| `amount` | `BigDecimal` | 关联 chargeItem **`total_price` 合计**（**BR-RPT-NWL-021**） |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `RPT_NWL_102` | 筛选项非法 | 同 IF-02 |
| `RPT_NWL_104` | 查询失败 | 查询失败，请稍后重试 |

#### Mapper 接口方法签名

| 方法签名 |
| --- |
| `toNurseWorkloadPageQuery(PageNurseExecutionWorkloadDetailRequest): NurseWorkloadPageQuery` |
| `toDetailRowDto(NurseExecutionWorkloadDetailQueryModel): NurseExecutionDetailRowDto` |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `#validateNurseWorkloadQueryRequest` | 同 IF-02 | **`RPT_NWL_102`** |
| 2 | Pre | `NurseWorkloadReportMapper#toNurseWorkloadPageQuery` | 含分页键 | |
| 3 | Pre | `NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes` | Query | |
| 4 | Pre | `ProcedureQueryService#pageNurseExecutionWorkloadDetail` | Query | `recorded_time DESC` |
| 5 | Pre | 收集页内 `serviceRequestId` / `practitionerId` / `encounterId` / `patientId` | 当前页键集 | |
| 6 | Pre | `ServiceRequestQueryService#mapListModelsByIds` | SR id 集合 | GenerateSource + activityId |
| 7 | Pre | `ActivityDefinitionQueryService#findAllByIds` | activityId 集合 | 项目名称 |
| 8 | Pre | `PractitionerQueryService#findAllByIds` | 执行人 id | |
| 9 | Pre | `PatientQueryService#findPatientNamesByIds` | patientId | |
| 10 | Pre | `EncounterQueryService#mapBusNoByEncounterIds` | encounterId | |
| 11 | Pre | `TerminologyOptionLoader#buildLabelMap(ACTIVITY_CATEGORY_CODE)` | — | 类目展示 |
| 12 | Asm | `NurseWorkloadAssemblySupport#enrichExecutionDetailRows` | 行 + 批量 map | 回填展示字段 |
| 13 | Pre | `NurseWorkloadAmountEnrichmentSupport#enrichExecutionDetailAmounts` | rows | **BR-RPT-NWL-021** |
| 14 | Asm | 组装 `PageNurseExecutionWorkloadDetailResponse` | `Page` total | **仅读路径**；翻页 **不** 触发 IF-02 |

---

### 3.5 IF-RPT-NWL-04 `summarizeNurseConsumableWorkload`（耗材使用汇总查询）

#### Request 字段表（`NurseWorkloadQueryRequest`）

与 **§3.3 IF-02** 相同（**BR-RPT-NWL-018** 筛选 payload 一致）。

#### Response 字段表（`SummarizeNurseConsumableWorkloadResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `rows` | `List<NurseConsumableSummaryRowDto>` | 高/低值汇总行 |
| `totalQuantity` | `BigDecimal` | 页脚数量合计（**BR-RPT-NWL-016**） |
| `totalAmount` | `BigDecimal` | 页脚金额合计（**BR-RPT-NWL-022**） |

#### DTO 字段表 — `NurseConsumableSummaryRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `hvcmFlag` | `Boolean` | `true`=高值；`false`=低值；`null`=未 enrich |
| `hvcmDisplay` | `String` | 「高值耗材」/「低值耗材」/「未知」 |
| `quantityTotal` | `BigDecimal` | 发放量 SUM（**BR-RPT-NWL-014**） |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `RPT_NWL_102` | 筛选项非法 | 同 IF-02 |
| `RPT_NWL_104` | 查询失败 | 查询失败，请稍后重试 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `#validateNurseWorkloadQueryRequest` | — | **`RPT_NWL_102`** |
| 2 | Pre | `NurseWorkloadReportMapper#toNurseWorkloadPageQuery` | Request | |
| 3 | Pre | `NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes` | Query | 含 hvcm enrich |
| 4 | Pre | `DeviceDispenseQueryService#summarizeNurseConsumableWorkloadByHvcm` | Query | **BR-RPT-NWL-010～014** |
| 5 | Asm | `NurseWorkloadAssemblySupport#buildConsumableSummaryResponse` | 汇总行 | **`totalQuantity`** |
| 6 | Pre | `#sumConsumableAmountForQuery` → `NurseWorkloadAmountEnrichmentSupport#sumDetailAmounts` | 复用导出循环 | **`totalAmount`** |
| 7 | Asm | 组装 `SummarizeNurseConsumableWorkloadResponse` | — | **仅读路径** |

---

### 3.6 IF-RPT-NWL-05 `pageNurseConsumableWorkloadDetail`（耗材使用明细分页查询）

#### Request 字段表（`PageNurseConsumableWorkloadDetailRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | `String` | 是 | 同 IF-04 |
| `dateFrom` | `LocalDate` | 是 | 同 IF-04 |
| `dateTo` | `LocalDate` | 是 | 同 IF-04 |
| `practitionerId` | `Long` | 否 | 同 IF-04 |
| `pageNo` | `Integer` | 否 | 默认 **1** |
| `pageSize` | `Integer` | 否 | 默认 **20** |

#### Response 字段表（`PageNurseConsumableWorkloadDetailResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `Long` | 命中总行数 |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `rows` | `List<NurseConsumableDetailRowDto>` | 当前页明细 |

#### DTO 字段表 — `NurseConsumableDetailRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | 发放主键 |
| `deviceDefId` | `Long` | 耗材定义主键 |
| `deviceName` | `String` | 耗材名称 |
| `specText` | `String` | 规格 |
| `hvcmFlag` | `Boolean` | 高值标志 |
| `hvcmDisplay` | `String` | 高/低值展示 |
| `generateSourceEnum` | `String` | DeviceRequest `generate_source_enum` |
| `generateSourceDisplay` | `String` | GenerateSource 展示名（**BR-RPT-NWL-015**） |
| `practitionerId` | `Long` | 归属护士 |
| `practitionerName` | `String` | 护士姓名 |
| `businessTime` | `LocalDateTime` | 业务时间（**BR-RPT-NWL-012**） |
| `quantity` | `BigDecimal` | 发放数量 |
| `unitCode` | `String` | 单位 code |
| `unitCodeLabel` | `String` | 单位展示 |
| `encounterId` | `Long` | 就诊主键 |
| `encounterBusNo` | `String` | 就诊号 |
| `patientId` | `Long` | 患者主键 |
| `patientName` | `String` | 患者姓名 |
| `amount` | `BigDecimal` | 关联 chargeItem **`total_price` 合计**（**BR-RPT-NWL-021**） |

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `RPT_NWL_102` | 筛选项非法 | 同 IF-02 |
| `RPT_NWL_104` | 查询失败 | 查询失败，请稍后重试 |

#### Mapper 接口方法签名

| 方法签名 |
| --- |
| `toNurseWorkloadPageQuery(PageNurseConsumableWorkloadDetailRequest): NurseWorkloadPageQuery` |
| `toDetailRowDto(NurseConsumableWorkloadDetailQueryModel): NurseConsumableDetailRowDto` |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `#validateNurseWorkloadQueryRequest` | — | **`RPT_NWL_102`** |
| 2 | Pre | `NurseWorkloadReportMapper#toNurseWorkloadPageQuery` | 含分页 | |
| 3 | Pre | `NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes` | Query | |
| 4 | Pre | `DeviceDispenseQueryService#pageNurseConsumableWorkloadDetail` | Query | 业务时间 DESC |
| 5 | Pre | 收集 `deviceReqId` / `deviceDefId` / 人患就诊 id | 当前页 | |
| 6 | Pre | `DeviceRequestQueryService#mapListModelsByIds` | deviceReqId | GenerateSource |
| 7 | Pre | `DeviceDefinitionQueryService#getAllDeviceDefinitions` | deviceDefId | 名称/规格 |
| 8 | Pre | `PractitionerQueryService#findAllByIds` | practitionerId | |
| 9 | Pre | `PatientQueryService#findPatientNamesByIds` | patientId | |
| 10 | Pre | `EncounterQueryService#mapBusNoByEncounterIds` | encounterId | |
| 11 | Pre | `TerminologyOptionLoader#buildLabelMap(UNIT_CODE)` | — | 单位展示 |
| 12 | Asm | `NurseWorkloadAssemblySupport#enrichConsumableDetailRows` | — | |
| 13 | Pre | `NurseWorkloadAmountEnrichmentSupport#enrichConsumableDetailAmounts` | rows + deviceReqId map | **BR-RPT-NWL-021** |
| 14 | Asm | 组装 `PageNurseConsumableWorkloadDetailResponse` | — | **仅读路径** |

---

### 3.7 IF-RPT-NWL-06 `exportNurseWorkloadDetail`（导出明细 Excel）

#### Request 字段表（`ExportNurseWorkloadDetailRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `mainTabCode` | `String` | 是 | **`EXECUTION`** 或 **`CONSUMABLE`**（**BR-RPT-NWL-019**） |
| `encounterScope` | `String` | 是 | 同对应明细 IF |
| `dateFrom` | `LocalDate` | 是 | 同对应明细 IF |
| `dateTo` | `LocalDate` | 是 | 同对应明细 IF |
| `practitionerId` | `Long` | 否 | 同对应明细 IF |

#### Response 字段表（`ExportNurseWorkloadDetailResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | `护士工作量_{主Tab}_{timestamp}.xlsx` |
| `contentType` | `String` | XLSX MIME |
| `fileBytes` | `byte[]` | Excel 二进制 |
| `truncated` | `Boolean` | 是否触顶 **EXPORT_MAX_ROWS** |
| `exportedRowCount` | `Integer` | 实际导出行数 |
| `mainTabCode` | `String` | 回显导出主 Tab |

#### DTO 字段表 — `NurseExecutionDetailExportRowDto`

与 **`NurseExecutionDetailRowDto`** 字段**同构**；列顺序固定；`@ExcelProperty` 注解列标题对齐 AS §1.3.2 诊疗明细列。

#### DTO 字段表 — `NurseConsumableDetailExportRowDto`

与 **`NurseConsumableDetailRowDto`** 字段**同构**；列标题对齐 AS §1.3.2 耗材明细列。

#### 失败码口径

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `RPT_NWL_102` | 筛选项非法 | 同 IF-02 |
| `RPT_NWL_201` | 无明细可导出 | 当前条件下无明细可导出 |
| `RPT_NWL_105` | 导出失败或超上限 | 导出失败，请缩小日期范围后重试 |

#### Mapper 接口方法签名

| 方法签名 |
| --- |
| `toNurseWorkloadPageQuery(ExportNurseWorkloadDetailRequest): NurseWorkloadPageQuery` |
| `toExecutionExportRowDto(NurseExecutionDetailRowDto): NurseExecutionDetailExportRowDto` |
| `toConsumableExportRowDto(NurseConsumableDetailRowDto): NurseConsumableDetailExportRowDto` |
| `buildExecutionExcelBytes(List<NurseExecutionDetailExportRowDto>): byte[]`（**MapperImpl** 委托 **`AnnotatedExcelExporter#exportToXlsxBytes`**） |
| `buildConsumableExcelBytes(List<NurseConsumableDetailExportRowDto>): byte[]`（同上） |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `#validateNurseWorkloadQueryRequest` + `#validateMainTabCode` | `mainTabCode` | 非法 Tab → **`RPT_NWL_102`** |
| 2 | Pre | `NurseWorkloadReportMapper#toNurseWorkloadPageQuery` | 无分页键 | |
| 3 | Pre | `NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes` | Query | |
| 4 | Pre | **若** `mainTabCode=EXECUTION` → 循环调用 **复用 IF-03 编排序列 4～12**（`pageNo` 递增，步长 **500**）直至空页或触顶 **100000**；**否则若** `mainTabCode=CONSUMABLE` → **复用 IF-05 编排序列 4～12** 同样循环 | 全量明细 | **互斥路径**：仅执行其一 |
| 5 | Gate | `#assertExportNotEmpty` | 聚合行数 | 0 行 → **`RPT_NWL_201`**（**NWL-SDD-R-004**） |
| 6 | Asm | **若** `EXECUTION` → `toExecutionExportRowDto` + `buildExecutionExcelBytes`；**否则** → `toConsumableExportRowDto` + `buildConsumableExcelBytes` | — | **禁止** 导出汇总或另一 Tab |
| 7 | Asm | 组装 `ExportNurseWorkloadDetailResponse` | `truncated` 标志 | **仅读路径** |

---

## 4. QueryService 与领域事件

| 事件 | 来源 | 读侧影响 |
| --- | --- | --- |
| `ProcedureRecordCompletedEvent` / `ProcedureRecordCancelledEvent` | 护士站执行 | 手动查询后反映净执行变化 |
| 耗材发放/取消事件 | 护士站耗材 | 手动查询后反映耗材统计 |
| `NursePricingServiceRequestCreatedEvent` | 住院划价 | Procedure 落库后纳入诊疗 IF-02/03 |

本页 **不发布** 领域事件；**手动查询** 为准。QueryService 返回查询时刻快照，**无** 读侧事件订阅刷新。

---

## 5. 写库顺序（核心写接口视角）

**无写库**（IF-RPT-NWL-01～06 均为 **仅读路径**）。

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| 01～06 | 全部 | — | 无 `*DomainService` 调用 |

**事务边界**：只读接口无事务性写；单次 HTTP 内多次 QS 调用为 **只读快照**，不要求同一 DB 事务。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 读接口天然幂等 | `NurseWorkloadReportAppService` 全部 public 方法 | §3 各 IF |

### 6.2 并发控制

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 无写库锁 | — | 不适用 |

### 6.3 重试与容错

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| QS 异常包装 **`RPT_NWL_104`** | `#executeQuerySafely` | §3.3 序号 4；§3.5 序号 4 等 |
| 导出异常 **`RPT_NWL_105`** | `exportNurseWorkloadDetail` catch | §3.7 序号 6 |
| 导出触顶 **`truncated=true`** | `#exportLoopUntilCap` | §3.7 序号 4 |

### 6.4 数据脱敏

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 租户隔离由框架 + 软删过滤保证 | 领域 QS | 全部 Pre |
| **无** 机构/个人数据权限 Gate | — | **AS §1.3** |

### 6.5 审计日志

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 导出操作记 info 日志（操作员、主 Tab、行数） | `exportNurseWorkloadDetail` | §3.7 |

### 6.6 性能

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 就诊 scope 叠统计日窗（相交） | `NurseWorkloadLoader` → `listEncounterIdsForNurseWorkloadScope(class, from, to)` | §3.3 序号 3 |
| 汇总页脚金额一次加载事实行 | `listNurse*WorkloadDetail` + `sumExecutionSaleAmountForQuery` / `sumConsumableAmountsForQuery`（IN 分块 enrich；耗材售价/进价同扫；**禁止**套 `EXPORT_MAX_ROWS` 静默截断） | IF-02 / IF-04 |
| 金额 enrich 仅锚点 IN | `NurseWorkloadAmountEnricher`（禁止按就诊全量 ChargeItem） | IF-02～06 |
| Support 按需 enrich | `prepareNurseWorkloadQueryScopes(query, needExecution, needConsumable)` | IF-02～06 |
| IF-07 卡片墙一次分桶 | `listNurseWorkloadNurseCards`（轻量 enrich；禁止前端翻页 IF-03/05） | UI / IF-07 |
| 导出分批 | `exportExecutionDetailsLoop` / `exportConsumableDetailsLoop` | IF-06 |

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 测试层级 | 自动化 | 优先级 |
| --- | --- | --- | --- | :---: |
| IF-01 初始化 | 默认门诊+当日；护士下拉非空 | 功能 | API | P0 |
| IF-02 诊疗汇总 | 按 category 分桶；页脚合计 | 功能 | API | P0 |
| IF-03 诊疗明细 | GenerateSource 单列；recorded_time DESC | 功能 | API | P0 |
| IF-04 耗材汇总 | 高/低值两行+合计 | 功能 | API | P0 |
| IF-05 耗材明细 | 开关关/开归属与时间字段 | 功能 | API | P0 |
| IF-06 导出 | 仅当前 Tab；无数据 **201** | 功能 | API | P0 |
| 净执行对冲 | A→取消→C 仅 C 计 1 | 功能 | 集成 | P0 |
| 未选护士 | 全院单块合计 | 功能 | API | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
| --- | --- | :---: |
| 90 天区间查询 | 响应 &lt; 5s（P95） | P1 |
| 导出 10 万行 | 触顶 `truncated` | P1 |

### 7.3 查询结果一致性

| 场景 | 要点 |
| --- | --- |
| 汇总 vs 明细 | 同筛选下 IF-02 合计 = IF-03 全量条数之和 |
| 耗材 SUM | IF-04 合计 = IF-05 全量 quantity 之和 |

### 7.4 集成测试

| 场景 | 要点 |
| --- | --- |
| 护士划价 + Procedure | `nurse-pricing` 落 Procedure 后 IF-02/03 可见 |
| 耗材开关 | 全局开关切换后 IF-04/05 口径切换 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | :---: |
| 应用层 E2E | 待 `NurseWorkloadReportAppService` 成码后补 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.8 | 2026-10-06 | 对齐 **AS-RPT-002 v1.10**：**BR-RPT-NWL-023** 优惠列；售价保持 `total_price` 原价 |
| v1.7 | 2026-09-19 | 对齐 **AS-RPT-002 v1.9**：科室筛选改为工作科室；下拉 NS+TECH；V12 按分类补岗 |
| v1.6 | 2026-08-24 | 对齐 **AS-RPT-002 v1.8**：页脚 IN 分块 enrich；耗材售价/进价同扫 |
| v1.5 | 2026-08-24 | 对齐 **AS-RPT-002 v1.7**：汇总页脚 `sum*AmountForQuery` 禁止套 `EXPORT_MAX_ROWS` 静默截断 |
| v1.4 | 2026-08-19 | 对齐 **AS-RPT-002 v1.6**：默认登录科室科室统计；`practitionerIds` 收窄；禁止 organizationId→requestingOrgId |
| v1.3 | 2026-08-03 | 对齐 **AS-RPT-002 v1.4**：增 IF-07 护士卡片墙批量；前端改调批量 IF |
| v1.2 | 2026-08-03 | 对齐 **AS-RPT-002 v1.3**：汇总金额一次加载；金额 enrich 锚点 IN；Support 按需 enrich；前端卡片一次拉明细分桶 |
| v1.1 | 2026-07-06 | 对齐 **AS-RPT-002 v1.2**：**`NurseWorkloadAmountEnrichmentsupport**；明细/汇总 **`amount`/`totalAmount`**；应用层标记 **已落地** |
| v1.0 | 2026-07-03 | 首版：对齐 **AS-RPT-002 v1.1**；6 IF 目标态契约；领域读侧已关闭 Gap |
