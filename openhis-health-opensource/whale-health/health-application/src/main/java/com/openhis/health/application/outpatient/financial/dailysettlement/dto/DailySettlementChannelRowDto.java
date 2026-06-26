/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 日结财务类别汇总行（IF-OP-DCL-02 上半区，fin_type_code 字典驱动）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "日结财务类别汇总行")
public class DailySettlementChannelRowDto {

    /** 财务类别字典码（fin_type_code.value）。 */
    @Schema(description = "财务类别码")
    private String categoryCode;

    /** 财务类别展示名。 */
    @Schema(description = "财务类别展示名")
    private String category;

    /** 两位小数字符串金额。 */
    @Schema(description = "金额")
    private String amount;

    /** 金额单位，固定「元」。 */
    @Schema(description = "金额单位")
    private String unit;
}
