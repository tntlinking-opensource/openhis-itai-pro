# DM-FIN-003 CashierSettlement 收费员交账

> **限界上下文**：费用域 · 收费员交账  
> **聚合类型**：实体聚合（交账批次头 + 源行挂载）  
> **版本**：v3.1  
> **日期**：2026-07-28

---

## 1. 概述

### 1.1 业务定义

收费员交账（CashierSettlement）记录收费员或药房将一批**已完成业务流水**提交财务审批的事实：每次交账新增一条交账头，提交时将纳入的源行回写 `cashier_settlement_id`（收款挂载 **PaymentReconciliation**；药房挂载 **MedicationDispense**），表明这些流水已进入交账批次。

它回答的核心业务问题是：

> **某提交人在某业务时间窗、某科室/药房，将哪些流水提交交账？审批结果如何？**

与源聚合的关系：

| 概念 | 职责 |
| --- | --- |
| **PaymentReconciliation** | 单笔收/退的支付事实（实收） |
| **MedicationDispense** | 单笔发药事实 |
| **CashierSettlement** | 多笔源行的**批次头**（交账凭证）；提交即挂载，审批通过后保持挂载 |

「未交账」由源行侧表达（`cashier_settlement_id IS NULL`），**不在**交账头表落「未交账」状态。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 交账提交 | 生成唯一 `bus_no`，记录提交人、提交时刻、业务时间窗、科室/药房、类型与快照 |
| 源行挂载 | 提交时将符合条件的 payment 或 dispense 回写 `cashier_settlement_id`（逻辑关联，无物理外键） |
| 审批流转 | 提交后进入 `SUBMITTED`（待审批）；支持撤回、驳回、审批通过 |
| 挂载释放 | 撤回或驳回时清空本批次源行挂载，头表保留审计 |
| 历史与审批查询 | 按类型分页查交账历史；财务侧分页查待审/已审列表 |

### 1.3 适用范围

| 类型 | `type_enum` | 挂载对象 | 纳入口径（摘要） |
| --- | --- | --- | --- |
| 门诊收款日结 | `OP_PAYMENT` | payment | `kind_enum = OUTPATIENT_CLINIC` |
| 住院收款日结 | `IP_PAYMENT` | payment | `kind_enum ∈ {INPATIENT_CLINIC, HOSPITAL_DEPOSIT}` |
| 门诊药房日结 | `OP_PHARMACY` | medication_dispense | 门诊发药未挂载行 |
| 住院药房日结 | `IP_PHARMACY` | medication_dispense | 住院发药未挂载行 |

收款日结范围键为 **科室**（`org_id` 必填）；药房日结范围键为 **药房**（`location_id` 必填，`org_id` 可空）。

### 1.4 本期范围与非目标

| 类别 | 说明 |
| --- | --- |
| **本期** | 预览未交账候选 → 确认提交（`SUBMITTED`）→ 写头表 + 批量挂载；撤回 / 驳回 / 审批通过；交账历史分页；审批列表分页；药房日结类型 |
| **非目标** | 头表打印快照字段（`print_snapshot_json` 等）；按交账批次打印凭证内容由应用层现算挂载（`mountedVoucher`） |

交账提交前，前端须二次确认；提交后为**待审可撤回**，非不可逆归档。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表头 + 源行逻辑关联。

| 表 / 列 | 说明 |
| --- | --- |
| `fin_cashier_settlement` | 交账头表 |
| `fin_payment_reconciliation.cashier_settlement_id` | payment → 交账头 ID（见 **DM-FIN-002** §3.1） |
| `adm_medication_dispense.cashier_settlement_id` | 发药行 → 交账头 ID（药房日结） |

> **设计取舍**：源行以 `cashier_settlement_id` 挂载（非 junction 表、无物理外键），便于扫描未交账流水与批量 update；头表不提供物理删除，撤回/驳回仅改态并清挂载。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 交账标识 | `Long` | 雪花 ID |

### 2.3 交账头（CashierSettlement）

对应 `fin_cashier_settlement` 表：

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 主键 | `id` | `Long` | 是 | 雪花 ID |
| 交账单号 | `bus_no` | `String` | 是 | 租户内唯一业务单号 |
| 类型 | `type_enum` | `String` | 是 | `OP_PAYMENT` / `IP_PAYMENT` / `OP_PHARMACY` / `IP_PHARMACY` |
| 状态 | `status_enum` | `String` | 是 | `SUBMITTED` / `APPROVED` / `REJECTED` / `WITHDRAWN` |
| 科室 | `org_id` | `Long` | 收款必填 | 交账所属科室；药房可空 |
| 药房 | `location_id` | `Long` | 药房必填 | 交账药房；收款侧不用 |
| 业务窗起 | `date_from` | `LocalDateTime` | 是 | 提交时落库，含时分秒 |
| 业务窗止 | `date_to` | `LocalDateTime` | 是 | 提交时落库，含时分秒 |
| 挂载笔数 | `payment_count` | `Integer` | 否 | 快照：费用=payment 条数；药房=dispense 行数 |
| 列表金额 | `total_income` | `BigDecimal` | 否 | 列表展示快照（口径见应用层 AS） |
| 人员快照 | `practitioner_ids` | `String` | 否 | 窗内人员，逗号分隔 Long |
| 提交人 | `submitter_id` | `Long` | 是 | 提交交账的 Practitioner |
| 提交时刻 | `submitted_at` | `LocalDateTime` | 是 | 交账提交时间 |
| 审批人 | `approver_id` | `Long` | 否 | 审批通过时写入 |
| 审批时刻 | `approved_at` | `LocalDateTime` | 否 | 审批通过时写入 |

> 通用审计字段（`created_at` / `updated_at` / `created_by` / `updated_by`）、租户字段（`tenant_id`）、逻辑删除字段继承 `BaseAuditedEntity`。  
> **废止列（不映射）**：`cashier_id`、`settled_at`；提交人/提交时刻分别用 `submitter_id` / `submitted_at`。  
> **领域不提供对头表的物理删除**；终态头只读审计。

#### 2.3.1 实体方法

| 方法 | 说明 |
| --- | --- |
| `initSubmitted(LocalDateTime submittedAt)` | 新建头：`status_enum = SUBMITTED`，写入 `submitted_at` |
| `markWithdrawn()` | 待审 → 撤回；非 `SUBMITTED` 拒绝 |
| `markRejected(Long approverId, LocalDateTime approvedAt)` | 待审 → 驳回；写驳回人/驳回时刻；非 `SUBMITTED` 拒绝 |
| `markApproved(Long approverId, LocalDateTime approvedAt)` | 待审 → 通过；审批人与时间须非空 |
| `assertBusNoNotBlank()` | 交账单号非空 |
| `assertPaymentScopeKeys()` | 收款须 `org_id`；药房须 `location_id` |

#### 2.3.2 快照字段口径

| 字段 | 口径 |
| --- | --- |
| `payment_count` | 本次实际挂载源行条数（提交命令可携带预览快照，落库供列表展示） |
| `total_income` | 应用层按日结汇总规则计算的列表金额快照（门诊/住院收款：`resolveSelectedSettlementTotalIncome`，与 `handoverSummary.totalIncome` 同管线）；领域层仅持久化 |
| `practitioner_ids` | 业务窗内涉及经办人/收费员快照，逗号分隔 |

### 2.4 状态机

```
                    [提交交账]
                         │
                         v
                  ┌──────────────┐
          ┌──────│  SUBMITTED   │──────┐
          │      │   待审批      │      │
   [撤回] │      └──────────────┘      │ [审批通过]
          v                              v
   ┌──────────────┐              ┌──────────────┐
   │  WITHDRAWN   │              │  APPROVED    │
   │   已撤回      │              │   已通过      │
   └──────────────┘              └──────────────┘
          ▲
          │ [驳回]
   ┌──────────────┐
   │  REJECTED    │
   │   已驳回      │
   └──────────────┘
```

| code | 显示 | 语义 |
| --- | --- | --- |
| `SUBMITTED` | 待审批 | 源行已挂载；可撤回、驳回、审批通过 |
| `APPROVED` | 已通过 | 终态；挂载保持，不可再撤回/驳回/改挂载 |
| `REJECTED` | 已驳回 | 终态；挂载已清，头表只读审计 |
| `WITHDRAWN` | 已撤回 | 终态；挂载已清，头表只读审计 |

| 未建模（源行侧） | 表达 |
| --- | --- |
| 未交账 | 源行 `cashier_settlement_id IS NULL` |

**废止状态**：`HANDED_OVER`、`CANCELLED` 等旧码已由 Flyway 一次性迁移，运行时不兼容。

---

## 3. 源行扩展（与 DM-FIN-002 / 发药域联动）

### 3.1 PaymentReconciliation

在 `fin_payment_reconciliation` 保留：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `cashier_settlement_id` | `Long` NULL | 非空 = 已纳入某交账批次 |

**规则**：

- 仅 `status_enum ∈ {SUCCESS, REFUNDED}` 的 payment 可纳入交账。
- 提交时批量 attach；须 row 级校验仍为 NULL，否则整单失败。
- 撤回/驳回时 batch clear 挂载。

### 3.2 MedicationDispense（药房日结）

发药行保留 `cashier_settlement_id`；提交 attach、撤回/驳回 clear；口径由 **MedicationDispenseQueryService** 与预览共用（B.6.4）。

---

## 4. 纳入交账的口径（BR-CS-*）

### 4.1 候选与挂载

| 规则 ID | 描述 |
| --- | --- |
| **BR-CS-001** | 源行业务时间在提交指定的 `[date_from, date_to]` 窗内（精确到时分秒）；**该时间窗落库**到头表 `date_from` / `date_to` |
| **BR-CS-002** | `cashier_settlement_id IS NULL`（未交账）；仅提交挂载与 `paymentCount` 计数施加 |
| **BR-CS-003** | payment：`status_enum ∈ {SUCCESS, REFUNDED}`；药房：正向 COMPLETED + 退药 REFUNDED |
| **BR-CS-004** | `OP_PAYMENT` → payment `kind_enum = OUTPATIENT_CLINIC` |
| **BR-CS-005** | `IP_PAYMENT` → payment `kind_enum ∈ {INPATIENT_CLINIC, HOSPITAL_DEPOSIT}` |
| **BR-CS-006** | 收款：`entererMatchIds` 可选软筛；**非空**时 payment `enterer_id IN entererMatchIds`；**空/null 不按人过滤**（与 AS-OP-FIN-004 / AS-IP-FIN-005 对齐）；人**不是**漏交硬栏维度 |
| **BR-CS-007** | 提交时若锁定候选集为空 → 拒绝（无可交账流水） |
| **BR-CS-008** | 提交须原子：insert 交账头 + 批量 attach 源行；任一行已被并发挂载 → 整单失败 |
| **BR-CS-009** | 仅 `SUBMITTED` 允许 withdraw / reject / approve；`APPROVED` / `REJECTED` / `WITHDRAWN` 对上述写为终态；**头表不物理删除** |
| **BR-CS-010** | 前端提交前须二次确认；提交后为待审可撤回，**非**「归档不可逆」 |
| **BR-CS-011** | 交账候选与 `paymentCount` 按 **源行条数**计，不按净额过滤：0 元 SUCCESS、一收一退等均须纳入 |
| **BR-CS-012** | 日结 rows/summary 展示汇总复用 **BR-CS-001、003～006**，**不**施加 **BR-CS-002**；`paymentCount`、提交挂载仍仅未交账 |
| **BR-CS-013** | 提交命令可携带 `selectedPaymentIds` / `selectedSourceIds`：非空时仅挂载该子集，须 ⊆ 锁定候选；`null` 回落全量；显式空列表 → **BR-CS-007** |
| **BR-CS-014** | `OP_PHARMACY` / `IP_PHARMACY`：范围键为 `location_id`（必填）；挂载 **MedicationDispense**；预览/提交共用 MedicationDispense QS 口径；`dispense_time` 为空 → 报错不回落 |
| **BR-CS-015** | 撤回（`WITHDRAWN`）或驳回（`REJECTED`）时须 **清空本批次全部源行挂载**，再改头态 |
| **BR-CS-016** | 审批通过（`APPROVED`）**不改挂载**；写入 `approver_id`、`approved_at` |
| **BR-CS-017** | 收款日结 `org_id` 必填；药房日结 `location_id` 必填（实体 `assertPaymentScopeKeys` + 提交 Gate 双校验） |

### 4.2 漏交硬栏与提醒（按时间 + 范围键，不按人）

| 规则 ID | 描述 |
| --- | --- |
| **BR-CS-020** | 同 `type_enum` + 筛选口径下：未交且业务时间 **≤ `dateTo`** 又 **&lt; `dateFrom`** → **硬栏禁止最终提交**（应用层提交 Gate，语义码 EX-*-DCL-106）；提示最早漏交时刻；**不**禁止打开明细弹窗与勾选 |
| **BR-CS-021** | 未交且业务时间 **&gt; `dateTo`** → 仅提醒，不拦截提交 |
| **BR-CS-022** | 硬栏只保证「截止前不存在窗外更早的未交」；**不**强制一次交净窗内全部 |
| **BR-CS-023** | 收款候选/漏交读侧：按 `type_enum`+时间窗（+可选人软筛）；头表落 `org_id`；**禁止**用科室下 location 白名单过滤 `fin_payment_reconciliation`（不绑款台、payment 无科室列） |

### 4.3 审批流

| 规则 ID | 描述 |
| --- | --- |
| **BR-CS-030** | 提交：头 `SUBMITTED`；写 `submitter_id` / `submitted_at` / 窗 / 笔数 / 金额快照 / `practitioner_ids` |
| **BR-CS-031** | 撤回：仅 `SUBMITTED`；权限由应用层按交账类型 Gate（收款：款员 `ROLE_CASHIER`；药房：药师 `ROLE_PHARMACIST`）；清挂载；头 → `WITHDRAWN` |
| **BR-CS-032** | 驳回：仅财务 + 仅 `SUBMITTED`；清挂载；头 → `REJECTED`；写 `approver_id` / `approved_at` |
| **BR-CS-033** | 审批通过：仅财务 + 仅 `SUBMITTED` → `APPROVED`；写 `approver_id` / `approved_at`；**不改挂载** |
| **BR-CS-034** | **本轮不做**审批后再驳回 |
| **BR-CS-035** | 交账 `status_enum` **不是**退费/退药/反结门禁 |

---

## 5. 领域服务

### 5.1 CashierSettlementDomainService（写）

| 方法 | 业务意义 |
| --- | --- |
| `submitCashierSettlement(SubmitCashierSettlementCommand)` | insert 头表（`SUBMITTED`）+ 批量挂载源行；收款经 `PaymentReconciliationQueryService#findUnsettledPaymentsForCashierSettlement`；药房经 `MedicationDispenseQueryService#findUnsettledDispensesForCashierSettlement`；支持 **BR-CS-013** 子集挂载 |
| `withdrawCashierSettlement(WithdrawCashierSettlementCommand)` | 清挂载 + 头改 `WITHDRAWN`；仅 `SUBMITTED` |
| `rejectCashierSettlement(RejectCashierSettlementCommand)` | 清挂载 + 头改 `REJECTED`；写 `approver_id`/`approved_at`；仅 `SUBMITTED` |
| `approveCashierSettlement(ApproveCashierSettlementCommand)` | 头改 `APPROVED` + 写审批人/时间；挂载保持；仅 `SUBMITTED` |

### 5.2 CashierSettlementQueryService（读，仅本聚合）

| 方法 | 业务意义 |
| --- | --- |
| `findBusNoBySettlementId(Long settlementId)` | 提交成功后读回交账单号 |
| `findEntityById(Long settlementId)` | 读取交账头实体 |
| `pageHistory(typeEnum, submittedFrom, submittedTo, pageable)` | 交账历史分页（按类型 + 提交时刻窗） |
| `findHeaderById(Long settlementId)` | 交账头读模型 |
| `pageForApproval(PageCashierSettlementApprovalQuery, pageable)` | 审批列表分页 |
| `defaultApprovalPageSort()` | 审批列表默认排序（`submittedAt DESC, id DESC`） |

### 5.3 PaymentReconciliationQueryService（收款候选读，DM-FIN-002 扩展）

| 方法 | 业务意义 |
| --- | --- |
| `previewUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery)` | 预览未交账候选 payment |
| `countUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery)` | 未交账条数 |
| `findUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery)` | 写侧提交锁定候选（**BR-CS-001～006**） |
| `previewPaymentsForCashierSettlementSummary(PreviewCashierSettlementQuery)` | 日结展示汇总（**BR-CS-012**，含已交账） |
| `findPaymentQueryModelsForCashierSettlementSummary(PreviewCashierSettlementQuery)` | 日结展示读模型 |

> **B.6.4**：未交账 payment 候选读侧归属 Payment 聚合 QueryService；`CashierSettlementQueryService` **不**跨聚合访问 `PaymentReconciliationRepository`。

### 5.4 MedicationDispenseQueryService（药房候选读）

| 方法 | 业务意义 |
| --- | --- |
| `previewUnsettledDispensesForCashierSettlement(PreviewPharmacyCashierSettlementQuery)` | 预览未交账发药候选 |
| `countUnsettledDispensesForCashierSettlement(PreviewPharmacyCashierSettlementQuery)` | 未交账条数 |
| `findUnsettledDispensesForCashierSettlement(PreviewPharmacyCashierSettlementQuery)` | 写侧提交锁定候选 |

---

## 6. 领域事件

| 事件 | 触发 | 载荷（摘要） | 消费方 |
| --- | --- | --- | --- |
| `CashierSettlementSubmittedEvent` | 提交成功后 | `settlementId`、`busNo`、`typeEnum`、`submitterId`、`orgId`、`submittedAt`、`paymentCount`、`operatorId` | 审计 |
| `CashierSettlementWithdrawnEvent` | 撤回成功后 | `settlementId`、`operatorId` | 审计 |
| `CashierSettlementRejectedEvent` | 驳回成功后 | `settlementId`、`approverId`、`approvedAt`、`reason` | 审计 / 通知 |
| `CashierSettlementApprovedEvent` | 审批通过后 | `settlementId`、`approverId`、`approvedAt` | 审计 |

发布时机：写侧持久化成功后、**beforeCommit** 同步投递。

---

## 7. 上下游关系

### 7.1 上游

| 上游 | 说明 |
| --- | --- |
| **PaymentReconciliation** | 收款日结挂载对象 |
| **MedicationDispense** | 药房日结挂载对象 |
| **Practitioner** | 提交人、窗内人员快照 |
| **Organization / Department** | 科室（`org_id`） |
| **Location** | 药房（`location_id`） |

### 7.2 下游

| 下游 | 说明 |
| --- | --- |
| **AS-OP-FIN-004 / AS-IP-FIN-005** | 门诊/住院收款日结应用编排 |
| **AS-FIN-CS-001** | 交账审批（通过/驳回） |
| **PrintTemplate（AS-BAS-PRT）** | 按挂载现算重打凭证（应用层 `mountedVoucher`，非头表快照） |
| **运维勾稽** | 可扫源行 `cashier_settlement_id IS NULL` 超龄未交账流水 |

---

## 8. 关联文档

| 文档 |
| --- |
| `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| `AS-OP-FIN-004-门诊收费门诊日结.md`（v4.1） |
| `AS-IP-FIN-005-住院收费住院日结.md`（v4.2） |
| `AS-FIN-CS-001-交账审批.md` |

---

## 9. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v3.1 | 2026-07-28 | 合并冲突定稿：保留 BR-CS-001～017；增补漏交 **BR-CS-020～023** 与审批 **BR-CS-030～035**；`markRejected` 写审批人/时间；对齐成码 EX-*-106 / enterer 软筛 |
| v3.0 | 2026-07-27 | **审批态交账**：四类型（`OP_PAYMENT`/`IP_PAYMENT`/`OP_PHARMACY`/`IP_PHARMACY`）；四状态（`SUBMITTED`/`APPROVED`/`REJECTED`/`WITHDRAWN`）；废止 `HANDED_OVER`/`CANCELLED`/`cashier_id`/`settled_at`；新增 `submitter_id`/`submitted_at`/`date_from`/`date_to`/快照列；写方法 submit/withdraw/reject/approve；读方法 history/approval/header；撤回/驳回清挂载；事件四件套；**BR-CS-001** 时间窗落库；**BR-CS-006** enterer 软筛；**BR-CS-009/010** 待审可撤回 |
| v2.2 | 2026-07-11 | **BR-CS-013**：`selectedPaymentIds` 子集挂载 |
| v2.0 | 2026-06-21 | 简化为单表；payment 挂载；只增不改 |
| v1.0 | 2026-06-19 | 首版（CashierDailySettlement，已废止） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`CashierSettlementDomainService`

| 方法 |
|------|
| `submitCashierSettlement` |
| `withdrawCashierSettlement` |
| `rejectCashierSettlement` |
| `approveCashierSettlement` |

### QueryService：`CashierSettlementQueryService`

| 方法 |
|------|
| `listHeadersByTypeEnums` |
| `findBusNoBySettlementId` |
| `findEntityById` |
| `pageHistory` |
| `findHeaderById` |
| `pageForApproval` |
| `defaultApprovalPageSort` |

