/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 未发放停发命令。
 */
@Getter
@Setter
public class StopUndispensedDeviceDispenseCommand {

    /** 标识列表。 */
    private List<Long> idList;

    /** notperformed原因枚举编码。 */
    private String notPerformedReasonEnum;

    /** 操作人主键。 */
    private Long operatorId;
}
