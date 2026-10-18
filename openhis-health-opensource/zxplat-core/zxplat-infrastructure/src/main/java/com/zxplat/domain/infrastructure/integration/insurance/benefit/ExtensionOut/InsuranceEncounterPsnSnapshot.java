/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 就诊侧医保人员展示快照（本地平台映射落库，不调远程）。
 */
@Data
@Schema(description = "就诊医保人员展示快照")
public class InsuranceEncounterPsnSnapshot {

    /** 医保人员编号（平台 psn_no）。 */
    @Schema(description = "医保人员编号")
    private String psnNo;

    /** 医疗类别 code（平台 med_type）。 */
    @Schema(description = "医疗类别 code")
    private String medType;

    /** 医疗类别展示（如 21-普通住院）。 */
    @Schema(description = "医疗类别展示")
    private String medTypeDisplay;
}
