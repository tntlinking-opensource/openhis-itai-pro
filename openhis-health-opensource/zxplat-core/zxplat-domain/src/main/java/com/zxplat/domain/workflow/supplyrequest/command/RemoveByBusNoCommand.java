/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 按业务单号逻辑删除供应请求命令（同 {@code bus_no} 全部明细行软删，见 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService#removeByBusNo}）。
 *
 * <p>跨域「已产生下游发放/库存」门控由应用层在调用前完成；领域仅做存在性删除与事件发布。</p>
 */
@Getter
@Setter
public class RemoveByBusNoCommand extends BatchCommand<RemoveByBusNoCommand.RemoveByBusNoItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单张单据删除参数。
     */
    @Getter
    @Setter
    public static class RemoveByBusNoItemCommand {
        /** 业务单号。 */
        private String busNo;
    }
}
