# DM-MRMS-003 BorrowRequest 病案借阅申请 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MRMS-003-BorrowRequest-病案借阅申请.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：MRMS 病案管理 · 借阅/复印申请  
> **聚合**：BorrowRequest（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-MRMS-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.mrms.borrowrequest` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 写 5、读 1（附录对齐源码） |
| DM 第 7 章 BR | 满足 | §6 映射 BR-BORROW-001～002 |
| DM 第 5 章事件 | 满足 | §7（minimal：无独立事件类） |

**与 DM 显式差异（采纳依据：以 zxplat-domain 实现为准）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 事件 | §5 minimal 无事件表 | 写侧无 `share.events` 类；状态变更同事务落库 |
| 命令形态 | §6 简表 | 写方法沿用**多参 + 聚合方法**（未拆 Command 文件），字段全集见 §5.1 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **BorrowRequest 单表聚合** 的持久化模型、写/读领域服务契约与状态机实现策略，与现有 `BorrowRequestDomainService` / `BorrowRequestQueryService` 对齐。
- 状态迁移委托聚合根方法；写侧参数以方法签名 + §5 字段全集表为准（模式 B 存量，后续可拆 Command 不改语义）。
- **单次成稿目标**：正文无「待确认」「后续补充」；缺口在文档阶段关闭。

### 1.2 边界

- **在内**：借阅/复印申请 CRUD 生命周期（pending → approved/rejected → borrowed → returned）、分页查询。
- **在外**：病案主档内容修改、PDF 导出、审批 UI、人员字典 join。
- **跨域仅端口声明**：`caseRecordId` 引用 DM-MRMS-001；写侧不注入 `CaseRecordRepository`（由应用层校验病案存在）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（实体/仓储/领域服务/查询）；Flyway 见 `V9__mrms_prt_ddl.sql` 等 MRMS 脚本。

### 2.2 包结构（`com.zxplat.domain.mrms.borrowrequest`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `BorrowRequest` |
| `valueobject` | 无 |
| `enums`（共享） | `com.zxplat.domain.share.enums.BorrowRequestStatus` |
| `command` | 无（写侧多参；见 §5） |
| `repository` | `BorrowRequestRepository` |
| `service` | `BorrowRequestDomainService`、`BorrowRequestQueryService` |
| `queries` | `BorrowRequestPageQuery` |
| `queries.model` | 无（读侧直接返回实体，见 §4.6） |
| `share.events` | 本聚合无独立事件类 |

**存储口径（DM 优先）**：`request_type_code`、`status_code` → `String`（存 code）；无 `*_enum` 列名。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`id`、`caseRecordId`、`applicantPractitionerId` |
| 时间 | `approvedAt`/`borrowedAt`/`dueReturnAt`/`returnedAt` → `LocalDateTime` |
| `*_code` | `String`（字典/状态 code） |
| `_flag` | 本表无 |
| 软删 | 基类 `isDeleted`；读默认过滤未删除 |
| 实体 | `@Entity` + `@Table(name="mrms_borrow_request")` |
| 仓储 | 仅 `JpaRepository` 接口，禁 `@Query` |
| 分页 | `Pageable`；未指定排序默认 `createdAt DESC` |
| 事务 | 写方法 `@Transactional`（存量实现；新代码可收敛至 AppService UoW） |
| 事件 | 无领域事件发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `mrms_borrow_request` | `BorrowRequest` | 单表聚合根 |

### 4.2 聚合根 `BorrowRequest`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | 租户 |
| `caseRecordId` | `Long` | 是 | 病案主档 FK |
| `requestTypeCode` | `String` | 是 | 借阅/复印类型 code |
| `applicantPractitionerId` | `Long` | 否 | 申请人 |
| `applicantName` | `String` | 否 | 申请人姓名快照 |
| `applicantDeptName` | `String` | 否 | 申请人科室快照 |
| `purpose` | `String` | 否 | 用途 |
| `scopeDesc` | `String` | 否 | 范围说明 |
| `copyCount` | `Integer` | 否 | 复印份数 |
| `patientAuthorizationInfo` | `String` | 否 | 患者授权摘要 |
| `statusCode` | `String` | 是 | `BorrowRequestStatus` code |
| `approveComment` | `String` | 否 | 审批/拒绝意见 |
| `approvedBy` | `Long` | 否 | 审批人 |
| `approvedAt` | `LocalDateTime` | 否 | 审批完成时间 |
| `borrowedAt` | `LocalDateTime` | 否 | 借出时间 |
| `dueReturnAt` | `LocalDateTime` | 否 | 应还时间 |
| `returnedAt` | `LocalDateTime` | 否 | 归还时间 |
| `createdAt` / `createdBy` / `updatedAt` / `updatedBy` / `isDeleted` | 审计+软删 | 是 | 基类 |

**不变式方法**：`initApply`、`approve`、`reject`、`markBorrowed`、`markReturned`、`assertStatus`（私有 Gate）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `BorrowRequestRepository extends JpaRepository<BorrowRequest, Long>`
- `findByIdAndIsDeletedFalse(Long id)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `queryPage` 出参

直接返回 `BorrowRequest` 实体分页，无独立 QueryModel；字段同 §4.2。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `BorrowRequestDomainService`

#### 5.1.1 `apply(...)` → `BorrowRequest`

**命令文件**：无（多参；语义等价 `ApplyBorrowRequestCommand`）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `tenantId` | `Long` | 租户 |
| `caseRecordId` | `Long` | 病案主档 |
| `requestTypeCode` | `String` | 申请类型 |
| `applicantPractitionerId` | `Long` | 申请人 |
| `applicantName` | `String` | 申请人姓名 |
| `applicantDeptName` | `String` | 申请人科室 |
| `purpose` | `String` | 用途 |
| `scopeDesc` | `String` | 范围 |
| `copyCount` | `Integer` | 复印份数 |
| `patientAuthorizationInfo` | `String` | 患者授权 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `initApply` |
| `statusTransitionMethod` | 初始 `PENDING` |
| `repositoryChecks` | 无（caseRecord 存在性应用层） |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-003-W-001` |

**方法契约摘要**：前置 caseRecordId 有效（应用层）；INSERT 且 status=pending；失败抛业务/校验异常；无事件。**注释锚点**：组装实体 → 持久化。

---

#### 5.1.2 `approve(Long id, String comment, Long operatorId)` → `BorrowRequest`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 申请主键 |
| `comment` | `String` | 审批意见 |
| `operatorId` | `Long` | 审批人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `approve` + `assertStatus(PENDING)` |
| `statusTransitionMethod` | `approve` → APPROVED |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-003-W-002` |

**方法契约摘要**：前置 pending；UPDATE 审批字段；非 pending 抛 `BORROW_INVALID_STATUS`。**注释锚点**：加载 → Gate → 持久化。

---

#### 5.1.3 `reject(Long id, String comment, Long operatorId)` → `BorrowRequest`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 申请主键 |
| `comment` | `String` | 拒绝原因 |
| `operatorId` | `Long` | 审批人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `reject` |
| `statusTransitionMethod` | pending → REJECTED |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-003-W-003` |

**方法契约摘要**：同 approve，目标 REJECTED。**注释锚点**：加载 → Gate → 持久化。

---

#### 5.1.4 `markBorrowed(Long id, LocalDateTime dueReturnAt, Long operatorId)` → `BorrowRequest`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 申请主键 |
| `dueReturnAt` | `LocalDateTime` | 应还时间 |
| `operatorId` | `Long` | 经办人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markBorrowed` |
| `statusTransitionMethod` | approved → BORROWED |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-003-W-004` |

**方法契约摘要**：前置 approved；写入 borrowedAt/dueReturnAt。**注释锚点**：加载 → Gate → 持久化。

---

#### 5.1.5 `markReturned(Long id, Long operatorId)` → `BorrowRequest`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 申请主键 |
| `operatorId` | `Long` | 经办人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markReturned` |
| `statusTransitionMethod` | borrowed → RETURNED |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-003-W-005` |

**方法契约摘要**：前置 borrowed；写入 returnedAt。**注释锚点**：加载 → Gate → 持久化。

### 5.2 读侧 — `BorrowRequestQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`；分页在 **数据库侧** 过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `queryPage` | `BorrowRequestPageQuery` + `Pageable` | `Page<BorrowRequest>` | Specification：tenantId 必填；可选 statusCode、caseRecordId | `TC-DM-MRMS-003-R-001` |

**`BorrowRequestPageQuery` 字段**：`tenantId`、`statusCode`、`caseRecordId`；关键字见基类 `keyword`。

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 返回实体（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-BORROW-001 | 聚合 `assertStatus` 仅允许 pending→approved/rejected→borrowed→returned 单步迁移 |
| BR-BORROW-002 | `caseRecordId` 非空列 + 应用层校验主档存在且未删 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| （无） | — | — | 写操作不发布领域事件 |

**发布时机**：N/A。

---

## 8. 删除与可见性契约

- 本聚合无业务软删写方法；读侧默认 `isDeleted=false`。
- 禁止物理 DELETE 清理借阅审计数据（除非运维脚本，非领域层）。

---

## 9. 框架优先适配规则

- 审计/租户/软删继承 `BaseAuditedEntity`。
- 错误码 `MrmsErrorCodes.BORROW_INVALID_STATUS`。
- 遵循项目级成码门禁（Repository 禁 `@Query`、字符串 trim 用框架 `StringUtils`）。

---

## 10. 生成与验收清单

- [ ] 实体字段与 §4.2 一致
- [ ] 写方法 5 + 读方法 1 与附录一致
- [ ] 状态枚举 `BorrowRequestStatus` 与 `statusCode` 一致
- [ ] `BorrowRequestPageQuery` + Specification 分页
- [ ] 编译 `zxplat-domain` 通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-MRMS-003 与本 SDD。
- 风格锚点：`mrms/borrowrequest` 现有实现。

### 11.2 DoD

- 字段 / 方法 / BR 与本文 100% 一致。
- Maven 编译 `zxplat-domain` 成功。

### 11.3 失败处理

- 发现 DOC-CONFLICT：以 DM + 本 SDD 为准修订文档或代码，禁止口头补丁。

---

## 定稿检查清单（可打勾）

- [x] 章节骨架与金母版同构
- [x] 写方法 5 = 附录写侧
- [x] 读方法 1 = 附录读侧
- [x] §4.2 字段表完整
- [x] 无「待确认/后续补充」措辞

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
