/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 占用床位命令：将床位地点与住院就诊建立占用关系。
 */
@Getter
@Setter
public class OccupyBedCommand extends BatchCommand<OccupyBedCommand.OccupyBedItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条占用项。 */
    @Getter
    @Setter
    public static class OccupyBedItemCommand {
        /** 床位所在位置主键。 */
        private Long locationId;
        /** 住院就诊主键。 */
        private Long encounterId;
        /** 为 true 时自 {@code RESERVED} 占床（预约患者分床，GAP-ATD-007）。 */
        private Boolean occupyFromReservation;
    }
}
