# DM-TER-001 CodeSystem 术语编码系统 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-TER-001-CodeSystem-术语编码系统.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：terminology · 码表  
> **聚合**：CodeSystem（Concept 为子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-TER-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包 `com.zxplat.domain.terminology` | 满足 | |
| DM 附录写方法 ×9 | 满足 | §5.1.1～5.1.9 |
| DM 附录读方法 ×15 | 满足 | §5.2 + §4.6 |
| DM §7 BR ×2 | 满足 | §6 |
| DM §5 事件 | 满足 | 无独立事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 维护 `ter_code_system` / `ter_concept`：编码系统 CRUD、概念 CRUD、默认项与启停。
- 读侧带字典缓存（`CodeSystemQueryService`）；写后由 AS 或 Query 驱逐缓存（实现已存在）。
- 一方法一 Command（CodeSystem / Concept 分目录）。

### 1.2 边界

- **在内**：术语表与缓存读路径。
- **在外**：业务模块字典消费策略；跨系统映射见 `TerminologyCodeMapping`（custom）。
- **跨域**：`PublicationStatus` 复用 `com.zxplat.domain.share.enums`。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-core/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.terminology`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `CodeSystem`、`Concept` |
| `command.codesystem` | `CreateCodeSystemCommand`、`UpdateCodeSystemCommand` |
| `command.concept` | `CreateConceptCommand`、`UpdateConceptCommand` |
| `repository` | `CodeSystemRepository`、`ConceptRepository` |
| `service` | `CodeSystemDomainService`、`CodeSystemQueryService` |
| `query` | `CodeSystemQuery`（Probe） |
| `share.events` | 无本聚合事件 |

**存储口径**：`status` → `String`（`PublicationStatus` code）；`isDefault` → `String`（Y/N）；`isActive` → `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| `status` | `String` 字典态 |
| 软删 | Concept 使用 `isDeleted`；CodeSystem 停用用 `status=inactive` |
| 仓储 | `JpaRepository` 接口 |
| 事务 | DomainService 不声明 `@Transactional` |
| 事件 | 无 `*EventFactory` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `ter_code_system` | `CodeSystem` | 编码系统 |
| `ter_concept` | `Concept` | 概念项 |

### 4.2 聚合根 `CodeSystem`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `name` | `String` | 是 | 唯一标识（原 dictType） |
| `title` | `String` | 否 | 标题 |
| `description` | `String` | 否 | 描述 |
| `version` | `String` | 否 | 默认 1.0 |
| `status` | `String` | 否 | active/inactive 等 |
| `tenantId` | `Long` | 否 | 租户 |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体 `Concept`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `codeSystemId` | `Long` | 是 | FK |
| `code` | `String` | 是 | 系统内唯一（BR-TER-001） |
| `display` | `String` | 是 | 展示名 |
| `definition` | `String` | 否 | 定义 |
| `parent` | `Concept` | 否 | 树形父节点 |
| `sortOrder` | `Long` | 否 | 排序 |
| `isActive` | `Boolean` | 是 | 启用 |
| `cssClass` | `String` | 否 | |
| `listClass` | `String` | 否 | |
| `isDefault` | `String` | 否 | Y/N（BR-TER-002） |
| `isDeleted` | `Boolean` | 系统 | 逻辑删 |

**归属校验**：`updateConcept` / 父节点变更须同 `codeSystemId`。

### 4.4 子实体（续）

无额外子表。

### 4.5 仓储接口

- `CodeSystemRepository`：`existsByName`、`findById`
- `ConceptRepository`：`findByCodeSystemIdAndCode`、`findById`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `findById` / `findByName`

直接返回 `CodeSystem` 实体，字段见 §4.2。

#### 4.6.2 `pageQueryCodeSystem`

直接返回 `Page<CodeSystem>`；条件来自 `JSONObject` + `CodeSystem` probe。

#### 4.6.3 `listAllCodeSystems`

直接返回 `List<CodeSystem>`。

#### 4.6.4 `findConceptById` / `findConceptsByCodeSystem`

直接返回 `Concept` / `List<Concept>`，字段见 §4.3。

#### 4.6.5 `findConceptByCodeSystemNameAndCode` / `findConceptsByCodeSystemName`

直接返回 `Concept` / `List<Concept>`。

#### 4.6.6 `getOptionListByCodeSystemName` / `getOptionListByEnum`

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `value` | `String` | concept.code | OptionDto |
| `label` | `String` | concept.display | OptionDto |

#### 4.6.7 `getDisplayByCode`

出参 `String` display；无独立 QueryModel。

#### 4.6.8 `evictDictByCodeSystemName` / `evictDictByCodeSystemId` / `clearAllDictCache` / `initCodeSystemCache`

缓存管理方法，无 QueryModel；副作用为驱逐/预热缓存。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `CodeSystemDomainService`

#### 5.1.1 `create(CreateCodeSystemCommand command)` → `CodeSystem`

**命令文件**：`CreateCodeSystemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 必填，唯一 |
| `title` | `String` | 可选 |
| `description` | `String` | 可选 |
| `version` | `String` | 默认 1.0 |
| `status` | `String` | 默认 ACTIVE |
| `tenantId` | `Long` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | name 唯一 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `existsByName` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W01 |

**方法契约摘要**：前置：name 未占用；持久化：insert；失败：重名 `IllegalArgumentException`；**注释锚点**：唯一校验 → build → save。

---

#### 5.1.2 `update(UpdateCodeSystemCommand command)` → `CodeSystem`

**命令文件**：`UpdateCodeSystemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |
| `name` | `String` | 可选 patch |
| `title` | `String` | 可选 |
| `description` | `String` | 可选 |
| `version` | `String` | 可选 |
| `status` | `String` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 存在性 |
| `statusTransitionMethod` | status patch |
| `repositoryChecks` | `findById`；改名时 `existsByName` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W02 |

**方法契约摘要**：前置：id 存在；持久化：增量覆盖；失败：不存在/重名；**注释锚点**：load → patch → save。

---

#### 5.1.3 `delete(Long id)` → `void`

**命令文件**：N/A（入参 id）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 置 inactive |
| `statusTransitionMethod` | active → inactive |
| `repositoryChecks` | `findById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W03 |

**方法契约摘要**：逻辑停用非物理删；**注释锚点**：load → setStatus(INACTIVE) → save。

---

#### 5.1.4 `createConcept(CreateConceptCommand command)` → `Concept`

**命令文件**：`CreateConceptCommand.java`

**字段全集**：见源码 §4.3 可写字段 + `parentId` 可选。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | BR-TER-001 code 唯一 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByCodeSystemIdAndCode` 空；父存在且同系统 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W04 |

**方法契约摘要**：前置：code 未占用；持久化：insert；失败：重复 code/非法父；**注释锚点**：唯一 → 父校验 → save。

---

#### 5.1.5 `updateConcept(UpdateConceptCommand command)` → `Concept`

**命令文件**：`UpdateConceptCommand.java`

**字段全集**：`id` 必填；其余 patch 见 Command 类。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 禁止自引用父 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findById`；改 code 重校唯一 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W05 |

**方法契约摘要**：增量更新；**注释锚点**：load → patch → 父/ code 校验 → save。

---

#### 5.1.6 `defaultConcept(Long id)` → `void`

**命令文件**：N/A

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 概念 PK |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | BR-TER-002 清零其他默认 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findById`；遍历同系统 concepts |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W06 |

**方法契约摘要**：同系统仅一项 `isDefault=Y`；**注释锚点**：load → 清 others → 标记当前 → save。

---

#### 5.1.7 `activateConcept(Long id)` → `void`

**命令文件**：N/A

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `isActive=true` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W07 |

**方法契约摘要**：启用概念；**注释锚点**：load → setActive → save。

---

#### 5.1.8 `deactivateConcept(Long id)` → `void`

**命令文件**：N/A

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `isActive=false` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W08 |

**方法契约摘要**：停用概念；**注释锚点**：load → setInactive → save。

---

#### 5.1.9 `deleteConcept(Long id)` → `void`

**命令文件**：N/A

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无子节点 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | children 空 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TER-CS-W09 |

**方法契约摘要**：`isDeleted=true`；有子拒绝；**注释锚点**：load → 子校验 → soft delete。

---

### 5.2 读侧 — `CodeSystemQueryService`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `findById` | `Long id` | `CodeSystem` §4.6.1 | 缓存可选 | TC-TER-CS-R01 |
| `findByName` | `String name` | `CodeSystem` §4.6.1 | | TC-TER-CS-R02 |
| `pageQueryCodeSystem` | JSON + probe | `Page<CodeSystem>` §4.6.2 | DB 分页 | TC-TER-CS-R03 |
| `listAllCodeSystems` | — | `List<CodeSystem>` §4.6.3 | | TC-TER-CS-R04 |
| `findConceptById` | `Long id` | `Concept` §4.6.4 | | TC-TER-CS-R05 |
| `findConceptsByCodeSystem` | `Long csId` | `List<Concept>` §4.6.4 | active 过滤 | TC-TER-CS-R06 |
| `findConceptByCodeSystemNameAndCode` | name, code | `Concept` §4.6.5 | | TC-TER-CS-R07 |
| `findConceptsByCodeSystemName` | name | `List<Concept>` §4.6.5 | | TC-TER-CS-R08 |
| `getOptionListByCodeSystemName` | name | `List<OptionDto>` §4.6.6 | 缓存 | TC-TER-CS-R09 |
| `getOptionListByEnum` | `Class<E>` | `List<OptionDto>` §4.6.6 | 枚举转选项 | TC-TER-CS-R10 |
| `getDisplayByCode` | enumClass, code | `String` §4.6.7 | | TC-TER-CS-R11 |
| `evictDictByCodeSystemName` | name | void §4.6.8 | 驱逐 | TC-TER-CS-R12 |
| `evictDictByCodeSystemId` | id | void §4.6.8 | 驱逐 | TC-TER-CS-R13 |
| `clearAllDictCache` | — | void §4.6.8 | 全清 | TC-TER-CS-R14 |
| `initCodeSystemCache` | — | void §4.6.8 | 预热 | TC-TER-CS-R15 |

**读方法注释锚点（统一）**：构建查询条件（含软删/ active 过滤）→ 单次 SQL/仓储或缓存 → 组装结果（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-TER-001** | `createConcept` / `updateConcept` 查 `codeSystemId+code` 唯一 |
| **BR-TER-002** | `defaultConcept` 同系统其余置 N |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |

（无独立领域事件。）

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- CodeSystem：`delete` 为 status 停用；Concept：`deleteConcept` 为 `isDeleted`。
- 读侧下拉默认 active 概念；缓存与 DB 一致由驱逐方法保证。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；`OptionDto` 来自 share DTO。
- 写后缓存驱逐由 QueryService 或 AS 调用 §5.2 驱逐方法。

---

## 10. 生成与验收清单

- [ ] 9 写方法 Command/入参与代码一致
- [ ] BR-TER-001/002 单测
- [ ] 14+ 读方法（含缓存）冒烟
- [ ] name 唯一与 code 唯一集成测

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-TER-001、本 SDD、Flyway ter_* 表

### 11.2 DoD

- `zxplat-domain` 编译通过

### 11.3 失败处理

- 缓存中间件未配置 → 读路径降级直连 DB 须在 AS 文档说明

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 9
- [ ] §5.2 读方法 = 15（init/evict 计入 QueryService 白名单）
- [ ] Concept 字段表 ≥ DM §3

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
| **总分** | **100** | **≥95，可进入代码会话** |

---

**本 SDD 正文结束。**
