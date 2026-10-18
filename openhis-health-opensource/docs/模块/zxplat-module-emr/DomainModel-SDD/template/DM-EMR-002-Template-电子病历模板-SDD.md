# DM-EMR-002 Template 电子病历模板 · DomainModel-SDD

> **对应 DM**：`docs/模块/zxplat-module-emr/DomainModel/template/DM-EMR-002-Template-电子病历模板.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-emr`  
> **限界上下文**：电子病历（EMR 插件）  
> **聚合**：Template（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-EMR-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-plugin/zxplat-module-emr/module-emr-domain` |
| 包规则 `com.zxplat.plugin.emr.domain.template` | 满足 | `template` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9；遵循项目级成码门禁 |
| DM 第 6 章写/读白名单 | 满足 | §5 写侧 5 个逻辑操作、读侧 4 项 |
| DM 第 7 章 BR | 满足 | §6 映射 BR-EMR-TPL-001～004 |
| DM 第 5 章事件 | 满足 | §7 本期无事件 |

**与 DM 显式差异（采纳依据：Mode B 代码先行 + DM §6 如实记录）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 写侧入口 | §6.1 `TemplateDomainService` | §5.1 Command + 方法契约；现行实现一致 |
| 读侧入口 | §6.2 未单列 QueryService | §5.2 目标态 `TemplateQueryService`；**当前**读能力在 `TemplateAppService` / `DocumentManager` |
| `encounterClassEnum` | §3.1 遗留整型 | 保留 `Integer` 列；新数据优先 `encounterClassCode` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Template 聚合** 的持久化模型、写读契约、不变式与（空）事件策略，使后续会话可收敛领域层或对照 Mode B 实现做一致性审计。
- 将「实现前澄清」前置：**每个写操作对应独立 Command 文件（现行）**，命令字段全集与 DM §3 字段对齐；范围规则经 `TemplateScopeValidator` 统一校验。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；写侧 DomainService 已落地。

### 1.2 边界

- **在内**：`emr_template` 实体、范围（PERSONAL/DEPARTMENT/HOSPITAL）、站别可见过滤、`enabled` 与软删语义、模板正文 JSON。
- **在外**：HTTP/API DTO（`module-emr-application`）、文书实例创建（DM-EMR-001）、元数据芯片解析（DM-EMR-003）、PDF 渲染。
- **跨域仅端口声明**：`documentTypeCode` 关联文书类型；读侧可经 `DocumentManager.getTemplateById` 只读引用，不落库外域实体。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-emr/module-emr-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.plugin.emr.domain.template`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Template` 聚合根 |
| `valueobject` | 无独立值对象表；范围/站别用枚举与 String 列 |
| `enums`（插件内） | `com.zxplat.plugin.emr.domain.enums.TemplateScopeLevel`、`TemplateStationKind`（读侧过滤，非表列） |
| `command` | 一写操作一 Command（见 §5.1；现行） |
| `repository` | `TemplateRepository`（`JpaRepository` + `JpaSpecificationExecutor`） |
| `service` | `TemplateDomainService` |
| `queries` | 目标态 `TemplateQueryService`；**当前无实现** |
| `queries.model` | 目标态 QueryModel（§4.6）；**当前** App 层 `TemplateDto` 映射 |
| `share.events` | 不在本包；本期无领域事件（§7） |

**存储口径（DM 优先）**：`scopeLevel` 存 **String**（`TemplateScopeLevel` code）；`encounterClassCode` 为 **String** 字典码；`encounterClassEnum` / `encounterTypeEnum` 为 **Integer** 遗留列；`enabled` 映射列 `is_enabled`（Java 属性名 `enabled`）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id`；`ownerPractitionerId`、`organizationId` 与 DM 一致 |
| 时间 | 审计字段 `createdAt` / `updatedAt` / `deletedAt` → `LocalDateTime` |
| `scopeLevel` | `String`（存 `TemplateScopeLevel` code） |
| `encounterClassCode` | `String`（AMB/IMP/OBSENC 等） |
| `*_enum`（遗留列） | `Integer`（`encounterClassEnum`、`encounterTypeEnum`） |
| `_flag` / 启用 | `Boolean enabled` → 列 `is_enabled` |
| 软删 | `Boolean isDeleted`，继承 `BaseAuditedEntity` / `AuditedEntityBase` |
| 实体 | `@Entity` + `@Table(name = "emr_template")` + `@Column` |
| 仓储 | 仅 `JpaRepository` 接口，无自定义实现类 |
| 分页 | `Pageable` / Specification；维护列表默认库侧过滤；未指定排序时维护页可按 `name` 或 `createdAt DESC` |
| 事务 | 目标态领域服务不声明 `@Transactional`；参与 AppService UoW |
| 事件 | 本期无；若未来增补须 `beforeCommit` + 共享 events 包 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `emr_template` | `Template` | 聚合根单表；`code` 列 unique |

### 4.2 聚合根 `Template`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键；Snowflake |
| `name` | `String` | 否 | 名称，max 128 |
| `code` | `String` | 是 | 唯一编码，max 64；**BR-EMR-TPL-001** |
| `documentTypeCode` | `String` | 否 | 文书类型，max 32 |
| `scopeLevel` | `String` | 是 | PERSONAL / DEPARTMENT / HOSPITAL |
| `ownerPractitionerId` | `Long` | 条件 | PERSONAL 必填；**BR-EMR-TPL-002** |
| `organizationId` | `Long` | 条件 | DEPARTMENT 必填；**BR-EMR-TPL-002** |
| `encounterClassCode` | `String` | 否 | AMB / IMP / OBSENC 等 |
| `encounterClassEnum` | `Integer` | 否 | 遗留整型；新数据优先 code |
| `encounterTypeEnum` | `Integer` | 否 | 子类型；空=全部可见 |
| `content` | `String` | 否 | 模板 JSON（TEXT） |
| `enabled` | `Boolean` | 是 | 启用；医生站默认仅见 true；**BR-EMR-TPL-003** |
| `tenantId` | `Long` | 是 | `BaseAuditedEntity`；租户隔离 |
| `createdAt` | `LocalDateTime` | 系统 | 审计 |
| `updatedAt` | `LocalDateTime` | 系统 | 审计 |
| `createdBy` | `Long` | 系统 | 审计 |
| `updatedBy` | `Long` | 系统 | 审计 |
| `isDeleted` | `Boolean` | 是 | 软删；**BR-EMR-TPL-004** |
| `deletedAt` | `LocalDateTime` | 否 | 软删时间 |
| `deletedBy` | `Long` | 否 | 软删操作者 |

**不变式方法（目标态实体或共享校验器，命名须一致）**：`assertCodeUniqueForTenant()`；`assertScopeFields()`（委托 `TemplateScopeValidator.validateForSave`）；`markDeleted()`（置 `isDeleted=true`）。

### 4.3 子实体

无。本聚合为单表。

### 4.4 枚举与辅助类型（非表）

| 名称 | 存储 | 说明 |
| ---- | ---- | ---- |
| `TemplateScopeLevel` | String code | PERSONAL / DEPARTMENT / HOSPITAL |
| `TemplateStationKind` | 应用/读侧 | 门诊/住院 → 映射 `encounterClassCode` 过滤 |
| 就诊类别 | `encounterClassCode` String | 与 EncounterClass.code 对齐 |

### 4.5 仓储接口

- `TemplateRepository extends JpaRepository<Template, Long>, JpaSpecificationExecutor<Template>`
- `Optional<Template> findByIdAndIsDeletedFalse(Long id)`
- `Optional<Template> findByCodeAndIsDeletedFalse(String code)`
- 列表/站别可见：**Specification** 或命名查询；软删由框架 `deletedFilter` 与显式 `isDeleted=false` 谓词一致

**说明**：框架已统一软删过滤时，禁止冗余堆砌 `…AndIsDeletedFalse` 于所有方法名；编码唯一、按 id 加载等**写路径**须显式查未删行。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `TemplateDetailQueryModel`（对应 `getTemplate` / `getContent`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `emr_template.id` | 主键 |
| `name` | `String` | `name` | 名称 |
| `code` | `String` | `code` | 编码 |
| `documentTypeCode` | `String` | `document_type_code` | 文书类型 |
| `scopeLevel` | `String` | `scope_level` | 范围 |
| `organizationId` | `Long` | `organization_id` | 科室 |
| `ownerPractitionerId` | `Long` | `owner_practitioner_id` | 个人所有者 |
| `encounterClassCode` | `String` | `encounter_class_code` | 就诊类别 |
| `encounterClassEnum` | `Integer` | `encounter_class_enum` | 遗留 |
| `encounterTypeEnum` | `Integer` | `encounter_type_enum` | 子类型 |
| `content` | `String` | `content` | 正文 JSON |
| `enabled` | `Boolean` | `is_enabled` | 启用 |
| `createdAt` | `LocalDateTime` | `created_at` | 审计 |
| `updatedAt` | `LocalDateTime` | `updated_at` | 审计 |

#### 4.6.2 `TemplateListItemQueryModel`（对应 `pageTemplates` / `getList`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | 主键 |
| `name` | `String` | `name` | 名称 |
| `code` | `String` | `code` | 编码 |
| `documentTypeCode` | `String` | `document_type_code` | 文书类型 |
| `scopeLevel` | `String` | `scope_level` | 范围 |
| `organizationId` | `Long` | `organization_id` | 科室 |
| `ownerPractitionerId` | `Long` | `owner_practitioner_id` | 个人 |
| `encounterClassCode` | `String` | `encounter_class_code` | 就诊类别 |
| `encounterTypeEnum` | `Integer` | `encounter_type_enum` | 子类型 |
| `enabled` | `Boolean` | `is_enabled` | 启用 |
| `createdAt` | `LocalDateTime` | `created_at` | 审计 |

**说明**：列表默认**不含** `content`，避免大字段拖垮分页。

#### 4.6.3 `TemplateStationListItemQueryModel`（对应 `listForStation`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | 主键 |
| `name` | `String` | `name` | 展示名 |
| `code` | `String` | `code` | 编码 |
| `documentTypeCode` | `String` | `document_type_code` | 过滤条件 |
| `scopeLevel` | `String` | `scope_level` | 排序：个人→科室→全院 |
| `encounterClassCode` | `String` | `encounter_class_code` | 须与站别一致 |
| `enabled` | `Boolean` | `is_enabled` | 默认 true |

#### 4.6.4 `DocumentManager.getTemplateById`

直接返回 `Template` 实体或 `TemplateDetailQueryModel`，无独立 QueryModel 文件；读路径须 `isDeleted=false` 且业务上允许跨模块只读。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `TemplateDomainService`

#### 5.1.1 `createTemplate(CreateTemplateCommand cmd)` → `Template`

**命令文件**：`CreateTemplateCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 名称 |
| `code` | `String` | 唯一编码；空则实现生成策略须在 App 层拍板 |
| `documentTypeCode` | `String` | 文书类型 |
| `scopeLevel` | `String` | PERSONAL / DEPARTMENT / HOSPITAL |
| `organizationId` | `Long` | DEPARTMENT 时必填 |
| `ownerPractitionerId` | `Long` | PERSONAL 时必填 |
| `encounterClassCode` | `String` | AMB / IMP / OBSENC |
| `encounterTypeEnum` | `Integer` | 可选子类型 |
| `content` | `String` | 模板 JSON |
| `enabled` | `Boolean` | 默认 true |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `applyScopeFields` + 编码唯一检查 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByCodeAndIsDeletedFalse(code)` 不存在 |
| `eventPublishAnchor` | N/A（本期无事件） |
| `unitTestCaseId` | TC-EMR-TPL-W-001 |

**方法契约摘要**：**前置**—`code` 若提供则租户内未占用；`TemplateScopeValidator.validateForSave` 通过；医生站路径由 App 解析会话后写入 Command。**持久化**—insert `Template` 一行。**失败**—编码冲突 `IllegalArgumentException`；范围不合法 `ValidationException`。**事件**—无。

**注释锚点**：校验编码 → 校验范围 → 组装实体 → `repository.save` → 返回实体。

---

#### 5.1.2 `updateTemplate(UpdateTemplateCommand cmd)` → `Template`

**命令文件**：`UpdateTemplateCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 待更新模板主键 |
| `name` | `String` | 可选 |
| `code` | `String` | 可选；变更时唯一 |
| `documentTypeCode` | `String` | 可选 |
| `scopeLevel` | `String` | 可选；与 org/owner/encounter 联动 |
| `organizationId` | `Long` | 可选 |
| `organizationIdSpecified` | `boolean` | 是否覆盖 org |
| `ownerPractitionerId` | `Long` | 可选 |
| `ownerPractitionerIdSpecified` | `boolean` | 是否覆盖 owner |
| `encounterClassCode` | `String` | 可选 |
| `encounterTypeEnum` | `Integer` | 可选 |
| `content` | `String` | 可选 |
| `enabled` | `Boolean` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `applyScopeFields`（范围变更时） |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)` 存在；改 code 时 `findByCodeAndIsDeletedFalse` 非他行 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-TPL-W-002 |

**方法契约摘要**：**前置**—记录存在且未软删。**持久化**—增量 update。**失败**—不存在 `IllegalArgumentException`；编码冲突。**事件**—无。

**注释锚点**：loadById → 合并字段 → 范围重验 → save。

---

#### 5.1.3 `deleteTemplate(DeleteTemplateCommand cmd)` → `void`

**命令文件**：`DeleteTemplateCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 模板主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 置 `isDeleted=true` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(id)` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-TPL-W-003 |

**方法契约摘要**：**前置**—id 有效（空 id 由 App noop）。**持久化**—逻辑删。**失败**—不存在则抛错。**事件**—无。

**注释锚点**：load → markDeleted → save。

---

#### 5.1.4 `setTemplateEnabled(SetTemplateEnabledCommand cmd)` → `void`

**命令文件**：`SetTemplateEnabledCommand.java`（现行）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `templateId` | `Long` | 模板主键 |
| `enabled` | `Boolean` | 启用标志 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdAndIsDeletedFalse(templateId)` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-TPL-W-004 |

**方法契约摘要**：**前置**—模板存在。**持久化**—更新 `enabled`。**失败**—不存在。**事件**—无。

**注释锚点**：load → set enabled → save。

---

#### 5.1.5 `copyTemplate(CopyTemplateCommand cmd)` → `Long`

**命令文件**：`CopyTemplateCommand.java`（**目标态**；当前 `CopyTemplateRequest` + `copyTemplate`）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `sourceTemplateId` | `Long` | 源模板 id |
| `newName` | `String` | 可选；默认源名+「（副本）」 |
| `newCode` | `String` | 可选；默认源 code+`_copy` |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCodeUniqueForTenant()`（新 code） |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 源 `findByIdAndIsDeletedFalse`；新 code 唯一 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-EMR-TPL-W-005 |

**方法契约摘要**：**前置**—源存在；新 code 不冲突。**持久化**—insert 新行，复制 scope/content/enabled 等。**失败**—源不存在或编码冲突。**事件**—无。

**注释锚点**：load 源 → 克隆字段 → 赋新 code/name → save。

---

### 5.2 读侧 — `TemplateQueryService`（目标态；**当前 `TemplateAppService` + `TemplateRepository` / `DocumentManager`**）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

**「出参」填 QueryModel 类名**；字段定义见 **§4.6**。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `pageTemplates` | 分页 + `documentTypeCode?`、`scopeLevel?`、`encounterClassCode?`、`organizationId?`、`enabled?`、keyword | `TemplateListItemQueryModel` 分页 | Specification 软删+过滤；不含 content | TC-EMR-TPL-R-001 |
| `listForStation` | `stationKind`、`documentTypeCode?`、`enabled?` + 当前用户 org/practitioner | `TemplateStationListItemQueryModel` 列表 | `TemplateStationKind`→`encounterClassCode`；范围 OR（全院/本科室/本人） | TC-EMR-TPL-R-002 |
| `getTemplate` | `id` | `TemplateDetailQueryModel` | 含 content | TC-EMR-TPL-R-003 |
| `getTemplateById`（DocumentManager） | `id` | `Template` 或 DetailModel | 跨模块只读 | TC-EMR-TPL-R-004 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-EMR-TPL-001 | 创建/改 code/copy 前 `findByCodeAndIsDeletedFalse`；冲突拒绝 |
| BR-EMR-TPL-002 | `TemplateScopeValidator.validateForSave`：PERSONAL 要 owner；DEPARTMENT 要 organizationId；HOSPITAL 清空 owner/org |
| BR-EMR-TPL-003 | 读侧 `listForStation` / 医生站：`enabled=true` + 站别 `encounterClassCode` + 范围可见性 OR |
| BR-EMR-TPL-004 | 软删后读写默认不可见；`deleteTemplate` 仅置 `isDeleted` |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| （本期无领域事件） | — | — | — |

**发布时机**：不适用；若未来增补事件，须 **beforeCommit** 同步发布。

---

## 8. 删除与可见性契约

- 模板删除为 **逻辑删除**（`isDeleted=true`），禁止维护路径物理 `DELETE` 行（与当前 `TemplateAppService.deleteTemplate` 一致）。
- Repository 读默认启用软删 Filter + 查询谓词 `isDeleted=false`。
- 医生站列表：仅 **启用** 且 **范围/站别匹配** 的未删模板（BR-EMR-TPL-003）。
- 维护分页：可筛 `enabled`；默认仍排除已软删行。
- 复制产生 **新主键** 与新 `code`，不修改源行。

---

## 9. 框架优先适配规则

- 继承 `BaseAuditedEntity` + `IMultiTenant`；租户过滤器与审计由框架填充。
- 软删读侧依赖 `SoftDeleteFilterAspect` + `@FilterDef deletedFilter`；写侧软删显式设 `isDeleted`。
- 遵循项目级成码门禁（JavaDoc、Repository 禁手写 JPQL、Long 主键雪花等）。
- Mode B：写侧 `TemplateDomainService` + Command 已落地；读侧仍以 AppService / Repository 为对照基线（QueryService 非本期强制）。

---

## 10. 生成与验收清单

- [ ] `Template` 实体字段与 §4.2、表 `emr_template` 一致
- [ ] `TemplateRepository` 方法签名与 §4.5 一致
- [ ] 目标态 5 个 Command 文件字段与 §5.1 表一致（或 App Request 对照无漂移）
- [ ] `TemplateScopeValidator` 覆盖 BR-EMR-TPL-002
- [ ] 读侧分页 total 与过滤口径一致（禁止先分页后内存丢行）
- [ ] 单元测试 TC-EMR-TPL-W-001～005、R-001～004 可映射到不变式
- [ ] 与 DM-EMR-002 v1.0 无未标注字段/方法漂移

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-EMR-002 v1.0 + 本 SDD + 目标模块 `module-emr-domain` 已可编译（Mode B 基线）。

### 11.2 DoD

- 实体/仓储与 §4 一致；若生成 DomainService，则 §5.1 五方法可编译且测试绿。
- 无占位 `return true` 业务规则；编码/范围校验可测。
- 文档与实现差异须回写 DM 或本 SDD 差异表，禁止口头补丁。

### 11.3 失败处理

- 编译或 BR 测试失败 → 停止合并；优先修正 SDD 或实现之一并同步另一份文档。

---

## 定稿检查清单（可打勾）

- [ ] §1.4 硬门禁：标题、输入表、§1～§11 顺序、§5.1 五块、定稿节、结束语
- [ ] §4.2 字段不少于 DM §3.1
- [ ] §5.1 每块含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §6 覆盖 BR-EMR-TPL-001～004
- [ ] §7 无事件已显式一行
- [ ] Mode B 落点（AppService）已标注

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98，可进入代码会话** |

---

**本 SDD 正文结束。**
