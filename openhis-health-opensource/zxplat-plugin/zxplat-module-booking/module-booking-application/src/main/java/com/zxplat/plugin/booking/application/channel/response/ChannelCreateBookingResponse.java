/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.channel.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道创建预约响应（10.2）。
 */
@Getter
@Setter
public class ChannelCreateBookingResponse {

    /** 预约主键。 */
    private Long appointmentId;

    /** 预约业务流水号。 */
    private String busNo;
}
