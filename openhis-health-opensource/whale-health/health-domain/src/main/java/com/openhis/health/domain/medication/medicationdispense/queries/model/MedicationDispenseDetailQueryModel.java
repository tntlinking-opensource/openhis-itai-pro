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

package com.openhis.health.domain.medication.medicationdispense.queries.model;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.DispenseStatus;

import java.math.BigDecimal;

/**
 * 发放详情查询模型。
 */
@Getter
@Setter
public class MedicationDispenseDetailQueryModel {
    /** 主键。 */
    private Long id;
    /** 状态枚举编码。 */
    private DispenseStatus statusEnum;
    /** med申请主键。 */
    private Long medReqId;
    /** 数量。 */
    private BigDecimal quantity;
    /** 发药数量。 */
    private BigDecimal dispenseQuantity;
}
