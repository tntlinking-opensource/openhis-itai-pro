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
 * 出院召回命令（DM-ADM-002 §6.1.25）：将已清床离院（{@code DISCHARGED}）的住院就诊拉回待入科态。
 *
 * <p>应用层须在同事务内完成反结（AS-IP-FIN-004，待内嵌）后再调用；本命令只迁移就诊主档与活动 {@code WARD} 轨迹。</p>
 */
@Getter
@Setter
public class RecallInpatientDischargeCommand
    extends BatchCommand<RecallInpatientDischargeCommand.RecallInpatientDischargeItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /** 单条出院召回项。 */
    @Getter
    @Setter
    public static class RecallInpatientDischargeItemCommand {

        /** 住院就诊主键。 */
        private Long encounterId;

        /**
         * 末次已完成病区位置主键（{@code adm_location.id}，形态 {@code WARD}）。
         *
         * <p>出院召回（{@code DISCHARGED} 轨）必填，供 INSERT 活动 {@code WARD}。</p>
         */
        private Long wardLocationId;

        /** 召回生效时刻；空则由领域服务取当前时间。 */
        private LocalDateTime recalledAt;
    }
}
