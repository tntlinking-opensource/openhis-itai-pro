/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.service;

import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.plugin.emr.application.document.response.DocumentContentDto;
import com.zxplat.plugin.emr.application.document.response.DocumentSummaryDto;
import com.zxplat.plugin.emr.application.document.response.PdfGenerateResultDto;
import com.zxplat.plugin.emr.application.document.response.EditorImageUploadDto;
import com.zxplat.plugin.emr.application.pdf.OpenPdfRenderer;
import com.zxplat.plugin.emr.application.pdf.PrintConfigResolver;
import com.zxplat.plugin.emr.application.pdf.config.PrintConfig;
import com.zxplat.plugin.emr.application.pdf.model.PdfRenderResult;
import com.zxplat.plugin.emr.application.document.response.QualityResultDto;
import com.zxplat.plugin.emr.application.document.request.UploadEditorImageRequest;
import com.zxplat.plugin.emr.application.support.EmrEditorImageStore;
import com.zxplat.plugin.emr.application.support.EmrQuerySupport;
import com.zxplat.plugin.emr.application.document.response.StructureItemDto;
import com.zxplat.plugin.emr.application.document.response.VersionDto;
import com.zxplat.plugin.emr.application.document.request.ChangeLockStateRequest;
import com.zxplat.plugin.emr.application.document.request.CreateDocumentRequest;
import com.zxplat.plugin.emr.application.document.request.GeneratePdfRequest;
import com.zxplat.plugin.emr.application.document.request.GetDocumentContentRequest;
import com.zxplat.plugin.emr.application.document.request.GetQualityResultRequest;
import com.zxplat.plugin.emr.application.document.request.GetStructureRequest;
import com.zxplat.plugin.emr.application.document.request.GetVersionContentRequest;
import com.zxplat.plugin.emr.application.document.request.GetVersionListRequest;
import com.zxplat.plugin.emr.application.document.request.InitEmrPageRequest;
import com.zxplat.plugin.emr.application.document.request.LongValueRequest;
import com.zxplat.plugin.emr.application.document.request.MergePdfRequest;
import com.zxplat.plugin.emr.application.document.request.QueryDocumentRequest;
import com.zxplat.plugin.emr.application.document.request.RollbackVersionRequest;
import com.zxplat.plugin.emr.application.document.request.SaveAsDocumentRequest;
import com.zxplat.plugin.emr.application.document.request.SaveDocumentRequest;
import com.zxplat.plugin.emr.application.document.request.UpdateDocumentTitleRequest;
import com.zxplat.plugin.emr.application.document.request.ValidateDocumentRequest;
import com.zxplat.plugin.emr.application.document.response.InitEmrPageResponse;
import com.zxplat.plugin.emr.application.document.response.SaveDocumentResponse;
import com.zxplat.plugin.emr.application.document.response.ValidateDocumentResponse;
import com.zxplat.plugin.emr.application.encounterMetadata.request.ResolveEncounterMetadataRequest;
import com.zxplat.plugin.emr.application.encounterMetadata.request.SetValuesByNamesRequest;
import com.zxplat.plugin.emr.application.encounterMetadata.service.EncounterMetadataAppService;
import com.zxplat.plugin.emr.domain.document.entity.Document;
import com.zxplat.plugin.emr.domain.document.entity.DocumentType;
import com.zxplat.plugin.emr.domain.document.entity.DocumentVersion;
import com.zxplat.plugin.emr.domain.document.model.StructureItem;
import com.zxplat.plugin.emr.domain.document.service.DocumentManager;
import com.zxplat.plugin.emr.domain.document.service.DocumentStructureManager;
import com.zxplat.plugin.emr.domain.enums.TemplateStationKind;
import com.zxplat.plugin.emr.domain.loader.EmrAdmissionContextLoader;
import com.zxplat.plugin.emr.domain.loader.EmrLinkedEncounterResolver;
import com.zxplat.plugin.emr.domain.template.entity.Template;
import com.zxplat.framework.identity.domain.User;
import com.zxplat.framework.identity.infrastructure.repository.UserRepository;
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
    private final EmrAdmissionContextLoader admissionContextLoader;
    private final EmrLinkedEncounterResolver linkedEncounterResolver;
    private final UserRepository userRepository;
    private final ICurrentUser currentUser;

    public DocumentAppService(
            DocumentManager documentManager,
            DocumentStructureManager documentStructureManager,
            OpenPdfRenderer openPdfRenderer,
            PrintConfigResolver printConfigResolver,
            EncounterMetadataAppService encounterMetadataAppService,
            EmrEditorImageStore emrEditorImageStore,
            EmrAdmissionContextLoader admissionContextLoader,
            @Autowired(required = false) EmrLinkedEncounterResolver linkedEncounterResolver,
            @Autowired(required = false) UserRepository userRepository,
            @Autowired(required = false) ICurrentUser currentUser) {
        this.documentManager = documentManager;
        this.documentStructureManager = documentStructureManager;
        this.openPdfRenderer = openPdfRenderer;
        this.printConfigResolver = printConfigResolver;
        this.encounterMetadataAppService = encounterMetadataAppService;
        this.emrEditorImageStore = emrEditorImageStore;
        this.admissionContextLoader = admissionContextLoader;
        if (linkedEncounterResolver != null) {
            this.linkedEncounterResolver = linkedEncounterResolver;
        } else {
            this.linkedEncounterResolver = EmrLinkedEncounterResolver.noop();
        }
        this.userRepository = userRepository;
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
        // Gate：解析就诊过滤集合（可选关联对端并集）
        List<String> encounterIds = resolveEncounterIdsForQuery(request);
        // Cmd：领域分页查询
        var docPage = documentManager.findPage(
                toPatientId(request.getPatientId()),
                encounterIds,
                request.getDocumentTypeCode(),
                request.getStatus(),
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        // Asm：映射摘要分页（科室/保存人批量解析，供病历树展示）
        Map<String, String> deptNameByEncounter = new HashMap<>();
        Map<Long, String> userNameById = new HashMap<>();
        List<DocumentSummaryDto> items = docPage.getContent().stream()
                .map(doc -> toSummaryDto(doc, deptNameByEncounter, userNameById))
                .collect(Collectors.toList());
        return PagedResult.of(items, docPage.getTotalElements(), pageNo, pageSize);
    }

    /**
     * 组装文书列表就诊过滤：显式 encounterIds ∪ encounterId；
     * 若 {@code includeLinkedEncounters=true} 再按门诊/住院关联展开。
     */
    private List<String> resolveEncounterIdsForQuery(QueryDocumentRequest request) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request.getEncounterIds() != null) {
            for (String raw : request.getEncounterIds()) {
                String id = toEncounterId(raw);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        String single = toEncounterId(request.getEncounterId());
        if (single != null) {
            ids.add(single);
        }
        if (Boolean.TRUE.equals(request.getIncludeLinkedEncounters()) && single != null) {
            List<String> linked = linkedEncounterResolver.resolveLinkedEncounterIds(single);
            if (linked != null) {
                for (String id : linked) {
                    String normalized = toEncounterId(id);
                    if (normalized != null) {
                        ids.add(normalized);
                    }
                }
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        return new ArrayList<>(ids);
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
        return toSummaryDto(doc, new HashMap<>(), new HashMap<>());
    }

    /**
     * 修改已写文书标题 → {@code POST /api/app/document/updateTitle}。
     * 仅改 {@code emr_document.title}（侧栏/Tab 显示名），不改正文；门禁与保存正文一致。
     *
     * @param request documentId、title
     * @return 更新后的文书摘要
     */
    public DocumentSummaryDto updateTitle(@Valid UpdateDocumentTitleRequest request) {
        // Gate：documentId 必填
        Long documentId = parseRequiredLongId(request.getDocumentId(), "documentId");
        // Pre：加载未删文书并校验编辑权限（锁定/归档、门诊仅创建者等）
        Document existing = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        assertCanEditDocument(existing);
        // Cmd：领域侧校验标题非空/长度并落库
        Document doc = documentManager.updateTitle(documentId, request.getTitle());
        // Asm：返回最新摘要供前端刷新列表与 Tab
        return toSummaryDto(doc, new HashMap<>(), new HashMap<>());
    }

    /**
     * 已归档文书提交修改归档 → {@code POST /api/app/document/submitRevisedArchive}。
     * 与首次提交进同一待审队列，对比走历史版本。
     *
     * @param request documentId 与待审 content
     */
    public void submitRevisedArchive(@Valid SaveDocumentRequest request) {
        Long documentId = parseRequiredLongId(request.getDocumentId(), "documentId");
        if (request.getContent() == null || !StringUtils.hasText(request.getContent())) {
            throw new ValidationException("提交内容不能为空");
        }
        Long operatorId = null;
        if (currentUser != null) {
            operatorId = currentUser.getCurrentUserId();
        }
        documentManager.submitRevisedArchive(documentId, request.getContent(), operatorId);
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
     * @param request 含 documentId（LongValueRequest.value）
     */
    public void delete(@Valid LongValueRequest request) {
        // Gate：id 为空则 noop
        if (request == null || request.getValue() == null) {
            return;
        }
        // Pre：加载未删文书
        Document doc = documentManager.findById(request.getValue())
                .orElseThrow(() -> new EntityNotFoundException("文书", request.getValue()));
        // Gate：锁定/归档不可删；仅创建者可删（病程/会诊他人可编但不可替删）
        assertCanDeleteDocument(doc);
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
        int newVer = 0;
        if (saveVersion) {
            newVer = documentManager.getVersionList(doc.getId()).stream()
                    .map(DocumentVersion::getVersionNumber)
                    .max(Integer::compareTo)
                    .orElse(0);
        }
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
            throw new com.zxplat.framework.core.application.exception.BusinessException(
                    "EMR_PDF_NO_CONTENT", "请提供 documentId 或 content");
        }
        Document doc = documentManager.findById(documentId)
                .orElseThrow(() -> new com.zxplat.framework.core.application.exception.BusinessException(
                        "EMR_DOCUMENT_NOT_FOUND", "文档不存在: " + request.getDocumentId()));
        if (!StringUtils.hasText(doc.getContent())) {
            throw new com.zxplat.framework.core.application.exception.BusinessException(
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
        // Gate：LOCK→ARCHIVED（归档只读），否则回 DRAFT
        String status;
        if ("LOCK".equalsIgnoreCase(request.getAction())) {
            status = DocumentManager.STATUS_ARCHIVED;
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
        String status = "";
        if (doc.getStatus() != null) {
            status = doc.getStatus().trim();
        }
        if (DocumentManager.STATUS_ARCHIVED.equals(status)
                || DocumentManager.STATUS_PENDING_ARCHIVE.equals(status)) {
            return true;
        }
        // 住院病历 / 病程 / 会诊：多人可编；门诊文书仍仅创建者可编
        if (allowsMultiUserEdit(doc)) {
            return false;
        }
        return !isEditableByCurrentUser(doc);
    }

    /**
     * 是否允许多人编辑（未锁定/归档前提下）。
     *
     * <p>住院就诊（{@code EncounterClass=IMP}）下的全部文书放开，含告知书、手术病历等。</p>
     * <p>就诊类别读不到时，再按文书类型分类 INPATIENT / SURGERY 兜底。</p>
     * <p>门诊病历不走本放行，仍仅创建者可改。</p>
     *
     * @param doc 已落库文书
     * @return true 时编辑/删除均不再校验创建者
     */
    private boolean allowsMultiUserEdit(Document doc) {
        String typeCode = doc.getDocumentTypeCode();
        // 先按文书类型编码识别病程/会诊
        if (StringUtils.hasText(typeCode)) {
            String upper = typeCode.trim().toUpperCase();
            // 病程、会诊类型编码：多人可编
            if ("PROGRESS_NOTE".equals(upper) || upper.contains("CONSULT")) {
                return true;
            }
        }
        // 住院就诊下所有文书：住院医生站全员可改
        if (isInpatientEncounterDocument(doc)) {
            return true;
        }
        // HIS 未返回就诊类别时：住院/手术分类仍放开
        if (isInpatientCategoryDocument(doc)) {
            return true;
        }
        Long templateId = doc.getSourceTemplateId();
        // 会诊已并入告知书类型：用源模板编码/名称兜底识别
        if (templateId != null) {
            Optional<Template> templateOpt = documentManager.getTemplateById(templateId);
            if (templateOpt.isPresent()) {
                Template template = templateOpt.get();
                String code = template.getCode();
                if (StringUtils.hasText(code) && code.toUpperCase().contains("CONSULT")) {
                    return true;
                }
                String name = template.getName();
                if (StringUtils.hasText(name) && name.contains("会诊")) {
                    return true;
                }
            }
        }
        String title = doc.getTitle();
        return StringUtils.hasText(title) && title.contains("会诊");
    }

    /**
     * 文书是否挂在住院就诊上（{@code encounter.classEnum = IMP}）。
     *
     * <p>B.6.4：经 {@link EmrAdmissionContextLoader} 读就诊类别，不直访 health Encounter 仓储。</p>
     * <p>读不到类别时返回 false，由 {@link #isInpatientCategoryDocument} 按类型分类兜底。</p>
     *
     * @param doc 已落库文书
     * @return true 表示住院就诊，该就诊下文书全员可改
     */
    private boolean isInpatientEncounterDocument(Document doc) {
        String encounterId = doc.getEncounterId();
        // 无就诊无法判住院，不放行
        if (!StringUtils.hasText(encounterId)) {
            return false;
        }
        Map<String, String> ctx = new HashMap<>();
        try {
            admissionContextLoader.contributeEncounter(ctx, encounterId.trim());
        } catch (RuntimeException ignored) {
            // HIS 未就绪时不按就诊放开，避免误判门诊
            return false;
        }
        String classEnum = ctx.get("encounter.classEnum");
        // 上下文未带就诊类别时交给类型分类兜底
        if (!StringUtils.hasText(classEnum)) {
            return false;
        }
        return TemplateStationKind.INPATIENT.getEncounterClassCode().equalsIgnoreCase(classEnum.trim());
    }

    /**
     * 文书类型是否属于住院侧分类（就诊类别读不到时的兜底）。
     *
     * <p>INPATIENT（入院/出院/病程等）与 SURGERY（手术病历）放开；告知书靠住院就诊判定，避免误开门诊告知书。</p>
     *
     * @param doc 已落库文书
     * @return true 表示住院分类，允许多人编辑
     */
    private boolean isInpatientCategoryDocument(Document doc) {
        String typeCode = doc.getDocumentTypeCode();
        // 无类型编码无法判分类，不放行
        if (!StringUtils.hasText(typeCode)) {
            return false;
        }
        Optional<DocumentType> typeOpt = documentManager.getDocumentTypeByCode(typeCode.trim());
        // 类型未配置或已删除：不按住院放开，避免误开门诊文书
        if (typeOpt.isEmpty()) {
            return false;
        }
        String category = typeOpt.get().getCategoryCode();
        // 未配分类时不猜住院，保持创建者闸门
        if (!StringUtils.hasText(category)) {
            return false;
        }
        String normalized = category.trim();
        // 住院病历分类（入院/出院/病程等）
        if (TemplateStationKind.INPATIENT.getCode().equalsIgnoreCase(normalized)) {
            return true;
        }
        // 手术病历挂在住院就诊下，住院医生站同样全员可改
        return "SURGERY".equalsIgnoreCase(normalized);
    }

    /**
     * 当前登录用户是否为创建者（无用户上下文时放行，供系统调用）。
     *
     * @param doc 已落库文书
     * @return true 可按创建者身份编辑
     */
    private boolean isEditableByCurrentUser(Document doc) {
        Long currentUserId = null;
        // 可选注入：部分系统调用没有登录用户
        if (currentUser != null) {
            currentUserId = currentUser.getCurrentUserId();
        }
        // 无登录上下文（系统任务）不拦编辑
        if (currentUserId == null) {
            return true;
        }
        Long createdBy = doc.getCreatedBy();
        // 历史数据无创建人时不拦
        if (createdBy == null) {
            return true;
        }
        return currentUserId.equals(createdBy);
    }

    /**
     * 是否处于不可直接改内容的归档相关态（待审/已归档/历史 locked）。
     *
     * @param doc 文书
     * @return true 表示须经编辑申请或不可改
     */
    private boolean isArchiveLockedStatus(Document doc) {
        String status = "";
        if (doc.getStatus() != null) {
            status = doc.getStatus().trim();
        }
        return DocumentManager.STATUS_ARCHIVED.equals(status)
                || DocumentManager.STATUS_PENDING_ARCHIVE.equals(status);
    }

    /**
     * 编辑闸门：待审/已归档不可改；住院侧多人可编，门诊仅创建者。
     *
     * @param doc 已落库文书
     */
    private void assertCanEditDocument(Document doc) {
        // 待审/已归档不可直接改内容；已归档修改走编辑申请
        if (isArchiveLockedStatus(doc)) {
            throw new ValidationException("文书已锁定或归档，不可修改");
        }
        if (allowsMultiUserEdit(doc)) {
            return;
        }
        if (!isEditableByCurrentUser(doc)) {
            throw new ValidationException("仅创建者可修改该文书");
        }
    }

    /**
     * 删除闸门：锁定/归档不可删；住院侧文书与多人可编辑同一口径，门诊仍仅创建者可删。
     *
     * @param doc 已落库文书
     */
    private void assertCanDeleteDocument(Document doc) {
        // 锁定/归档后任何人都不能删
        if (isArchiveLockedStatus(doc)) {
            throw new ValidationException("文书已锁定或归档，不可删除");
        }
        // 住院就诊/住院分类文书：删除也不限创建者
        if (allowsMultiUserEdit(doc)) {
            return;
        }
        // 门诊等其余类型：仍仅创建者可删
        if (!isEditableByCurrentUser(doc)) {
            throw new ValidationException("仅创建者可删除该文书");
        }
    }

    private DocumentSummaryDto toSummaryDto(Document doc) {
        return toSummaryDto(doc, new HashMap<>(), new HashMap<>());
    }

    private DocumentSummaryDto toSummaryDto(
            Document doc,
            Map<String, String> deptNameByEncounter,
            Map<Long, String> userNameById) {
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
        dto.setSourceTemplateId(doc.getSourceTemplateId());
        documentManager.getDocumentTypeByCode(doc.getDocumentTypeCode())
                .ifPresent(t -> dto.setDocumentTypeName(t.getName()));
        dto.setDeptName(resolveDeptName(doc.getEncounterId(), deptNameByEncounter));
        Long saverId = doc.getUpdatedBy();
        if (saverId == null) {
            saverId = doc.getCreatedBy();
        }
        dto.setSavedByName(resolveUserDisplayName(saverId, userNameById));
        // Asm：列表已加载 content，就地提取主诉，不额外查库
        dto.setChiefComplaint(
                documentStructureManager.extractPlainTextByDataItemName(doc.getContent(), "chiefComplaint"));
        return dto;
    }

    private String resolveDeptName(String encounterId, Map<String, String> cache) {
        if (!StringUtils.hasText(encounterId)) {
            return null;
        }
        String key = encounterId.trim();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        String deptName = null;
        try {
            Map<String, String> ctx = new HashMap<>();
            admissionContextLoader.contributeEncounter(ctx, key);
            deptName = firstNonBlank(ctx.get("encounter.deptName"), ctx.get("dept.name"));
        } catch (RuntimeException ignored) {
            // HIS 未就绪时列表仍可用
        }
        cache.put(key, deptName);
        return deptName;
    }

    private String resolveUserDisplayName(Long userId, Map<Long, String> cache) {
        if (userId == null) {
            return null;
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        String display = null;
        if (userRepository != null) {
            display = userRepository.findById(userId)
                    .map(this::formatUserDisplayName)
                    .orElse(null);
        }
        cache.put(userId, display);
        return display;
    }

    private String formatUserDisplayName(User user) {
        String name = firstNonBlank(user.getName(), user.getUserName());
        if (!StringUtils.hasText(name) && StringUtils.hasText(user.getSurname())) {
            name = user.getSurname().trim();
        }
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
