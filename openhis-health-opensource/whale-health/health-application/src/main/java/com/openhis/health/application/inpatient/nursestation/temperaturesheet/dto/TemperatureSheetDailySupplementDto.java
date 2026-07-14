/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 体温单补充记录块（仅 22:00 槽保存时写入，BR-IP-TMP-011）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单补充记录")
public class TemperatureSheetDailySupplementDto {

    /** 体重。 */
    @Schema(description = "体重")
    private String weight;

    /** 身高。 */
    @Schema(description = "身高")
    private String height;

    /** 入量。 */
    @Schema(description = "入量")
    private String intake;

    /** 尿量。 */
    @Schema(description = "尿量")
    private String urine;

    /** 大便次数。 */
    @Schema(description = "大便次数")
    private String stoolCount;

    /** 腹围。 */
    @Schema(description = "腹围")
    private String abdominalCircumference;

    /** 小便次数。 */
    @Schema(description = "小便次数")
    private String urineCount;

    /** 皮试阳性。 */
    @Schema(description = "皮试阳性")
    private String skinTestPositive;

    /** 其他说明。 */
    @Schema(description = "其他说明")
    private String other;
}
