# AS-LIS-RES-01 仪器结果接收 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-RES-01-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-RES-01-仪器结果接收.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 接收 C++ 程序回传的仪器结果报文 → 对照解析 → 创建/更新 Observation(preliminary) |
| **编排权威** | AS-LIS-RES-01 §2、§4 |
| **方法权威** | DM-LIS-002 §6 BR（ObservationDomainService 由 SDD 推导） |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **幂等** | 同一次测量重复发送 → 更新值（查找已有 Observation 按 workItemId+code） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/result/
├── LisResultIngressAppService.java
├── command/
│   └── IngestObservationCommand.java
├── request/
│   └── IngestResultRequest.java
├── response/
│   └── IngestResultResponse.java
├── assembler/
│   └── ResultIngressAssembler.java
└── support/
    └── InstrumentCodeMapper.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `ObservationDomainService` | 领域 | recordObservation / updateObservation |
| `ObservationQueryService` | 查询 | findByWorkItemAndCode（幂等查重） |
| `LisWorkItemQueryService` | 查询 | barcode 反查 workItem |
| `InstrumentCodeMapper` | support | 仪器码 → LIS 码映射 |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| Observation | `recordObservation(RecordObservationCommand)` | RES-01-A/B |
| ObservationQueryService | `searchByWorkItemAndCode(Long workItemId, String code)` | RES-01-A/B |
| LisWorkItemQueryService | `findByBarcode(String barcode)` | RES-01-A/B |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `ObsStatus` | `com.zxplat.domain.share.enums.ObsStatus` | DM-LIS-002 §4 | preliminary / final / amended / cancelled |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisResultIngressAppService` → `lisResultIngress`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-RES-01-A | `ingestSingle` | 单个测量值上报 | `POST /api/app/lisResultIngress/ingestSingle` |
| IF-RES-01-B | `ingestBatch` | 批量测量值上报 | `POST /api/app/lisResultIngress/ingestBatch` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 参数门禁 | 不再调用 |
| **Pre** | 写前只读：barcode 反查、码映射、幂等查重 | 抛业务异常 |
| **Cmd** | 领域写：ObservationDomainService | 事务回滚 |
| **Asm** | 事件发布 | — |

---

### 3.2 IF-RES-01-A `ingestSingle`（单个测量值接收）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `barcode` | `String` | 是 | 条码 |
| `instrumentCode` | `String` | 是 | 仪器码 |
| `value` | `BigDecimal` | 否 | 定量值 |
| `valueText` | `String` | 否 | 定性值 |
| `unit` | `String` | 否 | 单位 |
| `instrumentSerialNo` | `String` | 否 | 仪器流水号 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `observationId` | `Long` | 创建/更新后的 Observation ID |
| `isNew` | `Boolean` | 是否新建 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `WORKITEM_NOT_FOUND` | 条码无对应工单 |
| `CODE_MAPPING_MISSING` | 仪器码无 LIS 映射 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | **Gate** | 参数门禁 | barcode 非空 | |
| 2 | **Pre** | `LisWorkItemQueryService#findByBarcode(barcode)` | barcode | BR-RES-01：barcode 不存在 → 拒绝 |
| 3 | **Pre** | `InstrumentCodeMapper#map(instrumentCode)` | instrumentCode | BR-RES-02：映射丢失 → 拒绝 |
| 4 | **Pre** | `ObservationQueryService#searchByWorkItemAndCode(workItemId, lisCode)` | workItemId + code | BR-RES-03：幂等查重；已有 → 走 update 路径 |
| 5 | **Cmd** | `ObservationDomainService#recordObservation(RecordObservationCommand)` | 若新建 | **写库点**：BR-RES-04：linkStatusEnum → testing |
| 6 | **Asm** | 事件发布 | observationId | `ObservationPreliminaryRecordedEvent`（仅新建时） |

---

### 3.3 IF-RES-01-B `ingestBatch`（批量上报）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 批量上限门禁（≤ 200 条） | |
| 2 | **Pre** | 逐条执行 §3.2 序号 2~4 | 批量预处理：barcode反查 + 码映射 |
| 3 | **Cmd** | `ObservationDomainService#batchRecord(List<RecordObservationCommand>)` | **写库点**：批量 upsert |
| 4 | **Asm** | 批量事件发布 | 仅新建的 observation 发布事件 |

---

## 4. QueryService 与领域事件

- **查询语义**：LisWorkItemQueryService 按 barcode 反查 workItemId；ObservationQueryService 按 (workItemId, code) 幂等查重。
- **领域事件**：`ObservationPreliminaryRecordedEvent` 在新建 Observation 后由 AS 层发布，触发 AUD 待审核列表刷新。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| RES-01-A | `ObservationDomainService#recordObservation` | lis_observation 单行 insert/update |
| RES-01-B | `ObservationDomainService#batchRecord` | lis_observation 批量 upsert |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

### 6.1 吞吐

- IF-RES-01-B：单次最多 200 条测量值。

### 6.2 幂等

- 同一次测量重复 → update 不重复创建（BR-RES-03）。

### 6.3 超时

- 5s 超时返回。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 新测量值 → Observation=preliminary | P0 |
| 幂等 | 同 workItemId+code 重复 → update | P0 |
| 异常 | barcode 不存在 → reject | P0 |
| 异常 | 仪器码无映射 → reject | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
