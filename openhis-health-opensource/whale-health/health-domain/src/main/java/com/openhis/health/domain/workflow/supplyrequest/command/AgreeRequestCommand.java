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
 * 供应请求审批同意命令（同 {@code bus_no} 下全部明细批量同意，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#agreeRequest}）。
 *
 * <p>批次语义：{@link BatchCommand#resolveItems()} 须恰好一项；同意后实体 {@code status_enum} 写入
 * {@link com.openhis.health.domain.share.enums.SupplyStatus} 的持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class AgreeRequestCommand extends BatchCommand<AgreeRequestCommand.AgreeRequestItemCommand> {

    /** 操作人主键（事件载荷与审计）。 */
    private Long operatorId;

    /**
     * 单张单据同意参数。
     */
    @Getter
    @Setter
    public static class AgreeRequestItemCommand {
        /** 业务单号。 */
        private String busNo;
        /** 审批人主键（领域必填；应用层可回落为当前操作人）。 */
        private Long approverId;
        /** 审批业务时钟；可空时由领域默认当前时间。 */
        private LocalDateTime approvalClock;
    }
}
