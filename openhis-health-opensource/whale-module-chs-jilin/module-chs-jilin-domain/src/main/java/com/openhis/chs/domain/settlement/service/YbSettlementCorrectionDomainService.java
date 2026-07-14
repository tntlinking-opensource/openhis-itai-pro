/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementCorrection;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementCorrectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 2601 冲正审计落库 */
@Service
@RequiredArgsConstructor
public class YbSettlementCorrectionDomainService {

    private final YbSettlementCorrectionRepository correctionRepository;

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
}
