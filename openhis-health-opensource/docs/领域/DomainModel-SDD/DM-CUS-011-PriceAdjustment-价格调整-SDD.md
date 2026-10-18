# DM-CUS-011 PriceAdjustment 价格调整 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-011-PriceAdjustment-价格调整.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：PriceAdjustment（主表项目行）+ PriceAdjustmentDetail（子表明细）；单据键 `bus_no`；物流库房+批号多行（`location_id` 可空）/ 诊疗一对一

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-CUS-011 v2.1.3 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 | 满足 | `com.zxplat.domain.custom.priceadjustment` |
| 固定技术口径 | 满足 | Long 主键、`Date`/`LocalDateTime` 审计、软删、beforeCommit |
| DM §6 写/读白名单 | 满足 | §5 |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | §7 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化**单据型**调价：主表项目行 + 子表明细 + `bus_no` 聚合。
- **物流**（药品/耗材）：子表按**库房+批号**多行（`location_id` 可空）；提交前快照/拆库/加锁由**应用层**编排；调价单回显按批号合并。
- **诊疗**：一对一子表仅存调前售价（loc 空）；无库存锁。
- 读侧 `adjustmentStyle` 区分展示；`existsPendingByItem` 供各站 pending 阻断。

### 1.2 边界

- **在内**：`cus_price_adjustment`、`cus_price_adjustment_detail`、状态机、本聚合互斥查询、盈亏汇总读模型。
- **在外**：页面拼装、库存快照组装、在途互斥、锁预览/加锁/解锁、`ChargeItemDefinition` 写价（**应用层**或 `已删除，见 zxplat/DISSOLUTION.md）。
- **领域层禁止**：注入他聚合 `*QueryService` / `*DomainService`（B.6.4）。
- **本期不含**：`forceVoid` 紧急解锁。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

`zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.custom.priceadjustment`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PriceAdjustment`、`PriceAdjustmentDetail` |
| `command` | 一方法一 Command |
| `service` | `PriceAdjustmentDomainService`、`PriceAdjustmentQueryService` |
| `support | `PriceAdjustmentItemTablesupport（item_table 口径判断） |
| `queries` | `*Query` |
| `queries.model` | `*QueryModel` |
| `repository` | `PriceAdjustmentRepository`、`PriceAdjustmentDetailRepository` |
| `share.events` | `com.zxplat.domain.share.events.custom.priceadjustment` |
| `share.constants` | `CustomConstants.PriceAdjustment`（占位批号、adjustmentStyle 常量） |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long` |
| 业务时间 | `Date`；审计 `LocalDateTime` |
| `status_enum` | `String`，存 `PriceAdjustmentStatus.code` |
| `item_table` | `String`；见 §4.2 |
| 金额 / 数量 | `BigDecimal` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` + `@Table` + `@Column` |
| 仓储 | 仅 `JpaRepository` 接口 |
| 事件 | `publishBeforeCommit` |

---

## 4. 实体 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_price_adjustment` | `PriceAdjustment` | 调价项目行（同 `bus_no` 多行） |
| `cus_price_adjustment_detail` | `PriceAdjustmentDetail` | 物流库房+批号多行（`location_id` 可空）/ 诊疗一对一 |

### 4.2 实体 `PriceAdjustment`（主表）

| 字段名（Java） | 列名 | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- | ---- |
| `id` | `id` | `Long` | 是 | 主键 |
| `busNo` | `bus_no` | `String` | 是 | 调价单号 |
| `itemTable` | `item_table` | `String` | 是 | 见 DM §4.2 |
| `itemId` | `item_id` | `Long` | 是 | 项目定义主键 |
| `newPurchaseAmount` | `new_purchase_amount` | `BigDecimal` | 否 | 物流可选；诊疗须为空 |
| `newSellingAmount` | `new_selling_amount` | `BigDecimal` | 否 | 物流/诊疗；诊疗必填 |
| `adjustmentReason` | `adjustment_reason` | `String` | 是 | 调价原因（自由文案） |
| `adjustmentTime` | `adjustment_time` | `Date` | 是 | 制单时间 |
| `statusEnum` | `status_enum` | `String` | 是 | draft/pending/approved/rejected/deprecated |
| `submitterId` | `submitter_id` | `Long` | 否 | 提交人 |
| `submitTime` | `submit_time` | `Date` | 否 | 提交时间 |
| `approverId` | `approver_id` | `Long` | 否 | 审批人 |
| `approveTime` | `approve_time` | `Date` | 否 | 审批时间 |
| `approveComment` | `approve_comment` | `String` | 否 | 审批意见 |
| `operatorId` | `operator_id` | `Long` | 是 | 操作人 |
| `orgId` | `org_id` | `Long` | 是 | 机构 |

**实体方法（落码）**：`initDraft()`、`submit(...)`、`approve(...)`、`reject(...)`、`withdraw()`、`assertEditable()`、`assertRequiredFields()`（通用必填；分支规则在 DomainService `assertLineBusinessRules`）。

### 4.3 实体 `PriceAdjustmentDetail`（子表）

| 字段名（Java） | 列名 | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- | ---- |
| `id` | `id` | `Long` | 是 | 主键 |
| `adjustmentId` | `adjustment_id` | `Long` | 是 | 主表行 id |
| `lotNumber` | `lot_number` | `String` | 是 | 物流批号；诊疗占位 `-` |
| `quantity` | `quantity` | `BigDecimal` | 是 | 物流库存数量；诊疗 `1` |
| `oldPurchaseAmount` | `old_purchase_amount` | `BigDecimal` | 否 | 物流调前进价 |
| `oldSellingAmount` | `old_selling_amount` | `BigDecimal` | 否 | 调前售价；诊疗必填 |
| `purchaseImpact` | `purchase_impact` | `BigDecimal` | 否 | 进价盈亏 |
| `sellingImpact` | `selling_impact` | `BigDecimal` | 否 | 售价盈亏 |

**实体方法**：`recalculateImpact(newPurchase, newSelling)`（提交固化时由领域服务调用）。

### 4.4 仓储接口

与 v2.0.1 一致；子表全量对齐由 `PriceAdjustmentDomainService` 软删后 `saveAll`。

### 4.5 查询模型（`queries.model`）

#### 4.5.1 `PriceAdjustmentDocumentQueryModel`

单据读模型：`busNo`、`statusEnum`、审批字段、`lines`。

#### 4.5.2 `PriceAdjustmentLineQueryModel`

主行 + `adjustmentStyle`（`clinical` / `logistics`）+ `details`；项目名称由应用层填充。

#### 4.5.3 `PriceAdjustmentDetailQueryModel`

子表字段；诊疗 UI 可隐藏批号/数量列。

#### 4.5.4 `PriceAdjustmentImpactSummaryQueryModel`

`busNo`、`totalPurchaseImpact`、`totalSellingImpact`。

#### 4.5.5 应用层读模型（不在领域 QS）

| 模型 | 用途 |
| ---- | ---- |
| `PriceAdjustmentLockPreviewQueryModel` | 物流提交前锁预览（`application.financial.priceadjustment.support`） |
| `PriceAdjustmentInTransitCheckQueryModel` | 物流在途互斥预检（应用层编排） |

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `PriceAdjustmentDomainService`

**仅注入**：`PriceAdjustmentRepository`、`PriceAdjustmentDetailRepository`、`IDomainEventPublisher`、`BusinessSequenceService`。

#### 5.1.1 `savePriceAdjustmentDraft` → `String busNo`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `busNo` | `String` | 空则生成 |
| `operatorId` | `Long` | 操作人 |
| `orgId` | `Long` | 机构 |
| `adjustmentReason` | `String` | 原因 |
| `adjustmentTime` | `Date` | 制单时间 |
| `lines` | `List<Line>` | 项目行 |

**`SavePriceAdjustmentDraftCommand.Line`**：

| 字段 | 说明 |
| ---- | ---- |
| `itemTable` / `itemId` | 项目锚点 |
| `newPurchaseAmount` / `newSellingAmount` | 目标价（诊疗仅售价） |
| `oldSellingAmount` | 诊疗调前售价（与 `details` 二选一） |
| `details` | 物流批号明细列表 |

**`DetailLine`**：`lotNumber`、`quantity`、`oldPurchaseAmount`、`oldSellingAmount`。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields()` + `assertLineBusinessRules`（BR-PRC-006、BR-PRC-CLIN） |
| `statusTransitionMethod` | `initDraft`；已存在须 `draft`/`rejected` |
| `repositoryChecks` | 软删全量对齐子表 |
| `eventPublishAnchor` | `PriceAdjustmentDraftSavedEvent` |

**不加锁**；子表快照由命令入参带入（应用层组装）。

#### 5.1.2 `submitPriceAdjustmentForApproval` → `void`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `busNo` | `String` | 调价单号 |
| `submitterId` | `Long` | 提交人 |
| `submitTime` | `Date` | 可选 |
| `lockedInventoryCount` | `Integer` | 应用层加锁后回填事件 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `submit`；`assertDetailSnapshotReady` |
| `repositoryChecks` | `assertNoOtherPendingForItems`（BR-PRC-SUB-002） |
| `crossDomain` | **无**（锁/在途在应用层前置） |
| `eventPublishAnchor` | `PriceAdjustmentSubmittedEvent` |

**领域层步骤**：

1. `assertNoOtherPendingForItems`
2. `recalculateDetailImpacts`
3. `assertDetailSnapshotReady`
4. 主行 `submit`

#### 5.1.3 `approvePriceAdjustment` → `void`

领域层仅 `approve` 状态迁移；解锁与写价由应用层同事务编排。

#### 5.1.4 `rejectPriceAdjustment` → `void`

领域层仅 `reject`；物流解锁由应用层编排。

#### 5.1.5 `resubmitPriceAdjustmentForApproval` → `void`

同 **5.1.2**；入参含 `lockedInventoryCount`。

#### 5.1.6 `withdrawPriceAdjustmentDraft` → `void`

`draft` → `deprecated`。

---

### 5.2 读侧 — `PriceAdjustmentQueryService`

**仅注入本聚合 Repository**（13 个方法）。

| 方法 | 入参 | 出参 | 说明 |
| ---- | ---- | ---- | ---- |
| `getDocumentByBusNo` | `busNo` | `PriceAdjustmentDocumentQueryModel` | 含 `adjustmentStyle` |
| `countDistinctBusNo` | `PriceAdjustmentDocumentListQuery` | `long` | 列表统计不重复 `bus_no`（对齐进货单） |
| `listDistinctBusNoPage` | `PriceAdjustmentDocumentListQuery` | `List<String>` | 按 `bus_no` 分页；排序键 `COALESCE(submit_time, adjustment_time)` |
| `listLinesByBusNosWithFilter` | `query`, `busNos` | `List<PriceAdjustment>` | 同过滤条件下加载当前页主行 |
| `listDocumentsByBusNos` | `busNos`, `groupedLines` | `List<DocumentQueryModel>` | 批量 IN 子表组装读模型 |
| `listPendingDocuments` | `orgId?`, `Pageable` | `Page<...>` | （兼容）审批台 |
| `existsPendingByItem` | `itemTable`, `itemId` | `Boolean` | pending 互斥 / 阻断 |
| `sumImpactByBusNo` | `busNo` | `PriceAdjustmentImpactSummaryQueryModel` | SUM 盈亏 |
| `sumApprovedPurchaseImpactByLocation` | `itemTable`, `itemIds?`, `locationId`, 审批窗 | `Map<itemId, purchaseImpact>` | 日结本库进价盈亏；仅 `location_id` 匹配；loc 空不计 |
| `findApprovedHistoryByItem` | `itemTable`, `itemId` | `List<LineQueryModel>` | 已生效历史 |
| `countApprovedLines` | `PriceAdjustmentApprovedLineListQuery` | `long` | 调价明细主行计数；硬过滤 `approved`；`approve_time` 窗 |
| `pageApprovedLines` | `PriceAdjustmentApprovedLineListQuery` | `List<LineQueryModel>` | 调价明细主行分页；不含子表；含 `totalImpact` |
| `getApprovedLineById` | `id` | `LineQueryModel` | 已生效主行 + 子表；非 approved 视为不存在 |
| `listByOrgAndSubmitTime` | `orgId`, 时间, `Pageable` | `Page<...>` | （兼容）仅 `submit_time` 窗 |

#### 5.2.1 跨聚合读组装（`application.financial.priceadjustment.support`，非领域 QS）

| 能力 | 类 / 方法 | 说明 |
| ---- | --------- | ---- |
| 在途互斥 | `PriceAdjustmentInTransitChecker#checkByBusNo` / `#checkLine` | BR-PRC-SUB-003；药品/耗材 Request+Dispense |
| 锁预览 | `PriceAdjustmentLockPreviewLoader#loadByBusNo` | BR-PRC-LOCK-008 |
| 库存快照 | `PriceAdjustmentLogisticsSnapshotAssembler#assemble` | 物流子表 `details` 草稿结构（**按批号聚合**，供 IF-PRC-05 回显） |
| 落库拆库 | `PriceAdjustmentLogisticsDetailLocationExpandSupport#expandLogisticsDetailsByLocation` | IF-PRC-09：按库房+批号落 `location_id`（可空） |
| 审批写价 | `PriceAdjustmentApprovePricingOrchestration#applyApprovedPricing` | 停用旧 ACTIVE + 新增 ACTIVE；**批号去重**（BR-PRC-APR-003～005） |

**仍归应用层**：库存加锁/解锁（`InventoryItemDomainService`）、`PriceAdjustmentAppService` 编排。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| BR-PRC-001～017 | 实体状态方法 + DomainService 分支校验 |
| BR-PRC-SUB-001 | `assertLinesItemTableConsistent` |
| BR-PRC-SUB-002 | `assertNoOtherPendingForItems` |
| BR-PRC-SUB-003 | **querysupport** `PriceAdjustmentInTransitChecker` + 应用层提交 Gate |
| BR-PRC-SNAP-001～002 | `recalculateDetailImpacts`；approve 不重算 |
| BR-PRC-LOCK-001～008 | **应用层**加锁/解锁（仅物流）；预览 **querysupport** |
| BR-PRC-CLIN-001～004 | `assertLineBusinessRules`、`persistClinicalDetailFromCommand` |
| BR-PRC-APR-001～002 | **querysupport** `applyApprovedPricing` + 应用层 IF-PRC-13 编排 |

---

## 7. 事件契约（DM §5）

| 事件类 | 触发方法 | 最小载荷 |
| ------ | -------- | -------- |
| `PriceAdjustmentDraftSavedEvent` | `savePriceAdjustmentDraft` | `busNo` 等快照字段 |
| `PriceAdjustmentSubmittedEvent` | `submitPriceAdjustmentForApproval` | `busNo`, `submitterId`, `lockedInventoryCount`（可选） |
| `PriceAdjustmentApprovedEvent` | `approvePriceAdjustment` | `busNo`, `approverId` |
| `PriceAdjustmentRejectedEvent` | `rejectPriceAdjustment` | `busNo`, `approverId` |
| `PriceAdjustmentResubmittedEvent` | `resubmitPriceAdjustmentForApproval` | 同 Submitted |
| `PriceAdjustmentWithdrawnEvent` | `withdrawPriceAdjustmentDraft` | `busNo` |

---

## 8. 删除与可见性

逻辑删除；草稿全量对齐子表；`approved` 不可删改。

---

## 9. 库存锁协作（应用层，仅物流）

加锁/解锁/预览见 DM §7.4；领域层不参与。

---

## 10. Flyway 迁移（已落码）

`V76__cus_price_adjustment_v2.sql` — 诊疗与物流共用表结构，无额外迁移。

---

## 11. 生成与验收清单（2026-06-27 v2.1）

- [x] 主从实体与 §4.2、§4.3 一致（含诊疗口径）
- [x] 写方法 6 个；领域层仅本聚合 Repository
- [x] 读方法 13 个（含列表四步 + 调价明细三方法 + 兼容两条）；无他聚合 QS 注入
- [x] `PriceAdjustmentItemTablesupport + `adjustmentStyle` 读模型
- [x] `SavePriceAdjustmentDraftCommand` 含诊疗 `oldSellingAmount`
- [x] `Submit`/`Resubmit` 含 `lockedInventoryCount`
- [x] `application.financial.priceadjustment.support`：锁预览、在途预检、快照、审批写价编排
- [ ] 应用层：`PriceAdjustmentAppService`、库存加锁/解锁编排

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| ---- | ---- |
| 2026-07-26 | **v2.1.3**：子表可空 `location_id`；`sumApprovedPurchaseImpactByLocation`；落库拆库 ExpandSupport；回显批号合并；审批写价批号去重 |
| 2026-06-27 | **v2.1.2**：调价明细读侧 `countApprovedLines` / `pageApprovedLines` / `getApprovedLineById`（AS-FIN-PRC-002 Gap 关闭） |
| 2026-06-27 | **v2.1.1**：列表四步 Query + querysupport 四类组件落地；应用层仍待成码 |
| 2026-06-27 | **v2.1**：诊疗调价共用主从表；领域层纯聚合；跨聚合归 querysupport/应用层 |
| 2026-06-27 | v2.0 / v2.0.1 物流单据模型与 Flyway V76 |
| 2026-05-28 | v1.0 单表流水（superseded） |

---

**本 SDD 正文结束。**
