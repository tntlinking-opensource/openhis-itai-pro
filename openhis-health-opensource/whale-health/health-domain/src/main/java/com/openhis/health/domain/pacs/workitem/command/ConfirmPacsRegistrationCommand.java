/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.pacs.workitem.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 确认检查登记命令。
 */
@Getter
@Setter
public class ConfirmPacsRegistrationCommand {

    /** 工单主键。 */
    private Long workItemId;

    /** 检查号；空则按配置生成。 */
    private String accessionNumber;

    /** 操作人主键。 */
    private Long operatorId;
}
