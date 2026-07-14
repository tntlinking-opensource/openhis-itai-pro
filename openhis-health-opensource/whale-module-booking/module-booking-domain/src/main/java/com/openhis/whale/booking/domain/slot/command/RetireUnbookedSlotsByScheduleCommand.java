/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停诊后回收日程下无预约占用的号源命令（释放 uk_wkf_slot_org_time_window）。
 */
@Getter
@Setter
public class RetireUnbookedSlotsByScheduleCommand
    extends BatchCommand<RetireUnbookedSlotsByScheduleCommand.RetireUnbookedSlotsByScheduleItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条日程回收项。
     */
    @Getter
    @Setter
    public static class RetireUnbookedSlotsByScheduleItemCommand {
        /** 日程主键。 */
        private Long scheduleId;
    }
}
