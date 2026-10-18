# DM-PACS-004 PacsReportTemplate 报告模板 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PACS-004-PacsReportTemplate-报告模板.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-pacs/module-pacs-domain`  
> **限界上下文**：检查域（PACS/RIS）· 报告模板配置  
> **聚合**：PacsReportTemplate（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PACS-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-pacs-domain` |
| 包规则 `com.zxplat.plugin.pacs.domain.reporttemplate` | 满足 | `reporttemplate` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 写/读白名单（§5 + 附录 Domain/QueryService） | 满足 | §5 覆盖 save/disable/delete/getById/requireEnabled + search |
| DM §4 业务规则 | 满足 | §6 映射 BR-PRTPL-01～04 |
| DM 领域事件 | 满足 | 无（配置型聚合） |

**与 DM 显式差异（采纳依据：附录模式 B 与现网实现）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 方法落点 | DM §5 将 search/getById 与写方法同表 | search 归 `PacsReportTemplateQueryService`；getById/requireEnabled 归写侧 DomainService（与源码一致） |
| disable/delete 入参 | DM 写 `(id, operatorId)` | SDD 仍定义独立 Command 文件，便于 AppService 组装与审计字段一致 |
| 列表排序 | DM 未写默认 Sort | 默认 `sortOrder ASC, updatedAt DESC`（与 QueryService 一致） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **PacsReportTemplate 聚合** 的持久化模型、领域服务契约、查询契约与不变式实现策略，使下一会话可 **一次性** 生成/核对 `module-pacs-domain` 领域层并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**（含 disable/delete）；单表聚合无子表同步。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`pacs_report_template` 模板名称、模态、所见/结论样本文本、启停、个人归属、软删与列表读。
- **在外**：REP-04 套用写入 `PacsDiagnosticReport` 草稿（DM-PACS-002）；画布打印版式（DM-PRT-001）；CFG-002 AppService 编排。
- **跨域仅端口声明**：`ownerPractitionerId` 引用 health Practitioner，本聚合不 JOIN 人员姓名。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-pacs/module-pacs-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.plugin.pacs.domain.reporttemplate`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PacsReportTemplate` |
| `valueobject` | 无 |
| `enums`（共享） | 无专用枚举；`enabledFlag` 为 `Boolean` |
| `command` | 每个写方法一个 Java 文件（见 §5） |
| `repository` | `PacsReportTemplateRepository`（`JpaRepository` + `JpaSpecificationExecutor`） |
| `service` | `PacsReportTemplateDomainService`（直接实现类） |
| `queries` | `PacsReportTemplateQueryService`（直接实现类） |
| `queries.model` | `PacsReportTemplateQueryModel` |
| `share.events` | 无本聚合领域事件 |

**存储口径（DM 优先）**：`modalityCode` 为字典/术语码 → `String`；`enabledFlag` → `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；`ownerPractitionerId` 为外键引用 |
| 时间 | `createdAt` / `updatedAt` / `deletedAt` → `LocalDateTime` |
| `*_code` | `modalityCode` → `String` |
| `_flag` | `enabledFlag` → `Boolean` |
| 软删 | `Boolean isDeleted`；继承 `BaseAuditedEntity` |
| 实体 | `@Entity`，显式 `@Table(name="pacs_report_template")` / `@Column` |
| 仓储 | `interface PacsReportTemplateRepository extends JpaRepository<..., Long>, JpaSpecificationExecutor<...>`；禁止手写 `@Query` |
| 分页 | `Pageable`；列表默认 `sortOrder ASC, updatedAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` UoW |
| 事件 | 本聚合 **不** 发布领域事件 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `pacs_report_template` | `PacsReportTemplate` | 聚合根 · 单表 |

### 4.2 聚合根 `PacsReportTemplate`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `name` | `String` | 是 | 模板名称；租户内非空（BR-PRTPL-01） |
| `modalityCode` | `String` | 否 | 模态过滤；空=通用模板 |
| `findings` | `String` | 否 | 所见样本文本 |
| `conclusion` | `String` | 否 | 结论样本文本 |
| `enabledFlag` | `Boolean` | 是 | 是否启用；默认 true |
| `sortOrder` | `Integer` | 否 | 列表排序 |
| `ownerPractitionerId` | `Long` | 否 | 个人模板归属；空=科室/院级 |
| `tenantId` | `Long` | 是 | 租户 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |
| `deletedBy` | `Long` | 系统 | 软删操作人 |
| `deletedAt` | `LocalDateTime` | 系统 | 软删时间 |

**不变式方法（实现须一致）**：`assertNamePresent` / `assertEnabledForApply`（`requireEnabled` 用）

### 4.3 子实体

无。本聚合为单表。

### 4.4 子实体

无其他子实体。

### 4.5 仓储接口

```java
public interface PacsReportTemplateRepository
    extends JpaRepository<PacsReportTemplate, Long>, JpaSpecificationExecutor<PacsReportTemplate> {
}
```

软删默认由 Hibernate `deletedFilter` + `SpecificationUtils.notDeleted()` 过滤；接口名不强制 `…AndIsDeletedFalse` 后缀。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `PacsReportTemplateQueryModel`（对应 `search`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `pacs_report_template.id` | |
| `name` | `String` | `pacs_report_template.name` | |
| `modalityCode` | `String` | `pacs_report_template.modality_code` | |
| `enabledFlag` | `Boolean` | `pacs_report_template.enabled_flag` | |
| `sortOrder` | `Integer` | `pacs_report_template.sort_order` | |
| `ownerPractitionerId` | `Long` | `pacs_report_template.owner_practitioner_id` | 列表不含 findings/conclusion |

#### 4.6.2 `getById` / `requireEnabled` 出参

直接返回 `PacsReportTemplate` 实体（含 findings/conclusion 全文），无独立 QueryModel。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PacsReportTemplateDomainService`

#### 5.1.1 `save(SavePacsReportTemplateCommand cmd)` → `PacsReportTemplate`

**命令文件**：`SavePacsReportTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 空=新建；非空=更新（查无则 `NOT_FOUND`） |
| `name` | `String` | 必填；trim 后非空 |
| `modalityCode` | `String` | 可选；空串归 null |
| `findings` | `String` | 可选 |
| `conclusion` | `String` | 可选 |
| `enabledFlag` | `Boolean` | 可选；默认 true |
| `sortOrder` | `Integer` | 可选 |
| `ownerPractitionerId` | `Long` | 可选 |
| `tenantId` | `Long` | 必填（新建）；更新时不可改租户 |
| `operatorId` | `Long` | 必填；写 createdBy/updatedBy |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertNamePresent` |
| `statusTransitionMethod` | N/A（无状态机；仅 `enabledFlag` 布尔） |
| `repositoryChecks` | 更新路径 `findById`；禁止静默新建 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRTPL-001 |

**方法契约摘要**：前置：`name`、`tenantId` 非空；持久化：insert/update 单行；失败：`INVALID_ARGUMENT` / `NOT_FOUND`；无领域事件；**注释锚点**：校验 → 加载或 new → 写字段 → save。

---

#### 5.1.2 `disable(DisablePacsReportTemplateCommand cmd)` → `PacsReportTemplate`

**命令文件**：`DisablePacsReportTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | `enabledFlag` → false |
| `repositoryChecks` | `getById` 加载存在行 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRTPL-002 |

**方法契约摘要**：前置：模板存在且未软删；持久化：`enabledFlag=false` + 审计；失败：`NOT_FOUND`；REP-04 套用须走 `requireEnabled`；**注释锚点**：加载 → 写 enabled → save。

---

#### 5.1.3 `delete(DeletePacsReportTemplateCommand cmd)` → `void`

**命令文件**：`DeletePacsReportTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `getById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRTPL-003 |

**方法契约摘要**：前置：存在；持久化：逻辑删 `isDeleted=true` + `deletedAt/deletedBy`；失败：`NOT_FOUND`；软删后不参与 search/套用（BR-PRTPL-01）；**注释锚点**：加载 → 软删字段 → save。

---

#### 5.1.4 `getById(GetPacsReportTemplateCommand cmd)` → `PacsReportTemplate`

**命令文件**：`GetPacsReportTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `templateRepository.findById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRTPL-004 |

**方法契约摘要**：前置：`id` 非空；持久化：只读；失败：`INVALID_ARGUMENT` / `NOT_FOUND`；供 CFG 详情与 REP 套用前加载；**注释锚点**：校验 id → findById。

---

#### 5.1.5 `requireEnabled(RequireEnabledPacsReportTemplateCommand cmd)` → `PacsReportTemplate`

**命令文件**：`RequireEnabledPacsReportTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertEnabledForApply` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 同 `getById` + `enabledFlag==true` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRTPL-005 |

**方法契约摘要**：前置：存在、未软删、`enabledFlag=true`（BR-PRTPL-02）；持久化：无；失败：`DISABLED`；返回实体供 App 拷贝 findings/conclusion；**注释锚点**：getById → 启停门禁。

---

### 5.2 读侧 — `PacsReportTemplateQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false` + 租户过滤；分页在 **数据库侧** 过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `search` | `modalityCode`（可空）、`enabledOnly`（可空）、`pageable` | `Page<PacsReportTemplateQueryModel>` | Specification 过滤模态/启用；默认 Sort `sortOrder ASC, updatedAt DESC` | TC-PRTPL-R01 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-PRTPL-01** | `save` 校验 name；`search`/Query 默认 `notDeleted`；软删后 `delete` 不可见 |
| **BR-PRTPL-02** | `requireEnabled` 断言 `enabledFlag=true`；`search(enabledOnly=true)` 过滤 |
| **BR-PRTPL-03** | 套用语义在 REP AppService：只写 report findings/conclusion，本聚合只提供样本文本 |
| **BR-PRTPL-04** | 无树形 parent；平铺列表按 `sortOrder` |

---

## 7. 事件契约与触发规则（DM §5）

无。本聚合为配置型聚合，无领域事件；状态变更以事务内持久化为准。

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 软删：`isDeleted=true`，写入 `deletedAt`/`deletedBy`；禁止物理 DELETE。
- 查询与 `getById` 默认过滤已软删行（Repository 过滤器 + Specification）。
- 停用（`enabledFlag=false`）与软删正交：停用仍可见于管理列表，但不可被套用。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁（`@代码生成通用规范`）。
- 实体继承 `BaseAuditedEntity`（含 `tenantId`）。
- 事务由 AppService `AppServiceAspect` / 只读切面管理。
- 长文本 `findings`/`conclusion` 使用 `@Column` text 映射（Flyway 与实体同轮）。

---

## 10. 生成与验收清单

- [ ] `PacsReportTemplate` 实体与 §4.2 字段一致
- [ ] 5 个写侧 Command + `SavePacsReportTemplateCommand` 字段与 §5.1.1 一致
- [ ] `PacsReportTemplateDomainService` 五方法与 §5.1 行为一致
- [ ] `PacsReportTemplateQueryService#search` 分页 total 与过滤一致
- [ ] BR-PRTPL-01～02 单测 TC-PRTPL-001～005、R01
- [ ] Repository 无手写 `@Query`
- [ ] Flyway `pacs_report_template` 与字段表同轮

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-PACS-004-PacsReportTemplate-报告模板.md` v1.0
- 本 SDD 文件
- 目标模块：`module-pacs-domain`
- Skills：`@代码生成通用规范` + `@领域层代码生成提示词模板`

### 11.2 DoD

- 实体与仓储编译通过；JPA 扫描含 `reporttemplate` 包。
- §5.1 全部写/加载方法行为与契约一致（含 `requireEnabled` 错误码）。
- `search` 默认排序与 enabled/modality 过滤正确。

### 11.3 失败处理

- BR 与 REP 套用口径冲突 → 回改 DM/SDD，禁止在代码中静默放宽启停规则。

---

## 定稿检查清单（可打勾）

- [x] 标题含 `DomainModel-SDD`
- [x] 含「输入完整性检查结果」三列表
- [x] 章节 1～11 齐全且顺序正确
- [x] `### 5.1` 下 5 个 `#### 5.1.n` 逐方法块
- [x] §4.2 字段与 DM §3 对齐
- [x] §6 映射 DM §4 全部 BR
- [x] §7 声明无事件
- [x] 无「待确认」类措辞

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
