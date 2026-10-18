# DM-MED-001 MedicationDefinition 药品定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MED-001-MedicationDefinition-药品定义.md`（**v1.1.12**，结余留用批次号）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：medication  
> **聚合**：MedicationDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-MED-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | zxplat-domain |
| 包规则 `com.zxplat.domain.medication.medicationdefinition` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 `MedicationDefinition` 聚合的实体契约、方法契约、事件与不变式，实现一次成码输入闭环。
- 明确目录主数据与收费、发放、医保协作边界，避免目录服务覆盖他域事实字段。
- 固化单表聚合实现口径：一方法一 Command、beforeCommit 事件发布、默认软删过滤。

### 1.2 边界

- **在内**：药品目录主数据创建、编辑、启停用、废弃、导入、医保对码回写。
- **在外**：收费实例生成、库存台账、追溯码台账、医保网关调用。
- **跨域仅端口声明**：对 ChargeItemDefinition、医保对码、目录投影仅做端口与事件约定。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.medication.medicationdefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `MedicationDefinition` |
| `valueobject` | 目录内值对象（如后续扩展） |
| `command` | 每个写方法一个命令文件 |
| `repository` | `MedicationDefinitionRepository`（JpaRepository 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | 共享事件，位于 `zxplat-domain` |

**存储口径（实现对齐）**：`*_enum` / `*_code` 在本聚合均以 `String` 编码持久化；`*_flag` 使用 `Boolean`；`isDeleted` 统一软删。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id` |
| 时间 | 目录生效/失效、审计时间字段用 `LocalDateTime` |
| `*_enum` | `String`（存编码） |
| `*_code` | `String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` + `@Table(name = "med_medication_definition")` |
| 仓储 | `JpaRepository<MedicationDefinition, Long>` |
| 分页 | `Pageable` + 默认 `createdAt DESC` |
| 事件 | 领域服务内 `new XxxEvent` 后 `publish`，时机 beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `med_medication_definition` | `MedicationDefinition` | 药品目录单表聚合根 |

### 4.2 聚合根 `MedicationDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键（雪花 ID） |
| `busNo` | `String` | 是 | 业务编码，机构内唯一（BR-MDF-001） |
| `name` | `String` | 是 | 药品名称（BR-MDF-002） |
| `domainEnum` | `String` | 否 | 适用范围编码 |
| `version` | `String` | 否 | 药品版本 |
| `nameEn` | `String` | 否 | 英文药名 |
| `pyStr` | `String` | 否 | 药品名称拼音码（BR-MDF-004：名称变更时重算） |
| `wbStr` | `String` | 否 | 药品五笔码（BR-MDF-004） |
| `categoryCode` | `String` | 否 | 药品分类（字典码） |
| `merchandiseName` | `String` | 否 | 商品名称 |
| `merchandisePyStr` | `String` | 否 | 商品名拼音码 |
| `merchandiseWbStr` | `String` | 否 | 商品名五笔码 |
| `purchaseUnitCode` | `String` | 否 | 采购单位（字典码） |
| `splitUnitCode` | `String` | 否 | 拆零单位（字典码） |
| `salesUnitCode` | `String` | 否 | 销售单位（字典码） |
| `stockUnitCode` | `String` | 否 | 库存单位（字典码） |
| `comprisedText` | `String` | 否 | 所含耗材 |
| `ingredient` | `String` | 否 | 成分 |
| `doseFrom` | `Integer` | 否 | 剂量形式 |
| `approvalNumber` | `String` | 否 | 批准文号 |
| `ybMatchFlag` | `Boolean` | 否 | 医保是否对码 |
| `ybNo` | `String` | 否 | 医保编码 |
| `pharmacologyCategoryCode` | `String` | 否 | 药理作用分类（字典码） |
| `skinTestFlag` | `Boolean` | 否 | 是否皮试 |
| `injectFlag` | `Boolean` | 否 | 是否注射药物 |
| `manufacturerId` | `Long` | 否 | 生产厂家 ID |
| `manufacturerText` | `String` | 否 | 生产厂商文本 |
| `supplyId` | `Long` | 否 | 供应商 ID |
| `restrictedFlag` | `Boolean` | 否 | 是否限制使用 |
| `restrictedScope` | `String` | 否 | 限制使用范围 |
| `activeFlag` | `Boolean` | 否 | 启停用快照（`true`=启用） |
| `childrenFlag` | `Boolean` | 否 | 儿童用药标志 |
| `characteristic` | `Integer` | 否 | 产品特性 |
| `itemMinQuantity` | `BigDecimal` | 否 | 最小库存警戒数量 |
| `itemMaxQuantity` | `BigDecimal` | 否 | 最大库存警戒数量 |
| `defEncounterUnitCode` | `String` | 否 | 默认门诊单位 |
| `defInhospitalUnitCode` | `String` | 否 | 默认住院单位 |
| `nationalDrugCode` | `String` | 否 | 贯标国家编码 |
| `partAttributeEnum` | `String` | 否 | 门诊拆分属性编码 |
| `antibioticCode` | `String` | 否 | 抗生素分类（字典码） |
| `restrictedEnum` | `String` | 否 | 权限限制编码 |
| `selfFlag` | `Boolean` | 否 | 是否自制 |
| `antibioticFlag` | `Boolean` | 否 | 是否抗生素 |
| `basicFlag` | `Boolean` | 否 | 基药标识 |
| `nationalMonitorFlag` | `Boolean` | 否 | 国家重点监控药品（勾选=是） |
| `centralPurchaseFlag` | `Boolean` | 否 | 集采药品（勾选=是） |
| `keyDrugType`（筛选入参，非持久化） | `String` | 否 | 列表筛：`KeyDrugType` code；选中=对应 flag true |
| `thoPartAttributeEnum` | `String` | 否 | 住院临时医嘱拆分属性编码 |
| `doseUnitRatio` | `BigDecimal` | 否 | 剂量→拆零换算比 |
| `purchaseUnitRatio` | `BigDecimal` | 否 | 采购→拆零换算比 |
| `stockUnitRatio` | `BigDecimal` | 否 | 库存→拆零换算比 |
| `salesUnitRatio` | `BigDecimal` | 否 | 销售→拆零换算比 |
| `chrgitmLv` | `Integer` | 否 | 医保等级（枚举） |
| `mrSettlementTypeCode` | `String` | 否 | 病案结算类型（术语 `mr_settlement_type_code`） |
| `surplusRetentionBatchCode` | `String` | 否 | 结余留用批次号（术语 `surplus_retention_batch_code`） |
| `rxFlag` | `Boolean` | 否 | 处方标志 |
| `dosageInstruction` | `String` | 否 | 用药说明 |
| `drug69Code` | `String` | 否 | 药品 69 码 |
| `totalVolume` | `String` | 否 | 规格 |
| `doseFormCode` | `String` | 否 | 剂型（字典码） |
| `doseUnitCode` | `String` | 否 | 剂量单位（字典码） |
| `methodCode` | `String` | 否 | 默认用法（字典码） |
| `rateCode` | `String` | 否 | 默认频次（字典码） |
| `dose` | `BigDecimal` | 否 | 单次剂量 |
| `minUnit` | `BigDecimal` | 否 | 单次最小剂量 |
| `maxUnit` | `BigDecimal` | 否 | 单次最大剂量 |
| `usageLimit` | `BigDecimal` | 否 | 用量限定 |
| `locationId` | `Long` | 否 | 采购入库位置（他域事实列，BR-MDF-007） |
| `statusEnum` | `String` | 否 | 发布状态编码 |
| `orgId` | `Long` | 否 | 机构 ID（租户隔离） |
| `ingredientItem` | `String` | 否 | 成分项 |
| `definition` | `String` | 否 | 药品说明 |
| `dddCode` | `String` | 否 | 限定日剂量编码（WHO ATC） |
| `dddUnitCode` | `String` | 否 | 限定日剂量单位编码 |
| `minRateCode` | `String` | 否 | 最小频次编码（字典码） |
| `maxRateCode` | `String` | 否 | 最大频次编码（字典码） |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `tenantId` | `Long` | 系统 | 租户 ID |

**不变式方法（示例命名，实现须一致）**：`validateRequiredForCreate`、`ensureEditableStatus`、`retire`、`activate`、`markDeleted`、`validateInsuranceMapping`。

### 4.2.1 值对象字段展开

> **规则**：当 §4.2 或子实体节中某字段类型为值对象类时，字段表中该行类型列须填值对象类名，并在此处补一个值对象展开子节。

本聚合为单表聚合，无复合值对象字段需展开。所有字段均为基本类型或引用标识，平铺于聚合根表中。

### 4.3 子实体

无。本聚合为单表聚合。

**归属校验**：N/A（单表聚合，无子实体归属校验需求）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `MedicationDefinitionRepository extends JpaRepository<MedicationDefinition, Long>`
- `Optional<MedicationDefinition> findByIdAndIsDeletedFalse(Long id)`
- `boolean existsByOrgIdAndBusNoAndIsDeletedFalse(Long orgId, String busNo)`
- `Page<MedicationDefinition> findAll(Specification<MedicationDefinition> spec, Pageable pageable)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `MedicationDefinitionPageQueryModel`（对应 `pageMedicationCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_definition.id` | 主键 |
| `busNo` | `String` | `med_medication_definition.bus_no` | 业务编码 |
| `name` | `String` | `med_medication_definition.name` | 药品名称 |
| `categoryCode` | `String` | `med_medication_definition.category_code` | 分类 |
| `statusEnum` | `Integer` | `med_medication_definition.status_enum` | 状态 |
| `activeFlag` | `Boolean` | `med_medication_definition.active_flag` | 启用标志 |

#### 4.6.2 `MedicationDefinitionDetailQueryModel`（对应 `getMedicationCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `med_medication_definition.id` | 主键 |
| `busNo` | `String` | `med_medication_definition.bus_no` | 业务编码 |
| `name` | `String` | `med_medication_definition.name` | 名称 |
| `ybNo` | `String` | `med_medication_definition.yb_no` | 医保编码 |
| `doseFormCode` | `String` | `med_medication_definition.dose_form_code` | 剂型 |
| `dosageInstruction` | `String` | `med_medication_definition.dosage_instruction` | 用药说明 |
| `editabilityCode` | `String` | 应用层规则计算 | 编辑业务码（**AS-BAS-CAT-001**） |
| `editabilityMessage` | `String` | 应用层规则计算 | 编辑说明 |

#### 4.6.3 `MedicationDefinitionInitQueryModel`（对应 `getMedicationInit`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `categoryOptions` | `List<OptionModel>` | 术语服务 | 分类下拉 |
| `doseFormOptions` | `List<OptionModel>` | 术语服务 | 剂型下拉 |
| `unitOptions` | `List<OptionModel>` | 术语服务 | 单位下拉 |
| `frequencyOptions` | `List<OptionModel>` | 术语服务 | 频次下拉 |
| `mrSettlementTypeCodeOptions` | `List<OptionModel>` | 术语服务 | 病案结算类型下拉（`mr_settlement_type_code`） |
| `surplusRetentionBatchCodeOptions` | `List<OptionModel>` | 术语服务 | 结余留用批次号下拉（`surplus_retention_batch_code`） |

#### 4.6.4 `MedicationDefinitionTemplateQueryModel`（对应 `downloadMedicationCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `templateName` | `String` | 模板配置 | 模板名称 |
| `templateVersion` | `String` | 模板配置 | 模板版本 |
| `content` | `byte[]` | 模板文件存储 | 下载内容 |

#### 4.6.5 `MedicationDefinitionExportRowQueryModel`（对应窄字段 `exportMedicationCatalog`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `busNo` | `String` | `med_medication_definition.bus_no` | 编码 |
| `name` | `String` | `med_medication_definition.name` | 名称 |
| `categoryCode` | `String` | `med_medication_definition.category_code` | 分类 |
| `statusEnum` | `String` | `med_medication_definition.status_enum` | 状态（枚举 code） |
| `manufacturerText` | `String` | `med_medication_definition.manufacturer_text` | 厂商 |

> 完整模板列导出不扩展本模型，应用层使用 `MedicationDefinitionDetailQueryModel` + 维护态定价行组装 xlsx（见 AS-BAS-CAT-002 · IF-BAS-CAT-EXP-01）。

#### 4.6.5b `listMedicationCatalogForExport` 出参

与 §4.6.2 `MedicationDefinitionDetailQueryModel` 字段全集相同；条数受 `maxRows` 约束。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `MedicationDefinitionDomainService`

#### 5.1.1 `createMedicationDefinition(CreateMedicationDefinitionCommand cmd)` → `Long`
**命令文件**：`CreateMedicationDefinitionCommand.java`
**字段全集**：
| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 药品名称 |
| `categoryCode` | `String` | 分类 |
| `unitCode` | `String` | 单位 |
| `orgId` | `Long` | 机构 |
| `doseFormCode` | `String` | 剂型 |
| `methodCode` | `String` | 默认用法 |
| `rateCode` | `String` | 默认频次 |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredForCreate` |
| `statusTransitionMethod` | `applyPublicationStatus` |
| `repositoryChecks` | `existsByOrgIdAndBusNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDefinitionCreatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-001` |
**方法契约摘要**：前置条件为基础字段完整且编号唯一；持久化影响为插入主表并初始化状态为已发布；失败语义为必填缺失/唯一冲突拒绝；事件触发 `MedicationDefinitionCreatedEvent`。

---

#### 5.1.2 `editMedicationDefinition(EditMedicationDefinitionCommand cmd)` → `void`
**命令文件**：`EditMedicationDefinitionCommand.java`
**字段全集**：`id`、`name`、`merchandiseName`、`doseFormCode`、`methodCode`、`rateCode`、`dosageInstruction`、`mrSettlementTypeCode`、`surplusRetentionBatchCode`（其余 Item 字段以 `EditMedicationDefinitionCommand` 为准）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureEditableStatus` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDefinitionUpdatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-002` |
**方法契约摘要**：前置条件为状态可编辑且不覆盖他域字段；持久化影响为原记录更新并执行版本号递增（`version + 1`）；检索码规则为“UI非空优先，空值后端自动生成”；失败语义为状态不允许或字段非法拒绝；事件触发 `MedicationDefinitionUpdatedEvent`。

---

#### 5.1.3 `retireMedicationDefinitions(RetireMedicationDefinitionsCommand cmd)` → `void`
**命令文件**：`RetireMedicationDefinitionsCommand.java`  
**字段全集**：`ids(List<Long>)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `applyPublicationStatus` |
| `statusTransitionMethod` | `retire` |
| `repositoryChecks` | `findAllById` |
| `eventPublishAnchor` | `new MedicationDefinitionRetiredEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-003` |
**方法契约摘要**：前置条件为条目处于已发布；持久化影响为批量更新停用状态；失败语义为非法状态批次拒绝；事件按条发布 `MedicationDefinitionRetiredEvent`。

---

#### 5.1.4 `activateMedicationDefinitions(ActivateMedicationDefinitionsCommand cmd)` → `void`
**命令文件**：`ActivateMedicationDefinitionsCommand.java`  
**字段全集**：`ids(List<Long>)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `applyPublicationStatus` |
| `statusTransitionMethod` | `activate` |
| `repositoryChecks` | `findAllById` |
| `eventPublishAnchor` | `new MedicationDefinitionActivatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-004` |
**方法契约摘要**：前置条件为条目处于停用态；持久化影响为恢复为已发布；失败语义为状态不匹配拒绝；事件触发 `MedicationDefinitionActivatedEvent`。

---

#### 5.1.5 `discardMedicationDefinition(DiscardMedicationDefinitionCommand cmd)` → `void`
**命令文件**：`DiscardMedicationDefinitionCommand.java`  
**字段全集**：`id(Long)`、`reason(String)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted` |
| `statusTransitionMethod` | `discard` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDefinitionDeletedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-005` |
**方法契约摘要**：前置条件为无禁止废弃引用；持久化影响为逻辑废弃并不可逆；失败语义为被引用或已废弃时拒绝；事件触发 `MedicationDefinitionDeletedEvent`。

---

#### 5.1.6 `importMedicationCatalog(ImportMedicationCatalogCommand cmd)` → `ImportMedicationCatalogResult`
**命令文件**：`ImportMedicationCatalogCommand.java`  
**字段全集**：`batchNo`、`operatorId`、`fileId`、`rows(List<MedicationDefinitionImportRowCommand>)`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredForCreate` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `existsByOrgIdAndBusNoAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationCatalogImportedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-006` |
**方法契约摘要**：前置条件为模板与行校验通过；持久化影响为批量新增/更新目录；失败语义为行错误按策略拒绝整批或部分；事件触发批次事件并可按行触发创建/更新事件。

---

#### 5.1.7 `syncInsuranceCatalogMapping(SyncInsuranceCatalogMappingCommand cmd)` → `void`
**命令文件**：`SyncInsuranceCatalogMappingCommand.java`  
**字段全集**：`id`、`ybMatchFlag`、`ybNo`、`chrgitmLv`、`syncTime`。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateInsuranceMapping` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new MedicationDefinitionUpdatedEvent(...)` |
| `unitTestCaseId` | `TC-MEDDEF-WS-007` |
**方法契约摘要**：前置条件为目录存在且医保字段合法；持久化影响为回写医保对码列；失败语义为目录不存在或数据非法拒绝；事件触发目录更新事件。**跨模块消费**：`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService` 在 3301 成功后调用（回写院内 `yb_no` / `yb_match_flag` / `chrgitm_lv`）。

### 5.2 读侧 — `MedicationDefinitionQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧完成。  

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `pageMedicationCatalog` | `Specification`+`Pageable` 或 `MedicationDefinitionCatalogPageQuery`+`Pageable` | `Page<MedicationDefinitionPageQueryModel>` | 维护列表双重重载 | TC-MEDDEF-RS-001 |
| `getMedicationCatalog` | `Long id` | `MedicationDefinitionDetailQueryModel` | 主键查询 + 详情组装 | TC-MEDDEF-RS-002 |
| `getMedicationInit` | `Long orgId` | `MedicationDefinitionInitQueryModel` | 聚合术语/基础数据 | TC-MEDDEF-RS-003 |
| `downloadMedicationCatalog` | — | `MedicationDefinitionTemplateQueryModel` | 模板载入 | TC-MEDDEF-RS-004 |
| `exportMedicationCatalog` | — | `List<MedicationDefinitionExportRowQueryModel>` | 窄字段兼容导出；内部委托 listForExport | TC-MEDDEF-RS-005 |
| `listMedicationCatalogForExport` | `MedicationDefinitionCatalogPageQuery`+`maxRows` | `List<MedicationDefinitionDetailQueryModel>` | 维护列表同口径筛选；供应用层模板列导出 | TC-MEDDEF-RS-005b |
| `pageForPurchase` | `ProductDefinitionPurchasePageQuery`+`Pageable` | `Page<MedicationDefinition>` | 采购选品 | TC-MEDDEF-RS-006 |
| `pageForCatalog` | `ProductDefinitionCatalogPageQuery`+`Pageable` | `Page<MedicationDefinition>` | 医嘱开立目录；委托采购谓词 | TC-MEDDEF-RS-007 |
| `validateMedicationCatalog` | `Long id` | `boolean` | 非 DISCARDED 可编辑 | TC-MEDDEF-RS-008 |
| `resolveCategoryCodeByMedicationDefinitionId` | `Long medicationDefinitionId` | `String`（可 null） | FIFO 签发分类码 | TC-MEDDEF-RS-009 |
| `findAllByIds` | `Collection<Long> ids` | `List<MedicationDefinition>` | 批量主键 | TC-MEDDEF-RS-010 |
| `findByOrgIdAndBusNos` | `Long orgId`, `List<String> busNos` | `List<MedicationDefinition>` | 同步 upsert 前置 | TC-MEDDEF-RS-011 |
| `listIdsByCategoryCodesIn` | `Collection<String> categoryCodes` | `List<Long>` | 库存类型筛选 | TC-MEDDEF-RS-012 |
| `listIdsByChrgitmLvIn` | `Collection<Integer> chrgitmLvList` | `List<Long>` | 库存医保等级筛选 | TC-MEDDEF-RS-013 |

**读方法注释锚点（统一）**：构建过滤条件（含软删）→ 执行仓储查询 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-MDF-001 | 仓储唯一校验 `orgId + busNo` |
| BR-MDF-002 | 创建命令必填校验 |
| BR-MDF-003 | 状态机方法收口迁移 |
| BR-MDF-004 | 名称变更触发拼音/五笔重算 |
| BR-MDF-005 | 应用层编排校验收费实例存在 |
| BR-MDF-006 | `partPercent > 0` |
| BR-MDF-007 | 写命令白名单限制他域字段 |
| BR-MDF-008 | 废弃前引用保护检查 |
| BR-MDF-009 | 对外仅暴露 `MedicationDefinitionId` |
| BR-MDF-010 | 跨域强校验由应用层编排 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `MedicationDefinitionCreatedEvent` | `com.zxplat.domain.share.events.medication.medicationdefinition` | `id,busNo,name,orgId` | `createMedicationDefinition` |
| `MedicationDefinitionUpdatedEvent` | `com.zxplat.domain.share.events.medication.medicationdefinition` | `id,changedFields` | `editMedicationDefinition`,`syncInsuranceCatalogMapping` |
| `MedicationDefinitionRetiredEvent` | `com.zxplat.domain.share.events.medication.medicationdefinition` | `id,statusEnum` | `retireMedicationDefinitions` |
| `MedicationDefinitionActivatedEvent` | `com.zxplat.domain.share.events.medication.medicationdefinition` | `id,statusEnum` | `activateMedicationDefinitions` |
| `MedicationDefinitionDeletedEvent` | `com.zxplat.domain.share.events.medication.medicationdefinition` | `id` | `discardMedicationDefinition` |
| `MedicationCatalogImportedEvent` | `com.zxplat.domain.share.events.medication.medicationdefinition` | `batchNo,successCount,failCount` | `importMedicationCatalog` |

**发布时机**：全部 beforeCommit。

---

## 8. 删除与可见性契约

### 8.1 软删语义

- **逻辑删除字段**：`isDeleted`（`Boolean`），继承自 `BaseAuditedEntity`。
- **物理删除策略**：本聚合禁止物理 `DELETE`，仅允许逻辑删除。
- **查询默认过滤**：所有 Repository 查询默认附加 `isDeleted = false` 条件，框架统一处理。
- **审计追溯**：逻辑删除记录保留 `deletedAt`、`deletedBy` 审计字段，支持历史追溯。

### 8.2 状态可见性规则

| 状态（`statusEnum`） | 新建业务引用 | 列表查询 | 详情查询 | 编辑 | 废弃 |
| ------------------- | ------------ | -------- | -------- | ---- | ---- |
| `DRAFT`（草稿） | ❌ 禁止 | ✅ 可见 | ✅ 可加载 | ✅ 可编辑 | ✅ 允许 |
| `ACTIVE`（已发布） | ✅ 允许 | ✅ 可见 | ✅ 可加载 | ✅ 可编辑 | ⚠️ 需校验引用（BR-MDF-008） |
| `RETIRED`（已停用） | ❌ 禁止 | ✅ 可见 | ✅ 可加载 | ❌ 仅特定字段 | ⚠️ 需校验引用 |
| `DELETED`（已废弃） | ❌ 禁止 | ❌ 默认不可见 | ⚠️ 管理后台 | ❌ 不可编辑 | ❌ 终态 |

### 8.3 跨域引用保护

- **引用检查**：废弃（`DELETED`）前必须校验是否存在下游引用（BR-MDF-008）。
- **引用类型**：主要包括 `MedicationRequest`、`MedicationDispense`、`ChargeItemDefinition`、`InventoryItem` 等聚合的 `medicationId` 引用。
- **引用保护策略**：存在有效引用时拒绝废弃，返回业务错误码及引用详情。

---

## 9. 框架优先适配规则

### 9.1 实体与仓储

| 规则 | 说明 |
| ---- | ---- |
| 基类继承 | `MedicationDefinition extends BaseAuditedEntity`，统一继承审计、软删、租户字段 |
| 实体注解 | `@Entity` + `@Table(name = "med_medication_definition")`，显式声明表名 |
| 字段映射 | `@Column(name = "bus_no")` 等，下划线命名与 Java 驼峰互转 |
| 仓储形态 | `interface MedicationDefinitionRepository extends JpaRepository<MedicationDefinition, Long>`，禁止自定义实现类 |
| 查询方法 | 使用 Spring Data JPA 派生查询方法，禁止手写 JPQL（复杂查询走 Specification） |
| 软删过滤 | 框架统一处理，Repository 接口名不强制 `AndIsDeletedFalse` 冗余 |

### 9.2 领域服务

| 规则 | 说明 |
| ---- | ---- |
| 服务形态 | `MedicationDefinitionDomainService` 为直接实现类，不强制接口 + Impl 双层 |
| 事务管理 | 依赖应用层/框架工作单元，**禁止**在领域服务方法机械添加 `@Transactional` |
| 命令处理 | 一方法一 Command，禁止单文件容器聚合全部命令 |
| 事件发布 | `DomainEventPublisher.publish(new XxxEvent(...))`，**beforeCommit** 时机 |
| 异常处理 | 业务异常使用项目统一异常体系，禁止裸抛 `RuntimeException` |

### 9.3 值对象与枚举

| 规则 | 说明 |
| ---- | ---- |
| 值对象 | 本聚合无复合值对象，所有字段平铺于聚合根 |
| 枚举复用 | `PublicationStatus`、`MedicationCategory` 等优先复用 `zxplat-domain` 已有定义，禁止重复生成 |
| 字典码 | `categoryCode`、`doseFormCode` 等使用 `String` 存储字典码，查询时关联术语服务 |

### 9.4 分页与排序

| 规则 | 说明 |
| ---- | ---- |
| 分页参数 | `Pageable` 接口，未指定排序时默认 `createdAt DESC` |
| 查询优化 | 列表查询优先使用投影/QueryService，避免加载完整实体 |
| 统计口径 | 分页统计与过滤口径一致，禁止先分页后内存过滤 |

---

## 10. 生成与验收清单

- [ ] 实体字段与 DM `§3.1` 对齐
- [ ] 7 个写方法均具备独立 Command 文件
- [ ] QueryService字段与 §4.6 对齐
- [ ] 事件类与 §7 一致并 beforeCommit 发布
- [ ] BR 绑定测试用例覆盖关键路径

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-MED-001 与本 SDD。
- 明确共享事件包、命令文件清单与仓储签名。

### 11.2 DoD

- 编译通过；写读方法与命令/模型数量一致。
- 关键 BR（唯一、状态迁移、拆零比、引用保护）有测试。

### 11.3 失败处理

- 任一 BR 或事件契约不满足时回滚到文档修订，不进入实现提交。

---

## 12. 应用层目录编辑门禁（跨模块）

> 完整契约：**AS-BAS-CAT-001** / **AS-BAS-CAT-001-SDD**；DM 字段矩阵：**DM-MED-001 §9.1**。

| 组件 | 职责 |
|------|------|
| `CatalogEditabilityResolver#resolveMedicationEditability` | 读侧业务码 |
| `CatalogEditWriteGate#assertMedicationCatalogUpdateAllowed` | 写侧 immutable 断言（维护态采购参考价/销售参考价不比对） |
| `MedicationCatalogAppService#validateMedicationCatalogEdit` | validate 接口 |
| `MedicationCatalogAppService#listMedicationLotPrices` | 批号进价/售价辅读（`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`，当前 ACTIVE） |
| `MedicationDetailDto.editabilityCode/Message` | 详情/更新 DTO |
| `MedicationDetailDto.purchasePrice` / `retailPrice` | 维护态采购参考价 / 销售参考价（中草药分类码 `4`：UI 展示/录入 3 位，落库仍 6 位；见 **AS-BAS-CAT-001 BR-CAT-011**） |

**跨聚合读**：`MedicationRequestQueryService#existsByMedicationId`、`SupplyRequestQueryService#existsInboundByItemId`。

**前端**：`medication-catalog-edit-dialog.vue` + `catalog-editability.ts`（见 AS-SDD §5）；中药**进价**小数位见 `money-scale.ts`（`MONEY_TCM_UNIT_PRICE_DP` / `formatPurchaseUnitPriceDisplay`）；售价仍 2 位。

| 日期 | 摘要 |
| --- | --- |
| 2026-08-03 | 中药饮片维护态进售价 UI 3 位（BR-CAT-011）；落库仍 6 位 |
| 2026-07-15 | 对齐参考价文案与 `listMedicationLotPrices`；写侧门禁类名更正为 `CatalogEditWriteGate` |
| 2026-07-04 | 对齐 zxplat + openhis-pro-ui；§4.6.2 补 editability 字段 |

---

## 定稿检查清单（可打勾）

- [x] 章节结构与金骨架一致
- [x] 写侧方法与 DM 白名单一一对应
- [x] 事件表覆盖 DM 全事件
- [x] 无“后续补充/待确认”占位描述

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
| 2026-09-10 | **结余留用批次号**：§4.2 / §4.6.3 / §5.1.2 增补 `surplusRetentionBatchCode` 与 Init 选项；对齐 DM **v1.1.12** |
| 2026-07-14 | **病案结算类型**：§4.2 / §4.6.3 增补 `mrSettlementTypeCode` 与 Init 选项；对齐 DM **v1.1.5** |
| 2026-07-11 | **CHS 3301 写回**：§5.1.7 `syncInsuranceCatalogMapping` 增补跨模块消费（`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService`）；对齐 DM **v1.1.4** |
| 2026-05-28 | **DM-SDD M1**：§5.2 补 §6.2.6～13 读法；签名对齐 Java；对齐 DM **v1.1.1** |
| 2026-05-28 | **ReadModel 废止**（DM **v1.1**）：§5.2 / §8.2 读侧 **`MedicationDefinitionQueryService`**；废止 **ProductDefinition** RM 消费表述 |

---

**本 SDD 正文结束。**
