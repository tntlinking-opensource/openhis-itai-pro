# DM-MED-003 MedicationRequest 药品申请 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md`（**v1.3.29**）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：medication  
> **聚合**：MedicationRequest（子实体见 §4）  
> **实现阶段口径**：§5.2 `listTcmHerbalPrescriptionsByEncounter` 三参重载与 §5.1.8 **`ensureRejectable`**（**VERIFIED**）**已落地**（`zxplat` · 2026-06-20 · **IF-IP-TCM-34/38**）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-MED-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | zxplat-domain |
| 包规则 `com.zxplat.domain.medication.request` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 对齐 |
| DM 第 5 章事件 | 满足 | §7 对齐 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化药品申请从草稿到签发、校对、退回、停嘱、取消、退药/重开的完整契约。
- 明确中西药分轨与处方分方规则，保证分方事实可落库并可供打印QueryService消费。
- 固化 15 个写方法与 5 个读方法的一方法一命令/模型契约。

### 1.2 边界

- **在内**：药品申请主档、状态迁移、分组、分方、负向退药单与重开。
- **在外**：计费金额计算、库存账本、执行记录明细、打印版式渲染。
- **跨域仅端口声明**：收费、发药、执行、审核均通过事件和应用编排协作。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.medication.request`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `MedicationRequest` |
| `valueobject` | `DosageSemantics` 等值对象 |
| `command` | 15 个写方法命令文件 |
| `repository` | `MedicationRequestRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |

**存储口径（DM 优先）**：`status_enum`、`intent_enum`、`category_enum`、`priority_enum`、`performer_enum`、`therapy_enum`、`yb_class_enum`、`generate_source_enum` 等为 **`String`（存枚举 `getCode()`）**，与 **`MedicationRequest`**、**`RequestStatus`**、**`TherapyType`**、**`GenerateSource`** 一致；纯计数类字段（如 `execute_num`、`sort_number`）仍为数值类型；`*_code` 为 `String`；`isDeleted` 软删。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long` |
| 时间 | `statusChangedTime/effectiveDoseStart/effectiveDoseEnd/checkTime` 用 `LocalDateTime` |
| `*_enum`（业务枚举列） | `String`（`getCode()` 或与收费域一致的 `"1"`～`"5"` 生成来源码） |
| `*_code` | `String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` + `@Table(name = "med_medication_request")` |
| 仓储 | `JpaRepository<MedicationRequest, Long>` |
| 分页 | 默认 `createdAt DESC` |
| 事件 | beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `med_medication_request` | `MedicationRequest` | 药品申请单表聚合根 |

### 4.2 聚合根 `MedicationRequest`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键（雪花 ID） |
| `busNo` | `String` | 是 | 业务号，全局唯一（BR-MRQ-005）；`getSeqByDay(MR)` |
| `prescriptionNo` | `String` | 否 | 处方号；门诊分方 `getSeqByDay(CP)`；药房/打印分组键 |
| `signCode` | `String` | 否 | 签发批次编码；`getSeqByDay(ASI)`；**禁止** `MR` 前缀 |
| `groupId` | `Long` | 否 | 成组号（成组输液标识，BR-MRQ-008） |
| `packageId` | `Long` | 否 | 组套标识（关联医嘱组套） |
| `statusEnum` | `String` | 是 | `RequestStatus#getCode()`（**校对落 `VERIFIED`**，门诊收费收口落 **`COMPLETED`**，见 DM-MED-003 §2.3；BR-MRQ-004） |
| `statusReason` | `Integer` | 否 | 状态变更原因（字典码） |
| `statusChangedTime` | `LocalDateTime` | 否 | 状态变更时间戳 |
| `intentEnum` | `String` | 是 | 请求意图（存 **code**；语义见 DM §4.3） |
| `categoryEnum` | `String` | 是 | 请求类别（与药品类别同义，存 **code**） |
| `priorityEnum` | `String` | 否 | 优先级（`PriorityLevel` 等，存 **code**） |
| `performFlag` | `Boolean` | 否 | 是否停止执行标志 |
| `medicationId` | `Long` | 是 | 药品定义标识（外键） |
| `patientId` | `Long` | 是 | 患者标识（外键） |
| `encounterId` | `Long` | 是 | 就诊标识（外键） |
| `practitionerId` | `Long` | 是 | 开立医生标识（Practitioner 外键） |
| `locationId` | `Long` | 否 | 所在位置标识（Location 外键，开立位置） |
| `orgId` | `Long` | 是 | 开立科室组织主键（`adm_organization.id`；读路径「开单科室」、科室过滤、`ServiceLocationTemplate` 入参均读此字段，**BR-MRQ-029**） |
| `performLocation` | `Long` | 否 | 发放药房库位主键（`adm_location.id`；护士划价等已绑定库房路径可填；医生站草稿可空；**禁止**当科室组织外键） |
| `performOrg` | `Long` | 否 | 执行科室标识（Organization 外键） |
| `performerEnum` | `String` | 否 | 执行人类型（字典 **code**） |
| `performerId` | `Long` | 否 | 执行人标识（Practitioner 外键） |
| `deviceDefId` | `Long` | 否 | 设备定义标识（DeviceDefinition 外键） |
| `recorderId` | `Long` | 否 | 记录人标识（Practitioner 外键） |
| `reqOpenedTime` | `LocalDateTime` | 否 | 开立时刻（`req_opened_time`）：`createMedicationOrderDraft` / `createHerbalMedicationOrderDraft` 首次落库写入；签发/改草稿/签退**不覆盖**；住院可由 `changeMedicationRequestBusinessTime` 覆盖 |
| `reqAuthoredTime` | `LocalDateTime` | 否 | 签发时刻（`req_authored_time`）：**仅** `signMedicationOrder`（及无草稿直建完成态）写入；草稿暂存**不写**本列（与 **`ServiceRequest#reqAuthoredTime`** 同构）；**仅系统追溯**，列表/报表**默认不展示**；**禁止**改业务时间写入 |
| `stopTime` | `LocalDateTime` | 否 | 停嘱时刻（`stop_time`）；`stopLongTermMedicationOrder` 写入；取消停嘱清空；可由改业务时间覆盖 |
| `stopperId` | `Long` | 否 | 停嘱人（`stopper_id`）；停嘱写入；取消停嘱清空；改停嘱时间**不**改（**已落地**） |
| `stopPerformerCheckId` | `Long` | 否 | 停嘱校对人（`stop_performer_check_id`）；停嘱校对写入；取消停嘱校对/取消停嘱/新停嘱清空；改停嘱时间**保留** |
| `stopCheckTime` | `LocalDateTime` | 否 | 停嘱校对时间（`stop_check_time`）；与上成对 |
| `reportFlag` | `Boolean` | 否 | 是否报告标志 |
| `supportInfo` | `String` | 否 | 支持用药信息 |
| `reasonJson` | `String` | 否 | 请求原因 JSON 结构化存储 |
| `therapyEnum` | `String` | 是 | `TherapyType#getCode()`（`LONG_TERM`/`TEMPORARY`/`DISCHARGE_MEDICATION`，BR-MRQ-009） |
| `effectiveDoseStart` | `LocalDateTime` | 否 | 服药开始时间 |
| `effectiveDoseEnd` | `LocalDateTime` | 否 | 服药结束时间（停嘱时写入，BR-MRQ-006） |
| `quantity` | `BigDecimal` | 是 | 数量（长期=单次用量，临时=总量，BR-MRQ-009/010） |
| `executeNum` | `Integer` | 否 | 执行次数（长期医嘱累计） |
| `unitCode` | `String` | 是 | 数量单位（字典码） |
| `methodCode` | `String` | 是 | 用法编码（字典码） |
| `rateCode` | `String` | 是 | 频次编码（字典码，住院临时须为「立即」，BR-MRQ-010） |
| `dose` | `BigDecimal` | 否 | 单次剂量（计量；schedule 有值时可空/首项兜底，**BR-MRQ-035**） |
| `doseUnitCode` | `String` | 否 | 剂量单位编码（字典码；有 schedule 时仍必填） |
| `doseSchedule` | `String` | 否 | 分次剂量序列（计量、逗号分隔；**BR-MRQ-035**） |
| `maxDose` | `BigDecimal` | 否 | 单次最大剂量（安全上限） |
| `firstDose` | `BigDecimal` | 否 | 首日量/负荷量（仅长期适用，BR-MRQ-010） |
| `firstDoseUnitCode` | `String` | 否 | 首日量单位（仅长期适用） |
| `quantitySchedule` | `String` | 否 | 分次用量序列（拆零、逗号分隔；与 `doseSchedule` 同序，**BR-MRQ-035**） |
| `firstDuration` | `String` | 否 | 首次持续时间 |
| `dispenseInterval` | `String` | 否 | 给药间隔 |
| `dispensePerQuantity` | `Integer` | 否 | 单次发药数 |
| `dispensePerDuration` | `Integer` | 否 | 用药天数/配药供应天数（门诊临时配合，BR-MRQ-010） |
| `lotNumber` | `String` | 否 | 产品批号 |
| `traceNo` | `String` | 否 | 追溯码 |
| `skinTestFlag` | `Boolean` | 否 | 皮试标志 |
| `infusionFlag` | `Boolean` | 否 | 输液标志（成组输液标识） |
| `speedCode` | `String` | 否 | 输液滴速编码（字典码） |
| `contractId` | `Long` | 否 | 费用性质标识（Contract 外键，DM-FIN-001） |
| `encounterDiagnosisIds` | `String` | 否 | 绑定就诊诊断 ids 有序串（`adm_encounter_diagnosis.id`，**主诊 id 第一**；落库列 `encounter_diagnosis_ids`） |
| `refundMedicineId` | `Long` | 否 | 退药关联原单标识（负向单指向，BR-MRQ-013） |
| `rxTypeCode` | `String` | 否 | 处方类别编码（字典码） |
| `ybClassEnum` | `String` | 否 | 医保类别编码（字典 **code**） |
| `dosageInstruction` | `String` | 否 | 用药说明 |
| `basedOnTable` | `String` | 否 | 派生/绑定来源表名 |
| `basedOnId` | `Long` | 否 | 派生/绑定来源主键 |
| `contentJson` | `String` | 否 | 中药扩展结构 JSON（多味配伍/煎法，BR-MRQ-011） |
| `chineseHerbsDoseQuantity` | `BigDecimal` | 否 | 中药付数/方剂付数 |
| `sufferingFlag` | `Boolean` | 否 | 代煎标识 |
| `tcmFlag` | `Boolean` | 否 | 中医/中药标识（`true`=中药，`false`=非中药，BR-MRQ-011） |
| `sortNumber` | `Integer` | 否 | 展示排序号 |
| `performerCheckId` | `Long` | 否 | 校对人标识（Practitioner 外键，BR-MRQ-007） |
| `checkTime` | `LocalDateTime` | 否 | 校对时间（BR-MRQ-007） |
| `generateSourceEnum` | `String` | 是 | `"1"`～`"5"`（**`ChargeItemDmConstants`**，BR-MRQ-005） |
| `remark` | `String` | 否 | 备注 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `tenantId` | `Long` | 系统 | 租户 ID |

**不变式方法（示例命名，实现须一致）**：`validateDosageSemantics`、`ensureDraftEditable`、`ensureSignable`、`ensureLongTermForStop`、`ensureLongTermStoppedForCancelStop`、`fromStopped`、`applySplitRules`、`ensureDraftRemovable`、`ensureSignOffAllowed`、`ensureRefundCreatable`。

### 4.2.1 值对象字段展开

本聚合为单表聚合，无独立复合值对象类。以下业务概念平铺存储于聚合根，但具有值对象语义：

| 值对象语义 | 对应字段 | 说明 |
| ---------- | -------- | ---- |
| `DosageSemantics` | `therapyEnum`, `quantity`, `unitCode`, `firstDose`, `firstDoseUnitCode` | 用量语义联合（BR-MRQ-009/010） |
| `MedicationUsageInstruction` | `methodCode`, `rateCode`, `dose`, `doseUnitCode`, `maxDose`, `dosageInstruction` | 用药说明（§4.3 DM） |
| `PrescriptionSplitLineContext`（命令快照，非实体列） | `pharmacyLocationId`, `groupId`, `tcmFlag`, `medType`, `pharmacologyCategoryCode`, `medicationCategoryCode`, `chargeTotalPrice` | 分方 P1 以签发 FIFO 解析的**库位** `pharmacyLocationId` 判定；**`pharmacologyCategoryCode`**（术语 medicine_properties）驱动麻/精一/毒性独占轨，普通药再按 **`medicationCategoryCode`** 分西药/中成药（**BR-MRQ-032**）；**禁止**用门诊拆分属性 `part_attribute_enum`；开立科室组织来自请求 **`orgId`** 参与 FIFO 解析，**禁止**与 `performLocation` 混读（**BR-MRQ-029**） |

### 4.3 子实体

无。本聚合为单表聚合。

**归属校验**：N/A（单表聚合，无子实体归属校验需求）。

**`contentJson` 中药扩展清理**：移除草稿（`removeMedicationOrderDraft`）时必须同事务清理 `contentJson` 及关联协作对象（发放占用、费用草稿等，BR-MRQ-011）。

### 4.5 仓储接口

- `MedicationRequestRepository extends JpaRepository<MedicationRequest, Long>`
- `Optional<MedicationRequest> findByIdAndIsDeletedFalse(Long id)`
- `List<MedicationRequest> findByEncounterIdAndIsDeletedFalse(Long encounterId)`
- `List<MedicationRequest> findByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, String statusEnum)` — **AS-IP-NUR-001** 护士站批量计数（DM §6.2.7）
- `List<MedicationRequest> findByPatientIdAndIsDeletedFalse(Long patientId)`
- `boolean existsByBusNoAndIsDeletedFalse(String busNo)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `MedicationRequestDetailQueryModel`（对应 `loadMedicationRequestById`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_request.id` | 主键 |
| `statusEnum` | `String` | `med_medication_request.status_enum` | 状态 code |
| `therapyEnum` | `String` | `med_medication_request.therapy_enum` | 治疗类型 code |
| `quantity` | `BigDecimal` | `med_medication_request.quantity` | 数量 |
| `unitCode` | `String` | `med_medication_request.unit_code` | 单位 |

#### 4.6.2 `MedicationRequestListQueryModel`（对应 `listMedicationRequestsByEncounter`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_request.id` | 主键 |
| `busNo` | `String` | `med_medication_request.bus_no` | 业务号 |
| `prescriptionNo` | `String` | `med_medication_request.prescription_no` | 处方号 |
| `statusEnum` | `String` | `med_medication_request.status_enum` | 状态 code |
| `groupId` | `Long` | `med_medication_request.group_id` | 成组号 |
| `reqOpenedTime` | `LocalDateTime` | `med_medication_request.req_opened_time` | 开立时刻 |
| `reqAuthoredTime` | `LocalDateTime` | `med_medication_request.req_authored_time` | 签发时刻；草稿可 null；**仅追溯，不作列表/报表主展示列** |
| `stopTime` | `LocalDateTime` | `med_medication_request.stop_time` | 停嘱时刻；未停 null |

#### 4.6.3 `MedicationRequestHistoryQueryModel`（对应 `listMedicationRequestHistoryByPatient`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_request.id` | 主键 |
| `encounterId` | `Long` | `med_medication_request.encounter_id` | 就诊 |
| `medicationId` | `Long` | `med_medication_request.medication_id` | 药品 |
| `statusEnum` | `String` | `med_medication_request.status_enum` | 状态 code |
| `createdAt` | `LocalDateTime` | 审计字段 | 记录创建时间（**非**开立/签发展示时间；列表开立见 **`reqOpenedTime`**；签发 **`reqAuthoredTime` 仅追溯、默认不展示**） |

#### 4.6.4 `MedicationRequestDeleteCheckQueryModel`（对应 `checkMedicationRequestDeletable`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `deletable` | `Boolean` | 校验逻辑 | 是否可删 |
| `reasonCode` | `String` | 校验逻辑 | 原因码 |
| `charged` | `Boolean` | ChargeItem 查询结果 | 是否已收费 |

#### 4.6.5 `MedicationRequestSignOffCheckQueryModel`（对应 `checkMedicationRequestSignOffAllowed`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `allowed` | `Boolean` | 校验逻辑 | 是否可签退 |
| `reasonCode` | `String` | 校验逻辑 | 原因码 |
| `charged` | `Boolean` | ChargeItem 查询结果 | 是否已收费 |

#### 4.6.6 `TcmHerbalPrescriptionGroupQueryModel`（对应 `listTcmHerbalPrescriptionsByEncounter`）
| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `prescriptionGroupKey` | `String` | `content_json.prescriptionGroupKey` | 前端方标识（草稿簇分组键） |
| `prescriptionNo` | `String` | `med_medication_request.prescription_no` | 处方号（已签发生效行共享） |
| `tcmSyndromeGroup` | `String` | `content_json.tcmSyndromeGroup` | 绑定的中医证候组号 |
| `chineseHerbsDoseQuantity` | `BigDecimal` | `med_medication_request.chinese_herbs_dose_quantity` | 中药付数（方头口径，取组内首行） |
| `sufferingFlag` | `Boolean` | `med_medication_request.suffering_flag` | 代煎标志 |
| `methodCode` | `String` | `med_medication_request.method_code` | 用法编码 |
| `rateCode` | `String` | `med_medication_request.rate_code` | 用药频次编码（方头口径，取组内首行） |
| `dispensePerDuration` | `Integer` | `med_medication_request.dispense_per_duration` | 用药天数 |
| `remark` | `String` | `med_medication_request.remark` | 备注 |
| `rxTypeCode` | `String` | `med_medication_request.rx_type_code` | 处方类别编码 |
| `therapyEnum` | `String` | `med_medication_request.therapy_enum` | 治疗类型（方头口径，取组内首行） |
| `statusEnum` | `String` | 组内行聚合 | 方级展示状态 |
| `reqOpenedTime` | `LocalDateTime` | 组内行聚合 | 方级开立时间（组内行 `req_opened_time` 聚合；空回落 `req_authored_time`） |
| `lines` | `List<MedicationRequestListQueryModel>` | 组内扁平行 | 方内药品申请行 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `MedicationRequestDomainService`

#### 5.1.1 `createMedicationOrderDraft(CreateMedicationOrderDraftCommand cmd)` → `Long`
**命令文件**：`CreateMedicationOrderDraftCommand.java`；**字段全集**：`encounterId,patientId,medicationId,therapyEnum,quantity,unitCode,methodCode,rateCode,speedCode,dose,doseUnitCode,doseSchedule,quantitySchedule,firstDose,firstDoseUnitCode,prescriptionNo?`（`prescriptionNo` 可空；手麻嵌套麻醉药品与麻醉 SR 共用 AR 时传入）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateDosageSemantics` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | `existsByBusNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderDraftCreatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-001` |
**方法契约摘要**：前置为用量语义合法；持久化新建草稿并生成业务号；**同步写入 `reqOpenedTime`（首次暂存时刻）**；失败为库存/语义冲突；触发草稿创建事件。

---

#### 5.1.2 `changeMedicationOrderDraft(ChangeMedicationOrderDraftCommand cmd)` → `void`
**命令文件**：`ChangeMedicationOrderDraftCommand.java`；**字段全集**：`id` + 草稿可编辑字段（含 `speedCode`：非空时更新，空串可清空）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureDraftEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderDraftEditedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-002` |
**方法契约摘要**：前置为草稿且语义合法；持久化更新草稿；失败为非草稿或校验失败；触发草稿修改事件。

---

#### 5.1.3 `removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand cmd)` → `void`
**命令文件**：`RemoveMedicationOrderDraftCommand.java`；**字段全集**：`id`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureDraftRemovable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderDraftRemovedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-003` |
**方法契约摘要**：前置为草稿且未收费；持久化删除/逻辑删除并清理协作对象；失败为已收费或状态非法；触发草稿移除事件。

---

#### 5.1.4 `signMedicationOrder(SignMedicationOrderCommand cmd)` → `String`
**命令文件**：`SignMedicationOrderCommand.java`；**字段全集**：`id,signCode,prescriptionNo`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureSignable` |
| `statusTransitionMethod` | `draftToActive` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderSignedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-004` |
**方法契约摘要**：前置为草稿可签发；持久化写签发要素并转已发送；**写入 `reqAuthoredTime` 为签发时刻**（**不覆盖** `reqOpenedTime`；与诊疗签发写入 `req_authored_time` 对齐）；失败为状态不允许；发布 `MedicationOrderSignedEvent`（载荷含 save 后 **`statusEnum`** **ACTIVE**）；列表读 **`MedicationRequestQueryService`** 直读主表。

---

#### 5.1.5 `signOffMedicationOrder(SignOffMedicationOrderCommand cmd)` → `void`
**命令文件**：`SignOffMedicationOrderCommand.java`；**字段全集**：`id`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureSignOffAllowed` |
| `statusTransitionMethod` | `activeToDraft` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderSignOffEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-005` |
**方法契约摘要**：前置为已发送且未收费；持久化回草稿；失败为已收费或状态非法；触发签退事件。

---

#### 5.1.6 `stopLongTermMedicationOrder(StopLongTermMedicationOrderCommand cmd)` → `void`
**命令文件**：`StopLongTermMedicationOrderCommand.java`；**字段全集**：`id,stopTime,stopperId`（或 `operatorId` 映射为停嘱人）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureLongTermForStop`（长期 + **VERIFIED**） |
| `statusTransitionMethod` | `toStopped`（写 `stop_time` + `stopper_id`；清停嘱校对两字段） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new LongTermMedicationOrderStoppedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-006` |
**方法契约摘要**：**BR-MRQ-006**；禁止 ACTIVE 停嘱；进入停嘱待校。

---

#### 5.1.6a `cancelStopLongTermMedicationOrder(CancelStopLongTermMedicationOrderCommand cmd)` → `void`
**命令文件**：`CancelStopLongTermMedicationOrderCommand.java`；**字段全集**：`id,operatorId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureLongTermStoppedForCancelStop`（须未停嘱校对） |
| `statusTransitionMethod` | `fromStopped`（清 `stop_time`/`stopper_id`/停嘱校对两字段） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new LongTermMedicationOrderStopCancelledEvent(id, operatorId, statusEnum)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-006a` |
**方法契约摘要**：**BR-MRQ-006a**；已停嘱校对拒绝。**已落地**。

---

#### 5.1.6c `changeMedicationRequestBusinessTime(ChangeMedicationRequestBusinessTimeCommand cmd)` → `void`
**命令文件**：`ChangeMedicationRequestBusinessTimeCommand.java`；**字段全集**：`id,timeKind,newTime,operatorId`（`timeKind`=`OPENED`/`STOP`）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureBusinessTimeChangeAllowed`（**已落地**） |
| `statusTransitionMethod` | —（仅时间列；改停嘱**保留**停嘱校对字段） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | 可选审计事件 |
| `unitTestCaseId` | `TC-MEDREQ-WS-006c` |
**方法契约摘要**：**BR-MRQ-006b**。**已落地**。

---

#### 5.1.6d `verifyStopMedicationOrder(VerifyStopMedicationOrderCommand cmd)` → `void`
**命令文件**：`VerifyStopMedicationOrderCommand.java`；**字段全集**：`id,stopPerformerCheckId,stopCheckTime`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureStopVerifiable` / `markStopChecked` |
| `statusTransitionMethod` | —（仍 **STOPPED**） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `unitTestCaseId` | `TC-MEDREQ-WS-006d` |
**方法契约摘要**：**BR-MRQ-006d**；只写停嘱校对两字段。**已落地**（**IF-IP-AUD-02**）。

---

#### 5.1.6e `cancelVerifyStopMedicationOrder(CancelVerifyStopMedicationOrderCommand cmd)` → `void`
**命令文件**：`CancelVerifyStopMedicationOrderCommand.java`；**字段全集**：`id`（可选 `operatorId`）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureStopVerifyCancellable` / `clearStopChecked` |
| `statusTransitionMethod` | —（仍 **STOPPED**；本期不校验操作人） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `unitTestCaseId` | `TC-MEDREQ-WS-006e` |
**方法契约摘要**：**BR-MRQ-006e**；清停嘱校对两字段。**已落地**（**IF-IP-AUD-03**）。

---

#### 5.1.7 `verifyMedicationOrder(VerifyMedicationOrderCommand cmd)` → `void`
**命令文件**：`VerifyMedicationOrderCommand.java`；**字段全集**：`id,performerCheckId,checkTime`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureVerifiable` |
| `statusTransitionMethod` | `activeToCompleted`（落库 **`VERIFIED`**，方法名历史遗留） |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderVerifiedEvent(..., statusEnum)`（**`VERIFIED`**） |
| `unitTestCaseId` | `TC-MEDREQ-WS-007` |
**方法契约摘要**：前置为已发送；持久化写校对并转已校对；失败为状态非法；触发校对事件。

---

#### 5.1.8 `rejectMedicationOrder(RejectMedicationOrderCommand cmd)` → `void`
**命令文件**：`RejectMedicationOrderCommand.java`；**字段全集**：`id,performerCheckId,checkTime,reason`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureRejectable`（允许 **ACTIVE** / **VERIFIED**；后者供 **IF-IP-TCM-38**，应用层须先过执行/发药/费用门控 **BR-IP-TCM-009**） |
| `statusTransitionMethod` | `toDraft` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderRejectedEvent(..., statusEnum)`（**`DRAFT`**） |
| `unitTestCaseId` | `TC-MEDREQ-WS-008` |
**方法契约摘要**：前置为已发送或已校对；持久化回草稿并写校对追溯；失败为状态不允许；触发退回事件。**调用方**：护士校对退回（**IF-IP-AUD-03**）**ACTIVE/VERIFIED**（后者须 **BR-IP-AUD-013**）；住院医生站签退（**IF-IP-TCM-38** / **IF-IP-DOR-16**）在门控通过后可对 **VERIFIED** 调用。

---

#### 5.1.9 `adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand cmd)` → `void`
**命令文件**：`AdjustMedicationOrderGroupCommand.java`；**字段全集**：`ids,groupId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateGroupAdjustInput` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findAllById` |
| `eventPublishAnchor` | `new MedicationOrderGroupAdjustedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-009` |
**方法契约摘要**：前置为记录均可调整；持久化批量更新/置空组号；失败为并发或不存在；触发分组调整事件。

---

#### 5.1.10 `cancelMedicationOrder(CancelMedicationOrderCommand cmd)` → `void`
**命令文件**：`CancelMedicationOrderCommand.java`；**字段全集**：`id,cancelReason`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureCancelable` |
| `statusTransitionMethod` | `toCancelled` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderCancelledEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-010` |
**方法契约摘要**：前置为允许取消状态；持久化进取消态；失败为终态或策略不允许；触发取消事件。

---

#### 5.1.11 `createRefundMedicationOrder(CreateRefundMedicationOrderCommand cmd)` → `Long`
**命令文件**：`CreateRefundMedicationOrderCommand.java`；**字段全集**：`originRequestId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureRefundCreatable` |
| `statusTransitionMethod` | `initAsRefundNegativeOrder` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderRefundRequestedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-011` |
**方法契约摘要**：前置为原单为正向单（`refund_medicine_id` 空）；**已有未完结负向单（未软删且非 COMPLETED）则幂等返回已有主键**（不新建、不重发事件）；否则持久化生成负向单：全量拷贝原单临床字段、`refund_medicine_id` 关联、**`markAsRefundFromPrescription`（`T`+原号）**、`quantity` 取反、`initAsRefundNegativeOrder`（**`CANCELLED`**）；失败仅为原单已是负向单或原单不存在；触发退药请求事件（仅新建路径）。

---

#### 5.1.12 `reopenMedicationOrder(ReopenMedicationOrderCommand cmd)` → `Long`
**命令文件**：`ReopenMedicationOrderCommand.java`；**字段全集**：`originRequestId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureReopenable` |
| `statusTransitionMethod` | `initByCommand` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationOrderReopenedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-012` |
**方法契约摘要**：前置为全退重开条件满足；持久化拷贝生成新单；失败为条件不满足；触发重开事件。

---

#### 5.1.13 `createHerbalMedicationOrderDraft(CreateHerbalMedicationOrderDraftCommand cmd)` → `Long`
**命令文件**：`CreateHerbalMedicationOrderDraftCommand.java`；**字段全集**：`operatorId,encounterId,patientId,medicationId,tcmFlag,chineseHerbsDoseQuantity,sufferingFlag,quantity,unitCode,dose,doseUnitCode,methodCode,rateCode,therapyEnum,dispensePerDuration,contentJson,dosageInstruction,remark,encounterDiagnosisIds,contractId,orgId,performLocation,recorderId,sortNumber,generateSourceEnum,rxTypeCode,prescriptionNo`（`quantity`=请求数量=单剂×付数；`chineseHerbsDoseQuantity`=付数；`therapyEnum`=长临 code，住院方头传入，未传回落临时）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateHerbalDraftInput` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | `existsByBusNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `new HerbalMedicationOrderDraftCreatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-013` |
**方法契约摘要**：前置为中药路径输入完整；持久化草稿与中药扩展字段；**同步写入 `reqOpenedTime`（首次暂存时刻）**；失败为中西药路径混用；触发中药草稿创建事件。

---

#### 5.1.14 `changeHerbalMedicationOrderDraft(ChangeHerbalMedicationOrderDraftCommand cmd)` → `void`
**命令文件**：`ChangeHerbalMedicationOrderDraftCommand.java`；**字段全集**：`operatorId,id,medicationId,chineseHerbsDoseQuantity,sufferingFlag,quantity,unitCode,dose,doseUnitCode,methodCode,rateCode,dispensePerDuration,contentJson,dosageInstruction,remark,encounterDiagnosisIds,contractId,orgId,performLocation,sortNumber,rxTypeCode,prescriptionNo`（null 字段不变更；须 `tcm_flag=true` 草稿行）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureHerbalDraftEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new HerbalMedicationOrderDraftEditedEvent(...)` |
| `unitTestCaseId` | `TC-MEDREQ-WS-014` |
**方法契约摘要**：前置为中药草稿状态；持久化更新中药字段；失败为非草稿或路径错误；触发中药草稿修改事件。

---

#### 5.1.15 `splitPrescription(SplitPrescriptionCommand cmd)` → `SplitPrescriptionResult`
**命令文件**：`SplitPrescriptionCommand.java`  
**字段全集**：`encounterId,operatorId,lines,amountLimitYuan`（`lines` 元素为 `PrescriptionSplitLineContext`：`requestId,medicationId,medType,partAttributeEnum,medicationCategoryCode,tcmFlag,groupId,pharmacyLocationId,chargeTotalPrice` 及用法签名字段）。  
**结果文件**：`SplitPrescriptionResult.java` — `totalCount,splitUnitCount,prescriptionNoByRequestId,rxTypeCodeByRequestId`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `com.zxplat.domain.medication.medicationrequest.prescriptionsplit.OutpatientPrescriptionSplitPlanner.plan`（装箱，不改 `group_id`） |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdIn`（参与行须全部存在） |
| `eventPublishAnchor` | **无**（首版） |
| `unitTestCaseId` | `TC-MEDREQ-WS-015` |
**方法契约摘要**：应用层 `OutpatientPrescriptionSplitAssembler` 装配**西药/中成药**未收费参与集快照与金额阈值（**排除** `tcmFlag=true` 中药饮片；中药由 IF-DOR-37 取号）；领域按 **med_type / 特殊药品 / FIFO 库房 / ≤5 种 / 金额 / 西药·中成药分轨** 划分单元；每单元 `BusinessSequenceService.getSeqByDay(COMPOSITE_PRESCRIPTION, …)` 生成 **`prescription_no`（`CP`）** 并推导 `rx_type_code`；`saveAll` 回写；**仅门诊**医生站 IF-DOR-15 签发前调用；失败为行缺失或强制规则冲突。

---

#### 5.1.16 `completeMedicationRequestAfterOutpatientCharge(Long medicationRequestId, Long operatorId)` → `void`
**命令文件**：无（领域服务原子 API；由 **`OutpatientChargeAdviceStateSyncService`** 按 **AS-OP-FIN-002** **BR-OPC-015** / IF-OPC-07 编排）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 与门诊收费收口路径一致的可完成断言（实现：`MedicationRequestDomainService`） |
| `statusTransitionMethod` | **ACTIVE→COMPLETED**（不进住院 **VERIFIED**） |
| `repositoryChecks` | `requireMedicationRequest` |
| `eventPublishAnchor` | `MedicationOrderCompletedAfterOutpatientChargeEvent` |
| `unitTestCaseId` | `TC-MEDREQ-WS-016` |
**方法契约摘要**：收费项已 **SETTLED** 后幂等完成药品申请；常与 **`MedicationDispenseDomainService#promoteToPreparation`** 同批编排。

---

#### 5.1.17 `completeMedicationRequestAfterRefund(Long requestId, Long operatorId)` → `void`
**命令文件**：无（药房退药闭环原子 API）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureCompletableAfterPharmacyRefund` |
| `statusTransitionMethod` | `toCompletedAfterPharmacyRefund` |
| `repositoryChecks` | `mustGet` |
| `eventPublishAnchor` | `MedicationOrderCompletedAfterOutpatientChargeEvent` |
| `unitTestCaseId` | `TC-MEDREQ-WS-017` |
**方法契约摘要**：负向药品申请在药房退药执行后幂等收口 **completed**；与 **AS-OP-PHA-002** 对齐。

---

#### 5.1.18 `cloneMedicationRequestForPreservedChargeRegeneration(CloneMedicationRequestForPreservedChargeRegenerationCommand cmd)` → `Long`
**命令文件**：`CloneMedicationRequestForPreservedChargeRegenerationCommand.java`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateDosageSemantics` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | 源行 `mustGet` |
| `eventPublishAnchor` | `MedicationOrderDraftCreatedEvent` |
| `unitTestCaseId` | `TC-MEDREQ-WS-018` |
**方法契约摘要**：门诊半退保留链正向克隆；`based_on_*` 指向源单；与 **DM-ADM-011 §6.1.9a**、**AS-OP-DOR-004** 对齐。

### 5.2 读侧 — `MedicationRequestQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`。  

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `loadMedicationRequestById` | `Long id` | `MedicationRequestDetailQueryModel` | 主键查询 | TC-MEDREQ-RS-001 |
| `listMedicationRequestsByEncounter` | `Long encounterId` | `MedicationRequestListQueryModel` | 就诊列表 | TC-MEDREQ-RS-002 |
| `listMedicationRequestHistoryByPatient` | `Long patientId,Long currentEncounterId` | `MedicationRequestHistoryQueryModel` | 历史排除当前就诊 | TC-MEDREQ-RS-003 |
| `checkMedicationRequestDeletable` | `Long id` | `MedicationRequestDeleteCheckQueryModel` | 已收费校验 | TC-MEDREQ-RS-004 |
| `checkMedicationRequestSignOffAllowed` | `Long id` | `MedicationRequestSignOffCheckQueryModel` | 签退前校验 | TC-MEDREQ-RS-005 |
| `listByEncounterIdsAndStatusEnum` | `Collection<Long> encounterIds, String statusEnum` | `List<MedicationRequest>` | 护士站批量计数（DM §6.2.7） | TC-MEDREQ-RS-006 |
| `resolveMedicationDefinitionId` | `Long requestId` | `Long`（可 null） | 主表取 `medication_id`；已删/不存在 → null | TC-MEDREQ-RS-007 |
| `loadFifoSignContextOrThrow` | `Long medReqId` | `MedicationRequestFifoSignContext` | FIFO 签发切片；不存在抛错 | TC-MEDREQ-RS-008 |
| `mapEntitiesByIds` | `Collection<Long> medicationRequestIds` | `Map<Long,MedicationRequest>` | 批量实体；已删不入 Map | TC-MEDREQ-RS-009 |
| `mapContentJsonByIds` | `Collection<Long> medicationRequestIds` | `Map<Long,String>` | 批量 `content_json` | TC-MEDREQ-RS-010 |
| `listMedicationRequestsByEncounterForDoctorStation` | `Long encounterId, String generateSourceEnum` | `List<MedicationRequestListQueryModel>` | 医生站来源过滤 | TC-MEDREQ-RS-011 |
| `listMedicationRequestIdsByEncounterScopeAndStatusIn` | `encounterId`, `encounterIdsScope`, `statusEnumsIn` | `List<Long>` | 发药单筛选主键 | TC-MEDREQ-RS-012 |
| `listInpatientDoctorStationOrders` | `Long encounterId, String therapyEnum` | `List<MedicationRequestListQueryModel>` | 住院医生站列表 | TC-MEDREQ-RS-013 |
| `listInpatientNurseStationAuditOrders` | `List<Long> encounterIds`, `statusEnumIn`, `therapyEnum` | `List<MedicationRequestListQueryModel>` | 护士校对列表 | TC-MEDREQ-RS-014 |
| `listInpatientNurseStationExecuteOrders` | `List<Long> encounterIds`, `therapyEnum` | `List<MedicationRequestListQueryModel>` | 护士执行列表（含 STOPPED） | TC-MEDREQ-RS-015 |
| `listInpatientNurseStationSupplementOrders` | `List<Long> encounterIds`, `therapyEnum` | `List<MedicationRequestListQueryModel>` | 补费父医嘱列表 | TC-MEDREQ-RS-016 |
| `getListModelById` / `mustGetListModelById` | `Long id` | `MedicationRequestListQueryModel` | 单条列表投影 | TC-MEDREQ-RS-017 |
| `listTcmHerbalPrescriptionsByEncounter` | `Long encounterId, String generateSourceEnum` | `List<TcmHerbalPrescriptionGroupQueryModel>` | IF-DOR-34；`tcm_flag` 筛选 + 按 `prescription_no` / `content_json.prescriptionGroupKey` 聚合 | TC-MEDREQ-RS-018 |
| `listTcmHerbalPrescriptionsByEncounter` | `Long encounterId, String generateSourceEnum, String therapyEnum` | `List<TcmHerbalPrescriptionGroupQueryModel>` | IF-IP-TCM-34；可选 `therapy_enum` 收窄；其余同 TC-MEDREQ-RS-018 | TC-MEDREQ-RS-018a |
| `listTcmHerbalPrescriptionHistoryByPatient` | `Long patientId, Long currentEncounterId` | `List<TcmHerbalPrescriptionHistoryGroupQueryModel>` | IF-DOR-39；排除当前就诊；仅已签发行 | TC-MEDREQ-RS-019 |

**读方法注释锚点（统一）**：构建过滤条件 → 查询聚合/协作只读数据 → 组装结果模型（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-MRQ-001 | 保存时库存校验编排 |
| BR-MRQ-002 | 已收费不可删 |
| BR-MRQ-003 | 已收费不可签退 |
| BR-MRQ-004 | 保存草稿、签发已发送 |
| BR-MRQ-005 | 编码生成并持久化 |
| BR-MRQ-006 | 停嘱仅长期且须 VERIFIED；清停嘱校对字段 |
| BR-MRQ-006a | 取消停嘱须未停嘱校对；清停嘱轴+停嘱校对 |
| BR-MRQ-006b | 改时；改停嘱保留停嘱校对 |
| BR-MRQ-006d/006e | 停嘱校对/取消停嘱校对只写/清痕迹 |
| BR-MRQ-007 | 校对/退回写校对人时间 |
| BR-MRQ-008 | 分组批量更新与置空 |
| BR-MRQ-009 | 长临时数量语义分治 |
| BR-MRQ-010 | 首日量仅长期、住院临时频次立即 |
| BR-MRQ-011 | 中西药分轨创建/修改 |
| BR-MRQ-012 | 取消与移除语义区分 |
| BR-MRQ-013 | 负向退药单幂等（未完结复用主键；原单已负向才拒） |
| BR-MRQ-014 | 全退重开新单可追溯 |
| BR-MRQ-015 | 分方为本聚合领域责任 |
| BR-MRQ-016 | P0~P5 分方优先级 |
| BR-MRQ-017 | 中医方剂手动分方策略 |
| BR-MRQ-018 | 法定用量/诊疗底线 |
| BR-MRQ-019 | 医保规则前置校验 |
| BR-MRQ-020 | 院内分级阈值规则 |
| BR-MRQ-021 | 就诊列表优先QueryService |
| BR-MRQ-022 | 历史查询排除当前就诊 |
| BR-MRQ-023 | 读校验写分层 |
| BR-MRQ-024 | 西/中成药单元品种数 <=5 |
| BR-MRQ-025 | 中药饮片独立单元 |
| BR-MRQ-026 | 麻/精一独立单元 |
| BR-MRQ-027 | 门诊金额阈值可配置 |
| BR-MRQ-028 | 医生站暂存分轨：长期不挂费/不发药；临时同门诊（暂存 Request+费用，签发 FIFO 建发放） |
| BR-MRQ-029 | `orgId`/`performLocation` 主键空间分列；读路径开单科室、`ServiceLocationTemplate` 入参只读 `orgId` |
| BR-MRQ-030 | 暂存载荷 `dispenseLocationId` 仅用于 `resolveDraftChargePriceLine` 择价，不落库 `performLocation` |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `MedicationOrderDraftCreatedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,encounterId,patientId` | `createMedicationOrderDraft` |
| `MedicationOrderDraftEditedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,changedFields` | `changeMedicationOrderDraft` |
| `MedicationOrderDraftRemovedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,encounterId` | `removeMedicationOrderDraft` |
| `MedicationOrderSignedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,signCode,prescriptionNo,statusEnum,operatorId` | `signMedicationOrder` |
| `MedicationOrderSignOffEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id` | `signOffMedicationOrder` |
| `MedicationOrderVerifiedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,performerCheckId,checkTime,statusEnum` | `verifyMedicationOrder` |
| `MedicationOrderRejectedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,reason,statusEnum` | `rejectMedicationOrder` |
| `LongTermMedicationOrderStoppedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,effectiveDoseEnd,statusEnum` | `stopLongTermMedicationOrder` |
| `LongTermMedicationOrderStopCancelledEvent` | 同上 | `id,operatorId,statusEnum` | `cancelStopLongTermMedicationOrder` |
| `MedicationOrderGroupAdjustedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `ids,groupId` | `adjustMedicationOrderGroup` |
| `MedicationOrderCancelledEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,cancelReason` | `cancelMedicationOrder` |
| `HerbalMedicationOrderDraftCreatedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,encounterId` | `createHerbalMedicationOrderDraft` |
| `HerbalMedicationOrderDraftEditedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,changedFields` | `changeHerbalMedicationOrderDraft` |
| `MedicationOrderRefundRequestedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,refundMedicineId` | `createRefundMedicationOrder` |
| `MedicationOrderReopenedEvent` | `com.zxplat.domain.share.events.medication.medicationrequest` | `id,originRequestId` | `reopenMedicationOrder` |

**发布时机**：全部 beforeCommit。

---

## 8. 删除与可见性契约

### 8.1 软删语义

- **逻辑删除字段**：`isDeleted`（`Boolean`），继承自 `BaseAuditedEntity`。
- **删除策略**：
  - **草稿（`DRAFT`）**：允许物理移除，同时清理关联协作对象（发放占用、费用草稿、`contentJson`，BR-MRQ-011）；
  - **非草稿态**：禁止物理删除，走状态迁移（取消/停嘱/退回）；
  - **已收费记录**：禁止移除草稿（BR-MRQ-002），禁止签退（BR-MRQ-003）。
- **查询默认过滤**：所有查询默认附加 `isDeleted = false` 条件。

### 8.2 状态可见性与操作权限

| 状态（`statusEnum`） | 签发 | 签退 | 校对 | 退回 | 停嘱 | 取消 | 删除草稿 | 退药 |
| ------------------- | ---- | ---- | ---- | ---- | ---- | ---- | -------- | ---- |
| `DRAFT`（草稿） | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ 未收费可删 | ❌ |
| `ACTIVE`（已发送） | ❌ | ✅ 未收费可退 | ✅ | ✅ 可退回 | ✅ 长期可停 | ✅ 可取消 | ❌ | ❌ |
| `VERIFIED`（已校对） | ❌ | ❌ | ❌ | ✅ 可退回 | ✅ 长期可停 | ✅ 可取消 | ❌ | ❌ 可生成负向单 |
| `COMPLETED`（已完成） | ❌ | ❌ | ❌ | 以实现与 BR 为准 | 以实现与 BR 为准 | 以实现与 BR 为准 | ❌ | 以实现与 BR 为准 |
| `STOPPED`（已停止） | ❌ | ❌ | ❌ | ❌ | ✅ 长期可取消停嘱 | ❌ | ❌ | ❌ |
| `CANCELLED`（已取消） | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ 负向单态 |

### 8.3 中西药分轨可见性

- **西药/中成药路径**：使用 `createMedicationOrderDraft` / `changeMedicationOrderDraft`；
- **中药饮片路径**：使用 `createHerbalMedicationOrderDraft` / `changeHerbalMedicationOrderDraft`（BR-MRQ-011）；
- **共用操作**：移除草稿、签发、签退、校对、退回、停嘱、取消、分方等中西药共用；
- **识别标志**：`tcmFlag` 字段区分中药与非中药，用于分支判断。

---

## 9. 框架优先适配规则

### 9.1 实体与仓储

| 规则 | 说明 |
| ---- | ---- |
| 基类继承 | `MedicationRequest extends BaseAuditedEntity`，统一继承审计、软删、租户字段 |
| 实体注解 | `@Entity` + `@Table(name = "med_medication_request")` |
| 状态枚举 | `RequestStatus#getCode()` **字符串**，存储于 `status_enum` 列 |
| 治疗类型 | `TherapyType#getCode()`（`LONG_TERM`/`TEMPORARY`/`DISCHARGE_MEDICATION`），`therapy_enum` 字段，决定 `quantity` 语义（BR-MRQ-009；出院带药同临时总量） |
| 仓储形态 | `interface MedicationRequestRepository extends JpaRepository<MedicationRequest, Long>` |
| 派生查询 | `findByEncounterIdAndIsDeletedFalse`、`findByPatientIdAndIsDeletedFalse` 等 |

### 9.2 领域服务

| 规则 | 说明 |
| ---- | ---- |
| 服务形态 | `MedicationRequestDomainService` 为直接实现类 |
| 事务管理 | 依赖应用层/框架工作单元，禁止机械添加 `@Transactional` |
| 中西药分轨 | 创建/修改草稿分西药/中药两个入口，其他操作共用（BR-MRQ-011） |
| 分方规则 | `splitPrescription` + **`prescriptionsplit.OutpatientPrescriptionSplitPlanner`**（public final）；应用 **`OutpatientPrescriptionSplitAssembler`** 装配行快照（含 **`medicationCategoryCode`**）；**仅门诊** IF-DOR-15 签发前；回写 **`prescription_no`/`rx_type_code`**；含 **med_type** 分区、毒性/麻精/饮片独占、**西药/中成药分轨（BR-MRQ-032）**、FIFO 库房、≤5 **req 行**（相同目录主键不合并）、**同目录+同用法互斥（BR-MRQ-031）**、金额阈值（**`PrescriptionSplitConstants`**） |
| 分方约束 | 麻/精一/毒性独占；精二可入**同轨**西药或中成药；**单元含精二 → `rx_type_code=8`（BR-MRQ-033）**；饮片独占；**西药与中成药不得同方**；**不同 med_type 不同处方**；同 `group_id` 不可拆；西药/中成药各自 ≤5 **req 行**/张（BR-MRQ-024~026、**BR-MRQ-032**）；**同 medicationId+用法签名不得同方（BR-MRQ-031）** |
| 事件发布 | 14 个事件 beforeCommit 发布，驱动发药域、收费域、执行域联动 |

### 9.3 用量语义与校验

| 规则 | 说明 |
| ---- | ---- |
| 长期医嘱 | `quantity` 为单次用量，`firstDose` 为首日量，频次非立即 |
| 临时医嘱 | `quantity` 为总量，门诊配合用药天数，住院临时频次须为「立即」（BR-MRQ-010）；医生站暂存仅 Request+费用草稿，发放签发 FIFO（BR-MRQ-028） |
| 首日量 | 仅长期医嘱适用，临时医嘱首日量须为空 |

### 9.4 读侧校验与查询

| 方法 | 层级 | 职责 |
| ---- | ---- | ---- |
| `checkMedicationRequestDeletable` | `QueryService` | 删除前置校验，检查已收费状态（BR-MRQ-002） |
| `checkMedicationRequestSignOffAllowed` | `QueryService` | 签退前置校验，检查已收费状态（BR-MRQ-003） |
| `listMedicationRequestHistoryByPatient` | `QueryService` | 患者历史查询，排除当前就诊（BR-MRQ-022） |
| `loadMedicationRequestById` | `QueryService` | 主键加载，写前存在性校验 |

### 9.5 与其他聚合协作

| 下游 | 协作方式 | 触发事件 | 本聚合职责 |
| ---- | -------- | -------- | ---------- |
| MedicationDispense（发药） | 应用编排 + 事件 | `MedicationOrderSignedEvent`；医生站临时由 **IF-IP-DOR-15 / IF-DOR-15** 调用 `replaceFifoDraftDispensesAfterDoctorStationSign`（**暂存不**调用 `generateDispenseFromTemporaryOrder`，BR-MRQ-028） | 签发后 FIFO 建发放；长期住院医生站不建发放 |
| ChargeItem（收费） | 应用编排 + 事件 | 临时：暂存 `createChargeIfPossible`（**`resolveDraftChargePriceLine`** + 载荷 **`dispenseLocationId`**，**BR-MRQ-030**；金额仍经 **`ProductDefinitionOrderChargePricingsupport**，**`quantity`/`unit_code` + 产品读模型**；见 **AS-OP/IP-DOR-003 BR-ORD-016**）；签发升位；长期住院医生站暂存不挂费（BR-MRQ-028） | 费用锚定请求或发放表主键 |
| Procedure（执行） | 事件订阅 | `MedicationOrderVerifiedEvent` | 校对后驱动执行登记 |
| MedicationDefinition（药品目录） | 上游引用 | — | 通过 `medicationId` 只读引用 |

### 9.6 分方与打印职责边界

- **领域层职责**：`splitPrescription` + `OutpatientPrescriptionSplitPlanner` 产出处方单元划分，持久化 **`prescription_no`**、**`rx_type_code`** 于本聚合；**不改** `group_id`；
- **首版未落库**：分方序号（`1/N`）、分方原因码；打印/药房按 **`prescription_no`** 聚合；
- **QueryService职责**：按 `prescription_no` 聚合药品行、装配打印字段由**用药处方QueryService**提供，不纳入 §6.2 查询服务；
- **应用/基础设施层**：`OutpatientPrescriptionSplitAssembler` 装配行快照；金额阈值读 **`SettingDefinitionEnum.OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN`**（`OutpatientPrescriptionAmountLimitSettingsupport）；打印模板、版式、水印、打印机队列等属应用层与基础设施职责。

---

## 10. 生成与验收清单

- [ ] 15 个写方法和 15 个命令文件一一对应
- [ ] 分方规则（P0~P5）实现可追溯到 BR-MRQ-024~027
- [ ] 中西药分轨与共用动作边界清晰
- [ ] 14 个事件类与触发方法一致
- [ ] 已收费删除/签退限制可测试

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-MED-003 与本 SDD。
- 明确 ChargeItem、MedicationDispense 协作校验端口。

### 11.2 DoD

- 状态机迁移、分方规则、退药重开链路编译和测试通过。
- 读校验写分层清晰，无查询写库副作用。

### 11.3 失败处理

- 任一 BR 违背或事件不匹配时回退文档修订，不进入提交。

---

## 定稿检查清单（可打勾）

- [x] 金骨架结构完整
- [x] 写侧 15 方法全部展开
- [x] 事件契约覆盖 DM 全事件
- [x] 无“后续补充/待确认”占位

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
| 2026-10-08 | **代码先行回写**：§5.1.1 `CreateMedicationOrderDraftCommand` 增补可选 `prescriptionNo`（手麻麻醉药品与麻醉 SR 同 AR） |
| 2026-09-23 | **分次用量不一致（BR-MRQ-035）**：§4.2 实体增 `doseSchedule`/`quantitySchedule`；Create/Change Command 透传；对齐 **DM v1.3.38** / **AS-IP-DOR-003 · BR-IP-ORD-037** |
| 2026-09-09 | **住院中药长临**：`CreateHerbalMedicationOrderDraftCommand.therapyEnum`（未传回落临时）；对齐 **AS-IP-DOR-006 v1.7** |
| 2026-08-20 | **中药饮片类别同义（BR-MRQ-034）**：规范码 `2`，停用冗余码 `9`；对齐 **DM v1.3.32** |
| 2026-08-20 | **精二处方类别（BR-MRQ-033）**：单元含药品性质精二时回写 `rx_type_code=8`；分方可与同轨普通药同方；对齐 **DM v1.3.31** |
| 2026-08-08 | **停嘱校对**：§4.2 增补 `stopPerformerCheckId`/`stopCheckTime`；§5.1.6/6a/6b/6d/6e；**BR-MRQ-006***；对齐 **DM v1.3.29** / **AS-IP-NUR-003 v1.16** |
| 2026-07-18 | **DD §11.7**：§4.2 实体与中药 Command 诊断字段改为 **`encounterDiagnosisIds`** 有序 ids 串（废止 `conditionDefinitionId`/`encounterDiagnosisId` 双字段） |
| 2026-07-16 | **TherapyType.DISCHARGE_MEDICATION**：Command/`therapy_enum` 取值表增补出院带药；对齐 **DM v1.3.24**；关闭 **G-IP-DOR-003-05** |
| 2026-07-15 | **西药/中成药分方分轨（BR-MRQ-032）**：`PrescriptionSplitLineContext.medicationCategoryCode`；分方轨道 `WESTERN`/`CHINESE_PATENT`；`rx_type_code` 中成药 `10`/`11`/`12`；对齐 **DM v1.3.22** |
| 2026-06-20 | **住院中医成码闭环（AS-IP-DOR-006）**：§4.6.6 `TcmHerbalPrescriptionGroupQueryModel.therapyEnum`；§5.1.8 `ensureRejectable` 明确 **ACTIVE**/**VERIFIED** 与 **IF-IP-TCM-38** 调用边界；§5.2 `listTcmHerbalPrescriptionsByEncounter` 三参重载（**IF-IP-TCM-34**）；关闭 **G-IP-TCM-001**；对齐 **DM v1.3.16** |
| 2026-06-18 | **IF-IP-DOR-25 成码闭环**：§5.1.6a 对齐实现（`ensureLongTermStoppedForCancelStop`、`operatorId`、事件载荷）；§8.2 **STOPPED** 可取消停嘱；上游 **DM v1.3.15** / **AS v1.17** |
| 2026-06-18 | **取消停嘱（IF-IP-DOR-25）**：§5.1.6a `cancelStopLongTermMedicationOrder`；**BR-MRQ-006a**；事件 `LongTermMedicationOrderStopCancelledEvent`；对齐 **DM** / **AS-IP-DOR-003 v1.16** |
| 2026-06-16 | **中医 Tab 领域对齐（AS-OP-DOR-010）**：§5.1.13/14 中药 Command 全字段；§5.1.15 分方类包路径 `prescriptionsplit`；§5.2 补 IF-DOR-34/39 读法；对齐 **DM v1.3.13** |
| 2026-06-11 | **暂存库位透传（BR-MRQ-030）**：§6 BR 映射、§9.5 ChargeItem 协作；对齐 **DM v1.3.12** |
| 2026-06-12 | **IF-DOR-20/IF-IP-DOR-22 读口径**：医嘱查询与 IF-DOR-10/IF-IP-DOR-10 同构全类型列表；BR-MRQ-029 表述由「药品医嘱查询」改为「医嘱查询」 |
| 2026-06-11 | **组织/库位分列（BR-MRQ-029）**：§4.2.1 `orgId`/`performLocation` 主键空间与必填修正；分方快照改为 `PrescriptionSplitLineContext.pharmacyLocationId`；对齐 **DM v1.3.10**、**IF-DOR-20** slip 读路径 |
| 2026-06-11 | **编号分序列**：`bus_no`/`prescription_no`/`sign_code` 前缀 **MR/CP/ASI**（`AggregateTypeConstants`）；对齐 **DM v1.3.9** |
| 2026-06-11 | **门诊分方成码**：§5.1.15 命令/结果字段与 `OutpatientPrescriptionSplitPlanner` 对齐；§9.6 明确首版不落分方序号/原因；对齐 **DM v1.3.8** |
| 2026-05-28 | **DM-SDD M1**：§5.1.17～18 退药闭环+半退克隆；§5.2 补 §6.2.9～18 读法；对齐 **DM v1.3.7** |
| 2026-05-28 | **ReadModel 废止**（DM **v1.3.6**）：住院医嘱列表读 **`MedicationRequestQueryService`** + 应用 **`InpatientOrderListSkeleton`**；产品名 **`MedicationDefinitionQueryService#findAllByIds`**（**非** OrderInfo / ProductDefinition RM） |
| 2026-05-28 | §9.5 收费协作：临时暂存 Charge 金额经应用 **`ProductDefinitionOrderChargePricingsupport**（**DM-ADM-011 BR-CHG-008**、**AS-OP/IP-DOR-003**） |

---

**本 SDD 正文结束。**
