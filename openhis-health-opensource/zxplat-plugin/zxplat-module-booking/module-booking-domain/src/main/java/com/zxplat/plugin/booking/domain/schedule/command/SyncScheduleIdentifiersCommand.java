/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 同步日程外部标识命令。
 */
@Getter
@Setter
public class SyncScheduleIdentifiersCommand extends BatchCommand<SyncScheduleIdentifiersCommand.SyncScheduleIdentifiersItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 同步排班identifiers项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class SyncScheduleIdentifiersItemCommand {
        /** 排班主键。 */
        private Long scheduleId;
        /** identifiers。 */
        private List<ScheduleIdentifierItemCommand> identifiers;
    }

/**
 * 排班标识符项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ScheduleIdentifierItemCommand {
        /** 标识system。 */
        private String idSystem;
        /** 标识value。 */
        private String idValue;
        /** use编码。 */
        private String useCode;
    }
}
