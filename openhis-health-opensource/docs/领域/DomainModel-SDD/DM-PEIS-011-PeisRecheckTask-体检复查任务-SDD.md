# DM-PEIS-011 PeisRecheckTask 体检复查任务 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-011-PeisRecheckTask-体检复查任务.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-peis/module-peis-domain`  
> **限界上下文**：health · peis · 复查任务  
> **聚合**：PeisRecheckTask（单表）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PEIS-011 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-peis-domain` |
| 包 `com.zxplat.plugin.peis.domain.recheck` | 满足 | |
| DM 附录写方法 ×4 | 满足 | §5.1.1～5.1.4 |
| DM 附录读方法 ×3 | 满足 | §5.2 + §4.6 |
| DM §7 BR ×2 | 满足 | §6 |
| DM §5 事件 | 满足 | 无独立事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 承载 Peis 04 方案 A：`peis_recheck_task` 表表达复查任务（requested → in-progress → completed）。
- 写侧一方法一 Command（create/start/complete）；`hasOpenRecheck` 供创建前校验（BR-RECHK-001）。
- 读侧分页检索与 open 任务判断。

### 1.2 边界

- **在内**：任务 CRUD 与状态迁态。
- **在外**：不自动重开 SR/检查申请；DR 正文在 CLN 聚合。
- **跨域**：`diagnosticReportId` 仅存外键；完成前 AS 校验 DR 归属。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-peis/module-peis-domain`。

### 2.2 包结构（`com.zxplat.plugin.peis.domain.recheck`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisRecheckTask` |
| `command` | `CreateRecheckTaskCommand`、`StartRecheckTaskCommand`、`CompleteRecheckTaskCommand` |
| `repository` | `PeisRecheckTaskRepository` |
| `service` | `PeisRecheckDomainService` |
| `queries` | `PeisRecheckQueryService` |
| `queries.model` | `RecheckTaskRowModel` |
| `queries.query` | `RecheckTaskQuery`（分页条件） |

**存储口径**：`statusEnum` → `String`（存 `PeisRecheckTaskStatusConstants` code）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| `status_enum` | Java `statusEnum`，`String` |
| 时间 | `completedAt` → `LocalDateTime` |
| 软删 | `isDeleted` |
| 事务/事件 | 不声明 `@Transactional`；无领域事件类 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `peis_recheck_task` | `PeisRecheckTask` | 复查任务 |

### 4.2 聚合根 `PeisRecheckTask`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 否 | 机构 |
| `encounterId` | `Long` | 是 | 原体检就诊 |
| `patientId` | `Long` | 否 | 患者 |
| `statusEnum` | `String` | 是 | requested/in-progress/completed |
| `reason` | `String` | 是 | 复查原因 |
| `diagnosticReportId` | `Long` | 条件 | 完成时必填 |
| `completedAt` | `LocalDateTime` | 条件 | 完成时间 |
| `completedBy` | `Long` | 条件 | 完成人 |
| `isDeleted` | `Boolean` | 系统 | |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式方法**：状态迁态在 Service 内校验 `statusEnum`（BR-RECHK-002）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `PeisRecheckTaskRepository extends JpaRepository<PeisRecheckTask, Long>`
- `existsByEncounterIdAndStatusEnumInAndIsDeletedFalse(Long encounterId, Collection<String> statuses)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `RecheckTaskRowModel`（`searchRecheckTasks`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `taskId` | `Long` | task.id | |
| `encounterId` | `Long` | task.encounter_id | |
| `patientId` | `Long` | task.patient_id | |
| `statusCode` | `String` | task.status_enum | |
| `reason` | `String` | task.reason | |
| `diagnosticReportId` | `Long` | task.diagnostic_report_id | |
| `createdAt` | `LocalDateTime` | task.created_at | |

#### 4.6.2 `getById`（`PeisRecheckQueryService.getById`）

同 §4.6.1 字段；未找到返回 `Optional.empty()`。

#### 4.6.3 `hasOpenRecheckTask`（`PeisRecheckQueryService.hasOpenRecheckTask`）

出参为 `boolean`，无 QueryModel；语义同写侧 `hasOpenRecheck`（open = requested 或 in-progress）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PeisRecheckDomainService`

#### 5.1.1 `createRecheckTask(CreateRecheckTaskCommand cmd)` → `PeisRecheckTask`

**命令文件**：`CreateRecheckTaskCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `patientId` | `Long` | 可选 |
| `orgId` | `Long` | 可选 |
| `tenantId` | `Long` | 可选（可补当前租户） |
| `reason` | `String` | 必填，非空 trim |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 初始 `statusEnum=REQUESTED` |
| `statusTransitionMethod` | create → requested |
| `repositoryChecks` | `hasOpenRecheck(encounterId)` 须 false |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-RECHK-W01 |

**方法契约摘要**：前置：reason/encounter/operator 非空且无 open 任务；持久化：insert；失败：`PEIS_RECHECK_ALREADY_OPEN`；**注释锚点**：校验 open → 组装实体 → save。

---

#### 5.1.2 `startTask(StartRecheckTaskCommand cmd)` → `PeisRecheckTask`

**命令文件**：`StartRecheckTaskCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `taskId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 当前须 REQUESTED |
| `statusTransitionMethod` | requested → in-progress |
| `repositoryChecks` | `requireTask(taskId)` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-RECHK-W02 |

**方法契约摘要**：前置：状态为 requested；持久化：update status；失败：非法状态/不存在；**注释锚点**：load → 迁态 → save。

---

#### 5.1.3 `completeTask(CompleteRecheckTaskCommand cmd)` → `PeisRecheckTask`

**命令文件**：`CompleteRecheckTaskCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `taskId` | `Long` | 必填 |
| `diagnosticReportId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 非 completed |
| `statusTransitionMethod` | → completed |
| `repositoryChecks` | `requireTask` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-RECHK-W03 |

**方法契约摘要**：前置：task/report/operator 非空；持久化：写 DR id、completedAt/By；失败：已完成任务；**注释锚点**：load → 迁态 → 写完成字段 → save。

---

#### 5.1.4 `hasOpenRecheck(Long encounterId)` → `boolean`

**命令文件**：N/A

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 可空则 false |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `existsByEncounterIdAndStatusEnumInAndIsDeletedFalse` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-RECHK-W04 |

**方法契约摘要**：前置：无；持久化：只读；失败：无；**注释锚点**：判空 → exists 查询。

---

### 5.2 读侧 — `PeisRecheckQueryService`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchRecheckTasks` | `RecheckTaskQuery` + `Pageable` | `Page<RecheckTaskRowModel>` | DB 分页 + 软删 | TC-PEIS-RECHK-R01 |
| `getById` | `Long taskId` | `Optional<RecheckTaskRowModel>` | id + 软删 | TC-PEIS-RECHK-R02 |
| `hasOpenRecheckTask` | `Long encounterId` | `boolean` | 同 §5.1.4 | TC-PEIS-RECHK-R03 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-RECHK-001** | `createRecheckTask` 前 `hasOpenRecheck`；Query 侧 `hasOpenRecheckTask` 同口径 |
| **BR-RECHK-002** | `startTask` 仅 requested；`completeTask` 拒绝重复完成 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |

（无独立领域事件。）

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 查询默认 `isDeleted=false`；写侧 `requireTask` 过滤已删。
- 无物理 DELETE 公开 API。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；分页默认 `createdAt DESC`。
- 错误码使用 `ErrorCodeConstants.PEIS_RECHECK_*`。

---

## 10. 生成与验收清单

- [ ] 三 Command 与 §5.1 字段一致
- [ ] 状态机单测 TC-PEIS-RECHK-W01～W03
- [ ] open 任务重复创建拒绝
- [ ] `RecheckTaskRowModel` 与 §4.6.1 一致

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-PEIS-011、本 SDD、表 `peis_recheck_task`

### 11.2 DoD

- 编译通过；写读方法数与 DM 附录一致

### 11.3 失败处理

- DR FK 依赖未就绪 → AS 层校验先行

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 4
- [ ] §5.2 读方法 = 3
- [ ] BR-RECHK-001/002 有测试绑定

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
| **总分** | **100** | **≥95，可进入代码会话** |

---

**本 SDD 正文结束。**
