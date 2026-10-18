# DM-PRT-002 PrintLog 打印审计日志 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PRT-002-PrintLog-打印审计日志.md`  
> **目标工程**：`zxplat/whale-zxplat-domain`  
> **限界上下文**：Print 本地扩展  
> **聚合**：PrintLog（代码实体 `PrtPrintEventLog`）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PRT-002 v1.4 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `print-domain` |
| 包规则 | 满足 | `com.zxplat.domain.custom` |
| 固定技术口径 | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 写 1 方法；读由 App Specification |
| DM 第 7 章 BR | 满足 | §6 |
| DM 第 5 章事件 | 满足 | §7（0 事件） |

**与 DM 显式差异**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 写侧入口 | DM §6.1 列 `PrintJobAppService.confirmPrint` | 领域层提供 `PrtPrintLogWriter#appendLog`；App 组装 Command 后调用（避免 App 直写实体散落） |
| trace_id UK | DM §7 BR-PRT-LOG-001 写「全局唯一」 | **决策纪要**：`trace_id` **非 UK**，允许多次 confirm；SDD 与 Flyway 索引一致 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 `prt_print_event_log` append-only 实体与仓储；写路径经 `PrtPrintLogWriter` 单方法落库。
- **traceId** 由 prepare 生成、confirm 带回；**同一 traceId 允许多行**。
- 读路径由 `PrintLogAppService` + `JpaSpecificationExecutor` 只读（**不在**本聚合 QueryService）。

### 1.2 边界

- **在内**：实体字段全集、append 写契约、status 枚举、contentHash 规则。
- **在外**：preparePrint（不写本表）、日志分页 UI、operator 解析（`PrintOperatorResolver` 在 application）。
- **跨域仅端口声明**：逻辑引用 `bus_no` + `template_version`；无 FK。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain` / `health-application`（读 Specification 在 application）。

### 2.2 包结构（`com.zxplat.domain.custom`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PrtPrintEventLog` |
| `command` | `AppendPrintLogCommand` |
| `repository` | `PrtPrintEventLogRepository` |
| `service` | `PrtPrintLogWriter`（append-only 写入口） |
| `constants` | `PrintLogStatus`：`SUCCESS`、`FAILED` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| `status` | `String` 大写 code：`SUCCESS` / `FAILED` |
| JSONB | `bizRefJson`：`String` + `@JdbcTypeCode(SqlTypes.JSON)` |
| 软删 | 仅框架层；**无业务 update** |
| 异常 | 校验失败 `ValidationException`（App 层）；Writer 仅组装 |
| 事务 | Writer 不声明 `@Transactional`；随 confirmPrint UoW |
| 仓储 | **禁止** `@Query` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `prt_print_event_log` | `PrtPrintEventLog` | append-only 聚合根 |

### 4.2 聚合根 `PrtPrintEventLog`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | |
| `organizationId` | `Long` | 否 | confirm 时会话院点 |
| `traceId` | `String` | 是 | prepare 返回；**非 UK** |
| `busNo` | `String` | 是 | 以 prepare 为准；**BR-PRT-LOG-002** |
| `bindingCode` | `String` | 否 | 仅 prepare 用 bindingCode 时 |
| `templateVersion` | `Integer` | 否 | prepare resolved version |
| `status` | `String` | 是 | `SUCCESS` / `FAILED`；**BR-PRT-LOG-003** |
| `printerName` | `String` | 否 | |
| `copies` | `Integer` | 否 | |
| `contentHash` | `String` | 否 | printData SHA-256 hex |
| `errorCode` | `String` | 否 | FAILED |
| `errorMessage` | `String` | 否 | FAILED |
| `bizRefJson` | `String` | 否 | JSONB |
| `clientType` | `String` | 否 | 默认 `openhis-win` |
| `operatorId` | `Long` | 否 | |
| `operatorName` | `String` | 否 | ICurrentUser 显示名冗余 |
| `createdAt` 等 / `isDeleted` | — | — | 仅创建；无业务 update |

**废弃列（清库重跑后不存在）**：`clientJobId`、`eventType`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

`PrtPrintEventLogRepository extends JpaRepository<PrtPrintEventLog, Long>, JpaSpecificationExecutor<PrtPrintEventLog>`

读侧过滤：`tenant_id`、`is_deleted=false`、可选 busNo/status/日期（App Specification）。

### 4.6 查询模型（`queries.model`）

读侧无领域 QueryService；App 层直接映射 `PrtPrintEventLog` → DTO。

#### 4.6.1 `queryPage` 出参（App 层 DTO，非 domain QueryModel）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `id` | |
| `traceId` | `String` | `trace_id` | 替代废弃 clientJobId |
| `busNo` | `String` | `bus_no` | |
| `bindingCode` | `String` | `binding_code` | 可空 |
| `templateVersion` | `Integer` | `template_version` | 可空 |
| `status` | `String` | `status` | SUCCESS / FAILED |
| `printerName` | `String` | `printer_name` | |
| `copies` | `Integer` | `copies` | |
| `operatorId` | `Long` | `operator_id` | |
| `operatorName` | `String` | `operator_name` | |
| `errorCode` | `String` | `error_code` | |
| `errorMessage` | `String` | `error_message` | |
| `createdAt` | `LocalDateTime` | `created_at` | |

#### 4.6.2 confirmPrint Request → Command 字段映射（App 协作）

| Request 字段 | Command 字段 | 说明 |
| ------------ | ------------ | ---- |
| `traceId` | `traceId` | Gate 非空 |
| `busNo` | `busNo` | 与 prepare 一致 |
| `bindingCode` | `bindingCode` | 可选 |
| `templateVersion` | `templateVersion` | |
| `status` | `status` | 大写 |
| `printer` | `printerName` | 字段名映射 |
| `copies` | `copies` | |
| `printData` | `contentHash` | SHA-256，非原文 |
| `bizRef` | `bizRefJson` | JSON 字符串 |
| `clientType` | `clientType` | 默认 openhis-win |
| `errorCode` / `errorMessage` | 同名 | FAILED 时 |
| — | `operatorId` / `operatorName` | PrintOperatorResolver |
| — | `organizationId` | PrintOperatorResolver 院点 |
| — | `tenantId` | TenantContext |

#### 4.6.3 日汇总内存模型（App 层，非 domain）

| 字段名（Java） | 类型 | 说明 |
| -------------- | ---- | ---- |
| `day` | `LocalDate` | createdAt 取日 |
| `busNo` | `String` | 分组键 |
| `successCount` | `Integer` | status=SUCCESS 计数 |
| `failedCount` | `Integer` | 其余 status 计数 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PrtPrintLogWriter`

#### 5.1.1 `appendLog(AppendPrintLogCommand cmd)` → `PrtPrintEventLog`

**命令文件**：`AppendPrintLogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `tenantId` | `Long` | 非空 |
| `organizationId` | `Long` | 可空 |
| `traceId` | `String` | trim 非空 |
| `busNo` | `String` | trim 非空 |
| `bindingCode` | `String` | 可空 |
| `templateVersion` | `Integer` | 可空 |
| `status` | `String` | `SUCCESS` / `FAILED` 大写 |
| `printerName` | `String` | 可空 |
| `copies` | `Integer` | 可空 |
| `contentHash` | `String` | 可空 |
| `errorCode` | `String` | 可空 |
| `errorMessage` | `String` | 可空 |
| `bizRefJson` | `String` | 可空 JSON |
| `clientType` | `String` | 可空；默认 `openhis-win` |
| `operatorId` | `Long` | 可空 |
| `operatorName` | `String` | 可空 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | —（append-only） |
| `statusTransitionMethod` | 无（仅 insert） |
| `repositoryChecks` | 无唯一性校验（traceId 可重复） |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRT-LOG-APPEND-001 |

**方法契约摘要**：前置字段已由 App Gate 校验；持久化 **insert 一行**（**写库点 1**）；失败向上抛；不发事件。**注释锚点**：组装实体 → repository.save。

---

### 5.2 读侧 — 无领域 QueryService

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| — | — | — | 读由 `PrintLogAppService` + `Specification` | TC-PRT-LOG-QRY-001 |

**读方法注释锚点（统一）**：构建 Specification（租户+软删+筛选）→ 仓储分页查询 → 组装 App DTO（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PRT-LOG-001 | App Gate：`traceId` trim 非空（**非** DB UK） |
| BR-PRT-LOG-002 | `busNo` 非空；与 prepare 一致校验在 App |
| BR-PRT-LOG-003 | status 仅 SUCCESS/FAILED |
| BR-PRT-LOG-004 | 无 update；仅 append |
| BR-PRT-LOG-005 | App 计算 contentHash（规范化 JSON SHA-256） |

---

## 7. 事件契约与触发规则（DM §5）

首期 **不发** 域事件；下表为空集（与 DM §5 一致）。

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |

**发布时机**：无。

---

## 8. 删除与可见性契约

- append-only：**禁止**业务 update/delete API。
- 软删仅框架层；查询默认 `is_deleted=false`。
- 租户隔离：读侧必须 `tenant_id = 当前租户`。

---

## 9. 框架优先适配规则

- 实体继承 `BaseAuditedEntity`。
- Repository 禁 `@Query`；读侧用 `JpaSpecificationExecutor` + `Specification`（在 application）。
- `PrtPrintLogWriter` 不注入 `ICurrentUser`；operator 字段由 App 填入 Command。
- `contentHash` 计算在 **application**（confirmPrint Asm）：Object printData → 规范化 JSON 字符串 → SHA-256 hex。
- `bizRefJson` 序列化：Object bizRef → JSON String 写入 Command。
- `clientType` 默认 `openhis-win`；App Gate 在 Command 组装前补默认。
- `bindingCode` 仅 prepare 使用 bindingCode 时由 App 写入；直传 busNo 时保持 NULL。
- `organizationId` 来自 `PrintOperatorResolver#findOrganizationIdOrNull()`。
- append-only：**禁止** 在 Writer 或 App 提供 update/delete 方法。
- 遵循项目级成码门禁（Job AppService JavaDoc 注释锚点）。

---

## 10. 生成与验收清单

- [ ] `PrtPrintEventLog` 与 DM §3.1、Flyway V1 新结构对齐
- [ ] 移除 `clientJobId`、`eventType` 字段与旧索引
- [ ] `AppendPrintLogCommand` 字段全集与 §5.1.1 一致
- [ ] `PrtPrintLogWriter#appendLog` 单写入口
- [ ] `idx_prt_log_trace` 为普通索引（**非** UK）
- [ ] confirm 写入 operatorName（ICurrentUser 显示名，可 NULL）
- [ ] SUCCESS/FAILED 大写落库
- [ ] 同一 traceId 两次 confirm 产生两行
- [ ] `PrintJobAppService` 不直注 `ICurrentUser`
- [ ] `PrintLogAppService` queryPage/queryDailySummary 与 Log AS-SDD 一致
- [ ] Flyway 清库重跑无 client_job_id 列

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 本 SDD + DM-PRT-002 v1.4 + Flyway 清库重跑。

### 11.2 DoD

- `print-domain` 编译通过；Writer append 可测。
- `PrintJobAppService` 经 `PrtPrintLogWriter` 写库（**禁止** App 直注 `EntityManager`）。
- `PrintLogAppService` 只读 Specification 分页；DTO 字段与 §4.6.1 一致。
- Flyway 清库重跑后无 `client_job_id` 列。
- 规范合规摘要：注释锚点、P0 rg、B.6.4 不适用（Print 独立模块）。

### 11.3 失败处理

- 仍使用 clientJobId → 对齐 §4.2 与 Flyway 改名 trace_id。
- confirm 缺 templateVersion → 对照 Job AS-SDD §3.3 Asm 补全 Command。
- 读侧仍映射 clientJobId → 对照 Log AS-SDD §3.2 DTO 改 traceId。

---

## 定稿检查清单（可打勾）

- [x] §1～§11 金骨架齐全
- [x] §5.1.1 写方法 1 节
- [x] §4.2 字段与 DM §3.1 对齐
- [x] §7 0 事件与 DM §5 对齐
- [x] §4.6.1 读 DTO 字段表
- [x] 无 Gap / 待补充措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 23 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **97，可进入代码会话** |

---

**本 SDD 正文结束。**
