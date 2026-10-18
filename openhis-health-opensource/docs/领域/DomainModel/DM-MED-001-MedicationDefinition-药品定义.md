# DM-MED-001 MedicationDefinition 药品定义

> **限界上下文**：用药域 · 药品定义管理  
> **聚合类型**：单表聚合（物理表 `med_medication_definition`）  
> **版本**：v1.1.17（导入判重：旧编码优先，否则名称+规格+厂家主键）

---

## 1. 概述

### 1.1 业务定义

药品定义（MedicationDefinition）是院内药品目录的**唯一**主数据聚合，承载药品的基础属性、分类、规格、剂型、厂家、医保对照以及默认用法用量等信息。它是临床开方、药房发药、采购入库、计费结算等全链路业务的**产品锚点**。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 药品目录主数据维护 | 管理院内药品的名称、分类、剂型、规格、厂家、批准文号等基础信息 |
| 计费产品锚点 | 为 ChargeItemDefinition 提供产品实例（`instanceTable` + `instanceId`），确保计费有据可依 |
| 默认用药规范 | 承载默认用法、频次、单次剂量、DDD（日限定剂量）等临床用药参考数据 |
| 医保目录对照 | 存储医保编码、医保等级（甲/乙/丙）、对码标记等，支撑医保结算拆分 |
| 拆零管理 | 定义拆零比、最小单位与拆零策略，支持门诊和住院场景的拆零发药 |
| 检索码维护 | 名称或商品名变更时自动同步拼音码与五笔码，支撑快速检索 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 药品目录建设 | 新建院内药品目录，支持单条录入与批量导入 |
| 目录日常维护 | 修改药品属性、调整默认用法用量、更新医保信息 |
| 医嘱开立检索 | 作为 MedicationRequest 的药品选择数据源 |
| 采购与库存 | 作为采购入库、库存查询的产品类型键（`itemTable = med_medication_definition`） |
| 计费绑定 | 与 ChargeItemDefinition 联动，确保每种药品具备有效定价 |
| 医保对码 | 与医保目录进行对照匹配，标记对码状态与结果 |

### 1.4 与 MedicationRequest / MedicationDispense 的关系

| 维度 | MedicationDefinition | MedicationRequest | MedicationDispense |
|---|---|---|---|
| 定位 | 药品目录主数据（"是什么"） | 用药申请/医嘱（"要做什么"） | 药品发放记录（"做了什么"） |
| 生命周期 | 跟随目录维护，与就诊无关 | 跟随就诊，从开立到停止 | 跟随发药流程，从配药到签收 |
| 关联方式 | 被引用方（产品锚点） | 引用药品定义标识(MedicationDefinitionId) | 引用药品定义标识(MedicationDefinitionId) |
| 价格信息 | 不直接持有，通过 ChargeItemDefinition 关联 | 不持有价格 | 通过 ChargeItem 间接关联 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `med_medication_definition`，主键 `id`；一行表示一条院内药品目录定义。

**高频查询模式（建议索引口径）**：按 `bus_no`、`name`/`py_str`/`wb_str`、`category_code`、`active_flag`、租户；医嘱开立检索与目录维护分页按产品约定排序键。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 药品定义标识 | `MedicationDefinitionId` | 全局唯一标识，系统生成（雪花 ID） |

### 2.3 状态机

> **§2.2～§2.3** 仅承载标识与状态机。聚合根属性表在 `§3.1`；**字段全集**以 历史归档中的同主题文档 §2.1 为口径；当前 实体若未包含部分列，以实现与 Flyway 为准差异落地。

```
PublicationStatus（药品定义生命周期）

  [入口：手工新建 §6.1.1]
       │
       └──────────────────────────────────────> [已发布] ──停用──> [已停用] ──重新启用──> [已发布]
                                                    │                  │
                                                    └──废弃──> [已废弃]  └──废弃──> [已废弃]

  [草稿]（批量导入等；本图不连接「草稿 → 已发布」）
    │
    └──废弃──> [已废弃]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | DRAFT | 草稿 | 由批量导入等产生的中间态，尚未作为可引用目录终态；**不与「已发布」构成状态机上的直接迁移**，允许编辑、废弃 |
| 2 | ACTIVE | 已发布 | 生效中，可被医嘱和业务引用，允许编辑、停用、废弃 |
| 3 | RETIRED | 已停用 | 暂时停用，新业务不可引用，历史引用不受影响，允许重新启用、废弃 |
| 4 | DELETED | 已废弃 | 逻辑删除，终态 | — |

> **状态枚举说明**：`draft / active / retired / deleted` 与 ActivityDefinition、ConditionDefinition 等定义类聚合保持语义一致。**手工新建**见 §6.1.1：自初始态 **直接进入已发布**，图中不表达「草稿→已发布」迁移。导入等产生的 **草稿** 与 **已发布** 之间的收口（删除重建、直接落已发布、或分应用事务）由 **§6.1.6** 与 本稿业务约定，**不**在本状态机图中画箭头，避免与「新建即已发布」口径冲突。

---

## 3. 实体

> **同表存储与领域边界**（口径对齐旧版领域模型 §2.2，但以本稿不变式为准）：`med_medication_definition` 若存在科室/货位、批号、效期、入库位置等他域列，语义与生命周期归属发药/库存等上下文；目录维护用例不得覆盖上述事实字段（`BR-MDF-007`）。

```
MedicationDefinition（聚合根，单表聚合）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                 MedicationDefinition                  │
  │  - busNo / name / categoryCode / unitCode / minUnitCode │
  │  - partPercent / ybNo / activeFlag / manufacturerId …  │
  └──────────────────────────────────────────────────────┘
```

### 3.1 药品定义（MedicationDefinition）

药品定义聚合根属性，对应 `med_medication_definition` 表。**下列字段与 Java 属性名**与 历史归档中的同主题文档 §2.1 **一致**；**物理列名**按 snake_case 给出（与常见 MyBatis 下划线映射一致）。若与部署库实际列名不一致，以 Flyway/实体为准校正。

| 属性 | 字段名（物理列/映射） | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 主键 | `id` | `Long` | 是 | 主键 |
| 业务编码 | `bus_no`（`busNo`） | `String` | 是 | 药品编号（业务编码，由采番生成） |
| 药品名称 | `name` | `String` | 是 | 药品名称 |
| 适用范围 | `domain_enum`（`domainEnum`） | `Integer` | 否 | 适用范围 |
| 药品版本 | `version` | `String` | 否 | 药品版本 |
| 英文药名 | `name_en`（`nameEn`） | `String` | 否 | 英文药名 |
| 拼音码 | `py_str`（`pyStr`） | `String` | 否 | 药品名称拼音码 |
| 五笔码 | `wb_str`（`wbStr`） | `String` | 否 | 药品五笔码 |
| 药品分类 | `category_code`（`categoryCode`） | `String` | 否 | 药品分类 |
| 商品名称 | `merchandise_name`（`merchandiseName`） | `String` | 否 | 商品名称 |
| 商品名拼音码 | `merchandise_py_str`（`merchandisePyStr`） | `String` | 否 | 商品名称拼音码 |
| 商品名五笔码 | `merchandise_wb_str`（`merchandiseWbStr`） | `String` | 否 | 商品五笔码 |
| 采购单位 | `purchase_unit_code`（`purchaseUnitCode`） | `String` | 否 | 采购单位 |
| 拆零单位 | `split_unit_code`（`splitUnitCode`） | `String` | 否 | 拆零单位 |
| 销售单位 | `sales_unit_code`（`salesUnitCode`） | `String` | 否 | 销售单位 |
| 库存单位 | `stock_unit_code`（`stockUnitCode`） | `String` | 否 | 库存单位 |
| 所含耗材 | `comprised_text`（`comprisedText`） | `String` | 否 | 所含耗材 |
| 成分 | `ingredient` | `String` | 否 | 成分 |
| 剂量形式 | `dose_from`（`doseFrom`） | `Integer` | 否 | 剂量形式（持久化字段名） |
| 批准文号 | `approval_number`（`approvalNumber`） | `String` | 否 | 批准文号 |
| 医保是否对码 | `yb_match_flag`（`ybMatchFlag`） | `Boolean` | 否 | 医保是否对码 |
| 医保编码 | `yb_no`（`ybNo`） | `String` | 否 | 医保编码 |
| 药品性质 | `pharmacology_category_code`（`pharmacologyCategoryCode`） | `String` | 否 | 药品性质（术语 `medicine_properties`：普通/麻醉/毒性/精一/精二等）；**门诊分方特殊管理轨道以此为准**；勿与 `part_attribute_enum`（门诊拆分/拆零规则）混淆 |
| 是否皮试 | `skin_test_flag`（`skinTestFlag`） | `Boolean` | 否 | 是否皮试 |
| 是否注射药物 | `inject_flag`（`injectFlag`） | `Boolean` | 否 | 是否为注射药物 |
| 生产厂家 ID | `manufacturer_id`（`manufacturerId`） | `Long` | 否 | 生产厂家主键，关联 `cus_supplier.id`（`SupplierType.MANUFACTURER` / code=`1`）；展示名由应用层按 id 解析 `cus_supplier.name` |
| 供应商 ID | `supply_id`（`supplyId`） | `Long` | 否 | 供应商 ID（进货供货方；与生产厂家分离；药品目录维护写路径不再写入） |
| 是否限制使用 | `restricted_flag`（`restrictedFlag`） | `Boolean` | 否 | 是否限制使用 |
| 限制使用范围 | `restricted_scope`（`restrictedScope`） | `String` | 否 | 限制使用范围 |
| 是否使用（启用/停用） | `active_flag`（`activeFlag`） | `Boolean` | 否 | `true`=启用，`false`=停用 |
| 儿童用药标志 | `children_flag`（`childrenFlag`） | `Boolean` | 否 | 儿童用药标志 |
| 产品特性 | `characteristic` | `Integer` | 否 | 产品特性 |
| 最小库存警戒数量 | `item_min_quantity`（`itemMinQuantity`） | `BigDecimal` | 否 | 最低警戒（**库存单位**）；低于此值预警；**null**=未维护，库存预警回落系统设置（**AS-INV-WHS-004 BR-INV-DTL-024**） |
| 最大库存警戒数量 | `item_max_quantity`（`itemMaxQuantity`） | `BigDecimal` | 否 | 最高警戒（**库存单位**）；高于此值预警；**null**=未维护 |
| 默认门诊单位 | `def_encounter_unit_code`（`defEncounterUnitCode`） | `String` | 否 | 默认门诊单位 |
| 默认住院单位 | `def_inhospital_unit_code`（`defInhospitalUnitCode`） | `String` | 否 | 默认住院单位 |
| 贯标国家编码 | `national_drug_code`（`nationalDrugCode`） | `String` | 否 | 贯标国家编码 |
| 拆分属性 | `part_attribute_enum`（`partAttributeEnum`） | `String` | 否 | 门诊拆分属性编码 |
| 抗生素分类 | `antibiotic_code`（`antibioticCode`） | `String` | 否 | 抗生素分类 |
| 权限限制 | `restricted_enum`（`restrictedEnum`） | `String` | 否 | 权限限制编码 |
| 是否自制 | `self_flag`（`selfFlag`） | `Boolean` | 否 | 是否自制 |
| 是否抗生素 | `antibiotic_flag`（`antibioticFlag`） | `Boolean` | 否 | 是否抗生素 |
| 基药标识 | `basic_flag`（`basicFlag`） | `Boolean` | 否 | 基药标识 |
| 国家重点监控药品 | `national_monitor_flag`（`nationalMonitorFlag`） | `Boolean` | 否 | 勾选=是；口径对齐基药标识 |
| 集采药品 | `central_purchase_flag`（`centralPurchaseFlag`） | `Boolean` | 否 | 勾选=是；口径对齐基药标识 |
| 住院临时医嘱拆分属性 | `tho_part_attribute_enum`（`thoPartAttributeEnum`） | `String` | 否 | 住院临时医嘱拆分属性编码 |
| 剂量→拆零换算比 | `dose_unit_ratio`（`doseUnitRatio`） | `BigDecimal` | 否 | 剂量单位到拆零单位换算比 |
| 采购→拆零换算比 | `purchase_unit_ratio`（`purchaseUnitRatio`） | `BigDecimal` | 否 | 采购单位到拆零单位换算比 |
| 库存→拆零换算比 | `stock_unit_ratio`（`stockUnitRatio`） | `BigDecimal` | 否 | 库存单位到拆零单位换算比 |
| 销售→拆零换算比 | `sales_unit_ratio`（`salesUnitRatio`） | `BigDecimal` | 否 | 销售单位到拆零单位换算比 |
| 追溯码贴码单位 | `trace_unit_code`（`traceUnitCode`） | `String` | **是** | 追溯码所贴包装单位；**可游离于五类业务单位之外**；同意入库写入 `cus_trace_number.trace_unit_code`；已建码以主档为准。**不参与**发药/退药对 `wkf_inventory_item` 的扣加换算（扣库只认发放 **`unit_code`→五类拆零倍数**，见 **AS-OP-PHA-001 BR-OPP-017**） |
| 追溯码→拆零换算比 | `trace_unit_ratio`（`traceUnitRatio`） | `BigDecimal` | **是** | 1 个追溯码单位对应的拆零数量；须 &gt; 0。仅应扫码数、主档剩余盖住等**追溯域**使用；**禁止**当发药扣库倍数 |
| 医保等级 | `chrgitm_lv`（`chrgitmLv`） | `Integer` | 否 | 医保等级 |
| 病案结算类型 | `mr_settlement_type_code`（`mrSettlementTypeCode`） | `String` | 否 | 病案首页费用结算分类；术语体系 `mr_settlement_type_code` |
| 结余留用批次号 | `surplus_retention_batch_code`（`surplusRetentionBatchCode`） | `String` | 否 | 结余留用批次号；术语体系 `surplus_retention_batch_code`（title 与字段中文同名）；存概念 code |
| 处方标志 | `rx_flag`（`rxFlag`） | `Boolean` | 否 | 处方标志 |
| 用药说明 | `dosage_instruction`（`dosageInstruction`） | `String` | 否 | 用药说明 |
| 药品69码 | `drug_69_code`（`drug69Code`） | `String` | 否 | 药品69码 |
| 规格 | `total_volume`（`totalVolume`） | `String` | 否 | 规格 |
| 剂量单位 | `dose_unit_code`（`doseUnitCode`） | `String` | 否 | 剂量单位 |
| 剂型 | `dose_form_code`（`doseFormCode`） | `String` | 否 | 剂型（与 §4.5 码表口径并存时以码表为准） |
| 用量限定 | `usage_limit`（`usageLimit`） | `BigDecimal` | 否 | 用量限定 |
| 用法 | `method_code`（`methodCode`） | `String` | 否 | 用法 |
| 默认用药频次 | `rate_code`（`rateCode`） | `String` | 否 | 默认用药频次 |
| 单次剂量 | `dose` | `BigDecimal` | 否 | 单次剂量 |
| 单次最小剂量 | `min_unit`（`minUnit`） | `BigDecimal` | 否 | 单次最小剂量 |
| 单次最大剂量 | `max_unit`（`maxUnit`） | `BigDecimal` | 否 | 单次最大剂量 |
| 采购入库位置 | `location_id`（`locationId`） | `Long` | 否 | 采购入库位置（他域事实列，目录写边界见 `BR-MDF-007`） |
| 状态（可用/停用） | `status_enum`（`statusEnum`） | `String` | 否 | 生命周期状态编码（`ACTIVE/RETIRED/DISCARDED` 等） |
| 机构 ID | `org_id`（`orgId`） | `Long` | 否 | 机构 ID |
| 成分项 | `ingredient_item`（`ingredientItem`） | `String` | 否 | 成分项 |
| 药品说明 | `definition` | `String` | 否 | 药品说明 |
| 限定日剂量编码 | `ddd_code`（`dddCode`） | `String` | 否 | 限定日剂量编码 |
| 限定日剂量单位编码 | `ddd_unit_code`（`dddUnitCode`） | `String` | 否 | 限定日剂量单位编码 |
| 最小频次编码 | `min_rate_code`（`minRateCode`） | `String` | 否 | 最小频次编码 |
| 最大频次编码 | `max_rate_code`（`maxRateCode`） | `String` | 否 | 最大频次编码 |

> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）以 **`BaseAuditedEntity`** 为文档权威口径。当前实体类继承 `HisBaseEntity` 且仍存在 `deleteFlag` 等兼容列时，以部署库表与后续收敛迁移为准，不在此展开列级细节。

### 3.2 子实体

本聚合为单表聚合，无子实体。

---

## 4. 值对象（含枚举）

### 4.1 药品分类（MedicationCategory）

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 西药 | `western` | 化学药品 |
| 中成药 | `chinese-patent` | 中药成方制剂 |
| 中药饮片 | `chinese-herbal` | 中药饮片/中草药 |
| 生物制品 | `biological` | 疫苗、血液制品等生物制品 |

### 4.2 医保目录等级（InsuranceCatalogLevel）

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 甲类 | `category-a` | 甲类药品，100% 纳入医保报销基数 |
| 乙类 | `category-b` | 乙类药品，按比例纳入医保报销 |
| 丙类 | `category-c` | 丙类药品，不纳入医保报销（全自费） |
| 未分类 | `unclassified` | 尚未进行医保目录分类 |

### 4.3 拆零属性（SplitPolicy）

> 说明：拆零属性用于定义门诊发药数量的取整口径，取整方式均为向上取整。

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 门诊按最小单位每次量向上取整 | `opd-min-unit-each-dose-ceil` | 门诊药品可拆零。按医生处方每次剂量折算到最小单位后向上取整；因向上取整产生的零余药品（如额外半片）在当前计价口径下视为不可再次售卖部分，不得再次计价。 |
| 门诊按包装单位不可拆分 | `opd-pack-unit-no-split` | 门诊药品不可拆零。按处方总量折算到包装单位后向上取整，按整包装发药。 |
| 门诊按最小单位总量向上取整 | `opd-min-unit-total-ceil` | 门诊药品可拆零。按处方总量折算到最小单位后向上取整（例如 4.5 片按 5 片计发）。 |
| 门诊按包装单位每次量向上取整 | `opd-pack-unit-each-dose-ceil` | 门诊药品不可拆零。按处方每次量折算到包装单位后向上取整，每次均按整包装计发。 |

### 4.4 发布状态（PublicationStatus）

与 §2.3 状态定义一致（定义类聚合标准四态；持久化字段以实现 `activeFlag` 等为准，语义映射见 本稿 **§6.1**）。

| 状态 | 英文 | 说明 |
|---|---|---|
| 草稿 | `draft` | 新建尚未生效，不可被医嘱引用 |
| 已发布 | `active` | 生效中，可被医嘱和业务引用 |
| 已停用 | `retired` | 暂时停用，新业务不可引用 |
| 已废弃 | `deleted` | 逻辑删除，终态 |

### 4.5 剂型（DoseForm）

常用剂型枚举（可扩展，系统中以术语编码 `doseFormCode` 存储，由码表解析显示名）：

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 片剂 | `tablet` | 口服固体制剂 |
| 胶囊剂 | `capsule` | 硬/软胶囊 |
| 注射剂 | `injection` | 注射用溶液/粉针 |
| 口服液 | `oral-solution` | 口服液体制剂 |
| 颗粒剂 | `granule` | 冲剂/颗粒 |
| 丸剂 | `pill` | 中药丸剂 |
| 散剂 | `powder` | 粉末制剂 |
| 膏剂 | `ointment` | 外用膏剂 |
| 栓剂 | `suppository` | 直肠/阴道栓剂 |
| 气雾剂 | `aerosol` | 吸入/喷雾制剂 |
| 贴剂 | `patch` | 透皮贴剂 |
| 其他 | `other` | 其他剂型 |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称（中文） | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 药品定义已创建 | `MedicationDefinitionCreatedEvent` | 新建药品定义落库后 | 药品定义标识、业务编码、名称、租户（若有） | QueryService或缓存失效（若启用） |
| 药品定义已更新 | `MedicationDefinitionUpdatedEvent` | 目录属性更新后 | 药品定义标识、变更字段摘要 | 同上 |
| 药品定义已停用 | `MedicationDefinitionRetiredEvent` | 批量或单条停用后 | 药品定义标识列表、状态 | 同上 |
| 药品定义已重新启用 | `MedicationDefinitionActivatedEvent` | 停用后重新启用 | 药品定义标识、状态 | 同上 |
| 药品定义已废弃 | `MedicationDefinitionDeletedEvent` | 逻辑删除后 | 药品定义标识 | 同上 |
| 药品目录已批量导入 | `MedicationCatalogImportedEvent` | 批量导入落库后 | 批次标识、导入数量 | 检索/对账侧QueryService（若启用） |

事件载荷（药品定义标识、业务编码、变更摘要等）与发布约定以 `zxplat-domain` / 应用层 本稿 **§6.1** 为准。

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 新建 | 已发布 | `MedicationDefinitionCreatedEvent` |
| 草稿/已发布 | 编辑属性 | 不变 | `MedicationDefinitionUpdatedEvent` |
| 已发布 | 停用 | 已停用 | `MedicationDefinitionRetiredEvent` |
| 已停用 | 重新启用 | 已发布 | `MedicationDefinitionActivatedEvent` |
| 草稿/已发布/已停用 | 废弃 | 已废弃 | `MedicationDefinitionDeletedEvent` |
| — | 批量导入 | 草稿或已发布（逐行落库；**无**「草稿→已发布」状态机箭头，收口见 §6.1.6） | `MedicationCatalogImportedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（MedicationDefinitionDomainService）

> 写侧由应用服务用例编排事务后调用领域服务完成；命令类型、幂等与异常字面量见本稿与落库约束。编辑前强校验与业务码裁剪由应用层编排他域能力，领域侧 `validateEdit` 类入口若存在占位实现，不承载完整跨域规则（与 `BR-MDF-010` 一致）。

#### 6.1.1 新建药品定义（`createMedicationDefinition`）

- **业务意义**：在院内**一次性新建**一条药品目录主数据（不拆分为「注册 + 发布」两步），生成稳定 `busNo` 与检索辅助信息，并进入可被医嘱与计费引用的生效态（对齐旧版领域模型写侧「新建」语义）。
- **边界说明**：仅写入本聚合主表及聚合内初始化字段；不替代收费域创建 `ChargeItemDefinition` 的编排职责（由应用同事务或紧接编排）；医保实时对码网关不在本方法内展开。
- **入参**：药品基础信息命令（覆盖 §3.1 字段子集，以命令对象为准）。
- **出参**：药品定义标识（`MedicationDefinitionId` / 主键）。
- **前置条件**：满足 `BR-MDF-002` 最低完整性；`busNo` 采番成功（`BR-MDF-001`）；新建后须满足 `BR-MDF-005` 定价锚点编排（由应用保证）。
- **状态变化**：**新建默认已发布**（`active` / 已发布，见 §2.3、§4.4）；**不**先落 `draft` 再单独发布。批量导入等若仍产生草稿行，属 `importMedicationCatalog` 路径，不在本方法展开。
- **聚合内影响**：插入 `med_medication_definition`；生成 `bus_no`；自动生成拼音码/五笔码（`BR-MDF-004`）；`status_enum` / `active_flag` 等与实现一致置为可用态。
- **事件输出**：`MedicationDefinitionCreatedEvent`（不再单独要求 `MedicationDefinitionPublishedEvent`）。
- **失败语义**：采番失败、必填缺失、唯一性冲突或定价锚点编排失败时拒绝落库。

> **方法名提示**：领域实现若以 `registerMedicationDefinition` 等别名暴露，与本文 `createMedicationDefinition` 视为同一「新建」能力，以本稿 **§6.1** 与落库命名为准对齐。

#### 6.1.2 编辑药品定义（`editMedicationDefinition`）

- **业务意义**：维护已存在目录项的展示与临床参考属性，并保持检索码与名称一致。
- **边界说明**：仅更新目录域允许字段；应用服务须按 §9.3 矩阵与他域返回的业务码裁剪更新命令；不覆盖他域事实列（`BR-MDF-007`）。
- **入参**：药品定义标识、更新命令（增量或全量以落库与交互契约为准）。
- **出参**：—。
- **前置条件**：状态须为草稿或已发布（`BR-MDF-003`）；若已引用且禁止废弃，须满足 `BR-MDF-008`。
- **状态变化**：保持原记录更新语义，不变更发布状态；版本号每次编辑 +1。
- **聚合内影响**：采用“原地更新 + 版本号递增”的编辑模式；名称/商品名检索码按“UI优先，空值后端自动生成”规则处理；收费定价实例仍由应用编排同步（见 §8.2）。
- **事件输出**：`MedicationDefinitionUpdatedEvent`。
- **失败语义**：状态不允许编辑、越权字段或校验失败时拒绝保存。

#### 6.1.3 批量停用（`retireMedicationDefinitions`）

- **业务意义**：将多条已发布目录项批量置为停用，抑制新业务引用，保留历史引用。
- **边界说明**：仅批量更新本聚合状态字段，不级联停止已开立医嘱。
- **入参**：药品定义标识列表。
- **出参**：—。
- **前置条件**：列表内条目须为已发布（`active`）；非已发布项跳过或拒绝策略以落库与交互契约为准。
- **状态变化**：`active` → `retired`（已停用）。
- **聚合内影响**：批量更新 `active_flag` 等状态持久化字段。
- **事件输出**：逐条 `MedicationDefinitionRetiredEvent`。
- **失败语义**：列表为空或存在非法状态时批量失败或部分成功（以落库与交互契约为准）。

#### 6.1.4 批量重新启用（`activateMedicationDefinitions`）

- **业务意义**：将已停用目录项恢复为已发布，可重新被新业务引用。
- **边界说明**：只恢复目录状态，不回补停用期间外部业务数据。
- **入参**：药品定义标识列表。
- **出参**：—。
- **前置条件**：条目须为已停用（`retired`）。
- **状态变化**：`retired` → `active`。
- **聚合内影响**：批量更新状态字段。
- **事件输出**：逐条 `MedicationDefinitionActivatedEvent`。
- **失败语义**：含非停用项或定价锚点缺失时拒绝或部分失败（以落库与交互契约为准）。

#### 6.1.5 废弃药品定义（`discardMedicationDefinition`）

- **业务意义**：对目录项执行逻辑废弃，进入终态，默认查询不可见。
- **边界说明**：仅逻辑删除语义，不物理删除；跨聚合引用保护由前置校验或应用编排保证（`BR-MDF-008`）。
- **入参**：药品定义标识。
- **出参**：—。
- **前置条件**：不存在禁止废弃的活跃引用（`BR-MDF-008`）；非已废弃态。
- **状态变化**：进入 `deleted`（已废弃）或等价逻辑删除标记（与 §2.3、`BaseAuditedEntity` 约定一致）。
- **聚合内影响**：终态收口，不可逆（`BR-MDF-003`）。
- **事件输出**：`MedicationDefinitionDeletedEvent`。
- **失败语义**：存在禁止废弃引用或状态不允许时拒绝。

#### 6.1.6 批量导入目录（`importMedicationCatalog`）

- **业务意义**：通过 xlsx 模板批量新建院内药品目录，支撑上线建库与批量补录。
- **产品契约权威**：应用服务 **`AS-BAS-CAT-002`**（模板列、唯一键、价格同行、事务边界）。
- **边界说明**：文件解析、码表映射、整批校验与收费维护态定价由**应用层编排**；本方法承载逐行创建（或等价批量命令）。**本期仅新增**，不更新既有目录。
- **入参**：导入批次命令（批次号、操作者、已解析行列表；行字段以 `AS-BAS-CAT-002` §3.3 为准）。
- **出参**：导入结果（`batchNo`、成功条数；失败时由应用层在写库前拒绝，成功条数为 0）。
- **前置条件**：模板版本合法；全部行通过校验。文件内同键只保留首行，库内同键跳过不新建（`AS-BAS-CAT-002` BR-IMP-009/010）。有院内旧编码时按旧编码；否则按名称+规格+厂家主键。
- **状态变化**：成功行一律 **已发布（ACTIVE）**；**不**经草稿。
- **聚合内影响**：批量**新建** `med_medication_definition`；`busNo` 系统采番。
- **跨聚合协作**：每条新建后由应用层写入维护态 `ChargeItemDefinition`（至少 `PURCHASE` + `SALE`，可选 `LIMIT`），满足 `BR-MDF-005`。
- **事件输出**：按行 `MedicationDefinitionCreatedEvent`（可选）；批次级 `MedicationCatalogImportedEvent`。
- **失败语义**：**整批事务回滚**——任一行校验或写库失败则本批次零新增（`AS-BAS-CAT-002` D2 / BR-IMP-013）。

#### 6.1.7 同步医保目录对码（`syncInsuranceCatalogMapping`）

- **业务意义**：回写医保对码结果与目录侧展示字段，支撑结算与对码展示。
- **边界说明**：对码规则、网关与回写来源属医保上下文；本方法仅承载本表可写字段更新（与旧文档 `PR-MEDDEF-YB-001/002` 口径一致）。
- **入参**：药品定义标识、对码结果命令（编码、标记、来源、时间等以落库与交互契约为准）。
- **出参**：—。
- **前置条件**：目录项存在；对码命令通过医保侧校验（以落库与交互契约为准）。
- **状态变化**：`N/A`（通常不改变 PublicationStatus 主语义）。
- **聚合内影响**：更新 `yb_match_flag`、`yb_no` 等对码承载字段。
- **事件输出**：可选「对码状态变更」类事件以落库与交互契约为准；未统一命名则不写死。
- **失败语义**：目录不存在或对码回写非法时拒绝。
- **跨模块消费**：`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService` 在 3301 成功后调用（CHS UI 链回写院内 `yb_no` / `yb_match_flag` / `chrgitm_lv`）。

### 6.2 读侧查询服务（MedicationDefinitionQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达 **`MedicationDefinitionQueryService`**。列表/混页检索含 **`pageForCatalog`**、**`pageForPurchase`**；详情合并主表与 `ChargeItemDefinition` 定价（**BR-MEDDEF-QRY-001**～`003`）。已废止 **ProductDefinition** RM。

#### 6.2.1 分页查询药品目录（`pageMedicationCatalog`）

- **业务意义**：维护列表分页；两重重载：① `Specification` + `Pageable`（应用组装动态条件）；② `MedicationDefinitionCatalogPageQuery` + `Pageable`（维护页标准筛选）。
- **边界说明**：只读；映射 `MedicationDefinitionPageQueryModel`。
- **过滤口径**：`keyword`、`categoryCode`、`keyDrugType`、可选 `statusEnum`（`ACTIVE`/`INACTIVE`；空=不限）；默认排除逻辑删除。
- **返回约束**：`Page<MedicationDefinitionPageQueryModel>`。

#### 6.2.2 查询药品目录详情（`getMedicationCatalog`）

- **业务意义**：按主键加载单条目录详情，支撑编辑回显与只读详情页。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主表 `med_medication_definition` 与QueryService/收费实例联合查询（以落库与交互契约为准）。
- **过滤口径**：主键精确匹配；默认排除已逻辑删除。
- **组装规则**：合并「投影 + 主表」字段（旧文档 QRY-002 意图）。
- **返回约束**：不存在时抛业务异常或返回空以落库与交互契约为准。

#### 6.2.3 目录初始化字典（`getMedicationInit`）

- **业务意义**：返回维护页所需分类、剂型、单位、频次等初始化选项，降低前端分散请求成本。
- **边界说明**：只读聚合多域码表/主数据，不在本方法内写目录主表。
- **查询范围（读库，无写入）**：术语、供应商、库房等只读数据源（以落库与交互契约为准）。
- **过滤口径**：按机构/租户与缓存策略过滤（以落库与交互契约为准）。
- **组装规则**：聚合为单一初始化载荷 DTO。
- **返回约束**：只读；字段集合以产品配置为准。

#### 6.2.4 下载导入模板（`downloadMedicationCatalog`）

- **业务意义**：提供批量导入 xlsx 模板，支撑目录批量维护前置步骤。
- **产品契约权威**：`AS-BAS-CAT-002` §3（三 Sheet、列清单、模板版本 `v1`）。
- **边界说明**：只生成或返回模板流，不写业务目录表。
- **查询范围（读库，无写入）**：术语码表灌入「码表参照」Sheet（以应用编排为准）。
- **过滤口径**：当前支持的模板版本（本期仅 v1）。
- **组装规则**：xlsx 字节流（`Content-Disposition` 附件）。
- **返回约束**：权限与审计遵循应用层网关。

#### 6.2.5 导出药品目录（`listMedicationCatalogForExport` / 应用 `exportMedicationCatalog`）

- **业务意义**：按筛选条件导出目录数据，支撑盘点、对码、改表后按导入模板重导。
- **边界说明**：领域只读加载详情投影；xlsx 列序/中文下拉/维护态价回填由应用层 `MedicationCatalogAppService#exportMedicationCatalog`（**IF-BAS-CAT-EXP-01**）编排，不在领域内写库或生成 POI。
- **查询范围（读库，无写入）**：`listMedicationCatalogForExport(MedicationDefinitionCatalogPageQuery, maxRows)` → `List<MedicationDefinitionDetailQueryModel>`；谓词与维护列表分页一致（未删除 + 分类 + 关键字）。
- **过滤口径**：`keyword`、`categoryCode` 与 `pageMedicationCatalog` 同口径（含可选 `statusEnum`）；条数上限 `EXPORT_MAX_ROWS`（默认 3000，应用层多取 1 条判定截断）。
- **组装规则（应用层）**：导出为**单 Sheet**（表头 + 数据紧挨）；**首列编号（busNo）** + 其后列名对齐导入模板 **v1.6** `COLUMN_HEADERS`；术语写中文 label；维护态 `PURCHASE`/`SALE`/`LIMIT` 经 `findPriceLinesByProjects` 批量回填。**不是**导入模板复刻（无指导页/示例/下拉/_sys），不可直接当 IMP-02 上传。
- **返回约束**：`{ fileName, contentType, fileBytes, exportedRowCount, truncated }`；大数据量截断时 `truncated=true`，前端提示缩小筛选。
- **兼容**：窄字段 `exportMedicationCatalog()` → `List<MedicationDefinitionExportRowQueryModel>` 仍保留，内部委托 `listMedicationCatalogForExport`。

#### 6.2.6 采购选品分页（`pageForPurchase`）

- **业务意义**：采购/进货选品读 `med_medication_definition`（**AS-INV-WHS-001** 等）；谓词对齐原产品读模型采购分支。
- **入参**：`ProductDefinitionPurchasePageQuery` + `Pageable`。
- **出参**：`Page<MedicationDefinition>` 实体分页。

#### 6.2.7 医嘱开立目录分页（`pageForCatalog`）

- **业务意义**：医生站 IF-DOR-11 / IF-IP-DOR-11 药品分支；由应用 `ProductDefinitionCatalogPageLoader` 编排。
- **边界说明**：内部委托 `pageForPurchase`（谓词与采购分支一致）。
- **出参**：`Page<MedicationDefinition>`。

#### 6.2.8 编辑前门控（`validateMedicationCatalog`）

- **业务意义**：主键为空或记录已 **DISCARDED** 时不可进入编辑流。
- **出参**：`boolean`。
- **应用层补充**：`MedicationCatalogAppService#validateMedicationCatalogEdit` 返回 `CatalogEditabilityQueryModel`（`OK` / `LOCK_CLINICAL` / `LOCK_INVENTORY`）；写侧 `CatalogEditWriteGuardsupport 强制校验分类/单位/拆零倍数/维护态价格不可越权修改（见 §9.1）。

#### 6.2.9 解析目录类型码（`resolveCategoryCodeByMedicationDefinitionId`）

- **业务意义**：**MED-003** FIFO 签发等读侧解析 `category_code`。
- **出参**：类型码；未命中 `null`。

#### 6.2.10 按主键批量加载（`findAllByIds`）

- **业务意义**：医嘱列表预热、批量展示名解析。
- **出参**：`List<MedicationDefinition>`；空入参→空列表。

#### 6.2.11 按机构+业务编码批量加载（`findByOrgIdAndBusNos`）

- **业务意义**：外部目录同步幂等 upsert 前置查询。
- **出参**：`List<MedicationDefinition>`。

#### 6.2.12 按类型编码解析主键（`listIdsByCategoryCodesIn`）

- **业务意义**：库存明细按 `category_code` 筛选（禁止 inventory 包 Subquery 药品主表）。
- **出参**：`List<Long>` 主键列表。

#### 6.2.13 按医保等级解析主键（`listIdsByChrgitmLvIn`）

- **业务意义**：库存明细按 `chrgitm_lv` 筛选。
- **出参**：`List<Long>` 主键列表。

#### 6.2.14 按重点药类型解析主键（`listIdsByKeyDrugType`）

> **列表筛选口径**：药品目录与库存明细筛选项「重点药类型」入参 `keyDrugType`，枚举 `KeyDrugType`（`BASIC` / `NATIONAL_MONITOR` / `CENTRAL_PURCHASE` / `ANTIBIOTIC` / `NARCOTIC` / `PSYCH_ONE` / `PSYCH_TWO`）。前四类选中即筛对应布尔标识为 `true`；后三类筛 `pharmacology_category_code` 等值（术语 `medicine_properties`：麻醉 `2` / 精一 `4` / 精二 `5`）。

- **业务意义**：库存明细 / **AS-RPT-011** / 发药报表药品性质 / 处方点评特殊药等按重点药类型预解析目录主键（B.6.4：禁止他聚合 JOIN 药典表）。`keyDrugTypeCode` 对齐 `KeyDrugType`：`BASIC`→`basic_flag`；`NATIONAL_MONITOR`→`national_monitor_flag`；`CENTRAL_PURCHASE`→`central_purchase_flag`；`ANTIBIOTIC`→`antibiotic_flag`；`NARCOTIC`/`PSYCH_ONE`/`PSYCH_TWO`→`pharmacology_category_code`。仅未删除。
- **边界说明**：空/未知 code 返回空列表（表示无命中，而非「不限」）。
- **出参**：`List<Long>` 主键列表。
- **实现锚点**：`MedicationDefinitionQueryService#listIdsByKeyDrugType`。

#### 6.2.15 按结余留用批次号解析主键（`listIdsBySurplusRetentionBatchCode`）

- **业务意义**：**AS-RPT-015** 药品结余留用报表按药典 `surplus_retention_batch_code` 等值预解析目录主键（B.6.4：禁止他聚合 JOIN 药典表）。
- **边界说明**：仅本聚合 Repository；未删除；空/空白批次号返回空列表（表示无命中）。
- **入参**：`surplusRetentionBatchCode`（String）。
- **出参**：`List<Long>` 主键列表。
- **实现锚点**：`MedicationDefinitionQueryService#listIdsBySurplusRetentionBatchCode`。

### 6.3 领域边界说明

- `MedicationDefinition` 只描述院内药品目录主数据及目录生命周期动作；科室/货位、批号、效期等同表列若存在，归属发药/库存等上下文，不在本聚合内编排写入。
- 收费定价实例（`ChargeItemDefinition` 与 `instanceTable = med_medication_definition`）由收费域编排同步；本聚合通过事件/应用层协作，不直接维护收费明细。
- 页面初始化中的多域编排、导入事务与跨域业务码裁剪以应用服务为准；**只读查询能力**以 §6.2 `MedicationDefinitionQueryService` 所列方法为目录域读侧入口。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-MDF-001 | 同一机构内业务编码（`busNo`）唯一 | 药品目录的唯一性约束，防止重复建档 |
| BR-MDF-002 | 创建时药品名称、药品分类、包装单位不可为空 | 目录主数据最低完整性要求 |
| BR-MDF-003 | 状态迁移须遵循 §2.3 状态机，不允许跳跃迁移 | 如已废弃不可回退，草稿不可直接停用 |
| BR-MDF-004 | 名称或商品名变更时须同步重算拼音码与五笔码 | 保证检索码与显示名一致 |
| BR-MDF-005 | 已发布的药品定义须绑定有效的 ChargeItemDefinition 实例 | 避免"有目录无价"导致计费异常 |
| BR-MDF-006 | 拆零比（`partPercent`）必须大于 0 | 包装单位与最小单位的换算比不允许为零或负数 |
| BR-MDF-007 | 目录维护用例不得覆盖他域事实字段（科室/货位、批号、效期等） | 同表存储但领域边界不扩大，避免跨域写冲突 |
| BR-MDF-008 | 已被用药申请或发药记录引用的药品定义不可直接废弃 | 保护历史业务数据的引用完整性 |
| BR-MDF-009 | 跨聚合引用以药品定义标识(MedicationDefinitionId)为稳定关联 | 医嘱、收费、发药、采购统一以主键引用，不在本聚合内维护其生命周期 |
| BR-MDF-010 | 编辑前强校验由应用层编排他域判定，本聚合不实现跨域校验 | 用药侧闭环锁定/库存侧锁定的字段可编辑矩阵由应用服务按业务码裁剪（§9.3） |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 供应商管理 | Supplier | `供应商标识(SupplierId)` | 药品生产厂家与供应商，创建时可选关联 |
| 术语与编码 | TerminologyCodeMapping | 编码引用 | 药品分类、剂型、单位、频次、给药途径、病案结算类型（`mr_settlement_type_code`）、结余留用批次号（`surplus_retention_batch_code`）等码表 |
| 组织架构 | Organization | `机构标识(OrgId)` | 药品目录按机构隔离 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 收费定价 | ChargeItemDefinition | 编排同步 | 创建/编辑时同步维护收费定价实例（`instanceTable = med_medication_definition`） |
| 产品目录混页 | `MedicationDefinitionQueryService` | 应用编排 | `pageForCatalog` / `findAllByIds` 直读 `med_medication_definition` |
| 用药申请 | MedicationRequest | 只读引用 | 医嘱开立时引用药品定义标识(MedicationDefinitionId) |
| 药品发放 | MedicationDispense | 只读引用 | 发药时引用药品定义标识(MedicationDefinitionId) |
| 采购与库存 | SupplyRequest / InventoryItem | 只读引用 | 以 `itemTable = med_medication_definition` 作为产品类型键；单价经 **`ChargeItemDefinition`** 取价——**库房供应单据读锚**仅 **`PURCHASE` + `LOT_NUMBER_COST`**（**`DM-ADM-010` §1.5**） |

### 8.3 引用规则

> **统一规则**：所有需要引用药品定义的领域模型，统一使用 `药品定义标识(MedicationDefinitionId)` 作为关联字段，不直接持有聚合对象引用。消费方通过该标识在各自上下文内查询所需的药品信息。

### 8.4 事件流示意图

```
MedicationDefinition          ChargeItemDefinition        MedicationDefinitionQueryService（目录读）
      │                              │                            │
      │── Created/Updated ──────────>│── 同步定价实例               │
      │                              │                            │
      │── Created/Updated/Retired ──────────────────────────────>│── 刷新投影
      │                              │                            │
      │                   MedicationRequest                 MedicationDispense
      │                        │                                 │
      │<── 只读引用(id) ────────│                                 │
      │<── 只读引用(id) ────────────────────────────────────────│
```

---

## 9. 补充

### 9.1 字段可编辑矩阵（产品规格）

编辑态下，字段是否允许修改取决于 **editabilityCode**（由 `CatalogEditabilityResolvesupport 跨聚合判定，写侧由 `CatalogEditWriteGuardsupport 强制校验）：

| 业务码 | 触发条件（whale 口径） |
|---|---|
| `OK` | 无有效 `MedicationRequest`；且无 `SupplyRequest` 行 `item_id=药品主键` 且 `status_enum ∈ {APPROVAL, AGREE}` |
| `LOCK_CLINICAL` | 存在**任一**未删 `MedicationRequest`（且未命中入库码） |
| `LOCK_INVENTORY` | 存在上述 `SupplyRequest` 入库事实（**优先于**临床码） |

| 字段 | OK | LOCK_CLINICAL | LOCK_INVENTORY |
|---|---|---|---|
| 业务编码 busNo | 只读 | 只读 | 只读 |
| 通用名称 / 商品名称 | 可编辑 | **锁定** | **锁定** |
| 药品性质 pharmacologyCategoryCode | 可编辑 | **锁定** | **锁定** |
| 药品分类 / 财务类别 / 医保类别 / 单位 / 拆零比 | 可编辑 | **锁定** | **锁定** |
| 规格 totalVolume | 可编辑 | 可编辑 | 可编辑 |
| 剂型 / 剂量单位 | 可编辑 | **锁定** | 可编辑 |
| 剂量换算比 doseUnitRatio | 可编辑 | 可编辑 | 可编辑 |
| 采购参考价 / 销售参考价（维护态定价行） | 可编辑 | **锁定** | **锁定** |
| 拆分属性 partAttributeEnum / thoPartAttributeEnum | 可编辑 | 可编辑 | 可编辑 |
| 医保等级 chrgitmLv | 可编辑 | 可编辑 | 可编辑 |
| 病案结算类型 mrSettlementTypeCode | 可编辑 | 可编辑 | 可编辑 |
| 结余留用批次号 surplusRetentionBatchCode | 可编辑 | 可编辑 | 可编辑 |
| 最高零售价 | 可编辑 | 可编辑 | 可编辑 |
| 厂家 / 批准文号等 | 可编辑 | 可编辑 | 可编辑 |

应用层接口：`validateMedicationCatalogEdit` / 详情 DTO 回填 `editabilityCode`+`editabilityMessage`；`editMedicationCatalog` 写前执行 immutable 字段断言。

**批号真实价格查询（维护页辅读）**：`MedicationCatalogAppService#listMedicationLotPrices` —— 按药品主键查询当前 ACTIVE 批号维 **`LOT_NUMBER_COST`（进价）** / **`LOT_NUMBER_PRICE`（售价）**；与详情中的采购参考价/销售参考价分离。`lotNumber` 可空（空=该药品全部批号）。

**联调文档**：`AS-BAS-CAT-001-三大目录编辑门禁.md`；前端维护页见同 AS §1.4 与 `AS-BAS-CAT-001-SDD` §5。

> 业务码判定归属他域 QueryService，本聚合不实现跨域校验逻辑（BR-MDF-010）。

### 9.2 DDD（日限定剂量）管理

限定日剂量（Defined Daily Dose, DDD）是 WHO 定义的药物消耗统计度量单位：

- `dddCode`：DDD 分类编码，对应 WHO ATC 分类下的日限定剂量标准
- `dddUnitCode`：DDD 计量单位编码
- 用于抗菌药物使用强度计算（DDDs）、药物经济学分析等场景
- DDD 值由药学部维护，不参与临床用药决策

> **落库说明**：`ddd_code` / `ddd_unit_code` 等列定义见 §3.1；本节仅阐述 DDD 业务语义与使用场景。

### 9.3 定价计量与库房读锚

与 **`DM-ADM-010` §1.5** 对齐：

- 目录维护写入 **`ChargeItemDefinition`** 时，**`PURCHASE`（采购参考价）**、**`SALE`（销售参考价）** 的 **`priceValue` 均为每包装单位单价**；字段 JSON 名仍为 `purchasePrice` / `retailPrice`（仅文案口径为参考价）。
- **落库刻度**：售价类（SALE/LIMIT/`LOT_NUMBER_PRICE`）经 **`MonetaryAmountSupport#normalizeDefinitionPriceValue`** / `scaleSaleUnitPrice` 落 **2 位**；进价类（PURCHASE/`LOT_NUMBER_COST`）非中药 2 位、中药 ≤3 位；库列仍 `numeric(19,6)`（**BR-MON-001**）。
- **维护页 UI 刻度（中药饮片）**：当 **`medication_category_code=4`（中草药）** 时，**采购参考价（进价）**允许 **展示与录入 3 位小数**（常量 `CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE` / 前端 `MONEY_TCM_UNIT_PRICE_DP`）；**销售参考价（售价）仍为 2 位**。西药/中成药等仍为 **2 位**展示。UI 3 位**不得**反推为售价或替代结算 2 位。批号进价 / 库房进价查询展示同进价三位口径；批号售价与收费不受影响。
- **真正售卖/进货价**在批号维：**`LOT_NUMBER_COST`（进价）**、**`LOT_NUMBER_PRICE`（售价）**；目录页通过 `listMedicationLotPrices` 查询（**当前价**，BR-CHGDEF-QRY-004）。
- **库房供应单据**（进货/出库/盘点/药房入库）选品与详情槽价计算之**读锚仅两档**：无批号取 **`PURCHASE`**，有批号取 **`LOT_NUMBER_COST`**。
- 进货/期初同意后写入 **`LOT_NUMBER_COST`**（进价刻度）与 **`LOT_NUMBER_PRICE`（售价恒 ≤2 位占位）**；中草药同事务 AOP 再将批号售价覆盖为进价×1.25（两位）。包装价÷比除不尽**不**拒绝维护——收费侧 2 位 HALF_UP / BR-MON-PKG-001 消化。

### 9.4 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.1.18 | 2026-10-10 | 代码先行回写 §9.3：售价落库/批号售价恒 2 位；中药仅进价 3 位；进货/期初售价占位不得写三位进价；除不尽不拦维护（对齐 BR-MON-001 / AS-BAS-CAT-001 BR-CAT-011） |
| v1.1.17 | 2026-10-07 | 代码先行回写 §6.1.6：导入判重改为旧编码优先，否则名称+规格+厂家主键；文件内同键只保留首行（对齐 AS-BAS-CAT-002 v1.6） |
| v1.1.16 | 2026-09-22 | §6.2.14：`KeyDrugType` 扩展麻醉/精一/精二（`pharmacology_category_code`）；§9.1：规格 `totalVolume` 锁定态改为可编辑（对齐 AS-BAS-CAT-001 BR-CAT-013） |
| v1.1.15 | 2026-09-18 | §9.1：剂量换算比 `doseUnitRatio` 锁定态改为可编辑（对齐 AS-BAS-CAT-001 BR-CAT-012） |
| v1.1.14 | 2026-09-10 | §6.2.15：`listIdsBySurplusRetentionBatchCode`（**AS-RPT-015**） |
| v1.1.13 | 2026-09-10 | §9.1：病案结算类型锁定态改为可编辑（对齐 AS-BAS-CAT-001 BR-CAT-008） |
| v1.1.12 | 2026-09-10 | §3 / §8 / §9.1：增补结余留用批次号 `surplus_retention_batch_code`（术语同名 title；维护页可选；锁定态仍可改） |
| v1.1.11 | 2026-08-21 | §6.2.14：`listIdsByKeyDrugType`（库存筛选 / **AS-RPT-011**） |
| v1.1.10 | 2026-08-19 | §3：`trace_unit_*` 仅追溯域（应扫码数/主档剩余）；发药/退药扣加库存走发放 **`unit_code`→五类拆零倍数**（**AS-OP-PHA-001 BR-OPP-017**） |
| v1.1.9 | 2026-08-03 | §9.3：中药饮片维护态进售价 UI 允许 3 位小数（落库仍 6 位；对齐 BR-MON-001） |
| v1.1.8 | 2026-07-17 | §9.1：名称/药品性质锁定；拆分属性与医保等级可改；维护态 SALE 文案改为销售参考价 |
| v1.1.7 | 2026-07-15 | §3：`item_min/max_quantity` 明确为**库存单位**；供库存明细预警目录优先（**AS-INV-WHS-004 BR-024**） |
| v1.1.6 | 2026-07-15 | §9.1/§9.3：维护态 PURCHASE/SALE 文案改为采购参考价/零售参考价；增补 `listMedicationLotPrices` 批号进价/售价查询 |
| v1.1.5 | 2026-07-14 | §3 / §9.3 增补病案结算类型 `mr_settlement_type_code`（三大目录共用术语） |
| v1.1.4 | 2026-07-11 | §6.1.7 增补 CHS 跨模块消费（`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService`） |
| v1.1.3 | 2026-07-04 | **§9.1**：业务码触发条件按药品域收窄；补充 AS-BAS-CAT-001 联调引用 |
| v1.1.2 | 2026-05-29 | **§9.3 定价计量与库房读锚**：对齐 **`DM-ADM-010` §1.5**；§8.2 采购与库存取价说明 |
| v1.1.1 | 2026-05-28 | **M1**：§6.2.6～13 补 `pageForPurchase`/`pageForCatalog`/批量读法；§6.2.1 双重重载对齐 Java |
| v1.1 | 2026-05-28 | ReadModel 废止：§6.2 / §8.2 改 **`MedicationDefinitionQueryService`**；删除 **ProductDefinition** RM 表述 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`MedicationDefinitionDomainService`

| 方法 |
|------|
| `createMedicationDefinition` |
| `editMedicationDefinition` |
| `retireMedicationDefinitions` |
| `activateMedicationDefinitions` |
| `discardMedicationDefinition` |
| `importMedicationCatalog` |
| `syncInsuranceCatalogMapping` |

### QueryService：`MedicationDefinitionQueryService`

| 方法 |
|------|
| `findOptionalMedicationDefinitionEntityById` |
| `pageMedicationCatalog` |
| `pageForPurchase` |
| `pageForCatalog` |
| `getMedicationCatalog` |
| `getMedicationInit` |
| `downloadMedicationCatalog` |
| `validateMedicationCatalog` |
| `resolveCategoryCodeByMedicationDefinitionId` |
| `exportMedicationCatalog` |
| `listMedicationCatalogForExport` |
| `findAllByIds` |
| `findByOrgIdAndBusNos` |
| `findByBusNos` |
| `listIdsByCategoryCodesIn` |
| `listIdsByChrgitmLvIn` |
| `listIdsByInventoryDetailKeyword` |
| `findExistingDuplicateKeyTokens` |
| `listIdsByInjectFlag` |
| `mapKeyDrugFlagsByIds` |
| `listIdsByKeyDrugType` |
| `listIdsBySurplusRetentionBatchCode` |

