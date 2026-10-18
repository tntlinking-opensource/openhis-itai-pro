# DM-PEIS-007 PeisEncounterExt · 体检就诊扩展

> **表名**：`peis_encounter_ext`  
> **版本**：v1.2 · **日期**：2026-06-27  
> **关联**：`Encounter(class=PE)`、[booking H 线](../../../模块/zxplat-module-booking/00-功能清单.md)

---

## 1. 概述

一次 **`Encounter(PE)`** 的体检业务扩展：**个检**存套餐快照；**团检**存单位/合同/批次（团检字段放 ext；**号源**在 booking 侧 **`HealthcareService.category = PHYSICAL_EXAM`**）。

> **编排说明**：`registerPersonalCheckIn` 等登记编排见 **AS-PEIS-REG-001 / GRP-001 / BKG-001** §4，**不**在本聚合 DomainService 内实现。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| encounter_id | Long | PK/FK → `adm_encounter.id` |
| package_activity_id | Long | 登记所选套餐 AD |
| package_snapshot_json | Text | 登记时套餐/价格快照 |
| group_flag | Boolean | 是否团检 |
| company_id | Long | 团检单位 |
| contract_id | Long | 团检合同 |
| group_batch_id | Long | 团检批次/分组 |
| booking_appointment_id | Long | 可选 → booking 预约 |
| bus_no | String | 体检流水号（与 Encounter.bus_no 冗余索引） |
| tenant_id / org_id | Long | 租户/院区 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PeisEncounterExt`（1:1 `Encounter(PE)`） |
| **标识** | `encounter_id`（PK/FK） |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| encounterId | Long | 是 | PK/FK |
| packageActivityId | Long | 否 | 套餐 AD |
| packageSnapshotJson | String | 否 | 快照 JSON |
| groupFlag | Boolean | 是 | 团检标志 |
| companyId | Long | 条件 | groupFlag=true 必填 |
| contractId | Long | 否 | 合同 |
| groupBatchId | Long | 否 | 批次 |
| bookingAppointmentId | Long | 否 | 预约 |
| busNo | String | 否 | 流水号 |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `PeisEncounterExtCreatedEvent` | `encounterId, groupFlag, packageActivityId` | `createForRegistration` |
| `PeisEncounterPackageAdjustedEvent` | `encounterId` | `updatePackageSnapshot` |

包路径：`com.zxplat.domain.share.events.custom.peisencounterext`

---

## 6. 领域服务

### 6.1 写侧 — `PeisEncounterExtDomainService`

| 方法 | 说明 |
|------|------|
| `createForRegistration(CreatePeEncounterExtCommand)` | REG-02 / GRP-05 初始化 ext |
| `updatePackageSnapshot(UpdatePePackageSnapshotCommand)` | REG-04 加项减项后刷新快照 |

### 6.2 读侧 — `PeisEncounterExtQueryService`

| 接口签名 | 场景 |
|----------|------|
| `getByEncounterId(encounterId): PeisEncounterExtModel` | REG-03 |
| `findByBusNo(busNo): PeisEncounterExtModel` | BKG-02 |
| `findByBookingAppointmentId(appointmentId): PeisEncounterExtModel` | BKG-01 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PE-EXT-01** | 1 Encounter 1 行 ext |
| **BR-PE-EXT-02** | `group_flag=true` 时 `company_id` 必填 |
| **BR-PE-EXT-03** | 号源/scheduling **不**存本表 → booking Slot/HealthcareService |
| **BR-PE-EXT-04** | `bus_no` 与 Encounter.bus_no 登记时同步写入 |

---

## 8. 应用层编排（非本聚合 SDD）

以下方法由 **`health-application/peis`** 编排服务实现（类名可保留 `PeisRegistrationDomainService` 或 `PeisRegistrationOrchestrator`），**不**写入 `PeisEncounterExtDomainService` SDD：

| 方法 | 说明 | AS |
|------|------|-----|
| `registerPersonalCheckIn` | REG-02 | REG-001 |
| `registerFromAppointment` | BKG-01 | BKG-001 |
| `registerGroupCheckIn` | GRP-05 | GRP-001 |
| `activateEncounterFromCheckIn` | BKG-01 | BKG-001 |
| `adjustServiceRequests` | REG-04 | REG-001 |
| `addRecheckServiceRequests` | REC-02 | REC-001 |

编排写点：`EncounterDomainService` + 本 DM §6.1 + `ServiceRequestDomainService` + [DM-PEIS-001](./DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md) §6.1。

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | §8 登记编排归应用层；§5/§6.1 成 SDD 前置 |
| v1.1 | 2026-06-27 | 增补复查相关 AS 引用 |
| v1.0 | 2026-06-27 | 首版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PeisEncounterExtDomainService`

| 方法 |
|------|
| `createForRegistration` |
| `updatePackageSnapshot` |

### QueryService：`PeisEncounterExtQueryService`

| 方法 |
|------|
| `getByEncounterId` |
| `findOptionalByEncounterId` |
| `mapByEncounterIds` |
| `filterEncounterIdsByGroupFlag` |
| `findByBusNo` |
| `findByBookingAppointmentId` |

