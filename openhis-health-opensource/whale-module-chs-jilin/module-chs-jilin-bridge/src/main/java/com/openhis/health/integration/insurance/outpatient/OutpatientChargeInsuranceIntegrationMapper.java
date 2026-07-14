/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.outpatient;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeCorrectSettleExtensionIn;
import com.openhis.whale.core.application.exception.BusinessException;

/**
 * Health 门诊结算 ExtensionIn ↔ chs domain Command 映射（2601 补偿路径）。
 *
 * <p>映射方向：{@link OutpatientChargeCorrectSettleExtensionIn} + {@link YbSettlementRecord}
 * → {@link SettlementCorrectionCommand}（仅入向，无 ExtensionOut）。</p>
 */
public final class OutpatientChargeInsuranceIntegrationMapper {

    private OutpatientChargeInsuranceIntegrationMapper() {
    }

    /**
     * 2601 冲正：从 ExtensionIn + 结算回执组装 domain Command。
     *
     * @param extensionIn SPI 入参；{@code settlementRecordId} 必填
     * @param record      本地 {@code yb_settlement_record}
     * @param operatorId  当前操作员
     * @param tenantId    租户
     * @return 2601 冲正命令 */
    public static SettlementCorrectionCommand toCorrectionCommand(
            OutpatientChargeCorrectSettleExtensionIn extensionIn,
            YbSettlementRecord record,
            Long operatorId,
            Long tenantId) {
        if (extensionIn == null || extensionIn.getSettlementRecordId() == null) {
            throw new BusinessException("INS-014", "2601 冲正缺少 settlementRecordId");
        }
        if (record == null) {
            throw new BusinessException("INS-014", "结算回执不存在");
        }
        String omsgid;
        if (isNotEmpty(record.getMsgid2207())) {
            omsgid = record.getMsgid2207().trim();
        } else {
            omsgid = trimToNull(record.getMedinsSetlId());
        }
        if (isEmpty(omsgid)) {
            throw new BusinessException("INS-014", "结算回执缺少 msgid，无法冲正");
        }
        String oinfno = ChsInfNo.OUTPATIENT_SETTLE;
        if (isNotEmpty(extensionIn.getOriginalInfno())) {
            oinfno = extensionIn.getOriginalInfno().trim();
        }
        return new SettlementCorrectionCommand()
            .setSettlementRecordId(record.getId())
            .setPsnNo(record.getPsnNo())
            .setOmsgid(omsgid)
            .setOinfno(oinfno)
            .setOperatorId(operatorId)
            .setTenantId(tenantId);
    }
}
