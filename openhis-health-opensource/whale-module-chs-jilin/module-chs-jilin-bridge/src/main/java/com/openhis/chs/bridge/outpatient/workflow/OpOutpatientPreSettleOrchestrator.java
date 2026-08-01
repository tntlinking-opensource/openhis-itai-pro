/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.openhis.chs.bridge.outpatient.invoker.Op2201Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2202Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2203Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2204Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2205Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2206Invoker;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.chs.domain.outpatient.command.OpPreSettleBatchCommand;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.command.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.command.OpSelfPayRollup;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.result.OpPreSettleBatchResult;
import com.openhis.chs.domain.outpatient.result.OpPreSettleGroupResult;
import com.openhis.chs.domain.outpatient.result.OpPreSettleResult;
import com.openhis.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.openhis.chs.domain.share.constants.ChsOutpatientRegisterFeeConstants;
import com.openhis.chs.domain.share.enums.MedType;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.observability.ChsInvokeLogContext;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2204OutputParser;
import com.openhis.chs.gateway.support.ChargeBatchInvokeLogSupport;
import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 【2201→2203→2204→2206】门诊医保预结算编排。
 *
 * <p>Wave A：挂号费（med_type=12）在 2201 已写 map 后若 2203/2204/2206 失败，
 * 自动 【2205（有批次）→2202(12)】收口，避免孤儿平台挂号；关支付窗成功预结算后不走本收口。</p>
 */
@Component
@RequiredArgsConstructor
public class OpOutpatientPreSettleOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OpOutpatientPreSettleOrchestrator.class);

    private final Op2201Invoker op2201Invoker;
    private final Op2202Invoker op2202Invoker;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
    private final YbClinicFeeUploadDomainService clinicFeeUploadDomainService;
    private final Op2203Invoker op2203Invoker;
    private final Op2204Invoker op2204Invoker;
    private final Op2206Invoker op2206Invoker;
    private final Op2205Invoker op2205Invoker;

    /**
     * 单笔门诊医保预结算：2201→2203→2204→2206 链式出站并落预结算快照。
     *
     * @param context 预结算命令（须已由 ContextLoader 补全）
     * @return 预结算分摊结果
     */
    public OpPreSettleResult invoke(OpPreSettleCommand context) {
        OutpatientSettleCommandAssembler.validatePreSettleContext(context);
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(context.getTenantId());
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
     * 批量混合预结算：逐组 invoke，失败时对已完成组执行 2205。
     * <p>单笔 invoke 内部已对挂号费 med_type=12 做 2202 收口；批量已成功组若含挂号费，
     * 其 map 在后续「作废」或下次预结算失败链再清（组结果未携 encounter，避免半截 2202）。</p>
     *
     * @param command 批量命令
     * @return 批量预结算结果
     */
    public OpPreSettleBatchResult invokeBatch(OpPreSettleBatchCommand command) {
        List<OpPreSettleCommand> enrichedGroups = List.of();
        if (command.getEnrichedGroups() != null) {
            enrichedGroups = command.getEnrichedGroups();
        }
        List<OpPreSettleGroupResult> completedGroups = new ArrayList<>();
        try {
            for (OpPreSettleCommand enriched : enrichedGroups) {
                OpPreSettleResult platform = invoke(enriched);
                completedGroups.add(toGroupResult(platform, enriched));
            }
        } catch (RuntimeException ex) {
            cancelCompletedGroups(completedGroups);
            throw ex;
        }
        OpSelfPayRollup selfPayRollup = command.getSelfPayRollup();
        BigDecimal totalPsnCashPay = BigDecimal.ZERO;
        for (OpPreSettleGroupResult group : completedGroups) {
            if (group.getPsnCashPay() != null) {
                totalPsnCashPay = totalPsnCashPay.add(group.getPsnCashPay());
            }
        }
        if (selfPayRollup != null && selfPayRollup.getTotalAmount() != null) {
            totalPsnCashPay = totalPsnCashPay.add(selfPayRollup.getTotalAmount());
        }
        return new OpPreSettleBatchResult()
            .setGroups(completedGroups)
            .setSelfPayRollup(selfPayRollup)
            .setTotalPsnCashPay(totalPsnCashPay);
    }

    /**
     * 执行 2201→2203→2204→2206；中途失败时按 §3.1.1 收口。
     *
     * @param context  门诊预结算上下文
     * @param tenantId 租户 ID
     * @return 预结算结果
     */
    private OpPreSettleResult doInvoke(OpPreSettleCommand context, Long tenantId) {
        ContractConfig config = OutpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(context);
        String mdtrtId = null;
        String chrgBchno = null;
        boolean resolvedMdtrt = false;
        try {
            // 重试预结算前先 2205 清掉本就诊未结算旧批次，避免 feedetl_sn 重复主键
            this.revokePriorActivePreSettles(context, tenantId);
            // Pre：挂号费改传 11 时，先 2202 清掉 12 残留及可能被锁成「门诊挂号」的 11 map
            this.revokeStaleRegisterFeeMapsBeforeSettle(context, tenantId);

            // Cmd：查/建平台挂号（map miss 才 2201）
            mdtrtId = resolveMdtrtId(context, config, invokeContext, tenantId);
            resolvedMdtrt = isNotEmpty(mdtrtId);
            long seed = SnowflakeIdGenerator.nextId();
            String batchNo = OpOutpatientSettleInputAssembler.newChrgBchno(config, seed);
            chrgBchno = batchNo;
            // Pre：日志上下文写入批次号（须用 effectively final 的 batchNo）
            ChsInvokeLogContextHolder.merge(ctx -> ctx.setChrgBchno(batchNo));

            op2203Invoker.invoke(context.getContractBusNo(), context, mdtrtId, invokeContext);

            ResultBody body2204 = op2204Invoker.invoke(
                context.getContractBusNo(), context, mdtrtId, batchNo, invokeContext);
            clinicFeeUploadDomainService.persistFrom2204(
                context.getEncounterId(),
                mdtrtId,
                batchNo,
                context.getChargeLines(),
                Op2204OutputParser.indexByFeedetlSn(body2204),
                tenantId);

            BigDecimal medfeeSumamt = context.getMedfeeSumamt();
            ChsSetlinfoOutput setlinfo = op2206Invoker.invoke(
                context.getContractBusNo(), context, mdtrtId, batchNo, medfeeSumamt, invokeContext);
            PlatformSetlinfoFields platformSetlinfo = SetlinfoIntegrationMapper.toPlatformFields(setlinfo);

            YbClinicPreSettle row = clinicPreSettleDomainService.saveActiveFrom2206(
                context, mdtrtId, batchNo, platformSetlinfo, toChargeItemIdsJson(context), tenantId);
            clinicFeeUploadDomainService.linkPreSettle(batchNo, row.getId(), tenantId);
            ChargeBatchInvokeLogSupport.mergeBatch(ChargeBatch.fromClinicPreSettle(row));

            return new OpPreSettleResult()
                .setPreSettleId(row.getId())
                .setChrgBchno(batchNo)
                .setMdtrtId(mdtrtId)
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setFundPaySumamt(row.getFundPaySumamt())
                .setAcctPay(row.getAcctPay())
                .setPsnCashPay(row.getPsnCashPay());
        } catch (RuntimeException ex) {
            // Cmd：预结算未成功 → 自动清本单脏状态（与关窗不撤区分）
            this.rollbackIncompleteRegisterFeePreSettle(context, chrgBchno, mdtrtId, resolvedMdtrt);
            throw ex;
        }
    }

    /**
     * 挂号费现行 med_type=11 时，预结算前清掉可能类别冲突的平台 map，再走新 2201。
     *
     * <p>含：历史 med_type=12；以及已指向「门诊挂号」就诊号的 med_type=11（改码后复用会导致 2203 类别不一致）。</p>
     *
     * @param context  预结算上下文
     * @param tenantId 租户 ID
     */
    private void revokeStaleRegisterFeeMapsBeforeSettle(OpPreSettleCommand context, Long tenantId) {
        if (!Boolean.TRUE.equals(context.getRegisterFeePath())) {
            return;
        }
        String medType = OpOutpatientSettleInputAssembler.resolveMedTypeForContext(context);
        if (ChsOutpatientRegisterFeeConstants.MED_TYPE_OUTPATIENT_REGISTRATION.equals(medType)) {
            return;
        }
        if (!MedType.GENERAL_OUTPATIENT.getCode().equals(medType)) {
            return;
        }
        for (String staleMedType : List.of(
            ChsOutpatientRegisterFeeConstants.MED_TYPE_OUTPATIENT_REGISTRATION,
            MedType.GENERAL_OUTPATIENT.getCode())) {
            try {
                OpRevokePlatformRegistrationCommand revokeCommand = new OpRevokePlatformRegistrationCommand();
                revokeCommand.setEncounterId(context.getEncounterId());
                revokeCommand.setContractId(context.getContractId());
                revokeCommand.setContractBusNo(context.getContractBusNo());
                revokeCommand.setMedType(staleMedType);
                revokeCommand.setInsuplcAdmdvs(context.getInsuplcAdmdvs());
                revokeCommand.setOperatorId(context.getOperatorId());
                revokeCommand.setTenantId(tenantId);
                op2202Invoker.invoke(revokeCommand);
            } catch (RuntimeException ex) {
                log.warn(
                    "预结算前清理挂号费 map(medType={}) 失败 encounterId={}: {}",
                    staleMedType,
                    context.getEncounterId(),
                    ex.getMessage());
            }
        }
    }

    /**
     * 预结算前撤销本就诊同契约下 ACTIVE 旧批次（上次预结算成功、正式收费失败后的重试场景）。
     *
     * @param context  预结算上下文
     * @param tenantId 租户 ID
     */
    private void revokePriorActivePreSettles(OpPreSettleCommand context, Long tenantId) {
        List<YbClinicPreSettle> priors = clinicPreSettleQueryService.listActiveByEncounterIdAndContractId(
            context.getEncounterId(), context.getContractId(), tenantId);
        for (YbClinicPreSettle prior : priors) {
            if (isEmpty(prior.getChrgBchno())) {
                continue;
            }
            try {
                op2205Invoker.invoke(new OpRevokeFeeDetailCommand()
                    .setChrgBchno(prior.getChrgBchno().trim())
                    .setContractBusNo(context.getContractBusNo())
                    .setInsuplcAdmdvs(context.getInsuplcAdmdvs())
                    .setOperatorId(context.getOperatorId())
                    .setTenantId(tenantId));
            } catch (RuntimeException ex) {
                log.warn(
                    "预结算前撤销旧批次失败 encounterId={} chrgBchno={}: {}",
                    context.getEncounterId(),
                    prior.getChrgBchno(),
                    ex.getMessage());
                throw new BusinessException(
                    ChsErrorCodes.PLATFORM_FAILED,
                    "存在未结算的医保费用明细且撤销失败，请稍后重试。批次号：" + prior.getChrgBchno().trim());
            }
        }
    }

    /**
     * 预结算中途失败收口：有批次则 2205；挂号费 med_type=12 且已解析到 mdtrt 则 2202。
     * 收口再失败只记日志，由上层返回原错误，现场可用「作废」兜底。
     *
     * @param context       预结算上下文
     * @param chrgBchno     本单批次号（可能尚未生成）
     * @param mdtrtId       平台就诊号（可能尚未生成）
     * @param resolvedMdtrt 是否已 resolve 到平台就诊号
     */
    private void rollbackIncompleteRegisterFeePreSettle(
        OpPreSettleCommand context,
        String chrgBchno,
        String mdtrtId,
        boolean resolvedMdtrt) {
        if (isNotEmpty(chrgBchno)) {
            try {
                op2205Invoker.invoke(new OpRevokeFeeDetailCommand()
                    .setChrgBchno(chrgBchno.trim())
                    .setMdtrtId(mdtrtId)
                    .setPsnNo(context.getPsnNo())
                    .setContractBusNo(context.getContractBusNo())
                    .setInsuplcAdmdvs(context.getInsuplcAdmdvs())
                    .setOperatorId(context.getOperatorId())
                    .setTenantId(context.getTenantId()));
            } catch (RuntimeException rollbackEx) {
                log.warn(
                    "预结算失败后 2205 收口失败 encounterId={} chrgBchno={}: {}",
                    context.getEncounterId(),
                    chrgBchno,
                    rollbackEx.getMessage());
            }
        }
        String medType = OpOutpatientSettleInputAssembler.resolveMedTypeForContext(context);
        // Gate：仅挂号费批次自动 2202，避免误撤普通门诊/慢病 map（挂号费上报可为 11）
        if (!Boolean.TRUE.equals(context.getRegisterFeePath())
            && !ChsOutpatientRegisterFeeConstants.MED_TYPE_OUTPATIENT_REGISTRATION.equals(medType)) {
            return;
        }
        if (!resolvedMdtrt) {
            return;
        }
        try {
            OpRevokePlatformRegistrationCommand revokeCommand = new OpRevokePlatformRegistrationCommand();
            revokeCommand.setEncounterId(context.getEncounterId());
            revokeCommand.setContractId(context.getContractId());
            revokeCommand.setContractBusNo(context.getContractBusNo());
            revokeCommand.setMedType(medType);
            revokeCommand.setInsuplcAdmdvs(context.getInsuplcAdmdvs());
            revokeCommand.setOperatorId(context.getOperatorId());
            revokeCommand.setTenantId(context.getTenantId());
            op2202Invoker.invoke(revokeCommand);
        } catch (RuntimeException rollbackEx) {
            log.warn(
                "预结算失败后 2202(12) 收口失败 encounterId={}: {}",
                context.getEncounterId(),
                rollbackEx.getMessage());
        }
    }

    /**
     * 解析或注册医保就诊 ID（2201）。
     *
     * @param context       门诊预结算上下文
     * @param config        契约配置
     * @param invokeContext 出站调用上下文
     * @param tenantId      租户 ID
     * @return 平台 mdtrt_id
     */
    private String resolveMdtrtId(
            OpPreSettleCommand context,
            ContractConfig config,
            InvokeContext invokeContext,
            Long tenantId) {
        return op2201Invoker.resolveOrRegister(
            context.getContractBusNo(), context, invokeContext, tenantId);
    }

    /**
     * 批量预结算失败时撤销已完成组的费用明细（2205）。
     *
     * @param completedGroups 已成功完成的预结算组列表
     */
    private void cancelCompletedGroups(List<OpPreSettleGroupResult> completedGroups) {
        for (OpPreSettleGroupResult group : completedGroups) {
            if (group == null || isEmpty(group.getChrgBchno())) {
                continue;
            }
            try {
                op2205Invoker.invoke(
                    new OpRevokeFeeDetailCommand().setChrgBchno(group.getChrgBchno().trim()));
            } catch (RuntimeException rollbackEx) {
                log.warn(
                    "批量预结算回滚 2205 失败 chrgBchno={}: {}",
                    group.getChrgBchno(),
                    rollbackEx.getMessage());
            }
        }
    }

    /**
     * 将单笔预结算结果映射为批量分组结果，附带组级元数据。
     *
     * @param platform 平台预结算结果
     * @param enriched 组级预结算上下文
     * @return 批量分组结果
     */
    private static OpPreSettleGroupResult toGroupResult(OpPreSettleResult platform, OpPreSettleCommand enriched) {
        OpPreSettleGroupResult result = new OpPreSettleGroupResult();
        result.setPreSettleId(platform.getPreSettleId());
        result.setChrgBchno(platform.getChrgBchno());
        result.setMdtrtId(platform.getMdtrtId());
        result.setMedfeeSumamt(platform.getMedfeeSumamt());
        result.setFundPaySumamt(platform.getFundPaySumamt());
        result.setAcctPay(platform.getAcctPay());
        result.setPsnCashPay(platform.getPsnCashPay());
        result.setGroupIndex(enriched.getGroupIndex());
        result.setMedType(enriched.getMedType());
        result.setDiseCodg(enriched.getGroupDiseCodg());
        result.setAccountId(enriched.getAccountId());
        result.setChargeItemIds(enriched.getChargeItemIds());
        return result;
    }

    /**
     * 将收费明细行的 chargeItemId 序列化为 JSON 数组字符串。
     *
     * @param context 门诊预结算上下文
     * @return JSON 数组字符串；无明细时返回 null
     */
    private static String toChargeItemIdsJson(OpPreSettleCommand context) {
        if (context.getChargeLines() == null || context.getChargeLines().isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>(context.getChargeLines().size());
        for (int i = 0; i < context.getChargeLines().size(); i++) {
            ids.add(context.getChargeLines().get(i).getChargeItemId());
        }
        return ChargeItemIdsSupport.toJsonArray(ids);
    }
}
