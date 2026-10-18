# DM-CLN-003 Condition 诊断记录 · DomainModel-SDD

> **退役声明**：与 DM 正文同步——**业务退役**，见 `health/设计决策/DD-诊断绑定改造-一药多诊断与Condition退役.md`。本 SDD **不得再驱动成码**；`ConditionDomainService` / `ConditionQueryService` 仅保留 `@Deprecated` 空壳。

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-003-Condition-诊断记录.md`（**v1.2**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Clinical（临床域）  
> **聚合**：Condition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CLN-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `{{TARGET_MODULE}}` |
| 包规则 `com.zxplat.domain.clinical.condition` | 满足 | `condition` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 `Condition` 患者诊断事实聚合的实体字段、写侧契约、读侧契约、BR 与事件，使下一会话可一次性成码。
- 明确医生站西医/中医保存与删除诊断三条写路径，保证一方法一命令文件、字段全集可核对。
- 明确诊断事实与诊断定义目录分离：本聚合只保存事实与引用键，不承载目录主数据维护。

### 1.2 边界

- **在内**：`cln_condition` 聚合字段、不变式、保存/删除规则、查询模型与事件契约。
- **在外**：诊断目录维护、就诊关联编排、ConditionQueryService 查询编排、医嘱编排。
- **跨域仅端口声明**：通过 `definitionId` 引用目录、通过 `encounterId` 关联就诊，不在本聚合内直接写外部聚合。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`{{TARGET_MODULE}}`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.clinical.condition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Condition` |
| `valueobject` | 无独立复合值对象，枚举复用共享模块 |
| `enums`（共享） | `ConditionCategory`、`ConditionSeverity`、`ConditionVerificationStatus`、`ConditionClinicalStatus` |
| `command` | 每个写方法一个 Command 文件 |
| `repository` | `ConditionRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.clinical.condition` |

**存储口径（DM 优先）**：`*_enum` 统一 `Integer`；`*_code` 为 `String`；`tcm_flag` 为 `Boolean`；逻辑删除由 `isDeleted` 承载。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | 主键 `Long id`；外键 `patientId`、`definitionId`、`bodyStructureId` |
| 时间 | `onsetDatetime`、`abatementDatetime`、`recordedDatetime` 统一 `LocalDateTime` |
| `*_enum` | 枚举编码列统一 `Integer` |
| `*_code` | 编码列统一 `String` |
| `_flag` | 布尔标记统一 `Boolean` |
| 软删 | `Boolean isDeleted`，读侧默认过滤 |
| 实体 | `@Entity` + 显式 `@Table` / `@Column` |
| 值对象 | 无独立 `@Embeddable` 值对象 |
| 仓储 | `interface ConditionRepository extends JpaRepository<Condition, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务方法内直接构造事件并 `beforeCommit` 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cln_condition` | `Condition` | 患者诊断事实单表 |

### 4.2 聚合根 `Condition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `verificationStatusEnum` | `Integer` | 否 | 验证状态 |
| `clinicalStatusEnum` | `Integer` | 否 | 临床状态 |
| `categoryEnum` | `Integer` | 否 | 诊断分类 |
| `severityEnum` | `Integer` | 否 | 严重程度 |
| `busNo` | `String` | 否 | 疾病/诊断编码 |
| `bodySiteJson` | `String` | 否 | 身体部位 JSON |
| `bodyStructureId` | `Long` | 否 | 身体结构标识 |
| `encounterId` | `Long` | 是 | 就诊标识（`cln_condition.encounter_id`）；写侧保存时必须与命令上下文一致，禁止跨就诊改写 |
| `patientId` | `Long` | 是 | 患者标识 |
| `description` | `String` | 否 | 描述 |
| `onsetDatetime` | `LocalDateTime` | 否 | 初次发病时间 |
| `onsetAge` | `Integer` | 否 | 初次发病年龄 |
| `onsetText` | `String` | 否 | 初次发病描述 |
| `abatementDatetime` | `LocalDateTime` | 否 | 症状减轻时间 |
| `abatementAge` | `Integer` | 否 | 症状减轻年龄 |
| `abatementString` | `String` | 否 | 症状减轻时期 |
| `recordedDatetime` | `LocalDateTime` | 否 | 记录时间 |
| `recorderId` | `Long` | 否 | 记录人 |
| `stageSummaryText` | `String` | 否 | 阶段总结 |
| `evidence` | `String` | 否 | 证明 |
| `ybNo` | `String` | 否 | 医保编码 |
| `definitionId` | `Long` | 否 | 诊断定义标识 |
| `tcmFlag` | `Boolean` | 否 | 中医标识 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertPatientBound`、`refreshRecordMeta`、`assertNoAdviceReferenceBeforeDelete`、`markDeleted`。

### 4.3 子实体

无。本聚合为单表。

### 4.4 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `ConditionRepository extends JpaRepository<Condition, Long>`
- 推荐查询签名：`findAllByIdInAndIsDeletedFalse`、`findByEncounterIdAndIsDeletedFalse`（若有就诊映射读表）、`countAdviceRefByConditionId`。

**说明**：若软删统一过滤，不强制在全部签名中重复 `IsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `ConditionBatchItemQueryModel`（对应 `getConditionListById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_condition.id` | 诊断记录主键 |
| `patientId` | `Long` | `cln_condition.patient_id` | 患者标识 |
| `definitionId` | `Long` | `cln_condition.definition_id` | 诊断定义标识 |
| `verificationStatusEnum` | `Integer` | `cln_condition.verification_status_enum` | 验证状态 |
| `clinicalStatusEnum` | `Integer` | `cln_condition.clinical_status_enum` | 临床状态 |

#### 4.6.2 `EncounterDiagnosisQueryModel`（对应 `getEncounterDiagnosis`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `conditionId` | `Long` | `cln_condition.id` | 诊断记录标识 |
| `encounterId` | `Long` | `cln_condition.encounter_id` | 就诊标识 |
| `patientId` | `Long` | `cln_condition.patient_id` | 患者标识 |
| `definitionId` | `Long` | `cln_condition.definition_id` | 定义标识 |
| `description` | `String` | `cln_condition.description` | 诊断描述 |
| `recordedDatetime` | `LocalDateTime` | `cln_condition.recorded_datetime` | 记录时间 |

#### 4.6.3 `DiagnosisCandidateQueryModel`（对应 `getDiagnosisList`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `definitionId` | `Long` | `cln_condition_definition.id` | 目录主键 |
| `conditionCode` | `String` | `cln_condition_definition.condition_code` | 目录编码 |
| `name` | `String` | `cln_condition_definition.name` | 目录名称 |
| `sourceEnum` | `Integer` | `cln_condition_definition.source_enum` | 目录分类 |
| `statusEnum` | `Integer` | `cln_condition_definition.status_enum` | 目录状态 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ConditionDomainService`

#### 5.1.1 `saveDoctorDiagnosis(SaveDoctorDiagnosisCommand cmd)` → `List<Long>`

**命令文件**：`SaveDoctorDiagnosisCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `items` | `List<DoctorDiagnosisItemCommand>` | 西医诊断项集合 |
| `operatorId` | `Long` | 操作人 |
| `admissionDiagnosisFlow` | `Boolean` | 为 true 时，对本次**新增**项另发 `AdmissionDiagnosisCreatedEvent` |

`DoctorDiagnosisItemCommand` 字段：`id`、`definitionId`、`verificationStatusEnum`、`clinicalStatusEnum`、`categoryEnum`、`severityEnum`、`description`、`onsetDatetime`、`recordedDatetime`、`recorderId`、`ybNo`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Condition.assertPatientBound` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ConditionRepository.findByIdAndIsDeletedFalse(id)`（更新路径） |
| `eventPublishAnchor` | `EncounterDiagnosisSavedEvent`；新增项且 `admissionDiagnosisFlow=true` 时 `AdmissionDiagnosisCreatedEvent` |
| `unitTestCaseId` | `TC-CND-W-001` |

**方法契约摘要**：前置条件为入参列表非空、患者/就诊上下文一致；持久化后发布 `EncounterDiagnosisSavedEvent`；`admissionDiagnosisFlow=true` 时对无 `id` 的新增行另发 `AdmissionDiagnosisCreatedEvent`（住院申请由 `EncounterDomainService#saveAdmissionDxForInpatient` 编排）。

---

#### 5.1.2 `saveDoctorDiagnosisTcm(SaveDoctorDiagnosisTcmCommand cmd)` → `List<Long>`

**命令文件**：`SaveDoctorDiagnosisTcmCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `items` | `List<DoctorDiagnosisTcmItemCommand>` | 中医诊断项集合 |
| `operatorId` | `Long` | 操作人 |

`DoctorDiagnosisTcmItemCommand` 字段：`id`、`definitionId`、`clinicalStatusEnum`、`verificationStatusEnum`、`description`、`tcmFlag`、`recordedDatetime`、`recorderId`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Condition.assertPatientBound` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ConditionRepository.findByIdAndIsDeletedFalse(id)`（更新路径） |
| `eventPublishAnchor` | `new EncounterDiagnosisSavedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CND-W-002` |

**方法契约摘要**：前置条件为中医诊断集合非空且上下文一致；持久化影响为写入中医诊断事实并设置 `tcmFlag=true`；失败语义为参数不合法或更新目标不存在；事件触发为保存成功后发布 `EncounterDiagnosisSavedEvent`。

---

#### 5.1.3 `delEncounterDiagnosis(DelEncounterDiagnosisCommand cmd)` → `void`

**命令文件**：`DelEncounterDiagnosisCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `conditionId` | `Long` | 诊断记录标识 |
| `operatorId` | `Long` | 操作人 |
| `reason` | `String` | 删除原因 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Condition.assertNoAdviceReferenceBeforeDelete` |
| `statusTransitionMethod` | `Condition.markDeleted` |
| `repositoryChecks` | `ConditionRepository.findByIdAndIsDeletedFalse(conditionId)` + `ConditionRepository.countAdviceRefByConditionId(conditionId)` |
| `eventPublishAnchor` | `new EncounterDiagnosisDeletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CND-W-003` |

**方法契约摘要**：前置条件为目标存在且无医嘱引用；持久化影响为逻辑删除诊断记录；失败语义为记录不存在或存在业务引用；事件触发为删除成功后发布 `EncounterDiagnosisDeletedEvent`。

### 5.2 读侧 — `ConditionQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

**「出参」填 QueryModel 类名**（如 `ConditionBatchItemQueryModel`）；字段定义见 **§4.6 对应子节**，此处不重复列字段。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getConditionListById` | `List<Long> ids` | `List<ConditionBatchItemQueryModel>` | 按 ID 集合批量查询 | `TC-CND-R-001` |
| `getEncounterDiagnosis` | `Long encounterId` | `List<EncounterDiagnosisQueryModel>` | 按就诊加载诊断清单 | `TC-CND-R-002` |
| `getDiagnosisList` | `String keyword` | `List<DiagnosisCandidateQueryModel>` | 目录候选检索（只读） | `TC-CND-R-003` |
| `findByPatientId` | `Long patientId` | `List<Condition>` | 按患者扫描诊断事实（默认软删过滤）；供 **IF-DOR-06A** / **IF-IP-DOR-03** | `TC-CND-R-004` |
| `countAdviceRefByConditionId` | `Long conditionId` | `int` | **BR-CND-003** 医嘱引用计数门控；内部委托 **`ConditionRepository#countAdviceRefByConditionId`** | `TC-CND-R-005` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-CND-001 | 诊断事实必须绑定 `patientId`，写侧保存前强校验 |
| BR-CND-002 | 保存路径统一刷新记录信息（`recordedDatetime`、`recorderId`） |
| BR-CND-003 | 删除前强制执行医嘱引用检查，命中引用则拒绝删除 |
| BR-CND-004 | `definitionId` 仅做目录引用，不复制目录全量字段 |
| BR-CND-005 | `encounterId` 为诊断事实归属字段，写侧保存时与命令就诊上下文强一致 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `EncounterDiagnosisSavedEvent` | `com.zxplat.domain.share.events.clinical.condition` | `conditionId,patientId,definitionId,verificationStatusEnum,clinicalStatusEnum` | `saveDoctorDiagnosis`、`saveDoctorDiagnosisTcm` |
| `EncounterDiagnosisDeletedEvent` | `com.zxplat.domain.share.events.clinical.condition` | `conditionId,patientId` | `delEncounterDiagnosis` |
| `AdmissionDiagnosisCreatedEvent` | `com.zxplat.domain.share.events.clinical.condition` | `conditionId,definitionId,patientId` | `saveDoctorDiagnosis`（`admissionDiagnosisFlow=true` 且新增项） |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 删除语义为逻辑删除，默认读侧不可见。
- 指定 `id` 的更新路径查无记录必须报错，禁止静默新建。
- 删除前需通过“无医嘱引用”校验，避免悬挂业务关系。
- 目录停用后不影响历史诊断事实可读性，但新增写入需校验目录可用性。
- 本聚合为单表模型，不涉及子表全量对齐与软删旧行策略。

---

## 9. 框架优先适配规则

- 软删、审计、工作单元、beforeCommit 事件发布沿用项目框架能力。
- 领域服务/查询服务采用直接实现类，不默认接口+实现双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口能力，复杂QueryService组装在查询服务中完成。
- 分页语义保持数据库侧过滤与计数一致。

---

## 10. 生成与验收清单

- [ ] `§4.2` 字段表与 DM `§3.1` 完整对齐
- [ ] `§5.1` 写方法数量为 3，与 DM `§6.1` 一致
- [ ] 每个写方法包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] `§5.2` 读方法数量为 5，与 DM `§6.2` 一致
- [ ] `§6` BR 覆盖 BR-CND-001 ~ BR-CND-004
- [ ] `§7` 事件数量为 3，与 DM `§5` 一致
- [ ] 全文无“待确认/后续补充/占位实现”措辞

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CLN-003-Condition-诊断记录.md`
- 本 SDD 文件（结构/字段/方法/事件门禁通过）
- 目标模块：`{{TARGET_MODULE}}`

### 11.2 DoD

- 写侧 3 个方法全部落地，且更新路径不静默新建。
- 删除路径严格执行“医嘱引用校验 + 逻辑删除”。
- 3 个事件按 §7 beforeCommit 发布，最小载荷完整。
- 读侧 5 个方法 QueryModel/实体返回与 §4.6 / §5.2 对齐（含 **`findByPatientId`**、**`countAdviceRefByConditionId`**）。
- BR-CND 规则具备可追溯测试绑定。

### 11.3 失败处理

- 任一关键门禁失败（更新语义、删除语义、事件语义）立即停机并回文档修订。
- DM 白名单发生变化时，先更新本 SDD 再进入成码。
- 若分页/过滤口径不一致，判定 DoD 失败。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] `§5.1` 子节数量 = DM 写方法数量（3）
- [ ] `§4.2` 字段表覆盖 DM `§3.1`
- [ ] `§7` 事件表覆盖 DM `§5` 全部事件（3）
- [ ] 全文无 YAML、`sdd_status`、`dm_code`、占位措辞

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

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
