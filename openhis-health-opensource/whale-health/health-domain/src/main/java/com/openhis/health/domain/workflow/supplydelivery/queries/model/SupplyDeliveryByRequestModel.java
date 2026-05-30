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

package com.openhis.health.domain.workflow.supplydelivery.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 按供应请求维度汇总的发放读模型（请求行与发放记录对应及数量）。
 */
@Getter
@Setter
public class SupplyDeliveryByRequestModel {

    /** 供应请求行主键。 */
    private Long requestId;
    /** 发放记录主键。 */
    private Long deliveryId;
    /** 发放数量（可正可负依业务）。 */
    private BigDecimal quantity;
}
