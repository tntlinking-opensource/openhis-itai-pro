/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 组套模板生命周期变更响应（IF-BAS-OTL-06）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板生命周期变更响应")
public class ChangeOrderTemplateLifecycleResponse {

    @Schema(description = "是否成功")
    /** 是否成功。 */
    private Boolean success;
}
