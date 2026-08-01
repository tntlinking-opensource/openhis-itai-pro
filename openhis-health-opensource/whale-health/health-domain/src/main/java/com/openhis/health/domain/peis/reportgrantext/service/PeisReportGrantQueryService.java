/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.reportgrantext.service;

import com.openhis.health.domain.peis.reportgrantext.entity.PeisReportGrantExt;
import com.openhis.health.domain.peis.reportgrantext.queries.model.GrantRowModel;
import com.openhis.health.domain.peis.reportgrantext.queries.query.GrantSearchQuery;
import com.openhis.health.domain.peis.reportgrantext.repository.PeisReportGrantExtRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.PeisGrantStatusConstants;
import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 报告发放扩展读侧（DM-PEIS-005；仅本聚合 Repository）。
 *
 * <p>患者/流水号等 enrich 由应用层经 DiagnosticReport / EncounterExt QueryService 组装。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisReportGrantQueryService {

    private final PeisReportGrantExtRepository repository;

    /**
     * 分页查询发放扩展行。
     *
     * @param query 查询条件
     * @return 分页读模型（不含 encounterId/患者，由 App  enrich）
     */
    public Page<GrantRowModel> searchPendingGrant(GrantSearchQuery query) {
        GrantSearchQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new GrantSearchQuery();
        }
        final GrantSearchQuery finalQuery = safeQuery;
        Pageable pageable = PageRequest.of(finalQuery.springPageIndex(), finalQuery.getBoundedPageSize());
        String statusCode = finalQuery.getGrantStatusCode();
        if (StringUtils.isEmpty(statusCode)) {
            statusCode = PeisGrantStatusConstants.PENDING;
        }
        final String grantStatus = statusCode;
        Specification<PeisReportGrantExt> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            preds.add(cb.equal(root.get("grantStatusEnum"), grantStatus));
            if (finalQuery.getOrgId() != null) {
                preds.add(cb.equal(root.get("orgId"), finalQuery.getOrgId()));
            }
            LocalDateTime start = finalQuery.getStartDate();
            if (start != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("grantTime"), start));
            }
            LocalDateTime end = finalQuery.getEndDate();
            if (end != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("grantTime"), end));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<PeisReportGrantExt> page = repository.findAll(spec, pageable);
        List<GrantRowModel> models = new ArrayList<>();
        for (PeisReportGrantExt row : page.getContent()) {
            models.add(toModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 按总检报告主键读取发放扩展。
     *
     * @param diagnosticReportId 总检报告主键
     * @return 读模型（可空）
     */
    public Optional<GrantRowModel> getByDiagnosticReportId(Long diagnosticReportId) {
        if (diagnosticReportId == null) {
            return Optional.empty();
        }
        return repository.findByDiagnosticReportIdAndIsDeletedFalse(diagnosticReportId).map(this::toModel);
    }

    private GrantRowModel toModel(PeisReportGrantExt entity) {
        GrantRowModel model = new GrantRowModel();
        model.setDiagnosticReportId(entity.getDiagnosticReportId());
        model.setGrantStatusCode(entity.getGrantStatusEnum());
        model.setGrantTime(entity.getGrantTime());
        model.setRecipientName(entity.getRecipientName());
        return model;
    }
}
