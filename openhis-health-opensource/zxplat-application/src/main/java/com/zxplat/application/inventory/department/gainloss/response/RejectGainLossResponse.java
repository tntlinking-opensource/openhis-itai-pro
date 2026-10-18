/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.gainloss.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审批驳回损益出参（IF-DEPT-GL-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "审批驳回损益 IF-DEPT-GL-09")
public class RejectGainLossResponse {

    /** 是否成功 success。 */
    @Schema(description = "是否成功 success")
    private Boolean success;
}
