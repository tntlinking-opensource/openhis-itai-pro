/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.domain.event;

import java.time.LocalDateTime;

/**
 * 事件接口
 * 参考 ABP.io 的 IEvent 接口
 *
 * <p>所有领域事件和应用事件都应实现此接口</p>
 *
 * @author OpenHIS Contributors
 */
public interface IEvent {

    /**
     * 获取事件ID（唯一标识）
     *
     * @return 事件ID
     */
    String getEventId();

    /**
     * 设置事件ID
     *
     * @param eventId 事件ID
     */
    void setEventId(String eventId);

    /**
     * 获取事件发生时间
     *
     * @return 事件发生时间
     */
    LocalDateTime getEventTime();

    /**
     * 设置事件发生时间
     *
     * @param eventTime 事件发生时间
     */
    void setEventTime(LocalDateTime eventTime);
}
