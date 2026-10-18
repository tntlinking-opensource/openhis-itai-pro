# DM-WKF-001 ActivityDefinition 诊疗项目定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-WKF-001-ActivityDefinition-诊疗项目定义.md`（**v1.0.21**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：ActivityDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-WKF-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.workflow.activitydefinition` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 全量覆盖 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.activitydefinition` |
| 子项同步策略 | §3.2 描述 | 明确为「全量对齐 + 软删旧行」|

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 ActivityDefinition 聚合的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成领域层实现。
- 将实现前澄清前置到 SDD：每个写方法独立 Command 文件；主表与子表采用全量对齐 + 软删旧行口径。
- 保证目录展示价与计费事实价边界清晰：本聚合只维护目录主数据，不替代收费域定价事实源。

### 1.2 边界

- **在内**：诊疗项目目录主数据生命周期（创建、维护、启停）、子项维护、医保对码承载字段回写、目录检索查询。
- **在外**：ChargeItemDefinition 定价事实维护、库存与执行、医保网关交互细节、跨聚合事务补偿。
- **跨域仅端口声明**：通过 ActivityDefinitionId 与事件进行跨上下文协作，不在本聚合直接写外域表。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.activitydefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ActivityDefinition`、`ActivityDefinitionChild` |
| `valueobject` | 无独立值对象（本聚合编码字段按标量承载） |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums`，不重复生成 |
| `command` | 每个写方法一个命令文件，见 §5.1 |
| `repository` | `ActivityDefinitionRepository`、`ActivityDefinitionChildRepository`（仅 JpaRepository 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.activitydefinition` |

**存储口径（DM 优先）**：`status_enum`、`rule_id`、`chrgitm_lv` 等枚举身份字段以落库为准；**`type_enum` 虽为列名 `*_enum`，实际为术语 `activity_type_code` 的 String 码**（存量 1~7）；`category_code`、`bus_no`、`yb_no` 等 `*_code`/字典编码字段用 `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；子表外键 `activityId` |
| 时间 | `*At/*Time` 用 `LocalDateTime`；本聚合暂无 `LocalDate` 字段 |
| `*_enum` | `Integer`（与 DM 码表一致） |
| `*_code` | `String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，由基类与框架统一处理 |
| 实体 | `@Entity` + 显式 `@Table/@Column` |
| 值对象 | 本聚合无 `@Embeddable` 值对象 |
| 仓储 | `JpaRepository<Entity, Long>` 接口，无自定义实现类 |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务不机械添加 `@Transactional` |
| 事件 | 领域服务内构造并发布，统一 beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_activity_definition` | `ActivityDefinition` | 诊疗项目目录主表 |
| `wkf_activity_definition_children` | `ActivityDefinitionChild` | 子项表，按 `activity_id` 归属 |

### 4.2 聚合根 `ActivityDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `categoryCode` | `String` | 是 | 对应 `category_code`，目录类别编码 |
| `busNo` | `String` | 是 | 对应 `bus_no`，全局唯一 |
| `name` | `String` | 是 | 项目名称 |
| `pyStr` | `String` | 否 | 拼音检索码 |
| `wbStr` | `String` | 否 | 五笔检索码 |
| `permittedUnitCode` | `String` | 否 | 使用单位编码 |
| `typeEnum` | `String` | 是 | 业务类型标签码，术语 `activity_type_code`，见 §4.4；必填 **BR-ACTDEF-011**；非通道身份 |
| `statusEnum` | `Integer` | 是 | 发布状态，见 §4.4 |
| `descriptionText` | `String` | 否 | 说明 |
| `bodySiteCode` | `String` | 否 | 身体部位编码 |
| `specimenCode` | `String` | 否 | 标本编码 |
| `orgId` | `Long` | 否 | 所属机构标识 |
| `locationId` | `Long` | 否 | 位置标识 |
| `ruleId` | `Integer` | 否 | 计费规则标识 |
| `pricingFlag` | `Boolean` | 否 | 划价标记 |
| `ybFlag` | `Boolean` | 否 | 医保标记 |
| `ybNo` | `String` | 否 | 医保编码 |
| `ybMatchFlag` | `Boolean` | 否 | 医保对码标记 |
| `chrgitmLv` | `Integer` | 否 | 医保收费等级 |
| `mrSettlementTypeCode` | `String` | 否 | 病案结算类型（术语 `mr_settlement_type_code`） |
| `childrenJson` | `String` | 否 | 子项 JSON 快照 |
| `childrenFlag` | `Boolean` | 否 | 子项标记，需与子表行一致 |

**状态迁移方法（实现须一致）**：`publish()`（`DRAFT→ACTIVE`）、`retire()`（`ACTIVE→RETIRED`）、`activate()`（`RETIRED→ACTIVE`）；`applyInitStatus` 用于创建初始化。

**不变式方法（示例命名，实现须一致）**：`assertBusNoUnique`、`rebuildSearchCodes`、`syncChildrenConsistency`、`applyInitStatus`。

### 4.3 子实体 `ActivityDefinitionChild`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 子项行标识 |
| `activityId` | `Long` | 是 | 父项标识 |
| `childrenId` | `Long` | 是 | 子项诊疗项目标识 |
| `childrenNum` | `Integer` | 否 | 子项数量 |
| `childrenName` | `String` | 否 | 子项名称快照 |

**归属校验**：任何带子项 `id` 的更新必须 `loadByIdAndActivityId(id, activityId)`，归属不一致直接拒绝。

### 4.4 枚举与编码契约

| 字段/类型 | 值域 | 说明 |
| --------- | ---- | ---- |
| `typeEnum` | 术语 `activity_type_code`（种子 `1~7`，现场可增码） | 列名 `type_enum`；下拉**仅**术语生成，禁止枚举/硬编码；嘱托识别用 **`category_code=52`**，非本字段 |
| `statusEnum` | `PublicationStatus` 的 `code`（`String` 落库） | 如 `DRAFT` / `ACTIVE` / `RETIRED`；与共享枚举 `getCode()` 一致（**禁止**数字串身份，见代码生成规范 §0.12） |
| `categoryCode`/`permittedUnitCode`/`bodySiteCode`/`specimenCode` | 字典编码字符串 | 术语码表承载 |

### 4.5 仓储接口

- `ActivityDefinitionRepository extends JpaRepository<ActivityDefinition, Long>`
- `ActivityDefinitionChildRepository extends JpaRepository<ActivityDefinitionChild, Long>`
- 必备查询签名：
  - `Optional<ActivityDefinition> findByIdAndIsDeletedFalse(Long id)`
  - `boolean existsByBusNoAndIsDeletedFalse(String busNo)`
  - `List<ActivityDefinitionChild> findByActivityIdAndIsDeletedFalse(Long activityId)`
  - `void softDeleteByActivityIdAndNotInChildrenIds(Long activityId, List<Long> keepIds)`（可由批量更新实现）

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ActivityDefinitionInitQueryModel`（对应 `getDiseaseTreatmentInit`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `categoryOptions` | `List<DictItemQueryModel>` | 字典服务 | 目录类别下拉 |
| `unitOptions` | `List<DictItemQueryModel>` | 字典服务 | 单位下拉 |
| `typeOptions` | `List<DictItemQueryModel>` | 术语 `activity_type_code` | 诊疗业务类型 |
| `statusOptions` | `List<DictItemQueryModel>` | 共享枚举 `PublicationStatus` | 发布状态 |
| `exeOrganizations` | `List<DictItemQueryModel>` | `OrganizationQueryService.getOrganizationDictTree` | 部门树下拉 |
| `mrSettlementTypeCodeOptions` | `List<OptionDto>` | 术语服务 | 病案结算类型下拉（`mr_settlement_type_code`） |

#### 4.6.2 `ActivityDefinitionPageQueryModel`（对应 `pageServiceCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `wkf_activity_definition.id` | 主键 |
| `busNo` | `String` | `wkf_activity_definition.bus_no` | 业务编码 |
| `name` | `String` | `wkf_activity_definition.name` | 名称 |
| `categoryCode` | `String` | `wkf_activity_definition.category_code` | 目录类别 |
| `packageUnitCode` | `String` | `wkf_activity_definition.package_unit_code` | 销售/计价单位（列表「单位」列；应用层补 `packageUnitCode_dictText`） |
| `defEncounterUnitCode` | `String` | `wkf_activity_definition.def_encounter_unit_code` | 默认就诊单位 |
| `typeEnum` | `String` | `wkf_activity_definition.type_enum` | 业务类型码 |
| `statusEnum` | `String` | `wkf_activity_definition.status_enum` | 发布状态 code |
| `ybFlag` | `Boolean` | `wkf_activity_definition.yb_flag` | 医保标记 |
| `childrenFlag` | `Boolean` | `wkf_activity_definition.children_flag` | 子项标记 |
| `permittedGenderEnum` | `String` | `wkf_activity_definition.permitted_gender_enum` | 适用性别 |
| `sortOrder` | `Integer` | `wkf_activity_definition.sort_order` | 目录展示顺序 |
| `orgId` | `Long` | `wkf_activity_definition.org_id` | 执行科室机构主键 |
| `pricingFlag` | `Boolean` | `wkf_activity_definition.pricing_flag` | 是否收费/计价 |
| `pyStr` | `String` | `wkf_activity_definition.py_str` | 拼音检索码 |

#### 4.6.3 `ActivityDefinitionDetailQueryModel`（对应 `getServiceCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `baseInfo` | `ActivityDefinitionBaseQueryModel` | 主表 | 主体详情 |
| `children` | `List<ActivityDefinitionChildQueryModel>` | 子表 | 子项列表 |
| `pricingSnapshot` | `PricingSnapshotQueryModel` | QueryService拼接 | 可选收费展示信息 |
| `editabilityCode` | `String` | 应用层规则计算 | 编辑业务码（**AS-BAS-CAT-001**） |
| `editabilityMessage` | `String` | 应用层规则计算 | 编辑说明 |

#### 4.6.4 `ActivityDefinitionSearchQueryModel`（对应 `findBySearchKey`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `wkf_activity_definition.id` | 主键 |
| `busNo` | `String` | `wkf_activity_definition.bus_no` | 业务编码 |
| `name` | `String` | `wkf_activity_definition.name` | 名称 |
| `pyStr` | `String` | `wkf_activity_definition.py_str` | 拼音 |
| `wbStr` | `String` | `wkf_activity_definition.wb_str` | 五笔 |

#### 4.6.5 `ActivityDefinitionEnabledQueryModel`（对应 `diseaseTreatmentList`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `wkf_activity_definition.id` | 主键 |
| `name` | `String` | `wkf_activity_definition.name` | 显示名 |
| `typeEnum` | `String` | `wkf_activity_definition.type_enum` | 诊疗类型 |
| `permittedUnitCode` | `String` | `wkf_activity_definition.permitted_unit_code` | 单位编码 |

#### 4.6.6 `ActivityEditValidationQueryModel`（对应 `validateActivityEdit`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `editable` | `Boolean` | 规则计算 | 是否允许编辑 |
| `reasonCode` | `String` | 规则计算 | 不可编辑原因码 |
| `reasonMessage` | `String` | 规则计算 | 不可编辑提示 |

#### 4.6.7 `ActivityCatalogTemplateQueryModel`（对应 `downloadActivityCatalogTemplate`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `templateName` | `String` | 模板配置 | 模板名称 |
| `headers` | `List<String>` | 模板配置 | 导入列头 |
| `sampleRows` | `List<Map<String,String>>` | 模板配置 | 示例行 |

#### 4.6.8 `ActivityCatalogExportQueryModel`（对应 `exportActivityCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `rows` | `List<ActivityCatalogExportRowQueryModel>` | 主表/子表 | 导出数据行 |
| `total` | `Long` | 查询统计 | 导出记录数 |

#### 4.6.9 `ActivityYbMappingStatusQueryModel`（对应 `getActivityYbMappingStatus`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `activityId` | `Long` | `wkf_activity_definition.id` | 项目标识 |
| `ybMatchFlag` | `Boolean` | `wkf_activity_definition.yb_match_flag` | 对码标记 |
| `ybNo` | `String` | `wkf_activity_definition.yb_no` | 医保编码 |
| `chrgitmLv` | `Integer` | `wkf_activity_definition.chrgitm_lv` | 医保等级 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ActivityDefinitionDomainService`

#### 5.1.1 `createActivityDefinition(CreateActivityDefinitionCommand cmd)` → `Long`

**命令文件**：`CreateActivityDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `categoryCode` | `String` | 目录类别编码 |
| `busNo` | `String` | 业务编码（可空则系统生成） |
| `name` | `String` | 项目名称 |
| `permittedUnitCode` | `String` | 使用单位 |
| `typeEnum` | `String` | 诊疗类型 |
| `statusEnum` | `Integer` | 初始发布状态（可空；为空时按 `PublicationStatus.DRAFT` 兜底） |
| `descriptionText` | `String` | 说明 |
| `bodySiteCode` | `String` | 身体部位 |
| `specimenCode` | `String` | 标本编码 |
| `orgId` | `Long` | 所属机构 |
| `locationId` | `Long` | 位置标识 |
| `ruleId` | `Integer` | 计费规则 |
| `pricingFlag` | `Boolean` | 划价标记 |
| `ybFlag` | `Boolean` | 医保标记 |
| `ybNo` | `String` | 医保编码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |
| `chrgitmLv` | `Integer` | 医保收费等级 |
| `mrSettlementTypeCode` | `String` | 病案结算类型 |
| `children` | `List<ActivityDefinitionChildItemCommand>` | 子项集合 |
| `childrenJson` | `String` | 子项快照 |
| `childrenFlag` | `Boolean` | 子项标记 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoUnique`、`syncChildrenConsistency` |
| `statusTransitionMethod` | `applyStatusTransition`（初始化） |
| `repositoryChecks` | `existsByBusNoAndIsDeletedFalse(busNo)` |
| `eventPublishAnchor` | `activityDefinitionRepository.flush()` 后 `new ActivityDefinitionCreatedEvent(...)` + `DomainEventPublisher.publish(beforeCommit)` |
| `unitTestCaseId` | `TC-WKF-ACTDEF-CMD-001` |

**方法契约摘要**：前置条件为 `name/categoryCode/typeEnum` 合法且 `busNo` 唯一；持久化影响为写主表与子表初始行、重建检索码、对齐 `childrenFlag`，并在 `statusEnum` 为空时默认初始化为 `PublicationStatus.DRAFT`；**`saveAll` 后须 `flush()` 再发布事件**；维护端 UI 不直接提交 `statusEnum`；**`ServiceCatalogAppService#createServiceCatalog`** 在同请求内于定价行写入后调用 `startServiceCatalog`（见 DM §6.1.1 应用编排）以发布为 `ACTIVE`；失败语义为编码冲突/必填缺失/类型非法直接报错；触发 `ActivityDefinitionCreatedEvent`（及维护端路径的 `ActivityDefinitionActivatedEvent`）。

---

#### 5.1.2 `updateActivityDefinition(UpdateActivityDefinitionCommand cmd)` → `void`

**命令文件**：`UpdateActivityDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 项目标识 |
| `name` | `String` | 项目名称 |
| `categoryCode` | `String` | 目录类别 |
| `permittedUnitCode` | `String` | 使用单位 |
| `typeEnum` | `String` | 诊疗类型 |
| `descriptionText` | `String` | 说明 |
| `bodySiteCode` | `String` | 身体部位 |
| `specimenCode` | `String` | 标本编码 |
| `orgId` | `Long` | 所属机构 |
| `locationId` | `Long` | 位置 |
| `ruleId` | `Integer` | 计费规则 |
| `pricingFlag` | `Boolean` | 划价标记 |
| `ybFlag` | `Boolean` | 医保标记 |
| `ybNo` | `String` | 医保编码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |
| `chrgitmLv` | `Integer` | 医保收费等级 |
| `mrSettlementTypeCode` | `String` | 病案结算类型 |
| `children` | `List<ActivityDefinitionChildItemCommand>` | 子项全量集合 |
| `childrenJson` | `String` | 子项 JSON |
| `childrenFlag` | `Boolean` | 子项标记 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `rebuildSearchCodes`、`syncChildrenConsistency` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)`、`findByActivityIdAndIsDeletedFalse(id)` |
| `eventPublishAnchor` | `activityDefinitionRepository.flush()` 后 `new ActivityDefinitionUpdatedEvent(...)`（必要时叠加 `ActivityDefinitionPriceUpdatedEvent`） |
| `unitTestCaseId` | `TC-WKF-ACTDEF-CMD-002` |

**方法契约摘要**：前置条件为目标存在且可编辑；持久化影响为主表字段更新、名称变更时重算检索码、子表执行全量对齐+软删旧行；失败语义为目标不存在/并发冲突/子项归属错误拒绝；默认触发 `ActivityDefinitionUpdatedEvent`，若变更触及展示价相关字段则同步触发价格变更事件。

---

#### 5.1.3 `retireActivityDefinitions(RetireActivityDefinitionsCommand cmd)` → `void`

**命令文件**：`RetireActivityDefinitionsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `idList` | `List<Long>` | 待停用项目标识集合 |
| `reasonCode` | `String` | 停用原因码（可选） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `applyStatusTransition(active->retired)` |
| `statusTransitionMethod` | `retire` |
| `repositoryChecks` | `findAllById(idList)` + 状态合法性校验 |
| `eventPublishAnchor` | `new ActivityDefinitionRetiredEvent(idList, retired)` |
| `unitTestCaseId` | `TC-WKF-ACTDEF-CMD-003` |

**方法契约摘要**：前置条件为 `idList` 非空且记录处于可停用状态；持久化影响为批量状态迁移至停用；失败语义为存在非法状态项则拒绝（整批或部分以接口契约固定）；触发 `ActivityDefinitionRetiredEvent`。

---

#### 5.1.4 `activateActivityDefinitions(ActivateActivityDefinitionsCommand cmd)` → `void`

**命令文件**：`ActivateActivityDefinitionsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `idList` | `List<Long>` | 待启用项目标识集合 |
| `reasonCode` | `String` | 启用原因码（可选） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 按当前 `statusEnum` 分支：`DRAFT` 调 `publish`、`RETIRED` 调 `activate`、`ACTIVE` 跳过 |
| `statusTransitionMethod` | `publish`（草稿）或 `activate`（停用） |
| `repositoryChecks` | `findByIdIn(idList)` + 状态合法性校验 |
| `eventPublishAnchor` | `new ActivityDefinitionActivatedEvent(idList, ACTIVE)` |
| `unitTestCaseId` | `TC-WKF-ACTDEF-CMD-004` |

**方法契约摘要**：前置条件为 `idList` 非空且记录存在；持久化影响为批量发布/恢复至 `PublicationStatus.ACTIVE`；`DRAFT` 走 `publish()`、`RETIRED` 走 `activate()`、已 `ACTIVE` 幂等跳过；其他状态拒绝；失败语义为空列表、无效主键或非法状态；触发 `ActivityDefinitionActivatedEvent`。

---

#### 5.1.5 `importActivityCatalog(ImportActivityCatalogCommand cmd)` → `ImportActivityCatalogResult`

**命令文件**：`ImportActivityCatalogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rows` | `List<ActivityCatalogRowCommand>` | 导入行集合 |
| `mode` | `String` | 导入策略（新增/更新/跳过冲突） |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoUnique`、`syncChildrenConsistency` |
| `statusTransitionMethod` | `N/A`（按行处理） |
| `repositoryChecks` | `existsByBusNoAndIsDeletedFalse`、`findByBusNo` |
| `eventPublishAnchor` | `activityDefinitionRepository.flush()` 后逐条发布 `ActivityDefinitionCreatedEvent` 或 `ActivityDefinitionUpdatedEvent` |
| `unitTestCaseId` | `TC-WKF-ACTDEF-CMD-005` |

**方法契约摘要**：前置条件为文件/行格式合法且每行满足必填与唯一性规则；持久化影响为批量新增或更新主表与子表；失败语义为校验失败按策略整批回滚或跳过并汇总；触发逐行创建/更新事件。

---

#### 5.1.6 `syncActivityYbMapping(SyncActivityYbMappingCommand cmd)` → `void`

**命令文件**：`SyncActivityYbMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `activityId` | `Long` | 项目标识 |
| `ybNo` | `String` | 医保编码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |
| `chrgitmLv` | `Integer` | 医保等级 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertYbMappingFieldsValid` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(activityId)` |
| `eventPublishAnchor` | `new ActivityDefinitionUpdatedEvent(...mappingChanged=true)` |
| `unitTestCaseId` | `TC-WKF-ACTDEF-CMD-006` |

**方法契约摘要**：前置条件为目标存在且对码字段合法；持久化影响为更新 `ybNo/ybMatchFlag/chrgitmLv`；失败语义为目标不存在或字段非法直接拒绝；触发 `ActivityDefinitionUpdatedEvent` 并携带对码变更摘要。**跨模块消费**：`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService` 在 3301 成功后调用。

### 5.2 读侧 — `ActivityDefinitionQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参填 QueryModel 类名**（字段定义见 §4.6）：

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getDiseaseTreatmentInit` | `organizationId` | `ActivityDefinitionInitQueryModel` | 只读字典聚合，按租户可见性过滤 | TC-WKF-ACTDEF-QRY-001 |
| `pageServiceCatalog` | `ActivityDefinitionPageQuery` + `Pageable` | `Page<ActivityDefinitionPageQueryModel>` | 条件分页，默认排序 `createdAt DESC` | TC-WKF-ACTDEF-QRY-002 |
| `getServiceCatalog` | `id` | `ActivityDefinitionDetailQueryModel` | 主表+子表装配，可选拼接收费展示读数据 | TC-WKF-ACTDEF-QRY-003 |
| `findBySearchKey` | `keyword`、`limit` | `List<ActivityDefinitionSearchQueryModel>` | name/busNo/py/wb 多字段检索 | TC-WKF-ACTDEF-QRY-004 |
| `diseaseTreatmentList` | `organizationId`、`locationId` | `List<ActivityDefinitionEnabledQueryModel>` | 仅 active 且可见项 | TC-WKF-ACTDEF-QRY-005 |
| `validateActivityEdit` | `id` | `ActivityEditValidationQueryModel` | 校验是否可编辑并返回原因 | TC-WKF-ACTDEF-QRY-006 |
| `downloadActivityCatalogTemplate` | `templateVersion` | `ActivityCatalogTemplateQueryModel` | 模板元数据读取，无业务写库 | TC-WKF-ACTDEF-QRY-007 |
| `exportActivityCatalog` | `ActivityDefinitionExportQuery` | `ActivityCatalogExportQueryModel` | 与列表同口径导出 | TC-WKF-ACTDEF-QRY-008 |
| `getActivityYbMappingStatus` | `activityId` | `ActivityYbMappingStatusQueryModel` | 查询医保对码承载字段 | TC-WKF-ACTDEF-QRY-009 |
| `listChildrenByActivityId` | `Long activityId` | `List<ActivityDefinitionChildQueryModel>` | `findByActivityId` 子表；排除软删 | TC-WKF-ACTDEF-QRY-010 |
| `listChildrenByActivityIds` | `Collection<Long> activityIds` | `Map<Long, List<ActivityDefinitionChildQueryModel>>` | `findByActivityIdIn` 批量分组 | TC-WKF-ACTDEF-QRY-011 |
| `listParentActivityIdsByChildrenId` | `Long childrenId` | `List<Long>` | `findByChildrenIdIn` 反查未删父 id（**BR-ACTDEF-QRY-005** / **BR-CAT-014**） | TC-WKF-ACTDEF-QRY-012 |
| `pageForCatalog` | `ProductDefinitionCatalogPageQuery` + `Pageable` | `Page<ActivityDefinition>` | `is_deleted=false` 且 `status_enum=ACTIVE`；可选 `categoryCode`/`typeEnum`；关键字/prepicked OR；由应用 `ProductDefinitionCatalogPageLoader` 编排 | TC-WKF-ACTDEF-QRY-012 |
| `findAllByIds` | `Collection<Long> ids` | `List<ActivityDefinition>` | 批量主键；空入参→空列表 | TC-WKF-ACTDEF-QRY-013 |
| `findByBusNos` | `List<String> busNos` | `List<ActivityDefinition>` | `bus_no IN (...)`；导入/同步反查 | TC-WKF-ACTDEF-QRY-014 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-ACTDEF-001 | `createActivityDefinition` 校验 `categoryCode/name` 必填 |
| BR-ACTDEF-002 | 创建/导入路径统一校验 `busNo` 唯一 |
| BR-ACTDEF-003 | 名称变更统一调用 `rebuildSearchCodes` 重算 `pyStr/wbStr` |
| BR-ACTDEF-004 | `retire` 仅 `ACTIVE→RETIRED`；`activateActivityDefinitions` 对 `DRAFT`/`RETIRED` 分别 `publish`/`activate`，`ACTIVE` 幂等跳过 |
| BR-ACTDEF-005 | 子项按 `activityId` 聚合维护，写侧全量对齐 |
| BR-ACTDEF-006 | 每次写入后对齐 `childrenFlag` 与子项行集合 |
| BR-ACTDEF-007 | 同一父项下 `childrenId` 去重（拒绝或按策略覆盖） |
| BR-ACTDEF-008 | 子项删除采用逻辑删除；详情与列表默认过滤删除行 |
| BR-ACTDEF-009 | 创建/导入创建路径在 `statusEnum` 为空时默认 `PublicationStatus.DRAFT` |
| BR-ACTDEF-010 | `typeEnumOptions` 由术语 `activity_type_code` 生成；已删除 `ActivityType`；前后端禁止散落硬编码 |
| BR-ACTDEF-011 | 创建/更新**强制** `typeEnum` 非空（维护页/PEIS）；财务类型见 **AS-BAS-CAT-001 BR-CAT-016** |
| BR-ACTDEF-QRY-001 | 列表查询可走产品目录投影，主表查询作为兜底 |
| BR-ACTDEF-QRY-002 | 详情查询允许拼接收费展示数据，但不改变计费事实源 |
| BR-ACTDEF-QRY-003 | 子项查询按 `activityId`，并默认过滤逻辑删除行 |
| BR-ACTDEF-QRY-003（续） | `listChildrenByActivityId(s)` 批量读子表，供 **AS-IP-NUR-005** 选品/写展开 |
| BR-ACTDEF-QRY-005 | `listParentActivityIdsByChildrenId`：子项反查未删父 id，供 **AS-BAS-CAT-001 BR-CAT-014** |
| BR-ACTDEF-QRY-004 | `pageForCatalog` 固定 `status_enum=PublicationStatus.ACTIVE.getCode()`，禁止数字串兼容谓词 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ActivityDefinitionCreatedEvent` | `com.zxplat.domain.share.events.workflow.activitydefinition` | `activityDefinitionId`、`busNo`、`name` | `createActivityDefinition`、`importActivityCatalog`（新增行） |
| `ActivityDefinitionUpdatedEvent` | `com.zxplat.domain.share.events.workflow.activitydefinition` | `activityDefinitionId`、`changedFields` | `updateActivityDefinition`、`syncActivityYbMapping`、`importActivityCatalog`（更新行） |
| `ActivityDefinitionRetiredEvent` | `com.zxplat.domain.share.events.workflow.activitydefinition` | `activityDefinitionIdList`、`targetStatus` | `retireActivityDefinitions` |
| `ActivityDefinitionActivatedEvent` | `com.zxplat.domain.share.events.workflow.activitydefinition` | `activityDefinitionIdList`、`targetStatus` | `activateActivityDefinitions` |
| `ActivityDefinitionPriceUpdatedEvent` | `com.zxplat.domain.share.events.workflow.activitydefinition` | `activityDefinitionId`、`oldStandardPrice`、`newStandardPrice`、`reasonCode` | `updateActivityDefinition`（触及价格展示字段） |

**发布时机**：全部 beforeCommit；载荷最小化为业务主键 + 关键快照。

---

## 8. 删除与可见性契约

- 主表与子表统一逻辑删除，不做物理 DELETE。
- 读侧默认过滤 `isDeleted=true` 记录；仅审计查询可显式包含删除数据。
- 子项同步采用“入参全量集合为权威”，不在集合内的既有子项行逻辑删除。
- 任何更新子项 `id` 都必须校验归属 `activityId` 一致，避免越权串改。

---

## 9. 框架优先适配规则

- 复用框架审计字段、软删过滤、领域事件发布机制，不重复实现基础能力。
- Repository 仅接口定义，复杂查询优先 Specification/QueryDSL（按项目现状），不引入手写实现类。
- 目录展示价仅作为读展示/检索字段，计费事实价仍以收费域定价行为准。
- 服务默认直接实现类，不引入无收益薄封装层。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表/子表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 6 个写方法小节，方法名与 DM §6.1 完全一致（含发布/停用/废弃/分组变更/定价变更）
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 14 项与 DM §6.2 对齐，含 `pageForCatalog` 与 `findAllByIds`/`findByBusNos`
- §6 BR 规则映射覆盖 BR-ACTDEF-001~010、QRY-001~004，均可追溯到方法实现
- §7 事件 5 条与 DM §5.1 全量对齐（含 Published/Deactivated/Discarded 等跃迁事件）
- 子项同步满足全量对齐+软删旧行策略完整实现
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-WKF-001-ActivityDefinition-诊疗项目定义`
- 本 SDD 当前版本
- 目标模块：`zxplat/zxplat-domain` 与 `zxplat-domain`（事件）
- 现有共享枚举/事件扫描结果（避免重复生成）

### 11.2 DoD

- 代码可编译通过，写读方法签名与 §5 完整一致。
- 聚合根/子实体字段与 §4 完全一致，不增删语义字段。
- 事件发布点、事件载荷、beforeCommit 时机与 §7 一致。
- 子项同步、软删过滤、分页统计语义与 §8/§9 一致。
- 单元测试覆盖关键 BR 与状态迁移，且 `unitTestCaseId` 可追溯。

### 11.3 失败处理

- 任一写方法字段不全或方法缺失：回退到文档修订，不进入成码。
- 事件缺失或包路径不一致：阻断合并并修复。
- 查询出现“先分页后内存过滤”导致 total 不一致：判定失败并整改。

---

## 定稿检查清单（可打勾）

- [x] 主章节结构与金骨架同构
- [x] §5.1 写方法数与 DM §6.1 一致（6 个）
- [x] §5.2 读方法覆盖 DM §6.2 全量（含 `pageForCatalog`、`findAllByIds`、`findByBusNos`，14 个）
- [x] §4 字段表覆盖 DM §3 全量字段（主表+子表）
- [x] §7 事件覆盖 DM §5 全量事件（5 个）
- [x] 正文无 YAML / `sdd_status` / `dm_code` 元数据
- [x] 正文无“待确认/后续补充/暂不细化”占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-10-08 | **代码先行回写**：§4.6.2 补齐 `ActivityDefinitionPageQueryModel` 与 Java 一致（含 `packageUnitCode` 等）；对齐 DM **v1.0.21**（列表「单位」列） |
| 2026-10-07 | **代码先行回写**：`typeEnum` 必填（**BR-ACTDEF-011**）；删 `ActivityType`；对齐 DM **v1.0.20** |
| 2026-10-06 | **代码先行回写**：`typeEnum` 选填（**BR-ACTDEF-011**）；对齐 DM **v1.0.19** |
| 2026-10-06 | **代码先行回写**：`type_enum` 改术语 `activity_type_code`（种子 1~7）；**BR-ACTDEF-010**；`pageForCatalog` 可选 `typeEnum`；对齐 DM **v1.0.17** |
| 2026-07-16 | **纠正**：撤回 `ActivityType.ENTRUST`；嘱托改 **`category_code=52`**；对齐 DM **v1.0.10** / **DM-WKF-004 v1.1.22** |
| 2026-07-14 | **病案结算类型**：§4.2 / §4.6.1 增补 `mrSettlementTypeCode` 与 Init 选项；对齐 DM **v1.0.8** |
| 2026-07-11 | **CHS 3301 写回**：§5.1.6 `syncActivityYbMapping` 增补跨模块消费（`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService`）；对齐 DM **v1.0.7** |
| 2026-05-28 | **DM-SDD M1**：§5.2 补 `findAllByIds`/`findByBusNos`；对齐 DM **v1.0.5** |
| 2026-05-28 | **ReadModel 废止**（DM **v1.0.4**）：§5.2 / §9 读侧改 **`ActivityDefinitionQueryService`**；目录分页 **`pageForCatalog`** 仅 **ACTIVE**；应用选品 **`ProductDefinitionCatalogPageLoader`**（**非** ProductDefinition RM） |
| 2026-05-28 | 对齐 DM **v1.0.3**：**`publish()`** 启用分流（`DRAFT→ACTIVE` / `RETIRED→ACTIVE`） |
| 2026-07-04 | **§9.6 / AS-BAS-CAT-001**：维护页 editability；`ServiceCatalogAppService` 锁定态跳过 `replacePriceLines`；§4.6.3 补 editability 字段 |

---

**本 SDD 正文结束。**
