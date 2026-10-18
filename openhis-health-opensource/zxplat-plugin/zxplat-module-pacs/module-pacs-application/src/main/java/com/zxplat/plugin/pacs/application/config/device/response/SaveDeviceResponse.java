/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.device.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存/停用设备台账响应。
 */
@Getter
@Setter
@Schema(description = "设备台账-写响应")
public class SaveDeviceResponse {

    /** 主键。 */
    @Schema(description = "主键")
    private Long id;

    /** 设备编码。 */
    @Schema(description = "设备编码")
    private String deviceCode;

    /** 设备名称。 */
    @Schema(description = "设备名称")
    private String name;

    /** 是否启用。 */
    @Schema(description = "是否启用")
    private Boolean enabledFlag;
}
