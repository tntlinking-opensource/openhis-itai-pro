# 科室退库单 · 应用服务设计（AS）

> **页面边界**：科室药房业务线 — **`DEPT_RETURN`** 主动制单。明细**可选**从已同意**科室入库单**（`original_bus_no` → `DEPT_STOCK_IN`）拉取可退余量，**也可**按源科室药房库存**自由选品**（对偶 **AS-INV-PHA-003**）。
>
> **同意编排**：退库发放 → 源科室药房扣减 / 目的药库增加 → 追溯 → 自动生成 **`CABINET_RETURN_STOCK_IN`** 壳单（**AS-INV-WHS-006**）。
>
> **库位门禁**：源侧 **`LocationForm.DEPARTMENT_PHARMACY`**；目的侧 **`LocationForm.WAREHOUSE`**。后端包 **`inventory/department/returntowarehouse`**（`InventoryDepartmentReturnToWarehouseAppService`）；错误码 **`DEPT_RTW_*`**。
>
> **与科室退领分工**：**`DEPT_ISSUE_RETURN`（AS-INV-DEP-009）** 为领用未用完退回科室药房，强制拉 **`DEPT_ISSUE`**、禁自由选品；本单为科室药房→药库双端点退库。
>
> **编码**：AS-INV-DEP-003 · **版本**：v0.1 · **日期**：2026-08-11

---

## 1. 业务规则

| 规则 ID | 描述 |
|---------|------|
| **BR-DEP-RTW-001** | `originalBusNo` **可选**：非空时须指向已同意 `DEPT_STOCK_IN`，且明细 ⊆ 入库行、数量 ≤ 可退余量；**空**表示自由选品，仅受源科室药房可用库存门禁 |
| **BR-DEP-RTW-002** | 有关联入库单时：明细 ⊆ 入库行，数量 ≤ 可退余量 |
| **BR-DEP-RTW-003** | agree 后生成退库入库壳单，不在本页二次落账 |
| **BR-DEP-RTW-004** | **IF-DEP-RTW-11** `pageProductsForReturnToWarehouse`：源科室药房库存批号级选品；可选 `positiveQuantityOnly` |
| **BR-DEP-RTW-005** | 经手人 `practitionerId` 建单/修订必填（与制单人分离） |

## 2. 接口要点

| IF | 说明 |
|----|------|
| **IF-DEP-RTW-04** 新建 | `originalBusNo` 可空；空=自由选品 |
| **IF-DEP-RTW-05** 修订 | 同号软删重插；领域 **`assertDeptReturnLinesRevisable`**；保留存量 `originalBusNo` 快照 |
| **IF-DEP-RTW-11** 选品 | 源科室药房库存批号行 |
| **IF-DEP-RTW-12/13** | 可关联科室入库单列表 / 预填 |
| **IF-DEP-RTW-02** 列表 | 按 `bus_no` 聚合；侧栏整单金额仅 **`totalAmount`**（**无**列表行 `totalPrice` 双写） |

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.2 | 2026-09-03 | 列表聚合金额口径：仅 **`totalAmount`** |
| v0.1 | 2026-08-11 | 初稿：可选拉源 + 自由选品；与 **AS-INV-PHA-003 v0.2** 对称 |
