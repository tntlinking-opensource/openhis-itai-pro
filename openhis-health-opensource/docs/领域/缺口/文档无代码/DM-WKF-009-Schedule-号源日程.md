# DM-WKF-009 Schedule 号源日程

> **限界上下文**：WKF域 · 号源与预约\
> **聚合类型**：实体聚合\
> **版本**：v1.0.1 · **日期**：2026-05-28

## 1. 概述

### 1.1 业务定义

号源日程（Schedule）是医疗机构中**某类服务或可调度资源**在一段规划时间内的**可用性容器**：它声明规划窗口（`planningHorizon`）、可预约的服务/专科口径、以及该容器所对应的**一个或多个参与者（actor）**组合。系统在该容器之下生成或维护离散的 **`Slot`（号源时段）**；患者或业务方通过预约将 `Appointment` 绑定到 `Slot` 上。

FHIR 明确：`Schedule` **不提供**任何关于实际预约（`Appointment`）的信息，以便在**无权访问预约明细**时仍能判断“是否可能存在可预约时段”，满足安全与隐私隔离需求。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 规划窗口管理 | 维护 `planningHorizon`（起止时间），驱动号源生成与滚动前移 |
| 参与者绑定 | 以 1..* `actor` 表达“该日程对谁生效”（医师、诊室、医疗服务等组合） |
| 可预约口径 | 维护 `serviceCategory` / `serviceType` / `specialty` 等过滤与展示语义 |
| 生命周期 | 以业务状态 + FHIR `active` 对齐表达启用、停用与废弃 |
| 外部标识 | 维护 `identifier`，支撑与排班系统、第三方日历等外部主键对齐 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊医师排班 | 同一医师在不同院区/诊室可拆为多份 `Schedule`（每份绑定 location+practitioner） |
| 医技科室号源 | 以 `HealthcareService` 或 `Location` 为主 actor 的日程容器 |
| 设备类资源 | `Device` 作为 actor 的独占资源日程 |
| 多资源联合约束 | 复杂预约由应用层汇总多份 `Schedule` 的 `Slot` 可用性；本聚合只保证**单容器**一致性 |

### 1.4 与 Slot、Appointment 的边界

| 对象 | 职责 |
|---|---|
| **Schedule（本聚合）** | 定义“为谁、在什么规划期内、允许挂什么类型的号源”；变更频率相对较低（如按日滚动规划窗口） |
| **Slot（独立资源）** | 表达最小可预订时间单元；状态为 `free` / `busy` / `busy-unavailable` 等，随预约创建/取消高频变更（见 **`DM-WKF-010-Slot-号源时段`**） |
| **Appointment** | 表达已发生的预约承诺；可能引用 `Slot`，但**不写入**本聚合 |

> **非本资源用途**：药品给药计划应使用 `Timing` 数据类型，而非 `Schedule`（FHIR 规范说明）。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表（1:N）。

**表结构**：

- 主表：`wkf_schedule`（主键：`id`）
- 子表1：`wkf_schedule_actor`（外键：`schedule_id` → 主表 `id`，1:N，**N ≥ 1**，与 FHIR `actor` 基数一致）
- 子表2：`wkf_schedule_identifier`（外键：`schedule_id` → 主表 `id`，1:N，N 可为 0）

**高频查询模式**：

- 按主键加载：主表 + 子表一次查询组装
- 按参与者检索日程：子表 `wkf_schedule_actor` 上 `(actor_type, actor_id)` 组合索引，回联主表
- 按规划窗口与日期过滤：`planning_horizon_start`、`planning_horizon_end` 范围索引，配合 FHIR 搜索语义 `date`（`Schedule.planningHorizon` 包含指定日期）

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 号源日程标识 | `ScheduleId`（`Long`） | 系统内部唯一标识，对应 `Schedule.id` |

### 2.3 状态机

```
[草稿] ──发布──> [已启用] ──停用──> [已停用] ──恢复──> [已启用]
  │
  └──废弃──> [已废弃]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | draft | 草稿 | 已创建但未发布；不对外参与检索或未生成对外号源，允许编辑属性、维护 actor、发布、废弃 |
| 2 | active | 已启用 | 对外可用；`active_flag=true` 且业务状态为启用，允许调整规划窗口、维护分类与标识、停用 |
| 3 | inactive | 已停用 | 暂停接受新预约关联；历史 `Slot` 由 Slot 聚合处理，允许恢复启用、废弃 |
| 4 | retired | 已废弃 | 业务终止；不再滚动规划窗口 | — |

> **状态枚举说明**：业务状态机表达发布与废止过程；字段 `active_flag`（`Boolean`）与 FHIR `Schedule.active` 对齐——`已启用` 必须为 `true`，`已停用/已废弃` 为 `false`。`草稿` 在实现中可约定为 `active_flag=false` 直至发布，以避免未完成配置对外可见。

---

## 3. 实体

```
Schedule（聚合根）
════════════════════════════════════════════════════════════
  id · organizationId · name · status · activeFlag
  planningHorizonStart · planningHorizonEnd
  serviceTypeConceptJson · healthcareServiceId
  commentMarkdown
════════════════════════════════════════════════════════════
        │                              │
        │ 1..*                         │ 0..*
        ▼                              ▼
 ScheduleActor                  ScheduleIdentifier
 (actorType · actorId · sortNo)   (system · value · useCode)
════════════════════════════════════════════════════════════
```

### 3.1 号源日程（Schedule）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 号源日程标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 机构标识 | `organization_id` | `Long` | 是 | 日程归属机构，用于检索隔离 |
| 显示名称 | `name` | `String(255)` | 否 | 人类可读标签，对应 `Schedule.name` |
| 业务状态 | `status_enum` | `ScheduleStatus`（值对象） | 是 | 见 §2.3 |
| 启用标记 | `active_flag` | `Boolean` | 是 | 与 FHIR `Schedule.active` 对齐 |
| 规划窗口起 | `planning_horizon_start` | `DateTime` | 否 | `Period.start`；发布后建议必填 |
| 规划窗口止 | `planning_horizon_end` | `DateTime` | 否 | `Period.end`；须大于起（若均非空） |
| 服务类型概念快照 | `service_type_concept_json` | `String` | 否 | 承载 `CodeableConcept` 列表 JSON，对应 `Schedule.serviceType` 的 concept 侧（R5 为 `CodeableReference`） |
| 医疗服务引用 | `healthcare_service_id` | `Long` | 否 | 当服务类型以引用表达时指向 `HealthcareService` 主键 |
| 服务大类快照 | `service_category_json` | `String` | 否 | `serviceCategory` 多值 `CodeableConcept` 的 JSON 快照 |
| 专科快照 | `specialty_json` | `String` | 否 | `specialty` 多值 `CodeableConcept` 的 JSON 快照 |
| 可用性说明 | `comment_markdown` | `String` | 否 | R5 起为 `markdown` 类型，落库为文本 |
| 租户标识 | `tenant_id` | `Long` | 否 | 多租户字段 |

> 通用审计字段（`created_at`、`updated_at`、`created_by`、`updated_by`、`deleted_at`、`deleted_by`）继承自统一基类时，不在此逐行列出。

### 3.2 日程参与者（ScheduleActor）

承载 FHIR `actor`（1..*）的多引用语义；一行表示一个被调度实体。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 参与者行标识 | `id` | `Long` | 是 | 子表主键 |
| 号源日程标识 | `schedule_id` | `Long` | 是 | 外键，关联 `wkf_schedule.id` |
| 参与者类型 | `actor_type` | `ScheduleActorKind`（值对象） | 是 | 见 §4.1，对齐 FHIR 引用目标类型 |
| 参与者标识 | `actor_id` | `Long` | 是 | 对应类型聚合的业务主键 |
| 排序号 | `sort_no` | `Integer` | 是 | 稳定展示顺序，默认按创建顺序递增 |

### 3.3 日程外部标识（ScheduleIdentifier）

承载 FHIR `identifier`（0..*）。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 标识行标识 | `id` | `Long` | 是 | 子表主键 |
| 号源日程标识 | `schedule_id` | `Long` | 是 | 外键 |
| 标识体系 | `id_system` | `String(255)` | 是 | URI 或约定体系，如排班系统编码体系 |
| 标识值 | `id_value` | `String(255)` | 是 | 外部主键值 |
| 用途编码 | `use_code` | `String(32)` | 否 | 对齐 FHIR `Identifier.use`：`usual` / `official` / `temp` / `secondary` / `old` |

---

## 4. 值对象

### 4.1 日程参与者类型（`ScheduleActorKind`）

对齐 FHIR R5 `Schedule.actor` 允许的目标资源类型（R5 相较 R4 增加 `CareTeam`）。

| 常量 | 整型码 | 英文编码 | 中文 | 说明 |
|---|---|---|---|---|
| `PRACTITIONER` | 10 | `Practitioner` | 医师 | 个人执业者 |
| `PRACTITIONER_ROLE` | 11 | `PractitionerRole` | 医师岗位 | 机构内岗位组合 |
| `LOCATION` | 20 | `Location` | 位置/诊室 | 物理或服务地点 |
| `HEALTHCARE_SERVICE` | 30 | `HealthcareService` | 医疗服务 | 可预约服务定义 |
| `DEVICE` | 40 | `Device` | 设备 | 可独占设备资源 |
| `PATIENT` | 50 | `Patient` | 患者 | 特殊场景下以患者为被调度主体 |
| `RELATED_PERSON` | 60 | `RelatedPerson` | 相关人 | 陪护/家属参与型服务 |
| `CARE_TEAM` | 70 | `CareTeam` | 照护团队 | R5 新增目标类型 |
| `ORGANIZATION` | 80 | `Organization` | 组织 | 当以科室/机构整体为 actor 时 |

### 4.2 日程业务状态（`ScheduleStatus`）

| 常量 | 整型码 | 英文编码 | 中文 | 说明 |
|---|---|---|---|---|
| `DRAFT` | 1 | `draft` | 草稿 | 未发布 |
| `ACTIVE` | 2 | `active` | 已启用 | 对外可用 |
| `INACTIVE` | 3 | `inactive` | 已停用 | 暂停 |
| `RETIRED` | 4 | `retired` | 已废弃 | 终止 |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称（中文） | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 号源日程已创建 | `ScheduleCreatedEvent` | `createSchedule` 成功落库后 | `scheduleId`、`organizationId` | 号源生成、审计 |
| 号源日程已发布 | `SchedulePublishedEvent` | `publishSchedule` 将状态从草稿置为已启用后 | `scheduleId`、`planningHorizonStart`、`planningHorizonEnd` | 号源生成、检索索引 |
| 规划窗口已调整 | `SchedulePlanningHorizonChangedEvent` | `adjustPlanningHorizon` 成功提交后 | `scheduleId`、旧/新起止时间 | 号源滚动任务、缓存失效 |
| 服务口径已更新 | `ScheduleServiceProfileUpdatedEvent` | `updateServiceProfile` 成功提交后 | `scheduleId`、变更摘要版本号 | 检索与展示 |
| 参与者集合已替换 | `ScheduleActorsReplacedEvent` | `replaceScheduleActors` 成功提交后 | `scheduleId`、参与者数量 | 冲突检测、展示 |
| 外部标识已同步 | `ScheduleIdentifiersSyncedEvent` | `syncScheduleIdentifiers` 成功提交后 | `scheduleId`、标识条数 | 外部系统对账、审计 |
| 号源日程已停用 | `ScheduleDeactivatedEvent` | `deactivateSchedule` 后 | `scheduleId`、`reason`（可选） | 号源关闭、预约编排 |
| 号源日程已恢复 | `ScheduleReactivatedEvent` | `reactivateSchedule` 后 | `scheduleId` | 号源开放 |
| 号源日程已废弃 | `ScheduleRetiredEvent` | `retireSchedule` 后 | `scheduleId` | 清理任务、审计 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| 草稿 | 创建完成 | 草稿 | `ScheduleCreatedEvent` |
| 草稿 | 发布 | 已启用 | `SchedulePublishedEvent` |
| 已启用 | 调整规划窗口 | 已启用 | `SchedulePlanningHorizonChangedEvent` |
| 已启用 | 更新服务口径 | 已启用 | `ScheduleServiceProfileUpdatedEvent` |
| 草稿 / 已启用 | 替换参与者 | 同态 | `ScheduleActorsReplacedEvent` |
| 草稿 / 已启用 | 同步外部标识 | 同态 | `ScheduleIdentifiersSyncedEvent` |
| 已启用 | 停用 | 已停用 | `ScheduleDeactivatedEvent` |
| 已停用 | 恢复 | 已启用 | `ScheduleReactivatedEvent` |
| 草稿 / 已停用 | 废弃 | 已废弃 | `ScheduleRetiredEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（`ScheduleDomainService`）

#### 6.1.1 创建号源日程（`createSchedule`）

- **业务意义**：新建一条号源日程容器，绑定机构并初始化参与者集合（至少一条）。
- **边界说明**：仅创建 `Schedule` 与其子表，不生成 `Slot`、不创建 `Appointment`。
- **入参**：机构标识、初始 `actor` 列表（1..*）、可选名称与外部标识列表。
- **出参**：`ScheduleId`。
- **前置条件**：机构标识合法；`actor` 列表非空且各 `actorId` 在对应类型下可解析。
- **状态变化**：初始化为 `draft`；`active_flag=false`（按 §2.3 约定）。
- **聚合内影响**：主表插入；子表批量插入参与者与标识。
- **事件输出**：`ScheduleCreatedEvent`。
- **失败语义**：参与者为空、外键不可解析、重复业务键（若定义了唯一约束）时拒绝。

#### 6.1.2 发布号源日程（`publishSchedule`）

- **业务意义**：将草稿日程发布为已启用，使其进入对外检索与号源挂接生命周期。
- **边界说明**：不触发批量 `Slot` 生成（由号源领域或应用定时任务消费事件后执行）。
- **入参**：`ScheduleId`、规划窗口起止（建议必填）、可选 `active_flag` 覆盖（默认 `true`）。
- **出参**：—。
- **前置条件**：当前为 `draft`；`planning_horizon_end` > `planning_horizon_start`（若均非空）；至少一条 `ScheduleActor`。
- **状态变化**：`draft -> active`；`active_flag=true`。
- **聚合内影响**：更新主表状态与规划窗口。
- **事件输出**：`SchedulePublishedEvent`（规划窗口以入参一并写入时仍发布本事件；若实现需要额外审计粒度，可再发 `SchedulePlanningHorizonChangedEvent`，须避免重复消费同一业务事实）。
- **失败语义**：状态非草稿、时间非法、参与者缺失时拒绝。

#### 6.1.3 调整规划窗口（`adjustPlanningHorizon`）

- **业务意义**：滚动或修正规划期，保持与夜间批处理前移策略一致（FHIR 注释场景）。
- **边界说明**：不直接改动既有 `Slot` 行；`Slot` 对齐由 Slot 聚合或同步任务处理。
- **入参**：`ScheduleId`、新起止时间。
- **出参**：—。
- **前置条件**：日程非 `retired`；时间合法；若已启用，新窗口应覆盖业务要求的最低前瞻天数（具体天数为运营参数，由应用层传入校验标记或领域策略表）。
- **状态变化**：`N/A`（不改变业务状态枚举，除非实现将“窗口为空”与草稿联动）。
- **聚合内影响**：更新主表 `planning_horizon_start/end`。
- **事件输出**：`SchedulePlanningHorizonChangedEvent`。
- **失败语义**：已废弃、时间倒置、无权限策略命中时拒绝。

#### 6.1.4 更新服务口径（`updateServiceProfile`）

- **业务意义**：维护 `serviceCategory` / `specialty` / `serviceType` 概念快照与 `HealthcareService` 引用，用于检索过滤与展示（对齐 FHIR `type` 搜索对服务类型的约束语义）。
- **边界说明**：不修改参与者集合；不更改 `Slot`。
- **入参**：`ScheduleId`、服务大类 JSON、专科 JSON、服务类型概念 JSON、可选 `healthcareServiceId`。
- **出参**：—。
- **前置条件**：日程非 `retired`。
- **状态变化**：`N/A`。
- **聚合内影响**：更新主表 JSON 与引用列。
- **事件输出**：`ScheduleServiceProfileUpdatedEvent`。
- **失败语义**：JSON 结构非法、引用不存在时拒绝。

#### 6.1.5 替换参与者集合（`replaceScheduleActors`）

- **业务意义**：整体替换 `actor` 列表，保证 FHIR 1..* 约束与本地唯一性策略。
- **边界说明**：不做跨 `Schedule` 的冲突求解；冲突检测读取 `Slot` 由应用编排调用读端口。
- **入参**：`ScheduleId`、新参与者列表（1..*）、排序策略（可选）。
- **出参**：—。
- **前置条件**：日程非 `retired`；新列表非空。
- **状态变化**：`N/A`。
- **聚合内影响**：删除旧 `wkf_schedule_actor` 行并插入新行（或等价 diff 同步）。
- **事件输出**：`ScheduleActorsReplacedEvent`。
- **失败语义**：列表为空、类型非法时拒绝。

#### 6.1.6 同步外部标识（`syncScheduleIdentifiers`）

- **业务意义**：对齐外部系统的 `identifier` 集合。
- **边界说明**：仅维护本子表；不写入 `Slot`。
- **入参**：`ScheduleId`、标识列表（含 `system/value/use`）。
- **出参**：—。
- **前置条件**：日程非 `retired`；同一 `system+value` 不重复。
- **状态变化**：`N/A`。
- **聚合内影响**：按策略覆盖子表。
- **事件输出**：`ScheduleIdentifiersSyncedEvent`。
- **失败语义**：重复标识、格式非法时拒绝。

#### 6.1.7 更新可用性说明（`updateComment`）

- **业务意义**：维护 `comment`（R5 为 `markdown`）供管理端展示注意事项。
- **边界说明**：纯文本字段更新，不触发号源重算。
- **入参**：`ScheduleId`、`markdown` 文本。
- **出参**：—。
- **前置条件**：日程非 `retired`。
- **状态变化**：`N/A`。
- **聚合内影响**：更新 `comment_markdown`。
- **事件输出**：无（若需要审计，可实现侧可选发布 `ScheduleServiceProfileUpdatedEvent` 低优先级版本）。
- **失败语义**：长度超限时拒绝。

#### 6.1.8 停用号源日程（`deactivateSchedule`）

- **业务意义**：暂停对外可预约关联，对齐 FHIR `active=false` 管理语义。
- **边界说明**：不级联取消已存在 `Appointment`。
- **入参**：`ScheduleId`、停用原因（可选）。
- **出参**：—。
- **前置条件**：当前为 `active`。
- **状态变化**：`active -> inactive`；`active_flag=false`。
- **聚合内影响**：更新状态字段。
- **事件输出**：`ScheduleDeactivatedEvent`。
- **失败语义**：状态不合法时拒绝。

#### 6.1.9 恢复号源日程（`reactivateSchedule`）

- **业务意义**：将已停用日程恢复为启用。
- **边界说明**：不自动回补历史 `Slot`。
- **入参**：`ScheduleId`。
- **出参**：—。
- **前置条件**：当前为 `inactive`。
- **状态变化**：`inactive -> active`；`active_flag=true`。
- **聚合内影响**：更新状态字段。
- **事件输出**：`ScheduleReactivatedEvent`。
- **失败语义**：状态不合法时拒绝。

#### 6.1.10 废弃号源日程（`retireSchedule`）

- **业务意义**：终止容器生命周期，阻止后续滚动与关联。
- **边界说明**：不物理删除；下游清理策略由应用层定义。
- **入参**：`ScheduleId`、废弃原因（可选）。
- **出参**：—。
- **前置条件**：当前为 `draft` 或 `inactive`（实现可约束禁止从 `active` 直接废弃，须先停用）。
- **状态变化**：` -> retired`；`active_flag=false`。
- **聚合内影响**：更新状态字段。
- **事件输出**：`ScheduleRetiredEvent`。
- **失败语义**：若策略要求“启用态须先停用”，违反时拒绝。

### 6.2 读侧查询服务（`ScheduleQueryService`）

#### 6.2.1 按标识加载号源日程（`loadScheduleById`）

- **业务意义**：详情回显，组装主表与子表。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_schedule` + `wkf_schedule_actor` + `wkf_schedule_identifier`。
- **过滤口径**：默认排除逻辑删除；`retired` 是否可见由调用方场景位控制。
- **组装规则**：主表为根，子表按 `sort_no` 与标识行主键排序挂载。
- **返回约束**：不存在时返回空或抛业务异常（按统一查询规范二选一，须与全局异常策略一致）。

#### 6.2.2 按参与者与日期检索日程（`findSchedulesByActorAndDate`）

- **业务意义**：实现“先确定适用 `Schedule`，再查 `Slot`”的 FHIR 推荐路径的**前半段**。
- **边界说明**：只读查询，不写库，不做跨域编排；不返回 `Slot` 行。
- **查询范围（读库，无写入）**：`wkf_schedule` JOIN `wkf_schedule_actor`。
- **过滤口径**：`actor_type+actor_id` 命中；`active_flag=true`；`status_enum=ACTIVE`；指定日期落在 `[planning_horizon_start, planning_horizon_end]` 闭区间内（边界按机构时区解释）。
- **组装规则**：去重 `scheduleId`；附带参与者摘要列表。
- **返回约束**：结果集上限由调用方传入分页参数控制。

#### 6.2.3 按机构分页检索启用日程（`pageActiveSchedulesByOrganization`）

- **业务意义**：运营台批量浏览某机构当前启用日程。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_schedule`。
- **过滤口径**：`organization_id` 匹配；`active_flag=true`；`status_enum=ACTIVE`；可选名称模糊条件。
- **组装规则**：仅主表字段 + 参与者计数（子查询），详情加载走 `loadScheduleById`。
- **返回约束**：稳定排序（默认按 `planning_horizon_start` 升序）。

### 6.3 领域边界说明

- 本聚合只维护 **`Schedule` 容器及其子表**；**不**承担 `Slot` 生成算法、`Appointment` 预订事务、跨资源冲突求解（多 `Schedule` 联合）——后者归属应用编排或其他聚合。
- **检索组合**：按地址、服务大类等**间接**定位 `Schedule` 时，若条件落在 `HealthcareService` / `Location` 等上游聚合，应先通过其读服务解析出 `actor`，再调用本读服务（符合 **§2.4** 跨域组合不上收为单方法的约束口径）。
- **超售**：是否允许向 `busy` 的 `Slot` 写入预约，由 **Slot/Appointment** 所在边界规则决定，不在本聚合声明。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-SCH-001 | 每个 `Schedule` 至少存在一条 `ScheduleActor` | 对齐 FHIR `actor` 1..* |
| BR-SCH-002 | `planning_horizon_end` 必须大于 `planning_horizon_start`（当两者均非空） | `Period` 语义 |
| BR-SCH-003 | `已启用` 状态必须 `active_flag=true` | 与 FHIR `active` 对齐 |
| BR-SCH-004 | 进入 `已废弃（retired）` 后禁止再变更为草稿/启用/停用 | 终态保护 |
| BR-SCH-005 | `(id_system, id_value)` 在同一 `schedule_id` 下唯一 | 标识子表不重复 |
| BR-SCH-006 | `(actor_type, actor_id, schedule_id)` 组合唯一 | 同一参与者不在同一日程重复挂载 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 / 资源 | 关联方式 | 说明 |
|---|---|---|---|
| 组织架构 | Organization | `organization_id` | 多机构隔离 |
| 人员与岗位 | Practitioner / PractitionerRole | `ScheduleActor` | 医师维度排班 |
| 位置服务 | Location | `ScheduleActor` | 诊室/科室维度 |
| 服务目录 | HealthcareService | `ScheduleActor` 或 `healthcare_service_id` | 服务类型引用（R5 `CodeableReference`） |
| 设备主数据 | Device | `ScheduleActor` | 设备独占日程 |

### 8.2 下游影响

| 下游上下文 | 触发方式 | 说明 |
|---|---|---|
| 号源时段（Slot，`DM-WKF-010`） | 事件 / 定时任务 | 读取规划窗口生成或滚动 `Slot` |
| 预约（Appointment） | 间接 | 预约读写不穿透本聚合；经 `Slot` 关联 |
| 对外检索 / 缓存 | 事件 | 规划窗口与参与者变化驱动失效 |

### 8.3 引用规则

> **统一规则**：跨上下文传递使用 `ScheduleId`（`Long`）；禁止绕过领域服务直接写入 `wkf_schedule*` 表；下游通过应用服务或订阅 §5.1 事件获取变更。

### 8.4 事件流示意图

```
排班/运营配置
    │
    ├──(写)──> ScheduleDomainService
    │              │
    │              ├──> SchedulePublishedEvent ──> 号源生成（Slot 子系统）
    │              ├──> SchedulePlanningHorizonChangedEvent ──> 号源滚动任务
    │              └──> ScheduleDeactivatedEvent ──> 关闭新号源写入路径
    │
预约编排（应用层）
    │
    └──(读)──> ScheduleQueryService ──> SlotQueryService ──> Appointment 流程
```

---

## 9. 补充

### 

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：`§6.1/§6.2` 与 Java DomainService/QueryService 白名单对齐 |
| v1.0 | — | 初版 |
