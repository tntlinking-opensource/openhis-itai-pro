/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.encounterhistory.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 记录离院里程碑命令。
 */
@Getter
@Setter
public class RecordDischargeMilestoneCommand {
    /**
     * 就诊标识。
     */
    private Long encounterId;
    /**
     * 关联申请单主键。
     */
    private Long requestId;
    /**
     * 关联申请单所在表名。
     */
    private String requestTableName;
    /**
     * 离院方式编码。
     */
    private String outWayCode;
    /**
     * 离院前机构标识（可空）。
     */
    private Long originalOrganizationId;
    /**
     * 离院后去向机构标识（可空）。
     */
    private Long targetOrganizationId;
    /**
     * 离院前位置标识（可空）。
     */
    private Long originalLocationId;
    /**
     * 离院后位置标识（可空）。
     */
    private Long targetLocationId;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
