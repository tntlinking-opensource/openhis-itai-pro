/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.invoker;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.command.OnPaymentSettledCommand;
import com.openhis.chs.domain.settlement.command.SettlementCorrectionCommand;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.service.PaymentSettledCorrectionQueryService;
import com.openhis.chs.domain.settlement.service.YbSettlementCorrectionDomainService;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordDomainService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.invoke.service.YbInvokeLogQueryService;
import com.openhis.chs.gateway.observability.ChsInvokeLogContext;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextHolder;
import com.openhis.chs.gateway.observability.ChsInvokeLogContextSupport;
import com.openhis.chs.gateway.client.InsuranceGatewayClient;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.gateway.scheme.settlement.model.Op2601Input;
import com.openhis.chs.gateway.scheme.settlement.assembler.Op2601InputAssembler;
import com.openhis.chs.gateway.support.ChsPlatformResponseSupport;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【2601】冲正 Invoker：对平台状态不确定的结算交易发起冲正并落库审计。
 *
 * <p>命名遵循 bridge {@code *Invoker} 约定；无 Port 接口时不使用 {@code *PortImpl} 后缀。</p>
 *
 * <p>典型场景：收银落账后扫描支付单下 2207/2304 回执状态不明，
 * 逐条调用平台冲正接口；平台成功后以独立事务写 correction 并软删结算回执
 * （2601 冲正视同未发生，不对账写 reversal），并同步支付桥表 {@code REVOKED}、
 * 软删该 {@code setl_id} 的统筹/个账明细，避免日结虚增。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Op2601Invoker {

    private final InsuranceGatewayClient gatewayClient;
    private final PaymentSettledCorrectionQueryService correctionQueryService;
    private final YbSettlementRecordDomainService settlementRecordDomainService;
    private final YbSettlementCorrectionDomainService settlementCorrectionDomainService;
    private final YbInvokeLogQueryService invokeLogQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /**
     * 扫描支付单下平台状态不明的 2207/2304，逐条发起 2601 冲正。
     *
     * @param command 收银落账命令；{@code paymentId} 为空时跳过
     */
    public void correctUncertainSettlements(OnPaymentSettledCommand command) {
        if (command == null || command.getPaymentId() == null) {
            return;
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<SettlementCorrectionCommand> corrections = correctionQueryService.listUncertainByPayment(
            command.getPaymentId(),
            command.getEntererId(),
            tenantId);
        if (corrections.isEmpty()) {
            return;
        }
        for (SettlementCorrectionCommand correction : corrections) {
            try {
                invoke(correction);
            } catch (RuntimeException ex) {
                // 单条失败不阻断同支付单其余冲正
                log.error(
                    "2601 correction failed paymentId={} settlementRecordId={}",
                    command.getPaymentId(),
                    correction.getSettlementRecordId(),
                    ex);
            }
        }
    }

    /**
     * 对单条冲正命令发起 2601 并落库审计与撤销凭证。
     *
     * @param command 含 omsgid、oinfno、psnNo、contractBusNo；缺必填项时抛 {@link BusinessException}
     */
    public void invoke(SettlementCorrectionCommand command) {
        if (command == null || isEmpty(command.getOmsgid())
            || isEmpty(command.getOinfno()) || isEmpty(command.getPsnNo())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2601 冲正参数不完整");
        }
        Long tenantId = command.getTenantId();
        if (tenantId == null) {
            tenantId = TenantUtils.getCurrentTenantId();
        }
        YbSettlementRecord record = null;
        if (command.getSettlementRecordId() != null) {
            record = settlementRecordDomainService.findByIdAndTenant(command.getSettlementRecordId(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执不存在"));
        }
        if (isEmpty(command.getContractBusNo())) {
            resolveContractBusNo(command, tenantId);
        }
        if (isEmpty(command.getContractBusNo())) {
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "2601 冲正缺少 contractBusNo");
        }
        ChsInvokeLogContextHolder.set(new ChsInvokeLogContext()
            .setSettlementRecordId(command.getSettlementRecordId())
            .setTenantId(tenantId));
        if (record != null) {
            final YbSettlementRecord settlementRecord = record;
            ChsInvokeLogContextHolder.merge(ctx -> ctx
                .setContractId(settlementRecord.getContractId())
                .setEncounterId(settlementRecord.getEncounterId())
                .setChrgBchno(settlementRecord.getChrgBchno()));
        }
        try {
            InvokeContext invokeContext = buildInvokeContext(command.getOperatorId());
            Op2601Input input2601 = Op2601InputAssembler.assemble(
                command.getPsnNo().trim(),
                command.getOmsgid().trim(),
                command.getOinfno().trim());
            ResultBody body2601 = gatewayClient.invoke(
                command.getContractBusNo().trim(),
                ChsInfNo.TRANSACTION_CORRECT,
                input2601,
                invokeContext);
            ChsPlatformResponseSupport.requireSuccess(body2601, ChsInfNo.TRANSACTION_CORRECT);
            Long invokeLogId = ChsInvokeLogContextSupport.currentInvokeLogId();
            // 平台已冲正：本地独立事务软删结算（冲正≠一收一退），避免外层回滚留孤儿
            try {
                settlementCorrectionDomainService.persistLocalAfterPlatformSuccess(
                    command, invokeLogId, tenantId);
            } catch (RuntimeException ex) {
                log.error(
                    "2601 平台成功但本地落库失败，须运维软删结算；settlementRecordId={} omsgid={}",
                    command.getSettlementRecordId(),
                    command.getOmsgid(),
                    ex);
                throw ex;
            }
            // 内存实体与独立事务对齐；桥表 REVOKED + 软删统筹/个账明细（与平台净额 0 同态）
            if (record != null) {
                if (!YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                    record.setStatus(YbSettlementVoucherStatus.REVOKED);
                }
                record.setIsDeleted(true);
                this.paymentReconciliationDomainService.markYbSettlementsRevoked(
                    record.getId(),
                    record.getSetlId());
                // 仅软删本 setl 的 INSURANCE_*；不碰自付渠道、不影响 2208/UN_PAY 正常退费
                this.paymentReconciliationDomainService.softDeleteInsuranceFundAcctDetailsBySetlId(
                    record.getSetlId());
            }
            log.info(
                "2601 correction succeeded settlementRecordId={} oinfno={} omsgid={}",
                command.getSettlementRecordId(),
                command.getOinfno(),
                command.getOmsgid());
        } finally {
            ChsInvokeLogContextHolder.clear();
        }
    }

    /**
     * 构建出站调用上下文，携带操作员 ID。
     *
     * @param operatorId 操作员 ID
     * @return 出站上下文；operatorId 为空时返回 null
     */
    private static InvokeContext buildInvokeContext(Long operatorId) {
        if (operatorId == null) {
            return null;
        }
        return InvokeContext.builder().operatorId(operatorId.toString()).build();
    }

    /**
     * 从同回执或同支付单 invoke 审计中回填 contractBusNo（2601 出站路由必填）。
     *
     * @param command  冲正命令（就地回填 contractBusNo）
     * @param tenantId 租户 ID
     */
    private void resolveContractBusNo(SettlementCorrectionCommand command, Long tenantId) {
        if (command.getSettlementRecordId() != null) {
            invokeLogQueryService.findFirstContractBusNoBySettlementRecordId(
                    command.getSettlementRecordId(), tenantId)
                .ifPresent(command::setContractBusNo);
        }
        if (!isEmpty(command.getContractBusNo())) {
            return;
        }
        if (command.getSettlementRecordId() != null) {
            settlementRecordDomainService.findByIdAndTenant(command.getSettlementRecordId(), tenantId)
                .ifPresent(record -> {
                    if (record.getPaymentId() != null) {
                        invokeLogQueryService.findFirstContractBusNoByPaymentDraftId(
                                record.getPaymentId(), tenantId)
                            .ifPresent(command::setContractBusNo);
                    }
                });
        }
    }
}
