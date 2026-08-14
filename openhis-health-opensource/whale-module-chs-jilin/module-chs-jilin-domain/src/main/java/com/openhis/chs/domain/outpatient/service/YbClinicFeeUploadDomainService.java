/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.outpatient.command.OpSettleChargeLine;
import com.openhis.chs.domain.platform.model.ChsFeeUploadResultRow;
import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import com.openhis.chs.domain.outpatient.repository.YbClinicFeeUploadRepository;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.support.FeeUploadResultApplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 2204 门诊费用上传行落库 */
@Service
@RequiredArgsConstructor
public class YbClinicFeeUploadDomainService {

    private final YbClinicFeeUploadRepository clinicFeeUploadRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 将 2204 费用上传结果逐行 upsert 至 {@code yb_clinic_fee_upload}。
     *
     * @param encounterId  就诊 ID
     * @param mdtrtId      平台就诊 ID
     * @param chrgBchno    收费批次号
     * @param lines        待落库收费明细
     * @param resultBySn   平台返回结果（按 feedetl_sn 索引）
     * @param tenantId     租户 ID
     */
    public void persistFrom2204(
        Long encounterId,
        String mdtrtId,
        String chrgBchno,
        List<OpSettleChargeLine> lines,
        Map<String, ChsFeeUploadResultRow> resultBySn,
        Long tenantId) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (OpSettleChargeLine line : lines) {
            if (line == null || line.getChargeItemId() == null || isEmpty(line.getFeedetlSn())) {
                continue;
            }
            ChsFeeUploadResultRow platformRow = null;
            if (resultBySn != null) {
                platformRow = resultBySn.get(line.getFeedetlSn().trim());
            }
            YbClinicFeeUpload row = clinicFeeUploadRepository
                .findFirstByChargeItemIdAndChrgBchnoAndTenantIdAndIsDeletedFalse(
                    line.getChargeItemId(), chrgBchno, tenantId)
                .orElseGet(YbClinicFeeUpload::new);
            if (row.getId() == null) {
                row.setCreatedAt(now);
            }
            row.setEncounterId(encounterId);
            row.setChargeItemId(line.getChargeItemId());
            row.setFeedetlSn(line.getFeedetlSn().trim());
            row.setMdtrtId(mdtrtId);
            row.setChrgBchno(chrgBchno);
            row.setDetItemFeeSumamt(line.getDetItemFeeSumamt());
            chargeBatchLifecycle.markClinicFeeUploaded(row);
            FeeUploadResultApplier.applyToClinicRow(row, platformRow);
            row.setUploadedAt(now);
            row.setTenantId(tenantId);
            clinicFeeUploadRepository.save(row);
        }
    }

    /**
     * 将同一收费批次下的费用上传行关联至预结算快照。
     *
     * @param chrgBchno    收费批次号
     * @param preSettleId  预结算快照主键
     * @param tenantId     租户 ID
     */
    public void linkPreSettle(String chrgBchno, Long preSettleId, Long tenantId) {
        if (isEmpty(chrgBchno) || preSettleId == null) {
            return;
        }
        List<YbClinicFeeUpload> rows = clinicFeeUploadRepository
            .findAllByChrgBchnoAndTenantIdAndIsDeletedFalse(chrgBchno.trim(), tenantId);
        for (YbClinicFeeUpload row : rows) {
            row.setPreSettleId(preSettleId);
            clinicFeeUploadRepository.save(row);
        }
    }

    /**
     * 将指定收费批次下已上传的费用行标记为已结算。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     */
    public void markSettledByChrgBchno(String chrgBchno, Long tenantId) {
        if (isEmpty(chrgBchno)) {
            return;
        }
        List<YbClinicFeeUpload> rows = clinicFeeUploadRepository
            .findAllByChrgBchnoAndUploadStatusAndTenantIdAndIsDeletedFalse(
                chrgBchno.trim(), YbFeeUploadStatus.UPLOADED, tenantId);
        for (YbClinicFeeUpload row : rows) {
            chargeBatchLifecycle.markClinicFeeSettled(row);
            clinicFeeUploadRepository.save(row);
        }
    }

    /**
     * 将指定收费批次下的费用上传行标记为已撤销（不含已结算行）。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     */
    public void markRevokedByChrgBchno(String chrgBchno, Long tenantId) {
        markRevokedByChrgBchno(chrgBchno, tenantId, false);
    }

    /**
     * 按批次标记费用上传已撤销（独立事务，与 2205 平台副作用对齐）。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRevokedByChrgBchnoInNewTx(String chrgBchno, Long tenantId) {
        markRevokedByChrgBchno(chrgBchno, tenantId, false);
    }

    /**
     * 将指定收费批次下的费用上传行标记为结算撤销（含已结算行）。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     */
    public void markSettleRevokedByChrgBchno(String chrgBchno, Long tenantId) {
        markRevokedByChrgBchno(chrgBchno, tenantId, true);
    }

    /**
     * 按撤销类型更新收费批次下费用上传行状态。
     *
     * <p>普通 2205（{@code includeSettled=false}）只处理 {@code UPLOADED} 行，跳过他患
     * {@code SETTLED} 占同 {@code chrg_bchno} 的行，避免批次号碰撞时本地收口抛
     * 「已参与正式结算的费用明细不可 2205 撤销」阻断重试。</p>
     *
     * @param chrgBchno       收费批次号
     * @param tenantId        租户 ID
     * @param includeSettled  是否同时处理已结算行（结算撤销场景）
     */
    private void markRevokedByChrgBchno(String chrgBchno, Long tenantId, boolean includeSettled) {
        if (isEmpty(chrgBchno)) {
            return;
        }
        List<YbClinicFeeUpload> rows;
        if (includeSettled) {
            rows = clinicFeeUploadRepository
                .findAllByChrgBchnoAndTenantIdAndIsDeletedFalse(chrgBchno.trim(), tenantId);
        } else {
            // Pre：仅 UPLOADED，避开同批次号下他患已 SETTLED 行
            rows = clinicFeeUploadRepository
                .findAllByChrgBchnoAndUploadStatusAndTenantIdAndIsDeletedFalse(
                    chrgBchno.trim(), YbFeeUploadStatus.UPLOADED, tenantId);
        }
        for (YbClinicFeeUpload row : rows) {
            if (includeSettled) {
                chargeBatchLifecycle.markClinicFeeSettleRevoked(row);
            } else {
                chargeBatchLifecycle.markClinicFeeRevoked(row);
            }
            clinicFeeUploadRepository.save(row);
        }
    }
}
