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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-DRA-03：按支付主单分组的执行侧衔接摘要（供收费窗退费执行消费）。
 */
@Data
@Accessors(chain = true)
/** 门诊医生站-申请退费-按支付分组的执行衔接摘要。 */
@Schema(description = "门诊医生站-申请退费-按支付分组的执行衔接摘要")
public class OutpatientRefundApplicationPaymentSummaryDto {

    @Schema(description = "原支付主单主键")
    private Long paymentId;

    @Schema(description = "拟退收费明细主键列表")
    private List<Long> refundingChargeItemIds;

    /** 半退保留链再生、待再结算的收费明细主键。 */
    @Schema(description = "保留再生 PLANNED 收费明细主键列表")
    private List<Long> preservedNewChargeItemIds;

    /** 该笔支付是否为产品半退（同 payment 下既有拟退又有保留）。 */
    @Schema(description = "是否产品半退")
    private boolean halfRefundOnPayment;
}
