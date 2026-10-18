# DM-CUS-005 ServiceDeviceTemplate 诊疗耗材绑定 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-005-ServiceDeviceTemplate-诊疗耗材绑定.md`（**v1.0.7**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：ServiceDeviceTemplate（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-005 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.servicedevicetemplate` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 5 个、读方法 6 个 |
| DM 第 7 章 BR | 满足 | §6 映射 12 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 5 个事件 |

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

### 2.2 包结构（`com.zxplat.domain.custom.servicedevicetemplate`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.servicedevicetemplate` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；命名与 DM 字段一致 |
| 时间 | 审计字段 `LocalDateTime`（`BaseAuditedEntity`） |
| `*_enum`（本聚合 `status_enum`） | `String`；存 `PublicationStatus.getCode()`（如 `ACTIVE`/`INACTIVE`/`DISCARDED`） |
| `*_code`（`type_code`/`range_code`/`unit_code`） | `String`；存对应枚举或术语 `getCode()` |
| 软删 | `Boolean isDeleted` 默认过滤 |
| 实体 | `@Entity` + `@Table` + `@Column` |
| 仓储 | 仅接口，无默认实现类 |
| 分页 | `Pageable`；消费列表 `id ASC`，配置分页 `id DESC` |
| 事件 | `new XxxEvent` + `publishBeforeCommit`（save 之后） |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_service_device_template` | `ServiceDeviceTemplate` | Flyway V37；与 DM §3.1 一致 |

### 4.2 聚合根 `ServiceDeviceTemplate`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 是 | 主键，雪花 ID |
| `rangeCode` | `String` | 是 | `OUTPATIENT` / `INPATIENT` / `HOSPITAL`（历史）；见 `ServiceDeviceTemplateRangeCode` |
| `typeCode` | `String` | 是 | `USAGE` / `ACTIVITY`（`ServiceDeviceTemplateTypeCode`） |
| `itemTable` | `String` | 是 | 来源物理表名，见 DM §4.4 |
| `itemId` | `String` | 是 | 来源项键：`USAGE` 为 **`method_code`**；`ACTIVITY` 为活动定义主键字符串 |
| `devActTable` | `String` | 是 | `wkf_activity_definition` 或 `adm_device_definition` |
| `devActId` | `Long` | 是 | 目标主键 |
| `quantity` | `BigDecimal` | 是 | 正数 |
| `unitCode` | `String` | 是 | 单位术语 code |
| `statusEnum` | `String` | 是 | `PublicationStatus.getCode()` |
| `isDeleted` | `Boolean` | 是 | 逻辑删除标记（继承审计基类） |

**实体不变式方法（已实现）**：`assertRequiredFields`、`assertActivatable` / `assertDeactivatable`、`activate` / `deactivate`、`markDeleted`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `ServiceDeviceTemplateRepository extends JpaRepository<ServiceDeviceTemplate, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- 其余查询签名按 §5.2 与 §6 不变式派生。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ServiceDeviceTemplateQueryModel`（读侧统一出参）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cus_service_device_template.id` | 主键 |
| `statusEnum` | `String` | `status_enum` | `ACTIVE` / `INACTIVE` / `DISCARDED` |
| `rangeCode` | `String` | `range_code` | 如 `OUTPATIENT` / `INPATIENT` / `HOSPITAL`（历史） |
| `typeCode` | `String` | `type_code` | `USAGE` / `ACTIVITY` |
| `itemTable` | `String` | `item_table` | 来源表名 |
| `itemId` | `String` | `item_id` | 来源项键（用法 method_code / 诊疗主键字符串） |
| `devActTable` | `String` | `dev_act_table` | 目标表名 |
| `devActId` | `Long` | `dev_act_id` | 目标 id |
| `quantity` | `BigDecimal` | `quantity` | 绑定数量 |
| `unitCode` | `String` | `unit_code` | 单位 code |

> 展示名（来源项名、目标项名、单位文案等）不在 QueryModel 内 join，由应用层 enrich（DM `BR-SDT-QRY-004`）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ServiceDeviceTemplateDomainService`

#### 5.1.1 `createBinding(CreateBindingCommand cmd)` → `Long`

**命令文件**：`CreateBindingCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `rangeCode` | `String` | 绑定范围；须 `OUTPATIENT` 或 `INPATIENT`（`HOSPITAL` 仅历史兼容，禁止新建/改存） |
| `typeCode` | `String` | `USAGE` / `ACTIVITY` |
| `itemTable` | `String` | 来源表名 |
| `itemId` | `String` | 来源项 id |
| `devActTable` | `String` | 目标表名 |
| `devActId` | `Long` | 目标 id |
| `quantity` | `BigDecimal` | 数量 |
| `unitCode` | `String` | 单位 code |
| `statusEnum` | `String` | 初始状态，空则领域默认 `ACTIVE` |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields` |
| `repositoryChecks` | 插入前 BR 校验 |
| `eventPublishAnchor` | `ServiceDeviceTemplateCreatedEvent` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-005-W-001` |

---

#### 5.1.2 `editBinding(EditBindingCommand cmd)` → `void`

**命令文件**：`EditBindingCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `bindingId` | `Long` | 绑定主键 |
| `rangeCode` / `typeCode` / `itemTable` / `itemId` | `String` | 可选覆盖 |
| `devActTable` / `devActId` | `String` / `Long` | 可选覆盖 |
| `quantity` | `BigDecimal` | 可选覆盖 |
| `unitCode` / `statusEnum` | `String` | 可选覆盖 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `eventPublishAnchor` | `ServiceDeviceTemplateEditedEvent` |
| `unitTestCaseId` | `TC-DM-CUS-005-W-002` |

---

#### 5.1.3 `deleteBinding(DeleteBindingCommand cmd)` → `void`

**命令文件**：`DeleteBindingCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `bindingId` | `Long` | 绑定主键 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted`（`is_deleted=true`，`statusEnum=DISCARDED`） |
| `eventPublishAnchor` | `ServiceDeviceTemplateDeletedEvent` |
| `unitTestCaseId` | `TC-DM-CUS-005-W-003` |

---

#### 5.1.4 `activateBinding(ActivateBindingCommand cmd)` → `void`

**命令文件**：`ActivateBindingCommand.java`（`bindingId`、`operatorId`）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `activate`（`INACTIVE → ACTIVE`） |
| `eventPublishAnchor` | `ServiceDeviceTemplateActivatedEvent` |
| `unitTestCaseId` | `TC-DM-CUS-005-W-004` |

---

#### 5.1.5 `deactivateBinding(DeactivateBindingCommand cmd)` → `void`

**命令文件**：`DeactivateBindingCommand.java`（`bindingId`、`operatorId`）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `deactivate`（`ACTIVE → INACTIVE`） |
| `eventPublishAnchor` | `ServiceDeviceTemplateRetiredEvent` |
| `unitTestCaseId` | `TC-DM-CUS-005-W-005` |

### 5.2 读侧 — `ServiceDeviceTemplateQueryService`

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参要点 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | -------- | ---- | -------- | ---------------- |
| `listBindingsByRangeTypeAndSource` | `rangeCode`,`typeCode`,`itemTable`,`itemId` | `List<ServiceDeviceTemplateQueryModel>` | 仅 `statusEnum=ACTIVE`，`id ASC` | `TC-DM-CUS-005-R-001` |
| `pageBindingsBySource` | 来源四维 + `Pageable` | `Page<…>` | 含停用行，`id DESC` | `TC-DM-CUS-005-R-002` |
| `pageBindingsBySourceAndStatus` | 来源四维 + `statusEnum` + `Pageable` | `Page<…>` | 配置页状态筛选 | `TC-DM-CUS-005-R-003` |
| `pageBindingsByRangeAndType` | `rangeCode`,`typeCode` + `Pageable` | `Page<…>` | 无来源 id 浏览 | `TC-DM-CUS-005-R-004` |
| `pageBindingsByRangeAndTypeAndStatus` | 范围+类型+状态 + `Pageable` | `Page<…>` | 无来源 id + 状态筛选 | `TC-DM-CUS-005-R-005` |
| `findById` | `bindingId` | `ServiceDeviceTemplateQueryModel` | 不存在抛 `SERVICE_DEVICE_TEMPLATE_NOT_FOUND` | `TC-DM-CUS-005-R-006` |

### 5.3 关联应用层契约（`zxplat/health-application` · WH-BND001）

> 不属于领域模块正文，但与 DM **§9.4** 及 `servicedevicebinding` 前端验收一致。

| 落点 | 契约要点 |
| ---- | -------- |
| `ServiceDeviceBindingAppService#init` | 返回 `ServiceDeviceBindingInitDto`：`usageSourceOptions`、`activitySourceOptions`、`targetTableOptions`、`unitOptions` 等（左侧来源项）；可仍带 `deviceDefOptions` 作兼容，**WH-BND001 绑定目标选品不再消费该列表**。 |
| `ServiceDeviceBindingAppService#listDeviceDefOptions` | 兼容接口：入参 `ListDeviceDefOptionsRequest.keyword`；出参 `List<OptionDto>`。**不再**作为 WH-BND001 绑定目标选品主路径。 |
| `DeviceCatalogAppService#pageDeviceCatalog` / `ServiceCatalogAppService#pageServiceCatalog` | WH-BND001 绑定目标 SelectTable 分页真源；仅启用态由前端过滤；id 以字符串传递。 |
| `ServiceDeviceBindingAppService#unitOptionsByTarget` | 按目标表+目标 id 解析合法单位与默认单位（**BR-SDT-010**）。 |
| 前端 `servicedevicebinding/index.vue` | 绑定目标列 `SelectTable`（列定义对齐合管配置）；**KeepAlive** `onActivated` 重调 `init` 刷新左侧来源项；选中目标后调 `unitOptionsByTarget`。 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| `BR-SDT-001` | `createBinding` / `editBinding` 提交前须具备：绑定类型、来源表名、来源项标识、目标表名、目标项标识、数量、单位、发布状态；必填完整性约束；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-005-BR-001` |
| `BR-SDT-002` | 来源项语义须与绑定类型一致（用法对应用法表，诊疗对应诊疗定义表）；语义一致性约束；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-005-BR-002` |
| `BR-SDT-003` | 消费方读路径固定 `statusEnum=ACTIVE`（`PublicationStatus.ACTIVE.getCode()`） |
| `BR-SDT-004` | 不做来源+目标+类型强唯一；允许多行并存 |
| `BR-SDT-006` | 消费方排除 `INACTIVE`；配置分页可含停用行 |
| `BR-SDT-007` | `deleteBinding` → `markDeleted`：软删 + `DISCARDED` |
| `BR-SDT-008` | `quantity > 0`（`assertRequiredFields`） |
| `BR-SDT-009` | 目标引用有效（应用层/领域 Gate + 消费方 BatchLoader） |
| `BR-SDT-010` | 单位须在目标合法单位集合内（WH-BND001 保存前 `unitOptionsByTarget`） |
| `BR-SDT-011` | 保存须显式指定 `OUTPATIENT` 或 `INPATIENT`；禁止新建/改存为 `HOSPITAL`；领域层对未传范围码仍兜底 `HOSPITAL` 仅兼容旧调用 |
| `BR-SDT-012` | 写操作前 `findById` 且未逻辑删除 |
| `BR-SDT-013` | 仓储派生查询默认排除 `isDeleted=true` |
| `BR-SDT-014` | WH-BND001 绑定目标（诊疗/耗材）走目录分页 SelectTable 实时检索启用态档案；禁止 OptionDto 全量下拉作选品主路径 |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ServiceDeviceTemplateCreatedEvent` | `com.zxplat.domain.share.events.custom.servicedevicetemplate` | `aggregateId`、`operationType`、`occurredAt` | `createBinding` |
| `ServiceDeviceTemplateEditedEvent` | `com.zxplat.domain.share.events.custom.servicedevicetemplate` | `aggregateId`、`operationType`、`occurredAt` | `editBinding` |
| `ServiceDeviceTemplateActivatedEvent` | `com.zxplat.domain.share.events.custom.servicedevicetemplate` | `aggregateId`、`operationType`、`occurredAt` | `activateBinding` |
| `ServiceDeviceTemplateRetiredEvent` | `com.zxplat.domain.share.events.custom.servicedevicetemplate` | `aggregateId`、`operationType`、`occurredAt` | `deactivateBinding` |
| `ServiceDeviceTemplateDeletedEvent` | `com.zxplat.domain.share.events.custom.servicedevicetemplate` | `aggregateId`、`operationType`、`occurredAt` | `deleteBinding` |

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
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 5 个）
- [ ] §5.2 读方法 6 个与 `ServiceDeviceTemplateQueryModel` 对齐
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-005-ServiceDeviceTemplate-诊疗耗材绑定.md`
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
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 5，与 DM §6.1 对齐
- [x] 每个写方法含命令文件、字段全集、绑定项、方法契约摘要
- [x] 读侧统一 `ServiceDeviceTemplateQueryModel`（§4.6.1）
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
| 2026-10-06 | **WH-BND001 绑定目标 SelectTable**：§5.3 / **BR-SDT-014** 对齐合管目录分页选品；对齐 DM **v1.0.7** |
| 2026-06-24 | **WH-BND001 耗材下拉**：§5.3 应用层契约；**BR-SDT-014**（`listDeviceDefOptions` + 前端 KeepAlive 刷新）；对齐 DM **v1.0.5** |
| 2026-06-05 | **v1.0.3 对账**：与 DM v1.0.3 一致——移除 `SLOT` 与 `assertTargetAllowedForType`/BR-SDT-005；绑定类型仅 `USAGE`/`ACTIVITY`；Flyway V96 逻辑删存量 SLOT 行 |
| 2026-06-05 | **v1.0.2 对账**：与 DM v1.0.2 及 `zxplat-core/zxplat-domain` 现行实现一致——String code（`USAGE`/`ACTIVITY`/`SLOT`、`HOSPITAL`、`ACTIVE`/`INACTIVE`/`DISCARDED`）；统一 `ServiceDeviceTemplateQueryModel`；读侧 6 方法；修正 `RetiredEvent`→`deactivateBinding` |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费 |

---

**本 SDD 正文结束。**
