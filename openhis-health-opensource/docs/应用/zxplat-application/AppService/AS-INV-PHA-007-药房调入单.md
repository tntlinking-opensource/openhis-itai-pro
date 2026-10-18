# 药房调入单 · 应用服务设计（AS）

> **页面边界**：药房业务线 — **`PHARMACY_TRANSFER_IN`** 调入壳单**只读台账**。**禁止**手工新建/审批落账；库存仅在调出 agree 时变动一次。
>
> **生成来源**：同形态 **`TRANSFER_OUT`** agree 后生成：药库调出→`CABINET_TRANSFER_IN`、药房调出→`PHARMACY_TRANSFER_IN`、科室调出→`DEPT_TRANSFER_IN`（**禁止跨形态**）。
>
> **编码**：AS-INV-PHA-007 · **版本**：v0.2 · **日期**：2026-07-26

---

## 1. 业务规则

| 规则 ID | 描述 |
|---------|------|
| **BR-PHA-TI-001** | 写接口一律拒绝 |
| **BR-PHA-TI-002** | 列表/详情仅 `PHARMACY_TRANSFER_IN` |
| **BR-PHA-TI-003** | 列表侧栏展示制单人/审批人；历史壳缺 `approverId` 时按 `originalBusNo` 只读回填（不写库） |

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |
