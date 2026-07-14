/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.service;

import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.repository.YbInvUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 进销存上报记录只读查询服务。
 */
@Service
@RequiredArgsConstructor
public class YbInvUploadQueryService {

    private final YbInvUploadRepository uploadRepository;

    /**
     * 按来源业务行取最近一条指定状态的上报记录。
     *
     * @param tenantId     租户主键
     * @param infno        医保接口编号
     * @param sourceLineId 来源业务行主键
     * @param uploadStatus 上报状态
     * @return 最近一条匹配的上报记录，参数无效或未找到时 empty
     */
    public Optional<YbInvUpload> findLatestSuccessfulBySourceLine(
            Long tenantId,
            String infno,
            Long sourceLineId,
            String uploadStatus) {
        if (tenantId == null || sourceLineId == null) {
            return Optional.empty();
        }
        return uploadRepository.findFirstByTenantIdAndInfnoAndSourceLineIdAndUploadStatusOrderByIdDesc(
            tenantId, infno, sourceLineId, uploadStatus);
    }

    /**
     * 判断库存品是否已完成初始化 3501（盘存）+ 3502（库存变更）配对上报。
     *
     * @param tenantId        租户主键
     * @param inventoryItemId 库存品主键（作为 sourceLineId）
     * @return 3501 与 3502 均存在成功且来源为 INIT 的记录时 {@code true}
     */
    public boolean isInitializePairComplete(Long tenantId, Long inventoryItemId) {
        Optional<YbInvUpload> row3501 = findLatestSuccessfulBySourceLine(
            tenantId,
            ChsInfNo.INVENTORY_STOCKTAKE,
            inventoryItemId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        if (row3501.isEmpty()
            || !ChsInventoryConstants.SOURCE_TYPE_INIT.equals(row3501.get().getSourceType())) {
            return false;
        }
        Optional<YbInvUpload> row3502 = findLatestSuccessfulBySourceLine(
            tenantId,
            ChsInfNo.INVENTORY_CHANGE,
            inventoryItemId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
        return row3502.isPresent()
            && ChsInventoryConstants.SOURCE_TYPE_INIT.equals(row3502.get().getSourceType());
    }
}
