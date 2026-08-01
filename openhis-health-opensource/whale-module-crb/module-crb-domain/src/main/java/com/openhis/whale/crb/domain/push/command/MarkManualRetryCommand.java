/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.push.command;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 监控页人工重试命令（DM-CRB-PUSH-001 §6.1.3）。
 */
@Getter
@Setter
@Accessors(chain = true)
public class MarkManualRetryCommand {

    /** 租户 id。 */
    private Long tenantId;

    /** 推送任务主键。 */
    private Long taskId;
}
