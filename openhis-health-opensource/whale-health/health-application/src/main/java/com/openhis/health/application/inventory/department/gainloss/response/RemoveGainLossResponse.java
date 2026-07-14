/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 删除损益单出参（IF-DEPT-GL-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "删除损益单 IF-DEPT-GL-10")
public class RemoveGainLossResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
