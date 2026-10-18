/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.service;

import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.repository.YbInvUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /**
     * 列出就诊下已成功的清床汇总径销售 3505（{@code source_type=CLEAR_BED_SUMMARY}）。
     *
     * <p>反结/召回冲销时只冲本批次，不含发药当时 {@code DISPENSE} 的按患者 3505。</p>
     *
     * @param tenantId    租户主键
     * @param encounterId 就诊主键（写入 {@code source_id}）
     * @return 成功 3505 流水；无匹配时空列表
     */
    public List<YbInvUpload> listSuccessfulClearBedSummarySales(Long tenantId, Long encounterId) {
        List<YbInvUpload> result = new ArrayList<>();
        if (tenantId == null || encounterId == null) {
            return result;
        }
        List<YbInvUpload> rows = uploadRepository.findAllByTenantIdAndSourceTypeAndSourceIdOrderByIdAsc(
            tenantId,
            ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY,
            encounterId);
        for (YbInvUpload row : rows) {
            if (row == null) {
                continue;
            }
            if (!ChsInfNo.INVENTORY_SALE.equals(row.getInfno())) {
                continue;
            }
            if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 本就诊是否已有成功的清床汇总径 3505（用于反结是否触发 3506 的前置判断）。
     *
     * @param tenantId    租户主键
     * @param encounterId 就诊主键
     * @return 存在成功清床汇总销售时 true
     */
    public boolean hasSuccessfulClearBedSummarySales(Long tenantId, Long encounterId) {
        return !this.listSuccessfulClearBedSummarySales(tenantId, encounterId).isEmpty();
    }

    /**
     * 是否存在「已成功清床汇总 3505、尚未被成功 3506 冲销」的批次。
     *
     * <p>召回后再清床前用于轻门禁：有未冲销销售则禁止再次清床，避免平台双报。</p>
     *
     * @param tenantId    租户主键
     * @param encounterId 就诊主键
     * @return 存在未冲销成功销售时 true
     */
    public boolean hasUnreversedClearBedSummarySales(Long tenantId, Long encounterId) {
        List<YbInvUpload> sales = this.listSuccessfulClearBedSummarySales(tenantId, encounterId);
        if (sales.isEmpty()) {
            return false;
        }
        List<YbInvUpload> returns = this.listSuccessfulClearBedSummaryReturns(tenantId, encounterId);
        HashSet<String> reversedKeys = new HashSet<>();
        for (YbInvUpload ret : returns) {
            if (ret == null) {
                continue;
            }
            reversedKeys.add(this.buildClearBedSaleLineKey(ret.getSourceLineId(), ret.getFixmedinsBchno()));
        }
        for (YbInvUpload sale : sales) {
            if (sale == null) {
                continue;
            }
            String key = this.buildClearBedSaleLineKey(sale.getSourceLineId(), sale.getFixmedinsBchno());
            if (!reversedKeys.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 列出就诊下已成功的清床汇总径销售退货 3506。
     *
     * @param tenantId    租户主键
     * @param encounterId 就诊主键
     * @return 成功 3506 流水
     */
    public List<YbInvUpload> listSuccessfulClearBedSummaryReturns(Long tenantId, Long encounterId) {
        List<YbInvUpload> result = new ArrayList<>();
        if (tenantId == null || encounterId == null) {
            return result;
        }
        List<YbInvUpload> rows = uploadRepository.findAllByTenantIdAndSourceTypeAndSourceIdOrderByIdAsc(
            tenantId,
            ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY,
            encounterId);
        for (YbInvUpload row : rows) {
            if (row == null) {
                continue;
            }
            if (!ChsInfNo.INVENTORY_SALE_RETURN.equals(row.getInfno())) {
                continue;
            }
            if (!ChsInventoryConstants.UPLOAD_STATUS_SUCCESS.equals(row.getUploadStatus())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 清床汇总销售/冲销配对键：来源行 + 定点批次号。
     *
     * @param sourceLineId   来源行主键
     * @param fixmedinsBchno 定点批次号
     * @return 配对键
     */
    private String buildClearBedSaleLineKey(Long sourceLineId, String fixmedinsBchno) {
        String linePart = "null";
        if (sourceLineId != null) {
            linePart = String.valueOf(sourceLineId);
        }
        String batchPart = "";
        if (fixmedinsBchno != null) {
            batchPart = fixmedinsBchno.trim();
        }
        return linePart + "|" + batchPart;
    }
}
