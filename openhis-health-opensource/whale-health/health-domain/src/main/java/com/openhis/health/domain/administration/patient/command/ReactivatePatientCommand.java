/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 重新启用患者命令。
 */
@Getter
@Setter
public class ReactivatePatientCommand extends BatchCommand<ReactivatePatientCommand.ReactivatePatientItemCommand> {

    /**
     * 重新启用子命令。
     */
    @Getter
    @Setter
    public static class ReactivatePatientItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;
    }
}
