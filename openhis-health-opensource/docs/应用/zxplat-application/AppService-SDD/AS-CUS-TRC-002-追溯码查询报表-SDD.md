# 追溯码查询报表 · 应用服务详细设计（SDD）

> 编码：**AS-CUS-TRC-002-SDD**  
> 版本：**v1.0** | 日期：**2026-07-06** | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 报表 · **追溯码查询**（包 **`application/report/tracenumber`**；**单一** `TraceNumberReportAppService`） |
| 边界 | 编排 **IF-TRC-RPT-01～05**；**只读**；**药品**（`item_table=med_medication_definition`）；**无管辖库位 Gate** |
| 编排权威 | `AS-CUS-TRC-002-追溯码查询报表.md`（**v1.0**）§1～§8 |
| 方法权威 | **DM-CUS-010** §6.2（`TraceNumberQueryService`）；**DM-MED-001**；**DM-ADM-003**；**DM-MED-002**（患者 enrich） |
| 严格对齐 | IF **IF-TRC-RPT-01～05** 与 AS 母表一致 |
| 实现阶段口径 | **已成码**（对齐 `report/nurseworkload` 分包） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 列表真源 | **`cus_trace_number` + `cus_trace_number_flow` 聚合**；**禁止** `wkf_inventory_item.trace_no` |
| 全院在院 | 主表 `in_hospital_status_enum` + `remaining_split_quantity`（**不看** `location_id`） |
| 本库在库 | 子表按 `location_id` 轧差净拆零量 **> 0** |
| 本库已出 | 指定 `location_id` 下净拆零量 **≤ 0** |
| 药品关键字 | **`medicationKeyword`** → **`MedicationDefinitionQueryService#listIdsByInventoryDetailKeyword`** → `item_id IN` |
| 流水时间 | 子表 **`created_at`** 闭区间 EXISTS |
| deep link | Request/路由 **`traceNo`** + **`openDetail=true`** → 列表后自动打开详情 Drawer |
| 导出 | 与列表**同筛选**；分页循环拉全量；**不含**明细 Tab 流水行 |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **TRC-RPT-SDD-R-001** | **单一** `TraceNumberReportAppService`；HTTP **`traceNumberReport** |
| **TRC-RPT-SDD-R-002** | 列表/导出 **共用** `TraceNumberReportQueryAssemblySupport#buildPageQuery`（筛选单一真源） |
| **TRC-RPT-SDD-R-003** | **`queryScope=LOCATION`** 时 **`locationId` 必填**；否则 **`TRC_RPT_202`** |
| **TRC-RPT-SDD-R-004** | init **01**：库位下拉 = **`listAllActiveWarehouses` + `listAllActivePharmacies` + `listAllActiveDepartmentPharmacies`** 并集（**不做** Gate，对齐 **AS BR-TRC-RPT** 非目标说明） |
| **TRC-RPT-SDD-R-005** | 导出工作表名 **「追溯码查询」**；文件名 `追溯码查询_{timestamp}.xlsx`；上限 **`TraceNumberReportExportConstants.EXPORT_MAX_ROWS=30000`** |
| **TRC-RPT-SDD-R-006** | 明细 Tab 患者 enrich：**仅** `operation_type ∈ {MEDICATION_DISPENSE_OUT, MEDICATION_EXTRA_DISPENSE_OUT, MEDICATION_RETURN_IN}` 且 `bus_no` 非空时尝试；**次要列** `patientName`，无关联则空 |
| **TRC-RPT-SDD-R-007** | 患者链路：`bus_no` → **`MedicationDispenseQueryService#listByBusNos`** → `encounterId` → **`PatientQueryService#findPatientNamesByIds`** |

## 2. 代码结构

### 2.1 包结构

```text
com/openhis/health/application/report/tracenumber/
  service/TraceNumberReportAppService.java
  support/TraceNumberReportQueryAssemblySupport.java
  support/TraceNumberReportEnrichmentSupport.java
  support/TraceNumberReportPatientEnrichmentSupport.java
  request/
  response/
  dto/
  mapper/TraceNumberReportMapper.java
  mapper/TraceNumberReportMapperImpl.java
```

导出常量真源：**`com.zxplat.domain.share.constants.TraceNumberReportExportConstants`**（**禁止** application 层 constants 包）。

HTTP 前缀：**`/api/app/traceNumberReport/`**

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `TenantUtils` | Gate：租户 |
| `TraceNumberQueryService` | **`pageTraceNumberReport** / **`getTraceNumberByTraceNo`** / **`listRecordsByTraceNo`** |
| `LocationQueryService` | init 库位；明细/列表 enrich 库位名 |
| `MedicationDefinitionQueryService` | 药品关键字预解析；概览/列表 enrich |
| `MedicationDispenseQueryService` | **`listByBusNos`**（目标态）患者反查 |
| `PatientQueryService` | **`findPatientNamesByIds`** |
| `CodeSystemQueryService` | 操作类型/单位/方向 Label（按需） |
| `TraceNumberReportQueryAssemblysupport | Request → **`TraceNumberReportPageQuery`** |
| `TraceNumberReportEnrichmentsupport | 药品名、库位名、枚举 Label |
| `TraceNumberReportPatientEnrichmentsupport | 流水行 **`patientName`** |
| `TraceNumberReportMapper` | Model → DTO；Excel 行 |
| `TabularExcelExporter` | 导出字节 |
| `PageWindowGate` | 分页窗口 Gate（02） |

### 2.3 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `TraceNumberQueryService` | **`pageTraceNumberReport** | 02、05 |
| `TraceNumberQueryService` | **`getTraceNumberByTraceNo`** | 03 |
| `TraceNumberQueryService` | **`listRecordsByTraceNo`** | 04 |
| `LocationQueryService` | `listAllActiveWarehouses` / `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies` / `findOptionalById` | 01、02～05 |
| `MedicationDefinitionQueryService` | `listIdsByInventoryDetailKeyword` / `findAllByIds`（或 batch loader 同族） | 02～05 |
| `MedicationDispenseQueryService` | **`listByBusNos`** | 04 |
| `PatientQueryService` | **`findPatientNamesByIds`** | 04 |

### 2.4 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 查询范围 | `TraceNumberReportQueryScope`（目标态 FQCN：`com.zxplat.domain.share.enums.TraceNumberReportQueryScope`） | **AS-CUS-TRC-002** §1.3 `queryScope` | `HOSPITAL` / `LOCATION` |
| 本库状态 | `TraceNumberLocationStockStatus`（目标态 FQCN：`com.zxplat.domain.share.enums.TraceNumberLocationStockStatus`） | **AS-CUS-TRC-002** §1.3 | `IN_LOCATION` / `OUT_OF_LOCATION` |
| 在院状态 | `InHospitalStatus`（目标态 FQCN：`com.zxplat.domain.share.enums.InHospitalStatus`） | **DM-CUS-010** §2.3 / §4.5 | `IN_HOSPITAL` / `NOT_IN_HOSPITAL` |
| 流水类别 | `TraceFlowCategory`（目标态 FQCN：`com.zxplat.domain.share.enums.TraceFlowCategory`） | **DM-CUS-010** §4.3 | `HOSPITAL_IO` / `INTERNAL_TRANSFER` |
| 流水方向 | `TraceDirection`（目标态 FQCN：`com.zxplat.domain.share.enums.TraceDirection`） | **DM-CUS-010** §4.3 | `IN` / `OUT` |
| 操作类型 | `TraceNumberRecordOperationType` | **DM-CUS-010** §4.7 | 明细 `operationType` + Label |
| 品项表 | `TableNameConstants.MED_MEDICATION_DEFINITION` | **DM-CUS-010** §4.1 | 固定药品 |
| 错误码 | `ErrorCodeConstants.TRC_RPT_201`～`206` | **AS-CUS-TRC-002** §5 | 本模块 |

### 2.5 白名单双向一致性

§2.3 各方法均出现在 §3 编排序列；§3 **无 Cmd**（只读）。

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**AppService**：`TraceNumberReportAppService`

| IF | 应用服务方法 | HTTP |
| --- | --- | --- |
| IF-TRC-RPT-01 | `loadTraceNumberReportFormInit` | `POST /api/app/traceNumberReport/loadTraceNumberReportFormInit` |
| IF-TRC-RPT-02 | `pageTraceNumberReport | `POST /api/app/traceNumberReport/pageTraceNumberReport |
| IF-TRC-RPT-03 | `getTraceNumberOverview` | `POST /api/app/traceNumberReport/getTraceNumberOverview` |
| IF-TRC-RPT-04 | `listTraceRecords` | `POST /api/app/traceNumberReport/listTraceRecords` |
| IF-TRC-RPT-05 | `exportTraceNumberReport | `POST /api/app/traceNumberReport/exportTraceNumberReport |

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 租户、分页窗口、`queryScope`/`locationId` 合法性 | `BusinessException` |
| **Pre** | 关键字预解析、领域 Query、批量 enrich | 空页或异常 |
| **Cmd** | — | **无写库** |
| **Asm** | Mapper、Excel | — |

---

### 3.2 IF-TRC-RPT-01 `loadTraceNumberReportFormInit`

#### Request（`LoadTraceNumberReportFormInitRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 租户 Gate |

#### Response（`LoadTraceNumberReportFormInitResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `queryScopeOptions` | `List<OptionDto>` | `HOSPITAL` / `LOCATION` |
| `inHospitalStatusOptions` | `List<OptionDto>` | 在院 / 不在院 |
| `locationStockStatusOptions` | `List<OptionDto>` | 本库在库 / 本库已出 |
| `locationOptions` | `List<OptionDto>` | 药库+药房+科室药房并集 |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `TenantUtils#getCurrentTenantId` | 空 → **`TRC_RPT_201`** |
| 2 | Asm | 枚举 → `queryScopeOptions` 等 | |
| 3 | Pre | `LocationQueryService` 三 list 并集 | **TRC-RPT-SDD-R-004** |
| 4 | Asm | 组装 Response | |

---

### 3.3 IF-TRC-RPT-02 `pageTraceNumberReport

#### Request（`PageTraceNumberReportRequest` extends `AbstractPageQuery`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `Integer` | Y | ≥1 |
| `pageSize` | `Integer` | Y | **B.10.11** |
| `queryScope` | `String` | Y | `HOSPITAL` / `LOCATION` |
| `locationId` | `Long` | 条件 | `queryScope=LOCATION` 时 **Y** |
| `inHospitalStatusEnum` | `String` | N | `IN_HOSPITAL` / `NOT_IN_HOSPITAL` |
| `locationStockStatusEnum` | `String` | N | 仅 LOCATION 有效 |
| `traceNoKeyword` | `String` | N | `trace_no` LIKE |
| `medicationKeyword` | `String` | N | 药品名/编码/拼音/五笔 |
| `lotNumberKeyword` | `String` | N | 批号 LIKE |
| `flowTimeStart` | `LocalDateTime` | N | 流水时间下限 |
| `flowTimeEnd` | `LocalDateTime` | N | 流水时间上限 |
| `traceNo` | `String` | N | deep link 预选码（可选） |
| `openDetail` | `Boolean` | N | deep link：列表后前端打开详情 |

#### Response（`PageTraceNumberReportResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | `long` | |
| `pageNo` | `int` | |
| `pageSize` | `int` | |
| `records` | `List<TraceNumberReportRowDto>` | |
| `prefillTraceNo` | `String` | 回显 deep link 码 |
| `openDetail` | `Boolean` | 回显是否打开详情 |

#### DTO：`TraceNumberReportRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceNo` | `String` | 追溯码 |
| `medicationId` | `Long` | 药品 id |
| `medicationCode` | `String` | 院内编码（enrich） |
| `medicationName` | `String` | 药品名称（enrich） |
| `lotNumber` | `String` | 批号 |
| `inHospitalStatusEnum` | `String` | 在院状态 code |
| `inHospitalStatusLabel` | `String` | 读侧 Label |
| `remainingSplitQuantity` | `BigDecimal` | 拆零剩余 |
| `traceUnitCode` | `String` | 追溯码单位 |
| `locationStockStatusEnum` | `String` | 本库状态（LOCATION 范围；否则 null） |
| `locationStockStatusLabel` | `String` | |
| `locationNetSplitQuantity` | `BigDecimal` | 本库净拆零量（LOCATION 范围） |
| `lastFlowAt` | `LocalDateTime` | 最近流水时间 |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `TenantUtils#getCurrentTenantId` + `PageWindowGate` | **`TRC_RPT_201`** |
| 2 | Gate | **`#assertLocationRequiredWhenLocationScope`** | **TRC-RPT-SDD-R-003** → **`TRC_RPT_202`** |
| 3 | Pre | **`TraceNumberReportQueryAssemblySupport#buildPageQuery`** | 药品关键字 → `medicationItemIds`；固定 `itemTable=MED_MEDICATION_DEFINITION` |
| 4 | Pre | **`TraceNumberQueryService#pageTraceNumberReport** | |
| 5 | Pre | **`MedicationDefinitionQueryService#findAllByIds`**（或 batch loader） | enrich 药品 |
| 6 | Asm | **`TraceNumberReportEnrichmentSupport#enrichListRows`** | Label、库位名（若需要） |
| 7 | Asm | **`TraceNumberReportMapper#toRowDtoList`** | |
| 8 | Asm | 回显 `prefillTraceNo` / `openDetail` | deep link |

失败码：**`TRC_RPT_203`**

---

### 3.4 IF-TRC-RPT-03 `getTraceNumberOverview`

#### Request（`GetTraceNumberOverviewRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `traceNo` | `String` | Y | trim 非空 |

#### Response（`GetTraceNumberOverviewResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceNo` | `String` | |
| `medicationId` | `Long` | |
| `medicationCode` | `String` | enrich |
| `medicationName` | `String` | enrich |
| `specificationText` | `String` | enrich（可选） |
| `lotNumber` | `String` | |
| `traceUnitCode` | `String` | |
| `remainingSplitQuantity` | `BigDecimal` | |
| `inHospitalStatusEnum` | `String` | |
| `inHospitalStatusLabel` | `String` | |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 租户 + `traceNo` 非空 | **`TRC_RPT_101`** |
| 2 | Pre | **`TraceNumberQueryService#getTraceNumberByTraceNo`** | 空 → **`TRC_RPT_204`** |
| 3 | Pre | **`MedicationDefinitionQueryService`** 单条 enrich | |
| 4 | Asm | **`TraceNumberReportMapper#toOverviewResponse`** | |

---

### 3.5 IF-TRC-RPT-04 `listTraceRecords`

#### Request（`ListTraceRecordsRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `traceNo` | `String` | Y | |

#### Response（`ListTraceRecordsResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `traceNo` | `String` | |
| `records` | `List<TraceNumberRecordRowDto>` | 时间升序 |

#### DTO：`TraceNumberRecordRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 流水 id |
| `flowCategoryEnum` | `String` | |
| `flowCategoryLabel` | `String` | |
| `flowDirectionEnum` | `String` | |
| `flowDirectionLabel` | `String` | |
| `locationId` | `Long` | |
| `locationName` | `String` | enrich |
| `quantity` | `BigDecimal` | |
| `unitCode` | `String` | |
| `unitCodeLabel` | `String` | 可选 |
| `operationType` | `String` | |
| `operationTypeLabel` | `String` | |
| `busNo` | `String` | |
| `createdAt` | `LocalDateTime` | |
| `patientName` | `String` | **次要**；发药/退药 enrich |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 租户 + `traceNo` 非空 | |
| 2 | Pre | **`TraceNumberQueryService#getTraceNumberByTraceNo`** | 不存在 → **`TRC_RPT_204`** |
| 3 | Pre | **`TraceNumberQueryService#listRecordsByTraceNo`** | 时间升序 |
| 4 | Pre | **`LocationQueryService#findOptionalById`** 批量 | 库位名 |
| 5 | Pre | **`TraceNumberReportPatientEnrichmentSupport#enrichPatientNames`** | **TRC-RPT-SDD-R-006/007** |
| 6 | Asm | **`TraceNumberReportEnrichmentSupport#enrichFlowLabels`** + Mapper | |

---

### 3.6 IF-TRC-RPT-05 `exportTraceNumberReport

#### Request

与 **`PageTraceNumberReportRequest`** 同筛选字段（**无分页**或忽略 `pageNo`/`pageSize`）。

#### Response（`ExportTraceNumberReportResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | |
| `contentType` | `String` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `body` | `byte[]` | |
| `exportedRowCount` | `int` | |
| `truncated` | `boolean` | 触达 **30000** 上限 |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 同 IF-02 | |
| 2 | Pre | **`#fetchAllReportRows`**：`pageTraceNumberReport 循环（**`EXPORT_PAGE_SIZE=500`**）直至无下一页或达 **EXPORT_MAX_ROWS** | **TRC-RPT-SDD-R-005** |
| 3 | Asm | **`TraceNumberReportMapper#toExportRows`** | 列与列表对客列同构 |
| 4 | Asm | **`TabularExcelExporter`** 写 **「追溯码查询」** | |

失败码：**`TRC_RPT_205`**；截断提示 **`TRC_RPT_206`**

---

## 4. QueryService 与领域事件

### 4.1 `TraceNumberQueryService`（**DM-CUS-010** §6.2 / SDD §5.2）

| 方法 | IF | 语义摘要 |
| --- | --- | --- |
| **`pageTraceNumberReport** | 02、05 | 主表 + 子表聚合分页；`item_table=medication`；本库状态列 |
| **`getTraceNumberByTraceNo`** | 03、04 | 主档单条 |
| **`listRecordsByTraceNo`** | 04 | 子表全量时间序 |

### 4.2 `MedicationDispenseQueryService`（目标态扩展）

| 方法 | IF | 语义摘要 |
| --- | --- | --- |
| **`listByBusNos`** | 04 | `WHERE bus_no IN (...)` 批量读发放行（供患者 enrich） |

### 4.3 领域事件

本模块**只读**；**不订阅**写侧事件。列表数据为查询时点快照。

## 5. 写库顺序（核心写接口视角）

本 AS **无写接口**；§5 不适用。

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等

读接口天然幂等。

### 6.2 性能

| 项 | 承诺 |
| --- | --- |
| 列表 | 分页；子表聚合走 `(location_id, trace_no)` 索引 |
| 导出 | 分页循环 500 行/批；上限 3 万 |
| 明细 | 单码流水一次加载；患者 enrich 批量 `bus_no IN` |

### 6.3 安全

租户隔离；读权限与库存明细同级（库管/药师）。

### 6.4 审计

只读无审计写。

## 7. 测试要点

| 用例 | 优先级 |
| --- | --- |
| IF-02：全院筛选 + 在院态 + 追溯码/药品/批号/流水时间组合 | P0 |
| IF-02：LOCATION + 本库在库 vs 已出（调拨 OUT 后本库已出、全院仍在院） | P0 |
| IF-03：概览主表剩余与在院态 | P0 |
| IF-04：调拨成对 + 发药 OUT + 退药 IN；`patientName` 非主列 | P0 |
| IF-05：导出与列表同筛选；3 万截断 | P1 |
| deep link：`traceNo` + `openDetail` | P1 |
| 仅药品：不含 `device` 主表行 | P1 |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v1.1** | **2026-07-08** | 菜单 Flyway **V110**；对齐 **AS v1.1** |
| **v1.0** | **2026-07-06** | 初稿：IF-TRC-RPT-01～05；目标态 `TraceNumberReportAppService` + Query 扩展 |
