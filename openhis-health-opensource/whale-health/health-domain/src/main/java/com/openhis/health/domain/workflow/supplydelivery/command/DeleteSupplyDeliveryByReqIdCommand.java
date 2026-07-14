/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplydelivery.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按上游供应请求行主键批量删除发放记录命令（见 {@link com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService#deleteSupplyDeliveryByReqId}）。
 *
 * <p>跨域「已驱动库存」须由应用层先行处理；本命令仅按 {@code request_id} 定位并从仓储移除发放行。</p>
 */
@Getter
@Setter
public class DeleteSupplyDeliveryByReqIdCommand extends BatchCommand<DeleteSupplyDeliveryByReqIdCommand.DeleteSupplyDeliveryByReqIdItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 删除参数：一批供应请求行主键。
     */
    @Getter
    @Setter
    public static class DeleteSupplyDeliveryByReqIdItemCommand {
        /** 供应请求行主键 {@code wkf_supply_request.id} 列表。 */
        private List<Long> requestIdList;
    }
}
