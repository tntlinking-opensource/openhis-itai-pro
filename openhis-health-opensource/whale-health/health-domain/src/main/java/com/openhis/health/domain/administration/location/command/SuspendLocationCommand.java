/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 暂停位置命令：临时挂起该地点服务（如消杀），需记录原因。
 */
@Getter
@Setter
public class SuspendLocationCommand extends BatchCommand<SuspendLocationCommand.SuspendLocationItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条暂停项。 */
    @Getter
    @Setter
    public static class SuspendLocationItemCommand {
        /** 位置主键。 */
        private Long locationId;
        /** 暂停原因。 */
        private String reason;
    }
}
