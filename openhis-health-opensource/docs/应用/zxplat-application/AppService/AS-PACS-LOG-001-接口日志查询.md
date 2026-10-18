# 检查域 · 接口日志查询 · 应用服务设计（AS）

> **编码**：AS-PACS-LOG-001 · **版本**：v1.0 · **日期**：2026-09-22  
> **菜单**：接口日志 · **类名**：`PacsIntegrationLogAppService` · **路由**：`POST /api/app/pacsIntegrationLog/{method}`  
> **表**：`pacs_integration_log`（序号 16 / V12）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 集成日志分页查询、详情（含请求/响应摘要） |
| **在外** | 写日志（adapter/Domain `record`）；补发重试写路径（OutboundRetry） |
| **涉及域** | PacsIntegrationLog |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **运维 / 影像科管理员** | 按动作/结果/业务键查日志、看失败原因 |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **direction** | `outbound` / `inbound` |
| **outcome** | `success` / `failure` |
| **actionCode** | 如 `sendApply` / `reportFinalize` / `reportPath` / `retry` |

### 1.4 本期与非目标

| 类别 | 内容 |
|------|------|
| **本期（序号 46）** | 列表 + 详情只读 |
| **非目标** | 页面内一键重试写；物理删日志；跨租户联查 |

### 1.5 领域清单

| 聚合 | 说明 |
|------|------|
| PacsIntegrationLog | 序号 16 已落表；本期补读侧 search/get |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-LOG-01** | 默认排除软删行 | LOG-01a/b |
| **BR-LOG-02** | 纯只读；不得改 outcome | LOG-01a/b |
| **BR-LOG-03** | 列表可不回全文报文；详情返回 request/response | LOG-01a vs 01b |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 |
|----|----------|------|:--:|----------|
| **IF-PACS-LOG-01a** | 日志列表 | 条件分页 | 读 | BR-LOG-01/02/03 |
| **IF-PACS-LOG-01b** | 日志详情 | 含报文摘要 | 读 | BR-LOG-01/02 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（读侧 QueryService 与 App 同期落地）。

---

## 4. 接口编排说明书

### 4.1 IF-PACS-LOG-01a

| 维度 | 说明 |
|------|------|
| **主路径** | 按方向/动作/结果/业务键/时间窗分页 → 列表行（可不含大字段） |

### 4.2 IF-PACS-LOG-01b

| 维度 | 说明 |
|------|------|
| **主路径** | 按 id 加载 → 详情（含 requestBody/responseBody/errorMessage） |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_INTEGRATION_LOG_NOT_FOUND` | 详情不存在或已删 |
| `PACS_INTEGRATION_LOG_INVALID_ARGUMENT` | 分页非法、详情缺 id |

---

## 6. 事件语义

无。

---

## 7. 非功能性需求

列表 P95 < 1s；列表默认不选 Lob 全文可接受（实现可同表查询后 Mapper 省略字段）。

---

## 8. 测试要点

- 按 outcome=failure 过滤；详情 not found；软删不可见

---

## 9. 关联文档与变更历史

- SDD：[AS-PACS-LOG-001-SDD](../AppService-SDD/AS-PACS-LOG-001-接口日志查询-SDD.md)
- 对接：[PACS-Adapter对接说明](../../other/PACS-Adapter对接说明.md)
- 遗留：`WebLogController`

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-14 首版；解锁序号 46 |
