/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.labdevice.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量启用检验设备命令。
 */
@Getter
@Setter
public class ActivateLabDevicesCommand extends BatchCommand<ActivateLabDevicesCommand.ActivateLabDeviceItemCommand> {

    /**
     * 启用项。
     */
    @Getter
    @Setter
    public static class ActivateLabDeviceItemCommand {

        /** 设备主键。 */
        private Long id;
    }
}
