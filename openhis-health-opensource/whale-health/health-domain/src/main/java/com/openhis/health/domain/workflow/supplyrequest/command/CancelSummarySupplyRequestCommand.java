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

package com.openhis.health.domain.workflow.supplyrequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 取消药品汇总供应请求命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#cancelSummarySupplyRequest}）。
 *
 * <p>仅处理 {@link com.openhis.health.domain.share.enums.SupplyType#MEDICATION_SUMMARY_ORDER} 等约定类型；具体行筛选由领域实现。</p>
 */
@Getter
@Setter
public class CancelSummarySupplyRequestCommand extends BatchCommand<CancelSummarySupplyRequestCommand.CancelSummarySupplyRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 取消参数：按汇总主键列表定位待取消行。
     */
    @Getter
    @Setter
    public static class CancelSummarySupplyRequestItemCommand {
        /** 汇总主键列表 {@code summary_id}。 */
        private List<Long> summaryIdList;
    }
}
