/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.service;

import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.inpatient.repository.YbInpatientFeeUploadRepository;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 2301 住院费用上传行只读查询 */
@Service
@RequiredArgsConstructor
public class YbInpatientFeeUploadQueryService {

    private final YbInpatientFeeUploadRepository feeUploadRepository;

    /**
     * 按收费项 ID 查找住院费用上传行。
     *
     * @param chargeItemId 收费项 ID
     * @param tenantId     租户 ID
     * @return 费用上传行；参数无效或未找到时返回空
     */
    public Optional<YbInpatientFeeUpload> findOptionalByChargeItemId(Long chargeItemId, Long tenantId) {
        if (chargeItemId == null || tenantId == null) {
            return Optional.empty();
        }
        return feeUploadRepository.findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId);
    }

    /**
     * 判断收费项是否已成功上传至平台。
     *
     * @param chargeItemId 收费项 ID
     * @param tenantId     租户 ID
     * @return 已上传返回 {@code true}
     */
    public boolean isUploaded(Long chargeItemId, Long tenantId) {
        return findOptionalByChargeItemId(chargeItemId, tenantId)
            .filter(row -> YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
            .isPresent();
    }
}
