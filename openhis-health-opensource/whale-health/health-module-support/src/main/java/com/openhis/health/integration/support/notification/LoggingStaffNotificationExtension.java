/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.notification;

import com.openhis.health.integration.notification.StaffNotificationExtension;
import com.openhis.health.integration.notification.command.StaffNotificationCommand;
import lombok.extern.slf4j.Slf4j;

/**
 * 医护人员通知默认实现：首期写日志，后续可由消息中心 Module {@code @Primary} 覆盖。
 */
@Slf4j
public final class LoggingStaffNotificationExtension implements StaffNotificationExtension {

    @Override
    public void notify(StaffNotificationCommand command) {
        log.info(
                "医护人员通知 type={} caseRecordId={} recipientPractitionerId={} title={} message={}",
                command.getNotifyTypeCode(),
                command.getCaseRecordId(),
                command.getRecipientPractitionerId(),
                command.getTitle(),
                command.getMessage());
    }
}
