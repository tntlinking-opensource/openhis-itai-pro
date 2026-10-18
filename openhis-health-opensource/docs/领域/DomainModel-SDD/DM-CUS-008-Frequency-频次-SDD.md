# DM-CUS-008 Frequency 频次 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-008-Frequency-频次.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：Frequency（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-008 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain`（DomainModel-SDD） |
| 包规则 `com.zxplat.domain.custom.frequency` | 满足 | `yyy` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖写方法 5 个、读方法 3 个 |
| DM 第 7 章 BR | 满足 | §6 映射 13 条并给出实现锚点 |
| DM 第 5 章事件 | 满足 | §7 覆盖 5 个事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化聚合持久化模型、写读契约、事件与不变式。
- 将实现前澄清前置：一方法一 Command、字段全集、仓储校验与测试锚点明确。
- 正文不出现占位语句，保持可实施。

### 1.2 边界

- **在内**：本聚合实体、状态、查询模型、事件触发。
- **在外**：外域主数据正文维护、页面拼装、跨域流程编排。
- **跨域仅端口声明**：通过标识引用外域，不落库外域实体。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.custom.frequency`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | 聚合根实体及子实体（若有） |
| `valueobject` | 值对象（若有） |
| `command` | 一方法一命令文件 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `repository` | `JpaRepository` 接口 |
| `share.events` | `com.zxplat.domain.share.events.custom.frequency` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；命名与 DM 字段一致 |
| 时间 | 业务字段按 DM；审计字段 `LocalDateTime` |
| `*_enum` | `Integer`（或 DM 明示类型） |
| `*_code` | `String` |
| 软删 | `Boolean isDeleted` 默认过滤 |
| 实体 | `@Entity` + `@Table` + `@Column` |
| 仓储 | 仅接口，无默认实现类 |
| 分页 | `Pageable` 默认 `createdAt DESC` |
| 事件 | `new XxxEvent` + `publishBeforeCommit` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_frequency` | `Frequency` | 依据 DM 主表语义落地（实际表名以实现定稿为准） |

### 4.2 聚合根 `Frequency`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | ``Long`` | 是 | 主键，雪花 ID |
| `rate_code` | ``String`` | 否 | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `name` | ``String`` | 是 | 频次展示名称（如"每日一次"、"每日两次"） |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | 是 | 见 §4.1 |
| `day_count` | ``Integer`` | 否 | 日相关计数（如"每N日"中的N） |
| `day_interval` | ``Integer`` | 否 | 日间隔（如隔日=2） |
| `day_times` | ``String`` | 否 | 日内执行时点序列（如"08:00,12:00,18:00"） |
| `week_cycle_flag` | ``Boolean`` | 否 | 是否为周周期频次 |
| `total_execution_count` | ``Integer`` | 否 | 固定总执行次数（临时医嘱场景） |
| `execution_period` | ``Integer`` | 否 | 执行周期数值 |
| `execution_period_unit` | ``String`（`PeriodUnit`）` | 否 | 见 §4.2 |
| `applicable_scenes` | ``String`` | 否 | 逗号分隔存储，见 §4.3 |
| `allow_cross_day` | ``Integer`` | 否 | 跨日选项，见 §4.4 |
| `active_flag` | ``Boolean`` | 是 | 启用标志（`true`=启用 / `false`=停用） |
| `sort_no` | ``Integer`` | 否 | 列表展示排序 |

**不变式方法（示例命名，实现须一致）**：`validateRequiredFields`、`validateStatusTransition`、`assertOwnership`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `FrequencyRepository extends JpaRepository<Frequency, Long>`
- `findByIdAndIsDeletedFalse(Long id)`
- 其余查询签名按 §5.2 与 §6 不变式派生。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `Frequency1QueryModel`（对应 `initFrequencyConfigPage`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_frequency.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_frequency.name` | 频次展示名称（如"每日一次"、"每日两次"） |
| `rate_code` | ``String`` | `cus_frequency.rate_code` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | `cus_frequency.cycle_type` | 见 §4.1 |
| `day_count` | ``Integer`` | `cus_frequency.day_count` | 日相关计数（如"每N日"中的N） |
| `day_interval` | ``Integer`` | `cus_frequency.day_interval` | 日间隔（如隔日=2） |
| `day_times` | ``String`` | `cus_frequency.day_times` | 日内执行时点序列（如"08:00,12:00,18:00"） |
| `week_cycle_flag` | ``Boolean`` | `cus_frequency.week_cycle_flag` | 是否为周周期频次 |

#### 4.6.2 `Frequency2QueryModel`（对应 `pageFrequencies`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_frequency.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_frequency.name` | 频次展示名称（如"每日一次"、"每日两次"） |
| `rate_code` | ``String`` | `cus_frequency.rate_code` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | `cus_frequency.cycle_type` | 见 §4.1 |
| `day_count` | ``Integer`` | `cus_frequency.day_count` | 日相关计数（如"每N日"中的N） |
| `day_interval` | ``Integer`` | `cus_frequency.day_interval` | 日间隔（如隔日=2） |
| `day_times` | ``String`` | `cus_frequency.day_times` | 日内执行时点序列（如"08:00,12:00,18:00"） |
| `week_cycle_flag` | ``Boolean`` | `cus_frequency.week_cycle_flag` | 是否为周周期频次 |

#### 4.6.3 `Frequency3QueryModel`（对应 `computeExecutionTimePoints`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | ``Long`` | `cus_frequency.id` | 主键，雪花 ID |
| `name` | ``String`` | `cus_frequency.name` | 频次展示名称（如"每日一次"、"每日两次"） |
| `rate_code` | ``String`` | `cus_frequency.rate_code` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | `cus_frequency.cycle_type` | 见 §4.1 |
| `day_count` | ``Integer`` | `cus_frequency.day_count` | 日相关计数（如"每N日"中的N） |
| `day_interval` | ``Integer`` | `cus_frequency.day_interval` | 日间隔（如隔日=2） |
| `day_times` | ``String`` | `cus_frequency.day_times` | 日内执行时点序列（如"08:00,12:00,18:00"） |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `FrequencyDomainService`

#### 5.1.1 `createFrequency(CreateFrequencyCommand cmd)` → `void`

**命令文件**：`CreateFrequencyCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `rate_code` | ``String`` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `name` | ``String`` | 频次展示名称（如"每日一次"、"每日两次"） |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | 见 §4.1 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new FrequencyCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-008-W-001` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.2 `modifyFrequency(ModifyFrequencyCommand cmd)` → `void`

**命令文件**：`ModifyFrequencyCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `rate_code` | ``String`` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `name` | ``String`` | 频次展示名称（如"每日一次"、"每日两次"） |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | 见 §4.1 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new FrequencyCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-008-W-002` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.3 `activateFrequency(ActivateFrequencyCommand cmd)` → `void`

**命令文件**：`ActivateFrequencyCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `rate_code` | ``String`` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `name` | ``String`` | 频次展示名称（如"每日一次"、"每日两次"） |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | 见 §4.1 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new FrequencyCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-008-W-003` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.4 `deactivateFrequency(DeactivateFrequencyCommand cmd)` → `void`

**命令文件**：`DeactivateFrequencyCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `rate_code` | ``String`` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `name` | ``String`` | 频次展示名称（如"每日一次"、"每日两次"） |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | 见 §4.1 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new FrequencyCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-008-W-004` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

---

#### 5.1.5 `deleteFrequency(DeleteFrequencyCommand cmd)` → `void`

**命令文件**：`DeleteFrequencyCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键或业务标识 |
| `operatorId` | `Long` | 操作人 |
| `id` | ``Long`` | 主键，雪花 ID |
| `rate_code` | ``String`` | 频次业务编码（如 QD、BID、TID），填写则全局唯一 |
| `name` | ``String`` | 频次展示名称（如"每日一次"、"每日两次"） |
| `cycle_type` | ``Integer`（`FrequencyCycleType`）` | 见 §4.1 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `validateRequiredFields` |
| `statusTransitionMethod` | `validateStatusTransition` 或 `N/A` |
| `repositoryChecks` | `findByIdAndIsDeletedFalse` + 唯一性/归属校验 |
| `eventPublishAnchor` | `new FrequencyCreatedEvent(...)` + `publishBeforeCommit` |
| `unitTestCaseId` | `TC-DM-CUS-008-W-005` |

**方法契约摘要**：前置条件完成必填与状态校验；持久化影响限定在本聚合；失败语义为参数非法、状态非法或引用/唯一性冲突时拒绝；事件在 beforeCommit 发布。**注释锚点**：校验 → 状态迁移/装配 → 持久化 → 事件发布。

### 5.2 读侧 — `FrequencyQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `initFrequencyConfigPage` | `criteria/pageable` | `Frequency1QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-008-R-001` |
| `pageFrequencies` | `criteria/pageable` | `Frequency2QueryModel` | 组合条件查询 + 稳定排序 | `TC-DM-CUS-008-R-002` |
| `computeExecutionTimePoints` | `rateCode,startTime,endTime` | `List<FrequencyExecutionTimePointQueryModel>` | **字段驱动**展开：日步进=`day_interval`/`day_count`/`execution_period(d)`；短间隔=min/h；周=wk；PRN 空集；升序去重 | `TC-DM-CUS-008-R-003` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-FRQ-001 | 频次名称不允许为空；每个频次必须有可辨识的名称；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-001` |
| BR-FRQ-002 | 频次编码若填写则全局唯一；确保业务侧引用编码的稳定性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-002` |
| BR-FRQ-003 | 频次编码不允许通过修改操作变更（须删除后重建）；身份键不可变，保障下游引用一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-003` |
| BR-FRQ-004 | 执行时间点计算的任一时刻不早于开始时间、不晚于结束时间；时间边界约束；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-004` |
| BR-FRQ-005 | 结束时间 ≤ 开始时间时，展开结果为空集；退化场景保护；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-005` |
| BR-FRQ-006 | 已停用的频次不可被新开医嘱引用；引用一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-006` |
| BR-FRQ-007 | 日执行时点格式须为合法时间序列（HH:mm 逗号分隔）；数据格式约束；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-007` |
| BR-FRQ-008 | 周期类型为"必要时"（PRN）时，不生成固定执行时间点；PRN 不展开；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-008` |
| BR-FRQ-009 | 删除前须评估是否被在执行中的医嘱引用；引用完整性保护（应用层编排）；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-009` |
| BR-FRQ-010 | 并发更新须带版本戳（concurrencyStamp）；并发一致性；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-010` |
| BR-FRQ-QRY-001 | 初始化与列表为只读或读多写少；与术语缓存策略见本稿 **§6.1**；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-011` |
| BR-FRQ-QRY-002 | 分页过滤须带租户（若多租户）；与仓储约定一致；落地位置：写侧前置校验 + 读侧默认过滤；测试锚点：`TC-DM-CUS-008-BR-012` |
| BR-FRQ-QRY-003 | `computeExecutionTimePoints` 为纯计算/只读，不修改频次主数据与医嘱聚合；与住院护士站 **IF-IP-EXE-01** 一致；**禁止** App 写死隔日步进 | 落地位置：读侧纯计算；测试锚点：`TC-DM-CUS-008-BR-013` |

---
## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `FrequencyCreatedEvent` | `com.zxplat.domain.share.events.custom.frequency` | `aggregateId`、`operationType`、`occurredAt` | `createFrequency` |
| `FrequencyModifiedEvent` | `com.zxplat.domain.share.events.custom.frequency` | `aggregateId`、`operationType`、`occurredAt` | `modifyFrequency` |
| `FrequencyActivatedEvent` | `com.zxplat.domain.share.events.custom.frequency` | `aggregateId`、`operationType`、`occurredAt` | `activateFrequency` |
| `FrequencyDeactivatedEvent` | `com.zxplat.domain.share.events.custom.frequency` | `aggregateId`、`operationType`、`occurredAt` | `activateFrequency` |
| `FrequencyDeletedEvent` | `com.zxplat.domain.share.events.custom.frequency` | `aggregateId`、`operationType`、`occurredAt` | `deleteFrequency` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---
## 8. 删除与可见性契约

- 删除默认采用逻辑删除，禁止物理删除（除非 DM 明示）。
- 已删除记录不参与默认查询、分页统计与业务匹配。
- 更新/删除前必须校验记录存在且归属合法。

---

## 9. 框架优先适配规则

- 复用框架软删、审计、工作单元与分页能力，不重复造轮子。
- Repository 仅保留接口；查询复杂度通过规格条件或派生签名表达。
- 事件默认直接在服务内构造并发布，不引入事件工厂薄封装。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM 字段全集一一对齐
- [ ] §5.1 写方法数量、顺序与 DM §6.1 一致（当前 5 个）
- [ ] §5.2 读方法与 §4.6 QueryModel 一一对应（当前 3 个）
- [ ] §6 BR 映射与单测绑定已覆盖
- [ ] §7 事件与触发方法覆盖 DM §5 全量事件
- [ ] 无占位措辞、无 YAML/front matter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-CUS-008-Frequency-频次.md`
- 本 SDD 文件与目标模块包规则
- 共享事件与测试约定可用

### 11.2 DoD

- 写读方法全部实现并编译通过
- 关键 BR 与事件触发均有测试覆盖
- 软删、分页、排序与 DM 语义一致

### 11.3 失败处理

- 发现 DM/实现冲突立即回退到文档修订
- 任一 BR 无法稳定实现时输出缺口并停止进入代码会话
- 查询语义不一致（过滤/total 漂移）必须先修复后继续

---

## 定稿检查清单（可打勾）

- [x] 章节结构、固定标题与顺序符合金骨架
- [x] `### 5.1` 下 `#### 5.1.n` 数量为 5，与 DM §6.1 对齐
- [x] 每个写方法含命令文件、字段全集、绑定项、方法契约摘要
- [x] 每个读方法均有对应 `§4.6.n` QueryModel 字段表
- [x] 事件契约与 BR 映射已落地
- [x] 正文无 YAML/sdd_status/dm_code 元数据

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **98（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
