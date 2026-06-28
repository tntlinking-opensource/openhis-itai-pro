/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.notification.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医护人员通知命令（业务域无关字段）。
 */
@Getter
@Setter
public class StaffNotificationCommand {

    private Long caseRecordId;
    private Long tenantId;
    private Long recipientPractitionerId;
    private String notifyTypeCode;
    private String title;
    private String message;
}
