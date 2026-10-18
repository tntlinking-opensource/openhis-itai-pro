# EMR · 文书书写与导出 · 应用服务详细设计（SDD）

> **编码**：AS-EMR-DOC-001-SDD · **版本**：v1.0 · **日期**：2026-09-24  
> **对应 AS**：`../AppService/AS-EMR-DOC-001-文书书写与导出.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | `zxplat-plugin/zxplat-module-emr/module-emr-application` · `DocumentAppService` |
| **包根** | `com.zxplat.plugin.emr.application.document` |
| **边界** | IF-EMR-DOC-01～19 |
| **方法权威** | `DM-EMR-001` §6 DocumentManager / DocumentStructureManager |
| **实现阶段口径** | **当前已上线范围**（validate/getQualityResult 占位） |
| **HTTP** | `POST /api/app/document/{methodName}` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **主键入参** | documentId 等多为 String（雪花），内部 parse Long |
| **读侧** | 无 DocumentQueryService；Pre/Query 调用 `DocumentManager#*` |
| **事务** | DocumentManager 写方法在领域 `@Transactional` |

---

## 2. 代码结构

### 2.1 包路径

```text
document/service/DocumentAppService.java
document/request/*.java
document/response/*.java
encounterMetadata/service/EncounterMetadataAppService.java
application/support/EmrEditorImageStore.java, EmrQuerySupport.java
application/pdf/OpenPdfRenderer.java, PrintConfigResolver.java
domain/document/service/DocumentManager.java, DocumentStructureManager.java
```

### 2.2 依赖注入

| 依赖 | IF |
| --- | --- |
| `DocumentManager` | 02～07, 09～13, 17, 19 |
| `DocumentStructureManager` | 16, 17 |
| `EncounterMetadataAppService` | 03, 14 |
| `OpenPdfRenderer` / `PrintConfigResolver` | 14, 15 |
| `EmrEditorImageStore` | 01 |
| `EmrLinkedEncounterResolver` | 04 |
| `EmrAdmissionContextLoader` | 04 Asm, 03 只读判定 |
| `UserRepository` / `ICurrentUser` | 04/05/06/09 Gate |

### 2.2.1 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| `DocumentManager` | create | 02 |
| `DocumentManager` | findById, findLatest..., findPage | 03, 04, 05～07, 09, 14～17 |
| `DocumentManager` | saveContent | 05, 06, 17 |
| `DocumentManager` | saveAs | 07 |
| `DocumentManager` | softDelete | 09 |
| `DocumentManager` | listDocumentTypes | 10 |
| `DocumentManager` | getVersionList, getVersion, rollbackToVersion | 11～13 |
| `DocumentManager` | updateStatus | 19 |
| `DocumentStructureManager` | parseStructure, setValuesByNames, extractPlainText... | 16, 17, 04 Asm |
| `EncounterMetadataAppService` | resolveValues | 03, 14 |
| `EmrEditorImageStore` | saveImage | 01 |
| `OpenPdfRenderer` | render, mergePdfBytes, computeContentHash | 14, 15 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 文书状态 | `DocumentContentDto.status` | DM-EMR-001 §2.2 状态机 | IF-03/05/19 |
| STATUS 常量 | `DocumentManager.STATUS_DRAFT/LOCKED/ARCHIVED/SUBMITTED` | DM-EMR-001 §2.2 | Gate |
| 文书类型 | `CreateDocumentRequest.documentTypeCode` | DM-EMR-001 §3.1 | IF-02 |
| 锁定动作 | `ChangeLockStateRequest.action` | AS BR-EMR-DOC-007 | LOCK→LOCKED |

---

## 3. 按接口 ID 命名展开

> 先读完各 IF「领域编排序列」，Gate/Pre 失败不得 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-EMR-DOC-01 | `uploadEditorImage` | 上传编辑器图片 | `POST /api/app/document/uploadEditorImage` |
| IF-EMR-DOC-02 | `create` | 新建文书 | `POST /api/app/document/create` |
| IF-EMR-DOC-03 | `getDocumentContent` | 打开文书内容 | `POST /api/app/document/getDocumentContent` |
| IF-EMR-DOC-04 | `getList` | 文书分页列表 | `POST /api/app/document/getList` |
| IF-EMR-DOC-05 | `save` | 保存文书 | `POST /api/app/document/save` |
| IF-EMR-DOC-06 | `saveDocument` | 保存并返回版本号 | `POST /api/app/document/saveDocument` |
| IF-EMR-DOC-07 | `saveAs` | 另存为新文书 | `POST /api/app/document/saveAs` |
| IF-EMR-DOC-08 | `validate` | 校验文书（占位） | `POST /api/app/document/validate` |
| IF-EMR-DOC-09 | `delete` | 删除文书 | `POST /api/app/document/delete` |
| IF-EMR-DOC-10 | `initEmrPage` | 工作页初始化 | `POST /api/app/document/initEmrPage` |
| IF-EMR-DOC-11 | `getVersionList` | 版本列表 | `POST /api/app/document/getVersionList` |
| IF-EMR-DOC-12 | `getVersionContent` | 历史版本正文 | `POST /api/app/document/getVersionContent` |
| IF-EMR-DOC-13 | `rollbackVersion` | 回滚版本 | `POST /api/app/document/rollbackVersion` |
| IF-EMR-DOC-14 | `generatePdf` | 生成 PDF | `POST /api/app/document/generatePdf` |
| IF-EMR-DOC-15 | `mergePdf` | 合并 PDF | `POST /api/app/document/mergePdf` |
| IF-EMR-DOC-16 | `getStructureList` | 结构树 | `POST /api/app/document/getStructureList` |
| IF-EMR-DOC-17 | `setValuesByNames` | 按名写数据项 | `POST /api/app/document/setValuesByNames` |
| IF-EMR-DOC-18 | `getQualityResult` | 质控结果（占位） | `POST /api/app/document/getQualityResult` |
| IF-EMR-DOC-19 | `changeLockState` | 锁定解锁 | `POST /api/app/document/changeLockState` |

> **别名**：`getContent` → 与 IF-EMR-DOC-03 相同，HTTP `POST /api/app/document/getContent`。

#### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| Gate | 分页归一、id 解析、权限/只读 | ValidationException |
| Pre | DocumentManager 加载 | EntityNotFoundException |
| Cmd | DocumentManager / StructureManager / PDF | 回滚 |
| Asm | toContentDto / toSummaryDto / PDF DTO | — |

---

### 3.2 IF-EMR-DOC-01 `uploadEditorImage`

**UploadEditorImageRequest**：contentBase64*, fileName, mimeType

**EditorImageUploadDto**：url String

**序列**：`EmrEditorImageStore#saveImage` → 设 url（Cmd 文件 IO）

---

### 3.3 IF-EMR-DOC-02 `create`

**CreateDocumentRequest**：patientId*, encounterId*, documentTypeCode*, templateId String 可选

**DocumentContentDto**：id, patientId, encounterId, documentTypeCode, title, status, content, readOnly, metadataValues

**序列**：
1. Cmd `DocumentManager#create(patientId, encounterId, documentTypeCode, templateIdLong)` **写库点**
2. Asm `toContentDto`（含 resolveValues）

**失败**：首次病程唯一等领域校验由 Manager 抛出

---

### 3.4 IF-EMR-DOC-03 `getDocumentContent`（别名 getContent）

**GetDocumentContentRequest**：documentId **或** patientId+encounterId+documentTypeCode

**DocumentContentDto** 字段同 §3.3

**序列**：
1. Gate 若 documentId 有则 parse；否则三元组查 latest
2. Pre `findById` / `findLatestByPatientAndEncounterAndType`；null → 空 DTO
3. Asm `toContentDto`（readOnly 计算 + EncounterMetadata resolveValues）

---

### 3.5 IF-EMR-DOC-04 `getList`

**QueryDocumentRequest**：patientId, encounterId, encounterIds, includeLinkedEncounters, documentTypeCode, status, pageNo, pageIndex, pageSize, keyword

**Response**：`PagedResult<DocumentSummaryDto>`

**DocumentSummaryDto**：id, patientId, encounterId, documentTypeCode, documentTypeName, title, status, createdAt, updatedAt, createdBy, updatedBy, sourceTemplateId, deptName, savedByName, chiefComplaint

**序列**：resolvePageNo/Size → resolveEncounterIdsForQuery → `DocumentManager#findPage` → toSummaryDto 批量（AdmissionContextLoader 科室、UserRepository 保存人、extractPlainText chiefComplaint）

---

### 3.6 IF-EMR-DOC-05 `save`

**SaveDocumentRequest**：documentId* String, content*, saveVersion Boolean

**DocumentSummaryDto** 见 §3.5

**序列**：
1. Gate parse documentId → `findById` → `assertCanEditDocument`
2. Cmd `DocumentManager#saveContent(id, content, saveVersion)` **写库点**
3. Asm toSummaryDto

**失败码**：ValidationException 锁定/归档/非创建者

---

### 3.7 IF-EMR-DOC-06 `saveDocument`

**Request** 同 SaveDocumentRequest

**SaveDocumentResponse**：documentId Long, newVersionNumber Integer（未存版本为 0）

**序列**：复用 IF-EMR-DOC-05 的 Gate+Cmd → 若 saveVersion 则 `getVersionList` 取 max versionNumber → 装配 Response

---

### 3.8 IF-EMR-DOC-07 `saveAs`

**SaveAsDocumentRequest**：sourceDocumentId*, patientId, encounterId, documentTypeCode

**Response**：DocumentContentDto

**序列**：Cmd `DocumentManager#saveAs(...)` 写库点 → toContentDto

---

### 3.9 IF-EMR-DOC-08 `validate`

**ValidateDocumentRequest**：documentId Long, content String

**ValidateDocumentResponse**：errors List（ValidateDocumentErrorItem: dataItemName, code, message）

**序列**：返回空 errors（占位）

---

### 3.10 IF-EMR-DOC-09 `delete`

**LongValueRequest**：value Long（documentId）

**序列**：value null noop → findById → assertCanDeleteDocument → `softDelete` 写库点

---

### 3.11 IF-EMR-DOC-10 `initEmrPage`

**InitEmrPageRequest**：patientId, encounterId, organizationId（占位扩展）

**InitEmrPageResponse**：availableDocumentTypes List\<String\>, printOptions List（当前 empty）

**序列**：`listDocumentTypes` → map code（仅读）

---

### 3.12 IF-EMR-DOC-11 `getVersionList`

**GetVersionListRequest**：documentId Long

**VersionDto**：id, documentId, versionNumber, createdAt, createdBy

**序列**：`getVersionList` → map toVersionDto

---

### 3.13 IF-EMR-DOC-12 `getVersionContent`

**GetVersionContentRequest**：documentId*, versionId 或 versionNumber

**DocumentContentDto**（content + readOnly=true）

**序列**：`getVersion` → 空则空 DTO；否则设 content/readOnly

---

### 3.14 IF-EMR-DOC-13 `rollbackVersion`

**RollbackVersionRequest**：documentId*, versionId, versionNumber

**序列**：Cmd `DocumentManager#rollbackToVersion` 写库点（回写+快照）

---

### 3.15 IF-EMR-DOC-14 `generatePdf`

**GeneratePdfRequest**：documentId, content, pageRange, regionNames, includeTrackChanges, watermark, headerTemplate, footerTemplate, previewMode, printConfig, previewMaxPages, contentHash, patientId, encounterId, documentTitle, documentTypeName, metadata Map

**PdfGenerateResultDto**：url, token, pdfBase64, pageCount, contentHash, renderedAt, previewTruncated

**序列**：resolveContentJson → enrichRenderMetadata（含 resolveValues）→ PrintConfigResolver → OpenPdfRenderer#render → toPdfResultDto

**失败**：EMR_PDF_NO_CONTENT / EMR_DOCUMENT_NOT_FOUND / EMR_PDF_EMPTY

---

### 3.16 IF-EMR-DOC-15 `mergePdf`

**MergePdfRequest**：documentIds List\<String\>, previewMode, printConfig, patientId, encounterId, metadata

**Response**：PdfGenerateResultDto

**序列**：documentIds 空 → 空 DTO；否则循环每 id 构造 GeneratePdfRequest → render → mergePdfBytes

---

### 3.17 IF-EMR-DOC-16 `getStructureList`

**GetStructureRequest**：documentId, content, nodeTypes, firstLevelOnly, includeBlockPageNo

**StructureItemDto**：name, title, nodeType, depth, pageNo, children List

**序列**：解析 content（请求或 findById）→ `parseStructure` → map DTO

---

### 3.18 IF-EMR-DOC-17 `setValuesByNames`

**SetValuesByNamesRequest**：documentId Long, nameToValue Map\<String,Object\>

**序列**：空参数 noop → findById null noop → `setValuesByNames` → `saveContent(..., false)` 写库点

---

### 3.19 IF-EMR-DOC-18 `getQualityResult`

**GetQualityResultRequest**：documentId

**QualityResultDto**：passed boolean, issues List（level, dataItemName, location, message）

**序列**：返回空 DTO（占位）

---

### 3.20 IF-EMR-DOC-19 `changeLockState`

**ChangeLockStateRequest**：documentId*, action*（LOCK 或其它）, reason

**序列**：映射 status → `DocumentManager#updateStatus` 写库点

---

## 4. QueryService 与领域事件

读侧均在 `DocumentManager`（DM-EMR-001 §6.2）；无领域事件发布。

---

## 5. 写库顺序（Cmd 对照）

| IF | Cmd 调用 | 聚合 |
| --- | --- | --- |
| 02 | create | emr_document insert |
| 05/06 | saveContent | emr_document update (+ optional version) |
| 07 | saveAs | insert |
| 09 | softDelete | update is_deleted |
| 13 | rollbackToVersion | update + version insert |
| 17 | saveContent | update |
| 19 | updateStatus | update status |

**事务**：各写 IF 默认单事务；mergePdf 多次 render 只读文书，合并无 DB 写。

---

## 6. 非功能性需求实现

### 6.1 幂等

save 以最后一次 content 为准（§3.6 Cmd）。

### 6.2 并发

assertCanEditDocument / allowsMultiUserEdit（§3.6 Pre）。

### 6.3 审计

createdBy/updatedBy 在 Document 实体。

### 6.4 关联步骤

PDF metadata enrich → §3.15 步骤 resolveValues（§3.4 Asm 同链）。

---

## 7. 测试要点

| 层级 | 用例 | P |
| --- | --- | --- |
| 功能 | 创建→保存→打开 | P0 |
| 功能 | 锁定后 save 拒绝 | P0 |
| 功能 | includeLinkedEncounters 列表 | P1 |
| 功能 | PDF 无 content 失败码 | P1 |
| 占位 | validate/quality 空返回 | P2 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B：19 IF 对齐 DocumentAppService；getContent 别名注明 |
