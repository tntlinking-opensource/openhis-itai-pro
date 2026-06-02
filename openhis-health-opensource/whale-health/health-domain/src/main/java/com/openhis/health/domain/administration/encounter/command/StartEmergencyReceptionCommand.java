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
 * 急诊接诊命令：医师开始接诊并记录抢救标识。
 */
@Getter
@Setter
public class StartEmergencyReceptionCommand extends BatchCommand<StartEmergencyReceptionCommand.StartEmergencyReceptionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条接诊项。 */
    @Getter
    @Setter
    public static class StartEmergencyReceptionItemCommand {
        /** 就诊主键。 */
        private Long encounterId;
        /** 接诊医师主键。 */
        private Long doctorId;
        /**
         * 接诊医师姓名展示快照（应用层按医师主键解析）；供领域事件携带，驱动读模型医生列展示。
         */
        private String doctorName;
        /** 接诊时间。 */
        private LocalDateTime receptionTime;
        /** 是否抢救中。 */
        private Boolean rescueFlag;
    }
}
