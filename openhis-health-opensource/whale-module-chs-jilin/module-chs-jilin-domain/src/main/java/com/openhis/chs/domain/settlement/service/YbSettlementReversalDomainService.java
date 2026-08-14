/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import com.openhis.chs.domain.settlement.repository.YbSettlementReversalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 2208/2305 撤销凭证落库 */
@Service
@RequiredArgsConstructor
public class YbSettlementReversalDomainService {

    private final YbSettlementReversalRepository reversalRepository;
    private final ChargeBatchLifecycle chargeBatchLifecycle;

    /**
     * 2208/2305 撤销成功后落库撤销凭证并标记结算回执 REVOKED。
     *
     * @param record          原结算回执
     * @param revokeSetlinfo  撤销输出 setlinfo
     * @param setldetailJson  撤销 setldetail JSON
     * @param operatorId      操作员 ID
     * @param invokeLogId     关联 invoke 日志 ID
     * @return 撤销凭证主键；入参不完整时 {@code null}
     */
    public Long persistReversal(
        YbSettlementRecord record,
        PlatformSetlinfoFields revokeSetlinfo,
        String setldetailJson,
        Long operatorId,
        Long invokeLogId) {
        if (record == null || revokeSetlinfo == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        YbSettlementReversal reversal = new YbSettlementReversal();
        reversal.setSettlementRecordId(record.getId());
        reversal.setRevokeSetlId(textOr(revokeSetlinfo.getSetlId(), record.getSetlId()));
        reversal.setMdtrtId(textOr(revokeSetlinfo.getMdtrtId(), record.getMdtrtId()));
        reversal.setPsnNo(textOr(revokeSetlinfo.getPsnNo(), record.getPsnNo()));
        reversal.setEncounterClass(record.getEncounterClass());
        reversal.setMedfeeSumamt(decimalOr(revokeSetlinfo.getMedfeeSumamt(), record.getMedfeeSumamt()));
        reversal.setFundPaySumamt(decimalOr(revokeSetlinfo.getFundPaySumamt(), record.getFundPaySumamt()));
        reversal.setAcctPay(decimalOr(revokeSetlinfo.getAcctPay(), record.getAcctPay()));
        reversal.setPsnCashPay(decimalOr(revokeSetlinfo.getPsnCashPay(), record.getPsnCashPay()));
        if (isNotEmpty(setldetailJson)) {
            reversal.setFundBreakdownJson(setldetailJson);
        }
        reversal.setInvokeLogId(invokeLogId);
        reversal.setReversedAt(now);
        reversal.setTenantId(record.getTenantId());
        reversal.setCreatedAt(now);
        if (operatorId != null) {
            reversal.setCreatedBy(operatorId);
        }
        reversalRepository.saveAndFlush(reversal);

        chargeBatchLifecycle.markVoucherRevoked(record);
        record.setUpdatedAt(now);
        if (operatorId != null) {
            record.setUpdatedBy(operatorId);
        }
        return reversal.getId();
    }

    /**
     * 2601 冲正成功后以结算回执快照合成撤销凭证（无平台 setlinfo）。
     *
     * @param record      原结算回执
     * @param operatorId  操作员 ID
     * @param invokeLogId 关联 invoke 日志 ID
     * @return 撤销凭证主键；record 为空时 {@code null}
     */
    public Long persist2601Correction(YbSettlementRecord record, Long operatorId, Long invokeLogId) {
        if (record == null) {
            return null;
        }
        PlatformSetlinfoFields synthetic = new PlatformSetlinfoFields();
        synthetic.setSetlId(record.getSetlId());
        synthetic.setMdtrtId(record.getMdtrtId());
        synthetic.setPsnNo(record.getPsnNo());
        synthetic.setMedfeeSumamt(record.getMedfeeSumamt());
        synthetic.setFundPaySumamt(record.getFundPaySumamt());
        synthetic.setAcctPay(record.getAcctPay());
        synthetic.setPsnCashPay(record.getPsnCashPay());
        return persistReversal(record, synthetic, null, operatorId, invokeLogId);
    }

    /**
     * 无平台撤销 setlinfo 时按结算回执合成撤销凭证；已有未删除凭证则跳过（幂等）。
     * <p>供工伤 2208、历史补齐等场景：只保证对账抵减有反向行，不重复落库。</p>
     *
     * @param record      原结算回执
     * @param operatorId  操作员 ID
     * @param invokeLogId 关联 invoke 日志 ID
     * @return 已有或新建撤销凭证主键；record 为空时 {@code null}
     */
    public Long persistSyntheticIfAbsent(YbSettlementRecord record, Long operatorId, Long invokeLogId) {
        if (record == null || record.getId() == null) {
            return null;
        }
        Long tenantId = record.getTenantId();
        List<YbSettlementReversal> existing =
            reversalRepository.findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByReversedAtDesc(
                record.getId(), tenantId);
        if (!existing.isEmpty()) {
            chargeBatchLifecycle.markVoucherRevoked(record);
            return existing.get(0).getId();
        }
        return persist2601Correction(record, operatorId, invokeLogId);
    }

    /**
     * 2208/2305 撤销落库（不关联 invoke 日志）。
     *
     * @param record         原结算回执
     * @param revokeSetlinfo 撤销输出 setlinfo
     * @param setldetailJson 撤销 setldetail JSON
     * @param operatorId     操作员 ID
     * @return 撤销凭证主键；入参不完整时 {@code null}
     */
    public Long persistReversal(
        YbSettlementRecord record,
        PlatformSetlinfoFields revokeSetlinfo,
        String setldetailJson,
        Long operatorId) {
        return persistReversal(record, revokeSetlinfo, setldetailJson, operatorId, null);
    }

    /**
     * 取非空字符串，否则回落 fallback。
     *
     * @param value    首选值
     * @param fallback 回落值
     * @return 非空字符串
     */
    private static String textOr(String value, String fallback) {
        if (isNotEmpty(value)) {
            return value;
        }
        return fallback;
    }

    /**
     * 取非空金额，否则回落 fallback。
     *
     * @param value    首选金额
     * @param fallback 回落金额
     * @return 金额
     */
    private static BigDecimal decimalOr(BigDecimal value, BigDecimal fallback) {
        if (value != null) {
            return value;
        }
        return fallback;
    }
}
