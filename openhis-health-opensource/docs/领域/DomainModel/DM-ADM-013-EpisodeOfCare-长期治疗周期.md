# DM-ADM-013 EpisodeOfCare 长期治疗周期

> **限界上下文**：患者管理 · 长期治疗管理\
> **聚合类型**：实体聚合\
> **版本**：v1.0.1 · **日期**：2026-05-28

## 1. 概述

### 1.1 业务定义

长期治疗周期（EpisodeOfCare）是患者在一段时间内接受**连续治疗管理的聚合**，串联多次就诊（Encounter）以表达慢性病管理、康复治疗、随访管理等长期治疗场景。EpisodeOfCare 作为长期治疗总线的聚合根，为跨域协作提供稳定的 `治疗周期标识(EpisodeOfCareId)`。

### 1.2 核心职责

| 职责       | 说明                              |
| -------- | ------------------------------- |
| 长期治疗管理   | 表达患者的长期治疗周期，如慢性病管理、康复治疗等 |
| 就诊总线串联  | 串联长期治疗期间多次 Encounter，提供治疗周期视角的业务查询 |
| 治疗团队管理   | 管理长期治疗期间的治疗团队和责任医生               |
| 治疗目标跟踪   | 记录和跟踪长期治疗目标和进展                   |
| 跨域关联锚点   | 为长期治疗相关的费用、医嘱等提供稳定的治疗周期标识        |

### 1.3 适用范围

| 类型         | 说明                      |
| ---------- | ----------------------- |
| 慢性病管理     | 糖尿病、高血压等慢性病长期随访管理，串联多次门诊就诊      |
| 康复治疗      | 术后康复、功能恢复等连续治疗周期                |
| 随访管理      | 出院后随访、复查等连续性健康管理                |
| 多次就诊串联   | 同一疾病多次门诊或住院就诊的聚合管理               |

### 1.4 与 Encounter 的关系

EpisodeOfCare（长期治疗周期）与 Encounter（就诊）的职责划分：

| 维度    | Encounter           | EpisodeOfCare        |
| ----- | ------------------- | -------------------- |
| 管理范围  | 单次就诊周期（挂号→诊毕）       | 长期治疗管理（多次就诊串联）       |
| 门诊场景  | 完整覆盖                | 可参与，串联多次门诊就诊           |
| 住院场景  | 完整覆盖，包含住院全流程       | 可参与，串联多次住院或门诊就诊        |
| 时间跨度  | 短期（单次就诊）           | 长期（数月到数年）             |
| 业务视角  | 单次诊疗交互               | 治疗周期视角                 |

> **边界规则**：单次就诊只引用 Encounter；需要长期治疗管理视角的场景（如慢性病随访、康复治疗）同时引用 Encounter 和 EpisodeOfCare。Encounter 可以表达住院场景；本聚合表达**跨多次就诊的治疗管理周期**，不替代单次就诊内的住院流程编排。

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表+子表

聚合根对应主表，子实体对应子表，通过外键 `episode_of_care_id` 关联。聚合边界以主表主键 `id`（**治疗周期标识**）划分，主表一行对应子表 N 行（0 ≤ N）。

**表结构**：

- 主表：`adm_episode_of_care`（主键：`id`）
- 子表1：`adm_episode_of_care_encounter`（外键：`episode_of_care_id`）
- 关系：1:N，子表行数不限，可为 0

**高频查询模式**：

- 按治疗周期标识查询：单表主键查询主表，必要时 JOIN 子表
- 按业务号查询：主表 `bus_no` 索引扫描
- 按患者查询治疗周期列表：主表 `patient_id` 索引扫描
- 按机构科室查询：主表 `organization_id` 索引扫描

### 2.2 聚合根标识

| 属性   | 类型                  | 说明                 |
| ---- | ------------------- | ------------------ |
| 治疗周期标识 | `EpisodeOfCareId` | 全局唯一标识，系统生成（雪花 ID） |

### 2.3 状态机

```
长期治疗链路

  [PLANNED] ──启动治疗──> [ACTIVE]
      │                     │
      │                     │ 暂停治疗
      │                     ▼
      │                [ON_HOLD]
      │                     │
      │                     └──恢复治疗──> [ACTIVE]
      │
      └──取消──> [CANCELLED]
  任意非终止状态（权限允许）──> [ENTERED_IN_ERROR]

  [ACTIVE] / [ON_HOLD] ──正常结束──> [COMPLETED]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | PLANNED | 已计划 | 治疗周期已计划，待启动 |
| 2 | ACTIVE | 活跃中 | 治疗周期正在进行中，可关联多个 Encounter |
| 3 | ON_HOLD | 暂停中 | 治疗周期暂时暂停，可恢复 |
| 4 | COMPLETED | 已完成 | 治疗周期正常结束 |
| 5 | CANCELLED | 已取消 | 治疗周期被取消 |
| 9 | ENTERED_IN_ERROR | 错误录入 | 因误操作标记为错误 |

> **状态枚举说明**：
>
> - 文档以**本域枚举**为准；与 FHIR `EpisodeOfCare.status` 的取值集合语义相近处可建立展示映射，差异以实现与集成契约为准。
> - `planned`(1) 对应应用层「已计划」；`active`(2) 为「活跃中」；`on-hold`(3) 为「暂停中」；`completed`(4) 为「已完成」；`cancelled`(5) 与 MedicationRequest/ServiceRequest 的「已取消」语义一致；`entered-in-error`(9) 为纠错终止态。

## 3. 实体

```
EpisodeOfCare（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                   EpisodeOfCare                     │
  │                                                      │
  │  - patientId / busNo / statusEnum                 │
  │  - startTime / endTime / organizationId              │
  │  - careTeamId / careType                       │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ EpisodeOfCareEncounter（实体，0..N）    │    │
  │  │ - encounterId / joinTime / leaveTime       │    │
  │  └──────────────────────────────────────────────┘    │
  └──────────────────────────────────────────────────────┘
```

### 3.1 长期治疗周期（EpisodeOfCare）

EpisodeOfCare 聚合根的属性定义，对应 `adm_episode_of_care` 表。

| 属性     | 字段名                | 类型               | 必填 | 说明                               |
| ------ | ----------------- | ---------------- | ---- | -------------------------------- |
| 治疗周期标识 | `id`               | `Long`            | 是   | 主键，雪花 ID                            |
| 患者标识   | `patient_id`        | `PatientId`       | 是   | 关联患者聚合根的 ID                        |
| 业务号    | `bus_no`           | `String`          | 是   | 院内治疗周期业务编码                         |
| 状态枚举   | `status_enum`       | `EpisodeOfCareStatus` | 是   | 治疗周期状态，见 §2.3 / §4.1               |
| 开始时间   | `start_time`       | `DateTime`         | 是   | 治疗周期开始时间                            |
| 结束时间   | `end_time`         | `DateTime`         | 否   | 治疗周期结束时间（完成时填写）                    |
| 机构标识   | `organization_id`    | `OrganizationId`            | 是   | 关联机构/科室聚合根                        |
| 治疗团队标识 | `care_team_id`     | `Long`            | 否   | 关联治疗团队（若独立建模则为外键；否则预留）              |
| 治疗类型   | `care_type`        | `String`          | 否   | 治疗类型（慢性病管理、康复治疗、随访管理等）              |
| 治疗目标   | `care_goal`        | `String`          | 否   | 治疗目标和预期效果                         |

> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。

### 3.2 治疗周期就诊关联（EpisodeOfCareEncounter）

治疗周期就诊关联子实体，对应 `adm_episode_of_care_encounter` 表，表达治疗周期与就诊之间的关联关系。

| 属性     | 字段名                | 类型                | 必填 | 说明                     |
| ------ | ----------------- | ---------------- | ---- | ---------------------- |
| 关联标识 | `id`               | `Long`            | 是   | 主键，雪花 ID              |
| 治疗周期标识   | `episode_of_care_id` | `EpisodeOfCareId` | 是   | 外键，关联 EpisodeOfCare（`adm_episode_of_care.id`） |
| 就诊标识   | `encounter_id`      | `EncounterId`     | 是   | 关联就诊聚合根 ID           |
| 加入时间   | `join_time`        | `DateTime`        | 是   | 就诊加入治疗周期的时间           |
| 离开时间   | `leave_time`       | `DateTime`        | 否   | 就诊离开治疗周期的时间（治疗周期结束时填写） |

## 4. 值对象

### 4.1 治疗周期状态（EpisodeOfCareStatus）

见 §2.3 状态定义中的枚举表。

## 5. 领域事件

### 5.1 事件定义

| 事件名称     | 英文名称                                | 触发时机             | 携带数据                             | 消费方 |
| -------- | ----------------------------------- | ---------------- | -------------------------------- | ---- |
| 治疗周期已创建  | `EpisodeOfCareCreatedEvent`            | 创建治疗周期成功       | 治疗周期标识, 患者标识, 业务号, 机构标识, 状态 | 治疗管理, 随访管理 |
| 治疗周期已更新  | `EpisodeOfCareUpdatedEvent`          | 修改主数据成功        | 治疗周期标识, 变更字段摘要                  | 治疗看板, 统计 |
| 治疗周期已启动  | `EpisodeOfCareActivatedEvent`        | 自已计划进入活跃成功     | 治疗周期标识, 启动时间                    | 治疗管理, 随访管理 |
| 就诊已加入治疗周期 | `EncounterAddedToEpisodeEvent`       | 就诊加入治疗周期成功     | 治疗周期标识, 就诊标识, 加入时间              | 治疗管理, 就诊管理 |
| 治疗周期已暂停  | `EpisodeOfCarePausedEvent`           | 暂停成功           | 治疗周期标识, 暂停原因                    | 治疗管理, 随访管理 |
| 治疗周期已恢复  | `EpisodeOfCareResumedEvent`          | 从暂停恢复为活跃成功     | 治疗周期标识, 恢复时间                    | 治疗管理, 随访管理 |
| 治疗周期已完成  | `EpisodeOfCareCompletedEvent`        | 正常结束成功         | 治疗周期标识, 结束时间, 治疗结果摘要            | 治疗管理, 统计 |
| 治疗周期已取消  | `EpisodeOfCareCancelledEvent`        | 取消成功           | 治疗周期标识, 取消原因                    | 治疗管理, 随访管理 |
| 治疗周期已标记错误 | `EpisodeOfCareEnteredInErrorEvent`   | 标记为错误录入成功      | 治疗周期标识, 原因                      | 治疗管理, 审计 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
| ----- | ---- | ---- | ---- |
| — | 创建治疗周期 | `PLANNED` | `EpisodeOfCareCreatedEvent` |
| `PLANNED` | 启动治疗周期 | `ACTIVE` | `EpisodeOfCareActivatedEvent` |
| `PLANNED` / `ACTIVE` / `ON_HOLD` | 修改主数据（非状态迁移） | 不变 | `EpisodeOfCareUpdatedEvent` |
| `ACTIVE` / `ON_HOLD` | 加入就诊关联 | 不变 | `EncounterAddedToEpisodeEvent` |
| `ACTIVE` | 暂停 | `ON_HOLD` | `EpisodeOfCarePausedEvent` |
| `ON_HOLD` | 恢复 | `ACTIVE` | `EpisodeOfCareResumedEvent` |
| `ACTIVE` / `ON_HOLD` | 正常结束 | `COMPLETED` | `EpisodeOfCareCompletedEvent` |
| `PLANNED` / `ACTIVE` / `ON_HOLD` | 取消 | `CANCELLED` | `EpisodeOfCareCancelledEvent` |
| `PLANNED` / `ACTIVE` / `ON_HOLD` | 标记错误录入 | `ENTERED_IN_ERROR` | `EpisodeOfCareEnteredInErrorEvent` |

## 6. 领域服务

### 6.1 写侧领域服务（EpisodeOfCareDomainService）

#### 6.1.1 创建治疗周期（`createEpisodeOfCare`）

- **业务意义**：为患者开立一条长期治疗周期主数据，生成稳定的 `EpisodeOfCareId` 与院内业务号。
- **边界说明**：仅写入本聚合主表及聚合内初始化；不创建 Encounter、不编排收费与医嘱。
- **入参**：患者标识、机构标识、业务号生成策略入参（或由领域生成）、开始时间、可选治疗类型/治疗目标/治疗团队标识。
- **出参**：治疗周期标识（`EpisodeOfCareId`）。
- **前置条件**：患者、机构引用合法；同一患者在业务规则允许范围内重复创建策略满足 BR-EOC-001/002。
- **状态变化**：初始化为 `PLANNED`。
- **聚合内影响**：插入 `adm_episode_of_care`；子表可为 0 行。
- **事件输出**：`EpisodeOfCareCreatedEvent`。
- **失败语义**：必填缺失、患者无效、`bus_no` 冲突时拒绝创建。

#### 6.1.2 启动治疗周期（`activateEpisodeOfCare`）

- **业务意义**：将已计划的治疗周期转为活跃，允许持续纳入就诊关联。
- **边界说明**：仅变更本聚合状态与时间相关字段；不修改 Encounter 状态。
- **入参**：治疗周期标识、可选实际启动时间。
- **出参**：—。
- **前置条件**：当前状态须为 `PLANNED`。
- **状态变化**：`PLANNED` → `ACTIVE`。
- **聚合内影响**：更新 `status_enum`；可写入启动相关时间戳。
- **事件输出**：`EpisodeOfCareActivatedEvent`。
- **失败语义**：状态不合法或并发冲突时拒绝。

#### 6.1.3 修改治疗周期主数据（`updateEpisodeOfCareProfile`）

- **业务意义**：维护治疗类型、治疗目标、机构/团队等非状态机核心字段。
- **边界说明**：不改变状态机路径；就诊关联的增删使用专用方法。
- **入参**：治疗周期标识、变更字段集合。
- **出参**：—。
- **前置条件**：须为 `PLANNED` / `ACTIVE` / `ON_HOLD`；终止态禁止修改（纠错态除外由运维流程处理，不在此方法）。
- **状态变化**：`N/A`。
- **聚合内影响**：更新主表允许变更列。
- **事件输出**：`EpisodeOfCareUpdatedEvent`。
- **失败语义**：字段非法、状态不允许时拒绝。

#### 6.1.4 将就诊纳入治疗周期（`addEncounterToEpisode`）

- **业务意义**：把一次 Encounter 纳入本治疗周期，形成可追溯的周期—就诊关联。
- **边界说明**：仅维护子表 `adm_episode_of_care_encounter`；不替代 Encounter 聚合内状态机。
- **入参**：治疗周期标识、就诊标识、加入时间。
- **出参**：关联子实体标识（`Long`）。
- **前置条件**：治疗周期须为 `ACTIVE` 或 `ON_HOLD`；同一 `encounter_id` 未重复绑定于本周期（见 BR-EOC-006）。
- **状态变化**：`N/A`。
- **聚合内影响**：插入子表一行。
- **事件输出**：`EncounterAddedToEpisodeEvent`。
- **失败语义**：就诊无效、重复绑定、状态不允许时拒绝。

#### 6.1.5 暂停治疗周期（`pauseEpisodeOfCare`）

- **业务意义**：临时中断治疗周期，保留历史关联与审计。
- **边界说明**：不自动解绑已关联 Encounter；不级联暂停 Encounter。
- **入参**：治疗周期标识、暂停原因。
- **出参**：—。
- **前置条件**：当前状态须为 `ACTIVE`。
- **状态变化**：`ACTIVE` → `ON_HOLD`。
- **聚合内影响**：更新 `status_enum`。
- **事件输出**：`EpisodeOfCarePausedEvent`。
- **失败语义**：状态不合法时拒绝。

#### 6.1.6 恢复治疗周期（`resumeEpisodeOfCare`）

- **业务意义**：从暂停恢复为活跃，继续纳入就诊与业务引用。
- **边界说明**：不回溯修改历史子表行。
- **入参**：治疗周期标识、可选恢复时间。
- **出参**：—。
- **前置条件**：当前状态须为 `ON_HOLD`。
- **状态变化**：`ON_HOLD` → `ACTIVE`。
- **聚合内影响**：更新 `status_enum`。
- **事件输出**：`EpisodeOfCareResumedEvent`。
- **失败语义**：状态不合法时拒绝。

#### 6.1.7 完成治疗周期（`completeEpisodeOfCare`）

- **业务意义**：正常结束治疗周期，固化结束时间与结果摘要。
- **边界说明**：不删除子表关联；不自动完结 Encounter。
- **入参**：治疗周期标识、结束时间、治疗结果摘要（可选）。
- **出参**：—。
- **前置条件**：当前状态须为 `ACTIVE` 或 `ON_HOLD`；`end_time` 须满足 BR-EOC-003。
- **状态变化**：→ `COMPLETED`。
- **聚合内影响**：更新 `status_enum`、`end_time`；可按规则补写子表 `leave_time`（实现约定）。
- **事件输出**：`EpisodeOfCareCompletedEvent`。
- **失败语义**：时间非法、状态不允许时拒绝。

#### 6.1.8 取消治疗周期（`cancelEpisodeOfCare`）

- **业务意义**：业务上撤销治疗周期，终止后续纳入。
- **边界说明**：不物理删除主表与子表；是否允许取消由 BR-EOC-007 约束。
- **入参**：治疗周期标识、取消原因。
- **出参**：—。
- **前置条件**：当前状态为 `PLANNED` / `ACTIVE` / `ON_HOLD`。
- **状态变化**：→ `CANCELLED`。
- **聚合内影响**：更新 `status_enum`；可写 `end_time`（实现约定）。
- **事件输出**：`EpisodeOfCareCancelledEvent`。
- **失败语义**：已结束/已取消态禁止重复取消。

#### 6.1.9 标记治疗周期为错误录入（`markEpisodeOfCareEnteredInError`）

- **业务意义**：将误建或误关联的治疗周期标记为错误录入态，供审计与拦截引用。
- **边界说明**：不执行物理删除；下游引用迁移若有，由应用层/集成契约另行约定。
- **入参**：治疗周期标识、原因。
- **出参**：—。
- **前置条件**：当前状态非 `COMPLETED` / `CANCELLED`（具体以机构权限策略为准，文档层保留纠错入口）。
- **状态变化**：→ `ENTERED_IN_ERROR`。
- **聚合内影响**：更新 `status_enum`。
- **事件输出**：`EpisodeOfCareEnteredInErrorEvent`。
- **失败语义**：权限不足、状态不允许时拒绝。

### 6.2 读侧查询服务（EpisodeOfCareQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于列表、详情回显与前置校验。

#### 6.2.1 按标识加载治疗周期（`loadEpisodeOfCareById`）

- **业务意义**：按主键加载单条治疗周期主数据。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_episode_of_care` 主表。
- **过滤口径**：默认排除逻辑删除；终止态可见性以落库与交互契约为准。
- **组装规则**：主表行映射为聚合根展示结构；子表不强制加载。
- **返回约束**：不存在时返回空或抛业务异常（以实现统一约定为准）。

#### 6.2.2 按业务号加载治疗周期（`loadEpisodeOfCareByBusNo`）

- **业务意义**：按院内业务号精确定位治疗周期。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_episode_of_care`，`bus_no` 唯一索引。
- **过滤口径**：同 6.2.1。
- **组装规则**：同 6.2.1。
- **返回约束**：未命中返回空；命中多条视为数据异常并告警（与 Patient 类似约定）。

#### 6.2.3 按患者列出治疗周期（`listEpisodeOfCareByPatient`）

- **业务意义**：患者维度列表，支撑随访与周期管理入口。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_episode_of_care`，按 `patient_id` 过滤。
- **过滤口径**：默认排除逻辑删除；可按时间倒序。
- **组装规则**：返回列表 DTO，不含 Encounter 明细（需要时由应用层二次查询或专用QueryService扩展）。
- **返回约束**：允许空列表。

#### 6.2.4 按机构列出治疗周期（`listEpisodeOfCareByOrganization`）

- **业务意义**：科室/机构维度管理列表。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_episode_of_care`，按 `organization_id` 过滤。
- **过滤口径**：同 6.2.3。
- **组装规则**：同 6.2.3。
- **返回约束**：允许空列表。

#### 6.2.5 按患者列出活跃治疗周期（`listActiveEpisodeOfCareByPatient`）

- **业务意义**：筛选 `ACTIVE`/`ON_HOLD`（可选配置是否包含暂停）用于开立业务前的周期选择。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`adm_episode_of_care`。
- **过滤口径**：`patient_id` + `status_enum` 命中活跃集合；排除逻辑删除。
- **组装规则**：同 6.2.3。
- **返回约束**：允许空列表。

### 6.3 领域边界说明

- `EpisodeOfCare` 领域动作只描述本聚合根及子实体 `EpisodeOfCareEncounter` 的创建、状态迁移与关联维护。
- 不把挂号、收费、医嘱开立、跨聚合报表统计等跨域编排写入本聚合写方法；跨域由应用层编排与各自聚合文档约束。
- 读侧方法不写库、不做跨域写操作，遵循 CQRS 分离原则。

## 7. 不变式

| 编号         | 不变式                                                                                                               | 说明          |
| ---------- | ----------------------------------------------------------------------------------------------------------------- | ----------- |
| BR-EOC-001 | `bus_no` 在 `adm_episode_of_care` 表内全局唯一                                                                               | 业务号唯一性      |
| BR-EOC-002 | `patient_id` 必须引用有效患者主数据（`PatientId`）                                                                               | 患者引用完整性     |
| BR-EOC-003 | 状态为 `COMPLETED` 时 `end_time` 必填                                                                                    | 完成态时间完整性    |
| BR-EOC-004 | 同一治疗周期下可关联 0..N 个 Encounter；关联关系仅存于子表                                                                         | 就诊关联基数      |
| BR-EOC-005 | `start_time` 必填且不得晚于合理业务约束下的 `end_time`（若已填）                                                                       | 时间轴合法性      |
| BR-EOC-006 | 同一 `episode_of_care_id` 下同一 `encounter_id` 不得重复绑定有效行（重复定义以实现唯一索引为准）                                              | 子表唯一性       |
| BR-EOC-007 | `COMPLETED`、`CANCELLED`、`ENTERED_IN_ERROR` 为终止态；终止后禁止状态回退到 `PLANNED`/`ACTIVE`/`ON_HOLD`（纠错流程除外，由运维策略单独立项） | 终止态不可逆      |
| BR-EOC-008 | 默认读口径排除 `isDeleted=1` 的主表记录                                                                                      | 读可见性与 Patient 一致 |

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合           | 关联方式                     | 说明        |
| ----- | ------------ | ------------------------ | --------- |
| 患者管理  | Patient      | `患者标识(PatientId)`        | 治疗周期归属患者 |
| 组织架构  | Organization | `机构科室标识(OrganizationId)` | 管理机构/科室  |

### 8.2 下游影响

| 下游上下文 | 聚合            | 触发方式 | 说明                               |
| ----- | ------------- | ---- | -------------------------------- |
| 就诊管理  | Encounter     | 引用   | 通过子表关联就诊；单次就诊仍以 Encounter 为权威 |
| 临床诊断  | Condition     | 引用   | 可按治疗周期维度聚合展示（QueryService/应用层）        |
| 收费管理  | ChargeItem    | 引用   | 费用明细可携带治疗周期标识做统计维度           |
| 治疗团队  | CareTeam（若建模） | 引用   | `care_team_id` 预留关联            |

### 8.3 引用规则

> **统一规则**：所有需要引用长期治疗周期的领域模型，遵循以下原则：
>
> - 治疗周期引用统一使用 `治疗周期标识(EpisodeOfCareId)`（对应 FHIR `EpisodeOfCare` 逻辑标识字段），不使用模糊简称。
> - 下游聚合对 EpisodeOfCare 为**只读引用**——通过 ID 引用，不直接持有聚合内实体图。
> - 单次就诊业务默认只强依赖 `EncounterId`；需要跨就诊管理视角时同时携带 `EpisodeOfCareId`。

### 8.4 事件流示意图

```
Patient          EpisodeOfCare              Encounter           ChargeItem / Condition
   │                    │                        │                      │
   │──(建档引用)───────>│                        │                      │
   │                    │── CreatedEvent ─────>│ (可选订阅)            │
   │                    │── ActivatedEvent ──>│                      │
   │                    │── EncounterAdded ──>│ (索引治疗周期)         │
   │                    │── CompletedEvent ─────────────────────────>│ (统计口径)
   │                    │── CancelledEvent ──────────────────────────>│ (拦截新开)
```

## 9. 补充

### 9.1 治疗周期串联就诊说明

1. **创建周期**：`createEpisodeOfCare` 生成 `PLANNED` 状态主数据。
2. **启动周期**：`activateEpisodeOfCare` 进入 `ACTIVE` 后可 `addEncounterToEpisode`。
3. **纳入就诊**：每次 Encounter 与周期建立关联写入子表，供治疗管理/随访列表聚合展示。
4. **结束周期**：`completeEpisodeOfCare` 或 `cancelEpisodeOfCare` 进入终止态；读侧按 BR-EOC-008 默认过滤。

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `EpisodeOfCareDomainService`/`EpisodeOfCareQueryService` 白名单对齐（9写+5读） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`EpisodeOfCareDomainService`

| 方法 |
|------|
| `createEpisodeOfCare` |
| `activateEpisodeOfCare` |
| `updateEpisodeOfCareProfile` |
| `addEncounterToEpisode` |
| `pauseEpisodeOfCare` |
| `resumeEpisodeOfCare` |
| `completeEpisodeOfCare` |
| `cancelEpisodeOfCare` |
| `markEpisodeOfCareEnteredInError` |

### QueryService：`EpisodeOfCareQueryService`

| 方法 |
|------|
| `loadEpisodeOfCareById` |
| `loadEpisodeOfCareByBusNo` |
| `listEpisodeOfCareByPatient` |
| `listEpisodeOfCareByOrganization` |
| `listActiveEpisodeOfCareByPatient` |

