/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.orchestration.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 体检项目加/减项编排命令。
 */
@Getter
@Setter
public class AdjustPeServiceRequestsCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 加项 ActivityDefinition 主键列表。 */
    private List<Long> addActivityIds;

    /** 减项服务申请主键列表。 */
    private List<Long> removeServiceRequestIds;

    /** 操作人 practitioner 主键。 */
    private Long operatorId;
}
