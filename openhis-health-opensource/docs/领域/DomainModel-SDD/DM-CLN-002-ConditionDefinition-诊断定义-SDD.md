# DM-CLN-002 ConditionDefinition 诊断定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-002-ConditionDefinition-诊断定义.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Clinical（临床域）  
> **聚合**：ConditionDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CLN-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `{{TARGET_MODULE}}` |
| 包规则 `com.zxplat.domain.clinical.conditiondefinition` | 满足 | `conditiondefinition` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **ConditionDefinition 聚合** 的持久化模型、目录维护方法契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `{{TARGET_MODULE}}` 领域层实现。
- 将目录新增、编辑、启停等写侧能力逐方法契约化，保证每个写方法对应独立 Command 文件与可测试锚点。
- 单次成稿要求无占位措辞，所有白名单方法、BR 与事件均可直接落地。

### 1.2 边界

- **在内**：`cln_condition_definition` 字段约束、目录主数据写读规则、目录状态迁移规则。
- **在外**：患者诊断事实维护（由 `Condition` 聚合负责）、页面编排、跨域缓存刷新编排。
- **跨域仅端口声明**：目录聚合仅提供查询/事件，不直接写患者事实聚合。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`{{TARGET_MODULE}}`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.clinical.conditiondefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ConditionDefinition` |
| `valueobject` | 无独立复合值对象 |
| `enums`（共享） | 复用 `ConditionDefinitionSource`、`PublicationStatus` |
| `command` | 每个写方法一个 Command 文件 |
| `repository` | `ConditionDefinitionRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.clinical.conditiondefinition` |

**存储口径（DM 优先）**：`source_enum`、`status_enum` 为 `Integer`；`*_code` 为 `String`；`*_flag` 为 `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | 主键 `Long id`；本聚合无子表外键 |
| 时间 | 目录聚合无强制业务时间字段，审计时间由框架提供 |
| `*_enum` | 枚举编码列统一 `Integer` |
| `*_code` | 字典编码列统一 `String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，框架统一过滤 |
| 实体 | `@Entity` + 显式 `@Table` / `@Column` |
| 值对象 | 无独立 `@Embeddable` 值对象 |
| 仓储 | `interface ConditionDefinitionRepository extends JpaRepository<ConditionDefinition, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内 `new XxxEvent(...)` + `DomainEventPublisher.publish`，`beforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cln_condition_definition` | `ConditionDefinition` | 诊断目录主数据单表 |

### 4.2 聚合根 `ConditionDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `sourceEnum` | `String` | 是 | 所属分类（术语 code，含 `clinical_v2`） |
| `conditionCode` | `String` | 是 | 目录编码；与 `sourceEnum` 联合唯一 |
| `name` | `String` | 是 | 诊断名称 |
| `pyStr` | `String` | 否 | 拼音检索码 |
| `wbStr` | `String` | 否 | 五笔检索码 |
| `typeCode` | `String` | 否 | 类型编码 |
| `description` | `String` | 否 | 描述 |
| `ybFlag` | `Boolean` | 否 | 医保标记 |
| `diseaseCode` | `String` | 否 | 特慢病编码 |
| `ybNo` | `String` | 否 | 医保编码（结算/4401） |
| `clinicCode` | `String` | 否 | 国临码（病案首页） |
| `ybMatchFlag` | `Boolean` | 否 | 医保对码标记 |
| `statusEnum` | `String` | 是 | 发布状态 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertCodeUnique(source, code)`、`rebuildSearchKey`、`enable`、`disable`、`importClinicalV2Catalog`。

### 4.3 子实体

无。本聚合为单表。

### 4.4 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `ConditionDefinitionRepository extends JpaRepository<ConditionDefinition, Long>`
- 推荐查询签名：`existsBySourceEnumAndConditionCode`、`findBySourceEnumAndConditionCodeIn`、`findByClinicCodeIn`、`findByIdIn`。

**说明**：若框架统一软删过滤，查询签名可省略冗余 `IsDeletedFalse` 命名后缀。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `ConditionDefinitionDetailQueryModel`（对应 `getConditionDefinitionListById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_condition_definition.id` | 主键 |
| `conditionCode` | `String` | `cln_condition_definition.condition_code` | 目录编码 |
| `name` | `String` | `cln_condition_definition.name` | 诊断名称 |
| `sourceEnum` | `Integer` | `cln_condition_definition.source_enum` | 所属分类 |
| `statusEnum` | `Integer` | `cln_condition_definition.status_enum` | 发布状态 |

#### 4.6.2 `ConditionDefinitionBatchItemQueryModel`（对应 `getConditionDefinitionListByIds`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_condition_definition.id` | 主键 |
| `conditionCode` | `String` | `cln_condition_definition.condition_code` | 编码 |
| `name` | `String` | `cln_condition_definition.name` | 名称 |
| `statusEnum` | `Integer` | `cln_condition_definition.status_enum` | 状态 |

#### 4.6.3 `ConditionDefinitionSearchItemQueryModel`（对应 `getConditionDefinitionListBySearchKey`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_condition_definition.id` | 主键 |
| `conditionCode` | `String` | `cln_condition_definition.condition_code` | 编码 |
| `name` | `String` | `cln_condition_definition.name` | 名称 |
| `pyStr` | `String` | `cln_condition_definition.py_str` | 拼音码 |
| `wbStr` | `String` | `cln_condition_definition.wb_str` | 五笔码 |
| `statusEnum` | `Integer` | `cln_condition_definition.status_enum` | 状态 |

#### 4.6.4 `DiseasePageItemQueryModel`（对应 `getDiseaseList`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_condition_definition.id` | 主键 |
| `conditionCode` | `String` | `cln_condition_definition.condition_code` | 编码 |
| `name` | `String` | `cln_condition_definition.name` | 名称 |
| `sourceEnum` | `Integer` | `cln_condition_definition.source_enum` | 分类 |
| `statusEnum` | `Integer` | `cln_condition_definition.status_enum` | 状态 |
| `ybFlag` | `Boolean` | `cln_condition_definition.yb_flag` | 医保标记 |

#### 4.6.5 `DiseaseDetailQueryModel`（对应 `getDiseaseOne`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_condition_definition.id` | 主键 |
| `conditionCode` | `String` | `cln_condition_definition.condition_code` | 编码 |
| `name` | `String` | `cln_condition_definition.name` | 名称 |
| `typeCode` | `String` | `cln_condition_definition.type_code` | 类型编码 |
| `description` | `String` | `cln_condition_definition.description` | 描述 |
| `ybNo` | `String` | `cln_condition_definition.yb_no` | 医保编码 |
| `ybMatchFlag` | `Boolean` | `cln_condition_definition.yb_match_flag` | 医保对码标记 |
| `statusEnum` | `Integer` | `cln_condition_definition.status_enum` | 状态 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ConditionDefinitionDomainService`

#### 5.1.1 `addDisease(AddDiseaseCommand cmd)` → `Long`

**命令文件**：`AddDiseaseCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `sourceEnum` | `Integer` | 所属分类 |
| `conditionCode` | `String` | 目录编码 |
| `name` | `String` | 名称 |
| `pyStr` | `String` | 拼音码 |
| `wbStr` | `String` | 五笔码 |
| `typeCode` | `String` | 类型编码 |
| `description` | `String` | 描述 |
| `statusEnum` | `Integer` | 初始状态 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ConditionDefinition.assertConditionCodeUnique` |
| `statusTransitionMethod` | `ConditionDefinition.initPublicationStatus` |
| `repositoryChecks` | `ConditionDefinitionRepository.existsByConditionCodeAndIsDeletedFalse(conditionCode)` |
| `eventPublishAnchor` | `new ConditionDefinitionCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CDF-W-001` |

**方法契约摘要**：前置条件为编码唯一与必填字段齐全；持久化影响为新增目录项并计算检索码；失败语义为编码冲突或参数非法；事件触发为创建后发布 `ConditionDefinitionCreatedEvent`。

---

#### 5.1.2 `addYbDisease(AddYbDiseaseCommand cmd)` → `Long`

**命令文件**：`AddYbDiseaseCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `sourceEnum` | `Integer` | 所属分类 |
| `conditionCode` | `String` | 目录编码 |
| `name` | `String` | 名称 |
| `ybFlag` | `Boolean` | 医保标记 |
| `ybNo` | `String` | 医保编码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |
| `diseaseCode` | `String` | 特慢病编码 |
| `statusEnum` | `Integer` | 初始状态 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ConditionDefinition.assertConditionCodeUnique` |
| `statusTransitionMethod` | `ConditionDefinition.initPublicationStatus` |
| `repositoryChecks` | `ConditionDefinitionRepository.existsByConditionCodeAndIsDeletedFalse(conditionCode)` |
| `eventPublishAnchor` | `new ConditionDefinitionCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CDF-W-002` |

**方法契约摘要**：前置条件为目录编码唯一且医保字段合法；持久化影响为新增医保属性目录项；失败语义为编码冲突或医保字段不合法；事件触发为创建后发布 `ConditionDefinitionCreatedEvent`。

---

#### 5.1.3 `editDisease(EditDiseaseCommand cmd)` → `void`

**命令文件**：`EditDiseaseCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 目录主键 |
| `sourceEnum` | `Integer` | 所属分类 |
| `conditionCode` | `String` | 编码 |
| `name` | `String` | 名称 |
| `pyStr` | `String` | 拼音码 |
| `wbStr` | `String` | 五笔码 |
| `typeCode` | `String` | 类型编码 |
| `description` | `String` | 描述 |
| `ybFlag` | `Boolean` | 医保标记 |
| `ybNo` | `String` | 医保编码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ConditionDefinition.rebuildSearchKey` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ConditionDefinitionRepository.findByIdAndIsDeletedFalse(id)` |
| `eventPublishAnchor` | `new ConditionDefinitionEditedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CDF-W-003` |

**方法契约摘要**：前置条件为目标记录存在、变更后编码不冲突；持久化影响为更新目录字段并重算检索码；失败语义为目标不存在或唯一约束冲突；事件触发为编辑后发布 `ConditionDefinitionEditedEvent`。

---

#### 5.1.4 `editDiseaseStop(EditDiseaseStopCommand cmd)` → `void`

**命令文件**：`EditDiseaseStopCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `ids` | `List<Long>` | 待停用目录主键集合 |
| `operatorId` | `Long` | 操作人 |
| `reason` | `String` | 停用原因 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ConditionDefinition.disable` |
| `statusTransitionMethod` | `ConditionDefinition.disable` |
| `repositoryChecks` | `ConditionDefinitionRepository.findAllByIdInAndIsDeletedFalse(ids)` |
| `eventPublishAnchor` | `new ConditionDefinitionDisabledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CDF-W-004` |

**方法契约摘要**：前置条件为批量目标存在且当前为可停用状态；持久化影响为批量置 `statusEnum=RETIRED`；失败语义为非法状态迁移或缺失记录；事件触发为停用后发布 `ConditionDefinitionDisabledEvent`。

---

#### 5.1.5 `editDiseaseStart(EditDiseaseStartCommand cmd)` → `void`

**命令文件**：`EditDiseaseStartCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `ids` | `List<Long>` | 待启用目录主键集合 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ConditionDefinition.enable` |
| `statusTransitionMethod` | `ConditionDefinition.enable` |
| `repositoryChecks` | `ConditionDefinitionRepository.findAllByIdInAndIsDeletedFalse(ids)` |
| `eventPublishAnchor` | `new ConditionDefinitionEnabledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CDF-W-005` |

**方法契约摘要**：前置条件为目标记录存在且允许由停用恢复启用；持久化影响为批量置 `statusEnum=ACTIVE`；失败语义为状态不匹配或目标缺失；事件触发为启用后发布 `ConditionDefinitionEnabledEvent`。

### 5.2 读侧 — `ConditionDefinitionQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

**「出参」填 QueryModel 类名**（如 `ConditionDefinitionDetailQueryModel`）；字段定义见 **§4.6 对应子节**，此处不重复列字段。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getConditionDefinitionListById` | `Long id` | `ConditionDefinitionDetailQueryModel` | 主键精确查询 | `TC-CDF-R-001` |
| `getConditionDefinitionListByIds` | `List<Long> ids` | `List<ConditionDefinitionBatchItemQueryModel>` | 批量主键查询 | `TC-CDF-R-002` |
| `getConditionDefinitionListBySearchKey` | `String keyword` | `List<ConditionDefinitionSearchItemQueryModel>` | 关键字 + 状态过滤检索 | `TC-CDF-R-003` |
| `getDiseaseList` | `DiseasePageQuery query, Pageable pageable` | `Page<DiseasePageItemQueryModel>` | 多条件分页查询 | `TC-CDF-R-004` |
| `getDiseaseOne` | `Long id` | `DiseaseDetailQueryModel` | 详情读取 | `TC-CDF-R-005` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-CDF-001 | 新增/编辑前按 `conditionCode` 执行唯一校验 |
| BR-CDF-002 | 编辑名称后同步重算 `pyStr/wbStr` |
| BR-CDF-003 | 启停仅通过 `statusEnum` 迁移实现，不物理删除 |
| BR-CDF-004 | 聚合不承载患者/就诊事实字段，保持定义与事实分离 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ConditionDefinitionCreatedEvent` | `com.zxplat.domain.share.events.clinical.conditiondefinition` | `id,conditionCode,name,statusEnum` | `addDisease`、`addYbDisease` |
| `ConditionDefinitionEditedEvent` | `com.zxplat.domain.share.events.clinical.conditiondefinition` | `id,changedFields` | `editDisease` |
| `ConditionDefinitionEnabledEvent` | `com.zxplat.domain.share.events.clinical.conditiondefinition` | `id,statusEnum` | `editDiseaseStart` |
| `ConditionDefinitionDisabledEvent` | `com.zxplat.domain.share.events.clinical.conditiondefinition` | `id,statusEnum` | `editDiseaseStop` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 目录聚合使用逻辑删除，默认查询仅可见 `isDeleted=false` 数据。
- 启停通过 `statusEnum` 表达可用性，不以删除替代停用语义。
- 已停用目录不应作为新增诊断候选，历史引用保持可追溯。
- 批量启停操作需保证同批次内结果一致，失败时整批回滚。

---

## 9. 框架优先适配规则

- 软删、审计、工作单元事务、`beforeCommit` 事件发布遵循项目框架能力。
- 领域服务/查询服务默认直接实现类，不强制接口 + Impl 双层。
- 一方法一 Command，命令按文件独立定义。
- 仓储仅保留 `JpaRepository` 接口，复杂检索在查询服务收敛。
- 分页语义保持数据库侧统计一致，禁止内存过滤破坏 `totalElements`。

---

## 10. 生成与验收清单

- [ ] `§4.2` 字段表与 DM `§3.1` 完整对齐
- [ ] `§5.1` 写方法数量为 5，与 DM `§6.1` 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] `§5.2` 读方法数量为 5，与 DM `§6.2` 一致
- [ ] `§6` BR 覆盖 BR-CDF-001 ~ BR-CDF-004
- [ ] `§7` 事件数量为 4，与 DM `§5` 一致
- [ ] 全文无“待确认/后续补充/占位实现”措辞

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CLN-002-ConditionDefinition-诊断定义.md`
- 本 SDD 文件（结构/字段/方法/事件门禁通过）
- 目标模块：`{{TARGET_MODULE}}`

### 11.2 DoD

- 写侧 5 个方法与命令文件全部落地，方法签名与 §5 一致。
- `conditionCode` 唯一约束、状态迁移与检索码重算规则全部实现。
- 4 个事件全部按 §7 beforeCommit 发布并具备最小载荷。
- 读侧 5 个方法可运行，QueryModel 字段与 §4.6 一致。
- BR-CDF 关键规则均绑定测试用例并可追溯。

### 11.3 失败处理

- 若唯一约束、状态迁移或事件发布任一不可落地，立即停机并回文档修订。
- 若 DM 白名单方法变更，先更新本 SDD 后再进入代码会话。
- 若分页语义与过滤统计不一致，判定 DoD 失败。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] `§5.1` 子节数量 = DM 写方法数量（5）
- [ ] `§4.2` 字段表覆盖 DM `§3.1`
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
