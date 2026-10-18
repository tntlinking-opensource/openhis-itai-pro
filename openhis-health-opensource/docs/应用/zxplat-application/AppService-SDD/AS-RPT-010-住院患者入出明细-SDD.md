# 住院患者入出明细 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-010-SDD · **版本**：v1.8 · **日期**：2026-08-14  
> **编排权威**：`AS-RPT-010-住院患者入出明细.md`  
> **菜单**：报表中心 · 住院患者入出明细  
> **实现阶段口径**：**领域读侧 + 应用层均已落地**（Gap 已关；App 包见 §2.1）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院患者入出明细报表 |
| **边界** | 只读；单列表 + 页脚人次；无汇总/明细双 Tab；无写路径 |
| **编排权威** | AS-RPT-010 IF-RPT-IOE-01～03 |
| **方法权威** | `DM-ADM-002` / `DM-FIN-002` QueryService（§6.2.36/37 / §6.2.20） |
| **严格对齐** | IF-RPT-IOE-01、01a、02、03 |
| **实现阶段口径** | **领域读侧 + 应用层均已落地** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | 一律 `POST /api/app/inpatientInOutDetailReport/{methodName}`（Registrar，无另写 RestController） |
| **应用包** | `com.zxplat.domain.application.report.inoutdetail`（**无** `.inpatient` 子段；仅住院报表） |
| **前端** | `apps/web-ele/src/views/report/inpatient-in-out-detail/index.vue`；菜单 code=`InpatientInOutDetail`，path=`/report/inpatient-in-out-detail`，父=`ReportCenterRoot` |
| **字段口径** | 费用空= JSON `null`；禁止 `0`；列表转科列由 `showTransferColumn` 驱动前端显隐 |
| **转科判定** | EncounterLocation 中，最早入院 WARD 之后仍有其它 WARD → `transferred=true`（代理「已确认转科」；非转科确认事件表） |
| **showTransferColumn** | IF-02 按**当前页**行；IF-03 按**本次导出行**；非全量筛选重算 |
| **分页字段** | 响应继承 `PagedResult`：`totalCount`（非 `totalElements`）、1-based `pageNo` |
| **登录 Gate** | `CurrentOperatorResolver#requireOperatorId`（未登录 → EX-RPT-IOE-101） |
| **QueryService 口径** | 入院科室/入院病区条件下推；禁止用转科后当前 `organization_id` 冒充入院科室展示（Enricher 优先最早病区挂靠科室） |
| **事务口径** | 纯读；无 `@Transactional` 写事务 |

---

## 2. 代码结构

### 2.1 包路径

```text
com.zxplat.domain.application.report.inoutdetail/
  service/InpatientInOutDetailReportAppService.java
  request/
    LoadInpatientInOutDetailReportPageInitRequest.java
    ListInpatientInOutDetailWardOptionsRequest.java
    PageInpatientInOutDetailReportRequest.java
    ExportInpatientInOutDetailReportRequest.java
  response/
    LoadInpatientInOutDetailReportPageInitResponse.java
    ListInpatientInOutDetailWardOptionsResponse.java
    PageInpatientInOutDetailReportResponse.java
    ExportInpatientInOutDetailReportResponse.java
  dto/
    InpatientInOutDetailRowDto.java
    InpatientInOutDetailFooterDto.java
  mapper/
    InpatientInOutDetailReportMapper.java
    InpatientInOutDetailReportMapperImpl.java
  support/
    InpatientInOutDetailReportQuerySupport.java
    InpatientInOutDetailReportEnricher.java

# 常量（zxplat-domain，禁止落 application/constants）
com.zxplat.domain.share.constants.InpatientInOutDetailReportConstants
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate（`requireOperatorId`） |
| `InpatientInOutDetailReportQuerySupport` | 日期窗/状态/分页校验；入院病区 `resolvedAdmissionWardLocationIds` 预写 |
| `EncounterQueryService` | `pageInpatientInOutDetailReport`；`countInpatientInOutDetailPersonTimes` |
| `PaymentReconciliationQueryService` | `mapInpatientClinicSettledNetAmountByEncounterIds`；`filterReversedPendingResettleEncounterIds` |
| `OrganizationQueryService` | 住院入院科室下拉（App）；名称 enrich（Enricher） |
| `LocationQueryService` | IF-01a 病区下拉；Enricher 病区/病房/床名称；QuerySupport 科室→病区 scope |
| `PatientQueryService` | Enricher 人口学批量（不直注 AppService） |
| `InpatientInOutDetailReportEnricher` | 患者/科室/病区/床位/转科文案/入院身份费用性质/费用空值；`resolveShowTransferColumn` |
| `ReportPatientSettlementContextAssembler` | Enricher：就诊优先账户 → 费用性质（身份口径） |
| `AnnotatedExcelExporter` | 导出 xlsx（按 `InpatientInOutDetailRowDto` 上 `@Excel` 定列） |
| `InpatientInOutDetailReportMapper` | Request/Response/DTO 映射；分页壳经 `PagedResultBuilder` |

> 公共库 `OptionDto` 不列入包路径清单。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 IMP | `InpatientInOutDetailReportQuerySupport` 固定 `EncounterClass.IMP` | `DM-ADM-002` §2.3 / `class_enum` | 仅住院 |
| 入出状态筛 | `PageInpatientInOutDetailReportRequest.movementStatus` | AS-RPT-010 BR-RPT-IOE-003；常量见 `InpatientInOutDetailReportConstants` | `ALL` / `ADMISSION` / `DISCHARGE` |
| 就诊状态排除 | Query 内排除 `PLANNED`/`CANCELLED` | `DM-ADM-002` §2.3.1 | BR-RPT-IOE-002 |
| 支付种类 | `PaymentKind.INPATIENT_CLINIC` | `DM-FIN-002` kind 表 | 出院结算；排除预交金 |
| 支付状态 | `PaymentStatus.SUCCESS` / `REFUNDED` | `DM-FIN-002` §2.3 | 净额代数和 |
| 待重结 | `PaymentReconciliationQueryService#filterReversedPendingResettleEncounterIds` | `DM-FIN-002` 反结语义 / AS-IP-FIN-004 | 费用置空 |
| 位置形态 | `LocationForm.WARD` / `HOUSE` / `BED` | `DM-ADM-003` form_enum | 位置展示链 |
| 病区联动 | `ListInpatientInOutDetailWardOptionsRequest.organizationId` | DM-ADM-003 病区挂靠 `organization_id`；`LocationQueryService#listWardsByOrganization` | IF-01a；BR-RPT-IOE-005a |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `EncounterQueryService` | `pageInpatientInOutDetailReport` | 02、03 |
| `EncounterQueryService` | `countInpatientInOutDetailPersonTimes` | 02、03 |
| `PaymentReconciliationQueryService` | `mapInpatientClinicSettledNetAmountByEncounterIds` | 02、03 |
| `PaymentReconciliationQueryService` | `filterReversedPendingResettleEncounterIds` | 02、03 |
| `OrganizationQueryService` | `listOrganizationsForInpatientAdmission` | 01 |
| `LocationQueryService` | `listWardsByOrganization`（科室→病区联动）；病区/病房/床名称 enrich | 01a、02、03 |
| `PatientQueryService` | `mapInpatientProjectionSnapshotsByIds`（或等价人口学批量） | 02、03 |

> 入院科室筛选：**App Support** 经 `LocationQueryService#listWardsByOrganization` 取该科病区 id → 写入 `resolvedAdmissionWardLocationIds`（B.6.4）；空列表拒查。  
> **联动**：前端未选科室时病区禁用；切换科室先调 IF-01a 再清空 `wardLocationId`。

---

## 3. 按接口 ID 命名展开

> **先读各 IF「领域编排序列」**，按序号自上而下执行。Gate/Pre 失败不得进入后续领域读（本期无 Cmd）。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-RPT-IOE-01 | `loadInpatientInOutDetailReportPageInit` | 加载入出明细页初始化 | `POST /api/app/inpatientInOutDetailReport/loadInpatientInOutDetailReportPageInit` |
| IF-RPT-IOE-01a | `listInpatientInOutDetailWardOptions` | 按入院科室加载病区选项 | `POST /api/app/inpatientInOutDetailReport/listInpatientInOutDetailWardOptions` |
| IF-RPT-IOE-02 | `pageInpatientInOutDetailReport` | 分页查询入出明细 | `POST /api/app/inpatientInOutDetailReport/pageInpatientInOutDetailReport` |
| IF-RPT-IOE-03 | `exportInpatientInOutDetailReport` | 导出入出明细 | `POST /api/app/inpatientInOutDetailReport/exportInpatientInOutDetailReport` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录/租户门禁 | 不再调用后续 |
| **Pre** | 参数校验、只读预解析 | 抛业务异常 |
| **Cmd** | 本期无 | — |
| **Asm** | Mapper/Enricher DTO 装配 | — |

---

### 3.2 IF-RPT-IOE-01 `loadInpatientInOutDetailReportPageInit`（加载入出明细页初始化）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 空体占位即可 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultDateFrom` | `LocalDateTime` | 默认日起（今日往前 1 个自然月 00:00:00） |
| `defaultDateTo` | `LocalDateTime` | 默认日止（当日 23:59:59） |
| `defaultMovementStatus` | `String` | `ALL` |
| `defaultPageSize` | `Integer` | 如 20 |
| `movementStatusOptions` | `List<OptionDto>` | 全部/入院/出院 |
| `organizationOptions` | `List<OptionDto>` | 住院入院科室 |
| `wardOptions` | `List<OptionDto>` | **初始空列表**（BR-RPT-IOE-005a；选科室后走 IF-01a） |

#### 关键校验

- 须已登录。

#### 失败码

| 码 | 条件 |
| --- | --- |
| EX-RPT-IOE-101 | 主数据加载失败 |

#### Mapper

- `toPageInitResponse(...): LoadInpatientInOutDetailReportPageInitResponse`

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-RPT-IOE-101 | 未登录失败 |
| 2 | Pre | `OrganizationQueryService#listOrganizationsForInpatientAdmission` | — | 科室下拉 |
| 3 | Asm | `InpatientInOutDetailReportMapper#toPageInitResponse` | 选项 + 默认近一个月窗；`wardOptions=[]`；默认状态 `ALL` | 仅读路径；**不**调全院病区 |

---

### 3.2a IF-RPT-IOE-01a `listInpatientInOutDetailWardOptions`（按入院科室加载病区选项）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `organizationId` | `Long` | 否 | 入院科室；空则返回空病区列表 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `wardOptions` | `List<OptionDto>` | 该科下启用病区；科室空时为空列表 |

#### 关键校验

- 须已登录。

#### 失败码

| 码 | 条件 |
| --- | --- |
| EX-RPT-IOE-101 | 病区加载失败 |

#### Mapper

- `toWardOptionsResponse(...): ListInpatientInOutDetailWardOptionsResponse`

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-RPT-IOE-101 | — |
| 2 | Pre | — | `organizationId` | 若为空：跳至步骤 4，`wardOptions=[]` |
| 3 | Pre | `LocationQueryService#listWardsByOrganization` | organizationId | 启用 WARD；与住院登记挂靠同口径 |
| 4 | Asm | `InpatientInOutDetailReportMapper#toWardOptionsResponse` | Location→OptionDto | 仅读路径 |

---

### 3.3 IF-RPT-IOE-02 `pageInpatientInOutDetailReport`（分页查询入出明细）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | `LocalDateTime` | 是 | 窗起 |
| `dateTo` | `LocalDateTime` | 是 | 窗止；≥ from |
| `movementStatus` | `String` | 是 | `ALL` / `ADMISSION` / `DISCHARGE` |
| `organizationId` | `Long` | 否 | 入院科室；App 据此预写领域 Query 的 `resolvedAdmissionWardLocationIds` |
| `wardLocationId` | `Long` | 否 | 入院病区（最早 WARD） |
| `pageNo` | `Integer` | 是 | ≥1 |
| `pageSize` | `Integer` | 是 | 上限见常量 |

> **非 HTTP 字段**：`resolvedAdmissionWardLocationIds` 仅存在于领域 `InpatientInOutDetailReportPageQuery`；由 `QuerySupport#prepareQueryScopes` 写入，**不**出现在 Request。

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<InpatientInOutDetailRowDto>` | 当前页行（`PagedResult`） |
| `totalCount` | `Long` | 总行数（`PagedResult`；**非** `totalElements`） |
| `pageNo` / `pageSize` | `Integer` | 回显（1-based pageNo） |
| `showTransferColumn` | `boolean` | **当前页**行是否存在转科（`Enricher#resolveShowTransferColumn`） |
| `footer` | `InpatientInOutDetailFooterDto` | 人次（同筛选全窗；**忽略**分页与 `movementStatus`） |

#### DTO `InpatientInOutDetailRowDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 ID |
| `busNo` | `String` | 住院号 |
| `patientId` | `Long` | 患者 ID |
| `patientName` | `String` | 姓名 |
| `genderText` | `String` | 性别展示 |
| `ageText` | `String` | 年龄展示 |
| `admissionOrganizationName` | `String` | 入院科室名（优先最早病区挂靠科室；否则领域兜底主档 org） |
| `admissionWardName` | `String` | 入院病区名 |
| `wardName` | `String` | 当前/末次病区 |
| `houseName` | `String` | 当前/末次病房 |
| `bedName` | `String` | 当前/末次床 |
| `startTime` | `LocalDateTime` | 入院日 |
| `endTime` | `LocalDateTime` | 出院日；可空 |
| `inpatientDays` | `Integer` | 住院天数：**即进不计出**（入院日计、出院日不计；例 11→13=2）；在院未出院含当日；无入院日则空 |
| `transferTargetText` | `String` | 转往科室文案；无转科可空 |
| `feeNatureContractNo` | `String` | 入院身份优先账户 → `fin_contract.bus_no` |
| `feeNatureDisplay` | `String` | 费用性质展示（合同名；列表/导出） |
| `settledFeeAmount` | `BigDecimal` | 已结净额；无/待重结/净 0 → **null** |
| `transferred` | `boolean` | 是否转科（驱动 `showTransferColumn`；**不**导出） |

#### DTO `InpatientInOutDetailFooterDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `admissionPersonTimes` | `Long` | 入院人次（Encounter） |
| `dischargePersonTimes` | `Long` | 出院人次（Encounter） |

#### 关键校验

- `dateFrom`/`dateTo` 非空且 `dateTo ≥ dateFrom`
- `movementStatus` ∈ {ALL, ADMISSION, DISCHARGE}
- 分页合法

#### 失败码

| 码 | 条件 |
| --- | --- |
| EX-RPT-IOE-102 | 条件非法 |
| EX-RPT-IOE-103 | 查询失败 |

#### Mapper / Enricher

- 行装配：`InpatientInOutDetailReportEnricher#enrichRows`（**无**独立 Mapper `toRowDto`）
- 分页壳：`InpatientInOutDetailReportMapper#toPageResponse`（`PagedResultBuilder`）
- 页脚：`#toFooterDto`

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-RPT-IOE-101 | — |
| 2 | Gate | `InpatientInOutDetailReportQuerySupport#assertDateAndStatus` / `#assertPageWindow` | Request | 失败 → EX-RPT-IOE-102 |
| 3 | Pre | Mapper `#toPageQuery` + QuerySupport `#prepareQueryScopes` | 若 `organizationId` 非空则 `listWardsByOrganization` | 预写 `resolvedAdmissionWardLocationIds`；空病区列表 → 领域空页 |
| 4 | Pre | `EncounterQueryService#pageInpatientInOutDetailReport` | 窗、状态、入院病区 scope、分页 | 仅读；行投影含转科目标、位置链、入院科室/病区 id |
| 5 | Pre | `EncounterQueryService#countInpatientInOutDetailPersonTimes` | 同筛选（不计分页、忽略 movementStatus） | 页脚两侧人次 |
| 6 | Pre | `PaymentReconciliationQueryService#mapInpatientClinicSettledNetAmountByEncounterIds` | 本页 encounterIds | 净额 Map（净 0 不入 Map） |
| 7 | Pre | `PaymentReconciliationQueryService#filterReversedPendingResettleEncounterIds` | 本页 encounterIds | 待重结集合 |
| 8 | Asm | `InpatientInOutDetailReportEnricher#enrichRows` + `#resolveShowTransferColumn` | 行 + 净额 + 待重结 | 待重结或无净额 → `settledFeeAmount=null`；`showTransferColumn`=当前页 |
| 9 | Asm | `InpatientInOutDetailReportMapper#toPageResponse`（`PagedResultBuilder`） | — | 仅读路径 |

---

### 3.4 IF-RPT-IOE-03 `exportInpatientInOutDetailReport`（导出入出明细）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （同 IF-02 筛选字段） | — | 同 02 | **无** pageNo/pageSize；或忽略分页 |
| `dateFrom` / `dateTo` / `movementStatus` / `organizationId` / `wardLocationId` | 同 02 | 同 02 | — |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 如 `住院患者入出明细_yyyy-MM-dd-yyyy-MM-dd.xlsx` |
| `contentType` | `String` | spreadsheet MIME（常量 `EXPORT_CONTENT_TYPE`） |
| `body` | `byte[]` | 文件 |
| `rowCount` | `Integer` | 导出行数 |
| `truncated` | `Boolean` | 触达 `EXPORT_MAX_ROWS` 截断时为 true（**不**因此抛 104） |
| `showTransferColumn` | `Boolean` | 导出行集是否存在转科（元数据；xlsx 仍固定含「转往」列） |

#### 关键校验

- 同 IF-02 日期与状态（无分页校验）。

#### 失败码

| 码 | 条件 |
| --- | --- |
| EX-RPT-IOE-102 | 条件非法 |
| EX-RPT-IOE-104 | 导出过程运行时失败（写盘/序列化等）；**触顶截断不抛本码** |

#### Mapper / Excel

- 导出行复用 `InpatientInOutDetailRowDto`（含 `@Excel`）；经 Enricher 装配。
- **Excel 列**：`AnnotatedExcelExporter` 按类注解定列，**固定含「转往」列**（无转科时单元格空）；列表 UI 仍由 `showTransferColumn` 控制是否展示该列。本期不按开关裁剪 xlsx 列。

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-RPT-IOE-101 | 未登录 |
| 2 | Gate | `InpatientInOutDetailReportQuerySupport#assertDateAndStatus` | Request | EX-RPT-IOE-102 |
| 3 | Pre | Mapper `#toExportQuery` + QuerySupport `#prepareQueryScopes` | 同 IF-02 scope | — |
| 4 | Pre | 循环 `EncounterQueryService#pageInpatientInOutDetailReport` | `EXPORT_PAGE_SIZE`，直至空或达 `EXPORT_MAX_ROWS` | 仅读；触顶 → `truncated=true` 继续出文件 |
| 5 | Pre | 各批 Payment 净额 + 待重结（同 IF-02） | 本批 encounterIds | Enricher 内 Patient/Location/Org 批量 |
| 6 | Asm | Enricher + `resolveShowTransferColumn(导出行)` + `AnnotatedExcelExporter` | — | 写 xlsx；响应含 `truncated` / `showTransferColumn` |

---

## 4. QueryService 与领域事件

| 读侧 | 语义 |
| --- | --- |
| `pageInpatientInOutDetailReport` | **已落地**（DM-ADM-002 §6.2.36）：IMP；排除 PLANNED/CANCELLED；按 movementStatus 对 `start_time`/`end_time`；入院病区=最早 WARD；科室筛经 `resolvedAdmissionWardLocationIds` |
| `countInpatientInOutDetailPersonTimes` | **已落地**（§6.2.37）：忽略 movementStatus；两侧人次 |
| `mapInpatientClinicSettledNetAmountByEncounterIds` | **已落地**（DM-FIN-002 §6.2.20）：净 0 不入 Map |
| `filterReversedPendingResettleEncounterIds` | 已有实现；待重结费用置空 |

**本期无领域写事件。**

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| 01～03 | 全部 | — | **本期无写库** |

### 5.2 事务边界 / 失败回滚

| 项 | 约定 |
| --- | --- |
| 事务 | 无写事务 |
| 失败 | 抛业务异常；无部分写入 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 内容 |
| --- | --- |
| 代码位置（必须） | 各 IF 只读方法 |
| 关联步骤 | §3.2～3.4 全序列 |
| 承诺 | 重复查询无副作用 |

### 6.2 并发控制

| 项 | 内容 |
| --- | --- |
| 代码位置（必须） | QueryService 读已提交 |
| 关联步骤 | §3.3 序号 3～6 |
| 承诺 | 无悲观锁；报表允许短暂滞后 |

### 6.3 重试与容错

| 项 | 内容 |
| --- | --- |
| 代码位置（必须） | EX-RPT-IOE-101/103/104 |
| 关联步骤 | 各 IF 失败码 |
| 承诺 | 可重试查询/导出；参数非法不可重试蒙混 |

### 6.4 数据脱敏

| 项 | 内容 |
| --- | --- |
| 代码位置（必须） | RowDto 人口学字段 |
| 关联步骤 | §3.3 序号 7～8 |
| 承诺 | 按院点报表脱敏策略（若有）；本期不额外扩身份证明文 |

### 6.5 审计日志

| 项 | 内容 |
| --- | --- |
| 代码位置（必须） | 可选访问日志 |
| 关联步骤 | Gate |
| 承诺 | 无写审计；导出可记操作者与行数 |

**导出行上限**：`InpatientInOutDetailReportConstants.EXPORT_MAX_ROWS`（50000）；触顶截断并回 `truncated=true`。

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 入院/出院/全部过滤与并集 | 应用 | IT | P0 |
| 转科后入院科室筛仍命中；列表 `showTransferColumn`（当前页） | 应用 | IT | P0 |
| 费用：已结有值、未结 null、待重结 null、禁止 0 | 应用 | IT | P0 |
| 页脚人次：全部时两侧可重叠 | 应用 | IT | P0 |
| 排除 PLANNED/CANCELLED | 领域/应用 | UT/IT | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 日窗分页 ≤2s（数据量约定） | 应用 | 手工/压测 | P1 |
| 导出触顶截断 + `truncated=true` | 应用 | IT | P1 |

### 7.3 查询结果一致性

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 列表行数与分页 `totalCount` 一致 | 应用 | IT | P0 |
| 列表 UI 列随 `showTransferColumn`；xlsx 固定含「转往」列 | 应用 | IT | P1 |

### 7.4 集成测试

| 用例 | 层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 与登记/转科/结算/反结联调抽样 | 集成 | 手工 | P0 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 领域 Gap | **已关闭**（AS §3） |
| 前端页 / 菜单种子 | **已落地**：`views/report/inpatient-in-out-detail`；V121 挂报表中心 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.8 | 2026-08-14 | 默认业务窗改为近一个月（`DEFAULT_LOOKBACK_MONTHS=1`） |
| v1.7 | 2026-08-14 | `inpatientDays` 改为**即进不计出**（11→13=2）；在院含当日；出院时间真源=计划出院/`end_time` |
| v1.6 | 2026-08-02 | 行 DTO/列表/导出增费用性质（入院身份优先账户） |
| v1.5 | 2026-07-27 | 前端 + V121 菜单；包名确认 `report.inoutdetail` |
| v1.4 | 2026-07-27 | 校验回写：包路径去掉错误 `.inpatient`；删虚假 `toRowDto`；补 `transferred`；`resolvedAdmissionWardLocationIds` 标明非 HTTP；转科=WARD 轨迹代理 |
| v1.3 | 2026-07-27 | App 落地回写：`requireOperatorId`；`totalCount`；常量 domain-shared；`showTransferColumn`=当前页/导出行；导出截断 `truncated`；xlsx 固定含转往列 |
| v1.2 | 2026-07-27 | 科室→病区联动：IF-01a `listInpatientInOutDetailWardOptions`；init 病区空；查询前 `listWardsByOrganization` 预写 scope |
| v1.1 | 2026-07-27 | 领域 Gap 关闭回写：§6.2.36/37/FIN-002§6.2.20 已落地；强调 `resolvedAdmissionWardLocationIds` |
| v1.0 | 2026-07-27 | 首版目标态：报表中心包 `report.inoutdetail`；IF-01～03；动态转科列；费用空值；人次页脚 |
