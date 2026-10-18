# 门诊挂号明细 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-007-SDD · **版本**：v1.5 · **日期**：2026-09-09  
> **编排权威**：`AS-RPT-007-门诊挂号明细.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊挂号明细报表 |
| **边界** | 汇总净笔数（科室/接诊医生×号源）；明细含自费医保与接诊医生列；纯读 |
| **严格对齐** | IF-RPT-ORG-01～04 |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientRegistrationDetailReport/{methodName}` |
| **净** | 退号冲减（CANCELLED/PLANNED/ENTERED_IN_ERROR 不计净笔数） |
| **视角** | `summaryView=DEPARTMENT` \| `DOCTOR`（领域映射 `ORGANIZATION` \| `DOCTOR`；医生维=活动 **ADMITTER**） |
| **医生筛** | `practitionerId` → Support 预写 `resolvedEncounterIds`（`filterEncounterIdsByActiveAdmitterDoctor`） |
| **状态筛** | `statusEnum` → 领域 Query 单值 equal；空不收窄；非白名单 code → EX-RPT-ORG-102 |
| **明细页脚** | `footer.registrationCount` / `cancelledCount` / `activeCount`（对齐门诊挂号当日统计） |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/registrationdetail/
  service/OutpatientRegistrationDetailReportAppService.java
  request/
    LoadOutpatientRegistrationDetailReportPageInitRequest.java
    SummarizeOutpatientRegistrationDetailReportRequest.java
    PageOutpatientRegistrationDetailReportDetailRequest.java
    ExportOutpatientRegistrationDetailReportRequest.java
  response/
    LoadOutpatientRegistrationDetailReportPageInitResponse.java
    SummarizeOutpatientRegistrationDetailReportResponse.java
    PageOutpatientRegistrationDetailReportDetailResponse.java
    ExportOutpatientRegistrationDetailReportResponse.java
  dto/
    OutpatientRegistrationDetailSummaryRowDto.java
    OutpatientRegistrationDetailRowDto.java
    OutpatientRegistrationDetailFooterDto.java
  mapper/
    OutpatientRegistrationDetailReportMapper.java
    OutpatientRegistrationDetailReportMapperImpl.java
  support/
    OutpatientRegistrationDetailReportQuerySupport.java
    OutpatientRegistrationDetailReportEnricher.java
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate |
| `OutpatientRegistrationDetailReportQuerySupport` | 日期窗；关键字/医生 → `resolvedEncounterIds` |
| `EncounterQueryService` | 白名单汇总/明细分页；医生筛 |
| `OrganizationQueryService` / `HealthcareServiceQueryService` | 科室/号源下拉与展示名 |
| `PractitionerRoleScopeLoader` | 医生岗下拉（ROLE_DOCTOR） |
| `OutpatientRegistrationDetailReportEnricher` | 患者/科室/医生/号源名；费用性质（Account+Contract）；挂号费（ChargeItem） |
| `AnnotatedExcelExporter` | 导出 |
| `OutpatientRegistrationDetailReportMapper` | Request/Response 映射 |

> 费用性质走就诊优先合同号（`AccountQueryService#mapPreferredContractNoByEncounterIds`，挂号身份），**不**经 Payment / 收费行账户；挂号费为号源锚点 `ChargeItem.totalPrice` 代数和。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 汇总视角 | `SummarizeOutpatientRegistrationDetailReportRequest.summaryView` | AS-RPT-007 | DEPARTMENT/DOCTOR |
| 领域汇总维 | `OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_*` | DM-ADM-002 §6.2.35 | ORGANIZATION/DOCTOR |
| 就诊状态 | Encounter status / `statusEnum` 筛选 | DM-ADM-002 | 退号冲减；IF-01 `statusOptions` |
| 号源 | HealthcareService / serviceType | 挂号 AS-OP-FIN-001 | 便民/普通/专家 |
| 导出 Tab | `ExportOutpatientRegistrationDetailReportRequest.presentationMode` | BR-RPT-ORG-005 | SUMMARY/DETAIL |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `EncounterQueryService` | `summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType` | 02、04 |
| `EncounterQueryService` | `pageOutpatientRegistrationDetailReport` | 03、04 |
| `EncounterQueryService` | `countOutpatientRegistrationDetailReportStats` | 03 |
| `EncounterQueryService` | `filterEncounterIdsByActiveAdmitterDoctor`（Support 医生筛预写 `resolvedEncounterIds`） | 02～04 |
| `EncounterQueryService` | `listEncounterIdsForRegistrationBusinessWindow`（无关键字时医生候选；可叠 `statusEnum`） | 02～04 |

编排序列：Gate → 校验（含 `statusEnum`）→ Support（医生/关键字 → `resolvedEncounterIds`）→ Query → 医保/费用 Asm → Response/Excel。

---

## 3. 按接口 ID 命名展开

### 3.1 IF-RPT-ORG-01 `loadOutpatientRegistrationDetailReportPageInit`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientRegistrationDetailReport/loadOutpatientRegistrationDetailReportPageInit` |
| **Request** | 空体占位 |
| **Response** | `defaultDateFrom/To`（当日）；`defaultPageSize`；`defaultSummaryView=DEPARTMENT`；`organizationOptions`；`practitionerOptions`；`serviceTypeOptions`；`summaryViewOptions`；`statusOptions`（已挂号/在诊/退号等） |
| **编排序列** | 1 Gate 登录 → 2 科室/医生岗/号源/视角/就诊状态 → 3 Asm |
| **异常** | EX-RPT-ORG-101 |

### 3.2 IF-RPT-ORG-02 `summarizeOutpatientRegistrationDetailReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientRegistrationDetailReport/summarizeOutpatientRegistrationDetailReport` |
| **Request** | `dateFrom`/`dateTo`（必填）；`summaryView`；`organizationId`；`practitionerId`（接诊医生 ADMITTER；空=不收窄）；`keyword`；`statusEnum`（空=不收窄） |
| **Response** | `rows[]`（维度键、科室/接诊医生/号源名、净笔数）；`totalNetRegistrationCount` |
| **编排序列** | 1 Gate → 2 日期/视角/`statusEnum` 校验 → 3 Support scope → 4 `summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType` → 5 Enricher → 6 Response |
| **异常** | EX-RPT-ORG-102/103 |

### 3.3 IF-RPT-ORG-03 `pageOutpatientRegistrationDetailReportDetail`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientRegistrationDetailReport/pageOutpatientRegistrationDetailReportDetail` |
| **Request** | 同筛选 + `pageNo`/`pageSize`；关键字用基类 `keyword`；`practitionerId`=接诊医生；`statusEnum` 可选 |
| **Response** | 分页 `content`：患者、门诊号、科室、**接诊医生**、号源、状态、挂号时间、费用性质、医保类型、挂号费；`footer`：挂号/有效/已退 |
| **编排序列** | 1 Gate → 2 校验 → 3 Support（ADMITTER 医生筛）→ 4 `pageOutpatientRegistrationDetailReport` → 5 `countOutpatientRegistrationDetailReportStats` → 6 Enricher（BR-RPT-ORG-004；不展示挂号医生）→ 7 `PagedResultBuilder` + footer |
| **异常** | EX-RPT-ORG-102/103 |

### 3.4 IF-RPT-ORG-04 `exportOutpatientRegistrationDetailReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientRegistrationDetailReport/exportOutpatientRegistrationDetailReport` |
| **Request** | 同筛选（含 `statusEnum`）+ `presentationMode=SUMMARY\|DETAIL` |
| **Response** | `fileName`/`contentType`/`body`/`rowCount`/`truncated` |
| **编排序列** | 1 Gate → 2 校验 mode → 3 SUMMARY 走 02 全量或 DETAIL 分页循环至 `EXPORT_MAX_ROWS` → 4 Excel |
| **异常** | EX-RPT-ORG-102/104 |

---

## 4. QueryService 与领域事件

目标态 Encounter 报表读（DM-ADM-002 §6.2.34～35、§6.2.34a～b）；**无写事件**。

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

| 承诺 | 实现 |
| --- | --- |
| 纯读 | 无 `@Transactional` |
| 导出上限 | `OutpatientRegistrationReportConstants.EXPORT_MAX_ROWS` |
| 性能 | 条件下推；医生/关键字预写 `resolvedEncounterIds` |

---

## 7. 测试要点

退号净汇总；号源分列；医保类型列；导出仅当前 Tab。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.5 | 2026-09-09 | 状态筛：`statusEnum` 贯通 02～04；IF-01 `statusOptions`；领域 Query 规约 equal；医生候选改 coalesce 业务日窗并可叠状态 |
| v1.4 | 2026-08-03 | 医生筛/汇总医生维/明细列改为接诊医生；明细页脚挂/退；去掉挂号医生展示与导出列 |
| v1.3 | 2026-08-02 | 费用性质明确为挂号身份（优先账户），不跟收费行账户 |
| v1.2 | 2026-08-02 | （已回退）曾误跟挂号费行 `account_id` |
| v1.1 | 2026-07-21 | 补 §3 IF 契约；DI 对齐实现（Enricher 费用性质/挂号费；去掉未用 Payment/ContextReader） |
| v1.0 | 2026-07-21 | 首版 |
