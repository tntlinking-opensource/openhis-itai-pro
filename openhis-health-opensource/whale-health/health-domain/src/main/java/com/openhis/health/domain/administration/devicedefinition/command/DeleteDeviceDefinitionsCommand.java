/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.devicedefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除器材（耗材）定义命令（逻辑删除）。
 */
@Getter
@Setter
public class DeleteDeviceDefinitionsCommand extends BatchCommand<DeleteDeviceDefinitionsCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 批量命令明细项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 待删除目录标识。 */
        private Long id;
    }
}
