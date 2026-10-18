# 医保文件管理 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-CAT-005-SDD** · **版本**：v0.2 · **日期**：2026-07-14  
> **应用服务编号**：AS-CHS-CAT-005
> **成稿编号**：DM-CHS-CAT-005

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | `module-chs-jilin-application` / `module-chs-jilin-domain`（+ bridge Port 实现） |
| AppService | `InsuranceCatalogNoticeAppService` |
| HTTP 前缀 | `POST /api/app/insuranceCatalogNotice/{methodName}` |
| 编排权威 | AS-CHS-CAT-005 |
| 方法权威 | DM-CHS-CAT-005 §6 / §6.2 |
| **实现阶段口径** | **目标态全量契约**（领域与应用均待生成） |
| 严格对齐 | IF 集合与 AS §2 一致；禁止 GET/PUT/PATCH/DELETE |

### 1.2 术语口径

| 项 | 口径 |
| --- | --- |
| 事务 | W-02/W-03 默认单事务；大文件分批由 Workflow 扩展（同 doc 串行） |
| 预览态 | W-01 结果以 `previewToken`（服务端临时缓存，TTL 可配）提交 W-02 |
| QueryService | 禁止 App 直查 Repository |

---

## 2. 代码结构

### 2.1 包路径

```text
com.zxplat.plugin.chs.application.notice/
  service/InsuranceCatalogNoticeAppService.java
  request/
    ScanNoticeCanonicalDirRequest.java
    ConfirmNoticeScanImportRequest.java
    ApplyNoticeDocRequest.java
    CloseNoticeTodoRequest.java
    RerunNoticeComplianceRequest.java
    PageNoticeDocRequest.java
    PageNoticeLineRequest.java
    GetComplianceReportRequest.java
    PageNoticeTodoRequest.java
    DownloadNoticePackRequest.java
    PreviewNoticeApplyRequest.java
  response/
    ScanNoticeCanonicalDirResponse.java
    ConfirmNoticeScanImportResponse.java
    ApplyNoticeDocResponse.java
    CloseNoticeTodoResponse.java
    RerunNoticeComplianceResponse.java
    PageNoticeDocResponse.java
    PageNoticeLineResponse.java
    GetComplianceReportResponse.java
    NoticeTodoSummaryResponse.java
    PageNoticeTodoResponse.java
    DownloadNoticePackResponse.java
    PreviewNoticeApplyResponse.java
  dto/
    NoticeDocRowDto.java
    NoticeLineRowDto.java
    NoticeTodoRowDto.java
    ComplianceIssueDto.java
    ApplyPreviewSampleDto.java
    AffectedChargeBillDto.java
  mapper/
    InsuranceCatalogNoticeMapper.java
    InsuranceCatalogNoticeMapperImpl.java
  support/
    NoticeImportWorkflow.java
    NoticeApplyWorkflow.java
    NoticeComplianceWorkflow.java
    dto/   # workflow 内部上下文（可选）

com.zxplat.plugin.chs.domain.notice/
  service/
    CatalogNoticeImportDomainService.java
    CatalogNoticeApplyDomainService.java
    CatalogNoticeTodoDomainService.java
    CatalogNoticeComplianceDomainService.java
    CatalogNoticeComplianceGate.java
  command/  # PersistNoticeDocCommand, ApplyNoticeDocCommand, …
  queryservice/
    CatalogNoticeDocQueryService.java
    CatalogNoticeLineQueryService.java
    CatalogNoticeComplianceQueryService.java
    CatalogNoticeTodoQueryService.java
    CatalogNoticeApplyPreviewQueryService.java
    NationalCatalogOverlayQueryService.java
  port/
    CatalogNoticeHisProductPort.java
    CatalogNoticeHisChargePort.java
    CatalogNoticePackStorePort.java
  entity/ repository/ model/
```

### 2.2 依赖注入与领域方法白名单

| 注入类型 | 用途 |
| --- | --- |
| NoticeImportWorkflow / NoticeApplyWorkflow / NoticeComplianceWorkflow | 多步编排 |
| CatalogNoticeImportDomainService | W-02 |
| CatalogNoticeApplyDomainService | W-03 |
| CatalogNoticeTodoDomainService | W-04 |
| CatalogNoticeComplianceDomainService | W-05 |
| CatalogNoticeDocQueryService 等 | R-* |
| InsuranceCatalogNoticeMapper | Asm |
| CatalogNoticePackStorePort | R-05 读字节（经 Domain/Workflow） |

| 类型#方法 | 用于 IF |
| --- | --- |
| CatalogNoticeImportDomainService#persistImportedDoc(PersistNoticeDocCommand) | W-02 |
| CatalogNoticeApplyDomainService#applyDoc(ApplyNoticeDocCommand) | W-03 |
| CatalogNoticeTodoDomainService#closeTodo(CloseNoticeTodoCommand) | W-04 |
| CatalogNoticeComplianceDomainService#runCompliance(RunComplianceCommand) | W-05 |
| CatalogNoticeDocQueryService#pageDocs / #getDocById | R-01/R-05 |
| CatalogNoticeLineQueryService#pageLines | R-02 |
| CatalogNoticeComplianceQueryService#getLatestReport | R-03 |
| CatalogNoticeTodoQueryService#summarizeTodos / #pageTodos | R-04 |
| CatalogNoticeApplyPreviewQueryService#previewApply | R-06 |
| CatalogNoticePackStorePort#loadCanonicalPack | R-05 |
| CatalogNoticePackStorePort#storeCanonicalPack | W-02（经 ImportDomain） |
| InsuranceCatalogNoticeMapper#to* | 各 IF Asm |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| doc_kind | `ConfirmNoticeImportRequest.docKind`（目标态待生成） | DM-CHS-CAT-005 §3 `yb_catalog_notice_doc.doc_kind` | W-01/W-02 |
| category | `ConfirmNoticeImportRequest.category`（目标态待生成） | DM-CHS-CAT-005 §3 category | 全品类 |
| action | `NoticeLineRowDto.action`（目标态待生成） | DM-CHS-CAT-005 §3 action | ADD/STOP/… |
| chrgitm_lv | `NoticeLineRowDto.chrgitmLv`（目标态待生成） | DM-CHS-CAT-005 §3 chrgitm_lv；方案 BR T5 | 1/2/3 |
| parse_status | `NoticeDocRowDto.parseStatus`（目标态待生成） | DM-CHS-CAT-005 §3 parse_status | R-01 |
| apply_status | `NoticeDocRowDto.applyStatus`（目标态待生成） | DM-CHS-CAT-005 §3 apply_status | R-01 |
| todo_type | `NoticeTodoRowDto.todoType`（目标态待生成） | DM-CHS-CAT-005 §3 todo_type | STOP/PAY_CLASS |
| 门禁码 | `ComplianceGateResult.code`（目标态待生成） | DM-CHS-CAT-005 §5 INV-3/4；方案 C-* | 结算消费，非本 HTTP |

---

## 3. 按接口 ID 命名展开

先读完各 IF「领域编排序列」，按序号执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-CHS-CAT-005-W-01 | `startScanNoticeCanonicalDir` + `getScanNoticeCanonicalDirProgress` | 异步扫描约定目录 + 进度 | POST `/startScanNoticeCanonicalDir` · `/getScanNoticeCanonicalDirProgress` |
| IF-CHS-CAT-005-W-02 | `startConfirmNoticeScanImport` + `getConfirmNoticeScanImportProgress`（+ `resumeConfirmNoticeScanImport`） | 异步确认入库 + 进度（可续跑） | POST `/startConfirmNoticeScanImport` · `/getConfirmNoticeScanImportProgress` · `/resumeConfirmNoticeScanImport` |
| IF-CHS-CAT-005-W-03 | `startApplyNoticeDoc` / `startApplyNoticeDocs` + `getApplyNoticeDocProgress` | 异步应用文件 + 进度 | POST `/startApplyNoticeDoc` · `/startApplyNoticeDocs` · `/getApplyNoticeDocProgress` |
| IF-CHS-CAT-005-W-04 | closeNoticeTodo | 关闭待办 | POST /api/app/insuranceCatalogNotice/closeNoticeTodo |
| IF-CHS-CAT-005-W-05 | rerunNoticeCompliance | 再次执行校验 | POST /api/app/insuranceCatalogNotice/rerunNoticeCompliance |
| IF-CHS-CAT-005-R-01 | pageNoticeDocs | 查询文件列表 | POST /api/app/insuranceCatalogNotice/pageNoticeDocs |
| IF-CHS-CAT-005-R-02 | pageNoticeLines | 查询文件明细 | POST /api/app/insuranceCatalogNotice/pageNoticeLines |
| IF-CHS-CAT-005-R-03 | getComplianceReport | 查询合规报告 | POST /api/app/insuranceCatalogNotice/getComplianceReport |
| IF-CHS-CAT-005-R-04 | pageNoticeTodos | 查询催办汇总与待办 | POST /api/app/insuranceCatalogNotice/pageNoticeTodos |
| IF-CHS-CAT-005-R-05 | downloadNoticePack | 下载标准落地包 | POST /api/app/insuranceCatalogNotice/downloadNoticePack |
| IF-CHS-CAT-005-R-06 | previewNoticeApply | 应用差异预览 | POST /api/app/insuranceCatalogNotice/previewNoticeApply |

> **已删除（同步阻塞入口，UI 已切异步）**：`scanNoticeCanonicalDir` / `confirmNoticeScanImport` / `applyNoticeDoc`。下文 §3.2～§3.4 若仍写同步方法名，以本节白名单与 `InsuranceCatalogNoticeAppService` 为准。

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读加载与 BR 门禁 | 抛业务异常，禁止进入 Cmd |
| **Cmd** | 领域写、持久化 | 事务回滚见 §5 |
| **Asm** | Mapper DTO 装配 | — |

记法：`CatalogNoticeImportDomainService#persistImportedDoc(PersistNoticeDocCommand)`。

---

### 3.2 IF-CHS-CAT-005-W-01 `scanNoticeCanonicalDir`（重新扫描约定目录）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scanRoot | String | 否 | 空则用配置 `chs.notice.canonical-root` |
| contractId | Long | 否 | 契约（确认入库时可带回） |

**Response**

| 字段 | 类型 | 说明 |
|------|------|------|
| scanToken | String | 确认入库会话凭证（TTL） |
| scanRoot | String | 实际扫描根 |
| newCount / duplicateCount / failedCount | int | |
| items | List\<NoticeScanItemDto\> | 每包状态 NEW/DUPLICATE/FAILED |

**NoticeScanItemDto**：`packDir,sourceFilename,docKind,category,title,noticeDate,lineCount,addCount,stopCount,modifyCount,setPayClassCount,status,existingDocId,message,sourceFileHash`

**失败码**：根未配置/`NOTICE_FILE_UNSUPPORTED`（整批根错误）；单包失败进 items 不中断

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 解析 scanRoot | 空配置则失败 |
| 2 | Pre | NoticeScanWorkflow#scan | 枚举子目录、parseDirectory、hash、对库标状态；缓存 scanToken |
| 3 | Asm | Mapper#toScanResponse | 仅读 |

---

### 3.3 IF-CHS-CAT-005-W-02 `confirmNoticeScanImport`（确认扫描入库）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scanToken | String | 是 | |
| packDirs | List\<String\> | 否 | 空=全部 NEW；force 时可含 DUPLICATE |
| contractId | Long | 否 | |
| force | boolean | 否 | 强制重导重复 hash |

**Response**：`importedCount, skippedCount, failedCount, results[]{packDir,docId,status,message}`

**失败码**：`NOTICE_PREVIEW_EXPIRED`（令牌过期）/`NOTICE_DUPLICATE_HASH`（单包未 force）/`NOTICE_STORE_FAILED`

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | scanToken | |
| 2 | Pre | NoticeScanWorkflow#loadScan | 过期失败 |
| 3 | Cmd | 对选中项循环 `persistImportedDoc` | **写库点** |
| 4 | Asm | 汇总结果 | |

---

### 3.4 IF-CHS-CAT-005-W-03 `applyNoticeDoc`（应用文件）

**Request**：`docId: Long`（必填）、`confirm: boolean`（必填 true）

**Response**：`appliedLineCount, failedLineCount, writebackCount, syncedUnchargedCount`

**AffectedChargeBillDto**（日志侧）：`billId,billNo,encounterId,itemLineId,beforeChrgitmLv,afterChrgitmLv`

**失败码**：`NOTICE_DOC_NOT_FOUND` / `NOTICE_APPLY_DENIED` / `NOTICE_APPLY_FAILED`

**Mapper**：`toApplyResponse(ApplyResult): ApplyNoticeDocResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | confirm==true | | 否则 NOTICE_APPLY_DENIED |
| 2 | Pre | CatalogNoticeDocQueryService#getDocById | docId | 不存在则失败 |
| 3 | Cmd | CatalogNoticeApplyDomainService#applyDoc(ApplyNoticeDocCommand) | docId | **写库点**（overlay/map/lv/未收费/log） |
| 4 | Asm | InsuranceCatalogNoticeMapper#toApplyResponse | 结果 | |

---

### 3.5 IF-CHS-CAT-005-W-04 `closeNoticeTodo`（关闭待办）

**Request**：`todoId: Long`（必填）、`closeReason: String`（必填）

**Response**：`todoId, status`

**失败码**：`NOTICE_TODO_NOT_FOUND` / `NOTICE_TODO_CLOSED`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | 参数 | todoId | |
| 2 | Cmd | CatalogNoticeTodoDomainService#closeTodo(CloseNoticeTodoCommand) | | **写库点** |
| 3 | Asm | Mapper#toCloseTodoResponse | | |

---

### 3.6 IF-CHS-CAT-005-W-05 `rerunNoticeCompliance`（再次执行校验）

**Request**：`contractId: Long`（否）、`listType: String`（否）

**Response**：`reportId, issueCount, ranAt`

**失败码**：`NOTICE_COMPLIANCE_FAILED`

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | 权限 | | |
| 2 | Cmd | CatalogNoticeComplianceDomainService#runCompliance(RunComplianceCommand) | 过滤条件 | **写库点**（报告） |
| 3 | Asm | Mapper#toRerunResponse | | |

---

### 3.7 IF-CHS-CAT-005-R-01 `pageNoticeDocs`（查询文件列表）

**Request**：`page,size,docKind,category,applyStatus,keyword`（分页必填）

**Response**：`total, records: List<NoticeDocRowDto>`

**NoticeDocRowDto**：`id,docKind,category,title,sourceFilename,noticeDate,effectiveDate,parseStatus,applyStatus,lineCount,importedAt`

**领域编排序列**：Gate → `CatalogNoticeDocQueryService#pageDocs(PageNoticeDocQuery)` → Asm（仅读路径）

---

### 3.8 IF-CHS-CAT-005-R-02 `pageNoticeLines`（查询文件明细）

**Request**：`docId`（必填）,`page,size,action,matchStatus`

**Response**：`total, records: List<NoticeLineRowDto>`（字段同预览 DTO + `matchStatus,applyResult`）

**编排序列**：Gate → `CatalogNoticeLineQueryService#pageLines` → Asm

---

### 3.9 IF-CHS-CAT-005-R-03 `getComplianceReport`（查询合规报告）

**Request**：`contractId`（否）,`listType`（否）

**Response**：`ranAt, summaryJson, issues: List<ComplianceIssueDto>`

**ComplianceIssueDto**：`code,severityCode,projectCode,message,severity`

**编排序列**：Gate → `CatalogNoticeComplianceQueryService#getLatestReport` → Asm

---

### 3.10 IF-CHS-CAT-005-R-04 `pageNoticeTodos`（查询催办汇总与待办）

**Request**：`page,size,todoType,status`（默认 OPEN）

**Response**：`summary: NoticeTodoSummaryResponse{stopOpenCount,payClassOpenCount}, total, records: List<NoticeTodoRowDto>`

**NoticeTodoRowDto**：`id,todoType,hilistCode,projectCode,productName,effectiveDate,docId,status`

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | | |
| 2 | Pre | CatalogNoticeTodoQueryService#summarizeTodos | 汇总 |
| 3 | Pre | CatalogNoticeTodoQueryService#pageTodos | 列表 |
| 4 | Asm | Mapper 合并 summary+page | 仅读 |

---

### 3.11 IF-CHS-CAT-005-R-05 `downloadNoticePack`（下载标准落地包）

**Request**：`docId: Long`（必填）

**Response**：`fileName, contentBase64`（或约定二进制包装，与工程统一；本处字段表以可 JSON 承载）

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | | |
| 2 | Pre | CatalogNoticeDocQueryService#getDocById | |
| 3 | Pre | CatalogNoticePackStorePort#loadCanonicalPack | 若 URI 空则失败 |
| 4 | Asm | 装填 Response | 仅读 |

---

### 3.12 IF-CHS-CAT-005-R-06 `previewNoticeApply`（应用差异预览）

**Request**：`docId: Long`（必填）

**Response**：`overlayUpsertCount, writebackCount, unchargedSyncCount, samples: List<ApplyPreviewSampleDto>`

**ApplyPreviewSampleDto**：`action,hilistCode,projectCode,effectSummary`

**编排序列**：Gate → `CatalogNoticeApplyPreviewQueryService#previewApply` → Asm

---

## 4. QueryService 与领域事件

| 写侧事件 | 读侧刷新 |
|----------|----------|
| CatalogNoticeImported | R-01 列表 |
| CatalogNoticeApplied | R-04 待办；CAT-001 选码可用（overlay） |
| CatalogNoticeComplianceRan | R-03 报告；CAT-002 页提示条 |

CAT-002 入库成功后由 `NoticeComplianceWorkflow.runAfterImport` 调 `runCompliance`（无独立 App HTTP）。

结算链直接注入 `CatalogNoticeComplianceGate#evaluate`（非本 AppService IF）。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| W-02 | confirmNoticeImport | §3.3 序号 3 | persistImportedDoc | PackStore → Doc → Lines |
| W-03 | applyNoticeDoc | §3.4 序号 3 | applyDoc | Overlay/Map → ProductLv → UnchargedLines → ApplyLog → Doc.apply_status → Todo 重算 |
| W-04 | closeNoticeTodo | §3.5 序号 2 | closeTodo | Todo |
| W-05 | rerunNoticeCompliance | §3.6 序号 2 | runCompliance | Report |

### 5.2 事务边界 / 失败回滚

| IF | 事务 | 失败 |
| --- | --- | --- |
| W-02 | 单事务 | 全回滚；临时包删除尽力而为 |
| W-03 | 默认单事务 | 全回滚；万行级后续可分批（须保证 doc 串行） |
| W-04/W-05 | 单事务 | 回滚 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 代码位置（必须） | 关联步骤 |
|------------------|----------|
| ImportDomainService hash 唯一 | §3.3 序号 3 |
| ApplyDomain 重复应用覆盖 overlay+追加 log | §3.4 序号 3 |

### 6.2 并发控制

| 代码位置（必须） | 关联步骤 |
|------------------|----------|
| 同 docId 应用分布式锁或 DB 行锁 | §3.4 序号 3 |

### 6.3 重试与容错

| 代码位置（必须） | 关联步骤 |
|------------------|----------|
| W-05 可重入覆盖报告 | §3.6 序号 2 |
| CAT-002 钩子失败只记日志 | §4 |

### 6.4 数据脱敏

| 代码位置（必须） | 关联步骤 |
|------------------|----------|
| 下载 pack 仅授权角色 | §3.11 序号 1 |

### 6.5 审计日志

| 代码位置（必须） | 关联步骤 |
|------------------|----------|
| apply_log + affected_refs_json | §3.4 序号 3 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
|------|------|--------|--------|
| 标准包入库 | 集成 | 是 | P0 |
| 重复 hash | 集成 | 是 | P0 |
| 应用 STOP/限价/甲乙 Gate | 集成 | 是 | P0 |
| 未收费明细同步与日志 | 集成 | 是 | P0 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
|------|------|--------|--------|
| 同 doc 并发应用 | 集成 | 是 | P1 |

### 7.3 查询结果一致性

| 用例 | 层级 | 自动化 | 优先级 |
|------|------|--------|--------|
| 应用后待办与报告刷新 | 集成 | 是 | P0 |

### 7.4 集成测试

| 用例 | 层级 | 自动化 | 优先级 |
|------|------|--------|--------|
| CAT-002 钩子不阻断下载 | 集成 | 是 | P0 |

### 7.5 测试缺口跟踪

无额外测试文档缺口；领域未成码前用契约测试/组件测试桩 Port。

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-07-14 | 简版（未过 SDD SKILL：含 GET、缺字段表/编排序列） |
| v0.2 | 2026-07-14 | 按应用服务 SDD SKILL 重写；全 IF POST；锚定 DM-CHS-CAT-005；§2.3 目标态锚点 |
