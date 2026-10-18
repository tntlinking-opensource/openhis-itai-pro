/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 确认医技耗材划价响应（AS-MT-CHG-001 IF-MT-CHG-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "确认医技耗材划价响应")
public class ConfirmMedicalTechDevicePricingResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean succeeded;

    /** 新建 DeviceRequest 主键列表。 */
    @Schema(description = "新建请求主键")
    private List<Long> createdRequestIds;

    /** 提示文案。 */
    @Schema(description = "提示")
    private String message;
}
