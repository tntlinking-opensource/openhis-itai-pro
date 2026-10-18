# DM-WKF-010 Slot 号源时段

> **限界上下文**：WKF域 · 号源与预约\
> **聚合类型**：实体聚合\
> **版本**：v1.0.1 · **日期**：2026-05-28

## 1. 概述

### 1.1 业务定义

号源时段（Slot）是挂靠在某一 **`Schedule`（号源日程）** 下的**最小可预订时间区间**：用 `start` / `end`（R5 为 `instant`）描述起止，用 **`status`** 表达空闲/占用/封锁等 free/busy 语义。它不描述预约患者是谁、联系方式等——这些在 **`Appointment`** 中表达，从而支持「只暴露号源、不暴露预约」的检索与集成模式。

FHIR 说明：同一 `Slot` **可对应多个** `Appointment`（如团体服务固定席位数）；也可标记 **`overbooked`** 表示已超售、系统未必再接受新预约。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 时段实例化 | 在已发布 `Schedule` 的规划窗口内批量或单次生成 `Slot` 行 |
| 空闲/占用状态 | 维护 FHIR 要求的 `Slot.status`（`SlotStatus` 值集） |
| 容量与计数 | 以 `capacity` + `booked_count` 表达多席并发（本地扩展，对齐 FHIR 多预约语义） |
| 封锁与释放 | 将时段标为 `busy-unavailable`（停诊、设备检修等）或恢复为可约 |
| 与预约协作 | 在预约创建/取消时更新本聚合的占用计数与 `status`，**不**写入患者隐私字段 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊分时号源 | 与 `Schedule` 联动，按日/按周生成固定粒度时段 |
| 医技预约 | 检查设备、诊室为资源维度的可约时段 |
| 多席团体 | `capacity`>1 时允许多条预约指向同一 `Slot`，直至约满 |
| 预留号源 | 通过 `appointmentType` 区分现场号、预留号等（码表由院内约定） |

### 1.4 与 Schedule、Appointment 的边界

| 对象 | 职责 |
|---|---|
| **Schedule**（`DM-WKF-009`） | 容器与规划窗口、参与者组合；**不**内嵌 `Slot` |
| **Slot（本聚合）** | 具体时间片与 free/busy 状态、可选覆盖服务口径；**不**存储预约主数据 |
| **Appointment** | 预约合同与参与者；通过应用编排与本聚合的 `allocateBooking` / `releaseBooking` 协作 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表（0..* 标识）。

**表结构**：

- 主表：`wkf_slot`（主键：`id`；外键：`schedule_id` → `wkf_schedule.id`）
- 子表：`wkf_slot_identifier`（外键：`slot_id` → `wkf_slot.id`，0..*）

**高频查询模式**：

- 按日程 + 时间窗：`schedule_id` + `start_time`/`end_time` 范围索引，联合 `status` 过滤可约列表
- 按机构检索：依赖 `wkf_slot.organization_id`（冗余自 `Schedule`，避免每次 JOIN）+ 时间范围
- 预约前校验：主键或业务唯一键（`schedule_id` + `start_time` + `end_time`）唯一约束防重复时段

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 号源时段标识 | `SlotId`（`Long`） | 系统内部唯一标识，对应 `Slot.id` |

### 2.3 状态机（FHIR `Slot.status`）

FHIR 以单一 `status` 码表驱动 free/busy 语义；本地用 `booked_count` / `capacity` 细化多席占用（见 §4.2 与 §7）。

```text
                    ┌── 释放全部预约 / unblock ──┐
                    ▼                            │
[free] ──预约达成──> [busy] ──────────────────────┘
  │                    │
  │──封锁──> [busy-unavailable] ──解封──> [free]（若无预约）或 [busy]（若仍有预约）
  │
  ├──暂定──> [busy-tentative] ──确认──> [busy]；──取消──> [free]
  │
  └──差错──> [entered-in-error]（纠错终态，禁止再用于正常预约）
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | free | 空闲 | 可接受新预约（在容量与业务规则允许下），允许封锁、暂定、预约占用、维护覆盖字段 |
| 2 | busy | 已占用 | 已有关联预约或达到「不可再约」语义，允许继续加约（未达容量时）、释放、封锁（依策略）、超售标记 |
| 3 | busy-unavailable | 封锁 | 非预约原因的不可用（停诊等），允许解封（恢复规则见 §7） |
| 4 | busy-tentative | 暂定占用 | 临时锁定，待确认，允许确认、回滚为 `free` |
| 5 | entered-in-error | 录入差错 | 数据错误作废，仅允许审计类修正，不参与正常预约 |

> **多席与 `free`/`busy` 口径**：当 `capacity`>1 且 `0 < booked_count < capacity` 时，实现须二选一并在全院统一：**(A)** 仍标 `busy` 表示「已有占用但未满」；**(B)** 标 `free` 并用 `booked_count` 门控可约性。本文档 **BR-SLT-008** 默认采用 **(A)**，与 FHIR 示例「有预约即 busy」一致。

---

## 3. 实体

```
Slot（聚合根）
════════════════════════════════════════════════════════════
  id · organizationId · scheduleId
  startTime · endTime · statusCode
  capacity · bookedCount · overbookedFlag
  serviceCategoryJson · serviceTypeJson · specialtyJson · appointmentTypeJson
  commentText
════════════════════════════════════════════════════════════
        │
        │ 0..*
        ▼
 SlotIdentifier（identifier 子表）
════════════════════════════════════════════════════════════
```

### 3.1 号源时段（Slot）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 号源时段标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 机构标识 | `organization_id` | `Long` | 是 | 冗余自 `Schedule`，与 `wkf_schedule.organization_id` 一致 |
| 所属日程 | `schedule_id` | `Long` | 是 | 外键 → `wkf_schedule.id`，对齐 `Slot.schedule` |
| 开始时刻 | `start_time` | `DateTime` | 是 | 对齐 `Slot.start`（`instant` 精度在实现中映射为 `DateTime`） |
| 结束时刻 | `end_time` | `DateTime` | 是 | 对齐 `Slot.end`；须严格大于 `start_time` |
| 状态码 | `status_code` | `String(32)` | 是 | FHIR `Slot.status`，见 §4.1 |
| 额定容量 | `capacity` | `Integer` | 是 | 默认可为 1；多席场景 >1 |
| 已预约计数 | `booked_count` | `Integer` | 是 | 非负；与 `Appointment` 绑定数量一致（由应用编排保证） |
| 超售标记 | `overbooked_flag` | `Boolean` | 是 | 对齐 `Slot.overbooked`，默认 `false` |
| 服务大类快照 | `service_category_json` | `String` | 否 | 对齐 `serviceCategory` 0..* |
| 服务类型快照 | `service_type_json` | `String` | 否 | R5 `CodeableReference` 0..* 的 JSON 表达；有值时**覆盖**同字段在 `Schedule` 上的语义 |
| 专科快照 | `specialty_json` | `String` | 否 | 对齐 `specialty` 0..* |
| 预约类型/场景 | `appointment_type_json` | `String` | 否 | 对齐 `appointmentType` 0..*（ walk-in、预留等） |
| 备注 | `comment_text` | `String(512)` | 否 | 对齐 `Slot.comment`（R5 为 `string`） |
| 租户标识 | `tenant_id` | `Long` | 否 | 多租户字段 |

> 通用审计字段继承统一基类时，不在此逐行列出。

### 3.2 号源时段外部标识（SlotIdentifier）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 标识行标识 | `id` | `Long` | 是 | 子表主键 |
| 号源时段标识 | `slot_id` | `Long` | 是 | 外键 |
| 标识体系 | `id_system` | `String(255)` | 是 | 外部排班/日历体系 URI |
| 标识值 | `id_value` | `String(255)` | 是 | 外部主键 |
| 用途编码 | `use_code` | `String(32)` | 否 | 对齐 `Identifier.use` |

---

## 4. 值对象

### 4.1 号源时段状态（`SlotStatus` / FHIR Required）

绑定：[Slot Status](https://hl7.org/fhir/R5/valueset-slotstatus.html)（Required）。

| 常量 | 整型码（示例） | 英文编码 | 中文 | 说明 |
|---|---|---|---|---|
| `FREE` | 1 | `free` | 空闲 | 无占用或采用 §2.3 (B) 口径时的可约态 |
| `BUSY` | 2 | `busy` | 已占用 | 已有预约或已满（见 §2.3） |
| `BUSY_UNAVAILABLE` | 3 | `busy-unavailable` | 封锁 | 非预约原因不可用 |
| `BUSY_TENTATIVE` | 4 | `busy-tentative` | 暂定 | 待确认锁定 |
| `ENTERED_IN_ERROR` | 9 | `entered-in-error` | 录入差错 | 作废态 |

### 4.2 容量与占用（实现口径）

| 字段 | 语义 |
|---|---|
| `capacity` | 该时段最多允许的并发预约条数（团体号源） |
| `booked_count` | 当前已确认的预约占用条数（与 `busy-tentative` 是否计入由 **BR-SLT-009** 统一约定） |
| `overbooked_flag` | 当 `booked_count > capacity` 时为 `true`，表示 FHIR 超售语义 |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称（中文） | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 号源时段已批量生成 | `SlotBatchProvisionedEvent` | `provisionSlotBatch` 提交成功后 | `scheduleId`、生成条数、`slotId` 区间或首批 ID 列表 | 检索索引、缓存预热 |
| 号源时段已封锁 | `SlotBlockedEvent` | `blockSlot` 成功 | `slotId`、`scheduleId`、`startTime`、`endTime` | 预约编排、展示 |
| 号源时段已解封 | `SlotUnblockedEvent` | `unblockSlot` 成功 | `slotId`、`scheduleId`、新 `status_code` | 预约编排、展示 |
| 号源时段已记占用 | `SlotBookedEvent` | `allocateBooking` 成功 | `slotId`、`booked_count`、`status_code` | 预约、候诊 |
| 号源时段占用已释放 | `SlotReleasedEvent` | `releaseBooking` 成功 | `slotId`、`booked_count`、`status_code` | 预约、号源池 |
| 号源时段已标暂定 | `SlotTentativeMarkedEvent` | `markTentative` 成功 | `slotId`、`scheduleId` | 预约暂锁 |
| 暂定号源已确认 | `SlotTentativeConfirmedEvent` | `confirmTentative` 成功 | `slotId`、`booked_count`、`status_code` | 预约 |
| 号源时段已标录入差错 | `SlotEnteredInErrorEvent` | `markEnteredInError` 成功 | `slotId`、`reason`（可选） | 审计、对账 |
| 外部标识已同步 | `SlotIdentifiersSyncedEvent` | `syncSlotIdentifiers` 成功 | `slotId`、标识条数 | 外部系统对账 |
| 时段服务覆盖已更新 | `SlotServiceOverrideUpdatedEvent` | `updateOverrideServiceSnapshots` 成功 | `slotId` | 检索过滤 |
| 超售标记已更新 | `SlotOverbookedFlagSetEvent` | `setOverbooked` 成功 | `slotId`、`overbooked_flag` | 监控、拦截策略 |

### 5.2 事件触发规则

| 当前状态（摘要） | 操作 | 目标状态 / 结果 | 发布事件 |
|---|---|---|---|
| — | 批量生成 | 多条 `free` | `SlotBatchProvisionedEvent` |
| `free` / `busy` | 封锁 | `busy-unavailable` | `SlotBlockedEvent` |
| `busy-unavailable` | 解封 | `free` 或 `busy`（依是否仍有预约） | `SlotUnblockedEvent` |
| `free` 等可约 | 记一笔预约占用 | `busy`（或未满时保持策略口径） | `SlotBookedEvent` |
| `busy` 等 | 释放一笔预约 | `busy` 或 `free` | `SlotReleasedEvent` |
| `free` | 标暂定 | `busy-tentative` | `SlotTentativeMarkedEvent` |
| `busy-tentative` | 确认并记占用 | `busy` | `SlotTentativeConfirmedEvent` |
| 非差错 | 标录入差错 | `entered-in-error` | `SlotEnteredInErrorEvent` |
| 任意非差错 | 同步标识 | 同态 | `SlotIdentifiersSyncedEvent` |
| 任意非差错 | 更新覆盖快照 | 同态 | `SlotServiceOverrideUpdatedEvent` |
| 任意 | 设置超售标记 | 同态 | `SlotOverbookedFlagSetEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（`SlotDomainService`）

#### 6.1.1 批量生成号源时段（`provisionSlotBatch`）

- **业务意义**：在指定 `Schedule` 下按时间片批量插入 `Slot`，用于夜间滚动或排班发布后出号。
- **边界说明**：只校验 `Schedule` 已发布且未废弃（通过读端口或入参校验标记）；**不**创建 `Appointment`。
- **入参**：`scheduleId`、时段模板列表（每项含 `start`、`end`、`capacity` 可选、覆盖 JSON 可选）、幂等键（可选）。
- **出参**：新建 `SlotId` 列表或生成条数。
- **前置条件**：各时段 `[start,end)` 落在 `Schedule` 的规划窗口内；同一 `schedule_id` 下无重叠时段（或重叠策略显式允许时须单独 BR，默认**禁止**重叠）。
- **状态变化**：新建行均为 `status_code=free`，`booked_count=0`，`overbooked_flag=false`。
- **聚合内影响**：批量插入 `wkf_slot`；可同步写入 `wkf_slot_identifier`（若模板带外部号）。
- **事件输出**：`SlotBatchProvisionedEvent`。
- **失败语义**：`Schedule` 不可用、时间越界、重叠冲突时整批或按行失败（实现须约定原子粒度并在接口层声明）。

#### 6.1.2 封锁号源时段（`blockSlot`）

- **业务意义**：将时段标为 `busy-unavailable`（停诊、设备不可用等）。
- **边界说明**：不删除已有预约；若已有预约，是否允许封锁由 **BR-SLT-005** 与产品策略决定。
- **入参**：`slotId`、原因码（可选）。
- **出参**：—。
- **前置条件**：当前非 `entered-in-error`。
- **状态变化**：`-> busy-unavailable`。
- **聚合内影响**：更新 `status_code`；可写入 `comment_text`。
- **事件输出**：`SlotBlockedEvent`。
- **失败语义**：非法状态迁移时拒绝。

#### 6.1.3 解封号源时段（`unblockSlot`）

- **业务意义**：解除 `busy-unavailable`，恢复可约或可展示占用态。
- **边界说明**：不自动补建预约。
- **入参**：`slotId`。
- **出参**：—。
- **前置条件**：当前为 `busy-unavailable`。
- **状态变化**：若 `booked_count=0` → `free`；若 `booked_count>0` → `busy`（默认 **BR-SLT-008 (A)**）。
- **聚合内影响**：更新 `status_code`。
- **事件输出**：`SlotUnblockedEvent`。
- **失败语义**：状态不合法时拒绝。

#### 6.1.4 记预约占用（`allocateBooking`）

- **业务意义**：预约确认落库时增加 `booked_count` 并刷新 `status` / `overbooked_flag`。
- **边界说明**：**不**写入患者信息；患者与 `Appointment` 由应用服务编排；本方法仅在预约事务内被调用。
- **入参**：`slotId`、是否计为暂定（可选，默认否）、是否允许超售（可选策略位）。
- **出参**：更新后的 `booked_count`、`status_code`。
- **前置条件**：`status_code` 允许加约（`free` 或策略允许的 `busy` 未满）；非 `entered-in-error`、非 `busy-unavailable`（除非策略显式允许超售约入，默认禁止）。
- **状态变化**：`booked_count++`；满员或采用 (A) 口径时 `status_code=busy`；若超出 `capacity` 则 `overbooked_flag=true`。
- **聚合内影响**：更新主表计数与状态。
- **事件输出**：`SlotBookedEvent`；若 `overbooked_flag` 由 `false`→`true`，可再发 `SlotOverbookedFlagSetEvent`（实现二选一，文档允许同事务双事件）。
- **失败语义**：不可约、并发冲突（乐观锁版本号失败）时拒绝。

#### 6.1.5 释放预约占用（`releaseBooking`）

- **业务意义**：预约取消或迁出时递减 `booked_count` 并恢复空闲语义。
- **边界说明**：不删除 `Appointment` 行；仅处理占用计数。
- **入参**：`slotId`、释放笔数（默认 1）。
- **出参**：更新后的 `booked_count`、`status_code`。
- **前置条件**：`booked_count` ≥ 释放笔数；非 `entered-in-error`。
- **状态变化**：`booked_count` 递减；若降为 0 且非封锁 → `free`；若仍 >0 → `busy`（(A) 口径）。
- **聚合内影响**：更新主表；若此前 `overbooked_flag=true` 且计数已不大于 `capacity`，须清 `overbooked_flag=false`。
- **事件输出**：`SlotReleasedEvent`。
- **失败语义**：计数不足、状态非法时拒绝。

#### 6.1.6 标暂定占用（`markTentative`）

- **业务意义**：短时锁号，防止并发双约。
- **边界说明**：与分布式锁协作时，本状态表达业务层暂定语义。
- **入参**：`slotId`、超时时间（可选，由上层调度，本聚合可不存）。
- **出参**：—。
- **前置条件**：当前为 `free`；`booked_count=0`。
- **状态变化**：`free -> busy-tentative`。
- **聚合内影响**：更新 `status_code`。
- **事件输出**：`SlotTentativeMarkedEvent`。
- **失败语义**：状态不满足时拒绝。

#### 6.1.7 确认暂定（`confirmTentative`）

- **业务意义**：将暂定转为正式占用（等同一次 `allocateBooking` 与状态收敛）。
- **边界说明**：若与 `allocateBooking` 合并实现，对外仍暴露本方法以保持合同清晰。
- **入参**：`slotId`。
- **出参**：`booked_count`、`status_code`。
- **前置条件**：当前为 `busy-tentative`。
- **状态变化**：`booked_count++`；`busy-tentative -> busy`（或未满策略，见 **BR-SLT-009**）。
- **聚合内影响**：更新计数与状态。
- **事件输出**：`SlotTentativeConfirmedEvent`。
- **失败语义**：并发下状态已变时拒绝。

#### 6.1.8 标录入差错（`markEnteredInError`）

- **业务意义**：作废错误生成的时段，不参与预约。
- **边界说明**：不级联删除历史预约（由 Appointment 域处理）；若存在预约，默认拒绝（见 **BR-SLT-006**）。
- **入参**：`slotId`、原因（可选）。
- **出参**：—。
- **前置条件**：`booked_count=0` 且无未完结暂定锁；或具备管理员强制位（若允许强制，须在审计中记录）。
- **状态变化**：`-> entered-in-error`。
- **聚合内影响**：更新 `status_code`。
- **事件输出**：`SlotEnteredInErrorEvent`。
- **失败语义**：存在占用且未授权强制时拒绝。

#### 6.1.9 同步外部标识（`syncSlotIdentifiers`）

- **业务意义**：维护 `identifier` 子表，与外部日历/排班键对齐。
- **边界说明**：仅子表变更。
- **入参**：`slotId`、标识列表。
- **出参**：—。
- **前置条件**：非 `entered-in-error`（或策略允许纠错期修改）。
- **状态变化**：`N/A`。
- **聚合内影响**：覆盖/对齐 `wkf_slot_identifier`。
- **事件输出**：`SlotIdentifiersSyncedEvent`。
- **失败语义**：重复 `system+value` 时拒绝。

#### 6.1.10 更新时段服务覆盖快照（`updateOverrideServiceSnapshots`）

- **业务意义**：写入本时段对 `serviceCategory` / `serviceType` / `specialty` / `appointmentType` 的覆盖，**覆盖**同字段在 `Schedule` 上的检索语义（对齐 FHIR「若提供则覆盖 Schedule」）。
- **边界说明**：不修改 `start`/`end`（改时应走纠错流程或删除重建）。
- **入参**：`slotId`、四类 JSON（可空表示清除覆盖）。
- **出参**：—。
- **前置条件**：非 `entered-in-error`。
- **状态变化**：`N/A`。
- **聚合内影响**：更新 JSON 列。
- **事件输出**：`SlotServiceOverrideUpdatedEvent`。
- **失败语义**：JSON 非法时拒绝。

#### 6.1.11 设置超售标记（`setOverbooked`）

- **业务意义**：显式维护 `overbooked_flag`，与 FHIR `overbooked` 对齐。
- **边界说明**：可与 `allocateBooking` 内聚更新二选一，对外保留本方法便于运营干预。
- **入参**：`slotId`、`overbooked`。
- **出参**：—。
- **前置条件**：非 `entered-in-error`。
- **状态变化**：`N/A`。
- **聚合内影响**：更新布尔字段。
- **事件输出**：`SlotOverbookedFlagSetEvent`。
- **失败语义**：—。

#### 6.1.12 更新备注（`updateSlotComment`）

- **业务意义**：维护 `comment_text`。
- **边界说明**：纯文本更新。
- **入参**：`slotId`、`comment`。
- **出参**：—。
- **前置条件**：非 `entered-in-error`。
- **状态变化**：`N/A`。
- **聚合内影响**：更新备注列。
- **事件输出**：无（若需审计可配置映射到 `SlotServiceOverrideUpdatedEvent`，不推荐默认耦合）。
- **失败语义**：超长拒绝。

### 6.2 读侧查询服务（`SlotQueryService`）

#### 6.2.1 按标识加载号源时段（`loadSlotById`）

- **业务意义**：详情、预约前单条加载。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_slot` + `wkf_slot_identifier`。
- **过滤口径**：默认排除 `entered-in-error`（除非管理端显式开关）。
- **组装规则**：主表 + 标识列表。
- **返回约束**：不存在时按全局规范返回空或异常。

#### 6.2.2 按日程与时间范围查询时段（`findSlotsByScheduleAndTimeRange`）

- **业务意义**：号源列表、日历视图数据源。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_slot`。
- **过滤口径**：`schedule_id` + `[start,end)` 时间窗相交；可选 `status_code` 列表。
- **组装规则**：按 `start_time` 升序。
- **返回约束**：分页参数必填上限。

#### 6.2.3 检索可预约时段（`findBookableSlots`）

- **业务意义**：挂号/预约 UI 的「可约号源」列表。
- **边界说明**：只读查询，不写库，不做跨域编排；**不**联接患者数据。
- **查询范围（读库，无写入）**：`wkf_slot`（必要时 JOIN `wkf_schedule` 做机构/服务过滤）。
- **过滤口径**：`status_code in (free)` 或策略允许的 `busy` 且 `booked_count < capacity`（若采用 **(B)** 口径须改过滤条件）；`busy-unavailable` / `entered-in-error` 排除；时间下限 ≥ 当前时刻（机构时区）。
- **组装规则**：返回时段主数据 + 是否已满（衍生布尔）。
- **返回约束**：结果须可映射到唯一 `slotId` 供预约提交引用。

#### 6.2.4 按机构与日期分页检索时段（`pageSlotsByOrganizationAndDate`）

- **业务意义**：运营监控大屏、院区总览。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_slot`。
- **过滤口径**：`organization_id` + 日期（与 `start_time` 落在同日）+ 可选状态。
- **组装规则**：按 `start_time` 升序分页。
- **返回约束**：大数据量须走分页游标或限制最大跨度。

### 6.3 领域边界说明

- 本聚合维护 **`Slot` 行及其标识子表**；**不**实现「复杂多资源自动凑齐」的全局排程求解（属应用编排）。
- **预约主数据、患者隐私** 不在本聚合；`allocateBooking` / `releaseBooking` 须由 **`Appointment` 应用服务在同一事务域内编排**，保证计数与预约一致。
- **纯统计类跨机构报表** 不新增为本查询服务方法，归QueryService或报表域（符合跨聚合组合查询约束）。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-SLT-001 | `end_time` > `start_time` | 时段长度为正当 |
| BR-SLT-002 | `schedule_id` 必须引用已存在且非 `retired` 的 `Schedule` | 外键 + 生命周期 |
| BR-SLT-003 | `organization_id` 必须与所引用 `Schedule.organization_id` 一致 | 冗余一致性 |
| BR-SLT-004 | `status_code` 必须属于 FHIR `SlotStatus` 值集 | 术语合法 |
| BR-SLT-005 | `busy-unavailable` 下默认禁止 `allocateBooking` | 封锁不可约，除非显式策略扩展 |
| BR-SLT-006 | `entered-in-error` 禁止作为新预约目标 | 差错态隔离 |
| BR-SLT-007 | `0 <= booked_count <= capacity` 为常态；`booked_count > capacity` 时必须 `overbooked_flag=true` | 超售显式化 |
| BR-SLT-008 | 采用 (A) 口径时：若 `booked_count>=1` 则 `status_code` 不得为 `free` | 与 FHIR busy 示例一致 |
| BR-SLT-009 | `busy-tentative` 是否占用 `booked_count` 由实现选定并在全院统一；**默认不占**（计数仍为 0） | 避免与 `allocateBooking` 双计数 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 / 资源 | 关联方式 | 说明 |
|---|---|---|---|
| 号源日程 | Schedule（`DM-WKF-009`） | `schedule_id` | 时段挂靠容器 |
| 组织架构 | Organization | `organization_id` | 冗余检索维度 |

### 8.2 下游影响

| 下游上下文 | 触发方式 | 说明 |
|---|---|---|
| 预约（Appointment） | 应用编排 + 事件 | 预约创建/取消驱动占用；读侧组合 `SlotQueryService` |
| 检索 / 缓存 | 事件 | 批量生成或状态变更后失效可约列表缓存 |
| 对外集成（iCalendar 等） | 转换层 | FHIR 注记建议可选 iCalendar 表达，属适配层而非本聚合写模型 |

### 8.3 引用规则

> **统一规则**：跨上下文传递使用 `SlotId`（`Long`）；禁止绕过领域服务直接写 `wkf_slot*`；`Appointment` 仅持有 `SlotId` 引用，不反查患者字段于本表。

### 8.4 事件流示意图

```
Schedule（已发布）
    │
    └──> SlotDomainService.provisionSlotBatch ──> SlotBatchProvisionedEvent
              │
预约应用服务（编排）
    │
    ├──> allocateBooking ──> SlotBookedEvent ──> 候诊/支付联动
    │
    └──> releaseBooking ──> SlotReleasedEvent ──> 号源回池

运营停诊
    │
    └──> blockSlot ──> SlotBlockedEvent ──> 预约拦截策略
```

---

## 9. 补充

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：`§6.1/§6.2` 与 Java DomainService/QueryService 白名单对齐 |
| v1.0 | — | 初版 |
