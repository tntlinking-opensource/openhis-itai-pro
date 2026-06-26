/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.inpatient;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.chs.application.gateway.ChsInvokeLogContext;
import com.openhis.chs.application.gateway.ChsInvokeLogContextHolder;
import com.openhis.chs.application.gateway.ChsInvokeLogContextSupport;
import com.openhis.chs.application.gateway.YbInvokeLogWriter;
import com.openhis.chs.application.inpatient.assembler.ClinicInpatientPlatformAssembler;
import com.openhis.chs.application.outpatient.support.ChsChargeItemIdsSupport;
import com.openhis.chs.application.outpatient.support.ChsPlatformResultSupport;
import com.openhis.chs.application.outpatient.support.ChsSettlementRecordLookup;
import com.openhis.chs.application.outpatient.support.ChsSetlinfoSupport;
import com.openhis.chs.application.settlement.ChargeBatchInvokeLogSupport;
import com.openhis.chs.application.settlement.YbEncounterPlatformMapWriter;
import com.openhis.chs.application.settlement.YbSettlementFundDetailWriter;
import com.openhis.chs.application.settlement.YbSettlementReversalWriter;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import com.openhis.chs.domain.settlement.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.SettlementAmountValidator;
import com.openhis.chs.domain.settlement.SettlementInvariantException;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.domain.settlement.model.SettlementAmounts;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.support.FeeUploadResultApplier;
import com.openhis.chs.domain.settlement.support.FeeUploadResultIndexer;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.inpatient.constants.ChsInpatientConstants;
import com.openhis.chs.domain.inpatient.dto.IpAdmissionResult;
import com.openhis.chs.domain.inpatient.dto.IpConfirmSettleCommand;
import com.openhis.chs.domain.inpatient.dto.IpConfirmSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpEncounterPlatformCommand;
import com.openhis.chs.domain.inpatient.dto.IpPreSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeFeeDetailResult;
import com.openhis.chs.domain.inpatient.dto.IpRevokeSettleCommand;
import com.openhis.chs.domain.inpatient.dto.IpRevokeSettleResult;
import com.openhis.chs.domain.inpatient.dto.IpSettleChargeLine;
import com.openhis.chs.domain.inpatient.dto.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.facade.ChsInpatientSettleOperations;
import com.openhis.chs.domain.inpatient.repository.YbInpatientFeeUploadRepository;
import com.openhis.chs.domain.inpatient.repository.YbInpatientPreSettleRepository;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 住院医保结算编排（AS/IF 对应）：
 * <ul>
 *   <li>AS-IP-FIN-001 住院登记 / AS-IP-FIN-003 住院结算 经 Extension/Port 委托本类</li>
 *   <li>IF-IP-REG-03 {@code submitRegistration} 医保入院 → {@link #ensureAdmission}</li>
 *   <li>IF-IP-STL-05 {@code preSettleInpatientCharges} → {@link #preSettle}</li>
 *   <li>IF-IP-STL-06 {@code confirmInpatientSettle} → {@link #confirmSettle}</li>
 *   <li>2305/2302/2404/2405/2403 撤销与变更 → {@link #revokeSettle} 等</li>
 * </ul>
 * 平台链路：2401→2301→(2402)→2303 预结算，2304 正式结算；报文组装见 {@link ClinicInpatientPlatformAssembler}。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsInpatientSettleAppService implements ChsInpatientSettleOperations {

    private final InsuranceGatewayClient gatewayClient;
    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;
    private final YbEncounterPlatformMapWriter encounterPlatformMapWriter;
    private final YbInpatientFeeUploadRepository feeUploadRepository;
    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;
    private final YbSettlementRecordRepository settlementRecordRepository;
    private final YbSettlementFundDetailWriter fundDetailWriter;
    private final YbSettlementReversalWriter settlementReversalWriter;
    private final ChargeBatchLifecycle chargeBatchLifecycle;
    private final YbInvokeLogWriter invokeLogWriter;

    /**
     * 【2401】确保医保入院登记；已有 mdtrt_id 映射时直接返回。
     *
     * @param context 入院上下文（契约、就诊、读卡字段等）
     * @return 医保就诊 ID（mdtrt_id）
     * @throws BusinessException 上下文非法或 2401 平台拒绝时
     */
    @Override
    public IpAdmissionResult ensureAdmission(IpSettleContext context) {
        // Gate：校验 contractId、encounterId、psnNo 与就诊凭证
        validateAdmissionContext(context);
        // Pre：契约配置与 invoke 上下文
        ContractConfig config = requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = buildInvokeContext(context);
        String mdtrtId = resolveMdtrtId(context, config, invokeContext);
        // Asm：返回 mdtrt_id 供 HIS 登记流程回填
        IpAdmissionResult result = new IpAdmissionResult();
        result.setMdtrtId(mdtrtId);
        return result;
    }

    /**
     * 【2301→(2402)→2303】住院医保预结算；必要时先 2401 入院登记。
     *
     * @param context 结算上下文（含 settleMode、收费明细、paymentDraftId 等）
     * @return 预结算结果（含 preSettleId、mdtrtId、midSetlFlag 及分摊金额）
     * @throws BusinessException 上下文非法或平台拒绝时
     */
    @Override
    public IpPreSettleResult preSettle(IpSettleContext context) {
        // Gate：校验 contractId、psnNo、settleMode 与收费明细
        validatePreSettleContext(context);
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(context.getContractId())
            .setEncounterId(context.getEncounterId())
            .setPaymentDraftId(context.getPaymentDraftId())
            .setTenantId(resolveTenantId(context.getTenantId())));
        try {
            return doPreSettle(context);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    private IpPreSettleResult doPreSettle(IpSettleContext context) {
        Long tenantId = resolveTenantId(context.getTenantId());
        // Pre：作废同 draft 下旧的 ACTIVE 预结算，避免同一草稿多笔有效报价
        cancelActivePreSettlesForDraft(context.getPaymentDraftId(), tenantId);
        ContractConfig config = requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = buildInvokeContext(context);
        String mdtrtId = resolveMdtrtId(context, config, invokeContext);
        // Pre：2301 上传尚未 UPLOADED 的费用行（增量上传，已上传行跳过）
        uploadFeeDetailsIfNeeded(context, mdtrtId, invokeContext);
        // 出院全结须先 2402 出院登记，平台才接受 2303
        if (IpSettleContext.SETTLE_MODE_DISCHARGE_FULL.equals(context.getSettleMode())) {
            JSONObject input2402 = ClinicInpatientPlatformAssembler.assemble2402(context, mdtrtId);
            ResultBody body2402 = gatewayClient.invoke(
                context.getContractBusNo(), ChsInfNo.INPATIENT_DISCHARGE, input2402, invokeContext);
            ChsPlatformResultSupport.requireSuccess(body2402, ChsInfNo.INPATIENT_DISCHARGE);
        }

        // Cmd：2303 住院预结算
        BigDecimal medfeeSumamt = context.getMedfeeSumamt();
        JSONObject input2303 = ClinicInpatientPlatformAssembler.assemble2303(context, mdtrtId, medfeeSumamt);
        ResultBody body2303 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.INPATIENT_PRE_SETTLE, input2303, invokeContext);
        JSONObject setlinfo = ChsPlatformResultSupport.require2303Setlinfo(body2303);

        // Asm：持久化预结算快照并组装返回 DTO
        YbInpatientPreSettle row = persistPreSettle(context, mdtrtId, setlinfo);
        ChargeBatchInvokeLogSupport.mergeBatch(ChargeBatch.fromInpatientPreSettle(row));
        IpPreSettleResult result = new IpPreSettleResult();
        result.setPreSettleId(row.getId());
        result.setMdtrtId(mdtrtId);
        result.setSettleMode(context.getSettleMode());
        result.setMidSetlFlag(row.getMidSetlFlag());
        result.setMedfeeSumamt(decimal(setlinfo, "medfee_sumamt", medfeeSumamt));
        result.setFundPaySumamt(decimal(setlinfo, "fund_pay_sumamt", BigDecimal.ZERO));
        result.setAcctPay(decimal(setlinfo, "acct_pay", BigDecimal.ZERO));
        result.setPsnCashPay(decimal(setlinfo, "psn_cash_pay", medfeeSumamt));
        return result;
    }

    /**
     * 【2304】住院医保正式结算；须先有 ACTIVE 预结算快照。
     *
     * @param command 含 preSettleId 或 paymentDraftId
     * @return 正式结算结果（含 settlementRecordId、setl_id 及分摊金额）
     * @throws BusinessException 预结算快照不存在、状态非法或平台拒绝时
     */
    @Override
    public IpConfirmSettleResult confirmSettle(IpConfirmSettleCommand command) {
        // Pre：解析租户并定位 ACTIVE 预结算快照
        Long tenantId = resolveTenantId(command.getTenantId());
        YbInpatientPreSettle pre = findPreSettle(command, tenantId);
        ChargeBatch batch = ChargeBatch.fromInpatientPreSettle(pre);
        ChsInvokeLogContextHolder.set(ChargeBatchInvokeLogSupport.contextFromBatch(batch, tenantId)
            .setContractId(pre.getContractId())
            .setEncounterId(pre.getEncounterId())
            .setPaymentDraftId(command.getPaymentDraftId()));
        try {
            return doConfirmSettle(command, pre, batch);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    private IpConfirmSettleResult doConfirmSettle(
        IpConfirmSettleCommand command,
        YbInpatientPreSettle pre,
        ChargeBatch batch) {
        // Gate：仅 ACTIVE 快照可转正式结算
        if (!YbInpatientPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照状态非法，请重新预结算");
        }
        JSONObject setlinfo2303 = ChsSetlinfoSupport.build2303SetlinfoFor2304(pre);

        IpSettleContext ctx = rebuildContextFromPreSettle(pre);
        InvokeContext invokeContext = buildInvokeContext(ctx);

        // Cmd：2304 正式结算
        JSONObject input2304 = ClinicInpatientPlatformAssembler.assemble2304(
            ctx,
            batch.mdtrtId().value(),
            pre.getMedfeeSumamt(),
            setlinfo2303);
        ResultBody body2304 = gatewayClient.invoke(
            ctx.getContractBusNo(), ChsInfNo.INPATIENT_SETTLE, input2304, invokeContext);
        JSONObject setlinfo2304 = ChsPlatformResultSupport.require2304Setlinfo(body2304);
        String setldetailJson = ChsSetlinfoSupport.extractSetldetailJson(body2304.getOutput());
        validateSettlementAmounts(setlinfo2304, pre);

        // Asm：落结算回执并将预结算快照标记为已结算
        String chrgBchno = batch.batchKey();
        YbSettlementRecord record = persistSettlement(
            pre, chrgBchno, setlinfo2304, setldetailJson, command.getPaymentDraftId());
        markInpatientFeesSettled(pre, pre.getTenantId());
        ChsInvokeLogContextHolder.merge(ctxLog -> {
            ctxLog.setChrgBchno(chrgBchno);
            ctxLog.setSettlementRecordId(record.getId());
        });
        chargeBatchLifecycle.markInpatientPreSettleSettled(pre);
        pre.setSetlId(text(setlinfo2304, "setl_id"));
        inpatientPreSettleRepository.save(pre);

        IpConfirmSettleResult result = new IpConfirmSettleResult();
        result.setSettlementRecordId(record.getId());
        result.setPreSettleId(pre.getId());
        result.setSetlId(text(setlinfo2304, "setl_id"));
        result.setChrgBchno(chrgBchno);
        result.setMdtrtId(batch.mdtrtId().value());
        result.setMedfeeSumamt(decimal(setlinfo2304, "medfee_sumamt", pre.getMedfeeSumamt()));
        result.setFundPaySumamt(decimal(setlinfo2304, "fund_pay_sumamt", pre.getFundPaySumamt()));
        result.setAcctPay(decimal(setlinfo2304, "acct_pay", pre.getAcctPay()));
        result.setPsnCashPay(decimal(setlinfo2304, "psn_cash_pay", pre.getPsnCashPay()));
        return result;
    }

    /**
     * 【2305】撤销住院正式结算。
     *
     * @param command 含 settlementRecordId、paymentId 或 setlId 之一，可选 preSettleId
     * @return 撤销结果（含原结算回执 id、setl_id、mdtrt_id）
     * @throws BusinessException 结算回执不存在、无法解析 contractBusNo 或平台拒绝时
     */
    @Override
    public IpRevokeSettleResult revokeSettle(IpRevokeSettleCommand command) {
        // Pre：定位结算回执
        Long tenantId = resolveTenantId(command.getTenantId());
        YbSettlementRecord record = findSettlementRecord(command, tenantId);
        // Gate：已撤销回执不可重复 2305
        if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
        }
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(record.getContractId())
            .setEncounterId(record.getEncounterId())
            .setChrgBchno(record.getChrgBchno())
            .setPreSettleId(record.getPreSettleId())
            .setSettlementRecordId(record.getId())
            .setTenantId(tenantId));
        try {
            return doRevokeSettle(command, record, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    private IpRevokeSettleResult doRevokeSettle(
        IpRevokeSettleCommand command,
        YbSettlementRecord record,
        Long tenantId) {
        YbInpatientPreSettle pre = resolvePreSettleForRevoke(record, command, tenantId);
        String contractBusNo = resolveContractBusNoForRevoke(record, pre, tenantId);
        IpSettleContext ctx;
        // 有预结算快照时复用完整上下文，否则从回执构建最小上下文
        if (pre != null) {
            ctx = rebuildContextFromPreSettle(pre);
        } else {
            ctx = buildMinimalContext(record, tenantId);
        }
        ctx.setContractBusNo(contractBusNo);
        InvokeContext invokeContext = buildInvokeContext(ctx);

        // Cmd：2305 撤销正式结算
        JSONObject input2305 = ClinicInpatientPlatformAssembler.assemble2305(
            record.getMdtrtId(), record.getSetlId(), record.getPsnNo());
        ResultBody body2305 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_SETTLE_REVOKE, input2305, invokeContext);
        JSONObject revokeSetlinfo = ChsPlatformResultSupport.require2305Setlinfo(body2305);
        String setldetailJson = ChsSetlinfoSupport.extractSetldetailJson(body2305.getOutput());

        settlementReversalWriter.persistReversal(
            record, revokeSetlinfo, setldetailJson, command.getOperatorId(),
            ChsInvokeLogContextSupport.currentInvokeLogId());
        settlementRecordRepository.save(record);
        // 关联预结算存在时同步更新状态
        if (pre != null) {
            chargeBatchLifecycle.markInpatientPreSettleRevoked(pre);
            inpatientPreSettleRepository.save(pre);
            markInpatientFeesRevoked(pre, tenantId);
        }

        IpRevokeSettleResult result = new IpRevokeSettleResult();
        result.setSettlementRecordId(record.getId());
        result.setSetlId(record.getSetlId());
        result.setMdtrtId(record.getMdtrtId());
        return result;
    }

    /**
     * 【2302】撤销已上传的住院费用明细。
     *
     * @param command 含 encounterId、mdtrtId、psnNo 及 chargeItemIds
     * @return 撤销条数；无已上传明细时 revokedCount=0
     * @throws BusinessException 平台参数非法或平台拒绝时
     */
    @Override
    public IpRevokeFeeDetailResult revokeFeeDetails(IpRevokeFeeDetailCommand command) {
        // Gate：校验 encounterId、contractBusNo、mdtrtId、psnNo
        validateEncounterPlatformCommand(command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        Long tenantId = resolveTenantId(command.getTenantId());
        List<YbInpatientFeeUpload> uploads = resolveUploadedFees(command, tenantId);
        // 无已上传明细时跳过平台调用
        if (uploads.isEmpty()) {
            IpRevokeFeeDetailResult empty = new IpRevokeFeeDetailResult();
            empty.setRevokedCount(0);
            return empty;
        }
        List<String> feedetlSnList = new ArrayList<>();
        for (YbInpatientFeeUpload upload : uploads) {
            feedetlSnList.add(upload.getFeedetlSn());
        }
        InvokeContext invokeContext = buildInvokeContext(command.getOperatorId(), command.getInsuplcAdmdvs());

        // Cmd：2302 撤销费用明细
        JSONObject input2302 = ClinicInpatientPlatformAssembler.assemble2302(
            command.getMdtrtId(), command.getPsnNo(), feedetlSnList);
        ResultBody body2302 = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.INPATIENT_FEE_REVOKE, input2302, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2302, ChsInfNo.INPATIENT_FEE_REVOKE);

        // Asm：标记本地上传记录为已撤销
        LocalDateTime now = LocalDateTime.now();
        for (YbInpatientFeeUpload upload : uploads) {
            chargeBatchLifecycle.markInpatientFeeRevoked(upload);
            upload.setUploadedAt(now);
            feeUploadRepository.save(upload);
        }
        IpRevokeFeeDetailResult result = new IpRevokeFeeDetailResult();
        result.setRevokedCount(uploads.size());
        return result;
    }

    /**
     * 【2404】撤销医保入院登记，并软删本地就诊平台映射。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo
     * @throws BusinessException 平台参数非法或平台拒绝时
     */
    @Override
    public void revokeAdmission(IpEncounterPlatformCommand command) {
        // Gate：校验 encounterId、contractBusNo、mdtrtId、psnNo
        validateEncounterPlatformCommand(command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        InvokeContext invokeContext = buildInvokeContext(command.getOperatorId(), command.getInsuplcAdmdvs());

        // Cmd：2404 入院撤销
        JSONObject input2404 = ClinicInpatientPlatformAssembler.assemble2404Or2405(
            command.getMdtrtId(), command.getPsnNo());
        ResultBody body2404 = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.INPATIENT_ADMISSION_REVOKE, input2404, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2404, ChsInfNo.INPATIENT_ADMISSION_REVOKE);

        // Asm：软删本地 encounter→mdtrt_id 映射
        softDeleteEncounterPlatformMap(command, resolveTenantId(command.getTenantId()));
    }

    /**
     * 【2405】撤销医保出院登记。
     *
     * @param command 含 encounterId、contractBusNo、mdtrtId、psnNo
     * @throws BusinessException 平台参数非法或平台拒绝时
     */
    @Override
    public void revokeDischarge(IpEncounterPlatformCommand command) {
        // Gate：校验 encounterId、contractBusNo、mdtrtId、psnNo
        validateEncounterPlatformCommand(command.getEncounterId(), command.getContractBusNo(),
            command.getMdtrtId(), command.getPsnNo());
        InvokeContext invokeContext = buildInvokeContext(command.getOperatorId(), command.getInsuplcAdmdvs());

        // Cmd：2405 出院撤销
        JSONObject input2405 = ClinicInpatientPlatformAssembler.assemble2404Or2405(
            command.getMdtrtId(), command.getPsnNo());
        ResultBody body2405 = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.INPATIENT_DISCHARGE_REVOKE, input2405, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2405, ChsInfNo.INPATIENT_DISCHARGE_REVOKE);
    }

    /**
     * 【2403】变更住院医保登记信息（须已完成 2401 入院）。
     *
     * @param context 含变更后的入院/就诊信息及读卡字段
     * @throws BusinessException 上下文非法、未找到 mdtrt_id 或平台拒绝时
     */
    @Override
    public void changeInpatientInfo(IpSettleContext context) {
        // Gate：校验 contractId、encounterId、psnNo 与就诊凭证
        validateAdmissionContext(context);
        requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = buildInvokeContext(context);
        String mdtrtId = resolveExistingMdtrtId(context);

        // Cmd：2403 住院信息变更
        JSONObject input2403 = ClinicInpatientPlatformAssembler.assemble2403(context, mdtrtId);
        ResultBody body2403 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.INPATIENT_INFO_CHANGE, input2403, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2403, ChsInfNo.INPATIENT_INFO_CHANGE);
    }

    private void uploadFeeDetailsIfNeeded(IpSettleContext context, String mdtrtId, InvokeContext invokeContext) {
        List<IpSettleChargeLine> pendingLines = filterPendingUploadLines(context);
        // 全部费用行已 UPLOADED 则跳过 2301
        if (pendingLines.isEmpty()) {
            return;
        }
        // Cmd：2301 住院费用明细上传
        JSONObject input2301 = ClinicInpatientPlatformAssembler.assemble2301(context, mdtrtId, pendingLines);
        ResultBody body2301 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.INPATIENT_FEE_UPLOAD, input2301, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2301, ChsInfNo.INPATIENT_FEE_UPLOAD);
        persistFeeUploads(context, mdtrtId, pendingLines, body2301);
    }

    private List<IpSettleChargeLine> filterPendingUploadLines(IpSettleContext context) {
        Long tenantId = resolveTenantId(context.getTenantId());
        List<IpSettleChargeLine> pending = new ArrayList<>();
        for (IpSettleChargeLine line : context.getChargeLines()) {
            Optional<YbInpatientFeeUpload> uploaded = feeUploadRepository
                .findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(line.getChargeItemId(), tenantId);
            if (uploaded.isPresent()
                && YbInpatientFeeUpload.STATUS_UPLOADED.equals(uploaded.get().getUploadStatus())) {
                continue;
            }
            pending.add(line);
        }
        return pending;
    }

    private void persistFeeUploads(
        IpSettleContext context,
        String mdtrtId,
        List<IpSettleChargeLine> lines,
        ResultBody body2301) {
        Long tenantId = resolveTenantId(context.getTenantId());
        java.util.Map<String, JSONObject> resultBySn = FeeUploadResultIndexer.indexByFeedetlSn(body2301);
        LocalDateTime now = LocalDateTime.now();
        for (IpSettleChargeLine line : lines) {
            YbInpatientFeeUpload row = feeUploadRepository
                .findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(line.getChargeItemId(), tenantId)
                .orElseGet(YbInpatientFeeUpload::new);
            if (row.getId() == null) {
                row.setCreatedAt(now);
            }
            row.setEncounterId(context.getEncounterId());
            row.setChargeItemId(line.getChargeItemId());
            row.setFeedetlSn(line.getFeedetlSn());
            row.setMdtrtId(mdtrtId);
            row.setDetItemFeeSumamt(line.getDetItemFeeSumamt());
            chargeBatchLifecycle.markInpatientFeeUploaded(row);
            JSONObject platformRow = resultBySn.get(line.getFeedetlSn().trim());
            FeeUploadResultApplier.applyToInpatientRow(row, platformRow);
            FeeUploadResultApplier.applyFallbackJson(row, body2301.getOutput());
            row.setUploadedAt(now);
            row.setTenantId(tenantId);
            feeUploadRepository.save(row);
        }
    }

    private YbInpatientPreSettle persistPreSettle(
        IpSettleContext context,
        String mdtrtId,
        JSONObject setlinfo) {
        Long tenantId = resolveTenantId(context.getTenantId());
        YbInpatientPreSettle row = new YbInpatientPreSettle();
        row.setPaymentDraftId(context.getPaymentDraftId());
        row.setEncounterId(context.getEncounterId());
        row.setContractId(context.getContractId());
        row.setContractBusNo(context.getContractBusNo());
        row.setMdtrtId(mdtrtId);
        row.setPsnNo(context.getPsnNo());
        row.setInsuplcAdmdvs(context.getInsuplcAdmdvs());
        row.setMdtrtCertType(context.getMdtrtCertType());
        row.setMdtrtCertNo(context.getMdtrtCertNo());
        row.setInsutype(context.getInsutype());
        row.setAcctUsedFlag(context.getAcctUsedFlag());
        row.setPsnSetlway(context.getPsnSetlway());
        row.setMedType(context.getMedType());
        row.setSettleMode(context.getSettleMode());
        if (IpSettleContext.SETTLE_MODE_MID.equals(context.getSettleMode())) {
            row.setMidSetlFlag(ChsInpatientConstants.MID_SETL_YES);
        } else {
            row.setMidSetlFlag(ChsInpatientConstants.MID_SETL_NO);
        }
        ChsSetlinfoSupport.applyInpatientPreSettleAmountFields(row, setlinfo);
        row.setChargeItemIds(toChargeItemIdsJson(context));
        row.setStatus(YbInpatientPreSettle.STATUS_ACTIVE);
        row.setTenantId(tenantId);
        row.setCreatedAt(LocalDateTime.now());
        return inpatientPreSettleRepository.save(row);
    }

    private YbSettlementRecord persistSettlement(
        YbInpatientPreSettle pre,
        String chrgBchno,
        JSONObject setlinfo,
        String setldetailJson,
        Long paymentDraftId) {
        YbSettlementRecord row = new YbSettlementRecord();
        row.setContractId(pre.getContractId());
        row.setEncounterId(pre.getEncounterId());
        row.setPaymentId(paymentDraftId);
        row.setPreSettleId(pre.getId());
        row.setChrgBchno(chrgBchno);
        row.setMdtrtId(pre.getMdtrtId());
        row.setPsnNo(pre.getPsnNo());
        row.setEncounterClass(ChsSettleEncounterClass.IMP);
        row.setSettleMode(pre.getSettleMode());
        row.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        ChsSetlinfoSupport.applyInpatientSettlementFields(row, pre, setlinfo, setldetailJson);
        String msgid2304 = ChsInvokeLogContextSupport.currentMsgid();
        if (StringUtils.isNotBlank(msgid2304)) {
            row.setMsgid2207(msgid2304.trim());
        } else if (StringUtils.isNotBlank(row.getMedinsSetlId())) {
            row.setMsgid2207(row.getMedinsSetlId());
        }
        row.setTenantId(pre.getTenantId());
        row.setCreatedAt(LocalDateTime.now());
        settlementRecordRepository.saveAndFlush(row);
        invokeLogWriter.linkSettlementRecord(ChsInvokeLogContextSupport.currentInvokeLogId(), row.getId());
        fundDetailWriter.persistFromSetldetail(row.getId(), setldetailJson, row.getTenantId());
        return row;
    }

    private String resolveMdtrtId(IpSettleContext context, ContractConfig config, InvokeContext invokeContext) {
        Long tenantId = resolveTenantId(context.getTenantId());
        String medType = ClinicInpatientPlatformAssembler.resolveMedTypeForContext(context);
        Optional<YbEncounterPlatformMap> mapped = encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                context.getEncounterId(), context.getContractId(), medType, tenantId);
        if (mapped.isPresent() && StringUtils.isNotBlank(mapped.get().getMdtrtId())) {
            return mapped.get().getMdtrtId().trim();
        }
        JSONObject input2401 = ClinicInpatientPlatformAssembler.assemble2401(context);
        ResultBody body2401 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.INPATIENT_ADMISSION, input2401, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2401, ChsInfNo.INPATIENT_ADMISSION);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body2401);
        JSONObject result = output.getJSONObject("result");
        if (result == null) {
            result = output;
        }
        String mdtrtId = result.getString("mdtrt_id");
        if (StringUtils.isBlank(mdtrtId)) {
            throw new BusinessException("INS-IP-009", "2401 未返回 mdtrt_id，请先完成医保入院登记");
        }
        encounterPlatformMapWriter.saveAfterPlatformRegistration(
            context.getEncounterId(),
            context.getContractId(),
            medType,
            mdtrtId.trim(),
            context.getPsnNo(),
            context.getEncounterBusNo(),
            tenantId);
        return mdtrtId.trim();
    }

    private String resolveExistingMdtrtId(IpSettleContext context) {
        Long tenantId = resolveTenantId(context.getTenantId());
        String medType = ClinicInpatientPlatformAssembler.resolveMedTypeForContext(context);
        Optional<YbEncounterPlatformMap> mapped = encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                context.getEncounterId(), context.getContractId(), medType, tenantId);
        if (mapped.isPresent() && StringUtils.isNotBlank(mapped.get().getMdtrtId())) {
            return mapped.get().getMdtrtId().trim();
        }
        throw new BusinessException("INS-IP-009", "未找到医保就诊ID，请先完成医保入院登记");
    }

    private YbSettlementRecord findSettlementRecord(IpRevokeSettleCommand command, Long tenantId) {
        return ChsSettlementRecordLookup.requireOne(
            settlementRecordRepository,
            command.getSettlementRecordId(),
            command.getPaymentId(),
            command.getSetlId(),
            tenantId);
    }

    private YbInpatientPreSettle resolvePreSettleForRevoke(
        YbSettlementRecord record,
        IpRevokeSettleCommand command,
        Long tenantId) {
        if (command.getPreSettleId() != null) {
            return inpatientPreSettleRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(
                command.getPreSettleId(), tenantId).orElse(null);
        }
        if (record.getPreSettleId() != null) {
            return inpatientPreSettleRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(
                record.getPreSettleId(), tenantId).orElse(null);
        }
        if (record.getPaymentId() != null) {
            return inpatientPreSettleRepository
                .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    record.getPaymentId(), YbInpatientPreSettle.STATUS_SETTLED, tenantId)
                .orElse(null);
        }
        return null;
    }

    private String resolveContractBusNoForRevoke(
        YbSettlementRecord record,
        YbInpatientPreSettle pre,
        Long tenantId) {
        if (pre != null && StringUtils.isNotBlank(pre.getContractBusNo())) {
            return pre.getContractBusNo().trim();
        }
        if (record.getPreSettleId() != null) {
            Optional<YbInpatientPreSettle> linked = inpatientPreSettleRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(
                record.getPreSettleId(), tenantId);
            if (linked.isPresent() && StringUtils.isNotBlank(linked.get().getContractBusNo())) {
                return linked.get().getContractBusNo().trim();
            }
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法解析 contractBusNo");
    }

    private IpSettleContext buildMinimalContext(YbSettlementRecord record, Long tenantId) {
        IpSettleContext ctx = new IpSettleContext();
        ctx.setContractId(record.getContractId());
        ctx.setEncounterId(record.getEncounterId());
        ctx.setPsnNo(record.getPsnNo());
        ctx.setTenantId(tenantId);
        return ctx;
    }

    private List<YbInpatientFeeUpload> resolveUploadedFees(IpRevokeFeeDetailCommand command, Long tenantId) {
        List<YbInpatientFeeUpload> uploads = new ArrayList<>();
        if (command.getChargeItemIds() == null || command.getChargeItemIds().isEmpty()) {
            return uploads;
        }
        for (Long chargeItemId : command.getChargeItemIds()) {
            if (chargeItemId == null) {
                continue;
            }
            feeUploadRepository.findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId)
                .filter(row -> YbInpatientFeeUpload.STATUS_UPLOADED.equals(row.getUploadStatus()))
                .ifPresent(uploads::add);
        }
        return uploads;
    }

    private void cancelActivePreSettlesForDraft(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null) {
            return;
        }
        List<YbInpatientPreSettle> activeRows = inpatientPreSettleRepository
            .findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                paymentDraftId, YbInpatientPreSettle.STATUS_ACTIVE, tenantId);
        for (YbInpatientPreSettle row : activeRows) {
            chargeBatchLifecycle.markInpatientPreSettleCancelled(row);
            inpatientPreSettleRepository.save(row);
        }
    }

    private void softDeleteEncounterPlatformMap(IpEncounterPlatformCommand command, Long tenantId) {
        Optional<YbEncounterPlatformMap> mapped = Optional.empty();
        if (StringUtils.isNotBlank(command.getMdtrtId())) {
            mapped = encounterPlatformMapRepository
                .findFirstByEncounterIdAndContractIdAndMdtrtIdAndTenantIdAndIsDeletedFalse(
                    command.getEncounterId(), command.getContractId(), command.getMdtrtId().trim(), tenantId);
        }
        if (mapped.isEmpty() && StringUtils.isNotBlank(command.getMedType())) {
            mapped = encounterPlatformMapRepository
                .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                    command.getEncounterId(), command.getContractId(), command.getMedType().trim(), tenantId);
        }
        mapped.ifPresent(row -> {
            row.setIsDeleted(true);
            row.setDeletedAt(LocalDateTime.now());
            if (command.getOperatorId() != null) {
                row.setDeletedBy(command.getOperatorId());
            }
            encounterPlatformMapRepository.save(row);
        });
    }

    private InvokeContext buildInvokeContext(Long operatorId, String insuplcAdmdvs) {
        InvokeContext.InvokeContextBuilder builder = InvokeContext.builder();
        if (operatorId != null) {
            builder.operatorId(String.valueOf(operatorId));
        }
        if (StringUtils.isNotBlank(insuplcAdmdvs)) {
            builder.insuplcAdmdvs(insuplcAdmdvs);
        }
        return builder.build();
    }

    private void validateEncounterPlatformCommand(
        Long encounterId,
        String contractBusNo,
        String mdtrtId,
        String psnNo) {
        if (encounterId == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "encounterId 不能为空");
        }
        if (StringUtils.isBlank(contractBusNo)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (StringUtils.isBlank(mdtrtId)) {
            throw new BusinessException("INS-IP-009", "mdtrtId 不能为空");
        }
        if (StringUtils.isBlank(psnNo)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空");
        }
    }

    private YbInpatientPreSettle findPreSettle(IpConfirmSettleCommand command, Long tenantId) {
        if (command.getPreSettleId() != null) {
            return inpatientPreSettleRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(
                command.getPreSettleId(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        if (command.getPaymentDraftId() != null) {
            return inpatientPreSettleRepository
                .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    command.getPaymentDraftId(), YbInpatientPreSettle.STATUS_ACTIVE, tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 preSettleId 或 paymentDraftId");
    }

    private IpSettleContext rebuildContextFromPreSettle(YbInpatientPreSettle pre) {
        IpSettleContext ctx = new IpSettleContext();
        ctx.setContractId(pre.getContractId());
        ctx.setContractBusNo(pre.getContractBusNo());
        ctx.setEncounterId(pre.getEncounterId());
        ctx.setPsnNo(pre.getPsnNo());
        ctx.setMedType(pre.getMedType());
        ctx.setSettleMode(pre.getSettleMode());
        ctx.setMedfeeSumamt(pre.getMedfeeSumamt());
        ctx.setTenantId(pre.getTenantId());
        ctx.setMdtrtCertType(pre.getMdtrtCertType());
        ctx.setMdtrtCertNo(pre.getMdtrtCertNo());
        ctx.setInsutype(pre.getInsutype());
        ctx.setAcctUsedFlag(pre.getAcctUsedFlag());
        ctx.setPsnSetlway(pre.getPsnSetlway());
        ctx.setInsuplcAdmdvs(pre.getInsuplcAdmdvs());
        return ctx;
    }

    private static String toChargeItemIdsJson(IpSettleContext context) {
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>(context.getChargeLines().size());
        for (IpSettleChargeLine line : context.getChargeLines()) {
            ids.add(line.getChargeItemId());
        }
        return ChsChargeItemIdsSupport.toJsonArray(ids);
    }

    private ContractConfig requireContractConfig(String contractBusNo) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "未找到契约配置: " + contractBusNo);
        }
        return config;
    }

    private InvokeContext buildInvokeContext(IpSettleContext context) {
        InvokeContext.InvokeContextBuilder builder = InvokeContext.builder();
        if (context.getOperatorId() != null) {
            builder.operatorId(String.valueOf(context.getOperatorId()));
        }
        if (StringUtils.isNotBlank(context.getInsuplcAdmdvs())) {
            builder.insuplcAdmdvs(context.getInsuplcAdmdvs());
        }
        return builder.build();
    }

    private void validateAdmissionContext(IpSettleContext context) {
        if (context == null || context.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (StringUtils.isBlank(context.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (context.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "encounterId 不能为空");
        }
        if (StringUtils.isBlank(context.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空，请先读卡（1101）");
        }
        if (StringUtils.isBlank(context.getMdtrtCertType()) || StringUtils.isBlank(context.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
    }

    private void validatePreSettleContext(IpSettleContext context) {
        if (context == null || context.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (StringUtils.isBlank(context.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (StringUtils.isBlank(context.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空，请先读卡（1101）");
        }
        if (StringUtils.isBlank(context.getMdtrtCertType()) || StringUtils.isBlank(context.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
        if (StringUtils.isBlank(context.getSettleMode())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "settleMode 不能为空");
        }
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "收费明细不能为空");
        }
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }

    private void markInpatientFeesSettled(YbInpatientPreSettle pre, Long tenantId) {
        for (Long chargeItemId : ChsChargeItemIdsSupport.parseIds(pre.getChargeItemIds())) {
            feeUploadRepository.findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId)
                .filter(row -> YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
                .ifPresent(row -> {
                    chargeBatchLifecycle.markInpatientFeeSettled(row);
                    feeUploadRepository.save(row);
                });
        }
    }

    private void markInpatientFeesRevoked(YbInpatientPreSettle pre, Long tenantId) {
        for (Long chargeItemId : ChsChargeItemIdsSupport.parseIds(pre.getChargeItemIds())) {
            feeUploadRepository.findFirstByChargeItemIdAndTenantIdAndIsDeletedFalse(chargeItemId, tenantId)
                .filter(row -> YbFeeUploadStatus.SETTLED.equals(row.getUploadStatus())
                    || YbFeeUploadStatus.UPLOADED.equals(row.getUploadStatus()))
                .ifPresent(row -> {
                    chargeBatchLifecycle.markInpatientFeeSettleRevoked(row);
                    feeUploadRepository.save(row);
                });
        }
    }

    private void validateSettlementAmounts(JSONObject setlinfo, YbInpatientPreSettle pre) {
        if (setlinfo == null) {
            return;
        }
        try {
            SettlementAmounts amounts = SettlementAmountValidator.fromSetlinfo(setlinfo);
            amounts.assertMedfeeBalanced();
            if (pre != null) {
                amounts.assert2207PassthroughFrom(pre);
            }
        } catch (SettlementInvariantException ex) {
            throw new BusinessException(ex.getErrorCode(), ex.getMessage());
        }
    }

    private static BigDecimal decimal(JSONObject json, String key, BigDecimal fallback) {
        if (json == null || !json.containsKey(key) || json.get(key) == null) {
            return fallback;
        }
        return new BigDecimal(String.valueOf(json.get(key)));
    }

    private static String text(JSONObject json, String key) {
        if (json == null) {
            return null;
        }
        return json.getString(key);
    }
}
