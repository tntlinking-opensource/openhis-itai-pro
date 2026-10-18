# 门诊医生站 · 就诊管理 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-001-SDD  
> **版本**：v1.3.10 | **日期**：2026-08-20 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医生站 · 就诊管理应用服务 |
| **边界** | 编排 `Encounter` 读校验与写命令、`OutpatientEncounterContextQueryModel` `EncounterQueryService`查询；不在应用层实现聚合内部不变式 |
| **编排权威** | `AS-OP-DOR-001-门诊医生站就诊管理.md`（v1.19+）§2 + §4 |
| **方法权威** | `DM-ADM-002-Encounter-就诊.md` §6.1 / §6.2；`DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` §3 |
| **严格对齐** | 本 SDD 成码范围 **IF-DOR-01～04、IF-DOR-06**；**IF-DOR-05（办理住院）** 已迁至 **`AS-OP-DOR-005-SDD`**（独立包 `inpatientadmission`） |
| **实现阶段口径** | **目标态全量契约**。IF-DOR-01～04、06 与现有 `EncounterDomainService` / `EncounterQueryService` 可对齐落地；住院申请应用编排、HTTP、DTO 以 **`AS-OP-DOR-005` / `AS-OP-DOR-005-SDD`** 为准（§3.6 仅作历史对照） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 入出参与 AS、DM、DM §6.2一致；枚举存 **String code**（与 `EncounterStatus` 等对齐） |
| **QueryService口径** | IF-DOR-01 查询经 `EncounterQueryService`；分页与过滤口径以 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` §3 **Q-OPI-001** / §3.4 为准 |
| **事务口径** | 遵循项目统一事务约定；单就诊单 Cmd 接口默认单聚合或单服务方法同一事务 |
| **HTTP 接入口径** | 工程当前由 `whale-web` 的 `AppServiceControllerRegistrar` 动态注册：`POST /api/app/doctorStationEncounterManage/{methodName}`，请求体 JSON 与方法形参一致（`serviceName` 由 `DoctorStationEncounterManageAppService` 推导为 `doctorStationEncounterManage`） |
| **业务异常码口径** | 本文历史表中的 `DOR-BIZ-*` 为**语义别名**；实现层统一抛出 `com.zxplat.framework.core.application.exception.BusinessException`，`errorCode` 取 `com.zxplat.domain.share.constants.ErrorCodeConstants.ErrorCode` 中已定义常量（主要为 `ENCOUNTER_*`），见各 IF「失败码口径」映射表 |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/doctorstation/encountermanage/
  service/
    DoctorStationEncounterManageAppService.java
  request/
    PageOutpatientInfoForDoctorStationRequest.java
    StartOutpatientReceptionRequest.java
    MarkOutpatientLeaveRequest.java
    CompleteOutpatientEncounterRequest.java
  response/
    PageOutpatientInfoForDoctorStationResponse.java
    StartOutpatientReceptionResponse.java
    MarkOutpatientLeaveResponse.java
    CompleteOutpatientEncounterResponse.java
  dto/
    OutpatientInfoItemDto.java
  mapper/
    DoctorStationEncounterManageMapper.java
    DoctorStationEncounterManageMapperImpl.java
```

> **住院申请（IF-DOR-05）**：不再落在 `encountermanage`；见 **`outpatient/doctorstation/inpatientadmission`** 与 **`AS-OP-DOR-005-SDD`** §2。

### 2.2 依赖注入表（构造器注入）

| 依赖 | 用途 |
| --- | --- |
| `EncounterContextReader` | IF-DOR-01：`pageOutpatientEncounterContexts`（`application.shared.encounter`） |
| `ChargeItemQueryService` | IF-DOR-01 分页后对**本页就诊**批量 `listChargeItemsByEncounterIds`，内存汇总 `totalAmount`/`paidAmount`/`refundAmount`（不经支付流水） |
| `ContractQueryService` | IF-DOR-01：列表页 **`feeNatureOptions` 固定空列表**（医嘱 init 按就诊经 `ContractFeeNatureOptionLoader` 加载）；行内 **`contractDisplay`** 经 **`EncounterContractDisplayAssembler`** |
| `EncounterContractDisplayAssembler` | IF-DOR-01：本页就诊批量解析优先合同名（有医保账户优先，**BR-DOR-010**） |
| `AccountQueryService` | IF-DOR-01：`mapPreferredContractNoByEncounterIds`（医保户优先于登记个人现金户） |
| `EncounterQueryService` | IF-DOR-02～04、06 写前加载、完诊前校验、取消接诊诊断存在性（`mustGetEncounter`、`checkOutpatientCompletable`、`listEncounterDiagnoses` 等） |
| `EncounterDomainService` | IF-DOR-02～04、06 接诊、暂离、完诊、取消接诊 |
| `MedicationRequestQueryService` | IF-DOR-06 Pre：本诊医生开立药嘱是否仍存在 |
| `ServiceRequestQueryService` | IF-DOR-06 Pre：本诊医生开立诊疗/申请单是否仍存在 |
| `DeviceRequestQueryService` | IF-DOR-06 Pre：本诊医生开立耗材医嘱是否仍存在 |
| `EmrDocumentExtension` | IF-DOR-06 Pre：本诊 EMR 文书是否仍存在（未启用则跳过） |
| `PractitionerQueryService` | IF-DOR-02 写前按 `doctorId` 解析医师展示名，装配 `StartOutpatientReceptionCommand` 项上 `doctorName` 快照 |
| `DoctorStationEncounterManageMapper` | Request→`OutpatientEncounterPageQuery`/领域 Command、QueryService→Response/DTO 装配（见 §2.4） |
| `CurrentManagedOrganizationIdsResolver` | 会话机构（科室）用于 Facade 查询收窄与费用性质下拉（**B.6.5**） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002-Encounter-就诊` §2.3 状态机；§3.1 `adm_encounter.status_enum` | IF-DOR-01：`encounterStatus`**可空**——空则 `statusEnumIn=[REGISTERED,IN_PROGRESS,ON_HOLD,DISCHARGED]`；非空则 `statusEnum=encounterStatus.getCode()`；**须** `staffPractitionerId`→`doctorStationViewerPractitionerId`（**BR-DOR-009**）。IF-DOR-02～04 状态迁移；`OutpatientInfoItemDto.statusEnum` |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3.1 `adm_encounter.class_enum`；§4.1 | IF-DOR-02 Pre：`classEnum=AMB`；IF-DOR-05 住院 `IMP` |
| 入院来源（字典） | `RequestInpatientAdmissionFromOutpatientRequest.admitSourceCode` | `DM-ADM-002` §3.1 `adm_encounter.admit_source_code` | IF-DOR-05；**String code**，字典项以主数据/术语为准 |
| 展示 DTO 状态/类别等 | `OutpatientInfoItemDto.statusEnum` / `classEnum` / `genderEnum` / `triageLevelEnum` | `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` §2 字段（投影自 `adm_encounter` 等，与 **DM-ADM-002** §3.1 同源） | IF-DOR-01；持久化枚举语义以 Encounter/Patient DM 为准 |
| 性别编码 | `OutpatientInfoItemDto.genderEnum`（String，**`AdministrativeGender#getCode()`**；应用层**不做** sortOrder/整型转换） | `DM-ADM-001-Patient-患者` §3 `gender_enum`；QueryService见 **OutpatientEncounterContextQueryModel** §2、§4.2 | 与QueryService ``adm_encounter` + `OutpatientEncounterContextQueryModel`.gender_enum` 直透一致 |
| 行政性别枚举 | `com.zxplat.domain.share.enums.AdministrativeGender` | 同上；与患者主档、挂号事件口径一致 | MALE / FEMALE / UNKNOWN |
| 业务异常码（工程常量） | `com.zxplat.domain.share.constants.ErrorCodeConstants.ErrorCode`（如 `ENCOUNTER_*`） | `DM-ADM-002` §6 方法与状态门禁；与 AS 异常语义映射见本文 §1.2、各 IF「失败码口径」 | 非独立业务枚举表；**语义**以 DM/AS 为准 |

### 2.4 领域方法与 Mapper 白名单

**领域查询 / 写服务**

| 领域 / 聚合 | 类型 | 方法 | 用于 IF |
| --- | --- | --- | --- |
| 就诊上下文 | `EncounterContextReader` | `pageOutpatientEncounterContexts` | IF-DOR-01（**已落地**；替代直连 `EncounterQueryService` 分页） |
| ChargeItem（读） | `ChargeItemQueryService` | `listChargeItemsByEncounterIds` | IF-DOR-01（本页就诊批量费项；应用层汇总三金额） |
| Contract（读） | `ContractQueryService` | `listAllContractsByOrgId` | IF-DOR-01（机构费用性质全量简项 → `feeNatureOptions`） |
| Practitioner（读） | `PractitionerQueryService` | `findById` | IF-DOR-02（接诊医师主数据；不存在则拒绝 Cmd） |
| Encounter（读） | `EncounterQueryService` | `mustGetEncounter` | IF-DOR-02～05、06 |
| Encounter（读） | `EncounterQueryService` | `checkOutpatientCompletable` | IF-DOR-04 |
| Encounter（读） | `EncounterQueryService` | `listEncounterDiagnoses` | IF-DOR-06（取消接诊诊断门禁） |
| Encounter（读） | `EncounterQueryService` | `queryEncounterProjections` | IF-DOR-05（辅助粗筛，**非** BR-DOR-007 唯一依据） |
| Encounter（写） | `EncounterDomainService` | `startOutpatientReception` | IF-DOR-02 |
| Encounter（写） | `EncounterDomainService` | `markOutpatientLeave` | IF-DOR-03 |
| Encounter（写） | `EncounterDomainService` | `completeOutpatientEncounter` | IF-DOR-04 |
| Encounter（写） | `EncounterDomainService` | `cancelOutpatientReception` | IF-DOR-06 |
| MedicationRequest（读） | `MedicationRequestQueryService` | `listMedicationRequestsByEncounterForDoctorStation` | IF-DOR-06 |
| ServiceRequest（读） | `ServiceRequestQueryService` | `listServiceRequestsByGenerateSource` | IF-DOR-06 |
| DeviceRequest（读） | `DeviceRequestQueryService` | `listDeviceRequestsByEncounterForDoctorStation` | IF-DOR-06 |
| EMR（读 SPI） | `EmrDocumentExtension` | `listByEncounter` / `isEnabled` | IF-DOR-06 |
| Encounter（写） | `EncounterDomainService` | `requestInpatientAdmission` | **AS-OP-DOR-005**（**非**本 AppService） |

**Mapper（Request/Query/Cmd 映射 + 响应 Asm，与工程接口一致）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `DoctorStationEncounterManageMapper` | `toOutpatientEncounterPageQuery(PageOutpatientInfoForDoctorStationRequest)` | IF-DOR-01（Pre；缺省四态并集写 `statusEnumIn`；`doctorStationViewerPractitionerId`←`staffPractitionerId`，**BR-DOR-009**） |
| `DoctorStationEncounterManageMapper` | `toOutpatientInfoItemDto(OutpatientEncounterContextQueryModel)` | IF-DOR-01（行映射；由 `toPageOutpatientInfoForDoctorStationResponse` 内部调用） |
| `DoctorStationEncounterManageMapper` | `toPageOutpatientInfoForDoctorStationResponse(Page<OutpatientEncounterContextQueryModel>)` | IF-DOR-01（单参委托双参，`totalsByEncounterId` 为 null） |
| `DoctorStationEncounterManageMapper` | `toPageOutpatientInfoForDoctorStationResponse(Page<OutpatientEncounterContextQueryModel>, Map<Long, OutpatientInfoListFinancialTotals> totalsByEncounterId)` | IF-DOR-01（Asm；以 `OutpatientInfoListFinancialTotals` 覆盖 `OutpatientInfoItemDto` 三金额） |
| `DoctorStationEncounterManageMapper` | `toStartOutpatientReceptionCommand(StartOutpatientReceptionRequest, String doctorDisplayName)` | IF-DOR-02（第二参为 Pre 步解析的医师姓名快照，写入 Item 的 `doctorName`） |
| `DoctorStationEncounterManageMapper` | `toStartOutpatientReceptionResponse(Long, boolean)` | IF-DOR-02 |
| `DoctorStationEncounterManageMapper` | `toMarkOutpatientLeaveCommand(MarkOutpatientLeaveRequest)` | IF-DOR-03 |
| `DoctorStationEncounterManageMapper` | `toMarkOutpatientLeaveResponse(Long, boolean)` | IF-DOR-03 |
| `DoctorStationEncounterManageMapper` | `toCompleteOutpatientEncounterCommand(CompleteOutpatientEncounterRequest)` | IF-DOR-04 |
| `DoctorStationEncounterManageMapper` | `toCompleteOutpatientEncounterResponse(Long, boolean)` | IF-DOR-04 |
| `DoctorStationEncounterManageMapper` | `toCancelOutpatientReceptionCommand(CancelOutpatientReceptionRequest)` | IF-DOR-06 |
| `DoctorStationEncounterManageMapper` | `toCancelOutpatientReceptionResponse(Long, boolean)` | IF-DOR-06 |
| `DoctorStationEncounterManageMapper` | `toRequestInpatientAdmissionCommand(RequestInpatientAdmissionFromOutpatientRequest, Long resolvedPatientId)` | IF-DOR-05 |
| `DoctorStationEncounterManageMapper` | `toRequestInpatientAdmissionFromOutpatientResponse(Long, boolean)` | IF-DOR-05 |

### 2.5 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向（白名单 -> §3 序列） | PASS | §2.4 所列方法均在至少一个 IF 编排序列中出现 |
| 反向（§3 序列 -> 白名单） | PASS | 各 IF 的 Cmd/Pre/Query/Mapper 调用均可在 §2.4 找到同名条目 |
| 互斥步骤对检查 | PASS | IF-DOR-05 步骤 3 与 4 条件已按 §3.6 写明 |

---

## 3. 按接口 ID 命名展开

§3 导语：**先读完各 IF 的「领域编排序列」**，按序号从上到下执行；**Pre / Gate 任一失败不得进入 Cmd**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-DOR-01 | `pageOutpatientInfoForDoctorStation` | 分页查询就诊患者列表（含本科室机构维度**费用性质下拉** `feeNatureOptions`） | `POST /api/app/doctorStationEncounterManage/pageOutpatientInfoForDoctorStation` |
| IF-DOR-02 | `startOutpatientReception` | 医生接诊 | `POST /api/app/doctorStationEncounterManage/startOutpatientReception` |
| IF-DOR-03 | `markOutpatientLeave` | 患者暂离 | `POST /api/app/doctorStationEncounterManage/markOutpatientLeave` |
| IF-DOR-04 | `completeOutpatientEncounter` | 门诊完诊 | `POST /api/app/doctorStationEncounterManage/completeOutpatientEncounter` |
| IF-DOR-06 | `cancelOutpatientReception` | 取消接诊（医生同意退号） | `POST /api/app/doctorStationEncounterManage/cancelOutpatientReception` |

> **说明**：IF-DOR-01～04、06 的 HTTP 已按 `doctorStationEncounterManage` 动态路由填写（见 §1.2）。**IF-DOR-05 办理住院** 已迁至 **`DoctorStationInpatientAdmissionAppService`**（**`AS-OP-DOR-005-SDD`** §3.1），**不**列入本表。IF-DOR-01 响应字段 **`feeNatureOptions`** 为 `PageOutpatientInfoForDoctorStationResponse` 内嵌下拉，**非**独立 HTTP 方法。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁（含 `*QueryService`、`EncounterQueryService` 等） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`EncounterDomainService#...`，持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | `DoctorStationEncounterManageMapper` 纯 DTO 装配，无领域不变式 | — |

**记法**：`EncounterQueryService#mustGetEncounter` 表示在 `DoctorStationEncounterManageAppService` 中对**注入的该类型 Bean** 调用该方法（运行时 Bean 名以实现为准）。

应用读侧须写为：**`EncounterContextReader#pageOutpatientEncounterContexts`**（`OutpatientEncounterPageQuery` → `Page<OutpatientEncounterContextQueryModel>`；**Q-OPI-001**；过滤/排序细则见 `DM-ADM-002 §6.2 OutpatientEncounterContextQueryModel` §3 与 **§3.4**）。

---

### 3.2 IF-DOR-01 `pageOutpatientInfoForDoctorStation`（分页查询就诊患者列表）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationEncounterManageAppService#pageOutpatientInfoForDoctorStation` |
| **HTTP** | `POST /api/app/doctorStationEncounterManage/pageOutpatientInfoForDoctorStation` |

**Request `PageOutpatientInfoForDoctorStationRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterStatus` | `EncounterStatus` | N | 与QueryService `status_enum` 一致；**不传**时后端映射为 **`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`** 多值 IN（`statusEnumIn`）；**传了**则单状态过滤，由前端按 Tab 传入 |
| `keyword` | String | N | 模糊关键字；下推 **`OutpatientEncounterPageQuery.keyword`**（继承 `AbstractHealthPageRequest`） |
| `startDate` | LocalDateTime | N | 日期范围起（挂号/接诊日） |
| `endDate` | LocalDateTime | N | 日期范围止 |
| `pageNo` | Integer | Y | 页码，从 1 开始（**B.10.11**） |
| `pageSize` | Integer | Y | 页大小 |
| `staffPractitionerId` | Long | **Y** | 当前登录医师主键；映射为 `OutpatientEncounterPageQuery.doctorStationViewerPractitionerId`（**BR-DOR-009** 可见性） |

> **科室范围**：**禁止** Request 传 `organizationId`；由 **`CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery`** 写入 `OutpatientEncounterPageQuery`（与 **AS-IP-DOR-001**、**B.10.11 / B.6.5** 一致）。

**Response `PageOutpatientInfoForDoctorStationResponse`**（继承 **`PagedResult<OutpatientInfoItemDto>`**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<OutpatientInfoItemDto>` | 当前页行数据 |
| `totalCount` | Long | 总条数 |
| `pageNo` | Integer | 当前页（1-based） |
| `pageSize` | Integer | 页大小 |
| `feeNatureOptions` | `List<OptionDto>` | **列表页固定 `[]`**（医嘱开立 init 按就诊经 `ContractFeeNatureOptionLoader` 加载；历史「全量机构下拉」叙述废止） |

**DTO `OutpatientInfoItemDto`（与 `OutpatientEncounterContextQueryModel` `EncounterQueryService` §2 展示字段对齐）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 ID（主锚点） |
| `encounterBusNo` | String | N | 就诊业务号 |
| `classEnum` | Integer | N | 就诊类别（门诊，与来源表一致） |
| `statusEnum` | String | N | 就诊状态 code |
| `statusEnumText` | String | N | 状态展示文本 |
| `patientId` | Long | N | 患者 ID |
| `patientName` | String | N | 患者姓名 |
| `genderEnum` | String | N | 性别编码（**`AdministrativeGender#getCode()`**：MALE/FEMALE/UNKNOWN；与QueryService直透，不做数值映射） |
| `genderEnumText` | String | N | 性别展示（Mapper 由 `AdministrativeGender#getDisplay()` 解析） |
| `birthDate` | LocalDateTime | N | 生日 |
| `ageText` | String | N | 年龄文本 |
| `idCard` | String | N | 证件号 |
| `phone` | String | N | 电话 |
| `address` | String | N | 患者地址全文（主档 `Patient.address`；医生站卡片回显；空则前端展示「-」） |
| `organizationId` | Long | N | 科室 ID |
| `organizationName` | String | N | 科室名称 |
| `doctorId` | Long | N | 接诊医生 ID |
| `doctorName` | String | N | 接诊医生姓名 |
| `serviceTypeId` | Long | N | 服务类型 ID |
| `serviceTypeName` | String | N | 服务类型名称（号别，**非**费用性质） |
| `contractDisplay` | String | N | 费用性质展示名（**BR-DOR-010**：有医保账户优先其合同名） |
| `startTime` | LocalDateTime | N | 开始时间 |
| `receptionTime` | LocalDateTime | N | 接诊时间 |
| `endTime` | LocalDateTime | N | 结束时间 |
| `displayOrder` | Integer | N | 当日序号 |
| `firstEnumText` | String | N | 初诊/复诊文本 |
| `totalAmount` | BigDecimal | N | 门诊费用总额；IF-DOR-01 以批量收费明细汇总**覆盖**QueryService行初值（未删明细 `total_price` 合计） |
| `paidAmount` | BigDecimal | N | 已支付金额；IF-DOR-01 以明细状态 **SETTLED** 汇总覆盖 |
| `refundAmount` | BigDecimal | N | 已退款金额；IF-DOR-01 以明细状态 **REFUNDED** 汇总覆盖 |
| `diagnoses` | `List<EncounterDiagnosisRowDto>` | N | 诊断列表（读模型 `diagnosisJson` 经 **`DoctorStationDiagnosisMapper#parseDiagnosisJson`** 解析；**禁止**对外下发原始 JSON 串） |
| `triageLevelEnum` | Integer | N | 分诊级别（分诊场景；门诊医生站列表可空） |
| `triageTime` | LocalDateTime | N | 分诊时间 |

**关键校验**

- **`encounterStatus` 为空**：`OutpatientEncounterPageQuery.statusEnumIn` = `REGISTERED`、`IN_PROGRESS`、`ON_HOLD`、`DISCHARGED`（码表与 `EncounterStatus#getCode()` 一致）；`statusEnum=null`；`doctorStationViewerPractitionerId`←`staffPractitionerId`（**BR-DOR-009** 按各行状态组合判定可见性）。
- **`encounterStatus` 非空**：`statusEnumIn=null`；`statusEnum=encounterStatus.getCode()` 等值过滤；`doctorStationViewerPractitionerId`←`staffPractitionerId`。
- **BR-DOR-009 读侧谓词**（`EncounterContextCriteriaSupport#buildDoctorStationViewerVisibilityPredicate`，DB 侧）：**`DISCHARGED`** 科室共享；**`REGISTERED`/`TRIAGED`**——**无活动 `REGISTRATION_DOCTOR` 且无活动 `ADMITTER`** 则科室共享，否则 **`REGISTRATION_DOCTOR` 或（历史兼容）`ADMITTER` 须匹配 `doctorStationViewerPractitionerId`**；**`IN_PROGRESS`/`ON_HOLD`**——**仅活动 `ADMITTER` 匹配当前医师**（挂到科室与挂到医生同口径）；参与者子查询须 **`is_deleted=false`**。
- **写侧对齐**（`EncounterDomainService#registerOutpatientEncounter`）：指定医师时写入活动 **`REGISTRATION_DOCTOR`**，**禁止**开立阶段写 `ADMITTER`（`ADMITTER` 仅 **`startOutpatientReception`**）。
- **排序**（**BR-DOR-002**，落点 `EncounterContextCriteriaSupport#resolveOutpatientListSort` / `#doctorStationUnionOrderSpecification`）：
  - **待诊**（单选 `REGISTERED`/`TRIAGED`，及约定的 `PLANNED` 单查）：`startTime` **升序**，次键 `id` 升序（先挂号先看诊）。
  - **在诊/暂离**（单选 `IN_PROGRESS`/`ON_HOLD`）：`receptionTime` **降序**，次键 `id` 降序（后接诊在上）。
  - **缺省多状态并集**：Criteria CASE 分桶——待诊桶 → 在诊/暂离桶 → 其余；桶内分别按上述业务键；前端按 Tab 过滤后相对序仍成立。
  - **诊毕等其余单选**：`startTime` 降序。

**失败码口径**

| 语义（别名） | 工程 `ErrorCodeConstants.ErrorCode` | 场景 | 用户提示方向 |
| --- | --- | --- | --- |
| `DOR-BIZ-400` | `ENCOUNTER_INVALID_ARGUMENT`（`ENCOUNTER_001`） | 参数缺失/非法（含未传 `staffPractitionerId`） | 补全科室、分页、当前登录医师主键 |
| `DOR-SYS-500` | （运行时非业务异常或网关统一码） | 读库/基础设施失败 | 稍后重试（产品文案可对齐 EX-DOR-101） |

**Mapper 接口方法签名**

- `toOutpatientEncounterPageQuery(PageOutpatientInfoForDoctorStationRequest request): OutpatientEncounterPageQuery`
- `toPageOutpatientInfoForDoctorStationResponse(Page<OutpatientEncounterContextQueryModel> page): PageOutpatientInfoForDoctorStationResponse`
- `toPageOutpatientInfoForDoctorStationResponse(Page<OutpatientEncounterContextQueryModel> page, Map<Long, OutpatientInfoListFinancialTotals> totalsByEncounterId): PageOutpatientInfoForDoctorStationResponse`
- `toOutpatientInfoItemDto(OutpatientEncounterContextQueryModel row): OutpatientInfoItemDto`（供分页装配内部使用）

**领域编排序列（严格顺序；仅读路径）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验 | `pageNo`、`pageSize`、**`staffPractitionerId` 必填** | 缺失或非法即拒绝 |
| 2 | Pre | `DoctorStationEncounterManageMapper#toOutpatientEncounterPageQuery` → **`EncounterContextReader#pageOutpatientEncounterContexts`** | Request → `OutpatientEncounterPageQuery`（会话 `organizationId` 写入 Query；`encounterStatus==null` → 四态 `statusEnumIn`；`doctorStationViewerPractitionerId`←`staffPractitionerId`，**BR-DOR-009**） | 条件下推读侧，分页在 DB 完成 |
| 3 | Pre | `ChargeItemQueryService#listChargeItemsByEncounterIds` | ② 返回页内**去重**之 `encounterId` 列表 | 内存汇总 `Map<Long, OutpatientEncounterListFinancialTotals>` |
| 4 | Pre | `ContractQueryService#listAllContractsByOrgId` | **`CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery`**（会话机构；无机构键时不查合同） | 费用性质主数据；**无合同行时 []** |
| 5 | Asm | `DoctorStationEncounterManageMapper#toPageOutpatientInfoForDoctorStationResponse` | ② 的 `Page<OutpatientEncounterContextQueryModel>` + ③ 的 `totalsByEncounterId` | 无领域写；Mapper 以 totals **覆盖** 各行 DTO 三金额 |
| 6 | Asm | `DoctorStationEncounterManageAppService` 内将 ④ 映射为 `List<OptionDto>` 并 `setFeeNatureOptions` | ④ + ⑤ 的响应体 | 与 **AS-OP-DOR-003** 医嘱开立页解耦；映射实现见工程私有静态方法 `mapContractRowsToFeeNatureOptions` |

---

### 3.3 IF-DOR-02 `startOutpatientReception`（医生接诊）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationEncounterManageAppService#startOutpatientReception` |
| **HTTP** | `POST /api/app/doctorStationEncounterManage/startOutpatientReception` |

**Request `StartOutpatientReceptionRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 门诊就诊标识 |
| `doctorId` | Long | Y | 接诊医师（通常取当前登录医师） |
| `receptionTime` | LocalDateTime | N | 接诊时间，空则默认当前时间 |
| `directFromPlanned` | Boolean | N | 是否由计划直接接诊 |
| `operatorId` | Long | Y | 操作人（上下文注入） |

**Response `StartOutpatientReceptionResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊标识 |
| `success` | Boolean | 是否受理成功 |

**关键校验**

- 就诊须允许接诊（领域 `assertReceptionAllowed`，对齐 BR-DOR-003/004）。

**失败码口径**

| 语义（别名） | 工程 `ErrorCodeConstants.ErrorCode` | 场景 | 用户提示方向 |
| --- | --- | --- | --- |
| `DOR-BIZ-404` | `ENCOUNTER_NOT_FOUND`（`ENCOUNTER_002`） | 就诊不存在 | 刷新列表 |
| `DOR-BIZ-410` | `PRACTITIONER_NOT_FOUND` | 接诊医师不存在或已失效 | 对齐 EX-DOR-104，核对医师主键 |
| `DOR-BIZ-409` | `ENCOUNTER_INVALID_STATUS`（`ENCOUNTER_003`）或 `ENCOUNTER_RULE_CONFLICT`（`ENCOUNTER_005`） | 类别非门诊、状态不允许接诊 | 说明当前状态 |
| `DOR-BIZ-102` | `ENCOUNTER_RULE_CONFLICT` 等（以领域抛出为准） | 并发/版本冲突 | 对齐 EX-DOR-102，刷新后重试 |

**Mapper 接口方法签名**

- `toStartOutpatientReceptionCommand(StartOutpatientReceptionRequest request, String doctorDisplayName): StartOutpatientReceptionCommand`（`doctorDisplayName` 来自 `Practitioner#getName()`，映射至命令项 `doctorName`）
- `toStartOutpatientReceptionResponse(Long encounterId, boolean success): StartOutpatientReceptionResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验 | `encounterId`、`doctorId`、`operatorId` | 非法即拒绝 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long): EncounterDetailQueryModel` | `encounterId` | 存在性；应用层校验 `classEnum=AMB`（非门诊拒绝）；失败则 **不得 Cmd** |
| 3 | Pre | `PractitionerQueryService#findById(Long): Practitioner` | `doctorId` | 为 null 则抛 `PRACTITIONER_NOT_FOUND`；取 `name` 为 `doctorDisplayName` |
| 4 | Cmd | `DoctorStationEncounterManageMapper#toStartOutpatientReceptionCommand` → `EncounterDomainService#startOutpatientReception(StartOutpatientReceptionCommand)` | Request + 步骤 3 的 `doctorDisplayName` | **写库点**：Encounter + EncounterParticipant；`OutpatientReceptionStartedEvent` 携带 **`doctorId`/`doctorName`**（与QueryService §4.2 一致） |
| 5 | Asm | `DoctorStationEncounterManageMapper#toStartOutpatientReceptionResponse` | `encounterId`、受理成功标记 | 领域方法 `void`，成功标记由应用层约定 |

---

### 3.4 IF-DOR-03 `markOutpatientLeave`（患者暂离）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationEncounterManageAppService#markOutpatientLeave` |
| **HTTP** | `POST /api/app/doctorStationEncounterManage/markOutpatientLeave` |

**Request `MarkOutpatientLeaveRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 门诊就诊标识 |
| `leaveReason` | String | N | 暂离原因 |
| `occurredAt` | LocalDateTime | N | 发生时间，默认当前 |
| `operatorId` | Long | Y | 操作人 |

**Response `MarkOutpatientLeaveResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊标识 |
| `success` | Boolean | 是否成功 |

**关键校验**

- `assertCanMarkOnHold`（BR-DOR-005）。

**失败码口径**

| 语义（别名） | 工程 `ErrorCodeConstants.ErrorCode` | 场景 | 用户提示方向 |
| --- | --- | --- | --- |
| `DOR-BIZ-404` | `ENCOUNTER_NOT_FOUND` | 就诊不存在 | 刷新 |
| `DOR-BIZ-409` | `ENCOUNTER_INVALID_STATUS` / `ENCOUNTER_RULE_CONFLICT` | 不允许暂离 | 说明原因 |
| `DOR-BIZ-102` | 以领域抛出为准 | 写库失败 | EX-DOR-102 |

**Mapper 接口方法签名**

- `toMarkOutpatientLeaveCommand(MarkOutpatientLeaveRequest request): MarkOutpatientLeaveCommand`
- `toMarkOutpatientLeaveResponse(Long encounterId, boolean success): MarkOutpatientLeaveResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验 | `encounterId`、`operatorId` | 非法即拒绝 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long): EncounterDetailQueryModel` | `encounterId` | 失败则 **不得 Cmd** |
| 3 | Cmd | `EncounterDomainService#markOutpatientLeave(MarkOutpatientLeaveCommand)` | 由 Request 装配 `MarkOutpatientLeaveCommand` | **写库点**；`OutpatientEncounterOnHoldEvent` |
| 4 | Asm | `DoctorStationEncounterManageMapper#toMarkOutpatientLeaveResponse` | ③ 返回值与 `encounterId` | — |

---

### 3.5 IF-DOR-04 `completeOutpatientEncounter`（门诊完诊）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationEncounterManageAppService#completeOutpatientEncounter` |
| **HTTP** | `POST /api/app/doctorStationEncounterManage/completeOutpatientEncounter` |

**Request `CompleteOutpatientEncounterRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 门诊就诊标识 |
| `completedAt` | LocalDateTime | N | 完诊时间 |
| `summary` | String | N | 小结摘要 |
| `operatorId` | Long | Y | 操作人 |

**Response `CompleteOutpatientEncounterResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊标识 |
| `success` | Boolean | 是否成功 |

**关键校验**

- **Pre**：`checkOutpatientCompletable`（DM §6.2.8）；**Cmd** 内仍有 `assertCompletable`、负责医生、主诊断（BR-ENC-004/006，对齐 AS BR-DOR-006）。

**失败码口径**

| 语义（别名） | 工程 `ErrorCodeConstants.ErrorCode` | 场景 | 用户提示方向 |
| --- | --- | --- | --- |
| `DOR-BIZ-404` | `ENCOUNTER_NOT_FOUND` | 就诊不存在 | 刷新 |
| `DOR-BIZ-409` | `ENCOUNTER_INVALID_STATUS` / `ENCOUNTER_RULE_CONFLICT`（Pre 与 Cmd 断言） | 不完诊条件（状态/缺主诊断/缺医生） | 按 `checkOutpatientCompletable` 或领域消息展示 |
| `DOR-BIZ-102` | 以领域抛出为准 | 写库失败 | EX-DOR-102 |

**Mapper 接口方法签名**

- `toCompleteOutpatientEncounterCommand(CompleteOutpatientEncounterRequest request): CompleteOutpatientEncounterCommand`
- `toCompleteOutpatientEncounterResponse(Long encounterId, boolean success): CompleteOutpatientEncounterResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验 | `encounterId`、`operatorId` | 非法即拒绝 |
| 2 | Pre | `EncounterQueryService#checkOutpatientCompletable(Long): EncounterCompletionCheckQueryModel` | `encounterId` | 不通过则 **不得 Cmd** |
| 3 | Cmd | `EncounterDomainService#completeOutpatientEncounter(CompleteOutpatientEncounterCommand)` | 由 Request 装配 `CompleteOutpatientEncounterCommand` | **写库点**；`OutpatientEncounterCompletedEvent` |
| 4 | Asm | `DoctorStationEncounterManageMapper#toCompleteOutpatientEncounterResponse` | ③ 返回值与 `encounterId` | — |

---

### 3.5a IF-DOR-06 `cancelOutpatientReception`（取消接诊）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationEncounterManageAppService#cancelOutpatientReception` |
| **HTTP** | `POST /api/app/doctorStationEncounterManage/cancelOutpatientReception` |

**Request `CancelOutpatientReceptionRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 门诊就诊标识 |
| `operatorId` | Long | Y | 操作人 |

**Response `CancelOutpatientReceptionResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊标识 |
| `success` | Boolean | 是否成功 |

**关键校验**

- **Pre**：就诊须 `classEnum=AMB`；**本诊临床痕迹须已空**（病历 / 就诊诊断 / 医生开立药·耗·诊疗医嘱均无未软删行，**BR-DOR-011**）；**Cmd** 内 `assertCancelReceptionAllowed`（仅 `IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`）。
- 与账单无关；语义为医生同意退号；**不**自动删除病历/诊断/医嘱。

**失败码口径**

| 语义（别名） | 工程 `ErrorCodeConstants.ErrorCode` | 场景 | 用户提示方向 |
| --- | --- | --- | --- |
| `DOR-BIZ-404` | `ENCOUNTER_NOT_FOUND` | 就诊不存在 | 刷新 |
| `DOR-BIZ-409` | `ENCOUNTER_INVALID_STATUS` | 非门诊或当前状态不可取消接诊 | 当前状态不允许取消接诊 |
| `EX-DOR-105` | `ENCOUNTER_RULE_CONFLICT` | 本诊仍有病历/诊断/医嘱 | 取消接诊前须先删除本诊全部病历、诊断与医嘱 |

**Mapper 接口方法签名**

- `toCancelOutpatientReceptionCommand(CancelOutpatientReceptionRequest request): CancelOutpatientReceptionCommand`
- `toCancelOutpatientReceptionResponse(Long encounterId, boolean success): CancelOutpatientReceptionResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验 | `encounterId`、`operatorId` | 非法即拒绝 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` + AMB 门控 | `encounterId` | 非门诊拒绝 |
| 3 | Pre | 临床痕迹门禁：`EmrDocumentExtension#listByEncounter`；`EncounterQueryService#listEncounterDiagnoses`；药/诊疗/耗材按 `GenerateSource.DOCTOR_PRESCRIPTION` 列表 | `encounterId`、`patientId` | 任一类未空 → **EX-DOR-105**，**不得 Cmd** |
| 4 | Cmd | `EncounterDomainService#cancelOutpatientReception(CancelOutpatientReceptionCommand)` | Request 装配命令 | **写库点**；收口 ADMITTER；`OutpatientReceptionCancelledEvent` |
| 5 | Asm | `DoctorStationEncounterManageMapper#toCancelOutpatientReceptionResponse` | `encounterId` | — |

---

### 3.6 IF-DOR-05 `requestInpatientAdmission`（办理住院）— 历史对照

> **v1.3.1**：本节约束已迁至 **`AS-OP-DOR-005-门诊医生站住院申请-SDD`**。成码包 **`inpatientadmission`**，AppService **`DoctorStationInpatientAdmissionAppService`**，HTTP 前缀 **`doctorStationInpatientAdmission`**。下文 Request/Response 类名与编排保留作迁移前对照，**勿**再按本节在 `encountermanage` 实现。

| 项 | 内容 |
| --- | --- |
| **App（目标态）** | `DoctorStationInpatientAdmissionAppService#requestInpatientAdmission`（见 **AS-OP-DOR-005-SDD**） |
| **HTTP（目标态）** | `POST /api/app/doctorStationInpatientAdmission/requestInpatientAdmission` |

**Request `RequestInpatientAdmissionFromOutpatientRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ambEncounterId` | Long | Y | 当前门诊就诊（Gate 与 **目标态** BR-DOR-007 关联校验） |
| `patientId` | Long | N | 可选；若传入须与门诊头表 `patientId` 一致；未传时以 `mustGetEncounter` 头表为准装配 Command |
| `targetDepartmentId` | Long | Y | 拟收治科室 |
| `requestDoctorId` | Long | Y | 申请医师 |
| `admitSourceCode` | String | N | 入院来源编码 |
| `operatorId` | Long | Y | 操作人 |

**Response `RequestInpatientAdmissionFromOutpatientResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `inpatientEncounterId` | Long | 新建住院就诊标识 |
| `success` | Boolean | 是否成功 |

**关键校验**

- **BR-DOR-007**：同一门诊就诊不得重复关联住院申请——**目标态**须持久化 `amb_encounter_id` 并可查询；当前领域实现若未写入，须在扩展 `requestInpatientAdmission` 与命令项后在本链路增加等价 **Pre**（专用只读查询），见 §1.1。
- **BR-DOR-008**：住院初始态 **`IMP + PLANNED`**（DM §6.1.13）。

**失败码口径**

| 语义（别名） | 工程 `ErrorCodeConstants.ErrorCode` | 场景 | 用户提示方向 |
| --- | --- | --- | --- |
| `DOR-BIZ-404` | `ENCOUNTER_NOT_FOUND` | 门诊就诊不存在 | 刷新 |
| `DOR-BIZ-409` | `ENCOUNTER_INVALID_STATUS` / `ENCOUNTER_RULE_CONFLICT` | 非门诊锚点、患者不一致、头表无患者 | 核对 |
| `DOR-BIZ-201` | `ENCOUNTER_RULE_CONFLICT`（目标态；领域防重落地后） | 重复办理住院 | EX-DOR-201 |
| `DOR-BIZ-103` | 以领域/基础设施抛出为准 | 持久化失败 | EX-DOR-103 |

**Mapper 接口方法签名**

- `toRequestInpatientAdmissionCommand(RequestInpatientAdmissionFromOutpatientRequest request, Long resolvedPatientId): RequestInpatientAdmissionCommand`
- `toRequestInpatientAdmissionFromOutpatientResponse(Long inpatientEncounterId, boolean success): RequestInpatientAdmissionFromOutpatientResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验与主键解析 | `ambEncounterId`、`targetDepartmentId`、`requestDoctorId`、`operatorId`；可选 `patientId` | 若传入 `patientId`，须与步骤 2 头表一致；未传则以头表为准；**禁止**以 null 患者进入 Cmd |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long): EncounterDetailQueryModel` | `ambEncounterId` | 校验为 AMB；`patientId` 与头表一致；失败则 **不得 Cmd** |
| 3 | Pre | `EncounterQueryService#queryEncounterProjections(EncounterProjectionQuery): Page<EncounterProjectionQueryModel>` | `patientId`（来自 ②）、`classEnum=IMP`、`statusEnum=PLANNED` 等投影条件 | 若〔实现启用同患者住院申请粗筛辅助〕执行本步骤；若〔不启用〕跳过本步骤，直接进入步骤 4。**不能**单独作为 BR-DOR-007 终局判断 |
| 4 | Cmd | `EncounterDomainService#requestInpatientAdmission(RequestInpatientAdmissionCommand)` | 由 Request 与 ② 解析结果装配 `RequestInpatientAdmissionCommand` | **写库点**；`InpatientAdmissionRequestedEvent`；**待扩展**：写入 `amb_encounter_id`、参与者、诊断、位置 |
| 5 | Asm | `DoctorStationEncounterManageMapper#toRequestInpatientAdmissionFromOutpatientResponse` | ④ 返回之 `inpatientEncounterId` 与成功标记 | — |

---

## 4. QueryService 与事件刷新

| QueryService | 用途 | 投影触发点（语义级） |
| --- | --- | --- |
| `OutpatientEncounterContextQueryModel` | IF-DOR-01 列表；IF-DOR-02～04 写后最终一致展示 | `OutpatientReceptionStartedEvent`、`OutpatientEncounterOnHoldEvent`、`OutpatientEncounterCompletedEvent` 等（见DM §6.2.1） |

字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` §4.2 为唯一真源；本文不重复。

---

## 5. 写库顺序（核心写接口视角）

> Gate / Pre 链见 **§3**；本节汇总 **Cmd** 与事务，**不重复**完整 Pre 链。

### 5.1 Cmd 落库顺序一览（与 §3 序号对照）

| IF | App 方法 | Cmd 序号（§3） | 触达的领域写服务#方法 | 聚合（写入顺序，语义级） |
| --- | --- | --- | --- | --- |
| IF-DOR-02 | `startOutpatientReception` | 3 | `EncounterDomainService#startOutpatientReception` | Encounter → EncounterParticipant |
| IF-DOR-03 | `markOutpatientLeave` | 3 | `EncounterDomainService#markOutpatientLeave` | Encounter |
| IF-DOR-04 | `completeOutpatientEncounter` | 3 | `EncounterDomainService#completeOutpatientEncounter` | Encounter |
| IF-DOR-05 | `requestInpatientAdmission` | 4 | `EncounterDomainService#requestInpatientAdmission` | Encounter（住院新建） |

### 5.2 事务与回滚口径

| IF | 事务边界建议 | 失败回滚 |
| --- | --- | --- |
| IF-DOR-02～04 | **单 Cmd** 默认同一事务 | Pre 失败无写；Cmd 异常整体回滚 |
| IF-DOR-05 | **单 Cmd**；后续扩展多聚合写入时保持 **同一事务** 直至事件发布前提交策略与 Outbox 一致 | Pre 失败无写；Cmd 异常整体回滚 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 接口 | 幂等键设计 | 重复提交语义 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- | --- |
| IF-DOR-05 | `ambEncounterId`（目标态） | 第二次返回「已存在申请」 | `DoctorStationEncounterManageAppService#requestInpatientAdmission` + 领域防重 | §3.6 序号 4～5 |
| IF-DOR-02～04 | `encounterId` + 状态机 | 终态/非法迁移拒绝 | `EncounterDomainService` 内断言 | §3.3～3.5 序号 3 |

### 6.2 并发控制

| 接口 | 并发场景 | 控制策略 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- | --- |
| IF-DOR-02 | 双医生同时接诊 | 聚合状态机 + 乐观锁/版本 | `EncounterDomainService#startOutpatientReception` | §3.3 序号 3 |
| IF-DOR-04 | 双开完诊 | 同上 | `EncounterDomainService#completeOutpatientEncounter` | §3.5 序号 3 |

### 6.3 重试与容错

| 接口 | 重试策略 | 失败返回口径 |
| --- | --- | --- |
| IF-DOR-01 | 读接口可有限重试 | 产品侧可对齐 `DOR-SYS-500`；工程以运行时异常/统一网关码为准 |
| IF-DOR-02～05 | 写接口不自动重试 | 明确业务码，由用户确认后重提 |

### 6.4 数据脱敏

| 接口 | 脱敏字段 | 规则 |
| --- | --- | --- |
| IF-DOR-01 | 手机号、证件号（若 DTO 含） | 与挂号 SDD 同口径 |

### 6.5 审计日志

| 接口 | 审计内容 | 代码位置（必须） |
| --- | --- | --- |
| IF-DOR-02～05 | 操作人、`encounterId`（或住院新建 ID）、关键状态前后值、时间 | `DoctorStationEncounterManageAppService` 各写方法 |

---

## 7. 测试要点

### 7.1 功能测试

| 接口 | 测试场景 | 预期结果 | 测试类型 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- | --- | --- |
| IF-DOR-01 | 在诊/待诊切换 | 排序与过滤符合 BR-DOR-001/002/**009** | 正向测试 | 集成测试 | 后端 | P0 |
| IF-DOR-01 | `feeNatureOptions` | 与 `fin_contract` 按 `organizationId` 过滤结果一致；无主数据时为空列表且 `records` 仍正常 | 正向测试 | 集成测试 | 后端 | P1 |
| IF-DOR-02 | 正常接诊 | 状态 IN_PROGRESS，参与者切换 | 正向测试 | 集成测试 | 后端 | P0 |
| IF-DOR-02 | 非法状态接诊 | 拒绝 | 异常测试 | 单元测试 | 后端 | P1 |
| IF-DOR-02 | 无效 `doctorId`（主数据不存在） | `PRACTITIONER_NOT_FOUND` | 异常测试 | 集成测试 | 后端 | P1 |
| IF-DOR-03 | 暂离 | ON_HOLD + 事件 | 正向测试 | 集成测试 | 后端 | P0 |
| IF-DOR-04 | 完诊 | DISCHARGED + 事件 | 正向测试 | 集成测试 | 后端 | P0 |
| IF-DOR-04 | 缺主诊断 | Pre/Cmd 拒绝 | 规则测试 | 集成测试 | 后端 | P0 |
| IF-DOR-05 | 首次申请 | 返回新住院就诊 ID | 正向测试 | 集成测试 | 后端 | P0 |
| IF-DOR-05 | 重复申请（目标态） | `ENCOUNTER_RULE_CONFLICT`（语义对齐 DOR-BIZ-201） | 规则测试 | 集成测试 | 后端 | P0 |

### 7.2 非功能性测试

| 接口 | 测试项 | 验收标准 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| IF-DOR-01 | 分页性能 | ≤1s（AS §7） | 性能测试 | 后端 | P1 |
| IF-DOR-02～04 | 并发接诊/完诊 | 无逆状态 | 并发测试 | 后端 | P0 |

### 7.3 QueryService一致性测试

| QueryService | 验证方法 | 一致性要求 |
| --- | --- | --- |
| OutpatientEncounterContextQueryModel | 接诊/暂离/完诊后查询 IF-DOR-01 | 状态、`DISCHARGED` 展示与写侧一致 |

### 7.4 集成测试

| 测试项 | 范围 | 优先级 |
| --- | --- | --- |
| 医生站主链路 | IF-DOR-01 → 02 → 03 → 04 | P0 |
| 办理住院 | IF-DOR-05 + 后续住院登记（他 AS） | P0 |

### 7.5 待补测试用例

| 用例 ID | 范围 | 优先级 | 状态 |
| --- | --- | --- | --- |
| TC-DOR-INT-001 | IF-DOR-05 防重与 `amb_encounter_id` 全链路（见 **AS-OP-DOR-005** SDD） | P1 | 待补 |

---

## 8. 变更历史

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.3.10 | 2026-08-20 | **IF-DOR-06**：Pre 增临床痕迹门禁（病历/诊断/医生开立医嘱须已删，**EX-DOR-105**）；§2.2/§2.4 增补 QS/EMR 依赖；编排权威 **AS v1.19+**、**DM-ADM-002 v1.46**。 |
| v1.3.9 | 2026-08-06 | 新增 **IF-DOR-06** `cancelOutpatientReception`（取消接诊）；Mapper/领域写方法白名单；编排权威 **AS v1.18+**、**DM-ADM-002 v1.41**。 |
| v1.3.8 | 2026-08-03 | **BR-DOR-009**：`IN_PROGRESS`/`ON_HOLD` **统一仅活动 `ADMITTER` 可见**（挂到科室不再科室共享）。谓词落点 `EncounterContextCriteriaSupport#buildDoctorStationViewerVisibilityPredicate`。编排权威 **AS v1.17+**。 |
| v1.3.7 | 2026-08-02 | IF-DOR-01 `OutpatientInfoItemDto` 增 `address`（主档地址全文），供医生站患者卡片回显；读侧自 `OutpatientEncounterContextQueryModel` 透传。 |
| v1.3.6 | 2026-07-22 | **BR-DOR-009**：`IN_PROGRESS`/`ON_HOLD` 挂到科室（无 `REGISTRATION_DOCTOR`）改为科室共享；挂到医生仍仅 `ADMITTER`。谓词落点 `EncounterContextCriteriaSupport#buildDoctorStationViewerVisibilityPredicate`。编排权威 **AS v1.16+**。 |
| v1.3.5 | 2026-07-18 | IF-DOR-01 **排序**对齐 **AS v1.15 / BR-DOR-002**：待诊 `startTime` 升序、在诊/暂离 `receptionTime` 降序；缺省并集 CASE 分桶（`EncounterContextCriteriaSupport` + `EncounterContextReader#pageOutpatientEncounterEntities`）。**BR-DOR-010**：`OutpatientInfoItemDto.contractDisplay`；`EncounterContractDisplayAssembler` + `AccountQueryService#mapPreferredContractNoByEncounterIds`；列表 `feeNatureOptions` 固定空；编排权威 **AS v1.15+**。 |
| v1.3.4 | 2026-06-12 | **BR-DOR-009** 待诊谓词增补历史 **`ADMITTER`** 兼容与参与者 **`is_deleted`** 过滤；§3.2 写侧 **`registerOutpatientEncounter`→`REGISTRATION_DOCTOR`** 与读侧对齐；编排权威 **AS v1.14+**。 |
| v1.3.3 | 2026-06-12 | IF-DOR-01：新增 **BR-DOR-009** 列表可见性（`OutpatientEncounterPageQuery.doctorStationViewerPractitionerId` + `OutpatientEncounterContextLoader` DB 谓词）；**`staffPractitionerId` 改为必填**；移除在诊单队列 `doctorId` 映射。编排权威 **AS v1.13+**。 |
| v1.3.2 | 2026-05-28 | **L3 字段对账**：IF-DOR-01 Request 移除 `organizationId`、改 `pageNo`/`PagedResult#content`；Response 补 `feeNatureOptions` 会话机构口径；`OutpatientInfoItemDto`：`diagnoses`/`genderEnumText`；编排序列改 **`EncounterContextFacade#pageOutpatientEncounterContexts`** |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.3.1 | 2026-05-18 | **IF-DOR-05** 迁出至 **`AS-OP-DOR-005-SDD`**（`inpatientadmission` / `doctorStationInpatientAdmission`）；§1.1 成码范围收窄为 IF-DOR-01～04；§2.1 去除住院 DTO；§3.1 / §3.6 标注历史对照 |
| v1.3.0 | 2026-05-04 | IF-DOR-01：响应增加 **`feeNatureOptions`**（`ContractQueryService#listAllContractsByOrgId` + `OptionDto` 映射）；§2.2/§2.4/§3.2 响应表与编排序列 **④～⑥** 同步；**§7.1** 补测；承接原 **AS-OP-DOR-003 IF-DOR-13** 费用性质展示职责（原医嘱单独接口已下线）。编排权威 **AS v1.9+**。 |
| v1.2.9 | 2026-05-03 | IF-DOR-01：`OutpatientInfoItemDto.genderEnum` 改为 **String**（**`AdministrativeGender#getCode()`**）；§2.3 增补 **AdministrativeGender** 锚点；§3.2 DTO 表与 **OutpatientEncounterContextQueryModel（EncounterContextFacade / Loader） v3.1**、**AS v1.8** 对齐；应用层对性别**不做** sortOrder/整型转换，与QueryService直透。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.2.8 | 2026-05-02 | IF-DOR-01：§2.2/§2.4 增补 **`ChargeItemQueryService`**；编排序列在 Asm 前插入 **Pre ③** 批量费项汇总；§3.2 **`OutpatientInfoItemDto` 三金额**注明覆盖规则；Mapper 白名单与签名增补 **`toPageOutpatientInfoForDoctorStationResponse(..., Map<Long, OutpatientInfoListFinancialTotals>)`**。编排权威 **AS v1.7+**；QueryService **`OutpatientEncounterContextQueryModel` v3.0** §2。 |
| v1.2.7 | 2026-05-02 | §3.2 `keyword`：与 **`OutpatientEncounterContextQueryModel` v2.9** §3.4 一致，写明 **姓名 / 拼音码 / 五笔码 / 就诊业务号** **OR** 模糊。编排权威 **AS v1.6+**。 |
| v1.2.6 | 2026-05-02 | IF-DOR-01：**`encounterStatus` 可选**；缺省时 `statusEnumIn=[REGISTERED,IN_PROGRESS,ON_HOLD,DISCHARGED]` 且 `doctorId=null`；Gate/BR-DOR-001 收紧为**仅显式在诊**须 `staffPractitionerId`。§2.3/§2.4/§3.2/§8 与 **AS v1.5+**、`OutpatientEncounterContextQueryModel` `EncounterQueryService` **v2.8** §3.4 对齐。 |
| v1.2.5 | 2026-05-02 | IF-DOR-01：请求改为 **`encounterStatus`（`EncounterStatus`）**，与QueryService `statusEnum` 直接对齐；删除 `DoctorStationOutpatientQueueType` / `queueType`；映射为单值 `statusEnum` + 在诊时 `doctorId`。§2.3/§3.2 与 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` v2.7 §3.4、编排权威 **AS v1.4+** 对齐。 |
| v1.2.4 | 2026-05-02 | IF-DOR-01：`queueType` 改为 `DoctorStationOutpatientQueueType`（`IN_PROGRESS` / `WAITING_RECEPTION`）；候诊映射为 `OutpatientEncounterPageQuery.statusEnumIn=[REGISTERED,TRIAGED]` 并排除待挂号 `PLANNED`；§2.3/§3.2/编排序列与 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` v2.5 §3.4 对齐。编排权威 AS v1.3+。 |
| v1.2.3 | 2026-05-02 | 按《应用服务 SDD 文档生成提示词模板》新增 **§2.3 枚举、字典与值集（DM 与代码可追溯）**；原「领域方法与 Mapper 白名单」顺延为 **§2.4**；新增 **§2.5 白名单双向一致性复核**；§2.2 Mapper 引用改为「见 §2.4」。 |
| v1.2.2 | 2026-05-02 | 对齐 AS v1.2：§2.2/§2.3 增补 `PractitionerQueryService`；IF-DOR-02 Mapper 签名增加 `doctorDisplayName`；编排序列插入 Pre ③ 与 Cmd ④；失败码增 `PRACTITIONER_NOT_FOUND`（EX-DOR-104）；§7.1 补无效医师用例。编排权威注明 AS v1.2+。 |
| v1.2.1 | 2026-05-01 | 按 `AppServiceControllerRegistrar` 真实动态路由修正 §3.1 与 IF-DOR-01～05 的 HTTP：统一为 `POST /api/app/doctorStationEncounterManage/{methodName}`；同步调整 §1.2 HTTP 接入口径文案。 |
| v1.2.0 | 2026-05-01 | 与当前实现对齐：§1.2 增补 HTTP 接入（`AppServiceControllerRegistrar`）与业务异常码映射说明；§2.2/§2.3 依赖与 Mapper 全量白名单（含 `toOutpatientEncounterPageQuery`、各 Command 映射）；§3.1 脚注工程 URL；§3.2 增补 `staffPractitionerId`、三步骤读路径（医生条件下推 `OutpatientEncounterPageQuery`）、失败码映射至 `ENCOUNTER_*`；§3.3～3.6 失败码与 Mapper 签名对齐代码；§3.6 `patientId` 改可选；QueryService引用改指向 `OutpatientInfo…QueryService` §3.4；§6.3/§7.1 与工程码一致 |
| v1.1.1 | 2026-05-01 | §2.1 `mapper/` 补列 `DoctorStationEncounterManageMapperImpl.java`，与《应用服务 SDD 文档生成提示词模板》MapStruct 接口+Impl 同列约定及工程一致 |
| v1.1.0 | 2026-05-01 | 按《应用服务 SDD 文档生成提示词模板》重刷：§2.2/2.3 依赖与**白名单双向对齐**（含 Mapper Asm）；§3.1 列名「应用服务方法（英文）」；§3.1.1 Asm 统一 `DoctorStationEncounterManageMapper`；全文 App 类名统一 `DoctorStationEncounterManageAppService`；IF-DOR-01 读查询改为 Pre 阶段、`pageOutpatientEncounterContexts` 记法与工程一致，并写明在诊/待诊与医生过滤**分支**；各 IF 补全 **Cmd(Command)**、**Mapper 完整签名**；IF-DOR-01 展开 **OutpatientInfoItemDto** 全字段表；§3.6 住院申请 **Gate/Pre 条件分支**与可选 `queryEncounterProjections` 互斥说明；§5 Cmd 序号与 §3 对齐；§6 审计落点为 `DoctorStationEncounterManageAppService`（应用服务类无 `Impl` 后缀） |
| v1.0.2 | 2026-05-01 | §2.1 包路径全称 `outpatient/doctorstation/encountermanage/`，Service `DoctorStationEncounterManageAppService` |
| v1.0.1 | 2026-05-01 | §2.1 补全 `response/` 及五个 `*Response.java` |
| v1.0 | 2026-05-01 | 初版：对齐 `AS-OP-DOR-001` v1.1 |

---

**文档负责人**：（待填） · **评审**：（待填）
