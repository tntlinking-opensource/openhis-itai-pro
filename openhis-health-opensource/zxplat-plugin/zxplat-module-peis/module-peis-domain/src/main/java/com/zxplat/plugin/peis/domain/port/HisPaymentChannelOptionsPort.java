/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.port;

import java.util.List;

import com.zxplat.framework.core.application.dto.OptionDto;

/**
 * PEIS → HIS 支付渠道下拉端口。
 */
public interface HisPaymentChannelOptionsPort {

    /**
     * 加载用户可选支付渠道。
     *
     * @return 下拉项（按 sort 升序）
     */
    List<OptionDto> loadUserSelectableOptions();
}
