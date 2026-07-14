/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.invoke.entity.YbInvokeLog;
import com.openhis.chs.domain.invoke.service.YbInvokeLogQueryService;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收银落账后扫描「本地 SUCCESS、平台状态不明」的 2207/2304 调用，组装 2601 冲正命令 */
@Service
@RequiredArgsConstructor
public class PaymentSettledCorrectionQueryService {

    private final YbInvokeLogQueryService invokeLogQueryService;
    private final YbSettlementRecordQueryService settlementRecordQueryService;

    /**
     * 收银落账后扫描需 2601 冲正的结算调用并组装命令。
     *
     * @param paymentId  支付单 ID
     * @param operatorId 操作员 ID
     * @param tenantId   租户 ID
     * @return 按结算回执去重后的冲正命令列表
     */
    public List<SettlementCorrectionCommand> listUncertainByPayment(Long paymentId, Long operatorId, Long tenantId) {
        if (paymentId == null || tenantId == null) {
            return List.of();
        }
        List<YbInvokeLog> invokeLogs = invokeLogQueryService.listByPaymentDraftId(paymentId, tenantId);
        Map<Long, SettlementCorrectionCommand> byRecordId = new LinkedHashMap<>();
        for (YbInvokeLog invokeLog : invokeLogs) {
            if (invokeLog == null || !isSettleInfno(invokeLog.getInfno())) {
                continue;
            }
            if (ChsPlatformCodes.INFCODE_SUCCESS.equals(invokeLog.getInfcode())) {
                continue;
            }
            if (isEmpty(invokeLog.getMsgid())) {
                continue;
            }
            YbSettlementRecord record = resolveRecord(invokeLog, tenantId);
            if (record == null || YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                continue;
            }
            if (isEmpty(record.getPsnNo())) {
                continue;
            }
            String omsgid = firstNonEmpty(record.getMsgid2207(), record.getMedinsSetlId(), invokeLog.getMsgid());
            String contractBusNo = resolveContractBusNo(invokeLog, invokeLogs);
            if (isEmpty(omsgid) || isEmpty(contractBusNo)) {
                continue;
            }
            SettlementCorrectionCommand command = new SettlementCorrectionCommand()
                .setSettlementRecordId(record.getId())
                .setContractBusNo(contractBusNo)
                .setPsnNo(record.getPsnNo().trim())
                .setOmsgid(omsgid.trim())
                .setOinfno(invokeLog.getInfno().trim())
                .setOperatorId(operatorId)
                .setTenantId(tenantId);
            byRecordId.putIfAbsent(record.getId(), command);
        }
        return new ArrayList<>(byRecordId.values());
    }

    /**
     * 从 invoke 日志关联字段解析结算回执。
     *
     * @param invokeLog invoke 日志
     * @param tenantId  租户 ID
     * @return 匹配结算回执；无法唯一定位时 {@code null}
     */
    private YbSettlementRecord resolveRecord(YbInvokeLog invokeLog, Long tenantId) {
        if (invokeLog.getSettlementRecordId() != null) {
            var byId = settlementRecordQueryService.findOptionalByIdAndTenant(
                invokeLog.getSettlementRecordId(), tenantId);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        if (invokeLog.getPaymentDraftId() != null) {
            List<YbSettlementRecord> records = settlementRecordQueryService.listByPaymentId(
                invokeLog.getPaymentDraftId(), tenantId);
            for (YbSettlementRecord record : records) {
                if (record == null || YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                    continue;
                }
                if (isNotEmpty(invokeLog.getChrgBchno())
                    && invokeLog.getChrgBchno().equals(record.getChrgBchno())) {
                    return record;
                }
            }
            if (records.size() == 1) {
                return records.get(0);
            }
        }
        return null;
    }

    /**
     * 从同批次 invoke 日志中取 contractBusNo（2601 路由兜底）。
     *
     * @param invokeLog  当前 invoke 日志
     * @param invokeLogs 同支付单全部 invoke 日志
     * @return 首个非空 contractBusNo
     */
    private static String resolveContractBusNo(YbInvokeLog invokeLog, List<YbInvokeLog> invokeLogs) {
        if (invokeLog != null && isNotEmpty(invokeLog.getContractBusNo())) {
            return invokeLog.getContractBusNo().trim();
        }
        if (invokeLogs == null) {
            return null;
        }
        for (YbInvokeLog row : invokeLogs) {
            if (row != null && isNotEmpty(row.getContractBusNo())) {
                return row.getContractBusNo().trim();
            }
        }
        return null;
    }

    /**
     * 判断是否为正式结算交易号（2207/2304）。
     *
     * @param infno 交易号
     * @return 是否为结算交易
     */
    private static boolean isSettleInfno(String infno) {
        return ChsInfNo.OUTPATIENT_SETTLE.equals(infno)
            || ChsInfNo.INPATIENT_SETTLE.equals(infno);
    }

    /**
     * 返回首个非空字符串。
     *
     * @param values 候选值
     * @return 首个非空值；均无则 {@code null}
     */
    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (isNotEmpty(value)) {
                return value;
            }
        }
        return null;
    }
}
