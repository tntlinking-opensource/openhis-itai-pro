# 退库入库单 · 应用服务设计（AS）

> **文档职责**：说明「退库入库单（只读台账侧）」页面的**接口契约（IF）**、业务语义、规则（BR）、编排意图。
>
> **页面边界**：药库业务线 — **`CABINET_RETURN_STOCK_IN`**（type 5）壳单的**只读台账**：列表、详情、打印。**不支持**手工新建/改单/提交/同意/驳回/删除/自由选品/关联上游拉明细（**WHS_RSI_006**）。壳单由药房/科室药房退库单 agree 后系统自动生成（`original_bus_no` 指向退库单，初态 **`AGREE`**）；库存变动**已在退库 agree 时完成**。
>
> **与退库分工**：**AS-INV-PHA-003** / **AS-INV-DEP-003** 负责主动退库与 agree 落账 + 生成本壳单；本 AS 仅消费已生成壳单。
>
> **前端**：`views/inventory/warehouse/return-stock-in` 为只读展示 + 打印；**不**再暴露写 API 包装。写 IF 仍保留网关拒写契约（对齐调入壳 AS-INV-WHS-008）。
>
> **编码**：AS-INV-WHS-006 · **版本**：v0.3 · **日期**：2026-09-03

---

## 1. 场景概述

| 维度 | 说明 |
|------|------|
| **在内** | init、分页列表、详情、打印；写/选品/关联上游 IF 统一拒绝 |
| **在外** | 药房/科室退库制单与 agree |
| **涉及域** | 读侧 **SupplyRequest**、**Location**、产品读模型（unitBands 展示） |

## 2. 业务规则

| 规则 ID | 描述 |
|---------|------|
| **BR-WHS-RSI-001** | 写接口一律拒绝（须由退库 agree 生成壳单）→ **WHS_RSI_006** |
| **BR-WHS-RSI-002** | 列表/详情仅展示 `CABINET_RETURN_STOCK_IN` |
| **BR-WHS-RSI-003** | 壳单生成须拷贝下游退库行 **`reason`**，并写入 **`approverId`/`approvalTime`**（与退库同意人对齐；bootstrap AGREE **不**走 `approvalToAgree`） |
| **BR-WHS-RSI-004** | **IF-WHS-RSI-03** 详情须富化 **`productCatalog`/`unitCodeDictText`**（计量带回显）；行价取落库字段，**禁止**当前目录价覆盖（**BR-PRICE-SRC-001**） |
| **BR-WHS-RSI-005** | 历史壳单缺 **`approverId`/`reason`** 时，列表/详情可按 **`originalBusNo`** 只读回填展示（**不写库**） |
| **BR-WHS-RSI-006** | **IF-WHS-RSI-02** 列表须传 **`purposeLocationId`**（`0`=可见全部药库）；**禁止**再用 `sourceLocationId` 回落 |

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口；无独立 SDD（只读+拒写） | — |

## 4. 接口契约（摘要）

| IF | 能力 | 说明 |
|----|------|------|
| **IF-WHS-RSI-01** | `loadReturnStockInFormInit` | 药库/源侧位置下拉 |
| **IF-WHS-RSI-02** | `pageReturnStockIns` | 列表（`bus_no` 聚合）；侧栏金额 **`totalAmount`**；`creatorName`/`approverName` |
| **IF-WHS-RSI-03** | `getReturnStockInDetail` | 详情只读富化 |
| **IF-WHS-RSI-04～10** | create/update/submit/withdraw/agree/reject/remove | **立即** `WHS_RSI_006`（网关契约保留） |
| **IF-WHS-RSI-11** | `pageProductsForReturnStockIn` | **立即** `WHS_RSI_006` |
| **IF-WHS-RSI-12** | `pageLinkableDeliveries` | **立即** `WHS_RSI_006` |

## 5. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-03 | **v0.3**：补全 IF-01～12；列表强制 `purposeLocationId`；FE 去写面；Mapper 删死写装配；对齐只读壳 |
| 2026-07-26 | **v0.2**：壳单回显口径 **BR-WHS-RSI-003～005**；列表/详情 IF 摘要；对齐 **DM-WKF-006 v1.23** |
| 2026-07-01 | **v0.1**：只读台账初稿 |
