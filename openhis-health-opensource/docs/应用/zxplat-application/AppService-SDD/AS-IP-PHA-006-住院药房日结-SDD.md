# 住院药房日结 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-IP-PHA-006-住院药房日结.md`（**v1.18**）  
> **编码**：AS-IP-PHA-006-SDD · **版本**：v1.12 · **日期**：2026-10-14  
> **边界真源**：`DD-交账日结改造-收款与药房审批流.md`（**v1.5.10**）+ **DM-FIN-003 v3.0** + **DD-报表中心-进售价 v1.3**  
> **说明**：本 SDD **不维护 Gap 表**；与 **AS-OP-PHA-006-SDD** 同构，`type=IP_PHARMACY`。

---

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| **模块** | 发药管理 · **住院药房日结**（`inpatient/pharmacy/dailysettlement`） |
| **菜单** | **发药管理** → **住院药房日结**（Flyway **V15**） |
| **HTTP** | `POST /api/app/inpatientPharmacyDailySettlement/{methodName}` |
| **type** | `IP_PHARMACY` |
| **范围键 / 时间 / 漏交 / 状态 / 发药人 / 管辖药房** | 同 **AS-OP-PHA-006-SDD** §1；门/住场景用 encounter/dispense_enum 收窄；**BR-IP-PDC-009** |
| **品规销售排名** | 同门诊 **BR-IP-PDC-010** / **BR-OP-PDC-010** |
| **读价** | 同门诊：`PharmacyDailySettlementPricingSupport`（**售价**=账单 `ChargeItem`，缺则批号 `LOT_NUMBER_PRICE` 同批号回放；**进价**=批号 `LOT_NUMBER_COST` 按 `unitCode` 分支 + 同批号回放；禁串批/禁目录 SALE·PURCHASE/禁不判单位直乘） |

---

## 2. 接口白名单

| IF | 应用服务方法 | 中文 | HTTP |
| --- | --- | --- | --- |
| IF-IP-PDC-01 | `loadInpatientPharmacyDailySettlementPageInit` | 页初始化（管辖药房下拉 + 重点药类型下拉） | `POST .../loadInpatientPharmacyDailySettlementPageInit` |
| IF-IP-PDC-02 | `previewInpatientPharmacyDailySettlement` | 查询（大类/品规含四旗） | `POST .../previewInpatientPharmacyDailySettlement` |
| IF-IP-PDC-03 | `submitInpatientPharmacyDailySettlement` | 确认交账 | `POST .../submitInpatientPharmacyDailySettlement` |
| IF-IP-PDC-04 | `withdrawInpatientPharmacyDailySettlement` | 撤回待审 | `POST .../withdrawInpatientPharmacyDailySettlement` |
| IF-IP-PDC-05 | `pageInpatientPharmacyDailySettlementHistory` | 交账历史（仅管辖药房） | `POST .../pageInpatientPharmacyDailySettlementHistory` |

---

## 3. 领域依赖（摘要）

同 **AS-OP-PHA-006-SDD** §3（共用 `PharmacyDailySettlementPreviewAssembler` / `PharmacyDailySettlementPricingSupport` / `CurrentManagedLocationIdsResolver` 等 shared 组件）。

---

## 4. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.12 | 2026-10-14 | 同构 AS-OP-PHA-006-SDD v1.13：发药人岗池 `excludePlatformAdmin=false` / **B.6.3.1** |
| v1.0 | 2026-07-27 | 首版 stub：对齐 AS-IP-PHA-006 落地态 |
| v1.1 | 2026-07-27 | 对齐 AS v1.3：`pendingCount` |
| v1.2 | 2026-07-27 | 对齐 AS v1.4 / DD v1.4.7：同构门诊药房日结读价与发药人岗池 |
| v1.3 | 2026-07-28 | 对齐 AS v1.6 / DD v1.5.2：同构品规单价分桶与大类无净数量 |
| v1.4 | 2026-07-29 | 对齐 AS v1.7 / DD v1.5.7：同构仅批号价读价 |
| v1.5 | 2026-08-02 | 对齐 AS v1.8：管辖药房 Gate；IF-IP-PDC-05 历史 |
| v1.6 | 2026-08-02 | 对齐 AS v1.9：`total_income` = 售价−进价日结利润 |
| v1.7 | 2026-08-06 | 对齐 AS v1.10 / DD v1.5.8：售价=账单；进价仅同批号回放 |
| v1.8 | 2026-08-12 | 对齐 AS v1.11：进价按 unitCode 分支 |
| v1.9 | 2026-08-12 | 对齐 AS v1.12：售价账单缺失回落 LOT_NUMBER_PRICE |
| v1.10 | 2026-08-17 | 对齐 AS v1.14：`total_income` = 售价合计（非日结利润） |
| v1.11 | 2026-09-18 | 对齐 AS v1.17：品规销售排名（金额/数量互斥 + 重点药筛选） |
