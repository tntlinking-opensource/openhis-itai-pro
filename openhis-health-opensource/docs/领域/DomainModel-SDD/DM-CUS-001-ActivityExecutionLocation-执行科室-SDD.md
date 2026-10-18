# DM-CUS-001 ActivityExecutionLocation 执行科室 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-001-ActivityExecutionLocation-执行科室.md`（v1.5.1）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：基础数据 · 诊疗项目目录  
> **聚合**：ActivityExecutionLocation（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.activityexecutionlocation` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 审计时间字段按 `LocalDateTime`；业务时间窗字段按 DM 明示 `LocalTime` |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间窗类型 | DM §3.1 `start_time/end_time` | 采用 `LocalTime`，不改写为 `LocalDateTime` |
| 新增语义 | DM §6.1.1“单条或批量” | 单方法支持单条/批量；单方法一 Command 通过列表字段承载 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **ActivityExecutionLocation 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，字段全集列出；本聚合为单表聚合，不涉及子表同步。
- 单次成稿目标：正文不出现“后续补充”“待确认”类措辞；写/读白名单、BR 与事件均可直接映射实现。

### 1.2 边界

- **在内**：执行科室规则行（活动类别 + 锚点 + 时间窗 + 执行科室/位置 + 优先级）的新增、更新、软删、查询命中。
- **在外**：Organization、Location、ActivityDefinition 主数据维护；ServiceRequest 写入编排。
- **跨域仅端口声明**：仅通过标识（`executionOrgId`、`executionLocationId`、`activityDefinitionId`）引用外域，不在本聚合落库外域实体正文。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.custom.activityexecutionlocation`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ActivityExecutionLocation` 聚合根实体 |
| `valueobject` | 无真正值对象（本聚合时间窗直接字段表达） |
| `enums`（共享） | 如存在共享枚举，复用 `com.zxplat.domain.share.enums` |
| `command` | `CreateExecutionRuleCommand`、`UpdateExecutionRuleCommand`、`DeleteExecutionRuleCommand` |
| `repository` | `ActivityExecutionLocationRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | 共享事件包 `com.zxplat.domain.share.events.custom.activityexecutionlocation` |

**存储口径（DM 优先）**：本聚合不存在 `*_enum`；`activity_category_code` 属字典编码，Java 类型 `String`。时间窗字段 `start_time/end_time` 按 DM 明示使用 `LocalTime`；审计时间字段维持框架 `LocalDateTime`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键命名与 DM 一致：`activityDefinitionId`、`executionOrgId`、`executionLocationId` |
| 时间 | 业务时段字段 `startTime`/`endTime` 使用 `LocalTime`（DM 明示）；审计字段（`createdAt`/`updatedAt`）使用 `LocalDateTime` |
| `*_enum` | 本聚合无 `*_enum` 字段 |
| `*_code` | `activityCategoryCode`、`activityTypeCode` 使用 `String` |
| `_flag` | 本聚合无 `_flag` 业务字段 |
| 软删 | `Boolean isDeleted`，遵循框架统一逻辑删除语义 |
| 实体 | `@Entity` + `@Table(name = "cus_activity_execution_location")` + 显式 `@Column` |
| 值对象 | 本聚合无独立值对象，不创建 `@Embeddable` |
| 仓储 | `interface ActivityExecutionLocationRepository extends JpaRepository<ActivityExecutionLocation, Long>`，无自定义实现类 |
| 分页 | `Pageable`；未指定排序时默认 `createdAt DESC` |
| 事务 | 领域服务方法默认不机械添加 `@Transactional`，依赖框架工作单元 |
| 事件 | 领域服务内 `new ActivityExecutionLocationEditedEvent(...)` + `DomainEventPublisher.publish`，`beforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_activity_execution_location` | `ActivityExecutionLocation` | 单表聚合，一行一条执行科室规则 |

### 4.2 聚合根 `ActivityExecutionLocation`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `organizationId` | `Long` | 是 | 开立科室标识（`adm_organization.id`） |
| `activityDefinitionId` | `Long` | 否 | 诊疗活动定义标识（`wkf_activity_definition.id`）；**为空表示未绑单品** |
| `activityCategoryCode` | `String` | 是 | 活动类别编码 |
| `activityTypeCode` | `String` | 否 | 业务类型码（术语 `activity_type_code`）；可空 |
| `executionOrgId` | `Long` | 是 | 执行科室标识 |
| `executionLocationId` | `Long` | 否 | 执行位置标识 |
| `startTime` | `LocalTime` | 否 | 生效时段开始；空表示全天 |
| `endTime` | `LocalTime` | 否 | 生效时段结束；空表示全天 |
| `priority` | `Integer` | 是 | 优先级，数值越小越优先（1 最优先） |
| `displayOrder` | `Integer` | 否 | 展示顺序 |
| `remark` | `String` | 否 | 备注 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

**不变式方法（示例命名，实现须一致）**：`checkNoTimeRangeOverlap(...)`、`validateRequiredFields(...)`、`markDeleted()`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `findByIdAndIsDeletedFalse(Long id)`
- `existsOverlapForCreate(Long executionOrgId, String activityCategoryCode, Long activityDefinitionId, LocalTime startTime, LocalTime endTime)`
- `existsOverlapForUpdateExcludeSelf(Long id, Long executionOrgId, String activityCategoryCode, Long activityDefinitionId, LocalTime startTime, LocalTime endTime)`
- `findAllByActivityDefinitionIdAndIsDeletedFalse(Long activityDefinitionId, Pageable pageable)`
- `findTopByActivityDefinitionIdAndActivityCategoryCodeAndTime(Long activityDefinitionId, String activityCategoryCode, LocalTime atTime)`
- `findForBatchByActivityDefinitionIdsAndTime(List<Long> activityDefinitionIds, LocalTime atTime)`

**说明**：软删过滤若由框架统一处理，接口名可不强制拼接 `AndIsDeletedFalse`，但查询语义必须等价。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `ExecutionRuleListQueryModel`（对应 `listExecutionRulesByActivityDefinition` / `listExecutionRulesForActivityResolution`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cus_activity_execution_location.id` | 规则行主键 |
| `organizationId` | `Long` | `cus_activity_execution_location.organization_id` | 开立科室标识 |
| `activityDefinitionId` | `Long` | `cus_activity_execution_location.activity_definition_id` | 诊疗定义标识；未绑单品时为 null |
| `activityCategoryCode` | `String` | `cus_activity_execution_location.activity_category_code` | 活动类别编码 |
| `activityTypeCode` | `String` | `cus_activity_execution_location.activity_type_code` | 业务类型码；大类默认时为 null |
| `executionOrgId` | `Long` | `cus_activity_execution_location.execution_org_id` | 执行科室标识 |
| `executionLocationId` | `Long` | `cus_activity_execution_location.execution_location_id` | 执行位置标识 |
| `startTime` | `LocalTime` | `cus_activity_execution_location.start_time` | 生效开始时间 |
| `endTime` | `LocalTime` | `cus_activity_execution_location.end_time` | 生效结束时间 |
| `priority` | `Integer` | `cus_activity_execution_location.priority` | 优先级 |
| `displayOrder` | `Integer` | `cus_activity_execution_location.display_order` | 展示顺序 |
| `remark` | `String` | `cus_activity_execution_location.remark` | 备注 |

#### 4.6.2 `ExecutionRuleHitQueryModel`（对应 `findByActivityAndTime`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cus_activity_execution_location.id` | 命中规则主键 |
| `activityDefinitionId` | `Long` | `cus_activity_execution_location.activity_definition_id` | 诊疗定义标识 |
| `activityCategoryCode` | `String` | `cus_activity_execution_location.activity_category_code` | 活动类别编码 |
| `executionOrgId` | `Long` | `cus_activity_execution_location.execution_org_id` | 执行科室标识 |
| `executionLocationId` | `Long` | `cus_activity_execution_location.execution_location_id` | 执行位置标识 |
| `startTime` | `LocalTime` | `cus_activity_execution_location.start_time` | 命中规则开始时间 |
| `endTime` | `LocalTime` | `cus_activity_execution_location.end_time` | 命中规则结束时间 |
| `priority` | `Integer` | `cus_activity_execution_location.priority` | 用于取首排序 |

#### 4.6.3 `ExecutionRuleBatchHitQueryModel`（对应 `findForBatchByActivityAndTime`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `anchorId` | `Long` | `cus_activity_execution_location.activity_definition_id` | 批量输入锚点（诊疗定义） |
| `ruleId` | `Long` | `cus_activity_execution_location.id` | 命中规则主键 |
| `executionOrgId` | `Long` | `cus_activity_execution_location.execution_org_id` | 执行科室标识 |
| `executionLocationId` | `Long` | `cus_activity_execution_location.execution_location_id` | 执行位置标识 |
| `priority` | `Integer` | `cus_activity_execution_location.priority` | 命中优先级 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ActivityExecutionLocationDomainService`

#### 5.1.1 `createExecutionRule(CreateExecutionRuleCommand cmd)` → `List<Long>`

**命令文件**：`CreateExecutionRuleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `activityCategoryCode` | `String` | 活动类别编码，必填 |
| `organizationId` | `Long` | 开立科室标识，必填 |
| `executionOrgId` | `Long` | 执行科室标识，必填 |
| `rules` | `List<CreateExecutionRuleItemCommand>` | 新增规则明细；长度 1 表示单条，>1 表示批量 |
| `operatorId` | `Long` | 操作人标识 |
| `operatorName` | `String` | 操作人姓名 |

（子项命令独立文件 `CreateExecutionRuleItemCommand.java` 字段全集如下：`activityDefinitionId(Long，可空)`、`activityTypeCode(String，可空)`、`executionLocationId(Long)`、`startTime(LocalTime)`、`endTime(LocalTime)`、`priority(Integer)`、`displayOrder(Integer)`、`remark(String)`。）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` + `checkNoTimeRangeOverlap` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `existsOverlapForCreate(...)`（逐条校验） |
| `eventPublishAnchor` | 保存成功后 `new ActivityExecutionLocationEditedEvent(ruleId, "CREATE", snapshot)` 并 `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-001-W-001`、`TC-DM-CUS-001-W-002` |

**方法契约摘要**：前置条件为 `activityCategoryCode`、`executionOrgId`、`rules` 非空，且每条明细满足 BR-ACT-EXEC-001/002；持久化影响为向 `cus_activity_execution_location` 插入一条或多条规则行；失败语义为任一明细非法或冲突时按应用层策略整批失败或逐条拒绝并返回失败明细；事件触发为每条成功写入触发 `ActivityExecutionLocationEditedEvent`（可合并发布）。**注释锚点**：校验（必填+重叠）→ 组装实体 → 持久化插入 → 事件发布。

---

#### 5.1.2 `updateExecutionRule(UpdateExecutionRuleCommand cmd)` → `void`

**命令文件**：`UpdateExecutionRuleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 规则主键，必填 |
| `activityDefinitionId` | `Long` | 诊疗定义标识，可选更新 |
| `activityTypeCode` | `String` | 业务类型码，可选更新（允许清空） |
| `activityCategoryCode` | `String` | 活动类别编码，可选更新 |
| `executionOrgId` | `Long` | 执行科室标识，可选更新 |
| `executionLocationId` | `Long` | 执行位置标识，可选更新 |
| `startTime` | `LocalTime` | 生效开始时间，可选更新 |
| `endTime` | `LocalTime` | 生效结束时间，可选更新 |
| `priority` | `Integer` | 优先级，可选更新 |
| `displayOrder` | `Integer` | 展示顺序，可选更新 |
| `remark` | `String` | 备注，可选更新 |
| `operatorId` | `Long` | 操作人标识 |
| `operatorName` | `String` | 操作人姓名 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` + `checkNoTimeRangeOverlap` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)` + `existsOverlapForUpdateExcludeSelf(...)` |
| `eventPublishAnchor` | 更新成功后 `new ActivityExecutionLocationEditedEvent(id, "UPDATE", snapshot)` 并 `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-001-W-003`、`TC-DM-CUS-001-W-004` |

**方法契约摘要**：前置条件为目标规则存在且未软删，更新后字段仍满足 BR-ACT-EXEC-001/002；持久化影响为更新单条规则行，不新增、不物理删除；失败语义为记录不存在、已软删、时段重叠或参数非法时抛业务异常并回滚当前方法工作单元；事件触发为更新成功发布 `ActivityExecutionLocationEditedEvent`。**注释锚点**：加载并校验存在 → 合并更新字段并做重叠校验 → 持久化更新 → 事件发布。

---

#### 5.1.3 `deleteExecutionRule(DeleteExecutionRuleCommand cmd)` → `void`

**命令文件**：`DeleteExecutionRuleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 规则主键，必填 |
| `operatorId` | `Long` | 操作人标识 |
| `operatorName` | `String` | 操作人姓名 |
| `deleteReason` | `String` | 删除原因，可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)` |
| `eventPublishAnchor` | 软删成功后 `new ActivityExecutionLocationEditedEvent(id, "DELETE", snapshot)` 并 `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-001-W-005`、`TC-DM-CUS-001-W-006` |

**方法契约摘要**：前置条件为目标规则存在；持久化影响为仅更新 `isDeleted=true`（并记录审计字段），禁止物理删除；失败语义为记录不存在时按业务约定报错（或幂等吞并，需与应用层一致）；事件触发为软删成功发布 `ActivityExecutionLocationEditedEvent`。**注释锚点**：加载并校验存在 → 执行软删状态变更 → 持久化更新 → 事件发布。

### 5.2 读侧 — `ActivityExecutionLocationQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参填 QueryModel 类名**（如 `ExecutionRuleHitQueryModel`）；字段定义见 **§4.6 对应子节**，此处不重复列字段。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listExecutionRulesByActivityDefinition` | `activityDefinitionId` | `ExecutionRuleListQueryModel` | 按诊疗定义过滤，按 `priority ASC, display_order ASC, id ASC` | `TC-DM-CUS-001-R-001` |
| `listExecutionRulesForActivityResolution` | `organizationId, activityDefinitionId, activityCategoryCode?, activityTypeCode?` | `ExecutionRuleListQueryModel` | 单品 + 匹配类型层 + 同大类默认合并去重；排序单品→类型→大类再 priority | `TC-DM-CUS-001-R-007` |
| `listExecutionRulesByOrganization` | `organizationId, activityCategoryCode?` | `ExecutionRuleListQueryModel` | 配置页按开立科室列出规则 | `TC-DM-CUS-001-R-006` |
| `findByActivityAndTime` | `organizationId, activityDefinitionId, activityCategoryCode?, activityTypeCode?, atTime` | `ExecutionRuleHitQueryModel` | 单品 → 类型层 → 大类默认；按 `priority ASC, id ASC` 取首 | `TC-DM-CUS-001-R-002` |
| `findForBatchByActivityAndTime` | `activityDefinitionIds, atTime` | `ExecutionRuleBatchHitQueryModel` | 批量 IN 查询后按锚点分组取每组第一条 | `TC-DM-CUS-001-R-004` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-ACT-EXEC-001 | 在 `createExecutionRule`（逐条）与 `updateExecutionRule`（排除自身 id）中调用重叠校验；锚点分单品、业务类型、大类默认三级，时间窗按闭区间相交规则判定冲突 |
| BR-ACT-EXEC-002 | 写侧命令入参统一校验 `activityCategoryCode`、`executionOrgId`；`activityDefinitionId`、`activityTypeCode` 可空；删除仅允许软删 |
| BR-ACT-EXEC-003 | 所有读侧方法默认带 `isDeleted=false` 条件，软删记录不参与命中 |
| PR-ACT-EXEC-001 | 更新路径在仓储校验中强制排除当前 `id`，防止自冲突误判 |
| PR-ACT-EXEC-002 | 新增路径对 `rules` 每个元素独立校验并分别持久化，保证单条与批量语义一致 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ActivityExecutionLocationEditedEvent` | `com.zxplat.domain.share.events.custom.activityexecutionlocation` | `ruleId`、`operationType`（CREATE/UPDATE/DELETE）、`activityCategoryCode`、`executionOrgId`、`occurredAt` | `createExecutionRule`、`updateExecutionRule`、`deleteExecutionRule` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 删除语义统一为逻辑删除：`isDeleted=true`，禁止物理 `DELETE`。
- 读侧默认仅返回 `isDeleted=false` 记录；如需审计查询，必须显式走审计查询接口，不复用业务读接口。
- 时间窗命中查询仅在有效记录集合内排序与取首，软删记录不得参与比较。
- 更新/删除必须先校验目标记录存在且可见，防止对不可见记录静默写入。

---

## 9. 框架优先适配规则

- 复用框架统一审计字段与软删过滤能力，不重复在业务层实现同构过滤器。
- Repository 仅声明接口与必要派生查询签名，不新增无业务收益的自定义实现类。
- 事件发布走统一领域事件发布器，采用 beforeCommit，避免额外事件工厂薄封装。
- 领域服务与查询服务采用直接实现类，只有多实现切换场景才引入接口抽象。

---

## 10. 生成与验收清单

- [ ] 实体 `ActivityExecutionLocation` 字段与 DM §3.1 逐项对齐（含可空性与类型）
- [ ] 三个写方法均已生成独立 Command 文件，字段与 §5.1 完全一致
- [ ] 新增/更新均实现 BR-ACT-EXEC-001 重叠校验，更新路径已排除自身 id
- [ ] 删除实现为软删且读侧默认过滤软删
- [ ] 五个读方法均已在 `queries` 实现并返回对应 QueryModel
- [ ] 事件 `ActivityExecutionLocationEditedEvent` 在三写方法中 beforeCommit 发布
- [ ] 关键 BR 均绑定单元测试并通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-001-ActivityExecutionLocation-执行科室.md`
- 本 SDD 文件（当前版本）作为唯一领域成码契约
- 目标模块与包规则已确认：`zxplat-domain` / `com.zxplat.domain.custom.activityexecutionlocation`
- 共享事件包与测试命名规范可用

### 11.2 DoD

- 三个写方法、五个读方法全部实现并可编译通过
- 软删语义、时间窗命中语义、优先级排序语义与 SDD 一致
- BR-ACT-EXEC-001/002/003 与 PR-ACT-EXEC-001/002 全部落地并可测
- 事件发布时机为 beforeCommit，最小载荷字段齐全
- 单元测试覆盖写侧成功/失败路径与读侧关键命中路径

### 11.3 失败处理

- 发现 DM 与实现约束冲突时，停止成码并回到 SDD 修订，不做口头补丁
- 任一 BR 无法稳定实现或测试不通过时，标记失败并输出缺口清单
- 若发现查询统计与过滤口径不一致（分页总数偏差），判定失败并优先修正查询语义

---

## 定稿检查清单（可打勾）

- [x] 标题、章节结构、固定标题与顺序符合金骨架
- [x] `### 5.1` 下包含 3 个 `#### 5.1.n`，与 DM §6.1 写方法数量一致
- [x] 每个写方法小节均包含：命令文件、字段全集、绑定项表、方法契约摘要
- [x] `§4.2` 字段表与 DM §3.1 字段全集对齐，无子集截断
- [x] `§4.6` 为每个读方法提供独立 QueryModel 子节与字段表
- [x] `§7` 事件表覆盖 DM §5 全部事件（本聚合 1 个事件）
- [x] 正文无 YAML、无 `sdd_status`/`dm_code` 元数据键
- [x] 无“待确认/后续补充/暂不细化”占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **99（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-10-07 | **配置下拉口径**：对齐 DM v1.5.1；新增 `listActivityTypeOptionsByCategory`（按大类目录 `type_enum`，排除与大类同名） |
| 2026-10-07 | **代码先行回写**：对齐 DM v1.5.0；`activityTypeCode` 可选层；读侧三层匹配；重叠校验第三粒度 |
| 2026-09-19 | **回更**：对齐 DM v1.4.0；配置页分类门禁；无规则回落开立科室 |
| 2026-06-17 | **M3 回更**：对齐 DM v1.2.0；`activityDefinitionId` 可选（大类默认规则）；新增 `listExecutionRulesForActivityResolution`；`findByActivityAndTime` 增加大类默认回退 |
| 2026-06-05 | **M2 回更**：对齐 DM v1.1.0；优先级升序（1 最优先）；移除收费项锚点读/写字段；配置 App `ExecuteLocationAppService` 见 DM §6.4 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

## 13. 配置端应用层落点（与 DM §6.4 对齐）

| Maven 模块 | 包 | 类 |
| ---------- | -- | -- |
| `health-application` | `com.zxplat.domain.application.basic.executelocation` | `ExecuteLocationAppService` |
| 同上 | `...request` / `...dto` / `...mapper` | `QueryExecuteLocationRequest`、`CreateExecuteLocationRequest`、`ExecuteLocationDto`、`ExecuteLocationInitDto`、`ExecuteLocationMapper` |

**HTTP**：`POST /api/app/executeLocation/init` | `listActivityTypeOptionsByCategory` | `listByExecutionOrgId` | `create` | `update` | `deleteById` | `batchCreate` | `pageActivityDefinitionsForExecution`

**业务类型下拉**：`listActivityTypeOptionsByCategory` 入参 `activityCategoryCode`；领域侧 `ActivityDefinitionQueryService#listDistinctTypeEnumsByCategoryCode` 取该大类目录实际 `type_enum`，应用层排除与大类同名展示项（检查→CT/DR，不出护理/手术/检查）。

**写操作人**：`CurrentPractitionerResolver#requireOperatorId`（B.6.1）。

**展示 enrich**：`ProductDefinitionBatchLoader#loadAsMap`（`wkf_activity_definition`）、`TerminologyOptionLoader#buildLabelMap`（`activity_category_code`）、`LocationQueryService#mapNamesByIds`。

**诊疗挑选**：复用 `ProductDefinitionCatalogPageLoader`，表名 `TableNameConstants.WKF_ACTIVITY_DEFINITION`，不新增本模块分页 IF。

**写路径校验（WH-EXE001 配置页）**：

| 方法 | 必填 | 可选 |
| ---- | ---- | ---- |
| `create` / `update` | `organizationId`（门诊/住院）、`executionOrgId`（护士站/医技）、`activityCategoryCode`、时段 | `activityDefinitionId`（空=未绑单品）、`activityTypeCode`（空=非类型层）、`executionLocationId`、`priority`、`displayOrder`、`remark` |
| `batchCreate` | `organizationId`、`executionOrgId`、`activityCategoryCode`、至少一个 `activityDefinitionId` | 同单条公共字段 |

医生站 `listActivityExecutionLocations`（门诊/住院）无命中规则时，响应补一条执行科室=就诊开立科室的行，供医嘱行与申请单下拉，不回落全院科室。

---

**本 SDD 正文结束。**
