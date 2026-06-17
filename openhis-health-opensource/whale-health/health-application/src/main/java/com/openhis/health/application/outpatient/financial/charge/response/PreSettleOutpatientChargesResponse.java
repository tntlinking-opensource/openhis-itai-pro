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

import com.openhis.health.application.outpatient.financial.charge.dto.OpInsurancePreSettleGroupDto;
import com.openhis.health.application.outpatient.financial.charge.dto.OpSelfPayRollupDto;
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
/** 门诊预结算响应。 */
@Schema(description = "门诊预结算响应")
public class PreSettleOutpatientChargesResponse {
    @Schema(description = "支付草稿主键")
    /** 支付草稿主键（业务字段）。 */
    private Long paymentDraftId;
    @Schema(description = "展示应收金额")
    /** 展示应收金额（业务字段）。 */
    private BigDecimal displayAmount;
    /** 预结算行预览。 */
    @Schema(description = "预结算行预览")
    /** 预结算行预览（业务字段）。 */
    private List<PreSettleChargeLinePreviewDto> previews;
    /** 医保收费批次号（2206 成功后返回）。 */
    @Schema(description = "医保收费批次号")
    /** 医保收费批次号（业务字段）。 */
    private String ybChrgBchno;
    /** 医保医疗费总额。 */
    @Schema(description = "医保医疗费总额")
    /** 医保医疗费总额（业务字段）。 */
    private BigDecimal ybMedfeeSumamt;
    /** 医保统筹支付。 */
    @Schema(description = "医保统筹支付")
    /** 医保统筹支付（业务字段）。 */
    private BigDecimal ybFundPaySumamt;
    /** 医保个账支付。 */
    @Schema(description = "医保个账支付")
    /** 医保个账支付（业务字段）。 */
    private BigDecimal ybAcctPay;
    /** 医保现金/自付。 */
    @Schema(description = "医保现金自付")
    /** 医保现金自付（业务字段）。 */
    private BigDecimal ybPsnCashPay;
    /** 是否已完成医保 2206 预结算。 */
    @Schema(description = "是否已完成医保预结算")
    /** 是否已完成医保预结算（业务字段）。 */
    private Boolean insurancePreSettled;
    /** MIXED | INSURANCE_ONLY | SELF_PAY_ONLY */
    @Schema(description = "结算构成")
    /** 结算构成（业务字段）。 */
    private String settlementComposition;
    /** 医保分组明细 */
    @Schema(description = "医保预结算分组")
    /** 医保预结算分组（业务字段）。 */
    private List<OpInsurancePreSettleGroupDto> insuranceGroups;
    /** 自费汇总 */
    @Schema(description = "自费汇总")
    /** 自费汇总（业务字段）。 */
    private OpSelfPayRollupDto selfPayRollup;
}
