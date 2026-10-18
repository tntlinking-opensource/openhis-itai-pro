# DM-PEIS-008 PeisEncounterProgressQuery 体检进度查询 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-008-PeisEncounterProgressQuery-体检进度查询.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · 进度只读聚合  
> **聚合**：无持久化表；`PeisEncounterProgressQuery` 查询服务

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PEIS-008 |
| 包 `com.zxplat.plugin.peis.domain.encounterprogress` | 满足 | |
| DM §6.1 ×1 信号写 | 满足 | signalProgressChanged |
| DM §6.2 ×5 读 | 满足 | |
| DM §5 ×1 事件 | 满足 | |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 联合 Encounter(PE)+ext+SR ext+CLN+DR 计算展示进度（BR-PRG-01）。
- **禁止**扩展 Encounter 专用 status 列。
- SQL 层分页（BR-PRG-02）。

### 1.2 边界

- **在内**：Query 实现 + 进度变更信号事件。
- **在外**：各表写仍在对应聚合 DomainService。

---

## 2. 工程落点与包结构

### 2.2 包结构（`com.zxplat.plugin.peis.domain.encounterprogress`）

| 相对包 | 内容 |
| ------ | ---- |
| `command` | `SignalProgressChangedCommand` |
| `queries` | `PeisEncounterProgressQuery`（写信号+读合一实现类） |
| `queries.model` | `DeptDeskRowModel`、`MasterReviewRowModel`、`PrintableReportRowModel`、`GroupProgressRowModel`、`PeisProgressSummaryModel` |
| `queries.query` | `DeptDeskQuery`、`MasterReviewQuery`、`ReportPrintQuery`、`GroupProgressQuery` |

无 `entity` / `repository`。

---

## 3. 类型 / 字段命名 / 注解规则

QueryModel 字段 `String`/`Long`/`LocalDateTime`；`progressCode` → `String`。

---

## 4. 实体 / 仓储 / 查询契约

### 4.1 表与实体映射

无本聚合表。读路径 join：`adm_encounter`、`peis_encounter_ext`、`peis_service_request_ext`、`wkf_service_request`、`cln_observation`、`cln_diagnostic_report_index`（按需）。

### 4.2 聚合根

无。

### 4.3 子实体

无。

### 4.5 仓储接口

无。使用 `EntityManager` Criteria 或受控 `@Query` 仅在 **queries** 实现类（项目允许 QueryService 内 SQL 时以成码规范为准）。

### 4.6 查询模型

#### 4.6.1 `DeptDeskRowModel`（`searchPendingDeptDesk`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `encounterId` | `Long` | encounter | |
| `patientId` | `Long` | encounter | |
| `patientName` | `String` | patient | |
| `busNo` | `String` | ext | |
| `serviceRequestId` | `Long` | sr | 待检项目 |
| `activityDisplay` | `String` | ad | |
| `execStatusEnum` | `String` | ext | |
| `lisDispatchEnum` | `String` | ext | |

#### 4.6.2 `MasterReviewRowModel`（`searchPendingMasterReview`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `encounterId` | `Long` | | |
| `patientName` | `String` | | |
| `busNo` | `String` | | |
| `deptCompleteRatio` | `String` | 计算 | 分科完成度 |
| `checkInTime` | `LocalDateTime` | encounter | |

#### 4.6.3 `PrintableReportRowModel`（`searchPrintableReports`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `encounterId` | `Long` | | |
| `diagnosticReportId` | `Long` | dr | |
| `patientName` | `String` | | |
| `busNo` | `String` | | |
| `finalTime` | `LocalDateTime` | dr | |

#### 4.6.4 `GroupProgressRowModel`（`searchGroupProgress`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `groupBatchId` | `Long` | ext | |
| `encounterId` | `Long` | | |
| `patientName` | `String` | | |
| `progressCode` | `String` | 计算 | |
| `masterFinalFlag` | `Boolean` | dr | |

#### 4.6.5 `PeisProgressSummaryModel`（`getProgressSummary`）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `encounterId` | `Long` | |
| `progressCode` | `String` | 待检/待总检/可打印等 |
| `totalServiceRequestCount` | `Integer` | |
| `checkedCount` | `Integer` | |
| `abandonedCount` | `Integer` | |
| `masterReviewFinal` | `Boolean` | |

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `PeisEncounterProgressQuery`（信号，无表写）

#### 5.1.1 `signalProgressChanged(SignalProgressChangedCommand cmd)` → `void`

**命令文件**：`SignalProgressChangedCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `progressCode` | `String` | 可选快照 |
| `triggerSource` | `String` | DES/MAS/INT |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | encounter 存在（只读） |
| `eventPublishAnchor` | `PeisEncounterProgressChangedEvent` |
| `unitTestCaseId` | TC-PEIS-PRG-001 |

**方法契约摘要**：不写库；发布 beforeCommit 事件供缓存/队列；**注释锚点**：校验 encounter → 计算 progressCode（可选）→ publish。

---

### 5.2 读侧 — `PeisEncounterProgressQuery`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `searchPendingDeptDesk` | `DeptDeskQuery, Pageable` | `Page<DeptDeskRowModel>` | 科室+待检 SR | TC-PEIS-PRG-R01 |
| `searchPendingMasterReview` | `MasterReviewQuery, Pageable` | `Page<MasterReviewRowModel>` | BR-MAS-01 | TC-PEIS-PRG-R02 |
| `searchPrintableReports` | `ReportPrintQuery, Pageable` | `Page<PrintableReportRowModel>` | BR-PRG-03 | TC-PEIS-PRG-R03 |
| `searchGroupProgress` | `GroupProgressQuery, Pageable` | `Page<GroupProgressRowModel>` | groupBatchId | TC-PEIS-PRG-R04 |
| `getProgressSummary` | `Long encounterId` | `PeisProgressSummaryModel` | 单 Encounter | TC-PEIS-PRG-R05 |

**读方法注释锚点（统一）**：构建 Criteria/SQL（含软删）→ 分页 count+content → 组装 Model。

**`searchPendingMasterReview` 口径**：分科 SR ext 均为 checked 或 abandoned（BR-MAS-01）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-PRG-01** | 进度码计算在 Query 内，不改 Encounter |
| **BR-PRG-02** | 分页 total 与 filter 同 SQL |
| **BR-PRG-03** | printable 过滤 final+finished |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 载荷 | 触发 |
| ---- | ------ | ---- | ---- |
| `PeisEncounterProgressChangedEvent` | `com.zxplat.domain.share.events.custom.peisencounterprogress` | `encounterId, progressCode` | signalProgressChanged |

**发布时机**：**beforeCommit**。

---

## 8. 删除与可见性契约

- 读路径 respect 各源表 isDeleted=false。
- 信号方法不写库。

---

## 9. 框架优先适配规则

- 关闭 GAP-PEIS-MAS-01；DES-01 可改用 `searchPendingDeptDesk`。
- join 性能：院点索引 encounter_id、bus_no。

---

## 10. 生成与验收清单

- [ ] 五读方法 + 信号方法
- [ ] MAS-01 集成测试夹具
- [ ] 分页 total 一致性单测

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-008、DM-PEIS-001/007、Encounter(PE)

### 11.2 DoD

Query 编译；searchPendingMasterReview 单测

### 11.3 失败处理

SQL 过慢 → 索引回 Flyway 文档

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 1
- [ ] §4.6 五个 Model 齐全

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（§1.4 PASS）** |

---

**本 SDD 正文结束。**
