/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存渠道配置响应（10.1）。
 */
@Getter
@Setter
public class SaveChannelConfigResponse {

    /** 配置主键。 */
    private Long id;
}
