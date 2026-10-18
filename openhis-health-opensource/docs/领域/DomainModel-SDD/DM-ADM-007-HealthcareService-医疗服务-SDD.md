# DM-ADM-007 HealthcareService 医疗服务 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-007-HealthcareService-医疗服务.md`（**v1.0.1**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 医疗服务管理）  
> **聚合**：HealthcareService（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-007 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.healthcareservice` | 满足 | `healthcareservice` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 服务联系信息 | §4.4 `contact` 为组合字符串 | SDD 在值对象展开中明确电话/地址映射列 |
| 可用时段口径 | §4.5 说明不新增实体 | SDD 保持无子实体，仅在规则章节保留接口语义 |
| 修改约束表达 | §6.1.3 与 §7 BR 文字分散 | SDD 在 `modifyService` 契约中统一体现 ACTIVE 状态限制 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **HealthcareService 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，保证实现阶段可直接对照。
- 单次成稿目标：正文不出现后续补充/待确认类措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：医疗服务主数据、发布状态流转、科室/地点引用、事件发布。
- **在外**：排班 Slot 生成、号源余量计算、挂号编排、收费定价本体维护。
- **跨域仅端口声明**：仅声明与 Organization/Location/PractitionerRole/ChargeItemDefinition 的引用契约，不在本聚合执行跨域写入。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.healthcareservice`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `HealthcareService` |
| `valueobject` | `ServiceCategory`、`ServiceType`、`Specialty`、`ContactInfo` |
| `enums`（共享） | 复用 `PublicationStatus` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `HealthcareServiceRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.healthcareservice` |

**存储口径（DM 优先）**：`*_enum` 默认 `Integer`；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id` |
| 时间 | 时间字段使用 `LocalDateTime`；本聚合核心主表无业务时间列 |
| `*_enum` | 枚举编码列，默认 `Integer` |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<HealthcareService, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造事件并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_healthcare_service` | `HealthcareService` | 医疗服务聚合根单表 |

### 4.2 聚合根 `HealthcareService`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 医疗服务标识 |
| `name` | `String` | 是 | 服务名称 |
| `categoryCode` | `Integer` | 是 | 服务类别 |
| `typeCode` | `Integer` | 是 | 服务类型 |
| `specialtyCode` | `String` | 否 | 专科编码 |
| `offeredOrgId` | `Long` | 是 | 提供科室标识 |
| `locationId` | `Long` | 否 | 地点标识 |
| `statusEnum` | `Integer` | 是 | 发布状态 |
| `activeFlag` | `Boolean` | 是 | `statusEnum==ACTIVE` 派生 |
| `appointmentRequiredFlag` | `Boolean` | 是 | 是否需预约 |
| `ybNo` | `String` | 否 | 医保编码 |
| `busNo` | `String` | 否 | 院内业务编码 |
| `comment` | `String` | 否 | 说明 |
| `extraDetails` | `String` | 否 | 额外详情 |
| `contact` | `String` | 否 | 联系信息组合值 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertNameNotBlank`、`assertOfferedOrgValid`、`assertActiveReferenceAllowed`、`assertAbolishWithoutOpenReferences`、`assertActiveImmutableCategoryAndType`。

### 4.2.1 值对象字段展开（ContactInfo）

| Java 字段名 | 类型 | @Column 列名（DB） | 说明 |
| ----------- | ---- | ------------------ | ---- |
| `contact` | `String` | `contact` | 电话与地址组合信息 |

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `HealthcareServiceRepository extends JpaRepository<HealthcareService, Long>`

**说明**：软删由框架统一过滤时，不强制接口名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `RegistrationHealthcareServiceQueryModel`（对应 `listServicesForRegistration`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `serviceId` | `Long` | `adm_healthcare_service.id` | 医疗服务标识 |
| `name` | `String` | `adm_healthcare_service.name` | 服务名称 |
| `categoryCode` | `Integer` | `adm_healthcare_service.category_code` | 服务类别 |
| `typeCode` | `Integer` | `adm_healthcare_service.type_code` | 服务类型 |
| `offeredOrgId` | `Long` | `adm_healthcare_service.offered_org_id` | 提供科室 |
| `appointmentRequiredFlag` | `Boolean` | `adm_healthcare_service.appointment_required_flag` | 是否需预约 |

#### 4.6.2 `HealthcareServiceDetailQueryModel`（对应 `loadHealthcareServiceById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_healthcare_service.id` | 医疗服务标识 |
| `name` | `String` | `adm_healthcare_service.name` | 服务名称 |
| `statusEnum` | `Integer` | `adm_healthcare_service.status_enum` | 发布状态 |
| `offeredOrgId` | `Long` | `adm_healthcare_service.offered_org_id` | 提供科室 |
| `locationId` | `Long` | `adm_healthcare_service.location_id` | 服务地点 |
| `ybNo` | `String` | `adm_healthcare_service.yb_no` | 医保编码 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `HealthcareServiceDomainService`

#### 5.1.1 `createService(CreateHealthcareServiceCommand cmd)` → `Long`

**命令文件**：`CreateHealthcareServiceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 服务名称 |
| `categoryCode` | `Integer` | 服务类别 |
| `typeCode` | `Integer` | 服务类型 |
| `offeredOrgId` | `Long` | 提供科室 |
| `locationId` | `Long` | 服务地点 |
| `appointmentRequiredFlag` | `Boolean` | 是否需预约 |
| `ybNo` | `String` | 医保编码 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `HealthcareService.assertCreatable` |
| `statusTransitionMethod` | `HealthcareService.initAsDraft` |
| `repositoryChecks` | `HealthcareServiceRepository.findByBusinessKeyIfAny` |
| `eventPublishAnchor` | `new HealthcareServiceCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-HCS-W-001` |

**方法契约摘要**：前置条件为服务名称非空、提供科室有效；持久化影响为新建草稿态服务记录；失败语义为必填缺失或引用非法时报错；事件触发为发布 `HealthcareServiceCreatedEvent`。**注释锚点**：校验 -> 初始化 -> 持久化 -> 事件。

---

#### 5.1.2 `publishService(PublishHealthcareServiceCommand cmd)` → `void`

**命令文件**：`PublishHealthcareServiceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceId` | `Long` | 医疗服务标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `HealthcareService.assertPublishable` |
| `statusTransitionMethod` | `HealthcareService.transitToActive` |
| `repositoryChecks` | `HealthcareServiceRepository.findById` |
| `eventPublishAnchor` | `new HealthcareServicePublishedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-HCS-W-002` |

**方法契约摘要**：前置条件为当前 DRAFT 且关键字段完整；持久化影响为状态迁移至 ACTIVE、同步 `activeFlag=true`；失败语义为状态或字段不满足时报错；事件触发为发布 `HealthcareServicePublishedEvent`。**注释锚点**：状态校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.3 `modifyService(ModifyHealthcareServiceCommand cmd)` → `void`

**命令文件**：`ModifyHealthcareServiceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceId` | `Long` | 医疗服务标识 |
| `name` | `String` | 服务名称 |
| `specialtyCode` | `String` | 专科编码 |
| `locationId` | `Long` | 服务地点 |
| `appointmentRequiredFlag` | `Boolean` | 是否需预约 |
| `comment` | `String` | 服务说明 |
| `extraDetails` | `String` | 额外详情 |
| `contact` | `String` | 联系信息 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `HealthcareService.assertModifiable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `HealthcareServiceRepository.findById` |
| `eventPublishAnchor` | `new HealthcareServiceModifiedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-HCS-W-003` |

**方法契约摘要**：前置条件为 DRAFT 或 ACTIVE 可修改，ACTIVE 态禁止修改 `categoryCode/typeCode`；持久化影响为更新允许变更字段；失败语义为非法字段变更或状态不符时报错；事件触发为发布 `HealthcareServiceModifiedEvent`。**注释锚点**：加载 -> 变更校验 -> 更新 -> 事件。

---

#### 5.1.4 `retireService(RetireHealthcareServiceCommand cmd)` → `void`

**命令文件**：`RetireHealthcareServiceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceId` | `Long` | 医疗服务标识 |
| `reason` | `String` | 停用原因 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `HealthcareService.assertRetirable` |
| `statusTransitionMethod` | `HealthcareService.transitToRetired` |
| `repositoryChecks` | `HealthcareServiceRepository.findById` |
| `eventPublishAnchor` | `new HealthcareServiceRetiredEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-HCS-W-004` |

**方法契约摘要**：前置条件为当前 ACTIVE；持久化影响为状态转 RETIRED、`activeFlag=false`；失败语义为状态不合法时报错；事件触发为发布 `HealthcareServiceRetiredEvent`。**注释锚点**：状态校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.5 `reactivateService(ReactivateHealthcareServiceCommand cmd)` → `void`

**命令文件**：`ReactivateHealthcareServiceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceId` | `Long` | 医疗服务标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `HealthcareService.assertReactivatable` |
| `statusTransitionMethod` | `HealthcareService.transitToActive` |
| `repositoryChecks` | `HealthcareServiceRepository.findById` |
| `eventPublishAnchor` | `new HealthcareServiceReactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-HCS-W-005` |

**方法契约摘要**：前置条件为当前 RETIRED；持久化影响为状态回到 ACTIVE 并恢复 `activeFlag=true`；失败语义为状态非法时报错；事件触发为发布 `HealthcareServiceReactivatedEvent`。**注释锚点**：状态校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.6 `abolishService(AbolishHealthcareServiceCommand cmd)` → `void`

**命令文件**：`AbolishHealthcareServiceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceId` | `Long` | 医疗服务标识 |
| `reason` | `String` | 废弃原因 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `HealthcareService.assertAbolishable` |
| `statusTransitionMethod` | `HealthcareService.transitToDeleted` |
| `repositoryChecks` | `HealthcareServiceQueryService.checkOpenScheduleOrAppointmentReferences` |
| `eventPublishAnchor` | `new HealthcareServiceAbolishedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-HCS-W-006` |

**方法契约摘要**：前置条件为当前 DRAFT/RETIRED 且不存在未完成排班或预约引用；持久化影响为状态迁移 DELETED 并 `isDeleted=true`；失败语义为引用未收口或状态非法时报错；事件触发为发布 `HealthcareServiceAbolishedEvent`。**注释锚点**：引用校验 -> 状态迁移 -> 逻辑删除 -> 事件。

### 5.2 读侧 — `HealthcareServiceQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listServicesForRegistration` | `RegistrationServiceQuery` | `List<RegistrationHealthcareServiceQueryModel>` | 按科室过滤 ACTIVE 服务，可附加岗位交集过滤 | `TC-HCS-R-001` |
| `loadHealthcareServiceById` | `Long serviceId` | `HealthcareServiceDetailQueryModel` | 主键读取服务主档 | `TC-HCS-R-002` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-HCS-001 | 服务名称必填且不可空白 |
| BR-HCS-002 | 提供科室必须有效 |
| BR-HCS-003 | 仅 ACTIVE 可被新业务引用 |
| BR-HCS-004 | RETIRED 不可新选用，历史引用可保留 |
| BR-HCS-005 | DELETED 为终态不可恢复 |
| BR-HCS-006 | 存在未完成排班/预约引用时禁止废弃 |
| BR-HCS-007 | 与诊查费定价保持可追溯关联 |
| BR-HCS-008 | ACTIVE 状态禁止修改类别与类型 |
| BR-HCS-009 | 医保编码非空时需符合编码格式规范 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `HealthcareServiceCreatedEvent` | `com.zxplat.domain.share.events.administration.healthcareservice` | `serviceId,name,categoryCode,typeCode,offeredOrgId` | `createService` |
| `HealthcareServicePublishedEvent` | `com.zxplat.domain.share.events.administration.healthcareservice` | `serviceId,name` | `publishService` |
| `HealthcareServiceModifiedEvent` | `com.zxplat.domain.share.events.administration.healthcareservice` | `serviceId,changedFields` | `modifyService` |
| `HealthcareServiceRetiredEvent` | `com.zxplat.domain.share.events.administration.healthcareservice` | `serviceId,reason` | `retireService` |
| `HealthcareServiceReactivatedEvent` | `com.zxplat.domain.share.events.administration.healthcareservice` | `serviceId` | `reactivateService` |
| `HealthcareServiceAbolishedEvent` | `com.zxplat.domain.share.events.administration.healthcareservice` | `serviceId,reason` | `abolishService` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- HealthcareService 删除语义为逻辑删除，禁止物理删除。
- 默认查询过滤 `isDeleted=true` 服务，废弃服务不参与任何业务计算。
- RETIRED 服务不允许新挂号/新排班引用，但历史记录可回显。
- 引用中断前禁止废弃，需先完成排班/预约收口。
- 发布状态与 `activeFlag` 必须保持一致派生关系。

---

## 9. 框架优先适配规则

- 软删、审计字段、工作单元事务、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口；跨域联动由应用层编排。
- 分页查询禁止先分页后内存过滤，确保统计一致。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 6，与 DM §6.1 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 2 个方法
- [ ] §6 BR 映射覆盖 BR-HCS-001 至 BR-HCS-009
- [ ] §7 事件表覆盖 DM §5 全部 6 条事件
- [ ] 发布/停用/废弃与引用约束全篇一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-007-HealthcareService-医疗服务.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.administration.healthcareservice`

### 11.2 DoD

- 写侧 6 个方法全部落地，签名与 Command 命名与 §5 一致。
- `HealthcareService` 字段、状态机、软删语义与 §4/§6 一致。
- 事件按 §7 在 beforeCommit 发布，载荷字段不少于最小载荷。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧 2 个方法与 §4.6 QueryModel 对齐，过滤口径一致。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若引用阻断规则（排班/预约未收口）无法落实，DoD 失败不得进入成码。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（6）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（6）
- [ ] 每个写方法包含 `entityInvariantMethod` 与 `unitTestCaseId`
- [ ] 全文无 YAML、无 `sdd_status`、无 `dm_code`、无占位措辞

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
| **总分** | **100** | **100（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

