/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.volume.support.emr;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.integration.emr.EmrDocumentExtension;
import com.openhis.health.integration.emr.dto.EmrDocumentSnapshot;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.emr.application.document.request.GeneratePdfRequest;
import com.openhis.whale.emr.application.pdf.OpenPdfRenderer;
import com.openhis.whale.emr.application.pdf.model.PdfRenderResult;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.entity.DocumentType;
import com.openhis.whale.emr.domain.repository.DocumentRepository;
import com.openhis.whale.emr.domain.repository.DocumentTypeRepository;
import com.openhis.whale.emr.domain.service.DocumentManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link EmrDocumentExtension} MRMS 侧实现（volume/support/emr）。
 *
 * <p>需 EMR classpath 且 {@code openhis.modules.emr.enabled=true}；PDF 导出依赖 emr-application
 * {@link OpenPdfRenderer}。读就诊 patientId 经 {@link EncounterQueryService}（B.6.4）。</p>
 */
@Slf4j
@Component
@Primary
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.entity.Document")
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
public class EmrDocumentExtensionImpl implements EmrDocumentExtension {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentManager documentManager;
    private final EncounterQueryService encounterQueryService;
    private final OpenPdfRenderer openPdfRenderer;

    /**
     * 构造 EMR Extension 实现；PDF 渲染器可选注入（未装配时 exportPdf 阻断）。
     *
     * @param documentRepository 文书仓储
     * @param documentTypeRepository 文书类型仓储
     * @param documentManager 文书写服务
     * @param encounterQueryService 就诊 QS（B.6.4 读 patientId）
     * @param openPdfRenderer OpenPDF 渲染器，可为 null
     */
    public EmrDocumentExtensionImpl(
            DocumentRepository documentRepository,
            DocumentTypeRepository documentTypeRepository,
            DocumentManager documentManager,
            EncounterQueryService encounterQueryService,
            @Autowired(required = false) OpenPdfRenderer openPdfRenderer) {
        this.documentRepository = documentRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentManager = documentManager;
        this.encounterQueryService = encounterQueryService;
        this.openPdfRenderer = openPdfRenderer;
    }

    /**
     * EMR 模块已启用且本 Bean 已注册。
     *
     * @return 恒为 true
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 列出就诊下全部文书快照，按类型码与创建时间排序。
     *
     * @param patientId 患者主键
     * @param encounterId 就诊主键
     * @return 文书快照列表；主键缺失时空列表
     */
    @Override
    public List<EmrDocumentSnapshot> listByEncounter(Long patientId, Long encounterId) {
        // 主键缺失时不查库，返回空列表
        if (patientId == null || encounterId == null) {
            return List.of();
        }
        List<Document> documents = loadDocuments(patientId, encounterId);
        Map<String, String> typeNames = loadDocumentTypeNames(documents);
        List<EmrDocumentSnapshot> items = new ArrayList<>();
        for (Document document : documents) {
            items.add(toSnapshot(document, typeNames));
        }
        items.sort(Comparator
                .comparing(EmrDocumentSnapshot::getDocumentTypeCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(EmrDocumentSnapshot::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    /**
     * 判断就诊下是否存在已提交及以上状态的指定类型文书。
     *
     * @param patientId 患者主键
     * @param encounterId 就诊主键
     * @param documentTypeCode 文书类型 code
     * @return 存在则 true
     */
    @Override
    public boolean hasSubmittedDocument(Long patientId, Long encounterId, String documentTypeCode) {
        // 类型 code 为空时无法匹配文书
        if (trimToNull(documentTypeCode) == null) {
            return false;
        }
        String patientKey = String.valueOf(patientId);
        String encounterKey = String.valueOf(encounterId);
        List<Document> documents = documentRepository.findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
                patientKey, encounterKey, documentTypeCode);
        for (Document document : documents) {
            if (isSubmittedOrBeyond(document.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 列出就诊下已提交及以上状态的文书类型 code 集合（质控必写文书校验用）。
     *
     * @param patientId 患者主键
     * @param encounterId 就诊主键
     * @return 类型 code 集合
     */
    @Override
    public Set<String> listSubmittedDocumentTypeCodes(Long patientId, Long encounterId) {
        Set<String> codes = new HashSet<>();
        for (Document document : loadDocuments(patientId, encounterId)) {
            if (isSubmittedOrBeyond(document.getStatus()) && trimToNull(document.getDocumentTypeCode()) != null) {
                codes.add(document.getDocumentTypeCode());
            }
        }
        return codes;
    }

    /**
     * 导出单份文书 PDF 字节流（预览模式）。
     *
     * @param documentId 文书主键
     * @return PDF 字节流
     */
    @Override
    public byte[] exportPdf(Long documentId) {
        Document document = documentManager.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("文书", documentId));
        if (openPdfRenderer == null) {
            // 渲染器未装配阻断导出
            throw new BusinessException("MRMS_EMR_PDF_UNAVAILABLE", "EMR PDF 渲染器未启用，请使用 with-emr 启动");
        }
        GeneratePdfRequest request = new GeneratePdfRequest();
        request.setDocumentId(String.valueOf(documentId));
        request.setPreviewMode(Boolean.TRUE);
        request.setPatientId(document.getPatientId());
        request.setEncounterId(document.getEncounterId());
        request.setDocumentTitle(document.getTitle());
        String content = document.getContent();
        if (trimToNull(content) == null) {
            // 空内容不可生成 PDF
            throw new BusinessException("EMR_PDF_EMPTY", "文书内容为空");
        }
        PdfRenderResult result = openPdfRenderer.render(content, request);
        return result.getPdfBytes();
    }

    /**
     * 病案归档时将就诊下未锁定文书批量置为 LOCKED。
     *
     * @param encounterId 就诊主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockAllByEncounter(Long encounterId) {
        if (encounterId == null) {
            return;
        }
        Long patientId = encounterQueryService.findOptionalEncounterEntityById(encounterId)
                .map(row -> row.getPatientId())
                .orElse(null);
        if (patientId == null) {
            // 就诊无 patientId 时跳过 EMR 锁定
            log.warn("归档锁定 EMR 文书时未找到就诊 patientId encounterId={}", encounterId);
            return;
        }
        for (Document document : loadDocuments(patientId, encounterId)) {
            String status = document.getStatus();
            // 已锁定/已归档文书不再变更状态
            if (DocumentManager.STATUS_LOCKED.equals(status) || DocumentManager.STATUS_ARCHIVED.equals(status)) {
                continue;
            }
            documentManager.updateStatus(document.getId(), DocumentManager.STATUS_LOCKED);
        }
    }

    /**
     * 病案解档时将 LOCKED 文书恢复为 SUBMITTED。
     *
     * @param encounterId 就诊主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockAllByEncounter(Long encounterId) {
        if (encounterId == null) {
            return;
        }
        Long patientId = encounterQueryService.findOptionalEncounterEntityById(encounterId)
                .map(row -> row.getPatientId())
                .orElse(null);
        if (patientId == null) {
            // 就诊无 patientId 时跳过 EMR 解锁
            return;
        }
        for (Document document : loadDocuments(patientId, encounterId)) {
            if (DocumentManager.STATUS_LOCKED.equals(document.getStatus())) {
                documentManager.updateStatus(document.getId(), DocumentManager.STATUS_SUBMITTED);
            }
        }
    }

    /**
     * 按 patientId+encounterId 加载文书；patientId 维度无结果时降级仅按 encounterId 查。
     *
     * @param patientId 患者主键
     * @param encounterId 就诊主键
     * @return 文书实体列表
     */
    private List<Document> loadDocuments(Long patientId, Long encounterId) {
        String patientKey = String.valueOf(patientId);
        String encounterKey = String.valueOf(encounterId);
        List<Document> documents = documentRepository.findByPatientIdAndEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                patientKey, encounterKey);
        if (!documents.isEmpty()) {
            return documents;
        }
        // patient 维度无文书时降级仅按 encounterId 查
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("encounterId"), encounterKey));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return documentRepository.findAll(spec);
    }

    /**
     * 批量加载文书类型展示名（按 code 去重）。
     *
     * @param documents 文书实体列表
     * @return 类型 code → 展示名
     */
    private Map<String, String> loadDocumentTypeNames(List<Document> documents) {
        Map<String, String> names = new HashMap<>();
        for (Document document : documents) {
            String code = trimToNull(document.getDocumentTypeCode());
            if (code == null || names.containsKey(code)) {
                continue;
            }
            documentTypeRepository.findByCodeAndIsDeletedFalse(code)
                    .map(DocumentType::getName)
                    .ifPresent(name -> names.put(code, name));
        }
        return names;
    }

    /**
     * 文书实体转 MRMS 快照 DTO。
     *
     * @param document 文书实体
     * @param typeNames 类型 code → 展示名
     * @return 快照 DTO
     */
    private EmrDocumentSnapshot toSnapshot(Document document, Map<String, String> typeNames) {
        EmrDocumentSnapshot item = new EmrDocumentSnapshot();
        item.setDocumentId(document.getId());
        item.setTitle(document.getTitle());
        item.setDocumentTypeCode(document.getDocumentTypeCode());
        item.setDocumentTypeName(typeNames.get(document.getDocumentTypeCode()));
        item.setStatusCode(document.getStatus());
        item.setCreatedAt(document.getCreatedAt());
        item.setPrintable(isSubmittedOrBeyond(document.getStatus()));
        return item;
    }

    /**
     * 判断文书是否已达可打印状态（已提交/锁定/归档）。
     *
     * @param status 文书状态 code
     * @return 可打印则 true
     */
    private boolean isSubmittedOrBeyond(String status) {
        return DocumentManager.STATUS_SUBMITTED.equals(status)
                || DocumentManager.STATUS_LOCKED.equals(status)
                || DocumentManager.STATUS_ARCHIVED.equals(status);
    }
}
