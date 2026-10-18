# 基础资料 · 打印日志查看 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-BAS-MST-018-打印日志查看.md`（v1.1）  
> **编码**：AS-BAS-MST-018-SDD · **版本**：v1.1 · **日期**：2026-06-22

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | `zxplat-domain` · `PrintLogAppService` |
| **边界** | IF-BAS-PRT-01 `queryPage`、IF-BAS-PRT-02 `queryDailySummary`；**只读** |
| **编排权威** | `AS-BAS-MST-018` §2、§4 |
| **方法权威** | `DM-PRT-002-SDD` 实体字段；读经 Repository Specification |
| **实现阶段口径** | **目标态**（traceId 等新字段 DTO）；Flyway 清库重跑后生效 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/PrintLog/{methodName}` |
| **权限** | `Print:audit`；本期不写 @RequirePermission |
| **事务** | 无写事务 |
| **分页** | 请求 `pageNo`/`pageSize`；响应 `PagedResult` 或工程等价结构 |
| **写路径** | confirmPrint **不在**本服务（归 `PrintJobAppService`） |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat-domain/src/main/java/com/openhis/whale/Print/application/log/
  service/PrintLogAppService.java
  request/QueryPrintLogRequest.java
  request/QueryPrintDailySummaryRequest.java
  dto/PrintEventLogItemDto.java
  dto/PrintDailySummaryItemDto.java
```

### 2.2 依赖注入

| Bean | 用途 |
| ---- | ---- |
| `PrtPrintEventLogRepository` | Specification 分页只读 |
| `TenantContext` | 租户 Gate |

### 2.3 领域方法白名单

| 类型 | 方法 | 用于 IF |
| ---- | ---- | ------- |
| Repository | `findAll(Specification, Pageable)` | 01、02 |
| Repository | `count(Specification)` | 01 |

> Print 读侧 **无** PrintLog QueryService；与 health B.6.4 不适用。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| ---- | ------------ | ----------- | ---- |
| 日志状态 | `QueryPrintLogRequest.status` | DM-PRT-002 §4.1 `status` | SUCCESS/FAILED 筛选 |
| 业务键 | `QueryPrintLogRequest.busNo` | DM-PRT-002 §3.1 `bus_no` | 精确匹配 |
| 追踪号展示 | `PrintEventLogItemDto.traceId` | DM-PRT-002 §3.1 `trace_id` | 替代 clientJobId |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| -- | -------------------- | ---------- | ---- |
| IF-BAS-PRT-01 | `queryPage` | 明细分页查询 | `POST /api/app/PrintLog/queryPage` |
| IF-BAS-PRT-02 | `queryDailySummary` | 日汇总查询 | `POST /api/app/PrintLog/queryDailySummary` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| ---- | ---- | ------ |
| **Gate** | 租户门禁 | ValidationException |
| **Pre** | Specification 构建 | — |
| **Asm** | 实体→DTO / 内存聚合 | — |

---

### 3.2 IF-BAS-PRT-01 `queryPage`（明细分页查询）

#### Request `QueryPrintLogRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `pageNo` | Integer | 否 | 默认 1 |
| `pageSize` | Integer | 否 | 默认 20 |
| `busNo` | String | 否 | 精确 |
| `status` | String | 否 | 转大写 |
| `dateFrom` | LocalDate | 否 | 闭区间左端 |
| `dateTo` | LocalDate | 否 | 闭区间右端（`createdAt < dateTo+1`） |

#### Response 行 `PrintEventLogItemDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | Long | |
| `traceId` | String | |
| `busNo` | String | |
| `bindingCode` | String | 可选 |
| `templateVersion` | Integer | 可选 |
| `status` | String | |
| `printerName` | String | |
| `copies` | Integer | |
| `operatorId` | Long | |
| `operatorName` | String | |
| `errorCode` | String | |
| `errorMessage` | String | |
| `createdAt` | LocalDateTime | |

#### 失败码口径

| 场景 | 异常 |
| ---- | ---- |
| 无租户 | `ValidationException` EX-PRT-LOG-001 |
| 查询失败 | 包装 LOG-101 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| ---- | ---- | ---- | -------- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | — | LOG-001 |
| 2 | Pre | 构建 `Specification` | tenantId、busNo、status、日期 | BR-PRT-LOG-QRY-001～002 |
| 3 | Pre | `PrtPrintEventLogRepository#findAll` | spec, pageable | `createdAt DESC`；**仅读** |
| 4 | Asm | 映射 `PrintEventLogItemDto` | 实体列表 | 字段对齐 §3.2 Response |

---

### 3.3 IF-BAS-PRT-02 `queryDailySummary`（日汇总查询）

#### Request `QueryPrintDailySummaryRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `dateFrom` | LocalDate | 否 | 默认今日-7 |
| `dateTo` | LocalDate | 否 | 默认今日 |
| `busNo` | String | 否 | 可选过滤 |

#### Response 行 `PrintDailySummaryItemDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `day` | LocalDate | 分组日 |
| `busNo` | String | |
| `successCount` | Integer | status=SUCCESS |
| `failedCount` | Integer | 其余 |

#### 失败码口径

同 IF-01 LOG-001。

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| ---- | ---- | ---- | -------- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | — | LOG-001 |
| 2 | Pre | 复用 IF-01 同等 Specification | dateFrom/dateTo/busNo | |
| 3 | Pre | `Repository#findAll` 内部分页 | pageSize=10000 | 内存聚合窗口 |
| 4 | Asm | 按 day+busNo 分组计数 | BR-PRT-LOG-QRY-003 | SUCCESS vs 其余；**仅读** |

---

## 4. QueryService 与领域事件

- **无** QueryService；只读直查 `PrtPrintEventLogRepository`（Print 模块豁免 health B.6.4）。
- **无** 领域事件。

---

## 5. 写库顺序（核心写接口视角）

本 AS **无写接口**；写库顺序 N/A。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

只读；N/A。

### 6.2 并发控制

只读；N/A。

### 6.3 重试与容错

| 项 | 承诺 | 代码位置 | 关联步骤 |
| -- | ---- | -------- | -------- |
| 查询失败 | LOG-101 用户可重试 | PrintLogAppService | §3.2 |

### 6.4 数据脱敏

列表不展示 printData 全文（库本无存）。

### 6.5 审计日志

本页只读；不写审计。

### 6.5 性能（日汇总）

| 项 | 承诺 | 代码位置 | 关联步骤 |
| -- | ---- | -------- | -------- |
| 日汇总窗口 | 内部分页上限 10000 行 | queryDailySummary | §3.3-3 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| ---- | ---- | ------ | ------ |
| 租户过滤 | 集成 | 建议 | P0 |
| traceId 列展示 | 集成 | 建议 | P0 |
| 日汇总 SUCCESS/FAILED 计数 | 集成 | 建议 | P1 |

### 7.2 非功能测试

无租户 LOG-001。

### 7.3 查询结果一致性

与 confirm 写入后手动刷新列表一致（无推送）。

### 7.4 集成测试

`Print-log/index.vue` 列与 DTO 对齐。

### 7.5 测试缺口跟踪

大数据量日汇总性能未做 SQL GROUP BY（AS 约定内存聚合）。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.0 | 2026-06-22 | 首版草案 |
| v1.1 | 2026-06-22 | 路径 A 升格；移除 Gap；traceId DTO |

---
