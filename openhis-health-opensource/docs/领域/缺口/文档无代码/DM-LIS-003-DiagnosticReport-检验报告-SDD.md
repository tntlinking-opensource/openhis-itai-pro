# DM-LIS-003 DiagnosticReport 检验报告 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-003-DiagnosticReport-检验报告.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程：`zxplat``  
> **限界上下文**：检验域 · 报告管理  
> **聚合**：DiagnosticReport（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.DiagnosticReport | 满足 | `DiagnosticReport 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §5 领域事件 | 满足 | §7 —— DiagnosticReportFinalizedEvent / DiagnosticReportAmendedEvent |
| DM §6 业务规则 | 满足 | §6 映射（BR-DR-01~04），§5 写方法由 AS §4 编排推导 |
| DM 持久化形态 | 满足 | §4 字段表对齐 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **DiagnosticReport 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`lis_diagnostic_report 表内状态、字段、不变式、事件发布。
- **在外**：报告发布编排（AUD-001 AppService）、报告修订（REP-001 AppService）。
- **跨域仅端口声明**：对 Observation / LisWorkItem / health 报告索引仅声明引用约束。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.lis.DiagnosticReport）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DiagnosticReport |
| `valueobject` | 无 |
| `enums`（共享） | `ReportStatus`（partial/final/amended），复用 `com.zxplat.domain.share.enums` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `DiagnosticReportRepository`（仅 `JpaRepository` 接口） |
| `service` | `DiagnosticReportDomainService`（直接实现类） |
| `queries` | `DiagnosticReportQueryService`（直接实现类） |
| `queries.model` | `DiagnosticReportQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.lis.DiagnosticReport（见 §7） |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long`；聚合根主键 `id` |
| 外键 | `serviceRequestId`、`workItemId`、`patientId`、`encounterId`、`previousId` |
| 时间 | `issuedAt` → `LocalDateTime`；`createdAt`/`updatedAt` → `LocalDateTime` |
| `*_enum` | `statusEnum` → `String`（存 ReportStatus 枚举 code） |
| `_flag` | 无 |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 仓储 | `interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` |
| 事件 | 领域服务内 `new XxxEvent(...)` + `DomainEventPublisher.publish`，**beforeCommit** |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_diagnostic_report | `DiagnosticReport | 聚合根 · 单表 |

### 4.2 聚合根 `DiagnosticReport（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `serviceRequestId` | `Long` | 是 | 关联医嘱 |
| `workItemId` | `Long` | 是 | 关联工单 |
| `patientId` | `Long` | 是 | 患者 |
| `encounterId` | `Long` | 是 | 就诊 |
| `statusEnum` | `String` | 是 | partial / final / amended / cancelled |
| `resultIds` | `String` (JSON) | 否 | Observation ID 列表 |
| `issuedAt` | `LocalDateTime` | 否 | 发布日期 |
| `previousId` | `Long` | 否 | 修订前版本 |
| `reportTemplateId` | `Long` | 否 | 版式模板 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

**不变式方法**：无（报告为聚合容器，无聚合内生命周期方法）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, Long> {
    Optional<DiagnosticReport> findByServiceRequestIdAndIsDeletedFalse(Long serviceRequestId);
    List<DiagnosticReport> findByWorkItemIdAndIsDeletedFalse(Long workItemId);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `DiagnosticReportQueryModel`（对应 `searchReports`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_diagnostic_report.id` | |
| `serviceRequestId` | `Long` | `lis_diagnostic_report.service_request_id` | |
| `workItemId` | `Long` | `lis_diagnostic_report.work_item_id` | |
| `statusEnum` | `String` | `lis_diagnostic_report.status_enum` | |
| `issuedAt` | `LocalDateTime` | `lis_diagnostic_report.issued_at` | |
| `createdAt` | `LocalDateTime` | `lis_diagnostic_report.created_at` | |

#### 4.6.2 `DiagnosticReport（对应 `getByServiceRequestId`）

直接返回 `DiagnosticReport 实体，无独立 QueryModel。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DiagnosticReportDomainService`

#### 5.1.1 `finalize(FinalizeReportCommand cmd)` → `DiagnosticReport

**命令文件**：`FinalizeReportCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 必填 |
| `workItemId` | `Long` | 必填 |
| `patientId` | `Long` | 必填 |
| `encounterId` | `Long` | 必填 |
| `resultIds` | `List<Long>` | Observation ID 列表 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `statusTransitionMethod` | 新建 → `final` |
| `repositoryChecks` | BR-DR-01：所有 Observation 为 final/cancelled |
| `eventPublishAnchor` | `DiagnosticReportFinalizedEvent` |
| `unitTestCaseId` | TC-DR-001 |

**方法契约摘要**：前置：所有关联 Observation 为 final 或 cancelled；持久化：创建 DiagnosticReport(status=final) + resultIds；事件：DiagnosticReportFinalizedEvent；**注释锚点**：校验 Observation 状态 → 创建报告 → 发布事件。

---

#### 5.1.2 `amend(AmendReportCommand cmd)` → `DiagnosticReport

**命令文件**：`AmendReportCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `reportId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `statusTransitionMethod` | `final` → `amended` |
| `repositoryChecks` | BR-DR-02：召回仅 statusEnum = final；BR-DR-03：previousId 指向前版 |
| `eventPublishAnchor` | `DiagnosticReportAmendedEvent` |
| `unitTestCaseId` | TC-DR-002 |

**方法契约摘要**：前置：statusEnum = final；持久化：原报告 → amended + previousId 指向原版；事件：DiagnosticReportAmendedEvent；**注释锚点**：加载 → 状态门禁 → 召回 → 发布事件。

---

### 5.2 读侧 — `DiagnosticReportQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchReports` | `ReportQuery` | `Page<DiagnosticReportQueryModel>` | 分页 | TC-DR-R01 |
| `getByServiceRequestId` | `Long serviceRequestId` | `DiagnosticReport | 按医嘱 | TC-DR-R02 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL 调用 → 组装 QueryModel。

---

## 6. 不变式映射（DM §6）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-DR-01** | `finalize` 前置：所有关联 Observation 为 final 或 cancelled |
| **BR-DR-02** | `amend` 前置：statusEnum = final |
| **BR-DR-03** | `amend` 版本链：previousId 指向原版本 |
| **BR-DR-04** | 微生物三级报告：status partial → final（由 AS 编排，非领域内状态机） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 最小载荷 | 触发方法 |
| ---- | ------ | -------- | -------- |
| `DiagnosticReportFinalizedEvent` | `com.zxplat.domain.share.events.lis.DiagnosticReport | `id, serviceRequestId, workItemId, patientId, encounterId` | `finalize` |
| `DiagnosticReportAmendedEvent` | `com.zxplat.domain.share.events.lis.DiagnosticReport | `id, previousId, serviceRequestId` | `amend` |

---

## 8. 删除与可见性契约

- 软删：`isDeleted = true`，不物理删除。
- 查询默认过滤 `isDeleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 事务由 AppService `AppServiceAspect` / `ReadOnlyTransactionAspect` 管理。

---

## 10. 生成与验收清单

- [ ] `DiagnosticReport 实体 + 字段与 §4.2 一致
- [ ] 4 条 BR 不变式正确实现
- [ ] 2 个领域事件发布
- [ ] Repository 无 `@Query`
- [ ] 读侧 2 个方法可用

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-003-DiagnosticReport-检验报告.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- `DiagnosticReport 实体编译通过。
- 全部 2 个写方法功能正确。
- 事件发布与 beforeCommit 集成正确。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] §5.1 子节数量 = 2
- [ ] §4.2 字段表与 DM §3 一一对应
- [ ] 全文无 YAML、无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98（可进入代码会话）** |

---

**本 SDD 正文结束。**
