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
 * 发布医疗服务命令（批量）。
 * <p>
 * 将草稿或待发布状态的医疗服务发布为可用，供挂号、收费等业务引用。
 */
@Getter
@Setter
public class PublishHealthcareServiceCommand extends BatchCommand<PublishHealthcareServiceCommand.Item> {
    /** 执行本次批量发布的操作人标识。 */
    private Long operatorId;
    /** 单条发布项：指定待发布的医疗服务。 */
    @Getter
    @Setter
    public static class Item {
        /** 待发布的医疗服务主键。 */
        private Long serviceId;
    }
}
