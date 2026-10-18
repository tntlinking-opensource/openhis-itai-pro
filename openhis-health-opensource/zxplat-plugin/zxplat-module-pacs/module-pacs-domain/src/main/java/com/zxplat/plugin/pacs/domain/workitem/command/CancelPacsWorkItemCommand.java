/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 整单作废检查工单命令。
 */
@Getter
@Setter
public class CancelPacsWorkItemCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 作废原因。 */
    private String reason;

    /** 操作人主键。 */
    private Long operatorId;
}
