/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.notification;

import com.openhis.health.integration.notification.command.StaffNotificationCommand;

/**
 * 向医护人员发送通知（消息中心 Module 提供能力；首期默认写日志）。
 */
public interface StaffNotificationExtension {

    default boolean isEnabled() {
        return true;
    }

    default void notify(StaffNotificationCommand command) {
    }
}
