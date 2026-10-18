# DM-CUS-006 ServiceLocationTemplate 取药科室

> **限界上下文**：本地扩展 · 取药科室管理  
> **聚合类型**：实体聚合（单表配置型）  
> **版本**：v1.3.9 · **日期**：2026-10-11

---

## 1. 概述

### 1.1 业务定义

取药科室模板（ServiceLocationTemplate）定义了**科室上下文与默认发药药房/药库之间的规则映射关系**。它回答的核心业务问题是："**医生在某一科室上下文开立药品或耗材医嘱时，系统应将处方发往哪些候选药房或药库？**"——在**门诊医生站**，该科室上下文取**医生接诊所在科室（接诊科室）**：实现上与当前就诊接诊组织、登录/工作地点科室或写入 **`MedicationRequest`/`DeviceRequest` 之 `org_id`（开立科室）** 应对齐；若产品与「接诊科室」分列字段，须在 `Encounter` 或请求上显式建模后再消费本模板，**禁止**三口径长期混用不声明。

模板给出的是**候选发药点集合及优先级**；**可开数量展示**（目录侧）须在候选集合内各库房上做库存并扣除待发占用后再**汇总**（与 **`AS-OP-DOR-003`** **IF-DOR-11** / **`BR-ORD-011`** 一致），并解析目录行 **`defaultDispenseLocationId`**：**先**按匹配粒度（单品例外 → **耗材种类** / **药品用法** → 仅发放类别兜底）与组内 `priority` 得有序候选，**再**按 **`BR-SLT-005`** 在该序上有正库存下探（1 先试，无货再试下一档；均无正库存时回落序首）。**暂存/划价写路径**若前端已回传解析库位（`dispenseLocationId` / `performLocation`），应用层 **不再**二次 match 本模板（见 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`**）。**最终从哪个库房发、发几条**，由**签发编排**在候选集合内按 **FIFO** 结合库存数量计算，并落到 **`MedicationDispense`/`DeviceDispense`** 各行 `location_id`（见 **`DM-MED-002`** / **`DM-WKF-007`**）。

取药科室模板是**开立科室（Organization）与发药位置（Location）之间的桥梁**，属配置型主数据。与 **ActivityExecutionLocation** 并列：前者解决「药品/耗材发往哪个药房/器材房」，后者解决「诊疗项目在哪个科室执行」。本文档编码为 `DM-CUS-006`；对外交换时解析结果体现在 **`MedicationRequest` / `MedicationDispense`** 与 **`DeviceRequest` / `DeviceDispense`** 相关位置字段（门诊医生站药/材**对称**消费），规则本身不对外暴露。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 发药位置规则维护 | 维护开立科室与默认发药药房/药库的对应关系，按多维度条件精确配置 |
| 默认位置解析 | 医生站开立医嘱时，按规则粒度（从细到粗）与优先级（升序）自动解析默认发药位置 |
| 整科覆盖保存 | 按开立科室整科覆盖保存（先删后插），保证配置集合与界面一致 |
| 门诊/住院药房区分 | 通过发放类别、物品表名等维度实现门诊药房与住院药房的自动匹配 |
| 时段化规则管理 | 支持按当日生效时段限定规则生效范围（如白班发至门诊药房、夜班发至指定药房） |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 医生站开立药品医嘱 | 系统按规则粒度与优先级解析默认发药药房，自动填充取药科室 |
| 医生站开立耗材医嘱 | 同上，解析默认耗材发放位置 |
| 取药科室配置管理 | 管理员在 WH-DPL001 页面按开立科室维护取药规则 |
| 药房/药库调整联动 | 药房撤并或新设时，更新对应科室的取药科室配置 |
| 时段切换自动匹配 | 白班/夜班交接时段切换，自动匹配不同时段的发药位置 |

### 1.4 与关键上下游及交换边界的关系

规则锚定开立科室（Organization）与发药位置（Location），可选细化到药品/耗材定义（MedicationDefinition、DeviceDefinition）与发放类别等维度。**发药位置必须为 `adm_location`（`PHARMACY`/`WAREHOUSE`）**，**禁止**引用 Organization 药房/库房分类节点（见 **DM-ADM-004** §1.5）。医生站开立药品或耗材医嘱（**`MedicationRequest` / `DeviceRequest`** 等）时消费默认发药/发材位置解析结果；与 **ActivityExecutionLocation** 分列：本模板回答「药/材往哪发」，执行科室规则回答「诊疗在哪做」。签发拆库、FIFO 与 **`ChargeItem`** 计费锚点见 **`DM-MED-002`** / **`DM-WKF-007`** / **`DM-ADM-011`**。

**ZXPlat 读侧锚点**：`ServiceLocationTemplateQueryService#listApplicableDistributionLocationIds`（匹配粒度：单品 → **耗材种类** → **药品用法** → 仅发放类别，再 `priority` → `id`，**不用** `display_order`）供医生站目录/签发 FIFO；应用层 `ClinicalCatalogDispenseLocationResolver#pickFirstLocationWithPositiveStock` 在该序上叠 **`BR-SLT-005`** 正库存下探写 `defaultDispenseLocationId` / 绑定耗材 `performLocation`；`listCandidateDistributionLocationIds` / `listEffectiveTemplateRowsForDispensing`（`priority` → `id`）供降级全类别库位；`matchDefaultDistributionLocation` 为领域单点择优（**不查库存**）。

| 维度 | 上游 | 本聚合 | 消费方 |
|---|---|---|---|
| 主数据 | Organization、Location、可选物品定义 | 开立科室→发药位置规则 | MedicationRequest / DeviceRequest 及药、材发放编排 |
| 保存策略 | — | 整科覆盖（先删后插） | 以界面配置集为准 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cus_service_location_template`，主键 `id`；一行表示一条「开立科室 + 物品与发放维度 + 时段 + 默认发药位置 + 优先级」规则。

**高频查询模式（建议索引口径）**：按 `organization_id`、物品表与发放类别、`item_definition_id`、`type_code`、`method_code` 过滤；解析默认发药位置时叠加 `start_time`/`end_time` 与当前时刻；整科覆盖按 `organization_id` 批量删除后插入。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 取药科室模板标识 | `ServiceLocationTemplateId` | 系统内部唯一标识（雪花 ID） |

### 2.3 状态机

本聚合为纯配置型，以逻辑删除表达失效，不单独建业务生命周期状态机。

```
  [有效] ──逻辑删除──> [已删除]
```

| 状态 | 说明 | 允许的操作 |
|---|---|---|
| 有效 | 配置行有效，参与匹配解析 | 整科覆盖保存（替换）、匹配解析 |
| 已删除 | 逻辑删除，不参与任何业务查询 | — |

> 配置行的新增、修改、删除均通过「整科覆盖保存」一次性完成。

---

## 3. 实体

```text
ServiceLocationTemplate（聚合根，单表配置）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │  organizationId / itemTable / dispCategory / locId  │
  │  optional itemId / usage / timeRange / priority       │
  └──────────────────────────────────────────────────────┘
```

### 3.1 取药科室模板（ServiceLocationTemplate）

ServiceLocationTemplate 聚合根的属性定义，对应 `cus_service_location_template` 表（以落库实现为准）。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 规则标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 开立科室标识 | `organization_id` | `Long` | 是 | 左侧树选中的科室，配置的规则归属维度 |
| 物品表名 | `item_table` | `String` | 是 | 规则适用的物品物理表名，**落库与消费方须与 `TableNameConstants` 一致**（见 §4.1） |
| 发放类别编码 | `distribution_category_code` | `String` | 是 | 药品/耗材分类编码，须与目录侧 `categoryCode` 一致；**按 `item_table` 区分码表**（药品→`med_category_code`，耗材→`device_category_code`），同 code 值在不同物品表下语义独立 |
| 默认发药位置标识 | `def_location_id` | `Long` | 是 | 默认发药药房或药库位置 ID |
| 具体项定义标识 | `item_definition_id` | `Long` | 否 | 具体药品/耗材定义 ID；为空表示不限定单品（仍可按种类/用法/大类匹配） |
| 种类编码 | `type_code` | `String` | 否 | **耗材中间维**：与目录 `adm_device_definition.type_code`（术语 `device_type_code`）对齐；为空表示不限种类。**药品行通常为空**（药品中间维仍用 `method_code`）。用于同大类下拆房（如采血/注射/放射胶片），避免堆单品规则 |
| 用法编码 | `method_code` | `String` | 否 | 给药途径或用法编码；为空表示不限用法。**校验与展示分码表**：药品且发放类别=中草药（`med_category_code` 码值 `4`）时走 `tcm_method_code`；其余药品走 `method_code`；耗材通常为空 |
| 生效开始时间 | `start_time` | `LocalTime` | 否 | 当日内生效开始时间；为空表示全天有效 |
| 生效结束时间 | `end_time` | `LocalTime` | 否 | 当日内生效结束时间；为空表示全天有效 |
| 优先级 | `priority` | `Integer` | 是 | 1 为最优先，**数值越小越优先**（与 `DM-CUS-001` 执行科室一致） |
| 显示顺序 | `display_order` | `Integer` | 否 | 配置页列表显示排序 |
| 备注 | `remark` | `String` | 否 | 补充说明 |

> 审计字段、租户字段、逻辑删除字段继承自 `BaseAuditedEntity` / `IMultiTenant`，不在此处展开。

### 3.2 子实体

> 本聚合为单表配置模型，无聚合内子实体。

---

## 4. 值对象（含枚举）

### 4.1 物品表类型（ItemTableType）

| 枚举值 | 落库表名（权威） | 说明 |
|---|---|---|
| 药品表 | `med_medication_definition` | 规则适用于药品目录（`TableNameConstants.MED_MEDICATION_DEFINITION`）；医生站/发药消费方按此表名匹配 |
| 耗材表 | `adm_device_definition` | 规则适用于耗材目录（`TableNameConstants.ADM_DEVICE_DEFINITION`）；医生站/发药消费方按此表名匹配 |

> 物品表名是配置与匹配的**首要维度**；禁止使用简写别名（如 `medication_definition` / `device_definition`），否则与目录装配、`listCandidateDistributionLocationIds` 等消费路径无法对齐。

### 4.2 生效时段（EffectiveTimeRange）

| 属性 | 类型 | 说明 |
|---|---|---|
| 开始时间 | `LocalTime` | 当日内生效开始时间（如 08:00） |
| 结束时间 | `LocalTime` | 当日内生效结束时间（如 17:00） |

> **不变式**：开始时间须早于结束时间。两者均为空时表示全天有效。

### 4.3 匹配粒度（MatchGranularity）

医生站解析时，对模板行做 **calcScore**（模板非空约束与请求不一致则丢弃），再按分数从高到低、同分再按 `priority`→`id` 择优。

**择优序（分数语义）**：

| 优先级 | 命中形态 | 说明 |
|---|---|---|
| 最高 | 具体项命中 | 单品例外；模板上若同时写了种类/用法则须一并匹配 |
| 次高 | 种类命中（无具体项） | **耗材主路径**：同大类拆房（采血/注射/放射胶片等） |
| 再次 | 用法命中（无具体项、无种类） | **药品主路径**：按给药途径细化 |
| 最低 | 仅发放类别 | 大类兜底；不配种类/用法/单品时仍可命中 |

> 匹配粒度不落库。不配 `type_code` 的耗材请求仍可命中「仅大类」行，行为与历史一致。单品仅作例外，日常拆房应配种类而非堆单品。

### 4.4 术语表

| 名词 | 定义 |
|---|---|
| 开立科室 | 左侧树选中的科室，其下配置多条取药科室规则；对应 `organizationId` |
| 取药科室 | 默认发药药房或药库，即 `默认发药位置标识(LocationId)` 所指位置 |
| 项目类型 | 药品或耗材；决定物品表名 |
| 物品表名 | 规则适用的物品所在表，配置时**必选** |
| 发放类别 | 模板 `distribution_category_code`；**须与产品目录 `categoryCode` 一致**（药品→`med_category_code`，耗材→`device_category_code`；**非**给药途径维度） |
| 具体项 | 某一药品/耗材定义 ID；不填表示不限定单品 |
| 种类 | 耗材 `type_code`（`device_type_code`）；不填表示不限种类 |
| 用法 | 给药途径或用法编码；不填表示不限用法；**中草药类别**须选自 `tcm_method_code`，**西药/中成药**须选自 `method_code`（与 AS-OP-DOR-003 IF-DOR-18 口径一致） |
| 规则粒度 | 匹配顺序：具体项 → 种类 → 用法 → 仅发放类别（见 §4.3） |
| 整科覆盖 | 先删除该科室下全部配置行，再批量插入本次提交的完整列表 |

---

## 5. 领域事件

### 5.1 事件定义

| 编号 | 事件名称 | 触发时机 | 消费方 | 版本 |
|---|---|---|---|---|
| 5.1.1 | `ServiceLocationTemplateEditedEvent` | 整科覆盖保存成功后 | 医嘱开立编排、匹配缓存（可选） | v1 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| 有效（N 条） | 整科覆盖保存 | 有效（M 条） | `ServiceLocationTemplateEditedEvent` |
| 有效（N 条） | 整科清空保存 | 已删除（N 条） | `ServiceLocationTemplateEditedEvent` |

> 与 §2.3「有效 / 已删除」语义对齐；整科覆盖不产生多套事件名。

---

## 6. 领域服务

### 6.1 写侧领域服务（ServiceLocationTemplateDomainService）

> 整科覆盖须在**单事务**内完成：先软删后插（BR-SLT-006），中间态对外不可见。事务边界由 **`ServiceLocationTemplateAppService#saveDistributionConfig` 经框架 `AppServiceAspect`（切 `*AppService`）** 开启；领域 `ServiceLocationTemplateDomainService#saveDistributionConfig` **不**单独标注 `@Transactional`。

#### 6.1.1 整科覆盖保存（主动作）（`saveDistributionConfig`）

- **业务意义**：以一次提交原子替换某开立科室下全部发药位置规则，避免碎片增删导致匹配歧义。
- **边界说明**：编排内调用 6.1.2、6.1.3（或等价内联步骤）；不维护科室树与 Location 主数据；BR-SLT-001～006/009。
- **入参**：开立科室标识、配置行列表（物品表名、发放类别、具体项、用法、默认位置、优先级、生效时段等）。
- **出参**：`—`。
- **前置条件**：开立科室标识有效且存在（BR-SLT-001）；行内必填完整（BR-SLT-002/003）；配置键不重复（BR-SLT-004：匹配条件+默认发药位置，含生效时段）；优先级连续（BR-SLT-005）；时段合法（BR-SLT-009/010）。默认发药位置有效性（BR-SLT-008）与发放类别码表归属（按 `item_table`）由 **应用层 Gate** 在调用写侧前校验。
- **状态变化**：该科室旧规则集被替换为新集合（**逻辑软删**旧行后插入新行，同一事务）。
- **聚合内影响**：删除科室下全部旧行并插入新行（同一事务）。
- **事件输出**：`ServiceLocationTemplateEditedEvent`（整科保存成功后一次发布，以落库与交互契约为准）。
- **失败语义**：任一校验失败则整单回滚；不发布成功事件。

#### 6.1.2 按科室删除发放规则（前置子动作）（`deleteDistributionRulesByOrganization`）

- **业务意义**：整科覆盖前置步骤，清空该开立科室下既有规则，防止与本次提交并存。
- **边界说明**：通常仅由 `saveDistributionConfig` 在同一事务内调用；不建议单独对外暴露为无校验的公开 API（以本稿约定为准）。
- **入参**：开立科室标识。
- **出参**：`—`。
- **前置条件**：科室标识有效；调用方已持有覆盖事务。
- **状态变化**：该科室下配置行全部**逻辑软删**（`isDeleted=true`）。
- **聚合内影响**：按 `organizationId`（或等价键）批量删除。
- **事件输出**：通常不单独发布（由覆盖保存统一发布）；若实现拆分事件以本稿约定为准。
- **失败语义**：事务回滚则删除一并回滚。

#### 6.1.3 批量保存发放规则（后置子动作）（`batchInsertDistributionRules`）

- **业务意义**：在校验通过后批量持久化本次提交的配置行。
- **边界说明**：通常仅由 `saveDistributionConfig` 内联调用；BR-SLT-008 默认发药位置须引用有效药房/药库 Location（应用层保存前 Gate + 领域层非空校验）。
- **入参**：配置行列表。
- **出参**：`—`。
- **前置条件**：已通过同一事务内前置删除与行级校验。
- **状态变化**：新规则集生效。
- **聚合内影响**：批量插入。
- **事件输出**：同 6.1.1 统一事件。
- **失败语义**：插入失败导致整事务失败。

### 6.2 读侧查询服务（ServiceLocationTemplateQueryService）

#### 6.2.1 按开立科室列规则（`listDistributionRulesByOrganization`）

- **业务意义**：配置页右侧表展示当前科室下全部发药规则，带展示序（WH-DPL001）。
- **边界说明**：只读；复制粘贴未保存前不落库（BR-SLT-011/012）。
- **查询范围（读库，无写入）**：发药位置规则表；按开立科室过滤。
- **过滤口径**：BR-SLT-007 排除逻辑删除与时段外无效（若 persisted 含时段字段）。
- **组装规则**：按优先级、展示字段排序。
- **返回约束**：不含药品/耗材分页数据本体（他域或应用层加载）。

#### 6.2.2 全局规则列表（`listAllDistributionRules`）

- **业务意义**：管理端不按科室筛选查看全量或大范围规则，支撑导出与对账。
- **边界说明**：只读；数据量大时需分页或异步（应用层）。
- **查询范围（读库，无写入）**：规则全表或租户范围。
- **过滤口径**：可选租户、删除标记、时段。
- **组装规则**：列表或流式游标。
- **返回约束**：不内嵌导出文件生成。

#### 6.2.3 解析默认发药位置（`matchDefaultDistributionLocation`）

- **业务意义**：医生站开立医嘱时按物品维度+发放类别+用法/种类+优先级匹配默认发药位置，减少人工选点。
- **边界说明**：只读；BR-SLT-007 仅匹配未删除且时段有效行；不创建 Location；**本方法不查库存**。目录 `defaultDispenseLocationId`、护士绑定耗材 `performLocation` 等须「有库存下探」的消费方应先调 §6.2.6 再于应用层按序叠正库存（**BR-SLT-005**）。
- **查询范围（读库，无写入）**：规则表；可选多科室策略以落库与交互契约为准。
- **过滤口径**：开立科室 + 物品表名 + 发放类别 + 时段生效后，对具体项/**种类**/用法做 **calcScore**（≥0 才入选）；择优序为 **匹配粒度（分数降序，同档单品优先）→ `priority`（升序）→ `id`（升序）**，取最优一条的 `def_location_id`。
- **耗材软回落**：当 `item_table=adm_device_definition` 且主路径 calcScore 全负（无单品/种类/仅大类严格命中）时，`pickBest` 再收集同发放类别下**未绑单品**行（`item_definition_id` 为空，种类可放宽；模板若绑了用法须与请求同码），按 `priority`→`id` 取首条。药品无此软回落。
- **组装规则**：命中则返回默认 `LocationId`；无额外包装 DTO。
- **返回约束**：无命中时返回 `null` 并交由 UI 提示人工选择（产品约定）。

#### 6.2.4 列出候选发药库位（`listCandidateDistributionLocationIds`）

- **业务意义**：按接诊/开立科室 + 物品表 + 发放类别 + 时刻，返回该类别下**全部生效模板行**对应的候选 `def_location_id` 有序列表；供护士降级、运维宽口径等**不做**具体项/用法过滤的场景。
- **边界说明**：只读；消费方专用，**不**由 WH-DPL001 配置 App 暴露。医生站 IF-DOR-11 / 签发 FIFO 须用 §6.2.6 `listApplicable…`（带 calcScore），**禁止**误用本方法当作「指定物品优先」候选。
- **过滤口径**：开立科室 + 物品表 + 发放类别 + 未软删 + 时段生效；**不做** calcScore（不按具体项/用法收窄）。业务序为 **`priority` → `id`** 后对 `def_location_id` 去重。**禁止**用 `display_order`（仅配置页展示序）参与发药/FIFO 路由。

#### 6.2.5 列出生效模板行（`listEffectiveTemplateRowsForDispensing`）

- **业务意义**：与 6.2.4 同源过滤（**不做** calcScore），按 `priority` → `id` 返回完整模板读模型行，供目录装配降级。
- **边界说明**：只读；消费方专用。

#### 6.2.6 列出适用发药库位（`listApplicableDistributionLocationIds`）

- **业务意义**：医生站 IF-DOR-11 目录与库存求交、签发 FIFO 等：在生效模板行上再按具体项 + **种类** + 用法 **calcScore≥0** 过滤，返回适用候选 `def_location_id` 有序列表。
- **边界说明**：只读；消费方专用；写路径前端已回传库位时由应用层 `ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`（`clientResolvedDispenseLocationId`）跳过二次 match（见 §1.1）。耗材请求须携带目录 `typeCode`（可空）方可命中种类行。
- **过滤口径**：与 §6.2.3 同基础集 + calcScore；业务序为 **匹配粒度（分数降序）→ `priority` → `id`** 后对库位去重。**禁止**用 `display_order`。

### 6.3 领域边界说明

- 科室树、Location 下拉、发放类别字典、**用法字典（`method_code` / `tcm_method_code` 分码表）**、药品/耗材具体项选择为他域或应用层编排；**具体项分页不新增本模块查询 IF**，复用既有药品/耗材 catalog API。
- 写编排承担整科覆盖（`saveDistributionConfig`）；配置读编排承担 `listDistributionRulesByOrganization`；医生站读编排承担 `matchDefaultDistributionLocation`、适用候选（§6.2.6）与降级全类别候选（§6.2.4/6.2.5）。
- **发放类别空值回落**：领域读方法要求 `distributionCategoryCode` 非空；目录/FIFO 等应用层（如 `ClinicalCatalogDispenseLocationResolver`）在产品 `categoryCode` 为空时回落字面量 `"DEFAULT"` 再调本聚合，**不**在领域层隐式补默认。

### 6.4 配置端应用服务（`ServiceLocationTemplateAppService`，WH-DPL001）

> 包路径：`com.zxplat.application.basic.servicelocationtemplate`；HTTP 经 `AppServiceControllerRegistrar` 注册为 `POST /api/app/serviceLocationTemplate/{methodName}`。

| 方法 | 说明 | 领域调用 |
|---|---|---|
| `init` | 返回科室树（**仅门诊/住院科室**，上级医院节点保留成树）、药房/药库、物品表类型、药品/耗材分类（分码表）、**西药给药途径（`method_code`）与中药用法（`tcm_method_code`）**、耗材种类下拉 | 术语 + Organization/Location QueryService |
| `listByOrganization` | 按 `organizationId` 加载有效规则并 enrich 展示名；开立科室须为门诊/住院；**具体项仅批量取品名**（`ProductDefinitionQueryService#batchLoadByKeys`），不走选品全套术语预热 | `ServiceLocationTemplateQueryService#listDistributionRulesByOrganization` |
| `saveDistributionConfig` | 整科覆盖保存；`items` 空列表表示清空该科规则；写操作人经 `CurrentPractitionerResolver` | `ServiceLocationTemplateDomainService#saveDistributionConfig` |

**不对外暴露**：`deleteDistributionRulesByOrganization`、`batchInsertDistributionRules`（BR-SLT-012）；`matchDefaultDistributionLocation`、`listCandidateDistributionLocationIds` 等消费方读方法（见各医生站/发药 AS）。

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| `BR-SLT-001` | 保存/列表前须已选择有效开立科室（`organizationId` 非空且 Organization 存在），且须为**门诊或住院科室**（`type=DEPARTMENT` 且 `class∈{CLINIC,INPATIENT}`） | 左树裁剪与 `assertOrderingDepartment` 一致；护士站/医技/财务等不得作配置锚点；未选或不存在或不合法分类时拒绝 |
| `BR-SLT-002` | 每条配置行必填：物品表名 | 仅允许 `med_medication_definition` / `adm_device_definition` |
| `BR-SLT-003` | 每条配置行必填：发放类别编码、默认发药位置标识、优先级 | 核心匹配字段与结果字段的完整性保障 |
| `BR-SLT-004` | 同一开立科室下「物品表名 + 发放类别 + 具体项 + **种类** + 用法 + 生效时段（`start_time`/`end_time`，双空=全天）+ 默认发药位置」配置键不重复 | 同匹配条件可指向多个药房，由 `priority` 排候选序；仅条件与库位全同则拒绝（领域层 `assertNoDuplicateKey`） |
| `BR-SLT-014` | 非空 `type_code` 须落在术语 `device_type_code`；建议仅 `item_table=adm_device_definition` 使用 | 应用层 `saveDistributionConfig` Gate；药品行若误填种类可拒绝或忽略（实现以 Gate 为准） |
| `BR-SLT-005` | **同一匹配竞争组**（物品表 + 发放类别 + 具体项 + 种类 + 用法 + 生效时段，**不含**药房）内，非空 `priority` 须从 1 起连续；同组多药房时表示候选序（**1 先试，无正库存再试 2**）。**不同**品类/种类/单品条件各自独立从 1 起，整科允许出现多个 1 | 领域层 `assertPriorityContinuous`（按组校验）；前端按组重排；**匹配先比粒度（同档单品优先）再比组内 `priority`**；正库存下探由应用层 `ClinicalCatalogDispenseLocationResolver#pickFirstLocationWithPositiveStock`（目录默认点 / 绑定耗材发材房）落地，领域 `matchDefault…` 本身不查库存 |
| `BR-SLT-006` | 整科覆盖保存须在单事务内完成：先软删该科室全部配置行，再批量插入本次提交列表 | 事务由 `ServiceLocationTemplateAppService` + 框架 `AppServiceAspect` 提供；领域方法不单独 `@Transactional` |
| `BR-SLT-007` | 匹配时仅处理未逻辑删除且当前时间落在生效时段内的记录 | 过期或已删除的配置不参与解析 |
| `BR-SLT-008` | 默认发药位置标识须指向有效的药房、科室药房或药库位置（`form_enum ∈ {PHARMACY, DEPARTMENT_PHARMACY, WAREHOUSE}`） | 应用层保存前 Gate（`listAllWarehouseAndPharmacyLocations` 集合校验） |
| `BR-SLT-009` | 生效时段非空时，开始时间须早于结束时间 | 时间合法性校验 |
| `BR-SLT-010` | 生效时段为空（开始时间与结束时间均为空）表示全天有效 | 空值语义约定，简化配置 |
| `BR-SLT-011` | 复制粘贴操作仅追加表格行，须经"保存配置"才持久化 | 前端暂存不等于落库 |
| `BR-SLT-012` | 批量删除通过表格删行后"保存配置"提交剩余行，不单独调用删除接口 | 与整科覆盖模式一致 |
| `BR-SLT-013` | 非空 `method_code` 须落在当前行对应码表内 | 药品且发放类别=中草药（码值 `4`）→ `tcm_method_code`；其余药品→ `method_code`；耗材若有值仍走 `method_code`；应用层 `saveDistributionConfig` Gate + WH-DPL001 前端联动 |

### 7.1 子实体规则

> 本聚合为单表聚合，无聚合内子实体行。

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 组织架构 | Organization | `开立科室标识(OrganizationId)` | 配置归属的开立科室，左侧树数据来源 |
| 位置管理 | Location | `默认发药位置标识(LocationId)` | 规则指向的药房/药库位置 |
| 药品目录 | MedicationDefinition | `具体项定义标识` + `item_table=med_medication_definition` | 具体药品定义 ID（可选） |
| 耗材目录 | DeviceDefinition | `具体项定义标识` + `item_table=adm_device_definition` | 具体耗材定义 ID（可选） |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 药品申请 | MedicationRequest | 查询 | 医生站开立药品医嘱时查询 `matchDefaultDistributionLocation` 获取默认发药位置 |
| 物资申请 | SupplyRequest | 查询 | 开立耗材医嘱时查询默认发放位置 |
| QueryService / 缓存 | — | 事件订阅 | 监听 `ServiceLocationTemplateEditedEvent`，按开立科室维度失效匹配缓存 |

### 8.3 引用规则

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-ADM-004 Organization` | 关联 | 开立科室 |
| `DM-ADM-003 Location` | 关联 | 默认发药位置 |
| `DM-MED-001 MedicationDefinition` | 依赖 | 可选具体药品项 |
| `DM-ADM-008 DeviceDefinition`（`DM-ADM-008-DeviceDefinition-器材定义.md`） | 依赖 | 可选具体耗材项 |
| `DM-CUS-001 ActivityExecutionLocation` | 并列 | 执行科室与取药科室分列配置 |


### 8.4 事件流示意图

```
Organization              ServiceLocationTemplate              MedicationRequest
     │                            │                                  │
     │                            │<── matchDefaultDistributionLocation ──│
     │                            │── LocationId ───────────────────>│
     │                            │── EditedEvent ──> 缓存失效        │
```

> 状态迁移与事件发布矩阵见 **§5.2**。

---

## 9. 补充

### 9.1 QueryService与查询规则

| 编号 | 规则 ID | 规则描述 | 约束 / 落地要点 |
|---|---|---|---|
| R-QRY-1 | `BR-SLT-QRY-001` | 配置列表可直接查写表 | 本版本不强制独立QueryService表 |
| R-QRY-2 | `BR-SLT-QRY-002` | 保存后立即生效 | 读写同表 |
| R-QRY-3 | `BR-SLT-QRY-003` | 高频匹配可短期缓存 | 保存后失效 |

### 9.2 匹配规则粒度与优先级详解

见原 **匹配流程**（筛选基础集 → 四档粒度 → 优先级）；实现可先查基础集再内存排序（§4.3）。

### 9.3 整科覆盖保存流程

WH-DPL001：选科室 → `ServiceLocationTemplateAppService#listByOrganization` → 改表格 →「保存配置」→ `ServiceLocationTemplateAppService#saveDistributionConfig`（`AppServiceAspect` 事务内）→ 领域 `saveDistributionConfig`（先软删后插）→ 发布事件。具体药品/耗材项选择走既有 catalog API，不在本 App 新增分页 IF。

### 9.4 门诊/住院药房匹配示例

| 场景 | 配置方式 | 说明 |
|---|---|---|
| 门诊西药 → 门诊药房 | 发放类别=西药, 位置=门诊药房 | 门诊科室默认 |
| 住院西药 → 住院药房 | 发放类别=西药, 位置=住院药房 | 住院科室默认 |
| 夜间 → 指定药房 | 时段+发放类别 | 时段区分 |

### 9.5 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3.9 | 2026-10-11 | **代码先行回写**：§6.2.3 耗材软回落（未绑单品、种类可放宽）；§6.4 `listByOrganization` 轻量品名 enrich + 开立科室门诊/住院门禁；**BR-SLT-001** 与 `assertOrderingDepartment` 对齐；写操作人口径改为 `CurrentPractitionerResolver`；WH-DPL001 配置页客户端分页与轻控件（性能，不改契约） |
| v1.3.8 | 2026-10-11 | **代码先行回写**：明确药品主路径=用法、耗材主路径=种类、仅发放类别为双方兜底；**BR-SLT-005** 正库存下探落应用层 `pickFirstLocationWithPositiveStock`（目录 `defaultDispenseLocationId` / 绑定耗材 `performLocation` 与药对称）；领域 `matchDefault…` 不查库存 |
| v1.3.7 | 2026-08-25 | **BR-SLT-005 纠偏**：优先级按匹配竞争组（同条件多药房）1..N，非整科全局序号；不同品类/种类各自为 1 |
| v1.3.6 | 2026-08-25 | **耗材种类维**：模板新增可选 `type_code`（对齐目录 `device_type_code`）；匹配序改为具体项 → 种类 → 用法 → 仅大类；**BR-SLT-004** 配置键纳入种类；新增 **BR-SLT-014**；医生站开立经产品 `typeCode` 参与 `listApplicable…` |
| v1.3.5 | 2026-08-25 | **对账现行实现**：§6.2.4 `listCandidate…` 明确**不做** calcScore（仅 `priority`→`id`）；新增 §6.2.6 `listApplicable…`；**BR-SLT-005** 改为整科提交全局 1..N 连续；**BR-SLT-006** 事务边界改为 AppService 切面；§6.3 补充类别空值应用层回落 `"DEFAULT"` |
| v1.3.4 | 2026-07-28 | **BR-SLT-004**：配置键纳入默认发药位置；同匹配条件多药房允许，由 `priority` 排候选序 |
| v1.3.3 | 2026-06-17 | WH-DPL001：`init` 分码表下发 `method_code`/`tcm_method_code`；保存 Gate **BR-SLT-013**；列表 enrich 对齐 `TcmMethodCodeDisplaysupport |
| v1.3.2 | 2026-06-11 | §1.1 补充：目录读侧解析 `defaultDispenseLocationId`；写路径前端回传库位时应用层不二次 match 模板 |
| v1.3.1 | 2026-06-09 | §1.4 明确发药位置必须为 `adm_location`；禁止 Organization 药房/库房分类节点（交叉 **DM-ADM-004** §1.5） |
| v1.3.0 | 2026-06-05 | **M2**：§4.1 物品表落库值对齐 `TableNameConstants`；BR-SLT-001/004/008 与配置 App 实现对齐；新增 §6.2.4/6.2.5 消费读、§6.4 `ServiceLocationTemplateAppService` IF |
| v1.2.1 | 2026-05-28 | **M1**：§6.1/§6.2 与领域写读白名单对齐（3写+5读） |
| v1.2 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ServiceLocationTemplateDomainService`

| 方法 |
|------|
| `saveDistributionConfig` |
| `deleteDistributionRulesByOrganization` |
| `batchInsertDistributionRules` |

### QueryService：`ServiceLocationTemplateQueryService`

| 方法 |
|------|
| `listDistributionRulesByOrganization` |
| `listAllDistributionRules` |
| `listCandidateDistributionLocationIds` |
| `listAllByOrganizationId` |
| `listApplicableDistributionLocationIds` |
| `listApplicableDistributionLocationIdsFromLoaded` |
| `listEffectiveTemplateRowsForDispensing` |
| `matchDefaultDistributionLocation` |
| `matchDefaultDistributionLocationFromLoaded` |

