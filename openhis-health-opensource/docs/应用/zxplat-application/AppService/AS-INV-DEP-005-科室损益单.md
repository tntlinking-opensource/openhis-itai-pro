# 科室药房损益单 · 应用服务设计（AS）

> **页面边界**：科室药房业务线 — **`DEPT_GAIN_LOSS`** 主动制单；库位须为 **`LocationForm#DEPARTMENT_PHARMACY`**；同意须经**财务岗位**审批，发放入账调整本库库存。
>
> **与药房对偶**：与 **AS-INV-PHA-004-药房损益单** 同构；差异仅为 **`type_enum`**、**init 仅科室药房列表**、**经手人下拉为全体在职医务人员（不限药师/库管）**、**`bus_no` 前缀 `DGL`**、错误码前缀 **`DEP_GL_*`**。
>
> **编码**：AS-INV-DEP-005 · **版本**：v0.2 · **日期**：2026-08-31

---

## 1. 场景概述

| 维度 | 说明 |
|------|------|
| **在内** | 损益草稿、审批、同意落账（`createSupplyDelivery` + 库存报溢/报损） |
| **审批** | 须财务审批岗位（`DEPT_GAIN_LOSS` 在 `SupplyType#requiresFinanceApproval` 白名单） |
| **库位** | **`purpose_location_id`** 须为启用中 **`DEPARTMENT_PHARMACY`**；下拉来自 **`LocationQueryService#listAllActiveDepartmentPharmacies`** + 管辖 Gate |
| **在外** | 科室盘点（**AS-INV-DEP-006**）；药库/药房损益 |

## 2. 页面与接口总览

| IF 编号 | 用户动作 | 接口语义 | 读/写 |
| --- | --- | --- | --- |
| IF-DEP-GL-01 | 进入页面 | init：科室药房 OptionDto + 项目类型 + SupplyStatus + **经手人（全体在职医务人员，含拼音）** | 读 |
| IF-DEP-GL-02 | 列表 | 分页 **`DEPT_GAIN_LOSS`**，按 **`purpose_location_id`** 筛选 | 读 |
| IF-DEP-GL-03 | 详情 | 批号级明细 + 盈亏/实盘/金额 + 目录槽价（历史落库价） | 读 |
| IF-DEP-GL-04 | 新建 | **`createGainLoss`**，`type_enum`=`DEPT_GAIN_LOSS` | 写 |
| IF-DEP-GL-05 | 编辑 | 可编辑态全量对齐改单 | 写 |
| IF-DEP-GL-06 | 提交 | 待审核 → 审核中 | 写 |
| IF-DEP-GL-07 | 撤回 | 审核中撤回 | 写 |
| IF-DEP-GL-08 | 同意 | 损益发放 + 库存盈亏加减 | 写 |
| IF-DEP-GL-09 | 驳回 | 驳回 | 写 |
| IF-DEP-GL-10 | 删除 | 逻辑删除 | 写 |
| IF-DEP-GL-11 | 选品 | 库存批号行 + **`purposeLocationId`**；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**） | 读 |

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口；`createGainLoss` / `createSupplyDelivery` 已由 DM-WKF-006/005 覆盖 | — |

## 4. 业务异常（与实现对齐）

| 语义代码 | 场景 |
| --- | --- |
| DEP_GL_001 | 参数/明细校验失败 |
| DEP_GL_002 | 科室药房位置非法或越权 |
| DEP_GL_003 | 状态不允许提交/撤回/驳回 |
| DEP_GL_004 | 禁止编辑 |
| DEP_GL_005 | 删除拒绝 |
| DEP_GL_101 | init 失败 |
| DEP_GL_102 | 列表/选品查询失败 |
| DEP_GL_103 | 单据不存在或类型不匹配 |
| DEP_GL_104 | 重复同意或发放/库存编排失败 |
| DEP_GL_105 | 并发冲突 |

## 5. 关联文档

| 文档 |
| --- |
| `DM-WKF-006-SupplyRequest-供应请求.md` §6.1.6 |
| `DM-WKF-005-SupplyDelivery-供应发放.md` |
| `AS-INV-PHA-004-药房损益单.md` |
| `AS-INV-DEP-005-科室损益单-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.2 | 2026-08-31 | **IF-DEP-GL-01**：经手人下拉改为全体在职医务人员（含拼音），不限药师/库管 |
| v0.1 | 2026-07-01 | 初稿 |

---

**文档负责人**：（待填） · **评审**：（待填）
