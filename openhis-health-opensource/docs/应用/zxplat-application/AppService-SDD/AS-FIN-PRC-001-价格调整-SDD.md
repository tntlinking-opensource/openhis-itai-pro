# 财务管理 · 价格调整 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-FIN-PRC-001-价格调整.md`（v0.12）  
> **编码**：AS-FIN-PRC-001-SDD · **版本**：v0.12 · **日期**：2026-09-16

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 财务管理 · 价格调整（`finance/priceadjustment`；前端 `views/finance/price-adjustment`） |
| **菜单** | **一级「财务管理」** → **二级「价格调整」**（唯一二级；**无**再套 financial/财务 三级分组） |
| **边界** | IF-PRC-01～14：单页全链路；querysupport 负责物流快照、在途预检、锁预览 |
| **编排权威** | `AS-FIN-PRC-001` §2、§4 |
| **方法权威** | `DM-CUS-011` §6.1 `PriceAdjustmentDomainService`；§6.2 `PriceAdjustmentQueryService`；`DM-WKF-008` §6.1.5～6.1.6；`DM-ADM-010` 定价写 |
| **非目标** | `forceVoid`；Redis 锁；SupplyRequest 共表路径 |
| **实现阶段口径** | 领域层 + querysupport **已落地**；**应用层待成码** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | 一律 `POST /api/app/priceAdjustment/{methodName}`（`AppServiceControllerRegistrar`） |
| **包路径归口** | **制单 + 审批同一 AppService**；`application.finance.priceadjustment`（院级 finance 菜单域，对齐 `/finance/*` 路由）；**不用** `application.financial`（与 `outpatient.financial` / `inpatient.financial` 门诊住院收费混淆） |
| **querysupport** | `application.financial.priceadjustment.support`：跨聚合读组装；**禁止** import `application.*` DTO |
| **事务** | AppService **不**声明 `@Transactional`；`@AppService` 切面 + 领域单事务；IF-PRC-10/13/14 多 Cmd 须同事务 |
| **操作人** | `operatorId` / `submitterId` / `approverId` 由 `CurrentPractitionerResolver` / 会话医务人员解析，**不经**普通业务 Request 代传（审批驳回解锁审计 `operatorId` 例外可走 Command） |
| **操作科室** | 制单写路径 `org_id`、医疗服务选品过滤由 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 解析（**B.6.5**）；**禁止** Request 传 `orgId`。**IF-PRC-02 列表不按科室收窄**（Query.`orgId=null`）；`statusEnum` 可选，前端默认不传 |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/finance/priceadjustment/
  service/
    PriceAdjustmentAppService.java
  mapper/
    PriceAdjustmentMapper.java
    PriceAdjustmentMapperImpl.java
  request/
    InitPriceAdjustmentRequest.java
    PagePriceAdjustmentDocumentRequest.java
    GetPriceAdjustmentDocumentRequest.java
    SearchAdjustableItemRequest.java
    LoadLogisticsAdjustmentSnapshotRequest.java
    LoadClinicalOldPriceRequest.java
    PreviewSubmitLocksRequest.java
    CheckInTransitForSubmitRequest.java
    SavePriceAdjustmentDraftRequest.java
    SubmitPriceAdjustmentRequest.java
    WithdrawPriceAdjustmentDraftRequest.java
    ExistsPendingByItemRequest.java
    ApprovePriceAdjustmentRequest.java
    RejectPriceAdjustmentRequest.java
  response/
    InitPriceAdjustmentResponse.java
    PriceAdjustmentDocumentResponse.java
    ...
  dto/
    PriceAdjustmentLineDto.java
    PriceAdjustmentDetailDto.java
    AdjustableItemRowDto.java
    LockPreviewRowDto.java
    InTransitCheckDto.java

zxplat/health-application/src/main/java/com/openhis/health/application/financial/priceadjustment/support/
  PriceAdjustmentLogisticsSnapshotAssembler.java
  PriceAdjustmentInTransitChecker.java
  PriceAdjustmentLockPreviewLoader.java
  PriceAdjustmentApprovePricingOrchestration.java
  dto/
    LogisticsDetailSnapshotRowDto.java
    PriceAdjustmentInTransitCheckDto.java
    PriceAdjustmentLockPreviewRowDto.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/custom/priceadjustment/queries/
  PriceAdjustmentDocumentListQuery.java
```

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 用于 IF |
| --- | --- | --- | --- |
| 应用服务 | `PriceAdjustmentAppService` | 编排全部 IF | 全部 |
| 调价写 | `PriceAdjustmentDomainService` | 草稿/提交/审批/撤销 | 09～11、13、14 |
| 调价读 | `PriceAdjustmentQueryService` | 单据/列表/pending/互斥 | 02、03、12 |
| 库存写 | `InventoryItemDomainService` | lock / unlockByBusNo | 10、13、14 |
| 定价写 | `ChargeItemDefinitionDomainService` | 审批写价 | 13 |
| 定价读 | `ChargeItemDefinitionQueryService` | 调前价、写价前加载 | 05、06、13 |
| 库存读 | `InventoryItemQueryService` | 快照、锁状态 | 05、07 |
| 药品读 | `MedicationDefinitionQueryService` | 选品 | 04 |
| 耗材读 | `DeviceDefinitionQueryService` | 选品 | 04 |
| 活动读 | `ActivityDefinitionQueryService` | 选品 | 04 |
| 医疗服务读 | `HealthcareServiceQueryService`（以 DM 真源 FQCN 为准） | 选品 | 04 |
| 组织读 | — | （已移除）会话科室由 Resolver 解析，init 不再下发科室下拉 | — |
| 目录展示 | `ProductDefinitionBatchLoader` + `ProductDefinitionDisplaysupport | enrich | 03、04 |
| querysupport | `PriceAdjustmentLogisticsSnapshotAssembler` | 物流子表快照 | 05 |
| querysupport | `PriceAdjustmentInTransitChecker` | 在途预检 | 08、10 |
| querysupport | `PriceAdjustmentLockPreviewLoader` | 锁预览 | 07、10 |
| querysupport | `PriceAdjustmentApprovePricingOrchestration` | 审批停用旧价+新增 ACTIVE | 13 |
| Mapper | `PriceAdjustmentMapper` | Command/DTO | 写 IF |
| 操作人 | `CurrentPractitionerResolver` | operatorId | 09～11、13、14 |
| 操作科室 | `CurrentManagedOrganizationIdsResolver` | orgId（会话；02 仅 Gate，列表不收窄） | 02、04、09 |

### 2.3 领域方法白名单

| 聚合 | 方法 | 用于 IF |
| --- | --- | --- |
| PriceAdjustmentDomainService | `savePriceAdjustmentDraft` | IF-PRC-09 |
| PriceAdjustmentDomainService | `submitPriceAdjustmentForApproval` | IF-PRC-10 |
| PriceAdjustmentDomainService | `resubmitPriceAdjustmentForApproval` | IF-PRC-10（rejected） |
| PriceAdjustmentDomainService | `approvePriceAdjustment` | IF-PRC-13 |
| PriceAdjustmentDomainService | `rejectPriceAdjustment` | IF-PRC-14 |
| PriceAdjustmentDomainService | `withdrawPriceAdjustmentDraft` | IF-PRC-11 |
| PriceAdjustmentQueryService | `countDistinctBusNo` | IF-PRC-02 |
| PriceAdjustmentQueryService | `listDistinctBusNoPage` | IF-PRC-02 |
| PriceAdjustmentQueryService | `listLinesByBusNosWithFilter` | IF-PRC-02 |
| PriceAdjustmentQueryService | `listDocumentsByBusNos` | IF-PRC-02（应用层装配） |
| PriceAdjustmentQueryService | `getDocumentByBusNo` | IF-PRC-03 |
| PriceAdjustmentQueryService | `existsPendingByItem` / `existsPendingByAnyItem` | IF-PRC-12；签发门禁 |
| PriceAdjustmentQueryService | `sumImpactByBusNo` | IF-PRC-03（可选内嵌） |
| InventoryItemDomainService | `lockInventory` | IF-PRC-10 |
| InventoryItemDomainService | `unlockByBusNo` | IF-PRC-13、14 |
| ChargeItemDefinitionDomainService | `deactivateActivePriceLinesByCommand` | IF-PRC-13 |
| ChargeItemDefinitionDomainService | `addLotSalePrice` / `addLotPurchasePrice` / `addItemRetailPrice` / `addItemPurchasePrice` | IF-PRC-13 |

### 2.4 枚举、字典与值集一览

| 名称 | 存储/对外 code | DM 锚点 | 代码锚点 |
| --- | --- | --- | --- |
| 审批状态 | `draft`/`pending`/`approved`/`rejected`/`deprecated` | DM-CUS-011 §4.1 | `PriceAdjustmentStatus` |
| 项目表 | 四表名 + `adjustmentStyle` | DM-CUS-011 §4.2 | `TableNameConstants` + `PriceAdjustmentItemTablesupport |
| 价类 | `PURCHASE`/`SALE`/`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE` | DM-CUS-011 §4.3 | `PriceType` |
| 定价发布状态 | `ACTIVE` 启用 / `INACTIVE` 停用 | DM-ADM-010 §2.3 | `PublicationStatus` |
| 调价原因 | `adjustment_reason` 自由文案 | DM-CUS-011 §4.4 | 手写字符串；展示即存值 |
| 锁状态 | `LockStatus` | DM-WKF-008 §4.3 | `LockStatus` |

---

## 3. 按接口 ID 命名展开

### 3.1 IF-PRC-10 提交审批（核心写路径）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST submitForApproval` |
| **Request** | `busNo`（必填）；`submitterId` 服务端解析，**不入** Request |
| **Response** | `busNo`；`statusEnum=pending` |

**编排序列（物流 `adjustmentStyle=logistics`）**

| 序号 | 阶段 | 调用 | 失败则 |
| --- | --- | --- | --- |
| 1 | Pre | `PriceAdjustmentQueryService#getDocumentByBusNo` | 中止 |
| 2 | Gate | 锚点行 `statusEnum` ∈ {`draft`,`rejected`} | `INVALID_STATUS` |
| 3 | Gate | `PriceAdjustmentUnsignedDraftPolicySupport`：本单项目 **>48h** 未签发 DRAFT 静默 `CANCELLED`；仍有 **≤48h** → 阻断（提示医生/患者/项目名，不可确认绕过）；再 `PriceAdjustmentInTransitChecker#checkHardByBusNo` | `PRICE_ADJUSTMENT_005` |
| 4 | Gate | （可选）`PriceAdjustmentLockPreviewLoader#loadByBusNo` 仅 UI；服务端在 5 再校验 | — |
| 5 | Cmd | 对每个将锁库存行：`InventoryItemDomainService#lockInventory`（`busNo`=调价单号） | 整单失败回滚 |
| 6 | Cmd | `rejected` → `resubmitPriceAdjustmentForApproval`；否则 `submitPriceAdjustmentForApproval`（带 `lockedInventoryCount`） | 回滚 5 之锁 |
| 7 | Asm | 返回 Response | — |

**编排序列（诊疗 `clinical`）**：跳过 3～5；仅 1→2→6（`submit` 或 `resubmit`）。

### 3.2 IF-PRC-13 单据内审批同意

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST approve` |
| **Request** | `busNo`；`approveComment`（可选） |
| **Response** | `busNo`；`statusEnum=approved` |

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Pre | `getDocumentByBusNo` |
| 2 | Gate | `statusEnum=pending` |
| 3 | Cmd | **定价落库（停用 + 新增，BR-PRC-APR-003～005）** — 见下表 |
| 4 | Cmd | 物流：`InventoryItemDomainService#unlockByBusNo` |
| 5 | Cmd | `PriceAdjustmentDomainService#approvePriceAdjustment` |
| 6 | Asm | Response |

**步骤 3 定价落库（按调价项目行循环）**

| 口径 | 匹配键 | 停用（→ `INACTIVE`） | 新增（`ACTIVE`） |
| --- | --- | --- | --- |
| 物流·项目进价 | `instance + PURCHASE + lot_no=null` | 当前 ACTIVE `PURCHASE` 行 | `price_value=new_purchase_amount` |
| 物流·项目售价 | `instance + SALE + lot_no=null` | 当前 ACTIVE `SALE` 行 | `price_value=new_selling_amount` |
| 物流·批号进价 | `instance + LOT_NUMBER_COST + lot_no` | 该批 ACTIVE `LOT_NUMBER_COST` | `price_value=主行 new_purchase_amount` |
| 物流·批号售价 | `instance + LOT_NUMBER_PRICE + lot_no` | 该批 ACTIVE `LOT_NUMBER_PRICE` | `price_value=主行 new_selling_amount` |
| 诊疗·售价 | `instance + SALE + lot_no=null` | 当前 ACTIVE `SALE` 行 | `price_value=new_selling_amount` |

> 实现落点：`PriceAdjustmentApprovePricingOrchestration#applyApprovedPricing`（`application.financial.priceadjustment.support`）；先 `deactivateActivePriceLinesByCommand` → 再 `add*Price` 新增 ACTIVE 行。**禁止** `updatePriceLineByCommand` 直接改金额覆盖旧行。

### 3.3 IF-PRC-14 单据内审批驳回

| HTTP | `POST reject` |
| Request | `busNo`；`approveComment`（**必填**） |
| 序列 | Pre → Gate pending → 物流 `unlockByBusNo` → `rejectPriceAdjustment` |

### 3.4 IF-PRC-09 保存草稿

| HTTP | `POST saveDraft` |
| Request（摘要） | `busNo`（可空）；`adjustmentReason`；`adjustmentTime`；`lines[]`：`itemTable`、`itemId`、`newPurchaseAmount`、`newSellingAmount`、`oldSellingAmount`（诊疗）、`details[]`（物流：`lotNumber`、`quantity`、`oldPurchaseAmount`、`oldSellingAmount`）；**`orgId` 服务端解析，不入 Request** |
| Gate（物流） | 对本单 `lines` 物流项目：`>48h` 静默废弃未签发 DRAFT；`≤48h` 阻断并提示开立医生/患者/项目名（BR-PRC-SUB-003a） |
| Cmd | `savePriceAdjustmentDraft`（Mapper → Command；`orgId` 由 Resolver 注入） |

### 3.4a IF-PRC-08 提交前在途预检

| HTTP | `POST checkInTransitForSubmit` |
| Request | `busNo` |
| Response | `check.blocked`；硬在途原因；或 `reasonCode=FRESH_UNSIGNED_ORDER_DRAFT` + `freshUnsignedDraftTips[]`（医生/患者） |
| 读链 | 硬在途 `PriceAdjustmentInTransitChecker#checkByBusNo`；未阻断时再 `PriceAdjustmentUnsignedDraftPolicySupport#listFreshTipsByBusNo` |

### 3.5 IF-PRC-05 物流批号快照

| HTTP | `POST loadLogisticsSnapshot` |
| Request | `itemTable`；`itemIds[]` |
| Response | `rows[]`：与 `SavePriceAdjustmentDraftCommand.DetailLine` 同构 + 展示字段 |
| 读链 | `PriceAdjustmentLogisticsSnapshotAssembler#assemble` → InventoryItem QS + ChargeItemDefinition QS |

### 3.6 IF-PRC-02 列表（对齐进货单 IF-WHS-IN-02）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST pageDocuments` |
| **Request** | `statusEnum`（可选，前端默认不传=全状态）；`startDate`/`endDate` 或 `submitTimeFrom`/`submitTimeTo`（与进货单时刻窗映射一致）；`keyword`（单号）；分页；**`orgId` 不入 Request**；应用层会话 Gate 后 Query.`orgId=null`（不收窄科室） |
| **领域读链** | `countDistinctBusNo(query)` → `listDistinctBusNoPage(query)` → `listLinesByBusNosWithFilter(query, busNos)` → 应用层按 `bus_no` 聚合 + enrich |
| **时刻键** | 过滤/排序：`COALESCE(submit_time, adjustment_time)`；展示分列：制单 `adjustment_time`、申请 `submit_time`、审批 `approve_time` |
| **Query 类** | `PriceAdjustmentDocumentListQuery`（继承 `AbstractPageQuery`） |

### 3.7 其余 IF（摘要）

| IF | HTTP methodName | 读/写 | 主调用 |
| --- | --- | --- | --- |
| IF-PRC-01 | `init` | 读 | 枚举 + `TenantOptionLoader`（**不下发**科室下拉） |
| IF-PRC-02 | `pageDocuments` | 读 | `countDistinctBusNo` + `listDistinctBusNoPage` + `listLinesByBusNosWithFilter` |
| IF-PRC-03 | `getDocument` | 读 | `getDocumentByBusNo` + enrich（`itemCode`/`specification`/`manufacturer`/`categoryCodeDictText`/`unitName`）+ 可选 `sumImpactByBusNo`；子表 `details` 含批号影响 |
| IF-PRC-04 | `searchAdjustableItems` | 读 | `ProductDefinitionCatalogPageLoader` + `ChargeItemDefinitionQueryService` 目录当前价；医疗服务 Tab 按会话 `orgId` 过滤 |
| IF-PRC-06 | `loadClinicalOldPrice` | 读 | `ChargeItemDefinitionQueryService` · SALE |
| IF-PRC-07 | `previewSubmitLocks` | 读 | `PriceAdjustmentLockPreviewLoader` |
| IF-PRC-08 | `checkInTransitForSubmit` | 读 | `PriceAdjustmentInTransitChecker` + `PriceAdjustmentUnsignedDraftPolicySupport`（48h 提示含项目名） |
| IF-PRC-11 | `withdrawDraft` | 写 | `withdrawPriceAdjustmentDraft` |
| IF-PRC-12 | `existsPendingByItem` | 读 | `existsPendingByItem` |

### 3.8 IF-PRC-04 `searchAdjustableItems`（选品检索）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST searchAdjustableItems` |
| **Request** | `itemTable`（必填）；`keyword`（可选）；`pageNo`/`pageSize`；**`orgId` 服务端解析，不入 Request** |
| **Response** | `PagedResult<AdjustableItemRowDto>` |

**DTO：`AdjustableItemRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `itemTable` | String | 项目物理表 |
| `itemId` | Long | 项目定义主键 |
| `itemCode` | String | 院内编码（`productBusNo` / 医疗服务 `busNo`） |
| `itemName` | String | 项目名称 |
| `specification` | String | 规格（`totalVolume`） |
| `manufacturer` | String | 厂家/产地（物流） |
| `categoryCodeDictText` | String | 项目种类展示名 |
| `unitName` | String | 单位（物流=包装单位；诊疗=开立单位回落包装） |
| `quantity` | BigDecimal | 全院可用库存数量总和（**仅物流** `itemTable`；无可用行时为 `0`） |
| `currentPurchasePrice` | BigDecimal | 目录当前进价（ACTIVE `PURCHASE`，每包装；**仅物流**） |
| `currentSellingPrice` | BigDecimal | 目录当前售价（ACTIVE `SALE`） |

**编排序列**

| 序号 | 阶段 | 调用 |
| --- | --- | --- |
| 1 | Gate | `itemTable` 合法 + 分页窗口 |
| 2 | Pre | 药/耗/诊疗：`ProductDefinitionCatalogPageLoader#pageProductDefinition`；医疗服务：内存过滤 `offered_org_id` |
| 3 | Asm | `PriceAdjustmentMapper#toSearchAdjustableItemsResponse` |
| 4 | Asm | `enrichAdjustableItemCatalogCurrentPrices`：`resolveFirstPurchasePriceValue`（物流）+ `resolveFirstSalePriceValue`（**BR-PRICE-SRC-001** 新开单读路径） |
| 5 | Asm | `enrichAdjustableItemAvailableQuantities`（**仅物流**）：`InventoryItemQueryService#sumTotalAvailableInventoryByItemIds` |

> **分支判断**：物流/诊疗由请求 `itemTable` 与 `PriceAdjustmentItemTableSupport#isLogisticsItemTable` / `#isClinicalItemTable` 区分；**禁止**在选品出参下发 `adjustmentStyle`。

> **定价锚点**：选品列表价类与 **IF-WHS-IN-11** 进货选品一致读 **当前 ACTIVE 定价表**；**禁止**读 `ChargeItem` 历史价。无定价行时价字段 **null**。

> **IF-PRC-01** 租户选项「医嘱定价来源」：**非阻断**；缺省按批号价展示；待与门诊医嘱定价租户配置对齐后补 `TenantOption` 键名。

---

## 4. QueryService 与领域事件

| 读场景 | QueryService | 组装规则 |
| --- | --- | --- |
| 单据详情 | `getDocumentByBusNo` | 批量 IN 子表（领域内已完成）；App enrich 项目名称/编码/规格/厂家/**种类/单位**；子表批号明细原样出参 |
| 我的列表 | `countDistinctBusNo` / `listDistinctBusNoPage` | 按状态/时刻窗/关键字；按 `bus_no` 聚合 |
| 物流快照 | querysupport | 全院库存批号 × 定价读侧 |
| 锁预览 | querysupport | 库存行 + `InventoryLock` 冲突 |

事件发布在领域 `save/submit/approve/reject/withdraw` 之后；App **不**二次发布调价事件。

---

## 5. 写库顺序（核心写接口视角）

**IF-PRC-10（物流）**：`inventory_lock` INSERT → `cus_price_adjustment` UPDATE（pending）→ `cus_price_adjustment_detail` UPDATE（impact 固化）。

**IF-PRC-13**：`adm_charge_item_definition` 停用+新增 → 物流 `inventory_lock` 解锁 → `cus_price_adjustment` `approved`。

**IF-PRC-14**：物流 `inventory_lock` 解锁 → `cus_price_adjustment` `rejected`。

**IF-PRC-09**：软删旧主从 → INSERT/UPDATE 主行 draft → INSERT 子表。

---

## 6. 非功能性需求实现（承诺表）

| NFR | 落点 |
| --- | --- |
| 同事务 | IF-PRC-10/13/14 锁+领域+写价同一 `@AppService` 事务 |
| 幂等 | 重复 approve/reject 由领域状态 Gate 拒绝 |
| 批量 I/O | 快照/预览/ enrich 批量 IN；禁止循环仓储 |
| 审计 | 领域字段 + `InventoryLock` 操作人 |

---

## 7. 测试要点

| 类型 | 用例 |
| --- | --- |
| 集成 | 审批后旧价 INACTIVE、新价 ACTIVE；下游读价仅 ACTIVE |
| 集成 | 诊疗无锁提交审批 |
| 异常 | 锁冲突、在途阻断、互斥 pending |
| 契约 | HTTP 均为 POST；Request 字段与 §3.4 一致 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.13 | 2026-09-29 | **IF-PRC-02**：列表 Query.`orgId=null` 不收窄科室；前端默认不传 `statusEnum` |
| v0.12 | 2026-09-16 | **取消定时任务与 confirmAbandon**；IF-PRC-08/09/10：本单项目 48h 提示 / 超 48h 静默 CANCELLED；对齐 AS v0.12 |
| v0.11 | 2026-09-16 | （已由 v0.12 取代）硬/软在途分流；`softBlocked` + `confirmAbandonUnsignedDrafts`；7 天定时 CANCELLED |
| v0.8 | 2026-07-26 | **IF-PRC-03**：`PriceAdjustmentLineDto` enrich 补 `categoryCodeDictText`/`unitName`；前端主行展开看批号子表 |
| v0.7 | 2026-06-27 | **IF-PRC-04**：选品返回物流 `quantity`；移除 `adjustmentStyle` 出参；§3.8 编排补可用量 enrich |
| v0.6 | 2026-06-27 | **IF-PRC-04**：选品 enrich 编码/规格/厂家/种类/单位 + 目录当前价（PURCHASE/SALE）；**IF-PRC-03** 行 enrich 补 `itemCode`/`specification`/`manufacturer`；§3.8 选品 DTO 与编排 |
| v0.5 | 2026-06-27 | 操作科室改会话解析；Request 移除 orgId；init 移除 organizationOptions；注入 CurrentManagedOrganizationIdsResolver |
| v0.4.1 | 2026-06-27 | 方法名与代码对齐（`checkByBusNo`/`loadByBusNo`/`applyApprovedPricing`）；§3.5～3.7 章节顺序修正 |
| v0.4 | 2026-06-27 | Gap 关闭；列表对齐进货单；querysupport 四类落地 |
| v0.3 | 2026-06-27 | 单页单据内审批；取消审批工作台；IF 收敛为 01～14 |
| v0.2 | 2026-06-27 | 审批落价停用+新增；菜单财务管理 |
