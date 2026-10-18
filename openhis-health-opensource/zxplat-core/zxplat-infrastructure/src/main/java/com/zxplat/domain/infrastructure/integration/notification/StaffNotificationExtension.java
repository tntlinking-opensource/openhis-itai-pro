/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.notification;

import com.zxplat.domain.infrastructure.integration.notification.ExtensionIn.StaffNotificationExtensionIn;

/**
 * 向医护人员发送通知（消息中心 Module 提供能力；首期默认写日志）。
 */
public interface StaffNotificationExtension {

    /**
     * 医护通知扩展是否启用（默认 true，NoOp 写日志）。
     *
     * @return false 时完全跳过通知
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 发送医护人员通知。
     *
     * @param command 接收人、类型与消息内容
     */
    default void notify(StaffNotificationExtensionIn command) {
    }
}
