/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.config.modality.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存/停用模态能力响应。
 */
@Getter
@Setter
@Schema(description = "模态能力-写响应")
public class SaveCapabilityResponse {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "activityId")
    private Long activityId;

    @Schema(description = "模态码")
    private String modalityCode;

    @Schema(description = "是否启用")
    private Boolean enabledFlag;
}
