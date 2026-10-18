# 药库调出单 · 应用服务设计（AS）

> **文档职责**：说明「药库调出单（主动执行侧）」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图。
>
> **页面边界**：药库业务线 — **`CABINET_TRANSFER_OUT`** 主动制单。**平级调拨**：源/目的均须为药库（`WAREHOUSE`），禁止跨药房/科室药房形态。同意后编排发放、双向库存、追溯，并生成对侧 `CABINET_TRANSFER_IN` 调入壳单。
>
> **与调入分工**：**AS-INV-WHS-008** 只读展示 `CABINET_TRANSFER_IN` 壳单；本 AS 在 agree 相位生成对侧单据（目的为药房时类型为 **`PHARMACY_TRANSFER_IN`**，不出现在 W-08 列表）。
>
> **编码**：AS-INV-WHS-009 · **版本**：v0.2 · **日期**：2026-07-26

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 维护 **`CABINET_TRANSFER_OUT`** 草稿与审批链；**同意**后发放 + 双向库存 + 追溯 + 对侧调入壳单 |
| **在外** | 调入只读台账（**AS-INV-WHS-008**）；药房/科室调出包 |
| **涉及域** | **SupplyRequest**、**SupplyDelivery**、**InventoryItem**、**TraceNumberRecord**；**Location**、**ProductDefinition** |

### 1.2 术语

| 术语 | 口径 |
|------|------|
| **调出执行单** | `type_enum`=`CABINET_TRANSFER_OUT`；**唯一库存落账点**在 agree |
| **调入壳单** | agree 后 `createTransfer`；平级药库调拨对侧恒为 `CABINET_TRANSFER_IN`；壳单行写入 **`approverId`/`approvalTime`**（与本单调出同意人对齐） |
| **修订** | 同号 `bus_no` 软删重插；须满足 `assertCabinetTransferOutLinesRevisable` |

### 1.3 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
|---------|------|---------|
| **BR-WHS-TO-001** | 源/目的均须为岗位可见药库（平级调拨，源≠目的）；禁止跨药房/科室药房 | IF-WHS-TO-04 / 05 |
| **BR-WHS-TO-002** | 草稿明细可用量 ≤ 来源药库可用库存（两阶段 A/B） | IF-WHS-TO-04 / 05 / 11 |
| **BR-WHS-TO-003** | **同意**后：发放 + 源减/目增 + 生成调入壳单（含审批人/审批时刻）；禁止重复 agree/重复发放 | IF-WHS-TO-08 |
| **BR-WHS-TO-004** | **禁止**手写 `original_bus_no` 关联药房请领；请领下游执行见 **AS-INV-WHS-003** 药库出库（`WHS_TR_002`） | IF-WHS-TO-04 / 05 |

---

## 2. 页面与接口总览

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 |
|---------|---------|---------|-------|---------|
| IF-WHS-TO-01 | 进入页面 | 初始化药库/药房、枚举、人员 | 读 | — |
| IF-WHS-TO-02 | 列表 | 分页调出单 | 读 | — |
| IF-WHS-TO-03 | 详情 | 按 `busNo` 详情 | 读 | — |
| IF-WHS-TO-04 | 新建 | 创建调出草稿 | 写 | BR-WHS-TO-001～002 / **004** |
| IF-WHS-TO-05 | 编辑 | 可编辑态改明细 | 写 | BR-WHS-TO-001～002 / **004** |
| IF-WHS-TO-06 | 提交审批 | 待审 → 审中 | 写 | — |
| IF-WHS-TO-07 | 撤回 | 审中撤回 | 写 | — |
| IF-WHS-TO-08 | 同意 | agree + 落账 + 调入壳单 | 写 | BR-WHS-TO-003 |
| IF-WHS-TO-09 | 驳回 | 驳回 | 写 | — |
| IF-WHS-TO-10 | 删除 | 逻辑删除 | 写 | — |
| IF-WHS-TO-11 | 选品 | 来源药库批号级选品；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**） | 读 | BR-WHS-TO-002 |

---

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |

---

## 4. 接口编排说明书（摘要）

### IF-WHS-TO-08 同意

1. Gate：`busNo`、未重复 agree、未完成发放。
2. Pre：回读 AGREE 行快照；防重复发放。
3. Cmd：`agreeRequest` → `flush` → `createTransferDelivery`（源减/目增）→ 追溯 → `createTransfer`（壳单，`bootstrapFinalStatusCode=AGREE`）。
4. Asm：返回 `generatedTransferInBusNo`（对侧调入单号）。

### IF-WHS-TO-04 / 05 建单/改单

1. Gate：位置门控、明细非空、追溯码宽表；**`original_bus_no` 非空则拒绝**（BR-WHS-TO-004）。
2. Pre：来源可用量两阶段校验。
3. Cmd：`createTransfer`（改单同号修订路径）。

---

## 5. 业务异常与用户提示

| 错误码 | 场景 |
|--------|------|
| **WHS_TR_001** | 参数/门控/可用量不足 |
| **WHS_TR_002** | 请求携带 `original_bus_no`（不支持关联请领） |
| **WHS_TR_103** | 单据不存在或类型非调出 |
| **WHS_TR_104** | 重复 agree/发放失败/编排失败 |
| **WHS_TR_105** | 乐观锁冲突 |

---

## 6. 关联文档

- **DM-WKF-006** §6.1.8 / §6.1.14 表 B（v1.7）
- **AS-INV-WHS-003** 药库出库单（药房请领**唯一**下游执行入口）
- **AS-INV-WHS-008** 药库调入单（只读）
