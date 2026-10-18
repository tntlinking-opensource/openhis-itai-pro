/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.command.BatchCommand;

import java.time.LocalDateTime;

/**
 * 标记患者死亡命令。
 */
@Getter
@Setter
public class MarkPatientDeceasedCommand extends BatchCommand<MarkPatientDeceasedCommand.MarkPatientDeceasedItemCommand> {

    /**
     * 标记患者死亡子命令。
     */
    @Getter
    @Setter
    public static class MarkPatientDeceasedItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 死亡时间。
         */
        private LocalDateTime deceasedDate;

        /**
         * 死亡原因。
         */
        private String deceasedReason;
    }
}
