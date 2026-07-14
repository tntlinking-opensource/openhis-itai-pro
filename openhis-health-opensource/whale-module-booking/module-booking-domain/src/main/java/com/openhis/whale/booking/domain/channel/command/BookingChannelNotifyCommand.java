/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.channel.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 渠道通知入参（domain 契约；不含消息中心 HTTP 细节）。
 */
@Getter
@Setter
public class BookingChannelNotifyCommand {

    private Long appointmentId;
    private String channelCode;
    private String notifyTemplateCode;
    /** 业务事件，如 BOOKED / CANCELLED / TRANSFERRED */
    private String eventType;
}
