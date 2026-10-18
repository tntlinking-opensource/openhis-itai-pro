# 门诊处方点评 · 应用服务详细设计（SDD）

> **编码**：AS-OP-PHA-007-SDD  
> **版本**：v1.4 | **日期**：2026-08-31  
> **编排权威**：`AS-OP-PHA-007-门诊处方点评.md`（**v1.3**）  
> **领域权威**：`DM-CUS-015`（写 §6.1 / 读 §6.2）；Gate 协作 `DM-MED-003` §6.2.21、`DM-MED-002`（发放完成态反查）  
> **说明**：本 SDD **不维护 Gap 表**；领域层点评能力已落地；应用层 `outpatient/pharmacy/prescriptionreview/**` 按本文成码。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊处方点评（记录页 init/分页/导出、批量点评、撤销、自动点评、处方号批量映射） |
| **边界** | 点评聚合读写；已发药 Gate 只读药嘱/发放；**不**写发放/医嘱；**不**复制处方明细查询（详情复用 AS-OP-PHA-005）；与审方无关；**点评记录页 UI 只读**（init/分页/导出/详情，**无点评/撤销**）；列表/导出列对齐「处方点评工作表」（无序号）；方级指标读侧 enrichment |
| **编排权威** | `AS-OP-PHA-007` §2 / §4 |
| **方法权威** | `PrescriptionReviewDomainService#createReview` / `createReviews` / `revokeReview`；`PrescriptionReviewQueryService#findActiveByPrescriptionNo` / `mapActiveByPrescriptionNos` / `getById` / `pageReviews`；Gate：`MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines`；开方日预解析：`listDistinctSignedPrescriptionNosByOpenedTimeWindow`；enrichment：`listMedicationRequestListModelsByPrescriptionNos`；`MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum` |
| **严格对齐** | IF **IF-PRV-01～07** |
| **实现阶段口径** | **目标态全量契约**（领域已落地；应用层按本 SDD 生成） |
| **跨 AS** | **AS-OP-PHA-001** 核发成功后调用 IF-PRV-06；**AS-OP-PHA-005** 列表/详情调用 IF-PRV-07 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **有效点评** | `is_deleted=false`；`status_enum=REVIEWED` |
| **待点评筛** | `statusEnum=PENDING` → `pageReviews` 空页（领域已实现） |
| **已发药 Gate** | 处方号 → 已签发药品申请行 id 集合 → 与 `listDistinctMedicationRequestIdsByStatusEnum(COMPLETED)` 求交；**方内至少一行**药品申请 id 命中 COMPLETED 即视为已发药（与方级发药态一致前提） |
| **点评业务号 bus_no** | 领域 `BusinessSequenceService#getSeqByDay(AggregateTypeConstants.PRESCRIPTION_REVIEW, BUS_NO_SEQ_LENGTH)` 自生成（前缀 **PRV**）；**不是** MR/EN 快照；应用层不解析、不入参 |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/{methodName}` |
| **分页** | `pageNo` 1-based；`PageWindowGate` + `PagedResultBuilder`（B.10.11） |
| **事务** | 写 IF（04/05/06）依赖 **AppServiceAspect** UoW，业务代码**禁止**声明 `@Transactional`；读 IF 由只读切面管理；IF-PRV-06 由发药编排在发药成功后调用（失败不回滚发药） |
| **导出** | `AnnotatedExcelExporter` + Request.`exportColumns`；`PrescriptionReviewExportRowDto` `@Excel` 白名单与工作表数据列对齐；导出窗口=`pageNo`/`pageSize`（当前页） |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/pharmacy/prescriptionreview/
  service/
    OutpatientPharmacyPrescriptionReviewAppService.java
  request/
    LoadPageInitRequest.java
    PageReviewsRequest.java
    ExportReviewsRequest.java
    BatchCreateReviewsRequest.java
    RevokeReviewRequest.java
    CreateAutoReviewAfterDispenseRequest.java
    MapActiveReviewsByPrescriptionNosRequest.java
  response/
    LoadPageInitResponse.java
    PageReviewsResponse.java
    ExportReviewsResponse.java
    BatchCreateReviewsResponse.java
    RevokeReviewResponse.java
    CreateAutoReviewAfterDispenseResponse.java
    MapActiveReviewsByPrescriptionNosResponse.java
  dto/
    PrescriptionReviewListRowDto.java
    PrescriptionReviewExportRowDto.java
    PrescriptionReviewActiveDto.java
    BatchCreateReviewItemDto.java
  mapper/
    OutpatientPharmacyPrescriptionReviewMapper.java
    OutpatientPharmacyPrescriptionReviewMapperImpl.java
  support/
    OutpatientPharmacyPrescriptionReviewSupport.java
    OutpatientPharmacyPrescriptionReviewDispenseGate.java
    OutpatientPharmacyPrescriptionReviewListEnricher.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  OutpatientPharmacyPrescriptionReviewConstants.java
```

> 包名：`com.zxplat.domain.application.outpatient.pharmacy.prescriptionreview`。  
> `OptionDto` 使用 zxplat-framework 公共库，不在本模块 `dto` 声明。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentPractitionerResolver` | Gate 登录医务人员（`adm_practitioner.id`）；点评人/操作人 |
| `PageWindowGate` | IF-PRV-02 分页门禁 |
| `PractitionerQueryService` | 点评人姓名批量 |
| `PrescriptionReviewDomainService` | 写：createReview（单条原语）/ createReviews / revokeReview |
| `PrescriptionReviewQueryService` | 读：pageReviews / mapActiveByPrescriptionNos / findActiveByPrescriptionNo / getById |
| `MedicationRequestQueryService` | Gate 已发药：`pageOutpatientSignedPrescriptionDrugLines`（仅取 medReqId，不取 busNo）；开方日窗：`listDistinctSignedPrescriptionNosByOpenedTimeWindow` |
| `MedicationDispenseQueryService` | Gate：`listDistinctMedicationRequestIdsByStatusEnum` |
| `OutpatientPharmacyPrescriptionReviewDispenseGate` | 已发药判定封装 |
| `OutpatientPharmacyPrescriptionReviewSupport` | 选项装配、自动点评跳过逻辑 |
| `OutpatientPharmacyPrescriptionReviewListEnricher` | 列表/导方级指标 enrichment（工作表列） |
| `OutpatientPharmacyPrescriptionReviewMapper` | Request/Command/Response/DTO 映射 |
| `AnnotatedExcelExporter` | IF-PRV-03 xlsx |
| `PagedResultBuilder` | 分页响应装配（若工程惯例注入） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 点评状态 | `com.zxplat.domain.share.enums.PrescriptionReviewStatus` | `DM-CUS-015` §4.1 / §2.3 | IF-PRV-01/02/03；`PageReviewsRequest.statusEnum` |
| 是否合理 | `PageReviewsRequest.reasonableFlag` / `BatchCreateReviewsRequest.reasonableFlag` | `DM-CUS-015` §3.1 `reasonable_flag`；§4.2 | Boolean；选项文案合理/不合理 |
| 原因 | `BatchCreateReviewsRequest.reason` / `CreatePrescriptionReviewCommand.reason` | `DM-CUS-015` §3.1 `reason`；**BR-PRV-002** | 不合理必填 |
| 备注 | `BatchCreateReviewsRequest.remark` | `DM-CUS-015` §3.1 `remark` | 可选 |
| 发放完成态 | `com.zxplat.domain.share.enums.DispenseStatus.COMPLETED` | `DM-MED-002` 状态机 COMPLETED | Gate / 自动点评语境 |
| 应用语义码 | `OutpatientPharmacyPrescriptionReviewConstants` | **AS** §5 **EX-PRV-*** | 已落码（EX-PRV-101～205 + 导出常量） |
| 导出列白名单 | `PrescriptionReviewExportRowDto` 各 `@Excel` 字段 | **AS** **BR-PRV-A04** | 与列表列同构 |

### 2.4 领域方法与 Mapper 白名单

**领域写（DomainService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PrescriptionReviewDomainService` | `createReview(CreatePrescriptionReviewCommand)` | 领域单条原语；由 `createReviews` 逐条复用（App 不直调） |
| `PrescriptionReviewDomainService` | `createReviews(CreatePrescriptionReviewsCommand)` | IF-PRV-04；IF-PRV-06 |
| `PrescriptionReviewDomainService` | `revokeReview(RevokePrescriptionReviewCommand)` | IF-PRV-05 |

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PrescriptionReviewQueryService` | `pageReviews` | IF-PRV-02、03 |
| `PrescriptionReviewQueryService` | `mapActiveByPrescriptionNos` | IF-PRV-07；IF-PRV-04/06 Pre 过滤 |
| `PrescriptionReviewQueryService` | `findActiveByPrescriptionNo` | IF-PRV-05 可选定位 |
| `PrescriptionReviewQueryService` | `getById` | IF-PRV-05 主键定位 |
| `MedicationRequestQueryService` | `pageOutpatientSignedPrescriptionDrugLines` | Gate 已发药（仅 IF-PRV-04；IF-PRV-06 信任发药调用方 COMPLETED） |
| `MedicationRequestQueryService` | `listDistinctSignedPrescriptionNosByOpenedTimeWindow` | IF-PRV-02、03 开方日 → 方号 IN |
| `MedicationDispenseQueryService` | `listDistinctMedicationRequestIdsByStatusEnum` | Gate（IF-PRV-04） |
| `PractitionerQueryService` | `mapNamesByIds` | IF-PRV-02、03、07 姓名 |

**module-support**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientPharmacyPrescriptionReviewSupport` | `buildStatusOptions` | IF-PRV-01 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `buildReasonableOptions` | IF-PRV-01 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `defaultReviewedDateWindow` | IF-PRV-01 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `assertReviewedDateWindow` / `assertOpenedDateWindow` | IF-PRV-02、03 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `assertBatchCreateRequest` | IF-PRV-04 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `assertRevokeAnchor` | IF-PRV-05 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `assertAutoReviewRequest` | IF-PRV-06 |
| `OutpatientPharmacyPrescriptionReviewSupport` | `filterPrescriptionNosWithoutActiveReview` | IF-PRV-06 |
| `OutpatientPharmacyPrescriptionReviewDispenseGate` | `assertDispensedOutpatientPrescriptions` | IF-PRV-04 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toLoadPageInitResponse` | IF-PRV-01 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toPageQuery` | IF-PRV-02、03 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toListRowDto` | IF-PRV-02、03 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toPageResponse` | IF-PRV-02 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toExportRowDto` | IF-PRV-03 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toExportResponse` | IF-PRV-03 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toCreateReviewsCommand` | IF-PRV-04 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toBatchCreateResponse` | IF-PRV-04 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toRevokeCommand` | IF-PRV-05 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toCreateReviewsCommandForAuto` | IF-PRV-06 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toAutoReviewResponse` | IF-PRV-06 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toActiveDto` | IF-PRV-07 |
| `OutpatientPharmacyPrescriptionReviewMapper` | `toMapActiveResponse` | IF-PRV-07 |

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行；**Gate/Pre 失败禁止进入 Cmd**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-PRV-01 | `loadPageInit` | 点评记录页初始化 | `POST /api/app/outpatientPharmacyPrescriptionReview/loadPageInit` |
| IF-PRV-02 | `pageReviews` | 分页查询点评记录 | `POST /api/app/outpatientPharmacyPrescriptionReview/pageReviews` |
| IF-PRV-03 | `exportReviews` | 导出点评记录 | `POST /api/app/outpatientPharmacyPrescriptionReview/exportReviews` |
| IF-PRV-04 | `batchCreateReviews` | 批量创建点评 | `POST /api/app/outpatientPharmacyPrescriptionReview/batchCreateReviews` |
| IF-PRV-05 | `revokeReview` | 撤销点评 | `POST /api/app/outpatientPharmacyPrescriptionReview/revokeReview` |
| IF-PRV-06 | `createAutoReviewAfterDispense` | 发药完成后自动点评 | `POST /api/app/outpatientPharmacyPrescriptionReview/createAutoReviewAfterDispense` |
| IF-PRV-07 | `mapActiveReviewsByPrescriptionNos` | 按处方号批量映射有效点评 | `POST /api/app/outpatientPharmacyPrescriptionReview/mapActiveReviewsByPrescriptionNos` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录 / 分页 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：已发药 Gate、已点评过滤（业务号由领域自生成） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | Mapper / Exporter 纯装配 | — |

---

### 3.2 IF-PRV-01 `loadPageInit`（点评记录页初始化）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/loadPageInit` |

**Request `LoadPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体或占位 |

**Response `LoadPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `statusOptions` | `List<OptionDto>` | 待点评 `PENDING` / 已点评 `REVIEWED` |
| `reasonableOptions` | `List<OptionDto>` | 合理 `true` / 不合理 `false`（value 字符串化） |
| `defaultReviewedDateFrom` | `String` | 默认点评日起 `yyyy-MM-dd`（本月首日） |
| `defaultReviewedDateTo` | `String` | 默认点评日止 `yyyy-MM-dd`（本月末日） |

**关键校验**：依赖登录上下文。

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-PRV-101 | 未登录或初始化失败 |

**Mapper 方法签名**

- `toLoadPageInitResponse(List<OptionDto> statusOptions, List<OptionDto> reasonableOptions, String defaultFrom, String defaultTo): LoadPageInitResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 未登录 → EX-PRV-101 |
| 2 | Pre | `OutpatientPharmacyPrescriptionReviewSupport#buildStatusOptions` | — | PENDING/REVIEWED |
| 3 | Pre | `OutpatientPharmacyPrescriptionReviewSupport#buildReasonableOptions` | — | 合理/不合理 |
| 4 | Pre | `OutpatientPharmacyPrescriptionReviewSupport#defaultReviewedDateWindow` | — | 本月起止 |
| 5 | Asm | `Mapper#toLoadPageInitResponse` | 步骤 2～4 | 仅读路径 |

---

### 3.3 IF-PRV-02 `pageReviews`（分页查询点评记录）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/pageReviews` |

**Request `PageReviewsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `statusEnum` | `String` | 否 | `PENDING`/`REVIEWED`；空=不限（有效行全集，不含待点评虚行） |
| `reviewedDateFrom` | `String` | 否 | 点评日起 `yyyy-MM-dd`；映射日初 |
| `reviewedDateTo` | `String` | 否 | 点评日止 `yyyy-MM-dd`；映射日末 |
| `openedDateFrom` | `String` | 否 | 开方日起 `yyyy-MM-dd`；与止成对；应用层预解析方号 IN |
| `openedDateTo` | `String` | 否 | 开方日止 `yyyy-MM-dd`；与起成对 |
| `reasonableFlag` | `Boolean` | 否 | 是否合理；空=不限 |
| `reviewerId` | `Long` | 否 | 点评人；空=不限 |
| `keyword` | `String` | 否 | 关键字：处方号或点评业务号模糊（OR） |
| `pageNo` | `Integer` | 是 | 1-based |
| `pageSize` | `Integer` | 是 | 页大小 |

**Response `PageReviewsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalCount` | `Long` | 总条数 |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `content` | `List<PrescriptionReviewListRowDto>` | 列表行 |

**DTO `PrescriptionReviewListRowDto`**（工作表同列；`id` 仅行键）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 点评主键（不进导出列） |
| `prescriptionNo` | `String` | 处方编号 |
| `patientAgeText` | `String` | 年龄(岁) |
| `diagnosisName` | `String` | 诊断并集 |
| `drugVarietyCount` | `Integer` | 药品品种 |
| `antibioticFlag` | `Integer` | 抗菌药 0/1 |
| `injectFlag` | `Integer` | 注射剂 0/1 |
| `basicDrugVarietyCount` | `Integer` | 国家基本药物品种数 |
| `genericNameCount` | `Integer` | 药品通用名数 |
| `prescriptionAmount` | `BigDecimal` | 处方金额(元)，DISPLAY 2 位 |
| `orderingPractitionerName` | `String` | 处方医师 |
| `reasonableFlag` | `Boolean` | 是否合理 |
| `reasonableText` | `String` | 合理/不合理（列表辅助） |
| `reason` | `String` | 存在问题 |
| `busNo` | `String` | 点评业务号（详情「点评编号」；不进导出列） |
| `statusEnum` / `statusText` | `String` | 详情点评状态 |
| `remark` | `String` | 详情备注 |
| `reviewerId` / `reviewerName` | `Long`/`String` | 详情点评人 |
| `reviewedAt` | `String` | 详情点评时间 |

**关键校验**：`pageNo`/`pageSize` 经 PageWindowGate；若起止日期均传则 `from ≤ to`。

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-PRV-101 | 未登录 |
| EX-PRV-102 | 分页或时间窗非法 |
| EX-PRV-103 | 查询失败 |

**Mapper 方法签名**

- `toPageQuery(PageReviewsRequest request): PrescriptionReviewPageQuery`
- `toListRowDto(PrescriptionReviewQueryModel model, String reviewerName): PrescriptionReviewListRowDto`
- `toPageResponse(Page<PrescriptionReviewQueryModel> page, List<PrescriptionReviewListRowDto> rows): PageReviewsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 未登录 → EX-PRV-101 |
| 2 | Gate | `PageWindowGate#requirePositivePageWindow` | `pageNo`/`pageSize` | 非法 → EX-PRV-102 |
| 3 | Gate | `OutpatientPharmacyPrescriptionReviewSupport#assertReviewedDateWindow` | from/to | 非法 → EX-PRV-102；未传则跳过 |
| 4 | Gate | `OutpatientPharmacyPrescriptionReviewSupport#assertOpenedDateWindow` | opened from/to | 成对；非法 → EX-PRV-102；未传则跳过 |
| 5 | Pre | `Mapper#toPageQuery` | Request | 日期转 LocalDateTime 闭区间 |
| 6 | Pre | `MedicationRequestQueryService#listDistinctSignedPrescriptionNosByOpenedTimeWindow` → `PageQuery#prescriptionNosIn` | 开方日窗 | 两侧皆空跳过；空列表→空页 |
| 7 | Pre | `PrescriptionReviewQueryService#pageReviews` | Query + Pageable | 仅读；PENDING→空页；方号 IN 分批 |
| 8 | Asm | `Mapper#toListRowDto` | 逐行 model | 点评字段（方号/合理/原因） |
| 9 | Pre | `OutpatientPharmacyPrescriptionReviewListEnricher#enrichWorksheetColumns` | 页内行 | 方级指标批量 enrichment（B.6.4） |
| 10 | Asm | `Mapper#toPageResponse` | 分页 + 行 | 仅读路径 |

---

### 3.4 IF-PRV-03 `exportReviews`（导出点评记录）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/exportReviews` |

**Request `ExportReviewsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `statusEnum` | `String` | 否 | 同 IF-PRV-02 |
| `reviewedDateFrom` | `String` | 否 | 同 IF-PRV-02 |
| `reviewedDateTo` | `String` | 否 | 同 IF-PRV-02 |
| `openedDateFrom` | `String` | 否 | 同 IF-PRV-02 |
| `openedDateTo` | `String` | 否 | 同 IF-PRV-02 |
| `reasonableFlag` | `Boolean` | 否 | 同 IF-PRV-02 |
| `reviewerId` | `Long` | 否 | 同 IF-PRV-02 |
| `keyword` | `String` | 否 | 同 IF-PRV-02（处方号/业务号 OR） |
| `pageNo` | `Integer` | 是 | 当前页（1-based）；与列表一致 |
| `pageSize` | `Integer` | 是 | 页大小；导出条数=本页行数 |
| `exportColumns` | `List<ExcelExportColumnDto>` | 是 | 导出列（R-EXCEL-4）；`field` ⊆ ExportRowDto `@Excel` 白名单 |

**Response `ExportReviewsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 默认文件名（如 `处方点评记录.xlsx`） |
| `contentType` | `String` | xlsx MIME |
| `body` | `byte[]` | 文件字节 |
| `exportedRowCount` | `Integer` | 导出行数 |
| `truncated` | `Boolean` | 总条数大于本页累计导出时 true（尚有未导出页） |

**DTO `PrescriptionReviewExportRowDto`**（`@Excel` 与工作表数据列同构，无序号）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `prescriptionNo` | `String` | 处方编号 |
| `patientAgeText` | `String` | 年龄(岁) |
| `diagnosisName` | `String` | 诊断 |
| `drugVarietyCount` | `Integer` | 药品品种 |
| `antibioticFlag` | `Integer` | 抗菌药(0/1) |
| `injectFlag` | `Integer` | 注射剂(0/1) |
| `basicDrugVarietyCount` | `Integer` | 国家基本药物品种数 |
| `genericNameCount` | `Integer` | 药品通用名数 |
| `prescriptionAmount` | `BigDecimal` | 处方金额(元) |
| `orderingPractitionerName` | `String` | 处方医师 |
| `reasonableFlag` | `Integer` | 是否合理(0/1) |
| `reason` | `String` | 存在问题 |

**失败码**：EX-PRV-101、EX-PRV-102、EX-PRV-104

**Mapper 方法签名**

- `toPageQuery(ExportReviewsRequest request): PrescriptionReviewPageQuery`
- `toExportRowDto(PrescriptionReviewListRowDto row): PrescriptionReviewExportRowDto`
- `toExportResponse(byte[] body, int rowCount, boolean truncated): ExportReviewsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1～4 | Gate | **复用 IF-PRV-02 序号 1～4** | 校验 pageNo/pageSize + exportColumns 非空 + 点评/开方日窗 | 非法 → EX-PRV-102 |
| 5 | Pre | `Mapper#toPageQuery` | Export Request | 同筛选 |
| 6 | Pre | 开方日窗 → `prescriptionNosIn`（同 IF-PRV-02） | opened from/to | 两侧皆空跳过 |
| 7 | Pre | `PrescriptionReviewQueryService#pageReviews` | Query + `PageRequest.of(pageNo-1, pageSize)` | **当前页** |
| 8 | Asm | `Mapper#toListRowDto` + Enricher | 同 IF-PRV-02 | 工作表列 |
| 9 | Asm | `Mapper#toExportRowDto` | 逐行；合理→0/1 | ExportRowDto |
| 10 | Asm | `AnnotatedExcelExporter#exportToXlsxBytes` | rows + `exportColumns` | 工作表名见 Constants |
| 11 | Asm | `Mapper#toExportResponse` | bytes + count + truncated | 零行仍含表头；尚有未导出页时 truncated |

---

### 3.5 IF-PRV-04 `batchCreateReviews`（批量创建点评）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/batchCreateReviews` |

**Request `BatchCreateReviewsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<BatchCreateReviewItemDto>` | 是 | 待点评处方列表，非空 |
| `reasonableFlag` | `Boolean` | 是 | 共用是否合理 |
| `reason` | `String` | 条件 | 不合理时必填 |
| `remark` | `String` | 否 | 共用备注 |

**DTO `BatchCreateReviewItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `prescriptionNo` | `String` | 是 | 处方号 |

**Response `BatchCreateReviewsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `createdIds` | `List<Long>` | 新建点评主键列表 |
| `createdCount` | `Integer` | 成功条数 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| items 非空 | 否则 EX-PRV-200 |
| 处方号非空且去重 | 重复号 EX-PRV-200 |
| 不合理 → reason 非空白 | EX-PRV-203 |
| 已发药 | EX-PRV-201 |
| 无有效点评 | 已有 → EX-PRV-202 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-PRV-101 | 未登录 |
| EX-PRV-200 | 参数非法 |
| EX-PRV-201 | 未发药 |
| EX-PRV-202 | 已存在有效点评（领域一对一） |
| EX-PRV-203 | 原因必填 |

**Mapper 方法签名**

- `toCreateReviewsCommand(BatchCreateReviewsRequest request, Long reviewerId, Long operatorId, LocalDateTime reviewedAt, List<CreatePrescriptionReviewItemCommand> items): CreatePrescriptionReviewsCommand`
- `toBatchCreateResponse(List<Long> ids): BatchCreateReviewsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 未登录 → EX-PRV-101；兼作 reviewerId/operatorId |
| 2 | Gate | `OutpatientPharmacyPrescriptionReviewSupport#assertBatchCreateRequest` | items/reasonable/reason | 非法 → EX-PRV-200/203 |
| 3 | Pre | `PrescriptionReviewQueryService#mapActiveByPrescriptionNos` | 入参处方号 | 若 Map 非空命中任一号 → EX-PRV-202（已点评不可选） |
| 4 | Pre | `OutpatientPharmacyPrescriptionReviewDispenseGate#assertDispensedOutpatientPrescriptions` | 处方号列表 | 内部：对各号 `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines`（`prescriptionNo` 精确 + 门诊已签发口径）取 medReqId；再 `MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum(COMPLETED)` 求交；任一方无命中 → EX-PRV-201 |
| 5 | Cmd | `PrescriptionReviewDomainService#createReviews(CreatePrescriptionReviewsCommand)` | Mapper 装配命令 | **写库点**（逐条 INSERT + 每条 Created 事件）；任一条失败整批回滚 |
| 6 | Asm | `Mapper#toBatchCreateResponse` | 返回 id 列表 | — |

**DispenseGate 内部读序列（步骤 4 展开，均属 Pre）**

| 子序 | 调用 | 说明 |
| --- | --- | --- |
| 4.1 | `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines` | 按 `prescriptionNo` 精确过滤（Query **无** `outpatientAmbOnly` 字段；已签发门诊口径由领域方法内置）；取本方药品申请 id（不取/不落 busNo） |
| 4.2 | `MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum` | 入参 `DispenseStatus.COMPLETED`；与 4.1 id 求交 |

---

### 3.6 IF-PRV-05 `revokeReview`（撤销点评）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/revokeReview` |

**Request `RevokeReviewRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 条件 | 点评主键；与 `prescriptionNo` 二选一，**优先 id** |
| `prescriptionNo` | `String` | 条件 | 处方号锚点 |

**Response `RevokeReviewResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 固定 true（失败走异常） |

**关键校验**：id 与 prescriptionNo 至少其一非空。

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-PRV-101 | 未登录 |
| EX-PRV-200 | 锚点均空 |
| EX-PRV-204 | 无有效点评（领域 NOT_FOUND） |

**Mapper 方法签名**

- `toRevokeCommand(RevokeReviewRequest request, Long operatorId): RevokePrescriptionReviewCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 未登录 → EX-PRV-101 |
| 2 | Gate | `OutpatientPharmacyPrescriptionReviewSupport#assertRevokeAnchor` | id / prescriptionNo | 均空 → EX-PRV-200 |
| 3 | Pre | `PrescriptionReviewQueryService#getById` | 当 id 非空 | **若〔id 非空〕执行本步骤**；确认有效行存在，否则领域/本步 → EX-PRV-204；**若〔仅 prescriptionNo〕跳至步骤 4** |
| 4 | Pre | `PrescriptionReviewQueryService#findActiveByPrescriptionNo` | prescriptionNo | **若〔id 为空且 prescriptionNo 非空〕执行本步骤**；empty → EX-PRV-204；**若〔已走步骤 3〕跳过** |
| 5 | Cmd | `PrescriptionReviewDomainService#revokeReview(RevokePrescriptionReviewCommand)` | id 优先 | **写库点**（软删 + Revoked 事件） |
| 6 | Asm | — | `success=true` | 无 Mapper 亦可直接 new Response |

---

### 3.7 IF-PRV-06 `createAutoReviewAfterDispense`（发药完成后自动点评）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/createAutoReviewAfterDispense` |

**Request `CreateAutoReviewAfterDispenseRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `prescriptionNos` | `List<String>` | 是 | 本次核发完成涉及的处方号（去重后非空） |
| `dispenserId` | `Long` | 是 | 发药人（点评人）；通常=当前发药操作员 |
| `autoReviewEnabled` | `Boolean` | 是 | 开关；`false` 时直接空成功（兼容网关直调） |

**Response `CreateAutoReviewAfterDispenseResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `createdIds` | `List<Long>` | 新建 id |
| `createdCount` | `Integer` | 新建条数 |
| `skippedCount` | `Integer` | 因已有有效点评而跳过的条数 |
| `softFailed` | `Boolean` | **true**=点评软失败（未完整写入）；**false**=点评侧正常。失败时本接口**正常返回**且**不抛异常**，避免连带回滚发药（BR-PRV-A11） |

**关键校验**：`autoReviewEnabled=true` 时 prescriptionNos/dispenserId 必填；校验失败同样走 softFailed，不抛。

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-PRV-205 | **仅日志语义**：点评路径任一步失败时打 warn 并带此码说明；**响应不抛 BusinessException**，`softFailed=true` |

**Mapper 方法签名**

- `toCreateReviewsCommandForAuto(List<CreatePrescriptionReviewItemCommand> items, Long dispenserId, Long operatorId, LocalDateTime reviewedAt): CreatePrescriptionReviewsCommand`
- `toAutoReviewResponse(List<Long> createdIds, int skippedCount, boolean softFailed): CreateAutoReviewAfterDispenseResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 0 | Gate | — | `autoReviewEnabled` | **若〔false/空请求〕Asm 空成功 softFailed=false 并结束** |
| 1～6 | Pre/Cmd | 见下「开启后主路径」 | — | **整段包在 try**；任一步 `RuntimeException` → 步骤 7 |
| 7 | Asm | `Mapper#toAutoReviewResponse(..., softFailed=true)` | 空 ids | **catch 唯一出口**：打 EX-PRV-205 日志，**禁止 rethrow** |

**开启后主路径（try 内）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | dispenserId 作点评人/操作人，或 `CurrentPractitionerResolver#requirePractitionerId` | 无 dispenserId 时补登录医务人员 |
| 2 | Gate | `Support#assertAutoReviewRequest` | nos/dispenserId |
| 3 | Pre | `Support#filterPrescriptionNosWithoutActiveReview` | skippedCount；空列表 → 步骤 6 成功返回 |
| 4 | Cmd | `PrescriptionReviewDomainService#createReviews` | 合理点评写库；业务号领域 PRV 日序自生成 |
| 5 | Asm | `Mapper#toAutoReviewResponse(..., softFailed=false)` | 成功 |
| 6 | Asm | `Mapper#toAutoReviewResponse(空, skipped, false)` | **仅当步骤 3 过滤后为空** |

---

### 3.8 IF-PRV-07 `mapActiveReviewsByPrescriptionNos`（按处方号批量映射有效点评）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientPharmacyPrescriptionReview/mapActiveReviewsByPrescriptionNos` |

**Request `MapActiveReviewsByPrescriptionNosRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `prescriptionNos` | `List<String>` | 是 | 可空列表（空则空 Map）；建议 ≤ 200 |

**Response `MapActiveReviewsByPrescriptionNosResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `reviewsByPrescriptionNo` | `Map<String, PrescriptionReviewActiveDto>` | 仅含有有效点评的处方号 |

**DTO `PrescriptionReviewActiveDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 点评主键 |
| `prescriptionNo` | `String` | 处方号 |
| `busNo` | `String` | 点评业务号（PRV） |
| `statusEnum` | `String` | `REVIEWED` |
| `statusText` | `String` | 已点评 |
| `reasonableFlag` | `Boolean` | 是否合理 |
| `reasonableText` | `String` | 合理/不合理 |
| `reason` | `String` | 原因 |
| `remark` | `String` | 备注 |
| `reviewerId` | `Long` | 点评人 |
| `reviewerName` | `String` | 点评人姓名 |
| `reviewedAt` | `String` | 点评时间 |

**失败码**：EX-PRV-101、EX-PRV-103

**Mapper 方法签名**

- `toActiveDto(PrescriptionReviewQueryModel model, String reviewerName): PrescriptionReviewActiveDto`
- `toMapActiveResponse(Map<String, PrescriptionReviewActiveDto> map): MapActiveReviewsByPrescriptionNosResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 未登录 → EX-PRV-101；进程内可复用调用方会话 |
| 2 | Pre | `PrescriptionReviewQueryService#mapActiveByPrescriptionNos` | prescriptionNos | 仅读 |
| 3 | Pre | `PractitionerQueryService#mapNamesByIds` | Map 内 reviewerId | 姓名 |
| 4 | Asm | `Mapper#toActiveDto` | 逐条 | — |
| 5 | Asm | `Mapper#toMapActiveResponse` | 结果 Map | 仅读路径 |

---

## 4. QueryService 与领域事件

### 4.1 QueryService 查询语义

| QueryService 方法 | 查询语义 | 读侧刷新 |
| --- | --- | --- |
| `PrescriptionReviewQueryService#pageReviews` | 有效行；`reviewed_at` 轴；PENDING 空页；关键字 like | 创建/撤销提交后即时可见 |
| `PrescriptionReviewQueryService#mapActiveByPrescriptionNos` | IN 处方号 → 有效 Map | 同上 |
| `PrescriptionReviewQueryService#findActiveByPrescriptionNo` | 单号有效 Optional | 同上 |
| `PrescriptionReviewQueryService#getById` | 主键强加载有效行 | 同上 |
| `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines` | 已签发门诊药品行；本场景用 `prescriptionNo` 精确筛 | 只读 Gate |
| `MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum` | 按发放状态反查药品申请 id | 只读 Gate |

### 4.2 领域事件

| 事件 | 触发 IF / Cmd | 消费方 |
| --- | --- | --- |
| `PrescriptionReviewCreatedEvent` | IF-PRV-04 步骤 6；IF-PRV-06 步骤 7 | 审计/读侧刷新；PHA-005 下次 enrichment 见已点评 |
| `PrescriptionReviewRevokedEvent` | IF-PRV-05 步骤 5 | 审计/读侧刷新；展示回待点评 |

本应用服务**不**订阅外域事件；自动点评由 PHA-001 同步调用。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-PRV-04 | `batchCreateReviews` | §3.5 序号 6 | `PrescriptionReviewDomainService#createReviews` | 逐条 INSERT `cus_prescription_review` |
| IF-PRV-05 | `revokeReview` | §3.6 序号 5 | `PrescriptionReviewDomainService#revokeReview` | 软删有效行 |
| IF-PRV-06 | `createAutoReviewAfterDispense` | §3.7 序号 7 | `PrescriptionReviewDomainService#createReviews` | 逐条 INSERT（合理） |
| IF-PRV-01～03、07 | 读 | — | — | 无写库 |

### 5.2 事务边界 / 失败回滚

| IF | 事务 | 失败回滚 |
| --- | --- | --- |
| IF-PRV-04 | 单事务覆盖 Pre 后 Cmd | 任一条领域失败整批回滚，无部分成功 |
| IF-PRV-05 | 单事务 | 撤销失败无写 |
| IF-PRV-06 | 写 IF 依赖 AppServiceAspect UoW；**点评失败不抛异常**（`softFailed=true`）；发药应在核发成功后再调 | 失败**绝不**回滚发药；PHA-001 仅读 softFailed 做软提示 |
| 读 IF | 无写事务 | — |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 有效一对一防重复点评 | 领域 `existsByPrescriptionNoAndIsDeletedFalse` + Pre `mapActiveByPrescriptionNos` | §3.5 序号 3、6 |
| 自动点评跳过已点评 | `filterPrescriptionNosWithoutActiveReview` | §3.7 序号 4 |
| 撤销无目标失败 | 领域 NOT_FOUND | §3.6 序号 3～5 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 同方并发创建冲突 | DB 部分唯一/存在性校验 → EX-PRV-202 | §3.5 序号 6 |
| 批量同事务 | 写 IF 依赖 AppServiceAspect UoW，业务代码禁止声明 @Transactional | §3.5 全 Cmd |

### 6.3 重试与容错

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 自动点评软失败 | PHA-001 catch EX-PRV-205 | §3.7 序号 7；跨 AS |
| 导出触顶截断 | `EXPORT_MAX_ROWS` + `truncated` | §3.4 序号 5、10 |

### 6.4 数据脱敏

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 本期列表无证件号字段 | ListRowDto / ExportRowDto | §3.3 / §3.4 DTO |

### 6.5 审计日志

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 写操作框架审计 | AppService 方法级审计注解（工程惯例） | §3.5～3.7 Cmd |
| 事件 beforeCommit | 领域 `IDomainEventPublisher` | Created/Revoked |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 测试层级 | 自动化归属 | 优先级 | 期望 |
| --- | --- | --- | --- | --- |
| init 选项含 PENDING/REVIEWED | API | App | P0 | 下拉齐全 |
| 已点评分页可见字段全集 | API | App | P0 | 含原因/备注/点评人 |
| PENDING 筛选空页 | API | App | P0 | total=0 |
| 导出列=列表 + exportColumns | API | App | P0 | 列序可控 |
| 已发药批量合理点评 | API | App | P0 | 创建成功、enrichment 可见 |
| 不合理无原因拒绝 | API | App | P0 | EX-PRV-203 |
| 未发药拒绝 | API | App | P0 | EX-PRV-201 |
| 已点评再评拒绝 | API | App | P0 | EX-PRV-202 |
| 撤销后可重评 | API | App | P0 | 软删 + 新 id |
| 自动点评合理空原因 | API | App | P0 | reviewer=dispenser |
| 自动点评跳过已有 | API | App | P1 | skippedCount>0 |
| mapActive 缺席待点评号 | API | App | P0 | Map 无键 |

### 7.2 非功能测试

| 用例 | 测试层级 | 自动化归属 | 优先级 | 期望 |
| --- | --- | --- | --- | --- |
| 分页 P95≤1500ms | 性能 | 手工/压测 | P1 | 月窗常规数据 |
| 导出≤5000 行 | 性能 | App | P1 | truncated 标记 |

### 7.3 查询结果一致性

| 用例 | 测试层级 | 自动化归属 | 优先级 | 期望 |
| --- | --- | --- | --- | --- |
| 创建后 page/map 立即可见 | 集成 | App | P0 | 同事务提交后 |
| 撤销后 map 无键 | 集成 | App | P0 | 展示待点评 |

### 7.4 集成测试

| 用例 | 测试层级 | 自动化归属 | 优先级 | 期望 |
| --- | --- | --- | --- | --- |
| PHA-001 发药成功 + 开关 → IF-PRV-06 | 集成 | App | P0 | 发药不因点评失败回滚 |
| PHA-005 列表调用 IF-PRV-07 | 集成 | App | P0 | 列表点评列 enrichment |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 本期 | 无额外测试缺口；并发唯一约束依赖库索引时补集成用例 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-08-29 | 初版目标态全量契约：IF-PRV-01～07；HTTP `outpatientPharmacyPrescriptionReview`；领域白名单与发药 Gate；导出 `exportColumns`；跨 AS 协作 PHA-001/005；§2.3 枚举锚点（含目标态 Constants） |
| v1.0.1 | 2026-08-29 | 与成码对齐：`exportColumns`→`List<ExcelExportColumnDto>`；点评人姓名→`mapNamesByIds`；Gate 查询去掉不存在的 `outpatientAmbOnly`；写路径事务口径维持 AppServiceAspect UoW |
| v1.0.2 | 2026-08-29 | **BR-PRV-A11 加固**：IF-PRV-06 点评失败一律 `softFailed=true` 正常返回、禁止向上抛异常，避免连带回滚发药 |
| v1.2 | 2026-08-29 | **口径对齐成码**：`bus_no`=PRV 日序自生成（删除 resolveBusNo/快照）；Gate=`CurrentPractitionerResolver`；点评记录页 UI 只读；IF-PRV-04/06 子项仅处方号 |
| v1.3 | 2026-08-31 | 列表/导出对齐处方点评工作表列；`ExportReviewsRequest` 带 pageNo/pageSize；`ListEnricher` 方级指标；`MedicationRequestQueryService#listMedicationRequestListModelsByPrescriptionNos` |
| v1.4 | 2026-08-31 | 筛选增加开方时间 `openedDateFrom/To`：应用层预解析方号 IN（`listDistinctSignedPrescriptionNosByOpenedTimeWindow`） |
