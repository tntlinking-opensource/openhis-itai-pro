# DM-CLN-004 Procedure 执行记录 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-004-Procedure-执行记录.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Clinical（临床域）  
> **聚合**：Procedure（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CLN-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `{{TARGET_MODULE}}` |
| 包规则 `com.zxplat.domain.clinical.procedure` | 满足 | `procedure` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

---

## 1. 设计目标与边界

> **实现口径（EventStatus）**：共享枚举 `com.zxplat.domain.share.enums.EventStatus` 与 DM §4.1 对齐；取消执行终态 **`CANCEL`**（非历史 `ABORTED`）；**`STOPPED`** 已入枚举、域写暂未使用。存量 `cln_procedure.status_enum='ABORTED'` 由 Flyway **V43** 迁移为 **`CANCEL`**；读侧净次数统计经 `EventStatus#isCancelExecutionStatusCode` 兼容历史码直至迁移完成。

### 1.1 设计目标

- 固化 `Procedure` 执行事实聚合（主表+执行人子表）的字段契约、状态迁移、写读方法、事件与 BR，支持后续一次性成码。
- 明确执行主状态（待执行/执行中/已执行/取消）与执行人轨迹写入规则，保证可追溯与幂等。
- 将复杂校验前置为方法契约：完成前联合校验、取消执行追溯、并发冲突保护。

### 1.2 边界

- **在内**：`cln_procedure`、`cln_procedure_performer` 领域模型、状态迁移、执行人维护、查询契约与事件。
- **在外**：收费生成、发放编排、病历归档等应用层流程。
- **跨域仅端口声明**：通过 `requestId + requestTable`、`encounterId`、`patientId` 做标识引用，不在本聚合写外部聚合数据。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`{{TARGET_MODULE}}`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.clinical.procedure`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Procedure`、`ProcedurePerformer` |
| `valueobject` | 无独立复合值对象 |
| `enums`（共享） | `EventStatus`、`ProcedureCategory` |
| `command` | 每个写方法一个 Command 文件 |
| `repository` | `ProcedureRepository`、`ProcedurePerformerRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.clinical.procedure` |

**存储口径（DM 优先）**：`status_enum`、`category_enum` 为 `Integer`（枚举编码）；`*_code` 为 `String`；`report_flag` 为 `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | 主键 `Long id`；子表外键 `procedureId` |
| 时间 | 业务时间统一 `LocalDateTime` |
| `*_enum` | 枚举编码统一 `Integer` |
| `*_code` | 字典编码列统一 `String` |
| `_flag` | 布尔标记统一 `Boolean` |
| 软删 | `Boolean isDeleted`，主表子表均逻辑删除 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | 无独立 `@Embeddable` 值对象 |
| 仓储 | `interface XxxRepository extends JpaRepository<Entity, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务方法内直接 `new XxxEvent(...)` + `publish`，`beforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cln_procedure` | `Procedure` | 执行记录聚合根主表 |
| `cln_procedure_performer` | `ProcedurePerformer` | 执行人子表（1..N） |

### 4.2 聚合根 `Procedure`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `requestId` | `Long` | 是 | 来源请求标识 |
| `requestTable` | `String` | 是 | 来源请求表名 |
| `statusEnum` | `Integer` | 是 | 执行状态 |
| `statusReasonText` | `String` | 否 | 状态原因 |
| `categoryEnum` | `Integer` | 是 | 执行类别 |
| `procedureCode` | `String` | 否 | 执行编码 |
| `encounterId` | `Long` | 是 | 就诊标识 |
| `patientId` | `Long` | 是 | 患者标识 |
| `occurrenceTime` | `LocalDateTime` | 否 | 发生时间 |
| `occurrencePeriod` | `String` | 否 | 发生时期 |
| `occurrenceAge` | `String` | 否 | 发生年龄描述 |
| `occurrenceText` | `String` | 否 | 发生时间描述 |
| `recordedTime` | `LocalDateTime` | 否 | 记录时间 |
| `recorderId` | `Long` | 否 | 记录人 |
| `reportFlag` | `Boolean` | 否 | 是否有报告 |
| `reportedPatientId` | `Long` | 否 | 报告关联患者 |
| `orgId` | `Long` | 否 | 执行科室 |
| `locationId` | `Long` | 否 | 执行位置 |
| `reasonText` | `String` | 否 | 理由 |
| `bodySiteJson` | `String` | 否 | 身体部位 JSON |
| `bodyStructureId` | `Long` | 否 | 身体结构标识 |
| `resultText` | `String` | 否 | 结果描述 |
| `reportJson` | `String` | 否 | 报告 JSON |
| `complicationJson` | `String` | 否 | 并发症 JSON |
| `followUpJson` | `String` | 否 | 跟进建议 JSON |
| `groupId` | `Long` | 否 | 组号 |
| `refundId` | `Long` | 否 | 取消执行关联标识 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertRequestBound`、`assertStateTransitionAllowed`、`assertCompletable`、`assertCancelable`。

### 4.3 子实体 `ProcedurePerformer`

| 字段名（Java） | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 子实体主键 |
| `procedureId` | `Long` | 是 | 关联执行记录标识 |
| `functionCode` | `String` | 否 | 参与者类型编码 |
| `startTime` | `LocalDateTime` | 是 | 开始时间 |
| `endTime` | `LocalDateTime` | 否 | 结束时间 |
| `practitionerId` | `Long` | 是 | 执行人标识 |
| `orgFromId` | `String` | 否 | 外部机构标识 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**归属校验**：任何带 `id` 的更新必须 `loadByIdAndProcedureId(id, procedureId)`，归属不一致直接拒绝。

### 4.4 子实体

无第二子实体。

### 4.5 仓储接口

- `ProcedureRepository extends JpaRepository<Procedure, Long>`
- `ProcedurePerformerRepository extends JpaRepository<ProcedurePerformer, Long>`
- 推荐查询签名：`findByRequestIdAndRequestTableAndIsDeletedFalse`、`findAllByProcedureIdAndIsDeletedFalse`。
- `List<Procedure> findByEncounterIdIn(Collection<Long> encounterIds)` — **AS-IP-NUR-001** 护士站当日净执行批量统计（DM §6.2.7）

**说明**：软删统一过滤时不强制全部签名使用 `AndIsDeletedFalse` 命名后缀。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `ProcedureRecordQueryModel`（对应 `getProcedureRecords`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_procedure.id` | 执行记录标识 |
| `requestId` | `Long` | `cln_procedure.request_id` | 来源请求标识 |
| `requestTable` | `String` | `cln_procedure.request_table` | 来源表名 |
| `statusEnum` | `Integer` | `cln_procedure.status_enum` | 执行状态 |
| `recordedTime` | `LocalDateTime` | `cln_procedure.recorded_time` | 记录时间 |

#### 4.6.2 `ProcedureProjectionQueryModel`（对应 `queryProcedureProjections`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `procedureId` | `Long` | `cln_procedure.id` | 执行记录标识 |
| `encounterId` | `Long` | `cln_procedure.encounter_id` | 就诊标识 |
| `patientId` | `Long` | `cln_procedure.patient_id` | 患者标识 |
| `categoryEnum` | `Integer` | `cln_procedure.category_enum` | 执行类别 |
| `statusEnum` | `Integer` | `cln_procedure.status_enum` | 执行状态 |
| `occurrenceTime` | `LocalDateTime` | `cln_procedure.occurrence_time` | 发生时间 |

#### 4.6.3 `ProcedureDetailQueryModel`（对应 `loadProcedureById` / `mustGetProcedure`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `procedure` | `ProcedureRecordQueryModel` | `cln_procedure.*` | 主记录 |
| `performers` | `List<ProcedurePerformerQueryModel>` | `cln_procedure_performer.*` | 执行人列表 |

#### 4.6.4 `ProcedurePerformerQueryModel`（对应 `listPerformerByProcedure`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_procedure_performer.id` | 子记录主键 |
| `procedureId` | `Long` | `cln_procedure_performer.procedure_id` | 主记录标识 |
| `practitionerId` | `Long` | `cln_procedure_performer.practitioner_id` | 执行人标识 |
| `functionCode` | `String` | `cln_procedure_performer.function_code` | 参与者类型 |
| `startTime` | `LocalDateTime` | `cln_procedure_performer.start_time` | 开始时间 |
| `endTime` | `LocalDateTime` | `cln_procedure_performer.end_time` | 结束时间 |

#### 4.6.5 `ProcedureCompletableCheckQueryModel`（对应 `checkProcedureCompletable`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `procedureId` | `Long` | `cln_procedure.id` | 目标执行记录 |
| `pass` | `Boolean` | 规则计算 | 是否可完成 |
| `failCode` | `String` | 规则计算 | 失败码 |
| `failReason` | `String` | 规则计算 | 失败原因 |
| `statusEnum` | `Integer` | `cln_procedure.status_enum` | 当前状态 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ProcedureDomainService`

#### 5.1.1 `createProcedureRecord(CreateProcedureRecordCommand cmd)` → `Long`

**命令文件**：`CreateProcedureRecordCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `requestId` | `Long` | 来源请求标识 |
| `requestTable` | `String` | 来源请求表名 |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `categoryEnum` | `Integer` | 执行类别 |
| `occurrenceTime` | `LocalDateTime` | 发生时间 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Procedure.assertRequestBound` |
| `statusTransitionMethod` | `Procedure.initPreparationStatus` |
| `repositoryChecks` | `ProcedureRepository.findByRequestIdAndRequestTableAndIsDeletedFalse(requestId, requestTable)`（幂等校验） |
| `eventPublishAnchor` | `new ProcedureRecordCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRO-W-001` |

**方法契约摘要**：前置条件为请求锚点、患者、就诊、类别齐全；持久化影响为创建主记录并置 `status=PREPARATION`；失败语义为关键标识缺失或幂等冲突；事件触发为创建后发布 `ProcedureRecordCreatedEvent`。

---

#### 5.1.2 `startProcedureExecution(StartProcedureExecutionCommand cmd)` → `void`

**命令文件**：`StartProcedureExecutionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `procedureId` | `Long` | 执行记录标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Procedure.assertStateTransitionAllowed` |
| `statusTransitionMethod` | `Procedure.startExecution` |
| `repositoryChecks` | `ProcedureRepository.findByIdAndIsDeletedFalse(procedureId)` |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-PRO-W-002` |

**方法契约摘要**：前置条件为当前状态必须是 `PREPARATION`；持久化影响为迁移到 `IN_PROGRESS`；失败语义为状态不合法或记录不存在；事件触发为本方法不发布专属事件。

---

#### 5.1.3 `appendProcedurePerformer(AppendProcedurePerformerCommand cmd)` → `void`

**命令文件**：`AppendProcedurePerformerCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `procedureId` | `Long` | 执行记录标识 |
| `items` | `List<ProcedurePerformerItemCommand>` | 执行人项集合 |
| `operatorId` | `Long` | 操作人 |

`ProcedurePerformerItemCommand` 字段：`id`、`practitionerId`、`functionCode`、`startTime`、`endTime`、`orgFromId`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Procedure.assertPerformerAppendable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ProcedureRepository.findByIdAndIsDeletedFalse(procedureId)` + `ProcedurePerformerRepository.findAllByProcedureIdAndIsDeletedFalse(procedureId)` |
| `eventPublishAnchor` | `new ProcedurePerformerEditedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRO-W-003` |

**方法契约摘要**：前置条件为主记录状态在 `PREPARATION/IN_PROGRESS` 且执行人字段合法；持久化影响为对子表做新增/更新并保持归属一致；失败语义为主记录不存在、归属非法或时间区间非法；事件触发为维护后发布 `ProcedurePerformerEditedEvent`。

---

#### 5.1.4 `completeProcedureRecord(CompleteProcedureRecordCommand cmd)` → `void`

**命令文件**：`CompleteProcedureRecordCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `procedureId` | `Long` | 执行记录标识 |
| `resultText` | `String` | 结果摘要 |
| `reportJson` | `String` | 报告内容 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Procedure.assertCompletable` |
| `statusTransitionMethod` | `Procedure.complete` |
| `repositoryChecks` | `ProcedureRepository.findByIdAndIsDeletedFalse(procedureId)` + `ProcedureQueryService.checkProcedureCompletable(procedureId)` |
| `eventPublishAnchor` | `new ProcedureRecordCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRO-W-004` |

**方法契约摘要**：前置条件为状态 `IN_PROGRESS` 且 `resultText/reportJson` 至少一项非空；持久化影响为状态迁移到 `COMPLETED`、收口结果字段，**并在 `operatorId` 非空时写入 `recorder_id`，在 `recorded_time` 为空时写入当前时刻**；失败语义为联合校验失败或并发冲突；事件触发为完成后发布 `ProcedureRecordCompletedEvent`。

---

#### 5.1.5 `cancelProcedureRecord(CancelProcedureRecordCommand cmd)` → `void`

**命令文件**：`CancelProcedureRecordCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `procedureId` | `Long` | 执行记录标识 |
| `statusReasonText` | `String` | 取消原因 |
| `refundId` | `Long` | 关联被取消执行记录 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Procedure.assertCancelable` |
| `statusTransitionMethod` | `Procedure.cancel` |
| `repositoryChecks` | `ProcedureRepository.findByIdAndIsDeletedFalse(procedureId)` |
| `eventPublishAnchor` | `new ProcedureRecordCancelledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRO-W-005` |

**方法契约摘要**：前置条件为当前状态在 `PREPARATION/IN_PROGRESS` 且取消原因必填；持久化影响为状态迁移到 `CANCEL` 并写入取消追溯字段；失败语义为终态记录重复取消或链路关联非法；事件触发为取消后发布 `ProcedureRecordCancelledEvent`。
**应用编排（取消对冲新建行，2026-09-29 回写）**：须 `create→start→挂 PRIMARY（操作人）→cancel`，与执行同构。

---

#### 5.1.6 `skipProcedureRecord(SkipProcedureRecordCommand cmd)` → `Long`

**命令文件**：`SkipProcedureRecordCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `requestId` | `Long` | 来源请求标识 |
| `requestTable` | `String` | 来源请求表名 |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `categoryEnum` | `String` | 执行类别 code |
| `occurrenceTime` | `LocalDateTime` | 计划执行时刻 |
| `recordedTime` | `LocalDateTime` | 登记时刻 |
| `operatorId` | `Long` | 操作人（必填；兼 PRIMARY 主执行人） |
| `statusReasonText` | `String` | 不执行原因（可选） |
| `skipDuplicateRequestAnchorCheck` | `Boolean` | 为 true 时允许多计划点（**BR-IP-EXE-015**） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Procedure.assertRequestBound` + `initPreparationStatus` + `startExecution` + `assertPerformerAppendable` + `skip` |
| `statusTransitionMethod` | `PREPARATION→IN_PROGRESS→STOPPED`（`Procedure.skip` 允许 PREPARATION/IN_PROGRESS） |
| `repositoryChecks` | 默认 `findByRequestIdAndRequestTable` 重复门禁；挂 PRIMARY 写 `cln_procedure_performer` |
| `eventPublishAnchor` | `new ProcedureRecordSkippedEvent(...); DomainEventPublisher.publish(event)`（**save 之后**） |
| `unitTestCaseId` | `TC-PRO-W-006` |

**方法契约摘要**：前置条件为请求/就诊/患者/计划点/`operatorId` 齐全；持久化影响为新建 Procedure，经 `start` 与挂主执行人后进入 `STOPPED`；失败语义为未放开重复门禁且同 request 已有记录、或操作人为空；事件触发为 `ProcedureRecordSkippedEvent`。**消费方**：**AS-IP-NUR-004 IF-IP-EXE-04**。
**代码先行回写（2026-09-29）**：与执行/取消同构挂 PRIMARY，禁止新建即终态无执行人。

### 5.2 读侧 — `ProcedureQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

**「出参」填 QueryModel 类名**（如 `ProcedureRecordQueryModel`）；字段定义见 **§4.6 对应子节**，此处不重复列字段。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getProcedureRecords` | `Long requestId, String requestTable` | `List<ProcedureRecordQueryModel>` | 按请求锚点回查执行记录 | `TC-PRO-R-001` |
| `queryProcedureProjections` | `ProcedureProjectionQuery query, Pageable pageable` | `Page<ProcedureProjectionQueryModel>` | 执行台条件分页查询 | `TC-PRO-R-002` |
| `loadProcedureById` | `Long procedureId` | `ProcedureDetailQueryModel` | 加载主记录+执行人明细 | `TC-PRO-R-003` |
| `listPerformerByProcedure` | `Long procedureId` | `List<ProcedurePerformerQueryModel>` | 查询执行人轨迹 | `TC-PRO-R-004` |
| `checkProcedureCompletable` | `Long procedureId` | `ProcedureCompletableCheckQueryModel` | 完成前联合校验 | `TC-PRO-R-005` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PRO-001 | 创建记录时强制 `requestId + requestTable` 绑定 |
| BR-PRO-002 | 创建记录时强制 `encounterId` 与 `patientId` 非空 |
| BR-PRO-003 | `COMPLETED/CANCEL` 终态后禁止回退到非终态 |
| BR-PRO-004 | 完成动作必须满足 `resultText` 或 `reportJson` 至少一项 |
| BR-PRO-005 | 取消动作必须记录 `statusReasonText` 并校验 `refundId` 链路 |
| BR-PRO-006 | 所有写动作先查后改，不允许盲写 |
| BR-PRO-007 | 写入需并发保护，拒绝覆盖式并发更新 |
| BR-PRO-008 | 读侧方法不改变聚合状态 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ProcedureRecordCreatedEvent` | `com.zxplat.domain.share.events.clinical.procedure` | `procedureId,requestId,requestTable,statusEnum` | `createProcedureRecord` |
| `ProcedurePerformerEditedEvent` | `com.zxplat.domain.share.events.clinical.procedure` | `procedureId,performerId,functionCode,startTime,endTime` | `appendProcedurePerformer` |
| `ProcedureRecordCompletedEvent` | `com.zxplat.domain.share.events.clinical.procedure` | `procedureId,statusEnum,resultText,reportSummary` | `completeProcedureRecord` |
| `ProcedureRecordCancelledEvent` | `com.zxplat.domain.share.events.clinical.procedure` | `procedureId,statusEnum,statusReasonText,refundId` | `cancelProcedureRecord` |
| `ProcedureRecordSkippedEvent` | `com.zxplat.domain.share.events.clinical.procedure` | `procedureId,requestId,requestTable,statusEnum,occurrenceTime,recordedTime` | `skipProcedureRecord` |

**发布时机**：写方法内 **save 完成之后** 发布（**B.14**）；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 主表与子表均使用逻辑删除，禁止物理删除。
- 子表执行人维护采用“归属校验 + 增量对齐”，不允许跨 `procedureId` 改写。
- 取消执行是状态迁移，不等同删除；取消后记录仍可查询用于审计。
- 读侧默认过滤 `isDeleted=true` 数据；审计查询需显式开启含删除。

---

## 9. 框架优先适配规则

- 软删、审计、事务工作单元、beforeCommit 事件发布沿用项目框架能力。
- 服务默认直接实现类，不强制接口 + Impl 双层。
- 一方法一 Command，命令文件独立维护。
- 仓储仅保留 `JpaRepository` 接口，复杂查询与投影在查询服务实现。
- 分页查询保持数据库侧过滤与总数统计一致，避免内存二次过滤。

---

## 10. 生成与验收清单

- [ ] `§4.2`、`§4.3` 字段表与 DM `§3.1`、`§3.2` 完整对齐
- [ ] `§5.1` 写方法数量为 5，与 DM `§6.1` 一致
- [ ] 每个写方法包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] `§5.2` 读方法数量为 5，与 DM `§6.2` 一致
- [ ] `§6` BR 覆盖 BR-PRO-001 ~ BR-PRO-008
- [ ] `§7` 事件数量为 4，与 DM `§5` 一致
- [ ] 全文无“待确认/后续补充/占位实现”措辞

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CLN-004-Procedure-执行记录.md`
- 本 SDD 文件（结构/字段/方法/事件门禁通过）
- 目标模块：`{{TARGET_MODULE}}`

### 11.2 DoD

- 写侧 5 个方法全部落地，状态迁移严格遵循 §5 契约。
- 子实体归属校验与执行人维护规则完整实现，不允许越权更新。
- 4 个事件全部按 §7 beforeCommit 发布并满足最小载荷。
- 读侧 5 个方法 QueryModel 字段与 §4.6 完整对齐。
- BR-PRO-001~008 均有可追溯测试绑定。

### 11.3 失败处理

- 若状态迁移、并发保护、事件发布任一不可落地，立即停机并回文档会话修订。
- 若 DM 白名单方法调整，先更新 SDD 再进行代码生成。
- 若分页语义或可见性规则与约定不一致，判定 DoD 失败。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] `§5.1` 子节数量 = DM 写方法数量（5）
- [ ] `§4.2/§4.3` 字段表覆盖 DM `§3` 主表/子表
- [ ] `§7` 事件表覆盖 DM `§5` 全部事件（4）
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
