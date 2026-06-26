/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.mapper;

import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeConfirmCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeCorrectSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleBatchCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeRevokeFeeDetailCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeRevokeSettleCommand;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeConfirmResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleBatchResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleGroupResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeRevokeSettleResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeSelfPayRollupResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeSettlementRecordResult;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceCorrectSettleCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleBatchCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.contracts.command.OpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.contracts.dto.OpSelfPayRollupDto;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleBatchResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleGroupResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceRevokeSettleResult;
import com.openhis.whale.chs.jilin.contracts.result.OpInsuranceSettleRecordResult;

import java.util.ArrayList;
import java.util.List;

/** health-integration-contracts ↔ chs-jilin-contracts 门诊结算 DTO 映射。 */
public final class ChsOutpatientInsuranceIntegrationMapper {

    private ChsOutpatientInsuranceIntegrationMapper() {
    }

    public static OpInsurancePreSettleCommand toChsPreSettleCommand(OutpatientChargePreSettleCommand command) {
        if (command == null) {
            return null;
        }
        OpInsurancePreSettleCommand chsCommand = new OpInsurancePreSettleCommand();
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
        chsCommand.setAcctUsedFlag(command.getAcctUsedFlag());
        return chsCommand;
    }

    public static OpInsurancePreSettleBatchCommand toChsPreSettleBatchCommand(
            OutpatientChargePreSettleBatchCommand command) {
        if (command == null) {
            return null;
        }
        OpInsurancePreSettleBatchCommand chsCommand = new OpInsurancePreSettleBatchCommand();
        chsCommand.setEncounterId(command.getEncounterId());
        chsCommand.setChargeItemIds(command.getChargeItemIds());
        chsCommand.setContractId(command.getContractId());
        chsCommand.setPaymentDraftId(command.getPaymentDraftId());
        chsCommand.setMdtrtCertType(command.getPayerCertType());
        chsCommand.setMdtrtCertNo(command.getPayerCertNo());
        chsCommand.setPsnNo(command.getInsuredPersonNo());
        chsCommand.setInsuplcAdmdvs(command.getInsuplcAdmdvs());
        chsCommand.setInsutype(command.getInsutype());
        return chsCommand;
    }

    public static OutpatientChargePreSettleResult toNeutralPreSettleResult(OpInsurancePreSettleResult chsResult) {
        OutpatientChargePreSettleResult result = new OutpatientChargePreSettleResult();
        if (chsResult == null) {
            return result;
        }
        result.setPreSettleId(chsResult.getPreSettleId());
        result.setChargeBatchNo(chsResult.getChrgBchno());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        result.setMedfeeSumamt(chsResult.getMedfeeSumamt());
        result.setFundPaySumamt(chsResult.getFundPaySumamt());
        result.setAcctPay(chsResult.getAcctPay());
        result.setPsnCashPay(chsResult.getPsnCashPay());
        return result;
    }

    public static OutpatientChargePreSettleBatchResult toNeutralPreSettleBatchResult(
            OpInsurancePreSettleBatchResult chsResult) {
        OutpatientChargePreSettleBatchResult result = new OutpatientChargePreSettleBatchResult();
        if (chsResult == null) {
            return result;
        }
        result.setTotalPsnCashPay(chsResult.getTotalPsnCashPay());
        if (chsResult.getSelfPayRollup() != null) {
            OpSelfPayRollupDto rollup = chsResult.getSelfPayRollup();
            OutpatientChargeSelfPayRollupResult neutralRollup = new OutpatientChargeSelfPayRollupResult();
            neutralRollup.setAccountId(rollup.getAccountId());
            neutralRollup.setChargeItemIds(rollup.getChargeItemIds());
            neutralRollup.setTotalAmount(rollup.getTotalAmount());
            result.setSelfPayRollup(neutralRollup);
        }
        if (chsResult.getGroups() != null) {
            List<OutpatientChargePreSettleGroupResult> groups = new ArrayList<>();
            for (OpInsurancePreSettleGroupResult group : chsResult.getGroups()) {
                groups.add(toNeutralGroupResult(group));
            }
            result.setGroups(groups);
        }
        return result;
    }

    public static OutpatientChargePreSettleGroupResult toNeutralGroupResult(OpInsurancePreSettleGroupResult group) {
        OutpatientChargePreSettleGroupResult result = new OutpatientChargePreSettleGroupResult();
        if (group == null) {
            return result;
        }
        result.setPreSettleId(group.getPreSettleId());
        result.setChargeBatchNo(group.getChrgBchno());
        result.setEncounterPlatformId(group.getMdtrtId());
        result.setMedfeeSumamt(group.getMedfeeSumamt());
        result.setFundPaySumamt(group.getFundPaySumamt());
        result.setAcctPay(group.getAcctPay());
        result.setPsnCashPay(group.getPsnCashPay());
        result.setGroupIndex(group.getGroupIndex());
        result.setMedType(group.getMedType());
        result.setDiseCodg(group.getDiseCodg());
        result.setAccountId(group.getAccountId());
        result.setChargeItemIds(group.getChargeItemIds());
        return result;
    }

    public static OpInsuranceConfirmCommand toChsConfirmCommand(OutpatientChargeConfirmCommand command) {
        if (command == null) {
            return null;
        }
        OpInsuranceConfirmCommand chsCommand = new OpInsuranceConfirmCommand();
        chsCommand.setPaymentDraftId(command.getPaymentDraftId());
        chsCommand.setChrgBchno(command.getChargeBatchNo());
        chsCommand.setEncounterId(command.getEncounterId());
        return chsCommand;
    }

    public static OutpatientChargeConfirmResult toNeutralConfirmResult(OpInsuranceConfirmResult chsResult) {
        OutpatientChargeConfirmResult result = new OutpatientChargeConfirmResult();
        if (chsResult == null) {
            return result;
        }
        result.setSettlementId(chsResult.getSetlId());
        result.setChargeBatchNo(chsResult.getChrgBchno());
        result.setSettlementRecordId(chsResult.getSettlementRecordId());
        result.setMdtrtId(chsResult.getMdtrtId());
        result.setAccountId(chsResult.getAccountId());
        result.setMedfeeSumamt(chsResult.getMedfeeSumamt());
        result.setFundPaySumamt(chsResult.getFundPaySumamt());
        result.setAcctPay(chsResult.getAcctPay());
        result.setPsnCashPay(chsResult.getPsnCashPay());
        return result;
    }

    public static OutpatientChargeSettlementRecordResult toNeutralSettlementRecord(
            OpInsuranceSettleRecordResult chsResult) {
        OutpatientChargeSettlementRecordResult result = new OutpatientChargeSettlementRecordResult();
        if (chsResult == null) {
            return result;
        }
        result.setSettlementRecordId(chsResult.getSettlementRecordId());
        result.setPaymentId(chsResult.getPaymentId());
        result.setPreSettleId(chsResult.getPreSettleId());
        result.setContractId(chsResult.getContractId());
        result.setChargeBatchNo(chsResult.getChrgBchno());
        result.setSettlementId(chsResult.getSetlId());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        result.setMsgid2207(chsResult.getMsgid2207());
        return result;
    }

    public static OpInsuranceRevokeSettleCommand toChsRevokeSettleCommand(
            OutpatientChargeRevokeSettleCommand command) {
        if (command == null) {
            return null;
        }
        OpInsuranceRevokeSettleCommand chsCommand = new OpInsuranceRevokeSettleCommand();
        chsCommand.setSettlementRecordId(command.getSettlementRecordId());
        chsCommand.setPaymentId(command.getPaymentId());
        chsCommand.setPreSettleId(command.getPreSettleId());
        chsCommand.setSetlId(command.getSettlementId());
        return chsCommand;
    }

    public static OutpatientChargeRevokeSettleResult toNeutralRevokeSettleResult(
            OpInsuranceRevokeSettleResult chsResult) {
        OutpatientChargeRevokeSettleResult result = new OutpatientChargeRevokeSettleResult();
        if (chsResult == null) {
            return result;
        }
        result.setSettlementRecordId(chsResult.getSettlementRecordId());
        result.setSettlementId(chsResult.getSetlId());
        result.setEncounterPlatformId(chsResult.getMdtrtId());
        return result;
    }

    public static OpInsuranceRevokeFeeDetailCommand toChsRevokeFeeDetailCommand(
        OutpatientChargeRevokeFeeDetailCommand command) {
        if (command == null) {
            return null;
        }
        OpInsuranceRevokeFeeDetailCommand chsCommand = new OpInsuranceRevokeFeeDetailCommand();
        chsCommand.setChrgBchno(command.getChargeBatchNo());
        chsCommand.setPaymentDraftId(command.getPaymentDraftId());
        return chsCommand;
    }

    public static OpInsuranceCorrectSettleCommand toChsCorrectSettleCommand(
            OutpatientChargeCorrectSettleCommand command) {
        if (command == null) {
            return null;
        }
        OpInsuranceCorrectSettleCommand chsCommand = new OpInsuranceCorrectSettleCommand();
        chsCommand.setSettlementRecordId(command.getSettlementRecordId());
        chsCommand.setOriginalInfno(command.getOriginalInfno());
        return chsCommand;
    }
}
