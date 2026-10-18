/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.nursingrecord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 护理记录视图模式选项（IF-IP-NRC-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理记录视图模式选项")
public class NursingRecordViewModeOptionDto {

    /** 视图模式 code。 */
    @Schema(description = "视图模式 code")
    private String code;

    /** 展示文案。 */
    @Schema(description = "展示文案")
    private String displayText;

    /** 是否默认选中。 */
    @Schema(description = "是否默认选中")
    private Boolean defaultSelected;
}
