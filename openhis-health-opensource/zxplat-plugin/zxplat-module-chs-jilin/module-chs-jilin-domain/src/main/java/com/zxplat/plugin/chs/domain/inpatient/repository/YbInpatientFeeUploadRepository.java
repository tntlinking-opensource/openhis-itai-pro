/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.repository;

import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 2301 住院费用上传行 {@code yb_inpatient_fee_upload} 仓储 */
public interface YbInpatientFeeUploadRepository extends JpaRepository<YbInpatientFeeUpload, Long> {

    /**
     * 按收费项 ID 查找费用上传行。
     *
     * @param chargeItemId 收费项 ID
     * @param tenantId     租户 ID
     * @return 费用上传行
     */
    Optional<YbInpatientFeeUpload> findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(
        Long chargeItemId, Long tenantId);

    /**
     * 按收费项列出全部未软删上传行。
     *
     * <p>套餐子项共用父收费项主键、各占一条 {@code feedetl_sn}，不能只取第一行。</p>
     *
     * @param chargeItemId 收费项 ID
     * @param tenantId     租户 ID
     * @return 该收费项下的上传行；无则空列表
     */
    List<YbInpatientFeeUpload> findAllByChargeItemIdAndTenantIdAndIsDeletedFalse(
        Long chargeItemId, Long tenantId);

    /**
     * 按平台费用流水号查找上传行。
     *
     * @param feedetlSn 费用明细流水号
     * @param tenantId  租户 ID
     * @return 上传行
     */
    Optional<YbInpatientFeeUpload> findFirstByFeedetlSnAndTenantIdAndIsDeletedFalse(
        String feedetlSn, Long tenantId);

    /**
     * 列出指定就诊与上传状态下的费用上传行。
     *
     * @param encounterId  就诊 ID
     * @param uploadStatus 上传状态
     * @param tenantId     租户 ID
     * @return 费用上传行列表
     */
    List<YbInpatientFeeUpload> findAllByEncounterIdAndUploadStatusAndTenantIdAndIsDeletedFalse(
        Long encounterId, String uploadStatus, Long tenantId);
}
