# DM-FIN-003 CashierSettlement 收费员交账 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-FIN-003-CashierSettlement-收费员交账.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：费用域 · 收费员交账  
> **聚合**：CashierSettlement（单表头；源行挂载跨聚合）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-FIN-003 v3.0 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.financial.cashiersettlement` | 满足 | `cashiersettlement` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 5 章写/读白名单 | 满足 | §5 全覆盖并给出 Command / Query 字段全集 |
| DM 第 4 章 BR | 满足 | §6 映射 BR-CS-001～017；漏交/审批见 DM §4.2～4.3（BR-CS-020+） |
| DM 第 6 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：成码落地细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 提交命令快照 | DM 未强制命令携带 | 命令可携带 `paymentCount`/`totalIncome`/`practitionerIds` 快照落库；挂载仍以 QS 锁定候选为准 |
| `bus_no` 生成 | DM 仅声明字段 | 领域服务内 `BusinessSequenceService#getSeqByDay` + 租户内唯一校验 |
| 源行批量挂载 | DM 跨聚合 | 写侧经对应 QS 锁定候选；`Repository#saveAll` 批量 attach/clear |
| 审批列表排序 | DM 未列 | `CashierSettlementQueryService#defaultApprovalPageSort` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **CashierSettlement** 单表聚合的持久化模型、写/读领域服务契约、事件与 **BR-CS-*** 实现策略，与成码一致。
- 提交 **insert** 头表（`SUBMITTED`）并批量 attach 源行；撤回/驳回清挂载改终态；审批通过改态保持挂载。
- 将「实现前澄清」前置：写方法 4 个、读方法 6 个；Command / QueryModel 字段全集列出。

### 1.2 边界

- **在内**：交账头 CRUD 写路径（insert + 态变更）；历史分页；审批列表分页；四领域事件。
- **在外**：日结 rows/summary 展示、mountedVoucher 现算打印、应用 Gate（权限/确认框）、报表 batchId 重打。
- **跨域仅端口声明**：payment 读/写经 `PaymentReconciliationQueryService` / `PaymentReconciliationRepository`；dispense 读/写经 `MedicationDispenseQueryService` / `MedicationDispenseRepository`；`org_id`/`location_id` 存在性由应用 Gate 校验。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（实体/服务/仓储/命令/查询）；`zxplat-domain`（枚举/事件）；Flyway 在 `zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.financial.cashiersettlement`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `CashierSettlement` |
| `command` | `SubmitCashierSettlementCommand`、`WithdrawCashierSettlementCommand`、`RejectCashierSettlementCommand`、`ApproveCashierSettlementCommand` |
| `repository` | `CashierSettlementRepository` |
| `service` | `CashierSettlementDomainService`、`CashierSettlementQueryService` |
| `queries` | `PreviewCashierSettlementQuery`、`PageCashierSettlementApprovalQuery` |
| `queries.model` | `CashierSettlementHeaderQueryModel`、`CashierSettlementHistoryItemQueryModel`、`CashierSettlementApprovalListItemQueryModel`、`PreviewCashierSettlementQueryModel`、`UnsettledPaymentPreviewItemQueryModel` |
| `share.enums` | `CashierSettlementType`、`CashierSettlementStatus` → `com.zxplat.domain.share.enums` |
| `share.events` | `com.zxplat.domain.share.events.financial.cashiersettlement` |

**存储口径**：`type_enum`、`status_enum` 为 **`String`（存枚举 code）**；`bus_no` 为 `String`；`org_id`、`location_id`、`submitter_id`、`approver_id` 为 `Long`；`date_from`/`date_to`/`submitted_at`/`approved_at` 为 `LocalDateTime`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id`；Java 侧 camelCase |
| 时间 | 业务窗/提交/审批均 `LocalDateTime` |
| `*_enum` | `typeEnum`、`statusEnum` → `String`（存 code） |
| `bus_no` | Java `busNo` → `String` |
| 软删 | 继承 `BaseAuditedEntity.isDeleted`；查询默认 `isDeleted = false`；**不提供物理删除写方法** |
| 实体 | `@Entity` + `@Table(name = "fin_cashier_settlement")` |
| 仓储 | `JpaRepository` + `JpaSpecificationExecutor` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | `new *Event(...)` + `IDomainEventPublisher.publish`，**beforeCommit** |

---

## 4. 实体 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `fin_cashier_settlement` | `CashierSettlement` | 交账头表 |
| `fin_payment_reconciliation.cashier_settlement_id` | `PaymentReconciliation.cashierSettlementId` | 跨聚合逻辑关联 |
| `adm_medication_dispense.cashier_settlement_id` | `MedicationDispense.cashierSettlementId` | 跨聚合逻辑关联 |

### 4.2 聚合根 `CashierSettlement`（字段级契约）

| 字段名（Java） | 列名 | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ---- | ----------- |
| `id` | `id` | `Long` | 系统 | 雪花 ID |
| `busNo` | `bus_no` | `String` | 是 | 租户内唯一 |
| `typeEnum` | `type_enum` | `String` | 是 | 见 §4.3 |
| `statusEnum` | `status_enum` | `String` | 是 | 见 §4.4 |
| `orgId` | `org_id` | `Long` | 收款必填 | 科室 |
| `locationId` | `location_id` | `Long` | 药房必填 | 药房 |
| `dateFrom` | `date_from` | `LocalDateTime` | 是 | 业务窗起 |
| `dateTo` | `date_to` | `LocalDateTime` | 是 | 业务窗止 |
| `paymentCount` | `payment_count` | `Integer` | 否 | 挂载笔数快照 |
| `totalIncome` | `total_income` | `BigDecimal` | 否 | 列表金额快照（应用层现算写入） |
| `practitionerIds` | `practitioner_ids` | `String` | 否 | text，逗号分隔；**非**漏交硬栏维度 |
| `submitterId` | `submitter_id` | `Long` | 是 | 提交人 |
| `submittedAt` | `submitted_at` | `LocalDateTime` | 是 | 提交时刻 |
| `approverId` | `approver_id` | `Long` | 否 | 通过或驳回时写 |
| `approvedAt` | `approved_at` | `LocalDateTime` | 否 | 通过或驳回时刻 |
| `tenantId` 等 | — | — | 系统 | 继承 `BaseAuditedEntity` |

**已删除 / 不再映射**：`printSnapshotJson`、`cashierScopeLabel`、`cashierScopeIds`、`allCashiers`、`cashierId`、`settledAt`。

**不变式方法（实现须一致）**：

- `initSubmitted(LocalDateTime submittedAt)`：`statusEnum = SUBMITTED`，写 `submittedAt`
- `markWithdrawn()`：仅 `SUBMITTED` → `WITHDRAWN`
- `markRejected(Long approverId, LocalDateTime approvedAt)`：仅 `SUBMITTED` → `REJECTED`，写驳回人/驳回时刻（同审批人/审批时间列）
- `markApproved(Long approverId, LocalDateTime approvedAt)`：仅 `SUBMITTED` → `APPROVED`，写审批人/时间
- `assertBusNoNotBlank()` / `assertPaymentScopeKeys()`

### 4.3 枚举 `CashierSettlementType`

| code | 显示 | 挂载 |
| ---- | ---- | ---- |
| `OP_PAYMENT` | 门诊收款日结 | payment，`OUTPATIENT_CLINIC` |
| `IP_PAYMENT` | 住院收款日结 | payment，`INPATIENT_CLINIC` + `HOSPITAL_DEPOSIT` |
| `OP_PHARMACY` | 门诊药房日结 | medication_dispense |
| `IP_PHARMACY` | 住院药房日结 | medication_dispense |

静态方法：`isPaymentType(String)`、`isPharmacyType(String)`。

### 4.4 枚举 `CashierSettlementStatus`

| code | 显示 | 写动作 |
| ---- | ---- | ------ |
| `SUBMITTED` | 待审批 | 可 withdraw / reject / approve |
| `APPROVED` | 已通过 | 终态 |
| `REJECTED` | 已驳回 | 终态 |
| `WITHDRAWN` | 已撤回 | 终态 |

静态方法：`isSubmitted(String)`。

### 4.5 仓储接口

- `CashierSettlementRepository extends JpaRepository<CashierSettlement, Long>, JpaSpecificationExecutor<CashierSettlement>`
- `boolean existsByBusNoAndTenantId(String busNo, Long tenantId)`
- `Page<CashierSettlement> findByTypeEnumAndSubmittedAtBetweenAndIsDeletedFalseOrderBySubmittedAtDesc(...)`

**跨聚合（已有）**：

- `PaymentReconciliationRepository` — attach/clear/findByCashierSettlementId
- `MedicationDispenseRepository` — attach/clear/findByCashierSettlementId

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `CashierSettlementHeaderQueryModel`（`findHeaderById`）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `settlementId` | `Long` | 主键 |
| `busNo` | `String` | 单号 |
| `typeEnum` / `statusEnum` | `String` | 类型/状态 |
| `orgId` / `locationId` | `Long` | 范围键 |
| `dateFrom` / `dateTo` | `LocalDateTime` | 业务窗 |
| `paymentCount` / `totalIncome` | 数值 | 快照 |
| `practitionerIds` | `String` | 人员快照 |
| `submitterId` / `submittedAt` | — | 提交信息 |
| `approverId` / `approvedAt` | — | 审批信息 |

#### 4.6.2 `CashierSettlementHistoryItemQueryModel`（`pageHistory` 行）

在 §4.6.1 基础上增加 `canReprintFromMounts`（`paymentCount > 0` 时为 true）。

#### 4.6.3 `CashierSettlementApprovalListItemQueryModel`（`pageForApproval` 行）

同头表核心字段，不含 `practitionerIds` / `canReprintFromMounts`。

#### 4.6.4 收款预览 QueryModel（Payment QS，本包 `queries.model`）

`PreviewCashierSettlementQueryModel` + `UnsettledPaymentPreviewItemQueryModel` — 详见 **DM-FIN-002** SDD；筛选经 `PreviewCashierSettlementQuery`。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `CashierSettlementDomainService`

#### 5.1.1 `submitCashierSettlement(SubmitCashierSettlementCommand cmd)` → `Long`

**命令文件**：`SubmitCashierSettlementCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `typeEnum` | `String` | `OP_PAYMENT` / `IP_PAYMENT` / `OP_PHARMACY` / `IP_PHARMACY` |
| `orgId` | `Long` | 科室；收款必填 |
| `locationIds` | `List<Long>` | 收款可选 location 白名单（通常 null） |
| `locationId` | `Long` | 药房；药房必填 |
| `submitterId` | `Long` | 提交人（必填） |
| `entererMatchIds` | `List<Long>` | payment enterer 软筛；空=不过滤 |
| `dateFrom` / `dateTo` | `LocalDateTime` | 业务窗（落库） |
| `operatorId` | `Long` | 操作人（审计/事件） |
| `selectedPaymentIds` | `List<Long>` | 勾选 payment；`null` 全量 |
| `selectedSourceIds` | `List<Long>` | 勾选 dispense 别名；优先于 paymentIds |
| `practitionerIds` | `String` | 人员快照 |
| `paymentCount` | `Integer` | 笔数快照 |
| `totalIncome` | `BigDecimal` | 金额快照 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `initSubmitted`、`assertBusNoNotBlank`、`assertPaymentScopeKeys` |
| `repositoryChecks` | QS 锁定候选；源行 `attachCashierSettlement`；`existsByBusNoAndTenantId` |
| `eventPublishAnchor` | `CashierSettlementSubmittedEvent` |
| `unitTestCaseId` | `TC-FIN-CS-001` |

**方法契约摘要**：

- **前置**：类型/提交人/业务窗合法；范围键满足 **BR-CS-017**；锁定候选非空（**BR-CS-007**）；子集 ⊆ 候选（**BR-CS-013**）。
- **持久化**：生成 `busNo` → insert 头（`SUBMITTED`）→ batch attach → `saveAll`；并发 attach 失败整单回滚（**BR-CS-008**）。
- **分支**：`isPharmacyType` → MedicationDispense QS；否则 Payment QS。

#### 5.1.2 `withdrawCashierSettlement(WithdrawCashierSettlementCommand cmd)` → `void`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `settlementId` | `Long` | 交账头主键 |
| `operatorId` | `Long` | 操作人 |

- `clearMountedSources` → `markWithdrawn` → save → `CashierSettlementWithdrawnEvent`

#### 5.1.3 `rejectCashierSettlement(RejectCashierSettlementCommand cmd)` → `void`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `settlementId` | `Long` | 交账头主键 |
| `approverId` | `Long` | 驳回人 |
| `reason` | `String` | 原因（可空） |

- `clearMountedSources` → `markRejected(approverId, approvedAt)` → save → `CashierSettlementRejectedEvent`

#### 5.1.4 `approveCashierSettlement(ApproveCashierSettlementCommand cmd)` → `void`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `settlementId` | `Long` | 交账头主键 |
| `approverId` | `Long` | 审批人（必填） |

- `markApproved(approverId, now)` → save → `CashierSettlementApprovedEvent`；**不清挂载**

### 5.2 读侧 — `CashierSettlementQueryService`

所有查询默认 `isDeleted = false`。

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `findBusNoBySettlementId` | `Long settlementId` | `String` | 读 `busNo`；不存在 null | — |
| `findEntityById` | `Long settlementId` | `CashierSettlement` | `findById` | — |
| `pageHistory` | `typeEnum`, `submittedFrom`, `submittedTo`, `pageable` | `Page<CashierSettlementHistoryItemQueryModel>` | 按类型+提交时刻窗 | `TC-FIN-CS-004` |
| `findHeaderById` | `Long settlementId` | `CashierSettlementHeaderQueryModel` | 实体→读模型 | — |
| `pageForApproval` | `PageCashierSettlementApprovalQuery`, `pageable` | `Page<CashierSettlementApprovalListItemQueryModel>` | Specification 动态筛选 | `TC-FIN-CS-005` |
| `defaultApprovalPageSort` | — | `Sort` | `submittedAt DESC, id DESC` | — |

**`PageCashierSettlementApprovalQuery` 字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `typeEnums` | `List<String>` | 类型；空=不限 |
| `statusEnums` | `List<String>` | 状态；空=不限 |
| `submittedFrom` / `submittedTo` | `LocalDateTime` | 提交时刻窗 |
| `orgId` / `locationId` | `Long` | 范围键 |
| `busNoKeyword` | `String` | 单号模糊 |

**`PreviewCashierSettlementQuery` 字段全集**（Payment QS 共用）：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `typeEnum` | `String` | `OP_PAYMENT` / `IP_PAYMENT` |
| `orgId` | `Long` | 科室 |
| `locationIds` | `List<Long>` | 可选；通常 null |
| `entererMatchIds` | `List<Long>` | 软筛 |
| `dateFrom` / `dateTo` | `LocalDateTime` | 业务窗 |

### 5.3 读侧 — 跨聚合 QS（摘要）

| 服务 | 方法 | 用途 |
| ---- | ---- | ---- |
| `PaymentReconciliationQueryService` | `preview/count/findUnsettledPaymentsForCashierSettlement` | 收款候选 |
| `PaymentReconciliationQueryService` | `preview/findPaymentQueryModelsForCashierSettlementSummary` | 日结展示 **BR-CS-012** |
| `MedicationDispenseQueryService` | `preview/count/findUnsettledDispensesForCashierSettlement` | 药房候选 |

---

## 6. 不变式映射（DM §4 BR-CS-*）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-CS-001** | QS / submit：`billDate`（或 dispense 等价列）∈ `[dateFrom, dateTo]`；头表持久化 `dateFrom`/`dateTo` |
| **BR-CS-002** | `unsettledOnly=true` 时 `cashierSettlementId IS NULL` |
| **BR-CS-003** | payment `statusEnum IN (SUCCESS, REFUNDED)` |
| **BR-CS-004** | `OP_PAYMENT` → `kindEnum = OUTPATIENT_CLINIC` |
| **BR-CS-005** | `IP_PAYMENT` → `kindEnum IN (INPATIENT_CLINIC, HOSPITAL_DEPOSIT)` |
| **BR-CS-006** | `addEntererMatchPredicate`：`entererMatchIds` 非空才 IN；空不加谓词 |
| **BR-CS-007** | submit 候选 empty → `CASHIER_SETTLEMENT_EMPTY_CANDIDATES` |
| **BR-CS-008** | 单 UoW：insert 头 + saveAll 源行；attach 行级校验 |
| **BR-CS-009** | 实体 `assertSubmittedForWrite`；无 delete 写方法 |
| **BR-CS-010** | 应用层确认框 |
| **BR-CS-011** | 不按净额过滤条数 |
| **BR-CS-012** | summary QS `unsettledOnly=false` |
| **BR-CS-013** | `resolveMountCandidates` / `resolveMountDispenseCandidates` |
| **BR-CS-014** | `submitPharmacyCashierSettlement` + MedicationDispense QS |
| **BR-CS-015** | `clearMountedSources` on withdraw/reject |
| **BR-CS-016** | `approveCashierSettlement` 仅 `markApproved`，不调 clear |
| **BR-CS-017** | `assertSubmitCommandValid` + `assertPaymentScopeKeys` |
| **BR-CS-020** | 应用层 `findEarliestUnsettledPaymentBillTimeBeforeWindow`；提交抛 EX-*-106 |
| **BR-CS-021** | `countUnsettledPaymentsAfterWindow` → `gapSoftWarnCount` |
| **BR-CS-022** | 分批交账允许；硬栏不强制交净窗内全部 |
| **BR-CS-023** | 收款 preview/submit `locationIds=null`；禁止 org→location 滤 payment |
| **BR-CS-030～035** | 应用审批 Gate + 领域 submit/withdraw/reject/approve；撤回权限在 AppService |

---

## 7. 事件契约与触发规则（DM §6）

| 事件类名 | 包路径 | 最小载荷 | 触发方法 |
| -------- | ------ | -------- | -------- |
| `CashierSettlementSubmittedEvent` | `...cashiersettlement` | `settlementId`(super)、`busNo`、`typeEnum`、`submitterId`、`orgId`、`submittedAt`、`paymentCount`、`operatorId` | `submitCashierSettlement` |
| `CashierSettlementWithdrawnEvent` | 同上 | `settlementId`、`operatorId` | `withdrawCashierSettlement` |
| `CashierSettlementRejectedEvent` | 同上 | `settlementId`、`approverId`、`approvedAt`、`reason` | `rejectCashierSettlement` |
| `CashierSettlementApprovedEvent` | 同上 | `settlementId`、`approverId`、`approvedAt` | `approveCashierSettlement` |

**发布时机**：全部 **beforeCommit**。

---

## 8. 删除与可见性契约

- `fin_cashier_settlement`：**不提供**领域物理删除；终态头只读。
- 预览/提交均排除 `isDeleted = true` 的源行。
- 已交账源行通过 `cashierSettlementId != null` 表达；撤回/驳回后恢复 NULL。

---

## 9. 框架优先适配规则

- 枚举放 `zxplat-domain`；存 code 字符串。
- 单号 `busNo` 复用 `BusinessSequenceService` + `AggregateTypeConstants.CASHIER_SETTLEMENT`。
- Flyway 一次性迁移旧 `type_enum`/`status_enum` 与废止列；运行时不兼容旧码。
- 遵循 B.6.4：本聚合 QS 不注入 Payment/Dispense Repository。

---

## 10. 生成与验收清单

- [x] 实体 `CashierSettlement` 字段与 §4.2 一致
- [x] 共享枚举 `CashierSettlementType`、`CashierSettlementStatus`（四码/四态）
- [x] 命令：`Submit` / `Withdraw` / `Reject` / `Approve`
- [x] 写方法 4 个 + 事件 4 个
- [x] 读方法：`findBusNoBySettlementId`、`findEntityById`、`pageHistory`、`findHeaderById`、`pageForApproval`、`defaultApprovalPageSort`
- [x] QueryModel §4.6
- [x] 单元测试：`TC-FIN-CS-001`～`005`、并发 attach、终态拒绝写
- [x] 编译 `zxplat/zxplat-domain` 通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 **DM-FIN-003 v3.1** + 本 SDD + **AS-OP-FIN-004 v4.1** / **AS-IP-FIN-005 v4.2** / **AS-FIN-CS-001**。

### 11.2 DoD

- 领域层类与 Flyway 与 §4、§5 一致；四写四事件。
- submit 后源行 `cashierSettlementId` 非空；withdraw/reject 后恢复 NULL。
- 预览与 submit 共用 QS 筛选口径；仅 `SUBMITTED` 可 withdraw/reject/approve。

### 11.3 失败处理

- 与 DM/SDD 冲突时回文档会话修订，禁止口头补丁。

---

## 定稿检查清单（可打勾）

- [x] DM v3.1 已锁定且与本 SDD 字段/方法一一对应
- [x] §5.1 写方法数 = DM §5.1 写方法数（4）
- [x] §5.2 读方法与 DM §5.2 一致（6）
- [x] §4.2 字段与 DM §2.3 完整对齐
- [x] §7 事件与 DM §6 一致（4 个）
- [x] 无「待确认」「后续补充」措辞

---

**本 SDD 正文结束。**
