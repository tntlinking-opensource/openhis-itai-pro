/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.repository;

import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface YbInpatientFeeUploadRepository extends JpaRepository<YbInpatientFeeUpload, Long> {

    Optional<YbInpatientFeeUpload> findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(
        Long chargeItemId, Long tenantId);

    List<YbInpatientFeeUpload> findAllByEncounterIdAndUploadStatusAndTenantIdAndIsDeletedFalse(
        Long encounterId, String uploadStatus, Long tenantId);
}
