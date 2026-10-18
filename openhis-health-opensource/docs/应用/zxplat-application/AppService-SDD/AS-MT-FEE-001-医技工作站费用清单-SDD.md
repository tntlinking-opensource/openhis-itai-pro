# 医技工作站 · 费用清单 · 应用服务详细设计（SDD）

> **编码**：AS-MT-FEE-001-SDD · **版本**：v1.2.1 · **日期**：2026-10-07  
> **上级 AS**：`health/AppService/AS-MT-FEE-001-医技工作站费用清单.md`  
> **实现阶段口径**：**目标态全量契约**（应用层由本 SDD 驱动生成；**禁止**在本文件写 Gap）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 医技工作站 · 费用清单 |
| **边界** | IF-MT-FEE-01～02；仅登录 TECH 相关 ChargeItem；可切门诊/住院；**不是**全院费用 |
| **编排权威** | AS-MT-FEE-001 §2 / §4 |
| **方法权威** | `DM-ADM-011` ChargeItem QueryService；打印绑定见 `AS-BAS-PRT-001` |
| **严格对齐** | 可见性轴与 AS-MT-EXE / 可见性 DD 同科过滤；打印数据集不得扩成全院 |
| **实现阶段口径** | 目标态全量契约 |

### 1.2 术语口径

| 术语 | 口径 |
| --- | --- |
| **相关费用** | 满足任一：① 锚诊疗 `ServiceRequest.org_id` = 登录 TECH；② 锚耗材发放房 `Location.organizationId` = 登录 TECH；③ 回落 `ChargeItem.requesting_org_id`（或等价执行科室字段）= 登录 TECH |
| **字段口径** | Long 主键不转 Number；优先 **list**（就诊内费用行通常有限）；若后续分页须用 `PagedResult` 的 `content`/`totalCount` |
| **QueryService 口径** | 按就诊装载 ChargeItem 后内存过滤；禁止 App 直查 Repository |
| **事务口径** | 读 IF 无写库；打印仅触发打印运行时（副作用在 PrintLog） |
| **HTTP** | 一律 `POST /api/app/medicalTechFeeList/{methodName}` |

---

## 2. 代码结构

### 2.1 包路径

```text
com.zxplat.domain.application.medicaltech.feelist/
  service/MedicalTechFeeListAppService.java
  mapper/MedicalTechFeeListMapper.java
  mapper/MedicalTechFeeListMapperImpl.java
  request/
    ListMedicalTechFeeItemsRequest.java
    PrintMedicalTechFeeListRequest.java
  response/
    ListMedicalTechFeeItemsResponse.java
    PrintMedicalTechFeeListResponse.java
  dto/
    MedicalTechFeeItemRowDto.java
```

> 公共库 `OptionDto` / 打印 Prepare 响应 DTO 等不在本模块新建。

### 2.1.1 前端落点（openhis-pro-ui）

| 项 | 值 |
| --- | --- |
| 菜单 | 同 EXE：`MedicalTechWorkstation` · Tab「费用清单」· `V29` |
| 页 | `views/medical-tech/workstation/fee-list/fee-list-panel.vue` |
| HTTP | `api/medical-tech/medical-tech-fee-list.*` → `/medicalTechFeeList/*` |
| 打印场景 | `scenarioCode` 待配置（FE 未配则禁用打印） |

### 2.2 依赖注入与领域方法白名单

| 依赖 | 用途 |
| --- | --- |
| `CurrentManagedOrganizationIdsResolver` / `OrganizationQueryService` | TECH 登录门禁 |
| `EncounterQueryService` | 就诊门闸、scope 类别匹配 |
| `ChargeItemQueryService` | 按就诊装载费用行 |
| `ChargeItemDomainService` | IF-03 `stampMedicalTechFeeConfirmation` |
| `ServiceRequestQueryService` | 判定诊疗锚点 org_id |
| `DeviceRequestQueryService` / `DeviceDispenseQueryService` / `LocationQueryService` | 判定耗材房上级 = loginTech |
| `MedicalTechFeeListMapper` | Asm |
| 打印运行时（`PrintJobAppService#preparePrint` 或既有绑定 Facade） | IF-02 触发 `AS-BAS-PRT-001` |

**领域方法白名单**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| EncounterQueryService | `mustGetEncounter`（既有等价） | 01、02 |
| ChargeItemQueryService | `listChargeItemsByEncounterId`（既有等价） | 01、02 |
| ServiceRequestQueryService | `listServiceRequestsByEncounter` / `mapListModelsByIds`（既有等价） | 01 |
| DeviceRequestQueryService | `listDeviceRequestsByEncounter` / `mapListModelsByIds`（既有等价） | 01 |
| DeviceDispenseQueryService | `listDeviceDispensesByEncounter` / `listDeviceDispensesByIds`（既有等价） | 01 |
| LocationQueryService | `loadLocationEntityMapByIds`（既有等价） | 01 |
| OrganizationQueryService | `loadOrganizationByIdForDisplay`（既有等价） | 01、02 Gate |
| PrintJobAppService（或既有打印 Facade） | `preparePrint`（对齐 AS-BAS-PRT-001 IF-BAS-PRT-31） | 02 |
| MedicalTechFeeListMapper | `toFeeItemRows` / `toListResponse` / `toPrintPrepareRequest` 等 | 01、02 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 scope | `ListMedicalTechFeeItemsRequest.encounterScope` | DM-ADM-002 §3 | `OUTPATIENT`/`INPATIENT` |
| 组织分类 TECH | `com.zxplat.domain.share.enums.OrganizationClassification` | DM-ADM-004 §3 `class_enum` | 登录科须 TECH |
| 收费状态 | `MedicalTechFeeItemRowDto.chargeStatusEnum` | DM-ADM-011 §5 | 展示用 code |
| 收费 service 表锚 | `MedicalTechFeeItemRowDto.serviceTable` / `serviceId` | DM-ADM-011 §3 `service_table`/`service_id` | 关联 SR/DR |
| 请求组织 | `ChargeItem.requestingOrgId`（或工程等价字段） | DM-ADM-011 §3 请求/执行科室 | 过滤回落③ |
| 打印场景码 | `PrintMedicalTechFeeListRequest.scenarioCode` | AS-BAS-PRT-001 BR-PRT-JOB-001 | 与 busNo 二选一 |

---

## 3. 按接口 ID 命名展开

> **先读各 IF「领域编排序列」**，按序号执行。Pre/Gate 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-MT-FEE-01 | `listMedicalTechFeeItems` | 查询医技费用清单 | `POST /api/app/medicalTechFeeList/listMedicalTechFeeItems` |
| IF-MT-FEE-02 | `printMedicalTechFeeList` | 打印医技费用清单 | `POST /api/app/medicalTechFeeList/printMedicalTechFeeList` |
| IF-MT-FEE-03 | `confirmMedicalTechFees` | 一键确费 | `POST /api/app/medicalTechFeeList/confirmMedicalTechFees` |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | TECH / scope / 参数 | 不再后续 |
| **Pre** | 只读装载与过滤 | 抛业务异常 |
| **Cmd** | IF-03 写确费戳；打印仅调用打印运行时 | 确费见 EX-MT-FEE-301；打印见 EX-MT-FEE-201 |
| **Asm** | Mapper 装配 | — |

---

### 3.2 IF-MT-FEE-01 `listMedicalTechFeeItems`（查询医技费用清单）

**Request `ListMedicalTechFeeItemsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `encounterScope` | String | Y | `OUTPATIENT` / `INPATIENT` |

**Response `ListMedicalTechFeeItemsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `rows` | List\<MedicalTechFeeItemRowDto\> | 本科相关费用行 |
| `sumAmount` | BigDecimal | 行金额合计 |

**DTO `MedicalTechFeeItemRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | Long | 收费明细主键 |
| `itemName` | String | 项目名称 |
| `itemCode` | String | 项目编码 |
| `quantity` | BigDecimal | 数量 |
| `unitPrice` | BigDecimal | 单价 |
| `totalPrice` | BigDecimal | 行金额 |
| `chargeStatusEnum` | String | 收费状态码 |
| `chargeStatusEnumText` | String | 收费状态展示（`PENDING_SETTLEMENT`→「已划价待结算」） |
| `serviceTable` | String | 锚点表 |
| `serviceId` | Long | 锚点请求主键 |
| `pricedTime` | LocalDateTime | 划价时间（`occurrence_time`，护士执行入账） |
| `occurredTime` | LocalDateTime | 收费时间（`entered_date`，真正结算戳；待结算常空） |
| `relatedOrgDisplay` | String | 相关科室展示（可选） |
| `feeConfirmerId` | Long | 确费人主键；空=未确费 |
| `feeConfirmerName` | String | 确费人展示名；未确费为空 |
| `feeConfirmedAt` | LocalDateTime | 确费时刻 |
| `billingPractitionerId` | Long | 计费人主键（Procedure 主执行人 / DeviceDispense 发放·配药人；否则 Request.requester_id；**非** enterer_id） |
| `billingPractitionerName` | String | 计费人展示名 |
| `medicalTechPerformerId` | Long | 医技执行人主键（本站 TECH 净剩 COMPLETED 主执行人 / DeviceDispense.performer_id；未执行空） |
| `medicalTechPerformerName` | String | 医技执行人展示名 |
| `medicalTechExecutedTime` | LocalDateTime | 医技执行时间（Procedure.recorded_time / DeviceDispense.dispense_time） |

**关键校验**：登录 TECH；`encounterScope` 合法；就诊类别匹配 scope。

**失败码**：EX-MT-FEE-101

**Mapper**：`toFeeItemRows(...): List<MedicalTechFeeItemRowDto>`；`toListResponse(List, BigDecimal): ListMedicalTechFeeItemsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、encounterId、scope | 非 TECH / 非法 → EX-MT-FEE-101 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | encounterId | 类别须匹配 scope |
| 3 | Pre | `ChargeItemQueryService#listChargeItemsByEncounterId` | encounterId | 装载本就诊全部费用 |
| 4 | Pre | `ServiceRequestQueryService` / `DeviceRequestQueryService` + Dispense + `LocationQueryService` | 批量 | 构建「相关」判定索引；禁止 N+1 |
| 5 | Pre | 内存过滤 | loginTechOrgId | BR-MT-FEE-002：① SR.org_id **或** ② 发放房组织 **或** ③ requesting_org_id |
| 6 | Pre | `ChargeItemActivityOperatorResolver` + Request.requester 回落 + `PractitionerQueryService` | 相关行 | 确费人/计费人展示名批量 enrich |
| 7 | Asm | `MedicalTechFeeListMapper#toListResponse` | rows + sum | 仅读路径 |

---

### 3.3 IF-MT-FEE-02 `printMedicalTechFeeList`（打印医技费用清单）

**Request `PrintMedicalTechFeeListRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `encounterScope` | String | Y | |
| `chargeItemIds` | List\<Long\> | N | 勾选子集；空则=当前过滤全集 |
| `scenarioCode` | String | N | 与打印 AS 对齐；与 busNo 二选一策略由打印运行时定 |

**Response `PrintMedicalTechFeeListResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceId` | String | 打印追踪号（prepare 返回） |
| `message` | String | 提示 |
| `succeeded` | Boolean | 是否成功触发 prepare |

**关键校验**：同 IF-01 Gate；勾选 id 必须属于本科相关过滤结果（BR-MT-FEE-003）。

**失败码**：EX-MT-FEE-201（打印失败）；查询门禁失败可复用 EX-MT-FEE-101

**Mapper**：`toPrintPrepareRequest(...): PreparePrintRequest`（或工程既有打印入参类型）

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、encounterId、scope | |
| 2 | Pre | 复用 IF-01 序号 2～5 | 过滤后相关行 | 禁止扩成全院 |
| 3 | Pre | **若** chargeItemIds 非空 → 取交集；**否则** 用全集 | BR-003 | |
| 4 | Asm | `MedicalTechFeeListMapper#toPrintPrepareRequest` | PrintData | 组装本科相关数据集 |
| 5 | Cmd | `PrintJobAppService#preparePrint`（既有） | scenarioCode/busNo | **写库点**：Print 运行时侧（本模块无 Charge 写）；失败 → EX-MT-FEE-201 |
| 6 | Asm | Response | traceId | |

---

### 3.4 IF-MT-FEE-03 `confirmMedicalTechFees`（一键确费 · 仅住院）

**Request `ConfirmMedicalTechFeesRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `encounterScope` | String | Y | **仅** `INPATIENT`（`OUTPATIENT` → EX-MT-FEE-301「门诊无确费业务」） |

**Response `ConfirmMedicalTechFeesResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `succeeded` | Boolean | 是否成功 |
| `confirmedCount` | Integer | 本次新打戳行数（已确费跳过不计） |
| `message` | String | 提示 |

**关键校验**：登录 TECH；**scope=INPATIENT**；确费人为当前医务人员。

**失败码**：EX-MT-FEE-301

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | TECH、encounterId、**INPATIENT** | 门诊 → EX-MT-FEE-301 |
| 2 | Pre | 复用 IF-01 相关过滤 | loginTech | BR-MT-FEE-002 |
| 3 | Pre | 筛 `feeConfirmerId == null` | 相关行 | 空则幂等成功 count=0 |
| 4 | Cmd | `ChargeItemDomainService#stampMedicalTechFeeConfirmation` | confirmer + now + ids | 写 fee_confirmer_*；不改状态 |
| 5 | Asm | Response | confirmedCount | |

---

## 4. QueryService 与领域事件

| 写侧事件 | 读侧刷新 |
| --- | --- |
| 医技计费/护士执行产生 Charge | 用户手动刷新 IF-01 |
| IF-03 确费写行字段 | 用户刷新 IF-01；出院门禁读字段 |
| 打印 prepare/confirm | 打印日志页（AS-BAS-PRT） |

本模块不消费领域事件自动推送。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合顺序 |
| --- | --- | --- | --- | --- |
| 03 | `confirmMedicalTechFees` | 4 | `stampMedicalTechFeeConfirmation` | ChargeItem |
| 02 | `printMedicalTechFeeList` | 5 | 打印 `preparePrint` | Print 运行时（无 Charge 写） |
| 01 | `listMedicalTechFeeItems` | — | 无 | — |

### 5.2 事务边界 / 失败回滚

| IF | 策略 |
| --- | --- |
| 01 | 无写库 |
| 02 | 打印准备失败不写业务库；不回滚 Charge |
| 03 | `@Transactional`；领域写失败整批回滚 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 重复打开清单 | 纯读 | §3.2 |
| 重复点打印 | 每次新 traceId（打印 AS 口径） | §3.3 序号 5 |

### 6.2 并发控制

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 无业务写锁 | — | 读路径 |

### 6.3 重试与容错

| 项 | 说明 |
| --- | --- |
| 查询失败 | 可重试 EX-MT-FEE-101 |
| 打印失败 | 可重试 EX-MT-FEE-201 |

### 6.4 数据脱敏

| 项 | 说明 |
| --- | --- |
| 清单行 | 仅工作站所需费用字段，不扩展证件号等 |

### 6.5 审计日志

| 项 | 代码位置 | 关联步骤 |
| --- | --- | --- |
| 打印审计 | AS-BAS-PRT confirmPrint / PrintLog | §3.3 序号 5 之后由打印闭环 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 仅本科相关费用出现 | API | 是 | P0 |
| 他科/护士注射费用不出现 | API | 是 | P0 |
| 门诊/住院 scope 类别正确 | API | 是 | P0 |
| 合计 = 行金额之和 | API | 是 | P0 |
| 打印数据集不扩全院 | API | 是 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 过滤无 N+1 | 性能 | 抽测 | P1 |

### 7.3 查询结果一致性

| 用例 | 说明 |
| --- | --- |
| 与医技执行可见轴同科 | SR.org / 耗材房组织一致 |

### 7.4 集成测试

| 用例 | 说明 |
| --- | --- |
| 医技划价后清单可见耗材费 | 联调 AS-MT-CHG |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 无阻塞 | 领域读能力复用既有；应用层目标态 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2.1 | 2026-10-07 | IF-01 行 DTO 补 `medicalTechPerformer*` / `medicalTechExecutedTime`（BR-MT-FEE-006） |
| v1.2.0 | 2026-09-23 | IF-01 行 DTO 补 `feeConfirmerName` / `billingPractitioner*`；计费人解析口径 |
| v1.1.0 | 2026-09-22 | IF-03 **确费仅住院**；Request.scope 仅 INPATIENT；对齐 AS v1.2.0 |
| v1.0.1 | 2026-09-21 | 补 §2.1.1 前端落点；打印场景码待种子 |
| v1.0.0 | 2026-09-19 | 初版目标态：MedicalTechFeeListAppService；IF-MT-FEE-01 list + IF-02 print；相关费用三条件过滤；打印对齐 AS-BAS-PRT |
