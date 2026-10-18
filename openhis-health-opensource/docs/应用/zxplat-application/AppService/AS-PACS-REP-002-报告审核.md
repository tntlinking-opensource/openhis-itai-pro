# 检查域 · 报告审核 · 应用服务设计（AS）

> **编码**：AS-PACS-REP-002 · **版本**：v1.4 · **日期**：2026-09-22  
> **菜单**：报告审核 · **类名**：`PacsReportAuditAppService` · **路由**：`POST /api/app/pacsReportAudit/{method}`  
> **领域**：[DM-PACS-002](./DM-PACS-002-PacsDiagnosticReport-检查报告.md)、[DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)、[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 待审列表/详情（含可选关键图/阅片）、审核通过/退回、**取消审核**、PDF 键预览 |
| **在外** | INT-06 adapter 回传（可选）；对象存储字节下载 |
| **涉及域** | PacsDiagnosticReport、DR index、Observation 投影、PEIS ext |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **审核医生** | 待审队列、通过/退回、取消审核、看 PDF 键 |
| **系统** | final → upsert index → projectFromPacsSummary → 可选 PEIS resultReceived；取消审核对称回滚 |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **待审** | `preliminary` 且 `submittedAt≠null` |
| **final** | 审核通过；写 DR index |
| **退回** | 清 `submittedAt`，回可编辑草稿 |
| **取消审核** | `final`→待审；index→`cancelled`；Observation→`cancelled`；PEIS PACS 派发 `resultReceived`→`sent` |

### 1.4 范围

| P0 | REP-06～08、REP-10；待审列表 |
| P1 | 双人审核开关配置页 |

### 1.5 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REP-AUD-01** | 通过 → DR `final` | REP-07 |
| **BR-REP-AUD-02** | **upsert** `cln_diagnostic_report_catalog`（module=PACS） | REP-07 |
| **BR-REP-AUD-06** | SR 有 `encounterId` 时同事务 `projectFromPacsSummary`；无 AOL 则跳过 Observation 行 | REP-07 |
| **BR-REP-AUD-03** | INT-06 回传可选（adapter） | REP-07 |
| **BR-REP-AUD-04** | 退回须填原因 | REP-08 |
| **BR-REP-AUD-05** | 书写人≠审核人（作者非空时强制） | REP-07 |
| **BR-REP-AUD-07** | 取消审核须填原因；仅 `final`；同事务吊销 index/投影/PEIS | REP-09 |

---

## 2. 接口总览

| IF | 动作 | 语义 | 读写 |
|----|------|------|:--:|
| **IF-PACS-REP-06a** | 待审列表 | 分页队列 | 读 |
| **IF-PACS-REP-06** | 待审详情 | 加载 | 读 |
| **IF-PACS-REP-07** | 审核通过 | final + index + 投影 | 写 |
| **IF-PACS-REP-08** | 审核退回 | 回草稿 | 写 |
| **IF-PACS-REP-09** | 取消审核 | final→待审 + 回滚 | 写 |
| **IF-PACS-REP-10** | PDF 预览 | 返回 `pdfStorageKey` | 读 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 状态 | 说明 |
|--------|------|------|
| GAP-PACS-REP-AUD-01 | **已关闭（最小）** | `LisResultProjectionService#projectFromPacsSummary` 已落地；无 AOL 时跳过 Observation |

---

## 4. 编排 · IF-PACS-REP-07

| 步骤 | 说明 |
|------|------|
| 1 | 待审校验；作者≠审核人 |
| 2 | `approve` → final |
| 3 | `upsertFromPacs` |
| 4 | 逐 orderLink SR：`projectFromPacsSummary` |
| 5 | 若有 PEIS ext：`updateDispatchFlags(..., PACS, resultReceived)` |
| 6 | `PacsReportApprovedEvent`（Domain 内已发） |

---

## 4.1 编排 · IF-PACS-REP-09（取消审核回滚口径）

| 步骤 | 说明 |
|------|------|
| 1 | 须 `final`；原因必填 |
| 2 | `cancelAudit` → preliminary（保留 `submittedAt`，回待审） |
| 3 | `revokeFromPacs`：index `status=cancelled`（**不软删**，保 uniqueKey） |
| 4 | `revokeFromPacsSummary`：Observation `status=cancelled`（不软删） |
| 5 | PEIS ext 若 `pacsDispatch=resultReceived` → `sent` |
| 6 | `PacsReportAuditCancelledEvent` |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_REPORT_INVALID_STATUS` | 非待审 / 非 final（取消） |
| `PACS_REPORT_AUDITOR_SAME_AS_AUTHOR` | 同人审核 |
| `PACS_REPORT_REJECT_REASON_REQUIRED` | 退回无原因 |
| `PACS_REPORT_CANCEL_REASON_REQUIRED` | 取消无原因 |

---

## 6. 关联文档

- SDD：[AS-PACS-REP-002-SDD](../AppService-SDD/AS-PACS-REP-002-报告审核-SDD.md)
- 书写：[AS-PACS-REP-001](./AS-PACS-REP-001-报告书写.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.4 | 2026-09-22 | REP-09 取消审核：定 index/Obs/PEIS 回滚口径并成码 |
| v1.3 | 2026-09-22 | 序号 56：待审详情装配 `imaging`（同 REP-001 SPI） |
| v1.2 | 2026-09-21 | 国基 D-7；对齐 DM-002；关闭投影 Gap 最小实现；待审列表 |
| v1.1 | 2026-06-27 | REP-07 对称 LIS 投影 |
| v1.0 | 2026-06-27 | 首版 |
