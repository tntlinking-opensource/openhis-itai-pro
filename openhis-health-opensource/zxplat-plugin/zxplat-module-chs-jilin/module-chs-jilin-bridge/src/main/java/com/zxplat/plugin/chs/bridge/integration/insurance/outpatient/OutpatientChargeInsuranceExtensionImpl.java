/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.integration.insurance.outpatient;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.zxplat.plugin.chs.bridge.outpatient.assembler.ChsOutpatientInsuranceBatchPreSettleAssembler;
import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientInsuranceChargeGrouper;
import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientInsuranceChargeGrouper.OutpatientInsuranceChargeSplitResult;
import com.zxplat.plugin.chs.bridge.outpatient.assembler.OutpatientInsuranceChargeGrouper.OutpatientInsuranceSettleGroupPlan;
import com.zxplat.plugin.chs.bridge.outpatient.invoker.Op2205Invoker;
import com.zxplat.plugin.chs.bridge.outpatient.invoker.Op2207Invoker;
import com.zxplat.plugin.chs.bridge.outpatient.invoker.Op2208Invoker;
import com.zxplat.plugin.chs.bridge.outpatient.workflow.OpOutpatientPreSettleOrchestrator;
import com.zxplat.plugin.chs.bridge.settlement.invoker.Op2601Invoker;
import com.zxplat.plugin.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.zxplat.plugin.chs.domain.outpatient.command.OpPreSettleCommand;
import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.zxplat.plugin.chs.domain.outpatient.result.OpRevokeSettleResult;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.zxplat.plugin.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.zxplat.plugin.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementRecordDomainService;
import com.zxplat.plugin.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.enums.MedType;
import com.zxplat.plugin.chs.gateway.support.ChsCurrentOperatorResolver;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeConfirmExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeCorrectSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleBatchExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleListExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRebindSettlementPaymentExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeFeeDetailExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeConfirmExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleBatchExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeFeeDetailExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 吉林医保门诊收银结算 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link OutpatientChargeInsuranceExtension}</p>
 * <p><b>Health 调用方</b>：{@code OutpatientChargeAppService}、{@code OutpatientRegistrationAppService}、
 * {@code OutpatientRefundAppService}</p>
 * <p><b>Bridge 委托</b>：{@link OpOutpatientPreSettleOrchestrator}（2203→2204→2206）、
 * {@link Op2207Invoker}、{@link Op2208Invoker}、{@link Op2205Invoker}、{@link Op2601Invoker}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(OpOutpatientPreSettleOrchestrator.class)
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class OutpatientChargeInsuranceExtensionImpl implements OutpatientChargeInsuranceExtension {

    private final ChsInsuranceContractReader contractReader;
    private final OpPreSettleContextLoader contextLoader;
    private final OutpatientInsuranceChargeGrouper chargeGrouper;
    private final ChsOutpatientInsuranceBatchPreSettleAssembler batchPreSettleAssembler;
    private final OpOutpatientPreSettleOrchestrator preSettleOrchestrator;
    private final Op2207Invoker op2207Invoker;
    private final Op2208Invoker op2208Invoker;
    private final Op2205Invoker op2205Invoker;
    private final Op2601Invoker op2601Invoker;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    /** 支付侧医保桥表写：2208/2601 后与 yb_settlement_record 同态标 REVOKED。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 本地：返回 Extension 已启用。 */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /** 本地：判断合同是否为医保第三方合同。 */
    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return contractReader.isInsuranceContract(contractId);
    }

    /**
     * 【2203→2204→2206】门诊预结算，写入 yb_clinic_pre_settle。
     *
     * <p>与 batch 同源：按收费项绑定诊断解析 {@code med_type}/{@code dise_codg}，
     * 禁止仅依赖请求缺省 {@code 11}（慢病会错走普门 map）。</p>
     */
    @Override
    public OutpatientChargePreSettleExtensionOut preSettle(OutpatientChargePreSettleExtensionIn command) {
        if (command == null
            || command.getEncounterId() == null
            || command.getChargeItemIds() == null
            || command.getChargeItemIds().isEmpty()) {
            var enriched = contextLoader.enrichPreSettle(
                ChsOutpatientInsuranceIntegrationMapper.toPartialPreSettleCommand(command));
            return ChsOutpatientInsuranceIntegrationMapper.toExtensionPreSettleOut(
                preSettleOrchestrator.invoke(enriched));
        }
        OutpatientInsuranceChargeSplitResult split = chargeGrouper.split(
            command.getEncounterId(), command.getChargeItemIds());
        List<OutpatientInsuranceSettleGroupPlan> groups = split.getInsuranceGroups();
        if (groups == null || groups.isEmpty()) {
            var enriched = contextLoader.enrichPreSettle(
                ChsOutpatientInsuranceIntegrationMapper.toPartialPreSettleCommand(command));
            return ChsOutpatientInsuranceIntegrationMapper.toExtensionPreSettleOut(
                preSettleOrchestrator.invoke(enriched));
        }
        if (groups.size() > 1) {
            // Gate：多个病种、就诊类型或账户须分次收银；同一病种才允许一次 2206
            LinkedHashSet<String> medTypes = new LinkedHashSet<>();
            for (OutpatientInsuranceSettleGroupPlan group : groups) {
                if (group == null || group.getMedType() == null || group.getMedType().isBlank()) {
                    medTypes.add(MedType.GENERAL_OUTPATIENT.getCode());
                } else {
                    medTypes.add(group.getMedType().trim());
                }
            }
            if (medTypes.size() > 1) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_INVARIANT,
                    "普通门诊与慢病等须分次结算（起付线），请每次只勾选同一就诊类型的明细");
            }
            throw new BusinessException(
                ChsErrorCodes.SETTLEMENT_INVARIANT,
                "不同病种或医保账户须分开结算，每个诊断单独一笔。请每次只勾选同一病种的明细");
        }
        OutpatientInsuranceSettleGroupPlan plan = groups.get(0);
        List<Long> groupChargeItemIds = new ArrayList<>(plan.getChargeItems().size());
        for (ChargeItem item : plan.getChargeItems()) {
            groupChargeItemIds.add(item.getId());
        }
        OpPreSettleCommand partial = ChsOutpatientInsuranceIntegrationMapper.toPartialPreSettleCommand(command);
        partial.setMedType(plan.getMedType());
        partial.setChargeItemIds(groupChargeItemIds);
        OpPreSettleCommand enriched = contextLoader.enrichPreSettleForGroup(
            partial,
            groupChargeItemIds,
            plan.getMedType(),
            plan.getDiseCodg(),
            plan.getDiseName());
        enriched.setAccountId(plan.getAccountId());
        enriched.setGroupDiseCodg(plan.getDiseCodg());
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionPreSettleOut(
            preSettleOrchestrator.invoke(enriched));
    }

    /**
     * 【2203→2204→2206】批量门诊预结算。
     *
     * <p>医保组数须 ≤ 1，可与自费 rollup 同批。多组会在同一就诊上留下多批未结算明细。</p>
     */
    @Override
    public OutpatientChargePreSettleBatchExtensionOut preSettleBatch(OutpatientChargePreSettleBatchExtensionIn command) {
        // Gate：多医保结算组禁止出站（吉林 2206 按整张就诊未结算明细核对总额）
        this.assertAtMostOneInsuranceSettleGroup(command.getEncounterId(), command.getChargeItemIds());
        var enriched = batchPreSettleAssembler.enrichBatch(
            ChsOutpatientInsuranceIntegrationMapper.toPreSettleBatchCommand(command));
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionPreSettleBatchOut(
            preSettleOrchestrator.invokeBatch(enriched));
    }

    /**
     * 预览医保结算组数（账户|就诊类型|病种，不出站）。
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 待结算收费项
     * @return 医保组数；入参不全时 0
     */
    @Override
    public int countInsuranceSettleGroups(Long encounterId, List<Long> chargeItemIds) {
        if (encounterId == null || chargeItemIds == null || chargeItemIds.isEmpty()) {
            return 0;
        }
        OutpatientInsuranceChargeSplitResult split = chargeGrouper.split(encounterId, chargeItemIds);
        List<OutpatientInsuranceSettleGroupPlan> groups = split.getInsuranceGroups();
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        return groups.size();
    }

    /**
     * Gate：同批至多一个医保结算组；多组须分次收银。
     *
     * @param encounterId   就诊主键
     * @param chargeItemIds 待预结算收费项
     */
    private void assertAtMostOneInsuranceSettleGroup(Long encounterId, List<Long> chargeItemIds) {
        int groupCount = this.countInsuranceSettleGroups(encounterId, chargeItemIds);
        // 多病种同一就诊连续 2204，后一组 2206 总额对不上平台明细合计
        if (groupCount > 1) {
            throw new BusinessException(
                ChsErrorCodes.SETTLEMENT_INVARIANT,
                "不同慢特病须分开结算，每个病种单独一笔。请每次只勾选同一慢特病的明细");
        }
    }

    /** 【2207】本地落账前正式结算确认，写入 yb_settlement_record。 */
    @Override
    public OutpatientChargeConfirmExtensionOut confirmBeforeLocalSettle(OutpatientChargeConfirmExtensionIn command) {
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionConfirmOut(
            op2207Invoker.invoke(ChsOutpatientInsuranceIntegrationMapper.toConfirmCommand(command)));
    }

    /** 本地：按收银草稿 ID 查询活跃预结算快照，无网关出站。 */
    @Override
    public List<OutpatientChargePreSettleQueryExtensionOut> listActivePreSettlesByPaymentDraft(
            OutpatientChargePreSettleListExtensionIn query) {
        if (query == null || query.getPaymentDraftId() == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbClinicPreSettle> rows = clinicPreSettleQueryService
            .listActiveByPaymentDraftId(query.getPaymentDraftId(), tenantId);
        List<OutpatientChargePreSettleQueryExtensionOut> snapshots = new ArrayList<>();
        for (YbClinicPreSettle row : rows) {
            if (isEmpty(row.getChrgBchno())) {
                continue;
            }
            snapshots.add(new OutpatientChargePreSettleQueryExtensionOut()
                .setChargeBatchNo(row.getChrgBchno().trim())
                .setInsuredPersonNo(row.getPsnNo())
                .setInsuplcAdmdvs(row.getInsuplcAdmdvs())
                .setInsutype(row.getInsutype())
                .setPayerCertType(row.getMdtrtCertType())
                .setAcctUsedFlag(row.getAcctUsedFlag())
                .setMedfeeSumamt(row.getMedfeeSumamt())
                .setPsnCashPay(row.getPsnCashPay()));
        }
        return snapshots;
    }

    /** 本地：按 paymentId 查询结算回执列表，无网关出站。 */
    @Override
    public List<OutpatientChargeSettlementRecordExtensionOut> listSettlementsByPayment(Long paymentId) {
        if (paymentId == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbSettlementRecord> rows = settlementRecordQueryService.listByPaymentId(paymentId, tenantId);
        List<OutpatientChargeSettlementRecordExtensionOut> results = new ArrayList<>();
        for (YbSettlementRecord row : rows) {
            OutpatientChargeSettlementRecordExtensionOut out =
                ChsOutpatientInsuranceIntegrationMapper.toExtensionSettlementRecord(
                    ChsOutpatientInsuranceIntegrationMapper.toSettleRecordResult(row));
            if (row.getPreSettleId() != null) {
                YbClinicPreSettle pre = clinicPreSettleDomainService.findById(row.getPreSettleId());
                if (pre != null && tenantId.equals(pre.getTenantId()) && !Boolean.TRUE.equals(pre.getIsDeleted())) {
                    out.setChargeItemIds(parseChargeItemIdsText(pre.getChargeItemIds()));
                }
            }
            results.add(out);
        }
        return results;
    }

    /** 本地：孤儿 2207 补收时改挂回执与预结算到新草稿；任一失败则抛错。 */
    @Override
    public int rebindConfirmedSettlementsToPayment(OutpatientChargeRebindSettlementPaymentExtensionIn command) {
        if (command == null
            || command.getNewPaymentId() == null
            || command.getSettlementRecordIds() == null
            || command.getSettlementRecordIds().isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.SETTLEMENT_INVARIANT,
                "银联补收改挂参数不完整");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        Long newPaymentId = command.getNewPaymentId();
        int rebound = 0;
        for (Long settlementRecordId : command.getSettlementRecordIds()) {
            if (settlementRecordId == null) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_INVARIANT,
                    "银联补收改挂失败：结算回执主键为空");
            }
            YbSettlementRecord row = settlementRecordDomainService
                .findByIdAndTenant(settlementRecordId, tenantId)
                .orElse(null);
            if (row == null) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_INVARIANT,
                    "银联补收改挂失败：结算回执不存在 " + settlementRecordId);
            }
            boolean ok = settlementRecordDomainService.rebindPaymentId(settlementRecordId, newPaymentId, tenantId);
            if (!ok) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_INVARIANT,
                    "银联补收改挂失败：回执非 CONFIRMED 或不可改挂 " + settlementRecordId);
            }
            if (row.getPreSettleId() != null) {
                clinicPreSettleDomainService.rebindPaymentDraftId(row.getPreSettleId(), newPaymentId, tenantId);
            }
            rebound += 1;
        }
        if (rebound != command.getSettlementRecordIds().size()) {
            throw new BusinessException(
                ChsErrorCodes.SETTLEMENT_INVARIANT,
                "银联补收改挂条数不一致");
        }
        return rebound;
    }

    /** 解析预结算 charge_item_ids（JSON 数组或逗号串）。 */
    private static List<Long> parseChargeItemIdsText(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.trim();
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1).trim();
        }
        if (text.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String token = part.trim();
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
                token = token.substring(1, token.length() - 1).trim();
            }
            if (token.isEmpty()) {
                continue;
            }
            try {
                unique.add(Long.parseLong(token));
            } catch (NumberFormatException ignored) {
                // 跳过非法段
            }
        }
        return new ArrayList<>(unique);
    }

    /** 【2208】撤销门诊结算，标记回执已撤销，并同步支付侧桥表 REVOKED。 */
    @Override
    public OutpatientChargeRevokeSettleExtensionOut revokeSettle(OutpatientChargeRevokeSettleExtensionIn command) {
        OpRevokeSettleResult result = op2208Invoker.invoke(
            ChsOutpatientInsuranceIntegrationMapper.toRevokeSettleCommand(command));
        if (result == null) {
            return new OutpatientChargeRevokeSettleExtensionOut();
        }
        // Cmd：fin_payment_yb_settlement 与 yb_settlement_record 同态（日结个账/统筹依赖 CONFIRMED）
        this.paymentReconciliationDomainService.markYbSettlementsRevoked(
            result.getSettlementRecordId(),
            result.getSetlId());
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionRevokeSettleOut(result);
    }

    /** 【2205】撤销门诊费用明细上传。 */
    @Override
    public OutpatientChargeRevokeFeeDetailExtensionOut revokeFeeDetails(
        OutpatientChargeRevokeFeeDetailExtensionIn command) {
        var result = op2205Invoker.invoke(
            ChsOutpatientInsuranceIntegrationMapper.toRevokeFeeDetailCommand(command));
        OutpatientChargeRevokeFeeDetailExtensionOut out = new OutpatientChargeRevokeFeeDetailExtensionOut();
        if (result != null) {
            out.setChargeBatchNo(result.getChrgBchno());
            out.setRevoked(result.isRevoked());
        }
        return out;
    }

    /** 【2601】冲正原 2207 结算交易，写入 yb_settlement_correction，并同步支付侧桥表 REVOKED。 */
    @Override
    public void correctSettleTransaction(OutpatientChargeCorrectSettleExtensionIn command) {
        if (command == null || command.getSettlementRecordId() == null) {
            throw new BusinessException("INS-014", "2601 冲正缺少 settlementRecordId");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        YbSettlementRecord record = settlementRecordDomainService
            .findByIdAndTenant(command.getSettlementRecordId(), tenantId)
            .orElseThrow(() -> new BusinessException("INS-014", "settlement record not found"));
        Long operatorId = currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录");
        SettlementCorrectionCommand domainCommand = OutpatientChargeInsuranceIntegrationMapper.toCorrectionCommand(
            command, record, operatorId, tenantId);
        op2601Invoker.invoke(domainCommand);
        // Cmd：Invoker 已同步桥表 REVOKED + 软删统筹/个账；此处幂等再标 REVOKED，防旧路径漏调
        this.paymentReconciliationDomainService.markYbSettlementsRevoked(
            record.getId(),
            record.getSetlId());
        this.paymentReconciliationDomainService.softDeleteInsuranceFundAcctDetailsBySetlId(
            record.getSetlId());
    }
}
