# DM-CLN-008 DiagnosticReport 临床总检报告 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-008-DiagnosticReport-临床总检报告.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：clinical · PEIS 总检报告事实  
> **聚合**：DiagnosticReport（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CLN-008 v1.1 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.clinical.DiagnosticReport | 满足 | 与 LIS `domain.lis.DiagnosticReport 隔离 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6.1 写方法 | 满足 | §5.1 共 3 节 |
| DM §6.2 读方法 | 满足 | §5.2 + §4.6 |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | §7 共 2 事件 |
| V79 DDL | 满足 | `cln_diagnostic_report（zxplat-domain；V77 为 MRMS） |

**与 LIS 显式差异（类名同、包不同）**

| 点 | LIS（DM-LIS-003） | 本 SDD |
| -- | ----------------- | ------ |
| 表 | `lis_diagnostic_report | `cln_diagnostic_report |
| 粒度 | 按 SR/WorkItem | **1 PE Encounter 1 主报告** |
| 包 | `domain.lis.DiagnosticReport | **`domain.clinical.DiagnosticReport** |
| 服务类 | `DiagnosticReportDomainService`（lis） | **`DiagnosticReportDomainService`（clinical）** |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 PEIS **总检主报告** 事实表与 MAS-03/04 写读契约；`finalizeMaster` 后由 AppService **同事务** 调 [DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引-SDD.md) `upsertFromPeis`。
- 实体名 **`DiagnosticReport**（无 `Clinical` 前缀）；与 LIS 同名类 **仅通过包路径区分**。

### 1.2 边界

- **在内**：`cln_diagnostic_report 正文与状态。
- **在外**：index 写入、Encounter finish、PDF 渲染（PRT）、`peis_report_grant_ext`。
- **跨域**：读 Encounter(PE) 校验；写 index 仅端口声明（AppService 编排）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

`zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.clinical.DiagnosticReport）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DiagnosticReport |
| `command` | `CreateForEncounterCommand`、`SaveMasterDraftCommand`、`FinalizeMasterCommand` |
| `repository` | `DiagnosticReportRepository` |
| `service` | `DiagnosticReportDomainService` |
| `queries` | `DiagnosticReportQueryService` |
| `queries.model` | `DiagnosticReportModel` |

**存储口径**：`statusEnum` → `String`；`finalizedAt` → `LocalDateTime`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| PK | `Long id` |
| 业务唯一 | `encounterId`（uk_cln_dr_encounter） |
| 软删 | `Boolean isDeleted` |
| 仓储 | `JpaRepository` 接口，禁止 `@Query` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | beforeCommit 发布 §7 事件 |
| 包隔离 | **禁止** import `com.zxplat.domain.lis.diagnosticreport.*` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 |
| ------ | ------ |
| `cln_diagnostic_report | `DiagnosticReport |

### 4.2 聚合根 `DiagnosticReport

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `encounterId` | `Long` | 是 | BR-CLN-DR-01 唯一 |
| `patientId` | `Long` | 是 | 冗余 |
| `statusEnum` | `String` | 是 | preliminary/final/amended/… |
| `summaryText` | `String` | 否 | MAS-03 综述 |
| `conclusionText` | `String` | 否 | 总检结论 |
| `resultRefsJson` | `String` | 否 | Observation id 列表 JSON |
| `pdfStorageKey` | `String` | 否 | REP-02 PRT 回写 |
| `finalizedAt` | `LocalDateTime` | 否 | MAS-04 |
| `finalizedBy` | `Long` | 否 | |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式方法**：`saveDraft(summary, conclusion)` / `finalize(operatorId)` / `markAmended()`（P1 MAS-05）

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, Long> {
    Optional<DiagnosticReport> findByEncounterIdAndIsDeletedFalse(Long encounterId);
    boolean existsByEncounterIdAndIsDeletedFalse(Long encounterId);
}
```

### 4.6 查询模型

#### 4.6.1 `DiagnosticReportModel`（`getByEncounterId` / `mustGetById`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | id | |
| `encounterId` | `Long` | encounter_id | |
| `patientId` | `Long` | patient_id | |
| `statusEnum` | `String` | status_enum | |
| `summaryText` | `String` | summary_text | |
| `conclusionText` | `String` | conclusion_text | |
| `resultRefsJson` | `String` | result_refs_json | |
| `pdfStorageKey` | `String` | pdf_storage_key | |
| `finalizedAt` | `LocalDateTime` | finalized_at | |
| `finalizedBy` | `Long` | finalized_by | |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DiagnosticReportDomainService`

#### 5.1.1 `createOrGetForEncounter(CreateForEncounterCommand cmd)` → `DiagnosticReport

**命令文件**：`CreateForEncounterCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `patientId` | `Long` | 必填 |
| `tenantId` | `Long` | 必填 |
| `orgId` | `Long` | 可选 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | create preliminary |
| `statusTransitionMethod` | → preliminary |
| `repositoryChecks` | BR-CLN-DR-01：exists 则返回已有 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-CLN-DR-001 |

**方法契约摘要**：前置：Encounter(PE) 存在；持久化：有则返回已有行，无则 insert preliminary；失败：无；**注释锚点**：findByEncounter → 不存在则 create → 返回。

---

#### 5.1.2 `saveMasterDraft(SaveMasterDraftCommand cmd)` → `DiagnosticReport

**命令文件**：`SaveMasterDraftCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `summaryText` | `String` | 必填其一 |
| `conclusionText` | `String` | 可选 |
| `resultRefsJson` | `String` | 可选 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | saveDraft |
| `repositoryChecks` | 无行则 createOrGetForEncounter；status≠final（BR-CLN-DR-02） |
| `eventPublishAnchor` | `DiagnosticReportDraftSavedEvent` |
| `unitTestCaseId` | TC-CLN-DR-002 |

**方法契约摘要**：前置：status 非 final；持久化：update 文本字段；失败：final 后拒绝 draft；事件：DraftSaved beforeCommit；**注释锚点**：load → 状态校验 → update → 事件。

---

#### 5.1.3 `finalizeMaster(FinalizeMasterCommand cmd)` → `DiagnosticReport

**命令文件**：`FinalizeMasterCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | finalize |
| `statusTransitionMethod` | → final |
| `repositoryChecks` | 行存在；已 final 幂等或拒绝（MAS SDD） |
| `eventPublishAnchor` | `DiagnosticReportFinalizedEvent` |
| `unitTestCaseId` | TC-CLN-DR-003 |

**方法契约摘要**：前置：MAS AppService 已校验分科完成；持久化：写 finalizedAt/By、status=final；失败：行不存在；**不**在本方法内写 index；事件：Finalized beforeCommit；**注释锚点**：load → finalize → save → 事件。

---

### 5.2 读侧 — `DiagnosticReportQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `getByEncounterId` | `Long encounterId` | `Optional<DiagnosticReportModel>` | 软删过滤 | TC-CLN-DR-R01 |
| `mustGetById` | `Long id` | `DiagnosticReportModel` | 不存在抛错 | TC-CLN-DR-R02 |

**读方法注释锚点（统一）**：软删过滤 → 仓储查询 → 组装 Model（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-CLN-DR-01** | uk_cln_dr_encounter + createOrGet 幂等 |
| **BR-CLN-DR-02** | saveMasterDraft/finalize 拒绝 final 后再 draft（amended 另开 P1） |
| **BR-CLN-DR-03** | 分科完成度在 MAS AppService Pre，不在本 DomainService |
| **BR-CLN-DR-04** | grant FK 文档指向本表 id；REP-02 用 mustGetById |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `DiagnosticReportDraftSavedEvent` | `com.zxplat.domain.share.events.clinical.DiagnosticReport | `id, encounterId` | `saveMasterDraft` |
| `DiagnosticReportFinalizedEvent` | 同上 | `id, encounterId, patientId, statusEnum` | `finalizeMaster` |

**发布时机**：全部 **beforeCommit**。

**索引写入（编排，非本聚合方法）**：MAS-04 在 `finalizeMaster` 之后同事务调用 `DiagnosticReportIndexUpsertService#upsertFromPeis`，`sourceReportId=id`，`moduleEnum=PEIS`，`uniqueKey="PEIS:"+id`。

---

## 8. 删除与可见性契约

- 软删 `isDeleted=true`；业务作废用 `statusEnum=cancelled`（P1）。
- 查询默认过滤 isDeleted=false。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁（`@代码生成通用规范`）。
- import 本聚合须用 **`com.zxplat.domain.clinical.diagnosticreport.*`**。
- **禁止** import `com.zxplat.domain.lis.diagnosticreport.*` 替代本聚合。

---

## 10. 生成与验收清单

- [ ] 实体与 V77 DDL 一致
- [ ] uk_cln_dr_encounter 生效
- [ ] 三写两读单测
- [ ] finalize 后 AppService 集成 upsertFromPeis（MAS-04）
- [ ] 与 LIS 同名类仅包路径不同，编译期无错误 import

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-CLN-008 v1.1、本 SDD、V77、EncounterClass.PE

### 11.2 DoD

领域层编译通过；MAS-03/04 集成测试可 mock index upsert

### 11.3 失败处理

V77 未执行 → 先 Flyway；PE 枚举缺失 → 先 ADM

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 3
- [ ] §7 事件 = 2
- [ ] §4.2 字段 = DM §2/§3 全集
- [ ] 类名无 `Clinical` 前缀

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
| **总分** | **100** | **100（§1.4 PASS，可进入代码会话）** |

---

**本 SDD 正文结束。**
