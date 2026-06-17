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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 护士划价列表按就诊分组（IF-IP-PRC-03/05）。
 *
 * <p>同一 DTO 承载两种列表：{@link #orders} 为补费父医嘱；{@link #pricedOrders} 为已划价行，互斥使用。</p>
 */
@Data
@Accessors(chain = true)
/** 护士划价按就诊分组。 */
@Schema(description = "护士划价按就诊分组")
public class NurseStationPricingPatientGroupDto {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 补费父医嘱行（IF-IP-PRC-03）。 */
    @Schema(description = "补费父医嘱行")
    private List<NurseStationSupplementParentOrderRowDto> orders = new ArrayList<>();

    /** 已划价行（IF-IP-PRC-05）。 */
    @Schema(description = "已划价行")
    private List<NurseStationPricedOrderRowDto> pricedOrders = new ArrayList<>();
}
