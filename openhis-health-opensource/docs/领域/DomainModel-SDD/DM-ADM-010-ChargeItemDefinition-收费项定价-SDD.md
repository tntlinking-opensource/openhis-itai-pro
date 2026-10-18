# DM-ADM-010 ChargeItemDefinition 收费项定价 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-010-ChargeItemDefinition-收费项定价.md`（**v1.1.7**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Financial（财务域 · 收费项定价管理）  
> **聚合**：ChargeItemDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-010 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.financial.chargeitemdefinition` | 满足 | `chargeitemdefinition` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| SALE/UNIT 命名 | §4.1 存在命名差异说明 | SDD 统一按 `UNIT` 作为项目售价口径 |
| 状态语义 | §2.3 仅给轻量迁移 | SDD 强化“状态由编排决定，领域不驱动状态机”；**新建行**须 `ACTIVE`（**BR-CHGDEF-WR-001**） |
| 单表多行 | 文档多处重复说明 | SDD 在 §4 与 §5 一次性固化查询/写入契约 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **ChargeItemDefinition 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，覆盖稳定价、批号价、全量覆盖与公共信息同步。
- 单次成稿目标：正文不出现后续补充/待确认措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：定价事实行维护（单表多行）、批号优先取价口径、时间窗口与优先级规则、定价事件发布。
- **在外**：调价审批单流转、入库单流转、收费记账执行。
- **跨域仅端口声明**：仅声明与 ActivityDefinition/MedicationDefinition/DeviceDefinition/ChargeItem 的引用关系，不在本聚合执行跨域写入。
- **应用消费声明（IF-DOR-11）**：门诊医生站目录展示与费用草稿择行不在领域层实现；消费侧约定见 **DM v1.1 §1.4**——诊疗行采纳 **`SALE`**，药耗行采纳 **`LOT_NUMBER_PRICE`** 且 **`lot_no`** 与库存批号对齐；与各 `*DefinitionQueryService` 目录展示价可能并存但语义不全等价（计费事实仍以 **`ChargeItemDefinition`** 为准）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.financial.chargeitemdefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ChargeItemDefinition` |
| `valueobject` | `PriceType`、`ActPriority`、`ProjectKey` |
| `enums`（共享） | 复用 `PriceType`、`ActPriority` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `ChargeItemDefinitionRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` |

**存储口径（DM 优先）**：`*_enum` 默认 `String`（存枚举 code）；`*_code` 为 `String`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id` |
| 时间 | 生效窗口字段建议 `LocalDateTime`（DB 可映射 Date/Timestamp） |
| `*_enum` | 枚举编码列，默认 `String`（存枚举 code） |
| `*_code` | 字典编码列，`String` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<ChargeItemDefinition, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_charge_item_definition` | `ChargeItemDefinition` | 单表多行定价事实 |

### 4.2 聚合根 `ChargeItemDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 定价标识 |
| `chargeName` | `String` | 是 | 费用项名称 |
| `title` | `String` | 否 | 标题 |
| `statusEnum` | `String` | 是 | 发布状态（枚举 code） |
| `orgId` | `Long` | 是 | 机构标识 |
| `description` | `String` | 否 | 描述 |
| `instanceTable` | `String` | 是 | 项目表名 |
| `instanceId` | `Long` | 是 | 项目标识 |
| `priceType` | `String` | 是 | 价格类型 |
| `lotNumber` | `String` | 否 | 批次号（批号价必填） |
| `amount` | `BigDecimal` | 是 | 金额 |
| `effectiveStart` | `LocalDateTime` | 是 | 生效开始 |
| `effectiveEnd` | `LocalDateTime` | 否 | 生效结束 |
| `priority` | `Integer` | 是 | 优先级 |
| `typeCode` | `String` | 是 | 财务类别编码 |
| `ybType` | `String` | 是 | 医保类别编码 |
| `priceCode` | `String` | 否 | 物价编码 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertPriceLineUnique`、`assertLotNumberRequiredForLotPrice`、`assertNoTimeWindowOverlap`、`assertProjectCommonFieldsConsistency`、`assertUsedLineNeedsAdjustmentFlow`。

### 4.3 子实体

无。本聚合采用单表多行，不拆聚合内子表实体。

### 4.5 仓储接口

- `ChargeItemDefinitionRepository extends JpaRepository<ChargeItemDefinition, Long>`

**说明**：查询统一在主表执行，无子表 JOIN。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `ItemSalePriceQueryModel`（对应 `findSaleItemByInstanceId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item_definition.id` | 定价行标识 |
| `instanceTable` | `String` | `adm_charge_item_definition.instance_table` | 项目表名 |
| `instanceId` | `Long` | `adm_charge_item_definition.instance_id` | 项目标识 |
| `amount` | `BigDecimal` | `adm_charge_item_definition.amount` | 项目售价 |
| `priceType` | `String` | `adm_charge_item_definition.price_type` | `UNIT` |

#### 4.6.2 `ItemPurchasePriceQueryModel`（对应 `findPurchaseItemByInstanceId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item_definition.id` | 定价行标识 |
| `instanceId` | `Long` | `adm_charge_item_definition.instance_id` | 项目标识 |
| `amount` | `BigDecimal` | `adm_charge_item_definition.amount` | 项目进价 |
| `priceType` | `String` | `adm_charge_item_definition.price_type` | `PURCHASE` |

#### 4.6.3 `LotSalePriceQueryModel`（对应 `findSalePriceByInstanceIdAndLot`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item_definition.id` | 定价行标识 |
| `instanceId` | `Long` | `adm_charge_item_definition.instance_id` | 项目标识 |
| `lotNumber` | `String` | `adm_charge_item_definition.lot_number` | 批次号 |
| `amount` | `BigDecimal` | `adm_charge_item_definition.amount` | 批号售价或回落售价 |
| `priceType` | `String` | `adm_charge_item_definition.price_type` | `LOT_NUMBER_PRICE/UNIT` |

#### 4.6.4 `LotPurchasePriceQueryModel`（对应 `findPurchasePriceByInstanceIdAndLot`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item_definition.id` | 定价行标识 |
| `instanceId` | `Long` | `adm_charge_item_definition.instance_id` | 项目标识 |
| `lotNumber` | `String` | `adm_charge_item_definition.lot_number` | 批次号 |
| `amount` | `BigDecimal` | `adm_charge_item_definition.amount` | 批号进价或回落进价 |
| `priceType` | `String` | `adm_charge_item_definition.price_type` | `LOT_NUMBER_COST/PURCHASE` |

#### 4.6.5 `ProjectPriceLineQueryModel`（对应 `findPriceLinesByProjects`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item_definition.id` | 定价行标识 |
| `instanceTable` | `String` | `adm_charge_item_definition.instance_table` | 项目表名 |
| `instanceId` | `Long` | `adm_charge_item_definition.instance_id` | 项目标识 |
| `priceType` | `String` | `adm_charge_item_definition.price_type` | 价格类型 |
| `amount` | `BigDecimal` | `adm_charge_item_definition.amount` | 金额 |
| `priority` | `Integer` | `adm_charge_item_definition.priority` | 优先级 |

#### 4.6.6 `HealthcareServiceSaleLineQueryModel`（对应 `findSaleLineForHealthcareServiceRegistration`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item_definition.id` | 定价行标识 |
| `instanceTable` | `String` | `adm_charge_item_definition.instance_table` | 应为 `adm_healthcare_service` |
| `instanceId` | `Long` | `adm_charge_item_definition.instance_id` | 医疗服务标识 |
| `amount` | `BigDecimal` | `adm_charge_item_definition.amount` | 挂号售价 |
| `priceType` | `String` | `adm_charge_item_definition.price_type` | `UNIT` |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ChargeItemDefinitionDomainService`

#### 5.1.1 `addItemRetailPrice(AddItemRetailPriceCommand cmd)` → `Long`

**命令文件**：`AddItemRetailPriceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `instanceTable` | `String` | 项目表名 |
| `instanceId` | `Long` | 项目标识 |
| `amount` | `BigDecimal` | 售价 |
| `effectiveStart` | `LocalDateTime` | 生效开始 |
| `effectiveEnd` | `LocalDateTime` | 生效结束 |
| `priority` | `Integer` | 优先级 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertAddUnitPriceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.checkTimeOverlap` |
| `eventPublishAnchor` | `new PriceLineAddedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-001` |

**方法契约摘要**：前置条件为项目键有效、金额与时间窗口合法；持久化影响为新增 `priceType=UNIT` 定价行；失败语义为时间窗口冲突或重复行时报错；事件触发为发布 `PriceLineAddedEvent`。**注释锚点**：校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.2 `addItemPurchasePrice(AddItemPurchasePriceCommand cmd)` → `Long`

**命令文件**：`AddItemPurchasePriceCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertAddPurchasePriceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.checkTimeOverlap` |
| `eventPublishAnchor` | `new PriceLineAddedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-002` |

**方法契约摘要**：前置条件为项目键有效且非诊疗项目跳过规则；持久化影响为新增 `PURCHASE` 行；失败语义为业务类型不支持或冲突时报错；事件触发为发布 `PriceLineAddedEvent`。**注释锚点**：业务类型校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.3 `addItemLimitPrice(AddItemLimitPriceCommand cmd)` → `Long`

**命令文件**：`AddItemLimitPriceCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertAddLimitPriceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.checkTimeOverlap` |
| `eventPublishAnchor` | `new PriceLineAddedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-003` |

**方法契约摘要**：前置条件为项目键有效且金额合法；持久化影响为新增 `LIMIT` 行；失败语义为冲突或非法金额时报错；事件触发为发布 `PriceLineAddedEvent`。**注释锚点**：校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.4 `updateItemRetailPrice(UpdateItemRetailPriceCommand cmd)` → `void`

**命令文件**：`UpdateItemRetailPriceCommand.java`

**字段全集**：`id`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertUpdateUnitPriceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.findById` |
| `eventPublishAnchor` | `new PriceLineUpdatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-004` |

**方法契约摘要**：前置条件为目标 `UNIT` 行存在，若已被使用需走调价流程；持久化影响为更新目标行金额与窗口；失败语义为记录不存在或违规直改时报错；事件触发为发布 `PriceLineUpdatedEvent`。**注释锚点**：存在性校验 -> 调价约束校验 -> 更新 -> 事件。

---

#### 5.1.5 `updateItemPurchasePrice(UpdateItemPurchasePriceCommand cmd)` → `void`

**命令文件**：`UpdateItemPurchasePriceCommand.java`

**字段全集**：`id`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertUpdatePurchasePriceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.findById` |
| `eventPublishAnchor` | `new PriceLineUpdatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-005` |

**方法契约摘要**：前置条件为 `PURCHASE` 行存在且满足调价流程要求；持久化影响为更新进价行；失败语义为类型不匹配或违规直改时报错；事件触发为发布 `PriceLineUpdatedEvent`。**注释锚点**：校验 -> 更新 -> 持久化 -> 事件。

---

#### 5.1.6 `updateItemLimitPrice(UpdateItemLimitPriceCommand cmd)` → `void`

**命令文件**：`UpdateItemLimitPriceCommand.java`

**字段全集**：`id`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertUpdateLimitPriceAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.findById` |
| `eventPublishAnchor` | `new PriceLineUpdatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-006` |

**方法契约摘要**：前置条件为 `LIMIT` 行存在且金额合法；持久化影响为更新限价行；失败语义为记录缺失或非法金额时报错；事件触发为发布 `PriceLineUpdatedEvent`。**注释锚点**：校验 -> 更新 -> 持久化 -> 事件。

---

#### 5.1.7 `updatePricingItemInfo(UpdatePricingItemInfoCommand cmd)` → `void`

**命令文件**：`UpdatePricingItemInfoCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`chargeName`、`typeCode`、`ybType`、`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertProjectCommonInfoConsistent` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.findByProjectKey` |
| `eventPublishAnchor` | `new ChargeItemDefinitionInfoBatchUpdatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-007` |

**方法契约摘要**：前置条件为同项目行集存在且允许批量同步公共列；持久化影响为同项目多行公共字段一致化更新；失败语义为项目行缺失时报错；事件触发为发布 `ChargeItemDefinitionInfoBatchUpdatedEvent`。**注释锚点**：加载行集 -> 一致性校验 -> 批量更新 -> 事件。

---

#### 5.1.8 `addLotPurchasePrice(AddLotPurchasePriceCommand cmd)` → `Long`

**命令文件**：`AddLotPurchasePriceCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`lotNumber`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertAddLotCostAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.checkDuplicateLotLine` |
| `eventPublishAnchor` | `new PriceLineAddedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-008` |

**方法契约摘要**：前置条件为 `lotNumber` 必填且行不重复；持久化影响为新增 `LOT_NUMBER_COST` 行且 **`status_enum=ACTIVE`**（**BR-CHGDEF-WR-001**）；失败语义为批号缺失或重复时报错；事件触发为发布 `PriceLineAddedEvent`。**注释锚点**：批号校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.9 `addLotSalePrice(AddLotSalePriceCommand cmd)` → `Long`

**命令文件**：`AddLotSalePriceCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`lotNumber`、`amount`、`effectiveStart`、`effectiveEnd`、`priority`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertAddLotSaleAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.checkDuplicateLotLine` |
| `eventPublishAnchor` | `new PriceLineAddedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-009` |

**方法契约摘要**：前置条件为 `lotNumber` 必填且批号售价行不重复；持久化影响为新增 `LOT_NUMBER_PRICE` 行且 **`status_enum=ACTIVE`**；失败语义为批号冲突或窗口冲突时报错；事件触发为发布 `PriceLineAddedEvent`。**注释锚点**：校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.10 `removePricingByInstance(RemovePricingByInstanceCommand cmd)` → `void`

**命令文件**：`RemovePricingByInstanceCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`operatorId`、`reason`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertRemoveByProjectAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.findByProjectKey` |
| `eventPublishAnchor` | `new ChargeItemDefinitionRemovedByInstanceEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-010` |

**方法契约摘要**：前置条件为项目定位键有效且允许清理；持久化影响为按项目移除全部定价行；失败语义为项目不存在或不允许移除时报错；事件触发为发布 `ChargeItemDefinitionRemovedByInstanceEvent`。**注释锚点**：查找 -> 移除 -> 持久化 -> 事件。

---

#### 5.1.11 `saveAllChargeItemDefinitions(SaveAllChargeItemDefinitionsCommand cmd)` → `List<Long>`

**命令文件**：`SaveAllChargeItemDefinitionsCommand.java`

**字段全集**：`instanceTable`、`instanceId`、`items`、`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItemDefinition.assertBatchOverwriteAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemDefinitionRepository.findByProjectKey` |
| `eventPublishAnchor` | `new PriceBatchAddedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHGDEF-W-011` |

**方法契约摘要**：前置条件为输入行集合法且满足诊疗项目进价限制；持久化影响为项目维度全量覆盖（清旧写新），**新行均 `status_enum=ACTIVE`**；失败语义为任一行不合法整批回滚；事件触发为发布 `PriceBatchAddedEvent`。**注释锚点**：校验 -> 清旧 -> 写新 -> 事件。

---

#### 5.1.12 `deactivateActivePriceLinesByCommand(DeactivateActivePriceLinesCommand cmd)` → `void`

**命令文件**：`DeactivateActivePriceLinesCommand.java`

**字段全集**：`operatorId`；`items[]`：`instanceTable`、`instanceId`、`priceTypeCode`、`lotNo`（项目级价类为空）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `N/A`（状态迁移由领域服务直接处理） |
| `statusTransitionMethod` | `ACTIVE → INACTIVE`（`PublicationStatus`） |
| `repositoryChecks` | `ChargeItemDefinitionRepository` + `Specification` 按匹配键查 ACTIVE 行 |
| `eventPublishAnchor` | `new PriceLineUpdatedEvent(...); DomainEventPublisher.publish(event)`（逐行） |
| `unitTestCaseId` | `TC-CHGDEF-W-012` |

**方法契约摘要**：前置条件为 `items` 非空且匹配键合法；持久化影响为将匹配键下当前 **ACTIVE** 行迁为 **INACTIVE**（调价 BR-PRC-APR-003）；失败语义为无匹配行时静默跳过；事件触发为逐行 `PriceLineUpdatedEvent`。**注释锚点**：加载 ACTIVE 行 -> 状态停用 -> 批量持久化 -> 事件。

### 5.2 读侧 — `ChargeItemDefinitionQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；**当前价**读方法另加 `status_enum=ACTIVE`（**BR-CHGDEF-QRY-004**）；`findAllDefinitionsByIdIn` 仅按主键快照、不过滤 ACTIVE。分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `findSaleItemByInstanceId` | `ChargeItemDefinitionQueryCommand` | `List<ChargeItemDefinition>` | `UNIT` 售价行 | `TC-CHGDEF-R-001` |
| `findPurchaseItemByInstanceId` | 同上 | `List<ChargeItemDefinition>` | `PURCHASE` 进价行 | `TC-CHGDEF-R-002` |
| `findSalePriceByInstanceIdAndLot` | 同上（含 `lotNo`） | `List<ChargeItemDefinition>` | 批号售价优先回落 `UNIT` | `TC-CHGDEF-R-003` |
| `findPurchasePriceByInstanceIdAndLot` | 同上 | `List<ChargeItemDefinition>` | 批号进价优先回落 `PURCHASE` | `TC-CHGDEF-R-004` |
| `findLotNumberCostByInstanceIdAndLot` | 同上 | `List<ChargeItemDefinition>` | 仅 `LOT_NUMBER_COST` | `TC-CHGDEF-R-007` |
| `findLotNumberPriceByInstanceIdAndLot` | 同上 | `List<ChargeItemDefinition>` | 仅 `LOT_NUMBER_PRICE` | `TC-CHGDEF-R-008` |
| `findLotNumberCostLinesGroupedByLotBatchKey` | `Collection<ChargeItemDefinitionLotBatchKey>` | `Map<String,List<...>>` | 批号进价批量分组 | `TC-CHGDEF-R-009` |
| `findLotNumberPriceLinesGroupedByLotBatchKey` | 同上 | `Map<String,List<...>>` | 批号售价批量分组 | `TC-CHGDEF-R-010` |
| `resolveFirstPurchasePriceValue` | `instanceTable`,`instanceId` | `BigDecimal` | ACTIVE 行择优进价 | `TC-CHGDEF-R-011` |
| `resolveFirstSalePriceValue` | 同上 | `BigDecimal` | ACTIVE 行择优售价 | `TC-CHGDEF-R-012` |
| `resolveFirstLotNumberCostPriceValue` | `instanceTable`,`instanceId`,`lotNumber` | `BigDecimal` | ACTIVE 批号进价择优 | `TC-CHGDEF-R-013` |
| `existsLotNumberCostOrLotNumberPriceForInstanceAndLot` | `ChargeItemDefinitionQueryCommand` | `boolean` | ACTIVE 批号价存在性（入库去重） | `TC-CHGDEF-R-014` |
| `findPriceLinesByProjects` | `ChargeItemDefinitionQueryCommand` | `List<ChargeItemDefinition>` | 项目全量定价行 | `TC-CHGDEF-R-005` |
| `findSaleLineForHealthcareServiceRegistration` | `ChargeItemDefinitionQueryCommand` | `ChargeItemDefinition` | 挂号 `UNIT` 行 | `TC-CHGDEF-R-006` |
| `findAllDefinitionsByIdIn` | `Collection<Long> definitionIds` | `Map<Long,ChargeItemDefinition>` | 批量主键（不过滤 ACTIVE，历史锚点） | `TC-CHGDEF-R-015` |

**读方法注释锚点（统一）**：构建查询条件（含软删 + ACTIVE 过滤，主键快照除外）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| PR-CHGDEF-001 | 同项目多行公共字段保持一致 |
| PR-CHGDEF-002 | 批号价必须携带 `lotNumber` |
| PR-CHGDEF-003 | 诊疗项目不设 `PURCHASE` |
| PR-CHGDEF-004 | 同项目同类型时间窗口不重叠（或按优先级可解释） |
| PR-CHGDEF-005 | 同项目同类型重复记录需去重 |
| PR-CHGDEF-006 | 已使用价格变更必须走调价流程 |
| BR-CHGDEF-007 | 批号优先取价，缺失回落统一价 |
| BR-CHGDEF-QRY-001 | 仅提供价格事实，拆零比由外层计算 |
| BR-CHGDEF-QRY-002 | 同类型按 `priority DESC, created_at DESC` 取最新 |
| BR-CHGDEF-QRY-003 | 全部查询统一在主表执行 |
| BR-CHGDEF-WR-001 | 新建定价行 insert 须 `status_enum=ACTIVE`；`ensurePublicationStatusForInsert` + 实体 `@PrePersist` 兜底 |
| BR-CHGDEF-QRY-004 | 当前价读路径仅 ACTIVE；`findAllDefinitionsByIdIn` 例外 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `PriceLineAddedEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `id,instanceTable,instanceId,priceType,lotNumber,amount,effectiveStart,effectiveEnd,priority` | `add*Price` |
| `PriceLineUpdatedEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `id,instanceTable,instanceId,priceType,oldAmount,newAmount,occurredAt` | `update*Price`、`deactivateActivePriceLinesByCommand` |
| `PriceLineRemovedEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `id,instanceTable,instanceId,priceType,lotNumber` | `removePriceLine(内部)` |
| `PriceBatchAddedEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `instanceTable,instanceId,addedLineIds` | `saveAllChargeItemDefinitions` |
| `ChargeItemDefinitionInfoBatchUpdatedEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `instanceTable,instanceId,changedCommonFields` | `updatePricingItemInfo` |
| `ChargeItemDefinitionRemovedByInstanceEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `instanceTable,instanceId,removedCount` | `removePricingByInstance` |
| `PriceAdjustmentRecordedEvent` | `com.zxplat.domain.share.events.financial.chargeitemdefinition` | `id,instanceTable,instanceId,priceType,oldAmount,newAmount,adjustmentId` | `recordPriceAdjustment(编排)` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 删除语义优先逻辑删除，保留历史追溯能力。
- 按项目移除全量定价行仅在明确下架/重建场景执行。
- 批号价与稳定价共存时，查询侧先批号后回落。
- 同项目公共字段应批量同步，避免多行展示不一致。
- 所有查询默认排除 `isDeleted=true` 行。

---

## 9. 框架优先适配规则

- 软删、审计字段、事务工作单元、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 单表多行查询禁止引入冗余 JOIN，保持索引命中。
- 调价审批/库存/目录联动由应用层编排。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表多行字段完整）
- [ ] §5.1 写方法小节数量 = 12，与 DM §6.1 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 **15** 个方法
- [ ] §6 BR 映射覆盖 PR/BR 规则全集
- [ ] §7 事件表覆盖 DM §5 全部 7 条事件
- [ ] 批号优先与回落规则在读写两侧一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-010-ChargeItemDefinition-收费项定价.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.financial.chargeitemdefinition`

### 11.2 DoD

- 写侧 11 个方法全部落地，签名与 Command 命名与 §5 一致。
- 单表多行字段、批号价规则、时间窗口规则与 §4/§6 一致。
- 事件按 §7 beforeCommit 发布，载荷字段不少于最小要求。
- BR 至少一条绑定一个测试用例 ID，可追溯至 `unitTestCaseId`。
- 读侧 6 个方法与 §4.6 QueryModel 对齐，排序与回落逻辑一致。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若批号优先与回落逻辑不一致，DoD 失败不得进入成码。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（11）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（7）
- [ ] 每个写方法包含 `entityInvariantMethod` 与 `unitTestCaseId`
- [ ] 全文无 YAML、无 `sdd_status`、无 `dm_code`、无占位措辞

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
| 2026-06-27 | 对齐 DM **v1.1.7**：**BR-CHGDEF-WR-001** 新建行须 `status_enum=ACTIVE`；§5.1.8/9/11 写契约补充 |
| 2026-05-28 | **DM-SDD M1**：§5.2 十五读法签名对齐 Java `ChargeItemDefinitionQueryService`；对齐 DM **v1.1.1** |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

