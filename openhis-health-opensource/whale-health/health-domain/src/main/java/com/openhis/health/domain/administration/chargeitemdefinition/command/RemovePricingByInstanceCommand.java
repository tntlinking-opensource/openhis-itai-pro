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

package com.openhis.health.domain.administration.chargeitemdefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按业务实例移除定价命令：根据实例表名与实例主键批量清理关联的收费项目定义/价表行。
 */
@Getter
@Setter
public class RemovePricingByInstanceCommand {
    /** 实例所在逻辑表名或类型标识（与持久化分层约定一致）。 */
    private String instanceTable;
    /** 业务实例主键。 */
    private Long instanceId;
    /** 操作人主键。 */
    private Long operatorId;
}
