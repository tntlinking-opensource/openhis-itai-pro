/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.servicerequestext.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新体检项目外发标志命令。
 */
@Getter
@Setter
public class UpdatePeisSrDispatchCommand {

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 模块（LIS / PACS）。 */
    private String moduleEnum;

    /** 外发状态。 */
    private String dispatchEnum;
}
