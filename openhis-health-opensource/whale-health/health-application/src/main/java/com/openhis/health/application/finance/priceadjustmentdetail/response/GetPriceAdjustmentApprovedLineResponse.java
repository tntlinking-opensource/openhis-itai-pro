/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustmentdetail.response;

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

/**
 * 查看调价项目详情响应（IF-PRC-DTL-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "查看调价项目详情响应")
public class GetPriceAdjustmentApprovedLineResponse {

    /** 调价项目行详情。 */
    @Schema(description = "调价项目行详情")
    private PriceAdjustmentLineDto line;

    /** 行级盈亏合计。 */
    @Schema(description = "行级盈亏合计")
    private BigDecimal totalImpact;

    /** 生效时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "生效时间")
    private Date approveTime;

    /** 审批人主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "审批人主键")
    private Long approverId;

    /** 审批人展示名。 */
    @Schema(description = "审批人展示名")
    private String approverName;
}
