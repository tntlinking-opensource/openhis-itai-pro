# DM-ADM-003 Location 位置 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-003-Location-位置.md`（**v1.4**，本 SDD 假定该 DM 为当期锁定版本）  
> **SDD 版本**：v1.2.0 · **日期**：2026-07-02（M4 已对齐）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 位置管理）  
> **聚合**：Location（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.location` | 满足 | `location` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 根实体主键展示 | §3.1 使用 `LocationId` 语义类型 | 成码口径落到 `Long id`，语义通过命名保持 |
| 层级状态表达 | §2.3 混合生命周期与床位运营态 | SDD 分离为状态字段与运营字段，避免写侧歧义 |
| 查询模型粒度 | §6.2 仅给读方法语义 | SDD 在 §4.6 明确逐方法字段级模型；护士站等场景允许直接返回 `Location` 实体列表 |
| 管理病区解析 | DM §6.2.11 **已自本聚合移除** | 应用层 **`CurrentManagedWardLocationIdsResolver`** + **`PractitionerManagedScopeLoader`**；`LocationQueryService` **仅**本聚合仓储 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Location 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，保证写侧实现可直接对照。
- 单次成稿目标：正文不出现后续补充/待确认类措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：`adm_location` 单表聚合的数据一致性、层级关系（`parent_id` + `bus_no` 双轨）、状态流转、床位运营状态、事件发布；住院护士站/维护页所需**读侧**查询（含管理病区、病区→病房树）。
- **在外**：Encounter 入出转编排、排班生成、库存出入库等跨域业务流程；岗位分配主数据维护（**DM-ADM-006** 写侧）。
- **跨域仅端口声明**：仅声明对 Organization/Encounter/PractitionerRole 等外部聚合的引用校验与事件消费关系，不在本聚合中写跨域数据。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.administration.location`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Location` |
| `valueobject` | `LocationStatusSnapshot`（真正值对象） |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums` 的位置相关枚举 |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `LocationRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类；**禁止**注入其他聚合 `*QueryService`，见 **B.6.1.3.1**） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.location` |

**存储口径（DM 优先）**：`*_enum`（含 `form_enum`/`status_enum`/`operational_enum`/`mode_enum`）一律 **`String` 存枚举 `getCode()`**；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。**禁止**整型枚举列或 sortOrder↔code 互转。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id` |
| 时间 | `*Date` 用 `LocalDate`；`*Time/*At` 用 `LocalDateTime` |
| `*_enum` | 枚举 **code** 列，`String`（与 DM §4、`LocationForm` 等 `getCode()` 一致） |
| `*_code` | 字典/术语编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `interface XxxRepository extends JpaRepository<Entity, Long>` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内 `new XxxEvent(...)` + `DomainEventPublisher.publish`，beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_location` | `Location` | 位置聚合根单表 |

### 4.2 聚合根 `Location`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根主键 |
| `name` | `String` | 是 | 位置名称，同父级下唯一 |
| `busNo` | `String` | 是 | 层级编码（`.` 分隔），创建后不可变；根段经 `BusinessSequenceService` + `AggregateTypeConstants.LOCATION` 采番，子级 `父.busNo + "." + 本段`（**BR-LOC-003**） |
| `statusEnum` | `String` | 是 | 生命周期状态 code（`ACTIVE`/`DRAFT` 等） |
| `operationalEnum` | `String` | 否 | 运营状态 code（仅床位，`NOT_IN_USE`/`IN_USE`） |
| `formEnum` | `String` | 是 | 物理形式 code（`WARD`/`HOUSE`/`BED`/`PHARMACY`/`WAREHOUSE`/`DEPARTMENT_PHARMACY` 等） |
| `modeEnum` | `String` | 是 | 位置模式 code（`INSTANCE`/`KIND`） |
| `typeJson` | `String` | 否 | 扩展类型信息 JSON；病区可含 `servedOrganizationIds`（附加服务住院科室 Long 数组） |
| `organizationId` | `Long` | 是 | 所属科室机构标识 |
| `displayOrder` | `Integer` | 否 | 同级显示顺序 |
| `pyStr` | `String` | 否 | 拼音检索码 |
| `wbStr` | `String` | 否 | 五笔检索码 |
| `parentId` | `Long` | 否 | 父位置标识（根节点为空） |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertNameUniqueInParent`、`assertHierarchyValid`、`assertBedOnlyOperationalStatus`、`assertDeactivateAllowed`、`assertAbolishAllowed`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `LocationRepository extends JpaRepository<Location, Long>`

**派生方法（与实现对齐，禁止 `@Query`）**：

| 方法 | 用途 |
| ---- | ---- |
| `existsByParentIdAndName` | 同父级名称唯一 |
| `findAllByParentId` / `findAllByParentIdAndFormEnum` | 维护页子树 |
| `findAllByParentIdAndFormEnumAndStatusEnum` | 护士站启用病房 |
| `findAllByIdInAndFormEnumAndStatusEnum` | 岗位 `location_id` 收窄为管理病区 |
| `findAllByBusNoStartingWith` | `bus_no` 前缀展开一层子节点 |
| `findAllByOrganizationIdAndFormEnumAndStatusEnum` | 机构下启用病区等 |
| `findAllByFormEnum` / `findAllByFormEnumAndStatusEnum` | 形态/启用过滤 |

**说明**：软删由框架统一过滤时，不强制方法名追加 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `WardQueryModel`（对应 `listWardsByOrganization`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_location.id` | 位置标识 |
| `name` | `String` | `adm_location.name` | 病区名称 |
| `busNo` | `String` | `adm_location.bus_no` | 层级编码 |
| `organizationId` | `Long` | `adm_location.organization_id` | 所属科室 |
| `statusEnum` | `String` | `adm_location.status_enum` | 位置状态 code |

#### 4.6.2 `WardBedCountQueryModel`（对应 `countBedsByWard`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `wardId` | `Long` | `adm_location.parent_id` | 病区标识 |
| `totalBeds` | `Long` | 聚合统计 | 床位总数 |
| `idleBeds` | `Long` | 聚合统计 | 空闲床位数 |
| `occupiedBeds` | `Long` | 聚合统计 | 占用床位数 |

#### 4.6.3 `PharmacyQueryModel`（对应 `listPharmaciesByOrganization`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_location.id` | 药房位置标识 |
| `name` | `String` | `adm_location.name` | 药房名称 |
| `organizationId` | `Long` | `adm_location.organization_id` | 科室标识 |
| `statusEnum` | `String` | `adm_location.status_enum` | 位置状态 code |

#### 4.6.4 `WarehouseQueryModel`（对应 `listWarehousesByOrganization`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_location.id` | 仓库位置标识 |
| `name` | `String` | `adm_location.name` | 仓库名称 |
| `organizationId` | `Long` | `adm_location.organization_id` | 科室标识 |
| `statusEnum` | `String` | `adm_location.status_enum` | 位置状态 code |

#### 4.6.5 直接返回 `Location` 实体的读方法（字段同 §4.2）

以下读方法出参为 `List<Location>` 或单条 `Optional<Location>`，**不**单独建 QueryModel；应用层或 **AS-IP-NUR-001** 按需映射 DTO：

| 方法 | 出参 | 典型消费 |
| ---- | ---- | -------- |
| `listRoomsUnderWard` | `List<Location>` | 病区床位维护（不限状态） |
| `listHousesUnderWard` | `List<Location>` | 护士站树·病房（仅 `ACTIVE`） |
| `listBedsUnderParent` | `List<Location>` | 维护页/分床 |
| `listAllByFormCode` / `listByOrganizationIdAndFormCode` | `List<Location>` | 维护下拉 |
| `listChildrenByParentBusNo` / 重载 | `List<Location>` | `bus_no` 一层子节点 |
| `listActiveHousesByWardBusNo` | `List<Location>` | 按病区编码列病房 |
| `listAllActiveWards` | `List<Location>` | 无管理病区时树根 |
| ~~`listManagedWardsForPractitioner`~~ | — | **已移除（2026-06）**；见 **`CurrentManagedWardLocationIdsResolver`** |
| `findOptionalById` / `mapNamesByIds` | `Optional` / `Map` | 名称解析、批量展示 |

**排序口径**：`display_order ASC NULLS LAST`，再 `id ASC`（`LocationQueryService#sortByDisplayOrder`）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `LocationDomainService`

#### 5.1.1 `registerLocation(RegisterLocationCommand cmd)` → `Long`

**命令文件**：`RegisterLocationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 位置名称 |
| `parentId` | `Long` | 父位置标识 |
| `organizationId` | `Long` | 机构标识 |
| `formEnum` | `String` | 物理形式 code（`WARD`/`HOUSE`/`BED` 等） |
| `modeEnum` | `String` | 位置模式 code（默认 `INSTANCE`） |
| `typeJson` | `String` | 类型扩展 JSON，默认 `{}` |
| `displayOrder` | `Integer` | 展示顺序 |
| `pyStr` / `wbStr` | `String` | 检索码 |
| `registerAsActive` | `Boolean` | 为 `true` 时 `initActive()`，否则 `initDraft()` |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertRegisterRequiredFields` |
| `statusTransitionMethod` | `Location.initActive` / `Location.initDraft`（由 `registerAsActive` 分支） |
| `repositoryChecks` | `LocationRepository.existsByParentIdAndName` |
| `eventPublishAnchor` | `new LocationRegisteredEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-001` |

**方法契约摘要**：前置条件为父级可用、同父级名称不重复、层级合法（住院链 `WARD→HOUSE→BED`，**BR-LOC-004/011**）；持久化影响为新增 `adm_location` 并生成 `busNo`（`buildBusNo`：`BusinessSequenceService.getSeq(LOCATION, BUS_NO_SEQ_LENGTH)`，有父级则 `parent.busNo + "." + segment`）；失败语义为违反不变式拒绝写入；`registerAsActive=true` 时另发 `LocationActivatedEvent`。**注释锚点**：校验 -> 组装 busNo -> 持久化 -> 事件。

---

#### 5.1.2 `activateLocation(ActivateLocationCommand cmd)` → `void`

**命令文件**：`ActivateLocationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 位置标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertActivatable` |
| `statusTransitionMethod` | `Location.transitToActive` |
| `repositoryChecks` | `LocationRepository.findById` |
| `eventPublishAnchor` | `new LocationActivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-002` |

**方法契约摘要**：前置条件为位置存在且非已废弃；持久化影响为状态切换为 ACTIVE；失败语义为非法状态迁移时报错；事件触发为发布 `LocationActivatedEvent`。**注释锚点**：加载 -> 校验 -> 状态迁移 -> 事件。

---

#### 5.1.3 `editLocationInfo(EditLocationInfoCommand cmd)` → `void`

**命令文件**：`EditLocationInfoCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 位置标识 |
| `name` | `String` | 新名称 |
| `formEnum` | `String` | 物理形式 code（库房维护页可改） |
| `modeEnum` | `String` | 位置模式 code |
| `typeJson` | `String` | 扩展 JSON |
| `organizationId` | `Long` | 挂靠机构（科室药房改上级科室） |
| `displayOrder` | `Integer` | 显示顺序 |
| `pyStr` | `String` | 拼音码 |
| `wbStr` | `String` | 五笔码 |
| `changedFields` | `List<String>` | 变更字段白名单 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `LocationRepository.existsByParentIdAndNameAndIdNot` |
| `eventPublishAnchor` | `new LocationInfoEditedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-003` |

**方法契约摘要**：前置条件为未废弃且目标名称在同父级唯一；持久化影响为更新名称、形态、模式、扩展 JSON、排序、检索码及可选 `organizationId`；失败语义为重名或状态不允许时拒绝；事件触发为发布 `LocationInfoEditedEvent`。**注释锚点**：校验 -> 变更 -> 持久化 -> 事件。

---

#### 5.1.4 `suspendLocation(SuspendLocationCommand cmd)` → `void`

**命令文件**：`SuspendLocationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 位置标识 |
| `reason` | `String` | 暂停原因 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertSuspendAllowed` |
| `statusTransitionMethod` | `Location.transitToSuspended` |
| `repositoryChecks` | `LocationRepository.findById` |
| `eventPublishAnchor` | `new LocationSuspendedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-004` |

**方法契约摘要**：前置条件为仅 ACTIVE 可暂停且占用床位禁止暂停；持久化影响为状态改为 SUSPENDED；失败语义为状态不合法或业务占用冲突时报错；事件触发为发布 `LocationSuspendedEvent`。**注释锚点**：校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.5 `resumeLocation(ResumeLocationCommand cmd)` → `void`

**命令文件**：`ResumeLocationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 位置标识 |
| `operatorId` | `Long` | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertResumeAllowed` |
| `statusTransitionMethod` | `Location.transitToActive` |
| `repositoryChecks` | `LocationRepository.findById` |
| `eventPublishAnchor` | `new LocationResumedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-005` |

**方法契约摘要**：前置条件为仅 SUSPENDED 可恢复；持久化影响为状态恢复 ACTIVE；失败语义为重复恢复或状态非法时报错；事件触发为发布 `LocationResumedEvent`。**注释锚点**：加载 -> 校验 -> 状态迁移 -> 事件。

---

#### 5.1.6 `deactivateLocation(DeactivateLocationCommand cmd)` → `void`

**命令文件**：`DeactivateLocationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 位置标识 |
| `reason` | `String` | 停用原因 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertDeactivateAllowed` |
| `statusTransitionMethod` | `Location.transitToInactive` |
| `repositoryChecks` | `LocationRepository.existsActiveChildren` |
| `eventPublishAnchor` | `new LocationDeactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-006` |

**方法契约摘要**：前置条件为仅 ACTIVE/SUSPENDED 可停用且无启用子节点、占用床位先释放；持久化影响为状态迁移到 INACTIVE；失败语义为子树/占用冲突时报错；事件触发为发布 `LocationDeactivatedEvent`。**注释锚点**：递归校验 -> 状态迁移 -> 持久化 -> 事件。

---

#### 5.1.7 `abolishLocation(AbolishLocationCommand cmd)` → `void`

**命令文件**：`AbolishLocationCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 位置标识 |
| `reason` | `String` | 废弃原因 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertAbolishAllowed` |
| `statusTransitionMethod` | `Location.markDeleted` |
| `repositoryChecks` | `LocationRepository.existsActiveReferenceByEncounter` |
| `eventPublishAnchor` | `new LocationAbolishedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-007` |

**方法契约摘要**：前置条件为仅草稿/已停用可废弃、无活跃引用且子节点已收口；持久化影响为 `isDeleted=true` 并保留审计信息；失败语义为存在活跃引用时拒绝；事件触发为发布 `LocationAbolishedEvent`。**注释锚点**：引用校验 -> 逻辑删除 -> 持久化 -> 事件。

---

#### 5.1.8 `occupyBed(OccupyBedCommand cmd)` → `void`

**命令文件**：`OccupyBedCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 床位位置标识 |
| `encounterId` | `Long` | 就诊标识 |
| `occupiedAt` | `LocalDateTime` | 占用时间 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertBedOccupiable` |
| `statusTransitionMethod` | `Location.transitOperationalToOccupied` |
| `repositoryChecks` | `LocationRepository.findById` |
| `eventPublishAnchor` | `new BedOccupiedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-008` |

**方法契约摘要**：前置条件为仅床位类型、位置启用且当前运营状态可占用；持久化影响为运营状态切换为 OCCUPIED；失败语义为非床位或状态冲突时报错；事件触发为发布 `BedOccupiedEvent`。**注释锚点**：形态校验 -> 运营迁移 -> 持久化 -> 事件。

---

#### 5.1.9 `releaseBed(ReleaseBedCommand cmd)` → `void`

**命令文件**：`ReleaseBedCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 床位位置标识 |
| `encounterId` | `Long` | 原就诊标识 |
| `releasedAt` | `LocalDateTime` | 释放时间 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertBedReleasable` |
| `statusTransitionMethod` | `Location.transitOperationalToIdle` |
| `repositoryChecks` | `LocationRepository.findById` |
| `eventPublishAnchor` | `new BedReleasedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-009` |

**方法契约摘要**：前置条件为床位当前必须处于 OCCUPIED；持久化影响为运营状态迁移到 IDLE；失败语义为重复释放或非法状态时报错；事件触发为发布 `BedReleasedEvent`。**注释锚点**：校验 -> 迁移 -> 持久化 -> 事件。

---

#### 5.1.10 `reserveBed(ReserveBedCommand cmd)` → `void`

**命令文件**：`ReserveBedCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `locationId` | `Long` | 床位位置标识 |
| `reservedReason` | `String` | 预留原因 |
| `reservedAt` | `LocalDateTime` | 预留时间 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `Location.assertBedReservable` |
| `statusTransitionMethod` | `Location.transitOperationalToReserved` |
| `repositoryChecks` | `LocationRepository.findById` |
| `eventPublishAnchor` | `new BedReservedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-LOC-W-010` |

**方法契约摘要**：前置条件为床位处于空闲且位置状态为 ACTIVE；持久化影响为运营状态迁移到 RESERVED；失败语义为状态不满足时报错；事件触发为发布 `BedReservedEvent`。**注释锚点**：校验 -> 运营迁移 -> 持久化 -> 事件。

### 5.2 读侧 — `LocationQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；条件在仓储派生方法中下推，**禁止** `findAll()` 后内存过滤。
例外：`listWardsByOrganization` / `listByOrganizationIdAndFormCode(WARD)` 先按形态（及启用态）收窄全院病区，再在内存按主挂靠 ∪ `type_json.servedOrganizationIds` 过滤（院级病区规模可控；避免新建关系表）。

**出参**：见 §4.6；带 QueryModel 的方法在表中标注类名，其余为 `List<Location>` / `Map` / `Long`。

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `listWardsByOrganization` | `Long organizationId` | `List<WardQueryModel>` 或 `List<Location>` | `WARD` + `ACTIVE`；主挂靠或 `type_json.servedOrganizationIds` 命中 | `TC-LOC-R-001` |
| `wardServesOrganization` | `Location ward, Long organizationId` | `boolean` | 主挂靠 ∪ 附加服务科 | — |
| `countBedsByWard` | `Long wardId`, `boolean vacantOnly` | `Long` | 经 `HOUSE` 汇总 `BED`；`vacantOnly`→`NOT_IN_USE` | `TC-LOC-R-002` |
| `countTotalBedsByWard` | `Long wardId` | `Long` | `countBedsByWard(wardId, false)` | `TC-LOC-R-002a` |
| `listRoomsUnderWard` | `Long wardLocationId` | `List<Location>` | `parentId` + `HOUSE`，**不限**状态 | `TC-LOC-R-005` |
| `listHousesUnderWard` | `Long wardLocationId` | `List<Location>` | `parentId` + `HOUSE` + `ACTIVE` | `TC-LOC-R-010` |
| `listBedsUnderParent` | `Long parentLocationId` | `List<Location>` | `parentId` + `BED` | `TC-LOC-R-006` |
| `listAllByFormCode` | `String formCode` | `List<Location>` | `findAllByFormEnum` | `TC-LOC-R-007` |
| `listByOrganizationIdAndFormCode` | `Long orgId`, `String formCode` | `List<Location>` | 机构 + 形态 | `TC-LOC-R-008` |
| `listChildrenByParentBusNo` | `String parentBusNo` | `List<Location>` | 前缀 + 仅多一段；排序见 §4.6.5 | `TC-LOC-R-009` |
| `listChildrenByParentBusNo` | `String parentBusNo`, `String formCode` | `List<Location>` | 上式 + `form_enum` 过滤 | `TC-LOC-R-009a` |
| `listActiveHousesByWardBusNo` | `String wardBusNo` | `List<Location>` | `listChildrenByParentBusNo(ward, HOUSE)` + `ACTIVE` | `TC-LOC-R-011` |
| `listAllActiveWards` | — | `List<Location>` | 全机构 `WARD` + `ACTIVE` | `TC-LOC-R-012` |
| ~~`listManagedWardsForPractitioner`~~ | — | — | **已移除**；护士站见 **§5.2.1** | — |
| `listPharmaciesByOrganization` | `Long organizationId` | `List<PharmacyQueryModel>` | 仅 `PHARMACY` + `ACTIVE` | `TC-LOC-R-003` |
| `listDepartmentPharmaciesByOrganization` | `Long organizationId` | `List<Location>` | 仅 `DEPARTMENT_PHARMACY` + `ACTIVE` | `TC-LOC-R-003b` |
| `listPharmacyPartyByOrganization` | `Long organizationId` | `List<Location>` | `PHARMACY` + `DEPARTMENT_PHARMACY` + `ACTIVE`（显式并集） | `TC-LOC-R-003c` |
| `listWarehousesByOrganization` | `Long organizationId` | `List<WarehouseQueryModel>` | `WAREHOUSE` + `ACTIVE` | `TC-LOC-R-004` |
| `listAllActivePharmacies` | — | `List<Location>` | 仅 `PHARMACY` + `ACTIVE`（药房业务线；**不含**科室药房） | `TC-LOC-R-015` |
| `listAllActivePharmacyOnlyLocations` | — | `List<Location>` | 同 `listAllActivePharmacies` | `TC-LOC-R-015a` |
| `listAllActivePharmacyPartyLocations` | — | `List<Location>` | `PHARMACY` + `DEPARTMENT_PHARMACY` + `ACTIVE`（药库出库目的等显式并集） | `TC-LOC-R-015b` |
| `listAllActiveWarehouses` | — | `List<Location>` | `WAREHOUSE` + `ACTIVE` | `TC-LOC-R-016` |
| `listAllActiveDepartmentPharmacies` | — | `List<Location>` | 仅 `DEPARTMENT_PHARMACY` + `ACTIVE` | `TC-LOC-R-016b` |
| `listAllWarehouseAndPharmacyLocations` | — | `List<Location>` | `WAREHOUSE` + `PHARMACY` + `DEPARTMENT_PHARMACY`（不限状态） | `TC-LOC-R-017` |
| `findActivePharmaciesByIds` | `Collection<Long> locationIds` | `List<Location>` | `id IN` + `PHARMACY`/`DEPARTMENT_PHARMACY` + `ACTIVE` | `TC-LOC-R-018` |
| `findActiveWarehousesByIds` | `Collection<Long> locationIds` | `List<Location>` | `id IN` + `WAREHOUSE` + `ACTIVE` | `TC-LOC-R-019` |
| `listLocationsByType` | `Long organizationId`, `int typeSortOrder` | `List<Location>` | `2`→`WARD`；`16`→`listPharmacyPartyByOrganization`（药房族）；`17`→`WAREHOUSE` | `TC-LOC-R-021` |
| `requireSupplyRequestTypeForLocationId` | `Long locationId` | `SupplyRequestType` | `WAREHOUSE`→`CABINET`；`PHARMACY`/`DEPARTMENT_PHARMACY`→`PHARMACY`；`WARD`→`DEPARTMENT` | `TC-LOC-R-020` |
| `findOptionalById` / `mapNamesByIds` / `findLocationNamesByIds` | 见方法签名 | `Optional` / `Map` | 批量 `findAllById` | `TC-LOC-R-014` |

#### 5.2.1 护士站管理病区（**AS-IP-NUR-001** / **GAP-IP-NUR-003**；**2026-06 收敛**）

> 原 **`listManagedWardsForPractitioner`** 已从 **`LocationQueryService` 删除**（跨聚合违规）。现行编排：

| 步骤 | 说明 |
| ---- | ---- |
| 1 | **`HealthPractitionerManagedScopeResolver`**（`PractitionerManagedScopeLoader` → **DM-ADM-006** `resolveManagedScope`；**App 禁止直读**登录上下文） |
| 2 | **`CurrentManagedWardLocationIdsResolver#listManagedWardLocationIdsOrEmpty`**（队列 Gate）或 **`#listManagedActiveWardsOrEmpty`**（地理树初始化） |
| 3 | Resolver 内调用本聚合 **`findAllByIdInAndFormEnumAndStatusEnum(ids, WARD, ACTIVE)`** + `sortByDisplayOrder` |
| 4 | 非严格模式且步骤 1 为空 → 可回退 **`listAllActiveWards()`**（与 **BR-IP-NUR-001** 一致）；严格模式无管辖 → 空表 |

**依赖注入**：`LocationQueryService` **仅**注入本聚合 `LocationRepository`；**禁止**注入 **DM-ADM-006** `*QueryService`。

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 仓储单次/批量调用 -> 组装出参（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-LOC-001 | 被占用床位禁止暂停/停用，先释放后迁移 |
| BR-LOC-002 | 同父级下名称唯一，落地唯一校验 |
| BR-LOC-003 | `busNo` 创建后不可修改 |
| BR-LOC-004 | 物理形式受层级约束，禁止逆层级嵌套 |
| BR-LOC-005 | 运营状态仅床位可维护 |
| BR-LOC-006 | 存在启用/暂停子节点时父级不可停用 |
| BR-LOC-007 | 机构引用必须存在且可用 |
| BR-LOC-008 | 活跃就诊引用存在时不可废弃 |
| BR-LOC-009 | 层级深度上限控制（最多 6 层） |
| BR-LOC-010 | `mode=KIND` 不可参与患者分配 |
| BR-LOC-011 | 枚举仅用 code；禁止整型/sortOrder 互转及错误形态（如 WAREHOUSE 冒充病房） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `LocationRegisteredEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,name,formEnum,organizationId,locationBusNo,wardId,wardName` | `registerLocation` |
| `LocationActivatedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,name,formEnum` | `activateLocation` |
| `LocationSuspendedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,reason` | `suspendLocation` |
| `LocationResumedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId` | `resumeLocation` |
| `LocationDeactivatedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,reason,formEnum` | `deactivateLocation` |
| `LocationAbolishedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,reason` | `abolishLocation` |
| `LocationInfoEditedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,changedFields` | `editLocationInfo` |
| `BedOccupiedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,encounterId,occupiedAt` | `occupyBed` |
| `BedReleasedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,encounterId,releasedAt` | `releaseBed` |
| `BedReservedEvent` | `com.zxplat.domain.share.events.administration.location` | `locationId,reservedReason,reservedAt` | `reserveBed` |

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- Location 聚合仅允许逻辑删除，禁止物理删除。
- `isDeleted=true` 的位置默认不在业务查询结果中返回。
- 被 Encounter 活跃引用的位置不得执行废弃操作。
- 子树停用/废弃前需先满足下级状态收口约束。
- 床位运营状态变更需与位置生命周期状态协同校验。

---

## 9. 框架优先适配规则

- 软删、审计字段、工作单元事务、beforeCommit 事件发布遵循项目框架能力。
- 领域服务与查询服务采用直接实现类，不默认引入接口+实现双层。
- 一方法一 Command，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口，复杂查询下沉 `queries`。
- 分页与统计必须在数据库侧保证一致口径。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整无遗漏）
- [ ] §5.1 写方法小节数量 = 10，与 DM §6.1 方法名一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部读侧方法（含 v1.1 护士站与管理病区）
- [ ] §5.2.1 管理病区编排与 **DM-ADM-006**、**AS-IP-NUR-001** 可追溯
- [ ] §6 BR 覆盖 BR-LOC-001 至 BR-LOC-011
- [ ] §7 事件表覆盖 DM §5 全部 10 条事件
- [ ] 软删、层级约束、床位状态语义一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-003-Location-位置.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.administration.location`

### 11.2 DoD

- 写侧 10 个方法全部落地，签名与 Command 命名与 §5 一致。
- `Location` 字段、约束、软删语义与 §4 一致。
- 所有事件按 §7 beforeCommit 发布，载荷不少于最小载荷。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧查询方法与 §5.2 / §4.6 一致；护士站管理病区经 **`CurrentManagedWardLocationIdsResolver`** 与 **DM-ADM-006** 联调通过。

### 11.3 失败处理

- 任一写方法契约无法实现时立即停机并回到文档会话修订。
- 若 DM 白名单或事件清单变更，先更新 SDD 再进入代码会话。
- 若 BR 测试绑定缺失或事件不完整，DoD 失败禁止进入成码。

---

## 变更历史

| SDD 版本 | 日期 | 摘要 |
| -------- | ---- | ---- |
| v1.0 | — | 首版 |
| v1.1 | 2026-05-20 | 对齐 DM v1.1：`bus_no` 采番、住院三级读查询、管理病区跨聚合编排、仓储派生方法表 |

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（10）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部事件（10）
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
| 2026-07-24 | **库位三分**：`listAllActivePharmacies`/`listPharmaciesByOrganization` 仅 `PHARMACY`；新增 `listAllActivePharmacyPartyLocations`/`listPharmacyPartyByOrganization`/`listDepartmentPharmaciesByOrganization`；`listLocationsByType(16)` 改走并集方法 |
| 2026-07-02 | **`listLocationsByType(16)`** 药房族并集；**`listAllActivePharmacyOnlyLocations`**（药房平级调拨专用） |
| 2026-06-30 | 增补 **`DEPARTMENT_PHARMACY`**（科室药房）；`listAllActivePharmacies` / `findActivePharmaciesByIds` / `listPharmaciesByOrganization` 含药房族；`requireSupplyRequestTypeForLocationId` 映射科室药房→`PHARMACY`；`WarehouseAppService` 库房维护页（平铺列表、形态 code 传参）；禁止 `16`/`17`/`18` 数字形态码 |
| 2026-06-03 | §5.2.1 护士站 App 须用 `CurrentManagedWardLocationIdsResolver`；与 B.6.5 对齐 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
