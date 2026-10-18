# 门诊医生站 · 中医 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-010-SDD  
> **版本**：v1.15 | **日期**：2026-09-06 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医生站 · 中医 Tab 应用服务（`outpatient/doctorstation/chinesemedicine`） |
| **边界** | IF-DOR-30～39：中医病证组成组 CRUD、中药处方按 `prescriptionGroupKey` 暂存/签发（**不**调用 `splitPrescription`）、代煎费、签退/删除、饮片目录、历史处方；**组套/模板**消费 **AS-BAS-MST-017**，不在本模块维护 |
| **编排权威** | `AS-OP-DOR-010-门诊医生站中医.md`（v1.16）§2 + §4 |
| **方法权威** | `DM-CLN-003` §6.1.1～6.1.3；`DM-ADM-002` §6.1.11～6.1.13；`DM-CLN-002` §6.2.6～6.2.7；`DM-MED-003` §6.1.4～6.1.5、§6.1.13～6.1.14；`DM-MED-002` §6 发放 FIFO；`DM-ADM-011` §6 收费；`DM-WKF-001` 代煎诊疗项目 |
| **严格对齐** | IF 编号 **IF-DOR-30～39** 与 AS 一致 |
| **实现阶段口径** | 应用层 **已落地**（`DoctorStationTcmAppService` + 37 个契约类）；HTTP 路由 `doctorStationTcm`；常量见 `domain.share.constants.DoctorStationTcmConstants`（**禁止** application 层 constants 包）；领域 Gap **G-TCM-010-01～03 已关闭**；IF-DOR-32 **须**调用 `batchUpsertTcmEncounterDiagnosisAssociation` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 入出参与 AS、DM 一致；持久化枚举存 **String code**；`prescriptionGroupKey` **仅**存在于 Request/`content_json` 交互层，**不落**独立库列 |
| **QueryService 口径** | 写前门控：`EncounterContextReader#mustGetOutpatientContextByEncounterId`；IF-DOR-34/39 直接调用 `MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter` / `#listTcmHerbalPrescriptionHistoryByPatient`（领域层已按方聚合）；应用层仅 enrich 目录名与费用态 |
| **事务口径** | 逻辑顺序见 §5；写方法 `DoctorStationTcmAppService` 声明 `@Transactional(rollbackFor = Exception.class)`（对齐 **AS-OP-DOR-003-SDD** 目标态）；领域层不声明事务 |
| **HTTP 接入口径** | `AppServiceControllerRegistrar` 动态注册：`POST /api/app/doctorStationTcm/{methodName}` |
| **业务异常码口径** | 实现层 `BusinessException` + `ErrorCodeConstants`；AS 中 `EX-TCM-*` 为语义别名 |

#### 1.2.1 文档语义码与 `ErrorCodeConstants.ErrorCode` 对照

| 文档语义码 | 实现 `ErrorCode`（典型触发） |
| --- | --- |
| EX-TCM-101 | `ENCOUNTER_INVALID_ARGUMENT` / 检索类失败 |
| EX-TCM-102 | `CONDITION_RULE_CONFLICT`（医嘱引用 > 0） |
| EX-TCM-103 | `CHARGE_ITEM_RULE_CONFLICT`（已收费） |
| EX-TCM-104 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT`（患者/就诊不匹配） |
| EX-TCM-105 | 持久化失败包装 |
| EX-TCM-201～207 | `CONDITION_INVALID_ARGUMENT` / `MEDICATION_REQUEST_INVALID_ARGUMENT` / `MEDICATION_REQUEST_RULE_CONFLICT` 等 |

#### 1.2.2 `content_json` 方头扩展（目标态）

中药方头非主表列字段（含 **`prescriptionGroupKey`**、用法、天数、煎煮法、备注、绑定的 **`tcmSyndromeGroup`**）由 `MedicationRequest.content_json` 结构化承载；同一 `prescriptionGroupKey` 下各行 `content_json` 中方头段**保持一致**（应用层写入时复制方头快照）。

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```
health-application/src/main/java/com/openhis/health/application/
└── outpatient/
    └── doctorstation/
        └── chinesemedicine/
            ├── service/
            │   └── DoctorStationTcmAppService.java
            ├── assembler/
            │   ├── TcmDiagnosisAssembler.java
            │   ├── TcmRxListAssembler.java
            │   ├── TcmRxSignAssembler.java
            │   └── TcmDecoctionAssembler.java
            ├── request/
            │   ├── ListTcmDiagnosisGroupsRequest.java
            │   ├── InitDoctorStationTcmRequest.java
            │   ├── PageTcmConditionDefinitionRequest.java
            │   ├── TcmDiagnosisGroupItemRequest.java
            │   ├── SaveTcmDiagnosisGroupsBatchRequest.java
            │   ├── DeleteTcmDiagnosisGroupRequest.java
            │   ├── ListTcmRxRequest.java
            │   ├── PageTcmMedCatalogRequest.java
            │   ├── TcmRxDraftHeaderRequest.java
            │   ├── SaveTcmRxDraftBatchRequest.java
            │   ├── SignTcmRxTargetRequest.java
            │   ├── SignTcmRxBatchRequest.java
            │   ├── WithdrawOrDeleteTcmRxRequest.java
            │   └── ListPatientTcmRxHistoryRequest.java
            ├── response/
            │   ├── ListTcmDiagnosisGroupsResponse.java
            │   ├── InitDoctorStationTcmResponse.java
            │   ├── PageTcmConditionDefinitionResponse.java
            │   ├── SaveTcmDiagnosisGroupsBatchResponse.java
            │   ├── DeleteTcmDiagnosisGroupResponse.java
            │   ├── ListTcmRxResponse.java
            │   ├── PageTcmMedCatalogResponse.java
            │   ├── SaveTcmRxDraftBatchResponse.java
            │   ├── SignTcmRxBatchResponse.java
            │   ├── WithdrawOrDeleteTcmRxResponse.java
            │   └── ListPatientTcmRxHistoryResponse.java
            ├── dto/
            │   ├── TcmDiagnosisGroupRowDto.java
            │   ├── TcmDiagnosisCatalogRowDto.java
            │   ├── TcmRxHeaderDto.java
            │   ├── TcmRxLineDto.java
            │   ├── TcmRxDraftLineRequest.java
            │   └── TcmRxHistoryRowDto.java
            └── mapper/
                ├── DoctorStationTcmMapper.java
                └── DoctorStationTcmMapperImpl.java

health-application/.../outpatient/doctorstation/
├── advice/
│   ├── assembler/DoctorStationAdviceCatalogAssembler.java   ← IF-DOR-35 读路径共用
│   ├── dto/DoctorStationAdviceCatalogRowDto.java            ← IF-DOR-35 行契约（与 IF-DOR-11 同构）
│   └── mapper/DoctorStationAdviceMapper.java

health-application/.../outpatient/doctorstation/advice/support/
└── OutpatientMedicationSignChargeOrchestrator.java               ← IF-DOR-37 与 IF-DOR-15 药线共用（FIFO 分段按 lotNumber 挂费）

zxplat-domain/.../domain/share/constants/
└── DoctorStationTcmConstants.java   ← 跨层常量（B.10.2.1，不在 application 包）
```

**复用（不重复落包）**：

- `querysupport.encounter.EncounterContextReader`
- `application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler`
- `outpatient.doctorstation.advice.assembler.DoctorStationAdviceCatalogAssembler`（IF-DOR-35：`assemblePageContent`，强制 `categoryCode=中草药`；**不做**同名折叠）
- `outpatient.doctorstation.advice.dto.DoctorStationAdviceCatalogRowDto`（IF-DOR-35 响应行，**与 IF-DOR-11 同构**）
- `outpatient.doctorstation.advice.mapper.DoctorStationAdviceMapper`（IF-DOR-36 UPDATE void 锚点）
- `outpatient.doctorstation.advice.query.OrderRequestFifoSignContextLoader`
- `outpatient.doctorstation.advice.support.OutpatientMedicationSignChargeOrchestrator`（IF-DOR-37 签发 FIFO+分段挂费+PLANNED；单价按分段 `lotNumber` → `selectExecutionPriceLineForChargeDraft`）

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 来源 |
| --- | --- | --- | --- |
| 应用服务 | `DoctorStationTcmAppService` | IF-DOR-30～39 编排 | 本模块 |
| 领域服务 | `ConditionDomainService` | `saveDoctorDiagnosisTcm` / `delEncounterDiagnosis` | domain.clinical.condition |
| 领域服务 | `EncounterDomainService` | `upsertEncounterDiagnosisAssociation` / `removeEncounterDiagnosisAssociation` | domain.administration.encounter |
| 领域服务 | `MedicationRequestDomainService` | 中药草稿/签发/签退 | domain.medication.medicationrequest |
| 领域服务 | `MedicationDispenseDomainService` | FIFO 发放、释放预占 | domain.medication.medicationdispense |
| 领域服务 | `ChargeItemDomainService` | 费用草稿/PLANNED/回退/void | domain.administration.chargeitem |
| Facade | `EncounterContextReader` | 门诊就诊门控 | querysupport.encounter |
| Bridge | `EncounterDiagnosisSnapshotAssembler` | IF-DOR-30 诊断 JSON 真源 | application.shared.diagnosis |
| 查询服务 | `ConditionDefinitionQueryService` | IF-DOR-31 目录分页 | domain.clinical.conditiondefinition |
| 查询服务 | `ConditionQueryService` | `countAdviceRefByConditionId` | domain.clinical.condition |
| 查询服务 | `MedicationRequestQueryService` | 列表/历史/签退删除门控 | domain.medication.medicationrequest |
| 查询服务 | `MedicationDispenseQueryService` | 签退时查发放锚点 | domain.medication.medicationdispense |
| 应用共享 | `EncounterChargeAccountOpener` | IF-DOR-36/37：饮片挂账（双账户默认医保户）；代煎 `preferInsurance=false` 挂个人现金自费户（**BR-TCM-RX-009**） | application.shared.financial |
| 查询服务 | `ChargeItemQueryService` | 已收费门控（签退/删除） | domain.administration.chargeitem |
| 基础设施 | `BusinessSequenceService` | `tcm_syndrome_group`、处方号序列 | zxplat-framework |
| 操作人 | `CurrentOperatorResolver` | 写路径 operatorId | module.common.validator |
| Mapper | `DoctorStationTcmMapper` + `Impl` | Request/Command/Response | 本模块 |
| Mapper | `DoctorStationAdviceMapper` | IF-DOR-36 void 锚点、sign Command | `advice` 模块 |
| Support | `OutpatientMedicationSignChargeOrchestrator` | IF-DOR-37：`replaceFifoDispenseAndPlanChargesAfterMedicationSigned`（分段按 `lotNumber` → `selectExecutionPriceLineForChargeDraft`） | `outpatient.doctorstation.advice.support` |
| Assembler | 见 §2.1 `assembler/*` | 病证组聚合、处方列表、签发取号、代煎费 | 本模块 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 中医目录 source | `DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM` | `DM-CLN-002 §4.1` | IF-DOR-31；`tcm_dx` 病名 / `tcm_syndrome` 证候 |
| catalogRole | `PageTcmConditionDefinitionRequest.catalogRole` | AS-OP-DOR-010 §1.3 | `DISEASE` / `SYNDROME` |
| 中西医标签 | `DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM` | `DM-ADM-002` 快照 JSON | IF-DOR-30 过滤 |
| 药品分类（饮片） | `PageTcmMedCatalogRequest.categoryCode` | `DM-MED-001` 码表 221 / 900006 | 固定 `4`=中草药；IF-DOR-35 |
| 医嘱状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-MED-003 §5` | DRAFT/ACTIVE/COMPLETED… |
| 处方类别 rx_type | `MedicationRequest.rxTypeCode` | `DM-MED-003 §6.1.15` 饮片推导 | IF-DOR-37；**BR-TCM-RX-008** |
| 代煎标志 | `SaveTcmRxDraftBatchRequest.sufferingFlag` | `DM-MED-003 §3.1` `suffering_flag` | IF-DOR-36/37 |
| 中药付数 | `TcmRxHeaderDto.chineseHerbsDoseQuantity` | `DM-MED-003 §3.1` | IF-DOR-36 数量重算 |
| 验证状态 | `SaveTcmDiagnosisGroupsBatchRequest` 组内项 → Command String | `DM-CLN-003 §2.3.1` | 默认 `CONFIRMED` |

### 2.4 领域方法白名单

| 聚合 | 方法 | 用于 IF |
| --- | --- | --- |
| ConditionDomainService | `saveDoctorDiagnosisTcm(SaveDoctorDiagnosisTcmCommand)` | IF-DOR-32 |
| ConditionDomainService | `delEncounterDiagnosis(DelEncounterDiagnosisCommand)` | IF-DOR-33 |
| EncounterDomainService | `batchUpsertTcmEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand)` | IF-DOR-32 |
| EncounterDomainService | `removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand)` | IF-DOR-33 |
| MedicationRequestDomainService | `createHerbalMedicationOrderDraft(CreateHerbalMedicationOrderDraftCommand)` | IF-DOR-36 |
| MedicationRequestDomainService | `changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand)` | IF-DOR-36/37 |
| MedicationRequestDomainService | `removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand)` | IF-DOR-38 |
| MedicationRequestDomainService | `signMedicationOrder(SignMedicationOrderCommand)` | IF-DOR-37 |
| MedicationRequestDomainService | `signOffMedicationOrder(SignOffMedicationOrderCommand)` | IF-DOR-38 |
| MedicationDispenseDomainService | `replaceFifoDraftDispensesAfterDoctorStationSign(ReplaceFifoMedicationDispenseDraftsCommand)` | IF-DOR-37 |
| MedicationDispenseDomainService | `releaseDispenseReserve(ReleaseDispenseReserveCommand)` | IF-DOR-38 |
| ChargeItemDomainService | `createDraftChargeItemFromOrder(CreateChargeItemCommand)` | IF-DOR-36 |
| ChargeItemDomainService | `voidUnbilledDraftChargeItemsByServiceAnchors(...)` | IF-DOR-36/38 |
| ChargeItemDomainService | `planDraftChargeItemsAfterAdviceSignedByDispenseAnchors` | IF-DOR-37 |
| ChargeItemDomainService | `rollbackPlannedChargeItemsAfterAdviceSignedOff*` | IF-DOR-38 |
| ConditionDefinitionQueryService | `pageActiveDoctorStationDefinitions(...)` | IF-DOR-31 |
| MedicationRequestQueryService | `listTcmHerbalPrescriptionsByEncounter` | IF-DOR-34 |
| MedicationRequestQueryService | `listTcmHerbalPrescriptionHistoryByPatient` | IF-DOR-39 |
| MedicationRequestQueryService | `checkMedicationRequestSignOffAllowed` / `checkMedicationRequestDeletable` | IF-DOR-38 |
| ConditionQueryService | `countAdviceRefByConditionId` | IF-DOR-33 |

> **白名单说明**：**禁止** IF-DOR-37 调用 `MedicationRequestDomainService#splitPrescription`（**BR-TCM-ISO-003**）。**禁止** IF-DOR-32 调用 `batchUpsertEncounterDiagnosisAssociation`（**G-TCM-010-02**）。

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| — | `initDoctorStationTcm` | 中医 Tab 页初始化（用法/频次下拉） | `POST /api/app/doctorStationTcm/initDoctorStationTcm` |
| IF-DOR-30 | `listTcmDiagnosisGroups` | 加载中医病证组列表 | `POST /api/app/doctorStationTcm/listTcmDiagnosisGroups` |
| IF-DOR-31 | `pageTcmConditionDefinitionCatalog` | 分页检索中医病名/证候 | `POST /api/app/doctorStationTcm/pageTcmConditionDefinitionCatalog` |
| IF-DOR-32 | `saveTcmDiagnosisGroupsBatch` | 全量保存病证组 | `POST /api/app/doctorStationTcm/saveTcmDiagnosisGroupsBatch` |
| IF-DOR-33 | `deleteTcmDiagnosisGroup` | 删除病证组 | `POST /api/app/doctorStationTcm/deleteTcmDiagnosisGroup` |
| IF-DOR-34 | `listTcmHerbalPrescriptions` | 加载中药处方列表 | `POST /api/app/doctorStationTcm/listTcmHerbalPrescriptions` |
| IF-DOR-35 | `pageTcmHerbalMedicationCatalog` | 分页检索中药饮片 | `POST /api/app/doctorStationTcm/pageTcmHerbalMedicationCatalog` |
| IF-DOR-36 | `saveTcmHerbalPrescriptionDraftBatch` | 暂存中药处方 | `POST /api/app/doctorStationTcm/saveTcmHerbalPrescriptionDraftBatch` |
| IF-DOR-37 | `signTcmHerbalPrescriptionBatch` | 签发中药处方 | `POST /api/app/doctorStationTcm/signTcmHerbalPrescriptionBatch` |
| IF-DOR-38 | `withdrawOrDeleteTcmHerbalPrescription` | 签退/删除中药处方 | `POST /api/app/doctorStationTcm/withdrawOrDeleteTcmHerbalPrescription` |
| IF-DOR-39 | `listPatientTcmHerbalPrescriptionHistory` | 查询患者历史中药处方 | `POST /api/app/doctorStationTcm/listPatientTcmHerbalPrescriptionHistory` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数/权限/患者就诊一致性 | 不再调用后续步骤 |
| **Pre** | 只读：快照、引用计数、库存、状态门控 | 抛异常，**禁止 Cmd** |
| **Cmd** | 领域写 + 事件 | 事务回滚 §5 |
| **Asm** | Mapper/Assembler 纯装配 | — |

**重要说明**：先读完各 IF「领域编排序列」，按序号从上到下执行。`initDoctorStationTcm` 为 Tab 初始化辅助接口，**不**占用 IF-DOR-30～39 编号。

---

### 3.2 `initDoctorStationTcm`（中医 Tab 页初始化）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationTcmAppService#initDoctorStationTcm` |
| **HTTP** | `POST /api/app/doctorStationTcm/initDoctorStationTcm` |

#### Request 字段表（`InitDoctorStationTcmRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识；须与门诊快照一致 |

#### Response 字段表（`InitDoctorStationTcmResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| tcmMethodCodeOptions | List\<OptionDto\> | 中药用法下拉（术语 `tcm_method_code`，不含西医 `method_code`） |
| medicationRateOptions | List\<OptionDto\> | 用药频次下拉（与 **IF-DOR-18** `medicationRateOptions` 同源） |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId` + `validatePatientEncounter` | 就诊/患者一致 |
| ② | Pre | `CodeSystemQueryService#getOptionListByCodeSystemName(TCM_METHOD_CODE)` | 中药用法 |
| ③ | Asm | `FrequencyOptionLoader#loadActiveRateOptions` | 用药频次 |

---

### 3.3 IF-DOR-30 `listTcmDiagnosisGroups`（加载中医病证组列表）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationTcmAppService#listTcmDiagnosisGroups` |
| **HTTP** | `POST /api/app/doctorStationTcm/listTcmDiagnosisGroups` |

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |

#### Response 字段表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| groups | List | 病证组列表 |
| totalCount | Integer | 组数 |

#### DTO 字段表（TcmDiagnosisGroupRowDto）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| tcmSyndromeGroup | String | 组号（库列 `tcm_syndrome_group`） |
| diseaseConditionId | Long | 病名事实 id |
| diseaseDefinitionId | Long | 病名目录 id |
| diseaseCode | String | 病名编码 |
| diseaseName | String | 病名展示 |
| syndromeConditionId | Long | 证候事实 id |
| syndromeDefinitionId | Long | 证候目录 id |
| syndromeCode | String | 证候编码 |
| syndromeName | String | 证候展示 |
| primaryFlag | Integer | 主诊：1/0 |
| rank | Integer | 排序号 |
| medicalTypeCode | String | 医疗类型 |
| diagnosisDesc | String | 组级描述 |

#### 失败码口径表

| 失败码 | 场景 | 实现 ErrorCode |
| --- | --- | --- |
| EX-TCM-104 | 就诊/患者不匹配 | `ENCOUNTER_INVALID_ARGUMENT` |
| EX-TCM-101 | 快照失败 | `ENCOUNTER_INVALID_ARGUMENT` |

#### Mapper 方法签名

- `DoctorStationTcmMapper#validatePatientEncounter(OutpatientEncounterContextQueryModel, Long): void`
- `TcmDiagnosisAssembler#groupTcmDiagnosisRows(String diagnosisJson): List<TcmDiagnosisGroupRowDto>`
- `DoctorStationTcmMapper#toListTcmDiagnosisGroupsResponse(List<TcmDiagnosisGroupRowDto>): ListTcmDiagnosisGroupsResponse`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId` | encounterId | 门诊快照 |
| ② | Gate | `DoctorStationTcmMapper#validatePatientEncounter` | patientId | EX-TCM-104 |
| ③ | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId).diagnosisJson()` | encounterId | 读侧真源 |
| ④ | Asm | `TcmDiagnosisAssembler#groupTcmDiagnosisRows` | ③ JSON | 过滤 `diagnosisKind=TCM` + 按 `tcmSyndromeGroup` 聚合；仅一侧的组标记异常不返回 |
| ⑤ | Asm | `DoctorStationTcmMapper#toListTcmDiagnosisGroupsResponse` | ④ | 仅读路径 |

---

### 3.4 IF-DOR-31 `pageTcmConditionDefinitionCatalog`（分页检索中医病名/证候）

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| catalogRole | String | 是 | `DISEASE` 或 `SYNDROME` |
| keyword | String | 否 | 关键字 |
| pageNo | Integer | 是 | 页码 |
| pageSize | Integer | 是 | 页大小 |

#### Response 字段表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rows | List | 目录行 |
| total | Long | 总条数 |

#### DTO（TcmDiagnosisCatalogRowDto）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| definitionId | Long | 目录 id |
| conditionCode | String | 编码 |
| conditionName | String | 名称 |
| sourceCode | String | `tcm_dx`/`tcm_syndrome` |
| sourceText | String | 来源展示 |

#### 关键校验

- `catalogRole` 缺失 → EX-TCM-201（映射 `CONDITION_INVALID_ARGUMENT`）
- 仅 **ACTIVE** 目录（QueryService 内过滤）

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | AppService 解析 `catalogRole` → source 列表 | `DISEASE`→`tcm_dx`；`SYNDROME`→`tcm_syndrome` |
| ② | Pre | `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions(...)` | 分页读 |
| ③ | Asm | `DoctorStationTcmMapper#toPageTcmConditionDefinitionResponse` | 仅读 |

---

### 3.5 IF-DOR-32 `saveTcmDiagnosisGroupsBatch`（全量保存病证组）

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| groups | List | 是 | 全量组；空列表拒绝 |

**groups[] 元素**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| tcmSyndromeGroup | String | 否 | 空=新建组；非空=更新 |
| diseaseDefinitionId | Long | 是 | 病名目录 |
| diseaseConditionId | Long | 否 | 更新时病名事实 id |
| syndromeDefinitionId | Long | 是 | 证候目录 |
| syndromeConditionId | Long | 否 | 更新时证候事实 id |
| primaryFlag | Integer | 是 | 组级主诊 |
| rank | Integer | 是 | 组级排序 |
| medicalTypeCode | String | 否 | 医疗类型 |
| diagnosisDesc | String | 否 | 描述 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 成功 |
| savedGroupKeys | List\<String\> | 组号列表 |

#### 失败码

| 失败码 | 场景 |
| --- | --- |
| EX-TCM-201 | 缺一病一证 |
| EX-TCM-202 | 主诊组 >1 |
| EX-TCM-105 | 持久化失败 |

#### Mapper 签名

- `toSaveDoctorDiagnosisTcmCommand(SaveTcmDiagnosisGroupsBatchRequest, Long operatorId): SaveDoctorDiagnosisTcmCommand`
- `toBatchUpsertTcmEncounterDiagnosisAssociationCommand(SaveTcmDiagnosisGroupsBatchRequest, List<DiagnosisItemCommand>): BatchUpsertEncounterDiagnosisAssociationCommand`
- `diffRemovedTcmGroups(List<TcmDiagnosisGroupRowDto> existing, SaveTcmDiagnosisGroupsBatchRequest): List<String>`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId` | 门控 |
| ② | Gate | `validatePatientEncounter` | |
| ③ | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot` + 过滤 TCM 组 | 用于 diff |
| ④ | Gate | AppService 校验每组 1 病+1 证、主诊≤1 | EX-TCM-201/202 |
| ⑤ | Asm | `diffRemovedTcmGroups` | ③ vs Request → 待删组号 |
| ⑥ | Pre | 对每个待删组：组内 `conditionId` 调 `ConditionQueryService#countAdviceRefByConditionId` | >0 → EX-TCM-102 |
| ⑦ | Cmd | 对每个待删组：`removeEncounterDiagnosisAssociation`（2 项） | **写库点-1a** |
| ⑧ | Cmd | 对每个待删组：`delEncounterDiagnosis` ×2 | **写库点-1b** |
| ⑨ | Asm | 为新组生成 `tcmSyndromeGroup`（`BusinessSequenceService`） | 更新组沿用原组号 |
| ⑩ | Cmd | `ConditionDomainService#saveDoctorDiagnosisTcm(SaveDoctorDiagnosisTcmCommand)` | **写库点-2**；2×组数条事实 |
| ⑪ | Cmd | `EncounterDomainService#batchUpsertTcmEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand)` | **写库点-3**；仅对齐/软删 `tcm_flag=true` 关联，**不**删 Condition、**不**误删西医关联 |
| ⑫ | Asm | `toSaveTcmDiagnosisGroupsBatchResponse` | |

> **互斥**：步骤 ⑦～⑧ **仅当** ⑤ 产出非空待删组（须先解除关联再删 Condition 事实）；否则跳至 ⑨。**禁止**步骤 ⑪ 改用 `batchUpsertEncounterDiagnosisAssociation` 或 `upsertEncounterDiagnosisAssociation` 逐条替代全量中医批次。

---

### 3.6 IF-DOR-33 `deleteTcmDiagnosisGroup`（删除病证组）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | |
| patientId | Long | 是 | |
| tcmSyndromeGroup | String | 是 | 组号 |

#### 编排序列（摘要）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ①～② | Pre/Gate | 同 IF-DOR-30 | |
| ③ | Pre | 快照解析组内 2 个 `conditionId` | 组不存在 → 提示刷新 |
| ④ | Pre | `countAdviceRefByConditionId` ×2 | EX-TCM-102 |
| ⑤ | Cmd | `removeEncounterDiagnosisAssociation` | |
| ⑥ | Cmd | `delEncounterDiagnosis` ×2 | |

---

### 3.7 IF-DOR-34 `listTcmHerbalPrescriptions`（加载中药处方列表）

#### Request

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| encounterId | Long | 是 |
| patientId | Long | 是 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptions | List | 按方聚合 |

#### DTO TcmRxHeaderDto

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptionGroupKey | String | 自 `content_json` 解析 |
| prescriptionNo | String | 已签发时有值 |
| tcmSyndromeGroup | String | 绑定病证组 |
| chineseHerbsDoseQuantity | BigDecimal | 付数 |
| sufferingFlag | Boolean | 代煎 |
| methodCode | String | 用法 |
| methodName | String | 用法展示名（`tcm_method_code` 优先） |
| rateCode | String | 用药频次编码 |
| rateName | String | 用药频次展示名 |
| days | Integer | 天数 |
| decoctionMethodCode | String | 煎煮法 |
| remark | String | 备注 |
| signCode | String | 签发批次码 |
| statusEnum | String | 方状态（取行内最高态） |
| lines | List\<TcmRxLineDto\> | 明细 |

#### DTO TcmRxLineDto

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| medicationRequestId | Long | 行 id |
| medicationDefinitionId | Long | 饮片目录 |
| medicationName | String | 展示 |
| dose | BigDecimal | 单剂用量 |
| doseUnitCode | String | 单剂用量单位编码 |
| doseUnitName | String | 单剂用量单位展示名（**BR-ORD-022** / **BR-TCM-RX-012**） |
| quantity | BigDecimal | 请求数量=单剂×付数 |
| unitCode | String | 数量单位编码 |
| unitName | String | 数量单位展示名（**BR-ORD-022**） |
| retailUnitPrice | BigDecimal | 单剂用量单位零售价（元/剂量单位；已挂费取 `ChargeItem.unit_price`，否则 DOSE 槽目录价，**BR-TCM-RX-012**） |
| sortNumber | Integer | 味序 |
| chargeStatusText | String | 费用态展示 |
| lineStatusEnum | String | DRAFT/ACTIVE |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ①～② | Gate | 就诊门控 | |
| ③ | Pre | `MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter(encounterId, DOCTOR)` | 领域层已按方聚合为 `TcmHerbalPrescriptionGroupQueryModel` |
| ④ | Asm | enrich 目录名、ChargeItem 态与单价、**`doseUnitName`/`unitName`/`retailUnitPrice`**（`TcmRxListAssembler` + `ProductDefinitionBatchLoader` + `ClinicalCatalogPriceResolvesupport）、用法/频次/煎煮法展示（`TcmMethodCodeDisplaysupport + `FrequencyOptionLoader`） | 应用层 `TcmRxListAssembler` |
| ⑤ | Asm | `toListTcmRxResponse` | 仅读 |

---

### 3.8 IF-DOR-35 `pageTcmHerbalMedicationCatalog`（分页检索饮片）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationTcmAppService#pageTcmHerbalMedicationCatalog` |
| **HTTP** | `POST /api/app/doctorStationTcm/pageTcmHerbalMedicationCatalog` |

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 库存预减、定价上下文 |
| patientId | Long | 是 | 门控 |
| keyword | String | 否 | 关键字 |
| pageNo | Integer | 是 | 页码 |
| pageSize | Integer | 是 | 页大小 |
| prepickedProductKeys | List | 否 | 组套套用后预置键 |

#### Response 字段表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rows | List\<DoctorStationAdviceCatalogRowDto\> | 与 **IF-DOR-11** 相同行契约（**BR-TCM-RX-014**：不做同名折叠） |
| total | Long | 目录分页总条数（与 IF-DOR-11 同口径，不因折叠回减） |

#### DTO（DoctorStationAdviceCatalogRowDto，复用 IF-DOR-11）

行字段以 **AS-OP-DOR-003-SDD §3.3** `DoctorStationAdviceCatalogRowDto` 为准，含 **`productTableName`/`productDefinitionId`/`unitCode`/`unitName`（包装单位）/`unitBands`/`unitPrice`/`inventoryList`/`availableQuantity`/`manufacturer`/`defaultDispenseLocationId`** 等；**禁止**维护独立的 `TcmMedCatalogRowDto` 或饮片专用单位/单价换算逻辑；**禁止**下发 `defEncounterUnit*` / `defInhospitalUnit*`（与 **AS-OP-DOR-003 · BR-ORD-011 v1.42** 一致）；**禁止**下发 `mergedTotalAvailableQuantity*` 或读侧假合并 id。

**BR-TCM-RX-014（选品不合并）**：

| 字段/语义 | 说明 |
| --- | --- |
| `productDefinitionId` | 真实目录 id（与西药一致） |
| `manufacturer` | 该目录厂家/产地 |
| `availableQuantity` / Text | 该目录各批净可用合计（开立上限参考） |
| `inventoryList` | 预减后各库存行（批号定价对齐） |
| 非目标 | 当前页同名折叠、合并总量列、`excludeProductDefinitionIds` 露出「下一家」 |
| 写路径 | 暂存绑所选目录；签发 FIFO 跨批分段见 **IF-DOR-37 / BR-TCM-RX-007**（本 IF 不改） |

#### 关键校验

- **BR-TCM-RX-003**：`categoryCode` 后端强制中草药，忽略客户端其它值
- **BR-TCM-RX-014**：选品不做同名合并；与西药目录读路径同构
- 定价/库存预减口径与 **AS-OP-DOR-003 · IF-DOR-11** 同构

#### Mapper 方法签名

- `DoctorStationTcmMapper#toPageDoctorStationAdviceCatalogRequest(PageTcmMedCatalogRequest): PageDoctorStationAdviceCatalogRequest`（内部强制 category）
- `DoctorStationAdviceMapper#toProductDefinitionPageQuery(PageDoctorStationAdviceCatalogRequest): ProductDefinitionPageQuery`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId` | 取 orgId、科室 |
| ② | Gate | `validatePatientEncounter` | |
| ③ | Asm | `DoctorStationAdviceMapper#toProductDefinitionPageQuery` + `pageQuery.setCategoryCode(HERBAL)` | 强制中草药 |
| ④ | Pre | `ProductDefinitionCatalogPageLoader#pageProductDefinition` | 与 IF-DOR-11 同 Loader |
| ⑤ | Asm | `DoctorStationAdviceCatalogAssembler#assemblePageContent(catalogRequest, page, snapshot)` | 库存预减、批号定价、`unitBands`、`manufacturer` |
| ⑥ | Asm | `PagedResultBuilder#build` | 仅读；不做同名折叠 |

---

### 3.9 IF-DOR-36 `saveTcmHerbalPrescriptionDraftBatch`（暂存中药处方）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationTcmAppService#saveTcmHerbalPrescriptionDraftBatch` |
| **HTTP** | `POST /api/app/doctorStationTcm/saveTcmHerbalPrescriptionDraftBatch` |

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | |
| patientId | Long | 是 | |
| prescriptions | List | 是 | 一张或多张方 |

**prescriptions[] 方头**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| prescriptionGroupKey | String | 是 | 前端方标识 |
| tcmSyndromeGroup | String | 是 | 绑定病证组 |
| chineseHerbsDoseQuantity | BigDecimal | 是 | 付数 |
| sufferingFlag | Boolean | 否 | 代煎 |
| methodCode | String | 否 | 用法 |
| rateCode | String | 否 | 用药频次 |
| days | Integer | 否 | 天数 |
| decoctionMethodCode | String | 否 | 煎煮法 |
| remark | String | 否 | 备注 |
| lines | List | 是 | 明细行 |

**lines[]（TcmRxDraftLineRequest）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| lineOp | String | 是 | `CREATE`/`UPDATE`/`DELETE` |
| medicationRequestId | Long | 条件 | UPDATE/DELETE 必填 |
| medicationDefinitionId | Long | 条件 | CREATE/UPDATE 必填 |
| dose | BigDecimal | 条件 | 单剂用量 |
| doseUnitCode | String | 条件 | |
| sortNumber | Integer | 否 | 味序 |
| dispenseLocationId | Long | 否 | 发药库位 |

#### Response 字段表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| newMedicationRequestIds | List\<Long\> | 新建行 id |
| message | String | |

#### 失败码

| 失败码 | 场景 |
| --- | --- |
| EX-TCM-203 | 未绑病证组 |
| EX-TCM-204 | 库存不足 |
| EX-TCM-105 | 持久化失败 |

#### Mapper 方法签名

- `toCreateHerbalMedicationOrderDraftCommand(...): CreateHerbalMedicationOrderDraftCommand`
- `toChangeHerbalMedicationOrderDraftCommand(...): ChangeHerbalMedicationOrderDraftCommand`
- `toCreateDraftChargeItemCommand(...): CreateChargeItemCommand`
- `buildHerbalContentJson(TcmRxHeaderDto, TcmRxDraftLineRequest): String`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId` | |
| ② | Gate | `validatePatientEncounter` | |
| ③ | Pre | `EncounterChargeAccountOpener#resolveChargeAccountId(encounterId, null, preferInsurance=true)` | 双账户默认医保户；无可用账户拒绝 |
| ④ | Pre | 校验 `tcmSyndromeGroup` 在 IF-DOR-30 结果中存在 | EX-TCM-203 |
| ⑤ | Pre | 库存预校验 Assembler（UPDATE 先释放原占用） | EX-TCM-204 |
| ⑥ | Gate | 校验目录行均为中草药 category | |
| ⑦ | Cmd | **CREATE 分支**：`createHerbalMedicationOrderDraft(CreateHerbalMedicationOrderDraftCommand)` | **写库点-1**；领域已支持全字段（**G-TCM-010-01 已关闭**） |
| ⑧ | Cmd | **CREATE 分支**：`changeHerbalMedicationOrderDraft` 补 medicationId/dose/quantity/condition 绑定 | **写库点-1 续** |
| ⑨ | Cmd | **CREATE 分支**：`createDraftChargeItemFromOrder` | **写库点-2**；quantity=单剂×付数 |
| ⑩ | Cmd | **UPDATE 分支**：`ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchor`（`DoctorStationAdviceMapper#toVoidDraftChargeAnchor`，**带 operatorId**）→ `changeHerbalMedicationOrderDraft` → 重算挂费 | 与 **IF-DOR-14** 西药 UPDATE 对称 |
| ⑪ | Cmd | **DELETE 分支**：`voidUnbilledDraftChargeItemsByServiceAnchors` → `removeMedicationOrderDraft` | |
| ⑫ | Asm | `toSaveTcmRxDraftBatchResponse` | |

> **互斥**：⑦～⑨ / ⑩ / ⑪ 按 `lineOp` 三选一执行；**禁止**串行执行全部分支。

---

### 3.10 IF-DOR-37 `signTcmHerbalPrescriptionBatch`（签发中药处方）

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | |
| patientId | Long | 是 | |
| signTargets | List | 是 | 待签发行；含 `prescriptionGroupKey` + `medicationRequestId` |

#### Response 字段表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| prescriptionNoByGroupKey | Map\<String,String\> | key → 处方号 |
| signCode | String | 批次签发码（目标态） |

#### 失败码

| 失败码 | 场景 |
| --- | --- |
| EX-TCM-205 | 含非 `tcm_flag` 行 |
| EX-TCM-204 | FIFO 库存不足 |
| EX-TCM-206 | 代煎项目未配置 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre/Gate | 就诊门控 + `EncounterChargeAccountOpener` 默认挂账账户（双账户=医保户） | |
| ② | Pre | 加载待签行并断言 `tcmFlag=true`、状态 DRAFT | EX-TCM-205 |
| ③ | Asm | `TcmRxSignAssembler#resolvePrescriptionNoByGroupKey` | 按 key 分组；已有未撤回 `prescriptionNo` 则复用，否则 `BusinessSequenceService` 取 CP 序列；**禁止** `splitPrescription` |
| ④ | Cmd | `changeHerbalMedicationOrderDraft` 预写 `rxTypeCode`（**BR-TCM-RX-008**） | 目标态扩展 Command |
| ⑤ | Cmd | 逐行 `signMedicationOrder(SignMedicationOrderCommand)` | **写库点-1** |
| ⑥ | Cmd | `OutpatientMedicationSignChargeOrchestrator#replaceFifoDispenseAndPlanChargesAfterMedicationSigned` | **写库点-2/3**：void 请求锚点暂存费 → FIFO `replaceFifo*` → `#createDispenseSegmentChargeDrafts` → `planDraftChargeItemsAfterAdviceSignedByDispenseAnchors`；与 **IF-DOR-15** 药线同 Support |
| ⑦ | Cmd | **若**方头 `sufferingFlag=true`：`TcmDecoctionAssembler#replacePlannedDecoctionCharge` | 按付数生成代煎 companion SR（`based_on`→代表药行，**不**写方号）+ 费用 PLANNED；`ChargeItem.account_id`=个人现金自费户；SR.`contract_id`=自费冗余（**禁止** open/改写 Account，**BR-TCM-RX-009**）；**否则**按同方 based_on void 代煎费；「有效代煎 companion」仅 **DRAFT/ACTIVE**（停嘱/已完成不挡再补充） |
| ⑧ | Asm | `toSignTcmRxBatchResponse` | |

---

### 3.11 IF-DOR-38 `withdrawOrDeleteTcmHerbalPrescription`（签退/删除）

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | |
| patientId | Long | 是 | |
| action | String | 是 | `WITHDRAW` 签退 / `DELETE` 删除 |
| scope | String | 是 | `PRESCRIPTION` 整方 / `LINE` 单行 |
| medicationRequestIds | List\<Long\> | 是 | 目标行 |
| prescriptionGroupKey | String | 条件 | 整方时携带 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre/Gate | 就诊门控 | |
| ② | Pre | `ChargeItemQueryService` 校验无 SETTLED | EX-TCM-103 |
| ③ | Pre | `checkMedicationRequestSignOffAllowed`（WITHDRAW）或 `checkMedicationRequestDeletable`（DELETE） | EX-TCM-207 |
| ④ | Cmd | **WITHDRAW**：`rollbackPlanned*` → `signOffMedicationOrder` → void dispense → `releaseDispenseReserve` | 对称 **AS-OP-DOR-003** IF-DOR-16 |
| ⑤ | Cmd | **DELETE**：`voidUnbilledDraft*` → `removeMedicationOrderDraft` | 仅 DRAFT |
| ⑥ | Cmd | **若**整方撤回：void 同 `prescriptionNo` 代煎费 | |

---

### 3.12 IF-DOR-39 `listPatientTcmHerbalPrescriptionHistory`（历史中药处方）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | Long | 是 | |
| currentEncounterId | Long | 是 | 排除当前就诊 |
| keyword | String | 否 | 过滤药品名/处方号 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| historyRows | List | 历史方摘要 |

#### DTO（TcmRxHistoryRowDto）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptionNo | String | 处方号 |
| encounterId | Long | 来源就诊 |
| reqOpenedTime | LocalDateTime | 开立时间（`req_opened_time`；方级聚合；空回落 `req_authored_time`） |
| summaryText | String | 方摘要（药味数+付数） |
| medicationNames | List\<String\> | 药名列表 |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `MedicationRequestQueryService#listTcmHerbalPrescriptionHistoryByPatient(patientId, currentEncounterId)` | 领域层已按 `prescription_no` 聚合 |
| ② | Asm | `TcmRxListAssembler#enrichHistoryGroups` | 补药名、keyword 过滤 |
| ③ | Asm | `DoctorStationTcmMapper#toListPatientTcmRxHistoryResponse` | 只读；套用走 IF-DOR-36 |

---

## 4. QueryService 与领域事件

| 写 IF | 领域事件 | 读侧刷新 |
| --- | --- | --- |
| IF-DOR-32 | `EncounterDiagnosisSavedEvent` / `EncounterDiagnosisAssociationUpsertedEvent` | IF-DOR-30 快照；**非** QueryModel 直写 |
| IF-DOR-33 | `EncounterDiagnosisDeletedEvent` / `EncounterDiagnosisAssociationRemovedEvent` | 同上 |
| IF-DOR-36 | `HerbalMedicationOrderDraftCreated/Edited` | IF-DOR-34 列表 |
| IF-DOR-37 | `MedicationOrderSignedEvent` | IF-DOR-34；收费 PLANNED |
| IF-DOR-38 | `MedicationOrderSignOffEvent` | IF-DOR-34 |

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号（§3） | 聚合写入顺序 |
| --- | --- | --- | --- |
| IF-DOR-32 | saveTcmDiagnosisGroupsBatch | ⑦→⑧→⑩→⑪ | 待删组：Encounter 关联删 → Condition 删；本批保存：Condition upsert → **中医** Encounter 关联 batchUpsertTcm |
| IF-DOR-33 | deleteTcmDiagnosisGroup | ⑤→⑥ | 关联删 → Condition 删 |
| IF-DOR-36 | saveTcmHerbalPrescriptionDraftBatch | 见 §3.8 | MedicationRequest → ChargeItem 草稿 |
| IF-DOR-37 | signTcmHerbalPrescriptionBatch | §3.9 | Request sign → **Support** FIFO+分段 ChargeItem → PLANNED → 代煎 ChargeItem |
| IF-DOR-38 | withdrawOrDelete | §3.10 | Charge 回退 → signOff → void dispense |

### 5.2 事务边界 / 失败回滚

| 策略 | 范围 |
| --- | --- |
| 默认 | 各写 IF 单方法 `@Transactional` 全回滚 |
| IF-DOR-32 删组+保存 | 同一事务；任一步失败整批回滚 |
| IF-DOR-37 | 代煎费与药行签发同事务 |

---

## 6. 非功能性需求实现

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 重复签发同 key | 复用已有 `prescriptionNo` | `TcmRxSignAssembler` | §3.9-③ |

### 6.2 并发控制

以后写为准；可选乐观锁冲突 → EX-TCM-207。

### 6.3 重试与容错

外发无；库存/FIFO 失败不重试。

### 6.4 数据脱敏

无额外 PII；日志不打印完整 `content_json`。

### 6.5 审计日志

`operatorId` 写入领域事件；`signCode` 批次级（目标态字段）。

---

## 7. 测试要点

### 7.1 功能测试（P0）

| 用例 | IF |
| --- | --- |
| 新建 2 组病证 + 主诊唯一 | IF-DOR-32 |
| 有中药引用时删组拒绝 | IF-DOR-33 |
| 2 个 prescriptionGroupKey 同批签发得 2 个处方号 | IF-DOR-37 |
| 代煎=是按付数生成费 | IF-DOR-37 |
| 签发后收银可见 PLANNED 饮片费（分段挂费完整） | IF-DOR-37 |
| IF-DOR-35 响应字段与 IF-DOR-11 同构 | IF-DOR-35 |
| 不调用 splitPrescription | IF-DOR-37 |

### 7.2 非功能测试

IF-DOR-30/34 ≤800ms；IF-DOR-37 ≤3000ms。

### 7.3 查询一致性

IF-DOR-34 与 DB `tcm_flag` 行一致。

### 7.4 集成测试

与 **AS-OP-DOR-003** 西医签发互不影响（**BR-TCM-ISO-001**）。

### 7.5 测试缺口跟踪

| 缺口 | 说明 |
| --- | --- |
| 前后端 E2E | 应用层已编译通过；`tcm-advice.vue` 与 `POST /api/app/doctorStationTcm/*` 联调待前端接入 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.15 | 2026-09-06 | **BR-TCM-RX-009**：`assertDecoctionChargeVoidable` 与补充同口径——按代煎产品收费行 + based_on 反查方号拦待结/已结/退费中；对齐 **AS v1.19** |
| v1.14 | 2026-09-06 | **BR-TCM-RX-009**：代煎退款终态由 **AS-OP-FIN-003** 同步清 `suffering_flag`；有效 companion 仅 DRAFT/ACTIVE；对齐 **AS v1.18** / `OutpatientTcmDecoctionRefundDoctorStationSync` |
| v1.13 | 2026-09-06 | **BR-TCM-RX-009 / IF-DOR-37**：代煎 companion `based_on`→药嘱、不写 SR 方号；对齐 **AS v1.17** / `TcmDecoctionAssembler` |
| v1.12 | 2026-08-02 | **BR-TCM-RX-009 / IF-DOR-36/37**：饮片默认双账户医保户；代煎签发 `account_id`=个人现金自费户；对齐挂号双账户与 **AS v1.16** |
| v1.11 | 2026-08-02 | **BR-TCM-RX-009 纠偏**：曾偏展示覆盖（已由 v1.12 对齐双账户挂账） |
| v1.10 | 2026-08-02 | **IF-DOR-37 / BR-TCM-RX-009**：门诊代煎强制自费（初版曾写开户，已由 v1.11 纠偏） |
| v1.9 | 2026-07-31 | **IF-DOR-37 / BR-TCM-RX-007**：签发分段挂费按 `lotNumber` → `selectExecutionPriceLineForChargeDraft`；`OutpatientMedicationSignChargeOrchestrator` 落点改为 `outpatient.doctorstation.advice.support`；对齐 **AS v1.11** / **AS-OP-DOR-003-SDD v3.43** |
| v1.9 | 2026-08-01 | **IF-DOR-35 / BR-TCM-RX-014**：废止读侧同名合并与 `TcmHerbalCatalogSameNameMergeAssembler`；选品与 IF-DOR-11 同构；签发 FIFO 跨批不变 |
| v1.8 | 2026-07-24 | **IF-DOR-35 / BR-TCM-RX-014**：`TcmHerbalCatalogSameNameMergeAssembler` 当前页同名合并；行增 `manufacturer`/`mergedTotalAvailableQuantity*`；`availableQuantity` 语义为当前批剩余（已被 v1.9 废止） |
| v1.7 | 2026-06-20 | **orchestration 落点对齐**：`OutpatientMedicationSignChargeOrchestrator → `module.orchestration.advice`；§2 包树更新 |（已迁至 application.shared.advice）
| v1.6 | 2026-06-20 | **support 落点对齐**：`OutpatientMedicationSignChargeOrchestrator → `module/orchestration/advice`；对齐 **AS v1.7** |
| v1.5 | 2026-06-17 | **IF-DOR-34（BR-TCM-RX-012）**：`TcmRxLineDto` 增补 `doseUnitName`/`unitName`/`retailUnitPrice`；`TcmRxListAssembler` 批量目录/定价 enrich；对齐 **AS v1.6** / **AS-OP-DOR-003-SDD v3.29** |
| v1.4 | 2026-06-17 | **`initDoctorStationTcm`**：下发 `tcmMethodCodeOptions` + `medicationRateOptions`；**IF-DOR-34** `TcmRxHeaderDto` 增补 `rateCode`/`rateName`；**IF-DOR-36** 方头 `rateCode` 落库；领域 `TcmHerbalPrescriptionGroupQueryModel` 聚合 `rateCode`；**IF-DOR-35** 目录单位口径与 **AS-OP-DOR-003-SDD v3.28** 对齐（包装单位、`defEncounterUnit*` 不下发）；对齐 **AS v1.5** / **DM-MED-003** |
| v1.3 | 2026-06-17 | **IF-DOR-35**：废止 `TcmMedCatalogRowDto`，`PageTcmMedCatalogResponse` 复用 `DoctorStationAdviceCatalogRowDto`；**IF-DOR-37**：签发改调 `OutpatientMedicationSignChargeOrchestrator；**IF-DOR-36**：UPDATE void 带 operator；对齐 **AS v1.4** / **AS-OP-DOR-003-SDD v3.27**。对齐 `zxplat`：目录 source 术语码 `tcm_dx`/`tcm_syndrome`；处方列表 `methodName`（`TcmMethodCodeDisplaysupport）；`EncounterDomainService#assertTcmDiagnosisCollectionRuleByBatchItem` 组级主诊/rank |
| v1.2 | 2026-06-16 | 应用层成码对齐：类名缩短为 `DoctorStationTcm*` / `TcmRx*`；HTTP `doctorStationTcm`；常量 `DoctorStationTcmConstants` 落 `domain.share.constants`；§7.5 调整为 E2E 联调 |
| v1.1 | 2026-06-16 | 领域 Gap 已关闭：IF-DOR-32 改用 `batchUpsertTcmEncounterDiagnosisAssociation`；IF-DOR-34/39 直接调按方 QueryService |
| v1.0 | 2026-06-16 | 初版 SDD：IF-DOR-30～39 目标态全量契约；对齐 AS §3 Gap G-TCM-010-01～03 |

**文档负责人**：（待填）
