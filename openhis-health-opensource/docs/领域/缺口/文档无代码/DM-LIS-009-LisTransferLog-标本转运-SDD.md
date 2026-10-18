# DM-LIS-009 LisTransferLog 标本转运 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-009-LisTransferLog-标本转运.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）
> **目标工程：`zxplat``
> **限界上下文**：检验域 · 标本物流
> **聚合**：LisTransferLog（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-009 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.transferlog` | 满足 | `transferlog` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6 领域方法 | 满足 | §5 全覆盖 DM §6 写方法（confirm、addWorkItem） |
| DM §7 业务规则 | 满足 | §6 映射（BR-TRANS-01~03） |
| DM §5 领域事件 | 满足 | §7 覆盖 DM §5 全量事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **LisTransferLog 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞。

### 1.2 边界

- **在内**：`lis_transfer_log` 聚合内状态、字段、不变式、事件发布。
- **在外**：标本转运编排（SAM-001 AppService SAM-TRANS 子模块）。
- **跨域仅端口声明**：对 LisWorkItem 仅声明引用约束（workItemIds）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.lis.transferlog`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `LisTransferLog` |
| `valueobject` | 无 |
| `enums`（共享） | 无新增枚举 |
| `command` | 每个写方法一个 Java 文件（见 §5） |
| `repository` | `LisTransferLogRepository`（仅 `JpaRepository` 接口） |
| `service` | `LisTransferLogDomainService`（直接实现类） |
| `queries` | `LisTransferLogQueryService`（直接实现类） |
| `queries.model` | `LisTransferLogQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.lis.transferlog`（见 §7） |

**存储口径（DM 优先）**：`workItemIds` 为 `String`（存 JSON 数组）；`_flag` 采用 `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `operatorId` |
| 时间 | `transferTime` / `createdAt` → `LocalDateTime`（语义含时分秒精度） |
| JSON | `workItemIds` → `String` + `@Column(columnDefinition = "json")`，值对象层 `List<Long>` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 仓储 | `interface LisTransferLogRepository extends JpaRepository<LisTransferLog, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；默认 `transferTime DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` 已开启的 UoW |
| 事件 | `new XxxEvent(...)` + `DomainEventPublisher.publish`，**beforeCommit** |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_transfer_log` | `LisTransferLog` | 聚合根 · 单表 |

### 4.2 聚合根 `LisTransferLog`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `sourceLocation` | `String` | 是 | 源位置（病区/科室编码） |
| `targetLocation` | `String` | 是 | 目标位置（检验科/专业组） |
| `operatorId` | `Long` | 是 | 操作人 |
| `transferTime` | `LocalDateTime` | 是 | 交接时间 |
| `workItemIds` | `String` (JSON) | 是 | 标本 workItemId 列表（BR-TRANS-03：同批次不重复） |
| `batchNo` | `String` | 是 | 送检批次号，唯一 |
| `verifiedCount` | `Integer` | 否 | 实际接收数（BR-TRANS-01） |
| `expectedCount` | `Integer` | 是 | 期望接收数 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区/科室 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |

**不变式方法**：`addWorkItem(workItemId)` / `confirm(verifiedCount)`

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface LisTransferLogRepository extends JpaRepository<LisTransferLog, Long> {
    Optional<LisTransferLog> findByBatchNoAndIsDeletedFalse(String batchNo);
    List<LisTransferLog> findBySourceLocationAndTargetLocationAndIsDeletedFalse(String source, String target);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `LisTransferLogQueryModel`（对应 `searchByBatchNo`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_transfer_log.id` | |
| `batchNo` | `String` | `lis_transfer_log.batch_no` | |
| `sourceLocation` | `String` | `lis_transfer_log.source_location` | |
| `targetLocation` | `String` | `lis_transfer_log.target_location` | |
| `transferTime` | `LocalDateTime` | `lis_transfer_log.transfer_time` | |
| `expectedCount` | `Integer` | `lis_transfer_log.expected_count` | |
| `verifiedCount` | `Integer` | `lis_transfer_log.verified_count` | |
| `workItemCount` | `Integer` | JSON 数组长度 | |

#### 4.6.2 `LisTransferLogDetailQueryModel`（对应 `getDetail`）

同 §4.2 + `workItemIds` 解析为 `List<Long>`。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `LisTransferLogDomainService`

#### 5.1.1 `create(CreateLisTransferLogCommand cmd)` → `LisTransferLog`

**命令文件**：`CreateLisTransferLogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `sourceLocation` | `String` | 源位置 |
| `targetLocation` | `String` | 目标位置 |
| `operatorId` | `Long` | 操作人 |
| `workItemIds` | `List<Long>` | 标本 ID 列表 |
| `batchNo` | `String` | 送检批次号 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `create` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | batchNo 唯一性；BR-TRANS-03：workItemIds 不重复 |
| `eventPublishAnchor` | `LisTransferLogCreatedEvent`（beforeCommit） |
| `unitTestCaseId` | TC-TRANS-001 |

**方法契约摘要**：前置：batchNo 不重复，workItemIds 去重；持久化：expectedCount = workItemIds.size；失败：重复 batchNo → 拒绝；**注释锚点**：校验 → 创建 → 发布事件。

---

#### 5.1.2 `confirm(ConfirmLisTransferLogCommand cmd)` → `LisTransferLog`

**命令文件**：`ConfirmLisTransferLogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 转运记录 ID |
| `verifiedCount` | `Integer` | 实际接收数 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `confirm(verifiedCount)` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | BR-TRANS-01：verifiedCount ≤ expectedCount |
| `eventPublishAnchor` | `LisTransferConfirmedEvent`（beforeCommit） |
| `unitTestCaseId` | TC-TRANS-002 |

**方法契约摘要**：前置：verifiedCount ≤ expectedCount（BR-TRANS-01）；持久化：verifiedCount 赋值，差量（expectedCount - verifiedCount）记录；失败：verifiedCount > expectedCount → 拒绝；**注释锚点**：校验 → 更新 → 发布事件。

---

#### 5.1.3 `addWorkItem(AddWorkItemToTransferLogCommand cmd)` → `LisTransferLog`

**命令文件**：`AddWorkItemToTransferLogCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 转运记录 ID |
| `workItemId` | `Long` | 追加标本 ID |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `addWorkItem(workItemId)` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | BR-TRANS-03：workItemId 不在 workItemIds 中 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-TRANS-003 |

**方法契约摘要**：前置：workItemId 不在已有列表中；持久化：workItemIds JSON 追加，expectedCount + 1；失败：重复 → 拒绝；**注释锚点**：查重 → 追加 → 更新 expectedCount。

---

### 5.2 读侧 — `LisTransferLogQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchByBatchNo` | `LisTransferLogQuery` | `Page<LisTransferLogQueryModel>` | 分页 + 按 batchNo / sourceLocation / targetLocation / 时间范围过滤 | TC-TRANS-R01 |
| `getDetail` | `Long id` | `LisTransferLogDetailQueryModel` | 按 id 查全量字段 + JSON 解析 | TC-TRANS-R02 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-TRANS-01** | confirm() 前置：`if (verifiedCount > expectedCount) throw BusinessException` |
| **BR-TRANS-02** | 差量（expectedCount - verifiedCount）> 0 时标记 missing，记录到 AuditLog 或事件载荷 |
| **BR-TRANS-03** | create()/addWorkItem() 前置：workItemIds 去重校验 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `LisTransferLogCreatedEvent` | `com.zxplat.domain.share.events.lis.transferlog` | `transferLogId`, `batchNo`, `sourceLocation`, `targetLocation`, `workItemIds[]` | `create` |
| `LisTransferConfirmedEvent` | `com.zxplat.domain.share.events.lis.transferlog` | `transferLogId`, `batchNo`, `verifiedCount`, `expectedCount` | `confirm` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- **软删**：`isDeleted = true`；查询默认过滤 `isDeleted = false`。
- **物理删**：无定时清理策略。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 领域服务不声明 `@Transactional`；事务由 AppService `AppServiceAspect` 管理。
- 分页默认 `Pageable`，排序默认 `transferTime DESC`。
- 事件发布 beforeCommit。

---

## 10. 生成与验收清单

- [ ] `LisTransferLog` 实体 + 字段与 §4.2 一致
- [ ] `workItemIds` 为 JSON String + 值对象层 `List<Long>` 互转
- [ ] `CreateLisTransferLogCommand` / `ConfirmLisTransferLogCommand` / `AddWorkItemToTransferLogCommand` 独立文件
- [ ] `LisTransferLogDomainService` 三个写方法：create / confirm / addWorkItem
- [ ] `LisTransferLogQueryService` 两个读方法：searchByBatchNo / getDetail
- [ ] 事件类 `LisTransferLogCreatedEvent` / `LisTransferConfirmedEvent`
- [ ] 事件包路径 `com.zxplat.domain.share.events.lis.transferlog`

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-009-LisTransferLog-标本转运.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- [ ] 字段表 §4.2 与 DM §3 一致（字段名、类型、必填）
- [ ] 写方法 §5.1 覆盖 DM §6 全部方法（create / confirm / addWorkItem）
- [ ] BR §6 逐条有实现策略（BR-TRANS-01~03）
- [ ] 事件 §7 覆盖 DM §5 全量事件
- [ ] 包路径 `com.zxplat.domain.lis.transferlog` 正确
- [ ] 编译通过（`mvn -pl zxplat/zxplat-domain compile`）

**本 SDD 正文结束。**
