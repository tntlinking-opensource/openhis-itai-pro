/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.bridge.integration.workinjury;

import com.zxplat.plugin.wi.domain.invoke.command.PrepareWorkInjuryInvokeRequest;
import com.zxplat.plugin.wi.domain.invoke.result.PrepareWorkInjuryInvokeResponse;
import com.zxplat.plugin.wi.domain.invoke.command.SubmitWorkInjuryInvokeResultRequest;
import com.zxplat.plugin.wi.domain.invoke.result.SubmitWorkInjuryInvokeResultResponse;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryPrepareInvokeExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySubmitInvokeResultExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryPreSettleQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySubmitInvokeResultExtensionOut;
import com.zxplat.plugin.wi.domain.register.command.BindQualificationCommand;
import com.zxplat.plugin.wi.domain.register.command.Build2301PayloadCommand;
import com.zxplat.plugin.wi.domain.settle.command.ConfirmWorkInjurySettleCommand;
import com.zxplat.plugin.wi.domain.settle.result.ConfirmWorkInjurySettleResult;
import com.zxplat.plugin.wi.domain.settle.command.StartWorkInjuryPreSettleCommand;
import com.zxplat.plugin.wi.domain.settle.command.StartWorkInjuryReverseCommand;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryConfirmSettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjury9102ExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjuryReverseExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn.WorkInjurySettleExtensionIn;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryConfirmSettleExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySettlementRecordExtensionOut;
import com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut.PrepareWorkInjuryInvokeExtensionOut;

/**
 * health-integration Extension DTO ↔ wi-jilin domain DTO 映射（Extension 防腐层，无 IO）。
 *
 * <p>中性 Extension 类型不带 {@code Wi} 前缀；平台 scheme 仅在 bridge/gateway 内出现。</p>
 */
public final class WorkInjuryIntegrationMapper {

    private WorkInjuryIntegrationMapper() {
    }

    /** domain prepare 响应 → Extension 中性 DTO。
     *
     * @param response domain prepare 结果
     * @return Extension 出参
     */
    public static PrepareWorkInjuryInvokeExtensionOut toNeutralPrepareResult(PrepareWorkInjuryInvokeResponse response) {
        if (response == null) {
            return new PrepareWorkInjuryInvokeExtensionOut();
        }
        return new PrepareWorkInjuryInvokeExtensionOut()
                .setSessionId(response.getSessionId())
                .setStepToken(response.getStepToken())
                .setInfno(response.getInfno())
                .setFixmedinsCode(response.getFixmedinsCode())
                .setOperatorNo(response.getOperatorNo())
                .setInput(response.getInput())
                .setNextAction(response.getNextAction())
                .setErrMsg(response.getErrMsg());
    }

    /** 9102 ExtensionIn → domain prepare 请求。
     *
     * @param command 9102 启动参数
     * @return phase 由调用方后续设置为 REG_QUERY
     */
    public static PrepareWorkInjuryInvokeRequest toPrepareRequest(WorkInjury9102ExtensionIn command) {
        return new PrepareWorkInjuryInvokeRequest()
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setOperatorId(command.getOperatorId());
    }

    /** invoke ExtensionIn → domain prepare 请求。
     *
     * @param command 通用 prepare 入参
     * @return domain 请求
     */
    public static PrepareWorkInjuryInvokeRequest toPrepareRequest(WorkInjuryPrepareInvokeExtensionIn command) {
        if (command == null) {
            return new PrepareWorkInjuryInvokeRequest();
        }
        return new PrepareWorkInjuryInvokeRequest()
                .setSessionId(command.getSessionId())
                .setPhase(command.getPhase())
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setPaymentDraftId(command.getPaymentDraftId())
                .setOperatorId(command.getOperatorId())
                .setContext(command.getContext());
    }

    /** invoke submit ExtensionIn → domain submit 请求。
     *
     * @param command Win 回写结果
     * @return domain submit 请求
     */
    public static SubmitWorkInjuryInvokeResultRequest toSubmitRequest(WorkInjurySubmitInvokeResultExtensionIn command) {
        if (command == null) {
            return new SubmitWorkInjuryInvokeResultRequest();
        }
        return new SubmitWorkInjuryInvokeResultRequest()
                .setSessionId(command.getSessionId())
                .setStepToken(command.getStepToken())
                .setSuccess(command.isSuccess())
                .setInfcode(command.getInfcode())
                .setErrMsg(command.getErrMsg())
                .setOutput(command.getOutput())
                .setWinMsgid(command.getWinMsgid())
                .setContext(command.getContext())
                .setWinRawInput(command.getWinRawInput());
    }

    /** domain submit 响应 → Extension 中性 DTO。
     *
     * @param response domain submit 结果
     * @return Extension 出参
     */
    public static WorkInjurySubmitInvokeResultExtensionOut toNeutralSubmitResponse(
            SubmitWorkInjuryInvokeResultResponse response) {
        if (response == null) {
            return new WorkInjurySubmitInvokeResultExtensionOut();
        }
        return new WorkInjurySubmitInvokeResultExtensionOut()
                .setSessionId(response.getSessionId())
                .setNextAction(response.getNextAction())
                .setErrMsg(response.getErrMsg())
                .setSummary(response.getSummary())
                .setSettlementRecordId(response.getSettlementRecordId())
                .setSetlId(response.getSetlId())
                .setChrgBchno(response.getChrgBchno())
                .setMedfeeSumamt(response.getMedfeeSumamt())
                .setFundPaySumamt(response.getFundPaySumamt())
                .setPsnCashPay(response.getPsnCashPay());
    }

    /** 2301/对账等场景：组装 REG_APPROVAL prepare 请求（phase 由调用方设置）。
     *
     * @param encounterId 就诊 id
     * @param contractId  费用性质 id
     * @param operatorId  操作员 id
     * @param context     2301 表单等
     * @return prepare 请求（未设 phase）
     */
    public static PrepareWorkInjuryInvokeRequest toRegApprovalPrepareRequest(
            Long encounterId,
            Long contractId,
            Long operatorId,
            java.util.Map<String, Object> context) {
        return new PrepareWorkInjuryInvokeRequest()
                .setEncounterId(encounterId)
                .setContractId(contractId)
                .setOperatorId(operatorId)
                .setContext(context);
    }

    /** 门诊收银 ExtensionIn → startPreSettle 命令。
     *
     * @param command 收银预结算入参
     * @return domain 命令
     */
    public static StartWorkInjuryPreSettleCommand toStartPreSettleCommand(WorkInjurySettleExtensionIn command) {
        return new StartWorkInjuryPreSettleCommand()
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setPaymentDraftId(command.getPaymentDraftId())
                .setOperatorId(command.getOperatorId())
                .setChargeItemIds(command.getChargeItemIds())
                .setContext(command.getContext());
    }

    /** confirmSettle ExtensionIn → domain 命令。
     *
     * @param command 收银落账挂接入参
     * @return domain 命令
     */
    public static ConfirmWorkInjurySettleCommand toConfirmSettleCommand(WorkInjuryConfirmSettleExtensionIn command) {
        if (command == null) {
            return new ConfirmWorkInjurySettleCommand();
        }
        return new ConfirmWorkInjurySettleCommand()
                .setPaymentDraftId(command.getPaymentDraftId())
                .setPaymentId(command.getPaymentId())
                .setChrgBchno(command.getChrgBchno())
                .setEncounterId(command.getEncounterId());
    }

    /** 2208 退费 ExtensionIn → startReverse 命令。
     *
     * @param command 退费入参
     * @return domain 命令
     */
    public static StartWorkInjuryReverseCommand toStartReverseCommand(WorkInjuryReverseExtensionIn command) {
        if (command == null) {
            return new StartWorkInjuryReverseCommand();
        }
        return new StartWorkInjuryReverseCommand()
                .setSettlementRecordId(command.getSettlementRecordId())
                .setPaymentId(command.getPaymentId())
                .setEncounterId(command.getEncounterId())
                .setContractId(command.getContractId())
                .setOperatorId(command.getOperatorId());
    }

    /** 结算回执摘要 → Extension DTO。
     *
     * @param summary domain 摘要
     * @return Extension 出参；summary 为 null 时返回 null
     */
    public static WorkInjurySettlementRecordExtensionOut toNeutralSettlementRecord(
            com.zxplat.plugin.wi.domain.settle.result.WorkInjurySettlementRecordSummary summary) {
        if (summary == null) {
            return null;
        }
        return new WorkInjurySettlementRecordExtensionOut()
                .setSettlementRecordId(summary.getSettlementRecordId())
                .setPaymentId(summary.getPaymentId())
                .setPreSettleId(summary.getPreSettleId())
                .setContractId(summary.getContractId())
                .setChargeBatchNo(summary.getChrgBchno())
                .setSettlementId(summary.getSetlId())
                .setEncounterPlatformId(summary.getMdtrtId())
                .setStatus(summary.getStatus())
                .setMsgid2207(summary.getMsgid2207());
    }

    /** confirmSettle 结果 → Extension DTO。
     *
     * @param result domain 落账结果
     * @return Extension 出参
     */
    public static WorkInjuryConfirmSettleExtensionOut toNeutralConfirmResult(ConfirmWorkInjurySettleResult result) {
        if (result == null) {
            return new WorkInjuryConfirmSettleExtensionOut();
        }
        return new WorkInjuryConfirmSettleExtensionOut()
                .setSettlementRecordId(result.getSettlementRecordId())
                .setSetlId(result.getSetlId())
                .setChrgBchno(result.getChrgBchno())
                .setMdtrtId(result.getMdtrtId())
                .setAccountId(result.getAccountId())
                .setMedfeeSumamt(result.getMedfeeSumamt())
                .setFundPaySumamt(result.getFundPaySumamt())
                .setAcctPay(result.getAcctPay())
                .setPsnCashPay(result.getPsnCashPay());
    }

    /** 预结算快照 → Extension 查询 DTO。
     *
     * @param snapshot domain 2206 摘要
     * @return Extension 出参；snapshot 为 null 时返回 null
     */
    public static WorkInjuryPreSettleQueryExtensionOut toNeutralPreSettleSnapshot(
            com.zxplat.plugin.wi.domain.settle.result.WorkInjuryPreSettleSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new WorkInjuryPreSettleQueryExtensionOut()
                .setChrgBchno(snapshot.getChrgBchno())
                .setMedfeeSumamt(snapshot.getMedfeeSumamt())
                .setFundPaySumamt(snapshot.getFundPaySumamt())
                .setAcctPay(snapshot.getAcctPay())
                .setPsnCashPay(snapshot.getPsnCashPay());
    }

    /** bindQualification SPI 参数 → domain 命令。
     *
     * @param encounterId     就诊 id
     * @param contractId      费用性质 id
     * @param qualificationId   9102 所选 qualification_id
     * @return domain 命令
     */
    public static BindQualificationCommand toBindQualificationCommand(
            Long encounterId,
            Long contractId,
            String qualificationId) {
        return new BindQualificationCommand()
                .setEncounterId(encounterId)
                .setContractId(contractId)
                .setQualificationId(qualificationId);
    }

    /** 2301 表单 Map → Build2301Payload 命令。
     *
     * @param encounterId 就诊 id
     * @param contractId  费用性质 id
     * @param form        2301 表单字段 Map
     * @return domain 命令
     */
    public static Build2301PayloadCommand toBuild2301Command(
            Long encounterId,
            Long contractId,
            java.util.Map<String, Object> form) {
        Build2301PayloadCommand command = new Build2301PayloadCommand()
                .setEncounterId(encounterId)
                .setContractId(contractId);
        if (form == null) {
            return command;
        }
        command.setApproveType(stringValue(form, "approveType"));
        command.setStartDate(stringValue(form, "startDate"));
        command.setEndDate(stringValue(form, "endDate"));
        command.setTerminReason(stringValue(form, "terminReason"));
        command.setInjureAffirmId(stringValue(form, "injureAffirmId"));
        command.setOrgCode(stringValue(form, "orgCode"));
        command.setOrgName(stringValue(form, "orgName"));
        return command;
    }

    /** 从 2301 表单 Map 取字符串字段（空串视为 null）。 */
    private static String stringValue(java.util.Map<String, Object> form, String key) {
        Object raw = form.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw);
        if (value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
