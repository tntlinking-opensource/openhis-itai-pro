/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.workflow;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.bridge.inpatient.invoker.Ip2301Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2303Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2401Invoker;
import com.openhis.chs.bridge.inpatient.support.IpPlatformReadmitForSettleSupport;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.chs.bridge.settlement.support.ChsFeeUploadNettingSupport;
import com.openhis.chs.domain.inpatient.command.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.result.IpPreSettleResult;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadQueryService;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleDomainService;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.observability.ChsInvokeLogContext;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2301Input;
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2303Input;
import com.openhis.chs.gateway.scheme.inpatient.parser.Ip2303OutputParser;
import com.openhis.chs.gateway.scheme.inpatient.parser.IpFeeUploadResultIndexer;
import com.openhis.chs.gateway.support.ChargeBatchInvokeLogSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 【2401→2301→2303】住院医保预结算编排。
 *
 * <p>出站由 {@link Ip2401Invoker}、{@link Ip2301Invoker}、{@link Ip2303Invoker} 完成；本类不调 gateway。
 * 写 yb_inpatient_fee_upload、yb_inpatient_pre_settle。
 * 出院办理（2402）不在预结算调用；正式结算前再做 2405。
 * 若本地 mdtrt 在平台已失效（2303「未查询到有效就诊信息」），则兜底重新 2401 并用新就诊号重试。</p>
 */
@Component
@RequiredArgsConstructor
public class IpInpatientPreSettleOrchestrator {

    private final Ip2401Invoker ip2401Invoker;
    private final Ip2301Invoker ip2301Invoker;
    private final Ip2303Invoker ip2303Invoker;
    private final IpPlatformReadmitForSettleSupport platformReadmitForSettleSupport;
    private final YbInpatientFeeUploadQueryService feeUploadQueryService;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;
    private final YbInpatientPreSettleDomainService inpatientPreSettleDomainService;

    /** 单笔住院医保预结算：2401→2301→2303 链式出站并落预结算快照。 */
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
     * 执行 2401→2301→2303 住院医保预结算链并落预结算快照。
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
        BigDecimal medfeeSumamt = context.getMedfeeSumamt();
        ResultBody body2303;
        try {
            uploadFeeDetailsIfNeeded(context, mdtrtId, invokeContext, tenantId);
            Ip2303Input input2303 = IpInpatientSettleInputAssembler.assemble2303(
                context, mdtrtId, medfeeSumamt);
            body2303 = ip2303Invoker.invoke(context.getContractBusNo(), input2303, invokeContext);
        } catch (RuntimeException firstEx) {
            // Pre：本地 map 指向的 mdtrt 在平台已失效（如误办出院/撤销后）→ 重新入院换新号再预结
            if (!isInvalidMdtrtOnPlatform(firstEx)) {
                throw firstEx;
            }
            mdtrtId = platformReadmitForSettleSupport.readmitAndReupload(context, tenantId);
            Ip2303Input retry2303 = IpInpatientSettleInputAssembler.assemble2303(
                context, mdtrtId, medfeeSumamt);
            body2303 = ip2303Invoker.invoke(context.getContractBusNo(), retry2303, invokeContext);
        }
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
            .setPsnCashPay(decimalOr(setlinfo.getPsnCashPay(), medfeeSumamt))
            .setActPayDedc(setlinfo.getActPayDedc())
            .setCvlservFlag(setlinfo.getCvlservFlag())
            .setCvlservPay(setlinfo.getCvlservPay())
            .setHifpPay(setlinfo.getHifpPay())
            .setOthPay(setlinfo.getOthPay());
    }

    /**
     * 2303/2301 是否因平台就诊 ID 已失效被拒。
     *
     * @param ex 平台异常
     * @return 命中无效就诊文案时为 true
     */
    private static boolean isInvalidMdtrtOnPlatform(RuntimeException ex) {
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
     * 上传尚未提交的住院费用明细（2301）。
     *
     * <p>平台已成功而本地因外层回滚丢失上传态时，重试会撞 feeListDBO_41；
     * 此时按幂等落本地 UPLOADED 后继续预结算。</p>
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
        // Pre：未上平台的冲销对只传净量；被轧差行本地记已上传
        ChsFeeUploadNettingSupport.IpNetUploadPlan netPlan =
            ChsFeeUploadNettingSupport.planIpPendingUploadLines(pendingLines);
        List<IpSettleChargeLine> uploadLines = netPlan.uploadLines();
        if (!uploadLines.isEmpty()) {
            Ip2301Input input2301 = IpInpatientSettleInputAssembler.assemble2301(context, mdtrtId, uploadLines);
            try {
                ResultBody body2301 = ip2301Invoker.invoke(
                    context.getContractBusNo(), input2301, invokeContext);
                inpatientFeeUploadDomainService.persistFrom2301(
                    context.getEncounterId(),
                    mdtrtId,
                    uploadLines,
                    IpFeeUploadResultIndexer.indexByFeedetlSn(body2301),
                    body2301.getOutput(),
                    tenantId);
            } catch (RuntimeException ex) {
                // Pre：平台已有同 feedetl_sn（外层回滚导致本地无 UPLOADED）→ 对齐本地后继续 2303
                if (!isPlatformDuplicateFeeUpload(ex)) {
                    throw ex;
                }
                inpatientFeeUploadDomainService.persistFrom2301(
                    context.getEncounterId(),
                    mdtrtId,
                    uploadLines,
                    Map.of(),
                    null,
                    tenantId);
            }
        }
        // Cmd：轧差吃掉的冲销/净零原行本地记 UPLOADED，避免下次再带 init 出站
        if (!netPlan.locallyClosedLines().isEmpty()) {
            inpatientFeeUploadDomainService.persistFrom2301(
                context.getEncounterId(),
                mdtrtId,
                netPlan.locallyClosedLines(),
                Map.of(),
                null,
                tenantId);
        }
    }

    /**
     * 平台 2301 是否因费用流水主键重复失败（本地上传态丢失后的重试典型态）。
     *
     * @param ex 平台调用异常
     * @return 命中重复主键文案或 feeListDBO_41 时为 true
     */
    private static boolean isPlatformDuplicateFeeUpload(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            return false;
        }
        if (msg.contains("feeListDBO_41")) {
            return true;
        }
        return msg.contains("请勿插入重复的主键");
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
