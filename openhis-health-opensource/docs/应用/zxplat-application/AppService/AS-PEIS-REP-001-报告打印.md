# 体检域 · 报告打印 · 应用服务设计（AS）

> **编码**：AS-PEIS-REP-001 · **版本**：v1.0 · **日期**：2026-06-27  
> **菜单**：报告打印 · **类名**：`PeisReportPrintAppService` · **路由**：`/peis/report/print`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 可打印列表、预览、生成/打印 PDF |
| **在外** | PrintTemplate（AS-BAS-PRT） 渲染；MAS-04 final 前置 |
| **涉及域** | DiagnosticReport、DR index |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **登记/报告员** | 查询、预览、打印 |
| **系统** | 读 DR + index；委托 health 打印运行时（**AS-BAS-PRT**） |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **可打印** | DR `final`；Encounter `finished`（院点可配置） |
| **PDF** | health 打印运行时（**AS-BAS-PRT**）渲染；存储 key 在 DR |

### 1.4 范围

| P0 | REP-01～03（打印核心）；**REP-05 按学号历年/毕业合打** |
| P1 | REP-04 历史报告查询（通用） |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REP-01** | 仅 `final` DR 可打印 | REP-02 / REP-05 |
| **BR-REP-02** | 打印记 `PeisReportPrintExt`（可选 CUS） | REP-03 |
| **BR-REP-03** | 团检按人+Encounter 独立报告 | REP-01 |
| **BR-REP-GRAD-01** | 毕业合打仅含 `DiagnosticReport.final` 的历年 PE 报告 | REP-05 |

---

## 2. 接口总览

| IF | 动作 | 语义 | 读写 |
|----|------|------|:--:|
| **IF-PEIS-REP-01** | 可打印列表 | 分页 | 读 |
| **IF-PEIS-REP-02** | 预览 | HTML/PDF | 读 |
| **IF-PEIS-REP-03** | 确认打印 | 记次数+出纸 | 写 |
| **IF-PEIS-REP-05** | 按学号历年 / 毕业合打 | 列历年 final → 预览或合打 PDF | 读/写 |

### 4.4 IF-PEIS-REP-05 毕业历年合打

| 步骤 | 说明 |
|------|------|
| 1 | 入参学号 `studentNo`（Campus 解析 patientId） |
| 2 | 列出该患者全部 PE Encounter 下 `final` DiagnosticReport（按体检日倒序） |
| 3 | 预览单份或合打多学年 PDF（对齐遗留 Y1/Y3 思路，产品化在 REP） |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**——依赖 PrintTemplate（AS-BAS-PRT） 模板（P0 可用默认模板）。

---

## 4. 编排说明书

### 4.1 IF-PEIS-REP-01 可打印列表

| 步骤 | 说明 |
|------|------|
| 1 | `PeisEncounterProgressQuery#searchPrintableReports` 分页 |
| 2 | 过滤 DR `final` + Encounter `finished` |

### 4.2 IF-PEIS-REP-02 预览

| 步骤 | 说明 |
|------|------|
| 1 | 校验 DR `final`（BR-REP-01） |
| 2 | `ReportRenderPort#renderPeisReport → previewUrl/stream |

### 4.3 IF-PEIS-REP-03 确认打印

| 步骤 | 说明 |
|------|------|
| 1 | 同 REP-02 门禁 |
| 2 | 可选写打印次数 CUS |
| 3 | `PeisReportPrintedEvent` |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `REPORT_NOT_FINAL` | 未总检完成 |
| `RENDER_FAILED` | 报告服务失败 |

---

## 6. 事件

`PeisReportPrintedEvent`（可选统计）。

---

## 7. NFR

预览 P95 < 5s；打印幂等（同 session 重复打印记日志）。

---

## 8. 测试

- 未 final 不可打印
- MAS-04 后可预览
- 团检多人独立 PDF

---

## 9. 关联文档

- MAS：[AS-PEIS-MAS-001](./AS-PEIS-MAS-001-总检诊台.md)
- index：[DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版 P0 报告打印 |
