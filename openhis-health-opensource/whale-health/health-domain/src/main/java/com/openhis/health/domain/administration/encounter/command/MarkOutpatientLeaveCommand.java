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
 * 门诊暂离命令：患者暂时离开候诊/诊室，记录原因与时间。
 */
@Getter
@Setter
public class MarkOutpatientLeaveCommand extends BatchCommand<MarkOutpatientLeaveCommand.MarkOutpatientLeaveItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条暂离项。 */
    @Getter
    @Setter
    public static class MarkOutpatientLeaveItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 暂离原因。 */
        private String leaveReason;
        /** 发生时间。 */
        private LocalDateTime occurredAt;
    }
}
