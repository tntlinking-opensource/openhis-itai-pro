# 门诊医生站 · 诊断录入 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-002-SDD  
> **版本**：v1.11 | **日期**：2026-09-09 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界


| 项          | 内容                                                                                                                                                                                               |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **模块**     | 门诊医生站 · 诊断录入应用服务（`outpatient/doctorstation/diagnosis`）                                                                                                                                           |
| **边界**     | 就诊诊断列表查询、四类常用诊断查询、**慢特病备案可导入诊断查询（CHS 门控）**、诊断定义检索、诊断批量保存（全量替换）、诊断删除、**单点切换主诊**；不在应用层实现聚合内部不变式                                                                                                                                        |
| **编排权威**   | `AS-OP-DOR-002-门诊医生站诊断录入.md`（v2.1+）§2 + §4                                                                                                                                                       |
| **方法权威**   | `DM-ADM-002-Encounter-就诊.md` §6.1.9b、§6.1.11～6.1.13；`DM-CLN-003-Condition-诊断记录.md` §6.1.1～6.1.3、§6.4；`DM-CLN-002-ConditionDefinition-诊断定义.md` §6.2.6～6.2.8；`DM-CUS-003-ConditionTemplate-诊断常用模板.md` §6.2 |
| **严格对齐**   | IF 编号 IF-DOR-06/06A/06B/07/08/09/09A 与 AS 一致                                                                                                                                                             |
| **实现阶段口径** | **目标态全量契约**。IF-DOR-06～09 按 DM 方法白名单可实现，QueryService查询以 `OutpatientEncounterContextQueryModel`/`ConditionTemplate` 为准                                                                                                            |


### 1.2 术语口径


| 项             | 约定                                                                                                                                                                                                                                                                                                                                   |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **字段口径**      | 入出参与 AS、DM、DM §6.2一致；枚举存 **String code**（与 `ConditionVerificationStatus` 等对齐）                                                                                                                                                                                                                                                          |
| **QueryService口径**     | IF-DOR-06：`OutpatientEncounterContextQueryModel` 经 `EncounterQueryService`；IF-DOR-06A：病人历史经 `ConditionQueryService#findByPatientId` 聚合定义后装配，常用三类经 `ConditionTemplateQueryService#listByOwnerAndType`（与 `cus_condition_template` 对齐）；IF-DOR-07：已发布目录经 `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions`（JPA `Specification`，非QueryService专表） |
| **事务口径**      | 逻辑顺序：IF-DOR-08 先 `ConditionDomainService#saveDoctorDiagnosis`（Cmd-1）后 `EncounterDomainService#batchUpsertEncounterDiagnosisAssociation`（Cmd-2）；IF-DOR-09 先 `removeEncounterDiagnosisAssociation` 后 `delEncounterDiagnosis`。**应用层不声明 `@Transactional`**，与《代码生成通用规范》一致；跨聚合强一致由调用链与领域实现保证                                               |
| **HTTP 接入口径** | 工程由 `whale-web` 的 `AppServiceControllerRegistrar` 动态注册：`POST /api/app/doctorStationDiagnosis/{methodName}`，请求体 JSON 与方法形参一致（`serviceName` 由 `DoctorStationDiagnosisAppService` 推导为 `doctorStationDiagnosis`）                                                                                                                         |
| **业务异常码口径**   | 实现层统一抛出 `BusinessException`，`errorCode` 取 `ErrorCodeConstants.ErrorCode` 常量；AS 文档中的 `EX-DOD-`*/`EX-VAL-*` 为语义别名，与实现对照见下表                                                                                                                                                                                                             |


#### 1.2.1 诊断读快照与领域事件载荷（IF-DOR-06 / 08 / 09）

- **IF-DOR-06**：`EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId).diagnosisJson()` → `DoctorStationDiagnosisMapper#parseDiagnosisJson` → `#toEncounterDiagnosisResponse`（**禁止**以 `OutpatientEncounterContextQueryModel.diagnosisJson` 作为 Tab 读真源）。
- **IF-DOR-08 / IF-DOR-09**：写路径 `EncounterDomainService` 发布关联类事件时 **可选** 携带 `diagnosisJson`；**无** QueryService **`SET diagnosis_json`** 投影。Loader 批量 enrichment 时写入 QueryModel 内部字段（**B.6.2** 禁止 API 透传）。

#### 1.2.2 文档语义码与 `ErrorCodeConstants.ErrorCode` 对照（实现口径）


| 文档语义码                                | 实现 `ErrorCode`（典型触发）                                                                                                |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| EX-DOD-104                           | `ENCOUNTER_NOT_FOUND`（QueryService无就诊行）、`ENCOUNTER_INVALID_ARGUMENT`（请求 `patientId` 与快照不一致、未登录操作人、快照 JSON 解析失败、就诊类别非门诊门诊） |
| EX-DOD-101                           | `ENCOUNTER_INVALID_ARGUMENT` 或检索类失败统一口径（与 EX-DOD-104 共用实现常量时可合并为上述之一）                                               |
| EX-VAL-001 / EX-VAL-002 / EX-VAL-003 | `CONDITION_INVALID_ARGUMENT`（批次空、主诊重复、排序号重复、中西医类型非法等）                                                               |
| EX-DOD-105                           | `ENCOUNTER_RULE_CONFLICT`（保存/删除非业务异常包装后的持久化失败提示）                                                                    |
| EX-DOD-102                           | `CONDITION_RULE_CONFLICT`（医嘱引用计数大于零；当前 `ConditionQueryService#countAdviceRefByConditionId` 若为占位实现则门控可能恒为通过，见 §3.6 说明） |
| EX-DOD-106                           | 平台 5301 失败：不映射为硬失败码，AppService warn 后空列表（见 §3.3A） |
| EX-DOD-107                           | 行级未映射提示，不抛接口级 `BusinessException` |


---

## 2. 代码结构

### 2.1 包路径（类级清单）

```
health-application/src/main/java/com/openhis/health/application/
└── outpatient/
    └── doctorstation/
        └── diagnosis/
            ├── service/
            │   └── DoctorStationDiagnosisAppService.java
            ├── request/
            │   ├── GetEncounterDiagnosisRequest.java
            │   ├── GetCommonDiagnosesRequest.java
            │   ├── GetPatientOpspFilingDiagnosisRequest.java
            │   ├── PageConditionDefinitionRequest.java
            │   ├── SaveEncounterDiagnosisRequest.java
            │   ├── SaveEncounterDiagnosisChildRequest.java
            │   └── DeleteEncounterDiagnosisRequest.java
            ├── response/
            │   ├── EncounterDiagnosisResponse.java
            │   ├── CommonDiagnosesResponse.java
            │   ├── PatientOpspFilingDiagnosisResponse.java
            │   ├── ConditionDefinitionPageResponse.java
            │   ├── ConditionDefinitionRowResponse.java
            │   ├── SaveEncounterDiagnosisResponse.java
            │   └── DeleteEncounterDiagnosisResponse.java
            ├── dto/
            │   ├── EncounterDiagnosisRowDto.java
            │   ├── CommonDiagnosisRowDto.java
            │   └── OpspFilingDiagnosisRowDto.java
            └── mapper/
                ├── DoctorStationDiagnosisMapper.java
                └── DoctorStationDiagnosisMapperImpl.java
```

### 2.2 依赖注入表


| 注入类型       | 类名（FQCN）                                                            | 职责                                                                                           | 来源                                       |
| ---------- | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------- |
| 应用服务       | `DoctorStationDiagnosisAppService`                                  | 编排诊断查询与写操作                                                                                   | 本模块                                      |
| 领域服务       | `EncounterDomainService`                                            | 就诊诊断关联维护（`batchUpsertEncounterDiagnosisAssociation`/`removeEncounterDiagnosisAssociation`/`upsertEncounterDiagnosisAssociation`/`switchPrimaryEncounterDiagnosis`）；发布关联事件前拼装 **`diagnosisJson`/`conditionDefinitionName`** 快照 | domain.administration.encounter          |
| 领域服务       | `ConditionDomainService`                                            | 诊断事实写（`saveDoctorDiagnosis`/`delEncounterDiagnosis` 等）                                       | domain.clinical.condition                |
| Facade       | `EncounterContextReader`                                            | `mustGetOutpatientContextByEncounterId`（`application.shared.encounter`） |
| Bridge 装配  | `EncounterDiagnosisSnapshotAssembler`                           | `buildSnapshot(encounterId)` → 内部 `diagnosisJson`；IF-DOR-06 经 Mapper **`parseDiagnosisJson`** 结构化（**B.6.2**） | `application.shared.diagnosis` |
| Bridge 装配  | `PatientHistoryCommonDiagnosisAssembler`                            | IF-DOR-06A 病人历史：事实扫描 + 就诊关联反查；西医按定义去重、中医按病证组合并 | `application.shared.diagnosis` |
| 查询服务       | `EncounterQueryService`                                             | 薄就诊门控；`listActiveAssociationsByConditionIds` 供病人历史病证组反查 |
| 查询服务       | `ConditionDefinitionQueryService`                                   | 已发布诊断定义分页、按 id 批量装载、按门慢门特码批量解析（含 `listActiveDoctorStationRowsByOpspDiseCodes`） | domain.clinical.conditiondefinition      |
| 集成扩展       | `InsuranceRegisterBenefitQueryExtension`                            | CHS 门控 `isEnabled`；`resolveOpspFilingEncounter`；`queryOpspFiling`（5301） | infrastructure.integration.insurance.benefit |
| 查询服务       | `ConditionTemplateQueryService`                                     | 常用模板绑定只读列表（`listByOwnerAndType`）                                                             | domain.custom.conditiontemplate          |
| 查询服务       | `ConditionQueryService`                                             | 病人历史诊断扫描（`findByPatientId`）、`countAdviceRefByConditionId` 医嘱引用门控                                | domain.clinical.condition                |
| 查询服务       | `CodeSystemQueryService`                                            | 目录来源术语展示（`getOptionListByCodeSystemName`）                                                    | domain.terminology                       |
| 操作人/范围    | `CurrentOperatorResolver` / `CurrentManagedOrganizationIdsResolver` / `CurrentPractitionerResolver` | 写路径 `operatorId`；常用树科室/医师维度（**B.6.5**） | `module.common.validator` |
| Mapper     | `DoctorStationDiagnosisMapper` + `DoctorStationDiagnosisMapperImpl` | Request/Command/JSON 快照/Response 装配；JSON 解析经 `DiagnosisJsonSnapshotParser#parseStrict` | 本模块 mapper/                              |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别        | 代码实现锚点                                                                                                                                                                                                | DM 追溯锚点                              | 说明                                                                                                                             |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| 就诊状态枚举    | `com.zxplat.domain.share.enums.EncounterStatus`                                                                                                                                               | `DM-ADM-002 §2.3` 状态定义               | IF-DOR-06 列表过滤条件；取值：`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED` 等                                                        |
| 诊断验证状态    | Request 整型 `verificationStatusEnum` → 领域命令 `SaveDoctorDiagnosisCommand` 内 `String verificationStatusEnum`（如 `CONFIRMED`/`UNCONFIRMED`…）                                                               | `DM-CLN-003 §2.3.1`                  | 应用层 `DoctorStationDiagnosisMapperImpl#resolveVerificationCode` 映射；仓库侧若尚无独立 `ConditionVerificationStatus` 枚举类，以命令 String 为持久化口径 |
| 诊断临床状态枚举  | `com.zxplat.domain.share.enums.ConditionClinicalStatus`                                                                                                                                       | `DM-CLN-003 §2.3.2` 临床状态             | IF-DOR-08 Request 可选字段；取值：`ACTIVE`/`RECURRENCE`/`RELAPSE`/`INACTIVE`/`REMISSION`/`RESOLVED`/`UNKNOWN`                          |
| 发布状态枚举    | `com.zxplat.domain.share.enums.PublicationStatus`                                                                                                                                             | `DM-CLN-002 §2.3` 状态机                | IF-DOR-07 过滤条件；取值：`DRAFT`/`ACTIVE`/`RETIRED`/`UNKNOWN`；仅 `ACTIVE` 参与检索                                                         |
| 诊断定义来源过滤  | `PageConditionDefinitionRequest.medicineTypeCode` → `DoctorStationDiagnosisMapper#parseDiagnosisKindSourceFilter` 产出 **`List<String>`**（`general_dx`/`tcm_dx` 等 V58 术语码）参与 `source_enum IN` | `DM-CLN-002 §4.1`                    | IF-DOR-07；集合定义见 `DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_*`                                                                       |
| 诊断分类 code | `SaveEncounterDiagnosisChildRequest.categoryCode`                                                                                                                                                     | `DM-CLN-003 §3.1` 字段 `category_enum` | 诊断分类字典字段；关联 IF-DOR-08                                                                                                          |
| 严重程度 code | `SaveEncounterDiagnosisChildRequest.severityCode`                                                                                                                                                     | `DM-CLN-003 §3.1` 字段 `severity_enum` | 严重程度字典字段；关联 IF-DOR-08                                                                                                          |
| 中西医类型参数   | `PageConditionDefinitionRequest.medicineTypeCode`                                                                                                                                                     | `DM-CLN-002 §4.1` 所属分类               | IF-DOR-07 查询参数；`1`=西医，`2`=中医；按 `source_enum` IN 集合过滤                                                                           |
| 慢特病映射匹配列 | `OpspDiseCodeDefinitionMatchQueryModel.matchField`（`disease_code`/`yb_no`/`condition_code`）                                                                                                         | `DM-CLN-002 §6.2.8` 匹配优先级          | IF-DOR-06B                                                                                                                   |
| 慢特病候选状态   | `OpspFilingDiagnosisRowDto.matchStatus`（`matched`/`unmatched`/`expired`/`alreadyOnEncounter`/`ambiguous`）                                                                                           | `AS-OP-DOR-002` BR-DOD-013/014          | IF-DOR-06B 行状态                                                                                                            |
| 特慢病目录来源   | `DoctorStationDiagnosisConstants.SOURCE_CHRONIC_SPEC`                                                                                                                                                 | `DM-CLN-002 §4.1` `chronic_spec`        | IF-DOR-06B 歧义择优                                                                                                                        |


---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）


| IF         | 应用服务方法（英文）                            | 中文方法名        | HTTP                                                                       |
| ---------- | ------------------------------------- | ------------ | -------------------------------------------------------------------------- |
| IF-DOR-06  | `getEncounterDiagnosis`               | 查询就诊诊断信息     | `POST /api/app/doctorStationDiagnosis/getEncounterDiagnosis`               |
| IF-DOR-06A | `getConditionDefinitionBusinessClass` | 查询诊断定义业务分类数据 | `POST /api/app/doctorStationDiagnosis/getConditionDefinitionBusinessClass` |
| IF-DOR-06B | `getPatientOpspFilingDiagnosis`       | 查询慢特病备案可导入诊断 | `POST /api/app/doctorStationDiagnosis/getPatientOpspFilingDiagnosis`       |
| IF-DOR-06C | `ensureOpspFilingDiagnosis`           | 未匹配慢病备案补录目录 | `POST /api/app/doctorStationDiagnosis/ensureOpspFilingDiagnosis`           |
| IF-DOR-07  | `getConditionDefinitionMetadata`      | 查询诊断定义列表     | `POST /api/app/doctorStationDiagnosis/getConditionDefinitionMetadata`      |
| IF-DOR-08  | `saveDoctorDiagnosis`                 | 医生保存诊断       | `POST /api/app/doctorStationDiagnosis/saveDoctorDiagnosis`                 |
| IF-DOR-09  | `delEncounterDiagnosis`               | 删除就诊诊断信息     | `POST /api/app/doctorStationDiagnosis/delEncounterDiagnosis`               |
| IF-DOR-09A  | `switchMainEncounterDiagnosis`        | 切换就诊主诊断       | 待挂路由；目标前缀同现诊断 Controller：`POST /api/app/doctorStationDiagnosis/switchMainEncounterDiagnosis` |


#### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段       | 含义                                                                                                 | 失败时                    |
| -------- | -------------------------------------------------------------------------------------------------- | ---------------------- |
| **Gate** | 权限/租户/参数门禁：校验 encounterId/patientId 必填、当前登录医生权限                                                    | 不再调用后续步骤，返回 EX-DOD-104 |
| **Pre**  | 写前只读：加载就诊快照（`EncounterContextReader#mustGetOutpatientContextByEncounterId`）、校验医嘱引用数量            | 抛业务异常，**禁止进入 Cmd**     |
| **Cmd**  | 领域写：`ConditionDomainService` 批量写诊断事实，`EncounterDomainService` 批量写就诊诊断关联；持久化 + 领域事件                 | 失败语义见 §5.2             |
| **Asm**  | Mapper 纯 DTO 装配：`DoctorStationDiagnosisMapper` 各 `parse*` / `to*Response` / `to*Command` 方法，无领域不变式 | —                      |


**记法**：`EncounterContextReader#mustGetOutpatientContextByEncounterId` 表示在 AppService 中对注入的该类型 Bean 调用该方法。

**重要说明**：先读完各 IF 的「领域编排序列」，按序号从上到下执行。

---

### 3.2 IF-DOR-06 `getEncounterDiagnosis`（查询就诊诊断信息）


| 项        | 内容                                                           |
| -------- | ------------------------------------------------------------ |
| **App**  | `DoctorStationDiagnosisAppService#getEncounterDiagnosis`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/getEncounterDiagnosis` |


#### Request 字段表（JSON 请求体，与 `GetEncounterDiagnosisRequest` 一致）


| 字段          | 类型   | 必填  | 说明                                                    |
| ----------- | ---- | --- | ----------------------------------------------------- |
| encounterId | Long | 是   | 就诊标识                                                  |
| patientId   | Long | 是   | 患者标识；与QueryService `OutpatientEncounterContextQueryModel.patientId` 比对，防串患者 |


#### Response 字段表


| 字段            | 类型      | 说明      |
| ------------- | ------- | ------- |
| diagnosisList | List    | 就诊诊断行列表 |
| totalCount    | Integer | 列表总数    |


#### DTO 字段表（EncounterDiagnosisRowDto）


| 字段                         | 类型      | 说明                          |
| -------------------------- | ------- | --------------------------- |
| conditionId                | Long    | 诊断事实标识                      |
| encounterDiagnosisId       | Long    | 就诊诊断关联标识                    |
| conditionDefinitionId      | Long    | 诊断定义标识                      |
| conditionCode              | String  | 诊断编码                        |
| conditionDefinitionName    | String  | 诊断定义名称（目录标准名）               |
| diagnosisKind              | String  | 中西医维度：`WESTERN`=西医，`TCM`=中医 |
| diagnosisKindText          | String  | 中西医维度中文展示                   |
| verificationStatusEnum     | Integer | 验证状态：见 §2.3 枚举              |
| verificationStatusEnumText | String  | 验证状态展示文本                    |
| mainDiagnosisFlag          | Integer | 主诊标记：1=主诊断，0=非主诊断           |
| diagnosisSortNo            | Integer | 诊断排序号                       |
| medicalTypeCode            | String  | 医疗类型代码                      |
| diagnosisDesc              | String  | 诊断描述                        |
| iptDiseaseTypeCode         | String  | 患者疾病诊断类型代码                  |
| iptDiseaseTypeText         | String  | 患者疾病诊断类型展示文本                |
| ybNo                       | String  | 医保诊断编码                      |
| tcmSyndromeGroup           | String  | 中医病证组号（同组病名+证候共用）           |
| sourceEnum                 | String  | 诊断定义目录来源码（如 `tcm_dx`/`tcm_syndrome`） |


#### 关键校验

- encounterId 与 patientId 必须匹配当前就诊上下文，否则 Gate 失败 EX-DOD-104

#### 失败码口径表


| 失败码        | 场景       | 用户提示      | 实现 `ErrorCode`（见 §1.2.2）                             |
| ---------- | -------- | --------- | ---------------------------------------------------- |
| EX-DOD-101 | 查询失败     | 缩小条件或稍后重试 | `ENCOUNTER_INVALID_ARGUMENT`（含快照 JSON 解析失败等）         |
| EX-DOD-104 | 未选择患者或就诊 | 请先选择患者    | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` |


#### 领域编排序列（严格顺序）


| 序号  | 阶段  | 调用                                                                                                                      | 输入要点              | 说明                                                                  |
| --- | --- | ----------------------------------------------------------------------------------------------------------------------- | ----------------- | ------------------------------------------------------------------- |
| ①   | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId): OutpatientEncounterContextQueryModel`                 | encounterId       | 加载就诊快照；无行抛 `ENCOUNTER_NOT_FOUND`                                    |
| ②   | Pre | `DoctorStationDiagnosisMapper#validateEncounterPatientGate(snapshot, requestPatientId)`                                 | Request.patientId | 比对快照 `patientId`；不一致抛 `ENCOUNTER_INVALID_ARGUMENT`（语义等同 EX-DOD-104） |
| ③   | Pre | AppService 校验 `classEnum` 为门诊 `AMB`                                                                        | ① 结果              | 非上述类别抛 `ENCOUNTER_INVALID_STATUS`                                   |
| ④   | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId).diagnosisJson()`                         | encounterId       | 实时扫表拼装；**禁止**以 ① 的 QueryModel `diagnosisJson` 作真源              |
| ⑤   | Asm | `DoctorStationDiagnosisMapper#parseDiagnosisJson(String): List<EncounterDiagnosisRowDto>`                               | ④ 结果              | 解析委托 `DiagnosisJsonSnapshotParser#parseStrict`；空 JSON → 空列表       |
| ⑥   | Asm | `DoctorStationDiagnosisMapper#toEncounterDiagnosisResponse(List<EncounterDiagnosisRowDto>): EncounterDiagnosisResponse` | ⑤ 结果              | 装配 `diagnosisList` 与 `totalCount`                                   |


---

### 3.3 IF-DOR-06A `getConditionDefinitionBusinessClass`（查询诊断定义业务分类数据）


| 项        | 内容                                                                         |
| -------- | -------------------------------------------------------------------------- |
| **App**  | `DoctorStationDiagnosisAppService#getConditionDefinitionBusinessClass`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/getConditionDefinitionBusinessClass` |


#### Request 字段表（JSON 请求体，与 `GetCommonDiagnosesRequest` 一致）


| 字段        | 类型   | 必填  | 说明                 |
| --------- | ---- | --- | ------------------ |
| patientId | Long | 是   | 患者标识（用于病人历史诊断事实扫描） |


#### Response 字段表


| 字段                  | 类型   | 说明     |
| ------------------- | ---- | ------ |
| patientHistoryList  | List | 病人历史诊断 |
| doctorCommonUseList | List | 医生常用诊断 |
| userPersonalList    | List | 用户个人诊断 |
| organizationList    | List | 科室诊断   |
| medicalTypeOptions | List\<OptionDto\> | 门诊诊断类型下拉（**BR-OP-DOD-016**；`value`=YbMedType code，`label`=display；不含住院向码） |


#### DTO 字段表（CommonDiagnosisRowDto）


| 字段             | 类型      | 说明                          |
| -------------- | ------- | --------------------------- |
| conditionDefinitionId | Long    | 诊断定义标识                      |
| conditionCode  | String  | 诊断编码                        |
| conditionDefinitionName | String  | 诊断定义名称                    |
| diagnosisKind  | String  | 中西医维度：`WESTERN`=西医，`TCM`=中医 |
| diagnosisKindText | String  | 中西医维度中文展示                 |
| sourceEnum     | Integer | 来源分类（DM-CLN-002 §4.1）       |
| sourceEnumText | String  | 来源分类展示文本                    |
| ybNo           | String  | 医保诊断编码（定义级，可空）             |
| tcmSyndromeGroup | String | 中医证候组号（历史分支合并行回填，可空） |
| syndromeDefinitionId | Long | 证候定义主键（仅中医合并历史行，可空） |


#### 关键校验

- 当前登录用户、科室、医师解析：`CurrentOperatorResolver#findOperatorIdOrNull`、`CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery`、`CurrentPractitionerResolver#findPractitionerIdOrNull`（无绑定则医生常用列表为空）

#### 失败码口径表


| 失败码        | 场景   | 用户提示      | 实现 `ErrorCode`                            |
| ---------- | ---- | --------- | ----------------------------------------- |
| EX-DOD-101 | 查询失败 | 缩小条件或稍后重试 | 实现中未单独区分时可并入 `ENCOUNTER_INVALID_ARGUMENT` |


#### 领域编排序列（严格顺序）


| 序号  | 阶段   | 调用                                                                                                                                                                                             | 输入要点              | 说明                                                                    |
| --- | ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- | --------------------------------------------------------------------- |
| ①   | Gate | `CurrentOperatorResolver#findOperatorIdOrNull()`                                                                                                                                                              | —                 | 用户 id；可空时个人列表为空                                                       |
| ②   | Gate | `CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()`                                                                                                                                                  | —                 | 科室 id；可空时科室列表为空                                                       |
| ③   | Gate | `CurrentPractitionerResolver#findPractitionerIdOrNull()`                                                                                                                                                | —                 | 解析医师；无则医生常用列表为空                                                       |
| ④   | Pre  | `PatientHistoryCommonDiagnosisAssembler#assemble(patientId)` + `DoctorStationDiagnosisMapper#toCommonDiagnosisRowDto` | Request.patientId | 病人历史：事实表扫描 + 就诊关联反查；**西医**按 `conditionDefinitionId` 去重；**中医**按就诊内 `tcm_syndrome_group` 合并病+证为一行，跨就诊再按病证定义对去重 |
| ⑤   | Pre  | `ConditionTemplateQueryService#listByOwnerAndType(practitionerId, ConditionTemplateBinding.PERSONAL.getCode())`                                                                                | ③                 | 医生常用：以医师主键为 `object_id`、绑定码 `**PERSONAL`**（与实现一致；若业务改为独立绑定码需同步改码表与代码） |
| ⑥   | Pre  | `ConditionTemplateQueryService#listByOwnerAndType(userId, ConditionTemplateBinding.PERSONAL.getCode())`                                                                                        | ①                 | 用户个人模板                                                                |
| ⑦   | Pre  | `ConditionTemplateQueryService#listByOwnerAndType(orgId, ConditionTemplateBinding.DEPARTMENT.getCode())`                                                                                       | ②                 | 科室模板：绑定码与 `DM-CUS-003` §4.1 `department` 及 AS v1.5+ 口径一致              |
| ⑧   | Asm  | `DoctorStationDiagnosisMapper#toCommonDiagnosesResponse(..., medicalTypeOptions): CommonDiagnosesResponse`                                                                                                         | ④⑤⑥⑦              | 装配四类列表 + `buildOutpatientMedicalTypeOptions` |


---

### 3.3A IF-DOR-06B `getPatientOpspFilingDiagnosis`（查询慢特病备案可导入诊断）


| 项        | 内容                                                                   |
| -------- | -------------------------------------------------------------------- |
| **App**  | `DoctorStationDiagnosisAppService#getPatientOpspFilingDiagnosis`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/getPatientOpspFilingDiagnosis` |


#### Request 字段表（JSON 请求体，与 `GetPatientOpspFilingDiagnosisRequest` 一致）


| 字段          | 类型   | 必填  | 说明                         |
| ----------- | ---- | --- | -------------------------- |
| encounterId | Long | 是   | 就诊标识                       |
| patientId   | Long | 是   | 患者标识；与门诊就诊上下文比对，防串患者 |


#### Response 字段表（`PatientOpspFilingDiagnosisResponse`）


| 字段                      | 类型      | 说明                                                                 |
| ----------------------- | ------- | ------------------------------------------------------------------ |
| opspFilingImportEnabled | Boolean | CHS 门控：false 时前端隐藏「慢病备案」；true 才允许展示与刷新 |
| filingList              | List    | 备案候选行（见 DTO）；未启用时为空列表                              |


#### DTO 字段表（`OpspFilingDiagnosisRowDto`）


| 字段                      | 类型      | 说明                                                                 |
| ----------------------- | ------- | ------------------------------------------------------------------ |
| opspDiseCode            | String  | 门慢门特病种目录代码                                                     |
| opspDiseName            | String  | 门慢门特病种名称                                                         |
| beginDate               | String  | 备案开始日期（yyyy-MM-dd，可空）                                           |
| endDate                 | String  | 备案结束日期（yyyy-MM-dd，可空）                                           |
| valid                   | Boolean | 是否在有效期内                                                            |
| matchStatus             | String  | `matched` / `unmatched` / `expired` / `alreadyOnEncounter` / `ambiguous` |
| matchHint               | String  | 未匹配/歧义/过期时的提示文案（可空）                                         |
| conditionDefinitionId   | Long    | 映射成功时的诊断定义主键；未匹配可空                                           |
| conditionCode           | String  | 诊断目录编码（映射成功时）                                                    |
| conditionDefinitionName | String  | 诊断定义名称（映射成功时）                                                    |
| matchField              | String  | 命中列：`disease_code` / `yb_no` / `condition_code`（可空）               |
| diagnosisKind           | String  | `WESTERN` / `TCM`（由目录来源推导，可空）                                   |


#### 关键校验

- encounterId / patientId 必填且匹配门诊就诊上下文，否则 EX-DOD-104
- `InsuranceRegisterBenefitQueryExtension#isEnabled()==false` 时：**不**调用平台、**不**解析参保上下文，直接返回 `opspFilingImportEnabled=false` 与空列表（BR-DOD-012）

#### 失败码口径表


| 失败码        | 场景                 | 用户提示                         | 实现 `ErrorCode`（见 §1.2.2）                     |
| ---------- | ------------------ | ---------------------------- | -------------------------------------------- |
| EX-DOD-104 | 未选择患者或就诊 / 串患者    | 请先选择患者                       | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` |
| EX-DOD-101 | 目录映射查询失败           | 缩小条件或稍后重试                    | `CONDITION_DEFINITION_INVALID_ARGUMENT` 等   |
| EX-DOD-106 | 平台 5301 失败（模块已启用） | 暂时无法加载慢病备案，可稍后刷新             | 不抛阻断：warn + 空列表 + enabled 仍为 true           |
| EX-DOD-107 | 行级未映射（业务提示）        | 该病种未维护院内目录对码，请联系信息科维护后再导入 | 行级 `matchStatus=unmatched`，不抛接口级异常         |


#### Mapper 接口方法签名

```java
PatientOpspFilingDiagnosisResponse toPatientOpspFilingDiagnosisResponse(
    boolean opspFilingImportEnabled, List<OpspFilingDiagnosisRowDto> filingList);
OpspFilingDiagnosisRowDto toOpspFilingDiagnosisRowDto(
    InsuranceOpspFilingLineSnapshot filingLine,
    OpspDiseCodeDefinitionMatchQueryModel match,
    String matchStatus,
    String matchHint,
    boolean valid);
```

#### 领域编排序列（严格顺序）


| 序号  | 阶段  | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `InsuranceRegisterBenefitQueryExtension#isEnabled(): boolean` | — | 若 false → Asm 返回 enabled=false 空列表并**结束**（不进入后续步骤） |
| ② | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)` | Request.encounterId | 加载门诊上下文 |
| ③ | Pre | `DoctorStationDiagnosisMapper#validateEncounterPatientGate(snapshot, requestPatientId)` | Request.patientId | 串患者拒绝 |
| ④ | Pre | AppService 校验 `classEnum` 为 `AMB` | ② | 非门诊抛 `ENCOUNTER_INVALID_STATUS` |
| ⑤ | Pre | `InsuranceRegisterBenefitQueryExtension#resolveOpspFilingEncounter(encounterId): InsuranceOpspFilingEncounterSnapshot` | encounterId | 解析 `contractId`+`psnNo`；若 null/缺字段 → Asm 返回 enabled=true、空列表（自费/无平台映射，不报错） |
| ⑥ | Pre | `InsuranceRegisterBenefitQueryExtension#queryOpspFiling(InsuranceOpspFilingQueryExtensionIn)` | ⑤ | 5301；失败 catch → warn + 空备案行（EX-DOD-106 弱提示语义） |
| ⑦ | Pre | AppService 过滤过期备案行（`enddate` 为空或 ≥ 今天为有效；过期行仍可带回并标 `expired`） | ⑥ | BR-DOD-013 |
| ⑧ | Pre | `ConditionDefinitionQueryService#listActiveDoctorStationRowsByOpspDiseCodes(codes): List<OpspDiseCodeDefinitionMatchQueryModel>` | 有效备案码集合 | DM §6.2.8 |
| ⑨ | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId)` + Mapper 解析当前已开立 `conditionDefinitionId` 集合 | encounterId | 用于 `alreadyOnEncounter` |
| ⑩ | Asm | `DoctorStationDiagnosisMapper#toOpspFilingDiagnosisRowDto` / `#toPatientOpspFilingDiagnosisResponse` | ⑦⑧⑨ | 装配行状态与启用标志 |


---

### 3.3B IF-DOR-06C `ensureOpspFilingDiagnosis`（未匹配慢病备案补录目录）


| 项        | 内容                                                                   |
| -------- | -------------------------------------------------------------------- |
| **App**  | `DoctorStationDiagnosisAppService#ensureOpspFilingDiagnosis`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/ensureOpspFilingDiagnosis` |


#### Request 字段表（`EnsureOpspFilingDiagnosisRequest`）


| 字段          | 类型   | 必填  | 说明                         |
| ----------- | ---- | --- | -------------------------- |
| encounterId | Long | 是   | 就诊标识                       |
| patientId   | Long | 是   | 患者标识；与门诊就诊上下文比对，防串患者 |
| opspDiseCode | String | 是 | 门慢门特病种目录代码（5301） |
| opspDiseName | String | 否 | 备案名称；平台名为空时回退 |


#### Response 字段表（`EnsureOpspFilingDiagnosisResponse`）


| 字段                      | 类型      | 说明                                                                 |
| ----------------------- | ------- | ------------------------------------------------------------------ |
| opspDiseCode            | String  | 门慢门特病种目录代码                                                     |
| opspDiseName            | String  | 门慢门特病种名称                                                         |
| conditionDefinitionId   | Long    | 可导入诊断定义主键                                           |
| conditionCode           | String  | 诊断目录编码                                                    |
| conditionDefinitionName | String  | 诊断定义名称                                                    |
| diagnosisKind           | String  | `WESTERN` / `TCM`（可空）                                   |
| created                 | Boolean | true=本次对 `chronic_spec` 新建或启用；false=复用已有目录未写库 |


#### 关键校验

- encounterId / patientId / opspDiseCode 必填；门诊就诊与串患者门禁同 IF-DOR-06B
- `InsuranceRegisterBenefitQueryExtension#isEnabled()==false` 时拒绝（BR-DOD-012）
- 再查 5301：备案中须存在该码且未过期；歧义匹配拒绝

#### 领域编排序列（严格顺序）


| 序号  | 阶段  | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | `isEnabled` / 门诊就诊 / 串患者 / 备案码非空 | 不满足则抛业务异常 |
| ② | Pre | `resolveOpspFilingEncounter` + `queryOpspFiling` | 核对备案行属于本患者 |
| ③ | Pre | `listActiveDoctorStationRowsByOpspDiseCodes` | 已唯一匹配则跳过写库 |
| ④ | Cmd | `ConditionDefinitionDomainService#ensureActiveChronicSpecOpspDisease` | 仅写/启用 `chronic_spec` |
| ⑤ | Asm | 装配 `EnsureOpspFilingDiagnosisResponse` | 前端再走 IF-DOR-08 |


---

### 3.4 IF-DOR-07 `getConditionDefinitionMetadata`（查询诊断定义列表）


| 项        | 内容                                                                    |
| -------- | --------------------------------------------------------------------- |
| **App**  | `DoctorStationDiagnosisAppService#getConditionDefinitionMetadata`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/getConditionDefinitionMetadata` |


#### Request 字段表


| 字段               | 类型      | 必填  | 说明                                |
| ---------------- | ------- | --- | --------------------------------- |
| medicineTypeCode | String  | 否   | 中西医类型：`1`=西医，`2`=中医；为空时不过滤 source |
| searchKey        | String  | 否   | 模糊查询关键字（匹配 name/py_str/wb_str）    |
| pageNum          | Integer | 是   | 当前页码，默认 1                         |
| pageSize         | Integer | 是   | 每页条数，默认 10                        |


#### Response 字段表（ConditionDefinitionPageResponse）


| 字段      | 类型      | 说明      |
| ------- | ------- | ------- |
| total   | Long    | 总记录数    |
| pages   | Integer | 总页数     |
| current | Integer | 当前页码    |
| size    | Integer | 每页大小    |
| records | List    | 诊断定义行列表 |


#### DTO 字段表（ConditionDefinitionRowResponse）


| 字段             | 类型      | 说明                    |
| -------------- | ------- | --------------------- |
| conditionDefinitionId | Long    | 诊断定义标识                |
| conditionCode  | String  | 诊断编码                  |
| conditionDefinitionName | String  | 诊断定义名称                |
| pyStr          | String  | 拼音检索码                 |
| wbStr          | String  | 五笔检索码                 |
| diagnosisKind  | String  | 中西医维度：`WESTERN`/`TCM` |
| diagnosisKindText | String  | 中西医维度中文展示            |
| sourceEnum     | Integer | 来源分类                  |
| sourceEnumText | String  | 来源分类展示文本              |
| ybFlag         | Integer | 医保标记：1=是，0=否          |
| ybFlagText     | String  | 医保标记展示文本              |
| statusEnum     | Integer | 发布状态：仅返回 ACTIVE(2)    |


#### 关键校验

- medicineTypeCode 仅允许 `1`/`2`/null；**非法非空**抛 `CONDITION_DEFINITION_INVALID_ARGUMENT`（实现口径，语义对齐「抛参错」）

#### 失败码口径表


| 失败码        | 场景   | 用户提示      | 实现 `ErrorCode`                            |
| ---------- | ---- | --------- | ----------------------------------------- |
| EX-DOD-101 | 检索失败 | 缩小条件或稍后重试 | `CONDITION_DEFINITION_INVALID_ARGUMENT` 等 |


#### 领域编排序列（严格顺序）


| 序号  | 阶段   | 调用                                                                                                                                                               | 输入要点                            | 说明                                                                       |
| --- | ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- | ------------------------------------------------------------------------ |
| ①   | Gate | `DoctorStationDiagnosisMapper#parseDiagnosisKindSourceFilter(diagnosisKindCode): List<String>`                                                                     | Request.medicineTypeCode        | `1`→西医 source 集合（`general_dx`/`chronic_spec`/…）；`2`→`tcm_dx`/`tcm_syndrome`；null/空白→null（不过滤 source）     |
| ②   | Pre  | `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions(searchKey, sourceEnumIn, pageNum, pageSize): Page<DoctorStationConditionDefinitionRowModel>` | searchKey, ①, pageNum, pageSize | 仅 `ACTIVE`+未软删；关键字 OR 匹配 name/conditionCode/pyStr/wbStr；`updatedAt` desc |
| ③   | Pre  | `CodeSystemQueryService#getOptionListByCodeSystemName(CONDITION_DEFINITION_SOURCE)`                                                                              | —                               | 构建来源码→展示文案（AppService 内一次加载）                                             |
| ④   | Asm  | 对 ② `content` 逐行 `DoctorStationDiagnosisMapper#toConditionDefinitionRowResponse(model, sourceLabelMap)`                                                          | ②③                              | 装配行（含 `diagnosisKind`/`ybFlagText` 等）                                     |
| ⑤   | Asm  | `DoctorStationDiagnosisMapper#toConditionDefinitionPageResponse(Page<DoctorStationConditionDefinitionRowModel>, records)`                                        | ②, ④                            | 装配分页 Response                                                            |


---

### 3.5 IF-DOR-08 `saveDoctorDiagnosis`（医生保存诊断）


| 项        | 内容                                                         |
| -------- | ---------------------------------------------------------- |
| **App**  | `DoctorStationDiagnosisAppService#saveDoctorDiagnosis`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/saveDoctorDiagnosis` |


#### Request 字段表（SaveEncounterDiagnosisRequest）


| 字段                 | 类型   | 必填  | 说明                   |
| ------------------ | ---- | --- | -------------------- |
| encounterId        | Long | 是   | 就诊标识                 |
| patientId          | Long | 是   | 患者标识                 |
| diagnosisChildList | List | 是   | 诊断批次列表（完整列表，含新增与已存在） |


#### Request DTO 字段表（SaveEncounterDiagnosisChildRequest）


| 字段                     | 类型      | 必填  | 说明                       |
| ---------------------- | ------- | --- | ------------------------ |
| conditionId            | Long    | 否   | 诊断事实标识；**空=新增，非空=更新**    |
| encounterDiagnosisId   | Long    | 否   | 就诊诊断关联标识；**空=新增，非空=更新**  |
| conditionDefinitionId  | Long    | 是   | 诊断定义标识                   |
| conditionCode          | String  | 是   | 诊断编码                     |
| conditionDefinitionName | String  | 是   | 诊断定义名称                   |
| verificationStatusEnum | Integer | 否   | 验证状态，默认 CONFIRMED        |
| ybNo                   | String  | 否   | 医保编码                     |
| mainDiagnosisFlag      | Integer | 否   | 主诊标记：1=主诊断，默认 0          |
| diagnosisSortNo        | Integer | 否   | 排序号                      |
| medicalTypeCode        | String  | 否   | 医保医疗类别（`YbMedType` code）；**空则服务端默认 `11` 普通门诊** |
| diagnosisDesc          | String  | 否   | 诊断描述                     |
| iptDiseaseTypeCode     | String  | 否   | **忽略**；服务端固定 `2`（门诊诊断，**BR-OP-DOD-011**） |
| categoryCode           | String  | 否   | 诊断分类字典码（`category_enum`） |
| severityCode           | String  | 否   | 严重程度字典码（`severity_enum`） |


#### Response 字段表


| 字段                | 类型      | 说明           |
| ----------------- | ------- | ------------ |
| savedCount        | Integer | 保存成功条数       |
| savedConditionIds | List    | 保存后的诊断事实标识列表 |


#### 关键校验

- diagnosisChildList 非空，空列表 Gate 失败
- 每个子项 conditionDefinitionId/conditionCode/conditionDefinitionName 必填
- 主诊唯一性：批次中最多一个 mainDiagnosisFlag=1
- 排序号唯一性：批次中 diagnosisSortNo 不重复

#### 失败码口径表


| 失败码        | 场景       | 用户提示       | 实现 `ErrorCode`（见 §1.2.2）                                                          |
| ---------- | -------- | ---------- | --------------------------------------------------------------------------------- |
| EX-DOD-104 | 未选择患者或就诊 | 请先选择患者     | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` / `ENCOUNTER_INVALID_STATUS` |
| EX-DOD-105 | 保存持久化失败  | 稍后重试或联系管理员 | `ENCOUNTER_RULE_CONFLICT`（非 `BusinessException` 包装）                               |
| EX-VAL-001 | 请求参数非法   | 诊断批次不能为空   | `CONDITION_INVALID_ARGUMENT`                                                      |
| EX-VAL-002 | 请求参数非法   | 主诊标记只能有一个  | `CONDITION_INVALID_ARGUMENT`                                                      |
| EX-VAL-003 | 请求参数非法   | 排序号不能重复    | `CONDITION_INVALID_ARGUMENT`                                                      |


#### 领域编排序列（严格顺序）


| 序号  | 阶段    | 调用                                                                                                                                                                                       | 输入要点                | 说明                                                   |
| --- | ----- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- | ---------------------------------------------------- |
| ①   | Gate  | `DoctorStationDiagnosisMapper#validateSaveEncounterDiagnosisRequest(SaveEncounterDiagnosisRequest)`                                                                                      | Request             | 批次非空、子项必填、主诊唯一、排序唯一；失败抛 `CONDITION_INVALID_ARGUMENT` |
| ②   | Pre   | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)`                                                                                                           | Request.encounterId | 加载门诊QueryService快照                                            |
| ③   | Pre   | `DoctorStationDiagnosisMapper#validateEncounterPatientGate(snapshot, requestPatientId)`                                                                                                  | Request.patientId   | 防串患者                                                 |
| ④   | Pre   | AppService 校验 `classEnum` 为 `AMB`                                                                                                                                               | ②                   | 非门诊门诊抛 `ENCOUNTER_INVALID_STATUS`                 |
| ⑤   | Gate  | `CurrentOperatorResolver#requireOperatorId(...)`                                                                                                                     | —                   | 未登录抛 `ENCOUNTER_INVALID_ARGUMENT`                    |
| ⑥   | Cmd-1 | `DoctorStationDiagnosisMapper#toSaveDoctorDiagnosisCommand(request, operatorId)` → `ConditionDomainService#saveDoctorDiagnosis(SaveDoctorDiagnosisCommand): List<Long>`                  | Request, ⑤          | 批量写诊断事实，返回与请求行顺序一致的 `conditionId` 列表                 |
| ⑦   | Cmd-2 | `DoctorStationDiagnosisMapper#toBatchUpsertEncounterDiagnosisAssociationCommand(request, savedIds, operatorId)` → `EncounterDomainService#batchUpsertEncounterDiagnosisAssociation(...)` | Request, ⑥          | `medType` 空则 `11`（**BR-OP-DOD-010**）；`diagTypeCode` 固定 `2` 门诊诊断（**BR-OP-DOD-011**） |
| ⑧   | Asm   | `DoctorStationDiagnosisMapper#toSaveEncounterDiagnosisResponse(int savedCount, List<Long> savedConditionIds)`                                                                            | ⑥                   | 装配 `savedCount` / `savedConditionIds`                |


**说明**：新增/更新分支由 `SaveDoctorDiagnosisCommand` 与 `BatchUpsertEncounterDiagnosisAssociationCommand` 在领域层解释 `conditionId` / `encounterDiagnosisId` 是否为空，应用层不直接装配 `List<Condition>` 实体列表。

#### Mapper 接口方法签名（与 `DoctorStationDiagnosisMapper` 一致）

```java
void validateSaveEncounterDiagnosisRequest(SaveEncounterDiagnosisRequest request);
SaveDoctorDiagnosisCommand toSaveDoctorDiagnosisCommand(SaveEncounterDiagnosisRequest request, Long operatorId);
BatchUpsertEncounterDiagnosisAssociationCommand toBatchUpsertEncounterDiagnosisAssociationCommand(
    SaveEncounterDiagnosisRequest request, List<Long> savedConditionIds, Long operatorId);
SaveEncounterDiagnosisResponse toSaveEncounterDiagnosisResponse(int savedCount, List<Long> savedConditionIds);
```

---

### 3.6 IF-DOR-09 `delEncounterDiagnosis`（删除就诊诊断信息）


| 项        | 内容                                                           |
| -------- | ------------------------------------------------------------ |
| **App**  | `DoctorStationDiagnosisAppService#delEncounterDiagnosis`     |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/delEncounterDiagnosis` |


#### Request 字段表（JSON 请求体，与 `DeleteEncounterDiagnosisRequest` 一致）


| 字段          | 类型   | 必填  | 说明                   |
| ----------- | ---- | --- | -------------------- |
| conditionId | Long | 是   | 待删除诊断事实标识            |
| encounterId | Long | 是   | 就诊标识；与QueryService门控、关联移除命令一致 |


#### Response 字段表


| 字段      | 类型      | 说明     |
| ------- | ------- | ------ |
| success | Boolean | 删除成功标记 |
| message | String  | 操作结果提示 |


#### 关键校验

- conditionId 对应的诊断下**不存在已开立医嘱**，否则 Pre 失败 EX-DOD-102

#### 失败码口径表


| 失败码        | 场景       | 用户提示          | 实现 `ErrorCode`                                      |
| ---------- | -------- | ------------- | --------------------------------------------------- |
| EX-DOD-102 | 诊断已有关联医嘱 | 先撤销或调整相关医嘱后再删 | `CONDITION_RULE_CONFLICT`                           |
| EX-DOD-105 | 删除持久化失败  | 稍后重试或联系管理员    | `ENCOUNTER_RULE_CONFLICT`（非 `BusinessException` 包装） |
| EX-DOD-104 | 未选择患者或就诊 | 请先选择患者        | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_STATUS`  |


#### 领域编排序列（严格顺序）


| 序号  | 阶段    | 调用                                                                                                                                                                       | 输入要点                | 说明                                                                        |
| --- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------- | ------------------------------------------------------------------------- |
| ①   | Gate  | `DoctorStationDiagnosisMapper#validateDeleteEncounterDiagnosisRequest(conditionId, encounterId)`                                                                         | Request             | 参数必填                                                                      |
| ②   | Pre   | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)`                                                                                           | Request.encounterId | QueryService门控                                                                     |
| ③   | Pre   | AppService 校验 `classEnum` 为 `AMB`                                                                                                                               | ②                   | 否则 `ENCOUNTER_INVALID_STATUS`                                             |
| ④   | Pre   | `ConditionQueryService#countAdviceRefByConditionId(conditionId): int`                                                                                                      | Request.conditionId | **>0** 抛 `CONDITION_RULE_CONFLICT`（EX-DOD-102）；若仓储为占位恒 0，则门控可能失效，见 §1.2.2 |
| ⑤   | Gate  | `CurrentOperatorResolver#requireOperatorId(...)`                                                                                                                   | —                   | 未登录抛 `ENCOUNTER_INVALID_ARGUMENT`                                         |
| ⑥   | Cmd-1 | 组装 `RemoveEncounterDiagnosisAssociationCommand`（含 `encounterId`、`conditionId`、`operatorId`、`removeReason`）→ `EncounterDomainService#removeEncounterDiagnosisAssociation` | Request, ⑤          | 先移除就诊诊断关联                                                                 |
| ⑦   | Cmd-2 | 组装 `DelEncounterDiagnosisCommand` → `ConditionDomainService#delEncounterDiagnosis`                                                                                       | Request, ⑤          | 再软删诊断事实                                                                   |
| ⑧   | Asm   | `DoctorStationDiagnosisMapper#toDeleteEncounterDiagnosisResponse(boolean success, String message)`                                                                       | 成功或幂等成功路径           | 捕获 `CONDITION_NOT_FOUND` / `ENCOUNTER_NOT_FOUND` 视为删除成功（幂等）               |


#### Mapper 接口方法签名

```java
void validateDeleteEncounterDiagnosisRequest(Long conditionId, Long encounterId);
DeleteEncounterDiagnosisResponse toDeleteEncounterDiagnosisResponse(boolean success, String message);
```

---

### 3.7 IF-DOR-09A `switchMainEncounterDiagnosis`（切换就诊主诊断）


| 项        | 内容                                                                   |
| -------- | -------------------------------------------------------------------- |
| **App**  | `DoctorStationDiagnosisAppService#switchMainEncounterDiagnosis`     |
| **HTTP** | 待挂路由；与现诊断 Controller 同前缀：`POST /api/app/doctorStationDiagnosis/switchMainEncounterDiagnosis` |


#### Request 字段表（`SwitchMainEncounterDiagnosisRequest`）


| 字段                   | 类型   | 必填  | 说明                          |
| -------------------- | ---- | --- | --------------------------- |
| encounterId          | Long | 是   | 就诊标识                        |
| encounterDiagnosisId | Long | 是   | 拟设为主诊的就诊诊断行标识               |
| patientId            | Long | 否   | 患者标识；非空时与门诊快照交叉校验，防串患者 |


#### Response 字段表（`SwitchMainEncounterDiagnosisResponse`）


| 字段      | 类型      | 说明     |
| ------- | ------- | ------ |
| success | Boolean | 切换成功标记 |
| message | String  | 操作结果提示 |


#### 关键校验

- 仅门诊 `AMB` 就诊可切换；非门诊 → `ENCOUNTER_INVALID_STATUS`
- 目标行须属于本就诊且未删；领域层保证切换后主诊唯一（**BR-DOD-015** / **BR-ENC-014**）

#### 失败码口径表


| 失败码        | 场景           | 用户提示       | 实现 `ErrorCode`（见 §1.2.2）                                                          |
| ---------- | ------------ | ---------- | --------------------------------------------------------------------------------- |
| EX-DOD-104 | 未选择患者或就诊 / 参数缺失 | 请先选择患者     | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` / `CONDITION_INVALID_ARGUMENT` |
| EX-DOD-105 | 切换持久化失败      | 稍后重试或联系管理员 | `ENCOUNTER_RULE_CONFLICT`（非 `BusinessException` 包装）                               |


#### 领域编排序列（严格顺序）


| 序号  | 阶段  | 调用                                                                                                                      | 输入要点                | 说明                                                  |
| --- | --- | ----------------------------------------------------------------------------------------------------------------------- | ------------------- | --------------------------------------------------- |
| ①   | Gate | AppService 校验 `encounterId` / `encounterDiagnosisId` 必填                                                                  | Request             | 缺失 → 参数类业务异常（语义 EX-DOD-104）                         |
| ②   | Pre  | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)`                                           | Request.encounterId | 加载门诊快照                                              |
| ③   | Pre  | 若 `patientId` 非空：`DoctorStationDiagnosisMapper#validateEncounterPatientGate`                                           | Request.patientId   | 防串患者                                                |
| ④   | Pre  | AppService 校验 `classEnum` 为 `AMB`                                                                                       | ②                   | 非门诊 → `ENCOUNTER_INVALID_STATUS`                    |
| ⑤   | Gate | `CurrentOperatorResolver#requireOperatorId(...)`                                                                        | —                   | 未登录 → `ENCOUNTER_INVALID_ARGUMENT`                  |
| ⑥   | Cmd  | 组装 `SwitchPrimaryEncounterDiagnosisCommand` → `EncounterDomainService#switchPrimaryEncounterDiagnosis`                  | Request, ⑤          | 西医单行 / 中医病证组置主；清空同就诊其余主诊；发 `EncounterDiagnosisAssociationUpsertedEvent` |
| ⑦   | Asm  | 装配 `SwitchMainEncounterDiagnosisResponse`（`success`/`message`）                                                          | 成功路径                | 目标已是唯一主诊且无写库时亦返回成功（幂等）                              |


---

## 4. QueryService 与事件刷新

### 4.1 QueryService查询语义


| QueryService / 只读来源                   | 查询服务或仓储方法                                                                                                  | 用于 IF                     | 查询结果用途                                                                  |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------- | ----------------------------------------------------------------------- |
| OutpatientEncounterContextQueryModel               | `EncounterContextReader#getOutpatientContextByEncounterId`（可选弱查询）                                       | IF-DOR-06                 | 提取诊断 JSON 快照（实现以 `mustGetOutpatientContextByEncounterId` 为主路径）                                     |
| OutpatientEncounterContextQueryModel               | `EncounterContextReader#mustGetOutpatientContextByEncounterId`                                          | IF-DOR-06/08/09           | 快照加载与写前门控                                                               |
| Condition（事实表）               | `PatientHistoryCommonDiagnosisAssembler#assemble` + `EncounterQueryService#listActiveAssociationsByConditionIds` | IF-DOR-06A                | 病人历史：西医按定义去重；中医按病证组合并后装配 `CommonDiagnosisRowDto`                                   |
| ConditionTemplate（聚合查询服务）    | `ConditionTemplateQueryService#listByOwnerAndType(ownerId, bindingCode)`                                   | IF-DOR-06A                | 医生常用 / 用户个人 / 科室模板；绑定码与 `ConditionTemplateBinding` 一致（科室为 `DEPARTMENT`） |
| ConditionDefinition（JPA 行模型） | `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions` / `listActiveDoctorStationRowsByIds`  | IF-DOR-07、IF-DOR-06A 定义展示 | 已发布目录分页与批量行装载                                                           |
| ConditionDefinition（匹配投影） | `ConditionDefinitionQueryService#listActiveDoctorStationRowsByOpspDiseCodes` | IF-DOR-06B | 门慢门特码→已启用目录 |
| 医保待遇扩展 | `InsuranceRegisterBenefitQueryExtension#isEnabled` / `#resolveOpspFilingEncounter` / `#queryOpspFiling` | IF-DOR-06B / IF-DOR-06C | CHS 门控与 5301 |
| ConditionDefinition（慢病补录） | `ConditionDefinitionDomainService#ensureActiveChronicSpecOpspDisease` | IF-DOR-06C | 仅幂等写 `chronic_spec` |


> **演进说明**：若后续引入 `ConditionTemplateQueryService` 专表QueryService，可在保持 HTTP 契约不变前提下替换 §3.3 中模板与历史装载的实现，本 SDD 以当前 `zxplat` 落地为准。

### 4.2 领域事件（读侧无投影）

> **2026-06**：读模型投影器已废止；事件 **不** 更新 `OutpatientEncounterContextQueryModel.diagnosis_json` 物化列。Loader enrichment / 诊断 Tab 均走 **`EncounterDiagnosisSnapshotAssembler#buildSnapshot`**（AppService 经 Mapper 解析，**禁止** API 透传）。

| 领域事件 | 读侧消费 |
| --- | --- |
| `EncounterDiagnosisSavedEvent` / `DeletedEvent` / `EncounterDiagnosisAssociationUpsertedEvent` / `RemovedEvent` | 无 QueryService 投影；App 再查或 Loader 再 enrichment |


---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（与 §3 序号对照）


| IF        | App 方法                  | Cmd 序号 | `DomainService#method`（命令形态）                                                                                       | 聚合写入顺序                   | 事务策略                                           |
| --------- | ----------------------- | ------ | ------------------------------------------------------------------------------------------------------------------ | ------------------------ | ---------------------------------------------- |
| IF-DOR-08 | `saveDoctorDiagnosis`   | Cmd-1  | `ConditionDomainService#saveDoctorDiagnosis(SaveDoctorDiagnosisCommand): List<Long>`                               | 先写 Condition 聚合          | **应用层无 `@Transactional`**；是否同一物理事务由领域/基础设施实现决定 |
| IF-DOR-08 | `saveDoctorDiagnosis`   | Cmd-2  | `EncounterDomainService#batchUpsertEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand)` | 后写 Encounter 聚合（子实体）     | 同上                                             |
| IF-DOR-09 | `delEncounterDiagnosis` | Cmd-1  | `EncounterDomainService#removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand)`           | 先删 EncounterDiagnosis 关联 | 同上                                             |
| IF-DOR-09 | `delEncounterDiagnosis` | Cmd-2  | `ConditionDomainService#delEncounterDiagnosis(DelEncounterDiagnosisCommand)`                                       | 后删 Condition 事实          | 同上                                             |
| IF-DOR-09A | `switchMainEncounterDiagnosis` | Cmd-1  | `EncounterDomainService#switchPrimaryEncounterDiagnosis(SwitchPrimaryEncounterDiagnosisCommand)`                   | 仅改 EncounterDiagnosis 主诊标记 | 同上                                             |


### 5.2 事务边界与失败回滚


| 接口               | 事务边界                                                           | 失败回滚策略                                                                              | 说明                              |
| ---------------- | -------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------- |
| IF-DOR-08        | **不在** `DoctorStationDiagnosisAppService` 上声明 `@Transactional` | 期望由领域服务或持久化层保证 Cmd-1/Cmd-2 原子性；若仅 Cmd-2 失败，可能出现「事实已写、关联未齐」的中间态，需在领域层补救或接受与运维约定一致的风险 | 与《代码生成通用规范》应用层禁事务一致；与 §1.2 口径一致 |
| IF-DOR-09        | **不在** AppService 声明 `@Transactional`                          | 同左；删除路径捕获「记录不存在」`BusinessException` 视为幂等成功                                          | Cmd 顺序：先关联后事实                   |
| IF-DOR-09A        | **不在** AppService 声明 `@Transactional`                          | 领域内单聚合主诊切换；目标已为主诊可无写库直接成功                                                          | 与 IF-DOR-08 改主诊并存               |
| IF-DOR-06/06A/06B/07 | 无事务（只读）                                                        | —                                                                                   | 纯读接口；06B 平台失败不阻断                            |


### 5.3 幂等落点


| 接口        | 幂等策略                                            | 代码位置                                                        | 关联步骤        |
| --------- | ----------------------------------------------- | ----------------------------------------------------------- | ----------- |
| IF-DOR-08 | 数据库唯一约束（`encounter_id` + `condition_definition_id`）+ 行级乐观锁（若存在） | `EncounterDiagnosis` 表唯一索引                                  | §3.5 步骤 ⑦   |
| IF-DOR-08 | 批次重复提交依赖行 ID 区分：新增行无 ID 可能重复插入，更新行有 ID 幂等       | 业务层校验                                                       | §3.5 步骤 ①   |
| IF-DOR-09 | 删除操作幂等（已删除再删返回成功）                               | AppService 捕获 `CONDITION_NOT_FOUND` / `ENCOUNTER_NOT_FOUND` | §3.6 步骤 ⑥⑦⑧ |


---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 场景       | 承诺                              | 实现方式              | 关联步骤        |
| -------- | ------------------------------- | ----------------- | ----------- |
| 诊断保存重复提交 | 同一批次短时重复提交，依赖行 ID 与数据库约束呈现可理解结果 | 数据库唯一索引 + 业务异常包装  | §3.5 步骤 ①⑦  |
| 诊断删除重复提交 | 幂等：已删除再删返回成功                    | 捕获「记录不存在」异常转为成功语义 | §3.6 步骤 ⑥⑦⑧ |


### 6.2 并发控制


| 场景            | 承诺                          | 实现方式                        | 关联步骤      |
| ------------- | --------------------------- | --------------------------- | --------- |
| 双终端同时编辑同一就诊诊断 | 以后写者为准或返回冲突，由实现选型并在代码注释固化   | 乐观锁（version 字段）或数据库唯一约束冲突异常 | §3.5 步骤 ⑦ |
| 删除与保存并发       | 删除操作若发现诊断已被另一终端修改，返回可理解冲突提示 | 乐观锁版本校验                     | §3.5/§3.6 |


### 6.3 重试与容错


| 场景         | 承诺                                 | 实现方式                   | 关联步骤               |
| ---------- | ---------------------------------- | ---------------------- | ------------------ |
| 数据库瞬时失败    | 可重试异常（如连接超时）触发框架重试；业务异常（如约束冲突）不重试  | Spring Retry 或框架默认重试策略 | 所有 Cmd 步骤          |
| 下游QueryService暂时不可用 | 纯读接口降级提示；写接口不强依赖QueryService（Pre 加载失败抛业务异常） | 业务异常 EX-DOD-101/104    | §3.2/§3.3/§3.3A/§3.4 Pre |
| 5301 平台失败 | 模块已启用时返回空备案列表，不阻断诊断 Tab | warn + enabled=true + 空列表 | §3.3A 步骤 ⑥ |


### 6.4 数据脱敏


| 场景     | 承诺                   | 实现方式                  |
| ------ | -------------------- | --------------------- |
| 患者姓名展示 | 按需脱敏（如「张*三」）         | 通用脱敏工具类，在 Asm 步骤装配时处理 |
| 证件号展示  | 按需脱敏（如「110****1990」） | 同上                    |


### 6.5 审计日志


| 场景     | 承诺                  | 实现方式                                                                       | 关联步骤      |
| ------ | ------------------- | -------------------------------------------------------------------------- | --------- |
| 诊断保存审计 | 记录诊断记录时刻、记录人、主诊标记变更 | `Condition.recordedDatetime`、`Condition.recorderId` 由 Domain 写入；应用层日志记录操作人 | §3.5 步骤 ⑥ |
| 诊断删除审计 | 记录删除操作人与时间          | 应用层审计日志注解或手动记录                                                             | §3.6 步骤 ⑦ |


---

## 7. 测试要点

### 7.1 功能测试


| 测试项                                | 自动化归属 | 优先级 |
| ---------------------------------- | ----- | --- |
| IF-DOR-06 空就诊诊断列表查询                | 单元测试  | P0  |
| IF-DOR-06 有数据就诊诊断列表查询              | 单元测试  | P0  |
| IF-DOR-06A 四类常用诊断并行查询              | 单元测试  | P0  |
| IF-DOR-06A 任一类为空时展示                | 单元测试  | P1  |
| IF-DOR-06B CHS 未启用返回 enabled=false 且隐藏入口 | 单元测试  | P0  |
| IF-DOR-06B 有备案且映射成功可勾选           | 集成测试  | P0  |
| IF-DOR-06B 未映射行列表可展示但不可直接写入（须走 06C） | 单元测试  | P0  |
| IF-DOR-06C 未匹配点击幂等补录 chronic_spec 后可导入 | 集成测试  | P0  |
| IF-DOR-06C 歧义/过期拒绝写目录 | 单元测试  | P0  |
| IF-DOR-06B 5301 失败弱提示空列表          | 集成测试  | P1  |
| IF-DOR-07 西医/中医/不过滤三种检索            | 单元测试  | P0  |
| IF-DOR-07 关键字匹配 name/py_str/wb_str | 单元测试  | P0  |
| IF-DOR-08 全量替换：新增 + 更新混合批次         | 集成测试  | P0  |
| IF-DOR-08 全量替换：不在批次中的历史诊断需显式删除     | 集成测试  | P0  |
| IF-DOR-08 主诊唯一性校验                  | 单元测试  | P0  |
| IF-DOR-08 排序号唯一性校验                 | 单元测试  | P1  |
| IF-DOR-09 删除成功（无医嘱）                | 集成测试  | P0  |
| IF-DOR-09 删除失败（有医嘱）                | 集成测试  | P0  |
| IF-DOR-09A 西医单行切换主诊                  | 集成测试  | P0  |
| IF-DOR-09A 中医病证组整组切换主诊              | 集成测试  | P0  |
| IF-DOR-09A 与 IF-DOR-08 改主诊并存（不必整批保存） | 单元测试  | P1  |


### 7.2 非功能测试


| 测试项                                               | 自动化归属 | 优先级 |
| ------------------------------------------------- | ----- | --- |
| IF-DOR-06 性能 ≤800ms（95 分位）                        | 性能测试  | P1  |
| IF-DOR-06A 性能 ≤1200ms（四类聚合查询）                     | 性能测试  | P1  |
| IF-DOR-08 跨聚合失败数据一致性（Cmd-1 成功 Cmd-2 失败时的中间态与领域行为） | 集成测试  | P0  |
| IF-DOR-08 并发保存冲突处理                                | 并发测试  | P1  |
| IF-DOR-09 删除失败与幂等语义验证                             | 集成测试  | P0  |


### 7.3 QueryService一致性


| 测试项                                      | 自动化归属 | 优先级 |
| ---------------------------------------- | ----- | --- |
| 诊断保存后 OutpatientEncounterContextQueryModel.diagnosis_json 投影更新 | 集成测试  | P0  |
| 诊断删除后 OutpatientEncounterContextQueryModel.diagnosis_json 投影更新 | 集成测试  | P0  |
| QueryService 最终一致性延迟可接受（<2s）                        | 性能测试  | P1  |


### 7.4 集成测试


| 测试项                           | 自动化归属 | 优先级 |
| ----------------------------- | ----- | --- |
| 医生站诊断录入端到端流程（查询→选择→保存→刷新列表）   | 端到端测试 | P0  |
| 与 Encounter 聚合的集成（保存后就诊状态不变）  | 集成测试  | P1  |
| 与 Condition 聚合的集成（诊断事实独立生命周期） | 集成测试  | P1  |


### 7.5 测试缺口跟踪


| 缺口 ID | 缺口描述 | 计划关闭时间 |
| ----- | ---- | ------ |
| 无     | —    | —      |


---

## 8. 变更历史


| 版本   | 日期         | 变更摘要                                                                                                                                                                                                                                        |
| ---- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| v1.11 | 2026-09-09 | **IF-DOR-06A** 增 `medicalTypeOptions`（YbMedType 门诊向）；对齐 **AS v2.3 / BR-OP-DOD-016** |
| v1.10 | 2026-08-01 | 新增 **IF-DOR-06C** `ensureOpspFilingDiagnosis`：未匹配慢病备案点击时核对 5301 后幂等补录/启用 `chronic_spec`（不改其它来源）；修订 BR-DOD-013；就诊诊断写入仍走 IF-DOR-08 |
| v1.9 | 2026-07-26 | 新增 **IF-DOR-09A** `switchMainEncounterDiagnosis` → `EncounterDomainService#switchPrimaryEncounterDiagnosis`；HTTP 待挂路由（同前缀）；写路径 Gate/Pre/Cmd/Asm；与 IF-DOR-08 改主诊并存 |
| v1.8 | 2026-07-18 | 新增 **IF-DOR-06B** `getPatientOpspFilingDiagnosis`：CHS 门控、`resolveOpspFilingEncounter`+5301、`listActiveDoctorStationRowsByOpspDiseCodes` 映射；前端「慢病备案」仅 enabled 时展示；写入复用 IF-DOR-08 |
| v1.7 | 2026-06-16 | 对齐 `zxplat`：IF-DOR-06A 病人历史改 `PatientHistoryCommonDiagnosisAssembler`（中医病证组合并）；IF-DOR-06 快照增 `tcmSyndromeGroup`/`sourceEnum`；IF-DOR-07 source 过滤改 V58 术语码（`tcm_dx` 等）；`EncounterDomainService` 中医批次组级主诊/rank 与软删关联复活 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：`EncounterContextFacade#mustGetOutpatientContextByEncounterId`；§4 QueryService 口径与 `zxplat` 一致 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.6 | 2026-05-21 | 应用层 **`ConditionRepository`** 收敛至 **`ConditionQueryService#findByPatientId` / `#countAdviceRefByConditionId`**（对齐 **B.2**）；§1.1、§1.2.2、§2.2、§3.3/§3.6、§4.1 同步 |
| v1.5 | 2026-05-10 | 与 `zxplat` 对齐：HTTP JSON / 快照 / 事件载荷统一 **`conditionDefinitionId`/`conditionDefinitionName`**；挂号QueryService 查询列 **`condition_definition_name`**；DTO 中西医展示字段为 **`diagnosisKind`/`diagnosisKindText`**；§5.3 唯一约束表述改为写表列名；与 `EncounterQueryService（DM-ADM-002 §6.2）` v3.6、`EncounterQueryService#pageClinicRegister` v2.10、`DM-ADM-002 §6.2 EncounterQueryService` v1.11 一致 |
| v1.4 | 2026-05-03 | 新增 §1.2.1「诊断读快照与领域事件载荷」：`EncounterDomainService` 在关联落库后拼装 `diagnosisJson`/`conditionName` 写入 `EncounterDiagnosisAssociationUpsertedEvent` / `EncounterDiagnosisAssociationRemovedEvent`，领域事件 SET `OutpatientEncounterContextQueryModel.diagnosis_json` 等；语义码↔`ErrorCode` 对照顺延为 **§1.2.2**；与 `EncounterQueryService（DM-ADM-002 §6.2）` v3.1、`DM-ADM-002 §6.2 EncounterQueryService` v1.8 一致 |
| v1.3 | 2026-05-02 | 与 `zxplat` 诊断录入应用层实现对齐：§1.2.1 语义码↔`ErrorCode`、§2.1～§2.3、§3.2～§3.6 编排序列与 Mapper 签名、§4.1 读路径、§5 App 方法名与**应用层不声明 `@Transactional`**、修正 §3 小节表头双竖线；§1.1 编排权威 AS 版本更新为 **v1.6+**；§1.1 方法权威锚点同步 `DM-CLN-002` §6.2.6～6.2.7、`DM-CLN-003` §6.4 |
| v1.2 | 2026-05-02 | HTTP 方法修正：工程动态路由统一使用 `POST`，无 GET/DELETE；与 AS-OP-DOR-001-SDD 格式一致                                                                                                                                                                           |
| v1.1 | 2026-05-02 | 格式对齐 AS-OP-DOR-001-SDD：§1.1/§1.2 表格列头统一为「项                                                                                                                                                                                                  |
| v1.0 | 2026-05-02 | 初版：基于 AS-OP-DOR-002 v1.4 与 DM-ADM-002 / DM-CLN-002 / DM-CLN-003 / DM-CUS-003 生成；IF-DOR-06/06A/07/08/09 全量展开；§2.3 枚举/字典双锚点；§3.5/§3.6 领域编排序列含 Cmd 括号内 Command 类名与写库点标注；§5 事务边界与回滚策略；§7 测试矩阵                                                   |


---

**文档负责人**：（待填） · **评审**：（待填）