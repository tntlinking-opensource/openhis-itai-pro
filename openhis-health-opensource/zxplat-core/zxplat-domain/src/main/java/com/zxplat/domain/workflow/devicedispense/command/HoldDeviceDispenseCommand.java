/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 发放单挂起命令。
 */
@Getter
@Setter
public class HoldDeviceDispenseCommand {

    /** 标识列表。 */
    private List<Long> idList;

    /** 原因。 */
    private String reason;

    /** 操作人主键。 */
    private Long operatorId;
}
