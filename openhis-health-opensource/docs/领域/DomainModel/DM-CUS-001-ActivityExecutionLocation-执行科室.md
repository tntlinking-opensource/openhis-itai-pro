# DM-CUS-001 ActivityExecutionLocation 执行科室

> **限界上下文**：基础数据 · 诊疗项目目录\
> **聚合类型**：实体聚合\
> **版本**：v1.5.1 · **日期**：2026-10-07

## 1. 概述

### 1.1 业务定义

执行科室（ActivityExecutionLocation）定义了**开立科室上下文下的诊疗执行位置规则**：医生在某一开立科室开立某类/某项诊疗时，在指定当日时段内应由哪一执行科室（及可选执行位置）负责执行。规则可锚定到**具体诊疗项目**、**业务类型**（术语 `activity_type_code`，与 AD `type_enum` 同轴，如检查下 DR/CT），也可仅锚定到**诊疗大类**。三层均可独立维护，不必填全；未配业务类型层时行为与原先「大类 + 单品」一致。系统在医生站解析可选执行科室时，按**开立科室（就诊科室）** + 大类 / 业务类型 / 诊疗定义与时间窗查找命中的规则行，匹配顺序为 **单品 → 业务类型 → 大类默认**，取优先级最高（**数值越小越优先，1 最优先**，与取药科室一致）的记录作为默认/候选执行目的地。

配置交互与 **DM-CUS-006 取药科室**同构：WH-EXE001 左树选**开立科室**（仅门诊、住院），规则行内选**执行科室**（仅护士站、医技）。未配置规则的诊疗，医生站开立时执行科室回落就诊开立科室，字段仍必填。

执行科室是 ActivityDefinition 的**下游配置**：ActivityDefinition 定义"项目是什么"，ActivityExecutionLocation 定义"在哪个开立科语境下、谁来做、什么时间做"。本文档编码为 `DM-CUS-001`，属本地扩展（CUS），不作为 FHIR 标准资源直接交换；对外交换时需拆分映射到 `Location`、`HealthcareService`、`Schedule` 等标准资源组合与扩展字段。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 规则行维护 | 对开立科室维度的执行科室/位置规则行进行新增、修改、批量新增与软删操作 |
| 时间窗解析 | 按开立科室 + 诊疗活动定义与当前时间查询命中的规则行（单条或批量） |
| 批量查询支持 | 供医嘱/费用编排批量消费时间窗查询结果 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 医生站可执行科室 | 门诊/住院 IF-DOR-19：按就诊开立科室过滤候选执行科室 |
| 执行路径解析 | 医嘱执行时按开立科室 + 活动类别 + 时间窗确定执行科室 |
| 配置管理 | WH-EXE001 按开立科室维护「大类 / 可选业务类型 / 可选项目 → 执行科室」规则；左树仅门诊/住院，执行科室仅护士站/医技 |

### 1.4 与关键上下游及交换边界的关系

执行科室规则的上游为诊疗项目目录（ActivityDefinition）、组织架构（Organization）、位置（Location）等主数据；典型消费方为服务申请（ServiceRequest）及医嘱执行路径解析。本聚合不维护主数据正文，仅保存规则行与锚点标识。

| 维度 | 上游 / 并列 | 本聚合 | 消费方 |
|---|---|---|---|
| 职责边界 | ActivityDefinition 定义「做什么」 | 定义「在哪个开立科语境下、谁执行、在什么时段生效」 | ServiceRequest 等落库解析得到的执行科室 |
| 引用形态 | 外键式标识引用 | 单表多行规则 | QueryService 按开立科室 + 时间窗命中 |

本聚合无独立 FHIR 资源；对外交换时按院内扩展将规则行拆分映射到以下目标组合（示意）：

| 本模型元素 | FHIR 目标 | 映射说明 |
|---|---|---|
| 开立科室标识（`organization_id`） | `Organization` | 开立/就诊科室 |
| 执行科室标识（`execution_org_id`） | `Location` / `Organization` | 执行科室对应 Location 或 Organization |
| 执行位置标识（`execution_location_id`） | `Location` | 细化位置粒度 |
| 活动类别编码（`activity_category_code`） | `HealthcareService.category` | 服务类别映射 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cus_activity_execution_location`，主键 `id`；一行表示一条「开立科室 + 活动类别 + （可选）业务类型 + （可选）诊疗活动定义 + 时段 + 执行科室/位置 + 优先级」规则。

**高频查询模式（建议索引口径）**：按 `organization_id`、`activity_definition_id`、`activity_category_code` 过滤；时间窗命中按 `start_time`/`end_time` 与查询时刻比较；批量解析按开立科室 + 诊疗定义集合 IN 查询。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 规则行标识 | `ActivityExecutionLocationId` | 系统内部唯一标识，雪花 ID |

### 2.3 状态机

本聚合为单表聚合，不适用独立生命周期状态机；规则行以软删（`isDeleted`）表达失效。

```
[有效] ──软删──> [已删除]
```

| 状态 | 英文 | 说明 | 允许的操作 |
|---|---|---|---|
| 有效 | `active` | 规则行正常生效，参与时间窗查询 | 更新、软删 |
| 已删除 | `deleted` | 软删后不参与业务查询 | — |

---

## 3. 实体

本聚合为单表聚合，无子实体。

### 3.1 执行科室规则行（ActivityExecutionLocation）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 开立科室标识 | `organization_id` | `Long` | 是 | 锚定开立/就诊科室（Organization）；与取药科室 `organization_id` 同口径 |
| 诊疗定义标识 | `activity_definition_id` | `Long` | 否 | 锚定 `wkf_activity_definition` 主键；**为空表示未绑单品**（大类默认或业务类型层） |
| 活动类别编码 | `activity_category_code` | `String` | 是 | 诊疗大类编码 |
| 业务类型码 | `activity_type_code` | `String` | 否 | 术语 `activity_type_code`（与 AD `type_enum` 同轴）；**可空**。与 `activity_definition_id` 均为空 = 大类默认；本列有值且未绑单品 = 业务类型层（如检查下 DR→影像科） |
| 执行科室标识 | `execution_org_id` | `Long` | 是 | 承担执行责任的科室/机构 |
| 执行位置标识 | `execution_location_id` | `Long` | 否 | 可空，细化到位置资源粒度 |
| 生效时段开始 | `start_time` | `LocalTime` | 否 | 当日内生效开始时间，空表示全天 |
| 生效时段结束 | `end_time` | `LocalTime` | 否 | 当日内生效结束时间，空表示全天 |
| 优先级 | `priority` | `Integer` | 是 | **数值越小越优先（1 最优先）**；多条命中时取优先级最小的记录 |
| 展示顺序 | `display_order` | `Integer` | 否 | 管理端列表展示顺序 |
| 备注 | `remark` | `String` | 否 | 补充说明 |

> 审计字段、租户字段、逻辑删除字段继承自 `BaseAuditedEntity`，遵循统一约定。

---

## 4. 值对象

本聚合无独立值对象；时段由 `LocalTime` 对（`start_time` / `end_time`）表达，优先级由 `Integer` 表达（数值越小越优先）。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 执行科室规则已变更 | `ActivityExecutionLocationEditedEvent` | 新增/更新/软删成功 | `ruleId`（或聚合行 `id`）、操作类型（可选：`CREATE`/`UPDATE`/`DELETE`）、租户标识（若有） | QueryService或缓存失效（若启用） |

### 5.2 事件触发规则

| 操作 | 发布事件 |
|---|---|
| 新增执行规则（`createExecutionRule`） | `ActivityExecutionLocationEditedEvent` |
| 更新执行规则（`updateExecutionRule`） | `ActivityExecutionLocationEditedEvent` |
| 软删执行规则（`deleteExecutionRule`） | `ActivityExecutionLocationEditedEvent` |

> 单条与批量新增若合并通知，载荷须能区分受影响规则行集合（以落库与交互契约为准）。

---

## 6. 领域服务

### 6.1 写侧领域服务（ActivityExecutionLocationDomainService）

#### 6.1.1 新增执行规则（`createExecutionRule`）

- **业务意义**：新增「活动/收费项在当日时段内由哪一执行科室（及可选执行位置）负责执行」的规则，供医嘱执行路径解析写入 ServiceRequest；**不区分单条与批量**——默认同一新增契约既可提交一条，也可一次提交多条（锚点列表长度为 1 即单条）。
- **边界说明**：仅落库本聚合规则行；不修改 Organization、Location、ActivityDefinition 等主数据；跨表事务由应用层与仓储编排；多条时仍逐条受不变式约束，失败策略（全回滚或部分成功）以本稿 **§6.1**/应用层为准。
- **入参**：活动类别编码；**一条或多条**规则明细（每条可含可选诊疗活动定义标识、可选业务类型码）；与锚点同行的或可复用的执行科室标识、执行位置标识（可选）、当日生效时段起止（`LocalTime`）、优先级等。**未绑单品且 `activity_type_code` 为空 = 大类默认；未绑单品且 `activity_type_code` 有值 = 业务类型层**。
- **出参**：新建规则主键（单条时）或主键列表 / 成功失败明细（多条时，以落库与交互契约为准）。
- **前置条件**：满足 BR-ACT-EXEC-002 必填；**每一条待插入行**插入前均通过 BR-ACT-EXEC-001 与已有未软删规则做时段闭区间重叠校验（与条数无关）。
- **状态变化**：`N/A`（无独立生命周期状态，有效行即参与匹配）。
- **聚合内影响**：向 `cus_activity_execution_location` **插入一行或多行**；不触及子实体（单表聚合）。
- **事件输出**：每条成功可发一次 `ActivityExecutionLocationEditedEvent` 或合并为一次（以落库与交互契约为准）。
- **失败语义**：任一行时段重叠、必填缺失、外键引用无效时，按约定拒绝该行或整批回滚，并保持库表与约定一致。

#### 6.1.2 更新执行规则（`updateExecutionRule`）

- **业务意义**：调整已有规则的时段、优先级、执行科室/位置或锚点定义，使配置与临床路径一致。
- **边界说明**：仅更新本表字段；不级联更新 ServiceRequest 历史已落库结果。
- **入参**：规则主键；待更新的字段子集（时段、优先级、执行科室/位置、锚点等）。
- **出参**：`—`（或返回更新后版本号/时间戳，以落库与交互契约为准）。
- **前置条件**：目标行存在且未软删；PR-ACT-EXEC-001：重叠校验时排除自身 `id` 后再判 BR-ACT-EXEC-001。
- **状态变化**：`N/A`。
- **聚合内影响**：更新单行；不批量改写他行。
- **事件输出**：`ActivityExecutionLocationEditedEvent`。
- **失败语义**：行不存在、已软删、重叠或非法字段时拒绝。

#### 6.1.3 软删执行规则（`deleteExecutionRule`）

- **业务意义**：撤销一条规则，使其不再参与默认命中，同时保留审计与历史引用可能。
- **边界说明**：逻辑删除仅作用于本聚合；不删除 ServiceRequest 已记录的历史命中。
- **入参**：规则主键。
- **出参**：`—`。
- **前置条件**：规则行存在；幂等：已软删可视为成功（以产品为准，文档不强制）。
- **状态变化**：`有效 -> 已删除（isDeleted 语义）`。
- **聚合内影响**：将对应行标记为删除或等价字段；默认查询排除。
- **事件输出**：`ActivityExecutionLocationEditedEvent`。
- **失败语义**：主键不存在且非幂等策略时拒绝。

### 6.2 读侧查询服务（ActivityExecutionLocationQueryService）

#### 6.2.1 按诊疗定义查规则列表（`listExecutionRulesByActivityDefinition`）

- **业务意义**：返回**绑定该诊疗活动定义**的有效规则行（不含大类默认规则）。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`cus_activity_execution_location` 中未软删行，按租户隔离。
- **过滤口径**：`activity_definition_id` 精确匹配；默认不含软删。
- **组装规则**：按优先级升序（1 最优先）、`display_order`、`id` 稳定排序。
- **返回约束**：返回领域 DTO/实体列表，不含机构树、诊疗名称等需 join 的展示字段时由应用层补全。

#### 6.2.1a 按诊疗定义解析可命中规则列表（`listExecutionRulesForActivityResolution`）

- **业务意义**：医生站开立诊疗时加载可选执行科室/地点规则：**单品 + 匹配业务类型层 + 同诊疗大类默认**。未维护类型层时与仅「单品 + 大类」一致。
- **边界说明**：只读；须由应用层先解析诊疗定义的 `categoryCode` 与 `activityTypeCode`（`type_enum`）再调用。
- **查询范围**：本表未软删行。
- **过滤口径**：`organization_id = 开立科室` **AND**（`activity_definition_id = 入参` **OR**（`activity_definition_id IS NULL` **AND** `activity_category_code = 入参大类` **AND**（`activity_type_code IS NULL` **OR** `activity_type_code = 入参业务类型`）））。其它业务类型行不进入候选。
- **组装规则**：合并去重后按 **单品 → 类型层 → 大类**，再按优先级升序、`display_order`、`id`。
- **返回约束**：规则列表；未绑单品行 `activityDefinitionId` 为 `null`；大类默认行 `activityTypeCode` 为 `null`。

#### 6.2.2 按活动定义与时间窗查询单条命中（`findByActivityAndTime`）

- **业务意义**：医嘱/执行路径解析时，按诊疗活动定义与当前时刻取一条应生效的执行科室规则；**匹配顺序：单品 → 匹配业务类型层 → 大类默认**。未配类型层时与原先大类回退一致。
- **边界说明**：只读查询，不写库，不做跨域编排；不写入 ServiceRequest；多条命中时的取首策略由仓储/领域约定（优先级小者优先）。
- **查询范围（读库，无写入）**：本表有效行 + 时间窗包含查询时刻。
- **过滤口径**：`activity_definition_id`（及活动类别等辅助条件，以落库与交互契约为准）匹配；租户与未删除。
- **组装规则**：命中多条时按优先级与 `id` 取第一条；无命中返回空。
- **返回约束**：单条规则或空；不返回解析后的 Organization 实体。

#### 6.2.3 批量按活动与时间窗解析（`findForBatchByActivityAndTime`）

- **业务意义**：批量医嘱/批量活动在同一时间窗内解析执行规则，减少往返。
- **边界说明**：只读查询，不写库，不做跨域编排；不保证与单条 API 在同一事务；顺序与幂等由调用方控制。
- **查询范围（读库，无写入）**：本表；入参为活动定义标识集合与查询时刻（或时刻列表）。
- **过滤口径**：与 6.2.2 相同维度批量 OR/IN；租户与未删除。
- **组装规则**：每个输入活动至多一条命中；无命中则该键不返回或显式空占位（以落库与交互契约为准）。
- **返回约束**：映射结构（活动 ID → 规则行）；体积受分页或批次限制由应用层控制。

#### 6.2.4 按开立科室查规则列表（`listExecutionRulesByOrganization`）

- **业务意义**：WH-EXE001 配置页左树选开立科室后，列出该科下全部有效规则行。
- **边界说明**：只读；可选按 `activity_category_code` 过滤；展示字段由应用层 enrich。
- **查询范围**：`cus_activity_execution_location` 未软删行。
- **组装规则**：同 6.2.1 优先级升序排序。

### 6.3 领域边界说明

- `ActivityExecutionLocation` 描述开立科室语境下执行科室/位置规则的维护与查询；机构树、诊疗项目定义主数据由 **Organization、ActivityDefinition** 等他域维护。
- 写编排由应用服务调用领域服务完成；读编排由 QueryService 完成；持久化由仓储承担。
- 页面初始化、机构树、诊疗活动挑选等应用层拼装能力不记入本节动作编号；**诊疗活动分页不新增本模块查询 IF**，复用 `ProductDefinitionCatalogPageLoader`（`wkf_activity_definition`）。

### 6.4 配置端应用服务（`ExecuteLocationAppService`，WH-EXE001）

> 包路径：`com.zxplat.domain.application.basic.executelocation`；HTTP 经 `AppServiceControllerRegistrar` 注册为 `POST /api/app/executeLocation/{methodName}`。

| 方法 | 说明 | 领域/复用调用 |
|---|---|---|
| `init` | 返回开立科室树（门诊/住院，上级保留）、执行科室下拉（护士站/医技）、诊疗大类、全量业务类型（仅 enrich） | `OrganizationQueryService` + `TerminologyOptionLoader` |
| `listActivityTypeOptionsByCategory` | 按大类列出业务类型下拉：该大类目录实际出现的 `type_enum`，排除与大类同名项（对齐申请单 chips） | `ActivityDefinitionQueryService#listDistinctTypeEnumsByCategoryCode` |
| `listByOrganizationId` | 按 `organizationId`（可选 `activityCategoryCode`）列出规则并 enrich 展示名 | `ActivityExecutionLocationQueryService#listExecutionRulesByOrganization` |
| `create` / `update` / `deleteById` | 单条 CRUD；**`organizationId` + `executionOrgId` + `activityCategoryCode` 必填，`activityDefinitionId` / `activityTypeCode` 均可选**；写操作人经 `CurrentPractitionerResolver` | `ActivityExecutionLocationDomainService` |
| `batchCreate` | 多诊疗定义一次创建（须至少选择一个具体项目，并指定共用执行科室）；写操作人经 `CurrentPractitionerResolver` | `ActivityExecutionLocationDomainService#createExecutionRule` |
| `pageActivityDefinitionsForExecution` | 分页挑选待绑定诊疗活动（须 `organizationId` + 大类） | `ProductDefinitionCatalogPageLoader`（表名 `wkf_activity_definition`） |

**不对外暴露**：`findByActivityAndTime`、`findForBatchByActivityAndTime` 等消费方命中读方法（见各医生站 AS / IF-DOR-19；均须传入开立科室）。

**医生站开立回落（BR-ACT-EXEC-005）**：门诊/住院 `*DoctorStationAdviceAppService#listActivityExecutionLocations` 在无命中规则时补「执行科室=就诊开立科室」行；医嘱行与申请单前端只消费该接口，不再读配置页 `executionOrgOptions` 做全院回落。

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| BR-ACT-EXEC-001 | 同一开立科室 + 活动类别 + 相同锚点粒度下，生效时段不得两两重叠 | 锚点粒度三类：① 单品（`activity_definition_id` 非空）；② 业务类型（未绑单品且 `activity_type_code` 非空）；③ 大类默认（二者皆空）。不同类型码互不重叠校验。闭区间相交判定；`createExecutionRule` / `updateExecutionRule` 前校验 |
| BR-ACT-EXEC-002 | 创建/更新须具备有效开立科室标识、执行科室标识、活动类别编码；删除为软删 | 应用层校验；**不要求** `activity_definition_id`、`activity_type_code` 必填 |
| BR-ACT-EXEC-003 | 软删记录不参与业务查询默认结果 | 查询带未删除条件 |
| BR-ACT-EXEC-004 | 配置页开立科室须为门诊或住院，执行科室须为护士站或医技 | `ExecuteLocationAppService` 初始化下拉与写路径校验；急诊（`EMERGENCY`）、体检（`PEIS`）各自系统执行，不进本配置页；不符合的历史规则软删，不迁移改挂 |
| BR-ACT-EXEC-005 | 医生站解析无命中规则时，执行科室取就诊开立科室 | 门诊/住院 `listActivityExecutionLocations`；字段仍必填，不展开全院科室 |

### 7.1 过程规则

- `PR-ACT-EXEC-001`：更新时排除自身 `id` 后再做重叠校验。
- `PR-ACT-EXEC-002`：新增（单条或多条）时按每条锚点逐条做重叠校验后写入。

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 组织架构 | Organization | `开立科室标识(organization_id)` | 开立/就诊科室 |
| 组织架构 | Organization | `执行科室标识(execution_org_id)` | 执行科室关联的组织科室 |
| 位置管理 | Location | `执行位置标识(execution_location_id)` | 细化到位置粒度（可空） |
| 诊疗项目目录 | ActivityDefinition | `诊疗定义标识(activity_definition_id)` | 规则关联的诊疗项目定义 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 服务申请 | ServiceRequest | 查询 | 医嘱执行时查询命中的执行科室规则行 |
| 费用计算 | ChargeItem | 间接引用 | ServiceRequest 关联的执行科室影响收费归属（本聚合不锚定收费项定义） |

### 8.3 引用规则

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-WKF-001 ActivityDefinition` | 关联 | 通过规则行中的 `activity_definition_id` |
| `DM-ADM-004 Organization`（科室） | 关联 | 通过 `organization_id`（开立）与 `execution_org_id`（执行） |
| `DM-ADM-003 Location` | 关联 | 通过 `execution_location_id`（可空） |
| `DM-WKF-004 ServiceRequest` | 依赖 | 开立与执行路径引用执行科室规则行 |
| `DM-CUS-006 ServiceLocationTemplate` | 并列 | 取药/发放位置规则，与执行科室分列配置；交互同构（左开立科室） |


### 8.4 事件流示意图

```
ActivityDefinition   ActivityExecutionLocation   ServiceRequest   Organization / Location
       │                        │                       │
       │                        │<── 时间窗命中查询 ────────│（执行路径解析）
       │                        │── ActivityExecutionLocationEditedEvent ──> QueryService / 缓存失效（若启用）
```

> 事件与操作对应关系见 **§5.2**。

---

## 9. 补充

### 9.1 与 ServiceRequest 的协作流程

```
医嘱执行路径解析流程：

1. 医嘱触发执行路径解析
2. 系统按诊疗活动定义 + 当前时间调用 findByActivityAndTime（可携带大类与业务类型以启用类型层 / 大类默认回退）
3. 多条规则命中时，按优先级（数值越小越优先）取第一条命中规则；**单品优先于业务类型层，业务类型层优先于同大类默认**
4. ServiceRequest 记录命中规则中的执行科室标识；无命中规则时记录就诊开立科室
```

### 9.2 与 Encounter 的间接关系

执行科室不直接关联 Encounter（就诊），而是通过 ServiceRequest 间接关联：

```
Encounter(就诊) → ServiceRequest(服务申请) → ActivityExecutionLocation(执行科室规则行)
```

---

> **关联文档：** 详细设计见 `04-架构设计/软件详细设计-本稿 **§6.1**/ActivityExecutionLocation执行科室-详细设计.md`；并列配置见 `DM-CUS-006-ServiceLocationTemplate-取药科室.md`。


### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.5.1 | 2026-10-07 | **配置下拉口径**：`listActivityTypeOptionsByCategory` 按大类目录实际 `type_enum` 出选项（检查→CT/DR），排除与大类同名类型 |
| v1.5.0 | 2026-10-07 | **代码先行回写**：可选业务类型层 `activity_type_code`（术语 `activity_type_code`）；匹配序单品 → 类型 → 大类；BR-ACT-EXEC-001 第三粒度；未配类型层与 v1.4 行为一致 |
| v1.4.0 | 2026-09-19 | **WH-EXE001 收口**：左树仅门诊/住院，执行科室仅护士站/医技；无规则时医生站回落开立科室（BR-ACT-EXEC-004/005）；误配历史规则软删；急诊/体检单独分类（`EMERGENCY`/`PEIS`）不进配置页 |
| v1.3.0 | 2026-07-28 | **开立科室维度**：新增 `organization_id`；WH-EXE001 左树改开立科室、行内选执行科室；重叠锚点改为开立科室；IF-DOR-19 按就诊开立科室过滤 |
| v1.2.0 | 2026-06-17 | **M3**：`activity_definition_id` 改为可选；支持仅配诊疗大类的默认执行科室规则；命中解析增加大类默认回退 |
| v1.1.0 | 2026-06-05 | **M2**：优先级与取药科室对齐（数值越小越优先）；移除收费项锚点；新增 §6.2.4/§6.4 配置 App；诊疗挑选复用 `ProductDefinitionCatalogPageLoader` |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与领域服务白名单对齐（3写+3读） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ActivityExecutionLocationDomainService`

| 方法 |
|------|
| `createExecutionRule` |
| `updateExecutionRule` |
| `deleteExecutionRule` |

### QueryService：`ActivityExecutionLocationQueryService`

| 方法 |
|------|
| `listExecutionRulesForActivityResolution` |
| `listExecutionRulesByOrganization` |
| `findByActivityAndTime` |
| `findForBatchByActivityAndTime` |

