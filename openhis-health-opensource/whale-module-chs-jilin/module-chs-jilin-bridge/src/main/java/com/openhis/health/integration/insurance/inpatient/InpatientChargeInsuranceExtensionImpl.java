/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.inpatient;

import com.openhis.chs.bridge.inpatient.invoker.Ip2302Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2304Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2305Invoker;
import com.openhis.chs.bridge.inpatient.workflow.IpAdmissionOrchestrator;
import com.openhis.chs.bridge.inpatient.invoker.Ip2403Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2404Invoker;
import com.openhis.chs.bridge.inpatient.invoker.Ip2405Invoker;
import com.openhis.chs.bridge.inpatient.workflow.IpInpatientPreSettleOrchestrator;
import com.openhis.chs.bridge.inpatient.workflow.IpPlatformAdmissionOrchestrator;
import com.openhis.chs.domain.catalog.access.ChsInsuranceContractReader;
import com.openhis.chs.domain.inpatient.command.IpAdmissionCommand;
import com.openhis.chs.domain.inpatient.result.IpAdmissionResult;
import com.openhis.chs.domain.inpatient.command.IpConfirmSettleCommand;
import com.openhis.chs.domain.inpatient.result.IpConfirmSettleResult;
import com.openhis.chs.domain.inpatient.command.IpEncounterCommand;
import com.openhis.chs.domain.inpatient.queries.model.IpPlatformAdmissionQueryModel;
import com.openhis.chs.domain.inpatient.command.IpPreSettleCommand;
import com.openhis.chs.domain.inpatient.result.IpPreSettleResult;
import com.openhis.chs.domain.inpatient.command.IpRevokeFeeDetailCommand;
import com.openhis.chs.domain.inpatient.result.IpRevokeFeeDetailResult;
import com.openhis.chs.domain.inpatient.command.IpRevokeSettleCommand;
import com.openhis.chs.domain.inpatient.result.IpRevokeSettleResult;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.loader.IpSettleContextLoader;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleQueryService;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.health.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeEncounterExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargePreSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeFeeDetailExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionPlatformQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeConfirmExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargePreSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeRevokeFeeDetailExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeRevokeSettleExtensionOut;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 吉林医保住院收银结算 SPI 实现。
 *
 * <p><b>SPI 接口</b>：{@link InpatientChargeInsuranceExtension}</p>
 * <p><b>Health 调用方</b>：{@code InpatientSettleAppService}、{@code InpatientRegistrationAppService}</p>
 * <p><b>Bridge 委托</b>：{@link IpPlatformAdmissionOrchestrator}、{@link IpAdmissionOrchestrator}、
 * {@link IpInpatientPreSettleOrchestrator}、{@link Ip2304Invoker}、{@link Ip2305Invoker}、
 * {@link Ip2302Invoker}、{@link Ip2403Invoker}、{@link Ip2404Invoker}、{@link Ip2405Invoker}</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(IpInpatientPreSettleOrchestrator.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InpatientChargeInsuranceExtensionImpl implements InpatientChargeInsuranceExtension {

    private final ChsInsuranceContractReader contractReader;
    private final IpSettleContextLoader contextLoader;
    private final IpPlatformAdmissionOrchestrator platformAdmissionOrchestrator;
    private final IpAdmissionOrchestrator admissionOrchestrator;
    private final IpInpatientPreSettleOrchestrator preSettleOrchestrator;
    private final Ip2304Invoker ip2304Invoker;
    private final Ip2305Invoker ip2305Invoker;
    private final Ip2302Invoker ip2302Invoker;
    private final Ip2403Invoker ip2403Invoker;
    private final Ip2404Invoker ip2404Invoker;
    private final Ip2405Invoker ip2405Invoker;
    private final YbInpatientPreSettleQueryService inpatientPreSettleQueryService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;

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

    /** 本地：按就诊 ID 查询入院平台登记快照，无网关出站。 */
    @Override
    public List<InpatientChargeAdmissionPlatformQueryExtensionOut> findAdmissionPlatformSnapshots(
            InpatientChargeAdmissionPlatformQueryExtensionIn query) {
        if (query == null || query.getEncounterIds() == null || query.getEncounterIds().isEmpty()) {
            return List.of();
        }
        List<IpPlatformAdmissionQueryModel> rows =
            platformAdmissionOrchestrator.listSnapshots(query.getEncounterIds());
        List<InpatientChargeAdmissionPlatformQueryExtensionOut> result = new ArrayList<>(rows.size());
        for (IpPlatformAdmissionQueryModel row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            result.add(new InpatientChargeAdmissionPlatformQueryExtensionOut()
                .setEncounterId(row.getEncounterId())
                .setContractId(row.getContractId())
                .setMdtrtId(row.getMdtrtId())
                .setPlatformAdmitted(row.getPlatformAdmitted()));
        }
        return result;
    }

    /** 【2401】确保住院入院登记，写入 mdtrt_id。 */
    @Override
    public InpatientChargeAdmissionExtensionOut ensureAdmission(InpatientChargeAdmissionExtensionIn command) {
        IpAdmissionResult chsResult = admissionOrchestrator.ensureAdmission(
            contextLoader.enrichAdmission(toChsAdmissionCommand(command)));
        return toNeutralAdmissionResult(chsResult);
    }

    /** 【2301→2402→2303】住院预结算，写入 yb_inpatient_pre_settle。 */
    @Override
    public InpatientChargePreSettleExtensionOut preSettle(InpatientChargePreSettleExtensionIn command) {
        IpPreSettleResult chsResult = preSettleOrchestrator.invoke(
            contextLoader.enrichPreSettle(toChsPreSettleCommand(command)));
        return toNeutralPreSettleResult(chsResult);
    }

    /** 【2304】本地落账前住院正式结算确认，写入 yb_settlement_record。 */
    @Override
    public InpatientChargeConfirmExtensionOut confirmBeforeLocalSettle(InpatientChargeConfirmExtensionIn command) {
        IpConfirmSettleCommand domainCommand = new IpConfirmSettleCommand();
        domainCommand.setPaymentDraftId(command.getPaymentDraftId());
        domainCommand.setPreSettleId(command.getPreSettleId());
        domainCommand.setEncounterId(command.getEncounterId());
        domainCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domainCommand.setTenantId(TenantUtils.getCurrentTenantId());
        return toNeutralConfirmResult(ip2304Invoker.invoke(domainCommand));
    }

    /** 本地：按收银草稿 ID 查询活跃预结算列表，无网关出站。 */
    @Override
    public List<InpatientChargePreSettleExtensionOut> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        if (paymentDraftId == null) {
            return List.of();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbInpatientPreSettle> rows = inpatientPreSettleQueryService
            .listActiveByPaymentDraftId(paymentDraftId, tenantId);
        List<InpatientChargePreSettleExtensionOut> results = new ArrayList<>();
        for (YbInpatientPreSettle row : rows) {
            results.add(toNeutralPreSettleResult(toPreSettleResult(row)));
        }
        return results;
    }

    /** 本地：按 paymentId 查询已结算预结算记录，无网关出站。 */
    @Override
    public InpatientChargePreSettleExtensionOut findSettledPreSettleByPayment(Long paymentId) {
        if (paymentId == null) {
            return null;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        return inpatientPreSettleQueryService
            .findOptionalSettledByPaymentDraftId(paymentId, tenantId)
            .map(row -> toNeutralPreSettleResult(toPreSettleResult(row)))
            .orElse(null);
    }

    /** 【2305】撤销住院结算，标记回执已撤销。 */
    @Override
    public InpatientChargeRevokeSettleExtensionOut revokeSettle(InpatientChargeRevokeSettleExtensionIn command) {
        IpRevokeSettleCommand domainCommand = new IpRevokeSettleCommand();
        domainCommand.setSettlementRecordId(command.getSettlementRecordId());
        domainCommand.setPaymentId(command.getPaymentId());
        domainCommand.setPreSettleId(command.getPreSettleId());
        domainCommand.setSetlId(command.getSettlementId());
        domainCommand.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        domainCommand.setTenantId(TenantUtils.getCurrentTenantId());
        return toNeutralRevokeSettleResult(ip2305Invoker.invoke(domainCommand));
    }

    /** 【2302】撤销住院费用明细上传。 */
    @Override
    public InpatientChargeRevokeFeeDetailExtensionOut revokeFeeDetails(
            InpatientChargeRevokeFeeDetailExtensionIn command) {
        IpRevokeFeeDetailResult chsResult = ip2302Invoker.invoke(
            contextLoader.enrichRevokeFeeDetail(toChsRevokeFeeDetailCommand(command)));
        InpatientChargeRevokeFeeDetailExtensionOut result = new InpatientChargeRevokeFeeDetailExtensionOut();
        if (chsResult != null) {
            result.setRevokedCount(chsResult.getRevokedCount());
        }
        return result;
    }

    /** 【2404】撤销住院入院登记。 */
    @Override
    public void revokeAdmission(InpatientChargeEncounterExtensionIn command) {
        ip2404Invoker.invoke(
            contextLoader.enrichEncounterPlatform(toChsEncounterCommand(command)));
    }

    /** 【2405】撤销住院出院办理。 */
    @Override
    public void revokeDischarge(InpatientChargeEncounterExtensionIn command) {
        ip2405Invoker.invoke(
            contextLoader.enrichEncounterPlatform(toChsEncounterCommand(command)));
    }

    /** 【2403】变更住院登记信息。 */
    @Override
    public void changeInpatientInfo(InpatientChargeAdmissionExtensionIn command) {
        ip2403Invoker.invoke(
            contextLoader.enrichAdmission(toChsAdmissionCommand(command)));
    }

    /**
     * 将住院入院 ExtensionIn 映射为 CHS 入院命令。
     *
     * @param command 住院入院扩展入参
     * @return CHS 入院命令
     */
    private static IpAdmissionCommand toChsAdmissionCommand(InpatientChargeAdmissionExtensionIn command) {
        IpAdmissionCommand chsCommand = new IpAdmissionCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setMdtrtCertType(command.getPayerCertType());
        chsCommand.setMdtrtCertNo(command.getPayerCertNo());
        chsCommand.setPsnNo(command.getInsuredPersonNo());
        chsCommand.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        chsCommand.setInsutype(command.getInsutype());
        chsCommand.setMedType(command.getMedType());
        return chsCommand;
    }

    /**
     * 将住院预结算 ExtensionIn 映射为 CHS 预结算命令。
     *
     * @param command 住院预结算扩展入参
     * @return CHS 预结算命令
     */
    private static IpPreSettleCommand toChsPreSettleCommand(InpatientChargePreSettleExtensionIn command) {
        IpPreSettleCommand chsCommand = new IpPreSettleCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setChargeItemIds(command.getChargeItemIds());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setPaymentDraftId(command.getPaymentDraftId());
        chsCommand.setMdtrtCertType(command.getPayerCertType());
        chsCommand.setMdtrtCertNo(command.getPayerCertNo());
        chsCommand.setPsnNo(command.getInsuredPersonNo());
        chsCommand.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        chsCommand.setInsutype(command.getInsutype());
        chsCommand.setMedType(command.getMedType());
        chsCommand.setSettleMode(command.getSettleMode());
        chsCommand.setAcctUsedFlag(command.getAcctUsedFlag());
        chsCommand.setPsnSetlway(command.getPsnSetlway());
        return chsCommand;
    }

    /**
     * 将撤销费用明细 ExtensionIn 映射为 CHS 撤销命令。
     *
     * @param command 撤销费用明细扩展入参
     * @return CHS 撤销费用明细命令
     */
    private static IpRevokeFeeDetailCommand toChsRevokeFeeDetailCommand(
            InpatientChargeRevokeFeeDetailExtensionIn command) {
        IpRevokeFeeDetailCommand chsCommand = new IpRevokeFeeDetailCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setChargeItemIds(command.getChargeItemIds());
        return chsCommand;
    }

    /**
     * 将住院就诊 ExtensionIn 映射为 CHS 就诊平台命令。
     *
     * @param command 住院就诊扩展入参
     * @return CHS 就诊平台命令
     */
    private static IpEncounterCommand toChsEncounterCommand(InpatientChargeEncounterExtensionIn command) {
        IpEncounterCommand chsCommand = new IpEncounterCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        return chsCommand;
    }

    /**
     * 将 CHS 入院结果映射为中性 ExtensionOut。
     *
     * @param chsResult CHS 入院结果
     * @return 住院入院扩展出参；入参为 {@code null} 时返回 {@code null}
     */
    private static InpatientChargeAdmissionExtensionOut toNeutralAdmissionResult(IpAdmissionResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargeAdmissionExtensionOut result = new InpatientChargeAdmissionExtensionOut();
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        return result;
    }

    /**
     * 将 CHS 预结算结果映射为中性 ExtensionOut。
     *
     * @param chsResult CHS 预结算结果
     * @return 住院预结算扩展出参；入参为 {@code null} 时返回 {@code null}
     */
    private static InpatientChargePreSettleExtensionOut toNeutralPreSettleResult(IpPreSettleResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargePreSettleExtensionOut result = new InpatientChargePreSettleExtensionOut();
        result.setPreSettleId(chsResult.getPreSettleId());
        result.setContractId(chsResult.getContractId());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        result.setSettlementId(chsResult.getSetlId());
        result.setSettleMode(chsResult.getSettleMode());
        result.setMedfeeSumamt(chsResult.getMedfeeSumamt());
        result.setFundPaySumamt(chsResult.getFundPaySumamt());
        result.setAcctPay(chsResult.getAcctPay());
        result.setPsnCashPay(chsResult.getPsnCashPay());
        return result;
    }

    /**
     * 将 CHS 正式结算确认结果映射为中性 ExtensionOut。
     *
     * @param chsResult CHS 结算确认结果
     * @return 住院结算确认扩展出参；入参为 {@code null} 时返回 {@code null}
     */
    private static InpatientChargeConfirmExtensionOut toNeutralConfirmResult(IpConfirmSettleResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargeConfirmExtensionOut result = new InpatientChargeConfirmExtensionOut();
        result.setSettlementId(chsResult.getSetlId());
        result.setChargeBatchNo(chsResult.getChrgBchno());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        result.setSettlementRecordId(chsResult.getSettlementRecordId());
        result.setMedfeeSumamt(chsResult.getMedfeeSumamt());
        result.setFundPaySumamt(chsResult.getFundPaySumamt());
        result.setAcctPay(chsResult.getAcctPay());
        result.setPsnCashPay(chsResult.getPsnCashPay());
        return result;
    }

    /**
     * 将 CHS 撤销结算结果映射为中性 ExtensionOut。
     *
     * @param chsResult CHS 撤销结算结果
     * @return 住院撤销结算扩展出参；入参为 {@code null} 时返回 {@code null}
     */
    private static InpatientChargeRevokeSettleExtensionOut toNeutralRevokeSettleResult(
            IpRevokeSettleResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargeRevokeSettleExtensionOut result = new InpatientChargeRevokeSettleExtensionOut();
        result.setSettlementRecordId(chsResult.getSettlementRecordId());
        result.setSettlementId(chsResult.getSetlId());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        return result;
    }

    /**
     * 将住院预结算持久化行映射为 CHS 预结算结果。
     *
     * @param row 住院预结算实体行
     * @return CHS 预结算结果
     */
    private static IpPreSettleResult toPreSettleResult(YbInpatientPreSettle row) {
        return new IpPreSettleResult()
            .setPreSettleId(row.getId())
            .setContractId(row.getContractId())
            .setMdtrtId(row.getMdtrtId())
            .setSetlId(row.getSetlId())
            .setSettleMode(row.getSettleMode())
            .setMedfeeSumamt(row.getMedfeeSumamt())
            .setFundPaySumamt(row.getFundPaySumamt())
            .setAcctPay(row.getAcctPay())
            .setPsnCashPay(row.getPsnCashPay());
    }
}
