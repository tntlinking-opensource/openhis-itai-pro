/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.dto.nursestation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 入科分床抽屉入院体征提交块（001 IF-IP-NUR-03/04 与 002 IF-IP-NUR-ATD-02 复用）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "入院体征提交块")
public class AdmissionVitalSignSubmitDto {

    /** 身高（cm），映射明细 {@code vital_signs_code=HEIGHT}。 */
    @Schema(description = "身高(cm)")
    private String heightCm;

    /** 体重（kg），映射明细 {@code vital_signs_code=WEIGHT}。 */
    @Schema(description = "体重(kg)")
    private String weightKg;

    /** 体温（℃）；写主表 {@code dia_vital_sign.temperature}，不进明细 TEMP（BR-VS-007）。 */
    @Schema(description = "体温(℃)")
    private String temperature;

    /** 心率，映射明细 {@code vital_signs_code=HR}。 */
    @Schema(description = "心率")
    private String heartRate;

    /** 脉搏，映射明细 {@code vital_signs_code=PULSE}。 */
    @Schema(description = "脉搏")
    private String pulse;

    /** 收缩压（mmHg），映射明细 {@code vital_signs_code=SBP}。 */
    @Schema(description = "收缩压(mmHg)")
    private String bloodPressureHigh;

    /** 舒张压（mmHg），映射明细 {@code vital_signs_code=DBP}。 */
    @Schema(description = "舒张压(mmHg)")
    private String bloodPressureLow;
}
