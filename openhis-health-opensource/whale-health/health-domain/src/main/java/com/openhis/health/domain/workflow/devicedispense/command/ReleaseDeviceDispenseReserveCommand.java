/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 释放耗材发放预减占用命令。
 */
@Getter
@Setter
/**
 * ReleaseDeviceDispenseReserveCommand。
 */
public class ReleaseDeviceDispenseReserveCommand {

    /** 发放标识列表（逗号分隔）或请求维度键。 */
    /**
     * idListOrRequestKey。
     */
    private String idListOrRequestKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
