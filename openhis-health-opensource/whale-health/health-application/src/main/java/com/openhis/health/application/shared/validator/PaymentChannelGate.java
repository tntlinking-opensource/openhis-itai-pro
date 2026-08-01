/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.validator;

import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 支付渠道入站门控：枚举 + 477 能力开关；落库路径禁止 {@link PaymentChannel#SCAN_PAY}。
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelGate {

    /** 477 能力开关与用户可选支付渠道白名单提供器。 */
    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    /**
     * 校验并归一化用户可选支付渠道 code（477 active + 枚举 userSelectable）。
     *
     * @param payChannelCode 支付渠道 code
     * @param errorCode      失败业务码
     * @param message        失败提示
     * @return trim 后的可选渠道 code
     * @throws BusinessException 非法或不可选渠道
     */
    public String requireUserSelectablePayChannelCode(
        String payChannelCode,
        String errorCode,
        String message) {
        String normalized = requireKnownPayChannelCode(payChannelCode, errorCode, message);
        if (!paymentChannelOptionProvider.isAllowedUserSelectableCode(normalized)) {
            throw new BusinessException(errorCode, message);
        }
        return normalized;
    }

    /**
     * 校验可落库 pay_enum 的渠道（拒绝 {@link PaymentChannel#SCAN_PAY}）。
     *
     * @param payChannelCode 支付渠道 code
     * @param errorCode      失败业务码
     * @param message        失败提示
     * @return trim 后的可落库渠道 code
     * @throws BusinessException 非法、不可选或 UI-only 渠道
     */
    public String requirePersistablePayChannelCode(
        String payChannelCode,
        String errorCode,
        String message) {
        String normalized = requireUserSelectablePayChannelCode(payChannelCode, errorCode, message);
        PaymentChannel channel = EnumUtil.fromCode(PaymentChannel.class, normalized);
        if (channel != null && channel.isUiOnly()) {
            throw new BusinessException(errorCode, "扫码支付须先完成银联收单后再结算");
        }
        return normalized;
    }

    /**
     * 校验已落库 pay_enum（含医保统筹/个账等非用户可选渠道；仍拒绝 {@link PaymentChannel#SCAN_PAY}）。
     *
     * <p>用于退费原路返回：原支付明细可能含服务端写入的 {@link PaymentChannel#INSURANCE_FUND}/
     * {@link PaymentChannel#INSURANCE_ACCT}，不能按用户可选门控拒绝。</p>
     *
     * @param payChannelCode 支付渠道 code
     * @param errorCode      失败业务码
     * @param message        失败提示
     * @return trim 后的已落库渠道 code
     * @throws BusinessException 非法或 UI-only 渠道
     */
    public String requireKnownPersistedPayChannelCode(
        String payChannelCode,
        String errorCode,
        String message) {
        if (StringUtils.isEmpty(payChannelCode)) {
            throw new BusinessException(errorCode, message);
        }
        String trimmed = payChannelCode.trim();
        PaymentChannel channel = EnumUtil.fromCode(PaymentChannel.class, trimmed);
        if (channel == null) {
            throw new BusinessException(errorCode, message);
        }
        if (channel.isUiOnly()) {
            throw new BusinessException(errorCode, "扫码支付须先完成银联收单后再结算");
        }
        return trimmed;
    }

    /**
     * 支付渠道 Gate：非空、可解析为 {@link PaymentChannel} 且 userSelectable。
     *
     * @param payChannelCode 渠道码
     * @param errorCode      失败业务码
     * @param message        失败提示
     * @return trim 后的渠道码
     * @throws BusinessException 非法或不可选渠道
     */
    private static String requireKnownPayChannelCode(String payChannelCode, String errorCode, String message) {
        if (StringUtils.isEmpty(payChannelCode)) {
            throw new BusinessException(errorCode, message);
        }
        String trimmed = payChannelCode.trim();
        PaymentChannel channel = EnumUtil.fromCode(PaymentChannel.class, trimmed);
        if (channel == null) {
            throw new BusinessException(errorCode, message);
        }
        if (!channel.isUserSelectable()) {
            throw new BusinessException(errorCode, message);
        }
        return trimmed;
    }
}
