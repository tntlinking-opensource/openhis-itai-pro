# DM-LIS-007 AuditEvent 接口日志 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-007-AuditEvent-接口日志.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）
> **目标工程：`zxplat``
> **限界上下文**：检验域 · 接口日志
> **聚合**：AuditEvent（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-007 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.auditevent` | 满足 | `auditevent` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 持久化形态 | 满足 | §4 字段表对齐 |
| DM §4 业务规则 | 满足 | §6 映射（BR-AE-01~02） |
| DM 领域事件 | 满足 | 无（日志类不发布领域事件） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **AuditEvent 聚合** 的持久化模型、领域服务契约、查询契约、不变式实现策略。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞。

### 1.2 边界

- **在内**：`lis_audit_event` 聚合内字段、不变式。
- **在外**：日志查询页面编排（应用层），日志清理定时任务（应用层）。
- **跨域仅端口声明**：无。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.lis.auditevent`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `AuditEvent` |
| `valueobject` | 无 |
| `enums`（共享） | 无新增枚举（`typeEnum` / `action` / `outcome` 为 `String`） |
| `command` | `CreateAuditEventCommand`（见 §5） |
| `repository` | `AuditEventRepository`（仅 `JpaRepository` 接口） |
| `service` | `AuditEventDomainService`（直接实现类） |
| `queries` | `AuditEventQueryService`（直接实现类） |
| `queries.model` | `AuditEventQueryModel` |
| `share.events` | 无 |

**存储口径（DM 优先）**：`typeEnum` / `action` / `outcome` 采用 `String`；`requestBody` / `responseBody` 为 `@Lob @Column(columnDefinition = "text")`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long`；聚合根主键 `id` |
| 时间 | `recorded` → `LocalDateTime`（语义含时分秒精度） |
| 大文本 | `requestBody` / `responseBody` → `@Lob` + `@Column(columnDefinition = "text")` |
| 软删 | 无（日志类不软删，按 BR-AE-02 定时物理删除） |
| 仓储 | `interface AuditEventRepository extends JpaRepository<AuditEvent, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；默认 `recorded DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` 已开启的 UoW |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_audit_event` | `AuditEvent` | 聚合根 · 单表 |

### 4.2 聚合根 `AuditEvent`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `typeEnum` | `String` | 是 | rest / export |
| `action` | `String` | 是 | C / R / U / D / E |
| `recorded` | `LocalDateTime` | 是 | 时间 |
| `agent` | `String` | 是 | 调用方（HIS/仪器/用户） |
| `source` | `String` | 是 | LIS 节点 |
| `entityId` | `Long` | 否 | 关联 id |
| `entityType` | `String` | 否 | 关联类型 |
| `outcome` | `String` | 是 | success / failure |
| `requestBody` | `String` (text) | 否 | 请求报文（BR-AE-01：敏感字段脱敏后存储） |
| `responseBody` | `String` (text) | 否 | 响应报文（BR-AE-01：脱敏） |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区/科室 |

**不变式方法**：无（纯日志记录，无业务状态机）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findByEntityIdAndEntityType(Long entityId, String entityType, Pageable pageable);
    void deleteByRecordedBefore(LocalDateTime cutoff);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `AuditEventQueryModel`（对应 `searchLogs`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_audit_event.id` | |
| `typeEnum` | `String` | `lis_audit_event.type_enum` | |
| `action` | `String` | `lis_audit_event.action` | |
| `recorded` | `LocalDateTime` | `lis_audit_event.recorded` | |
| `agent` | `String` | `lis_audit_event.agent` | |
| `outcome` | `String` | `lis_audit_event.outcome` | |
| `entityType` | `String` | `lis_audit_event.entity_type` | |

#### 4.6.2 `AuditEventDetailQueryModel`（对应 `getDetail`）

同 §4.2 全量字段（含 requestBody / responseBody）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `AuditEventDomainService`

#### 5.1.1 `record(CreateAuditEventCommand cmd)` → `AuditEvent`

**命令文件**：`CreateAuditEventCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `typeEnum` | `String` | rest / export |
| `action` | `String` | C / R / U / D / E |
| `agent` | `String` | 调用方 |
| `source` | `String` | LIS 节点 |
| `entityId` | `Long` | 关联 id |
| `entityType` | `String` | 关联类型 |
| `outcome` | `String` | success / failure |
| `requestBody` | `String` | 请求报文（已脱敏） |
| `responseBody` | `String` | 响应报文（已脱敏） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `record` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-AE-001 |

**方法契约摘要**：前置：BR-AE-01 调用方已脱敏；持久化：insert；失败：无特殊——日志落盘不应阻塞业务；**注释锚点**：脱敏校验 → insert → 返回。
> **实现提示**：建议用 `@Async` 或事件监听方式异步写入，避免日志 I/O 阻塞主业务事务。

---

### 5.2 读侧 — `AuditEventQueryService`（`domain.queries`）

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchLogs` | `AuditEventQuery` | `Page<AuditEventQueryModel>` | 分页 + 按 agent / source / outcome / 时间范围过滤 | TC-AE-R01 |
| `getDetail` | `Long id` | `AuditEventDetailQueryModel` | 按 id 查全量字段（含报文） | TC-AE-R02 |

**读方法注释锚点（统一）**：构建查询条件 → 单次 SQL 调用并完成分页统计 → 组装 QueryModel。

---

## 6. 不变式映射（DM §4）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-AE-01** | 调用方在传入前完成脱敏（电话/身份证/姓名 → ****）；AuditEventDomainService 不重复脱敏 |
| **BR-AE-02** | 定时 Job 调用 `deleteByRecordedBefore(now - retentionDays)`；`retentionDays` 读自 `lis_config.log.retentionDays` |

---

## 7. 事件契约与触发规则（DM §5）

无。DM 未定义 AuditEvent 领域事件。

---

## 8. 删除与可见性契约

- **不软删**：日志表按 BR-AE-02 定时物理删除（`DELETE FROM lis_audit_event WHERE recorded < cutoff`）。
- 查询：无 `isDeleted` 过滤。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 领域服务不声明 `@Transactional`。
- 分页默认 `Pageable`，排序默认 `recorded DESC`。
- `requestBody` / `responseBody` 使用 `@Lob` + `text` 列类型。
- `deleteByRecordedBefore` 需 `@Modifying @Transactional`（物理删除，非软删场景）。

---

## 10. 生成与验收清单

- [ ] `AuditEvent` 实体 + 字段与 §4.2 一致
- [ ] `requestBody` / `responseBody` 为 `@Lob` + `text`
- [ ] `AuditEventRepository.deleteByRecordedBefore` 正确
- [ ] BR-AE-01 脱敏由调用方保证
- [ ] BR-AE-02 定时清理实现
- [ ] `CreateAuditEventCommand` 独立文件

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-007-AuditEvent-接口日志.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- [ ] 字段表 §4.2 与 DM §3 一致（字段名、类型、必填）
- [ ] 写方法 §5.1 覆盖 record 操作
- [ ] BR §6 逐条有实现策略
- [ ] 读方法 §5.2 覆盖全部查询场景
- [ ] 包路径 `com.zxplat.domain.lis.auditevent` 正确
- [ ] 编译通过（`mvn -pl zxplat/zxplat-domain compile`）

**本 SDD 正文结束。**
