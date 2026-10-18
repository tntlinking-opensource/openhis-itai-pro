# DM-ADM-006 PractitionerRole 岗位角色 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-006-PractitionerRole-岗位角色.md`（**v1.2.5**，本 SDD 假定该 DM 为当期锁定版本）  
> **SDD 版本**：v1.3 · **日期**：2026-08-19  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 岗位角色管理）  
> **聚合**：PractitionerRole（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-006 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.practitionerrole` | 满足 | `practitionerrole` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 状态表达 | §2.3 用 `activeFlag + isDeleted` 双字段 | SDD 统一抽象为 `ACTIVE/INACTIVE/DELETED` 业务态 |
| 子表更新策略 | §6.1 描述“删除或等价替换” | SDD 固化为“全量对齐 + 软删旧行或等价替换” |
| 批量替换事件 | §5.2 说明不逐条发事件 | SDD 明确仅发 `PractitionerRoleBatchReplacedEvent` |
| `locationId` 下游 | DM v1.1 §6.2 | 本聚合**不**解析 Location 形态；护士站管理病区由 **`CurrentManagedWardLocationIdsResolver`** + **DM-ADM-003** 单聚合读消费 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **PractitionerRole 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，避免实现阶段再反复拆口径。
- 单次成稿目标：正文不出现后续补充/待确认措辞，文档阶段关闭缺口。

### 1.2 边界

- **在内**：岗位主表、岗位服务项子表、岗位排班项子表，及其一致性、状态与事件规则。
- **在外**：号源生成、权限鉴权执行、排班引擎执行、挂号业务编排。
- **跨域仅端口声明**：仅声明 Practitioner/Organization/Location/HealthcareService 的引用完整性要求，不在本聚合写跨域事实；`location_id` 的**形态过滤**由 Location 读侧完成（**AS-IP-NUR-001**、库房位置收窄等）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.practitionerrole`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PractitionerRole`、`PractitionerRoleServiceItem`、`PractitionerRoleScheduleItem` |
| `valueobject` | `SpecialtyCode`、`AvailabilitySchedule` |
| `enums`（共享） | 复用 `PractitionerBusinessRoleCode`（`zxplat-domain`） |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `PractitionerRoleRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.practitionerrole` |

**存储口径（DM 优先）**：`*_enum` 默认 `Integer`；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`，子表外键 `assignmentId` |
| 时间 | 排班项时间段由 JSON 内部字段表达，实体时间字段遵循 `LocalDateTime` |
| `*_enum` | 枚举编码列，默认 `Integer` |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<Entity, Long>` |
| 分页 | `Pageable` 默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造事件并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_pr_assign` | `PractitionerRole` | 岗位角色主表 |
| `adm_pr_assign_service` | `PractitionerRoleServiceItem` | 岗位服务项子表 |
| `adm_pr_assign_schedule` | `PractitionerRoleScheduleItem` | 岗位排班项子表 |

### 4.2 聚合根 `PractitionerRole`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 岗位角色标识 |
| `name` | `String` | 否 | 岗位名称 |
| `roleCode` | `String` | 是 | 角色语义码（`DOCTOR`/`NURSE`/…，见 DM §4.1） |
| `activeFlag` | `Boolean` | 是 | 是否启用 |
| `practitionerId` | `Long` | 是 | 医务人员标识 |
| `orgId` | `Long` | 否 | 科室标识 |
| `locationId` | `Long` | 否 | 位置标识 |
| `specialtyCode` | `String` | 否 | 专业编码 |
| `typeCode` | `Integer` | 否 | 岗位类型编码 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertRoleAssignmentUnique`、`assertOrgOrLocationPresent`、`assertServiceIdsUnique`、`assertSchedulePayloadValid`、`assertEditableWhenInactiveRules`。

### 4.3 子实体 `PractitionerRoleServiceItem`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 服务项主键 |
| `assignmentId` | `Long` | 是 | 岗位角色标识 |
| `healthcareServiceId` | `Long` | 是 | 医疗服务标识 |

**归属校验**：更新服务项时必须按 `assignmentId` 作用域更新，不允许跨岗位串写。

### 4.4 子实体 `PractitionerRoleScheduleItem`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 排班项主键 |
| `assignmentId` | `Long` | 是 | 岗位角色标识 |
| `availabilityJson` | `String` | 是 | 可用时间 JSON |

**归属校验**：更新排班项时必须按 `assignmentId` 作用域更新，不允许跨岗位串写。

### 4.5 仓储接口

- `PractitionerRoleRepository extends JpaRepository<PractitionerRole, Long>`
- `PractitionerRoleServiceItemRepository extends JpaRepository<PractitionerRoleServiceItem, Long>`
- `PractitionerRoleScheduleItemRepository extends JpaRepository<PractitionerRoleScheduleItem, Long>`

**说明**：软删默认由框架过滤时，不强制接口名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `PractitionerRoleAssignmentQueryModel`（对应 `listAssignmentsByPractitioner`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `assignmentId` | `Long` | `adm_pr_assign.id` | 岗位角色标识 |
| `roleCode` | `String` | `adm_pr_assign.role_code` | 角色语义码 |
| `orgId` | `Long` | `adm_pr_assign.org_id` | 科室标识 |
| `locationId` | `Long` | `adm_pr_assign.location_id` | 位置标识（可指向病区/药房/药库等；下游按场景过滤） |
| `serviceIds` | `List<Long>` | `adm_pr_assign_service.healthcare_service_id` | 绑定就诊类型 |

#### 4.6.2 `PractitionerOrgIdQueryModel`（对应 `listOrganizationIdsByPractitioner`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `practitionerId` | `Long` | `adm_pr_assign.practitioner_id` | 医务人员标识 |
| `orgId` | `Long` | `adm_pr_assign.org_id` | 科室标识（去重前） |

#### 4.6.3 `PractitionerHealthcareServiceQueryModel`（对应 `listHealthcareServiceIdsByPractitionerAndOrg`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `assignmentId` | `Long` | `adm_pr_assign.id` | 岗位角色标识 |
| `orgId` | `Long` | `adm_pr_assign.org_id` | 科室标识 |
| `healthcareServiceId` | `Long` | `adm_pr_assign_service.healthcare_service_id` | 可用就诊类型 |

#### 4.6.4 `PractitionerManagedScopeQueryModel`（对应 `resolveManagedScope`）

| 字段名（Java） | 类型 | 来源（派生规则） | 说明 |
| -------------- | ---- | ---------------- | ---- |
| `managedLocationIds` | `List<Long>` | `role_code ∈ {WAREHOUSE_ADMIN, PHARMACIST}` → `location_id` | 可操作库房（药库+药房族：`PHARMACY`+`DEPARTMENT_PHARMACY`，`adm_location.id`） |
| `managedOrganizationUnitIds` | `List<Long>` | `role_code ∈ {DOCTOR, NURSE, ADMINISTRATIVE}` → `org_id` | 管辖科室（`adm_organization.id`） |
| `managedWardLocationIds` | `List<Long>` | `role_code = NURSE` → `location_id` | 管辖病区（`adm_location.id`，`form=WARD`） |

**DDL 迁移**：Flyway **V87** 将 `adm_pr_assign.role_code` 由整型 `1..6` 迁移为语义字符串；并新增平台表 `whale_role_menus`（与本聚合无直接外键）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PractitionerRoleDomainService`

#### 5.1.1 `assignRole(AssignPractitionerRoleCommand cmd)` → `Long`

**命令文件**：`AssignPractitionerRoleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `roleCode` | `String` | 角色语义码 |
| `orgId` | `Long` | 科室标识 |
| `locationId` | `Long` | 位置标识 |
| `specialtyCode` | `String` | 专业编码 |
| `typeCode` | `Integer` | 岗位类型编码 |
| `serviceIds` | `List<Long>` | 就诊类型集合 |
| `scheduleItems` | `List<AvailabilityScheduleCommand>` | 排班集合 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertAssignable` |
| `statusTransitionMethod` | `PractitionerRole.initAsActive` |
| `repositoryChecks` | `PractitionerRoleRepository.existsByUniqueBusinessKey` |
| `eventPublishAnchor` | `new PractitionerRoleCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-001` |

**方法契约摘要**：前置条件为人员有效、科室/位置至少一项非空、唯一键不冲突、服务与排班数据合法；持久化影响为写主表并写入服务/排班子表；失败语义为任一校验失败整单回滚；事件触发为发布 `PractitionerRoleCreatedEvent`。**注释锚点**：校验 -> 主表写入 -> 子表写入 -> 事件。

---

#### 5.1.2 `editRole(EditPractitionerRoleCommand cmd)` → `void`

**命令文件**：`EditPractitionerRoleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `assignmentId` | `Long` | 岗位角色标识 |
| `name` | `String` | 岗位名称 |
| `orgId` | `Long` | 科室标识 |
| `locationId` | `Long` | 位置标识 |
| `specialtyCode` | `String` | 专业编码 |
| `typeCode` | `Integer` | 岗位类型编码 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRoleRepository.existsByUniqueBusinessKeyExcludingSelf` |
| `eventPublishAnchor` | `new PractitionerRoleEditedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-002` |

**方法契约摘要**：前置条件为岗位存在且未废弃，修改后仍满足唯一与组织位置约束；持久化影响为更新主表可编辑列，不改服务/排班子表；失败语义为约束冲突或状态非法时报错；事件触发为发布 `PractitionerRoleEditedEvent`。**注释锚点**：加载 -> 校验 -> 更新 -> 事件。

---

#### 5.1.3 `activateRole(ActivatePractitionerRoleCommand cmd)` → `void`

**命令文件**：`ActivatePractitionerRoleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `assignmentId` | `Long` | 岗位角色标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertActivatable` |
| `statusTransitionMethod` | `PractitionerRole.transitToActive` |
| `repositoryChecks` | `PractitionerRoleRepository.findById` |
| `eventPublishAnchor` | `new PractitionerRoleActivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-003` |

**方法契约摘要**：前置条件为当前 INACTIVE 且未废弃；持久化影响为 `activeFlag` 置 `true`；失败语义为状态不匹配时报错；事件触发为发布 `PractitionerRoleActivatedEvent`。**注释锚点**：状态校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.4 `deactivateRole(DeactivatePractitionerRoleCommand cmd)` → `void`

**命令文件**：`DeactivatePractitionerRoleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `assignmentId` | `Long` | 岗位角色标识 |
| `reason` | `String` | 停用原因 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertDeactivatable` |
| `statusTransitionMethod` | `PractitionerRole.transitToInactive` |
| `repositoryChecks` | `PractitionerRoleRepository.findById` |
| `eventPublishAnchor` | `new PractitionerRoleDeactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-004` |

**方法契约摘要**：前置条件为当前 ACTIVE 且未废弃；持久化影响为 `activeFlag` 置 `false`；失败语义为状态不匹配时报错；事件触发为发布 `PractitionerRoleDeactivatedEvent`。**注释锚点**：状态校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.5 `retireRole(RetirePractitionerRoleCommand cmd)` → `void`

**命令文件**：`RetirePractitionerRoleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `assignmentId` | `Long` | 岗位角色标识 |
| `reason` | `String` | 废弃原因 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertRetirable` |
| `statusTransitionMethod` | `PractitionerRole.transitToDeleted` |
| `repositoryChecks` | `PractitionerRoleRepository.findById` |
| `eventPublishAnchor` | `new PractitionerRoleAbolishedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-005` |

**方法契约摘要**：前置条件为岗位存在且未废弃；持久化影响为主记录逻辑删除并退出业务可见范围；失败语义为重复废弃或目标不存在时报错；事件触发为发布 `PractitionerRoleAbolishedEvent`。**注释锚点**：校验 -> 逻辑删除 -> 持久化 -> 事件。

---

#### 5.1.6 `updateServices(UpdatePractitionerRoleServicesCommand cmd)` → `void`

**命令文件**：`UpdatePractitionerRoleServicesCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `assignmentId` | `Long` | 岗位角色标识 |
| `serviceIds` | `List<Long>` | 目标服务集合 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertServiceUpdatable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRoleRepository.findById / HealthcareService existence checks` |
| `eventPublishAnchor` | `new PractitionerRoleServiceUpdatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-006` |

**方法契约摘要**：前置条件为岗位 ACTIVE 且未废弃、服务项无重复且均有效；持久化影响为服务子表全量替换；失败语义为停用态更新或服务无效时报错；事件触发为发布 `PractitionerRoleServiceUpdatedEvent`。**注释锚点**：校验 -> 子表全量对齐 -> 持久化 -> 事件。

---

#### 5.1.7 `updateSchedule(UpdatePractitionerRoleScheduleCommand cmd)` → `void`

**命令文件**：`UpdatePractitionerRoleScheduleCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `assignmentId` | `Long` | 岗位角色标识 |
| `scheduleItems` | `List<AvailabilityScheduleCommand>` | 排班集合 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertScheduleUpdatable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRoleRepository.findById` |
| `eventPublishAnchor` | `new PractitionerRoleScheduleUpdatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-007` |

**方法契约摘要**：前置条件为岗位 ACTIVE 且未废弃、排班 JSON 满足 schema；持久化影响为排班子表全量替换；失败语义为停用态更新或排班非法时报错；事件触发为发布 `PractitionerRoleScheduleUpdatedEvent`。**注释锚点**：校验 -> 子表全量对齐 -> 持久化 -> 事件。

---

#### 5.1.8 `replaceRolesByType(ReplacePractitionerRolesByTypeCommand cmd)` → `List<Long>`

**命令文件**：`ReplacePractitionerRolesByTypeCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `roleCode` | `String` | 目标角色语义码 |
| `items` | `List<AssignPractitionerRoleItemCommand>` | 替换后岗位集合 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `PractitionerRole.assertBatchReplaceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRoleRepository.findByPractitionerIdAndRoleCode` |
| `eventPublishAnchor` | `new PractitionerRoleBatchReplacedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRL-W-008` |

**方法契约摘要**：前置条件为每条新岗位都满足 assignRole 校验；持久化影响为按 `practitionerId+roleCode` 清理旧集合并写入新集合，集合语义全量一致；失败语义为任一条目失败整批回滚；事件触发为仅发布 `PractitionerRoleBatchReplacedEvent`。**注释锚点**：批量校验 -> 旧集合清理 -> 新集合写入 -> 汇总事件。

### 5.2 读侧 — `PractitionerRoleQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listAssignmentsByPractitioner` | `Long practitionerId` | `List<PractitionerRoleAssignmentQueryModel>` | `activeFlag=true`；批量加载 `adm_pr_assign_service`；**不**解析 Location `form_enum` | `TC-PRL-R-001` |
| `listOrganizationIdsByPractitioner` | `Long practitionerId` | `List<PractitionerOrgIdQueryModel>` | 去重返回可用科室标识集合 | `TC-PRL-R-002` |
| `listHealthcareServiceIdsByPractitionerAndOrg` | `Long practitionerId`, `Long orgId` | `List<PractitionerHealthcareServiceQueryModel>` | 人员+科室维度返回可用就诊类型 | `TC-PRL-R-003` |
| `resolveManagedScope` | `Long practitionerId` | `PractitionerManagedScopeQueryModel` | 激活岗位派生管辖 org/location/ward；`module.shared.identity.HealthPractitionerManagedScopeResolver` 填充 whale 会话；App 经 **B.6.5** Resolver | `TC-PRL-R-004` |
| `collectLoginOrganizationUnitIds` | `Long practitionerId` | `List<Long>` | 自 `resolveManagedScope.managedOrganizationUnitIds` 汇总；供登录科室同步 | `TC-PRL-R-005` |
| `findPractitionerIdByAssignmentId` | `Long assignmentId` | `Long` | 岗位变更后反查医务人员 | `TC-PRL-R-006` |
| `listPractitionerIdsWithActiveRoleId` | `Long roleId`, `Long orgId`（可 null） | `Set<Long>` | `adm_pr_assign` 按 `role_id`+可选 `org_id`+`active_flag`；供 **`PractitionerRoleScopeLoader`**、用户管理出诊科室检索 | `TC-PRL-R-007` |
| `listPractitionerIdsWithActiveRoleIdInOrganizations` | `Long roleId`, `Collection<Long> orgIds` | `Set<Long>` | `adm_pr_assign` 按 `role_id`+`org_id IN (...)`+`active_flag`；门诊挂号未选科室时限定门诊科室集合 | `TC-PRL-R-008` |

#### 5.2.1 `locationId` 下游消费（本聚合只出 id；App 层经 Resolver）

| 消费方 | 领域/应用链 | 说明 |
| ------ | ----------- | ---- |
| **住院护士站**（**AS-IP-NUR-001**） | `resolveManagedScope.managedWardLocationIds` → `HealthPractitionerManagedScopeResolver` → **`CurrentManagedWardLocationIdsResolver`** | 队列 Gate 与初始化地理树**同源**（`#listManagedWardLocationIdsOrEmpty` / `#listManagedActiveWardsOrEmpty`） |
| **库存 · 药库/药房**（库存/发药 AppService） | `resolveManagedScope.managedLocationIds` → **`CurrentManagedLocationIdsResolver`** | 药库 `WAREHOUSE` + 药房族（`PHARMACY` + `DEPARTMENT_PHARMACY`）；`PractitionerManagedScopeLoader` 按 `form_enum` 归类；可经 `#findActiveWarehousesByIds` / `#findActivePharmaciesByIds` 过滤启用态 |
| **会话科室**（各工作站 Loader） | 选科 Session + `managedOrganizationUnitIds` → **`CurrentManagedOrganizationIdsResolver`** | 读 `resolveOrganizationUnitIdForQuery`；写 `requireSessionOrganizationUnitId` |

**禁止**：AppService / `querysupport.*Loader` 注入 `ICurrentUser` 或复制 `resolveOrganizationId` / `resolveManagedWardIds`（**B.6.5**）。详见 [Identity与HIS组织轻关联说明](../../Identity与HIS组织轻关联说明.md) §7。

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次/批量仓储调用 -> 组装 QueryModel（无写库、无 Location 形态分支）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PRL-001 | 人员+角色+科室+位置组合唯一 |
| BR-PRL-002 | 服务项必须引用有效 HealthcareService |
| BR-PRL-003 | 排班 JSON 必须通过结构校验 |
| BR-PRL-004 | 已废弃岗位默认不可见且不参与计算 |
| BR-PRL-005 | 单条废弃逻辑删除；全量替换后集合与入参一致 |
| BR-PRL-006 | 主表与子表同事务写入，保证聚合一致性 |
| BR-PRL-007 | 科室与位置允许同时为空（仅角色挂靠） |
| BR-PRL-008 | `practitionerId` 必须指向有效 Practitioner |
| BR-PRL-009 | 停用态不可更新服务/排班 |
| BR-PRL-010 | 同岗位下服务项 `healthcareServiceId` 不重复 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `PractitionerRoleCreatedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,practitionerId,orgId,roleCode` | `assignRole` |
| `PractitionerRoleEditedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,changedFields` | `editRole` |
| `PractitionerRoleServiceUpdatedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,healthcareServiceIds` | `updateServices` |
| `PractitionerRoleScheduleUpdatedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,scheduleSummary` | `updateSchedule` |
| `PractitionerRoleActivatedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,practitionerId,orgId` | `activateRole` |
| `PractitionerRoleDeactivatedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,reason` | `deactivateRole` |
| `PractitionerRoleAbolishedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `assignmentId,practitionerId` | `retireRole` |
| `PractitionerRoleBatchReplacedEvent` | `com.zxplat.domain.share.events.administration.practitionerrole` | `practitionerId,roleCode,replacedAssignmentIds` | `replaceRolesByType` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 单条删除语义为逻辑删除，禁止物理删除主记录。
- 默认查询过滤 `isDeleted=true` 的岗位与子项。
- 停用岗位（`activeFlag=false`）不参与排班/号源/权限计算。
- 全量替换按集合语义清理旧数据并写入新集合，保证替换结果可追溯。
- 子表更新必须限定在所属 `assignmentId` 作用域下执行。

---

## 9. 框架优先适配规则

- 软删、审计字段、事务工作单元、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认引入接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口，复杂装配在查询服务实现。
- 子表全量更新遵循“全量对齐 + 软删旧行或等价替换”口径。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（主表+两类子表）
- [ ] §5.1 写方法小节数量 = 8，与 DM §6.1 方法一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 3 个方法；§5.2.1 与 **DM-ADM-003**、**AS-IP-NUR-001** 可追溯
- [ ] §6 BR 映射覆盖 BR-PRL-001 至 BR-PRL-010
- [ ] §7 事件表覆盖 DM §5 全部 8 条事件
- [ ] 批量替换事件策略与单条事件策略一致且不冲突
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-006-PractitionerRole-岗位角色.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.administration.practitionerrole`

### 11.2 DoD

- 写侧 8 个方法全部落地，方法签名与 Command 命名与 §5 一致。
- 主表与子表字段、聚合边界、状态/删除语义与 §4/§6 一致。
- 事件按 §7 完整发布，替换场景仅发布批量替换事件。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧 3 个方法与 §4.6 QueryModel 对齐；`locationId` 可被 Location 读侧批量收窄（护士站管理病区）。

### 11.3 失败处理

- 任一写方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单、事件或 BR 变更，先更新 SDD 再进入代码会话。
- 若子表一致性或替换事件策略不达标，DoD 失败不得进入成码。

---

## 变更历史

| SDD 版本 | 日期 | 摘要 |
| -------- | ---- | ---- |
| v1.3 | 2026-08-19 | §5.2 用户管理出诊科室检索复用 `listPractitionerIdsWithActiveRoleId` |
| v1.2 | 2026-07-02 | **`managedLocationIds`** 药房族含 **`DEPARTMENT_PHARMACY`**；`PractitionerManagedScopeLoader` 形态归类 |
| v1.0 | — | 首版 |
| v1.1 | 2026-05-20 | 对齐 DM v1.1：`locationId` 下游消费（护士站管理病区、库房收窄） |

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（8）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（8）
- [ ] 每个写方法包含 `entityInvariantMethod` 与 `unitTestCaseId`
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

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-08-19 | 用户管理按出诊科室检索复用 `listPractitionerIdsWithActiveRoleId(ROLE_DOCTOR, orgId)` |
| 2026-07-02 | **`managedLocationIds`** 含科室药房；`PractitionerManagedScopeLoader` 与 **DM-ADM-003** 药房族对齐 |
| 2026-06-03 | 与 DM v1.2.2 对齐：§4.6.4 可操作库房语义；§5.2.1 五类 Resolver 消费链；Identity §7 / B.6.5 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

