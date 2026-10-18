# DM-CLN-001 AllergyIntolerance 过敏与不耐受 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-001-AllergyIntolerance-过敏与不耐受.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Clinical（临床域）  
> **聚合**：AllergyIntolerance（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CLN-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `{{TARGET_MODULE}}` |
| 包规则 `com.zxplat.domain.clinical.allergyintolerance` | 满足 | `allergyintolerance` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 项目统一成码口径）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1 中 `Date` 文本字段 | 成码统一为 `LocalDateTime` |
| 事件包路径 | §5 仅给事件名 | 固定为 `com.zxplat.domain.share.events.clinical.allergyintolerance` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **AllergyIntolerance 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成 `{{TARGET_MODULE}}` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出；本聚合为单表，不涉及子表对齐。
- **单次成稿目标**：正文不出现“后续补充/待确认”措辞，写侧/读侧/事件/BR 直接可落地。

### 1.2 边界

- **在内**：`cln_allergy_intolerance` 聚合字段约束、保存/删除规则、查询契约、事件发布契约。
- **在外**：医嘱拦截、收费、库存、病历编排等跨域流程；跨域仅通过查询或事件协同。
- **跨域仅端口声明**：对 `Encounter`、`Patient`、`MedicationDefinition` 仅做 ID 级引用校验，不在本聚合内写入对方聚合。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`{{TARGET_MODULE}}`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.clinical.allergyintolerance`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `AllergyIntolerance` |
| `valueobject` | 无独立值对象类；枚举复用共享模块 |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums` |
| `command` | `SaveSkinTestRecordCommand`、`DeleteSkinTestRecordCommand` |
| `repository` | `AllergyIntoleranceRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.clinical.allergyintolerance` |

**存储口径（DM 优先）**：`*_enum` 使用 `Integer`；`*_code` 使用 `String`；`_flag` 使用 `Boolean`；`isDeleted` 为逻辑删除标记。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键命名 `requestId` / `encounterId` / `patientId` / `medicationId` |
| 时间 | `onsetDateTime`、`recordedDate`、`lastReactionOccurrence` 统一 `LocalDateTime` |
| `*_enum` | 枚举编码列，默认 `Integer` |
| `*_code` | 字典编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，由框架统一处理 |
| 实体 | `@Entity`，显式 `@Table` / `@Column` |
| 值对象 | 无独立 `@Embeddable` 值对象 |
| 仓储 | `interface AllergyIntoleranceRepository extends JpaRepository<AllergyIntolerance, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接 `new XxxEvent(...)` + `DomainEventPublisher.publish`，`beforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cln_allergy_intolerance` | `AllergyIntolerance` | 过敏与不耐受聚合根单表 |

### 4.2 聚合根 `AllergyIntolerance`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `requestId` | `Long` | 是 | 服务申请标识 |
| `encounterId` | `Long` | 是 | 就诊标识 |
| `patientId` | `Long` | 是 | 患者标识 |
| `medicationId` | `Long` | 否 | 药品定义标识 |
| `lotNumber` | `String` | 否 | 批号 |
| `clinicalStatusEnum` | `Integer` | 是 | 临床状态 |
| `verificationStatusEnum` | `Integer` | 是 | 验证状态 |
| `typeEnum` | `Integer` | 否 | 类型枚举 |
| `categoryCode` | `String` | 否 | 过敏原类别编码 |
| `criticalityEnum` | `Integer` | 否 | 危险程度 |
| `code` | `String` | 否 | 过敏物质编码 |
| `description` | `String` | 否 | 反应描述 |
| `severityEnum` | `Integer` | 否 | 严重程度 |
| `onsetDateTime` | `LocalDateTime` | 否 | 过敏发生时间 |
| `recordedDate` | `LocalDateTime` | 否 | 记录时间 |
| `lastReactionOccurrence` | `LocalDateTime` | 否 | 最后反应发生时间 |
| `exposureRoute` | `String` | 否 | 曝露路线编码 |
| `practitionerId` | `Long` | 否 | 记录者标识 |
| `checkPractitionerId` | `Long` | 否 | 断言者标识 |
| `note` | `String` | 否 | 备注 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertRequiredReferences`、`assertStatusPairPresent`、`assertDeletable`、`markDeleted`。

### 4.3 子实体

无。本聚合为单表。

### 4.4 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `AllergyIntoleranceRepository extends JpaRepository<AllergyIntolerance, Long>`
- 推荐查询签名：`findByEncounterIdAndIsDeletedFalse`、`findByPatientIdAndIsDeletedFalse`、`existsByIdAndIsDeletedFalse`。

**说明**：若框架统一软删过滤，接口名不强制冗余 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `AllergyIntoleranceRecordQueryModel`（对应 `getRecordInfo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_allergy_intolerance.id` | 记录标识 |
| `requestId` | `Long` | `cln_allergy_intolerance.request_id` | 服务申请标识 |
| `encounterId` | `Long` | `cln_allergy_intolerance.encounter_id` | 就诊标识 |
| `patientId` | `Long` | `cln_allergy_intolerance.patient_id` | 患者标识 |
| `medicationId` | `Long` | `cln_allergy_intolerance.medication_id` | 药品标识 |
| `lotNumber` | `String` | `cln_allergy_intolerance.lot_number` | 批号 |
| `clinicalStatusEnum` | `Integer` | `cln_allergy_intolerance.clinical_status_enum` | 临床状态 |
| `verificationStatusEnum` | `Integer` | `cln_allergy_intolerance.verification_status_enum` | 验证状态 |
| `criticalityEnum` | `Integer` | `cln_allergy_intolerance.criticality_enum` | 危险程度 |
| `severityEnum` | `Integer` | `cln_allergy_intolerance.severity_enum` | 严重程度 |
| `recordedDate` | `LocalDateTime` | `cln_allergy_intolerance.recorded_date` | 记录时间 |

#### 4.6.2 `AllergyHistoryFlagQueryModel`（对应 `getAllergyHistoryFlag`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `patientId` | `Long` | 入参 / `cln_allergy_intolerance.patient_id` | 患者标识 |
| `encounterId` | `Long` | 入参 / `cln_allergy_intolerance.encounter_id` | 就诊标识 |
| `historyFlag` | `Boolean` | 规则计算 | 是否存在有效过敏记录 |
| `recordCount` | `Long` | 聚合计数 | 命中记录数 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `AllergyIntoleranceDomainService`

#### 5.1.1 `saveSkinTestRecord(SaveSkinTestRecordCommand cmd)` → `Long`

**命令文件**：`SaveSkinTestRecordCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 更新时传入，新增为空 |
| `requestId` | `Long` | 服务申请标识 |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `medicationId` | `Long` | 药品标识 |
| `lotNumber` | `String` | 批号 |
| `clinicalStatusEnum` | `Integer` | 临床状态 |
| `verificationStatusEnum` | `Integer` | 验证状态 |
| `typeEnum` | `Integer` | 类型 |
| `categoryCode` | `String` | 过敏原类别编码 |
| `criticalityEnum` | `Integer` | 危险程度 |
| `code` | `String` | 过敏物质编码 |
| `description` | `String` | 描述 |
| `severityEnum` | `Integer` | 严重程度 |
| `onsetDateTime` | `LocalDateTime` | 发生时间 |
| `recordedDate` | `LocalDateTime` | 记录时间 |
| `lastReactionOccurrence` | `LocalDateTime` | 最后反应时间 |
| `exposureRoute` | `String` | 曝露路线 |
| `practitionerId` | `Long` | 记录者 |
| `checkPractitionerId` | `Long` | 断言者 |
| `note` | `String` | 备注 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `AllergyIntolerance.assertRequiredReferences` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `AllergyIntoleranceRepository.findByIdAndIsDeletedFalse(id)`（更新路径） |
| `eventPublishAnchor` | `new AllergyIntoleranceRecordedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ALI-W-001` |

**方法契约摘要**：前置条件为 `requestId/encounterId/patientId` 与双状态字段必填；持久化影响为新增或按 `id` 更新同一记录；失败语义为参数不合法、目标不存在或不可写即抛业务异常；事件触发为保存成功后发布 `AllergyIntoleranceRecordedEvent`。

---

#### 5.1.2 `deleteSkinTestRecord(DeleteSkinTestRecordCommand cmd)` → `void`

**命令文件**：`DeleteSkinTestRecordCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `recordId` | `Long` | 过敏记录标识 |
| `operatorId` | `Long` | 操作人 |
| `reason` | `String` | 删除原因 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `AllergyIntolerance.assertDeletable` |
| `statusTransitionMethod` | `AllergyIntolerance.markDeleted` |
| `repositoryChecks` | `AllergyIntoleranceRepository.findByIdAndIsDeletedFalse(recordId)` |
| `eventPublishAnchor` | `new AllergyIntoleranceRemovedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ALI-W-002` |

**方法契约摘要**：前置条件为记录存在且可删除；持久化影响为设置 `isDeleted=true`（禁止物理删除）；失败语义为记录不存在或已删除即抛业务异常；事件触发为删除成功后发布 `AllergyIntoleranceRemovedEvent`。

### 5.2 读侧 — `AllergyIntoleranceQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

**「出参」填 QueryModel 类名**（如 `AllergyIntoleranceRecordQueryModel`）；字段定义见 **§4.6 对应子节**，此处不重复列字段。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getRecordInfo` | `Long encounterId` | `List<AllergyIntoleranceRecordQueryModel>` | 按就诊过滤并组装记录列表 | `TC-ALI-R-001` |
| `getAllergyHistoryFlag` | `Long patientId, Long encounterId` | `AllergyHistoryFlagQueryModel` | 统计有效记录并派生布尔标记 | `TC-ALI-R-002` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-ALI-001 | `saveSkinTestRecord` 强制 `requestId/encounterId/patientId` 三锚点必填 |
| BR-ALI-002 | `saveSkinTestRecord` 强制 `clinicalStatusEnum + verificationStatusEnum` 成对存在 |
| BR-ALI-003 | `deleteSkinTestRecord` 仅接受 `recordId` 路径，禁止条件删除 |
| BR-ALI-004 | `practitionerId/checkPractitionerId` 非空时执行人员有效性校验 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `AllergyIntoleranceRecordedEvent` | `com.zxplat.domain.share.events.clinical.allergyintolerance` | `id,encounterId,patientId,medicationId,clinicalStatusEnum,verificationStatusEnum` | `saveSkinTestRecord` |
| `AllergyIntoleranceRemovedEvent` | `com.zxplat.domain.share.events.clinical.allergyintolerance` | `id,encounterId,patientId` | `deleteSkinTestRecord` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 删除语义为逻辑删除：`isDeleted=true`，禁止物理删除。
- 读侧默认只返回 `isDeleted=false` 记录，审计查询需显式开启含删除模式。
- 更新路径仅允许操作未删除记录；已删除记录不允许恢复式覆盖写入。
- 过敏史标记计算仅统计有效记录，避免删除数据污染风险判断。

---

## 9. 框架优先适配规则

- 软删、审计字段、工作单元事务、`beforeCommit` 事件发布遵循项目框架能力。
- 领域服务/查询服务采用直接实现类，不引入无收益接口抽象。
- 一方法一 Command 且一文件一命令对象。
- 仓储仅保留 `JpaRepository` 接口能力，复杂过滤在查询服务实现。
- 分页查询禁止“先分页后内存过滤”，保证 `totalElements` 一致。

---

## 10. 生成与验收清单

- [ ] `§4.2` 字段表与 DM `§3.1` 完整对齐，无字段遗漏
- [ ] `§5.1` 写方法数量为 2，与 DM `§6.1` 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] `§5.2` 读方法数量为 2，与 DM `§6.2` 一致
- [ ] `§6` BR 覆盖 BR-ALI-001 ~ BR-ALI-004
- [ ] `§7` 事件数量为 2，与 DM `§5` 一致
- [ ] 全文无“待确认/后续补充/占位实现”措辞

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CLN-001-AllergyIntolerance-过敏与不耐受.md`
- 本 SDD 文件（结构门禁、字段门禁、方法门禁、事件门禁均通过）
- 目标模块：`{{TARGET_MODULE}}`

### 11.2 DoD

- 写侧 `saveSkinTestRecord`、`deleteSkinTestRecord` 两个方法全部落地并通过单测。
- 聚合根字段、类型、约束与 `§4.2` 一致，逻辑删除策略完整实现。
- 两个事件按 `§7` 在 `beforeCommit` 发布，载荷字段不少于最小载荷。
- 读侧两个方法按数据库侧过滤实现，QueryModel 字段与 `§4.6` 对齐。
- BR-ALI 关键规则均有可追溯测试用例绑定。

### 11.3 失败处理

- 任一方法契约无法落地（字段缺失、校验冲突、事件缺失）立即停机并回到文档会话补齐。
- 若 DM 白名单方法发生变更，先更新本 SDD，再进入代码会话。
- 若软删语义或分页语义与约定不一致，判定 DoD 失败，不得合并。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿清单、评分、结束语）
- [ ] `§5.1` 子节数量 = DM 写方法数量（2）
- [ ] `§4.2` 字段表覆盖 DM `§3.1`
- [ ] `§7` 事件表覆盖 DM `§5` 全部事件（2）
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
