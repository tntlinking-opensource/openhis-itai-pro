/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.outpatient.command.OpPreSettleBatchCommand;
import com.openhis.chs.domain.outpatient.result.OpPreSettleBatchResult;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.result.OpPreSettleGroupResult;
import com.openhis.chs.domain.outpatient.result.OpPreSettleResult;
import com.openhis.chs.domain.outpatient.command.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.command.OpSelfPayRollup;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.openhis.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.openhis.chs.domain.settlement.model.ChargeBatch;
import com.openhis.chs.domain.settlement.support.ChargeItemIdsSupport;
import com.openhis.chs.gateway.observability.ChsInvokeLogContext;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2204OutputParser;
import com.openhis.chs.gateway.scheme.outpatient.assembler.OpOutpatientSettleInputAssembler;
import com.openhis.chs.gateway.support.ChargeBatchInvokeLogSupport;
import com.openhis.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.openhis.chs.bridge.outpatient.invoker.Op2201Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2203Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2204Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2205Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2206Invoker;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.whale.core.infrastructure.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 【2201→2203→2204→2206】门诊医保预结算编排（失败时 2205 回滚）。
 *
 * <p>出站由 {@link Op2201Invoker}、{@link Op2203Invoker}、{@link Op2204Invoker}、{@link Op2206Invoker}、{@link Op2205Invoker} 完成；本类不调 gateway。
 * 写 yb_clinic_fee_upload、yb_clinic_pre_settle。</p>
 */
@Component
@RequiredArgsConstructor
public class OpOutpatientPreSettleOrchestrator {

    private final Op2201Invoker op2201Invoker;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbClinicFeeUploadDomainService clinicFeeUploadDomainService;
    private final Op2203Invoker op2203Invoker;
    private final Op2204Invoker op2204Invoker;
    private final Op2206Invoker op2206Invoker;
    private final Op2205Invoker op2205Invoker;

    /** 单笔门诊医保预结算：2201→2203→2204→2206 链式出站并落预结算快照。 */
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

    /** 批量混合预结算：逐组 invoke，失败时撤销已完成组的 2204/2205。 */
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
     * 执行 2201→2203→2204→2206 门诊医保预结算链并落预结算快照。
     *
     * @param context 门诊预结算上下文
     * @param tenantId 租户 ID
     * @return 预结算结果
     */
    private OpPreSettleResult doInvoke(OpPreSettleCommand context, Long tenantId) {
        ContractConfig config = OutpatientSettleCommandAssembler.requireContractConfig(context.getContractBusNo());
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(context);
        String mdtrtId = resolveMdtrtId(context, config, invokeContext, tenantId);
        long seed = SnowflakeIdGenerator.nextId();
        String chrgBchno = OpOutpatientSettleInputAssembler.newChrgBchno(config, seed);
        ChsInvokeLogContextHolder.merge(ctx -> ctx.setChrgBchno(chrgBchno));

        op2203Invoker.invoke(context.getContractBusNo(), context, mdtrtId, invokeContext);

        ResultBody body2204 = op2204Invoker.invoke(
            context.getContractBusNo(), context, mdtrtId, chrgBchno, invokeContext);
        clinicFeeUploadDomainService.persistFrom2204(
            context.getEncounterId(),
            mdtrtId,
            chrgBchno,
            context.getChargeLines(),
            Op2204OutputParser.indexByFeedetlSn(body2204),
            tenantId);

        BigDecimal medfeeSumamt = context.getMedfeeSumamt();
        ChsSetlinfoOutput setlinfo = op2206Invoker.invoke(
            context.getContractBusNo(), context, mdtrtId, chrgBchno, medfeeSumamt, invokeContext);
        PlatformSetlinfoFields platformSetlinfo = SetlinfoIntegrationMapper.toPlatformFields(setlinfo);

        YbClinicPreSettle row = clinicPreSettleDomainService.saveActiveFrom2206(
            context, mdtrtId, chrgBchno, platformSetlinfo, toChargeItemIdsJson(context), tenantId);
        clinicFeeUploadDomainService.linkPreSettle(chrgBchno, row.getId(), tenantId);
        ChargeBatchInvokeLogSupport.mergeBatch(ChargeBatch.fromClinicPreSettle(row));

        return new OpPreSettleResult()
            .setPreSettleId(row.getId())
            .setChrgBchno(chrgBchno)
            .setMdtrtId(mdtrtId)
            .setMedfeeSumamt(row.getMedfeeSumamt())
            .setFundPaySumamt(row.getFundPaySumamt())
            .setAcctPay(row.getAcctPay())
            .setPsnCashPay(row.getPsnCashPay());
    }

    /**
     * 解析或注册医保就诊 ID（2201）。
     *
     * @param context 门诊预结算上下文
     * @param config 契约配置
     * @param invokeContext 出站调用上下文
     * @param tenantId 租户 ID
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
     * 批量预结算失败时撤销已完成组的 2204/2205 费用明细。
     *
     * @param completedGroups 已成功完成的预结算组列表
     */
    private void cancelCompletedGroups(List<OpPreSettleGroupResult> completedGroups) {
        for (OpPreSettleGroupResult group : completedGroups) {
            if (group == null || isEmpty(group.getChrgBchno())) {
                continue;
            }
            op2205Invoker.invoke(new OpRevokeFeeDetailCommand().setChrgBchno(group.getChrgBchno().trim()));
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
