# 住院登记 · 应用服务详细设计（SDD）

> 编码：AS-IP-FIN-001-SDD  
> 版本：v1.25 | 日期：2026-10-14 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院登记应用服务（住院收费窗口） |
| 边界 | 编排 Patient / Encounter / Condition / Location / Contract / Account / PaymentReconciliation；不在应用层实现聚合不变式 |
| 编排权威 | `AS-IP-FIN-001-住院登记.md`（**v1.16**）§2、§4 |
| 方法权威 | 对应 DM §6（`*DomainService` / `*QueryService`）与 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §3 |
| 严格对齐 | IF 编号严格对齐 AS 的 **IF-IP-REG-00～07** |
| 实现阶段口径 | **本期全量成码**（IF-01～07 均实现）；应用层包 `com.zxplat.domain.application.inpatient.financial.registration` **已生成**；领域 `cancelInpatientAdmission` 已与 DM §6.1.23 对齐 |

**Out of scope（重复 AS §1.4，SDD 不得扩写）**

| 类别 | 内容 |
| --- | --- |
| 医保入院、医保账户 | **IF-IP-REG-03**（登记时医保路径）、**IF-IP-REG-07**（自费转医保）与 **IF-IP-REG-09**（医保转自费：2302/2405/2404 后停用医保户）经 `InpatientChargeInsuranceExtension` 编排 |
| 预交金续收/退款/专页维护 | **AS-IP-FIN-002**（**不在**住院登记页；登记弹窗仅可选首笔金额） |
| 护士站分床、入科 | **AS-IP-NUR-002** |
| 医生发起住院申请写操作 | **AS-OP-DOR-005**（本服务仅消费其产出的 `IMP+PLANNED`） |
| Episode 住院全程 | 本期不展开 |
| `PRE_ADMISSION` 状态迁移 | 本期住院申请/登记链路不使用（`startPreAdmission` 不调用） |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-OP-DOR-005-门诊医生站住院申请.md` | 产出 `InpatientAdmissionRequestedEvent` → `IMP+PLANNED` 待登记行 |
| `AS-OP-FIN-001-门诊挂号.md` | 患者分流（身份证复用/建档）编排参照 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 字段基于 AS 语义与 DM 能力；枚举持久化一律 **String code**（`EncounterStatus.getCode()` 等） |
| DTO 命名 | 去掉 `InpatientRegistration` 叠词、保留 `Registration` 等完整词；见 §2 命名约定 |
| QueryService口径 | 列表/详情以 `InpatientEncounterContextQueryModel` 为主；**同一列表**由 `registrationListStatus`（登记状态筛选）映射读侧条件，**默认待登记**（见 §4.1） |
| UI 口径 | **非 Tab 多页**；工具栏「无档登记」；行操作：**待登记**→登记/取消；**待入科**→查看/取消；**已入科**→查看；满足 **BR-IP-REG-019** 时→转医保（**BR-IP-REG-016**）；登记弹窗选病区后**区段标题**展示空床摘要（IF-IP-REG-05），**禁止**表单内「床位数」重复字段；已分床只读展示「入科床位」（**BR-IP-REG-018**）；门诊转住院「门诊医生」只读（**BR-IP-REG-017**） |
| 预交金口径 | 登记弹窗字段 `advanceDepositAmount`：IF-IP-REG-02 可回显医生意向 DRAFT 总额；收费员可改；有值且 **>0** 则登记同事务按 `paymentDetails` **逐渠道各落一笔**收款；≤0 **软删**意向 DRAFT 且不收款 |
| 事务口径 | IF-IP-REG-03/04 **产品期望**单事务（§5.2）；**成码口径**见 §5.2 **DOC-CONFLICT**：AppService **不**声明 `@Transactional`，多域 Cmd 事务由各领域写服务承担 |
| Encounter 口径 | 门诊/住院为**两条** Encounter；有申请登记 **`PLANNED→REGISTERED`**；无申请直登 **`registerDirectInpatientEncounter`**（`IMP+REGISTERED`，不经 `PLANNED`）；**不修改**门诊就诊；分床 `REGISTERED→IN_PROGRESS` 在 **AS-IP-NUR-002**（`assignInpatientBed`） |
| `ambEncounterId` 口径 | 列表/弹窗 DTO 的 `ambEncounterId` 来自写侧主档 `adm_encounter.amb_encounter_id`（`EncounterQueryService`），**非**QueryService投影；无申请直登为 **null**（§4.3） |
| 就诊读侧隔离 | 住院登记 IF 加载就诊主档须 **`encounterId` + `classEnum=IMP`**（`EncounterQueryService#mustGetEncounterOfClass`）；禁止仅用主键 `mustGetEncounter` 后再应用层筛类别；类别不符 → **EX-IP-REG-102/003/006**（§2.2、§3.3～3.5） |
| 分页口径 | `pageNo` **1-based**；出参经 `PagedResultBuilder` 装配 `content`/`totalCount`/`pageNo`/`pageSize`（B.10.11） |
| 入院诊断口径 | **有申请**：申请（**AS-OP-DOR-005**）已落住院线诊断（西医为门诊医保目录原样，**不**在申请时展开国临；关联 `diagTypeCode=31`，**BR-ENC-021**）；登记 IF-IP-REG-02 只读展示（`resolvePrimaryDiagnosisForRegistration` 优先医保名）；IF-IP-REG-03 **不传** `admissionDiagnoses`（**BR-ENC-014**）。国临一对多展开在住院医生站 IF-IP-DOR-02。**直登**：UI **默认西医**；**中医**一病一症两条（`tcmFlag=true`，疾病行主诊）；**西医**单条；`registerDirectInpatientEncounter` 内 `saveAdmissionDx` 一次（同样不展开国临） |
| 列表展示回填 | IF-IP-REG-01 / IF-IP-REG-02：QueryService 查询列缺失时，App 经 **`InpatientInfoQueueDisplayAssembler`** 批量回查患者/科室/病区/住院号/状态文案（**禁止** N+1） |
| IMP 状态展示 | 出参 `statusEnumText`：**禁止** `EncounterStatus#getDisplay()`；统一 **`InpatientEncounterStatusDisplayUtil`**（**DM-ADM-002** §2.3.2）；领域事件 `EncounterQueryService 状态展示装配（`InpatientEncounterStatusDisplayUtil`）` 写入QueryService 查询列 |
| 患者检索（后端） | **IF-IP-REG-06**：**复用** `OutpatientRegistrationAppService#pagePatientsForSelect`（内部 `PatientQueryService#searchPatientsPaged`）；HTTP **`POST /api/app/outpatientRegistration/pagePatientsForSelect`**；`InpatientRegistrationAppService` **无** `pagePatientsForSelect` 方法（与 `openhis-pro-ui` 住院登记页 `#/api/outpatient-registration` 一致） |
| 患者分流建档（后端） | **IF-IP-REG-03**：`resolvePatientIdForSubmit` → `PatientDomainService#registerPatient`（`InpatientRegistrationMapper#toRegisterPatientCommand`）；机构 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 当前会话机构，非住院科室 |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/financial/registration/
  service/
    InpatientRegistrationAppService.java
  request/
    PagePatientsForSelectRequest.java
    PageRegistrationQueueRequest.java
    LoadRegistrationContextRequest.java
    SubmitRegistrationRequest.java
    CancelRegistrationRequest.java
    ListWardsByDepartmentRequest.java
    LoadWardBedCapacityRequest.java
  response/
    PagePatientsForSelectResponse.java
    PageRegistrationQueueResponse.java
    LoadRegistrationContextResponse.java
    SubmitRegistrationResponse.java
    CancelRegistrationResponse.java
    ListWardsByDepartmentResponse.java
    LoadWardBedCapacityResponse.java
  dto/
    RegistrationQueueItemDto.java
    RegistrationPatientDto.java
    RegistrationPatientSelectItemDto.java
    RegistrationEncounterDto.java
    WardOptionDto.java
    PaymentDetailDto.java
    AdmissionDiagnosisSubmitDto.java
  mapper/
    InpatientRegistrationMapper.java
    InpatientRegistrationMapperImpl.java
```

> 包名：`com.zxplat.domain.application.inpatient.financial.registration`  
> HTTP：`AppServiceControllerRegistrar` → `POST /api/app/inpatientRegistration/{methodName}`

**命名约定（Request / Response / dto）**

| 原则 | 说明 |
| --- | --- |
| 包即域 | 包路径已是 `...inpatient.financial.registration`，类名**去掉**中间的 `InpatientRegistration` 叠词，保留 **`Registration`** 等业务词 |
| 与 App 方法对齐 | 如 `pageInpatientRegistrationQueue` → `PageRegistrationQueueRequest`（不必把方法名整段搬进类名） |
| 可读优先 | **禁止** `Reg`/`Dx` 等晦涩缩写；诊断 DTO 用 `AdmissionDiagnosisSubmitDto` |
| 对照 | 门诊挂号 `PageClinicRegistersRequest`（`outpatient.financial.registration`，同样不重复 OutpatientRegistration） |

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `PatientDomainService` / `PatientQueryService` | 患者检索、建档、详情（IF-IP-REG-03） |
| `CurrentOperatorResolver` | 写路径操作人（登记员 `operatorId`，B.6.1） |
| `CurrentManagedOrganizationIdsResolver` | 患者建档机构（`requireSessionOrganizationUnitId`，B.6.1） |
| `OrganizationQueryService` | 住院科室校验与展示 |
| `LocationQueryService` | 病区列表、床位余量（IF-IP-REG-05） |
| `ContractQueryService` | 费用性质（自费/合同）校验与下拉（IF-IP-REG-02/03） |
| `EncounterDomainService` / `EncounterQueryService` | 住院登记、取消、**`registerDirectInpatientEncounter`** 直登（IF-IP-REG-03/04） |
| `AccountDomainService` / `AccountQueryService` | 可选预交金开户与余额门禁（IF-IP-REG-03/04） |
| `PaymentReconciliationDomainService` | 住院存款收取与结算（IF-IP-REG-03） |
| `ConditionDomainService` | 新开入院诊断事实（**仅直登** IF-IP-REG-03，经 `registerDirectInpatientEncounter` → `saveAdmissionDx`） |
| `EncounterContextReader` | 登记队列（`pageInpatientRegQueue`）、详情上下文（IF-IP-REG-01/02） |
| `EncounterQueryService` | 薄就诊查询（`mustGetEncounterOfClass` 等） |
| `InpatientRegistrationMapper` | DTO 装配；公共库 `OptionDto` 直接复用 |
| `InpatientInfoQueueDisplayAssembler` | 登记/预交金共用：QueryService 查询列缺失时批量回填展示字段（IF-IP-REG-01/02） |
| `AdmissionPrimaryDiagnosisAssembler` | IF-IP-REG-02：从 Loader enrichment 的 QueryModel 内部 JSON 解析主诊断展示字段 |
| `DoctorStationDiagnosisMapper` | 诊断快照 JSON 解析（与医生站口径一致） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterContextReader` | `pageInpatientRegQueue`、`mustGetInpatientContextByEncounterId` | IF-IP-REG-01/02 |
| `EncounterQueryService` | `mustGetEncounterOfClass`、`mapAmbEncounterIdByEncounterIds` 等薄查询 | IF-IP-REG-01/02/03/04 |
| `PatientQueryService` | `searchPatientsPaged`、`findPatientByIdCard`、`loadPatientDetail` | IF-IP-REG-03/06 |
| `PatientDomainService` | `registerPatient`、患者更新命令（与挂号同源，见 DM-ADM-001） | IF-IP-REG-03 |
| `EncounterQueryService` | `mustGetEncounterOfClass`（`IMP`）、`mapAmbEncounterIdByEncounterIds`、`mapBusNoByEncounterIds`（仅 `IMP` 批量） | IF-IP-REG-01/02/03/04 |
| `PatientQueryService` | `findPatientNamesByIds` | IF-IP-REG-01（展示回填） |
| `OrganizationQueryService` | `mapNamesByIds` | IF-IP-REG-01（展示回填） |
| `LocationQueryService` | `mapNamesByIds` | IF-IP-REG-01（病区/床展示回填） |
| `EncounterDomainService` | `registerDirectInpatientEncounter`、`registerInpatientEncounter`、`cancelInpatientAdmission` | IF-IP-REG-03/04 |
| `EncounterDomainService` | `saveAdmissionDx`（经 `registerDirectInpatientEncounter` / 有申请时 `registerInpatientEncounter` 内条件跳过） | IF-IP-REG-03：直登落诊断；有申请且已有主诊则**不**二次写入 |
| `OrganizationQueryService` | `listOrganizationsForInpatientAdmission` | IF-IP-REG-02（`organizationOptions`） |
| `OrganizationQueryService` | `loadOrganizationByIdForDisplay` | IF-IP-REG-02/03 |
| `LocationQueryService` | `listWardsByOrganization`、`countBedsByWard`、`countTotalBedsByWard` | IF-IP-REG-05 |
| `ContractQueryService` | `listContracts`、`findContractById` | IF-IP-REG-02/03 |
| `AccountDomainService` | `openInpatientRegAccount`（登记内，余额 0） | IF-IP-REG-03（登记 Cmd，GAP-006） |
| `AccountDomainService` | `adjustAdvanceDepositBalance` | IF-IP-REG-03（预交金>0，`settle` 后 `balanceDelta=advanceDepositAmount`） |
| `AccountQueryService` | `getRegistrationAccount` | IF-IP-REG-03/04 |
| `PaymentReconciliationDomainService` | `collectAdvanceDeposit`、`settleInpatientPayment` | IF-IP-REG-03（预交金>0） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3.1 `adm_encounter.class_enum` | 住院登记场景为 **`IMP`** |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3.1（**仅 IMP**） | 待登记 **`PLANNED`**；登记后 **`REGISTERED`**；已入科 **`IN_PROGRESS`**（分床后）；取消 **`CANCELLED`** |
| 登记列表状态筛选 | `PageRegistrationQueueRequest.registrationListStatus` | `AS-IP-FIN-001` §4 IF-IP-REG-01 | `PLANNED`/`WAIT_ADMISSION`/`ADMITTED`；**缺省=PLANNED**（**`EncounterContextReader#pageInpatientRegQueue`**，Q-IPI-003） |
| 位置形态 | `com.zxplat.domain.share.enums.LocationForm` | `DM-ADM-003-Location-位置` §3 `form_enum` | 登记写入病区 **`WARD`**；分床为 **`BED`**（取消门禁） |
| 账户类型 | `com.zxplat.domain.share.enums.AccountTypeCode` | `DM-ADM-009` §4.3 / §6.1 | 预交金默认 **`PERSONAL_CASH_ACCOUNT`** |
| 支付业务类型 | `com.zxplat.domain.share.enums.PaymentKind` | `DM-FIN-002` §3.1 `kind_enum` | 预交金 **`HOSPITAL_DEPOSIT`** |
| 支付流水状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §3.1 `status_enum` | 草稿 **`DRAFT`** → 结算 **`SUCCESS`** |
| 入院途径 | `SubmitRegistrationRequest.inWayCode` | `DM-ADM-002` §3.1 `in_way_code` | 术语 `in_way_code`（code_system id=479）；与医保 **adm_way** / 病案 **adm_way_code** 同口径：**1** 急诊、**2** 门诊、**3** 其他医疗机构转入、**9** 其他（4401/4101 同字典）。**门诊转住院**默认 **2**。V28 迁移旧码 1↔2、4→3、5→9。**勿与** `admit_source_code`（入院类型，如 **6**=常规）混淆 |
| 入院类型 | `SubmitRegistrationRequest.admitSourceCode` | `DM-ADM-002` §3.1 `admit_source_code` | 术语 `admit_source`；与 `in_way_code` **独立** |
| 费用性质 | `SubmitRegistrationRequest.contractId` | `DM-FIN-001-Contract-费用性质`；`AS-IP-FIN-001` BR-IP-REG-006 | 本期仅校验合同存在；持久化锚点见 `AccountDomainService#openInpatientRegAccount` |
| IMP 状态展示文案 | `InpatientEncounterStatusDisplayConstants` + `InpatientEncounterStatusDisplayUtil` | `DM-ADM-002` §2.3.2；`AS-IP-FIN-001` **BR-IP-REG-014** | `PLANNED`→待登记；`REGISTERED`→待入科；`IN_PROGRESS`→已入科；`ON_HOLD`+`transfer_org_id`→待转科；`ON_HOLD`+`discharge_type_enum`→待出院；`CANCELLED`→取消登记 |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向（白名单 → §3 序列） | PASS | 白名单方法均出现在至少一个 IF 编排序列 |
| 反向（§3 序列 → 白名单） | PASS | 各 IF 的 Pre/Cmd 调用均可在 §2.2 定位 |
| 互斥步骤对 | PASS | IF-IP-REG-03 患者/就诊/预交金分支在序列中双侧写明条件 |

### 2.5 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-FIN-001` §3**（当前**无待跟踪缺口**）；本 SDD **禁止**维护 Gap 表。下列领域读/写能力均已对齐 `DM-ADM-002` / `DM-FIN-002` 与 zxplat 实现，供编排序列引用。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-REG-00 | `loadInpatientRegistrationPageInit` | 住院登记页初始化 | `POST /api/app/inpatientRegistration/loadInpatientRegistrationPageInit` |
| IF-IP-REG-01 | `pageInpatientRegistrationQueue` | 住院登记待办列表查询 | `POST /api/app/inpatientRegistration/pageInpatientRegistrationQueue` |
| IF-IP-REG-02 | `loadInpatientRegistrationContext` | 登记上下文加载 | `POST /api/app/inpatientRegistration/loadInpatientRegistrationContext` |
| IF-IP-REG-03 | `submitInpatientRegistration` | 提交住院登记 | `POST /api/app/inpatientRegistration/submitInpatientRegistration` |
| IF-IP-REG-04 | `cancelInpatientRegistration` | 取消住院登记 | `POST /api/app/inpatientRegistration/cancelInpatientRegistration` |
| IF-IP-REG-05 | `listWardsByDepartment` / `loadWardBedCapacity` | 按科室查询病区列表；病区床位余量查询 | `POST .../listWardsByDepartment`；`POST .../loadWardBedCapacity` |
| IF-IP-REG-06 | `pagePatientsForSelect` | 患者分页检索（登记下拉；**编排落门诊挂号 App**） | `POST /api/app/outpatientRegistration/pagePatientsForSelect` |
| IF-IP-REG-07 | `switchEncounterToInsurance` | 自费转医保 | `POST /api/app/inpatientRegistration/switchEncounterToInsurance` |

> 说明：AS 将 IF-IP-REG-05 合并为一个业务 IF；SDD 拆为两个 App 方法以便前端分步调用，语义仍属 IF-IP-REG-05。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService`，持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | Mapper 纯 DTO 装配 | — |

记法：`EncounterContextReader#mustGetInpatientContextByEncounterId` 表示调用注入 Bean 的该方法。

### 3.1A IF-IP-REG-00 `loadInpatientRegistrationPageInit`（住院登记页初始化）

**HTTP**：`POST /api/app/inpatientRegistration/loadInpatientRegistrationPageInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 占位请求体 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentChannelOptions` | `List<OptionDto>` | 用户可选支付渠道（**ADR-PAY-001**；`PaymentChannelOptionProvider`；登记弹窗首笔预交金与预交金页对齐） |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `PaymentChannelOptionProvider#loadUserSelectableOptions` | 477 ∩ 枚举 ∩ 模块 Gate |
| 2 | Asm | `InpatientRegistrationMapper#toLoadInpatientRegistrationPageInitResponse` | 纯 DTO 装配 |

**失败码**：EX-IP-REG-101 → 初始化失败。

### 3.2 IF-IP-REG-01 `pageInpatientRegistrationQueue`（住院登记待办列表查询）

**HTTP**：`POST /api/app/inpatientRegistration/pageInpatientRegistrationQueue`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `registrationListStatus` | String | N | 登记状态筛选：`PLANNED` 待登记 / `WAIT_ADMISSION` 待入科 / `ADMITTED` 已入科；**不传默认 `PLANNED`**（`InpatientRegistrationConstants`） |
| `keyword` | String | N | 患者姓名等关键字（读侧模糊，**`EncounterContextReader#pageInpatientRegQueue`**） |
| `idCard` | String | N | 身份证号精确筛选 |
| `pageNo` | Integer | Y | 页码（1-based） |
| `pageSize` | Integer | Y | 页大小 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<RegistrationQueueItemDto>` | 列表行 |
| `totalCount` | Long | 总条数 |
| `pageNo` | Integer | 当前页（1-based） |
| `pageSize` | Integer | 页大小 |

**DTO 字段表：RegistrationQueueItemDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `encounterBusNo` | String | Y | 住院号 |
| `patientId` | Long | Y | 患者 ID |
| `patientName` | String | Y | 患者姓名 |
| `contractDisplay` | String | N | 费用性质展示名（已开户时） |
| `chargeIntent` | String | N | 医生费用性质意向 code（**BR-IP-REG-024**；登记弹窗未开户时预填费用性质） |
| `chargeIntentText` | String | N | 意向展示（医保/自费）；待登记无合同时列表费用性质列可回落；弹窗**不**单独展示 |
| `statusEnum` | String | Y | `EncounterStatus` code |
| `statusEnumText` | String | Y | IMP 产品阶段文案（**BR-IP-REG-014**）：Mapper 优先 `overlay.impStatusText`（来自 `InpatientEncounterStatusDisplayUtil`），其次QueryService `status_enum_text`，**禁止** `status.getDisplay()` |
| `organizationId` | Long | N | 意向/当前科室 ID |
| `organizationName` | String | N | 科室名称 |
| `wardId` | Long | N | 病区 ID |
| `wardName` | String | N | 病区名称 |
| `bedId` | Long | N | 床位 ID（已入科时有值） |
| `bedName` | String | N | 床位名称 |
| `startTime` | LocalDateTime | N | 申请/入院时间 |
| `inWayCode` | String | N | 入院途径 code（**1/2/3/9**，对齐 adm_way；UI 标签「入院途径」） |
| `ambEncounterId` | Long | N | 源门诊就诊 ID；由 App 批量查 `adm_encounter.amb_encounter_id` 回填（§4.3）；无申请直登为 null |
| `cancelable` | Boolean | Y | 是否可取消（`PLANNED` 或 `REGISTERED`） |
| `switchToInsuranceEnabled` | Boolean | N | 是否允许自费转医保（**BR-IP-REG-019**；扩展未启用时为 false） |
| `insurancePlatformRegistered` | Boolean | N | 是否已在医保平台完成 2401（待入科医保退入院用） |

**关键校验**

- `pageNo`≥1，`pageSize`≥1。
- `registrationListStatus` 非法值 → EX-IP-REG-101。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-REG-101 | 查询失败 | 缩小条件或稍后重试 |

**Mapper 接口方法签名**

- `toRegistrationQueueItemDtoList(List<InpatientEncounterContextQueryModel>, Map<Long, Long> ambEncounterIdByEncounterId, Map<Long, InpatientQueueDisplayOverlay> displayOverlayByEncounterId): List<RegistrationQueueItemDto>`
- `toPageRegistrationQueueResponse(List<RegistrationQueueItemDto>, Page<InpatientEncounterContextQueryModel>): PageRegistrationQueueResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `pageNo`/`pageSize` | 非法分页拒绝 |
| 2 | Pre | `PatientQueryService#findPatientByIdCard` | 当 `idCard` 非空 | 无匹配则 Asm 空页（`PagedResultBuilder`，保留 `pageNo`/`pageSize`） |
| 3 | Pre | **`EncounterContextReader#pageInpatientRegQueue`**（`InpatientEncounterPageQuery`） | 装配 `registrationListStatus`（缺省 `PLANNED`） | 仅读；`PLANNED`→`PLANNED`；`WAIT_ADMISSION`→`REGISTERED`；`ADMITTED`→`IN_PROGRESS` |
| 4 | Pre | `EncounterQueryService#mapAmbEncounterIdByEncounterIds` | ②分页结果的 `encounterId` 集合 | 仓储 `findAllByIdInAndClassEnum(..., IMP)` 批量解析 `amb_encounter_id`（§4.3） |
| 5 | Pre | `InpatientInfoQueueDisplayAssembler#buildOverlay` | ②当前页QueryService行 | 批量补患者姓名/科室病区/住院号；**状态文案**经 `InpatientEncounterStatusDisplayUtil` 写入 overlay，Mapper **优先** overlay |
| 5a | Pre | `enrichRegistrationQueueSwitchToInsurance` | ②当前页 `encounterId` | 批量 `mapSettledTotalPriceByEncounterIds` + `computeSwitchToInsuranceEnabled` 回填 `switchToInsuranceEnabled` |
| 6 | Asm | `InpatientRegistrationMapper#toRegistrationQueueItemDtoList` → `toPageRegistrationQueueResponse` | ③④⑤ + `PagedResultBuilder` | 仅 DTO；**禁止**手写 `getNumber()+1` |

### 3.2A IF-IP-REG-06 `pagePatientsForSelect`（患者分页检索）

**HTTP**：`POST /api/app/outpatientRegistration/pagePatientsForSelect`（**非** `inpatientRegistration`；实现类 `OutpatientRegistrationAppService`）

**Request**（`PagePatientsForSelectRequest`，继承 `AbstractHealthPageRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 姓名/病历号/证件号等关键字（`PatientPagedQuery.name`） |
| `pageNo` | Integer | Y | 页码（1-based） |
| `pageSize` | Integer | Y | 页大小 |

**Response**（`PagePatientsForSelectResponse` = `PagedResult<RegistrationPatientSelectItemDto>`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<RegistrationPatientSelectItemDto>` | 患者行 |
| `totalCount` | Long | 总条数 |
| `pageNo` / `pageSize` | Integer | 分页元数据 |

**DTO 字段表：RegistrationPatientSelectItemDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `patientId` | Long | 患者 ID |
| `patientBusNo` | String | 患者业务号 |
| `name` | String | 姓名 |
| `genderCode` | String | 性别 code |
| `genderText` | String | 性别展示（`EnumUtil` + `AdministrativeGender#getDisplay()`） |
| `birthDate` | LocalDate | 出生日期 |
| `age` | Integer | 周岁 |
| `idCardNoMasked` | String | 证件号（读侧口径） |
| `phoneMasked` | String | 手机号（读侧口径） |

**Mapper 接口方法签名**

- `toRegistrationPatientSelectItemDtoList(Page<PatientPageItemQueryModel>): List<RegistrationPatientSelectItemDto>`
- `toPagePatientsForSelectResponse(List<RegistrationPatientSelectItemDto>, Page<PatientPageItemQueryModel>): PagePatientsForSelectResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate#requirePositivePageWindow` | `pageNo`/`pageSize` | EX-IP-REG-101 |
| 2 | Pre | `PatientQueryService#searchPatientsPaged(PatientPagedQuery)` | `keyword`→`name` | 仅读 |
| 3 | Asm | `toRegistrationPatientSelectItemDtoList` → `toPagePatientsForSelectResponse` | ② + `PagedResultBuilder` | 禁止手写 `getNumber()+1` |

### 3.3 IF-IP-REG-02 `loadInpatientRegistrationContext`（登记上下文加载）

**HTTP**：`POST /api/app/inpatientRegistration/loadInpatientRegistrationContext`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | N | 住院就诊 ID；无档登记为空 |
| `organizationId` | Long | N | 已选住院科室（预拉病区用） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `patient` | `RegistrationPatientDto` | 患者主档 |
| `encounter` | `RegistrationEncounterDto` | 住院就诊/申请信息；无 `encounterId` 时为 null |
| `contractOptions` | `List<OptionDto>` | 费用性质下拉 |
| `organizationOptions` | `List<OptionDto>` | **入院科室**下拉；`OrganizationQueryService#listOrganizationsForInpatientAdmission`（`type_enum=DEPARTMENT` 且 `class_enum=INPATIENT`）；**禁止**从 `wardBedInit` 全量 org 代替 |
| `readOnly` | Boolean | Y | **`REGISTERED` 或 `IN_PROGRESS`** 时为 true（查看模式，**BR-IP-REG-016**）；禁止仅 `IN_PROGRESS` 才只读 |
| `switchToInsuranceEnabled` | Boolean | N | 是否允许自费转医保（**BR-IP-REG-019**；与 IF-IP-REG-01 同口径） |

**DTO 字段表：RegistrationPatientDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientId` | Long | N | 患者 ID |
| `name` | String | N | 姓名 |
| `idCard` | String | N | 身份证 |
| `genderEnum` | String | N | 性别 code |
| `birthDate` | LocalDateTime | N | 出生日期 |
| `phone` | String | N | 联系电话 |
| `maritalStatusEnum` | String | N | 婚姻状况 code（→ 病案 `mrg_stas_code`） |
| `prfsEnum` | String | N | 职业 code（→ 病案 `prfs_code`） |
| `address` | String | N | 现住址详细地址 |
| `addressProvince` / `addressCity` / `addressDistrict` / `addressStreet` | String | N | 现住址省市区/街道（→ 病案 `curr_addr_codes` / `curr_addr_detail`） |
| `countryCode` | String | N | 国籍（`adm_patient.country_code` → 病案 `ntly_code`） |
| `nationalityCode` | String | N | 民族（`adm_patient.nationality_code` → 病案 `naty_code`） |
| `nativePlace` | String | N | 籍贯；省市区 code 斜杠串时 → 病案 `napl_codes` |
| `workCompany` | String | N | 工作单位（→ 病案 `empr_addr`） |
| `linkName` / `linkRelationCode` / `linkTelcom` | String / Integer / String | N | 联系人姓名/关系/电话（→ 病案 `coner_*`） |

**DTO 字段表：RegistrationEncounterDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `encounterBusNo` | String | Y | 住院号；QueryService无值时由 `EncounterDetailQueryModel.busNo` 补全 |
| `statusEnum` | String | Y | 状态 code |
| `organizationId` | Long | N | 住院科室 |
| `organizationName` | String | N | 科室名 |
| `wardId` | Long | N | 病区 |
| `wardName` | String | N | 病区名 |
| `bedId` | Long | N | 分床后床位 ID（`InpatientEncounterContextQueryModel`）；未分床为 null |
| `bedName` | String | N | 分床后床位名称；未分床为 null |
| `admitSourceCode` | String | N | 入院来源 |
| `admitSourceCodeText` | String | N | 入院来源展示文案 |
| `inWayCode` | String | N | 入院途径（adm_way 同口径：1 急诊 / 2 门诊 / 3 其他医疗机构转入 / 9 其他） |
| `inWayCodeText` | String | N | 入院途径展示文案 |
| `startTime` | LocalDateTime | N | 入院/申请时间 |
| `ambEncounterId` | Long | N | 门诊来源就诊；来自 `EncounterDetailQueryModel`（`adm_encounter.amb_encounter_id`） |
| `outpatientDoctorName` | String | N | 源门诊 **ADMITTER** 姓名；`ambEncounterId` 非空时 App 解析；登记弹窗**只读**（**BR-IP-REG-017**） |
| `chargeIntent` | String | N | 医生费用性质意向 code（`INSURANCE`/`SELF_PAY`；**BR-IP-REG-024**） |
| `chargeIntentText` | String | N | 意向展示（医保/自费）；弹窗**不**单独展示，供列表无合同时回落 |
| `advanceDepositAmount` | BigDecimal | N | 意向预交金总额（最新 `HOSPITAL_DEPOSIT` DRAFT 回显；**BR-IP-REG-021**） |
| `paymentDraftId` | Long | N | 意向预交金草稿支付主键（可选） |
| `primaryConditionDefinitionId` | Long | N | 主诊断定义 ID；由 `AdmissionPrimaryDiagnosisAssembler` → `DiagnosisSnapshotDisplayResolver` 从 `diagnosis_json` 解析（`mainDiagnosisFlag=1` 优先；**中医**取同组病名行 ID） |
| `primaryConditionDefinitionName` | String | N | 主诊断展示名；**中医一病一症**合并为「病名（证候）」；**禁止**前端用目录名回填「诊断描述」 |
| `priorityEnum` | String | N | 病情优先级；来自 `EncounterDetailQueryModel.priorityEnum` |
| `attendingDoctorId` | Long | N | 主治医生医务人员 ID |
| `attendingDoctorName` | String | N | 主治医生姓名 |
| `contractId` | Long | N | 费用性质合同 ID；已开户时按 **`mapPreferredContractNoByEncounterIds`**（医保户优先）解析；未开户为 null 时前端按 `chargeIntent` 预填（医保→市医保、自费→自费；**BR-IP-REG-024**），再回落下拉第一项 |

**关键校验**

- 若 `encounterId` 非空则必须存在且为 `IMP`。
- 无 `encounterId` 时返回空 `encounter` 模板（无档登记）。
- 有申请（`PLANNED`）时：前端诊断区绑定 **`primaryConditionDefinitionName`（及 ID）只读**（**BR-IP-REG-007**）；**禁止**返回/绑定整段 `diagnosisJson`；**禁止**按门诊就诊再查主诊覆盖展示。
- **`REGISTERED`/`IN_PROGRESS`**：`readOnly=true`（**BR-IP-REG-016**）。
- `ambEncounterId` 非空：`outpatientDoctorName` 由 App **`resolveOutpatientDoctorName`**（源门诊 `listDisplayEncounterParticipantsByType` + `PractitionerQueryService`）回填，前端只读。
- `bedId`/`bedName`：Mapper 自读模型映射；前端仅在已分床时展示「入科床位」（**BR-IP-REG-018**）。

**失败码口径**

| 语义码 | 场景 |
| --- | --- |
| EX-IP-REG-102 | 就诊不存在或QueryService未投影 |

**Mapper 接口方法签名**

- `toRegistrationPatientDto(PatientCoreQueryModel): RegistrationPatientDto`（含婚姻/职业/现住址/国籍/民族/籍贯/工作单位/联系人）
- `toRegistrationEncounterDto(InpatientEncounterContextQueryModel, EncounterDetailQueryModel, Map<String,String> inWayLabelByCode, Map<String,String> admitSourceLabelByCode): RegistrationEncounterDto`
- `toContractOptionList(List<ContractListItemQueryModel>): List<OptionDto>`
- `toLoadRegistrationContextResponse(..., boolean readOnly, boolean switchToInsuranceEnabled): LoadRegistrationContextResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `encounterId` 可空 | — |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | 当 `encounterId` 非空 | 仅读；失败 EX-IP-REG-102 |
| 3 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | 当 `encounterId` 非空；`classEnum=IMP` | 仓储 `findByIdAndClassEnum`；非 IMP → **EX-IP-REG-102**；供 Asm 补 `busNo`/`admitSourceCode`/`priorityEnum`/`ambEncounterId` |
| 4 | Pre | `PatientQueryService#loadPatientDetail` | 由②解析 `patientId` | 无就诊时跳过 |
| 5 | Pre | `ContractQueryService#listContracts` | — | 费用性质下拉 |
| 6 | Pre | `OrganizationQueryService#listOrganizationsForInpatientAdmission` | — | 入院科室下拉（住院分类科室，见 **DM-ADM-004** §1.5） |
| 6a | Pre | `EncounterQueryService#listDisplayEncounterParticipantsByType` + `PractitionerQueryService#findAllByIds` | 当 `ambEncounterId` 非空 | 解析 `outpatientDoctorName`（源门诊 ADMITTER；展示口径活动优先） |
| 6b | Pre | `AccountQueryService#mapPreferredContractNoByEncounterIds` + `ContractQueryService#mapContractIdByBusNoIn` | 当 `encounterId` 非空 | 回填 `RegistrationEncounterDto.contractId`（医保户优先） |
| 6c | Pre | `PaymentReconciliationQueryService#findLatestHospitalDepositDraftByEncounter` | 当 `encounterId` 非空 | 回填意向预交金 `advanceDepositAmount` / `paymentDraftId` |
| 7 | Gate | `readOnly` 判定 | `encounter.statusEnum` | **`REGISTERED` 或 `IN_PROGRESS` → true**（**BR-IP-REG-016**） |
| 8 | Asm | `InpatientRegistrationMapper#toRegistrationEncounterDto` 等 → `toLoadRegistrationContextResponse` | ②～⑦ | Mapper 内调 **`AdmissionPrimaryDiagnosisAssembler`** 从 `diagnosis_json` 解析主诊断字段；映射 `bedId`/`bedName`；App 再写 `contractId` |

### 3.4 IF-IP-REG-03 `submitInpatientRegistration`（提交住院登记）

**HTTP**：`POST /api/app/inpatientRegistration/submitInpatientRegistration`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | N | 已有医生申请时必填 |
| `patientId` | Long | N | 已有患者时传入 |
| `idCard` | String | C | 无 `patientId` 时必填 |
| `patientName` | String | C | 新建患者时必填 |
| `genderEnum` | String | N | 性别 code |
| `birthDate` | LocalDateTime | N | 出生日期 |
| `phone` | String | N | 电话 |
| `maritalStatusEnum` | String | N | 婚姻；`updatePatientFromIdCard=true` 时叠写主档 |
| `prfsEnum` | String | N | 职业 |
| `address` / `addressProvince` / `addressCity` / `addressDistrict` / `addressStreet` | String | N | 现住址详址与省市区 |
| `countryCode` | String | N | 国籍（→ `country_code` / 病案 ntly） |
| `nationalityCode` | String | N | 民族（→ `nationality_code` / 病案 naty） |
| `nativePlace` | String | N | 籍贯（code 斜杠串或文本） |
| `workCompany` | String | N | 工作单位 |
| `linkName` / `linkRelationCode` / `linkTelcom` | String / Integer / String | N | 联系人 |
| `updatePatientFromIdCard` | Boolean | N | 为 true 时以既有主档为底叠写上列人口学（**BR-IP-REG-025**）；登记后 bootstrap 病案首页从主档自动取值 |
| `organizationId` | Long | Y | 住院科室 |
| `wardLocationId` | Long | Y | 入院病区位置 ID（`LocationForm.WARD`） |
| `admitSourceCode` | String | N | 入院来源 |
| `inWayCode` | String | N | 入院途径 |
| `startTime` | LocalDateTime | Y | 入院时间 |
| `priorityEnum` | String | N | 病情优先级 |
| `contractId` | Long | Y | 费用性质（自费/合同） |
| `advanceDepositAmount` | BigDecimal | N | 预交金；**>0** 才开户收款 |
| `paymentDetails` | `List<PaymentDetailDto>` | C | 预交金>0 时必填 |
| `admissionDiagnoses` | `List<AdmissionDiagnosisSubmitDto>` | C | **直登/无档**（`encounterId` 为空）：**必填**，至少一条主诊断，登记时 `saveAdmissionDx`。**有申请**（`encounterId` 非空）：**不传或为空**；若误传且与申请主诊冲突 → 领域跳过或拒绝（**BR-IP-REG-007**、**BR-ENC-014**） |
| `operatorId` | Long | Y | 操作人/登记员 |

**DTO 字段表：PaymentDetailDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payChannelCode` | String | Y | 支付渠道 code |
| `amount` | BigDecimal | Y | 支付金额 |
| `accountId` | Long | N | 结算账户（开户后由 App 回填） |

**DTO 字段表：AdmissionDiagnosisSubmitDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `conditionId` | Long | N | 已有诊断事实 ID |
| `conditionDefinitionId` | Long | C | 新开诊断时必填 |
| `primaryFlag` | Boolean | Y | 是否主诊断 |
| `description` | String | N | 诊断说明；**仅**用户填写时提交，禁止回落目录名 |
| `tcmFlag` | Boolean | N | 是否中医；中医一病一症时两条均为 `true` |
| `tcmSyndromeGroup` | String | N | 中医病证组号（可空由编排层分配） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 住院就诊 ID |
| `encounterBusNo` | String | 住院号；**写侧即时**取自 `EncounterQueryService#mustGetEncounterOfClass`（`IMP`）的 `busNo`，不等待 `InpatientEncounterContextQueryModel` 投影 |
| `statusEnum` | String | 登记后 **`REGISTERED`**（待护士分床） |
| `paymentId` | Long | 预交金支付单 ID；未收款为 null |
| `accountOpened` | Boolean | 是否本次新开账户 |

**关键校验**

- BR-IP-REG-006：`contractId` 须有效。
- BR-IP-REG-001：必须先完成 `registerInpatientEncounter` 再处理预交金。
- BR-IP-REG-002：登记内已 `openInpatientRegAccount`；`advanceDepositAmount` 为空或 ≤0 时**不得**再开户，软删意向 DRAFT 并跳过收款。
- **预交金>0 时**：`paymentDetails` 非空（可多渠道）；各行 `amount` **> 0**；`sum(paymentDetails[].amount)` **等于** `advanceDepositAmount`（与 **AS-IP-FIN-002** **BR-IP-PREP-010** / SDD **EX-IP-PREP-007** 同口径；不等 → **EX-IP-REG-001**，文案「支付明细合计与预交金金额不一致」）；先软删旧意向 DRAFT，再**逐渠道**各 `collectAdvanceDeposit`+`settleInpatientPayment`（一渠道一流水，**BR-IP-REG-022**）；余额按合计一次调增。
- 有 `encounterId`：登记前须 **`PLANNED`**；**不得**通过本请求维护入院诊断（**BR-IP-REG-007**）。无 `encounterId`：走 **`registerDirectInpatientEncounter`**（`patientId` 必填，`admissionDiagnoses` 必填，不经 `PLANNED`）。

**失败码口径**

| 语义码 | 场景 |
| --- | --- |
| EX-IP-REG-001 | 必填缺失 |
| EX-IP-REG-002 | 科室/病区/费用性质无效 |
| EX-IP-REG-003 | 状态不允许登记 |
| EX-IP-REG-004 | 预交金收款失败（整单回滚） |

**Mapper 接口方法签名**

- `toRegisterPatientCommand(SubmitRegistrationRequest): RegisterPatientCommand`
- `toModifyPatientProfileCommand(SubmitRegistrationRequest, Long patientId, PatientCoreQueryModel existing): ModifyPatientProfileCommand`（既有主档为底叠写登记人口学，避免血型等被空覆盖）
- `toRegisterInpatientEncounterCommand(SubmitRegistrationRequest, Long encounterId, Long registrarId, String contractNo): RegisterInpatientEncounterCommand`（直登时 `encounterId` 为空，命令级 `patientId` 由 App 设置）
- `toRegisterInpatientEncounterCommand(..., String contractNo, ...)`：`contractId` 经 Pre 解析为 `fin_contract.bus_no`
- `toCollectAdvanceDepositCommand(SubmitRegistrationRequest, Long patientId, Long encounterId, Long accountId): CollectAdvanceDepositCommand`（`paymentEnum=PAY`；渠道在明细；`kindEnum=HOSPITAL_DEPOSIT`）
- `toSettleInpatientPaymentCommand(Long paymentId): SettleInpatientPaymentCommand`
- `toSubmitRegistrationResponse(Long encounterId, String busNo, Long paymentId, boolean accountOpened): SubmitRegistrationResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 科室/病区/入院时间/费用性质/操作人 | EX-IP-REG-001 |
| 2 | Pre | `ContractQueryService#findContractById` | `contractId` | 无效 → EX-IP-REG-002；有效则取 `busNo` 传入登记 Cmd 的 `contractNo` |
| 3 | Pre | `OrganizationQueryService#loadOrganizationByIdForDisplay` | `organizationId` | 科室无效 → EX-IP-REG-002 |
| 4 | Pre | `LocationQueryService#wardServesOrganization` | 启用 `WARD`；主挂靠或 `type_json.servedOrganizationIds` 命中所选科 | 未挂靠 → EX-IP-REG-002 |
| 5 | Cmd | `resolvePatientIdForSubmit` | `patientId`→直用；`idCard`→`findPatientByIdCard`；未命中→`toRegisterPatientCommand` + `registerPatient` | **建档机构**=`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`（**非** `organizationId` 住院科室）；**第一写库点** Patient 域 |
| 6 | Cmd | `PatientDomainService#registerPatient` | 未命中既有患者 | `RegisterPatientCommand.item.organizationId` 为步骤 5 解析之会话机构 |
| 7 | Cmd | 患者更新 | 若 `updatePatientFromIdCard=true` | `toModifyPatientProfileCommand(request, patientId, existing)` 叠写人口学（**BR-IP-REG-025**）；其后 afterCommit bootstrap 病案 |
| 8a | Cmd | `EncounterDomainService#registerDirectInpatientEncounter(RegisterInpatientEncounterCommand)` | **仅当** `encounterId` 为空 | **INSERT `IMP+REGISTERED`**；开户 + **`saveAdmissionDx`（本路径唯一写诊断点）**；发布 `InpatientAdmissionCompletedEvent`；直登路径 `registerDirectInpatientEncounter` |
| 8b | Pre | `EncounterQueryService#mustGetEncounterOfClass` | 有 `encounterId`；`classEnum=IMP` | 须 **`PLANNED`**；非 IMP → **EX-IP-REG-003** |
| 9 | Cmd | `EncounterDomainService#registerInpatientEncounter(RegisterInpatientEncounterCommand)` | 有申请路径 | **`PLANNED→REGISTERED`**；**悲观行锁** + 锁内断言（GAP-ATD-004）；开立 **`adm_account`（余额 0）**；若住院线**已有主诊**则**跳过** `saveAdmissionDx`（申请已落库）；发布 `InpatientAdmissionCompletedEvent` |
| 10 | — | （删除独立诊断步骤） | 有申请 | **禁止** App 层再调 `upsertEncounterDiagnosisAssociation`；`admissionDiagnoses` 应为空 |
| 11 | Cmd | 软删旧 `HOSPITAL_DEPOSIT` DRAFT/CANCELLED（若有）→ 逐渠道 `collectAdvanceDeposit` | **仅当** `advanceDepositAmount>0`；≤0 仅软删意向草稿 | 在**已有账户**上收款；一渠道一流水 |
| 12 | Cmd | `PaymentReconciliationDomainService#collectAdvanceDeposit`（按 `paymentDetails` 循环） | 每条渠道一笔：`kindEnum=HOSPITAL_DEPOSIT`；`paymentEnum=PAY`；头金额=该渠道金额 | 预交金草稿（单明细） |
| 13 | Cmd | `PaymentReconciliationDomainService#settleInpatientPayment(SettleInpatientPaymentCommand)` | 草稿 ID | 失败 → EX-IP-REG-004 |
| 14 | Cmd | `AccountDomainService#adjustAdvanceDepositBalance` | **仅当** `advanceDepositAmount>0`；`balanceDelta=advanceDepositAmount` | **写库点**：Account 余额增加；`AccountBalanceEditedEvent` |
| 15 | Asm | `EncounterQueryService#mustGetEncounterOfClass`（`IMP`）→ `toSubmitRegistrationResponse` | 登记后 `encounterId` | 响应 `statusEnum` 典型 **`REGISTERED`** |
| 16 | — | — | — | `InpatientEncounterContextQueryModel` 由领域事件载荷最终一致更新 |

### 3.5 IF-IP-REG-04 `cancelInpatientRegistration`（取消住院登记）

**HTTP**：`POST /api/app/inpatientRegistration/cancelInpatientRegistration`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `cancelReason` | String | N | 取消原因 |
| `operatorId` | Long | Y | 操作人 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 ID |
| `statusEnum` | String | **`CANCELLED`** |
| `success` | Boolean | 是否成功 |

**关键校验**

- BR-IP-REG-008/009：可取消态 + 余额为 0；取消前作废 `HOSPITAL_DEPOSIT` DRAFT。
- 已分床（活动 `BED` 位置）→ EX-IP-REG-006。

**失败码口径**

| 语义码 | 场景 |
| --- | --- |
| EX-IP-REG-005 | 预交金余额>0 |
| EX-IP-REG-006 | 已分床或重复取消 |

**Mapper 接口方法签名**

- `toCancelInpatientAdmissionCommand(CancelRegistrationRequest, Long accountBalanceSnapshot): CancelInpatientAdmissionCommand`
- `toCancelRegistrationResponse(Long encounterId): CancelRegistrationResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `encounterId`/`operatorId` | — |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `encounterId`；`classEnum=IMP` | 非 IMP → **EX-IP-REG-006** |
| 3 | Pre | `AccountQueryService#getRegistrationAccount` | 默认自费户 | 无户则余额视为 0 |
| 4 | Pre | 余额门禁 | `balance>0` | → EX-IP-REG-005 |
| 4a | Cmd | `softDeleteAdvanceDepositDraft`（DRAFT + 历史 CANCELLED） | `listHospitalDepositDraftsByEncounter` / `findPaymentsByEncounter` | 软删意向预交金草稿（**BR-IP-REG-008**） |
| 5 | Cmd | `EncounterDomainService#cancelInpatientAdmission(CancelInpatientAdmissionCommand)` | 项内 `accountBalance` 为分或元（与账务约定一致） | **写库点**；领域内 `assertInpatientAdmissionCancelable`+清 `ambEncounterId`；发布 `InpatientAdmissionCancelledEvent` |
| 6 | Asm | `InpatientRegistrationMapper#toCancelRegistrationResponse` | ⑤ | — |

### 3.6 IF-IP-REG-05 `listWardsByDepartment`（按科室查询病区列表）

**HTTP**：`POST /api/app/inpatientRegistration/listWardsByDepartment`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | Y | 住院科室 ID |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `wards` | `List<WardOptionDto>` | 病区列表 |

**DTO 字段表：WardOptionDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `wardLocationId` | Long | Y | 病区位置 ID |
| `wardCode` | String | N | 病区编码 |
| `wardName` | String | Y | 病区名称 |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `organizationId` | — |
| 2 | Pre | `LocationQueryService#listWardsByOrganization` | `organizationId` | 主挂靠 ∪ `servedOrganizationIds`；多科共用一病区可列出 |
| 3 | Asm | `InpatientRegistrationMapper#toListWardsByDepartmentResponse` | ② | — |

### 3.7 IF-IP-REG-05 `loadWardBedCapacity`（病区床位余量查询）

**HTTP**：`POST /api/app/inpatientRegistration/loadWardBedCapacity`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `wardLocationId` | Long | Y | 病区位置 ID |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `wardLocationId` | Long | 病区 ID |
| `totalBedCount` | Long | 总床位数（`countTotalBedsByWard`；仅 `HOUSE`→`BED` 树） |
| `vacantBedCount` | Long | 空床数（`countBedsByWard(wardId, true)`；`ACTIVE`+`NOT_IN_USE`） |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `wardLocationId` | — |
| 2 | Pre | `LocationQueryService#countTotalBedsByWard` | `wardLocationId` | 总床：病区下 `HOUSE` 内 `ACTIVE` 的 `BED` |
| 3 | Pre | `LocationQueryService#countBedsByWard` | `wardLocationId` | 空床：`operational_enum=NOT_IN_USE` |
| 4 | Asm | `InpatientRegistrationMapper#toLoadWardBedCapacityResponse` | ②③ | 前端消费：区段标题 `空床 {vacantBedCount} / 总床 {totalBedCount}`；**禁止**表单网格床位数重复展示 |

### 3.8 IF-IP-REG-07 `switchEncounterToInsurance`（自费转医保）

**HTTP**：`POST /api/app/inpatientRegistration/switchEncounterToInsurance`

**Request**（`SwitchInpatientToInsuranceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `contractId` | Long | Y | 目标省/市医保费用性质（合同）ID |
| `attendingDoctorId` | Long | Y | 主治医生 ID（须含 `yb_no`，供 2401 `atddr_no`） |
| `operatorId` | Long | Y | 操作人 ID |
| `psnNo` | String | Y | 人员编号（1101/读卡） |
| `ybMediumType` | String | N | 就诊凭证类型 |
| `ybCardInfo` | String | N | 就诊凭证编号 |
| `insuplcAdmdvs` | String | N | 参保地医保区划 |
| `insutype` | String | N | 险种类型 |
| `medType` | String | N | 医疗类别；缺省 `21` 普通住院 |

**Response**（`SwitchInpatientToInsuranceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 是否成功 |
| `ybMdtrtId` | String | 医保就诊 ID（2401 成功时） |
| `switchedChargeItemCount` | Integer | 本次切换至医保账户的收费行数 |
| `insuranceAccountId` | Long | 医保挂账账户 ID |
| `messageCode` | String | 语义码（成功时可为空） |

**关键校验**

- **BR-IP-REG-019**：`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`；无双账户；未 2401；无 `SETTLED` 合计 > 0。
- `contractId` 须为第三方医保合同（`InpatientChargeInsuranceExtension#supportsThirdPartyContract`）。
- `psnNo` 非空；`attendingDoctorId` 对应 `Practitioner.yb_no` 非空。

**失败码口径**

| 语义码 | 场景 |
| --- | --- |
| EX-IP-REG-201 | 已是医保入院 / 已 2401 |
| EX-IP-REG-202 | 状态不允许 |
| EX-IP-REG-203 | 存在已结算费用 |
| EX-IP-REG-204 | 未读卡 |
| EX-IP-REG-205 | 主治医生无效或缺 yb_no |
| EX-IP-REG-206 | 扩展未启用 / 非医保合同 / 2401 失败 |

**Support**

- `InpatientRegistrationInsuranceSwitchSupport#switchUnsettledChargeItemsToInsuranceAccount`：草稿/待收费/待结算行批量 `switchChargeItemAccount`（排除挂号锚点）。

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数 + 扩展启用 | `encounterId`/`contractId`/`psnNo` | 扩展未启用 → EX-IP-REG-206 |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | EX-IP-REG-102 |
| 3 | Pre | `assertSwitchToInsuranceAllowed` | 状态、2401、已结算合计 | EX-IP-REG-201～203 |
| 4 | Pre | `ContractQueryService#findContractById` | 第三方合同 | EX-IP-REG-002/206 |
| 5 | Pre | `assertAttendingDoctorIdForInsurance` | `attendingDoctorId` | EX-IP-REG-205 |
| 6 | Cmd | `EncounterChargeAccountOpener#ensureEncounterChargeAccounts` | 开 `MEDICAL_INSURANCE` | 保留个人现金户 |
| 7 | Cmd | `saveAttendingDoctorOnSwitch` | ADMITTER 角色参与者 | 供 2401 |
| 8 | Cmd | `InpatientChargeInsuranceExtension#ensureAdmission` | 2401；**入院时间=原就诊** | EX-IP-REG-206 |
| 9 | Cmd | `InpatientRegistrationInsuranceSwitchSupport#switchUnsettledChargeItemsToInsuranceAccount` | 未结 ChargeItem | — |
| 10 | Asm | `InpatientRegistrationMapper#toSwitchInpatientToInsuranceResponse` | mdtrtId、行数、账户 ID | — |

**前端**

- API：`#/api/inpatient-registration` → `httpSwitchInpatientToInsurance`
- 页面：`views/inpatient/charge/register/index.vue`；`dialogMode=switchInsurance`；复用 `useRegisterBenefit` 读卡与待遇预览

## 4. QueryService 与事件刷新

### 4.1 登记列表状态筛选与读侧过滤（IF-IP-REG-01）

> **产品口径**：页面为**同一列表** + 状态下拉/筛选项，**不是** Tab 分页；**默认选中「待登记」**。

| `registrationListStatus` | 产品语义 | 读侧条件（目标态） |
| --- | --- | --- |
| `PLANNED`（`InpatientRegistrationConstants.LIST_STATUS_PLANNED`，**默认**） | 待登记 | `status_enum = PLANNED` |
| `WAIT_ADMISSION` | 待入科 | `status_enum = REGISTERED` |
| `ADMITTED` | 已入科 | `status_enum = IN_PROGRESS` |

实现落点：`InpatientEncounterPageQuery.registrationListStatus` + **`EncounterContextReader#pageInpatientRegQueue`**。关键字/身份证与状态 **AND** 组合；不新增 IF。

### 4.2 事件 → QueryService（本场景相关）

| 事件 | 触发 IF | QueryService动作 |
| --- | --- | --- |
| `InpatientAdmissionRequestedEvent` | 上游 AS-OP-DOR-005 | `EncounterQueryService` **INSERT/UPSERT** `InpatientEncounterContextQueryModel`（待登记，含 `ward_id`←`event.wardId`；见QueryService §4.2） |
| `InpatientAdmissionCompletedEvent` | IF-IP-REG-03 | **UPDATE** `patient_id`、`encounter_bus_no`、`status_enum=REGISTERED`、`status_enum_text=待入科`、`ward_id`、`start_time`；人口学经领域事件 §4.4 回查 `adm_patient`（非事件载荷） |
| `InpatientAdmissionCancelledEvent` | IF-IP-REG-04 | **UPDATE** 取消态 / 列表不可见 |
| `InpatientBedAssignedEvent` | 非本 AS（**AS-IP-NUR-002**） | **UPDATE** `status_enum`、`bed_id`/`bed_name`；**不**改 `ward_id`（病区仍由本 AS 申请/登记领域事件载荷，**BR-IPI-004**） |
| `AdvanceDepositCollectedEvent` / `InpatientPaymentSettledEvent` | IF-IP-REG-03（有预交金） | 费用摘要列（QueryService 组装职责见DM §6.2） |
| `AccountBalanceEditedEvent` | IF-IP-REG-03 序号 14（有预交金） | 患者栏余额 |

字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §4.2 为唯一真源；本 SDD 不重复字段表。

### 4.5 患者人口学投影（QueryService §4.4）

| 时机 | 领域事件 | 查询服务 |
| --- | --- | --- |
| `InpatientAdmissionRequestedEvent` / `InpatientAdmissionCompletedEvent` 处理末尾 | `EncounterQueryService 人口学列由 `PatientQueryService#mapInpatientProjectionSnapshotsByIds` 组装` | `PatientQueryService#mapInpatientProjectionSnapshotsByIds` |

登记/预交金列表：`InpatientInfoQueueDisplayAssembler` 仅对**历史空列**请求级回填；新登记以投影为准。身份证不在QueryService 查询列（**DM-ADM-001 BR-PAT-023**），患者卡片由 App 按 `patient_id` 查主档。

### 4.4 `status_enum_text` 投影与应用装配（IF-IP-REG-01/02）

| 层级 | 类/方法 | 规则 |
| --- | --- | --- |
| 领域事件 | `EncounterQueryService 状态展示装配（`InpatientEncounterStatusDisplayUtil`）` | SET `status_enum` 后调用 `InpatientEncounterStatusDisplayUtil.resolveDisplayTextForContext` 写入 `status_enum_text` |
| 展示组装 | `InpatientInfoQueueDisplayAssembler` | 对当前页行按 `status_enum` + `transfer_org_id` + `discharge_type_enum` 计算 `impStatusText` |
| Mapper | `InpatientRegistrationMapperImpl#toRegistrationQueueItemDto` | `statusEnumText` = overlay.impStatusText **优先**，否则QueryService 查询列；**禁止** `EncounterStatus#getDisplay()` |
| 权威 | `DM-ADM-002` §2.3.2；`AS-IP-FIN-001` **BR-IP-REG-014** | 与预交金（**AS-IP-FIN-002-SDD**）、护士站（**AS-IP-NUR-001-SDD**）队列 DTO 同口径 |

### 4.3 `ambEncounterId` 回填（IF-IP-REG-01/02）

| 场景 | 数据来源 | 说明 |
| --- | --- | --- |
| 医生站住院申请（AS-OP-DOR-005） | `adm_encounter.amb_encounter_id`（住院线） | QueryService `InpatientEncounterContextQueryModel` **可不投影**该列；App **必须**从就诊主档补全，避免列表/弹窗误为 null |
| 无申请直登 | — | `ambEncounterId` 保持 **null** |
| IF-IP-REG-01 列表 | `EncounterQueryService#mapAmbEncounterIdByEncounterIds` | 对当前页 `encounterId` **批量**查询后传入 Mapper |
| IF-IP-REG-02 弹窗 | `EncounterQueryService#mustGetEncounterOfClass`（`IMP`）→ `EncounterDetailQueryModel` | 与QueryService行合并装配 `RegistrationEncounterDto` |

领域查询模型：`EncounterDetailQueryModel` 含 `busNo`、`admitSourceCode`、`priorityEnum`、`ambEncounterId`（与 `EncounterQueryService#toDetailModel` 对齐）。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号（§3） | 领域调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-IP-REG-03 | `submitInpatientRegistration` | 6～7 | Patient 建档/更新 | Patient |
| IF-IP-REG-03 | 同上 | 8a/9 | `registerDirectInpatientEncounter` 或 `registerInpatientEncounter` | Encounter + EncounterLocation |
| IF-IP-REG-03 | 直登 | 8a | `saveAdmissionDx` | EncounterDiagnosis / Condition |
| IF-IP-REG-03 | 有申请 | 9 | 已有主诊则跳过诊断写入 | — |
| IF-IP-REG-03 | 同上 | 11～14 | 预交金收/结/调余额（可选） | PaymentReconciliation、Account |
| IF-IP-REG-04 | `cancelInpatientRegistration` | 5 | `cancelInpatientAdmission` | Encounter |

### 5.2 事务边界与失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-IP-REG-03 | **产品期望**：单事务覆盖序号 5～15 | 任一步 Cmd 失败则登记、开户、支付、余额均回滚 |
| IF-IP-REG-04 | **产品期望**：单事务 | 取消失败不改变就诊态 |
| IF-IP-REG-01/02/05 | 无事务 | 只读 |

**DOC-CONFLICT（成码口径，与 `InpatientRegistrationAppService` 类 JavaDoc 一致）**

| 项 | SDD/AS 期望 | 成码实现 |
| --- | --- | --- |
| App 层 `@Transactional` | §5.2 表内「单事务」 | **禁止**（《代码生成通用规范》）；`InpatientRegistrationAppService` **不**声明事务注解 |
| 实际事务边界 | — | 由 `PatientDomainService`、`EncounterDomainService`、`PaymentReconciliationDomainService` 等各域写方法自带事务承担；与 `DoctorStationDiagnosisAppService` 口径一致 |
| 关闭条件 | 若产品强制「登记+预交金」跨域原子回滚 | 在领域层或统一编排门面补事务，**不在** AppService 上加 `@Transactional` |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 重复提交登记：同一 `encounterId` 且非 `PLANNED` 时拒绝（EX-IP-REG-003） | `InpatientRegistrationAppService#submitInpatientRegistration` Pre-8b | §3.4 序号 8b |
| 支付幂等以支付流水号/草稿态为准 | `PaymentReconciliationDomainService` | §3.4 序号 13～14 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 同一 `PLANNED` 行双窗口登记：悲观行锁 + 锁内 `PLANNED` 断言；后写者 → **EX-IP-REG-003**（GAP-ATD-004） | `EncounterDomainService#registerInpatientEncounter`；`InpatientRegistrationAppService` 异常映射 | §3.4 序号 9 |

### 6.3 重试与容错

| 承诺 | 说明 |
| --- | --- |
| QueryService最终一致 | 列表在领域事件载荷后刷新；提交响应取自 `mustGetEncounterOfClass`（`IMP`，§3.4 序号 14），不等待 `InpatientEncounterContextQueryModel` 投影 |

### 6.4 数据脱敏

| 承诺 | 说明 |
| --- | --- |
| 身份证展示 | 列表/详情按机构脱敏规则（与挂号一致，App 层 Asm 可选） |

### 6.5 审计日志

| 承诺 | 关联步骤 |
| --- | --- |
| `operatorId`/`registrarId` 来自 `CurrentOperatorResolver`（请求可覆盖） | §3.4 序号 8a/9 |
| 取消原因写入 `CancelInpatientAdmissionCommand` | §3.5 序号 5 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 列表状态筛选（默认待登记） | 与 §4.1 一致；非 Tab | P0 |
| 无档登记入口 | 打开空弹窗提交 IF-IP-REG-03 | P0 |
| 有申请登记 + 可选预交金 | `REGISTERED`；金额>0 收款；**不传** `admissionDiagnoses`；诊断与申请一致 | P0 |
| 待入科/已入科「查看」 | `readOnly=true`；待入科列表行有「查看」按钮 | P0 |
| 门诊转住院门诊医生 | `ambEncounterId` 非空时 `outpatientDoctorName` 只读展示 | P0 |
| 入科床位展示 | `bedId` 有值时展示 `wardName/bedName`；未分床不展示；无表单床位数 | P0 |
| 无档直登、无申请直登 | 患者新建 + 登记；**必选**主诊断并落库 | P0 |
| 有申请登记误传诊断列表 | 不重复主诊（跳过或拒绝，**BR-ENC-014**） | P0 |
| 取消 `PLANNED` / 待入科未分床 | `CANCELLED`；释放门诊关联 | P0 |
| 有余额取消 / 已分床取消 | EX-IP-REG-005 / 006 | P0 |

### 7.2 非功能测试

| 用例 | 指标 | 优先级 |
| --- | --- | --- |
| 列表查询 | ≤1000ms（AS §7） | P1 |
| 登记提交含收款 | ≤2500ms | P1 |

### 7.3 QueryService一致性

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 登记提交后刷新列表 | 行变为待入科 | P0 |
| 医生申请事件后 | 默认待登记列表可见；含意向病区时行展示 `wardId`/`wardName` | P0 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| AS-OP-DOR-005 申请 → 本服务登记 | 端到端 `PLANNED→REGISTERED`（分床后 `IN_PROGRESS`） | P0 |
| 取消后医生站可再申请 | `ambEncounterId` 释放 | P0 |
| 自费登记 → 读卡转医保 | 2401 成功；未结费用挂医保户；预交金仍在现金户 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 应用层 IF-01～05 自动化用例 | `InpatientRegistrationAppService` 已生成；集成/契约测试待补 | P1 |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.26 | 2026-10-15 | **BR-IP-REG-025**：`RegistrationPatientDto`/`SubmitRegistrationRequest` 增人口学（国籍/民族/籍贯/婚姻/职业/现住址/工作单位/联系人）；`toModifyPatientProfileCommand` 三参叠写；登记后病案 bootstrap 自主档回填 `curr_addr_codes`/`napl_codes` 等；对齐 **AS v1.25** |
| v1.25 | 2026-10-14 | **BR-IP-REG-024**：未开户按 `chargeIntent` 预填费用性质（医保默认市医保）；弹窗不单独展示意向字段；对齐 **AS v1.24** |
| v1.24 | 2026-10-14 | `RegistrationEncounterDto`/`RegistrationQueueItemDto` 增 `chargeIntent`/`chargeIntentText`；**BR-IP-REG-024**；对齐 **AS v1.23** |
| v1.23 | 2026-10-12 | §2.1 入院诊断口径：申请/直登不展开国临；登记只读优先医保名；展开延后住院医生站；对齐 **AS v1.15** |
| v1.22 | 2026-09-01 | **IF-IP-REG-07** `switchEncounterToInsurance`；`switchToInsuranceEnabled`；`InpatientRegistrationInsuranceSwitchSupport`；异常 EX-IP-REG-201～206；对齐 **AS v1.16** |
| v1.21 | 2026-08-13 | **`in_way_code` 对齐 adm_way**：登记字段 `inWayCode`（UI「入院途径」）字典 **1/2/3/9**；与 `admit_source_code`（入院类型）分列；V28 迁移与门诊转住院默认 **2** |
| v1.20 | 2026-07-08 | **中医主诊只读回显**：`DiagnosisSnapshotDisplayResolver#resolvePrimaryDisplayName` 合并一病一症；`description` 用户未填时不传 |
| v1.19 | 2026-06-25 | **IF-IP-REG-00** `loadInpatientRegistrationPageInit`：`paymentChannelOptions`（**ADR-PAY-001** / `PaymentChannelOptionProvider`） |
| v1.18 | 2026-06-17 | 对齐 **AS v1.12** 成码：**BR-IP-REG-016** `readOnly` 含 `REGISTERED`；**BR-IP-REG-017** `outpatientDoctorName` + Pre 解析 ADMITTER；**BR-IP-REG-018** `bedId`/`bedName` 与 UI 空床/入科床位口径；§3.3 DTO/编排序列/关键校验更新 |
| v1.17 | 2026-06-09 | **IF-IP-REG-02**：响应增 `organizationOptions`；编排序列增 `listOrganizationsForInpatientAdmission`；Mapper 签名扩展；对齐 **AS v1.11** / **BR-IP-REG-015** |
| v1.16 | 2026-05-28 | **六维对账**：`InpatientRegistrationAppService` 六 public 方法与 IF-IP-REG-01～05 对齐；IF-IP-REG-06 编排落门诊挂号 App（住院 App 无同名方法）；§2.1 IF 表合并 IF-05 双方法行；编排权威 **AS v1.10** |
| v1.15 | 2026-05-28 | **IF-IP-REG-06**：`pagePatientsForSelect` 实现落 **`OutpatientRegistrationAppService`**，HTTP **`/api/app/outpatientRegistration/...`**（修正原 `inpatientRegistration` 路径）。 |
| v1.15.1 | 2026-05-28 | IF-IP-REG-01 序列/§2.3：**`EncounterContextFacade#pageInpatientRegQueue`**（替代 `EncounterQueryService#page*` 叙述） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1 `EncounterContextFacade` + `InpatientEncounterContextQueryModel`；§3/§4 与 `zxplat` 一致 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-18 | 初版：对齐 `AS-IP-FIN-001` v1.1；IF-IP-REG-01～05；§2.5 `EncounterQueryService#pageInpatientRegQueue`～006 |
| v1.1 | 2026-05-18 | 产品裁决：本期全做 IF-01～05；`registrationListStatus` 替代 Tab；默认待登记；无档登记；预交金仅登记弹窗金额；对齐 AS v1.2 |
| v1.2 | 2026-05-18 | 对齐 AS v1.3：登记针对住院 Encounter；删除「空则引门诊主诊」后端分支 |
| v1.3 | 2026-05-18 | 对齐成码：`ambEncounterId` 主档回填（§4.3）；Mapper 签名与编排序列；IF-03 响应 `busNo` 写侧即时；§5.2 DOC-CONFLICT（App 无 `@Transactional`）；应用层已生成 |
| v1.4 | 2026-05-19 | 对齐 AS v1.4 |
| v1.6 | 2026-05-19 | IF-IP-REG-03：预交金 `settle` 后改余额对齐 `AccountDomainService#adjustAdvanceDepositBalance`（与 **AS-IP-FIN-002-SDD** v1.2 一致） |
| v1.5 | 2026-05-19 | 对齐 **`DM-ADM-002` v1.11** 与成码：`PLANNED→REGISTERED`；`registerDirectInpatientEncounter`；列表 `WAIT_ADMISSION`/`ADMITTED` 谓词；取消 `REGISTERED` |
| v1.6 | 2026-05-19 | 对齐 AS **v1.6**：有申请登记诊断只读、提交不重写；直登 `admissionDiagnoses` 必填；§3.4 编排序列与写库点表；**BR-IP-REG-007** / **BR-ENC-014** |
| v1.9 | 2026-05-20 | 新增 **IF-IP-REG-06** `pagePatientsForSelect`（§3.2A）；建档 `toRegisterPatientCommand` + `registerPatient` 机构口径；移除「复用门诊 HTTP」表述 |
| v1.8 | 2026-05-20 | §2.3 IMP 展示文案与 **BR-IP-REG-014**；IF-IP-REG-01 `statusEnumText` 装配规则；IF-IP-REG-03 `resolvePatientIdForSubmit` 患者分流；对齐 **DM-ADM-002 v1.17**、QueryService **v2.9**、`EncounterQueryService 状态展示装配（`InpatientEncounterStatusDisplayUtil`）` |
| v1.7 | 2026-05-20 | IF-IP-REG-01/02：`InpatientInfoQueueDisplayAssembler` 展示回填；IF-IP-REG-02：主诊断结构化字段（移除 `diagnosisJson`）；QueryService补齐 `InpatientAdmissionRequestedEvent` 的 `encounter_bus_no`/`status_enum_text`（见QueryService §4.2） |
| v1.11 | 2026-05-20 | 新增 §4.5：登记/申请事件后QueryService回查 `adm_patient` 写入人口学；§4.2 登记完成映射补 `patient_id`/busNo；对齐QueryService **v3.1** |
| v1.10 | 2026-05-20 | `InpatientAdmissionCompletedEvent` 增补 `wardId`/`wardName`；QueryService写入 `InpatientEncounterContextQueryModel.ward_id`（闭合登记后护士站地理筛选，见QueryService §4.2） |
| v1.13 | 2026-05-20 | §2.1 入院诊断口径：`saveAdmissionDx` 固定 `diagTypeCode=31`（**BR-ENC-021**）；对齐 **DM-ADM-002** v1.21、AS **v1.10** |
| v1.12 | 2026-05-20 | §4.2：`InpatientAdmissionRequestedEvent` 投影 `ward_id`；待登记列表/弹窗 `wardId` 以QueryService为准（对齐 **DM-ADM-002** v1.19、QueryService **v3.2**） |
| v1.14 | 2026-05-24 | §4.2：`InpatientBedAssignedEvent` 不覆盖 `ward_id`；护士站地理筛选依赖本 AS 登记/申请投影（QueryService **v3.6** **BR-IPI-004**） |
| v1.11 | 2026-05-20 | **Gap 收口**：删除 §2.5 SDD-GAP 表；正文 SDD-GAP 引用改为领域方法/QueryService锚点；对齐 AS/SDD Skill |

---

**文档负责人**：（待填） · **评审**：（待填）
