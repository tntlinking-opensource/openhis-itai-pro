/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inpatient.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.platform.model.ChsFeeUploadResultRow;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.inpatient.repository.YbInpatientFeeUploadRepository;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.openhis.chs.domain.settlement.support.FeeUploadResultApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 2301 住院费用上传行落库 */
@Service
@RequiredArgsConstructor
public class YbInpatientFeeUploadDomainService {

    private final YbInpatientFeeUploadRepository feeUploadRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 将 2301 费用上传结果逐行 upsert 至 {@code yb_inpatient_fee_upload}。
     *
     * @param encounterId        就诊 ID
     * @param mdtrtId              平台就诊 ID
     * @param lines                待落库收费明细
     * @param resultBySn           平台返回结果（按 feedetl_sn 索引）
     * @param fallbackOutputJson   平台原始应答 JSON（无逐行结果时兜底）
     * @param tenantId             租户 ID
     */
    public void persistFrom2301(
        Long encounterId,
        String mdtrtId,
        List<IpSettleChargeLine> lines,
        Map<String, ChsFeeUploadResultRow> resultBySn,
        String fallbackOutputJson,
        Long tenantId) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (IpSettleChargeLine line : lines) {
            if (line == null || line.getChargeItemId() == null) {
                continue;
            }
            YbInpatientFeeUpload row = feeUploadRepository
                .findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(line.getChargeItemId(), tenantId)
                .orElseGet(YbInpatientFeeUpload::new);
            if (row.getId() == null) {
                row.setCreatedAt(now);
            }
            row.setEncounterId(encounterId);
            row.setChargeItemId(line.getChargeItemId());
            row.setFeedetlSn(line.getFeedetlSn());
            row.setMdtrtId(mdtrtId);
            row.setDetItemFeeSumamt(line.getDetItemFeeSumamt());
            chargeBatchLifecycle.markInpatientFeeUploaded(row);
            ChsFeeUploadResultRow platformRow = null;
            if (resultBySn != null && isNotEmpty(line.getFeedetlSn())) {
                platformRow = resultBySn.get(line.getFeedetlSn().trim());
            }
            FeeUploadResultApplier.applyToInpatientRow(row, platformRow);
            FeeUploadResultApplier.applyFallbackJson(row, fallbackOutputJson);
            row.setUploadedAt(now);
            row.setTenantId(tenantId);
            feeUploadRepository.save(row);
        }
    }

    /**
     * 持久化单条住院费用上传行。
     *
     * @param row 费用上传实体
     */
    public void save(YbInpatientFeeUpload row) {
        feeUploadRepository.save(row);
    }

    /**
     * 将预结算快照关联的已上传费用行标记为已结算。
     *
     * @param pre      预结算快照
     * @param tenantId 租户 ID
     */
    public void markSettledByPreSettle(YbInpatientPreSettle pre, Long tenantId) {
        for (Long chargeItemId : ChargeItemIdsSupport.parseIds(pre.getChargeItemIds())) {
            feeUploadRepository.findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId)
                .filter(row -> YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
                .ifPresent(row -> {
                    chargeBatchLifecycle.markInpatientFeeSettled(row);
                    feeUploadRepository.save(row);
                });
        }
    }

    /**
     * 将预结算快照关联的费用行标记为结算撤销。
     *
     * @param pre      预结算快照
     * @param tenantId 租户 ID
     */
    public void markSettleRevokedByPreSettle(YbInpatientPreSettle pre, Long tenantId) {
        for (Long chargeItemId : ChargeItemIdsSupport.parseIds(pre.getChargeItemIds())) {
            feeUploadRepository.findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId)
                .filter(row -> YbFeeUploadStatus.SETTLED.equals(row.getUploadStatus())
                    || YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
                .ifPresent(row -> {
                    chargeBatchLifecycle.markInpatientFeeSettleRevoked(row);
                    feeUploadRepository.save(row);
                });
        }
    }

    /**
     * 批量将费用上传行标记为已撤销。
     *
     * @param uploads 费用上传行列表
     */
    public void markRevokedByUploads(List<YbInpatientFeeUpload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (YbInpatientFeeUpload upload : uploads) {
            chargeBatchLifecycle.markInpatientFeeRevoked(upload);
            upload.setUploadedAt(now);
            feeUploadRepository.save(upload);
        }
    }
}
