# AS-LIS-SMP-001 采样工作台 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-SMP-001-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-SMP-001-采样工作台.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 待采样列表；条码生成/绑定；采样签收（单/批）；拒收/重采；采样退单 |
| **编排权威** | AS-LIS-SMP-001 §2、§4 |
| **方法权威** | DM-LIS-001 §6（LisWorkItemDomainService） |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **待采样** | `statusEnum = available` 的 LisWorkItem |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/sampling/
├── LisSamplingAppService.java
├── command/
│   ├── CollectWorkItemCommand.java
│   ├── RejectWorkItemCommand.java
│   └── RecollectWorkItemCommand.java
├── request/
│   ├── SamplingSearchRequest.java
│   └── BarcodeGenerateRequest.java
├── response/
│   └── SamplingSearchResponse.java
├── assembler/
│   └── SamplingAssembler.java
└── support/
    └── BarcodeGenerator.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `LisWorkItemDomainService` | 领域 | collect / reject / cancelWorkItem |
| `LisWorkItemQueryService` | 查询 | searchWorkItems / getDetail |
| `LisConfigQueryService` | 查询 | barcode 规则 / 收费策略 |
| `BarcodeGenerator` | support | 条码生成 |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| LisWorkItem | `createWorkItem(CreateLisWorkItemCommand)` | SMP-06（重采） |
| LisWorkItem | `collect(CollectWorkItemCommand)` | SMP-03/03B |
| LisWorkItem | `reject(RejectWorkItemCommand)` | SMP-05 |
| LisWorkItem | `cancelWorkItem(CancelWorkItemCommand)` | SMP-07 |
| LisWorkItemQueryService | `searchWorkItems(WorkItemQuery, Pageable)` | SMP-01 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `LisWorkItemStatus` | `com.zxplat.domain.share.enums.LisWorkItemStatus` | DM-LIS-001 §3.3 | available / collected / received / processing / entered-in-error / unsatisfactory |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisSamplingAppService` → `lisSampling`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-SMP-01 | `searchPendingSampling` | 查看待采样列表 | `POST /api/app/lisSampling/searchPendingSampling` |
| IF-SMP-02 | `generateBarcode` | 生成/绑定条码 | `POST /api/app/lisSampling/generateBarcode` |
| IF-SMP-03 | `collect` | 采样签收 | `POST /api/app/lisSampling/collect` |
| IF-SMP-03B | `batchCollect` | 批量签收 | `POST /api/app/lisSampling/batchCollect` |
| IF-SMP-05 | `reject` | 拒收 | `POST /api/app/lisSampling/reject` |
| IF-SMP-06 | `recollect` | 重采 | `POST /api/app/lisSampling/recollect` |
| IF-SMP-07 | `returnSample` | 采样退单 | `POST /api/app/lisSampling/returnSample` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：加载快照、状态与 BR 校验 | 抛业务异常 |
| **Cmd** | 领域写：LisWorkItemDomainService | 事务回滚 |
| **Asm** | DTO 装配 / 事件发布 | — |

---

### 3.2 IF-SMP-03 `collect`（采样签收）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `workItemId` | `Long` | 是 | 工单 ID |
| `operatorId` | `Long` | 是 | 操作人 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `STATUS_INVALID` | 状态不允许签收 |
| `CHARGE_NOT_ALLOWED` | 未收费不允许采样 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | workItemId 非空 |
| 2 | **Pre** | `LisWorkItemQueryService#getDetail(workItemId)` | 加载工单快照 |
| 3 | **Pre** | 状态校验 | BR-SMP-01：statusEnum = available |
| 4 | **Pre** | `LisConfigQueryService#getByKey(tenantId, "sampling.allowWithoutCharge")` | BR-SMP-05：未收费允许采样校验 |
| 5 | **Cmd** | `LisWorkItemDomainService#collect(CollectWorkItemCommand)` | **写库点**：available → collected + collectionTime |
| 6 | **Asm** | 事件发布 | `LisWorkItemCollectedEvent` |

---

### 3.3 IF-SMP-05 `reject`（拒收）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | `LisWorkItemQueryService#getDetail(workItemId)` | BR-SMP-03：校验 statusEnum = available |
| 3 | **Cmd** | `LisWorkItemDomainService#reject(RejectWorkItemCommand)` | **写库点**：available → unsatisfactory |

---

### 3.4 IF-SMP-06 `recollect`（重采）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | 加载原工单快照 | 获取 patientId / encounterId / orderLinks |
| 3 | **Cmd** | `LisWorkItemDomainService#createWorkItem(CreateLisWorkItemCommand)` | **写库点**：新建 LisWorkItem（新 barcode）；BR-SMP-03：不复用原 ID |

---

### 3.5 IF-SMP-07 `returnSample`（采样退单）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | 状态校验 | |
| 3 | **Cmd** | `LisWorkItemDomainService#cancelWorkItem(CancelWorkItemCommand)` | **写库点**：collected → entered-in-error |

---

### 3.6 IF-SMP-01 `searchPendingSampling`（待采样列表）

| 阶段 | 调用 | 说明 |
|------|------|------|
| **Pre** | `LisWorkItemQueryService#searchWorkItems(WorkItemQuery, Pageable)` | statusEnum=available，按科室/日期过滤；**仅读路径** |

---

## 4. QueryService 与领域事件

- **领域事件**：`LisWorkItemCollectedEvent` 在 collect 成功后由 AS 层发布，触发 TAT 监控。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| SMP-03 | `LisWorkItemDomainService#collect` | lis_work_item（statusEnum + collectionTime） |
| SMP-05 | `LisWorkItemDomainService#reject` | lis_work_item（statusEnum → unsatisfactory） |
| SMP-06 | `LisWorkItemDomainService#createWorkItem` | lis_work_item + lis_work_item_order（新建） |
| SMP-07 | `LisWorkItemDomainService#cancelWorkItem` | lis_work_item（statusEnum → entered-in-error） |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

### 6.1 批量上限

- IF-SMP-03B：`lis_config.tat.batchSize`。

### 6.2 并发

- 同工单并发签收 → 乐观锁。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 签收 available 工单 → status=collected | P0 |
| 异常 | 签收 collected 工单 → 拒绝 | P0 |
| 拒收 | available → unsatisfactory；重采 → 新工单 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
