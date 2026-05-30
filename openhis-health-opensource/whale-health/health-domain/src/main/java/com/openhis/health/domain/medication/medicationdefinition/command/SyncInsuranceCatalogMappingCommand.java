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

package com.openhis.health.domain.medication.medicationdefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同步医保目录对码命令。
 */
@Getter
@Setter
public class SyncInsuranceCatalogMappingCommand
    extends BatchCommand<SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 医保对码同步子命令。
     */
    @Getter
    @Setter
    public static class SyncInsuranceCatalogMappingItemCommand {
        /** 药品定义标识。 */
        private Long id;
        /** 是否对码。 */
        private Boolean ybMatchFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 同步时间。 */
        private LocalDateTime syncTime;
    }
}
