# DM-ADM-004 Organization 机构科室 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-004-Organization-机构科室.md`（**v1.2.0**）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 组织机构管理）  
> **聚合**：Organization（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.organization` | 满足 | `organization` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 标识类型表达 | §2.2 使用 `OrganizationId` 语义类型 | 成码口径落地为 `Long id`，语义通过字段命名保留 |
| 状态派生字段 | §2.3 同时描述 `status_enum/active_flag` | 写侧统一以 `statusEnum` 为源，`activeFlag` 同步派生 |
| 读侧返回语义 | §6.2 多方法仅业务描述 | 在 §4.6 固化逐方法 QueryModel 字段表 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Organization 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，避免代码阶段反复对口径。
- 单次成稿目标：正文无后续补充/待确认语句，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：`adm_organization` 单表聚合、层级编码 `busNo`、状态流转、与身份域映射一致性契约、组织树查询契约。
- **在外**：挂号编排、住院编排、费用处理、排班任务等下游业务执行。
- **跨域仅端口声明**：Organization 与 OrganizationUnit 的 **code 轻关联**（见 **DM-ADM-004** §9.3）；OU 同步在 **health-application** `DepartmentAppService`，不在本领域模块内。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.organization`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Organization` |
| `valueobject` | `InputCode`、`MedicalInsuranceInfo` |
| `enums`（共享） | 复用 `PublicationStatus`、`OrganizationType`、`OrganizationClassification` |
| `command` | 每个写方法一个 Command 文件 |
| `repository` | `OrganizationRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.organization` |

**存储口径（DM 优先）**：`*_enum` 默认 **String 存枚举 code**；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id` |
| 时间 | `*Date` 用 `LocalDate`，`*Time/*At` 用 `LocalDateTime` |
| `*_enum` | 枚举编码列，默认 **String（存枚举 code）** |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<Entity, Long>` |
| 分页 | `Pageable` + 默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内 `new Event` + `publish`，beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_organization` | `Organization` | 机构科室聚合根单表 |

### 4.2 聚合根 `Organization`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 机构标识 |
| `name` | `String` | 是 | 机构名称，同父级唯一 |
| `busNo` | `String` | 是 | 层级编码，创建后按规则维护 |
| `parentId` | `Long` | 否 | 父机构标识 |
| `typeEnum` | `String` | 是 | 机构类型（HOSPITAL/DEPARTMENT） |
| `classEnum` | `OrganizationClassification` | 否 | 机构分类（Converter） |
| `statusEnum` | `String` | 是 | PublicationStatus code |
| `activeFlag` | `Boolean` | 是 | `statusEnum==ACTIVE` 派生 |
| `pyStr` | `String` | 否 | 拼音码 |
| `wbStr` | `String` | 否 | 五笔码 |
| `ybNo` | `String` | 否 | 医保编码 |
| `ybName` | `String` | 否 | 医保名称 |
| `medinsId` | `String` | 否 | 医疗服务机构标识 |
| `medinsAdmdvs` | `String` | 否 | 行政区划编码 |
| `medinsType` | `String` | 否 | 医保机构类型 |
| `medinsLv` | `String` | 否 | 医保机构等级 |
| `caty` | `String` | 否 | 科别 |
| `displayOrder` | `Integer` | 否 | 同级排序 |
| `defDoctorId` | `Long` | 否 | 默认挂号医生 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertSiblingNameUnique`、`assertNoHierarchyCycle`、`assertRetireAllowed`、`assertAbolishAllowed`、`assertInsuranceFieldsCompleteWhenRequired`。

### 4.2.1 值对象字段展开（InputCode）

| Java 字段名 | 类型 | @Column 列名（DB） | 说明 |
| ----------- | ---- | ------------------ | ---- |
| `pyStr` | `String` | `py_str` | 拼音码 |
| `wbStr` | `String` | `wb_str` | 五笔码 |

### 4.2.2 值对象字段展开（MedicalInsuranceInfo）

| Java 字段名 | 类型 | @Column 列名（DB） | 说明 |
| ----------- | ---- | ------------------ | ---- |
| `ybNo` | `String` | `yb_no` | 医保编码 |
| `ybName` | `String` | `yb_name` | 医保名称 |
| `medinsId` | `String` | `medins_id` | 医疗服务机构标识 |
| `medinsAdmdvs` | `String` | `medins_admdvs` | 行政区划编码 |
| `medinsType` | `String` | `medins_type` | 医保机构类型 |
| `medinsLv` | `String` | `medins_lv` | 医保机构等级 |

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `OrganizationRepository extends JpaRepository<Organization, Long>`

**说明**：软删默认由框架过滤时，不强制追加 `AndIsDeletedFalse` 命名。

**排序口径（读侧统一，对齐 DM §9.1）**

| 规则 | 说明 |
| ---- | ---- |
| 同级排序键 | `display_order ASC NULLS LAST`，再 `id ASC` |
| 适用范围 | 科室维护列表、通用组织树/字典树、门诊挂号/入院/护士站科室下拉、责任科室树、登录选院区 |
| 维护入口 | 基础数据 · 科室管理 · 编辑弹窗「显示顺序」→ `modifyOrganization.displayOrder` |
| OU 同步 | 写后 `DepartmentAppService#applyOrganizationToOu` 将 `displayOrder` 映射为 identity `OrganizationUnit.sort_order` |

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `OutpatientOrganizationQueryModel`（对应 `listOrganizationsForOutpatientRegistration`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 机构标识 |
| `name` | `String` | `adm_organization.name` | 机构名称 |
| `classEnum` | `OrganizationClassification` | `adm_organization.class_enum` | 机构分类（Converter） |
| `displayOrder` | `Integer` | `adm_organization.display_order` | 展示顺序 |

#### 4.6.2 `OrganizationTreeNodeQueryModel`（对应 `loadOrganizationTreeForOutpatientRegistration`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 节点标识 |
| `parentId` | `Long` | `adm_organization.parent_id` | 父节点标识 |
| `busNo` | `String` | `adm_organization.bus_no` | 层级编码 |
| `name` | `String` | `adm_organization.name` | 节点名称 |

#### 4.6.3 `InpatientOrganizationQueryModel`（对应 `listOrganizationsForInpatientAdmission`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 科室标识 |
| `name` | `String` | `adm_organization.name` | 科室名称 |
| `classEnum` | `OrganizationClassification` | `adm_organization.class_enum` | 分类=住院 |
| `statusEnum` | `String` | `adm_organization.status_enum` | PublicationStatus code |

#### 4.6.4 `OrganizationDisplayQueryModel`（对应 `loadOrganizationByIdForDisplay`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 机构标识 |
| `name` | `String` | `adm_organization.name` | 展示名称 |
| `typeEnum` | `String` | `adm_organization.type_enum` | OrganizationType code |
| `statusEnum` | `String` | `adm_organization.status_enum` | PublicationStatus code |
| `isDeleted` | `Boolean` | `adm_organization.is_deleted` | 逻辑删除标记 |

#### 4.6.5 `OrganizationAbolishableCheckQueryModel`（对应 `checkOrganizationAbolishable`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `abolishable` | `Boolean` | 规则计算 | 是否可废弃 |
| `blockReasonCode` | `String` | 规则计算 | 阻断原因码 |
| `blockReasonMessage` | `String` | 规则计算 | 阻断说明 |
| `referenceCount` | `Long` | 规则计算 | 活跃引用数量 |

#### 4.6.6 `OrganizationTreeNodeQueryModel`（对应 `getOrganizationTree`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 节点标识 |
| `parentId` | `Long` | `adm_organization.parent_id` | 父节点标识 |
| `name` | `String` | `adm_organization.name` | 节点名称 |
| `busNo` | `String` | `adm_organization.bus_no` | 层级编码 |

#### 4.6.7 ~~`OrganizationTreeNodeQueryModel`（`getOrganizationTreeForPharmacy`）~~ **【已废止 2026-06-09】**

- 药房业务改由 **`adm_location`**（`LocationForm.PHARMACY`）+ `CurrentManagedLocationIdsResolver`；本 QueryModel **不再实现**。
- 发药 init Mapper 入参为 `List<Location>`（见 **AS-OP-PHA-001-SDD** §3.2、**AS-IP-PHA-001-SDD** §3.2）。

#### 4.6.8 ~~`OrganizationTreeNodeQueryModel`（`getOrganizationTreeForStorage`）~~ **【已废止 2026-06-09】**

- 药库业务改由 **`adm_location`**（`LocationForm.WAREHOUSE`）；本 QueryModel **不再实现**。
- `DepartmentAppService#getTreeForPharmacy` / `#getTreeForStorage` **已删除**。

#### 4.6.9 `OrganizationOptionDictQueryModel`（对应 `getOrganizationDictTree`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 选项值 |
| `label` | `String` | `adm_organization.name` | 选项展示 |
| `busNo` | `String` | `adm_organization.bus_no` | 层级编码 |
| `parentId` | `Long` | `adm_organization.parent_id` | 上级节点 |

#### 4.6.10 `OrganizationTreeNodeQueryModel`（对应 `getOrganizationTreeForResponsibilityDept`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 节点标识 |
| `name` | `String` | `adm_organization.name` | 节点名称 |
| `classEnum` | `OrganizationClassification` | `adm_organization.class_enum` | 责任科室分类 |
| `parentId` | `Long` | `adm_organization.parent_id` | 父节点 |

#### 4.6.11 `OrganizationSimpleQueryModel`（对应 `findFirstByBusNo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_organization.id` | 机构标识 |
| `busNo` | `String` | `adm_organization.bus_no` | 层级编码 |
| `name` | `String` | `adm_organization.name` | 机构名称 |
| `statusEnum` | `String` | `adm_organization.status_enum` | PublicationStatus code |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `OrganizationDomainService`

#### 5.1.1 `createOrganization(CreateOrganizationCommand cmd)` → `Long`

**命令文件**：`CreateOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 机构名称 |
| `parentId` | `Long` | 父机构标识 |
| `typeEnum` | `String` | OrganizationType code |
| `classEnum` | `OrganizationClassification` | 机构分类 |
| `displayOrder` | `Integer` | 展示顺序 |
| `inputCode` | `InputCodeCommand` | 输入码 |
| `insuranceInfo` | `MedicalInsuranceInfoCommand` | 医保信息 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertCreateAllowed` |
| `statusTransitionMethod` | `Organization.initAsActive` |
| `repositoryChecks` | `OrganizationRepository.existsByParentIdAndName` |
| `eventPublishAnchor` | `new OrganizationCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-001` |

**方法契约摘要**：前置条件为同级名称唯一、层级编码合法、类型组合合法；**医院节点**（`HOSPITAL`）医保字段均可空、须无 `parent_id`；**科室节点**若填 `medins_id` 则 `medins_lv` 必填；持久化影响为新增组织节点并初始化为 **ACTIVE**；失败语义为约束冲突时拒绝写入；事件触发为发布 `OrganizationCreatedEvent`。**OU 同步不在本方法内**，由 `DepartmentAppService#syncCreateOu` 编排。

---

#### 5.1.2 `publishOrganization(PublishOrganizationCommand cmd)` → `void`

**命令文件**：`PublishOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 机构标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertPublishAllowed` |
| `statusTransitionMethod` | `Organization.transitToActive` |
| `repositoryChecks` | `OrganizationRepository.findById` |
| `eventPublishAnchor` | `new OrganizationPublishedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-002` |

**方法契约摘要**：前置条件为仅 DRAFT 可发布且必填字段完整；持久化影响为 `statusEnum=ACTIVE` 且 `activeFlag=true`；失败语义为状态非法或字段缺失时拒绝；事件触发为发布 `OrganizationPublishedEvent`。**注释锚点**：加载 -> 校验 -> 状态迁移 -> 事件。

---

#### 5.1.3 `modifyOrganization(ModifyOrganizationCommand cmd)` → `void`

**命令文件**：`ModifyOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 机构标识 |
| `name` | `String` | 机构名称 |
| `classEnum` | `OrganizationClassification` | 机构分类 |
| `displayOrder` | `Integer` | 展示顺序 |
| `defDoctorId` | `Long` | 默认挂号医生 |
| `inputCode` | `InputCodeCommand` | 输入码 |
| `insuranceInfo` | `MedicalInsuranceInfoCommand` | 医保信息 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertModifiable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `OrganizationRepository.existsByParentIdAndNameAndIdNot` |
| `eventPublishAnchor` | `new OrganizationEditedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-003` |

**方法契约摘要**：前置条件为 DRAFT/ACTIVE 可修改且名称同级唯一；持久化影响为更新属性并保持状态不变；失败语义为重名或非法状态时报错；事件触发为发布 `OrganizationEditedEvent`。**注释锚点**：校验 -> 变更 -> 持久化 -> 事件。

---

#### 5.1.4 `relocateOrganization(RelocateOrganizationCommand cmd)` → `void`

**命令文件**：`RelocateOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 机构标识 |
| `newParentId` | `Long` | 新上级机构标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertRelocateAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `OrganizationRepository.existsCyclePath` |
| `eventPublishAnchor` | `new OrganizationHierarchyChangedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-004` |

**方法契约摘要**：前置条件为节点处于 ACTIVE 且新上级不形成环；持久化影响为更新 `parentId` 并递归重算 `busNo`；失败语义为环路或上级非法时报错；事件触发为发布 `OrganizationHierarchyChangedEvent`。**注释锚点**：环校验 -> 计算新编码 -> 批量更新 -> 事件。

---

#### 5.1.5 `retireOrganization(RetireOrganizationCommand cmd)` → `void`

**命令文件**：`RetireOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 机构标识 |
| `retireReason` | `String` | 停用原因 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertRetireAllowed` |
| `statusTransitionMethod` | `Organization.transitToRetired` |
| `repositoryChecks` | `OrganizationRepository.existsActiveChildren` |
| `eventPublishAnchor` | `new OrganizationRetiredEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-005` |

**方法契约摘要**：前置条件为仅 ACTIVE 可停用且子节点状态满足约束；持久化影响为状态迁移到 RETIRED 并 `activeFlag=false`；失败语义为子节点冲突或状态不匹配时报错；事件触发为发布 `OrganizationRetiredEvent`。**注释锚点**：校验 -> 迁移 -> 持久化 -> 事件。

---

#### 5.1.6 `reactivateOrganization(ReactivateOrganizationCommand cmd)` → `void`

**命令文件**：`ReactivateOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 机构标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertReactivateAllowed` |
| `statusTransitionMethod` | `Organization.transitToActive` |
| `repositoryChecks` | `OrganizationRepository.findParentStatusById` |
| `eventPublishAnchor` | `new OrganizationReactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-006` |

**方法契约摘要**：前置条件为仅 RETIRED 可重新启用且父节点必须 ACTIVE；持久化影响为状态迁移 ACTIVE 且启用标记恢复；失败语义为父节点不可用或状态非法时报错；事件触发为发布 `OrganizationReactivatedEvent`。**注释锚点**：校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.7 `abolishOrganization(AbolishOrganizationCommand cmd)` → `void`

**命令文件**：`AbolishOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `organizationId` | `Long` | 机构标识 |
| `abolishReason` | `String` | 废弃原因 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Organization.assertAbolishAllowed` |
| `statusTransitionMethod` | `Organization.transitToDeleted` |
| `repositoryChecks` | `OrganizationQueryService.checkOrganizationAbolishable` |
| `eventPublishAnchor` | `new OrganizationAbolishedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-ORG-W-007` |

**方法契约摘要**：前置条件为废弃前引用扫描通过（**ACTIVE 亦可废弃**）；持久化影响为 `statusEnum=DISCARDED`、`isDeleted=true`、`activeFlag=false`；失败语义为仍有活跃引用时拒绝；事件触发为发布 `OrganizationAbolishedEvent`。OU 软删由 `DepartmentAppService#syncDeleteOu` 编排。

### 5.2 读侧 — `OrganizationQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listOrganizationsForOutpatientRegistration` | `OutpatientOrganizationQuery` | `List<OutpatientOrganizationQueryModel>` | 按门诊口径过滤 ACTIVE 节点；**排序** `display_order ASC NULLS LAST, id ASC` | `TC-ORG-R-001` |
| `loadOrganizationTreeForOutpatientRegistration` | `OutpatientOrganizationTreeQuery` | `List<OrganizationTreeNodeQueryModel>` | 构建门诊可见树（源列表已按 displayOrder 排序） | `TC-ORG-R-002` |
| `listOrganizationsForInpatientAdmission` | `InpatientOrganizationQuery` | `List<InpatientOrganizationQueryModel>` | 住院科室口径过滤；**排序** `display_order ASC NULLS LAST, id ASC` | `TC-ORG-R-003` |
| `loadOrganizationByIdForDisplay` | `Long organizationId` | `OrganizationDisplayQueryModel` | 单条机构展示查询 | `TC-ORG-R-004` |
| `checkOrganizationAbolishable` | `Long organizationId` | `OrganizationAbolishableCheckQueryModel` | 聚合活跃引用阻断校验 | `TC-ORG-R-005` |
| `getOrganizationTree` | `OrganizationTreeQuery` | `List<OrganizationTreeNodeQueryModel>` | 全量启用组织树；**排序** `display_order ASC NULLS LAST, id ASC` | `TC-ORG-R-006` |
| ~~`getOrganizationTreeForPharmacy`~~ | — | — | **已废止**；替代：`LocationQueryService` + `CurrentManagedLocationIdsResolver` | ~~`TC-ORG-R-007`~~ |
| ~~`getOrganizationTreeForStorage`~~ | — | — | **已废止**；替代：`LocationQueryService#listAllActiveWarehouses` 等 | ~~`TC-ORG-R-008`~~ |
| `mapNamesByIds` | `Collection<Long> organizationIds` | `Map<Long, String>` | 批量解析机构名称（开单科室展示等）；**非**药房下拉 | `TC-ORG-R-012` |
| `getOrganizationDictTree` | `OrganizationDictQuery` | `List<OrganizationOptionDictQueryModel>` | 通用字典树投影（与 `getOrganizationTree` 同源排序） | `TC-ORG-R-009` |
| `getOrganizationTreeForResponsibilityDept` | `ResponsibilityDeptTreeQuery` | `List<OrganizationTreeNodeQueryModel>` | 责任科室口径树；过滤后内存按 displayOrder 排序 | `TC-ORG-R-010` |
| `findFirstByBusNo` | `String busNo` | `OrganizationSimpleQueryModel` | 按层级编码精确查询 | `TC-ORG-R-011` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-ORG-001 | `busNo` 全局唯一，创建/调整时唯一校验 |
| BR-ORG-002 | 子节点编码按父编码规则生成 |
| BR-ORG-003 | 同父级名称唯一 |
| BR-ORG-004 | ACTIVE 不可直接废弃，须先 RETIRED |
| BR-ORG-005 | 调整上级禁止形成环 |
| BR-ORG-006 | 有 ACTIVE 子节点时禁止单独停用 |
| BR-ORG-007 | 重新启用要求父节点 ACTIVE |
| BR-ORG-008 | 活跃引用存在时禁止废弃 |
| BR-ORG-009 | 科室节点：有 `medins_id` 则 `medins_lv` 必填；医院根节点医保字段均可空；**禁止**用 Organization PHARMACY/STORAGE 分类选药房/药库 |
| BR-ORG-016 | 医院须为单租户根：`parent_id` 为空；维护时无 `class_enum`/默认医师/科别 |
| BR-ORG-010 | DRAFT 不可被下游新业务引用 |
| BR-ORG-011 | Organization 写操作后应用层按 **code** 轻同步 OU；**不要求**与 OrganizationUnit 主键一致 |
| BR-ORG-012 | 写操作启用乐观锁防并发覆盖 |
| BR-ORG-013 | RETIRED/DISCARDED 禁止新业务选用 |
| BR-ORG-014 | 历史展示依赖快照或可读加载 |
| BR-ORG-015 | 废弃执行两阶段（停用 -> 引用清理 -> 废弃） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `OrganizationCreatedEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,name,typeEnum,busNo,parentId` | `createOrganization` |
| `OrganizationPublishedEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,name,typeEnum,classEnum` | `publishOrganization` |
| `OrganizationEditedEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,changedFields` | `modifyOrganization` |
| `OrganizationRetiredEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,retireReason,operatorId` | `retireOrganization` |
| `OrganizationReactivatedEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,operatorId` | `reactivateOrganization` |
| `OrganizationAbolishedEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,abolishReason,operatorId` | `abolishOrganization` |
| `OrganizationHierarchyChangedEvent` | `com.zxplat.domain.share.events.administration.organization` | `organizationId,oldParentId,newParentId,newBusNo` | `relocateOrganization` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- Organization 聚合仅允许逻辑删除，禁止物理删除。
- `statusEnum=DISCARDED`（兼容存量 `DELETED`）同时要求 `isDeleted=true`，并退出所有可选业务列表。
- `RETIRED/DISCARDED` 节点不可作为新业务引用目标。
- 历史展示可通过快照字段或 `loadOrganizationByIdForDisplay` 返回展示态信息。
- 废弃前必须通过 `checkOrganizationAbolishable`，否则拒绝删除。

---

## 9. 框架优先适配规则

- 软删、审计、事务、beforeCommit 事件发布遵循项目框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认引入接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口，复杂查询在 `queries` 实现。
- 分页与过滤在数据库侧完成，确保统计口径一致。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 7，与 DM §6.1 方法名一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 11 个方法
- [ ] §6 BR 映射覆盖 BR-ORG-001 至 BR-ORG-015
- [ ] §7 事件表覆盖 DM §5 全部 7 条事件
- [ ] 软删、层级编码、状态流转与引用阻断规则一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-004-Organization-机构科室.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.administration.organization`

### 11.2 DoD

- 写侧 7 个方法全部落地，方法签名与 Command 命名与 §5 一致。
- `Organization` 字段、层级规则、状态机、软删语义与 §4/§6 一致。
- 所有事件按 §7 beforeCommit 发布，载荷不少于最小载荷。
- BR 至少一条绑定一个测试用例 ID，可追溯至 `unitTestCaseId`。
- 读侧 11 个方法与 §4.6 QueryModel 对齐，分页口径一致。

### 11.3 失败处理

- 任一写方法契约无法实现时立即停机并回到文档会话修订。
- 若 DM 白名单方法、事件或 BR 变更，先更新 SDD 再进入代码会话。
- 若引用阻断规则或历史展示规则未达标，DoD 失败不得进入成码。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（7）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（7）
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
| 2026-08-27 | **display_order 读侧排序落地**：仓储增 `OrderByDisplayOrderAscIdAsc`；门诊/入院/护士站/通用树/管理列表/登录选院区统一 `display_order ASC NULLS LAST, id ASC`；科室维护页可编辑排序号 |
| 2026-06-09 | **Organization/Location 分工**：废止 §4.6.7/4.6.8 与 `getOrganizationTreeForPharmacy/Storage`；增 `mapNamesByIds`；修订 BR-ORG-009/016、医院医保可选、`assertCreateAllowed` 契约；对齐 DM **v1.2.0** |
| 2026-06-03 | **M2 对齐代码**：PublicationStatus + initAsActive；typeEnum/classEnum/statusEnum **String**；OU **code 轻关联**（去 @MapsId）；BR-ORG-011/013/abolish 修订 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致 |

---

**本 SDD 正文结束。**
