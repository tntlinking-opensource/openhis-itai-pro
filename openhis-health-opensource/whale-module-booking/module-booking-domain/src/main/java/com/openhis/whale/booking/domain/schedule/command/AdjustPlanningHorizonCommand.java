/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.schedule.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 调整规划窗口命令。
 */
@Getter
@Setter
public class AdjustPlanningHorizonCommand extends BatchCommand<AdjustPlanningHorizonCommand.AdjustPlanningHorizonItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * adjust计划计划区间项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class AdjustPlanningHorizonItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** new开始。 */
        private LocalDateTime newStart;
        /** new结束。 */
        private LocalDateTime newEnd;
    }
}
