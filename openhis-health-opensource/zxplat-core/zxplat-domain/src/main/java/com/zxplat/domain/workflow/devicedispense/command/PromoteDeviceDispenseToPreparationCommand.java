/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 草稿批量推进为待发命令。
 */
@Getter
@Setter
/**
 * PromoteDeviceDispenseToPreparationCommand。
 */
public class PromoteDeviceDispenseToPreparationCommand {

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
