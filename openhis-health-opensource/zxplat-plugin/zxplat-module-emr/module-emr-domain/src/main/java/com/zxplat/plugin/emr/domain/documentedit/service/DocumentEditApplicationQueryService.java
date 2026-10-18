/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.documentedit.service;

import com.zxplat.plugin.emr.domain.enums.DocumentEditApplicationStatus;
import com.zxplat.plugin.emr.domain.documentedit.entity.DocumentEditApplication;
import com.zxplat.plugin.emr.domain.documentedit.queries.DocumentEditApplicationPageQuery;
import com.zxplat.plugin.emr.domain.documentedit.queries.model.DocumentEditApplicationQueryModel;
import com.zxplat.plugin.emr.domain.documentedit.repository.DocumentEditApplicationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 文书编辑申请读侧（DM-EMR-002）。
 *
 * <p>仅本聚合 {@link DocumentEditApplicationRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentEditApplicationQueryService {

    private final DocumentEditApplicationRepository documentEditApplicationRepository;

    /**
     * 按 id 加载。
     *
     * @param id 主键
     * @return 申请
     */
    public Optional<DocumentEditApplication> getById(Long id) {
        return documentEditApplicationRepository.findByIdAndIsDeletedFalse(id);
    }

    /**
     * 同文书在途申请（DRAFTING / PENDING_REVIEW）。
     *
     * @param tenantId 租户
     * @param documentId 文书
     * @return 在途申请（至多一条）
     */
    public Optional<DocumentEditApplication> findActiveByDocument(Long tenantId, Long documentId) {
        List<DocumentEditApplication> list = documentEditApplicationRepository
                .findByTenantIdAndDocumentIdAndStatusEnumInAndIsDeletedFalse(
                        tenantId,
                        documentId,
                        List.of(
                                DocumentEditApplicationStatus.DRAFTING.getCode(),
                                DocumentEditApplicationStatus.PENDING_REVIEW.getCode()));
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }

    /**
     * 按文书历史列表（申请时间倒序）。
     *
     * @param documentId 文书
     * @return 历史
     */
    public List<DocumentEditApplication> listHistoryByDocument(Long documentId) {
        return documentEditApplicationRepository.findByDocumentIdAndIsDeletedFalseOrderByAppliedAtDesc(documentId);
    }

    /**
     * 审核列表分页（不含双 JSON）。
     *
     * @param query 查询
     * @return 分页
     */
    public Page<DocumentEditApplicationQueryModel> pageForReview(DocumentEditApplicationPageQuery query) {
        Specification<DocumentEditApplication> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            }
            String status = trimToNull(query.getStatusEnum());
            if (status != null) {
                predicates.add(cb.equal(root.get("statusEnum"), status));
            }
            if (query.getDocumentId() != null) {
                predicates.add(cb.equal(root.get("documentId"), query.getDocumentId()));
            }
            String encounterClass = trimToNull(query.getEncounterClassCode());
            if (encounterClass != null) {
                predicates.add(cb.equal(root.get("encounterClassCode"), encounterClass));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int pageIndex = 0;
        if (query.getPageIndex() != null && query.getPageIndex() >= 1) {
            pageIndex = query.getPageIndex() - 1;
        }
        int pageSize = 20;
        if (query.getPageSize() != null && query.getPageSize() >= 1) {
            pageSize = query.getPageSize();
        }
        PageRequest pageable = PageRequest.of(
                pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "submittedAt", "appliedAt"));
        return documentEditApplicationRepository.findAll(spec, pageable).map(this::toListModel);
    }

    /**
     * 实体转列表 QueryModel（不含双 JSON）。
     *
     * @param entity 实体
     * @return QueryModel
     */
    private DocumentEditApplicationQueryModel toListModel(DocumentEditApplication entity) {
        DocumentEditApplicationQueryModel model = new DocumentEditApplicationQueryModel();
        model.setId(entity.getId());
        model.setDocumentId(entity.getDocumentId());
        model.setEncounterId(entity.getEncounterId());
        model.setOrganizationId(entity.getOrganizationId());
        model.setEncounterClassCode(entity.getEncounterClassCode());
        model.setStatusEnum(entity.getStatusEnum());
        model.setApplyReason(entity.getApplyReason());
        model.setRejectReason(entity.getRejectReason());
        model.setFromVersion(entity.getFromVersion());
        model.setToVersion(entity.getToVersion());
        model.setApplicantId(entity.getApplicantId());
        model.setAppliedAt(entity.getAppliedAt());
        model.setSubmittedAt(entity.getSubmittedAt());
        model.setReviewerId(entity.getReviewerId());
        model.setReviewedAt(entity.getReviewedAt());
        return model;
    }
}
