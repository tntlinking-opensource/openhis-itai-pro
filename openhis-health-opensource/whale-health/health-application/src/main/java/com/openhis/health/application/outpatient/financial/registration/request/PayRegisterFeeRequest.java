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

package com.openhis.health.application.outpatient.financial.registration.request;

import com.openhis.health.application.outpatient.financial.registration.dto.PaymentDetailDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 挂号支付请求。
 */
@Data
@Accessors(chain = true)
/** 挂号支付请求。 */
@Schema(description = "挂号支付请求")
public class PayRegisterFeeRequest {

    @Schema(description = "支付草稿ID")
    /** 支付草稿ID（业务字段）。 */
    private Long paymentDraftId;

    /** 支付明细列表。 */
    @Schema(description = "支付明细")
    /** 支付明细（业务字段）。 */
    private List<PaymentDetailDto> paymentDetails;

    /** 关联预约主键（缴费成功后回填 encounter）。 */
    @Schema(description = "预约ID")
    /** 预约ID（业务字段）。 */
    private Long appointmentId;
}
