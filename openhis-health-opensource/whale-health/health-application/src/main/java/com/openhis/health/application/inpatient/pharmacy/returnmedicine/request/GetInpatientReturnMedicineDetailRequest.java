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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 查询住院退药明细请求（IF-IPR-03）。
 */
@Data
/** 查询住院退药明细请求。 */
@Schema(description = "查询住院退药明细请求", requiredProperties = {"encounterId"})
public class GetInpatientReturnMedicineDetailRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 退药状态过滤：PENDING_REFUND / REFUNDED；空表示默认子集。 */
    @Schema(description = "退药状态过滤：PENDING_REFUND / REFUNDED；空表示默认子集")
    private String refundStatus;
}
