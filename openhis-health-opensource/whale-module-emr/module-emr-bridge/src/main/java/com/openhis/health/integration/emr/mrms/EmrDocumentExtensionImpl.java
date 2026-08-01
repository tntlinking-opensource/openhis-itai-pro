/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.emr.mrms;

import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.constants.EmrDocumentTypeCodes;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentListExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrOutpatientDocumentPageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentQueryExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentItemExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentPageExtensionOut;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.emr.domain.document.access.EmrPdfRenderCommand;
import com.openhis.whale.emr.domain.document.access.EmrPdfRenderResult;
import com.openhis.whale.emr.domain.document.access.EmrPdfRenderer;
import com.openhis.whale.emr.domain.document.entity.Document;
import com.openhis.whale.emr.domain.document.entity.DocumentType;
import com.openhis.whale.emr.domain.document.repository.DocumentRepository;
import com.openhis.whale.emr.domain.document.repository.DocumentTypeRepository;
import com.openhis.whale.emr.domain.document.service.DocumentManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * {@link EmrDocumentExtension} MRMS 侧实现。
 *
 * <p>需 EMR classpath 且 {@code openhis.modules.emr.enabled=true}；PDF 导出经 domain
 * {@link EmrPdfRenderer} 端口（application 装配实现）。读就诊 patientId 经
 * {@link EncounterQueryService}（B.6.4）。</p>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.document.entity.Document")
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
public class EmrDocumentExtensionImpl implements EmrDocumentExtension {

    private final DocumentRepository documentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentManager documentManager;
    private final EncounterQueryService encounterQueryService;

    @Autowired(required = false)
    private EmrPdfRenderer emrPdfRenderer;

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
     * @param query 患者与就诊主键
     * @return 文书快照列表；主键缺失时空列表
     */
    @Override
    public List<EmrDocumentQueryExtensionOut> listByEncounter(EmrDocumentListExtensionIn query) {
        // 主键缺失时不查库，返回空列表
        if (query == null || query.getPatientId() == null || query.getEncounterId() == null) {
            return List.of();
        }
        List<Document> documents = loadDocuments(query.getPatientId(), query.getEncounterId());
        Map<String, String> typeNames = loadDocumentTypeNames(documents);
        List<EmrDocumentQueryExtensionOut> items = new ArrayList<>();
        for (Document document : documents) {
            items.add(toSnapshot(document, typeNames));
        }
        items.sort(Comparator
                .comparing(EmrDocumentQueryExtensionOut::getDocumentTypeCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(EmrDocumentQueryExtensionOut::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
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
        if (emrPdfRenderer == null) {
            // 渲染器未装配阻断导出
            throw new BusinessException("MRMS_EMR_PDF_UNAVAILABLE", "EMR PDF 渲染器未启用，请使用 with-emr 启动");
        }
        EmrPdfRenderCommand command = new EmrPdfRenderCommand();
        command.setDocumentId(String.valueOf(documentId));
        command.setPreviewMode(Boolean.TRUE);
        command.setPatientId(document.getPatientId());
        command.setEncounterId(document.getEncounterId());
        command.setDocumentTitle(document.getTitle());
        String content = document.getContent();
        if (trimToNull(content) == null) {
            // 空内容不可生成 PDF
            throw new BusinessException("EMR_PDF_EMPTY", "文书内容为空");
        }
        EmrPdfRenderResult result = emrPdfRenderer.render(content, command);
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
     * 门诊文书跨患者分页（按类型 + 可选患者/就诊/状态）。
     *
     * @param query 过滤与分页；{@code null} 时返回空页
     * @return 分页摘要（不含正文）
     */
    @Override
    public EmrOutpatientDocumentPageExtensionOut queryOutpatientDocumentPage(
            EmrOutpatientDocumentPageExtensionIn query) {
        EmrOutpatientDocumentPageExtensionOut out = new EmrOutpatientDocumentPageExtensionOut();
        // Gate：入参为空时不查库
        if (query == null) {
            out.setContent(List.of());
            out.setTotalCount(0L);
            return out;
        }
        int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        if (pageSize > 200) {
            pageSize = 200;
        }
        out.setPageNo(pageNo);
        out.setPageSize(pageSize);
        String typeCode = trimToNull(query.getDocumentTypeCode());
        if (typeCode == null) {
            typeCode = EmrDocumentTypeCodes.OUTPATIENT_RECORD;
        }
        String status = trimToNull(query.getStatus());
        List<String> patientKeys = toStringIdList(query.getPatientIds());
        List<String> encounterKeys = toStringIdList(query.getEncounterIds());
        // Cmd：Specification 分页（患者/就诊同时有值时 OR）
        String finalTypeCode = typeCode;
        Specification<Document> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("documentTypeCode"), finalTypeCode));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (!patientKeys.isEmpty() && !encounterKeys.isEmpty()) {
                predicates.add(cb.or(
                        root.get("patientId").in(patientKeys),
                        root.get("encounterId").in(encounterKeys)));
            } else if (!patientKeys.isEmpty()) {
                predicates.add(root.get("patientId").in(patientKeys));
            } else if (!encounterKeys.isEmpty()) {
                predicates.add(root.get("encounterId").in(encounterKeys));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        PageRequest pageable = PageRequest.of(
                pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Page<Document> page = documentRepository.findAll(spec, pageable);
        Map<String, String> typeNames = loadDocumentTypeNames(page.getContent());
        List<EmrOutpatientDocumentItemExtensionOut> items = new ArrayList<>();
        for (Document document : page.getContent()) {
            items.add(toOutpatientItem(document, typeNames));
        }
        // Asm：分页壳
        out.setContent(items);
        out.setTotalCount(page.getTotalElements());
        return out;
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
    private EmrDocumentQueryExtensionOut toSnapshot(Document document, Map<String, String> typeNames) {
        EmrDocumentQueryExtensionOut item = new EmrDocumentQueryExtensionOut();
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
     * 文书实体转门诊检索行。
     *
     * @param document 文书实体
     * @param typeNames 类型 code → 展示名
     * @return 门诊检索行
     */
    private EmrOutpatientDocumentItemExtensionOut toOutpatientItem(
            Document document, Map<String, String> typeNames) {
        EmrOutpatientDocumentItemExtensionOut item = new EmrOutpatientDocumentItemExtensionOut();
        item.setDocumentId(document.getId());
        item.setPatientId(document.getPatientId());
        item.setEncounterId(document.getEncounterId());
        item.setTitle(document.getTitle());
        item.setDocumentTypeCode(document.getDocumentTypeCode());
        item.setDocumentTypeName(typeNames.get(document.getDocumentTypeCode()));
        item.setStatusCode(document.getStatus());
        item.setCreatedAt(document.getCreatedAt());
        item.setUpdatedAt(document.getUpdatedAt());
        item.setCreatedBy(document.getCreatedBy());
        item.setUpdatedBy(document.getUpdatedBy());
        item.setPrintable(isSubmittedOrBeyond(document.getStatus()));
        return item;
    }

    /**
     * Long 主键集合转 emr_document 存库用的字符串 ID 列表。
     *
     * @param ids 主键集合
     * @return 非空字符串 ID；空入参返回空列表
     */
    private static List<String> toStringIdList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                keys.add(String.valueOf(id));
            }
        }
        return keys;
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
