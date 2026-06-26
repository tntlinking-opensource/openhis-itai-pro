/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface YbClinicPreSettleRepository extends JpaRepository<YbClinicPreSettle, Long> {

    Optional<YbClinicPreSettle> findFirstByChrgBchnoAndTenantIdAndIsDeletedFalse(String chrgBchno, Long tenantId);

    Optional<YbClinicPreSettle> findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long paymentDraftId, Long tenantId);

    List<YbClinicPreSettle> findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long paymentDraftId, String status, Long tenantId);

    List<YbClinicPreSettle> findAllByEncounterIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Collection<Long> encounterIds, Long tenantId);
}
