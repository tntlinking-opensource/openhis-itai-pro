# 药房调出单 · 应用服务设计（AS）

> **页面边界**：药房业务线 — **`PHARMACY_TRANSFER_OUT`** 主动制单。源/目的均须为 **`PHARMACY`** 库位（平级调拨，不含科室药房），禁止跨药库/科室药房形态。
>
> **同意编排**：调拨发放 → 源减/目增 → 追溯 → 生成对侧 `PHARMACY_TRANSFER_IN` 调入壳单（行带 **`approverId`/`approvalTime`**）。
>
> **与请领分工**：调拨**不消费**请领单（**AS-INV-PHA-002** / **AS-INV-WHS-003**）。
>
> **编码**：AS-INV-PHA-008 · **版本**：v0.2 · **日期**：2026-07-26

---

## 1. 业务规则

| 规则 ID | 描述 |
|---------|------|
| **BR-PHA-TO-001** | 源/目的均为药房（`PHARMACY`）平级调拨；禁止科室药房/药库 |
| **BR-PHA-TO-002** | 禁止关联请领 `original_bus_no` |
| **BR-PHA-TO-003** | agree 为唯一落账点 + 生成调入壳单（含审批人/审批时刻） |
| **BR-PHA-TO-004** | 草稿同号修订走领域 `createTransfer` + **`assertPharmacyTransferOutLinesRevisable`**（可修订态：待审核/驳回/已撤回） |

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |
