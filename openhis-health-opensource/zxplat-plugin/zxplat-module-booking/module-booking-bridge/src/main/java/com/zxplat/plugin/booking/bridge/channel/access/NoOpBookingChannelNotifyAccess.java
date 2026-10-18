/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.channel.access;

import com.zxplat.plugin.booking.domain.channel.access.BookingChannelNotifyAccess;
import com.zxplat.plugin.booking.domain.channel.command.BookingChannelNotifyCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 渠道通知占位实现：记录 debug 日志，不调用外部消息中心。
 */
@Slf4j
@Service
public class NoOpBookingChannelNotifyAccess implements BookingChannelNotifyAccess {

    @Override
    public void notify(BookingChannelNotifyCommand command) {
        if (command == null) {
            return;
        }
        log.debug(
                "Booking channel notify stub (message center not integrated): appointmentId={}, channel={}, event={}",
                command.getAppointmentId(),
                command.getChannelCode(),
                command.getEventType());
    }
}
