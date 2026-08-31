/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementCorrection;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementCorrectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 2601 冲正审计落库 */
@Service
@RequiredArgsConstructor
public class YbSettlementCorrectionDomainService {

    private final YbSettlementCorrectionRepository correctionRepository;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbClinicFeeUploadDomainService clinicFeeUploadDomainService;

    /**
     * 2601 冲正审计落库。
     *
     * @param command     冲正命令（须含 omsgid、oinfno、psnNo）
     * @param record      关联结算回执（可空，用于回填 encounterClass）
     * @param invokeLogId 关联 invoke 日志 ID
     * @param tenantId    租户 ID
     * @return 冲正记录主键；命令不完整时 {@code null}
     */
    public Long persist(
        SettlementCorrectionCommand command,
        YbSettlementRecord record,
        Long invokeLogId,
        Long tenantId) {
        if (command == null || isEmpty(command.getOmsgid()) || isEmpty(command.getOinfno()) || isEmpty(command.getPsnNo())) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        YbSettlementCorrection row = new YbSettlementCorrection();
        row.setSettlementRecordId(command.getSettlementRecordId());
        row.setPsnNo(command.getPsnNo().trim());
        row.setOmsgid(command.getOmsgid().trim());
        row.setOinfno(command.getOinfno().trim());
        if (record != null) {
            row.setEncounterClass(record.getEncounterClass());
        }
        row.setInvokeLogId(invokeLogId);
        row.setCorrectedAt(now);
        row.setTenantId(tenantId);
        row.setCreatedAt(now);
        if (command.getOperatorId() != null) {
            row.setCreatedBy(command.getOperatorId());
        }
        correctionRepository.saveAndFlush(row);
        return row.getId();
    }

    /**
     * 平台 2601 已成功后，在独立事务中落本地冲正审计，并将结算从对账口径剔除。
     *
     * <p>使用 {@code REQUIRES_NEW}：外层收银/补偿事务回滚时，本地仍与平台冲正同态。</p>
     * <p>2601 是冲正（视同未发生），不是 2208 一收一退：只写 correction + 软删结算回执，
     * <b>不</b>写 {@code yb_settlement_reversal}，否则 3201 会按一收一退多计 2 笔。</p>
     *
     * @param command     冲正命令
     * @param invokeLogId 2601 invoke 日志 ID
     * @param tenantId    租户 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLocalAfterPlatformSuccess(
        SettlementCorrectionCommand command,
        Long invokeLogId,
        Long tenantId) {
        if (command == null) {
            return;
        }
        Long resolvedTenantId = tenantId;
        if (resolvedTenantId == null) {
            resolvedTenantId = command.getTenantId();
        }
        YbSettlementRecord record = null;
        if (command.getSettlementRecordId() != null && resolvedTenantId != null) {
            record = settlementRecordDomainService
                .findByIdAndTenant(command.getSettlementRecordId(), resolvedTenantId)
                .orElse(null);
        }
        persist(command, record, invokeLogId, resolvedTenantId);
        if (record == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 冲正：凭证作废并软删，对账 listBySetlTimeBetween 不再装载
        if (!YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
            record.setStatus(YbSettlementVoucherStatus.REVOKED);
        }
        if (!Boolean.TRUE.equals(record.getIsDeleted())) {
            record.setIsDeleted(true);
            record.setDeletedAt(now);
            if (command.getOperatorId() != null) {
                record.setDeletedBy(command.getOperatorId());
            }
        }
        record.setUpdatedAt(now);
        if (command.getOperatorId() != null) {
            record.setUpdatedBy(command.getOperatorId());
        }
        settlementRecordDomainService.save(record);
        if (record.getPreSettleId() != null) {
            YbClinicPreSettle pre = clinicPreSettleDomainService.findById(record.getPreSettleId());
            if (pre != null) {
                clinicPreSettleDomainService.markRevokedAndSave(pre);
            }
        }
        if (isNotEmpty(record.getChrgBchno()) && resolvedTenantId != null) {
            clinicFeeUploadDomainService.markSettleRevokedByChrgBchno(
                record.getChrgBchno().trim(),
                resolvedTenantId);
        }
    }
}
