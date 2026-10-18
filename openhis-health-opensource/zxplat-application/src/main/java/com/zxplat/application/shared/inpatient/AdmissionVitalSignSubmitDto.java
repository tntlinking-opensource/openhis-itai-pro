/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.inpatient;

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
