/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.config.device.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用/删除设备台账请求。
 */
@Getter
@Setter
@Schema(description = "设备台账-主键请求")
public class DeviceIdRequest {

    /** 主键。 */
    @NotNull
    @Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
}
