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

package com.openhis.health.domain.workflow.devicerequest.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 半退保留链：按源耗材请求事实在本聚合内正向克隆新请求（DM-WKF-003 §6.1.11），供应用编排与 ChargeItem 新锚点对齐。
 */
@Getter
@Setter
public class CloneDeviceRequestForPreservedChargeRegenerationCommand {

    /** 源耗材请求主键。 */
    private Long sourceDeviceRequestId;

    /** 操作人主键。 */
    private Long operatorId;
}
