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
/** 挂号预结算响应。 */
@Schema(description = "挂号预结算响应")
public class PreRegisterSettlementResponse {

    @Schema(description = "就诊ID")
    /** 就诊ID（业务字段）。 */
    private Long encounterId;

    /** 支付草稿 ID。 */
    @Schema(description = "支付草稿ID")
    /** 支付草稿ID（业务字段）。 */
    private Long paymentDraftId;

    /** 预结算总额。 */
    @Schema(description = "总额")
    /** 总额（业务字段）。 */
    private BigDecimal totalAmount;

    /** 预结算明细。 */
    @Schema(description = "明细")
    /** 明细（业务字段）。 */
    private List<ChargeItemPreviewDto> chargeItems;

    /** 关联预约主键（预约转挂号时回传）。 */
    @Schema(description = "预约ID")
    /** 预约ID（业务字段）。 */
    private Long appointmentId;

    /** 关联号源主键（预约转挂号时回传）。 */
    @Schema(description = "号源ID")
    /** 号源ID（业务字段）。 */
    private Long slotId;

    /** 是否已完成医保预结算（2203→2206）。 */
    @Schema(description = "是否已完成医保预结算")
    private Boolean insurancePreSettled;

    /** 医保批次号 chrg_bchno（缴费 confirm 2207 时使用）。 */
    @Schema(description = "医保批次号")
    private String ybChrgBchno;

    @Schema(description = "医保医疗费总额")
    private BigDecimal ybMedfeeSumamt;

    @Schema(description = "医保基金支付")
    private BigDecimal ybFundPaySumamt;

    @Schema(description = "医保个人账户支付")
    private BigDecimal ybAcctPay;

    @Schema(description = "医保个人现金支付")
    private BigDecimal ybPsnCashPay;
}
