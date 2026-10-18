# DM-DIA-001 BodyStructure 身体部位 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-DIA-001-BodyStructure-身体部位.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：DIA  
> **聚合**：BodyStructure（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-DIA-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.dia.bodystructure` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime`（审计字段） |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.diagnosis.bodystructure` |
| 父节点字段 | §3.1 未显式列出 | SDD 补充 `parentId` 支持树形结构（隐含于 DM 层级编码语义） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 `BodyStructure` 聚合的持久化模型、写读契约、事件与不变式映射，支撑一次性生成领域层代码。
- 将实现前澄清前置：一方法一 Command，写侧字段全集显式列出。
- 统一树形目录维护口径：编码、状态、检索码与软删语义一致。

### 1.2 边界

- **在内**：身体部位主数据生命周期、状态流转、目录检索字段维护。
- **在外**：诊断/检查/手术等业务对身体部位的消费编排。
- **跨域仅端口声明**：仅引用采番服务与共享事件发布，不在本聚合内落下游快照。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.dia.bodystructure`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `BodyStructure` |
| `valueobject` | 无（本聚合值对象由共享枚举承载） |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums.PublicationStatus` |
| `command` | `AddOrEditBodyStructureCommand`、`DeleteBodyStructureCommand`、`ActiveBodyStructureCommand`、`InactiveBodyStructureCommand` |
| `repository` | `BodyStructureRepository extends JpaRepository<BodyStructure, Long>` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.diagnosis.bodystructure` |

**存储口径（DM 优先）**：`status_enum` 为枚举编码列，类型 `Integer`；编码字段 `bus_no`、检索字段 `py_str/wb_str` 为 `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id` |
| 时间 | 本聚合无业务时间字段，审计时间沿用基类 `LocalDateTime` |
| `*_enum` | `statusEnum: Integer` |
| `*_code` | 本聚合无 |
| `_flag` | 本聚合无 |
| 软删 | `Boolean isDeleted`（基类） |
| 实体 | `@Entity` + `@Table(name="dia_body_structure")` + 显式 `@Column` |
| 值对象 | 无 |
| 仓储 | 仅 `JpaRepository` 接口 |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械加 `@Transactional` |
| 事件 | `new XxxEvent(...)` + `DomainEventPublisher.publish`，beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `dia_body_structure` | `BodyStructure` | 身体部位主数据单表聚合 |

### 4.2 聚合根 `BodyStructure`（字段级契约）

对应表 `dia_body_structure`，主键 `id`。

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 身体部位标识，雪花 ID |
| `busNo` | `String` | 是 | 层级业务编码，全局唯一，采番生成（BR-BDS-002） |
| `name` | `String` | 是 | 部位显示名称，非空（BR-BDS-001），变更时触发检索码重算 |
| `statusEnum` | `Integer` | 是 | 发布状态，映射 `PublicationStatus`：ACTIVE=2 / INACTIVE=3 |
| `pyStr` | `String` | 否 | 拼音检索码，由 `name` 自动生成，用于首字母检索 |
| `wbStr` | `String` | 否 | 五笔检索码，由 `name` 自动生成，用于五笔编码检索 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记，默认 false |

**不变式方法（示例命名，实现须一致）**：
- `assertNameNotBlank()`：名称非空校验（BR-BDS-001）
- `assertBusNoGenerated()`：编码由采番服务生成，禁止手工构造冲突编码（BR-BDS-002）
- `refreshSearchCodeFromName()`：名称变更时同步重算 `pyStr`/`wbStr`（BR-BDS-003）
- `assertSelectableWhenActive()`：仅 ACTIVE 状态可用于新业务引用（BR-BDS-004）

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `Optional<BodyStructure> findByIdAndIsDeletedFalse(Long id)`
- `List<BodyStructure> findByIdInAndIsDeletedFalse(Collection<Long> ids)`
- `boolean existsByBusNoAndIsDeletedFalse(String busNo)`
- `Page<BodyStructure> findByNameContainingOrPyStrContainingOrWbStrContaining(...)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `BodyStructureTreeQueryModel`（对应 `getBodyStructureTree`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `dia_body_structure.id` | 节点标识 |
| `busNo` | `String` | `dia_body_structure.bus_no` | 业务编码 |
| `name` | `String` | `dia_body_structure.name` | 节点名称 |
| `statusEnum` | `Integer` | `dia_body_structure.status_enum` | 状态 |
| `pyStr` | `String` | `dia_body_structure.py_str` | 拼音码（供 IF-DOR-18 前端模糊） |
| `wbStr` | `String` | `dia_body_structure.wb_str` | 五笔码（供 IF-DOR-18 前端模糊） |
| `children` | `List<BodyStructureTreeQueryModel>` | 组装字段 | 子节点 |

**应用层树形下拉（IF-DOR-18）**：`BodyStructureOptionAssembler#toActiveOptionTree` 在树模型上映射为 `OptionDto`（仅 ACTIVE，停用节点的启用子节点可上提；透出 **`pyStr`/`wbStr`**，库值空或误写成名称时按 `SearchCodeUtil` 现算）。

#### 4.6.2 `BodyStructureInfoQueryModel`（对应 `getBodyStructureInfo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `dia_body_structure.id` | 主键 |
| `busNo` | `String` | `dia_body_structure.bus_no` | 编码 |
| `name` | `String` | `dia_body_structure.name` | 名称 |
| `statusEnum` | `Integer` | `dia_body_structure.status_enum` | 状态 |
| `pyStr` | `String` | `dia_body_structure.py_str` | 拼音码 |
| `wbStr` | `String` | `dia_body_structure.wb_str` | 五笔码 |

#### 4.6.3 `BodyStructureListItemQueryModel`（对应 `getList`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `dia_body_structure.id` | 主键 |
| `busNo` | `String` | `dia_body_structure.bus_no` | 编码 |
| `name` | `String` | `dia_body_structure.name` | 显示名称 |
| `statusEnum` | `Integer` | `dia_body_structure.status_enum` | 状态 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `BodyStructureDomainService`

#### 5.1.1 `addOrEditBodyStructure(AddOrEditBodyStructureCommand cmd)` → `Long`

**命令文件**：`AddOrEditBodyStructureCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 空为新增，非空为编辑 |
| `name` | `String` | 部位名称 |
| `parentId` | `Long` | 父节点（可空） |
| `statusEnum` | `Integer` | 目标状态（可选） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertNameNotBlank`、`refreshSearchCodeFromName` |
| `statusTransitionMethod` | `changeStatus` |
| `repositoryChecks` | `existsByBusNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `BodyStructureCreatedEvent/BodyStructureModifiedEvent` |
| `unitTestCaseId` | `TC-DIA-BDS-001` |

**方法契约摘要**：前置条件为名称非空与编码规则合法；持久化为新增或更新主表单行；失败语义为编码冲突/状态非法拒绝；事件触发为创建或修改事件 beforeCommit 发布。

---

#### 5.1.2 `deleteBodyStructure(DeleteBodyStructureCommand cmd)` → `void`

**命令文件**：`DeleteBodyStructureCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `ids` | `List<Long>` | 批量删除主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDeletable` |
| `statusTransitionMethod` | `markDeleted` |
| `repositoryChecks` | `findByIdInAndIsDeletedFalse` |
| `eventPublishAnchor` | `BodyStructureDeletedEvent` |
| `unitTestCaseId` | `TC-DIA-BDS-002` |

**方法契约摘要**：前置条件为入参非空且记录存在；持久化影响为批量逻辑删除；失败语义为空集合或缺失 ID 拒绝；事件触发为删除事件 beforeCommit 发布。

---

#### 5.1.3 `activeBodyStructure(ActiveBodyStructureCommand cmd)` → `void`

**命令文件**：`ActiveBodyStructureCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSelectableWhenActive` |
| `statusTransitionMethod` | `activate` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `BodyStructureActivatedEvent` |
| `unitTestCaseId` | `TC-DIA-BDS-003` |

**方法契约摘要**：前置条件为记录存在且未删除；持久化影响为更新 `statusEnum=ACTIVE`；失败语义为不存在或非法迁移拒绝；事件触发为启用事件 beforeCommit 发布。

---

#### 5.1.4 `inactiveBodyStructure(InactiveBodyStructureCommand cmd)` → `void`

**命令文件**：`InactiveBodyStructureCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSelectableWhenActive` |
| `statusTransitionMethod` | `inactivate` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `BodyStructureInactivatedEvent` |
| `unitTestCaseId` | `TC-DIA-BDS-004` |

**方法契约摘要**：前置条件为记录存在；持久化影响为 `ACTIVE -> INACTIVE`；失败语义为非法迁移拒绝；事件触发为停用事件 beforeCommit 发布。

### 5.2 读侧 — `BodyStructureQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getBodyStructureTree` | `keyword,statusEnum,pageable` | `BodyStructureTreeQueryModel` | `findAllByOrderByBusNoAsc` + 跳过 `isDeleted` + 按 `parentId` 组树 | `TC-DIA-BDS-Q-001` |
| `getBodyStructureInfo` | `id` | `BodyStructureInfoQueryModel` | 主键查询+映射 DTO | `TC-DIA-BDS-Q-002` |
| `getList` | `keyword,statusEnum` | `BodyStructureListItemQueryModel` | 名称/拼音/五笔检索 | `TC-DIA-BDS-Q-003` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-BDS-001 | 在 `addOrEditBodyStructure` 调用 `assertNameNotBlank`，并绑定 `TC-DIA-BDS-001` |
| BR-BDS-002 | 新增时仅接受采番口径编码，禁止手工冲突编码 |
| BR-BDS-003 | 名称变更后强制重算 `pyStr/wbStr` |
| BR-BDS-004 | 停用后查询默认不返回可选项，读侧下拉仅取可用状态 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `BodyStructureCreatedEvent` | `com.zxplat.domain.share.events.diagnosis.bodystructure` | `id,busNo,name,statusEnum,tenantId` | `addOrEditBodyStructure`（新增） |
| `BodyStructureModifiedEvent` | `com.zxplat.domain.share.events.diagnosis.bodystructure` | `id,changedFields` | `addOrEditBodyStructure`（编辑） |
| `BodyStructureActivatedEvent` | `com.zxplat.domain.share.events.diagnosis.bodystructure` | `id,statusEnum` | `activeBodyStructure` |
| `BodyStructureInactivatedEvent` | `com.zxplat.domain.share.events.diagnosis.bodystructure` | `id,statusEnum` | `inactiveBodyStructure` |
| `BodyStructureDeletedEvent` | `com.zxplat.domain.share.events.diagnosis.bodystructure` | `ids` | `deleteBodyStructure` |

**发布时机**：全部 beforeCommit；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 删除语义为逻辑删除，不执行物理 DELETE。
- 默认QueryService仅可见 `isDeleted=false` 记录。
- 停用不影响历史引用，仅影响新增选择。

---

## 9. 框架优先适配规则

- 软删与审计字段继承统一基类。
- 领域服务/查询服务采用直接实现类。
- Repository 仅接口，无自定义实现类。
- 事件在领域服务中直接构造并发布，不引入工厂薄封装。

---

## 10. 生成与验收清单

- [ ] `BodyStructure` 实体字段与 DM §3.1 全量对齐
- [ ] 4 个写命令文件独立建模
- [ ] 写侧 4 个方法全部实现并绑定事件
- [ ] 读侧 3 个查询方法与 QueryModel 对齐
- [ ] BR-BDS-001~004 均有测试用例绑定

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-DIA-001`
- 本 SDD 与 DM 差异为 0（仅工程落点扩展）
- 共享枚举与事件包路径确认可用

### 11.2 DoD

- 写侧/读侧方法命名与签名与 §5 一致。
- `status_enum`、`py_str`、`wb_str` 映射一致。
- 事件发布点全部位于持久化成功后 beforeCommit。
- 单元测试覆盖写侧状态迁移、删除、查询过滤。

### 11.3 失败处理

- 任一写方法缺少字段全集或绑定项，停止成码并回补 SDD。
- 发现 DM 字段遗漏或事件缺失，回会话 A 修订后再继续。
- 若软删/状态语义与本稿冲突，以本稿回收实现并补测试。

---

## 定稿检查清单（可打勾）

- [x] 主章节与小节结构与金骨架一致
- [x] §5 写侧逐方法完整，含绑定项与方法契约摘要
- [x] §4 字段覆盖 DM §3.1
- [x] §7 事件覆盖 DM §5 全量事件
- [x] 文末结束语与评分节齐备

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
| 2026-06-24 | **前端展示**：管理端树形表格；**IF-DOR-18** `bodyStructureOptions` 改 `getBodyStructureTree` + `BodyStructureOptionAssembler` 启用态树；`getBodyStructureTree` 读侧排除软删行。 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
