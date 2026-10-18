# DM-ADM-005 Practitioner 医务人员

> **限界上下文**：管理域 · 医务人员管理  
> **聚合类型**：实体聚合  
> **版本**：v1.2.5 · **日期**：2026-08-19

---

## 1. 概述

### 1.1 业务定义

医务人员（Practitioner）是 HIS 系统中**人员主数据**的核心聚合，管理在本医疗机构内直接或间接参与诊疗服务的所有工作人员的基本信息。Practitioner 与身份域 `User` **共享主键**（应用层 `bindSharedUserIdentity`，**无** JPA `@MapsId`），复用统一用户标识；同时作为就诊参与者、医嘱开立者、处方审核者等业务角色的**人员锚点**，被 Encounter、MedicationRequest、ServiceRequest、Composition 等多个核心聚合广泛引用。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 人员档案管理 | 维护医务人员人口学信息（姓名、性别、出生日期、联系方式、地址等） |
| 身份绑定 | 与身份域 User **共享主键**（`bindSharedUserIdentity`），不设 JPA `@MapsId` |
| 职业信息管理 | 维护院内工号、医保码、开票点编码等院内职业标识 |
| 检索码维护 | 姓名变更时自动生成/更新拼音码和五笔码，支撑快速检索 |
| 电子签名管理 | 维护医务人员的签名图片，用于病历签章和处方签名 |
| 科室归属切换 | 支持医务人员登录后切换当前工作科室 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 系统管理 | 管理员创建/编辑/停用/废弃医务人员档案 |
| 门诊接诊 | 医生作为就诊参与者（Encounter.participant）接诊患者 |
| 医嘱开立 | 医生作为 MedicationRequest/ServiceRequest 的开立者 |
| 处方审核 | 药师作为处方审方记录的审核者 |
| 病历签章 | 医生/护士作为 Composition 的记录者和签章者 |
| 科室切换 | 多科室执业的医务人员切换当前工作科室 |
| 门诊挂号 | 挂号窗口选择接诊医生时检索可选医生列表 |

### 1.4 与 PractitionerRole 的关系

Practitioner 与 PractitionerRole 的职责划分是人员管理领域的核心关注点：

| 维度 | Practitioner | PractitionerRole |
|---|---|---|
| 管理范围 | 人员基本信息（人口学、职业标识） | 岗位分配（可提供的服务、排班、执业范围） |
| 生命周期 | 与人员在院期间一一对应 | 一个人员可有多个岗位角色 |
| 变更频率 | 低频（信息修正） | 中频（排班调整、科室调配） |
| 引用方式 | PractitionerRole 通过 `医务人员标识(PractitionerId)` 引用 | — |
| FHIR 对应 | Practitioner（人是谁） | PractitionerRole（人能做什么） |

> **边界规则**：Practitioner 只管"人是谁"，PractitionerRole 管"人在哪个岗位能做什么"。岗位分配（排班、出诊、服务能力）不纳入 Practitioner 聚合。岗位批量替换入口由应用层编排调用 `PractitionerRole` 聚合能力完成。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，与身份域 User **共享主键**（应用层保证同值，无 `@MapsId`）。聚合边界以主表主键 `id`（医务人员标识）划分。

**表结构**：
- 主表：`adm_practitioner`（主键：`id`）
- 关系：与 `sys_user` **共享主键**（`Practitioner.id = User.id`，由 `bindSharedUserIdentity` 写入）
- 索引：`bus_no`（院内工号）、`gender_enum`（性别）、`status_enum`（状态）、`active_flag`（是否启用）、`org_id`（科室）

**高频查询模式**：
- 按医务人员标识查询：单表主键查询
- 按院内工号查询：`bus_no` 索引扫描
- 按科室查询：`org_id` 索引扫描
- 按状态筛选：`status_enum` 索引扫描

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 医务人员标识 | `PractitionerId` | 全局唯一标识，与 `User.id` 相同（共享主键） |

### 2.3 状态机

```
           创建
            │
            ▼
  [ACTIVE] ──停用──> [INACTIVE]
     │                  │
     │     启用 <────────┘
     │                  │
     └──废弃──> [DELETED]
                      ▲
   [INACTIVE] ──废弃──┘
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | ACTIVE | 正常在用 | 可被就诊、医嘱等引用，允许编辑信息、停用、废弃、切换科室 |
| 2 | INACTIVE | 暂时停用 | 不可被新业务引用，允许启用、废弃 |
| 3 | DELETED | 逻辑删除 | 保留历史引用一致性 |

> **状态枚举说明**：持久化字段 `status_enum` 使用共享枚举 **`PublicationStatus`**（**String 存储，存枚举 code**）：`ACTIVE / INACTIVE / DISCARDED`（废弃；兼容存量 `DELETED`）。`active_flag` 为派生字段。

---

## 3. 实体

### 3.1 医务人员（Practitioner）

Practitioner 聚合根的属性定义，对应 `adm_practitioner` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 人员姓名 | `name` | `PersonName`（值对象） | 是 | 见 §4.1，含姓名、检索码 |
| 性别 | `gender_enum` | `Gender`（值对象） | 是 | 见 §4.2 |
| 出生日期 | `birth_date` | `LocalDate` | 否 | 精确到日（FHIR `Practitioner.birthDate`） |
| 死亡时间 | `deceased_date` | `DateTime` | 否 | 死亡标记（FHIR `Practitioner.deceased[x]`） |
| 联系电话 | `phone` | `String` | 否 | 手机号/座机号 |
| 地址 | `address` | `Address`（值对象） | 否 | 见 §4.3 |
| 人员状态 | `status_enum` | `PublicationStatus`（值对象） | 是 | ACTIVE/INACTIVE/DISCARDED，见 §2.3 |
| 是否启用 | `active_flag` | `Boolean` | 是 | `status_enum == ACTIVE` 时为 `true`，其余为 `false`；与 FHIR `Practitioner.active` 对齐 |
| 院内工号 | `bus_no` | `String` | 否 | 院内编码/工号（FHIR `identifier`） |
| 医保码 | `yb_no` | `String` | 否 | 医保系统人员编码 |
| 用户标识 | `user_id` | `UserId` | 是 | 关联身份域 User（与 `id` 同值） |
| 机构科室标识 | `org_id` | `DepartmentId` | 否 | 当前工作科室 ID（关联 Organization） |
| 职称 | `dr_profttl_code` | `ProfessionalTitle`（值对象） | 否 | 见 §4.5 |
| 执业证件编号 | `phar_prac_cert_no` | `String` | 否 | 医师/药师执业证书编号 |
| 证件类型编码 | `prsc_dr_cert_code` | `String` | 否 | 执业证件类型 |
| 签名图片 | `signature` | `String` | 否 | 签名图片存储路径（用于病历签章） |
| 开票点编码 | `kpd_code` | `String` | 否 | POS 开票点编码 |
| POS机编号 | `pos_no` | `String` | 否 | 关联 POS 机终端编号 |

> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`，遵循统一约定。

---

## 4. 值对象

### 4.1 人员姓名（PersonName）

封装人员姓名及其检索码，姓名变更时检索码同步重新生成。

| 属性 | 类型 | 说明 |
|---|---|---|
| 姓名 | `String` | 人员正式姓名 |
| 其他名称 | `String` | 其他名称 JSON（曾用名、英文名等） |
| 拼音码 | `String` | 姓名拼音首字母缩写，自动生成 |
| 五笔码 | `String` | 姓名五笔首码缩写，自动生成 |

> **FHIR 对齐**：对应 `Practitioner.name`（HumanName 数据类型），FHIR 支持 `family`、`given`、`prefix`、`suffix` 等结构化字段，本地使用单一 `name` 字段 + JSON 扩展。
> **存储口径**：对应主表字段 `name`（姓名）、`name_json`（其他名称）、`py_str`（拼音码）、`wb_str`（五笔码），使用字符串存储。

### 4.2 性别（AdministrativeGender）

| 编码值 | 英文 | 说明 |
|---|---|---|
| 0 | `MALE` | 男性 |
| 1 | `FEMALE` | 女性 |
| 2 | `UNKNOWN` | 未知 |

> **编码对齐**：与实现 `AdministrativeGender` 枚举 3 值一一对应。FHIR 定义另有 `other`，本系统未实现。
> **存储口径**：对应主表字段 `gender_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.3 地址（Address）

封装医务人员联系地址的结构化表达。

| 属性 | 类型 | 说明 |
|---|---|---|
| 省/自治区/直辖市 | `String` | 省级行政区划 |
| 市 | `String` | 地级行政区划 |
| 区/县 | `String` | 县级行政区划 |
| 街道/详细地址 | `String` | 街道及详细地址 |
| 扩展地址 | `String` | 其他地址 JSON（通信地址、户籍地址等） |

> **FHIR 对齐**：对应 `Practitioner.address`（Address 数据类型），FHIR 使用 `line`、`city`、`district`、`state`、`postalCode` 等字段。
> **存储口径**：对应主表字段 `address_province`、`address_city`、`address_district`、`address_street`、`address_json`，使用字符串存储。

### 4.4 职称（ProfessionalTitle）

标识医务人员的专业技术职称等级。

| 编码值 | 英文 | 说明 |
|---|---|---|
| 1 | `primary-junior` | 初级（师）：住院医师/药师/护师 |
| 2 | `primary-senior` | 初级（士）：医士/药士/护士 |
| 3 | `intermediate` | 中级：主治医师/主管药师/主管护师 |
| 4 | `associate-senior` | 副高级：副主任医师/副主任药师/副主任护师 |
| 5 | `senior` | 正高级：主任医师/主任药师/主任护师 |
| 0 | `none` | 无职称：暂未评定职称 |

> **存储口径**：对应主表字段 `dr_profttl_code`，使用字符串编码存储。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 医务人员已创建 | `PractitionerCreatedEvent` | 创建医务人员成功 | 医务人员标识, 用户标识, 姓名, 院内工号 | QueryService/缓存 |
| 医务人员已编辑 | `PractitionerEditedEvent` | 编辑基本信息成功 | 医务人员标识, 变更字段摘要 | QueryService/缓存 |
| 医务人员已启用 | `PractitionerActivatedEvent` | 停用状态恢复为启用 | 医务人员标识, 操作人标识 | QueryService/缓存 |
| 医务人员已停用 | `PractitionerDeactivatedEvent` | 启用状态变更为停用 | 医务人员标识, 停用原因, 操作人标识 | PractitionerRole（联动停用） |
| 医务人员已废弃 | `PractitionerRetiredEvent` | 逻辑删除成功 | 医务人员标识, 废弃原因, 操作人标识 | PractitionerRole（联动废弃） |
| 科室已切换 | `PractitionerOrgSwitchedEvent` | 切换当前工作科室 | 医务人员标识, 原科室标识, 目标科室标识 | 医生站/上下文 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 创建医务人员 | ACTIVE | `PractitionerCreatedEvent` |
| ACTIVE | 编辑基本信息 | ACTIVE | `PractitionerEditedEvent` |
| ACTIVE | 停用 | INACTIVE | `PractitionerDeactivatedEvent` |
| INACTIVE | 启用 | ACTIVE | `PractitionerActivatedEvent` |
| ACTIVE | 废弃 | DELETED | `PractitionerRetiredEvent` |
| INACTIVE | 废弃 | DELETED | `PractitionerRetiredEvent` |
| ACTIVE | 切换科室 | ACTIVE | `PractitionerOrgSwitchedEvent` |

---

## 6. 领域服务

### 6.1 医务人员领域服务（PractitionerDomainService）

#### 6.1.1 创建医务人员（`createPractitioner`）
- **业务意义**：建立 User 对应的医务人员主数据档案。
- **前置条件**：同一 `UserId` 不可重复（BR-PRC-001）；User 必须存在（BR-PRC-003）。
- **状态变化**：初始化为 ACTIVE。
- **聚合内影响**：生成拼音码与五笔码。
- **事件输出**：`PractitionerCreatedEvent`。

#### 6.1.2 编辑医务人员（`editPractitioner`）
- **业务意义**：维护人员档案基础信息。
- **前置条件**：人员须为 ACTIVE；姓名变更需同步检索码（BR-PRC-002）；工号变更满足唯一性（BR-PRC-004）。
- **事件输出**：`PractitionerEditedEvent`。

#### 6.1.3 停用医务人员（`deactivatePractitioner`）
- **业务意义**：阻止人员继续被新业务引用。
- **前置条件**：人员须为 ACTIVE；需通过进行中引用校验（BR-PRC-005）。
- **状态变化**：ACTIVE -> INACTIVE。
- **事件输出**：`PractitionerDeactivatedEvent`。

#### 6.1.4 启用医务人员（`activatePractitioner`）
- **业务意义**：恢复停用人员为可用状态。
- **前置条件**：人员须为 INACTIVE。
- **状态变化**：INACTIVE -> ACTIVE。
- **事件输出**：`PractitionerActivatedEvent`。

#### 6.1.5 废弃医务人员（`retirePractitioner`）
- **业务意义**：将人员从可用集合移除并保留历史追踪。
- **前置条件**：人员须为 ACTIVE 或 INACTIVE；废弃后不可恢复（BR-PRC-006）。
- **状态变化**：ACTIVE/INACTIVE -> DELETED。
- **聚合内影响**：`isDeleted = true`。
- **事件输出**：`PractitionerRetiredEvent`。

#### 6.1.6 切换科室（`switchOrganization`）
- **业务意义**：更新人员当前工作科室归属。
- **前置条件**：人员须为 ACTIVE 且非废弃（BR-PRC-008）；目标科室有效。
- **聚合内影响**：更新 `orgId`。
- **事件输出**：`PractitionerOrgSwitchedEvent`。

#### 6.1.7 更新电子签名（`updateSignature`）

- **业务意义**：独立维护医务人员签名图片（Base64 或 data URL），供病历签章与处方签名。
- **前置条件**：人员须为 ACTIVE（`assertEditable`）；`signatureBase64` 空白视为清空签名。
- **聚合内影响**：更新 `signature` 字段；空白写 `null`。
- **事件输出**：`PractitionerEditedEvent`（`changedFields` 含 `signature`）。

#### 6.1.8 按绑定用户注销医务人员（`retirePractitionerByBoundUserId`）

- **业务意义**：平台用户软删时，将仍绑定该 `user_id` 且未逻辑删除的 Practitioner 批量废弃，消除 identity 与 HIS 人员主档不一致（**BR-PRC-011**）。
- **前置条件**：`userId` 非空；由 health `UserPractitionerLifecycleSyncListener` 监听 `UserDeletedEvent` 触发，**不在** identity 模块直写 `adm_practitioner`。
- **聚合内影响**：对命中记录复用 `retirePractitioner`（`statusEnum=DISCARDED`、`isDeleted=true`）。
- **事件输出**：每条命中发布 `PractitionerRetiredEvent`。

### 6.2 读侧查询服务（PractitionerQueryService）

#### 6.2.1 门诊挂号医生列表（`listActivePractitionersByResolvedRoleScope`）

- **业务意义**：提供挂号可选医生集合（在职人员主档）。
- **入参**：`resolvedPractitionerIdsForRole` — 由应用层 **`PractitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(platformRoleCode, orgId)`** 预解析（内部经 **`listPractitionerIdsWithActiveRoleId`** 或 **`listPractitionerIdsWithActiveRoleIdInOrganizations`**）；`null`/空集合返回空列表。
- **查询范围**：`ACTIVE`、未软删、`id IN (resolvedPractitionerIdsForRole)`；**禁止**在本聚合 EXISTS `adm_pr_assign`（**B.6.1.3.1**）。
- **机构/角色收窄**：由 Loader 完成——已选科室时按 `adm_pr_assign.org_id` 收窄；未选科室时先取 **`OrganizationQueryService#listOrganizationsForOutpatientRegistration`** 的门诊科室 id 集合，再批量按岗位表收窄，**排除**仅在住院科室挂医师岗的人员。

#### 6.2.2 分页检索医务人员（`searchPractitionersPaged`）
- **业务意义**：支持管理端按关键字/工号检索人员。
- **查询范围**：人员主档分页查询。

#### 6.2.3 按标识加载医务人员（用于展示）（`findById`）
- **业务意义**：用于岗位管理、历史单据、详情页等场景回显医务人员名称与主档字段（含停用/废弃展示）。
- **边界说明**：只读查询，不写库；不存在返回 `null`。

#### 6.2.4 按系统用户加载医务人员（`findByUserId`）

- **业务意义**：登录用户反查绑定医务人员主档。
- **边界说明**：`userId` 为空返回 `null`；未绑定或已删返回 `null`。

#### 6.2.5 岗位相关在职人员分页（`pageActivePractitionersForPosition`）

- **业务意义**：发药页调配药师、岗位管理菜单等在职人员分页。
- **入参**：`Collection<Long> resolvedPractitionerIdsForRole`（`null`=不按岗位过滤；空集合=无命中）、检索关键字、`Pageable`；重载支持 `requireBoundSystemUser`（「按人」Tab 仅保留已绑定系统用户者）。
- **过滤口径**：仅 `adm_practitioner` 单表谓词 + `id IN (...)`；岗位/角色收窄须由应用层 **`PractitionerRoleScopeLoader`** 预解析主键，**禁止**传入 `platformRoleId`/`businessRoleCode` 并在领域层 EXISTS 岗位表。
- **出参**：`Page<Practitioner>`。

#### 6.2.6 批量解析经办人展示名（`mapDisplayNameByEntererIds`）

- **业务意义**：支付头 / 收费明细等 `enterer_id`（**仅** `adm_practitioner.id`，**BR-OPC-016**）批量解析为姓名。
- **入参**：`entererIds` 集合。
- **出参**：`Map<Long,String>`；未命中键不写入。
- **边界说明**：漏网 `whale_users.id` 由 Flyway `V23__backfill_enterer_id_practitioner` 一次性回填；本方法**不再**按绑定用户主键二次反查。

#### 6.2.7 按主键批量加载（`findAllByIds`）

- **业务意义**：列表装配等场景避免 N+1。
- **入参**：医务人员主键集合；空集合返回空列表。
- **出参**：`List<Practitioner>`。

#### 6.2.8 按医务人员主键批量取绑定用户（`listBoundUserIdsByPractitionerIds`）

- **业务意义**：把岗位预解析的医务人员主键映射为平台用户主键，供用户管理按出诊科室检索账号。
- **入参**：`practitionerIds` 集合；空集合返回空列表。
- **出参**：去重保序的 `user_id` 列表；未绑定系统用户的主档不写入。
- **边界说明**：仅本聚合 `PractitionerRepository`；岗位收窄须由调用方先走 **DM-ADM-006** `listPractitionerIdsWithActiveRoleId`（**B.6.4**）。

### 6.3 领域边界说明

- 本聚合维护**医务人员人口学与职业标识主数据**，不维护岗位分配（见 `PractitionerRole`）、不直接写入就诊或医嘱事实。
- 跨聚合岗位/角色读收窄（挂号医生、发药药师、库存经手人等）**唯一**落点 **`application.shared.practitioner.PractitionerRoleScopeLoader`**（**B.6.4** / 历史称 **B.6.1.3.1**）；本聚合 `*QueryService` 仅接受预解析的 `resolvedPractitionerIdsForRole`。
- **业务选型下拉默认剔平台 admin**（应用层口径，**非**本聚合 BR）：经 Loader 装岗池时默认剔除登录用户名 `admin`（`RolePlatformConstants.ADMIN_USERNAME`）绑定医务人员及激活 `ROLE_ADMIN` 岗人员；日结岗池等须保留挂业务岗的 admin 时显式 `excludePlatformAdmin=false`（见成码规范 **B.6.3.1**、`PractitionerPlatformAdminExcludeSupport`）。
- 与 `User` 的一对一绑定及岗位批量保存等编排见 **§9.2、§9.3**，不在本聚合领域服务内实现跨聚合替换逻辑。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-PRC-001 | 同一 `用户标识(UserId)` 不允许重复创建 Practitioner | 保证 User 与 Practitioner 的一对一映射 |
| BR-PRC-002 | 姓名变更时必须同步重新生成 `拼音码(pyStr)` 和 `五笔码(wbStr)` | 保证检索码与姓名一致 |
| BR-PRC-003 | 创建医务人员前必须存在有效的用户账号（User），遵循"先用户后人员"链路 | User → Practitioner 依赖顺序 |
| BR-PRC-004 | `院内工号(busNo)` 在同一租户内全局唯一（非空时校验） | 工号唯一性约束 |
| BR-PRC-005 | 已停用的医务人员不得作为新就诊参与者或新医嘱开立者被引用 | 防止停用人员继续产生业务关联 |
| BR-PRC-006 | 已废弃状态不可恢复至启用或已停用；如需重新启用须重新创建 | 废弃不可逆 |
| BR-PRC-007 | 删除采用逻辑删除（`isDeleted = true`），不做物理删除，保留历史引用一致性 | 软删除策略 |
| BR-PRC-008 | 切换科室操作仅限启用状态的医务人员执行 | 状态前置断言 |
| BR-PRC-009 | 聚合更新必须带版本号，发生并发冲突时拒绝写入（乐观锁） | 并发一致性 |
| BR-PRC-010 | `出生日期(birthDate)` 精确到日（`LocalDate`），不使用 `LocalDateTime` | 与 FHIR `birthDate` 类型对齐 |
| BR-PRC-011 | 平台用户**软删**时须联动注销绑定 Practitioner（`UserDeletedEvent` → `retirePractitionerByBoundUserId`）；**User 禁用**（`is_active=false`）不自动废弃 Practitioner；单独废弃 Practitioner 不删除 User | 与 identity 生命周期一致；读侧经办人下拉另经 `PractitionerCashierOptionSupport` 过滤 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 身份认证 | User | `用户标识(UserId)` / 共享主键 | 一对一绑定，Practitioner 主键与 User 主键相同 |
| 组织架构 | Organization | `机构科室标识(DepartmentId)` | 当前工作科室 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 岗位管理 | PractitionerRole | 引用 + 事件 | PractitionerRole 通过 `医务人员标识(PractitionerId)` 引用；监听 `PractitionerDeactivatedEvent` 联动停用岗位 |
| 就诊管理 | Encounter | 引用 | 就诊参与者（EncounterParticipant）通过 `医务人员标识(PractitionerId)` 引用 |
| 药品申请 | MedicationRequest | 引用 | 医嘱开立者、审核者通过 `医务人员标识(PractitionerId)` 引用 |
| 服务申请 | ServiceRequest | 引用 | 服务申请开立者通过 `医务人员标识(PractitionerId)` 引用 |
| 病历管理 | Composition | 引用 | 病历记录者和签章者通过 `医务人员标识(PractitionerId)` 引用 |
| 处方审方 | PrescriptionReviewRecord | 引用 | 审方药师通过 `医务人员标识(PractitionerId)` 引用 |
| 收费管理 | ChargeItem | 引用 | 操作人通过 `医务人员标识(PractitionerId)` 引用 |

### 8.3 引用规则

> **统一规则**：所有需要引用医务人员的领域模型，统一使用 `医务人员标识(PractitionerId)` 进行跨聚合 ID 引用，不直接持有 Practitioner 对象引用。
>
> Practitioner 是纯主数据聚合，不引用就诊标识或患者标识。被引用方通过 PractitionerId 做关联查询。

### 8.4 事件流示意图

```
User                  Practitioner              PractitionerRole       Encounter
  │                       │                           │                    │
  │── 用户已创建 ─────────>│                           │                    │
  │                       │                           │                    │
  │                       │── CreatedEvent ──────────>│ (可分配岗位)       │
  │                       │                           │                    │
  │                       │── DeactivatedEvent ──────>│ (联动停用岗位)     │
  │                       │                           │                    │
  │                       │── OrgSwitchedEvent ──────────────────────────>│ (更新上下文)
  │                       │                           │                    │
  │                       │── RetiredEvent ──────────>│ (联动废弃岗位)     │
  │                       │                           │                    │
```

---

## 9. 补充

### 9.1 与 User 的共享主键绑定说明

Practitioner 与 User **共享同一主键数值**，由领域实体方法 `bindSharedUserIdentity(userId)` 在创建时写入；**不使用** JPA `@MapsId`，**不设**库表外键：

| 维度 | 说明 |
|---|---|
| 主键来源 | `Practitioner.id = User.id`，须先创建 User 再 `bindSharedUserIdentity` |
| 创建链路 | 应用层编排：① 先创建 User ② 再创建 Practitioner（BR-PRC-003） |
| 删除策略 | **User 软删**联动注销绑定 Practitioner（`UserDeletedEvent` → `retirePractitionerByBoundUserId`，原因见 `PractitionerLifecycleConstants.RETIRE_REASON_BOUND_USER_DELETED`）；**User 禁用**（`is_active=false`）**不**自动废弃 Practitioner；单独废弃 Practitioner **不**删除 User |
| 查询关联 | 通过共享 ID 直接关联，无需额外外键 |

### 9.2 岗位与用户一体维护编排说明

人员岗位管理主链路由应用层 **`PositionAppService`**（`/position/*`）编排，跨越 **身份域 User/Role/OrganizationUnit** 与 **Practitioner / PractitionerRole** 两个管理域聚合：

1. **用户主档**：创建/更新 `User`（账号、姓名、平台角色分配）
2. **医务人员主档**：创建/更新 `Practitioner`（`bindSharedUserIdentity` 共享主键）
3. **六类岗位块**：按平台角色 StaffScope 全量替换 `adm_pr_assign`（出诊科室、库房、药房、工作科室、管理病区等，映射见 **DM-ADM-006 §4.1 / §6.2**）。工作科室：护士仅护士站、医技仅医技科室。
4. **可编辑块门控**：当前操作者平台角色的 `StaffScope` 声明（`whale_role_claims.claim_type=StaffScope`）决定哪些块可维护；写路径另经 **数据管辖 Gate** 校验仅可写本人管辖范围内 org/location
5. **同步登录科室**：自 `collectLoginOrganizationUnitIds` 得到 **HIS 组织 id** → `IOrganizationUnitScopeBridge` 按 code 解析 **ZXPlat OU id** → `syncOrganizationUnitsForUser` 写入 `whale_user_organization_units`；会话 `currentOrganizationUnitId` 仍为 **HIS 组织 id**
6. **运行时消费**：各业务 AppService / Loader **禁止**直读 `ICurrentUser`；登录用户、医务人员、会话科室、可操作库房、管辖病区分别经 **五类 Resolver**（**B.6.5**、[Identity与HIS组织轻关联说明](../../04-架构设计/Identity与HIS组织轻关联说明.md) §7）；数据管辖真源仍为 **DM-ADM-006** `resolveManagedScope`

> 此编排属于应用服务层职责，不在 Practitioner / PractitionerRole 领域服务内实现。历史文档中的 `saveRolesByPractitionerId` 口径已收敛为 **`saveStaff`**。

### 9.3 与 PractitionerRole 的职责边界

| 职责 | Practitioner | PractitionerRole |
|---|---|---|
| 人口学信息 | ✓ 维护姓名、性别、出生日期、地址、联系方式 | — |
| 职业标识 | ✓ 维护工号、医保码、执业证号 | — |
| 电子签名 | ✓ 维护签名图片 | — |
| 科室归属 | ✓ 维护当前工作科室 | ✓ 维护岗位归属科室（可多个） |
| 排班管理 | — | ✓ 可提供服务时间与排班 |
| 执业范围 | — | ✓ 可执行的诊疗项目范围 |
| 出诊能力 | — | ✓ 是否可出诊及号源配置 |

### 9.4 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2.7 | 2026-10-14 | §6.3：Loader 包路径更正为 `application.shared.practitioner`；补「业务选型下拉默认剔平台 admin / 日结显式保留」应用层口径（代码先行回写，**B.6.3.1**） |
| v1.2.6 | 2026-09-03 | §6.2.6 `mapDisplayNameByEntererIds`：仅按 `adm_practitioner.id`；漏网 userId 交 Flyway V23 回填，去掉运行时 userId 回落 |
| v1.2.5 | 2026-08-19 | §6.2.8 `listBoundUserIdsByPractitionerIds`：用户管理按出诊科室检索医生账号 |
| v1.2.4 | 2026-07-08 | **BR-PRC-011**：User 软删联动 `retirePractitionerByBoundUserId`；§9.1 删除策略与 identity 对齐 |
| v1.2.3 | 2026-06-10 | §6.2.1 门诊挂号医生收窄：未选科室时经门诊科室集合 + `listPractitionerIdsWithActiveRoleIdInOrganizations` 预解析，排除仅住院科室挂岗医师 |
| v1.2.2 | 2026-06-09 | **B.6.1.3.1**：§6.2.1 改为 `listActivePractitionersByResolvedRoleScope`；§6.2.5 入参改为 `resolvedPractitionerIdsForRole`；跨聚合岗位收窄收口 **`PractitionerRoleScopeLoader`** |
| v1.2.1 | 2026-06-03 | §9.2 第 6 步：App 层五类 Resolver；与 B.6.5 对齐 |
| v1.2.0 | 2026-06-03 | PublicationStatus 替代 PractitionerStatus；§9.2 登录科室桥接（HIS id ↔ ZXPlat OU id） |
| v1.1.0 | 2026-06-03 | §6.2.5 `businessRoleCode` 改语义码；§9.2 PositionAppService + StaffScope |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PractitionerDomainService`

| 方法 |
|------|
| `createPractitioner` |
| `editPractitioner` |
| `updateSignature` |
| `deactivatePractitioner` |
| `activatePractitioner` |
| `retirePractitioner` |
| `switchOrganization` |
| `retirePractitionerByBoundUserId` |

### QueryService：`PractitionerQueryService`

| 方法 |
|------|
| `listActivePractitionersByResolvedRoleScope` |
| `searchPractitionersPaged` |
| `pageActivePractitionersForPosition` |
| `findById` |
| `findActiveByBusNo` |
| `findByUserId` |
| `mapDisplayNameByEntererIds` |
| `findAllByIds` |
| `listActivePractitionerIdsByNameKeyword` |
| `mapNamesByIds` |
| `listActivePractitionersByBoundUserIds` |
| `listBoundUserIdsByPractitionerIds` |

