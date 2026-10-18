/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.service;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleChargeLine;
import com.zxplat.plugin.chs.domain.platform.model.ChsFeeUploadResultRow;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.zxplat.plugin.chs.domain.inpatient.repository.YbInpatientFeeUploadRepository;
import com.zxplat.plugin.chs.domain.settlement.YbFeeUploadStatus;
import com.zxplat.plugin.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.zxplat.plugin.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.zxplat.plugin.chs.domain.settlement.support.FeeUploadResultApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
     * <p>使用 {@code REQUIRES_NEW}：平台 2301 已成功后若后续 2303 失败回滚，本地上传态仍保留，
     * 避免重试再传同 {@code feedetl_sn} 触发平台 feeListDBO_41 重复主键。
     * 套餐子项共用父收费项主键，按 {@code feedetl_sn} 各 upsert 一行。</p>
     *
     * @param encounterId        就诊 ID
     * @param mdtrtId              平台就诊 ID
     * @param lines                待落库收费明细
     * @param resultBySn           平台返回结果（按 feedetl_sn 索引）
     * @param fallbackOutputJson   平台原始应答 JSON（无逐行结果时兜底）
     * @param tenantId             租户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            if (line == null || line.getChargeItemId() == null || line.getFeedetlSn() == null
                || line.getFeedetlSn().isBlank()) {
                continue;
            }
            // 套餐子项共用父 charge_item_id，按 feedetl_sn 各留一行，避免后写覆盖先写的流水
            YbInpatientFeeUpload row = feeUploadRepository
                .findFirstByFeedetlSnAndTenantIdAndIsDeletedFalse(line.getFeedetlSn().trim(), tenantId)
                .orElseGet(YbInpatientFeeUpload::new);
            if (row.getId() == null) {
                row.setCreatedAt(now);
            }
            row.setEncounterId(encounterId);
            row.setChargeItemId(line.getChargeItemId());
            row.setFeedetlSn(line.getFeedetlSn().trim());
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
            for (YbInpatientFeeUpload row : listRows(chargeItemId, tenantId)) {
                if (!YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus())) {
                    continue;
                }
                chargeBatchLifecycle.markInpatientFeeSettled(row);
                feeUploadRepository.save(row);
            }
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
            for (YbInpatientFeeUpload row : listRows(chargeItemId, tenantId)) {
                if (!YbFeeUploadStatus.SETTLED.equals(row.getUploadStatus())
                    && !YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus())) {
                    continue;
                }
                chargeBatchLifecycle.markInpatientFeeSettleRevoked(row);
                feeUploadRepository.save(row);
            }
        }
    }

    /**
     * 批量将指定收费项对应的住院费用上传行标记为已撤销（供换新 mdtrt 后重传 2301）。
     *
     * <p>使用 {@code REQUIRES_NEW} 先提交并释放行锁：随后 {@link #persistFrom2301} 同为
     * {@code REQUIRES_NEW}，若本方法挂在外层事务未提交则会对同一行死等，预结算卡在 2301 成功之后。</p>
     *
     * @param chargeItemIds 收费项 ID
     * @param tenantId      租户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRevokedByChargeItemIds(List<Long> chargeItemIds, Long tenantId) {
        if (chargeItemIds == null || chargeItemIds.isEmpty() || tenantId == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long chargeItemId : chargeItemIds) {
            if (chargeItemId == null) {
                continue;
            }
            for (YbInpatientFeeUpload row : listRows(chargeItemId, tenantId)) {
                chargeBatchLifecycle.markInpatientFeeRevoked(row);
                row.setUploadedAt(now);
                feeUploadRepository.save(row);
            }
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
            // 2305 已把正式结算行标 REVOKED；若 2302 先于该标记执行，SETTLED 也须能落到 REVOKED
            chargeBatchLifecycle.markInpatientFeeSettleRevoked(upload);
            upload.setUploadedAt(now);
            feeUploadRepository.save(upload);
        }
    }

    /**
     * 列出收费项下未软删上传行。
     *
     * @param chargeItemId 收费项 ID
     * @param tenantId     租户 ID
     * @return 上传行；参数无效时空列表
     */
    private List<YbInpatientFeeUpload> listRows(Long chargeItemId, Long tenantId) {
        if (chargeItemId == null || tenantId == null) {
            return List.of();
        }
        List<YbInpatientFeeUpload> rows = feeUploadRepository
            .findAllByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows;
    }
}
