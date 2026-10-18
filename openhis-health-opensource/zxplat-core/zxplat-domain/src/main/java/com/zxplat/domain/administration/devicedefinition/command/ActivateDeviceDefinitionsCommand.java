/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.devicedefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量启用医疗器械目录命令。
 */
@Getter
@Setter
public class ActivateDeviceDefinitionsCommand extends BatchCommand<ActivateDeviceDefinitionsCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;
    /**
     * 批量命令明细项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 待启用目录标识。 */
        private Long id;
    }
}
