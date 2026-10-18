/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inpatient.workflow;

import com.zxplat.plugin.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.zxplat.plugin.chs.bridge.inpatient.invoker.Ip2301Invoker;
import com.zxplat.plugin.chs.bridge.inpatient.invoker.Ip2303Invoker;
import com.zxplat.plugin.chs.bridge.inpatient.invoker.Ip2401Invoker;
import com.zxplat.plugin.chs.bridge.inpatient.support.IpOrphanUploadedFeeRevokeSupport;
import com.zxplat.plugin.chs.bridge.inpatient.support.IpPlatformReadmitForSettleSupport;
import com.zxplat.plugin.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.zxplat.plugin.chs.bridge.settlement.support.ChsFeeDetailBatchSupport;
import com.zxplat.plugin.chs.bridge.settlement.support.ChsFeeUploadNettingSupport;
import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleChargeLine;
import com.zxplat.plugin.chs.domain.inpatient.command.IpSettleContext;
import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.inpatient.result.IpFeeUploadBatchResult;
import com.zxplat.plugin.chs.domain.inpatient.result.IpPreSettleResult;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientFeeUploadQueryService;
import com.zxplat.plugin.chs.domain.inpatient.service.YbInpatientPreSettleDomainService;
import com.zxplat.plugin.chs.domain.settlement.model.ChargeBatch;
import com.zxplat.plugin.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.zxplat.plugin.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.gateway.client.InvokeContext;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContext;
import com.zxplat.plugin.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.zxplat.plugin.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2301Input;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.model.Ip2303Input;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.parser.Ip2303OutputParser;
import com.zxplat.plugin.chs.gateway.scheme.inpatient.parser.IpFeeUploadResultIndexer;
import com.zxplat.plugin.chs.gateway.support.ChargeBatchInvokeLogSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 2303 前经 {@link IpOrphanUploadedFeeRevokeSupport} 清扫院内已失效仍 UPLOADED 的孤儿明细。
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
    private final IpOrphanUploadedFeeRevokeSupport orphanUploadedFeeRevokeSupport;
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
     * 上传一批待传住院费用明细（2301，最多 {@link ChsFeeDetailBatchSupport#PLATFORM_MAX_FEEDTAIL_ROWS} 行）。
     *
     * <p>供前端进度条分批调用；{@code preferredMdtrtId} 非空时跳过 2401 解析。</p>
     *
     * @param context           已 enrich 的结算上下文
     * @param preferredMdtrtId  上批返回的 mdtrtId（可空）
     * @return 本批进度
     */
    public IpFeeUploadBatchResult uploadPendingFeeBatch(IpSettleContext context, String preferredMdtrtId) {
        InpatientSettleCommandAssembler.validatePreSettleContext(context);
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(context.getTenantId());
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(context.getContractId())
            .setEncounterId(context.getEncounterId())
            .setPaymentDraftId(context.getPaymentDraftId())
            .setTenantId(tenantId));
        try {
            InpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
            InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(context);
            String mdtrtId = StringUtils.isNotEmpty(preferredMdtrtId)
                ? preferredMdtrtId.trim()
                : ip2401Invoker.resolveOrRegister(
                    context.getContractBusNo(), context, invokeContext, tenantId);
            return this.uploadOnePendingFeeBatch(context, mdtrtId, invokeContext, tenantId);
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
            // Pre：清扫院内已删/非待结但仍 UPLOADED 的孤儿明细，避免 2303 明细总额≠医疗费总额
            orphanUploadedFeeRevokeSupport.revokeOrphansBeforePreSettle(
                context, mdtrtId, invokeContext, tenantId);
            uploadFeeDetailsIfNeeded(context, mdtrtId, invokeContext, tenantId);
            Ip2303Input input2303 = IpInpatientSettleInputAssembler.assemble2303(
                context, mdtrtId, medfeeSumamt);
            body2303 = ip2303Invoker.invoke(context.getContractBusNo(), input2303, invokeContext);
        } catch (RuntimeException firstEx) {
            // Pre：本地 map 指向的 mdtrt 在平台已失效（如误办出院/撤销后）→ 重新入院换新号再预结
            if (!isInvalidMdtrtOnPlatform(firstEx)) {
                throw firstEx;
            }
            try {
                mdtrtId = platformReadmitForSettleSupport.readmitAndReupload(context, tenantId);
            } catch (RuntimeException readmitEx) {
                // Asm：换号失败文案优先用 readmit 原因，否则回落首次平台错误
                String readmitCause = readmitEx.getMessage();
                if (readmitCause == null) {
                    readmitCause = firstEx.getMessage();
                }
                throw new BusinessException(
                    ChsErrorCodes.PLATFORM_FAILED,
                    "【死就诊】本地平台就诊号已失效且自动换号失败；请打开「平台在院对齐」软删死映射后重试预结算。原因："
                        + readmitCause,
                    readmitEx);
            }
            // Pre：换号后再次清扫孤儿（旧号残留不应挡新号；本号若仍有失效上传则撤）
            orphanUploadedFeeRevokeSupport.revokeOrphansBeforePreSettle(
                context, mdtrtId, invokeContext, tenantId);
            uploadFeeDetailsIfNeeded(context, mdtrtId, invokeContext, tenantId);
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
        // Pre：预结算路径内部分批直到传完（前端未预传时兜底）
        while (true) {
            IpFeeUploadBatchResult batch =
                this.uploadOnePendingFeeBatch(context, mdtrtId, invokeContext, tenantId);
            if (batch.isDone() || batch.getBatchUploaded() <= 0) {
                break;
            }
        }
    }

    /**
     * 上传一批待传明细并返回进度；无待传时 {@code done=true, batchUploaded=0}。
     */
    private IpFeeUploadBatchResult uploadOnePendingFeeBatch(
            IpSettleContext context,
            String mdtrtId,
            InvokeContext invokeContext,
            Long tenantId) {
        List<IpSettleChargeLine> pendingLines = filterPendingUploadLines(context, tenantId);
        ChsFeeUploadNettingSupport.IpNetUploadPlan netPlan =
            ChsFeeUploadNettingSupport.planIpPendingUploadLines(pendingLines);
        List<IpSettleChargeLine> uploadLines = netPlan.uploadLines();
        if (!netPlan.locallyClosedLines().isEmpty()) {
            inpatientFeeUploadDomainService.persistFrom2301(
                context.getEncounterId(),
                mdtrtId,
                netPlan.locallyClosedLines(),
                Map.of(),
                null,
                tenantId);
        }
        if (uploadLines.isEmpty()) {
            return new IpFeeUploadBatchResult()
                .setBatchUploaded(0)
                .setRemainingAfter(0)
                .setDone(true)
                .setMdtrtId(mdtrtId);
        }
        List<List<IpSettleChargeLine>> partitions = ChsFeeDetailBatchSupport.partition(
            uploadLines, ChsFeeDetailBatchSupport.PLATFORM_MAX_FEEDTAIL_ROWS);
        List<IpSettleChargeLine> batch = partitions.get(0);
        this.invoke2301AndPersist(context, mdtrtId, invokeContext, tenantId, batch);
        int remaining = Math.max(0, uploadLines.size() - batch.size());
        return new IpFeeUploadBatchResult()
            .setBatchUploaded(batch.size())
            .setRemainingAfter(remaining)
            .setDone(remaining == 0)
            .setMdtrtId(mdtrtId);
    }

    /**
     * 单批 2301 出站并落本地上传态；平台重复主键时按幂等对齐本地。
     */
    private void invoke2301AndPersist(
            IpSettleContext context,
            String mdtrtId,
            InvokeContext invokeContext,
            Long tenantId,
            List<IpSettleChargeLine> batchLines) {
        if (batchLines == null || batchLines.isEmpty()) {
            return;
        }
        Ip2301Input input2301 = IpInpatientSettleInputAssembler.assemble2301(context, mdtrtId, batchLines);
        try {
            ResultBody body2301 = ip2301Invoker.invoke(
                context.getContractBusNo(), input2301, invokeContext);
            inpatientFeeUploadDomainService.persistFrom2301(
                context.getEncounterId(),
                mdtrtId,
                batchLines,
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
                batchLines,
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
            // 套餐子项共用父收费项，按本行流水号判断，避免兄弟行被当成已上传跳过
            if (feeUploadQueryService.isUploadedFeedetl(line.getFeedetlSn(), tenantId)) {
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
