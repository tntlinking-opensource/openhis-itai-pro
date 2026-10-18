# DM-CLN-010 ProcedureDefinition 诊疗项目执行定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-010-ProcedureDefinition-诊疗项目执行定义.md`（minimal canonical，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Clinical · 手术/操作目录定义  
> **聚合**：ProcedureDefinition（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CLN-010 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.clinical.proceduredefinition` | 满足 | 全小写 |
| 固定技术口径 | 满足 | 见 §3、§9 |
| DM §6.1 写侧 | 满足 | 附录 7 写方法；§5.1 全覆盖 |
| DM §6.2 读侧 | 满足 | 附录含 `pageActiveByKeyword` 等（2026-10-07 模式 B 回写） |
| DM §7 BR | 满足 | §6 映射 |
| DM §5 事件 | 满足 | §7 六事件（模式 B 源码） |

**与 DM 显式差异（采纳依据：模式 B，以源码为准）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 实体字段 | §3 节选未列 `activity_definition_id` | §4.2 含 `activityDefinitionId`（国临计费挂靠 AD，BR-PD-010） |
| 读方法 | 历史附录偏薄 | §5.2 全量：含 `mapSearchItemsByIds`、`searchActiveByKeyword`、`pageActiveByKeyword`、AD 反查等（与 DM 附录对齐） |
| 分页关键字 | §6.2 简表 | 实现含 py/wb/ybNo/clinicCode（对齐 AS BR-POC-011） |
| `delete` 入参 | — | 公有方法 `delete(Long id)`，无独立 Command 文件 |
| 事件 | DM minimal 未列表 | 六类事件见 §7（`share/events/clinical/proceduredefinition`） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **ProcedureDefinition**（`cln_procedure_definition`）双源目录（国临 3.0 / 医保手术）维护、对码同步、导入 upsert 与读侧检索。
- 一方法一 Command（`delete` 除外，主键入参）；导入按 `(sourceEnum, procedureCode)` upsert。
- **单次成稿目标**：与 `ProcedureDefinitionDomainService` / `ProcedureDefinitionQueryService` 对齐。

### 1.2 边界

- **在内**：目录 CRUD、启停、软删、两路导入（国临 Excel / 医保 1308）、双码多对多互指、国临挂靠 AD。
- **在外**：医嘱开立编排、ActivityDefinition 本体维护、患者侧 Procedure 执行事实。
- **跨域**：`activityDefinitionId` 仅存外键，不校验 AD 存在性（应用层 Gate）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`；事件类 `zxplat-core/zxplat-share/.../events/clinical/proceduredefinition`。

### 2.2 包结构（`com.zxplat.domain.clinical.proceduredefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ProcedureDefinition` |
| `command` | `AddProcedureDefinitionCommand`、`EditProcedureDefinitionCommand`、`EditProcedureDefinitionStartCommand`、`EditProcedureDefinitionStopCommand`、`ClinicalProcV3ImportRowCommand`、`GeneralOpImportRowCommand` |
| `repository` | `ProcedureDefinitionRepository` |
| `service` | `ProcedureDefinitionDomainService`、`ProcedureDefinitionQueryService`、`ClinicalProcV3CatalogImportWriter`、`GeneralOpCatalogImportWriter` |
| `queries.model` | `ProcedureDefinitionPageItemQueryModel`、`ProcedureDefinitionDetailQueryModel`、`ProcedureDefinitionSearchItemQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.clinical.proceduredefinition` |

**存储口径**：`source_enum`、`status_enum` → `String`；`yb_flag`/`yb_match_flag` → `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`activityDefinitionId` 可空 |
| 时间 | 审计 `LocalDateTime`（基类） |
| `*_enum` | `String`（PublicationStatus、来源枚举 code） |
| `*_code` | `typeCode`、`clinicCode` → `String` |
| 软删 | 框架 `isDeleted`；`delete` 使用仓储软删 |
| 事件 | `IDomainEventPublisher.publish`，**beforeCommit** |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cln_procedure_definition` | `ProcedureDefinition` | 手术操作定义聚合根 |

### 4.2 聚合根 `ProcedureDefinition`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `sourceEnum` | `String` | 是 | `clinical_proc_v3` / `general_op` 等 |
| `procedureCode` | `String` | 是 | 来源内主编码 |
| `name` | `String` | 是 | 名称 |
| `pyStr` | `String` | 否 | 拼音 |
| `wbStr` | `String` | 否 | 五笔 |
| `typeCode` | `String` | 否 | 类别 |
| `description` | `String` | 否 | 描述 |
| `ybFlag` | `Boolean` | 否 | 医保标记 |
| `ybNo` | `String` | 否 | 医保手术码 |
| `ybMatchFlag` | `Boolean` | 否 | 对码标记 |
| `clinicCode` | `String` | 否 | 国临码 |
| `statusEnum` | `String` | 是 | 发布状态 |
| `activityDefinitionId` | `Long` | 否 | 挂靠 AD（国临计费） |
| `isDeleted` | `Boolean` | 系统 | 软删 |

**不变式方法**：`assertCoreFields`、`rebuildSearchKey`、`enable`、`disable`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `ProcedureDefinitionRepository extends JpaRepository<ProcedureDefinition, Long>`
- `existsBySourceEnumAndProcedureCode` / `AndIdNot`
- `findByIdIn`、`findBySourceEnumAndProcedureCode`、`findByActivityDefinitionId(In)`、`findByClinicCodeIn`、`findByProcedureCodeIn` 等（对码同步与读侧派生）

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ProcedureDefinitionPageItemQueryModel`（对应 `page`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `cln_procedure_definition.id` | |
| `procedureCode` | `String` | procedure_code | |
| `name` | `String` | name | |
| `sourceEnum` | `String` | source_enum | |
| `statusEnum` | `String` | status_enum | |
| `ybFlag` | `Boolean` | yb_flag | |
| `ybNo` | `String` | yb_no | |
| `clinicCode` | `String` | clinic_code | |
| `typeCode` | `String` | type_code | |
| `description` | `String` | description | |
| `pyStr` | `String` | py_str | |
| `wbStr` | `String` | wb_str | |
| `ybMatchFlag` | `Boolean` | yb_match_flag | |
| `activityDefinitionId` | `Long` | activity_definition_id | |

#### 4.6.2 `ProcedureDefinitionDetailQueryModel`（对应 `getOne`）

字段同 §4.6.1，含全部业务列（与 `getOne` 装配一致）。

#### 4.6.3 `ProcedureDefinitionSearchItemQueryModel`（对应 `searchClinicalProcV3ByKeyword` 及 AD 反查）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | id | |
| `procedureCode` | `String` | procedure_code | |
| `name` | `String` | name | |
| `pyStr` | `String` | py_str | |
| `wbStr` | `String` | wb_str | |
| `statusEnum` | `String` | status_enum | |
| `ybNo` | `String` | yb_no | |
| `activityDefinitionId` | `Long` | activity_definition_id | |

#### 4.6.4 `Map<String,String>`（对应 `mapYbNoByClinicCodes`）

键 `clinicCode`，值 `ybNo`；无独立 QueryModel 类。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ProcedureDefinitionDomainService`

#### 5.1.1 `add(AddProcedureDefinitionCommand cmd)` → `Long`

**命令文件**：`AddProcedureDefinitionCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `sourceEnum` | `String` | 来源 |
| `procedureCode` | `String` | 主编码；空则序列号 |
| `name` | `String` | 名称 |
| `pyStr` | `String` | 拼音 |
| `wbStr` | `String` | 五笔 |
| `typeCode` | `String` | 类别 |
| `description` | `String` | 描述 |
| `statusEnum` | `String` | 发布状态 |
| `ybFlag` | `Boolean` | 医保标记 |
| `ybNo` | `String` | 医保码 |
| `ybMatchFlag` | `Boolean` | 对码标记 |
| `clinicCode` | `String` | 国临码 |
| `activityDefinitionId` | `Long` | 挂靠 AD |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCoreFields` + `rebuildSearchKey` + `applyCrossCodeFields` |
| `statusTransitionMethod` | 命令 `statusEnum` 直写 |
| `repositoryChecks` | `assertCodeUnique` |
| `eventPublishAnchor` | `ProcedureDefinitionCreatedEvent` + publish |
| `unitTestCaseId` | `TC-DM-CLN-010-W-001` |

**方法契约摘要**：持久化后 `syncPeerCatalog`；失败编码冲突。**注释锚点**：解析编码 → 双码规范化 → save → 对端同步 → 事件。

---

#### 5.1.2 `edit(EditProcedureDefinitionCommand cmd)` → `void`

**命令文件**：`EditProcedureDefinitionCommand.java`

**字段全集**：同 `add`，另 **`id` 必填**；`statusEnum` 空则保持；`activityDefinitionId` 仅非 null 时改绑。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 同 add |
| `statusTransitionMethod` | 可选更新 status |
| `repositoryChecks` | `loadById` + 唯一校验 |
| `eventPublishAnchor` | `ProcedureDefinitionEditedEvent` |
| `unitTestCaseId` | `TC-DM-CLN-010-W-002` |

**方法契约摘要**：不存在抛 `PROCEDURE_DEFINITION_NOT_FOUND`。**注释锚点**：load → 改码分支 → save → syncPeer → 事件。

---

#### 5.1.3 `stop(EditProcedureDefinitionStopCommand cmd)` → `void`

**命令文件**：`EditProcedureDefinitionStopCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `ids` | `List<Long>` | 批量主键 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `disable` |
| `statusTransitionMethod` | `disable` → INACTIVE |
| `repositoryChecks` | `findByIdIn` 数量须等于 ids |
| `eventPublishAnchor` | `ProcedureDefinitionDisabledEvent` |
| `unitTestCaseId` | `TC-DM-CLN-010-W-003` |

**方法契约摘要**：仅改发布状态，非软删。**注释锚点**：批量 load → disable → saveAll → 事件。

---

#### 5.1.4 `delete(Long id)` → `void`

**命令文件**：无；入参 **`Long id`**（应用层直传主键）。

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 方法入参 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 未删行存在 |
| `eventPublishAnchor` | `ProcedureDefinitionDeletedEvent` |
| `unitTestCaseId` | `TC-DM-CLN-010-W-004` |

**方法契约摘要**：软删；不存在抛 NOT_FOUND。**注释锚点**：find → delete → 事件。

---

#### 5.1.5 `start(EditProcedureDefinitionStartCommand cmd)` → `void`

**命令文件**：`EditProcedureDefinitionStartCommand.java`（字段 `ids`）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `enable` |
| `statusTransitionMethod` | ACTIVE |
| `repositoryChecks` | 整批命中 |
| `eventPublishAnchor` | `ProcedureDefinitionEnabledEvent` |
| `unitTestCaseId` | `TC-DM-CLN-010-W-005` |

**方法契约摘要**：对称于 `stop`。

---

#### 5.1.6 `importClinicalProcV3Catalog(List<ClinicalProcV3ImportRowCommand> rows[, IntConsumer progressCallback])` → `int`

**命令文件**：行级 `ClinicalProcV3ImportRowCommand.java`

**字段全集（行）**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `procedureCode` | `String` | 国临码 |
| `name` | `String` | 名称 |
| `typeCode` | `String` | 类别 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | ImportWriter 内 upsert + 双码规则 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `(clinical_proc_v3, procedureCode)` upsert |
| `eventPublishAnchor` | `ProcedureDefinitionCatalogImportedEvent`（source=clinical_proc_v3, written） |
| `unitTestCaseId` | `TC-DM-CLN-010-W-006` |

**方法契约摘要**：非法行跳过；分批 1000；返回写入条数。**注释锚点**：归并 byCode → 分批 writer → 汇总 → 事件。

---

#### 5.1.7 `importGeneralOpCatalog(List<GeneralOpImportRowCommand> rows[, IntConsumer progressCallback])` → `int`

**命令文件**：`GeneralOpImportRowCommand.java`（`procedureCode`、`name`、`pyStr`、`fileClinicCode`、`fileClinicName`、`bindClinicCode`）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `bindAndSyncClinical` 内 `syncFromInsurance`（补建+空 yb_no）；医保行 `clinic_code` 空才写 |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `(general_op, procedureCode)` upsert；`(clinical_proc_v3, fileClinicCode)` 仅插入缺失 |
| `eventPublishAnchor` | `ProcedureDefinitionCatalogImportedEvent`（source=general_op） |
| `unitTestCaseId` | `TC-DM-CLN-010-W-007` |

**方法契约摘要**：非法行跳过；分批 1000；仅 16 列有临床版码时补建/绑定国临。16 列空或占位 `null` 时不使用第 11 列，也不清已有对照。**注释锚点**：归并 → 绑定+同步国临 → 写医保 → 事件。

### 5.2 读侧 — `ProcedureDefinitionQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `page` | `ProcedureDefinitionPageQuery` + `Pageable` | `Page<ProcedureDefinitionPageItemQueryModel>` | source 筛选 + 关键字 | `TC-DM-CLN-010-R-001` |
| `getOne` | `Long id` | `ProcedureDefinitionDetailQueryModel` | 已删抛 NOT_FOUND | `TC-DM-CLN-010-R-002` |
| `mapSearchItemsByIds` | `Collection<Long> ids` | `Map<Long, ProcedureDefinitionSearchItemQueryModel>` | 按主键装载；软删不入图；供 SR.procedure_definition_id 展示/病案 | `TC-DM-CLN-010-R-007` |
| `searchClinicalProcV3ByKeyword` | `String keyword` | `List<ProcedureDefinitionSearchItemQueryModel>` | ACTIVE + clinical_proc_v3 | `TC-DM-CLN-010-R-003` |
| `searchActiveByKeyword` | `String keyword` | `List<ProcedureDefinitionSearchItemQueryModel>` | ACTIVE；不限 source；空关键字→空列表 | `TC-DM-CLN-010-R-008` |
| `pageActiveByKeyword` | `String keyword` + `Pageable` | `Page<ProcedureDefinitionSearchItemQueryModel>` | ACTIVE；不限 source；**空关键字可分页浏览**（医生站术式选品） | `TC-DM-CLN-010-R-009` |
| `mapYbNoByClinicCodes` | `Collection<String> clinicCodes` | `Map<String,String>` | 国临→缺省医保码（优先国临 `yb_no`） | `TC-DM-CLN-010-R-004` |
| `findActiveClinicalByActivityDefinitionId` | `Long activityDefinitionId` | `ProcedureDefinitionSearchItemQueryModel` | 挂靠 AD 反查（兼容旧路径；术式真源已迁 SR.procedureDefinitionId） | `TC-DM-CLN-010-R-005` |
| `mapActiveClinicalByActivityDefinitionIds` | `Collection<Long> ids` | `Map<Long, ProcedureDefinitionSearchItemQueryModel>` | 源码扩展 | `TC-DM-CLN-010-R-006` |

**读方法注释锚点（统一）**：软删过滤 → Specification/仓储 → 投影 QueryModel。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PROCDEF-001 | 启停/软删与医嘱引用：当前以代码校验为准；停用 `disable` 非软删 |
| BR-PROCDEF-002 | 导入行须 code+name；重复 `(source, procedureCode)` upsert（ImportWriter） |
| BR-PD-001～010（源码） | 编码唯一、双码规范化、AD 挂靠见 DomainService JavaDoc |
| BR-PD-006 | 多对多互指：`syncPeerCatalog` 不拆其它行；对端空字段才回写；国临 `yb_no` 为 4401 缺省上传码；`importGeneralOpCatalog` 仅用 1308 第 16 列生成/绑定国临，空列不把医保码写成国临码、不清已有对照 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `ProcedureDefinitionCreatedEvent` | `com.zxplat.domain.share.events.clinical.proceduredefinition` | id, sourceEnum, procedureCode, name, statusEnum | `add` |
| `ProcedureDefinitionEditedEvent` | 同上 | id, sourceEnum, procedureCode, changedFields | `edit` |
| `ProcedureDefinitionDisabledEvent` | 同上 | idList, statusEnum | `stop` |
| `ProcedureDefinitionEnabledEvent` | 同上 | idList, statusEnum | `start` |
| `ProcedureDefinitionDeletedEvent` | 同上 | id, sourceEnum, procedureCode | `delete` |
| `ProcedureDefinitionCatalogImportedEvent` | 同上 | sourceEnum, writtenCount | `importClinicalProcV3Catalog` / `importGeneralOpCatalog` |

**发布时机**：全部 **beforeCommit**；save 成功后再 publish。

---

## 8. 删除与可见性契约

- `delete` 为软删；读侧 `isDeleted=false`。
- 启停不等于删除（BR-PD-007）。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；ImportWriter 分批避免 OOM。
- 关键字检索精确优先 `KeywordExactFirstOrderSupport`。

---

## 10. 生成与验收清单

- [ ] 七写方法契约与事件六类
- [ ] 实体含 `activityDefinitionId`
- [ ] 读侧 4+2 方法
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-CLN-010 + 本 SDD。

### 11.2 DoD

- 与模式 B 源码一致或可一次性补全差异。

### 11.3 失败处理

- 读方法扩展须回写 DM 附录。

---

## 定稿检查清单（可打勾）

- [x] 写方法 7 = DM 附录
- [x] 事件 6 = 源码
- [x] §4.2 与实体对齐
- [x] DOC-CONFLICT 已记差异表

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 19 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 18 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **96，可进入代码会话** |

---

**本 SDD 正文结束。**
