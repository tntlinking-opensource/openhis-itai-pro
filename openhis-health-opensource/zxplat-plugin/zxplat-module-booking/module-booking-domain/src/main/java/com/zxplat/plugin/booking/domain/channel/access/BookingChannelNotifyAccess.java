/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.channel.access;

import com.zxplat.plugin.booking.domain.channel.command.BookingChannelNotifyCommand;

/**
 * 预约渠道通知出站端口（实现在 bridge；消息中心未集成前为 no-op 占位）。
 *
 * <p>调用方：门诊/床位 workflow 编排器在写操作成功后触发；命令仅含预约主键、渠道码与事件类型。</p>
 */
public interface BookingChannelNotifyAccess {

    /**
     * 向渠道发送预约事件通知。
     *
     * @param command 预约主键、渠道码、模板与事件类型
     */
    void notify(BookingChannelNotifyCommand command);
}
