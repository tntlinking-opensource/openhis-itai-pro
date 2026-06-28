/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.borrowrequest.service;

import com.openhis.health.domain.mrms.borrowrequest.entity.BorrowRequest;
import com.openhis.health.domain.mrms.borrowrequest.queries.BorrowRequestPageQuery;
import com.openhis.health.domain.mrms.borrowrequest.repository.BorrowRequestRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 借阅申请读侧查询服务（仅本聚合 {@link BorrowRequestRepository}）。
 */
@Service
@RequiredArgsConstructor
public class BorrowRequestQueryService {

    private final BorrowRequestRepository borrowRequestRepository;

    /**
     * 分页检索借阅申请。
     *
     * @param query    过滤条件
     * @param pageable 分页
     * @return 借阅申请分页
     */
    public Page<BorrowRequest> queryPage(BorrowRequestPageQuery query, Pageable pageable) {
        Specification<BorrowRequest> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            // 可选：按状态过滤
            if (trimToNull(query.getStatusCode()) != null) {
                predicates.add(cb.equal(root.get("statusCode"), query.getStatusCode()));
            }
            // 可选：按病案主档过滤
            if (query.getCaseRecordId() != null) {
                predicates.add(cb.equal(root.get("caseRecordId"), query.getCaseRecordId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return borrowRequestRepository.findAll(spec, pageable);
    }
}
