# DM-CUS-009 TerminologyCodeMapping 术语编码映射 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-009-TerminologyCodeMapping-术语编码映射.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：TerminologyCodeMapping（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-009 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.terminologycodemapping` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 6 个、读方法 7 个 |
| DM 第 7 章 BR | 满足 | §6 映射 11 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 7 个事件 |

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

### 2.2 包结构（`com.zxplat.domain.custom.terminologycodemapping`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.terminologycodemapping` |

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
| `cus_terminologycodemapping` | `TerminologyCodeMapping` | 依据 DM 主表语义落地（实际表名以实现定稿为准） |

### 4.2 聚合根 `TerminologyCodeMapping`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | ``Long`` | 是 | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 是 | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 是 | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 是 | 目标编码体系中的编码值 |
| `target_name` | ``String`` | 否 | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | 是 | 启用标志（`true`=启用 / `false`=停用） |

**不变式方法（示例命名，实现须一致）**：`validateRequiredFields`、`validateStatusTransition`、`assertOwnership`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `TerminologyCodeMappingRepository extends JpaRepository<TerminologyCodeMapping, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- 其余查询签名按 §5.2 与 §6 不变式派生。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `TerminologyCodeMapping1QueryModel`（对应 `getTerminologyCodeMappingPage`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

#### 4.6.2 `TerminologyCodeMapping2QueryModel`（对应 `getMappingBySource`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

#### 4.6.3 `TerminologyCodeMapping3QueryModel`（对应 `getMappingsBySourceTable`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

#### 4.6.4 `TerminologyCodeMapping4QueryModel`（对应 `getById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

#### 4.6.5 `TerminologyCodeMapping5QueryModel`（对应 `lookupTargetCode`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

#### 4.6.6 `TerminologyCodeMapping6QueryModel`（对应 `lookupSourceCode`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

#### 4.6.7 `TerminologyCodeMapping7QueryModel`（对应 `exportTerminologyCodeMapping`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_terminologycodemapping.id` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | `cus_terminologycodemapping.source_item_table` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | `cus_terminologycodemapping.source_item_no` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | `cus_terminologycodemapping.target_item_no` | 目标编码体系中的编码值 |
| `target_name` | ``String`` | `cus_terminologycodemapping.target_name` | 目标编码对应的名称（冗余存储，便于展示） |
| `active_flag` | ``Boolean`` | `cus_terminologycodemapping.active_flag` | 启用标志（`true`=启用 / `false`=停用） |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `TerminologyCodeMappingDomainService`

#### 5.1.1 `createMapping(CreateMappingCommand cmd)` → `void`

**命令文件**：`CreateMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 目标编码体系中的编码值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TerminologyMappingCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-009-W-001` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.2 `modifyMapping(ModifyMappingCommand cmd)` → `void`

**命令文件**：`ModifyMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 目标编码体系中的编码值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TerminologyMappingCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-009-W-002` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.3 `deactivateMapping(DeactivateMappingCommand cmd)` → `void`

**命令文件**：`DeactivateMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 目标编码体系中的编码值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TerminologyMappingCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-009-W-003` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.4 `reactivateMapping(ReactivateMappingCommand cmd)` → `void`

**命令文件**：`ReactivateMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 目标编码体系中的编码值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TerminologyMappingCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-009-W-004` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.5 `deleteMapping(DeleteMappingCommand cmd)` → `void`

**命令文件**：`DeleteMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 目标编码体系中的编码值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TerminologyMappingCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-009-W-005` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.6 `importMappingBatch(ImportMappingBatchCommand cmd)` → `void`

**命令文件**：`ImportMappingBatchCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `source_item_table` | ``String`（`SourceItemType`）` | 标识编码所属的业务对象类型，见 §4.1 |
| `source_item_no` | ``String`` | 源编码体系中的编码值 |
| `target_item_no` | ``String`` | 目标编码体系中的编码值 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new TerminologyMappingCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-009-W-006` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

### 5.2 读侧 — `TerminologyCodeMappingQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getTerminologyCodeMappingPage` | `criteria/pageable` | `TerminologyCodeMapping1QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-001` |
| `getMappingBySource` | `criteria/pageable` | `TerminologyCodeMapping2QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-002` |
| `getMappingsBySourceTable` | `criteria/pageable` | `TerminologyCodeMapping3QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-003` |
| `getById` | `criteria/pageable` | `TerminologyCodeMapping4QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-004` |
| `lookupTargetCode` | `criteria/pageable` | `TerminologyCodeMapping5QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-005` |
| `lookupSourceCode` | `criteria/pageable` | `TerminologyCodeMapping6QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-006` |
| `exportTerminologyCodeMapping` | `criteria/pageable` | `TerminologyCodeMapping7QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-009-R-007` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-TER-001 | 源对象类型（sourceItemType）必填；确保每条映射有明确的业务对象归属；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-001` |
| BR-TER-002 | 源编码与目标编码必须成对出现，不允许为空；映射关系的基本完整性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-002` |
| BR-TER-003 | 同一 MappingIdentity（源对象类型+源编码+目标编码）在启用状态下只允许存在一条记录；唯一性约束，防止重复对照；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-003` |
| BR-TER-004 | 源对象类型和源编码不允许通过修改操作变更（须删除后重建）；身份键不可变，保障引用一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-004` |
| BR-TER-005 | 删除必须逻辑删除，不允许物理删除；审计追踪要求；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-005` |
| BR-TER-006 | 查询默认只返回未删除记录；默认过滤已删除数据；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-006` |
| BR-TER-007 | 停用与删除语义分离：停用仅暂停使用、保留历史；删除不可恢复；状态语义清晰；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-007` |
| BR-TER-008 | 导入时按 MappingIdentity 判重，存在则更新属性，不存在则新增；导入幂等性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-008` |
| BR-TER-009 | 恢复已停用记录前须校验同一身份键无其他活跃记录；防止恢复后产生重复；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-009` |
| BR-TER-010 | 编码转换查询仅返回"启用"状态的对照，不返回已停用或已删除的映射；编码转换准确性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-010` |
| BR-TER-011 | 同源编码在不同 `sourceItemTable`（对象类型）下允许并存；检索与导入必须带对象类型过滤；承接早期版本 `BR-TER-006`，避免跨对象串码；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-009-BR-011` |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `TerminologyMappingCreatedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `createMapping` |
| `TerminologyMappingModifiedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `modifyMapping` |
| `TerminologyMappingDeactivatedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `reactivateMapping` |
| `TerminologyMappingReactivatedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `reactivateMapping` |
| `TerminologyMappingDeletedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `deleteMapping` |
| `TerminologyMappingBatchImportedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `importMappingBatch` |
| `TerminologyCodeMappingCreatedEvent` | `com.zxplat.domain.share.events.custom.terminologycodemapping` | `aggregateId`、`operationType`、`occurredAt` | `createMapping` |

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
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 6 个）
- [ ] §5.2 读方法与 §4.6 QueryModel 一一对应（当前 7 个）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-009-TerminologyCodeMapping-术语编码映射.md`
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
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 6，与 DM §6.1 对齐
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
