# 门诊药房日结 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-OP-PHA-006-门诊药房日结.md`（**v1.18**）  
> **编码**：AS-OP-PHA-006-SDD · **版本**：v1.13 · **日期**：2026-10-14  
> **边界真源**：`DD-交账日结改造-收款与药房审批流.md`（**v1.5.10**）+ **DM-FIN-003 v3.0** + **DM-MED-002**；发药人岗池见 **B.6.3.1**（日结 `excludePlatformAdmin=false`）  
> **说明**：本 SDD **不维护 Gap 表**；编排细节以 AS 正文为准。

---

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| **模块** | 发药管理 · **门诊药房日结**（`outpatient/pharmacy/dailysettlement`） |
| **菜单** | **发药管理** → **门诊药房日结**（Flyway **V15**） |
| **HTTP** | `POST /api/app/outpatientPharmacyDailySettlement/{methodName}` |
| **type** | `OP_PHARMACY` |
| **范围键** | `location_id` 必填（单药房）；`org_id` 可空 |
| **管辖药房** | `CurrentManagedLocationIdsResolver`：下拉 = `listAllActivePharmacies`（或 org 收窄）∩ `narrowActiveLocationsByManaged`；查询/提交/撤回 `assertLocationInManagedGate`；历史 `pageHistory(..., gatedPharmacyIds, …)` |
| **时间轴** | `dispense_time`（非空；空则报错） |
| **发药人** | 页初始化 `practitionerOptions` = 平台角色 **`ROLE_PHARMACIST`**（`excludePlatformAdmin=false`，保留挂药师岗的 admin；业务选型下拉默认剔 admin 见 **B.6.3.1**）；预览入参 `practitionerIds` 软筛；漏交不按人 |
| **品规销售排名** | 预览 `productRows` 回传药典四旗；页初始化 `keyDrugTypeOptions`；前端表头对净数量/售价总额升降序 + 重点药类型筛选（**BR-OP-PDC-010**）；不改大类/`pendingCount`/`total_income` |
| **状态流** | 提交 → **`SUBMITTED`** + 挂载 dispense；撤回 → **`WITHDRAWN`** + 清挂载；审批在 **AS-FIN-CS-001** |
| **漏交** | 硬栏 **BR-CS-020** / 软提醒 **BR-CS-021**；不灰确认入口 |
| **读价** | `PharmacyDailySettlementPricingSupport`：**售价** = 发放锚点 `ChargeItem` 行额（有账单不回放）；**账单缺失**时回落 `HistoricalChargeItemPurchasePriceSupport#resolveLotSaleUnitPrice`（`LOT_NUMBER_PRICE`，同批号调价回放）+ `ChargeItemPackageUnitRatioSupport` 按 `unitCode` 分支；**禁止**目录 `SALE`。**进价** = `resolveLotPurchaseUnitPrice`（`LOT_NUMBER_COST`）+ `computePurchaseLineAmount`（包装→包装价×数量；拆零→满包+零头；`beginLotPriceSession`；同批号才调价回放；禁止串批；无调价不查 history；**禁止** `PURCHASE`；**禁止**不判单位 `lineTotal`）；品规键=`medicationId`+进/售单价后进价 `sumThenPurchaseSettle`（3 位）、售价 `sumThenSettle`（2 位）；大类=品规加总；有发药事实的品规行一律展示（含净 0） |

---

## 2. 接口白名单

| IF | 应用服务方法 | 中文 | HTTP |
| --- | --- | --- | --- |
| IF-OP-PDC-01 | `loadOutpatientPharmacyDailySettlementPageInit` | 页初始化（管辖药房下拉 + 重点药类型下拉） | `POST .../loadOutpatientPharmacyDailySettlementPageInit` |
| IF-OP-PDC-02 | `previewOutpatientPharmacyDailySettlement` | 查询（大类/品规含四旗/待交/漏交） | `POST .../previewOutpatientPharmacyDailySettlement` |
| IF-OP-PDC-03 | `submitOutpatientPharmacyDailySettlement` | 确认交账 | `POST .../submitOutpatientPharmacyDailySettlement` |
| IF-OP-PDC-04 | `withdrawOutpatientPharmacyDailySettlement` | 撤回待审 | `POST .../withdrawOutpatientPharmacyDailySettlement` |
| IF-OP-PDC-05 | `pageOutpatientPharmacyDailySettlementHistory` | 交账历史（仅管辖药房） | `POST .../pageOutpatientPharmacyDailySettlementHistory` |

> 主区金额含已挂载；待交笔数 `pendingCount` / 弹窗候选仅 `cashier_settlement_id IS NULL`。  
> 弹窗 items enrich：状态、类型、药品名、规格、厂家、数量、单位、进价、售价/小计（与主区同读价）。  
> 头表 `total_income`：**售价合计** = 各大类售价总额之和（与主区同口径；预览复用进/售快照，提交按勾选行现算；不读 payment；**不为**售价−进价利润）。  
> **BR-OP-PDC-009**：药房下拉与 IF-02/03/04/05 共用管辖白名单；伪造他库 `locationId` → EX-OP-PDC-102。

---

## 3. 领域依赖（摘要）

| 依赖 | 用途 |
| --- | --- |
| `MedicationDispenseQueryService` | 药房交账候选 / 预览 / 漏交 / 窗内发药人 |
| `CashierSettlementDomainService` | submit / withdraw |
| `CashierSettlementQueryService#pageHistory` | 历史分页；可选 `locationIds` IN |
| `PharmacyDailySettlementPreviewAssembler` | 大类四行 + 品规净额（含四旗）+ 弹窗 items + `keyDrugTypeOptions` |
| `PharmacyDailySettlementPricingSupport` | 批号进价按 unitCode 分支；账单售价（缺则批号售价回落）；头表金额=`sumSaleAmount`（售价合计）；`attachProductDisplay` 批量回填四旗 |
| `MedicationDefinitionQueryService#mapKeyDrugFlagsByIds` | 窗内药典主键 → 重点药四旗（B.6.4；禁全库扫） |
| `HistoricalChargeItemPurchasePriceSupport` | 批号进/售 + 同批号调价回放（无调价不查历史；禁串批；禁目录 SALE/PURCHASE） |
| `ChargeItemPackageUnitRatioSupport` | 包装/拆零操作单位行进/售价（与发药报表同口径） |
| `CurrentManagedLocationIdsResolver` | 岗位管辖药房下拉 / Gate / 历史 IN |
| `PractitionerRoleScopeLoader` | `ROLE_PHARMACIST` 发药人岗池 |

---

## 4. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-07-27 | 首版 stub：对齐 AS-OP-PHA-006 落地态；SUBMITTED 流；菜单 V15 |
| v1.1 | 2026-07-27 | 对齐 AS v1.3：`pendingCount` |
| v1.2 | 2026-07-27 | 对齐 AS v1.4 / DD v1.4.7：药师岗多选软筛；价格定义表读价；弹窗 enrich |
| v1.3 | 2026-07-28 | 对齐 AS v1.6 / DD v1.5.2：品规单价分桶；大类无净数量列；净 0 仍展示 |
| v1.4 | 2026-07-29 | 对齐 AS v1.7 / DD v1.5.7：进/售仅批号价，禁止参考价回落 |
| v1.5 | 2026-08-02 | 对齐 AS v1.8：管辖药房 Gate；IF-OP-PDC-05 历史；`CurrentManagedLocationIdsResolver` |
| v1.6 | 2026-08-02 | 对齐 AS v1.9：`total_income` = 售价−进价；去掉 payment 净收入依赖 |
| v1.7 | 2026-08-06 | 对齐 AS v1.10：售价=账单 ChargeItem；进价仅 LOT_NUMBER_COST 同批号回放；禁串批/禁售价回放 |
| v1.8 | 2026-08-12 | 对齐 AS v1.11：进价按 unitCode 分支（ChargeItemPackageUnitRatioSupport） |
| v1.9 | 2026-08-12 | 对齐 AS v1.12：售价账单缺失回落 LOT_NUMBER_PRICE（同批号调价回放） |
| v1.10 | 2026-08-17 | 对齐 AS v1.14：`total_income` = 售价合计（非日结利润） |
| v1.11 | 2026-09-18 | 对齐 AS v1.17：品规销售排名（金额/数量互斥 + 重点药四旗筛选） |
| v1.13 | 2026-10-14 | 发药人岗池显式 `excludePlatformAdmin=false`；对齐 **B.6.3.1** / 成码；代码先行回写 |
| v1.12 | 2026-10-08 | 对齐 AS v1.18：进价一律 `sumThenPurchaseSettle`（3 位），售价 `sumThenSettle`（2 位） |
