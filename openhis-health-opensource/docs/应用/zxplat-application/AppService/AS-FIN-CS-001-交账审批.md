# 交账审批 · 应用服务设计（AS）

> **文档职责**：财务管理下「交账审批」页的 IF / BR / 编排权威（费用日结 + 药房日结切换）。  
> **页面边界**：财务管理 → **交账审批** — 列表（交账时间 + 业务窗优先）→ 详情（按挂载现算日结单）→ **通过 / 驳回**。作业页负责提交/撤回。  
> **注意**：与 **AS-FIN-APR-001 财务审批**（供应单/调价）**分立**，勿混菜单与白名单。  
> **边界真源**：DD 交账日结改造 **v1.5.10** + **DM-FIN-003 v3.0**。  
> **编码**：AS-FIN-CS-001 · **版本**：v1.4 · **日期**：2026-08-17

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 初始化筛选项；交账头分页列表；详情（日结单现算）；待审**通过**；待审**驳回**（清挂载） |
| **在外** | 日结作业提交/撤回（各 OP/IP FIN/PHA 日结 AS）；供应/调价财务审批（**AS-FIN-APR-001**）；审批后再驳回 |
| **涉及域** | **CashierSettlement**；详情现算涉及 Payment 或 MedicationDispense |
| **后置** | 通过 → `APPROVED`；驳回 → `REJECTED` + 清挂载 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **财务人员** | 筛选费用/药房 → 列表 → 详情 → 通过/驳回 |
| **作业员** | 不在本页提交；撤回在各日结作业页 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **费用日结** | `OP_PAYMENT` / `IP_PAYMENT` |
| **药房日结** | `OP_PHARMACY` / `IP_PHARMACY` |
| **列表优先字段** | `submitted_at`、`date_from`～`date_to`、`payment_count`、`total_income`、状态、单号；**展示名**：交账人、审批人、审批时间、药房名/科室名；**金额口径**：收款=`总收入`；药房=**售价合计**（头表快照，与 AS-OP/IP-PHA-006 同口径） |
| **药房筛选** | 大类=`PHARMACY` 时可按头表 `location_id` 精确筛；init 返回 `pharmacyOptions` |
| **详情 body** | **按挂载源表现算**（`CashierSettlementApprovalDetailBodySupport`）；头区回显交账人/审批人/审批时间/药房或科室；不读已删 `print_snapshot_json`；头表 `payment_count`/`total_income` 仅列表快照（药房详情头金额现算售价合计复核） |
| **驳回** | 仅 `SUBMITTED`；清挂载（**BR-CS-032**） |
| **通过** | 仅 `SUBMITTED` → `APPROVED`；不改挂载（**BR-CS-033**） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-CS-APR-01～05 |
| **非目标** | 本页提交/撤回；审批后再驳回；与 APR-001 合并 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
| --- | --- | --- |
| CashierSettlement | 列表读；approve/reject | DM-FIN-003 |
| Payment / Dispense | 详情现算 | DM-FIN-002 / DM-MED-002 |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-CS-APR-001** | 列表可按费用/药房大类、状态、交账时间、业务窗筛选；药房大类可再按 `locationId` 筛 | 02 |
| **BR-CS-APR-002** | 仅 `SUBMITTED` 可通/驳 | 03、04 |
| **BR-CS-APR-003** | 通过不改挂载；驳回清挂载 | 03、04 |
| **BR-CS-APR-004** | 详情按挂载现算；头表金额仅列表快照；头区须回显交账人/审批人/审批时间/药房或科室 | 02、详情 |
| **BR-CS-APR-005** | 交账审批**不**改变退费/退药门禁 | — |
| **BR-CS-APR-006** | 列表行须 enrich 交账人/审批人/药房/科室展示名（批量读，禁 N+1） | 02 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键状态变化 |
| --- | --- | --- | --- | --- |
| IF-CS-APR-01 | 进入页 | 类型大类/状态/药房下拉 | 读 | — |
| IF-CS-APR-02 | 查询列表/打开详情 | 头表列表；详情现算 | 读 | — |
| IF-CS-APR-03 | 通过 | 审批通过 | 写 | SUBMITTED→APPROVED |
| IF-CS-APR-04 | 驳回 | 驳回并清挂载 | 写 | SUBMITTED→REJECTED |
| IF-CS-APR-05 | 运维回刷 | 药房头表 `total_income` 按挂载发药现算售价合计回写 | 写 | 存量利润→售价 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 类型 | 说明 | 状态 |
| --- | --- | --- | --- |
| GAP-CS-APR-001 | DM 写能力 | approve/reject + 审批分页读（DM v3.0 / SDD） | **已关闭**（领域 `approveCashierSettlement`/`rejectCashierSettlement` + `pageCashierSettlementApprovals` 已成码；应用 **AS-FIN-CS-001** 已落地） |

---

## 4. 接口编排说明书（业务步骤）

### IF-CS-APR-01 初始化

返回费用/药房大类选项、类型选项、状态选项、**药房 Option**（`pharmacyOptions`，供药房日结筛选）。

### IF-CS-APR-02 列表与详情

列表读头表（时间优先；可选 `locationId`）；行 DTO 含 `submitterName`/`approverName`/`approvedAt`/`locationName`/`orgName`（`CashierSettlementApprovalDisplayEnricher` 批量回填）。详情头同口径展示名；body 按 type 调对应日结现算编排（只读）。

### IF-CS-APR-03 通过

断言 SUBMITTED → `approveCashierSettlement` → 事件。

### IF-CS-APR-04 驳回

断言 SUBMITTED → `rejectCashierSettlement`（清挂载）→ 事件。

### IF-CS-APR-05 药房售价合计存量回刷

`POST .../refreshPharmacySettlementSaleTotals`：按挂载发药现算售价合计写回头表 `total_income`（仅 `OP_PHARMACY`/`IP_PHARMACY`；无挂载跳过）。财务角色。

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 |
| --- | --- |
| EX-CS-APR-101 | 初始化/查询失败 |
| EX-CS-APR-201 | 非待审不可通/驳 |
| EX-CS-APR-301 | 并发状态冲突 |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF |
| --- | --- |
| CashierSettlementApprovedEvent | 03 |
| CashierSettlementRejectedEvent | 04 |

---

## 7. 非功能性需求

列表不扫源表聚合金额（用头表快照）；详情可现算。

---

## 8. 测试要点（业务视角）

费用/药房切换；药房大类可按药房筛；列表展示交账人/审批人/审批时间/药房或科室；详情头回显一致；通过后不可再驳；驳回后作业页可再交。

---

## 9. 关联文档与变更历史

| 文档 |
| --- |
| `health/设计决策/DD-交账日结改造-收款与药房审批流.md` |
| `DM-FIN-003` / DomainModel-SDD |
| `AS-OP-FIN-004` / `AS-IP-FIN-005` / `AS-OP-PHA-006` / `AS-IP-PHA-006` |
| `AS-FIN-APR-001-财务审批.md`（分立，勿混） |

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-07-27 | 首版：财务管理交账审批 |
| v1.1 | 2026-07-27 | 关闭 GAP-CS-APR-001；明确详情 body 按挂载现算；对齐 DD v1.4 落地态 |
| v1.2 | 2026-07-28 | 列表/详情展示名（交账人、审批人、审批时间、药房/科室）；药房可筛；init 带回 pharmacyOptions；对齐 DD v1.5.3 |
| v1.3 | 2026-08-17 | 药房日结列表/详情金额口径明确为**售价合计**（对齐 AS-OP/IP-PHA-006 v1.14 / DD v1.5.10） |
| v1.4 | 2026-08-17 | 增 IF-CS-APR-05：药房交账头表售价合计存量回刷 |
