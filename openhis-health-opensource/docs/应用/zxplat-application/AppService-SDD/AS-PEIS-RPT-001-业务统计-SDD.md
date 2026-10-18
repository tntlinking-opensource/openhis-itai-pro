# AS-PEIS-RPT-001 业务统计 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-RPT-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-RPT-001-业务统计.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/statistics` |
| **边界** | 科室/医生/总检工作量；阳性/疾病统计；历年结果对比 |
| **编排权威** | AS-PEIS-RPT-001 §2 |
| **方法权威** | DM-PEIS-008、DM-CLN-005/006 |
| **实现阶段口径** | **目标态全量契约**（P2） |
| **排除** | 财务营收（归 health） |

### 术语口径

| 项 | 约定 |
|----|------|
| **进度态** | 走 `PeisEncounterProgressQuery`（BR-RPT-01） |
| **只读** | 全部 IF 无 Cmd 写库 |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/statistics/
├── PeisStatisticsAppService.java
├── request/
│   ├── WorkloadStatisticsRequest.java
│   ├── DiseaseStatisticsRequest.java
│   └── HistoryComparisonRequest.java
├── response/
│   ├── WorkloadStatisticsResponse.java
│   ├── DiseaseStatisticsResponse.java
│   └── HistoryComparisonResponse.java
├── dto/
│   ├── WorkloadRowDto.java
│   └── DiseaseStatRowDto.java
└── mapper/
    ├── PeisStatisticsMapper.java
    └── PeisStatisticsMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisStatisticsQueryService` | 查询 | 工作量/阳性聚合 |
| `PeisEncounterProgressQuery` | 查询 | 进度态过滤 |
| `ObservationQueryService` | 查询 | 历年对比 |

### 2.2 领域方法白名单

| 服务 | 方法 | 用于 IF |
|------|------|---------|
| PeisStatisticsQueryService | `aggregateDeptWorkload(WorkloadQuery)` | RPT-01 |
| PeisStatisticsQueryService | `aggregateDoctorWorkload(WorkloadQuery)` | RPT-01 |
| PeisStatisticsQueryService | `aggregateMasterReviewWorkload(WorkloadQuery)` | RPT-01 |
| PeisStatisticsQueryService | `aggregateDiseaseStats(DiseaseStatQuery)` | RPT-02 |
| ObservationQueryService | `compareHistory(HistoryComparisonQuery)` | RPT-03 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `progressLabel` | `WorkloadRowDto.progressLabel` | DM-PEIS-008 §6.2 | 展示态 |
| OD.category | `Observation.categoryCode` | DM-CLN-005 §4.1 | dept_conclusion |
| DR.status final | `DiagnosticReport.status` | DM-CLN-006 | 统计口径 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisStatisticsAppService` → `peisStatistics`

| IF | 应用服务方法 | 中文方法名 | HTTP |
|----|-------------|-----------|------|
| IF-PEIS-RPT-01 | `getWorkloadStatistics` | 工作量统计 | `POST /api/app/peisStatistics/getWorkloadStatistics` |
| IF-PEIS-RPT-02 | `getDiseaseStatistics` | 阳性疾病统计 | `POST /api/app/peisStatistics/getDiseaseStatistics` |
| IF-PEIS-RPT-03 | `getHistoryComparison` | 历年结果对比 | `POST /api/app/peisStatistics/getHistoryComparison` |

---

### 3.2 IF-PEIS-RPT-01 `getWorkloadStatistics`（工作量统计）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `dateFrom` | `LocalDate` | 是 |
| `dateTo` | `LocalDate` | 是 |
| `dimensionCode` | `String` | 是 | dept/doctor/master |

#### WorkloadRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `dimensionId` | `Long` | 科室/医生 ID |
| `dimensionName` | `String` | |
| `encounterCount` | `Integer` | |
| `finishedCount` | `Integer` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | dateFrom ≤ dateTo | |
| 2 | Pre | 若 dimension=dept → `PeisStatisticsQueryService#aggregateDeptWorkload`；若 doctor → `#aggregateDoctorWorkload`；若 master → `#aggregateMasterReviewWorkload` | **仅读路径** |
| 3 | Asm | Mapper | |

---

### 3.3 IF-PEIS-RPT-02 `getDiseaseStatistics`（阳性疾病统计）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `dateFrom` | `LocalDate` | 是 |
| `dateTo` | `LocalDate` | 是 |

#### DiseaseStatRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `conditionCode` | `String` | |
| `conditionDisplay` | `String` | |
| `caseCount` | `Integer` | |
| `ratePercent` | `BigDecimal` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisStatisticsQueryService#aggregateDiseaseStats(DiseaseStatQuery)` | **仅读路径** |
| 2 | Asm | Mapper | |

---

### 3.4 IF-PEIS-RPT-03 `getHistoryComparison`（历年结果对比）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `patientId` | `Long` | 是 |
| `observationDefinitionId` | `Long` | 是 |
| `yearCount` | `Integer` | 否 | 默认 3 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | patientId 非空 | |
| 2 | Pre | `ObservationQueryService#compareHistory(HistoryComparisonQuery)` | **仅读路径** |
| 3 | Asm | Mapper | |

---

## 4. QueryService 与领域事件

- 统计只读；写侧事件（MAS 完成）后统计 Query  eventual 一致（只读副本或定时刷新 P2）。

---

## 5. 写库顺序

**无写库**（全部 IF 仅读路径）。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- 只读接口天然幂等。

### 6.2 并发控制

- 无。

### 6.3 重试与容错

- 大区间查询超时 → 分页或异步导出（P2）。

### 6.4 数据脱敏

- 统计结果按角色聚合，不暴露个人明细（RPT-01/02）。

### 6.5 审计日志

- 导出类操作记 AuditLog（P2）。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 工作量 | 三维度 | 功能 | API | P2 |
| 阳性率 | 分母口径 | 功能 | API | P2 |
| 历年对比 | 同 OD 跨 year | 功能 | API | P2 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 90 天区间 | 响应 < 3s | P2 |

### 7.3 查询结果一致性

- finishedCount 与 DM-PEIS-008 progress 一致。

### 7.4 集成测试

- 造数 MAS 完成 → RPT-01 master 维度 +1。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| PeisStatisticsQueryService | AS Gap GAP-RPT-01 待实现 | P2 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版 |
