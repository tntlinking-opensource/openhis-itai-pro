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

package com.openhis.health.domain.administration.encounter.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 确认转科命令：确认患者转入目标科室的生效时间。
 */
@Getter
@Setter
public class ConfirmDepartmentTransferCommand extends BatchCommand<ConfirmDepartmentTransferCommand.ConfirmDepartmentTransferItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条确认项。 */
    @Getter
    @Setter
    public static class ConfirmDepartmentTransferItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 目标科室主键。 */
        private Long targetDepartmentId;
        /** 转科生效时间。 */
        private LocalDateTime effectiveAt;
    }
}
