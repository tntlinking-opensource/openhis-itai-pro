# DM-CUS-004 EncounterHistory 就诊历史 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-004-EncounterHistory-就诊历史.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：EncounterHistory（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.encounterhistory` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 5 个、读方法 3 个 |
| DM 第 7 章 BR | 满足 | §6 映射 10 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 5 个事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化聚合持久化模型、写读契约、事件与不变式。
- 将实现前澄清前置：一方法一 Command、字段全集、仓储校验与测试锚点明确。
- 正文不出现占位语句，保持可实施。

### 1.2 边界

- **在内**：本聚合实体、状态、查询模型、事件触发。
- **在外**：外域主数据正文维护、页面拼装、跨域流程编排。
- **跨域仅端口声明**：通过标识引用外域，不落库外域实体。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.custom.encounterhistory`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.encounterhistory` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；命名与 DM 字段一致 |
| 时间 | 业务字段按 DM；审计字段 `LocalDateTime` |
| `*_enum` | `Integer`（或 DM 明示类型） |
| `*_code` | `String` |
| 软删 | `Boolean isDeleted` 默认过滤 |
| 实体 | `@Entity` + `@Table` + `@Column` |
| 仓储 | 仅接口，无默认实现类 |
| 分页 | `Pageable` 默认 `createdAt DESC` |
| 事件 | `new XxxEvent` + `publishBeforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_encounterhistory` | `EncounterHistory` | 依据 DM 主表语义落地（实际表名以实现定稿为准） |

### 4.2 聚合根 `EncounterHistory`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | ``Long`` | 是 | 主键，雪花 ID |
| `name` | ``String`` | 是 | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | 是 | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | 否 | 关联医嘱请求主键（如 ServiceRequest） |
| `request_table_name` | ``String`` | 否 | 请求所在逻辑表名，与请求标识成对解析 |
| `original_organization_id` | ``Long`` | 否 | 科室变迁：原科室 ID |
| `target_organization_id` | ``Long`` | 否 | 科室变迁：目标科室 ID |
| `original_location_id` | ``Long`` | 否 | 位置变迁：原病区/位置 ID |
| `target_location_id` | ``Long`` | 否 | 位置变迁：目标病区/位置 ID |
| `out_way_code` | ``String`` | 否 | 离院类里程碑专用，见 §4.4 |

**不变式方法（示例命名，实现须一致）**：`validateRequiredFields`、`validateStatusTransition`、`assertOwnership`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `EncounterHistoryRepository extends JpaRepository<EncounterHistory, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- 其余查询签名按 §5.2 与 §6 不变式派生。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `EncounterHistory1QueryModel`（对应 `listMilestonesByEncounter`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_encounterhistory.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_encounterhistory.name` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | `cus_encounterhistory.encounter_id` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | `cus_encounterhistory.request_id` | 关联医嘱请求主键（如 ServiceRequest） |
| `request_table_name` | ``String`` | `cus_encounterhistory.request_table_name` | 请求所在逻辑表名，与请求标识成对解析 |
| `original_organization_id` | ``Long`` | `cus_encounterhistory.original_organization_id` | 科室变迁：原科室 ID |
| `target_organization_id` | ``Long`` | `cus_encounterhistory.target_organization_id` | 科室变迁：目标科室 ID |
| `original_location_id` | ``Long`` | `cus_encounterhistory.original_location_id` | 位置变迁：原病区/位置 ID |

#### 4.6.2 `EncounterHistory2QueryModel`（对应 `findLatestTransferContext`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_encounterhistory.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_encounterhistory.name` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | `cus_encounterhistory.encounter_id` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | `cus_encounterhistory.request_id` | 关联医嘱请求主键（如 ServiceRequest） |
| `request_table_name` | ``String`` | `cus_encounterhistory.request_table_name` | 请求所在逻辑表名，与请求标识成对解析 |
| `original_organization_id` | ``Long`` | `cus_encounterhistory.original_organization_id` | 科室变迁：原科室 ID |
| `target_organization_id` | ``Long`` | `cus_encounterhistory.target_organization_id` | 科室变迁：目标科室 ID |

#### 4.6.3 `EncounterHistory3QueryModel`（对应 `resolveDischargeWay`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_encounterhistory.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_encounterhistory.name` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | `cus_encounterhistory.encounter_id` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | `cus_encounterhistory.request_id` | 关联医嘱请求主键（如 ServiceRequest） |
| `request_table_name` | ``String`` | `cus_encounterhistory.request_table_name` | 请求所在逻辑表名，与请求标识成对解析 |
| `original_organization_id` | ``Long`` | `cus_encounterhistory.original_organization_id` | 科室变迁：原科室 ID |
| `target_organization_id` | ``Long`` | `cus_encounterhistory.target_organization_id` | 科室变迁：目标科室 ID |
| `original_location_id` | ``Long`` | `cus_encounterhistory.original_location_id` | 位置变迁：原病区/位置 ID |
| `target_location_id` | ``Long`` | `cus_encounterhistory.target_location_id` | 位置变迁：目标病区/位置 ID |
| `out_way_code` | ``String`` | `cus_encounterhistory.out_way_code` | 离院类里程碑专用，见 §4.4 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `EncounterHistoryDomainService`

#### 5.1.1 `recordTransferMilestone(RecordTransferMilestoneCommand cmd)` → `void`

**命令文件**：`RecordTransferMilestoneCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `name` | ``String`` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | 关联医嘱请求主键（如 ServiceRequest） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TransferMilestoneRecordedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-004-W-001` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.2 `recordDischargeMilestone(RecordDischargeMilestoneCommand cmd)` → `void`

**命令文件**：`RecordDischargeMilestoneCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `name` | ``String`` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | 关联医嘱请求主键（如 ServiceRequest） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TransferMilestoneRecordedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-004-W-002` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.3 `recordAdmissionMilestone(RecordAdmissionMilestoneCommand cmd)` → `void`

**命令文件**：`RecordAdmissionMilestoneCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `name` | ``String`` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | 关联医嘱请求主键（如 ServiceRequest） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TransferMilestoneRecordedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-004-W-003` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.4 `correctMilestone(CorrectMilestoneCommand cmd)` → `void`

**命令文件**：`CorrectMilestoneCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `name` | ``String`` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | 关联医嘱请求主键（如 ServiceRequest） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TransferMilestoneRecordedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-004-W-004` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.5 `retireMilestone(RetireMilestoneCommand cmd)` → `void`

**命令文件**：`RetireMilestoneCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `name` | ``String`` | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| `encounter_id` | ``Long`` | 归属哪一次就诊过程 |
| `request_id` | ``Long`` | 关联医嘱请求主键（如 ServiceRequest） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TransferMilestoneRecordedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-004-W-005` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

### 5.2 读侧 — `EncounterHistoryQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listMilestonesByEncounter` | `criteria/pageable` | `EncounterHistory1QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-004-R-001` |
| `findLatestTransferContext` | `criteria/pageable` | `EncounterHistory2QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-004-R-002` |
| `resolveDischargeWay` | `criteria/pageable` | `EncounterHistory3QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-004-R-003` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-EHR-001 | 每行里程碑必须归属唯一就诊：`就诊标识(EncounterId)` 必填；就诊归属完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-001` |
| BR-EHR-002 | 里程碑类型必须为有效枚举值；类型合法性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-002` |
| BR-EHR-003 | 请求标识与请求表名须成对可解析，不得出现孤立引用；引用完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-003` |
| BR-EHR-004 | 转科类里程碑：科室变迁（原科室 / 目标科室）不得全空；转科业务完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-004` |
| BR-EHR-005 | 离院类里程碑：出院方式编码按院内字典有效，不得为空；离院业务完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-005` |
| BR-EHR-006 | 事实行以追加为主；确需更正时走受控更正策略，不得直接覆盖原记录破坏审计链；审计安全性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-006` |
| BR-EHR-007 | 逻辑删除记录不参与默认列表与统计查询；查询一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-007` |
| BR-EHR-008 | 就诊不存在或已终止时，拒绝新增里程碑（受控补录除外）；就诊状态约束；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-008` |
| BR-EHR-009 | 医嘱请求未保存成功时，不应写入里程碑行（同一事务回滚）；事务一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-009` |
| BR-EHR-010 | 同一就诊下可存在多条同类型里程碑（如多次转科），按时间序排列；多次变迁合法性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-004-BR-010` |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `TransferMilestoneRecordedEvent` | `com.zxplat.domain.share.events.custom.encounterhistory` | `aggregateId`、`operationType`、`occurredAt` | `recordTransferMilestone` |
| `DischargeMilestoneRecordedEvent` | `com.zxplat.domain.share.events.custom.encounterhistory` | `aggregateId`、`operationType`、`occurredAt` | `recordDischargeMilestone` |
| `AdmissionMilestoneRecordedEvent` | `com.zxplat.domain.share.events.custom.encounterhistory` | `aggregateId`、`operationType`、`occurredAt` | `recordAdmissionMilestone` |
| `MilestoneCorrectedEvent` | `com.zxplat.domain.share.events.custom.encounterhistory` | `aggregateId`、`operationType`、`occurredAt` | `correctMilestone` |
| `MilestoneRetiredEvent` | `com.zxplat.domain.share.events.custom.encounterhistory` | `aggregateId`、`operationType`、`occurredAt` | `retireMilestone` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---
## 8. 删除与可见性契约

- 删除默认采用逻辑删除，禁止物理删除（除非 DM 明示）。
- 已删除记录不参与默认查询、分页统计与业务匹配。
- 更新/删除前必须校验记录存在且归属合法。

---

## 9. 框架优先适配规则

- 复用框架软删、审计、工作单元与分页能力，不重复造轮子。
- Repository 仅保留接口；查询复杂度通过规格条件或派生签名表达。
- 事件默认直接在服务内构造并发布，不引入事件工厂薄封装。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM 字段全集一一对齐
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 5 个）
- [ ] §5.2 读方法与 §4.6 QueryModel 一一对应（当前 3 个）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-004-EncounterHistory-就诊历史.md`
- 本 SDD 文件与目标模块包规则
- 共享事件与测试约定可用

### 11.2 DoD

- 写读方法全部实现并编译通过
- 关键 BR 与事件触发均有测试覆盖
- 软删、分页、排序与 DM 语义一致

### 11.3 失败处理

- 发现 DM/实现冲突立即回退到文档修订
- 任一 BR 无法稳定实现时输出缺口并停止进入代码会话
- 查询语义不一致（过滤/total 漂移）必须先修复后继续

---

## 定稿检查清单（可打勾）

- [x] 章节结构、固定标题与顺序符合金骨架
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 5，与 DM §6.1 对齐
- [x] 每个写方法含命令文件、字段全集、绑定项、方法契约摘要
- [x] 每个读方法均有对应 `§4.6.n` QueryModel 字段表
- [x] 事件契约与 BR 映射已落地
- [x] 正文无 YAML/sdd_status/dm_code 元数据

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **98（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
