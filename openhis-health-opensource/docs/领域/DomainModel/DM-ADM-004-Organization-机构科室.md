# DM-ADM-004 Organization 机构科室

> **限界上下文**：管理域 · 组织机构管理\
> **聚合类型**：实体聚合\
> **版本**：v1.3.0 · **日期**：2026-09-19

## 1. 概述

### 1.1 业务定义

机构科室（Organization）是 HIS 中**组织管理的核心主数据**，以树形层级结构表达「医院 → 科室」的行政组织关系（业务细分类别见 `OrganizationClassification`）。它为就诊、医嘱、费用、人员等跨域业务提供稳定的 `机构科室标识(OrganizationId)`，是门诊挂号选科、住院登记选科、费用归属、人员归属等场景的基础支撑。

### 1.2 核心职责

| 职责       | 说明                                                  |
| -------- | --------------------------------------------------- |
| 组织层级管理   | 通过层级编码（`busNo`，点号分隔）构建并维护医院 → 科室的行政组织树   |
| 机构主数据维护  | 管理名称、类型、分类、输入码、医保信息等核心属性                            |
| 启停与生命周期  | 通过 `PublicationStatus`（`DRAFT → ACTIVE → RETIRED → DISCARDED`）控制机构可用性 |
| 身份域映射同步  | 与 identity `OrganizationUnit` **轻关联**（`code` 同步，**主键独立**），见 §9.3 |
| 科室列表/树投影 | 为门诊挂号、住院登记等场景提供可选用的科室扁平列表与树形投影                      |

### 1.3 适用范围

| 类型      | 说明                                      |
| ------- | --------------------------------------- |
| 医院/院区设置 | 创建并维护医院、院区等顶层组织节点                       |
| 科室管理    | 创建并维护门诊科室、住院科室、医技科室、药房、行政科室等业务科室        |
| 诊疗组管理   | 在科室下创建诊疗组，用于细粒度人员与号源管理                  |
| 门诊挂号选科  | 为挂号窗口提供可选用的门诊科室列表或树（支撑 IF-REG-06）       |
| 住院登记选科  | 为住院登记窗口提供可选用的住院科室列表（支撑 IF-ADM-01）       |
| 费用归属    | 为收费明细（ChargeItem）、就诊账户（Account）提供费用归属科室 |
| 医保对接    | 维护医保机构编码、医保类型、机构等级等信息，用于医保结算            |

### 1.4 与 Location 的关系

| 维度   | Organization（机构科室）                              | Location（位置）            |
| ---- | ----------------------------------------------- | ----------------------- |
| 管理对象 | 行政组织关系（谁向谁汇报）                                   | 物理空间（建筑 / 楼层 / 诊室 / 床位） |
| 层级语义 | 医院 → 院区 → 科室 → 诊疗组                              | 院区 → 病区 → 病房 → 床位       |
| 关联方式 | `Location.managingOrganization` 指向 Organization | 一个科室可对应多个物理位置           |
| 典型用途 | 挂号选科、费用归属、人员归属、开单科室名称展示                                  | 入院分床、转床、诊室分配；**药房/药库点位**；**病区/病房/床位**            |

> **边界规则**：Organization 只管理"谁"（行政组织），Location 管理"哪里"（物理空间）。二者通过 ID 引用关联，不互相持有对象引用。

### 1.5 `class_enum` 与 Location 分工（P0，2026-06-09 对齐实现）

| 业务场景 | 主数据口径 | 过滤/收窄方式 | **禁止** |
|---|---|---|---|
| 门诊挂号选科 | `Organization` | `class_enum = CLINIC`（`listOrganizationsForOutpatientRegistration`） | 用 Location 代替科室 |
| 住院登记/病区挂靠科室 | `Organization` | `class_enum = INPATIENT`（`listOrganizationsForInpatientAdmission`） | 用全量机构树或 `wardBedInit` 全量 org |
| 入院病区 | `Location` | `form_enum = WARD`，按科室 `organization_id` 关联（`listWardsByOrganization`） | 用 `Organization.class_enum` 表示病区 |
| 门诊/住院发药页「药房」下拉 | `Location` | `form_enum = PHARMACY`，经岗位 `adm_pr_assign.location_id` → `CurrentManagedLocationIdsResolver` | **`getOrganizationTreeForPharmacy`（已废止）** |
| 药库/药房/科室药房库存、入库、出库、盘点 | `Location` | `form_enum = WAREHOUSE` / `PHARMACY` / `DEPARTMENT_PHARMACY`；药库 `listAllActiveWarehouses`、药房 `listAllActivePharmacies`（仅药房）、科室 `listAllActiveDepartmentPharmacies`；出库目的等并集见 `listAllActivePharmacyPartyLocations` | **`getOrganizationTreeForStorage`（已废止）**；**禁止**用 `Organization.class_enum = PHARMACY/STORAGE` 选库位 |
| 医院根节点维护 | `Organization` | `type_enum = HOSPITAL`；**无** `parent_id`；**无** `class_enum` | 医院挂上级、填类别/默认医师/科别 |
| 开单科室名称展示（发药单等） | `Organization` | `mapNamesByIds(orderingDepartmentOrganizationIds)` | 与药房下拉混用同一 Query |

> **说明**：`OrganizationClassification.PHARMACY` / `STORAGE` 枚举值**保留于字典**，但**不得**再作为发药/库存业务的组织筛选口径；药库药房与 Organization **无业务关联关系**，仅 Location 承载。

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，通过自引用实现层级关系。聚合边界以主表主键 `id`（机构标识）划分。

**表结构**：

- 主表：`adm_organization`（主键：`id`）
- 关系：自引用（父机构标识 `parent_id`），形成树形结构
- 索引：`bus_no`（层级编码）、`type_enum`（机构类型）、`status_enum`（状态）、`active_flag`（是否启用）

**高频查询模式**：

- 按机构标识查询：单表主键查询
- 按层级编码查询：`bus_no` 索引扫描
- 按机构类型筛选：`type_enum` 索引扫描
- 按科室分类筛选：`class_enum` 索引扫描

### 2.2 聚合根标识

| 属性   | 类型               | 说明                                                       |
| ---- | ---------------- | -------------------------------------------------------- |
| 机构标识 | `OrganizationId` | 全局唯一标识，系统生成（雪花 ID）；**仅指** `adm_organization.id`，与 `OrganizationUnit.id` **无关** |

### 2.3 状态机

```
  [DRAFT] ──发布──> [ACTIVE] ──停用──> [RETIRED] ──废弃──> [DISCARDED]
    │                │                 │                │
    │                │                 └──重新启用──> [ACTIVE]
    │                │
    └──废弃──────────┘
    │
    └──(应用默认)新建──> [ACTIVE]   ← 科室管理创建路径，跳过 DRAFT
```

> **说明**：`publishOrganization` 仍保留，供历史草稿数据或特殊流程发布。管理端新建科室默认 **ACTIVE**（`initAsActive`）。`ACTIVE` 可直接废弃（`abolishOrganization`），也可先停用再废弃。

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | DRAFT | 新创建尚未生效 | 不可被其他域引用，允许发布、废弃、修改 |
| 2 | ACTIVE | 生效中 | 可被门诊挂号、住院登记等场景选用，允许停用、修改、调整上级（不可直接废弃） |
| 3 | RETIRED | 临时停止使用 | 已有引用不受影响，新业务不可选用，允许重新启用、废弃 |
| 4 | DISCARDED | 逻辑删除 | 不可恢复；存量码 `DELETED` 只读兼容 |

> **状态枚举说明**：持久化字段 `status_enum` 使用共享枚举 **`PublicationStatus`**（**String 存储，存枚举 code**）。`active_flag`（Boolean）为派生字段，`status_enum == ACTIVE` 时为 `true`。`DISCARDED` 对应软删除（`isDeleted = true`）。存量库中可能仍有 `DELETED` 字符串，只读兼容。

> **术语（停用 / 废弃）**：「停用」指 `RETIRED`；「废弃」指 `DISCARDED`（兼容存量 `DELETED`）。

## 3. 实体

### 3.1 机构科室（Organization）

Organization 聚合根的属性定义，对应 `adm_organization` 表。

| 属性       | 字段名                 | 类型                                | 必填 | 说明                                            |
| -------- | ------------------- | --------------------------------- | -- | --------------------------------------------- |
| 机构名称     | `name`              | `String`                          | 是  | 机构 / 科室正式名称                                   |
| 层级编码     | `bus_no`            | `String`                          | 是  | 点号分隔的层级路径（如 `1.2.3`），子节点 = 父节点编码 + "." + 本级编码 |
| 机构类型     | `type_enum`         | `OrganizationType`（值对象）           | 是  | 见 §4.1                                        |
| 机构分类     | `class_enum`        | `OrganizationClassification`（值对象） | 否  | 见 §4.2；仅机构类型为"科室"时有意义                         |
| 机构状态     | `status_enum`       | `PublicationStatus`（值对象）           | 是  | 生命周期状态，见 §2.3 |
| 是否启用     | `active_flag`       | `Boolean`                              | 是  | `status_enum == ACTIVE` 时为 `true`，其余为 `false` |
| 输入码      | `py_str` / `wb_str` | `InputCode`（值对象）                  | 否  | 见 §4.3                                        |
| 医保信息     | `yb_no` 等           | `MedicalInsuranceInfo`（值对象）       | 否  | 见 §4.4；对接医保的机构节点须填写                           |
| 科别       | `caty`              | `String`                          | 否  | 科别编码（如内科、外科）                                  |
| 显示顺序     | `display_order`     | `Integer`                         | 否  | 同级内排序序号                                       |
| 默认挂号医生标识 | `def_doctor_id`     | `PractitionerId`                  | 否  | 门诊挂号场景下的默认受理医生                                |

> 通用审计字段（`createdAt / updatedAt / createdBy / updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted / deletedAt / deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。

本聚合为**单表主数据聚合**，聚合根即为唯一持久化实体。组织树投影（`OrganizationTree`）为只读投影模型（非持久化），不在聚合内定义独立实体。

## 4. 值对象

### 4.1 机构类型（OrganizationType）

表达组织树节点在行政层级中的角色。**仅两级**：

| 编码值 | 英文         | 说明                       |
| --- | ------------ | ------------------------ |
| 1   | `HOSPITAL`   | 医院（根或上级节点）              |
| 2   | `DEPARTMENT` | 科室（门诊/住院/医技等业务科室）         |

> **存储口径**：`type_enum`，**String 存枚举 code**。  
> **层级约束（应用层）**：医院上级只能是医院或根；科室上级只能是医院或科室。门诊/住院/药房等 **不** 占用 `type_enum`，见 §4.2 `class_enum`。

### 4.2 机构分类（OrganizationClassification）

用于细化「科室」类节点（`type_enum = DEPARTMENT`）的业务属性；**医院节点（`HOSPITAL`）与药库/药房业务均不使用本字段**（药房/药库见 `DM-ADM-003` Location；见 §1.5）。

| 编码值 | 英文          | 说明            |
| --- | ----------- | ------------- |
| 1   | `CLINIC`    | 门诊            |
| 2   | `INPATIENT` | 住院            |
| 3   | `PHARMACY`  | 药房（**历史字典项**；库存/发药业务**不**按此筛 Organization，见 §1.5） |
| 4   | `STORAGE`   | 库房（**历史字典项**；库存业务**不**按此筛 Organization，见 §1.5） |
| 5   | `FIN`       | 财务            |
| 6   | `NS`        | 护士站           |
| 7   | `MANAGER`   | 管理部门          |
| 8   | `SUPPORT`   | 后勤部门          |
| 9   | `OTHER`     | 其他            |
| 10  | `TECH`      | 医技（检验、检查、病理等） |
| 11  | `EMERGENCY` | 急诊（急诊科；在急诊系统执行，**不**进护士站/医技工作站，也**不**作 WH-EXE001 开立锚点） |
| 12  | `PEIS`      | 体检（体检科；在体检系统执行，**不**进护士站/医技工作站，也**不**作 WH-EXE001 开立锚点） |

> **存储口径**：对应主表字段 `class_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.3 输入码（InputCode）

封装快速检索所需的多种输入码，避免属性散落。

| 属性  | 类型       | 说明               |
| --- | -------- | ---------------- |
| 拼音码 | `String` | 名称拼音首字母缩写，用于快速检索 |
| 五笔码 | `String` | 名称五笔编码，用于快速检索    |

> **存储口径**：对应主表字段 `py_str`（拼音码）和 `wb_str`（五笔码），使用字符串存储。

### 4.4 医保信息（MedicalInsuranceInfo）

封装与医保对接相关的标识与分类信息，对应 `adm_organization` 表中以下字段。

| 属性       | 字段名             | 类型       | 说明                |
| -------- | --------------- | -------- | ----------------- |
| 医保编码     | `yb_no`         | `String` | 医保系统中的机构编码        |
| 医保名称     | `yb_name`       | `String` | 医保系统中的机构名称        |
| 医疗服务机构标识 | `medins_id`     | `String` | 医保定点机构编码          |
| 行政区划编码   | `medins_admdvs` | `String` | 所属行政区划编码          |
| 机构类型   | `medins_type`   | `String` | **医院节点**：产品约定 `TCM`（中医院）/ `WESTERN`（西医院），驱动病案首页「中医信息」显隐；亦兼容手填「中医院」「中医」。**医保平台**事中审核等场景若另有 `MEDINS_TYPE` 字典码，勿与本字段医院节点码混用同一取值空间而未约定映射。 |
| 医保机构等级   | `medins_lv`     | `String` | 三甲 / 三乙 / 二甲等等级编码 |

> **存储口径**：对应主表字段 `yb_no`、`yb_name`、`medins_id`、`medins_admdvs`、`medins_type`、`medins_lv`，使用字符串存储。
> **医院节点（`type_enum = HOSPITAL`）**：医保信息**均可为空**（应用层/前端不强制）；**无** `class_enum`、**无** `def_doctor_id`、**无** `caty`、**无** `parent_id`（须为根节点，见应用层 `validateOrganizationHierarchy`）。
> **科室节点**：若填写 `medins_id`（机构标识），则 `medins_lv`（机构等级）必填（`assertCreateAllowed`）。
> **BR-ORG-009（修订）**：仅当业务明确「对接医保且已维护机构标识」时，要求 `medins_id` + `medins_lv` 成对；**不再**要求医院根节点 `yb_no` 必填。

## 5. 领域事件

### 5.1 事件定义

| 事件名称    | 英文名称                                | 触发时机              | 携带数据                           | 消费方    |
| ------- | ----------------------------------- | ----------------- | ------------------------------ | ------ |
| 机构已创建   | `OrganizationCreatedEvent`          | 新机构 / 科室创建成功      | 机构标识, 机构名称, 机构类型, 层级编码, 上级机构标识 | QueryService/缓存 |
| 机构已发布   | `OrganizationPublishedEvent`        | 机构从草稿发布为已启用       | 机构标识, 机构名称, 机构类型, 机构分类         | QueryService/缓存 |
| 机构已修改   | `OrganizationEditedEvent`           | 机构属性修改成功          | 机构标识, 变更字段列表                   | QueryService/缓存 |
| 机构已停用   | `OrganizationRetiredEvent`          | 机构被停用             | 机构标识, 停用原因, 操作人标识              | QueryService/缓存 |
| 机构已重新启用 | `OrganizationReactivatedEvent`      | 机构从停用恢复为已启用       | 机构标识, 操作人标识                    | QueryService/缓存 |
| 机构已废弃   | `OrganizationAbolishedEvent`        | 机构被逻辑删除           | 机构标识, 废弃原因, 操作人标识              | QueryService/缓存 |
| 机构层级已调整 | `OrganizationHierarchyChangedEvent` | 机构上级归属变更，触发层级编码重算 | 机构标识, 原上级标识, 新上级标识, 新层级编码      | QueryService/缓存 |

### 5.2 事件触发规则

| 当前状态     | 操作   | 目标状态 | 发布事件                                |
| -------- | ---- | ---- | ----------------------------------- |
| —        | 创建机构 | 草稿   | `OrganizationCreatedEvent`          |
| 草稿       | 发布   | 已启用  | `OrganizationPublishedEvent`        |
| 草稿 / 已启用 | 修改属性 | 不变   | `OrganizationEditedEvent`           |
| 已启用      | 调整上级 | 已启用  | `OrganizationHierarchyChangedEvent` |
| 已启用      | 停用   | 已停用  | `OrganizationRetiredEvent`          |
| 已停用      | 重新启用 | 已启用  | `OrganizationReactivatedEvent`      |
| 草稿 / 已停用 | 废弃   | 已废弃  | `OrganizationAbolishedEvent`        |

## 6. 领域服务

### 6.1 机构科室领域服务（OrganizationDomainService）

#### 6.1.1 创建机构（`createOrganization`）

- **业务意义**：创建组织树节点并建立身份域映射。
- **边界说明**：只维护 Organization 聚合与身份域映射事实，不校验下游业务引用。
- **前置条件**：同父节点名称唯一（BR-ORG-003）；层级编码生成规则合法（BR-ORG-002）。
- **状态变化**：初始化为 **ACTIVE**（`initAsActive`，`PublicationStatus.ACTIVE`）。
- **聚合内影响**：写入机构主档；**应用层**按 `code` 同步创建/更新 `OrganizationUnit`（见 §9.3）。
- **事件输出**：`OrganizationCreatedEvent`。

#### 6.1.2 发布机构（`publishOrganization`）

- **业务意义**：将草稿机构发布为可引用状态。
- **边界说明**：不触发QueryService全量重建，由事件驱动增量刷新。
- **前置条件**：机构须为草稿，且名称/类型等必填完整。
- **状态变化**：草稿 -> 已启用。
- **聚合内影响**：同步启用 `OrganizationUnit`。
- **事件输出**：`OrganizationPublishedEvent`。

#### 6.1.3 修改机构（`modifyOrganization`）

- **业务意义**：维护机构展示与管理属性。
- **边界说明**：不自动改写历史业务单据中的科室名称；历史展示依赖各聚合写入时落库的名称快照（见 BR-ORG-013）。
- **前置条件**：机构须为草稿或已启用；名称变更需满足同级唯一（BR-ORG-003）。
- **聚合内影响**：同步更新 `OrganizationUnit` 展示字段。
- **事件输出**：`OrganizationEditedEvent`。

#### 6.1.4 调整上级（`relocateOrganization`）

- **业务意义**：调整组织树层级归属。
- **边界说明**：递归重算 `busNo` 仅影响本聚合与身份域树结构，不迁移下游业务中的科室引用。
- **前置条件**：机构须为已启用；新上级不得形成环（BR-ORG-005）。
- **聚合内影响**：递归重算本节点及后代 `busNo`，同步身份域层级关系。
- **事件输出**：`OrganizationHierarchyChangedEvent`。

#### 6.1.5 停用机构（`retireOrganization`）

- **业务意义**：将机构退出可用集合。
- **边界说明**：不修改下游已存在引用，仅通过状态使新选用不可用（BR-ORG-013）。
- **前置条件**：机构须为已启用；存在启用子节点时需级联处理或拒绝（BR-ORG-006）。
- **状态变化**：已启用 -> 已停用。
- **聚合内影响**：同步停用 `OrganizationUnit`。
- **事件输出**：`OrganizationRetiredEvent`。

#### 6.1.6 重新启用机构（`reactivateOrganization`）

- **业务意义**：恢复停用机构为可用状态。
- **边界说明**：不自动恢复已废弃节点；废弃须重新建档或走数据修复流程。
- **前置条件**：机构须为已停用，且父节点已启用（BR-ORG-007）。
- **状态变化**：已停用 -> 已启用。
- **聚合内影响**：同步启用 `OrganizationUnit`。
- **事件输出**：`OrganizationReactivatedEvent`。

#### 6.1.7 废弃机构（`abolishOrganization`）

- **业务意义**：终止机构并保留审计追踪。
- **边界说明**：废弃为逻辑删除，主键仍存在；历史单据通过快照或只读加载回显（BR-ORG-013、BR-ORG-014）。
- **前置条件**：不得已废弃；无活跃外部引用（BR-ORG-008）。**启用态（ACTIVE）可直接废弃**（须通过引用扫描）。
- **状态变化**：→ `DISCARDED`（`PublicationStatus.DISCARDED`）。
- **聚合内影响**：执行逻辑删除并同步废弃 `OrganizationUnit`。
- **事件输出**：`OrganizationAbolishedEvent`。
- **失败语义**：
  - 当前为 `ACTIVE`：业务错误「机构处于已启用状态，须先停用后再废弃」，不发布事件。
  - `checkOrganizationAbolishable` 未通过：业务错误「机构仍存在活跃引用，无法废弃」，载荷/附文建议包含引用类型与最小定位信息（如 `encounterId` 列表摘要、引用条数），不发布事件。

### 6.2 读侧查询服务（OrganizationQueryService）

> 查询类动作无写库；用于下拉可选列表、树回显、历史详情回显与废弃前校验。

#### 6.2.1 门诊挂号科室列表（`listOrganizationsForOutpatientRegistration`）

- **业务意义**：提供挂号场景**仅可选用**的科室扁平列表。
- **边界说明**：只读查询，不写库，不做挂号编排。
- **查询范围（读库，无写入）**：`adm_organization` 及必要码表。
- **过滤口径**：`ACTIVE` 且未逻辑删除；`type_enum` 为科室或诊疗组（按产品约定是否暴露诊疗组）；`class_enum` 含门诊（`clinic`）或与挂号场景匹配的配置集合。
- **组装规则**：按 `display_order`、名称排序；可附带输入码。
- **返回约束**：仅返回允许新建挂号引用的节点；空集时 UI 提示「无可用科室」。

#### 6.2.2 门诊挂号科室树（`loadOrganizationTreeForOutpatientRegistration`）

- **业务意义**：提供挂号窗口树形科室回显（父展开子）。
- **边界说明**：只读查询，树结构来自 `bus_no` 前缀关系或物化QueryService。
- **查询范围（读库，无写入）**：与 §6.2.1 相同过滤条件下的节点集合。
- **过滤口径**：剔除 `DRAFT / RETIRED / DISCARDED` 节点；仅保留对挂号可见分支。
- **组装规则**：按 `bus_no` 构建树，同级按 `display_order`。
- **返回约束**：根节点一般为医院或院区，叶子为可选科室；无数据时返回空树。

#### 6.2.3 住院登记科室列表（`listOrganizationsForInpatientAdmission`）

- **业务意义**：提供住院登记**仅可选用**的住院科室列表。
- **边界说明**：只读查询，不做入科分配编排。
- **查询范围（读库，无写入）**：`adm_organization`。
- **过滤口径**：`ACTIVE` 且未逻辑删除；`type_enum = DEPARTMENT` 且 `class_enum = INPATIENT`（住院分类）。
- **组装规则**：列表排序同 §6.2.1。
- **返回约束**：仅返回住院业务可选科室；与门诊列表口径隔离，避免误选。

#### 6.2.4 按标识加载机构（用于展示）（`loadOrganizationByIdForDisplay`）

- **业务意义**：按主键加载单条机构，用于**历史单据、审计、详情页**回显（含已停用、已废弃节点的名称展示）。
- **边界说明**：只读查询；不用于判断「是否允许新开业务引用」。
- **查询范围（读库，无写入）**：`adm_organization` 主档。
- **过滤口径**：默认包含所有非物理删除记录；若业务要求隐藏已废弃，由调用方传入策略参数。
- **组装规则**：返回名称、`type_enum`、`class_enum`、`ACTIVE` 状态及软删标记等展示字段。
- **返回约束**：不存在时返回空或明确「机构不存在」；已废弃节点仍可能需展示名称（BR-ORG-014）。
 - **实现对齐**：实现中可能以 `findById` 作为等价别名提供给应用层调用，语义以本条为准。

#### 6.2.5 废弃前引用扫描（`checkOrganizationAbolishable`）

- **业务意义**：在执行 `abolishOrganization` 前，判断当前是否存在**活跃外部引用**，避免废弃后破坏在途业务。
- **边界说明**：只读校验；可跨聚合查询引用计数或存在性（由应用层编排多域读服务时，仍保持本方法语义为「可废弃与否」）。
- **查询范围（读库，无写入）**：Encounter 非终态、PractitionerRole 有效分配、未结案费用归属等（与 BR-ORG-008 对齐的具体清单由实现阶段固化）。
- **过滤口径**：仅关心「仍会随业务推进变化」的引用；已归档终态历史不阻断废弃。
- **返回约束**：可废弃返回成功；否则返回结构化原因（如「存在在院就诊引用」）。

#### 6.2.6 全量机构树（`getOrganizationTree`）

- **业务意义**：提供机构管理场景使用的全量组织树（含门诊、住院、药房、库房等分类），用于维护界面、主数据浏览与层级定位。
- **边界说明**：只读查询；不做状态迁移，不做跨聚合写入。
- **查询范围（读库，无写入）**：`adm_organization` 全量节点，按创建时序与层级编码构树。
- **过滤口径**：默认不做业务场景裁剪（与挂号/住院选择口径区分）；是否隐藏逻辑删除节点由调用方策略决定。
- **组装规则**：以 `bus_no` 前缀关系构树，同级优先 `display_order`，无排序号时按名称稳定排序。
- **返回约束**：返回完整树结构；空库返回空树。

#### 6.2.7 ~~药房机构树（`getOrganizationTreeForPharmacy`）~~ **【已废止 2026-06-09】**

- **废止原因**：药房业务唯一口径为 `adm_location`（`LocationForm.PHARMACY`），经 `CurrentManagedLocationIdsResolver` 收窄；按 `Organization.class_enum = PHARMACY` 筛选**无法命中**实际维护的药房点位。
- **替代能力**：`LocationQueryService#listAllActivePharmacies`（仅药房）/ `listAllActivePharmacyPartyLocations`（发药/岗位药房族）/ `findActivePharmaciesByIds`；发药 init：`OutpatientPharmacyDispenseAppService` / `InpatientPharmacyDispenseAppService` → `CurrentManagedLocationIdsResolver#listManagedActivePharmaciesOrEmpty`。
- **响应字段兼容**：发药页 `pharmacyOrganizationOptions` **字段名保留**，`value` 为 **`adm_location.id`**（非 `adm_organization.id`）。

#### 6.2.8 ~~库房机构树（`getOrganizationTreeForStorage`）~~ **【已废止 2026-06-09】**

- **废止原因**：药库/药房库存业务唯一口径为 `adm_location`（`WAREHOUSE` / `PHARMACY`）；Organization 分类 **STORAGE/PHARMACY** 与库存点位无关联。
- **替代能力**：`LocationQueryService#listAllActiveWarehouses` / `listAllActivePharmacies`；库存各 AS 经 `CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`。
- **应用层**：`DepartmentAppService#getTreeForPharmacy` / `#getTreeForStorage` **已删除**。

#### 6.2.9 机构字典树（`getOrganizationDictTree`）

- **业务意义**：提供通用字典树（`OptionDto`）给应用初始化表单、弹窗选择器、规则配置页面复用。
- **边界说明**：只读查询；属于查询投影能力，不改变领域状态。
- **查询范围（读库，无写入）**：`adm_organization` 全量可见节点。
- **过滤口径**：默认与管理视图一致；业务需限制时由调用方在应用层二次裁剪。
- **组装规则**：统一转换为 `OptionDto(id,label,busNo,children)` 并按 `bus_no` 构树。
- **返回约束**：保证结构稳定与幂等，同一输入口径下返回顺序一致。

#### 6.2.10 责任科室机构树（`getOrganizationTreeForResponsibilityDept`）

- **业务意义**：提供门诊挂号/责任科室口径的机构树（科室、诊区、住院等业务类型的统一树）。
- **边界说明**：只读查询，不写库；作为 `loadOrganizationTreeForOutpatientRegistration` 的基础口径复用。
- **查询范围（读库，无写入）**：按机构业务类型枚举集合过滤后构建树；默认过滤已废弃节点。
- **组装规则**：按 `bus_no` 构树，同级按 `display_order`。
- **返回约束**：返回责任科室口径的树；无匹配返回空树。

#### 6.2.11 按层级编码加载机构（`findFirstByBusNo`）

- **业务意义**：为机构树/父级解析等只读场景，按 `busNo` 定位机构节点。
- **边界说明**：只读查询；不用于“是否允许新业务选用”的判定。
- **查询范围（读库，无写入）**：`adm_organization` 主档，按 `bus_no` 精确匹配。
- **过滤口径**：默认排除已逻辑删除节点（`isDeleted = true`）。
- **返回约束**：未命中返回空；命中多条视为数据异常，返回首条并记录告警（实现约定）。

### 6.3 领域边界说明

- 本聚合维护**机构/科室树与主数据状态**，不直接处理挂号业务规则、费用分摊、身份域账号生命周期；与 `OrganizationUnit` 的同步由应用层编排或约定事务保证（见不变式 BR-ORG-011）。
- 下游仅通过 `OrganizationId` / 语义化科室标识引用本聚合，不在本聚合内写入 Encounter/ChargeItem 等业务事实。

---

## 7. 不变式

| 编号         | 不变式                                                                                                        | 说明             |
| ---------- | ---------------------------------------------------------------------------------------------------------- | -------------- |
| BR-ORG-001 | 层级编码（`busNo`）全局唯一                                                                                          | 保证组织树结构的唯一可寻址性 |
| BR-ORG-002 | 子节点 `busNo` = 父节点.busNo + "." + 本级编码                                                                       | 确保层级关系在编码中可推导  |
| BR-ORG-003 | 同一父节点下机构名称不得重复                                                                                             | 防止同级组织名称混淆     |
| BR-ORG-004 | （已放宽）启用态机构可经引用校验后直接废弃；仍推荐先停用观察 | 降低误操作时可保留两阶段 SOP |
| BR-ORG-005 | 调整上级时，新上级不得为自身或自身的后代节点                                                                                     | 防止循环层级引用       |
| BR-ORG-006 | 存在已启用子节点的机构不得单独停用，须级联处理或先停用子节点                                                                             | 保证组织树状态一致性     |
| BR-ORG-007 | 重新启用时，父节点必须为已启用状态                                                                                          | 防止子节点启用而父节点不可用 |
| BR-ORG-008 | 被其他域**活跃引用**（如 Encounter 非终态、PractitionerRole 仍有效、在途费用未收口等）的机构不可废弃；已归档终态历史引用不阻断废弃                          | 引用完整性保护        |
| BR-ORG-009 | 科室节点：若维护 `medins_id` 则 `medins_lv` 必填；医院根节点医保字段均可空；**禁止**用 Organization `PHARMACY/STORAGE` 分类选药房/药库（见 §1.5） | 医保与库存口径分离      |
| BR-ORG-016 | 医院（`HOSPITAL`）须为单租户根节点：`parent_id` 为空；不可再建第二家医院；维护时无 `class_enum`/默认医师/科别 | 组织树层级正确性 |
| BR-ORG-010 | 草稿状态的机构不可被其他域引用（新建默认 ACTIVE，草稿仅存量或 publish 路径） | 防止引用未生效主数据     |
| BR-ORG-011 | 机构写操作后，**应用层**须按 `OrganizationUnitLinkCode` 同步 OU 增删改；**不要求**主键一致 | 跨域轻关联        |
| BR-ORG-012 | 聚合更新必须带版本号，发生并发冲突时拒绝写入（乐观锁）                                                                                | 并发一致性保护        |
| BR-ORG-013 | `RETIRED` / `DISCARDED` 状态的机构不得作为**新业务**的选用目标 | 阻断下线后的新依赖      |
| BR-ORG-014 | 历史业务展示所需科室名称，应在各业务聚合写入时落库**名称快照**或允许通过 `OrganizationId` 只读加载展示态机构；不得因机构已停用/废弃导致历史界面无法解释                    | 历史可追溯          |
| BR-ORG-015 | 废弃须为**两阶段**：先 `retireOrganization` 阻断新引用并观察，再 `abolishOrganization`；废弃前建议执行 `checkOrganizationAbolishable` | 降低对下游的冲击面      |

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合               | 关联方式            | 说明                                                    |
| ----- | ---------------- | --------------- | ----------------------------------------------------- |
| 身份认证  | OrganizationUnit | **code 轻关联**（无 FK、无 @MapsId） | 详见 §9.3 |

### 8.2 下游影响

| 下游上下文    | 聚合                 | 触发方式  | 说明                                        |
| -------- | ------------------ | ----- | ----------------------------------------- |
| 就诊管理     | Encounter          | ID 引用 | Encounter 通过 `机构科室标识(DepartmentId)` 引用科室  |
| 位置管理     | Location           | ID 引用 | Location 通过 `managingOrganization` 引用管理机构 |
| 医务人员     | PractitionerRole   | ID 引用 | PractitionerRole 通过 `organization` 引用所属科室 |
| 就诊账户     | Account            | ID 引用 | Account 通过 `科室标识(DepartmentId)` 引用费用归属科室  |
| 收费明细     | ChargeItem         | ID 引用 | ChargeItem 通过执行科室引用 Organization          |
| 药品发放     | MedicationDispense | ID 引用 | 发药药房通过机构标识引用 Organization                 |
| QueryService / 缓存 | —                  | 事件订阅  | 监听机构变更事件，刷新科室列表缓存与树形投影                    |

### 8.3 引用规则

> **统一规则**：所有下游领域模型引用 Organization 时，统一使用 `机构科室标识(OrganizationId)` 或语义化别名 `科室标识(DepartmentId)`。在 Encounter 等聚合中，约定使用 `科室标识(DepartmentId)` 指代费用归属或诊疗归属的科室。

### 8.4 事件流示意图

```
Organization                Encounter          PractitionerRole       QueryService/缓存
    │                          │                      │                  │
    │── PublishedEvent ──────────────────────────────────────────────────>│ (刷新科室列表)
    │                          │                      │                  │
    │── EditedEvent ─────────────────────────────────────────────────────>│ (刷新科室信息)
    │                          │                      │                  │
    │── RetiredEvent ────────────────────────────────────────────────────>│ (从可选列表移除)
    │                          │                      │                  │
    │── ReactivatedEvent ────────────────────────────────────────────────>│ (恢复到可选列表)
    │                          │                      │                  │
    │── HierarchyChangedEvent ───────────────────────────────────────────>│ (重建科室树)
    │                          │                      │                  │
    │── AbolishedEvent ───────────────────────────────────────────────────>│ (剔除缓存/失效投影)
    │                          │                      │                  │
```

## 9. 补充

### 9.1 组织树构建与查询说明

- **树构建算法**：基于 `busNo` 的前缀匹配实现无限级树构建——`busNo LIKE 'parent.busNo.%'` 即可获取所有后代节点
- **查询约束**：科室列表 / 树查询仅返回已启用（`active`）且未逻辑删除的节点
- **排序规则**：同级节点按 `displayOrder` 升序排列，未设置排序号的节点排在末尾
- **缓存策略**：科室列表 / 树应通过缓存加速；监听 `OrganizationPublishedEvent` / `OrganizationEditedEvent` / `OrganizationRetiredEvent` / `OrganizationReactivatedEvent` / `OrganizationHierarchyChangedEvent` / `OrganizationAbolishedEvent` 触发缓存刷新或失效

### 9.2 与 Practitioner / PractitionerRole 的协作

| 协作场景   | 说明                                                                     |
| ------ | ---------------------------------------------------------------------- |
| 医务人员归属 | PractitionerRole 通过 `organization` 字段关联科室，一名医务人员可在多个科室拥有不同角色           |
| 默认挂号医生 | Organization 维护 `默认挂号医生标识` 字段，门诊挂号未指定医生时使用该默认值（见 Encounter BR-ENC-012） |
| 科室启停级联 | 机构停用时不自动停用关联的 PractitionerRole，但停用的科室不可在新的 PractitionerRole 分配中被选用     |

### 9.3 身份域 OrganizationUnit 轻关联同步策略

> **实现**：`DepartmentAppService` + `OrganizationUnitLinkCode`

Organization 与 `whale_organization_units` **不共用主键**。关联键为 OU.`code`：

| 规则 | 值 |
| --- | --- |
| 优先 | `Organization.busNo` |
| 回退 | `org-{adm_organization.id}` |

| Organization 操作 | OrganizationUnit 同步 |
| --- | --- |
| 创建 | 按 code 查找；无则 insert（ZXPlat 雪花 id）；有则 update |
| 修改 | 按 code 更新；busNo 变更时迁移旧 code |
| 停用/启用 | 同步 `isEnabled`；启用时恢复软删 |
| 废弃 | 软删 OU |
| 调整上级 | `parent_id` = 上级组织对应 OU 的 ZXPlat id（按上级 code 解析） |

用户可登录科室（`whale_user_organization_units`）**不在此同步**；由岗位管理 `PositionAppService` 在保存岗位时派生（HIS org id → code → ZXPlat OU id）。

> **事务**：科室写操作与 OU 同步由 `DepartmentAppService` 在同一应用事务内编排；领域层 `OrganizationDomainService` 不依赖 identity 模块。

### 9.4 机构废弃 SOP（两阶段下线）与下游防影响

本节的「停用」指状态 `RETIRED`，「废弃」指逻辑删除态 `DISCARDED`（见 §2.3）。**ACTIVE 可直接废弃**（须先通过 `checkOrganizationAbolishable`）；仍推荐生产环境先停用观察的两阶段 SOP。

#### 9.41 推荐执行顺序（可落地）

1. **阻断新引用**：对目标机构执行 `retireOrganization`（BR-ORG-013：此后下拉/新开业务仅可选用 `ACTIVE` 机构）。
2. **活跃引用扫描与收口**：调用 `checkOrganizationAbolishable`（或等价编排），按 BR-ORG-008 处理 Encounter 非终态、PractitionerRole 有效关系、在途费用等；各领域完成在途业务迁移或自然终结。
3. **校验通过后废弃**：执行 `abolishOrganization`，发布 `OrganizationAbolishedEvent`。
4. **QueryService与缓存**：订阅方收到事件后刷新科室列表、失效树形投影（见 §9.3）。

#### 9.4.2 历史可读策略

- **新写入**：引用 Organization 的写操作须校验目标为 `ACTIVE`（BR-ORG-013）。
- **历史展示**：业务单据在写入时落库**科室名称等快照**，或通过 `loadOrganizationByIdForDisplay` 按 `OrganizationId` 回显（含已停用/已废弃），避免界面空白或「未知科室」（BR-ORG-014）。

#### 9.4.3 失败语义示例（废弃被拒）

| 场景                    | 返回语义（示例）                                | 说明                  |
| --------------------- | --------------------------------------- | ------------------- |
| 目标仍为 `ACTIVE`         | 「机构已启用，请先在主数据管理中停用后再废弃」                 | 强制两阶段，对应 BR-ORG-004 |
| 存在活跃 Encounter        | 「存在未完结就诊引用，无法废弃」+ 附 `encounterId` 或条数摘要 | BR-ORG-008          |
| 存在有效 PractitionerRole | 「存在有效人员科室关系，无法废弃」+ 附角色条数或示例 ID          | BR-ORG-008          |
| 在途费用未收口               | 「存在未结案费用归属，无法废弃」+ 附业务单号或条数              | BR-ORG-008          |

> **别名提示**：`机构科室标识(OrganizationId)` 与 `科室标识(DepartmentId)` 指同一雪花 ID，后者为就诊/费用等场景下的语义化别名（见 §8.3）。

#### 9.4.4 阶段要点速查

| 阶段            | 目的                 | 行为要点                                                                                              |
| ------------- | ------------------ | ------------------------------------------------------------------------------------------------- |
| 停用（`RETIRED`） | 阻断**新**业务选用，保留历史可读 | 下拉/树查询仅返回 `ACTIVE`；既有 Encounter 等引用仍合法，直至业务自然终结                                                   |
| 引用清理          | 满足 BR-ORG-008      | 在途就诊办结、人员科室调整、未结案费用收口等，由各领域配合处理                                                                   |
| 废弃（`DISCARDED`） | 逻辑删除，主键保留          | 执行 `checkOrganizationAbolishable` 通过后再 `abolishOrganization`；发布 `OrganizationAbolishedEvent` 刷新缓存 |
| 历史展示          | 不因废弃中断查询           | 依赖 BR-ORG-013 / BR-ORG-014：新写入拦截 + 历史快照或 `loadOrganizationByIdForDisplay`                         |

### 9.5 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3.0 | 2026-09-19 | §4.2 增补 `EMERGENCY`（急诊）、`PEIS`（体检）：各自系统执行，不作 WH-EXE001 开立锚点 |
| v1.2.0 | 2026-06-09 | **Organization/Location 分工**：新增 §1.5；废止 §6.2.7/6.2.8 药房/库房组织树；`class_enum` PHARMACY/STORAGE 标为历史字典；修订 BR-ORG-009/016 与医院节点医保可选；住院科室过滤口径改为 `type_enum=DEPARTMENT`+`INPATIENT` |
| v1.1.0 | 2026-06-03 | **M2**：PublicationStatus 默认 ACTIVE；OrganizationType 仅 HOSPITAL/DEPARTMENT；OU 改 code 轻关联（去 @MapsId）；§9.3 重写 |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 DomainService/QueryService 白名单对齐 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`OrganizationDomainService`

| 方法 |
|------|
| `createOrganization` |
| `publishOrganization` |
| `modifyOrganization` |
| `relocateOrganization` |
| `retireOrganization` |
| `reactivateOrganization` |
| `abolishOrganization` |

### QueryService：`OrganizationQueryService`

| 方法 |
|------|
| `listOrganizationsForOutpatientRegistration` |
| `loadOrganizationTreeForOutpatientRegistration` |
| `listOrganizationsForInpatientAdmission` |
| `listOrganizationsForNurseStation` |
| `listOrganizationsForWorkDepartment` |
| `loadOrganizationByIdForDisplay` |
| `mapByIds` |
| `mapNamesByIds` |
| `existsHospitalExcluding` |
| `listActiveHospitals` |
| `checkOrganizationAbolishable` |
| `getOrganizationTree` |
| `listOrganizationsForManagement` |
| `getOrganizationDictTree` |
| `getOrganizationTreeForResponsibilityDept` |
| `findOrganizationNamesByIds` |
| `findFirstByBusNo` |
| `mapIdsByBusNos` |
| `resolveWeistatCatyCode` |
| `mapYbNoByBusNos` |

