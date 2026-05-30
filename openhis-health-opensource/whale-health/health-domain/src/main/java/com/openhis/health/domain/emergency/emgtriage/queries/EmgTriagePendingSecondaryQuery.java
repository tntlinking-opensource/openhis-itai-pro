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

package com.openhis.health.domain.emergency.emgtriage.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 待二次分诊列表筛选（AS-SDD：与机构 SLA 参数协同；当前实现先按机构维度收敛候选集）。
 */
@Getter
@Setter
public class EmgTriagePendingSecondaryQuery {

    /** 科室 / 机构上下文。 */
    private Long organizationId;

    /**
     * 机构 SLA：超过该分钟数视为待二次分诊候选项的业务语义键（具体阈值由参数中心解析；查询层暂留口径位）。
     */
    private Integer slaMinutes;
}
