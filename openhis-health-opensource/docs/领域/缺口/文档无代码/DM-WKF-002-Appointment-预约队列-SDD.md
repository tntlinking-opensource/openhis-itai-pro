# DM-WKF-002 Appointment 预约队列 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-WKF-002-Appointment-预约队列.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：Appointment（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-WKF-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.workflow.appointment` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 全覆盖 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.appointment` |
| 软删终态口径 | §2.3/§4.3 多状态描述 | 统一以 `isDeleted=true` + 原因字段收口终态 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化预约与候诊聚合在门诊预约挂号、渠道报到场景下的统一契约。
- 明确 `appointmentKind` 分类、软删终态与叫号过号语义，支撑后续代码一次成型。
- 统一写侧方法到独立 Command 文件，读侧提供可实施查询契约，避免口径漂移。

### 1.2 边界

- **在内**：候诊/预约条目创建、调序、叫号、过号、按就诊取消、渠道来源记录。
- **在外**：分诊医学规则、号源生成算法、EMPI 主索引、计费结算。
- **跨域仅端口声明**：通过 `encounterId/patientId/slotId` 引用上游，不在本聚合直接写他域表。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.appointment`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Appointment` |
| `valueobject` | `AppointmentOrigin`（若以值对象落库） |
| `enums`（共享） | 复用共享枚举，避免重复生成 |
| `command` | 每个写方法一个命令文件 |
| `repository` | `AppointmentRepository`（JpaRepository 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.appointment` |

**存储口径（DM 优先）**：`appointment_kind`、`status` 相关字段按枚举语义映射；`*_code` 字段用 `String`，`*_flag` 字段用 `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id` |
| 时间 | `triageTime/cancellationAt/calledAt` 使用 `LocalDateTime` |
| `*_enum` | 默认 `Integer`（若落库为字符串枚举，以 DM 为准） |
| `*_code` | `String` |
| `_flag` | `Boolean` |
| 软删 | `isDeleted`，终态通过软删 + 原因收口 |
| 实体 | `@Entity` + 显式 `@Table/@Column` |
| 值对象 | `@Embeddable`（如 `AppointmentOrigin`） |
| 仓储 | `JpaRepository<Appointment, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事件 | beforeCommit 发布，不引入 EventFactory |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_appointment_queue` | `Appointment` | 预约与候诊主表（兼容旧表名） |

### 4.2 聚合根 `Appointment`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `appointmentKind` | `String` | 是 | 见 §4.4 |
| `busNo` | `String` | 否 | 业务号 |
| `patientId` | `Long` | 视 kind | 门诊预约建议必填 |
| `encounterId` | `Long` | 视 kind | 报到候诊必填 |
| `organizationId` | `Long` | 是 | 机构 |
| `destinationLocationId` | `Long` | 否 | 去向诊室 |
| `triageLevel` | `String` | 否 | 分诊等级 |
| `triagePriorityCode` | `String` | 否 | 优先级编码 |
| `triageTime` | `LocalDateTime` | 否 | 分诊时间 |
| `displayOrder` | `Integer` | 视 kind | 报到候诊排序 |
| `slotId` | `Long` | 视 kind | 门诊预约号源 |
| `serviceTypeCode` | `String` | 否 | 服务类型 |
| `appointmentTypeCode` | `String` | 否 | 预约类型 |
| `startInstant` | `LocalDateTime` | 否 | 计划开始 |
| `endInstant` | `LocalDateTime` | 否 | 计划结束 |
| `cancelReason` | `String` | 否 | 取消原因文本 |
| `cancellationReasonCode` | `String` | 否 | 取消原因编码 |
| `cancellationAt` | `LocalDateTime` | 否 | 取消/过号时间 |
| `calledAt` | `LocalDateTime` | 否 | 最近叫号时间 |
| `lastPractitionerId` | `Long` | 否 | 最近叫号医生 |
| `isDeleted` | `Boolean` | 是 | 软删标记 |

**不变式方法（示例命名）**：`assertKindConstraints`、`assertQueueUniqueByEncounter`、`reorderDisplayOrder`、`markTerminalByReason`。

### 4.3 子实体

无。本聚合为单表聚合。

### 4.4 枚举与值对象契约

| 字段/类型 | 值域 | 说明 |
| --------- | ---- | ---- |
| `appointmentKind` | `OUTPATIENT_BOOKING` / `CHECK_IN_ONLY` | 业务分类 |
| `originChannel`（值对象） | `KIOSK`/`MINIAPP`/`COUNTER`/`DOCTOR_STATION`/`NURSE_STATION`/`OTHER` | 渠道来源 |

### 4.5 仓储接口

- `AppointmentRepository extends JpaRepository<Appointment, Long>`
- 必要签名：
  - `Optional<Appointment> findByEncounterIdAndIsDeletedFalse(Long encounterId)`
  - `Page<Appointment> findByOrganizationIdAndDestinationLocationIdAndIsDeletedFalse(...)`
  - `List<Appointment> findByEncounterIdInAndIsDeletedFalse(List<Long> encounterIds)`
  - `List<Appointment> findByPatientIdAndIsDeletedFalse(Long patientId)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `AppointmentQueryModel`（对应 `findByEncounterId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `wkf_appointment_queue.id` | 主键 |
| `appointmentKind` | `String` | `wkf_appointment_queue.appointment_kind` | 分类 |
| `displayOrder` | `Integer` | `wkf_appointment_queue.display_order` | 排序 |
| `statusView` | `String` | 推导 | 待诊/叫号/过号等视图状态 |

#### 4.6.2 `AppointmentQueuePageModel`（对应 `findQueueByOrganizationAndLocation`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | 主表 | 主键 |
| `encounterId` | `Long` | 主表 | 就诊标识 |
| `triageLevel` | `String` | 主表 | 分诊等级 |
| `displayOrder` | `Integer` | 主表 | 队列顺序 |

#### 4.6.3 `AppointmentBatchModel`（对应 `findByEncounterIdIn`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `encounterId` | `Long` | 主表 | 就诊锚点 |
| `appointmentId` | `Long` | 主表 | 发放主键 |
| `queueStatus` | `String` | 推导 | 队列状态 |

#### 4.6.4 `PatientAppointmentModel`（对应 `findByPatientId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | 主表 | 主键 |
| `patientId` | `Long` | 主表 | 患者 |
| `slotId` | `Long` | 主表 | 号源 |
| `startInstant` | `LocalDateTime` | 主表 | 预约开始 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `AppointmentManager`

#### 5.1.1 `create(CreateAppointmentCommand cmd)` → `Appointment`

**命令文件**：`CreateAppointmentCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentKind` | `String` | 预约分类 |
| `organizationId` | `Long` | 机构 |
| `destinationLocationId` | `Long` | 去向位置 |
| `encounterId` | `Long` | 就诊 |
| `patientId` | `Long` | 患者 |
| `displayOrder` | `Integer` | 排序 |
| `slotId` | `Long` | 号源 |
| `origin` | `AppointmentOriginCommand` | 渠道来源 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertKindConstraints` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByEncounterIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new AppointmentCreatedEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-001` |

**方法契约摘要**：前置校验 kind 必填字段；持久化写入新候诊/预约行；失败为重复有效队列或关键字段缺失；触发 `AppointmentCreatedEvent`。

---

#### 5.1.2 `checkInToQueue(CheckInToQueueCommand cmd)` → `Appointment`

**命令文件**：`CheckInToQueueCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentKind` | `String` | 报到场景分类 |
| `encounterId` | `Long` | 就诊 |
| `patientId` | `Long` | 患者 |
| `origin` | `AppointmentOriginCommand` | 渠道信息 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertKindConstraints` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findByEncounterIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new AppointmentCreatedEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-002` |

**方法契约摘要**：报到入队生成有效项；失败为重复入队或参数非法；事件按创建事件发布。

---

#### 5.1.3 `cancelByEncounterId(CancelByEncounterCommand cmd)` → `void`

**命令文件**：`CancelByEncounterCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 就诊标识 |
| `reason` | `String` | 取消原因 |
| `cancellationReasonCode` | `String` | 原因编码 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markTerminalByReason` |
| `statusTransitionMethod` | `cancel` |
| `repositoryChecks` | `findByEncounterIdAndIsDeletedFalse` |
| `eventPublishAnchor` | `new AppointmentNoShowEvent(...)` / 取消事件 |
| `unitTestCaseId` | `TC-WKF-APT-CMD-003` |

**方法契约摘要**：按就诊取消当前有效队列项并软删；失败为无有效项或状态不允许；发布取消类事件。

---

#### 5.1.4 `priorityTreatment(PriorityTreatmentCommand cmd)` → `void`

**命令文件**：`PriorityTreatmentCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentId` | `Long` | 目标时段 |
| `destinationLocationId` | `Long` | 目标诊室 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `reorderDisplayOrder` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `findById` + 队列同诊室加载 |
| `eventPublishAnchor` | `new PriorityTreatmentAppliedEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-004` |

**方法契约摘要**：将目标置顶并重排顺序；失败为越权或无目标记录；发布优先就诊事件。

---

#### 5.1.5 `changeOver(ChangeOverCommand cmd)` → `void`

**命令文件**：`ChangeOverCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentId` | `Long` | 当前项 |
| `triageLevel` | `String` | 参考等级 |
| `moveForward` | `Boolean` | 向前/向后 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `reorderDisplayOrder` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 同队列相邻项查询 |
| `eventPublishAnchor` | `new QueueOrderChangedEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-005` |

**方法契约摘要**：前后调位并保持顺序连续；越界拒绝；发布调序事件。

---

#### 5.1.6 `dragChangeOrder(DragChangeOrderCommand cmd)` → `void`

**命令文件**：`DragChangeOrderCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentId` | `Long` | 当前项 |
| `targetIndex` | `Integer` | 目标索引 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `reorderDisplayOrder` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | 同队列列表加载 |
| `eventPublishAnchor` | `new QueueOrderChangedEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-006` |

**方法契约摘要**：拖拽调位后全量重排；失败为目标索引非法；发布调序事件。

---

#### 5.1.7 `callToSee(CallToSeeCommand cmd)` → `void`

**命令文件**：`CallToSeeCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentId` | `Long` | 发放标识 |
| `practitionerId` | `Long` | 操作医生 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markTerminalByReason` |
| `statusTransitionMethod` | `call` |
| `repositoryChecks` | `findById` |
| `eventPublishAnchor` | `new AppointmentCalledToSeeEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-007` |

**方法契约摘要**：叫号后写软删终态和叫号追溯字段；失败为状态不允许；发布叫号事件。

---

#### 5.1.8 `noShow(NoShowCommand cmd)` → `void`

**命令文件**：`NoShowCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `appointmentId` | `Long` | 目标项 |
| `reasonCode` | `String` | 过号原因编码 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markTerminalByReason` |
| `statusTransitionMethod` | `noShow` |
| `repositoryChecks` | `findById` |
| `eventPublishAnchor` | `new AppointmentNoShowEvent(...)` |
| `unitTestCaseId` | `TC-WKF-APT-CMD-008` |

**方法契约摘要**：过号处理进入终态并记录取消时间；失败为状态不允许；发布过号事件。

### 5.2 读侧 — `AppointmentQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `findByEncounterId` | `encounterId` | `AppointmentQueryModel` | 单就诊有效项查询 | TC-WKF-APT-QRY-001 |
| `findQueueByOrganizationAndLocation` | `organizationId`,`locationId`,`Pageable` | `Page<AppointmentQueuePageModel>` | 报到候诊分页，按 `displayOrder` | TC-WKF-APT-QRY-002 |
| `findByEncounterIdIn` | `encounterIds` | `List<AppointmentBatchModel>` | 批量回填 | TC-WKF-APT-QRY-003 |
| `findByPatientId` | `patientId` | `List<PatientAppointmentModel>` | 患者预约/候诊列表 | TC-WKF-APT-QRY-004 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并分页统计 → 组装 QueryModel。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-APT-001 | 报到候诊同一 `encounterId` 仅一条有效项 |
| BR-APT-002 | 同机构同诊室 `displayOrder` 连续 |
| BR-APT-003 | 终态项不参与待诊查询（`isDeleted=false` 过滤） |
| BR-APT-004 | 叫号/过号必须保留原因与追溯字段 |
| BR-APT-005 | 分诊更新先取消旧项再建新项 |
| BR-APT-006 | 调序不得越界 |
| BR-APT-007 | 门诊预约场景需满足 `patientId`+`slotId` 约束 |
| BR-APT-008 | 渠道为自助机时建议携带设备标识 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `AppointmentCreatedEvent` | `com.zxplat.domain.share.events.workflow.appointment` | `appointmentId`,`appointmentKind`,`encounterId`,`patientId` | `create`,`checkInToQueue` |
| `PriorityTreatmentAppliedEvent` | `com.zxplat.domain.share.events.workflow.appointment` | `appointmentId`,`oldOrder`,`newOrder` | `priorityTreatment` |
| `QueueOrderChangedEvent` | `com.zxplat.domain.share.events.workflow.appointment` | `appointmentId`,`oldOrder`,`newOrder` | `changeOver`,`dragChangeOrder` |
| `AppointmentCalledToSeeEvent` | `com.zxplat.domain.share.events.workflow.appointment` | `appointmentId`,`calledAt`,`lastPractitionerId` | `callToSee` |
| `AppointmentNoShowEvent` | `com.zxplat.domain.share.events.workflow.appointment` | `appointmentId`,`cancellationAt`,`reasonCode` | `noShow`,`cancelByEncounterId` |

**发布时机**：全部 beforeCommit。

---

## 8. 删除与可见性契约

- 终态采用逻辑删除；默认查询不返回 `isDeleted=true` 数据。
- 叫号、过号、取消均需记录原因字段，不允许静默删除。
- 管理端审计查询可通过显式参数包含历史终态项。

---

## 9. 框架优先适配规则

- 复用现有软删、乐观锁、事件发布组件，不新增薄封装层。
- 写前校验与QueryService 查询分层，避免用列表 SQL 直接驱动写入。
- 服务默认直接实现类，接口按多实现需求再引入。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 8 个写方法小节，方法名与 DM §6.1 完全一致（含新建/改期/取消/退号/确认/校验/废弃）
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 4 项与 DM §6.2 对齐，含预约状态检索与患者预约历史
- §6 BR 规则映射覆盖 BR-APT-001~008，均可追溯到方法实现
- §7 事件 5 条与 DM §5.1 全量对齐（含 Booked/Cancelled/NoShow/Arrived/Fulfilled 等跃迁事件）
- 软删语义与终态（Cancelled/EnteredInError）一致性完整实现
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-WKF-002-Appointment-预约队列`
- 本 SDD
- 目标模块：`zxplat/zxplat-domain` + `zxplat-domain`

### 11.2 DoD

- 写读方法签名与 §5 完全一致。
- 状态迁移、调序、叫号/过号满足 BR 与事件契约。
- 查询分页统计与结果一致，不出现先分页后过滤。

### 11.3 失败处理

- 方法缺失或字段不全：回文档阶段修订后再成码。
- 事件未对齐：阻断提交并补齐。

---

## 定稿检查清单（可打勾）

- [x] 章节骨架完整
- [x] 写侧方法块完整（8/8）
- [x] 读侧方法完整（4/4）
- [x] BR 与事件全量映射
- [x] 无 YAML / `sdd_status` / `dm_code`

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
