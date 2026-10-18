/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.domain.share.enums.PaymentChannel;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付渠道 code → 展示名装配（预交金流水、收银明细等）。
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelDisplayAssembler {

    /** 术语下拉加载器：补充码表展示名。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 解析支付渠道展示名：术语码表命中优先，否则回落 {@link PaymentChannel#getDisplay()}，仍无则返回原 code。
     *
     * @param payChannelCode 持久化 {@code pay_enum}
     * @param terminologyCodeSystemName 术语编码体系名；空白时仅用枚举展示名
     * @return 展示名；入参空白时为 {@code null}
     */
    public String resolvePayChannelDisplay(String payChannelCode, String terminologyCodeSystemName) {
        if (StringUtils.isEmpty(payChannelCode)) {
            return null;
        }
        String code = payChannelCode.trim();
        Map<String, String> labelByCode = this.buildLabelIndex(terminologyCodeSystemName);
        String label = labelByCode.get(code);
        if (StringUtils.isNotEmpty(label)) {
            return label;
        }
        return code;
    }

    /**
     * 日结自付渠道归类：银联被扫并入微信，收银台占位并入现金，避免金额进总收入却不出现在四格。
     *
     * @param payEnum 持久化 {@code pay_enum}
     * @return 日结展示名；入参空白时为 {@code null}
     */
    public String resolveDailySettlementSelfPayCategory(String payEnum) {
        if (StringUtils.isEmpty(payEnum)) {
            return null;
        }
        PaymentChannel channel = PaymentChannel.fromCode(payEnum.trim());
        // 折扣旁证不进自付渠道四格（BR-OP-DCL-031）
        if (PaymentChannel.DISCOUNT.equals(channel)) {
            return null;
        }
        // 预交金冲抵不进自付渠道四格
        if (PaymentChannel.DEPOSIT_OFFSET.equals(channel)) {
            return null;
        }
        if (PaymentChannel.SCAN_PAY.equals(channel)) {
            return PaymentChannel.WECHAT.getDisplay();
        }
        if (PaymentChannel.CASHIER.equals(channel)) {
            return PaymentChannel.CASH.getDisplay();
        }
        return this.resolvePayChannelDisplay(payEnum, null);
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
        if (StringUtils.isNotEmpty(terminologyCodeSystemName)) {
            Map<String, String> terminologyLabels =
                this.terminologyOptionLoader.buildLabelMap(terminologyCodeSystemName.trim());
            labelByCode.putAll(terminologyLabels);
        }
        return labelByCode;
    }
}
