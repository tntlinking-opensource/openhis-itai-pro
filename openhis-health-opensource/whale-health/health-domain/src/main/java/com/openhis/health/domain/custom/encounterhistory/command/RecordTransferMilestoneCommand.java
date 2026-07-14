/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.encounterhistory.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 记录转科里程碑命令。
 */
@Getter
@Setter
public class RecordTransferMilestoneCommand {
    /**
     * 就诊标识。
     */
    private Long encounterId;
    /**
     * 关联申请单主键（与 requestTableName 组成业务引用对）。
     */
    private Long requestId;
    /**
     * 关联申请单所在表名。
     */
    private String requestTableName;
    /**
     * 转出机构标识。
     */
    private Long originalOrganizationId;
    /**
     * 转入机构标识。
     */
    private Long targetOrganizationId;
    /**
     * 转出位置标识（可空）。
     */
    private Long originalLocationId;
    /**
     * 转入位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
