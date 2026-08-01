/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 日结下半区扁平展示行（公共头 + 费用性质 + 险种子行）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结下半区展示行")
public class DailySettlementSummaryRowDto {

    /** 行键（便于前端/联调定位；可空）。 */
    @Schema(description = "行键")
    private String key;

    /** 展示标签。 */
    @Schema(description = "标签")
    private String label;

    /** 展示值（金额两位小数或人次整数文本）。 */
    @Schema(description = "值")
    private String value;

    /** 单位：元 / 人。 */
    @Schema(description = "单位")
    private String unit;

    /** 缩进层级：0=公共/费用性质，1=险种子行。 */
    @Schema(description = "缩进层级")
    private Integer level;
}
