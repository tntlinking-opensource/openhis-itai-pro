/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 合并患者命令。
 */
@Getter
@Setter
public class MergePatientCommand extends BatchCommand<MergePatientCommand.MergePatientItemCommand> {

    /**
     * 合并患者子命令。
     */
    @Getter
    @Setter
    public static class MergePatientItemCommand {

        /**
         * 源患者标识。
         */
        private Long sourcePatientId;

        /**
         * 目标患者标识。
         */
        private Long targetPatientId;

        /**
         * 合并原因。
         */
        private String reason;
    }
}
