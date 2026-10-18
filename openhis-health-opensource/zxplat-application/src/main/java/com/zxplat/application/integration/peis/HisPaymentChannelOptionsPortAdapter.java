/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.integration.peis;

import java.util.List;

import org.springframework.stereotype.Component;

import com.zxplat.application.shared.financial.PaymentChannelOptionProvider;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.plugin.peis.domain.port.HisPaymentChannelOptionsPort;

import lombok.RequiredArgsConstructor;

/**
 * HIS 适配：支付渠道下拉 → {@link HisPaymentChannelOptionsPort}。
 */
@Component
@RequiredArgsConstructor
public class HisPaymentChannelOptionsPortAdapter implements HisPaymentChannelOptionsPort {

    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    @Override
    public List<OptionDto> loadUserSelectableOptions() {
        return paymentChannelOptionProvider.loadUserSelectableOptions();
    }
}
