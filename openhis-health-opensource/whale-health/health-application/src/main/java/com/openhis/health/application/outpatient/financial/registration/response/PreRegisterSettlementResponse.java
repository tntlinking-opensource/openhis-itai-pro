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

package com.openhis.health.application.outpatient.financial.registration.response;

import com.openhis.health.application.outpatient.financial.registration.dto.ChargeItemPreviewDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 挂号预结算响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号预结算响应")
public class PreRegisterSettlementResponse {

    /** 本次预结算对应的就诊主键（待挂号态 PLANNED，结算成功后变为 REGISTERED）。 */
    @Schema(description = "就诊ID")
    private Long encounterId;

    /** 支付草稿 ID。 */
    @Schema(description = "支付草稿ID")
    private Long paymentDraftId;

    /** 预结算总额。 */
    @Schema(description = "总额")
    private BigDecimal totalAmount;

    /** 预结算明细。 */
    @Schema(description = "明细")
    private List<ChargeItemPreviewDto> chargeItems;
}
