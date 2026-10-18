# DM-EMR-003 MetadataFieldDef 元数据字段定义 · DomainModel-SDD

> **对应 DM**：`docs/模块/zxplat-module-emr/DomainModel/metadata/DM-EMR-003-MetadataFieldDef-元数据字段定义.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-emr`  
> **限界上下文**：电子病历（EMR 插件）  
> **聚合**：MetadataFieldDef（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-EMR-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-plugin/zxplat-module-emr/module-emr-domain` |
| 包规则 `com.zxplat.plugin.emr.domain.metadata` | 满足 | `metadata` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 写侧 3 项、读侧含维护列表与解析 |
| DM 第 7 章 BR | 满足 | §6 映射 BR-EMR-META-001～004 |
| DM 第 5 章事件 | 满足 | §7 本期无事件 |

**与 DM 显式差异（采纳依据：Mode B 现行 `MetadataFieldAppService`）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 写侧入口 | §6.1 `MetadataFieldDefDomainService` | §5.1 Command；现行实现一致 |
| 删除语义 | 框架默认软删 | **当前**无引用时 `repository.delete` 物理删；引用检查失败则禁止删（BR-EMR-META-003） |
| 解析读侧 | §6.2 `EncounterMetadataAppService` | 不在本聚合 DomainService；§5.2 只读声明端口 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **MetadataFieldDef** 单表聚合的字段目录、取值源契约、删除引用门禁与查询模型，支撑模板页眉/芯片 fieldKey 绑定。
- **每个写操作**在目标态对应独立 Command；`fieldKey` 创建后不可变；`valueSource` + `sourcePath`/`defaultValue` 组合校验与现行 App 一致。
- 单次成稿：不写占位句；解析运行时值（按就诊）归属 `EncounterMetadataAppService`，本 SDD 只声明端口与只读 BR。

### 1.2 边界

- **在内**：`emr_metadata_field_def` CRUD 语义、启用列表、引用检查 `MetadataFieldReferenceChecker`。
- **在外**：HTTP DTO、HIS 读库（`EmrAdmissionContextLoader` bridge 实现）、文书 JSON 写入（`DocumentStructureManager`）。
- **跨域仅端口声明**：解析时只读 Encounter/Patient 上下文；**BR-EMR-META-004** 不写外域聚合。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-emr/module-emr-domain`。

### 2.2 包结构（`com.zxplat.plugin.emr.domain.metadata`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `MetadataFieldDef` |
| `valueobject` | 无表值对象 |
| `command` | Command（§5.1；现行） |
| `repository` | `MetadataFieldDefRepository` |
| `service` | `MetadataFieldDefDomainService` |
| `queries` | 目标态 `MetadataFieldDefQueryService`；**当前无** |
| `queries.model` | `MetadataFieldDefQueryModel` 等（§4.6） |
| 同包辅助 | `MetadataValueSource`、`MetadataFieldReferenceChecker`、`model.MetadataResolveRequest` |
| `loader`（端口） | `EmrAdmissionContextLoader`（domain 接口；bridge 实现） |
| `share.events` | 本期无事件 |

**存储口径**：`valueSource`、`dataType`、`maskRule` 均为 **String** 存 code/字面量；`sourceConfig` 为 **TEXT** JSON 字符串。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| 业务键 | `fieldKey` String，租户内唯一（未软删） |
| 时间 | 审计 `LocalDateTime` |
| `valueSource` / `dataType` / `maskRule` | `String` |
| `editableDefault` / `enabled` | `Boolean`；`enabled` → 列 `is_enabled` |
| 软删 | `Boolean isDeleted`（实体基类）；删除路径见 §8 |
| 实体 | `@Entity` `@Table(name = "emr_metadata_field_def")` |
| 仓储 | `JpaRepository` + `JpaSpecificationExecutor` |
| 分页 | DB 侧 Specification；排序 `sortOrder ASC, id ASC` |
| 事务 | 目标态领域服务不显式 `@Transactional` |
| 事件 | 本期无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `emr_metadata_field_def` | `MetadataFieldDef` | 单表聚合根 |

### 4.2 聚合根 `MetadataFieldDef`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `fieldKey` | `String` | 是 | 字段键，max 64；**BR-EMR-META-001**；创建后不可改 |
| `label` | `String` | 是 | 显示名，max 128 |
| `dataType` | `String` | 是 | 默认 string，max 16 |
| `editableDefault` | `Boolean` | 是 | 默认可编辑 |
| `valueSource` | `String` | 是 | 取值源；**BR-EMR-META-002** |
| `sourcePath` | `String` | 否 | 路径，max 256 |
| `sourceConfig` | `String` | 否 | JSON 配置 TEXT |
| `defaultValue` | `String` | 否 | 默认值，max 512 |
| `maskRule` | `String` | 是 | 默认 NONE，max 32 |
| `formatPattern` | `String` | 否 | 格式，max 64 |
| `sortOrder` | `Integer` | 是 | 排序，默认 0 |
| `enabled` | `Boolean` | 是 | 启用 |
| `remark` | `String` | 否 | 备注，max 512 |
| `tenantId` | `Long` | 是 | 租户 |
| `createdAt` | `LocalDateTime` | 系统 | 审计 |
| `updatedAt` | `LocalDateTime` | 系统 | 审计 |
| `createdBy` | `Long` | 系统 | 审计 |
| `updatedBy` | `Long` | 系统 | 审计 |
| `isDeleted` | `Boolean` | 是 | 软删标记 |
| `deletedAt` | `LocalDateTime` | 否 | 软删时间 |
| `deletedBy` | `Long` | 否 | 软删人 |

**不变式方法（目标态）**：`assertFieldKeyFormat()`；`assertFieldKeyUnique()`；`assertValueSourceRecognizable()`；`assertDefinitionComplete()`（非 CONST/MANUAL/REQUEST 等须 path 或 default）。

### 4.3 子实体

无。本聚合为单表。

### 4.4 辅助类型（非表）

| 类型 | 说明 |
| ---- | ---- |
| `MetadataValueSource` | 取值源枚举/常量，与 `valueSource` 列对齐 |
| `MetadataResolveRequest` | 解析入参（就诊/文书上下文键） |
| `MetadataFieldReferenceChecker` | 删除前扫描模板 content 是否引用 fieldKey |

### 4.5 仓储接口

- `MetadataFieldDefRepository extends JpaRepository<MetadataFieldDef, Long>, JpaSpecificationExecutor<MetadataFieldDef>`
- `findByIdAndIsDeletedFalse`、`findByFieldKeyAndIsDeletedFalse`
- `existsByFieldKeyAndIsDeletedFalse`（及 tenant 变体）
- `findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `MetadataFieldDefQueryModel`（对应 `getById` / 列表项）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | 主键 |
| `fieldKey` | `String` | `field_key` | 键 |
| `label` | `String` | `label` | 显示名 |
| `dataType` | `String` | `data_type` | 类型 |
| `editableDefault` | `Boolean` | `editable_default` | 可编默认 |
| `valueSource` | `String` | `value_source` | 来源 |
| `sourcePath` | `String` | `source_path` | 路径 |
| `sourceConfig` | `Map` 或 `String` | `source_config` | JSON |
| `defaultValue` | `String` | `default_value` | 默认 |
| `maskRule` | `String` | `mask_rule` | 掩码 |
| `formatPattern` | `String` | `format_pattern` | 格式 |
| `sortOrder` | `Integer` | `sort_order` | 排序 |
| `enabled` | `Boolean` | `is_enabled` | 启用 |
| `remark` | `String` | `remark` | 备注 |

#### 4.6.2 `MetadataFieldReferenceQueryModel`（对应 `checkReference`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `referenced` | `Boolean` | 计算 | 是否被引用 |
| `templateNames` | `List<String>` | 扫描 `emr_template.content` | 引用模板名列表 |

#### 4.6.3 `ResolvedMetadataValuesQueryModel`（对应 `EncounterMetadataAppService.resolveValues`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `valuesByFieldKey` | `Map<String, String>` | 运行时解析 | 不落库；只读上下文 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `MetadataFieldDefDomainService`

#### 5.1.1 `create(CreateMetadataFieldDefCommand cmd)` → `MetadataFieldDef`

**命令文件**：`CreateMetadataFieldDefCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `fieldKey` | `String` | 字母开头 2～64；唯一 |
| `label` | `String` | 显示名 |
| `dataType` | `String` | 默认 string |
| `editableDefault` | `Boolean` | 默认可编 |
| `valueSource` | `String` | 必填，大写规范化 |
| `sourcePath` | `String` | 可选 |
| `sourceConfig` | `Map<String, Object>` | 序列化入 `source_config` |
| `defaultValue` | `String` | 可选 |
| `maskRule` | `String` | 默认 NONE |
| `formatPattern` | `String` | 可选 |
| `sortOrder` | `Integer` | 默认 0 |
| `enabled` | `Boolean` | 默认 true |
| `remark` | `String` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertFieldKeyFormat()` + `assertFieldKeyUnique()` + `assertDefinitionComplete()` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `existsByFieldKeyAndIsDeletedFalse(fieldKey)` 为 false |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-META-W-001 |

**方法契约摘要**：**前置**—fieldKey 格式与唯一；valueSource 可识别；定义完整。**持久化**—insert 一行。**失败**—ValidationException。**事件**—无。

**注释锚点**：normalize fieldKey → 唯一校验 → validateDefinition → save → 返回 id。

---

#### 5.1.2 `update(UpdateMetadataFieldDefCommand cmd)` → `MetadataFieldDef`

**命令文件**：`UpdateMetadataFieldDefCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |
| `label` | `String` | 可选 |
| `dataType` | `String` | 可选 |
| `editableDefault` | `Boolean` | 可选 |
| `valueSource` | `String` | 可选 |
| `sourcePath` | `String` | 可选 |
| `sourceConfig` | `Map<String, Object>` | 可选 |
| `defaultValue` | `String` | 可选 |
| `maskRule` | `String` | 可选 |
| `formatPattern` | `String` | 可选 |
| `sortOrder` | `Integer` | 可选 |
| `enabled` | `Boolean` | 可选 |
| `remark` | `String` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDefinitionComplete()`（更新后整体） |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-META-W-002 |

**方法契约摘要**：**前置**—存在；**禁止**改 fieldKey。**持久化**—增量 update。**失败**—不存在 EntityNotFound；定义不合法 Validation。**事件**—无。

**注释锚点**：load → 合并 → validateDefinition → save。

---

#### 5.1.3 `delete(DeleteMetadataFieldDefCommand cmd)` → `void`

**命令文件**：`DeleteMetadataFieldDefCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)`；`MetadataFieldReferenceChecker.isReferenced(fieldKey)` 为 false |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-META-W-003 |

**方法契约摘要**：**前置**—存在；**BR-EMR-META-003** 仍被模板引用则拒绝（提示禁用）。**持久化**—**当前**物理 `delete`；目标态可收敛 `markDeleted()`。**失败**—引用中 ValidationException。**事件**—无。

**注释锚点**：load → reference check → delete 或 soft delete。

---

### 5.2 读侧 — `MetadataFieldDefQueryService`（目标态；**当前 `MetadataFieldAppService` + Repository**）

所有维护查询 **默认** `isDeleted=false`（启用 Filter + Specification）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `pageMetadataFieldDefs` | 分页 + keyword、`valueSource?`、`enabled?` | `MetadataFieldDefQueryModel` 分页 | Specification 租户+软删 | TC-EMR-META-R-001 |
| `listEnabledMetadataFieldDefs` | keyword? | `MetadataFieldDefQueryModel` 列表 | enabled=true；sortOrder | TC-EMR-META-R-002 |
| `getMetadataFieldDefById` | `id` | `MetadataFieldDefQueryModel` | 单条 | TC-EMR-META-R-003 |
| `checkFieldReference` | `fieldKey` | `MetadataFieldReferenceQueryModel` | `MetadataFieldReferenceChecker` | TC-EMR-META-R-004 |
| `resolveValues`（EncounterMetadataAppService） | `MetadataResolveRequest` | `ResolvedMetadataValuesQueryModel` | Loader 只读；不写 HIS | TC-EMR-META-R-005 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-EMR-META-001 | 创建时 `existsByFieldKeyAndIsDeletedFalse`；fieldKey 正则校验 |
| BR-EMR-META-002 | `MetadataValueSource` 识别；非法 valueSource 拒绝 |
| BR-EMR-META-003 | 删除前 `MetadataFieldReferenceChecker`；被引用则 ValidationException |
| BR-EMR-META-004 | 解析在 `EncounterMetadataAppService` + Loader；无写外域 Repository |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| （本期无领域事件） | — | — | — |

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 维护列表与按 id 查询：默认排除 `isDeleted=true`（与 Repository Filter 一致）。
- **删除**：须先通过引用检查；仍被模板引用时 **禁止删除**，应改 `enabled=false`（与现行 App 一致）。
- **现行实现**：无引用时 JPA `delete` 物理移除行；若未来统一软删，须在迁移策略中处理历史引用扫描口径。
- 启用下拉 `getEnabledList`：仅 `enabled=true` 且未删（物理删后自然不存在）。

---

## 9. 框架优先适配规则

- `BaseAuditedEntity` + 租户 SpecificationUtils（App 层已用）。
- `sourceConfig` 存 JSON 字符串；App 层 ObjectMapper 读写，领域层保持 String 列。
- 遵循项目级成码门禁；Repository 无手写 JPQL。
- Mode B：写侧 DomainService/Command 已落地；读/解析仍以 AppService 为对照基线。

---

## 10. 生成与验收清单

- [ ] 实体 §4.2 与表 `emr_metadata_field_def` 一致
- [ ] Repository §4.5 方法可用
- [ ] 三写操作 Command 字段与 §5.1 一致（或 Request 无漂移）
- [ ] 引用检查覆盖 BR-EMR-META-003
- [ ] 解析路径不写外域表（BR-EMR-META-004）
- [ ] TC-EMR-META-W/R 系列可映射测试

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-EMR-003 v1.0 + 本 SDD + module-emr-domain 可编译。

### 11.2 DoD

- 字段目录 CRUD 行为与 §5 一致；fieldKey 不可变；删除门禁可测。
- 若生成 DomainService，须迁移 App 内校验至实体/领域服务且不降级 BR。

### 11.3 失败处理

- BR 测试失败或删除误伤引用 → 停止；回写 SDD 差异或修实现。

---

## 定稿检查清单（可打勾）

- [ ] 金骨架章节齐全
- [ ] §5.1 三块写契约完整
- [ ] §4.2 覆盖 DM §3.1 全部业务字段
- [ ] §7 无事件一行
- [ ] 物理删与软删差异已标注

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 8 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **96，可进入代码会话** |

---

**本 SDD 正文结束。**
