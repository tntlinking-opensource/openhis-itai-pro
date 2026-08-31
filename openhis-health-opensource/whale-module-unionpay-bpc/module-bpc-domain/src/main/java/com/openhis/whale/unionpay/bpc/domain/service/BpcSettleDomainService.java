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
import com.openhis.whale.unionpay.bpc.domain.scan.command.AbandonPendingScanCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.ApplyClientScanCommand;
import com.openhis.whale.unionpay.bpc.domain.scan.command.PrepareConsumerScanCommand;
import com.openhis.whale.unionpay.bpc.domain.query.command.QueryBpcOrderCommand;
import com.openhis.whale.unionpay.bpc.domain.refund.result.ApplyClientRefundResult;
import com.openhis.whale.unionpay.bpc.domain.scan.result.AbandonPendingScanResult;
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
        // Gate：同支付草稿已有未退货 SUCCESS 消费单时禁止再发 C，避免银联半成功后重复扣款。
        this.assertNoOpenSuccessConsumerScan(command.getPaymentId());
        // Cmd：再 prepare 前关闭同支付下悬挂 PENDING，避免重试叠单。
        this.abandonPendingConsumerScans(
            command.getPaymentId(), null, "RP", "replaced by new prepare");
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
            // Pre：幂等补结算时下单分纠偏，并补写 POS 实付。
            this.retainOrderAmountIgnoringBankDiscount(order, command.getTxnAmtFen(), command.getAmount());
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
        // Cmd：下单分保持 txn_amt_fen；POS 实付写入 paid_amt_fen（可小于下单）。
        this.retainOrderAmountIgnoringBankDiscount(order, command.getTxnAmtFen(), command.getAmount());
        this.syncOrderPaymentId(order, command);
        order.setUpdatedAt(LocalDateTime.now());
        bpcOrderRepository.save(order);
        // Cmd：成功后关闭同支付其余 PENDING，收口重试孤儿单。
        this.abandonPendingConsumerScans(
            order.getPaymentId(), order.getMerTradeNo(), "SB", "sibling succeeded");
        return toScanResult(order, true);
    }

    /**
     * 关闭未确认的被扫 PENDING（取消/超时/草稿作废）。
     *
     * @param command paymentId 与 merTradeNo 至少填一个
     * @return 关闭条数
     */
    @Transactional
    public AbandonPendingScanResult abandonPendingScan(AbandonPendingScanCommand command) {
        if (command == null) {
            throw new BusinessException("BPC_ABANDON_INVALID", "abandon 参数不能为空");
        }
        String reason = StringUtils.hasText(command.getReason())
            ? command.getReason().trim()
            : "pending abandoned";
        String respCode = StringUtils.hasText(command.getRespCode())
            ? trimRespCode(command.getRespCode())
            : "AB";
        int count;
        if (StringUtils.hasText(command.getMerTradeNo())) {
            count = this.abandonPendingByMerTradeNo(command.getMerTradeNo().trim(), respCode, reason);
        } else if (command.getPaymentId() != null) {
            count = this.abandonPendingConsumerScans(
                command.getPaymentId(), null, respCode, reason);
        } else {
            throw new BusinessException("BPC_ABANDON_INVALID", "paymentId 与 merTradeNo 不能同时为空");
        }
        return new AbandonPendingScanResult().setAbandonedCount(count);
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

    /**
     * 下单分与 POS 实付分账：{@code txn_amt_fen} 保留院内下单；{@code paid_amt_fen} 记回传实付。
     *
     * <p>实付可小于下单（营销优惠或渠道拆分）；禁止用实付覆盖下单，也禁止丢掉实付。</p>
     *
     * @param order             预下单/已成功 BPC 订单
     * @param returnedTxnAmtFen Win 回传实付分；null 表示不改 paid
     * @param orderAmountYuan   院内扫码行金额（元）；非空时作为权威下单分
     * @throws BusinessException 回传分额无效或大于院内下单
     */
    private void retainOrderAmountIgnoringBankDiscount(
        BpcOrder order,
        Long returnedTxnAmtFen,
        BigDecimal orderAmountYuan) {
        long orderFen;
        if (orderAmountYuan != null) {
            orderFen = toFen(orderAmountYuan);
            order.setTxnAmtFen(orderFen);
        } else {
            Long preparedFen = order.getTxnAmtFen();
            if (preparedFen == null || preparedFen <= 0L) {
                throw new BusinessException("BPC_AMOUNT_INVALID", "预下单金额无效");
            }
            orderFen = preparedFen.longValue();
        }
        if (returnedTxnAmtFen == null) {
            return;
        }
        if (returnedTxnAmtFen <= 0L) {
            throw new BusinessException("BPC_AMOUNT_INVALID", "银联回传金额无效");
        }
        if (returnedTxnAmtFen > orderFen) {
            throw new BusinessException(
                "BPC_AMOUNT_MISMATCH",
                "银联回传金额大于预下单金额");
        }
        order.setPaidAmtFen(returnedTxnAmtFen);
    }

    /**
     * 银联商户应退分：有 POS 实付用实付，否则用下单分。
     */
    private static long resolveMerchantAmtFen(BpcOrder order) {
        if (order.getPaidAmtFen() != null && order.getPaidAmtFen() > 0L) {
            return order.getPaidAmtFen();
        }
        Long orderFen = order.getTxnAmtFen();
        return orderFen == null ? 0L : orderFen.longValue();
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

        // Gate：退货金额须等于银联商户实收分（有 paid_amt_fen 用实付，禁止按下单分多退）
        long refundFen = toFen(command.getRefundAmount());
        long originalFen = resolveMerchantAmtFen(original);
        if (originalFen > 0 && refundFen != originalFen) {
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
                .setTxnAmtFen(order.getTxnAmtFen())
                .setPaidAmtFen(order.getPaidAmtFen());
    }

    @Transactional(readOnly = true)
    public List<BpcOrder> getOrdersByPayment(Long paymentId) {
        requirePaymentId(paymentId);
        return bpcOrderRepository.findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
                TenantUtils.getCurrentTenantId(), paymentId);
    }

    /**
     * 同支付草稿是否已存在未退货的成功消费单（半成功补结算场景禁止再 prepare）。
     *
     * @param paymentId 支付草稿/支付单主键
     * @return true 表示存在可补结算的 SUCCESS 消费单
     */
    @Transactional(readOnly = true)
    public boolean hasOpenSuccessConsumerScanOrder(Long paymentId) {
        requirePaymentId(paymentId);
        List<BpcOrder> orders = this.getOrdersByPayment(paymentId);
        for (BpcOrder order : orders) {
            if (order == null) {
                continue;
            }
            if (!BpcOrderStatus.SUCCESS.getCode().equals(order.getStatus())) {
                continue;
            }
            if (!BpcTranType.CONSUMER_SCAN.getCode().equals(order.getTranType())) {
                continue;
            }
            if (!StringUtils.hasText(order.getMerTradeNo()) || !StringUtils.hasText(order.getTradeNo())) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Gate：已有 SUCCESS 消费单时拒绝新 C，提示补结算或银联退货后再收。
     *
     * @param paymentId 支付草稿主键
     */
    private void assertNoOpenSuccessConsumerScan(Long paymentId) {
        if (!this.hasOpenSuccessConsumerScanOrder(paymentId)) {
            return;
        }
        throw new BusinessException(
            "BPC_SCAN_ALREADY_SUCCESS",
            "该支付草稿银联已扣款成功，请补结算落账或先银联退货，禁止重复扫码");
    }

    /**
     * 将同支付下 PENDING 消费单置 FAILED（可排除指定 merTradeNo）。
     *
     * @param paymentId          支付主键
     * @param excludeMerTradeNo  保留不关闭的商户订单号；null 表示全关
     * @param respCode           落库响应码
     * @param reason             落库原因
     * @return 关闭条数
     */
    private int abandonPendingConsumerScans(
        Long paymentId,
        String excludeMerTradeNo,
        String respCode,
        String reason) {
        requirePaymentId(paymentId);
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<BpcOrder> orders = tenantId != null
            ? bpcOrderRepository.findByTenantIdAndPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(
                tenantId, paymentId)
            : bpcOrderRepository.findByPaymentIdAndIsDeletedFalseOrderByCreatedAtDesc(paymentId);
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        String msg = truncateRespMsg(reason);
        String code = trimRespCode(respCode);
        for (BpcOrder order : orders) {
            if (order == null || !isAbandonablePendingConsumerScan(order)) {
                continue;
            }
            if (StringUtils.hasText(excludeMerTradeNo)
                && excludeMerTradeNo.equals(order.getMerTradeNo())) {
                continue;
            }
            order.setStatus(BpcOrderStatus.FAILED.getCode());
            order.setRespCode(code);
            order.setRespMsg(msg);
            order.setUpdatedAt(now);
            bpcOrderRepository.save(order);
            count++;
        }
        return count;
    }

    /**
     * 按商户订单号关闭单条 PENDING。
     *
     * @param merTradeNo 商户订单号
     * @param respCode   响应码
     * @param reason     原因
     * @return 关闭条数（0 或 1）
     */
    private int abandonPendingByMerTradeNo(String merTradeNo, String respCode, String reason) {
        requireMerTradeNo(merTradeNo);
        Long tenantId = TenantUtils.getCurrentTenantId();
        BpcOrder order = bpcOrderRepository
            .findByTenantIdAndMerTradeNoAndIsDeletedFalse(tenantId, merTradeNo)
            .orElse(null);
        if (order == null || !isAbandonablePendingConsumerScan(order)) {
            return 0;
        }
        order.setStatus(BpcOrderStatus.FAILED.getCode());
        order.setRespCode(trimRespCode(respCode));
        order.setRespMsg(truncateRespMsg(reason));
        order.setUpdatedAt(LocalDateTime.now());
        bpcOrderRepository.save(order);
        return 1;
    }

    private static boolean isAbandonablePendingConsumerScan(BpcOrder order) {
        if (!BpcOrderStatus.PENDING.getCode().equals(order.getStatus())) {
            return false;
        }
        return BpcTranType.CONSUMER_SCAN.getCode().equals(order.getTranType());
    }

    private static String trimRespCode(String respCode) {
        if (!StringUtils.hasText(respCode)) {
            return "AB";
        }
        String trimmed = respCode.trim();
        return trimmed.length() <= 8 ? trimmed : trimmed.substring(0, 8);
    }

    private static String truncateRespMsg(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "pending abandoned";
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
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
