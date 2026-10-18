# DM-ADM-006 PractitionerRole 岗位角色

> **限界上下文**：管理域 · 岗位角色管理  
> **聚合类型**：实体聚合  
> **版本**：v1.2.8 · **日期**：2026-08-19

---

## 1. 概述

### 1.1 业务定义

岗位角色（PractitionerRole）表达"谁（Practitioner）在哪个科室/位置（Organization / Location）扮演什么角色、能提供哪些就诊类型、何时可用"的岗位分配关系。它是将医务人员的资质能力与组织结构、服务资源进行关联绑定的核心管理聚合，是排班、门诊号源、权限控制等业务的基础数据来源。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 岗位分配 | 将医务人员分配到指定科室/位置，并赋予特定角色编码（如医师、护士、药师、库管） |
| 就诊类型绑定 | 约束该岗位可提供的挂号/就诊类型（关联 HealthcareService），控制门诊号源可选范围 |
| 出诊排班 | 管理该岗位下的可用时间（排班/出诊时段），支撑号源生成与排班展示 |
| 权限基础 | 为临床系统中的科室权限、药房权限、库房权限提供数据支撑 |
| 多岗位支持 | 同一医务人员可持有多个岗位角色（如某医生既在内科出门诊也在病房管病人） |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊出诊分配 | 医师在指定科室出门诊，绑定普通门诊/专家门诊等就诊类型 |
| 住院病房分配 | 医师/护士分配到特定病区，管理在院患者 |
| 药房人员分配 | 药师分配到指定药房（Location），获得调剂/审方权限 |
| 库房人员分配 | 库管员分配到指定库房（Location），获得出入库操作权限 |
| 护士站分配 | 护士分配到指定护士站（Location），获得病区护理权限 |
| 挂号号源联动 | 「先选医」场景：根据医务人员的岗位分配，联动可挂号科室与就诊类型 |

### 1.4 与 Practitioner / Organization 的关系

| 关联聚合 | 说明 |
|---|---|
| Practitioner（医务人员） | 描述"这个人是谁、有什么资质"，是人员主数据；PractitionerRole 表达"这个人在哪里做什么"，是岗位分配关系。一个 Practitioner 可对应多个 PractitionerRole。 |
| Organization（机构科室） | 描述组织结构（医院、科室）；PractitionerRole 通过科室标识(OrganizationId)关联到科室，表示该人员在此科室承担角色。 |
| Location（位置） | 描述物理位置（护士站、药房、库房等）；PractitionerRole 通过位置标识(LocationId)关联到物理位置，用于护士/药师/库管等非科室级的岗位分配。 |
| HealthcareService（医疗服务） | 描述可提供的就诊类型；PractitionerRole 通过子实体 PractitionerRoleServiceItem 关联多个就诊类型，约束该岗位可挂号的服务范围。 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表

聚合根对应主表，通过子表表达岗位角色下的就诊类型和排班信息。聚合边界以主表主键 `id`（岗位角色标识）划分。

**表结构**：
- 主表：`adm_pr_assign`（主键：`id`）
- 子表1：`adm_pr_assign_service`（岗位服务项，关联主表 `assignment_id`）
- 子表2：`adm_pr_assign_schedule`（岗位排班项，关联主表 `assignment_id`）
- 索引：`practitioner_id`（医务人员）、`role_code`（角色编码）、`active_flag`（状态）、`org_id`（科室）、`location_id`（位置）

**高频查询模式**：
- 按岗位角色标识查询：主表主键查询
- 按医务人员查询：`practitioner_id` 索引扫描
- 按科室查询：`org_id` 索引扫描
- 按角色类型筛选：`role_code` 索引扫描
- 按状态筛选：`active_flag` 索引扫描

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 岗位角色标识 | `PractitionerRoleId` | 全局唯一标识，系统生成（雪花 ID） |

### 2.3 状态机

PractitionerRole 采用启用标记（`activeFlag`，持久化列 `active_flag`）+ 逻辑删除（`isDeleted`，持久化列 `is_deleted`）双字段控制生命周期，而非复杂的多状态流转：

```
[创建] ──启用──> [ACTIVE]
   │               │
   │               ├──停用──> [INACTIVE] ──启用──> [ACTIVE]
   │               │
   │               └──废弃──> [DELETED]
   │
   └──废弃──> [DELETED]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | ACTIVE | 岗位分配有效 | 参与排班/权限/号源计算，允许修改主表属性、停用、废弃；`updateServices` / `updateSchedule` |
| 2 | INACTIVE | 临时停用 | 不参与业务计算但保留记录，允许启用、废弃；可修改主表展示类属性（名称、专业编码、岗位类型等，见 `editRole`）；**不得** `updateServices` / `updateSchedule`（BR-PRL-009） |
| 3 | DELETED | 逻辑删除 | 不参与任何业务计算与展示 |

> **状态说明**：`activeFlag = true` 对应 FHIR R5 `PractitionerRole.active = true`；持久化层 `active_flag = true` / `false`。已废弃通过 `isDeleted` 实现，与平台全局逻辑删除策略一致。

---

## 3. 实体

### 3.1 岗位角色（PractitionerRole）

PractitionerRole 聚合根主实体，对应主表 `adm_pr_assign`，并由 Repository 组装其子实体集合。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 岗位角色标识 | `id` | `PractitionerRoleId` | 是 | 主键，全局唯一 |
| 岗位名称 | `name` | `String` | 否 | 岗位名称（可默认"出诊科室"），便于列表展示 |
| 角色编码 | `role_code` | `String`（语义码，见 §4.1） | 是 | 岗位角色类型 |
| 启用标记 | `active_flag` | `Boolean` | 是 | 是否启用，`true` 表示当前有效，`false` 表示停用 |
| 医务人员标识 | `practitioner_id` | `PractitionerId` | 是 | 关联医务人员聚合根的 ID |
| 科室标识 | `org_id` | `OrganizationId` | 否 | 关联科室/机构的 ID；可空（仅挂角色未配管辖科室）；医师、护士出诊场景通常填写 |
| 位置标识 | `location_id` | `LocationId` | 否 | 关联位置的 ID；可空（仅挂角色未配库房/药房/病区）；药房、库房、护士站等场景使用 |
| 专业编码 | `specialty_code` | `SpecialtyCode`（值对象） | 否 | 医务人员在此岗位下的专业方向；持久层存值对象之「编码」分量（字符串），见 §4.2 |
| 岗位类型编码 | `type_code` | `PractitionerRoleType`（值对象） | 否 | 岗位类型细分标记，见 §4.3 |

> 通用审计字段（`createdAt / updatedAt / createdBy / updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted / deletedAt / deletedBy`）继承自 `BaseAuditedEntity`，各文档不再逐一展开。

### 3.2 岗位服务项（PractitionerRoleServiceItem）

岗位服务项实体对应 `adm_pr_assign_service`，表达该岗位下可提供的就诊类型集合。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 服务项标识 | `id` | `Long` | 是 | 主键 |
| 岗位角色标识 | `assignment_id` | `PractitionerRoleId` | 是 | 关联 `adm_pr_assign.id` |
| 医疗服务标识 | `healthcare_service_id` | `HealthcareServiceId` | 是 | 关联就诊类型（HealthcareService） |

### 3.3 岗位排班项（PractitionerRoleScheduleItem）

岗位排班项实体对应 `adm_pr_assign_schedule`，表达该岗位下可用时段配置。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 排班项标识 | `id` | `Long` | 是 | 主键 |
| 岗位角色标识 | `assignment_id` | `PractitionerRoleId` | 是 | 关联 `adm_pr_assign.id` |
| 可用时间 | `availability_json` | `String` | 是 | 排班/可用时间 JSON，默认 `'{}'` |

---

## 4. 值对象

### 4.1 角色编码（RoleCode）

标识岗位角色类型，是岗位分配的核心属性之一。领域枚举 **`PractitionerBusinessRoleCode`**（`zxplat-domain`）与下表语义码一一对应。

| 语义码（持久化值） | 中文 | 说明 |
| --- | --- | --- |
| `DOCTOR` | 医师 | 临床医师（含中医、西医、口腔等），可出门诊和管理病房 |
| `NURSE` | 护士 | 护理人员，关联护士站/病区 |
| `PHARMACIST` | 药师 | 药学人员，关联药房/药库，负责调剂与审方 |
| `WAREHOUSE_ADMIN` | 库管 | 库房管理人员，关联库房位置 |
| `TECHNICIAN` | 技师 | 检验/检查/影像等医技人员；工作科室仅机构分类 `TECH` |
| `ADMINISTRATIVE` | 行政 | 行政管理人员（挂号员、收费员等） |

> 对应 FHIR R5 `PractitionerRole.code`，采用本地扩展值集。  
> **存储口径**：主表字段 `role_code` 存上表**字符串语义码**（`varchar`）；Flyway **V87** 已将历史整型 `1..6` 迁移为语义码。  
> **API 口径**：岗位分配读写一律使用平台角色主键 `roleId`（`whale_roles.id`）与选项 `code`（如 `ROLE_DOCTOR`）；**禁止**再传/再出数值 `1..6`。应用层已删除 `PositionRoleApiCompat` 数值桥。

### 4.2 专业编码（SpecialtyCode）

表达医务人员在此岗位下的专业方向，用于门诊号源分类与专科调度。

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 编码 | `String` | 专业编码值（如 `internal-medicine`、`surgery` 等），与主表 `specialty_code` 列一致 |
| 名称 | `String` | 专业中文名称（可由码表或应用层根据编码解析） |

> 对应 FHIR R5 `PractitionerRole.specialty`。值集可对接国家卫健委执业范围编码或院内自定义专业编码体系。
> **存储口径**：对应主表字段 `specialty_code`，使用字符串存储。

### 4.3 岗位类型（PractitionerRoleType）

对角色编码的进一步细分标记，用于区分同一角色下的不同职能。

| 编码值 | 英文 | 说明 |
| --- | --- | --- |
| 1 | `chief-physician` | 主任医师：高级职称，用于专家门诊号源 |
| 2 | `associate-chief-physician` | 副主任医师：副高级职称 |
| 3 | `attending-physician` | 主治医师：中级职称 |
| 4 | `resident-physician` | 住院医师：初级职称 |
| 5 | `visiting-physician` | 进修医师：进修人员，权限受限 |
| 0 | `general` | 通用：未细分类型，默认值 |

> **存储口径**：对应主表字段 `type_code`，使用字符串编码存储。

### 4.4 可用时间（AvailabilitySchedule）

表达岗位的排班/出诊时间，以 JSON 结构存储于持久层，领域层作为值对象使用。

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 星期几 | `List<DayOfWeek>` | 可用的星期（周一至周日） |
| 开始时间 | `LocalTime` | 当日出诊开始时间 |
| 结束时间 | `LocalTime` | 当日出诊结束时间 |
| 时段标记 | `String` | 上午/下午/全天等时段标记 |
| 是否全天可用 | `Boolean` | 是否全天出诊 |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
| --- | --- | --- | --- | --- |
| 岗位角色已创建 | `PractitionerRoleCreatedEvent` | 岗位角色创建成功 | 岗位角色标识, 医务人员标识, 科室标识, 角色编码 | QueryService/缓存 |
| 岗位角色已修改 | `PractitionerRoleEditedEvent` | 岗位角色属性修改成功 | 岗位角色标识, 变更字段摘要 | QueryService/缓存 |
| 岗位服务集合已更新 | `PractitionerRoleServiceUpdatedEvent` | 岗位服务项集合更新成功 | 岗位角色标识, 医疗服务标识列表 | QueryService/缓存 |
| 岗位排班已更新 | `PractitionerRoleScheduleUpdatedEvent` | 岗位排班集合更新成功 | 岗位角色标识, 可用时间摘要 | 排班/号源 |
| 岗位角色已启用 | `PractitionerRoleActivatedEvent` | 岗位角色从停用恢复为启用 | 岗位角色标识, 医务人员标识, 科室标识 | 排班/号源 |
| 岗位角色已停用 | `PractitionerRoleDeactivatedEvent` | 岗位角色被停用 | 岗位角色标识, 停用原因 | 排班/号源（关闭号源） |
| 岗位角色已废弃 | `PractitionerRoleAbolishedEvent` | 岗位角色被逻辑删除 | 岗位角色标识, 医务人员标识 | QueryService/缓存 |
| 岗位批量已替换 | `PractitionerRoleBatchReplacedEvent` | 按人员+角色类型全量替换岗位集合 | 医务人员标识, 角色编码, 替换后岗位ID列表 | QueryService/缓存 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
| --- | --- | --- | --- |
| — | 创建岗位角色 | ACTIVE | `PractitionerRoleCreatedEvent` |
| ACTIVE | 修改岗位属性 | ACTIVE | `PractitionerRoleEditedEvent` |
| ACTIVE | 更新就诊类型集合 | ACTIVE | `PractitionerRoleServiceUpdatedEvent` |
| ACTIVE | 更新排班信息 | ACTIVE | `PractitionerRoleScheduleUpdatedEvent` |
| ACTIVE | 停用 | INACTIVE | `PractitionerRoleDeactivatedEvent` |
| INACTIVE | 启用 | ACTIVE | `PractitionerRoleActivatedEvent` |
| ACTIVE/INACTIVE | 废弃 | DELETED | `PractitionerRoleAbolishedEvent` |
| — | 按人员+角色类型全量替换 | — | `PractitionerRoleBatchReplacedEvent` |

> **全量替换与单条创建的事件策略**：`replaceRolesByType` 对旧集合进行清理并批量新建时，**不**逐条发布 `PractitionerRoleCreatedEvent` / `PractitionerRoleAbolishedEvent`，仅以 `PractitionerRoleBatchReplacedEvent` 汇总通知集成方，避免订阅方重复处理；单条 `assignRole` / `retireRole` 仍发布对应单条事件。

---

## 6. 领域服务

### 6.1 岗位角色领域服务（PractitionerRoleDomainService）

#### 分配岗位（`assignRole`）

- **业务意义**：为医务人员建立一条新的岗位角色分配（聚合根 + 可选的服务项、排班项）。
- **边界说明**：仅创建本聚合内数据；医务人员/科室/位置/医疗服务的存在性校验可依赖上游或仓储查询，不在此编排跨聚合业务流程。
- **前置条件**：`PractitionerId` 有效（BR-PRL-008）；科室与位置允许双空（BR-PRL-007）；`PractitionerId + RoleCode + OrganizationId + LocationId` 唯一（BR-PRL-001）；若传入服务项则各 `HealthcareServiceId` 有效（BR-PRL-002）且无重复（BR-PRL-010）；若传入排班 JSON 则满足 BR-PRL-003。
- **状态变化**：`N/A`（新建记录默认为 ACTIVE，`activeFlag = true`，`isDeleted = false`）。
- **聚合内影响**：插入 `adm_pr_assign`；按入参插入 `adm_pr_assign_service`、`adm_pr_assign_schedule`（可为空集合）。
- **事件输出**：`PractitionerRoleCreatedEvent`。
- **失败语义**：医务人员无效；岗位分配重复；医疗服务无效或重复；排班数据不合法；事务失败时整体回滚。

#### 编辑岗位（`editRole`）

- **业务意义**：维护聚合根主表展示与分类属性（如岗位名称、专业编码、岗位类型、科室/位置等），**不**修改服务项与排班项（由 `updateServices` / `updateSchedule` 负责）。
- **边界说明**：仅更新主表字段；不改变启用/停用/废弃状态（由 `activateRole` / `deactivateRole` / `retireRole` 负责）。
- **前置条件**：岗位存在且未废弃；修改后的 `OrganizationId` / `LocationId` 仍满足 BR-PRL-007（允许双空）；若调整科室/位置/角色相关维度，组合仍满足 BR-PRL-001。
- **状态变化**：`N/A`（仍为 ACTIVE 或 INACTIVE 之一，不通过本方法切换）。
- **聚合内影响**：更新 `adm_pr_assign` 中允许编辑的列；**不**触碰子表。
- **事件输出**：`PractitionerRoleEditedEvent`。
- **失败语义**：岗位不存在或已废弃；违反 BR-PRL-001；无权修改。

#### 启用岗位（`activateRole`）

- **业务意义**：将已停用岗位恢复为可参与业务计算的状态。
- **边界说明**：仅切换 `active_flag`，不修改服务/排班内容。
- **前置条件**：当前为 INACTIVE 且未废弃。
- **状态变化**：`active_flag: false -> true`（INACTIVE -> ACTIVE）。
- **聚合内影响**：更新 `adm_pr_assign.active_flag`。
- **事件输出**：`PractitionerRoleActivatedEvent`。
- **失败语义**：岗位不存在、已废弃或当前非 INACTIVE 状态。

#### 停用岗位（`deactivateRole`）

- **业务意义**：使岗位退出排班/号源/权限等计算，保留数据以便再次启用。
- **边界说明**：不删除子表数据；不自动清理下游号源（由订阅方处理 `PractitionerRoleDeactivatedEvent`）。
- **前置条件**：当前为 ACTIVE 且未废弃。
- **状态变化**：`active_flag: true -> false`（ACTIVE -> INACTIVE）。
- **聚合内影响**：更新 `adm_pr_assign.active_flag`。
- **事件输出**：`PractitionerRoleDeactivatedEvent`。
- **失败语义**：岗位不存在、已废弃或当前非 ACTIVE 状态。

#### 废弃岗位（`retireRole`）

- **业务意义**：对单条岗位执行逻辑删除，保留审计痕迹，不再参与任何查询与计算。
- **边界说明**：非物理删除；全量替换场景下的清理见 `replaceRolesByType`（BR-PRL-005）。
- **前置条件**：岗位存在且未废弃。
- **状态变化**：进入 DELETED（`isDeleted = true`；`activeFlag` 按仓储约定落库，不参与业务判断）。
- **聚合内影响**：逻辑删除主表记录（子表随仓储策略级联或保留由实现约定，对外以「岗位已废弃」为准）。
- **事件输出**：`PractitionerRoleAbolishedEvent`。
- **失败语义**：岗位不存在或已废弃。

#### 更新就诊类型（`updateServices`）

- **业务意义**：全量替换该岗位下的就诊类型（服务项）集合。
- **边界说明**：仅维护 `adm_pr_assign_service`；不修改排班。
- **前置条件**：岗位为 ACTIVE 且未废弃（BR-PRL-009）；各 `HealthcareServiceId` 有效（BR-PRL-002）；无重复（BR-PRL-010）。
- **状态变化**：`N/A`。
- **聚合内影响**：删除（或等价替换）该 `assignment_id` 下原有服务行，写入新集合。
- **事件输出**：`PractitionerRoleServiceUpdatedEvent`。
- **失败语义**：岗位不存在、已废弃或已停用；医疗服务无效或重复。

#### 更新排班信息（`updateSchedule`）

- **业务意义**：全量替换该岗位下的排班项集合（可用时间 JSON）。
- **边界说明**：仅维护 `adm_pr_assign_schedule`；不修改服务项。
- **前置条件**：岗位已启用且未废弃（BR-PRL-009）；`AvailabilitySchedule` / JSON 满足 BR-PRL-003。
- **状态变化**：`N/A`。
- **聚合内影响**：删除（或等价替换）该 `assignment_id` 下原有排班行，写入新集合。
- **事件输出**：`PractitionerRoleScheduleUpdatedEvent`。
- **失败语义**：岗位不存在、已废弃或已停用；排班数据不合法。

#### 全量替换岗位（`replaceRolesByType`）

- **业务意义**：在同一事务内，按 `practitionerId + roleCode` 清空旧岗位集合并按入参批量新建，使库中集合与入参一致。
- **边界说明**：先对旧集合执行逻辑删除/等价替换清理，再插入新聚合；**不**逐条发布单条创建/废弃事件（见 §5.2 说明）。
- **前置条件**：入参内每条待创建记录均满足 `assignRole` 同等校验（BR-PRL-007、BR-PRL-001、BR-PRL-002、BR-PRL-003、BR-PRL-010 等）。
- **状态变化**：`N/A`（对调用方表现为集合被替换）。
- **聚合内影响**：清理匹配 `practitionerId + roleCode` 的旧 `adm_pr_assign` 及关联子表行（以逻辑删除/等价替换实现）；批量插入新主表与子表。
- **事件输出**：`PractitionerRoleBatchReplacedEvent`（载荷含替换后岗位 ID 列表）。
- **失败语义**：任一新记录校验失败则整批回滚；与 BR-PRL-005 冲突时以本方法「替换后完全一致」为准。

### 6.2 读侧查询服务（PractitionerRoleQueryService）

#### 按人员查岗位列表（`listAssignmentsByPractitioner`）

- **业务意义**：按医务人员返回其当前参与业务计算的岗位分配，用于挂号「先选医」、权限回显、**管理病区/管辖库位**解析等。
- **边界说明**：只读查询，不写库；**不在此方法内**解析 `location_id` 对应 Location 的 `form_enum`（形态过滤由应用层 **`CurrentManagedWardLocationIdsResolver`** / **`CurrentManagedLocationIdsResolver`** + **DM-ADM-003** 单聚合读完成）。
- **查询范围（读库，无写入）**：以 `practitioner_id` 为主过滤条件，读取 `adm_pr_assign` 及关联子表。
- **过滤口径**：`isDeleted = false`（BR-PRL-004）；`activeFlag = true`；当前租户（若有）。读 SQL 映射为 `is_deleted = 0`（或等价）、`active_flag = true`。
- **组装规则**：主表一行对应一个 `PractitionerRoleId`；返回 `org_id`、`location_id`、服务项列表等；`location_id` 可指向病区（`WARD`）、药房、药库等任意形态，下游按场景过滤。
- **下游消费（位置收窄）**：
  - **住院护士站**（**AS-IP-NUR-001**）：`location_id` → **DM-ADM-003** 保留 `WARD`+`ACTIVE` 作为管理病区；无有效病区时 Location 读侧回退全部启用病区。
  - **库房进货/出库**（应用层）：`location_id` 与机构下药房/药库列表求交，无交时不误收窄为空类（见库存应用服务实现）。
- **返回约束**：无匹配返回空列表；不抛「未找到」业务异常（除非产品约定按错误码返回）。

#### 按人员查科室列表（`listOrganizationIdsByPractitioner`）

- **业务意义**：汇总某医务人员可挂号的科室标识去重集合，用于科室下拉。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：基于岗位主表 `org_id`。
- **过滤口径**：同 `listAssignmentsByPractitioner`；仅保留 `org_id` 非空的记录。
- **组装规则**：对 `org_id` 去重、可排序（如按科室编码顺序由应用层决定）。
- **返回约束**：无岗位或无科室时返回空集合。

#### 按人员+科室查就诊类型（`listHealthcareServiceIdsByPractitionerAndOrg`）

- **业务意义**：在指定人员与科室下，返回可选的就诊类型（医疗服务）标识列表。
- **边界说明**：只读查询，不写库；不校验挂号业务规则（如号源余量）。
- **查询范围（读库，无写入）**：先定位该人员在该科室下的启用岗位，再读 `adm_pr_assign_service`。
- **过滤口径**：岗位层同上述过滤；`organization_id` 与入参科室匹配。
- **组装规则**：合并多岗位下的 `healthcare_service_id` 并去重。
- **返回约束**：无匹配返回空列表。

#### 解析数据管辖范围（`resolveManagedScope`）

- **业务意义**：从激活且未废弃的 `adm_pr_assign` 汇总医务人员的数据管辖范围；经 **`module.shared.identity.HealthPractitionerManagedScopeResolver`** 写入 whale 登录会话；应用层经 **五类 Resolver**（**B.6.5**）消费，**禁止** AppService/Loader 直读会话 Bean。
- **边界说明**：只读派生，不写库；**不在此方法内**校验 Location 形态。
- **派生规则（按 `role_code` 语义码）**：
  - `WAREHOUSE_ADMIN` / `PHARMACIST`：`location_id` → **`managedLocationIds`（可操作库房：药库 + 药房，均为 `adm_location.id`）**
  - `NURSE`：`location_id` → **管辖病区**（与工作科室无关联）；`org_id` 为工作科室（**写入时**仅 `NS`）
  - `DOCTOR` / `ADMINISTRATIVE`：`org_id` → **管辖科室**（医师出诊科室）
  - `TECHNICIAN`：`org_id` 为工作科室（**写入时**仅 `TECH`）；不写病区。读侧与其它岗位一样，凡 `org_id` 非空即进入管辖科室与登录候选
- **出参**：`PractitionerManagedScopeQueryModel`（`managedLocationIds` / `managedOrganizationUnitIds` / `managedWardLocationIds`）。

#### 汇总可登录科室（`collectLoginOrganizationUnitIds`）

- **业务意义**：从岗位 `org_id` 汇总登录可选科室（医师出诊科室、护士/医技工作科室），应用层可再并入 `Practitioner.orgId`（默认登录科室）后同步至身份域 `UserOrganizationUnit`。登录时在这些科室中选一个进入会话；**选中的科室即当前执行归属**，后续医技执行与工作量统计记到该科，不把人员默认归属当成唯一护士站。
- **工作科室候选**：由已选平台角色决定，与管理病区无关。只有 `ROLE_NURSE` 时只能选护士站（`class_enum=NS`）；只有 `ROLE_MEDICAL_TECH`（界面「技师」）时只能选医技科室（`class_enum=TECH`）；护士+技师双角色为两类并集。保存时按角色拆到各自 `adm_pr_assign` 行。平台角色声明码为 `StaffScope=WORK_ORG`（历史 `NURSE_ORG` 已废弃，由 Flyway 迁到新码）。
- **医生+技师（或医生+护士）**：人员维护同时出现「出诊科室」与「工作科室」（及护士时的「管理病区」）；两块独立维护。出诊科室挂医师岗，工作科室挂护士/技师岗。**登录页选科** = 两块岗位 `org_id` 去重并集（出诊科 ∪ 工作科）；未维护岗位且无默认登录科室时不出现选科（医院级）。选中的科室仍是当次会话执行归属。
- **边界说明**：只读；同步写库由 **身份域** `OrganizationUnitAppService#syncOrganizationUnitsForUser` 编排。

#### 按平台角色解析医务人员主键（`listPractitionerIdsWithActiveRoleId`）

- **业务意义**：返回拥有指定平台角色且岗位仍激活的医务人员主键集合；供 **`PractitionerRoleScopeLoader`**、挂号/发药/库存初始化，以及 **用户管理按出诊科室检索**（`ROLE_DOCTOR` + `org_id`）等跨聚合读编排使用。
- **入参**：`roleId`（`whale_roles.id`）；`orgId` 可选 — 非空时按 `adm_pr_assign.org_id` 收窄（「在本机构挂了该岗就算」）。
- **出参**：`Set<Long>`；`roleId` 为空或无命中返回空集合。
- **边界说明**：只读；**禁止**被 `PractitionerQueryService` 注入调用（跨聚合读编排唯一落点 **`application.shared.organization`**，**B.6.1.3.1**）。

#### 按平台角色与机构集合解析医务人员主键（`listPractitionerIdsWithActiveRoleIdInOrganizations`）

- **业务意义**：返回在**给定机构集合内**拥有指定平台角色且岗位仍激活的医务人员主键集合；供门诊挂号在未选科室时限定「全部门诊科室范围内有医师岗」。
- **入参**：`roleId`（`whale_roles.id`）；`orgIds` 非空机构主键集合（典型来源：`OrganizationQueryService#listOrganizationsForOutpatientRegistration`）。
- **出参**：`Set<Long>`；`roleId` 为空或 `orgIds` 为空时返回空集合。
- **边界说明**：只读；**禁止** `orgId` 为空时退化为全库 `role_id` 查询（否则会带出仅在住院科室挂岗的医师）。

### 6.3 领域边界说明

- 本聚合维护「岗位分配」主数据及子表（就诊类型绑定、排班可用性），不在本聚合内执行挂号、号源生成、权限鉴权、排班引擎等业务执行。
- Practitioner、Organization、Location、HealthcareService 等被引用聚合的完整性由各自领域或应用层编排保证；本聚合仅校验岗位层不变式（BR-PRL-*）。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| BR-PRL-001 | 同租户下 `医务人员标识(PractitionerId) + 角色编码(RoleCode) + 科室标识(OrganizationId) + 位置标识(LocationId)` 组合唯一 | 防止同一人在同一科室/位置下重复分配相同角色 |
| BR-PRL-002 | 就诊类型项中 `医疗服务标识(HealthcareServiceId)` 必须指向有效的 HealthcareService 聚合实例 | 保证就诊类型引用完整性，写入前校验或由应用服务保证 |
| BR-PRL-003 | 排班可用性项中 `AvailabilitySchedule` 必须符合系统约定的 JSON Schema（星期、时段、开始/结束时间不为空且开始 < 结束） | 保证排班数据合法性 |
| BR-PRL-004 | 已废弃（`isDeleted = true`）的岗位角色不参与任何业务计算与展示 | 查询默认过滤 `isDeleted = false` |
| BR-PRL-005 | 单条废弃走逻辑删除（`isDeleted = true`）；按人员+角色类型全量替换前清理走逻辑删除/等价替换 | 替换场景需保证替换后岗位集合与入参完全一致 |
| BR-PRL-006 | 创建/修改时聚合根主表与子表（就诊类型项、排班可用性项）在同一事务内写入 | Repository 负责组装/拆解三表（`adm_pr_assign`/`adm_pr_assign_service`/`adm_pr_assign_schedule`），保证聚合一致性 |
| BR-PRL-007 | 科室标识(OrganizationId)与位置标识(LocationId)允许同时为空 | 挂平台角色即可写入岗位行；管辖科室/库位可后续再配，双空表示仅角色挂靠 |
| BR-PRL-008 | 岗位角色的医务人员标识(PractitionerId)必须指向有效的 Practitioner 聚合实例 | 保证人员引用完整性 |
| BR-PRL-009 | 已停用的岗位角色不允许修改就诊类型集合和排班信息 | 需先启用后再调用 `updateServices` / `updateSchedule`；`editRole` 仍允许改主表展示类字段 |
| BR-PRL-010 | 同一岗位角色下就诊类型项不允许出现重复的 `医疗服务标识(HealthcareServiceId)` | 防止同一就诊类型被重复绑定 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
| --- | --- | --- | --- |
| 人员管理 | Practitioner | `医务人员标识(PractitionerId)` | 岗位角色关联的医务人员，创建时校验有效性 |
| 组织架构 | Organization | `科室标识(OrganizationId)` | 岗位角色所属科室，医师/护士出诊场景必填 |
| 位置管理 | Location | `位置标识(LocationId)` | 岗位角色关联的物理位置（药房、库房、护士站） |
| 医疗服务 | HealthcareService | `医疗服务标识(HealthcareServiceId)` | 岗位可提供的就诊类型，通过子实体 1:N 关联 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
| --- | --- | --- | --- |
| 门诊挂号 | Encounter | 查询依赖 | 挂号时根据岗位角色查询可选科室与就诊类型（号源联动） |
| 排班管理 | Schedule / Slot | 事件订阅 | 监听 `PractitionerRoleScheduleUpdatedEvent`，刷新号源时段 |
| 药品管理 | MedicationDispense | 查询依赖 | 调剂/发药时根据药师岗位确认操作权限 |
| 医嘱管理 | MedicationRequest / ServiceRequest | 查询依赖 | 开立医嘱时根据医师岗位确认出诊科室归属 |
| QueryService/缓存 | — | 事件订阅 | 监听所有岗位变更事件，刷新岗位列表/权限缓存 |
| 身份域 · 登录上下文 | User / OrganizationUnit | 查询依赖 + 应用编排 | `resolveManagedScope` → `HealthPractitionerManagedScopeResolver`（`module.shared.identity`）填充会话；App 经 **Resolver** 读取；`collectLoginOrganizationUnitIds` → 同步 `whale_user_organization_units` |
| 身份域 · 用户管理 | User | 查询依赖 | 列表 `organizationId`：经 Port 调 `listPractitionerIdsWithActiveRoleId(ROLE_DOCTOR, orgId)` 再映射 `user_id`，跨科出诊医生均可命中 |
| 位置管理 | Location（读侧） | 查询依赖 | `listAssignmentsByPractitioner` 提供 `location_id`；WARD 形态过滤由 **`CurrentManagedWardLocationIdsResolver`** 经 **DM-ADM-003** `findAllById…` 完成 |
| 库存 · 药库/药房 | —（库存/发药 AppService） | 查询依赖 | `CurrentManagedLocationIdsResolver`（`managedLocationIds` = 药库+药房族 location id，含 **`DEPARTMENT_PHARMACY`**） |
| 住院护理 · 护士站 | —（**AS-IP-NUR-001**） | 查询依赖 | **`CurrentManagedWardLocationIdsResolver`**（初始化地理树与队列 Gate 同源） |

### 8.3 引用规则

> **PractitionerRole 引用规则**：
>
> - 岗位角色通过 `医务人员标识(PractitionerId)` 关联人员，不直接持有 Practitioner 对象引用
> - 岗位角色通过 `科室标识(OrganizationId)` 关联科室，通过 `位置标识(LocationId)` 关联位置，两者至少填写其一
> - 就诊类型通过子实体 PractitionerRoleServiceItem 的 `医疗服务标识(HealthcareServiceId)` 关联 HealthcareService
> - 下游上下文引用岗位角色时统一使用 `岗位角色标识(PractitionerRoleId)`

### 8.4 事件流示意图

```
Practitioner         PractitionerRole                    Schedule/Slot         Encounter（挂号）
    │                      │                                 │                      │
    │                      │── PractitionerRoleCreatedEvent ─>│                      │
    │                      │                                 │── 刷新号源            │
    │                      │                                 │                      │
    │                      │── PractitionerRoleSchedule      │                      │
    │                      │   UpdatedEvent ────────────────>│── 更新时段            │
    │                      │                                 │                      │
    │                      │── PractitionerRoleDeactivated   │                      │
    │                      │   Event ───────────────────────>│── 关闭号源            │
    │                      │                                 │                      │
    │                      │<─────────────── 查询岗位分配 ──────────────────────────│
    │                      │── 返回可选科室+就诊类型 ──────────────────────────────>│
    │                      │                                 │                      │
```

---

## 9. 补充

### 9.1 全量替换机制说明

HIS 系统中岗位分配存在"全量替换"场景——管理员调整某角色类型下的全部岗位分配时（如护士关联科室、药师关联药房），前端提交完整的岗位集合，后端需先清除旧集合再创建新集合：

1. **全量替换**（`replaceRolesByType`）：按 `practitionerId + roleCode` 查询并清理旧岗位集合（主表+子表，逻辑删除/等价替换实现），再批量创建新集合
2. **单条废弃**（`retireRole`）：对单个岗位角色执行**逻辑删除**（`isDeleted = true`），保留历史与审计

两种删除口径的设计目的是：全量替换后岗位集合与提交内容完全一致（无历史残留），而单条废弃保留审计痕迹。

### 9.2 与排班/号源的协作关系

| 职责 | PractitionerRole | Schedule / Slot |
| --- | --- | --- |
| 出诊时间定义 | 管理岗位级的可用时间（`AvailabilitySchedule`） | 管理具体日期的号源时段与余量 |
| 号源生成 | 提供可出诊的时间模板 | 根据模板 + 日历规则生成具体号源 |
| 停诊处理 | 停用岗位角色 → 触发事件 | 监听事件 → 关闭对应号源 |
| 加号 | — | 在 Slot 层面扩展号源数量 |

### 9.3 读侧查询索引

读侧方法的完整定义（边界、过滤、组装、返回约束）见 **§6.2**。下表仅作方法名与场景索引：

| 方法 | 典型场景 |
| --- | --- |
| `listAssignmentsByPractitioner` | 挂号「先选医」、岗位列表；护士站/库房 **location_id** 收窄 |
| `listOrganizationIdsByPractitioner` | 科室下拉（去重） |
| `listHealthcareServiceIdsByPractitionerAndOrg` | 就诊类型下拉 |
| `resolveManagedScope` | 登录用户数据管辖；库存/发药/护士站 App 经 **Resolver** 收窄 |
| `collectLoginOrganizationUnitIds` | 岗位保存后同步可登录科室 |
| `listPractitionerIdsWithActiveRoleId` | 跨聚合读预解析（`PractitionerRoleScopeLoader` → `PractitionerQueryService` IN 过滤） |
| `listPractitionerIdsWithActiveRoleIdInOrganizations` | 门诊挂号未选科室：在全部门诊科室 id 集合内批量预解析医师岗 |

### 9.4 与身份域协同（平台能力，非本聚合表）

以下能力由 **zxplat-framework / whale-menu / whale-permission** 实现，本聚合仅提供 `adm_pr_assign` 事实与读侧派生：

| 能力 | 平台落点 | 与本聚合关系 |
| --- | --- | --- |
| 人员维护页可编辑块 | 平台角色 `whale_role_claims`（`claim_type=StaffScope`） | 应用层 `PositionAppService` 按 StaffScope 决定六类岗位块是否可写；写前经 **`PositionPractitionerRoleWriteGate`**（`module.common.validator.position`；医务人员/就诊类型存在性，**B.6.1.3.1**）校验 |
| 角色 ↔ 菜单 | `whale_role_menus`（V87） | 与岗位无关；平台 RBAC 菜单授权 |
| 登录科室 | `whale_user_organization_units`（存 **ZXPlat OU id**）+ 会话（存 **HIS org id**） | 岗位保存 → `collectLoginOrganizationUnitIds`（出诊科室 + 工作科室的 HIS org）→ `IOrganizationUnitScopeBridge` → `syncOrganizationUnitsForUser`；会话选中科室 = 当前执行归属 |
| 运行时 App 消费 | `module.common.validator.*Resolver` | **禁止** AppService/Loader 注入 `ICurrentUser`；见 [Identity与HIS组织轻关联说明](../../04-架构设计/Identity与HIS组织轻关联说明.md) §7、**B.6.5** |

### 9.6 应用层 Resolver 映射（非本聚合实现，成码必读）

| Resolver | 消费的 `resolveManagedScope` 字段 | 主键空间 |
| --- | --- | --- |
| `CurrentManagedOrganizationIdsResolver` | 会话科室 + `managedOrganizationUnitIds` | `adm_organization.id` |
| `CurrentManagedLocationIdsResolver` | `managedLocationIds` | `adm_location.id`（药库 `WAREHOUSE` + 药房 `PHARMACY` + 科室药房 `DEPARTMENT_PHARMACY`） |
| `CurrentManagedWardLocationIdsResolver` | `managedWardLocationIds` | `adm_location.id`（`WARD`） |
| `CurrentOperatorResolver` | —（`whale_users.id`） | 登录用户 |
| `CurrentPractitionerResolver` | —（JWT 绑定 `adm_practitioner.id`） | 医务人员 |

### 9.5 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2.10 | 2026-09-19 | **工作科室声明码** `NURSE_ORG`→`WORK_ORG`；内置角色显示名「医技」→「技师」 |
| v1.2.9 | 2026-09-19 | **工作科室**：`WORK_ORG`（当时仍记为 `NURSE_ORG`）界面称工作科室；护士仅 `NS`、技师仅 `TECH`、双角色并集；与管理病区无关；登录选中科室为执行归属 |
| v1.2.8 | 2026-08-19 | 用户管理按出诊科室检索：复用 `listPractitionerIdsWithActiveRoleId(ROLE_DOCTOR, orgId)` |
| v1.0 | — | 首版 |
| v1.1 | 2026-05-20 | 明确 `location_id` 下游消费：护士站管理病区（**DM-ADM-003**）、库房位置收窄 |
| v1.1.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `PractitionerRoleDomainService`/`PractitionerRoleQueryService` 白名单对齐（8写+5读） |
| v1.2.5 | 2026-07-02 | **`PractitionerManagedScopeLoader`** 显式将 **`DEPARTMENT_PHARMACY`** 计入 `managedLocationIds`；§9.6 药库+药房族口径含科室药房 |
| v1.2.6 | 2026-07-27 | **BR-PRL-007** 放宽：`org_id`/`location_id` 允许双空；挂平台角色即可写入 `adm_pr_assign`（仅角色挂靠） |
| v1.2.7 | 2026-07-27 | 用户维护增删改角色与岗位双向对齐：`adm_pr_assign` ↔ `whale_user_roles`；人员注销级联废弃岗位 |
| v1.2.4 | 2026-06-10 | §6.2 新增 `listPractitionerIdsWithActiveRoleIdInOrganizations`；门诊挂号未选科室时限定门诊科室集合，排除仅住院科室挂岗医师 |
| v1.2.3 | 2026-06-09 | §6.2 新增 `listPractitionerIdsWithActiveRoleId`；供 **`PractitionerRoleScopeLoader`** 预解析（**B.6.1.3.1**）；库存人员下拉口径：**`adm_pr_assign.org_id` 挂了该岗就算** |
| v1.2.2 | 2026-06-03 | §9.6 应用层五类 Resolver；`managedLocationIds`=药库+药房；与 B.6.5 对齐 |
| v1.2.1 | 2026-06-03 | §9.4 登录科室 id 语义（user_ou=ZXPlat OU，会话/managedScope=HIS org） |
| v1.2.0 | 2026-06-03 | resolveManagedScope / collectLoginOrganizationUnitIds；StaffScope |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PractitionerRoleDomainService`

| 方法 |
|------|
| `assignRole` |
| `editRole` |
| `activateRole` |
| `deactivateRole` |
| `retireRole` |
| `updateServices` |
| `updateSchedule` |
| `replaceRolesByType` |
| `ensureActiveRoleAssignmentIfAbsent` |
| `retireAssignmentsOutsideRoleIds` |
| `retireAllAssignmentsByPractitioner` |

### QueryService：`PractitionerRoleQueryService`

| 方法 |
|------|
| `listAssignmentsByPractitioner` |
| `listPractitionerIdsWithActiveRoleId` |
| `listPractitionerIdsWithActiveRoleIdAtLocation` |
| `listPractitionerIdsWithActiveRoleIdInOrganizations` |
| `listPractitionerIdsWithActiveRoleIdInLocations` |
| `hasActiveAssignmentForRole` |
| `findRoleIdByAssignmentId` |
| `listOrganizationIdsByPractitioner` |
| `listHealthcareServiceIdsByPractitionerAndOrg` |
| `listHealthcareServiceIdsByPractitioner` |
| `listAllRolesByPractitioner` |
| `collectLoginOrganizationUnitIds` |
| `findPractitionerIdByAssignmentId` |
| `listScheduleItemsByAssignmentIds` |

