/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 清床命令：患者出院后释放床位占用，记录清床时间。
 */
@Getter
@Setter
public class ClearDischargedBedCommand extends BatchCommand<ClearDischargedBedCommand.ClearDischargedBedItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条清床项。 */
    @Getter
    @Setter
    public static class ClearDischargedBedItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 床位地点主键。 */
        private Long bedId;
        /** 清床时间。 */
        private LocalDateTime clearedAt;
    }
}
