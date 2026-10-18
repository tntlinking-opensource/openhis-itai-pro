/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.outpatient.invoker;

import com.zxplat.plugin.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.domain.invoke.command.LinkYbInvokeLogSettlementCommand;
import com.zxplat.plugin.chs.domain.invoke.service.YbInvokeLogDomainService;
import com.zxplat.plugin.chs.domain.outpatient.command.OpConfirmSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.result.OpConfirmSettleResult;
import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.zxplat.plugin.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.zxplat.plugin.chs.domain.settlement.SettlementAmountValidator;
import com.zxplat.plugin.chs.domain.settlement.SettlementInvariantException;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.settlement.model.ChargeBatch;
import com.zxplat.plugin.chs.domain.settlement.model.SettlementAmounts;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementFundDetailDomainService;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementRecordDomainService;
import com.zxplat.plugin.chs.domain.settlement.support.SetlinfoFieldSupport;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextSupport;
import com.zxplat.plugin.chs.gateway.client.InsuranceGatewayClient;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.model.Op2207Input;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.assembler.Op2207InputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.outpatient.parser.Op2207OutputParser;
import com.zxplat.plugin.chs.gateway.support.ChargeBatchInvokeLogSupport;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2207】门诊医保正式结算 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读 yb_clinic_pre_settle，写 yb_settlement_record、yb_settlement_fund_detail、yb_clinic_fee_upload、yb_clinic_pre_settle、yb_invoke_log。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2207Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final YbSettlementFundDetailDomainService fundDetailDomainService;
    private final YbClinicFeeUploadDomainService clinicFeeUploadDomainService;
    private final YbInvokeLogDomainService invokeLogDomainService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;

    /**
     * 发起门诊医保正式结算并落库回执与基金分项。
     *
     * @param command 含 chrgBchno、paymentDraftId、operatorId 及可选金额校验字段
     * @return 正式结算结果（setlId、结算金额及回执 ID）
     */
    public OpConfirmSettleResult invoke(OpConfirmSettleCommand command) {
        if (command.getOperatorId() == null) {
            command.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        }
        if (command.getTenantId() == null) {
            command.setTenantId(TenantUtils.getCurrentTenantId());
        }
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        YbClinicPreSettle pre = clinicPreSettleDomainService.requireForConfirm(command, tenantId);
        ChargeBatch batch = ChargeBatch.fromClinicPreSettle(pre);
        ChsInvokeLogContextHolder.set(ChargeBatchInvokeLogSupport.contextFromBatch(batch, tenantId)
            .setContractId(pre.getContractId())
            .setEncounterId(pre.getEncounterId())
            .setPaymentDraftId(command.getPaymentDraftId()));
        try {
            return doInvoke(command, pre, batch, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 执行 2207 门诊医保正式结算并落库回执与基金分项。
     *
     * @param command 正式结算命令
     * @param pre 预结算快照
     * @param batch 收费批次
     * @param tenantId 租户 ID
     * @return 正式结算结果
     */
    private OpConfirmSettleResult doInvoke(
            OpConfirmSettleCommand command,
            YbClinicPreSettle pre,
            ChargeBatch batch,
            Long tenantId) {
        if (!YbClinicPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照状态非法，请重新预结算");
        }
        PlatformSetlinfoFields setlinfo2206Fields = SetlinfoFieldSupport.build2206SetlinfoFor2207(pre);
        ChsSetlinfoOutput setlinfo2206 = SetlinfoIntegrationMapper.fromPlatformFields(setlinfo2206Fields);

        OpPreSettleCommand ctx = OutpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
        OutpatientSettleCommandAssembler.requireContractConfig(ctx.getContractBusNo());
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(ctx);

        Op2207Input input2207 = Op2207InputAssembler.assemble(
            ctx,
            batch.mdtrtId().value(),
            batch.batchKey(),
            pre.getMedfeeSumamt(),
            setlinfo2206);
        ResultBody body2207 = gatewayClient.invoke(
            ctx.getContractBusNo(), ChsInfNo.OUTPATIENT_SETTLE, input2207, invokeContext);
        ChsSetlinfoOutput setlinfo2207 = Op2207OutputParser.requireSetlinfo(body2207);
        PlatformSetlinfoFields platformSetlinfo2207 = SetlinfoIntegrationMapper.toPlatformFields(setlinfo2207);
        String setldetailJson = SetlinfoFieldSupport.extractSetldetailJson(body2207.getOutput());
        validateSettlementAmounts(platformSetlinfo2207, pre);

        String msgid2207 = ChsInvokeLogContextSupport.currentMsgid();
        YbSettlementRecord record = settlementRecordDomainService.saveOutpatientConfirmed(
            pre, platformSetlinfo2207, setldetailJson, command.getPaymentDraftId(), msgid2207);
        invokeLogDomainService.linkSettlementRecord(new LinkYbInvokeLogSettlementCommand()
            .setInvokeLogId(ChsInvokeLogContextSupport.currentInvokeLogId())
            .setSettlementRecordId(record.getId()));
        fundDetailDomainService.persistFromSetldetail(record.getId(), setldetailJson, record.getTenantId());
        clinicFeeUploadDomainService.markSettledByChrgBchno(batch.batchKey(), tenantId);
        ChsInvokeLogContextHolder.merge(logCtx -> logCtx.setSettlementRecordId(record.getId()));
        clinicPreSettleDomainService.markSettledAndSave(pre);

        return new OpConfirmSettleResult()
            .setSettlementRecordId(record.getId())
            .setAccountId(record.getAccountId())
            .setSetlId(record.getSetlId())
            .setChrgBchno(batch.batchKey())
            .setMdtrtId(batch.mdtrtId().value())
            .setMedfeeSumamt(record.getMedfeeSumamt())
            .setFundPaySumamt(record.getFundPaySumamt())
            .setAcctPay(record.getAcctPay())
            .setPsnCashPay(record.getPsnCashPay());
    }

    /**
     * 校验平台结算金额与预结算快照的一致性。
     *
     * @param setlinfo 平台结算信息
     * @param pre 预结算快照
     */
    private static void validateSettlementAmounts(PlatformSetlinfoFields setlinfo, YbClinicPreSettle pre) {
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
}
