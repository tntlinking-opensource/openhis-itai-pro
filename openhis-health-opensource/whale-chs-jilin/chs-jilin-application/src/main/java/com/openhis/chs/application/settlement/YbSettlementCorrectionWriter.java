/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.settlement;

import com.openhis.chs.domain.settlement.dto.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementCorrection;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementCorrectionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 2601 冲正审计落库。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class YbSettlementCorrectionWriter {

    private final YbSettlementCorrectionRepository correctionRepository;

    /**
     * 记录一次 2601 冲正。
     *
     * @return 审计行 id；参数不完整时返回 null
     */
    public Long persist(
        SettlementCorrectionCommand command,
        YbSettlementRecord record,
        Long invokeLogId,
        Long tenantId) {
        if (command == null || StringUtils.isAnyBlank(command.getOmsgid(), command.getOinfno(), command.getPsnNo())) {
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
