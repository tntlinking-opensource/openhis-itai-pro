# DM-LIS-002 Observation 检验结果 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-002-Observation-检验结果.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程：`zxplat``  
> **限界上下文**：检验域 · 结果管理  
> **聚合**：Observation（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-002 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.observation` | 满足 | `observation` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §5 领域事件 | 满足 | §7 —— ObservationPreliminaryRecorded / ObservationFinalized / ObservationAmended |
| DM §6 业务规则 | 满足 | §6 映射（BR-OBS-01~04），§5 写方法由 AS §4 编排推导 |
| DM 持久化形态 | 满足 | §4 字段表对齐 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Observation 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`lis_observation` 表内状态、字段、不变式、事件发布。
- **在外**：仪器结果 ingress（RES-01 AppService）、审核编排（AUD-001 AppService）、报告修订（REP-001 AppService）。
- **跨域仅端口声明**：对 LisWorkItem / health ServiceRequest 仅声明引用约束与前置校验。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.lis.observation`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Observation` |
| `valueobject` | 无（`referenceRange` 以 JSON String 存储，不拆为 Embeddable） |
| `enums`（共享） | `ObsStatus`（preliminary/final/amended/cancelled），复用 `com.zxplat.domain.share.enums` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `ObservationRepository`（仅 `JpaRepository` 接口） |
| `service` | `ObservationDomainService`（直接实现类） |
| `queries` | `ObservationQueryService`（直接实现类） |
| `queries.model` | `ObservationQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.lis.observation` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `workItemId`、`serviceRequestId`、`deviceId`、`parentObservationId`、`previousId` |
| 时间 | `effectiveTime` → `LocalDateTime`；`createdAt`/`updatedAt` → `LocalDateTime` |
| `*_enum` | `statusEnum` → `String`（存 ObsStatus 枚举 code） |
| `*_code` | `code`、`codeSystem`、`unitCode` → `String` |
| `_flag` | 无 |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 仓储 | `interface ObservationRepository extends JpaRepository<Observation, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；未指定排序时默认 `createdAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` |
| 事件 | 领域服务内 `new XxxEvent(...)` + `DomainEventPublisher.publish`，**beforeCommit**；禁止 `*EventFactory` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_observation` | `Observation` | 聚合根 · 单表 |

### 4.2 聚合根 `Observation`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `workItemId` | `Long` | 是 | 关联工单 |
| `serviceRequestId` | `Long` | 是 | 关联医嘱 |
| `codeSystem` | `String` | 否 | 编码体系（LOINC / 院内码） |
| `code` | `String` | 是 | 项目码 |
| `valueQuantity` | `BigDecimal` | 否 | 定量值 |
| `valueString` | `String` | 否 | 定性值 / 微生物描述 |
| `valueCodeableConcept` | `String` | 否 | 阴阳性等编码值 |
| `unitCode` | `String` | 否 | 单位码 |
| `referenceRange` | `String` (JSON) | 否 | 参考范围（low/high/text） |
| `interpretationCode` | `String` | 否 | 判读（N/H/L/HH/LL/A） |
| `statusEnum` | `String` | 是 | preliminary / final / amended / cancelled |
| `effectiveTime` | `LocalDateTime` | 否 | 检测时间 |
| `deviceId` | `Long` | 否 | 仪器实例 |
| `reagentLotNumber` | `String` | 否 | 试剂批号（P2） |
| `previousId` | `Long` | 否 | 修订前版本（REVISE 场景） |
| `noteText` | `String` | 否 | 备注 |
| `parentObservationId` | `Long` | 否 | 组合项父引用 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

**不变式方法**：无（纯数据记录，无聚合内生命周期方法）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface ObservationRepository extends JpaRepository<Observation, Long> {
    Optional<Observation> findByWorkItemIdAndServiceRequestIdAndCodeAndIsDeletedFalse(Long workItemId, Long serviceRequestId, String code);
    List<Observation> findByWorkItemIdAndIsDeletedFalse(Long workItemId);
    List<Observation> findByServiceRequestIdAndIsDeletedFalse(Long serviceRequestId);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ObservationQueryModel`（对应 `searchObservations`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_observation.id` | |
| `workItemId` | `Long` | `lis_observation.work_item_id` | |
| `serviceRequestId` | `Long` | `lis_observation.service_request_id` | |
| `code` | `String` | `lis_observation.code` | |
| `valueQuantity` | `BigDecimal` | `lis_observation.value_quantity` | |
| `valueString` | `String` | `lis_observation.value_string` | |
| `unitCode` | `String` | `lis_observation.unit_code` | |
| `interpretationCode` | `String` | `lis_observation.interpretation_code` | |
| `statusEnum` | `String` | `lis_observation.status_enum` | |
| `effectiveTime` | `LocalDateTime` | `lis_observation.effective_time` | |
| `createdAt` | `LocalDateTime` | `lis_observation.created_at` | |

#### 4.6.2 `Observation`（对应 `getByWorkItemIdAndCode`）

直接返回 `Observation` 实体，无独立 QueryModel。

#### 4.6.3 `List<Observation>`（对应 `getByWorkItemId`）

直接返回实体列表，无独立 QueryModel。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ObservationDomainService`

#### 5.1.1 `recordPreliminary(RecordPreliminaryCommand cmd)` → `Observation`

**命令文件**：`RecordPreliminaryCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `serviceRequestId` | `Long` | 必填 |
| `code` | `String` | 必填，项目码 |
| `valueQuantity` | `BigDecimal` | 定量值（与 valueString 二选一） |
| `valueString` | `String` | 定性值 |
| `unitCode` | `String` | 单位码 |
| `referenceRange` | `String` | 参考范围 JSON |
| `interpretationCode` | `String` | 判读码 |
| `effectiveTime` | `LocalDateTime` | 检测时间 |
| `deviceId` | `Long` | 仪器实例 |
| `tenantId` | `Long` | 必填 |
| `orgId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无（纯数据创建/更新） |
| `statusTransitionMethod` | 新建 → `preliminary`；已存在则 update |
| `repositoryChecks` | BR-OBS-01：按 (workItemId, serviceRequestId, code) 查重，存在则 update |
| `eventPublishAnchor` | `ObservationPreliminaryRecordedEvent`（新建时） |
| `unitTestCaseId` | TC-OBS-001 |

**方法契约摘要**：前置：无；持久化：存在则 update 值 + 记录 editLog；不存在则 insert preliminary；事件：新建时发布 ObservationPreliminaryRecordedEvent；**注释锚点**：查重 → update/insert → 发布事件。

---

#### 5.1.2 `finalize(FinalizeObservationCommand cmd)` → `Observation`

**命令文件**：`FinalizeObservationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `observationId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `statusTransitionMethod` | `preliminary` → `final` |
| `repositoryChecks` | BR-OBS-02：前置状态 = preliminary |
| `eventPublishAnchor` | `ObservationFinalizedEvent` |
| `unitTestCaseId` | TC-OBS-002 |

**方法契约摘要**：前置：statusEnum = preliminary；持久化：statusEnum → final；事件：ObservationFinalizedEvent；**注释锚点**：加载 → 状态门禁 → 审核通过 → 发布事件。

---

#### 5.1.3 `amend(AmendObservationCommand cmd)` → `Observation`

**命令文件**：`AmendObservationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `observationId` | `Long` | 必填 |
| `newValueQuantity` | `BigDecimal` | 修订后的定量值 |
| `newValueString` | `String` | 修订后的定性值 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `statusTransitionMethod` | `final` → `amended` |
| `repositoryChecks` | BR-OBS-03：previousId 指向前版 |
| `eventPublishAnchor` | `ObservationAmendedEvent` |
| `unitTestCaseId` | TC-OBS-003 |

**方法契约摘要**：前置：statusEnum = final；持久化：原 Observation → amended + previousId 指向前版；新建 Observation(amended) 含新值；事件：ObservationAmendedEvent；**注释锚点**：加载 → 状态门禁 → 版本链 → 发布事件。

---

### 5.2 读侧 — `ObservationQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchObservations` | `ObservationQuery` | `Page<ObservationQueryModel>` | 分页 | TC-OBS-R01 |
| `getByWorkItemId` | `Long workItemId` | `List<Observation>` | 全量返回 | TC-OBS-R02 |
| `getByWorkItemIdAndCode` | `Long workItemId, String code` | `Observation` | 唯一查询 | TC-OBS-R03 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL 调用 → 组装 QueryModel。

---

## 6. 不变式映射（DM §6）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-OBS-01** | `recordPreliminary` 时查重：(workItemId, serviceRequestId, code) 存在则 update |
| **BR-OBS-02** | `finalize` 前置状态门禁：preliminary → final |
| **BR-OBS-03** | `amend` 前置版本链：previousId 指向前版 |
| **BR-OBS-04** | 危急值触发：interpretationCode = HH/LL + 阈值超标 → 创建 DetectedIssue（由 AUD AppService 编排） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 最小载荷 | 触发方法 |
| ---- | ------ | -------- | -------- |
| `ObservationPreliminaryRecordedEvent` | `com.zxplat.domain.share.events.lis.observation` | `id, workItemId, serviceRequestId, code, valueQuantity/valueString` | `recordPreliminary` |
| `ObservationFinalizedEvent` | `com.zxplat.domain.share.events.lis.observation` | `id, workItemId, serviceRequestId, statusEnum` | `finalize` |
| `ObservationAmendedEvent` | `com.zxplat.domain.share.events.lis.observation` | `id, previousId, serviceRequestId` | `amend` |

---

## 8. 删除与可见性契约

- 软删：`isDeleted = true`，不物理删除。
- 查询默认过滤 `isDeleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 事务由 AppService `AppServiceAspect` / `ReadOnlyTransactionAspect` 管理。
- 唯一约束：`(workItemId, serviceRequestId, code)` 联合索引。

---

## 10. 生成与验收清单

- [ ] `Observation` 实体 + 字段与 §4.2 一致
- [ ] 4 条 BR 不变式正确实现
- [ ] 3 个领域事件发布
- [ ] Repository 无 `@Query`
- [ ] 读侧 3 个方法可用

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-002-Observation-检验结果.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- `Observation` 实体编译通过。
- 全部 3 个写方法功能正确。
- 事件发布与 beforeCommit 集成正确。

### 11.3 失败处理

- 状态迁移冲突 → 回文档修订。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] §5.1 子节数量 = 3
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
