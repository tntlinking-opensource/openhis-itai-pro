# DM-ADM-001 Patient 患者 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-001-Patient-患者.md`（**v1.3**，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration（管理域 · 患者管理）  
> **聚合**：Patient（子实体：`PatientIdentifier`）

> **边界说明**：学籍/公费学生名单已迁出 `zxplat-module-campus`（`cmp_patient_student` / DM-CMP-STU-001）；本 SDD **不包含** `PatientStudent` / `adm_patient_student`。

---

## 输入完整性检查结果


| 最小输入项                                                  | 状态  | 说明                     |
| ------------------------------------------------------ | --- | ---------------------- |
| DM 路径                                                  | 满足  | 已绑定 DM-ADM-001         |
| SDD 输出路径                                               | 满足  | 本文件                    |
| 目标模块                                                   | 满足  | `{{TARGET_MODULE}}`    |
| 包规则 `com.zxplat.domain.administration.patient` | 满足  | `patient` 全小写          |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等）   | 满足  | 见 §9                   |
| DM 第 6 章写/读白名单                                         | 满足  | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR                                            | 满足  | §6 映射并可追溯              |
| DM 第 5 章事件                                             | 满足  | §7                     |


**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**


| 点      | DM 差异位置                     | SDD 采纳                                                       |
| ------ | --------------------------- | ------------------------------------------------------------ |
| 时间类型统一 | §3.1/§3.2（DateTime 文本） | 成码口径统一为 `LocalDateTime`                                      |
| 事件包路径  | §5（仅事件名）                    | 固定到 `com.zxplat.domain.share.events.administration.patient` |


---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Patient 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成 `{{TARGET_MODULE}}` 领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出；子表同步采用 **全量对齐 + 软删旧行**。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口在文档阶段关闭。

### 1.2 边界

- **在内**：`adm_patient`、`adm_patient_identifier` 的聚合内状态、字段、不变式、事件发布。
- **在外**：挂号/收费/医嘱/住院等跨域流程编排、消息路由、外部系统回调协议；**学籍**（`cmp_patient_student`）归 `zxplat-module-campus`。
- **跨域仅端口声明**：对 Encounter/Account/MedicationRequest 等仅声明引用约束与前置校验，不在本聚合内实现跨域写操作。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`{{TARGET_MODULE}}`（按工程实际填写）。

### 2.2 包结构（`com.zxplat.domain.administration.patient`）


| 相对包             | 内容                                                                                                 |
| --------------- | -------------------------------------------------------------------------------------------------- |
| `entity`        | `Patient`、`PatientIdentifier`                                                     |
| `valueobject`   | `Address`、`ContactPerson`、`BloodType`                                                              |
| `enums`（共享）     | 复用 `com.zxplat.domain.share.enums`，禁止重复生成同名枚举                                              |
| `command`       | 每个写方法一个 Command 文件，必要时子命令独立文件                                                                      |
| `repository`    | `PatientRepository`、`PatientIdentifierRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events`  | 共享模块统一事件包：`com.zxplat.domain.share.events.administration.patient`                          |


**存储口径（DM 优先）**：`*_enum` 采用 `Integer`（或 DM 明示类型）；`*_code` 采用 `String`；`_flag` 采用 `Boolean`（若存量库为 0/1，领域层转换）；禁止混用。

---

## 3. 类型 / 字段命名 / 注解规则


| 规则       | 说明                                                                        |
| -------- | ------------------------------------------------------------------------- |
| 主键 / 外键  | `Long`；聚合根主键 `id`；外键 `patientId`                                          |
| 时间       | `LocalDateTime`；生日字段使用 `LocalDate`                                        |
| `*_enum` | 枚举编码列，默认 `Integer`                                                        |
| `*_code` | 字典码列，`String`                                                             |
| `_flag`  | `Boolean`                                                                 |
| 软删       | `Boolean isDeleted`，继承基类时沿用框架字段                                           |
| 实体       | `@Entity` + 显式 `@Table`/`@Column`                                         |
| 值对象      | `@Embeddable`                                                             |
| 仓储       | `interface XxxRepository extends JpaRepository<Entity, Long>`             |
| 分页       | `Pageable`；默认 `createdAt DESC`                                            |
| 事务       | 领域服务方法不机械添加 `@Transactional`                                              |
| 事件       | 领域服务内 `new XxxEvent(...)` + `DomainEventPublisher.publish`，`beforeCommit` |


---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射


| 逻辑表                      | 实体类                 | 说明       |
| ------------------------ | ------------------- | -------- |
| `adm_patient`            | `Patient`           | 患者聚合根主表  |
| `adm_patient_identifier` | `PatientIdentifier` | 非身份证证件子表 |


### 4.2 聚合根 `Patient`（字段级契约）


| 字段名（Java）           | 类型              | 必填  | 说明 / 约束                                                                                         |
| ------------------- | --------------- | --- | ----------------------------------------------------------------------------------------------- |
| `id`                | `Long`          | 系统  | 聚合根主键                                                                                           |
| `busNo`             | `String`        | 是   | 院内编码，全局唯一，创建后不可修改                                                                               |
| `name`              | `String`        | 是   | 患者姓名                                                                                            |
| `nameJson`          | `String`        | 否   | 多语言/别名 JSON                                                                                     |
| `genderEnum`        | `Integer`       | 是   | 性别编码（Gender 值对象）                                                                                |
| `birthDate`         | `LocalDate`     | 否   | 出生日期                                                                                            |
| `statusEnum`        | `Integer`       | 是   | 患者生命周期状态（`PatientStatus` 枚举）：DRAFT=1 / ACTIVE=2 / INACTIVE=3 / MERGED=4 / CANCELLED=5，见 DM §2.3 |
| `activeFlag`        | `Boolean`       | 是   | 是否启用：`statusEnum == ACTIVE` 时为 `true`，其余为 `false`；与 FHIR `Patient.active` 对齐                    |
| `tempFlag`          | `Boolean`       | 是   | `true`=临时建档，`false`=正式建档                                                                        |
| `idCard`            | `String`        | 否   | 身份证号，全局唯一                                                                                       |
| `maritalStatusEnum` | `Integer`       | 否   | 婚姻状态编码（MaritalStatus 值对象）                                                                       |
| `prfsEnum`          | `Integer`       | 否   | 职业编码                                                                                            |
| `phone`             | `String`        | 否   | 联系电话                                                                                            |
| `address`           | `Address`       | 否   | 主要地址（`@Embeddable`，内部字段展开见 §4.2.1）                                                              |
| `addressExtendJson` | `String`        | 否   | 地址扩展 JSON                                                                                       |
| `nationalityCode`   | `String`        | 否   | 民族字典码                                                                                           |
| `pyStr`             | `String`        | 否   | 拼音码                                                                                             |
| `wbStr`             | `String`        | 否   | 五笔码                                                                                             |
| `bloodType`         | `BloodType`     | 否   | 血型（`@Embeddable`，内部字段展开见 §4.2.1）                                                                |
| `workCompany`       | `String`        | 否   | 工作单位                                                                                            |
| `nativePlace`       | `String`        | 否   | 籍贯                                                                                              |
| `countryCode`       | `String`        | 否   | 国家/地区编码                                                                                         |
| `linkName`          | `ContactPerson` | 否   | 主要联系人（`@Embeddable`，内部字段展开见 §4.2.1）                                                             |
| `linkJsons`         | `String`        | 否   | 联系人扩展 JSON                                                                                      |
| `deceasedDate`      | `LocalDateTime` | 否   | 死亡时间                                                                                            |
| `deceasedReason`    | `String`        | 否   | 死亡原因                                                                                            |
| `organizationId`    | `Long`          | 是   | 建档机构标识                                                                                          |
| `isDeleted`         | `Boolean`       | 系统  | 逻辑删除标记                                                                                          |


**不变式方法（示例命名，实现须一致）**：`assertRegisterRequiredFields`、`assertIdCardUnique`、`assertStatusOperable`、`assertDeceasedDateValid`、`markDeleted`。

### 4.2.1 值对象字段展开

#### Address（对应 DM §4.4）

`@Embeddable`，通过 `@Embedded + @AttributeOverrides` 映射到 `adm_patient` 主表列。


| Java 字段名   | 类型       | @Column 列名（DB）     | 说明                      |
| ---------- | -------- | ------------------ | ----------------------- |
| `text`     | `String` | `address`          | 地址全文（冗余展示字段，与结构化字段保持一致） |
| `province` | `String` | `address_province` | 省级行政区                   |
| `city`     | `String` | `address_city`     | 市级行政区                   |
| `district` | `String` | `address_district` | 区/县级行政区                 |
| `street`   | `String` | `address_street`   | 街道详细地址                  |


#### ContactPerson（对应 DM §4.3）

`@Embeddable`，通过 `@Embedded + @AttributeOverrides` 映射到 `adm_patient` 主表列（DB 列前缀 `link_`）。


| Java 字段名       | 类型        | @Column 列名（DB）       | 说明                                |
| -------------- | --------- | -------------------- | --------------------------------- |
| `name`         | `String`  | `link_name`          | 联系人姓名                             |
| `relationCode` | `Integer` | `link_relation_code` | GB 家庭关系编码（FamilyRelationshipType） |
| `telcom`       | `String`  | `link_telcom`        | 联系人电话                             |


#### BloodType（对应 DM §4.6）

`@Embeddable`，通过 `@Embedded + @AttributeOverrides` 映射到 `adm_patient` 主表列。


| Java 字段名 | 类型        | @Column 列名（DB）   | 说明                         |
| -------- | --------- | ---------------- | -------------------------- |
| `abo`    | `Integer` | `blood_type_abo` | ABO 血型编码（BloodTypeABO 值对象） |
| `rh`     | `Integer` | `blood_type_rh`  | Rh 血型编码                    |


### 4.3 子实体 `PatientIdentifier`


| 字段名（Java）      | 类型              | 必填  | 说明         |
| -------------- | --------------- | --- | ---------- |
| `id`           | `Long`          | 是   | 子实体主键      |
| `typeCode`     | `String`        | 是   | 非身份证证件类型编码 |
| `identifierNo` | `String`        | 是   | 证件号码       |
| `stateEnum`    | `Integer`       | 否   | 标识状态编码     |
| `startTime`    | `LocalDateTime` | 否   | 有效开始时间     |
| `endTime`      | `LocalDateTime` | 否   | 有效结束时间     |
| `patientId`    | `Long`          | 是   | 所属患者标识     |
| `isDeleted`    | `Boolean`       | 系统  | 逻辑删除标记     |


**归属校验**：任何带 `id` 的更新必须 `loadByIdAndPatientId(id, patientId)`，不一致直接拒绝。

### 4.5 仓储接口

- `PatientRepository extends JpaRepository<Patient, Long>`
- `PatientIdentifierRepository extends JpaRepository<PatientIdentifier, Long>`
- 可定义查询签名：`findByIdAndIsDeletedFalse`、`existsByIdCard`、`findAllByPatientIdInAndIsDeletedFalse`。

**说明**：框架统一软删过滤场景下，不强制在所有接口名冗余 `AndIsDeletedFalse`。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `PatientPageItemQueryModel`（对应 `searchPatientsPaged`）


| 字段名（Java）        | 类型          | 来源（表.列）                       | 说明                                 |
| ---------------- | ----------- | ----------------------------- | ---------------------------------- |
| `id`             | `Long`      | `adm_patient.id`              | 患者标识                               |
| `busNo`          | `String`    | `adm_patient.bus_no`          | 院内编码                               |
| `name`           | `String`    | `adm_patient.name`            | 姓名                                 |
| `genderEnum`     | `Integer`   | `adm_patient.gender_enum`     | 性别                                 |
| `birthDate`      | `LocalDate` | `adm_patient.birth_date`      | 出生日期                               |
| `idCard`         | `String`    | `adm_patient.id_card`         | 身份证号                               |
| `phone`          | `String`    | `adm_patient.phone`           | 联系电话                               |
| `statusEnum`     | `Integer`   | `adm_patient.status_enum`     | 患者生命周期状态（PatientStatus 枚举）         |
| `activeFlag`     | `Boolean`   | `adm_patient.active_flag`     | 是否启用（statusEnum == ACTIVE 时为 true） |
| `tempFlag`       | `Boolean`   | `adm_patient.temp_flag`       | `true`=临时建档，`false`=正式建档           |
| `organizationId` | `Long`      | `adm_patient.organization_id` | 建档机构                               |


#### 4.6.2 `PatientDetailQueryModel`（对应 `loadPatientDetail`）


| 字段名（Java）     | 类型                                  | 来源（表.列）                    | 说明       |
| ------------- | ----------------------------------- | -------------------------- | -------- |
| `patient`     | `PatientCoreQueryModel`             | `adm_patient.`*            | 主档核心信息   |
| `identifiers` | `List<PatientIdentifierQueryModel>` | `adm_patient_identifier.`* | 非身份证证件列表 |


#### 4.6.3 `DuplicatePatientCheckQueryModel`（对应 `checkRegistrationDuplicatePatient`）


| 字段名（Java）         | 类型                              | 来源（表.列）         | 说明       |
| ----------------- | ------------------------------- | --------------- | -------- |
| `duplicated`      | `Boolean`                       | 规则计算            | 是否命中重复患者 |
| `matchedPatients` | `List<PatientSimpleQueryModel>` | `adm_patient.*` | 命中候选集合   |
| `ruleHits`        | `List<String>`                  | 规则计算            | 命中规则编号   |


#### 4.6.4 `PatientSimpleQueryModel`（对应 `findPatientByIdCard`）


| 字段名（Java）    | 类型          | 来源（表.列）                   | 说明   |
| ------------ | ----------- | ------------------------- | ---- |
| `id`         | `Long`      | `adm_patient.id`          | 患者标识 |
| `busNo`      | `String`    | `adm_patient.bus_no`      | 院内编码 |
| `name`       | `String`    | `adm_patient.name`        | 姓名   |
| `genderEnum` | `Integer`   | `adm_patient.gender_enum` | 性别   |
| `birthDate`  | `LocalDate` | `adm_patient.birth_date`  | 出生日期 |
| `idCard`     | `String`    | `adm_patient.id_card`     | 身份证号 |


#### 4.6.5 `BatchPatientDetailQueryModel`（对应 `batchLoadPatientDetails`）


| 字段名（Java）       | 类型                        | 来源（表.列）          | 说明    |
| --------------- | ------------------------- | ---------------- | ----- |
| `patientId`     | `Long`                    | `adm_patient.id` | 患者标识  |
| `patientDetail` | `PatientDetailQueryModel` | 聚合组装             | 单患者详情 |


#### 4.6.6 `PatientInpatientProjectionSnapshot`（对应 `mapInpatientProjectionSnapshotsByIds`）

> 路径：`queries.model.PatientInpatientProjectionSnapshot`；供 **`InpatientEncounterContextLoader`** / `InpatientInfoQueueDisplayAssembler` 批量回填（DM **§6.2.7**、**BR-PAT-023**）。

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| --- | --- | --- | --- |
| `patientName` | `String` | `adm_patient.name` | 患者姓名 |
| `genderEnum` | `String` | `adm_patient.gender_enum` | 性别枚举 **code**（B.10.8.1） |
| `birthDate` | `LocalDateTime` | `adm_patient.birth_date` | 出生日期（日初时刻，与QueryService列一致） |
| `ageText` | `String` | 派生 `birth_date` | `AgeCalculatorUtil.getAge` 展示文本 |

**不含** `idCard`：身份证仅主表字段，不进入 ``adm_encounter` + `InpatientEncounterContextQueryModel``。


---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PatientDomainService`

#### 5.1.1 `registerPatient(RegisterPatientCommand cmd)` → `Long`

**命令文件**：`RegisterPatientCommand.java`

**字段全集**：


| 字段           | 类型                                 | 说明     |
| ------------ | ---------------------------------- | ------ |
| `item`       | `RegisterPatientItemCommand`       | 单条建档输入 |
| `items`      | `List<RegisterPatientItemCommand>` | 批量建档输入 |
| `operatorId` | `Long`                             | 操作人    |
| `occurredAt` | `LocalDateTime`                    | 操作时间   |


`RegisterPatientItemCommand` 字段：`name`、`genderEnum`（`String`）、`birthDate`、`idCard`、`phone`、`tempFlag`、`organizationId`、`identifierItems`。


| 绑定项                      | 内容                                                                                                                                           |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertRegisterRequiredFields`                                                                                                       |
| `statusTransitionMethod` | `Patient.initStatusByTempFlag`（`tempFlag=true` → `statusEnum=DRAFT/activeFlag=false`；`tempFlag=false` → `statusEnum=ACTIVE/activeFlag=true`） |
| `repositoryChecks`       | `PatientRepository.existsByIdCard(idCard)`                                                                                                   |
| `eventPublishAnchor`     | `new PatientRegisteredEvent(...); DomainEventPublisher.publish(event)`                                                                       |
| `unitTestCaseId`         | `TC-PAT-W-001`                                                                                                                               |


**方法契约摘要**：前置校验姓名/性别/身份证唯一；持久化主表并按需初始化标识子表；失败回滚并返回明确冲突原因；注释锚点：校验 -> 状态初始化 -> 持久化 -> 事件发布。

---

#### 5.1.2 `modifyPatientProfile(ModifyPatientProfileCommand cmd)` → `void`

**命令文件**：`ModifyPatientProfileCommand.java`

**字段全集**：


| 字段           | 类型                                      | 说明     |
| ------------ | --------------------------------------- | ------ |
| `item`       | `ModifyPatientProfileItemCommand`       | 单条档案修改 |
| `items`      | `List<ModifyPatientProfileItemCommand>` | 批量档案修改 |
| `operatorId` | `Long`                                  | 操作人    |


`ModifyPatientProfileItemCommand` 字段：`patientId`、`name`、`nameJson`、`genderEnum`（`String`）、`birthDate`、`idCard`、`maritalStatusEnum`（`String`）、`prfsEnum`（`String`）、`phone`、`address`、`addressExtendJson`、`nationalityCode`、`workCompany`、`nativePlace`、`countryCode`、`linkName`、`linkRelationCode`、`linkTelcom`、`linkJsons`、`bloodTypeAbo`（`Integer`）、`bloodTypeRh`（`Integer`）、`identifierItems`。


| 绑定项                      | 内容                                                                          |
| ------------------------ | --------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertProfileModifiable`                                           |
| `statusTransitionMethod` | `N/A`                                                                       |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                    |
| `eventPublishAnchor`     | `new PatientProfileModifiedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-002`                                                              |


**方法契约摘要**：前置校验状态与身份证唯一；持久化主表更新并可触发标识同步；失败时整体回滚；注释锚点：加载校验 -> 字段合并 -> 持久化 -> 事件发布。

---

#### 5.1.3 `syncPatientIdentifiers(SyncPatientIdentifiersCommand cmd)` → `void`

**命令文件**：`SyncPatientIdentifiersCommand.java`

**字段全集**：


| 字段           | 类型                                        | 说明       |
| ------------ | ----------------------------------------- | -------- |
| `item`       | `SyncPatientIdentifiersItemCommand`       | 单患者证件同步  |
| `items`      | `List<SyncPatientIdentifiersItemCommand>` | 批量患者证件同步 |
| `operatorId` | `Long`                                    | 操作人      |


`SyncPatientIdentifiersItemCommand` 字段：`patientId`、`identifierItems`。  
`PatientIdentifierItemCommand` 字段：`id`、`typeCode`（`String`）、`identifierNo`、`stateEnum`（`IdentifierState`）、`startTime`、`endTime`。


| 绑定项                      | 内容                                                                            |
| ------------------------ | ----------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertIdentifierSyncAllowed`                                         |
| `statusTransitionMethod` | `PatientIdentifier.transitState`                                              |
| `repositoryChecks`       | `PatientIdentifierRepository.findAllByPatientIdAndIsDeletedFalse(patientId)`  |
| `eventPublishAnchor`     | `new PatientIdentifiersSyncedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-003`                                                                |


**方法契约摘要**：按 `typeCode+identifierNo` 全量对齐，集合外旧记录软删；失败策略全有或全无；注释锚点：前置校验 -> 子表对齐 -> 持久化 -> 事件发布。

---

#### 5.1.4 `confirmPatientIdentity(ConfirmPatientIdentityCommand cmd)` → `void`

**命令文件**：`ConfirmPatientIdentityCommand.java`

**字段全集**：


| 字段           | 类型                                        | 说明   |
| ------------ | ----------------------------------------- | ---- |
| `item`       | `ConfirmPatientIdentityItemCommand`       | 单条确认 |
| `items`      | `List<ConfirmPatientIdentityItemCommand>` | 批量确认 |
| `operatorId` | `Long`                                    | 操作人  |


`ConfirmPatientIdentityItemCommand` 字段：`patientId`。


| 绑定项                      | 内容                                                                            |
| ------------------------ | ----------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertCanConfirmIdentity`                                            |
| `statusTransitionMethod` | `Patient.confirmIdentity`                                                     |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                      |
| `eventPublishAnchor`     | `new PatientIdentityConfirmedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-004`                                                                |


**方法契约摘要**：仅允许 `DRAFT -> ACTIVE`；更新 `statusEnum=ACTIVE`、`activeFlag=true`、`tempFlag=false`；状态不合法即失败；注释锚点：状态校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.5 `updatePatientPhone(UpdatePatientPhoneCommand cmd)` → `void`

**命令文件**：`UpdatePatientPhoneCommand.java`

**字段全集**：


| 字段           | 类型                                    | 说明   |
| ------------ | ------------------------------------- | ---- |
| `item`       | `UpdatePatientPhoneItemCommand`       | 单条更新 |
| `items`      | `List<UpdatePatientPhoneItemCommand>` | 批量更新 |
| `operatorId` | `Long`                                | 操作人  |


`UpdatePatientPhoneItemCommand` 字段：`patientId`、`phone`。


| 绑定项                      | 内容                                                                        |
| ------------------------ | ------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertPhoneFormatValid`                                          |
| `statusTransitionMethod` | `N/A`                                                                     |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                  |
| `eventPublishAnchor`     | `new PatientPhoneModifiedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-005`                                                            |


**方法契约摘要**：仅更新手机号字段，状态需为 active；格式不合法拒绝；注释锚点：加载校验 -> 字段更新 -> 持久化 -> 事件发布。

---

#### 5.1.6 `deactivatePatient(DeactivatePatientCommand cmd)` → `void`

**命令文件**：`DeactivatePatientCommand.java`

**字段全集**：


| 字段           | 类型                                   | 说明   |
| ------------ | ------------------------------------ | ---- |
| `item`       | `DeactivatePatientItemCommand`       | 单条停用 |
| `items`      | `List<DeactivatePatientItemCommand>` | 批量停用 |
| `operatorId` | `Long`                               | 操作人  |


`DeactivatePatientItemCommand` 字段：`patientId`、`reason`。


| 绑定项                      | 内容                                                                      |
| ------------------------ | ----------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertCanDeactivate`                                           |
| `statusTransitionMethod` | `Patient.deactivate`                                                    |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                |
| `eventPublishAnchor`     | `new PatientDeactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-006`                                                          |


**方法契约摘要**：`ACTIVE/DRAFT` 可迁移为 `INACTIVE`，同步置 `activeFlag=false`；存在进行中引用拒绝；注释锚点：引用校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.7 `reactivatePatient(ReactivatePatientCommand cmd)` → `void`

**命令文件**：`ReactivatePatientCommand.java`

**字段全集**：


| 字段           | 类型                                   | 说明     |
| ------------ | ------------------------------------ | ------ |
| `item`       | `ReactivatePatientItemCommand`       | 单条重新启用 |
| `items`      | `List<ReactivatePatientItemCommand>` | 批量重新启用 |
| `operatorId` | `Long`                               | 操作人    |


`ReactivatePatientItemCommand` 字段：`patientId`。


| 绑定项                      | 内容                                                                      |
| ------------------------ | ----------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertCanReactivate`                                           |
| `statusTransitionMethod` | `Patient.reactivate`                                                    |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                |
| `eventPublishAnchor`     | `new PatientReactivatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-007`                                                          |


**方法契约摘要**：仅 `INACTIVE -> ACTIVE`，同步置 `activeFlag=true`；非停用状态拒绝；注释锚点：状态校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.8 `mergePatient(MergePatientCommand cmd)` → `void`

**命令文件**：`MergePatientCommand.java`

**字段全集**：


| 字段           | 类型                              | 说明   |
| ------------ | ------------------------------- | ---- |
| `item`       | `MergePatientItemCommand`       | 单条合并 |
| `items`      | `List<MergePatientItemCommand>` | 批量合并 |
| `operatorId` | `Long`                          | 操作人  |


`MergePatientItemCommand` 字段：`sourcePatientId`、`targetPatientId`、`reason`。


| 绑定项                      | 内容                                                                             |
| ------------------------ | ------------------------------------------------------------------------------ |
| `entityInvariantMethod`  | `Patient.assertMergeTargetValid`                                               |
| `statusTransitionMethod` | `Patient.markMergedTo(targetPatientId)`                                        |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(sourcePatientId/targetPatientId)` |
| `eventPublishAnchor`     | `new PatientMergedEvent(...); DomainEventPublisher.publish(event)`             |
| `unitTestCaseId`         | `TC-PAT-W-008`                                                                 |


**方法契约摘要**：源/目标必须有效且不同；源 `statusEnum` 迁移为 `MERGED`，`activeFlag=false`；注释锚点：双对象校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.9 `markPatientDeceased(MarkPatientDeceasedCommand cmd)` → `void`

**命令文件**：`MarkPatientDeceasedCommand.java`

**字段全集**：


| 字段           | 类型                                     | 说明     |
| ------------ | -------------------------------------- | ------ |
| `item`       | `MarkPatientDeceasedItemCommand`       | 单条标记死亡 |
| `items`      | `List<MarkPatientDeceasedItemCommand>` | 批量标记死亡 |
| `operatorId` | `Long`                                 | 操作人    |


`MarkPatientDeceasedItemCommand` 字段：`patientId`、`deceasedDate`、`deceasedReason`。


| 绑定项                      | 内容                                                                         |
| ------------------------ | -------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertDeceasedDateValid`                                          |
| `statusTransitionMethod` | `N/A`                                                                      |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                   |
| `eventPublishAnchor`     | `new PatientDeceasedMarkedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-009`                                                             |


**方法契约摘要**：只写死亡事实不改主状态；死亡时间晚于当前拒绝；注释锚点：时间校验 -> 字段更新 -> 持久化 -> 事件发布。

---

#### 5.1.10 `discardDraftPatient(DiscardDraftPatientCommand cmd)` → `void`

**命令文件**：`DiscardDraftPatientCommand.java`

**字段全集**：


| 字段           | 类型                                     | 说明   |
| ------------ | -------------------------------------- | ---- |
| `item`       | `DiscardDraftPatientItemCommand`       | 单条废弃 |
| `items`      | `List<DiscardDraftPatientItemCommand>` | 批量废弃 |
| `operatorId` | `Long`                                 | 操作人  |


`DiscardDraftPatientItemCommand` 字段：`patientId`。


| 绑定项                      | 内容                                                                         |
| ------------------------ | -------------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertCanDiscardDraft`                                            |
| `statusTransitionMethod` | `Patient.discardDraft`                                                     |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`                   |
| `eventPublishAnchor`     | `new PatientDraftDiscardedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-010`                                                             |


**方法契约摘要**：仅 `DRAFT` 且无业务引用可废弃，`statusEnum` 迁移为 `CANCELLED`，`activeFlag=false`；失败整体回滚；注释锚点：引用校验 -> 状态迁移 -> 持久化 -> 事件发布。

---

#### 5.1.11 `deletePatient(DeletePatientCommand cmd)` → `void`

**命令文件**：`DeletePatientCommand.java`

**字段全集**：


| 字段           | 类型                               | 说明     |
| ------------ | -------------------------------- | ------ |
| `item`       | `DeletePatientItemCommand`       | 单条逻辑删除 |
| `items`      | `List<DeletePatientItemCommand>` | 批量逻辑删除 |
| `operatorId` | `Long`                           | 操作人    |


`DeletePatientItemCommand` 字段：`patientId`、`deleteReason`。


| 绑定项                      | 内容                                                                  |
| ------------------------ | ------------------------------------------------------------------- |
| `entityInvariantMethod`  | `Patient.assertCanBeDeleted`                                        |
| `statusTransitionMethod` | `Patient.markDeleted`                                               |
| `repositoryChecks`       | `PatientRepository.findByIdAndIsDeletedFalse(patientId)`            |
| `eventPublishAnchor`     | `new PatientDeletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId`         | `TC-PAT-W-011`                                                      |


**方法契约摘要**：仅逻辑删除，终止态或活跃引用拒绝；注释锚点：引用校验 -> 删除标记 -> 持久化 -> 事件发布。

### 5.2 读侧 — `PatientQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**「出参」填 QueryModel 类名**（如 `PatientPageItemQueryModel`）；字段定义见 **§4.6 对应子节**，此处不重复列字段。


| 方法                                     | 入参                               | 出参（类名，字段见 §4.6）                             | 关键实现                   | `unitTestCaseId` |
| -------------------------------------- | -------------------------------- | ------------------------------------------- | ---------------------- | ---------------- |
| `searchPatientsPaged`                  | `PatientPagedQuery`              | `Page<PatientPageItemQueryModel>`           | 条件构建 + 分页查询 + 结果映射     | `TC-PAT-R-001`   |
| `listPatientIdsByEncounterListKeyword` | `String keyword`               | `List<Long>`                                | 姓名/编码/身份证/拼音/五笔 OR 匹配 | `TC-PAT-R-010`   |
| `loadPatientDetail`                    | `Long patientId`                 | `PatientDetailQueryModel`                   | 主表 + 子表明细加载            | `TC-PAT-R-002`   |
| `checkRegistrationDuplicatePatient`    | `DuplicatePatientCheckQuery`     | `DuplicatePatientCheckQueryModel`           | 组合条件同人校验               | `TC-PAT-R-003`   |
| `findPatientByIdCard`                  | `String idCard`                  | `PatientSimpleQueryModel`                   | 身份证精确查找并过滤终止态；多条命中抛冲突异常 | `TC-PAT-R-004`   |
| `batchLoadPatientDetails`              | `List<Long> patientIds`          | `Map<Long, BatchPatientDetailQueryModel>`   | 批量主子表查询并按 patientId 分组 | `TC-PAT-R-005`   |
| `findPatientNamesByIds`                | `Collection<Long> patientIds`    | `Map<Long, String>`                         | 仅姓名轻量批量解析              | `TC-PAT-R-006`   |
| `mapInpatientProjectionSnapshotsByIds` | `Collection<Long> patientIds`    | `Map<Long, PatientInpatientProjectionSnapshot>` | 住院QueryService人口学快照；`findAllByIdIn` + 年龄派生 | `TC-PAT-R-007`   |


**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用分页或批量 -> 组装 QueryModel（无写库）。  
`findPatientByIdCard` 命中多条时必须抛 `PATIENT_ID_CARD_CONFLICT`，禁止“取最新一条”继续流程。

---

## 6. 不变式映射（DM §7）


| BR 编号      | 实现策略摘要                                  |
| ---------- | --------------------------------------- |
| BR-PAT-001 | `registerPatient` 执行姓名/性别最小必填校验         |
| BR-PAT-002 | 主表身份证全局唯一，建档与档案修改均校验；按身份证查询多条命中时抛冲突异常 |
| BR-PAT-003 | `busNo` 由序列服务生成且不可修改                    |
| BR-PAT-004 | `syncPatientIdentifiers` 拒绝身份证类型写入子表    |
| BR-PAT-005 | 证件 `typeCode + identifierNo` 有效期内唯一     |
| BR-PAT-006 | 标识有效期 `startTime <= endTime`，过期状态与可见性联动 |
| BR-PAT-007 | 同一 typeCode 的有效标识唯一且按优先级决策              |
| BR-PAT-008 | merged/cancelled 为终止态，不可逆转              |
| BR-PAT-009 | 删除前执行活跃引用校验，禁止物理删除                      |
| BR-PAT-010 | 合并时源/目标均 active 且不同                     |
| BR-PAT-011 | 死亡时间不得晚于当前，且不触发主状态迁移                    |
| BR-PAT-012 | 手机号格式由 `updatePatientPhone` 与档案更新统一校验   |
| BR-PAT-014 | 建档同一人校验采用姓名+性别+非空比对项一致                  |
| BR-PAT-015 | 子表仅在输入非身份证证件时写入/同步                      |
| BR-PAT-016 | 年龄与生日一致性在档案修改路径校验                       |
| BR-PAT-017 | 联系人约束按配置开关执行强校验                         |
| BR-PAT-018 | 删除仅逻辑删，读侧默认过滤                           |
| BR-PAT-019 | 默认读动作仅可见未删除数据                           |
| BR-PAT-020 | 身份证读取以主表为准，子表仅非身份证语义                    |
| BR-PAT-021 | 标识状态 `old` 不可恢复                         |
| BR-PAT-022 | 证件过期与 temp 状态有效期约束联动                    |
| BR-PAT-023 | `mapInpatientProjectionSnapshotsByIds` 仅输出姓名/性别/生日/年龄；禁止将 `id_card` 写入 `InpatientEncounterContextQueryModel` |


---

## 变更记录（SDD）

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.2 | 2026-07-19 | **学籍迁出 Campus**：移除 `PatientStudent` / `adm_patient_student`、相关 Command/Query/事件/BR-PAT-013；对齐 DM **v1.3** |
| v1.1 | 2026-05-20 | §4.6.6 `PatientInpatientProjectionSnapshot`；§5.2 增补 `findPatientNamesByIds`、`mapInpatientProjectionSnapshotsByIds`；§6 映射 **BR-PAT-023**；对齐 DM **v1.1** |
| v1.0 | （既有） | 与 DM v1.0 对齐 |

---

## 7. 事件契约与触发规则（DM §5）


| 事件类名                                | 事件类包路径                                                   | 最小载荷（字段）                                                             | 触发方法                          |
| ----------------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------- | ----------------------------- |
| `PatientRegisteredEvent`            | `com.zxplat.domain.share.events.administration.patient` | `patientId,busNo,name,genderEnum,statusEnum,tempFlag,organizationId` | `registerPatient`             |
| `PatientProfileModifiedEvent`       | `com.zxplat.domain.share.events.administration.patient` | `patientId,changedFields,operatorId`                                 | `modifyPatientProfile`        |
| `PatientIdentityConfirmedEvent`     | `com.zxplat.domain.share.events.administration.patient` | `patientId,busNo,statusEnum,confirmedAt`                             | `confirmPatientIdentity`      |
| `PatientIdentifiersSyncedEvent`     | `com.zxplat.domain.share.events.administration.patient` | `patientId,identifierCount`                                          | `syncPatientIdentifiers`      |
| `PatientPhoneModifiedEvent`         | `com.zxplat.domain.share.events.administration.patient` | `patientId,phone`                                                    | `updatePatientPhone`          |
| `PatientDeactivatedEvent`           | `com.zxplat.domain.share.events.administration.patient` | `patientId,statusEnum,reason`                                        | `deactivatePatient`           |
| `PatientReactivatedEvent`           | `com.zxplat.domain.share.events.administration.patient` | `patientId,statusEnum`                                               | `reactivatePatient`           |
| `PatientMergedEvent`                | `com.zxplat.domain.share.events.administration.patient` | `sourcePatientId,targetPatientId,statusEnum,mergedAt,reason`         | `mergePatient`                |
| `PatientDeceasedMarkedEvent`        | `com.zxplat.domain.share.events.administration.patient` | `patientId,deceasedDate,deceasedReason`                              | `markPatientDeceased`         |
| `PatientDraftDiscardedEvent`        | `com.zxplat.domain.share.events.administration.patient` | `patientId,busNo`                                                    | `discardDraftPatient`         |
| `PatientDeletedEvent`               | `com.zxplat.domain.share.events.administration.patient` | `patientId,busNo,deletedAt`                                          | `deletePatient`               |


**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 患者删除语义为逻辑删除：仅置位 `isDeleted=true`，禁止物理 `DELETE`。
- 聚合根逻辑删除后，`adm_patient_identifier` 默认不可见；子表历史保留用于审计。
- 读侧方法默认仅查询 `isDeleted=false`，审计查询需显式开启“含删除”模式。
- `merged/cancelled` 终止态对象不可执行恢复到非终止态的写操作。
- 子表同步采用全量对齐 + 软删旧行，避免硬删除破坏审计。

---

## 9. 框架优先适配规则

- 软删、审计字段、工作单元事务、`beforeCommit` 事件发布遵循项目框架默认能力。
- 领域服务/查询服务采用直接实现类，不默认生成无收益接口抽象。
- 一方法一 Command 且一文件一命令对象，禁止命令容器类。
- 仓储仅保留 `JpaRepository` 接口能力，复杂查询在查询服务聚合实现。
- 分页查询禁止“先分页后内存过滤”，确保 `totalElements` 与结果口径一致。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表/子表字段逐项对齐，无遗漏（含 `statusEnum` + `activeFlag` 双字段）
- §5.1 共 11 个写方法小节，方法名与 DM §6.1 完全一致
- 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- §5.2 读方法 8 项与 DM §6.2 对齐
- §6 BR 规则映射覆盖 BR-PAT-001 至 BR-PAT-023（不含已迁出 Campus 的 BR-PAT-013）
- §7 事件 11 条与 DM §5.1 全量对齐
- 删除语义、软删可见性、分页语义、幂等同步策略均可直接落地
- 无“待确认/后续补充/占位实现”措辞

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-001-Patient-患者.md`
- 本 SDD 文件（通过结构门禁、字段门禁、方法门禁、事件门禁）
- 目标模块：`{{TARGET_MODULE}}`
- 共享枚举与事件包可用：`com.zxplat.domain.share.enums`、`com.zxplat.domain.share.events.administration.patient`

### 11.2 DoD

- 写侧 11 个方法全部落地，方法签名与命令文件命名与 §5 一致。
- 聚合根与子实体字段、类型、约束与 §4 一致，且软删策略完整实现。
- 所有事件按 §7 在 `beforeCommit` 发布，载荷字段不少于最小载荷。
- BR 关键规则至少一条对应一条单测，`unitTestCaseId` 全量可追溯。
- 读侧 8 个方法实现数据库侧分页过滤，QueryModel 与 §4.6 对齐。

### 11.3 失败处理

- 任一方法契约无法落地（字段缺失、状态机冲突、事件缺失）立即停止成码并回滚到文档会话。
- 若发现 DM 新增/变更白名单方法，先更新 SDD 再进入代码生成。
- 若测试未覆盖关键 BR 或事件未发布，判定 DoD 失败，不得合并。

---

## 定稿检查清单（可打勾）

- 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- §5.1 子节数量 = DM 写方法数量（11）
- §4.2/§4.3 字段表覆盖 DM §3 主表与子表
- §7 事件表覆盖 DM §5 全部事件（11）
- 全文无 YAML、`sdd_status`、`dm_code`、占位措辞

---

## 定稿评分（百分制）


| 维度      | 分值      | 自评               |
| ------- | ------- | ---------------- |
| 输入完整性   | 20      | 20               |
| 方法白名单对齐 | 25      | 25               |
| 不变式映射   | 20      | 20               |
| 事件契约    | 15      | 15               |
| 软删与框架一致 | 10      | 10               |
| 去重与无歧义  | 10      | 10               |
| **总分**  | **100** | **100（可进入代码会话）** |


---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-07-19 | **学籍迁出 Campus**：移除 PatientStudent 全链路；对齐 DM **v1.3** |
| 2026-05-28 | **DM-SDD M1**：§5.2 `listPatientIdsByEncounterListKeyword`；对齐 DM **v1.2.1** |
| 2026-05-28 | **ReadModel 废止**（DM **v1.2**）：**`PatientQueryService#mapInpatientProjectionSnapshotsByIds`** 供 **`InpatientEncounterContextLoader`** / **`InpatientInfoQueueDisplayAssembler`** 回填（**BR-PAT-023**，**非** InpatientInfo RM） |

---

**本 SDD 正文结束。**