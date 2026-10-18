# DM-PACS-006 PacsIntegrationLog 影像集成日志 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PACS-006-PacsIntegrationLog-影像集成日志.md`（minimal canonical v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-pacs/module-pacs-domain`  
> **限界上下文**：检查域（PACS/RIS）· 集成审计  
> **聚合**：PacsIntegrationLog（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PACS-006 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-pacs-domain` |
| 包规则 `com.zxplat.plugin.pacs.domain.integrationlog` | 满足 | `integrationlog` 全小写 |
| 固定技术口径 | 满足 | 见 §9 |
| DM 附录写/读白名单 | 满足 | §5 覆盖 record/listFailedOutbound + search/getById |
| DM §7 不变式 | 满足 | §6 映射 BR-PACSLOG-001～002 |
| DM §5 领域事件 | 满足 | minimal 稿无独立事件类 |

**与 DM 显式差异（采纳依据：源码与 AS-PACS-LOG-001）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 实体字段 | DM §3 仅节选 | §4.2 与 `PacsIntegrationLog` 实体 + 审计基类完整对齐 |
| record 入参 | DM 未列 Command | `RecordPacsIntegrationLogCommand` 字段全集见 §5.1.1 |
| listFailedOutbound | 未过滤 direction | 实现以 `outcome=failure` + `actionCode` 查库；调用方须传入 outbound 场景 action（BR-PACSLOG-002 语义） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **PacsIntegrationLog 聚合** 的 append-only 日志模型、写入与失败补查契约、条件分页读侧，供集成插件与 LOG AppService 对齐。
- **不做** 消息发送与工单状态机；仅落库与查询。
- **单次成稿目标**：正文无「待确认」类措辞。

### 1.2 边界

- **在内**：`pacs_integration_log` 方向、动作、关联实体、结果、报文摘要、错误信息。
- **在外**：Adapter 实际 HTTP/SOAP；PacsWorkItem 状态迁移（DM-PACS-001）。
- **跨域仅端口声明**：`entityType`/`entityId` 多态引用，不在此聚合校验目标行存在性。
- **读写分工**：append 写与失败补查在 `PacsIntegrationLogDomainService`；条件分页与详情在 `PacsIntegrationLogQueryService`（与 DM 附录一致）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-pacs/module-pacs-domain`。

### 2.2 包结构（`com.zxplat.plugin.pacs.domain.integrationlog`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PacsIntegrationLog` |
| `valueobject` | 无 |
| `enums`（共享） | 方向/结果以 String code 存列；常量见 `PacsIntegrationLogDomainService` |
| `command` | `RecordPacsIntegrationLogCommand`、`ListFailedOutboundPacsIntegrationLogCommand` |
| `repository` | `PacsIntegrationLogRepository` |
| `service` | `PacsIntegrationLogDomainService` |
| `queries` | `PacsIntegrationLogQueryService` |
| `queries.model` | `PacsIntegrationLogQueryModel` |
| `share.events` | 无 |

**存储口径**：`directionEnum` / `outcomeEnum` → `String`（存 code：`outbound`/`inbound`，`success`/`failure`）；`actionCode` / `entityType` → `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| 时间 | `createdAt` 等 → `LocalDateTime` |
| `*_enum` | `directionEnum`、`outcomeEnum` → `String` |
| 大文本 | `requestBody`/`responseBody` → `@Column(columnDefinition="text")`；**禁止** `@Lob`（PostgreSQL oid 校验） |
| 软删 | `isDeleted`；日志默认仅追加，软删用于运维治理 |
| 仓储 | `JpaSpecificationExecutor` 支撑 search |
| 分页 | search 默认 `createdAt DESC` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `pacs_integration_log` | `PacsIntegrationLog` | 聚合根 · 审计日志 |

### 4.2 聚合根 `PacsIntegrationLog`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `directionEnum` | `String` | 是 | `outbound` / `inbound`（BR-PACSLOG-001） |
| `actionCode` | `String` | 是 | 如 sendApply、reportFinalize、reportPath、retry |
| `entityType` | `String` | 否 | 如 PacsWorkItem |
| `entityId` | `Long` | 否 | 关联业务主键 |
| `outcomeEnum` | `String` | 是 | `success` / `failure` |
| `requestBody` | `String` | 否 | 请求摘要或 Mock 报文 |
| `responseBody` | `String` | 否 | 响应摘要 |
| `errorMessage` | `String` | 否 | 失败原因；max 512 |
| `tenantId` | `Long` | 是 | 租户 |
| `isDeleted` | `Boolean` | 系统 | 软删 |
| `createdBy` | `Long` | 系统 | 操作人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |
| `deletedBy` | `Long` | 系统 | 软删人 |
| `deletedAt` | `LocalDateTime` | 系统 | 软删时间 |

**不变式方法**：`assertValidDirection` / `assertValidOutcome`

**推荐 code 常量（与 DomainService 一致）**：

| 常量名 | code 值 | 用途 |
| ------ | ------- | ---- |
| `DIRECTION_OUTBOUND` | `outbound` | 外发集成 |
| `DIRECTION_INBOUND` | `inbound` | 入站回调 |
| `OUTCOME_SUCCESS` | `success` | 调用成功 |
| `OUTCOME_FAILURE` | `failure` | 调用失败 |
| `ACTION_SEND_APPLY` | `sendApply` | 发送检查申请 |
| `ACTION_REPORT_FINALIZE` | `reportFinalize` | 报告定稿同步 |
| `ACTION_REPORT_PATH` | `reportPath` | 报告路径回写 |
| `ACTION_RETRY` | `retry` | 人工/任务补发 |

### 4.3 子实体

无。本聚合为单表。

### 4.4 子实体

无其他子实体。

### 4.5 仓储接口

```java
public interface PacsIntegrationLogRepository
    extends JpaRepository<PacsIntegrationLog, Long>, JpaSpecificationExecutor<PacsIntegrationLog> {

    List<PacsIntegrationLog> findByOutcomeEnumAndActionCodeOrderByCreatedAtDesc(
        String outcomeEnum, String actionCode);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `PacsIntegrationLogQueryModel`（对应 `search` / `getById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `pacs_integration_log.id` | |
| `directionEnum` | `String` | `pacs_integration_log.direction_enum` | |
| `actionCode` | `String` | `pacs_integration_log.action_code` | |
| `entityType` | `String` | `pacs_integration_log.entity_type` | |
| `entityId` | `Long` | `pacs_integration_log.entity_id` | |
| `outcomeEnum` | `String` | `pacs_integration_log.outcome_enum` | |
| `requestBody` | `String` | `pacs_integration_log.request_body` | |
| `responseBody` | `String` | `pacs_integration_log.response_body` | |
| `errorMessage` | `String` | `pacs_integration_log.error_message` | |
| `createdAt` | `LocalDateTime` | `pacs_integration_log.created_at` | |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PacsIntegrationLogDomainService`

#### 5.1.1 `record(RecordPacsIntegrationLogCommand cmd)` → `PacsIntegrationLog`

**命令文件**：`RecordPacsIntegrationLogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `directionEnum` | `String` | 必填；outbound/inbound |
| `actionCode` | `String` | 必填 |
| `entityType` | `String` | 可选 |
| `entityId` | `Long` | 可选 |
| `outcomeEnum` | `String` | 必填；success/failure |
| `requestBody` | `String` | 可选 |
| `responseBody` | `String` | 可选 |
| `errorMessage` | `String` | 可选 |
| `tenantId` | `Long` | 必填；写实体 tenantId |
| `operatorId` | `Long` | 必填；createdBy/updatedBy |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertValidDirection` + `assertValidOutcome` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 无前置存在性校验 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PACSLOG-001 |

**方法契约摘要**：前置：direction/action/outcome/tenant 合法（BR-PACSLOG-001）；持久化：insert 新行；失败：非法枚举抛 `INVALID_ARGUMENT`；无事件；**注释锚点**：校验枚举 → new 实体 → save。

---

#### 5.1.2 `listFailedOutbound(ListFailedOutboundPacsIntegrationLogCommand cmd)` → `List<PacsIntegrationLog>`

**命令文件**：`ListFailedOutboundPacsIntegrationLogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `actionCode` | `String` | 必填；补发场景动作码 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByOutcomeEnumAndActionCodeOrderByCreatedAtDesc(failure, actionCode)` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PACSLOG-002 |

**方法契约摘要**：前置：actionCode 非空；持久化：只读；返回 failure 集；调用方在 outbound 集成场景使用（BR-PACSLOG-002）；**注释锚点**：校验 action → repository 列表。

---

### 5.2 读侧 — `PacsIntegrationLogQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false` + 租户过滤；分页在数据库侧统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `search` | `directionEnum`、`actionCode`、`outcomeEnum`、`entityType`、`entityId`、`createdFrom`、`createdTo`（均可空）、`pageable` | `Page<PacsIntegrationLogQueryModel>` | 默认 `createdAt DESC` | TC-PACSLOG-R01 |
| `getById` | `Long id` | `PacsIntegrationLogQueryModel` | findById + notDeleted | TC-PACSLOG-R02 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-PACSLOG-001** | `record` 校验 directionEnum/outcomeEnum 为合法 code；非法拒绝 |
| **BR-PACSLOG-002** | `listFailedOutbound` 固定 outcome=failure；App 补发仅查 outbound 失败（direction 由写入侧保证） |

---

## 7. 事件契约与触发规则（DM §5）

无。minimal 稿未定义独立领域事件类；集成结果以日志行持久化为准。

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 日志以追加为主；软删仅运维场景，默认查询过滤 `isDeleted=false`。
- 不提供业务「编辑日志正文」写方法；纠错通过追加新行或运维软删。

---

## 9. 框架优先适配规则

| 项 | 规则 |
| ---- | ---- |
| 成码门禁 | 遵循 `@代码生成通用规范`；Repository 禁手写 JPQL |
| 基类 | `PacsIntegrationLog extends BaseAuditedEntity` |
| 租户 | `SpecificationUtils.tenantFilter()` 与 record 命令 `tenantId` 双轨一致 |
| 文本列 | PostgreSQL `text`；实体 `columnDefinition = "text"` |
| 事务 | 写日志随 AppService/Adapter 同一 UoW；失败回滚时不应出现半条日志 |
| PHI | 报文摘要由 Adapter 脱敏后再写入 `requestBody`/`responseBody`（应用层责任） |

---

## 10. 生成与验收清单

- [ ] 实体 §4.2 与 Flyway `pacs_integration_log` 列一致
- [ ] `RecordPacsIntegrationLogCommand` 字段与 §5.1.1 一致
- [ ] `ListFailedOutboundPacsIntegrationLogCommand` 与 §5.1.2 一致
- [ ] `record` 非法 direction/outcome 抛业务异常（TC-PACSLOG-001）
- [ ] `listFailedOutbound` 仅 failure + 指定 actionCode（TC-PACSLOG-002）
- [ ] `search` 各可选条件 AND 组合 + 时间闭区间 + 分页 total 正确（TC-PACSLOG-R01）
- [ ] `getById` 软删/不存在错误码（TC-PACSLOG-R02）
- [ ] Repository 派生方法无冗余 `@Query`

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-PACS-006-PacsIntegrationLog-影像集成日志.md`（minimal canonical）
- 本 SDD 文件
- 目标模块：`module-pacs-domain`
- Skills：`@代码生成通用规范` + `@领域层代码生成提示词模板`
- 关联 AS：`AS-PACS-LOG-001`（读侧入参与 search 对齐）

### 11.2 DoD

- `module-pacs-domain` 编译通过；JPA 扫描含 `integrationlog` 包。
- `record` 插入后 `id` 非空；审计字段 populated。
- `listFailedOutbound` 与 `findByOutcomeEnumAndActionCodeOrderByCreatedAtDesc` 签名一致。
- `search` / `getById` 返回 `PacsIntegrationLogQueryModel` 字段与 §4.6.1 一致。
- 枚举非法输入单测可复现。

### 11.3 失败处理

- 补发列表口径与 BR-PACSLOG-002 冲突 → 回改 SDD/DM，禁止放宽 outcome 过滤。
- Adapter 未传 tenantId → 在 App 层补齐后再调 `record`，禁止领域层默认租户猜测。

---

## 定稿检查清单（可打勾）

- [x] 金骨架章节齐全
- [x] `#### 5.1.1`～`5.1.2` 逐方法块
- [x] §4.6 两读方法字段表
- [x] §6 两条 BR
- [x] 无待确认措辞

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
