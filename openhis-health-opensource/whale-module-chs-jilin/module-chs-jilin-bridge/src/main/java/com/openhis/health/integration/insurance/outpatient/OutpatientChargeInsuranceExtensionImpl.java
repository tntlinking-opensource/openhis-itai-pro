/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.outpatient;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.bridge.outpatient.invoker.Op2205Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2207Invoker;
import com.openhis.chs.bridge.outpatient.invoker.Op2208Invoker;
import com.openhis.chs.bridge.outpatient.workflow.OpOutpatientPreSettleOrchestrator;
import com.openhis.chs.bridge.settlement.invoker.Op2601Invoker;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.outpatient.result.OpRevokeSettleResult;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.loader.OpPreSettleContextLoader;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordDomainService;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeCorrectSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleBatchExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleListExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeFeeDetailExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeConfirmExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleBatchExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeFeeDetailExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.openhis.chs.bridge.outpatient.assembler.ChsOutpatientInsuranceBatchPreSettleAssembler;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class OutpatientChargeInsuranceExtensionImpl implements OutpatientChargeInsuranceExtension {

    private final ChsInsuranceContractReader contractReader;
    private final OpPreSettleContextLoader contextLoader;
    private final ChsOutpatientInsuranceBatchPreSettleAssembler batchPreSettleAssembler;
    private final OpOutpatientPreSettleOrchestrator preSettleOrchestrator;
    private final Op2207Invoker op2207Invoker;
    private final Op2208Invoker op2208Invoker;
    private final Op2205Invoker op2205Invoker;
    private final Op2601Invoker op2601Invoker;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
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

    /** 【2203→2204→2206】门诊预结算，写入 yb_clinic_pre_settle。 */
    @Override
    public OutpatientChargePreSettleExtensionOut preSettle(OutpatientChargePreSettleExtensionIn command) {
        var enriched = contextLoader.enrichPreSettle(
            ChsOutpatientInsuranceIntegrationMapper.toPartialPreSettleCommand(command));
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionPreSettleOut(
            preSettleOrchestrator.invoke(enriched));
    }

    /** 【2203→2204→2206】批量门诊预结算，按分组写入 yb_clinic_pre_settle。 */
    @Override
    public OutpatientChargePreSettleBatchExtensionOut preSettleBatch(OutpatientChargePreSettleBatchExtensionIn command) {
        var enriched = batchPreSettleAssembler.enrichBatch(
            ChsOutpatientInsuranceIntegrationMapper.toPreSettleBatchCommand(command));
        return ChsOutpatientInsuranceIntegrationMapper.toExtensionPreSettleBatchOut(
            preSettleOrchestrator.invokeBatch(enriched));
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
                .setAcctUsedFlag(row.getAcctUsedFlag()));
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
            results.add(ChsOutpatientInsuranceIntegrationMapper.toExtensionSettlementRecord(
                ChsOutpatientInsuranceIntegrationMapper.toSettleRecordResult(row)));
        }
        return results;
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
        // Cmd：2601 后 yb 凭证已 REVOKED，支付桥表须同态，避免日结虚增
        this.paymentReconciliationDomainService.markYbSettlementsRevoked(
            record.getId(),
            record.getSetlId());
    }
}
