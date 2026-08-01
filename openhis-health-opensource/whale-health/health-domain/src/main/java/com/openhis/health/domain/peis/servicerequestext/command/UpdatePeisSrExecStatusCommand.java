/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.servicerequestext.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新体检项目执行状态命令。
 */
@Getter
@Setter
public class UpdatePeisSrExecStatusCommand {

    /** 服务申请主键。 */
    private Long serviceRequestId;

    /** 就诊主键（归属校验）。 */
    private Long encounterId;

    /** 目标执行状态。 */
    private String execStatusEnum;

    /** 弃检/延检原因。 */
    private String reason;
}
