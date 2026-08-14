/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;


import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.bridge.inpatient.support.IpPlatformDischargeRevokeSupport;
import com.openhis.chs.bridge.inpatient.support.IpPlatformReadmitForSettleSupport;
import com.openhis.chs.domain.inpatient.command.IpConfirmSettleCommand;
import com.openhis.chs.domain.inpatient.result.IpConfirmSettleResult;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleDomainService;
import com.openhis.chs.domain.invoke.command.LinkYbInvokeLogSettlementCommand;
import com.openhis.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.SettlementAmountValidator;
import com.openhis.chs.domain.settlement.SettlementInvariantException;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import com.openhis.chs.domain.settlement.model.SettlementAmounts;
import com.openhis.chs.domain.settlement.service.YbSettlementFundDetailDomainService;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordDomainService;
import com.openhis.chs.domain.settlement.support.SetlinfoFieldSupport;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextSupport;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2304Input;
import com.openhis.chs.gateway.scheme.inpatient.parser.Ip2304OutputParser;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.support.ChargeBatchInvokeLogSupport;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2304】住院医保正式结算 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读 yb_inpatient_pre_settle，写 yb_settlement_record、yb_settlement_fund_detail、yb_inpatient_fee_upload、yb_inpatient_pre_settle、yb_invoke_log。
 * 出院全结：确认前经 {@link IpPlatformDischargeRevokeSupport#ensureDischargedForSettle} 先 2402 离院再 2304；
 * 若旧就诊无法恢复则 {@link IpPlatformReadmitForSettleSupport} 重新 2401，再 2402→2304；
 * Extension 在 2304 后再调 2402 时幂等忽略。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2304Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final IpPlatformDischargeRevokeSupport platformDischargeRevokeSupport;
    private final IpPlatformReadmitForSettleSupport platformReadmitForSettleSupport;
    private final YbInpatientPreSettleDomainService inpatientPreSettleDomainService;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final YbSettlementFundDetailDomainService fundDetailDomainService;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;
    private final YbInvokeLogDomainService invokeLogDomainService;

    /**
     * 发起住院医保正式结算并落库回执与基金分项。
     *
     * @param command 含 chrgBchno、paymentDraftId、operatorId 及可选金额校验字段
     * @return 正式结算结果（setlId、结算金额及回执 ID）
     */
    public IpConfirmSettleResult invoke(IpConfirmSettleCommand command) {
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        YbInpatientPreSettle pre = inpatientPreSettleDomainService.requireForConfirm(command, tenantId);
        ChargeBatch batch = ChargeBatch.fromInpatientPreSettle(pre);
        ChsInvokeLogContextHolder.set(ChargeBatchInvokeLogSupport.contextFromBatch(batch, tenantId)
            .setContractId(pre.getContractId())
            .setEncounterId(pre.getEncounterId())
            .setPaymentDraftId(command.getPaymentDraftId()));
        try {
            return doInvoke(command, pre);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 执行 2304 住院医保正式结算并落库回执与基金分项。
     *
     * @param command 正式结算命令
     * @param pre 预结算快照
     * @return 正式结算结果
     */
    private IpConfirmSettleResult doInvoke(
            IpConfirmSettleCommand command,
            YbInpatientPreSettle pre) {
        if (!YbInpatientPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照状态非法，请重新预结算");
        }

        this.preparePlatformVisitOrReadmit(command, pre);

        String mdtrtId = pre.getMdtrtId();
        IpSettleContext ctx = InpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
        ctx.setOperatorId(command.getOperatorId());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(ctx);

        PlatformSetlinfoFields setlinfo2303Fields = SetlinfoFieldSupport.build2303SetlinfoFor2304(pre);
        ChsSetlinfoOutput setlinfo2303 = SetlinfoIntegrationMapper.fromPlatformFields(setlinfo2303Fields);
        Ip2304Input input2304 = IpInpatientSettleInputAssembler.assemble2304(
            ctx, mdtrtId, pre.getMedfeeSumamt(), setlinfo2303);

        ResultBody body2304;
        try {
            body2304 = gatewayClient.invoke(
                ctx.getContractBusNo(), ChsInfNo.INPATIENT_SETTLE, input2304, invokeContext);
            Ip2304OutputParser.requireSetlinfo(body2304);
        } catch (RuntimeException firstEx) {
            if (isInHospitalBlockingSettle(firstEx)) {
                // Pre：长春出院全结要求已离院；仍在院则补 2402 后再结
                IpSettleContext fullCtx = platformReadmitForSettleSupport
                    .buildFullContextFromPre(pre, command.getOperatorId());
                platformDischargeRevokeSupport.ensureDischargedForSettle(fullCtx, pre.getMdtrtId());
                mdtrtId = pre.getMdtrtId();
                ctx = InpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
                ctx.setOperatorId(command.getOperatorId());
                invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(ctx);
                setlinfo2303Fields = SetlinfoFieldSupport.build2303SetlinfoFor2304(pre);
                setlinfo2303 = SetlinfoIntegrationMapper.fromPlatformFields(setlinfo2303Fields);
                input2304 = IpInpatientSettleInputAssembler.assemble2304(
                    ctx, mdtrtId, pre.getMedfeeSumamt(), setlinfo2303);
                body2304 = gatewayClient.invoke(
                    ctx.getContractBusNo(), ChsInfNo.INPATIENT_SETTLE, input2304, invokeContext);
            } else if (isDischargedBlockingSettle(firstEx)) {
                // Pre：就诊不可用/离院死锁 → 重新入院后立刻 2402，再 2304
                platformReadmitForSettleSupport.readmitAndRefreshPreSettle(pre, command.getOperatorId());
                IpSettleContext fullCtx = platformReadmitForSettleSupport
                    .buildFullContextFromPre(pre, command.getOperatorId());
                platformDischargeRevokeSupport.ensureDischargedForSettle(fullCtx, pre.getMdtrtId());
                mdtrtId = pre.getMdtrtId();
                ctx = InpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
                ctx.setOperatorId(command.getOperatorId());
                invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(ctx);
                setlinfo2303Fields = SetlinfoFieldSupport.build2303SetlinfoFor2304(pre);
                setlinfo2303 = SetlinfoIntegrationMapper.fromPlatformFields(setlinfo2303Fields);
                input2304 = IpInpatientSettleInputAssembler.assemble2304(
                    ctx, mdtrtId, pre.getMedfeeSumamt(), setlinfo2303);
                body2304 = gatewayClient.invoke(
                    ctx.getContractBusNo(), ChsInfNo.INPATIENT_SETTLE, input2304, invokeContext);
            } else {
                throw firstEx;
            }
        }
        ChsSetlinfoOutput setlinfo2304 = Ip2304OutputParser.requireSetlinfo(body2304);
        PlatformSetlinfoFields platformSetlinfo2304 = SetlinfoIntegrationMapper.toPlatformFields(setlinfo2304);
        String setldetailJson = SetlinfoFieldSupport.extractSetldetailJson(body2304.getOutput());
        validateSettlementAmounts(platformSetlinfo2304, pre);

        ChargeBatch batch = ChargeBatch.fromInpatientPreSettle(pre);
        String chrgBchno = batch.batchKey();
        String msgid2304 = ChsInvokeLogContextSupport.currentMsgid();
        YbSettlementRecord record = settlementRecordDomainService.saveInpatientConfirmed(
            pre, chrgBchno, platformSetlinfo2304, setldetailJson, command.getPaymentDraftId(), msgid2304);
        invokeLogDomainService.linkSettlementRecord(new LinkYbInvokeLogSettlementCommand()
            .setInvokeLogId(ChsInvokeLogContextSupport.currentInvokeLogId())
            .setSettlementRecordId(record.getId()));
        fundDetailDomainService.persistFromSetldetail(record.getId(), setldetailJson, record.getTenantId());
        inpatientFeeUploadDomainService.markSettledByPreSettle(pre, pre.getTenantId());
        ChsInvokeLogContextHolder.merge(logCtx -> {
            logCtx.setChrgBchno(chrgBchno);
            logCtx.setSettlementRecordId(record.getId());
        });
        pre.setSetlId(setlinfo2304.getSetlId());
        inpatientPreSettleDomainService.markSettledAndSave(pre);

        return new IpConfirmSettleResult()
            .setSettlementRecordId(record.getId())
            .setPreSettleId(pre.getId())
            .setSetlId(setlinfo2304.getSetlId())
            .setChrgBchno(chrgBchno)
            .setMdtrtId(mdtrtId)
            .setMedfeeSumamt(decimalOr(setlinfo2304.getMedfeeSumamt(), pre.getMedfeeSumamt()))
            .setFundPaySumamt(decimalOr(setlinfo2304.getFundPaySumamt(), pre.getFundPaySumamt()))
            .setAcctPay(decimalOr(setlinfo2304.getAcctPay(), pre.getAcctPay()))
            .setPsnCashPay(decimalOr(setlinfo2304.getPsnCashPay(), pre.getPsnCashPay()));
    }

    /**
     * 出院全结确认前先 2402 离院；就诊失效时重新入院后再离院。
     *
     * @param command 确认命令
     * @param pre     预结算快照（可能被刷新 mdtrt）
     */
    private void preparePlatformVisitOrReadmit(
            IpConfirmSettleCommand command,
            YbInpatientPreSettle pre) {
        IpSettleContext fullCtx = platformReadmitForSettleSupport
            .buildFullContextFromPre(pre, command.getOperatorId());
        try {
            platformDischargeRevokeSupport.ensureDischargedForSettle(fullCtx, pre.getMdtrtId());
        } catch (RuntimeException ex) {
            if (!isReadmitHint(ex) && !isInvalidMdtrtHint(ex)) {
                throw ex;
            }
            platformReadmitForSettleSupport.readmitAndRefreshPreSettle(pre, command.getOperatorId());
            fullCtx = platformReadmitForSettleSupport
                .buildFullContextFromPre(pre, command.getOperatorId());
            platformDischargeRevokeSupport.ensureDischargedForSettle(fullCtx, pre.getMdtrtId());
        }
    }

    /**
     * 是否提示需要重新入院的业务异常。
     *
     * @param ex 异常
     * @return 命中时为 true
     */
    private static boolean isReadmitHint(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        return msg.contains("无法撤销本就诊出院")
            || msg.contains("自动撤销多余在院")
            || msg.contains("重新入院前无法撤销")
            || msg.contains("无法办理出院");
    }

    /**
     * 平台就诊号是否已失效。
     *
     * @param ex 异常
     * @return 命中时为 true
     */
    private static boolean isInvalidMdtrtHint(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("未查询到有效就诊信息")) {
            return true;
        }
        if (msg.contains("无有效就诊")) {
            return true;
        }
        return msg.contains("就诊信息不存在");
    }

    /**
     * 2304 是否因人员仍在院被平台拒绝（出院全结须先 2402）。
     *
     * @param ex 平台调用异常
     * @return 命中在院拦截文案时为 true
     */
    private static boolean isInHospitalBlockingSettle(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("该人员是在院状态")) {
            return true;
        }
        return msg.contains("是在院状态");
    }

    /**
     * 2304 是否因人员已离院且就诊不可结被平台拒绝。
     *
     * <p>注意：勿用笼统 {@code iptPsnSetlMgtBO_3021}——同错误码也会返回「在院状态」。</p>
     *
     * @param ex 平台调用异常
     * @return 命中离院拦截文案时为 true
     */
    private static boolean isDischargedBlockingSettle(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("该人员是离院状态")) {
            return true;
        }
        if (msg.contains("是离院状态")) {
            return true;
        }
        // Pre：仅「离院」语义，避免把「在院」3021 误判为离院后反复读入
        return msg.contains("离院状态") && !msg.contains("在院状态");
    }

    /**
     * 校验平台结算金额与预结算快照的一致性。
     *
     * @param setlinfo 平台结算信息
     * @param pre 预结算快照
     */
    private static void validateSettlementAmounts(PlatformSetlinfoFields setlinfo, YbInpatientPreSettle pre) {
        if (setlinfo == null) {
            return;
        }
        try {
            SettlementAmounts amounts = SettlementAmountValidator.fromPlatformFields(setlinfo);
            amounts.assertMedfeeBalanced();
            if (pre != null) {
                amounts.assert2207PassthroughFrom(pre);
            }
        } catch (SettlementInvariantException ex) {
            throw new BusinessException(ex.getErrorCode(), ex.getMessage());
        }
    }

    /**
     * 取非空金额，否则回退默认值。
     *
     * @param value 首选金额
     * @param fallback 回退金额
     * @return 非空金额
     */
    private static java.math.BigDecimal decimalOr(java.math.BigDecimal value, java.math.BigDecimal fallback) {
        if (value != null) {
            return value;
        }
        return fallback;
    }
}
