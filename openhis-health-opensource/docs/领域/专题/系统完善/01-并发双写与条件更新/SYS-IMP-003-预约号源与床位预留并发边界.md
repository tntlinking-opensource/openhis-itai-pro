# SYS-IMP-003 预约、号源与床位预留并发边界

> **问题类型**：并发双写与条件更新 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`BookingRegisterAppService`、`ChannelBookingAppService`、`BedBookingAppService`、`SlotDomainService`、`AppointmentQueryService`）。
>
> **关联 AS**：`DM-WKF-002`（Appointment/Slot）、门诊挂号 `AS-OP-FIN-001`（预约转挂号链路）
>
> **关联**：`SYS-IMP-002` GAP-FIN-006（当日同科室挂号防重 TOCTOU，与预约防重同族）
>
> **状态**：**已实施**（2026-06-28：GAP-BKG-001～007 全部落地）

---

## 1. 背景与结论摘要

ZXPlat 预约模块（`zxplat-module-booking`）采用 **「读 Gate → 占 Slot → 建 Appointment」** 同事务编排。与收费、发药问题同族，但 **Slot 聚合根已映射 `@Version`**，占号写路径具备乐观锁，**单号源超卖风险低于 ChargeItem/Account**。

| 阶段 | 行为 | 并发防护 |
| --- | --- | --- |
| 门诊预约/改约/渠道预约 | 读 Gate → `allocateBooking` → `create` | **DB 部分唯一**（`uk_wkf_appt_patient_service_day`）+ 读 Gate；占号 **Slot `@Version` + `SLOT_003`** |
| 候补入队/提升 | 不占号 / 释放后提升 | **slot 乐观锁** + `markPromotedIfWaiting` |
| 床位预留 | 读 Gate → `reserveBed` + 建 Appointment | **DB 唯一** + health Location 行锁（GAP-BKG-003/006） |

**结论**（2026-06-28 实施后；2026-08-11 补齐退号口径）：

- **号源容量**：Slot 乐观锁 + `assertBookable`；冲突映射 `SLOT_003`。
- **业务重复（同日同患者同服务双预约、同床双预留）**：DB 部分唯一 + 读 Gate 双保险。
- **退号后再挂**：部分唯一索引仅覆盖 `is_deleted=false`；IF-REG-10 默认须**软删**预约行，否则撞 `uk_wkf_appt_patient_service_day`（见 GAP-BKG-001 v1.2 / booking 概要 §8.3）。
- **预约 ↔ 分床**：booking 预留与 health 分床已联动（GAP-BKG-006 / GAP-ATD-007）。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 影响面 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-BKG-001 | 同日同患者同服务防重 TOCTOU | 中危 | 门诊预约 | P1 | **已实施** |
| GAP-BKG-002 | 末号源双请求（占号竞态） | 低危～中危 | 门+渠道 | P2 | **已实施** |
| GAP-BKG-003 | 床位预留同日同床防重 TOCTOU | 中危 | 住院预约 | P1 | **已实施** |
| GAP-BKG-004 | 虚拟号源 `virtualSlotKey` 物化竞态 | 中危 | 门诊预约 | P2 | **已实施** |
| GAP-BKG-005 | 候补提升与取消/改约释放竞态 | 低危～中危 | 门诊 | P2 | **已实施** |
| GAP-BKG-006 | booking 床位预留 vs health 分床占床未联动 | 中危 | 住 | P2 | **已实施** |
| GAP-BKG-007 | `Appointment` 无 `@Version` / 无自然键唯一 | 低危 | 门+住 | P3 | **已实施** |

---

## 3. 分项：问题与修改方案

### GAP-BKG-001 同日同患者同服务防重 TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `BookingRegisterAppService#createOutpatientBooking`；改约/候补/渠道创建同路径 |
| **现象** | 两请求几乎同时预约：均通过 `findDuplicateOutpatientBooking`，各占不同 slot、各建 Appointment。 |
| **根因** | 防重在事务开头**只读**；`wkf_appointment` 无 `(patient_id, healthcare_service_id, booking_date)` 部分唯一约束。 |
| **现有兜底** | 第二次若发生在第一次提交后，读 Gate 可拦；无 DB 硬约束则不可靠。 |
| **业务影响** | 同患者同日同科/同服务双预约；到诊占号混乱；与挂号防重（GAP-FIN-006）叠加。 |

**修改方案（推荐组合）**

1. **DB 部分唯一索引**（PostgreSQL，实施前须存量清洗）：

```sql
-- 示意：有效门诊预约 + 同 startInstant 日期 + 同 healthcareServiceId（经 slot→schedule 解析或冗余列）
CREATE UNIQUE INDEX uk_wkf_appointment_patient_service_day
ON wkf_appointment (patient_id, healthcare_service_id, (start_instant::date))
WHERE is_deleted = false
  AND appointment_kind = 'OUTPATIENT_BOOKING'
  AND status NOT IN ('CANCELLED', 'ENTERED_IN_ERROR');
```

2. **冗余列（可选）**：创建 Appointment 时写入 `healthcare_service_id`、`booking_date`，避免唯一索引依赖 join。
3. **幂等键**：渠道/小程序预约接口支持 `Idempotency-Key`，同 key 返回同一 `appointmentId`。

**涉及代码**

- `booking-application/.../register/BookingRegisterAppService.java`
- `booking-application/.../channel/ChannelBookingAppService.java`
- `booking-infrastructure/.../db/migration/`（Flyway）
- `booking-domain/.../appointment/entity/Appointment.java`

**验收用例**

- 两线程：同 patient + 同 service + 同日，不同 slotId → 仅 1 条 Appointment。
- 改约：排除自身 id 后仍满足防重。

**工作量**：2～3 人天

---

### GAP-BKG-002 末号源双请求（占号竞态）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危（Slot 层相对成熟） |
| **现象** | 容量=1 的 slot，两请求同时 `allocateBooking`；一个成功，一个 `OptimisticLockingFailureException` 或「号源已满」。 |
| **根因** | JPA 乐观锁重试未统一封装；App 层可能未映射为业务码。 |
| **现有兜底** | `Slot.increaseBookedCountAndRefreshStatus` → `assertBookable` 在实体态校验；`@Version` 冲突时第二笔失败。 |
| **修改方案** | ① `SlotDomainService#allocateBooking` 捕获 `OptimisticLockingFailureException` → `SLOT_CONFLICT` 可理解提示；② 可选 `@Retryable`（有限次数）；③ 禁止在无 `allowOverbook` 时静默吞异常。 |
| **工作量** | 1 人天 |

---

### GAP-BKG-003 床位预留同日同床防重 TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `BedBookingAppService#reserveBed`；`findActiveBedBookingByBedAndDate` |
| **现象** | 两窗口对**同一 bedLocationId + intendedAdmissionDate** 同时预留；均通过读 Gate，各建 Appointment + BedReservationExt。 |
| **根因** | 读 Gate + 无 DB 唯一约束；`bookingBedPort.reserveBed` 与 health `Location` 运营态**未同事务**。 |
| **业务影响** | 同床双预留；入院登记时冲突；与 GAP-ATD-001（分床占床）交叉。 |

**修改方案**

1. **DB 唯一**：`bed_reservation_ext (location_id, intended_admission_date)` 部分唯一（有效预约未取消）。
2. **占床前置锁**：`reserveBed` 时对 health `Location` 调用 `SELECT FOR UPDATE` 或条件 `UPDATE operational_enum`（需跨模块 Client，见 GAP-BKG-006）。
3. **确认入院** `confirmAdmission` 与住院登记链路幂等。

**涉及代码**

- `booking-application/.../bed/BedBookingAppService.java`
- `booking-domain/.../bed/entity/BedReservationExt.java`
- `booking-bridge/.../port/BookingBedPortAdapter.java`

**验收用例**

- 同床同日双预留：仅 1 成功。
- 预留后 health 侧分床同一床：第二操作 EX-IP-ATD-201 或预约冲突。

**工作量**：3～4 人天（含跨模块 Client）

---

### GAP-BKG-004 虚拟号源物化竞态

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | `virtualSlotKey` 路径先 `materializeVirtualSlot` 再占号；两请求可能物化出**两个** slot 或重复物化失败。 |
| **修改方案** | 物化层对 `(scheduleId, startTime, endTime)` 或 virtualKey 哈希加 **唯一约束 + INSERT ON CONFLICT**；物化与 `allocateBooking` 保持同事务。 |
| **工作量** | 2 人天 |

---

### GAP-BKG-005 候补提升与释放竞态

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **现象** | 取消预约释放 slot 同时 `tryPromoteWaitlist`；两候补提升或提升与直接预约并发。 |
| **修改方案** | 候补队首提升时对 slot 走与 GAP-BKG-002 相同锁；队列表对 `(slotId, queueNo)` 条件更新；失败则跳过本次提升。 |
| **状态** | 待评估（视候补业务量） |

---

### GAP-BKG-006 booking 预留 vs health 分床未联动

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `SYS-IMP-004` GAP-ATD-001 |
| **现象** | booking 标记床位 RESERVED；health `NurseStationATDManagerAppService` 分床仅查 `operational_enum`，**不知** booking 预留；反之预留时未锁 health Location。 |
| **修改方案** | 统一床位运营态权威：**health Location 为唯一真相**；预留时调用 `LocationDomainService#reserveBed`；分床前查 booking 有效预留并校验 patient 一致；或预留成功写入 Location 扩展标记。 |
| **工作量** | 4～5 人天（跨模块） |

---

### GAP-BKG-007 Appointment 乐观锁

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危 |
| **现象** | 改约/取消并发写 Appointment 可能 last-write-wins。 |
| **修改方案** | `Appointment` 映射 `@Version`；改约/取消用条件状态更新。 |

---

## 4. 边界问题（非并发）

| 编号 | 主题 | 说明 |
| --- | --- | --- |
| BND-BKG-001 | 预约未到诊 | 占号不释放策略与停诊迁移（`StopClinicImpactService`）需联调 |
| BND-BKG-002 | 预约转挂号 | `OutpatientRegistrationAppService#runBookingRegistrationGate` 与 slot 状态一致性 |
| BND-BKG-003 | 超售标记 | `overbookedFlag=true` 时运维与报表口径 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-BKG-001 | 同日同服务 DB 唯一 + 幂等键 | 2～3 |
| 2 | GAP-BKG-003 + 006 | 床位预留唯一 + 与 Location 联动 | 4～5 |
| 3 | GAP-BKG-002 | Slot 乐观锁异常业务化 | 1 |
| 4 | GAP-BKG-004 | 虚拟号源物化唯一 | 2 |
| 5 | GAP-BKG-005、007 | 按业务量 | 2～3 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅依赖 `findDuplicateOutpatientBooking` 读 Gate | 挡不住并发双预约 |
| 在 QueryService 写 Slot/Appointment | 违反 booking 分层 |
| 去掉 Slot `@Version` | 会重新引入号源超卖 |
| booking 与 health 各维护一套床态 | GAP-BKG-006 根因 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 同 patient/service/日 双预约 | 仅 1 成功 |
| T2 | 容量=1 slot 双占号 | 1 成功，1 号源已满或冲突 |
| T3 | 同床同日双预留 | 仅 1 成功 |
| T4 | 预留后 health 分同一床 | 明确失败 |
| T5 | 改约到新 slot（末号源） | 旧释放、新占用原子 |
| T6 | virtualSlotKey 双物化 | 单 slot 记录 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：预约号源与床位预留并发/边界审查 |
| v1.1 | 2026-06-28 | GAP-BKG-001～007 全部落地；状态 → 已实施 |
| v1.2 | 2026-08-11 | 结论补：退号须软删预约行，否则撞 `uk_wkf_appt_patient_service_day`（GAP-BKG-001 v1.2） |
