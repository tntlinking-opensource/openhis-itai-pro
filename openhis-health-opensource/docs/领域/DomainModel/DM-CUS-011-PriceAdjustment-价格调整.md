# DM-CUS-011 PriceAdjustment 价格调整

> **限界上下文**：本地扩展 · 价格调整管理  
> **聚合类型**：实体聚合（主表多行 + 子表批号明细）  
> **版本**：v2.1

---

## 1. 概述

### 1.1 业务定义

价格调整（PriceAdjustment）是**药品、耗材与诊疗项目调价单据**的领域模型，以 `bus_no` 聚合一张调价单，主表每行对应一个调价项目，子表记录调前价快照与盈亏。

- **药品/耗材（logistics）**：子表按**批号多行**，含数量、双侧调前价与盈亏。
- **诊疗（clinical）**：子表与主行**一对一**，仅存调前售价；占位批号 `-`、数量 `1`。

本模型回答：**某次调价单改了哪些项目、盈亏多少、审批前后定价与（药品耗材）库存如何一致**。

### 1.2 核心职责

| 职责 | 说明 |
| ---- | ---- |
| 调价单据 | 以 `bus_no` 管理草稿/审批/生效全生命周期 |
| 盈亏快照 | 子表记录调前价与盈亏（物流批号多行 / 诊疗一对一） |
| 定价落库 | 审批通过后由**应用层**写入 `ChargeItemDefinition` |
| 库存协作 | **药品/耗材**提交待审批时由**应用层**全院锁库存；诊疗不涉及库存锁 |
| 业务互斥 | 待审批期间阻断相关临床/物流操作（应用层编排校验） |
| 变更追溯 | 已生效单据行视为历史事实，不可改删 |

### 1.3 适用范围

| 类型 | 说明 |
| ---- | ---- |
| **在范围内** | `med_medication_definition`（药品）、`adm_device_definition`（耗材）、`wkf_activity_definition`（诊疗活动）；**不含** `adm_healthcare_service` |
| **展示口径** | 读模型 `adjustmentStyle`：`logistics`（批号多行）/ `clinical`（一对一调前售价） |
| 调价场景 | 物流：进价/售价双侧可选 + 批号；诊疗：仅目标售价 |
| 非目标 | 限价单独调价菜单；`forceVoid` 紧急解锁 |

### 1.4 与关键上下游

| 维度 | 上游 | 本聚合 | 下游 |
| ---- | ---- | ------ | ---- |
| 锚点 | 药品/耗材/诊疗定义、`ChargeItemDefinition` 读价 | 调价单 + 子表快照 | 应用层写 `ChargeItemDefinition` |
| 库存锁 | `InventoryItem` / `inventory_lock` | **应用层**提交前加锁、审批后解锁（仅物流） | 全院库存可用量 |
| 临床 | 医嘱、发药、护士执行/划价 | **应用层**互斥与 pending 阻断 | 医生站、护士站、药房 |
| 交换 | — | 院内 `bus_no` + 调价事实 | 可投射 `AuditEvent` |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**主从表 + 同表多行（类 `wkf_supply_request`）**：

- **主表** `cus_price_adjustment`：同一 `bus_no` 多行，每行一个调价**项目**；同行共享 `status_enum` 及审批字段（落库时各项目行冗余，以 `bus_no` 为单据口径）。
- **子表** `cus_price_adjustment_detail`：物流为每项目下多**批号**行；诊疗为每项目**一对一**行（仅存调前售价）。

**不在主表存储**：调前价、批号、单行盈亏合计、`total_impact`。  
**不在子表存储**：调后价（读主表 `new_purchase_amount` / `new_selling_amount`）。

**事务边界（领域层 + 应用层同事务编排）**：

- **领域层** `submit`：本聚合 pending 互斥 → 重算子表盈亏 → 子表就绪校验 → `status=pending`。
- **应用层**（物流）：提交前在途互斥、锁冲突预检、`inventory_lock`；审批通过/驳回后 `unlockByBusNo` 与写定价。
- **应用层**（诊疗）：无库存锁；提交前由应用层组装调前售价子表。

| 索引 | 用途 |
| ---- | ---- |
| `cus_price_adjustment.bus_no` | 按单加载 |
| `(item_table, item_id, status_enum)` | 互斥：是否存在 pending |
| `(status_enum, submit_time)` | 审批工作台 |
| `(org_id, submit_time)` | 机构维度列表 |
| `cus_price_adjustment_detail.adjustment_id` | 加载子表 |
| `(adjustment_id, lot_number)` | 批号明细 |

**事务边界**：

**索引建议**：

| 属性 | 类型 | 说明 |
| ---- | ---- | ---- |
| 调价单号 | `String` `bus_no` | 单据聚合键（同单多行共享） |
| 项目行标识 | `Long` `id` | 主表主键 |
| 批号行标识 | `Long` `id` | 子表主键 |

### 2.3 状态机（按 `bus_no`）

```text
  [草稿 draft]
      │ submit（互斥+快照+全院锁）
      ▼
  [待审批 pending] ──approve──> [已生效 approved]
      │                │
      │ reject         │（终态，行不可变）
      ▼                │
  [已拒绝 rejected]     │
      │ resubmit       │
      └──────────────> pending
      │
  draft ──withdraw──> [已废弃 deprecated]
```

| 状态 | 编码 | 说明 | 允许操作 |
| ---- | ---- | ---- | -------- |
| 草稿 | `draft` | 编辑中，**不加锁** | 保存行、提交、撤销 |
| 待审批 | `pending` | 已快照、已全院锁 | 审批通过、驳回 |
| 已生效 | `approved` | 定价已落库 | — |
| 已拒绝 | `rejected` | 已解锁 | 修改后重新提交 |
| 已废弃 | `deprecated` | 草稿撤销 | — |

> **存储口径**：`status_enum` 为字符串，与 `PriceAdjustmentStatus` 枚举 `code` 一致。

---

## 3. 实体

```text
调价单 bus_no
═══════════════════════════════════════════════════════════

  cus_price_adjustment（项目行 × N）
  ┌────────────────────────────────────────────────────┐
  │ bus_no, item_table, item_id                        │
  │ new_purchase_amount, new_selling_amount（目标价）    │
  │ adjustment_reason, status_enum, 审批/申请字段      │
  │ operator_id, org_id                                │
  └───────────────┬────────────────────────────────────┘
                  │ 1 : N
  cus_price_adjustment_detail（子表：物流多批号 / 诊疗一对一）
  ┌────────────────────────────────────────────────────┐
  │ lot_number（诊疗占位 "-"）, quantity（诊疗为 1）    │
  │ old_purchase_amount, old_selling_amount            │
  │ purchase_impact, selling_impact                    │
  └────────────────────────────────────────────────────┘
```

### 3.1 调价项目行（PriceAdjustment）

对应 `cus_price_adjustment` 表，**一个项目一行**。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| ---- | ------ | ---- | ---- | ---- |
| 行标识 | `id` | `Long` | 是 | 雪花 ID |
| 调价单号 | `bus_no` | `String` | 是 | 单据聚合键 |
| 项目表名 | `item_table` | `String` | 是 | 见 §4.2 |
| 项目标识 | `item_id` | `Long` | 是 | 定义主键 |
| 目标进价 | `new_purchase_amount` | `BigDecimal` | 否 | 物流可调；**诊疗须为空** |
| 目标售价 | `new_selling_amount` | `BigDecimal` | 否 | 物流与诊疗；诊疗**必填** |
| 调价原因 | `adjustment_reason` | `String` | 是 | **自由文案**（手写）；历史曾存枚举 code，已迁为展示字符串 |
| 申请时间 | `adjustment_time` | `Date` | 是 | 业务申请/制单时间，不得晚于当前时间 |
| 审批状态 | `status_enum` | `String` | 是 | 见 §2.3 |
| 提交人 | `submitter_id` | `Long` | 否 | 提交时必填 |
| 提交时间 | `submit_time` | `Date` | 否 | 提交时写入 |
| 审批人 | `approver_id` | `Long` | 否 | 审批时必填 |
| 审批时间 | `approve_time` | `Date` | 否 | 审批时写入 |
| 审批意见 | `approve_comment` | `String` | 否 | 驳回时必填 |
| 操作人 | `operator_id` | `Long` | 是 | 制单人 |
| 机构 | `org_id` | `Long` | 是 | 制单机构 |

**行级约束**：

- 同一 `bus_no` 内所有行的 `item_table` **必须相同**（全药、全耗材或全诊疗活动，不混单）。
- **物流**：`new_purchase_amount` 与 `new_selling_amount` 至少一侧非空。
- **诊疗**：仅 `new_selling_amount` 非空；`new_purchase_amount` 须为空。
- 草稿 / 已拒绝可改项目行与子表；`pending` 不可改；`approved` 不可改删。

### 3.2 调价明细（PriceAdjustmentDetail）

对应 `cus_price_adjustment_detail` 表。**物流**为**库房+批号**多行（`location_id` 可空）；**诊疗**为一对一行。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| ---- | ------ | ---- | ---- | ---- |
| 明细标识 | `id` | `Long` | 是 | 雪花 ID |
| 项目行标识 | `adjustment_id` | `Long` | 是 | 主表行 `id` |
| 库位 | `location_id` | `Long` | 否 | 物流有可用库存时落库；诊疗/未入库/无库位可空；**仅查询与日结按库过滤**，不参与盈亏公式与审批写价 |
| 批号 | `lot_number` | `String` | 是 | 物流真实批号；诊疗占位 `-`（`CustomConstants.PriceAdjustment.CLINICAL_DETAIL_LOT_PLACEHOLDER`） |
| 影响数量 | `quantity` | `BigDecimal` | 是 | 物流为该库（或全院回落）库存快照数量；诊疗固定 `1` |
| 调前进价 | `old_purchase_amount` | `BigDecimal` | 否 | 物流批号进价；诊疗为空 |
| 调前售价 | `old_selling_amount` | `BigDecimal` | 否 | 物流批号售价；**诊疗必填（唯一调前价）** |
| 进价盈亏 | `purchase_impact` | `BigDecimal` | 否 | 提交时固化；诊疗为空 |
| 售价盈亏 | `selling_impact` | `BigDecimal` | 否 | 提交时固化 |

**盈亏公式（提交时计算并落库）**：

```text
purchase_impact = (主行.new_purchase_amount - old_purchase_amount) × quantity
selling_impact  = (主行.new_selling_amount   - old_selling_amount)   × quantity
```

**单据盈亏合计**（不落主表，查询时聚合）：

```text
SUM(detail.purchase_impact)  -- 按 bus_no 或全单
SUM(detail.selling_impact)
```

**回显 vs 日结**：

- **调价单详情**：应用层按 `lot_number` **合并**同批多库行（数量/盈亏相加），界面仍为全院按批号。
- **库房日结**：按所选 `location_id` 过滤子表（loc 空不计），进价盈亏参与金额账平。
- **审批写价**：按批号**去重**写 `LOT_NUMBER_*`，同批多库不重复落价。

---

## 4. 值对象 / 枚举

### 4.1 审批状态（PriceAdjustmentStatus）

| 常量 | 编码 | 中文 |
| ---- | ---- | ---- |
| `DRAFT` | `draft` | 草稿 |
| `PENDING` | `pending` | 待审批 |
| `APPROVED` | `approved` | 已生效 |
| `REJECTED` | `rejected` | 已拒绝 |
| `DEPRECATED` | `deprecated` | 已废弃 |

### 4.2 项目表（item_table）与展示口径

| 值 | 口径 `adjustmentStyle` | 说明 |
| ---- | ---------------------- | ---- |
| `med_medication_definition` | `logistics` | 药品 |
| `adm_device_definition` | `logistics` | 耗材 |
| `wkf_activity_definition` | `clinical` | 诊疗活动（套餐父项禁止调价；子项审批后级联父 SALE） |

落码判断：`PriceAdjustmentItemTablesupport；读模型字段 `PriceAdjustmentLineQueryModel.adjustmentStyle`。

### 4.3 定价类型（审批落库时涉及）

审批通过后由应用编排写入 `ChargeItemDefinition`，涉及共享枚举 `PriceType`：

| 编码 | 用途 |
| ---- | ---- |
| `PURCHASE` | 项目统一进价（主行有 `new_purchase_amount` 时更新） |
| `SALE` | 项目零售价（主行有 `new_selling_amount` 时更新） |
| `LOT_NUMBER_COST` | 各批号进价（按子表批号 + 主行目标进价） |
| `LOT_NUMBER_PRICE` | 各批号售价（按子表批号 + 主行目标售价） |

### 4.4 调整原因

`adjustment_reason` 为**必填自由文案**（制单人手写或系统写入如「中药自动调价」）。**禁止**再以 `AdjustmentReason` 枚举 code 落库；共享枚举仅作历史 code→文案迁移对照。

---

## 5. 领域事件

| 编号 | 事件 | 触发 |
| ---- | ---- | ---- |
| 5.1 | `PriceAdjustmentDraftSavedEvent` | 草稿保存 |
| 5.2 | `PriceAdjustmentSubmittedEvent` | 提交待审批（含锁清单快照） |
| 5.3 | `PriceAdjustmentApprovedEvent` | 审批通过 |
| 5.4 | `PriceAdjustmentRejectedEvent` | 审批驳回 |
| 5.5 | `PriceAdjustmentResubmittedEvent` | 驳回后重新提交 |
| 5.6 | `PriceAdjustmentWithdrawnEvent` | 草稿撤销 |

**载荷建议**：`busNo`、项目行 `id` 列表、`itemTable`、`itemIds`、操作人、租户；`Submitted` 额外携带锁定 `inventoryId` 数量；`Approved` 携带定价变更摘要。

---

## 6. 领域服务

### 6.1 写侧（PriceAdjustmentDomainService）

#### 6.1.1 保存调价草稿（`savePriceAdjustmentDraft`）

- **入参**：`SavePriceAdjustmentDraftCommand`：`bus_no`（空则新单）、`lines`（含目标价与子表快照）、`adjustment_reason`、`operator_id`、`org_id` 等。
- **物流行**：`lines[].details` 为批号列表（数量、双侧调前价）；由**应用层**按库存与定价读侧组装。
- **诊疗行**：`lines[].oldSellingAmount` 或单行 `details[0].oldSellingAmount`；领域层落一对一子表（占位批号、数量 1）。
- **行为**：校验 `item_table` 一致与分支规则；软删全量对齐主从表；`status=draft`；**不加锁**。
- **事件**：`PriceAdjustmentDraftSavedEvent`。

#### 6.1.2 提交审批（`submitPriceAdjustmentForApproval`）

- **入参**：`bus_no`、`submitter_id`、`submit_time`（可选）、`lockedInventoryCount`（可选，应用层加锁后回填事件）。
- **领域层步骤**：
  1. 本聚合 pending 互斥（**BR-PRC-SUB-002**，`assertNoOtherPendingForItems`）。
  2. 重算子表盈亏并固化（`recalculateDetailImpacts`，BR-PRC-SNAP-001、BR-PRC-013）。
  3. 子表就绪校验（`assertDetailSnapshotReady`：物流每项目多批号 quantity&gt;0；诊疗每项目恰好一行且 `old_selling_amount` 非空）。
  4. `status → pending`，写 `submitter_id`、`submit_time`。
- **应用层前置（同事务，物流）**：在途互斥（BR-PRC-SUB-003）、锁冲突预检、`inventory_lock`（BR-PRC-LOCK-001～005）；失败则不得调用领域 `submit`。
- **应用层前置（诊疗）**：无库存锁；须先 `savePriceAdjustmentDraft` 带好调前售价子表。
- **事件**：`PriceAdjustmentSubmittedEvent`（可带 `lockedInventoryCount`）。

#### 6.1.3 审批通过（`approvePriceAdjustment`）

- **入参**：`bus_no`、`approver_id`、`approve_comment`（可选）、`approve_time`（可选）。
- **领域层行为**：`status → approved`；**不重算**子表盈亏。
- **应用层同事务**：物流须 `unlockByBusNo`；写 `ChargeItemDefinition`（停用旧 ACTIVE + 新增 ACTIVE，BR-PRC-APR-003～005）。
- **事件**：`PriceAdjustmentApprovedEvent`。

#### 6.1.4 审批驳回（`rejectPriceAdjustment`）

- **入参**：`bus_no`、`approver_id`、`approve_comment`（必填）、`approve_time`（可选）。
- **领域层行为**：`status → rejected`。
- **应用层同事务**：物流须 `unlockByBusNo`。
- **事件**：`PriceAdjustmentRejectedEvent`。

#### 6.1.5 重新提交（`resubmitPriceAdjustmentForApproval`）

- **前置**：`rejected`；须先 `savePriceAdjustmentDraft` 修正内容。
- **行为**：同 **6.1.2**（领域层步骤 + 应用层物流加锁编排）。

#### 6.1.6 撤销草稿（`withdrawPriceAdjustmentDraft`）

- **前置**：仅 `draft`。
- **行为**：`status → deprecated`；无锁操作。
- **事件**：`PriceAdjustmentWithdrawnEvent`。

> **本期不含**紧急强制作废解锁（`forceVoid`）；后续版本单独 AS/DM 扩展。

### 6.2 读侧（PriceAdjustmentQueryService）

**仅本聚合 Repository**；不注入他聚合 QueryService（B.6.4）。

| 方法 | 说明 |
| ---- | ---- |
| `getDocumentByBusNo` | 主行 + 子表树；行含 `adjustmentStyle` |
| `countDistinctBusNo` | 列表：按过滤条件统计不重复 `bus_no`（对齐进货单列表；**含财务审批扩展过滤**，见下） |
| `listDistinctBusNoPage` | 列表：按 `bus_no` 分组分页；排序键 `COALESCE(submit_time, adjustment_time)` 降序 |
| `listLinesByBusNosWithFilter` | 列表：加载当前页单号下全部主行 |
| `listDocumentsByBusNos` | 按单号列表组装单据读模型（应用层列表装配） |
| `existsPendingByItem` | `item_table + item_id` 是否有 pending（互斥 / 临床阻断） |
| `existsPendingByAnyItem` | 同表多 `item_id` 任一存在 pending（签发前批量互斥，BR-PRC-LOCK-007） |
| `sumImpactByBusNo` | 汇总 `purchase_impact` / `selling_impact` |
| `sumApprovedPurchaseImpactByLocation` | 按库位汇总期间已审批进价盈亏（日结；仅 `location_id` 匹配行） |
| `findApprovedHistoryByItem` | 某项目已生效调价历史（单品）；批量须用 `findApprovedHistoryByItems` |
| `findApprovedHistoryByItems` | **批量**已生效调价历史（一次主表 IN + 子表）；须先 `listApprovedItemIds` 收窄——**禁止**对无调价品规拉历史 |
| `listApprovedItemIds` | 批量筛出存在已审批调价的 itemId（报表进价是否需回放） |
| `existsApprovedByItem` | 单品是否存在已审批调价（禁止在报表循环中逐行调用；改用 `listApprovedItemIds`） |
| `countApprovedLines` | 调价明细：统计已生效主行（`approve_time` 窗 + `item_table` + `item_id IN` + 可选 `bus_no`） |
| `pageApprovedLines` | 调价明细：已生效主行分页；`approve_time` 降序；不含子表，含行级盈亏合计 |
| `getApprovedLineById` | 调价明细：单条已生效主行 + 全部子表明细 |
| `listPendingDocuments` | （兼容）待审批列表；**推荐**统一走 `PriceAdjustmentDocumentListQuery` + `status=pending` |
| `listByOrgAndSubmitTime` | （兼容）仅 `submit_time` 窗；**推荐**列表走 `countDistinctBusNo` 链路 |

**财务审批·调价侧列表（AS-FIN-APR-001 · IF-APR-02）**

- **边界**：只读；**仅本聚合** QS；与供应侧并集由应用层 **`FinanceApprovalDocumentPageLoader`** 编排（B.6.4，禁止本 QS 注入 `SupplyRequestQueryService`）。
- **过滤扩展（`PriceAdjustmentDocumentListQuery`）**：`itemTable`、`operatorId`（制单人）、`approverId`、`busNoLike`、`adjustmentTimeFrom/To`（制单）、`approveTimeFrom/To`（审批）、`excludeAdjustmentReasons`（原因 NOT IN）；待审 `statusEnum=pending`。
- **实现锚点**：`PriceAdjustmentDocumentListPredicateSupport#collectPredicates`。
- **默认值**：服务端**不得**写死默认进货/待审（默认仅前端携带）。

**`application.financial.priceadjustment.support`（已落地，不在领域 QueryService）**：

| 能力 | 类 | 说明 |
| ---- | ---- | ---- |
| 在途互斥预检 | `PriceAdjustmentInTransitChecker` | **硬在途**（已签发申请+发放）（BR-PRC-SUB-003）；未签发 DRAFT 不在此合并 |
| 未签发草稿策略 | `PriceAdjustmentUnsignedDraftPolicySupport` | 本单物流项目：≤48h 提示医生/患者/项目名；>48h 静默废弃（BR-PRC-SUB-003a） |
| 锁预览 | `PriceAdjustmentLockPreviewLoader` | 物流提交前 UI 确认将锁库存行（BR-PRC-LOCK-008） |
| 子表快照组装 | `PriceAdjustmentLogisticsSnapshotAssembler` | 库存批号 + 定价读侧 → 草稿 `details` |
| 审批写价 | `PriceAdjustmentApprovePricingOrchestration` | 停用旧 ACTIVE + 新增 ACTIVE（BR-PRC-APR-003～005） |

**`application.shared`（跨站配套，不在本聚合）**：

| 能力 | 类 | 说明 |
| ---- | ---- | ---- |
| 签发 pending 门禁 | `PendingPriceAdjustmentSignGate` | 消费 `existsPendingByAnyItem`（BR-PRC-LOCK-007） |
| 未签发草稿废弃 | `UnsignedOrderDraftAbandonSupport` | 调价保存/提交调用：仅本单调价项目上药/耗 DRAFT→CANCELLED（超过 48h；不软删；**无定时任务**） |

### 6.3 领域边界

- **本聚合写**：仅 `cus_price_adjustment` / `cus_price_adjustment_detail` 与状态迁移；**不注入**他聚合 QueryService / DomainService。
- **库存锁**：**应用层**调用 `InventoryItemDomainService`（仅物流，`inventory_lock.bus_no = 调价 bus_no`）。
- **定价写**：**应用层**审批通过调用 `ChargeItemDefinitionDomainService`。
- **临床阻断**：各入口调用 `existsPendingByItem` / `existsPendingByAnyItem`；pending 期间相关操作由产品规则阻断（含医生站签发）。

---

## 7. 不变式

### 7.1 单据与数据

| 编号 | 规则 |
| ---- | ---- |
| BR-PRC-001 | 生效须先 `pending` 再 `approved` |
| BR-PRC-002 | 草稿可提交或撤销 |
| BR-PRC-003 | `pending` 可审批通过或驳回 |
| BR-PRC-004 | `approved` 行不可改删 |
| BR-PRC-005 | `rejected` 可修正后重新提交 |
| BR-PRC-006 | 物流：主行目标进价、目标售价至少一侧非空；诊疗：仅目标售价非空、目标进价须为空 |
| BR-PRC-007 | `adjustment_reason` 必填 |
| BR-PRC-008 | `operator_id`、`org_id` 必填 |
| BR-PRC-009 | `adjustment_time` 不得晚于当前时间 |
| BR-PRC-010 | 同一 `bus_no` 内 `item_table` 必须相同 |
| BR-PRC-011 | 物流：子表真实批号必填；诊疗：占位批号 `-`、数量 `1`、调前售价必填 |
| BR-PRC-012 | 子表不存调后价；调后价仅主行 |
| BR-PRC-013 | 提交时盈亏公式与主行目标价、子表调前价、数量一致 |
| BR-PRC-014 | 单据盈亏由子表 `SUM`，主表不冗余合计字段 |
| BR-PRC-015 | 驳回时 `approve_comment` 必填 |
| BR-PRC-016 | 提交时 `submitter_id` 必填 |
| BR-PRC-017 | 审批时 `approver_id` 必填 |

### 7.2 提交互斥（BR-PRC-SUB）

| 编号 | 规则 |
| ---- | ---- |
| BR-PRC-SUB-001 | 同一 `bus_no` 仅允许一种 `item_table` |
| BR-PRC-SUB-002 | 同一 `item_table + item_id` 同时仅一张 `pending` 调价单 |
| BR-PRC-SUB-003 | **应用层**提交前：物流硬在途（已签发申请/发放）硬拦 |
| BR-PRC-SUB-003a | **仅本单调价项目**：≤48h 未签发药/耗 DRAFT → 阻断并提示开立医生/患者/项目名（须自行删除）；>48h → 保存/提交时静默 CANCELLED（不软删）；写前再确认未签发，已签发跳过；单外项目不动；无定时任务 |

### 7.3 快照（BR-PRC-SNAP）

| 编号 | 规则 |
| ---- | ---- |
| BR-PRC-SNAP-001 | `pending` 后子表数量、调前价、盈亏冻结，审批前不可变 |
| BR-PRC-SNAP-002 | 审批通过不重算子表；盈亏代表提交时刻库存影响 |

### 7.4 全院锁与防误锁（BR-PRC-LOCK，仅物流）

| 编号 | 规则 |
| ---- | ---- |
| BR-PRC-LOCK-001 | 仅物流 `submit → pending` 时由**应用层**加锁；草稿与诊疗不加锁 |
| BR-PRC-LOCK-002 | 锁范围：本单每个 `item_table + item_id` 下全院 `ACTIVE`、`quantity>0` 库存行 |
| BR-PRC-LOCK-003 | **禁止**锁入本单未包含的其他 `item_id` |
| BR-PRC-LOCK-004 | 若 `inventory_id` 已被其他 `bus_no` 锁定，**整单提交失败**（应用层预检） |
| BR-PRC-LOCK-005 | 应用层：互斥 → 加锁 → 领域 `submit` **同一事务** |
| BR-PRC-LOCK-006 | **应用层**在 `approve` / `reject` 时 `unlockByBusNo(bus_no)`（物流） |
| BR-PRC-LOCK-007 | `pending` 期间物流项目：相关临床/物流入口阻断（`existsPendingByItem` / `existsPendingByAnyItem`）；医生站签发经应用门禁整批拒绝 |
| BR-PRC-LOCK-008 | **应用层**提供 `previewSubmitLocks` 预览（`application.financial.priceadjustment.support`，非领域 QS） |

### 7.5 诊疗调价（BR-PRC-CLIN）

| 编号 | 规则 |
| ---- | ---- |
| BR-PRC-CLIN-001 | `item_table` **仅** `wkf_activity_definition`（不含医疗服务）；套餐父项（含子表）禁止入单 |
| BR-PRC-CLIN-002 | 主行仅 `new_selling_amount`；`new_purchase_amount` 须为空 |
| BR-PRC-CLIN-003 | 子表与主行一对一；仅 `old_selling_amount` 有值；`quantity=1` |
| BR-PRC-CLIN-004 | 不涉及 `inventory_lock` 与批号库存快照；审批后级联重算引用子项的父套餐 SALE |

### 7.6 审批落价（BR-PRC-APR）

| 编号 | 规则 |
| ---- | ---- |
| BR-PRC-APR-001 | 通过时：有 `new_purchase_amount` 则处理 `PURCHASE` 与各批 `LOT_NUMBER_COST`；有 `new_selling_amount` 则处理 `SALE` 与各批 `LOT_NUMBER_PRICE`（具体停用+新增见 APR-003～005） |
| BR-PRC-APR-002 | 批号价目标值取自主行目标价，不按批差异化目标价 |
| BR-PRC-APR-003 | **禁止物理删除**旧定价行；须将匹配到的当前 **ACTIVE** 行 `status_enum` 迁为 **`INACTIVE`（停用）** |
| BR-PRC-APR-004 | **物流**：按价类+批号匹配停用旧 **ACTIVE** 行后，**新增** `status_enum=ACTIVE` 行，`price_value` 为调价单目标价（`PURCHASE`/`SALE`/`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`） |
| BR-PRC-APR-005 | **诊疗**：停用旧 **ACTIVE `SALE`**（若存在）后 **新增 ACTIVE `SALE`**；不涉及进价与批号价类 |

---

## 8. 上下文映射

### 8.1 上游

| 上下文 | 说明 |
| ------ | ---- |
| 药品/耗材/诊疗定义 | `item_table` + `item_id` |
| InventoryItem | **应用层**物流批号展开与锁 |
| 定价读侧 | **应用层**组装调前价 |
| 医嘱/发药/护士站 | **应用层**互斥与 pending 阻断 |

### 8.2 下游

| 上下文 | 说明 |
| ------ | ---- |
| ChargeItemDefinition | 审批通过后写价 |
| 成本/对账 | 引用子表盈亏汇总 |

### 8.3 事件流

```text
调价草稿 save（应用层组装子表快照）
    → 应用层：互斥+加锁（物流）
    → 领域 submit → pending
        → 应用层 approve：写定价 + unlock（物流）→ 领域 approved
        → 应用层 reject：unlock（物流）→ 领域 rejected
```

---

## 9. 补充

### 9.1 业务示例

**物流**调价单 `TJ001`（药品），A 药一行：新进价 20、新售价 20。

| 批号 | 数量 | 调前进价 | 调前售价 | 进价盈亏 | 售价盈亏 |
| ---- | ---- | -------- | -------- | -------- | -------- |
| 001 | 10 | 10 | 10 | +100 | +100 |
| 002 | 10 | 30 | 30 | -100 | -100 |

合计进价盈亏 0，售价盈亏 0（`SUM(detail)`）。

**诊疗**调价单 `TJ002`（活动 `wkf_activity_definition`），项目 X：调前售价 50 → 目标售价 80。

| 批号 | 数量 | 调前售价 | 售价盈亏 |
| ---- | ---- | -------- | -------- |
| - | 1 | 50 | +30 |

### 9.2 与 v1.0 单表模型差异

| v1.0 | v2.0 |
| ---- | ---- |
| 单行一种 `price_type` | 主行同时可带新进价、新售价 |
| 批号在主表 | 批号在子表 |
| 无 `bus_no` | `bus_no` 单据聚合 |
| 无库存锁 | 物流提交 pending 全院锁（应用层） |
| 含诊疗/稳定价流水 | 诊疗与物流共用主从表，展示口径不同 |

### 9.3 后续规划（本期不实现）

- 紧急强制作废并解锁（`forceVoid`）、审批后定价回滚快照。
- 在途互斥二期：护士待执行槽、护士划价未完结（应用层编排）。
- **应用层**：`PriceAdjustmentAppService`、锁预览/在途 `application.financial.priceadjustment.support`、审批写价编排。

### 9.4 工程落点（与 zxplat 对齐）

| 项 | 落点 |
| ---- | ---- |
| 领域模块 | `zxplat/zxplat-domain`，包 `com.zxplat.domain.custom.priceadjustment` |
| 口径支持类 | `support.PriceAdjustmentItemTablesupport |
| 常量 | `CustomConstants.PriceAdjustment`（占位批号、adjustmentStyle） |
| 事件包 | `zxplat-domain` … `share.events.custom.priceadjustment` |
| Flyway | `zxplat-domain` … `V76__cus_price_adjustment_v2.sql` |
| 领域写边界 | 仅本聚合 Repository；跨聚合由应用层编排 |
| 子表对齐 | 领域服务软删旧明细（`is_deleted=true`） |
| 错误码 | `004` pending、`005` 在途、`006` 锁冲突、`007` item_table 不一致 |

### 9.5 变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v2.1.7 | 2026-09-16 | **BR-PRC-SUB-003a**：取消定时任务；本单项目 48h 提示 / 超 48h 静默 CANCELLED；对齐 AS-FIN-PRC-001 v0.12 |
| v2.1.6 | 2026-09-16 | （已由 v2.1.7 取代）开立满 7 天药/耗/诊 DRAFT→CANCELLED；软在途 confirmAbandon；对齐 AS v0.11 |
| v2.1.5 | 2026-09-16 | §6.2 `existsPendingByAnyItem`；**BR-PRC-LOCK-007** 签发门禁与陈旧草稿清理配套（AS-FIN-PRC-001 v0.10） |
| v2.1.4 | 2026-07-31 | 朝阳期初后清库：`cus_price_adjustment` / `_detail` 保留（中药入库自动调价审计） |
| v2.1.3 | 2026-07-26 | 子表可空 `location_id`（库房+批号落库）；回显按批号合并；日结 `sumApprovedPurchaseImpactByLocation`；审批写价按批号去重 |
| v2.1.2 | 2026-07-21 | §6.2：财务审批调价侧列表扩展过滤 + 跨聚合并集 Loader 契约（AS-FIN-APR-001 / GAP-APR-002） |
| v2.1.1 | 2026-06-27 | §6.2 增补调价明细读侧：`countApprovedLines` / `pageApprovedLines` / `getApprovedLineById`（AS-FIN-PRC-002） |
| v2.1 | 2026-06-27 | 纳入诊疗调价（共用主从表、一对一子表）；领域层纯聚合；跨聚合归应用层 |
| v2.0 | 2026-06-27 | 主从表单据模型、`bus_no`、物流全院锁 |
| v2.0.1 | 2026-06-27 | Flyway V76；领域层与代码初版对账 |
| v1.0 | — | 单表流水型调价 |

---

*领域模型负责人：[负责人] | 关联 SDD：`DM-CUS-011-PriceAdjustment-价格调整-SDD.md`*

*最后更新：2026-06-27 — v2.1（诊疗调价 + 领域层边界）*

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PriceAdjustmentDomainService`

| 方法 |
|------|
| `savePriceAdjustmentDraft` |
| `submitPriceAdjustmentForApproval` |
| `approvePriceAdjustment` |
| `rejectPriceAdjustment` |
| `resubmitPriceAdjustmentForApproval` |
| `withdrawPriceAdjustmentDraft` |

### QueryService：`PriceAdjustmentQueryService`

| 方法 |
|------|
| `getDocumentByBusNo` |
| `listPendingDocuments` |
| `existsPendingByItem` |
| `existsPendingByAnyItem` |
| `existsApprovedByItem` |
| `listApprovedItemIds` |
| `listApprovedItemLotCompositeKeys` |
| `itemLotCompositeKey` |
| `countDistinctBusNo` |
| `listDistinctBusNoPage` |
| `listLinesByBusNosWithFilter` |
| `listDocumentsByBusNos` |
| `sumImpactByBusNo` |
| `findApprovedHistoryByItem` |
| `findApprovedHistoryByItems` |
| `sumApprovedPurchaseImpactByLocation` |
| `countApprovedLines` |
| `pageApprovedLines` |
| `getApprovedLineById` |
| `listByOrgAndSubmitTime` |

