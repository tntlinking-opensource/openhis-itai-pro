/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.notification.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 医护人员通知命令（业务域无关字段）。
 *
 * <p>职责：由各业务 AppService 触发，消息中心 adapter 负责投递渠道（站内信/短信等）。</p>
 */
@Getter
@Setter
public class StaffNotificationExtensionIn {

    /** 业务病例/工单主键（幂等与追溯）。 */
    private Long caseRecordId;

    /** 租户主键。 */
    private Long tenantId;

    /** 接收人医务人员主键。 */
    private Long recipientPractitionerId;

    /** 通知类型 code（非展示 label）。 */
    private String notifyTypeCode;

    /** 通知标题。 */
    private String title;

    /** 通知正文。 */
    private String message;
}
