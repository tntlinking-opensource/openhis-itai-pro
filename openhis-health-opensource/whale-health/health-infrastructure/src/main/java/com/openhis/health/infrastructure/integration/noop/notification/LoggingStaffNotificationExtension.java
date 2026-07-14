/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.noop.notification;

import com.openhis.health.infrastructure.integration.notification.StaffNotificationExtension;
import com.openhis.health.infrastructure.integration.notification.ExtensionIn.StaffNotificationExtensionIn;
import lombok.extern.slf4j.Slf4j;

/**
 * 医护人员通知默认实现：首期写日志，后续可由消息中心 Module {@code @Primary} 覆盖。
 */
@Slf4j
public final class LoggingStaffNotificationExtension implements StaffNotificationExtension {

    /**
     * 首期占位：将通知内容写入应用日志，供联调与审计追踪；生产可替换为消息中心实现。
     *
     * @param command 含通知类型、病案/收件人、标题与正文
     */
    @Override
    public void notify(StaffNotificationExtensionIn command) {
        // 占位实现：结构化日志供联调与审计，不投递真实消息通道
        log.info(
                "医护人员通知 type={} caseRecordId={} recipientPractitionerId={} title={} message={}",
                command.getNotifyTypeCode(),
                command.getCaseRecordId(),
                command.getRecipientPractitionerId(),
                command.getTitle(),
                command.getMessage());
    }
}
