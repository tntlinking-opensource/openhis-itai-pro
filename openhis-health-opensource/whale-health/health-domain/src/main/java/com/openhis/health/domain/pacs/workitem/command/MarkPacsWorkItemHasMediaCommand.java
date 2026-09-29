/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 回写检查工单媒体标记命令。
 */
@Getter
@Setter
public class MarkPacsWorkItemHasMediaCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 是否已有影像/关键图。 */
    private Boolean hasMedia;

    /** 操作人主键。 */
    private Long operatorId;
}
