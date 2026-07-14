/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 停用患者命令。
 */
@Getter
@Setter
public class DeactivatePatientCommand extends BatchCommand<DeactivatePatientCommand.DeactivatePatientItemCommand> {

    /**
     * 停用患者子命令。
     */
    @Getter
    @Setter
    public static class DeactivatePatientItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 停用原因。
         */
        private String reason;
    }
}
