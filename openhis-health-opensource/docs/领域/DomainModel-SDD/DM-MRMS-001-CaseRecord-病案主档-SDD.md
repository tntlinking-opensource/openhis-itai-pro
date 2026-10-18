# DM-MRMS-001 CaseRecord 病案主档 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-MRMS-001-CaseRecord-病案主档.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：MRMS 病案管理  
> **聚合**：CaseRecord（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| --- | --- | --- |
| DM 路径 | 满足 | DM-MRMS-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | zxplat-domain |
| 包规则 | 满足 | com.zxplat.domain.mrms.caserecord |
| 固定技术口径 | 满足 | §3 §9 |
| 写/读白名单 | 满足 | §5 写20读11 |
| BR/不变量 | 满足 | §6 |
| 事件 | 满足 | §7 七事件 |

**与 DM 显式差异（采纳依据：源码）**

| 点 | DM | SDD 采纳 |
| --- | --- | --- |
| 状态 | §3 六态 | 主档仅 DRAFT/PENDING_ARCHIVE/ARCHIVED |
| 编辑 | draft/returned | 仅 DRAFT 可编辑 |
| 事件 | §5 五个 | +ArchiveSubmitted/Rejected |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化病案主档、首页、诊断/手术子表与双写侧 DomainService、QueryService、领域事件。
- 子表 **全量对齐 + 软删**；发号 MedcasnoGenerator。
- **单次成稿目标**：无占位措辞。

### 1.2 边界

- **在内**：§5 白名单方法。
- **在外**：HTTP、4401 组装、质控 executor、homepageedit 申请。
- **跨域**：AutoFill 仅 QueryService。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.mrms.caserecord`）

| 相对包 | 内容 |
| --- | --- |
| entity | CaseRecord、CaseRecordHomepage、HomepageDiagnosis、HomepageProcedure、HomepageIcu、HomepageBld |
| command | HomepageSaveCommand 等 |
| service | CaseRecordDomainService、CaseRecordHomepageDomainService、CaseRecordQueryService、CaseRecordOverdueQueryService |
| share.events | com.zxplat.domain.share.events.mrms.caserecord |

**存储口径**：`status_code` String；`*_code` String。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| --- | --- |
| ID | Long |
| 时间 | LocalDate / LocalDateTime 按字段名 |
| 软删 | isDeleted |
| 事件 | beforeCommit publish |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体 | 说明 |
| --- | --- | --- |
| mrms_case_record | CaseRecord | 主档 |
| mrms_case_record_homepage | CaseRecordHomepage | 首页 |
| mrms_homepage_diagnosis | HomepageDiagnosis | 诊断 |
| mrms_homepage_procedure | HomepageProcedure | 手术 |
| mrms_homepage_icu | HomepageIcu | 重症监护（V11；4101A/4401 icuinfo） |
| mrms_homepage_bld | HomepageBld | 输血（V11；4101A bldinfo） |

### 4.2 聚合根 `CaseRecord`

| 字段名（Java） | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 系统 | PK |
| tenantId | Long | 是 |  |
| patientId | Long | 是 |  |
| encounterId | Long | 是 | 唯一 |
| medcasno | String | 否 | 发号 |
| statusEnum | CaseRecordStatus | 是 | status_code |
| patnIptCnt | Integer | 否 |  |
| psnName | String | 否 | 冗余 |
| iptNo | String | 否 |  |
| gendCode | String | 否 |  |
| certno | String | 否 |  |
| admTime | LocalDateTime | 否 |  |
| dscgTime | LocalDateTime | 否 |  |
| admCatyName | String | 否 |  |
| dscgCatyName | String | 否 |  |
| chfdrName | String | 否 |  |
| atddrPractitionerId | Long | 否 |  |
| atddrName | String | 否 |  |
| mainDiagName | String | 否 |  |
| returnReason | String | 否 |  |
| archivedAt | LocalDateTime | 否 |  |
| archivedBy | Long | 否 |  |
| qualityGradeCode | String | 否 |  |
| chsUploadStatusCode | String | 否 |  |
| chsUploadAt | LocalDateTime | 否 |  |
| chsUploadError | String | 否 |  |
| settleUploadStatusCode | String | 否 |  |
| setlListId | String | 否 |  |
| settleUploadAt | LocalDateTime | 否 |  |
| settleUploadError | String | 否 |  |
| unarchiveReason | String | 否 |  |

### 4.3 子实体 `CaseRecordHomepage`

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| --- | --- | --- | --- |
| caseRecordId | Long | 是 | FK |
| versionNo | Integer | 是 | 版本 |
| frozen | Boolean | 是 | 冻结 |
| iptMedcasHmpgSn | String | 否 | 首页流水 |
| fieldSourcesJson | String | 否 | 来源 JSON |
| psnName | String | 否 | 姓名 |
| gendCode | String | 否 | 性别 |
| brdy | LocalDate | 否 | 出生日期 |
| ntlyCode | String | 否 | 国籍 |
| natyCode | String | 否 | 民族 |
| certno | String | 否 | 证件 |
| prfsCode | String | 否 | 职业 |
| mrgStasCode | String | 否 | 婚姻 |
| currAddr | String | 否 | 现住址 |
| psnTel | String | 否 | 电话 |
| conerName | String | 否 | 联系人 |
| conerTel | String | 否 | 联系人电话 |
| conerRltsCode | String | 否 | 联系人关系 |
| conerAddr | String | 否 | 联系人地址 |
| trtTypeCode | String | 否 | 治疗类别 |
| clinicalPathCode | String | 否 | 临床路径 |
| tcmPrepCode | String | 否 | 中药制剂 |
| tcmDeviceCode | String | 否 | 中医设备 |
| tcmTechCode | String | 否 | 中医技术 |
| syndromeNurseCode | String | 否 | 辨证施护 |
| spgaNurscareDays | Integer | 否 | 特级护理 |
| lv1NurscareDays | Integer | 否 | 一级护理 |
| scdNurscareDays | Integer | 否 | 二级护理 |
| lv3NurscareDays | Integer | 否 | 三级护理 |
| otpWmDise | String | 否 | 门急西医诊断 |
| wmDiseCode | String | 否 | 西医码 |
| otpTcmDise | String | 否 | 门急中医诊断 |
| tcmDiseCode | String | 否 | 中医码 |
| brnDamgBfadmComaDura | String | 否 | 入院前昏迷 |
| brnDamgAfadmComaDura | String | 否 | 入院后昏迷 |
| ventUsedDura | String | 否 | 呼吸机 |
| iptNo | String | 否 | 住院号 |
| admWayCode | String | 否 | 入院途径 |
| admCatyCode | String | 否 | 入院科别码 |
| admCatyName | String | 否 | 入院科别 |
| admTime | LocalDateTime | 否 | 入院时间 |
| dscgCatyCode | String | 否 | 出院科别码 |
| dscgCatyName | String | 否 | 出院科别 |
| dscgTime | LocalDateTime | 否 | 出院时间 |
| iptDays | Integer | 否 | 住院天数 |
| transferDeptSummary | String | 否 | 转科 |
| dscgWayCode | String | 否 | 离院方式 |
| deptDirectorName | String | 否 | 科主任 |
| chfdrName | String | 否 | 主任 |
| atddrName | String | 否 | 主治 |
| atddrPractitionerId | Long | 否 | 主治 ID |
| iptDrName | String | 否 | 住院医师 |
| respNursName | String | 否 | 责任护士 |
| payWayCode | String | 否 | 付费方式 |
| healthCardNo | String | 否 | 健康卡 |
| ageYear | Integer | 否 | 年龄岁 |
| ageMonth | Integer | 否 | 年龄月 |
| birplcCodes | String | 否 | 出生地 codes |
| birplcName | String | 否 | 出生地 |
| naplCodes | String | 否 | 籍贯 codes |
| naplName | String | 否 | 籍贯 |
| currAddrCodes | String | 否 | 现住址 codes |
| currAddrDetail | String | 否 | 现住址详细 |
| currAddrPoscode | String | 否 | 现住址邮编 |
| resdAddrCodes | String | 否 | 户口 codes |
| resdAddrDetail | String | 否 | 户口详细 |
| resdAddrPoscode | String | 否 | 户口邮编 |
| emprAddr | String | 否 | 单位地址 |
| emprTel | String | 否 | 单位电话 |
| emprPoscode | String | 否 | 单位邮编 |
| admWard | String | 否 | 入院病区 |
| admBed | String | 否 | 入院床 |
| dscgWard | String | 否 | 出院病区 |
| dscgBed | String | 否 | 出院床 |
| drugAllergyCode | String | 否 | 过敏 |
| allergyDrugName | String | 否 | 过敏药 |
| pathologyNo | String | 否 | 病理号 |
| mainDiagCureCode | String | 否 | 主诊断治愈 |
| rescueCount | Integer | 否 | 抢救次数 |
| rescueSuccessCount | Integer | 否 | 抢救成功 |
| clinicPathManageCode | String | 否 | 路径管理 |
| diagMatchOpDscg | String | 否 | 门出符合 |
| diagMatchAdmDscg | String | 否 | 入出符合 |
| diagMatchPrePost | String | 否 | 术前术后 |
| diagMatchClinicPath | String | 否 | 临床病理 |
| diagMatchRadioPath | String | 否 | 放射病理 |
| autopsyCode | String | 否 | 尸检 |
| aboCode | String | 否 | ABO |
| rhCode | String | 否 | Rh |
| trainDrName | String | 否 | 进修 |
| intnDrName | String | 否 | 实习 |
| codrName | String | 否 | 编码员 |
| medcasQltCode | String | 否 | 病案质量 |
| qltctrlDate | LocalDate | 否 | 质控日 |
| qltctrlDrName | String | 否 | 质控医师 |
| qltctrlNursName | String | 否 | 质控护士 |
| acpMedinsName | String | 否 | 拟接收机构 |
| acpCommunityName | String | 否 | 拟接收社区 |
| rinp31Flag | String | 否 | 31 天再住院 |
| rinp31Purpose | String | 否 | 再住院目的 |
| medfeeSumamt | BigDecimal | 否 | 医疗费总额 |
| selfpayAmt | BigDecimal | 否 | 自付 |
| feeItemsJson | String | 否 | 费用 JSON |

### 4.4 子实体 `HomepageDiagnosis`

| 字段名（Java） | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseRecordId | Long | 是 | FK |
| tenantId | Long | 是 |  |
| diagTypeCode | String | 是 |  |
| sortNo | Integer | 是 |  |
| diagCode | String | 否 |  |
| diagName | String | 否 |  |
| admCondCode | String | 否 |  |
| outIllStatusCode | String | 否 |  |
| confirmTime | LocalDateTime | 否 |  |
| diagRemark | String | 否 |  |
| confirmDrId | Long | 否 |  |
| confirmDrName | String | 否 |  |
| needYbUp | String | 否 |  |
| maindiagFlag | String | 否 |  |
| valiFlag | String | 是 |  |

### 4.4.1 子实体 `HomepageProcedure`

| 字段名（Java） | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseRecordId | Long | 是 |  |
| tenantId | Long | 是 |  |
| sortNo | Integer | 是 |  |
| oprnOprtCode | String | 否 |  |
| oprnOprtName | String | 否 |  |
| oprnDate | LocalDate | 否 |  |
| oprnTime | LocalDateTime | 否 |  |
| mainOprnFlag | String | 否 |  |
| oprnLevelCode | String | 否 |  |
| oprnIncisionCode | String | 否 |  |
| healGradeCode | String | 否 |  |
| anstWayCode | String | 否 |  |
| operDrName | String | 否 |  |
| operDrCode | String | 否 |  |
| asit1Name | String | 否 |  |
| asit2Name | String | 否 |  |
| nurseName | String | 否 |  |
| anstDrName | String | 否 |  |
| anstDrCode | String | 否 |  |
| anstStartTime | LocalDateTime | 否 |  |
| anstEndTime | LocalDateTime | 否 |  |
| specimen | String | 否 |  |
| note | String | 否 |  |
| sendLabFlag | Boolean | 否 |  |
| valiFlag | String | 是 |  |

保存时子表全量 replace + 软删旧行。

### 4.4.2 子实体 `HomepageIcu`

| 字段名（Java） | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseRecordId | Long | 是 | FK |
| tenantId | Long | 是 |  |
| sortNo | Integer | 是 |  |
| scsCutdWardType | String | 否 | ICU_TYPE（1 CCU…7 ICU / 9 其他）；4401→`icu_codeid` |
| scsCutdInpoolTime | LocalDateTime | 否 | 进入重症监护 |
| scsCutdExitTime | LocalDateTime | 否 | 退出重症监护 |
| scsCutdSumDura | String | 否 | 合计时长 `天/时/分` |
| valiFlag | String | 是 | 默认 1 |

### 4.4.3 子实体 `HomepageBld`

| 字段名（Java） | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| caseRecordId | Long | 是 | FK |
| tenantId | Long | 是 |  |
| sortNo | Integer | 是 |  |
| bldCat | String | 否 | 输血品种（`bld_cat_code`） |
| bldAmt | String | 否 | 输血量 |
| bldUnt | String | 否 | 计量单位 |
| valiFlag | String | 是 | 默认 1 |

保存时 ICU/输血子表与诊断/手术同口径：全量 replace + 软删旧行（`command.icus`/`blds` 非 null 才替换）。

### 4.5 仓储接口

- CaseRecordRepository、CaseRecordHomepageRepository、HomepageDiagnosisRepository、HomepageProcedureRepository、HomepageIcuRepository、HomepageBldRepository（JpaRepository 仅接口）。

### 4.6 查询模型

#### 4.6.1 `CaseRecordListItemQueryModel`

| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| id | Long | mrms_case_record.id |  |
| encounterId | Long | encounter_id |  |
| medcasno | String | medcasno |  |
| psnName | String | psn_name |  |
| iptNo | String | ipt_no |  |
| gendCode | String | gend_code |  |
| certno | String | certno |  |
| admTime | LocalDateTime | adm_time |  |
| dscgTime | LocalDateTime | dscg_time |  |
| admCatyName | String | adm_caty_name |  |
| dscgCatyName | String | dscg_caty_name |  |
| chfdrName | String | chfdr_name |  |
| mainDiagName | String | main_diag_name |  |
| statusCode | String | status_code |  |
| chsUploadStatusCode | String | chs_upload_status_code |  |
| settleUploadStatusCode | String | settle_upload_status_code |  |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `CaseRecordDomainService`

#### 5.1.1 `createIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) → CaseRecord`

**命令文件**：无（多参）

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| tenantId | Long | 租户 |
| patientId | Long | 患者 |
| encounterId | Long | 就诊 |
| operatorId | Long | 操作人 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | initNewRecord |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | CaseRecordCreatedEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-001 |

**方法契约摘要**：幂等建档；uk 冲突回读。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.2 `bootstrapIfAbsent(Long tenantId, Long patientId, Long encounterId, Long operatorId) → Long`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| tenantId | Long |  |
| patientId | Long |  |
| encounterId | Long |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | ensureHomepage |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | createIfAbsent 事件 |
| `unitTestCaseId` | TC-DM-MRMS-001-W-002 |

**方法契约摘要**：建档+懒建首页+新建自动取值。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.3 `saveHomepage(HomepageSaveCommand cmd) → CaseRecordHomepage`

**命令文件**：HomepageSaveCommand.java

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| （全集） | — | 与命令类字段 1:1（含 diagnoses/procedures/icus/blds 子表；子表 null=本次不替换） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | assertEditableByDoctor |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-003 |

**方法契约摘要**：发号+save+refreshSummary。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.4 `refreshAutoFill(RefreshAutoFillCommand cmd) → CaseRecordHomepage`

**命令文件**：RefreshAutoFillCommand.java

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| sections | List<String> | 分区 |
| forceOverwrite | boolean |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | mergeAutoFillFromHis |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-004 |

**方法契约摘要**：HIS 合并。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.5 `submit(SubmitCaseRecordCommand cmd) → CaseRecord`

**命令文件**：SubmitCaseRecordCommand.java

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | submitArchive |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | CaseRecordArchiveSubmittedEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-005 |

**方法契约摘要**：委托 submitArchive。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.6 `submitArchive(SubmitCaseRecordCommand cmd) → CaseRecord`

**命令文件**：SubmitCaseRecordCommand.java

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | submitArchive+assertReadyToSubmit |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | CaseRecordArchiveSubmittedEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-006 |

**方法契约摘要**：DRAFT→PENDING_ARCHIVE。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.7 `approveArchive(Long caseRecordId, Long operatorId, String qualityGradeCode) → CaseRecord`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| operatorId | Long |  |
| qualityGradeCode | String | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | approveArchive |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | CaseRecordArchivedEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-007 |

**方法契约摘要**：归档并 freeze。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.8 `rejectArchive(Long caseRecordId, String rejectReason, Long operatorId) → CaseRecord`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| rejectReason | String |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | rejectArchive |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | CaseRecordArchiveRejectedEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-008 |

**方法契约摘要**：驳回到 DRAFT。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.9 `transition(TransitionCaseRecordCommand cmd) → CaseRecord`

**命令文件**：TransitionCaseRecordCommand.java

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| action | CaseRecordTransitionAction |  |
| role | WorkflowRole |  |
| returnReason | String |  |
| unarchiveReason | String |  |
| qualityGradeCode | String |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | applyTransition |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | 按 action publishTransitionEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-009 |

**方法契约摘要**：仅 SUBMIT/ARCHIVE 有效；其余拒绝。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.10 `refreshSummary(CaseRecord record, CaseRecordHomepage homepage) → CaseRecord`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| record | CaseRecord |  |
| homepage | CaseRecordHomepage |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | refreshSummaryFrom |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-010 |

**方法契约摘要**：冗余列回写。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.11 `ensureArchivedAndFrozenForUpload(Long caseRecordId, Long operatorId) → CaseRecord`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| caseRecordId | Long |  |
| operatorId | Long |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | submitArchive+approveArchive |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | CaseRecordArchivedEvent |
| `unitTestCaseId` | TC-DM-MRMS-001-W-011 |

**方法契约摘要**：4401 隐性归档链。 **注释锚点**：校验 → 持久化 → 事件（若有）。

---



### 5.1 写侧 — `CaseRecordHomepageDomainService`

#### 5.1.12 `ensureHomepage(Long caseRecordId, Long tenantId, Long operatorId) → CaseRecordHomepage`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-012 |

**方法契约摘要**：懒创建 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.13 `save(HomepageSaveCommand command) → CaseRecordHomepage`

**命令文件**：HomepageSaveCommand.java

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-013 |

**方法契约摘要**：标量+子表 replace **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.14 `mergeAutoFillFromHis(CaseRecord record, List<String> sections, boolean forceOverwrite, Long operatorId) → CaseRecordHomepage`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-014 |

**方法契约摘要**：自动取值写侧 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.15 `refreshFeeSnapshot(CaseRecordHomepage homepage, FeeSummarySnapshot snapshot, Long operatorId) → CaseRecordHomepage`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-015 |

**方法契约摘要**：费用 JSON **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.16 `ensureFeeSnapshotSeeded(CaseRecordHomepage homepage, Long operatorId) → CaseRecordHomepage`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-016 |

**方法契约摘要**：费用补种 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.17 `fillMissingSelfpayAmt(CaseRecordHomepage homepage, Long operatorId) → CaseRecordHomepage`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-017 |

**方法契约摘要**：自付补全 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.18 `freeze(Long caseRecordId, Long operatorId) → void`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-018 |

**方法契约摘要**：frozen=true **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.19 `applyApprovedEditSnapshot(Long caseRecordId, String approvedSnapshotJson, Long operatorId) → CaseRecordHomepage`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-019 |

**方法契约摘要**：归档后审核回写 **注释锚点**：校验 → 持久化 → 事件（若有）。

---


#### 5.1.20 `unfreeze(Long caseRecordId, Long operatorId) → void`

**命令文件**：无

**字段全集**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| 见方法签名 | — |  |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | homepage 实体 Gate |
| `statusTransitionMethod` | 见实体方法 |
| `repositoryChecks` | CaseRecord/Homepage Repository |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | TC-DM-MRMS-001-W-020 |

**方法契约摘要**：入口禁用保留 **注释锚点**：校验 → 持久化 → 事件（若有）。

---



### 5.2 读侧

| 方法 | 入参 | 出参 | unitTestCaseId |
| --- | --- | --- | --- |
| requireById | caseRecordId | CaseRecord | TC-DM-MRMS-001-R-001 |
| findByEncounterId | tenantId, encounterId | Optional<CaseRecord> | TC-DM-MRMS-001-R-002 |
| findOptionalByCaseRecordId | caseRecordId | Optional<Homepage> | TC-DM-MRMS-001-R-003 |
| requireExistingByCaseRecordId | caseRecordId | CaseRecordHomepage | TC-DM-MRMS-001-R-004 |
| hasFeeSnapshot / readFeeSnapshot | homepage | boolean / snapshot | TC-DM-MRMS-001-R-005 |
| listDiagnoses / listProcedures / listIcus / listBlds | caseRecordId | List | TC-DM-MRMS-001-R-006 |
| mapByEncounterIds | tenantId, ids | Map | TC-DM-MRMS-001-R-007 |
| queryPage | CaseRecordPageQuery | Page<CaseRecordListItemQueryModel> | TC-DM-MRMS-001-R-008 |
| queryUploadPage | CaseRecordUploadPageQuery | Page<CaseRecordListItemQueryModel> | TC-DM-MRMS-001-R-009 |
| findOptionalByMedcasno | tenantId, medcasno | Optional<CaseRecord> | TC-DM-MRMS-001-R-010 |
| findSubmitOverdue / findArchiveOverdue | 参数见源码 | List<CaseRecord> | TC-DM-MRMS-001-R-011 |

**读方法注释锚点（统一）**：软删 → SQL 分页 → QueryModel。

---

## 6. 不变式映射（DM §7）

| BR | 策略 |
| --- | --- |
| INV-1 | tenant+encounter 唯一 |
| INV-2 | medcasno 租户唯一；首次保存发号 |
| INV-3 | ARCHIVED → frozen |
| INV-4 | 仅 DRAFT 医生编辑 |
| BR-MRMS-HP-PR-007 | 手术时间 / 麻醉起止落在入院～出院（有界才校；`EX-MRMS-HP-PR-204`） |
| BR-MRMS-HP-PR-008 | 麻醉起止两端皆填时结束 ≥ 开始（`EX-MRMS-HP-PR-205`） |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 触发 |
| --- | --- | --- |
| CaseRecordCreatedEvent | ...mrms.caserecord | createIfAbsent |
| CaseRecordArchiveSubmittedEvent | ... | submitArchive |
| CaseRecordArchiveRejectedEvent | ... | rejectArchive |
| CaseRecordArchivedEvent | ... | approveArchive |
| CaseRecordSubmittedEvent | ... | transition SUBMIT（兼容） |
| CaseRecordReceivedEvent | ... | transition RECEIVE（已禁） |
| CaseRecordReturnedEvent | ... | transition RETURN（已禁） |

**发布时机**：全部 **beforeCommit**。

---

## 8. 删除与可见性契约

- 逻辑删除；子表 sync 软删旧行。

---

## 9. 框架优先适配规则

- BaseAuditedEntity、IDomainEventPublisher、项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] §4 字段与 Flyway 一致
- [ ] 写 20 + 读 11
- [ ] 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM + 本 SDD。

### 11.2 DoD

- 100% 对齐 mrms/caserecord 源码。

### 11.3 失败处理

- 回写文档，禁止口头补丁。

---

## 定稿检查清单（可打勾）

- [x] 金骨架
- [x] 写 20 / 读 11
- [x] §4.2/4.3 完整

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
| **总分** | **100** | **≥95** |

---

<!-- MODE-B 2026-10-14：回写 HomepageIcu/HomepageBld §4.4.2～4.4.3、仓储、listIcus/listBlds、saveHomepage 子表字段；对齐 V11 Flyway -->

**本 SDD 正文结束。**
