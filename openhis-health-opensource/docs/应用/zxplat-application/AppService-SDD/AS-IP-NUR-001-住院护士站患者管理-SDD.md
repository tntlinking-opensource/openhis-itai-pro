# 住院护士站 · 患者管理 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-001-SDD  
> 版本：v2.31 | 日期：2026-08-19 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界


| 项      | 内容                                                                                                                                                                                              |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 模块     | 住院护士站工作台 · 患者上下文（病区/病房树、在院列表、详情、信息维护）                                                                                                                                                           |
| 实现阶段口径 | **目标态全量契约**（IF-IP-NUR-01～04）；应用层 **`NurseStationEncounterManageAppService`**（~~状态筛选/召回~~ 已废止）；HTTP **`nurseStationEncounterManage`**；前端护士站列表默认在院队列 + **入科状态下拉（在科/待入科，本地过滤，BR-IP-NUR-017）**；召回 **FIN-004 IF-IP-RCL-01** |
| 编排权威   | `AS-IP-NUR-001-住院护士站患者管理.md`（**v1.36**）§2、§4                                                                                                                                                     |
| 方法权威   | `DM-ADM-002`、`DM-ADM-003`、`DM-ADM-001`、`DM-ADM-009`、`DM-MED-003`、`DM-WKF-004`、`DM-CLN-004` §6；`DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §3                                                                     |
| 严格对齐   | IF 编号 **IF-IP-NUR-01～04**，不得增删改号                                                                                                                                                                |


**Out of scope（重复 AS §1.4）**


| 类别               | 内容                                             |
| ---------------- | ---------------------------------------------- |
| 床位级树/列表、分床/换床写操作 | **AS-IP-NUR-002**                              |
| 医嘱 Tab 明细校对/执行   | **AS-IP-NUR-003** 起                            |
| 入院体征/文书维护        | **v1.17 纳入** IF-IP-NUR-03/04；**002 ATD-02 ASSIGN** 首次分床写；体温单/护理记录另期 AS |
| QueryService 查询列扩展           | **GAP-IP-NUR-001** 已决议：不扩展 `InpatientEncounterContextQueryModel` 投影列 |


**上游依赖**


| 文档                         | 关系                                                |
| -------------------------- | ------------------------------------------------- |
| `AS-IP-FIN-002`            | 展示余额公式与 `InpatientDisplayAccountBalanceAssembler` |
| `AS-IP-FIN-004`            | **召回** **IF-IP-RCL-01** / **反结** **IF-IP-RVS-01**：`InpatientSettleAppService`（收银台已结算历史）；~~**002 ATD-07**~~ 已废止 |
| `AS-OP-NUR-001`            | 待执行「净执行」口径 **BR-NUR-005**                         |
| `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` | **Q-IPI-005** `pageInpatientNurseStationQueue`             |


### 1.2 术语口径


| 项          | 约定                                                                                                                                                                                                       |
| ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 状态/筛选 code | 持久化与比较一律 **String `code`**（`EncounterStatus.getCode()`、`RequestStatus.getCode()` 等）                                                                                                                      |
| 任务筛选       | `InpatientNurseStationConstants` 任务筛选 code；App 白名单校验；读侧 **不消费**                                                                                                                                          |
| 分页         | `AbstractPageQuery`；`pageNo` **1-based**；`PageWindowGate` + `PagedResultBuilder`                                                                                                                         |
| 待执行自然日     | `nurseStationExecutionDate` 可空 → `LocalDate.now()`                                                                                                                                                       |
| 展示余额       | **`InpatientAccountBalanceDisplayAssembler`**：`computeDisplayAccountBalance(account, pending)` = **库内 `balance` − 待结算**；卡片/队列**单一余额文案**（不含「可用/待结算」拆分）；`lowBalanceWarning`：**< 500** |
| 分层纪律       | **禁止** 在应用层新建 `com.zxplat.domain.inpatientnursestation` 等跨聚合包；计数与 BR-IP-NUR-007 在 **App** 完成                                                                                                                            |
| 应用分包       | 本 AS → `nursestation/encountermanage`；入出转 → `nursestation/ATDManager`（**AS-IP-NUR-002-SDD**）。**不是** `patientmanage` / `Patientmanage` |
| HTTP 接入口径  | `whale-web` 的 `AppServiceControllerRegistrar`：**一律 `POST`** `POST /api/app/nurseStationEncounterManage/{methodName}`；`serviceName` 由 **`NurseStationEncounterManageAppService`** 类名推导（废弃 `inpatientNurseStation`、`inpatientNurseStationEncounterManage` 等长前缀注册名） |


## 2. 代码结构

**目标态**：

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  encountermanage/                                  # AS-IP-NUR-001（患者管理）
    service/
      NurseStationEncounterManageAppService.java
    request/
      LoadWardTreeRequest.java
      LoadAdmissionParticipantInitRequest.java
      PagePatientQueueRequest.java
      GetPatientDetailRequest.java
      SavePatientInfoRequest.java
    response/
      LoadWardTreeResponse.java
      LoadAdmissionParticipantInitResponse.java
      PagePatientQueueResponse.java
      GetPatientDetailResponse.java
      SavePatientInfoResponse.java
    dto/
      NurseStationWardTreeNodeDto.java
      NurseStationHouseTreeNodeDto.java
      PatientQueueItemDto.java
      PatientDetailDto.java
      ParticipantDto.java
      ParticipantSubmitDto.java                     # 002 分床载荷复用
      AdmissionVitalSignSubmitDto.java              # 见 module.common.dto.nursestation
      AdmissionVitalSignSaveContext.java            # 见 application.shared.nursestation-device-dispense
      NurseStationTaskCountBundle.java
      NurseStationQueueDisplayEnrichment.java
    mapper/
      NurseStationEncounterManageMapper.java
      NurseStationEncounterManageMapperImpl.java
  ATDManager/                                       # AS-IP-NUR-002；见 AS-IP-NUR-002-SDD §2

zxplat/health-application/src/main/java/com/openhis/health/application/
  orchestration/nursestation/
    AdmissionVitalSignSupport.java                  # 入院体征 merge + 写编排（001/002 共用）
  common/dto/nursestation/
    AdmissionVitalSignSubmitDto.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  VitalSignAdmissionItemCodeConstants.java          # 入科抽屉 7 项 vital_signs_code 白名单
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.encountermanage`（本 AS）  
> AppService Bean 名 → HTTP 前缀：`nurseStationEncounterManage`（与成码 `NurseStationEncounterManageAppService` 一致；住院语义由包 `...inpatient.nursestation.encountermanage` 表达）

### 2.1 依赖注入表


| 依赖                                        | 用途                                                                            |
| ----------------------------------------- | ----------------------------------------------------------------------------- |
| `CurrentManagedWardLocationIdsResolver` | 管理病区硬权限（IF-IP-NUR-01/02 Gate；**B.6.5**） |
| `CurrentOperatorResolver`                 | 写路径操作人（IF-IP-NUR-04，`B.6.5`）                                                  |
| `CurrentPractitionerResolver`             | 入院体征 `recorder_id`（IF-IP-NUR-04，`B.6.5`）                                        |
| `PageWindowGate`                          | 分页入站（IF-IP-NUR-02，`B.10.11`）                                                  |
| `LocationQueryService`                    | 管理病区、病房树、床位地理（IF-IP-NUR-01/02）                                                |
| `EncounterContextReader`            | IF-IP-NUR-02：`pageInpatientNurseStationQueue`；IF-IP-NUR-03：`mustGetInpatientContextByEncounterId` |
| `EncounterQueryService`             | `mustGetEncounterOfClass`、参与者列表（IF-IP-NUR-03/04） |
| `PatientQueryService`                     | 患者扩展档（电话、证件，IF-IP-NUR-03）                                                     |
| `AccountQueryService`                     | 批量个人现金户（IF-IP-NUR-02/03）                                                      |
| `MedicationRequestQueryService`           | 待校对/待执行批量查询（IF-IP-NUR-02/03）                                                  |
| `ServiceRequestQueryService`              | 同上                                                                            |
| `ProcedureQueryService`                   | 当日净执行判定（IF-IP-NUR-02/03）                                                      |
| `EncounterDomainService`                  | 患者信息维护写（IF-IP-NUR-04，见 `**DM-ADM-002` §6 `saveNurseStationPatient**`）         |
| `InpatientInfoQueueDisplayAssembler`      | 队列行展示回填                                                                       |
| `AdmissionPrimaryDiagnosisAssembler`      | Loader enrichment 的 QueryModel 内部 JSON → `diagnosisList`/`diagnosisSummary`（**禁止** API 透传 JSON） |
| `EncounterParticipantRole`（`EnumInterface`） | 参与者 `role_code` 身份与 `getDisplay()`（DM-ADM-002 §3.2.1）                          |
| `EncounterParticipantRoleDisplayUtil`     | `roleName` 展示：`EnumUtil#fromCode` + 未知 code 回退（IF-IP-NUR-03）                    |
| `InpatientDisplayAccountBalanceAssembler` | 展示余额数值计算（**待结算合计**口径） |
| `InpatientAccountBalanceDisplayDtoApplier` | 队列/详情 DTO 文案覆盖（`application.shared.inpatient`） |
| `ChargeItemQueryService` | `mapPendingSettlementTotalPriceByEncounterIds` / `sumPendingSettlementTotalPriceByEncounterId` |
| `NurseStationEncounterManageMapper` | Request/Query/Command/DTO 装配                                                  |
| `PractitionerRoleScopeLoader` | 分床下拉：按科室/病区岗位分配收窄医师/护士（辅助读，**002** 入科分床） |
| `AdmissionVitalSignsupport（`application.shared.nursestation-device-dispense`） | IF-IP-NUR-03 回填 `toSubmitDto`；IF-IP-NUR-04 维护写 `saveIfPresent` |
| `InpatientPatientQueueNursingLevelEnrichAssembler` | IF-IP-NUR-02 护理级别批量 enrich（**BR-IP-NUR-015**） |
| `VitalSignQueryService` | `listHeadersByEncounterId` + `getWithDetailsById`（经 Support，锚定 `start_time`） |
| `VitalSignDomainService` | `saveVitalSign`（经 Support，IF-IP-NUR-04） |


### 2.2 领域方法白名单


| 类型                              | 方法                                                                             | 用于 IF                                               |
| ------------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedActiveWardsOrEmpty`（队列 `managedWardIds` 与 IF-01 树同一提取口径；**医院租户硬权限**） | IF-IP-NUR-01 / 02 Gate                            |
| `PractitionerRoleScopeLoader` | `listActivePractitionersForPlatformRole` / `listActivePractitionersForPlatformRoleInOrganization` / `listActivePractitionersForPlatformRoleAtWardLocation` | 辅助读 `loadAdmissionParticipantInit` |
| `PractitionerRoleQueryService` | `listPractitionerIdsWithActiveRoleIdAtLocation` | 责任护士按病区 `location_id` 收窄 |
| `LocationQueryService`          | `listHousesUnderWard`                                                          | IF-IP-NUR-01                                        |
| `LocationQueryService`          | `listActiveHousesByWardBusNo`                                                  | IF-IP-NUR-01（可选，与 `listHousesUnderWard` 二选一由 UI 约定） |
| `LocationQueryService`          | `listChildrenByParentBusNo`                                                    | IF-IP-NUR-01（`bus_no` 子树）                           |
| `EncounterContextReader` | `pageInpatientNurseStationQueue` | IF-IP-NUR-02（**Q-IPI-005**） |
| `EncounterContextReader` | `mustGetInpatientContextByEncounterId` | IF-IP-NUR-03 |
| `EncounterQueryService`         | `mustGetEncounterOfClass`                                                      | IF-IP-NUR-03/04                                     |
| `EncounterQueryService`         | `listDisplayEncounterParticipants`                                              | IF-IP-NUR-03（展示：活动优先 / 末次 COMPLETED） |
| `AccountQueryService`           | `mapRegistrationAccountsByEncounterIds`                                        | IF-IP-NUR-02（**禁止**当前页 N+1）                         |
| `MedicationRequestQueryService` | `listByEncounterIdsAndStatusEnum`                                              | IF-IP-NUR-02/03                                     |
| `ServiceRequestQueryService`    | `listByEncounterIdsAndStatusEnum`                                              | IF-IP-NUR-02/03                                     |
| `ServiceRequestQueryService`    | `mapNursingLevelForPatientQueueByEncounterIds`（经 `InpatientPatientQueueNursingLevelEnrichAssembler`） | IF-IP-NUR-02（**BR-IP-NUR-015**） |
| `ProcedureQueryService`         | `listByEncounterIds`                                                           | IF-IP-NUR-02/03                                     |
| `PatientQueryService`           | `loadPatientDetail`                                                            | IF-IP-NUR-03                                        |
| `EncounterDomainService`        | `saveNurseStationPatient`（**已落地**，见 `DM-ADM-002` §6 `saveNurseStationPatient`） | IF-IP-NUR-04                                        |
| `VitalSignQueryService`         | `listHeadersByEncounterId` + `getWithDetailsById`（经 Support） | IF-IP-NUR-03 |
| `VitalSignDomainService`        | `saveVitalSign`（经 Support）                                  | IF-IP-NUR-04 |
| `AdmissionVitalSignsupport     | `toSubmitDto` / `saveIfPresent` / `hasAnySubmittedValue`         | IF-IP-NUR-03/04                                     |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别        | 代码实现锚点                                                                                                        | DM 追溯锚点                                               | 说明                                                         |
| --------- | ------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ---------------------------------------------------------- |
| 就诊类别      | `com.zxplat.domain.share.enums.EncounterClass`                                                        | `DM-ADM-002` §3 `adm_encounter.class_enum`            | 本场景 `**IMP**`                                              |
| 就诊状态      | `com.zxplat.domain.share.enums.EncounterStatus`                                                       | `DM-ADM-002` §2.3.1 护士站可见并集                           | `REGISTERED`/`IN_PROGRESS`/`ON_HOLD`/`PENDING`/`COMPLETED` |
| 请求状态      | `com.zxplat.domain.share.enums.RequestStatus`                                                         | `DM-MED-003`/`DM-WKF-004` §3 `status_enum`            | 待校对 `**ACTIVE**`（仅 `doctor-prescription`）；待执行 `**VERIFIED**`（仅医生开立） |
| 医嘱来源      | `com.zxplat.domain.share.enums.GenerateSource`                                                        | `DM-WKF-004` §4.6                                     | 任务计数**仅计** `doctor-prescription`（**BR-IP-NUR-004/005**） |
| 位置形态      | `com.zxplat.domain.share.enums.LocationForm`                                                          | `DM-ADM-003` §3 `form_enum`                           | 树：`WARD`/`HOUSE`                                           |
| 任务筛选 code | `com.zxplat.domain.share.constants.InpatientNurseStationConstants`                                    | `AS-IP-NUR-001` **BR-IP-NUR-007**                     | `TASK_FILTER_*`；App 白名单                                    |
| ~~列表状态筛选~~ | ~~`PagePatientQueueRequest.nurseStationStatusFilter`~~ | **BR-IP-NUR-012 已废止** | **仅允许空**；~~`SETTLED_DISCHARGED`~~ 常量已删 |
| 入科状态下拉（前端） | `PatientAdmissionFilter`：`in-ward` / `pending-admission` | **BR-IP-NUR-017** | **在科**=排除 `REGISTERED`（未清床已入科轨，含 `COMPLETED`）；**待入科**=`REGISTERED`；默认在科；本地过滤不重拉队列 |
| ~~召回行操作~~ | ~~`PatientQueueItemDto.recallEnabled`~~ | **BR-IP-NUR-013 已废止** | **字段已移除**；写见 **FIN-004 IF-IP-RCL-01** |
| 未占床床展示 | `InpatientEncounterContextQueryModel.bedName` | 召回/清床未占床轨 | 无活动 BED 时末次 COMPLETED BED →「原{房名}-{床名}（未占床）」；无房名退化「原{床名}（未占床）」；不上床位卡 |
| 病情/优先级    | `SavePatientInfoRequest.priorityEnum`                                                                         | `DM-ADM-002` §3 `adm_encounter.priority_enum`         | 存 **code** 字符串                                             |
| 入科体征项 code | `VitalSignAdmissionItemCodeConstants` + `AdmissionVitalSignSubmitDto` | **DM-DIA-002** v2.1 §9.1.2 | `term_code=vital_signs_code`；`item_code`：`HEIGHT`/`WEIGHT`/`TEMP`/`HR`/`PULSE`/`SBP`/`DBP`；读锚点 `start_time` |
| 参与者角色     | `com.zxplat.domain.share.enums.EncounterParticipantRole`                                            | `DM-ADM-002` §3.2.1 `adm_encounter_participant.role_code` | 持久化/入参为 `getCode()`；展示为 `getDisplay()`（经 `EncounterParticipantRoleDisplayUtil`） |
| 请求锚点分隔符   | `com.zxplat.domain.share.constants.GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR` | `DM-CLN-004` 执行记录 `request_table`+`request_id` 锚点     | 待执行净执行统计                                                   |
| 低余额阈值     | `InpatientNurseStationConstants.LOW_BALANCE_WARNING_THRESHOLD`                                                | `AS-IP-NUR-001` **BR-IP-NUR-006**                     | **已落地**（`500` 元，`zxplat-domain`）                    |
| 业务消息码     | `InpatientNurseStationConstants.EX_IP_NUR_*`                                                                  | `AS-IP-NUR-001` §5                                    | **已落地**（`EX_IP_NUR_101/102/201`）                           |


### 2.4 任务计数口径（App 私有编排，非白名单方法）


| 计数                    | 规则                                                                | 数据来源                                                                                                 |
| --------------------- | ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `pendingAuditCount`   | 药品+诊疗 `ACTIVE` 且 `doctor-prescription`，并排除退药/派生（**BR-IP-AUD-002**） | `listByEncounterIdsAndStatusEnum` + App 内存过滤 |
| `pendingExecuteCount` | `VERIFIED` 且医生开立且 **开嘱～统计日截止前仍有未终态计划槽**（与待执行 Tab 同源；含跨日）；长期药品不可拆时按 **BR-IP-EXE-035** 盒首锚点，并叠加 **BR-IP-EXE-038** 按天抑制；排除划价/滚费/转科出院护理；**皮试检查 companion SR 与皮试药均计入**（AS-IP-NUR-011 v1.4） | 请求批量 + `Procedure` 按锚点索引 + `StandingOrderPackageBatchPlanTimeResolver` / `StandingOrderOralPackageInventorySupport`（药）/`FrequencyQueryService`（诊）；App 逐槽终态判定 |
| 当日窗口                  | `occurrence_time`（空则 `recorded_time`）∈ `[day 00:00, day+1 00:00)` | `nurseStationExecutionDate`                                                                          |


### 2.5 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-NUR-001` §3**；本 SDD **禁止**维护 Gap 表。

**领域能力（已落地）**：`EncounterDomainService#saveNurseStationPatient`（IF-IP-NUR-04）；**`EncounterContextReader#pageInpatientNurseStationQueue`**（**Q-IPI-005**）；`InpatientNurseStationConstants`（`zxplat-domain`）；药品/诊疗/执行批量 `listByEncounterIds*`。

**本 SDD 成码交付**：`NurseStationEncounterManageAppService` IF-01～04 及配套 Request/Response/DTO/Mapper（目标态契约，非缺口）。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）


| IF           | 应用服务方法（英文）         | 中文方法名      | HTTP                                                   |
| ------------ | ------------------ | ---------- | ------------------------------------------------------ |
| IF-IP-NUR-01 | `loadWardTree`     | 病区病房树查询    | `POST /api/app/nurseStationEncounterManage/loadWardTree`     |
| IF-IP-NUR-02 | `pagePatientQueue` | 在院患者列表分页查询 | `POST /api/app/nurseStationEncounterManage/pagePatientQueue` |
| IF-IP-NUR-03 | `getPatientDetail` | 患者详情查询     | `POST /api/app/nurseStationEncounterManage/getPatientDetail` |
| IF-IP-NUR-04 | `savePatientInfo`  | 患者信息维护     | `POST /api/app/nurseStationEncounterManage/savePatientInfo`  |

**辅助读方法（不增 IF 编号）**：`loadAdmissionParticipantInit` — 入科/分床责任医护下拉初始化；HTTP `POST /api/app/nurseStationEncounterManage/loadAdmissionParticipantInit`；供 **AS-IP-NUR-002** ATD-02 分床 UI 调用。

### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段       | 含义                      | 失败时                |
| -------- | ----------------------- | ------------------ |
| **Gate** | 权限 / 参数 / 任务筛选 code 白名单 | 不再调用后续步骤           |
| **Pre**  | 只读：QueryService、就诊状态、可见范围        | 抛业务异常，**禁止进入 Cmd** |
| **Cmd**  | 领域写（仅 IF-IP-NUR-04）     | 事务见 §5             |
| **Asm**  | Mapper + Assembler 纯装配  | —                  |


记法：**`EncounterContextReader#pageInpatientNurseStationQueue`** 表示注入 Facade 调用（内部 `InpatientEncounterContextLoader`）。历史路径 `POST /api/app/inpatientNurseStation/{methodName}` **已废弃**；HTTP 前缀 **`nurseStationEncounterManage`**。

### 3.2 IF-IP-NUR-01 `loadWardTree`（病区病房树查询）

**HTTP**：`POST /api/app/nurseStationEncounterManage/loadWardTree`

**Request**


| 字段      | 类型  | 必填  | 说明                                      |
| ------- | --- | --- | --------------------------------------- |
| （无业务字段） | —   | —   | 管理病区由 `CurrentManagedWardLocationIdsResolver` 解析（**B.6.5**） |


**Response**


| 字段          | 类型                                  | 说明      |
| ----------- | ----------------------------------- | ------- |
| `wardNodes` | `List<NurseStationWardTreeNodeDto>` | 病区根节点列表 |


`**NurseStationWardTreeNodeDto**`


| 字段             | 类型                                   | 说明      |
| -------------- | ------------------------------------ | ------- |
| `locationId`   | `Long`                               | 病区位置 id |
| `name`         | `String`                             | 病区名称    |
| `busNo`        | `String`                             | 位置编码    |
| `displayOrder` | `Integer`                            | 排序      |
| `houseNodes`   | `List<NurseStationHouseTreeNodeDto>` | 子病房     |


`**NurseStationHouseTreeNodeDto**`


| 字段             | 类型        | 说明      |
| -------------- | --------- | ------- |
| `locationId`   | `Long`    | 病房位置 id |
| `name`         | `String`  | 病房名称    |
| `busNo`        | `String`  | 位置编码    |
| `displayOrder` | `Integer` | 排序      |


**失败码口径**


| 错误码             | 场景     | 用户提示       |
| --------------- | ------ | ---------- |
| `EX-IP-NUR-101` | 位置查询异常 | 加载失败，请稍后重试 |


**Mapper 方法签名**

- `NurseStationEncounterManageMapper#toLoadWardTreeResponse(List<Location> wards, Map<Long, List<Location>> housesByWardId): LoadWardTreeResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                     | 输入要点                               | 说明                       |
| --- | ---- | ------------------------------------------------------ | ---------------------------------- | ------------------------ |
| 1   | Pre  | `CurrentManagedWardLocationIdsResolver#listManagedActiveWardsOrEmpty` | —                                  | **BR-IP-NUR-001**；医院租户未绑定则 empty，**禁止** App 回落全院 |
| 2   | Pre  | `LocationQueryService#listHousesUnderWard`             | 每个病区 `locationId`                  | 步骤 1 非空时组装子病房；**禁止**含床位节点        |
| 3   | Asm  | `NurseStationEncounterManageMapper#toLoadWardTreeResponse`   | 步骤 1～2 结果                          | 纯装配                      |

> **管理病区解析（与队列共用）**：`PractitionerManagedScopeLoader` 将护士岗位绑定的 **病房（`form_enum=HOUSE`）** 的上级 **病区（WARD）** 一并写入 `managedWardLocationIds`；队列 `managedWardIds` 由 `listManagedActiveWardsOrEmpty` 提取 id，与 IF-01 树同一数据源。**医院租户**：Resolver 内硬权限，未绑定则 empty；**宿主租户**无绑定时 Resolver 内部可回落机构/全院（App 不调 `listAllActiveWards`）。

### 3.2.1 辅助读 `loadAdmissionParticipantInit`（分床责任医护下拉）

**HTTP**：`POST /api/app/nurseStationEncounterManage/loadAdmissionParticipantInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | `Long` | N | 入院科室；有值时按科室岗位分配收窄住院医师 |
| `wardLocationId` | `Long` | N | 入院病区位置 id；有值时按病区岗位分配收窄责任护士 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `doctorOptions` | `List<OptionDto>` | 住院医师候选（平台角色 `ROLE_DOCTOR`） |
| `primaryNurseOptions` | `List<OptionDto>` | 责任护士候选（平台角色 `ROLE_NURSE`） |

**失败码**：`EX-IP-NUR-101`（读失败）

**Mapper**：`toLoadAdmissionParticipantInitResponse(List<Practitioner> doctors, List<Practitioner> nurses)`

**编程序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `organizationId` 非空 → `PractitionerRoleScopeLoader#listActivePractitionersForPlatformRoleInOrganization(ROLE_DOCTOR, orgId)` | 按入院科室激活医师岗 |
| 2 | Pre | `organizationId` 空 → `listActivePractitionersForPlatformRole(ROLE_DOCTOR, null)` | 全平台在职医师（不按门诊科室收窄） |
| 3 | Pre | `wardLocationId` 非空 → `listActivePractitionersForPlatformRoleAtWardLocation(ROLE_NURSE, wardId)` | `adm_pr_assign.location_id` 匹配病区 |
| 4 | Pre | `wardLocationId` 空 | `primaryNurseOptions` 返回空列表 |
| 5 | Asm | Mapper | `OptionDto(id, id字符串, name)` |

### 3.3 IF-IP-NUR-02 `pagePatientQueue`（在院患者列表分页查询）

**HTTP**：`POST /api/app/nurseStationEncounterManage/pagePatientQueue`

**Request**


| 字段                          | 类型          | 必填  | 说明                                             |
| --------------------------- | ----------- | --- | ---------------------------------------------- |
| `wardId`                    | `Long`      | N   | 选病区：该病区内全部患者                                   |
| `houseId`                   | `Long`      | N   | 选病房；**优先于** `wardId`                           |
| `keyword`                   | `String`    | N   | 姓名/住院号/床号                                      |
| `nurseStationTaskFilter`    | `String`    | N   | `InpatientNurseStationConstants.TASK_FILTER_*` |
| `nurseStationStatusFilter`  | `String`    | N   | **已废止**；非空拒绝；空=默认在院 |
| `nurseStationExecutionDate` | `LocalDate` | N   | 待执行计数自然日；空=当天                                  |
| `pageNo`                    | `Integer`   | Y   | 1-based                                        |
| `pageSize`                  | `Integer`   | Y   | 建议 ≤50                                         |


**Response**


| 字段         | 类型                          | 说明  |
| ---------- | --------------------------- | --- |
| `total`    | `Long`                      | 总条数 |
| `pageNo`   | `Integer`                   | 当前页 |
| `pageSize` | `Integer`                   | 页大小 |
| `items`    | `List<PatientQueueItemDto>` | 列表行 |


`**PatientQueueItemDto**`


| 字段                    | 类型              | 说明                                                                                                          |
| --------------------- | --------------- | ----------------------------------------------------------------------------------------------------------- |
| `encounterId`         | `Long`          | 就诊 id                                                                                                       |
| `accountId`           | `Long`          | 就诊默认个人现金账户 id（`mapRegistrationAccountsByEncounterIds`；未开立可 null；**IF-IP-EXE-02 执行时由前端回传**） |
| `encounterBusNo`      | `String`        | 住院号                                                                                                         |
| `statusEnum`          | `String`        | 就诊状态 code                                                                                                   |
| `statusEnumText`      | `String`        | IMP 产品阶段文案（**DM-ADM-002** §2.3.2；`NurseStationEncounterManageMapperImpl` + `InpatientEncounterStatusDisplayUtil`） |
| `patientId`           | `Long`          | 患者 id                                                                                                       |
| `patientName`         | `String`        | 姓名                                                                                                          |
| `genderEnum`          | `String`        | 性别 code                                                                                                     |
| `ageText`             | `String`        | 年龄                                                                                                          |
| `wardId`              | `Long`          | 病区 id                                                                                                       |
| `wardName`            | `String`        | 病区名                                                                                                         |
| `houseName`           | `String`        | 病房名（App 由 `bus_no` 或位置批量解析）                                                                                 |
| `bedId`               | `Long`          | 床 id；待入科可空                                                                                                  |
| `bedName`             | `String`        | 床号                                                                                                          |
| `organizationId`      | `Long`          | 科室 id                                                                                                       |
| `organizationName`    | `String`        | 科室名                                                                                                         |
| `priorityEnum`        | `String`        | 病情/优先级 code                                                                                                 |
| `startTime`           | `LocalDateTime` | 入院时间                                                                                                        |
| `admissionTime`       | `LocalDateTime` | 入科时间（QueryService有则返）                                                                                                |
| `totalAmount`         | `BigDecimal`    | 累计费用                                                                                                        |
| `paidAmount`          | `BigDecimal`    | 已支付                                                                                                         |
| `arrearsAmount`       | `BigDecimal`    | 欠费                                                                                                          |
| `accountBalance`      | `BigDecimal`    | **展示余额**（非 Account.balance 直出）                                                                              |
| `lowBalanceWarning`   | `Boolean`       | 展示余额 < 阈值                                                                                                   |
| `pendingAuditCount`   | `Integer`       | 待校对条数                                                                                                       |
| `pendingExecuteCount` | `Integer`       | 待执行条数                                                                                                       |
| `attendingDoctorName` | `String`        | 主治摘要                                                                                                        |
| `primaryNurseName`    | `String`        | 责任护士摘要                                                                                                      |
| `diagnosisSummary`    | `String`        | 多条诊断用 `；` 拼接的一行摘要（`AdmissionPrimaryDiagnosisAssembler`）；**仅诊断名称**，主诊可选 `[主]` 前缀，**禁止**拼接 `conditionCode` |
| `diagnosisList`       | `List<String>`  | 诊断展示列表（解析快照，**非** JSON 原文）；口径同 `diagnosisSummary`                                                |
| `pinyinCode`          | `String`        | 检索码                                                                                                         |
| `wubiCode`            | `String`        | 检索码                                                                                                         |
| ~~`recallEnabled`~~       | —       | **字段已移除**（**BR-IP-NUR-013**） |
| `nursingLevelServiceRequestId` | `Long` | 当前护理级别服务申请 id；无护理时为 null（**BR-IP-NUR-015**） |
| `nursingLevelActivityId` | `Long` | 护理级别 activityId |
| `nursingLevelName` | `String` | 护理级别展示名 |
| `nursingLevelStatusEnum` | `String` | 护理级别医嘱状态（`DRAFT`/`ACTIVE`/`VERIFIED`/`STOPPED` 等） |


**失败码口径**


| 错误码             | 场景                               | 用户提示               |
| --------------- | -------------------------------- | ------------------ |
| `EX-IP-NUR-101` | QueryService/批量查询失败                       | 加载失败，请稍后重试         |
| （Gate）          | `nurseStationTaskFilter` 非法 code | 参数错误（文案 SDD 与前端约定） |


**Mapper 方法签名**

- `toInpatientEncounterPageQuery(PagePatientQueueRequest request, List<Long> managedWardIds, Collection<Long> encounterIdsIn): InpatientEncounterPageQuery`
- `toPatientQueueItemDto(InpatientEncounterContextQueryModel row, NurseStationTaskCountBundle counts, BigDecimal accountBalance, boolean lowBalanceWarning, DisplayEnrichment enrichment, DisplayOverlay overlay, String priorityEnum, Long accountId): PatientQueueItemDto`
- `toPagePatientQueueResponse(Page<PatientQueueItemDto> page): PagePatientQueueResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                              | 输入要点                                            | 说明                                                               |
| --- | ---- | --------------------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------- |
| 1   | Gate | `PageWindowGate#requirePositivePageWindow`                      | `pageNo`/`pageSize`                             | 失败 → 不继续                                                         |
| 2   | Gate | App 校验 `nurseStationTaskFilter`                                 | 命中 `InpatientNurseStationConstants` 三者之一或空      | 非法抛 Gate 异常                                                      |
| 2b  | Gate | App 校验 `nurseStationStatusFilter`                               | `STATUS_FILTER_*` 白名单或空                         | 非法抛 Gate 异常                                                      |
| 3   | Pre  | `CurrentManagedWardLocationIdsResolver#listManagedActiveWardsOrEmpty` → App `extractWardLocationIds` | 当前用户 | 与 IF-01 树同一数据源写入 `managedWardIds` |
| 4   | Pre  | `NurseStationEncounterManageMapper#toInpatientEncounterPageQuery` | `wardId`/`houseId`/`keyword`/分页 | 设 `nurseStationQueue=true`；**强制** `nurseStationStatusFilter=null`（状态筛选已废止） |
| 5   | Pre  | **若** `nurseStationTaskFilter` 非空：`                             | 地理条件同步骤 4，`pageSize` 放大或专用内部查询取全量 `encounterId` | **BR-IP-NUR-007** 预收窄                                            |
| 6   | Pre  | `MedicationRequestQueryService#listByEncounterIdsAndStatusEnum` | 候选 ids，`ACTIVE`                                 | 待校对输入                                                            |
| 7   | Pre  | `ServiceRequestQueryService#listByEncounterIdsAndStatusEnum`    | 候选 ids，`ACTIVE`                                 | 同上                                                               |
| 8   | Pre  | `MedicationRequestQueryService#listByEncounterIdsAndStatusEnum` | 候选 ids，`VERIFIED`                               | 待执行输入                                                            |
| 9   | Pre  | `ServiceRequestQueryService#listByEncounterIdsAndStatusEnum`    | 候选 ids，`VERIFIED`                               | 同上                                                               |
| 10  | Pre  | `ProcedureQueryService#listByEncounterIds`                      | 候选 ids                                          | 净执行统计                                                            |
| 11  | Pre  | App `filterEncounterIdsByTaskFilter`                            | 步骤 6～10 计数                                      | 写入 `encounterIdsIn`；**若** filter 为空则跳过 5～11                      |
| 12  | Pre  | **`EncounterContextReader#pageInpatientNurseStationQueue`** | 步骤 4 Query | **Q-IPI-005**；仅读 |
| 13  | Pre  | `AccountQueryService#mapRegistrationAccountsByEncounterIds`     | **当前页** encounterId 集合                          | **BR-IP-NUR-006**；禁止 N+1                                         |
| 14  | Pre  | `ChargeItemQueryService#mapPendingSettlementTotalPriceByEncounterIds` | **当前页** encounterId 集合                    | 待结算合计 |
| 14b | Pre  | `ChargeItemQueryService#mapSettledTotalPriceByEncounterIds` | **当前页** encounterId 集合                 | 已结清文案 |
| 14c | Pre  | `PaymentReconciliationQueryService#filterReversedPendingResettleEncounterIds` | **当前页** encounterId 集合              | 反结后展示模式 |
| 14d | Pre  | `InpatientPatientQueueNursingLevelEnrichAssembler#enrichByEncounterIds` | **当前页** encounterId 集合；`referenceDate=当日` | **BR-IP-NUR-015**；禁止 N+1 |
| 15  | Pre  | App `buildTaskCountsForEncounters`                              | 当前页 ids                                         | 步骤 6～10 口径；**仅计** `doctor-prescription`（**BR-IP-NUR-004/005**）；执另排除转科/出院/护理 |
| 16  | Pre  | `InpatientInfoQueueDisplayAssembler#buildOverlay`               | 当前页QueryService行                                         | 缺列回填                                                             |
| 17  | Asm  | `InpatientAccountBalanceDisplayAssembler#assemble` | 账户 + 步骤 14/14b + 14c 反结标记                      | → `accountBalance` / 文案 / `lowBalanceWarning`                          |
| 18  | Asm  | `NurseStationEncounterManageMapper#toPatientQueueItemDto` + App `applyNursingLevelDisplay`             | … | 装配 `diagnosisList`/`diagnosisSummary`；**无** `recallEnabled`；床名可含「原…（未占床）」 |
| 19  | Asm  | `NurseStationEncounterManageMapper#toPagePatientQueueResponse`        | 步骤 12～18                                        | 纯装配                                                              |


### 3.4 IF-IP-NUR-03 `getPatientDetail`（患者详情查询）

**HTTP**：`POST /api/app/nurseStationEncounterManage/getPatientDetail`

**Request**


| 字段            | 类型     | 必填  | 说明   |
| ------------- | ------ | --- | ---- |
| `encounterId` | `Long` | Y   | 就诊主键 |


**Response**


| 字段       | 类型                 | 说明   |
| -------- | ------------------ | ---- |
| `detail` | `PatientDetailDto` | 全景详情 |


`**PatientDetailDto`**


| 字段                             | 类型                     | 说明                                                                                                                                    |
| ------------------------------ | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| （含 `PatientQueueItemDto` 全部字段） | —                      | 列表字段超集（含 `diagnosisList`/`diagnosisSummary`、`wardId`/`wardName`/`houseName`/`bedId`/`bedName`、**`nursingLevel*`** 四字段 **BR-IP-NUR-015** 等）；**禁止** API 透传 `diagnosis_json`、`location_json` |
| `patientPhone`                 | `String`               | 电话                                                                                                                                    |
| `idCardNo`                     | `String`               | 证件号（完整号，不脱敏；与队列同源）                                                                                                                       |
| `participants`                 | `List<ParticipantDto>` | 全角色参与者                                                                                                                                |
| `admissionVitalSigns`          | `AdmissionVitalSignSubmitDto` | 入院体征（**BR-IP-NUR-014**；与 **002** 同 DTO）                                                                                      |


`**ParticipantDto`**


| 字段                 | 类型       | 说明                                                                 |
| ------------------ | -------- | ------------------------------------------------------------------ |
| `roleCode`         | `String` | 角色 code（`EncounterParticipantRole#getCode()`）                         |
| `roleName`         | `String` | 角色展示名（`EncounterParticipantRole#getDisplay()`，经 `EncounterParticipantRoleDisplayUtil`） |
| `practitionerId`   | `Long`   | 医务人员 id |
| `practitionerName` | `String` | 姓名      |


**失败码口径**


| 错误码             | 场景          | 用户提示       |
| --------------- | ----------- | ---------- |
| `EX-IP-NUR-102` | 就诊不在护士站可见范围 | 患者不可用，请刷新  |
| `EX-IP-NUR-101` | 读失败         | 加载失败，请稍后重试 |


**Mapper 方法签名**

- `toPatientDetailDto(PatientQueueItemDto base, String patientPhone, String idCardNo, List<ParticipantDto> participants, AdmissionVitalSignSubmitDto admissionVitalSigns): PatientDetailDto`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                              | 输入要点                 | 说明                             |
| --- | ---- | --------------------------------------------------------------- | -------------------- | ------------------------------ |
| 1   | Gate | —                                                               | `encounterId` 非 null |                                |
| 2   | Pre  | `EncounterQueryService#mustGetEncounterOfClass`                 | `IMP`                | **BR-IP-NUR-002** 可见状态由QueryService二次保证 |
| 3   | Pre  | `EncounterContextReader#mustGetInpatientContextByEncounterId` | `encounterId`        | 不存在 → **EX-IP-NUR-102**        |
| 4   | Pre  | 复用 **IF-IP-NUR-02 序号 13～16** 口径                                 | 单 encounter          | 余额、计数、`InpatientInfoQueueDisplayAssembler`、诊断解析与列表一致 |
| 4b  | Pre  | `InpatientPatientQueueNursingLevelEnrichAssembler#enrichByEncounterIds` + App `applyNursingLevelDisplay` | 单 encounter；`referenceDate=当日` | **BR-IP-NUR-015**；口径同列表 |
| 5   | Pre  | `EncounterQueryService#listDisplayEncounterParticipants`         | `encounterId`        | 全角色展示（活动优先，清床/召回后回落末次 COMPLETED）；`roleName` 经 `EncounterParticipantRoleDisplayUtil` |
| 6   | Pre  | `PatientQueryService#loadPatientDetail`                         | `patientId`          | 电话、证件                          |
| 7   | Pre  | `AdmissionVitalSignSupport#toSubmitDto` | `recordedAt = encounter.start_time` | **BR-IP-NUR-014**；内部 `listHeadersByEncounterId`（`type_enum=TEMPERATURE_SHEET`）+ `getWithDetailsById` |
| 8   | Asm  | `toPatientQueueItemDto` → App `applyNursingLevelDisplay` → `toPatientDetailDto`                  | 步骤 3～7               | 详情增量：电话/证件/参与者/入院体征；**`nursingLevel*`** 自 base 拷贝 |


### 3.5 IF-IP-NUR-04 `savePatientInfo`（患者信息维护）

**HTTP**：`POST /api/app/nurseStationEncounterManage/savePatientInfo`

**Request**


| 字段             | 类型                           | 必填  | 说明          |
| -------------- | ---------------------------- | --- | ----------- |
| `encounterId`  | `Long`                       | Y   | 就诊 id       |
| `priorityEnum` | `String`                     | N   | 病情/优先级 code |
| `participants` | `List<ParticipantSubmitDto>` | N   | 责任医护变更集     |
| `admissionVitalSigns` | `AdmissionVitalSignSubmitDto` | N | 入院体征（全空则跳过；**BR-IP-NUR-011**） |


`**ParticipantSubmitDto`**


| 字段               | 类型       | 必填  | 说明                                    |
| ---------------- | -------- | --- | ------------------------------------- |
| `roleCode`       | `String` | Y   | `EncounterParticipantRole#getCode()`，落库 `adm_encounter_participant.role_code` |
| `practitionerId` | `Long`   | Y   | 医务人员 id                               |


**Response**


| 字段            | 类型        | 说明    |
| ------------- | --------- | ----- |
| `encounterId` | `Long`    | 就诊 id |
| `saved`       | `Boolean` | 是否成功  |


**失败码口径**


| 错误码             | 场景             | 用户提示        |
| --------------- | -------------- | ----------- |
| `EX-IP-NUR-201` | 状态不允许 / 领域校验失败 | 保存失败，请联系管理员 |
| `EX-IP-NUR-102` | 就诊不可见          | 患者不可用，请刷新   |


**Mapper 方法签名**

- `toSaveNurseStationPatientCommand(SavePatientInfoRequest request, Long operatorId): SaveNurseStationPatientCommand`（包 `com.zxplat.domain.administration.encounter.command`）

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                               | 输入要点                                      | 说明                                                     |
| --- | ---- | -------------------------------------------------------------------------------- | ----------------------------------------- | ------------------------------------------------------ |
| 1   | Gate | `CurrentOperatorResolver#requireOperatorId`                                      | —                                         | 写路径                                                    |
| 2   | Pre  | `EncounterQueryService#mustGetEncounterOfClass`                                  | `IMP`                                     |                                                        |
| 3   | Pre  | App 校验 **BR-IP-NUR-010**                                                         | `status_enum ∈ {REGISTERED, IN_PROGRESS}` | 否则 **EX-IP-NUR-201**；**禁止**改床/改状态                      |
| 4   | Cmd  | `EncounterDomainService#saveNurseStationPatient(SaveNurseStationPatientCommand)` | 步骤 1 Mapper 产物                            | **写库点**（`DM-ADM-002` §6 `saveNurseStationPatient` 已落地） |
| 5   | Cmd  | `AdmissionVitalSignSupport#saveIfPresent`（可选） | `admissionVitalSigns`；`recordedAt=encounter.start_time`；当前活动床 | 规则同 **002 BR-IP-ATD-023/024**；**须在步骤 4 成功后**；**无 `start_time` 则跳过** |
| 6   | —    | 投影                                                                               | `InpatientEncounterContextQueryModel` 等                         | **异步**；禁止同步调QueryService                                        |
| 7   | Asm  | `NurseStationEncounterManageMapper#toSavePatientInfoResponse`                          | `encounterId`                             |                                                        |


## 4. QueryService 与事件刷新


| QueryService             | 查询方法                                | 本 AS 用途                          |
| --------------- | ----------------------------------- | -------------------------------- |
| `InpatientEncounterContextQueryModel` | `pageInpatientNurseStationQueue`             | IF-IP-NUR-02 队列骨架（**Q-IPI-005**） |
| `InpatientEncounterContextQueryModel` | `mustGetInpatientContextByEncounterId` | IF-IP-NUR-03 详情骨架                |


**QueryService 查询语义（最终一致）**


| 领域事件（示例）                        | QueryService                            | 更新动作                         |
| ------------------------------- | ------------------------------ | ---------------------------- |
| `InpatientBedAssignedEvent`     | `InpatientEncounterContextQueryModel`                | `status_enum`、`bed_id`/`bed_name`；**不**改 `ward_id`（**BR-IPI-004**，见QueryService §4.2） |
| `AccountBalanceEditedEvent`     | `InpatientEncounterContextQueryModel` 费用列 + App 展示余额 | 余额/预警                        |
| 医嘱校对/执行类事件                      | —                              | App 下次查询重算 `pending_*_count` |
| `NurseStationPatientSavedEvent` | `InpatientEncounterContextQueryModel`                | 医护名、priority 展示列刷新（QueryService 待对齐）   |
| `VitalSignSavedEvent`           | —（App 读 `dia_vital_sign`）                         | 详情 `admissionVitalSigns`                       |


字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §4.2 为唯一真源；本文不重复 §4.2 细表。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（对照 §3.5）


| IF           | App 方法            | Cmd 序号 | 领域调用                                             | 聚合写入顺序                                                                                                                             |
| ------------ | ----------------- | ------ | ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| IF-IP-NUR-04 | `savePatientInfo` | 4～5   | `saveNurseStationPatient` + 可选 `saveVitalSign`（经 Support） | ① `adm_encounter`（priority）② 参与者按角色 COMPLETED+INSERT ③（可选）`dia_vital_sign` 主表+明细 merge upsert |


IF-IP-NUR-01～03：**无 Cmd**。

### 5.2 事务边界 / 失败回滚


| IF              | 事务策略                                                                                          | 失败回滚                                   |
| --------------- | --------------------------------------------------------------------------------------------- | -------------------------------------- |
| IF-IP-NUR-04    | `EncounterDomainService#saveNurseStationPatient` 方法级 `@Transactional`（与 DM-ADM-002 领域 SDD 一致） | 参与者与病情同事务；任一步失败全回滚 → **EX-IP-NUR-201** |
| IF-IP-NUR-01～03 | 无写事务                                                                                          | —                                      |


## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 承诺      | 代码位置（必须）                                   | 关联步骤         |
| ------- | ------------------------------------------ | ------------ |
| 读接口天然幂等 | `NurseStationEncounterManageAppService` 读方法无写副作用 | §3.2～3.4 全序列 |
| 维护接口非幂等 | 每次保存对提交角色结束活动参与者并 INSERT 新行                | §3.5 序号 4    |


### 6.2 并发控制


| 承诺                              | 代码位置（必须）                 | 关联步骤      |
| ------------------------------- | ------------------------ | --------- |
| 列表/详情无乐观锁                       | 只读                       | §3.2～3.4  |
| 维护并发以 Encounter 聚合版本为准（**目标态**） | `EncounterDomainService` | §3.5 序号 4 |


### 6.3 重试与容错


| 承诺                      | 代码位置（必须）                                   | 关联步骤     |
| ----------------------- | ------------------------------------------ | -------- |
| 读失败统一 **EX-IP-NUR-101** | AppService catch 或让 `BusinessException` 透出 | §3.2～3.4 |
| 写失败 **EX-IP-NUR-201**   | IF-IP-NUR-04                               | §3.5     |


### 6.4 数据脱敏


| 承诺             | 代码位置（必须）                                | 关联步骤      |
| -------------- | --------------------------------------- | --------- |
| 证件号不脱敏（体征卡/详情与队列展示完整号） | `NurseStationEncounterManageAppService#getPatientDetail` | §3.4 序号 7 |


### 6.5 审计日志


| 承诺              | 代码位置（必须）                                    | 关联步骤        |
| --------------- | ------------------------------------------- | ----------- |
| 维护操作人写入 Command | `SaveNurseStationPatientCommand.operatorId` | §3.5 序号 1、4 |


## 7. 测试要点

### 7.1 功能测试


| 用例                 | 预期                      | 优先级 | 自动化归属              |
| ------------------ | ----------------------- | --- | ------------------ |
| 管理病区非空仅返回子集树       | 树节点 ⊆ 授权病区              | P0  | App IT             |
| 无管理病区（医院租户）返回空树        | 树 `wardNodes` 为空；队列 0 条 | P0  | App IT             |
| 选病房后列表窄于选病区        | `houseId` 优先            | P0  | App IT             |
| 划价后任务计数不变 | 直接划价后 `pendingAuditCount`/`pendingExecuteCount` 不增加 | P0 | App IT |
| 任务筛选 pending-audit | 仅 `pendingAuditCount>0` | P0  | App IT             |
| 列表与详情余额/计数一致       | 同源组装                    | P0  | App IT             |
| 维护不改床不改状态          | 床号、status 不变            | P0  | App IT + Domain IT |


### 7.2 非功能测试


| 用例             | 预期                             | 优先级 | 自动化归属   |
| -------------- | ------------------------------ | --- | ------- |
| 列表页账户批量加载      | 无 N+1 `getRegistrationAccount` | P0  | 静态扫描/IT |
| 默认 pageSize≤50 | Gate 通过                        | P1  | App IT  |


### 7.3 QueryService一致性


| 用例        | 预期           | 优先级 | 自动化归属 |
| --------- | ------------ | --- | ----- |
| 分床后队列床号更新 | QueryService 刷新延迟可接受范围内一致 | P1  | E2E   |


### 7.4 集成测试


| 用例        | 预期                                                | 优先级 | 自动化归属  |
| --------- | ------------------------------------------------- | --- | ------ |
| POST 路径注册 | `POST /api/app/nurseStationEncounterManage/loadWardTree`；`POST /api/app/nurseStationEncounterManage/pagePatientQueue`；`POST /api/app/nurseStationEncounterManage/getPatientDetail`；`POST /api/app/nurseStationEncounterManage/savePatientInfo` | P0  | API IT |


### 7.5 待补测试用例


| 用例 ID             | 说明                                                                  | 优先级 | 状态  |
| ----------------- | ------------------------------------------------------------------- | --- | --- |
| TC-IP-NUR-04-001  | IF-IP-NUR-04：`saveNurseStationPatient` 参与者 COMPLETED+INSERT、禁止改床/状态 | P0  | 待补  |
| TC-IP-NUR-02-PERF | 任务筛选大数据量预收窄（候选 encounter 全量拉取）                                      | P1  | 待补  |


## 8. 变更历史


| 版本   | 日期         | 说明                                                                                                                                                                |
| ---- | ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| v2.31 | 2026-08-19 | **pendingExecuteCount** 叠加 **BR-IP-EXE-038** 按天抑制；对齐 **AS v1.36** / **AS-IP-NUR-004 v2.51** |
| v2.30 | 2026-08-17 | **入科状态下拉**：前端 **在科/待入科**（**BR-IP-NUR-017**）；对齐 **AS v1.32**；不改 IF-IP-NUR-02 请求参数 |
| v2.29 | 2026-08-07 | **pendingExecuteCount**：长期药品与 **BR-IP-EXE-035** 一盒一点同源（`StandingOrderPackageBatchPlanTimeResolver`）；对齐 **AS v1.29** |
| v2.28 | 2026-07-22 | **pendingExecuteCount**：皮试检查 companion SR **计入**；对齐 **AS v1.27** / **AS-IP-NUR-011 v1.4** |
| v2.27 | 2026-07-17 | **pendingExecuteCount**：仅排除皮试检查 companion SR；皮试药计入（**v2.28 废止排除**） |
| v2.26 | 2026-07-16 | **收尾**：废止 `SETTLED_DISCHARGED`/状态筛选；移除 `recallEnabled` 字段；未占床床列「原床（未占床）」；对齐 **AS v1.24** |
| v2.25 | 2026-07-16 | **出院召回 redesign**：废止 `recallEnabled`/护士站召回；上游 **FIN-004 IF-IP-RCL-01**；对齐 **AS v1.23** |
| v2.24.1 | 2026-07-04 | **IF-IP-NUR-03**：**BR-IP-NUR-015** 扩展至详情；编排序列 4b；`toPatientDetailDto` 拷贝 `nursingLevel*` |
| v2.24 | 2026-07-04 | **BR-IP-NUR-015**：IF-IP-NUR-02 增护理级别四字段 + 编排序列 14d/18；依赖 **`InpatientPatientQueueNursingLevelEnrichAssembler`** + **DM-WKF-004** §6.2.28 |
| v2.23 | 2026-06-20 | **orchestration 落点对齐**：`AdmissionVitalSignsupport → `module.orchestration.nursestation`；`AdmissionVitalSignSubmitDto` → `module.common.dto.nursestation` |（已迁至 application.shared.nursestation-device-dispense）
| v2.22 | 2026-06-20 | **support 落点对齐**：`InpatientAccountBalanceDisplayDtoApplier` → `module.common.assembler.inpatient`；对齐 **AS v1.20** |（已迁至 application.shared.inpatient）
| v2.23 | 2026-08-17 | 未占床床列改为「原{房}-{床}（未占床）」；对齐 **DM-ADM-002 v1.44** |
| v2.22 | 2026-08-17 | IF-IP-NUR-03：`listDisplayEncounterParticipants`（活动优先 / 末次 COMPLETED）；对齐 **AS v1.33** / **DM-ADM-002 v1.43** |
| v2.21 | 2026-06-19 | **账户余额语义**：展示 = 库内 balance − 待结算；`InpatientAccountBalanceDisplayAssembler` 文案；废弃 chargedOccupancy 展示公式 |
| v2.10 | 2026-06-19 | **DM-DIA-002 v2.0 对齐**：入院体征读/写改 `AdmissionVitalSignsupport + `saveVitalSign` / `listHeadersByEncounterId`；体温写主表 |
| v2.9.1 | 2026-05-28 | §2.1/§2.2/IF-IP-NUR-02 序列：**`EncounterContextFacade#pageInpatientNurseStationQueue`**、`mustGetInpatientContextByEncounterId` |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：`InpatientEncounterContextQueryModel` 队列；§3/§4 与 `zxplat` 一致 |
| v1.0 | 2026-05-20 | 首版：领域/读侧闭合说明（不完整）                                                                                                                                                 |
| v1.1 | 2026-05-20 | 删除域层跨聚合计数服务                                                                                                                                                       |
| v1.2 | 2026-05-20 | 任务筛选改回 `InpatientNurseStationConstants`                                                                                                                           |
| v2.0 | 2026-05-20 | **按应用服务 SDD Skill 重写**：完整 §1～§8；IF-01～04 契约、编排序列、白名单双向表；标注 `DM-ADM-002` §6 `saveNurseStationPatient`（IF-04 领域命令）与 App 待生成                                         |
| v2.1 | 2026-05-20 | §5.1/§6.1：参与者轨迹 **COMPLETED+INSERT**（按角色）；与 **002** 分床双入口、**AS-IP-NUR-001** v1.4 对齐                                                                               |
| v2.2 | 2026-05-20 | 队列 `statusEnumText` 对齐 **DM-ADM-002** §2.3.2 / QueryService **v2.9**（`InpatientEncounterStatusDisplayUtil`）                                                                |
| v2.3 | 2026-05-20 | §2：澄清分包目标为 `**encountermanage` + `ATDManager`**（对齐门诊）；纠正误写 `patientmanage`；标注住院 001 成码暂仍在 `nursestation` 根下                                                       |
| v2.4 | 2026-05-20 | IF-IP-NUR-02/03：`diagnosisList`+`diagnosisSummary` 经 `AdmissionPrimaryDiagnosisAssembler`；读侧 `pageInpatientNurseStationQueue` 登记后 `ward_id` 投影与待入科无病区兼容（见QueryService §3 Q-IPI-005） |
| v2.5 | 2026-05-20 | 对齐QueryService **v3.2**：医生申请阶段 `InpatientAdmissionRequestedEvent.wardId` 已投影 `ward_id`（待登记列表同源；见 **AS-OP-DOR-005** / **AS-IP-FIN-001**） |
| v2.5 | 2026-05-20 | **Gap 收口**：删除 §2.5 SDD-GAP 表；§7.5 改为待补测试用例（非 Gap）；对齐 AS/SDD Skill（Gap 仅 AS §3）                                                                                    |
| v2.6 | 2026-05-20 | **HTTP 与分包对齐**：AppService 更名为 `InpatientNurseStationEncounterManageAppService`；前缀 `inpatientNurseStationEncounterManage`（废弃 `inpatientNurseStation`）；§2 补全 request/response/dto 清单 |
| v2.7 | 2026-05-20 | **短命名对齐成码**：`NurseStationEncounterManageAppService` / `NurseStationEncounterManageMapper`；HTTP 前缀 `nurseStationEncounterManage`（废弃 `inpatientNurseStationEncounterManage`） |
| v2.8 | 2026-05-20 | IF-IP-NUR-03：删除出参 `locationJson`（位置沿用列表 `ward*`/`houseName`/`bed*`）；诊断仅 `diagnosisList`+`diagnosisSummary`（`AdmissionPrimaryDiagnosisAssembler`）；参与者 `roleName` 经 `EncounterParticipantRoleDisplayUtil` |
| v2.9 | 2026-05-20 | 参与者角色码表收敛为共享枚举 `EncounterParticipantRole`（`EnumInterface`）；§2.3 金骨架与 IF-IP-NUR-03/04 `roleCode` 契约对齐 |
| v2.12 | 2026-05-24 | §2.4 任务计数排除 **`nurse-pricing`**；对齐 **AS v1.9** / **AS-IP-NUR-005 v2.0** |
| v2.13 | 2026-05-24 | §4.2：`InpatientBedAssignedEvent` 投影不覆盖病区；对齐QueryService **v3.6** / **BR-IPI-004** / **AS-IP-NUR-002 v2.0** |
| v2.11 | 2026-05-22 | IF-IP-NUR-02 列表/详情 `PatientQueueItemDto` 增加 `accountId`（`mapRegistrationAccountsByEncounterIds`）；供 IF-IP-EXE-02 前端回传 |
| v2.10 | 2026-05-20 | `InpatientInfoQueueDisplayAssembler` 恢复 `mapInpatientProjectionSnapshotsByIds` 人口学回填；诊断展示行禁止拼接目录编码 |
| v2.21 | 2026-06-19 | **成码对齐**：§2 包结构 `AdmissionVitalSignSaveContext`/`VitalSignAdmissionItemCodeConstants`；Support `toSubmitDto`/`saveIfPresent`；IF-IP-NUR-04 无 `start_time` 跳过；前端 ASSIGN 体征提交口径 |
| v2.20 | 2026-06-19 | **入院体征**：§3.4/§3.5 `admissionVitalSigns`；`VitalSignQueryService` + `AdmissionVitalSignsupport；对齐 **AS v1.17** / **002 v2.12** / **DM-DIA-002** |
| v2.19 | 2026-06-18 | **成码类名锚点**：§1.1 上游 **002/FIN-004**；`recallEnabled` 装配步骤；召回写 **`RecallReverseSettleSupport#execute`**；对齐 **AS v1.16** |
| v2.18 | 2026-06-18 | **管理病区硬权限**：IF-IP-NUR-01 删除 App 层 `listAllActiveWards` 回落；§2.2 白名单、§3.2 编程序列、§7.1 测试同步 **AS v1.15** |
| v2.17 | 2026-06-18 | **召回口径对齐 002 v2.9**：`recallEnabled` **仅 `DISCHARGED`** |
| v2.16 | 2026-06-18 | **定稿对齐成码**：§1.1 补充前端 `views/inpatient/nurse-station` 召回入口；**GAP-IP-NUR-006** 应用层+前端全栈闭合 |
| v2.15 | 2026-06-18 | **出院召回读侧**：`nurseStationStatusFilter` / `STATUS_FILTER_*`；`PatientQueueItemDto.recallEnabled`；Q-IPI-005 状态筛选（**GAP-IP-NUR-006 已闭合**） |
| v2.14 | 2026-06-11 | 辅助读 **`loadAdmissionParticipantInit`**（分床医护下拉）；队列 **`nurseStationQueue=true`**；**BR-IP-NUR-003** 收窄（须活动入院病区或活动床位；取消「已登记未分床」单独并入）；`managedWardIds` 与 IF-01 树同源；**HOUSE** 岗位绑定回落上级 **WARD** 管辖 |


