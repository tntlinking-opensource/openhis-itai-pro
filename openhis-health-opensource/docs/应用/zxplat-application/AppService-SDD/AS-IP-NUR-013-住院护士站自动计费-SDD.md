# 住院护士站 · 自动计费 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-013-SDD  
> 版本：v1.9 | 日期：2026-09-27 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · **住院记账 / 划价页** · **「自动计费」Tab** + **住院滚费**定时任务 |
| 实现阶段口径 | **应用层已落地** IF-IP-ARG-02～09（8 方法）；领域 GAP-IP-ARG-001～004 **已关闭**；**IF-IP-ARG-01** 复用 **IF-IP-PRC-01**（仅 `productTableNames=[wkf_activity_definition]`，**不**强制 `categoryCode=51`）；包 **`inpatient/nursestation/inpatientbilling`** **不新建** |
| 编排权威 | `AS-IP-NUR-013-住院护士站自动计费.md`（**v1.5.1**）§1.3、§4、§10 |
| 方法权威 | `DM-CUS-002` §6.1/§6.2/§9.2.1；`DM-WKF-004` §6.1.20/§6.2.25；`DM-ADM-011` §6.1.3b/§6.2.24；`DM-ADM-002` §6.2.21 |
| 严格对齐 | IF 编号 **IF-IP-ARG-01～09**，不得增删改号 |
| 宿主 AppService | 扩展 **`NurseStationInpatientBillingAppService`**（与 **AS-IP-NUR-005** 同 Bean）；HTTP 前缀仍为 **`nurseStationInpatientBilling`** |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 左侧患者树/列表 | **AS-IP-NUR-001** |
| 划价确费 / 医嘱计费写路径 | **AS-IP-NUR-005** |
| 取消执行编排本体 | **AS-IP-NUR-012**（本 Tab 仅复用 HTTP，**不**新建取消写 IF） |
| **复制模版** | 本期不做 |
| 转科/请假/欠费冻结 | v1 不做 |
| 定时调度框架选型 | 基础设施；本 SDD 仅定义 **`executeInpatientAutoRollJob`** 可被调度器 POST 调用的应用契约 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见范围 **BR-IP-NUR-003**；配置 IF 须 `requireVisibleSnapshot(..., EX_IP_ARG_201)`（划价 IF 仍用 `EX_IP_PRC_102`） |
| `AS-IP-NUR-005-SDD` | **IF-IP-ARG-01** 复用 **IF-IP-PRC-01** `pageNurseStationPricingCatalog`（仅诊疗表；写侧黑名单见 **BR-IP-ARG-003**）；同包复用 `EncounterContextReader`、`ClinicalCatalogPriceResolver` |
| `AS-IP-NUR-012` | 已滚床位费用取消复用 **IF-IP-CXL-01**（**BR-IP-ARG-012**）；本模块 **IF-IP-ARG-09** 仅提供读侧 `procedureId` |
| `AS-IP-NUR-006` | 费用明细对床位滚费展示「自动计费」（**BR-IP-CDT-018**） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| UI Tab 名称 | **自动计费** |
| 任务侧名称 | **住院滚费**；Charge `generate_source_enum = order-execute`；床位配置 SR `generate_source_enum = auto-roll-fees` |
| `ruleId` | Update/Delete/Activate/Suspend Command 与 Request 字段 **`ruleId` = 表 `cus_charge_item_auto_gen.id`**；**无** `rule_id` 列 |
| 可配置诊疗 | `trigger_source_table = wkf_activity_definition`；允许床位（51）及其他可日结类目；**禁止**护理（26）、转科（32）、出院（34）、嘱托（52） |
| 护理级别 | `category_code = 26`；**只读展示**，**禁止**写入 `ChargeItemAutoGen` |
| `billDate` | 滚费业务日 = 任务触发日 **前一日自然日**（**BR-IP-ARG-006**）；Tab 护理只读区默认 **当日** |
| HTTP | 一律 **`POST`** + `AppServiceControllerRegistrar` |
| 事务 | 配置写 IF-03～07 各为独立 AppService 调用，由 **AppServiceAspect** 单方法 UoW；IF-08 任务入口同 UoW，单条落账失败 **catch 后继续**（**禁止**业务代码 `@Transactional`） |
| 选品（IF-IP-ARG-01） | **复用 IF-IP-PRC-01**；传 `productTableNames=[wkf_activity_definition]`（**不**强制 `categoryCode`）；写侧 **BR-IP-ARG-003**；**禁止**新增选品 HTTP |
| 幂等 | 同 `billDate + serviceRequestId` 已有 `order-execute` ChargeItem → **跳过**（**BR-IP-ARG-010**） |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  inpatientbilling/
    service/NurseStationInpatientBillingAppService.java          # 扩展 8 个 public 方法（IF-IP-ARG-02～09）；IF-IP-ARG-01 复用 IF-IP-PRC-01
    request/
      ListAutoRollConfigsRequest.java
      CreateAutoGenRuleRequest.java
      UpdateAutoGenRuleRequest.java
      DeleteAutoGenRuleRequest.java
      ActivateAutoGenRuleRequest.java
      SuspendAutoGenRuleRequest.java
      ExecuteInpatientAutoRollJobRequest.java
    response/
      ListAutoRollConfigsResponse.java
      CreateAutoGenRuleResponse.java
      UpdateAutoGenRuleResponse.java
      DeleteAutoGenRuleResponse.java
      ActivateAutoGenRuleResponse.java
      SuspendAutoGenRuleResponse.java
      ExecuteInpatientAutoRollJobResponse.java
    dto/
      NurseStationAutoRollConfigRowDto.java
      NurseStationAutoRollNursingReadonlyDto.java
      AutoRollLineContext.java
      AutoRollChargeLineResolved.java
      AutoRollEncounterProcessResult.java
    assembler/
      NurseStationAutoRollListAssembler.java
      NurseStationAutoRollWriteAssembler.java
    mapper/
      NurseStationAutoRollMapper.java
      NurseStationAutoRollMapperImpl.java
    support/
      NurseStationAutoRollEncounterProcessor.java
      NurseStationAutoRollLinePoster.java

zxplat/health-application/src/main/java/com/openhis/health/application/
  orchestration/nursestation/
    InpatientAutoRollOrderExecutionOrchestrationSupport.java
    InpatientAutoRollServiceOrderExecuteCommand.java
  querysupport/nursestation/
    InpatientAutoRollEncounterContextLoader.java              # 含 primaryNurseId 批量补齐

zxplat/health-application/.../inpatientbilling/request/
  PageNurseStationPricingCatalogRequest.java                   # IF-IP-ARG-01 复用；增补 categoryCode（见 AS-IP-NUR-005-SDD §3.2）

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  InpatientNurseStationConstants.java                            # 增补 EX_IP_ARG_*
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.inpatientbilling`  
> **不新建** `autobilling` / `autoroll` 子包；与 **AS-IP-NUR-005** 共用 `NurseStationInpatientBillingAppService` Bean。

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentManagedWardLocationIdsResolver` | IF-02～07 可见范围（**BR-IP-NUR-003**） |
| `EncounterContextReader` | IF-02～07 `listInpatientEncounterContexts` |
| `CurrentOperatorResolver` | IF-03～07 写路径 `operatorId` |
| `ClinicalCatalogPriceResolvesupport | IF-03/04/08 入账价解析 |
| `ChargeItemAutoGenQueryService` | IF-02 配置列表；IF-08 生效规则 |
| `ChargeItemAutoGenDomainService` | IF-03～07 五写 |
| `ActivityDefinitionQueryService` | IF-03/04 category 校验；IF-02/08 展示名 |
| `ChargeItemDefinitionQueryService` | IF-02/03/04/08 定价校验与批量名称 |
| `ServiceRequestQueryService` | IF-02/08 护理级别解析 |
| `EncounterQueryService` | IF-08 在院 encounter 扫描 |
| `ChargeItemQueryService` | IF-08 幂等查询 |
| `ServiceRequestDomainService` | IF-03 `ensureAutoRollBedLongTermServiceRequest`（**仅新建配置**） |
| `ChargeItemDomainService` | IF-08 `createPlannedChargeItemFromOrderExecution`（滚费编排内） |
| `InpatientAutoRollOrderExecutionOrchestrationSupport` | IF-08 长期 SR 单槽位执行（IF-IP-EXE-02 同构） |
| `InpatientAutoRollEncounterContextLoader` | IF-08 在院上下文 + accountId + primaryNurseId |
| `NurseStationAutoRollListAssembler` | IF-02 配置 + 护理只读装配 |
| `NurseStationAutoRollWriteAssembler` | IF-08 滚费定价解析与 Charge 行构建 |
| `NurseStationAutoRollEncounterProcessor` | IF-08 单 encounter 床位+护理编排 |
| `NurseStationAutoRollLinePoster` | IF-08 单条 Request+Charge 落账（参与任务 UoW；失败 catch 不抛） |
| `NurseStationAutoRollMapper` | Request ↔ Command / 滚费 DTO 映射 |

> 定价行选取复用 **`ClinicalCatalogPriceResolveSupport#resolveCatalogUnitPrice`**（同 **BR-IP-PRC-017** / **BR-ORD-001**）。

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CurrentManagedWardLocationIdsResolver` | `listManagedWardLocationIdsOrEmpty` | 02～07 |
| `EncounterContextReader` | `listInpatientEncounterContexts` | 02～07 |
| `ActivityDefinitionQueryService` | `findAllByIds` | 02、03、04、08 |
| `ActivityDefinitionQueryService` | `getServiceCatalog` | 03、04（单条 category 校验） |
| `ChargeItemDefinitionQueryService` | `findAllDefinitionsByIdIn` | 02、03、04、08 |
| `ChargeItemDefinitionQueryService` | `findSaleItemByInstanceId` | 08（护理分支定价反查） |
| `ChargeItemAutoGenQueryService` | `findRulesByEncounterId` | 02、08（createdAt 索引，见 §3.9） |
| `ChargeItemAutoGenQueryService` | `findActiveRulesByEncounter` | 08 |
| `ServiceRequestQueryService` | `findLastEffectiveNursingLevelForAutoRoll` | 08 |
| `ServiceRequestQueryService` | `listNursingLevelsForAutoRollTab` | 02 |
| `ServiceRequestQueryService` | `findNursingLevelForAutoRollPostedCharges` | 09 |
| `EncounterQueryService` | `listInpatientAutoRollEncounterContexts` | 08 |
| `ChargeItemQueryService` | `existsAutoRollChargeItemForBillDate` | 08 |
| `ChargeItemAutoGenDomainService` | `createAutoGenRule` | 03 |
| `ChargeItemAutoGenDomainService` | `updateAutoGenRule` | 04 |
| `ChargeItemAutoGenDomainService` | `deleteAutoGenRule` | 05 |
| `ChargeItemAutoGenDomainService` | `activateAutoGenRule` | 06 |
| `ChargeItemAutoGenDomainService` | `suspendAutoGenRule` | 07 |
| `ServiceRequestDomainService` | `ensureAutoRollBedLongTermServiceRequest` | 03 |
| `ChargeItemDomainService` | `createPlannedChargeItemFromOrderExecution` | 08 |
| `InpatientAutoRollOrderExecutionOrchestrationSupport` | `executeLongTermServiceOrderSlot` | 08 |
| `ServiceRequestQueryService` | `findAutoRollBedServiceRequestIdByRuleId` | 08 |
| `ChargeItemQueryService` | `existsOrderExecuteChargeForServiceRequestBillDate` | 08 |
| `InpatientAutoRollEncounterContextLoader` | `listInpatientAutoRollEncounterContexts` | 08 |
| `ClinicalCatalogPriceResolvesupport | `resolveCatalogUnitPrice` | 03、04、08 |
| `NurseStationAutoRollMapper` | `toCreateAutoGenRuleCommand` 等 | 03～08 |
| `NurseStationAutoRollListAssembler` | `assembleConfigRows` / `assembleNursingReadonly` | 02 |
| `NurseStationAutoRollWriteAssembler` | `resolveAutoRollChargeLine` | 08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 配置状态 | `com.zxplat.domain.share.enums.PublicationStatus` | `DM-CUS-002` §3 `cus_charge_item_auto_gen.status_enum` | ACTIVE / INACTIVE / DISCARDED；IF-02 列表含 INACTIVE |
| 滚费 Charge 来源 | `com.zxplat.domain.share.enums.GenerateSource.ORDER_EXECUTE` | `DM-ADM-011` §3；**BR-IP-ARG-015** | 持久化码 `order-execute` |
| 床位配置 SR 来源 | `GenerateSource.AUTO_ROLL_FEES` | `DM-WKF-004` §6.1.20；**BR-IP-ARG-019** | 持久化码 `auto-roll-fees`；禁止手动执行 |
| 自动计费类目黑名单 | `InpatientNurseStationConstants.SERVICE_CATEGORY_NURSING/TRANSFER/DISCHARGE/ENTRUST`（26/32/34/52） | `DM-WKF-001` §3；**BR-IP-ARG-003** | AppService `assertAutoRollConfigurableActivity` |
| 护理级别类目 | `ServiceRequestCategoryConstants.SERVICE_CATEGORY_NURSING` | `DM-WKF-004` §3 `category_enum`；**BR-IP-ARG-009** | 值 `"26"` |
| 触发来源表 | `com.zxplat.domain.share.constants.TableNameConstants.WKF_ACTIVITY_DEFINITION` | `DM-CUS-002` §3 `trigger_source_table`；**BR-IP-ARG-003** | 配置固定此表名 |
| 费用状态（滚费产出） | `com.zxplat.domain.share.enums.ChargeItemStatus.PENDING_SETTLEMENT` | `DM-ADM-011` §5；**BR-IP-ARG-015** | 滚费 Charge 待结算 |
| 请求状态（滚费产出） | `com.zxplat.domain.share.enums.RequestStatus.VERIFIED` | `DM-WKF-004` §6.1.20；**BR-IP-ARG-015** | 滚费 Request 创建即 VERIFIED |
| 长临嘱（床位配置 SR） | `TherapyType.LONG_TERM` + `rateCode=QD` | `DM-WKF-004` §3；**BR-IP-ARG-019** | 配置 ensure 即 VERIFIED |
| 配置行状态（出参） | `NurseStationAutoRollConfigRowDto.statusEnum` | `DM-CUS-002` §3 `status_enum` | IF-02 列表 |
| 护理只读状态 | `NurseStationAutoRollNursingReadonlyDto.statusEnum` | `DM-WKF-004` §6.2.25 | VERIFIED / STOPPED |
| 业务消息码 | `InpatientNurseStationConstants.EX_IP_ARG_*` | `AS-IP-NUR-013` §5 | **201、301、302、401、501**（**已生成**）；选品失败复用 **EX-IP-PRC-101** |
| 配置创建时刻（滚费过滤） | `ChargeItemAutoGenRuleQueryModel.createdAt` | `DM-CUS-002` §9.2.1 step 4a；**BR-IP-ARG-013** | **已落地**：`ChargeItemAutoGenQueryService#toRuleModel` 回填 `createdAt` |
| 域异常 → 应用码 | `NurseStationInpatientBillingAppService#translateAutoGenBusinessException` | IF-03～07 | `CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT`→301；`NOT_FOUND`→401；`INVALID_STATUS`→501；`INVALID_ARGUMENT`→302 |

### 2.4 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-NUR-013` §3**（GAP-IP-ARG-001～004 **已关闭**）；本 SDD **禁止**维护 Gap 表。

**已落地域能力**：`findLastEffectiveNursingLevelForAutoRoll`、`listNursingLevelsForAutoRollTab`、`findNursingLevelForAutoRollPostedCharges`、`findAutoRollBedServiceRequestIdByRuleId`、`listInpatientAutoRollEncounterContexts`（含 `primaryNurseId`）、`existsOrderExecuteChargeForServiceRequestBillDate`、`ensureAutoRollBedLongTermServiceRequest`、`executeLongTermServiceOrderSlot`、`ChargeItemAutoGen` 五写五读。

**本 SDD 成码交付（已完成）**：`NurseStationInpatientBillingAppService` 扩展 **IF-IP-ARG-02～09**；`request/response/dto/assembler/mapper/support` 上表清单；`InpatientNurseStationConstants.EX_IP_ARG_*`（含 901/902）；`ChargeItemAutoGenRuleQueryModel.createdAt`；可见性门控自动计费路径使用 **EX-IP-ARG-201**。**IF-IP-ARG-01** 由 **AS-IP-NUR-005-SDD** `PageNurseStationPricingCatalogRequest.categoryCode` 透传支撑。取消已滚费用复用 **AS-IP-NUR-012**。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-ARG-01 | **`pageNurseStationPricingCatalog`**（**IF-IP-PRC-01 复用**） | 诊疗选品目录（自动计费） | `POST /api/app/nurseStationInpatientBilling/pageNurseStationPricingCatalog` |
| IF-IP-ARG-02 | `listAutoRollConfigs` | 自动计费配置列表 | `POST /api/app/nurseStationInpatientBilling/listAutoRollConfigs` |
| IF-IP-ARG-03 | `createAutoGenRule` | 新增自动计费配置 | `POST /api/app/nurseStationInpatientBilling/createAutoGenRule` |
| IF-IP-ARG-04 | `updateAutoGenRule` | 更新自动计费配置 | `POST /api/app/nurseStationInpatientBilling/updateAutoGenRule` |
| IF-IP-ARG-05 | `deleteAutoGenRule` | 删除自动计费配置 | `POST /api/app/nurseStationInpatientBilling/deleteAutoGenRule` |
| IF-IP-ARG-06 | `activateAutoGenRule` | 启用自动计费配置 | `POST /api/app/nurseStationInpatientBilling/activateAutoGenRule` |
| IF-IP-ARG-07 | `suspendAutoGenRule` | 停用自动计费配置 | `POST /api/app/nurseStationInpatientBilling/suspendAutoGenRule` |
| IF-IP-ARG-08 | `executeInpatientAutoRollJob` | 住院滚费定时任务 | `POST /api/app/nurseStationInpatientBilling/executeInpatientAutoRollJob` |
| IF-IP-ARG-09 | `listAutoRollPostedCharges` | 配置/护理已滚费用记录 | `POST /api/app/nurseStationInpatientBilling/listAutoRollPostedCharges` |

> **IF-IP-ARG-01** **不新增** App 方法；契约与编排序列见 **`AS-IP-NUR-005-SDD` §3.2 IF-IP-PRC-01**；自动计费 Tab 调用时须传 **`productTableNames=[wkf_activity_definition]`**（**不**强制 `categoryCode=51`）。  
> **取消已滚费用** **不新增** App 方法；复用 **`AS-IP-NUR-012`** `POST …/nurseStationOrderCancelExecute/cancelNurseStationOrderExecutions`。

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 分页 / 必填 / 可见性 / 数量合法性 | 抛 **`EX-IP-ARG-*`** 或域码映射，不进入 Cmd |
| **Pre** | QueryService 加载、category/定价校验、幂等判定 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | `ChargeItemAutoGenDomainService` / `ServiceRequestDomainService` / `ChargeItemDomainService` | 事务回滚见 §5 |
| **Asm** | Mapper + Assembler 纯 DTO | — |

记法：`ChargeItemAutoGenQueryService#findRulesByEncounterId` 表示注入 Bean 调用。

---

### 3.2 IF-IP-ARG-01 诊疗选品目录（复用 IF-IP-PRC-01）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/pageNurseStationPricingCatalog`（**与 IF-IP-PRC-01 相同**）

**本 IF 不展开独立 Request/Response/DTO**——字段表、编排序列、失败码均以 **`AS-IP-NUR-005-SDD` §3.2 IF-IP-PRC-01** 为准。

**自动计费 Tab 调用约定（在 IF-IP-PRC-01 之上叠加）**

| 项 | 约定 |
| --- | --- |
| **BR-IP-ARG-003** | Request 须传 **`productTableNames=[wkf_activity_definition]`**（仅诊疗，不含耗材）；**不**强制 `categoryCode`；写侧排除护理/转科/出院/嘱托 |
| 响应行 | 使用既有 **`NurseStationPricingCatalogRowDto`**；前端取 `activityId` 作 `triggerSourceId`、`chargeItemDefinitionId` 作配置锚点 |
| 失败码 | 分页/目录失败 → **`EX-IP-PRC-101`**（**不**定义 EX-IP-ARG-101） |
| 成码落点 | 前端过滤黑名单类目；写路径 **`assertAutoRollConfigurableActivity`** |

**领域编排序列**：**复用 IF-IP-PRC-01 编排序列 1～5**；自动计费调用仅收窄 `productTableNames`，类目黑名单在 IF-03/04 Gate。

---

### 3.3 IF-IP-ARG-02 `listAutoRollConfigs`（自动计费配置列表）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/listAutoRollConfigs`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 当前选中住院就诊 |
| `patientId` | `Long` | Y | 左侧卡片患者 id（可见性校验） |
| `billDate` | `LocalDate` | N | 业务日透传展示；**不裁剪** `nursingLevels`（按日筛费用在 IF-09） |
| `includeNurseOptions` | `Boolean` | N | 是否装载 `nurseOptions`；空/true=装载；**false=跳过**全量护士查询（刷新减负） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `configs` | `List<NurseStationAutoRollConfigRowDto>` | 配置列表（含 INACTIVE） |
| `nursingLevels` | `List<NurseStationAutoRollNursingReadonlyDto>` | 护理级别只读列表（可展开）；无匹配时 empty |
| `nursingLevelReadonly` | `NurseStationAutoRollNursingReadonlyDto` | 兼容字段：等同 `nursingLevels` 首行；无则为 `null` |
| `nurseOptions` | `List<OptionDto>` | 在职护士下拉（配置计费人） |

**DTO：`NurseStationAutoRollConfigRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ruleId` | `Long` | 规则主键（表 id） |
| `ruleName` | `String` | 规则名称 |
| `statusEnum` | `String` | ACTIVE / INACTIVE |
| `statusDictText` | `String` | 状态展示名 |
| `encounterId` | `Long` | 住院就诊 |
| `triggerSourceTable` | `String` | 触发来源表 |
| `triggerSourceId` | `Long` | 诊疗 activityId |
| `activityName` | `String` | 诊疗展示名 |
| `chargeDefinitionId` | `Long` | 目标定价 id |
| `chargeDefinitionName` | `String` | 定价展示名 |
| `quantityValue` | `BigDecimal` | 生成数量 |
| `performingOrgId` | `Long` | 执行科室；空=全院 |
| `priority` | `Integer` | 优先级 |
| `remark` | `String` | 备注 |
| `businessTime` | `LocalTime` | 滚费业务时刻（默认 23:59:59） |
| `billingPractitionerId` | `Long` | 滚费计费人 |
| `billingPractitionerName` | `String` | 计费人展示名 |

**DTO：`NurseStationAutoRollNursingReadonlyDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `serviceRequestId` | `Long` | 源护理医嘱 id |
| `activityId` | `Long` | 护理级别 activityId |
| `activityName` | `String` | 护理级别展示名 |
| `quantity` | `BigDecimal` | 数量（通常 1） |
| `unitCode` | `String` | 单位 code |
| `unitName` | `String` | 单位展示名 |
| `checkTime` | `LocalDateTime` | 校对时刻 |
| `statusEnum` | `String` | VERIFIED / STOPPED |
| `statusDictText` | `String` | 状态展示名 |
| `chargeDefinitionId` | `Long` | 解析出的默认定价 id（只读展示） |
| `chargeDefinitionName` | `String` | 定价展示名 |
| `billDate` | `LocalDate` | 本次解析使用的业务日 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-ARG-001** | encounter 须在护士站可见范围 |
| **BR-IP-ARG-005** | 护理区只读，**不**写入配置表 |
| **BR-IP-ARG-009** | 护理仅 `VERIFIED`；`STOPPED` 停嘱即退池（同配置 INACTIVE） |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-ARG-201** | 就诊不可见 / patientId 不匹配 |

**ListAssembler 方法签名**

- `assembleConfigRows(List<ChargeItemAutoGenRuleQueryModel> rules, Map<Long, ActivityDefinition> activityById, Map<Long, ChargeItemDefinition> definitionById, Long organizationId, Map<Long, String> billingPractitionerNameById): List<NurseStationAutoRollConfigRowDto>`
- `assembleNursingReadonly(Optional<InpatientAutoRollNursingLevelQueryModel> nursing, LocalDate billDate, Map<Long, ActivityDefinition> activityById, Map<Long, ChargeItemDefinition> definitionById, Long organizationId): NurseStationAutoRollNursingReadonlyDto`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `encounterId`、`patientId` 非空 | — |
| 2 | Pre | `EncounterContextReader#listInpatientEncounterContexts` + App `requireVisibleSnapshot` | 单 encounter + patientId | 不可见 → **EX-IP-ARG-201** |
| 3 | Pre | `ChargeItemAutoGenQueryService#findRulesByEncounterId` | encounterId | 含 INACTIVE；**BR-IP-ARG-018** 列表不过滤科室 |
| 4 | Pre | `ServiceRequestQueryService#listNursingLevelsForAutoRollTab` | encounterId（billDate 忽略） | 全住院期 VERIFIED+STOPPED；无匹配 → nursing 空 |
| 5 | Pre | `ActivityDefinitionQueryService#findAllByIds` | 配置 triggerSourceId + 护理 activityId 并集 | 批量展示名 |
| 6 | Pre | `ChargeItemDefinitionQueryService#findAllDefinitionsByIdIn` | 配置 chargeDefinitionId + 护理解析定价 id | 批量展示名 |
| 7 | Pre | `PractitionerQueryService#mapDisplayNameByEntererIds` + 护士岗位下拉 | 计费人姓名 + `nurseOptions` | **BR-IP-ARG-021** |
| 8 | Asm | `NurseStationAutoRollListAssembler#assembleConfigRows` | ③⑤⑥⑦ | 配置区（含 businessTime / 计费人） |
| 9 | Asm | `NurseStationAutoRollListAssembler#assembleNursingReadonly`（逐行） | ④⑤⑥ + billDate | `nursingLevels`；`nursingLevelReadonly`=首行 |
| — | — | — | — | **仅读路径** |

---

### 3.4 IF-IP-ARG-03 `createAutoGenRule`（新增自动计费配置）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/createAutoGenRule`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 住院就诊 |
| `patientId` | `Long` | Y | 可见性校验 |
| `ruleName` | `String` | Y | 规则名称 |
| `activityId` | `Long` | Y | 可日结诊疗 ActivityDefinition id |
| `chargeDefinitionId` | `Long` | Y | 目标定价 id（来自选品） |
| `quantityValue` | `BigDecimal` | N | 默认 **1** |
| `performingOrgId` | `Long` | N | 执行科室；空=全院 |
| `priority` | `Integer` | N | 默认 **0** |
| `remark` | `String` | N | 备注 |
| `businessTime` | `LocalTime` | N | 滚费业务时刻；空则 **23:59:59**（**BR-IP-ARG-021**） |
| `billingPractitionerId` | `Long` | N | 计费人；空则当前登录人（**BR-IP-ARG-019/021**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ruleId` | `Long` | 新建规则主键 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-ARG-001～004** | 可见性、可日结诊疗（排除 26/32/34/52）、quantity>0、定价有效 |
| **BR-IP-ARG-016** | 写操作带 operatorId |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-ARG-201** | 就诊不可见 |
| **EX-IP-ARG-301** | 组合键冲突（域 `CHARGE_ITEM_AUTO_GEN_IDENTITY_CONFLICT`） |
| **EX-IP-ARG-302** | 类目禁止（护理/转科/出院/嘱托）/ 定价无效 |

**Mapper 接口方法签名**

- `toCreateAutoGenRuleCommand(CreateAutoGenRuleRequest request, Long operatorId): CreateAutoGenRuleCommand`
- `toEnsureAutoRollBedLongTermServiceRequestCommand(CreateAutoGenRuleRequest request, Long ruleId, Long operatorId): EnsureAutoRollBedLongTermServiceRequestCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | 必填、quantity>0 | quantity 空则默认 1 |
| 2 | Pre | `requireVisibleSnapshot` | encounterId + patientId | → **EX-IP-ARG-201** |
| 3 | Pre | `assertAutoRollConfigurableActivity`（`getServiceCatalog`） | activityId | 护理/转科/出院/嘱托 → **EX-IP-ARG-302**；返回 categoryCode |
| 4 | Pre | `ChargeItemDefinitionQueryService#findAllDefinitionsByIdIn` | chargeDefinitionId | 缺失或软删 → **EX-IP-ARG-302** |
| 5 | Pre | `NurseStationAutoRollMapper#toCreateAutoGenRuleCommand` | Request + operatorId；`triggerSourceTable=wkf_activity_definition`、`triggerSourceId=activityId` | **无 id** |
| 6 | Cmd | `ChargeItemAutoGenDomainService#createAutoGenRule(CreateAutoGenRuleCommand)` | ⑤ Command | **写库点**；默认 ACTIVE；冲突 → **EX-IP-ARG-301** |
| 7 | Cmd | `ServiceRequestDomainService#ensureAutoRollBedLongTermServiceRequest` | Mapper ensure 命令 + `categoryEnum`=③；`based_on=ruleId`；`unitCode`=活动 **`packageUnitCode`** | **BR-IP-ARG-019**；LONG_TERM + QD + VERIFIED |
| 8 | Asm | Response | ⑥ 返回 id | `ruleId` |

---

### 3.5 IF-IP-ARG-04 `updateAutoGenRule`（更新自动计费配置）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/updateAutoGenRule`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ruleId` | `Long` | Y | = 表 id |
| `encounterId` | `Long` | Y | 住院就诊 |
| `patientId` | `Long` | Y | 可见性校验 |
| `ruleName` | `String` | N | 非空覆盖 |
| `activityId` | `Long` | N | 非空则更新 triggerSourceId |
| `chargeDefinitionId` | `Long` | N | 非空覆盖 |
| `quantityValue` | `BigDecimal` | N | 非空须 >0 |
| `performingOrgId` | `Long` | N | 可显式置空语义按 Request 约定（null=不覆盖） |
| `priority` | `Integer` | N | 非空覆盖 |
| `remark` | `String` | N | 非空覆盖 |
| `businessTime` | `LocalTime` | N | 非空覆盖（**BR-IP-ARG-021**） |
| `billingPractitionerId` | `Long` | N | 非空覆盖（**BR-IP-ARG-021**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 固定 `true` |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-ARG-201** | 就诊不可见 |
| **EX-IP-ARG-301** | 组合键冲突 |
| **EX-IP-ARG-302** | activity/定价无效 |
| **EX-IP-ARG-401** | 规则不存在（域 `CHARGE_ITEM_AUTO_GEN_NOT_FOUND`） |

**Mapper 接口方法签名**

- `toUpdateAutoGenRuleCommand(UpdateAutoGenRuleRequest request, Long operatorId): UpdateAutoGenRuleCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | ruleId 非空 | — |
| 2 | Pre | `requireVisibleSnapshot` | encounterId + patientId | → **EX-IP-ARG-201** |
| 3 | Pre | App 校验 | 若 `activityId` 非空则 **Pre 同 IF-03 序号 3**；若 `chargeDefinitionId` 非空则 **Pre 同 IF-03 序号 4** | 否则跳过对应 Pre |
| 4 | Pre | `NurseStationAutoRollMapper#toUpdateAutoGenRuleCommand` | 非空覆盖；activityId 非空时同步 `triggerSourceTable/Id` | — |
| 5 | Cmd | `ChargeItemAutoGenDomainService#updateAutoGenRule(UpdateAutoGenRuleCommand)` | ④ Command | **写库点**；**不**改启停 |
| 6 | Asm | Response | — | `success=true` |

---

### 3.6 IF-IP-ARG-05 `deleteAutoGenRule`（删除自动计费配置）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/deleteAutoGenRule`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ruleId` | `Long` | Y | = 表 id |
| `encounterId` | `Long` | Y | 住院就诊 |
| `patientId` | `Long` | Y | 可见性校验 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 固定 `true` |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-ARG-201** | 就诊不可见 |
| **EX-IP-ARG-401** | 规则不存在 |

**Mapper 接口方法签名**

- `toDeleteAutoGenRuleCommand(DeleteAutoGenRuleRequest request, Long operatorId): DeleteAutoGenRuleCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | ruleId 非空 | — |
| 2 | Pre | `requireVisibleSnapshot` | encounterId + patientId | → **EX-IP-ARG-201** |
| 3 | Pre | `NurseStationAutoRollMapper#toDeleteAutoGenRuleCommand` | ruleId + operatorId | — |
| 4 | Cmd | `ChargeItemAutoGenDomainService#deleteAutoGenRule(DeleteAutoGenRuleCommand)` | ③ Command | **写库点** 软删（**BR-IP-ARG-017**） |
| 5 | Asm | Response | — | `success=true` |

---

### 3.7 IF-IP-ARG-06 `activateAutoGenRule`（启用自动计费配置）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/activateAutoGenRule`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ruleId` | `Long` | Y | = 表 id |
| `encounterId` | `Long` | Y | 住院就诊 |
| `patientId` | `Long` | Y | 可见性校验 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 固定 `true` |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-ARG-201** | 就诊不可见 |
| **EX-IP-ARG-401** | 规则不存在 |
| **EX-IP-ARG-501** | 当前非 INACTIVE（域 `CHARGE_ITEM_AUTO_GEN_INVALID_STATUS`） |

**Mapper 接口方法签名**

- `toActivateAutoGenRuleCommand(ActivateAutoGenRuleRequest request, Long operatorId): ActivateAutoGenRuleCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | ruleId 非空 | — |
| 2 | Pre | `requireVisibleSnapshot` | encounterId + patientId | → **EX-IP-ARG-201** |
| 3 | Pre | `NurseStationAutoRollMapper#toActivateAutoGenRuleCommand` | ruleId + operatorId | — |
| 4 | Cmd | `ChargeItemAutoGenDomainService#activateAutoGenRule(ActivateAutoGenRuleCommand)` | ③ Command | **写库点** INACTIVE→ACTIVE |
| 5 | Asm | Response | — | 状态不允许 → **EX-IP-ARG-501** |

---

### 3.8 IF-IP-ARG-07 `suspendAutoGenRule`（停用自动计费配置）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/suspendAutoGenRule`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ruleId` | `Long` | Y | = 表 id |
| `encounterId` | `Long` | Y | 住院就诊 |
| `patientId` | `Long` | Y | 可见性校验 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `Boolean` | 固定 `true` |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-ARG-201** | 就诊不可见 |
| **EX-IP-ARG-401** | 规则不存在 |
| **EX-IP-ARG-501** | 当前非 ACTIVE |

**Mapper 接口方法签名**

- `toSuspendAutoGenRuleCommand(SuspendAutoGenRuleRequest request, Long operatorId): SuspendAutoGenRuleCommand`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | ruleId 非空 | — |
| 2 | Pre | `requireVisibleSnapshot` | encounterId + patientId | → **EX-IP-ARG-201** |
| 3 | Pre | `NurseStationAutoRollMapper#toSuspendAutoGenRuleCommand` | ruleId + operatorId | — |
| 4 | Cmd | `ChargeItemAutoGenDomainService#suspendAutoGenRule(SuspendAutoGenRuleCommand)` | ③ Command | **写库点** ACTIVE→INACTIVE |
| 5 | Asm | Response | — | 状态不允许 → **EX-IP-ARG-501** |

---

### 3.9 IF-IP-ARG-08 `executeInpatientAutoRollJob`（住院滚费定时任务）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/executeInpatientAutoRollJob`

> 由基础设施 **00:05** 调度 POST 调用；**无**护士站 UI 按钮。须已绑定租户上下文。

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `billDate` | `LocalDate` | N | 滚费业务日；空则 **触发日减 1 自然日**（**BR-IP-ARG-006**） |
| `operatorId` | `Long` | N | 系统操作人；空则取 `CurrentOperatorResolver` 或租户系统账号 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `billDate` | `LocalDate` | 实际处理的业务日 |
| `scannedEncounterCount` | `Integer` | 扫描 encounter 数 |
| `processedEncounterCount` | `Integer` | 至少落账 1 条的 encounter 数 |
| `createdChargeItemCount` | `Integer` | 新建 ChargeItem 条数 |
| `skippedCount` | `Integer` | 幂等/边界跳过条数 |
| `failedCount` | `Integer` | 单条落账失败数（**不**阻断其它 encounter） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-ARG-006～015、018** | 见 AS §1.6 |
| **BR-IP-ARG-007** | 入院日未跨午夜 → 跳过该 encounter 当日 |
| **BR-IP-ARG-008** | 已分床未清床候选 + **记进不计出**（`end_time` 自然日 ≤ billDate 跳过） |
| **BR-IP-ARG-009** | 护理仅 `VERIFIED`；停嘱即不滚 |
| **BR-IP-ARG-010** | 幂等跳过，**不**抛错 |
| **BR-IP-ARG-011** | **不**校验与手工划价重复 |

**Mapper / WriteAssembler / Support 方法签名**

- `NurseStationAutoRollWriteAssembler#resolveAutoRollChargeLine(Long chargeDefinitionId, Long activityId, BigDecimal quantity, Long organizationId): AutoRollChargeLineResolved`
- `NurseStationAutoRollWriteAssembler#resolveNursingChargeDefinitionId(Long activityId): Long`
- `NurseStationAutoRollEncounterProcessor#processEncounter(InpatientAutoRollEncounterQueryModel ctx, LocalDate billDate, Long operatorId, Long performerId): AutoRollEncounterProcessResult`
- `NurseStationAutoRollLinePoster#postLine(AutoRollLineContext line, Long operatorId, Long performerId): Outcome`
- `InpatientAutoRollOrderExecutionOrchestrationSupport#executeLongTermServiceOrderSlot(InpatientAutoRollServiceOrderExecuteCommand command): Outcome`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 解析 | `billDate` 缺省规则（**BR-IP-ARG-006**） | 00:00～00:15 前一日；否则当日 |
| 2 | Pre | `InpatientAutoRollEncounterContextLoader#listInpatientAutoRollEncounterContexts` | — | 含 accountId、**primaryNurseId** |
| 3 | Loop | **对每个 encounter 上下文 `ctx`** | App Gate 4～6 后调用 Processor | 单 encounter 异常 catch 后继续 |
| 4 | Gate | App 入院边界 | `ctx.startTime.toLocalDate().isAfter(billDate)` | 跳过本 encounter |
| 5 | Gate | App 入院跨午夜（**BR-IP-ARG-007**） | 入院日 + 触发早于次日 00:00 | 补跑 Gate |
| 5a | Gate | App 出院日（**BR-IP-ARG-008** 记进不计出） | `end_time` 自然日不晚于 billDate | 跳过本 encounter（床位+护理） |
| 6 | Gate | App 账户 | `ctx.accountId == null` | 跳过本 encounter |
| 7 | Pre | `defaultPerformerId = ctx.primaryNurseId ?? operatorId` | — | 护理分支与存量空计费人回落（**BR-IP-ARG-019**） |
| 8 | Loop | **床位 ACTIVE 规则** | Processor 内 | 查已有 SR → 幂等 → execute slot |
| 9 | Pre | `ServiceRequestQueryService#findAutoRollBedServiceRequestIdByRuleId` | ruleId | **无 SR 计 failedCount**；**不** ensure |
| 10 | Pre | `existsOrderExecuteChargeForServiceRequestBillDate` | serviceRequestId + billDate | **BR-IP-ARG-010** |
| 11 | Cmd | `InpatientAutoRollOrderExecutionOrchestrator#executeLongTermServiceOrderSlot` | 长期 SR + billDate + `occurrenceTime` + performer | 配置项：`occurrence`/`recorded` = `billDate` + 规则 `business_time`；计费人=`billing_practitioner_id`（空回落⑦）；Procedure 内部 **create→start→挂主执行人→complete**（DM-CLN-004）；**BR-IP-ARG-015/019/021** |
| 12 | Pre/Cmd | **护理分支** | `findLastEffectiveNursingLevelForAutoRoll` 仅 **VERIFIED** | **STOPPED 不命中**；时刻写死日末；人用⑦；重复 10～11 |
| 13 | Asm | `ExecuteInpatientAutoRollJobResponse` | 汇总计数 | — |

---

### 3.10 IF-IP-ARG-09 `listAutoRollPostedCharges`（配置/护理已滚费用记录）

**HTTP**：`POST /api/app/nurseStationInpatientBilling/listAutoRollPostedCharges`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 住院就诊主键 |
| `patientId` | `Long` | Y | 患者主键 |
| `ruleId` | `Long` | 条件 | 床位配置展开：`cus_charge_item_auto_gen.id`；与 `serviceRequestId` **二选一** |
| `serviceRequestId` | `Long` | 条件 | 护理行展开：护理级别医嘱主键；与 `ruleId` **二选一** |
| `dateFrom` | `LocalDate` | N | occurrence 自然日起（含）；空=不限 |
| `dateTo` | `LocalDate` | N | occurrence 自然日止（含）；空=不限 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ruleId` | `Long` | 回显配置主键（护理展开时为 null） |
| `serviceRequestId` | `Long` | 床位滚费长期 SR 或护理医嘱 SR |
| `charges` | `List<NurseStationAutoRollPostedChargeRowDto>` | occurrence 倒序 |

**NurseStationAutoRollPostedChargeRowDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | `Long` | 收费明细主键 |
| `procedureId` | `Long` | 取消执行锚点；可空 |
| `occurrenceTime` | `LocalDateTime` | 业务发生时间（配置项=`billDate`+规则 `business_time`；护理=`billDate` 日末；与 Procedure.`recorded_time` 同值） |
| `quantityValue` | `BigDecimal` | 数量 |
| `quantityUnit` | `String` | 单位 code |
| `unitPrice` / `totalPrice` | `BigDecimal` | 行级 6 位金额 |
| `statusEnum` / `statusDisplay` | `String` | ChargeItemStatus |
| `cancelable` | `Boolean` | 待结算且 `procedureId` 非空 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | encounterId/patientId 非空；`ruleId` 与 `serviceRequestId` 恰好其一 | 否则 → **EX-IP-ARG-201** |
| 2 | Pre | `requireVisibleSnapshot` | → **EX-IP-ARG-201** |
| 3a | Pre | 床位：`findRulesByEncounterId` 匹配 ruleId | 无 → **EX-IP-ARG-401** |
| 4a | Pre | 床位：`findAutoRollBedServiceRequestIdByRuleId` | 无 → **EX-IP-ARG-902** |
| 3b | Pre | 护理：`findNursingLevelForAutoRollPostedCharges` | 无 → **EX-IP-ARG-401** |
| 5 | Pre | `listOrderExecuteChargesByServiceRequestId`（可选日期） | 异常 → **EX-IP-ARG-901** |
| 6 | Asm | `NurseStationAutoRollListAssembler#assemblePostedChargeRows` | 含 `cancelable` |

> **取消写路径**：前端对 `cancelable=true` 行调用 **AS-IP-NUR-012** `cancelNurseStationOrderExecutions`（`procedureIds`）；**不**经本 IF。

## 4. QueryService 与领域事件

| 读侧 | 写侧事件 | 读侧刷新 |
| --- | --- | --- |
| IF-02 配置列表 | `ChargeItemAutoGenEditedEvent`（IF-03～07） | 前端刷新 **IF-IP-ARG-02**；**不**触发即时滚费 |
| IF-02 护理只读 | — | 随医嘱校对/停嘱变化；Tab 进入时现查 |
| IF-09 已滚费用 | CXL-01 取消执行（物理删待结算 order-execute） | 前端刷新本配置/护理展开列表；**AS-IP-NUR-006** 同步不再展示该行 |
| IF-08 滚费落账 | `createPlannedChargeItemFromOrderExecution`（Charge **`order-execute`**） | **AS-IP-NUR-006** 费用明细（来源展示「自动计费」） |
| IF-03 ensure SR | `AutoRollServiceRequestCreatedEvent`（SR `generate_source=auto-roll-fees`） | 配置 SR 自 **AS-IP-NUR-004** 执行列表剔除（**BR-IP-ARG-019**） |

**QueryService 语义摘要**

| 方法 | 语义 |
| --- | --- |
| `findRulesByEncounterId` | 未软删全量配置，priority/id 升序 |
| `findActiveRulesByEncounter` | ACTIVE + 科室匹配（**BR-CAG-002**） |
| `findLastEffectiveNursingLevelForAutoRoll` | billDate 当日最后校对护理级别（滚费 IF-08） |
| `listNursingLevelsForAutoRollTab` | Tab 护理只读列表（billDate 空=全住院期） |
| `findNursingLevelForAutoRollPostedCharges` | 护理展开门控（本就诊 VERIFIED/STOPPED） |
| `listInpatientAutoRollEncounterContexts` | 分床后、清床前 IMP + 账户 + **primaryNurseId** |
| `existsOrderExecuteChargeForServiceRequestBillDate` | 同日同 SR 的 order-execute 幂等 |
| `listOrderExecuteChargesByServiceRequestId` | 同 SR 的 order-execute Charge 列表（可选日期；床位/护理共用） |

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 序号对照

| IF | App 方法 | Cmd 序号 | 领域写调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-IP-ARG-03 | `createAutoGenRule` | §3.4-6～7 | `createAutoGenRule` → `ensureAutoRollBedLongTermServiceRequest` | ChargeItemAutoGen → ServiceRequest |
| IF-IP-ARG-04 | `updateAutoGenRule` | §3.5-5 | `updateAutoGenRule` | ChargeItemAutoGen |
| IF-IP-ARG-05 | `deleteAutoGenRule` | §3.6-4 | `deleteAutoGenRule` | ChargeItemAutoGen 软删 |
| IF-IP-ARG-06 | `activateAutoGenRule` | §3.7-4 | `activateAutoGenRule` | ChargeItemAutoGen 状态 |
| IF-IP-ARG-07 | `suspendAutoGenRule` | §3.8-4 | `suspendAutoGenRule` | ChargeItemAutoGen 状态 |
| IF-IP-ARG-08 | `executeInpatientAutoRollJob` | §3.9-9～12 | `executeLongTermServiceOrderSlot` | Procedure（create→start→挂主执行人→complete）→ ChargeItem（**order-execute**） |

### 5.2 事务边界 / 失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-03～07 | **AppServiceAspect** 单方法 UoW | 任一步失败整单回滚 |
| IF-08 单条 | 任务入口 **AppServiceAspect** UoW 内顺序落账；`postLine` **catch 后不抛** | 单条失败记日志/计数，不阻断同批其它 rule/encounter |
| IF-08 任务级 | `executeInpatientAutoRollJob` 由 **AppServiceAspect** 包裹 | 未捕获异常则整任务回滚；已 catch 的单条失败不影响批次继续 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 滚费幂等键 | `ChargeItemQueryService#existsAutoRollChargeItemForBillDate` | §3.9 序号 11、18 |
| 配置组合键 | 域 `createAutoGenRule` / `updateAutoGenRule` 仓储 exists | §3.4-6、§3.5-5 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 单租户任务串行 | 基础设施调度策略 | §3.9 全文 |
| 配置写乐观 | 单 rule 单事务 | §3.4～3.8 |

### 6.3 重试与容错

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 单 encounter 失败隔离 | `executeInpatientAutoRollJob` try/catch per ctx | §3.9 序号 3 |
| 单条落账失败隔离 | `NurseStationAutoRollLinePoster#postLine` catch 不抛 + 任务层计数 | §3.9 序号 13 |
| 任务可重跑 | 幂等跳过已落账行 | §3.9 序号 11 |

### 6.4 数据脱敏

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 配置/护理 DTO 无余额/费别 | DTO 字段裁剪 | §3.3 |

### 6.5 审计日志

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 配置写 operatorId | `Create/Update/Delete/Activate/Suspend*Command.operatorId` | §3.4～3.8 |
| 滚费 operatorId | `CreateFromAutoRollCommand.operatorId` | §3.9-13 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| 选品仅诊疗表 + 写侧黑名单 | **IF-IP-PRC-01** 仅 activity 表；护理/转科/出院/嘱托保存 **302** | 集成 | 后端 | P0 |
| 配置 CRUD + 启停 | 组合键冲突 **301**；软删后列表不可见 | 集成 | 后端 | P0 |
| 护理只读不入配置表 | IF-02 有 nursing 区但 POST create 不含护理 trigger | 集成 | 后端 | P0 |
| 滚费床位 | ACTIVE 配置 quantity；INACTIVE 不参与 | 集成 | 后端 | P0 |
| 滚费护理 | 仅 VERIFIED；STOPPED 停嘱即不滚；换级取最后 VERIFIED | 集成 | 后端 | P0 |
| 幂等重跑 | 同 billDate 不重复 ChargeItem | 集成 | 后端 | P0 |
| 入院跨午夜 | 入院当日未跨日不计费 | 集成 | 后端 | P0 |
| 分床～清床 | 无床轨迹 encounter 不扫描 | 集成 | 后端 | P0 |

### 7.2 非功能测试

| 用例 | 预期 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| 在院 batch 扫描 | 无 N+1 批量定价/名称 | 性能 | 后端 | P1 |
| 00:05 调度 | 基础设施触发 POST IF-08 | 运维 | 手工 | P1 |

### 7.3 QueryService 一致性

| 用例 | 预期 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| 配置写后刷列表 | IF-02 见最新状态 | 集成 | 后端 | P0 |
| 滚费后费用明细 | generate_source=auto-roll-fees | 集成 | 后端 | P0 |

### 7.4 集成测试

| 用例 | 预期 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| POST `pageNurseStationPricingCatalog`（IF-IP-ARG-01） | 仅诊疗表；写侧拒绝 26/32/34/52 | 集成 | 后端 | P0 |
| POST `listAutoRollConfigs` … `listAutoRollPostedCharges` … `executeInpatientAutoRollJob` | 自动计费 **8 个**新增 IF 路径与方法名一致 | 集成 | 后端 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 集成测试待执行 | 应用层已成码；P0 用例待 CI/手工集成环境跑通 | P1 |
| 调度未绑定 | 00:05 POST `executeInpatientAutoRollJob` 待基础设施配置 | P1 |

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.10 | 2026-10-16 | 对齐 **AS v1.6.0**：IF-02 `nursingLevels` + `listNursingLevelsForAutoRollTab`；IF-09/`BR-IP-ARG-020` 支持护理 `serviceRequestId` 与日期闭区间 |
| v1.9 | 2026-09-27 | **BR-IP-ARG-015 / IF-08**：`executeLongTermServiceOrderSlot` 改为 **create→start→挂主执行人→complete**；EX-IP-EXE-301 复用 COMPLETED 时不再挂执行人；对齐 **AS v1.5.1** / **DM-CLN-004** / 划价 **AS-IP-NUR-005 v2.18** |
| v1.8 | 2026-08-13 | 对齐 **AS v1.5.0**：出院日 Gate（记进不计出）；护理仅 `VERIFIED`（停嘱即退池） |
| v1.7 | 2026-08-12 | 对齐 **AS v1.4.0**：配置 `business_time`/`billing_practitioner_id`；IF-02/03/04/08 与 **BR-IP-ARG-015/019/021** 同步 |
| v1.6 | 2026-08-08 | **BR-IP-ARG-003** 放开可日结诊疗；黑名单护理/转科/出院/嘱托；ensure `categoryEnum` 取自选品；选品不强制 51 |
| v1.5 | 2026-07-17 | **IF-IP-ARG-09**；**BR-IP-ARG-012** 改复用 CXL-01；取消写不新增 IF；费用明细「自动计费」展示 |
| v1.4 | 2026-07-09 | 对齐 **AS v1.1.1**：ensure SR **仅 IF-03**；IF-08 删除 lazy ensure；§3.9 序号 9 改为只读 `findAutoRollBedServiceRequestIdByRuleId`；移除滚费 Mapper ensure 重载 |
| v1.3 | 2026-07-09 | 滚费改 IF-IP-EXE-02 执行轨；床位 ensure LONG_TERM SR；Charge `order-execute` 幂等 |
| v1.2.1 | 2026-06-19 | 事务口径对齐框架：**AppServiceAspect** UoW；移除 per-line `@Transactional(REQUIRES_NEW)` 表述 |
| v1.2 | 2026-06-19 | **应用层已成码**；§2 增补 `support/` 与滚费 DTO；§5.2 事务策略；Mapper/Processor 签名与代码对齐；§2.3 移除「目标态待生成」 |
| v1.1 | 2026-06-19 | **IF-IP-ARG-01** 改为复用 **IF-IP-PRC-01**（`categoryCode=51`）；删除 `pageAutoRollBedServiceCatalog` 及目录专用 DTO/Assembler；本 SDD 新增方法降为 **7 个**（IF-ARG-02～08） |
| v1.0 | 2026-06-19 | 首版：对齐 **AS-IP-NUR-013 v1.0.3**；IF-IP-ARG-01～08；扩展 **`NurseStationInpatientBillingAppService`**（**不新建包**）；域 GAP-IP-ARG-001～004 已关 |

---

> **关联文档**  
> AS：`health/AppService/AS-IP-NUR-013-住院护士站自动计费.md`  
> 同页宿主 SDD：`health/AppService-SDD/AS-IP-NUR-005-住院护士站划价-SDD.md`  
> DM：`DM-CUS-002`、`DM-WKF-004`、`DM-ADM-011`、`DM-ADM-002`
