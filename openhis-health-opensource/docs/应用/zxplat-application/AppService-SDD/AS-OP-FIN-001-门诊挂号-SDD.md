# 门诊挂号 · 应用服务详细设计（SDD）

> 编码：AS-OP-FIN-001-SDD  
> 版本：v3.39 | 日期：2026-10-13 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 门诊挂号应用服务（挂号收费窗口） |
| 边界 | 编排 Patient / Encounter / Account / ChargeItem / PaymentReconciliation / Invoice / QueryService，不在应用层实现聚合不变式 |
| 编排权威 | `AS-OP-FIN-001-门诊挂号.md`（**v2.42**）§2、§4、§9 |
| 方法权威 | 对应 DM §6（`*DomainService` / `*QueryService`）与DM §6.2 §3 查询契约 |
| 严格对齐 | IF 编号严格对齐 AS 的 IF-REG-01~11 |
| 实现阶段口径 | **已落地**（`OutpatientRegistrationAppService`；IF-REG-03 并入 IF-REG-06） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 字段仅基于 AS 语义、DM 能力与QueryService查询契约 |
| QueryService口径 | IF-REG-08 使用 `ClinicRegister`（行内挂号费另见写模型 `ChargeItem`，见 §3.9）；IF-REG-11 使用 `RegistrationSlip` |
| 事务口径 | 事务边界在 §5 以 Cmd 顺序描述；Pre/Gate 失败禁止进入 Cmd |
| 就诊读侧隔离 | 退号链（IF-REG-09/10）加载就诊主档须 **`encounterId` + `classEnum=AMB`**（`EncounterQueryService#mustGetEncounterOfClass`）；禁止仅用主键命中住院线；类别不符 → **`ENCOUNTER_INVALID_STATUS`** |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/registration/
  service/
    OutpatientRegistrationAppService.java
  request/
    LoadRegisterFormInitRequest.java
    PagePatientsForSelectRequest.java
    SavePatientForFirstRegisterRequest.java
    ListDepartmentsRequest.java
    ListPractitionersRequest.java
    PreRegisterSettlementRequest.java
    PayRegisterFeeRequest.java
    PageClinicRegistersRequest.java
    ValidateCancelableRequest.java
    CancelWithRefundRequest.java
    PrintRegisterTicketRequest.java
  response/
    LoadRegisterFormInitResponse.java
    PagePatientsForSelectResponse.java
    SavePatientForFirstRegisterResponse.java
    ListDepartmentsResponse.java
    ListPractitionersResponse.java
    PreRegisterSettlementResponse.java
    PayRegisterFeeResponse.java
    PageClinicRegistersResponse.java
    ValidateCancelableResponse.java
    CancelWithRefundResponse.java
    PrintRegisterTicketResponse.java
  dto/
    PatientItemDto.java
    ChargeItemPreviewDto.java
    PaymentDetailDto.java
    ClinicRegisterItemDto.java
    RegistrationSlipDto.java
  mapper/
    OutpatientRegistrationMapper.java
    OutpatientRegistrationMapperImpl.java
  support/
    OutpatientHistoricalFollowUpRegistrationGate.java
    OutpatientRegistrationCancelGate.java
```

> 包名：`com.zxplat.domain.application.outpatient.financial.registration`（门诊财务域 **AS-OP-FIN-001**，与 **AS-OP-FIN-002** `financial/charge`、**AS-OP-FIN-003** `financial/refund` 同级）。  
> **HTTP** 仍由 `AppServiceControllerRegistrar` 按类名暴露：`POST /api/app/outpatientRegistration/{methodName}`（与 Java 包路径无关）。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `PatientDomainService` / `PatientQueryService` | 患者检索、建档、详情快照（`loadPatientDetail`）、复用时回写联系电话（`updatePatientPhone`） |
| `CurrentOperatorResolver` | 写路径操作人（挂号员 `operatorId`/`entererId`，B.6.1） |
| `CurrentManagedOrganizationIdsResolver` | 患者建档机构（`requireSessionOrganizationUnitId`，B.6.1） |
| `OrganizationQueryService` / `PractitionerQueryService` | 科室、医生查询 |
| `ContractQueryService` / `HealthcareServiceQueryService` | 费用性质、号源查询 |
| `EncounterDomainService` / `EncounterQueryService` | 就诊防重、创建、退号 |
| `AccountDomainService` | 就诊账户开立（IF-REG-07） |
| `AccountQueryService` | IF-REG-07：解析默认个人现金结算账户（`getRegistrationAccount`，内部 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode()`**） |
| `ChargeItemDefinitionQueryService` | 挂号定价查询 |
| `ChargeItemDomainService` / `ChargeItemQueryService` | 预结算、收费、退费、收费项校验；IF-REG-08 本页就诊批量拉取明细（`listChargeItemsByEncounterIds`） |
| `PaymentReconciliationDomainService` / `PaymentReconciliationQueryService` | 支付草稿、结算、退款 |
| `InvoiceDomainService` / `InvoiceQueryService` | 开票回写、冲红与票据校验 |
| `InvoiceAfterPaymentOrchestration` | 支付后条件开票编排（**BR-INV-008**） |
| `EncounterContextReader` | IF-REG-08：`pageClinicRegister`（内部 **`ClinicRegistrationLoader`**）；IF-REG-11：`getRegistrationSlipByEncounterId` / `pageRegistrationSlip` |
| `PractitionerRoleScopeLoader` | IF-REG-01/05：`listActivePractitionersForOutpatientRegistration` → `listActivePractitionersByResolvedRoleScope` |
| `WhaleRoleLookup` | IF-REG-01/05：`ROLE_DOCTOR` → `whale_roles.id` |
| `CodeSystemQueryService` | 术语枚举转下拉（IF-REG-01：`AdministrativeGender` → `OptionDto`，与 `PatientAppService#init` 性别同源） |
| `OutpatientRegistrationMapper` | DTO 装配；公共库 `OptionDto` 直接复用，不在本模块声明 |
| `ThirdPartyPaymentExtension` | **可选**（`-Pwith-unionpay-bpc`）：IF-REG-07 扫码、IF-REG-10 原路退；未装配时为 NoOp |
| `ThirdPartyPaymentSettlesupport | IF-REG-07：`clientScanResult` → applyClientScanResult → 语义码 |
| `ThirdPartyPaymentRefundsupport | IF-REG-10：退号原路退 `clientRefundResult` |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PatientQueryService` | `searchPatientsPaged`、`checkRegistrationDuplicatePatient`、`loadPatientDetail`、`findPatientByIdCard` | IF-REG-02/03/06 |
| `PatientDomainService` | `registerPatient`、`updatePatientPhone`、`updatePatientAddress` | IF-REG-06（未命中建档 / 复用时回写联系电话 **BR-REG-018**、地址全文 **BR-REG-019**） |
| `OrganizationQueryService` | `listOrganizationsForOutpatientRegistration`、`loadOrganizationByIdForDisplay` | IF-REG-01/04/06 |
| `PractitionerQueryService` | `listActivePractitionersByResolvedRoleScope`、`findById` | IF-REG-01/05/06 |
| `PractitionerRoleQueryService` | （经 **`PractitionerRoleScopeLoader`**）`listPractitionerIdsWithActiveRoleId`、`listPractitionerIdsWithActiveRoleIdInOrganizations` | IF-REG-01/05 Pre |
| `ContractQueryService` | `listContracts`、`findContractById` | IF-REG-01/06 |
| `HealthcareServiceQueryService` | `listServicesForRegistration`、`loadHealthcareServiceById`、`isHistoricalFollowUpService` | IF-REG-01/06 |
| `CodeSystemQueryService` | `getOptionListByEnum`（`AdministrativeGender.class`，IF-REG-01） | IF-REG-01 |
| `EncounterQueryService` | `checkOutpatientDuplicateRegistration`、`existsQualifyingHistoricalFollowUpPriorVisit`（**BR-REG-020**）、`mustGetEncounterOfClass`（`AMB`，IF-REG-09/10） | IF-REG-06/09/10 |
| `EncounterDomainService` | `registerOutpatientEncounter`、`completeOutpatientRegistration`、`cancelOutpatientRegistration` | IF-REG-06/07/10 |
| `AccountDomainService` | `openEncounterAccounts` | IF-REG-07 |
| `AccountQueryService` | `getRegistrationAccount`（及读侧辅助方法，见 **`DM-ADM-009`**） | IF-REG-07 |
| `ChargeItemDefinitionQueryService` | `findSaleLineForHealthcareServiceRegistration` | IF-REG-06 |
| `ChargeItemDomainService` | `preSettleChargeItems`、`createRegistrationChargeItem`、`settleChargeItems`、`returnRegistrationRefundChargeItems` | IF-REG-06/07/10 |
| `ChargeItemQueryService` | `listChargeItemsByEncounterId`（IF-REG-09 · 排除 `adm_healthcare_service` + `excludeSupersededSettledLines`）；`listChargeItemsByEncounterIds`（IF-REG-08） | IF-REG-08/09 |
| `OutpatientRegistrationCancelGate` | `evaluateClinicalBillingBlock`（IF-REG-09 · BR-REG-015 账单三态编排） | IF-REG-09 |
| `OutpatientHistoricalFollowUpRegistrationGate` | `assertEligibleForHistoricalFollowUpRegistration`（IF-REG-06 · **BR-REG-020**） | IF-REG-06 |
| `PaymentReconciliationQueryService` | `mustGetPayment`、`findPaySuccessOrRefundingByEncounter`、`findDraftPaymentIdsByEncounterIds` | IF-REG-07/08/09 |
| `PaymentReconciliationDomainService` | `createPaymentDraftFromPreSettle`、`settleOutpatientPayment`、`requestOutpatientRefund`、`completeRegistrationCancelRefund`、`abortOutpatientRefundAttempt` | IF-REG-06/07/10 |
| `InvoiceAfterPaymentOrchestration` | `createDraftAndScheduleIssueIfPayable`（**BR-REG-017** / **BR-INV-008**） |
| `InvoiceDomainService` / `InvoiceQueryService` | `issueElectronicInvoice`、`writeoffInvoiceAndCreateRedRecord`、`getLatestIssuedInvoiceByPaymentId` | IF-REG-07/09/10 |
| `EncounterContextReader` | `pageClinicRegister` | IF-REG-08 |
| `EncounterContextReader` | `getRegistrationSlipByEncounterId`、`pageRegistrationSlip` | IF-REG-11 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002-Encounter-就诊` §2.3 状态机；§3.1 `adm_encounter.status_enum` | IF-REG-06 开立后为 **`PLANNED`（待挂号）**；IF-REG-07 成功后 **`REGISTERED`**；`CancelWithRefundResponse.encounterStatus` 等同源 **String code** |
| 就诊账户类型（落库） | `com.zxplat.domain.share.enums.AccountTypeCode`（`getCode()` → `adm_account.type_code`） | **`DM-ADM-009`** §4.3 / §6.1 | IF-REG-07 默认结算户为 **`PERSONAL_CASH_ACCOUNT`**；**`REG`** 仅发票草稿等非 `adm_account` 主路径（见 DM） |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3.1 `adm_encounter.class_enum`；§4.1 | 门诊挂号场景为 `AMB` |
| 收费明细状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011-ChargeItem-收费明细` §2.3；§3.1 `adm_charge_item.status_enum` | IF-REG-06/07/10：`PLANNED`→`SETTLED` / 退费等 |
| 支付流水状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002-PaymentReconciliation-支付流水` §2.3；§3.1 `fin_payment_reconciliation.status_enum` | IF-REG-06 草稿 `DRAFT`、IF-REG-07 `SUCCESS`、IF-REG-10 退款链路等 |
| 支付业务类型 | `com.zxplat.domain.share.enums.PaymentKind` | `DM-FIN-002` §3.1 `kind_enum` | 挂号支付草稿/结算 |
| 付款类别（主记录） | `com.zxplat.domain.share.enums.PaymentType` | `DM-FIN-002` §3.1 `payment_enum`；§4.3 | 支付主记录业务分类 |
| 支付方式（明细） | `PaymentDetailDto.payChannelCode` → 领域装配 `PaymentRecDetail.payEnum` | `DM-FIN-002` §3.2 `PaymentRecDetail.pay_enum`；§4.7 | IF-REG-07；API 为 **String 渠道编码**，落库映射 `Integer` 与 DM 一致 |
| 患者性别（建档入参） | `PreRegisterSettlementRequest.gender` | `DM-ADM-001-Patient-患者` §3 主表 `gender_enum`；§4.2 AdministrativeGender | IF-REG-06；请求为 String，命令/持久化 **code** 口径见领域 SDD |
| 患者性别（挂号页初始化下拉） | `LoadRegisterFormInitResponse.genderOptions` | `com.zxplat.domain.share.enums.AdministrativeGender`；`CodeSystemQueryService#getOptionListByEnum` | IF-REG-01；与患者管理 `PatientInitDto.sex` 同源 **OptionDto** 装配 |
| 结算状态（出参） | `PayRegisterFeeResponse.status` | `DM-FIN-002` §2.3 / §3.1 `status_enum`（与 `PaymentStatus` 同源） | IF-REG-07 |
| 退号原因（文本） | `CancelWithRefundRequest.cancelReason` | `DM-ADM-002` §6.1.6 `cancelOutpatientRegistration`（聚合内记录取消原因）；§5 `OutpatientRegistrationCancelledEvent` | IF-REG-10 |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向（白名单 -> §3序列） | PASS | 白名单方法均在至少一个 IF 编排序列中出现 |
| 反向（§3序列 -> 白名单） | PASS | 各 IF 的 Cmd/Pre/Query 调用均可在 §2.2 找到同名条目 |
| 互斥步骤对检查 | PASS | IF-REG-06（刷新/创建草稿）与 IF-REG-11（按 encounterId/slipNo 分流）均双侧写明条件与跳转 |

## 3. 按接口 ID 命名展开

先读完每个 IF 的“领域编排序列（严格顺序）”，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-REG-01 | `loadRegisterFormInit` | 加载挂号页初始化 | `POST /api/app/outpatientRegistration/loadRegisterFormInit` |
| IF-REG-02 | `pagePatientsForSelect` | 患者分页检索 | `POST /api/app/outpatientRegistration/pagePatientsForSelect` |
| IF-REG-04 | `listDepartments` | 加载门诊科室下拉 | `POST /api/app/outpatientRegistration/listDepartments` |
| IF-REG-05 | `listPractitioners` | 加载医生下拉 | `POST /api/app/outpatientRegistration/listPractitioners` |
| IF-REG-06 | `preRegisterSettlement` | 确认挂号预结算 | `POST /api/app/outpatientRegistration/preRegisterSettlement` |
| IF-REG-07 | `payRegisterFee` | 支付挂号费正式结算 | `POST /api/app/outpatientRegistration/payRegisterFee` |
| IF-REG-08 | `pageClinicRegisters` | 门诊挂号列表查询 | `POST /api/app/outpatientRegistration/pageClinicRegisters` |
| IF-REG-09 | `validateCancelable` | 退号可否校验 | `POST /api/app/outpatientRegistration/validateCancelable` |
| IF-REG-10 | `cancelWithRefund` | 退号提交并联动退费 | `POST /api/app/outpatientRegistration/cancelWithRefund` |
| IF-REG-11 | `printRegisterTicket` | 补打挂号单 | `POST /api/app/outpatientRegistration/printRegisterTicket` |
| IF-REG-15 | `changeRegistrationDepartment` | 修改挂号科室 | `POST /api/app/outpatientRegistration/changeRegistrationDepartment` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁（`*QueryService` 等） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService` / Manager 等，持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | Mapper 纯 DTO 装配，无领域不变式 | — |

记法：`EncounterQueryService#mustGetEncounterDetail` 表示在 AppService 中调用注入 Bean 的该方法。

### 3.2 IF-REG-01 `loadRegisterFormInit`（加载挂号页初始化）

**HTTP**：`POST /api/app/outpatientRegistration/loadRegisterFormInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | N | 默认科室筛选 |
| `keyword` | String | N | 初始化关键字 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `departmentOptions` | `List<OptionDto>` | 科室下拉 |
| `practitionerOptions` | `List<OptionDto>` | 医生下拉 |
| `contractOptions` | `List<OptionDto>` | 费用性质下拉 |
| `serviceTypeOptions` | `List<OptionDto>` | 号源类型下拉 |
| `serviceTypeFeeById` | `Map<String, BigDecimal>` | 号源主键（String）→ 展示用挂号费（元）；**BR-REG-022**：按**全量 ACTIVE**号源绑定 AD SALE 汇总（空绑 0）；**不**随 `serviceTypeOptions` 医师收窄 |
| `genderOptions` | `List<OptionDto>` | 性别下拉（`AdministrativeGender`，与患者建档初始化同源） |
| `paymentChannelOptions` | `List<OptionDto>` | 用户可选支付渠道（**ADR-PAY-001**；`PaymentChannelOptionProvider`） |

**DTO 字段表：OptionDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `value` | String | Y | 选项值 |
| `label` | String | Y | 选项显示名 |
| `disabled` | Boolean | N | 是否禁用 |

**关键校验**

- 仅返回当前页面必需下拉项，不返回无关主数据。
- 医生下拉与 IF-REG-05 同口径：仅含在门诊科室（CLINIC）挂了激活医师岗的在职人员；未传 `organizationId` 时在全部门诊科室集合内收窄，不含仅在住院科室挂岗的医师。
- **BR-REG-001**：挂号医生统一可选；未选则挂到科室（不写 `REGISTRATION_DOCTOR`）。
- **号源类型（BR-REG-014）**：未传 `practitionerId` → 全部 ACTIVE；已传 → `PractitionerRoleServiceItem` 并集（医师级，不按科室分）；医师已选但未配置号源 → 返回空列表。**不与**当日 Schedule/Slot/`listBookableServiceTypes` 求交（手动挂号与用排班挂号解绑）。
- **挂号费映射（BR-REG-022）**：`serviceTypeFeeById` 在医师收窄**之前**对全量 ACTIVE 号源按绑定 AD SALE 汇总；空绑 0；与有绑预结算同口径。下拉 `serviceTypeOptions` 仍按 **BR-REG-014** 收窄。
- IF-REG-06 预结算：指定 `practitionerId` 时须 `assertPractitionerAcceptsHealthcareService`。
- IF-REG-06 占号（**BR-REG-016**）：仅请求显式携带 `slotId` 时 `allocateWalkInSlot`；无 `slotId` 的表单手动挂号不占号。
- 性别选项须与患者建档页同一枚举装配路径（`AdministrativeGender` → `OptionDto`），避免前后端码表分叉。

**Mapper 接口方法签名**

- `toLoadRegisterFormInitResponse(List<OptionDto>, List<OptionDto>, List<OptionDto>, List<OptionDto>, Map<String, BigDecimal>, List<OptionDto>): LoadRegisterFormInitResponse`（依次为科室、医生、费用性质、号源类型、号源费映射、性别）

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `organizationId`/`keyword` 可空 | 参数结构不合法直接拒绝 |
| 2 | Pre | `OrganizationQueryService#listOrganizationsForOutpatientRegistration` | 可选筛选条件 | 仅读路径 |
| 3 | Pre | `PractitionerRoleScopeLoader#listActivePractitionersForOutpatientRegistration(ROLE_DOCTOR, organizationId)` | 可选 `organizationId` | 仅读路径；口径同 IF-REG-05 |
| 4 | Pre | `ContractQueryService#listContracts` | — | 仅读路径 |
| 5 | Pre | `HealthcareServiceQueryService#listServicesForRegistration` | — | 全量 ACTIVE |
| 5b | Pre | `buildServiceTypeFeeById`（绑定 AD SALE 批量） | ⑤全量号源 | **BR-REG-022**；费用 map 不随医师收窄 |
| 5a | Pre | `PractitionerHealthcareServiceResolver#narrowByPractitioner` | 可选 `practitionerId` | 仅收窄下拉；**不读排班** |
| 6 | Pre | `CodeSystemQueryService#getOptionListByEnum` | `AdministrativeGender.class` | 术语枚举转 `OptionDto`；与 `PatientAppService#init` 中 `sex` 同源 |
| 7 | Pre | `PaymentChannelOptionProvider#loadUserSelectableOptions` | — | 交集 ∩ 枚举 ∩ 模块 Gate（**ADR-PAY-001**） |
| 8 | Asm | `OutpatientRegistrationMapper#toLoadRegisterFormInitResponse` | ②③④⑤a+5b⑥⑦结果 | 仅 DTO 装配；含 `serviceTypeFeeById`、`paymentChannelOptions` |
### 3.3 IF-REG-02 `pagePatientsForSelect`（患者分页检索）

**HTTP**：`POST /api/app/outpatientRegistration/pagePatientsForSelect`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 检索关键字 |
| `pageNo` | Integer | Y | 页码 |
| `pageSize` | Integer | Y | 页大小 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | 总条数 |
| `records` | `List<PatientItemDto>` | 患者列表 |

**DTO 字段表：PatientItemDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientId` | Long | Y | 患者ID |
| `patientBusNo` | String | Y | 患者业务号 |
| `name` | String | Y | 患者姓名 |
| `genderCode` | String | N | 性别编码（`AdministrativeGender#getCode()`） |
| `genderText` | String | N | 性别展示文案 |
| `birthDate` | `LocalDate` | N | 出生日期 |
| `age` | Integer | N | 周岁（由 `birthDate` 推算；缺失时为 `null`） |
| `idCardNoMasked` | String | N | 脱敏证件号 |
| `phoneMasked` | String | N | 脱敏手机号 |

**关键校验**

- `pageNo>0` 且 `pageSize` 在系统上限内。

**Mapper 接口方法签名**

- `toPagePatientsForSelectResponse(Page<PatientPageItemQueryModel>, List<PatientItemDto>): PagePatientsForSelectResponse`
- `toPatientItemDtoList(Page<PatientPageItemQueryModel>): List<PatientItemDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `pageNo`、`pageSize` | 参数非法直接失败 |
| 2 | Pre | `PatientQueryService#searchPatientsPaged` | `keyword` + 分页参数 | 仅读路径 |
| 3 | Asm | `OutpatientRegistrationMapper#toPagePatientsForSelectResponse` | ②结果 | 仅 DTO 装配 |

### 3.4 IF-REG-03（接口并入说明）

`savePatientForFirstRegister` 已并入 `IF-REG-06 preRegisterSettlement` 的前置患者分流编排，不再对外单独暴露接口。  
当前规则：优先复用 `patientId`；若未传 `patientId` 且传入 `idCardNo`，则按 `idCardNo` 精确命中既有患者；未命中或未传 `idCardNo` 时，以 `name/gender` 最小字段新建患者后继续预结算流程。

### 3.5 IF-REG-04 `listDepartments`（加载门诊科室下拉）

**HTTP**：`POST /api/app/outpatientRegistration/listDepartments`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 科室关键字 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `departmentOptions` | `List<OptionDto>` | 科室下拉 |

**DTO 字段表：OptionDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `value` | String | Y | 选项值 |
| `label` | String | Y | 选项显示名 |
| `disabled` | Boolean | N | 是否禁用 |

**关键校验**

- 返回集合仅含可挂号科室。

**Mapper 接口方法签名**

- `toListDepartmentsResponse(List<OptionDto>): ListDepartmentsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `keyword` 可空 | 参数结构非法直接失败 |
| 2 | Pre | `OrganizationQueryService#listOrganizationsForOutpatientRegistration` | 筛选条件 | 仅读路径 |
| 3 | Asm | `OutpatientRegistrationMapper#toListDepartmentsResponse` | ②结果 | 仅 DTO 装配 |

### 3.6 IF-REG-05 `listPractitioners`（加载医生下拉）

**HTTP**：`POST /api/app/outpatientRegistration/listPractitioners`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | Long | N | 科室筛选 |
| `keyword` | String | N | 医生关键字 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `practitionerOptions` | `List<OptionDto>` | 医生下拉 |

**DTO 字段表：OptionDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `value` | String | Y | 选项值 |
| `label` | String | Y | 选项显示名 |
| `disabled` | Boolean | N | 是否禁用 |

**关键校验**

- 若传 `organizationId` 则按该挂号科室过滤；未传则仅在**门诊科室（`OrganizationClassification.CLINIC`）**范围内解析激活医师岗，**排除**仅在住院科室挂医师岗的人员。

**Mapper 接口方法签名**

- `toListPractitionersResponse(List<OptionDto>): ListPractitionersResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `organizationId` 可空 | 参数结构非法直接失败 |
| 2 | Pre | `PractitionerRoleScopeLoader#listActivePractitionersForOutpatientRegistration(ROLE_DOCTOR, organizationId)` → 内部 `OrganizationQueryService#listOrganizationsForOutpatientRegistration`（未传科室时）→ `PractitionerRoleQueryService#listPractitionerIdsWithActiveRoleIdInOrganizations` / `#listPractitionerIdsWithActiveRoleId` → `PractitionerQueryService#listActivePractitionersByResolvedRoleScope` | 门诊科室 + 医师岗位 | 仅读路径 |
| 3 | Asm | `OutpatientRegistrationMapper#toListPractitionersResponse` | ②结果 | 仅 DTO 装配 |

### 3.7 IF-REG-06 `preRegisterSettlement`（确认挂号预结算）

**HTTP**：`POST /api/app/outpatientRegistration/preRegisterSettlement`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `patientId` | Long | N | 已选患者ID（有值时直接复用） |
| `name` | String | N | 患者姓名（仅 `patientId` 为空且需新建时必填） |
| `gender` | String | N | 性别（仅 `patientId` 为空且需新建时必填） |
| `idCardNo` | String | N | 身份证号（可选；有值时用于先查后建） |
| `phone` | String | N | 联系电话：新建时写入主档；复用既有患者时非空且与主档不同则 `updatePatientPhone` 回写（**BR-REG-018**） |
| `address` | String | N | 地址全文（身份证读卡户籍地址等，不拆省市区）：新建时写入主档 `Patient.address`；复用既有患者时非空且与主档不同则 `updatePatientAddress` 回写（**BR-REG-019**）；空串不覆盖 |
| `birthDate` | LocalDate | N | 出生日期（新建患者时写入） |
| `contractId` | Long | Y | 费用性质 |
| `organizationId` | Long | Y | 科室ID |
| `practitionerId` | Long | N | 医生ID |
| `serviceTypeId` | Long | Y | 号源类型ID |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 本次预结算关联的就诊主键（写模型初态 `PLANNED`，完成 IF-REG-07 后为 `REGISTERED`） |
| `paymentDraftId` | Long | 支付草稿ID |
| `totalAmount` | BigDecimal | 预结算总额（医保时等于 `ybPsnCashPay`） |
| `chargeItems` | `List<ChargeItemPreviewDto>` | 预结算明细 |
| `ybFundPaySumamt` / `ybAcctPay` / `ybPsnCashPay` / `ybMedfeeSumamt` | `BigDecimal` | 医保分摊摘要（2206；驱动 totalAmount / 支付草稿） |
| `ybActPayDedc` | `BigDecimal` | **只读展示**：实际支付起付线（2206 `act_pay_dedc`；**不**参与应收重算） |
| `ybCvlservFlag` | String | **只读展示**：公务员标志（2206 `cvlserv_flag`，0/1） |
| `ybCvlservPay` | `BigDecimal` | **只读展示**：公务员医疗补助资金支出（2206 `cvlserv_pay`；**不**参与应收重算） |

**DTO 字段表：ChargeItemPreviewDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chargeItemCode` | String | Y | 收费项编码 |
| `chargeItemName` | String | Y | 收费项名称 |
| `unitPrice` | BigDecimal | Y | 单价 |
| `quantity` | BigDecimal | Y | 数量 |
| `amount` | BigDecimal | Y | 金额 |

**关键校验**

- 先执行患者分流（有 `patientId` 直用 / 无则 `idCardNo` 命中复用 / 未命中新建）；**复用路径**若 `phone` 非空且与主档不一致，则 **`PatientDomainService#updatePatientPhone`** 回写主档（**BR-REG-018**，列表 `contactPhone` 读主档）；若 `address` 非空且与主档不一致，则 **`PatientDomainService#updatePatientAddress`** 回写主档地址全文（**BR-REG-019**，患者管理「详细地址」读 `Patient.address`）；再按 **`OutpatientSameDaySameDeptDuplicatePolicy`** 决定是否做 **BR-REG-011** 重复挂号校验（`EncounterQueryService#checkOutpatientDuplicateRegistration`：`OutpatientDuplicateCheckQuery` 含 **`patientId`、`organizationId`（挂号科室）**、**`registrationDate`**（与本次开立 `occurredAt` 同一日历日，通常取预结算编排起始时刻））；**产品默认**命中则 **`REG-BIZ-409`**；院点以常量启动注入 `allowsSameDaySameDeptMultiple=true` 时跳过读侧校验，并在开立命令置 **`skipSameDaySameDeptDuplicateCheck=true`**（领域同步跳过 **BR-ENC-011**；**禁止 yml**）。
- **BR-REG-020**：号源展示名「复诊0元号」（`HealthcareServiceConstants.NAME_HISTORICAL_FOLLOW_UP`；**按 `name` trim 精确匹配**，不按 `bus_no`）时，主数据加载后调用 **`OutpatientHistoricalFollowUpRegistrationGate#assertEligibleForHistoricalFollowUpRegistration`**；`EncounterQueryService#existsQualifyingHistoricalFollowUpPriorVisit` 判定同患者同科室、`reception_time` 起 72h 内、已接诊（`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`），排除复诊0元号自身；不满足 → **`REG-BIZ-420`**。
- **BR-REG-001**：`practitionerId` 可空（未选则挂到科室）；指定医师时须写活动 `REGISTRATION_DOCTOR`。
- 指定 `practitionerId` 时须 **`PractitionerHealthcareServiceSupport#assertPractitionerAcceptsHealthcareService`**（**BR-REG-014**）；未配置可接诊号源或号源越界 → **`PRACTITIONER_ROLE_RULE_CONFLICT`**。
- 新建患者分支的建档机构取当前登录机构；挂号就诊科室继续使用请求 `organizationId`。

**失败码口径**

| 错误码 | 触发条件 | 前端提示语义 | 是否可重试 |
| --- | --- | --- | --- |
| `REG-VAL-001` | 必填ID缺失或当前登录用户不可用 | 请补齐挂号要素 / 请重新登录 | N |
| `REG-PAT-VAL-001` | 新建患者分支缺少最小字段（`name/gender`） | 请补齐患者信息 | N |
| `REG-BIZ-409` | 同日同患者同挂号科室已存在占名额就诊（含待挂号 `PLANNED`、诊毕；退号 `CANCELLED` / 未收费取消 `ENTERED_IN_ERROR` 释放） | 本科室当日已有门诊挂号记录（含待支付/诊毕），须取消待挂号或退号后方可再挂 | N |
| `REG-BIZ-420` | 复诊0元号：同科室 72h 内无已接诊前次就诊（**BR-REG-020**） | 该患者在本科室近72小时内无已接诊记录，不能使用复诊0元号 | N |
| `REG-BIZ-404` | 合同/科室/号源不存在 | 主数据失效，请刷新 | N |
| `PRACTITIONER_ROLE_RULE_CONFLICT` | 指定医师未配置可接诊号源，或 `serviceTypeId` 不在其可接诊范围 | 请在用户管理维护医师可接诊号源 / 重新选择号源类型 | N |
| `REG-SYS-500` | 预结算执行异常 | 系统繁忙，请稍后重试 | Y |

**Mapper 接口方法签名**

- `toPreRegisterSettlementResponse(Long encounterId, Long paymentDraftId, BigDecimal totalAmount, List<ChargeItemPreviewDto> chargeItems): PreRegisterSettlementResponse`
- `toChargeItemPreviewDtoListBySaleLine(ChargeItemDefinition): List<ChargeItemPreviewDto>`
- `toRegisterPatientCommand(PreRegisterSettlementRequest): RegisterPatientCommand`
- `toUpdatePatientPhoneCommand(Long patientId, String phone): UpdatePatientPhoneCommand`
- `toUpdatePatientAddressCommand(Long patientId, String address): UpdatePatientAddressCommand`
- `toRegisterOutpatientEncounterCommand(PreRegisterSettlementRequest, Organization, HealthcareService, PatientCoreQueryModel, Long, String, LocalDateTime, String registrationPractitionerDisplayName): RegisterOutpatientEncounterCommand`
- `toCreateChargeItemCommand(PreRegisterSettlementRequest, ChargeItemDefinition, Long encounterId): CreateChargeItemCommand`
- `toCreatePaymentDraftFromPreSettleCommand(PreRegisterSettlementRequest, Long encounterId, Long chargeItemId, java.math.BigDecimal chargeItemAmount, List<PaymentDetailItemCommand> details): CreatePaymentDraftFromPreSettleCommand`（`chargeItemAmount` 须与已落库 `ChargeItem.amount` 一致）

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `contractId/organizationId/serviceTypeId` | 缺失直接失败；`practitionerId` 可空（**BR-REG-001**） |
| 2 | Pre | 患者分流（应用层编排） | `patientId/idCardNo/name/gender/phone/address` | 有 `patientId` 则直用；无则 `idCardNo` 有值时按 `PatientQueryService#findPatientByIdCard` 命中复用；未命中或未传 `idCardNo` 走步骤3 |
| 2a | Cmd | `PatientDomainService#updatePatientPhone(UpdatePatientPhoneCommand)`（条件） | `OutpatientRegistrationMapper#toUpdatePatientPhoneCommand(patientId, phone)` | **BR-REG-018**：仅步骤2复用既有患者且表单 `phone` 非空且与主档不一致时执行；写库点：Patient.phone |
| 2b | Cmd | `PatientDomainService#updatePatientAddress(UpdatePatientAddressCommand)`（条件） | `OutpatientRegistrationMapper#toUpdatePatientAddressCommand(patientId, address)` | **BR-REG-019**：仅步骤2复用既有患者且 `address` 非空且与主档不一致时执行；写库点：Patient.address（全文） |
| 3 | Cmd | `PatientDomainService#registerPatient(RegisterPatientCommand)` | `name/gender/idCardNo/phone/address/birthDate` + `CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId` | 仅当步骤2未命中患者时执行，建档命令的 `organizationId` 由会话机构解析，写库点：Patient（含可选地址全文） |
| 4 | Pre | `OutpatientSameDaySameDeptDuplicatePolicy` →（默认）`EncounterQueryService#checkOutpatientDuplicateRegistration` | 策略默认：`OutpatientDuplicateCheckQuery`：`patientId`、`organizationId`（挂号科室）、`registrationDate`（与本次 `occurredAt` 同日）；院点常量注入 `allowsSameDaySameDeptMultiple=true` 时整步跳过 | 默认口径见 **DM-ADM-002** §6.2.9 / **BR-ENC-011**；命中则失败；国基等放宽院点跳过 **REG-BIZ-409**（不配 yml） |
| 5 | Pre | `ContractQueryService#findContractById` | `contractId` | 不存在则失败 |
| 6 | Pre | `OrganizationQueryService#loadOrganizationByIdForDisplay` | `organizationId` | 不存在则失败 |
| 7 | Pre | `PractitionerQueryService#findById` | `practitionerId` | 若`practitionerId`为空则跳至步骤8；否则执行本步骤并**保留医师姓名为快照**供步骤10 Mapper 最后一参 |
| 7a | Gate | `PractitionerHealthcareServiceSupport#assertPractitionerAcceptsHealthcareService` | `practitionerId` + `serviceTypeId` | 指定医师时校验号源可接诊（**BR-REG-014**）；未配置或越界失败 |
| 8 | Pre | `HealthcareServiceQueryService#loadHealthcareServiceById` | `serviceTypeId` | 不存在则失败 |
| 8a | Gate | `OutpatientHistoricalFollowUpRegistrationGate#assertEligibleForHistoricalFollowUpRegistration` | `patientId` + `organizationId` + ⑧实体 + `occurredAt` | **BR-REG-020**；仅 `name=复诊0元号`；失败 **`REG-BIZ-420`** |
| 9 | Pre | `ChargeItemDefinitionQueryService#findSaleLineForHealthcareServiceRegistration` | 号源上下文 | 定价缺失则失败 |
| 10 | Asm+Cmd | `OutpatientRegistrationMapper#toRegisterOutpatientEncounterCommand` →（条件）置 `skipSameDaySameDeptDuplicateCheck` → `EncounterDomainService#registerOutpatientEncounter` | 请求+⑥组织+⑧号源+`PatientQueryService#loadPatientDetail`+`CurrentOperatorResolver#requireOperatorId`+`occurredAt`+**步骤7得到的 `registrationPractitionerDisplayName`（可 null）**；步骤4 放宽时命令头 `skipSameDaySameDeptDuplicateCheck=true` | 先装配命令（命令头 `operatorId`/`occurredAt`/[可选 skip] 与 Item 展示快照），再**第三写库点**：开立门诊就诊（**`status_enum=PLANNED`，待挂号**）；**`practitionerId` 非空时**新建活动参与者 **`role_code=REGISTRATION_DOCTOR`**（**禁止**开立阶段写 `ADMITTER`）；发布 `OutpatientRegistrationPlannedEvent`；`organizationId` 使用前端选择的挂号科室 |
| 11 | Cmd | `ChargeItemDomainService#createRegistrationChargeItem(CreateChargeItemCommand)` | `OutpatientRegistrationMapper#toCreateChargeItemCommand(request, saleLine, encounterId)`，其中 `encounterId` 必须来自步骤⑩ | **第四写库点**：创建挂号收费明细（`encounter_id` 为真实就诊主键，初始 `PLANNED`）；领域事件 `RegistrationChargeItemCreatedEvent` 须携带 `encounterId` 与 `totalPrice` 供QueryService累加 `account_amount` |
| 12 | Cmd | `ChargeItemDomainService#preSettleChargeItems(BatchChargeItemIdsCommand)` | ⑪产出的收费明细ID | **第五写库点**：预结算状态门禁校验与事件发布（明细状态保持 `PLANNED`） |
| 12.1 | Pre | `ChargeItemQueryService#loadChargeItemsByIds(ChargeItemQueryCommand)` | ⑪产出的收费明细主键 | 读取落库后的 `ChargeItem.amount`，作为支付草稿 `displayAmount`/`tenderedAmount` 与响应 `totalAmount` 的唯一金额口径（**禁止**再用 `ChargeItemDefinition.priceValue` 写支付头） |
| 13 | Cmd | `PaymentReconciliationDomainService#createPaymentDraftFromPreSettle(CreatePaymentDraftFromPreSettleCommand)` | ⑩结果 + ⑫结果 + **⑫.1 的 `ChargeItem.amount`** | **第六写库点**：支付草稿创建（`paymentEnum=PAY`、`statusEnum=DRAFT`，且 `encounterId` 必须来自步骤⑩；金额字段与已落库收费明细一致） |
| 14 | Asm | `OutpatientRegistrationMapper#toPreRegisterSettlementResponse` | **`encounterId` 取⑩**；`paymentDraftId` 取⑬；**`totalAmount` 取⑫.1**；`chargeItems` 预览仍可由⑨装配 | 仅 DTO 装配 |
| 15 | — | — | — | `ClinicRegister` 由领域事件载荷最终一致更新 |

### 3.8 IF-REG-07 `payRegisterFee`（支付挂号费正式结算）

**HTTP**：`POST /api/app/outpatientRegistration/payRegisterFee`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentDraftId` | Long | Y | 支付草稿ID |
| `paymentDetails` | `List<PaymentDetailDto>` | Y | 支付明细 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentId` | Long | 支付流水ID |
| `invoiceId` | Long | 发票ID |
| `status` | String | 结算状态 |

**DTO 字段表：PaymentDetailDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payChannelCode` | String | Y | 支付渠道编码：**现金 `CASH`**；扫码 settle 时 **`SCAN_PAY`**（服务端替换为 `WECHAT`/`ALIPAY`/`BANK_CARD`）；手工微信/支付宝 **`WECHAT`/`ALIPAY`** |
| `payAmount` | BigDecimal | Y | 支付金额 |
| `transactionNo` | String | N | 外部流水号 |
| `accountId` | Long | N | 账户主键（应用层在正式结算前回填） |
| `accountCode` | String | N | 账户编码（应用层在正式结算前回填） |

**关键校验**

- 草稿必须存在且处于可结算状态。
- **不得**在正式结算时再次调用 `ChargeItemDomainService#createRegistrationChargeItem`：须按草稿 `charge_item_ids` 加载预结算已创建的 `ChargeItem`，校验状态为 `PLANNED`、就诊/患者与草稿一致后再推进结算与 `settleChargeItems`。
  - **自费**：`displayAmount` 须与 `ChargeItem.totalPrice`（落库金额）一致。
  - **有 ACTIVE 2206**：`displayAmount` 为个人现金应付（`psnCashPay`），**允许**与 `ChargeItem.totalPrice`（原价）不一致；应付与渠道金额仍按草稿 `displayAmount` 校验（`resolveRegisterFeeExpectedPayable`）。
- 正式结算前必须能解析就诊默认 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`** 账户行；解析失败直接返回业务失败。

**失败码口径**

| 错误码 | 触发条件 | 前端提示语义 | 是否可重试 |
| --- | --- | --- | --- |
| `PAY-VAL-001` | 关键参数缺失 | 结算参数不完整 | N |
| `PAY-BIZ-404` | 草稿不存在或未找到可用结算账户 | 请重新预结算或检查账户配置 | N |
| `PAY-BIZ-409` | 草稿状态不可结算 | 当前状态不可重复结算 | N |
| `PAY-SYS-500` | 结算事务异常 | 结算失败，请刷新重试 | Y |

**Mapper 接口方法签名**

- `toPayRegisterFeeResponse(Payment, Invoice): PayRegisterFeeResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `paymentDraftId/paymentDetails` | 缺失直接失败 |
| 2 | Pre | `PaymentReconciliationQueryService#mustGetPayment` | `paymentDraftId` | 草稿不存在或状态非法则失败 |
| 3 | Cmd | `AccountDomainService#openEncounterAccounts(OpenEncounterAccountsCommand)` | `paymentDraft.encounterId` | 第一写库点：账户 |
| 4 | Pre | `AccountQueryService#getRegistrationAccount(patientId, encounterId)`（内部按 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode()`** 探测） | 草稿内 `patientId` + `encounterId` | 读取默认个人现金结算账户 |
| 5 | Asm | `request.paymentDetails[*].accountId/accountCode` 回填 | ④结果 | 不新增 mapper 额外入参，直接改 req DTO |
| 6 | Pre | `ChargeItemQueryService#loadChargeItemsByIds`（应用层私有门禁封装） | 草稿 `charge_item_ids`（挂号场景为单 ID）+ 草稿 `DRAFT` + 就诊/患者/金额与明细交叉校验 | **不新增写库点**：复用 IF-REG-06 已创建的 `ChargeItem`；非法则失败 |
| 7 | Cmd | `PaymentReconciliationDomainService#settleOutpatientPayment(SettleOutpatientPaymentCommand)` | 已回填账户字段的支付明细 | 第二写库点：支付结算（确保 `PaymentRecDetail.account_id` 非空） |
| 8 | Cmd | `ChargeItemDomainService#settleChargeItems(BatchChargeItemIdsCommand)` | ⑥解析出的收费明细主键 + `operatorId`（收费人）+ `billDate` | 第三写库点：明细 SETTLED 并写 `enterer_id`/`entered_date`（**BR-OPC-016**，与 **AS-OP-FIN-002** 同源） |
| 8.1 | Cmd | `EncounterDomainService#completeOutpatientRegistration(encounterId)` | `paymentDraft.encounterId` | 第四写库点（扩展）：缴费确认后将就诊 `status_enum` 从 `PLANNED` 迁移至 `REGISTERED`，QueryService 由 `OutpatientPaymentSettledEvent` QueryService 刷新 |
| 9 | Cmd | `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable` | `CreateDraftInvoiceCommand`（`amount=displayAmount`） | 第五写库点（**条件**）：实付 > 0 时发票草稿 + 博思异步入队；零元跳过，`invoiceId=null` |
| 10 | Cmd | `InvoiceDomainService#issueElectronicInvoice(IssueElectronicInvoiceCommand)` | 开票策略 | 博思 Worker 回写时执行；机构未启用 einvoice-boss 时跳过 |
| 11 | Asm | `OutpatientRegistrationMapper#toPayRegisterFeeResponse` | ⑦与⑨/⑩结果 | 仅 DTO 装配 |
| 12 | — | — | — | `ClinicRegister` 由领域事件载荷最终一致更新 |

#### 3.8.1 扩展：BPC 扫码支付（IF-REG-07 · 可选 Module）

> **真源**：`AS-OP-FIN-001` §9 · [模块/zxplat-module-unionpay-bpc/03 §3.10～§3.13](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

**Request 扩展字段**（`PayRegisterFeeRequest` 顶层，与 `paymentDetails` 并列）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `clientScanResult` | `ClientScanResultDto` | 含扫码行时 Y | Win consumer-scan 回传 + `merTradeNo` |

**`ClientScanResultDto`**：`merTradeNo`、`tradeNo`、`payType`（WEIX/ZFBA/UPAY）、`respCode`、`respMsg`、`txnAmtFen`（见 03 §3.10）。

**前端时序（通道 C，IF-REG-07 之前）**：`unionPayBpc#prepareConsumerScan` → Win `/api/unionpay/consumer-scan` → 失败则不调用 IF-REG-07。

在 §3.8 步骤 **6 与 7 之间**插入（`ThirdPartyPaymentExtension#isEnabled()` 且 `paymentDetails` 含 **`SCAN_PAY`** 行）：

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 6a | Pre | `ThirdPartyPaymentExtension#isEnabled()` | false → 跳过；UI 不展示「扫码支付」 |
| 6b | Gate | 顶层 `clientScanResult.respCode=00` | 非 00 → EX-REG-004 |
| 6c | Cmd | `ThirdPartyPaymentSettleSupport#applyScanBeforeSettle` | 内调 `Extension#applyClientScanResult`；`upc_bpc_order=SUCCESS` |
| 6d | Asm | 替换扫码行 `payChannelCode` | `BpcPayTypeMapper.toPayEnum(payType)` → WECHAT/ALIPAY/BANK_CARD |
| 6e | Gate | `PaymentChannelGate#requirePersistablePayChannelCode` | 各行（已替换后） |
| 6f | — | 6b/6c 失败 | **不**进入步骤 7；改 `CASH` 或重扫 |
| 6g | Pre | 步骤 7 失败且 6c 已成功 | 半成功 → 重传完整 `clientScanResult` 补偿（03 §3.12）；**禁止**重复 C |

**payType 双存**：BPC 原值在 `upc_bpc_order`；`fin_payment_rec_detail.pay_enum` 写语义码。

### 3.9 IF-REG-08 `pageClinicRegisters`（门诊挂号列表查询）

**HTTP**：`POST /api/app/outpatientRegistration/pageClinicRegisters`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | LocalDate | Y | 起始日期 |
| `dateTo` | LocalDate | Y | 结束日期 |
| `keyword` | String | N | 关键字：就诊业务号 `bus_no` **或** 患者姓名（含拼音/五笔，`existsPatientKeyword`）；与收费窗一致；**禁止**按诊断描述匹配；规约落点 `EncounterContextCriteriaSupport#clinicRegisterSpecification`；B.2.1 精确命中置顶见 `applyEncounterKeywordExactFirstOrder` |
| `pageNo` | Integer | Y | 页码 |
| `pageSize` | Integer | Y | 页大小 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Long | 总条数 |
| `records` | `List<ClinicRegisterItemDto>` | 门诊挂号列表 |

**DTO 字段表：ClinicRegisterItemDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊ID |
| `encounterBusNo` | String | Y | 就诊业务号 |
| `patientName` | String | Y | 患者姓名 |
| `patientGenderCode` | String | N | 患者性别编码（QueryService `patient_gender_code`，与行政性别术语码一致） |
| `patientGenderText` | String | N | 患者性别展示：`AdministrativeGenderDisplayUtil.toDisplayText`（脏码/空码不 NPE；未命中枚举回落 code）；优先 QueryService 文案，缺省时按 `patientGenderCode` 解析 |
| `contactPhone` | String | N | 患者联系电话（QueryService `contact_phone`） |
| `organizationId` | Long | N | 挂号科室主键（IF-REG-15 回显） |
| `organizationName` | String | Y | 挂号科室 |
| `doctorName` | String | N | 医生姓名（QueryService `doctor_name`） |
| `statusEnum` | String | N | 就诊状态枚举码（QueryService `status_enum`，与 `EncounterStatus.getCode()` 一致，如 `"PLANNED"`、`"REGISTERED"`、`"TRIAGED"`、`"CANCELLED"`) |
| `statusText` | String | Y | 状态文本；**`CANCELLED` 行由应用层覆写为「退号」**（常量 `OutpatientRegistrationConstants.CLINIC_REGISTER_STATUS_TEXT_REFUNDED`），其余取 QueryService `status_enum_text` |
| `cashierName` | String | N | 收费人姓名（已成功缴费时由支付流水 `enterer_id` 解析） |
| `contractName` | String | N | 费用性质名称：**挂号身份**——就诊优先账户合同名（有医保户优先医保）；`EncounterContractDisplayAssembler`；**不**跟挂号费账单挂账账户或支付渠道 |
| `startTime` | LocalDateTime | Y | 挂号开始时间（QueryService `start_time`） |
| `serviceTypeId` | Long | N | 号源类型ID |
| `serviceTypeName` | String | N | 号源类型名称 |
| `amount` | BigDecimal | N | **挂号费**：`adm_charge_item.total_price` 按锚点汇总（与预结算 `service_table`/`service_id` 一致）；**非**QueryService `account_amount`（后者为QueryService 侧多源金额摘要，见 `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` §3.4.1） |
| `paymentDraftId` | Long | N | **待挂号专用**：当 `statusEnum=PLANNED` 且存在 `DRAFT` 支付草稿时，为草稿主键，供前端直接调 IF-REG-07；已缴费或非草稿场景通常为 `null`（见 `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` §3.4.2） |
| `printCount` | Integer | N | 打印次数 |

**关键校验**

- 日期范围合法且分页参数合法。
- 读侧日期窗按 **挂号发生时刻** `coalesce(start_time, created_at)` 与 `[dateFrom 00:00, dateTo 23:59:59]` 比较（`EncounterQueryService`）；**禁止**误用行内 `end_time`（就诊结束，未完诊为 `NULL`）作区间上界，否则列表为空。
- **排除 `ENTERED_IN_ERROR`**（未收费窗口「取消」终态）；**保留 `CANCELLED`**（退号完成）并覆写展示文案为「退号」。

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | 日期范围+分页参数 | 范围非法直接失败 |
| 1.1 | Pre | `ClinicRegisterPageQuery#setExcludeStatusEnums` | `ENTERED_IN_ERROR` | 未收费取消行不进收费窗列表 |
| 2 | Pre | **`EncounterContextReader#pageClinicRegister`**（`ClinicRegisterPageQuery` → `Page<ClinicRegisterQueryModel>`） | 查询DTO | 仅读路径；**Q 契约**见 DM §6.2 §3.4 |
| 3 | Pre | `ChargeItemQueryService#listChargeItemsByEncounterIds` | 本页行 `encounter_id` 去重集合 | 一次 IN 查询；应用服务私有方法内按 `adm_healthcare_service` + `service_type_id` 锚点汇总 `total_price`，禁止按行逐条查库 |
| 3.1 | Pre | `PaymentReconciliationQueryService#findDraftPaymentIdsByEncounterIds` | 本页 `status_enum=PLANNED` 的 `encounter_id` 列表（可空则跳过） | 批量解析仍为 `DRAFT` 的支付草稿；同一就诊多草稿时取**最大 ID**（与领域查询服务实现一致）；供列表行 `paymentDraftId` |
| 3.2 | Pre | `PaymentReconciliationQueryService#mapEntererIdByPaymentIds` + `PractitionerQueryService#mapDisplayNameByEntererIds` | 本页成功支付 id | 装配 `cashierName` |
| 3.3 | Pre | `EncounterContractDisplayAssembler#mapContractDisplayByEncounterIds` | 本页 `encounter_id` | 装配 `contractName`（优先医保账户合同名 = 挂号身份） |
| 4 | Asm | `OutpatientRegistrationMapper#toClinicRegisterItemDtoList` → enrich（`CANCELLED`→`statusText=退号` + ③.3 费用性质）→ `toPageClinicRegistersResponse` | ②③③.1～3.3 结果 | DTO 装配：性别展示经 `AdministrativeGenderDisplayUtil`（空安全）；状态码与文案、其余快照字段；`amount` 取自③；`paymentDraftId` 取自③.1；退号文案覆写 |

### 3.10 IF-REG-09 `validateCancelable`（退号可否校验）

**HTTP**：`POST /api/app/outpatientRegistration/validateCancelable`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊ID |
| `reason` | String | N | 退号原因 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `cancelable` | Boolean | 是否可退 |
| `reasonCode` | String | 原因编码 |
| `reasonText` | String | 原因说明 |

**关键校验**

- 必须回写模型校验，不得仅凭QueryService判断。
- **`status_enum=PLANNED`（待挂号）**：`cancelable=false`，`reasonText` 明示不可退（与 `Encounter.assertCancelable` 门诊口径一致）。
- **`status_enum∈{IN_PROGRESS,ON_HOLD,DISCHARGED}`**：`cancelable=false`，`reasonCode=CANCEL-BIZ-416`，`reasonText=请让患者去医生处取消接诊后再退号`（**BR-REG-008**；与账单无关，须先 **IF-DOR-06**）。
- **可退状态**：仅 **`REGISTERED`/`TRIAGED`**。
- **BR-REG-015**：排除 `adm_healthcare_service` 挂号锚点后——存在 `PLANNED` 待收费 → 医生撤回签发；存在 `SETTLED` 已收费 → 门诊账单全退；存在 `REFUNDING` → 待退费收口。**不再**于退号侧校验药品/耗材发放态；退药/退耗由 **AS-OP-FIN-003 IF-REF-04** 在资金退费前收口（费用全退即药耗已完结）。**诊疗 Procedure 不在范围**。

**失败码口径**

| 错误码 | 触发条件 | 前端提示语义 | 是否可重试 |
| --- | --- | --- | --- |
| `CANCEL-VAL-001` | `encounterId` 缺失 | 请先选择挂号记录 | N |
| `CANCEL-BIZ-404` | 就诊不存在 | 挂号记录不存在或失效 | N |
| `ENCOUNTER_INVALID_STATUS` | 主键存在但 `classEnum` 非 `AMB`（如误传住院就诊 ID） | 当前就诊不是门诊类别，无法退号 | N |
| `CANCEL-BIZ-409` | 就诊状态不可退（含待挂号等其它不可退态） | 当前记录不可退号；待挂号时为「待挂号状态不可退号」 | N |
| `CANCEL-BIZ-416` | 已接诊/暂离/完诊未取消接诊 | 请让患者去医生处取消接诊后再退号 | N |
| `CANCEL-BIZ-410` | 存在非挂号锚点 `PLANNED` 待收费 | 存在待收费项目，请医生撤回已签发医嘱后再退号 | N |
| `CANCEL-BIZ-411` | 存在非挂号锚点 `SETTLED` 已收费 | 存在已收费账单，请先完成门诊退费后再退号 | N |
| `CANCEL-BIZ-412` | 存在非挂号锚点 `REFUNDING` | 存在退费处理中的账单，请待退费完成后再退号 | N |

**Mapper 接口方法签名**

- `toValidateCancelableResponse(Boolean, String, String): ValidateCancelableResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `encounterId` | 缺失直接失败 |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `encounterId`；`classEnum=AMB` | 不存在 → `ENCOUNTER_NOT_FOUND`；非 AMB → `ENCOUNTER_INVALID_STATUS` |
| 3 | Gate | `OutpatientRegistrationCancelGate#evaluateClinicalBillingBlock` | `encounterId` | **BR-REG-015**：仅非挂号锚点账单三态；命中则 `cancelable=false` |
| 4 | Pre | 就诊状态门禁 | `encounter.statusEnum` | 与 `Encounter.assertCancelable` 一致（仅 `REGISTERED`/`TRIAGED` 可退；已接诊→`CANCEL-BIZ-416`） |
| 5 | Asm | `OutpatientRegistrationMapper#toValidateCancelableResponse` | ②～④结果 | 仅读路径返回 |

### 3.11 IF-REG-10 `cancelWithRefund`（退号提交并联动退费）

**HTTP**：`POST /api/app/outpatientRegistration/cancelWithRefund`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊ID |
| `cancelReason` | String | Y | 取消原因 |
| `refundIntent` | String | N | 退费意图 |
| `refundRequestId` | String | Y | 幂等请求键 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterStatus` | String | 取消后就诊状态 |
| `refundStatus` | String | 退款处理状态 |
| `redInvoiceId` | Long | 红票ID |

**关键校验**

- 必须先执行可退校验链，未通过不得进入 Cmd。
- **原路返回（硬）**：逆向单须拷贝原支付 `contract_no` 与 `fin_payment_rec_detail` 全部分桶（含医保统筹/个账与自付渠道），口径对齐 **AS-OP-FIN-003 BR-REF-011**；非零元禁止落空明细逆向单。
- **零元号例外（BR-REG-012 / BR-REG-017）**：原支付 `displayAmount`/`tenderedAmount` 结算刻度均 ≤ 0 且无有效渠道明细时，允许空明细逆向单整单冲退（仍走 `requestOutpatientRefund` → `completeRegistrationCancelRefund` → 收费项回退）。

**失败码口径**

| 错误码 | 触发条件 | 前端提示语义 | 是否可重试 |
| --- | --- | --- | --- |
| `REFUND-VAL-001` | 必填参数缺失 | 退号参数不完整 | N |
| `REFUND-BIZ-403` | 可退校验不通过 | 与 IF-REG-09 返回的 `reasonText` 一致（如待挂号不可退号） | N |
| `REFUND-BIZ-409` | `refundRequestId` 重复提交 | 请求已受理，请勿重复操作 | N |
| `PAYMENT_RECONCILIATION_INVALID_ARGUMENT` | 非零元原支付无渠道明细 | 原挂号支付无渠道明细，无法退号退费 | N |
| `REFUND-SYS-500` | 退款链路异常 | 退款处理中或失败 | Y |

**Mapper 接口方法签名**

- `toCancelWithRefundResponse(Encounter, PaymentReconciliation, Invoice): CancelWithRefundResponse`
- `toRegistrationCancelRefundPaymentDetails(List<PaymentRecDetailQueryModel>): List<PaymentDetailItemCommand>`
- `toRequestOutpatientRefundCommand(CancelWithRefundRequest, PaymentReconciliationQueryModel, List<PaymentDetailItemCommand>): RequestOutpatientRefundCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `encounterId/cancelReason/refundRequestId` | 缺失直接失败 |
| 2 | Pre | 复用 IF-REG-09 序号 2~5 | `encounterId` | 若可退校验失败则终止并返回 `REFUND-BIZ-403` |
| 3 | Cmd | `EncounterDomainService#cancelOutpatientRegistration(CancelOutpatientRegistrationCommand)` | 就诊+原因+`cancelAt` | 第一写库点：就诊取消；发布后 `OutpatientRegistrationCancelledEvent` 须含 `statusEnum`/`cancelledAt`，供门诊信息/挂号列表/挂号单QueryService更新就诊状态与结束时刻 |
| 4 | Cmd | `PaymentReconciliationDomainService#requestOutpatientRefund(RequestOutpatientRefundCommand)` | 原单 `contractNo` + **原路渠道明细**（`listPaymentRecDetailsByPaymentId` → `toRegistrationCancelRefundPaymentDetails`） | 第二写库点：逆向单 `REFUND_PROCESSING`+`relation_id`+`paymentDetails`（含统筹/个账/自付）；原单保持 `SUCCESS`；**非零元**无渠道明细 → `PAYMENT_RECONCILIATION_INVALID_ARGUMENT`；**零元号**允许空明细 |
| 5 | Cmd | `PaymentReconciliationDomainService#completeRegistrationCancelRefund(CompleteRegistrationCancelRefundCommand)` | 原单+逆向单主键 | 第三写库点：逆向单 `REFUNDED`，**唯一** `OutpatientRefundCompletedEvent` |
| 6 | Cmd | `ChargeItemDomainService#returnRegistrationRefundChargeItems(ReturnRegistrationRefundChargeItemsCommand)` | 就诊上下文 | 第四写库点：收费项回退 |
| 7 | Cmd | `InvoiceDomainService#writeoffInvoiceAndCreateRedRecord(WriteoffInvoiceAndCreateRedRecordCommand)` | 发票上下文 | 若存在已开票记录则执行；否则跳至步骤8 |
| 8 | Asm | `OutpatientRegistrationMapper#toCancelWithRefundResponse` | ③~⑦结果 | 仅 DTO 装配 |
| 9 | — | — | — | `ClinicRegister`/`OutpatientEncounterContextQueryModel`/`RegistrationSlip`/`PatientSummary` 等由 `OutpatientRegistrationCancelledEvent` QueryService 最终一致更新 |
| — | Rollback | `abortOutpatientRefundAttempt(reversePaymentId)` | 步骤 4～6 任一步失败 |

> **booking 衔接（可选 Module）**：`OutpatientBookingRegistrationExtension#onRegistrationCancelled` 在就诊取消后同步调用（见 AppService 成码）。默认 `RELEASE_SLOT_ONLY` / `CANCEL_APPOINTMENT`：**回补 Slot + 软删预约行**，释放 `uk_wkf_appt_patient_service_day`，保证退号后同日同服务可再挂；`KEEP_SLOT` 仅解绑 `encounterId`。权威口径：[booking 概要 §8.3](../../../模块/zxplat-module-booking/01-概要设计.md)、[GAP-BKG-001](../系统完善/问题清单/01-并发双写与条件更新/GAP-BKG-001.md)。

#### 3.11.1 扩展：BPC 扫码原路退（IF-REG-10 · 可选 Module）

> **真源**：`AS-OP-FIN-001` §9 · [zxplat-module-unionpay-bpc/03 §3.11](../../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

**Request 扩展**：顶层 `clientRefundResult`（Win refund 回传；`refundAmountYuan` = 原 BPC `txnAmtFen`；见 03 §3.11）。

**前端**：`submitScanRefund`（金额取扫码段；半成功缓存校验；院内成功清缓存）→ 再调 IF-REG-10；口径同 **AS-OP-FIN-003-SDD §3.6.1**。

原支付经银联被扫（存在 `upc_bpc_order`）时，在步骤 **3 与 4 之间**（与步骤 4～6 **同一 `@Transactional`**）：

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 3a | Pre | `clientRefundResult.respCode=00` | 非 00 → 整体失败 |
| 3b | Cmd | `ThirdPartyPaymentRefundSupport#applyRefundBeforeLocal` | 内调 `Extension#applyClientRefundResult`（等额门禁 + 已 REFUNDED 幂等） |
| 3c | — | 3b 失败 | 步骤 4～6 **不执行** |

### 3.12 IF-REG-11 `printRegisterTicket`（补打挂号单）

**HTTP**：`POST /api/app/outpatientRegistration/printRegisterTicket`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | N | 就诊ID |
| `slipNo` | String | N | 挂号单号 |
| `printCopies` | Integer | N | 打印份数，默认1 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `slip` | `RegistrationSlipDto` | 挂号单详情 |
| `printCount` | Integer | 打印后次数 |

**DTO 字段表：RegistrationSlipDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `slipNo` | String | Y | 挂号单号 |
| `encounterId` | Long | Y | 就诊ID |
| `patientName` | String | Y | 患者姓名 |
| `organizationName` | String | Y | 科室名称 |
| `doctorName` | String | N | 医生姓名 |
| `serviceTypeName` | String | N | 挂号类型 |
| `statusText` | String | Y | 状态文本 |
| `amount` | BigDecimal | N | 金额 |
| `accountBalanceText` | String | N | 医保个账余额展示（**本笔扣款后**剩余；`InsuranceReceiptAccountBalanceSupport`：2207 `balc` 原样） |
| `printCount` | Integer | Y | 打印次数 |

**关键校验**

- `encounterId` 与 `slipNo` 必须二选一。

**Mapper 接口方法签名**

- `toPrintRegisterTicketResponse(RegistrationSlip): PrintRegisterTicketResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数解析门禁 | 解析 `encounterId/slipNo` 二选一 | 若两者都为空则失败；若两者都传，优先 `encounterId` |
| 2 | Pre | **`EncounterContextReader#getRegistrationSlipByEncounterId`** | `encounterId` | 若步骤1解析到`encounterId`则执行本步骤；否则跳至步骤3 |
| 3 | Pre | **`EncounterContextReader#pageRegistrationSlip`** | `slipNo` + `pageNo=1,pageSize=1` | 若〔步骤1仅解析到`slipNo`〕执行本步骤；否则跳至步骤4 |
| 4 | Asm | `OutpatientRegistrationMapper#toPrintRegisterTicketResponse` | ②或③结果 | 仅 DTO 装配 |
| 5 | — | — | — | 打印计数由 `RegistrationSlipPrintedEvent` 驱动QueryService最终一致更新（本 IF 无同步写库 Cmd） |

### 3.13 IF-REG-15 `changeRegistrationDepartment`（修改挂号科室）

**HTTP**：`POST /api/app/outpatientRegistration/changeRegistrationDepartment`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 门诊就诊 |
| `departmentId` | Long | Y | 目标门诊科室 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 院内科室已改 |
| `insurancePlatformChanged` | Boolean | 已撤销并按新科室重挂 2201 时为 true |

**关键校验（BR-REG-023）**

- 就诊须为 `AMB`，状态仅 `PLANNED`/`REGISTERED`/`TRIAGED`/`IN_PROGRESS`/`ON_HOLD`。
- 目标须在启用门诊科室列表中，且与当前 `organizationId` 不同。
- 同日同科防重沿用 **BR-REG-011**；院点放宽时命令 `skipSameDaySameDeptDuplicateCheck=true`。
- 已平台挂号且预结算/正式结算存在 → `REG-BIZ-409`，提示先取消或退号。
- 已平台挂号时目标科室 `yb_no` 必填；人员编号取平台映射；参保地取当日人员缓存，空则拒绝并提示重新读卡。

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 参数与门诊类别、状态 | 缺参 `REG-VAL-001`；状态不允许 `REG-BIZ-409` |
| 2 | Pre | 启用门诊科室、平台挂号快照 | 非门诊科室 `REG-BIZ-404`；已上传费用或已结算拒绝 |
| 3 | Cmd | `EncounterDomainService#correctOutpatientRegistrationDepartment` | 只写就诊 `organization_id` |
| 4 | Cmd | `ChargeItemDomainService#retargetRegistrationFeeDepartment` | 仅号源锚点挂号费行的开单/执行/成本科室 |
| 5 | Cmd | 已平台挂号时先 2202（11 与 12）再 2201（11） | 身份证凭证；参保地用当日人员缓存；失败回滚步骤 3～4 |

## 4. QueryService 与事件刷新

| QueryService | IF | 事件到QueryService更新动作（语义级） |
| --- | --- | --- |
| `ClinicRegister` | IF-REG-08 | `OutpatientRegistrationPlannedEvent` 首写；`OutpatientRegistrationCancelledEvent` 等就诊生命周期事件；**另**：`RegistrationChargeItemCreatedEvent`/`Refunded`、`RegistrationSlipPrintedEvent`、`AccountOpened`/`AccountBalanceEdited`、`OutpatientPaymentSettledEvent` 经独立监听更新金额/打印/账户摘要（详见QueryService §4.1 增补表，幂等与挂号单行分离） |
| `RegistrationSlip` | IF-REG-11 | `OutpatientRegistrationPlannedEvent` 建立主档；`RegistrationChargeItemCreatedEvent` 更新金额；`RegistrationSlipPrintedEvent` 累加打印次数（与列表 `print_count` 监听分离） |

## 5. 写库顺序（核心写接口视角）

仅汇总 Cmd 落库顺序；完整 Pre/Gate 见 §3。

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号（§3） | 写服务调用（顺序） | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-REG-06 | `preRegisterSettlement` | 2a→13 | `PatientDomainService#updatePatientPhone(条件)` → `PatientDomainService#registerPatient(条件)` → `OutpatientRegistrationMapper#toRegisterOutpatientEncounterCommand` → `EncounterDomainService#registerOutpatientEncounter` → `ChargeItemDomainService#createRegistrationChargeItem` → `ChargeItemDomainService#preSettleChargeItems` → `ChargeItemQueryService#loadChargeItemsByIds` → `OutpatientRegistrationMapper#toCreatePaymentDraftFromPreSettleCommand`（`chargeItemAmount`）→ `PaymentReconciliationDomainService#createPaymentDraftFromPreSettle` | Patient(条件回写/建档) → Encounter（命令含展示快照）→ ChargeItem（绑定真实 encounter）→ Payment（金额与 ChargeItem 对齐） |
| IF-REG-07 | `payRegisterFee` | 3→8 | `AccountDomainService#openEncounterAccounts` → `ChargeItemQueryService#loadChargeItemsByIds`（草稿关联 ID 门禁）→ `PaymentReconciliationDomainService#settleOutpatientPayment` → `ChargeItemDomainService#settleChargeItems` → `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable`（**BR-REG-017** / **BR-INV-008**）→ `InvoiceDomainService#issueElectronicInvoice`（博思回写，条件） | Account → Payment → ChargeItem（结算态）→ Invoice（条件） |
| IF-REG-10 | `cancelWithRefund` | 3→7 | `EncounterDomainService#cancelOutpatientRegistration` → `PaymentReconciliationDomainService#requestOutpatientRefund` → `PaymentReconciliationDomainService#completeRegistrationCancelRefund` → `ChargeItemDomainService#returnRegistrationRefundChargeItems` → `InvoiceDomainService#writeoffInvoiceAndCreateRedRecord(条件)` | Encounter → Payment → ChargeItem → Invoice |
| IF-REG-11 | `printRegisterTicket` | — | 无同步写库 Cmd（仅读查询 + QueryService 最终一致） | — |
| IF-REG-15 | `changeRegistrationDepartment` | 3→5 | `EncounterDomainService#correctOutpatientRegistrationDepartment` → `ChargeItemDomainService#retargetRegistrationFeeDepartment` →（已平台挂号）2202 再 2201 | Encounter → ChargeItem（挂号费科室）→ 医保挂号 |

### 5.2 事务边界与失败回滚

| IF | 事务边界 | 失败回滚 |
| --- | --- | --- |
| IF-REG-06 | 步骤3~13默认同一事务 | 任一步失败整体回滚 |
| IF-REG-07 | 步骤3~8同事务；步骤9~10可策略后置 | 主链路失败整体回滚；开票条件步骤失败不回滚已支付 |
| IF-REG-10 | 步骤3~8同事务 | 任一步失败整体回滚；重复幂等键返回已受理语义 |
| IF-REG-11 | 纯读无事务 | 读失败不写库 |
| IF-REG-15 | 步骤 3～5 同一事务 | 院内写入与医保重挂任一步失败则回滚就诊科室与挂号费归属 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 接口 | 幂等键 | 重复提交语义 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- | --- |
| IF-REG-06 | `patientId+organizationId+serviceTypeId(+bizDate)` | 返回同一预结算草稿语义 | `OutpatientRegistrationAppService.preRegisterSettlement`、`PaymentReconciliationDomainService#createPaymentDraftFromPreSettle` | §3.7 序号1、13 |
| IF-REG-07 | `paymentDraftId`（主）/`paymentRequestId`（外部） | 同草稿仅一次成功结算 | `OutpatientRegistrationAppService.payRegisterFee`、`PaymentReconciliationDomainService#settleOutpatientPayment` | §3.8 序号2、7 |
| IF-REG-10 | `encounterId+refundRequestId` | 同请求仅一次受理 | `OutpatientRegistrationAppService.cancelWithRefund`、`PaymentReconciliationDomainService#requestOutpatientRefund` | §3.11 序号1、5 |

### 6.2 并发控制

| 接口 | 并发场景 | 控制策略 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- | --- |
| IF-REG-06 | 同患者重复预结算 | Encounter 防重 + 草稿状态门控 | `EncounterQueryService#checkOutpatientDuplicateRegistration` | §3.7 序号4 |
| IF-REG-07 | 同草稿并发结算 | 支付状态机先到先得 | `PaymentReconciliationDomainService#settleOutpatientPayment` | §3.8 序号7 |
| IF-REG-10 | 退号与退费并发 | Encounter/Payment 双状态机门控 | `EncounterDomainService#cancelOutpatientRegistration`、`PaymentReconciliationDomainService#completeOutpatientRefund` | §3.11 序号3、6 |

### 6.3 重试与容错

| 接口 | 重试策略 | 容错策略 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- | --- |
| IF-REG-06 | 服务端不自动重试 | 返回草稿/状态供人工重提 | `OutpatientRegistrationAppService.preRegisterSettlement` | §3.7 序号10~14 |
| IF-REG-07 | 主链路不自动重试 | 支付失败回滚；开票失败进入补偿 | `OutpatientRegistrationAppService.payRegisterFee`、`InvoiceDomainService#issueElectronicInvoice` | §3.8 序号3~10 |
| IF-REG-10 | 不自动重试 | 退款失败可按同幂等键重入 | `OutpatientRegistrationAppService.cancelWithRefund` | §3.11 序号4~8 |

### 6.4 数据脱敏

| 接口 | 脱敏字段 | 脱敏规则 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- | --- |
| IF-REG-02 | 证件号、手机号 | 中间位掩码 | `OutpatientRegistrationMapper#toPatientItemDtoList` | §3.3 序号3 |
| IF-REG-08 | 列表 `contactPhone` 等敏感字段 | 与 IF-REG-02 同规则 | `OutpatientRegistrationMapper#toClinicRegisterItemDtoList` | §3.9 序号4 |

### 6.5 审计日志

| 接口 | 审计内容 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| IF-REG-06 | 操作人、患者、草稿状态前后值、时间 | `OutpatientRegistrationAppService.preRegisterSettlement` | §3.7 序号10~13 |
| IF-REG-07 | 操作人、草稿ID、支付ID、票据ID、状态前后值 | `OutpatientRegistrationAppService.payRegisterFee` | §3.8 序号3~10 |
| IF-REG-10 | 操作人、就诊ID、退款请求键、冲红票据ID、状态前后值 | `OutpatientRegistrationAppService.cancelWithRefund` | §3.11 序号3~8 |

## 7. 测试要点

### 7.1 功能测试

| 测试项 | 测试层级 | 自动化归属 | 优先级 | 场景 | 预期 |
| --- | --- | --- | --- | --- | --- |
| IF-REG-06 正常预结算 | 集成测试 | 后端 | P0 | 完整挂号要素提交 | 返回草稿与明细，创建就诊锚点 |
| IF-REG-07 正常结算（实付 > 0） | 集成测试 | 后端 | P0 | 草稿结算+开票策略 | 支付成功，收费项与票据状态收口 |
| IF-REG-07 零元挂号缴费 | 集成测试 | 后端 | P0 | **BR-REG-017** | 支付成功；`invoiceId` 为空；无博思开票任务 |
| IF-REG-10 正常退号退费 | 集成测试 | 后端 | P0 | 可退记录提交退号 | 就诊取消、退款完成、冲红按策略执行 |

### 7.2 非功能测试

| 测试项 | 测试层级 | 自动化归属 | 优先级 | 场景 | 验收标准 |
| --- | --- | --- | --- | --- | --- |
| 幂等性 | 集成测试 | 后端 | P0 | IF-REG-06/07/10 重复提交 | 仅一条有效业务结果 |
| 并发控制 | 性能测试 | 测试平台 | P0 | 同草稿并发结算/并发退号 | 无双写，无状态逆跳 |
| 容错补偿 | 集成测试 | 后端 | P1 | IF-REG-07 开票失败 | 已支付结果保留并进入补偿 |

### 7.3 QueryService一致性

| 测试项 | 测试层级 | 自动化归属 | 优先级 | 场景 | 验收标准 |
| --- | --- | --- | --- | --- | --- |
| `ClinicRegister` 一致性 | 集成测试 | 后端 | P0 | 结算/退号后查询 IF-REG-08 | 状态与金额最终一致 |
| `RegistrationSlip` 一致性 | 集成测试 | 后端 | P1 | 补打后再次查询 IF-REG-11 | `printCount` 递增且不丢增 |

### 7.4 集成测试

| 测试项 | 测试层级 | 自动化归属 | 优先级 | 范围 | 预期 |
| --- | --- | --- | --- | --- | --- |
| 挂号收费主链路 | 集成测试 | 后端 | P0 | IF-REG-06 → IF-REG-07 | Cmd 顺序与事务边界符合 §5 |
| 扫码支付挂号 | 集成测试 | 后端 | P0 | IF-REG-07 + Extension | SCAN_PAY + clientScanResult；语义码双存；BPC 失败不 settle |
| 退号退费主链路 | 集成测试 | 后端 | P0 | IF-REG-09 → IF-REG-10 | 可退校验与退款收口一致 |
| 退号 BPC 原路退 | 集成测试 | 后端 | P0 | IF-REG-10 + clientRefundResult | applyClientRefundResult 与本地退号 **同事务**；金额=原 `txnAmtFen` |
| 退号扫码半成功重试 | 集成/手工 | 前端+后端 | P0 | Win R 已成功、院内失败 | 带缓存凭证收口；不二次误退 |

### 7.5 测试缺口跟踪

| 测试项 | 测试层级 | 自动化归属 | 优先级 | 缺口 | 跟踪状态 |
| --- | --- | --- | --- | --- | --- |
| 并发压测脚本 | 性能测试 | 测试平台 | P1 | IF-REG-07/10 并发脚本待接入CI | Open |
| QueryService 刷新延迟观测 | 集成测试 | 后端 | P1 | QueryService最终一致延迟监控待补 | Open |

## 8. 变更历史

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v3.39 | 2026-10-13 | 新增 **IF-REG-15** `changeRegistrationDepartment`。列表行增 `organizationId`。对齐 **AS v2.45 / BR-REG-023**、**DM-ADM-002 v1.51**。 |
| v3.38 | 2026-10-12 | IF-REG-06：**BR-REG-011** 院点放宽（`OutpatientSameDaySameDeptDuplicatePolicy` + 院点常量注入；命令 `skipSameDaySameDeptDuplicateCheck`；不配 yml）。对齐 **AS v2.43**、国基 H22010202493。 |
| v3.37 | 2026-10-09 | IF-REG-01：响应增补 `serviceTypeFeeById`；**先**全量 `buildServiceTypeFeeById`（绑定 AD SALE）、**再**按医师收窄下拉（**BR-REG-022**）。对齐 **AS v2.42**。 |
| v3.36 | 2026-09-28 | IF-REG-11：`accountBalanceText` 纠偏为 2207 `balc` 原样（结算后，不再减 `acct_pay`）；与收费小票同 `InsuranceReceiptAccountBalanceSupport`；对齐 **AS-OP-FIN-002 v1.39** |
| v3.35 | 2026-09-22 | IF-REG-11：`RegistrationSlipDto.accountBalanceText` 明确为扣后个账余额（与收费小票同 `InsuranceReceiptAccountBalanceSupport`） |
| v3.34 | 2026-09-07 | **BR-REG-020**：号源更名「复诊0元号」；Gate 按 `name` 识别（种子默认 `bus_no=HS00000004` 不参与识别）；提示文案同步。对齐 **AS v2.40**。 |
| v3.33 | 2026-08-11 | IF-REG-10：补 booking `onRegistrationCancelled` 口径——默认软删预约行释放同日同服务防重键；对齐 **AS v2.37**、booking 概要 §8.3。 |
| v3.33 | 2026-08-22 | **BR-REG-020**：历史号复诊资格 Gate（`OutpatientHistoricalFollowUpRegistrationGate` + `EncounterQueryService#existsQualifyingHistoricalFollowUpPriorVisit`）；平台种子 `V2__adm_ddl`（`HS_HIST_FOLLOW_UP`）；失败码 `REG-BIZ-420`。对齐 **AS v2.38**。 |
| v3.32 | 2026-08-06 | **BR-REG-008**：IF-REG-09 可退状态收窄为 `REGISTERED`/`TRIAGED`；已接诊/暂离/完诊 → `CANCEL-BIZ-416`。对齐 **AS v2.36**、**DM-ADM-002 v1.41**、**AS-OP-DOR-001 IF-DOR-06**。 |
| v3.31 | 2026-08-03 | 退役 `registrationDoctorRequired`：IF-REG-01 不再下发；IF-REG-06 删除必选医生 Gate；**BR-REG-001** 固定医生可选。对齐 **AS v2.35**、**AS-OP-DOR-001 v1.17**。 |
| v3.30 | 2026-08-02 | IF-REG-06：**BR-REG-019** 地址全文：Request 增 `address`；新建 `registerPatient` 写入；复用步骤 2b `updatePatientAddress`；Mapper 增 `toUpdatePatientAddressCommand`。对齐 **AS v2.34**。 |
| v3.29 | 2026-08-01 | IF-REG-06 / 刷新预结算响应增补 **只读展示** `ybActPayDedc` / `ybCvlservFlag` / `ybCvlservPay`（2206 内存透传，不改 totalAmount）；§3.11.1：退号扫码退货金额=BPC `txnAmtFen`；半成功缓存/等额幂等对齐 **AS-OP-FIN-003-SDD v1.0.11**；对齐 **AS v2.32** |
| v3.28 | 2026-07-30 | **BR-REG-015** 收敛：IF-REG-09 门禁仅账单三态（`CANCEL-BIZ-410`～`412`）；撤销药耗发放门禁 `413`～`415` 与 §2.2 Medication/Device Dispense QS 依赖；药耗收口改由 **AS-OP-FIN-003 IF-REF-04**。对齐 **AS v2.31**。 |
| v3.27 | 2026-07-29 | IF-REG-06：**BR-REG-018** 复用既有患者回写联系电话（有 `patientId` 直用 / 无则身份证命中）；步骤 2a `updatePatientPhone`；Mapper 增 `toUpdatePatientPhoneCommand`。对齐 **AS v2.30**。 |
| v3.26 | 2026-07-29 | IF-REG-10：退号逆向单须原路拷贝原支付 `contract_no` + 渠道明细（对齐 **BR-REF-011**）；无明细硬拦；Mapper 增 `toRegistrationCancelRefundPaymentDetails`。对齐 **AS v2.29**。 |
| v3.25 | 2026-07-26 | IF-REG-08：`keyword` 对齐 `EncounterContextCriteriaSupport`（业务号/患者姓名含拼音五笔，**不含**诊断描述；B.2.1 精确置顶）；`patientGenderText` 经 `AdministrativeGenderDisplayUtil` 空安全。对齐 **AS v2.28**、**DM-ADM-002 v1.37**。 |
| v3.25 | 2026-07-22 | IF-REG-01 响应增 `registrationDoctorRequired`；IF-REG-06 Gate **BR-REG-001**（全局开关 `registrationDoctorRequired`，种子 `G`/默认 `0`）。对齐 **AS v2.28**、**AS-OP-DOR-001 v1.16**。 |
| v3.24 | 2026-07-16 | IF-REG-07：**BR-REG-017** 零元不开票；步骤 9 改 `InvoiceAfterPaymentOrchestration`（**BR-INV-008**）。IF-REG-08：排除 `ENTERED_IN_ERROR`；`CANCELLED` 展示「退号」；增 `cashierName`；**REG-BIZ-409** 文案同步。对齐 **AS v2.27**、**DM-ADM-002 v1.35**。 |
| v3.23 | 2026-07-14 | IF-REG-01/06：**BR-REG-014/016** 解绑排班——号源下拉仅医师可接诊收窄；占号仅显式 `slotId`。对齐 **AS v2.26**。 |
| v3.22.2 | 2026-07-13 | `CANCEL-BIZ-415` 提示改为先完成门诊退费；对齐 **AS v2.25.2**。 |
| v3.22.1 | 2026-07-13 | IF-REG-09：药品门禁拆分为药房在途 **`CANCEL-BIZ-415`** 与已发未全退 **`CANCEL-BIZ-413`**；对齐 **AS v2.25.1**。 |
| v3.22 | 2026-07-13 | IF-REG-09：新增 **BR-REG-015** 临床/收费全收口门禁；`OutpatientRegistrationCancelGate`；`CANCEL-BIZ-410`～`414`；§2.2 增补 Medication/Device Dispense QS；对齐 **AS v2.25**。 |
| v3.21 | 2026-06-25 | IF-REG-01 响应增 `paymentChannelOptions`；Pre 调 `PaymentChannelOptionProvider`（**ADR-PAY-001**） |
| v3.20 | 2026-06-23 | §3.8.1/§3.11.1：SCAN_PAY + clientScanResult/clientRefundResult；ThirdPartyPayment*Support；对齐 **AS v2.23** |
| v3.19 | 2026-06-23 | §3.8.1/§3.11.1：BPC「扫码支付」扩展（payType 双存、半成功补偿、退号 R 同事务）；对齐 **AS v2.22** |
| v3.19 | 2026-07-04 | IF-REG-06：**BR-REG-011** 占名额含 **`PLANNED` 待挂号**；`REG-BIZ-409` 提示更新；对齐 `EncounterStatus#outpatientDuplicateBlockingStatusCodes` 与 **DM-ADM-002 v1.27**。 |
| v3.18 | 2026-06-12 | IF-REG-06 步骤10：开立门诊指定医师时写活动 **`REGISTRATION_DOCTOR`** 参与者（对齐 **DM-ADM-002 v1.25** / **AS-OP-DOR-001** **BR-DOR-009**）；编排权威 **AS v2.19+**。 |
| v3.17 | 2026-05-28 | 六维对账：十方法与 §3.1 对齐；上游 **AS v2.19** |
| v3.8.1 | 2026-05-28 | IF-REG-08/11：读侧改 **`EncounterContextFacade`**（`pageClinicRegister` / 挂号条 **`ClinicRegistrationLoader`** 路径） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v3.14 | 2026-05-10 | IF-REG-06：重复挂号防重对齐 **BR-REG-011** / **DM-ADM-002 v1.7**（同日+患者+挂号科室 `organizationId`；占名额状态含诊毕；`PLANNED`/`CANCELLED` 语义）；§3.7 关键校验、`REG-BIZ-409`、编排序列步骤 4；编排权威 **v2.16+**。 |
| v3.13 | 2026-05-04 | IF-REG-07 §3.8：默认结算账户由 **`REG`** 更正为 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`**；编排序列步骤 4 与 `getRegistrationAccount` 实现一致；**§2.1/§2.2** 增补 `AccountQueryService`，**§2.3** 增补 `AccountTypeCode` 行；**v3.0** 变更历史脚注废止旧 `REG` 叙述；对齐 **AS-OP-FIN-001 v2.15**、**DM-ADM-009 v1.1**。 |
| v3.12 | 2026-05-02 | IF-REG-06 步骤10：`RegisterOutpatientEncounterItemCommand` 装配 **`pyStr`/`wbStr`** 并入 `OutpatientRegistrationPlannedEvent`，供 **EncounterQueryService 查询与医生站 **Q-OPI-001** 关键字（姓名/拼音/五笔/就诊业务号）。对齐 **AS v2.14**、**DM-ADM-002-SDD**（DM v1.4）、`DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` v2.9。 |
| v3.15 | 2026-05-18 | §2 应用层包路径收口为 `outpatient/financial/registration`；补充包名说明与 HTTP 暴露口径（类名路由不变）。 |
| v3.11 | 2026-05-02 | 对齐 **预结算 `PLANNED` / 结算后 `REGISTERED`**：IF-REG-06 响应增补 `encounterId`；步骤10 写明开立就诊初态 `PLANNED`；§2.2 增补 `EncounterDomainService#completeOutpatientRegistration`、`PaymentReconciliationQueryService#findDraftPaymentIdsByEncounterIds`；IF-REG-08 `ClinicRegisterItemDto` 增补 `paymentDraftId`，编排序列增补步骤 3.1 与 Mapper 第三参 `paymentDraftIdByEncounterId`；§2.3 就诊状态行补充 IF-REG-06/07 语义。对齐 `AS-OP-FIN-001` v2.13、QueryService `ClinicRegister` v2.7 §3.4.2。 |
| v3.10 | 2026-05-02 | IF-REG-02：`PatientItemDto` 由单一 `gender` 调整为 `genderCode`/`genderText`，并增补 `birthDate`、`age`（周岁）；`toPatientItemDtoList` 基于 `PatientPageItemQueryModel` 装配。§3.3 DTO 表与 Mapper 签名修正。对齐 `AS-OP-FIN-001` v2.12。 |
| v3.9 | 2026-05-02 | IF-REG-01：`LoadRegisterFormInitResponse` 增补 `genderOptions`；`OutpatientRegistrationAppService` 注入 `CodeSystemQueryService`，`getOptionListByEnum(AdministrativeGender.class)`；Mapper `toLoadRegisterFormInitResponse` 五参；§3.2 Response/编排序列/关键校验；§2.1/§2.2/§2.3 同步。对齐 `AS-OP-FIN-001` v2.11。 |
| v3.8 | 2026-05-02 | IF-REG-08：`ClinicRegisterItemDto` 增补 `patientGenderCode`、`patientGenderText`、`statusEnum`；Mapper 装配QueryService性别/状态码，性别展示缺省时按行政性别编码解析；§3.9 DTO 字段表与编排序列 4 同步。 |
| v3.7 | 2026-05-02 | IF-REG-08：`ClinicRegisterItemDto.amount` 改为写模型挂号费锚点汇总；编排序列增补 `listChargeItemsByEncounterIds` 批量 Pre；Mapper 签名增补 `Map` 入参；§2.2 白名单增补 `listChargeItemsByEncounterIds`。对齐QueryService `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` v2.6 §3.4.1。 |
| v3.6 | 2026-05-02 | 按《应用服务 SDD 文档生成提示词模板》增补 **§2.3 枚举、字典与值集（DM 与代码可追溯）**；原 §2.3 白名单双向复核顺延为 **§2.4**。 |
| v3.5 | 2026-05-02 | IF-REG-06：支付草稿 `displayAmount`/`tenderedAmount` 与响应 `totalAmount` 改为读取落库后 `ChargeItem.amount`；IF-REG-07：移除正式结算时重复 `createRegistrationChargeItem`，改为按草稿 `charge_item_ids` 加载既有明细并交叉校验后与 `settleOutpatientPayment`/`settleChargeItems` 衔接；§5.1/§3.7/§3.8 同步。 |
| v3.4 | 2026-05-02 | IF-REG-08 `ClinicRegisterItemDto` 仅保留与QueryService一致的字段名（`contactPhone`、`serviceTypeId`、`serviceTypeName`、`amount` 等），移除对外契约重复别名。 |
| v3.3 | 2026-05-02 | IF-REG-06 调整写库顺序为**先就诊后挂号收费明细**（步骤10~13），`toCreateChargeItemCommand` 增补 `encounterId` 入参；`RegistrationChargeItemCreatedEvent` 载荷与QueryService累加口径见 `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` v2.5。 |
| v3.2 | 2026-05-02 | `toRegisterOutpatientEncounterCommand` 增补入参 `registrationPractitionerDisplayName`；§3.7 步骤7/12 写明医师快照装配与 `OutpatientRegistrationPlannedEvent` 意向医师字段；§4 扩展 `ClinicRegister` 事件物化口径（金额/打印/账户/支付与 slip 幂等分离）。对齐 `AS-OP-FIN-001` v2.7、QueryService v2.4、领域事件 v1.1。 |
| v3.1 | 2026-05-02 | 对齐挂号列表与预结算实现：IF-REG-06 增补 `toRegisterOutpatientEncounterCommand` 多参装配及编排序列 12（Asm+Cmd）；`PatientQueryService#loadPatientDetail` 纳入 §2.2 白名单；IF-REG-08 明确日期窗 `coalesce(start_time,created_at)` 口径并修正 Mapper/Query 类型名为 `ClinicRegisterQueryModel`/`ClinicRegisterPageQuery`；§2.1 增补五类 Resolver。QueryService/EncounterQueryService 查询语义见 `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` v2.3、`DM-ADM-002 §6.2 EncounterQueryService`。 |
| v3.0 | 2026-05-01 | 对齐 IF-REG-07 账户回填实现：`PaymentDetailDto` 新增 `accountId/accountCode`，并在应用层解析默认结算账户后回填 `request.paymentDetails`；保持 mapper 结算方法仅接收 `PayRegisterFeeRequest`，禁止新增额外参数传递账户信息。（**v3.13** 起默认户类型码为 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`**，原 **`REG`**/`getAccountByEncounterFlag(...,"REG")` 叙述废止。） |
| v2.9 | 2026-05-01 | 对齐 IF-REG-07 最新实现：`payRegisterFee` 请求移除 `encounterId`，参数门禁收敛为 `paymentDraftId/paymentDetails`；账户开立所需就诊标识统一取支付草稿内 `encounterId`。 |
| v2.8 | 2026-05-01 | 对齐 IF-REG-06 最新实现时序：步骤12/13 改为先 `registerOutpatientEncounter` 后 `createPaymentDraftFromPreSettle`；同步修订 §5.1 写库顺序与 §6.1 幂等关联步骤，明确支付草稿必须绑定步骤12产生的 `encounterId`。 |
| v2.7 | 2026-05-01 | 对齐当前实现的 IF-REG-06 编排：新增 `createRegistrationChargeItem` 作为预结算前置，`preSettleChargeItems` 改为消费收费明细ID（`BatchChargeItemIdsCommand`），移除 `refreshRegistrationPaymentDraft` 分支；同步更新白名单、Mapper 签名、§5 写库顺序与 §6 幂等定位。 |
| v2.6 | 2026-05-01 | 对齐 `resolvePatientIdForPreRegister`：`idCardNo` 改为可选，仅在有值时执行同人识别；未传时直接进入最小字段建档分支；同步修订 IF-REG-06 字段、失败码与步骤2描述。 |
| v2.5 | 2026-05-01 | 明确机构来源双口径：IF-REG-06 新建患者分支建档机构取当前登录机构；就诊创建仍使用请求 `organizationId`（前端选择挂号科室）。 |
| v2.4 | 2026-05-01 | 按代码收敛首挂建档：移除独立 `IF-REG-03` 对外接口，改为并入 `IF-REG-06` 前置患者分流（patientId 复用 / idCardNo 命中复用 / 未命中新建）；同步更新请求字段、编排序列、事务/幂等/并发/审计步骤引用。 |
| v2.3 | 2026-05-01 | 按 `AppServiceControllerRegistrar` 真实暴露规则统一修正 §3.1 与各 IF 小节 HTTP：改为 `POST /api/app/outpatientRegistration/{methodName}`。 |
| v2.2 | 2026-05-01 | §2 包路径 `mapper/` 补列 `OutpatientRegistrationMapperImpl.java`，与《应用服务 SDD 文档生成提示词模板》MapStruct 接口+Impl 同列约定及工程一致 |
| v2.1 | 2026-05-01 | 按更新后的 SDD skill 补齐细则：新增 §2.3 白名单双向一致性复核；§3 全 IF 增加显式 HTTP；修正 IF-REG-06 互斥步骤对（refresh/create 草稿）为条件分流并标注跳转；补强 IF-REG-11 互斥查询步骤条件，移除无来源同步 Cmd，改为QueryService 最终一致口径。 |
| v2.0 | 2026-05-01 | 按应用服务 SDD skill 全量重刷：统一 1~8 章节骨架；补齐 §3.1 中文方法名、3.1.1 编排约定、每个 IF 的 Mapper 签名与 Cmd(Command) 记法；补齐 DTO 字段表、分支条件显式化、§5 Cmd 对照、§6/§7 承诺表字段。 |


