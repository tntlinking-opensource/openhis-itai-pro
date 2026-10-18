/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.service;

import com.zxplat.plugin.emr.domain.document.entity.Document;
import com.zxplat.plugin.emr.domain.document.queries.DocumentArchiveApprovalPageQuery;
import com.zxplat.plugin.emr.domain.document.repository.DocumentRepository;
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

/**
 * 文书读侧（DM-EMR-001 §6.2）。
 *
 * <p>仅本聚合 {@link DocumentRepository}；不跨查 Encounter/病案。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentQueryService {

    private final DocumentRepository documentRepository;

    /**
     * 按主键加载未软删文书。
     *
     * @param id 文书主键
     * @return 文书；不存在则 empty
     */
    public Optional<Document> getById(Long id) {
        return documentRepository.findByIdAndIsDeletedFalse(id);
    }

    /**
     * 某就诊文书列表（更新时间倒序）。
     *
     * @param patientId 患者标识
     * @param encounterId 就诊标识
     * @return 文书列表；无则空列表
     */
    public List<Document> listByEncounter(String patientId, String encounterId) {
        return documentRepository.findByPatientIdAndEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                patientId, encounterId);
    }

    /**
     * 转科隔离：同就诊且书写科室匹配的文书列表。
     *
     * @param patientId 患者
     * @param encounterId 就诊
     * @param organizationId 书写科室；空则退化为 listByEncounter
     * @return 文书列表
     */
    public List<Document> listByOrganizationAndEncounter(
            String patientId, String encounterId, Long organizationId) {
        List<Document> all = listByEncounter(patientId, encounterId);
        // 未指定科室：返回该就诊全部文书
        if (organizationId == null) {
            return all;
        }
        List<Document> filtered = new ArrayList<>();
        for (Document doc : all) {
            // 仅保留书写时科室一致的文书（转科隔离）
            if (organizationId.equals(doc.getOrganizationId())) {
                filtered.add(doc);
            }
        }
        return filtered;
    }

    /**
     * 待归档审批分页：status=PENDING_ARCHIVE。
     *
     * @param query 租户/就诊集合/科室/分页
     * @return 分页文书实体（列表场景可裁剪 content）
     */
    public Page<Document> pageForArchiveApproval(DocumentArchiveApprovalPageQuery query) {
        Specification<Document> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.equal(root.get("status"), DocumentManager.STATUS_PENDING_ARCHIVE));
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            }
            List<String> encounterIds = query.getEncounterIds();
            // 应用层按就诊 class 预筛后的 encounter IN
            if (encounterIds != null && !encounterIds.isEmpty()) {
                predicates.add(root.get("encounterId").in(encounterIds));
            }
            if (query.getOrganizationId() != null) {
                predicates.add(cb.equal(root.get("organizationId"), query.getOrganizationId()));
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
                pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "archiveSubmittedAt", "updatedAt"));
        return documentRepository.findAll(spec, pageable);
    }
}
