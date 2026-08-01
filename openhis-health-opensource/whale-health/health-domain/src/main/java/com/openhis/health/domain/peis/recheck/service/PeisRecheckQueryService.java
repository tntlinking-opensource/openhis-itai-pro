/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.recheck.service;

import com.openhis.health.domain.peis.recheck.entity.PeisRecheckTask;
import com.openhis.health.domain.peis.recheck.queries.model.RecheckTaskRowModel;
import com.openhis.health.domain.peis.recheck.queries.query.RecheckTaskQuery;
import com.openhis.health.domain.peis.recheck.repository.PeisRecheckTaskRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.PeisRecheckTaskStatusConstants;
import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 复查任务读侧（仅本聚合 Repository）。
 */
@Service
@RequiredArgsConstructor
public class PeisRecheckQueryService {

    private static final List<String> OPEN_STATUSES = Arrays.asList(
        PeisRecheckTaskStatusConstants.REQUESTED,
        PeisRecheckTaskStatusConstants.IN_PROGRESS);

    private final PeisRecheckTaskRepository repository;

    /**
     * 分页查询复查任务。
     *
     * @param query 查询条件
     * @return 分页读模型
     */
    public Page<RecheckTaskRowModel> searchRecheckTasks(RecheckTaskQuery query) {
        RecheckTaskQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new RecheckTaskQuery();
        }
        final RecheckTaskQuery finalQuery = safeQuery;
        Pageable pageable = PageRequest.of(finalQuery.springPageIndex(), finalQuery.getBoundedPageSize());
        Specification<PeisRecheckTask> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (finalQuery.getOrgId() != null) {
                preds.add(cb.equal(root.get("orgId"), finalQuery.getOrgId()));
            }
            if (StringUtils.isNotEmpty(finalQuery.getStatusCode())) {
                preds.add(cb.equal(root.get("statusEnum"), finalQuery.getStatusCode().trim()));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Page<PeisRecheckTask> page = repository.findAll(spec, pageable);
        List<RecheckTaskRowModel> models = new ArrayList<>();
        for (PeisRecheckTask row : page.getContent()) {
            models.add(toModel(row));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 按主键读取复查任务。
     *
     * @param taskId 任务主键
     * @return 读模型（可空）
     */
    public Optional<RecheckTaskRowModel> getById(Long taskId) {
        if (taskId == null) {
            return Optional.empty();
        }
        return repository.findById(taskId)
            .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
            .map(this::toModel);
    }

    /**
     * 是否存在未关闭复查任务。
     *
     * @param encounterId 就诊主键
     * @return 是否存在
     */
    public boolean hasOpenRecheckTask(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        return repository.existsByEncounterIdAndStatusEnumInAndIsDeletedFalse(encounterId, OPEN_STATUSES);
    }

    private RecheckTaskRowModel toModel(PeisRecheckTask entity) {
        RecheckTaskRowModel model = new RecheckTaskRowModel();
        model.setTaskId(entity.getId());
        model.setEncounterId(entity.getEncounterId());
        model.setPatientId(entity.getPatientId());
        model.setStatusCode(entity.getStatusEnum());
        model.setReason(entity.getReason());
        model.setDiagnosticReportId(entity.getDiagnosticReportId());
        model.setCreatedAt(entity.getCreatedAt());
        return model;
    }
}
