# 医技工作站 · 医技执行 · 应用服务详细设计（SDD）

> **编码**：AS-MT-EXE-001-SDD · **版本**：v1.3.7 · **日期**：2026-10-17
> **上级 AS**：`health/AppService/AS-MT-EXE-001-医技工作站医技执行.md`（v1.5.8）
> **实现阶段口径**：**应用层已落地** IF-MT-EXE-01～05/03B（`MedicalTechExecutionAppService`）；领域队列 `EncounterQueryService#pageMedicalTechWorkstationQueue` 已落地（**GAP-MT-EXE-001 已关闭**）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 医技工作站 · 医技执行 |
| **边界** | IF-MT-EXE-01～05、03B；费用清单见 `AS-MT-FEE-001`；医技计费见 `AS-MT-CHG-001`（不在本 SDD） |
| **编排权威** | AS-MT-EXE-001 §2 / §4 |
| **方法权威** | 对应 DM §6 / §6.2；耗材发放复用护士站发放工作流域能力 |
| **严格对齐** | 可见性 DD；门诊执行门控同 AS-OP-NUR-001 已计费语义；住院执行**不**建 ChargeItem；`Procedure.org_id`=登录 TECH（护士侧计数 `org_id` null）；IF-04/05 **无**同态硬拦；出院确费见 AS-MT-FEE / BR-IP-ATD-011⑤ |
| **实现阶段口径** | **应用层已落地** IF-MT-EXE-01～05/03B |

### 1.2 术语口径

| 术语 | 口径 |
| --- | --- |
| **字段口径** | Long 主键不转 Number；`encounterScope` = `OUTPATIENT` \| `INPATIENT`；分页继承 `PagedResult`：`content` + `totalCount`（**禁止** `total`/`records`） |
| **QueryService 口径** | 队列走 Encounter 医技队列方法；禁止 App 直查 Repository |
| **事务口径** | IF-04 / IF-05 单请求单事务；读 IF 无写库 |
| **执行侧隔离** | 医技写 `Procedure.org_id` = 登录 TECH；护士执行计数侧 `org_id` 为 null |
| **软落地取消** | 医技/护士取消本侧均不互卡；EX-214 / EX-IP-CXL-207 **不抛**；出院仅卡医技相关费用确费（EX-IP-ATD-309） |

---

## 2. 代码结构

### 2.1 包路径

```text
com.zxplat.domain.application.medicaltech.execution/
  service/MedicalTechExecutionAppService.java
  mapper/MedicalTechExecutionMapper.java
  mapper/MedicalTechExecutionMapperImpl.java
  request/
    PageMedicalTechPatientQueueRequest.java
    LoadMedicalTechExecutionSheetRequest.java
    PageMedicalTechProcedureRecordsRequest.java
    ListMedicalTechNetRemainingProceduresRequest.java
    BatchPerformMedicalTechExecutionRequest.java
    BatchCancelMedicalTechExecutionRequest.java
  response/
    PageMedicalTechPatientQueueResponse.java
    LoadMedicalTechExecutionSheetResponse.java
    PageMedicalTechProcedureRecordsResponse.java
    ListMedicalTechNetRemainingProceduresResponse.java
    BatchPerformMedicalTechExecutionResponse.java
    BatchCancelMedicalTechExecutionResponse.java
  dto/
    MedicalTechPatientQueueItemDto.java
    MedicalTechExecutionLineDto.java
    MedicalTechProcedureRecordDto.java
    MedicalTechPerformItemDto.java
    MedicalTechCancelProcedureItemDto.java
    MedicalTechCancelDeviceItemDto.java
```

> 公共库 `OptionDto` / `PagedResult` 等不在本模块 dto/ 新建。  
> 共享：`com.zxplat.domain.application.shared.medicaltech.MedicalTechBilateralHomologySupport`；  
> `com.zxplat.domain.application.shared.nursestation.DeviceDispenseNurseWorkflowOrchestrator`。

### 2.1.1 前端落点（openhis-pro-ui）

| 项 | 值 |
| --- | --- |
| 菜单 | `MedicalTechWorkstation` · path `/medical-tech/workstation` · `V29__seed_medical_tech_workstation_menu.sql` |
| 页 | `views/medical-tech/workstation/`（壳 + Tab 医技执行 / 费用清单 / 医技计费） |
| HTTP | `api/medical-tech/medical-tech-execution.*` → `/medicalTechExecution/*` |

### 2.2 依赖注入与领域方法白名单

| 依赖 | 用途 |
| --- | --- |
| `CurrentManagedOrganizationIdsResolver` / `OrganizationQueryService` | TECH 登录门禁 |
| `EncounterContextReader` / `EncounterQueryService` | 医技队列分页；就诊门闸；队列人口学 |
| `ServiceRequestQueryService` | 本就诊诊疗请求 |
| `DeviceRequestQueryService` / `DeviceDispenseQueryService` | 耗材请求/发放 |
| `ChargeItemQueryService` | 计费聚合与门诊执行门控 |
| `ProcedureQueryService` / `ProcedureDomainService` / `ProcedurePerformCountGate` | 次数、全量记录、净剩、写完成/取消 |
| `DeviceDispenseNurseWorkflowOrchestrator` | 耗材执行/取消（与护士站同开关） |
| `DeviceAutoInventoryDeductSettingResolver` | 全局扣库开关 |
| `InventoryItemDomainService` | 扣库/回补 |
| `ProcedureExecutionPerformerOrchestrator` | 主执行人登记 |
| `CurrentPractitionerResolver` | 当前医务人员 |
| `LocationQueryService` | 耗材房→组织 |
| `MedicalTechBilateralHomologySupport` | 取消同态空操作；出院确费门禁在入出转侧复用 `assertDischargeTechPending` |
| `MedicalTechExecutionMapper` | Asm |
| `OrderRequestCatalogWarmAssembler` / 收费归并 Helper | 目录与金额展示装配 |

**领域方法白名单**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| EncounterQueryService | `pageMedicalTechWorkstationQueue` | 01 |
| EncounterQueryService | `mustGetEncounter`（既有等价） | 02～05 |
| ServiceRequestQueryService | `listServiceRequestsByEncounter`（既有等价） | 02、04 |
| ServiceRequestQueryService | `mustGetServiceRequest`（既有等价） | 04 |
| DeviceRequestQueryService | `listDeviceRequestsByEncounter` / `listDeviceRequestsByEncounterIds`（既有等价） | 01、02、04 |
| DeviceDispenseQueryService | `listDeviceDispensesByEncounter` / `listDeviceDispensesByEncounterIds` / `listDeviceDispensesByIds`（既有等价） | 01～05 |
| ChargeItemQueryService | `listChargeItemsByEncounterId`（既有等价） | 02、04 |
| ProcedureQueryService | `listByEncounterIds` / `getProcedureRecords` / `listNetRemainingCompletedProcedureRecords` / `loadProcedureById`（既有等价） | 02、03、03B、04、05 |
| ProcedurePerformCountGate | `assertPerformCountBelowPlanned`（既有等价） | 04 |
| ProcedureDomainService | `createProcedureRecord` → `startProcedureExecution` → `completeProcedureRecord` | 04 |
| ProcedureDomainService | `createProcedureRecord` → `startProcedureExecution` → `cancelProcedureRecord`（`refund_id`） | 05 |
| DeviceDispenseNurseWorkflowOrchestrator | `validateOutpatientAutoDeductInventoryLocation` / `applyOutpatientNurseDeviceDispenses` / `revertOutpatientNurseDeviceDispenses` | 04、05 |
| InventoryItemDomainService | `decreaseQuantitiesMerged` / `increaseQuantities` | 04、05 |
| MedicalTechBilateralHomologySupport | `assertTechCancelHomology`（空操作） | 05 |
| MedicalTechExecutionMapper | 各 `to*` | 全 IF |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 scope | `PageMedicalTechPatientQueueRequest.encounterScope` | DM-ADM-002 §3 就诊类别 | `OUTPATIENT`/`INPATIENT` |
| 组织分类 TECH | `com.zxplat.domain.share.enums.OrganizationClassification` | DM-ADM-004 §3 `class_enum` | 登录科须 TECH |
| 就诊 class | `com.zxplat.domain.share.enums.EncounterClass` | DM-ADM-002 §3 | AMB/IMP 与 scope 对齐 |
| 服务申请状态 | `com.zxplat.domain.share.enums.RequestStatus` | DM-WKF-004 §5 | 门诊 COMPLETED；住院 VERIFIED；停嘱 STOPPED |
| 执行记录状态 | `com.zxplat.domain.share.enums.EventStatus` | DM-CLN-004 §5 / BR-PRO-005 | COMPLETED / CANCEL |
| Procedure 执行科室 | `CreateProcedureRecordCommand.orgId` / `Procedure.orgId` | DM-CLN-004 §3 `org_id` | 医技=登录 TECH；护士侧 null |
| 耗材发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | DM-WKF-007 §5 | PREPARATION/PREPARED/COMPLETED |
| 收费状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | DM-ADM-011 §5 | 门诊门控 PLANNED/SETTLED |
| 扣库开关 | `DeviceAutoInventoryDeductSettingResolver`（与护士站同常量） | AS-OP-NUR-001 BR-NUR-014 | `"1"`/`"0"` |
| 业务异常码 | `MedicalTechExecutionConstants.EX_MT_EXE_*` | AS-MT-EXE-001 §5 | 含 202/214 预留不抛 |

---

## 3. 按接口 ID 命名展开

> **先读各 IF「领域编排序列」**，按序号执行。Pre/Gate 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-MT-EXE-01 | `pageMedicalTechPatientQueue` | 分页查询医技患者队列 | `POST /api/app/medicalTechExecution/pageMedicalTechPatientQueue` |
| IF-MT-EXE-02 | `loadMedicalTechExecutionSheet` | 加载医技执行单 | `POST /api/app/medicalTechExecution/loadMedicalTechExecutionSheet` |
| IF-MT-EXE-03 | `pageMedicalTechProcedureRecords` | 分页查询医技执行记录 | `POST /api/app/medicalTechExecution/pageMedicalTechProcedureRecords` |
| IF-MT-EXE-03B | `listMedicalTechNetRemainingProcedures` | 查询净剩可取消执行记录 | `POST /api/app/medicalTechExecution/listMedicalTechNetRemainingProcedures` |
| IF-MT-EXE-04 | `batchPerformMedicalTechExecution` | 批量医技执行 | `POST /api/app/medicalTechExecution/batchPerformMedicalTechExecution` |
| IF-MT-EXE-05 | `batchCancelMedicalTechExecution` | 批量取消医技执行 | `POST /api/app/medicalTechExecution/batchCancelMedicalTechExecution` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数 / TECH 登录 | 不再后续 |
| **Pre** | 只读加载与 BR 门控 | 抛业务异常，禁止 Cmd |
| **Cmd** | 领域写 | 事务回滚见 §5 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-MT-EXE-01 `pageMedicalTechPatientQueue`（分页查询医技患者队列）

**Request `PageMedicalTechPatientQueueRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterScope` | String | Y | `OUTPATIENT` / `INPATIENT` |
| `keyword` | String | N | 姓名/拼音/五笔 |
| `startDate` | LocalDateTime | N | 就诊开始下界（**仅门诊**；住院忽略） |
| `endDate` | LocalDateTime | N | 就诊开始上界（**仅门诊**；住院忽略） |
| `pendingDeviceOnly` | Boolean | N | true=仅本站待发耗材 |
| `executionQueueFilter` | String | N | `PENDING`（待执行）/ `EXECUTED`（已执行）；空=不按执行态收窄。门诊按本站诊疗项目+耗材整单；住院仅按诊疗项目（BR-MT-EXE-023） |
| `inpatientStayFilter` | String | N | 仅住院：`IN_HOSPITAL`（默认/空）/ `NOT_IN_HOSPITAL`（已清床离院）；门诊忽略 |
| `pageNo` | Integer | N | 默认 1 |
| `pageSize` | Integer | N | 页大小 |

**Response `PageMedicalTechPatientQueueResponse`**（继承 `PagedResult`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | List\<MedicalTechPatientQueueItemDto\> | 当前页行 |
| `totalCount` | Long | 总数 |
| `pageNo` / `pageSize` 等 | — | 继承分页元数据（以 `PagedResult` 为准） |

**DTO `MedicalTechPatientQueueItemDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊主键 |
| `patientName` | String | 姓名 |
| `genderEnum` / `genderEnumText` | String | 性别 |
| `birthDate` | LocalDate | 出生日期 |
| `ageText` | String | 年龄 |
| `slipNo` | String | 业务号 |
| `encounterTime` | LocalDateTime | 就诊开始：门诊=挂号/就诊；住院=入院时间 |
| `dischargeTime` | LocalDateTime | 出院时间（仅住院；`end_time`；未出院可空） |
| `contractDisplay` | String | 费用性质展示 |
| `phone` | String | 手机号（信息条回显；门诊/住院均投影） |
| `idCard` | String | 身份证号（信息条回显；门诊/住院均投影） |
| `hasPendingDevice` | Boolean | 本站待发耗材 |
| `statusEnum` / `statusEnumText` | String | 就诊状态（仅住院；待结算轨前端 Gate 读此字段） |
| `nursingLevelName` | String | 护理级别（仅住院） |
| `wardName` / `houseName` / `bedName` | String | 病区/病房/床位（仅住院） |
| `diagnosisSummary` | String | 诊断摘要（仅住院） |
| `accountBalance` 等余额字段 | BigDecimal/String/Boolean | 住院余额展示（库内 − 待结算；对齐护士站） |

**关键校验**：`encounterScope` 合法；当前登录组织 `class_enum=TECH`；**住院**按 `inpatientStayFilter`：默认/`IN_HOSPITAL`→`IN_PROGRESS`/`ON_HOLD`/`PENDING`；`NOT_IN_HOSPITAL`→`DISCHARGED`（排除待入科）。`executionQueueFilter` 非法码不收窄（兼容旧调用）。

**失败码**：EX-MT-EXE-101

**Mapper**：`toQueueQuery(PageMedicalTechPatientQueueRequest, Long loginTechOrgId): MedicalTechWorkstationQueueQuery`；`toPageQueueResponse(Page\<Encounter\>, List\<MedicalTechPatientQueueItemDto\>): PageMedicalTechPatientQueueResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | scope、登录 TECH | 非 TECH → EX-MT-EXE-101 |
| 2 | Pre | `MedicalTechExecutionMapper#toQueueQuery` | Request + loginOrgId + 共看可见科 | 含 `executionQueueFilter` |
| 3 | Pre | `EncounterQueryService#pageMedicalTechWorkstationQueue` | Query | Spec：有活 + 可选待发 + **BR-MT-EXE-023** 分桶；返回 `Page<Encounter>` |
| 4 | Asm | 队列行装配（EncounterContextReader + 待发红点） | 本页 encounterIds | BR-MT-EXE-003 |
| 5 | Asm | `MedicalTechExecutionMapper#toPageQueueResponse` | Page + rows | `content`/`totalCount` |

---

### 3.3 IF-MT-EXE-02 `loadMedicalTechExecutionSheet`（加载医技执行单）

**Request `LoadMedicalTechExecutionSheetRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `encounterScope` | String | Y | 与队列一致 |
| `serviceCategoryCode` | String | N | 仅过滤诊疗列表 |
| `executionQueueFilter` | String | N | `PENDING`/`EXECUTED`；与 IF-01 同码；空=不按执行态收窄。待执行：诊疗尚可执行；门诊另保留待发耗材。已执行：诊疗已做满；门诊另保留非待发耗材。住院不按耗材态收窄（**BR-MT-EXE-023**） |

**Response `LoadMedicalTechExecutionSheetResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `serviceLines` | List\<MedicalTechExecutionLineDto\> | 诊疗 |
| `deviceLines` | List\<MedicalTechExecutionLineDto\> | 耗材 |

**DTO `MedicalTechExecutionLineDto`**（两列表同构）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | Long | 请求主键 |
| `requestTable` | String | 来源表 |
| `itemName` / `itemCode` | String | 名称/编码 |
| `specification` | String | 规格 |
| `quantity` | BigDecimal | 数量 |
| `unitName` | String | 单位 |
| `plannedExecutionCount` | Integer | 计划次数（诊疗） |
| `completedCount` / `cancelledCount` | Integer | 完成/取消次数（仅计 loginTech `org_id`） |
| `chargeAmount` | BigDecimal | 金额合计：有账单用落库合计；无账单用目录现价×数量预览 |
| `chargeStatusEnum` / `chargeStatusEnumText` | String | 首条收费状态（无账单可空） |
| `dispenseStatusEnum` / `dispenseStatusEnumText` | String | 发放态（耗材） |
| `lotNumber` | String | 签发批号（耗材） |
| `unitPrice` | BigDecimal | 单价：有账单用落库价；无账单用目录 SALE/参考售价现价 |
| `prescriptionNo` | String | 申请单号 |
| `serviceRequestDetails` | List | 部位/样本（申请单） |
| `remark` | String | 备注 |
| `selectable` | Boolean | 是否可勾选（**BR-MT-EXE-018**）：诊疗=尚可执行或净剩可取消；耗材=待发放或已配药/已发放可取消；满额执行后仍为 true 以便取消 |
| `dispenseId` | Long | **主发放行主键（耗材勾选执行用）** |

**关键校验**：就诊存在；类别匹配 scope；对本站有可见行或允许空单。

**失败码**：EX-MT-EXE-102

**Mapper**：`toSheetResponse(...): LoadMedicalTechExecutionSheetResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | encounterId/scope/TECH | |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | encounterId | 类别须匹配 scope |
| 3 | Pre | `ChargeItemQueryService#listChargeItemsByEncounterId` | encounterId | 金额聚合 |
| 4 | Pre | `ServiceRequestQueryService#listServiceRequestsByEncounter` | encounterId | |
| 5 | Pre | `DeviceRequestQueryService#listDeviceRequestsByEncounter` + Dispense | encounterId | |
| 6 | Pre | 内存按 loginTechOrgId 过滤 + 行状态同轴 | DD 轴 | BR-MT-EXE-004；**无药品**；诊疗 `org_id`=loginTech；耗材房上级=loginTech；**门诊 COMPLETED / 住院 VERIFIED** |
| 7 | Pre | Procedure 次数（仅 loginTech org） | 诊疗请求 | BR-MT-EXE-007 |
| 8 | Asm | `applyExecutionQueueFilterToSheetLines` | `executionQueueFilter` + scope | **BR-MT-EXE-023** 行级收窄；空/非法码跳过 |
| 9 | Asm | `MedicalTechExecutionMapper#toSheetResponse` | 聚合结果 | 含 `dispenseId` |

---

### 3.4 IF-MT-EXE-03 `pageMedicalTechProcedureRecords`（分页查询医技执行记录）

**Request `PageMedicalTechProcedureRecordsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |
| `encounterScope` | String | Y | |
| `requestId` | Long | N | |
| `statusEnum` | String | N | |
| `pageNo` / `pageSize` | Integer | N | |

**Response `PageMedicalTechProcedureRecordsResponse`**（继承 `PagedResult`）：`content` + `totalCount`

**DTO `MedicalTechProcedureRecordDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `procedureId` | Long | |
| `requestId` | Long | |
| `orderItemDisplayName` | String | 项目名 |
| `actionDisplayText` | String | 完成/取消等 |
| `statusEnum` | String | |
| `recordedTime` | LocalDateTime | |
| `operatorDisplayName` | String | 操作人 |

**失败码**：EX-MT-EXE-103

**领域编排序列**：Gate TECH → Pre 就诊门闸 → Pre `ProcedureQueryService#listByEncounterIds` 后内存按 loginTech `orgId` 过滤分页 → Asm Mapper。仅读路径。

---

### 3.5 IF-MT-EXE-03B `listMedicalTechNetRemainingProcedures`（查询净剩可取消执行记录）

**Request `ListMedicalTechNetRemainingProceduresRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |
| `encounterScope` | String | Y | |
| `requestId` | Long | N | |
| `requestTable` | String | N | 与 requestId 同传 |

**Response**：`records: List<MedicalTechProcedureRecordDto>`（含 `procedureId`）

**领域编排序列**：Gate → Pre 就诊 → Pre `ProcedureQueryService#listNetRemainingCompletedProcedureRecords` → 内存仅保留 loginTech `orgId` → Asm。仅读。BR-MT-EXE-016。

---

### 3.6 IF-MT-EXE-04 `batchPerformMedicalTechExecution`（批量医技执行）

**Request `BatchPerformMedicalTechExecutionRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |
| `encounterScope` | String | Y | |
| `serviceItems` | List\<MedicalTechPerformItemDto\> | N | 诊疗勾选 |
| `deviceItems` | List\<MedicalTechPerformItemDto\> | N | 耗材勾选 |
| `deviceLocationId` | Long | N | 扣库开时可选耗材房 |

**DTO `MedicalTechPerformItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `requestId` | Long | Y | |
| `requestTable` | String | Y | |
| `dispenseId` | Long | N | 耗材发放行（与执行单行 `dispenseId` 对齐） |

**Response `BatchPerformMedicalTechExecutionResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `successCount` | Integer | 成功条数 |
| `message` | String | 汇总提示 |

**失败码**：EX-MT-EXE-201、203～206（**EX-MT-EXE-202 预留不抛**：协同 DD 不要求执行先后）

**Mapper**：`toCreateProcedureRecordCommand` / `toStartProcedureExecutionCommand` / `toCompleteProcedureRecordCommand` 等

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、非空勾选 | 诊疗与耗材不得同时皆空 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | | scope 匹配 |
| 2b | Gate | `InpatientPendingSettlementClinicalGate` | 住院 statusEnum | PENDING/COMPLETED/DISCHARGED → **EX-206**（BR-MT-EXE-019） |
| 3 | Pre | 校验每条诊疗 `org_id`=loginTech；耗材 `dispenseId` 本站 | | 否则拒绝 |
| 4 | Pre | **若** outpatient → 已计费门控（BR-009）→ 失败 EX-201；**若** inpatient → BR-010，写医技侧 Procedure、**禁止**建诊疗 Charge；**不做**执行同态、**不抛** EX-202 | scope | |
| 5 | Pre | `ProcedurePerformCountGate#assertPerformCountBelowPlanned`（带 loginTech org） | 诊疗项 | BR-MT-EXE-011 → EX-203 |
| 6 | Cmd | `ProcedureDomainService`：`create→start→ProcedureExecutionPerformerOrchestrator#registerPrimaryPerformer→complete`；`createCmd.orgId=loginTech` | 每条诊疗 | **写库点1**；与护士站执行同构挂 PRIMARY |
| 7 | Cmd | `DeviceDispenseNurseWorkflowOrchestrator#applyOutpatientNurseDeviceDispenses` + 条件 `InventoryItemDomainService#decreaseQuantitiesMerged` | 耗材项 + 可选房 | **写库点2**；库存不足 EX-204；并发 EX-205 |
| 8 | Asm | Response | | |

---

### 3.7 IF-MT-EXE-05 `batchCancelMedicalTechExecution`（批量取消医技执行）

**Request `BatchCancelMedicalTechExecutionRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | |
| `encounterScope` | String | Y | |
| `procedureItems` | List\<MedicalTechCancelProcedureItemDto\> | N | |
| `deviceItems` | List\<MedicalTechCancelDeviceItemDto\> | N | |

**DTO**

| 类 | 字段 | 说明 |
| --- | --- | --- |
| `MedicalTechCancelProcedureItemDto` | `procedureId` Long Y | 被对冲 COMPLETED |
| `MedicalTechCancelDeviceItemDto` | `dispenseId` Long Y | 发放行 |

**Response**：`successCount` / `message`

**失败码**：EX-MT-EXE-206、211～213（**EX-MT-EXE-214 预留不抛**）

**软落地口径（住院取消）**：`MedicalTechBilateralHomologySupport#assertTechCancelHomology` / 护士侧 `assertNurseCancelHomology` **均为空操作**（不抛 EX-214 / EX-IP-CXL-207）。待结算轨 **EX-MT-EXE-206** 禁止取消；出院确费 **EX-IP-ATD-309** 另卡未确费。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、勾选非空 | |
| 1b | Gate | `InpatientPendingSettlementClinicalGate` | 住院 statusEnum | PENDING/COMPLETED/DISCHARGED → **EX-206** |
| 2 | Pre | 就诊门闸 + 归属校验 | procedure 属本就诊且 `org_id`=loginTech | EX-211/212 |
| 3 | Pre | **若** inpatient → `assertTechCancelHomology`（空操作）；**若** outpatient → 跳过 | 待取消 COMPLETED | 软落地：不抛 EX-214 |
| 4 | Cmd | `ProcedureDomainService`：`create→start→registerPrimaryPerformer→cancel`（refund_id）；`createCmd.orgId=loginTech` | 每条 procedureId | **写库点1**；与执行/护士站取消同构挂 PRIMARY；不改 Charge |
| 5 | Cmd | `DeviceDispenseNurseWorkflowOrchestrator#revertOutpatientNurseDeviceDispenses` + 条件库存回补 | dispenseIds | **写库点2**；态不符 EX-213 |
| 6 | Asm | Response | | |

---

## 4. QueryService 与领域事件

| 写侧事件 | 读侧刷新 |
| --- | --- |
| Procedure 完成/取消 | IF-03 / 03B / 02 次数；工作量统计手动刷新 |
| 耗材发放态变更 | IF-02 耗材栏；IF-01 待发红点 |

队列 QueryService 以 **`pageMedicalTechWorkstationQueue`** 为准（**GAP-MT-EXE-001 已关闭**）；不得用护士站队列方法冒充（可见性轴不同）。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合顺序 |
| --- | --- | --- | --- | --- |
| 04 | `batchPerformMedicalTechExecution` | 6 | Procedure 完成链 create→start→挂 PRIMARY→complete（orgId=loginTech） | Procedure |
| 04 | 同上 | 7 | 耗材发放 Orchestrator → 条件库存 | DeviceDispense → InventoryItem |
| 05 | `batchCancelMedicalTechExecution` | 4 | Procedure 取消链 create→start→挂 PRIMARY→cancel（orgId=loginTech） | Procedure |
| 05 | 同上 | 5 | 耗材回退 Orchestrator → 条件库存 | DeviceDispense → InventoryItem |

### 5.2 事务边界 / 失败回滚

| IF | 策略 |
| --- | --- |
| 04 / 05 | 默认同一本地事务；任一步失败整批回滚 |
| 01～03B | 无写库 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 取消对冲 refund_id 唯一占用 | ProcedureDomainService 取消链 | §3.7 序号 4 |
| 耗材仅 PREPARATION 可执行推进 | DeviceDispenseNurseWorkflowOrchestrator | §3.6 序号 7 |

### 6.2 并发控制

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 发放态条件更新 | 发放 Orchestrator | §3.6 序号 7；失败 EX-205 |

### 6.3 重试与容错

| 项 | 说明 |
| --- | --- |
| 读 IF | 可重试 |
| 库存不足 / 未计费 | 不可盲目重试，须业务处理后重提 |

### 6.4 数据脱敏

| 项 | 说明 |
| --- | --- |
| 队列 | 仅工作站所需人口学字段，不含多余证件号明文扩展 |

### 6.5 审计日志

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| Procedure 执行人/取消原因 | Procedure 写链 | §3.6 序号 6；§3.7 序号 4 |
| 发放操作人 | 发放 Orchestrator | §3.6～3.7 耗材 Cmd |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 门诊 TECH 队列仅本科有活 | API | 是 | P0 |
| 住院切换后类别收窄 | API | 是 | P0 |
| 双活点开无护士站行 | API | 是 | P0 |
| 门诊未收费执行失败 | API | 是 | P0 |
| 住院医技执行不建诊疗 Charge、不抛 EX-202 | API | 是 | P0 |
| 医技/护士取消本侧均不互卡（同态废止） | API | 是 | P0 |
| 耗材开关开/关；执行单含 dispenseId | API | 是 | P0 |
| 取消对冲后再执行 | API | 是 | P1 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 执行单无 N+1 | 性能 | 抽测 | P1 |

### 7.3 查询结果一致性

| 用例 | 说明 |
| --- | --- |
| 红点与右侧待发一致 | 同轴过滤 |
| 分页字段 `content`/`totalCount` | 对齐 PagedResult |

### 7.4 集成测试

| 用例 | 说明 |
| --- | --- |
| 与医技计费联调（住院） | 先划价待发再执行发放 |
| 出院门禁 EX-IP-ATD-309 | 医技相关费用未确费拒绝 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 无阻塞 | Gap-001 已关；应用层已落地 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3.7 | 2026-10-17 | 耗材可见轴撤销「任意 TECH 互看」；与 AS **BR-MT-EXE-004** / `DeviceSupplyTechVisibilitySupport` 对齐；代码先行回写 |
| v1.3.6 | 2026-10-16 | IF-02 Request 增 `executionQueueFilter`；装载后按 **BR-MT-EXE-023** 收窄右侧诊疗/门诊耗材；代码先行回写 |
| v1.3.5 | 2026-10-14 | IF-01 增 `executionQueueFilter`（待执行/已执行整单分桶；门诊项目+耗材，住院仅项目；**BR-MT-EXE-023**）；代码先行回写 |
| v1.3.4 | 2026-10-12 | **BR-MT-EXE-022**：采血贴排除合管派生「采血费」（前端按名称过滤）；代码先行回写 |
| v1.3.3 | 2026-10-09 | IF-02 耗材可见性：`DeviceSupplyTechVisibilitySupport` 挂靠科室 TECH 祖先链与登录科对齐；代码先行回写 |
| v1.3.2 | 2026-10-07 | IF-01 `dischargeTime`；IF-04/05 待结算轨 Gate + EX-206；代码先行回写 |
| v1.3.1 | 2026-10-07 | IF-02 `selectable` 对齐 **BR-MT-EXE-018**（可执行或可取消；满额不灰勾选）；代码先行回写 |
| v1.3.0 | 2026-09-23 | IF-01 增 `inpatientStayFilter`（在院/不在院=已清床离院） |
| v1.2.2 | 2026-09-22 | 执行单无账单时单价/总价用目录现价预览（住院诊疗不等护士计费） |
| v1.2.1 | 2026-09-22 | 队列 DTO 补 phone/idCard 与住院病区床位/护理/诊断/余额字段 |
| v1.1.1 | 2026-09-21 | 补 §2.1.1 前端落点（单菜单三 Tab；V29） |
| v1.2.0 | 2026-09-21 | 协同 DD 软落地：取消同态废止；出院 EX-IP-ATD-309 改为确费门禁 |
| v1.1.0 | 2026-09-19 | 对齐已落地代码：实现阶段改为已落地；§2.2 Orchestrator + 既有方法名；分页 `content`/`totalCount`；行 DTO `dispenseId`；IF-04 去掉执行同态/EX-202；IF-05 tech-first、EX-214 预留；`org_id`=login TECH |
| v1.0.1 | 2026-09-19 | 对齐协同 DD：住院诊疗费认护士执行；医技执行不建 Charge；EX-202 改为同态失败（后由 v1.1.0 改为预留） |
| v1.0.0 | 2026-09-19 | 初版目标态：MedicalTechExecutionAppService；IF-01～05/03B；§2.3 双锚 |
