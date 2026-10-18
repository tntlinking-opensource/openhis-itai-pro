# DM-ADM-008 DeviceDefinition 器材定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-008-DeviceDefinition-器材定义.md`（**v1.13**，本 SDD 与 DM 当期锁定版本对齐；含病案结算类型、是否收费耗材、五类计量单位贯通、`unit_code` 术语、耗材目录应用契约）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 器材目录主数据）  
> **聚合**：DeviceDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-008 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.devicedefinition` | 满足 | `devicedefinition` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 主路径状态 | §2.3 同时描述 `DRAFT/UNKNOWN` 与主路径 | SDD 保留完整枚举，同时将业务主路径明确为 `ACTIVE/RETIRED` |
| 标记字段类型 | §4.2 提到应用层 `Whether` 枚举 | SDD 在字段表统一落库类型为 `Boolean` |
| 导入/导出能力 | §6.2 含模板下载与导出方法 | SDD 读侧按查询/输出模型拆分并保留方法白名单 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **DeviceDefinition 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，确保目录维护与批量操作可直接实施。
- 单次成稿目标：正文不出现后续补充/待确认类措辞，缺口在文档阶段闭环。

### 1.2 边界

- **在内**：器材目录主数据维护、启停状态、医保回写、导入落库、事件发布。
- **在外**：耗材请求执行、发放扣减、库存变动、医保网关交互、收费定价本体维护。
- **跨域仅端口声明**：仅声明与 Organization/Location/Supplier/ChargeItemDefinition 的引用与编排边界。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.devicedefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DeviceDefinition` |
| `valueobject` | 无（本聚合当前无独立值对象类） |
| `enums`（共享） | 复用 `PublicationStatus` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `DeviceDefinitionRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.devicedefinition` |

**存储口径（DM 优先）**：本聚合 `status_enum` 落库为 `String`（存枚举 code）；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id` |
| 时间 | 审计时间遵循 `LocalDateTime`，业务主表无独立时间列 |
| `status_enum` | 枚举编码列，`String`（存 `PublicationStatus.code`） |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<DeviceDefinition, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_device_definition` | `DeviceDefinition` | 器材定义聚合根单表 |

### 4.2 聚合根 `DeviceDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 器材定义标识 |
| `busNo` | `String` | 是 | 器材业务编码（唯一） |
| `name` | `String` | 是 | 器材名称 |
| `pyStr` | `String` | 否 | 拼音码 |
| `wbStr` | `String` | 否 | 五笔码 |
| `categoryCode` | `String` | 否 | 器材分类编码 |
| `typeCode` | `String` | 是（应用维护/导入） | 器材种类编码（`device_type_code`；无明确种类用 `other`） |
| `purchaseUnitCode` | `String` | 否 | 采购单位（映射列 `purchase_unit_code`） |
| `totalVolume` | `String` | 否 | 包装规格（映射列 `total_volume`，与药品口径一致） |
| `itemMinQuantity` | `BigDecimal` | 否 | 最小库存警戒量 |
| `itemMaxQuantity` | `BigDecimal` | 否 | 最大库存警戒量 |
| `splitUnitCode` | `String` | 否 | 拆零单位（映射列 `split_unit_code`） |
| `packageUnitCode` | `String` | 否 | 包装单位（映射列 `package_unit_code`） |
| `defEncounterUnitCode` | `String` | 否 | 默认门诊/就诊单位（列 `def_encounter_unit_code`） |
| `defInhospitalUnitCode` | `String` | 否 | 默认住院单位（列 `def_inhospital_unit_code`） |
| `stockUnitCode` | `String` | 否 | 库存单位 |
| `doseUnitCode` | `String` | 否 | 剂量单位 |
| `doseUnitRatio` | `BigDecimal` | 否 | 剂量→拆零换算比 |
| `purchaseUnitRatio` | `BigDecimal` | 否 | 采购→拆零换算比 |
| `stockUnitRatio` | `BigDecimal` | 否 | 库存→拆零换算比 |
| `packageUnitRatio` | `BigDecimal` | 否 | 包装→拆零换算比 |
| `orgId` | `Long` | 否 | 所属科室标识 |
| `locationId` | `Long` | 否 | 所在位置标识 |
| `modelNumber` | `String` | 否 | 产品型号 |
| `hvcmFlag` | `Boolean` | 否 | 高值器材标志 |
| `chargeableFlag` | `Boolean` | 否 | 是否收费耗材（与界面/导入列「收费耗材」同向；导入空白默认 true） |
| `approvalNumber` | `String` | 否 | 批准文号 |
| `ybFlag` | `Boolean` | 否 | 医保标记 |
| `ybNo` | `String` | 否 | 医保编码 |
| `ybOrgNo` | `String` | 否 | 医药机构目录编码 |
| `ybMatchFlag` | `Boolean` | 否 | 医保对码标记 |
| `chrgitmLv` | `Integer` | 否 | 医保等级 |
| `mrSettlementTypeCode` | `String` | 否 | 病案结算类型（术语 `mr_settlement_type_code`） |
| `statusEnum` | `String` | 是 | 发布状态（存 `PublicationStatus.code`） |
| `manufacturerId` | `Long` | 否 | 生产厂家标识 |
| `manufacturerText` | `String` | 否 | 生产厂家名称 |
| `supplyId` | `Long` | 否 | 供应商标识 |
| `merchandiseName` | `String` | 否 | 商品名称（独立列 `merchandise_name`） |
| `description` | `String` | 否 | 器材说明 |
| `jurisdiction` | `String` | 否 | 适用范围 |
| `version` | `String` | 否 | 器材版本 |
| `substanceText` | `String` | 否 | 主要成分 |
| `allergenFlag` | `Boolean` | 否 | 过敏标记 |
| `rxFlag` | `Boolean` | 否 | 处方标志 |
| `activeFlag` | `Boolean` | 是 | 启用标记（兼容列） |
| `ybMappingCode` | `String` | 否 | 医保对码标识（兼容列） |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertBusNoUnique`、`refreshSearchCodeOnNameChanged`、`assertStatusTransitionLegal`、`assertEditabilityByBusinessCode`、`assertCatalogSyncConsistency`.

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `DeviceDefinitionRepository extends JpaRepository<DeviceDefinition, Long>`

**说明**：软删由框架统一过滤时，不强制在接口名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `DeviceManageInitQueryModel`（对应 `getDeviceManageInit`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `statusOptions` | `List<Option>` | 字典投影 | 状态字典 |
| `categoryOptions` | `List<Option>` | 字典投影 | 分类字典 |
| `typeOptions` | `List<Option>` | 字典投影 | 种类字典 |
| `orgOptions` | `List<Option>` | 组织投影 | 科室选项 |
| `locationOptions` | `List<Option>` | 位置投影 | 位置选项 |
| `mrSettlementTypeCodeOptions` | `List<Map<String, Object>>` | 术语服务 | 病案结算类型下拉（`mr_settlement_type_code`） |

#### 4.6.2 `DeviceDefinitionPageQueryModel`（对应 `pageDeviceDefinition`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device_definition.id` | 器材定义标识 |
| `busNo` | `String` | `adm_device_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_device_definition.name` | 器材名称 |
| `categoryCode` | `String` | `adm_device_definition.category_code` | 分类编码 |
| `typeCode` | `String` | `adm_device_definition.type_code` | 种类编码 |
| `statusEnum` | `String` | `adm_device_definition.status_enum` | 状态 |

#### 4.6.3 `DeviceDefinitionDetailQueryModel`（对应 `getDeviceDefinition`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device_definition.id` | 器材定义标识 |
| `busNo` | `String` | `adm_device_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_device_definition.name` | 器材名称 |
| `statusEnum` | `String` | `adm_device_definition.status_enum` | 状态 |
| `ybNo` | `String` | `adm_device_definition.yb_no` | 医保编码 |
| `description` | `String` | `adm_device_definition.description` | 器材说明 |
| `editabilityCode` | `String` | 应用层规则计算 | 编辑业务码（**AS-BAS-CAT-001**） |
| `editabilityMessage` | `String` | 应用层规则计算 | 编辑说明 |

#### 4.6.4 `DeviceDefinitionSearchQueryModel`（对应 `searchByKeyWord`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device_definition.id` | 器材定义标识 |
| `name` | `String` | `adm_device_definition.name` | 器材名称 |
| `busNo` | `String` | `adm_device_definition.bus_no` | 编码 |
| `pyStr` | `String` | `adm_device_definition.py_str` | 拼音码 |
| `wbStr` | `String` | `adm_device_definition.wb_str` | 五笔码 |

#### 4.6.5 `DeviceDefinitionLightQueryModel`（对应 `findBySearchKey`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device_definition.id` | 器材定义标识 |
| `name` | `String` | `adm_device_definition.name` | 器材名称 |
| `busNo` | `String` | `adm_device_definition.bus_no` | 业务编码 |

#### 4.6.6 `DeviceDefinitionEntityBatchQueryModel`（对应 `getAllDeviceDefinitions`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device_definition.id` | 器材定义标识 |
| `statusEnum` | `String` | `adm_device_definition.status_enum` | 状态 |
| `name` | `String` | `adm_device_definition.name` | 名称 |

#### 4.6.7 `DeviceDefinitionEditabilityQueryModel`（对应 `getDeviceDefinitionEditability`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `deviceDefId` | `Long` | 输入参数 | 目录标识 |
| `editabilityCode` | `String` | 规则计算 | 可编辑业务码 |
| `editabilityMessage` | `String` | 规则计算 | 可编辑说明 |

#### 4.6.8 `DeviceDefinitionImportTemplateQueryModel`（对应 `downloadDeviceDefinitionCatalogTemplate`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `templateName` | `String` | 模板资源 | 模板文件名 |
| `columns` | `List<String>` | 模板资源 | 模板列定义 |

#### 4.6.9 `DeviceDefinitionExportQueryModel`（对应 `exportDeviceDefinitionCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device_definition.id` | 器材定义标识 |
| `busNo` | `String` | `adm_device_definition.bus_no` | 业务编码 |
| `name` | `String` | `adm_device_definition.name` | 器材名称 |
| `statusEnum` | `String` | `adm_device_definition.status_enum` | 状态 |
| `ybNo` | `String` | `adm_device_definition.yb_no` | 医保编码 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DeviceDefinitionDomainService`

#### 5.1.1 `createDeviceDefinition(CreateDeviceDefinitionCommand cmd)` → `Long`

**命令文件**：`CreateDeviceDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `busNo` | `String` | 业务编码 |
| `name` | `String` | 器材名称 |
| `categoryCode` | `String` | 分类编码 |
| `typeCode` | `String` | 种类编码 |
| `unitCode` | `String` | 采购单位（命令项字段名；应用请求 **`purchaseUnitCode`** 直映本字段 → 列 `purchase_unit_code`） |
| `totalVolume` | `String` | 包装规格（映射实体 `totalVolume` / 列 `total_volume`） |
| `minUnitCode` | `String` | 拆零单位（命令项字段名；应用请求 **`splitUnitCode`** 直映本字段 → 列 `split_unit_code`） |
| `packageUnitCode` | `String` | 包装单位（列 `package_unit_code`） |
| `stockUnitCode` | `String` | 库存单位 |
| `doseUnitCode` | `String` | 剂量单位 |
| `doseUnitRatio` | `BigDecimal` | 剂量→拆零换算比 |
| `purchaseUnitRatio` | `BigDecimal` | 采购→拆零换算比 |
| `stockUnitRatio` | `BigDecimal` | 库存→拆零换算比 |
| `packageUnitRatio` | `BigDecimal` | 包装→拆零换算比 |
| `merchandiseName` | `String` | 商品名称（独立列） |
| `orgId` | `Long` | 所属科室 |
| `locationId` | `Long` | 所在位置 |
| `mrSettlementTypeCode` | `String` | 病案结算类型 |
| `chargeableFlag` | `Integer` | 是否收费耗材（Whether 语义码，落库 `Boolean`；与界面/导入「收费耗材」同向） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `DeviceDefinition.assertCreatable` |
| `statusTransitionMethod` | `DeviceDefinition.initAsActive` |
| `repositoryChecks` | `DeviceDefinitionRepository.existsByBusNo` |
| `eventPublishAnchor` | `new DeviceDefinitionCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-DEVDEF-W-001` |

**方法契约摘要**：前置条件为 `busNo` 唯一且关键主数据完整；持久化影响为新增主档并初始化 ACTIVE；失败语义为编码冲突或必填缺失时报错；事件触发为发布 `DeviceDefinitionCreatedEvent`。**注释锚点**：校验 -> 初始化 -> 持久化 -> 事件。

---

#### 5.1.2 `updateDeviceDefinition(UpdateDeviceDefinitionCommand cmd)` → `void`

**命令文件**：`UpdateDeviceDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 器材定义标识 |
| `name` | `String` | 器材名称 |
| `merchandiseName` | `String` | 商品名称（独立列） |
| `categoryCode` | `String` | 分类编码 |
| `typeCode` | `String` | 种类编码 |
| `unitCode` | `String` | 采购单位（命令项字段名；应用请求 **`purchaseUnitCode`** 直映本字段 → 列 `purchase_unit_code`） |
| `totalVolume` | `String` | 包装规格（映射实体 `totalVolume` / 列 `total_volume`） |
| `minUnitCode` | `String` | 拆零单位（命令项字段名；应用请求 **`splitUnitCode`** 直映本字段 → 列 `split_unit_code`） |
| `packageUnitCode` | `String` | 包装单位（列 `package_unit_code`） |
| `stockUnitCode` | `String` | 库存单位 |
| `doseUnitCode` | `String` | 剂量单位 |
| `doseUnitRatio` | `BigDecimal` | 剂量→拆零换算比 |
| `purchaseUnitRatio` | `BigDecimal` | 采购→拆零换算比 |
| `stockUnitRatio` | `BigDecimal` | 库存→拆零换算比 |
| `packageUnitRatio` | `BigDecimal` | 包装→拆零换算比 |
| `ybNo` | `String` | 医保编码 |
| `description` | `String` | 器材说明 |
| `mrSettlementTypeCode` | `String` | 病案结算类型 |
| `chargeableFlag` | `Integer` | 是否收费耗材（Whether 语义码，落库 `Boolean`；与界面/导入「收费耗材」同向） |
| `editabilityCode` | `String` | 编辑业务码 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `DeviceDefinition.assertUpdatableByBusinessCode` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `DeviceDefinitionRepository.findById` |
| `eventPublishAnchor` | `new DeviceDefinitionUpdateEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-DEVDEF-W-002` |

**方法契约摘要**：前置条件为符合业务码可编辑矩阵；持久化影响为更新目录字段且名称变更时重算检索码；失败语义为越权字段更新或目标不存在时报错；事件触发为发布 `DeviceDefinitionUpdateEvent`。**注释锚点**：可编辑性校验 -> 字段更新 -> 持久化 -> 事件。

> 事件载荷约束：`DeviceDefinitionUpdateEvent` 为最小载荷事件（`id + changedFields`），不承诺携带完整业务字段；领域事件后查询刷新需按 `aggregateId` 回源聚合快照再执行字段同步。应用层组装的 `changedFields` 与实体 Java 属性名对齐（如 `purchaseUnitCode`、`splitUnitCode`、`stockUnitCode`，常量键见 `FieldNameConstants`）。

---

#### 5.1.3 `retireDeviceDefinitions(RetireDeviceDefinitionsCommand cmd)` → `void`

**命令文件**：`RetireDeviceDefinitionsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `ids` | `List<Long>` | 待停用目录标识集合 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `DeviceDefinition.assertRetirable` |
| `statusTransitionMethod` | `DeviceDefinition.transitToRetired` |
| `repositoryChecks` | `DeviceDefinitionRepository.findAllById` |
| `eventPublishAnchor` | `new DeviceDefinitionStopEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-DEVDEF-W-003` |

**方法契约摘要**：前置条件为目录存在且当前可停用；持久化影响为批量 `ACTIVE -> RETIRED`；失败语义为非法状态或缺失记录时报错；事件触发为逐条发布 `DeviceDefinitionStopEvent`。**注释锚点**：批量校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.4 `activateDeviceDefinitions(ActivateDeviceDefinitionsCommand cmd)` → `void`

**命令文件**：`ActivateDeviceDefinitionsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `ids` | `List<Long>` | 待启用目录标识集合 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `DeviceDefinition.assertActivatable` |
| `statusTransitionMethod` | `DeviceDefinition.transitToActive` |
| `repositoryChecks` | `DeviceDefinitionRepository.findAllById` |
| `eventPublishAnchor` | `new DeviceDefinitionStartEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-DEVDEF-W-004` |

**方法契约摘要**：前置条件为目录当前 RETIRED；持久化影响为批量 `RETIRED -> ACTIVE`；失败语义为状态非法或缺失记录时报错；事件触发为逐条发布 `DeviceDefinitionStartEvent`。**注释锚点**：批量校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.5 `importDeviceDefinitionCatalog(ImportDeviceDefinitionCatalogCommand cmd)` → `ImportResult`

**命令文件**：`ImportDeviceDefinitionCatalogCommand.java`

**命令根字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `items` | `List<Item>` | 导入项集合（嵌套类 `ImportDeviceDefinitionCatalogCommand.Item`） |
| `operatorId` | `Long` | 操作人 |
| `overwrite` | `Boolean` | 是否覆盖更新 |

**导入项 `Item` 字段全集**（与实现对齐）：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | `String` | 是 | 器材业务编码（院内唯一） |
| `name` | `String` | 是 | 器材名称 |
| `categoryCode` | `String` | 否 | 器材分类编码 |
| `typeCode` | `String` | 是（应用维护/导入） | 器材种类编码（`device_type_code`；无明确种类用 `other`） |
| `unitCode` | `String` | 否 | 采购单位（写入 `purchase_unit_code`） |
| `totalVolume` | `String` | 否 | 包装规格（写入 `total_volume`） |
| `stockUnitCode` | `String` | 否 | 库存单位（写入 `stock_unit_code`）；**仅空白修剪**：`null`/空串归一为 `null`，不计入 `assertImportItemValid` 必填 |
| `orgId` | `Long` | 否 | 所属科室 |
| `locationId` | `Long` | 否 | 所在位置 |
| `ybFlag` | `Boolean` | 否 | 医保标记 |
| `ybNo` | `String` | 否 | 医保编码 |
| `ybOrgNo` | `String` | 否 | 医药机构目录编码 |
| `ybMatchFlag` | `Boolean` | 否 | 医保对码标记 |
| `chrgitmLv` | `Integer` | 否 | 医保等级 |
| `description` | `String` | 否 | 器材说明 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `DeviceDefinition.assertImportItemValid` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `DeviceDefinitionRepository.findByBusNoIn`（实现以仓储为准） |
| `eventPublishAnchor` | `new DeviceDefinitionCreatedEvent/UpdateEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-DEVDEF-W-005` |

**方法契约摘要**：前置条件为导入内重复与库内冲突规则通过；持久化影响为按项执行新增或更新（含 **`stockUnitCode` → `stock_unit_code`**）；失败语义为模板数据非法时整批或逐条失败按导入策略返回；事件触发为新增发创建事件、覆盖更新发更新事件且 **`changedFields` 固定摘要包含 `stockUnitCode`**（与界面编辑摘要口径一致）。**注释锚点**：导入校验 -> 分类写入 -> 持久化 -> 事件。

---

#### 5.1.6 `syncDeviceDefinitionYbMapping(SyncDeviceDefinitionYbMappingCommand cmd)` → `void`

**命令文件**：`SyncDeviceDefinitionYbMappingCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 器材定义标识 |
| `ybFlag` | `Boolean` | 医保标记 |
| `ybNo` | `String` | 医保编码 |
| `ybOrgNo` | `String` | 医药机构目录编码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |
| `chrgitmLv` | `Integer` | 医保等级 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `DeviceDefinition.assertYbMappingPayloadValid` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `DeviceDefinitionRepository.findById` |
| `eventPublishAnchor` | `new DeviceDefinitionUpdateEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-DEVDEF-W-006` |

**方法契约摘要**：前置条件为目标目录存在且回写数据结构合法；持久化影响为更新医保相关字段；失败语义为目标不存在或数据非法时报错；事件触发为发布 `DeviceDefinitionUpdateEvent`。**跨模块消费**：`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService` 在 3301 成功后调用。**注释锚点**：校验 -> 字段回写 -> 持久化 -> 事件。

### 5.2 读侧 — `DeviceDefinitionQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `getDeviceManageInit` | `DeviceManageInitQuery` | `DeviceManageInitQueryModel` | 聚合字典与选项初始化 | `TC-DEVDEF-R-001` |
| `pageDeviceDefinition` | `DeviceDefinitionPageQuery` | `Page<DeviceDefinitionPageQueryModel>` | 条件分页查询目录 | `TC-DEVDEF-R-002` |
| `getDeviceDefinition` | `Long id` | `DeviceDefinitionDetailQueryModel` | 主键详情查询 | `TC-DEVDEF-R-003` |
| `searchByKeyWord` | `String keyword` | `List<DeviceDefinitionSearchQueryModel>` | 名称/编码/拼音/五笔检索 | `TC-DEVDEF-R-004` |
| `findBySearchKey` | `String searchKey` | `List<DeviceDefinitionLightQueryModel>` | 轻量检索列表 | `TC-DEVDEF-R-005` |
| `getAllDeviceDefinitions` | `List<Long> ids` | `List<DeviceDefinitionEntityBatchQueryModel>` | 批量主档加载 | `TC-DEVDEF-R-006` |
| `getDeviceDefinitionEditability` | `Long id` | `DeviceDefinitionEditabilityQueryModel` | 编辑前可编辑性查询 | `TC-DEVDEF-R-007` |
| `downloadDeviceDefinitionCatalogTemplate` | `TemplateQuery` | `DeviceDefinitionImportTemplateQueryModel` | 导入模板下载 | `TC-DEVDEF-R-008` |
| `exportDeviceDefinitionCatalog` | `DeviceDefinitionExportQuery` | `List<DeviceDefinitionExportQueryModel>` | 目录批量导出 | `TC-DEVDEF-R-009` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

### 5.3 关联应用层契约（`zxplat/health-application` · 耗材目录）

以下不属于领域模块正文，但与 DM **§3.1 五类计量单位贯通口径**一致，供端到端验收对照（与药品目录 `MedicationCatalogAppService` / `CreateMedicationCatalogRequest` 字段策略对齐）：

| 落点 | 契约要点 |
| ---- | -------- |
| `DeviceCatalogAppService#init` | 返回 `DeviceCatalogInitDto.unitCodeOptions`（术语 `unit_code`）与 `doseUnits`（`dose_unit`）；**不再**同源双写 `packUnits`/`saleUnits`/`minUnits`/`stockUnits`。 |
| `DeviceCatalogAppService#pageDeviceCatalog` | 响应 `PageDeviceCatalogResponse` 继承 `PagedResult`（**B.10.11**）；总条数字段为 **`totalCount`**（经 `PagedResultBuilder`）；前端列表读 `totalCount`，兼容回落 `totalElements`。 |
| `DeviceCatalogAppService#createDeviceCatalog` / `#editDeviceCatalog` | **必填**：`name`、`categoryCode`、`itemTypeCode`、`totalVolume`、采购/拆零/包装/库存单位及采购/库存/包装相对拆零倍数、`purchasePrice`（采购参考价）、`retailPrice`（零售参考价）。**选填**：`manufacturerText` / `manufacturerId` 等。**剂量**：界面不维护；`normalizeDoseUnitDefaultsForWrite` 在未传时设 `doseUnitCode=splitUnitCode`、`doseUnitRatio=1`；`assertDeviceUnitRatiosPositive` 仅校验采购/库存/包装三类倍数。定价经 `replacePriceLines` 写维护态 `adm_charge_item_definition`。 |
| `DeviceCatalogAppService#listDeviceLotPrices` | 辅读：按耗材主键查当前 ACTIVE **`LOT_NUMBER_COST`（进价）** / **`LOT_NUMBER_PRICE`（售价）**；`lotNumber` 可空；出参 `CatalogLotPriceRowDto`（`costPrice`/`salePrice`）。与维护态参考价分离。 |
| `CreateDeviceCatalogRequest` / `UpdateDeviceCatalogRequest` | 主字段 **`purchaseUnitCode`**、**`splitUnitCode`**、**`packageUnitCode`**、**`stockUnitCode`** 及 `purchaseUnitRatio` / `stockUnitRatio` / `packageUnitRatio`；`doseUnitCode` / `doseUnitRatio` 可省略（由应用层默认）。**无**历史别名 `unitCode` / `minUnitCode`。`totalVolume` 为规格描述文本，不得写入单位展示名。 |
| 前端 `devicecatalog/[id]/index.vue` | 表单：`categoryCode`、`purchasePrice`、`retailPrice` 标红必填（文案：采购参考价/零售参考价）；`manufacturerText` 选填；`doseUnitCode`、`doseUnitRatio` 字段 `hide: true`。 |
| `DeviceCatalogMapper`（`DeviceCatalogMapperImpl`） | `toCreateCommand` / `toUpdateCommand`：`purchaseUnitCode` → 命令项 `unitCode`，`splitUnitCode` → `minUnitCode`；`packageUnitCode` / `stockUnitCode` / `doseUnitCode` 与换算比直映命令项；`toDto` **仅**回显 `purchaseUnitCode` / `splitUnitCode`（及 dictText）。更新路径 `changedFields` 含 `FieldNameConstants.PURCHASE_UNIT_CODE`、`SPLIT_UNIT_CODE` 等。 |
| `DeviceCatalogDto` | 详情与列表回显 `purchaseUnitCode`、`splitUnitCode`、`packageUnitCode`、`stockUnitCode`、`doseUnitCode` 及 `*_dictText`；字典文案统一经 **`unit_code`** 解析（`enrichPageFields`）；`purchasePrice` / `retailPrice` 由维护态定价行回填（采购参考价/零售参考价）。 |
| `DeviceDefinitionQueryService#getDeviceManageInit` | 未删除且 `status_enum=ACTIVE`（兼容 `"1"`）的全量启用耗材；仍供 `init.deviceDefOptions` / `listDeviceDefOptions` 兼容接口。**WH-BND001 耗材绑定目标选品**改走 `pageDeviceCatalog`（见 **DM-CUS-005** §9.4）。 |
| 前端 `devicecatalog/index.vue` | 分页总数读 `responseData.totalCount ?? totalElements`；与 **B.10.11** `PagedResult` 对齐。 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-DEVDEF-001 | `busNo` 全局唯一，写前去重 |
| BR-DEVDEF-002 | 名称变更时同步重算 `pyStr/wbStr` |
| BR-DEVDEF-003 | 启停仅改状态，不清理历史引用 |
| BR-DEVDEF-004 | 目录与收费实例保持可追溯一致 |
| BR-DEVDEF-005 | 编辑范围由业务码控制 |
| BR-DEVDEF-006 | 写侧变更后QueryService最终一致；耗材更新投影采用“最小事件 + 快照回源”策略 |
| BR-DEVDEF-007 | 业务发生判定由他域提供，目录侧按码执行 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `DeviceDefinitionCreatedEvent` | `com.zxplat.domain.share.events.administration.devicedefinition` | `id,busNo,name,statusEnum` | `createDeviceDefinition` |
| `DeviceDefinitionUpdateEvent` | `com.zxplat.domain.share.events.administration.devicedefinition` | `id,changedFields`（最小载荷；应用层/导入路径可含 `stockUnitCode` 等业务字段名摘要） | `updateDeviceDefinition`、`importDeviceDefinitionCatalog`（覆盖更新）、`syncDeviceDefinitionYbMapping` |
| `DeviceDefinitionStopEvent` | `com.zxplat.domain.share.events.administration.devicedefinition` | `id,statusEnum` | `retireDeviceDefinitions` |
| `DeviceDefinitionStartEvent` | `com.zxplat.domain.share.events.administration.devicedefinition` | `id,statusEnum` | `activateDeviceDefinitions` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- DeviceDefinition 删除语义为逻辑删除，禁止物理删除。
- 目录停用后限制新业务引用，历史业务保持可追溯。
- 默认查询过滤 `isDeleted=false`，停用状态是否包含可由场景参数控制。
- 批量导入更新遵循可编辑矩阵与唯一约束，不绕过门禁。
- 医保回写仅更新医保字段，不改变发布状态。

---

## 9. 框架优先适配规则

- 软删、审计字段、事务工作单元、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口，导入导出编排由应用层承接。
- 分页查询必须数据库侧过滤，确保统计一致。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 6，与 DM §6.1 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 9 个方法
- [ ] §6 BR 映射覆盖 BR-DEVDEF-001 至 BR-DEVDEF-007
- [ ] §7 事件表覆盖 DM §5 全部 4 条事件
- [ ] 导入、启停、医保回写与状态语义一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-008-DeviceDefinition-器材定义.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.administration.devicedefinition`

### 11.2 DoD

- 写侧 6 个方法全部落地，签名与 Command 命名与 §5 一致。
- 目录字段、状态语义、可编辑矩阵与 §4/§6 一致。
- 事件按 §7 beforeCommit 发布，载荷不低于最小要求。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧 9 个方法与 §4.6 QueryModel 对齐，分页口径一致。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若导入规则、可编辑矩阵或状态门禁无法落实，DoD 失败不得进入成码。

---

## 12. 应用层目录编辑门禁（跨模块）

> 完整契约：**AS-BAS-CAT-001** / **AS-BAS-CAT-001-SDD**；DM 字段矩阵：**DM-ADM-008 §9.2**。

| 组件 | 职责 |
|------|------|
| `CatalogEditabilityResolveSupport#resolveDeviceEditability` | 读侧业务码 |
| `CatalogEditWriteGuardSupport#assertDeviceCatalogUpdateAllowed` | 写侧 immutable 断言 |
| `DeviceCatalogAppService#validateDeviceCatalogEdit` | validate 接口 |
| `DeviceCatalogDto.editabilityCode/Message` | 详情/更新 DTO |
| `DeviceDefinition#assertUpdatableByBusinessCode` | 接受 OK/LOCK_* |

**跨聚合读**：`DeviceRequestQueryService#existsByDeviceDefId`、`SupplyRequestQueryService#existsInboundByItemId`。

**前端**：`devicecatalog/[id]/index.vue` + `catalog-editability.ts`。

| 日期 | 摘要 |
| --- | --- |
| 2026-07-04 | 对齐实现；§4.6.3 补 editability 字段；§4.6.7 与 validate 共用 `CatalogEditabilityQueryModel` |

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（6）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（4）
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
| 2026-10-06 | **WH-BND001 选品消费方**：`getDeviceManageInit` 不再作为绑定页耗材选品主路径；对齐 **DM-CUS-005** v1.0.7 |
| 2026-08-14 | **收费耗材同向 / 导入 v1.4**：§4.2 `chargeableFlag` 与界面/导入列同向；应用层模板废止「不收费耗材」取反；对齐 DM **v1.13** |
| 2026-07-15 | **参考价 / 批号真价**：§5.3 增补 `listDeviceLotPrices`；维护态 `purchasePrice`/`retailPrice` 文案对齐采购参考价/零售参考价；对齐 DM **v1.10** |
| 2026-07-14 | **病案结算类型 / 收费耗材**：§4.2 / §4.6.1 增补 `mrSettlementTypeCode`、`chargeableFlag` 与 Init 选项；对齐 DM **v1.9** |
| 2026-07-11 | **CHS 3301 写回**：§5.1.6 `syncDeviceDefinitionYbMapping` 增补跨模块消费（`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService`）；对齐 DM **v1.8** |
| 2026-06-24 | **WH-BND001 / 耗材目录分页**：§5.3 增补 `pageDeviceCatalog` 的 `totalCount` 与前端映射；`getDeviceManageInit` 消费方与启用态过滤口径；对齐 DM **§6.2.1** |
| 2026-05-29 | **维护页校验收口**：对齐 DM **v1.4** 与 `DeviceCatalogAppService` / `devicecatalog` 前端——分类与购入价/零售价必填、生产厂家选填、剂量单位界面隐藏及 `normalizeDoseUnitDefaultsForWrite`。 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

