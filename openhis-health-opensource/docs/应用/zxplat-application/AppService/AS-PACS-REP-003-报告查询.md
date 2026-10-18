# 检查域 · 报告查询 · 应用服务设计（AS）

> **编码**：AS-PACS-REP-003 · **版本**：v1.0 · **日期**：2026-09-22  
> **菜单**：报告查询 · **类名**：`PacsReportQueryAppService` · **路由**：`POST /api/app/pacsReportQuery/{method}`  
> **领域**：[DM-PACS-002](./DM-PACS-002-PacsDiagnosticReport-检查报告.md)、[DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md)

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 影像科报告列表、按患者历史报告、报告只读详情（含 PDF 键） |
| **在外** | 书写/审核写路径；医生站 CLN 报告查询（AS-OP-DOR-008）；对象存储下载字节 |
| **涉及域** | PacsDiagnosticReport、PacsWorkItem、Patient（姓名装配） |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **报告医生 / 技师** | 按条件查本科室报告、看某患者历史 |
| **审核医生** | 查已发报告与 PDF 键（只读） |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **报告列表** | 租户内 `pacs_diagnostic_report` 主报告分页（不含已软删） |
| **历史报告** | 同 `patientId` 的报告时间倒序列表 |

### 1.4 本期与非目标

| 类别 | 内容 |
|------|------|
| **本期（序号 45）** | REP-11 列表 + REP-12 患者历史 + 只读详情（含 pdfStorageKey） |
| **非目标** | 修订版树（amended）；跨院联查；医生站合并 LIS/PACS Tab |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsDiagnosticReport | [DM-PACS-002](./DM-PACS-002-PacsDiagnosticReport-检查报告.md) |
| PacsWorkItem | [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md)（检查号装配） |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REP-Q-01** | 默认仅主报告（`amendedFromId` 空） | REP-11/12 |
| **BR-REP-Q-02** | 列表/历史为只读；不改变报告状态 | REP-11/12/13 |
| **BR-REP-Q-03** | 历史须指定 `patientId` | REP-12 |
| **BR-REP-Q-04** | 出参装配检查号/姓名；缺失时字段可空，不得失败 | REP-11/12/13 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 |
|----|----------|------|:--:|----------|
| **IF-PACS-REP-11** | 报告列表 | 条件分页查询 | 读 | BR-REP-Q-01/02/04 |
| **IF-PACS-REP-12** | 患者历史报告 | 同患者倒序列表 | 读 | BR-REP-Q-01/02/03/04 |
| **IF-PACS-REP-13** | 报告只读详情 | 正文 + PDF 键 | 读 | BR-REP-Q-02/04 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（读侧扩展 `searchReports` / `searchHistoryByPatient` 与 App 同期落地）。

---

## 4. 接口编排说明书

### 4.1 IF-PACS-REP-11

| 维度 | 说明 |
|------|------|
| **主路径** | 按状态/科室/患者/签发时间窗分页查报告 → JOIN 工单检查号 + 患者姓名 → 列表 |

### 4.2 IF-PACS-REP-12

| 维度 | 说明 |
|------|------|
| **主路径** | 校验 patientId → 同患者主报告倒序分页 → 装配检查号/姓名 |

### 4.3 IF-PACS-REP-13

| 维度 | 说明 |
|------|------|
| **主路径** | 按 reportId 取详情 → 装配工单/姓名 |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_REPORT_NOT_FOUND` | 详情报告不存在 |
| `PACS_REPORT_INVALID_ARGUMENT` | 分页非法、历史缺 patientId |

---

## 6. 事件语义

本期不发事件（纯读）。

---

## 7. 非功能性需求

列表 P95 < 1s；姓名/检查号批量装配，避免逐行查库。

---

## 8. 测试要点

- 列表按 status 过滤正确；历史同患者；详情含 pdfStorageKey；缺姓名不抛错

---

## 9. 关联文档与变更历史

- SDD：[AS-PACS-REP-003-SDD](../AppService-SDD/AS-PACS-REP-003-报告查询-SDD.md)
- 书写/审核：AS-PACS-REP-001 / 002
- 遗留：`hzjcqkBgList`、`hzjcqkBglsList`

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-13 首版；解锁序号 45 |
