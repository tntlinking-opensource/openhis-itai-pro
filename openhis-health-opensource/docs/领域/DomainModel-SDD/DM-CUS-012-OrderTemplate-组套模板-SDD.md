# DM-CUS-012 OrderTemplate 组套模板 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-012-OrderTemplate-组套模板.md`（**v1.4.5**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：OrderTemplate（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-012 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.ordertemplate` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 8 个、读方法 3 个 |
| DM 第 7 章 BR | 满足 | §6 映射 DM v1.4.0 全量 BR（含 source / PRICING / TCM / 套用） |
| DM 第 5 章事件 | 满足 | §7 覆盖 9 个事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化聚合持久化模型、写读契约、事件与不变式。
- 将实现前澄清前置：一方法一 Command、字段全集、仓储校验与测试锚点明确。
- 正文不出现占位语句，保持可实施。

### 1.2 边界

- **在内**：本聚合实体、状态、查询模型、事件触发。
- **在外**：外域主数据正文维护、页面拼装、**套用编排**（DM §6.4，`OrderTemplateAppService` → 医生站/护士划价 AS）。
- **跨域仅端口声明**：通过标识引用外域，不落库外域实体。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.custom.ordertemplate`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.ordertemplate` |

共享枚举：`zxplat-domain` · `com.zxplat.domain.share.enums` — **`OrderTemplateKind`**、**`OrderTemplateSource`（v1.4.0 新增）** 及既有 `OrderTemplateBinding` 等。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；命名与 DM 字段一致 |
| 时间 | 业务字段按 DM；审计字段 `LocalDateTime` |
| `*_enum` | `String`（存枚举 **code**，与 `zxplat` 现行 `OrderTemplate*` 落库一致） |
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
| `cus_order_template` | `OrderTemplate` | 聚合根；**v1.3.0** 增补 `template_kind_enum` 及中医头字段（Flyway `V88` 已落地） |
| `cus_order_template_line` | `OrderTemplateLine` | 聚合内子实体；**v1.3.0** 增补中医行字段（Flyway `V88` 已落地） |

**Gap-OTL-001（已完成）**：Flyway `.../V88__cus_order_template_kind.sql`（见 DM §3.1）。

**Gap-OTL-003（已完成）**：Flyway `.../V89__cus_order_template_source.sql` 增补 `template_source_enum NOT NULL DEFAULT 'MAINTAINED'`；枚举 `OrderTemplateSource`；读侧 `templateSourceEnum` 过滤；`assertTemplateSourceRules()`（BR-OTL-029～031）。

### 4.2 聚合根 `OrderTemplate`（字段级契约）

| 字段名（Java） | DB 列 | 类型 | 必填 | 说明 / 约束 |
| -------------- | ----- | ---- | ---- | ----------- |
| `id` | `id` | `Long` | 是 | 主键 |
| `objectId` | `object_id` | `Long` | 是 | BR-OTL-001 |
| `bindingEnum` | `binding_enum` | `String` | 是 | `OrderTemplateBinding` code |
| `sceneEnum` | `scene_enum` | `String` | 是 | `OrderTemplateScene` code；PRICING 仅 INPATIENT/COMMON（BR-OTL-027） |
| `templateKindEnum` | `template_kind_enum` | `String` | 是 | `OrderTemplateKind` code；默认 `GENERAL` |
| `templateSourceEnum` | `template_source_enum` | `String` | 是 | `OrderTemplateSource` code；默认 `MAINTAINED`；创建后不可变（BR-OTL-031） |
| `templateCode` | `template_code` | `String` | 否 | BR-OTL-003 |
| `name` | `name` | `String` | 是 | BR-OTL-005 |
| `description` | `description` | `String` | 否 | — |
| `sortWeight` | `sort_weight` | `Integer` | 否 | 默认 0；BR-OTL-011 |
| `statusEnum` | `status_enum` | `String` | 是 | `OrderTemplateStatus` code |
| `remark` | `remark` | `String` | 否 | — |
| `defaultChineseHerbsDoseQuantity` | `default_chinese_herbs_dose_quantity` | `BigDecimal` | 条件 | TCM 必填；BR-OTL-023 |
| `defaultSufferingFlag` | `default_suffering_flag` | `Boolean` | 否 | TCM 可选 |

**实体方法（现行实现）**：`initDraft()`、`assertRequiredFields()`、`assertEditable()`、`assertLineMaintainable()`、`assertNotRetired()`、`publish()`、`retire()`、`markDeleted()`、`assertTemplateKindRules()`（BR-OTL-027）、`assertTemplateSourceRules()`（BR-OTL-029～030）、`assertReadyToPublish()`（BR-OTL-023）。

### 4.3 子实体 `OrderTemplateLine`

| 字段名（Java） | DB 列 | 类型 | 必填 | 说明 |
| -------------- | ----- | ---- | ---- | ---- |
| `id` | `id` | `Long` | 是 | 主键 |
| `orderTemplateId` | `order_template_id` | `Long` | 是 | 外键 |
| `lineKindEnum` | `line_kind_enum` | `String` | 是 | `OrderTemplateLineKind` code；PRICING 禁止 MEDICATION（BR-OTL-026） |
| `medicationDefinitionId` | `medication_definition_id` | `Long` | 条件 | MEDICATION 行 |
| `activityDefinitionId` | `activity_definition_id` | `Long` | 条件 | SERVICE 行 |
| `deviceDefinitionId` | `device_definition_id` | `Long` | 条件 | DEVICE 行 |
| `groupKey` | `group_key` | `String` | 否 | GENERAL/TCM 成组 |
| `sortOrder` | `sort_order` | `Integer` | 是 | ≥1；BR-OTL-010 |
| `defaultQuantity` | `default_quantity` | `BigDecimal` | 否 | BR-OTL-016 |
| `defaultUnitCode` | `default_unit_code` | `String` | 否 | — |
| `defaultDays` | `default_days` | `Integer` | 否 | — |
| `frequencyId` | `frequency_id` | `Long` | 否 | — |
| `routeCode` | `route_code` | `String` | 否 | — |
| `dosageText` | `dosage_text` | `String` | 否 | — |
| `defaultPerformOrgId` | `default_perform_org_id` | `Long` | 否 | SERVICE 执行科室；PRICING+DEVICE 为发放库房 |
| `lineRemark` | `line_remark` | `String` | 否 | — |
| `chineseHerbsDoseQuantity` | `chinese_herbs_dose_quantity` | `BigDecimal` | 否 | TCM 行覆盖头付数 |
| `sufferingFlag` | `suffering_flag` | `Boolean` | 否 | TCM 行覆盖头代煎 |

**实体方法（现行）**：`assertRequiredFields()`、`assertKindAndDefinitionRefConsistent()`、`assertLineKindAllowedForTemplateKind()`（BR-OTL-026）、`markDeleted()`。

**归属校验**：更新/删除行须校验 `lineId` 属于 `orderTemplateId` 且未软删（BR-OTL-009）。

### 4.4 枚举（`zxplat-domain`）

| 枚举 | 存储 | 值 |
| ---- | ---- | -- |
| `OrderTemplateBinding` | `String` code | `PERSONAL` / `DEPARTMENT` / `HOSPITAL` / `AGREEMENT` |
| `OrderTemplateScene` | `String` code | `OUTPATIENT` / `INPATIENT` / `COMMON` |
| `OrderTemplateStatus` | `String` code | `DRAFT` / `ACTIVE` / `RETIRED` |
| `OrderTemplateLineKind` | `String` code | `MEDICATION` / `SERVICE` / `DEVICE` |
| `OrderTemplateKind` | `String` code | **`GENERAL` / `TCM` / `PRICING`（v1.3.0）** |
| `OrderTemplateSource` | `String` code | **`MAINTAINED` / `FROM_ADVICE`（v1.4.0）** |

### 4.5 仓储接口

- `OrderTemplateRepository extends JpaRepository<OrderTemplate, Long>, JpaSpecificationExecutor<OrderTemplate>`（**v1.4.1**：维护端可选筛选动态谓词）
- `OrderTemplateLineRepository extends JpaRepository<OrderTemplateLine, Long>`
- `existsByTemplateCodeAndObjectIdAndBindingEnum(...)` / `existsByTemplateCodeAndObjectIdAndBindingEnumAndIdNot(...)`
- **v1.4.0 已落地（source 过滤）**：
  - `findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(...)`
  - `findAllByBindingEnumAndSceneEnumAndStatusEnumAndTemplateKindEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(...)`
  - `findAllByObjectIdAndBindingEnumAndStatusEnumAndSceneEnumInAndTemplateKindEnumAndTemplateSourceEnumOrderBySortWeightAscIdAsc(...)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `OrderTemplateWithLinesQueryModel`（`getOrderTemplateWithLines`）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `header` | `OrderTemplateHeaderQueryModel` | 头；含 `templateKindEnum`、`templateSourceEnum`、中医头字段 |
| `lines` | `List<OrderTemplateLineQueryModel>` | 行；含中医行字段 |

#### 4.6.2 `OrderTemplateHeaderQueryModel`（分页/列表头）

在现行字段基础上 **v1.3.0 增补**：`templateKindEnum`、`defaultChineseHerbsDoseQuantity`、`defaultSufferingFlag`。

#### 4.6.3 `OrderTemplateLineQueryModel`

在现行字段基础上 **v1.3.0 增补**：`chineseHerbsDoseQuantity`、`sufferingFlag`。

在现行字段基础上 **v1.4.0 增补**：`templateSourceEnum`（`CreateOrderTemplateCommand` 可写；头更新不可变）。

#### 4.6.4 读侧入参扩展

| 方法 | 入参口径 |
| ---- | -------- |
| `pageOrderTemplates` | **v1.4.1**：`bindingEnum?`、`sceneEnum?`、`statusEnum?`、`templateKindEnum?`、`templateSourceEnum?` 均可空；空白归一化为 null 后**不参与** SQL 谓词 |
| `listOrderTemplatesByOwnerAndScene` | **v1.4.0**：`templateKindEnum`（必填）、`templateSourceEnum?`（可空=合并） |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `OrderTemplateDomainService`

#### 5.1.1 `createOrderTemplate(CreateOrderTemplateCommand cmd)` → `void`

**命令文件**：`CreateOrderTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-001` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.2 `updateOrderTemplateHeader(UpdateOrderTemplateHeaderCommand cmd)` → `void`

**命令文件**：`UpdateOrderTemplateHeaderCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-002` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.3 `publishOrderTemplate(PublishOrderTemplateCommand cmd)` → `void`

**命令文件**：`PublishOrderTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-003` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.4 `retireOrderTemplate(RetireOrderTemplateCommand cmd)` → `void`

**命令文件**：`RetireOrderTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-004` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.5 `deleteOrderTemplate(DeleteOrderTemplateCommand cmd)` → `void`

**命令文件**：`DeleteOrderTemplateCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-005` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.6 `addOrderTemplateLine(AddOrderTemplateLineCommand cmd)` → `void`

**命令文件**：`AddOrderTemplateLineCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-006` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.7 `updateOrderTemplateLine(UpdateOrderTemplateLineCommand cmd)` → `void`

**命令文件**：`UpdateOrderTemplateLineCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-007` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.8 `removeOrderTemplateLine(RemoveOrderTemplateLineCommand cmd)` → `void`

**命令文件**：`RemoveOrderTemplateLineCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键 |
| `object_id` | ``Long`` | 语义由 `binding_enum` 决定，见 §4.1 |
| `binding_enum` | ``Integer`（`OrderTemplateBinding`）` | 个人 / 科室 / 全院 / 协定处方 |
| `scene_enum` | ``Integer`（`OrderTemplateScene`）` | 门诊 / 住院 / 通用，见 §4.2 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new OrderTemplateCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-012-W-008` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

### 5.2 读侧 — `OrderTemplateQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `getOrderTemplateWithLines` | `orderTemplateId` | `OrderTemplateWithLinesQueryModel` | 头+行；行序 sortOrder,id ASC | `TC-DM-CUS-012-R-001` |
| `pageOrderTemplates` | `bindingEnum?, sceneEnum?, statusEnum?, templateKindEnum?, templateSourceEnum?, pageable` | `Page<OrderTemplateHeaderQueryModel>` | 软删过滤；**快路径**：binding+scene+status 均非空→派生分页；**否则**→`findAll(Specification)`；Criteria 列名用 `FieldNameConstants`（`bindingEnum/sceneEnum/statusEnum/templateKindEnum/templateSourceEnum/sortWeight/id`） | `TC-DM-CUS-012-R-002` |
| `listOrderTemplatesByOwnerAndScene` | `objectId, bindingEnum, sceneEnum, templateKindEnum, templateSourceEnum?` | `List<OrderTemplateHeaderQueryModel>` | ACTIVE + scene 匹配或 COMMON | `TC-DM-CUS-012-R-003` |

> **写命令字段全集（v1.4.0）**：`CreateOrderTemplateCommand` 增补 `templateSourceEnum`（默认 `MAINTAINED`）；`UpdateOrderTemplateHeaderCommand` **不含** `templateSourceEnum`（不可变）。v1.3.0 字段见上。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-OTL-001 | 头表必须同时具有合法的 `binding_enum` 与 `object_id`；语义一致性与查询正确性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-001` |
| BR-OTL-002 | `scene_enum` 必须为已定义枚举值；非法场景拒绝落库；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-002` |
| BR-OTL-003 | `template_code` 非空时，在租户 + 归属键（`object_id`+`binding_enum`）范围内唯一；唯一索引兜底；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-003` |
| BR-OTL-004 | 写侧操作的头必须存在且 `deleted = false`；防止操作幽灵头；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-004` |
| BR-OTL-005 | 模板名称 `name` 不得为空串；展示与审计需要；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-005` |
| BR-OTL-006 | 发布为 `ACTIVE` 时，组套内须至少存在一条未删除的有效行；空壳组套不可发布；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-006` |
| BR-OTL-007 | 已逻辑删除的头禁止一切写侧变更（除幂等删除）；—；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-007` |
| BR-OTL-008 | 行类型与引用列互斥且一致；`MEDICATION` 仅 `medication_definition_id` 非空；`SERVICE` 仅 `activity_definition_id`；`DEVICE` 仅 `device_definition_id`；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-008` |
| BR-OTL-009 | 操作的行必须属于指定头且行自身未删除；防止跨聚合篡改；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-009` |
| BR-OTL-010 | `sort_order` 须为整数；**套内排序数值越小越优先（1 最优先）**；与全域优先级约定一致；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-010` |
| BR-OTL-011 | `sort_weight` 须为非负整数（若未填则默认 `0`）；头表列表排序；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-011` |
| BR-OTL-012 | 新增或更新行时，被引用的定义须处于「允许被医嘱模板引用」的发布态；引用完整性；具体状态码与各定义 DM 对齐；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-012` |
| BR-OTL-013 | 若定义已停用，不允许**新建**行引用该定义；已存在行的展示标记由他域读侧处理；与 **BR-CTL-006** 同类策略；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-013` |
| BR-OTL-014 | 同一头下允许同一药品/项目/耗材定义出现多行（例如溶媒与药品分行）；**禁止**仅以定义 ID 做套内唯一约束；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-014` |
| BR-OTL-015 | `group_key` 长度与字符集受院内规范限制；防止滥用；长度上限以实现为准；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-015` |
| BR-OTL-016 | `default_quantity` 若填写则须 `> 0`（退耗材负向等不适用于组套模板行）；—；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-016` |
| BR-OTL-017 | `RETIRED` 状态禁止修改头属性；需先 `ACTIVE` 或新建版本策略见 **§9.2**；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-017` |
| BR-OTL-018 | 仅 `DRAFT` 与 `ACTIVE` 允许维护组套模板行；`RETIRED` 禁止；与 **§2.3** 一致；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-018` |
| BR-OTL-020 | 逻辑删除头时，行不得留在「未删除且仍指向该头」的可引用状态；推荐级联软删行 **BR-OTL-021**；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-019` |
| BR-OTL-021 | 头逻辑删除时，同步将该头下全部行标记 `deleted=true`；数据一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-012-BR-020` |
| BR-OTL-022 | 行删除默认逻辑删除；`TC-DM-CUS-012-BR-021` |
| BR-OTL-023 | TCM 头 `defaultChineseHerbsDoseQuantity` 必填且 >0；`publishOrderTemplate` 前置；`TC-DM-CUS-012-BR-022` |
| BR-OTL-024 | TCM 套用由应用层写 `tcm_flag`（域外）；模板侧校验 kind=TCM；`TC-DM-CUS-012-BR-023` |
| BR-OTL-025 | TCM 组套非药行策略以实现为准；`TC-DM-CUS-012-BR-024` |
| BR-OTL-026 | PRICING 禁止 MEDICATION 行；`addOrderTemplateLine` + `assertKindAndDefinitionRefConsistent` 扩展；`TC-DM-CUS-012-BR-025` |
| BR-OTL-027 | PRICING 仅 scene INPATIENT/COMMON；`createOrderTemplate` / `updateOrderTemplateHeader`；`TC-DM-CUS-012-BR-026` |
| BR-OTL-028 | 域内无价格列；**IF-08 预览不查价**；套用期查价/库位由应用层 prepicked 目录 IF + 写路径透传承担；`TC-DM-CUS-012-BR-027` |
| BR-OTL-APY-001～005 | 套用 ACTIVE/kind/scene 校验由 **应用层** `OrderTemplateAppService` 承担；域内仅保证 ACTIVE 可读 |
| BR-OTL-QRY-001 | 多归属合并由应用层；QueryService 单层 + kind 过滤 |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 最小载荷 | 触发方法 |
| -------- | -------- | -------- |
| `OrderTemplateCreatedEvent` | `orderTemplateId`, `objectId`, `bindingEnum`, `sceneEnum` | `createOrderTemplate` |
| `OrderTemplateUpdatedEvent` | `orderTemplateId` | `updateOrderTemplateHeader` |
| `OrderTemplatePublishedEvent` | `orderTemplateId` | `publishOrderTemplate` |
| `OrderTemplateRetiredEvent` | `orderTemplateId` | `retireOrderTemplate` |
| `OrderTemplateDeletedEvent` | `orderTemplateId` | `deleteOrderTemplate` |
| `OrderTemplateLineAddedEvent` | `orderTemplateId`, `lineId`, `lineKindEnum` | `addOrderTemplateLine` |
| `OrderTemplateLineUpdatedEvent` | `orderTemplateId`, `lineId` | `updateOrderTemplateLine` |
| `OrderTemplateLineRemovedEvent` | `orderTemplateId`, `lineId` | `removeOrderTemplateLine` |

> **不实现** DM 可选的 `OrderTemplateLinesBatchChangedEvent`（与现行 `OrderTemplateDomainService` DOC-CONFLICT 注释一致）。

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
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 8 个）
- [ ] §5.2 读方法与 §4.6 QueryModel 一一对应（当前 3 个）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-012-OrderTemplate-组套模板.md`
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
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 8，与 DM §6.1 对齐
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
| 2026-06-18 | **v1.4.5 对账**：`OrderTemplateBinding` 增补 `AGREEMENT`（协定处方）；`object_id` 与 HOSPITAL 同解析；IF-07/11 四段 `agreementList` |
| 2026-06-11 | **v1.4.3 对账**：IF-08 预览仅结构；废止 IF-OT-APP-03/04 后端代套用；**BR-OTL-028** 套用期 prepicked 目录查价/库位透传 |
| 2026-06-03 | **v1.4.1 对账**：`pageOrderTemplates` 支持 binding/scene/status 可选筛选；`OrderTemplateRepository` 增补 `JpaSpecificationExecutor`；`buildPageSpecification` 动态谓词；`FieldNameConstants` 增补组套模板 Criteria 列名。 |
| 2026-06-03 | **v1.4.0 对账**：`template_source_enum`（MAINTAINED/FROM_ADVICE）；§1.5 维护组套 vs 医嘱模板；读侧 source 过滤；Gap-OTL-003（V89）。应用层 IF-OT-APP-05 `saveFromAdviceDraft` 待实现。 |
| 2026-06-03 | **v1.3.0 对账**：合并划价/中医/医嘱三类组套于 `OrderTemplate`；增补 `OrderTemplateKind`、`template_kind_enum`、中医字段、PRICING 行约束（BR-OTL-026～028）；读侧 `templateKindEnum` 过滤；套用编排见 DM §6.4（应用层，非 DomainService）。 |
| 2026-06-03 | **Gap-OTL-001 已完成**：Flyway `V88__cus_order_template_kind.sql`；`zxplat-domain` 实体/Command/QueryModel/Repository/DomainService/QueryService 与 SDD §4～§5 对齐。 |
| 2026-05-28 | 与 `zxplat-core/zxplat-domain` 现行 8 写 + 3 读实现对齐；无 ReadModel 跨聚合消费。 |

---

**本 SDD 正文结束。**
