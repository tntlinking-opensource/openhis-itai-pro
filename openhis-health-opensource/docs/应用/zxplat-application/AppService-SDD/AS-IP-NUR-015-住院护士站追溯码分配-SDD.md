# 住院护士站 · 追溯码分配 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-015-SDD  
> 版本：v1.2 | 日期：2026-07-08 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · **追溯码分配** Tab |
| 实现阶段口径 | **目标态全量契约**（IF-IP-TRC-01～04）；应用层 `inpatient/nursestation/traceassignment`，AppService **`NurseStationTraceAssignmentAppService`**，HTTP 前缀 **`nurseStationTraceAssignment`**；**`PharmacyTraceCodeResolvesupport** 按 AS 决议**已就绪** |
| 编排权威 | `AS-IP-NUR-015-住院护士站追溯码分配.md`（**v1.1**）§1.3、§4 |
| 方法权威 | `DM-MED-002` §6.1.16（写 **`assignTraceNoToCompletedDispense`**）；§6.2.19～20；`DM-WKF-005` §6.2.2；`DM-WKF-006` §6.2.11 |
| 严格对齐 | IF **IF-IP-TRC-01～04** |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 逐行住院发药补扫 | **AS-IP-PHA-001** |
| SupplyDelivery / TraceNumberRecord 写 | **AS-IP-PHA-002** 核发已写 OUT |
| 医保 3505 | 非目标 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见 encounter、患者点选 |
| `AS-IP-PHA-002` | 核发 COMPLETED + Supply 码池 |
| `AS-OP-PHA-001` | 追溯批量解析交互同族 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 汇总组键 | `summaryNo + medicationId + lotNumber`（**BR-IP-TRC-004**） |
| 码池 | 同 `summaryNo` 下 **SupplyDelivery.trace_no** 并集（逗号拆分） |
| 写库 | **仅** `med_medication_dispense.trace_no` UPDATE（**BR-IP-TRC-009**） |
| HTTP | 一律 **`POST`** |
| 事务 | **IF-IP-TRC-04** App **`@Transactional`** 覆盖全组 Cmd |
| 追溯码长度 | **20**（与前端 **`TRACE_CODE_LENGTH`** 一致） |

> **Gap 仅登记于 AS `AS-IP-NUR-015` §3**；本 SDD **禁止**维护 Gap 表。

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  traceassignment/
    service/
      NurseStationTraceAssignmentAppService.java
    request/
      PagePatientsPendingTraceAssignmentRequest.java
      ListPatientTraceAssignmentGroupsRequest.java
      ResolveTraceCodesForPatientRequest.java
      SavePatientTraceAssignmentRequest.java
    response/
      PagePatientsPendingTraceAssignmentResponse.java
      ListPatientTraceAssignmentGroupsResponse.java
      ResolveTraceCodesForPatientResponse.java
      SavePatientTraceAssignmentResponse.java
    dto/
      PatientTraceAssignmentQueueRowDto.java
      PatientTraceAssignmentGroupDto.java
      PatientTraceAssignmentGroupSubmitDto.java
      TraceCodeMatchPreviewDto.java
      TraceCodeResolveFailItemDto.java
    assembler/
      PatientTraceAssignmentListAssembler.java
      PatientTraceAssignmentWriteAssembler.java
      SummaryTracePoolAssembler.java
    mapper/
      NurseStationTraceAssignmentMapper.java
      NurseStationTraceAssignmentMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/pharmacy/dispense/support/
  PharmacyTraceCodeResolveSupport.java
  ResolveTraceCodesCommand.java
  ResolveTraceCodesResult.java
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.traceassignment`

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径操作人 |
| `CurrentManagedWardLocationIdsResolver` | 病区可见 encounter（**BR-IP-TRC-001**） |
| `EncounterContextReader` | IF-01 队列 encounter 回补 |
| `PageWindowGate` | 分页校验 |
| `MedicationDispenseQueryService` | IF-01/02 Pre；写前强加载 |
| `SupplyRequestQueryService` | IF-02：`listMedicationSummarySlipLinesByBusNo` |
| `SupplyDeliveryQueryService` | IF-02/03/04：码池 **`selectByRequestIdList`** |
| `MedicationDefinitionQueryService` / `ProductDefinitionBatchLoader` | 药品展示、码数换算 |
| `MedicationDispenseDomainService` | IF-04：**`assignTraceNoToCompletedDispense`**（目标态） |
| `PharmacyTraceCodeResolvesupport | IF-03 批量匹配（**已就绪**） |
| `PatientTraceAssignmentListAssembler` | 聚合组、队列 |
| `SummaryTracePoolAssembler` | summaryNo → 码池 Set |
| `PatientTraceAssignmentWriteAssembler` | Submit → Command |
| `NurseStationTraceAssignmentMapper` / `MapperImpl` | DTO 装配 |

### 2.2 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| 读 | `MedicationDispenseQueryService#pageEncountersPendingTraceAssignment` | 01 |
| 读 | `MedicationDispenseQueryService#listPatientTraceAssignmentAggregateLines` | 02 |
| 读 | `MedicationDispenseQueryService#listMedicationDispensesByIds` | 04 Pre |
| 读 | `MedicationDispenseQueryService#mustGetMedicationDispense` | 04 Pre |
| 读 | `SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo` | 02、03、04（summary → supplyRequestId） |
| 读 | `SupplyDeliveryQueryService#selectByRequestIdList` | 02、03、04 |
| 读 | `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered` | 01 Asm |
| 支持 | `PharmacyTraceCodeResolveSupport#resolveTraceCodes` | 03 |
| 写 | `MedicationDispenseDomainService#assignTraceNoToCompletedDispense` | 04 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus#COMPLETED` | `DM-MED-002` §2.2、`status_enum` | IF-02/04 门控 |
| 供应类型 | `com.zxplat.domain.share.enums.SupplyType#MEDICATION_SUMMARY_ORDER` | `DM-WKF-006` type **27** | 码池关联 summary |
| 追溯码字段 | `MedicationDispense.traceNo` / 表 `trace_no` | `DM-MED-002` §3 `trace_no` | 唯一写库点 |
| Supply 码池 | `SupplyDelivery.traceNo` | `DM-WKF-005` §3 `trace_no` | **只读** |
| 业务异常码 | `com.zxplat.domain.share.constants.InpatientNurseStationConstants.EX_IP_TRC_*` | `AS-IP-NUR-015` §5 | **目标态待生成** |
| 追溯码长度 | `PatientTraceAssignmentWriteAssembler.TRACE_CODE_LENGTH`（20） | `AS-IP-NUR-015` **BR-IP-TRC-014** | 与门诊前端同族 |

## 3. 按接口 ID 命名展开

> **编排约定**：Gate → Pre → Cmd → Asm；Pre 失败**禁止**进入 Cmd。先读完各 IF **领域编排序列**再实现。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-TRC-01 | `pagePatientsPendingTraceAssignment` | 分页查询待追溯分配患者 | `POST /api/app/nurseStationTraceAssignment/pagePatientsPendingTraceAssignment` |
| IF-IP-TRC-02 | `listPatientTraceAssignmentGroups` | 加载患者药批汇总待分配行 | `POST /api/app/nurseStationTraceAssignment/listPatientTraceAssignmentGroups` |
| IF-IP-TRC-03 | `resolveTraceCodesForPatient` | 批量解析追溯码 | `POST /api/app/nurseStationTraceAssignment/resolveTraceCodesForPatient` |
| IF-IP-TRC-04 | `savePatientTraceAssignment` | 保存患者追溯码分配 | `POST /api/app/nurseStationTraceAssignment/savePatientTraceAssignment` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、分页、登录 | 不再调用后续步骤 |
| **Pre** | 可见性、状态、码池、码数 | 抛业务异常，**禁止 Cmd** |
| **Cmd** | 领域写 `assignTraceNoToCompletedDispense` | 事务回滚 §5 |
| **Asm** | Mapper 纯装配 | — |

### 3.2 IF-IP-TRC-01 `pagePatientsPendingTraceAssignment`（分页查询待追溯分配患者）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `Integer` | Y | 页码 |
| `pageSize` | `Integer` | Y | 页大小 |
| `keyword` | `String` | N | 姓名/床号/就诊号 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `Long` | 总数 |
| `rows` | `List<PatientTraceAssignmentQueueRowDto>` | 队列行 |

**PatientTraceAssignmentQueueRowDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 ID |
| `patientId` | `Long` | 患者 ID |
| `patientName` | `String` | 姓名 |
| `bedName` | `String` | 床号 |
| `pendingGroupCount` | `Integer` | 待分配汇总组数 |

**失败码**

| 码 | 说明 |
| --- | --- |
| EX-IP-TRC-101 | 无权 |
| EX-IP-TRC-102 | 查询失败 |

**Mapper**

- `NurseStationTraceAssignmentMapper#toPendingTraceAssignmentPageResponse(Page<...>, Map<...>): PagePatientsPendingTraceAssignmentResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate#requireValidPage` | pageNo/pageSize | — |
| 2 | Pre | `CurrentManagedWardLocationIdsResolver#resolve` | — | 管辖病区 |
| 3 | Pre | `MedicationDispenseQueryService#pageEncountersPendingTraceAssignment` | 病区 + 分页 | 待分配就诊队列 |
| 4 | Pre | `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered` | encounterIds | 展示字段 |
| 5 | Asm | `NurseStationTraceAssignmentMapper#toPendingTraceAssignmentPageResponse` | — | 仅读 |

### 3.3 IF-IP-TRC-02 `listPatientTraceAssignmentGroups`（加载患者药批汇总待分配行）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 就诊 ID |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groups` | `List<PatientTraceAssignmentGroupDto>` | 汇总组 |

**PatientTraceAssignmentGroupDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groupKey` | `String` | 稳定键：`summaryNo:medicationId:lotNumber` |
| `summaryNo` | `String` | 汇总单号 |
| `medicationId` | `Long` | 药品 ID |
| `medicationName` | `String` | 药品名 |
| `specification` | `String` | 规格 |
| `lotNumber` | `String` | 批号 |
| `dispenseQuantitySplit` | `BigDecimal` | 拆零发药量 |
| `splitUnitCode` | `String` | 拆零单位 |
| `dispenseIds` | `List<Long>` | 底层发放行 id |
| `assignedTraceNo` | `String` | 已分配 trace（可空） |
| `assignmentStatus` | `String` | `PENDING` / `ASSIGNED` |
| `tracePoolPreview` | `String` | 码池摘要（脱敏可选） |
| `tracePoolCodes` | `List<String>` | 汇总单码池**完整列表**（同 `summaryNo` 下各组相同；供前端自动预填） |
| `maxTraceCodeCount` | `Integer` | 允许最大码数（**BR-IP-TRC-008**） |

**失败码**

| 码 | 说明 |
| --- | --- |
| EX-IP-TRC-101 | 未选患者或无权 |

**Mapper**

- `NurseStationTraceAssignmentMapper#toPatientTraceAssignmentGroupsResponse(List<PatientTraceAssignmentGroupDto>): ListPatientTraceAssignmentGroupsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `encounterId` 非空 | — |
| 2 | Pre | `EncounterContextReader#assertEncounterVisible` | encounterId | **BR-IP-TRC-001** |
| 3 | Pre | `MedicationDispenseQueryService#listPatientTraceAssignmentAggregateLines` | encounterId | 药+批汇总组 |
| 4 | Pre | `SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo` | 各 summaryNo | supplyRequestId 列表 |
| 5 | Pre | `SupplyDeliveryQueryService#selectByRequestIdList` | supplyRequestIds | **SummaryTracePoolAssembler#buildPoolBySummaryNo** |
| 6 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | medicationIds | 展示 + maxCodes |
| 7 | Asm | `NurseStationTraceAssignmentMapper#toPatientTraceAssignmentGroupsResponse` | — | 仅读 |

### 3.4 IF-IP-TRC-03 `resolveTraceCodesForPatient`（批量解析追溯码）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 就诊 ID |
| `traceCodes` | `List<String>` | Y | 批量码（≤50） |
| `groupKeys` | `List<String>` | Y | IF-02 返回的组键快照 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `matches` | `List<TraceCodeMatchPreviewDto>` | 匹配预览 |
| `failItems` | `List<TraceCodeResolveFailItemDto>` | 失败码明细 |

**TraceCodeMatchPreviewDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `groupKey` | `String` | 汇总组键 |
| `matchedTraceCodes` | `List<String>` | 命中码 |
| `medicationId` | `Long` | 药品 |
| `lotNumber` | `String` | 批号 |

**TraceCodeResolveFailItemDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceCode` | `String` | 原码 |
| `reasonCode` | `String` | `INVALID` / `NOT_IN_POOL` / `NOT_MATCHED` / `OVER_LIMIT` |

**失败码**

| 码 | 说明 |
| --- | --- |
| EX-IP-TRC-202 | 格式非法 |
| EX-IP-TRC-201 | 不在码池 |
| EX-IP-TRC-205 | 部分未匹配 |
| EX-IP-TRC-206 | 跨 summary |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | traceCodes 非空、≤50 | — |
| 2 | Pre | `EncounterContextReader#assertEncounterVisible` | encounterId | — |
| 3 | Pre | 复用 IF-IP-TRC-02 步骤 3～5 | — | 组快照 + 码池 |
| 4 | Pre | `PatientTraceAssignmentWriteAssembler#normalizeTraceCodes` | traceCodes | **BR-IP-TRC-014** |
| 5 | Pre | `SummaryTracePoolAssembler#assertCodesInPools` | 码 + summaryNo | **BR-IP-TRC-005**；失败 **EX-IP-TRC-201** |
| 6 | Pre | `PharmacyTraceCodeResolveSupport#resolveTraceCodes` | `ResolveTraceCodesCommand` | 药品+批号→groupKey；**只读** |
| 7 | Pre | `PatientTraceAssignmentWriteAssembler#assertTraceCountWithinLimit` | 组 + 码数 | **BR-IP-TRC-008** |
| 8 | Asm | `NurseStationTraceAssignmentMapper#toResolveTraceCodesResponse` | — | **不写库** |

### 3.5 IF-IP-TRC-04 `savePatientTraceAssignment`（保存患者追溯码分配）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 就诊 ID |
| `groups` | `List<PatientTraceAssignmentGroupSubmitDto>` | Y | 待保存组 |

**PatientTraceAssignmentGroupSubmitDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `groupKey` | `String` | Y | 汇总组键 |
| `dispenseIds` | `List<Long>` | Y | 底层发放行 |
| `traceNo` | `String` | N | 逗号分隔多码；空表示跳过 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `savedGroupCount` | `Integer` | 成功组数 |

**失败码**

| 码 | 说明 |
| --- | --- |
| EX-IP-TRC-201 | 不在码池 |
| EX-IP-TRC-203 | 批号空 |
| EX-IP-TRC-204 | 码池空 |
| EX-IP-TRC-207 | 状态非 COMPLETED |
| EX-IP-TRC-208 | 码数超限 |

**Mapper**

- `PatientTraceAssignmentWriteAssembler#toAssignTraceNoCommand(PatientTraceAssignmentGroupSubmitDto, operatorId): AssignTraceNoToCompletedDispenseCommand`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | groups 非空 | — |
| 2 | Pre | `EncounterContextReader#assertEncounterVisible` | encounterId | — |
| 3 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByIds` | 全部 dispenseIds | 强加载 |
| 4 | Pre | `PatientTraceAssignmentWriteAssembler#assertAllCompletedWithSummary` | 实体列表 | **BR-IP-TRC-002** → **EX-IP-TRC-207** |
| 5 | Pre | `PatientTraceAssignmentWriteAssembler#assertLotNumberPresent` | 各组 | **EX-IP-TRC-203** |
| 6 | Pre | 复用 IF-IP-TRC-03 码池 Pre | 各 summaryNo | **EX-IP-TRC-201/204** |
| 7 | Pre | `PatientTraceAssignmentWriteAssembler#assertTraceCountWithinLimit` | — | **EX-IP-TRC-208** |
| 8 | Cmd | `MedicationDispenseDomainService#assignTraceNoToCompletedDispense` | `AssignTraceNoToCompletedDispenseCommand` | **写库点**：UPDATE `trace_no` |
| 9 | Asm | `NurseStationTraceAssignmentMapper#toSavePatientTraceAssignmentResponse` | saved count | — |

> **Cmd 说明**：对每个 `group` 循环步骤 8；组内**全部** `dispenseIds` 写入**相同** `traceNo`（**BR-IP-TRC-010**）。**禁止**调用 `TraceNumberDomainService`。

## 4. QueryService 与领域事件

| 读模型 | 刷新触发 |
| --- | --- |
| IF-IP-TRC-01 队列 | IF-IP-TRC-04 成功后客户端刷新 |
| IF-IP-TRC-02 组列表 | 同上 |

**领域事件**：IF-IP-TRC-04 **不**发布新事件（AS §6）。

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | §3 Cmd 序号 | 领域调用 | 聚合写入 |
| --- | --- | --- | --- | --- |
| IF-IP-TRC-04 | `savePatientTraceAssignment` | 3.5-⑧ | `MedicationDispenseDomainService#assignTraceNoToCompletedDispense` | `MedicationDispense` UPDATE **`trace_no` only** |

**事务边界**

| IF | 策略 | 失败 |
| --- | --- | --- |
| IF-IP-TRC-04 | **单事务** 覆盖全部组 Cmd | 全局回滚 |
| IF-IP-TRC-01～03 | 无写 Cmd | — |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 实现 | 关联步骤 |
| --- | --- | --- |
| 覆盖保存 | 同 groupKey 重复提交覆盖 `trace_no` | §3.5 步骤 8 |

### 6.2 并发控制

| 项 | 实现 | 关联步骤 |
| --- | --- | --- |
| 乐观 | 依赖发放行 `updated_at`（若 DM 命令带版本） | §3.5 步骤 8 |

### 6.3 审计

| 项 | 实现 | 关联步骤 |
| --- | --- | --- |
| 操作人 | `CurrentOperatorResolver` → Command | §3.5 步骤 8 |

### 6.4 数据脱敏

| 项 | 实现 |
| --- | --- |
| 码池预览 | `tracePoolPreview` 可截断展示；完整列表见 `tracePoolCodes` |

### 6.5 重试与容错

| 项 | 实现 |
| --- | --- |
| 客户端 | 匹配失败可修正后重调 IF-03/04 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 优先级 |
| --- | --- | --- |
| IF-02 药批聚合 | 集成 | P0 |
| IF-03 批量匹配预览不落库 | 集成 | P0 |
| IF-04 仅 UPDATE trace_no | 集成 | P0 |
| 池外码拒绝 | 集成 | P0 |
| 共用码多患者 | 集成 | P1 |

### 7.2 非功能测试

| 用例 | 层级 | 优先级 |
| --- | --- | --- |
| 50 码批量解析 | 性能 | P1 |

### 7.3 查询一致性

| 用例 | 说明 |
| --- | --- |
| 保存后 IF-02 | `assignmentStatus=ASSIGNED` |

### 7.4 集成测试

| 用例 | 说明 |
| --- | --- |
| AS-IP-PHA-002 核发后 | 端到端 IF-02→03→04 |

### 7.5 测试缺口

| 项 | 说明 |
| --- | --- |
| 应用层 | **NurseStationTraceAssignmentAppService** 待 SDD 成码后补集成测 |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2 | 2026-07-08 | **IF-IP-TRC-02**：**`PatientTraceAssignmentGroupDto`** 增补 **`tracePoolCodes`**（同 summary 码池完整列表，供前端自动预填） |
| v1.1 | 2026-07-04 | 对齐 **AS v1.1** / **DM-MED-002 v1.3.9**：领域 Gap 001～003 已关闭 |
| v1.0 | 2026-07-04 | 首版：IF-IP-TRC-01～04；**PharmacyTraceCodeResolveSupport** 默认已就绪；写路径仅 **`assignTraceNoToCompletedDispense`**（AS **GAP-IP-TRC-001**） |
