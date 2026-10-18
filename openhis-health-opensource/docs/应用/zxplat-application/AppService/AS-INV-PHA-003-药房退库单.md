# 药房退库单 · 应用服务设计（AS）

> **页面边界**：药房业务线 — **`PHARMACY_RETURN_TO_WAREHOUSE`** 主动制单。明细**可选**从已同意**药房入库单**（`original_bus_no`）拉取可退余量，**也可**按源药房库存**自由选品**（对偶订货/进货可选拉源；**不对偶**药库退货 AS-INV-WHS-005，退货仍强制拉进货）。
>
> **同意编排**：退库发放 → 源药房扣减 / 目的药库增加 → 追溯 → 自动生成 **`CABINET_RETURN_STOCK_IN`** 壳单（**AS-INV-WHS-006**）。
>
> **科室药房**：业务口径对称（`DEPT_RETURN`），见 **AS-INV-DEP-003**；后端包 **`inventory/department/returntowarehouse`**；位置形态 **`LocationForm.DEPARTMENT_PHARMACY`**；错误码 **`DEPT_RTW_*`**。
>
> **编码**：AS-INV-PHA-003 · **版本**：v0.2 · **日期**：2026-08-11

---

## 1. 业务规则

| 规则 ID | 描述 |
|---------|------|
| **BR-PHA-RTW-001** | `originalBusNo` **可选**：非空时须指向已同意 `PHARMACY_STOCK_IN`，且明细 ⊆ 入库行、数量 ≤ 可退余量；**空**表示自由选品，仅受源药房可用库存门禁 |
| **BR-PHA-RTW-002** | 有关联入库单时：明细 ⊆ 入库行，数量 ≤ 可退余量（`ReturnToWarehouseStockInLineHelper`） |
| **BR-PHA-RTW-003** | agree 后生成退库入库壳单，不在本页二次落账 |
| **BR-PHA-RTW-004** | **IF-PHA-RTW-11** `pageProductsForReturnToWarehouse`：源药房库存批号级选品；可选 `positiveQuantityOnly` |

## 2. 接口要点

| IF | 说明 |
|----|------|
| **IF-PHA-RTW-04** 新建 | `originalBusNo` 可空；空=自由选品 |
| **IF-PHA-RTW-11** 选品 | 源药房库存批号行；与出库选品同构 |
| **IF-PHA-RTW-12/13** | 可关联入库单列表 / 预填；**仅拉源路径使用** |
| **IF-PHA-RTW-02** 列表 | 按 `bus_no` 聚合；侧栏整单金额仅 **`totalAmount`**（明细行 `total_price` 之和；**无**列表行 `totalPrice` 双写） |

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.3 | 2026-09-03 | 列表聚合金额口径：仅 **`totalAmount`** |
| v0.2 | 2026-08-11 | **可选拉源 + 自由选品**：`originalBusNo` 可空；恢复 IF-11；领域 `createReturn` 主动退库允空关联单号 |
| v0.1 | 2026-07-01 | 初稿（当时口径为强制拉入库、禁自由选品） |
