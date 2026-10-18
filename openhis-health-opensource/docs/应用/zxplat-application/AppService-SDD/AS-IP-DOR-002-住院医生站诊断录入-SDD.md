# 住院医生站 · 诊断录入 · 应用服务详细设计（SDD）

> **编码**：AS-IP-DOR-002-SDD  
> **版本**：v1.5 | **日期**：2026-10-12 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院医生站 · 诊断录入（`inpatient/doctorstation/diagnosis`） |
| **边界** | 就诊诊断列表、四类常用诊断、诊断定义检索、诊断批量保存、诊断删除、**单点切换主诊**；住院扩展 **`iptDiseaseTypeCode` → `adm_encounter_diagnosis.diag_type_code`** |
| **编排权威** | `AS-IP-DOR-002-住院医生站诊断录入.md`（v1.2+）§2、§4 |
| **方法权威** | `DM-ADM-002` §6.1.9b、§6.1.11～6.1.13；`DM-CLN-003` §6.1.1～6.1.3；`DM-CLN-002` §6.2.6～6.2.7；`DM-CUS-003` §6.2 |
| **严格对齐** | IF 编号 **IF-IP-DOR-02～07**，不得增删改号 |
| **实现阶段口径** | **目标态全量契约**；领域写能力与 **InpatientEncounterContextQueryModel** EncounterQueryService 诊断 JSON 刷新**已落地**；住院 AppService **`InpatientDoctorStationDiagnosisAppService` 已生成** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **与门诊同构** | IF-IP-DOR-02～07 分别对应 **AS-OP-DOR-002** IF-DOR-06/06A/07/08/09/**09A**；HTTP 方法名**保持一致**（`getEncounterDiagnosis`、`switchMainEncounterDiagnosis` 等） |
| **QueryService口径** | 读/写前门控：`EncounterContextReader#mustGetInpatientContextByEncounterId`；诊断 Tab **`EncounterDiagnosisSnapshotAssembler#buildSnapshot`** → Mapper **`parseDiagnosisJson`**（实时扫表，**禁止**解析 QueryModel 内 `diagnosisJson` 作为 IF-IP-DOR-02 真源） |
| **写前门控** | `class_enum=IMP`；`validateInpatientEncounterWritableGate` **仅校验快照非 null**（**BR-IP-DOD-007** 2026-08-13：**不**再要求 `IN_PROGRESS`/`ON_HOLD`；允许出院后补诊断）。**医嘱**可写态门控仍见 **AS-IP-DOR-003** |
| **事务口径** | IF-IP-DOR-05：先 `ConditionDomainService#saveDoctorDiagnosis` 后 `EncounterDomainService#batchUpsertEncounterDiagnosisAssociation`；IF-IP-DOR-06：先 `removeEncounterDiagnosisAssociation` 后 `delEncounterDiagnosis`；**应用层不声明 `@Transactional`** |
| **HTTP 接入口径** | `POST /api/app/inpatientDoctorStationDiagnosis/{methodName}` |
| **契约归属** | Request/Response/DTO 与 `InpatientDoctorStationDiagnosisMapper` **均位于** `inpatient/doctorstation/diagnosis`；与门诊 **同构** 但 **禁止跨包引用** 门诊诊断模块 |

#### 1.2.1 诊断读快照与领域事件载荷（IF-IP-DOR-02 / 05 / 06）

- **IF-IP-DOR-02**：`EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId).diagnosisJson()` → `InpatientDoctorStationDiagnosisMapper#parseDiagnosisJson` → `#toEncounterDiagnosisResponse`（**B.6.2**；与 **AS-OP-DOR-002-SDD** §1.2.1 同构）。
- **IF-IP-DOR-05 / 06**：写路径 `EncounterDomainService` 发布关联类事件时 **可选** 携带 `diagnosisJson` 载荷（审计/集成）；**无**读模型投影器消费 **`SET diagnosis_json`**。列表/登记等场景的 `InpatientEncounterContextQueryModel.diagnosisJson` 由 **`InpatientEncounterContextLoader`** 批量 enrichment 时 **`buildSnapshot`** 写入（Loader 内部字段，**禁止** API 透传）。

#### 1.2.2 文档语义码与 `ErrorCodeConstants` 对照

| 文档语义码 | 实现 `ErrorCode`（典型触发） |
| --- | --- |
| EX-IP-DOD-104 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT`（未选就诊、患者不一致、非 IMP、状态不可写） |
| EX-IP-DOD-101 | `ENCOUNTER_INVALID_ARGUMENT` 或检索失败统一口径 |
| EX-IP-DOD-106 | `CONDITION_INVALID_ARGUMENT`（主诊多于一个） |
| EX-IP-DOD-107 | `CONDITION_INVALID_ARGUMENT`（`iptDiseaseTypeCode` 非法） |
| EX-IP-DOD-108 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT`（快照缺失、非 IMP） |
| EX-IP-DOD-105 | `ENCOUNTER_RULE_CONFLICT`（持久化失败） |
| EX-IP-DOD-102 | `CONDITION_RULE_CONFLICT`（医嘱引用 >0） |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/doctorstation/
  diagnosis/
    service/
      InpatientDoctorStationDiagnosisAppService.java
    mapper/
      InpatientDoctorStationDiagnosisMapper.java
      InpatientDoctorStationDiagnosisMapperImpl.java
    request/
      GetEncounterDiagnosisRequest, GetCommonDiagnosesRequest,
      PageConditionDefinitionRequest, SaveEncounterDiagnosisRequest,
      SaveEncounterDiagnosisChildRequest, DeleteEncounterDiagnosisRequest
    response/
      EncounterDiagnosisResponse, CommonDiagnosesResponse,
      ConditionDefinitionPageResponse, ConditionDefinitionRowResponse,
      SaveEncounterDiagnosisResponse, DeleteEncounterDiagnosisResponse
    dto/
      EncounterDiagnosisRowDto, CommonDiagnosisRowDto
```

> 与门诊 `outpatient/doctorstation/diagnosis` **字段同构**；实现为住院包 **独立副本**，应用层 **不得** `import 门诊诊断包。

> HTTP 前缀：`inpatientDoctorStationDiagnosis`（与 `InpatientDoctorStationDiagnosisAppService` 类名推导一致）

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 来源 |
| --- | --- | --- | --- |
| 应用服务 | `InpatientDoctorStationDiagnosisAppService` | 编排 IF-IP-DOR-02～07 | 本模块 |
| Facade | `EncounterContextReader` | `mustGetInpatientContextByEncounterId`（写前/删前门控） | `application.shared.encounter` |
| Bridge 装配 | `EncounterDiagnosisSnapshotAssembler` | `buildSnapshot(encounterId)` → 内部 `diagnosisJson`；IF-IP-DOR-02 经 Mapper 结构化（**B.6.2**） | `application.shared.diagnosis` |
| QueryService查询 | `EncounterQueryService` | 薄就诊存在性等（与 Facade 分工见 §1.2） | domain.administration.encounter |
| 领域服务 | `ConditionDomainService` | 诊断事实写 | domain.clinical.condition |
| 领域服务 | `EncounterDomainService` | 就诊诊断关联写 | domain.administration.encounter |
| 查询服务 | `ConditionDefinitionQueryService` | 定义分页/批量 | domain.clinical.conditiondefinition |
| 查询服务 | `ConditionTemplateQueryService` | 常用模板 | domain.custom.conditiontemplate |
| 查询服务 | `ConditionQueryService` | 病人历史（`findByPatientId`）、医嘱引用计数（`countAdviceRefByConditionId`） | domain.clinical.condition |
| 查询服务 | `CodeSystemQueryService` | 术语下拉 | domain.terminology |
| 操作人/范围 | `CurrentOperatorResolver` / `CurrentManagedOrganizationIdsResolver` / `CurrentPractitionerResolver` | 写路径 `operatorId`；常用树维度（**B.6.5**） | `module.common.validator` |
| Mapper | `InpatientDoctorStationDiagnosisMapper` | JSON/Command/Response 装配 + 住院患者/状态/类型码门控；JSON 解析经 `DiagnosisJsonSnapshotParser#parseStrict` | 本模块 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | `EncounterClass` | `DM-ADM-002` §3 `class_enum` | 门控 **IMP** |
| 就诊状态 | `EncounterStatus` | `DM-ADM-002` §2.3.2 | 诊断写路径**不**按状态拦截；列表/患者可见性仍用 IMP 展示文案 |
| 患者疾病诊断类型 | `com.zxplat.domain.share.enums.IptPatientDiseaseDiagType` | `DM-ADM-002` §3.2 `adm_encounter_diagnosis.diag_type_code` | `SaveEncounterDiagnosisChildRequest.iptDiseaseTypeCode`；**可空** |
| 医保医疗类别 | `com.zxplat.domain.share.enums.YbMedType` | `DM-ADM-002` §3.2 `adm_encounter_diagnosis.med_type` | `medicalTypeCode`；空则 `GENERAL_OUTPATIENT`（`11`） |
| 主诊标记 | `DoctorStationDiagnosisConstants.MAIN_DIAGNOSIS_FLAG_YES` | `DM-ADM-002` 就诊诊断主诊 | `mainDiagnosisFlag`；**至多一个** |
| 诊断验证状态 | `SaveEncounterDiagnosisChildRequest.verificationStatusEnum` → 命令 String | `DM-CLN-003` §2.3.1 | `InpatientDoctorStationDiagnosisMapperImpl#resolveVerificationCode` |
| 诊断分类/严重程度 | `categoryCode` / `severityCode` | `DM-CLN-003` §3.1 | IF-IP-DOR-05 |
| 中西医过滤 | `PageConditionDefinitionRequest.medicineTypeCode` | `DM-CLN-002` §4.1 | IF-IP-DOR-04 |
| 模板绑定 | `ConditionTemplateBinding` | `DM-CUS-003` | IF-IP-DOR-03 |
| 业务消息码 | `InpatientDoctorStationDiagnosisConstants.EX_IP_DOD_*` | `AS-IP-DOR-002` §5 | **目标态待生成** |

### 2.4 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterContextReader` | `mustGetInpatientContextByEncounterId` | IF-IP-DOR-02/05/06 |
| `ConditionDefinitionQueryService` | `pageActiveDoctorStationDefinitions` | IF-IP-DOR-04 |
| `ConditionDefinitionQueryService` | `listActiveDoctorStationRowsByIds` | IF-IP-DOR-03 |
| `ConditionTemplateQueryService` | `listByOwnerAndType` | IF-IP-DOR-03 |
| `ConditionQueryService` | `findByPatientId` | IF-IP-DOR-03 |
| `ConditionQueryService` | `countAdviceRefByConditionId` | IF-IP-DOR-06 |
| `CodeSystemQueryService` | `getOptionListByCodeSystemName` | IF-IP-DOR-04 |
| `ConditionDomainService` | `saveDoctorDiagnosis` | IF-IP-DOR-05 |
| `ConditionDomainService` | `delEncounterDiagnosis` | IF-IP-DOR-06 |
| `EncounterDomainService` | `batchUpsertEncounterDiagnosisAssociation` | IF-IP-DOR-05 |
| `EncounterDomainService` | `removeEncounterDiagnosisAssociation` | IF-IP-DOR-06 |
| `EncounterDomainService` | `switchPrimaryEncounterDiagnosis` | IF-IP-DOR-07 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-DOR-02 | `getEncounterDiagnosis` | 查询就诊诊断信息 | `POST /api/app/inpatientDoctorStationDiagnosis/getEncounterDiagnosis` |
| IF-IP-DOR-03 | `getConditionDefinitionBusinessClass` | 查询诊断定义业务分类数据 | `POST /api/app/inpatientDoctorStationDiagnosis/getConditionDefinitionBusinessClass` |
| IF-IP-DOR-04 | `getConditionDefinitionMetadata` | 查询诊断定义列表 | `POST /api/app/inpatientDoctorStationDiagnosis/getConditionDefinitionMetadata` |
| IF-IP-DOR-05 | `saveDoctorDiagnosis` | 医生保存诊断 | `POST /api/app/inpatientDoctorStationDiagnosis/saveDoctorDiagnosis` |
| IF-IP-DOR-06 | `delEncounterDiagnosis` | 删除就诊诊断信息 | `POST /api/app/inpatientDoctorStationDiagnosis/delEncounterDiagnosis` |
| IF-IP-DOR-07 | `switchMainEncounterDiagnosis` | 切换就诊主诊断 | 待挂路由；目标前缀同现诊断 Controller：`POST /api/app/inpatientDoctorStationDiagnosis/switchMainEncounterDiagnosis` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、批次、主诊唯一、类型码合法 | 禁止 Cmd |
| **Pre** | `InpatientEncounterContextQueryModel` 快照、IMP+状态、患者一致、医嘱引用 | 禁止 Cmd |
| **Cmd** | 领域写（顺序见 §5） | 见失败码 |
| **Asm** | `DoctorStationDiagnosisMapper` 装配 | — |

**住院专用 Pre（所有写/读列表 IF 在加载快照后执行）**

1. `InpatientDoctorStationDiagnosisMapper#validateInpatientEncounterPatientGate(InpatientEncounterContextQueryModel, patientId)`
2. `InpatientDoctorStationDiagnosisMapper#validateInpatientEncounterWritableGate(InpatientEncounterContextQueryModel)` — IMP + **快照非 null**（**不**校验 `IN_PROGRESS`/`ON_HOLD`）
3. IF-IP-DOR-05 额外：`validateIptDiseaseTypeCodesInBatch(SaveEncounterDiagnosisRequest)` — 非空 code 须 `IptPatientDiseaseDiagType` 合法

---

### 3.2 IF-IP-DOR-02 `getEncounterDiagnosis`

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationDiagnosisAppService#getEncounterDiagnosis` |
| **HTTP** | `POST /api/app/inpatientDoctorStationDiagnosis/getEncounterDiagnosis` |

#### Request 字段表（`GetEncounterDiagnosisRequest`，住院包自持）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 与QueryService `patientId` 交叉校验 |

#### Response 字段表（`EncounterDiagnosisResponse`，住院包自持）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| diagnosisList | List\<EncounterDiagnosisRowDto\> | 含 `iptDiseaseTypeCode`/`iptDiseaseTypeText` |
| totalCount | Integer | 列表总数 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `InpatientDoctorStationEncounterVisibilityResolver#assertVisible` + `validateInpatientEncounterClassImp` | 可见性 / 患者一致 / IMP；缺失 → EX |
| 2 | Cmd | `EncounterInpatientAdmissionOrchestrator#ensureWesternInsuranceDiagnosesExpandedToClinical` | 幂等：门诊医保西医→国临一对多；已国临/无绑码/无操作人则跳过 |
| 3 | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId).diagnosisJson()` | 实时扫表（展开后重读）；**禁止** QueryModel 内 `diagnosisJson` 作真源 |
| 4 | Asm | `InpatientDoctorStationDiagnosisMapper#parseDiagnosisJson` → `toEncounterDiagnosisResponse` | 空 JSON → 空列表；解析委托 `DiagnosisJsonSnapshotParser#parseStrict` |

---

### 3.3 IF-IP-DOR-03 `getConditionDefinitionBusinessClass`

与 **AS-OP-DOR-002-SDD** §3.3 **同构**；`GetCommonDiagnosesRequest`（住院包，**仅** `patientId` 必填，**无** `encounterId`），故 **不执行** §3.2 住院快照门控（与门诊 IF-DOR-06A 一致）；目录/模板为只读能力，患者维度由 `@NotNull` 保证。

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Pre | `ConditionQueryService#findByPatientId` + 定义批量装载 |
| 2 | Pre | `ConditionTemplateQueryService#listByOwnerAndType` ×4（病人历史/医生/用户/科室） |
| 3 | Asm | `DoctorStationDiagnosisMapper#toCommonDiagnosesResponse` |

---

### 3.4 IF-IP-DOR-04 `getConditionDefinitionMetadata`

与门诊 IF-DOR-07 **流程同构**，但**西医目录来源刻意分流**：

| 项 | 住院（本 IF） | 门诊（对照） |
| --- | --- | --- |
| 西医 `source_enum IN` | 固定 `clinical_v2`（`DoctorStationDiagnosisConstants.INPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCES`） | 系统设置 `doctorStationWesternDiagnosisSources`（默认 `hospital_dx,general_dx`） |
| 中医 | `tcm_dx` / `tcm_syndrome` | 同左 |
| 快照门控 | **无需** InpatientEncounterContextQueryModel（纯目录检索） | 同「无需就诊快照」 |

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | AppService 内联中西医筛选校验（合法码 `1`/`2`/空） |
| 2 | Pre | `InpatientDoctorStationDiagnosisMapper#parseDiagnosisKindSourceFilter` → `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions` |
| 3 | Asm | `InpatientDoctorStationDiagnosisMapper#toConditionDefinitionPageResponse` |

---

### 3.5 IF-IP-DOR-05 `saveDoctorDiagnosis`

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationDiagnosisAppService#saveDoctorDiagnosis` |
| **HTTP** | `POST /api/app/inpatientDoctorStationDiagnosis/saveDoctorDiagnosis` |

#### Request（`SaveEncounterDiagnosisRequest` + `SaveEncounterDiagnosisChildRequest`，住院包自持）

子项关键字段：`conditionId`、`conditionDefinitionId`、`mainDiagnosisFlag`、`diagnosisSortNo`、`iptDiseaseTypeCode`（**可空**）、`verificationStatusEnum`、`categoryCode`、`severityCode` 等（与门诊一致）。

#### Response（`SaveEncounterDiagnosisResponse`，住院包自持）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| savedCount | Integer | 保存条数 |
| savedConditionIds | List\<Long\> | 与入参顺序一致 |

#### 失败码口径

| 失败码 | 场景 | 实现 `ErrorCode` |
| --- | --- | --- |
| EX-IP-DOD-106 | 主诊 >1 | `CONDITION_INVALID_ARGUMENT` |
| EX-IP-DOD-107 | 类型码非法 | `CONDITION_INVALID_ARGUMENT` |
| EX-IP-DOD-108 | 快照缺失/非 IMP | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` |
| EX-IP-DOD-104 | 患者/就诊门控 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` |
| EX-IP-DOD-105 | 持久化失败 | `ENCOUNTER_RULE_CONFLICT` |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `DoctorStationDiagnosisMapper#validateSaveEncounterDiagnosisRequest` | 批次非空、主诊唯一、排序唯一 |
| 2 | Gate | `InpatientDoctorStationDiagnosisMapper#validateIptDiseaseTypeCodesInBatch` | **BR-IP-DOD-008** |
| 3 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | |
| 4 | Pre | `validateInpatientEncounterPatientGate` | |
| 5 | Pre | `validateInpatientEncounterWritableGate` | **BR-IP-DOD-007** |
| 6 | Gate | `CurrentOperatorResolver#requireOperatorId` | 未登录 → EX-IP-DOD-104 |
| 7 | Cmd-1 | `DoctorStationDiagnosisMapper#toSaveDoctorDiagnosisCommand` → `ConditionDomainService#saveDoctorDiagnosis` | 返回 `savedConditionIds` |
| 8 | Cmd-2 | `DoctorStationDiagnosisMapper#toBatchUpsertEncounterDiagnosisAssociationCommand` → `EncounterDomainService#batchUpsertEncounterDiagnosisAssociation` | `diagTypeCode` ← `iptDiseaseTypeCode`（**BR-IP-DOD-004**）；`medType` 空则 `YbMedType.GENERAL_OUTPATIENT`（**BR-IP-DOD-010**） |
| 9 | Asm | `DoctorStationDiagnosisMapper#toSaveEncounterDiagnosisResponse` | |

#### Mapper 方法签名（住院增量）

```java
void validateInpatientEncounterPatientGate(InpatientEncounterContextQueryModel snapshot, Long requestPatientId);
void validateInpatientEncounterClassImp(InpatientEncounterContextQueryModel snapshot);
void validateInpatientEncounterWritableGate(InpatientEncounterContextQueryModel snapshot);
void validateIptDiseaseTypeCodesInBatch(SaveEncounterDiagnosisRequest request);
```

---

### 3.6 IF-IP-DOR-06 `delEncounterDiagnosis`

与 **AS-OP-DOR-002-SDD** §3.6 **同构**；Pre 使用 §3.2 住院快照门控 + `validateInpatientEncounterWritableGate`。

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `DoctorStationDiagnosisMapper#validateDeleteEncounterDiagnosisRequest` | |
| 2 | Pre | `mustGetInpatientContextByEncounterId` + 住院门控（`patientId` 未传时用QueryService `patientId` 做交叉校验） | |
| 3 | Pre | `ConditionQueryService#countAdviceRefByConditionId` | **>0** → EX-IP-DOD-102 |
| 4 | Gate | `requireOperatorId` | |
| 5 | Cmd-1 | `EncounterDomainService#removeEncounterDiagnosisAssociation` | |
| 6 | Cmd-2 | `ConditionDomainService#delEncounterDiagnosis` | |
| 7 | Asm | `toDeleteEncounterDiagnosisResponse`；捕获 NOT_FOUND 幂等成功 | |

---

### 3.7 IF-IP-DOR-07 `switchMainEncounterDiagnosis`

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationDiagnosisAppService#switchMainEncounterDiagnosis` |
| **HTTP** | 待挂路由；与现诊断 Controller 同前缀：`POST /api/app/inpatientDoctorStationDiagnosis/switchMainEncounterDiagnosis` |

#### Request（`SwitchMainEncounterDiagnosisRequest`，住院包自持）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| encounterDiagnosisId | Long | 是 | 拟设为主诊的就诊诊断行标识 |
| patientId | Long | 否 | 非空时与住院快照交叉校验；空则由可见性门控解析患者 |

#### Response（`SwitchMainEncounterDiagnosisResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 切换成功标记 |
| message | String | 操作结果提示 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | AppService 校验 `encounterId` / `encounterDiagnosisId` 必填 | 缺失 → EX-IP-DOD-104 |
| 2 | Pre | 住院快照门控（`patientId` 非空走 assertVisible；空走 assertVisibleEncounter）+ `validateInpatientEncounterWritableGate` | **BR-IP-DOD-007** |
| 3 | Pre | `validateInpatientEncounterPatientGate` + `validateInpatientEncounterClassImp` | IMP |
| 4 | Gate | `CurrentOperatorResolver#requireOperatorId` | 未登录 → EX-IP-DOD-104 |
| 5 | Cmd | `EncounterDomainService#switchPrimaryEncounterDiagnosis` | 西医单行 / 中医病证组；清空同就诊其余主诊（**BR-IP-DOD-011**） |
| 6 | Post | `syncAdmissionDiagnosisToEmrIfEnabled` | 成功后按需同步入院诊断至病历 |
| 7 | Asm | 装配 `SwitchMainEncounterDiagnosisResponse` | 目标已为主诊且无写库时幂等成功 |

---

## 4. QueryService 与事件刷新

### 4.1 QueryService查询语义

| QueryService | 查询服务方法 | 用于 IF | 用途 |
| --- | --- | --- | --- |
| InpatientEncounterContextQueryModel | `mustGetInpatientContextByEncounterId` | IF-IP-DOR-02/05/06 | 写前门控（IMP + 快照存在）；**非** IF-IP-DOR-02 诊断行真源 |
| `EncounterDiagnosisSnapshotAssembler` | `buildSnapshot` | IF-IP-DOR-02 | 诊断 Tab 读路径（实时扫表 → Mapper 解析） |
| ConditionDefinition | `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions` | IF-IP-DOR-04 | 目录分页 |
| ConditionTemplate / Condition | 见 §3.3 | IF-IP-DOR-03 | 常用树 |

### 4.2 领域事件（读侧无投影）

> **2026-06**：读模型投影器已废止；下列事件 **不** 触发 `EncounterQueryService` / QueryModel **`SET diagnosis_json`**。关联类事件中的 `diagnosisJson` 为**可选兼容载荷**；列表 enrichment 由 **`InpatientEncounterContextLoader`** 在分页/批量加载时注入 **`EncounterDiagnosisSnapshotAssembler#buildSnapshot`** 回填 QueryModel 内部字段（供 `AdmissionPrimaryDiagnosisAssembler` 解析，**禁止** API 透传）。

| 领域事件 | 读侧消费 |
| --- | --- |
| `EncounterDiagnosisSavedEvent` / `DeletedEvent` / `EncounterDiagnosisAssociationUpsertedEvent` / `RemovedEvent` | 无 QueryService 投影；下游刷新依赖 Loader 再查或 App 直调 **`buildSnapshot`** |

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | DomainService#method | 事务策略 |
| --- | --- | --- | --- | --- |
| IF-IP-DOR-05 | saveDoctorDiagnosis | Cmd-1 | `ConditionDomainService#saveDoctorDiagnosis` | App **无** `@Transactional` |
| IF-IP-DOR-05 | saveDoctorDiagnosis | Cmd-2 | `EncounterDomainService#batchUpsertEncounterDiagnosisAssociation` | 同上 |
| IF-IP-DOR-06 | delEncounterDiagnosis | Cmd-1 | `EncounterDomainService#removeEncounterDiagnosisAssociation` | 同上 |
| IF-IP-DOR-06 | delEncounterDiagnosis | Cmd-2 | `ConditionDomainService#delEncounterDiagnosis` | 同上 |
| IF-IP-DOR-07 | switchMainEncounterDiagnosis | Cmd-1 | `EncounterDomainService#switchPrimaryEncounterDiagnosis` | App **无** `@Transactional` |

### 5.2 事务边界与失败回滚

| 接口 | 事务边界 | 失败回滚策略 |
| --- | --- | --- |
| IF-IP-DOR-05 | 不在 AppService 声明事务 | 期望领域层保证 Cmd-1/Cmd-2 原子性 |
| IF-IP-DOR-06 | 不在 AppService 声明事务 | 先关联后事实；删除幂等 |
| IF-IP-DOR-07 | 不在 AppService 声明事务 | 单聚合主诊切换；目标已为主诊可无写库成功 |

### 5.3 幂等落点

| 接口 | 幂等策略 | 关联步骤 |
| --- | --- | --- |
| IF-IP-DOR-05 | 行 ID + DB 唯一约束 | §3.5 ⑦⑧ |
| IF-IP-DOR-06 | 已删再删返回成功 | §3.6 ⑦ |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 诊断保存重复提交 | 依赖行 ID 与唯一约束 | DB + 业务异常 | §3.5 |
| 诊断删除重复提交 | 幂等成功 | 捕获 NOT_FOUND | §3.6 |

### 6.2 并发控制

| 场景 | 承诺 | 实现方式 |
| --- | --- | --- |
| 双终端编辑同一就诊诊断 | 以后写为准或冲突提示 | 乐观锁/唯一约束 |

### 6.3 重试与容错

| 场景 | 承诺 | 实现方式 |
| --- | --- | --- |
| QueryService不可用 | 写接口 Pre 失败 EX-IP-DOD-104 | mustGet 强依赖 |

### 6.4 数据脱敏

按产品要求在 Asm 层处理患者姓名等（若启用）。

### 6.5 审计日志

| 场景 | 承诺 | 实现方式 |
| --- | --- | --- |
| 保存/删除诊断 | `operatorId` 写入 Command | §3.5/§3.6 Cmd |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| IMP 就诊加载诊断列表 | `EncounterDiagnosisSnapshotAssembler#buildSnapshot` + Mapper `parseDiagnosisJson` | P0 |
| 非 IMP / 快照缺失保存 | EX-IP-DOD-108 | P0 |
| 两个主诊保存 | EX-IP-DOD-106 | P0 |
| 非法 iptDiseaseTypeCode | EX-IP-DOD-107 | P0 |
| 空 iptDiseaseTypeCode 保存成功 | `diag_type_code` 为空 | P0 |
| 有医嘱引用删除 | EX-IP-DOD-102 | P0 |
| 保存后 InpatientEncounterContextQueryModel 快照更新 | 列表 diagnosisSummary 变化 | P1 |
| IF-IP-DOR-07 西医单行/中医病证组切换主诊 | 主诊唯一；不必整批保存 | P0 |

### 7.2 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| POST 五接口注册 | `/api/app/inpatientDoctorStationDiagnosis/*` | P0 |
| `switchMainEncounterDiagnosis` 挂路由后可调 | 待挂路由 / 同前缀 | P1 |

### 7.3 与门诊对照

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 与 OP-DOR-002 同请求体 | 住院仅多门控与QueryService | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.5 | 2026-10-12 | **IF-IP-DOR-02**：编排序列增 `ensureWesternInsuranceDiagnosesExpandedToClinical`（门诊医保→国临幂等展开）；对齐 AS **v1.4** |
| v1.4 | 2026-08-13 | **BR-IP-DOD-007 修订**：`validateInpatientEncounterWritableGate` 仅要求快照非 null；移除诊断写路径 `IN_PROGRESS`/`ON_HOLD` 门控；EX-IP-DOD-108 对齐 `ENCOUNTER_NOT_FOUND` |
| v1.3 | 2026-07-26 | 新增 **IF-IP-DOR-07** `switchMainEncounterDiagnosis` → `switchPrimaryEncounterDiagnosis`；HTTP 待挂路由；成功后 `syncAdmissionDiagnosisToEmrIfEnabled` |
| v1.2.1 | 2026-05-28 | §2.2 增补 **`EncounterDiagnosisSnapshotAssembler#buildSnapshot`**（与门诊 AS-OP-DOR-002 同口径） |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.2 | 2026-05-21 | 应用层 **`ConditionRepository`** 收敛至 **`ConditionQueryService#findByPatientId` / `#countAdviceRefByConditionId`**（对齐 **B.2**）；§2.2、§2.4、IF-IP-DOR-03/06 编排序列同步 |
| v1.0 | 2026-05-20 | 首版：IF-IP-DOR-02～06 目标态；对齐 **AS-IP-DOR-002** v1.0；QueryService **InpatientEncounterContextQueryModel** |
| v1.1 | 2026-05-20 | 住院包自持 request/response/dto 与 `InpatientDoctorStationDiagnosisMapper`（含门控与 JSON/Command 装配）；禁止跨包引用门诊诊断模块 |
