/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.servicerequestext.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量初始化体检服务申请执行扩展命令。
 */
@Getter
@Setter
public class InitPeisSrExtCommand {

    /** 服务申请主键列表。 */
    private List<Long> serviceRequestIds;

    /** 租户标识。 */
    private Long tenantId;

    /** 院区标识。 */
    private Long orgId;
}
