# 住院护士站 · 医嘱校对 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-003-SDD  
> 版本：v1.3.24 | 日期：2026-10-13 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · 医嘱校对 Tab（列表读 + 校对通过/退回写；含**停嘱校对 / 取消停嘱校对**分支） |
| 实现阶段口径 | **目标态全量契约**（IF-IP-AUD-01～03）；**领域写命令与 Q-IP-AUD-001 读查询**已落地（含 `verifyStop*` / `cancelVerifyStop*`）；**应用层**落包 `inpatient/nursestation/orderaudit`，AppService **`NurseStationOrderAuditAppService`**，HTTP 前缀 **`nurseStationOrderAudit`** |
| 编排权威 | `AS-IP-NUR-003-住院护士站医嘱校对.md`（**v1.25**）§1.3、§4 |
| 方法权威 | `DM-MED-003` §6.1.6d～e / §6.1.7～8；`DM-WKF-004` §6.1.8c～d / §6.1.6～7；`DM-ADM-002` §6.1.17～18；`ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3.4 **Q-IP-AUD-001** |
| 严格对齐 | IF 编号 **IF-IP-AUD-01、IF-IP-AUD-02、IF-IP-AUD-03**，不得增删改号 |
| 读侧方案 | **方案 A**：`MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationAuditOrders` 骨架 + 域 QueryService 补全明细；**禁止** App 注入QueryService Repository |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 左侧患者树/列表/详情 | **AS-IP-NUR-001** |
| 医嘱执行 Tab | **AS-IP-NUR-004** |
| 账户余额/费别/绑定耗材 | 列表不返回（**BR-IP-AUD-007**） |
| 分页 | 按左侧多选患者全量返回 |
| 已校对 Tab 批量退回 | UI「已校对」Tab 仅开放**退回**（校对通过仍仅「未校对」Tab）；后端 **BR-IP-AUD-013** 门控 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见范围 **BR-IP-NUR-003**；`encounterIds` 来自左侧多选患者 |
| `AS-IP-DOR-003` | 医生开立/签发语义；列表行展示字段与住院医生站列表同口径裁剪 |
| `AS-IP-DOR-006` | 住院中医下游：`medicineCategoryTab`、一方一行、整方写扩行 |
| `AS-IP-NUR-004` / `AS-IP-NUR-005` | 与执行/划价父医嘱列表**共用** `orderview` 行展示装配器（见 §2.5） |
| `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` | **Q-IP-AUD-001** 唯一读入口 |
| `DM §6.2 各 Request QueryService` | 校对/退回事件 → InpatientOrderListSkeleton QueryService 查询语义 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 code | 持久化与比较一律 **String `code`**（`RequestStatus`、`EncounterStatus` 等） |
| 校对态 | 开嘱校对（药/诊）统一 **`VERIFIED`**；**禁止**与门诊收费 **`COMPLETED`** 混用语义 |
| 停嘱校对 | 字段对 `stop_performer_check_id` + `stop_check_time`；只写/清两字段，**不改** `STOPPED`；临时无停嘱校对（**BR-IP-AUD-008/009/014**）；**不**自动 skip pending |
| auditTab | `pending` / `passed` / `stopped` / `suspended` → 映射 **BR-IP-AUD-003**（`pending` 含停嘱待校） |
| therapyType | App 白名单 `all` / `long` / `temp`；`long`→`LONG_TERM`，`temp`→`TEMPORARY`，`all` 不传读侧 |
| medicineCategoryTab | `west`（默认）/ `tcm`；`tcm` Tab 仅 `tcm_flag=true` 药嘱，**不展示** SERVICE_REQUEST（**BR-IP-NUR-003-TCM-001**） |
| orderType | 写接口锚点：`MED_REQUEST` / `SERVICE_REQUEST`（`QueryServiceOrderTypeConstants`） |
| 校对人/时间 | 写操作取当前登录 **Practitioner** + 服务端 **`LocalDateTime.now()`**；**禁止** Request 传入 |
| 转科/出院类目 | 库表 `category_enum` 持久化为 **`"32"`（转科）** / **`"34"`（出院）**（`DM-WKF-004` §4.3）；编排分支按此两码判定 |
| HTTP | 一律 **`POST`** + `AppServiceControllerRegistrar`；Bean **`NurseStationOrderAuditAppService`** → 前缀 **`nurseStationOrderAudit`** |
| 批量上限 | 写接口单批 **≤50** 条（**AS §7 NFR**） |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  orderaudit/
    service/
      NurseStationOrderAuditAppService.java
    request/
      ListNurseStationOrderAuditRequest.java
      VerifyNurseStationOrdersRequest.java
      RejectNurseStationOrdersRequest.java
    response/
      ListNurseStationOrderAuditResponse.java
      VerifyNurseStationOrdersResponse.java
      RejectNurseStationOrdersResponse.java
    dto/
      NurseStationOrderAuditPatientGroupDto.java
      NurseStationOrderAuditRowDto.java
      NurseStationOrderAuditWriteItemDto.java
    assembler/
      NurseStationOrderAuditListAssembler.java
    mapper/
      NurseStationOrderAuditMapper.java
      NurseStationOrderAuditMapperImpl.java

zxplat/health-application/.../application/inpatient/nursestation/{feature}/support/
  InpatientNurseStationTcmSupport.java
  NurseStationOrderViewListAssembler.java
  dto/NurseStationOrderView*.java
zxplat/health-application/.../application/shared/advice/
  InpatientOrderLookupLoader.java
  dto/InpatientOrderListSkeleton.java
zxplat/health-module-support/.../module/common/assembler/order/
  InpatientOrderListMergeAssembler.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  InpatientNurseStationConstants.java
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.orderaudit`  
> AppService Bean 名 → HTTP 前缀：`nurseStationOrderAudit`（与 001 `nurseStationEncounterManage`、002 `nurseStationATDManager` 同短命名规则）

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentPractitionerResolver` | 写路径校对人 `performerCheckId`（**B.6.5**） |
| `CurrentOperatorResolver` | 写路径 **operatorId**（**B.6.5**） |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty` → 管理病区范围（**BR-IP-NUR-003**） |
| `EncounterContextReader` | `listInpatientEncounterContexts`（AUD-01/02/03 可见 encounter 收窄；**BR-IP-AUD-012**） |
| `InpatientOrderListMergeAssembler` | `mergeNurseStationAuditOrders` → `InpatientOrderListSkeleton`（AUD-01） |
| `InpatientOrderLookupLoader` | 校对列表骨架加载（经 MergeAssembler；**Q-IP-AUD-001**） |
| `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService` | **Q-IP-AUD-001** 域明细（AUD-01） |
| `MedicationRequestQueryService` | 按就诊批量补全药品明细（AUD-01 Asm） |
| `ServiceRequestQueryService` | 按就诊批量补全诊疗明细；写前 reload 类目（AUD-01/02） |
| `EncounterHistoryQueryService` | 按里程碑解析转科目标科室、出院方式（AUD-02） |
| `MedicationRequestDomainService` | `verifyMedicationOrder` / `verifyStopMedicationOrder` / `rejectMedicationOrder` / `cancelVerifyStopMedicationOrder`（AUD-02/03） |
| `ServiceRequestDomainService` | `verifyServiceRequest` / `verifyStopServiceRequest` / `rejectServiceRequestToDraft` / `cancelVerifyStopServiceRequest`（AUD-02/03） |
| `EncounterDomainService` | `initiateDepartmentTransfer` / `dischargeInpatientEncounter`（AUD-02） |
| `NurseStationOrderViewListAssembler` | 共享：明细索引、批量预热、药/诊行 `DisplayCore`（§2.5） |
| `NurseStationOrderAuditListAssembler` | InpatientOrderListSkeleton + 域明细 → 委托 `OrderView` 展示内核 → 校对行 DTO + 分组排序（AUD-01 Asm） |
| `NurseStationOrderAuditMapper` | Request/Command/DTO 装配、Tab 映射、二次过滤 |
| `InpatientNurseStationTcmsupport | 中西药 Tab 过滤、中药方折叠、写路径整方扩行；西药成组写扩行（**BR-IP-NUR-003-TCM-001～003**；**BR-IP-NUR-003-GRP-001～002**） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty` | AUD-01/02/03 可见范围 |
| `EncounterContextReader` | `listInpatientEncounterContexts`（`encounterIdsIn` + 管理病区） | AUD-01/02/03 |
| `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService` | `listInpatientNurseStationAuditOrders` | AUD-01 |
| `MedicationRequestQueryService` | `listMedicationRequestsByEncounterForDoctorStation` | AUD-01 |
| `ServiceRequestQueryService` | `listServiceRequestsByGenerateSource` | AUD-01 |
| `ServiceRequestQueryService` | `mustGetServiceRequest` | AUD-02 转科/出院分支 |
| `EncounterQueryService` | `mustGetEncounterOfClass` | AUD-02 |
| `EncounterHistoryQueryService` | `listMilestonesByEncounter` | AUD-02 里程碑解析 |
| `MedicationRequestDomainService` | `verifyMedicationOrder` | AUD-02 开嘱校对 |
| `MedicationRequestDomainService` | `verifyStopMedicationOrder` | AUD-02 停嘱校对 |
| `MedicationRequestDomainService` | `rejectMedicationOrder` | AUD-03 开嘱退回 |
| `MedicationRequestDomainService` | `cancelVerifyStopMedicationOrder` | AUD-03 取消停嘱校对 |
| `ServiceRequestDomainService` | `verifyServiceRequest` | AUD-02 开嘱校对 |
| `ServiceRequestDomainService` | `verifyStopServiceRequest` | AUD-02 停嘱校对 |
| `ServiceRequestDomainService` | `rejectServiceRequestToDraft` | AUD-03 开嘱退回 |
| `ServiceRequestDomainService` | `cancelVerifyStopServiceRequest` | AUD-03 取消停嘱校对 |
| `EncounterDomainService` | `initiateDepartmentTransfer` | AUD-02 转科 |
| `EncounterDomainService` | `dischargeInpatientEncounter` | AUD-02 出院 |
| `InpatientAdviceRevokeGate` | `assertMedicationWithdrawAllowed` / `assertServiceRequestWithdrawAllowed` | AUD-03 **BR-IP-AUD-013**（发药净额对冲） |
| `InpatientTransferDischargeHoldOrchestrationsupport | `tryRevertHoldAfterServiceRequestReverted` | AUD-03 转科/出院逆向 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 请求状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-MED-003` §2.3 / `DM-WKF-004` §2.3 | Tab 过滤与写前门禁：`ACTIVE`/`VERIFIED`/`STOPPED`/`CANCELLED`/`DRAFT` |
| 长临嘱 | `com.zxplat.domain.share.enums.TherapyType` | `DM-MED-003` §3 `therapy_enum` | `LONG_TERM` / `TEMPORARY` |
| 医嘱来源 | `com.zxplat.domain.share.enums.GenerateSource` | `DM-MED-003` §4.5 | 固定 `DOCTOR_PRESCRIPTION.getCode()` → `doctor-prescription` |
| QueryService医嘱类型 | `com.zxplat.domain.share.constants.QueryServiceOrderTypeConstants` | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §2 `order_type` | `MED_REQUEST` / `SERVICE_REQUEST` |
| 诊疗类目（转科/出院） | `InpatientNurseStationConstants.SERVICE_CATEGORY_TRANSFER` / `SERVICE_CATEGORY_DISCHARGE` | `DM-WKF-004` §4.3 | 持久化 **`"32"`** / **`"34"`**（目标态待生成） |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §5 | 转科/出院编排要求 `IN_PROGRESS`；已 `ON_HOLD` 时幂等跳过 |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3 | 住院 **`IMP`** |
| auditTab | `ListNurseStationOrderAuditRequest.auditTab` | `AS-IP-NUR-003` **BR-IP-AUD-003** | App 白名单四值 |
| therapyType | `ListNurseStationOrderAuditRequest.therapyType` | `AS-IP-NUR-003` **BR-IP-AUD-004** | App 白名单三值 |
| 写锚点 orderType | `NurseStationOrderAuditWriteItemDto.orderType` | `AS-IP-NUR-003` §1.3 | 与 `QueryServiceOrderTypeConstants` 一致 |
| 业务消息码 | `InpatientNurseStationConstants.EX_IP_AUD_*` | `AS-IP-NUR-003` §5 | **101～302**（目标态待生成） |

### 2.4 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-NUR-003` §3**（均已关闭）；本 SDD **禁止**维护 Gap 表。应用层 `AppService`/DTO/Mapper/Assembler 由本 SDD 驱动生成，**不是**缺口。

**已落地读能力**：`MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationAuditOrders`（**Q-IP-AUD-001**）；`MedicationRequestDomainService#verifyMedicationOrder` / `#rejectMedicationOrder`；`ServiceRequestDomainService#verifyServiceRequest` / `#rejectServiceRequestToDraft`；`EncounterDomainService#initiateDepartmentTransfer` / `#dischargeInpatientEncounter`。

**本 SDD 成码交付**：`InpatientNurseStationConstants` 增补 `EX_IP_AUD_*`、`SERVICE_CATEGORY_*`；`orderview` 共享装配器（§2.5）；`NurseStationOrderAuditAppService` 三接口及配套 Request/Response/DTO/Mapper/Assembler。

### 2.5 共享医嘱查看装配器（`orderview`）

> **包路径**：`com.zxplat.domain.application.inpatient.nursestation.orderview`  
> **定位**：住院护士站多 Tab **列表行展示字段**的内核复用层；**无**独立 AppService / HTTP。

| 类 | 职责 |
| --- | --- |
| `NurseStationOrderViewListAssembler` | 第一遍明细索引、批量预热、药/诊 `DisplayCore` 组装 |
| `NurseStationOrderViewListDetailIndex` | `indexEncounterDetails` 产出：`medDetailById` / `svcDetailById` 及批量 id 集合 |
| `NurseStationOrderViewDisplayContext` | 术语、药/诊定义、产品、人员、库位名称预热上下文 |
| `NurseStationOrderViewDisplayCore` | 行展示内核（**非 HTTP 契约**；展示列与 §3.2 `NurseStationOrderAuditRowDto` 同构；含 **`performerCheckOrgDisplay`**（执行科室，**BR-IP-EXE-029**）；`groupId` 由 `buildMedicationDisplayCore` 自 `MedicationRequestListQueryModel` 写入后拷贝至各消费方行 DTO） |

**`NurseStationOrderViewListAssembler` 公共方法**

| 方法 | 用途 |
| --- | --- |
| `indexEncounterDetails(skeleton, medByEncounter, svcByEncounter)` | 建立明细 Map 并收集预热 id |
| `prepareDisplayContext(index)` | 批量预热后返回 `DisplayContext` |
| `resolveRequestId(order)` / `resolveRequestTable(order)` | InpatientOrderListSkeleton → 域 Request 主键 / 表名 |
| `buildListRowKey(requestId, orderType)` | 稳定行键 |
| `buildMedicationDisplayCore(order, medDetail, ctx)` | 药嘱展示内核 |
| `buildServiceDisplayCore(order, svcDetail, ctx)` | 诊嘱展示内核 |

**消费方与分工**

| 消费 Assembler | 职责边界 |
| --- | --- |
| `NurseStationOrderAuditListAssembler` | 调用 `OrderView` 三步（index → context → buildCore）→ `applyViewCoreToAuditRow` 拷贝至 `NurseStationOrderAuditRowDto`（**含药嘱 `groupId`**）→ 组内排序 + encounter 分组 |
| `NurseStationOrderExecuteListAssembler` | 同上展示内核 → 再叠加槽位四类列表 + `bindItems`（见 **AS-IP-NUR-004-SDD** §2.5） |
| `NurseStationInpatientBillingListAssembler` | **AS-IP-NUR-005** IF-IP-PRC-03：同上展示内核 → 拷贝至 `NurseStationSupplementParentOrderRowDto`（**含药嘱 `groupId`**）；**禁止**重复实现展示字段 |

**硬约束**

- QueryService骨架与各 IF 过滤仍由各业务 `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService` 场景方法承担；本装配器**不改变** IF 契约与 Gate 规则。
- 展示逻辑**禁止**在 `orderaudit` / `orderexecute` 等业务包内复制一份；新增护士站「父医嘱/医嘱行」列表须注入 `NurseStationOrderViewListAssembler`。
- **嘱托诊疗行**（**BR-IP-ORD-028④**）：`buildServiceDisplayCore` 末尾须经 **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`** 解析 `adviceDisplayName`——识别 `category_enum=52` 或目录名「嘱托」时**优先** `entrust_content` 正文，**禁止**列表只显示目录名「嘱托」；与住院医生站 **`InpatientDoctorStationAdviceListAssembler#buildServiceSummary`** 同源。
- **出院诊疗行**（**BR-IP-ORD-017** / **BR-IP-AUD-006**）：`prepareDisplayContext` 经 `EncounterHistoryQueryService#listMilestonesByEncounter` 索引出院 `ServiceRequest` → `out_way_code`；`resolveSpecialServiceAdviceDisplayName` 在 code=`5` 时 `adviceDisplayName`=「死亡出院」，否则「出院医嘱」。校对/执行/划价父医嘱列表共用本装配器，禁止各包另写展示名。
- **类型列**（**BR-IP-AUD-006** / **BR-IP-ORD-028**）：`resolveOrderKindDisplay` 按目录类目返回检验/检查/治疗/手术/麻醉等，**禁止**一律回落「诊疗」；术式挂 `procedureDefinitionId` 强制「手术」；`category_enum=52` 须返回「嘱托」；执行列表经 `DisplayCore.orderKindDisplay` 同源下发。
- `DisplayCore` **禁止**直接作为 HTTP Response 字段暴露。
- **`dosageDisplay`**（数量×单位拼接）中单位文案须经 **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`** / **`#resolveDoseUnitDisplayName`**（术语 `UNIT_CODE`/`DOSE_UNIT` 预热 + 产品QueryService `*_dict_text` 回落，与医生站 **BR-ORD-022** 同链）；**禁止**仅拼接 `unitCode`。
- **剂量双单位友好展示（OrderView / `MedicationDosageDisplayAssembler`）**：默认最小（拆零）单位为主、括号附计量单位；**目录 `injectFlag=true`（输液）且换算后拆零量 &lt; 1** 时，`dosageDisplay` / `dailyDoseDisplay` **主显计量单位**（例：`10毫升/次`、`每日10毫升`），**禁止**列表友好文案出现「0.1瓶」类不满整包装小数；结构化 `splitDose*` 仍可下发供编辑回填。校对/执行/划价父医嘱列表同源。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-AUD-01 | `listNurseStationOrderAudit` | 医嘱校对列表查询 | `POST /api/app/nurseStationOrderAudit/listNurseStationOrderAudit` |
| IF-IP-AUD-02 | `verifyNurseStationOrders` | 批量校对通过 | `POST /api/app/nurseStationOrderAudit/verifyNurseStationOrders` |
| IF-IP-AUD-03 | `rejectNurseStationOrders` | 批量退回 | `POST /api/app/nurseStationOrderAudit/rejectNurseStationOrders` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数 / Tab·长临白名单 / 批量条数 ≤50 | 不再调用后续步骤 |
| **Pre** | 可见性、QueryService骨架、明细补全、写前状态与 encounter 门禁 | 抛 **`EX-IP-AUD-*`**，**禁止进入 Cmd** |
| **Cmd** | 域写：药/诊校对或退回；转科/出院 **Encounter** 联动 | 同事务回滚 |
| **Asm** | ListAssembler / Mapper 纯 DTO 装配 | — |

记法：`MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationAuditOrders` 表示注入 Bean 调用。

### 3.2 IF-IP-AUD-01 `listNurseStationOrderAudit`（医嘱校对列表查询）

**HTTP**：`POST /api/app/nurseStationOrderAudit/listNurseStationOrderAudit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterIds` | `List<Long>` | Y | 左侧多选就诊；非空（**BR-IP-AUD-001**） |
| `auditTab` | `String` | Y | `pending` / `passed` / `stopped` / `suspended` |
| `therapyType` | `String` | N | `all` / `long` / `temp`；默认 `all` |
| `medicineCategoryTab` | `String` | N | `west` / `tcm`；默认 `west`（**InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_***） |
| `anesthesiaApplyOnly` | `Boolean` | N | `true`=仅麻醉申请（申请单号 AA 前缀）；`false`/空=排除麻醉申请（**BR-IP-NUR-003-ANES-001**；无类目 25 / 同单号池） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groups` | `List<NurseStationOrderAuditPatientGroupDto>` | 按 `encounterId` 分组；无数据就诊可省略或空 `orders` |

**DTO：`NurseStationOrderAuditPatientGroupDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊主键 |
| `orders` | `List<NurseStationOrderAuditRowDto>` | 该就诊下医嘱行（已排序，**BR-IP-AUD-005**） |

**DTO：`NurseStationOrderAuditRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `listRowKey` | `String` | 稳定行键：`requestId` + U+0001 + `orderType`（与医生站列表同口径） |
| `requestId` | `Long` | 药品/诊疗申请主键 |
| `orderType` | `String` | `MED_REQUEST` / `SERVICE_REQUEST` |
| `encounterId` | `Long` | 就诊主键 |
| `patientId` | `Long` | 患者主键（与QueryService快照一致） |
| `statusEnum` | `String` | 请求状态 code |
| `statusDisplay` | `String` | 状态中文（`RequestStatus` display 回落） |
| `therapyEnum` | `String` | 长临 code |
| `therapyDisplay` | `String` | 长临展示 |
| `adviceDisplayName` | `String` | 医嘱名称摘要（**BR-IP-AUD-006**）；**嘱托行**（`category_enum=52` 或目录名「嘱托」）**优先** `entrust_content` 正文（**BR-IP-ORD-028④**），不得只显示「嘱托」 |
| `specificationText` | `String` | 规格/大小；诊疗可为 null |
| `dosageDisplay` | `String` | 剂量/数量展示（单次剂量×单位或 quantity×unit；**输液且拆零&lt;1 主显计量单位**，见 §2.5） |
| `dailyDoseDisplay` | `String` | 日剂量友好展示（如「每日三片」；长期药；输液拆零&lt;1 时跟计量侧，如「每日10毫升」） |
| `firstDose` | `BigDecimal` | **长期药品**首日/首次用量（`first_dose`；可空；**BR-IP-ORD-030** / **BR-IP-AUD-006**） |
| `firstDoseUnitCode` | `String` | 首日用量单位编码（与 `firstDose` 成对） |
| `firstDoseUnitName` | `String` | 首日用量单位展示名（剂量术语优先，回落开立单位术语/目录字典） |
| `usageDisplay` | `String` | 用法展示（药品） |
| `frequencyDisplay` | `String` | 频次展示 |
| `speedCode` | `String` | 输液滴速编码（仅药嘱；可空） |
| `speedDisplay` | `String` | 输液滴速展示名（术语 `speed_code`） |
| `reqOpenedTime` | `LocalDateTime` | 开立时间（`req_opened_time`） |
| `reqAuthoredTime` | `LocalDateTime` | 签发时间（`req_authored_time`；**仅系统追溯**；列表/报表**默认不展示**；未签发可 null） |
| `stopTime` | `LocalDateTime` | 停嘱时间；未停 null |
| `requesterName` | `String` | 开立医生姓名；UI 跟在开立时间后（**BR-IP-ORD-031**） |
| `stopperId` | `Long` | 停嘱人主键（`stopper_id`；未停 null） |
| `stopperName` | `String` | 停嘱人姓名；UI 跟在停嘱时间后（**BR-IP-ORD-031**） |
| `stopPerformerCheckId` | `Long` | 停嘱校对人主键（`stop_performer_check_id`；未停嘱校对 null） |
| `stopCheckTime` | `LocalDateTime` | 停嘱校对时间（`stop_check_time`） |
| `stopCheckerName` | `String` | 停嘱校对人姓名（`stop_performer_check_id` 解析；**打印同源**） |
| `stopAuditPending` | `Boolean` | 停嘱待校醒目标识：长期 `STOPPED` 且 `stop_performer_check_id` 为空（**BR-IP-AUD-006**） |
| `performerCheckName` | `String` | 开嘱校对人姓名（`performer_check_id` 解析；非 `pending` Tab 前端展示） |
| `performerCheckOrgDisplay` | `String` | 校对科室展示名（共享 `DisplayCore` 字段；**执行 Tab 展示**，校对 Tab 不展示；**BR-IP-EXE-029**） |
| `performerLocationDisplay` | `String` | 执行/发药位置展示（若有；**校对页前端不展示**） |
| `categoryEnum` | `String` | 诊疗类目（转科/出院/嘱托 52 等）；药品行 null |
| `orderKindDisplay` | `String` | 类型列：药品/检验/检查/治疗/手术/麻醉/出院/转科/护理/**嘱托**（按 `category_enum`；术式挂 PD→手术；`52`→嘱托；**禁止**检验等一律回落「诊疗」；**BR-IP-AUD-006**） |
| `groupId` | `Long` | 药品成组号（`med_medication_request.group_id`；与住院医生站列表同源；仅药嘱行有效，未成组/诊嘱为 null） |
| `groupNo` | `Integer` | 药品组号 int 形态（`groupId ≤ Integer.MAX_VALUE` 时与 `groupId` 一致，供 **BR-IP-AUD-005** 排序；诊嘱 null） |
| `sortNumber` | `Integer` | 排序号（药品） |
| `tcmFlag` | `Boolean` | 是否中药（`tcm_flag`） |
| `prescriptionNo` | `String` | 处方号（中药方级） |
| `lineRequestIds` | `List<Long>` | 方内全部 req id（`tcm` Tab 折叠后） |
| `sufferingFlag` | `Boolean` | 代煎标志（方头） |
| `chineseHerbsDoseQuantity` | `BigDecimal` | 付数（方头） |
| `skinTestFlag` | `Boolean` | 是否皮试药（仅药嘱；`skin_test_flag`） |
| `basedOnMedicationRequestId` | `Long` | companion 皮试检查锚定皮试药 id；药品行 null |
| `skinTestOutcomeCode` | `String` | 皮试临床状态 code（仅皮试药且已登记；如 `active`=阳性） |
| `skinTestOutcomeText` | `String` | 皮试临床状态展示（如 阳性/阴性）；前端跟在药名后展示 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-AUD-001** | `encounterIds` 非空；收窄至护士站可见范围 |
| **BR-IP-AUD-002** | 读侧固定医生来源 + 药/诊；App 二次排除纯 `DRAFT`；**禁止**用 `based_on` 排除。住院无负向 Request 主路径；`refund_*_id`/负数量仅为门诊同源防御 |
| **BR-IP-AUD-003** | Tab→状态/停嘱校对痕迹映射：`pending`=`ACTIVE`∪（长期 `STOPPED` 且停嘱校对空）；`stopped`=`STOPPED` 且停嘱校对非空 |
| **BR-IP-AUD-004** | 长临过滤 |
| **BR-IP-AUD-005** | 组内排序 |
| **BR-IP-AUD-006** | 行字段含 `stopAuditPending` / `stopCheckerName` |
| **BR-IP-AUD-007** | 不返回余额/费别/绑定耗材 |
| **BR-IP-AUD-014** | 停嘱校对字段对契约 |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-AUD-101** | 列表查询异常 |
| **EX-IP-AUD-102** | 所选 encounter 均不可见 |

**Mapper 接口方法签名**

- `resolveStatusEnumIn(String auditTab): List<String>`
- `resolveTherapyEnum(String therapyType): String`（`all` 返回 null）
- `filterAuditQueueRows(List<InpatientOrderListSkeleton> skeleton): List<InpatientOrderListSkeleton>`（退药/退诊疗、纯 DRAFT 二次过滤）

**ListAssembler 方法签名**

- `NurseStationOrderAuditListAssembler#assemblePatientGroups(...)` → 内部委托 `NurseStationOrderViewListAssembler#indexEncounterDetails` → `#prepareDisplayContext` → 逐行 `#buildMedicationDisplayCore` / `#buildServiceDisplayCore` → `applyViewCoreToAuditRow`
- `NurseStationOrderViewListAssembler` 公共方法见 §2.5

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `encounterIds` 非空；`auditTab`/`therapyType`/`medicineCategoryTab` 白名单 | 非法 → 参数异常 |
| 2 | Pre | `CurrentManagedWardLocationIdsResolver#listManagedWardLocationIdsOrEmpty` | 当前 Practitioner | 管理病区 id 列表 |
| 3 | Pre | `EncounterQueryService#listInpatientEncounterContexts` | `encounterIdsIn` + 管理病区 | 可见 encounter 集合；**空** → **EX-IP-AUD-102** |
| 4 | Pre | `NurseStationOrderAuditMapper#resolveStatusEnumIn` | `auditTab` | **BR-IP-AUD-003** |
| 5 | Pre | `NurseStationOrderAuditMapper#resolveTherapyEnum` | `therapyType` | **BR-IP-AUD-004** |
| 6 | Pre | `MedicationRequestQueryService / ServiceRequestQueryService / DeviceRequestQueryService#listInpatientNurseStationAuditOrders` | 可见 encounterIds、`statusEnumIn`、`therapyEnum` | **Q-IP-AUD-001**；仅读 |
| 7 | Pre | `NurseStationOrderAuditMapper#filterAuditQueueRows` | ⑥ 结果 | **BR-IP-AUD-002** / **BR-ORD-006** |
| 7a | Pre | `InpatientNurseStationTcmSupport#filterSkeletonByMedicineCategory` + `#filterMedRowsByMedicineCategory` | ⑥ + ⑧ | **BR-IP-NUR-003-TCM-001** |
| 7b | Pre | `InpatientNurseStationAnesthesiaApplyScopeSupport#filterSkeletonByAnesthesiaApplyScope` | 7a + `anesthesiaApplyOnly` | **BR-IP-NUR-003-ANES-001** |
| 8 | Pre | 按可见 encounter **for-each** | `MedicationRequestQueryService#listMedicationRequestsByEncounterForDoctorStation` | 建 `medByEncounter` 索引 |
| 9 | Pre | 按可见 encounter **for-each** | `ServiceRequestQueryService#listServiceRequestsByGenerateSource`（`doctor-prescription`） | 建 `svcByEncounter` 索引 |
| 10 | Asm | `NurseStationOrderAuditListAssembler#assemblePatientGroups` | ⑦⑦a⑦b⑧⑨ + `medicineCategoryTab` | 委托 §2.5；`collapseTcmPrescriptionDisplayRows`（**BR-IP-NUR-003-TCM-002**：代表行开立时间取方内最晚） |
| 11 | Asm | `NurseStationOrderAuditMapper#toListNurseStationOrderAuditResponse` | ⑩ | 响应 |
| — | — | — | — | **仅读路径**；QueryService 最终一致，禁止同步写QueryService |

### 3.3 IF-IP-AUD-02 `verifyNurseStationOrders`（批量校对通过）

**HTTP**：`POST /api/app/nurseStationOrderAudit/verifyNurseStationOrders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<NurseStationOrderAuditWriteItemDto>` | Y | 1～50 条；每条含 `orderType` + `requestId` |

**DTO：`NurseStationOrderAuditWriteItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderType` | `String` | Y | `MED_REQUEST` / `SERVICE_REQUEST` |
| `requestId` | `Long` | Y | 申请主键 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功为 true |
| `processedCount` | `Integer` | 成功处理条数 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-AUD-008** | 双分支：`ACTIVE`→开嘱校对 **VERIFIED**；长期 `STOPPED` 且停嘱校对空→停嘱校对（只写两字段）；**不**自动 skip pending |
| **BR-IP-AUD-010** | 同一 `@Transactional`；整批失败回滚 |
| **BR-IP-AUD-011** | 开嘱校对后诊疗 `category_enum` 为 `"32"`/`"34"` 时编排 Encounter（停嘱校对不联动） |
| **BR-IP-AUD-012** | encounter 须在可见范围 |
| **BR-IP-AUD-014** | 停嘱校对字段对契约 |
| **BR-IP-AUD-017** | 出院开嘱校对前：本就诊**净生效**费用（冲销轧差后净额&gt;0）**客户可见业务时间**不得严格晚于计划出院；失败列项目与时间（**EX-IP-AUD-205**） |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-AUD-201** | 开嘱非 ACTIVE / 停嘱校对分支非法 / 域 assert 失败 |
| **EX-IP-AUD-202** | 就诊不可见 |
| **EX-IP-AUD-203** | 转科/出院 Encounter 联动失败 |
| **EX-IP-AUD-205** | 出院开嘱：费用发生时间晚于计划出院（**BR-IP-AUD-017**） |

**Mapper 接口方法签名**

- `toVerifyMedicationOrderCommand(NurseStationOrderAuditWriteItemDto item, Long operatorId, Long performerCheckId, LocalDateTime checkTime): VerifyMedicationOrderCommand`
- `toVerifyServiceRequestCommand(NurseStationOrderAuditWriteItemDto item, Long operatorId, Long performerCheckId, LocalDateTime checkTime): VerifyServiceRequestCommand`
- `toVerifyStopMedicationOrderCommand(...): VerifyStopMedicationOrderCommand`（`stopPerformerCheckId`/`stopCheckTime`）
- `toVerifyStopServiceRequestCommand(...): VerifyStopServiceRequestCommand`
- `toInitiateDepartmentTransferCommand(Long encounterId, Long fromDepartmentId, Long toDepartmentId, Long operatorId, LocalDateTime initiatedAt): InitiateDepartmentTransferCommand`
- `toDischargeInpatientEncounterCommand(Long encounterId, String outWayCode, LocalDateTime dischargedAt, String summary, Long operatorId): DischargeInpatientEncounterCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `items` 非空且 ≤50（**扩行前**） | **BR-IP-AUD-010** |
| 1a | Pre | `expandAuditWriteItemsByTcmPrescription` + 成组扩行 | 原始 `items` | **BR-IP-NUR-003-TCM-003** / **GRP-002** |
| 2 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | operatorId |
| 3 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 开嘱/停嘱校对人；时间=`now()` |
| 4 | Pre | 可见 encounter 收窄 | 从 items 解析 encounterIds | 不可见 → **EX-IP-AUD-202** |
| 5 | Pre | App 按行判定分支 | `ACTIVE`→开嘱校对；长期 `STOPPED` 且 `stop_performer_check_id` 空→停嘱校对 | 非法 → **EX-IP-AUD-201** |
| 5a | Pre | `DischargeAuditChargeTimeGate#assertNoChargesAfterPlannedDischarge` | 勾选含出院 `ACTIVE`：按就诊扫费用并**冲销轧差**（负向行/净额≤0 跳过）；比对净生效行**客户可见业务时间** vs 计划出院 | **BR-IP-AUD-017**；越界 → **EX-IP-AUD-205**（列项目名+费用时间；整批回滚） |
| 6 | Cmd | **for-each 开嘱药品** `verifyMedicationOrder` / **停嘱药品** `verifyStopMedicationOrder` | 逐条 | 开嘱→**VERIFIED**；停嘱校对只写两字段 |
| 7 | Cmd | **for-each 开嘱诊疗** `verifyServiceRequest` / **停嘱诊疗** `verifyStopServiceRequest` | 一次一条 | 同上 |
| 8 | Pre | **仅开嘱校对诊疗** reload `categoryEnum` | — | 停嘱校对跳过 |
| 9 | Cmd | 转科 `"32"` Encounter 联动（仅开嘱校对） | — | **BR-IP-AUD-011** |
| 10 | Cmd | 出院 `"34"` Encounter 联动（仅开嘱校对） | — | 同上 |
| 11 | Asm | Response | 处理条数 | — |

**转科/出院 Command 载荷解析（步骤 9～10 Pre 输入）**

| 字段 | 转科 `"32"` | 出院 `"34"` |
| --- | --- | --- |
| encounterId | ServiceRequest.encounterId | 同左 |
| 科室/方式 | `EncounterQueryService#mustGetEncounterOfClass` → `organizationId` 为 **from**；`EncounterHistoryQueryService#listMilestonesByEncounter` 中 `requestId` 匹配且 `name=transfer` 的 **`targetOrganizationId`** 为 **to** | 同里程碑列表中 `name=discharge` 且 `requestId` 匹配的 **`outWayCode`** |
| 时间/摘要 | `initiatedAt=now()` | `dischargedAt=ServiceRequest.occurrenceStartTime`（空则 now）；`summary=reasonText` |

> **App 事务**：`verifyNurseStationOrders` 标注 `@Transactional`，包裹步骤 5a～10；任一步失败整体回滚 → **EX-IP-AUD-201/203/205**。

### 3.4 IF-IP-AUD-03 `rejectNurseStationOrders`（批量退回）

**HTTP**：`POST /api/app/nurseStationOrderAudit/rejectNurseStationOrders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<NurseStationOrderAuditWriteItemDto>` | Y | 1～50 条 |
| `reason` | `String` | N | 统一退回原因；写入每条 Command（可空） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 整批成功 |
| `processedCount` | `Integer` | 成功条数 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-AUD-009** | 双分支：开嘱退回 `ACTIVE/VERIFIED`→`DRAFT`；取消停嘱校对 `STOPPED`+已停嘱校对→清两字段（不改状态；**暂不校验操作人**） |
| **BR-IP-AUD-013** | **开嘱退回门控**（仅 ACTIVE/VERIFIED）：`InpatientAdviceRevokeGate` + 来源/派生/净执行/发药净额（正向 `COMPLETED` 与 `REFUND+REFUNDED` 数量对冲；DM-MED-002 §6.1.4）/费用；**取消停嘱校对不走本门控** |
| **BR-IP-AUD-010** | 同事务整批回滚 |
| **BR-IP-AUD-012** | encounter 可见 |
| **BR-IP-AUD-014** | 停嘱校对字段对契约 |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-AUD-204** | 取消停嘱校对失败：尚未停嘱校对、非长期 STOPPED、或域断言失败 |
| **EX-IP-AUD-301** | 开嘱退回非 ACTIVE/VERIFIED 或 **BR-IP-AUD-013** 未满足 |
| **EX-IP-AUD-302** | 不可见 |

**Mapper 接口方法签名**

- `toRejectMedicationOrderCommand(NurseStationOrderAuditWriteItemDto item, String reason, Long operatorId, Long performerCheckId, LocalDateTime checkTime): RejectMedicationOrderCommand`
- `toRejectServiceRequestToDraftCommand(NurseStationOrderAuditWriteItemDto item, String reason, Long operatorId, Long performerCheckId, LocalDateTime checkTime): RejectServiceRequestToDraftCommand`
- `toCancelVerifyStopMedicationOrderCommand(...): CancelVerifyStopMedicationOrderCommand`
- `toCancelVerifyStopServiceRequestCommand(...): CancelVerifyStopServiceRequestCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `items` 非空且 ≤50 | |
| 2 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | |
| 3 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | — | 开嘱退回写校对人；取消停嘱校对**不**校验须为原校对人 |
| 4 | Pre | 可见性 + 按行分支 | 开嘱：`ACTIVE/VERIFIED` + **BR-IP-AUD-013**；取消停嘱校对：`STOPPED` 且 `stop_performer_check_id` 非空 | 不可见 **EX-IP-AUD-302**；开嘱门控 **EX-IP-AUD-301**；取消停嘱校对分支失败 **EX-IP-AUD-204** |
| 5 | Cmd | **for-each 开嘱药品** `rejectMedicationOrder` / **取消停嘱校对药品** `cancelVerifyStopMedicationOrder` | — | 开嘱→**DRAFT**；取消停嘱校对清两字段 |
| 6 | Cmd | **for-each 开嘱诊疗** `rejectServiceRequestToDraft` / **取消停嘱校对诊疗** `cancelVerifyStopServiceRequest` | — | 同上 |
| 7 | Cmd | **仅开嘱退回诊疗** `tryRevertHoldAfterServiceRequestReverted` | 里程碑对齐 | 取消停嘱校对跳过 |
| 8 | Asm | Response | — | |

> **App 事务**：`rejectNurseStationOrders` `@Transactional` 包裹 5～7。

## 4. QueryService 与事件刷新

| QueryService | 查询方法 | 本 AS 用途 |
| --- | --- | --- |
| `InpatientOrderListSkeleton` | `listInpatientNurseStationAuditOrders` | AUD-01 列表骨架（**Q-IP-AUD-001**） |
| `InpatientEncounterContextQueryModel` | `listInpatientEncounterContexts` | 可见 encounter 门禁 |

**QueryService 查询语义（最终一致）**

| 领域事件 | QueryService | 更新动作 |
| --- | --- | --- |
| `MedicationOrderVerifiedEvent` | `InpatientOrderListSkeleton` | 状态 → `VERIFIED`；校对人/时间 |
| `ServiceRequestVerifiedEvent` | `InpatientOrderListSkeleton` | 同上 |
| `MedicationOrderRejectedEvent` | `InpatientOrderListSkeleton` | 状态 → `DRAFT` |
| `ServiceRequestRejectedToDraftEvent` | `InpatientOrderListSkeleton` | 同上 |
| `DepartmentTransferInitiatedEvent` | `InpatientEncounterContextQueryModel` | 待转科展示 |
| `HospitalDischargedEvent` | `InpatientEncounterContextQueryModel` | 待出院展示 |

字段级映射以 `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §4.2、`DM §6.2 各 Request QueryService` §3 为唯一真源；本文不重复 §4.2 细表。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | 领域 / 资源写入顺序 |
| --- | --- | --- |
| **AUD-02** | `verifyNurseStationOrders` | ① 开嘱：药/诊 → **VERIFIED**；停嘱校对：写 `stop_*` 两字段、仍 **STOPPED** ② 仅开嘱校对转科/出院：同一事务 **Encounter** → `ON_HOLD` |
| **AUD-03** | `rejectNurseStationOrders` | ① 开嘱退回 → **DRAFT**；取消停嘱校对：清两字段、仍 **STOPPED** ② 仅开嘱退回转科/出院：尝试 `ON_HOLD` → `IN_PROGRESS`（GAP-ATD-008） |

### 5.2 事务边界 / 失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-IP-AUD-02 | App `@Transactional` 含 Med/Service Cmd + Encounter 联动 | 全回滚 → **EX-IP-AUD-201/203** |
| IF-IP-AUD-03 | App `@Transactional` 含全部 reject Cmd + 逆向 Encounter 编排 | 全回滚 → **EX-IP-AUD-301/204/203** |
| IF-IP-AUD-01 | 无写事务 | — |

> **顺序约束**：须先完成请求校对写库，再编排 Encounter（与 AS §4 IF-IP-AUD-02 一致）；避免「就诊已待转科但医嘱仍 ACTIVE」对外可见——同事务内按 §3.3 序号 6→10 调用。

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 读接口天然幂等 | `listNurseStationOrderAudit` | §3.2 |
| 转科/出院 encounter 已 `ON_HOLD` 且同 request 幂等跳过 | `verifyNurseStationOrders` 步骤 9～10：里程碑 `requestId` 对齐 | §3.3 |
| 乐观：域 assert 当前态 | `ensureVerifiable` / `assertVerifiable` / `assertRejectable` | §3.3 步骤 5、§3.4 步骤 4 |
| 写接口非幂等 | 重复提交同 ACTIVE 行第二次应 **EX-IP-AUD-201** | §3.3 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 转科/出院 ON_HOLD 并发拒绝 | `initiateDepartmentTransfer` / `dischargeInpatientEncounter` / `confirmDepartmentTransfer`：`requireEncounterForUpdate` + 锁内断言；校对 App 异类已校对 Pre 门禁；`OptimisticLockingFailureException` → **EX-IP-AUD-203** | §3.3 步骤 9～10（GAP-ATD-006） |
| 转科/出院逆向回滚 | `revertTransferOrDischargeHold` + `InpatientTransferDischargeHoldOrchestrationsupport；医生站签退/停嘱同步挂载 | §3.4 步骤 7（GAP-ATD-008） |
| 批量 ≤50 | Gate 步骤 1 | §3.3/§3.4 |

### 6.3 重试与容错

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 读失败 **EX-IP-AUD-101** | `listNurseStationOrderAudit` catch 非 BusinessException | §3.2 |
| 写失败整批回滚 | `verifyNurseStationOrders` / `rejectNurseStationOrders` | §3.3/§3.4 |

### 6.4 数据脱敏

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 列表不返回余额/费别 | DTO 无对应字段（**BR-IP-AUD-007**） | §3.2 |

### 6.5 审计日志

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 校对人/时间写入 Command | `performerCheckId`、`checkTime` on Verify/Reject Command | §3.3 步骤 3、§3.4 步骤 3 |
| 操作人 `operatorId` | 各 Domain Command | §3.3 步骤 2 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| pending Tab | `ACTIVE` ∪ 长期 `STOPPED` 且停嘱校对空；`stopAuditPending` | P0 |
| passed Tab | **仅** `VERIFIED` | P0 |
| stopped / suspended | stopped=已停嘱校对 `STOPPED`；suspended=`CANCELLED` | P0 |
| long / temp 筛选 | `therapy_enum` 正确；临时无停嘱待校 | P0 |
| 开嘱校对 | → **VERIFIED** + 校对人/时间 | P0 |
| 停嘱校对 | 只写两字段、仍 **STOPPED**；重复校对拒；不自动 skip pending | P0 |
| 非合法校对分支 | **EX-IP-AUD-201** | P0 |
| 开嘱退回 | → **DRAFT** 且离开护士 Tab | P0 |
| 取消停嘱校对 | 清两字段、仍 STOPPED；不校验操作人 | P0 |
| TRANSFER 校对 | Encounter → 待转科（`ON_HOLD`） | P0 |
| DISCHARGE 校对 | Encounter → 待出院 | P0 |
| 不可见 encounter 写 | **EX-IP-AUD-202/302** | P0 |
| 整批含一条非法 | 整批回滚 | P0 |
| 药嘱成组 | 同 `groupId` 多行回传一致组号并聚邻；前端合并括线；勾选校对/退回整组扩行；未成组为 null；诊嘱行无 `groupId` | P1 |

### 7.2 非功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 多 encounter 列表 | 单次 Q-IP-AUD-001 + 按 encounter 补明细，无 N+1 行级查询 | P0 |
| 写接口同事务 | Med/Service + Encounter 同成功或同回滚 | P0 |

### 7.3 QueryService一致性

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 校对后刷新列表 | InpatientOrderListSkeleton QueryService `VERIFIED`；001 待校对计数减 | P1 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| POST `listNurseStationOrderAudit` | `POST /api/app/nurseStationOrderAudit/listNurseStationOrderAudit` | P0 |
| POST `verifyNurseStationOrders` | `POST /api/app/nurseStationOrderAudit/verifyNurseStationOrders` | P0 |
| POST `rejectNurseStationOrders` | `POST /api/app/nurseStationOrderAudit/rejectNurseStationOrders` | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 应用层待成码 | 本 SDD 目标态；P0 用例待 `NurseStationOrderAuditAppService` 落盘后执行 | P1 |

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.3.25 | 2026-10-13 | **BR-IP-NUR-003-ANES-001 纠偏**：麻醉识别改为申请单号 AA 前缀；对齐 **AS v1.31** |
| v1.3.24 | 2026-10-13 | **BR-IP-NUR-003-ANES-001**：`ListNurseStationOrderAuditRequest.anesthesiaApplyOnly` + `InpatientNurseStationAnesthesiaApplyScopeSupport`；对齐 **AS v1.30** |
| v1.3.23 | 2026-10-12 | **剂量展示**：`MedicationDosageDisplayAssembler` 输液 `injectFlag` 且拆零&lt;1 主显计量单位；`dailyDoseDisplay` 同源；对齐 **AS v1.29** |
| v1.3.18 | 2026-10-08 | **死亡出院展示**：`NurseStationOrderViewDisplayContext.dischargeOutWayCodeByServiceRequestId`；`resolveSpecialServiceAdviceDisplayName` 在 `out_way_code=5` 时返回「死亡出院」；对齐 **AS v1.24** / **AS-IP-DOR-003 v1.54** |
| v1.3.17 | 2026-09-16 | **BR-IP-AUD-006**：`DisplayCore`/`NurseStationOrderAuditRowDto` 增补 `firstDose*`；`applyViewCoreToAuditRow` 拷贝；对齐 **AS v1.23** |
| v1.3.16 | 2026-09-16 | **废止 BR-IP-AUD-015**：删除 `InpatientStopVerifyAutoSkipPendingSupport` / SettingResolver / AUD-02 步骤 10a；对齐 **AS v1.22** |
| v1.3.15 | 2026-09-11 | **BR-IP-AUD-015**（已废止于 v1.3.16）：`InpatientStopVerifyAutoSkipPendingSupport` + SettingResolver；AUD-02 步骤 10a 复用 **IF-IP-EXE-04**；对齐 **AS v1.21** |
| v1.3.22 | 2026-10-09 | **BR-IP-AUD-017**：出院校对仅拦净生效费用（冲销 `refund_id` 轧差）；对齐 **AS v1.28** |
| v1.3.21 | 2026-10-09 | **BR-IP-AUD-017**：`DischargeAuditChargeTimeGate` 比对客户可见费用时间（与费用明细同轴）；对齐 **AS v1.27** |
| v1.3.20 | 2026-10-09 | **BR-IP-AUD-017 / EX-IP-AUD-205**：出院校对前 `DischargeAuditChargeTimeGate`；步骤 5a；对齐 **AS v1.26** |
| v1.3.19 | 2026-10-08 | **BR-IP-AUD-006 `orderKindDisplay`**：目录类目检验/检查/治疗/手术/麻醉；术式 PD→手术；对齐 **AS v1.25** / `resolveOrderKindDisplay` |
| v1.3.14 | 2026-08-13 | **BR-IP-AUD-013**：发药门控改为净已发数量对冲（正向 `COMPLETED` 不因已全额 `REFUNDED` 误拦）；对齐 **AS v1.19** / DM-MED-002 §6.1.4 |
| v1.3.13 | 2026-08-08 | **EX-IP-AUD-204** 与开嘱退回 **301** 分码；verify/reject `@Transactional` 对齐 **BR-IP-AUD-010**；对齐 **AS v1.17** |
| v1.3.12 | 2026-08-08 | **停嘱校对**：DTO `stopPerformerCheckId`/`stopCheckTime`/`stopCheckerName`/`stopAuditPending`；AUD-02/03 双分支域命令；**BR-IP-AUD-003/008/009/014**；对齐 **AS v1.16** / **DM-MED-003** / **DM-WKF-004** |
| v1.3.11 | 2026-08-04 | **输液滴速回显**：校对行 `speedCode`/`speedDisplay`（`DisplayCore` 同源）；对齐 **AS-IP-DOR-003-SDD v1.45** |
| v1.3.10 | 2026-07-22 | **BR-IP-AUD-002**：澄清住院退药不造负向 Request；纠偏重点=废止 based_on 排除；对齐 **AS v1.15** |
| v1.3.9 | 2026-07-22 | **BR-IP-AUD-002**：退药/退诊疗排除改认 `refund_*_id`；废止 based_on 派生排除；骨架透传 `refundMedicineId`/`refundServiceId`；对齐 **AS v1.14** |
| v1.3.9 | 2026-09-02 | **BR-IP-AUD-006**：`NurseStationOrderAuditRowDto` 增补 **`skinTestOutcomeCode`/`skinTestOutcomeText`**；`SkinTestOutcomeByMedicationRequestLoader` 批量预热 |
| v1.3.8 | 2026-07-22 | **BR-IP-AUD-002**：`filterAuditQueueRows` / pendingAuditCount **保留**皮试检查 companion（`based_on → med`）；退诊疗仍锚 `wkf_service_request`；对齐 **AS v1.13** |
| v1.3.7 | 2026-07-18 | **BR-IP-AUD-006 / BR-IP-ORD-028**：`resolveOrderKindDisplay` 嘱托→「嘱托」；`orderKindDisplay` 字段表补齐；对齐 **AS v1.12** |
| v1.3.6 | 2026-07-18 | **BR-IP-ORD-031**：列表行增补 `stopperId`/`stopperName`；开立/停嘱时间后跟人名；对齐医生站三时间轴读侧 |
| v1.3.5 | 2026-07-17 | **西药成组一起校对**：**BR-IP-NUR-003-GRP-001/002**；`InpatientNurseStationTcmOrchestrator#expandMedRequestIdsForNurseWrite`（中药整方优先，否则同 `group_id`）；对齐 **AS v1.11** |
| v1.3.4 | 2026-07-16 | **BR-IP-ORD-028④ 护士站嘱托展示**：§2.5 `buildServiceDisplayCore` 委托 **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`**；`adviceDisplayName` 嘱托行优先 `entrust_content`；对齐 **AS-IP-DOR-003** / **AS v1.10** |
| v1.3.3 | 2026-06-20 | **support 落点对齐**：废止 App 层 `orderview`；`NurseStationOrderViewListAssembler`/`InpatientNurseStationTcmsupport → `module.querysupport.nursestation`；骨架仍用 `querysupport/order` + `common/assembler/order`；对齐 **AS v1.7** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.3.2 | 2026-06-20 | **AS-IP-DOR-006 下游**：`medicineCategoryTab`、中药方折叠、`InpatientNurseStationTcmsupport、写路径整方扩行；对齐 **AS v1.6** |
| v1.3.1 | 2026-05-28 | §2.1：**`EncounterContextFacade`** + **`InpatientOrderListMergeAssembler`** / **`InpatientOrderLookupLoader`**（ReadModel 废止后校对列表骨架） |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.3 | 2026-05-27 | **IF-IP-AUD-01**：`NurseStationOrderAuditRowDto` 增补药嘱 **`groupId`**（`applyViewCoreToAuditRow` 自 `DisplayCore` 拷贝；与 **AS-IP-NUR-004** 执行列表、医生站列表同源） |
| v1.2 | 2026-05-27 | **`orderview`**：`dosageDisplay` 单位展示名与 **BR-ORD-022** 对齐（`ProductDefinitionCatalogUnitBandsAssembler`）；上游 **AS-OP-DOR-003 v1.25** |
| v1.1 | 2026-05-24 | 抽取 **`orderview`** 共享医嘱查看装配器（§2.5）；`NurseStationOrderAuditListAssembler` 行展示委托 `NurseStationOrderViewListAssembler`；对齐应用层成码 |
| v1.0 | 2026-05-22 | 首版：对齐 **AS-IP-NUR-003 v1.3**；方案 A 读侧 **Q-IP-AUD-001**；IF-IP-AUD-01～03 编排序列、DTO、转科/出院 Encounter 联动；常量 `EX_IP_AUD_*` / `SERVICE_CATEGORY_*` 目标态 |
