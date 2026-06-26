/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.repository;

import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 医保正式结算回执仓储（门诊/住院共用）。 */
public interface YbSettlementRecordRepository extends JpaRepository<YbSettlementRecord, Long> {

    Optional<YbSettlementRecord> findFirstByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    Optional<YbSettlementRecord> findFirstByPaymentIdAndTenantIdAndIsDeletedFalse(
        Long paymentId, Long tenantId);

    List<YbSettlementRecord> findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long paymentId, Long tenantId);

    Optional<YbSettlementRecord> findFirstBySetlIdAndTenantIdAndIsDeletedFalse(String setlId, Long tenantId);

    List<YbSettlementRecord> findAllByTenantIdAndIsDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        Long tenantId,
        LocalDateTime beginAt,
        LocalDateTime endExclusive);

    Optional<YbSettlementRecord> findFirstByEncounterIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long encounterId, Long tenantId);

    Optional<YbSettlementRecord> findFirstByEncounterIdAndEncounterClassAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long encounterId, String encounterClass, Long tenantId);
}
