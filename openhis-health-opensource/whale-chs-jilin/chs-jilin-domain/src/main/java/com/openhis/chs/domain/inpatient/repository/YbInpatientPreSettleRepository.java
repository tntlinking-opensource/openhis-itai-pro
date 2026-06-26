/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.repository;

import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 住院医保预结算快照仓储。 */
public interface YbInpatientPreSettleRepository extends JpaRepository<YbInpatientPreSettle, Long> {

    Optional<YbInpatientPreSettle> findFirstByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    Optional<YbInpatientPreSettle> findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long paymentDraftId, String status, Long tenantId);

    List<YbInpatientPreSettle> findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long paymentDraftId, String status, Long tenantId);

    /**
     * 查询指定时间段内已正式结算的住院预结算快照（左闭右开）。
     *
     * @param tenantId 租户
     * @param status 快照状态
     * @param beginAt 起始时间（含）
     * @param endExclusive 结束时间（不含）
     */
    List<YbInpatientPreSettle> findAllByTenantIdAndIsDeletedFalseAndStatusAndSetlIdIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        Long tenantId,
        String status,
        LocalDateTime beginAt,
        LocalDateTime endExclusive);
}
