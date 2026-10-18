/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.pharmacydailysettlement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房日结待交账发药明细行（弹窗勾选候选）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药房日结待交账发药明细")
public class PharmacyDailySettlementDispenseItemDto {

    /** 发药行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药行主键")
    private Long dispenseId;

    /** 业务单号。 */
    @Schema(description = "业务单号")
    private String busNo;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String statusEnum;

    /** 发放状态展示文案。 */
    @Schema(description = "发放状态展示")
    private String statusEnumText;

    /** 发放类型 code（正向发药 / 退药）。 */
    @Schema(description = "发放类型 code")
    private String dispenseEnum;

    /** 发放类型展示文案（发药 / 退药）。 */
    @Schema(description = "发放类型展示")
    private String dispenseEnumText;

    /** 药品主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品主键")
    private Long medicationId;

    /** 药品目录业务编码。 */
    @Schema(description = "药品目录编码")
    private String medicationBusNo;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 生产厂家。 */
    @Schema(description = "生产厂家")
    private String manufacturer;

    /** 计量单位 code。 */
    @Schema(description = "计量单位 code")
    private String unitCode;

    /** 计量单位展示。 */
    @Schema(description = "计量单位展示")
    private String unitText;

    /** 发药库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发药库房主键")
    private Long locationId;

    /** 核发药师 Practitioner 主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "核发药师 Practitioner 主键")
    private Long practitionerId;

    /** 发药时间。 */
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "发药时间")
    private LocalDateTime dispenseTime;

    /** 实发数量。 */
    @Schema(description = "实发数量")
    private BigDecimal dispenseQuantity;

    /** 进价单价。 */
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 进价行额（退药为负）。 */
    @Schema(description = "进价行额")
    private BigDecimal purchaseAmount;

    /** 售价单价。 */
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 售价行额 / 小计（退药为负）。 */
    @Schema(description = "售价行额")
    private BigDecimal saleAmount;
}
