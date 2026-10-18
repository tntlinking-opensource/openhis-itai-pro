/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.service;

import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.zxplat.plugin.chs.domain.inpatient.repository.YbInpatientFeeUploadRepository;
import com.zxplat.plugin.chs.domain.settlement.YbFeeUploadStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
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
     * 列出收费项下全部未软删上传行（套餐子流水各一行）。
     *
     * @param chargeItemId 收费项 ID
     * @param tenantId     租户 ID
     * @return 上传行；参数无效或无记录时空列表
     */
    public List<YbInpatientFeeUpload> listByChargeItemId(Long chargeItemId, Long tenantId) {
        if (chargeItemId == null || tenantId == null) {
            return Collections.emptyList();
        }
        List<YbInpatientFeeUpload> rows = feeUploadRepository
            .findAllByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows;
    }

    /**
     * 判断该费用流水是否已成功上传且尚未撤销。
     *
     * <p>套餐子项共用父收费项主键，须按 {@code feedetl_sn} 判断，不能看收费项上任意一行。</p>
     *
     * @param feedetlSn 费用明细流水号
     * @param tenantId  租户 ID
     * @return 状态为 UPLOADED 时返回 {@code true}
     */
    public boolean isUploadedFeedetl(String feedetlSn, Long tenantId) {
        if (feedetlSn == null || feedetlSn.isBlank() || tenantId == null) {
            return false;
        }
        return feeUploadRepository
            .findFirstByFeedetlSnAndTenantIdAndIsDeletedFalse(feedetlSn.trim(), tenantId)
            .filter(row -> YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
            .isPresent();
    }

    /**
     * 列出就诊下仍为 {@code UPLOADED} 的住院费用上传行。
     *
     * <p>供预结算前清扫：院内已软删/非待结但平台仍留底的孤儿明细。</p>
     *
     * @param encounterId 就诊 ID
     * @param tenantId    租户 ID
     * @return 已上传未撤销行；参数无效时空列表
     */
    public List<YbInpatientFeeUpload> listUploadedByEncounter(Long encounterId, Long tenantId) {
        if (encounterId == null || tenantId == null) {
            return Collections.emptyList();
        }
        List<YbInpatientFeeUpload> rows = feeUploadRepository
            .findAllByEncounterIdAndUploadStatusAndTenantIdAndIsDeletedFalse(
                encounterId, YbFeeUploadStatus.UPLOADED, tenantId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows;
    }
}
