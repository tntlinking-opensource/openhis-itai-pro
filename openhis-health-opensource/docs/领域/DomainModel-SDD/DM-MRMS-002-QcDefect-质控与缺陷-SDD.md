# DM-MRMS-002 QcDefect 质控与缺陷 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MRMS-002-QcDefect-质控与缺陷.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：MRMS 病案管理 · 质控子域  
> **聚合**：QcDefect / QcRun / OverdueNotifyLog（配置表 QcRule、RequiredDocType 只读；子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-MRMS-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.mrms.qc` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 对齐附录 + 源码 |
| DM 第 7 章 BR | 满足 | §6 归档门禁等 |
| DM 第 5 章事件 | 满足 | §7（归档事件归属主档） |

**与 DM 显式差异（采纳依据：源码）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 事件 | §5 写归档由主档发 | 本包不写 `CaseRecordArchivedEvent`；`QcDefectDomainService` 无领域事件 |
| 规则执行 | §3 `QcRuleExecutor` | 执行器在 application/executor 包，本 SDD 仅覆盖 defect/run/notify 持久化服务 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化质控**缺陷实例**、**执行批次**、**逾期通知幂等**与**规则/必写文书读侧**契约，与 `QcDefectDomainService`、`OverdueNotifyDomainService`、`QcRuleQueryService` 对齐。
- 归档门禁：`assertNoOpenBlockDefects`（open/rectified + block 级拒绝）。
- **单次成稿目标**：无占位措辞。

### 1.2 边界

- **在内**：缺陷 CRUD 状态流、规则命中 upsert/自动关闭、qc_run 批次、逾期通知幂等、规则/必写文书配置读。
- **在外**：`QcRuleExecutor` 硬编码规则分支、EMR 文书查询、员工通知发送、病案归档状态迁移（DM-MRMS-001）。
- **跨域**：`caseRecordId` 外键；归档事件由主档发布。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`；表见 DM §1（`mrms_qc_*`、`mrms_required_doc_type`、`mrms_overdue_notify_log`）。

### 2.2 包结构（`com.zxplat.domain.mrms.qc`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `QcDefect`、`QcRun`、`QcRule`、`RequiredDocType`、`OverdueNotifyLog` |
| `repository` | 各实体 `*Repository` |
| `service` | `QcDefectDomainService`、`OverdueNotifyDomainService`、`QcRuleQueryService` |
| `model` | `QcRunResult`（执行器结果，非持久化根） |
| `executor` | `QcRuleExecutor`（接口；实现可在 application） |
| `enums`（共享） | `QcDefectStatus`、`QcDefectSeverity`、`QcTrigger` |
| `command` | 无（多参写侧） |
| `queries.model` | 读侧直接返回实体或配置实体 |

**存储口径**：`*_code` → `String`；`enabled`/`required`/`passed` → `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`caseRecordId` |
| 时间 | `rectifiedAt`/`closedAt`/`finishedAt` → `LocalDateTime`；`notifyDate` → `LocalDate` |
| `*_code` | `String` |
| 软删 | 基类 `isDeleted` |
| 仓储 | 仅接口 |
| 事务 | 写方法 `@Transactional`（存量） |
| 事件 | 本包无 publish |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `mrms_qc_defect` | `QcDefect` | 缺陷实例（本 SDD 主聚合根） |
| `mrms_qc_run` | `QcRun` | 单次规则集执行批次 |
| `mrms_qc_rule` | `QcRule` | 规则配置（读为主） |
| `mrms_required_doc_type` | `RequiredDocType` | 必写文书配置（读） |
| `mrms_overdue_notify_log` | `OverdueNotifyLog` | 逾期通知幂等 |

### 4.2 聚合根 `QcDefect`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | 租户 |
| `caseRecordId` | `Long` | 是 | 病案 FK |
| `ruleCode` | `String` | 否 | 规则 code |
| `defectMessage` | `String` | 是 | 描述 |
| `severityCode` | `String` | 是 | block/warn 等 |
| `statusCode` | `String` | 是 | open/rectified/closed |
| `responsiblePractitionerId` | `Long` | 否 | 整改责任人 |
| `rectifiedAt` | `LocalDateTime` | 否 | 整改时间 |
| `closedAt` | `LocalDateTime` | 否 | 关闭时间 |
| 审计 + `isDeleted` | — | 是 | 基类 |

### 4.3 子实体 `QcRun`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | 租户 |
| `caseRecordId` | `Long` | 是 | 病案 |
| `triggerCode` | `String` | 是 | on_receive/manual 等 |
| `defectOpenCount` | `Integer` | 是 | open 阻断计数 |
| `defectWarnCount` | `Integer` | 是 | warn 计数 |
| `passed` | `Boolean` | 是 | 是否通过 |
| `finishedAt` | `LocalDateTime` | 是 | 完成时间 |
| 审计 + `isDeleted` | — | 是 | 基类 |

### 4.4 子实体 `OverdueNotifyLog`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | 租户 |
| `caseRecordId` | `Long` | 是 | 病案 |
| `notifyTypeCode` | `String` | 是 | submit_overdue 等 |
| `notifyDate` | `LocalDate` | 是 | 幂等自然日 |
| 审计 + `isDeleted` | — | 是 | 基类 |

### 4.5 仓储接口

- `QcDefectRepository`：`findByCaseRecordIdAndIsDeletedFalseOrderByCreatedAtDesc`、`findByCaseRecordIdAndRuleCodeAndStatusCodeAndIsDeletedFalse`、`countByCaseRecordIdAndStatusCodeInAndSeverityCodeAndIsDeletedFalse`、`findByIdAndIsDeletedFalse`、`findByCaseRecordIdAndStatusCodeInAndIsDeletedFalse`
- `QcRunRepository`：`findByCaseRecordIdAndIsDeletedFalseOrderByFinishedAtDesc`
- `OverdueNotifyLogRepository`：`existsByCaseRecordIdAndNotifyTypeCodeAndNotifyDateAndIsDeletedFalse`
- `QcRuleRepository` / `RequiredDocTypeRepository`：租户 IN 查询（读侧）

### 4.6 查询模型（`queries.model`）

#### 4.6.1 缺陷/批次/配置读方法

| 读方法 | 出参 | 说明 |
| ------ | ---- | ---- |
| `listDefects` | `List<QcDefect>` | 字段同 §4.2 |
| `listRuns` | `List<QcRun>` | 字段同 §4.3 |
| `listActiveDefects` | `List<QcDefect>` | open+rectified |
| `listEnabledRules` | `List<QcRule>` | 种子+租户 merge |
| `listRequiredDocTypes` | `List<RequiredDocType>` | 种子+租户 merge |

#### 4.6.2 `QcRule`（`listEnabledRules`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `ruleCode` | `String` | `mrms_qc_rule.rule_code` | 规则 code |
| `ruleName` | `String` | `rule_name` | 名称 |
| `severityCode` | `String` | `severity_code` | 默认级别 |
| `categoryCode` | `String` | `category_code` | 分类 |
| `enabled` | `Boolean` | `enabled` | 是否启用 |
| `expressionJson` | `String` | `expression_json` | 扩展 JSON |

#### 4.6.3 `RequiredDocType`（`listRequiredDocTypes`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `documentTypeCode` | `String` | `document_type_code` | 文书类型 |
| `encounterClassCode` | `String` | `encounter_class_code` | 就诊类别 |
| `required` | `Boolean` | `required` | 是否必写 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `QcDefectDomainService`

#### 5.1.1 `assertNoOpenBlockDefects(Long caseRecordId)` → `void`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `caseRecordId` | `Long` | 病案 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `countByCaseRecordIdAndStatusCodeInAndSeverityCodeAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-001` |

**方法契约摘要**：前置无 open/rectified 的 block 缺陷；否则抛 `QC_ARCHIVE_BLOCKED`；无持久化变更。**注释锚点**：计数 → Gate。

---

#### 5.1.2 `addManualDefect(...)` → `QcDefect`

**字段全集**：`caseRecordId`、`tenantId`、`severityCode`、`message`、`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 初始 status=open |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-002` |

**方法契约摘要**：INSERT open 缺陷。**注释锚点**：组装 → save。

---

#### 5.1.3 `markRectified(Long defectId, Long operatorId)` → `QcDefect`

**字段全集**：`defectId`、`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 仅 open→rectified |
| `statusTransitionMethod` | 写 rectifiedAt |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-003` |

**方法契约摘要**：非 open 抛 `QC_DEFECT_INVALID_STATUS`。**注释锚点**：加载 → Gate → save。

---

#### 5.1.4 `closeDefect(Long defectId, Long operatorId)` → `QcDefect`

**字段全集**：`defectId`、`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | open/rectified→closed；已 closed 幂等 |
| `statusTransitionMethod` | 写 closedAt |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-004` |

**方法契约摘要**：非法状态拒绝。**注释锚点**：加载 → Gate → save。

---

#### 5.1.5 `upsertOpenDefectFromRule(...)` → `void`

**字段全集**：`tenantId`、`caseRecordId`、`ruleCode`、`severityCode`、`message`、`operatorId`、`now`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 同 rule+open 则 UPDATE 描述 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByCaseRecordIdAndRuleCodeAndStatusCodeAndIsDeletedFalse` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-005` |

**方法契约摘要**：规则命中 upsert。**注释锚点**：查 open → 更新或 INSERT。

---

#### 5.1.6 `closeOpenDefectByRuleIfPresent(...)` → `void`

**字段全集**：`caseRecordId`、`ruleCode`、`operatorId`、`now`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 open 则 no-op |
| `statusTransitionMethod` | open→closed |
| `repositoryChecks` | 同 5.1.5 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-006` |

**方法契约摘要**：规则未命中时自动关闭 open 缺陷。**注释锚点**：查 open → 关闭。

---

#### 5.1.7 `appendQcRun(...)` → `QcRun`

**字段全集**：`tenantId`、`caseRecordId`、`triggerCode`、`openBlockCount`、`openWarnCount`、`passed`、`operatorId`、`finishedAt`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-007` |

**方法契约摘要**：INSERT 批次汇总。**注释锚点**：组装 QcRun → save。

### 5.1 写侧 — `OverdueNotifyDomainService`

#### 5.1.8 `recordNotified(...)` → `void`

**字段全集**：`caseRecordId`、`tenantId`、`notifyTypeCode`、`notifyDate`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 幂等键三元组 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 无 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-MRMS-002-W-008` |

**方法契约摘要**：INSERT 通知日志。**注释锚点**：组装 → save。

### 5.2 读侧 — `QcDefectDomainService` / `OverdueNotifyDomainService` / `QcRuleQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `listDefects` | caseRecordId, statusCode?, severityCode? | `List<QcDefect>` | 仓储 + 内存过滤 | `TC-DM-MRMS-002-R-001` |
| `listRuns` | caseRecordId | `List<QcRun>` | 按 finishedAt DESC | `TC-DM-MRMS-002-R-002` |
| `countOpenDefectsBySeverity` | caseRecordId, severity | `int` | count 查询 | `TC-DM-MRMS-002-R-003` |
| `listActiveDefects` | caseRecordId | `List<QcDefect>` | status IN open,rectified | `TC-DM-MRMS-002-R-004` |
| `alreadyNotified` | caseRecordId, notifyTypeCode, notifyDate | `boolean` | exists 幂等 | `TC-DM-MRMS-002-R-005` |
| `listEnabledRules` | tenantId | `List<QcRule>` | 种子+租户 merge | `TC-DM-MRMS-002-R-006` |
| `listRequiredDocTypes` | tenantId, encounterClassCode | `List<RequiredDocType>` | 过滤+merge | `TC-DM-MRMS-002-R-007` |

**读方法注释锚点（统一）**：软删过滤 → 仓储/merge → 返回（无写库）。

---

## 6. 不变式映射（DM §7 / §4）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-QC-ARCHIVE | `assertNoOpenBlockDefects`：open/rectified + severity=block 计数>0 拒绝 |
| 缺陷状态机 | open→rectified→closed；`closeDefect` 允许 open/rectified 收口 |
| 规则 upsert | 同 caseRecordId+ruleCode+open 更新 message，否则 INSERT |
| 逾期幂等 | caseRecordId+notifyTypeCode+notifyDate 唯一发送 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `CaseRecordArchivedEvent` | `com.zxplat.domain.share.events.mrms.caserecord` | 见 DM-MRMS-001 | 主档 `approveArchive`（非本包 publish） |

**发布时机**：质控写侧 **不** 发布领域事件；EMR 锁定由应用层 afterCommit + 集成扩展。

---

## 8. 删除与可见性契约

- 缺陷/批次/通知日志读默认 `isDeleted=false`。
- 禁止用物理 DELETE 清除缺陷审计。

---

## 9. 框架优先适配规则

- 错误码 `QC_ARCHIVE_BLOCKED`、`QC_DEFECT_INVALID_STATUS`。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] 五表实体与 §4 一致
- [ ] 写 8 + 读 7 与附录一致
- [ ] `QcRuleExecutor` 调用 defect 写方法契约不变
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-MRMS-002 + 本 SDD；关联 DM-MRMS-001。

### 11.2 DoD

- 与现有 qc 包行为一致；编译通过。

### 11.3 失败处理

- 规则执行与 defect 语义冲突时以本 SDD + 源码为准回写 DM。

---

## 定稿检查清单（可打勾）

- [x] 金骨架完整
- [x] 写侧 8 方法（含 assert + recordNotified）
- [x] 读侧 7 方法
- [x] §4.2～4.4 字段表完整
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
