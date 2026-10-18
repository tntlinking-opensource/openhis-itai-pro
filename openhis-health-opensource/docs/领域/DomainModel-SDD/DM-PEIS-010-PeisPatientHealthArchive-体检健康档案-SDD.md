# DM-PEIS-010 PeisPatientHealthArchive 体检健康档案 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-010-PeisPatientHealthArchive-体检健康档案.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-peis/module-peis-domain`  
> **限界上下文**：health · peis · 体检健康档案  
> **聚合**：PeisPatientHealthArchive（单表，无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PEIS-010 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-peis-domain` |
| 包规则 `com.zxplat.plugin.peis.domain.healtharchive` | 满足 | |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit 等） | 满足 | 见 §9 |
| DM 附录写方法 ×2 | 满足 | §5.1.1～5.1.2 |
| DM §7 BR ×2 | 满足 | §6 |
| DM §5 事件 | 满足 | 无独立事件，§7 空表 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在租户内按 `patientId` 维护体检侧健康史摘要（既往/家族/习惯/过敏摘要/标签），支撑登记与总检展示。
- **upsert** 保证同一租户+患者仅一条有效档案（BR-ARCH-001）。
- 模式 B 已实现：`PeisHealthArchiveDomainService` 为唯一写侧；无独立 QueryService。

### 1.2 边界

- **在内**：表 `peis_patient_health_archive` 及读写服务。
- **在外**：正式过敏域 `AllergyIntolerance`；总检结论写入 DR 聚合。
- **跨域仅端口声明**：Patient 存在性由应用层校验；本聚合仅存 `patientId` 外键。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-peis/module-peis-domain`。

### 2.2 包结构（`com.zxplat.plugin.peis.domain.healtharchive`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisPatientHealthArchive` |
| `command` | 模式 B：`upsert` 以实体入参为主；可选补 `UpsertPeisHealthArchiveCommand` 与实现对齐 |
| `repository` | `PeisPatientHealthArchiveRepository` |
| `service` | `PeisHealthArchiveDomainService` |
| `queries` | 无（读由 DomainService.findByPatientId） |
| `queries.model` | 无独立 QueryModel；读路径返回实体 |

**存储口径**：`tag_code` → `String`（字典码）；摘要字段均为 `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| 时间 | 审计字段 `LocalDateTime` |
| `*_code` | `String` |
| 软删 | `Boolean isDeleted`，继承 `BaseAuditedEntity` |
| 实体 | `@Entity` + `@Table(name = "peis_patient_health_archive")` |
| 仓储 | `JpaRepository` 接口，无自定义实现类 |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 本聚合无独立领域事件 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `peis_patient_health_archive` | `PeisPatientHealthArchive` | 租户内每患者一条有效档案 |

### 4.2 聚合根 `PeisPatientHealthArchive`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 否 | 机构 |
| `patientId` | `Long` | 是 | 患者；BR-ARCH-001 唯一键之一 |
| `pastHistory` | `String` | 否 | 既往史摘要 |
| `familyHistory` | `String` | 否 | 家族史摘要 |
| `habitSummary` | `String` | 否 | 习惯摘要 |
| `allergySummary` | `String` | 否 | 过敏摘要（BR-ARCH-002：不替代正式过敏域） |
| `tagCode` | `String` | 否 | 体检标签编码 |
| `remark` | `String` | 否 | 备注 |
| `isDeleted` | `Boolean` | 系统 | 软删 |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式方法（示例命名）**：无独立迁态；upsert 内保证 tenant+patient 唯一。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `PeisPatientHealthArchiveRepository extends JpaRepository<PeisPatientHealthArchive, Long>`
- `Optional<PeisPatientHealthArchive> findByTenantIdAndPatientIdAndIsDeletedFalse(Long tenantId, Long patientId)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `findByPatientId`（`PeisHealthArchiveDomainService`）

直接返回 `PeisPatientHealthArchive` 实体，无独立 QueryModel；字段见 §4.2。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PeisHealthArchiveDomainService`

#### 5.1.1 `findByPatientId(Long patientId)` → `Optional<PeisPatientHealthArchive>`

**命令文件**：N/A（只读入参；方法落在 DomainService 供应用层复用）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByTenantIdAndPatientIdAndIsDeletedFalse` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-ARCH-R01 |

**方法契约摘要**：前置：`TenantUtils.getCurrentTenantId()` 非空；持久化：无；失败：tenant/patient 为空返回 `Optional.empty()`；**注释锚点**：取 tenant → 仓储查询 → 返回 Optional。

---

#### 5.1.2 `upsert(PeisPatientHealthArchive incoming, Long operatorId)` → `PeisPatientHealthArchive`

**命令文件**：N/A（模式 B：`PeisPatientHealthArchive` 字段 patch + `operatorId`）

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 必填 |
| `orgId` | `Long` | 可选 |
| `pastHistory` | `String` | 可选 |
| `familyHistory` | `String` | 可选 |
| `habitSummary` | `String` | 可选 |
| `allergySummary` | `String` | 可选 |
| `tagCode` | `String` | 可选 |
| `remark` | `String` | 可选 |
| `operatorId` | `Long` | 审计 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | BR-ARCH-001：按 tenant+patient 加载或新建 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByTenantIdAndPatientIdAndIsDeletedFalse` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-ARCH-W01 |

**方法契约摘要**：前置：`patientId` 非空；持久化：存在则覆盖摘要字段，不存在则 insert；失败：无静默跳过；**注释锚点**：查已有 → 合并字段 → touch 审计 → save。

---

### 5.2 读侧 — （无 QueryService）

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| （无） | — | 读由 §5.1.1 承担 | — | — |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-ARCH-001** | `upsert` 以 `tenantId+patientId` 查唯一行，禁止第二行有效档案 |
| **BR-ARCH-002** | 仅维护 `allergySummary` 文本；不写 AllergyIntolerance |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |

（DM minimal：无独立领域事件类；写操作以事务内持久化为准。）

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 默认 `isDeleted=false`；本聚合未暴露物理删 API。
- `findByPatientId` / upsert 均过滤已删行。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；租户取自 `TenantUtils`。
- Repository 禁手写 JPQL（除非项目 Query 层例外）。

---

## 10. 生成与验收清单

- [ ] 实体字段与 §4.2 一致
- [ ] upsert 单测覆盖新建与更新
- [ ] tenant+patient 唯一集成测
- [ ] 无 QueryService 漂移

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-PEIS-010、本 SDD、Flyway `peis_patient_health_archive`

### 11.2 DoD

- `module-peis-domain` 编译通过；TC-PEIS-ARCH-W01/R01 绿

### 11.3 失败处理

- Patient FK 未建 → 应用层校验或文档标注依赖

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 2
- [ ] §7 事件 = 0
- [ ] 字段表与 DM §3 对齐

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
