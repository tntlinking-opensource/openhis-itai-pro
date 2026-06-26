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

/**
 * 取消划价单行入参（IF-IP-PRC-06，与已划价列表 {@code NurseStationPricedOrderRowDto} 对齐）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消划价单行")
public class NurseStationPricingCancelItemDto {

    /** 医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST。 */
    @Schema(description = "医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderType;

    /** 诊疗/耗材请求主键（与列表 orderId 一致）。 */
    @Schema(description = "请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 就诊主键（可见性 Gate 与列表分组一致）。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
