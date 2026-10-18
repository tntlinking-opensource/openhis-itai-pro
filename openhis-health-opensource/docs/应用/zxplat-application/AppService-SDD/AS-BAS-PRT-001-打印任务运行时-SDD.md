# 基础资料 · 打印任务运行时 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-BAS-PRT-001-打印任务运行时.md`（v1.0）  
> **编码**：AS-BAS-PRT-001-SDD · **版本**：v1.1 · **日期**：2026-06-22

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | `health-application` · `PrintJobAppService` |
| **边界** | IF-BAS-PRT-31 `preparePrint`、IF-BAS-PRT-32 `confirmPrint` |
| **编排权威** | `AS-BAS-PRT-001` §2、§4 |
| **方法权威** | `DM-PRT-001-SDD` `findPublished` / `findPublishedByScenarioCode`；`DM-PRT-002-SDD` `appendLog` |
| **实现阶段口径** | **目标态全量契约**（traceId、bindingCode、全字段 confirm）；旧 clientJobId 废弃 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | 一律 `POST /api/app/printJob/{methodName}` |
| **权限** | 菜单 `Print:print` / `Print:preview`；**本期不写 @RequirePermission** |
| **事务** | AppService **不** `@Transactional`；confirm Cmd 单写库点 |
| **操作人** | **禁止** App 直注 `ICurrentUser`；用 `PrintOperatorResolver` |
| **trace** | 无 pending 表；confirm 请求自带 busNo+templateVersion 与 prepare 一致校验 |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
health-application/src/main/java/com/openhis/health/application/print/printjob/
  service/PrintJobAppService.java
  request/PreparePrintRequest.java
  request/ConfirmPrintRequest.java
  dto/PreparePrintResultDto.java
  dto/ConfirmPrintResultDto.java
  mapper/PrintJobMapper.java
health-module-support/.../PrintOperatorResolver.java
health-application/.../print/printtemplate/support/PrintJsonSupport.java
```

> 本模块 **无** MapStruct Mapper；DTO 在 App 内手工装配。

### 2.2 依赖注入

| Bean | 用途 | DM 来源 |
| ---- | ---- | ------- |
| `PrtTemplateManager` | findPublished | DM-PRT-001 §6.1 |
| `PrintBindingResolver` | resolve bindingCode | DM-PRT-003 §6.1 |
| `PrtPrintLogWriter` | appendLog | DM-PRT-002 §5.1.1 |
| `PrintOperatorResolver` | operatorId/organizationId | application 支撑 |
| `TenantContext` | require tenantId | zxplat-framework |

### 2.3 领域方法白名单

| 聚合 | 方法 | 用于 IF |
| ---- | ---- | ------- |
| `PrtTemplateQueryService` | `findPublished` / `findPublishedByScenarioCode` | 31 |
| `PrtPrintEventLogDomainService` | `appendLog(AppendPrintLogCommand)` | 32 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| ---- | ------------ | ----------- | ---- |
| 打印日志状态 | `PrintLogStatus.SUCCESS` / `FAILED`（目标态：`com.zxplat.domain.custom.constants.PrintLogStatus`） | DM-PRT-002 §4.1 `status` | `ConfirmPrintRequest.status`；目标态待生成 |
| 客户端类型默认值 | `ConfirmPrintRequest.clientType` | DM-PRT-002 §3.1 `client_type` | 默认 `openhis-win` |
| prepare 入参互斥 | `PreparePrintRequest.scenarioCode` / `busNo`；`bindingCode` 作 scenarioCode 别名 | BR-PRT-JOB-001 | 二选一 |

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编程序列」，按序号从上到下执行；Gate/Pre 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| -- | -------------------- | ---------- | ---- |
| IF-BAS-PRT-31 | `preparePrint` | 打印准备 | `POST /api/app/printJob/preparePrint` |
| IF-BAS-PRT-32 | `confirmPrint` | 打印确认落库 | `POST /api/app/printJob/confirmPrint` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| ---- | ---- | ------ |
| **Gate** | 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 只读：解析、加载模板 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：Writer append | 事务回滚见 §5 |
| **Asm** | DTO 装配 | — |

记法：`PrtTemplateManager#findPublished` 表示注入 Bean 调用。

---

### 3.2 IF-BAS-PRT-31 `preparePrint`（打印准备）

#### Request `PreparePrintRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | String | 二选一 | trimToNull |
| `bindingCode` | String | 二选一 | trimToNull |

#### Response `PreparePrintResultDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `traceId` | String | 服务端 UUID |
| `busNo` | String | resolved |
| `templateVersion` | Integer | published.version |
| `PrintJson` | Object | 版式真源（**DOC-CONFLICT**：模板域例外，非业务 JSON 出参） |
| `samplePrintData` | Object | 可选 |

#### 失败码口径

| 场景 | 异常 |
| ---- | ---- |
| 无租户 | `ValidationException` EX-PRT-JOB-001 |
| 双入参/无入参 | `ValidationException` EX-PRT-JOB-201 |
| 无 published | `BusinessException PRT-404` EX-PRT-JOB-102 |
| binding 无定义 | `BusinessException PRT-404` EX-PRT-JOB-103 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| ---- | ---- | ---- | -------- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | — | 无租户 → JOB-001 |
| 2 | Gate | 入参互斥校验 | busNo/bindingCode | 恰好一个非空 → 否则 JOB-201 |
| 3 | Pre | `PrintBindingResolver#resolve` | bindingCode, tenantId | **若** bindingCode 非空；否则跳至 4 |
| 4 | Pre | — | busNo | **若** 仅 busNo：resolvedBindingCode=null |
| 5 | Pre | `PrtTemplateManager#findPublished` | busNo, tenantId | 无模板 → PRT-404 |
| 6 | Asm | 生成 traceId + 装配 `PreparePrintResultDto` | template 字段 | 解析 PrintJson；**仅读路径** |

---

### 3.3 IF-BAS-PRT-32 `confirmPrint`（打印确认落库）

#### Request `ConfirmPrintRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `traceId` | String | 是 | |
| `busNo` | String | 是 | 与 prepare 一致 |
| `templateVersion` | Integer | 是 | 与 prepare 一致 |
| `bindingCode` | String | 否 | prepare 用过 binding 时传 |
| `status` | String | 是 | SUCCESS / FAILED |
| `printer` | String | 否 | → printerName |
| `copies` | Integer | 否 | |
| `printData` | Object | 否 | contentHash |
| `bizRef` | Object | 否 | → bizRefJson |
| `clientType` | String | 否 | 默认 openhis-win |
| `errorCode` | String | 否 | FAILED |
| `errorMessage` | String | 否 | |

#### Response `ConfirmPrintResultDto`

空对象或 success 标记（与现工程一致）。

#### 失败码口径

| 场景 | 异常 |
| ---- | ---- |
| traceId 空 | `ValidationException` JOB-202 |
| 非法 status | `ValidationException` JOB-203 |
| 无租户 | `ValidationException` JOB-001 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| ---- | ---- | ---- | -------- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | — | JOB-001 |
| 2 | Gate | traceId/status 校验 | trim、大写 status | JOB-202/203 |
| 3 | Asm | 组装 `AppendPrintLogCommand` | 全字段 | operator 来自 `PrintOperatorResolver`；printData→SHA-256 |
| 4 | Cmd | `PrtPrintLogWriter#appendLog(AppendPrintLogCommand)` | cmd | **写库点 1**；append-only |
| 5 | Asm | 返回 `ConfirmPrintResultDto` | — | — |

---

## 4. QueryService 与领域事件

- **无** QueryService 依赖；prepare 经 Manager/Resolver 只读。
- **无** 领域事件发布（DM-PRT-001/002 §5 首期不发）。

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入顺序 | 事务 |
| -- | -------- | -------- | -------- | ------------ | ---- |
| 32 | `confirmPrint` | §3.3-4 | `PrtPrintLogWriter#appendLog` | PrintLog insert | 框架 `@AppService` 单事务；失败回滚 insert |

**失败回滚**：confirm Cmd 失败时 PrintLog 行不保留。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| -- | ---- | ---------------- | -------- |
| traceId 重复 confirm | **允许**多行；不做幂等拦截 | `PrtPrintLogWriter` | §3.3-4 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置 | 关联步骤 |
| -- | ---- | -------- | -------- |
| prepare/confirm | 无分布式锁 | — | — |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置 | 关联步骤 |
| -- | ---- | -------- | -------- |
| confirm 失败 | 前端可重试；可能产生多行 | Job AppService | §3.3 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置 | 关联步骤 |
| -- | ---- | -------- | -------- |
| printData | 仅存 contentHash，不存全文 | confirm Asm | §3.3-3 |

### 6.5 审计日志

| 项 | 承诺 | 代码位置 | 关联步骤 |
| -- | ---- | -------- | -------- |
| 操作人 | operatorId/operatorName 冗余落库 | PrintOperatorResolver | §3.3-3 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| ---- | ---- | ------ | ------ |
| prepare busNo 成功返回 traceId | 集成 | 建议 | P0 |
| prepare bindingCode resolve | 集成 | 建议 | P0 |
| confirm SUCCESS 落库全字段 | 集成 | 建议 | P0 |
| 入参互斥 JOB-201 | 单元 | 是 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| ---- | ---- | ------ | ------ |
| 无租户 Gate | 单元 | 是 | P0 |

### 7.3 查询结果一致性

prepare 与 confirm busNo/version 不一致时 App 可选校验 PRT-422（本期请求冗余自带一致值）。

### 7.4 集成测试

Vue `use-print-job.ts`：busNo/bindingCode + traceId 闭环。

### 7.5 测试缺口跟踪

Host 渲染不在本 SDD 范围。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.0 | 2026-06-22 | 首版草案 |
| v1.1 | 2026-06-22 | 路径 A 升格：§1～§8、编程序列、移除 Gap；`PrtPrintLogWriter` |

---
