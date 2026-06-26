/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient.support;

import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/** 结算回执定位（混合结算下一 payment 可对应多笔）。 */
public final class ChsSettlementRecordLookup {

    private ChsSettlementRecordLookup() {
    }

    /**
     * 按 settlementRecordId / setlId / paymentId 唯一定位结算回执。
     *
     * @param repository 结算回执仓储
     * @param settlementRecordId 结算回执主键（优先）
     * @param paymentId 本地 payment id（仅唯一时可用）
     * @param setlId 平台 setl_id
     * @param tenantId 租户
     * @return 结算回执
     */
    public static YbSettlementRecord requireOne(
        YbSettlementRecordRepository repository,
        Long settlementRecordId,
        Long paymentId,
        String setlId,
        Long tenantId) {
        if (settlementRecordId != null) {
            YbSettlementRecord record = repository.findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执"));
            if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
            }
            return record;
        }
        if (StringUtils.isNotBlank(setlId)) {
            YbSettlementRecord record = repository.findFirstBySetlIdAndTenantIdAndIsDeletedFalse(setlId.trim(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执"));
            if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
            }
            return record;
        }
        if (paymentId != null) {
            List<YbSettlementRecord> records = repository
                .findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(paymentId, tenantId)
                .stream()
                .filter(row -> !YbSettlementVoucherStatus.REVOKED.equals(row.getStatus()))
                .collect(Collectors.toList());
            if (records.isEmpty()) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执");
            }
            if (records.size() > 1) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_AMBIGUOUS,
                    "同一 payment 存在多笔医保结算，请指定 settlementRecordId 或 setlId");
            }
            return records.get(0);
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 settlementRecordId/paymentId/setlId");
    }
}
