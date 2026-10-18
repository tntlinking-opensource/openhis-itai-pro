# 住院医生站 · 中医 · 应用服务详细设计（SDD）

> **编码**：AS-IP-DOR-006-SDD  
> **版本**：v1.6 | **日期**：2026-09-09 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院医生站 · 中医 Tab 应用服务（`inpatient/doctorstation/chinesemedicine`） |
| **边界** | IF-IP-TCM-29～42：中医 Tab 初始化、病证组成组 CRUD、中药处方按 `prescriptionGroupKey` 暂存/签发（**不**调用 `splitPrescription`、**不**建 `ChargeItem`/`MedicationDispense`）、签退/删除、饮片目录、历史处方、**长临筛选**、长期**停嘱/取消停嘱**、**按方改开立时间**；组套消费 **AS-BAS-MST-017**，不在本模块维护 |
| **编排权威** | `AS-IP-DOR-006-住院医生站中医.md`（v1.7）§2 + §4 |
| **方法权威** | `DM-CLN-003` §6.1.1～6.1.3；`DM-ADM-002` §6.1.11～6.1.13、§6.2.19；`DM-CLN-002` §6.2.6～6.2.7；`DM-MED-003` §6.1.4～6.1.8、§6.1.13～6.1.14、§6.2.19；`DM-MED-001` §6.2；`DM-CLN-004` §6.2.6；`DM-MED-002` §6.2（签退门控只读） |
| **严格对齐** | IF **IF-IP-TCM-29～42** 与 AS 一致 |
| **实现阶段口径** | **已落地**（`InpatientDoctorStationTcmAppService` + 契约类）；HTTP 路由 `inpatientDoctorStationTcm`；领域 Gap **G-IP-TCM-001 已关闭**（`listTcmHerbalPrescriptionsByEncounter` 三参版） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 入出参与 AS、DM 一致；持久化枚举存 **String code**；`prescriptionGroupKey` **仅** Request/`content_json` 交互层，**不落**独立库列 |
| **QueryService 口径** | 写前门控：`EncounterContextReader#mustGetInpatientContextByEncounterId` → `InpatientEncounterContextQueryModel`（**DM-ADM-002 §6.2.19**）；IF-IP-TCM-34/39 调用 `MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter(encounterId, generateSource, therapyEnum)` / `#listTcmHerbalPrescriptionHistoryByPatient`；列表 enrich 已挂费优先 **`ChargeItem.unit_price`**（**BR-PRICE-SRC-001** / **BR-TCM-RX-012**，对齐门诊 `TcmRxListAssembler`） |
| **长临分轨** | 方头 `therapyEnum` → `therapy_enum`（创建命令写入，未传回落临时）。**长期**方 `rate_code` **不得**为立即；**临时**方不强制 `STAT`（**BR-IP-TCM-005**） |
| **住院签发链路** | 对齐 **AS-IP-DOR-003 §1.7**：暂存/签发**均仅** `MedicationRequest`；**不** FIFO、**不** `ChargeItem`、**不**代煎费（代煎费归属 **AS-IP-NUR-004**） |
| **事务口径** | 各写 IF 单方法由框架 **AppServiceAspect** 包裹为 UoW（**禁止**应用层 `@Transactional`）；领域层不声明事务 |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/{methodName}`（`AppServiceControllerRegistrar`） |
| **业务异常码** | `BusinessException` + `ErrorCodeConstants`；AS **`EX-IP-TCM-*`** 为语义别名 |

#### 1.2.1 文档语义码与 `ErrorCodeConstants` 对照

| 文档语义码 | 实现 `ErrorCode`（典型触发） |
| --- | --- |
| EX-IP-TCM-101 | `ENCOUNTER_INVALID_ARGUMENT` / 检索失败 |
| EX-IP-TCM-102 | `CONDITION_RULE_CONFLICT`（医嘱引用 > 0） |
| EX-IP-TCM-103 | `MEDICATION_REQUEST_RULE_CONFLICT`（已执行/已发药/不可回退费用） |
| EX-IP-TCM-104 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` |
| EX-IP-TCM-105 | 持久化失败包装 |
| EX-IP-TCM-201～202 | `CONDITION_INVALID_ARGUMENT` |
| EX-IP-TCM-203～210 | `MEDICATION_REQUEST_INVALID_ARGUMENT` / `MEDICATION_REQUEST_RULE_CONFLICT` / `MEDICATION_REQUEST_INVALID_STATUS` |
| EX-IP-TCM-211～213 | `MEDICATION_REQUEST_INVALID_STATUS` / `SERVICE_REQUEST_INVALID_ARGUMENT`（出院门禁） |

#### 1.2.2 与门诊 SDD 的复用关系

| 住院 IF | 门诊母版 SDD | 住院差异摘要 |
| --- | --- | --- |
| IF-IP-TCM-29 | §3.2 `initDoctorStationTcm` | `InpatientEncounterContextQueryModel` 门控；增补 `therapyTypeFilterDefault` |
| IF-IP-TCM-30～33 | §3.3～§3.6 IF-DOR-30～33 | 门控换住院快照；编排同构 |
| IF-IP-TCM-34 | §3.7 IF-DOR-34 | 入参 `therapyTypeCode`；方头 `therapyEnum`；已挂费 **`ChargeItem.unit_price`** enrich |
| IF-IP-TCM-35 | §3.8 IF-DOR-35 | 复用 `InpatientDoctorStationAdviceCatalogAssembler#assemblePageContent`（**BR-TCM-RX-014**：不做同名折叠） |
| IF-IP-TCM-36 | §3.9 IF-DOR-36 | 方头 `therapyEnum` 写入 `CreateHerbalMedicationOrderDraftCommand`；长临仅禁长期立即；CREATE 后按方头再写 `therapy_enum` |
| IF-IP-TCM-37 | §3.10 IF-DOR-37 | **仅** `signMedicationOrder`；**禁止** FIFO/挂费/代煎 |
| IF-IP-TCM-38 | §3.11 IF-DOR-38 | 门控改执行/发药/费用；住院**不** rollback 签发侧 Dispense/Charge |
| IF-IP-TCM-39 | §3.12 IF-DOR-39 | 同构 |
| IF-IP-TCM-40～41 | **AS-IP-DOR-003-SDD** §3.12/§3.12.1 | 按 `prescription_no` **整方**扩行后批量停嘱/取消停嘱 |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```
health-application/src/main/java/com/openhis/health/application/
└── inpatient/
    └── doctorstation/
        └── chinesemedicine/
            ├── service/
            │   └── InpatientDoctorStationTcmAppService.java
            ├── assembler/
            │   ├── InpatientTcmDiagnosisAssembler.java          ← 薄包装：委托注入门诊 TcmDiagnosisAssembler
            │   ├── InpatientTcmRxListAssembler.java             ← 住院列表 enrich（ChargeItem 账单单价优先）
            │   └── InpatientTcmRxSignAssembler.java               ← 取号 + rx_type（禁止 splitPrescription）
            ├── support/
            │   ├── InpatientTcmRxHeaderValidationSupport.java   ← BR-IP-TCM-005/006
            │   ├── InpatientTcmRxRevokeGateSupport.java           ← BR-IP-TCM-009
            │   └── InpatientTcmPrescriptionExpandSupport.java     ← prescription_no 整方扩行
            ├── request/
            │   ├── InitInpatientDoctorStationTcmRequest.java
            │   ├── ListTcmDiagnosisGroupsRequest.java
            │   ├── PageTcmConditionDefinitionRequest.java
            │   ├── TcmDiagnosisGroupItemRequest.java
            │   ├── SaveTcmDiagnosisGroupsBatchRequest.java
            │   ├── DeleteTcmDiagnosisGroupRequest.java
            │   ├── ListTcmHerbalPrescriptionsRequest.java
            │   ├── PageTcmHerbalMedicationCatalogRequest.java
            │   ├── TcmRxDraftHeaderRequest.java
            │   ├── SaveTcmHerbalPrescriptionDraftBatchRequest.java
            │   ├── SignTcmRxTargetRequest.java
            │   ├── SignTcmHerbalPrescriptionBatchRequest.java
            │   ├── WithdrawOrDeleteTcmHerbalPrescriptionRequest.java
            │   ├── ListPatientTcmHerbalPrescriptionHistoryRequest.java
            │   ├── StopLongTermTcmHerbalPrescriptionRequest.java
            │   └── CancelStopLongTermTcmHerbalPrescriptionRequest.java
            ├── response/
            │   ├── InitInpatientDoctorStationTcmResponse.java
            │   ├── ListTcmDiagnosisGroupsResponse.java
            │   ├── PageTcmConditionDefinitionResponse.java
            │   ├── SaveTcmDiagnosisGroupsBatchResponse.java
            │   ├── DeleteTcmDiagnosisGroupResponse.java
            │   ├── ListTcmHerbalPrescriptionsResponse.java
            │   ├── PageTcmHerbalMedicationCatalogResponse.java
            │   ├── SaveTcmHerbalPrescriptionDraftBatchResponse.java
            │   ├── SignTcmHerbalPrescriptionBatchResponse.java
            │   ├── WithdrawOrDeleteTcmHerbalPrescriptionResponse.java
            │   ├── ListPatientTcmHerbalPrescriptionHistoryResponse.java
            │   ├── StopLongTermTcmHerbalPrescriptionResponse.java
            │   └── CancelStopLongTermTcmHerbalPrescriptionResponse.java
            ├── dto/
            │   ├── TcmDiagnosisGroupRowDto.java
            │   ├── TcmDiagnosisCatalogRowDto.java
            │   ├── TcmRxHeaderDto.java
            │   ├── TcmRxLineDto.java
            │   ├── TcmRxDraftLineRequest.java
            │   └── TcmRxHistoryRowDto.java
            └── mapper/
                ├── InpatientDoctorStationTcmMapper.java
                └── InpatientDoctorStationTcmMapperImpl.java

health-application/.../inpatient/doctorstation/advice/
├── assembler/InpatientDoctorStationAdviceCatalogAssembler.java   ← IF-IP-TCM-35
└── dto/InpatientDoctorStationAdviceCatalogRowDto.java            ← IF-IP-TCM-35 行契约

health-application/.../outpatient/doctorstation/chinesemedicine/
├── assembler/TcmDiagnosisAssembler.java                          ← 可选直接注入复用
├── assembler/TcmRxListAssembler.java                             ← 历史方 enrich 可复用子集
└── assembler/TcmRxSignAssembler.java                             ← 取号逻辑参考（住院独立 Impl）

zxplat-domain/.../domain/share/constants/
├── DoctorStationTcmConstants.java                                ← 跨层常量（catalogRole/HERBAL 等）
└── InpatientDoctorStationTcmConstants.java                       ← 已生成：立即频次码、长临筛选默认、EX-IP-TCM 文案
```

**复用（不重复落包）**：

- `querysupport.encounter.EncounterContextReader`
- `application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler`
- `inpatient.doctorstation.advice.assembler.InpatientDoctorStationAdviceCatalogAssembler`（IF-IP-TCM-35）
- `inpatient.doctorstation.advice.dto.InpatientDoctorStationAdviceCatalogRowDto`（IF-IP-TCM-35，与 **IF-IP-DOR-11** 同构）
- `module.common.assembler.option.FrequencyOptionLoader`
- `module.common.assembler.product.ClinicalCatalogPriceResolvesupport
- `application.shared.product.ProductDefinitionCatalogPageLoader` / `ProductDefinitionBatchLoader`

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 来源 |
| --- | --- | --- | --- |
| 应用服务 | `InpatientDoctorStationTcmAppService` | IF-IP-TCM-29～41 编排 | 本模块（**已落地**） |
| 领域服务 | `ConditionDomainService` | 病证事实写 | domain.clinical.condition |
| 领域服务 | `EncounterDomainService` | 就诊诊断关联写 | domain.administration.encounter |
| 领域服务 | `MedicationRequestDomainService` | 中药草稿/签发/签退/停嘱 | domain.medication.medicationrequest |
| Facade | `EncounterContextReader` | 住院就诊门控 | querysupport.encounter |
| Bridge | `EncounterDiagnosisSnapshotAssembler` | IF-IP-TCM-30 诊断 JSON 真源 | application.shared.diagnosis |
| 查询服务 | `ConditionDefinitionQueryService` | IF-IP-TCM-31 | domain.clinical.conditiondefinition |
| 查询服务 | `ConditionQueryService` | `countAdviceRefByConditionId` | domain.clinical.condition |
| 查询服务 | `MedicationRequestQueryService` | 列表/历史/签退删除门控/按方扩行 | domain.medication.medicationrequest |
| 查询服务 | `MedicationDispenseQueryService` | 签退门控：已发药 | domain.medication.medicationdispense |
| 查询服务 | `ChargeItemQueryService` | 签退门控：不可回退费用 | domain.administration.chargeitem |
| 查询服务 | `ProcedureQueryService` | 签退门控：净执行 | domain.clinical.procedure |
| 查询服务 | `ServiceRequestQueryService` | IF-IP-TCM-41 出院门禁 | domain.workflow.servicerequest |
| 查询服务 | `CodeSystemQueryService` | IF-IP-TCM-29 中药用法 | domain.terminology |
| Loader | `ProductDefinitionCatalogPageLoader` | IF-IP-TCM-35 目录分页 | querysupport.product |
| Loader | `ProductDefinitionBatchLoader` | IF-IP-TCM-34 目录名/单位 | querysupport.product |
| 基础设施 | `BusinessSequenceService` | `tcm_syndrome_group`、处方号 | zxplat-framework |
| 操作人 | `CurrentOperatorResolver` | 写路径 operatorId | module.common.validator |
| Mapper | `InpatientDoctorStationTcmMapper` + `Impl` | Request/Command/Response | 本模块 |
| Assembler | `InpatientDoctorStationAdviceCatalogAssembler` | IF-IP-TCM-35（库存预减 + 定价，不做同名折叠） | advice 模块 |
| Support | `InpatientTcmRxHeaderValidationsupport 等 | 长临/方头/签退门控 | 本模块 support |

> **刻意不注入（住院中医本期）**：`MedicationDispenseDomainService`、`ChargeItemDomainService`、`AccountQueryService`、`OutpatientMedicationSignChargesupport、`TcmDecoctionAssembler`——对齐 **BR-IP-TCM-003**。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 长临医嘱 | `com.zxplat.domain.share.enums.TherapyType` | `DM-MED-003` §3 `therapy_enum` | IF-IP-TCM-34/36/37/40；`LONG_TERM`/`TEMPORARY` |
| 医嘱生成来源 | `com.zxplat.domain.share.enums.GenerateSource` | `DM-MED-003` §4.5 | 写/列表固定 `DOCTOR_PRESCRIPTION` |
| 请求状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-MED-003` §5 状态机 | 草稿/签发/校对/停嘱 |
| 中医目录 source | `DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM` | `DM-CLN-002` §4.1 | IF-IP-TCM-31 |
| catalogRole | `PageTcmConditionDefinitionRequest.catalogRole` | AS-IP-DOR-006 §1.3 | `DISEASE`/`SYNDROME` |
| 饮片分类 | `DoctorStationTcmConstants.HERBAL_CATEGORY_CODE` | `DM-MED-001` 中草药 | IF-IP-TCM-35；固定 `4` |
| 临嘱立即频次 | `InpatientDoctorStationTcmConstants.IMMEDIATE_RATE_CODE` | AS **BR-IP-TCM-005**；频次术语 `STAT` | 已生成；值 `STAT`（新建临嘱默认，**非强制**） |
| 长临筛选默认 | `InitInpatientDoctorStationTcmResponse.therapyTypeFilterDefault` | AS IF-IP-TCM-29 | 默认 `LONG_TERM` |
| 列表 therapy 筛选 | `ListTcmHerbalPrescriptionsRequest.therapyTypeCode` | AS **BR-IP-ORD-011** | 映射 `TherapyType#getCode()` |
| 方头 therapy | `TcmRxHeaderDto.therapyEnum` | `DM-MED-003` §3 `therapy_enum` | IF-IP-TCM-36/34 |
| 处方类别 rx_type | `MedicationRequest.rxTypeCode` | `DM-MED-003` §6.1.15；AS **BR-IP-TCM-007** | IF-IP-TCM-37；`PrescriptionSplitConstants.RX_TYPE_*_HERBAL` |
| 代煎标志 | `TcmRxHeaderDto.sufferingFlag` | `DM-MED-003` §3 `suffering_flag` | IF-IP-TCM-36；签发**不**建代煎费 |
| 中药付数 | `TcmRxHeaderDto.chineseHerbsDoseQuantity` | `DM-MED-003` §3 | 数量重算 **BR-TCM-RX-005** |
| 签退动作 | `DoctorStationTcmConstants.ACTION_WITHDRAW` | AS IF-IP-TCM-38 | `WITHDRAW`/`DELETE` |
| 整方范围 | `DoctorStationTcmConstants.SCOPE_PRESCRIPTION` | AS IF-IP-TCM-38/40/41 | 按 `prescription_no` 扩行 |

### 2.4 领域方法白名单

| 聚合/查询 | 方法 | 用于 IF |
| --- | --- | --- |
| ConditionDomainService | `saveDoctorDiagnosisTcm(SaveDoctorDiagnosisTcmCommand)` | IF-IP-TCM-32 |
| ConditionDomainService | `delEncounterDiagnosis(DelEncounterDiagnosisCommand)` | IF-IP-TCM-33 |
| EncounterDomainService | `batchUpsertTcmEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand)` | IF-IP-TCM-32 |
| EncounterDomainService | `removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand)` | IF-IP-TCM-33 |
| MedicationRequestDomainService | `createHerbalMedicationOrderDraft(CreateHerbalMedicationOrderDraftCommand)` | IF-IP-TCM-36 |
| MedicationRequestDomainService | `changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand)` | IF-IP-TCM-36/37 |
| MedicationRequestDomainService | `changeMedicationOrderDraft(ChangeMedicationOrderDraftCommand)` | IF-IP-TCM-36（写 `therapy_enum`） |
| MedicationRequestDomainService | `removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand)` | IF-IP-TCM-38 |
| MedicationRequestDomainService | `signMedicationOrder(SignMedicationOrderCommand)` | IF-IP-TCM-37 |
| MedicationRequestDomainService | `signOffMedicationOrder(SignOffMedicationOrderCommand)` | IF-IP-TCM-38 |
| MedicationRequestDomainService | `rejectMedicationOrder(RejectMedicationOrderCommand)` | IF-IP-TCM-38（**VERIFIED** 已校对未执行路径） |
| MedicationRequestDomainService | `stopLongTermMedicationOrder(StopLongTermMedicationOrderCommand)` | IF-IP-TCM-40 |
| MedicationRequestDomainService | `cancelStopLongTermMedicationOrder(CancelStopLongTermMedicationOrderCommand)` | IF-IP-TCM-41 |
| ConditionDefinitionQueryService | `pageActiveDoctorStationDefinitions(...)` | IF-IP-TCM-31 |
| MedicationRequestQueryService | `listTcmHerbalPrescriptionsByEncounter(Long, String, String)` | IF-IP-TCM-34 |
| MedicationRequestQueryService | `listTcmHerbalPrescriptionHistoryByPatient` | IF-IP-TCM-39 |
| MedicationRequestQueryService | `loadMedicationRequestById` / `listByIds` | IF-IP-TCM-37/38/40/41 |
| MedicationRequestQueryService | `checkMedicationRequestSignOffAllowed` / `checkMedicationRequestDeletable` | IF-IP-TCM-38 |
| ConditionQueryService | `countAdviceRefByConditionId` | IF-IP-TCM-33 |
| MedicationDispenseQueryService | `listByMedReqIds` | IF-IP-TCM-38 门控 |
| ChargeItemQueryService | `listChargeItemsByEncounterId` 等 | IF-IP-TCM-38 门控 |
| ProcedureQueryService | `listNetRemainingCompletedProcedureRecords` | IF-IP-TCM-38 门控 |
| ServiceRequestQueryService | `checkDuplicateTransferOrDischargeRequest` | IF-IP-TCM-41 |
| EncounterContextReader | `mustGetInpatientContextByEncounterId` | IF-IP-TCM-29～41 写/关键读 |

> **白名单说明**：**禁止** IF-IP-TCM-37 调用 `splitPrescription`（**BR-TCM-ISO-003**）。**禁止** IF-IP-TCM-32 调用 `batchUpsertEncounterDiagnosisAssociation`（全量中医批次须 `batchUpsertTcmEncounterDiagnosisAssociation`）。

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-TCM-29 | `initInpatientDoctorStationTcm` | 中医 Tab 页初始化 | `POST /api/app/inpatientDoctorStationTcm/initInpatientDoctorStationTcm` |
| IF-IP-TCM-30 | `listTcmDiagnosisGroups` | 加载中医病证组列表 | `POST /api/app/inpatientDoctorStationTcm/listTcmDiagnosisGroups` |
| IF-IP-TCM-31 | `pageTcmConditionDefinitionCatalog` | 分页检索中医病名/证候 | `POST /api/app/inpatientDoctorStationTcm/pageTcmConditionDefinitionCatalog` |
| IF-IP-TCM-32 | `saveTcmDiagnosisGroupsBatch` | 全量保存病证组 | `POST /api/app/inpatientDoctorStationTcm/saveTcmDiagnosisGroupsBatch` |
| IF-IP-TCM-33 | `deleteTcmDiagnosisGroup` | 删除病证组 | `POST /api/app/inpatientDoctorStationTcm/deleteTcmDiagnosisGroup` |
| IF-IP-TCM-34 | `listTcmHerbalPrescriptions` | 加载中药处方列表 | `POST /api/app/inpatientDoctorStationTcm/listTcmHerbalPrescriptions` |
| IF-IP-TCM-35 | `pageTcmHerbalMedicationCatalog` | 分页检索中药饮片 | `POST /api/app/inpatientDoctorStationTcm/pageTcmHerbalMedicationCatalog` |
| IF-IP-TCM-36 | `saveTcmHerbalPrescriptionDraftBatch` | 暂存中药处方 | `POST /api/app/inpatientDoctorStationTcm/saveTcmHerbalPrescriptionDraftBatch` |
| IF-IP-TCM-37 | `signTcmHerbalPrescriptionBatch` | 签发中药处方 | `POST /api/app/inpatientDoctorStationTcm/signTcmHerbalPrescriptionBatch` |
| IF-IP-TCM-38 | `withdrawOrDeleteTcmHerbalPrescription` | 签退/删除中药处方 | `POST /api/app/inpatientDoctorStationTcm/withdrawOrDeleteTcmHerbalPrescription` |
| IF-IP-TCM-39 | `listPatientTcmHerbalPrescriptionHistory` | 查询患者历史中药处方 | `POST /api/app/inpatientDoctorStationTcm/listPatientTcmHerbalPrescriptionHistory` |
| IF-IP-TCM-40 | `stopLongTermTcmHerbalPrescription` | 停嘱（长期中药方） | `POST /api/app/inpatientDoctorStationTcm/stopLongTermTcmHerbalPrescription` |
| IF-IP-TCM-41 | `cancelStopLongTermTcmHerbalPrescription` | 取消停嘱（长期中药方） | `POST /api/app/inpatientDoctorStationTcm/cancelStopLongTermTcmHerbalPrescription` |
| IF-IP-TCM-42 | `changeTcmHerbalPrescriptionOpenedTime` | 按方改开立时间 | `POST /api/app/inpatientDoctorStationTcm/changeTcmHerbalPrescriptionOpenedTime` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数/权限/患者就诊一致性 | 不再调用后续步骤 |
| **Pre** | 只读：快照、引用计数、状态与 BR 门禁 | 抛异常，**禁止 Cmd** |
| **Cmd** | 领域写 + 事件 | 事务回滚 §5 |
| **Asm** | Mapper/Assembler 纯装配 | — |

**记法**：`EncounterContextReader#mustGetInpatientContextByEncounterId` 表示注入 Bean 调用。

**重要说明**：先读完各 IF「领域编排序列」，按序号从上到下执行。

---

### 3.2 IF-IP-TCM-29 `initInpatientDoctorStationTcm`（中医 Tab 页初始化）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#initInpatientDoctorStationTcm` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/initInpatientDoctorStationTcm` |

#### Request 字段表（`InitInpatientDoctorStationTcmRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识；须与住院快照一致 |

#### Response 字段表（`InitInpatientDoctorStationTcmResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| tcmMethodCodeOptions | List\<OptionDto\> | 中药用法（`tcm_method_code`） |
| medicationRateOptions | List\<OptionDto\> | 用药频次（与 **IF-IP-DOR-18** 同源） |
| therapyTypeFilterDefault | String | 长临筛选默认值；固定 `LONG_TERM` |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId(encounterId)` | encounterId | IMP 门控 |
| ② | Gate | `InpatientDoctorStationTcmMapper#validatePatientEncounter(snapshot, patientId)` | patientId | EX-IP-TCM-104 |
| ③ | Pre | `CodeSystemQueryService#getOptionListByCodeSystemName(TCM_METHOD_CODE)` | — | 中药用法 |
| ④ | Asm | `FrequencyOptionLoader#loadActiveRateOptions` | — | 含 `STAT` 立即执行 |
| ⑤ | Asm | `InpatientDoctorStationTcmMapper#toInitInpatientDoctorStationTcmResponse(...)` | ③④ | 写入 `therapyTypeFilterDefault=LONG_TERM`；仅读 |

---

### 3.3 IF-IP-TCM-30 `listTcmDiagnosisGroups`（加载中医病证组列表）

> **母版**：`AS-OP-DOR-010-SDD` §3.3 IF-DOR-30；住院仅替换门控为 `mustGetInpatientContextByEncounterId`。

#### Request 字段表

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |

#### Response 字段表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| groups | List\<TcmDiagnosisGroupRowDto\> | 病证组列表 |
| totalCount | Integer | 组数 |

#### DTO 字段表（`TcmDiagnosisGroupRowDto`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| tcmSyndromeGroup | String | 组号 |
| diseaseConditionId | Long | 病名事实 id |
| diseaseDefinitionId | Long | 病名目录 id |
| diseaseCode | String | 病名编码 |
| diseaseName | String | 病名展示 |
| syndromeConditionId | Long | 证候事实 id |
| syndromeDefinitionId | Long | 证候目录 id |
| syndromeCode | String | 证候编码 |
| syndromeName | String | 证候展示 |
| primaryFlag | Integer | 主诊 1/0 |
| rank | Integer | 排序 |
| medicalTypeCode | String | 医疗类型 |
| diagnosisDesc | String | 组级描述 |

#### 失败码口径表

| 失败码 | 场景 | 实现 ErrorCode |
| --- | --- | --- |
| EX-IP-TCM-104 | 就诊/患者不匹配 | `ENCOUNTER_INVALID_ARGUMENT` |
| EX-IP-TCM-101 | 快照失败 | `ENCOUNTER_INVALID_ARGUMENT` |

#### Mapper 方法签名

- `InpatientDoctorStationTcmMapper#validatePatientEncounter(InpatientEncounterContextQueryModel, Long): void`
- `TcmDiagnosisAssembler#groupTcmDiagnosisRows(String diagnosisJson): List<TcmDiagnosisGroupRowDto>`
- `InpatientDoctorStationTcmMapper#toListTcmDiagnosisGroupsResponse(List<TcmDiagnosisGroupRowDto>): ListTcmDiagnosisGroupsResponse`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | encounterId | 住院快照 |
| ② | Gate | `InpatientDoctorStationTcmMapper#validatePatientEncounter` | patientId | |
| ③ | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot(encounterId).diagnosisJson()` | encounterId | |
| ④ | Asm | `TcmDiagnosisAssembler#groupTcmDiagnosisRows` | ③ JSON | 过滤 TCM + 按组聚合 |
| ⑤ | Asm | `InpatientDoctorStationTcmMapper#toListTcmDiagnosisGroupsResponse` | ④ | 仅读 |

---

### 3.4 IF-IP-TCM-31 `pageTcmConditionDefinitionCatalog`（分页检索中医病名/证候）

> **母版**：`AS-OP-DOR-010-SDD` §3.4 IF-DOR-31；编排同构，HTTP 路径换住院服务名。

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
| rows | List\<TcmDiagnosisCatalogRowDto\> | 目录行 |
| total | Long | 总条数 |

#### DTO 字段表（`TcmDiagnosisCatalogRowDto`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| definitionId | Long | 目录 id |
| conditionCode | String | 编码 |
| conditionName | String | 名称 |
| sourceCode | String | `tcm_dx`/`tcm_syndrome` |
| sourceText | String | 来源展示 |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-201 | `catalogRole` 缺失或非法 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | AppService 解析 `catalogRole` → source 列表 | `DISEASE`→`tcm_dx`；`SYNDROME`→`tcm_syndrome`；缺失 → EX-IP-TCM-201 |
| ② | Pre | `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions(...)` | 仅 ACTIVE |
| ③ | Asm | `InpatientDoctorStationTcmMapper#toPageTcmConditionDefinitionResponse` | 仅读 |

---

### 3.5 IF-IP-TCM-32 `saveTcmDiagnosisGroupsBatch`（全量保存病证组）

> **母版**：`AS-OP-DOR-010-SDD` §3.5 IF-DOR-32；步骤 ①～② 换住院门控，⑦～⑪ 同构。

#### Request 字段表（`SaveTcmDiagnosisGroupsBatchRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| groups | List\<TcmDiagnosisGroupItemRequest\> | 是 | 全量组；空列表拒绝 |

**groups[] 元素（`TcmDiagnosisGroupItemRequest`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| tcmSyndromeGroup | String | 否 | 空=新建；非空=更新 |
| diseaseDefinitionId | Long | 是 | 病名目录 |
| diseaseConditionId | Long | 否 | 更新时病名事实 id |
| syndromeDefinitionId | Long | 是 | 证候目录 |
| syndromeConditionId | Long | 否 | 更新时证候事实 id |
| primaryFlag | Integer | 是 | 组级主诊 |
| rank | Integer | 是 | 组级排序 |
| medicalTypeCode | String | 否 | 医疗类型 |
| diagnosisDesc | String | 否 | 描述 |

#### Response 字段表（`SaveTcmDiagnosisGroupsBatchResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 成功 |
| savedGroupKeys | List\<String\> | 组号列表 |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-201 | 缺一病一证 |
| EX-IP-TCM-202 | 主诊组 >1 |
| EX-IP-TCM-105 | 持久化失败 |

#### Mapper 方法签名

- `toSaveDoctorDiagnosisTcmCommand(SaveTcmDiagnosisGroupsBatchRequest, Long operatorId): SaveDoctorDiagnosisTcmCommand`
- `toBatchUpsertTcmEncounterDiagnosisAssociationCommand(...): BatchUpsertEncounterDiagnosisAssociationCommand`
- `diffRemovedTcmGroups(List<TcmDiagnosisGroupRowDto>, SaveTcmDiagnosisGroupsBatchRequest): List<String>`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | **BR-IP-TCM-001** |
| ② | Gate | `validatePatientEncounter` | |
| ③ | Pre | `EncounterDiagnosisSnapshotAssembler#buildSnapshot` + 过滤 TCM 组 | diff 基线 |
| ④ | Gate | 校验每组 1 病+1 证、主诊≤1 | EX-IP-TCM-201/202 |
| ⑤ | Asm | `diffRemovedTcmGroups` | 待删组号 |
| ⑥ | Pre | 待删组：`ConditionQueryService#countAdviceRefByConditionId` ×2 | >0 → EX-IP-TCM-102 |
| ⑦ | Cmd | **若**⑤非空：逐组 `EncounterDomainService#removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand)` | **写库点-1a** |
| ⑧ | Cmd | **若**⑤非空：逐组 `ConditionDomainService#delEncounterDiagnosis(DelEncounterDiagnosisCommand)` ×2 | **写库点-1b** |
| ⑨ | Asm | 新组生成 `tcmSyndromeGroup`（`BusinessSequenceService`） | |
| ⑩ | Cmd | `ConditionDomainService#saveDoctorDiagnosisTcm(SaveDoctorDiagnosisTcmCommand)` | **写库点-2** |
| ⑪ | Cmd | `EncounterDomainService#batchUpsertTcmEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand)` | **写库点-3**；仅 `tcm_flag=true` |
| ⑫ | Asm | `toSaveTcmDiagnosisGroupsBatchResponse` | |

> **互斥**：⑦～⑧ **仅当** ⑤ 产出非空待删组；否则跳至 ⑨。

---

### 3.6 IF-IP-TCM-33 `deleteTcmDiagnosisGroup`（删除病证组）

> **母版**：`AS-OP-DOR-010-SDD` §3.6 IF-DOR-33。

#### Request 字段表（`DeleteTcmDiagnosisGroupRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| tcmSyndromeGroup | String | 是 | 组号 |

#### Response 字段表（`DeleteTcmDiagnosisGroupResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 成功 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ①～② | Pre/Gate | 住院门控 + `validatePatientEncounter` | **BR-IP-TCM-001** |
| ③ | Pre | 快照解析组内 2 个 `conditionId` | 组不存在 → 提示刷新 |
| ④ | Pre | `countAdviceRefByConditionId` ×2 | EX-IP-TCM-102 |
| ⑤ | Cmd | `removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand)` | **写库点-1** |
| ⑥ | Cmd | `delEncounterDiagnosis(DelEncounterDiagnosisCommand)` ×2 | **写库点-2** |

---

### 3.7 IF-IP-TCM-34 `listTcmHerbalPrescriptions`（加载中药处方列表）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#listTcmHerbalPrescriptions` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/listTcmHerbalPrescriptions` |

#### Request 字段表（`ListTcmHerbalPrescriptionsRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| therapyTypeCode | String | 否 | `LONG_TERM`/`TEMPORARY`；空=全部 |

#### Response 字段表（`ListTcmHerbalPrescriptionsResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptions | List\<TcmRxHeaderDto\> | 按方聚合 |

#### DTO 字段表（`TcmRxHeaderDto`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptionGroupKey | String | 自 `content_json` |
| prescriptionNo | String | 已签发时有值 |
| therapyEnum | String | 方头长临（**BR-IP-TCM-005**） |
| tcmSyndromeGroup | String | 绑定病证组 |
| chineseHerbsDoseQuantity | BigDecimal | 付数 |
| sufferingFlag | Boolean | 代煎 |
| methodCode | String | 用法 |
| methodName | String | 用法展示 |
| rateCode | String | 频次编码 |
| rateName | String | 频次展示 |
| days | Integer | 天数 |
| decoctionMethodCode | String | 煎煮法 |
| remark | String | 备注 |
| signCode | String | 签发批次码 |
| statusEnum | String | 方状态（行内最高态） |
| lines | List\<TcmRxLineDto\> | 明细 |

#### DTO 字段表（`TcmRxLineDto`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| medicationRequestId | Long | 行 id |
| medicationDefinitionId | Long | 饮片目录 |
| medicationName | String | 展示 |
| dose | BigDecimal | 单剂用量 |
| doseUnitCode | String | 单剂单位编码 |
| doseUnitName | String | 单剂单位展示名 |
| quantity | BigDecimal | 单剂×付数 |
| unitCode | String | 数量单位编码 |
| unitName | String | 数量单位展示名 |
| retailUnitPrice | BigDecimal | 单剂用量单位零售价：已挂费 **`ChargeItem.unit_price`**，否则 DOSE 槽目录价（**BR-PRICE-SRC-001** / **BR-TCM-RX-012**） |
| sortNumber | Integer | 味序 |
| lineStatusEnum | String | DRAFT/ACTIVE/VERIFIED/STOPPED 等 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | encounterId | |
| ② | Gate | `validatePatientEncounter` | patientId | |
| ③ | Gate | 解析 `therapyTypeCode` → `therapyEnum` 或 null | Request | 非法值拒绝 |
| ④ | Pre | `MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter(encounterId, DOCTOR_PRESCRIPTION, therapyEnum)` | ③ | **G-IP-TCM-001 已关闭** |
| ⑤ | Asm | `InpatientTcmRxListAssembler#enrichInpatientPrescriptionGroups` | ④ + `ChargeItemQueryService` + `ProductDefinitionBatchLoader` + `ClinicalCatalogPriceResolvesupport | 已挂费 **`ChargeItem.unit_price`** 优先；草稿回落 DOSE 槽 |
| ⑥ | Asm | `InpatientDoctorStationTcmMapper#toListTcmHerbalPrescriptionsResponse` | ⑤ | 仅读 |

---

### 3.8 IF-IP-TCM-35 `pageTcmHerbalMedicationCatalog`（分页检索饮片）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#pageTcmHerbalMedicationCatalog` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/pageTcmHerbalMedicationCatalog` |

#### Request 字段表（`PageTcmHerbalMedicationCatalogRequest`，继承 `AbstractHealthPageRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 定价/库存上下文 |
| patientId | Long | 是 | 门控 |
| keyword | String | 否 | 关键字 |
| pageNo | Integer | 是 | 页码 |
| pageSize | Integer | 是 | 页大小 |
| prepickedProductKeys | List\<String\> | 否 | 组套预置键 |

#### Response 字段表（`PageTcmHerbalMedicationCatalogResponse`，继承 `PagedResult<InpatientDoctorStationAdviceCatalogRowDto>`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rows | List\<InpatientDoctorStationAdviceCatalogRowDto\> | 与 **IF-IP-DOR-11** 同构（**BR-TCM-RX-014**：不做同名折叠） |
| total | Long | 目录分页总条数（与西药同口径） |

#### DTO 字段表（`InpatientDoctorStationAdviceCatalogRowDto`，复用 IF-IP-DOR-11）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| productTableName | String | 固定 `med_medication_definition` |
| productDefinitionId | Long | 真实饮片目录 id |
| productName / name | String | 名称 |
| categoryCode | String | 强制中草药 `4` |
| manufacturer | String | 该目录厂家/产地 |
| unitCode | String | 包装单位编码 |
| unitName | String | 包装单位展示名 |
| unitBands | List\<ProductDefinitionCatalogUnitBandDto\> | 单位带 |
| unitPrice | BigDecimal | 与首条库存批号对齐的批号售价 |
| defaultDispenseLocationId | Long | 默认库位 |
| inventoryList | List | 预减后库存行 |
| availableQuantity | BigDecimal | 该目录各批净可用合计 |

#### Mapper 方法签名

- `InpatientDoctorStationTcmMapper#toPageInpatientDoctorStationAdviceCatalogRequest(PageTcmHerbalMedicationCatalogRequest): PageInpatientDoctorStationAdviceCatalogRequest`
- `InpatientDoctorStationAdviceMapper#toProductDefinitionPageQuery(...): ProductDefinitionPageQuery`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | |
| ② | Gate | `validatePatientEncounter` | |
| ③ | Asm | `toProductDefinitionPageQuery` + `setCategoryCode(HERBAL)` | **BR-TCM-RX-003** |
| ④ | Pre | `ProductDefinitionCatalogPageLoader#pageProductDefinition` | |
| ⑤ | Asm | `InpatientDoctorStationAdviceCatalogAssembler#assemblePageContent` | 与西药住院同构库存预减与定价 |
| ⑥ | Asm | `PagedResultBuilder#build` | 仅读；不做同名折叠（**BR-TCM-RX-014**） |

---

### 3.9 IF-IP-TCM-36 `saveTcmHerbalPrescriptionDraftBatch`（暂存中药处方）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#saveTcmHerbalPrescriptionDraftBatch` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/saveTcmHerbalPrescriptionDraftBatch` |

#### Request 字段表（`SaveTcmHerbalPrescriptionDraftBatchRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| prescriptions | List\<TcmRxDraftHeaderRequest\> | 是 | 一张或多张方 |

**prescriptions[] 方头（`TcmRxDraftHeaderRequest`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| prescriptionGroupKey | String | 是 | 前端方标识 |
| therapyEnum | String | 是 | `LONG_TERM`/`TEMPORARY` |
| tcmSyndromeGroup | String | 是 | 绑定病证组 |
| chineseHerbsDoseQuantity | BigDecimal | 是 | 付数 |
| sufferingFlag | Boolean | 否 | 代煎 |
| methodCode | String | 否 | 用法 |
| rateCode | String | 否 | 频次 |
| days | Integer | 否 | 天数 |
| decoctionMethodCode | String | 否 | 煎煮法 |
| remark | String | 否 | 备注 |
| lines | List\<TcmRxDraftLineRequest\> | 是 | 明细 |

**lines[]（`TcmRxDraftLineRequest`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| lineOp | String | 是 | `CREATE`/`UPDATE`/`DELETE` |
| medicationRequestId | Long | 条件 | UPDATE/DELETE 必填 |
| medicationDefinitionId | Long | 条件 | CREATE/UPDATE 必填 |
| dose | BigDecimal | 条件 | 单剂用量 |
| doseUnitCode | String | 条件 | 单剂单位 |
| sortNumber | Integer | 否 | 味序 |
| dispenseLocationId | Long | 否 | 发药库位（可选） |

#### Response 字段表（`SaveTcmHerbalPrescriptionDraftBatchResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| newMedicationRequestIds | List\<Long\> | 新建行 id |
| message | String | |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-203 | 未绑病证组 |
| EX-IP-TCM-208 | 存量：临嘱频次非立即（后端已不拒绝） |
| EX-IP-TCM-209 | 同方方头不一致 |
| EX-IP-TCM-210 | 长期方频次为立即 |
| EX-IP-TCM-105 | 持久化失败 |

#### Mapper 方法签名

- `toCreateHerbalMedicationOrderDraftCommand(...): CreateHerbalMedicationOrderDraftCommand`
- `toChangeHerbalMedicationOrderDraftCommand(...): ChangeHerbalMedicationOrderDraftCommand`
- `toChangeMedicationOrderDraftForTherapy(Long id, String therapyEnum, Long operatorId): ChangeMedicationOrderDraftCommand`
- `buildHerbalContentJson(TcmRxDraftHeaderRequest, TcmRxDraftLineRequest): String`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | **BR-IP-TCM-001** |
| ② | Gate | `validatePatientEncounter` | |
| ③ | Pre | 校验 `tcmSyndromeGroup` 在 IF-IP-TCM-30 结果中存在 | EX-IP-TCM-203 |
| ④ | Gate | `InpatientTcmRxHeaderValidationSupport#validateTherapyAndRateAndHeaderConsistency` | **BR-IP-TCM-005/006**；209/210（208 不再拒绝） |
| ⑤ | Gate | 目录行均为中草药 category | **BR-TCM-RX-003** |
| ⑥ | Cmd | **CREATE 分支**：`createHerbalMedicationOrderDraft(CreateHerbalMedicationOrderDraftCommand)` | **写库点-1**；命令带方头 `therapyEnum`，未传领域回落临时 |
| ⑦ | Cmd | **CREATE 分支**：`changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand)` | 方头/明细/数量 |
| ⑧ | Cmd | **CREATE 分支**：**若**方头 `therapyEnum` 非空：`changeMedicationOrderDraft(ChangeMedicationOrderDraftCommand)` 再写 `therapy_enum`（含临时） | **写库点-1 续** |
| ⑨ | Cmd | **UPDATE 分支**：`changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand)` → **若**需改长临：`changeMedicationOrderDraft` | **写库点-2** |
| ⑩ | Cmd | **DELETE 分支**：`removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand)` | 仅 DRAFT |
| ⑪ | Asm | `toSaveTcmHerbalPrescriptionDraftBatchResponse` | **不**写 ChargeItem（**BR-IP-TCM-003**） |

> **互斥**：⑥～⑧ / ⑨ / ⑩ 按 `lineOp` 三选一；**禁止**串行执行全部分支。

---

### 3.10 IF-IP-TCM-37 `signTcmHerbalPrescriptionBatch`（签发中药处方）

#### Request 字段表（`SignTcmHerbalPrescriptionBatchRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| signTargets | List\<SignTcmRxTargetRequest\> | 是 | 含 `prescriptionGroupKey` + `medicationRequestId` |

**signTargets[]（`SignTcmRxTargetRequest`）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| prescriptionGroupKey | String | 是 | 方标识 |
| medicationRequestId | Long | 是 | 待签发行 |

#### Response 字段表（`SignTcmHerbalPrescriptionBatchResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| prescriptionNoByGroupKey | Map\<String,String\> | key → 处方号 |
| signCode | String | 批次签发码 |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-205 | 含非 `tcm_flag` 行 |
| EX-IP-TCM-208/209/210 | 长临/方头校验失败 |
| EX-IP-TCM-105 | 持久化失败 |

#### Mapper 方法签名

- `toSignMedicationOrderCommand(SignTcmRxTargetRequest, String prescriptionNo, String signCode, Long operatorId): SignMedicationOrderCommand`
- `toChangeHerbalMedicationOrderDraftForRxType(...): ChangeHerbalMedicationOrderDraftCommand`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre/Gate | 住院门控 + `validatePatientEncounter` | **BR-IP-TCM-001** |
| ② | Pre | 加载待签行；断言 `tcmFlag=true`、状态 **DRAFT** | EX-IP-TCM-205 |
| ③ | Gate | `InpatientTcmRxHeaderValidationSupport#validateTherapyAndRateAndHeaderConsistency` | 按 groupKey 分组校验 |
| ④ | Asm | `InpatientTcmRxSignAssembler#resolvePrescriptionNoByGroupKey` | 按 key 取/生成 `prescription_no`；**禁止** `splitPrescription` |
| ⑤ | Asm | `InpatientTcmRxSignAssembler#deriveInpatientHerbalRxTypeCode(medType)` | **BR-IP-TCM-007** |
| ⑥ | Cmd | 预写：`changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand)` 写 `rxTypeCode`/`prescriptionNo` | |
| ⑦ | Cmd | 逐行 `signMedicationOrder(SignMedicationOrderCommand)` | **写库点-1**；→ **ACTIVE** |
| ⑧ | Asm | `toSignTcmHerbalPrescriptionBatchResponse` | **不**建 Dispense/Charge/代煎（**BR-IP-TCM-003/008**） |

---

### 3.11 IF-IP-TCM-38 `withdrawOrDeleteTcmHerbalPrescription`（签退/删除中药处方）

#### Request 字段表（`WithdrawOrDeleteTcmHerbalPrescriptionRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| action | String | 是 | `WITHDRAW`/`DELETE` |
| scope | String | 是 | `PRESCRIPTION`/`LINE` |
| medicationRequestIds | List\<Long\> | 是 | 目标行 |
| prescriptionGroupKey | String | 条件 | 整方时携带 |
| prescriptionNo | String | 条件 | 整方已签发时携带 |

#### Response 字段表（`WithdrawOrDeleteTcmHerbalPrescriptionResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-103 | 已执行/已发药/不可回退费用 |
| EX-IP-TCM-207 | 状态不允许 |

#### Mapper 方法签名

- `toSignOffMedicationOrderCommand(Long requestId, Long operatorId): SignOffMedicationOrderCommand`
- `toRejectMedicationOrderCommand(Long requestId, Long operatorId, String reason): RejectMedicationOrderCommand`
- `toRemoveMedicationOrderDraftCommand(Long requestId, Long operatorId): RemoveMedicationOrderDraftCommand`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre/Gate | 住院门控 + `validatePatientEncounter` | |
| ② | Gate | 解析 `scope`：若 `PRESCRIPTION` 则 `InpatientTcmPrescriptionExpandSupport#expandToAllTcmLines`（同 `prescription_no` 或 `prescriptionGroupKey`） | 得到目标 id 全集 |
| ③ | Pre | 逐行断言 `tcmFlag=true` | **BR-TCM-ISO-002** |
| ④ | Pre | `InpatientTcmRxRevokeGateSupport#assertNoNetExecutionOrCompletedDispenseOrLockedCharge` | **BR-IP-TCM-009**；失败 EX-IP-TCM-103 |
| ⑤ | Pre | **DELETE 分支**：`checkMedicationRequestDeletable`；**WITHDRAW 分支**：加载行状态 | |
| ⑥ | Cmd | **DELETE 分支**：`removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand)` | **写库点**；仅 **DRAFT** |
| ⑦ | Cmd | **WITHDRAW 分支·若**状态=**ACTIVE**：`signOffMedicationOrder(SignOffMedicationOrderCommand)` | **写库点** |
| ⑧ | Cmd | **WITHDRAW 分支·若**状态=**VERIFIED**：`rejectMedicationOrder(RejectMedicationOrderCommand)`（医生 `operatorId` 写入 `performer_check_id`） | **写库点**；已校对未执行路径（**BR-IP-TCM-009**）；成码时同步扩展 `ensureRejectable` 允许 **VERIFIED** |
| ⑨ | Asm | `toWithdrawOrDeleteTcmHerbalPrescriptionResponse` | **不** void Dispense/Charge（医生站未预建） |

> **互斥**：⑥ / ⑦ / ⑧ 按 `action` 与行状态互斥执行；**禁止**对 **STOPPED** 行走本 IF 删除（走停嘱/取消停嘱）。

---

### 3.12 IF-IP-TCM-39 `listPatientTcmHerbalPrescriptionHistory`（查询患者历史中药处方）

> **母版**：`AS-OP-DOR-010-SDD` §3.12 IF-DOR-39。

#### Request 字段表（`ListPatientTcmHerbalPrescriptionHistoryRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | Long | 是 | 患者 |
| currentEncounterId | Long | 是 | 排除当前就诊 |
| keyword | String | 否 | 过滤药品名/处方号 |

#### Response 字段表（`ListPatientTcmHerbalPrescriptionHistoryResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| historyRows | List\<TcmRxHistoryRowDto\> | 历史方摘要 |

#### DTO 字段表（`TcmRxHistoryRowDto`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptionNo | String | 处方号 |
| encounterId | Long | 来源就诊 |
| reqOpenedTime | LocalDateTime | 开立时间（`req_opened_time`；方级聚合；空回落 `req_authored_time`） |
| summaryText | String | 方摘要 |
| medicationNames | List\<String\> | 药名列表 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Pre | `MedicationRequestQueryService#listTcmHerbalPrescriptionHistoryByPatient(patientId, currentEncounterId)` | 按方聚合 |
| ② | Asm | `InpatientTcmRxListAssembler#enrichHistoryGroups` + keyword 过滤 | |
| ③ | Asm | `InpatientDoctorStationTcmMapper#toListPatientTcmHerbalPrescriptionHistoryResponse` | 仅读 |

---

### 3.13 IF-IP-TCM-40 `stopLongTermTcmHerbalPrescription`（停嘱·长期中药方）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#stopLongTermTcmHerbalPrescription` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/stopLongTermTcmHerbalPrescription` |

#### Request 字段表（`StopLongTermTcmHerbalPrescriptionRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| prescriptionNos | List\<String\> | 是 | 待停嘱处方号（整方） |
| stopTime | LocalDateTime | 否 | 默认当前时间 |

#### Response 字段表（`StopLongTermTcmHerbalPrescriptionResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| message | String | |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-211 | 非长期或状态不允许停嘱 |

#### Mapper 方法签名

- `toStopLongTermMedicationOrderCommand(Long requestId, LocalDateTime stopTime, Long operatorId): StopLongTermMedicationOrderCommand`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | `prescriptionNos` 非空 | |
| ② | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` + 可写门控 | **BR-IP-TCM-001** |
| ③ | Gate | `validatePatientEncounter` | |
| ④ | Pre | `InpatientTcmPrescriptionExpandSupport#resolveLongTermTcmRequestIdsByPrescriptionNos` | 每号下全部 `tcm_flag=true` **LONG_TERM** 行 |
| ⑤ | Pre | 逐行校验可停（非 **STOPPED**、非 **DRAFT**） | 失败 EX-IP-TCM-211 |
| ⑥ | Cmd | 逐行 `stopLongTermMedicationOrder(StopLongTermMedicationOrderCommand)` | **写库点**；→ **STOPPED** + 结束时间 |
| ⑦ | Asm | 成功响应 | **不**级联护士执行派生临嘱 |

---

### 3.14 IF-IP-TCM-41 `cancelStopLongTermTcmHerbalPrescription`（取消停嘱·长期中药方）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#cancelStopLongTermTcmHerbalPrescription` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/cancelStopLongTermTcmHerbalPrescription` |

#### Request 字段表（`CancelStopLongTermTcmHerbalPrescriptionRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| prescriptionNos | List\<String\> | 是 | 已停嘱处方号（整方） |

#### Response 字段表（`CancelStopLongTermTcmHerbalPrescriptionResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| message | String | |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| EX-IP-TCM-212 | 非 STOPPED 或非长期 |
| EX-IP-TCM-213 | 出院/转科门禁 |

#### Mapper 方法签名

- `toCancelStopLongTermMedicationOrderCommand(Long requestId, Long operatorId): CancelStopLongTermMedicationOrderCommand`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | `prescriptionNos` 非空 | |
| ② | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | |
| ③ | Pre | `ServiceRequestQueryService#checkDuplicateTransferOrDischargeRequest` + 快照 `dischargeTypeEnum` 门禁 | **BR-IP-ORD-025**；EX-IP-TCM-213 |
| ④ | Gate | `validatePatientEncounter` | |
| ⑤ | Pre | `InpatientTcmPrescriptionExpandSupport#resolveStoppedLongTermTcmRequestIdsByPrescriptionNos` | 整方 **STOPPED** + **LONG_TERM** |
| ⑥ | Cmd | 逐行 `cancelStopLongTermMedicationOrder(CancelStopLongTermMedicationOrderCommand)` | **写库点**；恢复 **VERIFIED** 或 **ACTIVE** |
| ⑦ | Asm | 成功响应 | 不因已有执行/发药拒绝（对齐西医 **IF-IP-DOR-25**） |

---

### 3.15 IF-IP-TCM-42 `changeTcmHerbalPrescriptionOpenedTime`（按方改开立时间）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationTcmAppService#changeTcmHerbalPrescriptionOpenedTime` |
| **HTTP** | `POST /api/app/inpatientDoctorStationTcm/changeTcmHerbalPrescriptionOpenedTime` |

#### Request 字段表（`ChangeTcmHerbalPrescriptionOpenedTimeRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| prescriptionNos | List\<String\> | 否 | 已签发处方号；与 `prescriptionGroupKeys` 至少其一 |
| prescriptionGroupKeys | List\<String\> | 否 | 草稿方标识；无处方号时使用 |
| newOpenedTime | LocalDateTime | 是 | 新开立时间（整方统一） |

#### Response 字段表（`ChangeTcmHerbalPrescriptionOpenedTimeResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | |
| message | String | |
| changedLineCount | Integer | 实际改写药味行数 |

#### 失败码口径表

| 失败码 | 场景 |
| --- | --- |
| ENCOUNTER_INVALID_ARGUMENT | 范围皆空 / 新时间为空 / 扩行后无药味 |

#### Mapper 方法签名

- `toChangeMedicationRequestOpenedTimeCommand(Long requestId, LocalDateTime newOpenedTime, Long operatorId): ChangeMedicationRequestBusinessTimeCommand`
- `toChangeTcmHerbalPrescriptionOpenedTimeResponse(Boolean success, String message, Integer changedLineCount)`

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | `newOpenedTime` 非空；`prescriptionNos`/`prescriptionGroupKeys` 至少其一 | **BR-IP-TCM-011** |
| ② | Pre | 住院可写快照 + `validatePatientEncounter` | **BR-IP-TCM-001** |
| ③ | Pre | `InpatientTcmPrescriptionExpandAssembler#resolveAllTcmRequestIdsForOpenedTimeChange` | 按处方号/方标识整方扩行 |
| ④ | Gate | 扩行结果非空 | |
| ⑤ | Cmd | 逐行 `changeMedicationRequestBusinessTime`（`timeKind=OPENED`） | **写库点**；不改开立人/签发时间 |
| ⑥ | Asm | 成功响应含 `changedLineCount` | |

---

## 4. QueryService 与领域事件

| 写 IF | 领域事件 | 读侧刷新 |
| --- | --- | --- |
| IF-IP-TCM-32 | `EncounterDiagnosisSavedEvent` / `EncounterDiagnosisAssociationUpsertedEvent` | IF-IP-TCM-30 快照 |
| IF-IP-TCM-33 | `EncounterDiagnosisDeletedEvent` / `EncounterDiagnosisAssociationRemovedEvent` | 同上 |
| IF-IP-TCM-36 | `HerbalMedicationOrderDraftCreatedEvent` / `EditedEvent` | IF-IP-TCM-34 |
| IF-IP-TCM-37 | `MedicationOrderSignedEvent` | IF-IP-TCM-34；护士校对（**AS-IP-NUR-003**） |
| IF-IP-TCM-38 | `MedicationOrderSignOffEvent` / `MedicationOrderRejectedEvent` | IF-IP-TCM-34 |
| IF-IP-TCM-40 | `LongTermMedicationOrderStoppedEvent` | 护士执行列表 |
| IF-IP-TCM-41 | `LongTermMedicationOrderStopCancelledEvent` | 护士执行/出院门禁读侧 |
| IF-IP-TCM-42 | （无独立领域事件；直写 `req_opened_time`） | IF-IP-TCM-34 / 护士站折叠展示 |

护士执行产生 Dispense/Charge（含代煎）归属 **AS-IP-NUR-004**，不在本服务事件表展开。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号（§3） | 聚合写入顺序 |
| --- | --- | --- | --- |
| IF-IP-TCM-32 | saveTcmDiagnosisGroupsBatch | ⑦→⑧→⑩→⑪ | 关联删 → Condition 删 → Condition upsert → Encounter 中医关联 batchUpsertTcm |
| IF-IP-TCM-33 | deleteTcmDiagnosisGroup | ⑤→⑥ | 关联删 → Condition 删 |
| IF-IP-TCM-36 | saveTcmHerbalPrescriptionDraftBatch | ⑥～⑩ | **仅** MedicationRequest（无 ChargeItem） |
| IF-IP-TCM-37 | signTcmHerbalPrescriptionBatch | ⑥→⑦ | Request 预写字段 → sign → **ACTIVE** |
| IF-IP-TCM-38 | withdrawOrDeleteTcmHerbalPrescription | ⑥/⑦/⑧ | Request 状态回退（signOff/reject/remove） |
| IF-IP-TCM-40 | stopLongTermTcmHerbalPrescription | ⑥ | Request → **STOPPED** |
| IF-IP-TCM-41 | cancelStopLongTermTcmHerbalPrescription | ⑥ | Request 恢复 **VERIFIED/ACTIVE** |

### 5.2 事务边界 / 失败回滚

| 策略 | 范围 |
| --- | --- |
| 默认 | 各写 IF 单方法由 **AppServiceAspect** UoW 全回滚（**禁止**业务代码 `@Transactional`） |
| IF-IP-TCM-32 | 删组+保存同一事务 |
| IF-IP-TCM-36 | 多方多行同一事务；任一行失败整批回滚 |
| IF-IP-TCM-37 | 同批多 key 签发同一事务 |
| IF-IP-TCM-40/41 | 多 `prescription_no` 扩行后逐条 Cmd，同一事务 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 重复签发同 key | 复用已有 `prescriptionNo` | `InpatientTcmRxSignAssembler` | §3.10-④ |
| 重复暂存同 key | 按 `medicationRequestId` UPDATE | AppService | §3.9 |

### 6.2 并发控制

双终端同方编辑以后写为准；乐观锁冲突 → EX-IP-TCM-105/207。

### 6.3 重试与容错

无外部网关；门控失败不重试。

### 6.4 数据脱敏

日志不打印完整 `content_json`。

### 6.5 审计日志

`operatorId` 写入领域事件；`signCode` 批次级；停嘱 `stopTime` 落 `stop_time`（**不**占用 `effective_dose_end` 计划结束列）。

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | IF | 优先级 |
| --- | --- | --- |
| 初始化含长临默认 `LONG_TERM` + 频次含立即 | IF-IP-TCM-29 | P0 |
| 长临筛选列表与 `therapy_enum` 一致 | IF-IP-TCM-34 | P0 |
| 长期方多味暂存/签发同一 `prescription_no` | IF-IP-TCM-36/37 | P0 |
| 临嘱 `rateCode=STAT` 校验；长期拒绝立即 | IF-IP-TCM-36/37 | P0 |
| 签发后**无** ChargeItem/Dispense | IF-IP-TCM-37 | P0 |
| 已执行/已发药不可签退 | IF-IP-TCM-38 | P0 |
| 长期整方停嘱/取消停嘱 | IF-IP-TCM-40/41 | P0 |
| 按方改开立时间整方统一 | IF-IP-TCM-42 | P0 |
| 取消停嘱出院门禁 | IF-IP-TCM-41 | P0 |
| 饮片目录与 IF-IP-DOR-11 同构且不做同名合并（BR-TCM-RX-014） | IF-IP-TCM-35 | P1 |

### 7.2 非功能测试

IF-IP-TCM-30/34 ≤800ms；IF-IP-TCM-36 ≤2000ms；IF-IP-TCM-37 ≤1500ms。

### 7.3 查询一致性

IF-IP-TCM-34 与 DB `tcm_flag=true` + `therapy_enum` 筛选一致。

### 7.4 集成测试

与 **AS-IP-DOR-003** 西医签发互不影响（**BR-TCM-ISO-001**）；护士校对后可执行计费（**AS-IP-NUR-004**）。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 无 | 领域读侧 **G-IP-TCM-001** 已关闭 | — |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.6 | 2026-09-09 | **BR-IP-TCM-005**：临时方不强制 `STAT`；`CreateHerbalMedicationOrderDraftCommand.therapyEnum`；CREATE 后按方头再写长临（含临时）；EX-IP-TCM-208 存量 |
| v1.5 | 2026-09-07 | **IF-IP-TCM-42 / BR-IP-TCM-011**：`changeTcmHerbalPrescriptionOpenedTime` 按方统一开立时间；扩行方 `resolveAllTcmRequestIdsForOpenedTimeChange`；对齐 **AS v1.6** |
| v1.4 | 2026-08-01 | **IF-IP-TCM-35 / BR-TCM-RX-014**：废止读侧同名合并与 `TcmHerbalCatalogSameNameMergeAssembler`；选品与 IF-IP-DOR-11 同构 |
| v1.3 | 2026-07-24 | **IF-IP-TCM-35 / BR-TCM-RX-014**：改为 `assemblePageContent` + `TcmHerbalCatalogSameNameMergeAssembler`；下发当前批/合并总量与厂家（已被 v1.4 废止） |
| v1.0 | 2026-06-20 | 首版：住院中医 Tab 目标态 SDD；IF **IF-IP-TCM-29～41**；对齐 **AS-IP-DOR-006 v1.1**；住院差异：长临分轨、医生站仅 Request、临嘱立即频次、方头一致性、执行/发药签退门控、整方停嘱/取消停嘱；复用门诊中医病证/历史方母版；**G-IP-TCM-001** 三参 `listTcmHerbalPrescriptionsByEncounter` 已纳入 §3.7；§2.3 增补 `InpatientDoctorStationTcmConstants`（目标态待生成） |
| v1.2 | 2026-06-27 | **IF-IP-TCM-34 / BR-PRICE-SRC-001**：`InpatientTcmRxListAssembler` 已挂费优先 **`ChargeItem.unit_price`**（对齐门诊 `TcmRxListAssembler`）；移除「无 ChargeItem enrich」口径 |
| v1.1 | 2026-06-20 | 应用层已落地：§1.1 实现阶段改为**已落地**；§1.2/§5.2 事务口径对齐 **AppServiceAspect**（移除 `@Transactional`）；§2.1 `InpatientTcmDiagnosisAssembler` 明确委托门诊 `TcmDiagnosisAssembler`；§2.3 `InpatientDoctorStationTcmConstants` 已生成；IF-IP-TCM-35 目录装配锚点 `assemblePageContentWithoutInventoryGate` |

---

**文档负责人**：（待填） · **评审**：（待填）
