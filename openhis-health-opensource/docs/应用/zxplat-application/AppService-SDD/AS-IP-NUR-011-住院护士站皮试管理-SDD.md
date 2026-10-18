# 住院护士站 · 皮试管理 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-011-SDD  
> 版本：v1.5.0 | 日期：2026-07-22 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · **皮试管理** Tab |
| 实现阶段口径 | **目标态全量契约**（IF-IP-STK-01～04）；应用层 `inpatient/nursestation/skintest`，AppService **`NurseStationSkinTestAppService`**，HTTP 前缀 **`nurseStationSkinTest`** |
| 编排权威 | `AS-IP-NUR-011-住院护士站皮试管理.md`（**v1.5.0**）§1.3、§4 |
| 方法权威 | `DM-CLN-001` §6.1；`DM-WKF-004`；`DM-CLN-002`（Procedure 只读）；`AS-IP-DOR-003` **BR-IP-ORD-024**；执行计费 **AS-IP-NUR-004** |
| 严格对齐 | IF **IF-IP-STK-01、02、03、04** |
| 读侧方案 | **方案 A**：`MedicationRequestQueryService` + `ServiceRequestQueryService` + `AllergyIntoleranceQueryService` + `ProcedureQueryService` + `MedicationDispenseQueryService`（批号候选）；**禁止** App 注入 Repository |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 医生站皮试 SR 联动 | **AS-IP-DOR-003 · IF-IP-DOR-14**（**BR-IP-ORD-024**） |
| 皮试检查执行/计费 | **AS-IP-NUR-004**（普通临嘱） |
| nurse-pricing / Tab 划价 | **已废止** |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-DOR-003` | 医生联动 doctor-prescription 皮试检查 SR |
| `AS-IP-NUR-003` | 皮试药 / 皮试检查须校对 |
| `AS-IP-NUR-004` | 执行皮试检查 → Procedure + Charge；跨批提示 **BR-IP-EXE-034** |
| `AS-IP-NUR-001` | 患者点选 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 皮试检查 SR | **`generate_source_enum = doctor-prescription`**，由 **IF-IP-DOR-14** 联动；本 Tab **只读**；执行/计费在 **IF-IP-EXE** |
| 过敏记录锚 | `AllergyIntolerance.request_id` = **doctor** 皮试检查 `ServiceRequest.id` |
| 已执行门禁 | companion SR 存在净剩 **COMPLETED** Procedure（与取消对冲规则同 **AS-IP-NUR-004**） |
| 批号候选 | 皮试药 `MedicationRequest` 下 `MedicationDispense.lot_number` 去重（**非**库存可用批） |
| HTTP | 一律 **`POST`** |
| 事务 | **IF-IP-STK-03** 仅过敏记录写；App **禁止** `@Transactional` |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  skintest/
    service/NurseStationSkinTestAppService.java
    request/ …
    response/ …
    dto/ …
    assembler/ …
    mapper/ …
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `MedicationRequestQueryService` | 皮试药列表/单条（**VERIFIED**） |
| `ServiceRequestQueryService` | 索引 **doctor-prescription** 皮试检查 SR（`based_on → medReq`，可多 companion） |
| `ActivityDefinitionQueryService` | `requireIdByExactName(SKIN_TEST_INSPECTION_ACTIVITY_NAME)` |
| `AllergyIntoleranceQueryService` / `AllergyIntoleranceDomainService` | 读/写过敏记录 |
| `ProcedureQueryService` | `executedFlag`、保存门禁（净剩 COMPLETED） |
| `MedicationDispenseQueryService` | 批号候选 `listByMedReqIds`；意向库房映射 |

> **废止注入**：`InventoryItemQueryService`（批号候选，v1.5）；`ChargeItemDomainService#priceByNurseStation`、`ChargeItemQueryService`（本 Tab）、`NurseStationInpatientBillingCatalogAssembler` / `NurseStationInpatientBillingMapper`（皮试路径）。

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `MedicationRequestQueryService` | `listMedicationRequestsByEncounterForDoctorStation` + `filterVerifiedSkinTestMedications` | 02、03 |
| `ServiceRequestQueryService` | `listServiceRequestsByGenerateSource(encounterId, DOCTOR_PRESCRIPTION)` + 索引 `based_on` 皮试检查 SR（**List**） | 02、03 |
| `ProcedureQueryService` | 按 request 判定净剩 COMPLETED（`listNetCompleted…` 或等价） | 02、03 |
| `MedicationDispenseQueryService` | `listByMedReqIds`（批号候选）；`mapMedicationRequestToPrimaryDispenseLocationId`（展示） | 02、03 |
| `AllergyIntoleranceDomainService` | `saveSkinTestRecord` / `deleteSkinTestRecord` | 03、04 |

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | HTTP |
| --- | --- | --- |
| IF-IP-STK-01 | `loadNurseStationSkinTestInit` | `POST …/loadNurseStationSkinTestInit` |
| IF-IP-STK-02 | `listNurseStationSkinTestRows` | `POST …/listNurseStationSkinTestRows` |
| IF-IP-STK-03 | `saveNurseStationSkinTestRecord` | `POST …/saveNurseStationSkinTestRecord` |
| IF-IP-STK-04 | `deleteNurseStationSkinTestRecord` | `POST …/deleteNurseStationSkinTestRecord` |

### 3.3 IF-IP-STK-02 `listNurseStationSkinTestRows`

**列表过滤（应用层）**

1. `filterVerifiedSkinTestMedications`（**BR-IP-STK-002**）
2. `indexDoctorPrescriptionSkinTestServiceRequests`：`listServiceRequestsByGenerateSource(DOCTOR_PRESCRIPTION)` 中 activity=皮试检查 且 `based_on_table=med_medication_request` → **Map medReqId → List&lt;SR&gt;**
3. **仅保留**存在 doctor 联动 SR 的皮试药行（**BR-IP-STK-009**）
4. **展开**：一行对应一条 companion SR（同一皮试药可多行）
5. 左连接 `AllergyIntolerance`（`request_id = doctor SR.id`）；`readOnlyFlag = allergyRecordId != null`
6. 批量装配 **`executedFlag`**（companion SR 净剩 COMPLETED）
7. 批号候选：`MedicationDispense` 按 `med_req_id` 去重 `lot_number`

**DTO 字段**

| 字段 | 说明 |
| --- | --- |
| `skinTestServiceRequestId` | doctor-prescription 皮试检查 SR id（行主键） |
| `executedFlag` | 皮试检查是否已执行（可登记） |
| `readOnlyFlag` | 已登记则 true（前端禁改保存） |
| `lotNumberOptions` | 发放批号候选 |

### 3.4 IF-IP-STK-03 `saveNurseStationSkinTestRecord`

**领域编排序列（首次/更新）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 可见性 + 患者一致 + `InpatientPendingSettlementClinicalGateSupport` | **BR-IP-STK-001、011、012**；`PENDING`/`COMPLETED`/`DISCHARGED` → **208** |
| 2 | Pre | 皮试药 **VERIFIED** + `skinTestFlag` | 否则 **202** |
| 3 | Pre | 解析锚定皮试药的 **doctor-prescription** companion SR | 请求可带 `skinTestServiceRequestId`；多 companion 须显式指定；不存在 → **207** |
| 4 | Pre | Procedure 净剩 COMPLETED | 未执行 → **209**（**BR-IP-STK-004**） |
| 5 | Pre | 批号必填且 ∈ 发放候选 | 否则 **210**（**BR-IP-STK-013**） |
| 6 | Cmd | `AllergyIntoleranceDomainService#saveSkinTestRecord` | **写库点**；`requestId`=doctor SR id |
| 7 | Asm | 响应 | `allergyRecordId` + `skinTestServiceRequestId` |

> **废止步骤**（v1.4）：`priceByNurseStation`、定价解析、`firstTimePriced`、ChargeItem 幂等 Pre。  
> **废止步骤**（v1.0）：`createFromNursePricing`。  
> **废止**（v1.5）：库存可用批作批号候选。

**Response**

| 字段 | 说明 |
| --- | --- |
| `skinTestServiceRequestId` | **doctor-prescription** 皮试检查 SR id |
| `allergyRecordId` | 过敏记录主键 |

### 3.5 IF-IP-STK-04 `deleteNurseStationSkinTestRecord`

校验可见性与记录归属 → `deleteSkinTestRecord`；**不**校验 ChargeItem；**不**删 doctor SR；**不**冲费用。

## 4. QueryService 与领域事件

| 事件 | 发布相位 | 消费方 |
| --- | --- | --- |
| `AllergyIntoleranceRecordedEvent` | IF-IP-STK-03 | 医生站 `skinTestOutcome*` |
| `AllergyIntoleranceRemovedEvent` | IF-IP-STK-04 | 同上 |

## 5. 写库顺序（核心写接口视角）

**IF-IP-STK-03（单事务）**

1. `cln_allergy_intolerance`（`request_id` → doctor 皮试检查 SR）
2. 事务提交后发布领域事件

> **不**写入 `adm_charge_item`、`wkf_service_request`、`cln_procedure`。

## 6. 非功能性需求实现

| 类别 | 实现承诺 |
| --- | --- |
| 门禁 | 保存前须 companion SR 已执行（**EX-IP-STK-209**）；批号 ∈ 发放候选（**EX-IP-STK-210**） |
| 并发 | 领域写事务 |

## 7. 测试要点

| 类别 | 要点 |
| --- | --- |
| 准入 | 无 doctor SR → **207** / 不进列表；未执行 → **209**；批号非法 → **210** |
| 多行 | 同一皮试药多 companion → 多列表行；保存须带目标 `skinTestServiceRequestId` |
| 保存 | **不**产生 ChargeItem；仅过敏记录 |
| 医生站 | 保存后 `skinTestOutcomeText` 更新（经 doctor SR；最新覆盖由医生站侧实现） |
| 执行轨 | companion 进 IF-IP-EXE-01；计费在执行路径 |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.5.0 | 2026-07-22 | **批号**：候选改 `MedicationDispense`；必填 **BR-IP-STK-013** / **EX-IP-STK-210**；**多 companion 分行**；`readOnlyFlag`；保存可选 `skinTestServiceRequestId` |
| v1.4.0 | 2026-07-22 | **去特殊化**：废止 Tab 划价；`ProcedureQueryService` 已执行门禁；`executedFlag`；对齐 **AS v1.4** |
| v1.3.0 | 2026-07-17 | **执行互斥收窄**（**v1.4 废止住院互斥**） |
| v1.2.0 | 2026-07-11 | **BR-IP-STK-012**：§3.4 Gate 增补就诊态；**EX-IP-STK-208** |
| v1.1.0 | 2026-06-18 | 废止 nurse-pricing；锚 doctor SR 计费（**v1.4 再废止 Tab 计费**） |
| v1.0.2 | 2026-06-10 | 成码对齐 v1.0 |
| v1.0 | 2026-06-10 | 初版（nurse-pricing 路径，**已由 v1.1 废止**） |

