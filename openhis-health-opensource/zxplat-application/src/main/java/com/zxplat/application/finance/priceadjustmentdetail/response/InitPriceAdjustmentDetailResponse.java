/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustmentdetail.response;

import com.zxplat.framework.core.application.dto.OptionDto;
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

    /** 默认生效时间下限（兼容字段；不下发，前端自带当月）。 */
    @Schema(description = "默认生效时间下限（兼容字段，不下发）")
    private String defaultApproveTimeFrom;

    /** 默认生效时间上限（兼容字段；不下发，前端自带当月）。 */
    @Schema(description = "默认生效时间上限（兼容字段，不下发）")
    private String defaultApproveTimeTo;

    /** 项目类型 Tab 选项。 */
    @Schema(description = "项目类型 Tab 选项")
    private List<OptionDto> itemTableTabOptions;

    /** 调价原因选项（兼容字段；原因手写，固定空列表）。 */
    @Schema(description = "调价原因选项（已废弃，固定空列表）")
    private List<OptionDto> adjustmentReasonOptions;

    /** 默认分页大小。 */
    @Schema(description = "默认分页大小")
    private Integer defaultPageSize;
}
