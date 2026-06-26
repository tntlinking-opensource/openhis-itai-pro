/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.validator;

import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.module.common.assembler.financial.PaymentChannelOptionProvider;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 支付渠道入站门控：枚举 + 477 能力开关；落库路径禁止 {@link PaymentChannel#SCAN_PAY}。
 */
@Component
public class PaymentChannelGate {

    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    public PaymentChannelGate(PaymentChannelOptionProvider paymentChannelOptionProvider) {
        this.paymentChannelOptionProvider = paymentChannelOptionProvider;
    }

    /**
     * 校验并归一化用户可选支付渠道 code（477 active + 枚举 userSelectable）。
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

    private static String requireKnownPayChannelCode(String payChannelCode, String errorCode, String message) {
        if (StringUtils.isBlank(payChannelCode)) {
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
