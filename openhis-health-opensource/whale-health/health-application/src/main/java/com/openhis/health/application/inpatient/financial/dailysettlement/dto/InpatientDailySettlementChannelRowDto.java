/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.dailysettlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院日结上半区 fin_type_code 财务类别行（IF-IP-DCL-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结财务类别汇总行")
public class InpatientDailySettlementChannelRowDto {

    /** fin_type_code 字典 value。 */
    @Schema(description = "财务类别码")
    private String categoryCode;

    /** 财务类别展示名。 */
    @Schema(description = "财务类别标签")
    private String category;

    /** 金额。 */
    @Schema(description = "金额")
    private String amount;

    /** 金额单位。 */
    @Schema(description = "金额单位")
    private String unit;
}
