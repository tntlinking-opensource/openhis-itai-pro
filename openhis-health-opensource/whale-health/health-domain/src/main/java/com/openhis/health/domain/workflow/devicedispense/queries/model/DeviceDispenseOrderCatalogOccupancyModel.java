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

package com.openhis.health.domain.workflow.devicedispense.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医嘱开立目录用：同就诊下耗材发放预占行（候选库位 ∩ 目录耗材定义）。
 *
 * <p>供 {@code DeviceDispenseQueryService#listDeviceDispensesForOrderCatalogOccupancy} 返回；状态列为字符串码，与耗材发放聚合持久化一致。</p>
 */
@Getter
@Setter
public class DeviceDispenseOrderCatalogOccupancyModel {

    /** 耗材定义主键（与目录行耗材 id 对齐）。 */
    private Long deviceDefId;

    /** 发放占用的库房库位。 */
    private Long locationId;

    /** 发放行声明数量（业务单位；目录预减须换算为拆零单位）。 */
    private BigDecimal quantity;

    /** 发放行数量单位编码。 */
    private String unitCode;

    /** 发放状态码（与 {@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()} 一致）。 */
    private String statusEnum;
}
