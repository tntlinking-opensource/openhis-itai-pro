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

package com.openhis.health.domain.workflow.supplydelivery.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量将发放记录推进为已完成命令（见 {@link com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService#updateCompletedStatusBatch}）。
 *
 * <p>目标完成态的 {@code status_enum} 由实体 {@link com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery#toCompleted} 写入，为
 * {@link com.openhis.health.domain.share.enums.DispenseStatus} 的持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class UpdateCompletedStatusBatchCommand extends BatchCommand<UpdateCompletedStatusBatchCommand.UpdateCompletedStatusBatchItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 一批发放统一完成参数。
     */
    @Getter
    @Setter
    public static class UpdateCompletedStatusBatchItemCommand {
        /** 待完成的发放主键列表。 */
        private List<Long> supplyDeliveryIdList;
        /** 经办执业者主键。 */
        private Long practitionerId;
        /** 完成业务时间。 */
        private LocalDateTime completedTime;
    }
}
