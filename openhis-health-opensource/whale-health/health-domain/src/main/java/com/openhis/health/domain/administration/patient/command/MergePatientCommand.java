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
