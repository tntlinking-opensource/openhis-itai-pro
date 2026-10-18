/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 作废登记响应（IF-PACS-REG-03）。
 */
@Getter
@Setter
@Schema(description = "检查登记-作废响应")
public class CancelRegistrationResponse {

    @Schema(description = "工单主键")
    private Long workItemId;

    @Schema(description = "状态枚举 code")
    private String statusEnum;

    @Schema(description = "是否成功")
    private Boolean success;
}
