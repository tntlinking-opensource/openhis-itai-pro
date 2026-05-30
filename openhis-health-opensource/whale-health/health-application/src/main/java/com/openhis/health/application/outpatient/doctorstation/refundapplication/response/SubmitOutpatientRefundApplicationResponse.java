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

package com.openhis.health.application.outpatient.doctorstation.refundapplication.response;

import com.openhis.health.application.outpatient.doctorstation.refundapplication.dto.OutpatientRefundApplicationPaymentSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-03：提交门诊退费申请响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-申请退费-提交门诊退费申请响应")
public class SubmitOutpatientRefundApplicationResponse {

    /** 受理成功。 */
    @Schema(description = "受理成功")
    private boolean success;

    /** 进入退款中的拟退行数。 */
    @Schema(description = "进入 REFUNDING 的拟退行数")
    private int refundingChargeItemCount;

    /** 按支付分组的执行衔接摘要（BR-DRA-020）。 */
    @Schema(description = "按支付分组的执行衔接摘要")
    private List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries;
}
