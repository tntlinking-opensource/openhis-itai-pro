# 住院护士站 · 入出转管理 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-002-SDD  
> 版本：v2.19 | 日期：2026-09-11 | 作者：（待填）

**配套文档**

| 类型 | 路径 |
| --- | --- |
| 应用服务 AS | `02-通用语言与领域建模/AppService/AS-IP-NUR-002-住院护士站入出转管理.md` |
| QueryService | `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| 领域事件 | `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService` |
| 成码 | `zxplat/.../nursestation/ATDManager` |

## 1. 实现边界

### 1.1 目标与边界


| 项      | 内容                                                                                                                                                                          |
| ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 模块     | 住院护士站工作台 · **入科** Tab（床位列表 + 分床/换床/互换写）· **出** Tab（床位列表 + 出院前待办读 + 办理出院/清床写）。~~**001 列表出院召回写（ATD-07）**~~ **已废止**（召回见 **AS-IP-FIN-004 IF-IP-RCL-01**） |
| 实现阶段口径 | **目标态契约** IF-IP-NUR-ATD-01～06；~~ATD-07~~ 已迁 **FIN-004**；应用层 **`NurseStationATDManagerAppService`**（ATD-01～06），HTTP 前缀 **`nurseStationATDManager`** |
| 编排权威   | `AS-IP-NUR-002-住院护士站入出转管理.md`（**v2.21**）§1.3、§1.7、§1.10、§4 |
| 方法权威   | `DM-ADM-002` §2.3.1～2.3.2、§6.1.17～18a、§6.1.21～25；`DM-ADM-003` §6.1.8～9、§6.2.2～4；**`DM-DIA-002`** §6.1；`DM-MED-002`/`DM-MED-003`/`DM-CLN-004` 读侧；**召回写**见 **`AS-IP-FIN-004`** |
| 严格对齐   | IF 编号 **IF-IP-NUR-ATD-01～06**（~~07~~ 已废止） |
| 患者上下文  | 树/列表/详情/维护仍走 **AS-IP-NUR-001**；**出院召回写** **AS-IP-FIN-004 IF-IP-RCL-01**（收银台）；校对出院 **AS-IP-NUR-003**；出院全结 **AS-IP-FIN-003**；反结 **AS-IP-FIN-004** |


**Out of scope（重复 AS §1.4）**


| 类别           | 内容                                               |
| ------------ | ------------------------------------------------ |
| 患者树/列表/详情/维护 | **AS-IP-NUR-001**                                |
| **转出** Tab、转科确认写 | 另期 AS；本期不做 |
| 开立/校对出院医嘱、医嘱执行/发药**写** | **AS-IP-DOR-003** / **AS-IP-NUR-003** / **AS-IP-NUR-004** / **AS-IP-PHA-001** |
| 住院结算 `PENDING→COMPLETED` | **AS-IP-FIN-003** |
| 体温单/护理记录独立 CRUD | 另期 AS（**DM-DIA-002** 读侧已供本 AS 写入） |
| QueryService 查询列扩展       | 不扩展 `InpatientEncounterContextQueryModel` 投影列 |


**上游依赖**


| 文档                         | 关系                                                             |
| -------------------------- | -------------------------------------------------------------- |
| `AS-IP-NUR-001`            | 左侧树范围 **BR-IP-NUR-003**；办理前 **IF-IP-NUR-03** 详情；占床展示字段与列表卡片同口径 |
| `AS-IP-FIN-002`            | 床位列表可选展示余额（**BR-IP-ATD-007**）                                  |
| `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` | 批量 `mustGetInpatientContextByEncounterId` / 按 id 列表查询             |


### 1.2 术语口径


| 项          | 约定                                                                                                                                                                       |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 状态/床态 code | 持久化与比较一律 **String `code`**（`EncounterStatus`、`LocationOperational` 等）                                                                                                    |
| 轨迹时刻       | **禁止** Request 传入 `assignTime`/`effectiveAt`/`savedAt`；`adm_encounter_location` / `adm_encounter_participant` 的 `start_time`/`end_time` 由 **领域服务** `LocalDateTime.now()` |
| 位置轨迹       | **禁止 UPDATE**；换人/换床 = 旧行 `COMPLETED`+`end_time` + **INSERT** 新 `ACTIVE`（**AS §1.7**）                                                                                     |
| WARD       | 申请/登记已写入；分床/换床/互换 **不收口 WARD**                                                                                                                                           |
| HOUSE+BED  | 分床/换床/互换仅收口并重建 **HOUSE+BED** 各至多一条 `ACTIVE`                                                                                                                              |
| 床资源        | `adm_location.operational_enum`：`IN_USE`/`NOT_IN_USE`；**仅**应用层 `occupyBed`/`releaseBed` 编排                                                                               |
| 写操作类型      | `ExecuteBedOperationRequest.operationType`：`ASSIGN` / `TRANSFER` / `SWAP`（**App 白名单**，见 §2.3）                                                                            |
| 床列表面板      | `ListBedBoardRequest.panel`：`admission`（入科，占床不过滤）/ `discharge`（出，**BR-IP-ATD-010**） |
| 出 Tab 可见性   | `ON_HOLD`+待出院 / `PENDING` / `COMPLETED`；排除待转科、`DISCHARGED`（与 **DM-ADM-002` §2.3.2** 一致） |
| HTTP       | 一律 **`POST`** + `AppServiceControllerRegistrar`；本 AS 独立 Bean **`NurseStationATDManagerAppService`**，前缀 **`nurseStationATDManager`**（废弃 `inpatientNurseStation`、`inpatientNurseStationATDManager` 等长前缀注册名） |


## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  ATDManager/                                       # 本 AS（入出转）
    service/
      NurseStationATDManagerAppService.java
    request/
      ListBedBoardRequest.java
      ExecuteBedOperationRequest.java
      ListDischargePendingExecutionsRequest.java
      ListDischargePendingMedicationsRequest.java
      ExecuteInpatientDischargeRequest.java
      ClearDischargedBedRequest.java
      RecallInpatientDischargeRequest.java
    response/
      ListBedBoardResponse.java
      ExecuteBedOperationResponse.java
      ListDischargePendingExecutionsResponse.java
      ListDischargePendingMedicationsResponse.java
      ExecuteInpatientDischargeResponse.java
      ClearDischargedBedResponse.java
    dto/
      BedBoardItemDto.java
      BedBoardLocationBlockDto.java
      BedBoardOccupantDto.java
      DischargePendingExecutionItemDto.java
      DischargePendingMedicationItemDto.java
    mapper/
      NurseStationATDManagerMapper.java
      NurseStationATDManagerMapperImpl.java
    support/
      AdmissionVitalSignSupport.java                    # ATD-02 ASSIGN 入院体征（见 application.shared）
  encountermanage/
    dto/
      ParticipantSubmitDto.java                     # 分床载荷复用 001

zxplat/health-application/src/main/java/com/openhis/health/application/
  orchestration/nursestation/
    AdmissionVitalSignSupport.java
    AdmissionVitalSignSaveContext.java
  common/dto/nursestation/
    AdmissionVitalSignSubmitDto.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  VitalSignAdmissionItemCodeConstants.java            # 入科抽屉 7 项 vital_signs_code 白名单

zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/financial/settle/
  service/InpatientSettleAppService.java              # IF-IP-RCL-01 recallInpatientDischarge（召回写权威）

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/administration/encounter/
  repository/
    EncounterLocationRepository.java
  service/
    EncounterQueryService.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  InpatientNurseStationConstants.java
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.ATDManager`（本 AS）；001 患者管理为 `com.zxplat.domain.application.inpatient.nursestation.encountermanage`  
> AppService Bean 名 → HTTP 前缀：`nurseStationATDManager`（与 001 `nurseStationEncounterManage` 同短命名规则；**禁止**挂到 `inpatientNurseStation` / `nurseStationEncounterManage` 路由）

### 2.1 依赖注入表


| 依赖                                        | 用途                                                                        |
| ----------------------------------------- | ------------------------------------------------------------------------- |
| `CurrentManagedWardLocationIdsResolver`   | 管理病区解析（ATD-01 默认范围，对齐 **BR-IP-NUR-003**；**B.6.5**）                                  |
| `CurrentOperatorResolver`                 | 写路径操作人（ATD-02，`B.6.5`）                                                    |
| `CurrentPractitionerResolver`             | 入院体征 `recorder_id`（ATD-02 ASSIGN，`B.6.5`）                                  |
| `LocationQueryService`                    | 范围内床位主数据；目标床 `operational_enum` 预检（ATD-01/02）                             |
| `EncounterContextReader`                  | 住院上下文（`listInpatientEncounterContexts`、`mustGetInpatientContextByEncounterId`） |
| `EncounterQueryService`                   | 薄查询：活动床 id；**床→就诊**批量反查（ATD-01/02）                                       |
| `EncounterDomainService`                  | `assignInpatientBed` / `confirmBedTransfer` / `swapInpatientBeds` / `executeInpatientNurseDischarge` / `clearDischargedBed` |
| `LocationDomainService`                   | `occupyBed` / `releaseBed`（ATD-02）                                        |
| `AccountQueryService`                     | 可选展示余额（ATD-01，**BR-IP-ATD-007**）                                          |
| `InpatientInfoQueueDisplayAssembler`      | 与 001 列表相同的医护/诊断展示回填（ATD-01）                                              |
| `InpatientDisplayAccountBalanceAssembler` | 展示余额（可选；**待结算**口径） |
| `ChargeItemQueryService` | 占床 encounter **待结算合计** 批量 |
| `MedicationRequestQueryService` | ATD-03/05 药品请求列表 |
| `ServiceRequestQueryService` | ATD-03/05 诊疗请求列表；ATD-05 出院医嘱时刻 |
| `ProcedureQueryService` | ATD-03/05 临时医嘱执行终态 |
| `MedicationDispenseQueryService` | ATD-04/05 待发/待退药 |
| `AdmissionVitalSignSupport` | ATD-02 **ASSIGN** 入院体征 |
| `MedicationDefinitionQueryService` | ATD-04 药品名称批量 |
| `ActivityDefinitionQueryService` | ATD-03 诊疗名称批量（目标态，按活动 id 批量解析） |
| `NurseStationATDManagerMapper`   | Request/Command/DTO 装配                                                    |


### 2.2 领域方法白名单


| 类型                            | 方法                                                                | 用于 IF                                         |
| ----------------------------- | ----------------------------------------------------------------- | --------------------------------------------- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedActiveWardsOrEmpty`（**医院租户硬权限**，与 **001** 同源）                         | ATD-01（默认范围）                                  |
| `LocationQueryService`        | `listRoomsUnderWard`                                              | ATD-01                                        |
| `LocationQueryService`        | `listBedsUnderParent`                                             | ATD-01                                        |
| `LocationQueryService`        | `findOptionalById`                                                | ATD-02 Pre 目标床/病房校验                           |
| `EncounterQueryService`       | `mustGetEncounterOfClass`                                         | ATD-02                                        |
| `EncounterQueryService`       | `mapActiveBedEncounterIdByLocationIds`                            | ATD-01（**已落地**）                   |
| `EncounterQueryService`       | `requireActiveLocationId`（`formEnum`=`BED`/`HOUSE`，调用方传 errorCode） | ATD-02/06 Pre（**已落地**） |
| `LocationQueryService`        | `mapNamesByIds`                                                   | ATD-01/04 |
| `ActivityDefinitionQueryService` | `findAllByIds`                                                 | ATD-03 |
| `MedicationDefinitionQueryService` | `findAllByIds`                                               | ATD-03/04 |
| `EncounterContextReader` | `listInpatientContextsByEncounterIdsOrdered` | ATD-01 占床患者块（**禁止** `listInpatientEncounterContexts`+`encounterIdsIn`，会套用登记列表默认状态过滤漏 `ON_HOLD`） |
| `AccountQueryService`         | `mapRegistrationAccountsByEncounterIds`                           | ATD-01（可选余额）                                  |
| `EncounterDomainService`      | `assignInpatientBed`                                              | ATD-02 `ASSIGN`                               |
| `EncounterDomainService`      | `confirmBedTransfer`                                              | ATD-02 `TRANSFER`                             |
| `EncounterDomainService`      | `swapInpatientBeds`                                               | ATD-02 `SWAP`                                 |
| `LocationDomainService`       | `occupyBed`                                                       | ATD-02/06 写后床态                                   |
| `LocationDomainService`       | `releaseBed`                                                      | ATD-02/06 释床                                   |
| `EncounterContextReader` | `mustGetInpatientContextByEncounterId` | ATD-03～06 出 Tab 门禁与展示 |
| `MedicationRequestQueryService` | `listMedicationRequestsByEncounter`                             | ATD-03/05 |
| `ServiceRequestQueryService`  | `listServiceRequestsByEncounter`                                | ATD-03/05 |
| `ProcedureQueryService`       | `listByEncounterIds` / `getProcedureRecords`                      | ATD-03/05 |
| `MedicationDispenseQueryService` | `listMedicationDispensesByEncounter`                           | ATD-04/05 |
| `MedicationDefinitionQueryService` | `findAllByIds`                                                | ATD-04 展示名 |
| `EncounterDomainService`      | `executeInpatientNurseDischarge`                                | ATD-05 |
| `EncounterDomainService`      | `clearDischargedBed`                                              | ATD-06 |
| `VitalSignDomainService`      | `saveVitalSign`（经 Support）                                  | ATD-02 **ASSIGN**（步骤 10，可选） |
| `VitalSignQueryService`       | `listHeadersByEncounterId` + `getWithDetailsById`（`type_enum=TEMPERATURE_SHEET`） | ATD-02 merge Pre |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别     | 代码实现锚点                                                                                        | DM 追溯锚点                                              | 说明                                                          |
| ------ | --------------------------------------------------------------------------------------------- | ---------------------------------------------------- | ----------------------------------------------------------- |
| 就诊类别   | `com.zxplat.domain.share.enums.EncounterClass`                                        | `DM-ADM-002` §3 `adm_encounter.class_enum`           | `**IMP`**                                                   |
| 就诊状态   | `com.zxplat.domain.share.enums.EncounterStatus`                                       | `DM-ADM-002` §2.3.1、§6.1.17～24                              | 分床 `REGISTERED`；在科 `IN_PROGRESS`；待出院 `ON_HOLD`；待结算 `PENDING`；已结算 `COMPLETED`；离院 `DISCHARGED`                |
| 位置形态   | `com.zxplat.domain.share.enums.LocationForm`                                          | `DM-ADM-003` §3 `form_enum`                          | 床 `**BED**`；病房 `**HOUSE**`；病区 `**WARD**`                    |
| 位置活动状态 | `com.zxplat.domain.share.enums.EncounterActivityStatus`                               | `DM-ADM-002` §3 `adm_encounter_location.status_enum` | 反查 `**ACTIVE**`                                             |
| 床运营态   | `com.zxplat.domain.share.enums.LocationOperational`                                   | `DM-ADM-003` §3 `operational_enum`                   | `**IN_USE**` / `**NOT_IN_USE**`                             |
| 写操作类型  | `ExecuteBedOperationRequest.operationType` + `InpatientNurseStationConstants.BED_OPERATION_*` | `AS-IP-NUR-002` IF-IP-NUR-ATD-02                     | `**ASSIGN`/`TRANSFER`/`SWAP**`；App 白名单 |
| 床列表面板      | `ListBedBoardRequest.panel` + `InpatientNurseStationConstants.ATD_PANEL_*` | `AS-IP-NUR-002` **BR-IP-ATD-010** | `admission` / `discharge` |
| 诊疗类目排除    | `InpatientNurseStationConstants.SERVICE_CATEGORY_*` | `DM-WKF-004` §4.3 `category_enum` | 转科 `32`、出院 `34`、护理 `26` |
| 请求状态/长临/来源 | `RequestStatus` / `TherapyType` / `GenerateSource` | `DM-MED-003` §3、`DM-WKF-004` §3 | ATD-03/05 过滤与门禁 |
| 发放状态        | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §3 `status_enum` | ATD-04/05：`PREPARATION`/`SUMMARIZED`/`PENDING_REFUND` |
| 执行终态        | `com.zxplat.domain.share.enums.EventStatus` | `DM-CLN-004` §3 `status_enum` | 临时嘱净态判定同 **AS-IP-NUR-004 BR-IP-EXE-006/008**（净 COMPLETED/STOPPED/CANCEL 为终态） |
| 出院类型        | `CustomConstants.EncounterHistoryMilestoneName.DISCHARGE` | `DM-ADM-002` §2.3.2、`InpatientEncounterContextQueryModel.discharge_type_enum` | 待出院分流 |
| 参与者行   | `ParticipantSubmitDto` / `AssignInpatientBedCommand.ParticipantLine`                          | `DM-ADM-002` §3 `adm_encounter_participant`          | 分床 **全量**收口+INSERT                                          |
| 业务消息码  | `InpatientNurseStationConstants.EX_IP_ATD_*`                                                  | `AS-IP-NUR-002` §5                                   | **101～104、201～202、301～306、401～403、501～504** |
| 出院诊疗类目 | `InpatientNurseStationConstants.SERVICE_CATEGORY_DISCHARGE`；反结 Pre 共用 `ServiceRequestCategoryConstants.SERVICE_CATEGORY_DISCHARGE`（值均为 **`"34"`**） | `DM-WKF-004` §4.3 | ATD-05/07 定位出院嘱；`InpatientReverseSettlePresupport 撤销 Pre |
| 低余额阈值  | `InpatientNurseStationConstants.LOW_BALANCE_WARNING_THRESHOLD`                                | `AS-IP-NUR-001` **BR-IP-NUR-006**                    | ATD-01 可选                                           |
| 入院体征项 code | `VitalSignAdmissionItemCodeConstants` + `AdmissionVitalSignSubmitDto`：体温 → 明细 `term_code=vital_signs_code`、`item_code=TEMP`；其余 → 明细 `HEIGHT`/`WEIGHT`/`HR`/`PULSE`/`SBP`/`DBP` | **DM-DIA-002** v2.1 §9.1、§9.1.2 | ATD-02 **ASSIGN**；001/002 Support merge；`type_enum=TEMPERATURE_SHEET` |


### 2.4 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-NUR-002` §3**；本 SDD **禁止**维护 Gap 表。应用层 `AppService`/DTO/Mapper 由本 SDD 驱动生成，**不是**缺口。

**领域读能力（已落地，`DM-ADM-002` §6.2.12～6.2.13）**：`EncounterQueryService#mapActiveBedEncounterIdByLocationIds`、`#requireActiveLocationId`（床位/病房由 `form_enum` 区分）。写侧 `EncounterDomainService#requireActiveBedId` 委托读侧同一口径。

**本 SDD 成码交付**：`InpatientNurseStationConstants`；`NurseStationATDManagerAppService` IF **ATD-01～06**；Request/Response/DTO/Mapper（`com.zxplat.domain.application.inpatient.nursestation.ATDManager`）。~~ATD-07~~ 已迁 **FIN-004**。

### 2.5 出 Tab 读接口公共 Pre（App 内联）

| 步骤 | 逻辑 | 失败码（按 IF） |
| --- | --- | --- |
| 加载QueryService | `EncounterContextReader#mustGetInpatientContextByEncounterId` | 103 / 104 / 304（映射为各 IF 文案） |
| 可见性 | **BR-IP-ATD-010**：`PENDING` / `COMPLETED`；或 `ON_HOLD` 且 `transfer_org_id` 空且（`discharge_type_enum` 非空 **或** 主档 `out_way_code` 非空） | 同上 |

> 实现类方法：`mustGetInpatientInfoForDischargeTab`（`NurseStationATDManagerAppService`）。**禁止**在 查询服务内硬编码出 Tab 过滤——床位图 `panel=discharge` 在 App 组装层过滤占床块。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）


| IF               | 应用服务方法（英文）            | 中文方法名    | HTTP                                                      |
| ---------------- | --------------------- | -------- | --------------------------------------------------------- |
| IF-IP-NUR-ATD-01 | `listBedBoard`        | 床位列表查询   | `POST /api/app/nurseStationATDManager/listBedBoard`        |
| IF-IP-NUR-ATD-02 | `executeBedOperation` | 分床换床互换提交 | `POST /api/app/nurseStationATDManager/executeBedOperation` |
| IF-IP-NUR-ATD-03 | `listDischargePendingExecutions` | 出院前待执行医嘱列表 | `POST /api/app/nurseStationATDManager/listDischargePendingExecutions` |
| IF-IP-NUR-ATD-04 | `listDischargePendingMedications` | 出院前待发退药列表 | `POST /api/app/nurseStationATDManager/listDischargePendingMedications` |
| IF-IP-NUR-ATD-05 | `executeInpatientDischarge` | 办理出院 | `POST /api/app/nurseStationATDManager/executeInpatientDischarge` |
| IF-IP-NUR-ATD-06 | `clearDischargedBed` | 清床离院 | `POST /api/app/nurseStationATDManager/clearDischargedBed` |
| IF-IP-NUR-ATD-06a | `listMissingDispenseTraceTips` | 缺追溯码软提示 | `POST /api/app/nurseStationATDManager/listMissingDispenseTraceTips` |

> ~~**IF-IP-NUR-ATD-07**~~ `recallInpatientDischarge` **已废止** → **AS-IP-FIN-004-SDD §3.4** `POST /api/app/inpatientSettle/recallInpatientDischarge`


### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段       | 含义                                                            | 失败时                                                      |
| -------- | ------------------------------------------------------------- | -------------------------------------------------------- |
| **Gate** | 权限 / 参数 / `operationType` 白名单                                 | 不再调用后续步骤                                                 |
| **Pre**  | 只读：范围、床态、就诊状态、活动床一致性                                          | 抛 `**EX-IP-ATD-201`** 或 `**EX-IP-ATD-101**`，**禁止进入 Cmd** |
| **Cmd**  | 领域 Encounter 写 → Location 床态（**同一 App `@Transactional` 方法内**） | 全回滚 → **EX-IP-ATD-202**                                  |
| **Asm**  | Mapper + `InpatientInfoQueueDisplayAssembler`                 | —                                                        |


记法：`EncounterDomainService#assignInpatientBed` 表示注入 Bean 调用。

### 3.2 IF-IP-NUR-ATD-01 `listBedBoard`（床位列表查询）

**HTTP**：`POST /api/app/nurseStationATDManager/listBedBoard`

**Request**


| 字段        | 类型     | 必填  | 说明                       |
| --------- | ------ | --- | ------------------------ |
| `wardId`  | `Long` | N   | 与 001 树一致：该病区内全部床位       |
| `houseId` | `Long` | N   | 病房位置 id；**优先于** `wardId` |
| `panel`   | `String` | Y | `admission` \| `discharge`（`InpatientNurseStationConstants.ATD_PANEL_*`）；非法 → Gate 异常 |


**Response**


| 字段      | 类型                      | 说明                    |
| ------- | ----------------------- | --------------------- |
| `items` | `List<BedBoardItemDto>` | 范围内**全部**启用床位；**列表顺序**按床位 `Location.busNo` 升序（层级编码固定段位，字典序即按病房分组） |


`**BedBoardItemDto`**


| 字段         | 类型                         | 说明                             |
| ---------- | -------------------------- | ------------------------------ |
| `location` | `BedBoardLocationBlockDto` | 位置块（**必返**）                    |
| `occupant` | `BedBoardOccupantDto`      | 占床患者块；**仅**当 `location.operationalEnum=IN_USE` 且反查到就诊（并经 **BR-IP-ATD-010** 过滤）时非 null；`NOT_IN_USE` 时**强制** null |


`**BedBoardLocationBlockDto`**


| 字段                | 类型        | 说明                                |
| ----------------- | --------- | --------------------------------- |
| `bedId`           | `Long`    | 床位 `locationId`                   |
| `bedName`         | `String`  | 床号/名称                             |
| `houseId`         | `Long`    | 所属病房 id                           |
| `houseName`       | `String`  | 病房名称                              |
| `operationalEnum` | `String`  | `IN_USE` / `NOT_IN_USE`（**code**） |
| `displayOrder`    | `Integer` | 床位展示序号（有则返；**非** `items` 列表排序键） |


`**BedBoardOccupantDto`**（`IN_USE` 时；字段对齐 `**PatientQueueItemDto**` 核心展示，**BR-IP-ATD-001**）


| 字段                    | 类型              | 说明            |
| --------------------- | --------------- | ------------- |
| `encounterId`         | `Long`          | 就诊 id         |
| `patientId`           | `Long`          | 患者 id         |
| `patientName`         | `String`        | 姓名            |
| `genderEnum`          | `String`        | 性别 code       |
| `ageText`             | `String`        | 年龄            |
| `encounterBusNo`      | `String`        | 住院号           |
| `statusEnum`          | `String`        | 就诊状态 code     |
| `statusEnumText`      | `String`        | 状态文案          |
| `priorityEnum`        | `String`        | 病情 code       |
| `admissionTime`       | `LocalDateTime` | 入科时间（QueryService有则返）  |
| `attendingDoctorName` | `String`        | 住院医师摘要        |
| `primaryNurseName`    | `String`        | 责任护士摘要        |
| `diagnosisSummary`    | `String`        | 诊断一行摘要        |
| `accountBalance`      | `BigDecimal`    | 展示余额（**可选**）  |
| `lowBalanceWarning`   | `Boolean`       | 低余额预警（**可选**） |


**失败码口径**


| 错误码             | 场景            | 用户提示   |
| --------------- | ------------- | ------ |
| `EX-IP-ATD-101` | 位置/反查/QueryService批量失败 | 床位加载失败 |


**Mapper 方法签名**

- `toListBedBoardResponse(List<BedBoardItemDto> items): ListBedBoardResponse`
- `toBedBoardLocationBlock(Location bed, String houseName): BedBoardLocationBlockDto`（`houseId` 取自 `bed.parentId`；`houseName` 由 App Pre 批量 `mapNamesByIds` 解析）
- `toBedBoardOccupantDto(InpatientEncounterContextQueryModel row, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay, BigDecimal accountBalance, boolean lowBalanceWarning, String priorityEnum): BedBoardOccupantDto`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                           | 输入要点                                          | 说明                                                                                                                                                                              |
| --- | ---- | ------------------------------------------------------------ | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Gate | App 校验 `panel`                                               | `ATD_PANEL_ADMISSION` / `ATD_PANEL_DISCHARGE`   | 非法 panel → 参数错误                                                                                                                                                              |
| 2   | Pre  | App 内联 + `LocationQueryService#listBedsUnderParent` / `#listRoomsUnderWard` | `wardId`/`houseId`                            | **houseId 优先**；均未选 → `CurrentManagedWardLocationIdsResolver#listManagedActiveWardsOrEmpty`（**医院租户**未绑定则 empty，**不**调 `listAllActiveWards`），再各病区 `listRoomsUnderWard` + 每病房 `listBedsUnderParent`；仅 `wardId` → 该病区内全部床；仅 `houseId` → 该病房下全部床；合并为 `List<Location>` 床位 |
| 3   | Pre  | App 内联                                                       | 步骤 2 床位列表                                   | 按 `Location.busNo` 升序排序（固定段位层级编码；**禁止**用 `displayOrder` 排列表）                                                                                                                    |
| 4   | Pre  | `LocationQueryService#mapNamesByIds`                         | 全部床的 `parentId`（病房 id）                        | 批量解析 `houseName`                                                                                                                                                  |
| 5   | Pre  | `EncounterQueryService#mapActiveBedEncounterIdByLocationIds` | 全部 `bedId`                                    | **BR-IP-ATD-009**；一床一患反查（**不**替代空床判定）                                                                                                                                                              |
| 6   | Pre  | `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered` | 步骤 5 中 **`operational_enum=IN_USE`** 床的占床就诊 id | **禁止** `listInpatientEncounterContexts`（`encounterIdsIn` 仍套用登记列表默认 `PLANNED/REGISTERED/IN_PROGRESS`，会漏 `ON_HOLD`/`PENDING`/`COMPLETED` 占床）；与 001 同源组装器；禁止 N+1 |
| 7   | Pre  | `AccountQueryService#mapRegistrationAccountsByEncounterIds`  | 步骤 6 占床 ids（**若**返回余额）                             | **BR-IP-ATD-007**                                                                                                                                                               |
| 8   | Pre  | `InpatientInfoQueueDisplayAssembler#buildOverlay`            | QueryService行列表                                        | 与 001 列表同源                                                                                                                                                                      |
| 9   | Asm  | App 合并 + Mapper                                              | 每床位置块 + 占床块 | **BR-IP-ATD-001**：空床/占床以 `operational_enum` 为准；`NOT_IN_USE` 强制 occupant=null（活动位置残留 → warn）；`IN_USE` 无 encounter → warn + occupant=null |
| 10  | Asm  | App 内联 `filterOccupantForPanel`                            | `panel` + QueryService行 | **若** `panel=discharge`（**BR-IP-ATD-010**）：仅保留 `ON_HOLD` 且 `transfer_org_id` 为空且（`discharge_type_enum` 非空 **或** 主档 `out_way_code` 非空），或 `PENDING`，或 `COMPLETED`；否则 occupant 置 **null**（床位置块仍返）。**若** `panel=admission`：不过滤 |
| 11  | Asm  | `NurseStationATDManagerMapper#toListBedBoardResponse`         | 步骤 9～10                                          |                                                                                                                                                                                 |


> **分床医护下拉（辅助读）**：UI 打开分床抽屉时调用 **AS-IP-NUR-001-SDD §3.2.1** `loadAdmissionParticipantInit`（同一 `nurseStationEncounterManage` 前缀）；本 ATD AppService **不**重复实现。

### 3.3 IF-IP-NUR-ATD-02 `executeBedOperation`（分床换床互换提交）

**HTTP**：`POST /api/app/nurseStationATDManager/executeBedOperation`

**Request**


| 字段                | 类型                           | 必填  | 说明                                                 |
| ----------------- | ---------------------------- | --- | -------------------------------------------------- |
| `operationType`   | `String`                     | Y   | `ASSIGN` / `TRANSFER` / `SWAP`（**禁止**传时刻字段）        |
| `encounterId`     | `Long`                       | Y   | 当前选中患者（发起方）                                        |
| `houseLocationId` | `Long`                       | 条件  | 目标病房 id（**ASSIGN/TRANSFER** 必填）                    |
| `bedLocationId`   | `Long`                       | 条件  | 目标床 id（**ASSIGN/TRANSFER** 必填）                     |
| `encounterIdB`    | `Long`                       | 条件  | 互换对手就诊 id（**SWAP** 必填）                             |
| `priorityEnum`    | `String`                     | N   | 病情（**ASSIGN** 可选，写入 `adm_encounter.priority_enum`） |
| `participants`    | `List<ParticipantSubmitDto>` | 条件  | **ASSIGN** 建议必填；全量收口+INSERT（**AS §1.7**）           |
| `admissionVitalSigns` | `AdmissionVitalSignSubmitDto` | N | **ASSIGN** 可选；全空则跳过体征写（**BR-IP-ATD-024**） |


> **TRANSFER** 的 `fromLocationId` **不由前台传**：App Pre 用 `requireActiveLocationId(encounterId, BED, …)` 解析，写入 `ConfirmBedTransferCommand`。  
> **SWAP** 的 `toHouseLocationIdA/B`：App Pre 分别对 A、B 调用 `requireActiveLocationId(encounterId, HOUSE, …)`，写入 `SwapInpatientBedsCommand`。

`**AdmissionVitalSignSubmitDto`**（嵌于 Request；字段可空，**至少一项非空**方触发写）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `heightCm` | `String` | 身高 → code `HEIGHT` |
| `weightKg` | `String` | 体重 → code `WEIGHT` |
| `temperature` | `String` | 体温 → code `TEMP` |
| `heartRate` | `String` | 心率 → code `HR` |
| `pulse` | `String` | 脉搏 → code `PULSE` |
| `bloodPressureHigh` | `String` | 收缩压 → code `SBP` |
| `bloodPressureLow` | `String` | 舒张压 → code `DBP` |

> **读回填**不在本 IF：由 **001** `getPatientDetail` 返回同结构 `admissionVitalSigns`（锚点 `recorded_at = encounter.start_time`；**禁止** `getLatestByEncounterId` 填入院表单）。

**Response**


| 字段              | 类型        | 说明       |
| --------------- | --------- | -------- |
| `encounterId`   | `Long`    | 发起方就诊 id |
| `operationType` | `String`  | 回显       |
| `succeeded`     | `Boolean` | 是否成功     |


**失败码口径**


| 错误码             | 场景                           | 用户提示        |
| --------------- | ---------------------------- | ----------- |
| `EX-IP-ATD-201` | 目标床已 `IN_USE` / 状态不允许 / 无活动床 | 请选择空床或互换    |
| `EX-IP-ATD-202` | 领域或床态持久化失败                   | 分床失败，请联系管理员 |
| （Gate）          | `operationType` 非法           | 参数错误        |


**Mapper 方法签名**

- `toAssignInpatientBedCommand(ExecuteBedOperationRequest request, Long operatorId): AssignInpatientBedCommand`
- `toConfirmBedTransferCommand(ExecuteBedOperationRequest request, Long fromLocationId, Long operatorId): ConfirmBedTransferCommand`
- `toSwapInpatientBedsCommand(ExecuteBedOperationRequest request, Long toHouseLocationIdA, Long toHouseLocationIdB, Long operatorId): SwapInpatientBedsCommand`
- `toOccupyBedCommand(Long bedLocationId, Long encounterId, Long operatorId): OccupyBedCommand`
- `toReleaseBedCommand(Long bedLocationId, Long encounterId, Long operatorId): ReleaseBedCommand`
- `toSwapReleaseBedCommand(...)` / `toSwapOccupyBedCommand(...)`：互换床批量 release/occupy（单命令多 item，A.9.1）
- `toExecuteBedOperationResponse(ExecuteBedOperationRequest request): ExecuteBedOperationResponse`
- `AdmissionVitalSignSupport#toSubmitDto(Long encounterId, LocalDateTime recordedAt): AdmissionVitalSignSubmitDto` — 001 IF-IP-NUR-03 回填；内部 `listHeadersByEncounterId`（`type_enum=TEMPERATURE_SHEET`）+ `getWithDetailsById`
- `AdmissionVitalSignSupport#saveIfPresent(AdmissionVitalSignSaveContext ctx): void` — ctx 含 `encounterId`、`patientId`、`locationId`（目标床/活动床）、`recordedAt`、`operatorId`、`recorderId`、`submitDto`；调 `saveVitalSign`

#### 3.3.1 `operationType=ASSIGN`（首次分床）

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                              | 输入要点                                                         | 说明                                                 |
| --- | ---- | ----------------------------------------------- | ------------------------------------------------------------ | -------------------------------------------------- |
| 1   | Gate | `CurrentOperatorResolver#requireOperatorId`     | —                                                            | 写路径                                                |
| 2   | Gate | App 校验 `operationType`                          | `BED_OPERATION_ASSIGN`                                       | 非法 → Gate 异常                                       |
| 3   | Pre  | `EncounterQueryService#mustGetEncounterOfClass` | `IMP`                                                        |                                                    |
| 4   | Pre  | App 校验 **BR-IP-ATD-002**                        | `status_enum=REGISTERED`；无活动 **HOUSE/BED**（可由领域二次校验）         | 否则 **EX-IP-ATD-201**                               |
| 5   | Pre  | `LocationQueryService` 加载目标床                    | `bedLocationId`                                              | `operational_enum=NOT_IN_USE`；否则 **EX-IP-ATD-201** |
| 6   | Pre  | `LocationQueryService` 校验 `houseLocationId`     | 为目标床父级或同病房语义                                                 | 与 DM 位置树一致                                         |
| 7   | Cmd  | `LocationDomainService#occupyBed`               | 目标 `bedLocationId` + `encounterId`                           | **悲观行锁** + `assertVacantForOccupy`；**须在步骤 8 之前**（GAP-ATD-001） |
| 8   | Cmd  | `EncounterDomainService#assignInpatientBed`     | `houseLocationId`、`locationId`、`participants`、`priorityEnum` | **WARD 保留**；HOUSE+BED 收口+INSERT；参与者全量收口；`transitToInProgress` 更新 `start_time`            |
| 9   | Pre  | `AdmissionVitalSignSupport#saveIfPresent`（可选） | `admissionVitalSigns`；锚点 `encounter.start_time`             | **若** DTO 至少一项非空且 `start_time` 非 null：**BR-IP-ATD-023** merge 该时点已有主表+明细 |
| 10  | Cmd  | `VitalSignDomainService#saveVitalSign`（经 Support） | `SaveVitalSignCommand`：`typeEnum=TEMPERATURE_SHEET`；`temperature` 写主表；其余写明细；`recordedAt=encounter.start_time`；`locationId=bedLocationId`；`recorderId` | **须在步骤 7～8 成功后**；发布 `VitalSignSavedEvent` |
| 11  | —    | 投影                                              | `InpatientEncounterContextQueryModel` 等                                            | **异步**；禁止同步调QueryService                                    |
| 12  | Asm  | `toExecuteBedOperationResponse`                 | —                                                            |                                                    |


#### 3.3.2 `operationType=TRANSFER`（换空床）


| 序号  | 阶段   | 调用                                                 | 输入要点                                                | 说明                      |
| --- | ---- | -------------------------------------------------- | --------------------------------------------------- | ----------------------- |
| 1   | Gate | `CurrentOperatorResolver#requireOperatorId`        | —                                                   |                         |
| 2   | Gate | App 校验 `operationType`                             | `BED_OPERATION_TRANSFER`                            |                         |
| 3   | Pre  | `EncounterQueryService#mustGetEncounterOfClass`    | `IMP`                                               |                         |
| 4   | Pre  | App 校验                                             | `status_enum=IN_PROGRESS`                           | **EX-IP-ATD-201**       |
| 5   | Pre  | `EncounterQueryService#requireActiveLocationId`（`BED`） | `encounterId` → `fromLocationId`                    | `DM-ADM-002` §6.2.13         |
| 6   | Pre  | 目标床 `NOT_IN_USE`                                   | `bedLocationId`                                     | **EX-IP-ATD-201**       |
| 7   | Cmd  | `LocationDomainService#occupyBed`                  | 新 `bedLocationId`                                   | **悲观行锁** + `assertVacantForOccupy`（GAP-ATD-001） |
| 8   | Cmd  | `EncounterDomainService#confirmBedTransfer`        | `fromLocationId`、`toHouseLocationId`、`toLocationId` | **WARD 不动**；仅 HOUSE+BED |
| 9   | Cmd  | `LocationDomainService#releaseBed`                 | `fromLocationId`                                    | 原床                      |
| 10  | Asm  | Response                                           | —                                                   | **不改参与者**               |


#### 3.3.3 `operationType=SWAP`（互换床）


| 序号  | 阶段   | 调用                                                   | 输入要点                                          | 说明                      |
| --- | ---- | ---------------------------------------------------- | --------------------------------------------- | ----------------------- |
| 1   | Gate | `CurrentOperatorResolver#requireOperatorId`          | —                                             |                         |
| 2   | Gate | App 校验 `operationType`                               | `BED_OPERATION_SWAP`                          |                         |
| 3   | Pre  | `EncounterQueryService#mustGetEncounterOfClass`      | A=`encounterId`，B=`encounterIdB`，均 `IMP`      |                         |
| 4   | Pre  | App 校验                                               | 双方 `IN_PROGRESS` 且 A≠B                        |                         |
| 5   | Pre  | `EncounterQueryService#requireActiveLocationId`（`BED`） | A→`bedIdA`，B→`bedIdB`                         | `DM-ADM-002` §6.2.13         |
| 6   | Pre  | `EncounterQueryService#requireActiveLocationId`（`HOUSE`） | A→`toHouseLocationIdA`，B→`toHouseLocationIdB` | `DM-ADM-002` §6.2.13         |
| 7   | Pre  | 目标床均已 `IN_USE`                                       | `bedIdA`/`bedIdB`                             | 互换语义（**BR-IP-ATD-005**） |
| 8   | Cmd  | `EncounterDomainService#swapInpatientBeds`           | `encounterIdA/B`、`toHouseLocationIdA/B`       | 领域内交叉 INSERT HOUSE+BED  |
| 9   | Cmd  | `LocationDomainService#swapBedsOperational`          | release 双方原床 + 交叉 occupy                  | **双床排序行锁**、单次 `saveAll`（GAP-ATD-003） |
| 10  | Asm  | Response                                             | —                                             |                         |


**App 事务**：`executeBedOperation` 方法标注 `@Transactional`（与 001 `savePatientInfo` 同级），包裹步骤 7～10（或 ASSIGN 的 7～10；**TRANSFER/SWAP** 无步骤 9～10），任一步失败整体回滚并映射 **EX-IP-ATD-202**。

### 3.4 IF-IP-NUR-ATD-03 `listDischargePendingExecutions`（出院前待执行医嘱列表）

**HTTP**：`POST /api/app/nurseStationATDManager/listDischargePendingExecutions`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 出 Tab 选中患者 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items` | `List<DischargePendingExecutionItemDto>` | 阻塞出院/待处理的医嘱行 |

**`DischargePendingExecutionItemDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | `Long` | 药品/诊疗请求主键 |
| `requestTable` | `String` | `med_medication_request` / `wkf_service_request`（`TableNameConstants`） |
| `therapyEnum` | `String` | `LONG_TERM` / `TEMPORARY` |
| `statusEnum` | `String` | 请求状态 code |
| `displayName` | `String` | 药品名或诊疗名；**嘱托行**优先 `entrust_content`（**BR-IP-ORD-028④**；`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`） |
| `reqOpenedTime` | `LocalDateTime` | 开立时间（`req_opened_time`；未签发可 null；空回落 `req_authored_time`） |
| `rateCode` | `String` | 频次 code（有则返） |
| `doseText` | `String` | 剂量/数量展示（App 组装） |
| `blockingReason` | `String` | 阻塞说明：`DISCHARGE_BLOCKING_LONG_NOT_STOPPED` / `DISCHARGE_BLOCKING_LONG_PENDING_SLOTS` / `DISCHARGE_BLOCKING_TEMP_NOT_COMPLETED` |
| `pendingPlanTimes` | `List<LocalDateTime>` | **仅** `LONG_PENDING_SLOTS`：与门禁 / **Q-IP-EXE-001** 同源的 pending 计划时刻（`listLongTermStoppedPendingPlanTimes`）；其它阻塞原因空列表；**禁止**另算日频次点 |

**失败码口径**

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `EX-IP-ATD-103` | 查询/装配失败 | 待执行医嘱加载失败 |

**Mapper 方法签名**

- `toListDischargePendingExecutionsResponse(List<DischargePendingExecutionItemDto> items): ListDischargePendingExecutionsResponse`
- `toDischargePendingExecutionItemFromMedication(MedicationRequestListQueryModel row, String medicationName, String doseText, String blockingReason): DischargePendingExecutionItemDto`
- `toDischargePendingExecutionItemFromService(ServiceRequestListModel row, String activityName, String doseText, String blockingReason): DischargePendingExecutionItemDto`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 `encounterId` 非空 | — | |
| 2 | Pre | `mustGetInpatientInfoForDischargeTab`（封装 §2.5） | `encounterId` | 出 Tab 可见；不可见 → **EX-IP-ATD-103** |
| 3 | Pre | `MedicationRequestQueryService#listMedicationRequestsByEncounter` | `encounterId` | 仅读 |
| 4 | Pre | `ServiceRequestQueryService#listServiceRequestsByEncounter` | `encounterId` | 仅读 |
| 5 | Pre | App 内联 `filterDischargePendingRequests` | 步骤 3～4 | **BR-IP-ATD-014**：`status_enum ∈ {VERIFIED, STOPPED}`；`generate_source_enum=doctor-prescription`；诊疗 `parent_id` 为空；**排除** `category_enum` ∈ {转科/出院/护理}；**排除**退药子行 |
| 6 | Pre | `ProcedureQueryService#listByEncounterIds` | `encounterId` | 按 `requestId`+`requestTable` 分组 |
| 7 | Pre | App 内联 | 步骤 5～6 | **长期未停**：`therapy_enum=LONG_TERM` 且未 `STOPPED` → `blockingReason=LONG_NOT_STOPPED` |
| 8 | Pre | `NurseStationOrderExecuteListReadAssembler#listLongTermStoppedPendingPlanTimes`（经 `InpatientDischargeClinicalGate`） | 步骤 5～6；`executionDeadline=now` | **长期已停嘱** pending 非空（与 **Q-IP-EXE-001** / **BR-IP-EXE-035** 槽位分桶同源）→ `blockingReason=LONG_PENDING_SLOTS`；Asm 回填 `pendingPlanTimes` |
| 9 | Pre | App 内联 | 步骤 5～6 | **临时**：无 Procedure 或整单净态仍为 pending（**BR-IP-EXE-006/008**）→ `blockingReason=TEMP_NOT_COMPLETED`（`pendingPlanTimes` 空） |
| 10 | Pre | `MedicationDefinitionQueryService#findAllByIds` / `ActivityDefinitionQueryService` 批量 | 活动/药品 id 集合 | 展示名；禁止 N+1 |
| 10a | Asm | **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`** | 诊疗行 `category_enum` + 目录名 + `entrust_content` | **嘱托行** `displayName` 优先正文（**BR-IP-ORD-028④**）；与 **OrderView** 同源 |
| 11 | Asm | `NurseStationATDManagerMapper#toListDischargePendingExecutionsResponse` | 步骤 7～10 | 仅读路径 |

### 3.5 IF-IP-NUR-ATD-04 `listDischargePendingMedications`（出院前待发退药列表）

**HTTP**：`POST /api/app/nurseStationATDManager/listDischargePendingMedications`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 出 Tab 选中患者 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items` | `List<DischargePendingMedicationItemDto>` | 待发/待退药行 |

**`DischargePendingMedicationItemDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | 发放主键 |
| `medicationRequestId` | `Long` | 关联药品请求 |
| `medicationId` | `Long` | 药品定义 id |
| `medicationName` | `String` | 药品名称 |
| `statusEnum` | `String` | `DispenseStatus` code |
| `statusEnumText` | `String` | 状态文案 |
| `quantity` | `BigDecimal` | 数量 |
| `unitCode` | `String` | 单位 code |
| `plannedDispenseTime` | `LocalDateTime` | 计划发药时间（有则返） |
| `performLocationId` | `Long` | 执行/发药库房位置 id |
| `performLocationName` | `String` | 库房名称（批量解析） |

**失败码口径**

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `EX-IP-ATD-104` | 查询/装配失败 | 待发药品加载失败 |

**Mapper 方法签名**

- `toListDischargePendingMedicationsResponse(List<DischargePendingMedicationItemDto> items): ListDischargePendingMedicationsResponse`
- `toDischargePendingMedicationItemDto(MedicationDispenseListQueryModel row, String medicationName, String performLocationName): DischargePendingMedicationItemDto`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 `encounterId` 非空 | — | |
| 2 | Pre | `mustGetInpatientInfoForDischargeTab`（封装 §2.5） | `encounterId` | 出 Tab 可见；不可见 → **EX-IP-ATD-104** |
| 3 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByEncounter` | `encounterId` | |
| 4 | Pre | App 内联 | 步骤 3 | **BR-IP-ATD-015**：`status_enum` ∈ {`PREPARATION`,`SUMMARIZED`,`PENDING_REFUND`} |
| 5 | Pre | `MedicationDefinitionQueryService#findAllByIds` | 药品 id 集合 | 批量名称 |
| 6 | Pre | `LocationQueryService#mapNamesByIds` | `performLocationId` 集合 | 库房名（可选） |
| 7 | Asm | Mapper；`statusEnumText` 经 `EnumUtil.fromCode(DispenseStatus.class, code).getDisplay()` | 步骤 4～6 | |

### 3.6 IF-IP-NUR-ATD-05 `executeInpatientDischarge`（办理出院）

**HTTP**：`POST /api/app/nurseStationATDManager/executeInpatientDischarge`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 待出院患者 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 id |
| `succeeded` | `Boolean` | 是否成功（含幂等成功） |

**失败码口径**

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `EX-IP-ATD-301` | 长期未停 | 长期医嘱需要先停嘱 |
| `EX-IP-ATD-307` | 长期已停嘱仍有待执行时点 | 长期医嘱仍有待执行时点，请先执行或不执行登记 |
| `EX-IP-ATD-302` | 临时未完结 | 存在未执行或未完成的临时医嘱，请先执行 |
| `EX-IP-ATD-303` | 待发/待退药 | 患者有待取的药品，请先取药 |
| `EX-IP-ATD-304` | 状态不允许 | 当前状态不允许办理出院，请刷新 |
| `EX-IP-ATD-305` | 非待出院 | 请先开立并校对出院医嘱 |
| `EX-IP-ATD-306` | 持久化失败 | 出院失败，请刷新后重试 |

**Mapper 方法签名**

- `toExecuteInpatientNurseDischargeCommand(ExecuteInpatientDischargeRequest request, Long operatorId, LocalDateTime dischargedAt): ExecuteInpatientNurseDischargeCommand`
- `toExecuteInpatientDischargeResponse(Long encounterId): ExecuteInpatientDischargeResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | |
| 2 | Gate | App 校验 `encounterId` 非空 | — | |
| 3 | Pre | `mustGetInpatientInfoForDischargeTab` | `encounterId` | 出 Tab 可见；不可见 → **EX-IP-ATD-304** |
| 5 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | |
| 8 | Pre | App 判断幂等 | `encounter.status_enum` | **若** 已 `PENDING`：**跳至步骤 11**（须在步骤 4 之前执行，避免误触发 305） |
| 4 | Pre | App 内联 `assertDischargeHoldReady` | QueryService | **BR-IP-ATD-011④** → **EX-IP-ATD-305** |
| 6 | Pre | App 内联 `assertDischargeGatePassed` | 批量读 | **BR-IP-ATD-011**①～③ → **301/302/303/307**；槽位判定委托 `NurseStationOrderExecuteListReadAssembler` |
| 7 | Pre | App 解析 `dischargedAt` | `listServiceRequestsByEncounter` | `SERVICE_CATEGORY_DISCHARGE` 的 `occurrence_start_time`；无则 `now()` |
| 9 | Pre | App 判断 | `encounter.status_enum` | 非 `ON_HOLD` 且非 `PENDING` → **EX-IP-ATD-304** |
| 10 | Cmd | `EncounterDomainService#executeInpatientNurseDischarge` | `encounterId`、`dischargedAt`、`operatorId` | `ON_HOLD→PENDING`；`HospitalDischargedEvent`（`PENDING`） |
| 11 | Asm | `toExecuteInpatientDischargeResponse` | — | |
| — | — | 投影 | — | 异步；**禁止**同步QueryService |

> **成码顺序说明**：步骤 **8（幂等）须在 4（待出院）之前**，否则已 `PENDING` 患者会因非 `ON_HOLD` 误报 305。表内序号保留 AS 业务语义编号，实现按上表「阶段」列执行。

> 领域 `executeInpatientNurseDischarge` 对非 `ON_HOLD` 抛 `ENCOUNTER_INVALID_STATUS`：App **须**在步骤 8～9 先行门禁，映射 **EX-IP-ATD-304/305**，不得裸抛领域码。

**App 事务**：`executeInpatientDischarge` `@Transactional` 仅包裹步骤 10。

### 3.7 IF-IP-NUR-ATD-06 `clearDischargedBed`（清床离院）

**HTTP**：`POST /api/app/nurseStationATDManager/clearDischargedBed`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 已结算占床患者 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 id |
| `succeeded` | `Boolean` | 是否成功 |

**失败码口径**

| 错误码 | 场景 | 用户提示 |
| --- | --- | --- |
| `EX-IP-ATD-401` | 非 `COMPLETED` | 请等待出院结算完成后再清床 |
| `EX-IP-ATD-403` | 展示账户余额非零 | 患者账户余额须为零后才能清床 |
| `EX-IP-ATD-404` | 存在未冲销清床汇总 3505 | 上次清床汇总销售尚未冲销成功，请完成冲销或联系运维后再清床 |
| `EX-IP-ATD-402` | 活动床≠1 或释床失败 | 清床失败，请检查病床状态 |

**Mapper 方法签名**

- `toClearDischargedBedCommand(ClearDischargedBedRequest request, Long operatorId, Long bedId, LocalDateTime clearedAt): ClearDischargedBedCommand`
- `toClearDischargedBedResponse(Long encounterId): ClearDischargedBedResponse`
- `toReleaseBedCommand(Long bedLocationId, Long encounterId, Long operatorId): ReleaseBedCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | |
| 2 | Gate | App 校验 `encounterId` 非空 | — | |
| 3 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | |
| 4 | Pre | App 校验 **BR-IP-ATD-013** | `status_enum` | **须** `COMPLETED`；否则 **EX-IP-ATD-401**（含仅 `PENDING` 未全结） |
| 4a | Gate | `InventoryThirdPartyUploadExtension#hasUnreversedClearBedSummarySales` | `encounterId` | **BR-IP-ATD-020**；未冲销 → **EX-IP-ATD-404** |
| 4b | Pre | App 校验 **BR-IP-ATD-016** | `Account` + `ChargeItem` 汇总 | `InpatientDisplayAccountBalanceAssembler#computeDisplayAccountBalance` **须为 0**；否则 **EX-IP-ATD-403** |
| 5 | Pre | `EncounterQueryService#requireActiveLocationId`（`BED`） | `encounterId` | 解析唯一活动床 `bedId`；失败 → **EX-IP-ATD-402** |
| 6 | Cmd | `EncounterDomainService#clearDischargedBed(ClearDischargedBedCommand)` | `encounterId`、`bedId`、`clearedAt`、`operatorId` | **第一写库点**：收口活动 `adm_encounter_location` / `adm_encounter_participant`；`transitToDischarged`；发布 `HospitalDischargedEvent`（`DISCHARGED`）、`TerminalCleaningCompletedEvent` |
| 7 | Cmd | `LocationDomainService#releaseBed(ReleaseBedCommand)` | 步骤 5 的 `bedId` | **第二写库点**：`operational_enum→NOT_IN_USE`；**须在步骤 6 成功后** |
| 7b | Asm | afterCommit → `uploadAfterDischargeSummarySales` | `encounterId` | 异步清床汇总 3505 |
| 8 | Asm | Response | — | |
| — | — | 投影 | — | 异步；患者自护士站列表消失 |

**App 事务**：`clearDischargedBed` `@Transactional` **必须**包裹步骤 6～7（与 AS §7 一致）。

### 3.7a IF-IP-NUR-ATD-06a `listMissingDispenseTraceTips`（缺追溯码软提示）

**HTTP**：`POST /api/app/nurseStationATDManager/listMissingDispenseTraceTips`

**口径**：**BR-IP-ATD-021**；只读；**不硬拦**清床。前端清床前调用，`tips` 非空则确认框展示 `tipText`（发药日+药名+批号）。

### 3.8 ~~IF-IP-NUR-ATD-07~~ 出院召回（**已废止 · 2026-07-16**）

> **写契约已迁至** **`AS-IP-FIN-004-SDD` §3.4 IF-IP-RCL-01** `recallInpatientDischarge`。  
> 原 HTTP `POST /api/app/nurseStationATDManager/recallInpatientDischarge`、`**RecallReverseSettleSupport**`、`**EX-IP-ATD-501～504**` 均不再适用；异常码见 **EX-IP-RCL-001～004**。  
> 召回后目标态：**`DISCHARGED→IN_PROGRESS`** + INSERT 活动 **WARD**（无床）；`requiresBedAssignment=false`。

## 4. QueryService 与事件刷新


| QueryService             | 查询方法                                  | 本 AS 用途      |
| --------------- | ------------------------------------- | ------------ |
| `InpatientEncounterContextQueryModel` | `listInpatientContextsByEncounterIdsOrdered` | ATD-01 占床患者块 |
| `InpatientEncounterContextQueryModel` | `mustGetInpatientContextByEncounterId` | ATD-03～06 出 Tab 门禁与展示 |


**QueryService 查询语义（最终一致）**


| 领域事件                                    | QueryService             | 更新动作                         |
| --------------------------------------- | --------------- | ---------------------------- |
| `InpatientBedAssignedEvent`             | `InpatientEncounterContextQueryModel` | `status_enum`、`bed_id`/`bed_name`（**不**改 `ward_id`；**BR-IPI-004**） |
| `VitalSignSavedEvent`                   | —（001 详情 App 读 `dia_vital_sign`；体温单/护理记录 AS） | 001 `admissionVitalSigns` 展示 |
| `BedTransferredEvent`                   | `InpatientEncounterContextQueryModel` | `bed_id`←`toLocationId`（**不**改病区） |
| `BedOccupiedEvent` / `BedReleasedEvent` | —（Location 主数据） | ATD-01 `operational_enum` 展示 |
| `HospitalDischargedEvent` | `InpatientEncounterContextQueryModel` | ATD-05：`status_enum→PENDING`，`end_time`；ATD-06：`status_enum→DISCHARGED`，清空床字段（§4.2） |
| `TerminalCleaningCompletedEvent` | `InpatientEncounterContextQueryModel` | 清床后床位/在院展示收口；001 列表移除该患 |
| `InpatientFinancialSettlementCompletedEvent` | `InpatientEncounterContextQueryModel` | **AS-IP-FIN-003** 写入 `COMPLETED`；清床前置态 |
| `InpatientDischargeRecalledEvent` | **FIN-004 IF-IP-RCL-01** | `status_enum→IN_PROGRESS`；`requiresBedAssignment=false` |
| `ServiceRequestRejectedToDraftEvent` | **FIN-004** 召回/反结 | 医嘱列表 |


字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §4.2 为唯一真源；本文不重复 §4.2 细表。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览


| 分支           | App 方法                | 领域 / 资源写入顺序                                                                                                                                                                                                  |
| ------------ | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **ASSIGN**   | `executeBedOperation` | ① `adm_encounter` 状态 `REGISTERED→IN_PROGRESS` ② `adm_encounter_location`：WARD 保留；HOUSE+BED 旧行 COMPLETED+INSERT ③ `adm_encounter_participant` 全量 COMPLETED+INSERT ④ `adm_location` 目标床 `occupyBed` → `IN_USE` ⑤（可选）`dia_vital_sign` 时点 upsert（**BR-IP-ATD-020～024**） |
| **TRANSFER** | 同上                    | ① `adm_encounter_location`：仅 HOUSE+BED 收口+INSERT ② 原床 `releaseBed` ③ 新床 `occupyBed`                                                                                                                          |
| **SWAP**     | 同上                    | ① 双方 `adm_encounter_location` HOUSE+BED 交叉 INSERT ② `swapBedsOperational`（双床行锁、release + 交叉 occupy 单次落库）                                                                                                                      |
| **ATD-05** | `executeInpatientDischarge` | ① `adm_encounter`：`ON_HOLD→PENDING`，`end_time` |
| **ATD-06** | `clearDischargedBed` | ① 活动 `adm_encounter_location` / `adm_encounter_participant` COMPLETED ② `adm_encounter` → `DISCHARGED` ③ `adm_location` `releaseBed` → `NOT_IN_USE` |


### 5.2 事务边界 / 失败回滚


| IF               | 事务策略                                                                          | 失败回滚                    |
| ---------------- | ----------------------------------------------------------------------------- | ----------------------- |
| IF-IP-NUR-ATD-02 | **App** `executeBedOperation` `@Transactional` 包含 Encounter Cmd + Location 床态 +（ASSIGN 可选）VitalSign Cmd | 全回滚 → **EX-IP-ATD-202** |
| IF-IP-NUR-ATD-05 | **App** `executeInpatientDischarge` `@Transactional` 仅 `executeInpatientNurseDischarge` | 全回滚 → **EX-IP-ATD-306** |
| IF-IP-NUR-ATD-06 | **App** `clearDischargedBed` `@Transactional`：`clearDischargedBed` + `releaseBed` | 全回滚 → **EX-IP-ATD-402** |
| IF-IP-NUR-ATD-01/03/04 | 无写事务                                                                          | —                       |


> **顺序约束**：Encounter 轨迹先于床态变更（与 AS §4 一致）；避免「床已占但就诊未落床」的中间态对外可见——同事务内仍须按上表序号调用。

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 承诺      | 代码位置（必须）                                                                                                   | 关联步骤 |
| ------- | ---------------------------------------------------------------------------------------------------------- | ---- |
| 读接口天然幂等 | `listBedBoard` / `listDischargePendingExecutions` / `listDischargePendingMedications` 无副作用 | §3.2、§3.4、§3.5 |
| 办理出院幂等 | `executeInpatientDischarge`：已 `PENDING` 时跳过 Cmd，返回 `succeeded=true`（§3.6 步骤 8） | §3.6 |
| 分床写非幂等  | **BR-IP-ATD-006**：依赖活动床 + 床态断言 | §3.3 |


### 6.2 并发控制


| 承诺                        | 代码位置（必须）                                                            | 关联步骤                     |
| ------------------------- | ------------------------------------------------------------------- | ------------------------ |
| 同床并发分床拒绝                  | `occupyBed`：`PESSIMISTIC_WRITE` + `assertVacantForOccupy` + `Location` `@Version`；`OptimisticLockingFailureException` → **EX-IP-ATD-201**；App Pre `NOT_IN_USE` 为软 Gate | §3.3 → **EX-IP-ATD-201**（GAP-ATD-001/002） |
| 出院/清床并发拒绝              | `executeInpatientNurseDischarge` / `clearDischargedBed`：`requireEncounterForUpdate` + 锁内断言；`Encounter` `@Version`；`OptimisticLockingFailureException` → **304/402** | §3.6～3.7（GAP-ATD-005） |
| `fromLocationId` 与库内活动床一致 | `ConfirmBedTransferCommand.fromLocationId`                          | §3.3.2 序号 5、7            |


### 6.3 重试与容错


| 承诺                    | 代码位置（必须）              | 关联步骤 |
| --------------------- | --------------------- | ---- |
| 读失败 **EX-IP-ATD-101/103/104** | 各读 IF | §3.2、§3.4、§3.5 |
| 写失败 **EX-IP-ATD-202/306/402** | 各写 IF | §3.3、§3.6、§3.7 |


### 6.4 审计日志


| 承诺             | 代码位置（必须）                                            | 关联步骤      |
| -------------- | --------------------------------------------------- | --------- |
| 操作人写入各 Command | `operatorId` on Assign/Transfer/Swap/Occupy/Release/Discharge/ClearBed | §3.3、§3.6、§3.7 序号 1 |


### 6.5 数据脱敏


| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 与 001 一致 | 本 AS 不新增 PHI 字段；患者姓名等待办 DTO 仅面向已授权护士站会话 | 全 IF |


## 7. 测试要点

### 7.1 功能测试


| 用例                 | 预期                                                 | 优先级 |
| ------------------ | -------------------------------------------------- | --- |
| 选病区返回全部床位          | 含 `IN_USE` 与 `NOT_IN_USE`；占床格有 occupant            | P0  |
| 选病房窄于病区            | 仅该病房床                                              | P0  |
| 空床 occupant 为 null | 仅位置块；**且** `operationalEnum=NOT_IN_USE`；不得因活动 `adm_encounter_location` 展示患者块 | P0  |
| `NOT_IN_USE` 仍有活动位置 | 日志告警；occupant 仍为 null | P1  |
| 首次分床               | `REGISTERED→IN_PROGRESS`；HOUSE+BED 轨迹；目标床 `IN_USE`；可选写入院体征 | P0  |
| 入院体征 ASSIGN        | 部分项落库；`recorded_at=start_time`；换床/互换不改体征 | P0  |
| 换空床                | 原床 `NOT_IN_USE`、新床 `IN_USE`；参与者不变                  | P0  |
| 互换                 | 双方床号对调；WARD 仍为活动                                   | P0  |
| 目标占床床分床/换床         | **EX-IP-ATD-201**                                  | P0  |
| 办理前只调 001 详情       | 入科 Tab；出 Tab 可调 ATD-03/04                                       | P1  |
| `panel=discharge` 过滤 | `ON_HOLD` 待出院、`PENDING`、`COMPLETED` 可见；`DISCHARGED`/待转科不可见 | P0 |
| 待执行/待发药列表 | 仅展示阻塞项；处理后 ATD-05 可通过 | P0 |
| 出院门禁 | 301/302/303 拦截；通过后 `→PENDING` | P0 |
| 清床 | 仅 `COMPLETED` 且展示余额为 0；成功后床空、`DISCHARGED` | P0 |
| 链路 | 003 校对 → ATD-05 出院 → FIN-003 全结 → ATD-06 清床 | P0 |
| 未清床反结 | `COMPLETED` 占床 → 收银台 **FIN-004 反结** | P0 |
| ~~护士站召回~~ | 已废止；见 **FIN-004 IF-IP-RCL-01**（收银台） | — |


### 7.2 非功能测试


| 用例          | 预期                                                | 优先级 |
| ----------- | ------------------------------------------------- | --- |
| 床位列表反查无 N+1 | 单次 `mapActiveBedEncounterIdByLocationIds` + 批量QueryService | P0  |
| ATD-02 同事务  | Encounter+Location 同成功或同回滚                        | P0  |
| ATD-06 同事务  | 清床 + `releaseBed` 同成功或同回滚 | P0 |
| ATD-03/04 无 N+1 | 就诊维度批量查询 + `findAllByIds` | P1 |


### 7.3 QueryService一致性


| 用例                      | 预期      | 优先级 |
| ----------------------- | ------- | --- |
| 分床后 001 列表与 ATD-01 床格一致 | QueryService 刷新延迟可接受 | P1  |
| 出院/清床后出 Tab 与 001 一致 | `PENDING`/`COMPLETED` 仍占床；清床后消失 | P0 |


### 7.4 集成测试


| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| POST `listBedBoard` | `POST /api/app/nurseStationATDManager/listBedBoard` | P0 |
| POST `executeBedOperation` | `POST /api/app/nurseStationATDManager/executeBedOperation` | P0 |
| POST `listDischargePendingExecutions` | 路径与方法名一致 | P0 |
| POST `listDischargePendingMedications` | 路径与方法名一致 | P0 |
| POST `executeInpatientDischarge` | 路径与方法名一致 | P0 |
| POST `clearDischargedBed` | 路径与方法名一致 | P0 |

> ~~POST `recallInpatientDischarge`~~ 已废止；召回见 **FIN-004** IF-IP-RCL-01。


### 7.5 测试缺口跟踪


| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 集成测试待执行 | 应用层 ATD-01～06；召回 E2E 见 **FIN-004-SDD §7** | P1 |


## 8. 变更历史


| 版本   | 日期         | 说明                                                                                                                    |
| ---- | ---------- | --------------------------------------------------------------------------------------------------------------------- |
| v2.21 | 2026-09-23 | **BR-IP-ATD-011①′**：交叉 **AS-IP-NUR-004 v2.60**——预计领用日补点认不执行终态，禁止假 pending 误拦出院 |
| v2.20 | 2026-09-14 | **BR-IP-ATD-011①′**：`listLongTermStoppedPendingPlanTimes` 叠加 **BR-IP-EXE-038**（含拆零下限 MIN_SPLIT）抑制；对齐 **AS v2.22** / **AS-IP-NUR-004 v2.57** |
| v2.19 | 2026-09-11 | **ATD-03**：`DischargePendingExecutionItemDto.pendingPlanTimes`；步骤 8 改 `listLongTermStoppedPendingPlanTimes`（与门禁 / **BR-IP-EXE-035⑤** 硬顶空收口同源）；对齐 **AS v2.21** |
| v2.18 | 2026-07-16 | **出院召回 redesign**：废止 **IF-IP-NUR-ATD-07** / **`RecallReverseSettleSupport`**；§3.8 指向 **FIN-004 IF-IP-RCL-01**；ATD-01～06 不变；对齐 **AS v2.19** |
| v2.17 | 2026-07-16 | **BR-IP-ORD-028④**：ATD-03 诊疗行 `displayName` 优先 `entrust_content`；`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`；对齐 **AS-IP-NUR-003-SDD v1.3.4** |
| v2.16 | 2026-07-11 | **BR-IP-ATD-011①′ 长期已停嘱待执行时点**：ATD-03/05 纳入 `LONG_PENDING_SLOTS` / **EX-IP-ATD-307**；槽位判定与 **Q-IP-EXE-001** 同源（`NurseStationOrderExecuteListReadAssembler`） |
| v2.15 | 2026-07-11 | **ATD-01 占床块装载修正**：步骤 6 改 `listInpatientContextsByEncounterIdsOrdered`；禁止 `listInpatientEncounterContexts`+`encounterIdsIn`（登记列表默认状态过滤漏 `ON_HOLD` 等待出院占床）；§2.2/§4 同步 |
| v2.14 | 2026-06-19 | **DM-DIA-002 v2.0 对齐**：入院体征改 `saveVitalSign`；体温写主表；读侧 `listHeadersByEncounterId`+`getWithDetailsById`；§2.3/§3.3.1 步骤 9～10 更新 |
| v2.13 | 2026-06-19 | **成码对齐**：`AdmissionVitalSignSaveContext`；Support 方法 `toSubmitDto`/`saveIfPresent`；`VitalSignAdmissionItemCodeConstants`；ASSIGN 无 `start_time` 跳过体征写；前端 ASSIGN 体征仅随 ATD-02 |
| v2.12 | 2026-06-19 | **入院体征（ATD-02 ASSIGN）**：`AdmissionVitalSignSubmitDto`、`AdmissionVitalSignsupport；§3.3.1 步骤 9～10；`VitalSignDomainService`/`QueryService` 白名单；§5.1 ASSIGN 写序；对齐 **AS v2.12** / **DM-DIA-002** |
| v2.11 | 2026-06-18 | **ATD-07 成码对齐**：`RecallReverseSettlesupport 内嵌反结；Pre 共用 `InpatientReverseSettlePresupport；§2/§3.8/§2.2 与代码一致 |
| v2.10 | 2026-06-18 | **管理病区硬权限**：ATD-01 默认地理范围与 **001** 同源；§2.2 删除 App 层 `listAllActiveWards` 回落；§3.2 编程序列同步 **AS-IP-NUR-001 v1.15** |
| v2.9 | 2026-06-18 | **召回口径修正（对齐 AS v2.9）**：仅 `DISCHARGED`；ATD-07 **内嵌 FIN-004 反结**；撤销出院嘱移出 ATD AppService 直接装配；§3.8/§5/§7 测试同步 |
| v2.8 | 2026-06-18 | **定稿对齐成码**：§1.1 ATD-07 应用层已成码；§3.8 反结改为收银台业务前置（**v2.9 废止**） |
| v2.7 | 2026-06-18 | **IF-IP-NUR-ATD-07** 出院召回：反结前置、`rejectServiceRequestToDraft`、分轨 `recallInpatientDischarge`；**EX-IP-ATD-501～504**；§5 写库顺序与事务 |
| v2.6 | 2026-06-17 | **BR-IP-ATD-011② 临时净态对齐 BR-IP-EXE-006/008**：取消执行（CANCEL）及 COMPLETED 已被对冲不再误判为「临时未完结」；ATD-03/05 按 request 分组 Procedure 净态判定 |
| v2.5 | 2026-06-17 | **ATD-01 空床口径对齐成码**：**BR-IP-ATD-001/009** — 空床/占床以 `operational_enum` 为准；批量读模型仅 `IN_USE` 床；`NOT_IN_USE`+活动位置 warn；§3.2 编排序列与 §7.1 验收更新 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `EncounterContextFacade`；`listInpatientEncounterContexts` / `mustGetInpatientContextByEncounterId`；§3/§4 与 `zxplat` 一致 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-20 | 首版：对齐 **AS-IP-NUR-002 v1.5** 与已落地领域命令；扩展现有 `InpatientNurseStationAppService`；登记 SDD-GAP-001～004（床位反查、活动床查询、常量、App 成码） |
| v1.1 | 2026-05-20 | §2：应用分包改为 **`encountermanage` + `ATDManager`**（对齐门诊护士站）；废弃 flat 根包与误写 `admissiontransfer`/`patientmanage`             |
| v1.2 | 2026-05-20 | §2.4：撤销 SDD-GAP-003/004；常量与 App/Mapper 归入「成码交付清单」；真缺口仅保留域读 **001～002** |
| v1.3 | 2026-05-20 | 闭合 **SDD-GAP-001～002**：`EncounterLocationRepository` + `EncounterQueryService` 床位反查与活动床/病房解析；同步 `DM-ADM-002` §6.2.12～6.2.14 |
| v1.4 | 2026-05-20 | **Gap 收口**：§2.4 改为「领域层能力核对」；删除 SDD-GAP 表与正文引用；对齐 AS/SDD Skill |
| v1.5 | 2026-05-20 | **HTTP 与分包对齐**：独立 `InpatientNurseStationATDManagerAppService`；前缀 `inpatientNurseStationATDManager`；§2 补全 ATDManager 类清单 |
| v1.6 | 2026-05-20 | **短命名对齐 001**：`NurseStationATDManagerAppService` / `NurseStationATDManagerMapper`；HTTP 前缀 `nurseStationATDManager`（废弃 `inpatientNurseStationATDManager`） |
| v1.7 | 2026-05-20 | 应用层成码对齐：`executeBedOperation` `@Transactional`（§5.2）；互换批量 release/occupy；§7.5 测试缺口更新 |
| v1.8 | 2026-05-20 | 读侧收口：`requireActiveLocationId` 替代 bed/house 双方法；写侧 `requireActiveBedId` 委托 QueryService（§2.2、§2.4、DM §6.2.13） |
| v1.9 | 2026-05-20 | §3.2 ATD-01 对齐成码：`listBedBoard` 内联范围收集（废弃 `resolveBedBoardScope`）；列表按 `busNo` 排序；`mapNamesByIds` 批量 `houseName`；`toBedBoardLocationBlock(bed, houseName)` |
| v2.0 | 2026-05-24 | §4.2 QueryService 查询语义：`InpatientBedAssignedEvent` 仅更新床位+状态、保留登记/申请 `ward_id`；对齐QueryService **v3.6** / 领域事件 **v1.14** / **BR-IPI-004** |
| v2.1 | 2026-05-26 | **对齐 AS v2.1**：IF ATD-01 `panel`、ATD-03～06（待执行/待发药读、办理出院、清床）；§2 白名单与 `InpatientNurseStationConstants` 扩展；§3.4～3.7 编排序列；§4～7 出院链路；**无 Gap 表** |
| v2.3 | 2026-05-29 | **清床余额门禁**：`EX-IP-ATD-403`、**BR-IP-ATD-016**；§3.7 编排序列步骤 4b；§7.1 验收更新 |
| v2.3 | 2026-06-11 | ATD-02 分床医护下拉交叉引用 **001** `loadAdmissionParticipantInit`（§3.2.1） |
| v2.2 | 2026-05-26 | **对齐成码 v2.2**：§2.5 出 Tab 公共 Pre；ATD-03 Mapper 签名；ATD-05 幂等顺序与实现一致；ATD-04 EnumUtil 展示；应用层全量已落地；§7.5 测试缺口更新 |


