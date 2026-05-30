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

package com.openhis.health.application.common.validator;

import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 支付渠道入站门控：统一校验 {@code payChannelCode} 为 {@link PaymentChannel} 用户可选 code。
 */
@Component
public class PaymentChannelGate {

    /**
     * 校验并归一化用户可选支付渠道 code（trim 后须命中 {@link PaymentChannel#isUserSelectable()}）。
     *
     * @param payChannelCode 请求中的支付渠道 code
     * @param errorCode      业务错误码
     * @param message        非法时透出文案
     * @return 归一化后的 code（与 {@link PaymentChannel#getCode()} 一致）
     * @throws BusinessException 为空或非法渠道时
     */
    public String requireUserSelectablePayChannelCode(
        String payChannelCode,
        String errorCode,
        String message) {
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
