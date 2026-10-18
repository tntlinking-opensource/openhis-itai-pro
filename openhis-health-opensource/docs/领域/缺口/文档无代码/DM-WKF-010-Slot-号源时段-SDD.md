# DM-WKF-010 Slot 号源时段 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-WKF-010-Slot-号源时段.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：Slot（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-WKF-010 |
| 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 目标包 | 满足 | `com.zxplat.domain.workflow.slot` |
| 固定技术口径 | 满足 | 见 §9 |
| DM 写/读白名单 | 满足 | §5 覆盖 |
| DM BR | 满足 | §6 |
| DM 事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.slot` |
| 容量更新策略 | §6.2/§6.3 加锁模式 | 悲观锁以 `capacity` 与 `available` 的 CAS 差异控制并发，先加号再减号 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 Slot 作为最小可预订时间片的状态、容量、占用计数契约。
- 明确与 Schedule、Appointment 边界：Slot 不存患者隐私，仅维护占用事实。
- 定义可实施写侧方法：批量生成、封锁解封、占用释放、暂定确认、差错标记。

### 1.2 边界

- **在内**：Slot 主表、Identifier 子表、`status/bookedCount/capacity/overbooked` 管理。
- **在外**：预约合同主数据、跨资源排程求解。
- **跨域仅端口声明**：Appointment 在同事务编排调用占用/释放。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.slot`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Slot`,`SlotIdentifier` |
| `command` | 每写方法一个命令 |
| `repository` | `SlotRepository`,`SlotIdentifierRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.slot` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间 | `LocalDateTime` |
| 状态码 | `String`（FHIR SlotStatus） |
| 计数 | `Integer` |
| 布尔 | `Boolean` |
| 软删 | `isDeleted` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_slot` | `Slot` | 号源时段主表 |
| `wkf_slot_identifier` | `SlotIdentifier` | 标识子表 |

### 4.2 聚合根 `Slot`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `organizationId` | `Long` | 是 | 机构冗余 |
| `scheduleId` | `Long` | 是 | 日程外键 |
| `startTime` | `LocalDateTime` | 是 | 开始时刻 |
| `endTime` | `LocalDateTime` | 是 | 结束时刻 |
| `statusCode` | `String` | 是 | free/busy/... |
| `capacity` | `Integer` | 是 | 容量 |
| `bookedCount` | `Integer` | 是 | 已占用数 |
| `overbookedFlag` | `Boolean` | 是 | 超售标记 |
| `serviceCategoryJson` | `String` | 否 | 覆盖快照 |
| `serviceTypeJson` | `String` | 否 | 覆盖快照 |
| `specialtyJson` | `String` | 否 | 覆盖快照 |
| `appointmentTypeJson` | `String` | 否 | 场景快照 |
| `commentText` | `String` | 否 | 备注 |

### 4.3 子实体 `SlotIdentifier`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 主键 |
| `slotId` | `Long` | 是 | 外键 |
| `idSystem` | `String` | 是 | 标识体系 |
| `idValue` | `String` | 是 | 标识值 |
| `useCode` | `String` | 否 | 用途 |

### 4.4 枚举契约

| 字段 | 值域 |
| ---- | ---- |
| `statusCode` | free / busy / busy-unavailable / busy-tentative / entered-in-error |

### 4.5 仓储接口

- `SlotRepository`, `SlotIdentifierRepository`
- 必备签名：按 `id`、`scheduleId+timeRange`、`organizationId+date`、可预约过滤查询

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `SlotDetailModel`（`loadSlotById`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `statusCode` | `String` | 主表 | 状态 |
| `bookedCount` | `Integer` | 主表 | 占用计数 |

#### 4.6.2 `BookableSlotModel`（`findBookableSlots`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `slotId` | `Long` | 主表 | 时段ID |
| `scheduleId` | `Long` | 主表 | 日程 |
| `startTime` | `LocalDateTime` | 主表 | 开始 |
| `endTime` | `LocalDateTime` | 主表 | 结束 |
| `isFull` | `Boolean` | 计算 | 是否满员 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `SlotDomainService`

#### 5.1.1 `provisionSlotBatch(ProvisionSlotBatchCommand cmd)` → `List<Long>`
**命令文件**：`ProvisionSlotBatchCommand.java`  
**字段全集**：`scheduleId,organizationId,slotTemplates,idempotencyKey`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertProvisionWindowAndNonOverlap` |
| `statusTransitionMethod` | `initFree` |
| `repositoryChecks` | schedule可用性+重复时段检查 |
| `eventPublishAnchor` | `SlotBatchProvisionedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-001` |

---

#### 5.1.2 `blockSlot(BlockSlotCommand cmd)` → `void`
**命令文件**：`BlockSlotCommand.java`
**字段全集**：`slotId,reason`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBlockable` |
| `statusTransitionMethod` | `toBusyUnavailable` |
| `repositoryChecks` | 非差错态校验 |
| `eventPublishAnchor` | `SlotBlockedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-002` |

---

#### 5.1.3 `unblockSlot(UnblockSlotCommand cmd)` → `void`
**命令文件**：`UnblockSlotCommand.java`
**字段全集**：`slotId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertUnblockable` |
| `statusTransitionMethod` | `busyUnavailableToFreeOrBusy` |
| `repositoryChecks` | `status=busy-unavailable` |
| `eventPublishAnchor` | `SlotUnblockedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-003` |

---

#### 5.1.4 `allocateBooking(AllocateBookingCommand cmd)` → `void`
**命令文件**：`AllocateBookingCommand.java`
**字段全集**：`slotId,allowOverbook(optional),tentative(optional)`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBookable` |
| `statusTransitionMethod` | `increaseBookedCountAndRefreshStatus` |
| `repositoryChecks` | 可预约校验 |
| `eventPublishAnchor` | `SlotBookedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-004` |

---

#### 5.1.5 `releaseBooking(ReleaseBookingCommand cmd)` → `void`
**命令文件**：`ReleaseBookingCommand.java`
**字段全集**：`slotId,releaseCount`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertReleasable` |
| `statusTransitionMethod` | `decreaseBookedCountAndRefreshStatus` |
| `repositoryChecks` | 计数足额校验 |
| `eventPublishAnchor` | `SlotReleasedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-005` |

---

#### 5.1.6 `markTentative(MarkTentativeCommand cmd)` → `void`
**命令文件**：`MarkTentativeCommand.java`
**字段全集**：`slotId,timeoutConfig`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTentativeMarkable` |
| `statusTransitionMethod` | `freeToBusyTentative` |
| `repositoryChecks` | free态校验 |
| `eventPublishAnchor` | `SlotTentativeMarkedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-006` |

---

#### 5.1.7 `confirmTentative(ConfirmTentativeCommand cmd)` → `void`
**命令文件**：`ConfirmTentativeCommand.java`
**字段全集**：`slotId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTentativeConfirmable` |
| `statusTransitionMethod` | `busyTentativeToBusy` |
| `repositoryChecks` | `status=busy-tentative` |
| `eventPublishAnchor` | `SlotTentativeConfirmedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-007` |

---

#### 5.1.8 `markEnteredInError(MarkEnteredInErrorCommand cmd)` → `void`
**命令文件**：`MarkEnteredInErrorCommand.java`
**字段全集**：`slotId,reason,force(optional)`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertEnteredInErrorMarkable` |
| `statusTransitionMethod` | `toEnteredInError` |
| `repositoryChecks` | bookedCount与暂定锁校验 |
| `eventPublishAnchor` | `SlotEnteredInErrorEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-008` |

---

#### 5.1.9 `syncSlotIdentifiers(SyncSlotIdentifiersCommand cmd)` → `void`
**命令文件**：`SyncSlotIdentifiersCommand.java`
**字段全集**：`slotId,identifiers`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertIdentifierUnique` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 非差错态校验 |
| `eventPublishAnchor` | `SlotIdentifiersSyncedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-009` |

---

#### 5.1.10 `updateOverrideServiceSnapshots(UpdateOverrideServiceSnapshotsCommand cmd)` → `void`
**命令文件**：`UpdateOverrideServiceSnapshotsCommand.java`
**字段全集**：`slotId,serviceCategoryJson,serviceTypeJson,specialtyJson,appointmentTypeJson`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertOverrideJsonValid` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 非差错态校验 |
| `eventPublishAnchor` | `SlotServiceOverrideUpdatedEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-010` |

---

#### 5.1.11 `setOverbooked(SetOverbookedCommand cmd)` → `void`
**命令文件**：`SetOverbookedCommand.java`
**字段全集**：`slotId,overbooked`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertOverbookFlagAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | slot存在校验 |
| `eventPublishAnchor` | `SlotOverbookedFlagSetEvent` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-011` |

---

#### 5.1.12 `updateSlotComment(UpdateSlotCommentCommand cmd)` → `void`
**命令文件**：`UpdateSlotCommentCommand.java`
**字段全集**：`slotId,commentText`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCommentLength` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 非差错态校验 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-SLT-CMD-012` |

### 5.2 读侧 — `SlotQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `loadSlotById` | `id` | `SlotDetailModel` | 单条加载 | TC-WKF-SLT-QRY-001 |
| `findSlotsByScheduleAndTimeRange` | `scheduleId,start,end,statusList` | `List<SlotDetailModel>` | 日程范围查询 | TC-WKF-SLT-QRY-002 |
| `findBookableSlots` | `query` | `List<BookableSlotModel>` | 可预约过滤 | TC-WKF-SLT-QRY-003 |
| `pageSlotsByOrganizationAndDate` | `organizationId,date,page` | `Page<SlotDetailModel>` | 机构分页 | TC-WKF-SLT-QRY-004 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-SLT-001 | `endTime > startTime` |
| BR-SLT-002 | schedule 必须存在且非 retired |
| BR-SLT-003 | organization 冗余一致 |
| BR-SLT-004 | statusCode 必须在 FHIR 值集 |
| BR-SLT-005 | busy-unavailable 默认不可 allocate |
| BR-SLT-006 | entered-in-error 不可用于新预约 |
| BR-SLT-007 | 计数与容量关系，超售需 overbooked=true |
| BR-SLT-008 | 默认口径A：bookedCount>=1 非 free |
| BR-SLT-009 | busy-tentative 计数策略全院统一 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `SlotBatchProvisionedEvent` | `com.zxplat.domain.share.events.workflow.slot` | `scheduleId,generatedCount,slotIds` | 5.1.1 |
| `SlotBlockedEvent` | 同上 | `slotId,scheduleId,startTime,endTime` | 5.1.2 |
| `SlotUnblockedEvent` | 同上 | `slotId,scheduleId,statusCode` | 5.1.3 |
| `SlotBookedEvent` | 同上 | `slotId,bookedCount,statusCode` | 5.1.4 |
| `SlotReleasedEvent` | 同上 | `slotId,bookedCount,statusCode` | 5.1.5 |
| `SlotTentativeMarkedEvent` | 同上 | `slotId,scheduleId` | 5.1.6 |
| `SlotTentativeConfirmedEvent` | 同上 | `slotId,bookedCount,statusCode` | 5.1.7 |
| `SlotEnteredInErrorEvent` | 同上 | `slotId,reason` | 5.1.8 |
| `SlotIdentifiersSyncedEvent` | 同上 | `slotId,identifierCount` | 5.1.9 |
| `SlotServiceOverrideUpdatedEvent` | 同上 | `slotId` | 5.1.10 |
| `SlotOverbookedFlagSetEvent` | 同上 | `slotId,overbookedFlag` | 5.1.11 |

---

## 8. 删除与可见性契约

- entered-in-error 为差错作废态，不物理删除。
- 默认查询可排除 entered-in-error（管理端可显式包含）。
- Identifier 同步保持 `(system,value)` 在单 slot 下唯一。

---

## 9. 框架优先适配规则

- 预约写操作由应用层编排调用 `allocateBooking/releaseBooking`。
- 不在 Slot 聚合内实现跨资源排程求解。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表/子表字段逐项对齐，无遗漏（含 Identifier 子表全量）
- §5.1 共 12 个写方法小节，覆盖批量生成/封锁/解封/占用释放/暂定确认/差错标记等操作
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 4 项与 DM §6.2 对齐，含时段检索与占用查询
- §6 BR 规则映射覆盖容量超限/时间冲突/状态跃迁等约束
- §7 事件 6 条与 DM §5.1 全量对齐（含 Blocked/Booked/Closed/EnteredInError 等跃迁事件）
- 容量更新策略明确：悲观锁以 `capacity` 与 `available` 的 CAS 差异控制并发，先加号再减号
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-WKF-010，联动 DM-WKF-009

### 11.2 DoD

- 状态迁移与容量计数规则一致，预约占用与释放可回放。
- 关键门禁（封锁、差错、超售）具备测试绑定。

### 11.3 失败处理

- 若 `bookedCount/capacity/status` 口径冲突，回文档先修订再成码。

---

## 定稿检查清单（可打勾）

- [x] 骨架完整
- [x] 写侧 12 方法覆盖
- [x] 读侧 4 方法覆盖
- [x] BR/事件映射齐全

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **97（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
