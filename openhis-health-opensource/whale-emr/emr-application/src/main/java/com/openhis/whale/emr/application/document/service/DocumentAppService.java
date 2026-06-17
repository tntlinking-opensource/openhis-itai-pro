/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.document.service;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.emr.application.document.response.DocumentContentDto;
import com.openhis.whale.emr.application.document.response.DocumentSummaryDto;
import com.openhis.whale.emr.application.document.response.PdfGenerateResultDto;
import com.openhis.whale.emr.application.document.response.EditorImageUploadDto;
import com.openhis.whale.emr.application.pdf.OpenPdfRenderer;
import com.openhis.whale.emr.application.pdf.PrintConfigResolver;
import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import com.openhis.whale.emr.application.pdf.model.PdfRenderResult;
import com.openhis.whale.emr.application.document.response.QualityResultDto;
import com.openhis.whale.emr.application.document.request.UploadEditorImageRequest;
import com.openhis.whale.emr.application.support.EmrEditorImageStore;
import com.openhis.whale.emr.application.support.EmrQuerySupport;
import com.openhis.whale.emr.application.document.response.StructureItemDto;
import com.openhis.whale.emr.application.document.response.VersionDto;
import com.openhis.whale.emr.application.document.request.ArchiveDocumentRequest;
import com.openhis.whale.emr.application.document.request.ChangeLockStateRequest;
import com.openhis.whale.emr.application.document.request.CreateDocumentRequest;
import com.openhis.whale.emr.application.document.request.GeneratePdfRequest;
import com.openhis.whale.emr.application.document.request.GetDocumentContentRequest;
import com.openhis.whale.emr.application.document.request.GetQualityResultRequest;
import com.openhis.whale.emr.application.document.request.GetStructureRequest;
import com.openhis.whale.emr.application.document.request.GetVersionContentRequest;
import com.openhis.whale.emr.application.document.request.GetVersionListRequest;
import com.openhis.whale.emr.application.document.request.InitEmrPageRequest;
import com.openhis.whale.emr.application.document.request.LongValueRequest;
import com.openhis.whale.emr.application.document.request.MergePdfRequest;
import com.openhis.whale.emr.application.document.request.QueryDocumentRequest;
import com.openhis.whale.emr.application.document.request.RollbackVersionRequest;
import com.openhis.whale.emr.application.document.request.SaveAsDocumentRequest;
import com.openhis.whale.emr.application.document.request.SaveDocumentRequest;
import com.openhis.whale.emr.application.document.request.ValidateDocumentRequest;
import com.openhis.whale.emr.application.document.response.InitEmrPageResponse;
import com.openhis.whale.emr.application.document.response.SaveDocumentResponse;
import com.openhis.whale.emr.application.document.response.ValidateDocumentResponse;
import com.openhis.whale.emr.application.encounterMetadata.request.ResolveEncounterMetadataRequest;
import com.openhis.whale.emr.application.encounterMetadata.request.SetValuesByNamesRequest;
import com.openhis.whale.emr.application.encounterMetadata.service.EncounterMetadataAppService;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.entity.DocumentType;
import com.openhis.whale.emr.domain.entity.DocumentVersion;
import com.openhis.whale.emr.domain.model.StructureItem;
import com.openhis.whale.emr.domain.service.DocumentManager;
import com.openhis.whale.emr.domain.service.DocumentStructureManager;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * 电子病历文书 → {@code POST /api/app/document/*}。
 * 负责文书 CRUD、版本、结构解析、PDF 生成与锁定状态变更。
 */
@Service
@Validated
public class DocumentAppService {

    private final DocumentManager documentManager;
    private final DocumentStructureManager documentStructureManager;
    private final OpenPdfRenderer openPdfRenderer;
    private final PrintConfigResolver printConfigResolver;
    private final EncounterMetadataAppService encounterMetadataAppService;
    private final EmrEditorImageStore emrEditorImageStore;
    private final ICurrentUser currentUser;

    public DocumentAppService(
            DocumentManager documentManager,
            DocumentStructureManager documentStructureManager,
            OpenPdfRenderer openPdfRenderer,
            PrintConfigResolver printConfigResolver,
            EncounterMetadataAppService encounterMetadataAppService,
            EmrEditorImageStore emrEditorImageStore,
            @Autowired(required = false) ICurrentUser currentUser) {
        this.documentManager = documentManager;
        this.documentStructureManager = documentStructureManager;
        this.openPdfRenderer = openPdfRenderer;
        this.printConfigResolver = printConfigResolver;
        this.encounterMetadataAppService = encounterMetadataAppService;
        this.emrEditorImageStore = emrEditorImageStore;
        this.currentUser = currentUser;
    }

    /**
     * 编辑器图片上传：落盘至 uploads/emr，返回 /uploads/emr/... 相对 URL。
     */
    public EditorImageUploadDto uploadEditorImage(@Valid UploadEditorImageRequest request) throws java.io.IOException {
        // Cmd：解码并落盘至 uploads/emr
        String url = emrEditorImageStore.saveImage(
                request.getContentBase64(),
                request.getFileName(),
                request.getMimeType());
        // Asm：返回可访问 URL
        EditorImageUploadDto dto = new EditorImageUploadDto();
        dto.setUrl(url);
        return dto;
    }

    /**
     * 新建文书 → {@code POST /api/app/document/create}
     *
     * @param request 患者、就诊、文书类型；可选 templateId 初始化内容
     * @return 含 content 的文书 DTO
     */
    public DocumentContentDto create(@Valid CreateDocumentRequest request) {
        // Cmd：领域层创建文书（含首次病程唯一性等业务规则）
        Document doc = documentManager.create(
                request.getPatientId(),
                request.getEncounterId(),
                request.getDocumentTypeCode(),
                parseOptionalLongId(request.getTemplateId(), "templateId"));
        // Asm：映射 content DTO
        return toContentDto(doc);
    }

    /** {@link #getDocumentContent} 的 API 别名。 */
    public DocumentContentDto getContent(@Valid GetDocumentContentRequest request) {
        return getDocumentContent(request);
    }

    /**
     * 文书分页列表 → {@code POST /api/app/document/getList}
     *
     * @param request 患者/就诊/类型/状态过滤与分页
     * @return 文书摘要分页
     */
    public PagedResult<DocumentSummaryDto> getList(@Valid QueryDocumentRequest request) {
        // Gate：归一化分页
        int pageNo = EmrQuerySupport.resolvePageNo(request.getPageNo(), request.getPageIndex());
        int pageSize = EmrQuerySupport.resolvePageSize(request.getPageSize());
        // Cmd：领域分页查询
        var docPage = documentManager.findPage(
                toPatientId(request.getPatientId()),
                toEncounterId(request.getEncounterId()),
                request.getDocumentTypeCode(),
                request.getStatus(),
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        // Asm：映射摘要分页
        List<DocumentSummaryDto> items = docPage.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return PagedResult.of(items, docPage.getTotalElements(), pageNo, pageSize);
    }

    /**
     * 保存文书内容 → {@code POST /api/app/document/save}
     *
     * @param request documentId、content；可选 saveVersion
     * @return 保存后的摘要
     */
    public DocumentSummaryDto save(@Valid SaveDocumentRequest request) {
        boolean saveVersion = Boolean.TRUE.equals(request.getSaveVersion());
        // Gate：documentId 必填且文书须可编辑（非锁定/归档）
        Long documentId = parseRequiredLongId(request.getDocumentId(), "documentId");
        Document existing = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        assertCanEditDocument(existing);
        // Cmd：保存正文并可选生成版本
        Document doc = documentManager.saveContent(documentId, request.getContent(), saveVersion);
        // Asm：返回摘要
        return toSummaryDto(doc);
    }

    /**
     * 另存为新文书 → {@code POST /api/app/document/saveAs}
     *
     * @param request 源 documentId 与新标题等
     * @return 新文书 content DTO
     */
    public DocumentContentDto saveAs(@Valid SaveAsDocumentRequest request) {
        // Cmd：复制源文书为新记录
        Document doc = documentManager.saveAs(
                request.getSourceDocumentId(),
                request.getPatientId(),
                request.getEncounterId(),
                request.getDocumentTypeCode());
        return toContentDto(doc);
    }

    /**
     * 文书内容校验（占位）→ {@code POST /api/app/document/validate}
     *
     * @param request 含 documentId
     * @return 校验结果
     */
    public ValidateDocumentResponse validate(@Valid ValidateDocumentRequest request) {
        return new ValidateDocumentResponse();
    }

    /**
     * 软删除文书 → {@code POST /api/app/document/delete}
     *
     * @param request 含 documentId
     */
    public void delete(@Valid LongValueRequest request) {
        // Gate：id 为空则 noop
        if (request == null || request.getValue() == null) {
            return;
        }
        // Cmd：软删文书
        documentManager.softDelete(request.getValue());
    }

    // —— 扩展能力（工作页 / 版本 / PDF 等，路径仍为 /api/app/document/*）——

    /**
     * EMR 工作页初始化 → {@code POST /api/app/document/initEmrPage}
     *
     * @param request 占位，可扩展患者/就诊上下文
     * @return 可用文书类型编码列表
     */
    public InitEmrPageResponse initEmrPage(@Valid InitEmrPageRequest request) {
        // Pre：加载全部文书类型编码
        List<DocumentType> types = documentManager.listDocumentTypes();
        // Asm：组装工作页初始化数据
        InitEmrPageResponse response = new InitEmrPageResponse();
        response.setAvailableDocumentTypes(types.stream().map(DocumentType::getCode).collect(Collectors.toList()));
        response.setPrintOptions(Collections.emptyList());
        return response;
    }

    /**
     * 加载文书正文 → {@code POST /api/app/document/getDocumentContent}
     *
     * @param request documentId 或 patientId+encounterId+documentTypeCode
     * @return 文书 content；未找到时返回空 DTO
     */
    public DocumentContentDto getDocumentContent(@Valid GetDocumentContentRequest request) {
        Document doc;
        // Pre：优先 documentId，否则按 patient+encounter+type 取最新
        Long documentId = parseOptionalLongId(request.getDocumentId(), "documentId");
        if (documentId != null) {
            doc = documentManager.findById(documentId).orElse(null);
        } else {
            doc = documentManager.findLatestByPatientAndEncounterAndType(
                    toPatientId(request.getPatientId()),
                    toEncounterId(request.getEncounterId()),
                    request.getDocumentTypeCode()).orElse(null);
        }
        // Asm：未找到返回空 DTO，否则映射 content
        if (doc == null) {
            return new DocumentContentDto();
        }
        return toContentDto(doc);
    }

    /**
     * 保存文书并返回版本号 → {@code POST /api/app/document/saveDocument}
     *
     * @param request documentId、content、saveVersion
     * @return 文书 id 与新版本号（未存版本时为 0）
     */
    public SaveDocumentResponse saveDocument(@Valid SaveDocumentRequest request) {
        boolean saveVersion = Boolean.TRUE.equals(request.getSaveVersion());
        // Gate：须可编辑
        Long documentId = parseRequiredLongId(request.getDocumentId(), "documentId");
        Document existing = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        assertCanEditDocument(existing);
        // Cmd：保存并可选版本快照
        Document doc = documentManager.saveContent(documentId, request.getContent(), saveVersion);
        // Asm：计算新版本号并返回
        int newVer = saveVersion
                ? documentManager.getVersionList(doc.getId()).stream()
                .map(DocumentVersion::getVersionNumber)
                .max(Integer::compareTo)
                .orElse(0)
                : 0;
        SaveDocumentResponse response = new SaveDocumentResponse();
        response.setDocumentId(doc.getId());
        response.setNewVersionNumber(newVer);
        return response;
    }

    /**
     * 文书版本列表 → {@code POST /api/app/document/getVersionList}
     *
     * @param request 含 documentId
     * @return 版本摘要列表
     */
    public List<VersionDto> getVersionList(@Valid GetVersionListRequest request) {
        // Cmd：查版本列表
        return documentManager.getVersionList(request.getDocumentId()).stream()
                .map(this::toVersionDto)
                .collect(Collectors.toList());
    }

    /**
     * 读取历史版本正文 → {@code POST /api/app/document/getVersionContent}
     *
     * @param request documentId 与 versionId 或 versionNumber
     * @return 只读 content DTO
     */
    public DocumentContentDto getVersionContent(@Valid GetVersionContentRequest request) {
        // Cmd：定位历史版本
        var versionOpt = documentManager.getVersion(
                request.getDocumentId(), request.getVersionId(), request.getVersionNumber());
        // Asm：只读 content DTO
        if (versionOpt.isEmpty()) {
            return new DocumentContentDto();
        }
        DocumentVersion v = versionOpt.get();
        DocumentContentDto dto = new DocumentContentDto();
        dto.setContent(v.getContent());
        dto.setReadOnly(true);
        return dto;
    }

    /**
     * 回滚到指定版本 → {@code POST /api/app/document/rollbackVersion}
     *
     * @param request documentId 与目标版本
     */
    public void rollbackVersion(@Valid RollbackVersionRequest request) {
        // Cmd：写回版本内容并生成新版本记录
        documentManager.rollbackToVersion(request.getDocumentId(), request.getVersionId(), request.getVersionNumber());
    }

    /**
     * 生成 PDF → {@code POST /api/app/document/generatePdf}
     *
     * @param request 文书 id 或内联 contentJson 与打印配置
     * @return PDF 字节 Base64 与页数
     */
    public PdfGenerateResultDto generatePdf(@Valid GeneratePdfRequest request) {
        // Pre：解析正文 JSON 并 enrich 打印元数据
        String contentJson = resolveContentJson(request);
        enrichRenderMetadata(request);
        PrintConfig printConfig = printConfigResolver.resolve(request);
        // Cmd：OpenPDF 渲染
        PdfRenderResult result = openPdfRenderer.render(contentJson, request);
        // Asm：Base64 + 页数
        return toPdfResultDto(result, contentJson, printConfig);
    }

    /**
     * 合并多份 PDF → {@code POST /api/app/document/mergePdf}
     *
     * @param request 多段 PDF Base64
     * @return 合并后的 PDF
     */
    public PdfGenerateResultDto mergePdf(@Valid MergePdfRequest request) {
        // Gate：无 documentIds 返回空结果
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            return new PdfGenerateResultDto();
        }
        // Pre：共用打印配置模板
        GeneratePdfRequest single = new GeneratePdfRequest();
        single.setPreviewMode(request.getPreviewMode());
        single.setPrintConfig(request.getPrintConfig());
        single.setPatientId(request.getPatientId());
        single.setEncounterId(request.getEncounterId());
        if (request.getMetadata() != null) {
            single.setMetadata(new HashMap<>(request.getMetadata()));
        }
        List<byte[]> parts = new ArrayList<>();
        int totalPages = 0;
        // Cmd：逐份文书渲染 PDF 字节
        for (String docIdRaw : request.getDocumentIds()) {
            Long docId = parseOptionalLongId(docIdRaw, "documentId");
            if (docId == null) {
                continue;
            }
            GeneratePdfRequest partReq = copyPdfRequestBasics(single);
            partReq.setDocumentId(String.valueOf(docId));
            String content = resolveContentJson(partReq);
            enrichRenderMetadata(partReq);
            PdfRenderResult partResult = openPdfRenderer.render(content, partReq);
            parts.add(partResult.getPdfBytes());
            totalPages += partResult.getPageCount();
        }
        if (parts.isEmpty()) {
            throw new ValidationException("没有可合并的有效文书");
        }
        // Asm：合并字节流并返回 Base64
        byte[] merged = openPdfRenderer.mergePdfBytes(parts);
        PdfRenderResult mergedResult = new PdfRenderResult(merged, totalPages, false);
        return toPdfResultDto(mergedResult, null, printConfigResolver.resolve(single));
    }

    private String resolveContentJson(GeneratePdfRequest request) {
        if (StringUtils.hasText(request.getContent())) {
            return request.getContent();
        }
        Long documentId = parseOptionalLongId(request.getDocumentId(), "documentId");
        if (documentId == null) {
            throw new com.openhis.whale.core.application.exception.BusinessException(
                    "EMR_PDF_NO_CONTENT", "请提供 documentId 或 content");
        }
        Document doc = documentManager.findById(documentId)
                .orElseThrow(() -> new com.openhis.whale.core.application.exception.BusinessException(
                        "EMR_DOCUMENT_NOT_FOUND", "文档不存在: " + request.getDocumentId()));
        if (!StringUtils.hasText(doc.getContent())) {
            throw new com.openhis.whale.core.application.exception.BusinessException(
                    "EMR_PDF_EMPTY", "文档内容为空");
        }
        if (!StringUtils.hasText(request.getDocumentTitle())) {
            request.setDocumentTitle(doc.getTitle());
        }
        if (!StringUtils.hasText(request.getDocumentTypeName())) {
            documentManager.getDocumentTypeByCode(doc.getDocumentTypeCode())
                    .ifPresent(t -> request.setDocumentTypeName(t.getName()));
        }
        return doc.getContent();
    }

    private void enrichRenderMetadata(GeneratePdfRequest request) {
        if (request.getMetadata() == null) {
            request.setMetadata(new HashMap<>());
        }
        Long documentId = parseOptionalLongId(request.getDocumentId(), "documentId");
        if (!StringUtils.hasText(toPatientId(request.getPatientId())) && documentId != null) {
            documentManager.findById(documentId).ifPresent(doc -> {
                if (!StringUtils.hasText(toPatientId(request.getPatientId()))) {
                    request.setPatientId(doc.getPatientId());
                }
                if (!StringUtils.hasText(toEncounterId(request.getEncounterId()))) {
                    request.setEncounterId(doc.getEncounterId());
                }
            });
        }
        String patientId = toPatientId(request.getPatientId());
        String encounterId = toEncounterId(request.getEncounterId());
        if (patientId != null) {
            request.getMetadata().putIfAbsent("patientId", patientId);
        }
        if (encounterId != null) {
            request.getMetadata().putIfAbsent("encounterId", encounterId);
        }
        if (StringUtils.hasText(patientId)) {
            ResolveEncounterMetadataRequest resolveReq = new ResolveEncounterMetadataRequest();
            resolveReq.setPatientId(patientId);
            resolveReq.setEncounterId(encounterId);
            resolveReq.setDocumentId(documentId);
            resolveReq.setMetadataOverrides(new HashMap<>(request.getMetadata()));
            Map<String, String> resolved = encounterMetadataAppService.resolveValues(resolveReq).getValues();
            if (resolved != null) {
                resolved.forEach(request.getMetadata()::putIfAbsent);
            }
        }
    }

    private static GeneratePdfRequest copyPdfRequestBasics(GeneratePdfRequest source) {
        GeneratePdfRequest target = new GeneratePdfRequest();
        target.setPreviewMode(source.getPreviewMode());
        target.setPrintConfig(source.getPrintConfig());
        target.setWatermark(source.getWatermark());
        target.setHeaderTemplate(source.getHeaderTemplate());
        target.setFooterTemplate(source.getFooterTemplate());
        target.setIncludeTrackChanges(source.getIncludeTrackChanges());
        target.setPreviewMaxPages(source.getPreviewMaxPages());
        target.setMetadata(EmrQuerySupport.copyMetadataMap(source.getMetadata()));
        return target;
    }

    private PdfGenerateResultDto toPdfResultDto(PdfRenderResult result, String contentJson, PrintConfig printConfig) {
        PdfGenerateResultDto dto = new PdfGenerateResultDto();
        dto.setPdfBase64(Base64.getEncoder().encodeToString(result.getPdfBytes()));
        dto.setPageCount(result.getPageCount());
        dto.setRenderedAt(result.getRenderedAt());
        dto.setPreviewTruncated(result.isPreviewTruncated());
        if (contentJson != null) {
            dto.setContentHash(openPdfRenderer.computeContentHash(contentJson, printConfig));
        }
        return dto;
    }

    /**
     * 解析文书结构树 → {@code POST /api/app/document/getStructureList}
     *
     * @param request documentId 或内联 contentJson；可选 nodeTypes
     * @return region/section/dataElement 结构项
     */
    public List<StructureItemDto> getStructureList(@Valid GetStructureRequest request) {
        // Pre：解析 contentJson（请求内联或从文书加载）
        String contentJson = request.getContent();
        if (contentJson == null && request.getDocumentId() != null) {
            contentJson = documentManager.findById(request.getDocumentId())
                    .map(Document::getContent)
                    .orElse(null);
        }
        if (contentJson == null || contentJson.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> nodeTypes = null;
        if (request.getNodeTypes() != null && !request.getNodeTypes().isEmpty()) {
            nodeTypes = new HashSet<>(request.getNodeTypes());
        }
        boolean firstLevelOnly = Boolean.TRUE.equals(request.getFirstLevelOnly());
        // Cmd：解析 ProseMirror 结构树
        List<StructureItem> items = documentStructureManager.parseStructure(contentJson, firstLevelOnly, nodeTypes);
        // Asm：映射 DTO 列表
        return items.stream().map(this::toStructureItemDto).collect(Collectors.toList());
    }

    /**
     * 按数据项名批量写入 value → {@code POST /api/app/document/setValuesByNames}
     *
     * @param request documentId 与 name→value 映射
     */
    public void setValuesByNames(@Valid SetValuesByNamesRequest request) {
        // Gate：documentId 与映射非空
        if (request.getDocumentId() == null || request.getNameToValue() == null || request.getNameToValue().isEmpty()) {
            return;
        }
        Document doc = documentManager.findById(request.getDocumentId()).orElse(null);
        if (doc == null) {
            return;
        }
        // Cmd：按 name 写入 attrs.value 并保存（不生成版本）
        String newContent = documentStructureManager.setValuesByNames(doc.getContent(), request.getNameToValue());
        documentManager.saveContent(request.getDocumentId(), newContent, false);
    }

    /**
     * 质控结果（占位）→ {@code POST /api/app/document/getQualityResult}
     *
     * @param request 含 documentId
     * @return 空质控结果
     */
    public QualityResultDto getQualityResult(@Valid GetQualityResultRequest request) {
        return new QualityResultDto();
    }

    /**
     * 锁定/解锁/归档文书 → {@code POST /api/app/document/changeLockState}
     *
     * @param request documentId 与目标 lockState
     */
    public void changeLockState(@Valid ChangeLockStateRequest request) {
        // Gate：action 映射为领域 status（LOCK→LOCKED，否则回 DRAFT）
        String status;
        if ("LOCK".equalsIgnoreCase(request.getAction())) {
            status = DocumentManager.STATUS_LOCKED;
        } else {
            status = DocumentManager.STATUS_DRAFT;
        }
        // Cmd：更新文书状态
        documentManager.updateStatus(request.getDocumentId(), status);
    }

    private static String toPatientId(Object patientId) {
        return EmrQuerySupport.stringIdOrNull(patientId);
    }

    private static String toEncounterId(Object encounterId) {
        return EmrQuerySupport.stringIdOrNull(encounterId);
    }

    private static Long parseRequiredLongId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new ValidationException(fieldName + " 不能为空");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(fieldName + " 格式无效: " + raw);
        }
    }

    private static Long parseOptionalLongId(String raw, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return parseRequiredLongId(raw, fieldName);
    }

    private boolean isDocumentReadOnly(Document doc) {
        if (DocumentManager.STATUS_LOCKED.equals(doc.getStatus())
                || DocumentManager.STATUS_ARCHIVED.equals(doc.getStatus())) {
            return true;
        }
        if (DocumentManager.STATUS_DRAFT.equals(doc.getStatus()) && !isDraftEditableByCurrentUser(doc)) {
            return true;
        }
        return false;
    }

    private boolean isDraftEditableByCurrentUser(Document doc) {
        Long currentUserId = null;
        if (currentUser != null) {
            currentUserId = currentUser.getCurrentUserId();
        }
        if (currentUserId == null) {
            return true;
        }
        Long createdBy = doc.getCreatedBy();
        if (createdBy == null) {
            return true;
        }
        return currentUserId.equals(createdBy);
    }

    private void assertCanEditDocument(Document doc) {
        if (DocumentManager.STATUS_LOCKED.equals(doc.getStatus())
                || DocumentManager.STATUS_ARCHIVED.equals(doc.getStatus())) {
            throw new ValidationException("文书已锁定或归档，不可修改");
        }
        if (DocumentManager.STATUS_DRAFT.equals(doc.getStatus()) && !isDraftEditableByCurrentUser(doc)) {
            throw new ValidationException("仅创建者可修改该文书");
        }
    }

    private DocumentSummaryDto toSummaryDto(Document doc) {
        DocumentSummaryDto dto = new DocumentSummaryDto();
        dto.setId(doc.getId());
        dto.setPatientId(doc.getPatientId());
        dto.setEncounterId(doc.getEncounterId());
        dto.setDocumentTypeCode(doc.getDocumentTypeCode());
        dto.setTitle(doc.getTitle());
        dto.setStatus(doc.getStatus());
        dto.setCreatedAt(doc.getCreatedAt());
        dto.setUpdatedAt(doc.getUpdatedAt());
        dto.setCreatedBy(doc.getCreatedBy());
        dto.setUpdatedBy(doc.getUpdatedBy());
        documentManager.getDocumentTypeByCode(doc.getDocumentTypeCode())
                .ifPresent(t -> dto.setDocumentTypeName(t.getName()));
        return dto;
    }

    private DocumentContentDto toContentDto(Document doc) {
        DocumentContentDto dto = new DocumentContentDto();
        dto.setId(doc.getId());
        dto.setPatientId(doc.getPatientId());
        dto.setEncounterId(doc.getEncounterId());
        dto.setDocumentTypeCode(doc.getDocumentTypeCode());
        dto.setTitle(doc.getTitle());
        dto.setStatus(doc.getStatus());
        dto.setContent(doc.getContent());
        dto.setReadOnly(isDocumentReadOnly(doc));
        if (StringUtils.hasText(doc.getPatientId())) {
            ResolveEncounterMetadataRequest resolveReq = new ResolveEncounterMetadataRequest();
            resolveReq.setPatientId(doc.getPatientId());
            resolveReq.setEncounterId(doc.getEncounterId());
            resolveReq.setDocumentId(doc.getId());
            dto.setMetadataValues(encounterMetadataAppService.resolveValues(resolveReq).getValues());
        }
        return dto;
    }

    private VersionDto toVersionDto(DocumentVersion v) {
        VersionDto dto = new VersionDto();
        dto.setId(v.getId());
        dto.setDocumentId(v.getDocumentId());
        dto.setVersionNumber(v.getVersionNumber());
        dto.setCreatedAt(v.getCreatedAt());
        dto.setCreatedBy(v.getCreatedBy());
        return dto;
    }

    private StructureItemDto toStructureItemDto(StructureItem item) {
        StructureItemDto dto = new StructureItemDto();
        dto.setName(item.getName());
        dto.setTitle(item.getTitle());
        dto.setNodeType(item.getNodeType());
        dto.setDepth(item.getDepth());
        dto.setPageNo(item.getPageNo());
        if (item.getChildren() != null) {
            dto.setChildren(item.getChildren().stream().map(this::toStructureItemDto).collect(Collectors.toList()));
        }
        return dto;
    }
}
