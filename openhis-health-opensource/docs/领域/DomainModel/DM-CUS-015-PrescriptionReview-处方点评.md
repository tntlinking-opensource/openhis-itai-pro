# DM-CUS-015 PrescriptionReview 处方点评

> **限界上下文**：系统基础 · 处方点评（CUS）  
> **FHIR 映射**：无标准 FHIR R5 对应资源（院内质量点评事实）  
> **聚合类型**：实体聚合（单表）  
> **版本**：v1.0.0 · **日期**：2026-08-29

---

## 1. 概述

### 1.1 业务定义

处方点评（PrescriptionReview）记录**门诊处方（方级，以处方号标识）**事后合理性点评事实。一张处方在任意时刻至多一条**有效**点评；撤销后软删旧行，再点评时新增新行。与发药审方 Gate **无关**，不阻断发药。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 点评落库 | 人工批量点评、发药完成后自动点评（合理）写入有效点评行 |
| 一对一约束 | 同一 `prescription_no` 仅允许一条未软删记录 |
| 撤销重评 | 软删当前有效行后允许再次点评 |
| 读侧查询 | 按处方号批量取有效点评；点评记录页分页筛选 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊处方查看 | 已发药方可点评；列表展示点评状态/结果；详情展示点评字段 |
| 发药完成自动点评 | 操作员勾选开关时，发药成功后自动写入「已点评 + 合理」 |
| 处方点评记录 | 按状态/时间/是否合理/点评人等查询有效点评并导出 |

### 1.4 与关键上下游及交换边界的关系

| 维度 | 上游 | 本聚合 | 下游 |
|---|---|---|---|
| 事实锚点 | 门诊药品申请方（`prescription_no`） | 点评行（自有 `bus_no`=PRV 日序） | 门诊处方查看、点评记录页、导出口径 |
| 发药 | 发药完成编排（应用层）可选调用写侧 | 不改发放状态 | — |

**边界**：本聚合**不**校验是否已发药（由应用层 Gate）；**不**写 `MedicationRequest` / `MedicationDispense`。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

| 表 | 实体 | 说明 |
|---|---|---|
| `cus_prescription_review` | `PrescriptionReview` | 聚合根；有效行 `is_deleted=false` |

**高频查询**：按 `prescription_no`（有效唯一）、`reviewer_id`、`reviewed_at`、`reasonable_flag`、`status_enum`。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 点评标识 | `PrescriptionReviewId` | 雪花 ID |

### 2.3 状态机

展示态与落库态约定：

```
  [待点评] ──提交点评──> [已点评] ──撤销──> [待点评]
```

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | PENDING | 待点评 | **展示态**：该处方无有效点评行（不落库为 PENDING 行） |
| 2 | REVIEWED | 已点评 | **落库态**：有效点评行的 `status_enum`；撤销后软删，展示回到待点评 |

> 有效行创建时 `status_enum` 一律写 `REVIEWED`。列表筛「待点评」由读侧/应用层按「无有效点评」判定，不查本表 PENDING 行。

---

## 3. 实体

本聚合为**扁平单实体**，无子表。

### 3.1 处方点评（PrescriptionReview）

对应 `cus_prescription_review`。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 点评标识 | `id` | `Long` | 是 | 雪花 ID |
| 处方业务号 | `bus_no` | `String` | 是 | 本聚合日序自生成（前缀 **PRV**，`BusinessSequenceService#getSeqByDay`）；**不是**药品申请 MR / 就诊 EN 快照 |
| 处方号 | `prescription_no` | `String` | 是 | 方级锚点；**有效行租户内唯一** |
| 点评人 | `reviewer_id` | `Long` | 是 | 医务人员标识；自动点评=发药人 |
| 点评时间 | `reviewed_at` | `LocalDateTime` | 是 | 点评落库时刻 |
| 是否合理 | `reasonable_flag` | `Boolean` | 是 | `true`=合理，`false`=不合理 |
| 点评状态 | `status_enum` | `String` | 是 | 存枚举 code；有效行固定 `REVIEWED` |
| 原因 | `reason` | `String` | 否 | 合理可填；**不合理时必填** |
| 备注 | `remark` | `String` | 否 | 可选说明 |
| 租户/审计 | 基类字段 | — | — | `BaseAuditedEntity`（含软删） |

---

## 4. 值对象（含枚举）

### 4.1 点评状态（PrescriptionReviewStatus）

| 枚举值 | code | 说明 |
|---|---|---|
| 待点评 | `PENDING` | 仅展示/筛选用；无有效行 |
| 已点评 | `REVIEWED` | 有效点评行存库值 |

### 4.2 是否合理

以 `reasonable_flag`（Boolean）表达，不另建枚举：`true`=合理，`false`=不合理。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 处方点评已创建 | `PrescriptionReviewCreatedEvent` | 新增有效点评成功 | 点评标识、处方号、业务号、点评人、是否合理、状态 | 读侧缓存刷新、审计 |
| 处方点评已撤销 | `PrescriptionReviewRevokedEvent` | 软删有效点评成功 | 点评标识、处方号、业务号、操作人 | 读侧缓存刷新、审计 |

### 5.2 事件触发规则

| 当前展示态 | 操作 | 目标展示态 | 发布事件 |
|---|---|---|---|
| 待点评 | 创建点评 | 已点评 | `PrescriptionReviewCreatedEvent` |
| 已点评 | 撤销 | 待点评 | `PrescriptionReviewRevokedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（PrescriptionReviewDomainService）

#### 6.1.1 创建点评（`createReview`）

- **业务意义**：对尚无有效点评的处方写入一条已点评记录（人工或自动）。
- **边界说明**：不校验发药态；一对一与原因必填在本方法校验。
- **入参**：`CreatePrescriptionReviewCommand`（prescriptionNo、reviewerId、reviewedAt、reasonableFlag、reason、remark、operatorId）；**不含** busNo（领域 PRV 日序自生成）。
- **出参**：点评标识 `Long`。
- **前置条件**：处方号非空；无有效点评行（BR-PRV-001）；不合理时原因非空（BR-PRV-002）。
- **状态变化**：展示 `待点评 -> 已点评`；落库 `status_enum=REVIEWED`。
- **聚合内影响**：INSERT 一行。
- **事件输出**：`PrescriptionReviewCreatedEvent`。
- **失败语义**：已存在有效点评、原因缺失、参数非法时拒绝。

#### 6.1.2 批量创建点评（`createReviews`）

- **业务意义**：一键多选点评，共用同一套是否合理/原因/备注；点评人与时间由命令统一传入。
- **边界说明**：任一条失败则整批失败（同事务由应用层 UoW 保证）；已点评方不得进入批次（应用层过滤，领域仍校验 BR-PRV-001）。
- **入参**：`CreatePrescriptionReviewsCommand`（items 列表 + 共用 reasonableFlag/reason/remark + reviewerId/reviewedAt/operatorId）。
- **出参**：创建成功的点评标识列表。
- **前置条件**：items 非空；每项满足 `createReview` 同等规则。
- **状态变化**：各处方 `待点评 -> 已点评`。
- **聚合内影响**：逐条 INSERT。
- **事件输出**：每条 `PrescriptionReviewCreatedEvent`。
- **失败语义**：任一条冲突或校验失败时拒绝整批。

#### 6.1.3 撤销点评（`revokeReview`）

- **业务意义**：软删当前有效点评，使处方回到可再评。
- **边界说明**：不物理删除；历史行保留 `is_deleted=true`。
- **入参**：`RevokePrescriptionReviewCommand`（id 或 prescriptionNo 二选一锚点、operatorId）。
- **出参**：`—`。
- **前置条件**：目标有效点评存在（BR-PRV-003）。
- **状态变化**：展示 `已点评 -> 待点评`。
- **聚合内影响**：软删该行。
- **事件输出**：`PrescriptionReviewRevokedEvent`。
- **失败语义**：无有效点评时拒绝。

### 6.2 读侧查询服务（PrescriptionReviewQueryService）

| 方法 | 说明 | 用于 |
|---|---|---|
| `findActiveByPrescriptionNo(String prescriptionNo)` | 按处方号查有效点评；无则空 | 详情点评块、撤销前定位 |
| `mapActiveByPrescriptionNos(List<String> prescriptionNos)` | 批量处方号 → 有效点评 Map | 门诊处方查看列表 enrichment |
| `getById(Long id)` | 按主键强加载有效点评；无则业务异常 | 点评记录详情入口 |
| `pageReviews(PrescriptionReviewPageQuery query, Pageable pageable)` | 有效点评分页；条件含状态/时间窗/是否合理/点评人/处方号关键字 | 点评记录页、导出同源查询 |

> 默认过滤 `is_deleted=false`；分页未指定排序时默认 `reviewed_at DESC`。

---

## 7. 不变式

| 规则 ID | 描述 |
|---|---|
| **BR-PRV-001** | 同一 `prescription_no` 在租户内至多一条 `is_deleted=false` 记录；创建前必须确认无有效行 |
| **BR-PRV-002** | `reasonable_flag=false` 时 `reason` 必填（去空白后非空）；`true` 时 `reason`/`remark` 均可空 |
| **BR-PRV-003** | 撤销仅针对有效已点评行；目标不存在则失败 |
| **BR-PRV-004** | 有效行 `status_enum` 必须为 `REVIEWED`；禁止写入 `PENDING` 行 |
| **BR-PRV-005** | `bus_no`、`prescription_no`、`reviewer_id`、`reviewed_at`、`reasonable_flag` 创建时必填 |

---

## 8. 上下文映射

| 协作方 | 关系 | 说明 |
|---|---|---|
| MedicationRequest | 上游锚点（只读引用） | 以 `prescription_no` 关联；本聚合不改药嘱；`bus_no` 为本聚合自号 |
| MedicationDispense | 应用层协作 | 「仅已发药可评 / 发药后自动评」由 AS 编排，本聚合不读发放表 |
| Practitioner | 引用 | `reviewer_id` |

---

## 9. 补充

### 9.1 意图表

| 意图 | 写方法 | 读方法 |
|---|---|---|
| 人工/自动单条点评 | `createReview` | — |
| 批量点评 | `createReviews` | — |
| 撤销后重评 | `revokeReview` → 再 `createReview` | — |
| 列表 enrichment | — | `mapActiveByPrescriptionNos` |
| 点评记录页 | — | `pageReviews` / `getById` |

### 9.2 关联文档

| 类型 | 路径 |
|---|---|
| DomainModel-SDD | `health/DomainModel-SDD/DM-CUS-015-PrescriptionReview-处方点评-SDD.md` |
| 应用服务（目标） | `AS-OP-PHA-007` 门诊处方点评；读展示协作 `AS-OP-PHA-005`；自动点评挂接 `AS-OP-PHA-001` |

---

*领域模型负责人：产品裁决 2026-08-29 | 关联 SDD：`DM-CUS-015-PrescriptionReview-处方点评-SDD.md`*

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PrescriptionReviewDomainService`

| 方法 |
|------|
| `createReview` |
| `createReviews` |
| `revokeReview` |

### QueryService：`PrescriptionReviewQueryService`

| 方法 |
|------|
| `findActiveByPrescriptionNo` |
| `listActivePrescriptionNos` |
| `mapActiveByPrescriptionNos` |
| `getById` |
| `pageReviews` |

