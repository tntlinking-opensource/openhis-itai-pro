/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.outpatient.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.chs.bridge.outpatient.assembler.OutpatientSettleCommandAssembler;
import com.openhis.chs.domain.outpatient.command.OpPreSettleCommand;
import com.openhis.chs.domain.outpatient.command.OpRevokeSettleCommand;
import com.openhis.chs.domain.outpatient.result.OpRevokeSettleResult;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.service.YbClinicFeeUploadDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleDomainService;
import com.openhis.chs.domain.outpatient.service.YbClinicPreSettleQueryService;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordDomainService;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.domain.settlement.service.YbSettlementReversalDomainService;
import com.openhis.chs.domain.settlement.support.SetlinfoFieldSupport;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.observability.ChsInvokeLogContext;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextSupport;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.common.model.ChsSetlinfoOutput;
import com.openhis.chs.gateway.scheme.outpatient.model.Op2208Input;
import com.openhis.chs.gateway.scheme.outpatient.assembler.Op2208InputAssembler;
import com.openhis.chs.gateway.scheme.outpatient.parser.Op2208OutputParser;
import com.openhis.chs.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 【2208】撤销门诊正式结算 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读 yb_settlement_record、yb_clinic_pre_settle，写 yb_settlement_reversal、yb_settlement_record、yb_clinic_pre_settle、yb_clinic_fee_upload。</p>
 */
@Component
@RequiredArgsConstructor
public class Op2208Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final YbSettlementReversalDomainService settlementReversalDomainService;
    private final YbClinicPreSettleDomainService clinicPreSettleDomainService;
    private final YbClinicPreSettleQueryService clinicPreSettleQueryService;
    private final YbClinicFeeUploadDomainService clinicFeeUploadDomainService;
    private final ChsCurrentOperatorResolver currentOperatorResolver;

    /**
     * 撤销门诊正式结算并回写回执与预结算状态。
     *
     * @param command 含 settlementRecordId 或 paymentId/setlId、operatorId
     * @return 撤销结果（setlId、是否已撤销）
     */
    public OpRevokeSettleResult invoke(OpRevokeSettleCommand command) {
        if (command.getOperatorId() == null) {
            command.setOperatorId(currentOperatorResolver.requireOperatorId("AUTH_REQUIRED", "请先登录"));
        }
        if (command.getTenantId() == null) {
            command.setTenantId(TenantUtils.getCurrentTenantId());
        }
        Long tenantId = OutpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        // Pre：撤销允许命中已 REVOKED（requireOne 会拒已撤，导致幂等短路失效、退号误报「请勿重复」）
        YbSettlementRecord record = settlementRecordQueryService.requireOneAllowRevoked(
            command.getSettlementRecordId(),
            command.getPaymentId(),
            command.getSetlId(),
            tenantId);
        // 幂等：本地已 REVOKED（含上次 2208 已成功、后续 2202/退费失败后的重试）时直接成功，禁止再打平台。
        if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
            return new OpRevokeSettleResult()
                .setSettlementRecordId(record.getId())
                .setSetlId(record.getSetlId())
                .setMdtrtId(record.getMdtrtId());
        }
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(record.getContractId())
            .setEncounterId(record.getEncounterId())
            .setChrgBchno(record.getChrgBchno())
            .setSettlementRecordId(record.getId())
            .setTenantId(tenantId));
        try {
            return doInvoke(command, record, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 执行 2208 撤销门诊正式结算并回写回执与预结算状态。
     *
     * @param command 撤销命令
     * @param record 结算回执实体
     * @param tenantId 租户 ID
     * @return 撤销结果（setlId、mdtrtId）
     */
    private OpRevokeSettleResult doInvoke(
            OpRevokeSettleCommand command,
            YbSettlementRecord record,
            Long tenantId) {
        YbClinicPreSettle pre = resolvePreSettleForRevoke(record, command, tenantId);
        String contractBusNo = resolveContractBusNoForRevoke(record, pre, tenantId);
        OpPreSettleCommand ctx;
        if (pre != null) {
            ctx = OutpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
        } else {
            ctx = OutpatientSettleCommandAssembler.buildMinimalContext(record);
        }
        ctx.setContractBusNo(contractBusNo);
        InvokeContext invokeContext = OutpatientSettleCommandAssembler.buildInvokeContext(ctx);

        Op2208Input input2208 = Op2208InputAssembler.assemble(
            record.getMdtrtId(), record.getSetlId(), record.getPsnNo());
        ResultBody body2208 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.OUTPATIENT_SETTLE_REVOKE, input2208, invokeContext);
        ChsSetlinfoOutput revokeSetlinfo = Op2208OutputParser.requireSetlinfo(body2208);
        String setldetailJson = SetlinfoFieldSupport.extractSetldetailJson(body2208.getOutput());

        settlementReversalDomainService.persistReversal(
            record,
            SetlinfoIntegrationMapper.toPlatformFields(revokeSetlinfo),
            setldetailJson,
            command.getOperatorId(),
            ChsInvokeLogContextSupport.currentInvokeLogId());
        settlementRecordDomainService.save(record);
        if (pre != null) {
            clinicPreSettleDomainService.markRevokedAndSave(pre);
        }
        clinicFeeUploadDomainService.markSettleRevokedByChrgBchno(record.getChrgBchno(), tenantId);

        return new OpRevokeSettleResult()
            .setSettlementRecordId(record.getId())
            .setSetlId(record.getSetlId())
            .setMdtrtId(record.getMdtrtId());
    }

    /**
     * 解析撤销结算关联的门诊预结算快照。
     *
     * @param record 结算回执实体
     * @param command 撤销命令（可含 preSettleId）
     * @param tenantId 租户 ID
     * @return 预结算快照；未找到时返回 null
     */
    private YbClinicPreSettle resolvePreSettleForRevoke(
            YbSettlementRecord record,
            OpRevokeSettleCommand command,
            Long tenantId) {
        if (command.getPreSettleId() != null) {
            return clinicPreSettleDomainService.findById(command.getPreSettleId());
        }
        if (record.getPreSettleId() != null) {
            return clinicPreSettleDomainService.findById(record.getPreSettleId());
        }
        if (record.getPaymentId() != null) {
            return clinicPreSettleDomainService.findLatestByPaymentDraftId(record.getPaymentId(), tenantId);
        }
        return null;
    }

    /**
     * 解析撤销结算所需的契约业务编号。
     *
     * @param record 结算回执实体
     * @param pre 关联的预结算快照（可为 null）
     * @param tenantId 租户 ID
     * @return contractBusNo
     */
    private String resolveContractBusNoForRevoke(
            YbSettlementRecord record,
            YbClinicPreSettle pre,
            Long tenantId) {
        if (pre != null && isNotEmpty(pre.getContractBusNo())) {
            return pre.getContractBusNo().trim();
        }
        if (record.getPreSettleId() != null) {
            return clinicPreSettleQueryService.findOptionalContractBusNoById(record.getPreSettleId())
                .filter(busNo -> isNotEmpty(busNo))
                .map(String::trim)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法解析 contractBusNo"));
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法解析 contractBusNo");
    }
}
