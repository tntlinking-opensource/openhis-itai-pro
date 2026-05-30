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
 * 按业务单号逻辑删除供应请求命令（同 {@code bus_no} 全部明细行软删，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#removeByBusNo}）。
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
