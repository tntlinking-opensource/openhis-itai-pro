/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 2204 门诊费用上传行 {@code yb_clinic_fee_upload} 仓储 */
public interface YbClinicFeeUploadRepository extends JpaRepository<YbClinicFeeUpload, Long> {

    /**
     * 按收费项与收费批次号查找费用上传行。
     *
     * @param chargeItemId 收费项 ID
     * @param chrgBchno    收费批次号
     * @param tenantId     租户 ID
     * @return 费用上传行
     */
    Optional<YbClinicFeeUpload> findFirstByChargeItemIdAndChrgBchnoAndTenantIdAndIsDeletedFalse(
        Long chargeItemId, String chrgBchno, Long tenantId);

    /**
     * 列出指定收费批次下的所有费用上传行。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     * @return 费用上传行列表
     */
    List<YbClinicFeeUpload> findAllByChrgBchnoAndTenantIdAndIsDeletedFalse(
        String chrgBchno, Long tenantId);

    /**
     * 列出指定收费批次与上传状态下的费用上传行。
     *
     * @param chrgBchno    收费批次号
     * @param uploadStatus 上传状态
     * @param tenantId     租户 ID
     * @return 费用上传行列表
     */
    List<YbClinicFeeUpload> findAllByChrgBchnoAndUploadStatusAndTenantIdAndIsDeletedFalse(
        String chrgBchno, String uploadStatus, Long tenantId);
}
