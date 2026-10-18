# 门诊医生站 · 住院申请 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-005-SDD  
> **版本**：v1.12 | **日期**：2026-10-14 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医生站 · 住院申请（**`outpatient/doctorstation/inpatientadmission`**，独立 AppService，与就诊管理 `encountermanage` 分离） |
| **边界** | **INSERT** 新住院 Encounter（`IMP+PLANNED`）；`ambEncounterId` 仅关联源门诊就诊（**不改**源就诊）；住院专用 `bus_no` 发号；为住院线新建参与者与诊断子表；不在本服务编排住院登记、预交金、医生撤销 |
| **编排权威** | `AS-OP-DOR-005-门诊医生站住院申请.md`（**v1.9**）§2、§4；领域契约见 `DM-ADM-002`（**§2.3.1**）、`DM-CLN-003` |
| **方法权威** | `DM-ADM-002-Encounter-就诊.md` §6.1.14；`DM-CLN-003-Condition-诊断记录.md` §6.1.1；`DM-ADM-003-Location-位置.md`；`DM-ADM-004-Organization-机构科室.md` |
| **严格对齐** | IF 编号 **IF-IP-REQ-01～02** 与 AS 一致 |
| **实现阶段口径** | **已落地**（`DoctorStationInpatientAdmissionAppService`）；HTTP **`doctorStationInpatientAdmission`**；`AS-OP-DOR-001-SDD` §3.6 **IF-DOR-05** 仅作历史对照 |

**Out of scope（重复 AS §1.4，SDD 不得扩写）**

| 类别 | 内容 |
| --- | --- |
| 住院处登记、预交金、账户 | **AS-IP-FIN-001** |
| 医生撤销申请 | **AS-IP-FIN-001** 取消登记 |
| 申请后修改申请内容 | 本期不支持 |
| 医保交互 | 表单可保留医保类别字段，本期不编排 |
| `PRE_ADMISSION` 状态迁移 | 不调用 `startPreAdmission` |

**下游消费**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-FIN-001-住院登记.md` | 消费 `InpatientAdmissionRequestedEvent` → 待登记（`PLANNED`）；登记后 `REGISTERED`，分床后 `IN_PROGRESS`；登记**只读**本住院线 `diagnosisJson`，**不改**申请已落诊断（**BR-IP-REG-007**） |
| `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` | QueryService upsert/UPSERT 待登记行 |

**与 AS-OP-DOR-001 关系**

| 项 | 说明 |
| --- | --- |
| 历史 IF | `AS-OP-DOR-001-SDD` §3.6 **IF-DOR-05** 曾挂在 `encountermanage`；**v1.4 起** 住院申请迁至本 AS 独立包 |
| HTTP | **`POST /api/app/doctorStationInpatientAdmission/{methodName}`**（与 `refundapplication` 等同构，**不**再使用 `doctorStationEncounterManage`） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 枚举持久化 **String code**（`EncounterStatus.getCode()` 等） |
| **状态口径** | 申请成功 **`IMP + PLANNED`**（**仅**本服务产生）；登记由 **AS-IP-FIN-001** 推进至 **`REGISTERED`**；不使用 `PRE_ADMISSION` |
| **QueryService口径** | IF-IP-REQ-02 优先 `EncounterQueryService` 按 `amb_encounter_id` 查活跃住院线；可选辅助 `EncounterQueryService`（非 BR 终局依据） |
| **事务口径** | IF-IP-REQ-01：Gate/Pre 失败不进 Cmd；诊断/参与者/位置与 Encounter 创建 **同一事务**（目标态）；应用层不声明 `@Transactional`（与《代码生成通用规范》一致） |
| **HTTP 接入口径** | `AppServiceControllerRegistrar` → **一律 `POST`**：`POST /api/app/doctorStationInpatientAdmission/{methodName}` |
| **诊断口径** | UI 可预填门诊主诊；Request 携带**入院诊断明细**（与门诊是否相同无关）；后台**一律**为**新住院 encounter_id** 落诊断关联（`EncounterInpatientAdmissionOrchestrator#saveAdmissionDiagnoses`，**申请阶段不展开国临**）；`diagTypeCode=31`（**BR-ENC-021**）；**无**「未另选则后端默认引门诊」分支；下游登记**只读展示医保名、不重写**（**AS-IP-FIN-001** **BR-IP-REG-007**）；国临一对多展开见 **AS-IP-DOR-002** IF-IP-DOR-02 |
| **bus_no 口径** | 住院号发号前缀 **≠** 门诊号；使用 **`AggregateTypeConstants.INPATIENT_ENCOUNTER`（`ZY`）** 与门诊 `ENCOUNTER` 分离（**已落地**） |

#### 1.2.1 文档语义码与 `ErrorCodeConstants.ErrorCode` 对照

| 文档语义码（AS §5） | 实现 `ErrorCode` | 场景 |
| --- | --- | --- |
| EX-IP-REQ-101 | `ENCOUNTER_INVALID_ARGUMENT` 或基础设施异常 | IF-IP-REQ-02 查询失败 |
| EX-IP-REQ-201 | `ENCOUNTER_RULE_CONFLICT` | 同一门诊就诊重复申请（BR-IP-REQ-002） |
| EX-IP-REQ-202 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_STATUS` / `ENCOUNTER_RULE_CONFLICT` | 源就诊不存在、非 AMB、已终态、患者不一致 |
| EX-IP-REQ-203 | 领域/基础设施异常 | 申请提交持久化失败 |
| EX-IP-REQ-301 | `ENCOUNTER_INVALID_ARGUMENT` | 未选就诊（Gate） |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/doctorstation/inpatientadmission/
  service/
    DoctorStationInpatientAdmissionAppService.java    # IF-IP-REQ-01/02
  request/
    RequestInpatientAdmissionRequest.java
    CheckInpatientAdmissionExistsRequest.java
  response/
    RequestInpatientAdmissionResponse.java
    CheckInpatientAdmissionExistsResponse.java
  dto/
    AdmissionDiagnosisSubmitDto.java
  mapper/
    DoctorStationInpatientAdmissionMapper.java
    DoctorStationInpatientAdmissionMapperImpl.java
```

> 包名：`com.zxplat.domain.application.outpatient.doctorstation.inpatientadmission`  
> AppService Bean 名 → HTTP 前缀：`doctorStationInpatientAdmission`（对齐 `doctorStationRefundApplication` 分包惯例）

**命名约定（Request / Response）**

| 原则 | 说明 |
| --- | --- |
| 包即域 | 独立 **`inpatientadmission`** 包承载住院申请，与 **`encountermanage`**（接诊/暂离/完诊）解耦 |
| 与 App 方法对齐 | `requestInpatientAdmission` → `RequestInpatientAdmissionRequest`；`checkInpatientAdmissionExistsByAmbEncounter` → `CheckInpatientAdmissionExistsRequest`（方法名可保留完整语义） |
| 可读优先 | 保留 `Inpatient`、`Admission` 等业务词；入院诊断 DTO 用 `AdmissionDiagnosisSubmitDto` |

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `EncounterQueryService` | `mustGetEncounter`、`assertAmbForInpatientReq`、`findOpenInpatientLine`（IF-IP-REQ-01/02） |
| `EncounterDomainService` | `requestInpatientAdmission`（含参与者/病区/诊断，域内编排） |
| `OrganizationQueryService` | 意向住院科室校验（IF-IP-REQ-01） |
| `LocationQueryService` | 可选 `wardId` 病区形态校验（IF-IP-REQ-01） |
| `DoctorStationInpatientAdmissionMapper` | Request/Command/Response 装配 |
| `CurrentPractitionerResolver` | 可选：审计日志 `sessionPractitionerId`（**业务** `operatorId` / `requestDoctorId` 来自 Request，非会话解析） |

### 2.3 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterQueryService` | `mustGetEncounter` | IF-IP-REQ-01 Pre |
| `EncounterQueryService` | `assertAmbForInpatientReq` | IF-IP-REQ-01 Pre：AMB 且可转住院 |
| `EncounterQueryService` | `findOpenInpatientLine` / `existsOpenInpatientLine` | IF-IP-REQ-01/02 Pre：BR-IP-REQ-002 |
| `EncounterDomainService` | `requestInpatientAdmission` | IF-IP-REQ-01 Cmd（**内含**参与者、病区、入院诊断与按需 `Condition` 新建；应用层不另调 `ConditionDomainService`） |
| `EncounterDomainService` | `upsertEncounterDiagnosisAssociation` | （域内由 `requestInpatientAdmission` 调用，非 App 直调） |
| `ConditionDomainService` | `saveDoctorDiagnosis`（`admissionDiagnosisFlow=true`） | （域内由 `requestInpatientAdmission` 调用） |
| `BusinessSequenceService` | `getSeqByDay(INPATIENT_ENCOUNTER)` | IF-IP-REQ-01：住院 busNo 前缀 `ZY` |
| `OrganizationQueryService` | `loadOrganizationByIdForDisplay` 或等价校验 | IF-IP-REQ-01 Pre |
| `LocationQueryService` | `loadLocationById` / 病区形态校验 | IF-IP-REQ-01 Pre（可选病区） |
### 2.4 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3.1 `class_enum` | 源就诊 **AMB**；新建住院 **IMP** |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3 | 住院申请初始 **`PLANNED`**；源就诊须非 `CANCELLED`/`DISCHARGED` 等终态 |
| 入院来源 | `RequestInpatientAdmissionRequest.admitSourceCode` | `adm_encounter.admit_source_code` | 门诊转住院场景带出 |
| 入院途径 | `inWayCode` | `adm_encounter.in_way_code` | BR-IP-REQ-006；与医保 **adm_way** 同口径 **1/2/3/9**；门诊转住院默认 **2** |
| 病情优先级 | `priorityCode` | `adm_encounter.priority_enum` | AS §4 表单要素 |
| 住院 bus_no 前缀 | `AggregateTypeConstants.INPATIENT_ENCOUNTER`（**目标态**，如 `ZY`） | 与 `ENCOUNTER`（门诊 `EN`）分离 | `AggregateTypeConstants.INPATIENT_ENCOUNTER`（已落地） |
| 入院诊断 | `admissionDiagnoses`（列表） | 表单提交；UI 可预填 | 后台按列表为住院线落库 |
| 参与者角色 | `EncounterConstants.ROLE_ADMITTER` | `DM-ADM-002` 参与者 | 申请医生 |
| 位置形态 | `com.zxplat.domain.share.enums.LocationForm` | `DM-ADM-003` | 意向病区 **WARD**（可选，非分床 BED） |

### 2.5 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向（白名单 → §3） | PASS（含目标态标注） | 目标态方法在 §3 序列中标注「待生成」 |
| 反向（§3 → 白名单） | PASS | 各 IF 调用可定位 §2.3 |
| 互斥步骤 | PASS | 无「默认/另选」双路径；仅「表单是否含新病情」决定是否 `saveDoctorDiagnosis` |

### 2.6 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-OP-DOR-005` §3**（当前**无待跟踪缺口**）；本 SDD **禁止**维护 Gap 表。`requestInpatientAdmission`、`findOpenInpatientLine` / `existsOpenInpatientLine`、`saveAdmissionDx` 等 **`DM-ADM-002` §6** 能力已落地；应用层 `DoctorStationInpatientAdmissionAppService` 为本 SDD 成码目标态。

---

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列」，按序号执行；**Pre / Gate 任一失败不得进入 Cmd**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-REQ-01 | `requestInpatientAdmission` | 发起住院申请 | `POST /api/app/doctorStationInpatientAdmission/requestInpatientAdmission` |
| IF-IP-REQ-02 | `checkInpatientAdmissionExistsByAmbEncounter` | 查询是否已存在住院申请 | `POST /api/app/doctorStationInpatientAdmission/checkInpatientAdmissionExistsByAmbEncounter` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 参数门禁 | 不进入 Pre |
| **Pre** | 写前只读：源就诊、防重、科室/病区 | 抛业务异常，**禁止 Cmd**；**不**在 Pre 解析/默认门诊主诊 |
| **Cmd** | 领域写 | 事务回滚见 §5 |
| **Asm** | Mapper 装配 Response | — |

---

### 3.2 IF-IP-REQ-01 `requestInpatientAdmission`（发起住院申请）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationInpatientAdmissionAppService#requestInpatientAdmission` |
| **HTTP** | `POST /api/app/doctorStationInpatientAdmission/requestInpatientAdmission` |

**Request `RequestInpatientAdmissionRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ambEncounterId` | Long | Y | 当前门诊就诊锚点 |
| `patientId` | Long | N | 可选；传入须与源就诊头表一致 |
| `targetDepartmentId` | Long | Y | 意向住院科室 |
| `wardId` | Long | N | 意向病区位置主键（`LocationForm.WARD`；落库/事件字段同为 `wardId`） |
| `requestDoctorId` | Long | Y | 申请医师 |
| `admitSourceCode` | String | N | 入院来源 |
| `inWayCode` | String | N | 入院途径（adm_way 同口径 1/2/3/9；门诊转住院默认 **2**） |
| `priorityCode` | String | N | 病情优先级 |
| `admissionDiagnoses` | `List<AdmissionDiagnosisSubmitDto>` | Y | 入院诊断明细（UI 可预填门诊主诊，后台按列表落库） |
| `operatorId` | Long | Y | 操作人 |
| `chargeIntent` | String | N | 费用性质意向：`INSURANCE`=医保 / `SELF_PAY`=自费；写入住院线 `charge_intent`；供登记窗口预填费用性质（医保默认市医保；**BR-IP-REQ-012** / **AS-IP-FIN-001 BR-IP-REG-024**） |
| `advanceDepositAmount` | BigDecimal | N | 意向预交金总额；**>0** 落 `HOSPITAL_DEPOSIT` DRAFT（无明细）；医生不填支付渠道（**BR-IP-REQ-010/011**） |

**DTO 字段表：AdmissionDiagnosisSubmitDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `conditionId` | Long | N | 引用已有病情事实（如门诊同 ICD，仍须为住院线**新建关联**） |
| `conditionDefinitionId` | Long | C | 新开诊断时必填 |
| `primaryFlag` | Boolean | Y | 是否主诊断 |
| `description` | String | N | 诊断说明；**仅**用户填写时提交 |
| `tcmFlag` | Boolean | N | 是否中医；中医一病一症时两条均为 `true` |
| `tcmSyndromeGroup` | String | N | 中医病证组号（同组病+证共用；可空由编排层分配） |

**Response `RequestInpatientAdmissionResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `inpatientEncounterId` | Long | 新建住院就诊标识 |
| `inpatientEncounterBusNo` | String | 住院业务号（目标态 Response 字段） |
| `success` | Boolean | 是否成功 |

**关键校验（BR）**

- **BR-IP-REQ-001**：源就诊 AMB 且可进行转住院（非终态）。
- **BR-IP-REQ-002**：同一 `ambEncounterId` 无另一条未取消住院线（`PLANNED` 或后续非 `CANCELLED`）。
- **BR-IP-REQ-003**：**INSERT** 新住院 Encounter **`IMP + PLANNED`**；写 `amb_encounter_id`；**不修改**源门诊就诊。
- **BR-IP-REQ-009**：住院 `bus_no` 使用**独立发号前缀**（≠ 门诊 `EN` 前缀）。
- **BR-IP-REQ-004～006**：参与者、诊断、入院方式/类型。
- **BR-IP-REQ-007**：提交后不可修改（无更新 IF）。
- **BR-IP-REQ-008**：不调用 `startPreAdmission`。
- **BR-IP-REQ-010/011**：可选意向预交金总额 → 仅 `collectAdvanceDeposit` 落 DRAFT（无明细、不开户、不 settle）。
- **BR-IP-REQ-012**：可选费用性质意向 `chargeIntent`（医保/自费）→ 写入住院线 `charge_intent`；非法 code 拒绝；不建 Account、不触发医保入院；登记窗口未开户时据此预填费用性质（医保默认市医保）。

**领域编排序列（严格顺序，目标态）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 入参校验 | `ambEncounterId`、`targetDepartmentId`、`requestDoctorId`、`operatorId` | EX-IP-REQ-301 |
| 2 | Pre | `mustGetEncounter` + `assertAmbForInpatientReq` | `ambEncounterId` | AMB + 可转住院 |
| 3 | Pre | `findOpenInpatientLine` / `existsOpenInpatientLine` | `ambEncounterId` | 已存在 → `ENCOUNTER_RULE_CONFLICT` 或幂等（App 编排） |
| 4 | Pre | `OrganizationQueryService` / `LocationQueryService`（可选） | 科室、病区 WARD | — |
| 5 | Cmd | `EncounterDomainService#requestInpatientAdmission` | 命令含 `ambEncounterId`、`wardId`、`admissionDiagnoses` 等 | **INSERT** IMP+PLANNED；域内写参与者/病区 WARD/诊断；事件 **`InpatientAdmissionRequestedEvent.wardId`** |
| 6 | Cmd | `PaymentReconciliationDomainService#collectAdvanceDeposit` | **仅当** `advanceDepositAmount>0`；空明细；`paymentEnum=PAY` | 意向预交金 DRAFT；**不**开户、**不** settle |
| 7 | Asm | `DoctorStationInpatientAdmissionMapper#toRequestInpatientAdmissionResponse` | 含 `inpatientEncounterBusNo` | — |

**Mapper**

- `toRequestInpatientAdmissionCommand(RequestInpatientAdmissionRequest, Long resolvedPatientId): RequestInpatientAdmissionCommand`
- `toRequestInpatientAdmissionResponse(Long encounterId, String busNo, boolean success): RequestInpatientAdmissionResponse`

---

### 3.3 IF-IP-REQ-02 `checkInpatientAdmissionExistsByAmbEncounter`（是否已存在住院申请）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationInpatientAdmissionAppService#checkInpatientAdmissionExistsByAmbEncounter` |
| **HTTP** | `POST /api/app/doctorStationInpatientAdmission/checkInpatientAdmissionExistsByAmbEncounter` |

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ambEncounterId` | Long | Y | 门诊就诊标识 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `exists` | Boolean | 是否已有关联未取消住院线 |
| `inpatientEncounterId` | Long | 存在时返回住院就诊 ID |
| `statusEnum` | String | 住院线状态 code（申请后 **`PLANNED`**；登记后 **`REGISTERED`**，见 DM §2.3.1） |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `ambEncounterId` 非空 | EX-IP-REQ-301 |
| 2 | Pre | `EncounterQueryService#findOpenInpatientLine`（或 `existsOpenInpatientLine`） | **只读**；返回 `OpenInpatientLineQuery` |
| 3 | Asm | Mapper 装配 `CheckInpatientAdmissionExistsResponse` | 无 Cmd |

---

## 4. QueryService 与事件刷新

| QueryService | 用途 | 投影触发点 |
| --- | --- | --- |
| `InpatientEncounterContextQueryModel` | 住院处待登记列表（下游 **AS-IP-FIN-001**） | `InpatientAdmissionRequestedEvent` → INSERT/UPSERT，**SET** `ward_id`←`event.wardId`（§4.2 唯一真源） |
| `OutpatientEncounterContextQueryModel` | 门诊侧「已申请」展示（目标态，以实现为准） | 可选：申请后 UPDATE 标记列，或 UI 调 IF-IP-REQ-02 |

字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §4.2 为唯一真源；本 SDD 不重复字段表。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号（§3.2） | 领域调用 | 聚合写入顺序（语义级） |
| --- | --- | --- | --- | --- |
| IF-IP-REQ-01 | `requestInpatientAdmission` | 5（Cmd） | `requestInpatientAdmission` | Encounter（住院新建）+ 参与者 + 病区 + 诊断（域内编排） |

### 5.2 事务边界与失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-IP-REQ-01 | 单事务覆盖 Cmd-1～4（目标态） | 任一步失败则 Encounter/诊断/参与者/位置均不回滚部分成功 |
| IF-IP-REQ-02 | 无事务 | 只读 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 同一 `ambEncounterId` 重复提交返回已存在语义（EX-IP-REQ-201） | App Pre-③ + 领域唯一约束 | §3.2 序号 3、7 |
| 短时双提交仅一条成功 | DB 唯一索引（目标态：`amb_encounter_id` + 活跃态）可选 | §3.2 序号 7 |

### 6.2 并发控制

| 承诺 | 代码位置 | 说明 |
| --- | --- | --- |
| 双窗口同门诊就诊同时申请 | Pre-③ + 事务隔离 | 后提交者 BR-IP-REQ-002 失败 |

### 6.3 重试与容错

| 承诺 | 说明 |
| --- | --- |
| QueryService最终一致 | 提交响应不等待 `InpatientEncounterContextQueryModel` 投影 |

### 6.4 审计

| 承诺 | 说明 |
| --- | --- |
| `operatorId`、`requestDoctorId`、源 `ambEncounterId` | 写入命令与日志（过渡期已有 `log.info`） |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 门诊进行中就诊申请 | `IMP+PLANNED`；住院处待登记可见 | P0 |
| 重复申请同一门诊就诊 | EX-IP-REQ-201 | P0 |
| UI 预填门诊主诊且医生未改提交 | 住院线**新建** `EncounterDiagnosis` 关联（可复用同一 `conditionId`） | P0 |
| 表单新开入院诊断 | `AdmissionDiagnosisCreatedEvent` + 住院线关联 | P1 |
| 住院 bus_no | 前缀与门诊号不同（如 `ZY` vs `EN`） | P0 |
| IF-IP-REQ-02 已申请 | `exists=true`，按钮禁用 | P0 |
| 源就诊已完诊/取消 | EX-IP-REQ-202 | P0 |

### 7.2 非功能测试

| 用例 | 指标 | 优先级 |
| --- | --- | --- |
| 申请提交 | ≤2000ms（AS §7） | P1 |

### 7.3 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 本服务申请 → **AS-IP-FIN-001** 登记 | `PLANNED→REGISTERED`（分床后 `IN_PROGRESS`）；登记**不传** `admissionDiagnoses`；诊断与申请一致 | P0 |
| 住院处取消 → 医生站再申请 | `ambEncounterId` 可再次申请 | P0 |

### 7.4 测试缺口跟踪

| 缺口 | 说明 | 状态 |
| --- | --- | --- |
| TEST-GAP-REQ-001 | — | **已关闭** | IF-IP-REQ-01/02 App 编排已落盘，可开展 E2E |
| TEST-GAP-REQ-002 | — | **已关闭** | `doctorStationInpatientAdmission` 下 IF-IP-REQ-01/02 均已注册 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.12 | 2026-10-14 | **BR-IP-REQ-012**：`chargeIntent` 供登记预填费用性质（医保默认市医保）；对齐 **AS v1.12** / **AS-IP-FIN-001 v1.24** |
| v1.11 | 2026-10-14 | Request 增 `chargeIntent`；**BR-IP-REQ-012**；对齐 **AS v1.11** / **DM-ADM-002** `charge_intent` |
| v1.10 | 2026-10-12 | §2.1 诊断口径：申请阶段不展开国临；展开改由 **AS-IP-DOR-002** IF-IP-DOR-02；对齐 **AS v1.10** |
| v1.8 | 2026-07-08 | **中医预填回显**与 `description` 口径对齐 **AS v1.9** |
| v1.7 | 2026-05-28 | 六维对账：§1.1 已落地口径；§2.4 诊断/参与者收口 **`EncounterDomainService#requestInpatientAdmission`**；上游 **AS v1.6** |
| v1.6.1 | 2026-05-28 | **L3 对账**：Request **`wardId`**（非 `wardLocationId`）；§2.1/§2.2 去重 `EncounterQueryService`、删除未使用的 `mustGetOutpatientContext`；与 `DoctorStationInpatientAdmissionAppService` 一致 |
| v* | 2026-05-28 | 下游 **InpatientEncounterContextQueryModel** 仍由事件投影；本服务**不**经 `EncounterContextFacade`（源就诊门禁直连 `EncounterQueryService`） |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-18 | 初版：对齐 `AS-OP-DOR-005` v1.0；IF-IP-REQ-01～02；§2.6 SDD-GAP-001～008 |
| v1.1 | 2026-05-18 | 产品裁决：固定 `doctorStationInpatientAdmission`；关闭 SDD-GAP-007；对齐 AS v1.1 |
| v1.2 | 2026-05-18 | 对齐 AS v1.2：双 Encounter INSERT；删除 `admissionConditionId`/后端默认引门诊主诊；`admissionDiagnoses`；`AggregateTypeConstants.INPATIENT_ENCOUNTER`（已落地） 住院 bus_no 前缀 |
| v1.3 | 2026-05-18 | 领域层关闭 GAP-001～005/008/009；查询命名 `findOpenInpatientLine`/`assertAmbForInpatientReq`；GAP-006 App 待接 |
| v1.4 | 2026-05-18 | 产品裁决：住院申请迁至独立包 **`inpatientadmission`**；`DoctorStationInpatientAdmissionAppService`；HTTP **`doctorStationInpatientAdmission`**；修订 SDD-GAP-007 |
| v1.5 | 2026-05-18 | 应用层成码对齐：关闭 SDD-GAP-006；§5.1 Cmd 表与 §3.2 单域内 `requestInpatientAdmission` 一致；测试缺口表更新 |
| v1.6 | 2026-05-20 | **Gap 收口**：删除 §2.6 SDD-GAP 表；正文引用改为领域/DM 锚点；对齐 AS/SDD Skill |
| v1.6 | 2026-05-19 | 对齐 **`DM-ADM-002` v1.11** / **AS-IP-FIN-001** v1.5：下游登记 `PLANNED→REGISTERED` |
| v1.7 | 2026-05-19 | 对齐 **AS-IP-FIN-001** v1.6：**BR-IP-REG-007** 登记只读住院诊断、提交不重写 |
| v1.9 | 2026-05-20 | §2.1 诊断口径：`saveAdmissionDx` 固定 `diagTypeCode=31`（**BR-ENC-021**）；对齐 **DM-ADM-002** v1.21、AS **v1.7** |
| v1.8 | 2026-05-20 | 对齐 **DM-ADM-002** v1.19 / QueryService **v3.2**：`InpatientAdmissionRequestedEvent.wardId` 投影 `InpatientEncounterContextQueryModel.ward_id` |

---

**文档负责人**：（待填） · **评审**：（待填）
