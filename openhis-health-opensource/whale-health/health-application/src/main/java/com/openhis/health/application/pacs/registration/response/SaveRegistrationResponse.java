/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.registration.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存登记响应（IF-PACS-REG-02）。
 */
@Getter
@Setter
@Schema(description = "检查登记-保存响应")
public class SaveRegistrationResponse {

    @Schema(description = "工单主键")
    private Long workItemId;

    @Schema(description = "检查号")
    private String accessionNumber;

    @Schema(description = "状态枚举 code")
    private String statusEnum;
}
