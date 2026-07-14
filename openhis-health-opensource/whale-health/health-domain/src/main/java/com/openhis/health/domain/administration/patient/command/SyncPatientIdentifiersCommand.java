/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.patient.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.command.BatchCommand;

import java.util.List;

/**
 * 同步患者证件命令。
 */
@Getter
@Setter
public class SyncPatientIdentifiersCommand extends BatchCommand<SyncPatientIdentifiersCommand.SyncPatientIdentifiersItemCommand> {

    /**
     * 同步患者证件子命令。
     */
    @Getter
    @Setter
    public static class SyncPatientIdentifiersItemCommand {

        /**
         * 患者标识。
         */
        private Long patientId;

        /**
         * 非身份证证件集合。
         */
        private List<PatientIdentifierItemCommand> identifierItems;
    }
}
