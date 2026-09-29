/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.homepageedit.service;

import com.openhis.health.domain.mrms.homepageedit.entity.HomepageEditApplication;
import com.openhis.health.domain.mrms.homepageedit.queries.HomepageEditApplicationPageQuery;
import com.openhis.health.domain.mrms.homepageedit.queries.model.HomepageEditApplicationQueryModel;
import com.openhis.health.domain.mrms.homepageedit.repository.HomepageEditApplicationRepository;
import com.openhis.health.domain.share.enums.HomepageEditApplicationStatus;
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

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 首页编辑申请读侧（仅本聚合 Repository）。
 */
@Service
@RequiredArgsConstructor
public class HomepageEditApplicationQueryService {

    private final HomepageEditApplicationRepository homepageEditApplicationRepository;

    /**
     * 按 id 加载。
     *
     * @param id 主键
     * @return 申请
     */
    public Optional<HomepageEditApplication> findById(Long id) {
        return homepageEditApplicationRepository.findByIdAndIsDeletedFalse(id);
    }

    /**
     * 同病案在途申请。
     *
     * @param tenantId 租户
     * @param caseRecordId 病案
     * @return 在途申请（至多一条）
     */
    public Optional<HomepageEditApplication> findActiveByCaseRecord(Long tenantId, Long caseRecordId) {
        List<HomepageEditApplication> list = homepageEditApplicationRepository
                .findByTenantIdAndCaseRecordIdAndStatusEnumInAndIsDeletedFalse(
                        tenantId,
                        caseRecordId,
                        List.of(
                                HomepageEditApplicationStatus.DRAFTING.getCode(),
                                HomepageEditApplicationStatus.PENDING_REVIEW.getCode()));
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }

    /**
     * 按病案历史列表。
     *
     * @param caseRecordId 病案
     * @return 历史
     */
    public List<HomepageEditApplication> listHistoryByCaseRecord(Long caseRecordId) {
        return homepageEditApplicationRepository.findByCaseRecordIdAndIsDeletedFalseOrderByAppliedAtDesc(caseRecordId);
    }

    /**
     * 审核列表分页（列表不含双 JSON，由 QueryModel 裁剪）。
     *
     * @param query 查询
     * @return 分页
     */
    public Page<HomepageEditApplicationQueryModel> pageForReview(HomepageEditApplicationPageQuery query) {
        Specification<HomepageEditApplication> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            }
            String status = trimToNull(query.getStatusEnum());
            if (status != null) {
                predicates.add(cb.equal(root.get("statusEnum"), status));
            }
            if (query.getCaseRecordId() != null) {
                predicates.add(cb.equal(root.get("caseRecordId"), query.getCaseRecordId()));
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
        PageRequest pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "submittedAt", "appliedAt"));
        return homepageEditApplicationRepository.findAll(spec, pageable).map(this::toListModel);
    }

    /**
     * 实体转列表 QueryModel（不含双 JSON）。
     *
     * @param entity 实体
     * @return QueryModel
     */
    private HomepageEditApplicationQueryModel toListModel(HomepageEditApplication entity) {
        HomepageEditApplicationQueryModel model = new HomepageEditApplicationQueryModel();
        model.setId(entity.getId());
        model.setCaseRecordId(entity.getCaseRecordId());
        model.setEncounterId(entity.getEncounterId());
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
