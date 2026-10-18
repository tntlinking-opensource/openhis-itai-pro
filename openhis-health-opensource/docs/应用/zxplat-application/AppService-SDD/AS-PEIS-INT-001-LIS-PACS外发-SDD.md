# AS-PEIS-INT-001 LIS/PACS 外发 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-INT-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-INT-001-LIS-PACS外发.md（v1.1）  
> **术语**：INT-01=本院 WorkItem；金域= [LisKingMed](../../../模块/zxplat-module-lis-kingmed/) — [`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md)

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application/peis/support |
| **类** | `PeisLisPacsDockAppService` |
| **边界** | 按 SR 外发 LIS/PACS；条码子能力；拉取结果；重置标志 |
| **编排权威** | AS-PEIS-INT-001、AS-LIS-INT-001、AS-PACS-INT-001 |
| **实现阶段口径** | **目标态全量契约**（P0） |
| **调用方式** | REG/DES **同进程注入**；可选暴露 dock HTTP |

### 术语口径

| 项 | 约定 |
|----|------|
| **路由** | PROOF→`LisWorkItemDomainService`；TEST→`PacsWorkItemDomainService` |
| **条码** | 读 WorkItem，不写 ext（D6） |
| **投影** | `LisResultProjectionService#projectFromLis`（AS Gap-PEIS-INT-01） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../peis/support/
├── PeisLisPacsDockAppService.java
├── request/
│   ├── DispatchByServiceRequestsRequest.java
│   ├── FetchResultsRequest.java
│   └── ResetDispatchFlagsRequest.java
└── response/
    └── DispatchResultResponse.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `LisWorkItemDomainService` | 领域 | INT-01 LIS |
| `PacsWorkItemDomainService` | 领域 | INT-01 PACS |
| `LisWorkItemQueryService` | 查询 | 条码 |
| `PacsWorkItemQueryService` | 查询 | 检查号 |
| `PeisServiceRequestExtDomainService` | 领域 | dispatch 标志 |
| `ActivityDefinitionQueryService` | 查询 | type_enum 路由 |
| `LisResultProjectionService` | 领域 | INT-03 |

### 2.2 领域方法白名单

| 服务 | 方法 | IF |
|------|------|-----|
| LisWorkItemDomainService | `createWorkItem(CreateLisWorkItemCommand)` | INT-01 |
| PacsWorkItemDomainService | `createWorkItem(CreatePacsWorkItemCommand)` | INT-01 |
| PeisServiceRequestExtDomainService | `updateDispatchFlags(...)` | INT-01/03/04 |
| LisResultProjectionService | `projectFromLis(Long srId)` | INT-03 |
| LisWorkItemQueryService | `findByServiceRequestId(Long)` | INT-01 条码 |
| PacsWorkItemQueryService | `findByServiceRequestId(Long)` | INT-01 条码 |

### 2.3 枚举

| 类别 | 代码锚点 | DM | 说明 |
|------|---------|-----|------|
| PeisDispatchStatus | `PeisDispatchStatus` | DM-PEIS-001 | notSent/sent/resultReceived |
| AD type PROOF/TEST | `ActivityDefinition.type_enum` | DM-WKF-001 | 路由 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| INT-01 | `dispatchByServiceRequests` | 外发+条码 | `POST /api/app/peisLisPacsDock/dispatchByServiceRequests` |
| INT-03 | `fetchResults` | 拉取结果 | `POST /api/app/peisLisPacsDock/fetchResults` |
| INT-04 | `resetDispatchFlags` | 重置标志 | `POST /api/app/peisLisPacsDock/resetDispatchFlags` |

### 3.2 IF-PEIS-INT-01 `dispatchByServiceRequests`

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `serviceRequestIds` | `List<Long>` | 是 |
| `operatorId` | `Long` | 是 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `successIds` | `List<Long>` | |
| `failedItems` | `List<DispatchFailItem>` | code+message |

#### 失败码

| 码 | 场景 |
|----|------|
| `DISPATCH_TARGET_UNKNOWN` | 无法路由 |
| `LIS_PACS_REJECT` | 下游拒绝 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | serviceRequestIds 非空 | |
| 2 | Pre | 逐 SR 加载 AD.type_enum | |
| 3 | Pre | 幂等：ext.dispatch≠sent 或可重试 | |
| 4 | Cmd | 若 PROOF：`LisWorkItemDomainService#createWorkItem(...)` | **写库点 LIS** |
| 5 | Cmd | 若 TEST：`PacsWorkItemDomainService#createWorkItem(...)` | **写库点 PACS**；与 4 **互斥** |
| 6 | Cmd | `PeisServiceRequestExtDomainService#updateDispatchFlags(..., sent)` | |
| 7 | Asm | 组装 barcode/accessionNo 读 WorkItem Query | 子能力 |

### 3.3 IF-PEIS-INT-03 `fetchResults`

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Pre | ext.dispatch=sent |
| 2 | Cmd | `LisResultProjectionService#projectFromLis(srId)` |
| 3 | Cmd | updateDispatchFlags resultReceived |

### 3.4 IF-PEIS-INT-04 `resetDispatchFlags`（重置外发/回写标志，P1）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `serviceRequestIds` | `List<Long>` | 是 | |
| `moduleCode` | `String` | 是 | `LIS` / `PACS` / `BOTH` |
| `operatorId` | `Long` | 是 | |
| `reason` | `String` | 否 | 审计 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `resetCount` | `Integer` | 成功条数 |

#### 失败码

| 码 | 场景 |
|----|------|
| `RESET_NOT_ALLOWED` | resultReceived 无管理员权限 |
| `ALREADY_NOT_SENT` | 已是 notSent |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | serviceRequestIds 非空 | |
| 2 | Pre | 逐 SR 读 ext；resultReceived 须管理员 Gate | BR-PEIS-INT-06 |
| 3 | Cmd | `PeisServiceRequestExtDomainService#updateDispatchFlags(..., notSent)` | **写库点** ext；**不删** WorkItem/CLN |
| 4 | Asm | AuditLog(reason) | BR-PEIS-INT-05 |

---

## 4. QueryService 与领域事件

- INT-03 完成后 DES 读侧刷新 `cln_observation`；INT-04 不删 CLN，仅 ext 回退。

---

## 5. 写库

| IF | 写入 |
|----|------|
| INT-01 | lis/pacs work_item + peis_service_request_ext |
| INT-03 | cln_observation + ext |
| INT-04 | peis_service_request_ext（仅 dispatch 列） |

事务：INT-01 与 ext 更新同事务；投影可最终一致（院点配置）。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- 已 sent 且 WorkItem 存在 → 跳过创建，返回已有条码。

### 6.2 并发控制

- 同 SR 并发 dispatch → ext dispatch 乐观锁。

### 6.3 重试与容错

- 外购 adapter 失败 → failedItems 明细，部分成功。

### 6.4 数据脱敏

- 无 P0。

### 6.5 审计日志

- dispatch 记 operatorId、SR 列表。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:--:|
| LIS 外发 → barcode | 功能 | 集成 | P0 |
| INT-04 reset | ext notSent，WorkItem 保留 | 功能 | API | P1 |
| PACS 外发 → accessionNo | 功能 | 集成 | P0 |
| 投影 source 链 | 功能 | 集成 | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 批量外发 50 SR | 部分失败隔离 | P0 |

### 7.3 查询结果一致性

- 条码与 LIS/PACS WorkItem 一致。

### 7.4 集成测试

- REG-02 登记 → INT-01 → DES-08。

### 7.5 测试缺口跟踪

| 缺口 | LisResultProjectionService | P0 |

---

## 8. 变更历史

| v1.1 | 2026-06-27 | §3.4 INT-04；§4 QueryService；对齐 AS v1.1 |
| v1.0 | 2026-06-27 | 对齐 LIS/PACS INT SDD |
