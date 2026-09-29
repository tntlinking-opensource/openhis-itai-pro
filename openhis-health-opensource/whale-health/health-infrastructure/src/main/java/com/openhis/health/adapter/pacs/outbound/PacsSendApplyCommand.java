/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.outbound;

import lombok.Getter;
import lombok.Setter;

/**
 * 外发检查申请命令（sendApply 对标）。
 */
@Getter
@Setter
public class PacsSendApplyCommand {

    /** PacsWorkItem 主键。 */
    private Long workItemId;

    /** ActivityDefinition 主键（对照用）。 */
    private Long activityId;

    /** 操作人。 */
    private Long operatorId;
}
