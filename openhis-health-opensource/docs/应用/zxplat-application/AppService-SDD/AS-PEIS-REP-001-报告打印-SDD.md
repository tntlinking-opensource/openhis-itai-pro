# AS-PEIS-REP-001 报告打印 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-REP-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-REP-001-报告打印.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application/peis/report/print` |
| **类** | `PeisReportPrintAppService` |
| **边界** | 可打印列表；预览；确认打印 |
| **方法权威** | DM-PEIS-008、DM-CLN-006、PrintTemplate（AS-BAS-PRT） |
| **实现阶段口径** | **目标态全量契约**（P0） |

---

## 2. 代码结构

### 2.2 白名单

| 服务 | 方法 | IF |
|------|------|-----|
| PeisEncounterProgressQuery | `searchPrintableReports(ReportPrintQuery, Pageable)` | REP-01 |
| DiagnosticReportQueryService | `getByEncounterId(encounterId)` | REP-02 |
| ReportRenderPort | `renderPeisReport(PeisReportRenderRequest)` | REP-02 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| IF-PEIS-REP-01 | `searchPrintable` | 可打印列表 | `POST /api/app/peisReportPrint/searchPrintable` |
| IF-PEIS-REP-02 | `previewReport | 预览报告 | `POST /api/app/peisReportPrint/previewReport |
| IF-PEIS-REP-03 | `confirmPrint` | 确认打印 | `POST /api/app/peisReportPrint/confirmPrint` |

### 3.2 IF-PEIS-REP-01 `searchPrintable`（可打印列表）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `dateFrom` | `LocalDate` | 否 |
| `dateTo` | `LocalDate` | 否 |
| `pageNo` | `Integer` | 是 |
| `pageSize` | `Integer` | 是 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `rows` | `List<PrintableReportRowDto>` | |
| `total` | `Long` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisEncounterProgressQuery#searchPrintableReports(ReportPrintQuery, Pageable)` | **仅读路径** |
| 2 | Asm | Mapper | |

### 3.3 IF-PEIS-REP-02 `previewReport（预览报告）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `encounterId` | `Long` | 是 |

#### 失败码

| 码 | 场景 |
|----|------|
| `REPORT_NOT_FINAL` | DR 非 final |
| `RENDER_FAILED` | report 服务失败 |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `DiagnosticReportQueryService#getByEncounterId` | status=final |
| 2 | Pre | `ReportRenderPort#renderPeisReport(...)` | **仅读/外部渲染** |
| 3 | Asm | 返回 previewUrl / stream | |

### 3.4 IF-PEIS-REP-03 `confirmPrint`（确认打印）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | 同 REP-02 Gate | |
| 2 | Cmd | 可选写打印次数 CUS | BR-REP-02 |
| 3 | Asm | `PeisReportPrintedEvent` | |

---

## 4. QueryService 与领域事件

- REP-01 列表走 `PeisEncounterProgressQuery`；MAS-04 final 后列表刷新。
- REP-03 `PeisReportPrintedEvent` → 统计/发放（P1）。

---

## 5. 写库顺序

| IF | 写库 |
|----|------|
| REP-02/03 | 主路径只读 DR；REP-03 可选打印计数 CUS |

---

## 6. 非功能性需求实现

### 6.1 幂等性

- confirmPrint 重复 → 打印次数递增或拒绝（院点配置）。

### 6.2 并发控制

- 同 DR 并发 preview 只读无锁。

### 6.3 重试与容错

- ReportRenderPort 失败 → RENDER_FAILED，不重试写库。

### 6.4 数据脱敏

- preview 含患者信息权限 Gate。

### 6.5 审计日志

- confirmPrint 记 operatorId。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:--:|
| 未 final 不可预览 | 功能 | API | P0 |
| MAS-04 后可预览 | 功能 | 集成 | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 渲染超时 | 友好失败码 | P0 |

### 7.3 查询结果一致性

- REP-01 列表与 MAS-04 后 DR final 一致。

### 7.4 集成测试

- PrintTemplate（AS-BAS-PRT） 联调 previewUrl。

### 7.5 测试缺口跟踪

| 缺口 | ReportRenderPort 桩 | P0 |

---

## 8. 变更历史

| v1.0 | 2026-06-27 | 首版 |
