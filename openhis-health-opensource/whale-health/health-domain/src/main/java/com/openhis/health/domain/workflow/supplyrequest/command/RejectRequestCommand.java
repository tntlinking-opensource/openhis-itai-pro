/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
        /**
         * 驳回原因（人读文案或原因码）；{@code rejectRequest} 全行写入实体 {@code reason}，列表以 {@code rejectReason} 回显。
         */
        private String changeReasonCode;
    }
}
