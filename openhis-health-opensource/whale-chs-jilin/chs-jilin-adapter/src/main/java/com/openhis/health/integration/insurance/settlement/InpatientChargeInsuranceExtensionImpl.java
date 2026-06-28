/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.settlement;

import com.openhis.health.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargeAdmissionCommand;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargeConfirmCommand;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargeEncounterCommand;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargePreSettleCommand;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargeRevokeFeeDetailCommand;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargeRevokeSettleCommand;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargeAdmissionPlatformSnapshot;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargeAdmissionResult;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargeConfirmResult;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargePreSettleResult;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargeRevokeFeeDetailResult;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargeRevokeSettleResult;
import com.openhis.whale.chs.jilin.contracts.client.ChsInpatientSettlementClient;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceAdmissionCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceAdmissionResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceEncounterCommand;
import com.openhis.whale.chs.jilin.contracts.command.IpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsurancePlatformAdmissionSnapshot;
import com.openhis.whale.chs.jilin.contracts.result.IpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceRevokeFeeDetailResult;
import com.openhis.whale.chs.jilin.contracts.command.IpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.contracts.result.IpInsuranceRevokeSettleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 住院收银 ↔ 吉林医保结算的 Extension 桥接层。
 *
 * <p>职责边界同门诊：本类只做 DTO 映射；平台链路 2401→2301→2303→2304 在 application 层。</p>
 * <p>对应 AS：{@code InpatientChargeSettlementAppService}。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsInpatientSettlementClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InpatientChargeInsuranceExtensionImpl implements InpatientChargeInsuranceExtension {

    private final ChsInpatientSettlementClient chsInpatientSettlementClient;

    /** @return CHS 模块启用时为 true */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 判断费用性质是否为医保契约。
     *
     * @param contractId {@code fin_contract.id}
     */
    @Override
    public boolean supportsThirdPartyContract(Long contractId) {
        return chsInpatientSettlementClient.isInsuranceContract(contractId);
    }

    @Override
    public List<InpatientChargeAdmissionPlatformSnapshot> findAdmissionPlatformSnapshots(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        List<IpInsurancePlatformAdmissionSnapshot> rows =
            chsInpatientSettlementClient.listPlatformAdmissionSnapshots(encounterIds);
        List<InpatientChargeAdmissionPlatformSnapshot> result = new ArrayList<>(rows.size());
        for (IpInsurancePlatformAdmissionSnapshot row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            result.add(new InpatientChargeAdmissionPlatformSnapshot()
                .setEncounterId(row.getEncounterId())
                .setContractId(row.getContractId())
                .setMdtrtId(row.getMdtrtId())
                .setPlatformAdmitted(row.getPlatformAdmitted()));
        }
        return result;
    }

    /**
     * 【2401】医保入院登记（IF-IP-REG-03），获取 mdtrt_id 供 HIS 登记回填。
     *
     * @param command 入院读卡与契约信息
     * @return 平台就诊 ID（mdtrt_id）
     */
    @Override
    public InpatientChargeAdmissionResult ensureAdmission(InpatientChargeAdmissionCommand command) {
        // Pre：health → Port 命令
        IpInsuranceAdmissionCommand chsCommand = toChsAdmissionCommand(command);
        // Cmd：2401 + 写 yb_encounter_platform_map
        IpInsuranceAdmissionResult chsResult = chsInpatientSettlementClient.ensureAdmission(chsCommand);
        // Asm
        return toNeutralAdmissionResult(chsResult);
    }

    /**
     * 【2301→(2402)→2303】住院预结算（IF-IP-STL-05）。
     *
     * @param command 含 settleMode（出院全结/中途结算）、收费明细
     * @return 预结算分摊金额与 preSettleId
     */
    @Override
    public InpatientChargePreSettleResult preSettle(InpatientChargePreSettleCommand command) {
        // Pre
        IpInsurancePreSettleCommand chsCommand = toChsPreSettleCommand(command);
        // Cmd：Port 编排 2301 费用上传、必要时 2402 出院、2303 预结算
        IpInsurancePreSettleResult chsResult = chsInpatientSettlementClient.preSettle(chsCommand);
        // Asm
        return toNeutralPreSettleResult(chsResult);
    }

    /**
     * 【2304】本地落账前正式结算，获取 setl_id。
     *
     * @param command 含 preSettleId 或 paymentDraftId
     * @return 四类分摊金额与 settlementRecordId
     */
    @Override
    public InpatientChargeConfirmResult confirmBeforeLocalSettle(InpatientChargeConfirmCommand command) {
        // Pre
        IpInsuranceConfirmCommand chsCommand = toChsConfirmCommand(command);
        // Cmd：2304
        IpInsuranceConfirmResult chsResult =
            chsInpatientSettlementClient.confirmBeforeLocalSettle(chsCommand);
        // Asm
        return toNeutralConfirmResult(chsResult);
    }

    /**
     * 查询草稿下 ACTIVE 预结算（重算/撤销前检查）。
     *
     * @param paymentDraftId 收银草稿 id
     */
    @Override
    public List<InpatientChargePreSettleResult> listActivePreSettlesByPaymentDraft(Long paymentDraftId) {
        // Cmd
        List<IpInsurancePreSettleResult> active =
            chsInpatientSettlementClient.listActivePreSettlesByPaymentDraft(paymentDraftId);
        // Asm：跳过 null 行
        List<InpatientChargePreSettleResult> results = new ArrayList<>();
        for (IpInsurancePreSettleResult row : active) {
            if (row == null) {
                continue;
            }
            results.add(toNeutralPreSettleResult(row));
        }
        return results;
    }

    /**
     * 按 payment 查已结算的预结算快照（退费/对账定位用）。
     *
     * @param paymentId {@code fin_payment.id}
     */
    @Override
    public InpatientChargePreSettleResult findSettledPreSettleByPayment(Long paymentId) {
        // Cmd：查 status=SETTLED 的最新预结算
        IpInsurancePreSettleResult chsResult = chsInpatientSettlementClient.findSettledPreSettleByPayment(paymentId);
        return toNeutralPreSettleResult(chsResult);
    }

    /**
     * 【2305】撤销住院正式结算。
     *
     * @param command 含 settlementRecordId / paymentId / setlId
     */
    @Override
    public InpatientChargeRevokeSettleResult revokeSettle(InpatientChargeRevokeSettleCommand command) {
        // Pre
        IpInsuranceRevokeSettleCommand chsCommand = toChsRevokeSettleCommand(command);
        // Cmd：2305 + reversal 审计
        IpInsuranceRevokeSettleResult chsResult = chsInpatientSettlementClient.revokeSettle(chsCommand);
        return toNeutralRevokeSettleResult(chsResult);
    }

    /**
     * 【2302】撤销已上传未结算的住院费用明细。
     *
     * @param command 含 chargeItemIds
     * @return 撤销条数
     */
    @Override
    public InpatientChargeRevokeFeeDetailResult revokeFeeDetails(InpatientChargeRevokeFeeDetailCommand command) {
        // Pre
        IpInsuranceRevokeFeeDetailCommand chsCommand = toChsRevokeFeeDetailCommand(command);
        // Cmd
        IpInsuranceRevokeFeeDetailResult chsResult =
            chsInpatientSettlementClient.revokeFeeDetails(chsCommand);
        // Asm
        InpatientChargeRevokeFeeDetailResult result = new InpatientChargeRevokeFeeDetailResult();
        if (chsResult != null) {
            result.setRevokedCount(chsResult.getRevokedCount());
        }
        return result;
    }

    /**
     * 【2404】撤销医保入院登记，并软删本地 encounter→mdtrt_id 映射。
     *
     * @param command 含 encounterId、contractId
     */
    @Override
    public void revokeAdmission(InpatientChargeEncounterCommand command) {
        // Cmd：2404 + 软删 yb_encounter_platform_map
        chsInpatientSettlementClient.revokeAdmission(toChsEncounterCommand(command));
    }

    /**
     * 【2405】撤销医保出院登记（中途结算改全结等场景）。
     *
     * @param command 含 mdtrt_id
     */
    @Override
    public void revokeDischarge(InpatientChargeEncounterCommand command) {
        // Cmd：2405
        chsInpatientSettlementClient.revokeDischarge(toChsEncounterCommand(command));
    }

    /**
     * 【2403】变更住院医保登记信息（科室/诊断等变更后同步平台）。
     *
     * @param command 变更后的入院信息
     */
    @Override
    public void changeInpatientInfo(InpatientChargeAdmissionCommand command) {
        // Cmd：2403
        chsInpatientSettlementClient.changeInpatientInfo(toChsAdmissionCommand(command));
    }

    private static IpInsuranceAdmissionCommand toChsAdmissionCommand(InpatientChargeAdmissionCommand command) {
        IpInsuranceAdmissionCommand chsCommand = new IpInsuranceAdmissionCommand();
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

    private static IpInsurancePreSettleCommand toChsPreSettleCommand(InpatientChargePreSettleCommand command) {
        IpInsurancePreSettleCommand chsCommand = new IpInsurancePreSettleCommand();
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

    private static IpInsuranceConfirmCommand toChsConfirmCommand(InpatientChargeConfirmCommand command) {
        IpInsuranceConfirmCommand chsCommand = new IpInsuranceConfirmCommand();
        chsCommand.setPaymentDraftId(command.getPaymentDraftId());
        chsCommand.setPreSettleId(command.getPreSettleId());
        chsCommand.setEncounterId(command.getEncounterId());
        return chsCommand;
    }

    private static IpInsuranceRevokeSettleCommand toChsRevokeSettleCommand(InpatientChargeRevokeSettleCommand command) {
        IpInsuranceRevokeSettleCommand chsCommand = new IpInsuranceRevokeSettleCommand();
        chsCommand.setSettlementRecordId(command.getSettlementRecordId());
        chsCommand.setPaymentId(command.getPaymentId());
        chsCommand.setPreSettleId(command.getPreSettleId());
        chsCommand.setSetlId(command.getSettlementId());
        return chsCommand;
    }

    private static IpInsuranceRevokeFeeDetailCommand toChsRevokeFeeDetailCommand(
        InpatientChargeRevokeFeeDetailCommand command) {
        IpInsuranceRevokeFeeDetailCommand chsCommand = new IpInsuranceRevokeFeeDetailCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setChargeItemIds(command.getChargeItemIds());
        return chsCommand;
    }

    private static IpInsuranceEncounterCommand toChsEncounterCommand(InpatientChargeEncounterCommand command) {
        IpInsuranceEncounterCommand chsCommand = new IpInsuranceEncounterCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setContractId(command.getContractId());
        return chsCommand;
    }

    private static InpatientChargeAdmissionResult toNeutralAdmissionResult(IpInsuranceAdmissionResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargeAdmissionResult result = new InpatientChargeAdmissionResult();
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        return result;
    }

    private static InpatientChargePreSettleResult toNeutralPreSettleResult(IpInsurancePreSettleResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargePreSettleResult result = new InpatientChargePreSettleResult();
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

    private static InpatientChargeConfirmResult toNeutralConfirmResult(IpInsuranceConfirmResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargeConfirmResult result = new InpatientChargeConfirmResult();
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

    private static InpatientChargeRevokeSettleResult toNeutralRevokeSettleResult(
        IpInsuranceRevokeSettleResult chsResult) {
        if (chsResult == null) {
            return null;
        }
        InpatientChargeRevokeSettleResult result = new InpatientChargeRevokeSettleResult();
        result.setSettlementRecordId(chsResult.getSettlementRecordId());
        result.setSettlementId(chsResult.getSetlId());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        return result;
    }
}
