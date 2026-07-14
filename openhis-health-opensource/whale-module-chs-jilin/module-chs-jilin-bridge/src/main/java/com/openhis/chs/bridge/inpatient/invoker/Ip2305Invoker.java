/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inpatient.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.inpatient.assembler.InpatientSettleCommandAssembler;
import com.openhis.chs.domain.inpatient.command.IpRevokeSettleCommand;
import com.openhis.chs.domain.inpatient.result.IpRevokeSettleResult;
import com.openhis.chs.domain.inpatient.command.IpSettleContext;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.service.YbInpatientFeeUploadDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleDomainService;
import com.openhis.chs.domain.inpatient.service.YbInpatientPreSettleQueryService;
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
import com.openhis.chs.gateway.scheme.inpatient.model.Ip2305Input;
import com.openhis.chs.gateway.scheme.inpatient.parser.Ip2305OutputParser;
import com.openhis.chs.gateway.scheme.inpatient.assembler.IpInpatientSettleInputAssembler;
import com.openhis.chs.bridge.outpatient.mapper.SetlinfoIntegrationMapper;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 【2305】撤销住院正式结算 Invoker。
 *
 * <p>出站：{@link InsuranceGatewayClient}；副作用：读 yb_settlement_record、yb_inpatient_pre_settle，写 yb_settlement_reversal、yb_settlement_record、yb_inpatient_pre_settle、yb_inpatient_fee_upload。</p>
 */
@Component
@RequiredArgsConstructor
public class Ip2305Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final YbSettlementReversalDomainService settlementReversalDomainService;
    private final YbInpatientPreSettleDomainService inpatientPreSettleDomainService;
    private final YbInpatientPreSettleQueryService inpatientPreSettleQueryService;
    private final YbInpatientFeeUploadDomainService inpatientFeeUploadDomainService;

    /**
     * 撤销住院正式结算并回写回执与预结算状态。
     *
     * @param command 含 settlementRecordId 或 paymentId/setlId、operatorId
     * @return 撤销结果（setlId、是否已撤销）
     */
    public IpRevokeSettleResult invoke(IpRevokeSettleCommand command) {
        Long tenantId = InpatientSettleCommandAssembler.resolveTenantId(command.getTenantId());
        YbSettlementRecord record = settlementRecordQueryService.requireOne(
            command.getSettlementRecordId(),
            command.getPaymentId(),
            command.getSetlId(),
            tenantId);
        if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
        }
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setContractId(record.getContractId())
            .setEncounterId(record.getEncounterId())
            .setChrgBchno(record.getChrgBchno())
            .setPreSettleId(record.getPreSettleId())
            .setSettlementRecordId(record.getId())
            .setTenantId(tenantId));
        try {
            return doInvoke(command, record, tenantId);
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 执行 2305 撤销住院正式结算并回写回执与预结算状态。
     *
     * @param command 撤销命令
     * @param record 结算回执实体
     * @param tenantId 租户 ID
     * @return 撤销结果（setlId、mdtrtId）
     */
    private IpRevokeSettleResult doInvoke(
            IpRevokeSettleCommand command,
            YbSettlementRecord record,
            Long tenantId) {
        YbInpatientPreSettle pre = resolvePreSettleForRevoke(record, command, tenantId);
        String contractBusNo = resolveContractBusNoForRevoke(record, pre, tenantId);
        IpSettleContext ctx;
        if (pre != null) {
            ctx = InpatientSettleCommandAssembler.rebuildContextFromPreSettle(pre);
        } else {
            ctx = InpatientSettleCommandAssembler.buildMinimalContext(record);
        }
        ctx.setContractBusNo(contractBusNo);
        InvokeContext invokeContext = InpatientSettleCommandAssembler.buildInvokeContext(ctx);

        Ip2305Input input2305 = IpInpatientSettleInputAssembler.assemble2305(
            record.getMdtrtId(), record.getSetlId(), record.getPsnNo());
        ResultBody body2305 = gatewayClient.invoke(
            contractBusNo, ChsInfNo.INPATIENT_SETTLE_REVOKE, input2305, invokeContext);
        ChsSetlinfoOutput revokeSetlinfo = Ip2305OutputParser.requireSetlinfo(body2305);
        String setldetailJson = SetlinfoFieldSupport.extractSetldetailJson(body2305.getOutput());

        settlementReversalDomainService.persistReversal(
            record,
            SetlinfoIntegrationMapper.toPlatformFields(revokeSetlinfo),
            setldetailJson,
            command.getOperatorId(),
            ChsInvokeLogContextSupport.currentInvokeLogId());
        settlementRecordDomainService.save(record);
        if (pre != null) {
            inpatientPreSettleDomainService.markRevokedAndSave(pre);
            inpatientFeeUploadDomainService.markSettleRevokedByPreSettle(pre, tenantId);
        }

        return new IpRevokeSettleResult()
            .setSettlementRecordId(record.getId())
            .setSetlId(record.getSetlId())
            .setMdtrtId(record.getMdtrtId());
    }

    /**
     * 解析撤销结算关联的住院预结算快照。
     *
     * @param record 结算回执实体
     * @param command 撤销命令（可含 preSettleId）
     * @param tenantId 租户 ID
     * @return 预结算快照；未找到时返回 null
     */
    private YbInpatientPreSettle resolvePreSettleForRevoke(
            YbSettlementRecord record,
            IpRevokeSettleCommand command,
            Long tenantId) {
        if (command.getPreSettleId() != null) {
            return inpatientPreSettleDomainService.findOptionalByIdAndTenant(
                command.getPreSettleId(), tenantId).orElse(null);
        }
        if (record.getPreSettleId() != null) {
            return inpatientPreSettleDomainService.findOptionalByIdAndTenant(
                record.getPreSettleId(), tenantId).orElse(null);
        }
        if (record.getPaymentId() != null) {
            return inpatientPreSettleQueryService
                .findOptionalSettledByPaymentDraftId(record.getPaymentId(), tenantId)
                .orElse(null);
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
            YbInpatientPreSettle pre,
            Long tenantId) {
        if (pre != null && isNotEmpty(pre.getContractBusNo())) {
            return pre.getContractBusNo().trim();
        }
        if (record.getPreSettleId() != null) {
            Optional<YbInpatientPreSettle> linked = inpatientPreSettleDomainService
                .findOptionalByIdAndTenant(record.getPreSettleId(), tenantId);
            if (linked.isPresent() && isNotEmpty(linked.get().getContractBusNo())) {
                return linked.get().getContractBusNo().trim();
            }
        }
        throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "无法解析 contractBusNo");
    }
}
