/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.workflow;

import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadQueryService;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleDomainService;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.openhis.chs.gateway.observability.ChsInvokeLogContext;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2301Input;
import com.openhis.chs.gateway.scheme.inpatient.parser.Ip2303OutputParser;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2402Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2303Input;
import com.openhis.chs.gateway.scheme.inpatient.parser.IpFeeUploadResultIndexer;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.support.ChargeBatchInvokeLogSupport;
import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.bridge.inpatient.invoker.Ip2301Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2401Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2402Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2303Invoker;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.chs.domain.inpatient.result.IpPreSettleResult;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 【2401→2301→（2402）→2303】住院医保预结算编排。
 *
 * <p>出站由 {@link Ip2401Invoker}、{@link Ip2301Invoker}、{@link Ip2402Invoker}、{@link Ip2303Invoker} 完成；本类不调 gateway。
 * 写 yb_inpatient_fee_upload、yb_inpatient_pre_settle。</p>
 */
@Component
@RequiredArgsConstructor
public class IpInpatientPreSettleOrchestrator {

    private final Ip2401Invoker ip2401Invoker;
    private final Ip2301Invoker ip2301Invoker;
    private final Ip2402Invoker ip2402Invoker;
    private final Ip2303Invoker ip2303Invoker;
    private final YbInpatientFeeUploadQueryService feeUploadQueryService;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;
    private final YbInpatientPreSettleDomainService inpatientPreSettleDomainService;

    /** 单笔住院医保预结算：2401→2301→（2402）→2303 链式出站并落预结算快照。 */
    public IpPreSettleResult invoke(IpSettleContext context) {
        InpatientSettleCommandAssembler.validatePreSettleContext(context);
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(context.getTenantId());
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(context.getContractId())
            .setEncounterId(context.getEncounterId())
            .setPaymentDraftId(context.getPaymentDraftId())
            .setTenantId(tenantId));
        try {
            return doInvoke(context, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 执行 2401→2301→（2402）→2303 住院医保预结算链并落预结算快照。
     *
     * @param context 住院结算上下文
     * @param tenantId 租户 ID
     * @return 预结算结果
     */
    private IpPreSettleResult doInvoke(IpSettleContext context, Long tenantId) {
        inpatientPreSettleDomainService.cancelActivePreSettlesForDraft(context.getPaymentDraftId(), tenantId);
        InpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(context);
        String mdtrtId = ip2401Invoker.resolveOrRegister(
            context.getContractBusNo(), context, invokeContext, tenantId);
        uploadFeeDetailsIfNeeded(context, mdtrtId, invokeContext, tenantId);
        if (IpSettleContext.SETTLE_MODE_DISCHARGE_FULL.equals(context.getSettleMode())) {
            Ip2402Input input2402 = IpInpatientSettleInputAssembler.assemble2402(context, mdtrtId);
            ip2402Invoker.invoke(context.getContractBusNo(), input2402, invokeContext);
        }
        BigDecimal medfeeSumamt = context.getMedfeeSumamt();
        Ip2303Input input2303 = IpInpatientSettleInputAssembler.assemble2303(context, mdtrtId, medfeeSumamt);
        ResultBody body2303 = ip2303Invoker.invoke(
            context.getContractBusNo(), input2303, invokeContext);
        ChsSetlinfoOutput setlinfo = Ip2303OutputParser.requireSetlinfo(body2303);
        PlatformSetlinfoFields platformSetlinfo = SetlinfoIntegrationMapper.toPlatformFields(setlinfo);

        YbInpatientPreSettle row = inpatientPreSettleDomainService.saveActiveFrom2303(
            context, mdtrtId, platformSetlinfo, toChargeItemIdsJson(context), tenantId);
        ChargeBatchInvokeLogSupport.mergeBatch(ChargeBatch.fromInpatientPreSettle(row));

        return new IpPreSettleResult()
            .setPreSettleId(row.getId())
            .setMdtrtId(mdtrtId)
            .setSettleMode(context.getSettleMode())
            .setMidSetlFlag(row.getMidSetlFlag())
            .setMedfeeSumamt(decimalOr(setlinfo.getMedfeeSumamt(), medfeeSumamt))
            .setFundPaySumamt(decimalOr(setlinfo.getFundPaySumamt(), BigDecimal.ZERO))
            .setAcctPay(decimalOr(setlinfo.getAcctPay(), BigDecimal.ZERO))
            .setPsnCashPay(decimalOr(setlinfo.getPsnCashPay(), medfeeSumamt));
    }

    /**
     * 上传尚未提交的住院费用明细（2301）。
     *
     * @param context 住院结算上下文
     * @param mdtrtId 医保就诊 ID
     * @param invokeContext 出站调用上下文
     * @param tenantId 租户 ID
     */
    private void uploadFeeDetailsIfNeeded(
            IpSettleContext context,
            String mdtrtId,
            InvokeContext invokeContext,
            Long tenantId) {
        List<IpSettleChargeLine> pendingLines = filterPendingUploadLines(context, tenantId);
        if (pendingLines.isEmpty()) {
            return;
        }
        Ip2301Input input2301 = IpInpatientSettleInputAssembler.assemble2301(context, mdtrtId, pendingLines);
        ResultBody body2301 = ip2301Invoker.invoke(
            context.getContractBusNo(), input2301, invokeContext);
        inpatientFeeUploadDomainService.persistFrom2301(
            context.getEncounterId(),
            mdtrtId,
            pendingLines,
            IpFeeUploadResultIndexer.indexByFeedetlSn(body2301),
            body2301.getOutput(),
            tenantId);
    }

    /**
     * 筛选尚未上传平台的住院收费明细行。
     *
     * @param context 住院结算上下文
     * @param tenantId 租户 ID
     * @return 待上传的收费明细行列表
     */
    private List<IpSettleChargeLine> filterPendingUploadLines(IpSettleContext context, Long tenantId) {
        List<IpSettleChargeLine> pending = new ArrayList<>();
        for (IpSettleChargeLine line : context.getChargeLines()) {
            if (feeUploadQueryService.isUploaded(line.getChargeItemId(), tenantId)) {
                continue;
            }
            pending.add(line);
        }
        return pending;
    }

    /**
     * 将收费明细行的 chargeItemId 序列化为 JSON 数组字符串。
     *
     * @param context 住院结算上下文
     * @return JSON 数组字符串；无明细时返回 null
     */
    private static String toChargeItemIdsJson(IpSettleContext context) {
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>(context.getChargeLines().size());
        for (IpSettleChargeLine line : context.getChargeLines()) {
            ids.add(line.getChargeItemId());
        }
        return ChargeItemIdsSupport.toJsonArray(ids);
    }

    /**
     * 取非空金额，否则回退默认值。
     *
     * @param value 首选金额
     * @param fallback 回退金额
     * @return 非空金额
     */
    private static BigDecimal decimalOr(BigDecimal value, BigDecimal fallback) {
        if (value != null) {
            return value;
        }
        return fallback;
    }
}
