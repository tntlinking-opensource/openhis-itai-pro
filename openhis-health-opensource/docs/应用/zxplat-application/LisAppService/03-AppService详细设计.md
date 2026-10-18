# LisAppService · 详细设计

> **原则**：**1 个主页面 = 1 个 AppService 类** → `/api/app/{camelCaseAppService}/{methodName}`  
> **菜单与路由真源**：[02-交互说明.md](./02-交互说明.md)  
> **功能 IF 真源**：[00-功能清单.md](./00-功能清单.md)  
> **领域模型**：[04-FHIR领域模型.md](./04-FHIR领域模型.md)  
> **版本**：v2.1 · **日期**：2026-06-27

---

## 一、统计

| 类别 | 主页面数 | AppService 数 | 本文档 |
|------|----------|---------------|--------|
| **zxplat/lis 核心** | 18 | 18 | §二～§五 |
| **zxplat-module-lis-kingmed 增值** | 2 | 2 | §六 |
| **zxplat-module-lis-collector 增值** | 1 | 1 | §六 |
| **合计** | **21** | **21** | |

### 1.1 API 规范

```text
基路径：  /api/app/{camelCaseAppService}/{methodName}
示例：    /api/app/lisSampling/searchWorkItems
请求体：  JSON（Request DTO）
响应体：  JSON（Response DTO / PageResult<T>）
分页：    统一使用 PageResult<T>，入参 pageNum / pageSize
错误码：  whale-framework 标准错误码体系，LIS 前缀 5-01-xxxx
```

### 1.2 公共约定

- 所有查询方法支持 `tenantId` / `orgId` 过滤（由框架 Resolver 自动注入，不在方法签名中显式声明）
- 时间参数统一使用 `LocalDate` / `Instant`
- 列表查询默认按 `createTime DESC` 排序
- 领域对象不允许穿透到 UI 层；Application 方法返回 DTO，Assembler 负责转换

---

## 二、检验业务 AS（7 页面）

---

### AS-LIS-SMP-001 · 采样工作台

| 属性 | 值 |
|------|-----|
| **类名** | `LisSamplingAppService` |
| **路由** | `/lis/sampling` |
| **优先级** | P0 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-SMP-001-采样工作台.md) |

#### §1 页面边界

采样工作台面向检验科采血/采样护士。包含 Tab：待采样列表、登记列表、拒收列表、本地开单（P2）。

**不出现在此页面**：仪器结果录入 → REP-001；审核操作 → AUD-001；条码标签版式设计 → PrintTemplate（AS-BAS-PRT）。

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| SMP-IF-01 | IF-LIS-SMP-01 | `searchWorkItems` | POST | `/searchWorkItems` | patientName, deptId, dateRange, statusList, pageNum, pageSize | PageResult<SamplingWorkItemDTO> | P0 |
| SMP-IF-02 | IF-LIS-SMP-02 | `generateBarcode` | POST | `/generateBarcode` | workItemId | SamplingWorkItemDTO(含新 barcode) | P0 |
| SMP-IF-03 | IF-LIS-SMP-03 | `collect` | POST | `/collect` | workItemId, collectedAt | void | P0 |
| SMP-IF-04 | IF-LIS-SMP-03 | `batchCollect` | POST | `/batchCollect` | workItemIds[], collectedAt | void（含失败明细） | P0 |
| SMP-IF-05 | IF-LIS-SMP-05 | `reject` | POST | `/reject` | workItemId, rejectReason | void | P1 |
| SMP-IF-06 | IF-LIS-SMP-05 | `recollect` | POST | `/recollect` | workItemId, newBarcode? | SamplingWorkItemDTO | P1 |
| SMP-IF-07 | IF-LIS-SMP-06 | `localRegister` | POST | `/localRegister` | LocalRegisterDTO | SamplingWorkItemDTO | P2 |
| SMP-IF-08 | IF-LIS-SMP-07 | `cancelCollect` | POST | `/cancelCollect` | workItemId, reason | void | P1 |
| SMP-IF-09 | IF-LIS-SMP-08 | `searchRegisterList` | POST | `/searchRegisterList` | deptId, dateRange, pageNum, pageSize | PageResult<RegisterItemDTO> | P1 |
| SMP-IF-10 | IF-LIS-SMP-09 | `quickReject` | POST | `/quickReject` | workItemId, rejectReason | void | P1 |
| SMP-IF-11 | — | `getWorkItemDetail` | GET | `/getWorkItemDetail` | workItemId | SamplingWorkItemDTO（含 orderLinks） | P0 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-SMP-01** | 采样签收前校验：LisWorkItem 状态须为 `available`；未收费是否允许采样读 `lis_config.sampling.allowWithoutCharge` |
| **BR-SMP-02** | 条码生成：1 个 LisWorkItem = 1 个 barcode；规则（前缀+日期+流水号）读 `lis_config.barcode.*` |
| **BR-SMP-03** | 拒收：状态变为 `unsatisfactory`，记录拒收原因；可新建 LisWorkItem 重采（recollect） |
| **BR-SMP-04** | 签收后不可编辑 orderLinks；签收时间记录到 `LisWorkItem.collectionTime` |
| **BR-SMP-05** | 本地开单（P2）：仅限未从 HIS 传入的 LisWorkItem；须关联 patientId + encounterId |
| **BR-SMP-06** | 采样退单（cancelCollect）：状态从 `collected` → `entered-in-error`，原因必填 |
| **BR-SMP-07** | 快检拒收：急诊标本已采样但未送出，可标记拒收并通知 HIS 撤单 |

---

### AS-LIS-SAM-001 · 样本管理

| 属性 | 值 |
|------|-----|
| **类名** | `LisSpecimenAppService` |
| **路由** | `/lis/specimen` |
| **优先级** | P1 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-SAM-001-标本管理.md) |
| **子模块** | SAM-TAT（TAT 时效监控）、SAM-TRANS（标本转运交接） |

#### §1 页面边界

检验科样本管理员视角。Tab：样本状态总览（含 TAT 超时预警）、上机核收、转运交接、试管架管理、标本附件。

#### §2 IF 表（SAM 本体）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| SAM-IF-01 | IF-LIS-SAM-01 | `searchSpecimens` | POST | `/searchSpecimens` | barcode, sampleNo, statusEnum, deptId, dateRange, pageNum, pageSize | PageResult<SpecimenDTO> | P1 |
| SAM-IF-02 | IF-LIS-SAM-02 | `receive` | POST | `/receive` | workItemId, receivedAt | void | P1 |
| SAM-IF-03 | IF-LIS-SAM-02 | `batchReceive` | POST | `/batchReceive` | workItemIds[], receivedAt | void（含失败明细） | P1 |
| SAM-IF-04 | IF-LIS-SAM-02 | `startProcessing` | POST | `/startProcessing` | workItemId, deviceId, startedAt | void | P1 |
| SAM-IF-05 | IF-LIS-SAM-03 | `assignRack` | POST | `/assignRack` | workItemId, rackNo, position | void | P2 |
| SAM-IF-06 | IF-LIS-SAM-03 | `searchByRack` | POST | `/searchByRack` | rackNo | List<SpecimenDTO> | P2 |
| SAM-IF-07 | IF-LIS-SAM-04 | `uploadAttachment` | POST | `/uploadAttachment` | workItemId, file(Multipart) | AttachmentDTO | P2 |
| SAM-IF-08 | — | `getSpecimenDetail` | GET | `/getSpecimenDetail` | workItemId | SpecimenDTO（含 orderLinks + TAT 信息） | P1 |
| SAM-IF-09 | — | `getStatusTimeline` | GET | `/getStatusTimeline` | workItemId | List<StatusChangeLogDTO> | P1 |

#### §2a IF 表（SAM-TAT）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| TAT-IF-01 | IF-LIS-TAT-01 | `getTatSummary` | GET | `/tat/summary` | deptId, dateRange | TatSummaryDTO（各阶段超时计数、完成数、TAT 中位数） | P0 |
| TAT-IF-02 | IF-LIS-TAT-03 | `getOverdueList` | POST | `/tat/overdue` | deptId, statusEnum, severity, pageNum, pageSize | PageResult<TatOverdueItemDTO> | P1 |
| TAT-IF-03 | IF-LIS-TAT-04 | `getTatStats` | POST | `/tat/stats` | deptId, dateRange, groupBy(project/dept/shift), pageNum, pageSize | PageResult<TatStatsDTO> | P1 |

#### §2b IF 表（SAM-TRANS）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| TRANS-IF-01 | IF-LIS-TRANS-01 | `createTransferBatch` | POST | `/trans/batch` | sourceLocation, workItemIds[] | TransferBatchDTO | P1 |
| TRANS-IF-02 | IF-LIS-TRANS-02 | `confirmReceive` | POST | `/trans/confirm` | transferBatchId, targetLocation, countVerified | void（差异告警） | P1 |
| TRANS-IF-03 | IF-LIS-TRANS-05 | `searchTransferLogs` | POST | `/trans/logs` | workItemId?, operId?, dateRange, pageNum, pageSize | PageResult<TransferLogDTO> | P1 |
| TRANS-IF-04 | IF-LIS-TRANS-03 | `interDeptTransfer` | POST | `/trans/interDept` | workItemIds[], targetLabDept | void | P2 |
| TRANS-IF-05 | IF-LIS-TRANS-04 | `externalShipment` | POST | `/trans/external` | workItemIds[], carrier, trackingNo | void | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-SAM-01** | 核收校验：LisWorkItem 状态须为 `collected`；核收后状态 → `received` |
| **BR-SAM-02** | 上机校验：LisWorkItem 状态须为 `received`；deviceId 必须在 `lis_device_capability` 中有匹配的能力行 |
| **BR-SAM-03** | TAT 时间戳自动记录：状态变更时同步写入 `LisWorkItem.statusUpdatedAt`，不可手动修改 |
| **BR-SAM-04** | TAT 超时阈值生效：读取 `lis_config.tat.timeout.*`；无科室级配置时 fallback 全局默认值 |
| **BR-SAM-05** | 转运签收差异告警：确认接收时若 countVerified ≠ 实际 batch 内标本数，记录差异并通知采样台 |
| **BR-SAM-06** | 专业组间交接：目标专业组须允许接收该标本类型 |

---

### AS-LIS-AUD-001 · 结果审核

| 属性 | 值 |
|------|-----|
| **类名** | `LisResultAuditAppService` |
| **路由** | `/lis/result-audit` |
| **优先级** | P0 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-AUD-001-结果审核.md) |
| **子模块** | AUD-AUTO（自动审核规则）、AUD-REVIEW（审核分级双签） |

#### §1 页面边界

检验师审核结果主页面。Tab：待审核列表、已审核列表、自动审核规则配置、审核日志。支持按条码/样本号快速检索。

#### §2 IF 表（AUD 本体）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| AUD-IF-01 | IF-LIS-AUD-01 | `searchPendingAudit` | POST | `/searchPendingAudit` | deptId, dateRange, urgency, pageNum, pageSize | PageResult<PendingAuditDTO> | P0 |
| AUD-IF-02 | IF-LIS-AUD-02 | `auditSingle` | POST | `/auditSingle` | workItemId, orderLinkId?, result | void | P0 |
| AUD-IF-03 | IF-LIS-AUD-03 | `batchAudit` | POST | `/batchAudit` | workItemIds[], result | void（含失败明细） | P1 |
| AUD-IF-04 | IF-LIS-AUD-04 | `batchCancelAudit` | POST | `/batchCancelAudit` | auditIds[], reason | void | P1 |
| AUD-IF-05 | IF-LIS-AUD-05 | `publishReport | POST | `/publishReport | workItemId, orderLinkId | DiagnosticReportId | P0 |
| AUD-IF-06 | IF-LIS-AUD-07 | `searchByBarcode` | GET | `/searchByBarcode` | barcode | AuditItemDTO（含 results + status） | P0 |
| AUD-IF-07 | — | `getResultDetail` | GET | `/getResultDetail` | observationId | ObservationDetailDTO | P0 |
| AUD-IF-08 | — | `getAuditHistory` | GET | `/getAuditHistory` | workItemId, orderLinkId? | List<AuditLogDTO> | P1 |

#### §2a IF 表（AUD-AUTO）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| AUTO-IF-01 | IF-LIS-AUTO-01 | `searchAutoRules` | POST | `/auto/search` | projectCode?, ruleType?, pageNum, pageSize | PageResult<AutoAuditRuleDTO> | P1 |
| AUTO-IF-02 | IF-LIS-AUTO-01 | `saveAutoRule` | POST | `/auto/save` | AutoAuditRuleDTO | AutoAuditRuleDTO | P1 |
| AUTO-IF-03 | IF-LIS-AUTO-01 | `deleteAutoRule` | POST | `/auto/delete` | ruleId | void | P1 |
| AUTO-IF-04 | IF-LIS-AUTO-04 | `getAutoAuditLog` | POST | `/auto/log` | projectCode?, dateRange, veredict, pageNum, pageSize | PageResult<AutoAuditLogDTO> | P1 |
| AUTO-IF-05 | IF-LIS-AUTO-02 | `getDeltaCheck` | GET | `/auto/deltaCheck` | patientId, observationId | DeltaCheckResultDTO | P1 |

#### §2b IF 表（AUD-REVIEW）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| REVIEW-IF-01 | IF-LIS-REVIEW-01 | `getReviewConfig` | GET | `/review/config` | projectCode? | ReviewConfigDTO | P2 |
| REVIEW-IF-02 | IF-LIS-REVIEW-01 | `saveReviewConfig` | POST | `/review/config` | ReviewConfigDTO | void | P2 |
| REVIEW-IF-03 | IF-LIS-REVIEW-02 | `dualSign` | POST | `/review/dualSign` | workItemId, orderLinkId | void | P2 |
| REVIEW-IF-04 | IF-LIS-REVIEW-03 | `delegateAudit` | POST | `/review/delegate` | delegatorId, delegateId, dateRange | void | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-AUD-01** | 审核前置：Observation 状态须为 `preliminary`；且 LisWorkItemOrder.linkStatusEnum 须为 `result_pending` |
| **BR-AUD-02** | 单条审核通过：`Observation.statusEnum` → `final`，`orderLink.linkStatusEnum` → `published` |
| **BR-AUD-03** | 批量审核：仅同 `workItemId` 下的所有 `orderLink` 可批量操作 |
| **BR-AUD-04** | 发布条件：所有 orderLink 的 Observation 均为 `final`（或 `cancelled`）时触发 `DiagnosticReport 生成 + 回写 health |
| **BR-AUD-05** | 取消审核：`Observation.statusEnum` final → preliminary；`linkStatusEnum` published → result_pending；记录 AuditLog |
| **BR-AUD-06** | 自动审核规则链顺序执行：参考范围 → Delta Check → 仪器异常标记 → 危急值；任一拦截则终止链，标记为人工审核 |
| **BR-AUD-07** | Delta Check 阈值读 referenceRange；患者最近一次同项目 Observation 值差异超阈值 → 拦截 |
| **BR-AUD-08** | 双签：forceDoubleSign 项目列表存 `lis_config.review.force_double_sign_activities`；level1 通过后 linkStatusEnum = `reviewed_level1`，待 level2 通过后 → `published` |
| **BR-AUD-09** | 审核委托：仅允许同科室或上级权限间委托；委托期间委托人的审核计入委托人记录 |

---

### AS-LIS-REP-001 · 报告管理

| 属性 | 值 |
|------|-----|
| **类名** | `LisReportManageAppService` |
| **路由** | `/lis/report-manage` |
| **优先级** | P0 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-REP-001-报告管理.md) |
| **子模块** | REP-REVISE（报告召回与修订） |

#### §1 页面边界

检验科报告操作主页面。Tab：报告列表、报告预览（PDF/HTML）、报告明细编辑、批量打印。版式模板设计归 AS-LIS-CFG-005 + PrintTemplate（AS-BAS-PRT）。

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| REP-IF-01 | IF-LIS-REP-01 | `previewReport | GET | `/previewReport | diagnosticReportId, format(pdf/html) | ReportPreviewDTO | P0 |
| REP-IF-02 | IF-LIS-REP-02 | `getReportDetail` | GET | `/getReportDetail` | diagnosticReportId | ReportDetailDTO（结构化） | P0 |
| REP-IF-03 | IF-LIS-REP-03 | `saveResult` | POST | `/saveResult` | observationId, value, unitCode, note | ObservationDTO | P1 |
| REP-IF-04 | IF-LIS-REP-04 | `getEditLog` | GET | `/getEditLog` | observationId | List<ResultEditLogDTO> | P1 |
| REP-IF-05 | IF-LIS-REP-05 | `deleteReport | POST | `/deleteReport | diagnosticReportId, reason | void | P1 |
| REP-IF-06 | IF-LIS-REP-06 | `batchPrint` | POST | `/batchPrint` | diagnosticReportIds[], printerId | PrintJobDTO | P0 |
| REP-IF-07 | IF-LIS-REP-07 | `downloadFile` | GET | `/downloadFile` | diagnosticReportId, format | BinaryStream | P1 |
| REP-IF-08 | IF-LIS-REP-09 | `finalizeReport | POST | `/finalizeReport | diagnosticReportId | void（触发 AUD-05） | P0 |
| REP-IF-09 | — | `searchReports` | POST | `/searchReports` | patientName, barcode, dateRange, statusEnum, pageNum, pageSize | PageResult<ReportListItemDTO> | P0 |

#### §2a IF 表（REP-REVISE）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| REVISE-IF-01 | IF-LIS-REVISE-01 | `recallReport | POST | `/revise/recall` | diagnosticReportId, reason | void（状态 → amended） | P2 |
| REVISE-IF-02 | IF-LIS-REVISE-02 | `getRevisionHistory` | GET | `/revise/history` | diagnosticReportId | List<RevisionHistoryDTO> | P2 |
| REVISE-IF-03 | IF-LIS-REVISE-03 | `reAuditAndPublish` | POST | `/revise/publish` | diagnosticReportId | void | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-REP-01** | 报告预览：PDF 生成走 PrintTemplate（AS-BAS-PRT）；HTML 由 LIS 组装结构化数据后 client 渲染 |
| **BR-REP-02** | 手工录入结果：仅允许 `Observation.statusEnum = preliminary` 或不存在时创建；记录编辑日志 |
| **BR-REP-03** | 删除报告：仅 `DiagnosticReport.status ≠ final` 时可删；删除后 Observation 回退为 `preliminary` |
| **BR-REP-04** | 批量打印：传入 printerId 校验打印机在线状态 |
| **BR-REP-05** | 召回修订：仅 `DiagnosticReport.status = final` 可召回；召回后 `DiagnosticReport.status → amended`，原版本保留为 previous；所有 Observation 的 previous 引用原值 |
| **BR-REP-06** | 修订后重新发布：走完整审核流程（AUD-02/AUTO）；发布后触发 `DiagnosticReportAmendedEvent` |

---

### AS-LIS-REP-002 · 历史报告

| 属性 | 值 |
|------|-----|
| **类名** | `LisReportHistoryAppService` |
| **路由** | `/lis/report-history` |
| **优先级** | P1 |

#### §1 页面边界

按患者/就诊查询历史检验报告；支持跨就诊报告对比（P2）。

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| REPH-IF-01 | IF-LIS-REP-H01 | `searchByPatient` | POST | `/searchByPatient` | patientId | List<ReportSummaryDTO> | P1 |
| REPH-IF-02 | IF-LIS-REP-H01 | `searchByEncounter` | POST | `/searchByEncounter` | encounterId | List<ReportSummaryDTO> | P1 |
| REPH-IF-03 | IF-LIS-REP-H02 | `compareReports` | POST | `/compareReports` | diagnosticReportIds[] | CompareResultDTO | P2 |
| REPH-IF-04 | — | `getReportTimeline` | GET | `/getReportTimeline` | patientId, projectCode? | List<ReportPointDTO> | P1 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-REPH-01** | 历史报告仅查询 `DiagnosticReport.status = final` 或 `amended` |
| **BR-REPH-02** | 跨就诊对比：最多选择 5 份报告；按项目码对齐展示差值 |

---

### AS-LIS-CRT-001 · 危急值

| 属性 | 值 |
|------|-----|
| **类名** | `LisCriticalValueAppService` |
| **路由** | `/lis/critical-value` |
| **优先级** | P1 |

#### §1 页面边界

危急值管理主页面。Tab：危急值列表、规则配置、通知记录、闭环确认、工作量统计。

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CRT-IF-01 | IF-LIS-CRT-01 | `searchRules` | POST | `/rules/search` | projectCode?, pageNum, pageSize | PageResult<CriticalRuleDTO> | P1 |
| CRT-IF-02 | IF-LIS-CRT-01 | `saveRule` | POST | `/rules/save` | CriticalRuleDTO | CriticalRuleDTO | P1 |
| CRT-IF-03 | IF-LIS-CRT-02 | `searchIssues` | POST | `/search` | deptId, dateRange, severity, statusEnum, pageNum, pageSize | PageResult<CriticalIssueDTO> | P1 |
| CRT-IF-04 | IF-LIS-CRT-02 | `notify` | POST | `/notify` | issueId, notifyMethod, recipient | void | P1 |
| CRT-IF-05 | IF-LIS-CRT-03 | `acknowledge` | POST | `/acknowledge` | issueId, mitigationAction | void | P2 |
| CRT-IF-06 | IF-LIS-CRT-04 | `exportStats` | POST | `/stats/ex旧命名（已废止） | deptId, dateRange, format(excel/csv) | BinaryStream | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CRT-01** | 危急值触发：Observation.interpretation = HH/LL + 值超过阈值 → 创建 DetectedIssue（status=preliminary） |
| **BR-CRT-02** | 危急值阈值配置按项目+性别+年龄分层；无分层配置时 fallback 默认 |
| **BR-CRT-03** | 通知完成 → DetectedIssue.status → final |
| **BR-CRT-04** | 闭环确认：临床确认后 `mitigatedAt` 记录确认时间；超时未确认（`lis_config.crt.acknowledgeTimeoutMinutes`）→ 升级通知 |

---

### AS-LIS-RES-002 · 无主结果绑定

| 属性 | 值 |
|------|-----|
| **类名** | `LisOrphanResultAppService` |
| **路由** | `/lis/orphan-result` |
| **优先级** | P2 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| ORPHAN-IF-01 | IF-LIS-RES-ORPHAN-01 | `searchOrphans` | POST | `/search` | barcode?, deviceId?, dateRange, pageNum, pageSize | PageResult<OrphanResultDTO> | P2 |
| ORPHAN-IF-02 | IF-LIS-RES-ORPHAN-01 | `bindToPatient` | POST | `/bind` | orphanResultId, patientId, encounterId | void | P2 |
| ORPHAN-IF-03 | IF-LIS-RES-ORPHAN-01 | `bindToWorkItem` | POST | `/bindToWorkItem` | orphanResultId, workItemId, serviceRequestId | void | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-ORPHAN-01** | 无主结果来源：仪器回传 barcode 在 lis_work_item 中匹配不到 → 暂存为无主结果 |
| **BR-ORPHAN-02** | 绑定患者：创建新的 LisWorkItem + Observation；绑定后不可解绑 |

---

## 三、检验配置 AS（7 页面）

---

### AS-LIS-CFG-001 · 检验目录维护

| 属性 | 值 |
|------|-----|
| **类名** | `LisCatalogConfigAppService` |
| **路由** | `/lis/config/catalog` |
| **优先级** | P0/P1 |
| **说明** | 检验组合/细项（CFG-01/02，P1）；**不**含 HIS↔LIS map（CFG-07 废止，见 [01 §10.4](./01-概要设计.md#104-项目对照--一体化无-mapd3)） |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG1-IF-01 | IF-LIS-CFG-01 | `searchGroups` | POST | `/group/search` | keyword?, statusEnum?, pageNum, pageSize | PageResult<LabGroupDTO> | P1 |
| CFG1-IF-02 | IF-LIS-CFG-01 | `saveGroup` | POST | `/group/save` | LabGroupDTO | LabGroupDTO | P1 |
| CFG1-IF-03 | IF-LIS-CFG-02 | `searchItems` | POST | `/item/search` | groupId?, keyword?, pageNum, pageSize | PageResult<LabItemDTO> | P1 |
| CFG1-IF-04 | IF-LIS-CFG-02 | `saveItem` | POST | `/item/save` | LabItemDTO | LabItemDTO | P1 |
| CFG1-IF-05 | ~~IF-LIS-CFG-07~~ | ~~`searchMapping`~~ | — | — | — | — | **废止**（[01 §10.4](./01-概要设计.md#104-项目对照--一体化无-mapd3)） |
| CFG1-IF-06 | ~~IF-LIS-CFG-07~~ | ~~`saveMapping`~~ | — | — | — | — | **废止** |
| CFG1-IF-07 | ~~IF-LIS-CFG-07~~ | ~~`batchImportMapping`~~ | — | — | — | — | **废止** |

> **方案 A（[01 §10.4](./01-概要设计.md#104-项目对照--一体化无-mapd3)）**：CFG1-IF-05～07（`lis_item_map` / `ZL_DIC` 对照 UI）**一体化不实现**。外购或 Gateway 院点若需遗留对照，仅在 **lis-adapter** SDD 内维护私有码表。

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CFG1-01** | 可检验性（P0）：每个开单 `activity_id` 须在 **`lis_device_capability`** 至少一条在用配置；接单 **BR-INT-06** 强制校验 |
| **BR-CFG1-02** | 检验组合（P1）：组合内细项须均有 `device_capability` 或按 `grouping.fallbackWhenNoProfile` 降级 |
| **BR-CFG1-03** | 禁用组合/细项：已存在进行中 LisWorkItem 关联时不允许禁用 |

---

### AS-LIS-CFG-002 · 标本与参考范围

| 属性 | 值 |
|------|-----|
| **类名** | `LisSpecimenRefConfigAppService` |
| **路由** | `/lis/config/specimen-ref` |
| **优先级** | P1 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG2-IF-01 | IF-LIS-CFG-03 | `searchSpecimenTypes` | POST | `/specimen/search` | keyword?, pageNum, pageSize | PageResult<SpecimenTypeDTO> | P1 |
| CFG2-IF-02 | IF-LIS-CFG-03 | `saveSpecimenTypeMapping` | POST | `/specimen/saveMapping` | lisSpecimenCode, healthSpecimenDefId | void | P1 |
| CFG2-IF-03 | IF-LIS-CFG-10 | `searchRefRanges` | POST | `/ref/search` | projectCode?, gender?, ageGroup?, pageNum, pageSize | PageResult<RefRangeDTO> | P1 |
| CFG2-IF-04 | IF-LIS-CFG-10 | `saveRefRange` | POST | `/ref/save` | RefRangeDTO | RefRangeDTO | P1 |
| CFG2-IF-05 | IF-LIS-CFG-10 | `batchImportRefRange` | POST | `/ref/import | file(Excel) | ImportResultDTO | P1 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CFG2-01** | 参考范围按项目+性别+年龄分组分层；默认范围未配置时自动审核判定为"无参考范围，需人工" |
| **BR-CFG2-02** | 标本类型对照：对齐 health `SpecimenDefinition`，LIS 仅存扩展字段 |

---

### AS-LIS-CFG-003 · 仪器与能力

| 属性 | 值 |
|------|-----|
| **类名** | `LisDeviceConfigAppService` |
| **路由** | `/lis/config/device` |
| **优先级** | P0 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-CFG-003-仪器与能力.md) |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG3-IF-01 | IF-LIS-CFG-04 | `searchDevices` | POST | `/search` | keyword?, statusEnum?, pageNum, pageSize | PageResult<DeviceDTO> | P0 |
| CFG3-IF-02 | IF-LIS-CFG-04 | `saveDevice` | POST | `/save` | DeviceDTO | DeviceDTO | P0 |
| CFG3-IF-03 | IF-LIS-CFG-04 | `getDeviceDetail` | GET | `/getDetail` | deviceId | DeviceDetailDTO（含 channels + capability + 双向配置） | P0 |
| CFG3-IF-04 | IF-LIS-CFG-05 | `searchChannels` | POST | `/channel/search` | deviceId | List<DeviceChannelDTO> | P0 |
| CFG3-IF-05 | IF-LIS-CFG-05 | `saveChannel` | POST | `/channel/save` | DeviceChannelDTO | DeviceChannelDTO | P0 |
| CFG3-IF-06 | IF-LIS-CFG-08 | `searchCapability` | POST | `/capability/search` | deviceId, activityId?, tubeSchemeId?, pageNum, pageSize | PageResult<DeviceCapabilityDTO> | P0 |
| CFG3-IF-07 | IF-LIS-CFG-08 | `saveCapability` | POST | `/capability/save` | DeviceCapabilityDTO | DeviceCapabilityDTO | P0 |
| CFG3-IF-08 | IF-LIS-CFG-08 | `batchImportCapability` | POST | `/capability/import | deviceId, file(Excel) | ImportResultDTO | P1 |

#### §2a IF 表（BIDI 双向通讯配置）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| BIDI-IF-01 | — | `getBidiConfig` | GET | `/bidi/config` | deviceId | BidiConfigDTO | P2 |
| BIDI-IF-02 | — | `saveBidiConfig` | POST | `/bidi/config` | BidiConfigDTO | void | P2 |
| BIDI-IF-03 | IF-LIS-BIDI-02 | `getDeviceStatus` | GET | `/bidi/status` | deviceId | DeviceStatusDTO（心跳/故障码） | P2 |

#### §2b IF 表（REAGENT 试剂批号）

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| RGNT-IF-01 | IF-LIS-REAGENT-01 | `searchReagents` | POST | `/reagent/search` | deviceId?, keyword?, pageNum, pageSize | PageResult<ReagentDTO> | P2 |
| RGNT-IF-02 | IF-LIS-REAGENT-02 | `saveReagentLot` | POST | `/reagent/saveLot` | ReagentLotDTO | ReagentLotDTO | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CFG3-01** | 仪器通道号在同一 deviceId 下唯一 |
| **BR-CFG3-02** | `lis_device_capability` 每行 (deviceId, activityId, tubeSchemeId) 唯一 |
| **BR-CFG3-03** | 禁用仪器前校验：无进行中 LisWorkItem 关联该仪器 |
| **BR-CFG3-04** | 双向通讯：C++ 程序连接信息（地址、端口、Token）存储在 `lis_device_channel` 扩展字段；worklist 格式模板存储为 JSON |
| **BR-CFG3-05** | 试剂批号关联仪器：开瓶效期超期时在审核页提示 |

---

### AS-LIS-CFG-004 · 合管与试管方案

| 属性 | 值 |
|------|-----|
| **类名** | `LisMergeProfileConfigAppService` |
| **路由** | `/lis/config/merge` |
| **优先级** | P0 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-CFG-004-合管与试管方案.md) |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG4-IF-01 | IF-LIS-CFG-08 | `searchTubeSchemes` | POST | `/tube/search` | keyword?, specimenDefId?, pageNum, pageSize | PageResult<TubeSchemeDTO> | P0 |
| CFG4-IF-02 | IF-LIS-CFG-08 | `saveTubeScheme` | POST | `/tube/save` | TubeSchemeDTO | TubeSchemeDTO | P0 |
| CFG4-IF-03 | IF-LIS-CFG-08 | `searchProfiles` | POST | `/profile/search` | keyword?, tubeSchemeId?, pageNum, pageSize | PageResult<MergeProfileDTO> | P0 |
| CFG4-IF-04 | IF-LIS-CFG-08 | `saveProfile` | POST | `/profile/save` | MergeProfileDTO（含 items） | MergeProfileDTO | P0 |
| CFG4-IF-05 | IF-LIS-CFG-08 | `testMerge` | POST | `/profile/test` | serviceRequestIds[] | MergeTestResultDTO（预分组合并结果） | P0 |
| CFG4-IF-06 | — | `getProfileItems` | GET | `/profile/items` | profileId | List<ProfileItemDTO> | P0 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CFG4-01** | 试管方案：`containerType` + `additive` 组合唯一；`maxItemCount` 须 > 0 |
| **BR-CFG4-02** | 合管方案绑定试管方案：`merge_profile.tube_scheme_id` 必填 |
| **BR-CFG4-03** | 方案内项目：每个 `activity_id` 须至少有一台在用仪器的 `device_capability` 行可覆盖 |
| **BR-CFG4-04** | 测试合并（testMerge）：返回每组 LisWorkItem 预览，不实际落库 |

---

### AS-LIS-CFG-005 · 报告版式

| 属性 | 值 |
|------|-----|
| **类名** | `LisReportLayoutConfigAppService` |
| **路由** | `/lis/config/report-layout` |
| **优先级** | P2 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG5-IF-01 | IF-LIS-CFG-06 | `searchTemplates` | POST | `/template/search` | keyword?, pageNum, pageSize | PageResult<ReportTemplateDTO> | P2 |
| CFG5-IF-02 | IF-LIS-CFG-06 | `saveTemplate` | POST | `/template/save` | ReportTemplateDTO | ReportTemplateDTO | P2 |
| CFG5-IF-03 | IF-LIS-CFG-06 | `previewTemplate` | POST | `/template/preview` | templateId, sampleData | ReportPreviewDTO | P2 |

---

### AS-LIS-CFG-006 · 条码与打印机

| 属性 | 值 |
|------|-----|
| **类名** | `LisBarcodePrintConfigAppService` |
| **路由** | `/lis/config/barcode-print` |
| **优先级** | P1 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG6-IF-01 | IF-LIS-CFG-11 | `saveBarcodeRule` | POST | `/barcode/save` | BarcodeRuleDTO | BarcodeRuleDTO | P1 |
| CFG6-IF-02 | IF-LIS-CFG-11 | `getBarcodeRule` | GET | `/barcode/get` | — | BarcodeRuleDTO | P1 |
| CFG6-IF-03 | IF-LIS-CFG-12 | `searchPrinters` | POST | `/printer/search` | keyword?, locationId?, pageNum, pageSize | PageResult<PrinterDTO> | P2 |
| CFG6-IF-04 | IF-LIS-CFG-12 | `savePrinter` | POST | `/printer/save` | PrinterDTO | PrinterDTO | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CFG6-01** | 条码规则：前缀（科室码/项目码）+ 日期格式 + 流水号位数；规则变更不影响已生成条码 |
| **BR-CFG6-02** | 打印机绑定科室/工作站位置 |

---

### AS-LIS-CFG-007 · LIS 系统参数

| 属性 | 值 |
|------|-----|
| **类名** | `LisModuleSettingsAppService` |
| **路由** | `/lis/config/settings` |
| **优先级** | P0 |
| **契约状态** | ✅ [独立 AS](../AppService/AS-LIS-CFG-007-LIS系统参数.md) |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| CFG7-IF-01 | IF-LIS-CFG-09 | `searchConfig` | POST | `/search` | configKey?, module?, pageNum, pageSize | PageResult<LisConfigDTO> | P0 |
| CFG7-IF-02 | IF-LIS-CFG-09 | `saveConfig` | POST | `/save` | LisConfigDTO | LisConfigDTO | P0 |
| CFG7-IF-03 | IF-LIS-CFG-09 | `getConfigByKey` | GET | `/getByKey` | configKey | LisConfigDTO | P0 |
| CFG7-IF-04 | IF-LIS-TAT-02 | `getTatConfig` | GET | `/tat/get` | projectCode?, deptId?, urgency? | List<TatConfigDTO> | P0 |
| CFG7-IF-05 | IF-LIS-TAT-02 | `saveTatConfig` | POST | `/tat/save` | TatConfigDTO | TatConfigDTO | P0 |
| CFG7-IF-06 | IF-LIS-TAT-05 | `getHolidayConfig` | GET | `/tat/holidays` | year | List<HolidayConfigDTO> | P1 |
| CFG7-IF-07 | IF-LIS-TAT-05 | `saveHolidayConfig` | POST | `/tat/holidays` | HolidayConfigDTO | HolidayConfigDTO | P1 |
| CFG7-IF-08 | IF-LIS-AUTO-01 | `getAutoAuditConfig` | GET | `/auto/get` | — | AutoAuditConfigDTO（全局开关） | P1 |
| CFG7-IF-09 | IF-LIS-REVIEW-01 | `getReviewConfig` | GET | `/review/get` | — | ReviewConfigDTO（强制双签活动列表） | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-CFG7-01** | 禁止读写 framework `Setting` 表；所有 LIS 参数落在 `lis_config` |
| **BR-CFG7-02** | `lis_config` 作用域：`tenantId` 必填；`orgId` 可选（院区分歧时用） |
| **BR-CFG7-03** | TAT 阈值 key 格式：`tat.timeout.{projectCode}.{deptId}.{urgency}.{phase}`；通配符 `*` 表示全局默认 |
| **BR-CFG7-04** | 参数变更记录 AuditLog |
| **BR-CFG7-05** | 危险参数（如 `sampling.allowWithoutCharge`）变更需二次确认 |

---

## 四、质控与运营 AS（4 页面）

---

### AS-LIS-QC-001 · 质控管理

| 属性 | 值 |
|------|-----|
| **类名** | `LisQcAppService` |
| **路由** | `/lis/qc` |
| **优先级** | P2 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| QC-IF-01 | IF-LIS-QC-01 | `searchMaterials` | POST | `/material/search` | keyword?, level?, pageNum, pageSize | PageResult<QcMaterialDTO> | P2 |
| QC-IF-02 | IF-LIS-QC-01 | `saveMaterial` | POST | `/material/save` | QcMaterialDTO | QcMaterialDTO | P2 |
| QC-IF-03 | IF-LIS-QC-02 | `searchRuns` | POST | `/run/search` | deviceId?, dateRange, materialId?, pageNum, pageSize | PageResult<QcRunDTO> | P2 |
| QC-IF-04 | IF-LIS-QC-02 | `saveMeasurement` | POST | `/run/measure` | runId, projectCode, value | QcMeasurementDTO | P2 |
| QC-IF-05 | IF-LIS-QC-03 | `evaluateWestgard` | POST | `/run/evaluate` | runId | WestgardResultDTO | P2 |
| QC-IF-06 | — | `getLeveyJenningsChart` | GET | `/chart/lj` | materialId, projectCode, dateRange | ChartDataDTO | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-QC-01** | 质控品：批号+水平唯一；靶值 + SD 用于 Westgard |
| **BR-QC-02** | Westgard 规则：1-2s / 1-3s / 2-2s / R-4s / 4-1s / 10x；任一失控 → run 标记为失控 |
| **BR-QC-03** | QcMeasurement 可选投影为 Observation（category=laboratory, subject→QcMaterial） |

---

### AS-LIS-LOG-001 · 接口日志

| 属性 | 值 |
|------|-----|
| **类名** | `LisIntegrationLogAppService` |
| **路由** | `/lis/log/integration` |
| **优先级** | P1 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| LOG-IF-01 | IF-LIS-LOG-01 | `searchAuditEvents` | POST | `/search` | action?, source?, dateRange, outcome?, pageNum, pageSize | PageResult<AuditEventDTO> | P1 |
| LOG-IF-02 | IF-LIS-LOG-01 | `getAuditEventDetail` | GET | `/getDetail` | eventId | AuditEventDetailDTO（含 request/responseBody） | P1 |
| LOG-IF-03 | — | `resend` | POST | `/resend` | eventId | void | P1 |
| LOG-IF-04 | IF-LIS-LOG-02 | `searchOperationLog` | POST | `/operation/search` | operId?, module?, dateRange, pageNum, pageSize | PageResult<OperationLogDTO> | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-LOG-01** | 报文存档：requestBody / responseBody 敏感字段脱敏（患者姓名、证件号） |
| **BR-LOG-02** | 报文保留策略：`lis_config.log.retentionDays`，超期自动清理 |
| **BR-LOG-03** | 补发（resend）：仅 INT-05 外发失败类型可补发；补发记录关联原 eventId |

---

### AS-LIS-RPT-001 · 对账管理

| 属性 | 值 |
|------|-----|
| **类名** | `LisReconcileAppService` |
| **路由** | `/lis/ops/reconcile` |
| **优先级** | P2 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| RPT1-IF-01 | IF-LIS-RPT-01 | `searchReconcile` | POST | `/search` | dateRange, deviceId?, diffOnly?, pageNum, pageSize | PageResult<ReconcileItemDTO> | P2 |
| RPT1-IF-02 | IF-LIS-RPT-01 | `runReconcile` | POST | `/run` | dateRange, deviceId? | ReconcileResultDTO | P2 |

#### §3 BR

| 编号 | 规则 |
|------|------|
| **BR-RPT1-01** | 对账：按日期+仪器对比 HIS ServiceRequest 数量 vs LIS LisWorkItem 数量；差异按 barcode 明细列出 |
| **BR-RPT1-02** | 对账结果保留 90 天 |

---

### AS-LIS-RPT-002 · 统计报表

| 属性 | 值 |
|------|-----|
| **类名** | `LisStatisticsAppService` |
| **路由** | `/lis/ops/statistics` |
| **优先级** | P2 |
| **子模块** | TAT 统计 |

#### §2 IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| RPT2-IF-01 | IF-LIS-RPT-02 | `deptStats` | POST | `/dept` | dateRange, deptId? | List<DeptStatsDTO> | P2 |
| RPT2-IF-02 | IF-LIS-RPT-03 | `annualReport | POST | `/annual` | year, deptId? | AnnualReportDTO | P2 |
| RPT2-IF-03 | IF-LIS-RPT-04 | `sourceStats` | POST | `/source` | dateRange | List<SourceStatsDTO> | P2 |
| RPT2-IF-04 | IF-LIS-TAT-04 | `tatStats` | POST | `/tat` | deptId, dateRange, groupBy(project/dept/shift) | List<TatStatsDTO> | P1 |
| RPT2-IF-05 | — | `exportExcel` | POST | `/ex旧命名（已废止） | reportType, params | BinaryStream | P2 |

---

## 五、无独立菜单 AS

---

### AS-LIS-INT-001 · HIS 接单入口（非 AppService）

> **v2.1 起 INT-01 不再作为独立 AppService**。医生站签发检验医嘱时直接注入 `LisWorkItemDomainService` 调用，合管分组逻辑收敛为 `LisOrderIngressOrchestrator`（support 类）。
>
> 详见 AS-LIS-INT-001-HIS、「SDD」。  
> **契约状态**：✅ 独立 AS（非 AppService 类）

---

| 编号 | 规则 |
|------|------|
| **BR-INT-01** | 幂等性：同一 `serviceRequestId` 已存在 `LisWorkItemOrder` → 不重复创建 |
| **BR-INT-02** | 合管分组：调用 `SpecimenGroupingPolicy.group()`，依据 `lis_merge_profile` + `lis_device_capability` + `lis_tube_scheme` |
| **BR-INT-03** | 分组失败降级：项目未配置 `merge_profile` 时按 `lis_config.grouping.fallbackWhenNoProfile` 降级（默认 `ONE_SR_ONE_TUBE`） |
| **BR-INT-04** | 撤单校验：LisWorkItem 状态须为 `available` 或 `collected` 方可撤单 |
| **BR-INT-05** | 撤单执行：`cancelOrder(srId)`；仅该管全部 order 作废时才 `LisWorkItem.cancel()` |
| **BR-INT-06** | 可检验性校验：逐 `activity_id` 查 **`lis_device_capability`**；任一缺失 → 拒绝整批（[01 §10.4](./01-概要设计.md#104-项目对照--一体化无-mapd3)） |
| **BR-INT-07** | 外发失败补发（retryDispatch）：health 侧记录外发失败 eventId → LIS 侧重试接单逻辑 |

---

### lis-adapter 说明

`InstrumentWebService`、遗留 `/webservice/*` 兼容归 **lis-adapter**，在 [AS-LIS-INT-001-SDD](../AppService-SDD/AS-LIS-INT-001-HIS接单入口-SDD.md) 或 adapter SDD 中引用，不单独占菜单。

---

## 六、增值 Module AS

### 6.1 zxplat-module-lis-kingmed（2 AS）

| 编码 | AppService 类名 | 主页面 | 文档目录 |
|------|-----------------|--------|----------|
| **AS-LIS-EXT-001** | `LisKingMedWorkbenchAppService` | 外送工作台 | [zxplat-module-lis-kingmed](../../../模块/zxplat-module-lis-kingmed/) |
| **AS-LIS-EXT-002** | `LisKingMedSettingsAppService` | 外送配置 | 同上 |

装配：`kingmed-starter` + `zxplat.modules.lis.kingmed.enabled=true`（可选）。

### 6.2 zxplat-module-lis-collector（1 AS）

| 编码 | AppService 类名 | 主页面 | 文档目录 |
|------|-----------------|--------|----------|
| **AS-LIS-COL-001** | `LisCollectorAppService` | 设备采集 | [zxplat-module-lis-collector](../../../模块/zxplat-module-lis-collector/) |

装配：`collector-starter` + `zxplat.modules.lis.collector.enabled=true`（可选）。

无菜单：`collector-agent`（C# 边缘服务）、Roslyn 脚本运行时 → adapter SDD。

---

## 七、RES-01 仪器结果 Ingress（无页面，lis-adapter）

| 属性 | 值 |
|------|-----|
| **触发方** | lis-adapter / C++ 通讯程序 |
| **优先级** | P0 |

#### IF 表

| # | IF 编号 | 方法名 | HTTP | 路径 | 请求概要 | 响应概要 | P |
|---|---------|--------|------|------|----------|----------|----|
| RES-IF-01 | IF-LIS-RES-01 | `ingressResult` | POST | `/instrument/result` | barcode, channelNo, results[{code, value, unit, abnormalFlag}] | void | P0 |
| RES-IF-02 | IF-LIS-RES-02 | `saveManualResult` | POST | `/instrument/manual` | workItemId, serviceRequestId, result | ObservationDTO | P1 |
| RES-IF-03 | IF-LIS-RES-03 | `getEditHistory` | GET | `/instrument/editHistory` | observationId | List<ResultEditLogDTO> | P1 |
| RES-IF-04 | IF-LIS-RES-04 | `markRerun` | POST | `/instrument/rerun` | observationId | void | P2 |

#### BR

| 编号 | 规则 |
|------|------|
| **BR-RES-01** | Ingress 匹配：barcode → `LisWorkItem.workItemId`；channelNo → `activityId` → `LisWorkItemOrder` → `serviceRequestId` |
| **BR-RES-02** | 匹配失败：barcode 找不到 → 存为无主结果（RES-ORPHAN-01） |
| **BR-RES-03** | 结果覆盖：同一 (workItemId, serviceRequestId, code) 已存在 preliminary Observation → 更新值，记录 editLog |
| **BR-RES-04** | 复查标记：Observation.statusEnum → `amended`，原值保留在 previous 引用 |

---

## 八、AS 文档命名与存放

```text
docs/领域/LisAppService/AppService/
  AS-LIS-SMP-001-采样工作台.md
  AS-LIS-REP-001-报告管理.md
  …
docs/模块/zxplat-module-lis-kingmed/AppService/
  AS-LIS-EXT-001-外送工作台.md
  AS-LIS-EXT-002-外送配置.md
docs/模块/zxplat-module-lis-collector/AppService/
  AS-LIS-COL-001-设备采集.md
```

每份独立 AS 文档须含：§1 页面边界、§2 IF 表、§3 BR，与 [00-功能清单.md](./00-功能清单.md) IF 编号互引。

---

## 九、建议编写顺序

1. **AS-LIS-INT-001**（接单，阻塞全链路）
2. **RES-01 Ingress**（仪器结果接收，P0）
3. **AS-LIS-SMP-001** + **AS-LIS-AUD-001** + **AS-LIS-REP-001**（P0 三角）
4. **AS-LIS-CFG-003/004/007**（仪器、合管、系统参数，阻塞 INT-01）
5. **AS-LIS-SAM-001**（含 TAT + TRANS，P1）
6. **AS-LIS-CRT-001**（危急值，P1）
7. **AS-LIS-EXT-***（院点有金域时）
8. **AS-LIS-COL-001**（院点需仪器采集时）
9. 其余 P2
10. **AUD-AUTO** / **AUD-REVIEW** / **REP-REVISE** 子模块（P1/P2，依赖主 AS）

---

## 十、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-18 | 首版：20 页面 AS + INT；金域 2 AS（仅索引） |
| v1.1 | 2026-06-19 | 新增 collector 1 AS；合计 21 页面 |
| v2.2 | 2026-06-27 | CFG-001 改名；独立 AS 互链；版本元数据 |
| v2.1 | 2026-06-22 | 去 INT-01 AppService：医生站直接注入 `LisWorkItemDomainService`；AppService 总数 22→21 |
| v2.0 | 2026-06-22 | 全面完善：22 个 AS 逐一编写 §1 页面边界、§2 IF 表（方法名+HTTP+路径+请求/响应）、§3 BR；新增 TAT/TRANS/AUTO/REVIEW/REVISE/BIDI/REAGENT 子模块 IF 表；新增 RES-01 独立设计 |
