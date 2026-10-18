# 药库调入单 · 应用服务设计（AS）

> **文档职责**：说明「药库调入单（只读台账侧）」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图。
>
> **页面边界**：药库业务线 — **`CABINET_TRANSFER_IN`** 调入执行单的**只读台账**：列表、详情、选品（兼容壳单展示）。**不支持**手工新建/改单/提交/同意/驳回/删除（**WHS_TI_002**）。调入执行单由药库调出 agree 后系统自动生成（`original_bus_no` 指向调出单，初态 **`AGREE`**）；库存变动**仅在调出 agree 时完成一次**（DM §6.1.8）。
>
> **与调出分工**：**AS-INV-WHS-009** 负责药库平级调出 agree 落账 + 生成 `CABINET_TRANSFER_IN` 壳单；本 AS 仅消费已生成壳单，**禁止二次 agree 落账**。
>
> **编码**：AS-INV-WHS-008 · **版本**：v0.2 · **日期**：2026-07-26

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 页初始化、按目的药库分页列表、按 `busNo` 详情、选品（只读辅助）；写接口统一 **WHS_TI_002** |
| **在外** | 调出制单与 agree（**AS-INV-WHS-009**）；药房/科室调入包 |
| **涉及域** | 读侧：**SupplyRequest**、**InventoryItem**（选品）、**ProductDefinition**；**Location**、**人员** |

### 1.2 术语

| 术语 | 口径 |
|------|------|
| **调入壳单** | `type_enum`=`CABINET_TRANSFER_IN`；`original_bus_no` **必填**（指向已 **AGREE** 的 `CABINET_TRANSFER_OUT`）；初态 **AGREE**；行须带 **`approver_id`/`approval_time`**（与调出同意人对齐） |
| **主动调入** | **不支持**；需求应走 `TRANSFER_IN_REQUEST` 申请链（待建） |

### 1.3 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
|---------|------|---------|
| **BR-WHS-TI-001** | 写接口一律拒绝（须由调出 agree 生成壳单） | IF-WHS-TI-04～10 |
| **BR-WHS-TI-002** | 列表/详情仅展示 `CABINET_TRANSFER_IN` | IF-WHS-TI-02 / 03 |
| **BR-WHS-TI-003** | 目的位置门控为岗位可见**药库** | IF-WHS-TI-02 / 11 |
| **BR-WHS-TI-004** | 列表侧栏展示制单人/审批人；历史壳缺 `approverId` 时按 `originalBusNo` 只读回填（不写库） | IF-WHS-TI-02 |

---

## 2. 页面与接口总览

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 |
|---------|---------|---------|-------|---------|
| IF-WHS-TI-01 | 进入页面 | 初始化药库、枚举、人员下拉 | 读 | — |
| IF-WHS-TI-02 | 列表 | 按 `bus_no` 聚合分页调入壳单 | 读 | BR-WHS-TI-002 / 003 |
| IF-WHS-TI-03 | 详情 | 按 `busNo` 读调入单 | 读 | BR-WHS-TI-002 |
| IF-WHS-TI-04 | 新建 | **拒绝**（WHS_TI_002） | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-05 | 编辑 | **拒绝** | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-06 | 提交审批 | **拒绝** | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-07 | 撤回 | **拒绝** | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-08 | 同意 | **拒绝**（库存已在调出 agree 落账） | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-09 | 驳回 | **拒绝** | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-10 | 删除 | **拒绝** | 写 | BR-WHS-TI-001 |
| IF-WHS-TI-11 | 选品 | 来源药库库存选品（只读） | 读 | BR-WHS-TI-003 |

---

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |

---

## 4. 接口编排说明书（摘要）

### IF-WHS-TI-02 列表

1. Gate：分页、机构、`purposeLocationId`（0=全部可见药库）。
2. Pre：药库位置门控；`type_enum` 固定 `CABINET_TRANSFER_IN`。
3. Cmd：`SupplyRequestQueryService` 按 `bus_no` 聚合分页。
4. Asm：富化来源库名、状态文案。

### IF-WHS-TI-04～10 写接口

1. Gate：无业务 Gate。
2. Pre：无。
3. Cmd：**立即** `BusinessException(WHS_TI_002)`。

---

## 5. 业务异常与用户提示

| 错误码 | 场景 |
|--------|------|
| **WHS_TI_001** | 参数/门控非法 |
| **WHS_TI_002** | 手工写路径（新建/改单/审批/删除） |
| **WHS_TI_103** | 单据不存在或类型非调入 |
| **WHS_TI_102** | 列表/选品查询失败 |

---

## 6. 关联文档

- **DM-WKF-006** §6.1.8（v1.7）
- **AS-INV-WHS-009** 药库调出单
