# DM-ADM-008 DeviceDefinition 器材定义

> **限界上下文**：管理域 · 器材目录主数据  
> **聚合类型**：实体聚合（单表）  
> **版本**：v1.17（规格锁定态可改）

---

## 1. 概述

### 1.1 业务定义

器材定义（DeviceDefinition）用于维护院内耗材/器材目录主数据，承载编码、名称、包装、库存预警、医保标识、厂家供应商等信息，供医嘱、发放、收费等下游业务引用。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 目录主数据维护 | 维护器材基础档案及检索字段 |
| 发布状态管理 | 通过 `statusEnum` 管理可用/停用状态（主路径） |
| 收费与医保协同锚点 | 提供收费实例和医保对码所需基础属性 |
| 下游引用主键提供 | 供 `DeviceRequest`、`DeviceDispense` 等业务通过 `id` 引用 |
| 收费实例锚点 | 为 `ChargeItemDefinition` 提供 `instanceTable + instanceId` 绑定锚点 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 目录管理 | 器材目录新增、编辑、启停、导入导出 |
| 下游引用 | 发放、收费、库存联动前的目录信息读取 |
| 医保对码 | 医保目录对照后的标志回写 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，聚合边界以主表主键 `id`（器材定义标识）划分。

**表结构**：
- 主表：`adm_device_definition`（主键：`id`）
- 索引：`bus_no`（业务编码）、`category_code`（器材分类）、`type_code`（器材种类）、`status_enum`（状态）

**高频查询模式**：
- 按器材定义标识查询：主表主键查询
- 按业务编码查询：`bus_no` 索引扫描
- 按器材分类筛选：`category_code` 索引扫描
- 按器材种类筛选：`type_code` 索引扫描
- 按状态筛选：`status_enum` 索引扫描

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 器材定义标识 | `Long` | 主键 `id` |

### 2.3 状态机

```
  [DRAFT] ──发布──> [ACTIVE] ──停用──> [RETIRED]
    │                                 │
    │                                 └──重新启用──> [ACTIVE]
    │
    └──> [UNKNOWN]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | DRAFT | 初始状态 | 当前目录新增默认直接写入启用，允许发布、归档 |
| 2 | ACTIVE | 可用于业务引用 | 可用于医嘱、发放、收费等业务引用，允许编辑、停用 |
| 3 | RETIRED | 不可新建业务引用 | 历史记录保留，允许重新启用 |
| 4 | UNKNOWN | 兼容状态 | `DRAFT`、`UNKNOWN` 在写侧未直接使用 |

> 状态码来自 `PublicationStatus` 枚举，目录管理主路径采用 `ACTIVE/RETIRED`。

---

## 3. 实体

### 3.1 器材定义（DeviceDefinition）

DeviceDefinition 聚合根的属性定义，对应 `adm_device_definition` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 器材定义标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 器材业务编码 | `bus_no` | `String` | 是 | 院内唯一业务编码 |
| 器材名称 | `name` | `String` | 是 | 器材名称 |
| 商品名称 | `merchandise_name` | `String` | 否 | 独立商品名称（用于目录展示与检索，不再与 `name` 共列） |
| 拼音码 | `py_str` | `String` | 否 | 辅助检索 |
| 五笔码 | `wb_str` | `String` | 否 | 辅助检索 |
| 器材分类编码 | `category_code` | `String` | 否 | 器材大类编码（术语 `device_category_code`）；**应用层维护页必填** |
| 器材种类编码 | `type_code` | `String` | 否（库列可空） | 器材细类编码（术语 `device_type_code`）；**取药科室模板中间维**（`DM-CUS-006`）依赖本字段做同大类拆房；**应用层维护页 / 导入必填**（无明确种类时选 `other`=其他） |
| 采购单位 | `purchase_unit_code`（`purchaseUnitCode`） | `String` | 否 | 五单位口径中的采购单位 |
| 包装规格 | `total_volume`（`totalVolume`） | `String` | 否 | 包装规格**描述文本**（与药品 `total_volume` 语义对齐，如「100片/盒」）；**不得**写入单位中文展示名或单位 code |
| 最小库存警戒量 | `item_min_quantity` | `BigDecimal` | 否 | 最低警戒（**库存单位**）；低于此值预警；**null**=未维护，库存预警回落系统设置（**AS-INV-WHS-004 BR-INV-DTL-024**） |
| 最大库存警戒量 | `item_max_quantity` | `BigDecimal` | 否 | 最高警戒（**库存单位**）；高于此值预警；**null**=未维护 |
| 拆零单位 | `split_unit_code`（`splitUnitCode`） | `String` | 否 | 五单位口径中的拆零单位（相对倍数锚点，倍数为 1） |
| 默认门诊/就诊单位 | `def_encounter_unit_code` | `String` | 否 | 默认门诊开单单位编码 |
| 默认住院单位 | `def_inhospital_unit_code` | `String` | 否 | 默认住院开单单位编码 |
| 所属科室标识 | `org_id` | `Long` | 否 | 关联科室 |
| 所在位置标识 | `location_id` | `Long` | 否 | 关联位置 |
| 产品型号 | `model_number` | `String` | 否 | 产品型号 |
| 高值器材标志 | `hvcm_flag` | `Boolean` | 否 | `true`=高值耗材 |
| 是否收费耗材 | `chargeable_flag`（`chargeableFlag`） | `Boolean` | 否 | `true`=收费耗材；与界面/导入列「收费耗材」勾选同向；仅耗材目录维护；导入模板 **v1.4** 空白默认 `true`（对齐新建默认勾选） |
| 病案结算类型 | `mr_settlement_type_code`（`mrSettlementTypeCode`） | `String` | 否 | 病案首页费用结算分类；术语体系 `mr_settlement_type_code`（三大目录共用） |
| 包装单位 | `package_unit_code`（`packageUnitCode`） | `String` | 否 | 五单位口径中的包装/销售单位 |
| 库存单位 | `stock_unit_code`（`stockUnitCode`） | `String` | 否 | 五单位口径中的库存单位 |
| 剂量单位 | `dose_unit_code`（`doseUnitCode`） | `String` | 否 | 五单位口径中的剂量单位；**维护界面不展示**，写路径未传时由应用层默认回填为 `splitUnitCode` |
| 剂量→拆零换算比 | `dose_unit_ratio`（`doseUnitRatio`） | `BigDecimal` | 否 | 剂量单位相对拆零单位换算比；**维护界面不维护**，写路径未传时由应用层默认 `1` |
| 采购→拆零换算比 | `purchase_unit_ratio`（`purchaseUnitRatio`） | `BigDecimal` | 否 | 采购单位相对拆零单位换算比 |
| 库存→拆零换算比 | `stock_unit_ratio`（`stockUnitRatio`） | `BigDecimal` | 否 | 库存单位相对拆零单位换算比 |
| 包装→拆零换算比 | `package_unit_ratio`（`packageUnitRatio`） | `BigDecimal` | 否 | 包装单位相对拆零单位换算比 |
| 追溯码贴码单位 | `trace_unit_code`（`traceUnitCode`） | `String` | 否 | 追溯码所贴包装单位；可游离于五类业务单位；**可空**；有码入库时须已维护。**不参与**发药/退药对 `wkf_inventory_item` 的扣加换算（同 **DM-MED-001** / **BR-OPP-017**） |
| 追溯码→拆零换算比 | `trace_unit_ratio`（`traceUnitRatio`） | `BigDecimal` | 否 | 1 个追溯码单位对应的拆零数量；与贴码单位成对；有值时须 &gt; 0。仅应扫码数、主档剩余盖住等**追溯域**使用 |
| 批准文号 | `approval_number`（`approvalNumber`） | `String` | 否 | 批准文号 |
| 批准文号 | `approval_number` | `String` | 否 | 注册证/批准文号 |
| 医保标记 | `yb_flag` | `Boolean` | 否 | 是否纳入医保 |
| 医保编码 | `yb_no` | `String` | 否 | 医保目录编码 |
| 医药机构目录编码 | `yb_org_no` | `String` | 否 | 医药机构目录编码 |
| 医保对码标记 | `yb_match_flag` | `Boolean` | 否 | 是否完成医保对码 |
| 医保等级 | `chrgitm_lv` | `Integer` | 否 | 医保收费等级 |
| 发布状态 | `status_enum` | `String` | 是 | 存 `PublicationStatus.code` |
| 生产厂家标识 | `manufacturer_id` | `Long` | 否 | 关联生产厂家主数据 `cus_supplier.id`（`SupplierType.MANUFACTURER` / code=`1`）；展示名由应用层解析供应商名称，**不再**持久化 `manufacturer_text` |
| 供应商标识 | `supply_id` | `Long` | 否 | 关联供应商 |
| 说明 | `description` | `String` | 否 | 器材说明 |
| 适用范围 | `jurisdiction` | `String` | 否 | 适用范围描述 |
| 器材版本 | `version` | `String` | 否 | 版本号 |
| 主要成分 | `substance_text` | `String` | 否 | 主要成分描述 |
| 过敏标记 | `allergen_flag` | `Boolean` | 否 | 是否含过敏原 |
| 处方标志 | `rx_flag` | `Boolean` | 否 | 是否处方类器材 |
| 启用标记 | `active_flag` | `Boolean` | 是 | 兼容列，展示与筛选口径使用 |
| 医保对码标识 | `yb_mapping_code` | `String` | 否 | 兼容列，承接存量调用方 |

> 审计、租户、删除标记字段继承 `HisBaseEntity`，不在此表重复展开。

**术语体系（勿混淆）**

| 聚合属性 | 库列 | 术语体系（`ter_code_system.name`） | 说明 |
|---|---|---|---|
| `category_code` | `category_code` | `device_category_code` | 耗材**分类** |
| `type_code` | `type_code` | `device_type_code` | 耗材**种类/类型**（与财务类型 `fin_type_code` 无关） |
| `mr_settlement_type_code` | `mr_settlement_type_code` | **`mr_settlement_type_code`** | 病案结算类型（与药品/诊疗目录同码表） |
| `purchase_unit_code` / `split_unit_code` / `package_unit_code` / `stock_unit_code` / `dose_unit_code` / `def_encounter_unit_code` / `def_inhospital_unit_code` | 同左 | **`unit_code`** | 五类计量单位及默认开单单位编码；维护页下拉与列表 `*_dictText` **统一**本码表（与药品目录 `unitCodeOptions` 同口径）。**禁止**按 `purchase_unit`、`split_unit`、`dose_unit` 等分码表各选各的。 |

**主表已移除的冗余列（与 DM 权威字段对齐）**

以下列**不再存在于** `adm_device_definition`（数据库迁移 **V30** 级删除；定价与财务/医保类型以 **`adm_charge_item_definition`** 实例维度维护为准）：`item_type_code`、`yb_type`、`purchase_price`、`retail_price`、`maximum_retail_price`。目录应用接口仍可将采购参考价/零售参考价/限价及财务类型等写入**收费定价行**，不落回器材主表。

**五类计量单位贯通口径（应用 → 领域 → 持久化，与药品目录对齐）**

- **术语与下拉**：`DeviceCatalogAppService#init` 填充 `DeviceCatalogInitDto.unitCodeOptions`（`unit_code`）；五类单位 Select 共用该列表。剂量候选单独 `doseUnits`（`dose_unit`）。
- **HTTP / 应用请求**：`CreateDeviceCatalogRequest`、`UpdateDeviceCatalogRequest` 单位主字段为 **`purchaseUnitCode`**、**`splitUnitCode`**、**`packageUnitCode`**、**`stockUnitCode`** 及采购/库存/包装三类换算比（`purchaseUnitRatio`、`stockUnitRatio`、`packageUnitRatio`）。**不再**接受/回显历史别名 `unitCode` / `minUnitCode`。`doseUnitCode` / `doseUnitRatio` 仍映射实体列，但**维护界面不展示、不维护**。
- **维护页必填（应用层 `DeviceCatalogAppService` 创建与更新）**：`name`（耗材名称）、`categoryCode`（耗材分类）、`typeCode`（耗材种类，术语 `device_type_code`；无明确路由种类时选 `other`）、`itemTypeCode`（财务类型）、`totalVolume`（包装规格）、采购/拆零/包装/库存单位及其相对拆零倍数、`purchasePrice`（采购参考价）、`retailPrice`（零售参考价）。
- **维护页选填**：`manufacturerText`（生产厂家）及 `manufacturerId`；供应商、型号等其余非列必填字段。
- **剂量单位默认（`normalizeDoseUnitDefaultsForWrite`）**：请求未传 `doseUnitCode` 时回落为 `splitUnitCode`；未传 `doseUnitRatio` 时回落为 `1`；编辑时若前端不回传剂量字段，合并原详情后保留库内原值。
- **单位与换算比校验（创建与更新）**：采购、拆零、包装、库存单位均非空；采购/库存/包装三类换算比非空且 **> 0**（拆零单位为倍数锚点，其相对倍数为 1 的逻辑由前端/业务填写保证）。**不再**对剂量单位与剂量倍数做界面必填校验。
- **定价**：`purchasePrice`、`retailPrice` 必填，经 `replacePriceLines` 写入 **`adm_charge_item_definition`**（`PriceType.PURCHASE` / `SALE`，维护页展示为**采购参考价 / 零售参考价**）；**`priceValue` 均为每包装单位单价**（与 **`DM-ADM-010` §1.5** 一致），不落回器材主表；`maximumRetailPrice` 仍选填。**真正进价/售价**在批号维 **`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`**，维护页辅读接口 **`DeviceCatalogAppService#listDeviceLotPrices`**（当前 ACTIVE 价；`lotNumber` 可空）。**库房供应单据读锚**仅 **`PURCHASE`**（无批号）与 **`LOT_NUMBER_COST`**（有批号）；**`LOT_NUMBER_PRICE`** 由进货同意写入、库房读路径不消费。
- **包装规格**：`totalVolume` 为规格描述文本，**不得**将单位展示名（如「片」）或单位 code 写入 `total_volume`。
- **命令映射**：`DeviceCatalogMapper` 将 `purchaseUnitCode` → `CreateDeviceDefinitionCommand.Item.unitCode`、`splitUnitCode` → `minUnitCode`（领域命令字段名保留；语义=采购/拆零）；`packageUnitCode` / `stockUnitCode` / `doseUnitCode` 与换算比直映实体列。
- **领域写侧**：`DeviceDefinitionDomainService#createDeviceDefinition` / `updateDeviceDefinition` 写入 `purchase_unit_code`、`split_unit_code`、`package_unit_code`、`stock_unit_code`、`dose_unit_code` 及四类 ratio 列。
- **查询展示**：`DeviceCatalogDto` 仅回显 `purchaseUnitCode`、`splitUnitCode` 及对应 `*_dictText`（及包装/库存/剂量等）；**不再**双写 `unitCode` / `minUnitCode`。字典文案统一经 **`unit_code`** 解析。
- **更新事件**：`changedFields` 与实体 Java 属性名对齐（如 `purchaseUnitCode` 变更记 `purchaseUnitCode`，常量键见 `FieldNameConstants.PURCHASE_UNIT_CODE`）。

### 3.2 聚合关系

```text
DeviceDefinition(目录定义)
  ├─ 被 DeviceRequest 引用（deviceDefId）
  ├─ 被 DeviceDispense 引用（deviceDefId）
  └─ 通过 instanceTable+instanceId 与 ChargeItemDefinition 关联
```

---

## 4. 枚举与值对象

### 4.1 发布状态（PublicationStatus）

| 编码值 | 英文 | 说明 |
|---|---|---|
| 1 | `DRAFT` | 草稿 |
| 2 | `ACTIVE` | 启用 |
| 3 | `RETIRED` | 停用 |
| 4 | `UNKNOWN` | 未知 |

> **存储口径**：对应主表字段 `status_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.2 标记类字段枚举口径

`hvcmFlag`、`chargeableFlag`、`ybFlag`、`ybMatchFlag`、`allergenFlag`、`rxFlag` 在应用层可按 `Whether` 语义封装；聚合字段存储为 `Boolean`。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 器材定义已创建 | `DeviceDefinitionCreatedEvent` | 新增目录后 | `id,busNo,name,statusEnum` | 产品目录QueryService、收费实例同步 |
| 器材定义已更新 | `DeviceDefinitionUpdateEvent` | 编辑目录后 | `id,changedFields`（最小载荷；含业务字段名摘要，如 `stockUnitCode`、`purchaseUnitCode` 等，与 `CommonConstants.FieldName` 对齐） | 产品目录QueryService、检索缓存 |
| 器材定义已停用 | `DeviceDefinitionStopEvent` | 批量停用后 | `id,statusEnum` | 发放候选过滤、目录列表 |
| 器材定义已启用 | `DeviceDefinitionStartEvent` | 批量启用后 | `id,statusEnum` | 发放候选过滤、目录列表 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 新建目录 | `ACTIVE` | `DeviceDefinitionCreatedEvent` |
| `ACTIVE`/`RETIRED` | 维护目录信息 | 原状态保持 | `DeviceDefinitionUpdateEvent` |
| `ACTIVE` | 批量停用 | `RETIRED` | `DeviceDefinitionStopEvent` |
| `RETIRED` | 批量启用 | `ACTIVE` | `DeviceDefinitionStartEvent` |
| `ACTIVE`/`RETIRED` | 批量导入落库（更新） | 原状态或按导入值更新 | `DeviceDefinitionUpdateEvent` |
| — | 批量导入落库（新增） | `ACTIVE` | `DeviceDefinitionCreatedEvent` |

> 投影约束：`DeviceDefinitionUpdateEvent` 仅承载最小载荷，领域事件后查询刷新不得假设事件含完整字段；应按 `aggregateId` 回源查询 `adm_device_definition` 快照再执行字段级投影。

---

## 6. 领域服务

### 6.1 写侧领域服务（DeviceDefinitionDomainService）

#### 6.1.1 新建器材定义（`createDeviceDefinition`）
- **业务意义**：创建新的器材目录主档，形成可被收费与临床引用的主数据锚点。
- **边界说明**：只维护目录主档，不在本聚合内维护收费价格明细、医保网关交互。
- **前置条件**：`busNo` 唯一（`BR-DEVDEF-001`）；关键主数据完整。
- **状态变化**：初始化为 `ACTIVE`。
- **聚合内影响**：新增 `adm_device_definition`（含五类单位编码与四类换算比列）；名称变更口径下同步检索码。
- **事件输出**：`DeviceDefinitionCreatedEvent`。

#### 6.1.2 维护器材定义（`updateDeviceDefinition`）
- **业务意义**：在既有目录上修正业务属性。
- **边界说明**：可编辑范围由应用层按业务码裁剪，本聚合不判定“是否已发生业务”。
- **前置条件**：遵循可编辑矩阵口径（`BR-DEVDEF-007`）。
- **状态变化**：无强制状态迁移（`N/A`）。
- **聚合内影响**：按主键更新目录字段（含五类单位与换算比）；名称变化需重算 `pyStr/wbStr`（`BR-DEVDEF-002`）。
- **事件输出**：`DeviceDefinitionUpdateEvent`（`changedFields` 由应用层映射组装，单位或换算比变更时包含对应实体属性名）。

#### 6.1.3 批量停用目录（`retireDeviceDefinitions`）
- **业务意义**：将目录移出新业务可选集合。
- **边界说明**：只改目录状态，不删除历史业务事实。
- **前置条件**：目录存在且当前可操作。
- **状态变化**：`ACTIVE -> RETIRED`（批量）。
- **聚合内影响**：批量更新 `statusEnum`。
- **事件输出**：`DeviceDefinitionStopEvent`（逐条）。

#### 6.1.4 批量启用目录（`activateDeviceDefinitions`）
- **业务意义**：恢复停用目录可被新业务引用。
- **边界说明**：不自动补建缺失的收费定价实例。
- **前置条件**：目录当前为 `RETIRED`。
- **状态变化**：`RETIRED -> ACTIVE`（批量）。
- **聚合内影响**：批量更新 `statusEnum`。
- **事件输出**：`DeviceDefinitionStartEvent`（逐条）。

#### 6.1.5 批量导入目录（`importDeviceDefinitionCatalog`）
- **业务意义**：通过模板批量维护目录。
- **边界说明**：导入编排、模板校验由应用层负责，本聚合承接落库。
- **前置条件**：文件内重复/库内重复校验通过。
- **状态变化**：按记录创建或更新。
- **聚合内影响**：批量写入 `adm_device_definition`；导入项可携带 **`stockUnitCode`（库存单位编码）**，空白或未传归一为 `null` 写入 `stock_unit_code`；覆盖更新时在 **`DeviceDefinitionUpdateEvent.changedFields`** 固定摘要中加入 `stockUnitCode`，与界面编辑路径一致。
- **应用层模板口径（`DeviceCatalogImportConstants` v1.4）**：32 列与「新增耗材」手工表单对齐；列「收费耗材」与 `chargeableFlag` **同向**（是→`true`，否→`false`）；空白默认 `true`。**禁止**再使用 v1.3「不收费耗材」取反列名/语义。
- **事件输出**：按落库结果发布创建/更新事件。

#### 6.1.6 医保对码结果回写（`syncDeviceDefinitionYbMapping`）
- **业务意义**：承接医保上下文返回的对码结果并更新目录医保标记字段。
- **边界说明**：仅承接回写，不在本聚合内实现医保对码规则与网关调用。
- **前置条件**：目标目录存在；回写数据结构合法。
- **状态变化**：`N/A`（不涉及发布状态迁移）。
- **聚合内影响**：更新 `ybFlag/ybNo/ybMatchFlag/ybOrgNo/chrgitmLv` 等医保关联字段。
- **事件输出**：按需要发布目录更新事件。
- **跨模块消费**：`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService` 在 3301 成功后调用。

### 6.2 读侧查询服务（DeviceDefinitionQueryService）

#### 6.2.1 目录初始化字典查询（`getDeviceManageInit`）
- **业务意义**：返回**启用中**（`status_enum=ACTIVE`，兼容存量 `"1"`）、**未删除**的器材定义全量列表，供下拉/绑定等场景初始化。
- **边界说明**：只读聚合查询，不写库；**不含**停用/草稿记录（与 `pageDeviceDefinition` 分页列表口径不同，后者含全状态便于启停运维）。
- **消费方**：`ServiceDeviceBindingDisplayAssembler#buildDeviceDefOptions` 仍组装 `init.deviceDefOptions` / `listDeviceDefOptions`（兼容旧调用）。**WH-BND001 耗材绑定目标选品主路径**改为 `pageDeviceCatalog` + 前端 SelectTable（见 **DM-CUS-005** §9.4 / **BR-SDT-014**）。
- **查询范围（读库，无写入）**：`adm_device_definition` 主表，条件 `is_deleted=false` 且启用态。

#### 6.2.2 目录分页查询（`pageDeviceDefinition`）
- **业务意义**：按筛选条件分页获取目录列表。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：目录主表 + 产品领域事件后查询刷新。

#### 6.2.3 目录详情查询（`getDeviceDefinition`）
- **业务意义**：读取单条目录详情供查看、编辑回显与写前断言。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：按 `id` 查询主档，并按需要合并QueryService 字段。

#### 6.2.4 关键字检索（`searchByKeyWord`）
- **业务意义**：按名称/编码/拼音/五笔快速检索目录。
- **边界说明**：只读查询。
- **过滤口径**：默认过滤停用与已删记录可由调用方控制。

#### 6.2.5 列表关键字检索（`findBySearchKey`）
- **业务意义**：为调价、绑定、下拉选择等场景返回轻量检索列表。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：按名称/编码/医保编码模糊匹配目录主档。

#### 6.2.6 批量加载目录实体（`getAllDeviceDefinitions`）
- **业务意义**：按标识集合一次性加载目录实体，支撑批量写前校验或批处理。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：按 `id` 集合查询 `adm_device_definition`。

#### 6.2.7 编辑前可编辑性查询（`getDeviceDefinitionEditability`）
- **业务意义**：返回编辑业务码（`OK` / `LOCK_CLINICAL` / `LOCK_INVENTORY`），供应用层裁剪可改字段。
- **边界说明**：业务码由 `DeviceRequest` / `SupplyRequest` 等他域 QueryService 判定；应用层 `DeviceCatalogAppService#validateDeviceCatalogEdit` + `CatalogEditWriteGuardsupport 写侧强制校验。
- **字段矩阵**：与 **DM-MED-001 §9.1** 一致（分类/类型/单位/拆零倍数/维护态进售价锁定；名称等描述字段可改）。
- **返回约束**：详情 DTO 回填 `editabilityCode` + `editabilityMessage`。

#### 6.2.8 导入模板下载（`downloadDeviceDefinitionCatalogTemplate`）
- **业务意义**：输出目录批量导入模板，统一字段口径。
- **边界说明**：只读输出能力，不写库。

#### 6.2.9 目录批量导出（`exportDeviceDefinitionCatalog`）
- **业务意义**：按筛选条件导出目录数据用于盘点、对码、调价与外部对接。
- **边界说明**：只读导出能力，不写库。

### 6.3 领域边界说明

- 本聚合维护「器材目录主数据」，不直接处理耗材请求、发放、库存扣减等执行事务。
- 目录与收费项、供应商、组织等跨域协同由应用层编排，领域层聚焦定义与可用性规则。
- 下游业务仅通过 `deviceDefId` 或编码引用本聚合，不在本聚合内回写下游状态。

---

## 7. 不变式（BR-DEVDEF-*）

| 编号 | 规则 | 说明 |
|---|---|---|
| BR-DEVDEF-001 | `busNo` 唯一 | 新增前按 `busNo` 去重 |
| BR-DEVDEF-002 | 编辑名称需重算检索码 | 更新时同步 `pyStr/wbStr` |
| BR-DEVDEF-003 | 启停只改状态不删历史 | 停用后仍保留历史引用 |
| BR-DEVDEF-004 | 目录与收费实例需一致 | 目录新增/编辑需联动 `ChargeItemDefinition` |
| BR-DEVDEF-005 | 编辑前可改范围受业务码约束 | 由应用服务按业务码裁剪字段 |
| BR-DEVDEF-006 | 写入与QueryService最终一致 | 目录变更后需保证投影一致性；更新事件走“最小事件 + 聚合快照回源”投影策略 |
| BR-DEVDEF-007 | 业务发生判定不在本聚合实现 | 由申领/供应等他域返回业务码，目录侧按码执行字段约束 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游 | 关联字段 | 说明 |
|---|---|---|
| Organization | `orgId` | 所属科室 |
| Location | `locationId` | 存放位置 |
| Supplier | `supplyId` | 供应商 |
| 术语字典 | `categoryCode/typeCode/purchaseUnitCode/splitUnitCode/salesUnitCode/stockUnitCode/doseUnitCode` | 分类与单位编码来源 |

### 8.2 下游影响

| 下游 | 触发方式 | 说明 |
|---|---|---|
| DeviceRequest | 查询引用 | 请求记录引用 `deviceDefId` |
| DeviceDispense | 查询引用 | 发放记录引用 `deviceDefId` |
| ChargeItemDefinition | 应用编排 | 目录作为收费实例锚点 |

### 8.3 引用规则

DeviceDefinition 与 `Organization` / `Location` / `Supplier` 等基础模型形成主数据协作关系，并被 `DeviceRequest`、`DeviceDispense`、`ChargeItemDefinition` 等业务模型引用。

- 下游创建业务单据时通过 `deviceDefId` 引用器材定义，不复制全量目录字段。
- 目录停用后应限制新增业务引用，历史单据保持可追溯。
- 目录编辑需遵循跨域编排校验（如收费目录联动约束）。

### 8.4 事件流示意图

```text
Organization / Location / Supplier / 术语
        └──(ID 引用)──> DeviceDefinition
                              ├── DeviceDefinitionCreatedEvent / UpdateEvent / StopEvent / StartEvent
                              └── 下游：DeviceRequest / DeviceDispense / ChargeItemDefinition（查询或编排引用）
```

---

## 9. 补充

### 9.1 补充说明

- 字段可编辑矩阵见 **§9.2**；导入导出、医保对码、启停批量能力已归入 `§6` 与不变式说明。
- 事件发布与领域事件后查询刷新刷新遵循统一领域事件规范，具体实现细节由 本稿 **§6.1** 约束。
- 值对象/枚举字典按业务需要可在 §4 正常定义；仅新增实体/新表能力按扩展规划单独表达。
- **v1.1**：聚合表字段列名以 `purchase_unit_code`、`split_unit_code`、`total_volume`、`stock_unit_code` 等为权威；导入命令项 `ImportDeviceDefinitionCatalogCommand.Item` 已支持可选 `stockUnitCode`（空白或未传视为未携带，写入 `null`；覆盖更新会刷新库内值并体现在更新事件变更摘要中）。
- **v1.2**：补充 §3.1 术语体系与厂家/供应商文本约束；声明主表删除冗余价格与 `item_type_code`/`yb_type` 列（**V30**），与 **ChargeItemDefinition** 分工。
- **v1.3**：五类计量单位与药品目录 `unit_code` 统一口径；`purchaseUnitCode`/`splitUnitCode` 应用契约。
- **v1.4**（2026-05-29）：耗材维护页校验收口——`categoryCode`、购入价/零售价必填；生产厂家选填；剂量单位/剂量倍数界面隐藏，写路径 `normalizeDoseUnitDefaultsForWrite` 默认回填。
- **v1.5**（2026-05-29）：定价计量与库房读锚对齐 **`DM-ADM-010` §1.5**——维护态进/售价为每包装；库房读锚仅 **`PURCHASE` + `LOT_NUMBER_COST`**；§3.1 定价条。
- **v1.6**（2026-06-24）：§6.2.1 明确 `getDeviceManageInit` 为启用态全量列表及 WH-BND001 消费方；与耗材目录分页（含停用）口径区分。
- **v1.7**（2026-07-04）：**§9.2 字段可编辑矩阵**；联调引用 **AS-BAS-CAT-001** 与前端维护页路径。

### 9.2 字段可编辑矩阵（产品规格）

编辑态下，字段是否允许修改取决于 **editabilityCode**（`CatalogEditabilityResolveSupport#resolveDeviceEditability`；写侧 `CatalogEditWriteGuardsupport）：

| 业务码 | 触发条件（whale 口径） |
|---|---|
| `OK` | 无有效 `DeviceRequest`；且无 `SupplyRequest` 入库中/已同意行 |
| `LOCK_CLINICAL` | 存在**任一**未删 `DeviceRequest`（且未命中入库码） |
| `LOCK_INVENTORY` | 存在 `SupplyRequest` 行 `item_id=器材主键` 且 `status_enum ∈ {APPROVAL, AGREE}`（**优先于**临床码） |

| 字段 | OK | LOCK_CLINICAL | LOCK_INVENTORY |
|---|---|---|---|
| 业务编码 busNo | 只读 | 只读 | 只读 |
| 耗材名称 / 商品名称 | 可编辑 | **锁定** | **锁定** |
| 耗材分类 / 耗材类型 / 财务类别 / 医保类别 / 单位 / 拆零倍数 | 可编辑 | **锁定** | **锁定** |
| 规格 totalVolume | 可编辑 | 可编辑 | 可编辑 |
| 采购参考价 / 销售参考价（维护态定价行） | 可编辑 | **锁定** | **锁定** |
| 医保等级 chrgitmLv | 可编辑 | 可编辑 | 可编辑 |
| 病案结算类型 mrSettlementTypeCode | 可编辑 | 可编辑 | 可编辑 |
| 是否收费耗材 chargeableFlag | 可编辑 | 可编辑 | 可编辑 |
| 最高零售价 | 可编辑 | 可编辑 | 可编辑 |
| 厂家 / 批准文号等 | 可编辑 | 可编辑 | 可编辑 |

应用层：`validateDeviceCatalogEdit`；详情 DTO 回填 `editabilityCode`+`editabilityMessage`；`editDeviceCatalog` 写前 immutable 断言。批号真实进价/售价辅读：`listDeviceLotPrices`。详见 **AS-BAS-CAT-001** / **AS-BAS-CAT-001-SDD**。

### 9.3 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.18 | 2026-10-06 | §6.2.1：WH-BND001 耗材绑定目标选品改走 `pageDeviceCatalog`；`getDeviceManageInit` 仅兼容接口 |
| v1.17 | 2026-09-22 | §9.2：规格 `totalVolume` 锁定态改为可编辑（对齐 AS-BAS-CAT-001 BR-CAT-013） |
| v1.16 | 2026-09-10 | §9.2：病案结算类型锁定态改为可编辑（对齐 AS-BAS-CAT-001 BR-CAT-008） |
| v1.15 | 2026-09-03 | §3.1：去掉 HTTP/DTO `unitCode`/`minUnitCode` 与 init `packUnits` 等同源双写下拉；契约仅 `purchaseUnitCode`/`splitUnitCode`/`unitCodeOptions` |
| v1.14 | 2026-08-19 | §3.1：`trace_unit_*` 不参与发退库存扣加（与 **DM-MED-001** / **BR-OPP-017** 同口径） |
| v1.13 | 2026-08-14 | §3.1 / §6.1.5：`chargeableFlag` 与界面/导入「收费耗材」同向；导入模板升 **v1.4**（废止「不收费耗材」取反列） |
| v1.12 | 2026-07-17 | §9.2：名称锁定；医保等级可改；维护态 SALE 文案改为销售参考价 |
| v1.11 | 2026-07-15 | §3.1：`item_min/max_quantity` 明确为**库存单位**；供库存明细预警目录优先（**AS-INV-WHS-004 BR-024**） |
| v1.10 | 2026-07-15 | §3.1 / §9.2：维护态购入价/零售价文案改为采购参考价/零售参考价；增补 `listDeviceLotPrices` |
| v1.9 | 2026-07-14 | §3.1 / 术语表 / §9.2 增补病案结算类型、是否收费耗材；平台菜单 `Navigation.Device` 显示名改为「耗材管理」 |
| v1.8 | 2026-07-11 | §6.1.6 增补 CHS 跨模块消费（`zxplat-module-chs-jilin` · `InsuranceCatalogMappingAppService`） |
| v1.7 | 2026-07-04 | **§9.2** 字段可编辑矩阵；AS-BAS-CAT-001 联调引用 |
| v1.2.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 Domain/QueryService 白名单对齐 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`DeviceDefinitionDomainService`

| 方法 |
|------|
| `createDeviceDefinition` |
| `updateDeviceDefinition` |
| `deleteDeviceDefinitions` |
| `retireDeviceDefinitions` |
| `activateDeviceDefinitions` |
| `importDeviceDefinitionCatalog` |
| `syncDeviceDefinitionYbMapping` |

### QueryService：`DeviceDefinitionQueryService`

| 方法 |
|------|
| `getDeviceManageInit` |
| `pageDeviceDefinition` |
| `getDeviceDefinition` |
| `resolveCategoryCodeByDeviceDefinitionId` |
| `searchByKeyWord` |
| `findBySearchKey` |
| `getAllDeviceDefinitions` |
| `pageForPurchase` |
| `pageForCatalog` |
| `findByBusNos` |
| `getDeviceDefinitionEditability` |
| `downloadDeviceDefinitionCatalogTemplate` |
| `exportDeviceDefinitionCatalog` |
| `listIdsByCategoryCodesIn` |
| `listIdsByChrgitmLvIn` |
| `findExistingDuplicateKeyTokens` |
| `listIdsByInventoryDetailKeyword` |

