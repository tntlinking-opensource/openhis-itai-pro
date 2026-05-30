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

package com.openhis.health.application.outpatient.financial.charge.response;

import com.openhis.health.application.outpatient.financial.charge.dto.PreSettleChargeLinePreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊预结算响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊预结算响应")
public class PreSettleOutpatientChargesResponse {
    /** 支付草稿主键。 */
    @Schema(description = "支付草稿主键")
    private Long paymentDraftId;
    /** 展示金额。 */
    @Schema(description = "展示应收金额")
    private BigDecimal displayAmount;
    /** 预结算行预览。 */
    @Schema(description = "预结算行预览")
    private List<PreSettleChargeLinePreviewDto> previews;
}
