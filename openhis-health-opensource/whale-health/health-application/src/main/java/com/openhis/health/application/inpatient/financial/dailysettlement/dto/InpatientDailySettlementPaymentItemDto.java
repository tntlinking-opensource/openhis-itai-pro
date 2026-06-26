/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.dailysettlement.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院日结候选 payment 明细行（IF-IP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结候选 payment 明细行")
public class InpatientDailySettlementPaymentItemDto {

    /** 支付流水主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "支付流水 ID")
    private Long paymentId;

    /** 支付单号。 */
    @Schema(description = "支付单号")
    private String paymentNo;

    /** 业务 kind code。 */
    @Schema(description = "业务 kind code")
    private String kindEnum;

    /** 支付状态 code。 */
    @Schema(description = "支付状态 code")
    private String statusEnum;

    /** 结算时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "结算时间")
    private LocalDateTime billDate;

    /** 收退方向。 */
    @Schema(description = "收退方向")
    private String paymentEnum;

    /** 逆向单关联原支付主键；非空表示反结/退费逆向流水。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "逆向单关联原支付 ID")
    private Long relationId;

    /** 实收金额。 */
    @Schema(description = "实收金额")
    private BigDecimal tenderedAmount;

    /** 退还金额。 */
    @Schema(description = "退还金额")
    private BigDecimal returnedAmount;

    /** 净额。 */
    @Schema(description = "净额")
    private BigDecimal netAmount;

    /** payment 关联费用性质合同业务号（fin_contract.bus_no）。 */
    @Schema(description = "费用性质合同业务号")
    private String contractNo;

    /** 是否存在已确认医保关联。 */
    @Schema(description = "是否存在已确认医保关联")
    private Boolean hasInsurance;

    /** 病区名；预交金为「预交金」。 */
    @Schema(description = "病区名")
    private String wardName;

    /** 展示分类（病区/预交金/渠道）。 */
    @Schema(description = "展示分类")
    private String categoryLabel;
}
