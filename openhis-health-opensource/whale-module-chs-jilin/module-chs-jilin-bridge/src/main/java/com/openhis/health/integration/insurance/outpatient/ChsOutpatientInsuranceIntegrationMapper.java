/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.insurance.outpatient;

import com.openhis.chs.domain.outpatient.command.OpConfirmSettleCommand;
import com.openhis.chs.domain.outpatient.result.OpConfirmSettleResult;
import com.openhis.chs.domain.outpatient.command.OpCorrectSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpPlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.result.OpPlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.command.OpPreSettleBatchCommand;
import com.openhis.chs.domain.outpatient.result.OpPreSettleBatchResult;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.result.OpPreSettleGroupResult;
import com.openhis.chs.domain.outpatient.result.OpPreSettleResult;
import com.openhis.chs.domain.outpatient.command.OpRevokeFeeDetailCommand;
import com.openhis.chs.domain.outpatient.command.OpRevokePlatformRegistrationCommand;
import com.openhis.chs.domain.outpatient.result.OpRevokePlatformRegistrationResult;
import com.openhis.chs.domain.outpatient.command.OpRevokeSettleCommand;
import com.openhis.chs.domain.outpatient.result.OpRevokeSettleResult;
import com.openhis.chs.domain.outpatient.command.OpSelfPayRollup;
import com.openhis.chs.domain.outpatient.result.OpSettleRecordResult;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeCorrectSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleBatchExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeFeeDetailExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeConfirmExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleBatchExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleGroupExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeRevokeSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSelfPayRollupExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformExtensionOut;

import java.util.ArrayList;
import java.util.List;

/**
 * Health 门诊结算/挂号 ExtensionIn/Out ↔ chs domain Command/Result 双向映射。
 *
 * <p>入向：{@code OutpatientCharge*ExtensionIn} / {@code OutpatientRegistration*ExtensionIn}
 * → {@code Op*Command}；出向：{@code Op*Result} → {@code OutpatientCharge*ExtensionOut} /
 * {@code OutpatientRegistration*ExtensionOut}。</p>
 */
public final class ChsOutpatientInsuranceIntegrationMapper {

    private ChsOutpatientInsuranceIntegrationMapper() {
    }

    public static OpPreSettleCommand toPartialPreSettleCommand(OutpatientChargePreSettleExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpPreSettleCommand()
            .setEncounterId(command.getEncounterId())
            .setChargeItemIds(command.getChargeItemIds())
            .setContractId(command.getContractId())
            .setPaymentDraftId(command.getPaymentDraftId())
            .setMdtrtCertType(command.getPayerCertType())
            .setMdtrtCertNo(command.getPayerCertNo())
            .setPsnNo(command.getInsuredPersonNo())
            .setInsuplcAdmdvs(command.getInsuplcAdmdvs())
            .setInsutype(command.getInsutype())
            .setMedType(command.getMedType())
            .setAcctUsedFlag(command.getAcctUsedFlag());
    }

    public static OpPreSettleBatchCommand toPreSettleBatchCommand(OutpatientChargePreSettleBatchExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpPreSettleBatchCommand()
            .setEncounterId(command.getEncounterId())
            .setChargeItemIds(command.getChargeItemIds())
            .setContractId(command.getContractId())
            .setPaymentDraftId(command.getPaymentDraftId())
            .setMdtrtCertType(command.getPayerCertType())
            .setMdtrtCertNo(command.getPayerCertNo())
            .setPsnNo(command.getInsuredPersonNo())
            .setInsuplcAdmdvs(command.getInsuplcAdmdvs())
            .setInsutype(command.getInsutype());
    }

    public static OutpatientChargePreSettleExtensionOut toExtensionPreSettleOut(OpPreSettleResult result) {
        OutpatientChargePreSettleExtensionOut out = new OutpatientChargePreSettleExtensionOut();
        if (result == null) {
            return out;
        }
        out.setPreSettleId(result.getPreSettleId());
        out.setChargeBatchNo(result.getChrgBchno());
        out.setEncounterPlatformId(result.getMdtrtId());
        out.setMedfeeSumamt(result.getMedfeeSumamt());
        out.setFundPaySumamt(result.getFundPaySumamt());
        out.setAcctPay(result.getAcctPay());
        out.setPsnCashPay(result.getPsnCashPay());
        return out;
    }

    public static OutpatientChargePreSettleBatchExtensionOut toExtensionPreSettleBatchOut(OpPreSettleBatchResult result) {
        OutpatientChargePreSettleBatchExtensionOut out = new OutpatientChargePreSettleBatchExtensionOut();
        if (result == null) {
            return out;
        }
        out.setTotalPsnCashPay(result.getTotalPsnCashPay());
        if (result.getSelfPayRollup() != null) {
            OpSelfPayRollup rollup = result.getSelfPayRollup();
            OutpatientChargeSelfPayRollupExtensionOut neutralRollup = new OutpatientChargeSelfPayRollupExtensionOut();
            neutralRollup.setAccountId(rollup.getAccountId());
            neutralRollup.setChargeItemIds(rollup.getChargeItemIds());
            neutralRollup.setTotalAmount(rollup.getTotalAmount());
            out.setSelfPayRollup(neutralRollup);
        }
        if (result.getGroups() != null) {
            List<OutpatientChargePreSettleGroupExtensionOut> groups = new ArrayList<>();
            for (OpPreSettleGroupResult group : result.getGroups()) {
                groups.add(toExtensionGroupOut(group));
            }
            out.setGroups(groups);
        }
        return out;
    }

    public static OutpatientChargePreSettleGroupExtensionOut toExtensionGroupOut(OpPreSettleGroupResult group) {
        OutpatientChargePreSettleGroupExtensionOut out = new OutpatientChargePreSettleGroupExtensionOut();
        if (group == null) {
            return out;
        }
        out.setPreSettleId(group.getPreSettleId());
        out.setChargeBatchNo(group.getChrgBchno());
        out.setEncounterPlatformId(group.getMdtrtId());
        out.setMedfeeSumamt(group.getMedfeeSumamt());
        out.setFundPaySumamt(group.getFundPaySumamt());
        out.setAcctPay(group.getAcctPay());
        out.setPsnCashPay(group.getPsnCashPay());
        out.setGroupIndex(group.getGroupIndex());
        out.setMedType(group.getMedType());
        out.setDiseCodg(group.getDiseCodg());
        out.setAccountId(group.getAccountId());
        out.setChargeItemIds(group.getChargeItemIds());
        return out;
    }

    public static OpConfirmSettleCommand toConfirmCommand(OutpatientChargeConfirmExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpConfirmSettleCommand()
            .setPaymentDraftId(command.getPaymentDraftId())
            .setChrgBchno(command.getChargeBatchNo());
    }

    public static OutpatientChargeConfirmExtensionOut toExtensionConfirmOut(OpConfirmSettleResult result) {
        OutpatientChargeConfirmExtensionOut out = new OutpatientChargeConfirmExtensionOut();
        if (result == null) {
            return out;
        }
        out.setSettlementId(result.getSetlId());
        out.setChargeBatchNo(result.getChrgBchno());
        out.setSettlementRecordId(result.getSettlementRecordId());
        out.setMdtrtId(result.getMdtrtId());
        out.setAccountId(result.getAccountId());
        out.setMedfeeSumamt(result.getMedfeeSumamt());
        out.setFundPaySumamt(result.getFundPaySumamt());
        out.setAcctPay(result.getAcctPay());
        out.setPsnCashPay(result.getPsnCashPay());
        return out;
    }

    public static OutpatientChargeSettlementRecordExtensionOut toExtensionSettlementRecord(OpSettleRecordResult result) {
        OutpatientChargeSettlementRecordExtensionOut out = new OutpatientChargeSettlementRecordExtensionOut();
        if (result == null) {
            return out;
        }
        out.setSettlementRecordId(result.getSettlementRecordId());
        out.setPaymentId(result.getPaymentId());
        out.setPreSettleId(result.getPreSettleId());
        out.setContractId(result.getContractId());
        out.setChargeBatchNo(result.getChrgBchno());
        out.setSettlementId(result.getSetlId());
        out.setEncounterPlatformId(result.getMdtrtId());
        out.setMsgid2207(result.getMsgid2207());
        out.setStatus(result.getStatus());
        out.setMedfeeSumamt(result.getMedfeeSumamt());
        out.setFulamtOwnpayAmt(result.getFulamtOwnpayAmt());
        out.setOverlmtSelfpay(result.getOverlmtSelfpay());
        out.setPreselfpayAmt(result.getPreselfpayAmt());
        out.setInscpScpAmt(result.getInscpScpAmt());
        out.setActPayDedc(result.getActPayDedc());
        out.setHifpPay(result.getHifpPay());
        out.setPoolPropSelfpay(result.getPoolPropSelfpay());
        out.setCvlservPay(result.getCvlservPay());
        out.setHifesPay(result.getHifesPay());
        out.setHifmiPay(result.getHifmiPay());
        out.setHifobPay(result.getHifobPay());
        out.setMafPay(result.getMafPay());
        out.setHifdmPay(result.getHifdmPay());
        out.setOthPay(result.getOthPay());
        out.setFundPaySumamt(result.getFundPaySumamt());
        out.setPsnPartAmt(result.getPsnPartAmt());
        out.setAcctPay(result.getAcctPay());
        out.setPsnCashPay(result.getPsnCashPay());
        out.setHospPartAmt(result.getHospPartAmt());
        out.setBalc(result.getBalc());
        out.setAcctMulaidPay(result.getAcctMulaidPay());
        out.setClrOptins(result.getClrOptins());
        out.setClrWay(result.getClrWay());
        out.setClrType(result.getClrType());
        return out;
    }

    /**
     * {@code yb_settlement_record} → 门诊结算回执摘要（含 2206/2207 setlinfo 金额）。
     *
     * @param row 结算回执实体
     * @return 领域结果；row 为 null 时返回空对象
     */
    public static OpSettleRecordResult toSettleRecordResult(YbSettlementRecord row) {
        OpSettleRecordResult result = new OpSettleRecordResult();
        if (row == null) {
            return result;
        }
        return result
            .setSettlementRecordId(row.getId())
            .setPaymentId(row.getPaymentId())
            .setPreSettleId(row.getPreSettleId())
            .setContractId(row.getContractId())
            .setChrgBchno(row.getChrgBchno())
            .setSetlId(row.getSetlId())
            .setMdtrtId(row.getMdtrtId())
            .setMsgid2207(row.getMsgid2207())
            .setStatus(row.getStatus())
            .setMedfeeSumamt(row.getMedfeeSumamt())
            .setFulamtOwnpayAmt(row.getFulamtOwnpayAmt())
            .setOverlmtSelfpay(row.getOverlmtSelfpay())
            .setPreselfpayAmt(row.getPreselfpayAmt())
            .setInscpScpAmt(row.getInscpScpAmt())
            .setActPayDedc(row.getActPayDedc())
            .setHifpPay(row.getHifpPay())
            .setPoolPropSelfpay(row.getPoolPropSelfpay())
            .setCvlservPay(row.getCvlservPay())
            .setHifesPay(row.getHifesPay())
            .setHifmiPay(row.getHifmiPay())
            .setHifobPay(row.getHifobPay())
            .setMafPay(row.getMafPay())
            .setHifdmPay(row.getHifdmPay())
            .setOthPay(row.getOthPay())
            .setFundPaySumamt(row.getFundPaySumamt())
            .setPsnPartAmt(row.getPsnPartAmt())
            .setAcctPay(row.getAcctPay())
            .setPsnCashPay(row.getPsnCashPay())
            .setHospPartAmt(row.getHospPartAmt())
            .setBalc(row.getBalc())
            .setAcctMulaidPay(row.getAcctMulaidPay())
            .setClrOptins(row.getClrOptins())
            .setClrWay(row.getClrWay())
            .setClrType(row.getClrType());
    }

    public static OpRevokeSettleCommand toRevokeSettleCommand(OutpatientChargeRevokeSettleExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpRevokeSettleCommand()
            .setSettlementRecordId(command.getSettlementRecordId())
            .setPaymentId(command.getPaymentId())
            .setPreSettleId(command.getPreSettleId())
            .setSetlId(command.getSettlementId());
    }

    public static OutpatientChargeRevokeSettleExtensionOut toExtensionRevokeSettleOut(OpRevokeSettleResult result) {
        OutpatientChargeRevokeSettleExtensionOut out = new OutpatientChargeRevokeSettleExtensionOut();
        if (result == null) {
            return out;
        }
        out.setSettlementRecordId(result.getSettlementRecordId());
        out.setSettlementId(result.getSetlId());
        out.setEncounterPlatformId(result.getMdtrtId());
        return out;
    }

    public static OpRevokeFeeDetailCommand toRevokeFeeDetailCommand(OutpatientChargeRevokeFeeDetailExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpRevokeFeeDetailCommand()
            .setChrgBchno(command.getChargeBatchNo())
            .setPaymentDraftId(command.getPaymentDraftId());
    }

    public static OpCorrectSettleCommand toCorrectSettleCommand(OutpatientChargeCorrectSettleExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpCorrectSettleCommand()
            .setSettlementRecordId(command.getSettlementRecordId())
            .setOriginalInfno(command.getOriginalInfno());
    }

    public static OpPlatformRegistrationCommand toPlatformRegistrationCommand(
        OutpatientRegistrationPlatformExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpPlatformRegistrationCommand()
            .setEncounterId(command.getEncounterId())
            .setContractId(command.getContractId())
            .setMdtrtCertType(command.getPayerCertType())
            .setMdtrtCertNo(command.getPayerCertNo())
            .setPsnNo(command.getInsuredPersonNo())
            .setInsuplcAdmdvs(command.getInsuplcAdmdvs())
            .setInsutype(command.getInsutype())
            .setMedType(command.getMedType());
    }

    public static OutpatientRegistrationPlatformExtensionOut toExtensionRegistrationOut(
        OpPlatformRegistrationResult result) {
        OutpatientRegistrationPlatformExtensionOut out = new OutpatientRegistrationPlatformExtensionOut();
        if (result != null) {
            out.setEncounterPlatformId(result.getMdtrtId());
        }
        return out;
    }

    public static OpRevokePlatformRegistrationCommand toRevokePlatformRegistrationCommand(
        OutpatientRegistrationPlatformExtensionIn command) {
        if (command == null) {
            return null;
        }
        return new OpRevokePlatformRegistrationCommand()
            .setEncounterId(command.getEncounterId())
            .setContractId(command.getContractId())
            .setMedType(command.getMedType())
            .setInsuplcAdmdvs(command.getInsuplcAdmdvs());
    }

    public static OutpatientRegistrationPlatformExtensionOut toExtensionRevokeRegistrationOut(
        OpRevokePlatformRegistrationResult result) {
        OutpatientRegistrationPlatformExtensionOut out = new OutpatientRegistrationPlatformExtensionOut();
        if (result != null) {
            out.setEncounterPlatformId(result.getMdtrtId());
        }
        return out;
    }
}
