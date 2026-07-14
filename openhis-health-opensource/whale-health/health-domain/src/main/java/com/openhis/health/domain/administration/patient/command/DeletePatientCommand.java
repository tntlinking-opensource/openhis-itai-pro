/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 逻辑删除患者命令。
 */
@Getter
@Setter
public class DeletePatientCommand extends BatchCommand<DeletePatientCommand.DeletePatientItemCommand> {

    /**
     * 逻辑删除患者子命令。
     */
    @Getter
    @Setter
    public static class DeletePatientItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 删除原因。
         */
        private String deleteReason;
    }
}
