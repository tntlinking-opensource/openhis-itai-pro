# 住院护士站 · 取消执行 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-012-SDD  
> 版本：v1.9 | 日期：2026-10-16 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · 医嘱执行 Tab · **已执行**槽位批量**取消执行**（写路径） |
| 实现阶段口径 | **目标态全量契约**（IF-IP-CXL-01）；**领域层 Gap 001～007 已关闭**（见 **AS-IP-NUR-012 §3 v1.1**）；**应用层已生成** `inpatient/nursestation/ordercancelexecute` |
| 编排权威 | `AS-IP-NUR-012-住院护士站取消执行.md`（**v1.8**）§1.3、§4 |
| 方法权威 | `DM-CLN-004` §6.1（Procedure CANCEL）；`DM-MED-002` §6.1（Dispense 删/回退/退药/撤汇总）；`DM-ADM-011` §6.1.4l/§6.1.5a；`DM-WKF-004` §6.1.13/§6.1.18；`DM-WKF-003` §6.2.10；`DM-WKF-006` §6.1（Supply 撤/驳）；`DM-WKF-007` §6.1（DeviceDispense revert）；`DM-WKF-006` + **AS-IP-NUR-010**（汇总过滤重建合并规则） |
| 严格对齐 | IF 编号 **IF-IP-CXL-01**；主路径顺序 **绑定项 → CANCEL → Dispense/Charge → 汇总重建**（**BR-IP-CXL-006**） |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 已执行 Tab 列表读侧 | **AS-IP-NUR-004 IF-IP-EXE-01**（`executedSlots` 提供 `procedureId`） |
| 正向执行 / 不执行 | **AS-IP-NUR-004 IF-IP-EXE-02/04** |
| 护士主动汇总 Tab 写操作 | **AS-IP-NUR-010**（本文仅在 SUMMARIZED 路径**自动**复用其合并规则） |
| 药房汇总核发 | **AS-IP-PHA-002** |
| 住院结算/退费编排 | **AS-IP-FIN-003** 等（本文仅 **Gate** 拒绝已结算 Charge） |
| 已结算费用退费 | 取消执行整批拒绝（**BR-IP-CXL-005**） |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见 encounter 集合 **BR-IP-CXL-001** |
| `AS-IP-NUR-004` | 槽位语义、Procedure 创建口径、绑定执行对称逻辑 |
| `AS-IP-NUR-010` | 汇总过滤重建：**BR-IP-SUM-005～008** 合并键与 WriteAssembler |
| `AS-OP-NUR-001` | CANCEL 对冲 **BR-NUR-005** 同构 |
| 门诊 `OutpatientNurseStationTreatmentAppService#batchCancelDisposition` | Procedure CANCEL 追加模式参照（**不含** Charge 回滚；住院本文含全分轨） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 / 枚举 code | 持久化与比较一律 **String `code`** |
| `procedureId`（入参） | 前端勾选 **净剩 COMPLETED** 执行记录主键（**非**新 CANCEL 行 id） |
| CANCEL 对冲 | 新建 Procedure：`create → start → 挂主执行人 PRIMARY（操作人）→ cancel`；新行 `refund_id` = 被对冲 COMPLETED.id（**BR-IP-CXL-003**）；与执行同构，**禁止**终态无执行人 |
| 绑定项定位 | 主医嘱 **`requestTable + requestId`** → **`based_on_*` 反查**；来源 **`nurse-pricing`（现行）** + 历史 **`order-execute`**（排除皮试 `doctor-prescription`）；本槽收窄：诊疗 Procedure/`Charge.occurrence` 与主槽 **秒级**对齐；耗材 Charge.`procedure_id`=主 Procedure 或 occurrence 秒级对齐（**BR-IP-CXL-013/014**） |
| 长临分轨（药 Dispense） | **长临**未发：**统一** `removeUndispensedDispense` |
| 长临分轨（Charge） | **长临**执行新建：**统一** `voidOrderExecuteChargeItemsByProcedureIds` |
| 汇总 SUMMARIZED | 同事务：**自动撤整单**（Supply 态分支）→ 剔除取消行 → 剩余非空则 **过滤重建**（新 `summaryNo`；定价同 **IF-IP-SUM-04**） |
| Supply 态分支 | **PENDING_APPROVAL**：`cancelMedicationDispenseSummary` + `removeByBusNo`；**APPROVAL**：`rejectRequest` + `cancelMedicationDispenseSummary` + **`removeByBusNo`（清驳回残单）**；**AGREE** / 发放 **COMPLETED**：**拒绝**（**EX-IP-CXL-204**） |
| HTTP | 一律 **`POST`**；Bean **`NurseStationOrderCancelExecuteAppService`** → 前缀 **`nurseStationOrderCancelExecute`** |
| 写事务 | **单批 ≤50** `procedureId`；**同一 `@Transactional`**；任一步失败 **整批回滚**（**BR-IP-CXL-015**） |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  ordercancelexecute/
    service/
      NurseStationOrderCancelExecuteAppService.java
    request/
      CancelNurseStationOrderExecutionsRequest.java
    response/
      CancelNurseStationOrderExecutionsResponse.java
    dto/
      CancelExecutionSummaryRebuildContext.java
    assembler/
      NurseStationOrderCancelExecuteWriteAssembler.java
    mapper/
      NurseStationOrderCancelExecuteMapper.java
      NurseStationOrderCancelExecuteMapperImpl.java
  dispensesummary/
    assembler/
      NurseStationDispenseSummaryWriteAssembler.java   （复用：过滤重建合并组）
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/{feature}/support/
  InpatientNurseStationTcmSupport.java
zxplat/health-application/.../application/shared/nursestation-device-dispense/
  DeviceDispenseNurseWorkflowSupport.java          # 住院绑定耗材回退
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.ordercancelexecute`

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 `operatorId` |
| `CurrentPractitionerResolver` | Supply `applicantId`（过滤重建建单） |
| `CurrentManagedWardLocationIdsResolver` | 管辖病区 → 可见 encounter（**BR-IP-CXL-001**） |
| `EncounterContextReader` | 批量就诊可见性收窄 |
| `ProcedureQueryService` | Pre：加载 COMPLETED 行、对冲检测、绑定派生 Procedure |
| `ProcedureDomainService` | Cmd：`createProcedureRecord` → `startProcedureExecution` → `cancelProcedureRecord` |
| `MedicationDispenseQueryService` | Pre：`listMedicationDispensesByProcedureIds` |
| `MedicationDispenseDomainService` | Cmd：删/回退/退药/撤汇总/重建汇总 |
| `ChargeItemQueryService` | Pre：`listChargeItemsByProcedureIds`；结算 Gate |
| `ChargeItemDomainService` | Cmd：`voidOrderExecuteChargeItemsByProcedureIds` |
| `InpatientNurseStationTcmsupport | Pre：`expandCancelProcedureIdsByTcmPrescription`（**BR-IP-NUR-012-TCM-001**） |
| `ServiceRequestQueryService` | Pre：`listServiceRequestsByBasedOnAnchors` |
| `ServiceRequestDomainService` | Cmd：`removeExecutedServiceRequest` |
| `MedicationRequestQueryService` | Pre：中药方级扩行索引（`listMedicationRequestsByEncounterForDoctorStation`） |
| `DeviceRequestQueryService` | Pre：`listDeviceRequestsByBasedOnAnchors` |
| `DeviceDispenseQueryService` | Pre：`listDeviceDispenseIdsByDeviceRequest` |
| `DeviceDispenseDomainService` | Cmd：`revertCompletedBatchToPreparation` 等 |
| `DeviceDispenseNurseWorkflowsupport | Cmd：绑定耗材对称回退 + 库存合并回补 |
| `InventoryItemDomainService` | Cmd：`increaseQuantities`（绑定耗材已发回补） |
| `SupplyRequestQueryService` | Pre：`getSupplyByBusNo`、Supply 态乐观校验 |
| `SupplyRequestDomainService` | Cmd：`removeByBusNo`、`rejectRequest`、`createSummarySupplyRequest` |
| `ChargeItemQueryService` | Pre：结算 Gate 再生链过滤 |
| `InpatientMedicationDispenseChargePricingLoader` | Cmd：汇总过滤重建单价（**PENDING_SETTLEMENT** + `dispenseId`，同 IF-IP-SUM-04） |
| `NurseStationOrderCancelExecuteWriteAssembler` | CANCEL 追加、绑定回退编排、汇总重建上下文 |
| `NurseStationDispenseSummaryWriteAssembler` | **复用** `buildMergeGroupsByLocationAndPrice` 等（**BR-IP-SUM-005～008**） |
| `NurseStationOrderCancelExecuteMapper` / `MapperImpl` | Request→Command、Response 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| 读 | `ProcedureQueryService#loadProcedureById` | 01 Pre |
| 读 | `ProcedureQueryService#getProcedureRecords` | 01 Pre（对冲检测） |
| 读 | `MedicationDispenseQueryService#listMedicationDispensesByProcedureIds` | 01 Pre |
| 读 | `MedicationDispenseQueryService#listMedicationDispensesBySummaryNo` | 01 Pre/Cmd（汇总重建） |
| 读 | `ChargeItemQueryService#listChargeItemsByProcedureIds` | 01 Pre |
| 读 | `InpatientMedicationDispenseChargePricingLoader#mapPendingSettlementChargeByDispenseIds` | 01 Cmd（重建分组定价） |
| 读 | `ServiceRequestQueryService#listServiceRequestsByBasedOnAnchors` | 01 Pre |
| 读 | `DeviceRequestQueryService#listDeviceRequestsByBasedOnAnchors` | 01 Pre |
| 读 | `DeviceDispenseQueryService#listDeviceDispenseIdsByDeviceRequest` | 01 Pre |
| 读 | `MedicationRequestQueryService#loadMedicationRequestById`（或 mustGet 列表模型） | 01 Pre（长临分轨） |
| 读 | `SupplyRequestQueryService#getSupplyByBusNo` | 01 Pre/Cmd |
| 写 | `ProcedureDomainService#createProcedureRecord` | 01 Cmd |
| 写 | `ProcedureDomainService#startProcedureExecution` | 01 Cmd |
| 写 | `ProcedureDomainService#cancelProcedureRecord` | 01 Cmd |
| 写 | `ServiceRequestDomainService#removeExecutedServiceRequest` | 01 Cmd（绑定诊疗） |
| 写 | `MedicationDispenseDomainService#removeUndispensedDispense` | 01 Cmd |
| 写 | `MedicationDispenseDomainService#rollbackUndispensedDispense` | 01 Cmd |
| 写 | `MedicationDispenseDomainService#createPendingRefundDispense` | 01 Cmd |
| 写 | `MedicationDispenseDomainService#cancelMedicationDispenseSummary` | 01 Cmd |
| 写 | `MedicationDispenseDomainService#summarizeMedicationDispenses` | 01 Cmd（过滤重建） |
| 写 | `ChargeItemDomainService#voidOrderExecuteChargeItemsByProcedureIds` | 01 Cmd |
| 写 | `ChargeItemDomainService#deactivateChargeByExe` | 01 Cmd |
| 写 | `SupplyRequestDomainService#removeByBusNo` | 01 Cmd |
| 写 | `SupplyRequestDomainService#rejectRequest` | 01 Cmd |
| 写 | `SupplyRequestDomainService#createSummarySupplyRequest` | 01 Cmd（过滤重建） |
| 写 | `DeviceDispenseDomainService#revertCompletedBatchToPreparation` | 01 Cmd（绑定耗材） |
| 写 | `DeviceDispenseDomainService#revertPreparedBatchToPreparation` | 01 Cmd（绑定耗材） |
| 写 | `DeviceDispenseDomainService#rollbackUndispensedDeviceDispense` | 01 Cmd（绑定耗材 PREPARATION） |
| 写 | `InventoryItemDomainService#increaseQuantities` | 01 Cmd（绑定耗材已发回补） |
| 编排 | `DeviceDispenseNurseWorkflowSupport#revertInpatientBindingDeviceDispenses` | 01 Cmd（绑定耗材 **BR-IP-CXL-014** 对称回退） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| Procedure 终态 | `com.zxplat.domain.share.enums.EventStatus#COMPLETED` / `#CANCEL` | `DM-CLN-004` §2 状态机 | Pre Gate：仅 COMPLETED 可取消 |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2 | PREPARATION/SUMMARIZED/COMPLETED 分轨 |
| 收费状态 | `com.zxplat.domain.share.enums.ChargeItemStatus#PENDING_SETTLEMENT` / `#PLANNED` / `#SETTLED` / `#REFUNDING` / `#REFUNDED` | `DM-ADM-011` §2 | Gate 与 Charge 分轨 |
| 生成来源 | `GenerateSource#isOrderExecuteFamilyCode`（`ORDER_EXECUTE` / `ANESTHESIA_EXECUTE`） | `DM-ADM-011` §4.2 / §6.1.5a | 执行新建 Charge 逻辑删（含麻醉执行） |
| 长临嘱 | `com.zxplat.domain.share.enums.TherapyType#LONG_TERM` / `#TEMPORARY` | `DM-MED-003` §3 `therapy_enum` | Dispense/Charge 分轨 |
| 供应状态 | `com.zxplat.domain.share.enums.SupplyStatus#PENDING_APPROVAL` / `#APPROVAL` / `#AGREE` | `DM-WKF-006` §2.3 | 汇总撤单分支 **BR-IP-CXL-011/012** |
| 供应类型 | `com.zxplat.domain.share.enums.SupplyType#MEDICATION_SUMMARY_ORDER` | `DM-WKF-006` §4.1.4 | 过滤重建 type 27 |
| 请求表锚点 | `com.zxplat.domain.share.constants.TableNameConstants#MED_MEDICATION_REQUEST` / `#WKF_SERVICE_REQUEST` | `DM-CLN-004` §3 `request_table` | 主医嘱 / based_on |
| 业务异常码 | `com.zxplat.domain.share.constants.InpatientNurseStationConstants#EX_IP_CXL_201`～`#EX_IP_CXL_205` | `AS-IP-NUR-012` §5 | 已实现 |
| 写批上限 | `InpatientNurseStationConstants.ORDER_CANCEL_EXECUTE_BATCH_MAX`（50） | `AS-IP-NUR-012` §7 NFR | 已实现 |
| Supply 自动驳回原因码 | `InpatientNurseStationConstants.ORDER_CANCEL_EXECUTE_SUPPLY_REJECT_REASON_CODE` | APPROVAL 撤汇总路径 | 已实现 |
| 入参 procedureId | `CancelNurseStationOrderExecutionsRequest.procedureIds` | `AS-IP-NUR-012` **BR-IP-CXL-002** | 净剩 COMPLETED 主键 |
| 取消原因 | `CancelNurseStationOrderExecutionsRequest.statusReasonText` | `AS-IP-NUR-012` **BR-IP-CXL-004** | 必填 |

### 2.4 领域层能力核对（非 Gap）

> **AS-IP-NUR-012 §3 Gap 001～007 均已关闭**（2026-06-10）。本 SDD 驱动**应用层**成码；**禁止**在 SDD 内新建 Gap 表。

**本 SDD 成码交付（目标态）**：`NurseStationOrderCancelExecuteAppService` 单写接口；WriteAssembler；Request/Response/Mapper；`InpatientNurseStationConstants` 增补 `EX_IP_CXL_*`；`DeviceDispenseNurseWorkflowSupport#revertInpatientBindingDeviceDispenses`（绑定耗材 **BR-IP-CXL-014**）。

## 3. 按接口 ID 命名展开

先读完 **IF-IP-CXL-01** 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 失败 **禁止**进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-CXL-01 | `cancelNurseStationOrderExecutions` | 批量取消执行 | `POST /api/app/nurseStationOrderCancelExecute/cancelNurseStationOrderExecutions` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、可见性、批上限、重复 procedureId、取消原因 | 抛 **`EX-IP-CXL-201`**，不进入 Pre |
| **Pre** | 批量加载 Procedure/Dispense/Charge；结算 Gate；**based_on** 反查绑定项 | 抛 **`EX-IP-CXL-202/203`**，**禁止进入 Cmd** |
| **Cmd** | 顺序：**绑定回退** → **主医嘱 CANCEL** → **Dispense/Charge 分轨** → **汇总过滤重建** | 同事务；失败 **`EX-IP-CXL-205`** 整批回滚 |
| **Asm** | Response 装配 | — |

记法：`ChargeItemDomainService#voidOrderExecuteChargeItemsByProcedureIds(VoidOrderExecuteChargeItemsByProcedureIdsCommand)` 表示注入 Bean 调用该方法。

### 3.2 IF-IP-CXL-01 `cancelNurseStationOrderExecutions`（批量取消执行）

**HTTP**：`POST /api/app/nurseStationOrderCancelExecute/cancelNurseStationOrderExecutions`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterIds` | `List<Long>` | Y | **AS-IP-NUR-001** 已选就诊；可见性 Gate（**BR-IP-CXL-001**） |
| `procedureIds` | `List<Long>` | Y | 已执行 Tab 勾选槽位 **净剩 COMPLETED** 的 `procedureId`；1～50；**同批禁止重复** |
| `statusReasonText` | `String` | Y | 取消原因（**BR-IP-CXL-004**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功为 `true` |
| `cancelledProcedureIds` | `List<Long>` | 本次成功对冲的 **原 COMPLETED** procedure 主键（与入参顺序一致的去重子集） |
| `rebuiltSummaryNos` | `List<String>` | 过滤重建生成的新汇总单号；无重建时 `[]` |
| `message` | `String` | 成功提示；含重建汇总时可提示「已自动重建汇总单」 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-CXL-001** | `encounterIds` 须在当前护士管辖病区可见集合内 |
| **BR-IP-CXL-002** | `procedureIds` 非空、去重后 ≤50、每条须属 `encounterIds` 并集 |
| **BR-IP-CXL-003** | 每条须 **COMPLETED**、requestTable 为药/诊、`encounter_id` 一致、**未被 CANCEL 对冲** |
| **BR-IP-CXL-005** | 主医嘱 + 绑定项关联 Charge 任一 **SETTLED/REFUNDING/REFUNDED** → 整批拒绝；**排除** `ChargeItemRegenerationChainSupport` 判定的链上历史祖先 `SETTLED`（**BR-REF-020** / **BR-IP-RVS-011**，含多轮反结） |
| **BR-IP-CXL-006** | Cmd 顺序：绑定 → CANCEL → Dispense/Charge → 汇总重建 |
| **BR-IP-CXL-012** | 汇总重建前 Supply 态乐观校验；并发改态 → **EX-IP-CXL-204** |
| **BR-IP-NUR-012-TCM-001** | Gate 后 Pre：`expandCancelProcedureIdsByTcmPrescription` — 中药方勾选 → 按 `prescription_no` 扩全方同 `occurrence` 净剩 COMPLETED `procedureId`；Gate ≤50 按扩行前计 |

**失败码口径**

| 码 | 场景 | 用户提示（对齐 AS §5） |
| --- | --- | --- |
| **EX-IP-CXL-201** | 未选 procedureId / 重复 id / 超 50 / 原因为空 / encounter 不可见 | 请选择已执行且未取消的计划点 |
| **EX-IP-CXL-202** | Procedure 不存在、非 COMPLETED、非本批就诊、已对冲、非药/诊来源 | 该计划点不可取消，请刷新后重试 |
| **EX-IP-CXL-203** | 费用已结算或退费中 | 费用已结算，请先办理退费后再取消执行 |
| **EX-IP-CXL-204** | Supply **AGREE** / 发放已 **COMPLETED** / 汇总态并发冲突 | 汇总单状态已变更，请刷新后重试 |
| **EX-IP-CXL-205** | 域写失败或未预期异常 | 取消执行失败，请稍后重试 |
| **EX-IP-CXL-206** | 就诊 `PENDING`/`COMPLETED`/`DISCHARGED` | 患者已办理出院或已结算，不可变更医嘱及费用 |

**Mapper 接口方法签名**

- `toCreateProcedureRecordCommand(ProcedureRecordQueryModel completedRow, Long operatorId, LocalDateTime now): CreateProcedureRecordCommand`
- `toStartProcedureExecutionCommand(Long newProcedureId): StartProcedureExecutionCommand`
- `toCancelProcedureRecordCommand(Long newProcedureId, String statusReasonText, Long offsetCompletedProcedureId, Long operatorId): CancelProcedureRecordCommand`
- `toVoidOrderExecuteChargeItemsByProcedureIdsCommand(Collection<Long> procedureIds, Long operatorId): VoidOrderExecuteChargeItemsByProcedureIdsCommand`
- `toDeactivateChargeByExeCommand(List<Long> chargeItemIds, Long operatorId): DeactivateChargeByExeCommand`
- `toRemoveExecutedServiceRequestCommand(List<Long> serviceRequestIds, Long operatorId): RemoveExecutedServiceRequestCommand`
- `toRemoveUndispensedDispenseCommand(Long dispenseId, Long operatorId): RemoveUndispensedDispenseCommand`
- `toRollbackUndispensedDispenseCommand(Long dispenseId, Long operatorId): RollbackUndispensedDispenseCommand`
- `toCreatePendingRefundDispenseCommand(Long originDispenseId, Long medReqId, Long cancelProcedureId, BigDecimal qty, Long operatorId): CreatePendingRefundDispenseCommand` — `refundRequestId`←`medReqId`（原发放药嘱）；`procedureId`←`cancelProcedureId`（序号 15 新 CANCEL 行，**BR-IP-CXL-010**）
- `toCancelMedicationDispenseSummaryCommand(String summaryNo, Long operatorId): CancelMedicationDispenseSummaryCommand`
- `toRemoveByBusNoCommand(String summaryNo, Long operatorId): RemoveByBusNoCommand`
- `toRejectRequestCommand(String summaryNo, Long operatorId): RejectRequestCommand`
- `toSummarizeMedicationDispensesCommand(Long locationId, List<Long> dispenseIds, Long operatorId): SummarizeMedicationDispensesCommand`
- `toCreateSummarySupplyRequestCommand(String summaryNo, List<SummaryMergeGroup> groups, Long applicantId, Long operatorId): CreateSummarySupplyRequestCommand`
- `toIncreaseQuantityCommand(Long operatorId, Map<Long, BigDecimal> inventoryIdToQty): IncreaseQuantityCommand`
- `toCancelNurseStationOrderExecutionsResponse(List<Long> cancelledProcedureIds, List<String> rebuiltSummaryNos): CancelNurseStationOrderExecutionsResponse`

**Assembler 方法签名（`NurseStationOrderCancelExecuteWriteAssembler`）**

- `normalizeDistinctProcedureIds(List<Long> raw): List<Long>`
- `assertSettlementGate(List<ChargeItem> allCharges): void` — 命中 SETTLED/REFUNDING/REFUNDED 抛 **EX-IP-CXL-203**
- `resolveMainOrderContext(ProcedureRecordQueryModel row): MainOrderAnchor` — `{ requestTable, requestId, encounterId, patientId, therapyEnum, categoryEnum }`
- `loadBindingServiceRequests(String basedOnTable, Collection<Long> basedOnIds): List<ServiceRequest>` — based_on 反查后**过滤** `GenerateSource.ORDER_EXECUTE`（**BR-IP-CXL-013**；排除皮试/划价挂靠）
- `loadBindingDeviceRequests(String basedOnTable, Collection<Long> basedOnIds): List<DeviceRequestListModel>`
- `isProcedureAlreadyOffset(Long requestId, String requestTable, Long completedProcedureId): boolean`
- `appendProcedureCancelOffset(MainOrderAnchor anchor, String statusReasonText, Long operatorId, Long offsetCompletedProcedureId): Long` — 返回**新 CANCEL** procedureId
- `rollbackBindingServiceRequest(ServiceRequest derived, String statusReasonText, Long operatorId): void`
- `rollbackBindingDeviceRequest(DeviceRequestListModel deviceReq, Long operatorId, Map<Long, BigDecimal> inventoryIncreaseAcc): void`
- `rollbackMainDispenseAndCharge(MedicationDispense dispense, String therapyEnum, Long completedProcedureId, Long newCancelProcedureId, Long operatorId): SummaryRebuildHint`
- `rollbackServiceChargeOnly(List<ChargeItem> charges, String therapyEnum, Long operatorId): void`
- `withdrawAndRebuildSummaryIfNeeded(CancelExecutionSummaryRebuildContext ctx, Long operatorId, Long applicantId): List<String>`

**内部结构 `MainOrderAnchor` / `SummaryRebuildHint` / `CancelExecutionSummaryRebuildContext`（应用层，非 API 出参）**

| 结构 | 字段 | 说明 |
| --- | --- | --- |
| `MainOrderAnchor` | `requestTable`, `requestId`, `encounterId`, `patientId`, `therapyEnum`, `categoryEnum` | 主医嘱锚点 |
| `SummaryRebuildHint` | `summaryNo`, `excludedDispenseIds` | 主路径 SUMMARIZED 发放触发的重建上下文 |
| `CancelExecutionSummaryRebuildContext` | `Map<summaryNo, Set<excludedDispenseId>>`, `Map<summaryNo, SupplyStatus snapshot>` | 按 summaryNo 聚合剔除行与 Supply 态快照 |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App + Resolver | `encounterIds`, `procedureIds`, `statusReasonText` | 非空；procedureId 去重；≤50；原因非 blank；**EX-IP-CXL-201** |
| 2 | Gate | `EncounterContextReader` + 病区 Resolver + `InpatientPendingSettlementClinicalGateSupport` | ① `encounterIds` | 不可见就诊 → **EX-IP-CXL-201**；`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-CXL-206**（**BR-IP-CXL-016**） |
| 2a | Pre | `expandCancelProcedureIdsByTcmPrescription` | ① `procedureIds` + ② 可见就诊 | **BR-IP-NUR-012-TCM-001**：`tcm_flag` 方按 `prescription_no` 扩全方同 occurrence 可取消 procedureId |
| 3 | Pre | `ProcedureQueryService#loadProcedureById` | 每条 **2a** 扩行后 `procedureId` | 不存在/非 COMPLETED/就诊不在 ①/非 med_medication_request 或 wkf_service_request → **EX-IP-CXL-202** |
| 4 | Pre | `ProcedureQueryService#getProcedureRecords` | ③ `requestId+requestTable` | 已存在 CANCEL 且 `refund_id`=③.id → **EX-IP-CXL-202** |
| 5 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByProcedureIds` | **2a** 扩行后 id 集合 | 主医嘱发放行索引 |
| 6 | Pre | `ChargeItemQueryService#listChargeItemsByProcedureIds` | **2a** 扩行后 id 集合 | 主医嘱 Charge 索引 |
| 7 | Pre | `WriteAssembler#assertSettlementGate` | ⑤⑥ + 后续绑定 Charge | **SETTLED/REFUNDING/REFUNDED** → **EX-IP-CXL-203** |
| 8 | Pre | `WriteAssembler#loadBindingServiceRequests`（内调 `listServiceRequestsByBasedOnAnchors` + **过滤 `order-execute`**） | 各主医嘱 `requestTable` + `requestId` 集合 | 仅执行派生绑定诊疗 SR（**BR-IP-CXL-013**）；皮试等开嘱联动不入回退 |
| 9 | Pre | `DeviceRequestQueryService#listDeviceRequestsByBasedOnAnchors` | 同上 | 绑定耗材（**BR-IP-CXL-014**）；**禁止**按 procedure 查 DeviceDispense |
| 10 | Pre | 绑定 Charge / Procedure 预加载 | ⑧⑨ 派生 id | ⑧ 派生 SR 的 Procedure（`getProcedureRecords` 或按 service 锚点）；绑定 Charge 按派生 procedureId 或 service 锚点并入 ⑦ |
| 11 | Cmd | **绑定诊疗回退**（逐条 ⑧） | 派生 SR | **写库点 A**：`ServiceRequestDomainService#removeExecutedServiceRequest(RemoveExecutedServiceRequestCommand)`；若派生 Procedure 为 COMPLETED → 序号 12 同构 CANCEL；Charge：`voidOrderExecute` / `deactivateChargeByExe` 按长临（**BR-IP-CXL-008**） |
| 12 | Cmd | **绑定 Procedure CANCEL**（若 ⑩ 存在 COMPLETED 派生 Procedure） | `statusReasonText` | 同序号 19～21 模式，`refund_id` 指向派生 COMPLETED |
| 13 | Cmd | **绑定耗材回退**（逐条 ⑨） | `DeviceRequest.id` | **写库点 B**：`DeviceDispenseNurseWorkflowSupport#revertInpatientBindingDeviceDispenses` → 内部 **PREPARATION/PREPARED**→待发（不补库存）；**COMPLETED**→revert + 累计回补；**DeviceRequest 不变** |
| 14 | Cmd | `InventoryItemDomainService#increaseQuantities(IncreaseQuantityCommand)` | ⑬ 累计 Map | 仅当 ⑬ 产生已发回补量；**saveAll 后再 publish** |
| 15 | Cmd | **主医嘱 CANCEL 对冲**（逐条 **2a** 扩行结果，按入参稳定顺序） | ③ + `statusReasonText` | **写库点 C**：`createProcedureRecord` → `startProcedureExecution` → `ProcedureExecutionPerformerOrchestrator#registerPrimaryPerformer` → `cancelProcedureRecord`；记录 `newCancelProcedureId` 映射 |
| 16 | Cmd | **主医嘱 Dispense 分轨**（逐条 ⑤，与 ③ 药嘱关联） | `therapyEnum` + 发放 `status_enum` | 见下方 **分支表**；**写库点 D** |
| 17 | Cmd | **主医嘱 Charge 回退**（⑥ + ③ 诊疗无 Dispense） | **长临统一** `voidOrderExecuteChargeItemsByProcedureIds`（**BR-IP-CXL-008/009**） |
| 18 | Cmd | **汇总过滤重建**（按 `summaryNo` 去重） | `CancelExecutionSummaryRebuildContext` | 见序号 19～25；**写库点 E** |
| 19 | Asm | `Mapper#toCancelNurseStationOrderExecutionsResponse` | ① 成功对冲集合 + ⑱ 新 summaryNo | — |

**序号 16 主医嘱 Dispense 分支（互斥，说明列必填）**

| 条件 | 调用 | 说明 |
| --- | --- | --- |
| 无关联发放行 | — | 跳过（纯诊疗） |
| `status=PREPARATION` | `removeUndispensedDispense` | **BR-IP-CXL-007**（长临统一） |
| `status=SUMMARIZED` | 不单独删行；登记 `SummaryRebuildHint` | 整单撤+重建在 ⑱；本步仅记录 `summaryNo` 与 `dispenseId` 入剔除集 |
| `status=COMPLETED` | `createPendingRefundDispense` | `refundRequestId`←原发放 **`medReqId`**；`procedureId`←序号 15 **新 CANCEL 行**（**BR-IP-CXL-010**）；**禁止**汇总重建 |

**序号 18～25 汇总过滤重建（每个 `summaryNo` 至多一次）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 18a | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | 快照 **SupplyStatus** 写入 `CancelExecutionSummaryRebuildContext`（**BR-IP-CXL-012** 乐观读） |
| 18b | Gate | 快照校验 | 若当前态 **AGREE** 或任一关联发放 **COMPLETED** → **EX-IP-CXL-204**；写前再次读 busNo，态变 → **EX-IP-CXL-204** |
| 18c | Cmd | **若 PENDING_APPROVAL** | `cancelMedicationDispenseSummary` + `removeByBusNo` |
| 18d | Cmd | **若 APPROVAL** | `rejectRequest` + `cancelMedicationDispenseSummary` + **`removeByBusNo`**（驳回残单含取消行，必须删掉再重建） |
| 18e.1 | Cmd | **剔除行 BR-IP-CXL-007** | 撤汇总后剔除行已回 **PREPARATION**；**长临统一** `removeUndispensedDispense` |
| 18e | Pre | `listMedicationDispensesBySummaryNo`（**撤单前预读**）+ 内存过滤 | 剔除 `excludedDispenseIds`；剩余为空 → **跳过 18f 重建**（18e.1 仍执行）；撤单后 `summary_no` 已清空，故须在 18c/18d **之前**预读全量 id |
| 18f | Cmd | **剩余非空** | `InpatientMedicationDispenseChargePricingLoader#mapPendingSettlementChargeByDispenseIds`（**仅 PENDING_SETTLEMENT**，键=`dispenseId`）→ `buildMergeGroupsByLocationAndPrice` → `summarizeMedicationDispenses` → `createSummarySupplyRequest`（初态 **PENDING_APPROVAL**）；合并规则同 **AS-IP-NUR-010 IF-IP-SUM-04**；**禁止**用 `listChargeItemsForMedicationDispensePricing` + `serviceId` |

> **互斥说明**：18c 与 18d **只执行其一**——若快照为 **PENDING_APPROVAL** 走 18c，否则若 **APPROVAL** 走 18d；其他态已在 18b 拒绝。

## 4. QueryService 与领域事件

| 事件 | 触发步骤 | 读侧刷新 |
| --- | --- | --- |
| `ProcedureRecordCancelledEvent` | 15、12 | **IF-IP-EXE-01** 槽位 executed→cancelled |
| `ServiceRequestExecutedRemovedEvent` | 11 | 绑定诊疗列表 |
| `ChargeItemExecutionCancelledEvent` | 11、17 | 住院待结算占用 |
| `MedicationDispenseReserveReleasedEvent` / 删除事件 | 16 未发删/回退；**18e.1 剔除行删/回退** | 待发池、**IF-IP-SUM-01** |
| `MedicationDispenseSummaryCancelledEvent` | 18c/18d | 汇总 Tab |
| `MedicationDispenseSummarizedEvent` | 18f | 新 summaryNo |
| `MedicationDispensePendingRefundCreatedEvent` | 16 COMPLETED | **AS-IP-PHA-002** 退药队列 |
| DeviceDispense / InventoryItem 回补事件 | 13～14 | 库存查询 |

> 列表不在本文另开读 IF；前端取消成功后 **刷新 IF-IP-EXE-01**（`executeTab=executed/cancelled`）及汇总/退药相关 Tab（事件驱动或主动 refetch）。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 序号对照（IF-IP-CXL-01）

| 写库点 | §3 序号 | 聚合写入顺序 |
| --- | --- | --- |
| A | 11 | `wkf_service_request` 软删 + detail 软删 |
| B | 13 | `wkf_device_dispense` revert |
| B′ | 14 | `inventory_item` 回补（条件） |
| C | 15 | `cln_procedure` CANCEL 行 |
| D | 16 | `med_medication_dispense` 删/回退/退药 |
| D′ | 17 | `adm_charge_item` 逻辑删或逆激活 |
| E | 18c～18f | `med_medication_dispense.summary_no` + `wkf_supply_request` 撤/重建；**18e.1** 剔除行删/回退 |

### 5.2 事务边界

| 项 | 约定 |
| --- | --- |
| 事务注解 | `NurseStationOrderCancelExecuteAppService#cancelNurseStationOrderExecutions` **`@Transactional(rollbackFor = Exception.class)`** |
| 范围 | 序号 11～18f **同一事务** |
| 失败 | 任一步 BusinessException 或 RuntimeException → **整批回滚**（**BR-IP-CXL-015**） |
| 并发 | 18b Supply 态 **读-校验-写**；冲突抛 **EX-IP-CXL-204**，不部分提交 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 同 COMPLETED 不可重复取消 | Pre 序号 4 对冲检测 | §3.2 序号 4 |
| 同批重复 procedureId 拒绝 | Gate 序号 1 去重 | §3.2 序号 1 |

### 6.2 并发控制

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 汇总 Supply 态乐观校验 | `WriteAssembler#withdrawAndRebuildSummaryIfNeeded` | §3.2 序号 18b |
| 域层 Charge 结算 Gate | `ChargeItemDomainService#voidOrderExecuteChargeItemsByProcedureIds` | §3.2 序号 17 |

### 6.3 重试与容错

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 可重试错误 | **EX-IP-CXL-201/202/204/205** | Gate/Pre/Cmd |
| 不可重试 | **EX-IP-CXL-203** 已结算 | Pre 序号 7 |

### 6.4 数据脱敏

| 承诺 | 说明 |
| --- | --- |
| 无新增 PII 出参 | Response 仅 id 与 summaryNo |

### 6.5 审计日志

| 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 操作日志 | AppService 成功路径 `log.info`：`operatorId`、`encounterIds`、`procedureIds`、`rebuiltSummaryNos` | Cmd 完成后 |
| 取消原因 | 写入 Procedure CANCEL 行 `status_reason_text` | 序号 15 |

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 长期药·未发 | Dispense 删除 + Charge 逻辑删 | 集成 | P0 |
| 临时药·未发 | Dispense→DRAFT + Charge→PLANNED 无 procedureId | 集成 | P0 |
| 汇总·待发送 | 自动撤单 + 过滤重建；取消行不在新单 | 集成 | P0 |
| 汇总·已发送 | reject + removeByBusNo + 重建；驳回残单无取消行；并发 AGREE 拒绝 | 集成 | P0 |
| 已发药 | PENDING_REFUND；原 COMPLETED 保留 | 集成 | P0 |
| 绑定诊疗 | based_on 反查；派生 SR 软删 + Charge 回退 | 集成 | P0 |
| 绑定耗材 | based_on 反查；已发回补；DeviceRequest 不变 | 集成 | P0 |
| 结算 Gate | SETTLED 整批 **EX-IP-CXL-203** | 单元 | P0 |
| CANCEL 对冲 | refund_id 指向原 COMPLETED | 集成 | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 批量上限 | 51 条 **EX-IP-CXL-201** | 单元 | P1 |
| 同事务回滚 | Cmd 中途失败无部分落库 | 集成 | P0 |

### 7.3 查询结果一致性

| 场景 | 要点 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 取消后列表 | IF-IP-EXE-01 executed 减、cancelled 增 | E2E | P0 |

### 7.4 集成测试

| 场景 | 要点 | 层级 | 优先级 |
| --- | --- | --- | --- |
| 与 AS-IP-NUR-004 联调 | 先执行再取消；槽位 procedureId 回传 | E2E | P0 |
| 与 AS-IP-NUR-010 联调 | SUMMARIZED 自动重建后 IF-IP-SUM-02 可见新单 | E2E | P1 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 无 | 领域 Gap 已关闭；应用层成码后按上表执行 | — |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.9 | 2026-10-16 | **BR-IP-CXL-008**：`voidOrderExecuteChargeItemsByProcedureIds` 覆盖执行族（含 `anesthesia-execute`）；对齐 **AS v1.15** / **DM-ADM-011 v1.6.27** |
| v1.8 | 2026-10-12 | **BR-IP-CXL-013/014**：本槽关联秒级 occurrence + Charge.occurrence 回落；`filterBinding*ForSlot` 防纳秒差漏冲；对齐 **AS v1.14** |
| v1.7 | 2026-10-09 | **BR-IP-CXL-013**：`loadBindingServiceRequests` 仅保留 `order-execute`；排除皮试检查 companion；对齐 **AS v1.12** |
| v1.6 | 2026-08-04 | **BR-IP-CXL-011**：18d APPROVAL 增 `removeByBusNo`；18f 重建定价改 `InpatientMedicationDispenseChargePricingLoader`（`PENDING_SETTLEMENT`+`dispenseId`）；对齐 **AS v1.8** |
| v1.5 | 2026-07-11 | **BR-IP-CXL-016**：§3 序号 2 增补就诊 Gate；**EX-IP-CXL-206**；对齐 **AS v1.7** |
| v1.4 | 2026-06-20 | **support 落点对齐**：`InpatientNurseStationTcmsupport → `module.querysupport.nursestation`；`DeviceDispenseNurseWorkflowsupport 注入 `module.orchestration.nursestation`；对齐 **AS v1.6** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.nursestation-device-dispense）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.3 | 2026-06-20 | **BR-IP-NUR-012-TCM-001**：Gate 后 `expandCancelProcedureIdsByTcmPrescription` 中药整方扩行；对齐 **AS v1.5** |
| v1.2 | 2026-06-17 | **长临取消同构**：`removeUndispensedDispense` + `voidOrderExecuteChargeItemsByProcedureIds`；移除 `rollbackUndispensedDispense`/`deactivateChargeByExe` 写路径；对齐 **AS v1.4** |
| v1.0 | 2026-06-10 | 首版：IF-IP-CXL-01 目标态全量契约；绑定 based_on → 主路径 CANCEL → Dispense/Charge/汇总重建；对齐 **AS-IP-NUR-012 v1.1** 与已关闭域 Gap 001～007 |
| v1.1 | 2026-06-10 | **BR-IP-CXL-010**：`toCreatePendingRefundDispenseCommand` 明确 `medReqId` + `cancelProcedureId`；对齐 **DM-MED-002 §6.1.8 v1.3.5** 与 **AS v1.3** |
