/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 未发放回退为草稿命令。
 */
@Getter
@Setter
/**
 * RollbackUndispensedDeviceDispenseCommand。
 */
public class RollbackUndispensedDeviceDispenseCommand {

    /** 标识列表。 */
    /**
     * idList。
     */
    private List<Long> idList;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
