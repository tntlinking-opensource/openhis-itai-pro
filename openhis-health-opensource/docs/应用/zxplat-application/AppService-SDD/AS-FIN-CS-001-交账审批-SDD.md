# 交账审批 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-FIN-CS-001-交账审批.md`（**v1.2**）  
> **编码**：AS-FIN-CS-001-SDD · **版本**：v1.1 · **日期**：2026-07-28  
> **边界真源**：`DD-交账日结改造-收款与药房审批流.md`（**v1.5.3**）+ **DM-FIN-003 v3.0**  
> **说明**：本 SDD **不维护 Gap 表**；编排细节以 AS 正文为准；详情 body **按挂载现算**，不读 `print_snapshot_json`。

---

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| **模块** | 财务管理 · **交账审批**（`finance/cashiersettlementapproval`） |
| **菜单** | **财务管理** → **交账审批**（Flyway **V15**） |
| **HTTP** | `POST /api/app/cashierSettlementApproval/{methodName}` |
| **状态流** | 列表/详情读头表；**仅 `SUBMITTED`** 可 **通过** → `APPROVED` 或 **驳回** → `REJECTED`（清挂载） |
| **非目标** | 本页提交/撤回（各日结作业 AS）；供应/调价审批（**AS-FIN-APR-001**） |

---

## 2. 接口白名单

| IF | 应用服务方法 | 中文 | HTTP |
| --- | --- | --- | --- |
| IF-CS-APR-01 | `loadCashierSettlementApprovalPageInit` | 页初始化 | `POST .../loadCashierSettlementApprovalPageInit` |
| IF-CS-APR-02 | `pageCashierSettlementApprovals` | 交账头分页列表 | `POST .../pageCashierSettlementApprovals` |
| IF-CS-APR-02 | `loadCashierSettlementApprovalDetail` | 详情（头 + 现算 body） | `POST .../loadCashierSettlementApprovalDetail` |
| IF-CS-APR-03 | `approveCashierSettlement` | 审批通过 | `POST .../approveCashierSettlement` |
| IF-CS-APR-04 | `rejectCashierSettlement` | 驳回并清挂载 | `POST .../rejectCashierSettlement` |

> 列表筛选：费用日结（`OP_PAYMENT`/`IP_PAYMENT`）或药房日结（`OP_PHARMACY`/`IP_PHARMACY`）；时间优先（`submitted_at`、`date_from`～`date_to`）；**可选 `locationId`**（药房日结）。  
> 列表/详情头展示名：`CashierSettlementApprovalDisplayEnricher` 批量回填 `submitterName` / `approverName` / `locationName` / `orgName`。  
> 详情 body：`CashierSettlementApprovalDetailBodySupport` 按 `type_enum` 复用对应日结预览编排，对**已挂载**源行现算。  
> IF-CS-APR-01：另返回 `pharmacyOptions`（`LocationQueryService.listAllActivePharmacies` + 药房日结 Option 装配）。

---

## 3. 领域依赖（摘要）

| 依赖 | 用途 |
| --- | --- |
| `CashierSettlementQueryService` | 头表读、审批分页 |
| `CashierSettlementDomainService` | `approveCashierSettlement` / `rejectCashierSettlement` |
| `FinanceApprovalAuthorizer` | 写路径财务角色门控 |
| `CashierSettlementApprovalDisplayEnricher` | 交账人/审批人/药房/科室展示名 |
| `LocationQueryService` | 药房下拉、药房名 enrich |
| 各日结 AppService / PreviewAssembler | 详情 body 现算（只读） |

---

## 4. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-07-27 | 首版 stub：对齐 AS-FIN-CS-001 落地态；SUBMITTED 审批流；详情按挂载现算 |
| v1.1 | 2026-07-28 | 列表/详情展示名 enrich；药房 `locationId` 筛选；init `pharmacyOptions`；对齐 AS v1.2 / DD v1.5.3 |
