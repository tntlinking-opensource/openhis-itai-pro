# AS-LIS-SAM-001 标本管理 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-SAM-001-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-SAM-001-标本管理.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 标本接收/上机/退样/复查；标本转运日志（SDD 到 DM-LIS-009）；标本 TAT |
| **编排权威** | AS-LIS-SAM-001 §2、§4 |
| **方法权威** | DM-LIS-001 §6（LisWorkItemDomainService）、DM-LIS-009 §4（LisTransferLogDomainService） |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **标本接收** | LisWorkItem statusEnum：collected → received |
| **上机** | LisWorkItem statusEnum：received → processing |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/sample/
├── LisSampleAppService.java
├── command/
│   ├── ReceiveWorkItemCommand.java
│   ├── StartTestingCommand.java
│   ├── ReturnSampleCommand.java
│   ├── CreateTransferLogCommand.java
│   └── ConfirmTransferLogCommand.java
├── request/
│   └── SampleSearchRequest.java
├── response/
│   └── SampleSearchResponse.java
└── assembler/
    └── SampleAssembler.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `LisWorkItemDomainService` | 领域 | receive / startTesting / returnSample |
| `LisTransferLogDomainService` | 领域 | createTransferLog / confirmTransferLog |
| `LisWorkItemQueryService` | 查询 | searchWorkItems / getDetail |
| `TransferLogQueryService` | 查询 | searchTransferLogs |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| LisWorkItem | `receive(ReceiveWorkItemCommand)` | SAM-01 |
| LisWorkItem | `startTesting(StartTestingCommand)` | SAM-03 |
| LisWorkItem | `returnSample(ReturnSampleCommand)` | SAM-05 |
| LisTransferLog | `createTransferLog(CreateTransferLogCommand)` | SAM-TR-01 |
| LisTransferLog | `confirmTransferLog(ConfirmTransferLogCommand)` | SAM-TR-02 |
| LisWorkItemQueryService | `searchWorkItems(WorkItemQuery, Pageable)` | SAM-01 |
| TransferLogQueryService | `searchTransferLogs(TransferLogQuery, Pageable)` | SAM-TR-01 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `LisWorkItemStatus` | `com.zxplat.domain.share.enums.LisWorkItemStatus` | DM-LIS-001 §3.3 | collected / received / processing |
| `TransferStatus` | `com.zxplat.domain.share.enums.TransferStatus` | DM-LIS-009 §3.2 | 在途 / 已确认 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisSampleAppService` → `lisSample`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-SAM-01 | `searchAndReceive` | 标本列表+接收 | `POST /api/app/lisSample/searchAndReceive` |
| IF-SAM-02 | `batchReceive` | 批量接收 | `POST /api/app/lisSample/batchReceive` |
| IF-SAM-03 | `startTesting` | 上机 | `POST /api/app/lisSample/startTesting` |
| IF-SAM-05 | `returnSample` | 退样 | `POST /api/app/lisSample/returnSample` |
| IF-SAM-TR-01 | `createTransfer` | 创建转运 | `POST /api/app/lisSample/createTransfer` |
| IF-SAM-TR-02 | `confirmTransfer` | 确认转运 | `POST /api/app/lisSample/confirmTransfer` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：加载快照、BR 校验 | 抛业务异常 |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | DTO 装配 / 事件发布 | — |

---

### 3.2 IF-SAM-01 `searchAndReceive`（接收）

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | workItemId 非空 |
| 2 | **Pre** | `LisWorkItemQueryService#getDetail(workItemId)` | 加载工单快照 |
| 3 | **Pre** | BR-SAM-01：statusEnum = collected | 仅已采样标本可接收 |
| 4 | **Cmd** | `LisWorkItemDomainService#receive(ReceiveWorkItemCommand)` | **写库点**：collected → received + receiveTime |
| 5 | **Asm** | 事件 | `LisWorkItemReceivedEvent` |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `STATUS_INVALID` | statusEnum ≠ collected |
| `SAMPLE_EXPIRED` | TAT 超时 |

---

### 3.3 IF-SAM-03 `startTesting`（上机）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | `LisWorkItemQueryService#getDetail(workItemId)` | BR-SAM-02：statusEnum = received |
| 3 | **Cmd** | `LisWorkItemDomainService#startTesting(StartTestingCommand)` | **写库点**：received → processing + linkDevice |

---

### 3.4 IF-SAM-TR-01 `createTransfer`（转运记录）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | 校验 workItemId 有效性 | |
| 3 | **Cmd** | `LisTransferLogDomainService#createTransferLog(CreateTransferLogCommand)` | **写库点**：create transfer_log；BR-TR-01：校验源科室 != 目标科室 |
| 4 | **Asm** | 事件 | `LisTransferCreatedEvent` |

---

### 3.5 IF-SAM-TR-02 `confirmTransfer`（确认转运）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | BR-TR-02：转运日志 statusEnum = 在途 | |
| 3 | **Cmd** | `LisTransferLogDomainService#confirmTransferLog(ConfirmTransferLogCommand)` | **写库点**：在途 → 已确认 |
| 4 | **Asm** | 事件 | `LisTransferConfirmedEvent` |

---

### 3.6 IF-SAM-05 `returnSample`（退样）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | 状态校验 | |
| 3 | **Cmd** | `LisWorkItemDomainService#returnSample(ReturnSampleCommand)` | **写库点**：cancel → entered-in-error |

---

## 4. QueryService 与领域事件

- **领域事件**：`LisWorkItemReceivedEvent` → TAT 启动；`LisTransferCreatedEvent` / `LisTransferConfirmedEvent` → 转运状态刷新。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| SAM-01 | `LisWorkItemDomainService#receive` | lis_work_item（statusEnum, receiveTime） |
| SAM-03 | `LisWorkItemDomainService#startTesting` | lis_work_item（statusEnum, deviceId） |
| SAM-TR-01 | `LisTransferLogDomainService#createTransferLog` | lis_transfer_log |
| SAM-TR-02 | `LisTransferLogDomainService#confirmTransferLog` | lis_transfer_log（statusEnum） |
| SAM-05 | `LisWorkItemDomainService#returnSample` | lis_work_item（statusEnum） |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

无特殊。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | collected → received | P0 |
| 正向 | received → processing（上机） | P0 |
| 正向 | 创建转运 → 对方确认 → 接收入库 | P0 |
| 异常 | 接收非 collected → 拒绝 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
