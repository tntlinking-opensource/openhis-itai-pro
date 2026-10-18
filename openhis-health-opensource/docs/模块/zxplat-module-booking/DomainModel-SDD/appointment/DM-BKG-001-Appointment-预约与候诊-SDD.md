# DM-BKG-001 Appointment · DomainModel-SDD

> **对应 DM**：`../DomainModel/appointment/DM-BKG-001-Appointment-预约与候诊.md`  
> **模块**：`module-booking-domain` · **包**：`com.zxplat.plugin.booking.domain.appointment`  
> **模式**：B · **版本**：v1.1 · **日期**：2026-09-24

## 1. 边界

- **在内**：`wkf_appointment_queue`、创建/签到/调序/叫号/改约/取消、事件发布  
- **在外**：HTTP、HIS Encounter 写、号源供给

## 2. 包结构

| 包 | 内容 |
| --- | --- |
| `entity` | `Appointment` |
| `command` | `CreateAppointmentCommand` 等 |
| `repository` | `AppointmentRepository` |
| `service` | `AppointmentDomainService`、`AppointmentQueryService` |
| `queries` / `queries.model` | 查询命令与模型 |

## 3. 技术口径

主键 `Long`；时间 `LocalDateTime`；软删 `isDeleted`；审计基类；乐观锁可选；事务在 DomainService 写方法。

## 4–5. 写/读白名单

写侧方法见 DM §6.1（一方法一 Command）。  
读侧：`AppointmentQueryService` 分页/详情/队列。

## 6. 不变式

对齐 DM §7 BR-APT-001～006；创建批次恰好一条已有单测。

## 7. 事件

经 `IDomainEventPublisher` 发布 `share.events.workflow.appointment.*`。

## 8. 缺口

无（Mode B 以现码为真源）。
