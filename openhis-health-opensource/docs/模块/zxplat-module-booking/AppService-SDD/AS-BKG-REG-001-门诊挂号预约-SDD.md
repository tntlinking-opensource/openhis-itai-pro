# Booking · 门诊挂号预约 · AS-SDD

> **编码**：AS-BKG-REG-001-SDD · **对应 AS**：`../AppService/AS-BKG-REG-001-门诊挂号预约.md` · **日期**：2026-09-24

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 类 | `com.zxplat.plugin.booking.application.register.BookingRegisterAppService` |
| HTTP | `POST /api/app/booking-register/{methodName}` |
| 阶段 | 当前已上线范围 |

## 2. 依赖（摘要）

Appointment/Slot/Waitlist Domain+Query；主数据/患者 bridge 读端口。

## 3. 方法白名单

| IF | 方法 |
| --- | --- |
| 01 | `listBookableSlots` |
| 02 | `createOutpatientBooking` |
| 03 | `rescheduleBooking` |
| 04 | `cancelBooking` |
| 05 | `joinWaitlist` |
| 06 | `cancelWaitlist` |
| 07 | `pageBookings` |
| 08 | `loadBookingDetail` |
| 09 | `pagePatientsForSelect` |

Request/Response 以 `application.register.request|response|dto` 源码为准。

## 4. 禁止写 Gap

本 SDD 不列应用层未实现缺口。
