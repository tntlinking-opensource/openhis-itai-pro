/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustment.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.finance.priceadjustment.dto.PriceAdjustmentLineDto;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 调价单详情响应（IF-PRC-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价单详情响应")
public class PriceAdjustmentDocumentResponse {

    /** 调价单号。 */
    @Schema(description = "调价单号")
    private String busNo;

    /** 审批状态 code。 */
    @Schema(description = "审批状态 code")
    private String statusEnum;

    /** 审批状态展示名。 */
    @Schema(description = "审批状态展示名")
    private String statusEnumText;

    /** 提交人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "提交人主键")
    private Long submitterId;

    /** 提交人展示名。 */
    @Schema(description = "提交人展示名")
    private String submitterName;

    /** 提交时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "提交时间")
    private Date submitTime;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;

    /** 审批时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "审批时间")
    private Date approveTime;

    /** 审批意见。 */
    @Schema(description = "审批意见")
    private String approveComment;

    /** 进价盈亏合计。 */
    @Schema(description = "进价盈亏合计")
    private BigDecimal totalPurchaseImpact;

    /** 售价盈亏合计。 */
    @Schema(description = "售价盈亏合计")
    private BigDecimal totalSellingImpact;

    /** 项目行列表。 */
    @Schema(description = "项目行列表")
    private List<PriceAdjustmentLineDto> lines;
}
