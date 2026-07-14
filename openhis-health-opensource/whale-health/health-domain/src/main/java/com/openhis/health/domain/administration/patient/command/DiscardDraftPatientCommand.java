/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;


/**
 * 废弃临时建档命令。
 */
@Getter
@Setter
public class DiscardDraftPatientCommand extends BatchCommand<DiscardDraftPatientCommand.DiscardDraftPatientItemCommand> {

    /**
     * 废弃临时建档子命令。
     */
    @Getter
    @Setter
    public static class DiscardDraftPatientItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;
    }
}
