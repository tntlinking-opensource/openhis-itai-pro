/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
