/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;


import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
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
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读 yb_inpatient_pre_settle，写 yb_settlement_record、yb_settlement_fund_detail、yb_inpatient_fee_upload、yb_inpatient_pre_settle、yb_invoke_log。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2304Invoker {

    private final InsuranceGatewayClient gatewayClient;
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
            return doInvoke(command, pre, batch);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 执行 2304 住院医保正式结算并落库回执与基金分项。
     *
     * @param command 正式结算命令
     * @param pre 预结算快照
     * @param batch 收费批次
     * @return 正式结算结果
     */
    private IpConfirmSettleResult doInvoke(
            IpConfirmSettleCommand command,
            YbInpatientPreSettle pre,
            ChargeBatch batch) {
        if (!YbInpatientPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照状态非法，请重新预结算");
        }
        PlatformSetlinfoFields setlinfo2303Fields = SetlinfoFieldSupport.build2303SetlinfoFor2304(pre);
        ChsSetlinfoOutput setlinfo2303 = SetlinfoIntegrationMapper.fromPlatformFields(setlinfo2303Fields);

        IpSettleContext ctx = InpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(ctx);

        Ip2304Input input2304 = IpInpatientSettleInputAssembler.assemble2304(
            ctx, batch.mdtrtId().value(), pre.getMedfeeSumamt(), setlinfo2303);
        ResultBody body2304 = gatewayClient.invoke(
            ctx.getContractBusNo(), ChsInfNo.INPATIENT_SETTLE, input2304, invokeContext);
        ChsSetlinfoOutput setlinfo2304 = Ip2304OutputParser.requireSetlinfo(body2304);
        PlatformSetlinfoFields platformSetlinfo2304 = SetlinfoIntegrationMapper.toPlatformFields(setlinfo2304);
        String setldetailJson = SetlinfoFieldSupport.extractSetldetailJson(body2304.getOutput());
        validateSettlementAmounts(platformSetlinfo2304, pre);

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
            .setMdtrtId(batch.mdtrtId().value())
            .setMedfeeSumamt(decimalOr(setlinfo2304.getMedfeeSumamt(), pre.getMedfeeSumamt()))
            .setFundPaySumamt(decimalOr(setlinfo2304.getFundPaySumamt(), pre.getFundPaySumamt()))
            .setAcctPay(decimalOr(setlinfo2304.getAcctPay(), pre.getAcctPay()))
            .setPsnCashPay(decimalOr(setlinfo2304.getPsnCashPay(), pre.getPsnCashPay()));
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
