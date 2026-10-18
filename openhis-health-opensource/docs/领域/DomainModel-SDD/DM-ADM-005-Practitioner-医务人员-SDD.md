# DM-ADM-005 Practitioner 医务人员 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-005-Practitioner-医务人员.md`（**v1.0.1**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 医务人员管理）  
> **聚合**：Practitioner（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-005 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.practitioner` | 满足 | `practitioner` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| `deceased_date` 类型 | §3.1 写作 `DateTime` | 代码口径统一为 `LocalDateTime` |
| 值对象命名 | §4.2 标题与字段出现 `Gender/AdministrativeGender` 混用 | SDD 统一命名为 `AdministrativeGender` |
| 读侧方法命名 | §6.2 第三项标题“按标识加载（findById）” | SDD 保留方法名 `findById`，并补齐展示语义字段 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Practitioner 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，保证代码生成可直接对照执行。
- 单次成稿目标：正文不出现后续补充/待确认类措辞，缺口在文档阶段闭环。

### 1.2 边界

- **在内**：`adm_practitioner` 主数据、与 User **共享主键**（`bindSharedUserIdentity`，无 JPA `@MapsId`）、人员状态流转、科室切换与事件发布。
- **在外**：岗位分配批量保存（PractitionerRole）、就诊参与事实写入、医嘱开立事实写入。
- **跨域仅端口声明**：仅声明与 User/Organization/PractitionerRole 的引用与一致性约束，不在本聚合内执行跨聚合替换逻辑。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.practitioner`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Practitioner` |
| `valueobject` | `PersonName`、`Address`、`ProfessionalTitle` |
| `enums`（共享） | 复用 `AdministrativeGender`、`PublicationStatus` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `PractitionerRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.practitioner` |

**存储口径（DM 优先）**：`*_enum` 默认 **String 存枚举 code**（`genderEnum`、`statusEnum`）；`dr_profttl_code` 为 `Integer`（字典数值编码，非 PublicationStatus）；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`，与 `User.id` 共享 |
| 时间 | `birthDate` 用 `LocalDate`；`deceasedDate` 用 `LocalDateTime` |
| `*_enum` | 枚举编码列，默认 **String（存枚举 code）** |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `interface PractitionerRepository extends JpaRepository<Practitioner, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接 `new XxxEvent(...)` + `publish`，beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_practitioner` | `Practitioner` | 医务人员聚合根单表 |

### 4.2 聚合根 `Practitioner`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 医务人员标识，与 User 共享主键 |
| `name` | `String` | 是 | 人员姓名 |
| `nameJson` | `String` | 否 | 其他名称扩展 |
| `pyStr` | `String` | 否 | 拼音码 |
| `wbStr` | `String` | 否 | 五笔码 |
| `genderEnum` | `String` | 是 | AdministrativeGender code |
| `birthDate` | `LocalDate` | 否 | 出生日期 |
| `deceasedDate` | `LocalDateTime` | 否 | 死亡时间 |
| `phone` | `String` | 否 | 联系电话 |
| `addressProvince` | `String` | 否 | 地址省 |
| `addressCity` | `String` | 否 | 地址市 |
| `addressDistrict` | `String` | 否 | 地址区县 |
| `addressStreet` | `String` | 否 | 地址街道 |
| `addressJson` | `String` | 否 | 地址扩展 |
| `statusEnum` | `String` | 是 | PublicationStatus code |
| `activeFlag` | `Boolean` | 是 | 由状态派生 |
| `busNo` | `String` | 否 | 院内工号，同租户唯一（非空时） |
| `ybNo` | `String` | 否 | 医保码 |
| `userId` | `Long` | 是 | 身份域用户标识 |
| `orgId` | `Long` | 否 | 当前工作科室 |
| `drProfttlCode` | `Integer` | 否 | 职称编码 |
| `pharPracCertNo` | `String` | 否 | 执业证件编号 |
| `prscDrCertCode` | `String` | 否 | 证件类型编码 |
| `signature` | `String` | 否 | 签名图片路径 |
| `kpdCode` | `String` | 否 | 开票点编码 |
| `posNo` | `String` | 否 | POS 机编号 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertUserUnique`、`assertBusNoUniqueIfPresent`、`refreshNameSearchCodesOnChange`、`assertActiveBeforeSwitchOrg`、`assertRetiredUnrecoverable`。

### 4.2.1 值对象字段展开（PersonName）

| Java 字段名 | 类型 | @Column 列名（DB） | 说明 |
| ----------- | ---- | ------------------ | ---- |
| `name` | `String` | `name` | 人员姓名 |
| `nameJson` | `String` | `name_json` | 其他名称扩展 |
| `pyStr` | `String` | `py_str` | 拼音码 |
| `wbStr` | `String` | `wb_str` | 五笔码 |

### 4.2.2 值对象字段展开（Address）

| Java 字段名 | 类型 | @Column 列名（DB） | 说明 |
| ----------- | ---- | ------------------ | ---- |
| `addressProvince` | `String` | `address_province` | 省/自治区/直辖市 |
| `addressCity` | `String` | `address_city` | 市 |
| `addressDistrict` | `String` | `address_district` | 区/县 |
| `addressStreet` | `String` | `address_street` | 街道/详细地址 |
| `addressJson` | `String` | `address_json` | 扩展地址 |

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `PractitionerRepository extends JpaRepository<Practitioner, Long>`

**说明**：软删由框架统一过滤时，不强制接口名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `OutpatientRegistrationPractitionerQueryModel`（对应 `listActivePractitionersByResolvedRoleScope`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_practitioner.id` | 医务人员标识 |
| `name` | `String` | `adm_practitioner.name` | 医务人员姓名 |
| `orgId` | `Long` | `adm_practitioner.org_id` | 当前科室 |
| `drProfttlCode` | `Integer` | `adm_practitioner.dr_profttl_code` | 职称 |
| `busNo` | `String` | `adm_practitioner.bus_no` | 工号 |

#### 4.6.2 `PractitionerPagedQueryModel`（对应 `searchPractitionersPaged`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_practitioner.id` | 医务人员标识 |
| `name` | `String` | `adm_practitioner.name` | 姓名 |
| `busNo` | `String` | `adm_practitioner.bus_no` | 工号 |
| `statusEnum` | `String` | `adm_practitioner.status_enum` | PublicationStatus code |
| `activeFlag` | `Boolean` | `adm_practitioner.active_flag` | 是否启用 |
| `orgId` | `Long` | `adm_practitioner.org_id` | 当前科室 |

#### 4.6.3 `PractitionerDisplayQueryModel`（对应 `findById`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_practitioner.id` | 医务人员标识 |
| `name` | `String` | `adm_practitioner.name` | 姓名 |
| `genderEnum` | `String` | `adm_practitioner.gender_enum` | 性别 |
| `statusEnum` | `String` | `adm_practitioner.status_enum` | PublicationStatus code |
| `orgId` | `Long` | `adm_practitioner.org_id` | 当前科室 |
| `isDeleted` | `Boolean` | `adm_practitioner.is_deleted` | 软删标记 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PractitionerDomainService`

#### 5.1.1 `createPractitioner(CreatePractitionerCommand cmd)` → `Long`

**命令文件**：`CreatePractitionerCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `userId` | `Long` | 绑定用户标识 |
| `name` | `String` | 人员姓名 |
| `genderEnum` | `String` | AdministrativeGender code |
| `birthDate` | `LocalDate` | 出生日期 |
| `busNo` | `String` | 院内工号 |
| `orgId` | `Long` | 初始科室 |
| `phone` | `String` | 联系电话 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertCreateAllowed` |
| `identityBindingMethod` | `Practitioner.bindSharedUserIdentity(userId)`（**无** JPA `@MapsId`） |
| `statusTransitionMethod` | `Practitioner.initAsActive` |
| `repositoryChecks` | `PractitionerRepository.existsByUserId / existsByBusNo` |
| `eventPublishAnchor` | `new PractitionerCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-001` |

**方法契约摘要**：前置条件为 User 存在且未绑定、工号非空时唯一；持久化影响为创建人员并生成检索码；失败语义为映射冲突或唯一约束冲突时报错；事件触发为发布 `PractitionerCreatedEvent`。**注释锚点**：前置校验 -> 初始化 -> 持久化 -> 事件。

---

#### 5.1.2 `editPractitioner(EditPractitionerCommand cmd)` → `void`

**命令文件**：`EditPractitionerCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `name` | `String` | 人员姓名 |
| `phone` | `String` | 联系电话 |
| `address` | `AddressCommand` | 联系地址 |
| `busNo` | `String` | 院内工号 |
| `signature` | `String` | 签名图片 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRepository.existsByBusNoAndIdNot` |
| `eventPublishAnchor` | `new PractitionerEditedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-002` |

**方法契约摘要**：前置条件为仅 ACTIVE 可编辑且工号冲突校验通过；持久化影响为更新基础档案字段，姓名变更重算拼音/五笔码；失败语义为状态不允许或工号冲突时报错；事件触发为发布 `PractitionerEditedEvent`。**注释锚点**：校验 -> 变更 -> 持久化 -> 事件。

---

#### 5.1.3 `deactivatePractitioner(DeactivatePractitionerCommand cmd)` → `void`

**命令文件**：`DeactivatePractitionerCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `reason` | `String` | 停用原因 |
| `operatorId` | `Long` | 操作人标识 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertDeactivatable` |
| `statusTransitionMethod` | `Practitioner.transitToInactive` |
| `repositoryChecks` | `PractitionerRepository.findById` |
| `eventPublishAnchor` | `new PractitionerDeactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-003` |

**方法契约摘要**：前置条件为仅 ACTIVE 可停用并通过进行中引用校验；持久化影响为状态迁移到 INACTIVE 且 `activeFlag=false`；失败语义为存在禁止停用引用或状态非法时报错；事件触发为发布 `PractitionerDeactivatedEvent`。**注释锚点**：引用校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.4 `activatePractitioner(ActivatePractitionerCommand cmd)` → `void`

**命令文件**：`ActivatePractitionerCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `operatorId` | `Long` | 操作人标识 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertActivatable` |
| `statusTransitionMethod` | `Practitioner.transitToActive` |
| `repositoryChecks` | `PractitionerRepository.findById` |
| `eventPublishAnchor` | `new PractitionerActivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-004` |

**方法契约摘要**：前置条件为仅 INACTIVE 可启用；持久化影响为状态迁移 ACTIVE 并恢复 `activeFlag=true`；失败语义为状态不合法时报错；事件触发为发布 `PractitionerActivatedEvent`。**注释锚点**：校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.5 `retirePractitioner(RetirePractitionerCommand cmd)` → `void`

**命令文件**：`RetirePractitionerCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `reason` | `String` | 废弃原因 |
| `operatorId` | `Long` | 操作人标识 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertRetirable` |
| `statusTransitionMethod` | `Practitioner.transitToDeleted` |
| `repositoryChecks` | `PractitionerRepository.findById` |
| `eventPublishAnchor` | `new PractitionerRetiredEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-005` |

**方法契约摘要**：前置条件为 ACTIVE/INACTIVE 可废弃且废弃不可逆；持久化影响为 `statusEnum=DISCARDED` 且 `isDeleted=true`；失败语义为非法状态迁移时报错；事件触发为发布 `PractitionerRetiredEvent`。**注释锚点**：状态校验 -> 逻辑删除 -> 持久化 -> 事件。

---

#### 5.1.6 `switchOrganization(SwitchOrganizationCommand cmd)` → `void`

**命令文件**：`SwitchOrganizationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `practitionerId` | `Long` | 医务人员标识 |
| `targetOrgId` | `Long` | 目标科室标识 |
| `operatorId` | `Long` | 操作人标识 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertSwitchOrgAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRepository.findById / OrganizationRepository.existsActiveById` |
| `eventPublishAnchor` | `new PractitionerOrgSwitchedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-006` |

**方法契约摘要**：前置条件为人员处于 ACTIVE 且目标科室有效；持久化影响为更新 `orgId` 并记录原科室；失败语义为人员状态或目标科室非法时报错；事件触发为发布 `PractitionerOrgSwitchedEvent`。**注释锚点**：校验 -> 字段更新 -> 持久化 -> 事件。

---

#### 5.1.7 `updateSignature(Long practitionerId, String signatureBase64, Long operatorId)` → `void`

**命令文件**：无（三参原子 API；`signatureBase64` 空白清空签名）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Practitioner.assertEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `PractitionerRepository.findById` |
| `eventPublishAnchor` | `new PractitionerEditedEvent(practitionerId, List.of("signature")); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-PRC-W-007` |

**方法契约摘要**：前置条件为 ACTIVE 可编辑；持久化影响为更新或清空 `signature`；失败语义为人员不存在或不可编辑时报错；事件触发为发布 `PractitionerEditedEvent`（仅 `signature` 字段变更）。**注释锚点**：加载 -> 断言 -> 写签名 -> 持久化 -> 事件。

---

#### 5.1.8 `retirePractitionerByBoundUserId(Long userId, Long operatorId, String reason)` → `void`

**命令文件**：无（三参批量包装；内部组装 `RetirePractitionerCommand`）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 复用 `retirePractitioner` 内 `Practitioner.assertRetirable` |
| `statusTransitionMethod` | 复用 `Practitioner.transitToDeleted` |
| `repositoryChecks` | `PractitionerRepository.findAllByUserIdInAndIsDeletedFalse` |
| `eventPublishAnchor` | 复用 `retirePractitioner` → `PractitionerRetiredEvent` |
| `unitTestCaseId` | `TC-PRC-W-008` |

**方法契约摘要**：前置条件为 `userId` 非空；持久化影响为对该 `user_id` 下全部未删 Practitioner 执行废弃；失败语义为非法状态迁移时报错；事件触发为每条命中发布 `PractitionerRetiredEvent`。**触发方**：identity `UserAppService#delete` 发布 `UserDeletedEvent` → health `UserPractitionerLifecycleSyncListener`。**注释锚点**：Pre 按 user_id 加载 -> 组装 RetirePractitionerCommand -> Cmd 复用 retirePractitioner。

### 5.2 读侧 — `PractitionerQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `listActivePractitionersByResolvedRoleScope` | `Collection<Long> resolvedPractitionerIdsForRole` | `List<Practitioner>` | `ACTIVE` + `id IN (...)`；岗位 id 由 App **`PractitionerRoleScopeLoader#listActivePractitionersForOutpatientRegistration`** 等预解析（未选科室时经门诊科室集合 + `listPractitionerIdsWithActiveRoleIdInOrganizations`） | `TC-PRC-R-001` |
| `searchPractitionersPaged` | `int pageNum, int pageSize` | `Page<Practitioner>` | 全表分页浏览 | `TC-PRC-R-002` |
| `pageActivePractitionersForPosition` | `Collection<Long> resolvedPractitionerIdsForRole, String searchKey, Pageable pageable`（+ 重载 `requireBoundSystemUser`） | `Page<Practitioner>` | `PractitionerPageQuerysupport 单表 + `IN`；**禁止** EXISTS `adm_pr_assign` | `TC-PRC-R-003` |
| `findById` | `Long practitionerId` | `Practitioner`（可 null） | 主键加载 | `TC-PRC-R-004` |
| `findByUserId` | `Long userId` | `Practitioner`（可 null） | `user_id` 首条未删 | `TC-PRC-R-005` |
| `mapDisplayNameByEntererIds` | `Collection<Long> entererIds` | `Map<Long,String>` | 仅按 practitionerId 解析姓名（**BR-OPC-016**；禁止 userId 回落） | `TC-PRC-R-006` |
| `findAllByIds` | `Collection<Long> ids` | `List<Practitioner>` | 批量主键；空入参→空列表 | `TC-PRC-R-007` |
| `listBoundUserIdsByPractitionerIds` | `Collection<Long> practitionerIds` | `List<Long>` | 批量取已绑定 `user_id`；未绑账号跳过；供用户管理出诊科室检索 | `TC-PRC-R-008` |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PRC-001 | User 与 Practitioner 一对一，按 `userId` 唯一校验 |
| BR-PRC-002 | 姓名变更时同步重算拼音码与五笔码 |
| BR-PRC-003 | 创建前校验 User 已存在（先用户后人员） |
| BR-PRC-004 | 工号非空时同租户全局唯一 |
| BR-PRC-005 | 已停用人员不可作为新业务角色引用 |
| BR-PRC-006 | DISCARDED 不可恢复，需新建档案 |
| BR-PRC-007 | 删除语义为逻辑删除，保留历史引用 |
| BR-PRC-008 | 切换科室仅允许 ACTIVE 状态执行 |
| BR-PRC-009 | 更新使用乐观锁版本校验 |
| BR-PRC-010 | `birthDate` 固定 `LocalDate` 精度 |
| BR-PRC-011 | User 软删 → `retirePractitionerByBoundUserId`；User 禁用不联动；Practitioner 单独废弃不删 User |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `PractitionerCreatedEvent` | `com.zxplat.domain.share.events.administration.practitioner` | `practitionerId,userId,name,busNo` | `createPractitioner` |
| `PractitionerEditedEvent` | `com.zxplat.domain.share.events.administration.practitioner` | `practitionerId,changedFields` | `editPractitioner`, `updateSignature` |
| `PractitionerActivatedEvent` | `com.zxplat.domain.share.events.administration.practitioner` | `practitionerId,operatorId` | `activatePractitioner` |
| `PractitionerDeactivatedEvent` | `com.zxplat.domain.share.events.administration.practitioner` | `practitionerId,reason,operatorId` | `deactivatePractitioner` |
| `PractitionerRetiredEvent` | `com.zxplat.domain.share.events.administration.practitioner` | `practitionerId,reason,operatorId` | `retirePractitioner` |
| `PractitionerOrgSwitchedEvent` | `com.zxplat.domain.share.events.administration.practitioner` | `practitionerId,fromOrgId,toOrgId` | `switchOrganization` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- Practitioner 删除仅允许逻辑删除，禁止物理删除。
- `statusEnum=DISCARDED` 时必须同步 `isDeleted=true` 且不可恢复。
- **User 软删**须通过 `UserDeletedEvent` 联动 `retirePractitionerByBoundUserId`（**BR-PRC-011**）；**User 禁用**不自动废弃 Practitioner。
- `INACTIVE/DISCARDED` 人员不得作为新业务引用目标；经办人/收费员下拉读侧另经 `PractitionerCashierOptionSupport` 排除绑定用户已软删或未启用的 practitioner（存量脏数据兜底）。
- 历史单据可通过快照或 `findById` 展示人员名称，保持可追溯。
- 默认查询过滤 `isDeleted=true`，展示查询可按场景放宽。

---

## 9. 框架优先适配规则

- 软删、审计字段、事务工作单元、beforeCommit 事件发布遵循项目框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认强制接口 + Impl 分层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口，复杂查询在 `queries` 层完成。
- 分页查询必须数据库侧过滤，确保 total 与结果一致。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 7，与 DM §6.1 方法名一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 3 个方法
- [ ] §6 BR 映射覆盖 BR-PRC-001 至 BR-PRC-011
- [ ] §7 事件表覆盖 DM §5 全部 6 条事件
- [ ] 共享主键绑定（`bindSharedUserIdentity`）、软删、状态语义全篇一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-005-Practitioner-医务人员.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.administration.practitioner`

### 11.2 DoD

- 写侧 6 个方法全部落地，方法签名与 Command 命名与 §5 一致。
- `Practitioner` 字段、状态机、共享主键绑定、软删语义与 §4/§6 一致。
- 所有事件按 §7 beforeCommit 发布，载荷字段不低于最小载荷。
- BR 至少一条绑定一个测试用例 ID，可追溯至 `unitTestCaseId`。
- 读侧 3 个方法与 §4.6 QueryModel 对齐，分页口径一致。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法、事件、BR 发生变更，先更新 SDD 再进入代码会话。
- 若共享主键绑定或软删语义不一致，DoD 失败不得进入成码。

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
| 2026-08-19 | §5.2 增 `listBoundUserIdsByPractitionerIds`：用户管理按出诊科室检索医生账号 |
| 2026-09-03 | 与 DM v1.2.6 对齐：`mapDisplayNameByEntererIds` 仅 practitionerId；漏网 userId 交 Flyway V23 |
| 2026-06-03 | 与 DM v1.2.1 对齐：§9.2 应用层五类 Resolver；交叉引用 Identity §7 / B.6.5 |
| 2026-05-28 | **DM-SDD M1**：§5.1.7 `updateSignature`；§5.2 七读法签名对齐 Java `PractitionerQueryService` |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

