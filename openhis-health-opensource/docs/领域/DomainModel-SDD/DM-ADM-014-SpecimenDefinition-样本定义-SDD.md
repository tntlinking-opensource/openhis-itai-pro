# DM-ADM-014 SpecimenDefinition 样本定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-014-SpecimenDefinition-样本定义.md`（v1.2，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 检验标本目录主数据）  
> **聚合**：SpecimenDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-014 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.specimendefinition` | 满足 | `specimendefinition` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD / 成码采纳 |
| -- | ----------- | -------------- |
| 审计基类名 | §3.1 历史写 `HisBaseEntity` | 成码落 `BaseAuditedEntity`（与 `zxplat` 框架基类一致） |
| 检索码生成 | §6.1 仅写「按名称生成」 | 实现统一 `SearchCodeUtil.toPyStr` / `toWbStr`；命令未传 `pyStr`/`wbStr` 时由名称回填；`update` 在 `name` 变更时先清空再重算 |
| 下游引用删除校验 | BR-SPDEF-004 预留 | 领域 `deleteSpecimenDefinition` 仅逻辑删除；**当前** `SpecimenDefinitionAppService.deleteSpecimenDefinition` **不**校验 `ActivityDefinition` 引用，待绑定能力接入后再编排 |
| 标本种类字段名 | 初稿 `type_code` | 采纳 DM v1.2：`type_enum`（Java `typeEnum`），存 `SpecimenType.getCode()`；不建术语码表 |
| 批量写命令形态 | §5.1.3～5.1.5 初稿写 `id`/`ids` 平铺 | 成码对齐项目惯例：删除/启停命令继承 `BatchCommand` + 明细项 `id`；删除命令含 `operatorId`，实体 `markDeleted(operatorId)` 写 `deletedBy`/`deletedAt` |
| `bus_no` 唯一约束 | §2.1 写唯一索引 | Flyway 落部分唯一索引 `uk_adm_specimen_definition_bus_no WHERE is_deleted = false` |
| 读侧详情不存在 | §6.2.2 写「空或业务提示」 | 成码锁定：`getSpecimenDefinition` 返回 `null`；应用层 `get` 转 `EntityNotFoundException` |
| 应用层单主键入参 | — | `delete` / `enable` / `disable` 等方法签名为 `Long id`，**禁止** `IdValueRequest`；前端经 `preserveApiIdForRequest` 以 **string** 提交 |
| 应用层出参主键 | — | `SpecimenDefinitionDto.id`、`UpdateSpecimenDefinitionRequest.id` 使用 `ToStringSerializer` |
| 删除操作人 | — | 应用层 `CurrentOperatorResolver.requireOperatorId` 写入删除命令 `operatorId`（B.6.5） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **SpecimenDefinition 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出；单表聚合无子表同步。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`adm_specimen_definition` 主数据新增、编辑、逻辑删除、批量启停、检索码维护、发布状态迁移。
- **在外**：诊疗项目目录绑定编排（`ActivityDefinition.specimen_code` 写入，**当前未接入**）、采血条码打印数据集、应用层 `SpecimenDefinitionAppService`（`health-application` / `basic.specimendefinition`）。
- **跨域仅端口声明**：BR-SPDEF-004 下游引用阻断为预留能力；本聚合与当前应用层删除均不查询 `wkf_activity_definition`。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（实体/仓储/服务）；`zxplat-domain`（`SpecimenType` / `PublicationStatus` 共享枚举、领域事件、`AggregateTypeConstants` / `ErrorCodeConstants` 增补项）。

### 2.2 包结构（`com.zxplat.domain.administration.specimendefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `SpecimenDefinition` |
| `valueobject` | 无独立复合值对象 |
| `enums`（共享） | 复用 `PublicationStatus`；本聚合 `type_enum` 使用 `SpecimenType`（`com.zxplat.domain.share.enums`，与 DM §4.1 对齐） |
| `command` | 每个写方法一个 Java 文件，见 §5 |
| `repository` | `SpecimenDefinitionRepository`（仅 `JpaRepository` 接口） |
| `service` | `SpecimenDefinitionDomainService`、`SpecimenDefinitionQueryService`（直接实现类） |
| `queries` | `PageSpecimenDefinitionQuery`、`SearchSpecimenDefinitionQuery` 等入参 |
| `queries.model` | 读侧 QueryModel，见 §4.6 |
| `share.events` | `com.zxplat.domain.share.events.administration.specimendefinition` |

**存储口径（DM 优先）**：`status_enum`、`type_enum` 均为 **`String`（存枚举 `code`）**；检索字段 `py_str`/`wb_str`、`bus_no` 为 `String`；软删统一 `Boolean isDeleted`（基类）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；本聚合无子表外键 |
| 时间 | 本聚合无业务时间列；审计时间字段由 `BaseAuditedEntity` 提供 `LocalDateTime` |
| `*_enum` | **枚举编码列**：`String`；`statusEnum` 存 `PublicationStatus.getCode()`，`typeEnum` 存 `SpecimenType.getCode()` |
| `*_code` | 本聚合无字典 `*_code` 列 |
| `_flag` | 本聚合无 |
| 软删 | `Boolean isDeleted`，框架默认过滤 |
| 实体 | `@Entity` + `@Table(name = "adm_specimen_definition")` + 显式 `@Column` |
| 值对象 | 无 `@Embeddable` |
| 仓储 | `interface SpecimenDefinitionRepository extends JpaRepository<SpecimenDefinition, Long>` |
| 分页 | `Pageable`；未指定排序时默认 `updatedAt DESC, id DESC`（对齐 DM §6.2.1） |
| 事务 | 领域服务方法 **不** 机械添加 `@Transactional` |
| 事件 | 服务内 `new XxxEvent(...)` + `IDomainEventPublisher.publish`，**beforeCommit**；禁止 `*EventFactory` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_specimen_definition` | `SpecimenDefinition` | 样本定义聚合根单表 |

### 4.2 聚合根 `SpecimenDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 样本定义标识，雪花 ID |
| `busNo` | `String` | 是 | 院内唯一业务编码；下游 `ActivityDefinition.specimen_code` 引用本字段（BR-SPDEF-001） |
| `name` | `String` | 是 | 样本名称，非空 |
| `pyStr` | `String` | 否 | 拼音检索码；名称变更时重算（BR-SPDEF-002） |
| `wbStr` | `String` | 否 | 五笔检索码；名称变更时重算（BR-SPDEF-002） |
| `typeEnum` | `String` | 是 | 标本种类，存 `SpecimenType.getCode()`（DM §4.1） |
| `statusEnum` | `String` | 是 | 发布状态，存 `PublicationStatus.getCode()`；主路径 `ACTIVE` / `INACTIVE` |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：
- `assertBusNoNotBlank()`：`busNo` 非空
- `assertNameNotBlank()`：`name` 非空
- `assertTypeEnumValid()`：`typeEnum` 须命中 `SpecimenType` 枚举
- `refreshSearchCodeFromName()`：按 `name` 重算 `pyStr`/`wbStr`（`SearchCodeUtil`）
- `initAsActive()`：新建默认 `statusEnum = PublicationStatus.ACTIVE.getCode()`
- `assertActive()` / `assertInactive()`：启停前置状态断言
- `activate()` / `deactivate()`：`INACTIVE ↔ ACTIVE` 状态迁移
- `markDeleted(Long operatorId)`：逻辑删除并写入 `deletedBy` / `deletedAt`

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `SpecimenDefinitionRepository extends JpaRepository<SpecimenDefinition, Long>, JpaSpecificationExecutor<SpecimenDefinition>`
- 推荐查询签名：
  - `boolean existsByBusNo(String busNo)`
  - `boolean existsByBusNoAndIdNot(String busNo, Long id)`
  - `Optional<SpecimenDefinition> findByBusNo(String busNo)`
  - `List<SpecimenDefinition> findByIdIn(Collection<Long> ids)`

**说明**：软删由框架统一过滤时，**禁止**在接口名强制冗余 `AndIsDeletedFalse`，除非基类未统一过滤。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `SpecimenDefinitionPageItemQueryModel`（对应 `pageSpecimenDefinition`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_specimen_definition.id` | 主键 |
| `busNo` | `String` | `adm_specimen_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_specimen_definition.name` | 样本名称 |
| `typeEnum` | `String` | `adm_specimen_definition.type_enum` | 标本种类（`SpecimenType`） |
| `statusEnum` | `String` | `adm_specimen_definition.status_enum` | 发布状态 |
| `pyStr` | `String` | `adm_specimen_definition.py_str` | 拼音码 |
| `wbStr` | `String` | `adm_specimen_definition.wb_str` | 五笔码 |
| `updatedAt` | `LocalDateTime` | `adm_specimen_definition.updated_at` | 最近更新时间 |

#### 4.6.2 `SpecimenDefinitionDetailQueryModel`（对应 `getSpecimenDefinition`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_specimen_definition.id` | 主键 |
| `busNo` | `String` | `adm_specimen_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_specimen_definition.name` | 样本名称 |
| `typeEnum` | `String` | `adm_specimen_definition.type_enum` | 标本种类 |
| `statusEnum` | `String` | `adm_specimen_definition.status_enum` | 发布状态 |
| `pyStr` | `String` | `adm_specimen_definition.py_str` | 拼音码 |
| `wbStr` | `String` | `adm_specimen_definition.wb_str` | 五笔码 |

#### 4.6.3 `SpecimenDefinitionSearchItemQueryModel`（对应 `searchByKeyWord`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_specimen_definition.id` | 主键 |
| `busNo` | `String` | `adm_specimen_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_specimen_definition.name` | 样本名称 |
| `typeEnum` | `String` | `adm_specimen_definition.type_enum` | 标本种类 |
| `statusEnum` | `String` | `adm_specimen_definition.status_enum` | 发布状态 |
| `pyStr` | `String` | `adm_specimen_definition.py_str` | 拼音码 |
| `wbStr` | `String` | `adm_specimen_definition.wb_str` | 五笔码 |

#### 4.6.4 `SpecimenDefinitionByBusNoQueryModel`（对应 `getByBusNo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_specimen_definition.id` | 主键 |
| `busNo` | `String` | `adm_specimen_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_specimen_definition.name` | 样本名称 |
| `typeEnum` | `String` | `adm_specimen_definition.type_enum` | 标本种类 |
| `statusEnum` | `String` | `adm_specimen_definition.status_enum` | 发布状态 |

#### 4.6.5 `SpecimenDefinitionBatchItemQueryModel`（对应 `listByIds`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_specimen_definition.id` | 主键 |
| `busNo` | `String` | `adm_specimen_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_specimen_definition.name` | 样本名称 |
| `typeEnum` | `String` | `adm_specimen_definition.type_enum` | 标本种类 |
| `statusEnum` | `String` | `adm_specimen_definition.status_enum` | 发布状态 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `SpecimenDefinitionDomainService`

#### 5.1.1 `createSpecimenDefinition(CreateSpecimenDefinitionCommand cmd)` → `Long`

**命令文件**：`CreateSpecimenDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `busNo` | `String` | 业务编码，全局唯一 |
| `name` | `String` | 样本名称 |
| `typeEnum` | `String` | 标本种类，须为 `SpecimenType` 合法 code |
| `pyStr` | `String` | 拼音码（可空，空则按 `name` 自动生成） |
| `wbStr` | `String` | 五笔码（可空，空则按 `name` 自动生成） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoNotBlank`、`assertNameNotBlank`、`assertTypeEnumValid`、`refreshSearchCodeFromName`、`initAsActive` |
| `statusTransitionMethod` | `initAsActive` |
| `repositoryChecks` | `SpecimenDefinitionRepository.existsByBusNo(busNo)` |
| `eventPublishAnchor` | `new SpecimenDefinitionCreatedEvent(...); domainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-SPDEF-W-001` |

**方法契约摘要**：前置条件为 `busNo` 唯一、`name`/`typeEnum` 非空且种类合法；持久化影响为插入一行并置 `statusEnum=ACTIVE`、写入检索码；失败语义为编码冲突或参数非法抛 `BusinessException`；事件触发为 `SpecimenDefinitionCreatedEvent`。**注释锚点**：校验 → 实体装配 → `initAsActive` → 检索码 → 持久化 → 事件。

---

#### 5.1.2 `updateSpecimenDefinition(UpdateSpecimenDefinitionCommand cmd)` → `void`

**命令文件**：`UpdateSpecimenDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 样本定义主键 |
| `busNo` | `String` | 业务编码 |
| `name` | `String` | 样本名称 |
| `typeEnum` | `String` | 标本种类 |
| `pyStr` | `String` | 拼音码（可空；`name` 变更且未传时重算） |
| `wbStr` | `String` | 五笔码（可空；`name` 变更且未传时重算） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoNotBlank`、`assertNameNotBlank`、`assertTypeEnumValid`、`refreshSearchCodeFromName` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `SpecimenDefinitionRepository.findById(id)`、`existsByBusNoAndIdNot(busNo, id)` |
| `eventPublishAnchor` | `new SpecimenDefinitionUpdatedEvent(...); domainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-SPDEF-W-002` |

**方法契约摘要**：前置条件为目标存在且未删、变更后 `busNo` 仍唯一；持久化影响为更新允许字段，`name` 变更时重算检索码并记入 `changedFields`；失败语义为记录不存在、编码冲突或静默新建（**禁止**，查无 `id` 须报错）；事件触发为 `SpecimenDefinitionUpdatedEvent`。**注释锚点**：加载 → 唯一校验 → 字段合并 → 检索码 → 持久化 → 事件。

---

#### 5.1.3 `deleteSpecimenDefinition(DeleteSpecimenDefinitionCommand cmd)` → `void`

**命令文件**：`DeleteSpecimenDefinitionCommand.java`

**字段全集**（继承 `BatchCommand<DeleteSpecimenDefinitionItemCommand>`）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `operatorId` | `Long` | 操作人主键，写入软删审计 |
| `items` | `List<DeleteSpecimenDefinitionItemCommand>` | 批量明细；每项含 `id` |

`DeleteSpecimenDefinitionItemCommand` 字段：`id`（`Long`，待删除样本定义主键）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted(operatorId)` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `SpecimenDefinitionRepository.findById(id)` |
| `eventPublishAnchor` | `new SpecimenDefinitionDeletedEvent(...); domainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-SPDEF-W-003` |

**方法契约摘要**：前置条件为记录存在且未逻辑删除、`operatorId` 非空（由应用层门控注入）；持久化影响为 `markDeleted(operatorId)` 写 `isDeleted`/`deletedBy`/`deletedAt`；失败语义为不存在或已删抛 `SPECIMEN_DEFINITION_002`；事件触发为 `SpecimenDefinitionDeletedEvent`。**注释锚点**：加载 → 软删标记 → 持久化 → 事件。

---

#### 5.1.4 `deactivateSpecimenDefinitions(DeactivateSpecimenDefinitionsCommand cmd)` → `void`

**命令文件**：`DeactivateSpecimenDefinitionsCommand.java`

**字段全集**（继承 `BatchCommand<DeactivateSpecimenDefinitionItemCommand>`）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `items` | `List<DeactivateSpecimenDefinitionItemCommand>` | 批量明细；每项含 `id`，非空 |

`DeactivateSpecimenDefinitionItemCommand` 字段：`id`（`Long`，待停用主键）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertActive`、`deactivate` |
| `statusTransitionMethod` | `deactivate` |
| `repositoryChecks` | `SpecimenDefinitionRepository.findByIdIn(ids)` |
| `eventPublishAnchor` | 逐条 `new SpecimenDefinitionDeactivatedEvent(...); domainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-SPDEF-W-004` |

**方法契约摘要**：前置条件为集合非空、每条记录存在且当前 `ACTIVE`；持久化影响为批量置 `statusEnum=INACTIVE`；失败语义为缺失记录或非法状态迁移时整批拒绝（与 `CommandBatchUtil` 批量语义一致）；事件触发为每条 `SpecimenDefinitionDeactivatedEvent`。**注释锚点**：批量加载 → 状态校验 → 迁移 → 批量保存 → 逐条事件。

---

#### 5.1.5 `activateSpecimenDefinitions(ActivateSpecimenDefinitionsCommand cmd)` → `void`

**命令文件**：`ActivateSpecimenDefinitionsCommand.java`

**字段全集**（继承 `BatchCommand<ActivateSpecimenDefinitionItemCommand>`）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `items` | `List<ActivateSpecimenDefinitionItemCommand>` | 批量明细；每项含 `id`，非空 |

`ActivateSpecimenDefinitionItemCommand` 字段：`id`（`Long`，待启用主键）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertInactive`、`activate` |
| `statusTransitionMethod` | `activate` |
| `repositoryChecks` | `SpecimenDefinitionRepository.findByIdIn(ids)` |
| `eventPublishAnchor` | 逐条 `new SpecimenDefinitionActivatedEvent(...); domainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-SPDEF-W-005` |

**方法契约摘要**：前置条件为集合非空、每条记录存在、未删且当前 `INACTIVE`；持久化影响为批量置 `statusEnum=ACTIVE`；失败语义为状态非法或记录缺失时拒绝；事件触发为每条 `SpecimenDefinitionActivatedEvent`。**注释锚点**：批量加载 → 状态校验 → 迁移 → 批量保存 → 逐条事件。

### 5.2 读侧 — `SpecimenDefinitionQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

**「出参」填 QueryModel 类名**；字段定义见 **§4.6 对应子节**，此处不重复列字段。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `pageSpecimenDefinition` | `PageSpecimenDefinitionQuery query, Pageable pageable` | `Page<SpecimenDefinitionPageItemQueryModel>` | JPA `Specification`：可选 `statusEnum`、`typeEnum`、关键字 OR 模糊（`name`/`bus_no`/`py_str`/`wb_str`，`SearchCodeUtil.toCaseInsensitiveLikePattern`）；排序 `updatedAt DESC, id DESC` | `TC-SPDEF-R-001` |
| `getSpecimenDefinition` | `Long id` | `SpecimenDefinitionDetailQueryModel` | 主键精确查询；不存在或已删返回 `null`（应用层转 `EntityNotFoundException`） | `TC-SPDEF-R-002` |
| `searchByKeyWord` | `SearchSpecimenDefinitionQuery query` | `List<SpecimenDefinitionSearchItemQueryModel>` | 默认 `statusEnum=ACTIVE`；关键字非空时 OR 模糊；上限条数由 `query.limit` 控制（默认 50） | `TC-SPDEF-R-003` |
| `getByBusNo` | `String busNo` | `SpecimenDefinitionByBusNoQueryModel` | `busNo` trim 后精确匹配；不存在返回 `null` | `TC-SPDEF-R-004` |
| `listByIds` | `List<Long> ids` | `List<SpecimenDefinitionBatchItemQueryModel>` | `id in (...)` 且未删；`ids` 空返回空列表；**不保证**与入参顺序一致 | `TC-SPDEF-R-005` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-SPDEF-001 | `createSpecimenDefinition` 调 `existsByBusNo`；`updateSpecimenDefinition` 调 `existsByBusNoAndIdNot`；冲突抛 `ErrorCodeConstants.SPECIMEN_DEFINITION_BUS_NO_CONFLICT`（`SPECIMEN_DEFINITION_004`） |
| BR-SPDEF-002 | `create`/`update` 在 `name` 变更或新建时调 `refreshSearchCodeFromName()`；`Update` 事件 `changedFields` 含 `pyStr`/`wbStr`（若变更） |
| BR-SPDEF-003 | 启停仅改 `statusEnum`，禁止用逻辑删除代替停用；`deactivate`/`activate` 不删行 |
| BR-SPDEF-004 | 领域删除不查下游；**当前** `SpecimenDefinitionAppService.deleteSpecimenDefinition(Long id)` 不校验 `ActivityDefinition` 引用；预留后续写前编排 |
| BR-SPDEF-005 | `searchByKeyWord` 默认仅 `ACTIVE`；维护页分页可通过入参显式查 `INACTIVE` |

### 6.1 错误码（`ErrorCodeConstants`，与成码一致）

| 常量 | code | 典型场景 |
| ---- | ---- | -------- |
| `SPECIMEN_DEFINITION_INVALID_ARGUMENT` | `SPECIMEN_DEFINITION_001` | 命令/参数为空、必填缺失、种类非法、删除无 `operatorId`（应用层门控） |
| `SPECIMEN_DEFINITION_NOT_FOUND` | `SPECIMEN_DEFINITION_002` | 主键不存在、已逻辑删除、批量写主键集合不完整 |
| `SPECIMEN_DEFINITION_INVALID_STATUS` | `SPECIMEN_DEFINITION_003` | 非 `ACTIVE` 记录停用、非 `INACTIVE` 记录启用 |
| `SPECIMEN_DEFINITION_BUS_NO_CONFLICT` | `SPECIMEN_DEFINITION_004` | `busNo` 唯一冲突（BR-SPDEF-001） |
| `SPECIMEN_DEFINITION_RULE_CONFLICT` | `SPECIMEN_DEFINITION_005` | 预留：下游引用/业务规则冲突（BR-SPDEF-004 接入后使用） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `SpecimenDefinitionCreatedEvent` | `com.zxplat.domain.share.events.administration.specimendefinition` | `id,busNo,name,typeEnum,statusEnum` | `createSpecimenDefinition` |
| `SpecimenDefinitionUpdatedEvent` | `com.zxplat.domain.share.events.administration.specimendefinition` | `id,changedFields` | `updateSpecimenDefinition` |
| `SpecimenDefinitionDeletedEvent` | `com.zxplat.domain.share.events.administration.specimendefinition` | `id` | `deleteSpecimenDefinition` |
| `SpecimenDefinitionActivatedEvent` | `com.zxplat.domain.share.events.administration.specimendefinition` | `id,statusEnum` | `activateSpecimenDefinitions`（逐条） |
| `SpecimenDefinitionDeactivatedEvent` | `com.zxplat.domain.share.events.administration.specimendefinition` | `id,statusEnum` | `deactivateSpecimenDefinitions`（逐条） |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。事件构造时 `aggregateType` 使用 `AggregateTypeConstants.SPECIMEN_DEFINITION`（成码会话须在 shared 常量类增补）。

---

## 8. 删除与可见性契约

- 删除语义为逻辑删除（`isDeleted=true`，并写 `deletedBy`/`deletedAt`），禁止物理 `DELETE`。
- 默认查询与关键字检索排除已删记录；`searchByKeyWord` 另默认仅 `ACTIVE`（BR-SPDEF-005）。
- `INACTIVE` 记录仍可读、可重新启用；不得作为新增绑定的默认候选。
- 停用不抹除历史引用；`ActivityDefinition` 已落库的 `specimen_code` 仍可解析名称（读侧不过滤 `INACTIVE`，由调用方决定）。

---

## 9. 框架优先适配规则

- 软删、审计、工作单元事务、`beforeCommit` 事件发布遵循项目级成码门禁。
- 领域服务与查询服务采用直接实现类，不默认接口 + Impl 双层。
- 一方法一 Command，命令按文件独立定义，禁止 `*Commands` 容器类。
- 仓储仅 `JpaRepository` 接口；复杂动态查询在 `SpecimenDefinitionQueryService` 用 `Specification` 收敛。
- 分页禁止先分页后内存过滤，保证 `totalElements` 与过滤口径一致。
- Flyway 建表与字段表同轮交付，遵循项目级迁移规范。

---

## 10. 生成与验收清单

- [x] `adm_specimen_definition` Flyway 脚本与 §4.2 字段一致（含 `bus_no` 唯一索引，`V76__adm_specimen_definition.sql`）
- [x] `SpecimenDefinition` 实体字段与 DM §3.1 / SDD §4.2 全量对齐
- [x] `SpecimenType` 枚举与 DM §4.1 code 表一致（`zxplat-domain`）
- [x] `AggregateTypeConstants.SPECIMEN_DEFINITION`（`SPD`）与 `ErrorCodeConstants.SPECIMEN_DEFINITION_001`～`005` 已落地
- [x] 应用层 `SpecimenDefinitionAppService` 已落地（`basic/specimendefinition`）；单主键接口为 `Long id`，删除经 `CurrentOperatorResolver`
- [x] §5.1 写方法 5 个、命令文件 5 个，均含绑定项与方法契约摘要
- [x] §5.2 读方法 5 个与 §4.6 QueryModel 一一对应；分页默认 `updatedAt DESC, id DESC`
- [x] §6 BR-SPDEF-001～005 均有实现落点
- [x] §7 事件 5 条均在 `beforeCommit` 发布
- [x] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-014-SpecimenDefinition-样本定义.md`
- 本 SDD 文件（结构/字段/方法/事件门禁通过）
- 目标模块：`zxplat/zxplat-domain`、`zxplat-domain`；应用层参考 `health-application` / `basic.specimendefinition`
- 共享事件包：`com.zxplat.domain.share.events.administration.specimendefinition`

### 11.2 DoD

- [x] 写侧 5 个方法与 5 个 Command 全部落地，签名与 §5 一致。
- [x] `busNo` 唯一、`statusEnum` 启停迁移、检索码重算与 §6 一致。
- [x] 5 个事件按 §7 在持久化成功后 beforeCommit 发布，最小载荷齐全。
- [x] 读侧 5 个方法可运行，QueryModel 字段与 §4.6 一致，分页/关键字过滤在 DB 侧完成。
- [x] BR-SPDEF 关键规则均绑定 `unitTestCaseId` 并可追溯。
- [x] 应用层 `SpecimenDefinitionAppService` 已落地；单主键 `Long id`、删除 `operatorId`、DTO 主键 `ToStringSerializer` 与 §「与 DM 显式差异」一致。

### 11.3 失败处理

- 任一写方法缺少字段全集或绑定项，停止成码并回补 SDD。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若 `SpecimenType` 与 `type_enum` 校验无法落地，判定 DoD 失败。

---

## 定稿检查清单（可打勾）

- [x] 主章节结构与金骨架一致（输入检查、1–11、定稿检查、评分、结束语）
- [x] §5.1 子节数量 = DM 写方法数量（5）
- [x] §4.2 字段表覆盖 DM §3.1 全部业务字段
- [x] §4.6 读方法子节数量 = 5，均含字段三列表
- [x] §7 事件表覆盖 DM §5 全部事件（5）
- [x] 每个写方法包含 `entityInvariantMethod` 与 `unitTestCaseId`
- [x] 全文无 YAML、无 `sdd_status`、无 `dm_code`、无占位措辞
- [x] 与 `zxplat` 领域层 + 应用层成码表述一致（v1.2 回刷）

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
| **总分** | **100** | **100（领域层 + 应用层已与 v1.2 代码对齐）** |

---

**本 SDD 正文结束。**
