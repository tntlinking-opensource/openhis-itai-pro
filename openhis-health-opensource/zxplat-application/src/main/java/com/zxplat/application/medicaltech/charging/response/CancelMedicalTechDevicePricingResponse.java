/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 取消医技耗材划价响应（AS-MT-CHG-001 IF-MT-CHG-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消医技耗材划价响应")
public class CancelMedicalTechDevicePricingResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /** 冲销处理条数。 */
    @Schema(description = "冲销条数")
    private Integer cancelledCount;

    /** 提示文案。 */
    @Schema(description = "提示")
    private String message;
}
