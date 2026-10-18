# DM-PEIS-005 PeisReportGrantExt 报告发放扩展 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-005-PeisReportGrantExt-报告发放扩展.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · 报告发放  
> **聚合**：PeisReportGrantExt（1:1 DiagnosticReport）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PEIS-005 |
| 包 `com.zxplat.plugin.peis.domain.reportgrantext` | 满足 | |
| DM §6.1 ×3 | 满足 | |
| DM §5 ×3 事件 | 满足 | |
| peis V1 DDL | 满足 | |

---

## 1. 设计目标与边界

### 1.1 设计目标

- REP-002 发放/签收/打印计数；PK=`diagnosticReportId`。
- 三写三事件；DR final 前置在 AS（BR-GRANT-03）。

### 1.2 边界

- **在内**：`peis_report_grant_ext`。
- **在外**：DR 正文与 final 在 health CLN/临床报告域。

---

## 2. 工程落点与包结构

### 2.2 包结构（`com.zxplat.plugin.peis.domain.reportgrantext`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisReportGrantExt` |
| `command` | `RegisterGrantCommand`、`SignGrantCommand`、`MarkGrantPrintedCommand` |
| `repository` | `PeisReportGrantExtRepository` |
| `service` | `PeisReportGrantDomainService` |
| `queries` | `PeisReportGrantQueryService` |
| `queries.model` | `GrantRowModel`、`PeisReportGrantModel` |

**存储口径**：`grantStatusEnum` → `String`；`grantTime` → `LocalDateTime`。

---

## 3. 类型 / 字段命名 / 注解规则

PK 为 `diagnosticReportId`（Long）；软删 `isDeleted`。

---

## 4. 实体 / 仓储 / 查询契约

### 4.2 聚合根 `PeisReportGrantExt`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `diagnosticReportId` | `Long` | 是 | PK/FK |
| `grantStatusEnum` | `String` | 是 | pending/printed/signed/mailed |
| `grantTime` | `LocalDateTime` | 否 | |
| `grantBy` | `Long` | 否 | |
| `recipientName` | `String` | 否 | |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体

无。

### 4.5 仓储

```java
public interface PeisReportGrantExtRepository extends JpaRepository<PeisReportGrantExt, Long> {
    Optional<PeisReportGrantExt> findByDiagnosticReportIdAndIsDeletedFalse(Long drId);
}
```

### 4.6 查询模型

#### 4.6.1 `GrantRowModel`（`searchPendingGrant`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `diagnosticReportId` | `Long` | ext | |
| `patientId` | `Long` | join DR | |
| `encounterId` | `Long` | join DR | |
| `grantStatusEnum` | `String` | ext | |
| `busNo` | `String` | join ext/pe | |
| `publishedAt` | `LocalDateTime` | DR | |

#### 4.6.2 `PeisReportGrantModel`（`getByDiagnosticReportId`）

§4.2 全字段 + `patientName`（join 只读）。

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `PeisReportGrantDomainService`

#### 5.1.1 `registerGrant(RegisterGrantCommand cmd)` → `PeisReportGrantExt`

**命令文件**：`RegisterGrantCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `diagnosticReportId` | `Long` | 必填 |
| `grantBy` | `Long` | 必填 |
| `grantTime` | `LocalDateTime` | 可选默认 now |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | register |
| `statusTransitionMethod` | → pending/printed 初始 |
| `repositoryChecks` | BR-GRANT-01 幂等 insert |
| `eventPublishAnchor` | `PeisReportGrantRegisteredEvent` |
| `unitTestCaseId` | TC-PEIS-GRANT-001 |

**方法契约摘要**：无行则 insert pending；**注释锚点**：exists → insert → 事件。

---

#### 5.1.2 `signGrant(SignGrantCommand cmd)` → `PeisReportGrantExt`

**命令文件**：`SignGrantCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `diagnosticReportId` | `Long` | 必填 |
| `recipientName` | `String` | BR-GRANT-02 |
| `grantBy` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | sign |
| `statusTransitionMethod` | → signed |
| `repositoryChecks` | 行存在 |
| `eventPublishAnchor` | `PeisReportGrantSignedEvent` |
| `unitTestCaseId` | TC-PEIS-GRANT-002 |

---

#### 5.1.3 `markPrinted(MarkGrantPrintedCommand cmd)` → `PeisReportGrantExt`

**命令文件**：`MarkGrantPrintedCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `diagnosticReportId` | `Long` | 必填 |
| `grantBy` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | markPrinted |
| `statusTransitionMethod` | → printed |
| `repositoryChecks` | 行存在 |
| `eventPublishAnchor` | `PeisReportGrantPrintedEvent` |
| `unitTestCaseId` | TC-PEIS-GRANT-003 |

---

### 5.2 读侧 — `PeisReportGrantQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `searchPendingGrant` | `GrantSearchQuery, Pageable` | `Page<GrantRowModel>` | TC-PEIS-GRANT-R01 |
| `getByDiagnosticReportId` | `Long drId` | `PeisReportGrantModel` | TC-PEIS-GRANT-R02 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-GRANT-01** | PK 唯一 |
| **BR-GRANT-02** | sign 校验 recipientName |
| **BR-GRANT-03** | AS Pre 校验 DR final |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 包路径 | 最小载荷 | 触发 |
| -------- | ------ | -------- | ---- |
| `PeisReportGrantRegisteredEvent` | `com.zxplat.domain.share.events.custom.peisreportgrantext` | `diagnosticReportId, grantStatusEnum` | registerGrant |
| `PeisReportGrantSignedEvent` | 同上 | `diagnosticReportId, recipientName` | signGrant |
| `PeisReportGrantPrintedEvent` | 同上 | `diagnosticReportId` | markPrinted |

**发布时机**：**beforeCommit**。

---

## 8. 删除与可见性契约

- 软删；发放记录不物理删。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] peis V1 表与实体一致
- [ ] 三写三事件
- [ ] searchPendingGrant 分页 SQL

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-005、peis V1、REP-002 AS

### 11.2 DoD

P1 领域编译通过

### 11.3 失败处理

DR FK 未就绪 → 文档标注

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 3
- [ ] §7 = 3 事件

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
| **总分** | **100** | **100** |

---

**本 SDD 正文结束。**
