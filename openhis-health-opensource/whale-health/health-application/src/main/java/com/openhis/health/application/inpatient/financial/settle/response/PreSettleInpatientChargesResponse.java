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

package com.openhis.health.application.inpatient.financial.settle.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院预结算响应（IF-IP-STL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院预结算响应")
public class PreSettleInpatientChargesResponse {

    @Schema(description = "住院临床结算支付草稿 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentDraftId;

    @Schema(description = "应收合计")
    private BigDecimal receivableTotal;

    @Schema(description = "预览文案")
    private String previewText;
}
