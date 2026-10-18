/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.application.order.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import com.zxplat.plugin.unionpay.bpc.application.order.response.BpcOrderSummaryResponse;
import com.zxplat.plugin.unionpay.bpc.domain.service.BpcSettleDomainService;
import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrder;
import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrderStatus;
import com.zxplat.plugin.unionpay.bpc.domain.refund.command.ApplyClientRefundCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.AbandonPendingScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.scan.command.PrepareConsumerScanCommand;
import com.zxplat.plugin.unionpay.bpc.domain.query.command.PageBpcOrdersCommand;
import com.zxplat.plugin.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.zxplat.plugin.unionpay.bpc.domain.query.command.RetrySettleFromBpcOrderCommand;
import com.zxplat.plugin.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.AbandonPendingScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.ApplyClientScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.query.result.PageBpcOrdersResult;
import com.zxplat.plugin.unionpay.bpc.domain.scan.result.PrepareConsumerScanResult;
import com.zxplat.plugin.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import com.zxplat.plugin.unionpay.bpc.domain.query.result.RetrySettleFromBpcOrderResult;
import com.zxplat.plugin.unionpay.bpc.domain.share.BpcTranType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 银联 BPC 对外 AppService（POST /api/app/unionPayBpc/{methodName}）。
 */
@Service
@Validated
@RequiredArgsConstructor
public class UnionPayBpcAppService {

    private final BpcSettleDomainService settleDomainService;

    private final UnionPayBpcOrderQueryService orderQueryService;

    private final PaymentReconciliationStatusLookup paymentStatusLookup;

    /**
     * 被扫预下单。医保 2206 仍为 ACTIVE 时拒绝，避免 2207 失败前银联已扣款。
     *
     * @param command 支付草稿、明细下标与金额
     * @return 商户订单号与终端参数，供 Win 发起消费
     * @throws BusinessException 草稿仍有未完成医保预结算时
     */
    public PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command) {
        // Gate：2207 未完成禁止预下单；成功后预结算离开 ACTIVE，补结算复用已有 SUCCESS 单不走本方法。
        Long paymentId = null;
        if (command != null) {
            paymentId = command.getPaymentId();
        }
        if (paymentStatusLookup.hasActiveInsurancePreSettle(TenantUtils.getCurrentTenantId(), paymentId)) {
            throw new BusinessException(
                "BPC_INSURANCE_BEFORE_SCAN",
                "医保结算尚未完成，请先完成医保结算再扫码，勿重复扫码");
        }
        return settleDomainService.prepareConsumerScan(command);
    }

    public ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command) {
        return settleDomainService.applyClientScanResult(command);
    }

    public AbandonPendingScanResult abandonPendingScan(AbandonPendingScanCommand command) {
        return settleDomainService.abandonPendingScan(command);
    }

    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        return settleDomainService.applyClientRefundResult(command);
    }

    /**
     * 补偿「HIS 已退、银联未退」：仅落 BPC 退货确认，禁止重做 HIS 本地退费。
     *
     * <p>前置：Win {@code /api/unionpay/refund} 已成功；原支付存在完成态逆向单；BPC 消费单仍为 SUCCESS。</p>
     */
    public ApplyClientRefundResult confirmOrphanClientRefund(ApplyClientRefundCommand command) {
        if (command == null || command.getPaymentId() == null) {
            throw new BusinessException("BPC_ORPHAN_REFUND_INVALID", "paymentId 不能为空");
        }
        if (!StringUtils.hasText(command.getMerTradeNo()) || !StringUtils.hasText(command.getTradeNo())) {
            throw new BusinessException("BPC_ORPHAN_REFUND_INVALID", "merTradeNo / tradeNo 不能为空");
        }
        if (command.getRefundAmount() == null) {
            throw new BusinessException("BPC_ORPHAN_REFUND_INVALID", "refundAmount 不能为空");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (!paymentStatusLookup.hasCompletedReverseRefund(tenantId, command.getPaymentId())) {
            throw new BusinessException(
                "BPC_ORPHAN_REFUND_NOT_ELIGIBLE",
                "未找到 HIS 完成态逆向退费单，禁止走孤儿补退（请走正常退费）");
        }
        List<BpcOrder> orders = settleDomainService.getOrdersByPayment(command.getPaymentId());
        BpcOrder successOrder = null;
        for (BpcOrder order : orders) {
            if (order == null) {
                continue;
            }
            if (BpcTranType.REFUND.getCode().equals(order.getTranType())
                || BpcOrderStatus.REFUNDED.getCode().equals(order.getStatus())) {
                throw new BusinessException(
                    "BPC_ORPHAN_REFUND_NOT_ELIGIBLE",
                    "该支付单已有银联退货记录，无需重复补退");
            }
            if (BpcOrderStatus.SUCCESS.getCode().equals(order.getStatus())
                && BpcTranType.CONSUMER_SCAN.getCode().equals(order.getTranType())
                && command.getMerTradeNo().trim().equals(order.getMerTradeNo())
                && StringUtils.hasText(order.getTradeNo())) {
                successOrder = order;
            }
        }
        if (successOrder == null) {
            throw new BusinessException(
                "BPC_ORPHAN_REFUND_NOT_ELIGIBLE",
                "未找到可补退的银联 SUCCESS 消费单");
        }
        // tradeNo：入参可为原消费单号，或 Win 退货回传的新单号；资格以 merTradeNo 锁定原单为准。
        // 落库仍用入参 tradeNo（空则回落原单），避免 Win 已退成功后因单号不一致导致 BPC 未落 REFUNDED。
        if (!StringUtils.hasText(command.getTradeNo())) {
            command.setTradeNo(successOrder.getTradeNo());
        }
        return settleDomainService.applyClientRefundResult(command);
    }

    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        return settleDomainService.queryOrder(command);
    }

    /** 财务银联订单分页查询（P1 查单页）。 */
    public PageBpcOrdersResult pageOrders(PageBpcOrdersCommand command) {
        return orderQueryService.pageOrders(command);
    }

    public List<BpcOrderSummaryResponse> getOrderByPayment(Long paymentId) {
        return settleDomainService.getOrdersByPayment(paymentId).stream()
                .map(BpcOrderSummaryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 半成功补结算资格校验：BPC 已 SUCCESS 时允许 health 侧重传 clientScanResult settle。
     *
     * @param command paymentId + merTradeNo
     * @return 是否可补结算
     */
    public RetrySettleFromBpcOrderResult retrySettleFromBpcOrder(RetrySettleFromBpcOrderCommand command) {
        if (command == null || command.getPaymentId() == null
            || command.getMerTradeNo() == null || command.getMerTradeNo().isBlank()) {
            return new RetrySettleFromBpcOrderResult()
                .setEligible(false)
                .setMessage("paymentId 与 merTradeNo 不能为空");
        }
        boolean eligible = settleDomainService.isRetrySettleEligible(
            command.getPaymentId(),
            command.getMerTradeNo());
        return new RetrySettleFromBpcOrderResult()
            .setEligible(eligible)
            .setMessage(eligible
                ? "可补结算，请重传完整 clientScanResult"
                : "未找到成功的 BPC 订单，不可补结算");
    }
}
