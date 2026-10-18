# 体检域 · 预约衔接 · 应用服务设计（AS）

> **编码**：AS-PEIS-BKG-001 · **版本**：v1.1 · **日期**：2026-06-27  
> **类名**：`PeisBookingBridgeAppService` · **触发**：booking H 线到检事件 / REG 读预约 · **无独立菜单**

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 消费 booking **到检签到**；创建或激活 `Encounter(PE)`；预约号 ↔ 体检号互查 |
| **在外** | 预约占用、Slot 放号、导检队列写侧 → **booking H 线**；登记 UI → REG-001 |
| **涉及域** | Encounter(PE)、`peis_encounter_ext`、ServiceRequest（到检新建时） |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **booking** | 到检签到成功后调用 BKG-01（或发事件） |
| **登记员** | REG 页按预约号/体检号查询（BKG-02） |
| **系统** | 回写 `appointment.encounterId` |

### 1.3 术语与口径

| 术语 | 口径 |
|------|------|
| **体检号源** | booking `HealthcareService.category = PHYSICAL_EXAM`，与门诊 Slot **分池** |
| **到检** | booking 9.9 签到 → BKG-01；非门诊挂号转挂 |
| **新建 vs 激活** | 无 ext 关联 → `registerFromAppointment`；已有 ext → `activateEncounterFromCheckIn` |
| **BKG-03** | 导引单打印 → 委托 **report**（00 §3.1），不在本 AS |

### 1.4 范围

| P1 | BKG-01、BKG-02 |
| 非目标 | BKG-03 导引单；booking 团检批次维护（GRP/booking）；门诊 O/D 线预约 |

### 1.5 领域清单

| 聚合 / Client | 职责 | DM / 文档 |
|-------------|------|-----------|
| Encounter(PE) | 到检创建/激活 | DM-ADM-002 §4.1 |
| PeisEncounterExt | `booking_appointment_id` 关联 | DM-PEIS-007 |
| PeisRegistrationDomainService | 到检登记编排 | DM-PEIS-007 §8 |
| PeisBookingAppointmentPort | 读预约快照、回写 encounterId | booking-contracts（H 线） |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-BKG-01** | 仅 **PHYSICAL_EXAM** 类别预约可触发 BKG-01 | BKG-01 |
| **BR-BKG-02** | 同一 `appointmentId` 幂等：已有 ext → 激活，禁止重复建 Encounter | BKG-01 |
| **BR-BKG-03** | 到检新建路径与 REG-02 等价展开 SR + ext（套餐来自预约快照） | BKG-01 |
| **BR-BKG-04** | `appointmentId` ↔ `busNo` 互查经 `PeisEncounterExtQueryService` | BKG-02 |
| **BR-BKG-05** | booking Client 回写失败不 rollback Encounter（补偿 P2） | BKG-01 |

---

## 2. 页面与接口总览（契约概览）

| IF | 用户动作 / 触发 | 接口语义 | 读写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
|----|-----------------|----------|:--:|----------|----------|----------|----------|
| **IF-PEIS-BKG-01** | booking 到检回调 | 创建或激活体检流水 | 写 | BR-BKG-01～03 | planned→in-progress 或新建 PE | Encounter、ext | PeisEncounterExtQueryService |
| **IF-PEIS-BKG-02** | 登记页互查 | 预约号/体检号查询 | 读 | BR-BKG-04 | — | ext | PeisEncounterExtQueryService |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（DM-PEIS-007 登记方法、`PeisEncounterExtQueryService#findByBusNo` / `#findByBookingAppointmentId` 契约已齐；`PeisBookingAppointment旧命名（已废止） 属 **booking-contracts** 外部集成，**不是** Gap）。

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-BKG-01 到检创建或激活体检

| 维度 | 说明 |
|------|------|
| **触发** | booking H 线 9.9 到检签到成功；携带 `appointmentId`、patientId、套餐 AD |
| **主路径** | 读预约快照 → 校验 PHYSICAL_EXAM → 查 ext 是否已关联 → 无则 `registerFromAppointment`；有则 `activateEncounterFromCheckIn` → 回写 booking |
| **分支** | 已有 Encounter 且已 finished → 拒绝 |
| **失败** | 预约不存在 / 类别错误 / 已结束 |
| **查询侧** | 步骤 2～3 只读 QueryService + Client |

### 4.2 IF-PEIS-BKG-02 预约号体检号互查

| 维度 | 说明 |
|------|------|
| **触发** | REG 或导检台输入预约号或体检 busNo |
| **主路径** | 二选一参数 → `findByBookingAppointmentId` 或 `findByBusNo` → 返回 encounterId、patientId、对端键 |
| **失败** | 两参数皆空；无匹配 |
| **查询侧** | 纯读，禁止直查 Repository |

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 | 可重试 |
|----|------|------|:--:|
| `APPOINTMENT_NOT_FOUND` | 预约不存在 | 预约无效 | 否 |
| `APPOINTMENT_CATEGORY_INVALID` | 非体检号源 | 非体检预约 | 否 |
| `ENCOUNTER_ALREADY_FINISHED` | 关联 Encounter 已结束 | 该预约体检已完成 | 否 |
| `LOOKUP_KEY_REQUIRED` | BKG-02 无键 | 请输入预约号或体检号 | 是 |
| `NOT_FOUND` | BKG-02 无匹配 | 未找到记录 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `PeisEncounterRegisteredEvent` | BKG-01 新建路径 | DES 待检列表（P1） |
| booking 到检事件（入站） | 签到成功 | 触发 BKG-01 |

---

## 7. 非功能性需求

| 项 | 承诺 |
|----|------|
| **幂等** | 同 appointmentId 重复到检仅激活一次 |
| **并发** | ext 上 appointment 唯一约束 |
| **审计** | BKG-01 写路径记 AuditLog |
| **一致性** | Encounter 创建与 booking 回写最终一致（回写失败补偿 P2） |

---

## 8. 测试要点（业务视角）

- PHYSICAL_EXAM 预约到检 → 新建 Encounter + ext + SR
- 重复到检 → 激活，不双 Encounter
- BKG-02 互查与 REG-03 同一 encounterId
- 门诊预约触发 BKG-01 → 拒绝（BR-BKG-01）
- booking 9.9 → BKG-01 → REG-03 可读详情

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-BKG-001-SDD](../AppService-SDD/AS-PEIS-BKG-001-预约衔接-SDD.md)
- REG：[AS-PEIS-REG-001](./AS-PEIS-REG-001-个检登记.md)
- booking：[booking 00 §4.9 H 线](../../../模块/zxplat-module-booking/00-功能清单.md)
- DM：[DM-PEIS-007](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补全 §1～§8；Gap 合规；号源 PHYSICAL_EXAM |
| v1.0 | 2026-06-27 | 首版 P1 骨架 |
