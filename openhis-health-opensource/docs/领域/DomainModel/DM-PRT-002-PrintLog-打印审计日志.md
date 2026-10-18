# DM-PRT-002 PrintLog 打印审计日志

> **限界上下文**：本地扩展 · 画布打印（Print）  
> **代码模块**：`zxplat/zxplat-*`  
> **聚合类型**：单表实体聚合（append-only 审计）  
> **版本**：v1.4 · **日期**：2026-06-22  
> **Flyway 真源**：`zxplat-domain/.../db/migration/health/`（**清库重跑**口径：新表结构 + JSONB；废弃 `client_job_id`、`event_type`）  
> **决策纪要**：2026-06-22 Print SDD 决策（traceId 闭环、DM 全量列、SUCCESS/FAILED、confirm 归 Job）

---

## 1. 概述

### 1.1 业务定义

**打印审计日志（PrintLog）** 记录一次 **preparePrint → 本地渲染 → confirmPrint** 闭环的 **结果快照**，用于追溯「谁在何时、以何模板、打印了哪类业务单据」。

- **不**存储 PrintJson 全文（体积大、可版本漂移）；存 **`bus_no`**、`template_version` 与可选 **`content_hash`**。
- **不** FK 至 `prt_template`；模板删除后审计仍可读。
- 写入时机：**confirmPrint** 成功或失败时由 **`PrintJobAppService`** 追加一行（与 prepare 同闭环）。

本文档编码为 **`DM-PRT-002`**；**无 FHIR 标准资源一一映射**（见 §9.2）。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 打印闭环审计 | `trace_id` 关联 prepare 与 confirm |
| 合规追溯 | 操作人、租户、院点（会话上下文）、时间 |
| 失败诊断 | `status=FAILED` + `error_code` / `error_message` |
| 分页查询 | Web **打印日志** 列表（`PrintLogAppService` 只读） |

### 1.3 适用范围

| 场景 | 说明 |
| --- | --- |
| 收费小票打印成功 | `bus_no=PT202606260005`，`scenario_code=op-charge-print`，`status=SUCCESS` |
| Host 渲染 / 打印失败 | `status=FAILED` |
| 重复 confirm | 每次 confirm 独立一行（幂等由 trace + 业务层控制） |

### 1.4 与关键上下游及交换边界的关系

| 维度 | 上游 | 本聚合 | 下游 |
| --- | --- | --- | --- |
| 职责 | preparePrint / confirmPrint | 追加审计行 | Web 日志列表、合规导出 |
| 数据 | traceId、busNo、templateVersion、操作人 | 快照字段 | 报表（规划） |
| 引用 | **DM-PRT-001**（逻辑引用 bus_no） | 无 FK | — |

> **边界**：本聚合**不**参与模板编辑；**不**重放 printData。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**单表 append-only**；无更新业务语义（软删仅框架层）。

| 物理表 | 说明 |
| --- | --- |
| `prt_print_event_log` | 聚合根 |

**建议索引**：

- `idx_prt_log_tenant_time (tenant_id, created_at DESC)`
- `idx_prt_log_bus_no (tenant_id, bus_no, created_at DESC)`
- `idx_prt_log_trace (trace_id)`（**非**唯一；同一 traceId 允许多次 confirm 多行）

> **废弃列（不再使用）**：`client_job_id`、`event_type`（清库重跑后自表结构中移除）。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 日志标识 | `PrintLogId` | 主键 `id`（`Long`） |

---

## 3. 实体

### 3.1 PrintLog（聚合根）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 日志标识 | `id` | `Long` | 是 | 主键 |
| 租户 | `tenant_id` | `Long` | 是 | |
| 院点 | `organization_id` | `Long` | 否 | 来自会话院点上下文（可空） |
| 追踪号 | `trace_id` | `String(64)` | 是 | **preparePrint 返回**，confirm **必带回** |
| 业务编码 | `bus_no` | `String(64)` | 是 | resolved 模板键（API **`busNo`**） |
| 绑定码 | `binding_code` | `String(128)` | 否 | 入参曾传 bindingCode 时冗余；直传 busNo 可空 |
| 模板版本 | `template_version` | `Integer` | 否 | prepare 时 resolved published version |
| 状态 | `status` | `String(16)` | 是 | **`SUCCESS`** / **`FAILED`**（大写） |
| 打印机 | `printer_name` | `String(256)` | 否 | |
| 份数 | `copies` | `Integer` | 否 | 失败可为 0 |
| 内容摘要 | `content_hash` | `String(64)` | 否 | printData 规范化 JSON 的 SHA-256 hex |
| 错误码 | `error_code` | `String(64)` | 否 | FAILED 时 |
| 错误信息 | `error_message` | `TEXT` | 否 | FAILED 时 |
| 业务引用 | `biz_ref_json` | `JSONB` | 否 | 业务页传入的 bizRef |
| 客户端 | `client_type` | `String(32)` | 否 | confirm 请求传入；Vue+Host 路径默认 `openhis-win` |
| 操作人 | `operator_id` | `Long` | 否 | |
| 操作人名 | `operator_name` | `String(64)` | 否 | confirm 时由 `PrintOperatorResolver` 冗余 |
| 审计 | `created_at` 等 / `is_deleted` | — | — | 仅创建，无业务 update |

---

## 4. 值对象与枚举

### 4.1 PrintLogStatus（`status`）

存库 **VARCHAR**，值为枚举 **code**（**大写**）：

| code | 说明 |
| --- | --- |
| `SUCCESS` | confirmPrint 成功（渲染 + 提交打印闭环完成） |
| `FAILED` | 渲染、Host 打印或 confirm 前失败 |

> **`cancelled`** 本期**不做**；用户取消场景映射为 `FAILED` + 相应 `error_code`。

---

## 5. 领域事件

首期 **不发** 域事件；日志表即事实记录。

---

## 6. 领域服务与应用编排

### 6.1 PrintJobAppService（写路径）

| 方法 | 权限 | 说明 |
| --- | --- | --- |
| `preparePrint` | `Print:print` 或 `Print:preview` | 返回 traceId、busNo、templateVersion、PrintJson；**不写本表** |
| `confirmPrint` | `Print:print` 或 `Print:preview` | 校验 traceId，**追加一行 PrintLog** |

### 6.2 PrintLogAppService（读路径）

| 方法 | 权限 | 说明 |
| --- | --- | --- |
| `queryPage` | `Print:audit` | 明细分页 |
| `queryDailySummary` | `Print:audit` | 日汇总（内存聚合） |

> **无** `getByTraceId` 独立 IF；详情弹窗复用列表行（**AS-BAS-MST-018**）。

### 6.3 与 preparePrint 协作

```text
Vue ──preparePrint(busNo|bindingCode)──> traceId + busNo + templateVersion + PrintJson
Vue ──Host 打印──>
Vue ──confirmPrint(traceId, status, ...)──> PrintLog 落库
```

| 步骤 | 规则 |
| --- | --- |
| prepare | 服务端生成 `traceId`（UUID 等），**不**落库 |
| confirm | `trace_id` 必填且与 prepare 一致；`bus_no` / `template_version` 以 prepare 结果为准（请求可冗余校验） |

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| BR-PRT-LOG-001 | `trace_id` 非空且全局唯一 | |
| BR-PRT-LOG-002 | `bus_no` 非空 | 与模板业务键一致 |
| BR-PRT-LOG-003 | `status` 为 `SUCCESS` 或 `FAILED` | |
| BR-PRT-LOG-004 | append-only；无业务 update | |
| BR-PRT-LOG-005 | `content_hash` 由 confirm 请求 `printData` 规范化后 SHA-256 | 可选空 |

---

## 8. 上下文映射

### 8.1 上游

| 来源 | 关系 |
| --- | --- |
| PrintJobAppService.preparePrint | 产生 traceId、resolved busNo / templateVersion |
| Vue + OpenHIS.Win | confirmPrint payload、client_type |

### 8.2 下游

| 消费方 | 方式 |
| --- | --- |
| Web 打印日志页 | queryPage / queryDailySummary |
| 合规 / 报表 | 导出（规划） |

### 8.3 引用规则

| 模型 | 关系 |
| --- | --- |
| DM-PRT-001 PrintTemplate | 逻辑引用 `bus_no` + `template_version`；无 FK |
| DM-PRT-003 PrintBinding | `binding_code` 可选冗余 |

---

## 9. 补充

### 9.1 content_hash

对 **printData JSON 规范化序列化** 后 SHA-256 hex。**非**防篡改签名；用于弱审计与「同模板不同数据」区分。

### 9.2 client_type 与 operator_name

| 字段 | 口径 |
| --- | --- |
| `client_type` | confirm 请求字段；未传时默认 `openhis-win`（Web + OpenHIS.Win 路径） |
| `operator_name` | confirm 时从当前登录用户冗余写入（与 `operator_id` 同源解析器） |

### 9.3 与 FHIR / 标准的映射说明

| 概念 | FHIR 近似（仅概念参考） |
| --- | --- |
| 打印操作审计 | `AuditEvent`（字段、粒度均不等同） |
| 已打印文档 | `DocumentReference`（本表不存文档本体） |

---

## 10. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.4 | 2026-06-22 | SDD 决策对齐：traceId 闭环、全量列、废弃 client_job_id/event_type、SUCCESS/FAILED、confirm 归 Job、权限 Print:audit |
| v1.3 | 2026-06-22 | 聚合命名 **PrintLog** |
| v1.2 | 2026-06-22 | Flyway V1 即 `bus_no` |
| v1.1 | 2026-06-22 | 迁入 `health/DomainModel` |
| v1.0 | 2026-06-22 | 初稿 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PrtPrintEventLogDomainService`

| 方法 |
|------|
| `appendLog` |

### QueryService：`PrtPrintEventLogQueryService`

| 方法 |
|------|
| `pageByFilter` |
| `pageForDailySummary` |
| `listSuccessByBindingCode` |

