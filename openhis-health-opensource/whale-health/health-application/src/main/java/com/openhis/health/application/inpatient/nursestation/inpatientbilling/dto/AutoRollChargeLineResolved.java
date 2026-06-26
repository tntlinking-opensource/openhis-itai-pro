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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * IF-IP-ARG-08：滚费定价解析结果。
 */
@Getter
@Setter
public class AutoRollChargeLineResolved {

    /** 是否解析成功。 */
    private boolean resolved;

    /** 收费项定价主键。 */
    private Long chargeDefinitionId;

    /** 诊疗活动定义主键。 */
    private Long activityId;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 单价。 */
    private BigDecimal unitPrice;

    /** 行总价（单价 × 数量）。 */
    private BigDecimal totalPrice;
}
