# GAP-BKG-001 同日同患者同服务防重 TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：门诊预约
> **优先级**：P1
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-003](../01-并发双写与条件更新/SYS-IMP-003-预约号源与床位预留并发边界.md)
> **关联 IF/BR**：`BookingRegisterAppService#createOutpatientBooking`；改约/候补/渠道创建同路径

---

## 1. 问题描述

同日同患者同服务防重 TOCTOU

## 2. 发生场景

两请求几乎同时预约：均通过 `findDuplicateOutpatientBooking`，各占不同 slot、各建 Appointment。

## 3. 根因分析

防重在事务开头**只读**；`wkf_appointment` 无 `(patient_id, healthcare_service_id, booking_date)` 部分唯一约束。

## 4. 业务影响

同患者同日同科/同服务双预约；到诊占号混乱；与挂号防重（GAP-FIN-006）叠加。

## 5. 修改方案

**已采用**（2026-06-28）：

1. Flyway `V10__Outpatient_Booking_Dedup.sql`：冗余列 `healthcare_service_id` / `booking_date`（`Asia/Shanghai`）、存量清洗（保留 id 最小）、部分唯一索引 `uk_wkf_appt_patient_service_day`
2. `OutpatientBookingWritesupport：读 Gate + 占号/create；唯一冲突释号并映射 `APPOINTMENT_004`
3. `ChannelBookingAppService` 门诊创建补防重
4. 幂等键：本次未实施（评审结论：DB 唯一即可）


## 6. 涉及代码

- `booking-application/.../register/BookingRegisterAppService.java`
- `booking-application/.../channel/ChannelBookingAppService.java`
- `booking-infrastructure/.../db/migration/`（Flyway）
- `booking-domain/.../appointment/entity/Appointment.java`

## 7. 验收用例

- 两线程：同 patient + 同 service + 同日，不同 slotId → 仅 1 条 Appointment。
- 改约：排除自身 id 后仍满足防重。
- **退号后再挂（2026-08-11）**：IF-REG-10 默认策略下预约行须软删（`is_deleted=true`），同日同服务再挂成功；历史「仅解绑 encounter」残留由现场取号前清理。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | V10 唯一索引 + 冗余列 + 应用层冲突映射；状态 → 已实施 |
| v1.2 | 2026-08-11 | 补：部分唯一索引仅排除 `is_deleted=false`；退号须软删预约行，否则退号后再挂撞 `uk_wkf_appt_patient_service_day`；对齐 `OutpatientBookingWriterImpl#onRegistrationCancelled` |
