/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 废弃位置命令：将地点/床位等资源标记为废止，不再参与业务选用。
 */
@Getter
@Setter
public class AbolishLocationCommand extends BatchCommand<AbolishLocationCommand.AbolishLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条废弃项。 */
    @Getter
    @Setter
    public static class AbolishLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
    }
}
