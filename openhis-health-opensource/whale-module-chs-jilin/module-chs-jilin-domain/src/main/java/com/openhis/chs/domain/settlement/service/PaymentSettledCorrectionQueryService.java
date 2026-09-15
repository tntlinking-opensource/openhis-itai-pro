/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.invoke.entity.YbInvokeLog;
import com.openhis.chs.domain.invoke.service.YbInvokeLogQueryService;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsPlatformCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 收银落账后扫描「本地 SUCCESS、平台状态不明」的 2207/2304 调用，组装 2601 冲正命令。
 *
 * <p>防误冲：失败 2207 之后若已有成功 2207，跳过该失败日志；{@code omsgid} 固定用失败 invoke 的 msgid，
 * 禁止按 chrg_bchno/单笔回落绑到后续成功回执（pro PMR-2026-09-010174/175）。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentSettledCorrectionQueryService {

    private final YbInvokeLogQueryService invokeLogQueryService;
    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;

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
        Map<String, SettlementCorrectionCommand> byOmsgid = new LinkedHashMap<>();
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
            // 2207 超时/失败后已重试成功：仅冲失败报文，禁止误绑后续成功回执（pro PMR-010174/175）
            if (this.hasLaterSuccessfulSettleInvoke(invokeLogs, invokeLog)) {
                continue;
            }
            YbSettlementRecord record = this.resolveRecordForFailedInvoke(invokeLog, tenantId);
            if (record != null && YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                continue;
            }
            String psnNo = this.resolvePsnNo(paymentId, tenantId, record);
            if (isEmpty(psnNo)) {
                continue;
            }
            // 2601 必须冲「本条失败 invoke 的 msgid」，禁止回落到其它成功 2207 的 msgid_2207
            String omsgid = invokeLog.getMsgid().trim();
            String contractBusNo = resolveContractBusNo(invokeLog, invokeLogs);
            if (isEmpty(contractBusNo)) {
                continue;
            }
            Long settlementRecordId = null;
            if (record != null) {
                settlementRecordId = record.getId();
            }
            SettlementCorrectionCommand command = new SettlementCorrectionCommand()
                .setSettlementRecordId(settlementRecordId)
                .setContractBusNo(contractBusNo)
                .setPsnNo(psnNo)
                .setOmsgid(omsgid)
                .setOinfno(invokeLog.getInfno().trim())
                .setOperatorId(operatorId)
                .setTenantId(tenantId);
            byOmsgid.putIfAbsent(omsgid, command);
        }
        return new ArrayList<>(byOmsgid.values());
    }

    /**
     * 失败 2207/2304 之后是否已有更晚的成功正式结算 invoke（重试成功则跳过落账钩子 2601）。
     */
    private boolean hasLaterSuccessfulSettleInvoke(List<YbInvokeLog> invokeLogs, YbInvokeLog failedLog) {
        if (invokeLogs == null || failedLog == null || failedLog.getCreatedAt() == null) {
            return false;
        }
        LocalDateTime failedAt = failedLog.getCreatedAt();
        for (YbInvokeLog row : invokeLogs) {
            if (row == null || !isSettleInfno(row.getInfno())) {
                continue;
            }
            if (!ChsPlatformCodes.INFCODE_SUCCESS.equals(row.getInfcode())) {
                continue;
            }
            if (row.getCreatedAt() != null && row.getCreatedAt().isAfter(failedAt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 失败 invoke 解析结算回执：仅 msgid 与 {@code msgid_2207} 一致或 invoke 已挂 record_id 时命中。
     *
     * <p>禁止按 chrg_bchno / 单笔回落，避免把「后续重试成功回执」误当作失败 2207 的冲正对象。</p>
     */
    private YbSettlementRecord resolveRecordForFailedInvoke(YbInvokeLog invokeLog, Long tenantId) {
        if (invokeLog.getSettlementRecordId() != null) {
            Optional<YbSettlementRecord> byId = settlementRecordQueryService.findOptionalByIdAndTenant(
                invokeLog.getSettlementRecordId(), tenantId);
            if (byId.isPresent() && this.recordMatchesFailedInvokeMsgid(invokeLog, byId.get())) {
                return byId.get();
            }
            return null;
        }
        if (invokeLog.getPaymentDraftId() == null) {
            return null;
        }
        List<YbSettlementRecord> records = settlementRecordQueryService.listByPaymentId(
            invokeLog.getPaymentDraftId(), tenantId);
        for (YbSettlementRecord record : records) {
            if (record == null || YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                continue;
            }
            if (this.recordMatchesFailedInvokeMsgid(invokeLog, record)) {
                return record;
            }
        }
        return null;
    }

    private boolean recordMatchesFailedInvokeMsgid(YbInvokeLog invokeLog, YbSettlementRecord record) {
        if (invokeLog == null || record == null || isEmpty(invokeLog.getMsgid())) {
            return false;
        }
        String invokeMsgid = invokeLog.getMsgid().trim();
        if (isNotEmpty(record.getMsgid2207()) && invokeMsgid.equals(record.getMsgid2207().trim())) {
            return true;
        }
        return isNotEmpty(record.getMedinsSetlId()) && invokeMsgid.equals(record.getMedinsSetlId().trim());
    }

    /**
     * 2601 人员编号：优先结算回执；无回执时回落预结算快照。
     */
    private String resolvePsnNo(Long paymentId, Long tenantId, YbSettlementRecord record) {
        if (record != null && isNotEmpty(record.getPsnNo())) {
            return record.getPsnNo().trim();
        }
        if (paymentId == null || tenantId == null) {
            return null;
        }
        Optional<YbClinicPreSettle> pre = clinicPreSettleQueryService.findOptionalLatestByPaymentDraftId(
            paymentId, tenantId);
        if (pre.isEmpty() || isEmpty(pre.get().getPsnNo())) {
            return null;
        }
        return pre.get().getPsnNo().trim();
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

}
