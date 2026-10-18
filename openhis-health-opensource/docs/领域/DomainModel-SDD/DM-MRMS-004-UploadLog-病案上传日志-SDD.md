# DM-MRMS-004 UploadLog 病案上传日志 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MRMS-004-UploadLog-病案上传日志.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：MRMS 病案管理 · 上传审计  
> **聚合**：UploadLog（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-MRMS-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.mrms.upload` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 写 3、读 2（附录对齐源码） |
| DM 第 7 章 BR | 满足 | §6 映射 BR-UPLOAD-001～002 |
| DM 第 5 章事件 | 满足 | §7（minimal：无事件类） |

**与 DM 显式差异（采纳依据：源码）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 跨聚合写 | DM 未单列 | `updateCaseRecord*` 经 `CaseRecordRepository` 回写主档冗余列，同一 UoW |
| 命令 | §6 简表 | 写方法多参，字段全集见 §5.1 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **UploadLog** 追加审计与 **CaseRecord 上传冗余字段** 回写契约，与 `UploadLogDomainService` 对齐。
- 每次 4401/4101A 尝试 INSERT 日志；成功/失败均留痕（BR-UPLOAD-001）。
- **单次成稿目标**：正文无占位措辞。

### 1.2 边界

- **在内**：`mrms_upload_log` 追加、按病案列日志、主档 `chs_upload_*` / `settle_upload_*` 冗余更新。
- **在外**：HTTP 调用医保、报文组装、插件编排。
- **跨域**：读写在同一服务内更新 `CaseRecord` 冗余列（DM-MRMS-001 字段），不发布跨域事件。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`；表 `mrms_upload_log`（Flyway MRMS 脚本）。

### 2.2 包结构（`com.zxplat.domain.mrms.upload`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `UploadLog` |
| `valueobject` | 无 |
| `enums`（共享） | `com.zxplat.domain.share.enums.ChsUploadStatus` |
| `command` | 无（多参写侧） |
| `repository` | `UploadLogRepository` |
| `service` | `UploadLogDomainService` |
| `queries` | 无独立 QueryService |
| `queries.model` | 无 |
| `share.events` | 无 |

**存储口径**：`status_code`、`infno` → `String`；`infcode` → `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`caseRecordId`、`contractId` |
| 时间 | 审计 `LocalDateTime`；主档回写 `chsUploadAt`/`settleUploadAt` |
| `*_code` | `String` |
| 软删 | 基类 `isDeleted` |
| 实体 | `@Table(name="mrms_upload_log")` |
| 仓储 | 仅接口 |
| 事务 | 写方法 `@Transactional`（存量） |
| 事件 | 无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `mrms_upload_log` | `UploadLog` | 上传尝试审计 |

### 4.2 聚合根 `UploadLog`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | 租户 |
| `caseRecordId` | `Long` | 是 | 病案 FK（BR-UPLOAD-002） |
| `contractBusNo` | `String` | 否 | 合约业务号 |
| `contractId` | `Long` | 否 | 合约主键 |
| `infno` | `String` | 是 | 接口号（4401 等） |
| `statusCode` | `String` | 是 | 本次结果 status |
| `msgid` | `String` | 否 | 医保 msgid |
| `requestDigest` | `String` | 否 | 请求摘要 hash |
| `responseSnapshot` | `String` | 否 | 响应 JSON 脱敏 |
| `errMsg` | `String` | 否 | 错误摘要 |
| `infcode` | `String` | 否 | 平台 infcode |
| 审计 + `isDeleted` | — | 是 | 基类 |

**不变式方法**：`init`（组装新日志行）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `UploadLogRepository extends JpaRepository<UploadLog, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- `findByCaseRecordIdAndIsDeletedFalseOrderByCreatedAtDesc(Long caseRecordId)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `listByCaseRecord` / `requireById`

直接返回 `UploadLog` 实体，字段同 §4.2。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `UploadLogDomainService`

#### 5.1.1 `appendLog(...)` → `UploadLog`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `tenantId` | `Long` | 租户 |
| `caseRecordId` | `Long` | 病案 |
| `contractBusNo` | `String` | 合约业务号 |
| `contractId` | `Long` | 合约 ID |
| `infno` | `String` | 接口编号 |
| `statusCode` | `String` | 结果 status |
| `requestDigest` | `String` | 请求摘要 |
| `infcode` | `String` | 回执码 |
| `errMsg` | `String` | 错误信息 |
| `responseSnapshot` | `String` | 响应快照 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `init` |
| `statusTransitionMethod` | N/A（追加日志） |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-004-W-001` |

**方法契约摘要**：前置 caseRecordId 非空；INSERT 新日志行，不修改历史行（BR-UPLOAD-001）。**注释锚点**：组装 → 持久化。

---

#### 5.1.2 `updateCaseRecordUploadStatus(...)` → `void`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `caseRecordId` | `Long` | 病案 |
| `status` | `ChsUploadStatus` | 4401 冗余状态 |
| `errorMessage` | `String` | 失败摘要 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `CaseRecordRepository.findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-004-W-002` |

**方法契约摘要**：UPDATE 主档 `chsUploadStatusCode`、`chsUploadAt`、`chsUploadError`；无主档抛 `EntityNotFoundException`。**注释锚点**：加载主档 → 写冗余 → save。

---

#### 5.1.3 `updateCaseRecordSettleUploadStatus(...)` → `void`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `caseRecordId` | `Long` | 病案 |
| `settleUploadStatusCode` | `String` | 0/1/2 |
| `setlListId` | `String` | 清单流水号 |
| `clearSetlListId` | `boolean` | 是否清空 setlListId |
| `errorMessage` | `String` | 错误摘要 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-004-W-003` |

**方法契约摘要**：UPDATE `settleUploadStatusCode`、`setlListId`（按 clear 标志）、`settleUploadAt`、`settleUploadError`。**注释锚点**：加载 → 合并 setlListId 规则 → save。

### 5.2 读侧 — `UploadLogDomainService`（加载/列表，无独立 QueryService）

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `requireById` | `Long id` | `UploadLog` | `findByIdAndIsDeletedFalse` | `TC-DM-MRMS-004-R-001` |
| `listByCaseRecord` | `Long caseRecordId` | `List<UploadLog>` | 按 createdAt DESC | `TC-DM-MRMS-004-R-002` |

**读方法注释锚点（统一）**：软删过滤 → 仓储查询 → 返回实体（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-UPLOAD-001 | 仅 INSERT 新日志；不回写历史日志行快照 |
| BR-UPLOAD-002 | `caseRecordId` 非空；回写主档前必须存在未删主档 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| （无） | — | — | — |

**发布时机**：N/A。

---

## 8. 删除与可见性契约

- 日志追加型聚合；读默认 `isDeleted=false`。
- 禁止领域层物理 DELETE 日志行。

---

## 9. 框架优先适配规则

- 跨聚合写主档冗余与日志 INSERT 同事务（`@Transactional`）。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] `UploadLog` 与 §4.2 一致
- [ ] 写 3 + 读 2 方法
- [ ] 主档冗余列与 CaseRecord 实体一致
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-MRMS-004 + 本 SDD；依赖 DM-MRMS-001 主档字段。

### 11.2 DoD

- 与现有 `UploadLogDomainService` 行为一致；编译通过。

### 11.3 失败处理

- 跨域字段漂移时同步修订 CaseRecord SDD §4.2。

---

## 定稿检查清单（可打勾）

- [x] 金骨架完整
- [x] 写 3 / 读 2
- [x] §4.2 完整
- [x] 无占位措辞

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
