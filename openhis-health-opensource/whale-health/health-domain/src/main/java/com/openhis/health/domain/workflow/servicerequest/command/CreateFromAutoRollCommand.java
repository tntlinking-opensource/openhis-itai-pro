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

package com.openhis.health.domain.workflow.servicerequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院滚费建单命令（AS-IP-NUR-013 IF-IP-ARG-08；DM-WKF-004 §6.1.20）。
 *
 * <p>语义同 {@link CreateFromNursePricingCommand}，但生成来源固定为 {@code auto-roll-fees}，且须携带费用发生时刻与诊疗类目。</p>
 */
@Getter
@Setter
public class CreateFromAutoRollCommand extends BatchCommand<CreateFromAutoRollCommand.CreateFromAutoRollItemCommand> {

    /** 系统/任务操作人主键（写入 performer_check_id）。 */
    private Long operatorId;

    /**
     * 单条滚费诊疗申请创建项。
     */
    @Getter
    @Setter
    public static class CreateFromAutoRollItemCommand {

        /** 就诊主键。 */
        private Long encounterId;

        /** 患者主键。 */
        private Long patientId;

        /** 诊疗活动定义主键。 */
        private Long activityId;

        /** 数量。 */
        private BigDecimal quantity;

        /** 数量单位持久化码。 */
        private String unitCode;

        /** 开立人主键。 */
        private Long requesterId;

        /** 诊疗类目持久化码（床位服务 51 / 护理级别 26 等）。 */
        private String categoryEnum;

        /** 费用发生时刻（通常为 billDate 当日口径；写入 check_time / 费用 occurrence_time）。 */
        private LocalDateTime occurrenceTime;
    }
}
