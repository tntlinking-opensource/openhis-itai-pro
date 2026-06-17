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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 退药确认单项请求（IF-PHA-02-04）。
 */
@Data
/** 退药确认单项请求。 */
@Schema(description = "退药确认单项请求")
public class ReturnMedicineItemRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "发放单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 负向请求单主键。 */
    @Schema(description = "负向请求单主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 业务表名，固定为 med_medication_request。 */
    @Schema(description = "业务表名，固定 med_medication_request", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableName;
}
