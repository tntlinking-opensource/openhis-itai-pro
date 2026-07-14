/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 明确患者身份命令。
 */
@Getter
@Setter
public class ConfirmPatientIdentityCommand extends BatchCommand<ConfirmPatientIdentityCommand.ConfirmPatientIdentityItemCommand> {

    /**
     * 明确患者身份子命令。
     */
    @Getter
    @Setter
    public static class ConfirmPatientIdentityItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;
    }
}
