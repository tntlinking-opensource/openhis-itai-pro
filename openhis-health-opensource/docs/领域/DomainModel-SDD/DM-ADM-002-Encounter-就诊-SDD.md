# DM-ADM-002 Encounter 就诊 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 就诊管理）  
> **聚合**：Encounter（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.encounter` | 满足 | `encounter` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型落地 | §3 主表/子表使用 `DateTime` 文本 | 成码统一使用 `LocalDateTime` |
| 状态编码写法 | §5.2 存在 `INPROGRESS/ONHOLD/PREADMISSION` 简写 | 统一为 `IN_PROGRESS/ON_HOLD/PRE_ADMISSION` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Encounter 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出；子表同步采用 **全量对齐 + 软删旧行**。
- 单次成稿目标：正文不出现待确认/后续补充类措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：`adm_encounter`、`adm_encounter_participant`、`adm_encounter_location`、`adm_encounter_diagnosis`、`adm_encounter_reason` 的聚合内状态、字段、不变式、事件发布。
- **在外**：号源占用、支付退款、住院窗口办理、结算编排、队列叫号外部系统策略。
- **跨域仅端口声明**：仅声明与 Patient/Location/Practitioner/Account/Condition/EpisodeOfCare 的引用与事件契约，不在本聚合内执行跨域写入。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.encounter`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Encounter`、`EncounterParticipant`、`EncounterLocation`、`EncounterDiagnosis`、`EncounterReason` |
| `valueobject` | `EncounterStatusSnapshot`（真正值对象） |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums`（`EncounterStatus`、`EncounterClass`、`EncounterParticipantRole`、`ActPriority`、`LocationForm` 等） |
| `command` | 每个写方法一个 Command 文件，必要时子命令独立文件 |
| `repository` | `EncounterRepository` 及子实体仓储（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | 共享事件包：`com.zxplat.domain.share.events.administration.encounter` |

**存储口径（DM 优先）**：`*_enum` 默认 `Integer`（或 DM 明示类型）；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `encounterId` |
| 时间 | 统一 `LocalDateTime` |
| `*_enum` | 枚举编码列，默认 `Integer`（或 DM 明示类型） |
| `*_code` | 字典码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，由框架统一过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `interface XxxRepository extends JpaRepository<Entity, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务不机械添加 `@Transactional` |
| 事件 | 服务内直接 `new XxxEvent(...)` + `DomainEventPublisher.publish`，`beforeCommit` 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_encounter` | `Encounter` | 聚合根主表 |
| `adm_encounter_participant` | `EncounterParticipant` | 参与者轨迹 |
| `adm_encounter_location` | `EncounterLocation` | 位置轨迹 |
| `adm_encounter_diagnosis` | `EncounterDiagnosis` | 诊断关联 |
| `adm_encounter_reason` | `EncounterReason` | 就诊原因 |

### 4.2 聚合根 `Encounter`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根主键 |
| `patientId` | `Long` | 是 | 患者标识，关联 Patient |
| `groupId` | `Long` | 否 | 就诊分组标识 |
| `busNo` | `String` | 是 | 全局唯一业务号 |
| `statusEnum` | `Integer` | 是 | 就诊状态，见 DM §2.3 |
| `classEnum` | `Integer` | 是 | 就诊类别，见 DM §4.1 |
| `ybClassEnum` | `Integer` | 否 | 医保类别编码 |
| `ybClassText` | `String` | 否 | 医保类别文本 |
| `classJson` | `String` | 否 | 类别扩展 JSON |
| `priorityEnum` | `Integer` | 否 | 优先级，数值越小越优先 |
| `typeEnum` | `Integer` | 否 | 就诊类型编码 |
| `serviceTypeId` | `Long` | 否 | 号源类型标识 |
| `subjectStatusEnum` | `Integer` | 否 | 就诊对象状态 |
| `startTime` | `LocalDateTime` | 否 | 就诊开始时间 |
| `endTime` | `LocalDateTime` | 否 | 就诊结束时间 |
| `organizationId` | `Long` | 是 | 机构科室标识 |
| `displayOrder` | `Integer` | 否 | 显示顺序 |
| `firstEnum` | `Integer` | 否 | 初复诊标识 |
| `admitSourceCode` | `String` | 否 | 入院类型编码（术语 `admit_source`；**勿与** `inWayCode` 混淆） |
| `inWayCode` | `String` | 否 | 入院途径编码（术语 `in_way_code`；与医保 **adm_way** 同口径 **1/2/3/9**） |
| `receptionTime` | `LocalDateTime` | 否 | 接诊时间 |
| `registrarId` | `Long` | 否 | 登记员标识 |
| `ambEncounterId` | `Long` | 否 | 门诊转住院原就诊标识 |
| `outWayCode` | `String` | 否 | 出院方式编码 |
| `chargeIntent` | `String` | 否 | 入院办理意向（`ChargeIntent` code：`INSURANCE`/`SELF_PAY`）；供登记未开户预填费用性质（医保默认市医保）；非已开户合同真源 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertStateTransitionAllowed`、`assertTerminalStateImmutable`、`assertOutpatientDuplicateRule`、`assertDoctorBeforeComplete`、`assertMainDiagnosisRequired`。

### 4.3 子实体 `EncounterParticipant`

| 字段名（Java） | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 子实体主键 |
| `encounterId` | `Long` | 是 | 所属就诊标识 |
| `roleCode` | `String` | 是 | 参与者角色编码（存 `EncounterParticipantRole#getCode()`，DM §3.2.1） |
| `practitionerId` | `Long` | 是 | 医务人员标识 |
| `statusEnum` | `Integer` | 是 | 活动状态 |
| `startTime` | `LocalDateTime` | 否 | 参与开始时间 |
| `endTime` | `LocalDateTime` | 否 | 参与结束时间 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**归属校验**：更新子记录必须校验 `id + encounterId` 一致，不一致拒绝。

### 4.4 子实体 `EncounterLocation` / `EncounterReason` / `EncounterDiagnosis`

#### 4.4.1 `EncounterLocation`

| 字段名（Java） | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 子实体主键 |
| `encounterId` | `Long` | 是 | 所属就诊标识 |
| `locationId` | `Long` | 是 | 位置资源标识 |
| `formEnum` | `Integer` | 否 | 位置形态编码 |
| `statusEnum` | `Integer` | 是 | 占用状态编码 |
| `startTime` | `LocalDateTime` | 否 | 占用开始时间 |
| `endTime` | `LocalDateTime` | 否 | 占用结束时间 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

#### 4.4.2 `EncounterReason`

| 字段名（Java） | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 子实体主键 |
| `encounterId` | `Long` | 是 | 所属就诊标识 |
| `useCode` | `String` | 是 | 原因用途编码 |
| `reasonTable` | `String` | 否 | 原因来源表 |
| `reasonId` | `Long` | 否 | 原因值标识 |
| `description` | `String` | 否 | 原因描述 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

#### 4.4.3 `EncounterDiagnosis`

| 字段名（Java） | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 子实体主键 |
| `encounterId` | `Long` | 是 | 所属就诊标识 |
| `conditionId` | `Long` | 是 | 诊断标识（Condition） |
| `primaryFlag` | `Boolean` | 否 | 主诊断标记；软删关联时须置 `false`（活动主诊唯一，**BR-ENC-014**） |
| `rank` | `Integer` | 否 | 诊断排序 |
| `description` | `String` | 否 | 诊断描述 |
| `diagTypeCode` | `String` | 否 | 住院疾病诊断类型（`ipt_dise_type_code` 的 code，如入院诊断 `31`） |
| `diagCode` | `Integer` | 否 | 住院疾病对应码 |
| `conditionCode` | `Integer` | 否 | 入院病情代码 |
| `medType` | `String` | 否 | 医保医疗类别；`YbMedType#getCode()`；未传默认 `11`（普通门诊） |
| `highestDiagFlag` | `Boolean` | 否 | 最高诊断依据标记 |
| `tcmSyndromeGroup` | `String` | 否 | 中医证候组号 |
| `tcmFlag` | `Boolean` | 否 | 中医标识 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

### 4.5 仓储接口

- `EncounterRepository extends JpaRepository<Encounter, Long>`
  - `findByIdAndClassEnum(Long id, String classEnum)` — 按主键与就诊类别加载（住院/门诊挂号读侧隔离）
  - `findAllByIdInAndClassEnum(Collection<Long> ids, String classEnum)` — 批量按类别加载（如住院登记列表解析 `amb_encounter_id`）
  - `findTopByAmbEncounterIdAndClassEnumAndStatusEnumNotInOrderByCreatedAtDesc(Long ambEncounterId, String classEnum, Collection<String> excludedStatusEnums)` — 住院申请防重（**BR-ENC-018**）
- `EncounterParticipantRepository extends JpaRepository<EncounterParticipant, Long>`
- `EncounterLocationRepository extends JpaRepository<EncounterLocation, Long>`
- `EncounterReasonRepository extends JpaRepository<EncounterReason, Long>`
- `EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long>`

**说明**：软删由框架统一过滤时，不强制在接口名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `EncounterDetailQueryModel`（对应 `loadEncounterById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_encounter.id` | 就诊标识 |
| `patientId` | `Long` | `adm_encounter.patient_id` | 患者标识 |
| `classEnum` | `Integer` | `adm_encounter.class_enum` | 就诊类别 |
| `statusEnum` | `Integer` | `adm_encounter.status_enum` | 就诊状态 |
| `startTime` | `LocalDateTime` | `adm_encounter.start_time` | 开始时间 |
| `endTime` | `LocalDateTime` | `adm_encounter.end_time` | 结束时间 |

#### 4.6.2 `EncounterDetailQueryModel`（对应 `mustGetEncounter` / `mustGetEncounterOfClass`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_encounter.id` | 就诊标识 |
| `patientId` | `Long` | `adm_encounter.patient_id` | 患者标识 |
| `classEnum` | `Integer` | `adm_encounter.class_enum` | 就诊类别 |
| `statusEnum` | `Integer` | `adm_encounter.status_enum` | 就诊状态 |
| `organizationId` | `Long` | `adm_encounter.organization_id` | 科室标识 |
| `receptionTime` | `LocalDateTime` | `adm_encounter.reception_time` | 接诊时间 |

#### 4.6.3 `EncounterSimpleQueryModel`（对应 `loadEncounterByBusNo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_encounter.id` | 就诊标识 |
| `busNo` | `String` | `adm_encounter.bus_no` | 就诊业务号 |
| `classEnum` | `String` | `adm_encounter.class_enum` | 就诊类别 code |
| `statusEnum` | `String` | `adm_encounter.status_enum` | 就诊状态 code |

#### 4.6.3a `OpenInpatientLineQuery`（对应 `findOpenInpatientLine`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `exists` | `boolean` | 派生 | 是否存在未取消住院线 |
| `encounterId` | `Long` | `adm_encounter.id` | 住院就诊标识；无命中为 null |
| `busNo` | `String` | `adm_encounter.bus_no` | 住院业务号 |
| `statusEnum` | `String` | `adm_encounter.status_enum` | 住院状态 code |

#### 4.6.4 `EncounterParticipantQueryModel`（对应 `listActiveEncounterParticipants`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `participantId` | `Long` | `adm_encounter_participant.id` | 参与者标识 |
| `encounterId` | `Long` | `adm_encounter_participant.encounter_id` | 就诊标识 |
| `roleCode` | `String` | `adm_encounter_participant.role_code` | 角色编码 |
| `practitionerId` | `Long` | `adm_encounter_participant.practitioner_id` | 医务人员 |
| `statusEnum` | `Integer` | `adm_encounter_participant.status_enum` | 活动状态 |

#### 4.6.5 `EncounterParticipantQueryModel`（对应 `listActiveEncounterParticipantsByType`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `participantId` | `Long` | `adm_encounter_participant.id` | 参与者标识 |
| `encounterId` | `Long` | `adm_encounter_participant.encounter_id` | 就诊标识 |
| `roleCode` | `String` | `adm_encounter_participant.role_code` | 角色编码 |
| `practitionerId` | `Long` | `adm_encounter_participant.practitioner_id` | 医务人员 |
| `startTime` | `LocalDateTime` | `adm_encounter_participant.start_time` | 开始时间 |

#### 4.6.6 `EncounterLocationQueryModel`（对应 `listEncounterLocations`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `locationRecordId` | `Long` | `adm_encounter_location.id` | 位置记录标识 |
| `encounterId` | `Long` | `adm_encounter_location.encounter_id` | 就诊标识 |
| `locationId` | `Long` | `adm_encounter_location.location_id` | 位置标识 |
| `formEnum` | `Integer` | `adm_encounter_location.form_enum` | 位置形态 |
| `statusEnum` | `Integer` | `adm_encounter_location.status_enum` | 占用状态 |
| `startTime` | `LocalDateTime` | `adm_encounter_location.start_time` | 开始时间 |
| `endTime` | `LocalDateTime` | `adm_encounter_location.end_time` | 结束时间 |

#### 4.6.7 `EncounterLocationInfoQueryModel`（对应 `getEncounterLocationInfo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `encounterId` | `Long` | `adm_encounter_location.encounter_id` | 就诊标识 |
| `currentLocationId` | `Long` | `adm_encounter_location.location_id` | 当前位置 |
| `currentFormEnum` | `Integer` | `adm_encounter_location.form_enum` | 当前形态 |
| `activeSince` | `LocalDateTime` | `adm_encounter_location.start_time` | 当前位置起始时间 |

#### 4.6.8 `EncounterDiagnosisQueryModel`（对应 `listEncounterDiagnoses`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `diagnosisId` | `Long` | `adm_encounter_diagnosis.id` | 诊断关联标识 |
| `encounterId` | `Long` | `adm_encounter_diagnosis.encounter_id` | 就诊标识 |
| `conditionId` | `Long` | `adm_encounter_diagnosis.condition_id` | 诊断标识 |
| `primaryFlag` | `Boolean` | `adm_encounter_diagnosis.primary_flag` | 主诊断标记 |
| `rank` | `Integer` | `adm_encounter_diagnosis.rank` | 排序 |

#### 4.6.9 `EncounterProjectionQueryModel`（对应 `queryEncounterProjections`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `encounterId` | `Long` | `adm_encounter.id` | 就诊标识 |
| `patientName` | `String` | 联查患者或占位（以实现为准） | 患者姓名 |
| `classEnum` | `Integer` | `adm_encounter.class_enum` | 就诊类别 |
| `statusEnum` | `Integer` | `adm_encounter.status_enum` | 当前状态 |
| `organizationId` | `Long` | `adm_encounter.organization_id` | 科室标识 |

#### 4.6.10 `EncounterCompletionCheckQueryModel`（对应 `checkOutpatientCompletable`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `completable` | `Boolean` | 规则计算 | 是否可完诊 |
| `failureCode` | `String` | 规则计算 | 不可完诊原因码 |
| `failureMessage` | `String` | 规则计算 | 不可完诊说明 |
| `encounterId` | `Long` | `adm_encounter.id` | 就诊标识 |

#### 4.6.11 `OutpatientDuplicateCheckQueryModel`（对应 `checkOutpatientDuplicateRegistration`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `duplicated` | `Boolean` | 规则计算 | 是否重复挂号 |
| `encounterIds` | `List<Long>` | `adm_encounter.id` | 命中记录集合 |
| `patientId` | `Long` | `adm_encounter.patient_id` | 患者标识 |
| `serviceTypeId` | `Long` | `adm_encounter.service_type_id` | 号源类型标识 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `EncounterDomainService`

#### 5.1.1 `registerOutpatientEncounter(RegisterOutpatientEncounterCommand cmd)` → `Long`

**命令文件**：`RegisterOutpatientEncounterCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `RegisterOutpatientEncounterItemCommand` | 单条开立门诊 |
| `items` | `List<RegisterOutpatientEncounterItemCommand>` | 批量开立门诊 |
| `operatorId` | `Long` | 操作人 |
| `occurredAt` | `LocalDateTime` | 操作发生时间（挂号/开立时刻） |
| `skipSameDaySameDeptDuplicateCheck` | `Boolean` | 为 true 时跳过同日同科占名额防重（**BR-ENC-011**）；院点策略放宽时由应用层置位 |

`RegisterOutpatientEncounterItemCommand` 字段（与工程一致）：

- **锚点**：`patientId`、`serviceTypeId`、`organizationId`、`practitionerId`（可选）、`practitionerName`（可选，意向医师展示名）、`classEnum`
- **展示快照**（装配进 `OutpatientRegistrationPlannedEvent`）：`patientBusNo`、`patientName`、`pyStr`、`wbStr`、`genderEnum`、`genderEnumText`、`phone`、`birthDate`、`patientAgeText`、`organizationName`、`serviceTypeName`、`startTime`、`displayOrder`、`registrarId`、`registrarName`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertOutpatientRegistrationRequiredFields` |
| `statusTransitionMethod` | `Encounter.initOutpatientPlanned` |
| `repositoryChecks` | `EncounterRepository.existsOutpatientByPatientAndServiceTypeAndBusinessDate` |
| `participantWrite` | `practitionerId` 非空 → 新建活动 **`EncounterParticipant.role_code=REGISTRATION_DOCTOR`**（**禁止**开立阶段写 `ADMITTER`；接诊 `ADMITTER` 见 **§5.1.4**） |
| `eventPublishAnchor` | `new OutpatientRegistrationPlannedEvent(encounterId, patientId, serviceTypeId, organizationId, busNo)` → `setSlipNo`、`setPatient*`、`setPyStr`/`setWbStr`、`setOrganizationName`、`setServiceTypeName`、`setStartTime`、`setDisplayOrder`、`setRegistrar*`、`setClassEnum`、`setRegistrationDoctorId(item.getPractitionerId())`、`setRegistrationDoctorName(item.getPractitionerName())` 等 → `DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-001` |

**方法契约摘要**：默认校验门诊防重（**BR-ENC-011**）与基础必填后创建主档并初始化参与者；`skipSameDaySameDeptDuplicateCheck=true` 时跳过防重断言；持久化成功后发布事件；失败整体回滚。**注释锚点**：校验 -> 初始化 -> 持久化 -> 事件发布。

---

#### 5.1.2 `completeOutpatientRegistration(Long encounterId)` → `void`

**命令文件**：（无独立 Command；与工程 `EncounterDomainService#completeOutpatientRegistration` 签名一致，由应用层在挂号费正式结算成功后传入 `encounterId`。）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 待确认挂号的门诊就诊主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClass(AMB)` + `Encounter.transitToRegistered`（内联 `assertStatus(PLANNED)`） |
| `statusTransitionMethod` | `Encounter.transitToRegistered` |
| `repositoryChecks` | `EncounterRepository.findById` |
| `eventPublishAnchor` | **无** Encounter 域新事件；读侧与 **`OutpatientPaymentSettledEvent`**（支付域）对齐 |
| `unitTestCaseId` | `TC-ENC-W-001B` |

**方法契约摘要**：仅 **`AMB + PLANNED` → REGISTERED**；不发布额外领域事件。**注释锚点**：加载 -> 类别门禁 -> 状态迁移 -> 持久化。

---

#### 5.1.3 `startOutpatientReception(StartOutpatientReceptionCommand cmd)` → `void`

**命令文件**：`StartOutpatientReceptionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `StartOutpatientReceptionItemCommand` | 单条接诊 |
| `items` | `List<StartOutpatientReceptionItemCommand>` | 批量接诊 |
| `operatorId` | `Long` | 操作人 |

`StartOutpatientReceptionItemCommand` 字段：`encounterId`、`doctorId`、`doctorName`（接诊医师姓名展示快照，**须由应用层与 `doctorId` 成对装配**）、`receptionTime`、`directFromPlanned`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertReceptionAllowed` |
| `statusTransitionMethod` | `Encounter.transitToInProgress` |
| `repositoryChecks` | `EncounterParticipantRepository.findActiveByEncounterIdAndRoleCode` |
| `eventPublishAnchor` | `new OutpatientReceptionStartedEvent(encounterId, doctorId, receptionTime)` → `receptionEvent.setDoctorName(item.getDoctorName())` → `DomainEventPublisher.publish(receptionEvent)`（事件字段名 **`doctorId`/`doctorName`**，禁止 `practitioner*` 异名载荷） |
| `unitTestCaseId` | `TC-ENC-W-003` |

**方法契约摘要**：**门诊**仅允许 **`REGISTERED/TRIAGED -> IN_PROGRESS`**（`assertReceptionAllowed`，**禁止 `PLANNED` 接诊**）；写接诊时间并切换活动接诊医生。**注释锚点**：前置校验 -> 状态迁移 -> 参与者收口/新增 -> 事件发布。

---

#### 5.1.5 `markOutpatientLeave(MarkOutpatientLeaveCommand cmd)` → `void`

**命令文件**：`MarkOutpatientLeaveCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `MarkOutpatientLeaveItemCommand` | 单条暂离 |
| `items` | `List<MarkOutpatientLeaveItemCommand>` | 批量暂离 |
| `operatorId` | `Long` | 操作人 |

`MarkOutpatientLeaveItemCommand` 字段：`encounterId`、`leaveReason`、`occurredAt`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertCanMarkOnHold` |
| `statusTransitionMethod` | `Encounter.transitToOnHold` |
| `repositoryChecks` | `EncounterRepository.findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new OutpatientEncounterOnHoldEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-004` |

**方法契约摘要**：仅 `AMB + IN_PROGRESS` 可暂离，状态迁移为 `ON_HOLD`。**注释锚点**：状态校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.6 `completeOutpatientEncounter(CompleteOutpatientEncounterCommand cmd)` → `void`

**命令文件**：`CompleteOutpatientEncounterCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `CompleteOutpatientEncounterItemCommand` | 单条完诊 |
| `items` | `List<CompleteOutpatientEncounterItemCommand>` | 批量完诊 |
| `operatorId` | `Long` | 操作人 |

`CompleteOutpatientEncounterItemCommand` 字段：`encounterId`、`completedAt`、`summary`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertOutpatientCompletable` |
| `statusTransitionMethod` | `Encounter.transitToDischarged` |
| `repositoryChecks` | `EncounterDiagnosisRepository.existsPrimaryByEncounterId` |
| `eventPublishAnchor` | `new OutpatientEncounterCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-005` |

**方法契约摘要**：完诊前校验负责医生与主诊约束，迁移到 `DISCHARGED` 并补齐结束时间。**注释锚点**：完诊资格校验 -> 状态迁移 -> 参与者收口 -> 事件发布。

---

#### 5.1.7 `cancelOutpatientRegistration(CancelOutpatientRegistrationCommand cmd)` → `void`

**命令文件**：`CancelOutpatientRegistrationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `CancelOutpatientRegistrationItemCommand` | 单条取消 |
| `items` | `List<CancelOutpatientRegistrationItemCommand>` | 批量取消 |
| `operatorId` | `Long` | 操作人 |

`CancelOutpatientRegistrationItemCommand` 字段：`encounterId`、`cancelReason`、`cancelAt`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertCancelable` |
| `statusTransitionMethod` | `Encounter.transitToCancelled` |
| `repositoryChecks` | `EncounterRepository.findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new OutpatientRegistrationCancelledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-006` |

**方法契约摘要**：门诊允许 **`REGISTERED`/`TRIAGED`/`IN_PROGRESS`/`DISCHARGED`** 退号（`assertCancelable`）；**禁止 `PLANNED`（待挂号）** 走本取消域动作（待挂号走 §5.1.7a）。写取消原因并收口活动参与者。**注释锚点**：状态校验 -> 状态迁移 -> 收口 -> 事件发布。

---

#### 5.1.7a `cancelPlannedOutpatientRegistration(Long encounterId, String cancelReason)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 断言 `AMB + PLANNED` |
| `statusTransitionMethod` | `Encounter.transitToEnteredInError` |
| `repositoryChecks` | `EncounterRepository.findById` |
| `eventPublishAnchor` | `new OutpatientRegistrationCancelledEvent(...); setStatusEnum(ENTERED_IN_ERROR); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-006a` |

**方法契约摘要**：仅待挂号未收费场景；迁入 **`ENTERED_IN_ERROR`**，与 §5.1.7 退号 **`CANCELLED`** 分流。先落库后发事件。**注释锚点**：前置校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.7b `correctOutpatientRegistrationDepartment(CorrectOutpatientRegistrationDepartmentCommand cmd)` → `void`

**命令文件**：`CorrectOutpatientRegistrationDepartmentCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 门诊就诊 |
| `departmentId` | `Long` | 目标挂号科室，写入 `organization_id` |
| `skipSameDaySameDeptDuplicateCheck` | `Boolean` | 院点放宽同日同科时为 true |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `AMB` 且状态 ∈ `PLANNED`/`REGISTERED`/`TRIAGED`/`IN_PROGRESS`/`ON_HOLD` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | `EncounterRepository.findByIdAndClassEnumAndIsDeletedFalse`；未跳过时 `assertNoOutpatientDuplicate` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | — |

**方法契约摘要**：只改就诊科室。不改状态、不改参与者、不重占号源、不发事件。科室未变化或状态不允许时拒绝。**注释锚点**：前置校验 -> 防重 -> 写入。

---

#### 5.1.8 `upsertEncounterDiagnosisAssociation(UpsertEncounterDiagnosisAssociationCommand cmd)` → `void`

**命令文件**：`UpsertEncounterDiagnosisAssociationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `UpsertEncounterDiagnosisAssociationItemCommand` | 单条维护诊断关联 |
| `items` | `List<UpsertEncounterDiagnosisAssociationItemCommand>` | 批量维护诊断关联 |
| `operatorId` | `Long` | 操作人 |

`UpsertEncounterDiagnosisAssociationItemCommand` 字段：`encounterId`、`conditionId`、`primaryFlag`、`rank`、`description`、`diagTypeCode`、`diagCode`、`conditionCode`、`medType`、`highestDiagFlag`、`tcmSyndromeGroup`、`tcmFlag`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertDiagnosisUpsertAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterDiagnosisRepository.findByEncounterIdAndConditionIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new EncounterDiagnosisAssociationUpsertedEvent(...);` **setter** `diagnosisJson`、`conditionName`（就诊维度快照，见 §7）；`DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-010` |

**方法契约摘要**：单条 upsert 也执行主诊唯一与排序不重复校验。**注释锚点**：集合校验 -> upsert -> 持久化 -> 事件发布（载荷含诊断快照）。

---

#### 5.1.9 `batchUpsertEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand cmd)` → `void`

**命令文件**：`BatchUpsertEncounterDiagnosisAssociationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `BatchUpsertEncounterDiagnosisAssociationItemCommand` | 单个集合维护请求 |
| `items` | `List<BatchUpsertEncounterDiagnosisAssociationItemCommand>` | 批量集合维护请求 |
| `operatorId` | `Long` | 操作人 |

`BatchUpsertEncounterDiagnosisAssociationItemCommand` 字段：`encounterId`、`diagnosisItems(List<EncounterDiagnosisItemCommand>)`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertDiagnosisBatchConsistency` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterDiagnosisRepository.findAllByEncounterIdAndIsDeletedFalse` |
| `eventPublishAnchor` | 每条诊断项：`new EncounterDiagnosisAssociationUpsertedEvent(...);` **setter** `diagnosisJson`、`conditionName`；`DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-011` |

**方法契约摘要**：以集合为单位执行全量对齐+软删旧行，保证中间态一致性。**注释锚点**：批量校验 -> 全量对齐 -> 持久化 -> 事件发布（每条事件携带同一就诊下刷新后的完整快照）。

---

#### 5.1.9a `batchUpsertTcmEncounterDiagnosisAssociation(BatchUpsertEncounterDiagnosisAssociationCommand cmd)` → `void`

**命令文件**：同 §5.1.9（`BatchUpsertEncounterDiagnosisAssociationCommand.java`）；批次内 `DiagnosisItemCommand.tcmFlag` **须为 true**。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTcmBatchUpsertItem` + `assertDiagnosisCollectionRuleByBatchItem` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterDiagnosisRepository.findAllByEncounterId` |
| `eventPublishAnchor` | 本批活动关联逐条 `EncounterDiagnosisAssociationUpsertedEvent`（B.14 先 `saveAll` 后 publish） |
| `unitTestCaseId` | `TC-ENC-W-011a` |

**方法契约摘要**：中医 Tab 全量批次专用；写入强制 `tcm_flag=true`；**仅**软删同就诊下 `tcm_flag=true` 且未再出现于本批的关联行，**不**影响西医关联。**典型消费方**：**AS-OP-DOR-010 · IF-DOR-32**。**互斥**：禁止 IF-DOR-32 调用 §5.1.9 全量 batchUpsert（会误删西医行）。

---

#### 5.1.9b `switchPrimaryEncounterDiagnosis(SwitchPrimaryEncounterDiagnosisCommand cmd)` → `void`

**命令文件**：`SwitchPrimaryEncounterDiagnosisCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 就诊主键 |
| `encounterDiagnosisId` | `Long` | 拟设为主诊的就诊诊断行主键 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 目标行归属本就诊且未删；切换后主诊唯一（**BR-ENC-014**） |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterDiagnosisRepository.findAllByEncounterId` |
| `eventPublishAnchor` | 对主诊标记变更行：`new EncounterDiagnosisAssociationUpsertedEvent(...); DomainEventPublisher.publish(event)`（先 `saveAll` 后 publish） |
| `unitTestCaseId` | `TC-ENC-W-011b` |

**方法契约摘要**：西医按单行置主；中医（`tcm_flag=true` 且 `tcm_syndrome_group` 非空）按病证组整组置主；清空同就诊其余活动行主诊；无变更幂等返回。**典型消费方**：**AS-OP-DOR-002 · IF-DOR-09A**、**AS-IP-DOR-002 · IF-IP-DOR-07**。

**注释锚点**：定位目标/解析组 → 重算主诊标记 → 持久化 → 事件发布。

---

#### 5.1.10 `removeEncounterDiagnosisAssociation(RemoveEncounterDiagnosisAssociationCommand cmd)` → `void`

**命令文件**：`RemoveEncounterDiagnosisAssociationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `RemoveEncounterDiagnosisAssociationItemCommand` | 单条移除关联 |
| `items` | `List<RemoveEncounterDiagnosisAssociationItemCommand>` | 批量移除关联 |
| `operatorId` | `Long` | 操作人 |

`RemoveEncounterDiagnosisAssociationItemCommand` 字段：`encounterId`、`conditionId`、`removeReason`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertDiagnosisRemovable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterDiagnosisRepository`（活动行加载）；软删写 `is_deleted` + `primary_flag=false` |
| `eventPublishAnchor` | `new EncounterDiagnosisAssociationRemovedEvent(...);` **setter** `diagnosisJson`、`conditionName`（移除后快照，见 §7）；`DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-012` |

**方法契约摘要**：仅逻辑删除诊断关联，不删除 Condition 本体；软删时同步卸主诊（`primaryFlag=false`），使同事务「先删后插主诊」不再触发 **BR-ENC-014** 假冲突。西医/中医全量批次软删旧行同口径。**注释锚点**：归属校验 -> 逻辑删除并卸主诊 -> 持久化 -> 事件发布（载荷含刷新后诊断快照）。

---

#### 5.1.11 `requestInpatientAdmission(RequestInpatientAdmissionCommand cmd)` → `Long`

**命令文件**：`RequestInpatientAdmissionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `RequestInpatientAdmissionItemCommand` | 单条发起住院申请 |
| `items` | `List<RequestInpatientAdmissionItemCommand>` | 批量发起住院申请 |
| `operatorId` | `Long` | 操作人 |

`RequestInpatientAdmissionItemCommand` 字段：`ambEncounterId`、`patientId`、`targetDepartmentId`、`requestDoctorId`、`wardId`、`admitSourceCode`、`inWayCode`、`priorityCode`、`chargeIntent`、`admissionDiagnoses`（`List<AdmissionConditionItem>`）。

**值类型文件**：`AdmissionConditionItem.java`（`command` 包）。

`AdmissionConditionItem` 字段：`conditionId`、`conditionDefinitionId`、`primaryFlag`、`description`（**不含** `diagTypeCode`）。

**私有编排 `saveAdmissionDx` / `newAdmissionDiagnosisAssocItem`**（§5.1.14 / §5.1.16 / `registerDirectInpatientEncounter` 共用）：

| 步骤 | 说明 |
| ---- | ---- |
| 病情解析 | `conditionId` 为空时复用同目录病情或 `saveDoctorDiagnosis`（`admissionDiagnosisFlow=true`） |
| 关联 upsert | 经 `newAdmissionDiagnosisAssocItem` → `upsertEncounterDiagnosisAssociation` |
| 诊断类型 | **`diagTypeCode` 固定** `IptPatientDiseaseDiagType.ADMISSION.getCode()`（**`31`**），满足 **BR-ENC-021**；快照 `diagnosisJson` 行写入 `iptDiseaseTypeCode` |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.initInpatientPlanned` |
| `statusTransitionMethod` | `Encounter.initInpatientPlanned` |
| `repositoryChecks` | `EncounterRepository.findById`（源就诊）；`findTopByAmbEncounterIdAndClassEnumAndStatusEnumNotInOrderByCreatedAtDesc`（防重） |
| `eventPublishAnchor` | `new InpatientAdmissionRequestedEvent(...)` + setter `ambEncounterId,busNo,classEnum,statusEnum,inWayCode,priorityEnum,wardId`（`wardId`←`item.wardLocationId`）；`DomainEventPublisher.publish` |
| `unitTestCaseId` | `TC-ENC-W-013` |

**方法契约摘要**：**INSERT** `IMP+PLANNED`；`allocateInpatientBusNos`（`AggregateTypeConstants.INPATIENT_ENCOUNTER`）；写 `ambEncounterId`、参与者、可选 WARD 位置、`saveAdmissionDx`（含 `ConditionDomainService#saveDoctorDiagnosis` 当 `admissionDiagnosisFlow=true`；关联 `diagTypeCode=31` 入院诊断，**BR-ENC-021**）。**注释锚点**：源就诊/防重 -> 主档落库 -> 子表 -> 事件发布（B.14）。

---

#### 5.1.12 `startPreAdmission(StartPreAdmissionCommand cmd)` → `void`

**命令文件**：`StartPreAdmissionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `StartPreAdmissionItemCommand` | 单条预入院 |
| `items` | `List<StartPreAdmissionItemCommand>` | 批量预入院 |
| `operatorId` | `Long` | 操作人 |

`StartPreAdmissionItemCommand` 字段：`encounterId`、`preAdmissionItems`、`occurredAt`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertPreAdmissionAllowed` |
| `statusTransitionMethod` | `Encounter.transitToPreAdmission` |
| `repositoryChecks` | `EncounterRepository.findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new InpatientPreAdmissionStartedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-014` |

**方法契约摘要**：仅 `IMP + PLANNED` 可进入 `PRE_ADMISSION`，记录预入院项目。**注释锚点**：状态校验 -> 状态迁移 -> 字段更新 -> 事件发布。

---

#### 5.1.13 `registerInpatientEncounter(RegisterInpatientEncounterCommand cmd)` → `void`

**命令文件**：`RegisterInpatientEncounterCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `RegisterInpatientEncounterItemCommand` | 单条住院登记 |
| `items` | `List<RegisterInpatientEncounterItemCommand>` | 批量住院登记 |
| `operatorId` | `Long` | 操作人 |

`RegisterInpatientEncounterItemCommand` 字段：`encounterId`、`departmentId`、`wardId`（病区位置）、`registrarId`、`registerTime`、`contractNo`（费用性质 `bus_no`，登记时开账户）、`admitSourceCode`、`inWayCode`、`priorityCode`、`admissionDiagnoses`（`List<AdmissionConditionItem>`）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertInpatientRegistrationAllowed` |
| `statusTransitionMethod` | `Encounter.transitToInpatientRegistered` |
| `repositoryChecks` | `EncounterLocationRepository`；`EncounterDiagnosisRepository.findFirstByEncounterIdAndPrimaryFlagTrueAndIsDeletedFalse`（已有活动主诊则跳过 `saveAdmissionDx`） |
| `eventPublishAnchor` | `new InpatientAdmissionCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-015` |

**方法契约摘要**：**`IMP + PLANNED → REGISTERED`**；写病区位置、登记员/时间；`openInpatientRegAccount`（应用编排或领域内）；若住院线**尚无**主诊且命令带 `admissionDiagnoses` 则 `saveAdmissionDx`（`diagTypeCode=31`，**BR-ENC-021**），**已有主诊则跳过**（申请已在 `requestInpatientAdmission` 落库，**BR-ENC-014**）。**注释锚点**：状态校验 -> 主档迁移 -> 位置/账户 -> 条件诊断 -> 事件发布。

---

#### 5.1.16a `registerDirectInpatientEncounter(RegisterInpatientEncounterCommand cmd)` → `Long`

**命令文件**：`RegisterInpatientEncounterCommand.java`（命令级 **`patientId` 必填**；子项 **不得** 带 `encounterId`）

**字段全集**：同 **5.1.16**；直登路径 **`admissionDiagnoses` 必填**且须含主诊断。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `initInpatientRegistered`；`assertDirectRegistrationAdmissionDiagnosesPresent` |
| `statusTransitionMethod` | **INSERT** `IMP + REGISTERED`（不经 `PLANNED`） |
| `repositoryChecks` | `allocateInpatientBusNos`；`saveAdmissionDx` 唯一落库入院诊断 |
| `eventPublishAnchor` | `new InpatientAdmissionCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-015A` |

**方法契约摘要**：收费窗口无申请直登；**不**发布 `InpatientAdmissionRequestedEvent`；开户 + 病区位置 + 入院诊断一次落库；返回新建 `encounterId`。**注释锚点**：校验 -> INSERT 主档 -> 位置/账户/诊断 -> 事件。

---

#### 5.1.14 `assignInpatientBed(AssignInpatientBedCommand cmd)` → `void`

**命令文件**：`AssignInpatientBedCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `AssignInpatientBedItemCommand` | 单条分床 |
| `items` | `List<AssignInpatientBedItemCommand>` | 批量分床 |
| `operatorId` | `Long` | 操作人 |

`AssignInpatientBedItemCommand` 字段：`encounterId`、`locationId`、`formEnum`（空则 `BED`）、`bedCode`、`assignTime`、`priorityEnum`、`participants`（`ParticipantLine`：`roleCode`、`practitionerId`）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertInpatientBedAssignmentAllowed` |
| `statusTransitionMethod` | `Encounter.transitToInProgress` |
| `repositoryChecks` | `EncounterLocationRepository.findAllByEncounterIdAndStatusEnum`；`EncounterParticipantRepository.findAllByEncounterIdAndRoleCodeAndStatusEnum` |
| `eventPublishAnchor` | `new InpatientBedAssignedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-016` |

**方法契约摘要**：`REGISTERED→IN_PROGRESS`；关闭旧活动位置；新建床位位置；可选写 `priority_enum` 与首次参与者；持久化后再发事件。**注释锚点**：状态校验 -> 病情/参与者 -> 位置收口 -> 入科 -> 事件发布。

---

#### 5.1.15 `confirmBedTransfer(ConfirmBedTransferCommand cmd)` → `void`

**命令文件**：`ConfirmBedTransferCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `ConfirmBedTransferItemCommand` | 单条确认转床 |
| `items` | `List<ConfirmBedTransferItemCommand>` | 批量确认转床 |
| `operatorId` | `Long` | 操作人 |

`ConfirmBedTransferItemCommand` 字段：`encounterId`、`fromLocationId`、`toLocationId`、`effectiveAt`、`toFormEnum`（空则 `BED`）、`toBedCode`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertInpatientBedTransferAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterLocationRepository.findAllByEncounterIdAndStatusEnum`；校验 `fromLocationId` 与活动床一致 |
| `eventPublishAnchor` | `new BedTransferredEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-017` |

**方法契约摘要**：保持 `IN_PROGRESS`；关闭全部活动位置；建立目标床位活动行。**注释锚点**：状态校验 -> 原床归属 -> 位置切换 -> 持久化 -> 事件发布。

---

#### 5.1.15b `correctInpatientAdmissionDepartment(CorrectInpatientAdmissionDepartmentCommand cmd)` → `void`

**命令文件**：`CorrectInpatientAdmissionDepartmentCommand.java`

**字段全集**：`encounterId`、`departmentId`、`wardId`、`houseId`（已入科换床）、`bedId`（已入科；与当前活动床相同则不迁床）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 状态须为 `REGISTERED` 或 `IN_PROGRESS` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 活动 `WARD`；已入科换床时活动 `HOUSE`/`BED` |
| `eventPublishAnchor` | 仅换床：`new BedTransferredEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | — |

**方法契约摘要**：更新 `organization_id` 与活动病区；已入科换床时收口旧病房/床并插入新轨迹。不改就诊状态，不写转科里程碑。代码先行回写 2026-10-12（IF-IP-REG-08）。

---

#### 5.1.18a `swapInpatientBeds(SwapInpatientBedsCommand cmd)` → `void`

**命令文件**：`SwapInpatientBedsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` / `items` | `SwapInpatientBedsItemCommand` | 互换项 |
| `operatorId` | `Long` | 操作人 |

`SwapInpatientBedsItemCommand` 字段：`encounterIdA`、`encounterIdB`、`effectiveAt`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertInpatientBedTransferAllowed`（双方） |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 双方活动 `BED` 位置；`bedIdA`/`bedIdB` 交叉写入 |
| `eventPublishAnchor` | `BedTransferredEvent` ×2 |
| `unitTestCaseId` | `TC-ENC-W-017a` |

**方法契约摘要**：双方收口活动位置后互写对方原床；各发一条 `BedTransferredEvent`。**注释锚点**：双方状态 -> 解析活动床 -> 交叉落库 -> 双事件。

---

#### 5.1.16 `initiateDepartmentTransfer(InitiateDepartmentTransferCommand cmd)` → `void`

**命令文件**：`InitiateDepartmentTransferCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `InitiateDepartmentTransferItemCommand` | 单条发起转科 |
| `items` | `List<InitiateDepartmentTransferItemCommand>` | 批量发起转科 |
| `operatorId` | `Long` | 操作人 |

`InitiateDepartmentTransferItemCommand` 字段：`encounterId`、`fromDepartmentId`、`toDepartmentId`、`initiatedAt`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertDepartmentTransferInitiable` |
| `statusTransitionMethod` | `Encounter.transitToOnHold` |
| `repositoryChecks` | `EncounterParticipantRepository.findActiveByEncounterId` |
| `eventPublishAnchor` | `new DepartmentTransferInitiatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-018` |

**方法契约摘要**：`IN_PROGRESS -> ON_HOLD`，完成转科过渡准备并关闭旧责任关系。**注释锚点**：状态校验 -> 状态迁移 -> 子实体收口 -> 事件发布。

---

#### 5.1.17 `confirmDepartmentTransfer(ConfirmDepartmentTransferCommand cmd)` → `void`

**命令文件**：`ConfirmDepartmentTransferCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `ConfirmDepartmentTransferItemCommand` | 单条确认转科 |
| `items` | `List<ConfirmDepartmentTransferItemCommand>` | 批量确认转科 |
| `operatorId` | `Long` | 操作人 |

`ConfirmDepartmentTransferItemCommand` 字段：`encounterId`、`targetDepartmentId`、`effectiveAt`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertDepartmentTransferConfirmable` |
| `statusTransitionMethod` | `Encounter.transitToInProgress` |
| `repositoryChecks` | `EncounterLocationRepository.findPendingTransferByEncounterId` |
| `eventPublishAnchor` | `new DepartmentTransferConfirmedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-019` |

**方法契约摘要**：仅 `ON_HOLD` 可确认转科，回到 `IN_PROGRESS` 并激活目标科室位置。**注释锚点**：状态校验 -> 状态迁移 -> 位置生效 -> 事件发布。

---

#### 5.1.18 `dischargeInpatientEncounter(DischargeInpatientEncounterCommand cmd)` → `void`

**命令文件**：`DischargeInpatientEncounterCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `DischargeInpatientEncounterItemCommand` | 单条出院 |
| `items` | `List<DischargeInpatientEncounterItemCommand>` | 批量出院 |
| `operatorId` | `Long` | 操作人 |

`DischargeInpatientEncounterItemCommand` 字段：`encounterId`、`outWayCode`、`dischargedAt`、`summary`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertInpatientDischargeAllowed` |
| `statusTransitionMethod` | `Encounter.transitToOnHold` |
| `repositoryChecks` | `EncounterDiagnosisRepository.existsPrimaryByEncounterId` |
| `eventPublishAnchor` | `new HospitalDischargedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-020` |

**方法契约摘要**：医生开立出院医嘱并经护士校对后，就诊由在院执行推进为待出院（`IN_PROGRESS→ON_HOLD`），写入出院方式；**不**写 `end_time`；**不**收口 `adm_encounter_location` / `adm_encounter_participant`、**不** `releaseBed`（仍占床，释床见 **§5.1.22**）。**注释锚点**：资格校验 -> 状态迁移 -> 字段收口 -> 事件发布。

---

#### 5.1.19 `executeInpatientNurseDischarge(ExecuteInpatientNurseDischargeCommand cmd)` → `void`

**命令文件**：`ExecuteInpatientNurseDischargeCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` / `items` | `ExecuteInpatientNurseDischargeItemCommand` | 单条/批量护士出院 |
| `operatorId` | `Long` | 操作人 |

`ExecuteInpatientNurseDischargeItemCommand` 字段：`encounterId`、`dischargedAt`（可选，写入 `end_time`）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClass(IMP)` + 状态须 `ON_HOLD`（已为 `PENDING` 幂等跳过） |
| `statusTransitionMethod` | `Encounter.transitToPending` |
| `repositoryChecks` | `EncounterRepository.findById` |
| `eventPublishAnchor` | `new HospitalDischargedEvent(...); event.setStatusEnum(PENDING); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-020B` |

**方法契约摘要**：护士办理出院（**AS-IP-NUR-002** IF-IP-NUR-ATD-05）；`ON_HOLD→PENDING`；为 **AS-IP-FIN-003** 出院全结前置。**注释锚点**：状态校验 -> 写 end_time -> 持久化 -> 事件。

---

#### 5.1.19a `cancelInpatientNurseDischarge(CancelInpatientNurseDischargeCommand cmd)` → `void`

**命令文件**：`CancelInpatientNurseDischargeCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` / `items` | `CancelInpatientNurseDischargeItemCommand` | 单条/批量取消办理出院 |
| `operatorId` | `Long` | 操作人 |

`CancelInpatientNurseDischargeItemCommand` 字段：`encounterId`、`restoreToInProgress`（无有效出院嘱时 true）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClass(IMP)` + 状态须 `PENDING`（已为目标态幂等跳过） |
| `statusTransitionMethod` | `Encounter.cancelNurseDischarge(boolean)` |
| `repositoryChecks` | `EncounterRepository` 悲观写锁加载 |
| `eventPublishAnchor` | `new HospitalDischargedEvent(...); event.setStatusEnum(ON_HOLD\|IN_PROGRESS); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-020B2` |

**方法契约摘要**：取消护士办理出院（**AS-IP-NUR-002** IF-IP-NUR-ATD-08）；有有效出院嘱 `PENDING→ON_HOLD` 清 `end_time` 保留 `out_way_code`；无有效出院嘱 `PENDING→IN_PROGRESS` 清 `end_time`/`out_way_code`。**注释锚点**：状态校验 -> 分流目标态 -> 持久化 -> 事件。

---

#### 5.1.20 `completeInpatientFinancialSettlement(CompleteInpatientFinancialSettlementCommand cmd)` → `void`

**命令文件**：`CompleteInpatientFinancialSettlementCommand.java`

**字段全集**：`encounterId`、`settlementTime`（可选）、`operatorId`（可选）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClass(IMP)` + 状态须 `PENDING` |
| `statusTransitionMethod` | **有活动床** `Encounter.transitToCompleted`；**无活动床** 收口活动位置后 `Encounter.transitToDischarged` |
| `repositoryChecks` | `EncounterRepository.findById`；`EncounterLocationRepository`（无床轨） |
| `eventPublishAnchor` | `new InpatientFinancialSettlementCompletedEvent(...); event.setStatusEnum(...); event.setSettlementTime(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-020C` |

**方法契约摘要**：住院收费**出院全结**（**AS-IP-FIN-003**）；**中途结算不得调用**。**有活动床** `PENDING→COMPLETED`；**无活动床**（召回未占床）`PENDING→DISCHARGED` 并收口活动 WARD。

---

#### 5.1.20a `revertInpatientFinancialSettlement(Long encounterId)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClass(IMP)` + 状态须 `COMPLETED` |
| `statusTransitionMethod` | `Encounter.recallFromCompletedToInProgress` |
| `repositoryChecks` | `EncounterRepository` 悲观写锁加载 |
| `eventPublishAnchor` | 无（反结编排由应用层发布支付/医嘱事件） |
| `unitTestCaseId` | `TC-ENC-W-020D` |

**方法契约摘要**：住院**出院全结反结**对称回退（**AS-IP-FIN-004** IF-IP-RVS-01）；`COMPLETED→IN_PROGRESS`；清空 `end_time`/`out_way_code`；**保留**活动床/参与者。**注释锚点**：加载 -> 状态门禁 -> 迁移 -> save。

---

#### 5.1.21 `clearDischargedBed(ClearDischargedBedCommand cmd)` → `void`

**命令文件**：`ClearDischargedBedCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `ClearDischargedBedItemCommand` | 单条清床 |
| `items` | `List<ClearDischargedBedItemCommand>` | 批量清床 |
| `operatorId` | `Long` | 操作人 |

`ClearDischargedBedItemCommand` 字段：`encounterId`、`clearedAt`、`bedLocationId`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClearBedAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `EncounterLocationRepository.findActiveByEncounterIdAndFormEnum` |
| `eventPublishAnchor` | `new TerminalCleaningCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-021` |

**方法契约摘要**：仅 `DISCHARGED` 后执行，关闭活动床位与活动参与者记录。**注释锚点**：状态校验 -> 子实体收口 -> 持久化 -> 事件发布。

---

#### 5.1.22 `cancelInpatientAdmission(CancelInpatientAdmissionCommand cmd)` → `void`

**命令文件**：`CancelInpatientAdmissionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `CancelInpatientAdmissionItemCommand` | 单条取消住院登记 |
| `items` | `List<CancelInpatientAdmissionItemCommand>` | 批量取消住院登记 |
| `operatorId` | `Long` | 操作人 |

`CancelInpatientAdmissionItemCommand` 字段：`encounterId`、`cancelReason`、`cancelAt`、`accountBalance`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertInpatientAdmissionCancelable` |
| `statusTransitionMethod` | `Encounter.transitToCancelled` |
| `repositoryChecks` | `EncounterRepository.findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new InpatientAdmissionCancelledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ENC-W-022` |

**方法契约摘要**：仅 `IMP + PLANNED/PRE_ADMISSION` 且余额为 0 可取消，迁移为 `CANCELLED`。**注释锚点**：前置校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.23 `saveNurseStationPatient(SaveNurseStationPatientCommand cmd)` → `Long`

**命令文件**：`SaveNurseStationPatientCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` | `ItemCommand` | 单条保存项 |
| `items` | `List<ItemCommand>` | 批量保存项 |
| `operatorId` | `Long` | 操作人 |

`ItemCommand` 字段：`encounterId`、`priorityEnum`（可空，有值则更新）、`participants`（`List<ParticipantLine>`，可空）、`savedAt`（可空，默认当前时间）。

`ParticipantLine` 字段：`roleCode`、`practitionerId`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 私有 `assertNurseStationPatientSavable`（`IMP` + `REGISTERED`/`IN_PROGRESS`） |
| `statusTransitionMethod` | **无**（禁止改 `status_enum`） |
| `repositoryChecks` | `EncounterRepository.findAllByIdIn`；`EncounterParticipantRepository.findAllByEncounterIdAndRoleCodeAndStatusEnum` |
| `eventPublishAnchor` | `new NurseStationPatientSavedEvent(encounterId, patientId)` + setter `priorityEnum,statusEnum,savedAt,operatorId` → `DomainEventPublisher.publish` |
| `unitTestCaseId` | `TC-ENC-W-023` |

**方法契约摘要**：更新 `priority_enum`（可选）；按角色结束活动参与者并新建；**禁止**位置与状态迁移；方法级 `@Transactional`。**注释锚点**：门禁 -> 主档/参与者落库 -> 事件发布（B.14）。

---

#### 5.1.24 `recallInpatientDischarge(RecallInpatientDischargeCommand cmd)` → `void`

**命令文件**：`RecallInpatientDischargeCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `item` / `items` | `RecallInpatientDischargeItemCommand` | 单条/批量召回 |
| `operatorId` | `Long` | 操作人 |

`RecallInpatientDischargeItemCommand` 字段：`encounterId`、`wardLocationId`（**必填**，末次 `COMPLETED` WARD）、`recalledAt`（可选）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Encounter.assertClass(IMP)` + **`status_enum = DISCHARGED`** |
| `statusTransitionMethod` | `Encounter.recallFromDischargedToInProgress`（清 `out_way_code`/`end_time`） |
| `repositoryChecks` | `EncounterRepository.findAllByIdIn`；`EncounterLocationRepository` 活动轨迹 |
| `eventPublishAnchor` | `new InpatientDischargeRecalledEvent(...)` + setter `previousStatusEnum,statusEnum,requiresBedAssignment,wardLocationId` → `DomainEventPublisher.publish` |
| `unitTestCaseId` | `TC-ENC-W-024` |

**方法契约摘要**：出院召回本体（**AS-IP-FIN-004** **IF-IP-RCL-01**）；**前置**应用层 **`executeReverseInpatientSettle(..., allowDischarged=true)`** 成功；`DISCHARGED→IN_PROGRESS` + **INSERT** 活动 **WARD**（**不** INSERT 活动床）；`requiresBedAssignment=false`；**不**恢复参与者。

### 5.2 读侧 — `EncounterQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参填 QueryModel 类名**（字段定义见 §4.6）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `loadEncounterById` / `mustGetEncounter` | `Long encounterId` | `EncounterDetailQueryModel` | 按主键加载主档（**不**区分类别）；不存在抛异常 | `TC-ENC-R-001` |
| `mustGetEncounterOfClass` | `Long encounterId, String classEnum, String wrongClassErrorCode, String wrongClassMessage` | `EncounterDetailQueryModel` | 仓储 `findByIdAndClassEnum`；主键存在但类别不符抛业务异常；不存在 → `ENCOUNTER_NOT_FOUND`；**AS-IP-FIN-001**（`IMP`）、**AS-OP-FIN-001** 退号（`AMB`） | `TC-ENC-R-013` |
| `mapAmbEncounterIdByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long, Long>` | 批量 `findAllByIdInAndClassEnum(..., IMP)` 解析 `amb_encounter_id` | `TC-ENC-R-014` |
| `loadEncounterByBusNo` | `String busNo` | `EncounterSimpleQueryModel` | 按业务号唯一查询 | `TC-ENC-R-002` |
| `listActiveEncounterParticipants` | `Long encounterId` | `List<EncounterParticipantQueryModel>` | **仅 ACTIVE**（写前门禁） | `TC-ENC-R-003` |
| `listActiveEncounterParticipantsByType` | `Long encounterId, String roleCode` | `List<EncounterParticipantQueryModel>` | **仅 ACTIVE** + 角色 | `TC-ENC-R-004` |
| `listDisplayEncounterParticipants` / `listDisplayParticipantEntitiesByEncounterIds` | `Long` / `Collection<Long>` | `List<EncounterParticipantQueryModel>` / `List<EncounterParticipant>` | 展示：同角色 ACTIVE 优先，否则末次 COMPLETED（`end_time` 最大） | `TC-ENC-R-003a` |
| `listDisplayEncounterParticipantsByType` | `Long encounterId, String roleCode` | `List<EncounterParticipantQueryModel>` | 展示单角色变体 | `TC-ENC-R-003b` |
| `mapDisplayWardLocationIdByEncounterIds` / `mapDisplayBedLocationIdByEncounterIds` | `Collection<Long>` | `Map<Long, Long>` | 展示位置：活动优先，否则末次 COMPLETED WARD/BED | `TC-ENC-R-016b` |
| `listEncounterLocations` / `getEncounterLocationInfo` | `Long encounterId` | `List<EncounterLocationQueryModel>` / `EncounterLocationInfoQueryModel` | 位置轨迹按时间排序；当前活动位置查询 | `TC-ENC-R-005` |
| `listEncounterDiagnoses` | `Long encounterId` | `List<EncounterDiagnosisQueryModel>` | 诊断关联读取 | `TC-ENC-R-006` |
| `queryEncounterProjections` | `EncounterProjectionQuery` | `Page<EncounterProjectionQueryModel>` | 直接查 `adm_encounter` 分页组装 | `TC-ENC-R-007` |
| `checkOutpatientCompletable` | `Long encounterId` | `EncounterCompletionCheckQueryModel` | 完诊前组合校验 | `TC-ENC-R-008` |
| `checkOutpatientDuplicateRegistration` | `OutpatientDuplicateCheckQuery` | `OutpatientDuplicateCheckQueryModel` | 门诊防重判断 | `TC-ENC-R-009` |
| `assertAmbForInpatientReq` | `Long ambEncounterId` | `void`（失败抛异常） | 源 AMB 可转住院 | `TC-ENC-R-010` |
| `findOpenInpatientLine` | `Long ambEncounterId` | `OpenInpatientLineQuery` | 按 amb 查未取消住院线 | `TC-ENC-R-011` |
| `existsOpenInpatientLine` | `Long ambEncounterId` | `boolean` | `findOpenInpatientLine.exists` | `TC-ENC-R-012` |
| `findPatientIdsWithActiveInpatientStay` | `Collection<Long> patientIds` | `Set<Long>` | 批量：有在院 IMP 的 patientId（排除取消/错误录入/完诊/已完成；**AS-OP-FIN-004 / BR-OP-DCL-032**） | `TC-ENC-R-012a` |
| `mapActiveBedEncounterIdByLocationIds` | `Collection<Long> locationIds` | `Map<Long, Long>` | 床位→占床就诊 | `TC-ENC-R-015` |
| `requireActiveLocationId` | `encounterId, formEnum, errorCodes…` | `Long` | 解析唯一活动位置 | `TC-ENC-R-016` |
| `findLastCompletedWardLocationIdByEncounterId` | `Long encounterId` | `Optional<Long>` | `adm_encounter_location`：`form_enum=WARD` + `status_enum=COMPLETED`，按 `end_time` 取最大 `location_id`；**AS-IP-NUR-002** ATD-07 Pre | `TC-ENC-R-016a` |
| `mapMedTypeByEncounterDiagnosisIds` | `Collection<Long> diagnosisIds` | `Map<Long, String>` | 诊断→医保类别 | `TC-ENC-R-017` |
| `mapBusNoByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long, String>` | IMP 线批量业务号 | `TC-ENC-R-032` |
| `mapPatientIdByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long, Long>` | IMP 线批量患者主键 | `TC-ENC-R-033` |
| `mapPriorityEnumByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long, String>` | IMP 线批量病情优先级 | `TC-ENC-R-034` |
| `listEncounterIdsByBusNoKeyword` | `String keyword` | `List<Long>` | 发药单：业务号反查就诊 | `TC-ENC-R-035` |
| `listEncounterIdsByPatientIds` | `Collection<Long> patientIds` | `List<Long>` | 发药单：患者反查就诊 | `TC-ENC-R-036` |
| `requireEncounterEntityOfClass` | 同 `mustGetEncounterOfClass` 入参 | `Encounter` | 读侧门控实体 | `TC-ENC-R-037` |
| `listEncounterIdsForPractitionerWorkloadScope` | `encounterClassCode`, `organizationIds`；可选 `windowFrom`/`windowTo` | `List<Long>` | 工作量/挂号医生候选就诊 scope（日窗相交；**AS-RPT-001/007**） | `TC-ENC-R-038` |
| `listEncounterIdsForNurseWorkloadScope` | `encounterClassCode`；可选 `windowFrom`/`windowTo` | `List<Long>` | 护士/耗材报表就诊 scope（日窗相交；**AS-RPT-002/005/006**） | `TC-ENC-R-038a` |
| `mapPractitionerWorkloadEncounterContextByIds` | `Collection<Long> encounterIds` | `Map<Long, PractitionerWorkloadEncounterContextQueryModel>` | 挂号号源分组上下文（应用层未选科室时跨 org 合并） | `TC-ENC-R-039` |
| `filterEncounterIdsByActiveRegistrationDoctor` | `Collection<Long> candidateEncounterIds`, `Long practitionerId` | `Set<Long>` | 活动 REGISTRATION_DOCTOR 过滤（**AS-RPT-001** **BR-RPT-WKL-005**） | `TC-ENC-R-040` |
| `filterEncounterIdsByActiveAdmitterDoctor` | `Collection<Long> candidateEncounterIds`, `Long practitionerId` | `Set<Long>` | 活动 ADMITTER 过滤（**AS-RPT-007** 接诊医生筛） | `TC-ENC-R-040a` |
| `countOutpatientRegistrationDetailReportStats` | `OutpatientRegistrationReportPageQuery` | `OutpatientRegistrationDetailStatsQueryModel` | 挂号明细页脚挂/退/有效（**AS-RPT-007**） | `TC-ENC-R-040b` |
| `existsPaidOutpatientEncounterForDepartmentOnCalendarDay` | `patientId, organizationId, classEnums, statusEnums, dayStart, dayEnd` | `boolean` | 已付门诊防重辅助 | `TC-ENC-R-020` |
| `pageMedicalTechWorkstationQueue` | `MedicalTechWorkstationQueueQuery`（`loginTechOrgId` + `encounterScope` + 日期/关键字/`pendingDeviceOnly`/`executionQueueFilter`） | `Page<Encounter>` | Spec：`EncounterContextCriteriaSupport#medicalTechWorkstationSpecification`（TECH 诊疗有活硬门槛；可选待发耗材；可选待执行/已执行分桶 **BR-MT-EXE-023**；**AS-MT-EXE-001**） | `TC-ENC-R-041` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用分页或批量 -> 组装 QueryModel（无写库）。

### 5.4 读侧 — 就诊上下文单条装载（`EncounterContextQueryService` · 跨模块 bridge）

> **落点**：`zxplat-domain` · `com.zxplat.domain.administration.encounter.service.EncounterContextQueryService`。  
> **消费方**：`zxplat-module-chs-jilin` · `ChsOutpatientInsuranceContextLoader` / `ChsInpatientInsuranceContextLoader`。  
> **与 §5.3 区别**：§5.3 为 `health-application` 分页队列；本节为 Extension 链**单就诊**薄上下文，允许在 domain 内跨聚合组装。

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `mustGetOutpatientContextByEncounterId` | `Long encounterId` | `OutpatientEncounterContextQueryModel` | `EncounterQueryService` + Patient/Org/Practitioner/Service/Location 批量回填 | `TC-ENC-R-041` |
| `mustGetInpatientContextByEncounterId` | `Long encounterId` | `InpatientEncounterContextQueryModel` | 同上（IMP 类别过滤） | `TC-ENC-R-042` |
| `getOutpatientContextByEncounterId` | `Long encounterId` | `Optional<OutpatientEncounterContextQueryModel>` | 不存在返回 empty | `TC-ENC-R-043` |
| `getInpatientContextByEncounterId` | `Long encounterId` | `Optional<InpatientEncounterContextQueryModel>` | 不存在返回 empty | `TC-ENC-R-044` |

**读方法注释锚点**：`findOptionalEncounterEntityById` → 类别过滤 → 跨聚合批量查询 → 组装 `*EncounterContextQueryModel`（无写库）。

### 5.3 读侧 — 就诊上下文（`health-application` · `EncounterContextReader`）

> **非** `zxplat-domain` 交付物；与 DM **§6.2.18～§6.2.20**、**`10-就诊上下文读组装-禁止领域层Support.md`** 对齐。AppService 注入 Facade；实现类 `application.shared.encounter.*Loader` + `application.shared.inpatient.*`（如 `AdmissionPrimaryDiagnosisAssembler`）+ `application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler`（Loader 内 enrichment）。

| 方法 | 入参 | 出参 | 实现类 | `unitTestCaseId` |
| ---- | ---- | ---- | ------ | ---------------- |
| `pageOutpatientEncounterContexts` | `OutpatientEncounterPageQuery` | `Page<OutpatientEncounterContextQueryModel>` | `OutpatientEncounterContextLoader` | `TC-ENC-APP-R-021` |
| `pageNurseStationOutpatientQueue` | `NurseStationOutpatientQueueQuery`（扩展 `AbstractPageQuery`；可选 `orgId`→就诊 `organization_id`，空=全部；`pendingDeviceOnly=true` 时叠加待发耗材 EXISTS，分页前生效） | `Page<OutpatientEncounterContextQueryModel>` | 同上 | `TC-ENC-APP-R-022` |
| `getOutpatientContextByEncounterId` / `mustGetOutpatientContextByEncounterId` | `Long encounterId` | `Optional` / 模型 | 同上 | `TC-ENC-APP-R-023` |
| `listOutpatientContextsByEncounterIdsOrdered` | `List<Long>` | `List<OutpatientEncounterContextQueryModel>` | 同上 | `TC-ENC-APP-R-024` |
| `pageInpatientRegQueue` / `pageInpatientPrepayQueue` / `pageInpatientNurseStationQueue` / `pageInpatientDoctorStationQueue` / `pageInpatientSettleQueue` | `InpatientEncounterPageQuery` | `Page<InpatientEncounterContextQueryModel>` | `InpatientEncounterContextLoader` | `TC-ENC-APP-R-025` |
| `pageInpatientEncounterContexts` / `listInpatientEncounterContexts` | `InpatientEncounterPageQuery` | `Page` / `List` | 同上 | `TC-ENC-APP-R-026` |
| `getInpatientContextByEncounterId` / `mustGetInpatientContextByEncounterId` | `Long encounterId` | `Optional` / 模型 | 同上 | `TC-ENC-APP-R-027` |
| `listInpatientContextsByEncounterIdsOrdered` | `List<Long>` | `List<InpatientEncounterContextQueryModel>` | 同上 | `TC-ENC-APP-R-028` |
| `pageClinicRegister` | `ClinicRegisterPageQuery` | `Page<ClinicRegisterQueryModel>` | `ClinicRegistrationLoader` | `TC-ENC-APP-R-029` |
| `pageRegistrationSlip` | `RegistrationSlipPageQuery` | `Page<RegistrationSlipQueryModel>` | 同上 | `TC-ENC-APP-R-030` |
| `getRegistrationSlipByEncounterId` | `Long encounterId` | `Optional<RegistrationSlipQueryModel>` | 同上 | `TC-ENC-APP-R-031` |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-ENC-001 | 注册时校验患者/类别/组织三要素必填 |
| BR-ENC-002 | 开始时间与结束时间在状态迁移节点写入并校验区间合法 |
| BR-ENC-003 | 终止态不可逆，在聚合方法统一拦截；**IMP 出院召回**（§6.1.25）经 `recallFromDischargedToRegistered` 豁免 |
| BR-ENC-004 | 完诊/出院前必须存在负责医生参与者 |
| BR-ENC-005 | 同形态位置仅允许一条活动记录 |
| BR-ENC-006 | 要求主诊时必须存在唯一**未软删**主诊关联（`findFirstByEncounterIdAndPrimaryFlagTrueAndIsDeletedFalse`） |
| BR-ENC-007 | 取消与删除分离，仅允许逻辑删除 |
| BR-ENC-008 | 写操作使用乐观锁版本校验 |
| BR-ENC-018 | `findTopByAmbEncounterId…` + 写侧 `assertNoOpenInpatientForAmb` |
| BR-ENC-019 | `BusinessSequenceService.getSeqByDay(INPATIENT_ENCOUNTER)` 与门诊 `ENCOUNTER` 分离 |
| BR-ENC-009 | 重复状态迁移按幂等成功处理 |
| BR-ENC-010 | 所有写操作先查后改 |
| BR-ENC-011 | 同日同患者同号源门诊防重 |
| BR-ENC-012 | 未指定医生时按科室默认医生策略 |
| BR-ENC-013 | `busNo` 全局唯一且不可修改 |
| BR-ENC-014 | 诊断排序唯一 + **活动**主诊唯一；软删关联须卸 `primary_flag`；冲突校验不计已软删行 |
| BR-ENC-015 | 参与者时间必须落在就诊时间区间 |
| BR-ENC-016 | 新增活动位置前必须收口旧活动位置 |
| BR-ENC-017 | 原因记录只追加，不物理删除 |
| BR-ENC-020 | 场景化读侧须按 `encounterId` + `classEnum` 在仓储层隔离（`findByIdAndClassEnum` / `mustGetEncounterOfClass`）；住院登记 `IMP`、门诊退号 `AMB`；禁止仅 `findById` 命中后再内存筛类别 |
| BR-ENC-021 | `saveAdmissionDx` → `newAdmissionDiagnosisAssocItem` 写入的 `EncounterDiagnosis.diagTypeCode` 固定 `IptPatientDiseaseDiagType.ADMISSION.getCode()`（`31`） | 入院/登记诊断类型 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `OutpatientRegistrationPlannedEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造器最小锚点 `encounterId,patientId,serviceTypeId,organizationId,busNo`；**另含 setter 快照**（`slipNo`、`patient*`、`pyStr`、`wbStr`、`organizationName`、`serviceTypeName`、`startTime`、`displayOrder`、`registrar*`、`classEnum`、`registrationDoctorId`、`registrationDoctorName` 等） | `registerOutpatientEncounter` |
| `OutpatientReceptionStartedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,doctorId,receptionTime` + **`doctorName`（setter，与QueryService 字段同名）** | `startOutpatientReception` |
| `OutpatientEncounterOnHoldEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,leaveReason` | `markOutpatientLeave` |
| `OutpatientEncounterCompletedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,patientId,endTime` | `completeOutpatientEncounter` |
| `OutpatientRegistrationCancelledEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造器最小锚点 `encounterId,patientId,cancelReason`；**发布前 setter**：`statusEnum`（退号=`CANCELLED`；未收费取消=`ENTERED_IN_ERROR`）、`cancelledAt` | `cancelOutpatientRegistration` / `cancelPlannedOutpatientRegistration` |
| `EncounterDiagnosisAssociationUpsertedEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造锚点 `encounterId,conditionId,primaryFlag,rank`；**发布前 setter**：`diagnosisJson`（当前就诊未软删关联 + 诊断事实/定义拼装的 JSON 数组字符串，与医生站 `parseDiagnosisJson` 元素口径一致）、`conditionName`（主诊断名称摘要，供挂号列表等读侧） | `upsertEncounterDiagnosisAssociation`、`batchUpsertEncounterDiagnosisAssociation` |
| `EncounterDiagnosisAssociationRemovedEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造锚点 `encounterId,conditionId`；**发布前 setter**：同上 `diagnosisJson`、`conditionName`（移除后重算，可为 `[]`/空） | `removeEncounterDiagnosisAssociation` |
| `InpatientAdmissionRequestedEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造器 `encounterId,patientId,targetDepartmentId`；**setter** `ambEncounterId,busNo,classEnum,statusEnum,inWayCode,priorityEnum,wardId`；聚合类型 `INPATIENT_ENCOUNTER` | `requestInpatientAdmission` |
| `InpatientPreAdmissionStartedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,preAdmissionItems` | `startPreAdmission` |
| `InpatientAdmissionCompletedEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造器 `aggregateId,patientId,departmentId,registrarId`；**setter** `statusEnum,startTime,busNo,wardId`（`wardId`←`item.wardLocationId`）、`wardName`（可选） | `registerInpatientEncounter`、`registerDirectInpatientEncounter` |
| `InpatientBedAssignedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,locationId,bedCode,patientId,patientName,patientGenderText,patientAgeText,attendingDoctorId,admissionTime` | `assignInpatientBed` |
| `BedTransferredEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,fromLocationId,toLocationId,effectiveAt` | `confirmBedTransfer`、`correctInpatientAdmissionDepartment`（仅换床） |
| `DepartmentTransferInitiatedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,fromDepartmentId,toDepartmentId` | `initiateDepartmentTransfer` |
| `DepartmentTransferConfirmedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,targetDepartmentId,effectiveAt` | `confirmDepartmentTransfer` |
| `HospitalDischargedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,patientId,outWayCode,endTime` + **`statusEnum`（setter）** | `dischargeInpatientEncounter`（`ON_HOLD`）、**`executeInpatientNurseDischarge`**（`PENDING`） |
| `InpatientFinancialSettlementCompletedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,patientId` + **`statusEnum=COMPLETED`、`settlementTime`（setter）** | **`completeInpatientFinancialSettlement`**（**AS-IP-FIN-003**） |
| `TerminalCleaningCompletedEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,bedLocationId,cleanedAt` | `clearDischargedBed` |
| `InpatientAdmissionCancelledEvent` | `com.zxplat.domain.share.events.administration.encounter` | `encounterId,cancelReason` | `cancelInpatientAdmission` |
| `NurseStationPatientSavedEvent` | `com.zxplat.domain.share.events.administration.encounter` | 构造器 `encounterId,patientId`；**setter** `priorityEnum,statusEnum,savedAt,operatorId` | `saveNurseStationPatient` |
| `InpatientDischargeRecalledEvent` | 同上 | 构造器 `encounterId,patientId`；**setter** `previousStatusEnum,statusEnum,requiresBedAssignment,wardLocationId` | **`recallInpatientDischarge`**（**FIN-004 IF-IP-RCL-01**） |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。**就诊诊断关联类事件**的 **`diagnosisJson`** / **`conditionName`** 为**可选兼容载荷**（**2026-06**：无读模型投影器消费）；读侧统一 **`EncounterDiagnosisSnapshotAssembler#buildSnapshot`** / Loader enrichment（**B.6.2**），**禁止**在领域 `EncounterQueryService` 内做跨域诊断拼装。

---

## 8. 删除与可见性契约

- Encounter 聚合删除语义为逻辑删除，禁止物理删除。
- 聚合根逻辑删除后，参与者/位置/诊断/原因子表默认不可见，历史用于审计。
- 读侧默认查询 `isDeleted=false`，审计查询需显式开启含删除模式。
- 终止态（`DISCHARGED/CANCELLED/ENTERED_IN_ERROR`）后禁止恢复为非终态；**例外**：**IMP** `recallInpatientDischarge` 允许 `DISCHARGED→IN_PROGRESS`（**BR-ENC-003** / §5.1.24）。
- 子表更新默认全量对齐 + 软删旧行，禁止整表物理清空重建。

---

## 9. 框架优先适配规则

- 软删、审计字段、工作单元事务、`beforeCommit` 事件发布遵循项目框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认引入无收益接口层。
- 一方法一 Command 且单文件定义，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口；复杂聚合查询在查询服务实现。
- 分页查询禁止先分页后内存过滤，保证 total 与结果口径一致。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 主表/子表字段逐项对齐（含必填与语义）
- [ ] §5.1 写方法覆盖 DM §6.1 全部 **29** 项（含 **5.1.9b**、**5.1.16a**、**5.1.18a**），方法名与 Java 完全一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 领域 `EncounterQueryService` 全部方法；§5.3 覆盖 DM §6.2.18～§6.2.20 应用层 Facade
- [ ] §6 BR 映射覆盖 BR-ENC-001 至 BR-ENC-017
- [ ] §7 事件表覆盖 DM §5.1 全部 21 条事件
- [ ] 软删、幂等、分页一致性、更新不静默新建均有明确契约
- [ ] 全文无占位语、无待确认语、无半成品段落

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-002-Encounter-就诊.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享枚举与事件包可用：`com.zxplat.domain.share.enums`、`com.zxplat.domain.share.events.administration.encounter`

### 11.2 DoD

- 写侧 **24** 个方法全部落地，方法签名与命令文件命名与 §5 一致。
- 聚合根与子实体字段、类型、约束与 §4 一致，软删策略完整实现。
- 所有事件按 §7 在 `beforeCommit` 发布，载荷字段不少于最小载荷。
- 关键 BR 至少一条对应一个测试用例 ID，`unitTestCaseId` 可追溯。
- 读侧方法实现数据库侧分页过滤，QueryModel 与 §4.6 对齐。

### 11.3 失败处理

- 任一方法契约无法落地（字段缺失、状态机冲突、事件缺失）立即停机并回退文档会话。
- 若 DM 白名单方法变更，先更新 SDD 再进入代码会话。
- 若关键 BR 未覆盖测试或事件发布不完整，DoD 失败不得合并。

---

## 12. 体检扩展：`EncounterClass.PE` 成码契约

> **DM 真源**：[DM-ADM-002 §4.1](./DM-ADM-002-Encounter-就诊.md#41-就诊类别encounterclass) · PEIS [04 §3](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md#三encounterclass_enum--pe健康体检)

| 项 | 约定 |
|----|------|
| **枚举** | `EncounterClass.PE`，`code="PE"`，`info="健康体检"` |
| **Java 包** | `com.zxplat.domain.share.enums.EncounterClass`（与 AMB/IMP 同枚举） |
| **持久化** | `adm_encounter.class_enum` 存 **字符串 code** `"PE"`（与 DM §4.1 一致） |
| **PEIS 硬约束** | **禁止**为 PE 新增专用 `status_enum`（如 PENDING_MASTER）；进度见 DM-PEIS-008 |
| **成码 DoD** | 枚举含 PE ✅（2026-07-19）；REG 创建 Encounter 写 `classEnum=PE`；Query 可按 class 过滤 |
| **Flyway** | 若库内无 CHECK 约束，**不强制** DDL；仅 Java + 数据口径 |

**PEIS REG 创建**：`PeisRegistrationOrchestrator` 在到检/登记时创建或激活 `Encounter`，`classEnum=PE`，`statusEnum` 走通用值（如 `planned` / `in-progress`）。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（含 **5.1.9b** 等增量）
- [ ] §4 字段表覆盖 DM §3 主表与子表，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（21）
- [ ] 全文无 YAML、无 `sdd_status`、无 `dm_code`、无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（可进入代码会话）** |

---

## SDD 文档修订记录

| 修订 | 日期 | 说明 |
| --- | --- | --- |
| charge_intent 预填 | 2026-10-14 | §4.2 `chargeIntent`：供登记未开户预填费用性质（医保默认市医保）；对齐 **DM v1.57** / **AS-IP-FIN-001 BR-IP-REG-024**（代码先行回写） |
| 医技队列分桶 | 2026-10-14 | §5.2 `pageMedicalTechWorkstationQueue` 增 `executionQueueFilter`（待执行/已执行；门诊项目+耗材，住院仅项目）；对齐 DM **v1.50** / **AS-MT-EXE-001 BR-MT-EXE-023**（代码先行回写） |
| 软删卸主诊 | 2026-10-14 | §5.1.10 / §5.1.16 `repositoryChecks` / **BR-ENC-006**/**BR-ENC-014**：软删诊断关联同步卸主诊；主诊校验仅认未软删行。对齐 **DM v1.55**（代码先行回写） |
| 医技队列 | 2026-09-19 | §5.2 增 `pageMedicalTechWorkstationQueue`；对齐 DM **v1.48** / **AS-MT-EXE-001**（关闭 **GAP-MT-EXE-001**） |
| 未占床房-床展示 | 2026-08-17 | 无活动床 `bedName`→「原{房}-{床}（未占床）」；对齐 DM **v1.44** |
| 展示参与者/位置 | 2026-08-17 | §5.2 增 `listDisplay*` / `mapDisplay*Location*`；活动优先、无则末次 COMPLETED；对齐 DM **v1.43**（清床收口与召回不恢复后卡片仍回显主治/责任护士） |
| in_way 对齐 adm_way | 2026-08-13 | §4.2：`inWayCode` 与医保 adm_way / 病案 adm_way_code 同口径（1/2/3/9）；`admitSourceCode` 为入院类型 |
| 切换主诊 | 2026-07-26 | 新增 §5.1.9b `switchPrimaryEncounterDiagnosis`；自检覆盖 **29** 项；对齐 DM **v1.38**、**AS-OP-DOR-002 IF-DOR-09A**、**AS-IP-DOR-002 IF-IP-DOR-07** |
| 门诊取消分流 | 2026-07-16 | 新增 §5.1.7a `cancelPlannedOutpatientRegistration`→`ENTERED_IN_ERROR`；§5.1.7 退号仍 `CANCELLED`；事件表区分两终态；对齐 DM **v1.35**、**AS-OP-FIN-001 v2.27**。 |
| 出院召回 redesign | 2026-07-16 | §5.1.24 `DISCHARGED→IN_PROGRESS`+WARD（无床）；**FIN-004 IF-IP-RCL-01**；§5.1.20 无床全结→`DISCHARGED`；对齐 DM **v1.34** |
| 挂号参与者角色 | 2026-06-12 | §5.1.1 增补 `participantWrite`：`registerOutpatientEncounter` 指定医师写 **`REGISTRATION_DOCTOR`**，对齐 DM **v1.25** 与 **AS-OP-DOR-001** **BR-DOR-009**。 |
| DM-SDD M1 | 2026-05-28 | 补 **§5.1.16a** `registerDirectInpatientEncounter`；§5.1.16 字段 `wardId` 对齐 Java；对齐 DM **v1.24.1** |
| ReadModel 废止 | 2026-05-28 | 对齐 DM **v1.24**：§5.2 仅保留薄 `EncounterQueryService`；新增 §5.3 `EncounterContextFacade`（废止 InpatientInfo/OutpatientInfo RM）；§7 诊断展示改应用 Assembler |（已迁至 EncounterContextReader / application.shared.encounter）
| 入院诊断类型 | 2026-05-20 | 对齐 DM **v1.21**：§4.4.3 `diagTypeCode`→`String`；§5.1.14 增补 `saveAdmissionDx`/`newAdmissionDiagnosisAssocItem` 与 **BR-ENC-021**（`diagTypeCode=31`）。 |
| 住院结算链 | 2026-05-26 | 新增 §5.1.22 `executeInpatientNurseDischarge`、§5.1.23 `completeInpatientFinancialSettlement`；§5.1.24～26 顺延编号；§7 增补 `InpatientFinancialSettlementCompletedEvent`（**AS-IP-FIN-003**） |
| PE class | 2026-06-27 | 新增 **§12** `EncounterClass.PE` 成码契约（PEIS P0） |
| 护士站保存患者信息 | 2026-05-20 | 新增 §5.1.24 `saveNurseStationPatient` / `SaveNurseStationPatientCommand` / `NurseStationPatientSavedEvent`；对齐 DM **v1.13**、**AS-IP-NUR-001** IF-IP-NUR-04。 |
| 登记诊断分工 | 2026-05-19 | §5.1.16：对齐 DM **v1.12** / **AS-IP-FIN-001** **BR-IP-REG-007**——有申请登记跳过已有主诊的 `saveAdmissionDx`；§5.1.16a 直登唯一写诊断。 |
| AdmissionConditionItem | 2026-05-18 | 入院诊断命令项：`AdmissionDxItem` → **`AdmissionConditionItem`**（`command/AdmissionConditionItem.java`）；§5.1.14/§5.1.16 字段类型与 `saveAdmissionDx` 方法名对齐代码。 |
| 退号不含待挂号 | 2026-05-02 | §5.1.7 `cancelOutpatientRegistration`：`assertCancelable` 门诊侧**排除 `PLANNED`**，与 DM §5.2 事件表、§6.1.7 及 `AS-OP-FIN-001` BR-REG-008 一致。 |
| 对齐 DM v1.2 | 2026-05-02 | 新增 **§5.1.2 `completeOutpatientRegistration`**；§5.1.3～§5.1.23 顺延编号；§5.1.3/§5.1.4 契约与 `assertTriagedFromPlanned`/`assertReceptionAllowed` 一致；§5.1.7 退号不变式改为 `assertCancelable`。文首锁定 DM **v1.2**。 |
| 对齐 DM v1.1 | 2026-05-02 | §5.1.1 / §5.1.3 / §5.1.8 命令项字段与 `eventPublishAnchor` 对齐工程；§7 三事件载荷列补充 `doctorName` / 计划事件快照 setter；§7 段末脚注关联 DM §5.1 命名约定。 |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-10-14 | §5.1.10 / BR-ENC-006/014：软删卸主诊 + 活动主诊校验；对齐 **DM v1.55**（代码先行回写） |
| 2026-10-13 | §5.2：增 `findPatientIdsWithActiveInpatientStay`；对齐 **DM v1.51**、**AS-OP-FIN-004 / BR-OP-DCL-032**（代码先行回写） |
| 2026-10-12 | §5.1.1：命令增 `skipSameDaySameDeptDuplicateCheck`；对齐 **DM v1.49**、**AS-OP-FIN-001 v2.43**（代码先行回写） |
| 2026-07-29 | **报表性能**：§5.2 `listEncounterIdsForPractitionerWorkloadScope` / `listEncounterIdsForNurseWorkloadScope` 增补业务日窗重载；对齐 **DM v1.38** |
| 2026-07-11 | **CHS bridge**：§5.4 `EncounterContextQueryService` 单条就诊上下文（**zxplat-module-chs-jilin**）；对齐 **DM v1.32** |
| 2026-07-03 | **工作量读扩展**：§5.2 增补 `listEncounterIdsForPractitionerWorkloadScope` / `mapPractitionerWorkloadEncounterContextByIds` / `filterEncounterIdsByActiveRegistrationDoctor`；对齐 **DM v1.30** §6.2.30～6.2.31 与 **AS-RPT-001** |
| 2026-06-16 | **中医 Tab（AS-OP-DOR-010）**：§5.1.9a `batchUpsertTcmEncounterDiagnosisAssociation`；对齐 **DM v1.26** |
| 2026-05-28 | **DM-SDD M1**：§5.1.16a 直登登记；§5.2/§5.3 与 DM §6.2 及 `EncounterContextFacade` 白名单对齐 |（已迁至 EncounterContextReader / application.shared.encounter）
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
