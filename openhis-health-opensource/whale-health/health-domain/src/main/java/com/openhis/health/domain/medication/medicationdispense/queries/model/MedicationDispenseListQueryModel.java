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
import java.time.LocalDateTime;

/**
 * 发放列表查询模型。
 */
@Getter
@Setter
public class MedicationDispenseListQueryModel {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 状态枚举编码。 */
    private DispenseStatus statusEnum;
    /** 药品请求主键。 */
    private Long medReqId;
    /** 药品定义主键。 */
    private Long medicationId;
    /** 发放数量。 */
    private BigDecimal quantity;
    /** 单位 code。 */
    private String unitCode;
    /** 位置主键。 */
    private Long locationId;
    /** planned发药时间。 */
    private LocalDateTime plannedDispenseTime;
}
