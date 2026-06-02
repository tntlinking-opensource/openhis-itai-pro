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

import java.time.LocalDateTime;

/**
 * 供应请求审批驳回命令（同 {@code bus_no} 全行驳回，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#rejectRequest}）。
 *
 * <p>{@code changeReasonCode} 为写入聚合的原因/变更码（字符串，与 AS 约定对齐）；状态列仍为枚举 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class RejectRequestCommand extends BatchCommand<RejectRequestCommand.RejectRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单张单据驳回参数。
     */
    @Getter
    @Setter
    public static class RejectRequestItemCommand {
        /** 业务单号。 */
        private String busNo;
        /** 审批人主键。 */
        private Long approverId;
        /** 审批业务时钟；可空时由领域默认。 */
        private LocalDateTime approvalClock;
        /** 驳回/变更原因编码或短文本（领域落 {@code reason} 或与 DM 约定列对齐）。 */
        private String changeReasonCode;
    }
}
