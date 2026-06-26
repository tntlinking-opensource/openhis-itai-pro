/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.outpatient;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.ChsPlatformCodes;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.chs.application.gateway.ChsInvokeLogContext;
import com.openhis.chs.application.gateway.ChsInvokeLogContextHolder;
import com.openhis.chs.application.gateway.ChsInvokeLogContextSupport;
import com.openhis.chs.application.gateway.YbInvokeLogWriter;
import com.openhis.chs.application.outpatient.assembler.ClinicOutpatientPlatformAssembler;
import com.openhis.chs.application.outpatient.support.ChsChargeItemIdsSupport;
import com.openhis.chs.application.outpatient.support.ChsPlatformResultSupport;
import com.openhis.chs.application.outpatient.support.ChsSettlementRecordLookup;
import com.openhis.chs.application.outpatient.support.ChsSetlinfoSupport;
import com.openhis.chs.application.settlement.ChargeBatchInvokeLogSupport;
import com.openhis.chs.application.settlement.YbClinicFeeUploadWriter;
import com.openhis.chs.application.settlement.YbEncounterPlatformMapWriter;
import com.openhis.chs.application.settlement.YbSettlementCorrectionWriter;
import com.openhis.chs.application.settlement.YbSettlementFundDetailWriter;
import com.openhis.chs.application.settlement.YbSettlementReversalWriter;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import com.openhis.chs.domain.settlement.SettlementAmountValidator;
import com.openhis.chs.domain.settlement.SettlementInvariantException;
import com.openhis.chs.domain.settlement.dto.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.model.SettlementAmounts;
import com.openhis.chs.domain.settlement.service.ChargeBatchLifecycle;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.ChsSettleEncounterClass;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.chs.domain.outpatient.dto.OpPlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.dto.OpConfirmSettleCommand;
import com.openhis.chs.domain.outpatient.dto.OpConfirmSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpPreSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokePlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokeFeeDetailResult;
import com.openhis.chs.domain.outpatient.dto.OpRevokeSettleCommand;
import com.openhis.chs.domain.outpatient.dto.OpRevokeSettleResult;
import com.openhis.chs.domain.outpatient.dto.OpSettleContext;
import com.openhis.chs.domain.outpatient.dto.YbIdetInfoRow;
import com.openhis.chs.domain.outpatient.dto.YbInsuInfoRow;
import com.openhis.chs.domain.outpatient.dto.YbPersonInfoResult;
import com.openhis.chs.domain.outpatient.dto.YbPersonQueryCommand;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentCheckCommand;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentCheckResult;
import com.openhis.chs.domain.outpatient.dto.YbTreatmentInfoRow;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.entity.YbEncounterPlatformMap;
import com.openhis.chs.domain.outpatient.entity.YbPersonCache;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.outpatient.facade.ChsOutpatientSettleOperations;
import com.openhis.chs.domain.outpatient.repository.YbClinicPreSettleRepository;
import com.openhis.chs.domain.outpatient.repository.YbEncounterPlatformMapRepository;
import com.openhis.chs.domain.outpatient.repository.YbPersonCacheRepository;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 门诊医保结算编排（AS/IF 对应）：
 * <ul>
 *   <li>AS：health {@code OutpatientChargeSettlementAppService} 经 Extension/Port 委托本类</li>
 *   <li>IF-OPC-06 {@code preSettleOutpatientCharges} → {@link #preSettle}</li>
 *   <li>IF-OPC-07 {@code confirmOutpatientChargeSettlement} → {@link #confirmSettle}</li>
 *   <li>1101 读卡 → {@link #getPersonInfo}</li>
 *   <li>2208/2205 撤销 → {@link #revokeSettle} / {@link #revokeFeeDetails}</li>
 * </ul>
 * 平台链路：2201→2203→2204→2206 预结算，2207 正式结算；报文组装见 {@link ClinicOutpatientPlatformAssembler}。
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsOutpatientSettleAppService implements ChsOutpatientSettleOperations {

    /** 电子凭证 01 按身份证号复用当日 1101 缓存时使用的凭证类型键（与真实读卡 02 共用）。 */
    private static final String PERSON_CACHE_CERTNO_ALIAS_TYPE = "02";
    private static final ConcurrentHashMap<String, Object> PERSON_CACHE_UPSERT_LOCKS = new ConcurrentHashMap<>();

    private final InsuranceGatewayClient gatewayClient;
    private final YbPersonCacheRepository personCacheRepository;
    private final YbEncounterPlatformMapRepository encounterPlatformMapRepository;
    private final YbEncounterPlatformMapWriter encounterPlatformMapWriter;
    private final YbClinicPreSettleRepository clinicPreSettleRepository;
    private final YbSettlementRecordRepository settlementRecordRepository;
    private final YbSettlementFundDetailWriter fundDetailWriter;
    private final YbClinicFeeUploadWriter clinicFeeUploadWriter;
    private final YbSettlementReversalWriter settlementReversalWriter;
    private final YbSettlementCorrectionWriter settlementCorrectionWriter;
    private final ChargeBatchLifecycle chargeBatchLifecycle;
    private final YbInvokeLogWriter invokeLogWriter;

    /**
     * 【2201】确保医保门诊挂号登记；已有同 med_type 的 mdtrt_id 映射时直接返回。
     *
     * @param context 含 encounterId、contractId、读卡字段
     * @return 平台就诊 ID（mdtrt_id），供后续 2203/2204 使用
     */
    @Override
    public OpPlatformRegistrationResult ensurePlatformRegistration(OpSettleContext context) {
        // Gate：就诊、契约、psnNo、就诊凭证必填
        validateRegistrationContext(context);
        // Pre：契约配置与 invoke 上下文（含 insuplcAdmdvs 路由）
        ContractConfig config = requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = buildInvokeContext(context);
        // Cmd：查本地映射或调 2201 新建 mdtrt_id
        String mdtrtId = resolveMdtrtId(context, config, invokeContext);
        // Asm
        return new OpPlatformRegistrationResult().setMdtrtId(mdtrtId);
    }

    /**
     * 【2202】撤销医保门诊平台挂号登记并软删本地 mdtrt_id 映射。
     *
     * @param command 含 encounterId、contractId、medType
     * @return 撤销结果；无有效映射时 revoked=false
     */
    @Override
    public OpRevokePlatformRegistrationResult revokePlatformRegistration(OpRevokePlatformRegistrationCommand command) {
        if (command == null || command.getEncounterId() == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2202 缺少就诊或费用性质");
        }
        Long tenantId = resolveTenantId(command.getTenantId());
        String medType = ClinicOutpatientPlatformAssembler.resolveMedTypeForCommand(command.getMedType());
        Optional<YbEncounterPlatformMap> mapped = encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                command.getEncounterId(), command.getContractId(), medType, tenantId);
        if (mapped.isEmpty() || StringUtils.isBlank(mapped.get().getMdtrtId())) {
            OpRevokePlatformRegistrationResult skipped = new OpRevokePlatformRegistrationResult();
            skipped.setRevoked(false);
            return skipped;
        }
        YbEncounterPlatformMap row = mapped.get();
        String contractBusNo = command.getContractBusNo();
        if (StringUtils.isBlank(contractBusNo)) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2202 缺少 contractBusNo");
        }
        requireContractConfig(contractBusNo);
        InvokeContext invokeContext = buildInvokeContext(command.getOperatorId(), command.getInsuplcAdmdvs());
        JSONObject input2202 = ClinicOutpatientPlatformAssembler.assemble2202(
            row.getPsnNo(), row.getMdtrtId(), row.getIptOtpNo());
        ResultBody body2202 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_REG_REVOKE, input2202, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2202, ChsInfNo.OUTPATIENT_REG_REVOKE);
        encounterPlatformMapWriter.softDeleteAfterPlatformRevocation(
            command.getEncounterId(), command.getContractId(), medType, tenantId);
        OpRevokePlatformRegistrationResult result = new OpRevokePlatformRegistrationResult();
        result.setMdtrtId(row.getMdtrtId());
        result.setRevoked(true);
        return result;
    }

    /**
     * 【2203→2204→2206】门诊医保预结算；必要时先 2201 挂号登记。
     *
     * @param context 结算上下文（契约、就诊、收费明细、读卡字段等）
     * @return 预结算结果（含 preSettleId、chrgBchno、mdtrtId 及分摊金额）
     * @throws BusinessException 上下文非法或平台拒绝时
     */
    @Override
    public OpPreSettleResult preSettle(OpSettleContext context) {
        // Gate：校验 contractId、psnNo、就诊凭证及收费明细
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

    private OpPreSettleResult doPreSettle(OpSettleContext context) {
        // Pre：契约配置、invoke 上下文、mdtrtId 与 chrg_bchno
        ContractConfig config = requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = buildInvokeContext(context);
        String mdtrtId = resolveMdtrtId(context, config, invokeContext);
        long seed = SnowflakeIdGenerator.nextId();
        String chrgBchno = ClinicOutpatientPlatformAssembler.newChrgBchno(config, seed);
        ChsInvokeLogContextHolder.merge(ctx -> ctx.setChrgBchno(chrgBchno));

        // Cmd：2203 就诊上传 → 2204 费用上传 → 2206 预结算
        JSONObject input2203 = ClinicOutpatientPlatformAssembler.assemble2203(context, mdtrtId);
        ResultBody body2203 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.OUTPATIENT_VISIT_UPLOAD, input2203, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2203, ChsInfNo.OUTPATIENT_VISIT_UPLOAD);

        JSONObject input2204 = ClinicOutpatientPlatformAssembler.assemble2204(context, mdtrtId, chrgBchno);
        ResultBody body2204 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.OUTPATIENT_FEE_UPLOAD, input2204, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2204, ChsInfNo.OUTPATIENT_FEE_UPLOAD);
        Long tenantId = resolveTenantId(context.getTenantId());
        clinicFeeUploadWriter.persistFrom2204(
            context.getEncounterId(),
            mdtrtId,
            chrgBchno,
            context.getChargeLines(),
            body2204,
            tenantId);

        BigDecimal medfeeSumamt = context.getMedfeeSumamt();
        JSONObject input2206 = ClinicOutpatientPlatformAssembler.assemble2206(
            context, mdtrtId, chrgBchno, medfeeSumamt);
        ResultBody body2206 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.OUTPATIENT_PRE_SETTLE, input2206, invokeContext);
        JSONObject setlinfo = ChsPlatformResultSupport.requireSetlinfo(body2206);

        // Asm：持久化预结算快照并组装返回 DTO
        YbClinicPreSettle row = persistPreSettle(context, mdtrtId, chrgBchno, setlinfo);
        clinicFeeUploadWriter.linkPreSettle(chrgBchno, row.getId(), tenantId);
        ChargeBatchInvokeLogSupport.mergeBatch(ChargeBatch.fromClinicPreSettle(row));
        OpPreSettleResult result = new OpPreSettleResult();
        result.setPreSettleId(row.getId());
        result.setChrgBchno(chrgBchno);
        result.setMdtrtId(mdtrtId);
        result.setMedfeeSumamt(row.getMedfeeSumamt());
        result.setFundPaySumamt(row.getFundPaySumamt());
        result.setAcctPay(row.getAcctPay());
        result.setPsnCashPay(row.getPsnCashPay());
        return result;
    }

    /**
     * 【2207】门诊医保正式结算；须先有 ACTIVE 预结算快照。
     *
     * @param command 含 chrg_bchno 或 paymentDraftId，可选 paymentDraftId 回填
     * @return 正式结算结果（含 settlementRecordId、setl_id 及分摊金额）
     * @throws BusinessException 预结算快照不存在、状态非法或平台拒绝时
     */
    @Override
    public OpConfirmSettleResult confirmSettle(OpConfirmSettleCommand command) {
        // Pre：解析租户并定位预结算快照
        Long tenantId = resolveTenantId(command.getTenantId());
        YbClinicPreSettle pre = findPreSettle(command, tenantId);
        ChargeBatch batch = ChargeBatch.fromClinicPreSettle(pre);
        ChsInvokeLogContextHolder.set(ChargeBatchInvokeLogSupport.contextFromBatch(batch, tenantId)
            .setContractId(pre.getContractId())
            .setEncounterId(pre.getEncounterId())
            .setPaymentDraftId(command.getPaymentDraftId()));
        try {
            return doConfirmSettle(command, pre, batch, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    private OpConfirmSettleResult doConfirmSettle(
        OpConfirmSettleCommand command,
        YbClinicPreSettle pre,
        ChargeBatch batch,
        Long tenantId) {
        // Gate：仅 ACTIVE 快照可转正式结算
        if (!YbClinicPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "预结算快照状态非法，请重新预结算");
        }
        JSONObject setlinfo2206 = ChsSetlinfoSupport.build2206SetlinfoFor2207(pre);

        OpSettleContext ctx = rebuildContextFromPreSettle(pre);
        requireContractConfig(ctx.getContractBusNo());
        InvokeContext invokeContext = buildInvokeContext(ctx);

        // Cmd：2207 正式结算
        JSONObject input2207 = ClinicOutpatientPlatformAssembler.assemble2207(
            ctx,
            batch.mdtrtId().value(),
            batch.batchKey(),
            pre.getMedfeeSumamt(),
            setlinfo2206);
        ResultBody body2207 = gatewayClient.invoke(
            ctx.getContractBusNo(), ChsInfNo.OUTPATIENT_SETTLE, input2207, invokeContext);
        JSONObject setlinfo2207 = ChsPlatformResultSupport.require2207Setlinfo(body2207);
        String setldetailJson = ChsSetlinfoSupport.extractSetldetailJson(body2207.getOutput());
        validateSettlementAmounts(setlinfo2207, pre);

        // Asm：落结算回执并将预结算快照标记为已结算
        YbSettlementRecord record = persistSettlement(
            pre, setlinfo2207, setldetailJson, command.getPaymentDraftId());
        clinicFeeUploadWriter.markSettledByChrgBchno(batch.batchKey(), tenantId);
        ChsInvokeLogContextHolder.merge(logCtx -> logCtx.setSettlementRecordId(record.getId()));
        chargeBatchLifecycle.markClinicPreSettleSettled(pre);
        clinicPreSettleRepository.save(pre);

        OpConfirmSettleResult result = new OpConfirmSettleResult();
        result.setSettlementRecordId(record.getId());
        result.setAccountId(record.getAccountId());
        result.setSetlId(record.getSetlId());
        result.setChrgBchno(batch.batchKey());
        result.setMdtrtId(batch.mdtrtId().value());
        result.setMedfeeSumamt(record.getMedfeeSumamt());
        result.setFundPaySumamt(record.getFundPaySumamt());
        result.setAcctPay(record.getAcctPay());
        result.setPsnCashPay(record.getPsnCashPay());
        return result;
    }

    /**
     * 【2208】撤销门诊正式结算。
     *
     * @param command 含 settlementRecordId、paymentId 或 setlId 之一，可选 preSettleId
     * @return 撤销结果（含原结算回执 id、setl_id、mdtrt_id）
     * @throws BusinessException 结算回执不存在、无法解析 contractBusNo 或平台拒绝时
     */
    @Override
    public OpRevokeSettleResult revokeSettle(OpRevokeSettleCommand command) {
        // Pre：定位结算回执、关联预结算快照与契约 bus_no
        Long tenantId = resolveTenantId(command.getTenantId());
        YbSettlementRecord record = findSettlementRecord(command, tenantId);
        // Gate：重复撤销无业务意义，直接拒绝
        if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
        }
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(record.getContractId())
            .setEncounterId(record.getEncounterId())
            .setChrgBchno(record.getChrgBchno())
            .setSettlementRecordId(record.getId())
            .setTenantId(tenantId));
        try {
            return doRevokeSettle(command, record, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    private OpRevokeSettleResult doRevokeSettle(
        OpRevokeSettleCommand command,
        YbSettlementRecord record,
        Long tenantId) {
        YbClinicPreSettle pre = resolvePreSettleForRevoke(record, command, tenantId);
        String contractBusNo = resolveContractBusNoForRevoke(record, pre, tenantId);
        OpSettleContext ctx;
        // 有预结算快照时复用完整上下文，否则从回执构建最小上下文
        if (pre != null) {
            ctx = rebuildContextFromPreSettle(pre);
        } else {
            ctx = buildMinimalContext(record);
        }
        ctx.setContractBusNo(contractBusNo);
        InvokeContext invokeContext = buildInvokeContext(ctx);

        // Cmd：2208 撤销正式结算
        JSONObject input2208 = ClinicOutpatientPlatformAssembler.assemble2208(
            record.getMdtrtId(), record.getSetlId(), record.getPsnNo());
        ResultBody body2208 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_SETTLE_REVOKE, input2208, invokeContext);
        JSONObject revokeSetlinfo = ChsPlatformResultSupport.require2208Setlinfo(body2208);
        String setldetailJson = ChsSetlinfoSupport.extractSetldetailJson(body2208.getOutput());

        settlementReversalWriter.persistReversal(
            record, revokeSetlinfo, setldetailJson, command.getOperatorId(),
            ChsInvokeLogContextSupport.currentInvokeLogId());
        settlementRecordRepository.save(record);
        // Asm：标记预结算快照为已撤销
        if (pre != null) {
            chargeBatchLifecycle.markClinicPreSettleRevoked(pre);
            clinicPreSettleRepository.save(pre);
        }
        clinicFeeUploadWriter.markSettleRevokedByChrgBchno(record.getChrgBchno(), tenantId);

        OpRevokeSettleResult result = new OpRevokeSettleResult();
        result.setSettlementRecordId(record.getId());
        result.setSetlId(record.getSetlId());
        result.setMdtrtId(record.getMdtrtId());
        return result;
    }

    /**
     * 【2205】撤销未正式结算的费用明细（预结算回滚）。
     *
     * @param command 含 chrg_bchno，可选 contractBusNo、insuplcAdmdvs
     * @return 撤销结果；非 ACTIVE 快照时 revoked=false 且不调用平台
     * @throws BusinessException 缺少 chrg_bchno、快照不存在或平台拒绝时
     */
    @Override
    public OpRevokeFeeDetailResult revokeFeeDetails(OpRevokeFeeDetailCommand command) {
        // Gate：chrg_bchno 为定位预结算快照的必填键
        if (command == null || StringUtils.isBlank(command.getChrgBchno())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 chrg_bchno");
        }
        // Pre：定位预结算快照并解析契约与 invoke 上下文
        Long tenantId = resolveTenantId(command.getTenantId());
        YbClinicPreSettle pre = clinicPreSettleRepository.findFirstByChrgBchnoAndTenantIdAndIsDeletedFalse(
            command.getChrgBchno().trim(), tenantId)
            .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        // 已结算或已撤销的快照无需再调 2205
        if (!YbClinicPreSettle.STATUS_ACTIVE.equals(pre.getStatus())) {
            OpRevokeFeeDetailResult skipped = new OpRevokeFeeDetailResult();
            skipped.setChrgBchno(pre.getChrgBchno());
            skipped.setRevoked(false);
            return skipped;
        }
        String contractBusNo;
        if (StringUtils.isNotBlank(command.getContractBusNo())) {
            contractBusNo = command.getContractBusNo().trim();
        } else {
            contractBusNo = pre.getContractBusNo();
        }
        requireContractConfig(contractBusNo);
        String insuplcAdmdvs;
        if (StringUtils.isNotBlank(command.getInsuplcAdmdvs())) {
            insuplcAdmdvs = command.getInsuplcAdmdvs();
        } else {
            insuplcAdmdvs = pre.getInsuplcAdmdvs();
        }
        InvokeContext invokeContext = buildInvokeContext(command.getOperatorId(), insuplcAdmdvs);

        // Cmd：2205 撤销费用明细
        JSONObject input2205 = ClinicOutpatientPlatformAssembler.assemble2205(
            pre.getMdtrtId(), pre.getChrgBchno(), pre.getPsnNo());
        ResultBody body2205 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_FEE_REVOKE, input2205, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2205, ChsInfNo.OUTPATIENT_FEE_REVOKE);

        clinicFeeUploadWriter.markRevokedByChrgBchno(pre.getChrgBchno(), tenantId);
        // Asm：标记预结算快照为已取消
        chargeBatchLifecycle.markClinicPreSettleCancelled(pre);
        clinicPreSettleRepository.save(pre);

        OpRevokeFeeDetailResult result = new OpRevokeFeeDetailResult();
        result.setChrgBchno(pre.getChrgBchno());
        result.setRevoked(true);
        return result;
    }

    /**
     * 【1101】读卡获取人员信息；当日缓存未过期时直接返回。
     *
     * @param command 含 contractId、contractBusNo、就诊凭证及 operatorId
     * @return 人员编号、险种、参保地及账户余额等
     * @throws BusinessException 请求非法、平台拒绝或响应缺少 baseinfo 时
     */
    @Override
    public YbPersonInfoResult getPersonInfo(YbPersonQueryCommand command) {
        // Gate：校验 contractId、contractBusNo 与就诊凭证
        validatePersonQuery(command);
        Long tenantId = resolveTenantId(command.getTenantId());
        // Pre：查当日人员信息缓存（电子凭证 01 优先按身份证号命中，避免 ecToken 每次不同）
        Optional<YbPersonCache> cached = findValidPersonCache(command, tenantId);
        if (cached.isPresent()) {
            return mapPersonCache(cached.get());
        }

        // Cmd：1101 人员信息获取
        InvokeContext invokeContext = InvokeContext.builder()
            .operatorId(String.valueOf(command.getOperatorId()))
            .insuplcAdmdvs(command.getInsuplcAdmdvs())
            .build();
        JSONObject input = ClinicOutpatientPlatformAssembler.assemble1101(
            command.getMdtrtCertType(),
            command.getMdtrtCertNo(),
            command.getCardSn(),
            command.getPsnCertType(),
            command.getCertno(),
            command.getPsnName());
        ResultBody body = invoke1101WithIdCardFallback(command, invokeContext, input);
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.PERSON_INFO);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        JSONObject baseinfo = output.getJSONObject("baseinfo");
        if (baseinfo == null) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "1101 缺少 baseinfo");
        }

        // Asm：解析平台响应并写入当日缓存
        YbPersonInfoResult result = parse1101(baseinfo, output, command.getInsuplcAdmdvs());
        persistPersonCache(command, result, body.getOutput(), tenantId);
        return result;
    }

    /**
     * 【2001】人员待遇享受检查（挂号/结算前校验参保待遇）。
     *
     * @param command 含 psnNo、insutype、medType（缺省普通门诊 11）
     * @return 各基金待遇检查行 trtinfo
     */
    @Override
    public YbTreatmentCheckResult checkPersonTreatment(YbTreatmentCheckCommand command) {
        // Gate：契约与人员编号必填
        validateTreatmentCheck(command);
        // Pre：契约定点编码、invoke 上下文
        ContractConfig config = requireContractConfig(command.getContractBusNo());
        InvokeContext invokeContext = InvokeContext.builder()
            .operatorId(String.valueOf(command.getOperatorId()))
            .insuplcAdmdvs(command.getInsuplcAdmdvs())
            .build();
        String medType = "11";
        if (StringUtils.isNotBlank(command.getMedType())) {
            medType = command.getMedType().trim();
        }
        String begntime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // Cmd：2001 待遇检查
        JSONObject input = ClinicOutpatientPlatformAssembler.assemble2001(
            command.getPsnNo(),
            command.getInsutype(),
            config.getFixmedinsCode(),
            medType,
            begntime);
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.PERSON_TREATMENT_CHECK, input, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.PERSON_TREATMENT_CHECK);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        // Asm：解析 trtinfo 列表
        return parse2001(output);
    }

    private void validateTreatmentCheck(YbTreatmentCheckCommand command) {
        if (command == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (StringUtils.isBlank(command.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (StringUtils.isBlank(command.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "psnNo 不能为空");
        }
        if (StringUtils.isBlank(command.getInsutype())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "insutype 不能为空");
        }
    }

    private YbTreatmentCheckResult parse2001(JSONObject output) {
        YbTreatmentCheckResult result = new YbTreatmentCheckResult();
        if (output.getJSONArray("trtinfo") == null) {
            return result;
        }
        for (int i = 0; i < output.getJSONArray("trtinfo").size(); i++) {
            JSONObject item = output.getJSONArray("trtinfo").getJSONObject(i);
            YbTreatmentInfoRow row = new YbTreatmentInfoRow();
            row.setPsnNo(item.getString("psn_no"));
            row.setTrtChkType(item.getString("trt_chk_type"));
            row.setFundPayType(item.getString("fund_pay_type"));
            row.setTrtEnjymntFlag(item.getString("trt_enjymnt_flag"));
            row.setBegndate(item.getString("begndate"));
            row.setEnddate(item.getString("enddate"));
            row.setTrtChkRslt(item.getString("trt_chk_rslt"));
            result.getTrtinfoList().add(row);
        }
        return result;
    }

    private YbClinicPreSettle persistPreSettle(
        OpSettleContext context,
        String mdtrtId,
        String chrgBchno,
        JSONObject setlinfo) {
        Long tenantId = resolveTenantId(context.getTenantId());
        YbClinicPreSettle row = new YbClinicPreSettle();
        row.setContractId(context.getContractId());
        row.setEncounterId(context.getEncounterId());
        row.setPaymentDraftId(context.getPaymentDraftId());
        row.setAccountId(context.getAccountId());
        row.setChrgBchno(chrgBchno);
        row.setContractBusNo(context.getContractBusNo());
        row.setMdtrtId(mdtrtId);
        row.setPsnNo(context.getPsnNo());
        row.setInsuplcAdmdvs(context.getInsuplcAdmdvs());
        row.setMdtrtCertType(context.getMdtrtCertType());
        row.setMdtrtCertNo(context.getMdtrtCertNo());
        row.setInsutype(context.getInsutype());
        row.setAcctUsedFlag(context.getAcctUsedFlag());
        row.setPsnSetlway(context.getPsnSetlway());
        ChsSetlinfoSupport.applyPreSettleAmountFields(row, setlinfo);
        row.setChargeItemIds(toChargeItemIdsJson(context));
        row.setStatus(YbClinicPreSettle.STATUS_ACTIVE);
        row.setMedType(context.getMedType());
        row.setDiseCodg(context.getMainDiagCode());
        row.setTenantId(tenantId);
        row.setCreatedAt(LocalDateTime.now());
        return clinicPreSettleRepository.save(row);
    }

    private YbSettlementRecord persistSettlement(
        YbClinicPreSettle pre,
        JSONObject setlinfo,
        String setldetailJson,
        Long paymentDraftId) {
        YbSettlementRecord row = new YbSettlementRecord();
        row.setContractId(pre.getContractId());
        row.setEncounterId(pre.getEncounterId());
        row.setPaymentId(paymentDraftId);
        row.setPreSettleId(pre.getId());
        row.setChrgBchno(pre.getChrgBchno());
        row.setMdtrtId(pre.getMdtrtId());
        row.setPsnNo(pre.getPsnNo());
        row.setEncounterClass(ChsSettleEncounterClass.AMB);
        row.setStatus(YbSettlementVoucherStatus.CONFIRMED);
        ChsSetlinfoSupport.applySettlementFields(row, pre, setlinfo, setldetailJson);
        String msgid2207 = ChsInvokeLogContextSupport.currentMsgid();
        if (StringUtils.isNotBlank(msgid2207)) {
            row.setMsgid2207(msgid2207.trim());
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

    /**
     * 【2601】冲正原交易（2207/2208 等超时补偿）。
     *
     * <p>由 {@code PaymentSettledInsuranceListener} 在本地落账成功但平台状态不明时触发；
     * 冲正成功后写 {@code yb_settlement_correction} 并标记回执 REVOKED。</p>
     *
     * @param command 含 omsgid、oinfno、psnNo；可选 settlementRecordId 关联回执
     */
    @Override
    public void correctTransaction(SettlementCorrectionCommand command) {
        // Gate：冲正三要素 omsgid/oinfno/psnNo 缺一不可
        if (command == null || StringUtils.isBlank(command.getOmsgid())
            || StringUtils.isBlank(command.getOinfno()) || StringUtils.isBlank(command.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2601 冲正参数不完整");
        }
        Long tenantId = resolveTenantId(command.getTenantId());
        // Pre：按 settlementRecordId 加载回执，反查 contractBusNo
        YbSettlementRecord record = command.getSettlementRecordId() != null
            ? settlementRecordRepository.findById(command.getSettlementRecordId())
                .filter(row -> tenantId.equals(row.getTenantId()) && !Boolean.TRUE.equals(row.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执不存在"))
            : null;
        String contractBusNo = command.getContractBusNo();
        // 命令未带 bus_no 时从回执/预结算/契约配置反查
        if (StringUtils.isBlank(contractBusNo) && record != null) {
            contractBusNo = resolveContractBusNoFromRecord(record, tenantId);
        }
        requireContractConfig(contractBusNo);
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setSettlementRecordId(command.getSettlementRecordId())
            .setTenantId(tenantId));
        // 有回执时填充关联键，无回执时仅冲正平台交易
        if (record != null) {
            ChsInvokeLogContextHolder.merge(ctx -> ctx
                .setContractId(record.getContractId())
                .setEncounterId(record.getEncounterId())
                .setChrgBchno(record.getChrgBchno()));
        }
        try {
            // Cmd：2601 冲正
            InvokeContext invokeContext = buildInvokeContext(command.getOperatorId(), null);
            JSONObject input2601 = ClinicOutpatientPlatformAssembler.assemble2601(
                command.getPsnNo().trim(),
                command.getOmsgid().trim(),
                command.getOinfno().trim());
            ResultBody body2601 = gatewayClient.invoke(
                contractBusNo, ChsInfNo.TRANSACTION_CORRECT, input2601, invokeContext);
            ChsPlatformResultSupport.requireSuccess(body2601, ChsInfNo.TRANSACTION_CORRECT);
            // Asm：写冲正审计；回执未撤销时同步写 reversal 并标记 REVOKED
            Long invokeLogId = ChsInvokeLogContextSupport.currentInvokeLogId();
            settlementCorrectionWriter.persist(command, record, invokeLogId, tenantId);
            if (record != null && !YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                settlementReversalWriter.persist2601Correction(
                    record,
                    command.getOperatorId(),
                    ChsInvokeLogContextSupport.currentInvokeLogId());
                settlementRecordRepository.save(record);
            }
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    private String resolveContractBusNoFromRecord(YbSettlementRecord record, Long tenantId) {
        if (record.getPreSettleId() != null) {
            Optional<YbClinicPreSettle> pre = clinicPreSettleRepository
                .findById(record.getPreSettleId())
                .filter(row -> tenantId.equals(row.getTenantId()) && !Boolean.TRUE.equals(row.getIsDeleted()));
            if (pre.isPresent() && StringUtils.isNotBlank(pre.get().getContractBusNo())) {
                return pre.get().getContractBusNo().trim();
            }
        }
        if (record.getContractId() != null) {
            ContractConfig config = ContractConfigLoader.getByContractId(record.getContractId());
            if (config != null && StringUtils.isNotBlank(config.getBusNo())) {
                return config.getBusNo().trim();
            }
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "无法解析 contractBusNo");
    }

    private static String toChargeItemIdsJson(OpSettleContext context) {
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            return null;
        }
        java.util.List<Long> ids = new java.util.ArrayList<>(context.getChargeLines().size());
        for (int i = 0; i < context.getChargeLines().size(); i++) {
            ids.add(context.getChargeLines().get(i).getChargeItemId());
        }
        return ChsChargeItemIdsSupport.toJsonArray(ids);
    }

    private void validateSettlementAmounts(JSONObject setlinfo, YbClinicPreSettle pre) {
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

    private void persistPersonCache(
        YbPersonQueryCommand command,
        YbPersonInfoResult result,
        String snapshotJson,
        Long tenantId) {
        upsertPersonCacheRow(
            command.getContractId(),
            command.getMdtrtCertType(),
            command.getMdtrtCertNo(),
            tenantId,
            result,
            snapshotJson);
        String certno = resolvePersonCacheCertno(command, result);
        if (certno != null && !isCertnoPrimaryCacheKey(command.getMdtrtCertType(), command.getMdtrtCertNo(), certno)) {
            upsertPersonCacheRow(
                command.getContractId(),
                PERSON_CACHE_CERTNO_ALIAS_TYPE,
                certno,
                tenantId,
                result,
                snapshotJson);
        }
    }

    /**
     * 查当日有效缓存。电子凭证（01）优先用身份证号（存为 02+certno 别名行），再回退 ecToken 行。
     */
    private Optional<YbPersonCache> findValidPersonCache(YbPersonQueryCommand command, Long tenantId) {
        String certno = resolvePersonCacheCertno(command, null);
        if ("01".equals(StringUtils.trimToEmpty(command.getMdtrtCertType())) && certno != null) {
            Optional<YbPersonCache> byCertno = findPersonCacheRow(
                command.getContractId(), PERSON_CACHE_CERTNO_ALIAS_TYPE, certno, tenantId);
            if (byCertno.isPresent() && isPersonCacheValid(byCertno.get())) {
                return byCertno;
            }
        }
        Optional<YbPersonCache> byCredential = findPersonCacheRow(
            command.getContractId(),
            command.getMdtrtCertType(),
            command.getMdtrtCertNo(),
            tenantId);
        if (byCredential.isPresent() && isPersonCacheValid(byCredential.get())) {
            return byCredential;
        }
        return Optional.empty();
    }

    private Optional<YbPersonCache> findPersonCacheRow(
        Long contractId,
        String mdtrtCertType,
        String mdtrtCertNo,
        Long tenantId) {
        return personCacheRepository.findFirstByContractIdAndMdtrtCertTypeAndMdtrtCertNoAndTenantIdAndIsDeletedFalse(
            contractId, mdtrtCertType, mdtrtCertNo, tenantId);
    }

    private boolean isPersonCacheValid(YbPersonCache row) {
        return row.getExpireAt() != null && row.getExpireAt().isAfter(LocalDateTime.now());
    }

    private void upsertPersonCacheRow(
        Long contractId,
        String mdtrtCertType,
        String mdtrtCertNo,
        Long tenantId,
        YbPersonInfoResult result,
        String snapshotJson) {
        String lockKey = contractId + "\0" + mdtrtCertType + "\0" + mdtrtCertNo + "\0" + tenantId;
        Object lock = PERSON_CACHE_UPSERT_LOCKS.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                upsertPersonCacheRowInternal(contractId, mdtrtCertType, mdtrtCertNo, tenantId, result, snapshotJson);
            } catch (DataIntegrityViolationException ex) {
                if (!isPersonCacheCertDuplicate(ex)) {
                    throw ex;
                }
                log.warn("yb_person_cache upsert conflict, retrying: contractId={}, type={}, tenantId={}",
                    contractId, mdtrtCertType, tenantId);
                upsertPersonCacheRowInternal(contractId, mdtrtCertType, mdtrtCertNo, tenantId, result, snapshotJson);
            } finally {
                PERSON_CACHE_UPSERT_LOCKS.remove(lockKey, lock);
            }
        }
    }

    private void upsertPersonCacheRowInternal(
        Long contractId,
        String mdtrtCertType,
        String mdtrtCertNo,
        Long tenantId,
        YbPersonInfoResult result,
        String snapshotJson) {
        Optional<YbPersonCache> existing = findPersonCacheRow(contractId, mdtrtCertType, mdtrtCertNo, tenantId);
        YbPersonCache row = existing.orElseGet(YbPersonCache::new);
        row.setContractId(contractId);
        row.setMdtrtCertType(mdtrtCertType);
        row.setMdtrtCertNo(mdtrtCertNo);
        row.setPsnNo(result.getPsnNo());
        row.setInsuplcAdmdvs(result.getInsuplcAdmdvs());
        row.setInsutype(result.getInsutype());
        row.setBalc(result.getBalc());
        row.setSnapshotJson(snapshotJson);
        LocalDateTime now = LocalDateTime.now();
        row.setCachedAt(now);
        row.setExpireAt(LocalDateTime.of(LocalDate.now(), LocalTime.of(23, 59, 59)));
        row.setTenantId(tenantId);
        personCacheRepository.saveAndFlush(row);
    }

    private boolean isPersonCacheCertDuplicate(DataIntegrityViolationException ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains("uk_yb_person_cache_cert")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String resolvePersonCacheCertno(YbPersonQueryCommand command, YbPersonInfoResult result) {
        String certno = normalizePersonCacheCertno(command.getCertno());
        if (certno != null) {
            return certno;
        }
        if (result != null) {
            return normalizePersonCacheCertno(result.getCertno());
        }
        return null;
    }

    private String normalizePersonCacheCertno(String certno) {
        if (StringUtils.isBlank(certno)) {
            return null;
        }
        return certno.trim().toUpperCase();
    }

    private boolean isCertnoPrimaryCacheKey(String mdtrtCertType, String mdtrtCertNo, String certno) {
        return PERSON_CACHE_CERTNO_ALIAS_TYPE.equals(StringUtils.trimToEmpty(mdtrtCertType))
            && certno.equals(normalizePersonCacheCertno(mdtrtCertNo));
    }

    private String resolveMdtrtId(OpSettleContext context, ContractConfig config, InvokeContext invokeContext) {
        Long tenantId = resolveTenantId(context.getTenantId());
        String medType = ClinicOutpatientPlatformAssembler.resolveMedTypeForContext(context);
        // Pre：同就诊+契约+med_type 已有映射则复用，避免重复 2201
        Optional<YbEncounterPlatformMap> mapped = encounterPlatformMapRepository
            .findFirstByEncounterIdAndContractIdAndMedTypeAndTenantIdAndIsDeletedFalse(
                context.getEncounterId(), context.getContractId(), medType, tenantId);
        if (mapped.isPresent()) {
            return mapped.get().getMdtrtId();
        }
        // Cmd：2201 门诊挂号登记
        JSONObject input2201 = ClinicOutpatientPlatformAssembler.assemble2201(context);
        ResultBody body2201 = gatewayClient.invoke(
            context.getContractBusNo(), ChsInfNo.OUTPATIENT_REG, input2201, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body2201, ChsInfNo.OUTPATIENT_REG);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body2201);
        JSONObject data = output.getJSONObject("data");
        if (data == null) {
            data = output;
        }
        String mdtrtId = data.getString("mdtrt_id");
        if (StringUtils.isBlank(mdtrtId)) {
            throw new BusinessException("INS-009", "2201 未返回 mdtrt_id，请先完成医保挂号登记");
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

    private YbClinicPreSettle findPreSettle(OpConfirmSettleCommand command, Long tenantId) {
        if (StringUtils.isNotBlank(command.getChrgBchno())) {
            return clinicPreSettleRepository.findFirstByChrgBchnoAndTenantIdAndIsDeletedFalse(
                command.getChrgBchno().trim(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        if (command.getPaymentDraftId() != null) {
            return clinicPreSettleRepository.findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                command.getPaymentDraftId(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到预结算快照"));
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 chrg_bchno 或 paymentDraftId");
    }

    private OpSettleContext rebuildContextFromPreSettle(YbClinicPreSettle pre) {
        OpSettleContext ctx = new OpSettleContext();
        ctx.setContractId(pre.getContractId());
        ctx.setEncounterId(pre.getEncounterId());
        ctx.setPsnNo(pre.getPsnNo());
        ctx.setInsuplcAdmdvs(pre.getInsuplcAdmdvs());
        ctx.setMdtrtCertType(pre.getMdtrtCertType());
        ctx.setMdtrtCertNo(pre.getMdtrtCertNo());
        ctx.setInsutype(pre.getInsutype());
        ctx.setMedType(pre.getMedType());
        ctx.setAcctUsedFlag(pre.getAcctUsedFlag());
        ctx.setPsnSetlway(pre.getPsnSetlway());
        ctx.setAccountId(pre.getAccountId());
        ctx.setContractBusNo(pre.getContractBusNo());
        ctx.setMedfeeSumamt(pre.getMedfeeSumamt());
        ctx.setTenantId(pre.getTenantId());
        return ctx;
    }

    private YbSettlementRecord findSettlementRecord(OpRevokeSettleCommand command, Long tenantId) {
        return ChsSettlementRecordLookup.requireOne(
            settlementRecordRepository,
            command.getSettlementRecordId(),
            command.getPaymentId(),
            command.getSetlId(),
            tenantId);
    }

    private YbClinicPreSettle resolvePreSettleForRevoke(
        YbSettlementRecord record,
        OpRevokeSettleCommand command,
        Long tenantId) {
        if (command.getPreSettleId() != null) {
            return clinicPreSettleRepository.findById(command.getPreSettleId()).orElse(null);
        }
        if (record.getPreSettleId() != null) {
            return clinicPreSettleRepository.findById(record.getPreSettleId()).orElse(null);
        }
        if (record.getPaymentId() != null) {
            return clinicPreSettleRepository
                .findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    record.getPaymentId(), tenantId)
                .orElse(null);
        }
        return null;
    }

    private String resolveContractBusNoForRevoke(
        YbSettlementRecord record,
        YbClinicPreSettle pre,
        Long tenantId) {
        if (pre != null && StringUtils.isNotBlank(pre.getContractBusNo())) {
            return pre.getContractBusNo().trim();
        }
        if (record.getPreSettleId() != null) {
            return clinicPreSettleRepository.findById(record.getPreSettleId())
                .map(YbClinicPreSettle::getContractBusNo)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法解析 contractBusNo"));
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法解析 contractBusNo");
    }

    private OpSettleContext buildMinimalContext(YbSettlementRecord record) {
        OpSettleContext ctx = new OpSettleContext();
        ctx.setContractId(record.getContractId());
        ctx.setEncounterId(record.getEncounterId());
        ctx.setPsnNo(record.getPsnNo());
        ctx.setTenantId(record.getTenantId());
        return ctx;
    }

    private ContractConfig requireContractConfig(String contractBusNo) {
        ContractConfig config = ContractConfigLoader.getByBusNo(contractBusNo);
        if (config == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_NOT_FOUND, "未找到契约配置: " + contractBusNo);
        }
        return config;
    }

    private InvokeContext buildInvokeContext(OpSettleContext context) {
        InvokeContext.InvokeContextBuilder builder = InvokeContext.builder();
        if (context.getOperatorId() != null) {
            builder.operatorId(String.valueOf(context.getOperatorId()));
        }
        if (StringUtils.isNotBlank(context.getInsuplcAdmdvs())) {
            builder.insuplcAdmdvs(context.getInsuplcAdmdvs());
        }
        return builder.build();
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

    private void validateRegistrationContext(OpSettleContext context) {
        if (context == null || context.getEncounterId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "encounterId 不能为空");
        }
        if (context.getContractId() == null) {
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
    }

    private void validatePreSettleContext(OpSettleContext context) {
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
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "收费明细不能为空");
        }
    }

    private ResultBody invoke1101WithIdCardFallback(
        YbPersonQueryCommand command,
        InvokeContext invokeContext,
        JSONObject input) {
        ResultBody body = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.PERSON_INFO, input, invokeContext);
        if (!shouldRetry1101WithIdCard(body, command)) {
            return body;
        }
        String certno = command.getCertno().trim();
        JSONObject input02 = ClinicOutpatientPlatformAssembler.assemble1101(
            "02", certno, null, command.getPsnCertType(), certno);
        ResultBody retried = gatewayClient.invoke(
            command.getContractBusNo(), ChsInfNo.PERSON_INFO, input02, invokeContext);
        if (retried != null && ChsPlatformCodes.INFCODE_SUCCESS.equals(retried.getInfcode())) {
            command.setMdtrtCertType("02");
            command.setMdtrtCertNo(certno);
            command.setCardSn(null);
        }
        return retried;
    }

    private boolean shouldRetry1101WithIdCard(ResultBody body, YbPersonQueryCommand command) {
        if (body == null || ChsPlatformCodes.INFCODE_SUCCESS.equals(body.getInfcode())) {
            return false;
        }
        if (!"03".equals(StringUtils.trimToEmpty(command.getMdtrtCertType()))) {
            return false;
        }
        if (StringUtils.isBlank(command.getCertno())) {
            return false;
        }
        String err = ChsTextSupport.platformErrMsg(body, "");
        return err.contains("解密卡串") || err.contains("encodeCardReturnCardNo");
    }

    private void validatePersonQuery(YbPersonQueryCommand command) {
        if (command == null || command.getContractId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractId 不能为空");
        }
        if (StringUtils.isBlank(command.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "contractBusNo 不能为空");
        }
        if (StringUtils.isBlank(command.getMdtrtCertType()) || StringUtils.isBlank(command.getMdtrtCertNo())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "就诊凭证不能为空");
        }
        if ("03".equals(command.getMdtrtCertType().trim()) && StringUtils.isBlank(command.getCardSn())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "社保卡读卡缺少卡识别码 card_sn");
        }
        if ("01".equals(command.getMdtrtCertType().trim())) {
            if (StringUtils.isBlank(command.getCertno())) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "电子凭证读码缺少证件号码 certno");
            }
            if (StringUtils.isBlank(command.getPsnName())) {
                throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "电子凭证读码缺少人员姓名 psn_name");
            }
        }
    }

    private YbPersonInfoResult mapPersonCache(YbPersonCache row) {
        if (StringUtils.isNotBlank(row.getSnapshotJson())) {
            try {
                JSONObject output = JSONObject.parseObject(row.getSnapshotJson().trim());
                JSONObject baseinfo = output.getJSONObject("baseinfo");
                if (baseinfo != null) {
                    return parse1101(baseinfo, output, row.getInsuplcAdmdvs());
                }
            } catch (RuntimeException ex) {
                log.warn("1101 人员缓存 snapshot 解析失败 psnNo={}: {}", row.getPsnNo(), ex.getMessage());
            }
        }
        YbPersonInfoResult result = new YbPersonInfoResult();
        result.setPsnNo(row.getPsnNo());
        result.setInsuplcAdmdvs(row.getInsuplcAdmdvs());
        result.setInsutype(row.getInsutype());
        result.setBalc(row.getBalc());
        return result;
    }

    private YbPersonInfoResult parse1101(JSONObject baseinfo, JSONObject output, String preferredAdmdvs) {
        YbPersonInfoResult result = new YbPersonInfoResult();
        result.setPsnNo(baseinfo.getString("psn_no"));
        result.setPsnName(baseinfo.getString("psn_name"));
        result.setCertno(baseinfo.getString("certno"));
        result.setGend(baseinfo.getString("gend"));
        result.setBrdy(baseinfo.getString("brdy"));
        if (output.getJSONArray("insuinfo") != null) {
            for (int i = 0; i < output.getJSONArray("insuinfo").size(); i++) {
                result.getInsuinfoList().add(mapInsuInfoRow(output.getJSONArray("insuinfo").getJSONObject(i)));
            }
        }
        if (output.getJSONArray("idetinfo") != null) {
            for (int i = 0; i < output.getJSONArray("idetinfo").size(); i++) {
                result.getIdetinfoList().add(mapIdetInfoRow(output.getJSONArray("idetinfo").getJSONObject(i)));
            }
        }
        if (!result.getInsuinfoList().isEmpty()) {
            YbInsuInfoRow picked = result.getInsuinfoList().get(0);
            if (StringUtils.isNotBlank(preferredAdmdvs)) {
                for (YbInsuInfoRow row : result.getInsuinfoList()) {
                    if (preferredAdmdvs.equals(row.getInsuplcAdmdvs())) {
                        picked = row;
                        break;
                    }
                }
            }
            result.setInsuplcAdmdvs(picked.getInsuplcAdmdvs());
            result.setInsutype(picked.getInsutype());
            result.setBalc(picked.getBalc());
        }
        return result;
    }

    private YbInsuInfoRow mapInsuInfoRow(JSONObject item) {
        YbInsuInfoRow row = new YbInsuInfoRow();
        row.setInsuplcAdmdvs(item.getString("insuplc_admdvs"));
        row.setInsutype(item.getString("insutype"));
        row.setPsnType(item.getString("psn_type"));
        row.setPsnInsuStas(item.getString("psn_insu_stas"));
        row.setPsnInsuDate(item.getString("psn_insu_date"));
        row.setPausInsuDate(item.getString("paus_insu_date"));
        row.setCvlservFlag(item.getString("cvlserv_flag"));
        row.setEmpName(item.getString("emp_name"));
        String balc = item.getString("balc");
        if (StringUtils.isNotBlank(balc)) {
            row.setBalc(new BigDecimal(balc.trim()));
        }
        return row;
    }

    private YbIdetInfoRow mapIdetInfoRow(JSONObject item) {
        YbIdetInfoRow row = new YbIdetInfoRow();
        row.setPsnIdetType(item.getString("psn_idet_type"));
        row.setPsnTypeLv(item.getString("psn_type_lv"));
        row.setMemo(item.getString("memo"));
        row.setBegntime(item.getString("begntime"));
        row.setEndtime(item.getString("endtime"));
        return row;
    }

    private Long resolveTenantId(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        return TenantUtils.getCurrentTenantId();
    }
}
