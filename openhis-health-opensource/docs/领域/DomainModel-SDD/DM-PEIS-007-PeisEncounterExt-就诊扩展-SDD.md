# DM-PEIS-007 PeisEncounterExt 就诊扩展 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-007-PeisEncounterExt-就诊扩展.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · Encounter(PE) 扩展  
> **聚合**：PeisEncounterExt

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PEIS-007 |
| 包 `com.zxplat.plugin.peis.domain.encounterext` | 满足 | |
| DM §6.1 ×2 | 满足 | 不含登记编排 |
| DM §5 ×2 事件 | 满足 | |
| peis V1 | 满足 | |

**与 DM 显式差异**

| 点 | DM | SDD 采纳 |
| -- | -- | -------- |
| 登记编排 | 旧版列 PeisRegistrationDomainService | **仅 AS-REG/GRP/BKG** 编排；本 SDD 仅 ext 聚合 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 1:1 扩展 `Encounter(PE)`：个检套餐快照 + 团检单位/批次 + booking 预约关联。
- PK=`encounterId`。

### 1.2 边界

- **在内**：`peis_encounter_ext`。
- **在外**：Encounter 创建、SR 展开在 AS + WKF。

---

## 2. 工程落点与包结构

### 2.2 包结构（`com.zxplat.plugin.peis.domain.encounterext`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisEncounterExt` |
| `command` | `CreatePeEncounterExtCommand`、`UpdatePePackageSnapshotCommand` |
| `repository` | `PeisEncounterExtRepository` |
| `service` | `PeisEncounterExtDomainService` |
| `queries` | `PeisEncounterExtQueryService` |
| `queries.model` | `PeisEncounterExtModel` |

**存储口径**：`groupFlag` → `Boolean`；`packageSnapshotJson` → `String`。

---

## 3. 类型 / 字段命名 / 注解规则

PK `encounterId`；`busNo` → `String` 索引。

---

## 4. 实体 / 仓储 / 查询契约

### 4.2 聚合根 `PeisEncounterExt`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `encounterId` | `Long` | 是 | PK/FK |
| `packageActivityId` | `Long` | 否 | 套餐 AD |
| `packageSnapshotJson` | `String` | 否 | 快照 |
| `groupFlag` | `Boolean` | 是 | 团检 |
| `companyId` | `Long` | 条件 | groupFlag=true |
| `contractId` | `Long` | 否 | |
| `groupBatchId` | `Long` | 否 | |
| `bookingAppointmentId` | `Long` | 否 | |
| `busNo` | `String` | 否 | |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体

无。

### 4.5 仓储

```java
public interface PeisEncounterExtRepository extends JpaRepository<PeisEncounterExt, Long> {
    Optional<PeisEncounterExt> findByBusNoAndIsDeletedFalse(String busNo);
    Optional<PeisEncounterExt> findByBookingAppointmentIdAndIsDeletedFalse(Long appointmentId);
}
```

### 4.6 查询模型

#### 4.6.1 `PeisEncounterExtModel`（get/find 系列）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `encounterId` | `Long` | ext | |
| `packageActivityId` | `Long` | ext | |
| `packageSnapshotJson` | `String` | ext | |
| `groupFlag` | `Boolean` | ext | |
| `companyId` | `Long` | ext | |
| `contractId` | `Long` | ext | |
| `groupBatchId` | `Long` | ext | |
| `bookingAppointmentId` | `Long` | ext | |
| `busNo` | `String` | ext | |

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `PeisEncounterExtDomainService`

#### 5.1.1 `createForRegistration(CreatePeEncounterExtCommand cmd)` → `PeisEncounterExt`

**命令文件**：`CreatePeEncounterExtCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `packageActivityId` | `Long` | 个检 |
| `packageSnapshotJson` | `String` | |
| `groupFlag` | `Boolean` | 必填 |
| `companyId` | `Long` | 团检必填 |
| `contractId` | `Long` | |
| `groupBatchId` | `Long` | |
| `bookingAppointmentId` | `Long` | |
| `busNo` | `String` | BR-PE-EXT-04 |
| `tenantId` | `Long` | |

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | BR-PE-EXT-01/02 |
| `eventPublishAnchor` | `PeisEncounterExtCreatedEvent` |
| `unitTestCaseId` | TC-PEIS-EXT-001 |

**方法契约摘要**：insert 一行；**注释锚点**：校验团检 → insert → 事件。

---

#### 5.1.2 `updatePackageSnapshot(UpdatePePackageSnapshotCommand cmd)` → `PeisEncounterExt`

**命令文件**：`UpdatePePackageSnapshotCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `packageActivityId` | `Long` | |
| `packageSnapshotJson` | `String` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `eventPublishAnchor` | `PeisEncounterPackageAdjustedEvent` |
| `unitTestCaseId` | TC-PEIS-EXT-002 |

---

### 5.2 读侧 — `PeisEncounterExtQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `getByEncounterId` | `Long encounterId` | `PeisEncounterExtModel` | TC-PEIS-EXT-R01 |
| `findByBusNo` | `String busNo` | `PeisEncounterExtModel` | TC-PEIS-EXT-R02 |
| `findByBookingAppointmentId` | `Long appointmentId` | `PeisEncounterExtModel` | TC-PEIS-EXT-R03 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-PE-EXT-01** | encounterId 唯一 |
| **BR-PE-EXT-02** | create 校验 companyId |
| **BR-PE-EXT-03** | 无号源字段 |
| **BR-PE-EXT-04** | busNo 同步写入 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 载荷 | 触发 |
| ---- | ------ | ---- | ---- |
| `PeisEncounterExtCreatedEvent` | `com.zxplat.domain.share.events.custom.peisencounterext` | `encounterId, groupFlag, packageActivityId` | createForRegistration |
| `PeisEncounterPackageAdjustedEvent` | 同上 | `encounterId` | updatePackageSnapshot |

---

## 8. 删除与可见性契约

- 软删 ext；Encounter 删除策略由 ADM 主导。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] PK encounterId
- [ ] 团检 company 校验单测
- [ ] findByBusNo / findByBookingAppointmentId 索引

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-007、peis V1、EncounterClass.PE

### 11.2 DoD

ext 读写编译通过

### 11.3 失败处理

PE 枚举未加 → 先 ADM

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 2
- [ ] 无 RegistrationDomainService

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（§1.4 PASS）** |

---

**本 SDD 正文结束。**
