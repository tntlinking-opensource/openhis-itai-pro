# 门诊护士站 · 应用服务详细设计（SDD）

> **编码**：AS-OP-NUR-001-SDD  
> **版本**：v1.3.11 | **日期**：2026-08-19 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界


| 项          | 内容                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **模块**     | 门诊护士站 · 处置队列、处置单查询、执行记录查询、批量执行（含耗材发放）与**批量取消执行**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **边界**     | 编排 QueryService、收费只读校验（**仅 IF-NUR-04**）、Procedure / DeviceDispense / InventoryItem 等领域写服务；耗材执行/取消经 **`DeviceDispenseNurseWorkflowsupport**（**BR-NUR-014**）；**不**调用 `DeviceRequestDomainService#executeDeviceOrder` / `#revokeDeviceOrderExecution`；**IF-NUR-05 不调用 ChargeItem 写服务** |
| **编排权威**   | `AS-OP-NUR-001-门诊护士站.md`（**v1.9.8**）§2 处置 + **§2.1/§4** 皮试目标态                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **子模块**   | **患者** `patient`（IF-NUR-01）· **处置** `treatment`（IF-NUR-02～05/03B）· **皮试** `skintest`（IF-OP-STK-01～04）；详见分拆 SDD：**PAT / TRT / STK** |
| **方法权威**   | 各聚合 DM §6（Procedure / DeviceRequest / DeviceDispense / InventoryItem 等）；QueryService `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）`、`ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）`、`DM-CLN-004 §6.2 ProcedureQueryService / OrderExecutionSheetLoader` §3                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **严格对齐**   | IF **IF-NUR-01～05**、**IF-NUR-03B** 与 AS 一致                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **实现阶段口径** | **目标态全量契约**。**IF-NUR-01** 已与 `EncounterContextReader#pageNurseStationOutpatientQueue` 可对齐。**IF-NUR-02** 按 **InpatientOrderListSkeleton（Q-ORD-006** 由应用层组合 **域只读查询服务**（非单一巨型 Criteria）；**IF-NUR-03** 与 `OrderExecutionSheetLoader#pageOrderExecutionSheet` 对齐（**全量**执行记录分页）。**IF-NUR-03B**：**`ProcedureQueryService#listNetRemainingCompletedProcedureRecords`** + **`listNetRemainingProcedureRecordsForNurseStation`**（净剩 **COMPLETED**，已对冲对不返回）。**IF-NUR-04**：护士站创建执行记录命令显式 `**CreateProcedureRecordCommand#skipDuplicateRequestAnchorCheck=true**`，在保留默认域幂等（`null`/`false`）前提下允许多次 `create→start→complete` 以满足 **BR-NUR-005**；`completeProcedureRecord` 仍要求 **IN_PROGRESS** 与结果占位（与 §3.5 链路一致）。耗材发放后：若拣配批号为真实值且可定位 `**wkf_inventory_item**`，则 `**InventoryItemQueryService#requireInventoryItemIdForDeviceDispenseBatch**` + `**InventoryItemDomainService#decreaseQuantitiesMerged**` 合并扣减；占位批号时跳过扣减（待 FIFO 全链路接入）。**IF-NUR-05**：**`batchCancelDisposition` 已实现**（§3.7）；诊疗/药品入参 **`procedureIds`**（每条对应一次 `create→start→cancel`，`refundId` = 被对冲 **COMPLETED.id**）；耗材路径独立；**不**调用 ChargeItem 写服务 |


### 1.2 术语口径


| 项                       | 约定                                                                                                                                                                                                                                                                                                         |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **字段口径**                | Request/Response/DTO 字段命名与 **InpatientOrderListSkeleton（Q-ORD-006**、`NurseStationTreatmentLineRow` 推荐字段一致（camelCase）；枚举持久化 **String code**（`getCode()`）                                                                                                                                                                     |
| **「已计费」门控（BR-NUR-004）** | 与 **DM-ADM-011** §2.3 对齐：至少一条关联收费明细 `adm_charge_item.status_enum` 为 `**ChargeItemStatus.PLANNED`**（已形成可收费事实）或 `**ChargeItemStatus.SETTLED**`（已结算）；实现筛选逻辑须与产品确认是否排除仅 `DRAFT`                                                                                                                                |
| **执行次数（BR-NUR-005）**    | **诊疗**：计划次数 ← `ServiceRequest.execution_count`（**临嘱/门诊**；域模型 **DM-WKF-004**；**禁止**回落 `quantity`）。净次数 = **完成态条数 − 取消态条数**（完成 **`EventStatus.COMPLETED`**；取消 **`EventStatus.CANCEL`**，见 **DM-CLN-004** §4.1）。**药品**：计划次数 ← `MedicationRequest.execute_num`（**DM-MED-003**）。当前 **ProcedureQueryService#getProcedureRecords** 可用；统计口径与 **Q-ORD-006** §3.4.3「诊疗次数」一致 |
| **QueryService口径**               | IF-NUR-01：`OutpatientEncounterContextQueryModel` **Q-OPI-004**；IF-NUR-02：**Q-ORD-006**（应用编排）；IF-NUR-03：`OrderExecutionSheet` **Q-OES-001**（全量分页）；**IF-NUR-03B**：域表 `cln_procedure` 净剩查询（**非**QueryService 分页）                                                                                                                                                                                       |
| **净剩列表（BR-NUR-013）**     | `ProcedureQueryService#listNetRemainingCompletedProcedureRecords(encounterId, requestId?, requestTable?)`：返回未被 **CANCEL.refund_id** 占用的 **COMPLETED**；仓储 `findByEncounterId` / `findByEncounterIdAndRequestIdAndRequestTable`                                                                                                                                 |
| **取消执行（BR-NUR-011）**     | 入参 **`procedureIds`**（已完成执行记录主键）；每条追加 **CANCEL**（`create→start→cancel`，`skipDuplicateRequestAnchorCheck=true`）；`CancelProcedureRecordCommand.refundId` **必填** = 被对冲 **COMPLETED** 的 `cln_procedure.id`（**BR-PRO-005**）；Gate 校验 COMPLETED / 本就诊 / 未对冲 / 同批去重；**不**读取/写入 ChargeItem                                                                                                                                 |
| **取消执行（BR-NUR-012）**     | **PREPARED**（开关 **关**）→ `revertPreparedBatchToPreparation`；**COMPLETED**（开关 **开**）→ 回退前快照 → `revertCompletedBatchToPreparation` → `increaseQuantities` 合并回补；统一 **PREPARATION**；**保留签发 `lot_number`**（**BR-DD-009**）；**DeviceRequest 不变** |
| **耗材自动扣库（BR-NUR-014）** | `DeviceAutoInventoryDeductSettingSupport#isEnabled` 读 `SettingDefinitionEnum.DEVICE_AUTO_INVENTORY_DEDUCT_SWITCH`（name=`deviceAutoInventoryDeductSwich`）；编排 **`DeviceDispenseNurseWorkflowsupport** |
| **事务口径**                | **IF-NUR-04 / IF-NUR-05** 默认 **单次批量同一事务**：任一 Cmd 失败则全批回滚；跨就诊批量 **禁止**（Gate）                                                                                                                                                                                                                             |
| **HTTP 接入口径**           | `AppServiceControllerRegistrar`：三子域前缀 **`outpatientNurseStationPatient`** / **`outpatientNurseStationTreatment`** / **`outpatientNurseStationSkinTest`**；`POST /api/app/{serviceName}/{methodName}`                                                                                                                                                                                                       |
| **业务异常**                | 抛出 `com.zxplat.framework.core.application.exception.BusinessException`，`errorCode` 使用 `ErrorCodeConstants.ErrorCode`；本文「失败码口径」语义对齐 AS **EX-NUR-***                                                                                                                                                            |


### 1.3 DOC-CONFLICT（Procedure × 护士站多次执行）—实现缓解说明


| ID                       | 现象（历史描述）                                                                                                                           | 权威来源                                                 | 当前实现缓解                                                                                                                                                                                                                |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **DOC-CONFLICT-NUR-P01** | `**ProcedureDomainService#createProcedureRecord**` 在已存在同 `requestId+requestTable` 记录时拒绝第二条，与 **BR-NUR-005** 冲突 | **AS-OP-NUR-001** BR-NUR-005 vs **BR-PRO-001**          | 护士站装配的 `**CreateProcedureRecordCommand**` 置 `**skipDuplicateRequestAnchorCheck=true**`，仅此路径跳过「同锚点已存在则拒绝」；其它调用方未传该字段时仍走默认幂等。长期仍可由 **DM-CLN-004** 或独立原子命令收敛。 |
| **DOC-CONFLICT-NUR-P02** | `**completeProcedureRecord**` 要求 **IN_PROGRESS**                                                                                  | AS IF-NUR-04 vs **ProcedureDomainService**           | 护士站已按序 `**create→start→complete**`，且完成命令带结果占位以满足 **BR-PRO-004**；与域状态机一致，本条在护士站链路视为 **已对齐**。 |
| **DOC-CONFLICT-NUR-P03** | IF-NUR-05 采用「**追加 CANCEL**」+ **`refund_id` 指向被对冲 COMPLETED**；**DM-CLN-004** `cancelProcedureRecord` 仍供**其它场景**撤销 **PREPARATION/IN_PROGRESS** 单条                                                                 | AS IF-NUR-05 vs **DM-CLN-004** §6.1.5                  | **非护士站 Gap**：两条路径并存。IF-NUR-05：前端传 **`procedureId`**（COMPLETED）→ `create→start→cancel`（`refundId` 必填）；不对 **COMPLETED** 历史行直接 `cancelProcedureRecord`。 |
| **DOC-CONFLICT-NUR-P04** | 历史：IF-NUR-05 曾编排 `revokeDeviceOrderExecution` | **DM-WKF-003** §6.1.7 | **已废止（v1.1.0）**：护士站 IF-NUR-04/05 **不**变更 **DeviceRequest**；`executeDeviceOrder`/`revokeDeviceOrderExecution` 仍供 **AS-OP-FIN-002** 等场景 |


---

## 2. 代码结构

### 2.1 包路径（三子域）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/nursestation/
  patient/          # IF-NUR-01 患者队列（左侧列表，处置/皮试共用）
    service/OutpatientNurseStationPatientAppService.java
    mapper/OutpatientNurseStationPatientMapper.java
    request/ response/ dto/
  treatment/        # IF-NUR-02～05、IF-NUR-03B 处置
    service/OutpatientNurseStationTreatmentAppService.java
    mapper/OutpatientNurseStationTreatmentMapper.java
    request/ response/ dto/
  skintest/         # IF-OP-STK-01～04 皮试
    service/OutpatientNurseStationSkinTestAppService.java
    mapper/ assembler/ request/ response/ dto/
```

| 子域 | AppService | HTTP 前缀 |
| --- | --- | --- |
| 患者 | `OutpatientNurseStationPatientAppService` | `POST /api/app/outpatientNurseStationPatient/{methodName}` |
| 处置 | `OutpatientNurseStationTreatmentAppService` | `POST /api/app/outpatientNurseStationTreatment/{methodName}` |
| 皮试 | `OutpatientNurseStationSkinTestAppService` | `POST /api/app/outpatientNurseStationSkinTest/{methodName}` |

> 分拆 SDD：`AS-OP-NUR-001-PAT-患者队列-SDD.md`、`AS-OP-NUR-001-TRT-处置-SDD.md`、`AS-OP-NUR-001-STK-皮试-SDD.md`。

### 2.2 依赖注入表（构造器注入）


| 依赖                                  | 用途                                                                                            |
| ----------------------------------- | --------------------------------------------------------------------------------------------- |
| `EncounterContextReader` | **PAT** IF-NUR-01：`pageNurseStationOutpatientQueue`（可选 `orgId` / `pendingDeviceOnly`） |
| `EncounterQueryService` | **TRT** IF-NUR-02～05、**STK** Gate：`mustGetEncounter` 等 |
| `OrderExecutionSheetLoader` | IF-NUR-03：`pageOrderExecutionSheet`（`application.outpatient.nursestation.procedure.support`） |
| `ServiceRequestQueryService`        | IF-NUR-02：诊疗申请列表；IF-NUR-04 / 05：计划次数、净次数门控                                                            |
| `MedicationRequestQueryService`     | IF-NUR-02 / 04 / 05：药品申请列表与 `execute_num`                                                          |
| `DeviceRequestQueryService`         | IF-NUR-01：本页批量 `listDeviceRequestsByEncounterIds`（**BR-NUR-017**）；IF-NUR-02：耗材请求列表（**只读**）                                      |
| `DeviceDispenseQueryService`        | IF-NUR-01：本页批量 `listDeviceDispensesByEncounterIds`（**BR-NUR-017**）；IF-NUR-02：发放单行视图；IF-NUR-04/05：发放态加载与 preparable 校验                                                      |
| `ChargeItemQueryService`            | IF-NUR-02：按就诊/服务锚点装载费用；**仅 IF-NUR-04**：已计费门控（IF-NUR-05 **不注入/不调用**）                                                        |
| `ProcedureQueryService`             | IF-NUR-02：按请求统计执行次数；IF-NUR-03B：净剩 **COMPLETED** 列表；IF-NUR-04：净次数门控；IF-NUR-05：按 **procedureId** 加载与对冲校验                                                           |
| `ProcedureDomainService`            | IF-NUR-04：执行登记；IF-NUR-05：`create/start/cancel`（均带 `skipDuplicateRequestAnchorCheck`，见 §1.3）                                |
| `DeviceDispenseDomainService`       | IF-NUR-04/05：经 **`DeviceDispenseNurseWorkflowsupport** 间接调用 `prepareDeviceDispense` / `dispenseDevice` / `revertPreparedBatchToPreparation` / `revertCompletedBatchToPreparation` 等 |
| `InventoryItemDomainService`        | IF-NUR-04：`decreaseQuantitiesMerged`；IF-NUR-05：`increaseQuantities`（Map 合并，对称 IF-NUR-04）                                  |
| `DeviceAutoInventoryDeductSettingsupport | IF-NUR-04 Gate：耗材房是否必填 |
| `DeviceDispenseNurseWorkflowsupport | IF-NUR-04：`applyOutpatientNurseDeviceDispenses`；IF-NUR-05：`revertOutpatientNurseDeviceDispenses`（`application.shared.nursestation-device-dispense`，门诊/住院 AppService 注入） |
| `ProcedureExecutionPerformerOrchestrationsupport | IF-NUR-04 / IF-IP-EXE-02：`completeProcedureRecord` 后登记主执行人至 `cln_procedure_performer`（暂 1:1） |
| `ProcedurePerformerDisplayAssembler` | IF-NUR-02：批量回填 `lastPerformerDisplay`；IF-NUR-03：经 `mapPrimaryPractitionerIdByProcedureIds` 回填 `operatorDisplayName` |
| `CurrentPractitionerResolver` | IF-NUR-04/05 写路径操作/执行人解析（**B.6.5**；`operatorId` 未传时取当前登录医务人员） |
| `InventoryItemQueryService`         | IF-NUR-04 / 05：按批号定位耗材库存项主键 `requireInventoryItemIdForDeviceDispenseBatch`（真实批号路径）                    |
| `PractitionerQueryService`          | IF-NUR-02 / IF-NUR-03：执行人展示名解析                                                                                   |
| `OutpatientNurseStationPatientMapper` / `OutpatientNurseStationTreatmentMapper` | 各子域 Request→Query/Command、DTO→Response |
| `EncounterContractDisplayAssembler` | **PAT** IF-NUR-01：本页就诊批量回填 `contractDisplay` |
| `OrderRequestCatalogWarmAssembler`（`module.common`） | IF-NUR-02 / IF-DOR-10 / IF-OPP-03：批量加载诊疗/药品/耗材定义与三类类目术语；`OrderCatalogDisplayAssembler` 解析展示名 |
| `TerminologyOptionLoader` / `FrequencyOptionLoader` / `ProductDefinitionBatchLoader`（`module.common` / `querysupport.product`） | IF-NUR-02 步骤 **8b**：药品行 `unitName` / `doseUnitName` / `methodName` / `rateName` 等 code+展示名（对齐 IF-DOR-10 **BR-ORD-022**；`ProductDefinitionCatalogUnitBandsAssembler` 单位回落） |
| `CurrentOperatorResolver` | **STK** IF-OP-STK 写路径 `operatorId`（**B.6.5**）；**PAT** 机构收窄由 `EncounterContextReader` / 域查询承担，**本模块 Patient AppService 不注入 Resolver** |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别             | 代码实现锚点                                                                                                                               | DM 追溯锚点                                                                                  | 说明                                                                   |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| 就诊类别（门诊过滤）     | `com.zxplat.domain.share.enums.EncounterClass`                                                                               | `DM-ADM-002` §3.1 `class_enum`；**BR-NUR-001** 门诊队列                                       | IF-NUR-01：QueryService侧已与 **Q-OPI-004** 联合；App 层一般不重复过滤除非 Pre 二次校验            |
| 请求状态           | `com.zxplat.domain.share.enums.RequestStatus`                                                                                | `DM-WKF-004` / `DM-MED-003` / `DM-WKF-003` §3 **status_enum**；**BR-NUR-001** `COMPLETED` | IF-NUR-01 准入；IF-NUR-02 列表过滤                                          |
| 收费明细状态         | `com.zxplat.domain.share.enums.ChargeItemStatus`                                                                             | `DM-ADM-011` §2.3 **status_enum** / §3 **ChargeItemStatus**；**BR-NUR-004**               | **IF-NUR-04 Pre**：`PLANNED` ∪ `SETTLED`（见 §1.2）                      |
| 执行记录状态         | `com.zxplat.domain.share.enums.EventStatus`                                                                                  | `DM-CLN-004` §4.1 与 **Procedure.status_enum**；**Q-ORD-006** 诊疗次数统计                       | **COMPLETED** / **CANCEL**（取消执行）；**STOPPED**（执行中止，当前域写未使用）；历史 **ABORTED** 由 V43 迁移为 **CANCEL** |
| 发放状态           | `com.zxplat.domain.share.enums.DispenseStatus`                                                                               | `DM-WKF-007` §3 **dispense 状态**                                                          | IF-NUR-01 **BR-NUR-017** 待发判定（`DRAFT`/`PREPARATION`/`IN_PROGRESS`）；IF-NUR-02 行展示；IF-NUR-04 **BR-NUR-008** 仅处理准备中                        |
| 请求表锚点          | `com.zxplat.domain.share.constants.TableNameConstants.WKF_SERVICE_REQUEST` / `MED_MEDICATION_REQUEST` / `WKF_DEVICE_REQUEST` | `DM-CLN-004` §3.1 **request_table**；**InpatientOrderListSkeleton（Q-ORD-006** `requestTable`               | IF-NUR-02 DTO `requestTable`；IF-NUR-04 Procedure 关联                  |
| Procedure 执行类目 | `CreateProcedureRecordCommand.categoryEnum`                                                                                          | `DM-CLN-004` §4.2 **category_enum** / §3 实体字段                                            | IF-NUR-04：门诊医嘱执行类目 **String code**                                   |
| 错误码            | `com.zxplat.domain.share.constants.ErrorCodeConstants.ErrorCode`                                                             | 各领域 DM §6 失败语义；AS **EX-NUR-***                                                           | 映射见各 IF「失败码口径」                                                       |


### 2.4 领域方法与 Mapper 白名单

**QueryService / 查询**


| 领域 / QueryService            | 类型                                  | 方法                                                                                                                                                                                                                                                        | 用于 IF          |
| ------------------- | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| EncounterContractDisplayAssembler | `EncounterContractDisplayAssembler` | `mapContractDisplayByEncounterIds(Collection<Long>)` | IF-NUR-01      |
| OutpatientEncounterContextQueryModel      | `EncounterContextReader`      | `pageNurseStationOutpatientQueue(NurseStationOutpatientQueueQuery)`（可选 `orgId`、`pendingDeviceOnly`；工程实现与 B.10.11 分页基类一致）                                                                                                                                                                        | IF-NUR-01      |
| Encounter           | `EncounterQueryService`             | `mustGetEncounter(Long)`                                                                                                                                                                                                                                  | IF-NUR-02～05   |
| OrderExecutionSheet | `OrderExecutionSheetLoader` | `pageOrderExecutionSheet(OrderExecutionSheetPageQuery)`                                                                                                                                                                                                   | IF-NUR-03      |
| OrderExecutionSheet | `OrderExecutionSheetLoader` | `listOrderExecutionSheet(OrderExecutionSheetPageQuery)`                                                                                                                                                                                                   | IF-NUR-03      |
| ServiceRequest      | `ServiceRequestQueryService`        | `listServiceRequestsByEncounter(Long)`                                                                                                                                                                                                                    | IF-NUR-02      |
| MedicationRequest   | `MedicationRequestQueryService`     | `listMedicationRequestsByEncounter(Long)`                                                                                                                                                                                                                 | IF-NUR-02      |
| DeviceRequest       | `DeviceRequestQueryService`         | `listDeviceRequestsByEncounter(Long)`、**`listDeviceRequestsByEncounterIds(Collection<Long>)`**（IF-NUR-01 **BR-NUR-017**）                                                                                                                                                                                                                     | IF-NUR-01 / 02      |
| DeviceDispense      | `DeviceDispenseQueryService`        | `listDeviceDispensesByEncounter(Long)`、**`listDeviceDispensesByEncounterIds(Collection<Long>)`**（IF-NUR-01 **BR-NUR-017**）、**`mapDeviceDispenseIdsByDeviceRequestIds(Collection<Long>)`**（IF-NUR-02 一次 IN）、`listDeviceDispenseIdsByDeviceRequest(Long)`（单条兼容）、`loadDeviceDispenseById(Long)`、`mapDeviceRequestToPrimaryDispenseLocationId(Collection<Long>)`、`mustGetDeviceDispense(Long)`、`validateDeviceDispensePreparable(Long)` | IF-NUR-01 / 02 / 04 |
| ChargeItem          | `ChargeItemQueryService`            | `listChargeItemsByEncounterId(ChargeItemQueryCommand)`、`listChargeItemsByServiceIds(ChargeItemQueryCommand)`                                                                                                                                              | IF-NUR-02 / 04 |
| InventoryItem（读）   | `InventoryItemQueryService`         | `requireInventoryItemIdForDeviceDispenseBatch(String itemTableName, Long deviceDefinitionId, Long locationId, String lotNumber)`                                                                                                                          | IF-NUR-04      |
| Procedure           | `ProcedureQueryService`             | `getProcedureRecords(Long, String)`、`listNetRemainingCompletedProcedureRecords(Long, Long, String)`、`loadProcedureById(Long)`                                                                                                                                                       | IF-NUR-02 / 03B / 04 / 05 |
| ServiceRequest      | `ServiceRequestQueryService`        | `mustGetServiceRequest(Long)`                                                                                                                                                                                                                             | IF-NUR-04      |
| MedicationRequest   | `MedicationRequestQueryService`     | `loadMedicationRequestById(Long)`                                                                                                                                                                                                                         | IF-NUR-04      |


**领域写**


| 聚合             | 类型                            | 方法                                                                                                                                                                        | 用于 IF                       |
| -------------- | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| Procedure      | `ProcedureDomainService`      | `createProcedureRecord(CreateProcedureRecordCommand)`（护士站入参 `skipDuplicateRequestAnchorCheck`）                                                                                                                     | IF-NUR-04 / 05 |
| Procedure      | `ProcedureDomainService`      | `startProcedureExecution(StartProcedureExecutionCommand)`                                                                                                                 | IF-NUR-04 / 05            |
| Procedure      | `ProcedureDomainService`      | `appendProcedurePerformer(AppendProcedurePerformerCommand)`（经 **`ProcedureExecutionPerformerOrchestrationSupport#registerPrimaryPerformer`**，IF-NUR-04 完成态后 1:1 登记） | IF-NUR-04            |
| Procedure      | `ProcedureDomainService`      | `completeProcedureRecord(CompleteProcedureRecordCommand)`                                                                                                                 | IF-NUR-04            |
| Procedure      | `ProcedureDomainService`      | `cancelProcedureRecord(CancelProcedureRecordCommand)`                                                                                                                     | IF-NUR-05            |
| DeviceDispense | `DeviceDispenseDomainService` | `revertPreparedBatchToPreparation`、`revertCompletedBatchToPreparation`（经 WorkflowSupport） | IF-NUR-05                   |
| InventoryItem  | `InventoryItemDomainService`  | `decreaseQuantitiesMerged`、`increaseQuantities`（Map 合并）                                                                       | IF-NUR-04 / 05                   |


**Mapper**


| 类型                             | 方法                                                                                                                                                                                                                                                         | 用于 IF          |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| `OutpatientNurseStationPatientMapper` | `toNurseStationOutpatientQueueQuery` / `toPageNurseStationPatientQueueResponse` | IF-NUR-01      |
| `OutpatientNurseStationTreatmentMapper` | `toQueryEncounterTreatmentSheetResponse(QueryEncounterTreatmentSheetRequest, EncounterDetailQueryModel, List<NurseStationTreatmentLineDto>, List<NurseStationTreatmentLineDto>, List<NurseStationTreatmentLineDto>): QueryEncounterTreatmentSheetResponse` | IF-NUR-02（Asm） |
| `OutpatientNurseStationTreatmentMapper` | `toOrderExecutionSheetPageQuery(ListProcedureRecordsForNurseStationRequest)`                                                                                                                                                                               | IF-NUR-03      |
| `OutpatientNurseStationTreatmentMapper` | `toListProcedureRecordsForNurseStationResponse(Page<OrderExecutionSheetQueryModel>)`                                                                                                                                                                        | IF-NUR-03      |
| `OutpatientNurseStationTreatmentMapper` | `toListNetRemainingProcedureRecordsForNurseStationResponse(List<ProcedureRecordQueryModel>)`                                                                                                                                                                 | IF-NUR-03B     |
| `OutpatientNurseStationTreatmentMapper` | `toCreateProcedureRecordCommand(...)`、`toStartProcedureExecutionCommand`、`toCompleteProcedureRecordCommand`、`toChargeItemQueryCommand(Long)`                          | IF-NUR-04      |
| `OutpatientNurseStationTreatmentMapper` | `toCancelProcedureRecordCommand(...)`、`toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal>)` | IF-NUR-05      |


### 2.5 白名单双向一致性复核


| 复核项     | 结论                                         |
| ------- | ------------------------------------------ |
| 正向      | §2.4 每个方法至少在 §3 某一 IF 编排序列出现               |
| 反向      | §3 序列中的 **类型#方法** 均在 §2.4 有同名条目            |
| 互斥 / 分支 | IF-NUR-04 / IF-NUR-05 诊疗药品路径与耗材路径 **分支** 在 §3.5 / §3.6「说明」列写明 |


---

## 3. 按接口 ID 命名展开

§3 导语：**先读完各 IF 的「领域编排序列」**，按序号从上到下执行；**Pre / Gate 任一失败不得进入 Cmd**。

### 3.1 应用服务接口（方法白名单）


| IF        | 应用服务方法（英文）                            | 中文方法名                | HTTP                                                                       |
| --------- | ------------------------------------- | -------------------- | -------------------------------------------------------------------------- |
| IF-NUR-01 | `pageNurseStationPatientQueue`        | 分页查询门诊护士站患者队列        | `POST /api/app/outpatientNurseStationPatient/pageNurseStationPatientQueue`        |
| IF-NUR-02 | `queryEncounterTreatmentSheet`        | 按就诊查询处置单（三列表同构）      | `POST /api/app/outpatientNurseStationTreatment/queryEncounterTreatmentSheet`        |
| IF-NUR-03 | `listProcedureRecordsForNurseStation` | 分页查询诊疗执行记录全量（执行记录抽屉）   | `POST /api/app/outpatientNurseStationTreatment/listProcedureRecordsForNurseStation` |
| IF-NUR-03B | `listNetRemainingProcedureRecordsForNurseStation` | 查询净剩可取消的已完成执行记录 | `POST /api/app/outpatientNurseStationTreatment/listNetRemainingProcedureRecordsForNurseStation` |
| IF-NUR-04 | `batchPerformDisposition`             | 批量处置执行（诊疗/药品登记与耗材发放） | `POST /api/app/outpatientNurseStationTreatment/batchPerformDisposition`             |
| IF-NUR-05 | `batchCancelDisposition`              | 批量取消执行（按 **procedureId** 追加 CANCEL、耗材回退与库存回补） | `POST /api/app/outpatientNurseStationTreatment/batchCancelDisposition`              |


### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段       | 含义                                                                                                                   | 失败时                |
| -------- | -------------------------------------------------------------------------------------------------------------------- | ------------------ |
| **Gate** | 权限 / 租户 / 参数 / 批量非空                                                                                                  | 不再调用后续步骤           |
| **Pre**  | 写前只读：就诊、收费、请求状态、执行次数、待发耗材筛选                                                                                          | 抛业务异常，**禁止进入 Cmd** |
| **Cmd**  | 领域写：`ProcedureDomainService`、`DeviceDispenseNurseWorkflowsupport、`InventoryItemDomainService` | 事务回滚见 §5           |
| **Asm**  | 各子域 Mapper 装配 Response（`OutpatientNurseStationPatientMapper` / `OutpatientNurseStationTreatmentMapper` / `OutpatientSkinTestMapper`） | —                  |


---

### 3.2 IF-NUR-01 `pageNurseStationPatientQueue`（分页查询门诊护士站患者队列）


| 项        | 内容                                                                  |
| -------- | ------------------------------------------------------------------- |
| **HTTP** | `POST /api/app/outpatientNurseStationPatient/pageNurseStationPatientQueue` |


**Request `PageNurseStationPatientQueueRequest`**


| 字段               | 类型        | 必填  | 说明                                  |
| ---------------- | --------- | --- | ----------------------------------- |
| `orgId` | Long      | N   | **开立科室**筛选（可选）；**空/未传=全部科室**；非空时等值匹配就诊 `organization_id`（门诊挂号/开立科室上下文）。与 B.10.11 会话管辖机构收窄分离 |
| `keyword`        | String    | N   | 姓名 / 拼音 / 五笔 **OR** 匹配（QueryService内实现）      |
| `startDate`      | LocalDateTime | N   | 就诊开始时间下界（与工程 `AbstractPageQuery` 一致；工作台默认当天 00:00:00） |
| `endDate`        | LocalDateTime | N   | 就诊开始时间上界（工作台默认当天 23:59:59）                              |
| `pendingDeviceOnly` | Boolean | N | **true**=仅仍有待发耗材的就诊（**BR-NUR-017**，域 EXISTS **分页前**生效）；空/false=不收窄 |
| `pageNo`        | Integer   | N   | 页码，默认 1（B.10.11；1-based） |
| `pageSize`       | Integer   | N   | 页大小                                 |


**Response `PageNurseStationPatientQueueResponse`**


| 字段        | 类型                                      | 说明   |
| --------- | --------------------------------------- | ---- |
| `records` | `List<NurseStationPatientQueueItemDto>` | 当前页行 |
| `total`   | Long                                    | 总条数  |


`**NurseStationPatientQueueItemDto**`


| 字段              | 类型            | 说明                        |
| --------------- | ------------- | ------------------------- |
| `encounterId`   | Long          | 就诊主键                      |
| `patientName`   | String        | 患者姓名（来自QueryService投影）             |
| `genderEnum`    | String        | 性别 code                   |
| `birthDate`     | LocalDate     | 出生日期                      |
| `ageText`       | String        | 派生年龄展示（可选）                |
| `slipNo`        | String        | 业务流水/挂号相关展示字段（若QueryService提供）     |
| `encounterTime` | LocalDateTime | 就诊时间（同源 **Q-OPI-001** 列表） |
| `contractDisplay` | String | 费用性质（有医保账户优先合同名） |
| `hasPendingDevice` | Boolean | 是否仍有待发耗材（**BR-NUR-017**；对齐处置单侧栏「待发放」） |


**失败码口径**


| 语义（AS）     | ErrorCode（示例） | 场景    |
| ---------- | ------------- | ----- |
| EX-NUR-101 | 运行时异常 / 统一网关  | 查询不可用 |


**Mapper 接口方法签名**

- `toNurseStationOutpatientQueueQuery(PageNurseStationPatientQueueRequest): NurseStationOutpatientQueueQuery`（与 `EncounterContextReader#pageNurseStationOutpatientQueue` 入参一致；含可选 `orgId`）
- `toPageNurseStationPatientQueueResponse(Page<OutpatientEncounterContextQueryModel>): PageNurseStationPatientQueueResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                                               | 输入要点             | 说明                                         |
| --- | ---- | ------------------------------------------------------------------------------------------------ | ---------------- | ------------------------------------------ |
| 1   | Gate | App 入参校验                                                                                         | Request 非空 | 空体拒绝；**不**强制 `orgId`（空=全部） |
| 2   | Pre  | `OutpatientNurseStationPatientMapper#toNurseStationOutpatientQueueQuery`                                | Request          | 映射为 `NurseStationOutpatientQueueQuery`（含可选 `orgId` / `startDate` / `endDate` / `pendingDeviceOnly`） |
| 3   | Pre  | `EncounterContextReader#pageNurseStationOutpatientQueue` | ②                | **仅读路径**；**BR-NUR-001** EXISTS + 可选 `organization_id`；`pendingDeviceOnly=true` 时叠加 **BR-NUR-017** EXISTS（分页前） |
| 4   | Asm  | `OutpatientNurseStationPatientMapper#toPageNurseStationPatientQueueResponse`                            | ③ `Page`         | 组装 DTO                                     |
| 5   | Asm  | `EncounterContractDisplayAssembler#mapContractDisplayByEncounterIds` | 本页 `encounterId` | 行内 `contractDisplay` |
| 6   | Asm  | `DeviceRequestQueryService#listDeviceRequestsByEncounterIds` + `DeviceDispenseQueryService#listDeviceDispensesByEncounterIds` | 本页 `encounterId` | 行内 `hasPendingDevice`（**BR-NUR-017**） |


---

### 3.3 IF-NUR-02 `queryEncounterTreatmentSheet`（按就诊查询处置单）


| 项        | 内容                                                                  |
| -------- | ------------------------------------------------------------------- |
| **HTTP** | `POST /api/app/outpatientNurseStationTreatment/queryEncounterTreatmentSheet` |


**Request `QueryEncounterTreatmentSheetRequest`**


| 字段                    | 类型     | 必填  | 说明                                    |
| --------------------- | ------ | --- | ------------------------------------- |
| `encounterId`         | Long   | Y   | 当前选中就诊                                |
| `serviceCategoryCode` | String | N   | **仅缩小诊疗列表**（**BR-NUR-009**），药品/耗材列表忽略 |


**Response `QueryEncounterTreatmentSheetResponse`**


| 字段                | 类型                                   | 说明    |
| ----------------- | ------------------------------------ | ----- |
| `serviceLines`    | `List<NurseStationTreatmentLineDto>` | 诊疗申请行 |
| `medicationLines` | `List<NurseStationTreatmentLineDto>` | 药品申请行 |
| `deviceLines`     | `List<NurseStationTreatmentLineDto>` | 耗材相关行（**BR-NUR-018**：含 PLANNED/SETTLED 与 REFUNDING/REFUNDED；退费态展示不可勾选；有效计费下不含作废发放） |


`**NurseStationTreatmentLineDto`（三列表字段名一致，见 InpatientOrderListSkeleton §3.4.2）**


| 字段                     | 类型         | 说明                                                                        |
| ---------------------- | ---------- | ------------------------------------------------------------------------- |
| `encounterId`          | Long       | 就诊                                                                        |
| `requestId`            | Long       | 请求主键                                                                      |
| `dispenseId`           | Long       | 发放锚点；诊疗可空                                                                 |
| `requestTable`         | String     | `SERVICE` / `MEDICATION` / `DEVICE`（持久化 code）                             |
| `orderType`            | String     | 与 `requestTable` 同义展示（可选，与前端约定二选一）                                        |
| `itemCode`             | String     | 目录业务编码：优先各定义表 `bus_no`；缺失时回落为定义主键字符串（**禁止**占位常量）                         |
| `prescriptionNo`       | String     | **药品**：`med_medication_request.prescription_no`（`CP` 处方分方号，**禁止**回落 `bus_no`/MR）；**诊疗**：`wkf_service_request.prescription_no`，**`AR` 前缀**为检验/检查/治疗申请单整单号（**BR-NUR-015**）；耗材可空 |
| `itemName`             | String     | 目录展示名：诊疗 `wkf_activity_definition.name` / 药品 `med_medication_definition.name` / 耗材 `adm_device_definition.name`；定义缺失时回落 `OutpatientNurseStationConstants` 占位文案 |
| `specText`             | String     | 规格：药品 `total_volume` 或 `comprised_text`；耗材优先请求 `device_specifications`，否则定义 `total_volume` |
| `pyStr`                | String     | 拼音检索码（来自对应定义表）                                                         |
| `wbStr`                | String     | 五笔检索码（来自对应定义表）                                                         |
| `serviceCategoryCode`  | String     | **仅诊疗**：`wkf_activity_definition.category_code`（**非** `wor_service_request.therapy_enum`） |
| `serviceCategoryCodeText` | String  | **仅诊疗**：`activity_category_code` 术语展示名（`CodeSystemQueryService`）              |
| `deviceCategoryCode`   | String     | **仅耗材**：`adm_device_definition.category_code`                                      |
| `deviceCategoryCodeText` | String   | **仅耗材**：`device_category_code` 术语展示名                                            |
| `medCategoryCode`      | String     | **仅药品**：`med_medication_definition.category_code`（**非** `med_medication_request.therapy_enum`） |
| `medCategoryCodeText`  | String     | **仅药品**：`medication_category_code` 术语展示名                                        |
| `quantity`             | BigDecimal | 数量                                                                        |
| `unitCode`             | String     | 单位编码                                                                      |
| `unitName`             | String     | **仅药品**：开立/发药单位展示名（术语 `unit_code` + 产品字典回落，**BR-ORD-022**）              |
| `dose`                 | BigDecimal | **仅药品**：单次剂量（`med_medication_request.dose`）                              |
| `doseUnitCode`         | String     | **仅药品**：剂量单位编码（`med_medication_request.dose_unit_code`）                  |
| `doseUnitName`         | String     | **仅药品**：剂量单位展示名（**BR-ORD-022**）                                        |
| `methodCode`           | String     | **仅药品**：用法编码（`med_medication_request.method_code`）                         |
| `methodName`           | String     | **仅药品**：用法展示名（术语 `method_code`）                                         |
| `rateCode`             | String     | **仅药品**：频次编码（`med_medication_request.rate_code`）                           |
| `rateName`             | String     | **仅药品**：频次展示名                                                           |
| `dispensePerDuration`  | Integer    | **仅药品**：用药天数（`med_medication_request.dispense_per_duration`）               |
| `plannedPerformCount`  | Integer    | **仅诊疗**：计划次数                                                              |
| `medicationExecuteNum` | Integer    | **仅药品**：`execute_num`                                                     |
| `performCount`         | Integer    | **仅诊疗**：完成次数                                                              |
| `cancelCount`          | Integer    | **仅诊疗**：取消次数                                                              |
| `chargeStatusEnum`     | String     | **首条**：关联 `adm_charge_item.id` **升序** 第一条 **status_enum**（**BR-ORD-005**） |
| `chargeStatusEnumText` | String     | `ChargeItemStatus` 展示文案（`EnumUtil#getDisplay`）                              |
| `displayTotalPrice`    | BigDecimal | 关联收费明细 **金额之和**（锚点与退费过滤见 **Q-ORD-006**）                                   |
| `unitPrice`            | BigDecimal | 可选                                                                        |
| `dispenseStatusEnum`   | String     | 发放状态持久化 code                                                           |
| `dispenseStatusEnumText` | String   | `DispenseStatus` 展示文案（`EnumUtil#getDisplay`）                              |
| `refundPairRequestId`  | Long       | 退费关联                                                                      |
| `groupId`              | Long       | 成组                                                                        |
| `sortNumber`           | Integer    | 排序                                                                        |
| `lastPerformerPractitionerId` | Long | 最近已完成执行的主执行人主键（`cln_procedure_performer`，暂 1:1） |
| `lastPerformerDisplay` | String     | 最近主执行人展示名（读侧装配） |
| `remark` | String | **申请单诊疗行**：`wkf_service_request.remark` 纯备注；非申请单可空 |
| `serviceRequestDetails` | List\<`ServiceRequestDetailSummaryDto`\> | **申请单诊疗行**：部位/样本 Detail 摘要（`targetTable`/`targetId`/`targetDisplayName`/`remark`）；对齐 IF-DOR-10；非申请单可 null/空 |


**关键校验**：**BR-NUR-002～003**、**BR-NUR-009～010**、**BR-NUR-015**、**BR-NUR-016**、**BR-NUR-018**；药品/耗材行 `**plannedPerformCount` / `performCount` / `cancelCount` 置 null**（与 **Q-ORD-006** 一致）。**药品列表**须额外回显规格（`specText`）、单次剂量/总量（`dose*` / `quantity` + `unitName`）、用法/频次/天数（`method*` / `rate*` / `dispensePerDuration`），展示口径与 IF-DOR-10 **BR-ORD-022** 一致；诊疗/耗材行上述槽位置 **null**。**诊疗/药品行**在存在净剩 **COMPLETED** Procedure 时回填 `lastPerformer*`（**BR-NUR-013** 对冲后）。**诊疗列表**须在 Asm 前按 **BR-NUR-015** 对 `prescriptionNo`（`AR` 前缀申请单）聚邻排序；**申请单诊疗行**须在 Asm 前批量回填 `remark` + `serviceRequestDetails`（**BR-NUR-016**）。

**类目筛选与回显分离（IF-NUR-02）**：Request `serviceCategoryCode` **仅用于缩小诊疗列表**，比对 **`wor_service_request.therapy_enum`**（**BR-NUR-009**）；Response 行内 `serviceCategoryCode` / `serviceCategoryCodeText` 为**活动定义类目**（`wkf_activity_definition.category_code` + 术语），二者语义不同，禁止混用。

**失败码口径**


| 语义（AS）     | ErrorCode（示例）           | 场景       |
| ---------- | ----------------------- | -------- |
| EX-NUR-102 | `ENCOUNTER_NOT_FOUND` 等 | 就诊不存在或非法 |


**Mapper 接口方法签名**

- `toQueryEncounterTreatmentSheetResponse(QueryEncounterTreatmentSheetRequest request, EncounterDetailQueryModel encounter, List<NurseStationTreatmentLineDto> serviceLines, List<NurseStationTreatmentLineDto> medicationLines, List<NurseStationTreatmentLineDto> deviceLines): QueryEncounterTreatmentSheetResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                                                                                                     | 输入要点                                     | 说明                                                                          |
| --- | ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- | --------------------------------------------------------------------------- |
| 1   | Gate | App 入参校验                                                                                                                                               | `encounterId`                            | —                                                                           |
| 2   | Pre  | `EncounterQueryService#mustGetEncounter(Long)`                                                                                                         | `encounterId`                            | 校验就诊存在                                                                      |
| 3   | Pre  | `ServiceRequestQueryService#listServiceRequestsByEncounter(Long)`                                                                                      | `encounterId`                            | 过滤 **COMPLETED**；Request `serviceCategoryCode` 比对 `therapy_enum`（**BR-NUR-009**） |
| 4   | Pre  | `MedicationRequestQueryService#listMedicationRequestsByEncounter(Long)`                                                                                | `encounterId`                            | 过滤 **COMPLETED**                                                            |
| 5   | Pre  | `DeviceRequestQueryService#listDeviceRequestsByEncounter(Long)`                                                                                        | `encounterId`                            | 过滤退费镜像 **BR-NUR-003**；**BR-NUR-018** 剔除收费 REFUNDING/REFUNDED 与 DECLINED 等作废发放 |
| 6   | Pre  | `ChargeItemQueryService#listChargeItemsByEncounterId(ChargeItemQueryCommand)`                                                                          | `encounterId`                            | 装配 `**displayTotalPrice`** / `**chargeStatusEnum**`（按 **Q-ORD-006** §3.4.3） |
| 7   | Pre  | `ProcedureQueryService#getProcedureRecords(Long, String)`                                                                                              | 每条诊疗 `requestId` + `wkf_service_request` | 填充诊疗 `**performCount`/`cancelCount`**                                       |
| 8   | Pre  | `DeviceDispenseQueryService#listDeviceDispensesByEncounter` + **`#mapDeviceDispenseIdsByDeviceRequestIds`**（**禁止**循环 `#listDeviceDispenseIdsByDeviceRequest`） | 耗材请求 id 集合                               | 加载发放态；医嘱→发放单 id 列表（**A.9.1** 一次 IN + 内存分组） |
| 8a  | Pre  | `OrderRequestCatalogWarmAssembler#prepareFromTreatmentSheetRows(...)`                                                                                 | ③～⑤ 请求行 id 集合                            | 批量加载定义表 + 预热类目术语；装配 `itemName` / `*CategoryCode*` / `*CategoryCodeText` |
| 8b  | Pre  | App `prepareMedicationDisplayWarm` + `TerminologyOptionLoader` / `FrequencyOptionLoader` / `ProductDefinitionBatchLoader`                              | ④ 药品申请列表                                 | 预热单位/用法/频次/剂量单位术语 Map 与产品字典；`buildMedicationLine` 写入 `dose*` / `method*` / `rate*` / `unitName` / `dispensePerDuration`（**BR-ORD-022**） |
| 8c  | Pre  | `ServiceRequestQueryService#mapDetailListModelsByServiceRequestIds` + `ServiceApplyFormAssembler#toDetailSummaryDtos`                               | ③ 中 `AR` 前缀申请单 requestId 集合              | 批量回填申请单 `serviceRequestDetails` 与 `remark`（**BR-NUR-016**，对齐 IF-DOR-10） |
| 9   | Asm  | App 内 `buildServiceLine` / `buildMedicationLine` / `buildDeviceLine` + Mapper 包装响应                                                                  | ③～⑧c                                    | **仅读路径**；`deviceLines` 经 **BR-NUR-018** 过滤；**诊疗列表** Asm 前 `sortServiceLinesForApplyFormGrouping`（**BR-NUR-015**） |


---

### 3.4 IF-NUR-03 `listProcedureRecordsForNurseStation`（分页查询执行记录）


| 项        | 内容                                                                         |
| -------- | -------------------------------------------------------------------------- |
| **HTTP** | `POST /api/app/outpatientNurseStationTreatment/listProcedureRecordsForNurseStation` |


**Request `ListProcedureRecordsForNurseStationRequest`**


| 字段             | 类型            | 必填  | 说明                                            |
| -------------- | ------------- | --- | --------------------------------------------- |
| `encounterId`  | Long          | Y   | 就诊                                            |
| `requestId`    | Long          | N   | 缩窄单条医嘱                                        |
| `statusEnum`   | String        | N   | **EventStatus** code                          |
| `categoryEnum` | String        | N   | 执行分类                                          |
| `startTime`    | LocalDateTime | N   | 对应 **OrderExecutionSheetPageQuery.startTime** |
| `endTime`      | LocalDateTime | N   | **endTime**                                   |
| `pageNum`      | Integer       | N   | 页码；与 `pageNo` 等价，Mapper 并入 `AbstractPageQuery#pageNo`（B.10.11） |
| `pageSize`     | Integer       | N   | 页大小                                           |


**Response `ListProcedureRecordsForNurseStationResponse`**


| 字段        | 类型                                 | 说明    |
| --------- | ---------------------------------- | ----- |
| `records` | `List<OrderExecutionSheetItemDto>` | 执行记录行 |
| `total`   | Long                               | 总条数   |


`**OrderExecutionSheetItemDto`（源自 `OrderExecutionSheetQueryModel`）**


| 字段                  | 类型            | 说明         |
| ------------------- | ------------- | ---------- |
| `procedureId`       | Long          | 执行记录主键     |
| `requestId`         | Long          | 来源请求       |
| `requestTable`      | String        | 来源表名       |
| `statusEnum`        | String        | 执行状态       |
| `categoryEnum`      | String        | 分类         |
| `patientId`         | Long          | 患者         |
| `encounterId`       | Long          | 就诊         |
| `occurrenceTime`    | LocalDateTime | 发生时间       |
| `recordedTime`      | LocalDateTime | 记录时间       |
| `recorderId`        | Long          | 记录人        |
| `resultText`        | String        | 结果摘要       |
| `orderItemDisplayName` | String | 对应医嘱/项目展示名（目录定义回填，供抽屉「目标」列） |
| `actionDisplayText` | String | 操作展示文案（如「执行完成」「取消执行」） |
| `operatorDisplayName` | String | 本条记录操作人姓名（完成=执行人，取消=取消执行人） |
| `operatorRoleDisplayText` | String | 操作人列标题语义（「执行人」/「取消执行人」） |


**失败码口径**


| 语义（AS）     | 场景   |
| ---------- | ---- |
| EX-NUR-103 | 查询失败 |


**Mapper 接口方法签名**

- `toOrderExecutionSheetPageQuery(ListProcedureRecordsForNurseStationRequest): OrderExecutionSheetPageQuery`
- `toListProcedureRecordsForNurseStationResponse(Page<OrderExecutionSheetQueryModel>): ListProcedureRecordsForNurseStationResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                                        | 输入要点          | 说明                                              |
| --- | ---- | ----------------------------------------------------------------------------------------- | ------------- | ----------------------------------------------- |
| 1   | Gate | App 入参校验                                                                                  | `encounterId` | —                                               |
| 2   | Pre  | `EncounterQueryService#mustGetEncounter(Long)`                                            | `encounterId` | —                                               |
| 3   | Pre  | `OutpatientNurseStationTreatmentMapper#toOrderExecutionSheetPageQuery`                             | Request       | —                                               |
| 4   | Pre  | `OrderExecutionSheetLoader#pageOrderExecutionSheet(OrderExecutionSheetPageQuery)` | ③             | **仅读路径**；必要时 `**listOrderExecutionSheet`** 改不分页 |
| 5   | Asm  | `OutpatientNurseStationTreatmentMapper#toListProcedureRecordsForNurseStationResponse`              | ④             | —                                               |
| 6   | Asm  | App 内 `enrichExecutionSheetDrawerDisplay`：批量预热目录 + 回填 `orderItemDisplayName` / `actionDisplayText` / `operatorDisplayName` / `operatorRoleDisplayText` | ⑤             | **禁止**前端解析 JSON；时间列用 `occurrenceTime`（回落 `recordedTime`） |


> **与 IF-NUR-03B 分工**：本接口返回**全量**执行记录（含 **CANCEL**、已对冲 **COMPLETED**），供审计/查看全部；**可取消勾选**须调用 **IF-NUR-03B**。

---

### 3.4.1 IF-NUR-03B `listNetRemainingProcedureRecordsForNurseStation`（净剩可取消执行记录）


| 项        | 内容                                                                                      |
| -------- | --------------------------------------------------------------------------------------- |
| **HTTP** | `POST /api/app/outpatientNurseStationTreatment/listNetRemainingProcedureRecordsForNurseStation` |


**Request `ListNetRemainingProcedureRecordsForNurseStationRequest`**


| 字段             | 类型     | 必填  | 说明                                      |
| -------------- | ------ | --- | --------------------------------------- |
| `encounterId`  | Long   | Y   | 就诊                                      |
| `requestId`    | Long   | N   | 缩窄单条来源请求（与 `requestTable` 成对）            |
| `requestTable` | String | N   | `wkf_service_request` / `med_medication_request` |


**Response `ListNetRemainingProcedureRecordsForNurseStationResponse`**


| 字段        | 类型                                      | 说明                          |
| --------- | --------------------------------------- | --------------------------- |
| `records` | `List<NetRemainingProcedureRecordItemDto>` | 净剩 **COMPLETED** 行（无分页）     |


**`NetRemainingProcedureRecordItemDto`（核心字段）**


| 字段              | 类型            | 说明                                    |
| --------------- | ------------- | ------------------------------------- |
| `procedureId`   | Long          | 执行记录主键；取消时回传 **IF-NUR-05** `procedureIds` |
| `requestId`     | Long          | 来源请求                                  |
| `requestTable`  | String        | 来源表                                   |
| `statusEnum`    | String        | 固定 **COMPLETED** code                 |
| `recordedTime`  | LocalDateTime | 记录时间（列表倒序）                            |
| `occurrenceTime` | LocalDateTime | 发生时间                                  |
| `resultText`    | String        | 结果摘要                                  |


**失败码口径**


| 语义（AS）     | 场景                         |
| ---------- | -------------------------- |
| EX-NUR-103 | 查询失败                       |
| 参数错误       | `requestId` 有值但 `requestTable` 为空 |


**领域编排序列**


| 序号  | 阶段   | 调用                                                                                              | 说明                                      |
| --- | ---- | ----------------------------------------------------------------------------------------------- | --------------------------------------- |
| 1   | Gate | `encounterId` 非空；成对缩窄校验                                                                       | —                                       |
| 2   | Pre  | `EncounterQueryService#mustGetEncounter`                                                        | —                                       |
| 3   | Pre  | `ProcedureQueryService#listNetRemainingCompletedProcedureRecords(encounterId, requestId, requestTable)` | **BR-NUR-013**：排除已被 `refund_id` 对冲的 COMPLETED |
| 4   | Asm  | App 内按 `recordedTime`、`id` 倒序；`Mapper#toListNetRemainingProcedureRecordsForNurseStationResponse` | —                                       |


---

### 3.5 IF-NUR-04 `batchPerformDisposition`（批量处置执行）


| 项        | 内容                                                             |
| -------- | -------------------------------------------------------------- |
| **HTTP** | `POST /api/app/outpatientNurseStationTreatment/batchPerformDisposition` |


**Request `BatchPerformDispositionRequest`**


| 字段                        | 类型           | 必填  | 说明                                          |
| ------------------------- | ------------ | --- | ------------------------------------------- |
| `encounterId`             | Long         | Y   | 就诊                                          |
| `operatorId`              | Long         | N   | 操作/执行医务人员（`adm_practitioner.id`）；未传时服务端取当前登录绑定医务人员 |
| `inventoryLocationId`     | Long         | N   | **耗材房**：传入则写前断言目标房签发批号库存，再**就地只**回写发放行 `location_id` 与请求 `perform_location`（**禁止**改批号、**禁止**FIFO、**禁止**软删重建、**禁止**改账单）；未传回落医生签发 `location_id`/批号仍断言库存（**BR-NUR-006**；院点可仅前端必选） |
| `serviceRequestIds`       | `List<Long>` | N   | 勾选的诊疗请求 id                                  |
| `medicationRequestIds`    | `List<Long>` | N   | 勾选的药品请求 id                                  |
| `deviceDispenseIds`       | `List<Long>` | N   | 勾选的待发耗材发放行 id（或发放单 id，前后端约定其一，须在实现固定）       |


**Response `BatchPerformDispositionResponse`**


| 字段                           | 类型           | 说明          |
| ---------------------------- | ------------ | ----------- |
| `success`                    | Boolean      | 是否整批成功      |
| `performedServiceIds`        | `List<Long>` | 实际完成登记的诊疗请求 |
| `performedMedicationIds`     | `List<Long>` | 实际完成登记的药品请求 |
| `completedDeviceDispenseIds` | `List<Long>` | 完成发放的耗材行    |


**失败码口径（对齐 AS）**


| 语义（AS）         | ErrorCode（示例）                       | 场景         |
| -------------- | ----------------------------------- | ---------- |
| EX-NUR-201     | `*_INVALID_ARGUMENT`                | 未勾选任何项     |
| EX-NUR-202     | `CHARGE_ITEM_*` / 业务规则              | 剔除后无已计费项   |
| EX-NUR-203     | `PROCEDURE_*`                       | 执行登记失败     |
| EX-NUR-204     | `DEVICE_DISPENSE_*`                 | **BR-NUR-014 开**且未锁库房但未传耗材房 |
| EX-NUR-205～207 | `INVENTORY_*` / `DEVICE_DISPENSE_*` | 库存不足（目标房签发批号）/ 发放态 |
| EX-NUR-208     | 持久化异常                               | 发放批量保存失败   |


**Mapper 接口方法签名**

- `toChargeItemQueryCommand(Long encounterId): ChargeItemQueryCommand`
- `toCreateProcedureRecordCommand(Long requestId, String requestTable, Long encounterId, Long patientId, String categoryEnum, LocalDateTime occurrenceTime, Long operatorId): CreateProcedureRecordCommand`
- `toStartProcedureExecutionCommand(Long procedureId): StartProcedureExecutionCommand`
- `toCompleteProcedureRecordCommand(Long procedureId, Long operatorId): CompleteProcedureRecordCommand`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                                                                                                       | 输入要点                                            | 说明                                                                            |
| --- | ---- | -------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ----------------------------------------------------------------------------- |
| 1   | Gate | App 入参校验                                                                                                                                                 | 三类 id 列表至少其一非空；`encounterId`       | **EX-NUR-201**                                                                |
| 2   | Pre  | `CurrentPractitionerResolver#requireOperatorId`（`operatorId` 未传时）                                                                                      | —                                        | 未绑定医务人员 → **EX-NUR-204** 类语义                                              |
| 3   | Pre  | `EncounterQueryService#mustGetEncounter(Long)`                                                                                                           | `encounterId`                                   | —                                                                             |
| 4   | Pre  | `ChargeItemQueryService#listChargeItemsByEncounterId` + 按请求锚点聚合                                                                                          | 诊疗/药品勾选 id                                      | **BR-NUR-004**                                                                |
| 5   | Pre  | 对保留的每个 **诊疗/药品**：净次数 vs 计划次数                                                                                                           | **BR-NUR-005**                                                                |
| 6   | Cmd  | **诊疗/药品**：`create→start→complete` → **`ProcedureExecutionPerformerOrchestrationSupport#registerPrimaryPerformer`**（`functionCode=PRIMARY`，1:1）                                                                                                                                 | —                                               | **写库点**（`cln_procedure_performer`）                                                                             |
| 7   | Pre  | **若** `deviceDispenseIds` 非空：读 **BR-NUR-014**；**开**时门禁：传入 `inventoryLocationId` 则管辖校验，且逐行须已有真实批号（换房不补批）（**BR-NUR-006**） | `inventoryLocationId` 可空 | 缺批号 → 拒绝；未传房且缺库房 → **EX-NUR-204** |
| 8   | Cmd  | **`DeviceDispenseNurseWorkflowOrchestrator#applyOutpatientNurseDeviceDispenses`**                                                                                   | 已解析 `practitionerId`、`inventoryLocationId`、输出 `completedDispenseIds`、库存累计 Map | **写库点**：写前断言目标房签发批号库存（不足→库存不足）；**关**→PREPARED；**开**→就地只改 location（不改批号）+COMPLETED；**禁止**软删重建/改账单 |
| 9   | Cmd  | **若**库存 Map 非空：`InventoryItemDomainService#decreaseQuantitiesMerged`                                                                       | 批次末合并扣减                                            | **写库点**（**仅 BR-NUR-014 开**）                                                                       |
| 10  | Asm  | 组装 `BatchPerformDispositionResponse`                                                                                                                     | —                                               | —                                                                             |


**互斥路径 A（诊疗 / 药品 → Procedure）**

- **当前实现**：对每个允许执行的请求依次 `**createProcedureRecord**`（命令 `**skipDuplicateRequestAnchorCheck=true**`）→ `**startProcedureExecution**` → `**completeProcedureRecord**`（占位摘要）。其它应用服务若未置该标志，仍受 **BR-PRO-001** 同锚点幂等保护。
- **目标态（可选）**：合并为单一原子命令 `**registerOutpatientAdviceExecution**`（FQCN 待 DM 裁决后生成）。

**条件分支**

- **若**步骤 3 剔除后 **仅耗材**且 WorkflowSupport 有完成行：允许仅执行步骤 6～8。
- **若**仅诊疗药品且无耗材：跳过步骤 6～8。
- **若**并发导致待发记录非准备中：跳过或失败返回 **BR-NUR-008**。

---

### 3.6 IF-NUR-05 `batchCancelDisposition`（批量取消执行）


| 项        | 内容                                                            |
| -------- | ------------------------------------------------------------- |
| **HTTP** | `POST /api/app/outpatientNurseStationTreatment/batchCancelDisposition` |


**Request `BatchCancelDispositionRequest`**


| 字段                     | 类型           | 必填  | 说明                                       |
| ---------------------- | ------------ | --- | ---------------------------------------- |
| `encounterId`          | Long         | Y   | 就诊                                       |
| `operatorId`           | Long         | N   | 操作医务人员；未传时服务端取当前登录绑定医务人员                  |
| `statusReasonText`     | String       | Y   | 取消原因（**BR-PRO-005**；写入 `CancelProcedureRecordCommand`） |
| `procedureIds`         | `List<Long>` | N   | 勾选的**已完成**执行记录主键（`cln_procedure.id`）；每条产生一条 **CANCEL**，`refundId` 指向该 id |
| `deviceDispenseIds`    | `List<Long>` | N   | 勾选的耗材发放行 id（**PREPARATION** 待执行，或 **PREPARED/COMPLETED** 可取消）                         |


**Response `BatchCancelDispositionResponse`**


| 字段                          | 类型           | 说明                 |
| --------------------------- | ------------ | ------------------ |
| `success`                   | Boolean      | 是否整批成功             |
| `cancelledProcedureIds`     | `List<Long>` | 本批实际完成对冲的 **COMPLETED** 执行记录主键 |
| `revertedDeviceDispenseIds` | `List<Long>` | 实际回退准备中的耗材发放行      |


**失败码口径（对齐 AS）**


| 语义（AS）     | ErrorCode（示例）                       | 场景              |
| ---------- | ----------------------------------- | --------------- |
| EX-NUR-209 | `*_INVALID_ARGUMENT`                | 未勾选任何可取消项       |
| EX-NUR-210 | `PROCEDURE_*` / 业务规则              | 执行记录不可取消（非 COMPLETED、已对冲、非本就诊、同批重复 id 等）   |
| EX-NUR-211 | `DEVICE_DISPENSE_INVALID_STATUS`    | 发放态非 PREPARED/COMPLETED，不可取消    |
| EX-NUR-213 | `DEVICE_*` / `PROCEDURE_*`          | 发放回退失败     |


**Mapper 接口方法签名**

- `toCancelProcedureRecordCommand(Long procedureId, String statusReasonText, Long refundId, Long operatorId): CancelProcedureRecordCommand`
- `toRevertCompletedBatchToPreparationCommand(List<Long> dispenseIds, Long operatorId): RevertCompletedBatchToPreparationCommand`（WorkflowSupClient 内部构建，Mapper 可选）
- `toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToQty): IncreaseQuantityCommand`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                                                                                 | 输入要点                                      | 说明                                                                                  |
| --- | ---- | ---------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------------- |
| 1   | Gate | App 入参校验                                                                                                                           | `procedureIds` 与 `deviceDispenseIds` 至少其一非空 | **EX-NUR-209**          |
| 2   | Pre  | `EncounterQueryService#mustGetEncounter(Long)`                                                                                     | `encounterId`                             | —                                                                                   |
| 3   | Pre/Cmd  | 对每个 **`procedureId`**：校验 COMPLETED + 追加 CANCEL（**互斥路径 B**）              | —                                         | **写库点**                                                                             |
| 4   | Cmd  | **`DeviceDispenseNurseWorkflowSupport#revertOutpatientNurseDeviceDispenses`**                                                                 | `deviceDispenseIds`、库存回补 Map         | **PREPARED**→`revertPreparedBatchToPreparation`；**COMPLETED**→`revertCompletedBatchToPreparation`+回补 |
| 5   | Cmd  | **若**回补 Map 非空：`InventoryItemDomainService#increaseQuantities`                                      | 批次末合并回补                          | **写库点**（**仅开关开**路径）                                                                             |
| 6   | Asm  | 组装 `BatchCancelDispositionResponse`                                                                                                | —                                         | —                                                                                   |


**互斥路径 B（诊疗 / 药品 → 追加 CANCEL）**

- 对每个 Request.`procedureIds` 中的 **COMPLETED** 主键依次：`createProcedureRecord`（`**skipDuplicateRequestAnchorCheck=true**`，锚点取自该 COMPLETED 的 `requestId`/`requestTable`）→ `startProcedureExecution` → `cancelProcedureRecord`（`statusReasonText` 来自 Request；**`refundId` = 该 COMPLETED 的 `procedureId`**，见 **BR-PRO-005** / **BR-NUR-011**）。
- **禁止**对既有 **COMPLETED** 记录直接调用 `cancelProcedureRecord` 作为批量取消主路径（见 **DOC-CONFLICT-NUR-P03**）。

**条件分支**

- **若**仅诊疗/药品且无耗材：跳过步骤 4～5。
- **若**仅耗材且无诊疗/药品 Cmd：跳过步骤 3。

---

## 4. QueryService 与事件刷新


| QueryService                     | 用途              | 投影触发（语义）                                                                                                                                              |
| ----------------------- | --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **OutpatientEncounterContextQueryModel**      | IF-NUR-01 队列    | 就诊/申请侧事件 → **Q-OPI-004** 已由QueryService 刷新（见 OutpatientEncounterContextQueryModel §4）                                                                                                 |
| **InpatientOrderListSkeleton**（组装视图）     | IF-NUR-02 处置单   | 非单一表：由应用组合 **ServiceRequest/MedicationRequest/DeviceRequest** + **ChargeItem** + **Procedure**                                                        |
| **OrderExecutionSheet** | IF-NUR-03 全量列表；IF-NUR-04 / 05 写后 | **ProcedureRecordCreatedEvent**、**ProcedureRecordCompletedEvent**、**ProcedureRecordCancelledEvent**（IF-NUR-05 取消路径，见 **DM-CLN-004 §5**） |
| **（域表 `cln_procedure`）** | IF-NUR-03B 净剩列表；IF-NUR-05 写后即时可读 | 不经过 OrderExecutionSheet 投影；应用层 `ProcedureQueryService` 直接查询 |
| **供应链与库存QueryService**（若启用）      | IF-NUR-04 / 05 库存展示 | **InventoryItem** 数量变更事件（扣减/回补）                                                                                                                              |


字段级映射以各DM §6.2 **§4.2** 为真源；本文不展开字段映射细表。IF-NUR-05 **不**消费 `ChargeItemExecutionCancelledEvent`。

---

## 5. 写库顺序（核心写接口视角）

> Gate / Pre 见 **§3.5**；本节汇总 **IF-NUR-04 Cmd** 与事务。

### 5.1 Cmd 落库顺序一览（与 §3.5 序号对照）


| IF        | App 方法                    | Cmd 序号（§3.5） | 领域写服务#方法                                                                                            | 聚合（语义顺序）       |
| --------- | ------------------------- | ------------ | --------------------------------------------------------------------------------------------------- | -------------- |
| IF-NUR-04 | `batchPerformDisposition` | 6            | `ProcedureDomainService#create/start/complete`（目标态）或等价原子命令                                          | Procedure      |
| IF-NUR-04 | `batchPerformDisposition` | 7            | `DeviceDispenseNurseWorkflowSupport#applyOutpatientNurseDeviceDispenses` | DeviceDispense |
| IF-NUR-04 | `batchPerformDisposition` | 8            | `InventoryItemDomainService#decreaseQuantitiesMerged`（条件）               | InventoryItem  |
| IF-NUR-05 | `batchCancelDisposition`  | 4            | `DeviceDispenseNurseWorkflowSupport#revertOutpatientNurseDeviceDispenses` | DeviceDispense |
| IF-NUR-05 | `batchCancelDisposition`  | 5            | `InventoryItemDomainService#increaseQuantities`（条件）                         | InventoryItem  |


### 5.2 事务与回滚口径


| IF        | 事务边界                                                 | 失败回滚                                |
| --------- | ---------------------------------------------------- | ----------------------------------- |
| IF-NUR-04 | **单次调用一事务**：诊疗药品 Cmd + 耗材 Cmd + 库存扣减须在同事务（AS §7 一致性） | 任一 Cmd 失败整体回滚；**禁止**部分成功除非产品另行开立 AS |
| IF-NUR-05 | **单次调用一事务**：诊疗药品取消 Cmd + 耗材对称回退 + 条件库存回补 | 任一 Cmd 失败整体回滚 |


---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 接口        | 幂等键设计                                                                        | 关联步骤         |
| --------- | ---------------------------------------------------------------------------- | ------------ |
| IF-NUR-04 | 执行次数满额后重复提交：**不得**新增完成记录（**BR-NUR-005**）；耗材：**非准备中**行不得重复扣库存（**BR-NUR-008**） | §3.5 序号 4～10 |
| IF-NUR-05 | 净次数为 0 重复取消：**不得**新增 CANCEL 记录（**BR-NUR-011**）；已回退准备中的发放行不得重复回补 | §3.6 序号 3～9 |


### 6.2 并发控制


| 接口        | 场景       | 策略                       | 关联步骤        |
| --------- | -------- | ------------------------ | ----------- |
| IF-NUR-04 | 多护士同待发耗材 | 后发者校验；乐观失败返回可理解提示（AS §7） | §3.5 序号 7～8 |
| IF-NUR-05 | 多护士同已发放耗材取消 | 后发者校验发放态；失败返回可理解提示 | §3.6 序号 6～9 |


### 6.3 重试与容错


| 接口           | 策略          |
| ------------ | ----------- |
| IF-NUR-01～03 | 读接口可有限重试    |
| IF-NUR-04    | 不自动重试；明确业务码 |
| IF-NUR-05    | 不自动重试；明确业务码 |


### 6.4 数据脱敏


| 接口           | 规则                |
| ------------ | ----------------- |
| IF-NUR-01～03 | 患者隐私字段若有则与工作站统一脱敏 |


### 6.5 审计日志


| 接口        | 审计内容                                           | 代码位置（必须）                                                   |
| --------- | ---------------------------------------------- | ---------------------------------------------------------- |
| IF-NUR-04 | `operatorId`、`encounterId`、请求 id 列表、耗材房、库存扣减摘要 | `OutpatientNurseStationTreatmentAppService#batchPerformDisposition` |
| IF-NUR-05 | `operatorId`、`encounterId`、`statusReasonText`、取消请求/发放 id 列表、库存回补摘要 | `OutpatientNurseStationTreatmentAppService#batchCancelDisposition` |


---

## 7. 测试要点

### 7.1 功能测试


| 接口        | 测试场景                 | 预期                         | 测试层级 | 自动化归属 | 优先级 |
| --------- | -------------------- | -------------------------- | ---- | ----- | --- |
| IF-NUR-01 | BR-NUR-001 准入        | 仅合规就诊入列                    | 集成测试 | 后端    | P0  |
| IF-NUR-01 | 日期区间按就诊开始时间收窄 | 起止闭区间命中；清空回落当天 | 集成测试 | 后端/前端 | P0  |
| IF-NUR-01 | **BR-NUR-017** 行内待发耗材 | `hasPendingDevice` 与侧栏「待发放」一致 | 集成测试 | 后端    | P0  |
| IF-NUR-01 | **BR-NUR-017** 仅待发耗材 | `pendingDeviceOnly=true` 分页前过滤；总数与列表一致 | 集成测试 | 后端    | P0  |
| IF-NUR-02 | 诊疗类目仅过滤诊疗列表          | 药耗不受影响                     | 集成测试 | 后端    | P0  |
| IF-NUR-02 | 计费首条与金额和             | 对齐 **Q-ORD-006**           | 集成测试 | 后端    | P0  |
| IF-NUR-02 | 药品行用法用量总量展示         | `dose*`/`method*`/`rate*`/`unitName` 成对返回 | 集成测试 | 后端/前端 | P1  |
| IF-NUR-03 | 按就诊分页                | 命中 **OrderExecutionSheet** | 集成测试 | 后端    | P1  |
| IF-NUR-04 | 未计费拦截                | EX-NUR-202                 | 集成测试 | 后端    | P0  |
| IF-NUR-04 | 同请求多次执行登记（`skipDuplicateRequestAnchorCheck`） | BR-NUR-005                 | 集成测试 | 后端    | P0  |
| IF-NUR-03B | 执行+取消对冲后净剩列表为空；再执行出现新行 | BR-NUR-013                 | 集成测试 | 后端    | P0  |
| IF-NUR-05 | 按 procedureId 取消，refund_id 正确 | BR-NUR-011 / BR-PRO-005    | 集成测试 | 后端    | P0  |
| IF-NUR-05 | 已发放耗材取消回准备中 + 真实批号回补 | BR-NUR-012                 | 集成测试 | 后端    | P0  |
| IF-NUR-05 | 不调用 ChargeItem 写服务 | 边界                       | 集成测试 | 后端    | P0  |


### 7.2 非功能性测试


| 接口           | 测试项  | 验收标准（AS §7）  |
| ------------ | ---- | ------------ |
| IF-NUR-01～03 / 03B | 响应时间 | ≤1000ms      |
| IF-NUR-04    | 响应时间 | ≤3000ms（含拆分） |
| IF-NUR-05    | 响应时间 | ≤3000ms（含回补） |


### 7.3 QueryService一致性测试


| QueryService                 | 验证                                  |
| ------------------- | ----------------------------------- |
| OrderExecutionSheet | IF-NUR-04 / 05 写毕后 IF-NUR-03 可见新记录（最终一致） |
| `cln_procedure`（IF-NUR-03B） | IF-NUR-05 写毕后 IF-NUR-03B 立即不展示已对冲 COMPLETED（同事务内读为域查询） |


### 7.4 集成测试


| 项                            | 优先级 |
| ---------------------------- | --- |
| 护士站：IF-NUR-01 → 02 → 03 → 03B → 04 → 05 | P0  |


### 7.5 待补测试用例

| 用例 ID | 说明 | 优先级 | 状态 |
| --- | --- | --- | --- |
| TC-NUR-04-001 | IF-NUR-04：多次 Procedure、真实批号 `decreaseQuantitiesMerged`、占位批号跳过扣减 | P0 | 待补 |
| TC-NUR-05-001 | IF-NUR-05：`revertPreparedBatchToPreparation` / `revertCompletedBatchToPreparation` 与库存回补顺序、占位批号跳过回补 | P0 | 待补 |
| TC-NUR-03B-001 | IF-NUR-03B + IF-NUR-05：COMPLETED→取消→净剩消失→再执行→再取消 | P0 | 待补 |


---

## 8. 门诊皮试模块（已实现·后端）

> **权威**：`AS-OP-NUR-001` **§1.7、§2.1、§4**；与住院 **AS-IP-NUR-011-SDD** 对照。皮试检查 `activityId`：**GAP-OP-STK-001 已关闭**（`GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME` + `#requireIdByExactName`）。

### 8.1 实现边界

| 项 | 内容 |
| --- | --- |
| **UI** | 门诊护士站工作台 **皮试 Tab**（未落地）；左侧患者 **复用 IF-NUR-01**，**不**另建皮试队列 |
| **落包** | `.../nursestation/skintest`（**已实现**）；详 **AS-OP-NUR-001-STK-皮试-SDD** |
| **AppService** | `OutpatientNurseStationSkinTestAppService` → **`outpatientNurseStationSkinTest`** |
| **与处置隔离** | **禁止**用 IF-NUR-02 三列表承载皮试登记；皮试列表独立 **IF-OP-STK-02** |
| **费用** | 皮试操作费在**医生开立 + 门诊收费**链路完成；IF-OP-STK-03 **禁止**注入 `ServiceRequestDomainService` / `ChargeItemDomainService` 写侧 |
| **列表门控** | 皮试检查 `ServiceRequest.status_enum = **COMPLETED**`（= 门诊收费已关闭）；**非**住院 `VERIFIED` 口径 |

### 8.2 费用时机与写侧边界（实现必读）

| 费用项 | 产生时机 | 责任模块 | IF-OP-STK-03 是否写入 |
| --- | --- | --- | --- |
| 皮试药费 | 医生开皮试药 + 门诊收费 | 医生站 + **AS-OP-FIN-002** | **否** |
| 皮试操作费 | 医生开「皮试检查」SR + ChargeItem；门诊收费后 SR→`COMPLETED` | 医生站 + **AS-OP-FIN-002** | **否** |
| 皮试临床结果 | 护士双签保存 | **IF-OP-STK-03** | **仅** `AllergyIntoleranceDomainService#saveSkinTestRecord` |

门诊收费结算后皮试检查 SR 置 `COMPLETED` 的编排以收费 AS/SDD 为准；皮试列表 Pre 须校验 SR 仍为 `COMPLETED` 方可保存。

### 8.3 代码结构（已实现）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/nursestation/
  skintest/
    service/OutpatientNurseStationSkinTestAppService.java
    request/
      LoadOutpatientSkinTestInitRequest.java
      ListOutpatientSkinTestRowsRequest.java
      SaveOutpatientSkinTestRecordRequest.java
      DeleteOutpatientSkinTestRecordRequest.java
    response/...
    dto/
      OutpatientSkinTestRowDto.java
      OutpatientSkinTestLotOptionDto.java
    assembler/OutpatientSkinTestListAssembler.java
    mapper/OutpatientSkinTestMapper.java
```

### 8.4 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `EncounterQueryService` / `PatientEncounterConsistencyGate` | 就诊可见性与患者一致 Gate（与 **PAT** IF-NUR-01 同 `mustGetEncounter` 口径） |
| `PatientEncounterConsistencyGate` | `patientId` 与就诊一致 |
| `ActivityDefinitionQueryService` | `#requireIdByExactName(GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME)` |
| `ServiceRequestQueryService` | IF-OP-STK-02：按 encounter + activity + `COMPLETED` 加载皮试检查 SR |
| `MedicationRequestQueryService` | 经 `based_on_id` 装载皮试药行信息 |
| `AllergyIntoleranceQueryService` | 按就诊 / `request_id` 加载已有记录 |
| `AllergyIntoleranceDomainService` | IF-OP-STK-03/04 写侧 |
| `InventoryItemQueryService` | 批号候选只读 |
| `OutpatientSkinTestListAssembler` / `OutpatientSkinTestInitAssembler` | 列表与枚举装配 |
| `OutpatientSkinTestMapper` | Request ↔ `SaveSkinTestRecordCommand` |
| `ProductDefinitionBatchLoader` / `PractitionerQueryService` | 药品名、人员展示名 |
| `CurrentOperatorResolver` | IF-OP-STK-04 删除操作人 |

> **禁止注入**：`ServiceRequestDomainService`、`ChargeItemDomainService`（门诊皮试护士保存路径）。

### 8.5 列表查询机制（IF-OP-STK-02）

与初版 `OutpatientSkinTestAppMapper#getRecordInfo` 同构，读侧改为 QueryService 编排（**禁止** App 注入 Repository）：

| 步骤 | 读侧 | 说明 |
| --- | --- | --- |
| 1 | Gate | `encounterId` 可见（与 IF-NUR-01 一致） |
| 2 | Pre | `ActivityDefinitionQueryService#requireIdByExactName(GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME)` |
| 3 | Pre | `ServiceRequestQueryService#list…`：`encounterId` + `activityId` + `status=COMPLETED` + `basedOnTable=MED_MEDICATION_REQUEST` |
| 4 | Pre | 批量 `MedicationRequestQueryService#listByIds(basedOnIds)` → 药品名、开立医生、`medicationId` |
| 5 | Pre | `MedicationDispenseQueryService`（或等价）→ 皮试药发放意向 `locationId` |
| 6 | Pre | `AllergyIntoleranceQueryService#getRecordInfo(encounterId)` → 按 `request_id`（皮试检查 SR.id）左连接 |
| 7 | Pre | `InventoryItemQueryService` → 按 `medicationId`+`locationId` 批号候选 |
| 8 | Asm | `OutpatientSkinTestListAssembler#assembleRows` |

**行主键语义**：`serviceRequestId` = 皮试检查 SR.id（**非**皮试药 MR.id）。`allergyRecordId` 可空（待登记）。

### 8.6 应用服务接口（方法白名单）

| IF | 应用服务方法 | HTTP |
| --- | --- | --- |
| IF-OP-STK-01 | `loadOutpatientSkinTestInit` | `POST /api/app/outpatientNurseStationSkinTest/loadOutpatientSkinTestInit` |
| IF-OP-STK-02 | `listOutpatientSkinTestRows` | `POST /api/app/outpatientNurseStationSkinTest/listOutpatientSkinTestRows` |
| IF-OP-STK-03 | `saveOutpatientSkinTestRecord` | `POST /api/app/outpatientNurseStationSkinTest/saveOutpatientSkinTestRecord` |
| IF-OP-STK-04 | `deleteOutpatientSkinTestRecord` | `POST /api/app/outpatientNurseStationSkinTest/deleteOutpatientSkinTestRecord` |

### 8.7 IF-OP-STK-03 保存编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 就诊可见 + `PatientEncounterConsistencyGate` | **BR-OP-STK-001** |
| 2 | Pre | `ServiceRequestQueryService#loadById(serviceRequestId)` | 须 `COMPLETED` + 皮试检查 activity + 本就诊 → 否则 **EX-OP-STK-202** |
| 3 | Pre | 校验双签、临床/验证状态 | **BR-OP-STK-004** |
| 4 | Cmd | `AllergyIntoleranceDomainService#saveSkinTestRecord` | `requestId` = 皮试检查 SR.id；**写库点** |
| — | — | **跳过** SR/Charge 写 | **BR-OP-STK-005** |

### 8.8 IF-OP-STK-04 删除编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 就诊可见 | |
| 2 | Cmd | `AllergyIntoleranceDomainService#deleteSkinTestRecord` | **不**删 SR/Charge（**BR-OP-STK-007**） |

### 8.9 DTO 要点（`OutpatientSkinTestRowDto`）

| 字段 | 说明 |
| --- | --- |
| `serviceRequestId` | 皮试检查 SR.id（列表锚点） |
| `medicationRequestId` | 皮试药 MR.id（`based_on`） |
| `medicationId` / `medicationName` | 皮试药定义 |
| `openDoctorId` / `openDoctorName` | 皮试药开立医生 |
| `allergyRecordId` | 已有过敏记录（可空） |
| `lotNumber` / `lotNumberOptions` | 已选批号 / 候选 |
| `clinicalStatusCode` / `verificationStatusCode` | 及 `*Text` |
| `practitionerId` / `checkPractitionerId` | 双签 |
| `feeSettledFlag` | 恒真（列表已过滤 `COMPLETED`）；供前端展示「已收费」 |

### 8.10 与住院皮试实现差异（防混用）

| 检查项 | 门诊（本节） | 住院（`NurseStationSkinTestAppService`） |
| --- | --- | --- |
| 列表过滤状态 | SR `COMPLETED` | MR `VERIFIED` |
| 列表主实体 | 皮试检查 SR | 皮试药 MR |
| 首次保存 | 仅 Allergy | SR + Charge + Allergy |
| `request_id` 指向 | 医生皮试检查 SR | nurse-pricing 皮试检查 SR |

### 8.11 皮试测试要点（目标态）

| 场景 | 预期 |
| --- | --- |
| 医生开皮试未收费 | IF-OP-STK-02 空列表 |
| 门诊收费后 | 皮试行出现；`feeSettledFlag=true` |
| 保存 | 仅 `cln_allergy_intolerance` 变更；SR/Charge 不变 |
| 删除 | 过敏记录软删；费用可退由收费模块独立处理 |
| 患者列表 | IF-NUR-01 与皮试 Tab 同一 `encounterId` |

---

## 9. 变更历史

| v2.1.1 | 2026-05-28 | §2.2 依赖表：**`EncounterContextFacade`**（IF-NUR-01）、**`OrderExecutionSheetLoader`**（IF-NUR-03，替代 `ProcedureQueryService#page*` 叙述） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：`OutpatientEncounterContextQueryModel` + Facade；§3/§4 序列与 `zxplat` 一致 |

| 版本     | 日期         | 变更                                                                                                                                                                                      |
| ------ | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| v1.3.15 | 2026-09-14 | **BR-NUR-018**：退费中/已退费耗材侧栏仍返回并展示待退费/已退费；执行与待发红点仍排除 |
| v1.3.14 | 2026-09-14 | **BR-NUR-017/018**：侧栏/队列待发须 PLANNED/SETTLED；IF-NUR-04 执行前剔除已退费耗材；分页前 EXISTS 叠加有效计费 |
| v1.3.13 | 2026-08-21 | **BR-NUR-018**：IF-NUR-02 `deviceLines` 排除 REFUNDING/REFUNDED 与 DECLINED 等作废发放；队列待发判定对齐 |
| v1.3.12 | 2026-08-21 | **BR-NUR-012 / BR-DD-009**：取消执行回退 **保留**签发批号；对齐 **DM-WKF-007 v1.2.12** / **AS-OP-NUR-001 v1.9.9** |
| v1.3.11 | 2026-08-19 | **IF-NUR-01 / BR-NUR-017**：Request `pendingDeviceOnly`；行内 `hasPendingDevice`；日期区间沿用 `startDate`/`endDate`；编排序列增合同展示与待发耗材批量回填；白名单增 `listDeviceRequestsByEncounterIds` / `listDeviceDispensesByEncounterIds` |
| v1.3.10 | 2026-08-01 | **IF-NUR-01**：Request `organizationId` 必填改为可选 **`orgId`（开立科室，空=全部）**；映射 `NurseStationOutpatientQueueQuery`；域 Spec 等值匹配就诊 `organization_id` |
| v1.3.9 | 2026-06-25 | **B.10.9**：药品 `prescriptionNo` 禁止回落 `bus_no`（MR）；仅展示 `prescription_no`（`CP`） |
| v1.3.8 | 2026-06-25 | **BR-NUR-012/EX-NUR-211**：耗材已发取消门控仅 `COMPLETED`；对齐 **DM-WKF-007 v1.2.4** |
| v1.3.7 | 2026-06-25 | **BR-NUR-016**：`remark` + `serviceRequestDetails`；步骤 **8c** 批量 Detail；前端 extension 展示样本/部位/备注 |
| v1.3.6 | 2026-06-25 | **BR-NUR-015** 前端：申请单号改为同组首行医嘱内容内联标签（取消独立列） |
| v1.3.4 | 2026-06-24 | **IF-NUR-04**：`complete` 后经 `ProcedureExecutionPerformerOrchestrationsupport 写入 `cln_procedure_performer`（PRIMARY，1:1）；**IF-NUR-02** 增 `lastPerformer*`；**IF-NUR-03** 回填 `performers[].practitionerName` |
| v1.3.3 | 2026-06-20 | **support 落点对齐**：`DeviceDispenseNurseWorkflowsupport 主实现归 **`module.orchestration.nursestation`**（门诊/住院/划价/执行 AppService 注入）；对齐 **AS v1.9.3** |（已迁至 application.shared.nursestation-device-dispense）
| v1.3.2 | 2026-06-16 | **IF-NUR-02**：`NurseStationTreatmentLineDto` 增补药品展示字段（`dose*` / `method*` / `rate*` / `unitName` / `dispensePerDuration`）；编排序列增步骤 **8b**（术语+产品批量预热，对齐 IF-DOR-10 **BR-ORD-022**） |
| v1.3.1 | 2026-06-10 | §8.4/§8.5 与代码对齐：`EncounterQueryService`（非 Facade）；`listByIds` |
| v1.3.0 | 2026-06-10 | **子域拆包**：`patient` / `treatment` / `skintest`；AppService 与 HTTP 前缀三分（`outpatientNurseStationPatient` / `Treatment` / `SkinTest`）；新增分拆 SDD **PAT / TRT / STK** |
| v1.2.2 | 2026-06-10 | **§8 门诊皮试后端已实现**：`OutpatientNurseStationSkinTestAppService` + IF-OP-STK-01～04 全量落盘；§8.1/§8.3 状态由「未实现」改为「已实现」 |
| v1.2.1 | 2026-06-10 | **GAP-OP-STK-001 已关闭**：`GeneralSharedConstants` + `#requireIdByExactName` |
| v1.2.0 | 2026-06-10 | 新增 **§8 门诊皮试目标态**：IF-OP-STK-01～04、`outpatientNurseStationSkinTest` 包；列表以皮试检查 SR `COMPLETED` 为门控；费用时机表；与 **AS-IP-NUR-011** 对照；对齐 **AS v1.8** |
| v1.0.8 | 2026-05-18 | **IF-NUR-02**：步骤 **8** 改为 **`mapDeviceDispenseIdsByDeviceRequestIds`** 批量查发放 id（A.9.1）；移除无效 `mapDeviceRequestToPrimaryDispenseLocationId` 调用 |
| v1.0.7 | 2026-05-18 | 目录预热收口 **`module.common`**：`OrderRequestCatalogWarmAssembler` / `OrderCatalogDisplayAssembler` / `TerminologyOptionLoader`（与 IF-DOR-10 / IF-OPP-03 共用） |
| v1.0.6 | 2026-05-18 | **IF-NUR-02**：§3.3 补充目录来源、`*CategoryCodeText` 字段与类目筛选/回显分离；编排序列增步骤 **8a** |
| v1.0.5 | 2026-05-18 | 对齐 **AS v1.6**：新增 **IF-NUR-03B** §3.4.1（`listNetRemainingProcedureRecordsForNurseStation`、**BR-NUR-013**）；**IF-NUR-05** 入参改为 **`procedureIds`** / 响应 **`cancelledProcedureIds`**；`toCancelProcedureRecordCommand` 增加 **`refundId`**；§2.1 包结构、§2.4 白名单、§4 / §7 增量 |
| v1.1.0 | 2026-06-08 | **BR-NUR-014**：`DeviceDispenseNurseWorkflowsupport + `DeviceAutoInventoryDeductSettingsupport；移除护士站 `executeDeviceOrder`/`revokeDeviceOrderExecution`；§3.5/§3.6/§5 重排；对齐 **AS v1.7** |
| v1.0.4 | 2026-05-18 | §1.1：**IF-NUR-05 已实现**；§1.3 **DOC-CONFLICT P03/P04** 改为实现说明（非 Gap）；对齐 **AS v1.5.1** |
| v1.0.3 | 2026-05-18 | 对齐 **AS v1.5**：新增 **IF-NUR-05** `batchCancelDisposition` 全章；**BR-NUR-011～012**；§1.3 **DOC-CONFLICT-NUR-P03/P04**；明确**不**调用 ChargeItem 写服务；§5～§7 增量 |
| v1.0.2 | 2026-05-18 | 执行状态口径统一：**EventStatus** 取消态 **CANCEL**（替代 **ABORTED**）；预留 **STOPPED**；§2.3 / §1.2 BR-NUR-005 与 **DM-CLN-004** §4.1 对齐；存量 **ABORTED** 由 Flyway **V43** 迁移 |
| v1.0.1 | 2026-05-10 | 与实现对齐：§1.1/§1.3 **Procedure** 多次执行缓解（`CreateProcedureRecordCommand#skipDuplicateRequestAnchorCheck`）；IF-NUR-04 耗材真实批号路径补充 **InventoryItemQueryService#requireInventoryItemIdForDeviceDispenseBatch**；§2.2/§2.4 依赖与白名单增量 |
| v1.0.0 | 2026-05-10 | 初版：对齐 **AS-OP-NUR-001** v1.4；落盘 **AppService-SDD**；§1.3 / §3.5 声明 **Procedure** 与 **BR-NUR-005** 之 **DOC-CONFLICT** 及目标态 FQCN 占位；HTTP 统一 `**POST /api/app/outpatientNurseStation/...`** |


---

**文档负责人**：（待填） · **评审**：（待填）