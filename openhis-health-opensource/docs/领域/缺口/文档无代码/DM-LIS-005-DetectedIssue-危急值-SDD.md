# DM-LIS-005 DetectedIssue 危急值 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-005-DetectedIssue-危急值.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）
> **目标工程：`zxplat``
> **限界上下文**：检验域 · 危急值管理
> **聚合**：DetectedIssue（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-005 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.detectedissue` | 满足 | `detectedissue` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §4 业务规则 | 满足 | §6 映射（BR-CRT-01~04），§5 写方法由 §1 职责 + AS §4 编排推导 |
| DM 持久化形态 | 满足 | §4 字段表对齐 |
| DM 领域事件 | 满足 | 无（DM 未定义事件；告警通知由 AS 层编排） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **DetectedIssue 聚合** 的持久化模型、领域服务契约、查询契约、不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`lis_detected_issue` 聚合内状态、字段、不变式。
- **在外**：危急值告警编排（AUD-001 AppService 自动审核链触发）、通知发送（外部通知服务）。
- **跨域仅端口声明**：对 health Patient/Encounter 仅声明引用约束，不在本聚合内实现跨域写操作。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.lis.detectedissue`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DetectedIssue` |
| `valueobject` | 无（本聚合无 Embeddable 值对象） |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums`：`IssueStatus`、`Severity`；生成前须先判断是否已存在 |
| `command` | 每个写方法一个 Java 文件（见 §5） |
| `repository` | `DetectedIssueRepository`（仅 `JpaRepository` 接口） |
| `service` | `DetectedIssueDomainService`（直接实现类） |
| `queries` | `DetectedIssueQueryService`（直接实现类） |
| `queries.model` | `DetectedIssueQueryModel` |
| `share.events` | 无（本聚合无领域事件） |

**存储口径（DM 优先）**：`statusEnum` / `severityEnum` 采用 `String`（存枚举 code）；禁止混用。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `patientId`、`encounterId`、`observationId`、`authorId`、`acknowledgedBy` |
| 时间 | `identifiedDateTime` / `notifiedAt` / `acknowledgedAt` → `LocalDateTime`（语义含时分秒精度） |
| `*_enum` | `statusEnum` / `severityEnum` → `String`（存枚举 code） |
| `_flag` | 无 |
| 软删 | `Boolean isDeleted`；继承基类则遵循框架字段名 |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 值对象 | 本聚合无 `@Embeddable` |
| 仓储 | `interface DetectedIssueRepository extends JpaRepository<DetectedIssue, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；未指定排序时默认 `createdAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` 已开启的 UoW |
| 事件 | 本聚合无领域事件 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_detected_issue` | `DetectedIssue` | 聚合根 · 单表 |

### 4.2 聚合根 `DetectedIssue`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `statusEnum` | `String` | 是 | 枚举 code：preliminary / final / mitigated / entered-in-error |
| `severityEnum` | `String` | 是 | 枚举 code：high / moderate |
| `code` | `String` | 否 | 危急值类别编码 |
| `patientId` | `Long` | 是 | 患者标识，引用 health Patient |
| `encounterId` | `Long` | 是 | 就诊标识，引用 health Encounter |
| `observationId` | `Long` | 是 | 触发的结果 ID，引用 Observation |
| `identifiedDateTime` | `LocalDateTime` | 是 | 识别时间 |
| `authorId` | `Long` | 是 | 检验师 |
| `notificationMethod` | `String` | 否 | 通知方式（电话/短信/站内信） |
| `notifiedAt` | `LocalDateTime` | 否 | 通知时间 |
| `notifiedTo` | `String` | 否 | 通知对象 |
| `mitigationAction` | `String` | 否 | 临床处理措施 |
| `acknowledgedAt` | `LocalDateTime` | 否 | 临床确认时间（BR-CRT-03） |
| `acknowledgedBy` | `Long` | 否 | 确认人 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区/科室 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

**不变式方法**：`create` / `notify(method, target)` / `acknowledge(operatorId)`

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface DetectedIssueRepository extends JpaRepository<DetectedIssue, Long> {
    List<DetectedIssue> findByObservationIdAndIsDeletedFalse(Long observationId);
    List<DetectedIssue> findByStatusEnumAndSeverityEnumAndIsDeletedFalse(String status, String severity);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `DetectedIssueQueryModel`（对应 `searchByFilter`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_detected_issue.id` | |
| `statusEnum` | `String` | `lis_detected_issue.status_enum` | |
| `severityEnum` | `String` | `lis_detected_issue.severity_enum` | |
| `code` | `String` | `lis_detected_issue.code` | |
| `patientId` | `Long` | `lis_detected_issue.patient_id` | |
| `observationId` | `Long` | `lis_detected_issue.observation_id` | |
| `identifiedDateTime` | `LocalDateTime` | `lis_detected_issue.identified_date_time` | |
| `acknowledgedAt` | `LocalDateTime` | `lis_detected_issue.acknowledged_at` | |

#### 4.6.2 `DetectedIssueDetailQueryModel`（对应 `getDetail`）

同 §4.2 全量字段。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DetectedIssueDomainService`

#### 5.1.1 `create(CreateDetectedIssueCommand cmd)` → `DetectedIssue`

**命令文件**：`CreateDetectedIssueCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `statusEnum` | `String` | 初值：preliminary |
| `severityEnum` | `String` | high / moderate |
| `code` | `String` | 危急值类别编码 |
| `patientId` | `Long` | 患者 |
| `encounterId` | `Long` | 就诊 |
| `observationId` | `Long` | 触发的结果 |
| `authorId` | `Long` | 检验师 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `create` |
| `statusTransitionMethod` | 初始 → `preliminary` |
| `repositoryChecks` | BR-CRT-01：Observation.interpretation = HH/LL + 值超阈值（跨聚合校验，调用方保证） |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-CRT-001 |

**方法契约摘要**：前置：Observation 满足危急值触发条件（BR-CRT-01，由调用方校验）；持久化：新建 DetectedIssue，statusEnum = preliminary；失败：不满足触发条件 → 不创建；**注释锚点**：校验触发条件 → 创建 → 返回。

---

#### 5.1.2 `notify(NotifyDetectedIssueCommand cmd)` → `DetectedIssue`

**命令文件**：`NotifyDetectedIssueCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | issue ID |
| `notificationMethod` | `String` | 电话 / 短信 / 站内信 |
| `notifiedTo` | `String` | 通知对象 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `notify(method, target)` |
| `statusTransitionMethod` | preliminary → final（BR-CRT-03） |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-CRT-002 |

**方法契约摘要**：前置：无；持久化：notificationMethod / notifiedTo / notifiedAt 赋值，statusEnum → final；失败：无特殊；**注释锚点**：更新通知字段 → 状态迁移 → 返回。

---

#### 5.1.3 `acknowledge(AcknowledgeDetectedIssueCommand cmd)` → `DetectedIssue`

**命令文件**：`AcknowledgeDetectedIssueCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | issue ID |
| `mitigationAction` | `String` | 临床处理措施 |
| `acknowledgedBy` | `Long` | 确认人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `acknowledge(operatorId)` |
| `statusTransitionMethod` | final → mitigated（BR-CRT-03） |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-CRT-003 |

**方法契约摘要**：前置：无；持久化：mitigationAction / acknowledgedAt / acknowledgedBy 赋值，statusEnum → mitigated；失败：无特殊；**注释锚点**：更新确认字段 → 状态迁移 → 返回。

---

### 5.2 读侧 — `DetectedIssueQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchByFilter` | `DetectedIssueQuery` | `Page<DetectedIssueQueryModel>` | 分页 + 按 statusEnum / severityEnum / patientId / 时间范围过滤 | TC-CRT-R01 |
| `getDetail` | `Long id` | `DetectedIssueDetailQueryModel` | 按 id 查全量字段 | TC-CRT-R02 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §4）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-CRT-01** | 触发条件由调用方（AUD-001 自动审核链）校验：Observation.interpretationCode = HH/LL 且值超阈值；DetectedIssueDomainService 不重复校验跨聚合条件 |
| **BR-CRT-02** | 阈值按项目+性别+年龄分层，读 `lis_config.crt.threshold.*`；由 QueryService 或应用层 support 加载阈值，不在领域方法内直查配置表 |
| **BR-CRT-03** | notify() 将 statusEnum 从 preliminary → final；acknowledge() 将 statusEnum 从 final → mitigated |
| **BR-CRT-04** | 超时未确认升级通知：由定时 Job 查询 `notifiedAt < now - lis_config.crt.acknowledgeTimeoutMinutes` 且 statusEnum != mitigated 的记录，触发升级通知（应用层编排） |

---

## 7. 事件契约与触发规则（DM §5）

无。DM 未定义 DetectedIssue 领域事件；告警通知由 AS 层编排外部通知服务。

---

## 8. 删除与可见性契约

- **软删**：`isDeleted = true`；查询默认过滤 `isDeleted = false`。
- **物理删**：无定时清理策略。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 领域服务不声明 `@Transactional`；事务由 AppService `AppServiceAspect` 管理。
- 分页默认 `Pageable`，排序默认 `createdAt DESC`。
- 枚举统一复用 `com.zxplat.domain.share.enums`。

---

## 10. 生成与验收清单

- [ ] `DetectedIssue` 实体 + 字段与 §4.2 一致
- [ ] `CreateDetectedIssueCommand` / `NotifyDetectedIssueCommand` / `AcknowledgeDetectedIssueCommand` 独立文件
- [ ] `DetectedIssueRepository` 仅 `JpaRepository` 接口，无 `@Query`
- [ ] `DetectedIssueDomainService` 三个写方法：create / notify / acknowledge
- [ ] `DetectedIssueQueryService` 两个读方法：searchByFilter / getDetail
- [ ] `DetectedIssueQueryModel` / `DetectedIssueDetailQueryModel`
- [ ] 枚举 `IssueStatus` / `Severity` 复用共享模块（若已存在）

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-005-DetectedIssue-危急值.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- [ ] 字段表 §4.2 与 DM §3 一致（字段名、类型、必填）
- [ ] 写方法 §5.1 覆盖全部业务操作（create / notify / acknowledge）
- [ ] BR §6 逐条有实现策略
- [ ] 读方法 §5.2 覆盖全部查询场景
- [ ] 包路径 `com.zxplat.domain.lis.detectedissue` 正确
- [ ] 编译通过（`mvn -pl zxplat/zxplat-domain compile`）

**本 SDD 正文结束。**
