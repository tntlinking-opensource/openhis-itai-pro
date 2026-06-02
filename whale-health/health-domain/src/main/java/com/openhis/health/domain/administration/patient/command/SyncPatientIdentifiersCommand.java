/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
