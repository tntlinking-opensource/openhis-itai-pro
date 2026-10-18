# DM-CUS-012 OrderTemplate 组套模板

> **限界上下文**：本地扩展 · 组套模板（通用 / 中医 / 划价）  
> **聚合类型**：实体聚合（主表 + 明细表；头表为聚合根，明细为聚合内子实体）  
> **版本**：v1.4.5 · **日期**：2026-06-18

---

## 1. 概述

### 1.1 业务定义

组套模板（OrderTemplate）表达医疗机构内可复用的**组套模板头**及其**组套模板行**。通过头表 **`template_kind_enum`** 区分三类组套，**共用** `cus_order_template` / `cus_order_template_line` 主从表与同一套 DomainService / QueryService：

| 种类 | code | 套用消费方 |
|---|---|---|
| 通用组套 | `GENERAL` | 门诊/住院医生站 `saveDraftBatch` |
| 中医组套 | `TCM` | 同上；药品行带 `tcm_flag`、付数等 |
| 划价组套 | `PRICING` | 住院护士站 `confirmDirect/SupplementNurseStationPricing` |

每一行在业务上对应「将来套用时应生成的一条医嘱草稿或划价写行」的**目录锚点与默认参数**，而不是患者侧事实本身。

它回答的核心业务问题是：「**面对相同或相近场景时，临床人员如何不必逐项检索目录，仍能一次性带出结构化默认素材？**」——模板由个人、科室或全院维护；临床事实（`MedicationRequest` / `ServiceRequest` / `DeviceRequest` / `ChargeItem`）仅在套用阶段由他域创建，本聚合**不**承担患者医嘱生命周期、计费试算、库存扣减与合理用药判定。

**OrderTemplate** 为配置型主数据；**MedicationDefinition** / **ActivityDefinition** / **DeviceDefinition** 为被引用的目录主数据；套用后的医嘱事实以各医嘱聚合为准。本文档编码为 `DM-CUS-012`；对外信息交换以医嘱事实资源为主，模板标识可作为扩展追溯字段，**不强制**对外暴露模板结构。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 组套模板头维护 | 维护模板名称、编码、适用场景、归属层级、启用状态等头信息 |
| 组套模板行维护 | 在组套内维护多行：药品行、非药品行、耗材行及其默认剂量/频次/数量/成组键等 |
| 归属与可见性 | 支持个人、科室、全院、协定处方四级归属，支撑医生站/护士站按层级分段加载候选组套 |
| 引用完整性 | 组套模板行仅引用已发布或可引用的目录定义；不复制目录正文 |
| 生命周期控制 | 通过状态与逻辑删除控制模板是否参与默认检索与套用 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| **通用组套**（`template_kind_enum = GENERAL`） | 西医/通用成套医嘱：药品、诊疗、耗材组合 |
| **中医组套**（`template_kind_enum = TCM`） | 协定处方/中药方案：套用后药品行须带 `tcm_flag`、付数等（见 §3.1、§6.4） |
| 门诊/住院场景 | 按 `OrderTemplateScene` 区分或组合；同一模板可限制为仅门诊、仅住院或通用 |
| 专科路径化套餐 | 科室维护本科室高频方案 |
| 个人经验组套 | 医生沉淀个人模板 |
| 全院标准化方案 | 管理部门发布全院通用组套 |
| **划价组套**（`template_kind_enum = PRICING`） | 护士站成套划价：行仅 SERVICE / DEVICE（不含药品）；套用走护士划价写路径（§6.4.6） |
| 与诊疗定义的弱关联 | `ActivityDefinition` 可选引用推荐组套；权威定义以本 DM 为准 |

### 1.4 与关键上下游及交换边界的关系

| 维度 | 上游 | 本聚合 | 下游 |
|---|---|---|---|
| 职责 | 药品/诊疗/耗材目录主数据 | 组套模板头 + 组套模板行及默认参数 | 医嘱/划价事实由他域创建 |
| 引用 | 各定义主键 | `OrderTemplateId`、行上 `*DefinitionId` | GENERAL/TCM → MR/SR/DR；PRICING → SR/DR + ChargeItem |
| 套用 | — | 只读 `getOrderTemplateWithLines` | 应用层编排（§6.4） |

> **套用边界**：物化为患者事实时的就诊校验、成组签发、吸氧 Gate、套餐子项展开、合理用药与库存等，**不属于**本聚合，由各下游 DM 与消费方 AS 收口。

### 1.5 产品入口与领域统一（维护组套 vs 医嘱模板）

临床侧存在两种**产品叫法**，在 whale 中**共用同一聚合** `OrderTemplate`（`cus_order_template` / `cus_order_template_line`），通过头字段 **`template_source_enum`** 区分来源，读侧按来源过滤列表。

| 产品名称 | `template_source_enum` | 典型创建入口 | 典型查询入口 |
|---|---|---|---|
| **维护组套** | `MAINTAINED` | 组套维护端 CRUD（§6.4.4 IF-OT-MNT-*） | 维护端分页：`pageOrderTemplates(..., MAINTAINED, ...)` |
| **医嘱模板** | `FROM_ADVICE` | 医生站开嘱完成后「存为模板/另存组套」（§6.4.5 IF-OT-APP-05） | 医生站「我的模板」Tab：`listOrderTemplatesByOwnerAndScene(..., FROM_ADVICE)` |

**套用侧（引用）**：医生站开嘱面板可按 Tab 分别展示「组套 / 医嘱模板」，或合并展示（`templateSourceEnum` 传空）；**套用编排相同**——均为 **IF-08 预览结构 → 前端以行 ids 调 prepicked 目录 IF → 填充编辑区 → 既有** `save*AdviceDraftBatch` / 护士划价写 IF（**不**经本聚合 DomainService 物化事实，**不写** `package_id`）。

**与 openhis-sever 对照（Gap-OTL-REF-001）**：

| openhis | whale 映射 |
|---|---|
| `tmp_order_group` + `groupJson`：维护页组套（JSON  blob） | **不单独落表**；维护组套 → `OrderTemplate` + `template_source_enum = MAINTAINED` + 结构化行 |
| `tmp_orders_group_package` + detail：医生站存取、开嘱列表 | **合并进** `OrderTemplate`；医生站另存 → `FROM_ADVICE`；开嘱列表与维护组套**同表同 Query**，靠 `template_source_enum` 分 Tab |
| `OrdersGroupPackage` **无**来源字段；维护 JSON 组套与 package **双轨** | whale **单表 + 来源枚举**，避免双轨漂移 |

> **划价组套**（`template_kind_enum = PRICING`）仅通过维护端产生（`MAINTAINED`）；**禁止** `FROM_ADVICE`（**BR-OTL-030**）。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 明细表（同一聚合事务内维护）。

| 物理表（建议名，以库表定稿为准） | 说明 |
|---|---|
| `cus_order_template` | 组套模板头；主键 `id` |
| `cus_order_template_line` | 组套模板行；主键 `id`，外键 `order_template_id` → 组套模板头 |

**高频查询模式（建议索引口径）**：

- 头表：`(tenant_id, object_id, binding_enum, status_enum, deleted)` 列表与筛选；`(tenant_id, template_code)` 唯一（`template_code` 非空时）。
- 行表：`(order_template_id, deleted, sort_order)` 套内有序加载；`(order_template_id, group_key)` 成组行聚集（`group_key` 可空）。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 组套模板标识 | `OrderTemplateId` | 系统内部唯一标识，对应头表主键 `id`（`Long`，雪花或序列策略以实现为准） |

### 2.3 状态机

组套模板头以 `OrderTemplateStatus`（**存枚举 code（字符串）**于头表 `status_enum`）表达是否可被检索与套用；**逻辑删除**与「启用/停用」正交：已逻辑删除的头不参与任何默认业务查询。

```text
  [DRAFT] ──发布──> [ACTIVE] ──停用──> [RETIRED]
     │                  │                    │
     │                  └──重新启用───────────┘
     │
     └── 逻辑删除（deleted=true）──> 不参与默认查询（审计可查）
```

#### 状态定义

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `DRAFT` | 草稿 | 允许维护组套模板行；**不参与**默认「可套用组套」列表，除非产品明确开放草稿可见（以实现为准） |
| 2 | `ACTIVE` | 启用 | 允许维护组套模板行（见 **BR-OTL-018**）；参与检索与套用 |
| 3 | `RETIRED` | 停用 | **禁止**增删改组套模板行；不参与默认检索与套用 |

> **与逻辑删除关系**：`deleted = true` 时无论 `status_enum` 为何，均视为已移除；不允许对已删除头做写侧恢复（需新建模板）。

---

## 3. 实体

```text
OrderTemplate（聚合根，头表）
════════════════════════════════════════════════════════════
  │
  │ 1..*
  ▼
OrderTemplateLine（聚合内子实体，行表）
════════════════════════════════════════════════════════════
```

### 3.1 组套模板头（OrderTemplate）

OrderTemplate 为聚合根，对应 `cus_order_template` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 模板标识 | `id` | `Long` | 是 | 主键 |
| 归属对象标识 | `object_id` | `Long` | 是 | 语义由 `binding_enum` 决定，见 §4.1 |
| 绑定类型 | `binding_enum` | `Integer`（`OrderTemplateBinding`） | 是 | 个人 / 科室 / 全院 / 协定处方 |
| 适用场景 | `scene_enum` | `Integer`（`OrderTemplateScene`） | 是 | 门诊 / 住院 / 通用，见 §4.2 |
| 组套种类 | `template_kind_enum` | `String`（`OrderTemplateKind`） | 是 | GENERAL / TCM / PRICING，见 §4.6；默认 GENERAL |
| 模板来源 | `template_source_enum` | `String`（`OrderTemplateSource`） | 是 | MAINTAINED / FROM_ADVICE，见 §4.7；默认 MAINTAINED；**创建后不可变更**（**BR-OTL-031**） |
| 模板编码 | `template_code` | `String` | 否 | 院内唯一编码；非空时建议在租户内唯一，见 **BR-OTL-003** |
| 模板名称 | `name` | `String` | 是 | 展示名称 |
| 描述 | `description` | `String` | 否 | 临床说明、适应症提示等 |
| 列表排序权重 | `sort_weight` | `Integer` | 否 | **同层级内**列表顺序：**数值越小越优先（1 最优先）**；默认 `0` |
| 状态 | `status_enum` | `Integer`（`OrderTemplateStatus`） | 是 | 见 §2.3 / §4.3 |
| 备注 | `remark` | `String` | 否 | 可空 |
| 默认中药付数 | `default_chinese_herbs_dose_quantity` | `Decimal` | 条件 | 当 `template_kind_enum = TCM` 时必填；套用至药品行的默认付数 |
| 默认代煎标志 | `default_suffering_flag` | `Boolean` | 否 | TCM 组套可选；套用至 `CreateHerbalMedicationOrderDraftCommand.sufferingFlag` |

> **审计字段**：`created_at / updated_at / created_by / updated_by / deleted / tenant_id` 继承自 `BaseAuditedEntity`（或项目统一基类），不在属性表中重复列出。

> **迁移说明（Gap-OTL-001，已完成）**：`zxplat` Flyway `V88__cus_order_template_kind.sql` 已增补 `template_kind_enum` 及中医头/行字段；实体 `OrderTemplate` / `OrderTemplateLine`、枚举 `OrderTemplateKind` 与 DomainService / QueryService 已同步。

> **迁移说明（Gap-OTL-003，已完成）**：Flyway `V89__cus_order_template_source.sql` 增补 `template_source_enum NOT NULL DEFAULT 'MAINTAINED'`；枚举 `OrderTemplateSource` 与读侧 `templateSourceEnum` 过滤已同步。

### 3.2 组套模板行（OrderTemplateLine）

OrderTemplateLine 为聚合内子实体，对应 `cus_order_template_line` 表；**必须通过**聚合根 `OrderTemplate` 的领域服务维护，禁止绕过聚合根直接写行表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 行标识 | `id` | `Long` | 是 | 主键 |
| 组套模板头标识 | `order_template_id` | `Long` | 是 | 外键 → `cus_order_template.id` |
| 行类型 | `line_kind_enum` | `Integer`（`OrderTemplateLineKind`） | 是 | 药品 / 非药品 / 耗材，见 §4.4 |
| 药品定义标识 | `medication_definition_id` | `Long` | 条件 | 当 `line_kind_enum = MEDICATION` 时必填；否则必须为空 |
| 诊疗项目定义标识 | `activity_definition_id` | `Long` | 条件 | 当 `line_kind_enum = SERVICE` 时必填；否则必须为空 |
| 器材定义标识 | `device_definition_id` | `Long` | 条件 | 当 `line_kind_enum = DEVICE` 时必填；否则必须为空 |
| 成组键 | `group_key` | `String` | 否 | 非空时，套用后映射为同一成组医嘱分组语义（与他域 `group_id` 的映射策略以实现为准）；同键多行表示同一组 |
| 行内排序 | `sort_order` | `Integer` | 是 | **套内展示与套用顺序**：**数值越小越优先（1 最优先）** |
| 默认数量 | `default_quantity` | `Decimal` | 否 | 药品或耗材默认开立数量；非药品诊疗默认次数/数量 |
| 默认单位编码 | `default_unit_code` | `String` | 否 | 与单位字典一致 |
| 默认用药天数 | `default_days` | `Integer` | 否 | 门诊药品常用；住院语义以实现为准 |
| 频次标识 | `frequency_id` | `Long` | 否 | 引用 **Frequency**（`DM-CUS-008`）；仅药品行或需要频次的场景有意义 |
| 用药途径编码 | `route_code` | `String` | 否 | 与途径字典一致 |
| 剂量说明 | `dosage_text` | `String` | 否 | 单次剂量、规格换算等文字或结构化摘要（细粒度以实现为准） |
| 默认执行科室标识 | `default_perform_org_id` | `Long` | 否 | 非药品医嘱默认执行科室；药品发放位置等可沿用他域模板（如 `ServiceLocationTemplate`），本字段不重复承担「发药位置」职责 |
| 行备注 | `line_remark` | `String` | 否 | 组套模板行提示 |
| 行级中药付数 | `chinese_herbs_dose_quantity` | `Decimal` | 否 | 仅 TCM 组套 + MEDICATION 行；非空时覆盖头默认付数 |
| 行级代煎标志 | `suffering_flag` | `Boolean` | 否 | 仅 TCM 组套；非空时覆盖头 `default_suffering_flag` |

> **审计字段**：行表建议同样具备 `created_at / updated_at / created_by / updated_by / deleted / tenant_id`，以实现为准。

> **TCM 套用语义**：套用编排对 MEDICATION 行须写 `MedicationRequest.tcm_flag = true`，付数取行级优先、否则头默认（**BR-OTL-023**）。

> **PRICING 行语义**：`template_kind_enum = PRICING` 时行仅允许 SERVICE / DEVICE（**BR-OTL-026**）；DEVICE 行上 `default_perform_org_id` 为行级默认执行/发放科室意向（**不**等同套用期权威库位）；**不存单价**；套用期价/库位与护士划价目录 **IF-IP-PRC-01** 同源（**BR-OTL-028**），由前端 prepicked 目录解析后回传 `performLocation`。

### 3.3 子实体与一致性

- 子实体仅 **OrderTemplateLine** 一类；不在本版本引入「子组套嵌套引用」行类型（若后续需要，以独立 DM 修订为准）。
- 同一聚合内：行的增删改与头的状态迁移须满足 **§7** 不变式；跨表一致性由聚合根事务边界保证。

---

## 4. 值对象（含枚举）

### 4.1 模板绑定类型（`OrderTemplateBinding` → `binding_enum`）

对应主表字段 `binding_enum`，**使用字符串编码存储（存枚举 code）**。

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `PERSONAL` | 个人模板 | `object_id` 为医生账号 ID |
| 2 | `DEPARTMENT` | 科室模板 | `object_id` 为科室 / 机构 ID |
| 3 | `HOSPITAL` | 全院模板 | `object_id` 为顶层机构 ID |
| 4 | `AGREEMENT` | 协定处方模板 | `object_id` 为顶层机构 ID（与 HOSPITAL 同解析口径） |

### 4.2 模板适用场景（`OrderTemplateScene` → `scene_enum`）

对应主表字段 `scene_enum`，**使用字符串编码存储（存枚举 code）**。

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `OUTPATIENT` | 门诊 | 仅门诊医生站 / 门诊就诊上下文中展示与套用（口径与 Encounter 类型对齐以实现为准） |
| 2 | `INPATIENT` | 住院 | 仅住院医生站 / 住院就诊上下文中展示与套用 |
| 3 | `COMMON` | 通用 | 门诊与住院均可加载；套用侧仍须满足各医嘱聚合对就诊类型的约束 |

### 4.3 模板头状态（`OrderTemplateStatus` → `status_enum`）

对应主表字段 `status_enum`，**使用字符串编码存储（存枚举 code）**。

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `DRAFT` | 草稿 | 维护中，默认不参与套用列表 |
| 2 | `ACTIVE` | 启用 | 可套用 |
| 3 | `RETIRED` | 停用 | 不可维护行、不可套用 |

### 4.4 组套模板行类型（`OrderTemplateLineKind` → `line_kind_enum`）

对应行表字段 `line_kind_enum`，**使用字符串编码存储（存枚举 code）**。

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `MEDICATION` | 药品行 | 引用 `medication_definition_id`；套用目标为 **MedicationRequest** 草稿素材 |
| 2 | `SERVICE` | 非药品行 | 引用 `activity_definition_id`；套用目标为 **ServiceRequest** 草稿素材 |
| 3 | `DEVICE` | 耗材行 | 引用 `device_definition_id`；GENERAL/TCM → **DeviceRequest**；PRICING → 护士划价 **DeviceRequest** |

> **种类与行类型约束**：`template_kind_enum = PRICING` 时**禁止** MEDICATION 行（**BR-OTL-026**）；GENERAL/TCM 允许三种行类型。

### 4.5 模板列表优先级（隐含规则）

医生站/护士站合并展示个人、科室、全院、协定处方组套时，**分段展示**顺序为：

| 顺序 | 层级 | 说明 |
|---|---|---|
| 1 | 个人模板 | 最贴合个人习惯 |
| 2 | 科室模板 | 体现专科方案 |
| 3 | 全院模板 | 跨科室标准化方案 |
| 4 | 协定处方模板 | 全院级协定处方；`object_id` 与 HOSPITAL 同为顶层机构，独立分段展示 |

> 同层级内：先按 `sort_weight` **升序**（数值越小越靠前），再按 `id` **升序**稳定排序。  
> **套内**行顺序仅依赖 `sort_order` **升序**与 `id` 稳定序，不跨模板混排。

### 4.6 组套种类（`OrderTemplateKind` → `template_kind_enum`）

| value | code | info | 说明 |
|---|---|---|---|
| 1 | `GENERAL` | 通用组套 | 西医/通用；医生站套用 |
| 2 | `TCM` | 中医组套 | 协定处方；套用须 **BR-OTL-023**～**025** |
| 3 | `PRICING` | 划价组套 | 护士站套用；`scene_enum` 仅 INPATIENT 或 COMMON（**BR-OTL-027**）；行仅 SERVICE/DEVICE |

### 4.7 模板来源（`OrderTemplateSource` → `template_source_enum`）

| value | code | info | 说明 |
|---|---|---|---|
| 1 | `MAINTAINED` | 维护组套 | 组套维护端提前配置；默认来源 |
| 2 | `FROM_ADVICE` | 医嘱模板 | 医生站开嘱完成后另存；仅 GENERAL/TCM kind（**BR-OTL-030**） |

> **查询口径**：维护端列表 **默认不过滤** `template_source_enum`（含 MAINTAINED 与 FROM_ADVICE，可传参精确筛选）；医生站「组套」Tab 固定 `MAINTAINED`；「医嘱模板 / 我的模板」Tab 固定 `FROM_ADVICE`；合并引用列表传空不过滤（**BR-OTL-QRY-002**）。列表 `object_id` 与另存写路径须同一 `OrderTemplateScopeResolver` 口径（**BR-BAS-OTL-008**）。

### 4.8 术语表

| 名词 | 定义 |
|---|---|
| 组套模板 | 领域统一名称；对应聚合 `OrderTemplate`（含维护组套与医嘱模板） |
| 维护组套 | 产品名；`template_source_enum = MAINTAINED` |
| 医嘱模板 | 产品名；医生开嘱后另存；`template_source_enum = FROM_ADVICE`；与 §9.4 ActivityDefinition 侧「推荐组套引用」不同 |
| 组套模板头 | 一条 `OrderTemplate` 记录，表达一个命名方案及其归属、场景、种类与来源 |
| 组套模板行 | 一条 `OrderTemplateLine` 记录，表达组套内一项可物化为某类医嘱的目录引用与默认值 |
| 套用 | 消费方读取组套模板行并在下游创建事实；见 **§6.4**（医生站 / 护士站分路径） |
| 成组键 | 行上 `group_key`；GENERAL/TCM 套用后映射 `MedicationRequest.group_id`（**BR-OTL-APY-003**） |
| 组套实例标识 | GENERAL/TCM 套用写 `package_id = OrderTemplateId`；PRICING 可选扩展 `source_template_id`（§9.5） |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 组套模板已创建 | `OrderTemplateCreatedEvent` | 头表新建成功 | `orderTemplateId`、`objectId`、`bindingEnum`、`sceneEnum` | 检索索引、缓存（可选） |
| 组套模板已更新 | `OrderTemplateUpdatedEvent` | 头表关键属性变更（名称、场景、状态、排序等） | `orderTemplateId`、变更摘要键 | 同上 |
| 组套模板已停用 | `OrderTemplateRetiredEvent` | 状态迁移至 `RETIRED` | `orderTemplateId` | 同上 |
| 组套模板已发布 | `OrderTemplatePublishedEvent` | 状态自 `DRAFT` → `ACTIVE` | `orderTemplateId` | 同上 |
| 组套模板已逻辑删除 | `OrderTemplateDeletedEvent` | 头表逻辑删除 | `orderTemplateId` | 同上 |
| 组套模板行已新增 | `OrderTemplateLineAddedEvent` | 行插入成功 | `orderTemplateId`、`lineId`、`lineKindEnum` | 同上 |
| 组套模板行已更新 | `OrderTemplateLineUpdatedEvent` | 行属性变更 | `orderTemplateId`、`lineId` | 同上 |
| 组套模板行已移除 | `OrderTemplateLineRemovedEvent` | 行逻辑删除或物理删除策略触发时 | `orderTemplateId`、`lineId` | 同上 |

> **批量策略**：若实现选择合并通知，可增设 `OrderTemplateLinesBatchChangedEvent`（载荷为 `orderTemplateId` + `lineIds[]`），与 **§5.2** 二选一，以实现为准。

### 5.2 事件触发规则

| 写侧操作（概念名，方法名以实现为准） | 发布事件 |
|---|---|
| 创建组套模板头 | `OrderTemplateCreatedEvent` |
| 更新组套模板头 | `OrderTemplateUpdatedEvent` |
| 发布（`DRAFT`→`ACTIVE`） | `OrderTemplatePublishedEvent` |
| 停用（→`RETIRED`） | `OrderTemplateRetiredEvent` |
| 逻辑删除组套模板头 | `OrderTemplateDeletedEvent` |
| 新增行 | `OrderTemplateLineAddedEvent` |
| 更新行 | `OrderTemplateLineUpdatedEvent` |
| 删除行 | `OrderTemplateLineRemovedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（OrderTemplateDomainService）

> 写侧由应用层编排事务边界后调用；本服务方法**不使用**应用层注解声明事务，与项目统一约定一致。

#### 6.1.1 创建组套模板（`createOrderTemplate`）

- **业务意义**：新建组套模板头，初始状态为 `DRAFT`，用于后续维护组套模板行并发布。
- **边界说明**：不创建任何 `MedicationRequest` / `ServiceRequest` / `DeviceRequest`；不写目录主数据。
- **入参**：归属对象标识、绑定类型、适用场景、**组套种类**、名称、可选编码/描述/排序权重/备注；TCM 须含默认付数。
- **出参**：`OrderTemplateId`。
- **前置条件**：**BR-OTL-001**～**003**、**005**。
- **状态变化**：新建为 `DRAFT`。
- **聚合内影响**：插入头表一行。
- **事件输出**：`OrderTemplateCreatedEvent`。
- **失败语义**：编码唯一冲突、必填缺失、归属语义非法时整单拒绝。

#### 6.1.2 更新组套模板头（`updateOrderTemplateHeader`）

- **业务意义**：变更名称、场景、编码、描述、排序权重、备注等头属性。
- **边界说明**：不改变行集合；不触发套用。
- **入参**：`OrderTemplateId` 及待变更字段子集。
- **出参**：`—`。
- **前置条件**：**BR-OTL-004**、**007**；`RETIRED` 下禁止变更（**BR-OTL-017**）。
- **状态变化**：无（除显式调用发布/停用外）。
- **聚合内影响**：更新头表字段。
- **事件输出**：`OrderTemplateUpdatedEvent`。
- **失败语义**：乐观锁或版本冲突策略以实现为准。

#### 6.1.3 发布组套模板（`publishOrderTemplate`）

- **业务意义**：将 `DRAFT` 推进为 `ACTIVE`，使模板进入默认可套用集合。
- **边界说明**：不要求套内必须有行方可创建头，但发布时须满足 **BR-OTL-006**。
- **入参**：`OrderTemplateId`。
- **出参**：`—`。
- **前置条件**：**BR-OTL-004**、**006**、**007**。
- **状态变化**：`DRAFT` → `ACTIVE`。
- **聚合内影响**：更新 `status_enum`。
- **事件输出**：`OrderTemplatePublishedEvent`。
- **失败语义**：当前非 `DRAFT`、或无有效行时拒绝。

#### 6.1.4 停用组套模板（`retireOrderTemplate`）

- **业务意义**：将模板标记为 `RETIRED`，禁止套用与行维护。
- **边界说明**：不级联删除患者侧历史医嘱。
- **入参**：`OrderTemplateId`。
- **出参**：`—`。
- **前置条件**：**BR-OTL-004**；当前为 `ACTIVE` 或 `DRAFT`（是否允许从 `DRAFT` 直接停用以实现与 **BR-OTL-019** 为准）。
- **状态变化**：→ `RETIRED`。
- **聚合内影响**：更新 `status_enum`。
- **事件输出**：`OrderTemplateRetiredEvent`。
- **失败语义**：已删除、已是 `RETIRED` 时幂等或拒绝以实现为准。

#### 6.1.5 逻辑删除组套模板（`deleteOrderTemplate`）

- **业务意义**：头表逻辑删除，模板默认不可见；行随聚合策略一并逻辑删除或保留为孤儿禁止策略见 **BR-OTL-020**。
- **边界说明**：不提供写侧恢复；需重新 `createOrderTemplate`。
- **入参**：`OrderTemplateId`。
- **出参**：`—`。
- **前置条件**：**BR-OTL-004**。
- **状态变化**：`deleted=true`；行表建议同步 `deleted=true`（**BR-OTL-021**）。
- **聚合内影响**：头、行软删（推荐）或头删 + 行物理删（须满足审计与可追溯要求）。
- **事件输出**：`OrderTemplateDeletedEvent`；行级可批量子事件或省略以实现为准。
- **失败语义**：重复删除幂等跳过。

#### 6.1.6 新增组套模板行（`addOrderTemplateLine`）

- **业务意义**：在指定组套内增加一行目录引用及默认值。
- **边界说明**：单条新增；批量由应用层多次调用或扩展批量命令以实现为准。
- **入参**：`OrderTemplateId`、行类型、对应定义标识、可选成组键、排序、默认字段集。
- **出参**：`OrderTemplateLineId`。
- **前置条件**：**BR-OTL-004**、**007**～**016**、**018**。
- **状态变化**：无（头状态不变）。
- **聚合内影响**：插入行表。
- **事件输出**：`OrderTemplateLineAddedEvent`。
- **失败语义**：引用不存在、类型与引用不一致时拒绝。

#### 6.1.7 更新组套模板行（`updateOrderTemplateLine`）

- **业务意义**：变更某一行的默认参数、成组键或排序等。
- **边界说明**：一般不允许切换 `line_kind_enum` 与主引用（避免与审计语义混淆）；若产品允许「改类型」，应建模为先删后增，并在实现中显式约定迁移规则。
- **入参**：`OrderTemplateLineId` 及变更子集。
- **出参**：`—`。
- **前置条件**：**BR-OTL-009**、**018**。
- **状态变化**：无。
- **聚合内影响**：更新行表。
- **事件输出**：`OrderTemplateLineUpdatedEvent`。
- **失败语义**：行不存在、引用非法时拒绝。

#### 6.1.8 移除组套模板行（`removeOrderTemplateLine`）

- **业务意义**：逻辑删除一行或物理删除（策略见 **BR-OTL-022**）。
- **边界说明**：不删除目录定义。
- **入参**：`OrderTemplateLineId`。
- **出参**：`—`。
- **前置条件**：**BR-OTL-009**、**018**。
- **状态变化**：行 `deleted=true` 或物理移除。
- **聚合内影响**：行表更新或删除。
- **事件输出**：`OrderTemplateLineRemovedEvent`。
- **失败语义**：行不存在幂等跳过或拒绝以实现为准。

### 6.2 读侧查询服务（OrderTemplateQueryService）

#### 6.2.1 按标识加载组套及行（`getOrderTemplateWithLines`）

- **业务意义**：供维护端或套用侧一次性加载头与全部有效行。
- **边界说明**：只读；不 join 患者与就诊。
- **查询范围**：头表一行 + 行表 `order_template_id` 且默认 `deleted=false`。
- **过滤口径**：头未逻辑删除；可选仅 `ACTIVE`（参数控制）。
- **组装规则**：行按 `sort_order` 升序、`id` 升序。
- **返回约束**：目录显示名、价格、库存等他域投影由消费方扩展，本查询可仅返回 ID 与默认字段。

#### 6.2.2 分页查询组套模板头（`pageOrderTemplates`）

- **业务意义**：管理端按归属、场景、状态、种类、来源等分页检索组套模板头。
- **边界说明**：只读；**名称/编码关键字**不在本方法内过滤，由应用层 IF-BAS-OTL-02 对**当前页**结果做子串过滤（`SearchCodeUtil` 语义）。
- **查询范围**：头表有效数据（软删由底座默认过滤）。
- **过滤口径**：
  - `bindingEnum`、`sceneEnum`、`statusEnum`、`templateKindEnum`、`templateSourceEnum` **均可空**；非空时按等值下推 SQL。
  - **维护端典型传参**：`templateKindEnum` + `templateSourceEnum=MAINTAINED` 必填；`bindingEnum` / `sceneEnum` / `statusEnum` 随筛选表单可选。
  - 当 `bindingEnum`、`sceneEnum`、`statusEnum` **三者均非空**时，走 Repository **派生分页**（快路径）；**任一为空**时，走 `JpaSpecificationExecutor` + 动态 `Specification`（仅拼装非空谓词）。
- **组装规则**：分页 DTO；排序：`sort_weight` 升序、`id` 升序。
- **返回约束**：不保证含行数统计；若需要 `lineCount` 以实现侧可选子查询或冗余字段为准。

#### 6.2.3 按归属与场景列可用组套（`listOrderTemplatesByOwnerAndScene`）

- **业务意义**：医生站按当前 `object_id` + `binding_enum` + 就诊场景列出候选组套模板头（单层，不合并多归属）。
- **边界说明**：只读；**不**在本方法内合并个人/科室/全院/协定处方（合并规则见 **BR-OTL-QRY-001**）；若需单次合并，由上层连续调用或单独查询方法扩展，以实现为准。
- **查询范围**：头表；`status_enum = ACTIVE` 且未删除；`scene_enum` 与请求场景匹配或 `COMMON`。
- **过滤口径**：绑定类型 + `object_id` 精确匹配 + **`template_kind_enum` 精确匹配**。
- **组装规则**：`sort_weight` 升序、`id` 升序。
- **返回约束**：不返回已停用、已删除、草稿（除非参数 `includeDraft=true` 且权限允许）。

### 6.3 领域边界说明

- 本聚合**仅**维护组套模板头与行；**不**创建或修改 `MedicationRequest`、`ServiceRequest`、`DeviceRequest`。
- 目录侧「是否可引用」状态以各定义 DM 为准；本聚合在写行时执行 **BR-OTL-012**～**014** 所述引用策略。
- 合理用药、库存、分方、医保控费等均不在本聚合内执行。
- 展示层合并多归属列表、与诊断模板（`ConditionTemplate`）联动过滤等，属产品编排，**不**强制进入本查询服务单一方法（见 **BR-OTL-QRY-001**）。

### 6.4 套用消费编排（应用层，非 DomainService 方法）

维护完成后「顺其自然可用」依赖下列编排。**套用物化**由前端串联 **IF-BAS-OTL-08**（结构）→ **prepicked 目录 IF**（价/库位）→ 下游医生站/护士站既有写 IF；**不**在本聚合 DomainService 内批量创建 Request/ChargeItem。

#### 6.4.1 加载可套用组套（IF-BAS-OTL-07 / IF-OT-APP-01）

- **入口**：`OrderTemplateAppService.listOrderTemplatesForClinicalShortcut(...)`（医生站 / 护士划价 Tab）
- **读侧**：`listOrderTemplatesByOwnerAndScene`（个人 / 科室 / 全院 / 协定处方）+ **BR-OTL-QRY-001** 分段
- **过滤**：`status_enum = ACTIVE`；`template_kind_enum` 与前端 Tab（医嘱/中医/划价）一致；scene 与就诊匹配或 COMMON

#### 6.4.2 预览套用结构（IF-BAS-OTL-08 / IF-OT-APP-02）

- **入口**：`OrderTemplateAppService.previewOrderTemplateApply(...)`
- **步骤**：
  1. `getOrderTemplateWithLines`；断言 ACTIVE（**BR-OTL-APY-001**）及 scene/kind 与上下文一致（**BR-OTL-APY-002**）
  2. **仅**映射头行结构（ids、数量、频次/用法、成组键、TCM 付数/代煎等）；**不** enrich 目录展示名、**不**查价、**不**解析发药库位
  3. TCM：带头/行默认付数/代煎字段随结构返回
- **不落库**；价/库位/展示名由前端下一步 **prepicked 目录 IF**（IF-DOR-11 / IF-IP-DOR-11 / IF-IP-PRC-01）补齐

#### 6.4.3 套用至医嘱草稿（前端编排，**废止** IF-OT-APP-03 后端代套用）

- **产品路径**：IF-08 预览 → 组装 `prepickedProductKeys` → 调 **IF-DOR-11** / **IF-IP-DOR-11** 目录 → 填充编辑区（含 `defaultDispenseLocationId`）→ 用户确认后调 **`DoctorStationAdviceAppService` / `InpatientDoctorStationAdviceAppService` · `save*AdviceDraftBatch`**
- **映射要点**（前端负责，与 §3.2 行默认值对齐）：
  - **MEDICATION** → `Save*MedicationAdviceDraftLinePayload`；`group_key` 相同者套用后须同一 `group_id`（**BR-OTL-APY-003**）
  - **SERVICE** → `Save*ServiceAdviceDraftLinePayload`；**不自动展开**套餐子项（仍由 `childOverridesJson` 交互）
  - **DEVICE** → `Save*DeviceAdviceDraftLinePayload`；写路径回传 **`dispenseLocationId`**（目录已解析库位，**BR-OTL-028**）
  - TCM（**BR-OTL-023**～**025**）：MEDICATION 行 `tcm_flag = true`；付数/代煎按行→头默认
- **不写** `package_id` / `source_template_id`（本期不追溯）
- **不**在本步骤签发；签发仍走既有 `signBatch`
- **住院边界**：仅 **TEMPORARY** 临嘱写路径使用 `dispenseLocationId` 暂存定价；**LONG_TERM** 不适用（见 **AS-IP-DOR-003**）

#### 6.4.4 维护端 CRUD（IF-OT-MNT-*）

| IF | 用户动作 | 编排 |
|---|---|---|
| IF-OT-MNT-01 | 初始化 | Binding / Scene / Kind / LineKind / Status |
| IF-OT-MNT-02 | 分页 | `pageOrderTemplates` + kind 筛选 |
| IF-OT-MNT-03 | 详情 | `getOrderTemplateWithLines` + enrich |
| IF-OT-MNT-04 | 保存头 | create / updateHeader |
| IF-OT-MNT-05 | 保存行 | add / update / remove（支持全量对齐 diff） |
| IF-OT-MNT-06 | 发布/停用/删除 | publish / retire / delete |

#### 6.4.5 医生站另存为医嘱模板（IF-OT-APP-05）

- **入口**：`OrderTemplateAppService.saveFromAdviceDraft(SaveOrderTemplateFromAdviceRequest)`
- **业务意义**：将当前就诊已开/草稿医嘱批量复制为可复用模板（对应 openhis `OrdersGroupPackageController.save-*` + detail 落库）。
- **步骤**：
  1. 读取当前医生站草稿/选中行（MedicationRequest / ServiceRequest / DeviceRequest 读模型），映射为 `AddOrderTemplateLineCommand` 列表（含 `group_key`、剂量/频次/付数等）。
  2. `CreateOrderTemplateCommand`：`template_source_enum = FROM_ADVICE`；`template_kind_enum` 由 `tcm_flag` 推导 GENERAL/TCM；`binding_enum` 默认 **DEPARTMENT**（`object_id` = 当前会话科室），用户选择个人时为 **PERSONAL**（`object_id` = 当前登录医务人员 id）。
  3. 调用 `createOrderTemplate` + 批量 `addOrderTemplateLine`；**建议**同一事务内 `publishOrderTemplate` 直接 `ACTIVE`（对齐 openhis 存完即可引用，无 DRAFT 停留）。
  4. 维护端 IF-OT-MNT 路径创建时 **`template_source_enum = MAINTAINED`**（默认），走 DRAFT→发布常规流程。
- **边界**：不复制患者事实 ID；仅复制目录引用与默认参数；PRICING kind 禁止（**BR-OTL-030**）。

#### 6.4.6 读侧参数扩展

| 方法 | 说明 |
|---|---|
| `pageOrderTemplates` | `bindingEnum?`、`sceneEnum?`、`statusEnum?`、`templateKindEnum?`、`templateSourceEnum?` 均可空；维护端传 `MAINTAINED` + 当前 Tab `kind`；**三者 binding/scene/status 均非空**时走派生分页，**否则**走 `Specification` 动态谓词 |
| `listOrderTemplatesByOwnerAndScene` | 增加 `templateKindEnum`（必填）、`templateSourceEnum?`（可空=组套+医嘱模板合并） |

#### 6.4.7 套用 — 护士划价（前端编排，**废止** IF-OT-APP-04 后端代套用）

- **产品路径**：IF-08（`applyContextCode = NURSE_PRICING`）→ `prepickedProductKeys` → **IF-IP-PRC-01** 目录（耗材 `defaultDispenseLocationId`）→ 填充划价编辑区 → **`confirmDirectNurseStationPricing` / `confirmSupplementNurseStationPricing`**
- **映射要点**：
  - 按 `sort_order` 映射数量/单位；价由目录 IF + 写 IF 内 **`ClinicalCatalogPriceResolveSupport#resolveDraftChargePriceLine`** 权威重算（**BR-OTL-028**）
  - 耗材写路径回传 **`performLocation`**（= 目录 `defaultDispenseLocationId`）；写路径 **不**二次 match 取药科室模板
  - SERVICE 父项带子项时，写路径 **SR 仍一条父请求** + Charge **父账单+子项账单**（`parent_id`、冻价，**BR-IP-PRC-011**）；开立/划价界面只见父项
  - 直接划价：`parentRequestAnchor` 为空；补费：携带父锚点（**BR-OTL-APY-005**）
- **不**在本聚合存储单价；**不**执行吸氧 Gate（写路径负责）

---

## 7. 不变式

| 编号 | 不变式 | 约束 / 落地要点 |
|---|---|---|
| BR-OTL-001 | 头表必须同时具有合法的 `binding_enum` 与 `object_id` | 语义一致性与查询正确性 |
| BR-OTL-002 | `scene_enum` 必须为已定义枚举值 | 非法场景拒绝落库 |
| BR-OTL-003 | `template_code` 非空时，在租户 + 归属键（`object_id`+`binding_enum`）范围内唯一 | 唯一索引兜底 |
| BR-OTL-004 | 写侧操作的头必须存在且 `deleted = false` | 防止操作幽灵头 |
| BR-OTL-005 | 模板名称 `name` 不得为空串 | 展示与审计需要 |
| BR-OTL-006 | 发布为 `ACTIVE` 时，组套内须至少存在一条未删除的有效行 | 空壳组套不可发布 |
| BR-OTL-007 | 已逻辑删除的头禁止一切写侧变更（除幂等删除） | — |
| BR-OTL-008 | 行类型与引用列互斥且一致 | `MEDICATION` 仅 `medication_definition_id` 非空；`SERVICE` 仅 `activity_definition_id`；`DEVICE` 仅 `device_definition_id` |
| BR-OTL-009 | 操作的行必须属于指定头且行自身未删除 | 防止跨聚合篡改 |
| BR-OTL-010 | `sort_order` 须为整数；**套内排序数值越小越优先（1 最优先）** | 与全域优先级约定一致 |
| BR-OTL-011 | `sort_weight` 须为非负整数（若未填则默认 `0`） | 头表列表排序 |
| BR-OTL-012 | 新增或更新行时，被引用的定义须处于「允许被组套模板引用」的发布态 | 引用完整性；具体状态码与各定义 DM 对齐 |
| BR-OTL-013 | 若定义已停用，不允许**新建**行引用该定义；已存在行的展示标记由他域读侧处理 | 与 **BR-CTL-006** 同类策略 |
| BR-OTL-014 | 同一头下允许同一药品/项目/耗材定义出现多行（例如溶媒与药品分行） | **禁止**仅以定义 ID 做套内唯一约束 |
| BR-OTL-015 | `group_key` 长度与字符集受院内规范限制 | 防止滥用；长度上限以实现为准 |
| BR-OTL-016 | `default_quantity` 若填写则须 `> 0`（退耗材负向等不适用于组套模板行） | — |
| BR-OTL-017 | `RETIRED` 状态禁止修改头属性 | 需先 `ACTIVE` 或新建版本策略见 **§9.2** |
| BR-OTL-018 | 仅 `DRAFT` 与 `ACTIVE` 允许维护组套模板行；`RETIRED` 禁止 | 与 **§2.3** 一致 |
| BR-OTL-019 | 是否允许 `DRAFT` 直接 `RETIRED` 由产品与 **6.1.4** 实现择一；文档不强制 |
| BR-OTL-020 | 逻辑删除头时，行不得留在「未删除且仍指向该头」的可引用状态 | 推荐级联软删行 **BR-OTL-021** |
| BR-OTL-021 | 头逻辑删除时，同步将该头下全部行标记 `deleted=true` | 数据一致性 |
| BR-OTL-022 | 行删除默认逻辑删除以保留审计；若物理删除须保证审计日志或变更流水可追溯 | 与合规要求对齐 |
| BR-OTL-023 | `template_kind_enum = TCM` 时，头 `default_chinese_herbs_dose_quantity` 必填且 > 0 | 中医组套发布前置 |
| BR-OTL-024 | TCM 组套内 MEDICATION 行套用须写 `tcm_flag = true` | 与 **DM-MED-003** 中药语义一致 |
| BR-OTL-025 | TCM 组套 SERVICE/DEVICE 行不受付数约束；若产品禁止 TCM 组套含非药行以实现为准 | 协定处方以药品为主 |
| BR-OTL-APY-001 | 仅 ACTIVE 组套可套用 | 套用入口 |
| BR-OTL-APY-002 | 套用 scene/kind 须与当前就诊/前端上下文一致 | 防止错场景套用 |
| BR-OTL-APY-003 | 同 `group_key` 多行套用后须处于同一 `group_id` | GENERAL/TCM 药品成组 |
| BR-OTL-026 | `template_kind_enum = PRICING` 时禁止 MEDICATION 行；增行/发布校验 | 划价组套 |
| BR-OTL-027 | PRICING 组套 `scene_enum` 仅允许 INPATIENT 或 COMMON | 与护士划价场景一致 |
| BR-OTL-028 | 组套表**不存**单价/总价；**IF-08 预览不查价**；套用期与目录 IF **共用** `ClinicalCatalogPriceResolvesupport（诊疗 SALE；药耗 FIFO 批号→LOT_NUMBER_PRICE）；库位读侧由目录 IF 解析、写侧前端回传 `dispenseLocationId`/`performLocation` | 与 IF-DOR-11 / IF-IP-DOR-11 / IF-IP-PRC-01 一致 |
| BR-OTL-029 | `template_source_enum` 必填且为已定义枚举值 | 来源可查询 |
| BR-OTL-030 | `template_source_enum = FROM_ADVICE` 时禁止 `template_kind_enum = PRICING` | 医嘱模板仅医生站 GENERAL/TCM |
| BR-OTL-031 | `template_source_enum` 创建后不可变更 | 防止维护端与医生端语义串用 |
| BR-OTL-APY-004 | 仅 ACTIVE 且 kind 与入口一致可套用 | 防止 GENERAL 组套走进护士划价 |
| BR-OTL-APY-005 | PRICING 直接划价套用不得夹带 parentRequestAnchor | 对称 **BR-OTL-APY-002** |

### 7.1 读侧查询规则（查询服务）

| 编号 | 规则 ID | 规则描述 |
|---|---|---|
| R-QRY-1 | `BR-OTL-QRY-001` | 多归属（个人/科室/全院/协定处方）分段展示时，顺序为个人 → 科室 → 全院 → 协定处方；同一定义多模板头共存时不自动去重（组套模板头为不同聚合实例） |
| R-QRY-2 | `BR-OTL-QRY-002` | 列表须支持按 `template_source_enum` 精确过滤；医生站 Tab 与维护端列表不得混查（除非显式传空合并） |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 / 资源 | 关联方式 | 说明 |
|---|---|---|---|
| 用药域 | MedicationDefinition | `medication_definition_id` | 药品行引用 |
| WKF 域 | ActivityDefinition | `activity_definition_id` | 非药品行引用 |
| ADM 域 | DeviceDefinition | `device_definition_id` | 耗材行引用 |
| 本地扩展 | Frequency | `frequency_id` | 可选默认频次 |
| 组织架构 / 用户 | Organization、UserAccount | `object_id` + `OrderTemplateBinding` | 归属语义 |
| 就诊域 | Encounter | 无直接外键 | 场景枚举与就诊类型对齐在消费侧完成 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 用药域 | MedicationRequest | 读模板 / 追溯 | 套用生成草稿时消费组套模板行默认值 |
| WKF 域 | ServiceRequest | 读模板 / 套用 | GENERAL/TCM/PRICING 均可能生成 |
| WKF 域 | DeviceRequest | 读模板 / 套用 | 同上 |
| ADM 域 | ChargeItem | PRICING 套用 | 护士划价写路径 |
| 缓存 / 检索索引 | — | 领域事件 | 订阅 **§5.1** 事件刷新组套缓存（可选） |

### 8.3 引用规则

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-MED-001 MedicationDefinition` | 关联 | 药品行引用 |
| `DM-WKF-001 ActivityDefinition` | 关联 | 非药品行引用 |
| `DM-ADM-008 DeviceDefinition` | 关联 | 耗材行引用 |
| `DM-CUS-008 Frequency` | 关联 | 行可选频次默认 |
| `DM-MED-003 MedicationRequest` | 依赖 | 医嘱事实，非本聚合子实体 |
| `DM-WKF-004 ServiceRequest` | 依赖 | 同上 |
| `DM-WKF-003 DeviceRequest` | 依赖 | 同上 |

### 8.4 事件流示意图

```text
MedicationDefinition / ActivityDefinition / DeviceDefinition
              │
              │ 被引用
              ▼
       OrderTemplate ──1..*──> OrderTemplateLine
              │                        │
              │  Created/Updated/…      │  LineAdded/Updated/Removed
              └────────── 领域事件 §5.1 ────────> 缓存 / 检索索引（可选）
              
       OrderTemplate / Line（只读加载）
              │  getOrderTemplateWithLines
              ├────────────────────────────> MR / SR / DR 草稿（GENERAL/TCM，医生站）
              └────────────────────────────> 护士划价写行 → SR/DR + ChargeItem（PRICING）
```

---

## 9. 补充

### 9.1 与 FHIR / 标准的映射说明

本聚合为院内配置模型，**无**与 FHIR 资源的一一强制映射。若需与 **PlanDefinition** 或 **ActivityDefinition**（FHIR）做概念对齐，以实现侧扩展表或映射层为准，**不**改变本聚合边界。

### 9.2 版本化与变更策略（非本版本强制表结构）

当已发布模板需结构性调整且需保留历史审计时，推荐产品策略为：**停用当前 `OrderTemplateId` 并新建版本头**，或引入 `version` / `replaced_by_id` 扩展字段；具体表结构以实现与迁移脚本定稿为准。

### 9.3 与 `MedicationRequest.package_id` 的关系

库表 `med_medication_request` / `wkf_device_request` 保留 `package_id` 列以兼容 openhis 组合套餐语义；**本期快捷开嘱不写该字段**——产品不要求追溯组套来源。若后续需要列表归组/停嘱级联，再单独立项启用。

### 9.4 与 `ActivityDefinition` 文档中「推荐组套」行的关系

`DM-WKF-001` 中 ActivityDefinition 可选引用推荐组套；**不等于**医生站「医嘱模板」（`FROM_ADVICE`）。本 DM 为 **OrderTemplate 权威定义**；`template_source_enum` 仅区分维护组套 vs 开嘱另存。

### 9.5 划价套用追溯（本期不做）

~~PRICING 套用可在 `wkf_service_request` / `wkf_device_request` 增加可空 `source_template_id`~~ **本期不落地**；护士划价快捷开嘱**不写** `source_template_id`。原 **Gap-OTL-002** 关闭（产品不要求追溯）。

### 9.6 三类组套一览

| kind | source | 维护端 | 套用端 | 允许行类型 |
|---|---|---|---|---|
| GENERAL | MAINTAINED | 组套维护 | 医生站 | MEDICATION / SERVICE / DEVICE |
| GENERAL | FROM_ADVICE | 医生站另存 | 医生站 | 同上 |
| TCM | MAINTAINED / FROM_ADVICE | 同上 | 医生站 | 同上 + 中医字段 |
| PRICING | MAINTAINED only | 组套维护（划价 Tab） | 护士站 | SERVICE / DEVICE |

---

> **关联文档**：`DM-MED-001`、`DM-WKF-001`、`DM-ADM-008`、`DM-ADM-010`、`DM-ADM-011`、`DM-CUS-008`、`DM-MED-003`、`DM-WKF-004`、`DM-WKF-003`。字段级落库与索引见 **§2、§3**。

### 9.7 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.4.5 | 2026-06-18 | **`OrderTemplateBinding` 增补 `AGREEMENT`（协定处方）**；`object_id` 与 HOSPITAL 同为顶层机构；IF-07/11 列表响应四段（`agreementList`）；**BR-OTL-QRY-001** 顺序扩展 |
| v1.4.4 | 2026-06-12 | **IF-BAS-OTL-10**：医嘱模板另存支持 PERSONAL / DEPARTMENT（默认科室）；IF-07 列表按归属分段合并（科室 + 个人 + 全院） |
| v1.4.3 | 2026-06-11 | **IF-BAS-OTL-08**：预览仅结构、不 enrich/查价；废止 IF-OT-APP-03/04 后端代套用；套用期 prepicked 目录 IF + 写路径库位透传；**BR-OTL-028** 收窄至套用期 |
| v1.4.2 | 2026-06-11 | 套用不写 package_id / source_template_id；医嘱模板另存固定 DEPARTMENT；IF-07～12 应用层落地 |
| v1.4.1 | 2026-06-03 | §6.2.2 `pageOrderTemplates`：`binding/scene/status` 可选筛选；派生分页快路径 vs `Specification` 动态谓词；名称关键字明确为应用层当前页过滤 |
| v1.4.0 | 2026-06-03 | 增补 `template_source_enum`（MAINTAINED/FROM_ADVICE）；§1.5 维护组套 vs 医嘱模板；openhis 对照；Flyway V89 |
| v1.3.0 | 2026-06-03 | 合并划价组套至本聚合（`PRICING` kind）；删除独立 DM-013/014；§6.4.7 护士划价套用；Gap-OTL-001 领域层已落地（Flyway V88） |
| v1.2.0 | 2026-06-03 | `template_kind_enum`（GENERAL/TCM）、中医字段、§6.4 医生站套用 |
| v1.0.1/v1.1.1 | 2026-05-28 | §6.1/§6.2 与 DomainService/QueryService 白名单对齐 |
| v1.0/v1.1 | — | 既有版本 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`OrderTemplateDomainService`

| 方法 |
|------|
| `createOrderTemplate` |
| `updateOrderTemplateHeader` |
| `publishOrderTemplate` |
| `retireOrderTemplate` |
| `deleteOrderTemplate` |
| `addOrderTemplateLine` |
| `updateOrderTemplateLine` |
| `removeOrderTemplateLine` |

### QueryService：`OrderTemplateQueryService`

| 方法 |
|------|
| `getOrderTemplateWithLines` |
| `pageOrderTemplates` |
| `listOrderTemplatesByOwnerAndScene` |

