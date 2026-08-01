/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.invoke.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.invoke.entity.YbInvokeLog;
import com.openhis.chs.domain.invoke.queries.InvokeLogPageQuery;
import com.openhis.chs.domain.invoke.repository.YbInvokeLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** {@code yb_invoke_log} 分页查询 */
@Service
@RequiredArgsConstructor
public class YbInvokeLogPageQueryService {

    private static final String SUCCESS_INFCODE = "0";

    private final YbInvokeLogRepository invokeLogRepository;

    /**
     * 按条件分页查询未删除调用日志（创建时间倒序）。
     *
     * @param tenantId 租户 ID
     * @param query    筛选条件；调用方保证时间窗已校验
     * @return 分页结果
     */
    public Page<YbInvokeLog> page(Long tenantId, InvokeLogPageQuery query) {
        InvokeLogPageQuery safe = query == null ? new InvokeLogPageQuery() : query;
        int pageNo = safe.getPageNo() != null && safe.getPageNo() > 0 ? safe.getPageNo() : 1;
        int pageSize = safe.getPageSize() != null && safe.getPageSize() > 0 ? safe.getPageSize() : 20;
        Pageable pageable = PageRequest.of(
            Math.max(pageNo - 1, 0),
            Math.min(Math.max(pageSize, 1), 200),
            Sort.by(Sort.Order.desc("createdAt")));

        Specification<YbInvokeLog> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (safe.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), safe.getCreatedFrom()));
            }
            if (safe.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), safe.getCreatedTo()));
            }
            String infno = trimToNull(safe.getInfno());
            if (infno != null) {
                predicates.add(cb.equal(root.get("infno"), infno));
            }
            if (Boolean.TRUE.equals(safe.getSuccess())) {
                predicates.add(cb.equal(root.get("infcode"), SUCCESS_INFCODE));
            } else if (Boolean.FALSE.equals(safe.getSuccess())) {
                predicates.add(cb.or(
                    cb.isNull(root.get("infcode")),
                    cb.notEqual(root.get("infcode"), SUCCESS_INFCODE)));
            }
            String msgid = trimToNull(safe.getMsgid());
            if (msgid != null) {
                predicates.add(cb.equal(root.get("msgid"), msgid));
            }
            String contractBusNo = trimToNull(safe.getContractBusNo());
            if (contractBusNo != null) {
                predicates.add(cb.equal(root.get("contractBusNo"), contractBusNo));
            }
            if (safe.getEncounterId() != null) {
                predicates.add(cb.equal(root.get("encounterId"), safe.getEncounterId()));
            } else if (safe.getEncounterIds() != null && !safe.getEncounterIds().isEmpty()) {
                predicates.add(root.get("encounterId").in(safe.getEncounterIds()));
            }
            String chrgBchno = trimToNull(safe.getChrgBchno());
            if (chrgBchno != null) {
                predicates.add(cb.equal(root.get("chrgBchno"), chrgBchno));
            }
            if (safe.getPaymentDraftId() != null) {
                predicates.add(cb.equal(root.get("paymentDraftId"), safe.getPaymentDraftId()));
            }
            if (safe.getSettlementRecordId() != null) {
                predicates.add(cb.equal(root.get("settlementRecordId"), safe.getSettlementRecordId()));
            }
            String errKw = trimToNull(safe.getErrMsgKeyword());
            if (errKw != null) {
                predicates.add(cb.like(root.get("errMsg"), "%" + errKw + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return invokeLogRepository.findAll(spec, pageable);
    }
}
