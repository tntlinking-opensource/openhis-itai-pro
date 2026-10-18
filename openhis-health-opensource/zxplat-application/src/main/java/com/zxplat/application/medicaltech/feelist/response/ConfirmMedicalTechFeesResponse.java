/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.feelist.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医技费用清单一键确费响应（AS-MT-FEE-001 IF-MT-FEE-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技费用清单一键确费响应")
public class ConfirmMedicalTechFeesResponse {

    /** 是否成功；失败走业务异常。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /** 本次新打戳的确费行数（已确费跳过的不计）。 */
    @Schema(description = "新确费行数")
    private Integer confirmedCount;

    /** 提示文案（可选；如「无待确费行」）。 */
    @Schema(description = "提示文案")
    private String message;
}
