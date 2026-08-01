/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.service;

import com.openhis.health.domain.share.payment.BpcPayTypeMapper;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import com.openhis.whale.unionpay.bpc.gateway.config.BpcContractConfig;
import com.openhis.whale.unionpay.bpc.gateway.session.BpcContractConfigLoader;
import com.openhis.whale.unionpay.bpc.domain.share.MerTradeNoGenerator;
import com.openhis.whale.unionpay.bpc.domain.refund.command.ApplyClientRefundCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.ApplyClientScanResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.PrepareConsumerScanResult;
import com.openhis.whale.unionpay.bpc.domain.query.result.QueryBpcOrderResult;
import com.openhis.whale.unionpay.bpc.domain.order.entity.BpcOrder;
import com.openhis.whale.unionpay.bpc.domain.order.repository.BpcOrderRepository;
import com.openhis.whale.unionpay.bpc.domain.order.entity.BpcOrderStatus;
import com.openhis.whale.unionpay.bpc.domain.share.BpcTranType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 银联 BPC 被扫落库编排（upc_bpc_order 幂等与状态迁移）。
 */
@Service
@RequiredArgsConstructor
public class BpcSettleDomainService {

    private final BpcOrderRepository bpcOrderRepository;

    @Transactional
    public PrepareConsumerScanResult prepareConsumerScan(PrepareConsumerScanCommand command) {
        requirePaymentId(command.getPaymentId());
        requireAmount(command.getAmount());
        String fixmedinsCode = BpcContractConfigLoader.resolveFixmedinsCode(command.getFixmedinsCode());
        if (!StringUtils.hasText(fixmedinsCode)) {
            throw new BusinessException(
                "BPC_CONFIG_MISSING",
                "fixmedinsCode 不能为空（多院点配置时请显式传入）");
        }
        command.setFixmedinsCode(fixmedinsCode);
        BpcContractConfig config = requireConfig(fixmedinsCode);
        Long tenantId = TenantUtils.getCurrentTenantId();

        String merTradeNo = MerTradeNoGenerator.generate(
                command.getPaymentId(), command.getDetailIndex(), config);
        long txnAmtFen = toFen(command.getAmount());

        BpcOrder order = new BpcOrder();
        order.setTenantId(tenantId);
        order.setPaymentId(command.getPaymentId());
        order.setDetailIndex(command.getDetailIndex() != null ? command.getDetailIndex() : 0);
        order.setMerTradeNo(merTradeNo);
        order.setTranType(BpcTranType.CONSUMER_SCAN.getCode());
        order.setTxnAmtFen(txnAmtFen);
        order.setStatus(BpcOrderStatus.PENDING.getCode());
        order.setProfileId(resolveProfileId(config));
        order.setPosNo(resolvePosNo(config));
        order.setCreatedAt(LocalDateTime.now());
        order.setIsDeleted(false);
        bpcOrderRepository.save(order);

        return new PrepareConsumerScanResult()
                .setOrderId(order.getId())
                .setMerTradeNo(merTradeNo)
                .setPosNo(order.getPosNo())
                .setMid(config.getMid())
                .setTid(config.getTid())
                .setTxnAmtFen(txnAmtFen)
                .setTransport(config.getTransport());
    }

    @Transactional
    public ApplyClientScanResult applyClientScanResult(ApplyClientScanCommand command) {
        requireMerTradeNo(command.getMerTradeNo());
        Long tenantId = TenantUtils.getCurrentTenantId();
        BpcOrder order = bpcOrderRepository
                .findByTenantIdAndMerTradeNoAndIsDeletedFalse(tenantId, command.getMerTradeNo())
                .orElseThrow(() -> new BusinessException("BPC_ORDER_NOT_FOUND", "BPC 订单不存在"));

        if (BpcOrderStatus.SUCCESS.getCode().equals(order.getStatus())) {
            this.syncOrderPaymentId(order, command);
            order.setUpdatedAt(LocalDateTime.now());
            bpcOrderRepository.save(order);
            return toScanResult(order, true);
        }

        if (!"00".equals(StringUtils.trimAllWhitespace(command.getRespCode()))) {
            order.setStatus(BpcOrderStatus.FAILED.getCode());
            order.setRespCode(command.getRespCode());
            order.setRespMsg(command.getRespMsg());
            order.setUpdatedAt(LocalDateTime.now());
            bpcOrderRepository.save(order);
            throw new BusinessException("BPC_SCAN_FAILED", StringUtils.hasText(command.getRespMsg())
                    ? command.getRespMsg() : "银联被扫失败");
        }

        order.setStatus(BpcOrderStatus.SUCCESS.getCode());
        order.setTradeNo(command.getTradeNo());
        order.setPayType(command.getPayType());
        order.setRespCode(command.getRespCode());
        order.setRespMsg(command.getRespMsg());
        if (command.getTxnAmtFen() != null) {
            order.setTxnAmtFen(command.getTxnAmtFen());
        }
        this.syncOrderPaymentId(order, command);
        order.setUpdatedAt(LocalDateTime.now());
        bpcOrderRepository.save(order);
        return toScanResult(order, true);
    }

    /**
     * 住院结算少补等场景：预下单 paymentId 与最终 settle paymentId 不一致时，回写 BPC 订单锚点。
     */
    private void syncOrderPaymentId(BpcOrder order, ApplyClientScanCommand command) {
        if (command.getPaymentId() == null || command.getPaymentId().equals(order.getPaymentId())) {
            return;
        }
        order.setPaymentId(command.getPaymentId());
        if (command.getDetailIndex() != null) {
            order.setDetailIndex(command.getDetailIndex());
        }
    }

    @Transactional(readOnly = true)
    public boolean isRetrySettleEligible(Long paymentId, String merTradeNo) {
        requirePaymentId(paymentId);
        requireMerTradeNo(merTradeNo);
        Long tenantId = TenantUtils.getCurrentTenantId();
        BpcOrder order = bpcOrderRepository
            .findByTenantIdAndMerTradeNoAndIsDeletedFalse(tenantId, merTradeNo.trim())
            .orElse(null);
        if (order == null) {
            return false;
        }
        return BpcOrderStatus.SUCCESS.getCode().equals(order.getStatus());
    }

    @Transactional
    public ApplyClientRefundResult applyClientRefundResult(ApplyClientRefundCommand command) {
        requireMerTradeNo(command.getMerTradeNo());
        Long tenantId = TenantUtils.getCurrentTenantId();
        BpcOrder original = bpcOrderRepository
                .findByTenantIdAndMerTradeNoAndIsDeletedFalse(tenantId, command.getMerTradeNo())
                .orElseThrow(() -> new BusinessException("BPC_ORDER_NOT_FOUND", "原 BPC 订单不存在"));

        if (!"00".equals(StringUtils.trimAllWhitespace(command.getRespCode()))) {
            throw new BusinessException("BPC_REFUND_FAILED", StringUtils.hasText(command.getRespMsg())
                    ? command.getRespMsg() : "银联退货失败");
        }

        // Gate：退货金额须等于原消费单分（混合支付禁止把医保个账打进银联退货）
        long refundFen = toFen(command.getRefundAmount());
        Long originalFen = original.getTxnAmtFen();
        if (originalFen != null && originalFen > 0 && refundFen != originalFen.longValue()) {
            throw new BusinessException(
                    "BPC_REFUND_AMOUNT_MISMATCH",
                    "银联退货金额须等于原扫码实付（"
                            + originalFen
                            + " 分），当前为 "
                            + refundFen
                            + " 分");
        }

        // 幂等：原单已 REFUNDED 时不再重复插单；若尚无 R 行则补一笔便于对账
        if (BpcOrderStatus.REFUNDED.getCode().equals(original.getStatus())) {
            String existingRefundMerTradeNo = null;
            Long existingRefundOrderId = null;
            for (BpcOrder row : bpcOrderRepository.findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
                    tenantId, original.getPaymentId())) {
                if (row != null && BpcTranType.REFUND.getCode().equals(row.getTranType())) {
                    existingRefundMerTradeNo = row.getMerTradeNo();
                    existingRefundOrderId = row.getId();
                    break;
                }
            }
            if (existingRefundOrderId != null) {
                return new ApplyClientRefundResult()
                        .setSuccess(true)
                        .setOrderId(existingRefundOrderId)
                        .setMerTradeNo(original.getMerTradeNo())
                        .setRefundMerTradeNo(existingRefundMerTradeNo)
                        .setRespMsg(StringUtils.hasText(command.getRespMsg())
                                ? command.getRespMsg()
                                : "BPC 已退货，幂等成功");
            }
            String refundMerTradeNo = StringUtils.hasText(command.getRefundMerTradeNo())
                    ? command.getRefundMerTradeNo()
                    : MerTradeNoGenerator.generateRefundMerTradeNo(original.getMerTradeNo());
            BpcOrder refundOrder = new BpcOrder();
            refundOrder.setTenantId(tenantId);
            refundOrder.setPaymentId(original.getPaymentId());
            refundOrder.setDetailIndex(original.getDetailIndex());
            refundOrder.setMerTradeNo(refundMerTradeNo);
            refundOrder.setTradeNo(command.getTradeNo());
            refundOrder.setTranType(BpcTranType.REFUND.getCode());
            refundOrder.setTxnAmtFen(refundFen > 0 ? refundFen : original.getTxnAmtFen());
            refundOrder.setStatus(BpcOrderStatus.REFUNDED.getCode());
            refundOrder.setRespCode(command.getRespCode());
            refundOrder.setRespMsg(StringUtils.hasText(command.getRespMsg())
                    ? command.getRespMsg()
                    : "BPC 已退货，补记退货单");
            refundOrder.setProfileId(original.getProfileId());
            refundOrder.setPosNo(original.getPosNo());
            refundOrder.setCreatedAt(LocalDateTime.now());
            refundOrder.setIsDeleted(false);
            bpcOrderRepository.save(refundOrder);
            return new ApplyClientRefundResult()
                    .setSuccess(true)
                    .setOrderId(refundOrder.getId())
                    .setMerTradeNo(original.getMerTradeNo())
                    .setRefundMerTradeNo(refundMerTradeNo)
                    .setRespMsg(refundOrder.getRespMsg());
        }

        String refundMerTradeNo = StringUtils.hasText(command.getRefundMerTradeNo())
                ? command.getRefundMerTradeNo()
                : MerTradeNoGenerator.generateRefundMerTradeNo(original.getMerTradeNo());

        BpcOrder refundOrder = new BpcOrder();
        refundOrder.setTenantId(tenantId);
        refundOrder.setPaymentId(original.getPaymentId());
        refundOrder.setDetailIndex(original.getDetailIndex());
        refundOrder.setMerTradeNo(refundMerTradeNo);
        refundOrder.setTradeNo(command.getTradeNo());
        refundOrder.setTranType(BpcTranType.REFUND.getCode());
        refundOrder.setTxnAmtFen(refundFen);
        refundOrder.setStatus(BpcOrderStatus.REFUNDED.getCode());
        refundOrder.setRespCode(command.getRespCode());
        refundOrder.setRespMsg(command.getRespMsg());
        refundOrder.setProfileId(original.getProfileId());
        refundOrder.setPosNo(original.getPosNo());
        refundOrder.setCreatedAt(LocalDateTime.now());
        refundOrder.setIsDeleted(false);
        bpcOrderRepository.save(refundOrder);

        original.setStatus(BpcOrderStatus.REFUNDED.getCode());
        original.setUpdatedAt(LocalDateTime.now());
        bpcOrderRepository.save(original);

        return new ApplyClientRefundResult()
                .setSuccess(true)
                .setOrderId(refundOrder.getId())
                .setMerTradeNo(original.getMerTradeNo())
                .setRefundMerTradeNo(refundMerTradeNo)
                .setRespMsg(command.getRespMsg());
    }

    @Transactional(readOnly = true)
    public QueryBpcOrderResult queryOrder(QueryBpcOrderCommand command) {
        Long tenantId = TenantUtils.getCurrentTenantId();
        BpcOrder order = findOrder(tenantId, command);
        if (order == null) {
            return new QueryBpcOrderResult().setSuccess(false);
        }
        return new QueryBpcOrderResult()
                .setSuccess(true)
                .setOrderId(order.getId())
                .setMerTradeNo(order.getMerTradeNo())
                .setTradeNo(order.getTradeNo())
                .setStatus(order.getStatus())
                .setPayType(order.getPayType())
                .setTxnAmtFen(order.getTxnAmtFen());
    }

    @Transactional(readOnly = true)
    public List<BpcOrder> getOrdersByPayment(Long paymentId) {
        requirePaymentId(paymentId);
        return bpcOrderRepository.findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
                TenantUtils.getCurrentTenantId(), paymentId);
    }

    private BpcOrder findOrder(Long tenantId, QueryBpcOrderCommand command) {
        if (StringUtils.hasText(command.getMerTradeNo())) {
            return bpcOrderRepository
                    .findByTenantIdAndMerTradeNoAndIsDeletedFalse(tenantId, command.getMerTradeNo())
                    .orElse(null);
        }
        if (StringUtils.hasText(command.getTradeNo())) {
            return bpcOrderRepository
                    .findByTenantIdAndTradeNoAndIsDeletedFalse(tenantId, command.getTradeNo())
                    .orElse(null);
        }
        if (command.getPaymentId() != null) {
            List<BpcOrder> orders = bpcOrderRepository
                    .findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
                            tenantId, command.getPaymentId());
            return orders.isEmpty() ? null : orders.get(0);
        }
        return null;
    }

    private ApplyClientScanResult toScanResult(BpcOrder order, boolean success) {
        String channelCode = null;
        if (StringUtils.hasText(order.getPayType())) {
            channelCode = BpcPayTypeMapper.toPayEnum(order.getPayType());
        }
        return new ApplyClientScanResult()
                .setSuccess(success)
                .setOrderId(order.getId())
                .setMerTradeNo(order.getMerTradeNo())
                .setTradeNo(order.getTradeNo())
                .setPayType(order.getPayType())
                .setChannelCode(channelCode)
                .setRespMsg(order.getRespMsg());
    }

    private BpcContractConfig requireConfig(String fixmedinsCode) {
        if (!StringUtils.hasText(fixmedinsCode)) {
            throw new BusinessException("BPC_CONFIG_MISSING", "fixmedinsCode 不能为空");
        }
        BpcContractConfig config = BpcContractConfigLoader.getByFixmedinsCode(fixmedinsCode.trim());
        if (config == null || !config.isEnabled()) {
            throw new BusinessException("BPC_CONFIG_DISABLED", "院点未开通银联 BPC: " + fixmedinsCode);
        }
        if (!StringUtils.hasText(config.getMid()) || !StringUtils.hasText(config.getTid())) {
            throw new BusinessException("BPC_CONFIG_INVALID", "mid/tid 未配置: " + fixmedinsCode);
        }
        return config;
    }

    private static String resolveProfileId(BpcContractConfig config) {
        return StringUtils.hasText(config.getAcquirerProfile())
                ? config.getAcquirerProfile()
                : "bpc-default";
    }

    private static String resolvePosNo(BpcContractConfig config) {
        if (StringUtils.hasText(config.getPosNo())) {
            return config.getPosNo();
        }
        return config.getTid();
    }

    private static long toFen(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException("BPC_AMOUNT_INVALID", "金额不能为空");
        }
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    private static void requirePaymentId(Long paymentId) {
        if (paymentId == null) {
            throw new BusinessException("BPC_PAYMENT_REQUIRED", "paymentId 不能为空");
        }
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("BPC_AMOUNT_INVALID", "扫码金额必须大于 0");
        }
    }

    private static void requireMerTradeNo(String merTradeNo) {
        if (!StringUtils.hasText(merTradeNo)) {
            throw new BusinessException("BPC_MER_TRADE_NO_REQUIRED", "merTradeNo 不能为空");
        }
    }
}
