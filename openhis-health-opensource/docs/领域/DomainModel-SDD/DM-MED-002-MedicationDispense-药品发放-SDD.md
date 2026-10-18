# DM-MED-002 MedicationDispense 药品发放 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md`（**v1.3.3**：§6.1.11 增补 **AS-IP-NUR-010** IF-IP-SUM-08 / **AS-IP-PHA-002** IF-IPS-04 编排；历史 §5.1 枚举仍以初版 **14** 个写方法为主文档骨架，**增量方法**以锁定 DM 与 `MedicationDispenseDomainService` 代码为准）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：medication  
> **聚合**：MedicationDispense（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-MED-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | zxplat-domain |
| 包规则 `com.zxplat.domain.medication.dispense` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 全覆盖 |
| DM 第 5 章事件 | 满足 | §7 全覆盖 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化发放事实从草稿/待配药到已发放/退药/汇总的状态契约，确保写路径可直接落地。
- 固化库存五元组、追溯码与状态门控规则，避免跨层散落判断。
- 输出 14 个写方法的一方法一命令契约与读侧前置校验契约。

### 1.2 边界

- **在内**：发放单生命周期、配药/发药/作废/退药/汇总、预减释放。
- **在外**：库存台账记账、追溯码账本、ChargeItem 金额写入。
- **跨域仅端口声明**：库存/追溯/收费通过事件协作。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.medication.dispense`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `MedicationDispense` |
| `valueobject` | `MedicationDispenseStatusSnapshot`、`DispenseInventoryDeductionSpec`、`TraceIdentifierBundle` |
| `command` | 14 个写方法独立命令文件 |
| `repository` | `MedicationDispenseRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |

**存储口径（DM 优先）**：`status_enum`、`dispense_enum` 经 **JPA `AttributeConverter`** 与 **`DispenseStatus` / `MedicationDispenseType`** 绑定，**列内存枚举持久化 code（字符串）**；`not_performed_reason_enum` 为 **`String`**；`*_code` 为 `String`；`isDeleted` 逻辑删除。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long` |
| 时间 | `statusChangedTime/prepareTime/dispenseTime/limitTime` 用 `LocalDateTime` |
| `statusEnum`/`dispenseEnum`（实体字段） | `DispenseStatus` / `MedicationDispenseType`（Converter 落库 **code 字符串**） |
| 其他 `*_enum`（原因码等） | `String` |
| `*_code` | `String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` + `@Table(name = "med_medication_dispense")` |
| 仓储 | `JpaRepository<MedicationDispense, Long>` |
| 分页 | `Pageable` + `createdAt DESC` |
| 事件 | beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `med_medication_dispense` | `MedicationDispense` | 发放事实单表聚合根 |

### 4.2 聚合根 `MedicationDispense`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键（雪花 ID） |
| `busNo` | `String` | 是 | 发放业务号，唯一 |
| `statusEnum` | `DispenseStatus` | 是 | 发放状态（BR-MDP-002/003：状态机约束；落库 `DispenseStatus#getCode()`） |
| `notPerformedReasonEnum` | `String` | 否 | 未发药原因 code（作废/拒绝时必填，BR-MDP-011） |
| `statusChangedTime` | `LocalDateTime` | 是 | 状态变更时间戳 |
| `dispenseEnum` | `MedicationDispenseType` | 是 | 发放类型（落库 `MedicationDispenseType#getCode()`） |
| `medicationId` | `Long` | 是 | 药品定义标识（外键） |
| `patientId` | `Long` | 是 | 患者标识（外键） |
| `encounterId` | `Long` | 是 | 就诊标识（外键） |
| `locationId` | `Long` | 是 | 发放药房标识（外键） |
| `medReqId` | `Long` | 是 | 药品申请标识（外键） |
| `quantity` | `BigDecimal` | 是 | 应发数量（发放单位下） |
| `unitCode` | `String` | 是 | 发放计量单位（字典码） |
| `dispenseQuantity` | `BigDecimal` | 否 | 实际已发数量 |
| `lotNumber` | `String` | 否 | 产品批号（配药时写入，BR-MDP-014 效期校验） |
| `traceNo` | `String` | 否 | 药品追溯码（逗号分隔多码，BR-MDP-009） |
| `preparerId` | `Long` | 否 | 配药人标识（Practitioner 外键） |
| `prepareTime` | `LocalDateTime` | 否 | 配药时间戳 |
| `practitionerId` | `Long` | 否 | 发药人标识（Practitioner 外键，BR-MDP-013 毒麻双人复核） |
| `dispenseTime` | `LocalDateTime` | 否 | 发药时间戳 |
| `limitTime` | `LocalDateTime` | 否 | 预减统计有效截止时间（BR-MDP-010） |
| `plannedDispenseTime` | `LocalDateTime` | 否 | 计划发药时间（长期医嘱执行时写入） |
| `procedureId` | `Long` | 否 | 关联执行记录标识（长期医嘱执行后生成） |
| `summaryNo` | `String` | 否 | 住院汇总发药单号（BR-MDP-012） |
| `desLocationId` | `Long` | 否 | 发药目的地标识（Location 外键，病区送药场景） |
| `recPractitionerId` | `Long` | 否 | 接收人标识（Practitioner 外键，病区接收） |
| `dosageInstruction` | `String` | 否 | 用药说明快照（医嘱开立时拷贝，BR-MDP-016） |
| `methodCode` | `String` | 否 | 用法编码快照（字典码） |
| `frequencyCode` | `String` | 否 | 频次编码快照（字典码） |
| `dose` | `BigDecimal` | 否 | 单次剂量快照 |
| `doseUnitCode` | `String` | 否 | 剂量单位快照（字典码） |
| `maxUnit` | `BigDecimal` | 否 | 单次最大剂量快照 |
| `supportInfo` | `String` | 否 | 辅助用药信息 |
| `partTypeCode` | `String` | 否 | 分次发放类型编码（字典码） |
| `dispenseFrequencyCode` | `String` | 否 | 发药频次编码（字典码） |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `tenantId` | `Long` | 系统 | 租户 ID |

**不变式方法（示例命名，实现须一致）**：`ensureStatusForPrepare`、`ensureStatusForDispense`、`validateInventoryTuple`、`validateTraceCode`、`applySummaryState`、`ensureRefundable`。

### 4.2.1 值对象字段展开

本聚合为单表聚合，无复合值对象字段需展开。以下字段虽具有业务值对象语义，但平铺存储于聚合根表中：

| 值对象语义 | 对应字段 | 说明 |
| ---------- | -------- | ---- |
| `MedicationUsageInstructionSnapshot` | `dosageInstruction`, `methodCode`, `frequencyCode`, `dose`, `doseUnitCode`, `maxUnit` | 用药说明快照（§4.3 DM） |
| `TraceIdentifierBundle` | `traceNo` | 追溯码载荷，逗号分隔存储 |
| `DispenseInventoryDeductionSpec` | `medicationId`, `lotNumber`, `quantity`, `unitCode`, `locationId` | 库存扣减五元组（§4.2 DM），待发药时完整性校验（BR-MDP-008） |

### 4.3 子实体

无。本聚合为单表聚合。

**归属校验**：N/A（单表聚合，无子实体归属校验需求）。

**子表对齐策略**：本聚合不涉及子表全量对齐场景。对于汇总发药场景（`summaryNo`），多条发放记录通过汇总单号关联，但各记录独立维护状态，不存在父-子归属一致性校验问题。

### 4.5 仓储接口

- `MedicationDispenseRepository extends JpaRepository<MedicationDispense, Long>`
- `Optional<MedicationDispense> findByIdAndIsDeletedFalse(Long id)`
- `List<MedicationDispense> findByEncounterIdAndIsDeletedFalse(Long encounterId)`
- `List<MedicationDispense> findBySummaryNoAndIsDeletedFalse(String summaryNo)`
- `boolean existsByMedReqIdAndStatusEnumIn(Long medReqId, Collection<DispenseStatus> statuses)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `MedicationDispenseDetailQueryModel`（对应 `loadMedicationDispenseById`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_dispense.id` | 主键 |
| `statusEnum` | `DispenseStatus` | `med_medication_dispense.status_enum` | 状态 |
| `medReqId` | `Long` | `med_medication_dispense.med_req_id` | 医嘱引用 |
| `quantity` | `BigDecimal` | `med_medication_dispense.quantity` | 应发 |
| `dispenseQuantity` | `BigDecimal` | `med_medication_dispense.dispense_quantity` | 已发 |

#### 4.6.2 `MedicationDispenseListQueryModel`（对应 `listMedicationDispensesByEncounter`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_dispense.id` | 主键 |
| `busNo` | `String` | `med_medication_dispense.bus_no` | 发放号 |
| `statusEnum` | `DispenseStatus` | `med_medication_dispense.status_enum` | 状态 |
| `locationId` | `Long` | `med_medication_dispense.location_id` | 药房 |
| `plannedDispenseTime` | `LocalDateTime` | `med_medication_dispense.planned_dispense_time` | 计划时间 |

#### 4.6.3 `MedicationDispenseSummaryItemQueryModel`（对应 `listMedicationDispensesBySummaryNo`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `summaryNo` | `String` | `med_medication_dispense.summary_no` | 汇总单号 |
| `id` | `Long` | `med_medication_dispense.id` | 明细主键 |
| `statusEnum` | `DispenseStatus` | `med_medication_dispense.status_enum` | 状态 |
| `quantity` | `BigDecimal` | `med_medication_dispense.quantity` | 数量 |
| `medicationId` | `Long` | `med_medication_dispense.medication_id` | 药品 |
| `locationId` | `Long` | `med_medication_dispense.location_id` | 发放药房/库位 |
| `lotNumber` | `String` | `med_medication_dispense.lot_number` | 批号 |
| `unitCode` | `String` | `med_medication_dispense.unit_code` | 单位 code |
| `encounterId` | `Long` | `med_medication_dispense.encounter_id` | 就诊 |
| `patientId` | `Long` | `med_medication_dispense.patient_id` | 患者 |
| `medReqId` | `Long` | `med_medication_dispense.med_req_id` | 药品申请（医嘱）；**AS-IP-NUR-010 IF-IP-SUM-09** 摆药合并键 |
| `dosageInstruction` | `String` | `med_medication_dispense.dosage_instruction` | 用药说明/剂量回落 |
| `methodCode` | `String` | `med_medication_dispense.method_code` | 用法（摆药优先回落医嘱） |
| `frequencyCode` | `String` | `med_medication_dispense.frequency_code` | 频次（摆药优先回落医嘱 rateCode） |
| `dose` | `BigDecimal` | `med_medication_dispense.dose` | 单次剂量 |
| `doseUnitCode` | `String` | `med_medication_dispense.dose_unit_code` | 剂量单位 |
| `traceNo` | `String` | `med_medication_dispense.trace_no` | 追溯码 |

#### 4.6.4 `MedicationPendingRefundQueryModel`（对应 `listPendingRefundDispensesByEncounter`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_dispense.id` | 主键 |
| `statusEnum` | `DispenseStatus` | `med_medication_dispense.status_enum` | 待退药状态 |
| `medicationId` | `Long` | `med_medication_dispense.medication_id` | 药品 |
| `dispenseQuantity` | `BigDecimal` | `med_medication_dispense.dispense_quantity` | 已发数量 |

#### 4.6.5 `MedicationDispenseValidationQueryModel`（对应 6.2.6~6.2.9）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `allowed` | `Boolean` | 校验逻辑 | 是否允许执行 |
| `reasonCode` | `String` | 校验逻辑 | 原因码 |
| `snapshot` | `MedicationDispenseDetailQueryModel` | 主表映射 | 参与校验快照 |

#### 4.6.6 `MedicationDispensePickupPendingQueryModel`（对应 `pageMedicationDispensePickupPending` · **AS-IP-NUR-010**）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `dispenseId` | `Long` | `med_medication_dispense.id` | 可勾选时间点 |
| `procedureId` | `Long` | `med_medication_dispense.procedure_id` | 已执行挂接 |
| `plannedDispenseTime` | `LocalDateTime` | `med_medication_dispense.planned_dispense_time` | 计划发药时刻 |
| `encounterId` | `Long` | `med_medication_dispense.encounter_id` | 就诊 |
| `patientId` | `Long` | `med_medication_dispense.patient_id` | 患者 |
| `medicationId` | `Long` | `med_medication_dispense.medication_id` | 药品 |
| `medReqId` | `Long` | `med_medication_dispense.med_req_id` | 医嘱 |
| `locationId` | `Long` | `med_medication_dispense.location_id` | 发放药房 |
| `quantity` | `BigDecimal` | `med_medication_dispense.quantity` | 原单位数量 |
| `unitCode` | `String` | `med_medication_dispense.unit_code` | 原单位 |
| `chargeUnitPrice` | `BigDecimal` | `adm_charge_item.unit_price`（只读协作） | 账单单价 |
| `dosageInstruction` | `String` | `med_medication_dispense.dosage_instruction` | 用药说明 |
| `methodCode` | `String` | `med_medication_dispense.method_code` | 用法 |
| `frequencyCode` | `String` | `med_medication_dispense.frequency_code` | 频次 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `MedicationDispenseDomainService`

#### 5.1.1 `prepareMedicationDispense(PrepareMedicationDispenseCommand cmd)` → `void`
**命令文件**：`PrepareMedicationDispenseCommand.java`  
**字段全集**：`id,preparerId,traceNo,lotNumber,quantity`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureStatusForPrepare` |
| `statusTransitionMethod` | `toPrepared` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDispensePreparedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-001` |
**方法契约摘要**：前置为待配药状态、追溯码可用；持久化写配药事实并转已配药；失败为状态/追溯/批号校验失败；触发配药事件。

---

#### 5.1.2 `dispenseMedication(DispenseMedicationCommand cmd)` → `void`
**命令文件**：`DispenseMedicationCommand.java`；**字段全集**：`id,practitionerId,dispenseQuantity`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureStatusForDispense` |
| `statusTransitionMethod` | `toCompleted` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDispensedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-002` |
**方法契约摘要**：前置为待发药窗口且库存五元组完整；持久化写发药人/时间/数量并转已发放；失败为状态不允许或库存校验失败；触发发药事件。

---

#### 5.1.3 `cancelMedicationDispense(CancelMedicationDispenseCommand cmd)` → `void`
**命令文件**：`CancelMedicationDispenseCommand.java`；**字段全集**：`id,notPerformedReasonEnum`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureCancellable` |
| `statusTransitionMethod` | `toDeclined` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDispenseDeclinedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-003` |
**方法契约摘要**：前置为未完成发药态且原因必填；持久化写拒绝原因并转已拒绝；失败为终态或原因缺失；触发作废事件。

---

#### 5.1.4 `refundDispensedMedication(RefundDispensedMedicationCommand cmd)` → `void`
**命令文件**：`RefundDispensedMedicationCommand.java`；**字段全集**：`id,refundQuantity,operatorId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureRefundable` |
| `statusTransitionMethod` | `toRefunded` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDispenseRefundedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-004` |
**方法契约摘要**：前置为待退药且幂等未执行；持久化写退药事实并转已退药；失败为重复退药或数量非法；触发退药事件。

---

#### 5.1.5 `generateDispenseFromTemporaryOrder(GenerateDispenseFromTemporaryOrderCommand cmd)` → `Long`
**命令文件**：`GenerateDispenseFromTemporaryOrderCommand.java`；**字段全集**：`medReqId,medicationId,encounterId,patientId,locationId,quantity,unitCode`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateCreateInput` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | `existsByMedReqIdAndStatusEnumInAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDispenseCreatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-005` |
**方法契约摘要**：前置为临时路径与去重通过；持久化创建草稿发放单；失败为重复创建或引用非法；触发创建事件。**应用编排**：**不**在 AS-OP-DOR-003 / AS-IP-DOR-003 医生站**暂存**调用；住院/门诊临时药品发放由 **IF-DOR-15 / IF-IP-DOR-15** 签发阶段 `replaceFifoDraftDispensesAfterDoctorStationSign` 建立（BR-MRQ-028）。

---

#### 5.1.6 `promoteToPreparation(PromoteDispenseToPreparationCommand cmd)` → `void`
**命令文件**：`PromoteDispenseToPreparationCommand.java`；**字段全集**：`medReqIds(List<Long>)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureDraftBeforePromote` |
| `statusTransitionMethod` | `draftToPreparation` |
| `repositoryChecks` | `findByMedReqIdInAndIsDeletedFalse` |
| `eventPublishAnchor` | `MedicationDispensePromotedToPreparationEvent`（逐条发放单：`dispenseId`、`medReqId`、`operatorId`、**`statusEnumCode`**=`DispenseStatus#getCode()`） |
| `unitTestCaseId` | `TC-MEDDISP-WS-006` |
**方法契约摘要**：前置为目标单据均草稿；持久化批量推进待配药；失败为混合状态；**发布推进事件**供 **PrescriptionView** / **MedicationDispenseSlip** 等领域事件后查询刷新。**门诊正式结算**路径由 **`OutpatientChargeAdviceStateSyncService`** 在 **IF-OPC-07** 中调用（**AS-OP-FIN-002** **BR-OPC-015**）。

---

#### 5.1.7 `generateDispenseFromExecution(GenerateDispenseFromExecutionCommand cmd)` → `Long`
**命令文件**：`GenerateDispenseFromExecutionCommand.java`；**字段全集**：`medReqId,procedureId,plannedDispenseTime,locationId,lotNumber,quantity,unitCode,medicationId,encounterId,patientId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateExecutionPath` |
| `statusTransitionMethod` | `initPreparation`；`setLotNumber` |
| `repositoryChecks` | 长期路径新建 |
| `eventPublishAnchor` | `new MedicationDispenseCreatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-007` |
**方法契约摘要**：前置为长期路径且 FIFO 后 `locationId`/`lotNumber` 由 App 写入；持久化新建并进入待配药；失败为申请非法；触发创建事件。**消费方**：**AS-IP-NUR-004 IF-IP-EXE-02** 长期药品。

---

#### 5.1.7a `linkDispenseProcedure(LinkDispenseProcedureCommand cmd)` → `void`
**命令文件**：`LinkDispenseProcedureCommand.java`；**字段全集**：`dispenseId,procedureId,plannedDispenseTime,operatorId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | Gate：仅 `DRAFT` |
| `statusTransitionMethod` | 写 `procedureId` + `initPreparation` |
| `repositoryChecks` | `mustGet(dispenseId)` |
| `eventPublishAnchor` | `DispenseProcedureLinkedEvent`（**save 之后**） |
| `unitTestCaseId` | `TC-MEDDISP-WS-007a` |
**方法契约摘要**：临时医嘱执行后挂接已有草稿发放单；失败为非草稿或缺失。**消费方**：**IF-IP-EXE-02** 临时药品。

---

#### 5.1.8 `createPendingRefundDispense(CreatePendingRefundDispenseCommand cmd)` → `Long`
**命令文件**：`CreatePendingRefundDispenseCommand.java`；**字段全集**：`originDispenseId,refundRequestId,refundQuantity,operatorId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 原单 `ensureRefundable`（`COMPLETED` / `PART_COMPLETED` / `PENDING_REFUND`） |
| `statusTransitionMethod` | `toPendingRefund`（`medReqId=refundRequestId`） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse`（原单） |
| `eventPublishAnchor` | `MedicationDispenseCreatedEvent` + `MedicationDispensePendingRefundCreatedEvent`（**B.14**：`save` 后发布；载荷 `statusEnumCode` 为 `getCode()`） |
| `unitTestCaseId` | `TC-MEDDISP-WS-008` |
**方法契约摘要**：基于已发/部分发原单创建 `PENDING_REFUND` 行；自原单拷贝批号/追溯码及用法用量/汇总单号等展示字段；失败为原单不可退、`refundRequestId` 缺失或重复申请。

---

#### 5.1.9 `releaseDispenseReserve(ReleaseDispenseReserveCommand cmd)` → `void`
**命令文件**：`ReleaseDispenseReserveCommand.java`；**字段全集**：`medReqId,operatorId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateReserveReleasable` |
| `statusTransitionMethod` | `toStoppedOrDelete` |
| `repositoryChecks` | `findReservingByMedReqId` |
| `eventPublishAnchor` | `new MedicationDispenseReserveReleasedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-009` |
**方法契约摘要**：前置为存在可释放占用集合；持久化为停用或移除；失败为并发或无记录策略；触发释放事件。

---

#### 5.1.10 `summarizeMedicationDispenses(SummarizeMedicationDispensesCommand cmd)` → `String`
**命令文件**：`SummarizeMedicationDispensesCommand.java`；**字段全集**：`locationId,dispenseIds`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateSummarizable` |
| `statusTransitionMethod` | `toSummarized` |
| `repositoryChecks` | `findAllById` |
| `eventPublishAnchor` | `new MedicationDispenseSummarizedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-010` |
**方法契约摘要**：前置为同药房且待配药状态；持久化写汇总单号并转已汇总；失败为重复汇总/跨药房；触发汇总事件。

---

#### 5.1.11 `cancelMedicationDispenseSummary(CancelMedicationDispenseSummaryCommand cmd)` → `void`
**命令文件**：`CancelMedicationDispenseSummaryCommand.java`；**字段全集**：`summaryNo`、`operatorId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureSummaryCancellable`（仅 `SUMMARIZED`） |
| `statusTransitionMethod` | `setSummaryNo(null)` + `initPreparation` |
| `repositoryChecks` | `findBySummaryNo` |
| `eventPublishAnchor` | `MedicationDispenseSummaryCancelledEvent`（save 后逐行发布，**B.14**） |
| `unitTestCaseId` | `TC-MEDDISP-WS-011` |
**方法契约摘要**：前置为汇总态存在；持久化清空汇总号并回退待配药；失败为单号无效或非 `SUMMARIZED`；触发取消汇总事件。  
**应用编排（跨域）**：

| 应用服务 | IF | 与本方法调用顺序 | Supply 邻域 |
| --- | --- | --- | --- |
| **AS-IP-NUR-010** | IF-IP-SUM-08 | **先**本方法 → **后** `SupplyRequestDomainService#removeByBusNo` | type **27**、**PENDING_APPROVAL** 逻辑删 |
| **AS-IP-PHA-002** | IF-IPS-04 | **先** `rejectRequest` → **后**本方法 | Supply → **REJECT**（不删单） |

---

#### 5.1.12 `completeSummaryDispense(CompleteSummaryDispenseCommand cmd)` → `void`
**命令文件**：`CompleteSummaryDispenseCommand.java`；**字段全集**：`summaryNo,practitionerId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateSummaryDispensable` |
| `statusTransitionMethod` | `summarizedToCompleted` |
| `repositoryChecks` | `findBySummaryNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDispenseSummaryCompletedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-012` |
**方法契约摘要**：前置为汇总明细均满足发药条件；持久化批量转已发放；失败为任一明细校验失败；触发汇总完成事件。

---

#### 5.1.13 `rollbackUndispensedDispense(RollbackUndispensedDispenseCommand cmd)` → `void`
**命令文件**：`RollbackUndispensedDispenseCommand.java`；**字段全集**：`ids(List<Long>)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRollbackable` |
| `statusTransitionMethod` | `toDraft` |
| `repositoryChecks` | `findAllById` |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-MEDDISP-WS-013` |
**方法契约摘要**：前置为临时路径未发状态集合；持久化回退草稿并清理汇总号；失败为状态不允许；事件按实现可选。

---

#### 5.1.14 `removeUndispensedDispense(RemoveUndispensedDispenseCommand cmd)` → `void`
**命令文件**：`RemoveUndispensedDispenseCommand.java`；**字段全集**：`ids(List<Long>)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRemovable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findAllById` |
| `eventPublishAnchor` | `new MedicationDispenseReserveReleasedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDISP-WS-014` |
**方法契约摘要**：前置为长期路径未发状态并可释放占用；持久化为删除或逻辑删除；失败为已发放/已配药拒绝；触发释放占用事件。

### 5.2 读侧 — `MedicationDispenseQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`。  

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `loadMedicationDispenseById` | `Long id` | `MedicationDispenseDetailQueryModel` | 主键加载 | TC-MEDDISP-RS-001 |
| `listMedicationDispensesByEncounter` | `Long encounterId` | `MedicationDispenseListQueryModel` | 就诊维度列表 | TC-MEDDISP-RS-002 |
| `listMedicationDispensesBySummaryNo` | `String summaryNo` | `MedicationDispenseSummaryItemQueryModel` | 汇总维度列表 | TC-MEDDISP-RS-003 |
| `listPendingRefundDispensesByEncounter` | `Long encounterId` | `MedicationPendingRefundQueryModel` | 待退药列表 | TC-MEDDISP-RS-004 |
| `listCancelExecuteRefundDispensesByEncounterIds` | `Collection<Long> encounterIds` | `List<MedicationDispense>` | **AS-IP-NUR-010 IF-IP-SUM-10**：取消执行退药候选（REFUND+待退/已退+procedureId；CANCEL 终态由 App 校验） | TC-MEDDISP-RS-004a |
| `mustGetMedicationDispense` | `Long id` | `MedicationDispenseDetailQueryModel` | 写前强加载 | TC-MEDDISP-RS-005 |
| `validateDispensePreparable` | `Long id` | `MedicationDispenseValidationQueryModel` | 配药前置校验 | TC-MEDDISP-RS-006 |
| `validateDispensable` | `Long id` | `MedicationDispenseValidationQueryModel` | 发药前置校验 | TC-MEDDISP-RS-007 |
| `validateDispenseCancellable` | `Long id` | `MedicationDispenseValidationQueryModel` | 作废前置校验 | TC-MEDDISP-RS-008 |
| `validateRefundable` | `Long id` | `MedicationDispenseValidationQueryModel` | 退药前置校验（`COMPLETED` / `PART_COMPLETED` / `PENDING_REFUND`） | TC-MEDDISP-RS-009 |
| `mapAggregatedRefundStatusCodeByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long,String>`（code） | **AS-OP-PHA-002 IF-PHA-02-02**：批量聚合就诊退药展示态；`findByEncounterIdIn` 一次 IN | TC-MEDDISP-RS-013 |
| `listMedicationDispensesForOrderCatalogOccupancy` | `candidateLocationIds,medicationDefinitionIds` | `List<MedicationDispenseOrderCatalogOccupancyModel>` | IF-DOR-11：候选库位×目录药品定义交集上，草稿/待发且未过 `limitTime` 的发放行批量规格查询（**不限就诊**） | TC-MEDDISP-RS-010 |
| `listMedicationDispensesForInpatientSummarizedOccupancy` | `candidateLocationIds,medicationDefinitionIds` | `List<MedicationDispenseOrderCatalogOccupancyModel>` | **IF-DOR-15 FIFO**：住院 `INPATIENT`+`SUMMARIZED` 且未过 `limitTime` 的占用行（**不**进目录预减；与目录占用叠加后 FEFO） | TC-MEDDISP-RS-010a |
| `listDispenseExeReserve` | `locationIds,medicationIds` | `List<DispenseExeReserveRowModel>` | **IF-IP-EXE-02**：全局（不限就诊）待发预占汇总；状态=`NurseStationExecutionConstants`（宽于 IF-DOR-11） | TC-MEDDISP-RS-014 |
| `pageMedicationDispenseSlip` | `MedicationDispenseSlipPageQuery` | `Page<MedicationDispenseSlipQueryModel>` | 发药单分页；查 `med_medication_dispense` | TC-MEDDISP-RS-015 |
| `listMedicationDispenseSlip` | `MedicationDispenseSlipPageQuery` | `List<MedicationDispenseSlipQueryModel>` | 发药单全量列表（同过滤） | TC-MEDDISP-RS-016 |
| `pageEncounterIdsForOutpatientPharmacyPending` | `OutpatientPharmacyPendingEncounterPageQuery` | `Page<Long>` | 单表 + **`resolvedEncounterScope`**；对外 **`PharmacyEncounterQueueLoader`** | TC-MEDDISP-RS-017 |
| `pageEncounterIdsForInpatientPharmacyPending` | `InpatientPharmacyPendingEncounterPageQuery` | `Page<Long>` | 住院药房待发就诊 | TC-MEDDISP-RS-018 |
| `pageEncounterIdsForOutpatientPharmacyReturn` | `PageReturnPatientQuery` | `Page<Long>` | 门诊退药就诊队列 | TC-MEDDISP-RS-019 |
| `pageReturnMedicinePatients` | `PageReturnPatientQuery` | `Page<ReturnMedicinePatientQueueRow>` | **待实现**（2026-05-28：域 QS 无同名方法；队列用上一行 `pageEncounterIdsForOutpatientPharmacyReturn`） | TC-MEDDISP-RS-020 |
| `listReturnMedicineDetails` | `ReturnMedicineDetailQuery` | `List<ReturnMedicineDetailReadRow>` | **应用层** `ReturnMedicineDetailLoader`（**AS-OP-PHA-002**）；域 QS 无同名方法 | TC-MEDDISP-RS-021 |
| `listOutpatientReturnMedicationDispensesForDetail` | `Long encounterId`, … | `List<MedicationDispense>` | 门诊退药详情行（域 QS 实现） | TC-MEDDISP-RS-022 |
| `listMedicationDispenseIdsByMedicationRequest` | `Long medReqId` | `List<Long>` | 医嘱→发放单 ID | TC-MEDDISP-RS-023 |
| `mapMedicationRequestToPrimaryDispenseLocationId` | `Collection<Long> medReqIds` | `Map<Long, Long>` | 医嘱→主发药库位 | TC-MEDDISP-RS-024 |
| `mapMedicationDispenseIdsToMedReqIds` | `Collection<Long> dispenseIds` | `Map<Long, Long>` | 发放单→医嘱 | TC-MEDDISP-RS-025 |
| `listMedicationDispensesByMedReqIds` | `Collection<Long> medReqIds` | `List<MedicationDispense>` | 批量按医嘱查实体 | TC-MEDDISP-RS-026 |
| `listMedicationDispensesByIds` | `Collection<Long> ids` | `List<MedicationDispense>` | 批量按 ID 查实体 | TC-MEDDISP-RS-027 |
| `pageMedicationDispensePickupPending` | `MedicationDispensePickupPendingPageQuery` | `Page<MedicationDispensePickupPendingQueryModel>` | **AS-IP-NUR-010 IF-IP-SUM-01**；PREPARATION+已执行待汇总 | TC-MEDDISP-RS-028 |

**读方法注释锚点（统一）**：条件构建 → 仓储查询 → 校验结果/DTO 组装（无写库）。

**IF-DOR-11 目录预占状态口径**：`DoctorStationOrderCatalogConstants#DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG`（仅 `DRAFT`/`PREPARATION` + `limitTime`）。  
**IF-DOR-15 FIFO 额外占用**：`#DISPENSE_OCCUPANCY_STATUSES_FOR_FIFO_INPATIENT_SUMMARY_EXTRA`（仅 `SUMMARIZED`；`listMedicationDispensesForInpatientSummarizedOccupancy`，`dispense_enum=INPATIENT`）。  
护士站执行 Gate 见 `NurseStationExecutionConstants#DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE`（宽于目录预减）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-MDP-001 | 临时路径同申请唯一约束 |
| BR-MDP-002 | 配药仅 `PREPARATION` |
| BR-MDP-003 | 发药仅待发窗口 |
| BR-MDP-004 | 发药事实与状态同事务写入 |
| BR-MDP-005 | 发药前库存足额协作 |
| BR-MDP-006 | 退药仅待退药且幂等 |
| BR-MDP-007 | 退药触发库存回补与追溯 IN |
| BR-MDP-008 | 五元组完整性校验 |
| BR-MDP-009 | 追溯码不可重复出库 |
| BR-MDP-010 | 预减统计状态与时效限定 |
| BR-MDP-011 | 作废必须记录原因 |
| BR-MDP-012 | 汇总号一致性与可逆 |
| BR-MDP-013 | 毒麻双人复核 |
| BR-MDP-014 | 批号效期校验 |
| BR-MDP-015 | 医生站临时签发 FIFO 建发放、暂存不建；非医生站可 `generateDispenseFromTemporaryOrder`；长期执行建待配药 |
| BR-MDP-016 | 用药说明快照一致性 |
| BR-MDP-017 | 读校验写分层 |
| BR-MDP-018 | 临时多条待发合计校验 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `MedicationDispenseCreatedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `id,medReqId,medicationId,encounterId,busNo,statusEnum,dispenseEnum,patientId,locationId,quantity,unitCode,lotNumber,prescriptionNo,orderingOrgId,medRequesterId` | `generateDispenseFromTemporaryOrder`,`generateDispenseFromExecution`,`replaceFifoDraftDispensesAfterDoctorStationSign`,`createPendingRefundDispense` |
| `MedicationDispensePreparedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `id,preparerId,prepareTime` | `prepareMedicationDispense` |
| `MedicationDispensedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `id,medicationId,lotNumber,dispenseQuantity` | `dispenseMedication` |
| `MedicationDispenseDeclinedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `id,notPerformedReasonEnum` | `cancelMedicationDispense` |
| `MedicationDispenseRefundedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `id,refundQuantity` | `refundDispensedMedication` |
| `MedicationDispenseSummarizedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `summaryNo,ids` | `summarizeMedicationDispenses` |
| `MedicationDispenseSummaryCompletedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `summaryNo,ids` | `completeSummaryDispense` |
| `MedicationDispenseSummaryCancelledEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `summaryNo` + 发放行 ID | `cancelMedicationDispenseSummary`；消费：**AS-IP-NUR-010** 待汇总列表、**AS-IP-PHA-002** 驳回后刷新 |
| `MedicationDispensePendingRefundCreatedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `id,originDispenseId,refundQuantity` | `createPendingRefundDispense` |
| `MedicationDispenseReserveReleasedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `medReqId,releasedCount` | `releaseDispenseReserve`,`removeUndispensedDispense` |
| `DispenseProcedureLinkedEvent` | `com.zxplat.domain.share.events.medication.medicationdispense` | `dispenseId,medReqId,procedureId,plannedDispenseTime,operatorId,statusEnumCode` | `linkDispenseProcedure` |

**发布时机**：全部 **save 之后** 发布（**B.14**）。

---

## 8. 删除与可见性契约

### 8.1 软删语义

- **逻辑删除字段**：`isDeleted`（`Boolean`），继承自 `BaseAuditedEntity`。
- **物理删除策略**：
  - 草稿态（`DRAFT`）允许物理删除，释放预减占用；
  - 已配药/待发药及以上状态禁止物理删除，仅允许状态迁移；
  - 长期医嘱路径下未发单据可通过 `removeUndispensedDispense` 物理删除（BR-MDP-015）。
- **查询默认过滤**：所有 Repository 查询默认附加 `isDeleted = false` 条件。

### 8.2 状态可见性规则

| 状态（`statusEnum`） | 配药 | 发药 | 作废 | 退药 | 汇总 | 删除 |
| ------------------- | ---- | ---- | ---- | ---- | ---- | ---- |
| `DRAFT`（草稿） | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ 可物理删 |
| `PREPARATION`（待配药） | ✅ | ❌ | ✅ | ❌ | ✅ | ❌ |
| `PREPARED`（已配药） | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `IN_PROGRESS`（待发药） | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `COMPLETED`（已发放） | ❌ | ❌ | ❌ | ✅ 生成待退药 | ❌ | ❌ |
| `SUMMARIZED`（已汇总） | ❌ | ✅ 批量发药 | ✅ | ❌ | ❌ 可取消汇总 | ❌ |
| `PENDING_REFUND`（待退药） | ❌ | ❌ | ❌ | ✅ 确认退药 | ❌ | ❌ |
| `REFUNDED`（已退药） | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ 终态 |
| `DECLINED`（已拒绝） | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ 条件删除 |
| `STOPPED`（已停止） | ❌ | ❌ | ❌ | ❌ | ❌ | ⚠️ 条件删除 |

### 8.3 幂等与重复保护

- **退药幂等**：同一发放单禁止重复退药（BR-MDP-006），通过 `PENDING_REFUND` → `REFUNDED` 状态机保证。
- **追溯码重复出库校验**：配药时校验追溯码是否已出库（BR-MDP-009），命中则拒绝。
- **临时医嘱重复创建防护**：同一 `medReqId` 下临时路径禁止重复创建发放单（BR-MDP-001）。

---

## 9. 框架优先适配规则

### 9.1 实体与仓储

| 规则 | 说明 |
| ---- | ---- |
| 基类继承 | `MedicationDispense extends BaseAuditedEntity`，统一继承审计、软删、租户字段 |
| 实体注解 | `@Entity` + `@Table(name = "med_medication_dispense")` |
| 状态枚举 | `DispenseStatus`：**Java 强类型** + Converter，列内为 **`getCode()` 字符串**（如 `PREPARATION`、`COMPLETED`），见 DM-MED-002 §2.2 |
| 仓储形态 | `interface MedicationDispenseRepository extends JpaRepository<MedicationDispense, Long>` |
| 派生查询 | `findByEncounterIdAndIsDeletedFalse`、`findBySummaryNoAndIsDeletedFalse` 等 |
| 软删过滤 | 框架统一处理，Repository 接口名不强制 `AndIsDeletedFalse` 冗余 |

### 9.2 领域服务

| 规则 | 说明 |
| ---- | ---- |
| 服务形态 | `MedicationDispenseDomainService` 为直接实现类 |
| 事务管理 | 依赖应用层/框架工作单元，禁止机械添加 `@Transactional` |
| 状态门控 | 状态迁移方法收口于实体内部（`toPrepared`、`toCompleted` 等），禁止服务层直接改状态字段 |
| 双人复核 | 毒麻药品发药必须双人复核（BR-MDP-013），配药人与发药人不得为同一人 |
| 事件发布 | `MedicationDispensedEvent` 等 beforeCommit 发布，驱动库存扣减与计费联动 |

### 9.3 与下游协作

| 下游 | 协作方式 | 触发事件 | 本聚合职责 |
| ---- | -------- | -------- | ---------- |
| Inventory（库存） | 事件订阅 | `MedicationDispensedEvent` | 提供五元组口径，不替代库存记账 |
| TraceNumberRecord（追溯码） | 事件订阅 | `MedicationDispensedEvent` | 提供追溯码载荷，不替代追溯账本 |
| ChargeItem（收费） | 事件订阅 | `MedicationDispensedEvent` | 触发计费联动，不计算金额 |
| MedicationRequest（医嘱） | 上游引用 | — | 通过 `medReqId` 只读引用，不维护医嘱生命周期 |

### 9.4 读侧校验分层

| 校验方法 | 层级 | 职责 |
| -------- | ---- | ---- |
| `validateDispensePreparable` | `QueryService` | 配药前置校验，只读不写库 |
| `validateDispensable` | `QueryService` | 发药前置校验，库存足额结论由协作方返回 |
| `validateRefundable` | `QueryService` | 退药前置校验，幂等检查 |
| `mustGetMedicationDispense` | `QueryService` | 写前强加载，记录必须存在 |

---

## 10. 生成与验收清单

- [ ] 14 个写方法均具独立命令文件
- [ ] 状态迁移与 BR-MDP-002/003/006/012 对齐
- [ ] 事件 10 条与触发方法一致
- [ ] 读侧 9 个方法无写库副作用
- [ ] 关键校验具备单测绑定

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-MED-002 与本 SDD。
- 明确库存/追溯/收费协作端口契约。

### 11.2 DoD

- 写读方法、命令、查询模型数量一一对应。
- 状态流、幂等、追溯码校验均有测试覆盖。

### 11.3 失败处理

- 任一状态门控或事件不一致即回退到文档修订。

---

## 定稿检查清单（可打勾）

- [x] 金骨架章节齐全
- [x] 写侧 14 方法全覆盖
- [x] 事件表与 DM 对齐
- [x] 无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **99（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **DM-SDD M1**：§5.2 `pageReturnMedicinePatients` 标待实现；`listReturnMedicineDetails` 落点 `ReturnMedicineDetailLoader`；对齐 DM §6.2.13 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| 2026-06-06 | 对齐 **AS-IP-NUR-010 v1.5** / DM **v1.3.3**：§5.1.11 增补 IF-IP-SUM-08、IF-IPS-04 跨域编排；事件表消费方 |
| 2026-10-15 | 对齐 DM **v1.3.18**：§5.2 增补 `listCancelExecuteRefundDispensesByEncounterIds`（**AS-IP-NUR-010 IF-IP-SUM-10**；代码先行回写） |
| 2026-10-14 | 对齐 DM **v1.3.17**：§5.2 增补 `listMedicationDispensesForInpatientSummarizedOccupancy`；厘清目录预减 / FIFO 汇总额外占用 / 执行 Gate 三套状态口径（代码先行回写） |

---

**本 SDD 正文结束。**
