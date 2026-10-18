# DM-ADM-010 ChargeItemDefinition 收费项定价

> **限界上下文**：财务域 · 收费项定价管理
> **聚合类型**：实体聚合（单表多行）
> **版本**：v1.1.12

## 1. 概述

### 1.1 业务定义

收费项定价（ChargeItemDefinition）是医疗服务项目的价格定义核心，维护各类型项目的多维度价格事实，包括项目售价、进价、限价以及按批号定价的信息，供收费、成本核算、按批号取价等场景使用。

### 1.2 核心职责

| 职责   | 说明                         |
| ---- | -------------------------- |
| 价格维护 | 维护项目的多类型价格事实（售价/进价/限价/批号价） |
| 定价查询 | 支持按项目定位键批量查询全部定价行（**仅「当前价」读路径**；已落库账单金额见 **DM-ADM-011 BR-PRICE-SRC-001**） |
| 批号取价 | 提供批号优先取价能力（批号价 > 项目统一价）    |
| 时间窗口 | 管理定价的生效时间窗口（生效开始/生效结束）     |
| 事件发布 | 发布价格变更领域事件，驱动下游对账与结算       |

### 1.3 适用范围

| 类型     | 说明                                         |
| ------ | ------------------------------------------ |
| 医生开立医嘱 | 从 ChargeItemDefinition 获取项目售价生成 ChargeItem |
| 护士执行收费 | 按批号优先规则取批号售价或项目售价                          |
| 库房入库定价 | 供应同意入库写 **`LOT_NUMBER_COST`** / **`LOT_NUMBER_PRICE`**（每包装）；**库房单据读锚仅 `PURCHASE` + `LOT_NUMBER_COST`**（见 **§1.5**） |
| 挂号场景   | 通过 HealthcareService 项目定位键查询定价             |
| 价格历史追溯 | 按生效时间窗口查询历史价格                              |

### 1.4 门诊医生站目录消费（IF-DOR-11，应用层）

与 **`AS-OP-DOR-003` / `AS-OP-DOR-003-SDD`** 对齐的**读路径约定**（域内仍为多行事实表，不在本聚合内实现 UI）：

| 目录类型 | 实例表（`instance_table`） | 目录装配采纳的价型 | 批号维度 |
| --- | --- | --- | --- |
| 诊疗 | `wkf_activity_definition` | **零售价 `SALE`**（`PriceType`，持久化码） | 无 |
| 药品 / 耗材 | `med_medication_definition` / `adm_device_definition` | **批号售价 `LOT_NUMBER_PRICE`**（`PriceType`） | 定价行 **`lot_no`** 须与库存明细 **`lotNumber`** 对齐；目录汇总价与**首条**库存批号命中行一致 |

暂存费用草稿择行：诊疗 **`SALE`**（`selectFirstPriceLineForChargeDraft` / 等价）；药耗经 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`**（目录已解析库位 → FIFO 首条批号匹配 **`LOT_NUMBER_PRICE`**；**有批号无该批定价不得跨批号 priority 冒充**；仅无库存/无批号时允许择优兜底）。

**签发 FIFO 分段挂费（IF-DOR-15/37）**：每一发放分段的单价须按该段 **`lotNumber`** 调用 **`ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft`** 匹配 **`LOT_NUMBER_PRICE`**（与护士执行挂费同口径）；批号未命中 → 业务异常；**禁止** `selectFirstPriceLineForChargeDraft` / `selectBestLotNumberPriceLine` 跨批号落账。

### 1.5 药品/耗材维护态定价计量口径（库房锚价）

对 **`med_medication_definition`**、**`adm_device_definition`** 实例：

| 价类（`price_type`） | `price_value` 计量 | 目录维护页文案 | 库房单据读路径 |
| --- | --- | --- | --- |
| **`PURCHASE`** | 每包装单位进价 | **采购参考价**（JSON 字段名仍 `purchasePrice`） | **读锚**（无批号时） |
| **`SALE`** | 每包装单位售价 | **零售参考价**（JSON 字段名仍 `retailPrice`） | **不读**（门诊/收费等下游亦不以此为批号真卖价） |
| **`LOT_NUMBER_COST`** | 每包装单位进价（批号维） | **进价**（真实；`list*LotPrices`） | **读锚**（有批号时） |
| **`LOT_NUMBER_PRICE`** | 每包装单位售价（批号维） | **售价**（真实售卖；`list*LotPrices`） | **仅写**（同意入库落库）；**库房读路径不消费**（门诊/收费下游） |

库房进货/出库/盘点/药房入库之**选品与详情 `unitBands` 槽价**：仅以 **`PURCHASE`** / **`LOT_NUMBER_COST`** 为包装锚（`P_pkg × R_slot / R_pkg`）；**禁止**读 **`SALE`**、**`LOT_NUMBER_PRICE`**。

药品/耗材目录维护页辅读真实批号价：`MedicationCatalogAppService#listMedicationLotPrices` / `DeviceCatalogAppService#listDeviceLotPrices`（**当前 ACTIVE 价**，BR-CHGDEF-QRY-004；与参考价字段分离）。

### 1.6 槽价与账单换算锚点（硬约束）

凡 **`PURCHASE` / `SALE` / `LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`** 之 **`price_value`** 均为**每包装单位价**（见 §1.5）。应用层经 **`ProductDefinitionCatalogUnitBandsAssembler`** / **`ProductDefinitionOrderChargePricingsupport** 写各槽单价或 **`ChargeItem.unit_price`** 时：

- **唯一输入锚**：**包装单位**（`BAND_PACKAGE` / `package_unit_code`）及其包装价；
- **允许**：**包装价 → 其它单位价**，`P_slot = P_pkg × R_slot / R_pkg`（`R_*` 为相对拆零倍数）；
- **禁止**：以**拆零单位**或**默认开立单位**（`defEncounterUnitCode`）为锚把 `price_value` 当作拆零价，再乘采购倍数「往回」还原包装价；
- **禁止**：先舍入拆零单价再乘回各槽（避免整数包装价漂尾差）。

目录行 **`unitPrice`** 展示值仍为定价表**每包装单位**价；**`unitBands`** 槽价装配须调用 **`#resolveDoctorStationBandPricingUnitCode`** / **`#resolvePackageAnchoredPricingUnitCode`**，**不得**用 **`#resolveCatalogPrimaryUnitCode`** 作为定价换算锚。

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**表结构**：单表 `adm_charge_item_definition` 多行设计。所有价格（稳定价+批号价）统一存储在主表中，允许财务类别等信息冗余：

- **单表设计**：所有价格类型（项目售价/项目进价/项目限价/批号进价/批号售价）都在主表中
- **多行表达**：同一项目（`instanceTable` + `instanceId`）下可有多条记录，每条记录为**一条价格事实行**
- **冗余设计**：财务类别等信息在多行中冗余存储，换取查询性能和扩展性

同一项目（`instanceTable` + `instanceId`）下可有多条定价事实行，每条记录为**一条价格事实行**（含价格类型、条件值、金额、时间窗口）；聚合边界以**项目定位键**划分，每条记录都有独立主键 `id`。

**索引配置**：

- 主键索引：`adm_charge_item_definition.id`
- 业务索引：`(instance_table, instance_id)` - 项目定位键查询（高频）
- 业务索引：`(instance_id, price_type)` - 按项目和价格类型查询
- 业务索引：`(instance_id, price_type, lot_number)` - 命中具体一行（含批号维度）
- 业务索引：`(price_type, effective_start, effective_end)` - 时间窗口查询
- 业务索引：`(org_id, status_enum)` - 机构和状态筛选

**高频查询模式**：

- 查项目售价：`SELECT amount FROM adm_charge_item_definition WHERE instance_table = ? AND instance_id = ? AND price_type = 'UNIT' AND ? BETWEEN effective_start AND effective_end LIMIT 1`
- 查项目进价：`SELECT amount FROM adm_charge_item_definition WHERE instance_table = ? AND instance_id = ? AND price_type = 'PURCHASE' AND ? BETWEEN effective_start AND effective_end LIMIT 1`
- 查批号价：`SELECT amount FROM adm_charge_item_definition WHERE instance_table = ? AND instance_id = ? AND price_type IN ('LOT_NUMBER_COST','LOT_NUMBER_PRICE') AND lot_number = ? AND ? BETWEEN effective_start AND effective_end LIMIT 1`
- 统计分析：`SELECT price_type, SUM(amount) FROM adm_charge_item_definition WHERE instance_id IN (?) GROUP BY price_type`

**事务边界**：所有增删改操作在同一事务内维护，保证定价事实的原子性。

**设计原则**：

- **性能优先**：所有查询都在单表，无需JOIN，查询性能最优（<1ms）
- **数据一致性**：单一数据源，无主表/子表同步问题
- **扩展强劲**：新增价格类型仅增加记录，无需改表结构
- **统计分析友好**：单表SQL查询简洁，无需UNION
- **允许冗余**：财务类别等信息在多行中冗余，换取性能和扩展性

### 2.2 标识

| 属性    | 类型                             | 说明                     |
| ----- | ------------------------------ | ---------------------- |
| 定价标识  | `Long`                         | 聚合根主键，雪花 ID            |
| 项目定位键 | `instanceTable` + `instanceId` | 项目所在业务表名 + 项目主键，定位具体项目 |

### 2.3 状态机

无独立状态机。发布状态 `statusEnum` 的含义与迁移由目录/调价审批等应用用例编排决定，不由领域层驱动。

```
  [ACTIVE] ──停用──> [INACTIVE]
```

> `statusEnum` 存储为 **String**（`PublicationStatus#getCode()`：`ACTIVE` / `INACTIVE`）；状态流转由目录/调价审批等应用用例编排。

## 3. 实体

```text
ChargeItemDefinition（聚合根，单表多行结构）
════════════════════════════════════════════════════════════

项目定位键：instanceTable + instanceId

  ┌──────────────────────────────────────────────────────┐
│         ChargeItemDefinition（单表多行）               │
│  adm_charge_item_definition                          │
│                                                      │
│  公共列：chargeName/typeCode/ybType/statusEnum        │
│  项目定位：instanceTable/instanceId                   │
│  价格类型：priceType（UNIT/PURCHASE/LIMIT/...）       │
│  批次号：lotNumber（批号价时为批次号，稳定价为空）        │
│  金额：amount（统一金额字段）                          │
│  时间窗口：effectiveStart/effectiveEnd               │
│  优先级：priority（默认 ROUTINE）                      │
│                                                      │
│  同一项目可有多行记录：
│  - 1行 UNIT（项目售价）                              │
│  - 1行 PURCHASE（项目进价，药品/器材）               │
│  - 1行 LIMIT（项目限价）                              │
│  - N行 LOT_NUMBER_COST（批号进价，药品）              │
│  - N行 LOT_NUMBER_PRICE（批号售价，药品）             │
│                                                      │
│  注：财务类别等信息在多行中冗余存储                     │
└──────────────────────────────────────────────────────┘
```

### 3.1 收费项定价（ChargeItemDefinition）

ChargeItemDefinition 聚合根的属性定义，对应 `adm_charge_item_definition` 表。同一项目可有多条记录，每条记录代表一种价格类型。

| 属性     | 字段名               | 类型           | 必填 | 说明                                                            |
| ------ | ----------------- | ------------ | -- | ------------------------------------------------------------- |
| 定价标识   | `id`              | `Long`       | 是  | 主键，雪花 ID（每条记录独立）                                              |
| 费用项名称  | `charge_name`     | `String`     | 是  | 同一项目多行建议一致（`PR-CHGDEF-001`）                                   |
| 标题     | `title`           | `String`     | 否  | 标题/别名                                                         |
| 发布状态   | `status_enum`     | `String`（`PublicationStatus` code） | 是  | 新建行须落 `ACTIVE`（**BR-CHGDEF-WR-001**）；调价停用为 `INACTIVE` |
| 机构标识   | `org_id`          | `Long`       | 是  | 机构 ID                                                         |
| 描述     | `description`     | `String`     | 否  | 描述信息                                                          |
| 项目所在表名 | `instance_table`  | `String`     | 是  | 项目所在业务表名                                                      |
| 项目主键   | `instance_id`     | `Long`       | 是  | 关联项目主键                                                        |
| 价格类型   | `price_type`      | `String`     | 是  | 价格类型：UNIT/PURCHASE/LIMIT/LOT\_NUMBER\_COST/LOT\_NUMBER\_PRICE |
| 批次号    | `lot_number`      | `String`     | 否  | 批次号（批号价时必填），稳定价时为空                                            |
| 金额     | `amount`          | `BigDecimal` | 是  | 价格金额                                                          |
| 生效开始时间 | `effective_start` | `Date`       | 是  | 生效开始时间                                                        |
| 生效结束时间 | `effective_end`   | `Date`       | 否  | 生效结束时间                                                        |
| 优先级    | `priority`        | `Integer`    | 是  | 优先级（默认7-ROUTINE），同类型多行时取最高                                    |
| 财务类别   | `type_code`       | `String`     | 是  | 财务/费用类别编码（允许多行冗余）                                             |
| 医保类别   | `yb_type`         | `String`     | 是  | 医保类别编码（允许多行冗余）                                                |
| 物价编码   | `price_code`      | `String`     | 否  | 物价编码                                                          |

> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段继承自 `HisBaseEntity`，遵循统一约定。

> **单表多行方案说明**：
>
> - 同一项目（`instanceTable + instanceId`）下可有多条记录
> - 每条记录通过 `priceType` 区分价格类型
> - 批号价通过 `lotNumber` 存储批次号
> - 财务类别等信息在多行中冗余存储，换取查询性能和扩展性
> - 所有查询都在单表，无需JOIN，性能最优

**单表多行方案无子实体**：v6.0版本移除了子表 `ChargeItemDefDetail`，所有价格类型统一在主表 `ChargeItemDefinition` 中表达，通过 `priceType` 字段区分。

## 4. 值对象 / 枚举

### 4.1 定价类型（PriceType）

枚举类：`PriceType`（共享枚举库）

| 枚举常量               | 编码                 | 中文         | condition\_value 使用 |
| ------------------ | ------------------ | ---------- | ------------------- |
| `UNIT`             | *`UNIT`*           | 项目售价       | 否（稳定价）              |
| `PURCHASE`         | `PURCHASE`         | 采购（项目统一进价；药品/耗材目录展示为**采购参考价**） | 否（稳定价）              |
| `LIMIT`            | `LIMIT`            | 限制（限价）     | 否（稳定价）              |
| `LOT_NUMBER_COST`  | `LOT_NUMBER_COST`  | 产品批号进价（真实进价）     | 是（批号价）              |
| `LOT_NUMBER_PRICE` | `LOT_NUMBER_PRICE` | 产品批号售价（真实售卖价）     | 是（批号价）              |

> **存储口径**：主表字段 `price_type` 使用字符串编码存储。

> **单表多行方案说明**：
>
> - 所有价格类型（稳定价/批号价）统一存储在主表中，每条记录对应一个价格类型
> - 通过 `price_type` 字段区分价格类型
> - 通过 `lot_number` 字段区分批号（批号价时必填）
> - 取价逻辑完全一致，所有查询都在单表
> - 便于统计分析和数据一致性保证

> **扩展性说明**：
>
> - 未来可扩展价格类型（如客户等级价、地区价等）
> - 无需修改表结构，仅增加主表记录

> **命名差异说明**：一侧枚举常量为 `SALE`（项目售价），另一侧为 `UNIT`（项目售价），语义一致但命名不同。

### 4.2 优先级（ActPriority）

枚举类：`ActPriority`（共享枚举库）

在单表多行方案下，优先级用于同一价格类型多条记录的排序。通常使用 `ROUTINE`（value=7, code="R", 常规服务）作为默认优先级。完整枚举含 15 个值（ASAP/EMERGENCY/ROUTINE/STAT/URGENT 等），此处仅记录与定价相关的条目。

> **存储口径**：对应主表字段 `priority`，使用整数数值存储。

> **优先级使用场景**：
>
> - 同一价格类型（如批号价）多条记录时，按优先级取最高
> - 时间窗口重叠时，优先级决定取价顺序
> - 支持特殊场景（如促销价优先于常规价）

> **取价逻辑**：
>
> 1. 按 `instance_table`, `instance_id`, `price_type` 过滤
> 2. 过滤时间窗口内记录
> 3. 按 `priority DESC, created_at DESC` 排序
> 4. 取第一条记录

### 4.3 项目定位键（ProjectKey）

| `instanceTable` 字符串（与来源 DM 主档表名一致） | 对应聚合                 | 权威 DM        |
| ---------------------------------- | -------------------- | ------------ |
| `wkf_activity_definition`          | ActivityDefinition   | `DM-WKF-001` |
| `med_medication_definition`        | MedicationDefinition | `DM-MED-001` |
| `adm_device_definition`            | DeviceDefinition     | `DM-ADM-008` |

## 5. 领域事件

### 5.1 事件定义

| 编号    | 事件名称                                         | 触发动作              | 消费方        | 版本 |
| ----- | -------------------------------------------- | ----------------- | ---------- | -- |
| 5.1.1 | `PriceLineAddedEvent`                        | 新增价格行（主表）         | 收费、成本核算、统计 | v6 |
| 5.1.2 | `PriceLineUpdatedEvent`                      | 更新价格行（主表）         | 收费、成本核算、统计 | v6 |
| 5.1.3 | `PriceLineRemovedEvent`                      | 删除价格行（主表）         | 收费、成本核算、统计 | v6 |
| 5.1.4 | `PriceBatchAddedEvent`                       | 批量新增价格行（主表）       | 收费、成本核算、统计 | v6 |
| 5.1.5 | `ChargeItemDefinitionInfoBatchUpdatedEvent`  | 批量更新项目公共列（同一项目多行） | QueryService        | v6 |
| 5.1.6 | `ChargeItemDefinitionRemovedByInstanceEvent` | 按项目移除全部定价行        | QueryService        | v1 |
| 5.1.7 | `PriceAdjustmentRecordedEvent`               | 记录价格调整历史          | 价格追溯       | v6 |

载荷建议：`id`（主表主键）、`instanceTable`、`instanceId`、`priceType`（UNIT/PURCHASE/LIMIT/LOT\_NUMBER\_COST/LOT\_NUMBER\_PRICE）、`lotNumber`、`amount`、`oldAmount`（更新时）、`effectiveStart`、`effectiveEnd`、`priority`、`occurredAt`、租户等。

> **单表多行方案事件说明**：
>
> - 所有价格变更（稳定价/批号价）统一触发 `PriceLine*Event`
> - 事件载荷包含完整的 `priceType` 和 `lotNumber`
> - 统一事件命名，简化下游处理逻辑
> - 批量更新公共信息触发 `ChargeItemDefinitionInfoBatchUpdatedEvent`
> - 价格调整历史记录触发 `PriceAdjustmentRecordedEvent`

### 5.2 事件触发规则

| 当前状态     | 操作                                    | 目标状态         | 发布事件                                         |
| -------- | ------------------------------------- | ------------ | -------------------------------------------- |
| —        | 新增价格行（`addPriceLine`）                 | 新增主表记录       | `PriceLineAddedEvent`                        |
| 已存在      | 更新价格行（`updatePriceLine`）              | 更新主表记录       | `PriceLineUpdatedEvent`                      |
| 已存在      | 删除价格行（`removePriceLine`）              | 删除主表记录       | `PriceLineRemovedEvent`                      |
| —        | 批量新增价格行（`batchAddPriceLines`）         | 批量新增主表记录     | `PriceBatchAddedEvent`                       |
| 已存在同项目多行 | 批量更新项目公共列（`updatePricingItemInfo`）    | 同项目多行公共列同步更新 | `ChargeItemDefinitionInfoBatchUpdatedEvent`  |
| 任意       | 按项目移除全部定价行（`removePricingByInstance`） | 项目下定价行移除     | `ChargeItemDefinitionRemovedByInstanceEvent` |
| 价格变更     | 记录价格调整历史（`recordPriceAdjustment`）     | 写入调价历史表      | `PriceAdjustmentRecordedEvent`               |

> **单表多行方案触发说明**：
>
> - 所有价格变更（稳定价/批号价）统一触发 `PriceLine*Event`
> - 通过 `priceType` 区分价格类型，事件载荷统一
> - 批量更新公共信息需要更新同一项目的多行记录
> - 所有价格变更操作均应触发 `PriceAdjustmentRecordedEvent`，确保价格追溯完整性
> - 事件处理逻辑统一，简化下游系统

## 6. 领域服务

> `statusEnum` 存储为 **String**（`PublicationStatus#getCode()`：`ACTIVE` / `INACTIVE`）；状态流转由目录/调价审批等应用用例编排。**新建定价行**须写入 `ACTIVE`，否则「当前价」读路径（**BR-CHGDEF-QRY-004**）无法命中。

### 6.1 写侧领域服务（ChargeItemDefinitionDomainService）

> 写侧由应用服务用例编排事务后调用领域服务完成。详见：
> `04-架构设计/软件详细设计-本稿 **§6.1**/领域模型详细设计/ChargeItemDefinition收费项定价-详细设计.md`

**新建行发布状态（BR-CHGDEF-WR-001）**：凡 **insert** 定价行（`saveAllChargeItemDefinitions`、`addPriceLineByCommand` 及委派其的 `addItem*` / `addLot*`），`status_enum` 须落 **`PublicationStatus.ACTIVE.getCode()`**；`SavePriceLineCommand.Item.statusEnum` 非空时以命令为准（调价审批新增行）。`ChargeItemDefinitionDomainService#ensurePublicationStatusForInsert` 在落库前赋值；实体 `@PrePersist` 对仍为空的 `status_enum` 兜底写 `ACTIVE`。**更新**已有行（`updatePriceLineByCommand`）不修改 `status_enum`，停用仅经 `deactivateActivePriceLinesByCommand`。

**新建行财务类型（BR-CHGDEF-WR-002）**：凡 **insert** 定价行须写入非空 **`type_code`**（对齐 `fin_type_code` 字典）。`saveAllChargeItemDefinitions` 命令项须显式携带；`addPriceLineByCommand` 命令缺省时经 `resolveSiblingTypeCode` 继承同实例历史行（含 INACTIVE）。仍为空则 `ensureTypeCodeForInsert` 抛错。号源（`adm_healthcare_service`）定价 **`type_code=1012`（诊察费）**；批号价写路径须带 fin_type 供日结/费用明细 `definition_id` 锚点汇总。库表 `adm_charge_item_definition.type_code NOT NULL`（Flyway V81 回填后约束）。

**已知写路径（均须落 fin_type）**

| 入口 | 方法 | type_code 来源 |
|------|------|----------------|
| 药品目录 | `MedicationCatalogAppService#replacePriceLines` | 请求 `typeCode` 或 `categoryCode` 映射 |
| 诊疗目录 | `ServiceCatalogAppService#replacePriceLines` | 请求 `itemTypeCode`；维护页必填，后端 `assertItemTypeCodeWhenPricingPresent`（有售价/进价才拒空；**AS-BAS-CAT-001 BR-CAT-016**） |
| 耗材目录 | `DeviceCatalogAppService#replacePriceLines` | 请求 `itemTypeCode`（Gate 必填） |
| 号源类型 | `RegistrationTypeAppService#syncSalePricingForHealthcareService` | 固定 `1012` 诊察费 |
| 入库批号价 | `InventoryWarehouseInboundReceiptAppService` | `ChargePricingTypeCodeResolvesupport |
| 调价审批 | `PriceAdjustmentApprovePricingOrchestration` | 同实例历史定价行 |
| 装配器 | `ChargePricingCommandAssembler#appendPriceLineIfPresent` | 有金额则 type_code 非空，否则抛错 |
| 领域兜底 | `ChargeItemDefinitionDomainService` | insert 统一 `ensureTypeCodeForInsert` |

除上述入口外，定价表仅经 `ChargeItemDefinitionDomainService` 写库，禁止绕路 `Repository` insert。

#### 6.1.1 新增项目售价（`addItemRetailPrice`）

- **业务意义**：为目标项目新增项目售价（`UNIT`）定价事实。
- **边界说明**：仅写定价聚合，不改目录主数据。
- **前置条件**：项目定位键有效；金额与时间窗口合法。
- **状态变化**：新建 → `ACTIVE`（**BR-CHGDEF-WR-001**）。
- **聚合内影响**：新增主表记录（`priceType=UNIT`）。
- **事件输出**：`PriceLineAddedEvent`。

#### 6.1.2 新增项目统一进价（`addItemPurchasePrice`）

- **业务意义**：为目标项目新增统一进价（`PURCHASE`）定价事实。
- **边界说明**：诊疗项目按规则跳过 `PURCHASE`（`PR-CHGDEF-003`）。
- **前置条件**：项目定位键有效；金额与时间窗口合法。
- **状态变化**：新建 → `ACTIVE`（**BR-CHGDEF-WR-001**）。
- **聚合内影响**：新增主表记录（`priceType=PURCHASE`）。
- **事件输出**：`PriceLineAddedEvent`。

#### 6.1.3 新增限价（`addItemLimitPrice`）

- **业务意义**：为项目新增限价（`LIMIT`）约束。
- **边界说明**：仅写定价事实，不执行业务控费策略。
- **前置条件**：项目定位键有效；金额合法。
- **状态变化**：新建 → `ACTIVE`（**BR-CHGDEF-WR-001**）。
- **聚合内影响**：新增主表记录（`priceType=LIMIT`）。
- **事件输出**：`PriceLineAddedEvent`。

#### 6.1.4 更新项目售价（`updateItemRetailPrice`）

- **业务意义**：更新既有 `UNIT` 行金额。
- **边界说明**：仅影响 `UNIT` 行，不联动其他价格类型；若定价记录已被使用过（被收费单、订单等引用），必须通过调价功能（创建 PriceAdjustment 记录并经过审批）才能修改价格（`PR-CHGDEF-006`）。
- **前置条件**：目标 `UNIT` 行存在；金额合法。
- **状态变化**：`N/A`。
- **聚合内影响**：更新目标主表记录 `amount`。
- **事件输出**：`PriceLineUpdatedEvent`。

#### 6.1.5 更新项目进价（`updateItemPurchasePrice`）

- **业务意义**：更新既有 `PURCHASE` 行金额。
- **边界说明**：仅影响 `PURCHASE` 行，不联动其他价格类型；若定价记录已被使用过（被收费单、订单等引用），必须通过调价功能（创建 PriceAdjustment 记录并经过审批）才能修改价格（`PR-CHGDEF-006`）。
- **前置条件**：目标 `PURCHASE` 行存在；金额合法。
- **状态变化**：`N/A`。
- **聚合内影响**：更新目标主表记录 `amount`。
- **事件输出**：`PriceLineUpdatedEvent`。

#### 6.1.6 更新限价（`updateItemLimitPrice`）

- **业务意义**：更新既有 `LIMIT` 行金额。
- **边界说明**：仅影响 `LIMIT` 行，不联动其他价格类型；若定价记录已被使用过（被收费单、订单等引用），必须通过调价功能（创建 PriceAdjustment 记录并经过审批）才能修改价格（`PR-CHGDEF-006`）。
- **前置条件**：目标 `LIMIT` 行存在；金额合法。
- **状态变化**：`N/A`。
- **聚合内影响**：更新目标主表记录 `amount`。
- **事件输出**：`PriceLineUpdatedEvent`。

#### 6.1.7 同步项目公共信息（`updatePricingItemInfo`）

- **业务意义**：当同一项目（`instanceTable + instanceId`）的名称或分类口径变更时，一次性把该项目所有定价行的公共信息改成一致，避免同项目多行显示不一致。
- **边界说明**：只改公共信息，不改价格金额，不改 `lotNumber`，不新增/删除定价行。
- **前置条件**：同项目行集存在；字段一致性要求满足 `PR-CHGDEF-001`。
- **状态变化**：`N/A`。
- **聚合内影响**：批量更新主表记录的 `chargeName/typeCode/ybType` 等公共列，使同项目下 `UNIT/PURCHASE/LIMIT/LOT_NUMBER_COST/LOT_NUMBER_PRICE` 行保持同口径。
- **事件输出**：`ChargeItemDefinitionInfoBatchUpdatedEvent`。

#### 6.1.8 新增批号进价（`addLotPurchasePrice`）

- **业务意义**：为特定批号建立进价优先定价事实。
- **边界说明**：批号来源由库存/入库域提供，本域只承接写入。
- **前置条件**：`lotNumber`（批次号）必填（`PR-CHGDEF-002`）。
- **状态变化**：新建 → `ACTIVE`（**BR-CHGDEF-WR-001**）。
- **聚合内影响**：新增主表记录（`priceType=LOT_NUMBER_COST`，`lotNumber=批次号`）。
- **事件输出**：`PriceLineAddedEvent`。

#### 6.1.9 新增批号售价（`addLotSalePrice`）

- **业务意义**：为特定批号建立售价优先定价事实（**门诊/收费下游读**）。
- **边界说明**：批号来源由库存/入库域提供，本域只承接写入；**库房供应单据读路径不消费**本价类（见 **§1.5**）。
- **前置条件**：`lotNumber`（批次号）必填（`PR-CHGDEF-002`）。
- **状态变化**：新建 → `ACTIVE`（**BR-CHGDEF-WR-001**）。
- **聚合内影响**：新增主表记录（`priceType=LOT_NUMBER_PRICE`，`lotNumber=批次号`）。
- **事件输出**：`PriceLineAddedEvent`。

#### 6.1.10 按项目移除全部定价（`removePricingByInstance`）

- **业务意义**：清理项目全部定价事实，供重建或下架流程使用。
- **边界说明**：是否允许移除由应用层先判断记账引用风险。
- **前置条件**：项目定位键有效。
- **状态变化**：`N/A`。
- **聚合内影响**：按项目移除主表中全部定价记录。
- **事件输出**：`ChargeItemDefinitionRemovedByInstanceEvent`。

#### 6.1.11 批量保存定价（`saveAllChargeItemDefinitions`）

- **业务意义**：按项目执行定价行全量覆盖。
- **边界说明**：覆盖前清旧行，覆盖后形成新行集合。
- **前置条件**：输入行集合法；诊疗项目跳过 `PURCHASE`（`PR-CHGDEF-003`）。
- **状态变化**：新建 → `ACTIVE`（**BR-CHGDEF-WR-001**）。
- **聚合内影响**：旧行逻辑删除/替换，新行批量写入主表。
- **事件输出**：`PriceBatchAddedEvent`。

#### 6.1.12 批量停用当前启用定价行（`deactivateActivePriceLinesByCommand`）

- **业务意义**：调价审批通过前将匹配键下当前 **ACTIVE** 行迁为 **INACTIVE**（BR-PRC-APR-003）；与新增 ACTIVE 行配对使用，**禁止**物理删除或 `updatePriceLineByCommand` 覆盖旧价。
- **边界说明**：匹配键 = `instanceTable + instanceId + priceTypeCode + lotNo`（项目级价类 `lotNo` 为空）。
- **前置条件**：`DeactivateActivePriceLinesCommand.items` 非空。
- **状态变化**：`ACTIVE → INACTIVE`。
- **聚合内影响**：更新 `status_enum` 与审计字段。
- **事件输出**：`PriceLineUpdatedEvent`（逐行）。

### 6.2 读侧查询服务（ChargeItemDefinitionQueryService）

**当前价读路径（BR-PRICE-SRC-001 / BR-CHGDEF-QRY-004）**：除 `findAllDefinitionsByIdIn` 外，所有按实例/价类/批号解析「当前价」的方法仅返回 `is_deleted=false` 且 `status_enum=ACTIVE` 的行；单值解析（`resolveFirst*`、`findSaleLineForHealthcareServiceRegistration`）在 ACTIVE 集合内按 **priority 升序、同 priority 时 id 升序** 择优（与 `ClinicalCatalogPriceResolvesupport 一致）。`findAllDefinitionsByIdIn` 按主键快照加载，供 `ChargeItem.definition_id` 历史锚点，**不过滤** ACTIVE。

#### 6.2.1 按项目查售价（`findSaleItemByInstanceId`）

- **业务意义**：按项目定位键获取项目售价（`priceType = UNIT`）。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：`instanceTable + instanceId` + 主表 `priceType = UNIT`。
- **过滤口径**：按时间窗口过滤，按优先级排序。
- **返回约束**：无匹配返回空。

> **单表多行方案查询逻辑**：
>
> ```sql
> SELECT * FROM adm_charge_item_definition 
> WHERE instance_table = ? 
>   AND instance_id = ? 
>   AND price_type = 'UNIT' 
>   AND ? BETWEEN effective_start AND effective_end 
> ORDER BY priority DESC, created_at DESC 
> LIMIT 1
> ```
>
> - 查询性能：<1ms（命中索引，单表查询）
> - 逻辑统一，与批号价查询一致

#### 6.2.2 按项目查进价（`findPurchaseItemByInstanceId`）

- **业务意义**：按项目定位键获取统一进价（`priceType = PURCHASE`）。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：`instanceTable + instanceId` + 主表 `priceType = PURCHASE`。
- **过滤口径**：按时间窗口过滤，按优先级排序；诊疗项目可能无进价（`PR-CHGDEF-003`）。
- **返回约束**：无匹配返回空。

> **单表多行方案查询逻辑**：
>
> ```sql
> SELECT * FROM adm_charge_item_definition 
> WHERE instance_table = ? 
>   AND instance_id = ? 
>   AND price_type = 'PURCHASE' 
>   AND ? BETWEEN effective_start AND effective_end 
> ORDER BY priority DESC, created_at DESC 
> LIMIT 1
> ```
>
> - 查询性能：<1ms（命中索引，单表查询）

#### 6.2.3 按项目+批号取售价（`findSalePriceByInstanceIdAndLot`）

- **业务意义**：按批号优先规则获取可用售价。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：项目键 + 批号 + 主表 `priceType IN ('LOT_NUMBER_PRICE','UNIT')`。
- **过滤口径**：先批号价（`LOT_NUMBER_PRICE`），后回落统一价（`UNIT`）（`BR-CHGDEF-006`）。
- **返回约束**：无匹配返回空或回落值。

> **单表多行方案查询逻辑**：
>
> ```sql
> -- 1. 先查批号售价
> SELECT * FROM adm_charge_item_definition 
> WHERE instance_table = ? 
>   AND instance_id = ? 
>   AND price_type = 'LOT_NUMBER_PRICE' 
>   AND lot_number = ? 
>   AND ? BETWEEN effective_start AND effective_end 
> ORDER BY priority DESC, created_at DESC 
> LIMIT 1
>
> -- 2. 无批号售价则回落到项目售价
> SELECT * FROM adm_charge_item_definition 
> WHERE instance_table = ? 
>   AND instance_id = ? 
>   AND price_type = 'UNIT' 
>   AND ? BETWEEN effective_start AND effective_end 
> ORDER BY priority DESC, created_at DESC 
> LIMIT 1
> ```
>
> - 查询性能：<1ms（命中索引，单表查询）
> - 逻辑统一，无需区分主表/子表

#### 6.2.4 按项目+批号取进价（`findPurchasePriceByInstanceIdAndLot`）

- **业务意义**：按批号优先规则获取可用进价。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：项目键 + 批号 + 主表 `priceType IN ('LOT_NUMBER_COST','PURCHASE')`。
- **过滤口径**：先批号价（`LOT_NUMBER_COST`），后回落统一价（`PURCHASE`）（`BR-CHGDEF-006`）。
- **返回约束**：无匹配返回空或回落值。

> **单表多行方案查询逻辑**：
>
> ```sql
> -- 1. 先查批号进价
> SELECT * FROM adm_charge_item_definition 
> WHERE instance_table = ? 
>   AND instance_id = ? 
>   AND price_type = 'LOT_NUMBER_COST' 
>   AND lot_number = ? 
>   AND ? BETWEEN effective_start AND effective_end 
> ORDER BY priority DESC, created_at DESC 
> LIMIT 1
>
> -- 2. 无批号进价则回落到项目进价
> SELECT * FROM adm_charge_item_definition 
> WHERE instance_table = ? 
>   AND instance_id = ? 
>   AND price_type = 'PURCHASE' 
>   AND ? BETWEEN effective_start AND effective_end 
> ORDER BY priority DESC, created_at DESC 
> LIMIT 1
> ```
>
> - 查询性能：<1ms（命中索引，单表查询）
> - 逻辑统一，无需区分主表/子表

#### 6.2.5 查项目全部定价（`findPriceLinesByProjects`）

- **业务意义**：批量返回项目全量定价事实。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：单项目或多项目行集。
- **过滤口径**：同类型多行按时间排序输出（`BR-CHGDEF-QRY-002`）。

#### 6.2.6 挂号售价行解析（`findSaleLineForHealthcareServiceRegistration`）

- **业务意义**：为挂号组装解析 HealthcareService 对应售价行。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：按 `HealthcareService` 项目定位键取 `priceType = UNIT` 行。
- **返回约束**：无行时由应用层决定报错或回落策略。

> **单表多行方案**：查询逻辑与其他售价查询完全一致，通过 `priceType = UNIT` 过滤。

#### 6.2.7 按项目+批号取批号进价行（`findLotNumberCostByInstanceIdAndLot`）

- **业务意义**：仅查 `LOT_NUMBER_COST` 行（不回落 `PURCHASE`）；入库/成本核算批量场景。
- **入参**：`ChargeItemDefinitionQueryCommand`（`instanceTable`、`instanceId`、`lotNo`）。
- **出参**：`List<ChargeItemDefinition>`。

#### 6.2.8 按项目+批号取批号售价行（`findLotNumberPriceByInstanceIdAndLot`）

- **业务意义**：仅查 `LOT_NUMBER_PRICE` 行（不回落 `UNIT`）。
- **出参**：`List<ChargeItemDefinition>`。

#### 6.2.9 批号进价批量分组（`findLotNumberCostLinesGroupedByLotBatchKey`）

- **业务意义**：按 `ChargeItemDefinitionLotBatchKey#compositeKey()` 一次或多次下推分组，禁止循环单条仓储。
- **出参**：`Map<String, List<ChargeItemDefinition>>`。

#### 6.2.10 批号售价批量分组（`findLotNumberPriceLinesGroupedByLotBatchKey`）

- **业务意义**：同 **6.2.9**，`LOT_NUMBER_PRICE` 分支。

#### 6.2.11 解析首个稳定进价（`resolveFirstPurchasePriceValue`）

- **业务意义**：解析维护态 **`PURCHASE`** 首个非空单价（**每包装单位**）；**库房单据读锚**（无批号时）。
- **入参**：`instanceTable`、`instanceId`。
- **出参**：`BigDecimal` 或 `null`。

#### 6.2.12 解析首个稳定售价（`resolveFirstSalePriceValue`）

- **业务意义**：解析维护态 **`SALE`** 首个非空单价（**每包装单位**）；**非**库房供应单据读锚（门诊/订货展示等另途）。

#### 6.2.13 解析批号进价（`resolveFirstLotNumberCostPriceValue`）

- **业务意义**：指定批号 **`LOT_NUMBER_COST`** 首个非空单价（**每包装单位**）；**库房单据读锚**（有批号时）。

#### 6.2.14 批号价存在性（`existsLotNumberCostOrLotNumberPriceForInstanceAndLot`）

- **业务意义**：判断项目+批号是否已有批号进价或批号售价行。
- **出参**：`boolean`。

#### 6.2.15 按主键批量加载（`findAllDefinitionsByIdIn`）

- **业务意义**：列表装配批量取定价行实体。
- **出参**：`Map<Long, ChargeItemDefinition>`。

#### 6.2.16 按实例加载历史定价行（`listHistoricalDefinitionsByInstance`）

- **业务意义**：按 **`instanceTable + instanceId`** 加载该实例下**全部未软删**定价行（含 INACTIVE）；供工作量/日结 **PURCHASE/SALE 兄弟行回落**（**AS-RPT-001** **BR-RPT-WKL-010**；`PractitionerWorkloadCostEnrichmentsupport）。
- **入参**：`instanceTable`（可空）、`instanceId`（必填）。
- **出参**：`List<ChargeItemDefinition>`；无命中返回空列表。
- **边界说明**：只读；**禁止**应用层直查 Repository。

### 6.3 领域边界说明

- **写入类**：只变更 `adm_charge_item_definition`；不包含调价审批单状态机、入库单流转等。
- **查询类**：无写库；本域只表达价格事实，拆零比等由外层主数据计算。
- **跨域**：目录、入库、调价审批等为应用编排，本文只在动作末尾以"跨域说明"指向。
- 写编排由 `ChargeItemDefinitionDomainService` 承担；读编排由 `ChargeItemDefinitionQueryService` 承担。

## 7. *不变式*

| 编号                | 不变式                                                                              | 约束/落地要点                       |
| ----------------- | -------------------------------------------------------------------------------- | ----------------------------- |
| PR-CHGDEF-001     | 同一项目（`instanceTable + instanceId`）下，`chargeName/typeCode/ybType` 等展示字段在各行间应一致    | 由写侧"改项目信息"动作对全量行批量更新，或由编排保证一致 |
| PR-CHGDEF-002     | `LOT_NUMBER_COST/LOT_NUMBER_PRICE` 记录必须带批号写入 `lotNumber`                         | 写入动作断言必填                      |
| PR-CHGDEF-003     | 诊疗项目（`instanceTable = wkf_activity_definition`，见 `DM-WKF-001`）不设统一进价行 `PURCHASE` | 与现行实现一致：创建/编辑时跳过              |
| PR-CHGDEF-004     | 同一项目、同一 `priceType` 下时间窗口不应重叠（除优先级不同外）                                           | 写入时校验时间窗口冲突                   |
| PR-CHGDEF-005     | 同一项目、同一 `priceType` 下不应存在完全相同的记录（去重）                                             | 写入时校验重复记录                     |
| PR-CHGDEF-006     | 如果定价记录已被使用过（被收费单、订单等引用），修改价格必须通过调价功能（创建 PriceAdjustment 记录并经过审批）               | 调价流程完整性                       |
| BR-CHGDEF-007     | 批号优先：有批号且存在 `LOT_NUMBER_PRICE/LOT_NUMBER_COST` 记录则优先用批号价，否则回落 UNIT/PURCHASE      | 读侧取价方法遵循该规则                   |
| BR-CHGDEF-QRY-001 | 本域查询只负责价格事实；拆零比等由外层用药品/耗材主数据计算                                                   | 取价与计量拆分由上层组合                  |
| BR-CHGDEF-QRY-002 | 同一项目、同一 `priceType` 多行时按 `priority DESC, created_at DESC` 取最新，禁止依赖未排序 List 顺序    | 查询必须显式排序并取最新                  |
| BR-CHGDEF-QRY-003 | 所有价格查询都在主表执行，无子表查询                                                               | 单表多行方案统一查询逻辑                  |
| BR-CHGDEF-WR-001  | 凡 **insert** 定价行须写入 `status_enum=ACTIVE`（命令显式指定时以命令为准）；禁止落库 `status_enum` 为空 | `ChargeItemDefinitionDomainService#ensurePublicationStatusForInsert`；实体 `@PrePersist` 兜底 |
| BR-CHGDEF-WR-002  | 凡 **insert** 定价行须写入非空 `type_code`（fin_type_code）；批号价/调价可继承同实例历史行；号源定价固定诊察费 `1012`；库表 `type_code NOT NULL` | `ensureTypeCodeForInsert`；`resolveSiblingTypeCode`；Flyway V81 |
| BR-CHGDEF-QRY-004 | 「当前价」读路径仅 `status_enum=ACTIVE` 且未软删；`findAllDefinitionsByIdIn` 例外（历史 `definition_id` 锚点）；单值择优见 **§6.2** | 调价后 INACTIVE 旧行不得参与新开单取价 |

### 7.1 索引与查询约定

- `IDX-CHGDEF-001`：`(instance_table, instance_id)`，项目定位键查询（高频）。
- `IDX-CHGDEF-002`：`(instance_id, price_type)`，按项目和价格类型查询。
- `IDX-CHGDEF-003`：`(instance_id, price_type, lot_number)`，命中具体一行（含批号维度）。
- `IDX-CHGDEF-004`：`(price_type, effective_start, effective_end)`，时间窗口查询。
- `IDX-CHGDEF-005`：`(org_id, status_enum)`，机构和状态筛选。

> **单表多行方案索引优化**：
>
> - 所有查询都在主表上执行，索引设计统一
> - `(instance_id, price_type)` 覆盖80%查询场景
> - `(instance_id, price_type, lot_number)` 支持批号价查询
> - 时间窗口查询通过 `(price_type, effective_start, effective_end)` 覆盖索引范围查询
> - 统计分析可直接使用索引，无需全表扫描
> - 无需JOIN，索引利用效率最高

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文  | 聚合                   | 关联方式                           | 说明                   |
| ------ | -------------------- | ------------------------------ | -------------------- |
| 工作流上下文 | ActivityDefinition   | `instanceTable` + `instanceId` | 诊疗项目创建/更新时驱动定价行创建与更新 |
| 药品管理   | MedicationDefinition | `instanceTable` + `instanceId` | 药品目录创建/更新时驱动定价行创建与更新 |
| 临床管理   | DeviceDefinition     | `instanceTable` + `instanceId` | 器材目录创建/更新时驱动定价行创建与更新 |

### 8.2 下游影响

| 下游上下文 | 聚合                 | 触发方式 | 说明                                 |
| ----- | ------------------ | ---- | ---------------------------------- |
| 财务上下文 | ChargeItem         | 引用   | 记账取价依赖本表行事实（`definitionId` 与项目定位键） |
| 药品管理  | MedicationDispense | 引用   | 药品发放时按批号优先规则取价                     |

### 8.3 引用规则

| 模型名称                    | 关系类型 | 说明                                           |
| ----------------------- | ---- | -------------------------------------------- |
| `DM-ADM-011 ChargeItem` | 依赖   | 记账取价依赖本表行事实                                  |
| 目录（药品/耗材/诊疗）            | 依赖   | 驱动 `addItem`\* 与 `updatePricingItemInfo` 等动作 |
| 入库/库存                   | 依赖   | 审批通过后驱动批号价动作（应用编排）                           |

- 记账侧通过 `definitionId` 与项目定位键引用定价行；目录侧通过 `instanceTable` + `instanceId` 绑定业务主数据锚点。

### 8.4 事件流示意图

```text
ActivityDefinition / MedicationDefinition / DeviceDefinition（目录）
        └──(instanceTable+instanceId)──> ChargeItemDefinition
                                              ├── 定价行变更事件 ──> ChargeItem（取价）
                                              └── 调价历史 ──> 审计 / 报表
```

## 9. 补充

### 9.1 诊疗项目与药品/器材的定价差异

| 定价类型                       | 诊疗项目            | 药品 | 器材 |
| ------------------------------ | ------------------- | ---- | ---- |
| 项目售价（UNIT）               | 有                  | 有   | 有   |
| 项目进价（PURCHASE）           | 无（PR-CHGDEF-003） | 有   | 有   |
| 项目限价（LIMIT）              | 有                  | 有   | 有   |
| 批号进价（LOT\_NUMBER\_COST）  | 无                  | 有   | 有   |
| 批号售价（LOT\_NUMBER\_PRICE） | 无                  | 有   | 有   |

### 9.3 定价行优先级规则

取价时遵循以下优先级（BR-CHGDEF-006）：

1. **批号优先**：如果存在批号且存在 `LOT_NUMBER_PRICE/LOT_NUMBER_COST` 记录，则优先使用批号价
2. **生效时间优先**：同一类型价格下，优先匹配当前生效时间窗口内的行
3. **优先级排序**：同项目同 `priceType` 多行时按 `priority DESC, created_at DESC` 取最新（BR-CHGDEF-QRY-002）
4. **默认回落**：若无匹配项，回落到基础价格（UNIT/PURCHASE）

### 9.4 分层说明

- 值对象/枚举字典只要具备业务语义，可在 §4 直接定义并说明当前承载方式。
- 涉及新增实体/新表/新聚合边界时，才进入扩展规划并由后续迭代落地。
- 当前 `ChargeItemDefinition` 采用单表多行模型承载，所有价格统一在主表中表达。

> **单表多行方案扩展性优势**：
>
> - 新增价格类型：仅需增加 `PriceType` 枚举值，无需修改表结构
> - 新增定价维度：通过 `lot_number` 扩展批次
> - 新增业务规则：通过 `priority` 和时间窗口灵活控制
> - 统计分析友好：单一数据源，SQL查询简洁

### 9.5 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.1.12 | 2026-10-06 | §6.1：诊疗目录 `itemTypeCode` 维护页必填、后端有价才验（**BR-CAT-016**） |
| v1.1.11 | 2026-07-31 | **§1.4**：签发 FIFO 分段挂费须 `selectExecutionPriceLineForChargeDraft(lotNumber)`；有批号禁止跨批号 priority；对齐 **AS-OP-DOR-003 v1.56** / **BR-ORD-016** |
| v1.1.10 | 2026-07-15 | §1.5 / §4.1：药品/耗材维护态 PURCHASE/SALE 目录文案为采购参考价/零售参考价；批号价为真实进价/售价；增补 `list*LotPrices` 辅读锚点 |
| v1.1.9 | 2026-07-06 | §6.2.16 **`listHistoricalDefinitionsByInstance`**：按 instance 加载历史定价兄弟行，供工作量成本 PURCHASE/SALE 回落（**AS-RPT-001 v1.4**） |
| v1.1.8 | 2026-06-27 | **BR-CHGDEF-WR-002**：新建定价行 `type_code` 必填；批号价继承同实例 fin_type；号源定价诊察费 1012；Flyway V81 NOT NULL |
| v1.1.7 | 2026-06-27 | **BR-CHGDEF-WR-001**：新建定价行须落 `status_enum=ACTIVE`（`ensurePublicationStatusForInsert` + 实体 `@PrePersist`）；§3.1 `status_enum` 类型修正为 String |
| v1.1.5 | 2026-06-27 | **§1.2 定价查询**：与 **DM-ADM-011 BR-PRICE-SRC-001** 对齐——定价表供当前价/新建计费；历史已落库金额读 `ChargeItem` |
| v1.1.4 | 2026-05-29 | **§1.6 槽价/账单换算锚点**：定价 `price_value` 仅以**包装单位**为输入锚，允许包装→其它单位线性换算；禁止拆零/默认开立单位锚再往回乘；对齐 **BR-ORD-016** / **ProductDefinitionOrderChargePricingSupport** |
| v1.1.3 | 2026-05-29 | **§1.5 库房读锚两档**：维护态 **`PURCHASE`/`SALE`** 与批号价均为每包装；库房单据读锚仅 **`PURCHASE` + `LOT_NUMBER_COST`**；**`LOT_NUMBER_PRICE`** 同意入库仅写、库房不读；§1.3、§6.1.9、§6.2.11～13 |
| v1.1.1 | 2026-05-28 | **M1**：§6.2.7～15 批号价批量/解析/存在性/批量主键读法对齐 Java |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ChargeItemDefinitionDomainService`

| 方法 |
|------|
| `addItemRetailPrice` |
| `addItemPurchasePrice` |
| `addItemLimitPrice` |
| `updateItemRetailPrice` |
| `updateItemPurchasePrice` |
| `updateItemLimitPrice` |
| `updatePricingItemInfo` |
| `addLotPurchasePrice` |
| `addLotSalePrice` |
| `deactivateActivePriceLinesByCommand` |
| `removePricingByInstance` |
| `saveAllChargeItemDefinitions` |

### QueryService：`ChargeItemDefinitionQueryService`

| 方法 |
|------|
| `findSaleItemByInstanceId` |
| `findPurchaseItemByInstanceId` |
| `findSalePriceByInstanceIdAndLot` |
| `findPurchasePriceByInstanceIdAndLot` |
| `findLotNumberCostByInstanceIdAndLot` |
| `findLotNumberPriceByInstanceIdAndLot` |
| `findLotNumberCostLinesGroupedByLotBatchKey` |
| `resolveFirstPurchasePriceValue` |
| `resolveFirstSalePriceValue` |
| `mapFirstSalePriceValueByInstanceIds` |
| `resolveFirstLotNumberCostPriceValue` |
| `resolveFirstLotNumberPriceValue` |
| `findLotNumberPriceLinesGroupedByLotBatchKey` |
| `existsLotNumberCostOrLotNumberPriceForInstanceAndLot` |
| `listDefinitionIdsByInstanceAndLot` |
| `findPriceLinesByProjects` |
| `findSaleLineForHealthcareServiceRegistration` |
| `findAllDefinitionsByIdIn` |
| `listHistoricalDefinitionsByInstance` |
| `findTypeCodeFromInstancePricing` |

