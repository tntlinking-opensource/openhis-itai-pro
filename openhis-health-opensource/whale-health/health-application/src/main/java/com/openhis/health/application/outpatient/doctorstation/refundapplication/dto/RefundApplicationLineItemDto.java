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

package com.openhis.health.application.outpatient.doctorstation.refundapplication.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DRA-03：提交体单行（支付锚点 + 收费行锚点 + 本单退费标记）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-提交行")
public class RefundApplicationLineItemDto {

    /** 支付流水主键。 */
    @NotNull(message = "支付流水标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "支付锚点（与 IF-DRA-01 返回成套）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long paymentId;

    /** 收费明细主键。 */
    @NotNull(message = "收费明细标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费行锚点", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long chargeItemId;

    /** 本单是否拟退。 */
    @NotNull(message = "本单退费标记不能为空")
    @Schema(description = "本单退费：true 拟退；false 保留并重开账单", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean refundForCurrentBill;
}
