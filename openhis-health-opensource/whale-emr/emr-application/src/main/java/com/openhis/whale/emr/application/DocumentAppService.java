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

package com.openhis.whale.emr.application;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.emr.application.dto.DocumentContentDto;
import com.openhis.whale.emr.application.dto.DocumentSummaryDto;
import com.openhis.whale.emr.application.dto.PdfGenerateResultDto;
import com.openhis.whale.emr.application.dto.EditorImageUploadDto;
import com.openhis.whale.emr.application.pdf.OpenPdfRenderer;
import com.openhis.whale.emr.application.pdf.PrintConfigResolver;
import com.openhis.whale.emr.application.pdf.config.PrintConfig;
import com.openhis.whale.emr.application.pdf.model.PdfRenderResult;
import com.openhis.whale.emr.application.dto.QualityResultDto;
import com.openhis.whale.emr.application.request.UploadEditorImageRequest;
import com.openhis.whale.emr.application.support.EmrEditorImageStore;
import com.openhis.whale.emr.application.dto.StructureItemDto;
import com.openhis.whale.emr.application.dto.VersionDto;
import com.openhis.whale.emr.application.request.*;
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
 * 电子病历文书 AppService → /api/app/document/{methodName}
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
        String url = emrEditorImageStore.saveImage(
                request.getContentBase64(),
                request.getFileName(),
                request.getMimeType());
        EditorImageUploadDto dto = new EditorImageUploadDto();
        dto.setUrl(url);
        return dto;
    }

    public DocumentContentDto create(@Valid CreateDocumentRequest request) {
        Document doc = documentManager.create(
                request.getPatientId(),
                request.getEncounterId(),
                request.getDocumentTypeCode(),
                parseOptionalLongId(request.getTemplateId(), "templateId"));
        return toContentDto(doc);
    }

    public DocumentContentDto getContent(@Valid GetDocumentContentRequest request) {
        return getDocumentContent(request);
    }

    public PagedResult<DocumentSummaryDto> getList(@Valid QueryDocumentRequest request) {
        int pageNo = resolvePageNo(request.getPageNo(), request.getPageIndex());
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
        var docPage = documentManager.findPage(
                toPatientId(request.getPatientId()),
                toEncounterId(request.getEncounterId()),
                request.getDocumentTypeCode(),
                request.getStatus(),
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<DocumentSummaryDto> items = docPage.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return PagedResult.of(items, docPage.getTotalElements(), pageNo, pageSize);
    }

    public DocumentSummaryDto save(@Valid SaveDocumentRequest request) {
        boolean saveVersion = Boolean.TRUE.equals(request.getSaveVersion());
        Long documentId = parseRequiredLongId(request.getDocumentId(), "documentId");
        Document existing = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        assertCanEditDocument(existing);
        Document doc = documentManager.saveContent(documentId, request.getContent(), saveVersion);
        return toSummaryDto(doc);
    }

    public DocumentContentDto saveAs(@Valid SaveAsDocumentRequest request) {
        Document doc = documentManager.saveAs(
                request.getSourceDocumentId(),
                request.getPatientId(),
                request.getEncounterId(),
                request.getDocumentTypeCode());
        return toContentDto(doc);
    }

    public ValidateDocumentResponse validate(@Valid ValidateDocumentRequest request) {
        return new ValidateDocumentResponse();
    }

    public void delete(@Valid LongValueRequest request) {
        if (request == null || request.getValue() == null) {
            return;
        }
        documentManager.softDelete(request.getValue());
    }

    // —— 扩展能力（工作页 / 版本 / PDF 等，路径仍为 /api/app/document/*）——

    public InitEmrPageResponse initEmrPage(@Valid InitEmrPageRequest request) {
        List<DocumentType> types = documentManager.listDocumentTypes();
        InitEmrPageResponse response = new InitEmrPageResponse();
        response.setAvailableDocumentTypes(types.stream().map(DocumentType::getCode).collect(Collectors.toList()));
        response.setPrintOptions(Collections.emptyList());
        return response;
    }

    public DocumentContentDto getDocumentContent(@Valid GetDocumentContentRequest request) {
        Document doc;
        Long documentId = parseOptionalLongId(request.getDocumentId(), "documentId");
        if (documentId != null) {
            doc = documentManager.findById(documentId).orElse(null);
        } else {
            doc = documentManager.findLatestByPatientAndEncounterAndType(
                    toPatientId(request.getPatientId()),
                    toEncounterId(request.getEncounterId()),
                    request.getDocumentTypeCode()).orElse(null);
        }
        if (doc == null) {
            return new DocumentContentDto();
        }
        return toContentDto(doc);
    }

    public SaveDocumentResponse saveDocument(@Valid SaveDocumentRequest request) {
        boolean saveVersion = Boolean.TRUE.equals(request.getSaveVersion());
        Long documentId = parseRequiredLongId(request.getDocumentId(), "documentId");
        Document existing = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        assertCanEditDocument(existing);
        Document doc = documentManager.saveContent(documentId, request.getContent(), saveVersion);
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

    public List<VersionDto> getVersionList(@Valid GetVersionListRequest request) {
        return documentManager.getVersionList(request.getDocumentId()).stream()
                .map(this::toVersionDto)
                .collect(Collectors.toList());
    }

    public DocumentContentDto getVersionContent(@Valid GetVersionContentRequest request) {
        var versionOpt = documentManager.getVersion(
                request.getDocumentId(), request.getVersionId(), request.getVersionNumber());
        if (versionOpt.isEmpty()) {
            return new DocumentContentDto();
        }
        DocumentVersion v = versionOpt.get();
        DocumentContentDto dto = new DocumentContentDto();
        dto.setContent(v.getContent());
        dto.setReadOnly(true);
        return dto;
    }

    public void rollbackVersion(@Valid RollbackVersionRequest request) {
        documentManager.rollbackToVersion(request.getDocumentId(), request.getVersionId(), request.getVersionNumber());
    }

    public PdfGenerateResultDto generatePdf(@Valid GeneratePdfRequest request) {
        String contentJson = resolveContentJson(request);
        enrichRenderMetadata(request);
        PrintConfig printConfig = printConfigResolver.resolve(request);
        PdfRenderResult result = openPdfRenderer.render(contentJson, request);
        return toPdfResultDto(result, contentJson, printConfig);
    }

    public PdfGenerateResultDto mergePdf(@Valid MergePdfRequest request) {
        if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            return new PdfGenerateResultDto();
        }
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
        target.setMetadata(source.getMetadata() != null ? new HashMap<>(source.getMetadata()) : new HashMap<>());
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

    public List<StructureItemDto> getStructureList(@Valid GetStructureRequest request) {
        String contentJson = request.getContent();
        if (contentJson == null && request.getDocumentId() != null) {
            contentJson = documentManager.findById(request.getDocumentId())
                    .map(Document::getContent)
                    .orElse(null);
        }
        if (contentJson == null || contentJson.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> nodeTypes = request.getNodeTypes() != null && !request.getNodeTypes().isEmpty()
                ? new HashSet<>(request.getNodeTypes())
                : null;
        boolean firstLevelOnly = Boolean.TRUE.equals(request.getFirstLevelOnly());
        List<StructureItem> items = documentStructureManager.parseStructure(contentJson, firstLevelOnly, nodeTypes);
        return items.stream().map(this::toStructureItemDto).collect(Collectors.toList());
    }

    public void setValuesByNames(@Valid SetValuesByNamesRequest request) {
        if (request.getDocumentId() == null || request.getNameToValue() == null || request.getNameToValue().isEmpty()) {
            return;
        }
        Document doc = documentManager.findById(request.getDocumentId()).orElse(null);
        if (doc == null) {
            return;
        }
        String newContent = documentStructureManager.setValuesByNames(doc.getContent(), request.getNameToValue());
        documentManager.saveContent(request.getDocumentId(), newContent, false);
    }

    public QualityResultDto getQualityResult(@Valid GetQualityResultRequest request) {
        return new QualityResultDto();
    }

    public void changeLockState(@Valid ChangeLockStateRequest request) {
        String status = "LOCK".equalsIgnoreCase(request.getAction())
                ? DocumentManager.STATUS_LOCKED
                : DocumentManager.STATUS_DRAFT;
        documentManager.updateStatus(request.getDocumentId(), status);
    }

    private static int resolvePageNo(Integer pageNo, Integer pageIndex) {
        if (pageNo != null && pageNo > 0) {
            return pageNo;
        }
        if (pageIndex != null && pageIndex > 0) {
            return pageIndex;
        }
        return 1;
    }

    private static String toPatientId(Object patientId) {
        return patientId == null ? null : String.valueOf(patientId);
    }

    private static String toEncounterId(Object encounterId) {
        return encounterId == null ? null : String.valueOf(encounterId);
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
        Long currentUserId = currentUser != null ? currentUser.getCurrentUserId() : null;
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
