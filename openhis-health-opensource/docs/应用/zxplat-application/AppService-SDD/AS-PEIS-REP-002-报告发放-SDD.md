# AS-PEIS-REP-002 报告发放 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-REP-002-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-REP-002-报告发放.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/report/grant` |
| **边界** | 发放登记/签收；团体批量打印；邮寄状态（P3 可选） |
| **编排权威** | AS-PEIS-REP-002 §2、§4 |
| **方法权威** | DM-PEIS-005、DM-CLN-006 |
| **实现阶段口径** | **目标态全量契约**（P1；REP-07 P3） |

### 术语口径

| 项 | 约定 |
|----|------|
| **可发放** | DiagnosticReport.status = final（BR-REP2-01） |
| **团检打印** | 按 Encounter 批量 cliPrint（BR-REP2-02） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/report/grant/
├── PeisReportGrantAppService.java
├── request/
│   ├── SearchPendingGrantRequest.java
│   ├── RegisterGrantRequest.java
│   ├── SignGrantRequest.java
│   └── BatchGroupPrintRequest.java
├── response/
│   ├── SearchPendingGrantResponse.java
│   └── BatchGroupPrintResponse.java
├── dto/
│   └── GrantRowDto.java
└── mapper/
    ├── PeisReportGrantMapper.java
    └── PeisReportGrantMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisReportGrantDomainService` | 领域 | registerGrant / signGrant / markPrinted |
| `PeisReportGrantQueryService` | 查询 | REP-05 列表 |
| `DiagnosticReportQueryService` | 查询 | final 校验 |
| `PeisEncounterProgressQuery` | 查询 | 团检 Encounter 列表 |
| `ReportRender旧命名（已废止） | Extension | REP-06 渲染 |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| PeisReportGrantDomainService | `registerGrant(RegisterGrantCommand)` | REP-05 |
| PeisReportGrantDomainService | `signGrant(SignGrantCommand)` | REP-05 |
| PeisReportGrantDomainService | `markPrinted(MarkGrantPrintedCommand)` | REP-06 |
| PeisReportGrantQueryService | `searchPendingGrant(GrantSearchQuery, Pageable)` | REP-05 |
| PeisReportGrantQueryService | `getByDiagnosticReportId(Long)` | REP-05 |
| DiagnosticReportQueryService | `getByEncounterId(Long)` | REP-05/06 Pre |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `PeisGrantStatus` | `com.zxplat.plugin.peis.domain.enums.PeisGrantStatus` | DM-PEIS-005 §2 `grant_status_enum` | pending/printed/signed/mailed；目标态待生成 |
| `grant_status_enum` | `RegisterGrantRequest.grantStatusCode` | DM-PEIS-005 §2 | 发放态 |
| DR final | `DiagnosticReport.status` | DM-CLN-006 | BR-REP2-01 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP**：`PeisReportGrantAppService` → `peisReportGrant`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-PEIS-REP-05 | `searchPendingGrant` | 待发放列表 | `POST /api/app/peisReportGrant/searchPendingGrant` |
| IF-PEIS-REP-05 | `registerGrant` | 发放登记 | `POST /api/app/peisReportGrant/registerGrant` |
| IF-PEIS-REP-05 | `signGrant` | 签收 | `POST /api/app/peisReportGrant/signGrant` |
| IF-PEIS-REP-06 | `batchGroupPrint` | 团体报告打印 | `POST /api/app/peisReportGrant/batchGroupPrint` |
| IF-PEIS-REP-07 | `markMailed` | 报告邮寄 | `POST /api/app/peisReportGrant/markMailed` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数 | 不再调用 |
| **Pre** | DR final、grant 状态 | 抛业务异常 |
| **Cmd** | PeisReportGrantDomainService | 事务回滚 |
| **Asm** | Mapper / ReportRenderPort | — |

---

### 3.2 IF-PEIS-REP-05 `searchPendingGrant`（待发放列表）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `orgId` | `Long` | 是 | |
| `grantStatusCode` | `String` | 否 | 默认 pending |
| `dateFrom` | `LocalDate` | 否 | |
| `dateTo` | `LocalDate` | 否 | |
| `pageNo` | `Integer` | 是 | |
| `pageSize` | `Integer` | 是 | |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `rows` | `List<GrantRowDto>` | |
| `total` | `Long` | |

#### GrantRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `diagnosticReportId` | `Long` | |
| `encounterId` | `Long` | |
| `busNo` | `String` | |
| `patientName` | `String` | |
| `grantStatusCode` | `String` | |
| `reportFinalTime` | `Instant` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | orgId 非空 | |
| 2 | Pre | `PeisReportGrantQueryService#searchPendingGrant(GrantSearchQuery, Pageable)` | **仅读路径** |
| 3 | Asm | `PeisReportGrantMapper#toSearchPendingGrantResponse` | |

---

### 3.3 IF-PEIS-REP-05 `registerGrant`（发放登记）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `diagnosticReportId` | `Long` | 是 | |
| `operatorId` | `Long` | 是 | |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `REPORT_NOT_FINAL` | DR 非 final |
| `GRANT_ALREADY_REGISTERED` | 重复登记 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | diagnosticReportId 非空 | |
| 2 | Pre | `DiagnosticReportQueryService#getById(diagnosticReportId)` | status=final |
| 3 | Pre | `PeisReportGrantQueryService#getByDiagnosticReportId(drId)` | 无 pending 重复 |
| 4 | Cmd | `PeisReportGrantDomainService#registerGrant(RegisterGrantCommand)` | **写库点**：peis_report_grant_ext |
| 5 | Asm | AuditLog | |

---

### 3.4 IF-PEIS-REP-05 `signGrant`（签收）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `diagnosticReportId` | `Long` | 是 | |
| `recipientName` | `String` | 否 | |
| `operatorId` | `Long` | 是 | |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `GRANT_NOT_PENDING` | 未登记或已签收 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisReportGrantQueryService#getByDiagnosticReportId` | status=pending/printed |
| 2 | Cmd | `PeisReportGrantDomainService#signGrant(SignGrantCommand)` | **写库点**：→ signed |

---

### 3.5 IF-PEIS-REP-06 `batchGroupPrint`（团体报告打印）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `groupBatchId` | `Long` | 是 | booking 批次 |
| `encounterIds` | `List<Long>` | 否 | 空则整批 |
| `operatorId` | `Long` | 是 | |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `successCount` | `Integer` | |
| `failedEncounterIds` | `List<Long>` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisEncounterProgressQuery#searchPrintableReports` | groupBatchId 过滤 |
| 2 | Pre | 逐条 `DiagnosticReportQueryService#getByEncounterId` | final |
| 3 | Asm | `ReportRenderPort#renderPeisReport | 批量渲染 |
| 4 | Cmd | `PeisReportGrantDomainService#markPrinted` | **写库点**：逐 DR |

---

### 3.6 IF-PEIS-REP-07 `markMailed`（报告邮寄，P3）

复用 `signGrant` 编排序列 1～2；Cmd 改为 `grant_status_enum → mailed`。

---

## 4. QueryService 与领域事件

- REP-05 列表走 `PeisReportGrantQueryService`；DR final 态变更后读侧 eventual refresh（DiagnosticReport 事件 P2）。

---

## 5. 写库顺序

| IF | Cmd 序号 | 领域方法 | 表 |
|----|----------|----------|-----|
| registerGrant | 4 | `registerGrant` | peis_report_grant_ext INSERT |
| signGrant | 2 | `signGrant` | UPDATE grant_status |
| batchGroupPrint | 4 | `markPrinted` | UPDATE printed |

**事务边界**：registerGrant/signGrant 单 IF 单事务；batchGroupPrint 默认每 DR 独立事务（部分失败可继续）。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- registerGrant：diagnostic_report_id PK 防重复。

### 6.2 并发控制

- signGrant：乐观锁 grant_status。

### 6.3 重试与容错

- batchGroupPrint 单条渲染失败收集 failedEncounterIds。

### 6.4 数据脱敏

- GrantRowDto.patientName 按权限裁剪。

### 6.5 审计日志

- registerGrant/signGrant 记 grant_by、grant_time，关联 §3.3/3.4 Cmd。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 未 final 拒绝 | registerGrant | 功能 | API | P1 |
| 签收闭环 | pending → signed | 功能 | API | P1 |
| 团检批量 | batchGroupPrint | 功能 | 集成 | P1 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 批量 100 份 | 部分失败隔离 | P1 |

### 7.3 查询结果一致性

- searchPendingGrant 与 REP-01 searchPrintable 口径：均需 DR final。

### 7.4 集成测试

- MAS-04 总检完成 → REP-05 可见 → REP-06 打印。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| ReportRenderPort | PrintTemplate（AS-BAS-PRT） 桩 | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；DM-PEIS-005 §5 对齐 |
