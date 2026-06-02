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
 * 重新激活医疗服务命令（批量）。
 * <p>
 * 将因暂停、退役等原因不可用的医疗服务恢复为可对外提供状态（与业务状态机一致）。
 */
@Getter
@Setter
public class ReactivateHealthcareServiceCommand extends BatchCommand<ReactivateHealthcareServiceCommand.Item> {
    /** 执行本次批量重新激活的操作人标识。 */
    private Long operatorId;
    /** 单条激活项：指定待重新激活的医疗服务。 */
    @Getter
    @Setter
    public static class Item {
        /** 待重新激活的医疗服务主键。 */
        private Long serviceId;
    }
}
