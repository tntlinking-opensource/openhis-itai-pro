/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.labdevice.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量停用检验设备命令。
 */
@Getter
@Setter
public class DeactivateLabDevicesCommand extends BatchCommand<DeactivateLabDevicesCommand.DeactivateLabDeviceItemCommand> {

    /**
     * 停用项。
     */
    @Getter
    @Setter
    public static class DeactivateLabDeviceItemCommand {

        /** 设备主键。 */
        private Long id;
    }
}
