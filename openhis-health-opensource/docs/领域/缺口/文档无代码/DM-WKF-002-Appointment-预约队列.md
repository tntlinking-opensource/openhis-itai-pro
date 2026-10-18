# DM-WKF-002 Appointment 预约与候诊

> **限界上下文**：WKF域 · 预约与候诊（门诊号源预约、多渠道报到）
> **聚合类型**：实体聚合
> **版本**：v1.0.1 · **日期**：2026-05-28

---

## 1. 概述

### 1.1 业务定义

**Appointment（预约与候诊）** 在本限界内表达三类行政侧语义，共用同一聚合根与持久化主表（演进中可物理拆分扩展表，逻辑上仍属本聚合）：

1. **门诊预约挂号**：对齐 FHIR 号源占用语义，关联患者、号源时段、服务类型等（字段以 `appointmentKind = OUTPATIENT_BOOKING` 时生效的不变式约束为准）。
3. **服务触点与审计**：自助机、小程序等渠道及会话信息，不单独命名为并列聚合，而以值对象 **AppointmentOrigin** 挂在 `Appointment` 上；互操作导出时映射为 `Appointment.extension` 与/或 `identifier` 体系。

本实现采用**显式适配**：临床与文书仍围绕 `Encounter`；**候诊排队、叫号顺序、过号、门诊号源占位** 由本聚合承担，并在集成视图中映射为 `Appointment` 资源（院内 Profile），避免与 FHIR 原文冲突的同时满足国内叫号与预约统一建模需求。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 分类承载 | 通过 `appointmentKind` 区分门诊预约、报到候诊等形态，各形态字段子集由不变式约束 |
| 入队/创建 | 分诊提交或报到流程创建有效项；生成业务号 `busNo` |
| 排序 | 报到候诊形态下按机构/去向诊室维度维护 `displayOrder` |
| 队列操作 | 报到候诊形态下支持优先就诊、前后调位、拖拽调位 |
| 出队/终态 | 叫号或过号等写入终态原因；持久化可与 FHIR `status` 导出映射对齐 |
| 查询 | 按机构、诊室、患者、就诊等维度分页或单条查询有效候诊/预约项 |

### 1.3 适用范围

| 场景 | `appointmentKind` | 说明 |
|---|---|---|
| 门诊预约挂号 | `OUTPATIENT_BOOKING` | 患者经窗口/自助机/小程序选择号源；与 `Slot`/排班域协作 |
| 仅报到入队（可选） | `CHECK_IN_ONLY` | 已预约或现场挂号后的报到产生候诊项；是否启用由机构策略决定 |

### 1.4 与相关聚合的关系

- **Encounter（就诊）**：报到候诊形态下每条有效项以 `encounterId` 关联就诊；分诊更新触发旧项取消并新建。门诊预约形态可在创建就诊前仅关联 `patientId`，就诊生成后再回填 `encounterId`（若业务如此编排）。
- **Location（位置）**：`destinationLocationId` 指向目标诊室/科室；队列展示按机构与去向地点维度隔离。
- **Practitioner（医生）**：叫号操作由医生发起时，记录操作者标识（持久化字段见实体表；可与 FHIR `participant` 导出映射）。
- **Patient（患者）**：门诊预约与小程序侧查询宜保留 `patientId`（设计扩展），与 FHIR `subject` 对齐。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

- **当前主表**：`wkf_appointment_queue`（表名保留以实现兼容；领域类型名为 `Appointment`）。
- **扩展策略**：新增领域字段可通过表结构增量或**逻辑上属于本聚合的扩展表**落地；跨上下文仍只暴露 `AppointmentId`。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 预约与候诊标识 | `AppointmentId`（Long） | 院内主键；FHIR 导出为 `Appointment.id` 或 `identifier` |

### 2.3 分类：`appointmentKind`

| 常量 | 英文编码 | 中文 | 说明 |
|---|---|---|---|
| `OUTPATIENT_BOOKING` | `OUTPATIENT_BOOKING` | 门诊预约挂号 | `patientId`、号源相关字段子集由不变式约束 |
| `CHECK_IN_ONLY` | `CHECK_IN_ONLY` | 报到候诊 | 可选；与预约系统衔接后的报到入队 |

持久化建议：`appointment_kind` 字符串或整型枚举码。


## 3. 实体

```
Appointment（聚合根）
════════════════════════════════════════════════════════════
  id · appointmentKind · busNo · patientId · encounterId · organizationId
  destinationLocationId · triageLevel · triageTime · triagePriorityCode
  displayOrder · slotId · serviceTypeCode · appointmentTypeCode
  startInstant · endInstant · appointmentOrigin（值对象）
  cancelReason · cancellationReasonCode · cancellationAt · calledAt
  lastPractitionerId · isDeleted
════════════════════════════════════════════════════════════
```

### 3.1 Appointment（聚合根）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 分类 | `appointment_kind` | 枚举/String | 是 | 见 §2.3 |
| 业务号 | `bus_no` | `String(64)` | 否 | 采番前缀 `APN`；FHIR `identifier` |
| 患者标识 | `patient_id` | `Long` | 视 kind | 门诊预约、小程序查询建议必填；报到候诊可自 Encounter 解析 |
| 就诊标识 | `encounter_id` | `Long` | 视 kind | 报到候诊形态必填；纯预约阶段可空后填 |
| 机构标识 | `organization_id` | `Long` | 是 | 机构维度 |
| 去向诊室 | `destination_location_id` | `Long` | 否 | 诊室/地点；FHIR `participant.actor` → Location |
| 分诊等级（文本） | `triage_level` | `String(64)` | 否 | 展示用；可与 `triage_priority_code` 并存迁移 |
| 分诊优先级编码 | `triage_priority_code` | `String(64)` | 否 | 术语编码；FHIR `priority`（CodeableConcept） |
| 分诊时间 | `triage_time` | `LocalDateTime` | 否 | 报到排序基准；可映射 `start` |
| 显示顺序 | `display_order` | `Integer` | 视 kind | 报到候诊形态必填，默认 1 |
| 号源标识 | `slot_id` | `Long` | 视 kind | 门诊预约；FHIR `slot` |
| 服务类型编码 | `service_type_code` | `String(64)` | 否 | FHIR `serviceType` |
| 预约类型编码 | `appointment_type_code` | `String(64)` | 否 | FHIR `appointmentType` |
| 计划开始 | `start_instant` | `Instant`/`OffsetDateTime` | 视 kind | FHIR `start` |
| 计划结束 | `end_instant` | `Instant`/`OffsetDateTime` | 否 | FHIR `end` |
| 取消原因（文本） | `cancel_reason` | `String(255)` | 否 | 软删除业务原因（中文落库惯例可保留） |
| 取消原因（编码） | `cancellation_reason_code` | `String(64)` | 否 | 对齐 FHIR `cancellationReason` |
| 取消/过号时间 | `cancellation_at` | `LocalDateTime` | 否 | FHIR `cancellationDate` |
| 最近叫号时间 | `called_at` | `LocalDateTime` | 否 | 院内扩展；便于大屏与小程序 |
| 最近叫号医生 | `last_practitioner_id` | `Long` | 否 | 叫号操作者；FHIR `participant` |
| 软删除 | `is_deleted` | `Boolean` | 是 | 报到候诊形态下终态判定核心 |
| 租户 | `tenant_id` | `Long` | 否 | 多租户 |

**AppointmentOrigin（值对象，与主表同列或 JSON 列/扩展表承载）**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 渠道 | `origin_channel` | 枚举/String | 否 | 如 `KIOSK`、`MINIAPP`、`COUNTER`、`DOCTOR_STATION`、`NURSE_STATION` |
| 渠道会话 | `origin_channel_session_id` | `String(128)` | 否 | 小程序/自助机会话，审计与风控 |
| 来源设备 | `origin_source_device_id` | `Long` | 否 | 自助机 Device；FHIR `participant.actor` → Device |
| 操作者 | `origin_operator_id` | `Long` | 否 | 代操作时护士账号 |
| 客户端版本 | `origin_client_app_version` | `String(32)` | 否 | 小程序/App 版本 |

> 通用审计字段（`created_at`、`updated_at`、`created_by`、`updated_by`、`deleted_at`、`deleted_by`）继承自 HisBaseEntity，不在此处逐行列出。

---

## 4. 值对象

### 4.1 采番前缀（`AssignSeqEnum`）

| 枚举项 | 编码 | 前缀 | 说明 |
|---|---|---|---|
| `APPOINTMENT_NO` | `70` | `APN` | 业务号采番前缀 |

### 4.2 服务触点渠道（`AppointmentOriginChannel`）

| 常量 | 英文编码 | 中文 | 说明 |
|---|---|---|---|
| `KIOSK` | `KIOSK` | 自助机 | 来源设备标识建议绑定 Device |
| `MINIAPP` | `MINIAPP` | 小程序 | 建议携带 `origin_channel_session_id` |
| `COUNTER` | `COUNTER` | 窗口 | 人工建单/预约 |
| `DOCTOR_STATION` | `DOCTOR_STATION` | 医生站 | 诊间预约等 |
| `NURSE_STATION` | `NURSE_STATION` | 护士站 | 分诊台代操作 |
| `OTHER` | `OTHER` | 其他 | 兜底 |

### 4.3 业务状态与持久化（报到候诊形态）

| 业务状态 | 持久化表达 | 触发方法 |
|---|---|---|
| 待诊中 | `is_deleted=false` | `create` |
| 已取消 | `is_deleted=true` + `cancel_reason` / `cancellation_reason_code` | `cancelByEncounterId` 等 |
| 已叫号 | `is_deleted=true` + `cancel_reason=医生叫号看诊` | `callToSee` |
| 已过号 | `is_deleted=true` + `cancel_reason=过号` | `noShow` |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称（中文） | 英文名称 | 触发时机 | 携带数据 |
|---|---|---|---|
| 预约候诊项已创建 | `AppointmentCreatedEvent` | 创建后 | `appointmentId`、`appointmentKind`、`encounterId`（若有）、`patientId`（若有）、`displayOrder`（若有）、`AppointmentOrigin` 摘要 |
| 优先就诊已应用 | `PriorityTreatmentAppliedEvent` | `priorityTreatment` 后 | `appointmentId`、原/新顺序 |
| 队列顺序已调整 | `QueueOrderChangedEvent` | `changeOver` / `dragChangeOrder` 后 | `appointmentId`、原/新顺序 |
| 已叫号看诊 | `AppointmentCalledToSeeEvent` | `callToSee` 后 | `appointmentId`、`encounterId`、`calledAt`、`lastPractitionerId`、`AppointmentOrigin` 摘要 |
| 已过号 | `AppointmentNoShowEvent` | `noShow` 后 | `appointmentId`、`encounterId`、`cancellationAt`、`AppointmentOrigin` 摘要 |

> 设计预留：实现尚未覆盖上述事件类与发布代码时，本文档作为目标契约。

### 5.2 事件触发规则（报到候诊形态）

| 当前状态 | 操作 | 目标状态 | 领域事件 |
|---|---|---|---|
| 待诊中 | 优先就诊 | 待诊中 | `PriorityTreatmentAppliedEvent` |
| 待诊中 | 顺序调整 | 待诊中 | `QueueOrderChangedEvent` |
| 待诊中 | 叫号看诊 | 已叫号 | `AppointmentCalledToSeeEvent` |
| 待诊中 | 过号处理 | 已过号 | `AppointmentNoShowEvent` |
| 待诊中 | 分诊更新取消 | 已取消 | 取消类事件（命名可与 `QueueOrderChangedEvent` 旧口径对齐后统一为 `AppointmentCancelledEvent`） |

---

## 6. 领域服务

### 6.1 写侧领域服务（`AppointmentManager`）

| 中文方法名 | 方法 | 入参 | 出参 | 业务规则 |
|---|---|---|---|---|
| 创建候诊/预约项 | `create` | `appointmentKind`、机构、去向诊室；报到候诊：`encounterId`、分诊字段、`displayOrder`；门诊：`patientId`、`slotId` 等；可选 `AppointmentOrigin` | `Appointment` | 按 kind 校验必填子集；生成 `busNo`；报到候诊默认 `displayOrder=1` |
| 渠道报到入队 | `checkInToQueue` | `appointmentKind`、`encounterId` 或预约关联键、`AppointmentOrigin` | `Appointment` | 与 `create` 等价或包装；写入渠道/会话/设备 |
| 按就诊取消 | `cancelByEncounterId` | `encounterId`、`reason`、可选编码原因 | `void` | 仅取消当前**报到候诊形态**有效项；软删除 + 原因 |
| 优先就诊 | `priorityTreatment` | `appointmentId`、`destinationLocationId` | `void` | 置顶并重排 `displayOrder` |
| 前后调位 | `changeOver` | `appointmentId`、`triageLevel`、`moveForward` | `void` | 与相邻项交换；越界报错 |
| 拖拽调位 | `dragChangeOrder` | `appointmentId`、`targetIndex` | `void` | 移动并重排 |
| 叫号看诊 | `callToSee` | `appointmentId`、操作医生标识 | `void` | 软删除；原因「医生叫号看诊」；写 `called_at`、`last_practitioner_id` |
| 过号处理 | `noShow` | `appointmentId` | `void` | 软删除；原因「过号」；写 `cancellation_at` |

### 6.2 读侧查询服务（`AppointmentQueryService`）

| 编号 | 中文方法名 | 方法名 | 业务职责 | 使用场景/约束 |
|---|---|---|---|---|
| Q-01 | 按就诊查询有效项 | `findByEncounterId` | 单就诊有效 `Appointment` | 就诊详情回填排队信息 |
| Q-02 | 按机构与诊室分页查询队列 | `findQueueByOrganizationAndLocation` | `appointmentKind=CHECK_IN_ONLY` 且 `is_deleted=false`，按 `displayOrder` 升序 | 候诊列表分页 |
| Q-03 | 批量按就诊查询 | `findByEncounterIdIn` | 批量回填 | 列表联查 |
| Q-04 | 按患者查询预约/候诊 | `findByPatientId` | 过滤 kind 与日期 | 小程序「我的预约/候诊」 |

> 设计预留：读侧可由 `AppointmentManager` 与应用服务暂代，接口名以本文档为准逐步收敛。

### 6.3 领域边界说明

| 分类 | 内容 |
|---|---|
| 聚合内动作 | 分类创建、取消、优先、调序、叫号、过号；渠道值对象随创建/报到写入 |
| 跨域编排 | 分诊提交、挂号成功、号源锁定由应用服务编排；本聚合不实现排班算法 |
| 非本聚合职责 | 分诊医学规则、EMPI、号源生成规则、计费 |

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-APT-003 | 终态项不参与待诊查询 | `is_deleted=false` 过滤 |
| BR-APT-004 | 叫号与过号必须保留可追溯原因 | `cancel_reason` 与/或 `cancellation_reason_code` |
| BR-APT-006 | 调序不得越界 | 首位/末位规则同前 |
| BR-APT-007 | `OUTPATIENT_BOOKING` 须满足患者与号源约束 | 至少 `patientId` 与 `slot_id`（或等价引用）由业务确认 |
| BR-APT-008 | `AppointmentOrigin.channel` 与自助机设备 | `KIOSK` 时建议 `origin_source_device_id` 非空（机构策略可豁免） |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合/服务 | 关联方式 | 说明 |
|---|---|---|---|
| 就诊管理 | Encounter | `encounterId` | 报到候诊核心 |
| 患者主索引 | Patient | `patientId` | 门诊与小程序 |
| 组织架构 | Organization | `organizationId` | 分组 |
| 位置管理 | Location | `destinationLocationId` | 诊室维度 |
| 号源排班 | Slot/Schedule（本域或其他限界上下文） | `slotId` | 门诊预约 |

### 8.2 下游影响

| 下游上下文 | 触发方式 | 说明 |
|---|---|---|
| 医生工作站 | 查询/事件 | 叫号与候诊列表 |
| 候诊大屏/自助机 | 查询 | 排队展示 |
| 小程序 | 查询/事件推送（应用层） | 候诊进度、预约列表 |

### 8.3 引用规则

> **统一规则**：跨上下文传递 **`AppointmentId`（Long）**；禁止旁路直连本表做写操作；消费方通过应用服务或领域事件获取状态。

### 8.4 事件流示意图

```
分诊/报到/预约确认  ──> [AppointmentCreatedEvent]     ──> 候诊列表/小程序
叫号操作            ──> [AppointmentCalledToSeeEvent]  ──> 就诊流转
过号操作            ──> [AppointmentNoShowEvent]     ──> 就诊流转
调序操作            ──> [QueueOrderChangedEvent]       ──> 候诊列表刷新
```

---

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `AppointmentDomainService`/`AppointmentQueryService` 白名单对齐（8写+4读） |
| v1.0 | — | 初版 |

---

## 9. 补充

### 
