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
 * 取消门诊就诊命令：退号或撤销就诊登记。
 */
@Getter
@Setter
public class CancelOutpatientRegistrationCommand extends BatchCommand<CancelOutpatientRegistrationCommand.CancelOutpatientRegistrationItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条取消项。 */
    @Getter
    @Setter
    public static class CancelOutpatientRegistrationItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 取消原因。 */
        private String cancelReason;
        /** 取消时间。 */
        private LocalDateTime cancelAt;
    }
}
