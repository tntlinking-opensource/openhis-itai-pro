# Booking · 门诊挂号预约 · 应用服务设计（AS）

> **编码**：AS-BKG-REG-001 · **版本**：v1.0 · **日期**：2026-09-24  
> **实现**：`BookingRegisterAppService` · **HTTP**：`POST /api/app/booking-register/{method}`  
> **HTTP/字段权威**：`../AppService-SDD/AS-BKG-REG-001-门诊挂号预约-SDD.md`

## 1. 场景概述

| 维度 | 说明 |
| --- | --- |
| **在内** | 可约号源列表、创建门诊预约、改约、取消、候补加入/取消、预约分页/详情、选患者分页 |
| **在外** | 排班发布与号源供给（AS-BKG-SCH-001 / AS-BKG-SLT-001）；体检预约（AS-BKG-HC-001） |
| **涉及域** | Appointment、Slot、Waitlist；Patient 读经 bridge |

## 2. 接口总览

| IF | 方法 | 读/写 | 领域 |
| --- | --- | --- | --- |
| IF-BKG-REG-01 | `listBookableSlots` | 读 | Slot |
| IF-BKG-REG-02 | `createOutpatientBooking` | 写 | Appointment + Slot |
| IF-BKG-REG-03 | `rescheduleBooking` | 写 | Appointment + Slot |
| IF-BKG-REG-04 | `cancelBooking` | 写 | Appointment + Slot |
| IF-BKG-REG-05 | `joinWaitlist` | 写 | Waitlist |
| IF-BKG-REG-06 | `cancelWaitlist` | 写 | Waitlist |
| IF-BKG-REG-07 | `pageBookings` | 读 | Appointment |
| IF-BKG-REG-08 | `loadBookingDetail` | 读 | Appointment |
| IF-BKG-REG-09 | `pagePatientsForSelect` | 读 | Patient（bridge） |

## 3. 缺口（Gap）

**无。** Gap 仅记领域层三类；应用层未另列 AS 视为未实现不算缺口。

## 4. 编排意图（摘要）

创建/改约：校验可约 → Slot 占号 → Appointment 创建/改绑定 → 事件。取消：释放 Slot → Appointment 软删。
