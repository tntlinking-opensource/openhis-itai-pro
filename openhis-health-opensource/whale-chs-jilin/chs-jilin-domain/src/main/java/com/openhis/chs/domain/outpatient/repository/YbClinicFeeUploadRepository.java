/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** {@link YbClinicFeeUpload} 仓储。 */
public interface YbClinicFeeUploadRepository extends JpaRepository<YbClinicFeeUpload, Long> {

    Optional<YbClinicFeeUpload> findFirstByChargeItemIdAndChrgBchnoAndTenantIdAndIsDeletedFalse(
        Long chargeItemId, String chrgBchno, Long tenantId);

    List<YbClinicFeeUpload> findAllByChrgBchnoAndTenantIdAndIsDeletedFalse(
        String chrgBchno, Long tenantId);

    List<YbClinicFeeUpload> findAllByChrgBchnoAndUploadStatusAndTenantIdAndIsDeletedFalse(
        String chrgBchno, String uploadStatus, Long tenantId);
}
