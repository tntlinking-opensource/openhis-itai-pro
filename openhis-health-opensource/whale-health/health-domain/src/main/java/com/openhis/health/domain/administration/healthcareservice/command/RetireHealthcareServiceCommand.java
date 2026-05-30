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

package com.openhis.health.domain.administration.healthcareservice.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 退役医疗服务命令（批量）。
 * <p>
 * 将医疗服务标记为退役（业务下线），通常仍可查询历史，但不再作为新开业务选项，需记录原因。
 */
@Getter
@Setter
public class RetireHealthcareServiceCommand extends BatchCommand<RetireHealthcareServiceCommand.Item> {
    /** 执行本次批量退役的操作人标识。 */
    private Long operatorId;
    /**
     * 单条退役项：指定待退役的医疗服务及原因。
     */
    @Getter
    @Setter
    public static class Item {
        /** 待退役的医疗服务主键。 */
        private Long serviceId;
        /** 退役原因说明。 */
        private String reason;
    }
}
