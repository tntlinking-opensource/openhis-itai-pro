/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除检验设备命令。
 */
@Getter
@Setter
public class DeleteLabDeviceCommand extends BatchCommand<DeleteLabDeviceCommand.DeleteLabDeviceItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 删除项。
     */
    @Getter
    @Setter
    public static class DeleteLabDeviceItemCommand {

        /** 设备主键。 */
        private Long id;
    }
}
