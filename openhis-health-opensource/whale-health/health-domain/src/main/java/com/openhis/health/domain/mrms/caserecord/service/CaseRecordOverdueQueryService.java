/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.service;

import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.repository.CaseRecordRepository;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 病案逾期扫描读侧（定时任务/通知用）。
 *
 * <p>仅注入本聚合 {@link CaseRecordRepository}；按出院时间与状态条件下推 DB。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordOverdueQueryService {

    private final CaseRecordRepository caseRecordRepository;

    /**
     * 出院后超过截止日仍未提交的病案（draft / returned）。
     *
     * @param dischargeBefore 出院时间上限（含）；通常为「当前日 - 提交期限天数」
     * @return 逾期未提交主档列表
     */
    public List<CaseRecord> findSubmitOverdue(LocalDateTime dischargeBefore) {
        Specification<CaseRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.isNotNull(root.get("dscgTime")));
            predicates.add(cb.lessThanOrEqualTo(root.get("dscgTime"), dischargeBefore));
            // 仅医生侧未提交态计入逾期提交
            predicates.add(root.get("statusEnum").in(
                    CaseRecordStatus.DRAFT,
                    CaseRecordStatus.RETURNED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRecordRepository.findAll(spec);
    }

    /**
     * 出院后超过截止日仍未归档的病案（非 archived 均视为未归档完成）。
     *
     * @param dischargeBefore 出院时间上限（含）
     * @return 逾期未归档主档列表
     */
    public List<CaseRecord> findArchiveOverdue(LocalDateTime dischargeBefore) {
        Specification<CaseRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), false));
            predicates.add(cb.isNotNull(root.get("dscgTime")));
            predicates.add(cb.lessThanOrEqualTo(root.get("dscgTime"), dischargeBefore));
            // 未归档完成（非 archived）均视为逾期归档
            predicates.add(cb.notEqual(root.get("statusEnum"), CaseRecordStatus.ARCHIVED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return caseRecordRepository.findAll(spec);
    }
}
