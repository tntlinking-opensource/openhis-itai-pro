/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.priceadjustmentdetail.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 调价明细页初始化响应（IF-PRC-DTL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调价明细页初始化响应")
public class InitPriceAdjustmentDetailResponse {

    /** 默认生效时间下限。 */
    @Schema(description = "默认生效时间下限")
    private String defaultApproveTimeFrom;

    /** 默认生效时间上限。 */
    @Schema(description = "默认生效时间上限")
    private String defaultApproveTimeTo;

    /** 项目类型 Tab 选项。 */
    @Schema(description = "项目类型 Tab 选项")
    private List<OptionDto> itemTableTabOptions;

    /** 调价原因选项。 */
    @Schema(description = "调价原因选项")
    private List<OptionDto> adjustmentReasonOptions;

    /** 默认分页大小。 */
    @Schema(description = "默认分页大小")
    private Integer defaultPageSize;
}
