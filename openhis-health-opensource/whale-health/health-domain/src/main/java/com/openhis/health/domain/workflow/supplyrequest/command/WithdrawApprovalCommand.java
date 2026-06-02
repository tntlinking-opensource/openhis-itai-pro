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

/**
 * 供应请求撤回审批命令（同 {@code bus_no} 全行撤回，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#withdrawApproval}）。
 *
 * <p>目标状态为 {@link com.openhis.health.domain.share.enums.SupplyStatus} 持久化 code（B.10.8.1）。</p>
 */
@Getter
@Setter
public class WithdrawApprovalCommand extends BatchCommand<WithdrawApprovalCommand.WithdrawApprovalItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单张单据撤回参数。
     */
    @Getter
    @Setter
    public static class WithdrawApprovalItemCommand {
        /** 业务单号。 */
        private String busNo;
    }
}
