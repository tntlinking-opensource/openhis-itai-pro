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

package com.openhis.health.application.common.assembler.financial;

import com.openhis.health.application.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.domain.share.enums.PaymentChannel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付渠道 code → 展示名装配（预交金流水、收银明细等）。
 */
@Component
public class PaymentChannelDisplayAssembler {

    private final TerminologyOptionLoader terminologyOptionLoader;

    public PaymentChannelDisplayAssembler(TerminologyOptionLoader terminologyOptionLoader) {
        this.terminologyOptionLoader = terminologyOptionLoader;
    }

    /**
     * 解析支付渠道展示名：术语码表命中优先，否则回落 {@link PaymentChannel#getDisplay()}，仍无则返回原 code。
     *
     * @param payChannelCode 持久化 {@code pay_enum}
     * @param terminologyCodeSystemName 术语编码体系名；空白时仅用枚举展示名
     * @return 展示名；入参空白时为 {@code null}
     */
    public String resolvePayChannelDisplay(String payChannelCode, String terminologyCodeSystemName) {
        if (StringUtils.isBlank(payChannelCode)) {
            return null;
        }
        String code = payChannelCode.trim();
        Map<String, String> labelByCode = this.buildLabelIndex(terminologyCodeSystemName);
        String label = labelByCode.get(code);
        if (StringUtils.isNotBlank(label)) {
            return label;
        }
        return code;
    }

    /**
     * 构建码 → 展示名索引（枚举内置 + 可选术语表）。
     *
     * @param terminologyCodeSystemName 术语体系名
     * @return 非 null 映射
     */
    private Map<String, String> buildLabelIndex(String terminologyCodeSystemName) {
        Map<String, String> labelByCode = new HashMap<>(8);
        for (PaymentChannel channel : PaymentChannel.values()) {
            if (channel == null) {
                continue;
            }
            labelByCode.put(channel.getCode(), channel.getDisplay());
        }
        if (StringUtils.isNotBlank(terminologyCodeSystemName)) {
            Map<String, String> terminologyLabels =
                this.terminologyOptionLoader.buildLabelMap(terminologyCodeSystemName.trim());
            labelByCode.putAll(terminologyLabels);
        }
        return labelByCode;
    }
}
