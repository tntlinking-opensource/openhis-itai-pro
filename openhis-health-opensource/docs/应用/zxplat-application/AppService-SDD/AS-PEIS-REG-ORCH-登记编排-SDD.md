# AS-PEIS-REG-ORCH 体检登记编排 · 应用层 SDD

> **编码**：AS-PEIS-REG-ORCH · **版本**：v1.0 · **日期**：2026-06-27  
> **模块**：`zxplat/health-application/peis`  
> **权威 DM**：[DM-PEIS-007 §8](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md#8-应用层编排非本聚合-sdd)  
> **消费 AS**：REG-001、BKG-001、GRP-001、REC-001

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **定位** | 跨聚合 **登记编排**；**不是** `PeisEncounterExt` 领域聚合 SDD |
| **类名（推荐）** | `PeisRegistrationOrchestrator`（或历史名 `PeisRegistrationDomainService` 放 application 包，**禁止**与 zxplat-domain 实体服务混淆） |
| **包路径** | `com.zxplat.domain.application.peis.orchestration` |
| **事务** | 由 AppService 入口 `@AppService` 触发 UoW；Orchestrator **不**声明 `@Transactional` |

### 1.1 术语

| 口语 | 含义 |
|------|------|
| INT-01 | **本院** LisWorkItem/PacsWorkItem，登记后可由配置自动触发 |
| 外发 LIS | **金域** LisKingMed，LIS 侧 EXT，非本编排职责 |

---

## 2. 编排方法白名单

| 方法 | Command | 消费 AS / IF | 写库点（顺序） |
|------|---------|--------------|----------------|
| `registerPersonalCheckIn` | `RegisterPersonalCheckInCommand` | REG-02 | Encounter(PE) → PeisEncounterExt → SR 展开 → PeisServiceRequestExt.init |
| `registerFromAppointment` | `RegisterFromAppointmentCommand` | BKG-01 | 读 Reader 快照 → 同上 |
| `activateEncounterFromCheckIn` | `ActivatePeCheckInCommand` | BKG-01 | Encounter in-progress（已有 planned） |
| `registerGroupCheckIn` | `RegisterGroupCheckInCommand` | GRP-05 | ext.groupFlag=true + companyId |
| `adjustServiceRequests` | `AdjustPeServiceRequestsCommand` | REG-04 | SR 增删 + ext 快照 + srExt |
| `addRecheckServiceRequests` | `AddRecheckSrCommand` | REC-02 | 追加复查 SR + ext |

---

## 3. 依赖注入白名单

| 依赖 | 类型 | 用途 |
|------|------|------|
| `EncounterDomainService` | 领域 | 创建/激活 PE Encounter |
| `PeisEncounterExtDomainService` | 领域 | createForRegistration / updatePackageSnapshot |
| `PeisEncounterExtQueryService` | 查询 | 幂等/详情 |
| `ServiceRequestDomainService` | 领域 | 套餐展开 SR |
| `PeisServiceRequestExtDomainService` | 领域 | initForServiceRequests |
| `ActivityDefinitionQueryService` | 查询 | 套餐/项目校验 |
| `PeisConfigQueryService` | 查询 | auto_dispatch、bus_no 规则 |
| `PeisBookingAppointment旧命名（已废止） | Extension | BKG 预约快照/回写 |
| `PeisGroup`BookingClient`（规划） | Extension | GRP  roster 校验 |
| `PeisLisPacsDockAppService` | 应用 | 可选：登记后 INT-01（auto_dispatch） |

**禁止 Gap**：本层为应用编排，缺口仅当 **领域 DM 方法缺失** 时在对应 AS §3 登记。

---

## 4. 编排序列（目标态）

### 4.1 `registerPersonalCheckIn`

| 步骤 | 类型 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 租户/患者/套餐 | |
| 2 | Pre | AD 套餐 children 有效 | BR-CFG-03 |
| 3 | Cmd | `EncounterDomainService#create` class=**PE** | DM-ADM-002 §4.1 |
| 4 | Cmd | `PeisEncounterExtDomainService#createForRegistration` | DM-PEIS-007 §6.1 |
| 5 | Cmd | 展开 SR + `initForServiceRequests` | DM-WKF-004 + DM-PEIS-001 |
| 6 | Asm | 可选 `PeisBookingAppointmentPort#linkEncounter` | |
| 7 | Post | 若 `peis.reg.auto_dispatch` → INT-01 | **本院** WorkItem |

### 4.2 `registerFromAppointment`

步骤 1–2：Client 读快照；3 起同 4.1；必须 `HealthcareService.category=PHYSICAL_EXAM`。

### 4.3 `registerGroupCheckIn`

Pre：`PeisGroupBookingPort#isOnRoster`；ext 必填 `companyId`（BR-PE-EXT-02）。

### 4.4 `adjustServiceRequests`

REG-04 加项减项：diff SR → init 新 ext 行 → `updatePackageSnapshot`。

### 4.5 `addRecheckServiceRequests`

REC-02：追加 SR + ext；**不**新建 Encounter。

---

## 5. Command 字段摘要

### 5.1 `RegisterPersonalCheckInCommand`

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `patientId` | Long | 是 |
| `packageActivityId` | Long | 是 |
| `orgId` | Long | 是 |
| `bookingAppointmentId` | Long | 否 |
| `operatorId` | Long | 是 |

### 5.2 `AdjustPeServiceRequestsCommand`

| 字段 | 说明 |
|------|------|
| `encounterId` | 必填 |
| `addActivityIds` / `removeServiceRequestIds` | 至少一项非空 |

（其余 Command 字段与对应 AS-SDD Request 对齐，成码时以 REG/BKG/GRP/REC SDD 为 DTO 真源。）

---

## 6. 业务异常

| 码 | 场景 |
|----|------|
| `PE_ENCOUNTER_ALREADY_EXISTS` | 同预约重复登记 |
| `PACKAGE_INVALID` | 套餐无有效子项 |
| `GROUP_ROSTER_MISMATCH` | 团检不在名单 |
| `ENCOUNTER_CLASS_PE_MISSING` | 枚举 PE 未实现（成码前） |

---

## 7. 测试要点

- 个检：Encounter(PE) + ext + N 条 SR + N 条 srExt
- 预约：Client 回写 encounterId
- 团检：groupFlag + companyId
- REG-04：加项后 snapshot JSON 更新
- auto_dispatch：仅创建本院 WorkItem

---

## 8. 关联文档

| 文档 | 用途 |
|------|------|
| [AS-PEIS-REG-001-SDD](./AS-PEIS-REG-001-个检登记-SDD.md) | HTTP/DTO |
| [AS-PEIS-BKG-001-SDD](./AS-PEIS-BKG-001-预约衔接-SDD.md) | Extension |
| [AS-PEIS-GRP-001-SDD](./AS-PEIS-GRP-001-团检管理-SDD.md) | 团检 |
| [AS-PEIS-REC-001-SDD](./AS-PEIS-REC-001-复查管理-SDD.md) | 复查 |
| [07-成码前Checklist](../PeisAppService/07-成码前Checklist.md) | 门禁 |

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；统一 REG/BKG/GRP/REC 登记编排 |
