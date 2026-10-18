# DM-ADM-003 Location 位置

> **限界上下文**：管理域 · 位置管理  
> **聚合类型**：实体聚合  
> **版本**：v1.6 · **日期**：2026-10-12

---

## 1. 概述

### 1.1 业务定义

位置（Location）是院内**物理空间**的抽象表达，用于标识诊疗与管理活动发生的具体地点。位置通过层级编码组织为树形结构，覆盖从院区到床位的多级物理空间管理需求。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 物理空间管理 | 管理院区、楼栋、楼层、病区、病房、床位、诊室、药房、库房等物理位置主数据 |
| 层级结构维护 | 通过层级编码（busNo）和父位置引用维护位置间的从属关系 |
| 状态生命周期 | 管理位置的启用、暂停、停用、废弃等状态流转 |
| 床位运营管理 | 维护床位的运营状态（空闲/占用/预留/维护），支撑住院床位调度 |
| 检索与展示 | 提供拼音码、五笔码等检索码，支持位置快速选择与下拉过滤 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 住院登记 | 为患者分配病区、病房、床位 |
| 转床/转科 | 变更患者所在物理位置（床位/病区） |
| 诊室排班 | 门诊排班绑定具体诊室位置 |
| 药房窗口管理 | 发药窗口绑定具体药房位置 |
| 库房管理 | 物资出入库绑定具体库房位置 |
| 床位统计 | 按病区统计床位使用率与空闲床位数 |
| 住院护士站 | 按登录者**管理病区**加载病区→病房树；按 `bus_no` 前缀筛选在院患者地理范围（**AS-IP-NUR-001**） |
| 病区床位维护 | 基础资料维护病区/病房/床位三级主数据（`WardBedAppService`） |

### 1.4 与 Organization 的关系

| 维度 | Location（位置） | Organization（机构科室） |
|---|---|---|
| 管理对象 | 物理空间（**在哪里**） | 行政组织（**是谁/归谁管**） |
| 层级逻辑 | 院区 → 楼栋 → 楼层 → 病区/诊区 → 病房/诊室 → 床位 | 医院 → 院区 → 科室 → 医疗组 |
| 关联方式 | Location 通过 `机构标识(OrganizationId)` 引用所属科室 | Organization 不直接持有 Location 引用 |
| 典型示例 | "内科楼 3 层 301 病房 01 床" | "内科一科" |
| FHIR 映射 | `Location.managingOrganization` → Organization | `Organization.partOf` → Organization |

> **区分原则**：一个科室（Organization）可管理多个物理位置（Location）。病区以 `organization_id` 记**主挂靠**科室，并可经 `type_json.servedOrganizationIds` **同时服务多个住院科**（入院选科下病区按并集）。转科时变更的是 Encounter 的位置关联与科室关联；病区主挂靠可不随单次就诊转科而改。

### 1.5 药房/药库/病区与 Organization 分工（P0，2026-06-09 对齐实现）

| 业务 | 主数据 | 关键字段/方法 | 与 Organization 关系 |
|---|---|---|---|
| **药房点位**（药房单据 init、库存、入库） | `adm_location` | `form_enum = PHARMACY`；`listAllActivePharmacies` / `listPharmaciesByOrganization`（**仅药房**）；发药/岗位管辖见 `listManagedActivePharmaciesOrEmpty`（药房族并集） | **无** Organization 父子/分类关联；发药响应 `pharmacyOrganizationOptions` 的 `value` 实为 **locationId**（字段名历史兼容） |
| **科室药房点位**（科室单据/库存） | `adm_location` | `form_enum = DEPARTMENT_PHARMACY`；`organization_id` → 上级科室；`listAllActiveDepartmentPharmacies` / `listDepartmentPharmaciesByOrganization`；`requireSupplyRequestTypeForLocationId` → **`PHARMACY`**；岗位 id 收窄仍走 `findActivePharmaciesByIds`（药房族） | **挂靠** `Organization`（维护页须显式选择上级科室）；列表平铺展示 `organizationName` |
| **药库点位**（采购、出库、盘点） | `adm_location` | `form_enum = WAREHOUSE`；`listAllActiveWarehouses` + 管辖收窄 | **无** Organization 关联；**禁止** `Organization.class_enum = STORAGE` |
| **库房药房维护页** | `adm_location` | `WarehouseAppService`；`listAllWarehouseAndPharmacyLocations`；路由 `/basic/warehouse` | 药库/药房/科室药房**平铺**维护（非树）；`formEnum` 下拉 value 为 **`LocationForm.getCode()`**；科室药房必选上级科室 |
| **病区**（住院登记、病区床位维护） | `adm_location` | `form_enum = WARD`；`organization_id` → **主挂靠**住院科室；`type_json.servedOrganizationIds` → 附加服务科室（可多科共用一病区） | 病区挂靠 `Organization`（`class_enum = INPATIENT`），非 Organization 子节点；入院「科室下病区」= 主挂靠 ∪ 附加服务科 |
| **病房/床位** | `adm_location` | `HOUSE` / `BED`；父链 `WARD → HOUSE → BED` | 继承病区**主挂靠** `organization_id` |
| **病区床位维护 init** | `WardBedAppService` | `organizationOptions` ← `listOrganizationsForInpatientAdmission()` | 仅 **住院分类科室** 下拉，非全量机构树 |

> **交叉引用**：Organization 侧废止 `getOrganizationTreeForPharmacy` / `getOrganizationTreeForStorage`（见 **DM-ADM-004** §1.5、§6.2.7～6.2.8）。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，通过自引用实现层级关系。聚合边界以主表主键 `id`（位置标识）划分。

**表结构**：
- 主表：`adm_location`（主键：`id`）
- 关系：自引用（父位置标识 `parent_id`），形成树形结构
- 索引：`organization_id`（所属科室）、`bus_no`（层级编码）、`form_enum`（物理形式）

**高频查询模式**：
- 按位置标识查询：单表主键查询
- 按科室查询位置列表：`organization_id` 索引扫描
- 按层级编码查询：`bus_no` 索引扫描；**前缀查询** `bus_no LIKE '{父.bus_no}.%'`（护士站树展开、患者列表地理筛选）
- 按物理形式筛选：`form_enum` 索引扫描
- 按父级 + 形态 + 状态：`parent_id` + `form_enum` + `status_enum`（病房/床位子列表）
- 按主键集合 + 形态 + 状态：`id IN (...)` + `form_enum` + `status_enum`（岗位分配收窄为管理病区）

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 位置标识 | `LocationId` | 全局唯一标识，系统生成 |

### 2.3 状态机

```
  [DRAFT] ──启用──> [ACTIVE] ──停用──> [INACTIVE]
                        │   ^               │
                        │   └──启用──────────┘
                        │
                        ├──临时停用──> [SUSPENDED]
                        │                │
                        │                └──恢复──> [ACTIVE]
```

#### 状态定义（LocationStatus）

| code | info | 语义 |
| ---- | ---- | ---- |
| DRAFT | 草稿 | 登记后默认状态，不可被住院/床位业务引用 |
| ACTIVE | 启用 | 可被业务引用 |
| INACTIVE | 停用 | 退出可用集合 |
| SUSPENDED | 暂停 | 临时下线 |

> **编码对齐**：与实现 `LocationStatus` 枚举一致，持久化存 `code` 字符串。床位占用/空闲由 `operational_enum`（`LocationOperational`）维护，不与 `status_enum` 混用。

---

## 3. 实体

### 3.1 位置（Location）

Location 聚合根的属性定义，对应 `adm_location` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 位置标识 | `id` | `LocationId` | 全局唯一标识，系统生成，主键 |
| 位置名称 | `name` | `String` | 是 | 位置的业务名称，如"内科一病区""301房""01床" |
| 层级编码 | `bus_no` | `String` | 是 | 层级路径编码（`.` 分隔）：顶级为位置聚合采番段（前缀 `LO`，如 `LO0010`），子级为 `父 bus_no + "." + 本段`（如 `LO0010.LO0026.LO0076`）；**禁止**使用技术主键或 `display_order` 拼接 |
| 位置状态 | `status_enum` | `String` | 是 | 存 `LocationStatus.getCode()`（如 `ACTIVE`）；见 §2.3 |
| 运营状态 | `operational_enum` | `String` | 否 | 存 `LocationOperational.getCode()`；见 §4.2 |
| 物理形式 | `form_enum` | `String` | 是 | 存 `LocationForm.getCode()`（如 `WARD`/`HOUSE`/`BED`）；**禁止**整型 sortOrder 或历史数字串作为接口/持久化身份 |
| 位置模式 | `mode_enum` | `String` | 是 | 存 `LocationMode.getCode()`（默认 `INSTANCE`） |
| 父位置标识 | `parent_id` | `Long` | 否 | 根节点为空；病区床位树：病区→病房→床位 |
| 位置类型扩展 | `type_json` | `String` | 否 | 类型扩展 JSON，默认 `{}`；病区可含 `servedOrganizationIds`（Long 数组，附加服务住院科室） |
| 机构标识 | `organization_id` | `OrganizationId` | 是 | 主挂靠管理科室；病区另可由 `type_json.servedOrganizationIds` 附加服务科 |
| 显示顺序 | `display_order` | `Integer` | 否 | 同级位置的排序序号 |
| 拼音码 | `py_str` | `String` | 否 | 位置名称的拼音首字母缩写，辅助快速检索 |
| 五笔码 | `wb_str` | `String` | 否 | 位置名称的五笔编码缩写，辅助快速检索 |

> **审计字段**：继承自 `BaseAuditedEntity`（`createdAt` / `updatedAt` / `createdBy` / `updatedBy` / `deleted`），不再逐一列出。

本聚合为**单表主数据聚合**，当前无内部子实体。

> 位置层级通过 `父位置标识(parent_id)` 自引用实现父子关系，并与 `bus_no` 点分路径**双轨表达**同一棵树：`parent_id` 便于 ORM 与维护页按父节点加载；`bus_no` 便于前缀匹配展开子树（护士站、就诊位置筛选）。二者登记时同步生成，**禁止**仅改其一。

---

## 4. 值对象

### 4.1 物理形式（LocationForm）

| code | 说明 | sortOrder（仅下拉排序，**非**存储身份） |
|---|---|---|
| WARD | 病区 | 4 |
| BED | 床位 | 8 |
| HOUSE | 病房 | 10 |
| PHARMACY | 药房 | 16 |
| WAREHOUSE | 仓库（药库/物资库，**非**住院病房） | 17 |
| DEPARTMENT_PHARMACY | 科室药房（须挂靠 `organization_id` 上级科室） | 18 |

> **存储口径**：`form_enum` / `status_enum` / `operational_enum` / `mode_enum` 一律存枚举 **`code` 字符串**（如 `PHARMACY`、`WAREHOUSE`、`DEPARTMENT_PHARMACY`）；前后端接口传 **`getCode()`**，**禁止**数字整型、历史数字串（如 `16`/`17`/`18`）或 sortOrder 互转。  
> **sortOrder 列**：仅用于下拉排序（`OptionDto.sort`），**不是**持久化身份。  
> **住院三级树**：病区（`WARD`）→ 病房（`HOUSE`）→ 床位（`BED`）；病房**必须**为 `HOUSE`，**禁止**使用 `WAREHOUSE` 或其它形态冒充病房。  
> **基础资料维护**：病区/病房/床位由 **FR-BASIC-006**、`WardBedAppService` 维护，登记固定 `registerAsActive=true`；药库/药房/科室药房由 **`WarehouseAppService`**（路由 `/basic/warehouse`）维护，登记固定 `registerAsActive=true`，列表**平铺**（非树），科室药房须选上级科室。  
> FHIR 扩展形态（Site/Building 等）后续按需增补枚举，不与上表混用。

### 4.2 运营状态（LocationOperational）

| code | 说明 |
|---|---|
| `IN_USE` | 使用（床位占用等） |
| `NOT_IN_USE` | 未使用 |

> **存储口径**：`operational_enum` 存枚举 `code` 字符串；登记默认 `NOT_IN_USE`。

### 4.3 位置模式（LocationMode）

| code | 说明 |
|---|---|
| `INSTANCE` | 具体实例（如"内科一病区 301 房 01 床"） |
| `KIND` | 种类模板（不直接分配患者） |

> **存储口径**：`mode_enum` 存枚举 `code` 字符串；登记默认 `INSTANCE`。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 位置已登记 | `LocationRegisteredEvent` | 新位置登记成功 | 位置标识, 位置名称, 物理形式, 机构标识, 床位业务号, 病区标识, 病区名称 | QueryService/缓存 |
| 位置已启用 | `LocationActivatedEvent` | 位置启用成功 | 位置标识, 位置名称, 物理形式 | QueryService/缓存 |
| 位置已暂停 | `LocationSuspendedEvent` | 位置被临时暂停 | 位置标识, 暂停原因 | QueryService/缓存 |
| 位置已恢复 | `LocationResumedEvent` | 位置从暂停恢复为启用 | 位置标识 | QueryService/缓存 |
| 位置已停用 | `LocationDeactivatedEvent` | 位置被停用 | 位置标识, 停用原因, 物理形式 | QueryService/缓存、就诊预警 |
| 位置已废弃 | `LocationAbolishedEvent` | 位置被逻辑删除 | 位置标识 | QueryService/缓存 |
| 位置信息已变更 | `LocationInfoEditedEvent` | 位置名称/类型等属性变更 | 位置标识, 变更字段摘要 | QueryService/缓存 |
| 床位已占用 | `BedOccupiedEvent` | 床位运营状态变为占用 | 位置标识, 就诊标识(EncounterId) | 护理站、床位系统 |
| 床位已释放 | `BedReleasedEvent` | 床位运营状态变为空闲 | 位置标识, 原就诊标识(EncounterId) | 床位看板、统计 |
| 床位已预留 | `BedReservedEvent` | 床位运营状态变为预留 | 位置标识, 预留原因 | 床位看板 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 登记位置 | 草稿（库房等）/ 启用（病区床位 `registerAsActive`） | `LocationRegisteredEvent`（启用时另发 `LocationActivatedEvent`） |
| 草稿 | 启用 | `ACTIVE` | `LocationActivatedEvent` |
| `ACTIVE` | 暂停 | `SUSPENDED` | `LocationSuspendedEvent` |
| `SUSPENDED` | 恢复 | `ACTIVE` | `LocationResumedEvent` |
| `ACTIVE` | 停用 | `INACTIVE` | `LocationDeactivatedEvent` |
| `SUSPENDED` | 停用 | `INACTIVE` | `LocationDeactivatedEvent` |
| 草稿 | 废弃 | 已废弃 | `LocationAbolishedEvent` |
| `INACTIVE` | 废弃 | 已废弃 | `LocationAbolishedEvent` |
| — | 编辑信息 | （不变） | `LocationInfoEditedEvent` |
| 床位空闲 | 占用 | 床位占用 | `BedOccupiedEvent` |
| 床位占用 | 释放 | 床位空闲 | `BedReleasedEvent` |
| 床位空闲 | 预留 | 床位预留 | `BedReservedEvent` |

---

## 6. 领域服务

### 6.1 位置领域服务（LocationDomainService）

#### 6.1.1 登记位置（`registerLocation`）
- **业务意义**：创建位置主数据并形成稳定层级编码。
- **边界说明**：仅维护 Location 聚合，不处理排班或就诊分配编排。
- **前置条件**：同父级名称不重复（BR-LOC-002）；层级编码生成合法（BR-LOC-003）；物理形式层级合法（BR-LOC-004）。
- **命令项扩展**：`RegisterLocationItemCommand.registerAsActive` 为 `true` 时走 `initActive()`，否则 `initDraft()`。
- **状态变化**：默认草稿 + `operational_enum=NOT_IN_USE`；病区床位应用层固定 `registerAsActive=true`，登记即为 `ACTIVE` + `NOT_IN_USE`。
- **聚合内影响**：写入主档；`bus_no` 由 `BusinessSequenceService` 生成本段并与父级 `bus_no` 级联拼接。
- **事件输出**：`LocationRegisteredEvent`；`registerAsActive=true` 时另发 `LocationActivatedEvent`。

#### 6.1.2 启用位置（`activateLocation`）
- **业务意义**：将草稿位置发布为可引用状态。
- **边界说明**：只改位置状态，不处理下游引用迁移。
- **前置条件**：仅草稿可启用；机构引用有效且已启用（BR-LOC-007）。
- **状态变化**：草稿 -> 已启用。
- **事件输出**：`LocationActivatedEvent`。

#### 6.1.3 编辑位置信息（`editLocationInfo`）
- **业务意义**：维护位置名称与展示属性；库房维护页可变更 `organization_id`（科室药房改上级科室）。
- **边界说明**：仅改主档属性，不改层级结构（`parent_id` / `bus_no` 不在本命令范围）。
- **前置条件**：位置非已废弃；同父级名称唯一（BR-LOC-002）。
- **命令项扩展**：`EditLocationInfoItemCommand.organizationId` 非空时更新挂靠机构；`changedFields` 须声明 `organizationId` 等实际变更列。
- **聚合内影响**：名称变更时重算检索码。
- **事件输出**：`LocationInfoEditedEvent`。

#### 6.1.4 暂停位置（`suspendLocation`）
- **业务意义**：临时下线位置资源。
- **边界说明**：只改变可用状态，不删除资源。
- **前置条件**：仅已启用可暂停；被占用床位不可暂停（BR-LOC-001）。
- **状态变化**：已启用 -> 已暂停。
- **事件输出**：`LocationSuspendedEvent`。

#### 6.1.5 恢复位置（`resumeLocation`）
- **业务意义**：恢复暂停位置为可用状态。
- **前置条件**：仅已暂停可恢复。
- **状态变化**：已暂停 -> 已启用。
- **事件输出**：`LocationResumedEvent`。

#### 6.1.6 停用位置（`deactivateLocation`）
- **业务意义**：将位置退出可用集合。
- **边界说明**：只做状态终止，不做物理删除。
- **前置条件**：仅已启用/已暂停可停用；占用床位不可停用（BR-LOC-001）；存在启用/暂停子节点不可停用（BR-LOC-006）。
- **状态变化**：已启用/已暂停 -> 已停用。
- **事件输出**：`LocationDeactivatedEvent`。

#### 6.1.7 废弃位置（`abolishLocation`）
- **业务意义**：终止不再使用的位置并保留审计追踪。
- **前置条件**：仅草稿/已停用可废弃；无活跃引用（BR-LOC-008）；子节点均已废弃。
- **状态变化**：草稿/已停用 -> 已废弃。
- **事件输出**：`LocationAbolishedEvent`。

#### 6.1.8 占用床位（`occupyBed`）
- **业务意义**：将床位运营状态置为占用，绑定在院就诊。
- **边界说明**：只维护 Location 运营状态，不改 Encounter 状态。
- **前置条件**：必须是床位（BR-LOC-005）；模式为实例（BR-LOC-010）；床位当前为 `NOT_IN_USE` 或（`RESERVED` 且 App 已校验预约患者，GAP-ATD-007）；位置状态为已启用。
- **并发（GAP-ATD-001/002）**：写路径须 `PESSIMISTIC_WRITE` 加载床位行，锁内 `assertVacantForOccupy()` 后再 `occupy()`；实体映射 `@Version`（`adm_location.version`）；批量占床按 `locationId` 升序加锁。
- **状态变化**：床位运营状态 -> 占用。
- **事件输出**：`BedOccupiedEvent`。

#### 6.1.9 释放床位（`releaseBed`）
- **业务意义**：将床位从占用恢复为空闲。
- **前置条件**：必须是床位且当前为占用。
- **并发（GAP-ATD-003）**：写路径须 `PESSIMISTIC_WRITE` 加载床位行，锁内 `assertOccupiedForRelease()` 后再 `release()`；批量释床按 `locationId` 升序加锁。
- **状态变化**：占用 -> 空闲。
- **事件输出**：`BedReleasedEvent`。

#### 6.1.9a 互换床运营态（`swapBedsOperational`）
- **业务意义**：互换床场景下双床 release + 交叉 occupy，消除分步 Cmd 间空窗。
- **前置条件**：参与互换的两床均 `IN_USE`；交叉占床目标在锁内须 `NOT_IN_USE`（释床后内存态）。
- **并发（GAP-ATD-003）**：两床 `locationId` 升序 `PESSIMISTIC_WRITE`；锁内先双方 `release`、再交叉 `occupy`；**单次** `saveAll`；占床路径复用 `assertVacantForOccupy` 与 `@Version`。
- **状态变化**：双床运营态对调。
- **事件输出**：`BedReleasedEvent` ×2 + `BedOccupiedEvent` ×2。

#### 6.1.10 预留床位（`reserveBed`）
- **业务意义**：将空闲床位标记为预留。
- **前置条件**：必须是床位；当前为 `NOT_IN_USE`；位置状态为已启用。
- **并发（GAP-ATD-007）**：`requireBedForUpdate` + `assertVacantForReserve` + `reserve()`。
- **状态变化**：`NOT_IN_USE -> RESERVED`。
- **事件输出**：`BedReservedEvent`。

#### 6.1.10a 释放床位预留（`releaseReservedBed`）
- **业务意义**：取消预约或入院确认前释放预留态。
- **前置条件**：床位 `RESERVED`。
- **状态变化**：`RESERVED -> NOT_IN_USE`。
- **事件输出**：`BedReleasedEvent`（无 encounterId）。

#### 6.1.11 关闭库存期初（`closeInventoryOpening`）
- **业务意义**：将药库/药房/科室药房标记为**期初已关闭**（独立表 `adm_location_inventory_opening`）；关闭后禁止再建/改/同意期初单；可空关；业务封锁命中时应用须先调本方法（U3）。
- **前置**：关前应用校验无 OPENING `APPROVAL`（U8）；本方法幂等写关闭行（`closed_at`/`closed_by`）。
- **事件**：以实现为准。

### 6.2 读侧查询服务（LocationQueryService）

#### 6.2.0a 期初关闭态（`isInventoryOpeningClosed` / 批量 map）
- **业务意义**：读 `adm_location_inventory_opening` 判断某 `location_id` 是否已关闭；列表初始化下拉带 `openingClosed` 标志。
- **边界**：只读；未建关闭行视为未关闭。

#### 6.2.1 按科室查病区列表（`listWardsByOrganization`）
- **业务意义**：为住院登记提供可选病区范围（「选科室 → 选该科室下病区」）。
- **边界说明**：只读查询，不做资源状态修改。
- **查询范围**：启用 `formEnum=WARD`；命中条件为 **`organization_id = organizationId` 或 `type_json.servedOrganizationIds` 含该科**（多科共用一病区）。
- **判定方法**：`wardServesOrganization(ward, organizationId)`（同口径供登记 Gate）。

#### 6.2.2 按病区统计床位（`countBedsByWard` / `countTotalBedsByWard`）
- **业务意义**：提供床位余量判断依据（空闲床 / 总床）。
- **边界说明**：只读统计，不替代床位分配动作。
- **查询范围**：先取病区下 `formEnum=HOUSE` 的病房，再汇总各病房下 `formEnum=BED` 且 `status_enum=ACTIVE` 的床位；`vacantOnly=true` 时另要求 `operational_enum=NOT_IN_USE`。床位必须挂在病房下，不接受直挂病区或其它形态。
- **实现口径**：`countTotalBedsByWard(wardId)` 等价于 `countBedsByWard(wardId, false)`。

#### 6.2.3 按病区查病房（`listRoomsUnderWard`）
- **业务意义**：病区床位维护页加载病房列表、统计床位的前置查询。
- **查询范围**：`parent_id = 病区` 且 `form_enum = HOUSE`；**不包含** `WAREHOUSE` 等其它形态。

#### 6.2.4 按父位置查床位（`listBedsUnderParent`）
- **业务意义**：加载某病房（或合法父节点）下的床位列表。
- **查询范围**：`parent_id = 父位置` 且 `form_enum = BED`。

#### 6.2.5 按物理形态查全部位置（`listAllByFormCode`）
- **业务意义**：维护页按形态过滤下拉（如全部病区、全部病房）。
- **查询范围**：`form_enum = 指定 code`，条件在仓储层过滤，禁止应用层全表内存筛选。

#### 6.2.6 按机构与形态查位置（`listByOrganizationIdAndFormCode`）
- **业务意义**：按机构收窄的维护列表（不限状态）。
- **查询范围**：非 `WARD` 仍为 `organization_id` + `form_enum`；**`WARD`** 与 §6.2.1 同口径（主挂靠 ∪ `servedOrganizationIds`），使共用病区在每个挂靠科维护树下可见。

#### 6.2.7 按父级 busNo 查直接子节点（`listChildrenByParentBusNo`）
- **业务意义**：按层级编码展开**下一层**（病区→病房、病房→病床），不依赖 `parent_id` 逐层跳转；与 **BR-LOC-003** 点分路径一致。
- **查询范围**：`bus_no` 以 `父.bus_no + "."` 为前缀，且后缀中**不再含** `.`（仅直接子节点）。
- **重载（`listChildrenByParentBusNo(parentBusNo, formCode)`）**：可选按 `form_enum` 过滤（如仅 `HOUSE`）；结果按 `display_order`、`id` 排序。

#### 6.2.8 按病区查启用病房（`listHousesUnderWard`）
- **业务意义**：住院护士站左侧树子节点（**AS-IP-NUR-001** IF-IP-NUR-01）；仅展示可筛选的病房，不含床位。
- **边界说明**：与 **6.2.3** 区分——维护页 `listRoomsUnderWard` **不限** `status_enum`；本方法仅 `status_enum=ACTIVE`。
- **查询范围**：`parent_id = 病区` 且 `form_enum = HOUSE` 且 `status_enum = ACTIVE`。

#### 6.2.9 按病区 busNo 查启用病房（`listActiveHousesByWardBusNo`）
- **业务意义**：与 **6.2.8** 等价语义，入参为病区 `bus_no`；先 **6.2.7** 取 `HOUSE` 直接子节点，再过滤 `ACTIVE`。
- **查询范围**：`bus_no` 一层子前缀 + `form_enum=HOUSE` + `status_enum=ACTIVE`。

#### 6.2.10 查全部启用病区（`listAllActiveWards`）
- **业务意义**：登录者无岗位分配、或分配中无 `WARD` 形态位置时的**并集口径**（**BR-IP-NUR-001** / **AS-IP-NUR-001**）。
- **查询范围**：`form_enum = WARD` 且 `status_enum = ACTIVE`（全机构，不按 `organization_id` 收窄）。

#### 6.2.11 管理病区解析（**已自本聚合移除**，2026-06）

> **DOC-CONFLICT 收敛**：原 **`LocationQueryService#listManagedWardsForPractitioner`** 含跨聚合读编排（注入 **DM-ADM-006**），违反 **B.6.1.3.1**，**已从代码删除**。

- **现行落点（应用层）**：
  1. **`HealthPractitionerManagedScopeResolver`**（内部 `PractitionerManagedScopeLoader` → **DM-ADM-006** `resolveManagedScope`；**App 禁止直读**登录上下文）；
  2. **`CurrentManagedWardLocationIdsResolver#listManagedWardLocationIdsOrEmpty`** / **`#listManagedActiveWardsOrEmpty`**：队列 Gate、护士站初始化地理树、入出转默认范围等（**AS-IP-NUR-001～006**）；
  3. 本聚合仅提供 **`listAllActiveWards`**、**`findAllByIdInAndFormEnumAndStatusEnum`** 等**单聚合**读能力，由 Resolver 在拿到 id 集合后调用。
- **回退语义**：非严格模式且管辖 id 为空时，`listManagedActiveWardsOrEmpty` 可回退 **6.2.10**；严格模式（`isPractitionerDataScopeActive`）无管辖则返回空表。

#### 6.2.12 按科室查药房列表（`listPharmaciesByOrganization`）
- **业务意义**：药房业务单据、药品目录等按机构收窄**仅药房**候选。
- **边界说明**：只读查询，不做资源状态修改；**不含**科室药房（科室侧用 `listDepartmentPharmaciesByOrganization`）。
- **查询范围**：按 `organizationId` 查询 `form_enum = PHARMACY` 且 `status_enum=ACTIVE`、未软删的位置。

#### 6.2.12a 按科室查科室药房列表（`listDepartmentPharmaciesByOrganization`）
- **业务意义**：科室药房业务单据按机构收窄候选。
- **查询范围**：按 `organizationId` 查询 `form_enum = DEPARTMENT_PHARMACY` 且 `status_enum=ACTIVE`。

#### 6.2.12b 按科室查药房族并集（`listPharmacyPartyByOrganization`）
- **业务意义**：供应类型 `PHARMACY(16)` 下拉、岗位「管理药房」无管辖回落等**显式并集**场景。
- **查询范围**：`listPharmaciesByOrganization` ∪ `listDepartmentPharmaciesByOrganization`。

#### 6.2.13 按科室查药库列表（`listWarehousesByOrganization`）
- **业务意义**：为库存管理和物资出入库提供药库范围。
- **边界说明**：只读查询，不做资源状态修改。
- **查询范围**：按 `organizationId` 查询 `form_enum=WAREHOUSE` 且启用、未软删的位置。

#### 6.2.14 查全部启用药房（`listAllActivePharmacies`）
- **业务意义**：药房业务线 init/Gate 全院**仅药房**候选（请领/入库/退库/盘点/损益等）；**不含**科室药房。
- **查询范围**：`form_enum = PHARMACY` 且 `status_enum=ACTIVE`；不按 `organization_id` 收窄。

#### 6.2.14a 查全部启用药房族并集（`listAllActivePharmacyPartyLocations`）
- **业务意义**：**药库出库目的**、退库入库源侧筛选、岗位管辖回落等确需「药房+科室药房」的场景；**禁止**药房业务单据误用本方法。
- **查询范围**：`listAllActivePharmacies` ∪ `listAllActiveDepartmentPharmacies`。

#### 6.2.15 查全部启用药库（`listAllActiveWarehouses`）
- **业务意义**：药库业务 init 下拉的全院药库候选。
- **查询范围**：`form_enum=WAREHOUSE` 且 `status_enum=ACTIVE`。

#### 6.2.15a 查全部启用科室药房（`listAllActiveDepartmentPharmacies`）
- **业务意义**：科室业务线 init/Gate 全院**仅科室药房**候选。
- **查询范围**：`form_enum = DEPARTMENT_PHARMACY` 且 `status_enum=ACTIVE`。

#### 6.2.16 查全部库房维护形态（`listAllWarehouseAndPharmacyLocations`）
- **业务意义**：基础资料「库房药房」维护页平铺列表（**含各启用/停用态**）；取药科室模板保存 Gate（**DM-CUS-006** `BR-SLT-008`）。
- **查询范围**：`form_enum ∈ {WAREHOUSE, PHARMACY, DEPARTMENT_PHARMACY}`，**不限** `status_enum`。

#### 6.2.17 按 id 批量查启用药房（`findActivePharmaciesByIds`）
- **业务意义**：岗位分配收窄、管辖 Resolver 与发药 Gate 共用；**药房族**含 `PHARMACY` 与 `DEPARTMENT_PHARMACY`。
- **查询范围**：`id IN (...)` 且 `form_enum ∈ {PHARMACY, DEPARTMENT_PHARMACY}` 且 `status_enum=ACTIVE`。

#### 6.2.18 按位置解析供应类型（`requireSupplyRequestTypeForLocationId`）
- **业务意义**：供应单头 `source_type_enum` / `purpose_type_enum` 落库（**B.10.8.1**）；库存类 Mapper 据 `locationId` 推导，HTTP 请求体不传类型枚举。
- **映射口径**：

| `LocationForm.code` | `SupplyRequestType` |
|---|---|
| `WAREHOUSE` | `CABINET` |
| `PHARMACY` | `PHARMACY` |
| `DEPARTMENT_PHARMACY` | `PHARMACY`（与药房等价） |
| `WARD` | `DEPARTMENT` |

- **边界说明**：床位等其它形态拒绝，防止误落库。

#### 6.2.19 库房药房应用编排（`WarehouseAppService`）

> 包路径：`com.zxplat.domain.application.basic.warehouse`；HTTP `POST /api/app/warehouse/{methodName}`；前端路由 `/basic/warehouse`。

| 方法 | 说明 | 领域调用 |
|---|---|---|
| `init` | 位置状态字典、上级科室下拉（`getOrganizationTreeForResponsibilityDept`）、形态三项下拉（value=`LocationForm.getCode()`） | 术语 + Organization/Location QueryService |
| `getWarehouseList` | 平铺列表 + 关键词；科室药房行 enrich `organizationName` | `listAllWarehouseAndPharmacyLocations` |
| `getWarehouse` / `createWarehouse` / `editWarehouse` | 详情/登记/编辑；科室药房 `organizationId` 必填 | `LocationDomainService` register/edit |
| `enableWarehouse` / `deactivateWarehouse` / `deleteWarehouse` | 启停与废止；扁平节点无子节点门禁 | activate/deactivate/abolish |

- **挂靠机构**：药库/药房创建时回落机构树首节点；科室药房须请求显式 `organizationId`。
- **形态校验**：应用层与 `WarehouseMapper` 仅接受 `WAREHOUSE` / `PHARMACY` / `DEPARTMENT_PHARMACY` 三种 code。

#### 6.2.20 按供应类型序号列位置（`listLocationsByType`）

- **业务意义**：供应请求模块按 `source_type_enum` / `purpose_type_enum`（`SupplyRequestType.sortOrder`）筛选机构下启用位置下拉。
- **映射口径**：

| `typeSortOrder` | 查询范围 |
|---|---|
| `2`（`DEPARTMENT`） | `form_enum = WARD` |
| `16`（`PHARMACY`） | **`listPharmacyPartyByOrganization` 并集**：`PHARMACY` + **`DEPARTMENT_PHARMACY`**（供应头均映射 `SupplyRequestType.PHARMACY`；与药房单据「仅药房」口径分离） |
| `17`（`CABINET`） | `form_enum = WAREHOUSE` |

- **边界说明**：`organizationId` 为空返回空表；其它 `typeSortOrder` 返回空表。

#### 6.2.21 仅启用药房（不含科室药房）（`listAllActivePharmacyOnlyLocations`）

- **业务意义**：与 **`listAllActivePharmacies`** 同口径（仅 `PHARMACY`）；历史别名，药房平级调拨等可继续调用。
- **查询范围**：委托 `listAllActivePharmacies()`。

### 6.3 领域边界说明

- 本聚合维护**位置主数据与层级树**，不执行就诊状态迁移、排班 Slot 生成、库存实物出入库；床位占用/释放由 Encounter 等聚合编排调用本聚合写方法完成事实落库。
- `Organization` 存在性与启用态由引用方或应用层校验；本聚合仅校验 BR-LOC-* 不变式。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-LOC-001 | 被占用的床位不可暂停或停用 | 运营状态为"占用"的床位必须先释放，确保在院患者不受影响 |
| BR-LOC-002 | 同父级下位置名称不可重复 | 在相同父位置下，位置名称必须唯一，防止混淆 |
| BR-LOC-003 | 层级编码创建后不可修改 | 登记时由 `BusinessSequenceService` + `AggregateTypeConstants.LOCATION` 生成本段；有父级时 `bus_no = 父.bus_no + "." + 本段`；**禁止**用 `parent_id`、`display_order` 或 ROOT 占位拼接；生成后不可变更 |
| BR-LOC-004 | 物理形式须符合层级约束 | 院区下可建楼栋；楼栋下可建楼层；楼层下可建病区/诊区；病区（WARD）下可建病房（HOUSE）；病房下可建床位（BED）。禁止逆层级嵌套 |
| BR-LOC-005 | 运营状态仅适用于床位 | 只有物理形式为"床位"的位置才维护运营状态；非床位位置的运营状态字段为空 |
| BR-LOC-006 | 下级存在已启用/已暂停节点时父级不可停用 | 停用操作须递归检查所有子孙节点，确保无"已启用"或"已暂停"状态的后代 |
| BR-LOC-007 | 机构引用须存在且为已启用状态 | `机构标识(OrganizationId)` 所引用的组织机构须存在且处于已启用状态 |
| BR-LOC-008 | 被活跃就诊引用的位置不可废弃 | 如存在活跃状态的 `EncounterLocation` 引用该位置，则不允许废弃 |
| BR-LOC-009 | 层级深度不超过 6 层 | 位置树最大深度为 6 层（院区→楼栋→楼层→病区→病房→床位），防止层级失控 |
| BR-LOC-010 | 位置模式为"类型"时不可分配患者 | `LocationMode = KIND` 的位置仅作为模板定义，不参与床位运营和就诊分配 |
| BR-LOC-011 | 枚举身份仅用 code | `form_enum`/`status_enum`/`operational_enum`/`mode_enum` 接口与库表均使用枚举 **code** 字符串；**禁止**整型 sortOrder、历史数字串或其它形态冒充（如用 `WAREHOUSE` 表示病房） |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 管理域 · 机构管理 | Organization | `机构标识(OrganizationId)` | 位置所属的管理科室，创建时验证机构存在且已启用 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 管理域 · 就诊管理 | Encounter | 事件订阅 / ID 引用 | 住院登记时通过 `位置标识(LocationId)` 绑定床位；监听 `LocationDeactivatedEvent` 预警在院患者 |
| 管理域 · 岗位管理 | PractitionerRole | ID 引用 | 岗位角色通过 `位置标识(LocationId)` 绑定护士站/药房/库房等工作位置 |
| 工作流域 · 排班管理 | Schedule / Slot | ID 引用 | 门诊排班通过 `位置标识(LocationId)` 绑定具体诊室 |
| 财务域 · 库存管理 | InventoryItem | ID 引用 | 库存按 `位置标识(LocationId)` 维度管理不同药房/库房的在库数量 |
| 住院护理 · 护士站 | —（应用服务 **AS-IP-NUR-001**） | 读侧查询 | **`CurrentManagedWardLocationIdsResolver`** + `listHousesUnderWard` / `listChildrenByParentBusNo`；患者列表地理范围可比对活动床位 `bus_no` 与所选病房/病区前缀 |

### 8.3 引用规则

> **统一规则**：所有需要引用位置的领域模型，遵循以下原则：
>
> - 通过 `位置标识(LocationId)` 引用，不直接持有 Location 对象
> - 位置名称等展示字段通过QueryService冗余，写模型仅存储 ID
> - 机构引用统一使用 `机构标识(OrganizationId)`（对应 FHIR `managingOrganization` 字段）

### 8.4 事件流示意图

```
Encounter                Location              PractitionerRole       Schedule/Slot
    │                       │                       │                       │
    │── 入院占床 ──────────>│                       │                       │
    │                       │── BedOccupiedEvent ──>│（通知护士站）           │
    │                       │                       │                       │
    │── 出院释放 ──────────>│                       │                       │
    │                       │── BedReleasedEvent ──>│（更新床位看板）         │
    │                       │                       │                       │
    │                       │── LocationDeactivated>│── 解除绑定             │
    │                       │         Event         │                       │── 关闭排班
    │                       │                       │                       │
```

---

## 9. 补充

### 9.1 位置层级与床位管理

#### 典型层级结构

**住院三级（系统强制形态）**：`WARD` → `HOUSE` → `BED`；`bus_no` 示例 `LO0010.LO0026.LO0076`（病区.病房.床位）。

```
XX医院总院（院区/site）
├── 内科楼（楼栋/building）
│   ├── 3楼（楼层/wing）
│   │   ├── 内科一病区（WARD）
│   │   │   ├── 301房（HOUSE）
│   │   │   │   ├── 01床（BED）
│   │   │   │   └── 02床（BED）
│   │   │   └── 302房（HOUSE）
│   │   └── 内科二病区（WARD）
│   └── 4楼（楼层/wing）
├── 门诊楼（楼栋/building）
│   ├── 1楼（楼层/wing）
│   │   ├── 内科诊室1（诊室/clinic-room）
│   │   └── 内科诊室2（诊室/clinic-room）
│   └── 药房（药房/pharmacy）
└── 后勤楼（楼栋/building）
    └── 中心药库（库房/warehouse）
```

#### 床位运营状态流转

```
  [空闲] ──入住──> [占用] ──出院/转床──> [空闲]
    │                                    ^
    ├──预留──> [预留] ──确认入住──> [占用]   │
    │                  │                  │
    │                  └──取消预留─────────┘
    │
    ├──维护──> [维护中] ──维护完成──────────┘
    │
    └──关闭──> [关闭] ──重新开放───────────┘
```

### 9.2 与 Encounter 的职责边界

| 职责 | Location | Encounter |
|---|---|---|
| 位置主数据 | 管理物理位置的创建、启停用、层级 | — |
| 床位运营状态 | 维护空闲/占用/预留/维护状态 | 通过 `EncounterLocation` 记录入住轨迹 |
| 转床操作 | 执行床位的释放与占用 | 维护位置变更历史与转科关联 |
| 床位统计 | 提供按病区的床位数量与空闲数查询 | — |
| 在院患者查询 | — | 提供按位置维度的在院患者列表 |

### 9.3 读侧查询索引（与实现对齐）

| 方法 | 典型场景 | 备注 |
| --- | --- | --- |
| `listWardsByOrganization` | 住院登记选病区 | 启用病区 |
| `listRoomsUnderWard` | 病区床位维护 | 不限状态 |
| `listHousesUnderWard` | 护士站树·病房子节点 | 仅 `ACTIVE` |
| `listBedsUnderParent` | 维护页/分床选床 | 父级多为 `HOUSE` |
| `listChildrenByParentBusNo` | 按 `bus_no` 展开一层 | 可选 `formCode` |
| `listActiveHousesByWardBusNo` | 按病区编码列病房 | 护士站 |
| `listAllActiveWards` | 无管理病区时全量根 | BR-IP-NUR-001 |
| `listPharmaciesByOrganization` | 按科室列药房 | 仅 `PHARMACY` |
| `listDepartmentPharmaciesByOrganization` | 按科室列科室药房 | 仅 `DEPARTMENT_PHARMACY` |
| `listPharmacyPartyByOrganization` | 供应类型 `16` / 管辖回落 | 药房族并集 |
| `listWarehousesByOrganization` | 按科室列药库 | `WAREHOUSE` |
| `listAllActivePharmacies` | 药房业务线全院下拉/Gate | **仅** `PHARMACY`（不含科室药房） |
| `listAllActiveDepartmentPharmacies` | 科室业务线全院下拉/Gate | 仅 `DEPARTMENT_PHARMACY` |
| `listAllActivePharmacyPartyLocations` | 药库出库目的、退库入库源侧等 | 药房族显式并集 |
| `listAllActiveWarehouses` | 全院启用药库下拉 | — |
| `listAllWarehouseAndPharmacyLocations` | 库房药房维护页 / 取药科室 Gate | 含各状态 |
| `findActivePharmaciesByIds` | 管辖药房收窄 | 药房族（`PHARMACY`+`DEPARTMENT_PHARMACY`） |
| `listLocationsByType` | 供应单按 `SupplyRequestType.sortOrder` 列位置 | `16`→`listPharmacyPartyByOrganization` |
| `listAllActivePharmacyOnlyLocations` | 同 `listAllActivePharmacies` | 仅 `PHARMACY` |
| `requireSupplyRequestTypeForLocationId` | 供应单头类型推导 | 科室药房→`PHARMACY` |
| ~~`listManagedWardsForPractitioner`~~ | **已移除**；管理病区见 **CurrentManagedWardLocationIdsResolver** | **PractitionerManagedScopeLoader** + **DM-ADM-006** |
| `countBedsByWard` / `countTotalBedsByWard` | 空床/总床统计 | 经 `HOUSE` 汇总 `BED` |

### 9.4 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.6 | 2026-10-12 | **代码先行回写**：病区多科挂靠——`organization_id` 为主挂靠，`type_json.servedOrganizationIds` 为附加服务科；`listWardsByOrganization` / `listByOrganizationIdAndFormCode(WARD)` / `wardServesOrganization` 按并集判定；不新建关系表 |
| v1.5 | 2026-07-24 | **库位三分口径**：`listAllActivePharmacies` / `listPharmaciesByOrganization` **仅** `PHARMACY`；科室侧 `listAllActiveDepartmentPharmacies`；确需并集新增 **`listAllActivePharmacyPartyLocations`** / **`listPharmacyPartyByOrganization`**（药库出库目的、`listLocationsByType(16)`、岗位管辖回落）；禁止药房/科室单据互串 |
| v1.4 | 2026-07-02 | **`listLocationsByType(16)`** 改为药房族并集（`PHARMACY`+`DEPARTMENT_PHARMACY`）；新增 **`listAllActivePharmacyOnlyLocations`**（药房平级调拨专用，不含科室药房） |
| v1.3 | 2026-06-30 | 新增 **`DEPARTMENT_PHARMACY`**（科室药房）；§6.2.14～§6.2.19 药房族读查询与 `WarehouseAppService`；`requireSupplyRequestTypeForLocationId` 映射；接口/库表**仅用** `LocationForm.getCode()`，废止 `16`/`17`/`18` 数字形态码 |
| v1.0 | — | 首版 |
| v1.2.0 | 2026-06-09 | 新增 §1.5：药房/药库/病区与 Organization 分工；发药 `pharmacyOrganizationOptions` 实为 locationId；废止 Organization 药房/库房树 |
| v1.2 | 2026-06-09 | §6.2.11 移除 `listManagedWardsForPractitioner`；管理病区收口 **`CurrentManagedWardLocationIdsResolver`** + **B.6.1.3.1** |
| v1.1 | 2026-05-20 | 住院三级 `WARD`/`HOUSE`/`BED` 与 `bus_no` 双轨；护士站管理病区/病房读查询；与 **AS-IP-NUR-001** GAP-003 对齐 |
| v1.1.2 | 2026-06-03 | §6.2.11 区分 Location 查询 vs App `CurrentManagedWardLocationIdsResolver` |
| v1.1.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `LocationDomainService`/`LocationQueryService` 白名单对齐（10写+13读） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`LocationDomainService`

| 方法 |
|------|
| `registerLocation` |
| `activateLocation` |
| `editLocationInfo` |
| `suspendLocation` |
| `resumeLocation` |
| `deactivateLocation` |
| `abolishLocation` |
| `occupyBed` |
| `releaseBed` |
| `swapBedsOperational` |
| `reserveBed` |
| `releaseReservedBed` |
| `closeInventoryOpening` |

### QueryService：`LocationQueryService`

| 方法 |
|------|
| `listWardsByOrganization` |
| `wardServesOrganization` |
| `listAllByFormCode` |
| `listByOrganizationIdAndFormCode` |
| `listRoomsUnderWard` |
| `listHousesUnderWard` |
| `listActiveBedIdsByNameKeyword` |
| `listActiveBedIdsUnderWard` |
| `mapFormEnumCodeByIds` |
| `findActiveWardsByIds` |
| `findActiveWarehousesByIds` |
| `findActivePharmaciesByIds` |
| `listAllActiveWards` |
| `listBedsUnderParent` |
| `countBedsByWard` |
| `listChildrenByParentBusNo` |
| `listActiveHousesByWardBusNo` |
| `countTotalBedsByWard` |
| `listPharmaciesByOrganization` |
| `listDepartmentPharmaciesByOrganization` |
| `listPharmacyPartyByOrganization` |
| `listWarehousesByOrganization` |
| `listAllActivePharmacies` |
| `listAllActivePharmacyOnlyLocations` |
| `listAllActivePharmacyPartyLocations` |
| `listAllActiveWarehouses` |
| `listAllActiveDepartmentPharmacies` |
| `listAllWarehouseAndPharmacyLocations` |
| `listActiveLocationsByOrganization` |
| `findOptionalById` |
| `requireSupplyRequestTypeForLocationId` |
| `listLocationsByType` |
| `findNameById` |
| `mapNamesByIds` |
| `loadLocationEntityMapByIds` |
| `findLocationNamesByIds` |
| `isInventoryOpeningClosed` |
| `mapInventoryOpeningClosedByIds` |

