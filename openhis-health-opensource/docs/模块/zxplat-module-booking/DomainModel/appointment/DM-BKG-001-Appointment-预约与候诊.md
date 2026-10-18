# DM-BKG-001 Appointment 预约与候诊

> **限界上下文**：统一预约（Booking 插件）  
> **代码模块**：`zxplat-plugin/zxplat-module-booking/module-booking-domain`  
> **包**：`com.zxplat.plugin.booking.domain.appointment`  
> **聚合类型**：单表聚合根（`wkf_appointment_queue`）  
> **版本**：v1.1 · **日期**：2026-09-24 · **模式**：B（代码先行回写）  
> **写侧服务**：`AppointmentDomainService` · **读侧**：`AppointmentQueryService`

---

## 1. 概述

### 1.1 业务定义

**预约/候诊项（Appointment）** 表示患者在某机构下的门诊预约或现场候诊队列条目，可绑定号源（`slotId`）与就诊（`encounterId`）。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 创建 | 预约/候诊入队（批次须恰好一条） |
| 签到/关联就诊 | 门诊预约签到、候诊入队、链接/解除 Encounter |
| 队列调序 | 优待、过号、拖拽调序、叫号、NoShow |
| 改约/取消 | 改约、按 id/就诊取消（软删+原因） |

### 1.3 不做的事

- 不生成号源（见 DM-BKG-003 Slot / DM-BKG-002 Schedule）  
- 不写 HIS Encounter 事实（bridge/应用编排）  
- 不实现 HTTP

---

## 2. 聚合根与持久化

| 物理表 | 实体 |
| --- | --- |
| `wkf_appointment_queue` | `Appointment` |

终态：软删 `isDeleted=true` + 取消/过号原因字段。

---

## 3. 实体（摘要）

| 字段 | 说明 |
| --- | --- |
| `appointmentKind` | OUTPATIENT_BOOKING / CHECK_IN_ONLY |
| `patientId` / `encounterId` | 患者 / 就诊 |
| `organizationId` | 机构（必填） |
| `slotId` / `healthcareServiceId` | 号源与服务冗余 |
| `displayOrder` | 候诊显示序 |
| `busNo` | 业务号 |
| 审计/租户/软删/乐观锁 | 基类 + `@Version` |

完整字段以实体与 L1 卡为准。

---

## 4. 值对象 / 枚举

| 名称 | 说明 |
| --- | --- |
| `AppointmentKind` 常量 | `share.AppointmentKindConstants` |
| 嵌入来源 | `AppointmentOrigin` |

---

## 5. 领域事件

写侧发布（经 `IDomainEventPublisher`）：`AppointmentCreatedEvent`、`AppointmentCalledToSeeEvent`、`AppointmentNoShowEvent`、`PriorityTreatmentAppliedEvent`、`QueueOrderChangedEvent` 等（`com.zxplat.domain.share.events.workflow.appointment`）。

---

## 6. 领域服务

### 6.1 写侧 `AppointmentDomainService`

| 方法 | 命令 | 说明 |
| --- | --- | --- |
| `create` | `CreateAppointmentCommand` | 批次恰好一条 |
| `checkInToQueue` / `checkInOutpatientBooking` | 对应 Command | 签到入队 |
| `linkEncounter` / `releaseEncounterLink` | | 关联就诊 |
| `cancelAppointment` / `cancelByEncounterId` | | 取消 |
| `rescheduleOutpatientBooking` | | 改约 |
| `priorityTreatment` / `changeOver` / `dragChangeOrder` | | 队列调序 |
| `callToSee` / `noShow` | | 叫号 / 过号 |

### 6.2 读侧

`AppointmentQueryService`：分页/详情/队列查询。

---

## 7. 不变式

| 规则 ID | 描述 |
| --- | --- |
| BR-APT-001 | 同就诊有效队列项唯一（业务注释） |
| BR-APT-002 | 同机构同诊室 `displayOrder` 连续语义 |
| BR-APT-003 | 终态不参与待诊 |
| BR-APT-004 | 叫号/过号可追溯原因 |
| BR-APT-005 | 创建批次须恰好一条（`BatchCommand`） |
| BR-APT-006 | 调序不得越界 |

---

## 8. 上下文映射

| 方向 | 协作方 |
| --- | --- |
| 上游 | Slot / Schedule；Patient/Encounter（经 bridge 查询） |
| 下游 | 挂号 AS、分诊队列 AS、HIS ExtensionImpl |

---

## 9. 补充

| 日期 | 版本 | 说明 |
| --- | --- | --- |
| 2026-09-24 | v1.1 | M31 质量收口：Mode B 九章成稿 |
