/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
